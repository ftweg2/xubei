package com.ftwegc.myapplication.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.domain.calc.MonthSpend
import com.ftwegc.myapplication.domain.calc.SpendBasis
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.ui.brand.BrandMark
import com.ftwegc.myapplication.ui.brand.MarkSize
import com.ftwegc.myapplication.ui.brand.VluerLogo
import com.ftwegc.myapplication.ui.charts.BarChart
import com.ftwegc.myapplication.ui.charts.BarDatum
import com.ftwegc.myapplication.ui.charts.CalendarHeatmap
import com.ftwegc.myapplication.ui.charts.ChartColors
import com.ftwegc.myapplication.ui.charts.ChartPlaceholder
import com.ftwegc.myapplication.ui.charts.DonutChart
import com.ftwegc.myapplication.ui.charts.DonutSlice
import com.ftwegc.myapplication.ui.charts.HBarRow
import com.ftwegc.myapplication.ui.charts.HorizontalBars
import com.ftwegc.myapplication.ui.charts.PlaceholderKind
import com.ftwegc.myapplication.ui.charts.TimelineChart
import com.ftwegc.myapplication.ui.charts.TimelineEvent
import com.ftwegc.myapplication.ui.charts.chartColors
import com.ftwegc.myapplication.ui.components.animateCountAsState
import com.ftwegc.myapplication.ui.format.Formatters
import com.ftwegc.myapplication.ui.format.categoryLabel
import com.ftwegc.myapplication.ui.format.cycleText
import com.ftwegc.myapplication.ui.glass.GlassAccents
import com.ftwegc.myapplication.ui.glass.GlassDefaults
import com.ftwegc.myapplication.ui.glass.GlassLabel
import com.ftwegc.myapplication.ui.glass.GlassPillButton
import com.ftwegc.myapplication.ui.glass.GlassSegmented
import com.ftwegc.myapplication.ui.glass.GlassSheet
import com.ftwegc.myapplication.ui.glass.GlassSurface
import com.ftwegc.myapplication.ui.glass.LegendRow
import com.ftwegc.myapplication.ui.glass.MonthSwitcher
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.glass.rememberLatest
import com.ftwegc.myapplication.ui.subscriptions.trafficBarColors
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import java.time.format.DateTimeFormatter
import java.util.Locale

// ------------------------------------------------------------------ Building blocks

/** A block of the page: standard glass with the mockup's 16 dp padding. */
@Composable
internal fun StatsBlock(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(modifier = modifier, onClick = onClick, onClickLabel = onClickLabel) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/**
 * True once the element carrying [seenModifier] has been on screen: charts are composed (and
 * start their entrance) only then, not while the page is built below the fold.
 */
@Composable
internal fun rememberSeen(): MutableState<Boolean> {
    val reduced = LocalReducedMotion.current
    return remember { mutableStateOf(reduced) }
}

internal fun Modifier.seenModifier(seen: MutableState<Boolean>): Modifier = onGloballyPositioned { coords ->
    if (!seen.value && coords.boundsInWindow().height > SEEN_MIN_PX) seen.value = true
}

/** A chart counts as visible once this many pixels of it are on screen. */
private const val SEEN_MIN_PX = 24f

/** [content] once [seen]; until then an empty box of [height], so nothing jumps. */
@Composable
private fun WhenSeen(seen: MutableState<Boolean>, height: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().seenModifier(seen).heightIn(min = height)) {
        if (seen.value) content()
    }
}

