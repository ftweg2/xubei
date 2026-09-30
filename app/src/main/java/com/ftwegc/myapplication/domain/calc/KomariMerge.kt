package com.ftwegc.myapplication.domain.calc

import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.KomariNodeSnapshot
import com.ftwegc.myapplication.domain.model.LockedField
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.VpsTrafficFields
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Price as merged: [minor] in minor units (price × 100); [isFree] for Komari price `-1`. */
data class MergePrice(val minor: Long, val isFree: Boolean)

/** Due date as merged: [date] is `null` when there is none; [isPermanent] for long-term valid nodes. */
data class MergeDue(val date: LocalDate?, val isPermanent: Boolean)

/**
 * One field the merge changes, for display on the import page.
 * `old` is `null` when the subscription (or its traffic settings) did not exist before.
 */
sealed interface FieldChange {
    /** The lockable field this change belongs to. */
    val field: LockedField

    data class Name(val old: String?, val new: String) : FieldChange {
        override val field: LockedField get() = LockedField.NAME
    }

    data class Price(val old: MergePrice?, val new: MergePrice) : FieldChange {
        override val field: LockedField get() = LockedField.PRICE
    }

    data class Currency(val old: String?, val new: String) : FieldChange {
        override val field: LockedField get() = LockedField.CURRENCY
    }

    data class Cycle(val old: BillingCycle?, val new: BillingCycle) : FieldChange {
        override val field: LockedField get() = LockedField.CYCLE
    }

    data class DueDate(val old: MergeDue?, val new: MergeDue) : FieldChange {
        override val field: LockedField get() = LockedField.DUE_DATE
    }

    data class AutoRenew(val old: Boolean?, val new: Boolean) : FieldChange {
        override val field: LockedField get() = LockedField.AUTO_RENEW
    }

    /** Traffic limit in bytes (0 = no limit). */
    data class TrafficLimit(val old: Long?, val new: Long) : FieldChange {
        override val field: LockedField get() = LockedField.TRAFFIC_LIMIT
    }

    data class LimitType(val old: TrafficLimitType?, val new: TrafficLimitType) : FieldChange {
        override val field: LockedField get() = LockedField.TRAFFIC_LIMIT_TYPE
    }
}

/**
 * Outcome of [KomariMerge.merge].
 *
 * @property subscription merged subscription (keeps `id`, category, note, archived of an existing one).
 * @property traffic merged VPS traffic settings.
 * @property changes changed fields in [LockedField] declaration order; empty when nothing changes.
 * @property isNew true when no subscription existed and a new one was built.
 */
data class KomariMergeResult(
    val subscription: Subscription,
    val traffic: VpsTrafficFields,
    val changes: List<FieldChange>,
    val isNew: Boolean,
) {
    /** True when [changes] is not empty. */
    val hasChanges: Boolean get() = changes.isNotEmpty()
}

/** Merges a Komari node into a (possibly new) VPS subscription, honoring locked fields. */
object KomariMerge {

    /** Komari price meaning "free". */
    const val FREE_PRICE: Double = -1.0

    /** Komari price meaning "not set" (the panel hides the price tag). */
    const val UNSET_PRICE: Double = 0.0

    /** Expiry years `<=` this mean "no due date" (Komari's zero time 0001-01-01, or the Unix epoch). */
    const val NO_DUE_MAX_YEAR: Int = 1970

    /**
     * An expiry more than this many years after "now" means "long-term valid". Komari uses the same
     * rule (server auto-renewal: `now + 100 years`; web UI: more than 36500 days left), and its admin
     * UI's "set to long term" button writes `now + 200 years`.
     */
    const val PERMANENT_AFTER_YEARS: Long = 100

    /** Cycle of a new subscription when Komari's billing cycle is not set. */
    val DEFAULT_NEW_CYCLE: BillingCycle = BillingCycle.MONTHLY

