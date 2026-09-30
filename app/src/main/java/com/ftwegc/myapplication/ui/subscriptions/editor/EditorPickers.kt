package com.ftwegc.myapplication.ui.subscriptions.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.data.repo.KomariLinkChange
import com.ftwegc.myapplication.data.repo.PickerNode
import com.ftwegc.myapplication.domain.brand.BrandMatcher
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.ui.brand.BrandCatalog
import com.ftwegc.myapplication.ui.brand.IconImages
import com.ftwegc.myapplication.ui.brand.MarkSource
import com.ftwegc.myapplication.ui.format.Formatters
import com.ftwegc.myapplication.ui.glass.GlassAccents
import com.ftwegc.myapplication.ui.glass.GlassPillButton
import com.ftwegc.myapplication.ui.glass.GlassSheet
import com.ftwegc.myapplication.ui.glass.GlassSurface
import com.ftwegc.myapplication.ui.glass.GlassTextField
import com.ftwegc.myapplication.ui.glass.GlassThickness
import com.ftwegc.myapplication.ui.glass.LineIcons
import com.ftwegc.myapplication.ui.glass.SheetSectionTitle
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.komari.komariErrorText
import com.ftwegc.myapplication.ui.subscriptions.NodePickerState
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionEditUiState
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionEditViewModel
import kotlinx.coroutines.launch

/**
 * All built-in icons (with a search), the letter mark, colors for the base, and a picture from
 * the gallery (the system photo picker: no storage permission; the picture is copied into the
 * app, cropped to a rounded square).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IconPickerSheet(
    visible: Boolean,
    name: String,
    category: Category,
    iconKey: String?,
    iconColor: Int?,
    onDismiss: () -> Unit,
    onPick: (String?, Int?) -> Unit,
    onPicked: (String) -> Unit,
) {
    val env = currentGlass()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        failed = false
        scope.launch {
            val key = IconImages.import(context, uri)
            importing = false
            if (key == null) {
                failed = true
            } else {
                onPicked(key)
                onPick(key, iconColor)
                onDismiss()
            }
        }
    }
    val displayName = name.ifBlank { "?" }
    val resolved = BrandCatalog.resolve(displayName, iconKey, iconColor)
    GlassSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        contentDescription = stringResource(R.string.editor_icon_picker_title),
    ) {
        Text(stringResource(R.string.editor_icon_picker_title), color = env.ink.primary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.padding(top = 10.dp))
        GlassPillButton(stringResource(R.string.editor_icon_gallery), { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            if (importing) {
                com.ftwegc.myapplication.ui.brand.VluerSpinner(size = 16.dp)
            } else {
                Icon(LineIcons.Photo, contentDescription = null, tint = env.ink.primary, modifier = Modifier.size(18.dp))
            }
        }
        if (failed) Text(stringResource(R.string.editor_icon_gallery_failed), color = GlassAccents.coral(env.dark), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))

        SheetSectionTitle(stringResource(R.string.editor_icon_auto_title), Modifier.padding(top = 14.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Automatic: matched from the name (or the letter mark when nothing matches).
            LabeledMark(
                source = MarkSource(displayName, category, null, iconColor),
                label = stringResource(R.string.editor_icon_auto),
                selected = iconKey == null,
                onClick = { onPick(null, iconColor) },
            )
            LabeledMark(
                source = MarkSource(displayName, category, BrandCatalog.LETTER_KEY, iconColor),
                label = stringResource(R.string.editor_icon_letter),
                selected = iconKey == BrandCatalog.LETTER_KEY,
                onClick = { onPick(BrandCatalog.LETTER_KEY, iconColor) },
            )
            if (BrandCatalog.imageName(iconKey) != null) {
                LabeledMark(
                    source = MarkSource(displayName, category, iconKey, iconColor),
                    label = stringResource(R.string.editor_icon_picture),
                    selected = true,
                    onClick = {},
                )
            }
        }

        SheetSectionTitle(stringResource(R.string.editor_icon_color), Modifier.padding(top = 14.dp, bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ColorDot(null, resolved.start, resolved.end, selected = iconColor == null, description = stringResource(R.string.editor_icon_color_default)) { onPick(iconKey, null) }
            BrandCatalog.MonogramGradients.forEach { (start, end) ->
                val argb = start.toInt()
                ColorDot(argb, start, end, selected = iconColor == argb, description = stringResource(R.string.editor_icon_color_item)) { onPick(iconKey, argb) }
            }
        }

        SheetSectionTitle(stringResource(R.string.editor_icon_builtin, BrandCatalog.brands.size), Modifier.padding(top = 14.dp, bottom = 8.dp))
        GlassTextField(
            value = query,
            onValueChange = { query = it.take(30) },
            label = stringResource(R.string.editor_icon_search),
            placeholder = stringResource(R.string.editor_icon_search_hint),
        )
        val q = BrandMatcher.normalize(query)
        val brands = BrandCatalog.brands.filter { b ->
            q.isEmpty() || BrandMatcher.normalize(b.title).contains(q) || b.aliases.any { BrandMatcher.normalize(it).contains(q) }
        }
        FlowRow(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            brands.forEach { brand ->
                LabeledMark(
                    source = MarkSource(brand.title, category, brand.id, iconColor),
                    label = brand.title,
                    selected = iconKey == brand.id,
                    onClick = {
                        onPick(brand.id, iconColor)
                        onDismiss()
                    },
                )
            }
        }
        if (brands.isEmpty()) {
            Text(stringResource(R.string.editor_icon_search_empty), color = env.ink.secondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
}

@Composable
private fun LabeledMark(source: MarkSource, label: String, selected: Boolean, onClick: () -> Unit) {
    val env = currentGlass()
    Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        SelectableMark(source, selected, label, onClick)
        Text(
            label,
            color = if (selected) env.ink.primary else env.ink.secondary,
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ColorDot(argb: Int?, start: Long, end: Long, selected: Boolean, description: String, onClick: () -> Unit) {
    val env = currentGlass()
    val ring = if (env.dark) Color.White else env.ink.primary
    Box(
        Modifier
            .size(32.dp)
            .border(if (selected) 2.5.dp else 0.dp, if (selected) ring else Color.Transparent, CircleShape)
            .padding(4.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(start), Color(end))))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (argb == null) Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
    }
}

/**
 * The Komari node picker as a glass sheet: loading, an error with retry, empty, or the nodes
 * (those linked to another subscription are shown but disabled); "解除关联" while one is linked.
 */
