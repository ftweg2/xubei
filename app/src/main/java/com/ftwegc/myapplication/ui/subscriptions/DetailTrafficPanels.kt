package com.ftwegc.myapplication.ui.subscriptions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.domain.calc.ReminderPlanner
import com.ftwegc.myapplication.domain.calc.TrafficCalculator
import com.ftwegc.myapplication.domain.calc.TrafficTrend
import com.ftwegc.myapplication.domain.model.TrafficUsage
import com.ftwegc.myapplication.domain.model.VpsConfig
import com.ftwegc.myapplication.ui.charts.BarChart
import com.ftwegc.myapplication.ui.charts.BarDatum
import com.ftwegc.myapplication.ui.charts.ChartPlaceholder
import com.ftwegc.myapplication.ui.charts.DualLineChart
import com.ftwegc.myapplication.ui.charts.ForecastLineChart
import com.ftwegc.myapplication.ui.charts.LiquidRing
import com.ftwegc.myapplication.ui.charts.PlaceholderKind
import com.ftwegc.myapplication.ui.charts.SemiGauge
import com.ftwegc.myapplication.ui.charts.chartColors
import com.ftwegc.myapplication.ui.charts.liquidColors
import com.ftwegc.myapplication.ui.common.AppTrafficInfo
import com.ftwegc.myapplication.ui.common.TrafficIssue
import com.ftwegc.myapplication.ui.common.TrafficUi
import com.ftwegc.myapplication.ui.format.Formatters
import com.ftwegc.myapplication.ui.format.countingModeText
import com.ftwegc.myapplication.ui.format.dateText
import com.ftwegc.myapplication.ui.format.limitTypeText
import com.ftwegc.myapplication.ui.format.relativeTimeText
import com.ftwegc.myapplication.ui.glass.GlassAccents
import com.ftwegc.myapplication.ui.glass.GlassLabel
import com.ftwegc.myapplication.ui.glass.GlassPanel
import com.ftwegc.myapplication.ui.glass.GlassPillButton
import com.ftwegc.myapplication.ui.glass.GlassSegmented
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.komari.komariErrorText
import com.ftwegc.myapplication.ui.theme.Motion
import com.ftwegc.myapplication.ui.theme.motionSpec
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class TrafficContent { Unlinked, NoData, Waiting, Data }

/** Relative times on the page tick this often. */
private const val TICK_MILLIS = 30_000L

private const val SECONDS_PER_DAY = 86_400L

/**
 * "本周期流量" (mockup): the liquid ring (the level is the used share; without a limit it shows the
 * amount) and the numbers beside it: used, limit, up, down, remaining. Below: what the numbers
 * mean for the counting mode, the app's own counting (mode 2), and the banners for an offline or
 * missing node, a failed refresh and monthly counters, then the traffic settings.
 */
