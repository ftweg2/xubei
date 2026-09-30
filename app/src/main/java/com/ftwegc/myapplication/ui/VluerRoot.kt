package com.ftwegc.myapplication.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.VluerApp
import com.ftwegc.myapplication.dev.DevHooks
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.ui.res.stringResource
import com.ftwegc.myapplication.ui.glass.GlassConfirmDialog
import com.ftwegc.myapplication.ui.glass.rememberLatest
import com.ftwegc.myapplication.ui.background.AuroraBackground
import com.ftwegc.myapplication.ui.background.AuroraClock
import com.ftwegc.myapplication.ui.background.AuroraPalettes
import com.ftwegc.myapplication.ui.background.BackgroundToneController
import com.ftwegc.myapplication.ui.background.LocalAuroraClock
import com.ftwegc.myapplication.ui.background.LocalBackgroundTone
import com.ftwegc.myapplication.ui.background.PageTone
import com.ftwegc.myapplication.ui.common.AppMessenger
import com.ftwegc.myapplication.ui.common.LocalAppMessenger
import com.ftwegc.myapplication.ui.common.LocalNotificationPrompter
import com.ftwegc.myapplication.ui.common.NotificationPrompter
import com.ftwegc.myapplication.ui.common.NotificationStatus
import com.ftwegc.myapplication.ui.common.rememberNotificationPermission
import androidx.compose.ui.zIndex
import com.ftwegc.myapplication.ui.glass.GlassToast
import com.ftwegc.myapplication.ui.glass.GlassEnvironment
import com.ftwegc.myapplication.ui.glass.GlassShaderWarmUp
import com.ftwegc.myapplication.ui.glass.GlassSource
import com.ftwegc.myapplication.ui.glass.GlassTier
import com.ftwegc.myapplication.ui.glass.LocalGlass
import com.ftwegc.myapplication.ui.glass.LocalGlassSource
import com.ftwegc.myapplication.ui.glass.LocalPageBackdrop
import com.ftwegc.myapplication.ui.glass.LocalPageBarColor
import com.ftwegc.myapplication.ui.navigation.DockScrollState
import com.ftwegc.myapplication.ui.navigation.HomeRoute
import com.ftwegc.myapplication.ui.navigation.LocalBottomBarPadding
import com.ftwegc.myapplication.ui.navigation.LocalSharedTransitionScope
import com.ftwegc.myapplication.ui.navigation.SharedKeys
import com.ftwegc.myapplication.ui.navigation.SubscriptionDetailRoute
import com.ftwegc.myapplication.ui.navigation.KomariSettingsRoute
import com.ftwegc.myapplication.ui.navigation.DockFade
import com.ftwegc.myapplication.ui.glass.LocalOverlayHost
import com.ftwegc.myapplication.ui.glass.OverlayHost
import com.ftwegc.myapplication.ui.glass.OverlayHostContent
import com.ftwegc.myapplication.ui.glass.rememberGrowOrigin
import com.ftwegc.myapplication.ui.subscriptions.editor.EditorController
import com.ftwegc.myapplication.ui.subscriptions.editor.EditorSession
import com.ftwegc.myapplication.ui.subscriptions.editor.LocalEditor
import com.ftwegc.myapplication.ui.subscriptions.editor.SubscriptionEditorHost
import com.ftwegc.myapplication.ui.navigation.TopLevelTab
import com.ftwegc.myapplication.ui.navigation.VluerDock
import com.ftwegc.myapplication.ui.navigation.VluerNavHost
import com.ftwegc.myapplication.ui.navigation.bottomBarTotalHeight
import com.ftwegc.myapplication.ui.navigation.dockBottomOffset
import com.ftwegc.myapplication.ui.navigation.navigateToTab
import com.ftwegc.myapplication.ui.theme.Motion
import com.ftwegc.myapplication.ui.theme.Spacing
import com.ftwegc.myapplication.ui.theme.VluerTheme
import com.ftwegc.myapplication.ui.theme.motionSpec
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** [VluerRoot]'s `openRequest` value that opens the overview tab instead of a subscription. */
const val OPEN_HOME_REQUEST: Long = -1L

/**
 * App shell: the flowing background, the pages over it, the floating glass dock (hidden on pages
 * below the tabs), the app-wide messages, the notification explanation and the handling of a
 * tapped notification.
 *
 * Glass layers: the background is recorded once ([layerBackdrop]) for the panels inside the
 * pages; the background together with the pages is recorded for the floating dock, which bends
 * whatever scrolls under it. Both only exist on tiers that blur.
 *
 * @param openRequest subscription a tapped notification asked to open ([OPEN_HOME_REQUEST]: the
 *   overview); set back to `null` once shown.
 */
