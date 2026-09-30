package com.ftwegc.myapplication.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.ui.brand.BrandMark
import com.ftwegc.myapplication.ui.brand.MarkSize
import com.ftwegc.myapplication.ui.brand.LiveCupLogo
import com.ftwegc.myapplication.ui.brand.VluerLogo
import com.ftwegc.myapplication.ui.charts.BarChart
import com.ftwegc.myapplication.ui.charts.BarDatum
import com.ftwegc.myapplication.ui.charts.ChartColors
import com.ftwegc.myapplication.ui.charts.ChartPlaceholder
import com.ftwegc.myapplication.ui.charts.ConcentricRings
import com.ftwegc.myapplication.ui.charts.DonutChart
import com.ftwegc.myapplication.ui.charts.DonutSlice
import com.ftwegc.myapplication.ui.charts.PlaceholderKind
import com.ftwegc.myapplication.ui.charts.ProgressRing
import com.ftwegc.myapplication.ui.charts.RingSeries
import com.ftwegc.myapplication.ui.charts.SmoothAreaChart
import com.ftwegc.myapplication.ui.charts.Sparkline
import com.ftwegc.myapplication.ui.charts.TimelineChart
import com.ftwegc.myapplication.ui.charts.TimelineEvent
import com.ftwegc.myapplication.ui.charts.chartColors
import com.ftwegc.myapplication.ui.components.EntranceState
import com.ftwegc.myapplication.ui.components.animateCountAsState
import com.ftwegc.myapplication.ui.format.Formatters
import com.ftwegc.myapplication.ui.format.categoryLabel
import com.ftwegc.myapplication.ui.glass.BreathingDot
import com.ftwegc.myapplication.ui.glass.GlassChip
import com.ftwegc.myapplication.ui.glass.GlassDefaults
import com.ftwegc.myapplication.ui.glass.GlassLabel
import com.ftwegc.myapplication.ui.glass.GlassPillButton
import com.ftwegc.myapplication.ui.glass.GlassSurface
import com.ftwegc.myapplication.ui.glass.GlassThickness
import com.ftwegc.myapplication.ui.glass.LegendRow
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Inner padding of a block (mockup `.pad`: 16 px). */
private val BlockPadding = 16.dp

/**
 * First entrance of bento block [index] (mockup `.rise`): fades in, rises 16 dp and grows from
 * 97 %, 0.7 s on an overshooting curve, one block after another. Blocks shown after the entrance
 * has played (or with animations off) appear directly.
 */
@Composable
fun Modifier.rise(index: Int, state: EntranceState): Modifier {
    val reduced = LocalReducedMotion.current
    val startHidden = remember { !state.played && !reduced }
    val progress = remember { Animatable(if (startHidden) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (startHidden) progress.animateTo(1f, tween(Motion.DurationRise, Motion.riseDelay(index), Motion.RiseEasing))
    }
    LaunchedEffect(reduced) { if (reduced) progress.snapTo(1f) }
    val offset = with(LocalDensity.current) { Motion.RiseOffset.toPx() }
    return graphicsLayer {
        val p = progress.value
        // Modulated alpha: no offscreen buffer, so the glass shadows are not cut off while fading in.
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * offset
        val s = lerp(Motion.RiseScale, 1f, p)
        scaleX = s
        scaleY = s
    }
}

/** A bento block: standard glass with the mockup's padding. */
@Composable
internal fun Block(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(modifier = modifier, onClick = onClick, onClickLabel = onClickLabel) {
        Column(Modifier.padding(BlockPadding)) { content() }
    }
}

// ------------------------------------------------------------------ Header

/** The mark in the overview header (mockup logo-xubei.html: 52 px beside the title). */
private val HeaderMarkSize = 52.dp

@Composable
internal fun HomeHeader(state: HomeUiState, live: HomeLive, onOpenKomari: () -> Unit, modifier: Modifier = Modifier) {
    val env = currentGlass()
    val date = if (state.today == LocalDate.MIN) "" else
        DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern_header), Locale.CHINA).format(state.today)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LiveCupLogo(refreshing = state.isRefreshing, size = HeaderMarkSize)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), color = env.ink.primary, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
            Text(date, color = env.ink.secondary, fontSize = 12.5.sp, lineHeight = 16.sp, maxLines = 1)
        }
        if (state.today != LocalDate.MIN && state.hasAny) StatusCapsule(state, live, onOpenKomari)
    }
}

