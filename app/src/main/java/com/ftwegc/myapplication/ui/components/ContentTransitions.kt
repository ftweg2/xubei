package com.ftwegc.myapplication.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion

/**
 * Cross-fade between content, loading, error and empty states ([androidx.compose.animation.AnimatedContent]).
 * Instant with reduced motion.
 */
@Composable
fun <S> crossFadeSpec(): AnimatedContentTransitionScope<S>.() -> ContentTransform {
    val reduced = LocalReducedMotion.current
    return {
        fadeIn(Motion.reduced(reduced, Motion.emphasized())) togetherWith
            fadeOut(Motion.reduced(reduced, Motion.emphasizedAccelerate()))
    }
}

/** A value rolling in from below (new) while the old one leaves upward, e.g. a changed date. */
@Composable
fun <S> rollUpSpec(): AnimatedContentTransitionScope<S>.() -> ContentTransform {
    val reduced = LocalReducedMotion.current
    return {
        (fadeIn(Motion.reduced(reduced, Motion.emphasized())) +
            slideInVertically(Motion.reduced(reduced, Motion.emphasized())) { it / 2 }) togetherWith
            (fadeOut(Motion.reduced(reduced, Motion.emphasizedAccelerate())) +
                slideOutVertically(Motion.reduced(reduced, Motion.emphasizedAccelerate())) { -it / 2 })
    }
}
