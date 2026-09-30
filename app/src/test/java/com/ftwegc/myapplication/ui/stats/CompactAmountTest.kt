package com.ftwegc.myapplication.ui.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactAmountTest {

    @Test
    fun smallAmountsKeepCentsLargerOnesRoundToWholeUnits() {
        // $9.99 must not be cut to $9.
        assertEquals("$9.99", compactAmount(999, "USD"))
        assertEquals("¥49.90", compactAmount(4990, "CNY"))
        // Whole small amounts need no cents.
        assertEquals("¥68", compactAmount(6800, "CNY"))
        // From 100 on: rounded half up, never truncated.
        assertEquals("¥312", compactAmount(31190, "CNY"))
        assertEquals("¥311", compactAmount(31149, "CNY"))
        assertEquals("¥1,217", compactAmount(121690, "CNY"))
        assertEquals("-¥312", compactAmount(-31190, "CNY"))
    }
}
