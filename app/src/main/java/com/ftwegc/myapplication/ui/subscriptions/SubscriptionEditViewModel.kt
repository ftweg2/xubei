package com.ftwegc.myapplication.ui.subscriptions

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.remote.komari.KomariError
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import com.ftwegc.myapplication.data.repo.KomariLinkChange
import com.ftwegc.myapplication.data.repo.KomariNodeTakenException
import com.ftwegc.myapplication.data.repo.KomariRepository
import com.ftwegc.myapplication.data.repo.PickerNode
import com.ftwegc.myapplication.domain.model.LockedField
import com.ftwegc.myapplication.data.repo.SubscriptionRepository
import com.ftwegc.myapplication.data.repo.TrafficRepository
import com.ftwegc.myapplication.domain.model.CountingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import com.ftwegc.myapplication.domain.calc.DueDateCalculator
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.VpsConfig
import com.ftwegc.myapplication.ui.components.CyclePreset
import com.ftwegc.myapplication.ui.subscriptions.editor.EditorSession
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

@Immutable
data class SubscriptionEditUiState(
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
    val form: SubscriptionForm = SubscriptionForm(),
    /** Current field errors; only filled after the first save attempt. */
    val errors: Map<FormField, FieldError> = emptyMap(),
    val showErrors: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    /** The form differs from what was loaded. */
    val isDirty: Boolean = false,
    /** The stored Komari link. */
    val komariName: String? = null,
    val komariUuid: String? = null,
    val komariMissing: Boolean = false,
    /** A panel address is saved, so nodes can be picked. */
    val komariConfigured: Boolean = false,
    /** Link change applied on save. */
    val linkChange: KomariLinkChange = KomariLinkChange.Keep,
    /** Stored [LockedField] mask. */
    val lockedFields: Int = LockedField.NONE,
    /** Fields the user unlocked on this page (applied on save). */
    val unlocked: Int = LockedField.NONE,
    val picker: NodePickerState = NodePickerState.Hidden,
    /** The stored subscription is still "to be completed": price, due date and cycle may stay empty. */
    val allowIncomplete: Boolean = false,
    /** The app-wide reminder days (shown while the subscription uses the default). */
    val defaultReminderDays: List<Int> = emptyList(),
) {
    /** Locks shown next to the fields: none once the link is being changed. */
    val visibleLocks: Int
        get() = if (linkChange == KomariLinkChange.Keep && komariUuid != null) lockedFields and unlocked.inv() else LockedField.NONE

    fun isLocked(field: LockedField): Boolean = field.isLockedIn(visibleLocks)

    /** The panel name of the node the subscription is (or will be) linked to, `null` when none. */
    val panelName: String?
        get() = when (val change = linkChange) {
            KomariLinkChange.Keep -> komariName.takeIf { komariUuid != null }
            KomariLinkChange.Unlink -> null
            is KomariLinkChange.Link -> change.name
        }?.trim()?.ifEmpty { null }
}

/** The node picker sheet. */
sealed interface NodePickerState {
    data object Hidden : NodePickerState
    data object Loading : NodePickerState
    data class Failed(val error: KomariError) : NodePickerState
    data class Loaded(val nodes: List<PickerNode>) : NodePickerState
}

sealed interface EditEvent {
    /** [hasDueDate]: the saved subscription has a due date (reminders can go out for it). */
    data class Saved(val id: Long, val name: String, val isNew: Boolean, val hasDueDate: Boolean = false, val iconKey: String? = null) : EditEvent

    /** The picked node was linked to another subscription meanwhile. */
    data object LinkTaken : EditEvent
}

/**
 * The edit page. The form, the pending link change, unlocked fields and the error display are
 * kept in [savedState], so the page comes back as the user left it after the process was killed.
 *
 * @param trafficRepository applies the entered usage of the current period (counting mode 2).
 * @param appScope runs the first reading after switching to counting mode 2, beyond this page.
 */
