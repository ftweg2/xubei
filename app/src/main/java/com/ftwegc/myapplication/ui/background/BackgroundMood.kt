package com.ftwegc.myapplication.ui.background

import androidx.compose.runtime.Immutable
import com.ftwegc.myapplication.domain.calc.DueDateCalculator
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.ui.common.TrafficUi
import java.time.LocalDate

/**
 * What the background color field says without any text.
 *
 * @property dueSoon a subscription is due within [BackgroundMoods.DUE_SOON_DAYS] days (today
 *   included): amber is mixed in.
 * @property alert a subscription has expired or a VPS used up its traffic: coral is mixed in.
 */
@Immutable
data class BackgroundMood(val dueSoon: Boolean = false, val alert: Boolean = false) {
    companion object {
        val Calm = BackgroundMood()
    }
}

/** Derives the [BackgroundMood] from the subscriptions. Pure. */
object BackgroundMoods {

    /** "7 天内到期". */
    const val DUE_SOON_DAYS: Long = 7

    /** Traffic at or above this share of the limit counts as used up. */
    const val TRAFFIC_EXCEEDED_PERCENT: Double = 100.0

    fun of(items: List<SubscriptionWithVps>, today: LocalDate): BackgroundMood {
        var dueSoon = false
        var alert = false
        for (item in items) {
            val sub = item.subscription
            if (sub.archived) continue
            val due = sub.nextDue
            if (due != null && !sub.isPermanent) {
                val days = DueDateCalculator.daysUntil(due, today)
                when {
                    days < 0 -> alert = true
                    days <= DUE_SOON_DAYS -> dueSoon = true
                }
            }
            val vps = item.vps
            if (sub.category == Category.VPS && vps != null && vps.isLinked) {
                val percent = TrafficUi.of(vps, item.appPeriod, today).usage?.percent
                if (percent != null && percent >= TRAFFIC_EXCEEDED_PERCENT) alert = true
            }
        }
        return BackgroundMood(dueSoon = dueSoon, alert = alert)
    }
}
