package com.ftwegc.myapplication.ui.subscriptions

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ftwegc.myapplication.data.prefs.AppSettings
import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.prefs.SortOrder
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import com.ftwegc.myapplication.data.repo.NodeLiveRepository
import com.ftwegc.myapplication.data.repo.RenewOutcome
import com.ftwegc.myapplication.data.repo.SubscriptionRepository
import com.ftwegc.myapplication.data.repo.toReading
import com.ftwegc.myapplication.domain.brand.BrandMatcher
import com.ftwegc.myapplication.domain.calc.LiveSeriesCalculator
import com.ftwegc.myapplication.domain.calc.RenewMode
import com.ftwegc.myapplication.domain.calc.SpendCalculator
import com.ftwegc.myapplication.domain.calc.SpendSchedule
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.ui.common.SubscriptionUi
import com.ftwegc.myapplication.ui.common.needsBillingInfo
import com.ftwegc.myapplication.ui.common.toUi
import com.ftwegc.myapplication.ui.home.CurrencyAmount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.time.LocalDate
import java.util.Locale

/** Filter chips of the subscription page: the categories, and "待补充" (billing still missing). */
enum class CategoryFilter(val category: Category?) {
    ALL(null), AI(Category.AI), VPS(Category.VPS), OTHER(Category.OTHER), NEEDS_INFO(null);

    fun matches(c: Category): Boolean = this == ALL || category == c

    fun matches(item: SubscriptionWithVps): Boolean = when (this) {
        NEEDS_INFO -> item.subscription.needsBillingInfo()
        else -> matches(item.subscription.category)
    }
}

/** CPU, memory and disk of a node right now (0..1 each, `null` when unknown), for the tile rings. */
@Immutable
data class NodeResources(val cpu: Float?, val ram: Float?, val disk: Float?)

@Immutable
data class SubscriptionListUiState(
    val isLoading: Boolean = true,
    /** Every subscription shown, sorted (the pinned one included). */
    val items: List<SubscriptionUi> = emptyList(),
    val filter: CategoryFilter = CategoryFilter.ALL,
    val sortOrder: SortOrder = SortOrder.DUE_DATE,
    val showArchived: Boolean = false,
    /** Number of subscriptions per filter chip (respecting the archived setting). */
    val counts: Map<CategoryFilter, Int> = emptyMap(),
    /** All subscriptions, archived included. */
    val totalCount: Int = 0,
    /** Archived subscriptions not shown because [showArchived] is off. */
    val hiddenArchivedCount: Int = 0,
    val loadError: String? = null,
    /** The name search; blank = no search. */
    val query: String = "",
    /** Tiles (true) or rows (false). */
    val grid: Boolean = true,
    /** The subscription due soonest among [items], shown as the wide bar on top; `null` while searching. */
    val pinned: SubscriptionUi? = null,
    /** Active (not archived) subscriptions and their monthly average in the leading currency. */
    val activeCount: Int = 0,
    val monthly: CurrencyAmount? = null,
    /** The "due soon" window (days) of the settings. */
    val dueSoonDays: Int = AppSettings.DEFAULT_DUE_SOON_DAYS,
) {
    /** Nothing at all has been added yet. */
    val isEmpty: Boolean get() = !isLoading && totalCount == 0

    /** Subscriptions exist but none matches the current filter. */
    val isFilteredEmpty: Boolean get() = !isLoading && totalCount > 0 && items.isEmpty()

    /** The tiles below the pinned bar. */
    val collection: List<SubscriptionUi> get() = pinned?.let { p -> items.filter { it.id != p.id } } ?: items

    val searching: Boolean get() = query.isNotBlank()
}

/** Results of the long-press actions, shown as messages. */
sealed interface ListEvent {
    data class Renewed(val outcome: RenewOutcome, val today: LocalDate) : ListEvent
    data object RenewUndone : ListEvent
    data class ArchiveChanged(val archived: Boolean) : ListEvent
    data class Deleted(val name: String) : ListEvent
    data class Failed(val message: String) : ListEvent
}

/**
 * The subscription page. Filter and search live in [savedState], so they survive the process being
 * killed in the background; sort order, archived visibility and the tile / row choice are settings.
 * While the page is on screen, linked VPSs are polled for their CPU, memory and disk.
 */
