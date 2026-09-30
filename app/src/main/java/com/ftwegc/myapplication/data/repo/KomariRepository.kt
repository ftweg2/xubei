package com.ftwegc.myapplication.data.repo

import com.ftwegc.myapplication.data.local.dao.SubscriptionDao
import com.ftwegc.myapplication.data.local.dao.VpsDao
import com.ftwegc.myapplication.data.local.toDomain
import com.ftwegc.myapplication.data.local.toEntity
import com.ftwegc.myapplication.data.prefs.SecretStore
import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.remote.komari.KomariApi
import com.ftwegc.myapplication.data.remote.komari.KomariApiFactory
import com.ftwegc.myapplication.data.remote.komari.KomariEndpoint
import com.ftwegc.myapplication.data.remote.komari.KomariError
import com.ftwegc.myapplication.data.remote.komari.KomariNode
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import com.ftwegc.myapplication.data.remote.komari.KomariUrl
import com.ftwegc.myapplication.domain.calc.FieldChange
import com.ftwegc.myapplication.domain.calc.KomariMerge
import com.ftwegc.myapplication.domain.model.LockedField
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.VpsConfig
import com.ftwegc.myapplication.domain.model.VpsTrafficFields
import kotlinx.coroutines.flow.first
import java.time.Clock

/** Which API key a connection test uses. */
sealed interface KeyInput {
    /** The key stored in [SecretStore] (if any). */
    data object Saved : KeyInput

    /** Guest mode, no key. */
    data object None : KeyInput

    /** A newly typed key (not stored yet). */
    class New(val key: String) : KeyInput {
        override fun toString(): String = "New(<redacted>)"
    }
}

/** What a successful connection test found. */
data class KomariConnectionInfo(
    /** Panel version, empty when the panel does not report one. */
    val version: String,
    val nodeCount: Int,
    /** Nodes dropped because they had no uuid. */
    val droppedCount: Int,
)

/** State of one panel node relative to the local subscriptions. */
enum class ImportStatus {
    /** No subscription is linked to this node yet. */
    NEW,

    /** Linked and nothing would change. */
    LINKED,

    /** Linked and a sync would change some fields. */
    UPDATE,
}

/**
 * One row of the import page.
 *
 * @property changes what an import would change (for a new node: every field with `old = null`).
 */
data class ImportItem(
    val node: KomariNode,
    val status: ImportStatus,
    val subscriptionId: Long?,
    val subscriptionName: String?,
    val changes: List<FieldChange>,
    val lockedFields: Int,
    /** The panel has no price and no due date for this node (fill them in after import). */
    val billingMissing: Boolean = false,
    /** The subscription's name after an import without a new name (panel name unless locked). */
    val importName: String = subscriptionName ?: node.name.ifBlank { node.uuid },
)

/** The import page model. */
data class ImportPreview(
    val items: List<ImportItem>,
    val droppedCount: Int,
    /** Names of linked subscriptions whose node is no longer listed by the panel. */
    val missingNames: List<String>,
)

/**
 * @property incompleteIds imported subscriptions that still lack a price and a due date
 *   ([ImportItem.billingMissing]); the UI offers to complete them.
 */
data class ImportSummary(
    val created: Int,
    val updated: Int,
    val unchanged: Int,
    val incompleteIds: List<Long> = emptyList(),
)

data class SyncSummary(
    val updated: Int,
    val unchanged: Int,
    /** Linked subscriptions whose node was not found (marked, never deleted). */
    val missing: Int,
    /** Panel nodes that are not linked to any subscription. */
    val unlinkedNodes: Int,
)

/** A node offered in the edit page's link picker, with the subscription it is linked to. */
data class PickerNode(val node: KomariNode, val linkedSubscriptionId: Long?, val linkedSubscriptionName: String?)

/**
 * Komari panel configuration, node import and sync. Merging goes through [KomariMerge], so
 * locked fields ([VpsConfig.lockedFields]) are never overwritten. Nodes that disappear from the
 * panel only get `komari_missing`; subscriptions are never deleted here.
 */
