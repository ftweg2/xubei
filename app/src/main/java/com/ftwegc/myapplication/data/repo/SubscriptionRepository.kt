package com.ftwegc.myapplication.data.repo

import com.ftwegc.myapplication.data.local.dao.SubscriptionDao
import com.ftwegc.myapplication.data.local.dao.TrafficDao
import com.ftwegc.myapplication.data.local.dao.VpsDao
import com.ftwegc.myapplication.data.local.toDomain
import com.ftwegc.myapplication.data.local.toEntity
import com.ftwegc.myapplication.domain.calc.DueDateCalculator
import com.ftwegc.myapplication.domain.calc.RenewCalculator
import com.ftwegc.myapplication.domain.calc.RenewMode
import com.ftwegc.myapplication.domain.calc.RenewPlan
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.CycleUnit
import com.ftwegc.myapplication.domain.model.LockedField
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.domain.model.VpsConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Runs a block atomically. Room-backed in the app, a pass-through in unit tests. */
interface TransactionRunner {
    suspend fun <R> inTransaction(block: suspend () -> R): R
}

/**
 * What [SubscriptionRepository.markRenewed] changed, enough to undo it.
 */
data class RenewOutcome(
    val subscriptionId: Long,
    val previousDue: LocalDate?,
    val previousAnchorDay: Int?,
    val previousLockedFields: Int?,
    val newDue: LocalDate,
    val newAnchorDay: Int?,
)

/** How [SubscriptionRepository.save] changes the Komari node link of a VPS. */
sealed interface KomariLinkChange {
    /** Keep the stored link (default). */
    data object Keep : KomariLinkChange

    /**
     * Link to node [uuid]; a different node than before resets locks and counters. [region] is the
     * node's flag emoji when known (the next sync fills it in otherwise).
     */
    data class Link(val uuid: String, val name: String?, val group: String?, val region: String? = null) : KomariLinkChange

    /** Remove the link together with its locks and counters. */
    data object Unlink : KomariLinkChange
}

/** Thrown by [SubscriptionRepository.save] when the node is already linked to [ownerId]. */
class KomariNodeTakenException(val ownerId: Long) : IllegalStateException("node already linked")