class SubscriptionListViewModel(
    private val repository: SubscriptionRepository,
    private val settingsStore: SettingsStore,
    private val savedState: SavedStateHandle,
    private val liveRepository: NodeLiveRepository? = null,
) : ViewModel() {

    private val filter: Flow<CategoryFilter> = savedState.getStateFlow(KEY_FILTER, CategoryFilter.ALL.name)
        .map { name -> CategoryFilter.entries.firstOrNull { it.name == name } ?: CategoryFilter.ALL }

    private val query: Flow<String> = savedState.getStateFlow(KEY_QUERY, "")

    private val _events = Channel<ListEvent>(Channel.BUFFERED)
    val events: Flow<ListEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<SubscriptionListUiState> = combine(
        repository.observeAll(),
        settingsStore.settings,
        filter,
        query,
    ) { all, settings, currentFilter, q ->
        build(all, settings, currentFilter, repository.today(), q)
    }
        .catch { e -> emit(SubscriptionListUiState(isLoading = false, loadError = e.message ?: e.javaClass.simpleName)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionListUiState())

    /**
     * CPU, memory and disk of the linked, online nodes by subscription id, polled every
     * [LIVE_INTERVAL_MILLIS] while collected; nothing is polled without a panel or linked VPS.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val resources: StateFlow<Map<Long, NodeResources>> = combine(repository.observeAll(), settingsStore.settings) { all, settings ->
        if (!settings.komariConfigured) {
            emptyList()
        } else {
            all.filter { !it.subscription.archived && it.subscription.category == Category.VPS && it.vps?.komariUuid != null }
                .map { it.subscription.id to it.vps!!.komariUuid!! }
        }
    }
        .distinctUntilChanged()
        .flatMapLatest { nodes -> if (nodes.isEmpty() || liveRepository == null) flowOf(emptyMap()) else poll(liveRepository, nodes) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyMap())

    private fun poll(repo: NodeLiveRepository, nodes: List<Pair<Long, String>>): Flow<Map<Long, NodeResources>> = flow {
        var last = emptyMap<Long, NodeResources>()
        while (true) {
            val result = repo.recent(nodes.map { it.second })
            if (result is KomariResult.Ok) {
                last = nodes.mapNotNull { (id, uuid) ->
                    when (val r = result.value[uuid]) {
                        is KomariResult.Ok -> {
                            val series = LiveSeriesCalculator.series(r.value.map { it.toReading() })
                            // Offline (no reports): no rings.
                            if (!series.online) null
                            else id to NodeResources(
                                cpu = series.latestCpu?.let { (it / 100.0).coerceIn(0.0, 1.0).toFloat() },
                                ram = series.latestRam?.toFloat(),
                                disk = series.latestDisk?.toFloat(),
                            )
                        }
                        else -> last[id]?.let { id to it }
                    }
                }.toMap()
                emit(last)
            }
            delay(LIVE_INTERVAL_MILLIS)
        }
    }

    fun setFilter(value: CategoryFilter) {
        savedState[KEY_FILTER] = value.name
    }

    fun setQuery(value: String) {
        savedState[KEY_QUERY] = value.take(QUERY_MAX)
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { settingsStore.setSortOrder(order) }
    }

    fun setShowArchived(show: Boolean) {
        viewModelScope.launch { settingsStore.setShowArchived(show) }
    }

    fun setGrid(grid: Boolean) {
        viewModelScope.launch { settingsStore.setListGrid(grid) }
    }

    fun markRenewed(id: Long, mode: RenewMode) {
        viewModelScope.launch {
            runCatching { repository.markRenewed(id, mode) }
                .onSuccess { outcome -> if (outcome != null) _events.send(ListEvent.Renewed(outcome, repository.today())) }
                .onFailure { _events.send(ListEvent.Failed(it.message ?: it.javaClass.simpleName)) }
        }
    }

    /** Reverts a renewal; suspends in the caller's (app-wide) scope so the undo outlives the page. */
    suspend fun undoRenew(outcome: RenewOutcome) {
        runCatching { repository.undoRenew(outcome) }.onSuccess { _events.trySend(ListEvent.RenewUndone) }
    }

    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setArchived(id, archived) }
                .onSuccess { _events.send(ListEvent.ArchiveChanged(archived)) }
                .onFailure { _events.send(ListEvent.Failed(it.message ?: it.javaClass.simpleName)) }
        }
    }

    fun delete(id: Long, name: String) {
        viewModelScope.launch {
            runCatching { repository.delete(id) }
                .onSuccess { _events.send(ListEvent.Deleted(name)) }
                .onFailure { _events.send(ListEvent.Failed(it.message ?: it.javaClass.simpleName)) }
        }
    }

    companion object {
        private const val KEY_FILTER = "list_filter"
        private const val KEY_QUERY = "list_query"
        private const val QUERY_MAX = 40

        /** Live node polling of the tiles (never below 5 s). */
        const val LIVE_INTERVAL_MILLIS: Long = 10_000

        internal fun build(
            all: List<SubscriptionWithVps>,
            settings: AppSettings,
            filter: CategoryFilter,
            today: LocalDate,
            query: String = "",
        ): SubscriptionListUiState {
            val visible = if (settings.showArchived) all else all.filterNot { it.subscription.archived }
            val counts = CategoryFilter.entries.associateWith { f -> visible.count { f.matches(it) } }
            val filtered = visible.filter { filter.matches(it) && matchesQuery(it.subscription.name, query) }
            val items = sort(filtered, settings.sortOrder).map { it.toUi(today, settings.dueSoonDays) }
            val active = all.filterNot { it.subscription.archived }.map { it.subscription }
            val monthly = SpendCalculator.monthlyByCurrency(active)
            val primary = SpendSchedule.primaryCurrency(active, settings.defaultCurrency)
            return SubscriptionListUiState(
                isLoading = false,
                items = items,
                filter = filter,
                sortOrder = settings.sortOrder,
                showArchived = settings.showArchived,
                counts = counts,
                totalCount = all.size,
                // Only archived subscriptions the chip and the search would have shown.
                hiddenArchivedCount = if (settings.showArchived) {
                    0
                } else {
                    all.count { it.subscription.archived && filter.matches(it) && matchesQuery(it.subscription.name, query) }
                },
                query = query,
                grid = settings.listGrid,
                pinned = if (query.isBlank()) pinnedOf(items) else null,
                activeCount = active.size,
                monthly = monthly[primary]?.let { CurrencyAmount(primary, it) }
                    ?: monthly.maxByOrNull { it.value }?.let { CurrencyAmount(it.key, it.value) },
                dueSoonDays = settings.dueSoonDays,
            )
        }

        /**
         * The subscription due soonest (overdue first), for the wide bar on top: not archived, not
         * permanent, not waiting for billing data, with a due date. `null` when none qualifies.
         */
        internal fun pinnedOf(items: List<SubscriptionUi>): SubscriptionUi? =
            items.filter { !it.archived && !it.isPermanent && !it.needsBillingInfo && it.due.date != null }
                .minWithOrNull(compareBy<SubscriptionUi>({ it.due.date }, { it.name }))

        /**
         * Name search: case, spaces, punctuation and full-width characters are ignored (the same
         * normalization as the icon matching); a blank [query] matches everything.
         */
        internal fun matchesQuery(name: String, query: String): Boolean {
            val q = BrandMatcher.normalize(query)
            if (q.isEmpty()) return true
            return BrandMatcher.normalize(name).contains(q)
        }

        private val collator: Collator = Collator.getInstance(Locale.CHINA)

        /**
         * Archived items always go last. Within each group:
         * - DUE_DATE: earliest due first; no due date and permanent last; then name.
         * - PRICE: highest monthly-equivalent first (one-time / free use the raw price); then name.
         * - NAME: Chinese collation (pinyin order), then id.
         */
        internal fun sort(items: List<SubscriptionWithVps>, order: SortOrder): List<SubscriptionWithVps> {
            val byName = compareBy<SubscriptionWithVps, String>(collator) { it.subscription.name }
            val primary: Comparator<SubscriptionWithVps> = when (order) {
                SortOrder.DUE_DATE -> compareBy<SubscriptionWithVps> {
                    val s = it.subscription
                    if (s.isPermanent || s.nextDue == null) Long.MAX_VALUE else s.nextDue.toEpochDay()
                }.then(byName)
                SortOrder.PRICE -> compareByDescending<SubscriptionWithVps> {
                    val s = it.subscription
                    SpendCalculator.monthlyExact(s.copy(archived = false))?.toDouble() ?: s.priceMinor.toDouble()
                }.then(byName)
                SortOrder.NAME -> byName.thenBy { it.subscription.id }
            }
            return items.sortedWith(compareBy<SubscriptionWithVps> { it.subscription.archived }.then(primary))
        }
    }
}
