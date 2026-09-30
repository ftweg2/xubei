package com.ftwegc.myapplication.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import com.ftwegc.myapplication.ui.brand.VluerPullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.data.prefs.AppSettings
import com.ftwegc.myapplication.ui.AppViewModelProvider
import com.ftwegc.myapplication.ui.common.LocalAppMessenger
import com.ftwegc.myapplication.ui.components.EntranceCompletion
import com.ftwegc.myapplication.ui.components.crossFadeSpec
import com.ftwegc.myapplication.ui.components.rememberEntranceState
import com.ftwegc.myapplication.ui.glass.StatusBarScrim
import com.ftwegc.myapplication.ui.komari.komariErrorMessage
import com.ftwegc.myapplication.ui.navigation.LocalBottomBarPadding
import com.ftwegc.myapplication.ui.preview.GlassPreview
import com.ftwegc.myapplication.ui.preview.SampleData

/** Screen margin of the overview (mockup `.scroll`: 15 px on a 372 px phone). */
internal val HomeMargin: Dp = 16.dp

/** Gap between bento blocks (mockup `.grid`: 11 px). */
internal val BentoGap: Dp = 11.dp

@Composable
fun HomeDestination(
    onOpenSubscription: (Long) -> Unit,
    onOpenTraffic: (Long) -> Unit,
    onAddSubscription: () -> Unit,
    onSeeAll: () -> Unit,
    onOpenKomari: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenStats: () -> Unit = onSeeAll,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Collected only while the overview is started: live polling stops on other pages and in the background.
    val live by viewModel.live.collectAsStateWithLifecycle()
    val messenger = LocalAppMessenger.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel, messenger) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.RefreshFailed -> messenger?.showInfo(
                    resources.getString(R.string.traffic_refresh_failed, komariErrorMessage(resources, event.error)),
                )
                is HomeEvent.PartlyRefreshed -> messenger?.showInfo(
                    resources.getString(R.string.traffic_refreshed_partial, event.updated, event.notUpdated),
                )
            }
        }
    }
    HomeScreen(
        state = state,
        live = live,
        actions = HomeActions(
            onRefresh = viewModel::refresh,
            onOpenSubscription = onOpenSubscription,
            onOpenTraffic = onOpenTraffic,
            onAddSubscription = onAddSubscription,
            onSeeAll = onSeeAll,
            onOpenStats = onOpenStats,
            onOpenKomari = onOpenKomari,
            onOpenImport = onOpenImport,
        ),
    )
}

/** Everything the overview can open. */
class HomeActions(
    val onRefresh: () -> Unit = {},
    val onOpenSubscription: (Long) -> Unit = {},
    val onOpenTraffic: (Long) -> Unit = onOpenSubscription,
    val onAddSubscription: () -> Unit = {},
    val onSeeAll: () -> Unit = {},
    val onOpenStats: () -> Unit = {},
    val onOpenKomari: () -> Unit = {},
    val onOpenImport: () -> Unit = {},
)

private enum class HomeContent { Loading, Empty, Content }

