package com.ftwegc.myapplication.ui.common

import androidx.compose.runtime.Immutable
import com.ftwegc.myapplication.domain.calc.DueDateCalculator
import com.ftwegc.myapplication.domain.calc.RenewCalculator
import com.ftwegc.myapplication.domain.calc.RenewMode
import com.ftwegc.myapplication.domain.calc.SpendCalculator
import com.ftwegc.myapplication.domain.calc.SpendSchedule
import com.ftwegc.myapplication.ui.brand.MarkSource
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.DueStatus
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.domain.model.TrafficUsage
import com.ftwegc.myapplication.ui.format.DueLabel
import com.ftwegc.myapplication.ui.format.Formatters
import java.time.LocalDate

/** Due-date state of one subscription as shown in badges. */
@Immutable
data class DueInfo(
    val status: DueStatus,
    val label: DueLabel,
    val date: LocalDate?,
) {
    companion object {
        fun of(due: LocalDate?, permanent: Boolean, today: LocalDate, soonDays: Int): DueInfo = DueInfo(
            status = DueDateCalculator.statusOf(due, today, soonDays, permanent),
            label = Formatters.dueLabel(due, today, permanent),
            date = due,
        )
    }
}

/** Display model of a subscription card. */
@Immutable
data class SubscriptionUi(
    val id: Long,
    val name: String,
    val category: Category,
    val priceMinor: Long,
    val isFree: Boolean,
    val currency: String,
    val cycle: BillingCycle,
    val due: DueInfo,
    val autoRenew: Boolean,
    val archived: Boolean,
    /** Monthly-equivalent cost in minor units, `null` when it does not count (free, one-time…). */
    val monthlyMinor: Long?,
    /** Traffic usage for VPS cards, `null` when there is no data yet. */
    val traffic: TrafficUsage? = null,
    /**
     * No price, due date or lifetime flag yet (typically a node imported from a panel where
     * nothing was filled in): shown as "to be completed" instead of a 0 price.
     */
    val needsBillingInfo: Boolean = false,
    /** False while no billing cycle was filled in: the cycle is not shown. */
    val cycleKnown: Boolean = true,
    /** What the subscription's mark shows (icon, color, the VPS's flag). */
    val mark: MarkSource = MarkSource(name, category),
    /** Share of the current cycle that has passed (the due ring), 0..1; 1 when overdue. */
    val elapsed: Float = 0f,
    val isPermanent: Boolean = false,
    /** "标记已续费" makes sense (a recurring, non-permanent subscription). */
    val canRenew: Boolean = false,
    /** Linked Komari node, `null` when none. */
    val komariUuid: String? = null,
    /** New due date when renewed from the old due date / from today (the renew sheet's previews). */
    val renewFromDue: LocalDate? = null,
    val renewFromToday: LocalDate? = null,
)

/** See [SubscriptionUi.needsBillingInfo]. */
fun Subscription.needsBillingInfo(): Boolean =
    priceMinor == 0L && !isFree && nextDue == null && !isPermanent && cycle.unit != com.ftwegc.myapplication.domain.model.CycleUnit.ONCE

/**
 * Builds the card model. Traffic is shown for a VPS with data: Komari's counters (mode 1) or the
 * period the app accumulated (mode 2).
 */
fun SubscriptionWithVps.toUi(today: LocalDate, soonDays: Int): SubscriptionUi {
    val sub = subscription
    val vpsConfig = vps
    val traffic = if (sub.category == Category.VPS && vpsConfig != null) TrafficUi.of(vpsConfig, appPeriod, today).usage else null
    return SubscriptionUi(
        id = sub.id,
        name = sub.name,
        category = sub.category,
        priceMinor = sub.priceMinor,
        isFree = sub.isFree,
        currency = sub.currency,
        cycle = sub.cycle,
        due = DueInfo.of(sub.nextDue, sub.isPermanent, today, soonDays),
        autoRenew = sub.autoRenew,
        archived = sub.archived,
        monthlyMinor = SpendCalculator.monthlyMinor(sub.copy(archived = false)),
        traffic = traffic,
        needsBillingInfo = sub.needsBillingInfo(),
        cycleKnown = sub.cycleKnown,
        mark = MarkSource(sub.name, sub.category, sub.iconKey, sub.iconColor, vpsConfig?.komariRegion),
        elapsed = SpendSchedule.elapsedFraction(sub, today),
        isPermanent = sub.isPermanent,
        canRenew = RenewCalculator.canRenew(sub),
        komariUuid = vpsConfig?.komariUuid?.takeIf { sub.category == Category.VPS },
        renewFromDue = RenewCalculator.renew(sub, today, RenewMode.FROM_DUE)?.nextDue,
        renewFromToday = RenewCalculator.renew(sub, today, RenewMode.FROM_TODAY)?.nextDue,
    )
}
