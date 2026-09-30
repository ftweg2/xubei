package com.ftwegc.myapplication.ui.subscriptions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.data.prefs.AppSettings
import com.ftwegc.myapplication.data.prefs.SortOrder
import com.ftwegc.myapplication.domain.calc.RenewMode
import com.ftwegc.myapplication.ui.AppViewModelProvider
import com.ftwegc.myapplication.ui.common.LocalAppMessenger
import com.ftwegc.myapplication.ui.common.SubscriptionUi
import com.ftwegc.myapplication.ui.components.crossFadeSpec
import com.ftwegc.myapplication.ui.format.Formatters
import com.ftwegc.myapplication.ui.format.categoryLabel
import com.ftwegc.myapplication.ui.glass.GlassChip
import com.ftwegc.myapplication.ui.glass.GlassIconButton
import com.ftwegc.myapplication.ui.glass.GlassMenu
import com.ftwegc.myapplication.ui.glass.GlassMenuItem
import com.ftwegc.myapplication.ui.glass.GlassSurface
import com.ftwegc.myapplication.ui.glass.GlassTextField
import com.ftwegc.myapplication.ui.glass.LineIcons
import com.ftwegc.myapplication.ui.glass.StatusBarScrim
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.navigation.LocalBottomBarPadding
import com.ftwegc.myapplication.ui.navigation.SharedKeys
import com.ftwegc.myapplication.ui.preview.GlassPreview
import com.ftwegc.myapplication.ui.preview.SampleData
import com.ftwegc.myapplication.ui.subscriptions.editor.EditorSession
import com.ftwegc.myapplication.ui.subscriptions.editor.LocalEditor
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion
import com.ftwegc.myapplication.ui.theme.motionSpec
import com.ftwegc.myapplication.ui.format.Formatters.formatAmount
import java.time.LocalDate

/** Screen margin (mockup `.scroll`: 15 px) and header spacing. */
private val ScreenMargin = 16.dp

@Composable
fun SubscriptionListDestination(
    onOpenSubscription: (Long) -> Unit,
    viewModel: SubscriptionListViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources by viewModel.resources.collectAsStateWithLifecycle()
    val messenger = LocalAppMessenger.current
    val strings = LocalResources.current
    val editor = LocalEditor.current
    val sameYear = stringResource(R.string.date_pattern_same_year)
    val fullYear = stringResource(R.string.date_pattern_full)
    LaunchedEffect(viewModel, messenger) {
        viewModel.events.collect { event ->
            when (event) {
                is ListEvent.Renewed -> messenger?.showSuccess(
                    message = strings.getString(R.string.detail_renewed, Formatters.formatDate(event.outcome.newDue, event.today, sameYear, fullYear)),
                    actionLabel = strings.getString(R.string.action_undo),
                    onAction = { viewModel.undoRenew(event.outcome) },
                )
                ListEvent.RenewUndone -> messenger?.showInfo(strings.getString(R.string.detail_renew_undone))
                is ListEvent.ArchiveChanged -> messenger?.showSuccess(
                    strings.getString(if (event.archived) R.string.detail_archived_done else R.string.detail_unarchived_done),
                )
                is ListEvent.Deleted -> messenger?.showSuccess(strings.getString(R.string.detail_deleted, event.name))
                is ListEvent.Failed -> messenger?.showInfo(strings.getString(R.string.detail_error, event.message))
            }
        }
    }
    SubscriptionListScreen(
        state = state,
        resources = resources,
        actions = ListActions(
            onFilterChange = viewModel::setFilter,
            onQueryChange = viewModel::setQuery,
            onSortChange = viewModel::setSortOrder,
            onShowArchivedChange = viewModel::setShowArchived,
            onGridChange = viewModel::setGrid,
            onOpen = onOpenSubscription,
            onAdd = { editor?.open(EditorSession.NEW_ID) },
            onEdit = { editor?.open(it) },
            onRenew = viewModel::markRenewed,
            onArchive = viewModel::setArchived,
            onDelete = viewModel::delete,
        ),
    )
}

/** Everything the subscription page can do. */
class ListActions(
    val onFilterChange: (CategoryFilter) -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onSortChange: (SortOrder) -> Unit = {},
    val onShowArchivedChange: (Boolean) -> Unit = {},
    val onGridChange: (Boolean) -> Unit = {},
    val onOpen: (Long) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onEdit: (Long) -> Unit = {},
    val onRenew: (Long, RenewMode) -> Unit = { _, _ -> },
    val onArchive: (Long, Boolean) -> Unit = { _, _ -> },
    val onDelete: (Long, String) -> Unit = { _, _ -> },
)

