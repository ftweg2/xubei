package com.ftwegc.myapplication.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * True when the system "remove animations" setting is on (animator duration scale == 0).
 *
 * Compose already scales its own animation clocks by that setting, but coroutine `delay`s,
 * infinite transitions and custom sequences are not scaled; components read this local to jump
 * straight to their end state and skip decorative loops.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Current system animator duration scale (1 when unavailable). */
fun animatorDurationScale(context: Context): Float =
    try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (_: SecurityException) {
        1f
    }

/** True when the animator duration scale is 0. */
fun isSystemAnimationDisabled(context: Context): Boolean = animatorDurationScale(context) == 0f

/** Observes the system animator scale and returns true while animations are disabled. */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current.applicationContext
    var disabled by remember(context) { mutableStateOf(isSystemAnimationDisabled(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                disabled = isSystemAnimationDisabled(context)
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        context.contentResolver.registerContentObserver(uri, false, observer)
        // Re-read in case the value changed between remember and registration.
        disabled = isSystemAnimationDisabled(context)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return disabled
}
