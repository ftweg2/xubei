package com.ftwegc.myapplication.ui.subscriptions.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.runtime.Composable
import com.ftwegc.myapplication.ui.glass.GlassConfirmDialog
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.VluerApp
import com.ftwegc.myapplication.ui.brand.BrandCatalog
import com.ftwegc.myapplication.ui.brand.IconImages
import com.ftwegc.myapplication.ui.common.LocalAppMessenger
import com.ftwegc.myapplication.ui.common.LocalNotificationPrompter
import com.ftwegc.myapplication.ui.glass.GlassSheet
import com.ftwegc.myapplication.ui.subscriptions.EditEvent
import com.ftwegc.myapplication.ui.subscriptions.GlassActionButton
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionEditViewModel
import com.ftwegc.myapplication.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The add / edit sheet at the app root (mockup, fifth screen: "厚玻璃弹层，从加号按钮生长出来").
 *
 * Each opening gets its own [SubscriptionEditViewModel], whose saved state lives in the
 * [EditorSession] (and with it in the activity's saved state), so the form survives the process
 * being killed. Closing with unsaved changes asks first. The sheet steps aside while the Komari
 * settings are open ([onOpenKomari]) and comes back on return ([currentEntryId]).
 */
@Composable
fun SubscriptionEditorHost(
    controller: EditorController,
    currentEntryId: String?,
    onOpenKomari: () -> Unit,
) {
    val session = controller.session ?: return
    key(session.token) {
        EditorSessionContent(controller, session, currentEntryId, onOpenKomari)
    }
}

@Composable
private fun EditorSessionContent(
    controller: EditorController,
    session: EditorSession,
    currentEntryId: String?,
    onOpenKomari: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as VluerApp
    val store = remember { ViewModelStore() }
    DisposableEffect(store) { onDispose { store.clear() } }
    val viewModel = remember {
        val factory = viewModelFactory {
            initializer {
                val c = app.container
                SubscriptionEditViewModel(
                    id = session.id,
                    repository = c.subscriptionRepository,
                    settingsStore = c.settingsStore,
                    komariRepository = c.komariRepository,
                    savedState = session.handle,
                    trafficRepository = c.trafficRepository,
                    appScope = app.applicationScope,
                )
            }
        }
        ViewModelProvider.create(store, factory)[SubscriptionEditViewModel::class]
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalAppMessenger.current
    val prompter = LocalNotificationPrompter.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    /** Pictures picked in this sheet; the ones not saved are removed when it closes. */
    val picked = remember { mutableStateListOf<String>() }
    var saved by remember { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel, messenger) {
        viewModel.events.collect { event ->
            when (event) {
                is EditEvent.Saved -> {
                    saved = true
                    messenger?.showSuccess(resources.getString(if (event.isNew) R.string.edit_added else R.string.edit_saved, event.name))
                    val unused = picked.filter { it != event.iconKey } +
                        listOfNotNull(viewModel.baseIconKey?.takeIf { it != event.iconKey && BrandCatalog.imageName(it) != null })
                    if (unused.isNotEmpty()) scope.launch { IconImages.delete(context, unused) }
                    if (event.hasDueDate) prompter?.maybePrompt()
                    controller.close()
                }
                EditEvent.LinkTaken -> messenger?.showInfo(resources.getString(R.string.edit_link_taken))
            }
        }
    }
    // Back from the Komari settings: the sheet returns.
    LaunchedEffect(currentEntryId, session.suspendedAt) {
        if (session.suspendedAt != null && currentEntryId == session.suspendedAt) session.suspendedAt = null
    }
    // Closed: let the sheet shrink away, then drop the session (and unsaved pictures).
    LaunchedEffect(session.visible) {
        if (!session.visible) {
            delay(Motion.DurationSheetClose.toLong() + 60)
            if (!saved && picked.isNotEmpty()) IconImages.delete(context, picked.toList())
            controller.clear(session)
        }
    }

    val requestClose = { if (state.isDirty) confirmDiscard = true else controller.close() }
    GlassSheet(
        visible = session.visible && session.suspendedAt == null,
        onDismissRequest = requestClose,
        origin = session.origin,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        contentDescription = stringResource(if (state.isNew) R.string.edit_title_new else R.string.edit_title_edit),
        bottomBar = {
            GlassActionButton(
                text = stringResource(R.string.edit_save),
                onClick = viewModel::save,
                enabled = !state.isSaving && !state.isLoading && !state.notFound,
            )
        },
    ) {
        EditorForm(
            state = state,
            viewModel = viewModel,
            onClose = requestClose,
            onOpenKomari = {
                session.suspendedAt = currentEntryId
                onOpenKomari()
            },
            onPicked = { picked += it },
        )
    }

    EditorNodePicker(state = state, viewModel = viewModel, visible = session.visible && session.suspendedAt == null)

    GlassConfirmDialog(
        visible = confirmDiscard,
        onDismissRequest = { confirmDiscard = false },
        icon = Icons.Rounded.EditNote,
        title = stringResource(R.string.edit_discard_title),
        message = stringResource(R.string.edit_discard_message),
        confirmLabel = stringResource(R.string.edit_discard_confirm),
        onConfirm = {
            confirmDiscard = false
            controller.close()
        },
        dismissLabel = stringResource(R.string.edit_discard_keep),
        destructive = true,
    )
}