@Composable
internal fun TrafficPanel(state: SubscriptionDetailUiState, live: DetailLive, onUnlink: () -> Unit) {
    val env = currentGlass()
    val config = state.vps ?: VpsConfig()
    val ui = state.traffic ?: TrafficUi.of(config)
    val content = when {
        ui.usage != null -> TrafficContent.Data
        ui.isAppMode -> TrafficContent.Waiting
        !ui.linked -> TrafficContent.Unlinked
        else -> TrafficContent.NoData
    }
    val usage = ui.usage
    val period = usage?.period ?: ui.app?.period
    val range = period?.let {
        stringResource(R.string.traffic_period_range, dateText(it.start, state.today), dateText(it.endExclusive.minusDays(1), state.today))
    }
    val monthlyCounters = config.agentMonthRotate || state.monthRotateSuspected
    GlassPanel(Modifier.fillMaxWidth()) {
        GlassLabel(stringResource(R.string.detail_traffic_label), trailing = range ?: ui.nodeName)
        AnimatedContent(targetState = content, transitionSpec = com.ftwegc.myapplication.ui.components.crossFadeSpec(), label = "trafficContent") { kind ->
            when (kind) {
                TrafficContent.Unlinked -> PanelMessage(
                    icon = Icons.Rounded.LinkOff,
                    title = stringResource(R.string.traffic_unlinked_title),
                    message = stringResource(if (state.komariConfigured) R.string.traffic_unlinked_message_configured else R.string.traffic_unlinked_message),
                )
                TrafficContent.NoData -> PanelMessage(
                    icon = if (ui.issue == TrafficIssue.Offline) Icons.Rounded.CloudOff else Icons.Rounded.DataUsage,
                    title = stringResource(R.string.traffic_no_data_title),
                    message = stringResource(if (ui.issue == TrafficIssue.Offline) R.string.traffic_no_data_offline_message else R.string.traffic_no_data_message),
                )
                TrafficContent.Waiting -> PanelMessage(
                    icon = Icons.Rounded.Timelapse,
                    title = stringResource(R.string.traffic_app_waiting_title),
                    message = stringResource(if (ui.linked) R.string.traffic_app_waiting_message else R.string.traffic_app_waiting_unlinked),
                )
                TrafficContent.Data -> if (usage != null) {
                    Column {
                        LiquidNumbers(usage, config, state)
                        if (!ui.isAppMode) {
                            ReportedAt(ui.reportedAt, state.now, state.zone)
                            CounterBasis(ui.uptimeSec, config.agentMonthRotate, state.monthRotateSuspected, config.resetDay)
                        } else {
                            ui.app?.let { AppPeriodRows(it, usage, ui.fetchedAt, state) }
                        }
                    }
                }
            }
        }
        if (ui.isAppMode) AccuracyNote()
        AnimatedVisibility(
            visible = ui.isAppMode && monthlyCounters,
            enter = expandVertically(motionSpec(Motion.emphasized())) + fadeIn(motionSpec(Motion.emphasized())),
            exit = shrinkVertically(motionSpec(Motion.emphasizedAccelerate())) + fadeOut(motionSpec(Motion.emphasizedAccelerate())),
        ) {
            Notice(
                icon = Icons.Rounded.RestartAlt,
                text = stringResource(if (config.agentMonthRotate) R.string.traffic_month_rotate_set_warning else R.string.traffic_month_rotate_warning),
                accent = GlassAccents.amber(env.dark),
            )
        }
        IssueNotice(ui, onUnlink)
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KvRow(stringResource(R.string.traffic_limit), if (config.hasLimit) Formatters.formatBytes(config.trafficLimitBytes) else stringResource(R.string.traffic_no_limit))
            KvRow(stringResource(R.string.traffic_limit_type), limitTypeText(config.trafficLimitType))
            KvRow(stringResource(R.string.traffic_counting_mode), countingModeText(config.countingMode))
            KvRow(stringResource(R.string.traffic_reset_day), stringResource(R.string.traffic_reset_day_value, config.resetDay))
            if (config.hasLimit) KvRow(stringResource(R.string.traffic_alert), alertThresholdsText(config.alertPercent))
        }
    }
}

