package com.ftwegc.myapplication.ui.settings

import android.content.pm.ApplicationInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.data.backup.BackupCodec
import com.ftwegc.myapplication.data.backup.ImportPlan
import com.ftwegc.myapplication.data.prefs.AppSettings
import com.ftwegc.myapplication.data.prefs.ThemeMode
import com.ftwegc.myapplication.domain.model.ReminderDays
import com.ftwegc.myapplication.domain.model.VpsConfig
import com.ftwegc.myapplication.ui.AppViewModelProvider
import com.ftwegc.myapplication.ui.background.PageTone
import com.ftwegc.myapplication.ui.brand.VluerSpinner
import com.ftwegc.myapplication.ui.common.LocalAppMessenger
import com.ftwegc.myapplication.ui.common.NotificationStatus
import com.ftwegc.myapplication.ui.common.openBatteryOptimizationSettings
import com.ftwegc.myapplication.ui.common.rememberNotificationPermission
import com.ftwegc.myapplication.ui.format.Formatters
import com.ftwegc.myapplication.ui.format.refreshIntervalText
import com.ftwegc.myapplication.ui.format.relativeTimeText
import com.ftwegc.myapplication.ui.format.reminderDayLabel
import com.ftwegc.myapplication.ui.format.reminderDaysText
import com.ftwegc.myapplication.ui.glass.GlassAccents
import com.ftwegc.myapplication.ui.glass.GlassButton
import com.ftwegc.myapplication.ui.glass.GlassChip
import com.ftwegc.myapplication.ui.glass.GlassChoiceSheet
import com.ftwegc.myapplication.ui.glass.GlassConfirmDialog
import com.ftwegc.myapplication.ui.glass.GlassDivider
import com.ftwegc.myapplication.ui.glass.GlassGroup
import com.ftwegc.myapplication.ui.glass.GlassIconTile
import com.ftwegc.myapplication.ui.glass.GlassListDefaults
import com.ftwegc.myapplication.ui.glass.GlassPillButton
import com.ftwegc.myapplication.ui.glass.GlassRow
import com.ftwegc.myapplication.ui.glass.GlassSegmented
import com.ftwegc.myapplication.ui.glass.GlassSheet
import com.ftwegc.myapplication.ui.glass.GlassSlider
import com.ftwegc.myapplication.ui.glass.GlassSwitch
import com.ftwegc.myapplication.ui.glass.PageTitle
import com.ftwegc.myapplication.ui.glass.StatusBarScrim
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.glass.rememberLatest
import com.ftwegc.myapplication.ui.brand.VluerLogo
import com.ftwegc.myapplication.ui.komari.komariErrorMessage
import com.ftwegc.myapplication.ui.navigation.LocalBottomBarPadding
import com.ftwegc.myapplication.ui.preview.GlassPreview
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as ZoneTextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsDestination(
    onOpenKomari: () -> Unit = {},
    onOpenDebugGallery: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalAppMessenger.current
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel, messenger) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Synced -> messenger?.showSuccess(
                    if (event.summary.missing > 0) resources.getString(R.string.komari_sync_missing, event.summary.missing)
                    else resources.getString(R.string.komari_sync_done, event.summary.updated, event.summary.unchanged),
                )
                is SettingsEvent.SyncFailed -> messenger?.showInfo(
                    resources.getString(R.string.komari_sync_failed, komariErrorMessage(resources, event.error)),
                )
                is SettingsEvent.Exported -> messenger?.showSuccess(resources.getString(R.string.backup_exported, event.count))
                SettingsEvent.ExportFailed -> messenger?.showInfo(resources.getString(R.string.backup_export_failed))
                is SettingsEvent.Imported -> messenger?.showSuccess(
                    if (event.outcome.linksDropped > 0) {
                        resources.getString(R.string.backup_imported_links_dropped, event.outcome.imported, event.outcome.linksDropped)
                    } else {
                        resources.getString(R.string.backup_imported, event.outcome.imported)
                    },
                )
                is SettingsEvent.ImportFailed -> messenger?.showInfo(
                    resources.getString(
                        when (event.reason) {
                            ImportFailure.UNREADABLE -> R.string.backup_import_unreadable
                            ImportFailure.NOT_BACKUP -> R.string.backup_import_not_backup
                            ImportFailure.NEWER_VERSION -> R.string.backup_import_newer
                            ImportFailure.TOO_LARGE -> R.string.backup_import_too_large
                        },
                    ),
                )
                SettingsEvent.DebugTriggered -> messenger?.showInfo(resources.getString(R.string.debug_triggered))
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            viewModel.export { bytes ->
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("no output stream")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.prepareImport {
                val stream = context.contentResolver.openInputStream(uri) ?: error("no input stream")
                stream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        if (out.size() > SettingsViewModel.MAX_IMPORT_BYTES) throw FileTooLargeException()
                    }
                    out.toString(Charsets.UTF_8.name())
                }
            }
        }
    }
    val isDebuggable = remember { context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
    SettingsScreen(
        state = state,
        onShowArchivedChange = viewModel::setShowArchived,
        onDefaultCurrencyChange = viewModel::setDefaultCurrency,
        onOpenKomari = onOpenKomari,
        onSync = viewModel::sync,
        actions = SettingsActions(
            onRefreshInterval = viewModel::setRefreshInterval,
            onReminderDays = viewModel::setReminderDays,
            onTrafficAlert = viewModel::setTrafficAlertPercent,
            onResetZone = viewModel::setResetZone,
            onNotificationAsked = viewModel::setNotificationPermissionRequested,
            onExport = { exportLauncher.launch(BackupCodec.fileName(LocalDate.now())) },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onConfirmImport = viewModel::confirmImport,
            onCancelImport = viewModel::cancelImport,
            onThemeMode = viewModel::setThemeMode,
            onReduceTransparency = viewModel::setReduceTransparency,
            onBackgroundAnimation = viewModel::setBackgroundAnimation,
            onOpenAbout = onOpenAbout,
            debug = if (isDebuggable) DebugActions(viewModel::debugRunReminders, viewModel::debugRunRefresh, onOpenDebugGallery) else null,
        ),
    )
}

