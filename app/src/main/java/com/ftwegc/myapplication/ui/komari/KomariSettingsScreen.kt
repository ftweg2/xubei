package com.ftwegc.myapplication.ui.komari

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyOff
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.data.prefs.ApiKeyStatus
import com.ftwegc.myapplication.data.remote.komari.KomariError
import com.ftwegc.myapplication.data.repo.KomariConnectionInfo
import com.ftwegc.myapplication.ui.AppViewModelProvider
import com.ftwegc.myapplication.ui.background.PageTone
import com.ftwegc.myapplication.ui.brand.CupFill
import com.ftwegc.myapplication.ui.brand.CupMark
import com.ftwegc.myapplication.ui.brand.CupSpinner
import com.ftwegc.myapplication.ui.brand.VluerSpinner
import com.ftwegc.myapplication.ui.common.LocalAppMessenger
import com.ftwegc.myapplication.ui.components.RollingNumber
import com.ftwegc.myapplication.ui.components.crossFadeSpec
import com.ftwegc.myapplication.ui.format.Formatters
import com.ftwegc.myapplication.ui.format.relativeTimeText
import com.ftwegc.myapplication.ui.glass.GlassAccents
import com.ftwegc.myapplication.ui.glass.GlassButton
import com.ftwegc.myapplication.ui.glass.GlassButtonStyle
import com.ftwegc.myapplication.ui.glass.GlassChip
import com.ftwegc.myapplication.ui.glass.GlassDivider
import com.ftwegc.myapplication.ui.glass.GlassGroup
import com.ftwegc.myapplication.ui.glass.GlassIconTile
import com.ftwegc.myapplication.ui.glass.GlassLabel
import com.ftwegc.myapplication.ui.glass.GlassNotice
import com.ftwegc.myapplication.ui.glass.GlassPage
import com.ftwegc.myapplication.ui.glass.GlassPanel
import com.ftwegc.myapplication.ui.glass.GlassPillButton
import com.ftwegc.myapplication.ui.glass.GlassPlainIconButton
import com.ftwegc.myapplication.ui.glass.GlassRow
import com.ftwegc.myapplication.ui.glass.GlassTextField
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.preview.GlassPreview
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion
import com.ftwegc.myapplication.ui.theme.motionSpec
import java.time.Instant
import java.time.ZoneId

@Composable
fun KomariSettingsDestination(
    onBack: () -> Unit,
    onOpenImport: () -> Unit,
    viewModel: KomariSettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalAppMessenger.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel, messenger) {
        viewModel.events.collect { event ->
            when (event) {
                is KomariSettingsEvent.Saved -> messenger?.showSuccess(
                    resources.getString(if (event.removed) R.string.komari_removed else R.string.komari_saved),
                )
                is KomariSettingsEvent.SaveFailed -> messenger?.showInfo(
                    if (event.error == KomariError.Parse) resources.getString(R.string.komari_error_encrypt)
                    else komariErrorMessage(resources, event.error),
                )
                is KomariSettingsEvent.Synced -> messenger?.showSuccess(
                    if (event.summary.missing > 0) resources.getString(R.string.komari_sync_missing, event.summary.missing)
                    else resources.getString(R.string.komari_sync_done, event.summary.updated, event.summary.unchanged),
                )
                is KomariSettingsEvent.SyncFailed -> messenger?.showInfo(
                    resources.getString(R.string.komari_sync_failed, komariErrorMessage(resources, event.error)),
                )
            }
        }
    }
    KomariSettingsScreen(
        state = state,
        onBack = onBack,
        onUrlChange = viewModel::setUrl,
        onKeyChange = viewModel::setKeyText,
        onReplaceKey = viewModel::startReplaceKey,
        onCancelReplaceKey = viewModel::cancelReplaceKey,
        onClearKey = viewModel::clearKey,
        onUndoClearKey = viewModel::undoClearKey,
        onTest = viewModel::testConnection,
        onSave = viewModel::save,
        onOpenImport = onOpenImport,
        onSync = viewModel::sync,
    )
}

/** Komari brand color of the page's icon tiles (VPS blue). */
private val PanelTint = Color(0xFF3B82F6)

/**
 * The Komari panel page in glass: address, API key (saved keys are never shown again), the
 * cleartext warning, the connection test (idle, connecting, the cup filling up on success, a
 * shaking mark on failure) and, once saved, node import and sync. Save sits at the bottom.
 */