/** The liquid ring and the numbers beside it (mockup `.liquid` 128 px and `.kv`). */
@Composable
private fun LiquidNumbers(usage: TrafficUsage, config: VpsConfig, state: SubscriptionDetailUiState) {
    val env = currentGlass()
    val chart = chartColors()
    val percent = usage.percent
    val fraction = percent?.let { (it / 100).toFloat() }
    val memory = "detail-liquid-${config.subscriptionId}"
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (fraction != null) {
            LiquidRing(
                fraction = fraction.coerceIn(0f, 1f),
                value = Formatters.formatPercent(percent),
                label = stringResource(R.string.traffic_used),
                contentDescription = stringResource(R.string.traffic_percent_desc, Formatters.formatPercent(percent)),
                colors = liquidColors(fraction, chart),
                waveBack = liquidColors(fraction, chart).first().copy(alpha = 0.6f),
                memoryKey = memory,
                valueSize = if (Formatters.formatPercent(percent).length > 4) 26.sp else 32.sp,
            )
        } else {
            // No limit: a calm level, the amount used instead of a share.
            val used = Formatters.formatBytes(usage.usedBytes)
            LiquidRing(
                fraction = UNLIMITED_LEVEL,
                value = used,
                label = stringResource(R.string.traffic_no_limit),
                contentDescription = stringResource(R.string.traffic_used_unlimited, used),
                colors = listOf(chart.blue.copy(alpha = 0.85f), chart.teal.copy(alpha = 0.75f)),
                waveBack = chart.mint.copy(alpha = 0.6f),
                memoryKey = memory,
                valueSize = if (used.length > 6) 20.sp else 24.sp,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            KvRow(stringResource(R.string.traffic_used), Formatters.formatBytes(usage.usedBytes))
            KvRow(stringResource(R.string.traffic_limit_short), if (usage.limitBytes > 0) Formatters.formatBytes(usage.limitBytes) else stringResource(R.string.traffic_no_limit))
            KvRow(stringResource(R.string.traffic_up), Formatters.formatBytes(usage.upBytes))
            KvRow(stringResource(R.string.traffic_down), Formatters.formatBytes(usage.downBytes))
            if (usage.limitBytes > 0) {
                val left = (usage.limitBytes - usage.usedBytes)
                val color = when {
                    fraction == null -> env.ink.primary
                    fraction >= 0.9f -> GlassAccents.coral(env.dark)
                    fraction >= 0.7f -> GlassAccents.amber(env.dark)
                    else -> env.ink.primary
                }
                KvRow(
                    stringResource(if (left >= 0) R.string.traffic_remaining else R.string.traffic_over),
                    Formatters.formatBytes(kotlin.math.abs(left)),
                    valueColor = color,
                )
            }
        }
    }
}

/** Level of the liquid when there is no limit. */
private const val UNLIMITED_LEVEL = 0.55f

@Composable
private fun ReportedAt(reportedAt: Instant?, now: Instant, zone: java.time.ZoneId) {
    if (reportedAt == null) return
    val env = currentGlass()
    val clock = com.ftwegc.myapplication.ui.common.appClock()
    val liveNow by produceState(now, now) {
        while (true) {
            delay(TICK_MILLIS)
            value = maxOf(clock.instant(), now)
        }
    }
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = env.ink.secondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.traffic_updated_at, relativeTimeText(Formatters.relativeTime(reportedAt, liveNow, zone))),
            color = env.ink.secondary,
            fontSize = 12.sp,
        )
    }
}

/**
 * What the numbers mean in mode 1: since boot, or since the reset day with `--month-rotate`
 * ([agentMonthRotate], said by the user) or apparently so ([suspected]); the how-to unfolds.
 */
@Composable
private fun CounterBasis(uptimeSec: Long?, agentMonthRotate: Boolean, suspected: Boolean, resetDay: Int) {
    val basis = when {
        agentMonthRotate -> stringResource(R.string.traffic_basis_month, resetDay)
        suspected -> stringResource(R.string.traffic_basis_month_rotate_suspected)
        uptimeSec == null -> stringResource(R.string.traffic_basis_unknown)
        uptimeSec >= SECONDS_PER_DAY -> stringResource(R.string.traffic_basis_uptime_days, (uptimeSec / SECONDS_PER_DAY).toInt())
        else -> stringResource(R.string.traffic_basis_uptime_hours, (uptimeSec / 3_600).toInt().coerceAtLeast(1))
    }
    Expandable(
        icon = if (agentMonthRotate) Icons.Rounded.DateRange else Icons.Rounded.RestartAlt,
        title = basis,
        body = if (agentMonthRotate) null else stringResource(if (suspected) R.string.traffic_basis_help_suspected else R.string.traffic_basis_help),
        showLabel = stringResource(R.string.traffic_basis_help_show),
        hideLabel = stringResource(R.string.traffic_basis_help_hide),
    )
}

