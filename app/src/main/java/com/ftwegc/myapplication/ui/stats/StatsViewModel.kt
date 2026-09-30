package com.ftwegc.myapplication.ui.stats

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ftwegc.myapplication.data.prefs.SettingsStore
import com.ftwegc.myapplication.data.repo.SubscriptionRepository
import com.ftwegc.myapplication.domain.calc.CategoryShare
import com.ftwegc.myapplication.domain.calc.DueDateCalculator
import com.ftwegc.myapplication.domain.calc.MonthEntry
import com.ftwegc.myapplication.domain.calc.MonthSpend
import com.ftwegc.myapplication.domain.calc.RankRow
import com.ftwegc.myapplication.domain.calc.SpendBasis
import com.ftwegc.myapplication.domain.calc.SpendCalculator
import com.ftwegc.myapplication.domain.calc.SpendSchedule
import com.ftwegc.myapplication.domain.calc.StatsCalculator
import com.ftwegc.myapplication.domain.calc.StatsMetrics
import com.ftwegc.myapplication.domain.calc.StatsPeriod
import com.ftwegc.myapplication.domain.calc.TrafficComparison
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.ui.brand.MarkSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

/** Month or year view of the statistics page. */
enum class StatsMode { MONTH, YEAR }

/** What the page shows about one subscription (name, mark, cycle) wherever it appears. */
@Immutable
data class StatsItem(
    val id: Long,
    val name: String,
    val category: Category,
    val cycle: BillingCycle,
    val cycleKnown: Boolean,
    val mark: MarkSource,
)

/** One pin of the 90-day timeline. */
@Immutable
data class StatsPin(val id: Long, val date: LocalDate, val dayOffset: Int, val overdue: Boolean, val amountMinor: Long, val currency: String)

/**
 * The line under the 90-day timeline, in the selected currency: [count] renewals adding up to
 * [amountMinor]; [otherCurrencyCount] paid renewals in other currencies are only counted (no
 * exchange rates), so the line can say that there are more.
 */
@Immutable
data class TimelineTotal(val count: Int, val amountMinor: Long, val otherCurrencyCount: Int)

/** The page's own choices (kept by the view model while the tab is left). */
@Immutable
data class StatsSelection(
    val currency: String? = null,
    val mode: StatsMode = StatsMode.MONTH,
    val basis: SpendBasis = SpendBasis.CASH_FLOW,
    val calendarMonth: YearMonth? = null,
    val year: Int? = null,
)

/**
 * The statistics page.
 *
 * @property months the monthly bars: [StatsViewModel.MONTHS_BEFORE] months back, this month
 *   ([currentIndex]) and [StatsViewModel.MONTHS_AFTER] ahead.
 * @property years totals of the years of the year view, with [selectedYear]'s months in [yearMonths].
 * @property calendar payments per day of [calendarMonth].
 */
@Immutable
data class StatsUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.MIN,
    val hasAny: Boolean = false,
    val activeCount: Int = 0,
    val needsInfoIds: List<Long> = emptyList(),
    val currencies: List<String> = emptyList(),
    val currency: String = "CNY",
    val mode: StatsMode = StatsMode.MONTH,
    val basis: SpendBasis = SpendBasis.CASH_FLOW,
    val months: List<MonthSpend> = emptyList(),
    val currentIndex: Int = StatsViewModel.MONTHS_BEFORE,
    val years: List<Pair<Int, Long>> = emptyList(),
    val selectedYear: Int = 0,
    val yearMonths: List<MonthSpend> = emptyList(),
    val metrics: StatsMetrics? = null,
    val categories: List<CategoryShare> = emptyList(),
    val ranking: List<RankRow> = emptyList(),
    val calendarMonth: YearMonth = YearMonth.of(2000, 1),
    val calendar: Map<Int, List<MonthEntry>> = emptyMap(),
    val traffic: TrafficComparison = TrafficComparison(emptyList(), emptyList()),
    val vpsCount: Int = 0,
    val upcoming: List<StatsPin> = emptyList(),
    val items: Map<Long, StatsItem> = emptyMap(),
) {
    /** Nothing costs money in any currency (no subscription, or only ones waiting for a price). */
    val nothingCounted: Boolean get() = currencies.isEmpty()

    val needsInfoCount: Int get() = needsInfoIds.size
}

