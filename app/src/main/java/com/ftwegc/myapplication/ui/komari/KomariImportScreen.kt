package com.ftwegc.myapplication.ui.komari

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.data.remote.komari.KomariError
import com.ftwegc.myapplication.data.remote.komari.KomariNode
import com.ftwegc.myapplication.data.repo.ImportItem
import com.ftwegc.myapplication.data.repo.ImportPreview
import com.ftwegc.myapplication.data.repo.ImportStatus
import com.ftwegc.myapplication.domain.calc.FieldChange
import com.ftwegc.myapplication.domain.calc.KomariCycleMapper
import com.ftwegc.myapplication.domain.calc.KomariMerge
import com.ftwegc.myapplication.domain.calc.MergeDue
import com.ftwegc.myapplication.domain.calc.MergePrice
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.LockedField
import com.ftwegc.myapplication.ui.AppViewModelProvider
import com.ftwegc.myapplication.ui.background.PageTone
import com.ftwegc.myapplication.ui.brand.BrandMark
import com.ftwegc.myapplication.ui.brand.MarkSize
import com.ftwegc.myapplication.ui.brand.MarkSource
import com.ftwegc.myapplication.ui.brand.VluerSpinner
import com.ftwegc.myapplication.ui.common.LocalAppMessenger
import com.ftwegc.myapplication.ui.components.EntranceCompletion
import com.ftwegc.myapplication.ui.components.crossFadeSpec
import com.ftwegc.myapplication.ui.components.rememberEntranceState
import com.ftwegc.myapplication.ui.components.rollUpSpec
import com.ftwegc.myapplication.ui.components.staggeredEntrance
import com.ftwegc.myapplication.ui.format.Formatters
import com.ftwegc.myapplication.ui.format.cycleText
import com.ftwegc.myapplication.ui.format.fullDateText
import com.ftwegc.myapplication.ui.format.limitTypeText
import com.ftwegc.myapplication.ui.glass.GlassAccents
import com.ftwegc.myapplication.ui.glass.GlassButton
import com.ftwegc.myapplication.ui.glass.GlassCheck
import com.ftwegc.myapplication.ui.glass.GlassChip
import com.ftwegc.myapplication.ui.glass.GlassNotice
import com.ftwegc.myapplication.ui.glass.GlassPageScaffold
import com.ftwegc.myapplication.ui.glass.GlassPanel
import com.ftwegc.myapplication.ui.glass.GlassPillButton
import com.ftwegc.myapplication.ui.glass.GlassPlainIconButton
import com.ftwegc.myapplication.ui.glass.GlassSurface
import com.ftwegc.myapplication.ui.glass.GlassTextField
import com.ftwegc.myapplication.ui.glass.LineIcons
import com.ftwegc.myapplication.ui.glass.PageTitle
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.preview.GlassPreview
import com.ftwegc.myapplication.ui.subscriptions.LogoEmptyState
import com.ftwegc.myapplication.ui.theme.Motion
import com.ftwegc.myapplication.ui.theme.VluerTheme
import com.ftwegc.myapplication.ui.theme.motionSpec
import java.time.Instant
import java.time.ZoneId

@Composable
fun KomariImportDestination(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEdit: (Long) -> Unit = {},
    viewModel: KomariImportViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalAppMessenger.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel, messenger) {
        viewModel.events.collect { event ->
            when (event) {
                is ImportEvent.Imported -> {
                    val summary = event.summary
                    val incomplete = summary.incompleteIds
                    if (incomplete.isEmpty()) {
                        messenger?.showSuccess(resources.getString(R.string.import_done, summary.created, summary.updated))
                    } else {
                        // Guide the user to fill in what the panel did not have.
                        messenger?.showSuccess(
                            message = resources.getString(R.string.import_done_incomplete, summary.created + summary.updated, incomplete.size),
                            actionLabel = resources.getString(R.string.import_complete_action),
                            onAction = { onOpenEdit(incomplete.first()) },
                        )
                    }
                    onBack()
                }
                is ImportEvent.Failed -> messenger?.showInfo(resources.getString(R.string.detail_error, event.message))
            }
        }
    }
    KomariImportScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onOpenSettings = onOpenSettings,
        onToggle = viewModel::toggle,
        onToggleAll = viewModel::toggleAll,
        onToggleExpanded = viewModel::toggleExpanded,
        onImport = viewModel::import,
        onNameChange = viewModel::setName,
    )
}

