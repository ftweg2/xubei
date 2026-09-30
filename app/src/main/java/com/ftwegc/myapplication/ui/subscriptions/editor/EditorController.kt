package com.ftwegc.myapplication.ui.subscriptions.editor

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.SavedStateHandle
import com.ftwegc.myapplication.ui.glass.GrowOrigin

/**
 * One opening of the add / edit sheet.
 *
 * @property id the subscription to edit, [NEW_ID] for a new one.
 * @property handle the sheet's saved state (form, pending link, unlocked fields); kept with the
 *   activity's saved state so the form comes back after the process was killed.
 * @property suspendedAt the sheet stepped aside for another page (the Komari settings) and comes
 *   back when the navigation entry with this id is shown again; `null` while it is shown.
 */
@Stable
class EditorSession(val id: Long, val handle: SavedStateHandle, val token: Long) {
    var origin: GrowOrigin? by mutableStateOf(null)
    var visible: Boolean by mutableStateOf(true)
    var suspendedAt: String? by mutableStateOf(null)

    companion object {
        const val NEW_ID: Long = 0L
    }
}

/**
 * Opens the add / edit sheet from anywhere (the add button, the detail page, a long-press menu,
 * the Komari import). Lives at the app root; see `SubscriptionEditorHost`.
 */
@Stable
class EditorController(initial: EditorSession? = null) {
    var session: EditorSession? by mutableStateOf(initial)
        private set

    private var nextToken = (initial?.token ?: 0L) + 1

    /**
     * Opens the sheet for [id] ([EditorSession.NEW_ID] adds one), growing out of [origin].
     *
     * The sheet is not a navigation destination, so its state handle is made here and saved by
     * [Saver] with the activity's state instead of a saved-state registry (hence the lint
     * suppression for the handle's constructor).
     */
    @SuppressLint("VisibleForTests")
    fun open(id: Long, origin: GrowOrigin? = null) {
        val current = session
        if (current != null && current.visible && current.suspendedAt == null) return
        session = EditorSession(id, SavedStateHandle(), nextToken++).also { it.origin = origin }
    }

    /** Starts the closing animation; [clear] follows when it has finished. */
    fun close() {
        session?.visible = false
    }

    internal fun clear(session: EditorSession) {
        if (this.session === session) this.session = null
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_TOKEN = "token"
        private const val KEY_SUSPENDED = "suspended"
        private const val KEY_STATE = "state"

        /** Keeps an open sheet (and its form) across process death. */
        val Saver: Saver<EditorController, Bundle> = Saver(
            save = { controller ->
                val s = controller.session?.takeIf { it.visible }
                Bundle().apply {
                    if (s != null) {
                        putLong(KEY_ID, s.id)
                        putLong(KEY_TOKEN, s.token)
                        putString(KEY_SUSPENDED, s.suspendedAt)
                        val state = Bundle()
                        s.handle.keys().forEach { key ->
                            when (val v = s.handle.get<Any?>(key)) {
                                is String -> state.putString(key, v)
                                is Int -> state.putInt(key, v)
                                is Boolean -> state.putBoolean(key, v)
                                is Long -> state.putLong(key, v)
                            }
                        }
                        putBundle(KEY_STATE, state)
                    }
                }
            },
            restore = @SuppressLint("VisibleForTests") { bundle ->
                val state = bundle.getBundle(KEY_STATE)
                if (state == null || !bundle.containsKey(KEY_ID)) {
                    EditorController()
                } else {
                    @Suppress("DEPRECATION")
                    val map = state.keySet().associateWith { state.get(it) }
                    val session = EditorSession(bundle.getLong(KEY_ID), SavedStateHandle(map), bundle.getLong(KEY_TOKEN))
                    session.suspendedAt = bundle.getString(KEY_SUSPENDED)
                    EditorController(session)
                }
            },
        )
    }
}

/** The app's editor, `null` outside the app shell (previews). */
val LocalEditor = staticCompositionLocalOf<EditorController?> { null }
