package com.ftwegc.myapplication.ui.stats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.domain.calc.MonthSpend
import com.ftwegc.myapplication.domain.calc.SpendBasis
import com.ftwegc.myapplication.ui.AppViewModelProvider
import com.ftwegc.myapplication.ui.background.PageTone
import com.ftwegc.myapplication.ui.components.EntranceCompletion
import com.ftwegc.myapplication.ui.components.rememberEntranceState
import com.ftwegc.myapplication.ui.format.Formatters
import com.ftwegc.myapplication.ui.glass.GlassChip
import com.ftwegc.myapplication.ui.glass.GlassSegmented
import com.ftwegc.myapplication.ui.glass.StatusBarScrim
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.home.rise
import com.ftwegc.myapplication.ui.navigation.LocalBottomBarPadding
import com.ftwegc.myapplication.ui.preview.GlassPreview
import com.ftwegc.myapplication.ui.preview.SampleData

/** Screen margin and gap between blocks (mockup `.scroll` 15 px, `.grid` 11 px). */
internal val StatsMargin: Dp = 16.dp
internal val StatsGap: Dp = 11.dp

@Composable
fun StatsDestination(
    onAddSubscription: () -> Unit,
    onCompleteInfo: (Long) -> Unit,
    onOpenSubscription: (Long) -> Unit,
    viewModel: StatsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatsScreen(
        state = state,
        actions = StatsActions(
            onMode = viewModel::setMode,
            onBasis = viewModel::setBasis,
            onCurrency = viewModel::setCurrency,
            onYear = viewModel::setYear,
            onMoveCalendar = viewModel::moveCalendar,
            onAddSubscription = onAddSubscription,
            onCompleteInfo = onCompleteInfo,
            onOpenSubscription = onOpenSubscription,
        ),
    )
}

/** Everything the statistics page can do. */
class StatsActions(
    val onMode: (StatsMode) -> Unit = {},
    val onBasis: (SpendBasis) -> Unit = {},
    val onCurrency: (String) -> Unit = {},
    val onYear: (Int) -> Unit = {},
    val onMoveCalendar: (Long) -> Unit = {},
    val onAddSubscription: () -> Unit = {},
    val onCompleteInfo: (Long) -> Unit = {},
    val onOpenSubscription: (Long) -> Unit = {},
)

/**
 * The statistics page (mockup `vluer-screens.html`, fourth screen): the title with the month or
 * year and the 按月 / 按年 switch; key figures as a row of small glass tiles; the monthly bars
 * (cash flow or amortized, tap a bar for the month's payments) or the year view; the category
 * donut beside the ranking; the payment calendar; the traffic of every VPS; the next 90 days.
 * Charts start their entrance when they scroll into view.
 */
