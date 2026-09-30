package com.ftwegc.myapplication.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.ftwegc.myapplication.ui.theme.Motion

/*
 * Page transitions.
 *
 * - Forward: the new page slides in from 1/10 of the width on the right and fades in; the old
 *   page drifts 1/10 to the left and fades out. Back is the mirror image. 300 ms, emphasized.
 * - Between bottom tabs: cross-fade with a slight scale.
 * - Reduced motion: no transition at all.
 */

private typealias Scope = AnimatedContentTransitionScope<NavBackStackEntry>

private fun Scope.isTabSwitch(): Boolean =
    TopLevelTab.of(initialState.destination) != null && TopLevelTab.of(targetState.destination) != null

private fun slideOffset(width: Int): Int = (width * Motion.PageSlideFraction).toInt()

private val tabEnter: EnterTransition =
    fadeIn(Motion.emphasizedDecelerate(Motion.DurationMedium, delayMillis = Motion.DurationShort / 2)) +
        scaleIn(Motion.emphasizedDecelerate(Motion.DurationMedium), initialScale = Motion.TabEnterScale)

private val tabExit: ExitTransition =
    fadeOut(Motion.emphasizedAccelerate(Motion.DurationShort)) +
        scaleOut(Motion.emphasizedAccelerate(Motion.DurationShort), targetScale = Motion.TabExitScale)

fun navEnter(reduced: Boolean): Scope.() -> EnterTransition = {
    when {
        reduced -> EnterTransition.None
        isTabSwitch() -> tabEnter
        else -> slideInHorizontally(Motion.emphasized()) { slideOffset(it) } + fadeIn(Motion.emphasized())
    }
}

fun navExit(reduced: Boolean): Scope.() -> ExitTransition = {
    when {
        reduced -> ExitTransition.None
        isTabSwitch() -> tabExit
        else -> slideOutHorizontally(Motion.emphasized()) { -slideOffset(it) } + fadeOut(Motion.emphasized())
    }
}

fun navPopEnter(reduced: Boolean): Scope.() -> EnterTransition = {
    when {
        reduced -> EnterTransition.None
        isTabSwitch() -> tabEnter
        else -> slideInHorizontally(Motion.emphasized()) { -slideOffset(it) } + fadeIn(Motion.emphasized())
    }
}

fun navPopExit(reduced: Boolean): Scope.() -> ExitTransition = {
    when {
        reduced -> ExitTransition.None
        isTabSwitch() -> tabExit
        else -> slideOutHorizontally(Motion.emphasized()) { slideOffset(it) } + fadeOut(Motion.emphasized())
    }
}
