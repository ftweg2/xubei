package com.ftwegc.myapplication.ui.subscriptions.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.data.repo.KomariLinkChange
import com.ftwegc.myapplication.domain.brand.BrandMatcher
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.CycleUnit
import com.ftwegc.myapplication.domain.model.LockedField
import com.ftwegc.myapplication.domain.model.ReminderDays
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.VpsConfig
import com.ftwegc.myapplication.ui.brand.BrandCatalog
import com.ftwegc.myapplication.ui.brand.BrandMark
import com.ftwegc.myapplication.ui.brand.MarkSize
import com.ftwegc.myapplication.ui.brand.MarkSource
import com.ftwegc.myapplication.ui.glass.GlassDatePickerDialog
import com.ftwegc.myapplication.ui.glass.GlassPlainIconButton
import com.ftwegc.myapplication.ui.components.CyclePreset
import com.ftwegc.myapplication.ui.format.Formatters
import com.ftwegc.myapplication.ui.format.SizeUnit
import com.ftwegc.myapplication.ui.format.categoryLabel
import com.ftwegc.myapplication.ui.format.countingModeDescription
import com.ftwegc.myapplication.ui.format.countingModeText
import com.ftwegc.myapplication.ui.format.fullDateText
import com.ftwegc.myapplication.ui.format.limitTypeText
import com.ftwegc.myapplication.ui.format.reminderDayLabel
import com.ftwegc.myapplication.ui.format.reminderDaysText
import com.ftwegc.myapplication.ui.glass.GlassAccents
import com.ftwegc.myapplication.ui.glass.GlassChip
import com.ftwegc.myapplication.ui.glass.GlassMenu
import com.ftwegc.myapplication.ui.glass.GlassMenuItem
import com.ftwegc.myapplication.ui.glass.GlassSegmented
import com.ftwegc.myapplication.ui.glass.GlassSelectField
import com.ftwegc.myapplication.ui.glass.GlassSlider
import com.ftwegc.myapplication.ui.glass.GlassSurface
import com.ftwegc.myapplication.ui.glass.GlassSwitch
import com.ftwegc.myapplication.ui.glass.GlassSwitchRow
import com.ftwegc.myapplication.ui.glass.GlassTextField
import com.ftwegc.myapplication.ui.glass.GlassThickness
import com.ftwegc.myapplication.ui.glass.LineIcons
import com.ftwegc.myapplication.ui.glass.SheetSectionTitle
import com.ftwegc.myapplication.ui.glass.currentGlass
import com.ftwegc.myapplication.ui.subscriptions.FieldError
import com.ftwegc.myapplication.ui.subscriptions.FormField
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionEditUiState
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionEditViewModel
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionFormValidator
import com.ftwegc.myapplication.ui.theme.LocalReducedMotion
import com.ftwegc.myapplication.ui.theme.Motion
import com.ftwegc.myapplication.ui.theme.motionSpec
import kotlin.math.roundToInt

/** Brands offered right under the name for each category (the rest are in the icon picker). */
private val Suggestions = mapOf(
    Category.AI to listOf("claude", "openai", "gemini", "cursor", "copilot", "deepseek"),
    Category.VPS to listOf("oracle", "aws", "vultr", "digitalocean", "hetzner", "alibabacloud"),
    Category.OTHER to listOf("icloud", "spotify", "netflix", "youtube", "telegram", "notion"),
)

/** Cycle options of the segmented control (mockup: 月付 季付 年付 自定义, plus the rest of the old choices). */
private val CycleOptions = listOf(
    CyclePreset.MONTHLY, CyclePreset.QUARTERLY, CyclePreset.SEMI_ANNUAL, CyclePreset.ANNUAL, CyclePreset.ONCE, CyclePreset.CUSTOM,
)

