package com.ftwegc.myapplication.ui.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FormattersTest {

    private val kib = 1024L
    private val mib = kib * 1024
    private val gib = mib * 1024
    private val tib = gib * 1024

    // ------------------------------------------------------------ bytes

    @Test
    fun bytes_belowOneKb_areWholeBytes() {
        assertEquals("0 B", Formatters.formatBytes(0))
        assertEquals("1 B", Formatters.formatBytes(1))
        assertEquals("1023 B", Formatters.formatBytes(1023))
    }

    @Test
    fun bytes_negative_treatedAsZero() {
        assertEquals("0 B", Formatters.formatBytes(-5))
    }

    @Test
    fun bytes_pickLargestUnitBelow1024() {
        assertEquals("1.00 KB", Formatters.formatBytes(kib))
        assertEquals("1.50 MB", Formatters.formatBytes(mib + mib / 2))
        assertEquals("12.3 GB", Formatters.formatBytes((12.3 * gib).toLong()))
        assertEquals("512 GB", Formatters.formatBytes(512 * gib))
        assertEquals("1.00 TB", Formatters.formatBytes(tib))
        assertEquals("2.50 TB", Formatters.formatBytes(tib * 5 / 2))
    }

    @Test
    fun bytes_decimalsShrinkAsValueGrows() {
        assertEquals("9.99 GB", Formatters.formatBytes((9.99 * gib).toLong() + 1))
        assertEquals("99.5 GB", Formatters.formatBytes((99.5 * gib).toLong()))
        assertEquals("100 GB", Formatters.formatBytes(100 * gib))
    }

    @Test
    fun bytes_roundingTo1024_promotesToNextUnit() {
        // 1023.9 GB would round to "1024 GB"; show TB instead.
        assertEquals("1.00 TB", Formatters.formatBytes(tib - gib / 10))
        assertEquals("1.00 MB", Formatters.formatBytes(mib - 1))
    }

    @Test
    fun bytes_hugeValues_stayInPetabytes() {
        assertEquals("PB", Formatters.byteParts(Long.MAX_VALUE).unit.label)
        assertEquals("8192", Formatters.byteParts(Long.MAX_VALUE).value)
    }

    @Test
    fun byteParts_splitsValueAndUnit() {
        val parts = Formatters.byteParts(853 * gib)
        assertEquals("853", parts.value)
        assertEquals(ByteUnit.GB, parts.unit)
    }

    // ------------------------------------------------------------ percent

    @Test
    fun percent_formats() {
        assertEquals("0%", Formatters.formatPercent(0.0))
        assertEquals("7.5%", Formatters.formatPercent(7.46))
        assertEquals("85%", Formatters.formatPercent(85.2))
        assertEquals("112%", Formatters.formatPercent(112.4))
        assertEquals("0%", Formatters.formatPercent(Double.NaN))
        assertEquals("0%", Formatters.formatPercent(-3.0))
    }

    @Test
    fun percent_ringAndBarShowTheSameNumber() {
        // The reported case: 30.46 % showed "31%" in the animated ring (rounded to 30.5 first,
        // then to 31) and "30%" on the card. Both now go through percentTenths once.
        val usage = 32_709_413_000.0 * 100 / (100L shl 30) // 30.46 %
        assertEquals("30%", Formatters.formatPercent(usage))
        assertEquals(300L, Formatters.percentTenths(usage))
        assertEquals("30%", Formatters.formatPercentTenths(Formatters.percentTenths(usage)))
    }

    /** The display rule, written out independently: one decimal below 10 %, HALF_UP once. */
    private fun referencePercent(p: Double): String {
        val safe = if (p.isNaN() || p < 0) 0.0 else p
        val scale = if (safe < 10) 1 else 0
        return java.math.BigDecimal.valueOf(safe).setScale(scale, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%"
    }

    @Test
    fun percent_animatedAndStaticTextAgreeWithTheRule() {
        val samples = listOf(0.0, 0.04, 0.05, 7.46, 7.45, 9.94, 9.95, 9.96, 10.0, 10.49, 10.5, 30.46, 30.5, 79.5, 99.5, 99.96, 100.0, 112.4, Double.NaN, -1.0)
        // Every value between 0 and 150 % in steps of 0.001 as well.
        val all = samples + (0..150_000).map { it / 1000.0 }
        for (p in all) {
            val expected = referencePercent(p)
            // Card / bar / notification text.
            assertEquals("p=$p", expected, Formatters.formatPercent(p))
            // Where the ring's and the overview's count-up ends.
            assertEquals("p=$p", expected, Formatters.formatPercentTenths(Formatters.percentTenths(p)))
        }
    }

    // ------------------------------------------------------------ names

    @Test
    fun wrappableName_addsInvisibleBreaksAfterSeparators() {
        val zw = '​'
        assertEquals("Auto-${zw}C202609280075129", Formatters.wrappableName("Auto-C202609280075129"))
        assertEquals("甲骨文-${zw}us-${zw}129", Formatters.wrappableName("甲骨文-us-129"))
        assertEquals("a_${zw}b/${zw}c", Formatters.wrappableName("a_b/c"))
        // Looks the same: removing the breaks gives the name back.
        val mixed = "DMIT 洛杉矶 PremiumCN2GIA-Ultra-Performance 年付"
        assertEquals(mixed, Formatters.wrappableName(mixed).replace(zw.toString(), ""))
    }

    @Test
    fun wrappableName_leavesNumbersSpacesAndPlainNamesAlone() {
        assertEquals("ChatGPT Plus", Formatters.wrappableName("ChatGPT Plus"))
        assertEquals("1.5TB 10:30", Formatters.wrappableName("1.5TB 10:30"))
        assertEquals("a - b", Formatters.wrappableName("a - b"))
        assertEquals("--​x", Formatters.wrappableName("--x"))
        assertEquals("end-", Formatters.wrappableName("end-"))
    }

    @Test
    fun shortName_cutsLongNamesByDisplayWidth() {
        assertEquals("ChatGPT", Formatters.shortName(" ChatGPT "))
        assertEquals("甲骨文-us-129", Formatters.shortName("甲骨文-us-129")) // 13 columns fit
        assertEquals("Auto-C202609280…", Formatters.shortName("Auto-C202609280075129"))
        val long = Formatters.shortName("甲骨文云 Oracle Cloud Infrastructure 首尔 Ampere A1")
        assertTrue(long, long.endsWith("…"))
        assertEquals("甲骨文云 Oracle…", long)
        // 8 CJK characters are 16 columns: they fit; 9 do not.
        assertEquals("一二三四五六七八", Formatters.shortName("一二三四五六七八"))
        assertEquals("一二三四五六七…", Formatters.shortName("一二三四五六七八九"))
        assertEquals(24, Formatters.shortName("x".repeat(40), maxWidth = 24).length)
    }

    @Test
    fun percent_tenthsRoundOnceHalfUp() {
        assertEquals(75L, Formatters.percentTenths(7.46))
        assertEquals(100L, Formatters.percentTenths(9.96))
        assertEquals(300L, Formatters.percentTenths(30.46))
        assertEquals(310L, Formatters.percentTenths(30.5))
        assertEquals(0L, Formatters.percentTenths(Double.NaN))
        assertEquals("9.9%", Formatters.formatPercentTenths(99))
        assertEquals("10%", Formatters.formatPercentTenths(100))
        // A count-up frame between two whole percentages.
        assertEquals("31%", Formatters.formatPercentTenths(305))
    }

    // ------------------------------------------------------------ money

    @Test
    fun currencySymbol_knownCodesAndFreeForm() {
        assertEquals("¥", Formatters.currencySymbol("CNY"))
        assertEquals("¥", Formatters.currencySymbol(" cny "))
        assertEquals("$", Formatters.currencySymbol("USD"))
        assertEquals("€", Formatters.currencySymbol("eur"))
        assertEquals("HK$", Formatters.currencySymbol("HKD"))
        assertEquals("USDT", Formatters.currencySymbol(" USDT "))
        assertEquals("$", Formatters.currencySymbol("$"))
    }

    @Test
    fun amountParts_splitsAndGroups() {
        assertEquals(AmountParts("¥", "1,234", "50"), Formatters.amountParts(123_450, "CNY"))
        assertEquals(AmountParts("$", "0", "05"), Formatters.amountParts(5, "USD"))
        assertEquals(AmountParts("€", "1,000,000", "00"), Formatters.amountParts(100_000_000, "EUR"))
    }

    @Test
    fun formatAmount_oneLine() {
        assertEquals("¥1,234.50", Formatters.formatAmount(123_450, "CNY"))
        assertEquals("$20.00", Formatters.formatAmount(2_000, "USD"))
        assertEquals("-¥0.01", Formatters.formatAmount(-1, "CNY"))
    }

    @Test
    fun groupThousands() {
        assertEquals("0", Formatters.groupThousands("0"))
        assertEquals("999", Formatters.groupThousands("999"))
        assertEquals("1,000", Formatters.groupThousands("1000"))
        assertEquals("12,345", Formatters.groupThousands("12345"))
        assertEquals("123,456,789", Formatters.groupThousands("123456789"))
    }

    @Test
    fun parseAmount_valid() {
        assertEquals(0L, Formatters.parseAmountToMinor("0"))
        assertEquals(1_200L, Formatters.parseAmountToMinor("12"))
        assertEquals(1_250L, Formatters.parseAmountToMinor("12.5"))
        assertEquals(1_205L, Formatters.parseAmountToMinor("12.05"))
        assertEquals(1_200L, Formatters.parseAmountToMinor("12."))
        assertEquals(50L, Formatters.parseAmountToMinor(".5"))
        assertEquals(123_456L, Formatters.parseAmountToMinor(" 1,234.56 "))
        assertEquals(990L, Formatters.parseAmountToMinor("9。9"))
    }

    @Test
    fun parseAmount_invalid() {
        assertNull(Formatters.parseAmountToMinor(""))
        assertNull(Formatters.parseAmountToMinor("  "))
        assertNull(Formatters.parseAmountToMinor("-1"))
        assertNull(Formatters.parseAmountToMinor("12.345"))
        assertNull(Formatters.parseAmountToMinor("abc"))
        assertNull(Formatters.parseAmountToMinor("1.2.3"))
        assertNull(Formatters.parseAmountToMinor("."))
        assertNull(Formatters.parseAmountToMinor("1e5"))
        assertNull(Formatters.parseAmountToMinor("99999999999999"))
    }

    @Test
    fun minorToInput_roundTrips() {
        assertEquals("12", Formatters.minorToInput(1_200))
        assertEquals("12.50", Formatters.minorToInput(1_250))
        assertEquals("0.05", Formatters.minorToInput(5))
        for (minor in listOf(0L, 1L, 99L, 100L, 1_250L, 999_999L)) {
            assertEquals(minor, Formatters.parseAmountToMinor(Formatters.minorToInput(minor)))
        }
    }

    // ------------------------------------------------------------ traffic size input

    @Test
    fun parseSize_gbAndTb() {
        assertEquals(500 * gib, Formatters.parseSizeToBytes("500", SizeUnit.GB))
        assertEquals(tib + tib / 2, Formatters.parseSizeToBytes("1.5", SizeUnit.TB))
        assertEquals(gib / 2, Formatters.parseSizeToBytes("0.5", SizeUnit.GB))
    }

    @Test
    fun parseSize_invalid() {
        assertNull(Formatters.parseSizeToBytes("", SizeUnit.GB))
        assertNull(Formatters.parseSizeToBytes("0", SizeUnit.GB))
        assertNull(Formatters.parseSizeToBytes("-1", SizeUnit.TB))
        assertNull(Formatters.parseSizeToBytes("x", SizeUnit.TB))
        assertNull(Formatters.parseSizeToBytes("99999999999", SizeUnit.TB))
    }

    @Test
    fun bytesToSizeInput_choosesUnit_andRoundTrips() {
        assertEquals("" to SizeUnit.GB, Formatters.bytesToSizeInput(0))
        assertEquals("500" to SizeUnit.GB, Formatters.bytesToSizeInput(500 * gib))
        assertEquals("1" to SizeUnit.TB, Formatters.bytesToSizeInput(tib))
        assertEquals("1.5" to SizeUnit.TB, Formatters.bytesToSizeInput(tib + tib / 2))
        for (bytes in listOf(gib, 200 * gib, tib, 3 * tib)) {
            val (value, unit) = Formatters.bytesToSizeInput(bytes)
            assertEquals(bytes, Formatters.parseSizeToBytes(value, unit))
        }
    }

    // ------------------------------------------------------------ dates

    @Test
    fun formatDate_sameYearOmitsYear() {
        val today = LocalDate.of(2026, 9, 28)
        assertEquals("10月5日", Formatters.formatDate(LocalDate.of(2026, 10, 5), today, "M月d日", "yyyy年M月d日"))
        assertEquals("2027年1月31日", Formatters.formatDate(LocalDate.of(2027, 1, 31), today, "M月d日", "yyyy年M月d日"))
        assertEquals("2025年12月31日", Formatters.formatDate(LocalDate.of(2025, 12, 31), today, "M月d日", "yyyy年M月d日"))
    }

    @Test
    fun dueLabel_classifiesRemainingDays() {
        val today = LocalDate.of(2026, 9, 28)
        assertEquals(DueLabel.InDays(3), Formatters.dueLabel(LocalDate.of(2026, 10, 1), today, permanent = false))
        assertEquals(DueLabel.InDays(1), Formatters.dueLabel(LocalDate.of(2026, 9, 29), today, permanent = false))
        assertEquals(DueLabel.Today, Formatters.dueLabel(today, today, permanent = false))
        assertEquals(DueLabel.Overdue(1), Formatters.dueLabel(LocalDate.of(2026, 9, 27), today, permanent = false))
        assertEquals(DueLabel.Overdue(365), Formatters.dueLabel(LocalDate.of(2025, 9, 28), today, permanent = false))
        assertEquals(DueLabel.NoDate, Formatters.dueLabel(null, today, permanent = false))
        assertEquals(DueLabel.Permanent, Formatters.dueLabel(today, today, permanent = true))
    }

    @Test
    fun dueLabel_crossesYearBoundary() {
        val today = LocalDate.of(2026, 12, 30)
        assertEquals(DueLabel.InDays(3), Formatters.dueLabel(LocalDate.of(2027, 1, 2), today, permanent = false))
    }
}