/**
 * The overview: a bento grid of glass blocks (mockup `vluer-screens.html`, first screen, plus the
 * category and annual blocks of `home-liquid-glass.html`). Pull down to refresh the traffic.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    modifier: Modifier = Modifier,
    live: HomeLive = HomeLive(),
    actions: HomeActions = HomeActions(),
) {
    val bottomPadding = LocalBottomBarPadding.current
    val content = when {
        state.isLoading -> HomeContent.Loading
        !state.hasAny -> HomeContent.Empty
        else -> HomeContent.Content
    }
    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = content,
            transitionSpec = crossFadeSpec(),
            modifier = Modifier.fillMaxSize(),
            label = "homeContent",
        ) { kind ->
            when (kind) {
                HomeContent.Loading -> HomeSkeleton(bottomPadding)
                HomeContent.Empty -> HomeEmpty(state, bottomPadding, actions)
                HomeContent.Content -> VluerPullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = actions.onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    HomeBento(state, live, bottomPadding, actions)
                }
            }
        }
        StatusBarScrim()
    }
}

@Composable
private fun HomeBento(state: HomeUiState, live: HomeLive, bottomPadding: Dp, actions: HomeActions) {
    val entrance = rememberEntranceState()
    EntranceCompletion(entrance)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = HomeMargin, end = HomeMargin, top = 8.dp, bottom = bottomPadding + 8.dp),
        verticalArrangement = Arrangement.spacedBy(BentoGap),
    ) {
        item(key = "header") {
            HomeHeader(state, live, onOpenKomari = actions.onOpenKomari, modifier = Modifier.padding(bottom = 16.dp - BentoGap))
        }
        item(key = "spend") {
            SpendBlock(state, onClick = actions.onOpenStats, modifier = Modifier.rise(0, entrance))
        }
        item(key = "dueTraffic") {
            Row(horizontalArrangement = Arrangement.spacedBy(BentoGap)) {
                DueBlock(
                    due = state.nextDue,
                    today = state.today,
                    onOpen = actions.onOpenSubscription,
                    onAdd = actions.onAddSubscription,
                    modifier = Modifier
                        .weight(1f)
                        .rise(1, entrance),
                )
                TrafficBlock(
                    state = state,
                    onOpen = actions.onOpenTraffic,
                    onOpenKomari = actions.onOpenKomari,
                    onOpenImport = actions.onOpenImport,
                    modifier = Modifier
                        .weight(1f)
                        .rise(2, entrance),
                )
            }
        }
        item(key = "timeline") {
            TimelineBlock(state, onOpen = actions.onOpenSubscription, onSeeAll = actions.onSeeAll, modifier = Modifier.rise(3, entrance))
        }
        item(key = "nodes") {
            NodesBlock(
                state = state,
                live = live,
                onOpen = actions.onOpenTraffic,
                onOpenKomari = actions.onOpenKomari,
                onOpenImport = actions.onOpenImport,
                modifier = Modifier.rise(4, entrance),
            )
        }
        item(key = "categoryYear") {
            Row(horizontalArrangement = Arrangement.spacedBy(BentoGap)) {
                CategoryBlock(state, onClick = actions.onOpenStats, modifier = Modifier.weight(1f).rise(5, entrance))
                YearBlock(state, onClick = actions.onOpenStats, modifier = Modifier.weight(1f).rise(6, entrance))
            }
        }
    }
}

@Composable
private fun HomeSkeleton(bottomPadding: Dp) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = HomeMargin, end = HomeMargin, top = 8.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(BentoGap),
    ) {
        HomeHeader(HomeUiState(), HomeLive(), onOpenKomari = {}, modifier = Modifier.padding(bottom = 16.dp - BentoGap))
        SkeletonGlass(Modifier.fillMaxWidth().height(208.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(BentoGap)) {
            SkeletonGlass(Modifier.weight(1f).height(196.dp))
            SkeletonGlass(Modifier.weight(1f).height(196.dp))
        }
        SkeletonGlass(Modifier.fillMaxWidth().height(118.dp))
        Spacer(Modifier.height(0.dp))
    }
}

// ------------------------------------------------------------------ Previews

private fun previewState(empty: Boolean = false, loading: Boolean = false): HomeUiState =
    if (loading) HomeUiState() else HomeViewModel.build(
        all = if (empty) emptyList() else SampleData.all,
        settings = AppSettings(komariUrl = "http://10.0.2.2:8765"),
        today = SampleData.today,
        isRefreshing = false,
    )

@PreviewLightDark
@Composable
private fun HomeScreenPreview() {
    GlassPreview(padding = 0.dp) {
        HomeScreen(previewState())
    }
}

@PreviewLightDark
@Composable
private fun HomeScreenEmptyPreview() {
    GlassPreview(padding = 0.dp) {
        HomeScreen(previewState(empty = true))
    }
}