/**
 * The form of the add / edit sheet, in the order people fill it in: category, icon, name, price and
 * currency, cycle, due date and auto-renewal; the VPS settings unfold when VPS is chosen; the
 * rarely used fields (lifetime, free, reminders, note) are folded under "更多设置".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EditorForm(
    state: SubscriptionEditUiState,
    viewModel: SubscriptionEditViewModel,
    onClose: () -> Unit,
    onOpenKomari: () -> Unit,
    onPicked: (String) -> Unit,
) {
    val env = currentGlass()
    val form = state.form
    val errors = state.errors
    val focus = LocalFocusManager.current
    var iconPicker by rememberSaveable { mutableStateOf(false) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    var currencyMenu by remember { mutableStateOf(false) }
    var currencyAnchor by remember { mutableStateOf<Rect?>(null) }
    var moreOpen by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        // Mockup: "新增订阅" and the category chips on the right.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (state.isNew) R.string.edit_title_new else R.string.edit_title_edit),
                color = env.ink.primary,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Category.entries.forEach { c ->
                    GlassChip(
                        text = categoryLabel(c),
                        selected = form.category == c,
                        onClick = { viewModel.updateForm { it.copy(category = c) } },
                    )
                }
            }
            GlassPlainIconButton(LineIcons.Close, stringResource(R.string.action_close), onClose, iconSize = 20.dp)
        }
        if (state.isLoading) {
            Box(Modifier.fillMaxWidth().heightIn(min = 240.dp))
            return@Column
        }
        if (state.notFound) {
            Text(stringResource(R.string.detail_not_found), color = env.ink.secondary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 40.dp))
            return@Column
        }
        val banner = when {
            state.saveError != null -> stringResource(R.string.edit_save_failed, state.saveError)
            state.showErrors && errors.isNotEmpty() -> stringResource(R.string.edit_error_summary)
            else -> null
        }
        AnimatedVisibility(visible = banner != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            FormNotice(banner.orEmpty(), GlassAccents.coral(env.dark), onDismiss = if (state.saveError != null) viewModel::dismissSaveError else null)
        }
        LockedFields(if (form.category == Category.VPS) state.visibleLocks else 0, viewModel::unlock)

        // Mockup: "图标 · 输入名称后自动匹配" and a row of marks, the chosen one ringed.
        SheetSectionTitle(stringResource(R.string.editor_icon_title), Modifier.padding(top = 4.dp))
        IconRow(
            name = form.name,
            category = form.category,
            iconKey = form.iconKey,
            iconColor = form.iconColor,
            onPick = { key -> viewModel.updateForm { it.copy(iconKey = key) } },
            onMore = { iconPicker = true },
        )

        val panelName = if (form.category == Category.VPS) state.panelName else null
        GlassTextField(
            value = form.name,
            onValueChange = { v -> viewModel.updateForm { it.copy(name = v.take(SubscriptionFormValidator.NAME_MAX + 10)) } },
            label = stringResource(R.string.edit_name),
            placeholder = stringResource(R.string.edit_name_placeholder),
            error = errors[FormField.NAME]?.let { fieldErrorText(FormField.NAME, it) },
            supporting = panelName?.takeIf { it != form.name.trim() }?.let { stringResource(R.string.edit_name_panel_hint, it) },
            locked = state.isLocked(LockedField.NAME),
            lockDescription = stringResource(R.string.edit_field_locked_desc, stringResource(R.string.edit_name)),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            trailing = if (form.name.isNotEmpty()) {
                {
                    GlassPlainIconButton(LineIcons.Close, stringResource(R.string.edit_name_clear), { viewModel.updateForm { it.copy(name = "") } })
                }
            } else {
                null
            },
        )

        AnimatedVisibility(visible = !form.isFree, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                GlassTextField(
                    value = form.price,
                    onValueChange = { v -> viewModel.updateForm { it.copy(price = v.filter { ch -> ch.isDigit() || ch == '.' }.take(12)) } },
                    label = stringResource(if (state.allowIncomplete) R.string.edit_price_optional else R.string.edit_price),
                    prefix = Formatters.currencySymbol(form.currency),
                    error = errors[FormField.PRICE]?.let { fieldErrorText(FormField.PRICE, it) },
                    locked = state.isLocked(LockedField.PRICE),
                    lockDescription = stringResource(R.string.edit_field_locked_desc, stringResource(R.string.edit_price)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1.4f),
                )
                GlassTextField(
                    value = form.currency,
                    onValueChange = { v -> viewModel.updateForm { it.copy(currency = v.trim().uppercase().take(SubscriptionFormValidator.CURRENCY_MAX)) } },
                    label = stringResource(R.string.edit_currency),
                    error = errors[FormField.CURRENCY]?.let { fieldErrorText(FormField.CURRENCY, it) },
                    locked = state.isLocked(LockedField.CURRENCY),
                    lockDescription = stringResource(R.string.edit_field_locked_desc, stringResource(R.string.edit_currency)),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { currencyAnchor = it.boundsInRoot() },
                    trailing = {
                        GlassPlainIconButton(
                            Icons.Rounded.ArrowDropDown,
                            stringResource(R.string.edit_currency_pick),
                            {
                                focus.clearFocus()
                                currencyMenu = true
                            },
                            iconSize = 24.dp,
                        )
                    },
                )
            }
        }

        // Mockup `.seg`: the billing cycle.
        Row(verticalAlignment = Alignment.CenterVertically) {
            SheetSectionTitle(stringResource(R.string.edit_cycle), Modifier.weight(1f))
            if (state.isLocked(LockedField.CYCLE)) LockIcon(stringResource(R.string.edit_cycle))
        }
        GlassSegmented(
            labels = CycleOptions.map { cycleLabel(it) },
            selected = CycleOptions.indexOf(form.cyclePreset),
            onSelect = { viewModel.setCyclePreset(CycleOptions[it]) },
        )
        AnimatedVisibility(visible = form.cyclePreset == CyclePreset.CUSTOM, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                GlassTextField(
                    value = form.customCount,
                    onValueChange = { v -> viewModel.updateForm { it.copy(customCount = v.filter(Char::isDigit).take(4)) } },
                    label = stringResource(R.string.cycle_custom_count),
                    error = errors[FormField.CYCLE_COUNT]?.let { fieldErrorText(FormField.CYCLE_COUNT, it) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                GlassSegmented(
                    labels = listOf(stringResource(R.string.cycle_unit_day), stringResource(R.string.cycle_unit_month)),
                    selected = if (form.customUnit == CycleUnit.MONTH) 1 else 0,
                    onSelect = { i -> viewModel.updateForm { it.copy(customUnit = if (i == 1) CycleUnit.MONTH else CycleUnit.DAY) } },
                    modifier = Modifier
                        .width(128.dp)
                        .padding(top = 6.dp),
                )
            }
        }
        val cycleNote = errors[FormField.CYCLE]?.let { fieldErrorText(FormField.CYCLE, it) }
            ?: if (form.cyclePreset == null) stringResource(R.string.edit_cycle_unset_hint) else null
        if (cycleNote != null) {
            Text(
                cycleNote,
                color = if (errors[FormField.CYCLE] != null) GlassAccents.coral(env.dark) else env.ink.secondary,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(start = 14.dp),
            )
        }

        // Mockup: "下次到期" and "自动续费" side by side.
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
            AnimatedVisibility(visible = !form.isPermanent, modifier = Modifier.weight(1f), enter = fadeIn(), exit = fadeOut()) {
                val dueLabel = stringResource(if (form.dueRequired && !state.allowIncomplete) R.string.edit_due_date_short else R.string.edit_due_date_optional_short)
                GlassSelectField(
                    value = form.nextDue?.let { fullDateText(it) }.orEmpty(),
                    label = dueLabel,
                    placeholder = stringResource(R.string.date_field_pick),
                    onClick = { datePicker = true },
                    error = errors[FormField.DUE_DATE]?.let { fieldErrorText(FormField.DUE_DATE, it) },
                    locked = state.isLocked(LockedField.DUE_DATE),
                    lockDescription = stringResource(R.string.edit_field_locked_desc, dueLabel),
                    trailing = if (form.nextDue != null && !(form.dueRequired && !state.allowIncomplete)) {
                        {
                            GlassPlainIconButton(LineIcons.Close, stringResource(R.string.action_clear), { viewModel.setDueDate(null) })
                        }
                    } else {
                        null
                    },
                )
            }
            if (!form.isOneTime) {
                ToggleField(
                    label = stringResource(R.string.edit_auto_renew),
                    value = stringResource(if (form.autoRenew) R.string.editor_on else R.string.editor_off),
                    checked = form.autoRenew,
                    locked = state.isLocked(LockedField.AUTO_RENEW),
                    onCheckedChange = { v -> viewModel.updateForm { it.copy(autoRenew = v) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // VPS settings unfold when VPS is chosen.
        AnimatedVisibility(
            visible = form.category == Category.VPS,
            enter = expandVertically(motionSpec(Motion.emphasized()), expandFrom = Alignment.Top) + fadeIn(motionSpec(Motion.emphasized())),
            exit = shrinkVertically(motionSpec(Motion.emphasizedAccelerate()), shrinkTowards = Alignment.Top) + fadeOut(motionSpec(Motion.emphasizedAccelerate())),
        ) {
            VpsFields(state, viewModel, onOpenKomari)
        }

        // "更多设置": lifetime, free, reminders, note.
        FoldHeader(stringResource(R.string.editor_more), moreOpen, onToggle = { moreOpen = !moreOpen })
        AnimatedVisibility(visible = moreOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!form.isOneTime) {
                    GlassSwitchRow(
                        title = stringResource(R.string.edit_permanent),
                        description = stringResource(R.string.edit_permanent_desc),
                        checked = form.isPermanent,
                        onCheckedChange = { v -> viewModel.updateForm { it.copy(isPermanent = v) } },
                    )
                }
                GlassSwitchRow(
                    title = stringResource(R.string.editor_free),
                    description = stringResource(R.string.editor_free_desc),
                    checked = form.isFree,
                    onCheckedChange = { v -> viewModel.updateForm { it.copy(isFree = v) } },
                )
                if (!form.isPermanent) ReminderFields(state, viewModel)
                GlassTextField(
                    value = form.note,
                    onValueChange = { v -> viewModel.updateForm { it.copy(note = v.take(SubscriptionFormValidator.NOTE_MAX)) } },
                    label = stringResource(R.string.edit_note),
                    placeholder = stringResource(R.string.edit_note_placeholder),
                    singleLine = false,
                    minLines = 3,
                )
            }
        }
        Spacer(Modifier.padding(bottom = 4.dp))
    }

    GlassDatePickerDialog(
        visible = datePicker,
        initial = form.nextDue ?: state.today,
        title = stringResource(R.string.date_field_pick),
        onPick = viewModel::setDueDate,
        onDismiss = { datePicker = false },
        today = state.today,
    )
    GlassMenu(
        visible = currencyMenu,
        anchor = currencyAnchor,
        onDismissRequest = { currencyMenu = false },
        items = Formatters.COMMON_CURRENCIES.map { code ->
            GlassMenuItem("${Formatters.currencySymbol(code)}  $code", null, selected = code == form.currency) {
                viewModel.updateForm { it.copy(currency = code) }
            }
        },
    )
    IconPickerSheet(
        visible = iconPicker,
        name = form.name,
        category = form.category,
        iconKey = form.iconKey,
        iconColor = form.iconColor,
        onDismiss = { iconPicker = false },
        onPick = { key, color ->
            viewModel.updateForm { it.copy(iconKey = key, iconColor = color) }
        },
        onPicked = onPicked,
    )
}

@Composable
private fun cycleLabel(preset: CyclePreset): String = when (preset) {
    CyclePreset.MONTHLY -> stringResource(R.string.cycle_monthly)
    CyclePreset.QUARTERLY -> stringResource(R.string.cycle_quarterly)
    CyclePreset.SEMI_ANNUAL -> stringResource(R.string.cycle_semi_annual)
    CyclePreset.ANNUAL -> stringResource(R.string.cycle_annual)
    CyclePreset.ONCE -> stringResource(R.string.cycle_once)
    CyclePreset.CUSTOM -> stringResource(R.string.cycle_custom)
}

/**
 * The mark as it will look (matched from the name unless one was picked), a few marks of the
 * category, and "＋" for all icons, colors and a picture from the gallery.
 */