private enum class ImportContent { Loading, NotConfigured, Error, Empty, Content }

/**
 * Node import in glass: every node of the panel as a glass row with its service mark and flag, a
 * round glass check, its status and a summary; it unfolds to rename it and to show the changes
 * (old → new). The import button sits at the bottom.
 */
@Composable
fun KomariImportScreen(
    state: KomariImportUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggle: (String) -> Unit,
    onToggleAll: () -> Unit,
    onToggleExpanded: (String) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    onNameChange: (uuid: String, name: String) -> Unit = { _, _ -> },
) {
    val content = when {
        state.isLoading && state.preview == null -> ImportContent.Loading
        state.error == KomariError.NotConfigured -> ImportContent.NotConfigured
        state.error != null -> ImportContent.Error
        state.preview?.items.isNullOrEmpty() -> ImportContent.Empty
        else -> ImportContent.Content
    }
    val list = rememberLazyListState()
    val density = LocalDensity.current
    val span = with(density) { 56.dp.toPx() }
    val collapsed by remember { derivedStateOf { if (list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset / span).coerceIn(0f, 1f) } }
    GlassPageScaffold(
        title = stringResource(R.string.import_title),
        onBack = onBack,
        collapsed = { collapsed },
        modifier = modifier,
        actions = {
            if (content == ImportContent.Content && state.selectable.isNotEmpty()) {
                GlassSurface(
                    shape = com.ftwegc.myapplication.ui.glass.GlassDefaults.CapsuleShape,
                    thickness = com.ftwegc.myapplication.ui.glass.GlassThickness.Thin,
                    tint = Color(0xFF2DD4BF),
                    onClick = onToggleAll,
                ) {
                    AnimatedContent(targetState = state.allSelected, transitionSpec = rollUpSpec(), label = "selectAll") { all ->
                        Text(
                            stringResource(if (all) R.string.import_select_none else R.string.import_select_all),
                            color = currentGlass().ink.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            } else {
                Spacer(Modifier.size(42.dp))
            }
        },
        bottomBar = if (content == ImportContent.Content) ({ ImportBar(state, onImport) }) else null,
    ) { padding, record ->
        AnimatedContent(
            targetState = content,
            transitionSpec = crossFadeSpec(),
            modifier = Modifier.fillMaxSize(),
            label = "importContent",
        ) { kind ->
            when (kind) {
                ImportContent.Loading -> Centered(padding) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        VluerSpinner(size = 44.dp)
                        Text(stringResource(R.string.import_loading), color = currentGlass().ink.secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
                    }
                }
                ImportContent.NotConfigured -> Centered(padding) {
                    LogoEmptyState(
                        title = stringResource(R.string.import_not_configured_title),
                        message = stringResource(R.string.import_not_configured_message),
                        action = stringResource(R.string.import_open_settings),
                        onAction = onOpenSettings,
                    )
                }
                ImportContent.Error -> Centered(padding) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LogoEmptyState(
                            title = stringResource(R.string.import_error_title),
                            message = state.error?.let { komariErrorText(it) }.orEmpty(),
                            action = stringResource(R.string.action_retry),
                            onAction = onRetry,
                        )
                        Spacer(Modifier.height(10.dp))
                        GlassChip(stringResource(R.string.import_open_settings), onClick = onOpenSettings)
                    }
                }
                ImportContent.Empty -> Centered(padding) {
                    LogoEmptyState(
                        title = stringResource(R.string.import_empty_title),
                        message = stringResource(R.string.import_empty_message),
                        action = stringResource(R.string.action_retry),
                        onAction = onRetry,
                    )
                }
                ImportContent.Content -> state.preview?.let {
                    ImportList(it, state, padding, record, list, onToggle, onToggleExpanded, onNameChange)
                }
            }
        }
    }
}

