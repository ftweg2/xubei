package com.ftwegc.myapplication.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.repo.SubscriptionRepository
import com.ftwegc.myapplication.ui.background.BackgroundMood
import com.ftwegc.myapplication.ui.background.BackgroundMoods
import com.ftwegc.myapplication.ui.glass.GlassTier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Glass switches from the settings. */
@Immutable
data class GlassSettings(
    val reduceTransparency: Boolean = false,
    val tierOverride: GlassTier? = null,
    /** "背景动画": the flowing background drifts (off: it stands still). */
    val backgroundAnimation: Boolean = true,
)

/**
 * State of the app shell around the pages: the background mood (from the subscriptions) and the
 * glass settings. Activity scoped.
 */
class AppChromeViewModel(
    private val repository: SubscriptionRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val mood: StateFlow<BackgroundMood> = repository.observeAll()
        .map { BackgroundMoods.of(it, repository.today()) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BackgroundMood.Calm)

    val glass: StateFlow<GlassSettings> = settingsStore.settings
        .map { GlassSettings(it.reduceTransparency, GlassTier.fromKey(it.glassTierOverride), it.backgroundAnimation) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), GlassSettings())

    /** "降低透明效果" (also on the settings page, "外观"). */
    fun setReduceTransparency(reduce: Boolean) {
        viewModelScope.launch { settingsStore.setReduceTransparency(reduce) }
    }

    /** Debug builds: force the Android 12 / Android 11 glass on this device; `null` = native. */
    fun setGlassTierOverride(tier: GlassTier?) {
        viewModelScope.launch { settingsStore.setGlassTierOverride(tier?.key) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
