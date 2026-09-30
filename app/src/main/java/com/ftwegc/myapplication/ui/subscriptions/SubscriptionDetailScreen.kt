package com.ftwegc.myapplication.ui.subscriptions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.contentDescription
import com.ftwegc.myapplication.ui.brand.VluerPullToRefreshBox
import com.ftwegc.myapplication.ui.glass.GlassButton
import com.ftwegc.myapplication.ui.glass.GlassConfirmDialog
import com.ftwegc.myapplication.ui.glass.TopBarScrim
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.abs
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.data.prefs.AppSettings
import com.ftwegc.myapplication.domain.calc.CumulativeSpend
import com.ftwegc.myapplication.domain.calc.RenewMode
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.CycleUnit
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.ui.AppViewModelProvider
import com.ftwegc.myapplication.ui.background.PageBackgroundTone
import com.ftwegc.myapplication.ui.background.PageTone
import com.ftwegc.myapplication.ui.brand.BrandCatalog
import com.ftwegc.myapplication.ui.brand.BrandMark
import com.ftwegc.myapplication.ui.brand.MarkSize
import com.ftwegc.myapplication.ui.charts.ProgressRing
import com.ftwegc.myapplication.ui.charts.StepAreaChart
import com.ftwegc.myapplication.ui.charts.StepPoint
import com.ftwegc.myapplication.ui.charts.chartColors
import com.ftwegc.myapplication.ui.common.LocalAppMessenger
import com.ftwegc.myapplication.ui.common.SubscriptionUi
import com.ftwegc.myapplication.ui.components.crossFadeSpec
import com.ftwegc.myapplication.ui.components.rollUpSpec
import com.ftwegc.myapplication.ui.format.DueLabel
import com.ftwegc.myapplication.ui.format.Formatters
import com.ftwegc.myapplication.ui.format.categoryLabel
import com.ftwegc.myapplication.ui.format.cycleText
import com.ftwegc.myapplication.ui.format.fullDateText
import com.ftwegc.myapplication.ui.glass.BreathingDot
import com.ftwegc.myapplication.ui.glass.GlassAccents
import com.ftwegc.myapplication.ui.glass.GlassChip
import com.ftwegc.myapplication.ui.glass.GlassDefaults
import com.ftwegc.myapplication.ui.glass.GlassIconButton
import com.ftwegc.myapplication.ui.glass.GlassLabel
import com.ftwegc.myapplication.ui.glass.GlassPanel
import com.ftwegc.myapplication.ui.glass.GlassPillButton
import com.ftwegc.myapplication.ui.glass.GlassSurface
import com.ftwegc.myapplication.ui.glass.LineIcons
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.glass.pageBarColor
import com.ftwegc.myapplication.ui.glass.rememberGrowOrigin
import com.ftwegc.myapplication.ui.komari.komariErrorMessage
import com.ftwegc.myapplication.ui.navigation.SharedKeys
import com.ftwegc.myapplication.ui.navigation.sharedBoundsOrSelf
import com.ftwegc.myapplication.ui.navigation.sharedElementOrSelf
import com.ftwegc.myapplication.ui.preview.GlassPreview
import com.ftwegc.myapplication.ui.preview.SampleData
import com.ftwegc.myapplication.ui.subscriptions.editor.LocalEditor
import com.ftwegc.myapplication.ui.theme.Motion
import com.ftwegc.myapplication.ui.theme.motionSpec
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Screen margin and panel gap (mockup `.scroll` 15 px, panels 11 px apart). */
private val Margin = 16.dp
private val PanelGap = 11.dp

