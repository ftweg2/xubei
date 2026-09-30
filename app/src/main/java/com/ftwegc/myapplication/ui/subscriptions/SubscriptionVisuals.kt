package com.ftwegc.myapplication.ui.subscriptions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.ftwegc.myapplication.ui.glass.GlassConfirmDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.domain.calc.RenewMode
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.TrafficUsage
import com.ftwegc.myapplication.ui.brand.VluerLogo
import com.ftwegc.myapplication.ui.charts.ChartColors
import com.ftwegc.myapplication.ui.charts.chartColors
import com.ftwegc.myapplication.ui.charts.rememberAnimatedValues
import com.ftwegc.myapplication.ui.common.SubscriptionUi
import com.ftwegc.myapplication.ui.format.DueLabel
import com.ftwegc.myapplication.ui.format.Formatters
import com.ftwegc.myapplication.ui.format.cycleText
import com.ftwegc.myapplication.ui.format.dateText
import com.ftwegc.myapplication.ui.glass.GlassAccents
import com.ftwegc.myapplication.ui.glass.GlassDefaults
import com.ftwegc.myapplication.ui.glass.GlassPillButton
import com.ftwegc.myapplication.ui.glass.GlassSheet
import com.ftwegc.myapplication.ui.glass.GlassSurface
import com.ftwegc.myapplication.ui.glass.GlassThickness
import com.ftwegc.myapplication.ui.glass.GrowOrigin
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.theme.Motion
import java.time.LocalDate

/** Days before the due date from which rings and bars turn amber (mockup: 3 and 5 days are amber, 9 is teal). */
const val DUE_WARN_DAYS: Long = 7L

/** Days left until the due date (negative when overdue), `null` without one. */
fun SubscriptionUi.daysLeft(): Long? = when (val label = due.label) {
    is DueLabel.InDays -> label.days
    DueLabel.Today -> 0L
    is DueLabel.Overdue -> -label.days
    DueLabel.Permanent, DueLabel.NoDate -> null
}

/** Ring / bar color of a subscription's due state: coral overdue, amber soon, teal otherwise. */
fun dueAccent(days: Long?, chart: ChartColors): Color = when {
    days == null -> chart.teal
    days < 0 -> chart.coral
    days <= DUE_WARN_DAYS -> chart.amber
    else -> chart.teal
}

/** Mockup tile traffic bar: teal while low, teal to amber past half, amber to coral near the limit. */
fun trafficBarColors(fraction: Float, chart: ChartColors): List<Color> = when {
    fraction >= 0.8f -> listOf(chart.amber, chart.coral)
    fraction >= 0.5f -> listOf(chart.teal, chart.amber)
    else -> listOf(chart.teal, chart.teal)
}

/**
 * Mockup `.mini`: a 40 dp ring (radius 16, stroke 4) filled with the passed share of the cycle,
 * the days left in the middle. Permanent subscriptions show a full ring with ∞; no due date a
 * dashed ring.
 */
@Composable
fun MiniDueRing(item: SubscriptionUi, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val chart = chartColors()
    val env = currentGlass()
    val days = item.daysLeft()
    val color = dueAccent(days, chart)
    val fraction = when {
        item.isPermanent -> 1f
        days == null -> 0f
        else -> item.elapsed
    }
    val anim = rememberAnimatedValues(
        targets = listOf(fraction),
        memoryKey = "mini-ring-${item.id}",
        durationMillis = Motion.DurationChartArc,
        easing = Motion.ChartArcEasing,
        delayOf = { Motion.DelayChartArc },
    )
    val number = when {
        item.isPermanent -> "∞"
        days == null -> "–"
        days < 0 -> (-days).toString()
        else -> days.toString()
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val unit = this.size.minDimension / 40f
            val stroke = 4f * unit
            val r = 16f * unit
            val topLeft = Offset(center.x - r, center.y - r)
            val arc = Size(r * 2, r * 2)
            if (days == null && !item.isPermanent) {
                drawCircle(
                    chart.track,
                    radius = r,
                    style = Stroke(stroke / 2, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f * unit, 4f * unit))),
                )
            } else {
                drawCircle(chart.track, radius = r, style = Stroke(stroke))
                val f = anim[0].value.coerceIn(0f, 1f)
                if (f > 0f) drawArc(color, -90f, 360f * f, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Text(
            number,
            color = if (days != null && days < 0) GlassAccents.coral(env.dark) else env.ink.primary,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
        )
    }
}

/**
 * Mockup `.tile .pr`: the price with a small, dim currency symbol; cents only when there are any.
 * "免费" for free subscriptions.
 */
