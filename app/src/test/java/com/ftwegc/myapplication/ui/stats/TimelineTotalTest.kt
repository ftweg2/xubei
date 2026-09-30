package com.ftwegc.myapplication.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The line under the statistics page's 90-day timeline. */
class TimelineTotalTest {

    private val day = LocalDate.of(2026, 10, 1)

    private fun pin(id: Long, amount: Long, currency: String) = StatsPin(id, day, 2, false, amount, currency)

    private val pins = listOf(
        pin(1, 6_800, "CNY"),
        pin(2, 4_990, "CNY"),
        pin(3, 2_000, "USD"),
        pin(4, 1_000, "USD"),
        pin(5, 900, "EUR"),
        // Free: its currency does not make it a payment in another currency.
        pin(6, 0, "JPY"),
    )

    @Test
    fun sumsTheSelectedCurrency_andCountsOtherCurrencies() {
        assertEquals(TimelineTotal(count = 2, amountMinor = 11_790, otherCurrencyCount = 3), StatsViewModel.timelineTotal(pins, "CNY"))
        assertEquals(TimelineTotal(count = 2, amountMinor = 3_000, otherCurrencyCount = 3), StatsViewModel.timelineTotal(pins, "USD"))
        assertEquals(TimelineTotal(count = 1, amountMinor = 900, otherCurrencyCount = 4), StatsViewModel.timelineTotal(pins, "eur"))
    }

    @Test
    fun onlyOtherCurrencies_orNothing() {
        assertEquals(TimelineTotal(count = 0, amountMinor = 0, otherCurrencyCount = 5), StatsViewModel.timelineTotal(pins, "GBP"))
        assertEquals(TimelineTotal(count = 0, amountMinor = 0, otherCurrencyCount = 0), StatsViewModel.timelineTotal(emptyList(), "CNY"))
    }

    @Test
    fun followsTheSelectedCurrencyOfTheState() {
        val all = listOf(pin(1, 6_800, "CNY"), pin(2, 2_000, "USD"))
        val state = StatsUiState(currency = "USD", upcoming = all)
        assertEquals(TimelineTotal(1, 2_000, 1), StatsViewModel.timelineTotal(state.upcoming, state.currency))
    }
}