/** Mode 2: the period, how "used" is made up, when counting began and the last refresh. */
@Composable
private fun AppPeriodRows(app: AppTrafficInfo, usage: TrafficUsage, fetchedAt: Instant?, state: SubscriptionDetailUiState) {
    val env = currentGlass()
    val clock = com.ftwegc.myapplication.ui.common.appClock()
    val liveNow by produceState(state.now, state.now) {
        while (true) {
            delay(TICK_MILLIS)
            value = maxOf(clock.instant(), state.now)
        }
    }
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        app.period?.let { period ->
            IconKv(Icons.Rounded.DateRange, stringResource(R.string.traffic_app_period), stringResource(R.string.traffic_period_range, dateText(period.start, state.today), dateText(period.endExclusive.minusDays(1), state.today)))
        }
        IconKv(
            Icons.Rounded.DataUsage,
            stringResource(R.string.traffic_app_used),
            if (app.initialUsedBytes > 0) stringResource(R.string.traffic_app_used_parts, Formatters.formatBytes(app.initialUsedBytes), Formatters.formatBytes(app.accumulatedUsedBytes))
            else Formatters.formatBytes(usage.usedBytes),
        )
        app.countingSince?.let { IconKv(Icons.Rounded.PlayCircleOutline, stringResource(R.string.traffic_app_since), relativeTimeText(Formatters.relativeTime(it, liveNow, state.zone))) }
        IconKv(
            Icons.Rounded.Schedule,
            stringResource(R.string.traffic_app_last_refresh),
            fetchedAt?.let { relativeTimeText(Formatters.relativeTime(it, liveNow, state.zone)) } ?: stringResource(R.string.traffic_app_never),
        )
    }
    val hint = when {
        app.waitingForNewPeriod -> R.string.traffic_app_new_period
        app.waitingForBaseline -> R.string.traffic_app_baseline_set
        else -> null
    }
    if (hint != null) Notice(Icons.Rounded.Info, stringResource(hint), GlassAccents.teal(env.dark))
}

@Composable
private fun IconKv(icon: ImageVector, label: String, value: String) {
    val env = currentGlass()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = env.ink.secondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(7.dp))
        Box(Modifier.weight(1f)) { KvRow(label, value) }
    }
}

/** Mode 2: what limits the accuracy, behind an expandable row. */
@Composable
private fun AccuracyNote() {
    Expandable(
        icon = Icons.Rounded.Info,
        title = stringResource(R.string.traffic_app_accuracy_title),
        body = stringResource(R.string.traffic_app_accuracy_body),
        showLabel = stringResource(R.string.traffic_note_show),
        hideLabel = stringResource(R.string.traffic_note_hide),
    )
}

/** A flat glass row inside a panel that unfolds a longer explanation. */
@Composable
private fun Expandable(icon: ImageVector, title: String, body: String?, showLabel: String, hideLabel: String) {
    val env = currentGlass()
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, motionSpec(Motion.emphasized()), label = "expandArrow")
    val teal = GlassAccents.teal(env.dark)
    Column(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(env.ink.track),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (body != null) Modifier.clickable(role = Role.Button) { expanded = !expanded } else Modifier)
                .heightIn(min = 44.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = env.ink.secondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, color = env.ink.primary, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            if (body != null) {
                Text(if (expanded) hideLabel else showLabel, color = teal, fontSize = 12.sp)
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = teal,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = arrow },
                )
            }
        }
        AnimatedVisibility(
            visible = expanded && body != null,
            enter = expandVertically(motionSpec(Motion.emphasized())) + fadeIn(motionSpec(Motion.emphasized())),
            exit = shrinkVertically(motionSpec(Motion.emphasizedAccelerate())) + fadeOut(motionSpec(Motion.emphasizedAccelerate())),
        ) {
            Text(
                body.orEmpty(),
                color = env.ink.secondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        }
    }
}

