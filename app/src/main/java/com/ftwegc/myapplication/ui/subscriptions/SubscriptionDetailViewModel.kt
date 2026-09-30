package com.ftwegc.myapplication.ui.subscriptions

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ftwegc.myapplication.data.prefs.AppSettings
import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.repo.RenewOutcome
import com.ftwegc.myapplication.data.repo.SubscriptionRepository
import com.ftwegc.myapplication.domain.calc.RenewCalculator
import com.ftwegc.myapplication.domain.calc.RenewMode
import com.ftwegc.myapplication.domain.model.CycleUnit
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.domain.model.VpsConfig
import com.ftwegc.myapplication.ui.common.SubscriptionUi
import com.ftwegc.myapplication.ui.common.toUi
import com.ftwegc.myapplication.data.remote.komari.KomariError
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import com.ftwegc.myapplication.data.repo.TrafficRepository
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.ui.common.TrafficUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import com.ftwegc.myapplication.domain.calc.DeltaNote
import com.ftwegc.myapplication.domain.calc.SnapshotPoint
import com.ftwegc.myapplication.domain.model.AppPeriodUsage
import java.time.Duration
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import com.ftwegc.myapplication.data.remote.komari.KomariMachine
import com.ftwegc.myapplication.data.repo.NodeLiveRepository
import com.ftwegc.myapplication.data.repo.toReading
import com.ftwegc.myapplication.domain.calc.CumulativeSpend
import com.ftwegc.myapplication.domain.calc.LiveSeries
import com.ftwegc.myapplication.domain.calc.LiveSeriesCalculator
import com.ftwegc.myapplication.domain.calc.SpendStep
import com.ftwegc.myapplication.domain.calc.TrafficPeriodCalculator
import com.ftwegc.myapplication.domain.calc.TrafficTrend
import com.ftwegc.myapplication.domain.calc.UsageForecast
import com.ftwegc.myapplication.domain.calc.UsageForecastResult
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Immutable
data class SubscriptionDetailUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val today: LocalDate = LocalDate.MIN,
    val subscription: Subscription? = null,
    val vps: VpsConfig? = null,
    val card: SubscriptionUi? = null,
    /** Elapsed share (0..1) of the current billing cycle, `null` when it has none. */
    val cycleProgress: Float? = null,
    val canRenew: Boolean = false,
    val renewFromDue: LocalDate? = null,
    val renewFromToday: LocalDate? = null,
    /** A delete is in progress; the last content stays on screen until the page closes. */
    val isDeleting: Boolean = false,
    /** Traffic of a VPS (null for other categories). */
    val traffic: TrafficUi? = null,
    val komariConfigured: Boolean = false,
    val isRefreshing: Boolean = false,
    val now: Instant = Instant.EPOCH,
    val zone: ZoneId = ZoneOffset.UTC,
    /** Snapshots of the last [TREND_DAYS] days (trend chart input), oldest first. */
    val trendPoints: List<SnapshotPoint> = emptyList(),
    /** Accumulated periods before the current one (mode 2), newest first. */
    val history: List<AppPeriodUsage> = emptyList(),
    /** The readings suggest `--month-rotate` (counters kept after a reboot or reset without one). */
    val monthRotateSuspected: Boolean = false,
    /** Where the traffic of this period is heading (a VPS with a reading), `null` otherwise. */
    val forecast: UsageForecastResult? = null,
    /** Payments so far (the cumulative spend step curve), oldest first. */
    val spend: List<SpendStep> = emptyList(),
    /** The day the subscription was added to the app (start of the spend curve), `null` while unknown. */
    val addedOn: LocalDate? = null,
) {
    /** Pull to refresh is offered for a linked VPS. */
    val canRefreshTraffic: Boolean get() = traffic?.linked == true
}

/**
 * Live data of the linked node.
 *
 * @property series the last minute of reports (speeds, CPU, memory, disk, load, connections);
 *   `null` until the first answer. Offline nodes answer with an empty series.
 * @property machine what the agent reported about the machine (OS, CPU, virtualization).
 * @property failed the last request failed (the panel was not reachable).
 */
@Immutable
data class DetailLive(
    val series: LiveSeries? = null,
    val machine: KomariMachine? = null,
    val loaded: Boolean = false,
    val failed: Boolean = false,
)

/** One-off results the screen turns into messages or navigation. */
sealed interface DetailEvent {
    data class Renewed(val outcome: RenewOutcome) : DetailEvent
    data object RenewUndone : DetailEvent
    data class Deleted(val name: String) : DetailEvent
    data class ArchiveChanged(val archived: Boolean) : DetailEvent
    data class Failed(val message: String) : DetailEvent
    data class TrafficFailed(val error: KomariError) : DetailEvent
    data object Unlinked : DetailEvent
}