/** Widens the element by [margin] on both sides (a sideways strip running to the screen edges). */
internal fun Modifier.bleed(margin: Dp): Modifier = layout { measurable, constraints ->
    val extra = margin.roundToPx() * 2
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** "¥720": symbol and whole units (ranking values, legends). */
internal fun compactAmount(minor: Long, currency: String): String {
    val abs = if (minor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minor)
    val sign = if (minor < 0) "-" else ""
    // Small amounts keep their cents ($9.99 must not read as $9); larger ones round to whole units.
    if (abs < COMPACT_CENTS_BELOW && abs % 100 != 0L) return sign + Formatters.formatAmount(abs, currency)
    val rounded = if (abs > Long.MAX_VALUE - 50) abs else (abs + 50) / 100 * 100
    val parts = Formatters.amountParts(rounded, currency)
    return sign + parts.symbol + parts.integer
}

/** Below this (in minor units, 100.00) compact amounts still show cents. */
private const val COMPACT_CENTS_BELOW = 10_000L

/**
 * Mockup `.num`: a small dim symbol, the whole units, dim cents; counts up when first shown.
 */
@Composable
internal fun StatsAmount(
    minor: Long,
    currency: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 30.sp,
    memoryKey: String? = null,
    color: Color = Color.Unspecified,
) {
    val env = currentGlass()
    val shown by animateCountAsState(minor, countFromZero = true, memoryKey = memoryKey)
    val parts = Formatters.amountParts(shown, currency)
    val ink = if (color == Color.Unspecified) env.ink.primary else color
    val dim = ink.copy(alpha = 0.7f)
    Text(
        buildAnnotatedString {
            if (parts.negative) append('-')
            withStyle(SpanStyle(fontSize = fontSize * 0.47f, color = dim, fontWeight = FontWeight.Medium, baselineShift = BaselineShift(0.5f))) {
                append(parts.symbol)
                append(' ')
            }
            append(parts.integer)
            withStyle(SpanStyle(fontSize = fontSize * 0.5f, color = dim)) {
                append('.')
                append(parts.fraction)
            }
        },
        modifier = modifier.semantics { contentDescription = Formatters.formatAmount(minor, currency) },
        color = ink,
        fontSize = fontSize,
        lineHeight = fontSize * 1.12f,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
        style = TextStyle(fontFeatureSettings = "tnum"),
        maxLines = 1,
    )
}

/** Stats mockup: AI violet, VPS blue, other teal. */
internal fun categoryColor(category: Category, chart: ChartColors): Color = when (category) {
    Category.AI -> chart.violet
    Category.VPS -> chart.blue
    Category.OTHER -> chart.teal
}

// ------------------------------------------------------------------ Empty states

/** No subscription yet: the mark, a sentence, and the add action. */
@Composable
internal fun EmptyStatsBlock(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val env = currentGlass()
    StatsBlock(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            VluerLogo(size = 64.dp, modifier = Modifier.padding(top = 6.dp))
            Text(stringResource(R.string.stats_empty_title), color = env.ink.primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Text(
                stringResource(R.string.stats_empty_message),
                color = env.ink.secondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(14.dp))
            GlassPillButton(stringResource(R.string.stats_empty_action), onAdd)
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Subscriptions still without a price and a due date: they are not counted until completed. */
@Composable
internal fun NeedsInfoBlock(state: StatsUiState, onComplete: (Long) -> Unit, modifier: Modifier = Modifier) {
    val env = currentGlass()
    val amber = GlassAccents.amber(env.dark)
    StatsBlock(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EditCalendar, contentDescription = null, tint = amber, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                if (state.nothingCounted) stringResource(R.string.stats_only_needs_info, state.needsInfoCount)
                else stringResource(R.string.stats_needs_info, state.needsInfoCount),
                color = env.ink.primary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            GlassPillButton(stringResource(R.string.import_complete_action), { state.needsInfoIds.firstOrNull()?.let(onComplete) })
        }
    }
}

/** The sentence a chart shows when nothing is counted yet. */
@Composable
private fun emptyChartText(state: StatsUiState): String = when {
    !state.hasAny -> stringResource(R.string.stats_chart_no_subs)
    state.nothingCounted && state.needsInfoCount > 0 -> stringResource(R.string.stats_chart_needs_info)
    else -> stringResource(R.string.stats_chart_nothing, state.currency)
}

// ------------------------------------------------------------------ Key figures

/** The key figures as a row of small glass tiles that scrolls sideways. */
@Composable
internal fun MetricStrip(state: StatsUiState, modifier: Modifier = Modifier) {
    val metrics = state.metrics ?: return
    val env = currentGlass()
    val c = state.currency
    val year = state.mode == StatsMode.YEAR
    val delta = metrics.deltaMinor
    Row(
        modifier
            .bleed(StatsMargin)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = StatsMargin),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        MetricTile(
            label = stringResource(if (year) R.string.stats_metric_year else R.string.stats_metric_month),
            value = { StatsAmount(metrics.periodMinor, c, fontSize = 20.sp, memoryKey = "stats-metric-period-$c-$year") },
            note = stringResource(if (year) R.string.stats_metric_previous_year else R.string.stats_metric_previous_month, compactAmount(metrics.previousMinor, c)),
        )
        MetricTile(
            label = stringResource(R.string.stats_metric_daily),
            value = { StatsAmount(metrics.dailyAverageMinor, c, fontSize = 20.sp, memoryKey = "stats-metric-daily-$c") },
            note = stringResource(R.string.stats_metric_daily_note),
        )
        val top = metrics.topSubscriptionId?.let { state.items[it] }
        MetricTile(
            label = stringResource(R.string.stats_metric_top),
            value = {
                if (top == null) {
                    Text("–", color = env.ink.primary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        BrandMark(top.mark, size = MarkSize.Small)
                        Text(top.name, color = env.ink.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            note = if (top != null) stringResource(R.string.stats_per_month, compactAmount(metrics.topMonthlyMinor, c)) else stringResource(R.string.chart_empty_short),
        )
        MetricTile(
            label = stringResource(if (year) R.string.stats_metric_change_year else R.string.stats_metric_change_month),
            value = {
                val text = when {
                    delta > 0 -> "+" + compactAmount(delta, c)
                    delta < 0 -> "−" + compactAmount(-delta, c)
                    else -> stringResource(R.string.stats_metric_same)
                }
                Text(
                    text,
                    color = when {
                        delta > 0 -> GlassAccents.amber(env.dark)
                        delta < 0 -> GlassAccents.mint(env.dark)
                        else -> env.ink.primary
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
            },
            note = when {
                metrics.previousMinor <= 0L -> stringResource(R.string.stats_metric_change_none)
                else -> stringResource(R.string.stats_metric_change_percent, Formatters.formatPercent(kotlin.math.abs(delta) * 100.0 / metrics.previousMinor))
            },
        )
        MetricTile(
            label = stringResource(R.string.stats_metric_lifetime),
            value = { StatsAmount(metrics.lifetimeMinor, c, fontSize = 20.sp, memoryKey = "stats-metric-lifetime-$c") },
            note = stringResource(R.string.stats_metric_lifetime_note),
        )
        MetricTile(
            label = stringResource(R.string.stats_metric_active),
            value = {
                Text(
                    metrics.activeCount.toString(),
                    color = env.ink.primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
            },
            note = if (metrics.needsInfoCount > 0) stringResource(R.string.stats_metric_active_note_incomplete, metrics.countedCount, metrics.needsInfoCount)
            else stringResource(R.string.stats_metric_active_note, metrics.countedCount),
            noteColor = if (metrics.needsInfoCount > 0) GlassAccents.amber(env.dark) else Color.Unspecified,
        )
        Spacer(Modifier.width(1.dp))
    }
}

private val MetricTileShape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)

@Composable
private fun MetricTile(label: String, value: @Composable () -> Unit, note: String, noteColor: Color = Color.Unspecified) {
    val env = currentGlass()
    GlassSurface(Modifier.width(MetricTileWidth), shape = MetricTileShape) {
        Column(
            Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .heightIn(min = 74.dp)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, color = env.ink.secondary, fontSize = 11.5.sp, lineHeight = 15.sp, maxLines = 1)
            Box(Modifier.heightIn(min = 26.dp), contentAlignment = Alignment.CenterStart) { value() }
            Text(
                note,
                color = if (noteColor == Color.Unspecified) env.ink.secondary else noteColor,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val MetricTileWidth = 136.dp

// ------------------------------------------------------------------ Monthly bars

/** The one sentence under the basis switch. */
@Composable
private fun basisExplanation(basis: SpendBasis): String =
    stringResource(if (basis == SpendBasis.CASH_FLOW) R.string.stats_basis_cash_explain else R.string.stats_basis_amortized_explain)

@Composable
private fun BasisSwitch(basis: SpendBasis, onBasis: (SpendBasis) -> Unit, modifier: Modifier = Modifier) {
    GlassSegmented(
        labels = listOf(stringResource(R.string.stats_basis_cash), stringResource(R.string.stats_basis_amortized)),
        selected = if (basis == SpendBasis.CASH_FLOW) 0 else 1,
        onSelect = { onBasis(if (it == 0) SpendBasis.CASH_FLOW else SpendBasis.AMORTIZED) },
        modifier = modifier,
    )
}

/**
 * Mockup "每月应付": this month's amount, the bars of six months back and six ahead (the future
 * light, this month bright), month names below. Cash flow or amortized; tap a bar for its month.
 */
@Composable
internal fun MonthlyBlock(state: StatsUiState, onBasis: (SpendBasis) -> Unit, onOpenMonth: (MonthSpend) -> Unit, modifier: Modifier = Modifier) {
    val env = currentGlass()
    val seen = rememberSeen()
    val months = state.months
    val current = months.getOrNull(state.currentIndex)
    StatsBlock(modifier.fillMaxWidth()) {
        GlassLabel(stringResource(R.string.stats_monthly_label), trailing = stringResource(R.string.stats_monthly_future))
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            StatsAmount(current?.totalMinor ?: 0L, state.currency, Modifier.weight(1f), memoryKey = "stats-month-now-${state.currency}-${state.basis}")
            BasisSwitch(state.basis, onBasis, Modifier.width(150.dp))
        }
        Text(
            basisExplanation(state.basis),
            color = env.ink.secondary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (state.nothingCounted || months.all { it.totalMinor <= 0L }) {
            ChartPlaceholder(emptyChartText(state), Modifier.padding(top = 10.dp).fillMaxWidth().height(96.dp), PlaceholderKind.Bars)
            return@StatsBlock
        }
        var selected by remember { mutableIntStateOf(-1) }
        val monthName = stringResource(R.string.stats_month_short)
        val description = stringResource(
            R.string.stats_monthly_desc,
            months.joinToString("，") { "${it.month.monthValue}$monthName ${compactAmount(it.totalMinor, state.currency)}" },
        )
        WhenSeen(seen, 96.dp, Modifier.padding(top = 10.dp)) {
            BarChart(
                bars = months.mapIndexed { i, m -> BarDatum(m.totalMinor.toFloat(), future = i > state.currentIndex, current = i == state.currentIndex) },
                contentDescription = description,
                height = 96.dp,
                gap = 5.dp,
                selected = selected,
                onSelect = { i ->
                    if (i >= 0) {
                        selected = i
                        onOpenMonth(months[i])
                    } else {
                        selected = -1
                    }
                },
                memoryKey = "stats-months-${state.currency}",
            )
        }
        MonthAxis(months.map { it.month.monthValue }, state.currentIndex, Modifier.padding(top = 5.dp))
        Text(stringResource(R.string.stats_tap_bar), color = env.ink.secondary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Month names under the bars: every second one and this month (white). */
@Composable
private fun MonthAxis(months: List<Int>, currentIndex: Int, modifier: Modifier = Modifier, gap: Dp = 5.dp, everyOther: Boolean = true) {
    val env = currentGlass()
    val unit = stringResource(R.string.stats_month_short)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        months.forEachIndexed { i, m ->
            val show = !everyOther || (i - currentIndex) % 2 == 0
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (show) {
                    Text(
                        "$m$unit",
                        color = if (i == currentIndex) env.ink.primary else env.ink.secondary.copy(alpha = env.ink.secondary.alpha * 0.8f),
                        fontSize = 10.sp,
                        fontWeight = if (i == currentIndex) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Year view

/** 按年: each year's total as a bar (tap one to pick it), then the months of the picked year. */
@Composable
internal fun YearBlock(
    state: StatsUiState,
    onBasis: (SpendBasis) -> Unit,
    onYear: (Int) -> Unit,
    onOpenMonth: (MonthSpend) -> Unit,
    modifier: Modifier = Modifier,
) {
    val env = currentGlass()
    val seenYears = rememberSeen()
    val seenMonths = rememberSeen()
    val years = state.years
    val thisYear = state.today.year
    val picked = years.firstOrNull { it.first == state.selectedYear }?.second ?: 0L
    StatsBlock(modifier.fillMaxWidth()) {
        GlassLabel(stringResource(R.string.stats_year_label), trailing = stringResource(R.string.stats_year_trailing))
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                StatsAmount(picked, state.currency, memoryKey = "stats-year-total-${state.currency}-${state.basis}-${state.selectedYear}")
                Text(stringResource(R.string.stats_year_of, state.selectedYear), color = env.ink.secondary, fontSize = 11.5.sp)
            }
            BasisSwitch(state.basis, onBasis, Modifier.width(150.dp))
        }
        Text(basisExplanation(state.basis), color = env.ink.secondary, fontSize = 11.5.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
        if (state.nothingCounted || years.all { it.second <= 0L }) {
            ChartPlaceholder(emptyChartText(state), Modifier.padding(top = 10.dp).fillMaxWidth().height(96.dp), PlaceholderKind.Bars)
            return@StatsBlock
        }
        val pickedIndex = years.indexOfFirst { it.first == state.selectedYear }
        WhenSeen(seenYears, 84.dp, Modifier.padding(top = 12.dp)) {
            BarChart(
                bars = years.map { (y, total) -> BarDatum(total.toFloat(), future = y > thisYear, current = y == thisYear) },
                contentDescription = stringResource(
                    R.string.stats_years_desc,
                    years.joinToString("，") { (y, total) -> "$y ${compactAmount(total, state.currency)}" },
                ),
                height = 84.dp,
                gap = 14.dp,
                selected = pickedIndex,
                onSelect = { i -> if (i >= 0) onYear(years[i].first) },
                memoryKey = "stats-years-${state.currency}",
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            years.forEach { (y, total) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        y.toString(),
                        color = if (y == state.selectedYear) env.ink.primary else env.ink.secondary,
                        fontSize = 11.sp,
                        fontWeight = if (y == state.selectedYear) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.clickable { onYear(y) },
                    )
                    Text(compactAmount(total, state.currency), color = env.ink.secondary, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        GlassLabel(
            stringResource(R.string.stats_year_months, state.selectedYear),
            trailing = stringResource(R.string.stats_monthly_average, compactAmount(picked / 12, state.currency)),
            modifier = Modifier.padding(top = 16.dp),
        )
        val months = state.yearMonths
        val nowIndex = if (state.selectedYear == thisYear) state.today.monthValue - 1 else -1
        var selected by remember { mutableIntStateOf(-1) }
        val monthName = stringResource(R.string.stats_month_short)
        WhenSeen(seenMonths, 72.dp, Modifier.padding(top = 10.dp)) {
            BarChart(
                bars = months.mapIndexed { i, m ->
                    BarDatum(m.totalMinor.toFloat(), future = state.selectedYear > thisYear || (nowIndex >= 0 && i > nowIndex), current = i == nowIndex)
                },
                contentDescription = stringResource(
                    R.string.stats_monthly_desc,
                    months.joinToString("，") { "${it.month.monthValue}$monthName ${compactAmount(it.totalMinor, state.currency)}" },
                ),
                height = 72.dp,
                gap = 4.dp,
                selected = selected,
                onSelect = { i ->
                    selected = if (i >= 0) i else -1
                    if (i >= 0) onOpenMonth(months[i])
                },
                memoryKey = "stats-year-months-${state.currency}-${state.selectedYear}",
            )
        }
        MonthAxis(months.map { it.month.monthValue }, if (nowIndex >= 0) nowIndex else 0, Modifier.padding(top = 5.dp), gap = 4.dp)
    }
}

// ------------------------------------------------------------------ Category donut

/** Mockup "分类占比": a donut (tap a slice) with the share and amount of the chosen category. */
@Composable
internal fun CategoryBlock(state: StatsUiState, modifier: Modifier = Modifier) {
    val env = currentGlass()
    val chart = chartColors()
    val shares = state.categories
    val seen = rememberSeen()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    StatsBlock(modifier.heightIn(min = HalfBlockMinHeight)) {
        GlassLabel(stringResource(R.string.home_category_label))
        if (shares.isEmpty()) {
            ChartPlaceholder(emptyChartText(state), Modifier.padding(top = 10.dp).align(Alignment.CenterHorizontally).size(104.dp), PlaceholderKind.Ring)
            return@StatsBlock
        }
        val pick = selected.coerceIn(0, shares.lastIndex)
        val labels = shares.map { categoryLabel(it.category) }
        val description = stringResource(
            R.string.home_category_desc,
            shares.indices.joinToString("，") { "${labels[it]} ${Formatters.formatPercent(shares[it].fraction * 100)}" },
        )
        WhenSeen(seen, 104.dp, Modifier.padding(top = 10.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DonutChart(
                    slices = shares.mapIndexed { i, s -> DonutSlice(s.monthlyMinor.toFloat(), categoryColor(s.category, chart), labels[i]) },
                    contentDescription = description,
                    size = 104.dp,
                    selected = pick,
                    onSelect = { if (it >= 0) selected = it },
                    memoryKey = "stats-category-${state.currency}",
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            Formatters.formatPercent(shares[pick].fraction * 100),
                            color = env.ink.primary,
                            fontSize = 18.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                        )
                        Text(labels[pick], color = env.ink.secondary, fontSize = 10.5.sp, lineHeight = 13.sp)
                        Text(compactAmount(shares[pick].monthlyMinor, state.currency), color = env.ink.secondary, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
                    }
                }
            }
        }
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            shares.forEachIndexed { i, s ->
                LegendRow(
                    categoryColor(s.category, chart),
                    labels[i],
                    compactAmount(s.monthlyMinor, state.currency),
                    Modifier.clickable { selected = i },
                )
            }
        }
        Text(stringResource(R.string.stats_per_month_note), color = env.ink.secondary, fontSize = 10.5.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Half-width blocks keep the same height in a row. */
private val HalfBlockMinHeight = 236.dp

// ------------------------------------------------------------------ Ranking

/** Rows in the half-width ranking (the rest in its sheet). */
private const val RANKING_ROWS = 5

/** Mockup "花费排行": marks with bars by monthly equivalent, largest first. */
@Composable
internal fun RankingBlock(state: StatsUiState, onOpenAll: () -> Unit, modifier: Modifier = Modifier) {
    val rows = state.ranking
    val seen = rememberSeen()
    StatsBlock(
        modifier.heightIn(min = HalfBlockMinHeight),
        onClick = if (rows.isNotEmpty()) onOpenAll else null,
        onClickLabel = stringResource(R.string.stats_ranking_all),
    ) {
        GlassLabel(stringResource(R.string.stats_ranking_label), trailing = if (rows.isNotEmpty()) stringResource(R.string.stats_ranking_trailing) else null)
        if (rows.isEmpty()) {
            ChartPlaceholder(emptyChartText(state), Modifier.padding(top = 10.dp).fillMaxWidth().height(120.dp), PlaceholderKind.Bars)
            return@StatsBlock
        }
        WhenSeen(seen, 120.dp, Modifier.padding(top = 10.dp)) {
            RankingBars(state, rows.take(RANKING_ROWS), valueWidth = 38.dp, memoryKey = "stats-ranking-${state.currency}")
        }
        if (rows.size > RANKING_ROWS) {
            Text(
                stringResource(R.string.stats_ranking_more, rows.size - RANKING_ROWS),
                color = currentGlass().ink.secondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun RankingBars(state: StatsUiState, rows: List<com.ftwegc.myapplication.domain.calc.RankRow>, valueWidth: Dp, memoryKey: String, onOpen: ((Long) -> Unit)? = null) {
    val chart = chartColors()
    val description = stringResource(
        R.string.stats_ranking_desc,
        rows.joinToString("，") { "${state.items[it.subscriptionId]?.name.orEmpty()} ${compactAmount(it.monthlyMinor, state.currency)}" },
    )
    HorizontalBars(
        rows = rows.map { r ->
            val item = state.items[r.subscriptionId]
            val spec = com.ftwegc.myapplication.ui.brand.BrandCatalog.resolve(item?.name.orEmpty(), item?.mark?.iconKey, item?.mark?.iconColor)
            HBarRow(r.fraction, rankColors(spec, chart), compactAmount(r.monthlyMinor, state.currency), item?.name.orEmpty())
        },
        contentDescription = description,
        valueWidth = valueWidth,
        memoryKey = memoryKey,
    ) { i ->
        val item = state.items[rows[i].subscriptionId]
        if (item != null) {
            BrandMark(
                item.mark,
                size = MarkSize.Small,
                modifier = if (onOpen != null) Modifier.clickable { onOpen(item.id) } else Modifier,
            )
        }
    }
}

/** A ranking bar in the service's own colors (mockup: Claude clay to amber, ChatGPT green to mint). */
private fun rankColors(spec: com.ftwegc.myapplication.ui.brand.MarkSpec, chart: ChartColors): List<Color> {
    if (spec.start to spec.end == com.ftwegc.myapplication.ui.brand.BrandCatalog.NeutralGradient) return listOf(Color(0xFF8B95A5), Color(0xFFCBD5E1))
    val start = Color(spec.start)
    return listOf(start, androidx.compose.ui.graphics.lerp(start, if (chart.dark) Color.White else chart.mint, 0.45f))
}

/** Every subscription of the ranking, with its name and the share of the whole. */
@Composable
internal fun RankingSheet(visible: Boolean, state: StatsUiState, onDismiss: () -> Unit, onOpenSubscription: (Long) -> Unit) {
    val env = currentGlass()
    GlassSheet(visible = visible, onDismissRequest = onDismiss, contentDescription = stringResource(R.string.stats_ranking_label)) {
        val rows = state.ranking
        val total = rows.sumOf { it.monthlyMinor }
        Text(stringResource(R.string.stats_ranking_label), color = env.ink.primary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.stats_ranking_sheet_sub, rows.size, compactAmount(total, state.currency)),
            color = env.ink.secondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { r ->
                val item = state.items[r.subscriptionId] ?: return@forEach
                PaymentRow(
                    item = item,
                    title = item.name,
                    subtitle = if (item.cycleKnown) cycleText(item.cycle) else null,
                    amount = stringResource(R.string.stats_per_month, compactAmount(r.monthlyMinor, state.currency)),
                    share = if (total > 0) r.monthlyMinor.toFloat() / total else 0f,
                    onClick = { onOpenSubscription(item.id) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Month detail

/**
 * The payments of a tapped month (cash flow) or its amortized shares: mark, name, date, amount and
 * the share of the month.
 */
@Composable
internal fun MonthDetailSheet(month: MonthSpend?, state: StatsUiState, onDismiss: () -> Unit, onOpenSubscription: (Long) -> Unit) {
    val env = currentGlass()
    val shown = rememberLatest(month)
    val format = remember { DateTimeFormatter.ofPattern("M月d日", Locale.CHINA) }
    GlassSheet(visible = month != null, onDismissRequest = onDismiss, contentDescription = stringResource(R.string.stats_detail_title)) {
        val m = shown ?: return@GlassSheet
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.stats_detail_month, m.month.year, m.month.monthValue),
                color = env.ink.primary,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(if (state.basis == SpendBasis.CASH_FLOW) R.string.stats_basis_cash else R.string.stats_basis_amortized),
                color = GlassAccents.mint(env.dark),
                fontSize = 12.sp,
            )
        }
        StatsAmount(m.totalMinor, state.currency, Modifier.padding(top = 6.dp), fontSize = 28.sp)
        Text(
            if (m.entries.isEmpty()) stringResource(R.string.stats_detail_none)
            else stringResource(R.string.stats_detail_count, m.entries.size),
            color = env.ink.secondary,
            fontSize = 12.5.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val entries = if (state.basis == SpendBasis.CASH_FLOW) m.entries else m.entries.sortedByDescending { it.amountMinor }
            entries.forEach { e ->
                val item = state.items[e.subscriptionId] ?: return@forEach
                val subtitle = when {
                    e.oneTime -> stringResource(R.string.stats_detail_one_time, e.date.format(format))
                    state.basis == SpendBasis.CASH_FLOW -> e.date.format(format) + (if (item.cycleKnown) " · " + cycleText(item.cycle) else "")
                    else -> stringResource(R.string.stats_detail_spread, cycleText(item.cycle), compactAmount(e.paymentMinor, state.currency), e.date.format(format))
                }
                PaymentRow(
                    item = item,
                    title = item.name,
                    subtitle = subtitle,
                    amount = Formatters.formatAmount(e.amountMinor, state.currency),
                    share = m.share(e).toFloat(),
                    onClick = { onOpenSubscription(item.id) },
                )
            }
        }
    }
}

/** A payment line: mark, name and subline, the amount with its share, and a thin share bar. */
@Composable
private fun PaymentRow(item: StatsItem, title: String, subtitle: String?, amount: String, share: Float, onClick: () -> Unit) {
    val env = currentGlass()
    val chart = chartColors()
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(item.mark, size = MarkSize.Medium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = env.ink.primary, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, color = env.ink.secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, color = env.ink.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, style = TextStyle(fontFeatureSettings = "tnum"), maxLines = 1)
                Text(Formatters.formatPercent(share * 100.0), color = env.ink.secondary, fontSize = 11.5.sp, style = TextStyle(fontFeatureSettings = "tnum"))
            }
        }
        androidx.compose.foundation.Canvas(
            Modifier
                .padding(start = 56.dp, top = 7.dp)
                .fillMaxWidth()
                .height(4.dp),
        ) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(chart.track, cornerRadius = r)
            val w = size.width * share.coerceIn(0f, 1f)
            if (w > 0f) {
                drawRoundRect(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(chart.mint, chart.blue), endX = w.coerceAtLeast(1f)),
                    size = androidx.compose.ui.geometry.Size(w.coerceAtLeast(size.height), size.height),
                    cornerRadius = r,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Calendar

/**
 * Mockup "缴费日历": days with a payment tinted by amount; the arrows change the month; tapping a
 * day lists its payments below.
 */
@Composable
internal fun CalendarBlock(state: StatsUiState, onMove: (Long) -> Unit, onOpenSubscription: (Long) -> Unit, modifier: Modifier = Modifier) {
    val env = currentGlass()
    val seen = rememberSeen()
    val month = state.calendarMonth
    var picked by rememberSaveable(month.toString()) { mutableStateOf<Int?>(null) }
    val days = state.calendar
    val amounts = days.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
    val format = remember { DateTimeFormatter.ofPattern("M月d日", Locale.CHINA) }
    StatsBlock(modifier.fillMaxWidth()) {
        GlassLabel(stringResource(R.string.stats_calendar_label), trailing = stringResource(R.string.stats_calendar_legend))
        MonthSwitcher(month, onPrevious = { onMove(-1) }, onNext = { onMove(1) }, modifier = Modifier.padding(top = 8.dp))
        val total = amounts.values.sum()
        Text(
            if (total > 0) stringResource(R.string.stats_calendar_total, amounts.size, Formatters.formatAmount(total, state.currency))
            else stringResource(R.string.stats_calendar_empty),
            color = env.ink.secondary,
            fontSize = 11.5.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
        WhenSeen(seen, 220.dp, Modifier.padding(top = 10.dp)) {
            CalendarHeatmap(
                month = month,
                amounts = amounts,
                today = state.today,
                selectedDay = picked,
                onSelectDay = { day -> picked = if (picked == day) null else day },
                memoryKey = "stats-calendar-${state.currency}-$month",
                contentDescription = stringResource(R.string.stats_calendar_desc, month.year, month.monthValue, amounts.size),
                dayDescription = { day ->
                    val list = days[day].orEmpty()
                    if (list.isEmpty()) "${month.monthValue}月${day}日"
                    else "${month.monthValue}月${day}日 " + list.joinToString("，") { "${state.items[it.subscriptionId]?.name.orEmpty()} ${Formatters.formatAmount(it.amountMinor, state.currency)}" }
                },
            )
        }
        AnimatedContent(targetState = picked, label = "calendarDay") { day ->
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (day == null) {
                    Text(stringResource(R.string.stats_calendar_hint), color = env.ink.secondary, fontSize = 11.5.sp)
                } else {
                    val list = days[day].orEmpty()
                    val date = month.atDay(day)
                    Text(
                        if (list.isEmpty()) stringResource(R.string.stats_calendar_day_none, date.format(format))
                        else stringResource(R.string.stats_calendar_day, date.format(format), Formatters.formatAmount(list.sumOf { it.amountMinor }, state.currency)),
                        color = env.ink.primary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val dayTotal = list.sumOf { it.amountMinor }
                    list.forEach { e ->
                        val item = state.items[e.subscriptionId] ?: return@forEach
                        PaymentRow(
                            item = item,
                            title = item.name,
                            subtitle = if (e.oneTime) stringResource(R.string.stats_detail_one_time_short) else if (item.cycleKnown) cycleText(item.cycle) else null,
                            amount = Formatters.formatAmount(e.amountMinor, state.currency),
                            share = if (dayTotal > 0) e.amountMinor.toFloat() / dayTotal else 0f,
                            onClick = { onOpenSubscription(item.id) },
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Traffic

/**
 * Mockup "各台流量": each VPS with a limit as a bar of its used share (highest first); VPSs
 * without a limit below, by the amount used.
 */
@Composable
internal fun TrafficBlock(state: StatsUiState, onOpenSubscription: (Long) -> Unit, modifier: Modifier = Modifier) {
    val env = currentGlass()
    val chart = chartColors()
    val traffic = state.traffic
    val seen = rememberSeen()
    StatsBlock(modifier.fillMaxWidth()) {
        GlassLabel(stringResource(R.string.stats_traffic_label), trailing = if (!traffic.isEmpty) stringResource(R.string.stats_traffic_trailing) else null)
        if (traffic.isEmpty) {
            ChartPlaceholder(
                stringResource(if (state.vpsCount == 0) R.string.stats_traffic_no_vps else R.string.stats_traffic_no_data),
                Modifier.padding(top = 10.dp).fillMaxWidth().height(88.dp),
                PlaceholderKind.Bars,
            )
            return@StatsBlock
        }
        WhenSeen(seen, 40.dp, Modifier.padding(top = 10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (traffic.limited.isNotEmpty()) {
                    val rows = traffic.limited
                    HorizontalBars(
                        rows = rows.map { r ->
                            val f = ((r.percent ?: 0.0) / 100).toFloat()
                            HBarRow(f.coerceIn(0f, 1f), trafficBarColors(f, chart), Formatters.formatPercent(r.percent ?: 0.0), state.items[r.subscriptionId]?.name.orEmpty())
                        },
                        contentDescription = stringResource(
                            R.string.stats_traffic_desc,
                            rows.joinToString("，") { "${state.items[it.subscriptionId]?.name.orEmpty()} ${Formatters.formatPercent(it.percent ?: 0.0)}" },
                        ),
                        valueWidth = 44.dp,
                        memoryKey = "stats-traffic-limited",
                    ) { i -> TrafficMark(state, rows[i].subscriptionId, onOpenSubscription) }
                }
                if (traffic.unlimited.isNotEmpty()) {
                    Text(stringResource(R.string.stats_traffic_unlimited), color = env.ink.secondary, fontSize = 11.5.sp)
                    val rows = traffic.unlimited
                    val max = rows.maxOf { it.usedBytes }.coerceAtLeast(1L)
                    HorizontalBars(
                        rows = rows.map { r ->
                            HBarRow(r.usedBytes.toFloat() / max, listOf(chart.blue.copy(alpha = 0.7f), chart.teal.copy(alpha = 0.7f)), Formatters.formatBytes(r.usedBytes), state.items[r.subscriptionId]?.name.orEmpty())
                        },
                        contentDescription = stringResource(
                            R.string.stats_traffic_unlimited_desc,
                            rows.joinToString("，") { "${state.items[it.subscriptionId]?.name.orEmpty()} ${Formatters.formatBytes(it.usedBytes)}" },
                        ),
                        valueWidth = 64.dp,
                        memoryKey = "stats-traffic-unlimited",
                    ) { i -> TrafficMark(state, rows[i].subscriptionId, onOpenSubscription) }
                }
            }
        }
    }
}

@Composable
private fun TrafficMark(state: StatsUiState, id: Long, onOpen: (Long) -> Unit) {
    val item = state.items[id] ?: return
    BrandMark(item.mark, size = MarkSize.Small, modifier = Modifier.clickable { onOpen(id) })
}

// ------------------------------------------------------------------ Timeline

/** Rows of pins on the 90-day timeline; more fold into "+N". */
private const val TIMELINE_LANES = 3

/** "未来 90 天": the subscriptions due soon, their marks pinned on the dates. */
@Composable
internal fun TimelineBlock(state: StatsUiState, onOpenSubscription: (Long) -> Unit, modifier: Modifier = Modifier) {
    val env = currentGlass()
    val pins = state.upcoming
    val days = StatsViewModel.TIMELINE_DAYS
    val seen = rememberSeen()
    val format = remember { DateTimeFormatter.ofPattern("M/d", Locale.CHINA) }
    StatsBlock(modifier.fillMaxWidth()) {
        GlassLabel(stringResource(R.string.home_timeline_label, days), trailing = if (pins.isNotEmpty()) stringResource(R.string.stats_timeline_count, pins.size) else null)
        val description = if (pins.isEmpty()) {
            stringResource(R.string.home_upcoming_empty, days)
        } else {
            stringResource(
                R.string.home_timeline_desc,
                days,
                pins.size,
                pins.joinToString("，") { "${state.items[it.id]?.name.orEmpty()} ${it.date.format(format)}" },
            )
        }
        WhenSeen(seen, 70.dp, Modifier.padding(top = 12.dp)) {
            TimelineChart(
                events = pins.map { TimelineEvent(it.id, it.dayOffset, if (it.overdue) stringResource(R.string.home_due_overdue_unit) else it.date.format(format), it.overdue) },
                windowDays = days,
                contentDescription = description,
                maxLanes = TIMELINE_LANES,
                memoryKey = "stats-timeline",
                emptyText = stringResource(R.string.home_upcoming_empty, days),
            ) { event ->
                val item = state.items[event.key as Long]
                if (item != null) BrandMark(item.mark, size = MarkSize.Small, modifier = Modifier.clickable(interactionSource = null, indication = null) { onOpenSubscription(item.id) })
            }
        }
        val total = StatsViewModel.timelineTotal(pins, state.currency)
        val totalText = when {
            total.count > 0 && total.otherCurrencyCount > 0 ->
                stringResource(R.string.stats_timeline_total, total.count, Formatters.formatAmount(total.amountMinor, state.currency)) +
                    "，" + stringResource(R.string.stats_timeline_other_currency, total.otherCurrencyCount)
            total.count > 0 -> stringResource(R.string.stats_timeline_total, total.count, Formatters.formatAmount(total.amountMinor, state.currency))
            total.otherCurrencyCount > 0 -> stringResource(R.string.stats_timeline_only_other, total.otherCurrencyCount)
            else -> null
        }
        if (totalText != null) {
            Text(
                totalText,
                color = env.ink.secondary,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
