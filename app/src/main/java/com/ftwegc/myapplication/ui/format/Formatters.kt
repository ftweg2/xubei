package com.ftwegc.myapplication.ui.format

import com.ftwegc.myapplication.domain.calc.DueDateCalculator
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Binary byte units (1 KB = 1024 B), as used by VPS providers and Komari. */
enum class ByteUnit(val label: String) { B("B"), KB("KB"), MB("MB"), GB("GB"), TB("TB"), PB("PB") }

/** A formatted byte count split into number and unit, so the unit can be drawn smaller. */
data class ByteParts(val value: String, val unit: ByteUnit) {
    override fun toString(): String = "$value ${unit.label}"
}

/**
 * A formatted amount split for display: [symbol] (small, top-left), [integer] (large, grouped
 * with ","), [fraction] (small, always two digits).
 */
data class AmountParts(val symbol: String, val integer: String, val fraction: String, val negative: Boolean = false) {
    /** Plain one-line form, for example "¥1,234.50". */
    override fun toString(): String = buildString {
        if (negative) append('-')
        append(symbol)
        append(integer)
        append('.')
        append(fraction)
    }
}

/** Remaining-time classification of a due date; the UI maps it to a string resource. */
sealed interface DueLabel {
    /** Due in [days] (>= 1) days. */
    data class InDays(val days: Long) : DueLabel

    data object Today : DueLabel

    /** Expired [days] (>= 1) days ago. */
    data class Overdue(val days: Long) : DueLabel

    data object Permanent : DueLabel

    data object NoDate : DueLabel
}

/** How long ago something happened, classified for display ([Formatters.relativeTime]). */
sealed interface RelativeTime {
    /** Less than a minute ago (or slightly in the future: clock skew). */
    data object JustNow : RelativeTime

    data class MinutesAgo(val minutes: Long) : RelativeTime

    data class HoursAgo(val hours: Long) : RelativeTime

    /** Yesterday at [time]. */
    data class Yesterday(val time: LocalDateTime) : RelativeTime

    /** Earlier: an absolute date and time; [sameYear] picks the shorter pattern. */
    data class At(val time: LocalDateTime, val sameYear: Boolean) : RelativeTime
}

/** Size units offered in the traffic limit input. */
enum class SizeUnit(val bytes: Long) {
    GB(1L shl 30),
    TB(1L shl 40),
}

/** Pure display formatting. No Android dependencies; strings with words live in resources. */
object Formatters {

    private const val BYTE_STEP = 1024L
    private val BYTE_STEP_DECIMAL = BigDecimal(BYTE_STEP)
    private const val MAX_PRICE_MINOR = 99_999_999_999L // 999,999,999.99

    /** Larger percentages are shown as this (keeps the tenths inside a Long). */
    private const val MAX_PERCENT = 1.0e12

    // ------------------------------------------------------------------ Bytes

    /**
     * Picks the largest unit that keeps the value below 1024 and formats it with 2 decimals
     * below 10, 1 decimal below 100, none otherwise (fixed width while counting).
     * Bytes (< 1 KB) never show decimals. Negative input is treated as 0.
     */
    fun byteParts(bytes: Long): ByteParts {
        val safe = bytes.coerceAtLeast(0)
        if (safe < BYTE_STEP) return ByteParts(safe.toString(), ByteUnit.B)
        var unitIndex = 0
        var value = BigDecimal(safe)
        while (value >= BYTE_STEP_DECIMAL && unitIndex < ByteUnit.entries.lastIndex) {
            value = value.divide(BYTE_STEP_DECIMAL)
            unitIndex++
        }
        var rounded = roundForDisplay(value)
        // 1023.996 KB rounds to "1024": show the next unit instead.
        if (rounded >= BYTE_STEP_DECIMAL && unitIndex < ByteUnit.entries.lastIndex) {
            value = value.divide(BYTE_STEP_DECIMAL)
            unitIndex++
            rounded = roundForDisplay(value)
        }
        return ByteParts(rounded.toPlainString(), ByteUnit.entries[unitIndex])
    }