@Composable
fun VluerRoot(openRequest: MutableStateFlow<Long?> = remember { MutableStateFlow(null) }) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val currentTab = TopLevelTab.of(entry?.destination)
    // Keep the last tab highlighted (and its colors) while on a page below the tabs.
    val lastTab = remember { arrayOf(TopLevelTab.HOME) }
    if (currentTab != null) lastTab[0] = currentTab
    val overlays = remember { OverlayHost() }
    val editor = rememberSaveable(saver = EditorController.Saver) { EditorController() }
    val addOrigin = rememberGrowOrigin()
    // The dock steps aside while a sheet or a menu covers the page.
    val tabPage = currentTab != null || entry == null
    val showDock = tabPage && overlays.modalCount == 0

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val messenger = remember(snackbarHostState, scope) { AppMessenger(snackbarHostState, scope) }
    val barHeight = bottomBarTotalHeight()

    // Background and glass.
    val chrome: AppChromeViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val mood by chrome.mood.collectAsStateWithLifecycle()
    val glassSettings by chrome.glass.collectAsStateWithLifecycle()
    val dark = VluerTheme.colors.isDark
    val reduced = VluerTheme.reducedMotion
    val toneController = remember { BackgroundToneController() }
    val tone = toneController.requested ?: toneOf(currentTab ?: lastTab[0])
    val palette = remember(tone, mood, dark) { AuroraPalettes.of(tone, mood, dark) }
    val tier = GlassTier.resolve(Build.VERSION.SDK_INT, glassSettings.tierOverride, glassSettings.reduceTransparency)
    val glass = remember(palette, tier) { GlassEnvironment.of(palette, tier) }
    // Development builds can hold the drift at a fixed time (comparable screenshots).
    val clock = remember {
        AuroraClock().also { c ->
            DevHooks.auroraFreezeMs?.let {
                c.timeMs = it
                c.pause(DevHooks)
            }
        }
    }
    val thresholdPx = with(LocalDensity.current) { Motion.DockCollapseThreshold.toPx() }
    val dockScroll = remember(clock, thresholdPx) { DockScrollState(thresholdPx, clock) }
    LaunchedEffect(currentTab) { dockScroll.expand() }

    // A tapped notification opens that subscription's detail page on top of the current page.
    val pendingOpen by openRequest.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpen, entry) {
        val id = pendingOpen ?: return@LaunchedEffect
        if (entry == null) return@LaunchedEffect // graph not ready yet
        openRequest.value = null
        if (id == OPEN_HOME_REQUEST) {
            // A summary of several subscriptions: the overview lists them. Home is the start
            // destination, so popping to it also closes a detail page opened from there.
            if (!navController.popBackStack<HomeRoute>(inclusive = false)) navController.navigateToTab(TopLevelTab.HOME)
            return@LaunchedEffect
        }
        // Not singleTop: on top of another detail page that would keep the other subscription.
        val showing = entry?.takeIf { it.destination.hasRoute(SubscriptionDetailRoute::class) }?.toRoute<SubscriptionDetailRoute>()?.id
        if (showing != id) navController.navigate(SubscriptionDetailRoute(id, SharedKeys.ORIGIN_NOTIFICATION))
    }

    // Notification permission: explained once, at the first save of a subscription with a due date.
    val app = LocalContext.current.applicationContext as? VluerApp
    val requested by (app?.container?.settingsStore?.settings?.map { it.notificationPermissionRequested } ?: flowOf(false))
        .collectAsStateWithLifecycle(initialValue = false)
    val permission = rememberNotificationPermission(
        askedBefore = requested,
        onRequested = { app?.let { scope.launch { it.container.settingsStore.setNotificationPermissionRequested(true) } } },
    )
    var explain by rememberSaveable { mutableStateOf(false) }
    val prompter = remember(permission, app) {
        NotificationPrompter {
            scope.launch {
                val shown = app?.container?.settingsStore?.settings?.first()?.notificationPromptShown ?: true
                if (!shown && permission.status != NotificationStatus.ALLOWED) explain = true
            }
        }
    }
    fun markShown() {
        app?.let { scope.launch { it.container.settingsStore.setNotificationPromptShown(true) } }
    }

    val bgBackdrop = rememberLayerBackdrop()
    val pageBackdrop = rememberLayerBackdrop()
    CompositionLocalProvider(
        LocalAppMessenger provides messenger,
        LocalBottomBarPadding provides barHeight,
        LocalNotificationPrompter provides prompter,
        LocalGlass provides glass,
        LocalAuroraClock provides clock,
        LocalBackgroundTone provides toneController,
        LocalPageBarColor provides palette.base.copy(alpha = PAGE_BAR_ALPHA),
        LocalOverlayHost provides overlays,
        LocalEditor provides editor,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (tier.blurs) Modifier.layerBackdrop(pageBackdrop) else Modifier)
                    .nestedScroll(dockScroll.connection),
            ) {
                AuroraBackground(
                    palette = palette,
                    animate = !reduced && glassSettings.backgroundAnimation,
                    clock = clock,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (tier.blurs) Modifier.layerBackdrop(bgBackdrop) else Modifier),
                )
                CompositionLocalProvider(LocalGlassSource provides GlassSource(if (tier.blurs) bgBackdrop else null, 0)) {
                    SharedTransitionLayout(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                            VluerNavHost(
                                navController = navController,
                                editor = editor,
                                modifier = Modifier.fillMaxSize(),
                                onSetReduceTransparency = chrome::setReduceTransparency,
                                onSetGlassTier = chrome::setGlassTierOverride,
                                glassSettings = glassSettings,
                            )
                        }
                    }
                    // Content scrolling toward the dock dissolves into the background before it
                    // reaches the glass, and below the dock it is gone.
                    DockFade(visible = tabPage, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
            CompositionLocalProvider(LocalPageBackdrop provides if (tier.blurs) pageBackdrop else null) {
                AnimatedVisibility(
                    visible = showDock,
                    // Above the overlays while it is (coming) back: a sheet still shrinking into the
                    // add button must not take the tap meant for that button.
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(if (showDock) 1f else 0f),
                    // A short rise, so the buttons are where they will stay while they slide in.
                    enter = slideInVertically(motionSpec(tween(Motion.DurationDockHide, easing = Motion.EmphasizedDecelerate))) { (it * DOCK_ENTER_RISE).toInt() } +
                        fadeIn(motionSpec(tween(Motion.DurationDockHide, easing = Motion.EmphasizedDecelerate))),
                    exit = slideOutVertically(motionSpec(tween(Motion.DurationDockHide, easing = Motion.EmphasizedAccelerate))) { it } +
                        fadeOut(motionSpec(tween(Motion.DurationDockHide, easing = Motion.EmphasizedAccelerate))),
                ) {
                    VluerDock(
                        selected = currentTab ?: lastTab[0],
                        onSelect = { tab -> if (tab != currentTab) navController.navigateToTab(tab) },
                        onAdd = { editor.open(EditorSession.NEW_ID, addOrigin) },
                        collapsed = dockScroll.collapsed,
                        addOrigin = addOrigin,
                    )
                }
                if (tier.refracts) GlassShaderWarmUp()
                // Sheets and menus of the pages, above the pages and the dock.
                OverlayHostContent(overlays)
                SubscriptionEditorHost(
                    controller = editor,
                    currentEntryId = entry?.id,
                    onOpenKomari = { navController.navigate(KomariSettingsRoute) { launchSingleTop = true } },
                )
                NotificationExplainSheet(
                    visible = explain,
                    onAllow = {
                        explain = false
                        markShown()
                        permission.request()
                    },
                    onLater = {
                        explain = false
                        markShown()
                    },
                )
                // A database of an earlier, incompatible build was kept aside: said once.
                val legacyFlow = remember(app) { app?.container?.legacyDatabaseNotice ?: MutableStateFlow(null) }
                val legacyNotice by legacyFlow.collectAsStateWithLifecycle()
                val legacyShown = rememberLatest(legacyNotice)
                GlassConfirmDialog(
                    visible = legacyNotice != null,
                    onDismissRequest = { app?.container?.dismissLegacyDatabaseNotice() },
                    title = stringResource(R.string.legacy_db_title),
                    message = stringResource(R.string.legacy_db_message, legacyShown.orEmpty()),
                    confirmLabel = stringResource(R.string.legacy_db_ok),
                    onConfirm = { app?.container?.dismissLegacyDatabaseNotice() },
                    icon = Icons.Rounded.Archive,
                    dismissLabel = null,
                )
                val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val dockTop = dockBottomOffset() + com.ftwegc.myapplication.ui.navigation.LiquidTabsDefaults.Height
                val snackbarBottom by animateDpAsState(
                    targetValue = when {
                        showDock -> dockTop + Spacing.sm
                        imeBottom > 0.dp -> imeBottom + Spacing.sm
                        else -> navBottom + 84.dp // clear of bottom buttons on detail / edit pages
                    },
                    animationSpec = motionSpec(Motion.emphasized()),
                    label = "snackbarOffset",
                )
                // App messages float as glass over the page (they refract what is under them). While
                // a sheet or dialog is up they move to the top so they never cover its buttons.
                val overModal = overlays.modalCount > 0 && imeBottom == 0.dp
                val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(if (overModal) Alignment.TopCenter else Alignment.BottomCenter)
                        .zIndex(2f)
                        .padding(
                            top = if (overModal) statusTop + Spacing.sm else 0.dp,
                            bottom = if (overModal) 0.dp else snackbarBottom,
                        ),
                    snackbar = { data -> GlassToast(data) },
                )
            }
        }
    }
}

/** The dock slides in from this share of its height below (a short rise, see VluerRoot). */
private const val DOCK_ENTER_RISE = 0.35f

/** Opacity of the top and bottom bars of pages not yet redesigned (over the flowing background). */
private const val PAGE_BAR_ALPHA = 0.86f

private fun toneOf(tab: TopLevelTab): PageTone = when (tab) {
    TopLevelTab.HOME -> PageTone.Home
    TopLevelTab.SUBSCRIPTIONS -> PageTone.Subscriptions
    TopLevelTab.STATS -> PageTone.Stats
    TopLevelTab.SETTINGS -> PageTone.Settings
}
