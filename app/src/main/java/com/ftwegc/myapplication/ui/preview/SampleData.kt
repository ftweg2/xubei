package com.ftwegc.myapplication.ui.preview

import com.ftwegc.myapplication.domain.calc.TrafficCalculator
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.VpsConfig
import com.ftwegc.myapplication.ui.common.SubscriptionUi
import com.ftwegc.myapplication.ui.common.toUi
import java.time.Instant
import java.time.LocalDate

/**
 * Sample data for previews and tests only. Never written to the app database.
 */
object SampleData {

    val today: LocalDate = LocalDate.of(2026, 9, 28)

    private const val GIB = 1L shl 30
    private const val TIB = 1L shl 40

    val chatGpt = Subscription(
        id = 1, name = "ChatGPT Plus", category = Category.AI, priceMinor = 2_000, currency = "USD",
        cycle = BillingCycle.MONTHLY, nextDue = today.plusDays(3), anchorDay = 1, autoRenew = true,
    )
    val claude = Subscription(
        id = 2, name = "Claude Pro", category = Category.AI, priceMinor = 14_800, currency = "CNY",
        cycle = BillingCycle.MONTHLY, nextDue = today.plusDays(16), anchorDay = 14,
    )
    val vpsTokyo = Subscription(
        id = 3, name = "东京 CN2 GIA", category = Category.VPS, priceMinor = 49_900, currency = "CNY",
        cycle = BillingCycle.ANNUAL, nextDue = today.plusDays(58), anchorDay = 25, autoRenew = false,
        note = "搬瓦工 · 年付优惠",
    )
    val vpsLa = Subscription(
        id = 4, name = "洛杉矶 9929", category = Category.VPS, priceMinor = 1_299, currency = "USD",
        cycle = BillingCycle.QUARTERLY, nextDue = today.minusDays(2), anchorDay = 26,
    )
    val icloud = Subscription(
        id = 5, name = "iCloud+ 200GB", category = Category.OTHER, priceMinor = 2_100, currency = "CNY",
        cycle = BillingCycle.MONTHLY, nextDue = today, anchorDay = 28, autoRenew = true,
    )
    val domain = Subscription(
        id = 6, name = "域名 vluer.app", category = Category.OTHER, priceMinor = 9_800, currency = "CNY",
        cycle = BillingCycle.ANNUAL, nextDue = today.plusDays(210), archived = true,
    )

    val tokyoVps = VpsConfig(
        subscriptionId = 3, komariUuid = "a1b2", komariName = "tokyo-01",
        trafficLimitBytes = TIB, trafficLimitType = TrafficLimitType.MAX,
        countingMode = CountingMode.KOMARI_CUMULATIVE, resetDay = 25, alertPercent = 80,
        lastTotalUp = 180 * GIB, lastTotalDown = 612 * GIB, lastUptimeSec = 86_400,
        lastReportedAt = Instant.parse("2026-09-28T08:00:00Z"),
    )
    val laVps = VpsConfig(
        subscriptionId = 4, trafficLimitBytes = 500 * GIB, trafficLimitType = TrafficLimitType.SUM,
        resetDay = 1,
    )

    val all: List<SubscriptionWithVps> = listOf(
        SubscriptionWithVps(chatGpt, null),
        SubscriptionWithVps(claude, null),
        SubscriptionWithVps(vpsTokyo, tokyoVps),
        SubscriptionWithVps(vpsLa, laVps),
        SubscriptionWithVps(icloud, null),
        SubscriptionWithVps(domain, null),
    )

    val cards: List<SubscriptionUi> = all.map { it.toUi(today, soonDays = 7) }

    /** A VPS imported from a panel where no price or expiry was entered. */
    val incompleteVps = Subscription(
        id = 7, name = "甲骨文-eu-92", category = Category.VPS, priceMinor = 0, currency = "CNY",
        cycle = BillingCycle.MONTHLY,
    )
    val incompleteVpsConfig = VpsConfig(
        subscriptionId = 7, komariUuid = "c3d4", komariName = "甲骨文-eu-92",
        lastTotalUp = 685L * GIB, lastTotalDown = 633L * GIB, lastUptimeSec = 12_186_921,
        lastReportedAt = Instant.parse("2026-09-28T08:00:00Z"),
    )
    val incompleteVpsCard: SubscriptionUi
        get() = SubscriptionWithVps(incompleteVps, incompleteVpsConfig).toUi(today, soonDays = 7)

    /** A card with traffic usage at [percent] of a 1 TB limit (for component previews). */
    fun vpsCardWithTraffic(percent: Double): SubscriptionUi {
        val used = (TIB * percent / 100.0).toLong()
        return cards[2].copy(
            traffic = TrafficCalculator.usage(used / 4, used, TIB, TrafficLimitType.MAX),
        )
    }
}
