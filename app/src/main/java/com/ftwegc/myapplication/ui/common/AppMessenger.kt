package com.ftwegc.myapplication.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import com.ftwegc.myapplication.ui.glass.SuccessVisuals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * App-wide success messages (snackbar with the animated check). Lives at the root, so a message
 * sent right before popping a page is still shown on the page below.
 */
@Stable
class AppMessenger(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    /**
     * Shows [message]; with [actionLabel] the message stays longer and [onAction] runs on tap.
     * [onAction] runs in the app-wide scope, so it survives the page that sent the message.
     */
    fun showSuccess(message: String, actionLabel: String? = null, onAction: (suspend () -> Unit)? = null) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            val result = hostState.showSnackbar(
                SuccessVisuals(
                    message = message,
                    actionLabel = actionLabel,
                    duration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
                ),
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }

    /** Shows a message without the check (for neutral information). */
    fun showInfo(message: String) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(SuccessVisuals(message = message, withCheck = false))
        }
    }
}

val LocalAppMessenger = staticCompositionLocalOf<AppMessenger?> { null }
