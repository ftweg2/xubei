package com.ftwegc.myapplication.ui.glass

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion

/** Block label (mockup `.lab`: 12 px, secondary ink), with optional trailing text. */
@Composable
fun GlassLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val ink = currentGlass().ink
    // With trailing text the label spans the block, so the text sits at the right edge (mockup `.sp`).
    Row(if (trailing != null) modifier.fillMaxWidth() else modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            color = ink.secondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            modifier = Modifier
                .weight(1f, fill = trailing != null)
                .semantics { heading() },
        )
        if (trailing != null) {
            Text(trailing, color = ink.secondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/**
 * A small glass capsule (mockup `.chip`: 11.5 px, 5 × 10 px padding, white .1 with a bright top
 * edge). Inside a panel it is drawn flat; on its own it is thin glass. [selected] is `.chip.on`.
 */
@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val env = currentGlass()
    // Mockup `.chip.on`: a brighter white glass (135°, .36 → .12) with a stronger top edge.
    val on = if (env.dark) {
        listOf(Color.White.copy(alpha = 0.36f), Color.White.copy(alpha = 0.12f))
    } else {
        listOf(Color.White.copy(alpha = 0.96f), Color.White.copy(alpha = 0.72f))
    }
    val edge = Color.White.copy(alpha = if (env.dark) 0.6f else 0.95f)
    GlassSurface(
        modifier = modifier.semantics { this.selected = selected },
        shape = GlassDefaults.CapsuleShape,
        thickness = GlassThickness.Thin,
        onClick = onClick,
    ) {
        Text(
            text,
            color = when {
                color != Color.Unspecified -> color
                selected && env.dark -> Color.White
                else -> env.ink.primary
            },
            fontSize = 11.5.sp,
            lineHeight = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .drawBehind {
                    if (selected) {
                        val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
                        drawRoundRect(androidx.compose.ui.graphics.Brush.linearGradient(on, end = androidx.compose.ui.geometry.Offset(size.width, size.height)), cornerRadius = r)
                        val stroke = 1.dp.toPx()
                        drawRoundRect(
                            androidx.compose.ui.graphics.Brush.verticalGradient(0f to edge, 0.5f to edge.copy(alpha = 0f)),
                            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                            cornerRadius = r,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                        )
                    }
                }
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/**
 * Online dot (mockup `.dot`: 7 px teal, a 9 px ring breathing out every 2.4 s). Still with
 * animations off; grey and still when [online] is false.
 */
@Composable
fun BreathingDot(online: Boolean, modifier: Modifier = Modifier, color: Color = Color(0xFF2DD4BF), size: Dp = 7.dp) {
    val reduced = LocalReducedMotion.current
    val pulse by if (!online || reduced) {
        remember { mutableFloatStateOf(1f) }
    } else {
        rememberInfiniteTransition(label = "breathe").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(Motion.DurationBreathe, easing = Motion.StandardDecelerate), RepeatMode.Restart),
            label = "breatheValue",
        )
    }
    val offline = currentGlass().ink.secondary
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        if (online && pulse < 1f) {
            // box-shadow: 0 0 0 0 → 9px, rgba(45,212,191,.6) fading to 0 by 70 %.
            val t = (pulse / 0.7f).coerceAtMost(1f)
            val ring = Motion.BreatheRing.toPx() * t
            drawCircle(color.copy(alpha = 0.6f * (1f - t)), radius = r + ring)
        }
        drawCircle(if (online) color else offline.copy(alpha = 0.6f), radius = r)
    }
}

/** A legend row (mockup `.legend`): dot, name, value on the right. */
@Composable
fun LegendRow(color: Color, name: String, value: String, modifier: Modifier = Modifier) {
    val ink = currentGlass().ink
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text(name, color = ink.secondary, fontSize = 11.5.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(value, color = ink.primary, fontSize = 11.5.sp, lineHeight = 15.sp, maxLines = 1)
    }
}

/** A glass capsule button with a label (inside panels: a flat thin glass). */
@Composable
fun GlassPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leading: @Composable RowScope.() -> Unit = {}) {
    val env = currentGlass()
    GlassSurface(
        modifier = modifier,
        shape = GlassDefaults.CapsuleShape,
        thickness = GlassThickness.Thin,
        tint = Color(0xFF2DD4BF),
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            leading()
            Text(text, color = env.ink.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
