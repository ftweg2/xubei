package com.ftwegc.myapplication.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.ui.brand.CupInk
import com.ftwegc.myapplication.ui.brand.CupMark
import com.ftwegc.myapplication.ui.brand.CupPaths
import com.ftwegc.myapplication.ui.brand.drawCupMark
import com.ftwegc.myapplication.ui.brand.sloshTilt
import com.ftwegc.myapplication.ui.theme.Motion
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

/**
 * The splash of the 续杯 mark, played once when the app starts (not with animations off).
 *
 * The system splash (res/drawable/splash_mark_*.xml, and the launch window before Android 12)
 * shows the first frame: the empty cup inside its base ring, on the splash color. This overlay
 * draws the same frame at the same place over the app, then the ring draws itself clockwise, the
 * liquid rises from the bottom of the cup, sloshes two or three times and rests, and the splash
 * fades into the overview while the mark grows a little. Touches are held back while it plays.
 */
@Composable
fun SplashOverlay(dark: Boolean, revealed: StateFlow<Boolean>, onFinished: () -> Unit) {
    val ring = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    val slosh = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        // The first frames are composed before they reach the screen (the system splash stays
        // until the first one is drawn, and the first frames of a cold start are slow): the
        // animation starts once this frame is really visible, so it is never half over when the
        // splash gives way. Without a sign (no system splash was shown) it starts after a while.
        withTimeoutOrNull(SPLASH_REVEAL_TIMEOUT_MILLIS) { revealed.first { it } }
        repeat(SETTLE_FRAMES) { withFrameNanos { } }
        coroutineScope {
            launch { ring.animateTo(1f, tween(Motion.SplashRingMillis, easing = Motion.EmphasizedDecelerate)) }
            launch { rise.animateTo(1f, tween(Motion.SplashRiseMillis, Motion.SplashRiseDelayMillis, Motion.Emphasized)) }
            launch { slosh.animateTo(1f, tween(Motion.SplashSloshMillis, Motion.SplashSloshDelayMillis, LinearEasing)) }
            launch { exit.animateTo(1f, tween(Motion.DurationSplashExit, Motion.SplashExitDelayMillis, Motion.Emphasized)) }
        }
        finished()
    }
    val background = colorResource(if (dark) R.color.splash_background_dark else R.color.splash_background_light)
    val ink = CupInk.of(dark)
    val paths = remember { CupPaths() }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(background)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(SplashMarkSize)
                .graphicsLayer {
                    val s = 1f + (Motion.SplashExitIconScale - 1f) * exit.value
                    scaleX = s
                    scaleY = s
                },
        ) {
            val t = slosh.value
            drawCupMark(
                ink,
                level = CupMark.NormalLevel * rise.value,
                ringShare = CupMark.RingShare * ring.value,
                tilt = sloshTilt(t, Motion.SplashSloshTilt, Motion.SplashSloshCycles),
                phase = t * Motion.SplashSloshDrift,
                paths = paths,
            )
        }
    }
}

/**
 * The mark's 108-unit canvas on screen. The system splash draws an icon without a background in a
 * 288 dp box and splash_mark_*.xml scales the mark to 0.6 of it: 172.8 dp. Before Android 12 the
 * launch window draws the same drawable in a 288 dp box (splash_legacy.xml).
 */
val SplashMarkSize: Dp = 172.8.dp

/** Longest wait for the system splash to give way before the animation starts anyway. */
private const val SPLASH_REVEAL_TIMEOUT_MILLIS = 2_500L

/** Frames to let pass after the splash gave way (the first ones after a cold start are slow). */
private const val SETTLE_FRAMES = 2
