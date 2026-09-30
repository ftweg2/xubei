package com.ftwegc.myapplication.ui.komari

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ftwegc.myapplication.data.remote.komari.KomariError
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import com.ftwegc.myapplication.data.repo.ImportItem
import com.ftwegc.myapplication.data.repo.ImportPreview
import com.ftwegc.myapplication.data.repo.ImportStatus
import com.ftwegc.myapplication.data.repo.ImportSummary
import com.ftwegc.myapplication.data.repo.KomariRepository
import com.ftwegc.myapplication.data.repo.TrafficRepository
import com.ftwegc.myapplication.ui.subscriptions.SubscriptionFormValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class KomariImportUiState(
    val isLoading: Boolean = true,
    val error: KomariError? = null,
    val preview: ImportPreview? = null,
    /** Selected node uuids. */
    val selected: Set<String> = emptySet(),
    /** Node uuids whose change list is expanded. */
    val expanded: Set<String> = emptySet(),
    /** Names typed on the page by node uuid ("导入后的名称"); blank means the default name. */
    val names: Map<String, String> = emptyMap(),
    val isImporting: Boolean = false,
) {
    /** uuids that can be selected (a linked node without changes has nothing to import). */
    val selectable: Set<String>
        get() = preview?.items.orEmpty().filter { it.status != ImportStatus.LINKED }.mapTo(LinkedHashSet()) { it.node.uuid }

    val allSelected: Boolean get() = selectable.isNotEmpty() && selected.containsAll(selectable)

    /** The text of [uuid]'s name field: what the user typed, else the name it will get. */
    fun nameFieldText(item: ImportItem): String = names[item.node.uuid] ?: item.importName

    /** The typed name that replaces the default one, or `null` (none, blank or unchanged). */
    fun customName(item: ImportItem): String? =
        names[item.node.uuid]?.trim()?.takeIf { it.isNotEmpty() && it != item.importName }
}

sealed interface ImportEvent {
    data class Imported(val summary: ImportSummary) : ImportEvent
    data class Failed(val message: String) : ImportEvent
}

/**
 * Node import page. After an import the traffic of every linked VPS is refreshed in
 * [appScope], so the numbers arrive even when the page is already closed.
 */
class KomariImportViewModel(
    private val repository: KomariRepository,
    private val trafficRepository: TrafficRepository,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow(KomariImportUiState())
    val uiState: StateFlow<KomariImportUiState> = _state.asStateFlow()

    private val _events = Channel<ImportEvent>(Channel.BUFFERED)
    val events: Flow<ImportEvent> = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = repository.loadImportPreview()) {
                is KomariResult.Ok -> {
                    val preview = result.value
                    val defaults = preview.items.filter { it.status != ImportStatus.LINKED }.mapTo(LinkedHashSet()) { it.node.uuid }
                    _state.update { s ->
                        // Keep the user's choice on reload, limited to nodes that still exist.
                        val selected = if (s.preview == null) defaults else s.selected.intersect(defaults)
                        s.copy(isLoading = false, error = null, preview = preview, selected = selected)
                    }
                }
                is KomariResult.Err -> _state.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    fun toggle(uuid: String) {
        _state.update { s ->
            if (uuid !in s.selectable) return@update s
            s.copy(selected = if (uuid in s.selected) s.selected - uuid else s.selected + uuid)
        }
    }

    fun toggleAll() {
        _state.update { s -> s.copy(selected = if (s.allSelected) emptySet() else s.selectable) }
    }

    /**
     * The name field of [uuid] changed. Longer input is cut to the edit page's limit, and typing
     * a name selects the node (the name only applies to an import).
     */
    fun setName(uuid: String, text: String) {
        _state.update { s ->
            val selected = if (uuid in s.selectable) s.selected + uuid else s.selected
            s.copy(names = s.names + (uuid to text.take(SubscriptionFormValidator.NAME_MAX)), selected = selected)
        }
    }

    fun toggleExpanded(uuid: String) {
        _state.update { s -> s.copy(expanded = if (uuid in s.expanded) s.expanded - uuid else s.expanded + uuid) }
    }

    fun import() {
        val s = _state.value
        val preview = s.preview ?: return
        if (s.isImporting || s.selected.isEmpty()) return
        val items = preview.items.filter { it.node.uuid in s.selected }
        val nodes = items.map { it.node }
        val names = items.mapNotNull { item -> s.customName(item)?.let { item.node.uuid to it } }.toMap()
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true) }
            runCatching { repository.importNodes(nodes, names) }
                .onSuccess { summary ->
                    appScope.launch { runCatching { trafficRepository.refreshAll() } }
                    _events.send(ImportEvent.Imported(summary))
                }
                .onFailure { e ->
                    _state.update { it.copy(isImporting = false) }
                    _events.send(ImportEvent.Failed(e.javaClass.simpleName))
                }
        }
    }
}