@Composable
internal fun EditorNodePicker(state: SubscriptionEditUiState, viewModel: SubscriptionEditViewModel, visible: Boolean) {
    val env = currentGlass()
    val picker = state.picker
    val currentUuid = when (val change = state.linkChange) {
        is KomariLinkChange.Link -> change.uuid
        KomariLinkChange.Unlink -> null
        KomariLinkChange.Keep -> state.komariUuid
    }
    GlassSheet(
        visible = visible && picker != NodePickerState.Hidden,
        onDismissRequest = viewModel::closePicker,
        contentDescription = stringResource(R.string.picker_title),
    ) {
        Text(stringResource(R.string.picker_title), color = env.ink.primary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.picker_hint), color = env.ink.secondary, fontSize = 12.5.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        when (picker) {
            NodePickerState.Hidden, NodePickerState.Loading -> Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                com.ftwegc.myapplication.ui.brand.VluerSpinner(size = 40.dp)
            }
            is NodePickerState.Failed -> PickerMessage(Icons.Rounded.CloudOff, komariErrorText(picker.error), viewModel::openPicker)
            is NodePickerState.Loaded -> if (picker.nodes.isEmpty()) {
                PickerMessage(Icons.Rounded.Dns, stringResource(R.string.picker_empty), viewModel::openPicker)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    picker.nodes.forEach { node ->
                        val takenByOther = node.linkedSubscriptionId != null && node.node.uuid != state.komariUuid
                        NodeRow(node, selected = node.node.uuid == currentUuid, enabled = !takenByOther) { viewModel.selectNode(node) }
                    }
                }
            }
        }
        if (currentUuid != null) {
            GlassPillButton(stringResource(R.string.picker_unlink), viewModel::unlink, Modifier.padding(top = 14.dp)) {
                Icon(Icons.Rounded.LinkOff, contentDescription = null, tint = GlassAccents.coral(env.dark), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun NodeRow(node: PickerNode, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val env = currentGlass()
    val teal = GlassAccents.teal(env.dark)
    val alpha = if (enabled) 1f else 0.5f
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, if (selected) teal else Color.Transparent, RoundedCornerShape(18.dp))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        thickness = GlassThickness.Thin,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(node.node.region.ifBlank { "🖥" }, fontSize = 18.sp, modifier = Modifier.padding(end = 10.dp))
            Column(Modifier.weight(1f)) {
                Text(node.node.name.ifBlank { node.node.uuid }, color = env.ink.primary.copy(alpha = alpha), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = if (!enabled) {
                    stringResource(R.string.picker_linked_other, node.linkedSubscriptionName.orEmpty())
                } else {
                    listOfNotNull(
                        node.node.group.ifBlank { null },
                        if (node.node.trafficLimitBytes > 0) Formatters.formatBytes(node.node.trafficLimitBytes) else stringResource(R.string.traffic_no_limit),
                    ).joinToString(" · ")
                }
                Text(detail, color = env.ink.secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier
                    .size(18.dp)
                    .border(2.dp, if (selected) teal else env.ink.secondary.copy(alpha = alpha), CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(if (selected) teal else Color.Transparent),
            )
        }
    }
}

@Composable
private fun PickerMessage(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onRetry: () -> Unit) {
    val env = currentGlass()
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = GlassAccents.teal(env.dark), modifier = Modifier.size(32.dp))
        Text(text, color = env.ink.secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp))
        GlassPillButton(stringResource(R.string.action_retry), onRetry) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = env.ink.primary, modifier = Modifier.size(16.dp))
        }
    }
}
