package com.ftwegc.myapplication.domain.model

import java.time.LocalDate

/** Subscription category. */
enum class Category { AI, VPS, OTHER }

/**
 * A subscription the user pays for.
 *
 * @property priceMinor price per [cycle] in minor units (price × 100, e.g. "fen"/cents), `>= 0`.
 * @property isFree free subscriptions are excluded from all spend totals regardless of [priceMinor].
 * @property currency free-form currency label, may be a symbol such as "$" or "¥"; no FX conversion is done.
 * @property nextDue next due date (local date), `null` when unknown.
 * @property anchorDay preferred day of month (1–31) for MONTH cycles; keeps a "31st" subscription on
 *   month ends instead of drifting to the 28th after February. `null` means "use the due date's day".
 * @property isPermanent lifetime / long-term valid; has no meaningful due date.
 * @property cycleKnown false while the billing cycle was never filled in (for example a Komari node
 *   without a billing cycle); [cycle] then only holds a placeholder and is not shown.
 * @property reminderDays this subscription's own reminder days (days before the due date, 0 = the
 *   due date itself, sorted descending; empty = never remind), `null` = use the app default.
 * @property iconKey icon picked by the user: a brand id of the built-in icon set, or "letter" for
 *   the generated letter mark; `null` = matched automatically from [name].
 * @property iconColor base color picked by the user (ARGB); `null` = the brand color or a color
 *   derived from [name].
 */
data class Subscription(
    val id: Long = 0,
    val name: String,
    val category: Category,
    val priceMinor: Long,
    val isFree: Boolean = false,
    val currency: String,
    val cycle: BillingCycle,
    val nextDue: LocalDate? = null,
    val anchorDay: Int? = null,
    val isPermanent: Boolean = false,
    val autoRenew: Boolean = false,
    val note: String = "",
    val archived: Boolean = false,
    val cycleKnown: Boolean = true,
    val reminderDays: List<Int>? = null,
    val iconKey: String? = null,
    val iconColor: Int? = null,
) {
    init {
        require(priceMinor >= 0) { "priceMinor must be >= 0, was $priceMinor" }
        require(anchorDay == null || anchorDay in 1..31) { "anchorDay must be in 1..31, was $anchorDay" }
    }
}

/** Reminder day lists: "7,3,1,0" text form, normalized to 0..[MAX] without duplicates, descending. */
object ReminderDays {
    /** Largest supported "days before the due date". */
    const val MAX: Int = 60

    /** Values offered in the pickers. */
    val CHOICES: List<Int> = listOf(30, 14, 7, 3, 1, 0)

    fun normalize(days: Collection<Int>): List<Int> = days.filter { it in 0..MAX }.distinct().sortedDescending()

    fun encode(days: Collection<Int>): String = normalize(days).joinToString(",")

    fun decode(raw: String): List<Int> = normalize(raw.split(',').mapNotNull { it.trim().toIntOrNull() })
}
