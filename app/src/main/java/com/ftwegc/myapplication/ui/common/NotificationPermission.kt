package com.ftwegc.myapplication.ui.common

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ftwegc.myapplication.notify.Notifier

/** Whether reminders can reach the user. */
enum class NotificationStatus {
    /** Notifications are allowed. */
    ALLOWED,

    /** Android 13+: the permission was never requested (or can still be requested). */
    NOT_ASKED,

    /** Denied or turned off in the system settings: only the settings page can change it. */
    BLOCKED,
}

/**
 * Notification permission state and actions. [status] is re-read every time the screen resumes,
 * so coming back from the system settings updates it.
 */
@Stable
class NotificationPermissionState internal constructor(
    private val context: Context,
    private val launch: () -> Unit,
) {
    var status: NotificationStatus by mutableStateOf(NotificationStatus.ALLOWED)
        internal set

    /** The system dialog was shown before (persisted by the caller). */
    internal var askedBefore = false

    /** Called right before the system dialog is shown, so the caller can persist it. */
    internal var onRequested: () -> Unit = {}

    internal fun refresh() {
        status = when {
            Notifier.notificationsAllowed(context) -> NotificationStatus.ALLOWED
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && canStillAsk() -> NotificationStatus.NOT_ASKED
            else -> NotificationStatus.BLOCKED
        }
    }

    private fun canStillAsk(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val activity = context.findActivity() ?: return !askedBefore
        // Before the first request and after one "deny" the system still shows its dialog.
        return !askedBefore || ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Asks through the system dialog when that is still possible, otherwise opens the settings. */
    fun request() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && status == NotificationStatus.NOT_ASKED) {
            askedBefore = true
            onRequested()
            launch()
        } else {
            openSettings()
        }
    }

    /** The app's notification settings page. */
    fun openSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            openAppDetails(context)
        }
    }
}

/**
 * Remembers a [NotificationPermissionState]. [askedBefore] comes from persistent storage (the app
 * records that the system dialog was shown, see [onRequested]), so a permanent denial is
 * recognized after a restart.
 */
// The permission constant is only used after the Android 13 check in NotificationPermissionState.request().
@SuppressLint("InlinedApi")
@Composable
fun rememberNotificationPermission(askedBefore: Boolean, onRequested: () -> Unit = {}): NotificationPermissionState {
    val context = LocalContext.current
    val holder = remember { arrayOfNulls<NotificationPermissionState>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        holder[0]?.refresh()
    }
    val state = remember(context) {
        NotificationPermissionState(context) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    holder[0] = state
    state.onRequested = onRequested
    if (!LocalInspectionMode.current) {
        LaunchedEffect(askedBefore) {
            state.askedBefore = state.askedBefore || askedBefore
            state.refresh()
        }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.refresh() }
    }
    return state
}

/** Opens the system battery optimization list (no permission needed; nothing is requested). */
fun openBatteryOptimizationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        openAppDetails(context)
    }
}

private fun openAppDetails(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Nothing left to open.
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
