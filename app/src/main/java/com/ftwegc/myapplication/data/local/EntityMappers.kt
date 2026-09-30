package com.ftwegc.myapplication.data.local

import com.ftwegc.myapplication.data.local.entity.SubscriptionEntity
import com.ftwegc.myapplication.data.local.entity.TrafficPeriodEntity
import com.ftwegc.myapplication.data.local.entity.SubscriptionWithVpsRow
import com.ftwegc.myapplication.data.local.entity.VpsConfigEntity
import com.ftwegc.myapplication.domain.model.AppPeriodUsage
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.CycleUnit
import com.ftwegc.myapplication.domain.model.ReminderDays
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.TrafficPeriod
import com.ftwegc.myapplication.domain.model.VpsConfig
import java.time.Instant
import java.time.LocalDate

/*
 * Entity <-> domain mapping.
 *
 * Reading is lenient so that one bad row can never crash the app: unknown enum names fall back to
 * a default, counts below 1 become 1, negative prices become 0 and out-of-range days are dropped
 * or clamped. Writing is exact, so any valid domain value survives a round trip unchanged.
 */

/** Lenient enum lookup by exact name, [default] for null or unknown names. */
internal inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default

fun SubscriptionEntity.toDomain(): Subscription {
    val unit = enumOrDefault(cycleUnit, CycleUnit.MONTH)
    return Subscription(
        id = id,
        name = name,
        category = enumOrDefault(category, Category.OTHER),
        priceMinor = priceMinor.coerceAtLeast(0),
        isFree = isFree,
        currency = currency,
        cycle = BillingCycle.of(unit, cycleCount.coerceAtLeast(1)),
        nextDue = nextDueDay?.let(LocalDate::ofEpochDay),
        anchorDay = anchorDay?.takeIf { it in 1..31 },
        isPermanent = isPermanent,
        autoRenew = autoRenew,
        note = note,
        archived = archived,
        cycleKnown = cycleKnown || unit == CycleUnit.ONCE,
        reminderDays = reminderDays?.let(ReminderDays::decode),
        iconKey = iconKey?.takeIf { it.isNotBlank() },
        iconColor = iconColor,
    )
}

fun Subscription.toEntity(createdAt: Long, updatedAt: Long): SubscriptionEntity = SubscriptionEntity(
    id = id,
    name = name,
    category = category.name,
    priceMinor = priceMinor,
    isFree = isFree,
    currency = currency,
    cycleUnit = cycle.unit.name,
    cycleCount = cycle.count,
    nextDueDay = nextDue?.toEpochDay(),
    anchorDay = anchorDay,
    isPermanent = isPermanent,
    autoRenew = autoRenew,
    note = note,
    archived = archived,
    cycleKnown = cycleKnown,
    reminderDays = reminderDays?.let(ReminderDays::encode),
    createdAt = createdAt,
    updatedAt = updatedAt,
    iconKey = iconKey,
    iconColor = iconColor,
)

fun VpsConfigEntity.toDomain(): VpsConfig = VpsConfig(
    subscriptionId = subscriptionId,
    komariUuid = komariUuid,
    komariName = komariName,
    komariGroup = komariGroup,
    komariMissing = komariMissing,
    lockedFields = lockedFields,
    trafficLimitBytes = trafficLimitBytes.coerceAtLeast(0),
    trafficLimitType = TrafficLimitType.parse(trafficLimitType),
    countingMode = enumOrDefault(countingMode, CountingMode.KOMARI_CUMULATIVE),
    resetDay = resetDay.coerceIn(1, 31),
    resetZoneId = resetZoneId,
    alertPercent = alertPercent.coerceIn(1, VpsConfig.MAX_ALERT_PERCENT),
    lastTotalUp = lastTotalUp,
    lastTotalDown = lastTotalDown,
    lastUptimeSec = lastUptimeSec,
    lastReportedAt = lastReportedAt?.let(Instant::ofEpochMilli),
    lastFetchAt = lastFetchAt?.let(Instant::ofEpochMilli),
    lastError = lastError,
    appCountingSince = appCountingSince?.let(Instant::ofEpochMilli),
    agentMonthRotate = agentMonthRotate,
    komariRegion = komariRegion?.takeIf { it.isNotBlank() },
)

/** Maps to a row owned by [subscriptionId] (defaults to [VpsConfig.subscriptionId]). */
fun VpsConfig.toEntity(subscriptionId: Long = this.subscriptionId): VpsConfigEntity = VpsConfigEntity(
    subscriptionId = subscriptionId,
    komariUuid = komariUuid,
    komariName = komariName,
    komariGroup = komariGroup,
    komariMissing = komariMissing,
    lockedFields = lockedFields,
    trafficLimitBytes = trafficLimitBytes,
    trafficLimitType = trafficLimitType.name,
    countingMode = countingMode.name,
    resetDay = resetDay,
    resetZoneId = resetZoneId,
    alertPercent = alertPercent,
    lastTotalUp = lastTotalUp,
    lastTotalDown = lastTotalDown,
    lastUptimeSec = lastUptimeSec,
    lastReportedAt = lastReportedAt?.toEpochMilli(),
    lastFetchAt = lastFetchAt?.toEpochMilli(),
    lastError = lastError,
    appCountingSince = appCountingSince?.toEpochMilli(),
    agentMonthRotate = agentMonthRotate,
    komariRegion = komariRegion,
)

fun SubscriptionWithVpsRow.toDomain(appPeriod: AppPeriodUsage? = null): SubscriptionWithVps =
    SubscriptionWithVps(subscription = subscription.toDomain(), vps = vps?.toDomain(), appPeriod = appPeriod)

fun TrafficPeriodEntity.toDomain(): AppPeriodUsage? {
    if (periodEndDay <= periodStartDay) return null
    return AppPeriodUsage(
        period = TrafficPeriod(LocalDate.ofEpochDay(periodStartDay), LocalDate.ofEpochDay(periodEndDay)),
        upBytes = upBytes.coerceAtLeast(0),
        downBytes = downBytes.coerceAtLeast(0),
        initialUsedBytes = initialUsedBytes.coerceAtLeast(0),
        updatedAt = updatedAt.takeIf { it > 0 }?.let(Instant::ofEpochMilli),
    )
}