private enum class ListContent { Loading, Empty, FilteredEmpty, SearchEmpty, Content }

/**
 * The subscription page (mockup `vluer-screens.html`, second screen): a large title with the
 * active count and monthly average, search and view buttons, glass filter chips, the subscription
 * due soonest as a wide amber bar, and the rest as glass tiles with their marks and due rings (or
 * as compact rows). A long press opens a glass menu (edit, renewed, archive, delete).
 */
@Composable
fun SubscriptionListScreen(
    state: SubscriptionListUiState,
    modifier: Modifier = Modifier,
    resources: Map<Long, NodeResources> = emptyMap(),
    actions: ListActions = ListActions(),
) {
    val reduced = LocalReducedMotion.current
    val bottomPadding = LocalBottomBarPadding.current
    val scroll = rememberScrollState()
    var viewport by remember { mutableIntStateOf(0) }
    val morph = animateFloatAsState(
        targetValue = if (state.grid) 0f else 1f,
        animationSpec = if (reduced) tween(0) else tween(Motion.DurationMorph, easing = Motion.MorphEasing),
        label = "tileMorph",
    )
    var searchOpen by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }
    var menuFor by remember { mutableStateOf<SubscriptionUi?>(null) }
    var menuAnchor by remember { mutableStateOf<Rect?>(null) }
    var sortOpen by remember { mutableStateOf(false) }
    var sortAnchor by remember { mutableStateOf<Rect?>(null) }
    var renewFor by remember { mutableStateOf<RenewChoice?>(null) }
    var deleteFor by remember { mutableStateOf<SubscriptionUi?>(null) }
    val content = when {
        state.isLoading -> ListContent.Loading
        state.isEmpty -> ListContent.Empty
        state.isFilteredEmpty && state.searching -> ListContent.SearchEmpty
        state.isFilteredEmpty -> ListContent.FilteredEmpty
        else -> ListContent.Content
    }
    val openMenu: (SubscriptionUi, Rect) -> Unit = { item, rect ->
        menuFor = item
        menuAnchor = rect
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it.height },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .statusBarsPadding()
                .padding(horizontal = ScreenMargin)
                .padding(top = 6.dp, bottom = bottomPadding + 8.dp),
        ) {
            ListHeader(
                state = state,
                searchOpen = searchOpen,
                onSearch = { searchOpen = true },
                onToggleView = { actions.onGridChange(!state.grid) },
                onSort = { rect ->
                    sortAnchor = rect
                    sortOpen = true
                },
            )
            AnimatedVisibility(
                visible = searchOpen,
                enter = expandHorizontally(motionSpec(tween(Motion.DurationSearch, easing = Motion.Emphasized)), expandFrom = Alignment.End) +
                    expandVertically(motionSpec(tween(Motion.DurationSearch, easing = Motion.Emphasized))) +
                    fadeIn(motionSpec(tween(Motion.DurationSearch))),
                exit = shrinkHorizontally(motionSpec(tween(Motion.DurationShort, easing = Motion.EmphasizedAccelerate)), shrinkTowards = Alignment.End) +
                    shrinkVertically(motionSpec(tween(Motion.DurationShort))) +
                    fadeOut(motionSpec(tween(Motion.DurationShort))),
            ) {
                SearchField(
                    query = state.query,
                    onQueryChange = actions.onQueryChange,
                    onClose = {
                        actions.onQueryChange("")
                        searchOpen = false
                    },
                )
            }
            if (content != ListContent.Loading && content != ListContent.Empty) {
                FilterChips(state, actions.onFilterChange)
            }
            AnimatedContent(targetState = content, transitionSpec = crossFadeSpec(), label = "listContent") { kind ->
                when (kind) {
                    ListContent.Loading -> ListSkeleton()
                    ListContent.Empty -> LogoEmptyState(
                        title = stringResource(R.string.list_empty_title),
                        message = stringResource(R.string.list_empty_message),
                        action = stringResource(R.string.list_empty_action),
                        onAction = actions.onAdd,
                        modifier = Modifier.padding(top = 72.dp),
                    )
                    ListContent.FilteredEmpty -> LogoEmptyState(
                        title = if (state.filter == CategoryFilter.NEEDS_INFO) stringResource(R.string.list_needs_info_empty) else stringResource(R.string.list_filtered_empty_title),
                        message = stringResource(R.string.list_filtered_empty_message),
                        action = stringResource(R.string.list_filtered_empty_action),
                        onAction = { actions.onFilterChange(CategoryFilter.ALL) },
                        modifier = Modifier.padding(top = 48.dp),
                    )
                    ListContent.SearchEmpty -> LogoEmptyState(
                        title = stringResource(R.string.list_search_empty, state.query.trim()),
                        message = stringResource(R.string.list_search_empty_message),
                        action = stringResource(R.string.list_search_clear),
                        onAction = { actions.onQueryChange("") },
                        modifier = Modifier.padding(top = 48.dp),
                    )
                    ListContent.Content -> Column {
                        val pinned = state.pinned
                        AnimatedContent(targetState = pinned, transitionSpec = crossFadeSpec(), contentKey = { it?.id }, label = "pinned") { p ->
                            if (p != null) {
                                PinnedDueBar(
                                    item = p,
                                    onClick = { actions.onOpen(p.id) },
                                    onLongPress = { openMenu(p, it) },
                                    modifier = Modifier.padding(bottom = 11.dp),
                                )
                            }
                        }
                        SubscriptionCollection(
                            items = state.collection,
                            morph = morph,
                            scroll = scroll,
                            viewportHeight = { viewport },
                            reduced = reduced,
                        ) { item, leaving ->
                            SubscriptionTile(
                                item = item,
                                morph = morph,
                                resources = resources[item.id],
                                onClick = { if (!leaving) actions.onOpen(item.id) },
                                onLongPress = { if (!leaving) openMenu(item, it) },
                            )
                        }
                        if (state.hiddenArchivedCount > 0) {
                            Text(
                                stringResource(R.string.list_archived_hidden, state.hiddenArchivedCount),
                                color = currentGlass().ink.secondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 18.dp),
                            )
                        }
                    }
                }
            }
        }
        StatusBarScrim()
    }

    val target = menuFor
    GlassMenu(
        visible = target != null,
        anchor = menuAnchor,
        onDismissRequest = { menuFor = null },
        items = target?.let { item ->
            buildList {
                add(GlassMenuItem(stringResource(R.string.detail_edit), Icons.Rounded.Edit) { actions.onEdit(item.id) })
                if (item.canRenew && !item.needsBillingInfo && item.renewFromDue != null) {
                    add(GlassMenuItem(stringResource(R.string.detail_mark_renewed), Icons.Rounded.EventRepeat) { renewFor = item.renewChoice() })
                }
                add(
                    GlassMenuItem(
                        stringResource(if (item.archived) R.string.detail_unarchive else R.string.detail_archive),
                        if (item.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                    ) { actions.onArchive(item.id, !item.archived) },
                )
                add(GlassMenuItem(stringResource(R.string.detail_delete), Icons.Rounded.DeleteOutline, destructive = true) { deleteFor = item })
            }
        }.orEmpty(),
    )
    GlassMenu(
        visible = sortOpen,
        anchor = sortAnchor,
        onDismissRequest = { sortOpen = false },
        items = listOf(
            GlassMenuItem(stringResource(R.string.sort_due_date), Icons.Rounded.Event, selected = state.sortOrder == SortOrder.DUE_DATE) { actions.onSortChange(SortOrder.DUE_DATE) },
            GlassMenuItem(stringResource(R.string.sort_price), Icons.Rounded.Payments, selected = state.sortOrder == SortOrder.PRICE) { actions.onSortChange(SortOrder.PRICE) },
            GlassMenuItem(stringResource(R.string.sort_name), Icons.Rounded.SortByAlpha, selected = state.sortOrder == SortOrder.NAME) { actions.onSortChange(SortOrder.NAME) },
            GlassMenuItem(
                stringResource(R.string.list_show_archived),
                if (state.showArchived) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                selected = state.showArchived,
            ) { actions.onShowArchivedChange(!state.showArchived) },
        ),
    )
    RenewSheet(
        choice = renewFor,
        today = LocalDate.now(com.ftwegc.myapplication.ui.common.appClock()),
        onDismiss = { renewFor = null },
        onConfirm = { id, mode ->
            renewFor = null
            actions.onRenew(id, mode)
        },
    )
    val deleting = com.ftwegc.myapplication.ui.glass.rememberLatest(deleteFor)
    DeleteSubscriptionDialog(
        visible = deleteFor != null,
        name = deleting?.name.orEmpty(),
        onDismiss = { deleteFor = null },
        onConfirm = {
            val item = deleteFor
            deleteFor = null
            if (item != null) actions.onDelete(item.id, item.name)
        },
    )
}