@Composable
fun PriceLabel(item: SubscriptionUi, modifier: Modifier = Modifier, fontSize: TextUnit = 19.sp) {
    val env = currentGlass()
    if (item.isFree) {
        Text(
            stringResource(R.string.free_tag),
            color = GlassAccents.mint(env.dark),
            fontSize = (fontSize.value * 0.82f).sp,
            fontWeight = FontWeight.SemiBold,
            modifier = modifier,
            maxLines = 1,
        )
        return
    }
    val parts = Formatters.amountParts(item.priceMinor, item.currency)
    val dim = env.ink.primary.copy(alpha = 0.7f)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = (fontSize.value * 0.58f).sp, fontWeight = FontWeight.Medium, color = dim)) {
                append(parts.symbol)
                append(' ')
            }
            append(parts.integer)
            if (parts.fraction != "00") {
                withStyle(SpanStyle(fontSize = (fontSize.value * 0.63f).sp, color = dim)) {
                    append('.')
                    append(parts.fraction)
                }
            }
        },
        color = env.ink.primary,
        fontSize = fontSize,
        lineHeight = (fontSize.value * 1.2f).sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
        style = TextStyle(fontFeatureSettings = "tnum"),
        maxLines = 1,
        modifier = modifier.semantics { contentDescription = Formatters.formatAmount(item.priceMinor, item.currency) },
    )
}

/** Mockup `.tb`: a 4 dp track with the used share of the limit (a calm full bar without a limit). */
@Composable
fun TrafficMiniBar(usage: TrafficUsage, modifier: Modifier = Modifier, memoryKey: String? = null) {
    val chart = chartColors()
    val fraction = usage.percent?.let { (it / 100).toFloat() }
    val anim = rememberAnimatedValues(
        targets = listOf((fraction ?: 1f).coerceIn(0f, 1f)),
        memoryKey = memoryKey,
        durationMillis = Motion.DurationHBarGrow,
        easing = Motion.HBarGrowEasing,
        delayOf = { Motion.DelayHBarGrow },
    )
    val colors = if (fraction == null) listOf(chart.blue.copy(alpha = 0.55f), chart.teal.copy(alpha = 0.55f)) else trafficBarColors(fraction, chart)
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .drawBehind {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(chart.track, cornerRadius = r)
                val w = size.width * anim[0].value.coerceIn(0f, 1f)
                if (w > 0f) drawRoundRect(Brush.horizontalGradient(colors, endX = w.coerceAtLeast(1f)), size = Size(w, size.height), cornerRadius = r)
            },
    )
}

/**
 * CPU, memory and disk of a live node as three small rings (teal, amber, blue like the detail
 * page's gauges), the percentage inside.
 */
