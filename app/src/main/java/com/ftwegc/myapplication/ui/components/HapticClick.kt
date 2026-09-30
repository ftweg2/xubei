package com.ftwegc.myapplication.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Wraps [onClick] so that it also performs a light haptic tick. */
@Composable
fun rememberHapticClick(
    onClick: () -> Unit,
    type: HapticFeedbackType = HapticFeedbackType.ContextClick,
): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(onClick, haptics, type) {
        {
            haptics.performHapticFeedback(type)
            onClick()
        }
    }
}
