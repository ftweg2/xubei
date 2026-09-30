package com.ftwegc.myapplication.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Dialog geometry: a thick glass card with 30 dp corners in the middle of the screen. */
object GlassDialogDefaults {
    val Shape = RoundedCornerShape(30.dp)
    val MaxWidth = 380.dp
    val SideMargin = 28.dp
    val Padding = 22.dp
    val ButtonHeight = 46.dp
}

/** The last non-null [value]: a closing dialog keeps showing what it showed. */
@Composable
fun <T : Any> rememberLatest(value: T?): T? {
    var last by remember { mutableStateOf(value) }
    if (value != null) last = value
    return last
}

/**
 * A thick glass dialog drawn at the app root (see [Overlay]): the page behind is dimmed and
 * blurred (like under a sheet), the card refracts it on Android 13+ and grows out of the middle
 * with a little overshoot. Back or a tap outside calls [onDismissRequest] (unless
 * [dismissOnOutside] is false). While it closes it no longer takes touches.
 *
 * Glass inside the card samples the card (it exports its backdrop), as nested glass must.
 */
@Composable
fun GlassDialogFrame(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    paneTitle: String? = null,
    dismissOnOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (visible && reduced) 1f else 0f) }
    var shown by remember { mutableStateOf(visible) }
    LaunchedEffect(visible, reduced) {
        if (visible) {
            shown = true
            if (reduced) progress.snapTo(1f) else progress.animateTo(1f, tween(Motion.DurationDialogOpen, easing = Motion.DialogEasing))
        } else {
            if (reduced) progress.snapTo(0f) else progress.animateTo(0f, tween(Motion.DurationDialogClose, easing = Motion.EmphasizedAccelerate))
            shown = false
        }
    }
    if (!shown && !visible) return
    val dismiss by rememberUpdatedState(onDismissRequest)
    // Over a sheet (the date picker in the editor) the blurred page would hide the sheet: dim only.
    val host = LocalOverlayHost.current
    val overOverlay = remember(visible) { (host?.modalCount ?: 0) > 0 }
    Overlay(modal = visible) {
        BackHandler(enabled = visible) { dismiss() }
        val env = currentGlass()
        val layer = if (env.tier.blurs) rememberLayerBackdrop() else null
        Box(
            Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                    .then(if (env.tier.blurs && !overOverlay) Modifier.glassDim(env.dark) else Modifier.background(if (env.dark) SheetDefaults.DimColor else SheetDefaults.LightDimColor))
                    // No pointer handling while closing: the page (and the dock) take touches at once.
                    .then(
                        if (visible) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { if (dismissOnOutside) dismiss() },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clearAndSetSemantics { },
            )
            // Over a sheet the page backdrop does not contain the sheet, so sampling it would show
            // the page through the dialog: the dialog is then tinted glass (no sampling).
            val pageBackdrop = if (overOverlay) null else LocalPageBackdrop.current
            val outerSource = if (overOverlay) GlassSource(null, 0) else LocalGlassSource.current
            CompositionLocalProvider(LocalPageBackdrop provides pageBackdrop, LocalGlassSource provides outerSource) {
                Column(
                    modifier
                        .padding(horizontal = GlassDialogDefaults.SideMargin)
                        .widthIn(max = GlassDialogDefaults.MaxWidth)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val p = progress.value
                            val s = Motion.DialogStartScale + (1f - Motion.DialogStartScale) * p
                            scaleX = s
                            scaleY = s
                            alpha = p.coerceIn(0f, 1f)
                        }
                        .semantics { if (paneTitle != null) this.paneTitle = paneTitle }
                        .then(
                            if (overOverlay) {
                                Modifier.background(env.material(GlassThickness.Thick).solid.copy(alpha = OVER_SHEET_ALPHA), GlassDialogDefaults.Shape)
                            } else {
                                Modifier
                            },
                        )
                        .glass(GlassDialogDefaults.Shape, GlassThickness.Thick, floating = true, exportTo = layer),
                ) {
                    CompositionLocalProvider(
                        LocalGlassSource provides GlassSource(layer, LocalGlassSource.current.depth + 1, exported = layer != null),
                        LocalContentColor provides env.ink.primary,
                    ) {
                        Column(
                            Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(GlassDialogDefaults.Padding),
                        ) { content() }
                    }
                }
            }
        }
    }
}

/** How opaque a dialog over a sheet is under its glass (the sheet's text must not show through). */
private const val OVER_SHEET_ALPHA = 0.9f

/**
 * A confirmation in glass: an icon on a tinted glass disc, the title, the message, optional extra
 * [content], then "取消" and the confirming button side by side ([destructive]: coral).
 */
@Composable
fun GlassConfirmDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    message: String? = null,
    icon: ImageVector? = null,
    accent: Color = Color.Unspecified,
    destructive: Boolean = false,
    dismissLabel: String? = stringResource(R.string.action_cancel),
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    GlassDialogFrame(visible = visible, onDismissRequest = onDismissRequest, paneTitle = title) {
        GlassDialogHeader(title = title, message = message, icon = icon, accent = accent, destructive = destructive)
        content?.invoke(this)
        Spacer(Modifier.height(20.dp))
        GlassDialogButtons(
            confirmLabel = confirmLabel,
            onConfirm = onConfirm,
            dismissLabel = dismissLabel,
            onDismiss = onDismissRequest,
            destructive = destructive,
            confirmEnabled = confirmEnabled && visible,
        )
    }
}

/** Icon disc, centered title and message of a dialog. */
@Composable
fun ColumnScope.GlassDialogHeader(title: String, message: String?, icon: ImageVector?, accent: Color = Color.Unspecified, destructive: Boolean = false) {
    val env = currentGlass()
    val tint = when {
        accent != Color.Unspecified -> accent
        destructive -> GlassAccents.coral(env.dark)
        else -> GlassAccents.teal(env.dark)
    }
    if (icon != null) {
        GlassSurface(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(56.dp),
            shape = GlassDefaults.CapsuleShape,
            thickness = GlassThickness.Thin,
            tint = tint,
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(14.dp))
    }
    Text(
        title,
        color = env.ink.primary,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (message != null) {
        Text(
            message,
            color = env.ink.secondary,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

/** "取消" (plain glass) and the confirming button, side by side; one button fills the row. */
@Composable
fun GlassDialogButtons(
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String?,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (dismissLabel != null) {
            GlassButton(
                text = dismissLabel,
                onClick = onDismiss,
                style = GlassButtonStyle.Secondary,
                height = GlassDialogDefaults.ButtonHeight,
                modifier = Modifier.weight(1f),
            )
        }
        GlassButton(
            text = confirmLabel,
            onClick = onConfirm,
            style = if (destructive) GlassButtonStyle.Destructive else GlassButtonStyle.Primary,
            enabled = confirmEnabled,
            height = GlassDialogDefaults.ButtonHeight,
            modifier = Modifier.weight(1f),
        )
    }
}