/** Mockup: `h1` 30 px, "9 个生效 · 月均 ¥386", then the search and view buttons (and sorting). */
@Composable
private fun ListHeader(
    state: SubscriptionListUiState,
    searchOpen: Boolean,
    onSearch: () -> Unit,
    onToggleView: () -> Unit,
    onSort: (Rect) -> Unit,
) {
    val env = currentGlass()
    var sortBounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 3.dp, end = 3.dp, top = 6.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.list_title), color = env.ink.primary, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
            val sub = when {
                state.isLoading -> ""
                state.monthly != null -> stringResource(R.string.list_subtitle, state.activeCount, formatAmount(state.monthly.minor, state.monthly.currency).substringBefore('.'))
                else -> stringResource(R.string.list_subtitle_count, state.activeCount)
            }
            Text(sub, color = env.ink.secondary, fontSize = 12.5.sp, lineHeight = 16.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
        }
        if (!state.isLoading && !state.isEmpty) {
            AnimatedVisibility(visible = !searchOpen, enter = fadeIn(), exit = fadeOut()) {
                GlassIconButton(LineIcons.Search, stringResource(R.string.list_search_desc), onSearch, glassTint = HeaderTint)
            }
            GlassIconButton(
                if (state.grid) LineIcons.Rows else LineIcons.Grid,
                stringResource(if (state.grid) R.string.list_view_rows else R.string.list_view_tiles),
                onToggleView,
                glassTint = HeaderTint,
            )
            GlassIconButton(
                LineIcons.Sort,
                stringResource(R.string.list_sort_desc),
                { onSort(sortBounds) },
                modifier = Modifier.onGloballyPositioned { sortBounds = it.boundsInRoot() },
                glassTint = HeaderTint,
            )
        }
    }
}

