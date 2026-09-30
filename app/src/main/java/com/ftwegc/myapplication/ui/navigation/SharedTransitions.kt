package com.ftwegc.myapplication.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion

/** The app-wide [SharedTransitionScope] (null in previews). */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The navigation destination's [AnimatedVisibilityScope] (null in previews). */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Provides [scope] as the current destination's animated visibility scope. */
@Composable
fun ProvideNavAnimatedScope(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides scope, content = content)
}

/**
 * Keys of elements that morph from a card into the detail header. [origin] tells which screen
 * the card lives on, so the same subscription on two tabs never matches during a tab switch.
 */
object SharedKeys {
    const val ORIGIN_HOME: String = "home"
    const val ORIGIN_LIST: String = "list"

    /** Home traffic rows: no card morph (the row has no matching header parts). */
    const val ORIGIN_TRAFFIC: String = "traffic"

    /** The statistics page: no card morph (marks in charts). */
    const val ORIGIN_STATS: String = "stats"

    /** Opened from a notification: nothing to morph from. */
    const val ORIGIN_NOTIFICATION: String = "notification"

    fun card(origin: String, id: Long): String = "$origin-card-$id"
    fun icon(origin: String, id: Long): String = "$origin-icon-$id"
    fun name(origin: String, id: Long): String = "$origin-name-$id"
    fun amount(origin: String, id: Long): String = "$origin-amount-$id"
}

/** Emphasized tween used for every shared bounds morph. */
private val SharedBoundsTransform = BoundsTransform { _, _ ->
    tween(Motion.DurationSharedElement, easing = Motion.Emphasized)
}

/**
 * Shared element (same content on both sides, for example an icon). No-op in previews, outside a
 * navigation destination, or when system animations are off.
 */
@Composable
fun Modifier.sharedElementOrSelf(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return this
    if (LocalReducedMotion.current) return this
    return with(shared) {
        this@sharedElementOrSelf.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
            boundsTransform = SharedBoundsTransform,
        )
    }
}

/**
 * Shared bounds whose content differs on each side (text at another size, a card turning into a
 * header). Text scales to the animated bounds; containers remeasure.
 */
@Composable
fun Modifier.sharedBoundsOrSelf(key: String, scaleContent: Boolean = true): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return this
    if (LocalReducedMotion.current) return this
    return with(shared) {
        this@sharedBoundsOrSelf.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
            enter = fadeIn(Motion.emphasized(Motion.DurationSharedElement)),
            exit = fadeOut(Motion.emphasized(Motion.DurationSharedElement)),
            boundsTransform = SharedBoundsTransform,
            resizeMode = if (scaleContent) {
                SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillHeight, Alignment.CenterStart)
            } else {
                SharedTransitionScope.ResizeMode.RemeasureToBounds
            },
        )
    }
}
