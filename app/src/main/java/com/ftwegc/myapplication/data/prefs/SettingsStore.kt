package com.ftwegc.myapplication.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ftwegc.myapplication.domain.model.ReminderDays
import com.ftwegc.myapplication.domain.model.VpsConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Sort order of the subscription list. */
enum class SortOrder {
    /** Earliest due date first; no due date / permanent last. */
    DUE_DATE,

    /** Highest monthly-equivalent cost first. */
    PRICE,

    /** Name in Chinese collation order. */
    NAME,
}

/** The app's theme: follow the system, or always dark / light. */
enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
}

/**
 * User preferences needed by the UI.
 *
 * @property reminderDays days before the due date to remind on (0 = the due date itself),
 *   sorted descending. The largest value is the "due soon" window.
 * @property defaultCurrency currency pre-filled for a new subscription.
 * @property trafficAlertPercent default alert threshold for a new VPS.
 * @property komariUrl normalized Komari panel address, `null` when not configured. The API key is
 *   not here: it lives encrypted in [SecretStore].
 * @property komariLastSyncAt when nodes were last synced from the panel (epoch millis).
 * @property refreshIntervalMinutes background traffic refresh interval; 0 = off.
 * @property resetZoneId time zone of traffic reset days, `null` = the device zone.
 * @property notificationPromptShown the app already showed its notification explanation once.
 * @property notificationPermissionRequested the system permission dialog was shown at least once
 *   (Android 13+); after a denial only the system settings can turn notifications on.
 * @property reduceTransparency "降低透明效果": glass is drawn nearly opaque and without blur.
 * @property glassTierOverride debug builds only: forces a lower glass tier ("blurred" / "tinted")
 *   to preview the Android 12 / Android 11 fallbacks; `null` = what the device supports.
 * @property themeMode dark by default (the app is designed dark first), light, or the system's.
 * @property backgroundAnimation the flowing background drifts; off, it stands still.
 */
data class AppSettings(
    val sortOrder: SortOrder = SortOrder.DUE_DATE,
    val showArchived: Boolean = false,
    val reminderDays: List<Int> = DEFAULT_REMINDER_DAYS,
    val defaultCurrency: String = DEFAULT_CURRENCY,
    val trafficAlertPercent: Int = VpsConfig.DEFAULT_ALERT_PERCENT,
    val komariUrl: String? = null,
    val komariLastSyncAt: Long? = null,
    val refreshIntervalMinutes: Int = DEFAULT_REFRESH_MINUTES,
    val resetZoneId: String? = null,
    val notificationPromptShown: Boolean = false,
    val notificationPermissionRequested: Boolean = false,
    val reduceTransparency: Boolean = false,
    val glassTierOverride: String? = null,
    /** The subscription page shows tiles (true) or compact rows (false). */
    val listGrid: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val backgroundAnimation: Boolean = true,
) {
    /** True when a panel address is saved. */
    val komariConfigured: Boolean get() = komariUrl != null

    /** Window (days) in which a subscription counts as "due soon". */
    val dueSoonDays: Int get() = reminderDays.maxOrNull()?.coerceAtLeast(1) ?: DEFAULT_DUE_SOON_DAYS

    companion object {
        val DEFAULT_REMINDER_DAYS: List<Int> = listOf(7, 3, 1, 0)
        const val DEFAULT_CURRENCY: String = "CNY"
        const val DEFAULT_DUE_SOON_DAYS: Int = 7
        const val MAX_REMINDER_DAYS: Int = ReminderDays.MAX
        const val DEFAULT_REFRESH_MINUTES: Int = 30

        /** Offered refresh intervals in minutes; 0 turns background refresh off. */
        val REFRESH_CHOICES: List<Int> = listOf(0, 15, 30, 60, 180, 360)
    }
}

