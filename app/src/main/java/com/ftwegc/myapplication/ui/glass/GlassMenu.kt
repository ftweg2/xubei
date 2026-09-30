package com.ftwegc.myapplication.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion
import kotlin.math.roundToInt

/** One action of a [GlassMenu]. [destructive] actions are drawn in coral. */
@Immutable
data class GlassMenuItem(
    val label: String,
    /** Leading icon; 
ull for plain options (currencies). */
    val icon: ImageVector?,
    val destructive: Boolean = false,
    /** Shows a check at the end (a chosen option). */
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

private val MenuWidth = 224.dp
private val MenuShape = RoundedCornerShape(24.dp)
private val MenuGap = 10.dp
private val MenuMargin = 16.dp

/**
 * A thick glass menu next to [anchor] (root coordinates of the pressed element): below it when it
 * fits, otherwise above. Opens from the anchor with a little overshoot; tap outside or back closes
 * it. Drawn at the app root (see [Overlay]).
 */
@Composable
fun GlassMenu(
    visible: Boolean,
    anchor: Rect?,
    items: List<GlassMenuItem>,
    onDismissRequest: () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(false) }
    var lastAnchor by remember { mutableStateOf(anchor) }
    if (anchor != null) lastAnchor = anchor
    var lastItems by remember { mutableStateOf(items) }
    if (visible) lastItems = items
    LaunchedEffect(visible, reduced) {
        if (visible) {
            shown = true
            if (reduced) progress.snapTo(1f) else progress.animateTo(1f, tween(Motion.DurationMenu, easing = Motion.MenuEasing))
        } else {
            if (reduced) progress.snapTo(0f) else progress.animateTo(0f, tween(Motion.DurationShort, easing = Motion.EmphasizedAccelerate))
            shown = false
        }
    }
    val rect = lastAnchor ?: return
    if (!shown && !visible) return
    val dismiss by rememberUpdatedState(onDismissRequest)
    Overlay(modal = visible) {
        BackHandler(enabled = visible) { dismiss() }
        val env = currentGlass()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            var menuSize by remember { mutableStateOf(IntSize.Zero) }
            // A light veil: the page stays visible, the pressed tile is not covered by the menu.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = if (env.dark) 0.28f else 0.10f))
                    // No handler at all while closing (a disabled one still swallows taps).
                    .then(
                        if (visible) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { dismiss() },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clearAndSetSemantics { },
            )
            val maxW = with(density) { maxWidth.toPx() }
            val maxH = with(density) { maxHeight.toPx() }
            val gap = with(density) { MenuGap.toPx() }
            val margin = with(density) { MenuMargin.toPx() }
            val w = with(density) { MenuWidth.toPx() }
            val below = rect.bottom + gap + menuSize.height <= maxH - margin * 5
            val y = if (below) rect.bottom + gap else (rect.top - gap - menuSize.height).coerceAtLeast(margin * 3)
            val x = rect.left.coerceIn(margin, (maxW - w - margin).coerceAtLeast(margin))
            Column(
                Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .width(MenuWidth)
                    .onSizeChanged { menuSize = it }
                    .graphicsLayer {
                        val p = progress.value
                        val s = Motion.MenuStartScale + (1f - Motion.MenuStartScale) * p
                        scaleX = s
                        scaleY = s
                        alpha = p.coerceIn(0f, 1f)
                        val ox = ((rect.center.x - x) / size.width).coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(ox, if (below) 0f else 1f)
                    }
                    .glass(MenuShape, GlassThickness.Thick, floating = true)
                    .padding(vertical = 6.dp),
            ) {
                lastItems.forEach { item ->
                    MenuRow(item, enabled = visible) {
                        dismiss()
                        item.onClick()
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(item: GlassMenuItem, enabled: Boolean, onClick: () -> Unit) {
    val env = currentGlass()
    val color = if (item.destructive) Color(if (env.dark) 0xFFFDA4AF else 0xFFE11D48) else env.ink.primary
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (item.selected) selected = true }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.icon != null) {
            Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(item.label, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
        if (item.selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = GlassAccents.teal(env.dark), modifier = Modifier.size(18.dp))
    }
}