@Composable
private fun Centered(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ImportBar(state: KomariImportUiState, onImport: () -> Unit) {
    val count = state.selected.size
    GlassButton(
        text = when {
            state.isImporting -> stringResource(R.string.import_running)
            count == 0 -> stringResource(R.string.import_action_none)
            else -> stringResource(R.string.import_action, count)
        },
        onClick = onImport,
        icon = if (state.isImporting) null else Icons.Rounded.CloudDownload,
        enabled = count > 0 && !state.isImporting,
        modifier = Modifier.fillMaxWidth(),
        trailing = if (state.isImporting) ({ VluerSpinner(size = 20.dp, colors = listOf(Color.White, Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.6f))) }) else null,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportList(
    preview: ImportPreview,
    state: KomariImportUiState,
    padding: PaddingValues,
    modifier: Modifier,
    list: androidx.compose.foundation.lazy.LazyListState,
    onToggle: (String) -> Unit,
    onToggleExpanded: (String) -> Unit,
    onNameChange: (String, String) -> Unit,
) {
    val entrance = rememberEntranceState()
    EntranceCompletion(entrance)
    val reduced = VluerTheme.reducedMotion
    val counts = preview.items.groupingBy { it.status }.eachCount()
    val env = currentGlass()
    LazyColumn(
        state = list,
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
        contentPadding = padding,
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item(key = "summary") {
            Column(Modifier.staggeredEntrance(0, entrance)) {
                PageTitle(stringResource(R.string.import_title), subtitle = stringResource(R.string.import_summary, preview.items.size))
                FlowRow(
                    Modifier.padding(start = 3.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    counts[ImportStatus.NEW]?.let { GlassChip(stringResource(R.string.import_count_new, it), color = statusColor(ImportStatus.NEW)) }
                    counts[ImportStatus.UPDATE]?.let { GlassChip(stringResource(R.string.import_count_update, it), color = statusColor(ImportStatus.UPDATE)) }
                    counts[ImportStatus.LINKED]?.let { GlassChip(stringResource(R.string.import_count_linked, it), color = statusColor(ImportStatus.LINKED)) }
                }
                if (preview.droppedCount > 0) {
                    Text(
                        stringResource(R.string.import_dropped, preview.droppedCount),
                        color = env.ink.secondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 3.dp, top = 6.dp),
                    )
                }
            }
        }
        val billingMissing = preview.items.count { it.billingMissing }
        if (billingMissing > 0) {
            item(key = "billingMissing") {
                GlassPanel(Modifier.fillMaxWidth().staggeredEntrance(1, entrance), tint = Color(0xFFFBBF24)) {
                    GlassNotice(
                        icon = Icons.Rounded.EditNote,
                        text = stringResource(R.string.import_billing_missing_banner, billingMissing),
                        accent = GlassAccents.amber(env.dark),
                    )
                }
            }
        }
        if (preview.missingNames.isNotEmpty()) {
            item(key = "missing") {
                GlassPanel(Modifier.fillMaxWidth().staggeredEntrance(1, entrance), tint = Color(0xFFFB7185)) {
                    GlassNotice(
                        icon = Icons.Rounded.WarningAmber,
                        title = stringResource(R.string.import_missing_title, preview.missingNames.size),
                        text = preview.missingNames.joinToString("、"),
                        accent = GlassAccents.coral(env.dark),
                    )
                }
            }
        }
        itemsIndexed(preview.items, key = { _, item -> item.node.uuid }) { index, item ->
            NodeImportCard(
                item = item,
                selected = item.node.uuid in state.selected,
                expanded = item.node.uuid in state.expanded,
                nameText = state.nameFieldText(item),
                customName = state.customName(item),
                onToggle = { onToggle(item.node.uuid) },
                onToggleExpanded = { onToggleExpanded(item.node.uuid) },
                onNameChange = { onNameChange(item.node.uuid, it) },
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = if (reduced) null else Motion.emphasized(),
                        placementSpec = if (reduced) null else Motion.placementSpring(),
                        fadeOutSpec = if (reduced) null else Motion.emphasizedAccelerate(),
                    )
                    .staggeredEntrance(index + 1, entrance),
            )
        }
    }
}

@Composable
private fun statusColor(status: ImportStatus): Color {
    val env = currentGlass()
    return when (status) {
        ImportStatus.NEW -> GlassAccents.mint(env.dark)
        ImportStatus.UPDATE -> GlassAccents.amber(env.dark)
        ImportStatus.LINKED -> env.ink.secondary
    }
}

private val NodeShape = RoundedCornerShape(24.dp)

/**
 * A node: the check, its mark with the flag, name, status and a summary; unfolds to the name the
 * subscription will get (editable for nodes that can be imported) and the field changes.
 */
@Composable
private fun NodeImportCard(
    item: ImportItem,
    selected: Boolean,
    expanded: Boolean,
    nameText: String,
    customName: String?,
    onToggle: () -> Unit,
    onToggleExpanded: () -> Unit,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val env = currentGlass()
    val node = item.node
    val selectable = item.status != ImportStatus.LINKED
    val ring by animateColorAsState(
        if (selected) GlassAccents.teal(env.dark).copy(alpha = 0.7f) else Color.Transparent,
        motionSpec(Motion.standard(Motion.DurationMedium)),
        label = "nodeRing",
    )
    val arrow by animateFloatAsState(if (expanded) 180f else 0f, motionSpec(Motion.emphasized()), label = "expandArrow")
    val nodeName = node.name.ifBlank { node.uuid }
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, ring, NodeShape),
        shape = NodeShape,
        tint = if (selected) Color(0xFF2DD4BF) else Color.Unspecified,
        onClick = if (selectable) onToggle else onToggleExpanded,
    ) {
        Column(Modifier.padding(start = 12.dp, end = 6.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .toggleable(value = selected, enabled = selectable, role = Role.Checkbox, onValueChange = { onToggle() })
                        .semantics { contentDescription = nodeName },
                    contentAlignment = Alignment.Center,
                ) {
                    GlassCheck(checked = selected, enabled = selectable)
                }
                Spacer(Modifier.width(6.dp))
                BrandMark(MarkSource(nodeName, Category.VPS, region = node.region.ifBlank { null }), size = MarkSize.Medium)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            nodeName,
                            color = env.ink.primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        StatusTag(item.status)
                    }
                    Text(
                        nodeSummary(node),
                        color = env.ink.secondary,
                        fontSize = 12.sp,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (item.billingMissing) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                            Icon(Icons.Rounded.EditNote, contentDescription = null, tint = GlassAccents.amber(env.dark), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.billing_incomplete_tag), color = GlassAccents.amber(env.dark), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    val sub = item.subscriptionName
                    if (customName != null) {
                        Text(stringResource(R.string.import_renamed_to, customName), color = GlassAccents.mint(env.dark), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else if (sub != null && sub != node.name) {
                        Text(stringResource(R.string.import_linked_to, sub), color = env.ink.secondary, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (node.hidden || node.group.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (node.hidden) stringResource(R.string.import_hidden_tag) else node.group,
                        color = env.ink.secondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(env.ink.track)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                GlassPlainIconButton(
                    Icons.Rounded.ExpandMore,
                    stringResource(if (expanded) R.string.import_collapse else R.string.import_expand),
                    onToggleExpanded,
                    iconSize = 22.dp,
                    modifier = Modifier.graphicsLayer { rotationZ = arrow },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(motionSpec(Motion.emphasized())) + fadeIn(motionSpec(Motion.emphasized())),
                exit = shrinkVertically(motionSpec(Motion.emphasizedAccelerate())) + fadeOut(motionSpec(Motion.emphasizedAccelerate())),
            ) {
                Column(
                    Modifier.padding(start = 4.dp, end = 6.dp, top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (selectable) ImportNameField(item, nameText, customName, onNameChange)
                    ChangeList(item, hideName = selectable)
                }
            }
        }
    }
}

@Composable
private fun StatusTag(status: ImportStatus) {
    val color = statusColor(status)
    Text(
        stringResource(
            when (status) {
                ImportStatus.NEW -> R.string.import_status_new
                ImportStatus.UPDATE -> R.string.import_status_update
                ImportStatus.LINKED -> R.string.import_status_linked
            },
        ),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun nodeSummary(node: KomariNode): String {
    val parts = mutableListOf<String>()
    val price = KomariMerge.priceFromKomari(node.price)
    val cycle = KomariCycleMapper.fromDays(node.billingCycleDays)
    if (price != null) {
        val p = if (price.isFree) stringResource(R.string.free_tag) else Formatters.formatAmount(price.minor, node.currency)
        parts += if (cycle != null) "$p · ${cycleText(cycle)}" else p
    } else if (cycle != null) {
        parts += cycleText(cycle)
    }
    parts += if (node.trafficLimitBytes > 0) Formatters.formatBytes(node.trafficLimitBytes) else stringResource(R.string.traffic_no_limit)
    return parts.joinToString(" · ")
}

/**
 * The name the subscription gets: starts with the name the import would give (the panel's name
 * unless the name is locked). A name different from the panel's is locked on import, like a name
 * edited in the app, so syncs keep it.
 */
@Composable
private fun ImportNameField(item: ImportItem, text: String, customName: String?, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    val locked = customName != null && customName != item.node.name.trim()
    GlassTextField(
        value = text,
        onValueChange = onChange,
        label = stringResource(R.string.import_name_label),
        placeholder = item.importName,
        supporting = stringResource(if (locked) R.string.import_name_hint_locked else R.string.import_name_hint_panel),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        trailing = if (text.isNotEmpty()) {
            { GlassPlainIconButton(LineIcons.Close, stringResource(R.string.import_name_clear), { onChange("") }) }
        } else {
            null
        },
    )
}

/** Field changes of [item]; [hideName] leaves out the name, which the name field above shows. */
@Composable
private fun ChangeList(item: ImportItem, modifier: Modifier = Modifier, hideName: Boolean = false) {
    val env = currentGlass()
    val lockedCount = LockedField.fromMask(item.lockedFields).size
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(env.ink.track)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val changes = if (hideName) item.changes.filterNot { it is FieldChange.Name } else item.changes
        if (item.changes.isEmpty()) {
            Text(stringResource(R.string.import_up_to_date), color = env.ink.secondary, fontSize = 12.5.sp)
        } else {
            // Fields the panel left empty are not "changes"; they are listed as to be completed instead.
            val shown = if (item.billingMissing) {
                changes.filterNot { it is FieldChange.Price || it is FieldChange.Currency || it is FieldChange.Cycle || it is FieldChange.DueDate || it is FieldChange.AutoRenew }
            } else {
                changes
            }
            if (shown.isNotEmpty()) {
                Text(
                    stringResource(if (item.status == ImportStatus.NEW) R.string.import_new_fields_title else R.string.import_changes_title),
                    color = env.ink.secondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            shown.forEach { change -> ChangeRow(change, item.node.currency) }
            if (item.billingMissing) {
                Text(stringResource(R.string.import_billing_missing_note), color = GlassAccents.amber(env.dark), fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        if (lockedCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = GlassAccents.amber(env.dark), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.import_locked_note, lockedCount), color = env.ink.secondary, fontSize = 11.5.sp)
            }
        }
    }
}

@Composable
private fun ChangeRow(change: FieldChange, currency: String) {
    val env = currentGlass()
    val (label, old, new) = changeTexts(change, currency)
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = env.ink.secondary, fontSize = 12.sp, modifier = Modifier.width(64.dp))
        Column(Modifier.weight(1f)) {
            if (old != null) {
                Text(old, color = env.ink.secondary, fontSize = 12.sp, style = TextStyle(fontFeatureSettings = "tnum"), textDecoration = TextDecoration.LineThrough)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (old != null) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = GlassAccents.teal(env.dark), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(new, color = env.ink.primary, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, style = TextStyle(fontFeatureSettings = "tnum"))
            }
        }
    }
}

/** Label, old text (null for new values) and new text of a change. */
@Composable
private fun changeTexts(change: FieldChange, currency: String): Triple<String, String?, String> = when (change) {
    is FieldChange.Name -> Triple(stringResource(R.string.field_name), change.old, change.new)
    is FieldChange.Price -> Triple(stringResource(R.string.field_price), change.old?.let { priceText(it, currency) }, priceText(change.new, currency))
    is FieldChange.Currency -> Triple(
        stringResource(R.string.field_currency),
        change.old?.let { it.ifBlank { stringResource(R.string.import_value_none) } },
        change.new.ifBlank { stringResource(R.string.import_value_none) },
    )
    is FieldChange.Cycle -> Triple(stringResource(R.string.field_cycle), change.old?.let { cycleText(it) }, cycleText(change.new))
    is FieldChange.DueDate -> Triple(stringResource(R.string.field_due), change.old?.let { dueText(it) }, dueText(change.new))
    is FieldChange.AutoRenew -> Triple(
        stringResource(R.string.field_auto_renew),
        change.old?.let { stringResource(if (it) R.string.import_value_yes else R.string.import_value_no) },
        stringResource(if (change.new) R.string.import_value_yes else R.string.import_value_no),
    )
    is FieldChange.TrafficLimit -> Triple(stringResource(R.string.field_traffic_limit), change.old?.let { limitText(it) }, limitText(change.new))
    is FieldChange.LimitType -> Triple(stringResource(R.string.field_limit_type), change.old?.let { limitTypeText(it) }, limitTypeText(change.new))
}

@Composable
private fun priceText(price: MergePrice, currency: String): String =
    if (price.isFree) stringResource(R.string.free_tag) else Formatters.formatAmount(price.minor, currency)

@Composable
private fun dueText(due: MergeDue): String = when {
    due.isPermanent -> stringResource(R.string.due_permanent)
    due.date != null -> fullDateText(due.date)
    else -> stringResource(R.string.import_value_none)
}

@Composable
private fun limitText(bytes: Long): String =
    if (bytes > 0) Formatters.formatBytes(bytes) else stringResource(R.string.import_value_unlimited)

// ------------------------------------------------------------------ Previews

private fun previewNode(uuid: String, name: String, price: Double, cycle: Int, limit: Long, group: String = "", region: String = "") = KomariNode(
    uuid = uuid, name = name, group = group, tags = emptyList(), hidden = false, weight = 0, publicRemark = "",
    price = price, billingCycleDays = cycle, currency = "¥", autoRenewal = true,
    expiredAt = Instant.parse("2026-10-25T00:00:00Z"), trafficLimitBytes = limit, trafficLimitType = "max", region = region,
)

private fun previewState(): KomariImportUiState {
    val tib = 1L shl 40
    val n1 = previewNode("a", "东京 CN2 GIA", 49.9, 30, tib, "亚洲", "🇯🇵")
    val n2 = previewNode("b", "洛杉矶 9929", 129.0, 90, 500L shl 30, region = "🇺🇸")
    val n3 = previewNode("c", "法兰克福", 360.0, 365, 0, region = "🇩🇪")
    val items = listOf(
        ImportItem(
            n1, ImportStatus.UPDATE, 3, "东京 CN2 GIA",
            listOf(
                FieldChange.Price(MergePrice(3_990, false), MergePrice(4_990, false)),
                FieldChange.DueDate(MergeDue(java.time.LocalDate.of(2026, 9, 25), false), MergeDue(java.time.LocalDate.of(2026, 10, 25), false)),
            ),
            LockedField.maskOf(LockedField.NAME),
        ),
        ImportItem(
            n2, ImportStatus.NEW, null, null,
            KomariMerge.merge(null, null, n2.toSnapshot(), 0, ZoneId.of("Asia/Shanghai"), Instant.parse("2026-09-28T00:00:00Z")).changes,
            0,
        ),
        ImportItem(n3, ImportStatus.LINKED, 5, "法兰克福", emptyList(), 0),
    )
    return KomariImportUiState(
        isLoading = false,
        preview = ImportPreview(items, droppedCount = 0, missingNames = listOf("旧香港节点")),
        selected = setOf("a", "b"),
        expanded = setOf("a"),
    )
}

@PreviewLightDark
@Composable
private fun ImportPreviewContent() {
    GlassPreview(padding = 0.dp, tone = PageTone.Settings) {
        KomariImportScreen(previewState(), {}, {}, {}, {}, {}, {}, {})
    }
}

@PreviewLightDark
@Composable
private fun ImportPreviewError() {
    GlassPreview(padding = 0.dp, tone = PageTone.Settings) {
        KomariImportScreen(KomariImportUiState(isLoading = false, error = KomariError.NotKomari), {}, {}, {}, {}, {}, {}, {})
    }
}