/** Debug-build-only triggers for the background workers, and the glass / chart / icon gallery. */
class DebugActions(val onRunReminders: () -> Unit, val onRunRefresh: () -> Unit, val onOpenGallery: () -> Unit = {})

/** Callbacks of the settings rows. */
class SettingsActions(
    val onRefreshInterval: (Int) -> Unit = {},
    val onReminderDays: (List<Int>) -> Unit = {},
    val onTrafficAlert: (Int) -> Unit = {},
    val onResetZone: (String?) -> Unit = {},
    val onNotificationAsked: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onConfirmImport: () -> Unit = {},
    val onCancelImport: () -> Unit = {},
    val onThemeMode: (ThemeMode) -> Unit = {},
    val onReduceTransparency: (Boolean) -> Unit = {},
    val onBackgroundAnimation: (Boolean) -> Unit = {},
    val onOpenAbout: () -> Unit = {},
    val debug: DebugActions? = null,
)

private enum class SettingsSheet { None, Currency, RefreshInterval, ReminderDays, TrafficAlert, ResetZone }

/** Group tints (the icon tiles), after the mockup's palette. */
private object RowTints {
    val Teal = Color(0xFF14B8A6)
    val Blue = Color(0xFF3B82F6)
    val Violet = Color(0xFF8B5CF6)
    val Amber = Color(0xFFF59E0B)
    val Coral = Color(0xFFF43F5E)
    val Green = Color(0xFF22C55E)
    val Slate = Color(0xFF64748B)
    val Indigo = Color(0xFF6366F1)
}

