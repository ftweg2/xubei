package com.ftwegc.myapplication

import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ftwegc.myapplication.data.prefs.ThemeMode
import com.ftwegc.myapplication.dev.DevHooks
import com.ftwegc.myapplication.notify.Notifier
import com.ftwegc.myapplication.ui.OPEN_HOME_REQUEST
import com.ftwegc.myapplication.ui.SplashOverlay
import com.ftwegc.myapplication.ui.VluerRoot
import com.ftwegc.myapplication.ui.theme.Motion
import com.ftwegc.myapplication.ui.theme.VluerTheme
import com.ftwegc.myapplication.ui.theme.isSystemAnimationDisabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Subscription a tapped notification asked to open ([OPEN_HOME_REQUEST]: the overview); consumed by [VluerRoot]. */
    private val openRequest = MutableStateFlow<Long?>(null)

    /** [Notifier.EXTRA_OPEN_TOKEN] of the last notification tap handled (kept across recreation). */
    private var handledToken = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DevHooks.onActivityCreate(this)
        // Draw behind the status and navigation bars; every screen handles its own insets.
        enableEdgeToEdge()
        // The mark's splash animation continues the system splash on a fresh start (not after a
        // rotation, and not with animations off).
        val playSplash = savedInstanceState == null && !isSystemAnimationDisabled(this) && !DevHooks.settle
        splashPlaying = playSplash
        fadeOutSplash()
        handledToken = savedInstanceState?.getLong(KEY_HANDLED_TOKEN) ?: 0L
        // A recreation (rotation, or the process was killed in the background) delivers the old
        // launch intent again: only a tap that was not handled yet opens its target.
        consume(intent, restored = savedInstanceState != null)
        // The first frame waits (behind the splash) until the stored theme is known, so the app
        // never flashes in the other theme.
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!themeReady) return false
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    // Before Android 12 there is no splash view: the launch window gives way now.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) splashRevealed.value = true
                    return true
                }
            },
        )
        setContent {
            val settingsFlow = remember { (application as VluerApp).container.settingsStore.settings.map { it.themeMode } }
            val mode by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val systemDark = isSystemInDarkTheme()
            val current = mode ?: return@setContent
            val dark = when (current) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            LaunchedEffect(dark) {
                // Status and navigation bar icons follow the app's theme, not the system's.
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                themeReady = true
            }
            LaunchedEffect(current) { applySplashTheme(current) }
            var splash by rememberSaveable { mutableStateOf(playSplash) }
            VluerTheme(darkTheme = dark, reducedMotion = if (DevHooks.settle) true else null) {
                Box(Modifier.fillMaxSize()) {
                    VluerRoot(openRequest = openRequest)
                    if (splash) {
                        SplashOverlay(dark = dark, revealed = splashRevealed) {
                            splash = false
                            splashPlaying = false
                        }
                    }
                }
            }
        }
    }

    /** The stored theme has been read (the first frame may be drawn). */
    private var themeReady = false

    /** [SplashOverlay] is (about to be) drawn over the app. */
    private var splashPlaying = false

    /** The system splash (or the launch window) is gone: the app's first frame is on screen. */
    private val splashRevealed = MutableStateFlow(false)

    /**
     * Android 12+: the system splash (the first frame of the mark's splash) leaves as soon as the
     * app's first frame is ready. When [SplashOverlay] plays, it draws that same frame at the same
     * place, so the system splash is simply removed and the overlay animates on; otherwise (a
     * restart after rotation) it fades out while the mark grows a little. With animations off it
     * is removed at once.
     */
    private fun fadeOutSplash() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        splashScreen.setOnExitAnimationListener { view ->
            if (splashPlaying || isSystemAnimationDisabled(this)) {
                view.remove()
                splashRevealed.value = true
                return@setOnExitAnimationListener
            }
            val easing = PathInterpolator(0.2f, 0f, 0f, 1f)
            view.iconView?.animate()
                ?.scaleX(Motion.SplashExitIconScale)
                ?.scaleY(Motion.SplashExitIconScale)
                ?.setDuration(Motion.DurationSplashExit.toLong())
                ?.setInterpolator(easing)
                ?.start()
            view.animate()
                .alpha(0f)
                .setDuration(Motion.DurationSplashExit.toLong())
                .setInterpolator(easing)
                .withEndAction { view.remove() }
                .start()
        }
    }

    /**
     * Android 13+: the next system splash follows the app's theme setting, not only the system's
     * dark mode (the system keeps the choice across launches). "Follow system" resets it to
     * Theme.Vluer, whose resources follow the system. Android 12 and older always follow the
     * system: the splash is drawn before the app runs and there is no way to choose it there.
     */
    private fun applySplashTheme(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val theme = when (mode) {
            ThemeMode.SYSTEM -> Resources.ID_NULL
            ThemeMode.DARK -> R.style.Theme_Vluer_SplashDark
            ThemeMode.LIGHT -> R.style.Theme_Vluer_SplashLight
        }
        try {
            splashScreen.setSplashScreenTheme(theme)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not set the splash theme", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consume(intent, restored = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_HANDLED_TOKEN, handledToken)
    }

    private fun consume(intent: Intent?, restored: Boolean) {
        val id = openTarget(intent, handledToken, restored) ?: return
        handledToken = intent?.getLongExtra(Notifier.EXTRA_OPEN_TOKEN, 0L) ?: 0L
        openRequest.value = id
        // The tapped row is gone now; a summary left without rows is removed.
        // Off the main thread: the notifier paces its posts (see NotificationPacer).
        (application as? VluerApp)?.let { app ->
            app.applicationScope.launch {
                delay(SUMMARY_REFRESH_DELAY_MS)
                app.container.notifier.refreshSummaries()
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val KEY_HANDLED_TOKEN = "handled_notification_token"
        private const val SUMMARY_REFRESH_DELAY_MS = 1_000L

        /**
         * The subscription [intent] asks to open ([OPEN_HOME_REQUEST] for the overview), or `null`:
         * not a notification intent, a launch
         * from the recent apps list (which repeats the task's first intent), or a tap already
         * handled ([handledToken]). [restored]: the activity is being recreated from saved state,
         * where an intent without a token cannot be told apart from one already handled.
         */
        fun openTarget(intent: Intent?, handledToken: Long, restored: Boolean = false): Long? {
            val action = intent?.action
            if (action != Notifier.ACTION_OPEN_SUBSCRIPTION && action != Notifier.ACTION_OPEN_HOME) return null
            if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
            val token = intent.getLongExtra(Notifier.EXTRA_OPEN_TOKEN, 0L)
            if (if (token == 0L) restored else token == handledToken) return null
            if (action == Notifier.ACTION_OPEN_HOME) return OPEN_HOME_REQUEST
            val id = intent.getLongExtra(Notifier.EXTRA_SUBSCRIPTION_ID, 0L)
            return id.takeIf { it > 0 }
        }
    }
}