class SubscriptionDetailViewModel(
    private val id: Long,
    private val repository: SubscriptionRepository,
    settingsStore: SettingsStore,
    private val trafficRepository: TrafficRepository? = null,
    private val liveRepository: NodeLiveRepository? = null,
) : ViewModel() {

    private val deleting = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private val _events = Channel<DetailEvent>(Channel.BUFFERED)

    val events: Flow<DetailEvent> = _events.receiveAsFlow()

    /** When the subscription was added (the start of the spend curve). */
    private val addedOn = flow {
        emit(null)
        emit(runCatching { repository.createdAt(id) }.getOrNull()?.atZone(repository.zone)?.toLocalDate())
    }

    private val trafficExtras: Flow<TrafficExtras> = if (trafficRepository == null) {
        addedOn.map { TrafficExtras(addedOn = it) }
    } else {
        combine(
            trafficRepository.observeTrendPoints(id, repository.now().minus(Duration.ofDays(TREND_DAYS.toLong()))),
            trafficRepository.observePeriods(id),
            trafficRepository.observeNotes(id),
            addedOn,
        ) { points, periods, notes, added -> TrafficExtras(points, periods, notes, added) }
    }

    val uiState: StateFlow<SubscriptionDetailUiState> = combine(
        repository.observe(id),
        settingsStore.settings,
        deleting,
        refreshing,
        trafficExtras,
    ) { item, settings, isDeleting, isRefreshing, extras -> Inputs(item, settings, isDeleting, isRefreshing, extras) }
        .runningFold(SubscriptionDetailUiState()) { previous, input ->
            if (input.item == null && input.isDeleting && previous.subscription != null) {
                previous.copy(isDeleting = true)
            } else {
                build(input.item, input.settings, repository.today(), repository.now(), repository.zone, input.extras)
                    .copy(isDeleting = input.isDeleting, isRefreshing = input.isRefreshing)
            }
        }
        .catch { emit(SubscriptionDetailUiState(isLoading = false, notFound = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionDetailUiState())

    private data class Inputs(
        val item: SubscriptionWithVps?,
        val settings: AppSettings,
        val isDeleting: Boolean,
        val isRefreshing: Boolean,
        val extras: TrafficExtras,
    )

    /** Trend input, accumulated periods and snapshot notes of this VPS. */
    internal data class TrafficExtras(
        val points: List<SnapshotPoint> = emptyList(),
        val periods: List<AppPeriodUsage> = emptyList(),
        val notes: Set<DeltaNote> = emptySet(),
        val addedOn: LocalDate? = null,
    )

    /**
     * Live data of the linked node while the page is collected (in front): the last minute of
     * reports every [LIVE_INTERVAL_MILLIS] and the machine facts once. Nothing is polled for a
     * subscription without a linked node or without a panel; polling stops as soon as the page
     * leaves the screen.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val live: StateFlow<DetailLive> = uiState
        .map { s -> s.vps?.komariUuid?.takeIf { s.komariConfigured && s.subscription?.category == Category.VPS } }
        .distinctUntilChanged()
        .flatMapLatest { uuid ->
            val repo = liveRepository
            if (uuid == null || repo == null) flowOf(DetailLive()) else pollLive(repo, uuid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), DetailLive())

    private fun pollLive(repo: NodeLiveRepository, uuid: String): Flow<DetailLive> = flow {
        var current = DetailLive()
        var machineTried = false
        while (true) {
            val result = repo.recent(listOf(uuid))
            current = when (result) {
                is KomariResult.Ok -> when (val r = result.value[uuid]) {
                    is KomariResult.Ok -> current.copy(series = LiveSeriesCalculator.series(r.value.map { it.toReading() }), loaded = true, failed = false)
                    is KomariResult.Err -> current.copy(loaded = true, failed = true)
                    null -> current.copy(loaded = true)
                }
                is KomariResult.Err -> current.copy(loaded = true, failed = true)
            }
            if (!machineTried) {
                machineTried = true
                (repo.node(uuid) as? KomariResult.Ok)?.value?.let { current = current.copy(machine = it.machine) }
            }
            emit(current)
            delay(LIVE_INTERVAL_MILLIS)
        }
    }

    /**
     * Pull to refresh: re-reads this VPS's traffic. Node errors are stored with the reading and
     * shown on the card; only configuration problems are reported as a message.
     */
    fun refreshTraffic() {
        val traffic = trafficRepository ?: return
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val started = System.currentTimeMillis()
            val result = runCatching { traffic.refreshOne(id) }.getOrNull()
            if (result is KomariResult.Err && (result.error == KomariError.NotConfigured || result.error == KomariError.InvalidUrl)) {
                _events.send(DetailEvent.TrafficFailed(result.error))
            }
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < MIN_REFRESH_MILLIS) delay(MIN_REFRESH_MILLIS - elapsed)
            refreshing.value = false
        }
    }

    /** Removes the Komari link (node gone from the panel or offline for good). */
    fun unlinkKomari() {
        viewModelScope.launch {
            runCatching { repository.unlinkKomari(id) }
                .onSuccess { if (it) _events.send(DetailEvent.Unlinked) }
                .onFailure { _events.send(DetailEvent.Failed(it.message ?: it.javaClass.simpleName)) }
        }
    }

    fun markRenewed(mode: RenewMode) {
        viewModelScope.launch {
            runCatching { repository.markRenewed(id, mode) }
                .onSuccess { outcome -> if (outcome != null) _events.send(DetailEvent.Renewed(outcome)) }
                .onFailure { _events.send(DetailEvent.Failed(it.message ?: it.javaClass.simpleName)) }
        }
    }

    /**
     * Reverts a renewal. Suspends in the caller's scope (the app-wide message scope), so the undo
     * still works after this page was closed.
     */
    suspend fun undoRenew(outcome: RenewOutcome) {
        runCatching { repository.undoRenew(outcome) }
            .onSuccess { _events.trySend(DetailEvent.RenewUndone) }
    }

    fun setArchived(archived: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setArchived(id, archived) }
                .onSuccess { _events.send(DetailEvent.ArchiveChanged(archived)) }
                .onFailure { _events.send(DetailEvent.Failed(it.message ?: it.javaClass.simpleName)) }
        }
    }

    fun delete() {
        val name = uiState.value.subscription?.name.orEmpty()
        viewModelScope.launch {
            deleting.value = true
            runCatching { repository.delete(id) }
                .onSuccess { _events.send(DetailEvent.Deleted(name)) }
                .onFailure {
                    deleting.value = false
                    _events.send(DetailEvent.Failed(it.message ?: it.javaClass.simpleName))
                }
        }
    }

    companion object {
        /** Keeps the refresh indicator visible long enough to read as feedback. */
        private const val MIN_REFRESH_MILLIS = 450L

        /** Longest trend range offered (and the snapshots kept for the forecast). */
        const val TREND_DAYS: Int = 31

        /** Live node data refresh while the page is in front (never below 5 s). */
        const val LIVE_INTERVAL_MILLIS: Long = 5_000

        internal fun build(
            item: SubscriptionWithVps?,
            settings: AppSettings,
            today: LocalDate,
            now: Instant = today.atStartOfDay(ZoneOffset.UTC).toInstant(),
            zone: ZoneId = ZoneOffset.UTC,
            extras: TrafficExtras = TrafficExtras(),
        ): SubscriptionDetailUiState {
            if (item == null) return SubscriptionDetailUiState(isLoading = false, notFound = true, today = today)
            val sub = item.subscription
            val traffic = if (sub.category == Category.VPS) TrafficUi.of(item.vps ?: VpsConfig(), item.appPeriod, today, now) else null
            val currentStart = traffic?.app?.period?.start
            val vps = item.vps
            val forecast = if (traffic != null && vps != null) {
                traffic.usage?.let { usage ->
                    val period = traffic.app?.period ?: TrafficPeriodCalculator.periodFor(today, vps.resetDay)
                    val daily = TrafficTrend.daily(extras.points, zone, today, period.lengthDays.toInt().coerceAtLeast(1)).days
                    UsageForecast.compute(period, today, usage.usedBytes, vps.trafficLimitBytes, daily, vps.trafficLimitType)
                }
            } else {
                null
            }
            val spend = extras.addedOn?.let { CumulativeSpend.steps(sub, it, today) }.orEmpty()
            return SubscriptionDetailUiState(
                isLoading = false,
                today = today,
                subscription = sub,
                vps = item.vps,
                card = item.toUi(today, settings.dueSoonDays),
                cycleProgress = cycleProgress(sub, today),
                canRenew = RenewCalculator.canRenew(sub),
                renewFromDue = RenewCalculator.renew(sub, today, RenewMode.FROM_DUE)?.nextDue,
                renewFromToday = RenewCalculator.renew(sub, today, RenewMode.FROM_TODAY)?.nextDue,
                traffic = traffic,
                komariConfigured = settings.komariConfigured,
                now = now,
                zone = zone,
                trendPoints = if (sub.category == Category.VPS) extras.points else emptyList(),
                history = extras.periods.filter { currentStart == null || it.period.start.isBefore(currentStart) },
                monthRotateSuspected = DeltaNote.REBOOT_COUNTERS_KEPT in extras.notes || DeltaNote.COUNTER_RESET in extras.notes,
                forecast = forecast,
                spend = spend,
                addedOn = extras.addedOn,
            )
        }

        /**
         * Elapsed share of the current cycle: the cycle is taken to start one cycle before the
         * due date. `null` for one-time, permanent or undated subscriptions.
         */
        internal fun cycleProgress(sub: Subscription, today: LocalDate): Float? {
            if (sub.isPermanent || !sub.cycleKnown) return null
            val due = sub.nextDue ?: return null
            val start = when (sub.cycle.unit) {
                CycleUnit.MONTH -> due.minusMonths(sub.cycle.count.toLong())
                CycleUnit.DAY -> due.minusDays(sub.cycle.count.toLong())
                CycleUnit.ONCE -> return null
            }
            val total = ChronoUnit.DAYS.between(start, due)
            if (total <= 0) return null
            val elapsed = ChronoUnit.DAYS.between(start, today)
            return (elapsed.toFloat() / total).coerceIn(0f, 1f)
        }
    }
}
