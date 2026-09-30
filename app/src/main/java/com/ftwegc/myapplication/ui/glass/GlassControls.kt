package com.ftwegc.myapplication.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.ui.components.rememberHapticClick
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion

/** Accent and alert colors on glass (mockup `:root`), deeper in light mode for contrast. */
object GlassAccents {
    fun teal(dark: Boolean): Color = if (dark) Color(0xFF2DD4BF) else Color(0xFF0D9488)
    fun amber(dark: Boolean): Color = if (dark) Color(0xFFFCD34D) else Color(0xFFB45309)
    fun coral(dark: Boolean): Color = if (dark) Color(0xFFFDA4AF) else Color(0xFFE11D48)
    fun mint(dark: Boolean): Color = if (dark) Color(0xFF7EF0DD) else Color(0xFF0F766E)
}

/** Mockup `.ic`: a 42 dp glass circle with a 20 dp line icon; [glassTint] washes the glass with a color. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    tint: Color = Color.Unspecified,
    glassTint: Color = Color.Unspecified,
) {
    val env = currentGlass()
    GlassSurface(
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
        shape = GlassDefaults.CapsuleShape,
        tint = glassTint,
        onClick = onClick,
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (tint == Color.Unspecified) env.ink.primary else tint, modifier = Modifier.size(20.dp))
    }
}

/** A standard glass panel with the mockup's 16 dp padding (the bento block of every page). */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(modifier = modifier, tint = tint, onClick = onClick, onClickLabel = onClickLabel) {
        Column(Modifier.padding(padding)) { content() }
    }
}

/** Mockup `.fld`: 18 dp corners, a faint fill with a bright top edge. */
val FieldShape = RoundedCornerShape(18.dp)

/**
 * Mockup `.fld`: a thin glass field with an 11 sp label above the 15 sp value. Focus draws a teal
 * ring, an [error] a coral one with the message below; [supporting] shows below otherwise.
 * [locked] adds the sync lock after the label.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    supporting: String? = null,
    locked: Boolean = false,
    lockDescription: String? = null,
    prefix: String? = null,
    suffix: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val env = currentGlass()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val teal = GlassAccents.teal(env.dark)
    val coral = GlassAccents.coral(env.dark)
    val ring by animateColorAsState(
        when {
            error != null -> coral
            focused -> teal
            else -> Color.Transparent
        },
        animationSpec = tween(if (LocalReducedMotion.current) 0 else Motion.DurationShort),
        label = "fieldRing",
    )
    val valueStyle = TextStyle(color = env.ink.primary, fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum")
    Column(modifier) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, ring, FieldShape)
                .semantics { if (error != null) error(error) },
            shape = FieldShape,
            thickness = GlassThickness.Thin,
        ) {
            Row(Modifier.padding(start = 14.dp, end = if (trailing != null) 4.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(vertical = 11.dp),
                ) {
                    FieldLabel(label, locked, lockDescription)
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (prefix != null) {
                            Text(prefix, color = env.ink.secondary, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(end = 4.dp))
                        }
                        Box(Modifier.weight(1f)) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(placeholder, color = env.ink.secondary.copy(alpha = env.ink.secondary.alpha * 0.7f), fontSize = 15.sp, lineHeight = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            BasicTextField(
                                value = value,
                                onValueChange = onValueChange,
                                textStyle = valueStyle,
                                singleLine = singleLine,
                                minLines = if (singleLine) 1 else minLines,
                                keyboardOptions = keyboardOptions,
                                keyboardActions = keyboardActions,
                                visualTransformation = visualTransformation,
                                cursorBrush = SolidColor(teal),
                                interactionSource = interaction,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { this.contentDescription = label },
                            )
                        }
                        if (suffix != null) {
                            Text(suffix, color = env.ink.secondary, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
                if (trailing != null) trailing()
            }
        }
        FieldNote(error, supporting)
    }
}

/**
 * A `.fld` that is tapped instead of typed in (dates, currencies): label above, [value] (or a
 * [placeholder]) below, an optional [trailing] icon.
 */
