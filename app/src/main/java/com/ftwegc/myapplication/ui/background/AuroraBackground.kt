package com.ftwegc.myapplication.ui.background

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ftwegc.myapplication.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Time of the background drift. Advanced by [AuroraBackground] a few times per second while the
 * app is visible; frozen while [paused] (scrolling, page transitions) and when animations are off.
 */
@Stable
class AuroraClock {
    var timeMs: Long by mutableLongStateOf(0L)
        internal set

    /** Pause requests by owner: scrolling lists and page transitions freeze the drift. */
    private val pauses = mutableStateListOf<Any>()

    /** [hold] deadline, [System.nanoTime] based; read only by the drift loop. */
    @Volatile
    private var holdUntilNanos = 0L

    val paused: Boolean get() = pauses.isNotEmpty() || System.nanoTime() < holdUntilNanos

    /** Freezes the drift for [millis] (called on every scroll step, so it stays still while scrolling). */
    fun hold(millis: Long) {
        holdUntilNanos = System.nanoTime() + millis * 1_000_000
    }

    fun pause(owner: Any) {
        if (owner !in pauses) pauses += owner
    }

    fun resume(owner: Any) {
        pauses -= owner
    }
}

val LocalAuroraClock = staticCompositionLocalOf<AuroraClock?> { null }

/** Page tones requested by the pages on screen (the newest wins), e.g. a detail page's color. */
@Stable
class BackgroundToneController {
    private val requests = mutableStateListOf<Pair<Any, PageTone>>()

    /** The newest requested tone, or `null` to use the tab's own tone. */
    val requested: PageTone? get() = requests.lastOrNull()?.second

    fun request(owner: Any, tone: PageTone) {
        requests.removeAll { it.first === owner }
        requests += owner to tone
    }

    fun release(owner: Any) {
        requests.removeAll { it.first === owner }
    }
}

val LocalBackgroundTone = staticCompositionLocalOf<BackgroundToneController?> { null }

/**
 * Tints the background with [tone] while this composable is on screen. The detail page uses it
 * with [PageTone.Accent] (the subscription's representative color).
 */
@Composable
fun PageBackgroundTone(tone: PageTone) {
    val controller = LocalBackgroundTone.current ?: return
    val owner = remember { Any() }
    DisposableEffect(controller, tone) {
        controller.request(owner, tone)
        onDispose { controller.release(owner) }
    }
}

/**
 * The flowing color field behind every page: a few large, soft blobs drifting slowly.
 *
 * Cheap by design: each blob is one radial gradient (no blur pass), and the picture changes only
 * [Motion.AuroraFrameIntervalMillis] apart (the drift is a few pixels per second, so that is
 * smooth to the eye). The clock stops while the app is not started, while [clock] is paused and
 * when [animate] is false (system animations off): the picture then stays still. A palette change
 * (another page, another mood) cross-fades.
 */
@Composable
fun AuroraBackground(
    palette: AuroraPalette,
    animate: Boolean,
    modifier: Modifier = Modifier,
    clock: AuroraClock = remember { AuroraClock() },
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(clock, animate, lifecycle) {
        if (!animate) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = -1L
            while (isActive) {
                delay(Motion.AuroraFrameIntervalMillis)
                // Infinite-animation frame: tests and "animations off" policies stop it.
                withInfiniteAnimationFrameMillis { now ->
                    if (last >= 0 && !clock.paused) {
                        clock.timeMs += (now - last).coerceIn(0L, Motion.AuroraMaxStepMillis)
                    }
                    last = now
                }
            }
        }
    }

    // Cross-fade between palettes.
    var shown by remember { mutableStateOf(palette) }
    var previous by remember { mutableStateOf<AuroraPalette?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(palette, animate) {
        if (palette == shown) return@LaunchedEffect
        if (!animate) {
            previous = null
            shown = palette
            fade.snapTo(1f)
            return@LaunchedEffect
        }
        previous = shown
        shown = palette
        fade.snapTo(0f)
        fade.animateTo(1f, Motion.emphasized(Motion.DurationAuroraFade))
        previous = null
    }

    Canvas(modifier) {
        val t = clock.timeMs
        val f = fade.value
        val old = previous
        drawRect(if (old == null) shown.base else lerp(old.base, shown.base, f))
        if (old != null) drawBlobs(old, t, 1f - f)
        drawBlobs(shown, t, f)
    }
}

/** Radial falloff of a CSS `filter: blur(58px)` disk: fully opaque inside, soft edge outside. */
private val BlobStops = floatArrayOf(0f, 0.38f, 0.59f, 0.80f, 1f)
private val BlobStrength = floatArrayOf(1f, 0.93f, 0.50f, 0.12f, 0f)

/** The visible radius of a blurred disk is about 1.7 times the disk radius. */
private const val BLOB_REACH = 1.7f

private fun DrawScope.drawBlobs(palette: AuroraPalette, timeMs: Long, fraction: Float) {
    if (fraction <= 0f) return
    val blend = if (palette.screenBlend) BlendMode.Screen else BlendMode.SrcOver
    for (blob in palette.blobs) {
        val frame = AuroraPalettes.frame(blob, timeMs, size.width, size.height)
        val reach = frame.radius * BLOB_REACH
        val alpha = blob.alpha * fraction
        val stops = Array(BlobStops.size) { i -> BlobStops[i] to blob.color.copy(alpha = alpha * BlobStrength[i]) }
        drawCircle(
            brush = Brush.radialGradient(*stops, center = Offset(frame.x, frame.y), radius = reach),
            radius = reach,
            center = Offset(frame.x, frame.y),
            blendMode = blend,
        )
    }
}