class StatsViewModel(
    private val repository: SubscriptionRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val selection = MutableStateFlow(StatsSelection())

    val uiState: StateFlow<StatsUiState> = combine(
        repository.observeAll(),
        repository.observeAddedDates(),
        settingsStore.settings.map { it.defaultCurrency }.distinctUntilChanged(),
        selection,
    ) { all, added, preferred, choice ->
        build(all, added, preferred, choice, repository.today())
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StatsUiState())

    fun setCurrency(currency: String) = selection.update { it.copy(currency = currency) }

    fun setMode(mode: StatsMode) = selection.update { it.copy(mode = mode) }

    fun setBasis(basis: SpendBasis) = selection.update { it.copy(basis = basis) }

    fun setYear(year: Int) = selection.update { it.copy(year = year) }

    /** Moves the calendar by [months] (negative: back). */
    fun moveCalendar(months: Long) {
        val current = uiState.value.calendarMonth
        selection.update { it.copy(calendarMonth = current.plusMonths(months)) }
    }

    companion object {
        const val MONTHS_BEFORE: Int = 6
        const val MONTHS_AFTER: Int = 6

        /** Days of the timeline. */
        const val TIMELINE_DAYS: Int = 90

        private const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Sum of the timeline's renewals in [currency]; free ones in other currencies are not "other currency" payments. */
        fun timelineTotal(pins: List<StatsPin>, currency: String): TimelineTotal {
            val key = SpendCalculator.currencyKey(currency)
            val inCurrency = pins.filter { it.currency == key }
            return TimelineTotal(
                count = inCurrency.size,
                amountMinor = inCurrency.sumOf { it.amountMinor },
                otherCurrencyCount = pins.count { it.currency != key && it.amountMinor > 0 },
            )
        }

        internal fun build(
            all: List<SubscriptionWithVps>,
            added: Map<Long, LocalDate>,
            preferred: String,
            choice: StatsSelection,
            today: LocalDate,
        ): StatsUiState {
            val subs = all.map { it.subscription }
            val active = all.filter { !it.subscription.archived }
            val currencies = StatsCalculator.currencies(subs, added)
            val currency = choice.currency?.let(SpendCalculator::currencyKey)?.takeIf { it in currencies }
                ?: StatsCalculator.primaryCurrency(subs, added, preferred)
            val thisMonth = YearMonth.from(today)
            val calendarMonth = choice.calendarMonth ?: thisMonth
            val selectedYear = choice.year?.takeIf { it in StatsCalculator.yearRange(today, added.values.minOrNull()) } ?: today.year
            val period = if (choice.mode == StatsMode.YEAR) StatsPeriod.YEAR else StatsPeriod.MONTH
            val years = StatsCalculator.yearRange(today, added.values.minOrNull()).map { y ->
                y to StatsCalculator.year(subs, added, y, currency, choice.basis).sumOf { it.totalMinor }
            }
            val items = all.associate { item ->
                val s = item.subscription
                s.id to StatsItem(
                    id = s.id,
                    name = s.name,
                    category = s.category,
                    cycle = s.cycle,
                    cycleKnown = s.cycleKnown,
                    mark = MarkSource(s.name, s.category, s.iconKey, s.iconColor, item.vps?.komariRegion),
                )
            }
            val activeSubs = active.map { it.subscription }
            return StatsUiState(
                isLoading = false,
                today = today,
                hasAny = active.isNotEmpty(),
                activeCount = active.size,
                needsInfoIds = activeSubs.filter(StatsCalculator::needsInfo).map { it.id },
                currencies = currencies,
                currency = currency,
                mode = choice.mode,
                basis = choice.basis,
                months = StatsCalculator.months(subs, added, thisMonth, MONTHS_BEFORE, MONTHS_AFTER, currency, choice.basis),
                currentIndex = MONTHS_BEFORE,
                years = years,
                selectedYear = selectedYear,
                yearMonths = StatsCalculator.year(subs, added, selectedYear, currency, choice.basis),
                metrics = StatsCalculator.metrics(subs, added, today, currency, period),
                categories = StatsCalculator.categories(activeSubs, currency),
                ranking = StatsCalculator.ranking(activeSubs, currency),
                calendarMonth = calendarMonth,
                calendar = StatsCalculator.days(subs, added, calendarMonth, currency),
                traffic = StatsCalculator.traffic(all, today),
                vpsCount = active.count { it.subscription.category == Category.VPS },
                upcoming = SpendSchedule.upcoming(activeSubs, today, TIMELINE_DAYS).map { (sub, date) ->
                    val days = DueDateCalculator.daysUntil(date, today)
                    StatsPin(sub.id, date, days.coerceAtLeast(0).toInt(), days < 0, if (sub.isFree) 0L else sub.priceMinor, SpendCalculator.currencyKey(sub.currency))
                },
                items = items,
            )
        }
    }
}