@Composable
fun SubscriptionDetailDestination(
    origin: String,
    onBack: () -> Unit,
    viewModel: SubscriptionDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalAppMessenger.current
    val resources = LocalResources.current
    val editor = LocalEditor.current
    val sameYear = stringResource(R.string.date_pattern_same_year)
    val fullYear = stringResource(R.string.date_pattern_full)
    LaunchedEffect(viewModel, messenger) {
        viewModel.events.collect { event ->
            when (event) {
                is DetailEvent.Renewed -> {
                    val today = viewModel.uiState.value.today
                    val date = Formatters.formatDate(event.outcome.newDue, today, sameYear, fullYear)
                    messenger?.showSuccess(
                        message = resources.getString(R.string.detail_renewed, date),
                        actionLabel = resources.getString(R.string.action_undo),
                        onAction = { viewModel.undoRenew(event.outcome) },
                    )
                }
                DetailEvent.RenewUndone -> messenger?.showInfo(resources.getString(R.string.detail_renew_undone))
                is DetailEvent.Deleted -> {
                    messenger?.showSuccess(resources.getString(R.string.detail_deleted, event.name))
                    onBack()
                }
                is DetailEvent.ArchiveChanged -> messenger?.showSuccess(
                    resources.getString(if (event.archived) R.string.detail_archived_done else R.string.detail_unarchived_done),
                )
                is DetailEvent.Failed -> messenger?.showInfo(resources.getString(R.string.detail_error, event.message))
                is DetailEvent.TrafficFailed -> messenger?.showInfo(
                    resources.getString(R.string.traffic_refresh_failed, komariErrorMessage(resources, event.error)),
                )
                DetailEvent.Unlinked -> messenger?.showSuccess(resources.getString(R.string.detail_unlinked_done))
            }
        }
    }
    // Collected only while the page is in front: the live polling stops as soon as it is left.
    val live by viewModel.live.collectAsStateWithLifecycle()
    SubscriptionDetailScreen(
        state = state,
        live = live,
        origin = origin,
        actions = DetailActions(
            onBack = onBack,
            onEdit = { id, grow -> editor?.open(id, grow) },
            onRenew = viewModel::markRenewed,
            onArchive = viewModel::setArchived,
            onDelete = viewModel::delete,
            onRefreshTraffic = viewModel::refreshTraffic,
            onUnlink = viewModel::unlinkKomari,
        ),
    )
}

/** Everything the detail page can do. */
class DetailActions(
    val onBack: () -> Unit = {},
    val onEdit: (Long, com.ftwegc.myapplication.ui.glass.GrowOrigin?) -> Unit = { _, _ -> },
    val onRenew: (RenewMode) -> Unit = {},
    val onArchive: (Boolean) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onRefreshTraffic: () -> Unit = {},
    val onUnlink: () -> Unit = {},
)

private enum class DetailContent { Loading, NotFound, Content }

/**
 * The detail page (mockup, third screen): the background takes the subscription's color, the big
 * mark sits on a glass pedestal and shrinks into the top bar while scrolling, and the facts come
 * as glass panels of charts: traffic (liquid ring), forecast, live node, node info, daily usage,
 * history, spend so far, and the due ring with "标记已续费".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionDetailScreen(
    state: SubscriptionDetailUiState,
    origin: String,
    modifier: Modifier = Modifier,
    live: DetailLive = DetailLive(),
    actions: DetailActions = DetailActions(),
) {
    val sub = state.subscription
    val card = state.card
    val scroll = rememberScrollState()
    var showRenew by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showUnlink by rememberSaveable { mutableStateOf(false) }
    val editOrigin = rememberGrowOrigin()
    val renewOrigin = rememberGrowOrigin()
    var headerHeight by remember { mutableIntStateOf(1) }

    if (card != null) PageBackgroundTone(PageTone.Accent(accentOf(card)))

    val content = when {
        sub != null && card != null -> DetailContent.Content
        state.notFound -> DetailContent.NotFound
        else -> DetailContent.Loading
    }
    // The page content is recorded for the top bar, which blurs what scrolls under it.
    val contentLayer = if (currentGlass().tier.blurs) rememberLayerBackdrop() else null
    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = content,
            transitionSpec = crossFadeSpec(),
            label = "detailContent",
            modifier = if (contentLayer != null) Modifier.layerBackdrop(contentLayer) else Modifier,
        ) { kind ->
            when (kind) {
                DetailContent.Loading -> Box(Modifier.fillMaxSize())
                DetailContent.NotFound -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LogoEmptyState(
                        title = stringResource(R.string.detail_not_found),
                        message = null,
                        action = stringResource(R.string.action_back),
                        onAction = actions.onBack,
                    )
                }
                DetailContent.Content -> if (sub != null && card != null) {
                    val body: @Composable () -> Unit = {
                        DetailBody(
                            state = state,
                            live = live,
                            sub = sub,
                            card = card,
                            origin = origin,
                            scroll = scroll,
                            onHeaderHeight = { headerHeight = it.coerceAtLeast(1) },
                            actions = actions,
                            renewOrigin = renewOrigin,
                            onRenew = { showRenew = true },
                            onDelete = { showDelete = true },
                            onUnlink = { showUnlink = true },
                        )
                    }
                    if (state.canRefreshTraffic) {
                        VluerPullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = actions.onRefreshTraffic, modifier = Modifier.fillMaxSize()) { body() }
                    } else {
                        body()
                    }
                }
            }
        }
        DetailTopBar(
            state = state,
            live = live,
            scroll = scroll,
            headerHeight = { headerHeight },
            onBack = actions.onBack,
            onEdit = { sub?.let { actions.onEdit(it.id, editOrigin) } },
            editOrigin = editOrigin,
            contentLayer = contentLayer,
        )
    }

    if (sub != null && card != null) {
        RenewSheet(
            choice = if (showRenew) card.renewChoice() else null,
            today = state.today,
            origin = renewOrigin,
            onDismiss = { showRenew = false },
            onConfirm = { _, mode ->
                showRenew = false
                actions.onRenew(mode)
            },
        )
        DeleteSubscriptionDialog(
            visible = showDelete,
            name = sub.name,
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                actions.onDelete()
            },
        )
        UnlinkDialog(
            visible = showUnlink,
            nodeName = state.vps?.komariName ?: state.vps?.komariUuid.orEmpty(),
            onDismiss = { showUnlink = false },
            onConfirm = {
                showUnlink = false
                actions.onUnlink()
            },
        )
    }
}

/** The page color: the middle of the mark's gradient; black logos (graphite marks) use indigo. */
private fun accentOf(card: SubscriptionUi): Long {
    val spec = BrandCatalog.resolve(card.mark.name, card.mark.iconKey, card.mark.iconColor)
    if (spec.start to spec.end == BrandCatalog.NeutralGradient) return 0xFF4F5BD5
    fun ch(c: Long, shift: Int) = (c shr shift) and 0xFF
    val r = (ch(spec.start, 16) + ch(spec.end, 16)) / 2
    val g = (ch(spec.start, 8) + ch(spec.end, 8)) / 2
    val b = (ch(spec.start, 0) + ch(spec.end, 0)) / 2
    return (0xFFL shl 24) or (r shl 16) or (g shl 8) or b
}