@Composable
fun GlassSelectField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    supporting: String? = null,
    locked: Boolean = false,
    lockDescription: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val env = currentGlass()
    val coral = GlassAccents.coral(env.dark)
    Column(modifier) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (error != null) Modifier.border(1.5.dp, coral, FieldShape) else Modifier)
                .semantics { if (error != null) error(error) },
            shape = FieldShape,
            thickness = GlassThickness.Thin,
            onClick = onClick,
            onClickLabel = label,
        ) {
            Row(Modifier.padding(start = 14.dp, end = if (trailing != null) 4.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(vertical = 11.dp),
                ) {
                    FieldLabel(label, locked, lockDescription)
                    val empty = value.isEmpty()
                    Text(
                        if (empty) placeholder.orEmpty() else value,
                        color = if (empty) env.ink.secondary else env.ink.primary,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (trailing != null) trailing()
            }
        }
        FieldNote(error, supporting)
    }
}

@Composable
private fun FieldLabel(label: String, locked: Boolean, lockDescription: String?) {
    val env = currentGlass()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = env.ink.secondary, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1)
        if (locked) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = lockDescription,
                tint = GlassAccents.amber(env.dark),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(12.dp),
            )
        }
    }
}

@Composable
private fun FieldNote(error: String?, supporting: String?) {
    val env = currentGlass()
    val text = error ?: supporting ?: return
    Text(
        text,
        color = if (error != null) GlassAccents.coral(env.dark) else env.ink.secondary,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 5.dp),
    )
}

/**
 * Mockup `.seg`: a glass capsule with the options side by side and a bright glass pill under the
 * chosen one that slides between them. [labels] and [selected] index; -1 selects nothing.
 */
@Composable
fun GlassSegmented(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val env = currentGlass()
    val reduced = LocalReducedMotion.current
    val position by animateFloatAsState(
        targetValue = selected.coerceAtLeast(0).toFloat(),
        animationSpec = if (reduced) tween(0) else tween(Motion.DurationDropMove, easing = Motion.DropMoveEasing),
        label = "segment",
    )
    val pill = if (env.dark) {
        listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.10f))
    } else {
        listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.70f))
    }
    val edge = Color.White.copy(alpha = if (env.dark) 0.6f else 0.95f)
    GlassSurface(modifier = modifier, shape = GlassDefaults.CapsuleShape, thickness = GlassThickness.Thin) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(4.dp),
        ) {
            val count = labels.size.coerceAtLeast(1)
            val itemWidth = maxWidth / count
            if (selected >= 0) {
                Canvas(
                    Modifier
                        .width(itemWidth)
                        .height(34.dp)
                        .graphicsLayer { translationX = itemWidth.toPx() * position },
                ) {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(Brush.linearGradient(pill, start = Offset.Zero, end = Offset(size.width, size.height)), cornerRadius = r)
                    val stroke = 1.dp.toPx()
                    drawRoundRect(
                        Brush.verticalGradient(0f to edge, 0.45f to edge.copy(alpha = 0f)),
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = r,
                        style = Stroke(stroke),
                    )
                }
            }
            Row(Modifier.selectableGroup()) {
                labels.forEachIndexed { index, label ->
                    val on = index == selected
                    val click = rememberHapticClick({ onSelect(index) })
                    Box(
                        Modifier
                            .width(itemWidth)
                            .height(34.dp)
                            .clip(GlassDefaults.CapsuleShape)
                            .selectable(selected = on, enabled = enabled, role = Role.RadioButton, onClick = click),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (on) (if (env.dark) Color.White else env.ink.primary) else env.ink.secondary,
                            fontSize = 12.5.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A row with a title, a description and a [GlassSwitch]; the whole row toggles. [locked] adds the
 * sync lock after the title.
 */
@Composable
fun GlassSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    locked: Boolean = false,
    lockDescription: String? = null,
    enabled: Boolean = true,
) {
    val env = currentGlass()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = env.ink.primary, fontSize = 15.sp, lineHeight = 20.sp)
                if (locked) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = lockDescription,
                        tint = GlassAccents.amber(env.dark),
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(13.dp),
                    )
                }
            }
            if (description != null) {
                Text(description, color = env.ink.secondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        GlassSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** A small section title inside a sheet (mockup `.lab`, 12 sp secondary ink). */
@Composable
fun SheetSectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val env = currentGlass()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, color = env.ink.secondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}