@Composable
private fun IconRow(
    name: String,
    category: Category,
    iconKey: String?,
    iconColor: Int?,
    onPick: (String?) -> Unit,
    onMore: () -> Unit,
) {
    val env = currentGlass()
    val current = MarkSource(name.ifBlank { "?" }, category, iconKey, iconColor)
    val resolved = BrandCatalog.resolve(current.name, iconKey, iconColor)
    val suggestions = Suggestions.getValue(category).filter { it != resolved.brandId }.take(4)
    val auto = iconKey == null
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val matched = if (auto && resolved.brandId != null) stringResource(R.string.editor_icon_matched) else stringResource(R.string.editor_icon_current)
        SelectableMark(current, selected = true, description = matched, onClick = onMore)
        suggestions.forEach { id ->
            val brand = BrandMatcher.byId(id)
            SelectableMark(
                MarkSource(brand?.title ?: id, category, id, null),
                selected = false,
                description = brand?.title ?: id,
                onClick = { onPick(id) },
            )
        }
        // Mockup: a translucent "＋" mark.
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(env.ink.primary.copy(alpha = if (env.dark) 0.12f else 0.08f))
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.editor_icon_more)) { onMore() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.editor_icon_more), tint = env.ink.primary, modifier = Modifier.size(22.dp))
        }
    }
}

