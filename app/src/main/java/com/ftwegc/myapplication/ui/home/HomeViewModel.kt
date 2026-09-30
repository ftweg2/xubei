package com.ftwegc.myapplication.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ftwegc.myapplication.data.prefs.AppSettings
import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.remote.komari.KomariError
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import com.ftwegc.myapplication.data.repo.NodeLiveRepository
import com.ftwegc.myapplication.data.repo.SubscriptionRepository
import com.ftwegc.myapplication.data.repo.TrafficRepository
import com.ftwegc.myapplication.data.repo.toReading
import com.ftwegc.myapplication.domain.calc.CategoryShare
import com.ftwegc.myapplication.domain.calc.DueDateCalculator
import com.ftwegc.myapplication.domain.calc.LiveSeries
import com.ftwegc.myapplication.domain.calc.LiveSeriesCalculator
import com.ftwegc.myapplication.domain.calc.SpendCalculator
import com.ftwegc.myapplication.domain.calc.SpendSchedule
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.domain.model.TrafficUsage
import com.ftwegc.myapplication.ui.brand.MarkSource
import com.ftwegc.myapplication.ui.common.SubscriptionUi
import com.ftwegc.myapplication.ui.common.TrafficIssue
import com.ftwegc.myapplication.ui.common.TrafficUi
import com.ftwegc.myapplication.ui.common.toUi
import com.ftwegc.myapplication.ui.common.usageSortKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** Amount in minor units for one currency key. */
@Immutable
data class CurrencyAmount(val currency: String, val minor: Long)

/**
 * The spending part of the overview, in the [currency] the overview leads with (the default
 * currency, see [SpendSchedule.primaryCurrency]); other currencies are listed in [otherCurrencies].
 *
 * @property thisMonthMinor payable this calendar month; [lastMonthMinor] last month.
 * @property pace payable from the 1st up to each day of this month ([todayIndex] = today).
 * @property yearByMonth payable in each month of this year ([monthIndex] = this month).
 */
@Immutable
data class HomeOverview(
    val currency: String,
    val thisMonthMinor: Long,
    val lastMonthMinor: Long,
    val otherCurrencies: List<CurrencyAmount>,
    val pace: List<Long>,
    val todayIndex: Int,
    val yearTotalMinor: Long,
    val yearByMonth: List<Long>,
    val monthIndex: Int,
    val categories: List<CategoryShare>,
    val recurringCount: Int,
) {
    val deltaMinor: Long get() = thisMonthMinor - lastMonthMinor
}

/** The subscription due soonest, for the due ring. */
@Immutable
data class HomeDue(val id: Long, val name: String, val mark: MarkSource, val daysLeft: Long, val elapsed: Float)

/** One VPS ring of the traffic overview. */
@Immutable
data class HomeRing(val id: Long, val name: String, val percent: Double)

/** One pin of the 30-day timeline. */
@Immutable
data class HomePin(val id: Long, val mark: MarkSource, val date: LocalDate, val dayOffset: Int, val overdue: Boolean)

/**
 * One linked VPS of the live node grid.
 *
 * @property storedOnline the last traffic refresh reached the node (used until live data arrives).
 */
@Immutable
data class HomeNode(val id: Long, val name: String, val uuid: String, val region: String?, val storedOnline: Boolean)

/** Live samples of the linked nodes, by subscription id; [loaded] after the first answer. */
@Immutable
data class HomeLive(val series: Map<Long, LiveSeries> = emptyMap(), val loaded: Boolean = false)

@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.MIN,
    /** Monthly amortized spend per currency, largest first. */
    val monthly: List<CurrencyAmount> = emptyList(),
    /** Annual spend per currency, same order as [monthly]. */
    val annual: List<CurrencyAmount> = emptyList(),
    val activeCount: Int = 0,
    /** Overdue and due within [HomeViewModel.UPCOMING_DAYS] days, earliest first. */
    val upcoming: List<SubscriptionUi> = emptyList(),
    val vpsCount: Int = 0,
    /** Linked VPSs, highest usage share first. */
    val traffic: List<HomeTrafficRow> = emptyList(),
    val komariConfigured: Boolean = false,
    val hasAny: Boolean = false,
    val isRefreshing: Boolean = false,
    val defaultCurrency: String = AppSettings.DEFAULT_CURRENCY,
    // ---- v2 overview
    val overview: HomeOverview? = null,
    val nextDue: HomeDue? = null,
    /** Up to [HomeViewModel.MAX_RINGS] limited VPSs, highest usage first. */
    val rings: List<HomeRing> = emptyList(),
    val timeline: List<HomePin> = emptyList(),
    val nodes: List<HomeNode> = emptyList(),
)