/** Offline, gone from the panel, or a failed refresh; stale numbers stay visible above. */
@Composable
private fun IssueNotice(ui: TrafficUi, onUnlink: () -> Unit) {
    val env = currentGlass()
    val issue = ui.issue
    AnimatedVisibility(
        visible = issue != null,
        enter = expandVertically(motionSpec(Motion.emphasized())) + fadeIn(motionSpec(Motion.emphasized())),
        exit = shrinkVertically(motionSpec(Motion.emphasizedAccelerate())) + fadeOut(motionSpec(Motion.emphasizedAccelerate())),
    ) {
        val hasData = ui.usage != null
        val unlink = stringResource(R.string.traffic_unlink_action)
        when (issue) {
            null -> Unit
            TrafficIssue.Offline -> Notice(
                icon = Icons.Rounded.CloudOff,
                text = when {
                    ui.longOffline -> stringResource(R.string.traffic_offline_long)
                    hasData -> stringResource(R.string.traffic_offline)
                    else -> stringResource(R.string.traffic_offline_no_data)
                },
                accent = GlassAccents.amber(env.dark),
                action = unlink.takeIf { ui.longOffline },
                onAction = onUnlink,
            )
            TrafficIssue.Missing -> Notice(
                icon = Icons.Rounded.CloudOff,
                text = stringResource(R.string.traffic_node_missing, ui.nodeName.orEmpty()),
                accent = env.ink.secondary,
                action = unlink,
                onAction = onUnlink,
            )
            is TrafficIssue.Failed -> {
                val reason = komariErrorText(issue.error)
                Notice(
                    icon = Icons.Rounded.SyncProblem,
                    text = if (hasData) stringResource(R.string.traffic_fetch_failed, reason) else stringResource(R.string.traffic_fetch_failed_no_data, reason),
                    accent = GlassAccents.coral(env.dark),
                )
            }
        }
    }
}