/** Mockup: a glass capsule with the breathing dot and "N 台在线". */
@Composable
private fun StatusCapsule(state: HomeUiState, live: HomeLive, onOpenKomari: () -> Unit) {
    val env = currentGlass()
    val nodes = state.nodes
    val online = nodes.count { n -> live.series[n.id]?.online ?: n.storedOnline }
    val text = when {
        nodes.isEmpty() -> stringResource(R.string.home_active_subs, state.activeCount)
        online == nodes.size -> stringResource(R.string.home_online_count, online)
        else -> stringResource(R.string.home_online_partial, online, nodes.size)
    }
    GlassSurface(
        shape = GlassDefaults.CapsuleShape,
        onClick = if (nodes.isNotEmpty() || state.komariConfigured) onOpenKomari else null,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (nodes.isNotEmpty()) BreathingDot(online = online > 0, color = if (online == nodes.size) Color(0xFF2DD4BF) else Color(0xFFFBBF24))
            Text(text, color = env.ink.primary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------ Spend

/** Mockup "本月支出": big number, chips, the spending pace curve. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpendBlock(state: HomeUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val env = currentGlass()
    val chart = chartColors()
    val overview = state.overview
    val currency = overview?.currency ?: state.defaultCurrency
    val thisMonth = overview?.thisMonthMinor ?: 0L
    val description = spendDescription(overview, currency)
    Block(modifier.fillMaxWidth(), onClick = onClick, onClickLabel = stringResource(R.string.home_open_stats)) {
        GlassLabel(stringResource(R.string.home_spend_label))
        HeroAmount(thisMonth, currency, Modifier.padding(top = 5.dp).semantics { contentDescription = description })
        FlowRow(
            Modifier.padding(top = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            overview?.otherCurrencies?.forEach { GlassChip(Formatters.formatAmount(it.minor, it.currency)) }
            if (overview != null) {
                val delta = overview.deltaMinor
                val deltaText = when {
                    delta < 0 -> stringResource(R.string.home_spend_less, Formatters.formatAmount(-delta, currency))
                    delta > 0 -> stringResource(R.string.home_spend_more, Formatters.formatAmount(delta, currency))
                    else -> stringResource(R.string.home_spend_same)
                }
                GlassChip(deltaText, color = if (delta <= 0) chart.mint else chart.amber)
            }
            GlassChip(stringResource(R.string.home_spend_count, state.activeCount))
        }
        val pace = overview?.pace.orEmpty()
        if (overview != null && pace.isNotEmpty() && pace.last() > 0L) {
            val today = overview.todayIndex.coerceIn(0, pace.lastIndex)
            SmoothAreaChart(
                values = pace.take(today + 1).map { it.toFloat() }.let { if (it.size < 2) listOf(0f) + it else it },
                forecast = pace.drop(today + 1).map { it.toFloat() },
                slots = pace.size.coerceAtLeast(2),
                contentDescription = description,
                memoryKey = "home-pace",
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            ChartPlaceholder(
                stringResource(R.string.home_spend_none),
                Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(86.dp),
                PlaceholderKind.Line,
            )
        }
    }
}

@Composable
private fun spendDescription(overview: HomeOverview?, currency: String): String {
    if (overview == null) return stringResource(R.string.home_spend_none)
    val today = overview.pace.getOrElse(overview.todayIndex) { 0L }
    return stringResource(
        R.string.home_spend_desc,
        Formatters.formatAmount(overview.thisMonthMinor, currency),
        Formatters.formatAmount(overview.lastMonthMinor, currency),
        Formatters.formatAmount(today, currency),
    )
}

/** Mockup `.num`: symbol 17 px .7 raised, integer 44 px, decimals 21 px .75. Counts up once. */
@Composable
private fun HeroAmount(minor: Long, currency: String, modifier: Modifier = Modifier) {
    val env = currentGlass()
    val shown by animateCountAsState(minor, countFromZero = true, memoryKey = "home-hero-$currency")
    val parts = Formatters.amountParts(shown, currency)
    val dim = env.ink.primary.copy(alpha = 0.72f)
    Text(
        buildAnnotatedString {
            if (parts.negative) append('-')
            withStyle(SpanStyle(fontSize = 17.sp, color = dim, fontWeight = FontWeight.Medium, baselineShift = BaselineShift(0.62f))) {
                append(parts.symbol)
                append(' ')
            }
            append(parts.integer)
            withStyle(SpanStyle(fontSize = 21.sp, color = dim)) {
                append('.')
                append(parts.fraction)
            }
        },
        modifier = modifier,
        color = env.ink.primary,
        fontSize = 44.sp,
        lineHeight = 46.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        maxLines = 1,
    )
}

// ------------------------------------------------------------------ Due ring

/** Mockup "最近到期": the ring of the current cycle, days left in the middle, the mark below. */
@Composable
internal fun DueBlock(due: HomeDue?, today: LocalDate, onOpen: (Long) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val env = currentGlass()
    val chart = chartColors()
    Block(
        modifier.heightIn(min = BlockMinHeight),
        onClick = if (due != null) ({ onOpen(due.id) }) else onAdd,
        onClickLabel = if (due != null) stringResource(R.string.home_open_detail) else stringResource(R.string.home_empty_action),
    ) {
        GlassLabel(stringResource(R.string.home_due_label))
        if (due == null) {
            ChartPlaceholder(
                stringResource(R.string.home_due_none),
                Modifier
                    .padding(top = 9.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(112.dp),
                PlaceholderKind.Ring,
            )
            return@Block
        }
        val overdue = due.daysLeft < 0
        val color = when {
            overdue -> chart.coral
            due.daysLeft <= DUE_WARN_DAYS -> chart.amber
            else -> chart.teal
        }
        val (number, unit) = when {
            overdue -> (-due.daysLeft).toString() to stringResource(R.string.home_due_overdue_unit)
            due.daysLeft == 0L -> stringResource(R.string.home_due_today_short) to stringResource(R.string.home_due_today_unit)
            else -> due.daysLeft.toString() to stringResource(R.string.home_due_after_unit)
        }
        val description = stringResource(R.string.home_due_desc, due.name, "$number $unit", (due.elapsed * 100).toInt())
        ProgressRing(
            fraction = due.elapsed,
            contentDescription = description,
            colors = listOf(color),
            memoryKey = "home-due-${due.id}",
            modifier = Modifier
                .padding(top = 9.dp, bottom = 7.dp)
                .align(Alignment.CenterHorizontally),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(number, color = env.ink.primary, fontSize = 30.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(unit, color = env.ink.secondary, fontSize = 11.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
        Row(
            Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            BrandMark(due.mark, size = MarkSize.Small)
            Text(due.name, color = env.ink.primary, fontSize = 12.5.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Amber from this many days before the due date (the mockup's "3 天后" ring is amber). */
private const val DUE_WARN_DAYS = 7L

/** Half-width blocks keep the same height in a row. */
private val BlockMinHeight = 196.dp

// ------------------------------------------------------------------ Traffic

/** Mockup "流量总览": concentric rings, one per VPS, and a legend. */
@Composable
internal fun TrafficBlock(
    state: HomeUiState,
    onOpen: (Long) -> Unit,
    onOpenKomari: () -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chart = chartColors()
    val rings = state.rings
    val top = rings.firstOrNull()
    val click: (() -> Unit)? = when {
        top != null -> ({ onOpen(top.id) })
        state.vpsCount > 0 && !state.komariConfigured -> onOpenKomari
        state.vpsCount > 0 && state.traffic.isEmpty() -> onOpenImport
        else -> null
    }
    Block(modifier.heightIn(min = BlockMinHeight), onClick = click) {
        GlassLabel(stringResource(R.string.home_traffic_label))
        when {
            rings.isNotEmpty() -> {
                val colors = ringColors(chart)
                val description = stringResource(
                    R.string.home_traffic_desc,
                    rings.joinToString("，") { "${it.name} ${Formatters.formatPercent(it.percent)}" },
                )
                ConcentricRings(
                    series = rings.mapIndexed { i, r -> RingSeries((r.percent / 100).toFloat(), colors[i % colors.size], r.name) },
                    contentDescription = description,
                    memoryKey = "home-rings",
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.CenterHorizontally),
                )
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    rings.take(LEGEND_ROWS).forEachIndexed { i, r ->
                        LegendRow(colors[i % colors.size], r.name, Formatters.formatPercent(r.percent))
                    }
                }
            }
            else -> {
                val (text, action) = when {
                    state.vpsCount == 0 -> stringResource(R.string.home_traffic_no_vps) to null
                    !state.komariConfigured -> stringResource(R.string.home_traffic_setup_title) to stringResource(R.string.home_traffic_setup_action)
                    state.traffic.isEmpty() -> stringResource(R.string.home_traffic_unlinked_title) to stringResource(R.string.home_traffic_import_action)
                    else -> stringResource(R.string.home_traffic_no_limit) to null
                }
                EmptyInBlock(text, action, onAction = click ?: {}, kind = PlaceholderKind.Ring)
            }
        }
    }
}

private const val LEGEND_ROWS = 3

/** Mockup ring order: coral, amber, teal, blue. */
private fun ringColors(chart: ChartColors): List<Color> = listOf(chart.coral, chart.amber, chart.teal, chart.blue)

/** An empty block body: a placeholder shape, one sentence and an optional action. */
@Composable
private fun EmptyInBlock(text: String, action: String?, onAction: () -> Unit, kind: PlaceholderKind) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChartPlaceholder(text, Modifier.size(width = 132.dp, height = 104.dp), kind)
        if (action != null) GlassPillButton(action, onAction)
    }
}

// ------------------------------------------------------------------ Timeline

/** Mockup "未来 30 天": the marks of the subscriptions due, on their dates. */
@Composable
internal fun TimelineBlock(state: HomeUiState, onOpen: (Long) -> Unit, onSeeAll: () -> Unit, modifier: Modifier = Modifier) {
    val days = HomeViewModel.UPCOMING_DAYS
    val pins = state.timeline
    val names = remember(pins) { pins.associate { it.id to it.mark.name } }
    val dateFormat = remember { DateTimeFormatter.ofPattern("M/d", Locale.CHINA) }
    Block(modifier.fillMaxWidth(), onClick = onSeeAll, onClickLabel = stringResource(R.string.home_see_all)) {
        GlassLabel(stringResource(R.string.home_timeline_label, days))
        val description = if (pins.isEmpty()) {
            stringResource(R.string.home_upcoming_empty, days)
        } else {
            stringResource(
                R.string.home_timeline_desc,
                days,
                pins.size,
                pins.joinToString("，") { "${names[it.id]} ${it.date.format(dateFormat)}" },
            )
        }
        TimelineChart(
            events = pins.map { TimelineEvent(it.id, it.dayOffset, if (it.overdue) stringResource(R.string.home_due_overdue_unit) else it.date.format(dateFormat), it.overdue) },
            windowDays = days,
            contentDescription = description,
            memoryKey = "home-timeline",
            emptyText = stringResource(R.string.home_upcoming_empty, days),
            modifier = Modifier.padding(top = 12.dp),
        ) { event ->
            val pin = pins.first { it.id == event.key }
            Box(Modifier.semantics { contentDescription = names[pin.id].orEmpty() }) {
                BrandMark(
                    pin.mark,
                    size = MarkSize.Small,
                    modifier = Modifier.clickable(interactionSource = null, indication = null) { onOpen(pin.id) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Live nodes

/** Mockup "节点实时": a cell per VPS with the online dot, the upload speed line and its value. */
@Composable
internal fun NodesBlock(
    state: HomeUiState,
    live: HomeLive,
    onOpen: (Long) -> Unit,
    onOpenKomari: () -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chart = chartColors()
    Block(modifier.fillMaxWidth()) {
        GlassLabel(stringResource(R.string.home_nodes_label))
        when {
            !state.komariConfigured -> NodesGuide(stringResource(R.string.home_nodes_setup), stringResource(R.string.home_traffic_setup_action), onOpenKomari)
            state.nodes.isEmpty() -> NodesGuide(stringResource(R.string.home_nodes_unlinked), stringResource(R.string.home_traffic_import_action), onOpenImport)
            else -> Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val colors = listOf(chart.mint, chart.coral, chart.blue, chart.amber, chart.violet, chart.teal)
                state.nodes.chunked(NODE_COLUMNS).forEachIndexed { row, cells ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cells.forEachIndexed { col, node ->
                            val index = row * NODE_COLUMNS + col
                            NodeCell(node, live, colors[index % colors.size], onOpen, Modifier.weight(1f))
                        }
                        repeat(NODE_COLUMNS - cells.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private const val NODE_COLUMNS = 3

@Composable
private fun NodesGuide(text: String, action: String, onAction: () -> Unit) {
    val env = currentGlass()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text, color = env.ink.secondary, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        GlassPillButton(action, onAction)
    }
}

/** Mockup `.fld` node cell: 16 px corners, a flat glass inside the block. */
@Composable
private fun NodeCell(node: HomeNode, live: HomeLive, color: Color, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val env = currentGlass()
    val series = live.series[node.id]
    val online = series?.online ?: node.storedOnline
    val speed = series?.latestUp
    val speedText = when {
        series == null && !live.loaded -> stringResource(R.string.home_node_waiting)
        !online -> stringResource(R.string.home_node_offline)
        else -> stringResource(R.string.home_node_speed, Formatters.formatBytes(speed ?: 0L))
    }
    val description = stringResource(
        R.string.home_node_desc,
        node.name,
        stringResource(if (online) R.string.home_node_online else R.string.home_node_offline),
        Formatters.formatBytes(series?.latestUp ?: 0L),
        Formatters.formatBytes(series?.latestDown ?: 0L),
    )
    GlassSurface(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        shape = GlassDefaults.CellShape,
        thickness = GlassThickness.Thin,
        onClick = { onOpen(node.id) },
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BreathingDot(online)
                Text(node.name, color = env.ink.primary, fontSize = 11.5.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val values = series?.up?.map { it.toFloat() }.orEmpty()
            if (online && values.size >= 2) {
                Sparkline(values, description, color = color, memoryKey = "home-node-${node.id}")
            } else {
                Spacer(Modifier.height(26.dp))
            }
            Text(speedText, color = env.ink.secondary, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1)
        }
    }
}

// ------------------------------------------------------------------ Category and year

/** Early mockup "分类占比": a donut (tap a slice) with the share in the middle. */
@Composable
internal fun CategoryBlock(state: HomeUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val env = currentGlass()
    val chart = chartColors()
    val shares = state.overview?.categories.orEmpty()
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Block(modifier.heightIn(min = BlockMinHeight), onClick = onClick, onClickLabel = stringResource(R.string.home_open_stats)) {
        GlassLabel(stringResource(R.string.home_category_label))
        if (shares.isEmpty()) {
            ChartPlaceholder(stringResource(R.string.chart_empty), Modifier.padding(top = 10.dp).align(Alignment.CenterHorizontally).size(96.dp), PlaceholderKind.Ring)
            return@Block
        }
        val colorOf = { c: Category -> categoryChartColor(c, chart) }
        val labels = shares.map { categoryLabel(it.category) }
        val description = stringResource(
            R.string.home_category_desc,
            shares.indices.joinToString("，") { "${labels[it]} ${Formatters.formatPercent(shares[it].fraction * 100)}" },
        )
        val pick = selected.coerceIn(0, shares.lastIndex)
        DonutChart(
            slices = shares.mapIndexed { i, s -> DonutSlice(s.monthlyMinor.toFloat(), colorOf(s.category), labels[i]) },
            contentDescription = description,
            size = 96.dp,
            selected = pick,
            onSelect = { if (it >= 0) selected = it },
            memoryKey = "home-category",
            modifier = Modifier
                .padding(top = 10.dp)
                .align(Alignment.CenterHorizontally),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Formatters.formatPercent(shares[pick].fraction * 100),
                    color = env.ink.primary,
                    fontSize = 17.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(labels[pick], color = env.ink.secondary, fontSize = 10.5.sp, lineHeight = 13.sp)
            }
        }
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            shares.forEachIndexed { i, s ->
                LegendRow(colorOf(s.category), labels[i], Formatters.formatPercent(s.fraction * 100))
            }
        }
    }
}

/** Stats mockup: AI violet, VPS blue, other teal. */
private fun categoryChartColor(category: Category, chart: ChartColors): Color = when (category) {
    Category.AI -> chart.violet
    Category.VPS -> chart.blue
    Category.OTHER -> chart.teal
}

/** Early mockup "全年预估": the year's total and a bar per month, future months light. */
@Composable
internal fun YearBlock(state: HomeUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val env = currentGlass()
    val overview = state.overview
    Block(modifier.heightIn(min = BlockMinHeight), onClick = onClick, onClickLabel = stringResource(R.string.home_open_stats)) {
        GlassLabel(stringResource(R.string.home_year_label))
        if (overview == null || overview.yearTotalMinor <= 0L) {
            ChartPlaceholder(stringResource(R.string.chart_empty), Modifier.padding(top = 10.dp).fillMaxWidth().height(96.dp), PlaceholderKind.Bars)
            return@Block
        }
        val shown by animateCountAsState(overview.yearTotalMinor, countFromZero = true, memoryKey = "home-year-${overview.currency}")
        val parts = Formatters.amountParts(shown, overview.currency)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 14.sp, color = env.ink.primary.copy(alpha = 0.72f))) {
                    append(parts.symbol)
                    append(' ')
                }
                append(parts.integer)
            },
            color = env.ink.primary,
            fontSize = 27.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
        val description = stringResource(
            R.string.home_year_desc,
            state.today.year,
            Formatters.formatAmount(overview.yearTotalMinor, overview.currency),
        )
        BarChart(
            bars = overview.yearByMonth.mapIndexed { i, v ->
                BarDatum(v.toFloat(), future = i > overview.monthIndex, current = i == overview.monthIndex)
            },
            contentDescription = description,
            height = 46.dp,
            gap = 4.dp,
            memoryKey = "home-year-bars",
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            stringResource(R.string.home_year_future),
            color = env.ink.secondary,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ------------------------------------------------------------------ Empty and loading

/** No subscription yet: a welcome block with the mark and the add action, and the Komari hint. */
@Composable
internal fun HomeEmpty(state: HomeUiState, bottomPadding: androidx.compose.ui.unit.Dp, actions: HomeActions) {
    val env = currentGlass()
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = HomeMargin, end = HomeMargin, top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(BentoGap),
    ) {
        HomeHeader(state, HomeLive(), onOpenKomari = actions.onOpenKomari, modifier = Modifier.padding(bottom = 16.dp - BentoGap))
        Block(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                VluerLogo(size = 72.dp, modifier = Modifier.padding(top = 8.dp))
                Text(
                    stringResource(R.string.home_empty_title),
                    color = env.ink.primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    stringResource(R.string.home_empty_message),
                    color = env.ink.secondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp, start = 8.dp, end = 8.dp),
                )
                Spacer(Modifier.height(16.dp))
                GlassPillButton(stringResource(R.string.home_empty_action), actions.onAddSubscription)
                Spacer(Modifier.height(8.dp))
            }
        }
        Block(Modifier.fillMaxWidth(), onClick = actions.onOpenKomari) {
            GlassLabel(stringResource(R.string.home_nodes_label))
            Text(
                stringResource(R.string.home_nodes_setup),
                color = env.ink.secondary,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(BentoGap)) {
            Block(Modifier.weight(1f)) {
                GlassLabel(stringResource(R.string.home_due_label))
                ChartPlaceholder(stringResource(R.string.home_due_none), Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally).size(96.dp), PlaceholderKind.Ring)
            }
            Block(Modifier.weight(1f)) {
                GlassLabel(stringResource(R.string.home_year_label))
                ChartPlaceholder(stringResource(R.string.chart_empty), Modifier.padding(top = 8.dp).fillMaxWidth().height(96.dp), PlaceholderKind.Bars)
            }
        }
    }
}

/** A glass block without content while loading. */
@Composable
internal fun SkeletonGlass(modifier: Modifier = Modifier) {
    GlassSurface(modifier = modifier) { Box(Modifier.width(1.dp)) }
}