/** One VPS row of the home traffic section. */
@Immutable
data class HomeTrafficRow(
    val id: Long,
    val name: String,
    val usage: TrafficUsage?,
    val issue: TrafficIssue?,
)

/** One-off refresh results shown as messages. */
sealed interface HomeEvent {
    data class RefreshFailed(val error: KomariError) : HomeEvent
    data class PartlyRefreshed(val updated: Int, val notUpdated: Int) : HomeEvent
}

class HomeViewModel(
    private val repository: SubscriptionRepository,
    private val settingsStore: SettingsStore,
    private val trafficRepository: TrafficRepository? = null,
    private val liveRepository: NodeLiveRepository? = null,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeAll(),
        settingsStore.settings,
        refreshing,
    ) { all, settings, isRefreshing ->
        build(all, settings, repository.today(), isRefreshing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    /**
     * Live samples of the linked nodes, polled every [LIVE_INTERVAL_MILLIS] while the overview is
     * collected (on screen and started); polling stops as soon as nobody collects it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val live: StateFlow<HomeLive> = uiState
        .map { state -> if (state.komariConfigured) state.nodes.map { it.id to it.uuid } else emptyList() }
        .distinctUntilChanged()
        .flatMapLatest { nodes -> if (nodes.isEmpty() || liveRepository == null) flowOf(HomeLive()) else poll(liveRepository, nodes) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), HomeLive())

    private fun poll(repo: NodeLiveRepository, nodes: List<Pair<Long, String>>): Flow<HomeLive> = flow {
        var last = HomeLive()
        while (true) {
            val result = repo.recent(nodes.map { it.second })
            if (result is KomariResult.Ok) {
                val byUuid = result.value
                val series = nodes.mapNotNull { (id, uuid) ->
                    when (val r = byUuid[uuid]) {
                        is KomariResult.Ok -> id to LiveSeriesCalculator.series(r.value.map { it.toReading() })
                        // A failed node keeps what it showed.
                        else -> last.series[id]?.let { id to it }
                    }
                }.toMap()
                last = HomeLive(series, loaded = true)
                emit(last)
            }
            delay(LIVE_INTERVAL_MILLIS)
        }
    }

    /**
     * Pull to refresh: rolls auto-renewals forward and, when a panel is configured and a VPS is
     * linked, re-reads every linked VPS's traffic from Komari.
     */
    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val started = System.currentTimeMillis()
            runCatching { repository.rollForwardAutoRenewals() }
            val state = uiState.value
            val traffic = trafficRepository
            if (traffic != null && state.komariConfigured && state.traffic.isNotEmpty()) {
                when (val result = runCatching { traffic.refreshAll() }.getOrNull()) {
                    is KomariResult.Err -> _events.send(HomeEvent.RefreshFailed(result.error))
                    is KomariResult.Ok -> {
                        val summary = result.value
                        val notUpdated = summary.offline + summary.failed
                        val common = summary.commonError
                        when {
                            common != null -> _events.send(HomeEvent.RefreshFailed(common))
                            notUpdated > 0 -> _events.send(HomeEvent.PartlyRefreshed(summary.updated, notUpdated))
                        }
                    }
                    null -> Unit
                }
            }
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < MIN_REFRESH_MILLIS) delay(MIN_REFRESH_MILLIS - elapsed)
            refreshing.value = false
        }
    }

    companion object {
        const val UPCOMING_DAYS: Int = 30

        /** Rings in the traffic overview (mockup: four). */
        const val MAX_RINGS: Int = 4

        /** Live node polling interval (never below 5 s). */
        const val LIVE_INTERVAL_MILLIS: Long = 10_000

        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Keeps the refresh indicator visible long enough to read as feedback. */
        private const val MIN_REFRESH_MILLIS = 450L

        internal fun build(
            all: List<SubscriptionWithVps>,
            settings: AppSettings,
            today: LocalDate,
            isRefreshing: Boolean,
        ): HomeUiState {
            val active = all.filter { !it.subscription.archived }
            val subs = active.map { it.subscription }
            val monthly = SpendCalculator.monthlyByCurrency(subs)
            val annual = SpendCalculator.annualByCurrency(subs)
            val order = monthly.entries.sortedByDescending { it.value }.map { it.key }
            val horizon = today.plusDays(UPCOMING_DAYS.toLong())
            val upcoming = active
                .filter { item ->
                    val s = item.subscription
                    val due = s.nextDue
                    !s.isPermanent && due != null && !due.isAfter(horizon)
                }
                .sortedWith(compareBy({ it.subscription.nextDue }, { it.subscription.name }))
                .map { it.toUi(today, settings.dueSoonDays) }
            val traffic = active
                .filter { it.subscription.category == Category.VPS && it.vps?.isLinked == true }
                .map { item ->
                    val ui = TrafficUi.of(item.vps!!, item.appPeriod, today)
                    HomeTrafficRow(item.subscription.id, item.subscription.name, ui.usage, ui.issue)
                }
                .sortedWith(
                    compareByDescending<HomeTrafficRow> { it.usage.usageSortKey().first }
                        .thenByDescending { it.usage.usageSortKey().second }
                        .thenBy { it.name },
                )
            val marks = active.associate { it.subscription.id to markOf(it) }
            return HomeUiState(
                isLoading = false,
                today = today,
                monthly = order.map { CurrencyAmount(it, monthly.getValue(it)) },
                annual = order.map { CurrencyAmount(it, annual[it] ?: 0L) },
                activeCount = active.size,
                upcoming = upcoming,
                vpsCount = active.count { it.subscription.category == Category.VPS },
                traffic = traffic,
                komariConfigured = settings.komariConfigured,
                hasAny = all.isNotEmpty(),
                isRefreshing = isRefreshing,
                defaultCurrency = settings.defaultCurrency,
                overview = overviewOf(subs, settings.defaultCurrency, today),
                nextDue = SpendSchedule.nextDue(subs, today)?.let { next ->
                    HomeDue(next.subscription.id, next.subscription.name, marks.getValue(next.subscription.id), next.daysLeft, next.elapsedFraction)
                },
                rings = traffic.mapNotNull { row -> row.usage?.percent?.let { HomeRing(row.id, row.name, it) } }.take(MAX_RINGS),
                timeline = SpendSchedule.upcoming(subs, today, UPCOMING_DAYS).map { (sub, date) ->
                    val days = DueDateCalculator.daysUntil(date, today)
                    HomePin(sub.id, marks.getValue(sub.id), date, days.coerceAtLeast(0).toInt(), overdue = days < 0)
                },
                nodes = active
                    .filter { it.subscription.category == Category.VPS && it.vps?.isLinked == true }
                    .sortedBy { it.subscription.name }
                    .map { item ->
                        val vps = item.vps!!
                        HomeNode(
                            id = item.subscription.id,
                            name = item.subscription.name,
                            uuid = vps.komariUuid!!,
                            region = vps.komariRegion,
                            storedOnline = vps.lastError == null && vps.lastReportedAt != null,
                        )
                    },
            )
        }

        private fun markOf(item: SubscriptionWithVps): MarkSource = MarkSource(
            name = item.subscription.name,
            category = item.subscription.category,
            iconKey = item.subscription.iconKey,
            iconColor = item.subscription.iconColor,
            region = item.vps?.komariRegion,
        )

        /** The spending numbers of the overview; `null` when nothing is paid for. */
        internal fun overviewOf(subs: List<com.ftwegc.myapplication.domain.model.Subscription>, defaultCurrency: String, today: LocalDate): HomeOverview? {
            val recurring = subs.filter { SpendCalculator.isRecurringSpend(it) }
            if (recurring.isEmpty()) return null
            val currency = SpendSchedule.primaryCurrency(subs, defaultCurrency)
            val month = YearMonth.from(today)
            val thisMonth = SpendSchedule.monthTotals(subs, month)
            val lastMonth = SpendSchedule.monthTotals(subs, month.minusMonths(1))
            val monthlyOrder = SpendCalculator.monthlyByCurrency(subs).entries.sortedByDescending { it.value }.map { it.key }
            val others = (monthlyOrder + thisMonth.keys).distinct()
                .filter { it != currency }
                .map { CurrencyAmount(it, thisMonth[it] ?: 0L) }
                // Only currencies with something payable this month get a chip.
                .filter { it.minor > 0 }
            val byMonth = SpendSchedule.yearByMonth(subs, today.year, currency)
            return HomeOverview(
                currency = currency,
                thisMonthMinor = thisMonth[currency] ?: 0L,
                lastMonthMinor = lastMonth[currency] ?: 0L,
                otherCurrencies = others,
                pace = SpendSchedule.dailyCumulative(subs, month, currency),
                todayIndex = today.dayOfMonth - 1,
                yearTotalMinor = byMonth.sum(),
                yearByMonth = byMonth,
                monthIndex = today.monthValue - 1,
                categories = SpendSchedule.categoryShares(subs, currency),
                recurringCount = recurring.size,
            )
        }
    }
}
