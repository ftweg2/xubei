package com.ftwegc.myapplication.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.ui.components.rememberHapticClick
import com.ftwegc.myapplication.ui.theme.Motion
import com.ftwegc.myapplication.ui.theme.motionSpec

/** How a [GlassButton] is filled. */
enum class GlassButtonStyle {
    /** Mockup `.btn`: teal-to-blue filled glass with white text (the main action). */
    Primary,

    /** Plain glass with ink text (cancel, secondary actions). */
    Secondary,

    /** Coral filled glass with white text (delete, discard). */
    Destructive,
}

/** Fills of the filled styles (135°: start, 45 %, end), like the add button's glass. */
object GlassButtonFills {
    val Primary: List<Color> = listOf(Color(0xFF2DD4BF).copy(alpha = 0.80f), Color(0xFF3FA7E0).copy(alpha = 0.66f), Color(0xFF60A5FA).copy(alpha = 0.58f))
    val Destructive: List<Color> = listOf(Color(0xFFFB7185).copy(alpha = 0.86f), Color(0xFFE8466A).copy(alpha = 0.74f), Color(0xFFBE285A).copy(alpha = 0.66f))

    /** The light theme lightens what is behind the glass: deeper, denser fills keep white text readable. */
    val PrimaryLight: List<Color> = listOf(Color(0xFF0D9488).copy(alpha = 0.92f), Color(0xFF0E7FB8).copy(alpha = 0.88f), Color(0xFF2563EB).copy(alpha = 0.84f))
    val DestructiveLight: List<Color> = listOf(Color(0xFFE11D48).copy(alpha = 0.92f), Color(0xFFBE123C).copy(alpha = 0.88f), Color(0xFF9F1239).copy(alpha = 0.84f))

    fun primary(dark: Boolean): List<Color> = if (dark) Primary else PrimaryLight
    fun destructive(dark: Boolean): List<Color> = if (dark) Destructive else DestructiveLight
}

/**
 * A glass capsule button (mockup `.btn`: 50 dp high, 25 dp corners, 14.5 sp semibold). Presses
 * in like every glass; disabled it fades to half.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: GlassButtonStyle = GlassButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 50.dp,
    trailing: (@Composable () -> Unit)? = null,
) {
    val env = currentGlass()
    // Disabled: plain glass (no color fill) with dimmed text; the glass itself stays solid, so
    // nothing behind it shows through.
    val ink = when {
        style == GlassButtonStyle.Secondary || !enabled -> env.ink.primary
        else -> Color.White
    }
    GlassSurface(
        modifier = modifier.height(height),
        shape = RoundedCornerShape(height / 2),
        thickness = if (style == GlassButtonStyle.Secondary) GlassThickness.Thin else GlassThickness.Standard,
        onClick = if (enabled) onClick else null,
        contentAlignment = Alignment.Center,
        fill = when {
            !enabled -> null
            style == GlassButtonStyle.Primary -> GlassButtonFills.primary(env.dark)
            style == GlassButtonStyle.Destructive -> GlassButtonFills.destructive(env.dark)
            else -> null
        },
    ) {
        Row(
            Modifier
                .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(19.dp))
            Text(text, color = ink, fontSize = if (height < 48.dp) 14.sp else 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            trailing?.invoke()
        }
    }
}

/**
 * A bare icon button for places inside glass (clearing a field, a close cross): no glass of its
 * own, a soft round highlight while pressed.
 */
@Composable
fun GlassPlainIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 40.dp,
    iconSize: Dp = 18.dp,
) {
    val env = currentGlass()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val glow by animateFloatAsState(if (pressed) 1f else 0f, motionSpec(Motion.standard(Motion.DurationShort)), label = "plainIconPress")
    val highlight = env.ink.primary.copy(alpha = if (env.dark) 0.14f else 0.08f)
    Box(
        modifier
            .size(size)
            .drawBehind { if (glow > 0f) drawCircle(highlight.copy(alpha = highlight.alpha * glow), radius = this.size.minDimension / 2 * (0.7f + 0.3f * glow)) }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = rememberHapticClick(onClick),
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (tint == Color.Unspecified) env.ink.secondary else tint, modifier = Modifier.size(iconSize))
    }
}