/** Mockup: the header buttons are violet glass. */
private val HeaderTint = Color(0xFF8B5CF6)

/** A glass search field that opened out of the search button. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Focus (and the keyboard) only when the search is opened, not when the page comes back to
    // an open search (back from a detail page, or after the process was killed).
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focusedOnce) {
            focusedOnce = true
            runCatching { focus.requestFocus() }
        }
    }
    GlassTextField(
        value = query,
        onValueChange = onQueryChange,
        label = stringResource(R.string.list_search_label),
        placeholder = stringResource(R.string.list_search_placeholder),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .focusRequester(focus),
        trailing = {
            com.ftwegc.myapplication.ui.glass.GlassPlainIconButton(LineIcons.Close, stringResource(R.string.list_search_close), onClose, iconSize = 20.dp)
        },
    )
}

/** Mockup `.chips`: 全部 9 · AI 4 · VPS 4 · 其他 1 · 待补充 2, scrolling sideways. */
@Composable
private fun FilterChips(state: SubscriptionListUiState, onFilterChange: (CategoryFilter) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 2.dp, end = 2.dp, bottom = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        CategoryFilter.entries.forEach { filter ->
            val count = state.counts[filter] ?: 0
            if (filter == CategoryFilter.NEEDS_INFO && count == 0 && state.filter != filter) return@forEach
            val label = when (filter) {
                CategoryFilter.ALL -> stringResource(R.string.list_filter_all)
                CategoryFilter.NEEDS_INFO -> stringResource(R.string.billing_incomplete_tag)
                else -> categoryLabel(filter.category!!)
            }
            GlassChip(
                text = stringResource(R.string.list_chip_count, label, count),
                selected = filter == state.filter,
                onClick = { onFilterChange(filter) },
            )
        }
    }
}

@Composable
private fun ListSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        GlassSurface(Modifier.fillMaxWidth().height(104.dp)) {}
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                GlassSurface(Modifier.weight(1f).height(150.dp)) {}
                GlassSurface(Modifier.weight(1f).height(150.dp)) {}
            }
        }
    }
}

// ------------------------------------------------------------------ Previews

private fun previewState(grid: Boolean = true) =
    SubscriptionListViewModel.build(SampleData.all, AppSettings(showArchived = true, listGrid = grid), CategoryFilter.ALL, SampleData.today)

@PreviewLightDark
@Composable
private fun SubscriptionListPreview() {
    GlassPreview(padding = 0.dp) {
        SubscriptionListScreen(previewState())
    }
}

@PreviewLightDark
@Composable
private fun SubscriptionListRowsPreview() {
    GlassPreview(padding = 0.dp) {
        SubscriptionListScreen(previewState(grid = false))
    }
}

@PreviewLightDark
@Composable
private fun SubscriptionListEmptyPreview() {
    GlassPreview(padding = 0.dp) {
        SubscriptionListScreen(SubscriptionListUiState(isLoading = false))
    }
}
