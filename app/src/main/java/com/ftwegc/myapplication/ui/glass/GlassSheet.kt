package com.ftwegc.myapplication.ui.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion
import androidx.compose.ui.graphics.RectangleShape
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

/** Sheet geometry (mockup `.sheet`, `.handle`, `.dim`). */
object SheetDefaults {
    /** `border-radius: 36px 36px 0 0`. */
    val Shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)

    /** `padding: 10px 18px 30px`. */
    val PaddingTop: Dp = 10.dp
    val PaddingSide: Dp = 18.dp
    val PaddingBottom: Dp = 30.dp

    /** `.handle`: 38 × 5, white .4, 14 px below. */
    val HandleWidth: Dp = 38.dp
    val HandleHeight: Dp = 5.dp
    val HandleGap: Dp = 14.dp

    /** `.dim`: rgba(3,6,10,.45) over a 6 px blur. */
    val DimColor = Color(0xFF03060A).copy(alpha = 0.45f)
    val LightDimColor = Color(0xFF0B1A20).copy(alpha = 0.22f)
    val DimBlur: Dp = 6.dp

    /** A partial sheet leaves this much of the page visible above it. */
    val PartialTopGap: Dp = 56.dp

    /** Dragging the handle down this far closes the sheet. */
    val DismissDrag: Dp = 110.dp
}

/**
 * Where a sheet grows from: remember it with [rememberGrowOrigin] and attach [modifier] to the
 * button that opens the sheet.
 */
class GrowOrigin {
    var center: Offset by mutableStateOf(Offset.Unspecified)
        internal set

    val modifier: Modifier = Modifier.onGloballyPositioned { coords ->
        val p = coords.positionInRoot()
        center = Offset(p.x + coords.size.width / 2f, p.y + coords.size.height / 2f)
    }
}

@Composable
fun rememberGrowOrigin(): GrowOrigin = remember { GrowOrigin() }

/**
 * A thick glass bottom sheet drawn at the app root (see [Overlay]): the page behind is dimmed and
 * blurred, the sheet itself refracts it (Android 13+). It grows out of [origin] (a circle opening
 * from the button that opened it) and shrinks back into it when closed; without an origin it
 * rises from the bottom edge.
 *
 * Glass inside the sheet samples the sheet (the sheet exports its backdrop), as the glass library
 * requires for glass on glass. The sheet moves up with the keyboard; [bottomBar] (a save button)
 * stays pinned above the keyboard while [content] scrolls.
 *
 * @param expanded when true the sheet fills the screen (below the status bar); otherwise it is
 *   as tall as its content, leaving [SheetDefaults.PartialTopGap] of the page visible at most.
 * @param onExpandedChange the handle drags between the two heights; `null` keeps [expanded].
 * @param onDismissRequest tap outside, back, or a drag down on the handle.
 */
@Composable
fun GlassSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    origin: GrowOrigin? = null,
    expanded: Boolean = false,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    contentDescription: String? = null,
    scrollable: Boolean = true,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (visible && reduced) 1f else 0f) }
    var shown by remember { mutableStateOf(visible) }
    LaunchedEffect(visible, reduced) {
        if (visible) {
            shown = true
            if (reduced) progress.snapTo(1f) else progress.animateTo(1f, tween(Motion.DurationSheetOpen, easing = Motion.SheetEasing))
        } else {
            if (reduced) progress.snapTo(0f) else progress.animateTo(0f, tween(Motion.DurationSheetClose, easing = Motion.EmphasizedAccelerate))
            shown = false
        }
    }
    if (!shown && !visible) return
    Overlay(modal = visible) {
        SheetFrame(
            progress = { progress.value },
            visible = visible,
            onDismissRequest = onDismissRequest,
            origin = origin,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            contentDescription = contentDescription,
            scrollable = scrollable,
            bottomBar = bottomBar,
            modifier = modifier,
            content = content,
        )
    }
}