    /** "12.3 GB". */
    fun formatBytes(bytes: Long): String = byteParts(bytes).toString()

    private fun roundForDisplay(value: BigDecimal): BigDecimal {
        val scale = when {
            value < BigDecimal.TEN -> 2
            value < BigDecimal(100) -> 1
            else -> 0
        }
        return value.setScale(scale, RoundingMode.HALF_UP)
    }

    // ------------------------------------------------------------------ Percent

    /**
     * The one rounding step of every traffic percentage the app shows, in tenths of a percent:
     * one decimal below 10 %, whole percent from 10 % on, HALF_UP (30.46 → 300, 30.5 → 310,
     * 7.54 → 75, 9.96 → 100). NaN and negative values count as 0; not capped at 100.
     *
     * Animated numbers count on this value and format it with [formatPercentTenths], so they end
     * exactly on the text of [formatPercent] (rounding a pre-rounded value again would turn
     * 30.46 into 30.5 and then 31 %).
     */
    fun percentTenths(percent: Double): Long {
        val safe = when {
            percent.isNaN() || percent < 0 -> 0.0
            percent > MAX_PERCENT -> MAX_PERCENT
            else -> percent
        }
        val scale = if (safe < 10) 1 else 0
        return BigDecimal.valueOf(safe).setScale(scale, RoundingMode.HALF_UP).movePointRight(1).toLong()
    }

    /** Text of a value from [percentTenths] (or a count-up frame of it): "7.5%", "85%". */
    fun formatPercentTenths(tenths: Long): String {
        val value = BigDecimal.valueOf(tenths.coerceAtLeast(0), 1)
        val shown = if (tenths < 100) value else value.setScale(0, RoundingMode.HALF_UP)
        return shown.stripTrailingZeros().toPlainString() + "%"
    }

    /** "7.5%" below 10, "85%" otherwise; not capped (a value of 112.4 gives "112%"). */
    fun formatPercent(percent: Double): String = formatPercentTenths(percentTenths(percent))

    // ------------------------------------------------------------------ Names

    /** Zero-width space: an invisible line break opportunity. */
    private const val BREAK_OPPORTUNITY = '​'

    /** Characters after which a long name may wrap (Android does not wrap after "-" before letters or digits). */
    private const val NAME_BREAK_AFTER = "-_/.·:|+"

    /**
     * [name] for a multi-line title: an invisible break opportunity after separators, so
     * "Auto-C202609280075129" wraps as "Auto-" / "C202609280075129" instead of in the middle of
     * the number. Only for display; the text looks the same.
     */
    fun wrappableName(name: String): String {
        if (name.none { it in NAME_BREAK_AFTER }) return name
        val out = StringBuilder(name.length + 4)
        name.forEachIndexed { i, c ->
            out.append(c)
            val next = name.getOrNull(i + 1)
            // "1.5TB" and "10:30" stay whole.
            val inNumber = (c == '.' || c == ':') && name.getOrNull(i - 1)?.isDigit() == true && next?.isDigit() == true
            if (c in NAME_BREAK_AFTER && next != null && !next.isWhitespace() && next !in NAME_BREAK_AFTER && !inNumber) {
                out.append(BREAK_OPPORTUNITY)
            }
        }
        return out.toString()
    }

    /**
     * Display width budget of a name inside a notification title (a CJK character counts 2): what
     * fits in a row of an expanded group next to its time, with "还有 1 天到期" still readable.
     */
    const val NOTIFICATION_NAME_WIDTH: Int = 16

    /**
     * [name] cut to [maxWidth] (CJK characters count 2, others 1) with "…", so the rest of a
     * one-line title ("还有 3 天到期", "流量已用 85%") stays visible.
     */
    fun shortName(name: String, maxWidth: Int = NOTIFICATION_NAME_WIDTH): String {
        val trimmed = name.trim()
        if (displayWidth(trimmed) <= maxWidth) return trimmed
        val out = StringBuilder()
        var width = 0
        var i = 0
        while (i < trimmed.length) {
            val cp = trimmed.codePointAt(i)
            val w = if (isWide(cp)) 2 else 1
            if (width + w > maxWidth - 1) break
            out.appendCodePoint(cp)
            width += w
            i += Character.charCount(cp)
        }
        return out.toString().trimEnd() + "…"
    }