/** Subscriptions and their VPS settings. "Today" always comes from the injected [clock]. */
class SubscriptionRepository(
    private val subscriptionDao: SubscriptionDao,
    private val vpsDao: VpsDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
    private val trafficDao: TrafficDao? = null,
) {

    /** Today's local date according to the injected clock. */
    fun today(): LocalDate = LocalDate.now(clock)

    /** The current instant according to the injected clock. */
    fun now(): Instant = clock.instant()

    /** Zone of the injected clock. */
    val zone: ZoneId get() = clock.zone

    /** Every subscription (archived included) with VPS settings. */
    fun observeAll(): Flow<List<SubscriptionWithVps>> {
        val rows = subscriptionDao.observeAll()
        val dao = trafficDao ?: return rows.map { list -> list.map { it.toDomain() } }.distinctUntilChanged()
        return combine(rows, dao.observeLatestPeriods()) { list, periods ->
            val byId = periods.associateBy { it.subscriptionId }
            list.map { row -> row.toDomain(byId[row.subscription.id]?.toDomain()) }
        }.distinctUntilChanged()
    }

    /** One subscription; emits `null` when it does not exist (for example after deletion). */
    fun observe(id: Long): Flow<SubscriptionWithVps?> {
        val row = subscriptionDao.observeById(id)
        val dao = trafficDao ?: return row.map { it?.toDomain() }.distinctUntilChanged()
        return combine(row, dao.observeLatestPeriod(id)) { r, period -> r?.toDomain(period?.toDomain()) }
            .distinctUntilChanged()
    }

    suspend fun get(id: Long): SubscriptionWithVps? =
        subscriptionDao.getById(id)?.toDomain(trafficDao?.getLatestPeriod(id)?.toDomain())

    /** The day each subscription was added to the app (local date in the clock's zone), by id. */
    fun observeAddedDates(): Flow<Map<Long, LocalDate>> =
        subscriptionDao.observeAll()
            .map { rows -> rows.associate { it.subscription.id to Instant.ofEpochMilli(it.subscription.createdAt).atZone(clock.zone).toLocalDate() } }
            .distinctUntilChanged()

    /** When the subscription was added to the app, `null` when it does not exist. */
    suspend fun createdAt(id: Long): Instant? =
        subscriptionDao.getEntity(id)?.createdAt?.let(Instant::ofEpochMilli)

    /**
     * Inserts ([Subscription.id] == 0) or updates a subscription and returns its id.
     *
     * VPS settings are stored only for [Category.VPS]; switching away from VPS deletes them.
     * On update only the user-editable VPS fields are taken from [vps]; the Komari link and the
     * last counters are kept from the stored row unless [link] says otherwise. While the
     * subscription is linked to a Komari node, every field the user changed is added to
     * [VpsConfig.lockedFields] so that later syncs do not overwrite it; fields in [unlocked] are
     * removed from the stored locks first (the next sync may overwrite them again).
     *
     * A newly linked node starts without locks: until the user edits a field, the panel's values
     * win on the next sync.
     *
     * @throws NoSuchElementException when updating a subscription that no longer exists.
     * @throws KomariNodeTakenException when [link] names a node linked to another subscription.
     */
    suspend fun save(
        subscription: Subscription,
        vps: VpsConfig?,
        link: KomariLinkChange = KomariLinkChange.Keep,
        unlocked: Int = LockedField.NONE,
    ): Long = transactions.inTransaction {
        val now = clock.millis()
        if (subscription.id == 0L) {
            if (link is KomariLinkChange.Link && subscription.category == Category.VPS) ensureFree(link.uuid, ownerId = 0)
            val id = subscriptionDao.upsert(subscription.toEntity(createdAt = now, updatedAt = now))
            if (subscription.category == Category.VPS) {
                var config = (vps ?: VpsConfig()).copy(subscriptionId = id, lockedFields = LockedField.NONE)
                config = applyLink(config, link)
                config = config.copy(appCountingSince = if (config.countingMode == CountingMode.APP_ACCUMULATED) clock.instant() else null)
                vpsDao.upsert(config.toEntity(id))
            }
            id
        } else {
            val id = subscription.id
            val stored = subscriptionDao.getById(id)
                ?: throw NoSuchElementException("subscription $id does not exist")
            val old = stored.subscription.toDomain()
            subscriptionDao.upsert(subscription.toEntity(createdAt = stored.subscription.createdAt, updatedAt = now))
            val current = stored.vps?.toDomain()
            if (subscription.category == Category.VPS) {
                val edited = vps ?: current ?: VpsConfig()
                val base = current ?: VpsConfig(subscriptionId = id)
                var merged = base.copy(
                    subscriptionId = id,
                    trafficLimitBytes = edited.trafficLimitBytes,
                    trafficLimitType = edited.trafficLimitType,
                    countingMode = edited.countingMode,
                    resetDay = edited.resetDay,
                    resetZoneId = edited.resetZoneId,
                    alertPercent = edited.alertPercent,
                    agentMonthRotate = edited.agentMonthRotate,
                )
                if (link is KomariLinkChange.Link && link.uuid != base.komariUuid) ensureFree(link.uuid, ownerId = id)
                val relinked = link is KomariLinkChange.Link && link.uuid != base.komariUuid
                merged = applyLink(merged, link)
                if (merged.isLinked) {
                    val kept = if (relinked) LockedField.NONE else base.lockedFields and unlocked.inv()
                    val changed = if (relinked) LockedField.NONE else changedMask(old, subscription, base, merged)
                    merged = merged.copy(lockedFields = kept or changed)
                }
                val nodeChanged = relinked || (link == KomariLinkChange.Unlink && base.komariUuid != null)
                val appMode = merged.countingMode == CountingMode.APP_ACCUMULATED
                val startsCounting = appMode && (base.countingMode != CountingMode.APP_ACCUMULATED || nodeChanged || base.appCountingSince == null)
                merged = merged.copy(
                    appCountingSince = when {
                        !appMode -> null
                        startsCounting -> clock.instant()
                        else -> base.appCountingSince
                    },
                )
                // Readings of another node are no baseline and no trend for this one.
                if (nodeChanged) trafficDao?.deleteSnapshotsFor(id)
                vpsDao.upsert(merged.toEntity(id))
            } else if (current != null) {
                vpsDao.deleteBySubscriptionId(id)
            }
            id
        }
    }

    private suspend fun ensureFree(uuid: String, ownerId: Long) {
        val owner = vpsDao.getByKomariUuid(uuid)?.subscriptionId
        if (owner != null && owner != ownerId) throw KomariNodeTakenException(owner)
    }

    /** [config] with [link] applied; switching to another node clears locks and counters. */
    private fun applyLink(config: VpsConfig, link: KomariLinkChange): VpsConfig = when (link) {
        KomariLinkChange.Keep -> config
        is KomariLinkChange.Link -> if (link.uuid == config.komariUuid) {
            config
        } else {
            config.copy(
                komariUuid = link.uuid,
                komariName = link.name,
                komariGroup = link.group,
                komariRegion = link.region,
                komariMissing = false,
                lockedFields = LockedField.NONE,
            ).withoutCounters()
        }
        KomariLinkChange.Unlink -> config.copy(
            komariUuid = null,
            komariName = null,
            komariGroup = null,
            komariRegion = null,
            komariMissing = false,
            lockedFields = LockedField.NONE,
        ).withoutCounters()
    }

    private fun VpsConfig.withoutCounters(): VpsConfig = copy(
        lastTotalUp = null,
        lastTotalDown = null,
        lastUptimeSec = null,
        lastReportedAt = null,
        lastFetchAt = null,
        lastError = null,
    )

    /**
     * Removes the Komari link of a VPS (for a node that is gone from the panel or offline for
     * good). The subscription, its settings and the traffic periods stay; locks and counters go.
     * Returns false when there is no linked VPS with this id.
     */
    suspend fun unlinkKomari(id: Long): Boolean = transactions.inTransaction {
        val stored = subscriptionDao.getById(id) ?: return@inTransaction false
        val vps = stored.vps?.toDomain()?.takeIf { it.isLinked } ?: return@inTransaction false
        val unlinked = applyLink(vps, KomariLinkChange.Unlink).copy(
            appCountingSince = if (vps.countingMode == CountingMode.APP_ACCUMULATED) clock.instant() else null,
        )
        trafficDao?.deleteSnapshotsFor(id)
        vpsDao.upsert(unlinked.toEntity(id))
        true
    }

    /** Archives or restores a subscription. Returns false when it does not exist. */
    suspend fun setArchived(id: Long, archived: Boolean): Boolean =
        subscriptionDao.setArchived(id, archived, clock.millis()) > 0

    /** Deletes a subscription together with its VPS settings, traffic data and reminder log. */
    suspend fun delete(id: Long): Boolean = subscriptionDao.delete(id) > 0

    /** The renewal [mode] would produce for [subscription], without saving; `null` if not renewable. */
    fun previewRenew(subscription: Subscription, mode: RenewMode): RenewPlan? =
        RenewCalculator.renew(subscription, today(), mode)

    /**
     * Marks a subscription as renewed for one cycle (see [RenewCalculator.renew]).
     * Returns `null` when it does not exist or cannot be renewed (one-time or permanent).
     */
    suspend fun markRenewed(id: Long, mode: RenewMode): RenewOutcome? = transactions.inTransaction {
        val stored = subscriptionDao.getById(id) ?: return@inTransaction null
        val sub = stored.subscription.toDomain()
        val plan = RenewCalculator.renew(sub, today(), mode) ?: return@inTransaction null
        subscriptionDao.updateDue(id, plan.nextDue.toEpochDay(), plan.anchorDay, clock.millis())
        val vps = stored.vps?.toDomain()
        if (vps != null && vps.isLinked) {
            vpsDao.upsert(vps.copy(lockedFields = LockedField.lock(vps.lockedFields, LockedField.DUE_DATE)).toEntity(id))
        }
        RenewOutcome(
            subscriptionId = id,
            previousDue = sub.nextDue,
            previousAnchorDay = sub.anchorDay,
            previousLockedFields = vps?.lockedFields,
            newDue = plan.nextDue,
            newAnchorDay = plan.anchorDay,
        )
    }

    /** Reverts [markRenewed]. */
    suspend fun undoRenew(outcome: RenewOutcome) = transactions.inTransaction {
        val id = outcome.subscriptionId
        subscriptionDao.updateDue(id, outcome.previousDue?.toEpochDay(), outcome.previousAnchorDay, clock.millis())
        val lock = outcome.previousLockedFields
        if (lock != null) {
            vpsDao.getBySubscriptionId(id)?.let { vpsDao.upsert(it.copy(lockedFields = lock)) }
        }
    }

    /**
     * Rolls every overdue auto-renewing subscription forward to its first due date that is not
     * before today ([DueDateCalculator.rollForward]). Returns the number of rows changed.
     */
    suspend fun rollForwardAutoRenewals(): Int = transactions.inTransaction {
        val today = today()
        var changed = 0
        for (entity in subscriptionDao.getAutoRenewExpired(today.toEpochDay())) {
            val sub = entity.toDomain()
            val due = sub.nextDue ?: continue
            val anchor = sub.anchorDay ?: due.dayOfMonth.takeIf { sub.cycle.unit == CycleUnit.MONTH }
            val rolled = DueDateCalculator.rollForward(due, sub.cycle, anchor, today)
            if (rolled.periods > 0) {
                subscriptionDao.updateDue(sub.id, rolled.due.toEpochDay(), anchor ?: sub.anchorDay, clock.millis())
                changed++
            }
        }
        changed
    }

    private fun changedMask(old: Subscription, new: Subscription, oldVps: VpsConfig, newVps: VpsConfig): Int {
        var mask = LockedField.NONE
        if (old.name != new.name) mask = mask or LockedField.NAME.mask
        if (old.priceMinor != new.priceMinor || old.isFree != new.isFree) mask = mask or LockedField.PRICE.mask
        if (old.currency != new.currency) mask = mask or LockedField.CURRENCY.mask
        if (old.cycle != new.cycle) mask = mask or LockedField.CYCLE.mask
        if (old.nextDue != new.nextDue || old.isPermanent != new.isPermanent) mask = mask or LockedField.DUE_DATE.mask
        if (old.autoRenew != new.autoRenew) mask = mask or LockedField.AUTO_RENEW.mask
        if (oldVps.trafficLimitBytes != newVps.trafficLimitBytes) mask = mask or LockedField.TRAFFIC_LIMIT.mask
        if (oldVps.trafficLimitType != newVps.trafficLimitType) mask = mask or LockedField.TRAFFIC_LIMIT_TYPE.mask
        return mask
    }
}