@Composable
private fun DetailBody(
    state: SubscriptionDetailUiState,
    live: DetailLive,
    sub: Subscription,
    card: SubscriptionUi,
    origin: String,
    scroll: ScrollState,
    onHeaderHeight: (Int) -> Unit,
    actions: DetailActions,
    renewOrigin: com.ftwegc.myapplication.ui.glass.GrowOrigin,
    onRenew: () -> Unit,
    onDelete: () -> Unit,
    onUnlink: () -> Unit,
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val vps = sub.category == Category.VPS
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .statusBarsPadding()
            .padding(horizontal = Margin)
            .padding(top = TopBarHeight, bottom = navBottom + 40.dp),
        verticalArrangement = Arrangement.spacedBy(PanelGap),
    ) {
        DetailHeader(
            state = state,
            sub = sub,
            card = card,
            origin = origin,
            scroll = scroll,
            modifier = Modifier.onSizeChanged { onHeaderHeight(it.height) },
        )
        if (vps) {
            TrafficPanel(state, live, onUnlink = onUnlink)
            ForecastPanel(state)
            if (state.vps?.isLinked == true && state.komariConfigured) {
                LivePanel(state, live)
                NodeInfoPanel(state, live)
            }
            DailyUsagePanel(state)
            HistoryPanel(state)
        }
        if (card.needsBillingInfo) {
            BillingMissingPanel(onComplete = { actions.onEdit(sub.id, null) })
        } else {
            DuePanel(state, card, onRenew = onRenew, renewOrigin = renewOrigin)
        }
        SpendPanel(state, sub)
        InfoPanel(sub, card)
        ManagePanel(
            archived = sub.archived,
            linked = state.vps?.isLinked == true,
            deleting = state.isDeleting,
            onArchive = { actions.onArchive(!sub.archived) },
            onUnlink = onUnlink,
            onDelete = onDelete,
        )
    }
}

/** Height of the top bar (back, status, edit) below the status bar. */
private val TopBarHeight = 56.dp

/** The top fade runs on this far below the bar. */
private val TopFadeReach = 22.dp

/**
 * Mockup: the back and edit buttons as glass circles, the status capsule between them. Once the
 * header has scrolled away, a small mark and the name dock in the middle; a soft fade keeps the
 * content scrolling underneath from colliding with the buttons.
 */