@Composable
fun KomariSettingsScreen(
    state: KomariSettingsUiState,
    onBack: () -> Unit,
    onUrlChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onReplaceKey: () -> Unit,
    onCancelReplaceKey: () -> Unit,
    onClearKey: () -> Unit,
    onUndoClearKey: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onOpenImport: () -> Unit,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
    now: Instant = com.ftwegc.myapplication.ui.common.appNow(),
) {
    GlassPage(
        title = stringResource(R.string.komari_settings_title),
        subtitle = stringResource(R.string.komari_settings_intro),
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            GlassButton(
                text = stringResource(if (state.isSaving) R.string.komari_saving else R.string.komari_save),
                onClick = onSave,
                enabled = !state.isLoading && !state.isSaving && state.isDirty,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        UrlBlock(state, onUrlChange, onTest)
        KeyBlock(state, onKeyChange, onReplaceKey, onCancelReplaceKey, onClearKey, onUndoClearKey)
        AnimatedVisibility(
            visible = state.showCleartextWarning,
            enter = expandVertically(motionSpec(Motion.emphasized())) + fadeIn(motionSpec(Motion.emphasized())),
            exit = shrinkVertically(motionSpec(Motion.emphasizedAccelerate())) + fadeOut(motionSpec(Motion.emphasizedAccelerate())),
        ) {
            GlassPanel(Modifier.fillMaxWidth(), tint = Color(0xFFFBBF24)) {
                GlassNotice(
                    icon = Icons.Rounded.WarningAmber,
                    title = stringResource(R.string.komari_cleartext_title),
                    text = stringResource(R.string.komari_cleartext_warning),
                    accent = GlassAccents.amber(currentGlass().dark),
                )
            }
        }
        ConnectionCard(state.test, onTest, enabled = state.url.isNotBlank() && !state.isLoading)
        AnimatedVisibility(
            visible = state.isConfigured,
            enter = expandVertically(motionSpec(Motion.emphasized())) + fadeIn(motionSpec(Motion.emphasized())),
            exit = shrinkVertically(motionSpec(Motion.emphasizedAccelerate())) + fadeOut(motionSpec(Motion.emphasizedAccelerate())),
        ) {
            NodesGroup(state, now, onOpenImport, onSync)
        }
    }
}

@Composable
private fun UrlBlock(state: KomariSettingsUiState, onUrlChange: (String) -> Unit, onTest: () -> Unit) {
    val focus = LocalFocusManager.current
    GlassPanel(Modifier.fillMaxWidth()) {
        GlassLabel(stringResource(R.string.komari_url_section))
        GlassTextField(
            value = state.url,
            onValueChange = onUrlChange,
            label = stringResource(R.string.komari_url_label),
            placeholder = stringResource(R.string.komari_url_placeholder),
            error = if (state.urlInvalid) stringResource(R.string.komari_error_invalid_url) else null,
            supporting = stringResource(R.string.komari_url_hint),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = {
                focus.clearFocus()
                onTest()
            }),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

private enum class KeyMode { Saved, ClearPending, Editing }

@Composable
private fun KeyBlock(
    state: KomariSettingsUiState,
    onKeyChange: (String) -> Unit,
    onReplaceKey: () -> Unit,
    onCancelReplaceKey: () -> Unit,
    onClearKey: () -> Unit,
    onUndoClearKey: () -> Unit,
) {
    val env = currentGlass()
    GlassPanel(Modifier.fillMaxWidth()) {
        GlassLabel(stringResource(R.string.komari_key_section))
        AnimatedVisibility(
            visible = state.keyStatus == ApiKeyStatus.UNREADABLE && state.editingKey,
            enter = expandVertically(motionSpec(Motion.emphasized())) + fadeIn(motionSpec(Motion.emphasized())),
            exit = shrinkVertically(motionSpec(Motion.emphasizedAccelerate())) + fadeOut(motionSpec(Motion.emphasizedAccelerate())),
        ) {
            GlassNotice(
                icon = Icons.Rounded.WarningAmber,
                text = stringResource(R.string.komari_key_unreadable),
                accent = GlassAccents.amber(env.dark),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        val mode = when {
            state.clearKeyPending -> KeyMode.ClearPending
            state.editingKey -> KeyMode.Editing
            else -> KeyMode.Saved
        }
        AnimatedContent(targetState = mode, transitionSpec = crossFadeSpec(), label = "keyMode", modifier = Modifier.padding(top = 10.dp)) { m ->
            when (m) {
                KeyMode.Saved -> SavedKeyRow(onReplaceKey, onClearKey)
                KeyMode.ClearPending -> PendingClearRow(onUndoClearKey)
                KeyMode.Editing -> KeyEditor(
                    value = state.keyText,
                    onValueChange = onKeyChange,
                    canCancel = state.keyStatus == ApiKeyStatus.SAVED,
                    onCancel = onCancelReplaceKey,
                )
            }
        }
    }
}

/** A saved key is never shown again: "已保存", with replace and clear. */
@Composable
private fun SavedKeyRow(onReplace: () -> Unit, onClear: () -> Unit) {
    val env = currentGlass()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconTile(Icons.Rounded.Key, Color(0xFF22C55E))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.komari_key_saved), color = GlassAccents.mint(env.dark), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.komari_key_saved_desc), color = env.ink.secondary, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        Row(Modifier.padding(top = 12.dp).align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassPillButton(stringResource(R.string.komari_key_replace), onReplace)
            GlassChip(stringResource(R.string.komari_key_clear), color = GlassAccents.coral(env.dark), onClick = onClear)
        }
    }
}

@Composable
private fun PendingClearRow(onUndo: () -> Unit) {
    val env = currentGlass()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        GlassIconTile(Icons.Rounded.KeyOff, Color(0xFF64748B))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.komari_key_cleared_pending), color = env.ink.secondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        GlassPillButton(stringResource(R.string.komari_key_undo_clear), onUndo)
    }
}