    /**
     * Merges [node] into [existing] / [existingTraffic].
     *
     * - A field whose bit is set in [lockedFields] (see [LockedField]) keeps its existing value.
     * - A node value that is "not set" keeps the existing value: blank name or currency, a price of
     *   0 or a negative price other than -1, a billing cycle of 0 or below -1. The currency counts
     *   as not set whenever the price is not set (Komari stores "$" by default).
     * - Price: `-1` → free with price 0; `> 0` → minor units rounded HALF_UP ([priceFromKomari]).
     * - Expiry: local date in [zone]; year `<= 1970` → no due date; more than 100 years after [now] →
     *   permanent with no due date ([dueFromKomari]).
     * - When the due date changes, the anchor day follows it unless the existing anchor already
     *   explains the new date (anchor 31 with Feb 28 stays 31).
     * - [existing] `== null`: builds a new [Category.VPS] subscription; locks are ignored (nothing to
     *   protect) and every field is listed in [KomariMergeResult.changes] with `old = null`. Missing
     *   node values default to name = uuid, price 0, currency [defaultCurrency], cycle [DEFAULT_NEW_CYCLE].
     * - [existingTraffic] `== null`: node traffic values are applied even when locked.
     */
    fun merge(
        existing: Subscription?,
        existingTraffic: VpsTrafficFields?,
        node: KomariNodeSnapshot,
        lockedFields: Int,
        zone: ZoneId,
        now: Instant,
        defaultCurrency: String = "",
    ): KomariMergeResult {
        val nodeName = node.name.trim().ifEmpty { null }
        val nodePrice = priceFromKomari(node.price)
        // Komari fills `currency` with its default "$" even when no price was entered, so the
        // currency only means something together with a price.
        val nodeCurrency = node.currency.trim().ifEmpty { null }?.takeIf { nodePrice != null }
        val nodeCycle = KomariCycleMapper.fromDays(node.billingCycleDays)
        val nodeDue = dueFromKomari(node.expiredAt, zone, now)
        val nodeLimit = node.trafficLimitBytes.coerceAtLeast(0)
        val nodeLimitType = TrafficLimitType.parse(node.trafficLimitType)

        if (existing == null) {
            return createNew(node, nodeName, nodePrice, nodeCurrency ?: defaultCurrency.trim(), nodeCycle, nodeDue, nodeLimit, nodeLimitType)
        }

        fun open(field: LockedField) = !field.isLockedIn(lockedFields)

        val oldPrice = MergePrice(existing.priceMinor, existing.isFree)
        val oldDue = MergeDue(existing.nextDue, existing.isPermanent)

        val name = if (open(LockedField.NAME) && nodeName != null) nodeName else existing.name
        val price = if (open(LockedField.PRICE) && nodePrice != null) nodePrice else oldPrice
        val currency = if (open(LockedField.CURRENCY) && nodeCurrency != null) nodeCurrency else existing.currency
        val cycleFromNode = open(LockedField.CYCLE) && nodeCycle != null
        val cycle = if (cycleFromNode) nodeCycle!! else existing.cycle
        val due = if (open(LockedField.DUE_DATE)) nodeDue else oldDue
        val autoRenew = if (open(LockedField.AUTO_RENEW)) node.autoRenewal else existing.autoRenew
        val anchorDay = if (due == oldDue) existing.anchorDay else anchorFor(due.date, existing.anchorDay)

        val subscription = existing.copy(
            name = name,
            priceMinor = price.minor,
            isFree = price.isFree,
            currency = currency,
            cycle = cycle,
            cycleKnown = existing.cycleKnown || cycleFromNode,
            nextDue = due.date,
            anchorDay = anchorDay,
            isPermanent = due.isPermanent,
            autoRenew = autoRenew,
        )

        val oldLimit = existingTraffic?.limitBytes
        val oldLimitType = existingTraffic?.limitType
        val limit = if (oldLimit == null || open(LockedField.TRAFFIC_LIMIT)) nodeLimit else oldLimit
        val limitType =
            if (oldLimitType == null || open(LockedField.TRAFFIC_LIMIT_TYPE)) nodeLimitType else oldLimitType

        val changes = buildList {
            if (name != existing.name) add(FieldChange.Name(existing.name, name))
            if (price != oldPrice) add(FieldChange.Price(oldPrice, price))
            if (currency != existing.currency) add(FieldChange.Currency(existing.currency, currency))
            if (cycle != existing.cycle || subscription.cycleKnown != existing.cycleKnown) {
                add(FieldChange.Cycle(existing.cycle.takeIf { existing.cycleKnown }, cycle))
            }
            if (due != oldDue) add(FieldChange.DueDate(oldDue, due))
            if (autoRenew != existing.autoRenew) add(FieldChange.AutoRenew(existing.autoRenew, autoRenew))
            if (limit != oldLimit) add(FieldChange.TrafficLimit(oldLimit, limit))
            if (limitType != oldLimitType) add(FieldChange.LimitType(oldLimitType, limitType))
        }
        return KomariMergeResult(subscription, VpsTrafficFields(limit, limitType), changes, isNew = false)
    }