/** A flat, tinted notice inside a panel: an icon in the [accent] color, the text, an optional action. */
@Composable
private fun Notice(icon: ImageVector, text: String, accent: Color, action: String? = null, onAction: (() -> Unit)? = null) {
    val env = currentGlass()
    Column(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = if (env.dark) 0.14f else 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(9.dp))
            Text(text, color = env.ink.primary, fontSize = 12.5.sp, lineHeight = 18.sp)
        }
        if (action != null && onAction != null) {
            GlassPillButton(action, onAction, Modifier.padding(top = 8.dp).align(Alignment.End)) {
                Icon(Icons.Rounded.LinkOff, contentDescription = null, tint = env.ink.primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PanelMessage(icon: ImageVector, title: String, message: String) {
    val env = currentGlass()
    val teal = GlassAccents.teal(env.dark)
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(teal.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = teal, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = env.ink.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(message, color = env.ink.secondary, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** "80% · 95% · 100%" for the thresholds that notify. */
@Composable
private fun alertThresholdsText(alertPercent: Int): String {
    val resources = androidx.compose.ui.platform.LocalResources.current
    return ReminderPlanner.trafficThresholds(alertPercent).joinToString(" · ") { resources.getString(R.string.traffic_alert_value, it) }
}

/**
 * "用量预测" (mockup): the used amount this period as a filled curve, the dashed projection to the
 * end of the period, the dashed limit line; the title says when the limit runs out at this rate.
 */
@Composable
internal fun ForecastPanel(state: SubscriptionDetailUiState) {
    val forecast = state.forecast ?: return
    val env = currentGlass()
    val chart = chartColors()
    val limit = forecast.limitBytes
    val trailing = when {
        forecast.exceeded -> stringResource(R.string.forecast_exceeded)
        forecast.exhaustDate != null -> stringResource(R.string.forecast_runs_out, dateText(forecast.exhaustDate, state.today))
        limit != null -> stringResource(R.string.forecast_enough)
        else -> stringResource(R.string.forecast_projected, Formatters.formatBytes(forecast.projectedEnd))
    }
    val color = when {
        forecast.runsOut -> chart.coral
        limit != null && forecast.projectedEnd >= limit * 0.8 -> chart.amber
        else -> chart.teal
    }
    val format = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)
    val description = stringResource(
        R.string.forecast_desc,
        Formatters.formatBytes(forecast.usedBytes),
        Formatters.formatBytes(forecast.ratePerDay.toLong()),
        trailing,
    )
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.forecast_label), color = env.ink.secondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                trailing,
                color = when {
                    forecast.runsOut -> GlassAccents.coral(env.dark)
                    else -> GlassAccents.mint(env.dark)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        ForecastLineChart(
            actual = forecast.cumulative.map { it.toFloat() },
            totalDays = forecast.totalDays,
            contentDescription = description,
            limit = limit?.toFloat(),
            limitLabel = limit?.let { stringResource(R.string.forecast_limit, Formatters.formatBytes(it)) },
            startLabel = forecast.period.start.format(format),
            todayLabel = stringResource(R.string.gallery_today),
            color = color,
            memoryKey = "detail-forecast-${state.subscription?.id}",
            emptyText = stringResource(R.string.forecast_too_early),
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            stringResource(R.string.forecast_rate, Formatters.formatBytes(forecast.ratePerDay.toLong())),
            color = env.ink.secondary,
            fontSize = 11.5.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * "实时" (mockup): upload and download of the last minute as two lines with the current speeds in
 * the title, and CPU, memory and disk as half-circle gauges. Refreshed every few seconds while
 * the page is in front.
 */
@Composable
internal fun LivePanel(state: SubscriptionDetailUiState, live: DetailLive) {
    val env = currentGlass()
    val chart = chartColors()
    val series = live.series
    GlassPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.live_label), color = env.ink.secondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (series != null && series.online) {
                LegendDot(chart.mint)
                Text(stringResource(R.string.live_up, Formatters.formatBytes(series.latestUp)), color = env.ink.secondary, fontSize = 12.sp)
                Spacer(Modifier.width(10.dp))
                LegendDot(chart.violet)
                Text(stringResource(R.string.live_down, Formatters.formatBytes(series.latestDown)), color = env.ink.secondary, fontSize = 12.sp)
            }
        }
        when {
            series == null && !live.loaded -> ChartPlaceholder(stringResource(R.string.home_node_waiting), Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 64.dp), PlaceholderKind.Line)
            series == null || !series.online -> ChartPlaceholder(
                stringResource(if (live.failed) R.string.live_failed else R.string.live_offline),
                Modifier.padding(top = 8.dp).fillMaxWidth().heightIn(min = 64.dp),
                PlaceholderKind.Line,
            )
            else -> {
                DualLineChart(
                    first = series.up.map { it.toFloat() },
                    second = series.down.map { it.toFloat() },
                    contentDescription = stringResource(R.string.live_desc, Formatters.formatBytes(series.latestUp), Formatters.formatBytes(series.latestDown)),
                    memoryKey = "detail-live-${state.subscription?.id}",
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val cpu = series.latestCpu?.let { (it / 100).toFloat() }
                    val ram = series.latestRam?.toFloat()
                    val disk = series.latestDisk?.toFloat()
                    Gauge(cpu, "CPU", chart.teal, Modifier.weight(1f), "live-cpu")
                    Gauge(ram, stringResource(R.string.live_memory), chart.amber, Modifier.weight(1f), "live-ram")
                    Gauge(disk, stringResource(R.string.live_disk), chart.blue, Modifier.weight(1f), "live-disk")
                }
            }
        }
    }
}

@Composable
private fun Gauge(fraction: Float?, label: String, color: Color, modifier: Modifier, key: String) {
    val text = fraction?.let { "${(it * 100).toInt().coerceIn(0, 100)}%" } ?: "–"
    SemiGauge(
        fraction = fraction?.coerceIn(0f, 1f),
        value = text,
        label = label,
        contentDescription = "$label $text",
        color = color,
        memoryKey = key,
        modifier = modifier,
    )
}

@Composable
private fun LegendDot(color: Color) {
    androidx.compose.foundation.Canvas(Modifier.padding(end = 4.dp).size(8.dp)) { drawCircle(color) }
}

/**
 * "节点信息" (Komari data that was not shown before): load, processes, TCP / UDP connections,
 * system and kernel, CPU model, virtualization and uptime.
 */
@Composable
internal fun NodeInfoPanel(state: SubscriptionDetailUiState, live: DetailLive) {
    val latest = live.series?.latest
    val machine = live.machine
    if (latest == null && machine == null) return
    val dash = "–"
    GlassPanel(Modifier.fillMaxWidth()) {
        GlassLabel(stringResource(R.string.node_info_label), trailing = state.vps?.komariName)
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (latest != null) {
                KvRow(stringResource(R.string.node_info_load), "%.2f · %.2f · %.2f".format(Locale.ROOT, latest.load1, latest.load5, latest.load15))
                KvRow(stringResource(R.string.node_info_process), latest.process.toString())
                KvRow(stringResource(R.string.node_info_connections), stringResource(R.string.node_info_connections_value, latest.tcp, latest.udp))
                latest.uptimeSec?.let { KvRow(stringResource(R.string.node_info_uptime), uptimeText(it)) }
            }
            if (machine != null) {
                val system = listOf(machine.os, machine.kernelVersion).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { dash }
                KvRow(stringResource(R.string.node_info_system), system)
                val cpu = buildString {
                    append(machine.cpuName.ifBlank { dash })
                    if (machine.cpuCores > 0) append(" ×").append(machine.cpuCores)
                }
                KvRow(stringResource(R.string.node_info_cpu), cpu)
                KvRow(stringResource(R.string.node_info_virtualization), listOf(machine.virtualization, machine.arch).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { dash })
            }
        }
    }
}

@Composable
private fun uptimeText(seconds: Long): String {
    val days = seconds / SECONDS_PER_DAY
    val hours = (seconds % SECONDS_PER_DAY) / 3_600
    return if (days > 0) stringResource(R.string.node_info_uptime_days, days.toInt(), hours.toInt()) else stringResource(R.string.node_info_uptime_hours, hours.toInt().coerceAtLeast(0))
}

/** Trend ranges offered, in days. */
private val TrendRanges = listOf(7, 14, 30)

/**
 * "每日用量" (mockup): upload and download stacked per day for the last 7 / 14 / 30 days, the daily
 * average in the title; tap a bar to read its day.
 */
@Composable
internal fun DailyUsagePanel(state: SubscriptionDetailUiState) {
    val vps = state.vps ?: return
    val traffic = state.traffic
    if (state.trendPoints.isEmpty() && !(vps.isLinked && traffic?.usage != null)) return
    val env = currentGlass()
    val chart = chartColors()
    var range by rememberSaveable { mutableIntStateOf(TrafficTrend.DEFAULT_DAYS) }
    val trend = remember(state.trendPoints, state.zone, state.today, range) { TrafficTrend.daily(state.trendPoints, state.zone, state.today, range) }
    val used = remember(trend) { trend.days.map { it.usedBytes(vps.trafficLimitType) } }
    val average = if (used.isNotEmpty()) used.sum() / used.size else 0L
    var selected by remember(trend) { mutableIntStateOf(-1) }
    val format = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)
    GlassPanel(Modifier.fillMaxWidth()) {
        GlassLabel(
            stringResource(R.string.trend_title),
            trailing = if (trend.enoughData) stringResource(R.string.trend_average_value, Formatters.formatBytes(average)) else null,
        )
        GlassSegmented(
            labels = TrendRanges.map { stringResource(R.string.trend_range_days, it) },
            selected = TrendRanges.indexOf(range),
            onSelect = { range = TrendRanges[it] },
            modifier = Modifier.padding(top = 10.dp),
        )
        if (!trend.enoughData) {
            ChartPlaceholder(
                stringResource(if (trend.days.isNotEmpty()) R.string.trend_not_enough_message_some else R.string.trend_not_enough_message),
                Modifier.padding(top = 10.dp).fillMaxWidth().heightIn(min = 86.dp),
                PlaceholderKind.Bars,
            )
            return@GlassPanel
        }
        val todayIndex = trend.days.lastIndex
        BarChart(
            bars = trend.days.mapIndexed { i, d -> BarDatum(listOf(d.upBytes.toFloat(), d.downBytes.toFloat()), current = i == todayIndex && selected < 0) },
            contentDescription = stringResource(
                R.string.trend_summary_desc,
                trend.days.size,
                Formatters.formatBytes(average),
                Formatters.formatBytes(used.maxOrNull() ?: 0L),
                trend.days.getOrNull(used.indexOf(used.maxOrNull() ?: 0L))?.date?.format(format).orEmpty(),
            ),
            height = 76.dp,
            gap = if (trend.days.size > 20) 3.dp else 5.dp,
            stackColors = listOf(chart.mint, chart.violet),
            selected = selected,
            onSelect = { selected = it },
            memoryKey = "detail-daily-${vps.subscriptionId}-$range",
            modifier = Modifier.padding(top = 12.dp),
        )
        val day = trend.days.getOrNull(if (selected >= 0) selected else todayIndex)
        if (selected < 0 && day != null && day.upBytes + day.downBytes == 0L) {
            // Nothing counted today yet: say so (and point at the last day with traffic) instead of "今天 0 B".
            val last = trend.days.dropLast(1).lastOrNull { it.upBytes + it.downBytes > 0L }
            Text(
                if (last != null) {
                    stringResource(R.string.trend_today_none_last, last.date.format(format), Formatters.formatBytes(last.usedBytes(vps.trafficLimitType)))
                } else {
                    stringResource(R.string.trend_today_none)
                },
                color = env.ink.secondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@GlassPanel
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                day?.let { if (selected >= 0) it.date.format(format) else stringResource(R.string.trend_today) }.orEmpty(),
                color = env.ink.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            LegendDot(chart.mint)
            Text(stringResource(R.string.live_up_amount, Formatters.formatBytes(day?.upBytes ?: 0)), color = env.ink.secondary, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            LegendDot(chart.violet)
            Text(stringResource(R.string.live_down_amount, Formatters.formatBytes(day?.downBytes ?: 0)), color = env.ink.secondary, fontSize = 12.sp)
        }
    }
}

/** "历史周期" (mode 2): earlier periods as bars against the limit, newest on the right. */
@Composable
internal fun HistoryPanel(state: SubscriptionDetailUiState) {
    val vps = state.vps ?: return
    if (state.traffic?.isAppMode != true || state.history.isEmpty()) return
    val env = currentGlass()
    val shown = state.history.take(MAX_HISTORY).reversed()
    val used = shown.map { TrafficCalculator.appUsage(it.upBytes, it.downBytes, it.initialUsedBytes, vps.trafficLimitBytes, vps.trafficLimitType).usedBytes }
    val format = DateTimeFormatter.ofPattern("M/d", Locale.CHINA)
    var selected by remember(shown) { mutableIntStateOf(shown.lastIndex) }
    val max = if (vps.trafficLimitBytes > 0) maxOf(vps.trafficLimitBytes, used.maxOrNull() ?: 0L) else (used.maxOrNull() ?: 1L)
    GlassPanel(Modifier.fillMaxWidth()) {
        GlassLabel(
            stringResource(R.string.history_title),
            trailing = if (vps.trafficLimitBytes > 0) stringResource(R.string.forecast_limit, Formatters.formatBytes(vps.trafficLimitBytes)) else null,
        )
        BarChart(
            bars = used.mapIndexed { i, v -> BarDatum(v.toFloat(), current = i == shown.lastIndex) },
            contentDescription = shown.indices.joinToString("，") { i ->
                "${shown[i].period.start.format(format)} ${Formatters.formatBytes(used[i])}"
            },
            height = 76.dp,
            maxValue = max.toFloat(),
            selected = selected,
            onSelect = { selected = if (it < 0) shown.lastIndex else it },
            memoryKey = "detail-history-${vps.subscriptionId}",
            modifier = Modifier.padding(top = 12.dp),
        )
        val i = selected.coerceIn(0, shown.lastIndex)
        val period = shown[i].period
        val percent = if (vps.trafficLimitBytes > 0) " · " + Formatters.formatPercent(used[i] * 100.0 / vps.trafficLimitBytes) else ""
        Text(
            stringResource(R.string.traffic_period_range, dateText(period.start, state.today), dateText(period.endExclusive.minusDays(1), state.today)) +
                "  " + Formatters.formatBytes(used[i]) + percent,
            color = env.ink.secondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Earlier periods shown at most. */
private const val MAX_HISTORY = 6
