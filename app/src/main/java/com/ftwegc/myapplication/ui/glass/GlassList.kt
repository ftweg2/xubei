package com.ftwegc.myapplication.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.ui.components.rememberHapticClick
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion

/*
 * Grouped lists of glass (settings, Komari): each group is one glass panel with a small title
 * above it; the rows inside are separated by hairlines, not by more glass.
 */

/** Row insets: icon tile, gap, text. */
object GlassListDefaults {
    val RowMinHeight: Dp = 60.dp
    val IconTile: Dp = 34.dp
    val HorizontalPadding: Dp = 14.dp
    val DividerIndent: Dp = HorizontalPadding + IconTile + 12.dp
}

/** A group: the title (mockup `.lab`) and one standard glass panel holding [content]. */
@Composable
fun GlassGroup(title: String?, modifier: Modifier = Modifier, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val env = currentGlass()
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                color = env.ink.secondary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(start = 6.dp, bottom = 8.dp)
                    .semantics { heading() },
            )
        }
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
        if (footer != null) {
            Text(
                footer,
                color = env.ink.secondary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 7.dp),
            )
        }
    }
}

/** The hairline between two rows of a group, starting after the icon. */
@Composable
fun GlassDivider(modifier: Modifier = Modifier, indent: Dp = GlassListDefaults.DividerIndent) {
    val env = currentGlass()
    Box(
        modifier
            .padding(start = indent, end = GlassListDefaults.HorizontalPadding)
            .fillMaxWidth()
            .height(1.dp)
            .background(env.ink.primary.copy(alpha = if (env.dark) 0.09f else 0.08f)),
    )
}

/**
 * The icon of a row: a small rounded tile washed with [tint] (like a mark, mockup `.mark.s`), a
 * white line icon on it.
 */
@Composable
fun GlassIconTile(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = GlassListDefaults.IconTile, dimmed: Boolean = false) {
    val shape = RoundedCornerShape(size * 0.32f)
    val start = lerp(tint, Color.White, 0.18f)
    val end = lerp(tint, Color.Black, 0.22f)
    Box(
        modifier
            .size(size)
            .graphicsLayer { alpha = if (dimmed) 0.5f else 1f }
            .clip(shape)
            .background(Brush.linearGradient(listOf(start, end)))
            .drawBehind {
                // inset 0 1px 0 rgba(255,255,255,.5): the bright top edge of a mark.
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    Brush.verticalGradient(0f to Color.White.copy(alpha = 0.5f), 0.4f to Color.Transparent),
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke),
                    cornerRadius = CornerRadius(this.size.minDimension * 0.32f),
                    style = Stroke(stroke),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

/**
 * One row of a group: icon tile, title, a dim [subtitle] below, and at the end a [value] (teal),
 * a [trailing] element and a chevron when it opens something ([chevron]).
 */
@Composable
fun GlassRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Color(0xFF2DD4BF),
    value: String? = null,
    valueColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    chevron: Boolean = onClick != null && value == null,
    role: Role = Role.Button,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val env = currentGlass()
    val click = if (onClick != null && enabled) rememberHapticClick(onClick) else null
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = GlassListDefaults.RowMinHeight)
            .then(if (click != null) Modifier.clickable(role = role, onClick = click) else Modifier)
            .padding(horizontal = GlassListDefaults.HorizontalPadding, vertical = 10.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.55f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            GlassIconTile(icon, iconTint)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = env.ink.primary, fontSize = 15.sp, lineHeight = 20.sp)
            if (!subtitle.isNullOrEmpty()) {
                Text(subtitle, color = env.ink.secondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (value != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                value,
                color = if (valueColor == Color.Unspecified) GlassAccents.mint(env.dark) else valueColor,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
        if (chevron || (value != null && onClick != null)) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = env.ink.secondary.copy(alpha = env.ink.secondary.alpha * 0.8f),
                modifier = Modifier
                    .padding(start = 2.dp)
                    .size(20.dp),
            )
        }
    }
}

/**
 * A round glass check: an empty ring when off, a teal-to-blue drop with a white check when on
 * (it pops in). Decoration; the row carries the checkbox semantics.
 */
@Composable
fun GlassCheck(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, size: Dp = 24.dp) {
    val env = currentGlass()
    val reduced = LocalReducedMotion.current
    val on by animateFloatAsState(
        if (checked) 1f else 0f,
        if (reduced) tween(0) else tween(Motion.DurationMedium, easing = Motion.DialogEasing),
        label = "glassCheck",
    )
    val ring by animateColorAsState(if (checked) Color.Transparent else env.ink.secondary.copy(alpha = 0.55f), label = "glassCheckRing")
    val teal = Color(0xFF2DD4BF)
    val blue = Color(0xFF60A5FA)
    Canvas(
        modifier
            .size(size)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f },
    ) {
        val r = this.size.minDimension / 2
        val stroke = 1.6.dp.toPx()
        drawCircle(ring, radius = r - stroke / 2, style = Stroke(stroke))
        if (on > 0f) {
            val rr = (r * on.coerceAtMost(1.1f)).coerceAtMost(r)
            drawCircle(Brush.linearGradient(listOf(teal, blue), start = Offset.Zero, end = Offset(this.size.width, this.size.height)), radius = rr)
            drawCircle(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.45f), 0.5f to Color.Transparent), radius = rr)
            val w = this.size.width
            val h = this.size.height
            val t = ((on - 0.35f) / 0.65f).coerceIn(0f, 1f)
            if (t > 0f) {
                val a = Offset(w * 0.29f, h * 0.52f)
                val b = Offset(w * 0.44f, h * 0.66f)
                val c = Offset(w * 0.72f, h * 0.37f)
                val sw = 2.2.dp.toPx()
                val first = (t * 2f).coerceAtMost(1f)
                drawLine(Color.White, a, a + (b - a) * first, strokeWidth = sw, cap = StrokeCap.Round)
                if (t > 0.5f) drawLine(Color.White, b, b + (c - b) * ((t - 0.5f) * 2f), strokeWidth = sw, cap = StrokeCap.Round)
            }
        }
    }
}

/** A choice row with a check at the end when [selected] (single-choice lists). */
@Composable
fun GlassChoiceRow(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, description: String? = null) {
    val env = currentGlass()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClick = rememberHapticClick(onClick))
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = env.ink.primary, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            if (description != null) Text(description, color = env.ink.secondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = GlassAccents.teal(env.dark), modifier = Modifier.size(20.dp))
    }
}

/** A flat tinted notice inside glass: an [accent] icon, the text, an optional action pill. */
@Composable
fun GlassNotice(
    icon: ImageVector,
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    title: String? = null,
    action: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
) {
    val env = currentGlass()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = if (env.dark) 0.15f else 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (title != null) Text(title, color = env.ink.primary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
                Text(text, color = if (title != null) env.ink.secondary else env.ink.primary, fontSize = 12.5.sp, lineHeight = 18.sp)
            }
        }
        if (action != null && onAction != null) {
            GlassPillButton(action, onAction, Modifier.padding(top = 10.dp).align(Alignment.End)) {
                if (actionIcon != null) Icon(actionIcon, contentDescription = null, tint = env.ink.primary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
