package com.ftwegc.myapplication.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * Whether a list's first-entrance stagger has already played. Saved, so returning from a detail
 * page (or rotating) does not replay it.
 */
@Stable
class EntranceState(played: Boolean) {
    var played: Boolean by mutableStateOf(played)
        private set

    fun markPlayed() {
        played = true
    }

    companion object {
        val Saver: Saver<EntranceState, Boolean> = Saver(save = { it.played }, restore = { EntranceState(it) })

        /** Time until the last staggered item has finished entering. */
        val totalMillis: Long =
            Motion.StaggerStepMillis * Motion.StaggerMaxItems + Motion.DurationItemEnter
    }
}

@Composable
fun rememberEntranceState(): EntranceState = rememberSaveable(saver = EntranceState.Saver) { EntranceState(false) }

/**
 * Marks [state] played once the stagger window has passed. Call it where the list content is
 * first shown.
 */
@Composable
fun EntranceCompletion(state: EntranceState) {
    LaunchedEffect(state) {
        if (!state.played) {
            delay(EntranceState.totalMillis)
            state.markPlayed()
        }
    }
}

/**
 * First-entrance animation of list item [index]: fades in and rises by [Motion.StaggerOffset],
 * [Motion.StaggerStepMillis] after the previous item, at most [Motion.StaggerMaxItems] steps.
 * Items composed after the entrance has played (or with reduced motion) appear immediately.
 * Only graphics-layer properties change, so the list never relayouts.
 */
@Composable
fun Modifier.staggeredEntrance(index: Int, state: EntranceState): Modifier {
    val reduced = LocalReducedMotion.current
    val startHidden = remember { !state.played && !reduced }
    val progress = remember { Animatable(if (startHidden) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (startHidden) {
            delay(Motion.StaggerStepMillis * index.coerceIn(0, Motion.StaggerMaxItems))
            progress.animateTo(1f, Motion.emphasizedDecelerate(Motion.DurationItemEnter))
        }
    }
    LaunchedEffect(reduced) { if (reduced) progress.snapTo(1f) }
    val offsetPx = with(LocalDensity.current) { Motion.StaggerOffset.toPx() }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * offsetPx
    }
}