@Composable
private fun KeyEditor(value: String, onValueChange: (String) -> Unit, canCancel: Boolean, onCancel: () -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    Column {
        GlassTextField(
            value = value,
            onValueChange = onValueChange,
            label = stringResource(R.string.komari_key_label),
            supporting = stringResource(R.string.komari_key_hint),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = {
                GlassPlainIconButton(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    stringResource(if (visible) R.string.komari_key_hide else R.string.komari_key_show),
                    { visible = !visible },
                )
            },
        )
        if (canCancel) {
            GlassPillButton(stringResource(R.string.komari_key_cancel_replace), onCancel, Modifier.padding(top = 10.dp).align(Alignment.End))
        }
    }
}

// ------------------------------------------------------------------ Connection

private enum class TestKind { Idle, Testing, Success, Failure }

private fun ConnectionTest.kind(): TestKind = when (this) {
    ConnectionTest.Idle -> TestKind.Idle
    ConnectionTest.Testing -> TestKind.Testing
    is ConnectionTest.Success -> TestKind.Success
    is ConnectionTest.Failure -> TestKind.Failure
}

/**
 * The connection test with animated steps: idle → the cup's ring turning while connecting →
 * the ring drawing itself round with a check (version, node count counting up, guest or key),
 * or a shaking coral mark with the reason.
 */
