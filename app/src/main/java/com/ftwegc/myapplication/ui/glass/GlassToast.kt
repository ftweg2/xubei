package com.ftwegc.myapplication.ui.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.ui.brand.RingCheck

/** Visuals of an app message; [withCheck] adds the ring check (a success). */
data class SuccessVisuals(
    override val message: String,
    override val actionLabel: String? = null,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
    val withCheck: Boolean = true,
) : SnackbarVisuals {
    override val withDismissAction: Boolean get() = false
}

private val ToastShape = RoundedCornerShape(24.dp)

/**
 * The app's message ("已保存", "已归档 · 撤销"): a floating thick glass capsule over the page with
 * the ring check for a success (an info mark otherwise), the message and an optional action as a
 * small teal glass pill.
 */
@Composable
fun GlassToast(data: SnackbarData, modifier: Modifier = Modifier) {
    val visuals = data.visuals
    val haptics = LocalHapticFeedback.current
    val success = (visuals as? SuccessVisuals)?.withCheck ?: true
    LaunchedEffect(data) { if (success) haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    GlassToastContent(visuals.message, visuals.actionLabel, success, onAction = { data.performAction() }, modifier = modifier)
}

@Composable
fun GlassToastContent(message: String, actionLabel: String?, success: Boolean, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val env = currentGlass()
    val outer = LocalGlassSource.current
    androidx.compose.foundation.layout.Box(
        modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .glass(ToastShape, GlassThickness.Thick, floating = true),
    ) {
        // The action pill inside is drawn flat on the toast (glass in glass).
        androidx.compose.runtime.CompositionLocalProvider(LocalGlassSource provides GlassSource(null, outer.depth + 1)) {
            ToastRow(message, actionLabel, success, onAction, env)
        }
    }
}

@Composable
private fun ToastRow(message: String, actionLabel: String?, success: Boolean, onAction: () -> Unit, env: GlassEnvironment) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 14.dp, end = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (success) {
            RingCheck(size = 28.dp)
        } else {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = GlassAccents.teal(env.dark), modifier = Modifier.size(22.dp))
        }
        Text(
            message,
            color = env.ink.primary,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 13.dp),
        )
        if (actionLabel != null) {
            GlassSurface(
                shape = GlassDefaults.CapsuleShape,
                thickness = GlassThickness.Thin,
                tint = GlassAccents.teal(env.dark),
                onClick = onAction,
                onClickLabel = actionLabel,
            ) {
                Text(
                    actionLabel,
                    color = GlassAccents.mint(env.dark),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        } else {
            androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        }
    }
}