@Composable
private fun DetailTopBar(
    state: SubscriptionDetailUiState,
    live: DetailLive,
    scroll: ScrollState,
    headerHeight: () -> Int,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    editOrigin: com.ftwegc.myapplication.ui.glass.GrowOrigin,
    contentLayer: com.kyant.backdrop.backdrops.LayerBackdrop?,
) {
    val env = currentGlass()
    val docked by remember { derivedStateOf { (scroll.value / (headerHeight() * 0.72f)).coerceIn(0f, 1f) } }
    val card = state.card
    Box(Modifier.fillMaxWidth()) {
        // What scrolls under the bar is blurred hard and fades out from the top, so the docked
        // title never sits on readable text; it reaches a little below the bar.
        TopBarScrim(
            contentLayer = contentLayer,
            visibility = { (scroll.value / 120f).coerceIn(0f, 1f) },
            barHeight = TopBarHeight,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(bottom = TopFadeReach)
                .height(TopBarHeight)
                .padding(horizontal = Margin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Mockup: the back and edit buttons and the status capsule take the page color.
            val accent = card?.let { Color(accentOf(it)) } ?: Color.Unspecified
            GlassIconButton(LineIcons.Back, stringResource(R.string.action_back), onBack, glassTint = accent)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (card != null) {
                    Row(
                        Modifier.graphicsLayer {
                            alpha = ((docked - 0.55f) / 0.45f).coerceIn(0f, 1f)
                            translationY = (1f - docked) * 12.dp.toPx()
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BrandMark(card.mark, size = MarkSize.Small)
                        Text(card.name, color = env.ink.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = (1f - docked * 2f).coerceIn(0f, 1f) },
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        StatusCapsule(state, live)
                    }
                }
            }
            if (state.subscription != null) {
                GlassIconButton(LineIcons.Edit, stringResource(R.string.detail_edit), onEdit, modifier = editOrigin.modifier, glassTint = accent)
            }
        }
    }
}


/** Mockup "在线 · 开机 58 天": the node's state for a linked VPS, otherwise the due state. */
@Composable
private fun StatusCapsule(state: SubscriptionDetailUiState, live: DetailLive) {
    val env = currentGlass()
    val card = state.card ?: return
    val traffic = state.traffic
    val series = live.series
    val (dot, text) = when {
        card.archived -> null to stringResource(R.string.archived_tag)
        // The panel did not answer: say so rather than calling the node offline.
        traffic?.linked == true && state.komariConfigured && live.failed -> null to stringResource(R.string.detail_status_unreachable)
        traffic?.linked == true && state.komariConfigured -> {
            val online = series?.online ?: (traffic.issue == null && traffic.fetchedAt != null)
            val uptime = series?.latest?.uptimeSec ?: traffic.uptimeSec
            val days = uptime?.let { (it / 86_400).toInt() }
            val base = stringResource(if (online) R.string.traffic_status_online else R.string.traffic_status_offline)
            online to if (online && days != null) stringResource(R.string.detail_status_uptime, base, days) else base
        }
        card.needsBillingInfo -> null to stringResource(R.string.billing_incomplete_tag)
        else -> null to dueShortText(card)
    }
    GlassSurface(shape = GlassDefaults.CapsuleShape, tint = Color(accentOf(card))) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (dot != null) BreathingDot(online = dot, color = Color(0xFF2DD4BF))
            Text(text, color = env.ink.primary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
        }
    }
}