@Composable
fun ConnectionCard(test: ConnectionTest, onTest: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    GlassPanel(modifier.fillMaxWidth()) {
        GlassLabel(stringResource(R.string.komari_test_section))
        AnimatedContent(
            targetState = test,
            contentKey = { it.kind() },
            transitionSpec = {
                val enter = fadeIn(Motion.reduced(reduced, Motion.emphasizedDecelerate())) +
                    scaleIn(Motion.reduced(reduced, Motion.emphasizedDecelerate()), initialScale = 0.94f)
                val exit = fadeOut(Motion.reduced(reduced, Motion.emphasizedAccelerate()))
                (enter togetherWith exit).using(SizeTransform(clip = false) { _, _ -> Motion.reduced(reduced, Motion.emphasized()) })
            },
            label = "connectionState",
            modifier = Modifier
                .padding(top = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) { current ->
            when (current) {
                ConnectionTest.Idle -> StatusRow(
                    leading = { GlassIconTile(Icons.Rounded.WifiTethering, Color(0xFF64748B), size = 44.dp) },
                    title = null,
                    body = stringResource(R.string.komari_test_idle),
                )
                ConnectionTest.Testing -> StatusRow(
                    leading = { CupSpinner(size = 48.dp, level = CupMark.NormalLevel) },
                    title = stringResource(R.string.komari_test_running),
                    body = null,
                )
                is ConnectionTest.Success -> SuccessBody(current.info, current.withKey)
                is ConnectionTest.Failure -> FailureBody(current.error)
            }
        }
        GlassButton(
            text = stringResource(R.string.komari_test_action),
            onClick = onTest,
            style = GlassButtonStyle.Secondary,
            icon = Icons.Rounded.WifiTethering,
            enabled = enabled && test != ConnectionTest.Testing,
            height = 46.dp,
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun StatusRow(leading: @Composable () -> Unit, title: String?, body: String?) {
    val env = currentGlass()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (title != null) Text(title, color = env.ink.primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (body != null) Text(body, color = env.ink.secondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuccessBody(info: KomariConnectionInfo, withKey: Boolean) {
    val env = currentGlass()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            CupFill(size = 52.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.komari_test_success), color = GlassAccents.mint(env.dark), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GlassChip(
                    if (info.version.isNotBlank()) stringResource(R.string.komari_test_version, info.version)
                    else stringResource(R.string.komari_test_version_unknown),
                )
                NodeCountChip(info.nodeCount)
                GlassChip(stringResource(if (withKey) R.string.komari_test_with_key else R.string.komari_test_guest))
            }
        }
    }
}

/** "7 个节点" with the number counting up. */
@Composable
private fun NodeCountChip(count: Int) {
    val env = currentGlass()
    com.ftwegc.myapplication.ui.glass.GlassSurface(
        shape = com.ftwegc.myapplication.ui.glass.GlassDefaults.CapsuleShape,
        thickness = com.ftwegc.myapplication.ui.glass.GlassThickness.Thin,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            RollingNumber(
                value = count.toLong(),
                format = { it.toString() },
                style = TextStyle(color = env.ink.primary, fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                countFromZero = true,
            )
            Text(" " + stringResource(R.string.komari_test_nodes), color = env.ink.primary, fontSize = 11.5.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun FailureBody(error: KomariError) {
    val env = currentGlass()
    val reduced = LocalReducedMotion.current
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (!reduced) {
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = Motion.DurationMedium + Motion.DurationShort
                    0f at 0
                    -10f at 60
                    9f at 130
                    -6f at 200
                    4f at 270
                    -2f at 340
                    0f at 450
                },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .size(52.dp)
                .graphicsLayer { translationX = shake.value.dp.toPx() },
            contentAlignment = Alignment.Center,
        ) {
            GlassIconTile(Icons.Rounded.ErrorOutline, Color(0xFFF43F5E), size = 44.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.komari_test_failed), color = GlassAccents.coral(env.dark), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(komariErrorText(error), color = env.ink.secondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// ------------------------------------------------------------------ Nodes

@Composable
private fun NodesGroup(state: KomariSettingsUiState, now: Instant, onOpenImport: () -> Unit, onSync: () -> Unit) {
    GlassGroup(stringResource(R.string.komari_nodes_section)) {
        GlassRow(
            title = stringResource(R.string.komari_import_entry),
            subtitle = stringResource(R.string.komari_import_entry_desc),
            icon = Icons.Rounded.CloudDownload,
            iconTint = PanelTint,
            onClick = onOpenImport,
        )
        GlassDivider()
        val last = state.lastSyncAt
        GlassRow(
            title = stringResource(if (state.isSyncing) R.string.komari_syncing else R.string.komari_sync_entry),
            subtitle = if (last == null) {
                stringResource(R.string.komari_sync_never)
            } else {
                stringResource(R.string.komari_sync_last, relativeTimeText(Formatters.relativeTime(Instant.ofEpochMilli(last), now, ZoneId.systemDefault())))
            },
            icon = Icons.Rounded.Sync,
            iconTint = Color(0xFF14B8A6),
            onClick = if (state.isSyncing) null else onSync,
            chevron = false,
            trailing = if (state.isSyncing) ({ VluerSpinner(size = 22.dp) }) else null,
        )
    }
}

// ------------------------------------------------------------------ Previews

@Composable
private fun PreviewScreen(state: KomariSettingsUiState) {
    GlassPreview(padding = 0.dp, tone = PageTone.Settings) {
        KomariSettingsScreen(
            state = state,
            onBack = {}, onUrlChange = {}, onKeyChange = {}, onReplaceKey = {}, onCancelReplaceKey = {},
            onClearKey = {}, onUndoClearKey = {}, onTest = {}, onSave = {}, onOpenImport = {}, onSync = {},
            now = Instant.parse("2026-09-28T08:00:00Z"),
        )
    }
}

@PreviewLightDark
@Composable
private fun KomariSettingsEmptyPreview() {
    PreviewScreen(KomariSettingsUiState(isLoading = false))
}

@PreviewLightDark
@Composable
private fun KomariSettingsSuccessPreview() {
    PreviewScreen(
        KomariSettingsUiState(
            isLoading = false,
            url = "https://status.example.com",
            savedUrl = "https://status.example.com",
            keyStatus = ApiKeyStatus.SAVED,
            editingKey = false,
            test = ConnectionTest.Success(KomariConnectionInfo("1.5.1", 4, 0), withKey = true),
            lastSyncAt = Instant.parse("2026-09-28T07:40:00Z").toEpochMilli(),
        ),
    )
}

@PreviewLightDark
@Composable
private fun KomariSettingsErrorPreview() {
    PreviewScreen(
        KomariSettingsUiState(
            isLoading = false,
            url = "http://10.0.2.2:25774",
            keyText = "preview-key",
            editingKey = true,
            test = ConnectionTest.Failure(KomariError.Unauthorized(keySent = true)),
        ),
    )
}