/** DataStore-backed [AppSettings]. Read errors fall back to defaults. */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map(::read)
        .distinctUntilChanged()

    suspend fun setSortOrder(order: SortOrder) {
        dataStore.edit { it[KEY_SORT] = order.name }
    }

    suspend fun setShowArchived(show: Boolean) {
        dataStore.edit { it[KEY_SHOW_ARCHIVED] = show }
    }

    suspend fun setDefaultCurrency(currency: String) {
        val value = currency.trim().ifEmpty { AppSettings.DEFAULT_CURRENCY }
        dataStore.edit { it[KEY_CURRENCY] = value }
    }

    suspend fun setReminderDays(days: Collection<Int>) {
        dataStore.edit { it[KEY_REMINDER_DAYS] = encodeDays(days) }
    }

    suspend fun setTrafficAlertPercent(percent: Int) {
        val value = percent.coerceIn(VpsConfig.MIN_ALERT_PERCENT, VpsConfig.MAX_ALERT_PERCENT)
        dataStore.edit { it[KEY_ALERT] = value }
    }

    /** Saves the panel address ([url] should already be normalized); `null` or blank removes it. */
    suspend fun setKomariUrl(url: String?) {
        dataStore.edit {
            val value = url?.trim()
            if (value.isNullOrEmpty()) {
                it.remove(KEY_KOMARI_URL)
                it.remove(KEY_KOMARI_SYNC)
            } else {
                it[KEY_KOMARI_URL] = value
            }
        }
    }

    suspend fun setKomariLastSyncAt(millis: Long) {
        dataStore.edit { it[KEY_KOMARI_SYNC] = millis }
    }

    /** Background refresh interval in minutes; values not in [AppSettings.REFRESH_CHOICES] are ignored. */
    suspend fun setRefreshIntervalMinutes(minutes: Int) {
        if (minutes !in AppSettings.REFRESH_CHOICES) return
        dataStore.edit { it[KEY_REFRESH] = minutes }
    }

    /** Reset-day time zone; `null` or an unknown id means the device zone. */
    suspend fun setResetZoneId(zoneId: String?) {
        val valid = zoneId?.takeIf { isValidZone(it) }
        dataStore.edit { if (valid == null) it.remove(KEY_RESET_ZONE) else it[KEY_RESET_ZONE] = valid }
    }

    suspend fun setNotificationPromptShown(shown: Boolean) {
        dataStore.edit { it[KEY_NOTIFY_PROMPT] = shown }
    }

    suspend fun setNotificationPermissionRequested(requested: Boolean) {
        dataStore.edit { it[KEY_NOTIFY_REQUESTED] = requested }
    }

    suspend fun setReduceTransparency(reduce: Boolean) {
        dataStore.edit { it[KEY_REDUCE_TRANSPARENCY] = reduce }
    }

    /** Debug glass tier override; `null` or blank removes it. */
    suspend fun setGlassTierOverride(key: String?) {
        val value = key?.trim()?.takeIf { it.isNotEmpty() }
        dataStore.edit { if (value == null) it.remove(KEY_GLASS_TIER) else it[KEY_GLASS_TIER] = value }
    }

    suspend fun setListGrid(grid: Boolean) {
        dataStore.edit { it[KEY_LIST_GRID] = grid }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setBackgroundAnimation(animate: Boolean) {
        dataStore.edit { it[KEY_BACKGROUND_ANIMATION] = animate }
    }

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_BACKGROUND_ANIMATION = booleanPreferencesKey("background_animation")
        private val KEY_LIST_GRID = booleanPreferencesKey("list_view_grid")
        private val KEY_KOMARI_URL = stringPreferencesKey("komari_url")
        private val KEY_KOMARI_SYNC = longPreferencesKey("komari_last_sync_at")
        private val KEY_SORT = stringPreferencesKey("sort_order")
        private val KEY_SHOW_ARCHIVED = booleanPreferencesKey("show_archived")
        private val KEY_REMINDER_DAYS = stringPreferencesKey("reminder_days")
        private val KEY_CURRENCY = stringPreferencesKey("default_currency")
        private val KEY_ALERT = intPreferencesKey("traffic_alert_percent")
        private val KEY_REFRESH = intPreferencesKey("refresh_interval_minutes")
        private val KEY_RESET_ZONE = stringPreferencesKey("reset_zone_id")
        private val KEY_NOTIFY_PROMPT = booleanPreferencesKey("notification_prompt_shown")
        private val KEY_NOTIFY_REQUESTED = booleanPreferencesKey("notification_permission_requested")
        private val KEY_REDUCE_TRANSPARENCY = booleanPreferencesKey("reduce_transparency")
        private val KEY_GLASS_TIER = stringPreferencesKey("debug_glass_tier")

        private fun isValidZone(id: String): Boolean = try {
            java.time.ZoneId.of(id)
            true
        } catch (_: java.time.DateTimeException) {
            false
        }

        internal fun read(prefs: Preferences): AppSettings = AppSettings(
            sortOrder = SortOrder.entries.firstOrNull { it.name == prefs[KEY_SORT] } ?: SortOrder.DUE_DATE,
            showArchived = prefs[KEY_SHOW_ARCHIVED] ?: false,
            reminderDays = prefs[KEY_REMINDER_DAYS]?.let(::decodeDays) ?: AppSettings.DEFAULT_REMINDER_DAYS,
            defaultCurrency = prefs[KEY_CURRENCY]?.takeIf { it.isNotBlank() } ?: AppSettings.DEFAULT_CURRENCY,
            trafficAlertPercent = (prefs[KEY_ALERT] ?: VpsConfig.DEFAULT_ALERT_PERCENT)
                .coerceIn(VpsConfig.MIN_ALERT_PERCENT, VpsConfig.MAX_ALERT_PERCENT),
            komariUrl = prefs[KEY_KOMARI_URL]?.takeIf { it.isNotBlank() },
            komariLastSyncAt = prefs[KEY_KOMARI_SYNC],
            refreshIntervalMinutes = prefs[KEY_REFRESH]?.takeIf { it in AppSettings.REFRESH_CHOICES }
                ?: AppSettings.DEFAULT_REFRESH_MINUTES,
            resetZoneId = prefs[KEY_RESET_ZONE]?.takeIf { isValidZone(it) },
            notificationPromptShown = prefs[KEY_NOTIFY_PROMPT] ?: false,
            notificationPermissionRequested = prefs[KEY_NOTIFY_REQUESTED] ?: false,
            reduceTransparency = prefs[KEY_REDUCE_TRANSPARENCY] ?: false,
            glassTierOverride = prefs[KEY_GLASS_TIER]?.takeIf { it.isNotBlank() },
            listGrid = prefs[KEY_LIST_GRID] ?: true,
            themeMode = ThemeMode.entries.firstOrNull { it.name == prefs[KEY_THEME] } ?: ThemeMode.DARK,
            backgroundAnimation = prefs[KEY_BACKGROUND_ANIMATION] ?: true,
        )

        /** "7,3,1,0" form; values outside 0..MAX_REMINDER_DAYS are dropped, duplicates removed. */
        internal fun encodeDays(days: Collection<Int>): String = ReminderDays.encode(days)

        internal fun decodeDays(raw: String): List<Int> = ReminderDays.decode(raw)
    }
}