    /**
     * Komari price (major units) to [MergePrice]: `-1` → free with 0; `> 0` → minor units rounded
     * HALF_UP on the decimal value (12.345 → 1235, 0.1 → 10); `0` (Komari: "not shown"), any other
     * negative, NaN, infinity or a value too large for a `Long` → `null` ("not set", do not sync).
     */
    fun priceFromKomari(price: Double): MergePrice? {
        if (price == FREE_PRICE) return MergePrice(0, isFree = true)
        if (price.isNaN() || price.isInfinite() || price <= UNSET_PRICE) return null
        val minor = BigDecimal.valueOf(price).movePointRight(2).setScale(0, RoundingMode.HALF_UP)
        if (minor > BigDecimal.valueOf(Long.MAX_VALUE)) return null
        return MergePrice(minor.longValueExact(), isFree = false)
    }

    /**
     * Komari expiry to [MergeDue] using the local date in [zone]:
     * `null` or year `<= 1970` → no due date; later than [now] + 100 years (in [zone]) → permanent,
     * no date; otherwise that date.
     */
    fun dueFromKomari(expiredAt: Instant?, zone: ZoneId, now: Instant): MergeDue {
        if (expiredAt == null) return MergeDue(null, isPermanent = false)
        val date = try {
            expiredAt.atZone(zone).toLocalDate()
        } catch (ignored: DateTimeException) {
            // Beyond LocalDateTime's range: far past means none, far future means permanent.
            return MergeDue(null, isPermanent = expiredAt.epochSecond > 0)
        }
        if (date.year <= NO_DUE_MAX_YEAR) return MergeDue(null, isPermanent = false)
        val longTermFrom = try {
            now.atZone(zone).plusYears(PERMANENT_AFTER_YEARS).toInstant()
        } catch (ignored: DateTimeException) {
            Instant.MAX
        }
        return if (expiredAt.isAfter(longTermFrom)) MergeDue(null, isPermanent = true) else MergeDue(date, isPermanent = false)
    }

    private fun createNew(
        node: KomariNodeSnapshot,
        nodeName: String?,
        nodePrice: MergePrice?,
        currency: String,
        nodeCycle: BillingCycle?,
        nodeDue: MergeDue,
        nodeLimit: Long,
        nodeLimitType: TrafficLimitType,
    ): KomariMergeResult {
        val name = nodeName ?: node.uuid.trim().ifEmpty { "VPS" }
        val price = nodePrice ?: MergePrice(0, isFree = false)
        val cycle = nodeCycle ?: DEFAULT_NEW_CYCLE
        val subscription = Subscription(
            name = name,
            category = Category.VPS,
            priceMinor = price.minor,
            isFree = price.isFree,
            currency = currency,
            cycle = cycle,
            // Komari had no billing cycle: the monthly placeholder is not shown as "月付".
            cycleKnown = nodeCycle != null,
            nextDue = nodeDue.date,
            anchorDay = nodeDue.date?.dayOfMonth,
            isPermanent = nodeDue.isPermanent,
            autoRenew = node.autoRenewal,
        )
        val changes = listOf(
            FieldChange.Name(null, name),
            FieldChange.Price(null, price),
            FieldChange.Currency(null, currency),
            FieldChange.Cycle(null, cycle),
            FieldChange.DueDate(null, nodeDue),
            FieldChange.AutoRenew(null, node.autoRenewal),
            FieldChange.TrafficLimit(null, nodeLimit),
            FieldChange.LimitType(null, nodeLimitType),
        )
        return KomariMergeResult(subscription, VpsTrafficFields(nodeLimit, nodeLimitType), changes, isNew = true)
    }

    /**
     * Anchor day after the due date changed to [newDue]: keeps [oldAnchor] when it already yields
     * [newDue] (`day == min(anchor, lengthOfMonth)`), otherwise uses [newDue]'s day.
     * Keeps [oldAnchor] when there is no new date.
     */
    private fun anchorFor(newDue: LocalDate?, oldAnchor: Int?): Int? {
        if (newDue == null) return oldAnchor
        val monthLength = YearMonth.from(newDue).lengthOfMonth()
        if (oldAnchor != null && newDue.dayOfMonth == minOf(oldAnchor, monthLength)) return oldAnchor
        return newDue.dayOfMonth
    }

    /**
     * True when the panel has neither a price nor a due date for [node] (Komari's defaults: price 0,
     * no expiry). Such nodes import as "to be completed" subscriptions.
     */
    fun lacksBillingInfo(node: KomariNodeSnapshot, zone: ZoneId, now: Instant): Boolean {
        val due = dueFromKomari(node.expiredAt, zone, now)
        return priceFromKomari(node.price) == null && due.date == null && !due.isPermanent
    }
}