@Composable
private fun SheetFrame(
    progress: () -> Float,
    visible: Boolean,
    onDismissRequest: () -> Unit,
    origin: GrowOrigin?,
    expanded: Boolean,
    onExpandedChange: ((Boolean) -> Unit)?,
    contentDescription: String?,
    scrollable: Boolean,
    bottomBar: (@Composable () -> Unit)?,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val env = currentGlass()
    val density = LocalDensity.current
    val dismiss by rememberUpdatedState(onDismissRequest)
    BackHandler(enabled = visible) { dismiss() }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val sheetLayer = if (env.tier.blurs) rememberLayerBackdrop() else null
    var sheetOriginInRoot by remember { mutableStateOf(Offset.Zero) }

    Box(Modifier.fillMaxSize()) {
        // The dimmed, blurred page (mockup .dim).
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress() }
                .then(if (env.tier.blurs) Modifier.glassDim(env.dark) else Modifier.background(if (env.dark) SheetDefaults.DimColor else SheetDefaults.LightDimColor))
                // A disabled clickable still takes part in hit testing and would swallow the taps
                // meant for the dock sliding back in: while closing there is no handler at all.
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
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val maxSheet = if (expanded) maxHeight else maxHeight - statusTop - SheetDefaults.PartialTopGap
            Column(
                modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheet)
                    .then(if (expanded) Modifier.fillMaxSize().padding(top = statusTop) else Modifier)
                    .onGloballyPositioned { sheetOriginInRoot = it.positionInRoot() }
                    .graphicsLayer {
                        val p = progress()
                        translationY = drag.value.coerceAtLeast(0f) + if (origin == null) (1f - p) * size.height else (1f - p) * 24.dp.toPx()
                        val center = origin?.center
                        if (center != null && center.isValid()) {
                            clip = true
                            shape = RevealShape(center - sheetOriginInRoot, p)
                        }
                    }
                    .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
                    .glass(
                        shape = SheetDefaults.Shape,
                        thickness = GlassThickness.Thick,
                        floating = true,
                        exportTo = sheetLayer,
                    ),
            ) {
                val childSource = GlassSource(sheetLayer, LocalGlassSource.current.depth + 1, exported = sheetLayer != null)
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalGlassSource provides childSource,
                    androidx.compose.material3.LocalContentColor provides env.ink.primary,
                ) {
                    // Handle: drag down to close, up / down to switch the height.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        val moved = drag.value
                                        scope.launch {
                                            when {
                                                moved > with(density) { SheetDefaults.DismissDrag.toPx() } -> {
                                                    if (expanded && onExpandedChange != null) onExpandedChange(false) else dismiss()
                                                    drag.animateTo(0f, tween(Motion.DurationMedium, easing = Motion.Emphasized))
                                                }
                                                moved < -with(density) { SheetDefaults.PartialTopGap.toPx() } / 2 && !expanded && onExpandedChange != null -> {
                                                    onExpandedChange(true)
                                                    drag.snapTo(0f)
                                                }
                                                else -> drag.animateTo(0f, tween(Motion.DurationMedium, easing = Motion.Emphasized))
                                            }
                                        }
                                    },
                                    onDragCancel = { scope.launch { drag.animateTo(0f) } },
                                ) { change, amount ->
                                    change.consume()
                                    scope.launch { drag.snapTo((drag.value + amount).coerceAtLeast(-with(density) { SheetDefaults.PartialTopGap.toPx() })) }
                                }
                            }
                            .padding(top = SheetDefaults.PaddingTop, bottom = SheetDefaults.HandleGap),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(SheetDefaults.HandleWidth, SheetDefaults.HandleHeight)
                                .clip(RoundedCornerShape(SheetDefaults.HandleHeight))
                                .background(env.ink.primary.copy(alpha = if (env.dark) 0.4f else 0.25f)),
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                            .padding(horizontal = SheetDefaults.PaddingSide),
                    ) { content() }
                    if (bottomBar != null) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = SheetDefaults.PaddingSide, end = SheetDefaults.PaddingSide, top = 10.dp, bottom = max(navBottom.value, SheetDefaults.PaddingBottom.value).dp),
                        ) { bottomBar() }
                    } else {
                        Box(Modifier.padding(bottom = max(navBottom.value, SheetDefaults.PaddingBottom.value).dp))
                    }
                }
            }
        }
    }
}

/** Mockup `.dim`: the page blurred a little and darkened (lightened a little in light mode). */
@Composable
internal fun Modifier.glassDim(dark: Boolean): Modifier {
    val color = if (dark) SheetDefaults.DimColor else SheetDefaults.LightDimColor
    val page = LocalPageBackdrop.current ?: return background(color)
    return drawBackdrop(
        backdrop = page,
        shape = { RectangleShape },
        effects = { blur(SheetDefaults.DimBlur.toPx()) },
        highlight = null,
        shadow = null,
        onDrawSurface = { drawRect(color) },
    )
}

private fun Offset.isValid(): Boolean = this != Offset.Unspecified && x.isFinite() && y.isFinite()

/** A circle around [center] (sheet coordinates) covering [fraction] of the way to the farthest corner. */
private class RevealShape(private val center: Offset, private val fraction: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (fraction >= 1f) return Outline.Rectangle(Rect(Offset.Zero, size))
        val far = maxOf(
            hypot(center.x, center.y),
            hypot(size.width - center.x, center.y),
            hypot(center.x, size.height - center.y),
            hypot(size.width - center.x, size.height - center.y),
        )
        val start = with(density) { 24.dp.toPx() }
        val r = start + (far - start) * fraction.coerceIn(0f, 1f)
        return Outline.Generic(Path().apply { addOval(Rect(center, r)) })
    }
}