@Composable
fun ResourceRings(resources: NodeResources, modifier: Modifier = Modifier, ring: Dp = 20.dp) {
    val chart = chartColors()
    val env = currentGlass()
    val values = listOf(resources.cpu, resources.ram, resources.disk)
    val colors = listOf(chart.teal, chart.amber, chart.blue)
    val description = stringResource(
        R.string.list_resources_desc,
        percentText(resources.cpu),
        percentText(resources.ram),
        percentText(resources.disk),
    )
    Row(
        modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        values.forEachIndexed { i, v ->
            Box(Modifier.size(ring), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(ring)) {
                    val stroke = 2.4.dp.toPx()
                    val r = size.minDimension / 2 - stroke / 2
                    drawCircle(chart.track, radius = r, style = Stroke(stroke))
                    val f = v ?: 0f
                    if (f > 0f) {
                        drawArc(
                            colors[i], -90f, 360f * f.coerceIn(0f, 1f), false,
                            Offset(center.x - r, center.y - r), Size(r * 2, r * 2),
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                Text(
                    v?.let { (it * 100).toInt().coerceIn(0, 100).toString() } ?: "–",
                    color = env.ink.primary,
                    fontSize = 7.5.sp,
                    lineHeight = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun percentText(v: Float?): String = v?.let { "${(it * 100).toInt()}%" } ?: "–"

/** The line under a subscription's name: cycle, auto-renewal or traffic, or "待补充". */
@Composable
fun subscriptionMeta(item: SubscriptionUi): String {
    val cycle = cycleText(item.cycle)
    val traffic = item.traffic
    return when {
        item.needsBillingInfo -> stringResource(R.string.billing_incomplete_meta)
        item.category == Category.VPS && traffic != null -> {
            val usage = traffic.percent?.let { stringResource(R.string.list_meta_traffic, Formatters.formatPercent(it)) }
                ?: stringResource(R.string.list_meta_traffic_used, Formatters.formatBytes(traffic.usedBytes))
            if (item.cycleKnown) "$cycle · $usage" else usage
        }
        !item.cycleKnown -> stringResource(R.string.cycle_unknown_meta)
        item.autoRenew -> "$cycle · ${stringResource(R.string.auto_renew_on)}"
        else -> cycle
    }
}

/** "3 天后到期" / "今天到期" / "已过期 2 天" / "长期有效" for the pinned bar. */
@Composable
fun dueShortText(item: SubscriptionUi): String = when (val label = item.due.label) {
    is DueLabel.InDays -> stringResource(R.string.list_due_in, label.days.toInt())
    DueLabel.Today -> stringResource(R.string.due_today)
    is DueLabel.Overdue -> stringResource(R.string.due_overdue_days, label.days.toInt())
    DueLabel.Permanent -> stringResource(R.string.due_permanent)
    DueLabel.NoDate -> stringResource(R.string.due_no_date)
}

/** Amber "待补充" capsule shown instead of the price (and the ring) while billing data is missing. */
@Composable
fun IncompleteCapsule(modifier: Modifier = Modifier) {
    val env = currentGlass()
    val amber = GlassAccents.amber(env.dark)
    Text(
        stringResource(R.string.billing_incomplete_tag),
        color = amber,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier
            .drawBehind {
                drawRoundRect(amber.copy(alpha = 0.16f), cornerRadius = CornerRadius(size.height / 2))
                drawRoundRect(amber.copy(alpha = 0.45f), cornerRadius = CornerRadius(size.height / 2), style = Stroke(1.dp.toPx()))
            }
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** The data the renew sheet needs about one subscription. */
@Immutable
data class RenewChoice(val id: Long, val name: String, val due: LocalDate?, val fromDue: LocalDate?, val fromToday: LocalDate?)

fun SubscriptionUi.renewChoice(): RenewChoice = RenewChoice(id, name, due.date, renewFromDue, renewFromToday)

/**
 * "标记已续费": a glass sheet with the two ways to compute the new due date (from the old due
 * date, from today), each with its preview.
 */
@Composable
fun RenewSheet(
    choice: RenewChoice?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (Long, RenewMode) -> Unit,
    origin: GrowOrigin? = null,
) {
    val env = currentGlass()
    GlassSheet(
        visible = choice != null,
        onDismissRequest = onDismiss,
        origin = origin,
        contentDescription = stringResource(R.string.detail_renew_title),
    ) {
        val c = remember(choice) { choice } ?: return@GlassSheet
        Text(stringResource(R.string.detail_renew_title), color = env.ink.primary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.list_renew_message, c.name),
            color = env.ink.secondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            c.fromDue?.let { newDue ->
                RenewOptionRow(
                    icon = Icons.Rounded.Update,
                    title = stringResource(R.string.detail_renew_from_due),
                    description = c.due?.let { stringResource(R.string.detail_renew_from_due_desc, dateText(it, today)) }
                        ?: stringResource(R.string.detail_renew_from_today_desc),
                    newDue = dateText(newDue, today),
                    onClick = { onConfirm(c.id, RenewMode.FROM_DUE) },
                )
            }
            c.fromToday?.let { newDue ->
                RenewOptionRow(
                    icon = Icons.Rounded.Today,
                    title = stringResource(R.string.detail_renew_from_today),
                    description = stringResource(R.string.detail_renew_from_today_desc),
                    newDue = dateText(newDue, today),
                    onClick = { onConfirm(c.id, RenewMode.FROM_TODAY) },
                )
            }
        }
    }
}

@Composable
private fun RenewOptionRow(icon: ImageVector, title: String, description: String, newDue: String, onClick: () -> Unit) {
    val env = currentGlass()
    val teal = GlassAccents.teal(env.dark)
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        thickness = GlassThickness.Thin,
        onClick = onClick,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassSurface(
                modifier = Modifier.size(42.dp),
                shape = GlassDefaults.CapsuleShape,
                thickness = GlassThickness.Thin,
                tint = teal,
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = teal, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = env.ink.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = env.ink.secondary, fontSize = 12.sp, lineHeight = 16.sp)
                Text(
                    stringResource(R.string.detail_renew_new_due, newDue),
                    color = teal,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Delete confirmation, a glass dialog with the coral delete button. */
@Composable
fun DeleteSubscriptionDialog(visible: Boolean, name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    GlassConfirmDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.DeleteOutline,
        title = stringResource(R.string.detail_delete_title),
        message = stringResource(R.string.detail_delete_message, name),
        confirmLabel = stringResource(R.string.detail_delete),
        onConfirm = onConfirm,
        destructive = true,
    )
}

/** An empty page with the 续杯 mark (on a soft glow), a sentence and an optional action. */
@Composable
fun LogoEmptyState(title: String, message: String?, action: String?, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val env = currentGlass()
    val chart = chartColors()
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(132.dp)
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(
                            0f to chart.mint.copy(alpha = if (env.dark) 0.30f else 0.22f),
                            0.55f to chart.violet.copy(alpha = 0.10f),
                            1f to Color.Transparent,
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            VluerLogo(size = 84.dp)
        }
        Text(
            title,
            color = env.ink.primary,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (message != null) {
            Text(
                message,
                color = env.ink.secondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp),
            )
        }
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            GlassPillButton(action, onAction)
        }
    }
}

/** A color between [a] and [b] (kept here so tiles and rows blend the same way). */
internal fun mix(a: Color, b: Color, t: Float): Color = lerp(a, b, t.coerceIn(0f, 1f))
