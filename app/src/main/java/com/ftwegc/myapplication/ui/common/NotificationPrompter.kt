package com.ftwegc.myapplication.ui.common

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Asks for the notification permission at a sensible moment (after the first subscription with a
 * due date was saved), with the app's own explanation first. Provided by the app root.
 */
@Stable
fun interface NotificationPrompter {
    /** Shows the explanation when it was never shown and notifications are not allowed yet. */
    fun maybePrompt()
}

val LocalNotificationPrompter = staticCompositionLocalOf<NotificationPrompter?> { null }