/** A mark that can be chosen; the chosen one gets the mockup's white ring and glow. */
@Composable
internal fun SelectableMark(source: MarkSource, selected: Boolean, description: String, onClick: () -> Unit) {
    val ring by animateFloatAsState(if (selected) 1f else 0f, motionSpec(Motion.emphasized()), label = "markRing")
    val env = currentGlass()
    val ringColor = if (env.dark) Color.White else env.ink.primary
    Box(
        Modifier
            .size(44.dp)
            .border(2.5.dp * ring, ringColor.copy(alpha = ring), RoundedCornerShape(15.dp))
            .clip(RoundedCornerShape(15.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        BrandMark(source, size = MarkSize.Medium, modifier = Modifier.graphicsLayer { val s = 1f - 0.08f * ring; scaleX = s; scaleY = s })
    }
}

/** A `.fld` with a label, the state as text ("开启") and a switch (mockup "自动续费 / 开启"). */
@Composable
private fun ToggleField(label: String, value: String, checked: Boolean, locked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val env = currentGlass()
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = checked, role = Role.Switch, onClick = { onCheckedChange(!checked) }),
        shape = com.ftwegc.myapplication.ui.glass.FieldShape,
        thickness = GlassThickness.Thin,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = env.ink.secondary, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1)
                    if (locked) LockIcon(label)
                }
                Text(value, color = env.ink.primary, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp))
            }
            GlassSwitch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.graphicsLayer { scaleX = 0.8f; scaleY = 0.8f })
        }
    }
}

