package com.ftwegc.myapplication.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.ui.glass.GlassDefaults
import com.ftwegc.myapplication.ui.glass.GlassThickness
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.glass.glass
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/*
 * The ring of the 续杯 mark outside the mark: the loading spinner and the success check draw its
 * open ring (mint → blue → violet, round caps, 74 % of the circle starting at the lower left); the
 * pull-to-refresh indicator and the connection test draw the whole cup (see VluerLogo.kt).
 */

/** Stroke of the ring relative to its size (thicker than in the mark, so small spinners read). */
private const val RING_STROKE = 4.5f / 30f

/** The mark's gap: the ring covers 74 % of the circle. */
private const val RING_SHARE = CupMark.RingShare

private fun DrawScope.drawVluerRing(sweepShare: Float, rotation: Float, colors: List<Color>, track: Color?) {
    val stroke = size.minDimension * RING_STROKE
    val inset = stroke / 2
    val arc = Size(size.width - stroke, size.height - stroke)
    if (track != null) drawCircle(track, radius = arc.width / 2, style = Stroke(stroke))
    if (sweepShare <= 0f) return
    rotate(rotation) {
        drawArc(
            brush = Brush.sweepGradient(colors + colors.first(), center = center),
            startAngle = CupMark.RingStart,
            sweepAngle = 360f * sweepShare.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arc,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

/**
 * Loading: the ring turning (one turn per 1.1 s) while its gap breathes a little. Still, with
 * the logo's gap, when animations are off.
 */
@Composable
fun VluerSpinner(modifier: Modifier = Modifier, size: Dp = 28.dp, colors: List<Color> = LogoRing, contentDescription: String? = null) {
    val reduced = LocalReducedMotion.current
    val description = contentDescription ?: stringResource(R.string.loading_desc)
    val transition = rememberInfiniteTransition(label = "vluerSpinner")
    val turn = if (reduced) 0f else transition.animateFloat(0f, 360f, infiniteRepeatable(tween(Motion.DurationSpinnerTurn, easing = LinearEasing)), label = "spinTurn").value
    val breathe = if (reduced) RING_SHARE else transition.animateFloat(
        0.55f, RING_SHARE,
        infiniteRepeatable(tween(Motion.DurationSpinnerBreathe, easing = Motion.Standard), RepeatMode.Reverse),
        label = "spinBreathe",
    ).value
    val track = currentGlass().ink.track
    Canvas(modifier.size(size).semantics { this.contentDescription = description }) {
        drawVluerRing(breathe, turn, colors, track)
    }
}

/**
 * Success: the ring draws itself around, then a check stroke follows and the mark pops a
 * little. Plays once when shown; the end state with animations off.
 */
@Composable
fun RingCheck(modifier: Modifier = Modifier, size: Dp = 26.dp, check: Color = Color.Unspecified, colors: List<Color> = LogoRing) {
    val reduced = LocalReducedMotion.current
    val ring = remember { Animatable(if (reduced) 1f else 0f) }
    val stroke = remember { Animatable(if (reduced) 1f else 0f) }
    val scale = remember { Animatable(if (reduced) 1f else 0.6f) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        coroutineScope {
            launch { scale.animateTo(1f, Motion.successSpring()) }
            ring.animateTo(1f, Motion.emphasizedDecelerate(Motion.DurationSuccessCircle))
            stroke.animateTo(1f, Motion.emphasizedDecelerate(Motion.DurationSuccessCheck))
        }
    }
    val ink = if (check == Color.Unspecified) currentGlass().ink.primary else check
    val description = stringResource(R.string.success_desc)
    val path = remember { Path() }
    val segment = remember { Path() }
    val measure = remember { PathMeasure() }
    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .semantics { contentDescription = description },
    ) {
        drawCircle(colors.first().copy(alpha = 0.14f), radius = this.size.minDimension / 2 * 0.86f)
        drawVluerRing(ring.value, 0f, colors, null)
        if (stroke.value > 0f) {
            val w = this.size.width
            val h = this.size.height
            path.reset()
            path.moveTo(w * 0.31f, h * 0.52f)
            path.lineTo(w * 0.45f, h * 0.65f)
            path.lineTo(w * 0.70f, h * 0.38f)
            measure.setPath(path, false)
            segment.reset()
            measure.getSegment(0f, measure.length * stroke.value, segment, true)
            drawPath(segment, ink, style = Stroke(this.size.minDimension * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * Busy (a refresh, a connection test): the cup of the mark, full, while its ring turns (one turn
 * per 1.1 s) and the waves drift. Still when animations are off.
 */
@Composable
fun CupSpinner(modifier: Modifier = Modifier, size: Dp = 44.dp, level: Float = CupMark.FullLevel, contentDescription: String? = null) {
    val reduced = LocalReducedMotion.current
    val description = contentDescription ?: stringResource(R.string.loading_desc)
    val transition = rememberInfiniteTransition(label = "cupSpinner")
    val turn = if (reduced) 0f else transition.animateFloat(0f, 360f, infiniteRepeatable(tween(Motion.DurationSpinnerTurn, easing = LinearEasing)), label = "cupTurn").value
    val drift = if (reduced) 0f else transition.animateFloat(0f, CupMark.WaveHalf * 2, infiniteRepeatable(tween(Motion.DurationWaveFront, easing = LinearEasing)), label = "cupDrift").value
    val ink = CupInk.of(currentGlass().dark)
    val paths = remember { CupPaths() }
    Canvas(modifier.size(size).semantics { this.contentDescription = description }) {
        drawCupMark(ink, level = level, ringRotation = turn, phase = drift, paths = paths)
    }
}

/**
 * A successful connection test: the cup fills up and its ring closes, then the mark pops a little.
 * Plays once when shown; the end state (full, closed) with animations off.
 */
@Composable
fun CupFill(modifier: Modifier = Modifier, size: Dp = 50.dp) {
    val reduced = LocalReducedMotion.current
    val fill = remember { Animatable(if (reduced) 1f else 0f) }
    val close = remember { Animatable(if (reduced) 1f else 0f) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        coroutineScope {
            launch { fill.animateTo(1f, tween(Motion.DurationCupFill, easing = Motion.Emphasized)) }
            close.animateTo(1f, tween(Motion.DurationRingClose, delayMillis = Motion.DurationCupFill - Motion.DurationRingClose, easing = Motion.EmphasizedDecelerate))
            scale.snapTo(0.9f)
            scale.animateTo(1f, Motion.successSpring())
        }
    }
    val ink = CupInk.of(currentGlass().dark)
    val description = stringResource(R.string.success_desc)
    val paths = remember { CupPaths() }
    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .semantics { contentDescription = description },
    ) {
        val f = fill.value
        drawCupMark(
            ink,
            level = CupMark.NormalLevel + (CupMark.FullLevel - CupMark.NormalLevel) * f,
            ringShare = CupMark.RingShare + (1f - CupMark.RingShare) * close.value,
            // Pouring in: the surface waves while it rises and calms down when full.
            phase = f * CupMark.WaveHalf,
            amplitude = 1f - 0.4f * f,
            paths = paths,
        )
    }
}

/**
 * Pull to refresh with the cup of the mark: while pulling, a small glass disc follows the finger
 * and the liquid rises the further it is pulled; released past the threshold the ring turns until
 * [isRefreshing] ends, and the liquid falls back as the disc leaves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VluerPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            val fraction = if (isRefreshing) 1f else state.distanceFraction
            val description = stringResource(R.string.refresh_desc)
            if (fraction > 0f || isRefreshing) Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        val reach = PullIndicatorReach.toPx()
                        translationY = reach * fraction.coerceAtMost(1.4f) - PullIndicatorSize.toPx()
                        alpha = fraction.coerceIn(0f, 1f)
                        val s = 0.7f + 0.3f * fraction.coerceIn(0f, 1f)
                        scaleX = s
                        scaleY = s
                    }
                    .size(PullIndicatorSize)
                    .glass(GlassDefaults.CapsuleShape, GlassThickness.Thin)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                if (isRefreshing) {
                    CupSpinner(size = PullCupSize)
                } else {
                    val ink = CupInk.of(currentGlass().dark)
                    val paths = remember { CupPaths() }
                    Canvas(Modifier.size(PullCupSize)) {
                        val pull = state.distanceFraction.coerceIn(0f, 1f)
                        drawCupMark(ink, level = PullEmptyLevel + (CupMark.FullLevel - PullEmptyLevel) * pull, amplitude = 0.6f + 0.4f * pull, paths = paths)
                    }
                }
            }
        },
        content = content,
    )
}

private val PullIndicatorSize = 48.dp
private val PullCupSize = 40.dp

/** The liquid at the very start of a pull: a thin layer at the bottom of the cup. */
private const val PullEmptyLevel = 0.06f

/** How far the disc travels at the refresh threshold (below the status bar). */
private val PullIndicatorReach = 110.dp
