package com.ftwegc.myapplication.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.ftwegc.myapplication.R
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/** Type-safe navigation routes. */
@Serializable
data object HomeRoute

@Serializable
data object SubscriptionsRoute

@Serializable
data object StatsRoute

@Serializable
data object SettingsRoute

/** Detail page; [origin] is the [SharedKeys] origin of the card that opened it. */
@Serializable
data class SubscriptionDetailRoute(val id: Long, val origin: String = SharedKeys.ORIGIN_LIST)

/** Komari panel settings (address, API key, connection test). */
@Serializable
data object KomariSettingsRoute

/** Node import from the Komari panel. */
@Serializable
data object KomariImportRoute

/** About 续杯: the mark, the version, the way to the licenses. */
@Serializable
data object AboutRoute

/** Open source licenses (THIRD_PARTY_NOTICES.md). */
@Serializable
data object LicensesRoute

/** Debug builds only: glass switches, the chart gallery and the service icon gallery. */
@Serializable
data object DebugGalleryRoute

/** The four tabs of the floating glass navigation. */
enum class TopLevelTab(
    val route: Any,
    val routeClass: KClass<*>,
    @param:StringRes val label: Int,
    val icon: ImageVector,
) {
    HOME(HomeRoute, HomeRoute::class, R.string.tab_home, NavIcons.Overview),
    SUBSCRIPTIONS(SubscriptionsRoute, SubscriptionsRoute::class, R.string.tab_subscriptions, NavIcons.Subscriptions),
    STATS(StatsRoute, StatsRoute::class, R.string.tab_stats, NavIcons.Stats),
    SETTINGS(SettingsRoute, SettingsRoute::class, R.string.tab_settings, NavIcons.Settings);

    companion object {
        /** The tab [destination] belongs to, or `null` for a non-tab page. */
        fun of(destination: NavDestination?): TopLevelTab? =
            destination?.let { dest -> entries.firstOrNull { tab -> dest.hierarchy.any { it.hasRoute(tab.routeClass) } } }
    }
}