class KomariRepository(
    private val settingsStore: SettingsStore,
    private val secretStore: SecretStore,
    private val apiFactory: KomariApiFactory,
    private val subscriptionDao: SubscriptionDao,
    private val vpsDao: VpsDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {

    // ------------------------------------------------------------------ Configuration

    /** An API for the saved configuration, or [KomariError.NotConfigured] / [KomariError.InvalidUrl]. */
    suspend fun currentApi(): KomariResult<KomariApi> {
        val url = settingsStore.settings.first().komariUrl ?: return KomariResult.Err(KomariError.NotConfigured)
        val normalized = KomariUrl.normalize(url) ?: return KomariResult.Err(KomariError.InvalidUrl)
        return KomariResult.Ok(apiFactory.create(KomariEndpoint(normalized, secretStore.getApiKey())))
    }

    /**
     * Tests [rawUrl] with [key] without saving anything: lists the nodes, then asks for the version
     * (a panel without `/api/version` still passes with an empty version).
     */
    suspend fun testConnection(rawUrl: String, key: KeyInput): KomariResult<KomariConnectionInfo> {
        if (rawUrl.isBlank()) return KomariResult.Err(KomariError.NotConfigured)
        val url = KomariUrl.normalize(rawUrl) ?: return KomariResult.Err(KomariError.InvalidUrl)
        val api = apiFactory.create(KomariEndpoint(url, resolveKey(key)))
        val nodes = when (val r = api.getNodes()) {
            is KomariResult.Err -> return r
            is KomariResult.Ok -> r.value
        }
        val version = (api.getVersion() as? KomariResult.Ok)?.value?.version.orEmpty()
        return KomariResult.Ok(KomariConnectionInfo(version, nodes.nodes.size, nodes.droppedCount))
    }

    /**
     * Saves the panel address (normalized) and applies [key]. A blank address removes the
     * configuration and the key. Returns the stored address, or an error for an invalid one.
     */
    suspend fun saveConfig(rawUrl: String, key: KeyInput): KomariResult<String?> {
        if (rawUrl.isBlank()) {
            settingsStore.setKomariUrl(null)
            secretStore.clear()
            return KomariResult.Ok(null)
        }
        val url = KomariUrl.normalize(rawUrl) ?: return KomariResult.Err(KomariError.InvalidUrl)
        when (key) {
            KeyInput.Saved -> Unit
            KeyInput.None -> secretStore.clear()
            is KeyInput.New -> if (!secretStore.setApiKey(key.key)) return KomariResult.Err(KomariError.Parse)
        }
        settingsStore.setKomariUrl(url)
        return KomariResult.Ok(url)
    }

    private suspend fun resolveKey(key: KeyInput): String? = when (key) {
        KeyInput.Saved -> secretStore.getApiKey()
        KeyInput.None -> null
        is KeyInput.New -> key.key
    }

    // ------------------------------------------------------------------ Import

    /** Fetches the nodes and describes what importing each one would do. */
    suspend fun loadImportPreview(): KomariResult<ImportPreview> {
        val api = when (val r = currentApi()) {
            is KomariResult.Err -> return r
            is KomariResult.Ok -> r.value
        }
        return when (val r = api.getNodes()) {
            is KomariResult.Err -> r
            is KomariResult.Ok -> KomariResult.Ok(buildPreview(r.value.nodes, r.value.droppedCount))
        }
    }

    /** Pure part of [loadImportPreview], on already fetched [nodes]. */
    suspend fun buildPreview(nodes: List<KomariNode>, droppedCount: Int = 0): ImportPreview {
        val now = clock.instant()
        val defaultCurrency = settingsStore.settings.first().defaultCurrency
        val items = nodes.map { node ->
            val row = vpsDao.getByKomariUuid(node.uuid)?.let { subscriptionDao.getById(it.subscriptionId) }
            val sub = row?.subscription?.toDomain()
            val vps = row?.vps?.toDomain()
            val locked = vps?.lockedFields ?: 0
            val result = KomariMerge.merge(sub, vps?.trafficFields(), node.toSnapshot(), locked, clock.zone, now, defaultCurrency)
            ImportItem(
                node = node,
                status = when {
                    sub == null -> ImportStatus.NEW
                    result.hasChanges -> ImportStatus.UPDATE
                    else -> ImportStatus.LINKED
                },
                subscriptionId = sub?.id,
                subscriptionName = sub?.name,
                changes = result.changes,
                lockedFields = locked,
                // Only for nodes that would be created: an imported one already shows "待补充" itself.
                billingMissing = sub == null && KomariMerge.lacksBillingInfo(node.toSnapshot(), clock.zone, now),
                importName = result.subscription.name,
            )
        }
        val listed = nodes.mapTo(HashSet()) { it.uuid }
        val missing = vpsDao.getLinked()
            .filter { it.komariUuid !in listed }
            .mapNotNull { subscriptionDao.getEntity(it.subscriptionId)?.name }
        return ImportPreview(items, droppedCount, missing)
    }

    /**
     * Creates or updates one subscription per node in [nodes] and records the time as the last
     * sync (the nodes were just read from the panel and applied).
     *
     * @param names new names by node uuid, typed on the import page. A name that differs from the
     *   panel's is locked like a name edited in the app, so later syncs keep it. Blank names are ignored.
     */
    suspend fun importNodes(nodes: List<KomariNode>, names: Map<String, String> = emptyMap()): ImportSummary {
        val summary = importInTransaction(nodes, names)
        if (nodes.isNotEmpty()) settingsStore.setKomariLastSyncAt(clock.millis())
        return summary
    }

    private suspend fun importInTransaction(nodes: List<KomariNode>, names: Map<String, String>): ImportSummary = transactions.inTransaction {
        var created = 0
        var updated = 0
        var unchanged = 0
        val settings = settingsStore.settings.first()
        val incomplete = mutableListOf<Long>()
        for (node in nodes) {
            when (apply(node, settings.trafficAlertPercent, settings.defaultCurrency, names[node.uuid])) {
                Applied.CREATED -> created++
                Applied.UPDATED -> updated++
                Applied.UNCHANGED -> unchanged++
            }
            val id = vpsDao.getByKomariUuid(node.uuid)?.subscriptionId ?: continue
            val sub = subscriptionDao.getEntity(id)?.toDomain() ?: continue
            if (sub.priceMinor == 0L && !sub.isFree && sub.nextDue == null && !sub.isPermanent) incomplete += id
        }
        ImportSummary(created, updated, unchanged, incomplete)
    }

    // ------------------------------------------------------------------ Sync

    /**
     * Re-reads the panel and updates every linked subscription (locks honored). Linked nodes that
     * are no longer listed get `komari_missing = true`; nothing is created or deleted.
     */
    suspend fun sync(): KomariResult<SyncSummary> {
        val api = when (val r = currentApi()) {
            is KomariResult.Err -> return r
            is KomariResult.Ok -> r.value
        }
        val nodes = when (val r = api.getNodes()) {
            is KomariResult.Err -> return r
            is KomariResult.Ok -> r.value.nodes
        }
        val summary = syncWith(nodes)
        settingsStore.setKomariLastSyncAt(clock.millis())
        return KomariResult.Ok(summary)
    }

    /** Pure part of [sync], on already fetched [nodes]. */
    suspend fun syncWith(nodes: List<KomariNode>): SyncSummary = transactions.inTransaction {
        val byUuid = nodes.associateBy { it.uuid }
        var updated = 0
        var unchanged = 0
        var missing = 0
        val settings = settingsStore.settings.first()
        val linked = vpsDao.getLinked()
        for (config in linked) {
            val node = byUuid[config.komariUuid]
            if (node == null) {
                if (!config.komariMissing) vpsDao.setKomariMissing(config.subscriptionId, true)
                missing++
                continue
            }
            when (apply(node, settings.trafficAlertPercent, settings.defaultCurrency)) {
                Applied.UPDATED -> updated++
                else -> unchanged++
            }
        }
        val linkedUuids = linked.mapNotNullTo(HashSet()) { it.komariUuid }
        SyncSummary(updated, unchanged, missing, nodes.count { it.uuid !in linkedUuids })
    }

    /** Nodes for the edit page's link picker. */
    suspend fun loadPickerNodes(): KomariResult<List<PickerNode>> {
        val api = when (val r = currentApi()) {
            is KomariResult.Err -> return r
            is KomariResult.Ok -> r.value
        }
        return when (val r = api.getNodes()) {
            is KomariResult.Err -> r
            is KomariResult.Ok -> KomariResult.Ok(
                r.value.nodes.map { node ->
                    val owner = vpsDao.getByKomariUuid(node.uuid)?.subscriptionId
                    PickerNode(node, owner, owner?.let { subscriptionDao.getEntity(it)?.name })
                },
            )
        }
    }

    // ------------------------------------------------------------------ Merge application

    private enum class Applied { CREATED, UPDATED, UNCHANGED }

    /**
     * Merges [node] into its linked subscription, or creates a new VPS subscription for it.
     * [newName] (typed on the import page) replaces the merged name; it is locked when it differs
     * from the panel's name.
     */
    private suspend fun apply(node: KomariNode, defaultAlertPercent: Int, defaultCurrency: String, newName: String? = null): Applied {
        val nowMillis = clock.millis()
        val existingVps = vpsDao.getByKomariUuid(node.uuid)
        val row = existingVps?.let { subscriptionDao.getById(it.subscriptionId) }
        val sub: Subscription? = row?.subscription?.toDomain()
        val vps: VpsConfig? = row?.vps?.toDomain()
        val merged = KomariMerge.merge(sub, vps?.trafficFields(), node.toSnapshot(), vps?.lockedFields ?: 0, clock.zone, clock.instant(), defaultCurrency)
        val custom = newName?.trim()?.takeIf { it.isNotEmpty() && it != merged.subscription.name }
        val result = if (custom == null) merged else merged.copy(subscription = merged.subscription.copy(name = custom))
        val nameLock = if (custom != null && custom != node.name.trim()) LockedField.NAME.mask else LockedField.NONE

        if (sub == null || vps == null) {
            val id = subscriptionDao.upsert(result.subscription.toEntity(createdAt = nowMillis, updatedAt = nowMillis))
            val config = VpsConfig(
                subscriptionId = id,
                komariUuid = node.uuid,
                komariName = node.name.ifBlank { null },
                komariGroup = node.group.ifBlank { null },
                komariRegion = node.region.ifBlank { null },
                trafficLimitBytes = result.traffic.limitBytes,
                trafficLimitType = result.traffic.limitType,
                alertPercent = defaultAlertPercent.coerceIn(VpsConfig.MIN_ALERT_PERCENT, VpsConfig.MAX_ALERT_PERCENT),
                lockedFields = nameLock,
            )
            vpsDao.upsert(config.toEntity(id))
            return Applied.CREATED
        }

        val changed = result.hasChanges || custom != null
        if (changed) {
            subscriptionDao.upsert(result.subscription.toEntity(createdAt = row.subscription.createdAt, updatedAt = nowMillis))
        }
        val newVps = vps.copy(
            komariName = node.name.ifBlank { vps.komariName },
            komariGroup = node.group.ifBlank { null },
            // A node whose region was cleared on the panel keeps the last known flag.
            komariRegion = node.region.ifBlank { vps.komariRegion },
            komariMissing = false,
            trafficLimitBytes = result.traffic.limitBytes,
            trafficLimitType = result.traffic.limitType,
            lockedFields = vps.lockedFields or nameLock,
        )
        if (newVps != vps) vpsDao.upsert(newVps.toEntity(vps.subscriptionId))
        return if (changed) Applied.UPDATED else Applied.UNCHANGED
    }

    private fun VpsConfig.trafficFields(): VpsTrafficFields = VpsTrafficFields(trafficLimitBytes, trafficLimitType)
}