@Composable
private fun LockIcon(field: String) {
    val env = currentGlass()
    Icon(
        Icons.Rounded.Lock,
        contentDescription = stringResource(R.string.edit_field_locked_desc, field),
        tint = GlassAccents.amber(env.dark),
        modifier = Modifier
            .padding(start = 4.dp)
            .size(12.dp),
    )
}

@Composable
private fun FoldHeader(title: String, open: Boolean, onToggle: () -> Unit) {
    val env = currentGlass()
    val arrow by animateFloatAsState(if (open) 180f else 0f, motionSpec(Motion.emphasized()), label = "foldArrow")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button) { onToggle() }
            .heightIn(min = 44.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = env.ink.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = env.ink.secondary,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = arrow },
        )
    }
}

/** A flat tinted notice in the sheet (form errors). */
@Composable
private fun FormNotice(text: String, accent: Color, onDismiss: (() -> Unit)?) {
    val env = currentGlass()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.16f))
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = env.ink.primary, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
        if (onDismiss != null) {
            GlassPlainIconButton(LineIcons.Close, stringResource(R.string.action_dismiss), onDismiss)
        }
    }
}

/** Locked fields of a linked VPS with one unlock chip each; disappears when nothing is locked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LockedFields(visibleLocks: Int, onUnlock: (LockedField) -> Unit) {
    val env = currentGlass()
    val fields = LockedField.fromMask(visibleLocks).sortedBy { it.mask }
    val shown = remember { mutableStateOf(fields) }
    if (fields.isNotEmpty()) shown.value = fields
    AnimatedVisibility(visible = fields.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val amber = GlassAccents.amber(env.dark)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(amber.copy(alpha = 0.14f))
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = amber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.edit_locked_title, shown.value.size), color = env.ink.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(stringResource(R.string.edit_locked_message), color = env.ink.secondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                shown.value.forEach { field ->
                    val label = stringResource(R.string.edit_unlock_field, lockedFieldName(field))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(env.ink.primary.copy(alpha = 0.10f))
                            .clickable(role = Role.Button) { onUnlock(field) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.LockOpen, contentDescription = null, tint = amber, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(label, color = env.ink.primary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun lockedFieldName(field: LockedField): String = stringResource(
    when (field) {
        LockedField.NAME -> R.string.field_name
        LockedField.PRICE -> R.string.field_price
        LockedField.CURRENCY -> R.string.field_currency
        LockedField.CYCLE -> R.string.field_cycle
        LockedField.DUE_DATE -> R.string.field_due
        LockedField.AUTO_RENEW -> R.string.field_auto_renew
        LockedField.TRAFFIC_LIMIT -> R.string.field_traffic_limit
        LockedField.TRAFFIC_LIMIT_TYPE -> R.string.field_limit_type
    },
)

/** Reminder days of one subscription: the app default, or its own selection. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderFields(state: SubscriptionEditUiState, viewModel: SubscriptionEditViewModel) {
    val env = currentGlass()
    val form = state.form
    Column {
        GlassSwitchRow(
            title = stringResource(R.string.edit_reminder_custom),
            description = if (form.customReminders) stringResource(R.string.edit_reminder_custom_on) else stringResource(R.string.edit_reminder_default, reminderDaysText(state.defaultReminderDays)),
            checked = form.customReminders,
            onCheckedChange = { v ->
                viewModel.updateForm { it.copy(customReminders = v, reminderDays = if (v && it.reminderDays.isEmpty()) state.defaultReminderDays else it.reminderDays) }
            },
        )
        AnimatedVisibility(visible = form.customReminders, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    ReminderDays.CHOICES.forEach { day ->
                        GlassChip(
                            text = reminderDayLabel(day),
                            selected = day in form.reminderDays,
                            onClick = {
                                viewModel.updateForm { f ->
                                    val next = if (day in f.reminderDays) f.reminderDays - day else f.reminderDays + day
                                    f.copy(reminderDays = next.sortedDescending())
                                }
                            },
                        )
                    }
                }
                if (form.reminderDays.isEmpty()) {
                    Text(stringResource(R.string.edit_reminder_none), color = GlassAccents.amber(env.dark), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/**
 * VPS settings: the Komari node, the limit and how it is counted, the counting mode (with the
 * usage before counting started in mode 2), monthly counters, the reset day and the alert level.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VpsFields(state: SubscriptionEditUiState, viewModel: SubscriptionEditViewModel, onOpenKomari: () -> Unit) {
    val env = currentGlass()
    val form = state.form
    val errors = state.errors
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SheetSectionTitle(stringResource(R.string.edit_section_vps))
        KomariLinkRow(state, onPick = if (state.komariConfigured) viewModel::openPicker else onOpenKomari)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
            GlassTextField(
                value = form.limitValue,
                onValueChange = { v -> viewModel.updateForm { it.copy(limitValue = v.filter { ch -> ch.isDigit() || ch == '.' }.take(10)) } },
                label = stringResource(R.string.edit_traffic_limit),
                placeholder = stringResource(R.string.edit_traffic_limit_placeholder),
                error = errors[FormField.TRAFFIC_LIMIT]?.let { fieldErrorText(FormField.TRAFFIC_LIMIT, it) },
                locked = state.isLocked(LockedField.TRAFFIC_LIMIT),
                lockDescription = stringResource(R.string.edit_field_locked_desc, stringResource(R.string.edit_traffic_limit)),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
            )
            GlassSegmented(
                labels = SizeUnit.entries.map { it.name },
                selected = SizeUnit.entries.indexOf(form.limitUnit),
                onSelect = { i -> viewModel.updateForm { it.copy(limitUnit = SizeUnit.entries[i]) } },
                modifier = Modifier
                    .width(118.dp)
                    .padding(top = 6.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SheetSectionTitle(stringResource(R.string.edit_limit_type), Modifier.weight(1f))
            if (state.isLocked(LockedField.TRAFFIC_LIMIT_TYPE)) LockIcon(stringResource(R.string.edit_limit_type))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            TrafficLimitType.entries.forEach { type ->
                GlassChip(limitTypeText(type), selected = type == form.limitType, onClick = { viewModel.updateForm { it.copy(limitType = type) } })
            }
        }
        SheetSectionTitle(stringResource(R.string.edit_counting_mode), Modifier.padding(top = 4.dp))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            CountingMode.entries.forEach { mode ->
                ModeOption(mode, selected = mode == form.countingMode, onSelect = { viewModel.updateForm { it.copy(countingMode = mode) } })
            }
        }
        InitialUsed(state, viewModel)
        GlassSwitchRow(
            title = stringResource(R.string.edit_month_rotate),
            description = stringResource(R.string.edit_month_rotate_desc),
            checked = form.agentMonthRotate,
            onCheckedChange = { v -> viewModel.updateForm { it.copy(agentMonthRotate = v) } },
        )
        GlassTextField(
            value = form.resetDay,
            onValueChange = { v -> viewModel.updateForm { it.copy(resetDay = v.filter(Char::isDigit).take(2)) } },
            label = stringResource(R.string.edit_reset_day),
            suffix = stringResource(R.string.edit_reset_day_suffix),
            error = errors[FormField.RESET_DAY]?.let { fieldErrorText(FormField.RESET_DAY, it) },
            supporting = stringResource(R.string.edit_reset_day_hint),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            SheetSectionTitle(stringResource(R.string.edit_alert_percent), Modifier.weight(1f))
            Text(stringResource(R.string.traffic_alert_value, form.alertPercent), color = GlassAccents.teal(env.dark), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        GlassSlider(
            value = form.alertPercent.toFloat(),
            onValueChange = { v -> viewModel.updateForm { it.copy(alertPercent = (v / 5).roundToInt() * 5) } },
            valueRange = VpsConfig.MIN_ALERT_PERCENT.toFloat()..VpsConfig.MAX_ALERT_PERCENT.toFloat(),
            step = 5f,
            contentDescription = stringResource(R.string.edit_alert_percent),
        )
    }
}

/** The Komari node row: opens the node picker, or the Komari settings without a panel. */
@Composable
private fun KomariLinkRow(state: SubscriptionEditUiState, onPick: () -> Unit) {
    val env = currentGlass()
    val change = state.linkChange
    val linked = when (change) {
        is KomariLinkChange.Link -> true
        KomariLinkChange.Unlink -> false
        KomariLinkChange.Keep -> state.komariUuid != null
    }
    val subtitle = when {
        change is KomariLinkChange.Link -> stringResource(R.string.edit_komari_pending_link, change.name ?: change.uuid)
        change == KomariLinkChange.Unlink -> stringResource(R.string.edit_komari_pending_unlink)
        state.komariUuid != null && state.komariMissing -> stringResource(R.string.edit_komari_missing, state.komariName ?: state.komariUuid)
        state.komariUuid != null -> state.komariName ?: state.komariUuid
        !state.komariConfigured -> stringResource(R.string.edit_komari_link_disabled)
        else -> stringResource(R.string.edit_komari_unlinked)
    }
    val teal = GlassAccents.teal(env.dark)
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        thickness = GlassThickness.Thin,
        tint = if (linked) teal else Color.Unspecified,
        onClick = onPick,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Hub, contentDescription = null, tint = if (linked) teal else env.ink.secondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.edit_komari_link), color = env.ink.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    color = if (change != KomariLinkChange.Keep) teal else env.ink.secondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!state.komariConfigured) Text(stringResource(R.string.home_traffic_setup_action), color = teal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = env.ink.secondary)
        }
    }
}