/**
 * The settings tab as glass groups: each group one glass panel, its rows separated by hairlines.
 * Choices open glass sheets; the import confirmation is a glass dialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onShowArchivedChange: (Boolean) -> Unit,
    onDefaultCurrencyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenKomari: () -> Unit = {},
    onSync: () -> Unit = {},
    now: Instant = com.ftwegc.myapplication.ui.common.appNow(),
    actions: SettingsActions = SettingsActions(),
    notificationStatusOverride: NotificationStatus? = null,
) {
    val bottomPadding = LocalBottomBarPadding.current
    var sheet by rememberSaveable { mutableStateOf(SettingsSheet.None) }
    val settings = state.settings
    val context = LocalContext.current
    val env = currentGlass()
    val permission = rememberNotificationPermission(
        askedBefore = settings.notificationPermissionRequested,
        onRequested = actions.onNotificationAsked,
    )
    val notificationStatus = notificationStatusOverride ?: permission.status

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding + 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PageTitle(stringResource(R.string.settings_title), subtitle = stringResource(R.string.settings_subtitle))

            // ---------------------------------------------------------------- Komari
            GlassGroup(stringResource(R.string.settings_section_komari)) {
                val url = settings.komariUrl
                GlassRow(
                    title = stringResource(R.string.settings_komari_panel),
                    subtitle = url?.substringAfter("://") ?: stringResource(R.string.settings_komari_panel_desc),
                    icon = Icons.Rounded.Hub,
                    iconTint = RowTints.Blue,
                    onClick = onOpenKomari,
                )
                AnimatedVisibility(visible = url != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
                        GlassDivider()
                        val last = settings.komariLastSyncAt
                        GlassRow(
                            title = stringResource(if (state.isSyncing) R.string.komari_syncing else R.string.settings_komari_sync),
                            subtitle = if (last == null) {
                                stringResource(R.string.komari_sync_never)
                            } else {
                                stringResource(R.string.komari_sync_last, relativeTimeText(Formatters.relativeTime(Instant.ofEpochMilli(last), now, ZoneId.systemDefault())))
                            },
                            icon = Icons.Rounded.Sync,
                            iconTint = RowTints.Teal,
                            onClick = if (state.isSyncing) null else onSync,
                            chevron = false,
                            trailing = if (state.isSyncing) ({ VluerSpinner(size = 22.dp) }) else null,
                        )
                    }
                }
            }

            // ---------------------------------------------------------------- Reminders
            GlassGroup(stringResource(R.string.settings_section_reminder)) {
                val allowed = notificationStatus == NotificationStatus.ALLOWED
                GlassRow(
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(
                        when (notificationStatus) {
                            NotificationStatus.ALLOWED -> R.string.settings_notifications_on
                            NotificationStatus.NOT_ASKED -> R.string.settings_notifications_not_asked
                            NotificationStatus.BLOCKED -> R.string.settings_notifications_blocked
                        },
                    ),
                    icon = if (allowed) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                    iconTint = if (allowed) RowTints.Green else RowTints.Coral,
                    onClick = { if (allowed) permission.openSettings() else permission.request() },
                    chevron = false,
                    trailing = {
                        StatusCapsule(
                            text = stringResource(
                                when (notificationStatus) {
                                    NotificationStatus.ALLOWED -> R.string.settings_notifications_on_short
                                    NotificationStatus.NOT_ASKED -> R.string.settings_notifications_enable
                                    NotificationStatus.BLOCKED -> R.string.settings_notifications_to_settings
                                },
                            ),
                            color = if (allowed) GlassAccents.mint(env.dark) else GlassAccents.coral(env.dark),
                        )
                    },
                )
                GlassDivider()
                GlassRow(
                    title = stringResource(R.string.settings_reminder_days),
                    subtitle = reminderDaysText(settings.reminderDays),
                    icon = Icons.AutoMirrored.Rounded.EventNote,
                    iconTint = RowTints.Amber,
                    onClick = { sheet = SettingsSheet.ReminderDays },
                )
                GlassDivider()
                GlassRow(
                    title = stringResource(R.string.settings_traffic_alert),
                    subtitle = stringResource(R.string.settings_traffic_alert_desc, settings.trafficAlertPercent),
                    icon = Icons.Rounded.DataUsage,
                    iconTint = RowTints.Teal,
                    value = stringResource(R.string.traffic_alert_value, settings.trafficAlertPercent),
                    onClick = { sheet = SettingsSheet.TrafficAlert },
                )
            }

            // ---------------------------------------------------------------- Background refresh
            GlassGroup(stringResource(R.string.settings_section_background)) {
                GlassRow(
                    title = stringResource(R.string.settings_refresh_interval),
                    subtitle = stringResource(if (settings.refreshIntervalMinutes > 0) R.string.settings_refresh_interval_desc else R.string.settings_refresh_interval_off_desc),
                    icon = Icons.Rounded.Update,
                    iconTint = RowTints.Blue,
                    value = refreshIntervalText(settings.refreshIntervalMinutes),
                    onClick = { sheet = SettingsSheet.RefreshInterval },
                )
                GlassDivider()
                GlassRow(
                    title = stringResource(R.string.settings_reset_zone),
                    subtitle = stringResource(R.string.settings_reset_zone_desc),
                    icon = Icons.Rounded.Public,
                    iconTint = RowTints.Violet,
                    value = zoneShortLabel(settings.resetZoneId),
                    onClick = { sheet = SettingsSheet.ResetZone },
                )
                GlassDivider()
                BackgroundLimitNote(onOpen = { openBatteryOptimizationSettings(context) })
            }

            // ---------------------------------------------------------------- Appearance
            GlassGroup(stringResource(R.string.settings_section_appearance)) {
                Column(Modifier.padding(horizontal = GlassListDefaults.HorizontalPadding, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassIconTile(Icons.Rounded.DarkMode, RowTints.Indigo)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_theme), color = env.ink.primary, fontSize = 15.sp)
                            Text(stringResource(R.string.settings_theme_desc), color = env.ink.secondary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                    val modes = listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT)
                    GlassSegmented(
                        labels = modes.map { themeLabel(it) },
                        selected = modes.indexOf(settings.themeMode),
                        onSelect = { actions.onThemeMode(modes[it]) },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                GlassDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_reduce_transparency),
                    subtitle = stringResource(R.string.settings_reduce_transparency_desc),
                    icon = Icons.Rounded.BlurOn,
                    tint = RowTints.Slate,
                    checked = settings.reduceTransparency,
                    onCheckedChange = actions.onReduceTransparency,
                )
                GlassDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_background_animation),
                    subtitle = stringResource(R.string.settings_background_animation_desc),
                    icon = Icons.Rounded.Animation,
                    tint = RowTints.Teal,
                    checked = settings.backgroundAnimation,
                    onCheckedChange = actions.onBackgroundAnimation,
                )
            }

            // ---------------------------------------------------------------- Display
            GlassGroup(stringResource(R.string.settings_section_display)) {
                SwitchRow(
                    title = stringResource(R.string.settings_show_archived),
                    subtitle = stringResource(R.string.settings_show_archived_desc),
                    icon = Icons.Rounded.Archive,
                    tint = RowTints.Slate,
                    checked = settings.showArchived,
                    onCheckedChange = onShowArchivedChange,
                )
                GlassDivider()
                GlassRow(
                    title = stringResource(R.string.settings_default_currency),
                    subtitle = stringResource(R.string.settings_default_currency_desc),
                    icon = Icons.Rounded.CurrencyExchange,
                    iconTint = RowTints.Violet,
                    value = "${Formatters.currencySymbol(settings.defaultCurrency)} ${settings.defaultCurrency}",
                    onClick = { sheet = SettingsSheet.Currency },
                )
            }

            // ---------------------------------------------------------------- Data
            GlassGroup(stringResource(R.string.settings_section_data)) {
                GlassRow(
                    title = stringResource(R.string.settings_export),
                    subtitle = stringResource(R.string.settings_export_desc),
                    icon = Icons.Rounded.FileUpload,
                    iconTint = RowTints.Teal,
                    onClick = actions.onExport,
                    enabled = !state.isTransferring,
                )
                GlassDivider()
                GlassRow(
                    title = stringResource(R.string.settings_import),
                    subtitle = stringResource(R.string.settings_import_desc),
                    icon = Icons.Rounded.FileDownload,
                    iconTint = RowTints.Blue,
                    onClick = actions.onImport,
                    enabled = !state.isTransferring,
                    trailing = if (state.isTransferring) ({ VluerSpinner(size = 22.dp) }) else null,
                )
            }

            // ---------------------------------------------------------------- Debug
            val debug = actions.debug
            if (debug != null) {
                GlassGroup(stringResource(R.string.settings_section_debug)) {
                    GlassRow(
                        title = stringResource(R.string.debug_gallery),
                        subtitle = stringResource(R.string.debug_gallery_desc),
                        icon = Icons.Rounded.Widgets,
                        iconTint = RowTints.Violet,
                        onClick = debug.onOpenGallery,
                    )
                    GlassDivider()
                    GlassRow(
                        title = stringResource(R.string.debug_run_reminders),
                        subtitle = stringResource(R.string.debug_run_reminders_desc),
                        icon = Icons.Rounded.BugReport,
                        iconTint = RowTints.Amber,
                        onClick = debug.onRunReminders,
                        chevron = false,
                    )
                    GlassDivider()
                    GlassRow(
                        title = stringResource(R.string.debug_run_refresh),
                        subtitle = stringResource(R.string.debug_run_refresh_desc),
                        icon = Icons.Rounded.BugReport,
                        iconTint = RowTints.Amber,
                        onClick = debug.onRunRefresh,
                        chevron = false,
                    )
                }
            }

            // ---------------------------------------------------------------- About
            GlassGroup(stringResource(R.string.settings_section_about)) {
                AboutRow(state.versionName, actions.onOpenAbout)
                GlassDivider()
                GlassRow(
                    title = stringResource(R.string.settings_privacy),
                    subtitle = stringResource(R.string.settings_privacy_desc),
                    icon = Icons.Rounded.Shield,
                    iconTint = RowTints.Green,
                )
            }
        }
        StatusBarScrim()
    }

    SettingsSheets(sheet, settings, onDismiss = { sheet = SettingsSheet.None }, onDefaultCurrencyChange, actions)

    val plan = rememberLatest(state.pendingImport)
    ImportConfirmDialog(visible = state.pendingImport != null, plan = plan, onConfirm = actions.onConfirmImport, onDismiss = actions.onCancelImport)
}

@Composable
private fun SettingsSheets(
    sheet: SettingsSheet,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onDefaultCurrencyChange: (String) -> Unit,
    actions: SettingsActions,
) {
    GlassChoiceSheet(
        visible = sheet == SettingsSheet.Currency,
        title = stringResource(R.string.settings_default_currency),
        description = stringResource(R.string.settings_default_currency_desc),
        options = Formatters.COMMON_CURRENCIES,
        selected = settings.defaultCurrency,
        label = { "${Formatters.currencySymbol(it)}  $it" },
        onPick = onDefaultCurrencyChange,
        onDismiss = onDismiss,
    )
    GlassChoiceSheet(
        visible = sheet == SettingsSheet.RefreshInterval,
        title = stringResource(R.string.settings_refresh_interval),
        description = stringResource(R.string.settings_refresh_interval_desc),
        options = AppSettings.REFRESH_CHOICES,
        selected = settings.refreshIntervalMinutes,
        label = { refreshIntervalText(it) },
        onPick = actions.onRefreshInterval,
        onDismiss = onDismiss,
    )
    GlassChoiceSheet(
        visible = sheet == SettingsSheet.ResetZone,
        title = stringResource(R.string.settings_reset_zone),
        description = stringResource(R.string.settings_reset_zone_desc),
        options = listOf<String?>(null) + ResetZones.map { it.first },
        selected = settings.resetZoneId,
        label = { zoneLongLabel(it) },
        onPick = actions.onResetZone,
        onDismiss = onDismiss,
    )
    ReminderDaysSheet(
        visible = sheet == SettingsSheet.ReminderDays,
        current = settings.reminderDays,
        onDismiss = onDismiss,
        onSave = {
            onDismiss()
            actions.onReminderDays(it)
        },
    )
    TrafficAlertSheet(
        visible = sheet == SettingsSheet.TrafficAlert,
        current = settings.trafficAlertPercent,
        onDismiss = onDismiss,
        onSave = {
            onDismiss()
            actions.onTrafficAlert(it)
        },
    )
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.DARK -> R.string.settings_theme_dark
        ThemeMode.LIGHT -> R.string.settings_theme_light
    },
)

/** A group row with a glass switch; the whole row toggles. */
@Composable
private fun SwitchRow(title: String, subtitle: String, icon: ImageVector, tint: Color, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    GlassRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTint = tint,
        chevron = false,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        trailing = { GlassSwitch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

/** A small tinted capsule at the end of a row (notification state). */
@Composable
private fun StatusCapsule(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .drawBehind {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(color.copy(alpha = 0.16f), cornerRadius = r)
                drawRoundRect(color.copy(alpha = 0.45f), cornerRadius = r, style = Stroke(1.dp.toPx()))
            }
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** "关于续杯": the mark as the row's icon, the version at the end. */
@Composable
private fun AboutRow(version: String, onClick: () -> Unit) {
    val env = currentGlass()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = GlassListDefaults.HorizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VluerLogo(size = GlassListDefaults.IconTile)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.about_title), color = env.ink.primary, fontSize = 15.sp)
            Text(stringResource(R.string.about_row_desc), color = env.ink.secondary, fontSize = 12.sp)
        }
        Text(stringResource(R.string.settings_version_value, version), color = GlassAccents.mint(env.dark), fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = env.ink.secondary,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

/** Why background refresh can be late, and a way to the battery optimization list. */
@Composable
private fun BackgroundLimitNote(onOpen: () -> Unit) {
    val env = currentGlass()
    Column(Modifier.padding(horizontal = GlassListDefaults.HorizontalPadding, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            GlassIconTile(Icons.Rounded.BatterySaver, RowTints.Amber)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_background_limit_title), color = env.ink.primary, fontSize = 15.sp)
                Text(
                    stringResource(R.string.settings_background_limit_desc),
                    color = env.ink.secondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        GlassPillButton(stringResource(R.string.settings_background_limit_action), onOpen, Modifier.padding(top = 10.dp).align(Alignment.End))
    }
}

/** Zones offered for the traffic reset day, with their Chinese names. */
private val ResetZones: List<Pair<String, Int>> = listOf(
    "Asia/Shanghai" to R.string.zone_shanghai,
    "Asia/Hong_Kong" to R.string.zone_hong_kong,
    "Asia/Tokyo" to R.string.zone_tokyo,
    "Asia/Singapore" to R.string.zone_singapore,
    "Europe/London" to R.string.zone_london,
    "Europe/Berlin" to R.string.zone_berlin,
    "Europe/Moscow" to R.string.zone_moscow,
    "America/New_York" to R.string.zone_new_york,
    "America/Los_Angeles" to R.string.zone_los_angeles,
    "UTC" to R.string.zone_utc,
)

@Composable
private fun zoneShortLabel(zoneId: String?): String {
    if (zoneId == null) return stringResource(R.string.zone_system_short)
    val name = ResetZones.firstOrNull { it.first == zoneId }?.second
    return if (name != null) stringResource(name) else zoneId
}

@Composable
private fun zoneLongLabel(zoneId: String?): String {
    val zone = zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
    val offset = zone.rules.getOffset(Instant.now()).getDisplayName(ZoneTextStyle.FULL, Locale.ROOT).let { if (it == "Z") "+00:00" else it }
    return if (zoneId == null) {
        stringResource(R.string.zone_system, zone.id, offset)
    } else {
        stringResource(R.string.zone_named, zoneShortLabel(zoneId), offset)
    }
}

/** "到期提醒": which days before the due date remind, as glass chips; saved with the button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderDaysSheet(visible: Boolean, current: List<Int>, onDismiss: () -> Unit, onSave: (List<Int>) -> Unit) {
    val env = currentGlass()
    var days by rememberSaveable(visible) { mutableStateOf(current) }
    GlassSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        contentDescription = stringResource(R.string.settings_reminder_days),
        bottomBar = { GlassButton(stringResource(R.string.action_save), { onSave(days) }, Modifier.fillMaxWidth()) },
    ) {
        Text(stringResource(R.string.settings_reminder_days), color = env.ink.primary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.settings_reminder_days_dialog_desc), color = env.ink.secondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReminderDays.CHOICES.forEach { day ->
                val selected = day in days
                GlassChip(
                    text = reminderDayLabel(day),
                    selected = selected,
                    onClick = { days = if (selected) days - day else ReminderDays.normalize(days + day) },
                )
            }
        }
        Text(
            reminderDaysText(days),
            color = if (days.isEmpty()) GlassAccents.amber(env.dark) else GlassAccents.mint(env.dark),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

/** "默认流量阈值": a glass slider in 5 % steps. */
@Composable
private fun TrafficAlertSheet(visible: Boolean, current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val env = currentGlass()
    var value by rememberSaveable(visible) { mutableFloatStateOf(current.toFloat()) }
    val percent = (value / 5).roundToInt() * 5
    GlassSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        contentDescription = stringResource(R.string.settings_traffic_alert),
        bottomBar = { GlassButton(stringResource(R.string.action_save), { onSave(percent) }, Modifier.fillMaxWidth()) },
    ) {
        Text(stringResource(R.string.settings_traffic_alert), color = env.ink.primary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.settings_traffic_alert_dialog_desc), color = env.ink.secondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
        Text(
            stringResource(R.string.traffic_alert_value, percent),
            color = env.ink.primary,
            fontSize = 40.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(fontFeatureSettings = "tnum"),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 14.dp, bottom = 6.dp),
        )
        GlassSlider(
            value = value,
            onValueChange = { value = it },
            valueRange = VpsConfig.MIN_ALERT_PERCENT.toFloat()..VpsConfig.MAX_ALERT_PERCENT.toFloat(),
            step = 5f,
            contentDescription = stringResource(R.string.settings_traffic_alert),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
    }
}

/** The import confirmation: how many subscriptions come in, duplicates and invalid entries. */
@Composable
private fun ImportConfirmDialog(visible: Boolean, plan: ImportPlan?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val env = currentGlass()
    val count = plan?.toImport?.size ?: 0
    GlassConfirmDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        icon = Icons.Rounded.FileDownload,
        title = if (count > 0) stringResource(R.string.backup_import_title, count) else stringResource(R.string.backup_import_nothing_title),
        message = if (count > 0) stringResource(R.string.backup_import_message) else null,
        confirmLabel = stringResource(if (count > 0) R.string.backup_import_confirm else R.string.action_confirm),
        onConfirm = if (count > 0) onConfirm else onDismiss,
        dismissLabel = if (count > 0) stringResource(R.string.action_cancel) else null,
    ) {
        if (plan != null && plan.duplicates > 0) {
            Text(
                stringResource(R.string.backup_import_duplicates, plan.duplicates),
                color = env.ink.secondary,
                fontSize = 12.5.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        if (plan != null && plan.invalid > 0) {
            Text(
                stringResource(R.string.backup_import_invalid, plan.invalid),
                color = GlassAccents.amber(env.dark),
                fontSize = 12.5.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun SettingsScreenPreview() {
    GlassPreview(padding = 0.dp, tone = PageTone.Settings) {
        SettingsScreen(
            SettingsUiState(isLoading = false, settings = AppSettings(), versionName = "1.0"),
            {}, {},
            notificationStatusOverride = NotificationStatus.BLOCKED,
        )
    }
}

@PreviewLightDark
@Composable
private fun SettingsScreenKomariPreview() {
    GlassPreview(padding = 0.dp, tone = PageTone.Settings) {
        SettingsScreen(
            SettingsUiState(
                isLoading = false,
                settings = AppSettings(
                    komariUrl = "https://status.example.com",
                    komariLastSyncAt = Instant.parse("2026-09-28T07:00:00Z").toEpochMilli(),
                    refreshIntervalMinutes = 60,
                    resetZoneId = "Asia/Shanghai",
                ),
                versionName = "1.0",
            ),
            {}, {},
            now = Instant.parse("2026-09-28T08:00:00Z"),
            actions = SettingsActions(debug = DebugActions({}, {})),
            notificationStatusOverride = NotificationStatus.ALLOWED,
        )
    }
}
