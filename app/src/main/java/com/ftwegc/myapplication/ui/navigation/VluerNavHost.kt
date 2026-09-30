package com.ftwegc.myapplication.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.ftwegc.myapplication.ui.GlassSettings
import com.ftwegc.myapplication.ui.debug.DebugGalleryScreen
import com.ftwegc.myapplication.ui.glass.GlassTier
import com.ftwegc.myapplication.ui.home.HomeDestination
import com.ftwegc.myapplication.ui.komari.KomariImportDestination
import com.ftwegc.myapplication.ui.komari.KomariSettingsDestination
import com.ftwegc.myapplication.ui.settings.AboutScreen
import com.ftwegc.myapplication.ui.settings.LicensesScreen
import com.ftwegc.myapplication.ui.settings.SettingsDestination
import com.ftwegc.myapplication.ui.stats.StatsDestination
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionDetailDestination
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionListDestination
import com.ftwegc.myapplication.ui.subscriptions.editor.EditorController
import com.ftwegc.myapplication.ui.subscriptions.editor.EditorSession
import com.ftwegc.myapplication.ui.theme.VluerTheme

/** True while [this] entry is the resumed top destination: guards against double taps. */
private fun NavBackStackEntry.isResumed(): Boolean = lifecycle.currentState == Lifecycle.State.RESUMED

/** Switches to [tab], keeping each tab's back stack and scroll state. */
fun NavHostController.navigateToTab(tab: TopLevelTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * The pages. Adding and editing a subscription is not a page: it is the glass sheet of the
 * [editor] at the app root, so it opens over whatever page is showing.
 */
@Composable
fun VluerNavHost(
    navController: NavHostController,
    editor: EditorController,
    modifier: Modifier = Modifier,
    onSetReduceTransparency: (Boolean) -> Unit = {},
    onSetGlassTier: (GlassTier?) -> Unit = {},
    glassSettings: GlassSettings = GlassSettings(),
) {
    val reduced = VluerTheme.reducedMotion

    fun NavBackStackEntry.openDetail(id: Long, origin: String) {
        if (isResumed()) navController.navigate(SubscriptionDetailRoute(id, origin))
    }

    fun NavBackStackEntry.back() {
        if (isResumed()) navController.popBackStack()
    }

    fun NavBackStackEntry.openKomariSettings() {
        if (isResumed()) navController.navigate(KomariSettingsRoute) { launchSingleTop = true }
    }

    fun NavBackStackEntry.openKomariImport() {
        if (isResumed()) navController.navigate(KomariImportRoute) { launchSingleTop = true }
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
        enterTransition = navEnter(reduced),
        exitTransition = navExit(reduced),
        popEnterTransition = navPopEnter(reduced),
        popExitTransition = navPopExit(reduced),
    ) {
        composable<HomeRoute> { entry ->
            ProvideNavAnimatedScope(this) {
                HomeDestination(
                    onOpenSubscription = { entry.openDetail(it, SharedKeys.ORIGIN_HOME) },
                    onOpenTraffic = { entry.openDetail(it, SharedKeys.ORIGIN_TRAFFIC) },
                    onAddSubscription = { editor.open(EditorSession.NEW_ID) },
                    onSeeAll = { if (entry.isResumed()) navController.navigateToTab(TopLevelTab.SUBSCRIPTIONS) },
                    onOpenStats = { if (entry.isResumed()) navController.navigateToTab(TopLevelTab.STATS) },
                    onOpenKomari = { entry.openKomariSettings() },
                    onOpenImport = { entry.openKomariImport() },
                )
            }
        }
        composable<SubscriptionsRoute> { entry ->
            ProvideNavAnimatedScope(this) {
                SubscriptionListDestination(
                    onOpenSubscription = { entry.openDetail(it, SharedKeys.ORIGIN_LIST) },
                )
            }
        }
        composable<StatsRoute> { entry ->
            ProvideNavAnimatedScope(this) {
                StatsDestination(
                    onAddSubscription = { editor.open(EditorSession.NEW_ID) },
                    onCompleteInfo = { id -> editor.open(id) },
                    onOpenSubscription = { entry.openDetail(it, SharedKeys.ORIGIN_STATS) },
                )
            }
        }
        composable<SettingsRoute> { entry ->
            ProvideNavAnimatedScope(this) {
                SettingsDestination(
                    onOpenKomari = { entry.openKomariSettings() },
                    onOpenDebugGallery = { if (entry.isResumed()) navController.navigate(DebugGalleryRoute) { launchSingleTop = true } },
                    onOpenAbout = { if (entry.isResumed()) navController.navigate(AboutRoute) { launchSingleTop = true } },
                )
            }
        }
        composable<AboutRoute> { entry ->
            ProvideNavAnimatedScope(this) {
                AboutScreen(
                    onBack = { entry.back() },
                    onOpenLicenses = { if (entry.isResumed()) navController.navigate(LicensesRoute) { launchSingleTop = true } },
                )
            }
        }
        composable<LicensesRoute> { entry ->
            ProvideNavAnimatedScope(this) {
                LicensesScreen(onBack = { entry.back() })
            }
        }
        composable<DebugGalleryRoute> { entry ->
            ProvideNavAnimatedScope(this) {
                DebugGalleryScreen(
                    glassSettings = glassSettings,
                    onSetReduceTransparency = onSetReduceTransparency,
                    onSetGlassTier = onSetGlassTier,
                    onBack = { entry.back() },
                )
            }
        }
        composable<KomariSettingsRoute> { entry ->
            ProvideNavAnimatedScope(this) {
                KomariSettingsDestination(onBack = { entry.back() }, onOpenImport = { entry.openKomariImport() })
            }
        }
        composable<KomariImportRoute> { entry ->
            ProvideNavAnimatedScope(this) {
                KomariImportDestination(
                    onBack = { entry.back() },
                    onOpenSettings = { entry.openKomariSettings() },
                    // Runs from the success message after this page closed: open the sheet directly.
                    onOpenEdit = { id -> editor.open(id) },
                )
            }
        }
        composable<SubscriptionDetailRoute> { entry ->
            val route = entry.toRoute<SubscriptionDetailRoute>()
            ProvideNavAnimatedScope(this) {
                SubscriptionDetailDestination(
                    origin = route.origin,
                    onBack = { entry.back() },
                )
            }
        }
    }
}
