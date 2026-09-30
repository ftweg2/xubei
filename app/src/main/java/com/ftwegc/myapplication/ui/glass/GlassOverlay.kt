package com.ftwegc.myapplication.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Glass that floats over whole pages (sheets, menus, the dimmed backdrop behind them) is drawn at
 * the app root, outside the page layer: there it may sample the pages themselves (blurred and,
 * on Android 13+, refracted) without sampling itself. Pages hand such content to the root with
 * [Overlay]; the root draws everything registered here above the pages and the dock.
 */
@Stable
class OverlayHost {
    internal val entries = mutableStateListOf<OverlayEntry>()

    /** Overlays that currently cover the page (a sheet or a menu is open): the dock steps aside. */
    val modalCount: Int get() = entries.count { it.modal }
}

@Stable
internal class OverlayEntry {
    var content: @Composable () -> Unit by mutableStateOf({})
    var modal: Boolean by mutableStateOf(false)
}

/** The root's overlay host; `null` outside the app shell (previews), where overlays draw in place. */
val LocalOverlayHost = staticCompositionLocalOf<OverlayHost?> { null }

/**
 * Draws [content] at the app root (see [OverlayHost]) for as long as this call stays in the
 * composition. [modal]: the overlay covers the page right now (the dock hides meanwhile).
 */
@Composable
fun Overlay(modal: Boolean = false, content: @Composable () -> Unit) {
    val host = LocalOverlayHost.current
    if (host == null) {
        content()
        return
    }
    val entry = remember { OverlayEntry() }
    SideEffect {
        entry.content = content
        entry.modal = modal
    }
    DisposableEffect(host, entry) {
        host.entries += entry
        onDispose { host.entries -= entry }
    }
}

/** Draws every registered overlay, oldest below. Call once, at the root, above the pages. */
@Composable
fun OverlayHostContent(host: OverlayHost) {
    host.entries.forEach { entry ->
        key(entry) { entry.content() }
    }
}