/**
 * Mockup: the large mark on a glass pedestal (with the flag), the name, "VPS · 季付 · 62 天后到期"
 * and three chips (price, per month, spent so far). It shrinks as it scrolls away; the list tile
 * turns into it when the page opens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHeader(
    state: SubscriptionDetailUiState,
    sub: Subscription,
    card: SubscriptionUi,
    origin: String,
    scroll: ScrollState,
    modifier: Modifier = Modifier,
) {
    val env = currentGlass()
    val chart = chartColors()
    val collapse = { (scroll.value / 420f).coerceIn(0f, 1f) }
    Column(
        modifier
            .fillMaxWidth()
            .sharedBoundsOrSelf(SharedKeys.card(origin, sub.id), scaleContent = false)
            .graphicsLayer {
                val c = collapse()
                val s = 1f - (1f - Motion.HeaderDockScale) * c
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 0f)
                alpha = (1f - c * 1.4f).coerceIn(0f, 1f)
            }
            .padding(top = 4.dp, bottom = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Mockup: .glass padding 13px, 36px corners, the mark 78 px.
        GlassSurface(shape = RoundedCornerShape(36.dp)) {
            BrandMark(
                card.mark,
                size = MarkSize.Large,
                modifier = Modifier
                    .padding(13.dp)
                    .sharedElementOrSelf(SharedKeys.icon(origin, sub.id)),
            )
        }
        Text(
            sub.name,
            color = env.ink.primary,
            fontSize = 23.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 12.dp)
                .semantics { heading() }
                .sharedBoundsOrSelf(SharedKeys.name(origin, sub.id)),
        )
        val parts = buildList {
            add(categoryLabel(sub.category))
            if (!card.needsBillingInfo && sub.cycleKnown) add(cycleText(sub.cycle))
            add(if (card.needsBillingInfo) stringResource(R.string.billing_incomplete_meta) else headerDueText(card))
        }
        Text(
            parts.joinToString(" · "),
            color = env.ink.secondary,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp),
        )
        FlowRow(
            Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            when {
                card.needsBillingInfo -> GlassChip(stringResource(R.string.billing_incomplete_tag), color = GlassAccents.amber(env.dark))
                sub.isFree -> GlassChip(stringResource(R.string.free_tag), color = GlassAccents.mint(env.dark))
                else -> {
                    val price = Formatters.formatAmount(sub.priceMinor, sub.currency).removeSuffix(".00")
                    val perCycle = if (sub.cycleKnown && sub.cycle.unit != CycleUnit.ONCE) stringResource(R.string.detail_chip_per, price, cycleUnitShort(sub)) else price
                    GlassChip(perCycle)
                    val monthly = card.monthlyMinor
                    if (monthly != null && !(sub.cycle.unit == CycleUnit.MONTH && sub.cycle.count == 1)) {
                        GlassChip(stringResource(R.string.detail_chip_monthly, Formatters.formatAmount(monthly, sub.currency).removeSuffix(".00")))
                    }
                }
            }
            if (state.spend.isNotEmpty()) {
                GlassChip(stringResource(R.string.detail_chip_spent, Formatters.formatAmount(CumulativeSpend.total(state.spend), sub.currency).removeSuffix(".00")))
            }
            if (sub.isPermanent) GlassChip(stringResource(R.string.due_permanent), color = chart.mint)
            if (sub.archived) GlassChip(stringResource(R.string.archived_tag))
        }
        AnimatedVisibility(
            visible = sub.archived,
            enter = expandVertically(motionSpec(Motion.emphasized())) + fadeIn(motionSpec(Motion.emphasized())),
            exit = shrinkVertically(motionSpec(Motion.emphasizedAccelerate())) + fadeOut(motionSpec(Motion.emphasizedAccelerate())),
        ) {
            Text(
                stringResource(R.string.detail_status_archived),
                color = env.ink.secondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** "62 天后到期" style text for the header line. */
@Composable
private fun headerDueText(card: SubscriptionUi): String = when (val label = card.due.label) {
    is DueLabel.InDays -> stringResource(R.string.list_due_in, label.days.toInt())
    DueLabel.Today -> stringResource(R.string.due_today)
    is DueLabel.Overdue -> stringResource(R.string.due_overdue_days, label.days.toInt())
    DueLabel.Permanent -> stringResource(R.string.due_permanent)
    DueLabel.NoDate -> stringResource(R.string.due_no_date)
}

/** "季" in "¥129 / 季". */
@Composable
private fun cycleUnitShort(sub: Subscription): String = when {
    sub.cycle.unit == CycleUnit.MONTH && sub.cycle.count == 1 -> stringResource(R.string.cycle_short_month)
    sub.cycle.unit == CycleUnit.MONTH && sub.cycle.count == 3 -> stringResource(R.string.cycle_short_quarter)
    sub.cycle.unit == CycleUnit.MONTH && sub.cycle.count == 6 -> stringResource(R.string.cycle_short_half)
    sub.cycle.unit == CycleUnit.MONTH && sub.cycle.count == 12 -> stringResource(R.string.cycle_short_year)
    else -> cycleText(sub.cycle)
}

/**
 * "到期": the due ring (the passed share of the cycle, the days left in the middle), the next due
 * date and "标记已续费" (both renewal ways, with undo).
 */