class SubscriptionEditViewModel(
    private val id: Long,
    private val repository: SubscriptionRepository,
    private val settingsStore: SettingsStore,
    private val komariRepository: KomariRepository? = null,
    private val savedState: SavedStateHandle,
    private val trafficRepository: TrafficRepository? = null,
    private val appScope: CoroutineScope? = null,
) : ViewModel() {

    private val isNew = id == EditorSession.NEW_ID
    private val _state = MutableStateFlow(
        SubscriptionEditUiState(isNew = isNew, isLoading = true, today = repository.today()),
    )
    val uiState: StateFlow<SubscriptionEditUiState> = _state.asStateFlow()

    /** The current period's entered usage when the page opened (mode 2). */
    private var initialUsedLoaded: Long? = null

    private val _events = Channel<EditEvent>(Channel.BUFFERED)
    val events: Flow<EditEvent> = _events.receiveAsFlow()

    private var base: Subscription? = null

    /** The icon stored before this edit (a replaced gallery picture is removed after saving). */
    val baseIconKey: String? get() = base?.iconKey
    private var baseVps: VpsConfig? = null
    private var initialForm = SubscriptionForm()

    /** New subscriptions follow the chosen cycle with their due date until the user picks one. */
    private var dueFollowsCycle: Boolean
        get() = savedState[KEY_DUE_FOLLOWS] ?: isNew
        set(value) {
            savedState[KEY_DUE_FOLLOWS] = value
        }

    init {
        viewModelScope.launch {
            load()
            _state.collect { persist(it) }
        }
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                _state.update { it.copy(komariConfigured = settings.komariConfigured, defaultReminderDays = settings.reminderDays) }
            }
        }
    }

    private suspend fun load() {
        val today = repository.today()
        if (isNew) {
            val settings = settingsStore.settings.first()
            val form = SubscriptionForm(
                currency = settings.defaultCurrency,
                alertPercent = settings.trafficAlertPercent,
                nextDue = defaultDue(CyclePreset.MONTHLY, today),
                reminderDays = settings.reminderDays,
            )
            initialForm = form
            _state.update { restore(it.copy(isLoading = false, form = form, today = today)) }
        } else {
            val item = repository.get(id)
            if (item == null) {
                _state.update { it.copy(isLoading = false, notFound = true) }
                return
            }
            base = item.subscription
            baseVps = item.vps
            val settings = settingsStore.settings.first()
            // The stored period only counts when it is the current one.
            val period = item.appPeriod?.takeIf { today in it.period }
            initialUsedLoaded = period?.initialUsedBytes
            var form = SubscriptionForm.from(item.subscription, item.vps, initialUsedLoaded)
            if (!form.customReminders) form = form.copy(reminderDays = settings.reminderDays)
            initialForm = form
            _state.update {
                restore(
                    it.copy(
                        isLoading = false,
                        form = form,
                        today = today,
                        komariName = item.vps?.komariName,
                        komariUuid = item.vps?.komariUuid,
                        komariMissing = item.vps?.komariMissing == true,
                        lockedFields = item.vps?.lockedFields ?: LockedField.NONE,
                        allowIncomplete = SubscriptionFormValidator.allowsIncomplete(item.subscription),
                    ),
                )
            }
        }
    }

    /** Applies what [savedState] kept from before a process death, on top of the loaded [state]. */
    private fun restore(state: SubscriptionEditUiState): SubscriptionEditUiState {
        val savedForm = savedState.get<String>(KEY_FORM)?.let { text ->
            runCatching { formJson.decodeFromString(SubscriptionForm.serializer(), text) }.getOrNull()
        } ?: return state
        val restored = state.copy(
            form = savedForm,
            linkChange = decodeLink(savedState[KEY_LINK]),
            unlocked = savedState[KEY_UNLOCKED] ?: LockedField.NONE,
            showErrors = savedState[KEY_SHOW_ERRORS] ?: false,
        ).dirtyChecked()
        return if (restored.showErrors) restored.copy(errors = errorsOf(savedForm)) else restored
    }

    private fun errorsOf(form: SubscriptionForm): Map<FormField, FieldError> =
        (SubscriptionFormValidator.validate(form, base, baseVps) as? FormResult.Invalid)?.errors.orEmpty()

    /** Mirrors the restorable parts of [state] into [savedState]. */
    private fun persist(state: SubscriptionEditUiState) {
        if (state.isLoading || state.notFound) return
        savedState[KEY_FORM] = formJson.encodeToString(SubscriptionForm.serializer(), state.form)
        savedState[KEY_LINK] = encodeLink(state.linkChange)
        savedState[KEY_UNLOCKED] = state.unlocked
        savedState[KEY_SHOW_ERRORS] = state.showErrors
    }

    /** Applies a field change. */
    fun updateForm(transform: (SubscriptionForm) -> SubscriptionForm) {
        _state.update { s -> s.withForm(transform(s.form)) }
    }

    fun setCyclePreset(preset: CyclePreset) {
        _state.update { s ->
            var form = s.form.copy(cyclePreset = preset)
            if (dueFollowsCycle && preset != CyclePreset.CUSTOM) form = form.copy(nextDue = defaultDue(preset, s.today))
            s.withForm(form)
        }
    }

    fun setDueDate(date: LocalDate?) {
        dueFollowsCycle = false
        _state.update { s -> s.withForm(s.form.copy(nextDue = date)) }
    }

    // ------------------------------------------------------------------ Komari link and locks

    fun openPicker() {
        val komari = komariRepository ?: return
        _state.update { it.copy(picker = NodePickerState.Loading) }
        viewModelScope.launch {
            val result = komari.loadPickerNodes()
            _state.update { s ->
                if (s.picker == NodePickerState.Hidden) return@update s
                s.copy(
                    picker = when (result) {
                        is KomariResult.Ok -> NodePickerState.Loaded(result.value)
                        is KomariResult.Err -> NodePickerState.Failed(result.error)
                    },
                )
            }
        }
    }

    fun closePicker() {
        _state.update { it.copy(picker = NodePickerState.Hidden) }
    }

    /** Links [node] on save (or reverts a pending change when it is the stored node). */
    fun selectNode(node: PickerNode) {
        _state.update { s ->
            val change = if (node.node.uuid == s.komariUuid) {
                KomariLinkChange.Keep
            } else {
                KomariLinkChange.Link(
                    node.node.uuid,
                    node.node.name.ifBlank { null },
                    node.node.group.ifBlank { null },
                    node.node.region.ifBlank { null },
                )
            }
            s.copy(linkChange = change, picker = NodePickerState.Hidden).dirtyChecked()
        }
    }

    /** Removes the link on save (or drops a pending new link). */
    fun unlink() {
        _state.update { s ->
            val change = if (s.komariUuid == null) KomariLinkChange.Keep else KomariLinkChange.Unlink
            s.copy(linkChange = change, picker = NodePickerState.Hidden).dirtyChecked()
        }
    }

    /** Unlocks [field] on save: the next sync may overwrite it with the panel's value. */
    fun unlock(field: LockedField) {
        _state.update { s -> s.copy(unlocked = LockedField.lock(s.unlocked, field)).dirtyChecked() }
    }

    private fun SubscriptionEditUiState.dirtyChecked(): SubscriptionEditUiState =
        copy(isDirty = form != initialForm || linkChange != KomariLinkChange.Keep || (lockedFields and unlocked) != 0)

    fun dismissSaveError() {
        _state.update { it.copy(saveError = null) }
    }

    fun save() {
        val current = _state.value
        if (current.isSaving || current.isLoading) return
        when (val result = SubscriptionFormValidator.validate(current.form, base, baseVps)) {
            is FormResult.Invalid -> _state.update { it.copy(showErrors = true, errors = result.errors) }
            is FormResult.Valid -> viewModelScope.launch {
                _state.update { it.copy(isSaving = true, saveError = null, errors = emptyMap()) }
                val link = if (result.subscription.category == com.ftwegc.myapplication.domain.model.Category.VPS) current.linkChange else KomariLinkChange.Keep
                val wasAppMode = baseVps?.countingMode == CountingMode.APP_ACCUMULATED
                runCatching {
                    val savedId = repository.save(result.subscription, result.vps, link, current.unlocked)
                    afterSave(savedId, result, wasAppMode)
                    savedId
                }
                    .onSuccess { savedId ->
                        initialForm = _state.value.form
                        _state.update { it.copy(isSaving = false, isDirty = false) }
                        _events.send(EditEvent.Saved(savedId, result.subscription.name, isNew, result.subscription.nextDue != null, result.subscription.iconKey))
                    }
                    .onFailure { e ->
                        if (e is KomariNodeTakenException) {
                            _state.update { it.copy(isSaving = false, linkChange = KomariLinkChange.Keep) }
                            _events.send(EditEvent.LinkTaken)
                        } else {
                            _state.update { it.copy(isSaving = false, saveError = e.message ?: e.javaClass.simpleName) }
                        }
                    }
            }
        }
    }

    /**
     * Counting mode 2 follow-up: stores the entered usage of the current period (when changed or
     * newly entered), keeps the current period aligned with a changed reset day, and reads the
     * node once when counting just started, so the starting point is set right away.
     */
    private suspend fun afterSave(savedId: Long, result: FormResult.Valid, wasAppMode: Boolean) {
        val traffic = trafficRepository ?: return
        val vps = result.vps ?: return
        if (vps.countingMode != CountingMode.APP_ACCUMULATED) return
        val entered = result.initialUsedBytes ?: 0L
        if (!wasAppMode || entered != (initialUsedLoaded ?: 0L)) {
            traffic.setInitialUsed(savedId, entered)
        } else {
            traffic.alignCurrentPeriod(savedId)
        }
        if (!wasAppMode) {
            val scope = appScope ?: return
            scope.launch { runCatching { traffic.refreshOne(savedId) } }
        }
    }

    private fun SubscriptionEditUiState.withForm(form: SubscriptionForm): SubscriptionEditUiState {
        val errors = if (showErrors) {
            (SubscriptionFormValidator.validate(form, base, baseVps) as? FormResult.Invalid)?.errors.orEmpty()
        } else {
            emptyMap()
        }
        return copy(form = form, errors = errors, saveError = null).dirtyChecked()
    }

    companion object {
        private const val KEY_FORM = "edit_form"
        private const val KEY_LINK = "edit_link"
        private const val KEY_UNLOCKED = "edit_unlocked"
        private const val KEY_SHOW_ERRORS = "edit_show_errors"
        private const val KEY_DUE_FOLLOWS = "edit_due_follows_cycle"
        private const val SEP = '\u001F'

        private val formJson = Json { ignoreUnknownKeys = true }

        /** [KomariLinkChange] as text for [SavedStateHandle]. */
        internal fun encodeLink(change: KomariLinkChange): String = when (change) {
            KomariLinkChange.Keep -> "keep"
            KomariLinkChange.Unlink -> "unlink"
            is KomariLinkChange.Link ->
                listOf("link", change.uuid, change.name.orEmpty(), change.group.orEmpty(), change.region.orEmpty()).joinToString(SEP.toString())
        }

        /** Reads [encodeLink]'s text; the four-part form written before the region was kept still reads. */
        internal fun decodeLink(text: String?): KomariLinkChange {
            if (text == null || text == "keep") return KomariLinkChange.Keep
            if (text == "unlink") return KomariLinkChange.Unlink
            val parts = text.split(SEP)
            if (parts.size !in 4..5 || parts[0] != "link" || parts[1].isEmpty()) return KomariLinkChange.Keep
            return KomariLinkChange.Link(
                parts[1],
                parts[2].ifEmpty { null },
                parts[3].ifEmpty { null },
                parts.getOrNull(4)?.ifEmpty { null },
            )
        }

        /** One cycle after today, or `null` for one-time purchases. */
        internal fun defaultDue(preset: CyclePreset?, today: LocalDate): LocalDate? {
            val cycle = preset?.cycle ?: return null
            return DueDateCalculator.advance(today, cycle, today.dayOfMonth)
        }
    }
}