@Composable
fun StatsScreen(state: StatsUiState, modifier: Modifier = Modifier, actions: StatsActions = StatsActions()) {
    val bottom = LocalBottomBarPadding.current
    val scroll = rememberScrollState()
    val entrance = rememberEntranceState()
    EntranceCompletion(entrance)
    var detail by remember { mutableStateOf<MonthSpend?>(null) }
    var rankingOpen by rememberSaveable { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .statusBarsPadding()
                .padding(start = StatsMargin, end = StatsMargin, top = 6.dp, bottom = bottom + 8.dp),
            verticalArrangement = Arrangement.spacedBy(StatsGap),
        ) {
            StatsHeader(state, actions.onMode)
            if (state.isLoading) return@Column
            if (state.currencies.size > 1) CurrencyChips(state, actions.onCurrency)
            if (!state.hasAny) {
                EmptyStatsBlock(actions.onAddSubscription, Modifier.rise(0, entrance))
            } else {
                MetricStrip(state, Modifier.rise(0, entrance))
                if (state.needsInfoCount > 0) NeedsInfoBlock(state, actions.onCompleteInfo, Modifier.rise(1, entrance))
            }
            if (state.mode == StatsMode.MONTH) {
                MonthlyBlock(state, actions.onBasis, onOpenMonth = { detail = it }, modifier = Modifier.rise(1, entrance))
            } else {
                YearBlock(state, actions.onBasis, actions.onYear, onOpenMonth = { detail = it }, modifier = Modifier.rise(1, entrance))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(StatsGap)) {
                CategoryBlock(state, Modifier.weight(1f).rise(2, entrance))
                RankingBlock(state, onOpenAll = { rankingOpen = true }, modifier = Modifier.weight(1f).rise(3, entrance))
            }
            CalendarBlock(state, actions.onMoveCalendar, actions.onOpenSubscription, Modifier.rise(4, entrance))
            TrafficBlock(state, actions.onOpenSubscription, Modifier.rise(5, entrance))
            TimelineBlock(state, actions.onOpenSubscription, Modifier.rise(6, entrance))
        }
        StatusBarScrim()
    }
    MonthDetailSheet(
        month = detail,
        state = state,
        onDismiss = { detail = null },
        onOpenSubscription = {
            detail = null
            actions.onOpenSubscription(it)
        },
    )
    RankingSheet(
        visible = rankingOpen,
        state = state,
        onDismiss = { rankingOpen = false },
        onOpenSubscription = {
            rankingOpen = false
            actions.onOpenSubscription(it)
        },
    )
}

/** Mockup: `h1` 统计, "2026 年 9 月", and the 按月 / 按年 capsule (150 px). */
@Composable
private fun StatsHeader(state: StatsUiState, onMode: (StatsMode) -> Unit) {
    val env = currentGlass()
    val today = state.today
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 3.dp, end = 3.dp, top = 6.dp, bottom = 12.dp - StatsGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.stats_title),
                color = env.ink.primary,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                modifier = Modifier.semantics { heading() },
            )
            if (today != java.time.LocalDate.MIN) {
                Text(
                    if (state.mode == StatsMode.MONTH) stringResource(R.string.stats_subtitle, today.year, today.monthValue)
                    else stringResource(R.string.stats_subtitle_year, state.selectedYear),
                    color = env.ink.secondary,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        GlassSegmented(
            labels = listOf(stringResource(R.string.stats_mode_month), stringResource(R.string.stats_mode_year)),
            selected = if (state.mode == StatsMode.MONTH) 0 else 1,
            onSelect = { onMode(if (it == 0) StatsMode.MONTH else StatsMode.YEAR) },
            modifier = Modifier.width(150.dp),
        )
    }
}

/** One chip per currency (no exchange rates: each is counted on its own). */
@Composable
private fun CurrencyChips(state: StatsUiState, onCurrency: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.stats_currency_label), color = currentGlass().ink.secondary, fontSize = 12.sp, modifier = Modifier.padding(end = 2.dp))
        state.currencies.forEach { c ->
            GlassChip(
                text = "${Formatters.currencySymbol(c)} $c",
                selected = c == state.currency,
                onClick = { onCurrency(c) },
            )
        }
    }
}

// ------------------------------------------------------------------ Previews

private fun previewState(mode: StatsMode = StatsMode.MONTH, empty: Boolean = false): StatsUiState =
    StatsViewModel.build(
        all = if (empty) emptyList() else SampleData.all,
        added = emptyMap(),
        preferred = "CNY",
        choice = StatsSelection(mode = mode),
        today = SampleData.today,
    )

@PreviewLightDark
@Composable
private fun StatsPreview() {
    GlassPreview(padding = 0.dp, tone = PageTone.Stats) { StatsScreen(previewState()) }
}

@PreviewLightDark
@Composable
private fun StatsYearPreview() {
    GlassPreview(padding = 0.dp, tone = PageTone.Stats) { StatsScreen(previewState(StatsMode.YEAR)) }
}

@PreviewLightDark
@Composable
private fun StatsEmptyPreview() {
    GlassPreview(padding = 0.dp, tone = PageTone.Stats) { StatsScreen(previewState(empty = true)) }
}