@Composable
private fun ModeOption(mode: CountingMode, selected: Boolean, onSelect: () -> Unit) {
    val env = currentGlass()
    val teal = GlassAccents.teal(env.dark)
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, if (selected) teal else Color.Transparent, RoundedCornerShape(18.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        shape = RoundedCornerShape(18.dp),
        thickness = GlassThickness.Thin,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(18.dp)
                    .border(2.dp, if (selected) teal else env.ink.secondary, CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(if (selected) teal else Color.Transparent),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(countingModeText(mode), color = env.ink.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(countingModeDescription(mode), color = env.ink.secondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/**
 * Counting mode 2: why the usage so far matters and the field to enter it; unfolds with mode 2 and
 * scrolls itself into view.
 */
@Composable
private fun InitialUsed(state: SubscriptionEditUiState, viewModel: SubscriptionEditViewModel) {
    val env = currentGlass()
    val form = state.form
    val visible = form.countingMode == CountingMode.APP_ACCUMULATED
    val requester = remember { BringIntoViewRequester() }
    val reduced = LocalReducedMotion.current
    val wasVisible = remember { booleanArrayOf(visible) }
    LaunchedEffect(visible) {
        if (visible && !wasVisible[0]) {
            if (!reduced) kotlinx.coroutines.delay(Motion.DurationMedium.toLong())
            requester.bringIntoView()
        }
        wasVisible[0] = visible
    }
    val teal = GlassAccents.teal(env.dark)
    AnimatedVisibility(visible = visible, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(
            Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(requester)
                .clip(RoundedCornerShape(18.dp))
                .background(teal.copy(alpha = 0.10f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timelapse, contentDescription = null, tint = teal, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.edit_initial_used_title), color = env.ink.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(stringResource(R.string.edit_initial_used_why), color = env.ink.secondary, fontSize = 12.sp, lineHeight = 17.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                GlassTextField(
                    value = form.initialUsedValue,
                    onValueChange = { v -> viewModel.updateForm { it.copy(initialUsedValue = v.filter { ch -> ch.isDigit() || ch == '.' }.take(10)) } },
                    label = stringResource(R.string.edit_initial_used),
                    placeholder = "0",
                    error = state.errors[FormField.INITIAL_USED]?.let { fieldErrorText(FormField.INITIAL_USED, it) },
                    supporting = stringResource(R.string.edit_initial_used_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                GlassSegmented(
                    labels = SizeUnit.entries.map { it.name },
                    selected = SizeUnit.entries.indexOf(form.initialUsedUnit),
                    onSelect = { i -> viewModel.updateForm { it.copy(initialUsedUnit = SizeUnit.entries[i]) } },
                    modifier = Modifier
                        .width(118.dp)
                        .padding(top = 6.dp),
                )
            }
            AnimatedVisibility(visible = form.agentMonthRotate) {
                val amber = GlassAccents.amber(env.dark)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(amber.copy(alpha = 0.14f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = amber, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.edit_month_rotate_mode2_warning), color = env.ink.primary, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

@Composable
internal fun fieldErrorText(field: FormField, error: FieldError): String = when (error) {
    FieldError.Required -> stringResource(if (field == FormField.CURRENCY) R.string.error_currency_required else R.string.error_name_required)
    is FieldError.TooLong -> stringResource(if (field == FormField.CURRENCY) R.string.error_currency_too_long else R.string.error_name_too_long, error.max)
    FieldError.InvalidAmount -> stringResource(R.string.error_price_invalid)
    is FieldError.CycleCount -> stringResource(R.string.error_cycle_count, error.max)
    FieldError.DueRequired -> stringResource(R.string.error_due_required)
    FieldError.InvalidLimit -> stringResource(R.string.error_limit_invalid)
    FieldError.InvalidResetDay -> stringResource(R.string.error_reset_day)
    FieldError.CycleRequired -> stringResource(R.string.error_cycle_required)
    FieldError.InvalidInitialUsed -> stringResource(R.string.error_initial_used)
}