@Composable
private fun DuePanel(
    state: SubscriptionDetailUiState,
    card: SubscriptionUi,
    onRenew: () -> Unit,
    renewOrigin: com.ftwegc.myapplication.ui.glass.GrowOrigin,
) {
    val sub = state.subscription ?: return
    val env = currentGlass()
    val chart = chartColors()
    val days = card.daysLeft()
    val color = dueAccent(days, chart)
    GlassPanel(Modifier.fillMaxWidth()) {
        GlassLabel(stringResource(R.string.detail_due_section), trailing = state.cycleProgress?.let { stringResource(R.string.detail_cycle_progress, (it * 100).toInt()) })
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val (number, unit) = when (val label = card.due.label) {
                is DueLabel.InDays -> label.days.toString() to stringResource(R.string.home_due_after_unit)
                DueLabel.Today -> stringResource(R.string.home_due_today_short) to stringResource(R.string.home_due_today_unit)
                is DueLabel.Overdue -> label.days.toString() to stringResource(R.string.home_due_overdue_unit)
                DueLabel.Permanent -> "∞" to stringResource(R.string.due_permanent)
                DueLabel.NoDate -> "–" to stringResource(R.string.due_no_date)
            }
            ProgressRing(
                fraction = when {
                    sub.isPermanent -> 1f
                    days == null -> null
                    else -> card.elapsed
                },
                contentDescription = stringResource(R.string.detail_due_ring_desc, "$number $unit"),
                size = 104.dp,
                strokeWidth = 9.dp,
                colors = listOf(color),
                memoryKey = "detail-due-${sub.id}",
                emptyText = stringResource(R.string.due_no_date),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(number, color = if (days != null && days < 0) GlassAccents.coral(env.dark) else env.ink.primary, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
                    Text(unit, color = env.ink.secondary, fontSize = 11.sp, lineHeight = 14.sp)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KvRow(stringResource(R.string.detail_next_due), sub.nextDue?.let { fullDateText(it) } ?: stringResource(R.string.due_no_date), animateKey = sub.nextDue)
                KvRow(stringResource(R.string.detail_auto_renew), stringResource(if (sub.autoRenew) R.string.detail_yes else R.string.detail_no))
                if (sub.nextDue != null) {
                    val left = days ?: 0
                    KvRow(
                        stringResource(if (left < 0) R.string.detail_days_overdue_label else R.string.detail_days_left_label),
                        stringResource(R.string.detail_days_value, kotlin.math.abs(left).toInt()),
                    )
                }
            }
        }
        if (state.canRenew) {
            RenewButton(
                enabled = !state.isDeleting,
                onClick = onRenew,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .then(renewOrigin.modifier),
            )
        } else {
            Text(
                stringResource(if (sub.isPermanent) R.string.detail_not_renewable_permanent else R.string.detail_not_renewable_once),
                color = env.ink.secondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

/** Mockup `.btn`: a 50 dp teal-to-blue glass capsule. */
@Composable
fun RenewButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassActionButton(
        text = stringResource(R.string.detail_mark_renewed),
        icon = Icons.Rounded.EventRepeat,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    )
}

/** Mockup `.btn`: 50 dp high, 25 dp corners, washed teal to blue, 14.5 sp semibold. */
@Composable
fun GlassActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    // Mockup "保存": a teal-to-blue filled glass, like the add button.
    GlassButton(
        text = text,
        onClick = onClick,
        icon = icon,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Replaces the due panel while a Komari import still lacks price and due date. */
@Composable
private fun BillingMissingPanel(onComplete: () -> Unit) {
    val env = currentGlass()
    val amber = GlassAccents.amber(env.dark)
    GlassPanel(Modifier.fillMaxWidth(), tint = Color(0xFFFBBF24)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.EditCalendar, contentDescription = null, tint = amber, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.detail_billing_missing_title), color = env.ink.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.detail_billing_missing_message), color = env.ink.secondary, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        GlassActionButton(stringResource(R.string.detail_billing_missing_action), onComplete, Modifier.padding(top = 14.dp))
    }
}

/** "累计花费": the staircase of every payment since the subscription was added. */
@Composable
private fun SpendPanel(state: SubscriptionDetailUiState, sub: Subscription) {
    val env = currentGlass()
    val chart = chartColors()
    val steps = state.spend
    val total = CumulativeSpend.total(steps)
    val start = steps.firstOrNull()?.date ?: state.addedOn
    val today = state.today
    val label = DateTimeFormatter.ofPattern("yy/M/d", Locale.CHINA)
    GlassPanel(Modifier.fillMaxWidth()) {
        GlassLabel(
            stringResource(R.string.detail_spend_label),
            trailing = if (steps.isNotEmpty()) stringResource(R.string.detail_spend_count, steps.size) else null,
        )
        Text(
            if (sub.isFree) stringResource(R.string.free_tag) else Formatters.formatAmount(total, sub.currency),
            color = env.ink.primary,
            fontSize = 26.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(fontFeatureSettings = "tnum"),
            modifier = Modifier.padding(top = 4.dp),
        )
        val span = if (start != null) ChronoUnit.DAYS.between(start, today).coerceAtLeast(1) else 1
        val points = steps.map { s ->
            StepPoint((ChronoUnit.DAYS.between(start, s.date).toFloat() / span).coerceIn(0f, 1f), s.totalMinor.toFloat())
        }
        if (steps.size in 1..FEW_PAYMENTS) {
            // One or two payments make no staircase: a line of time with a dot per payment. A
            // renewal can book a payment on the (future) due date: the line then runs past today.
            val first = steps.first().date
            val dotSpan = ChronoUnit.DAYS.between(first, maxOf(today, steps.last().date)).coerceAtLeast(1)
            PaymentDots(
                payments = steps.map { PaymentDot(it.date, it.amountMinor) },
                fractions = steps.map { (ChronoUnit.DAYS.between(first, it.date).toFloat() / dotSpan).coerceIn(0f, 1f) },
                todayFraction = (ChronoUnit.DAYS.between(first, today).toFloat() / dotSpan).coerceIn(0f, 1f),
                currency = sub.currency,
                color = chart.violet,
                description = stringResource(R.string.detail_spend_desc, Formatters.formatAmount(total, sub.currency), steps.size),
                modifier = Modifier.padding(top = 10.dp),
            )
            return@GlassPanel
        }
        StepAreaChart(
            steps = points,
            contentDescription = stringResource(R.string.detail_spend_desc, Formatters.formatAmount(total, sub.currency), steps.size),
            color = chart.violet,
            startLabel = start?.format(label),
            endLabel = stringResource(R.string.gallery_today),
            memoryKey = "detail-spend-${sub.id}",
            emptyText = stringResource(if (sub.isFree) R.string.detail_spend_free else R.string.detail_spend_none),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Up to this many payments show as dots instead of a staircase. */
private const val FEW_PAYMENTS = 2

private data class PaymentDot(val date: java.time.LocalDate, val amountMinor: Long)

/**
 * A few payments on a line of time: the line runs from the first payment to today (or to a later
 * payment), each payment is a glowing dot with its amount above and its date below; an open ring
 * marks today ([todayFraction]), labeled "今天" when it closes the line clear of the payments.
 */
@Composable
private fun PaymentDots(
    payments: List<PaymentDot>,
    fractions: List<Float>,
    todayFraction: Float,
    currency: String,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
) {
    val env = currentGlass()
    val chart = chartColors()
    val format = remember { DateTimeFormatter.ofPattern("yy/M/d", Locale.CHINA) }
    val reveal by com.ftwegc.myapplication.ui.charts.rememberReveal(
        payments, "detail-dots-${payments.hashCode()}", Motion.DurationChartDraw, Motion.DelayChartDraw, Motion.ChartDrawEasing,
    )
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(78.dp)
            .semantics { this.contentDescription = description },
    ) {
        val width = maxWidth
        val inset = 26.dp
        val usable = width - inset * 2
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val y = 40.dp.toPx()
            val left = inset.toPx()
            val right = size.width - inset.toPx()
            val end = left + (right - left) * reveal
            drawLine(
                Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), chart.track), startX = left, endX = right),
                androidx.compose.ui.geometry.Offset(left, y),
                androidx.compose.ui.geometry.Offset(end, y),
                strokeWidth = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            // Today: a small open ring (the right end unless a payment lies ahead).
            val todayX = left + (right - left) * todayFraction
            drawCircle(chart.inkSecondary.copy(alpha = reveal), radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(todayX, y), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            fractions.forEach { f ->
                val x = left + (right - left) * f
                if (x > end + 1f) return@forEach
                drawCircle(color.copy(alpha = 0.28f), radius = 11.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                drawCircle(color, radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                drawCircle(Color.White, radius = 2.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
            }
        }
        payments.forEachIndexed { i, p ->
            val x = inset + usable * fractions[i]
            Column(
                Modifier
                    .width(96.dp)
                    .offset(x = (x - 48.dp).coerceIn(0.dp, width - 96.dp))
                    .graphicsLayer { alpha = ((reveal - fractions[i] * 0.8f) / 0.2f).coerceIn(0f, 1f) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    Formatters.formatAmount(p.amountMinor, currency),
                    color = env.ink.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
                Spacer(Modifier.height(34.dp))
                Text(p.date.format(format), color = env.ink.secondary, fontSize = 10.5.sp, maxLines = 1)
            }
        }
        if (fractions.all { abs(it - todayFraction) > 0.2f }) {
            val x = inset + usable * todayFraction
            Text(
                stringResource(R.string.gallery_today),
                color = env.ink.secondary,
                fontSize = 10.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .width(48.dp)
                    .offset(x = (x - 24.dp).coerceIn(0.dp, width - 48.dp), y = 49.dp),
            )
        }
    }
}

/** "订阅信息": price, per month, cycle, currency, auto-renewal and the note. */
@Composable
private fun InfoPanel(sub: Subscription, card: SubscriptionUi) {
    val env = currentGlass()
    GlassPanel(Modifier.fillMaxWidth()) {
        GlassLabel(stringResource(R.string.detail_info_section))
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            KvRow(
                stringResource(R.string.detail_price),
                when {
                    card.needsBillingInfo -> stringResource(R.string.billing_incomplete_tag)
                    sub.isFree -> stringResource(R.string.free_tag)
                    else -> Formatters.formatAmount(sub.priceMinor, sub.currency)
                },
            )
            card.monthlyMinor?.let { KvRow(stringResource(R.string.detail_monthly_equivalent), Formatters.formatAmount(it, sub.currency)) }
            KvRow(
                stringResource(R.string.detail_cycle),
                when {
                    !sub.cycleKnown -> stringResource(R.string.detail_cycle_unknown)
                    card.needsBillingInfo -> stringResource(R.string.billing_incomplete_tag)
                    else -> cycleText(sub.cycle)
                },
            )
            KvRow(stringResource(R.string.detail_currency), sub.currency)
            KvRow(stringResource(R.string.detail_auto_renew), stringResource(if (sub.autoRenew) R.string.detail_yes else R.string.detail_no))
        }
        if (sub.note.isNotBlank()) {
            Text(stringResource(R.string.detail_note), color = env.ink.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            Text(sub.note, color = env.ink.primary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** Archive, unlink and delete, as glass capsules at the end of the page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManagePanel(
    archived: Boolean,
    linked: Boolean,
    deleting: Boolean,
    onArchive: () -> Unit,
    onUnlink: () -> Unit,
    onDelete: () -> Unit,
) {
    val env = currentGlass()
    val coral = GlassAccents.coral(env.dark)
    GlassPanel(Modifier.fillMaxWidth()) {
        GlassLabel(stringResource(R.string.detail_manage_label))
        FlowRow(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassPillButton(stringResource(if (archived) R.string.detail_unarchive else R.string.detail_archive), onArchive) {
                Icon(if (archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, contentDescription = null, tint = env.ink.primary, modifier = Modifier.size(16.dp))
            }
            if (linked) {
                GlassPillButton(stringResource(R.string.detail_unlink), onUnlink) {
                    Icon(Icons.Rounded.LinkOff, contentDescription = null, tint = env.ink.primary, modifier = Modifier.size(16.dp))
                }
            }
            GlassPillButton(stringResource(R.string.detail_delete), { if (!deleting) onDelete() }) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = coral, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Mockup `.kv`: a dim 12 sp label with the value on the right. */
@Composable
fun KvRow(label: String, value: String, valueColor: Color = Color.Unspecified, animateKey: Any? = null) {
    val env = currentGlass()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 18.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = env.ink.secondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
        val style = TextStyle(fontFeatureSettings = "tnum")
        val color = if (valueColor == Color.Unspecified) env.ink.primary else valueColor
        if (animateKey != null) {
            AnimatedContent(targetState = value, transitionSpec = rollUpSpec(), label = "kv") { v ->
                Text(v, color = color, fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, style = style, maxLines = 1)
            }
        } else {
            Text(value, color = color, fontSize = 12.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "解除关联": the subscription and its history stay, only the node link goes. */
@Composable
private fun UnlinkDialog(visible: Boolean, nodeName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    GlassConfirmDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.LinkOff,
        title = stringResource(R.string.detail_unlink_title),
        message = stringResource(R.string.detail_unlink_message, nodeName),
        confirmLabel = stringResource(R.string.detail_unlink_confirm),
        onConfirm = onConfirm,
    )
}

// ------------------------------------------------------------------ Previews

private fun previewState(index: Int): SubscriptionDetailUiState =
    SubscriptionDetailViewModel.build(SampleData.all[index], AppSettings(), SampleData.today)

@PreviewLightDark
@Composable
private fun DetailAiPreview() {
    GlassPreview(padding = 0.dp) {
        SubscriptionDetailScreen(previewState(0), SharedKeys.ORIGIN_LIST)
    }
}

@PreviewLightDark
@Composable
private fun DetailVpsPreview() {
    GlassPreview(padding = 0.dp) {
        SubscriptionDetailScreen(previewState(2), SharedKeys.ORIGIN_LIST)
    }
}