    private fun displayWidth(text: String): Int {
        var width = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            width += if (isWide(cp)) 2 else 1
            i += Character.charCount(cp)
        }
        return width
    }

    /** CJK ideographs, kana, hangul, full-width forms and emoji take two columns. */
    private fun isWide(cp: Int): Boolean =
        cp in 0x1100..0x115F || cp in 0x2E80..0xA4CF || cp in 0xAC00..0xD7A3 || cp in 0xF900..0xFAFF ||
            cp in 0xFE30..0xFE4F || cp in 0xFF00..0xFF60 || cp in 0xFFE0..0xFFE6 || cp in 0x1F300..0x1FAFF || cp in 0x20000..0x3FFFD

    // ------------------------------------------------------------------ Money

    /**
     * Display symbol for a free-form currency label: known ISO codes map to their symbol
     * (CNY → ¥, USD → $ …), anything else is shown trimmed as typed ("USDT").
     */
    fun currencySymbol(currency: String): String {
        val key = currency.trim()
        return CURRENCY_SYMBOLS[key.uppercase(Locale.ROOT)] ?: key
    }

    /** Splits [minor] units into symbol / grouped integer / two-digit fraction. */
    fun amountParts(minor: Long, currency: String): AmountParts {
        val negative = minor < 0
        val abs = if (minor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(minor)
        val integer = groupThousands((abs / 100).toString())
        val fraction = (abs % 100).toString().padStart(2, '0')
        return AmountParts(currencySymbol(currency), integer, fraction, negative)
    }

    /** "¥1,234.50". */
    fun formatAmount(minor: Long, currency: String): String = amountParts(minor, currency).toString()

    /** Inserts "," every three digits of a plain digit string. */
    fun groupThousands(digits: String): String {
        if (digits.length <= 3) return digits
        val out = StringBuilder(digits.length + digits.length / 3)
        val first = digits.length % 3
        if (first > 0) out.append(digits, 0, first)
        var i = first
        while (i < digits.length) {
            if (out.isNotEmpty()) out.append(',')
            out.append(digits, i, i + 3)
            i += 3
        }
        return out.toString()
    }

    /**
     * Parses a user-typed price into minor units. Accepts digits with an optional "." or "。"
     * and up to two decimals; "," grouping and surrounding spaces are ignored.
     * Returns `null` for empty, negative, malformed, more than two decimals, or too large values.
     */
    fun parseAmountToMinor(input: String): Long? {
        val text = input.trim().replace(",", "").replace("，", "").replace('。', '.')
        if (text.isEmpty() || !AMOUNT_REGEX.matches(text)) return null
        val value = text.toBigDecimalOrNull() ?: return null
        if (value.scale() > 2) return null
        val minor = value.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY)
        if (minor > BigDecimal.valueOf(MAX_PRICE_MINOR)) return null
        return minor.longValueExact()
    }

    /** Minor units back to an editable string: "12" for whole amounts, else "12.50". */
    fun minorToInput(minor: Long): String {
        val safe = minor.coerceAtLeast(0)
        return if (safe % 100 == 0L) (safe / 100).toString()
        else "${safe / 100}.${(safe % 100).toString().padStart(2, '0')}"
    }

    // ------------------------------------------------------------------ Traffic size input

    /**
     * Parses a traffic limit typed in [unit] ("1.5" TB) into bytes. Returns `null` when empty,
     * malformed, not positive, or too large.
     */
    fun parseSizeToBytes(input: String, unit: SizeUnit): Long? {
        val text = input.trim().replace(",", "").replace('。', '.')
        if (text.isEmpty() || !AMOUNT_REGEX_ANY_SCALE.matches(text)) return null
        val value = text.toBigDecimalOrNull() ?: return null
        if (value.signum() <= 0) return null
        val bytes = value.multiply(BigDecimal(unit.bytes)).setScale(0, RoundingMode.HALF_UP)
        if (bytes > BigDecimal.valueOf(Long.MAX_VALUE)) return null
        return bytes.longValueExact().takeIf { it > 0 }
    }

    /**
     * Bytes back to an editable value and unit: TB when the limit is at least 1 TB, else GB;
     * up to three decimals, trailing zeros stripped. `0` gives an empty GB field.
     */
    fun bytesToSizeInput(bytes: Long): Pair<String, SizeUnit> {
        if (bytes <= 0) return "" to SizeUnit.GB
        val unit = if (bytes >= SizeUnit.TB.bytes) SizeUnit.TB else SizeUnit.GB
        val value = BigDecimal(bytes).divide(BigDecimal(unit.bytes), 3, RoundingMode.HALF_UP).stripTrailingZeros()
        return value.toPlainString() to unit
    }

    // ------------------------------------------------------------------ Dates

    /**
     * Formats [date] with [sameYearPattern] when it is in [today]'s year, else [fullPattern]
     * (patterns come from string resources, for example "M月d日" / "yyyy年M月d日").
     */
    fun formatDate(date: LocalDate, today: LocalDate, sameYearPattern: String, fullPattern: String): String {
        val pattern = if (date.year == today.year) sameYearPattern else fullPattern
        return DateTimeFormatter.ofPattern(pattern, Locale.CHINA).format(date)
    }

    /** Remaining-days classification of [due] relative to [today]. */
    fun dueLabel(due: LocalDate?, today: LocalDate, permanent: Boolean): DueLabel {
        if (permanent) return DueLabel.Permanent
        if (due == null) return DueLabel.NoDate
        val days = DueDateCalculator.daysUntil(due, today)
        return when {
            days > 0 -> DueLabel.InDays(days)
            days == 0L -> DueLabel.Today
            else -> DueLabel.Overdue(-days)
        }
    }

    // ------------------------------------------------------------------ Relative time

    /**
     * Classifies [at] relative to [now] in [zone]: under a minute (or up to 5 minutes in the
     * future) → just now; under an hour → minutes; same local day → hours; the previous local day →
     * yesterday; otherwise an absolute time.
     */
    fun relativeTime(at: Instant, now: Instant, zone: ZoneId): RelativeTime {
        val seconds = Duration.between(at, now).seconds
        if (seconds < 60) {
            return if (seconds >= -300) RelativeTime.JustNow else RelativeTime.At(LocalDateTime.ofInstant(at, zone), sameYear(at, now, zone))
        }
        val local = LocalDateTime.ofInstant(at, zone)
        val today = now.atZone(zone).toLocalDate()
        return when {
            seconds < 3_600 -> RelativeTime.MinutesAgo(seconds / 60)
            local.toLocalDate() == today -> RelativeTime.HoursAgo(seconds / 3_600)
            local.toLocalDate() == today.minusDays(1) -> RelativeTime.Yesterday(local)
            else -> RelativeTime.At(local, sameYear(at, now, zone))
        }
    }

    private fun sameYear(a: Instant, b: Instant, zone: ZoneId): Boolean = a.atZone(zone).year == b.atZone(zone).year

    private val AMOUNT_REGEX = Regex("""\d+(\.\d*)?|\.\d+""")
    private val AMOUNT_REGEX_ANY_SCALE = AMOUNT_REGEX

    private val CURRENCY_SYMBOLS: Map<String, String> = mapOf(
        "CNY" to "¥",
        "RMB" to "¥",
        "JPY" to "¥",
        "USD" to "$",
        "EUR" to "€",
        "GBP" to "£",
        "HKD" to "HK$",
        "TWD" to "NT$",
        "SGD" to "S$",
        "AUD" to "A$",
        "CAD" to "C$",
        "KRW" to "₩",
        "INR" to "₹",
        "RUB" to "₽",
    )

    /** Currencies offered as quick picks in the form, in display order. */
    val COMMON_CURRENCIES: List<String> = listOf("CNY", "USD", "EUR", "HKD", "JPY", "GBP")
}
