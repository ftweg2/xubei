package com.ftwegc.myapplication.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.ui.glass.GlassAccents
import com.ftwegc.myapplication.ui.glass.GlassButton
import com.ftwegc.myapplication.ui.glass.GlassButtonStyle
import com.ftwegc.myapplication.ui.glass.GlassDefaults
import com.ftwegc.myapplication.ui.glass.GlassIconTile
import com.ftwegc.myapplication.ui.glass.GlassSheet
import com.ftwegc.myapplication.ui.glass.GlassSurface
import com.ftwegc.myapplication.ui.glass.GlassThickness
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.preview.GlassPreview
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion

/**
 * The app's own explanation before the system notification dialog, as a glass sheet: what the app
 * notifies about and that nothing else is sent. [onAllow] continues to the system dialog (or the
 * settings); [onLater] (also back or a tap outside) closes it.
 */
@Composable
fun NotificationExplainSheet(visible: Boolean, onAllow: () -> Unit, onLater: () -> Unit) {
    GlassSheet(
        visible = visible,
        onDismissRequest = onLater,
        contentDescription = stringResource(R.string.notify_explain_title),
    ) {
        NotificationExplainContent(onAllow = onAllow, onLater = onLater)
    }
}

@Composable
private fun NotificationExplainContent(onAllow: () -> Unit, onLater: () -> Unit) {
    val env = currentGlass()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        RingingBell()
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.notify_explain_title),
            color = env.ink.primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.notify_explain_message),
            color = env.ink.secondary,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ExplainRow(Icons.AutoMirrored.Rounded.EventNote, Color(0xFFF59E0B), stringResource(R.string.notify_explain_due))
            ExplainRow(Icons.Rounded.DataUsage, Color(0xFF14B8A6), stringResource(R.string.notify_explain_traffic))
        }
        Spacer(Modifier.height(22.dp))
        GlassButton(stringResource(R.string.notify_explain_allow), onAllow, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        GlassButton(
            stringResource(R.string.notify_explain_later),
            onLater,
            Modifier.fillMaxWidth(),
            style = GlassButtonStyle.Secondary,
            height = 46.dp,
        )
    }
}

@Composable
private fun ExplainRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, text: String) {
    val env = currentGlass()
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlassIconTile(icon, tint)
        Spacer(Modifier.width(12.dp))
        Text(text, color = env.ink.primary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

/** The bell on a teal glass disc; it rings (a short damped swing) once when the sheet opens. */
@Composable
private fun RingingBell() {
    val env = currentGlass()
    val reduced = LocalReducedMotion.current
    val swing = remember { Animatable(0f) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        kotlinx.coroutines.delay(Motion.DurationMedium.toLong())
        for (angle in floatArrayOf(18f, -14f, 10f, -6f, 3f)) {
            swing.animateTo(angle, Motion.emphasized(Motion.DurationIconBounceUp))
        }
        swing.animateTo(0f, Motion.iconBounceSpring())
    }
    val teal = GlassAccents.teal(env.dark)
    GlassSurface(
        modifier = Modifier
            .padding(top = 4.dp)
            .size(76.dp),
        shape = GlassDefaults.CapsuleShape,
        thickness = GlassThickness.Thin,
        tint = teal,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.NotificationsActive,
            contentDescription = null,
            tint = GlassAccents.mint(env.dark),
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0.1f)
                    rotationZ = swing.value
                },
        )
    }
}

@PreviewLightDark
@Composable
private fun NotificationExplainPreview() {
    GlassPreview {
        NotificationExplainContent(onAllow = {}, onLater = {})
    }
}
