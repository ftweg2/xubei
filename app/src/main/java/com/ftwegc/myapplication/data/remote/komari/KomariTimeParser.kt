package com.ftwegc.myapplication.data.remote.komari

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

/**
 * Lenient parser for the time values Komari has used over its versions.
 *
 * Tried in order:
 * 1. RFC 3339 with offset (`Z` or `±hh:mm`), with or without a fraction of up to 9 digits
 *    (current Komari serializes Go `time.Time`, e.g. `2026-07-17T01:02:03.123456789Z`); a space
 *    instead of `T` is accepted.
 * 2. No offset: `yyyy-MM-dd HH:mm:ss` and `yyyy-MM-dd'T'HH:mm:ss` (optional fraction), read in
 *    [localZone].
 * 3. Date only `yyyy-MM-dd`, start of that day in [localZone] (so the local date is unchanged).
 * 4. Digits only: up to 10 digits are epoch seconds, 11 to 13 digits epoch milliseconds.
 *
 * Returns `null` for `null`, blank, `"null"` or anything unparseable; never throws.
 * The Go zero time `0001-01-01T00:00:00Z` parses normally; callers decide what it means.
 */
object KomariTimeParser {

    private val LOCAL_DATE_TIME: DateTimeFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd")
        .optionalStart().appendLiteral('T').optionalEnd()
        .optionalStart().appendLiteral(' ').optionalEnd()
        .appendPattern("HH:mm:ss")
        .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
        .toFormatter()

    private val DIGITS = Regex("""\d{1,13}""")

    fun parse(raw: String?, localZone: ZoneId): Instant? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.equals("null", ignoreCase = true)) return null
        return parseOffset(text)
            ?: parseLocalDateTime(text, localZone)
            ?: parseDate(text, localZone)
            ?: parseEpoch(text)
    }

    private fun parseOffset(text: String): Instant? {
        val iso = if (text.length > 10 && text[10] == ' ') text.substring(0, 10) + "T" + text.substring(11) else text
        return try {
            OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun parseLocalDateTime(text: String, zone: ZoneId): Instant? = try {
        LocalDateTime.parse(text, LOCAL_DATE_TIME).atZone(zone).toInstant()
    } catch (_: DateTimeException) {
        null
    }

    private fun parseDate(text: String, zone: ZoneId): Instant? = try {
        LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(zone).toInstant()
    } catch (_: DateTimeException) {
        null
    }

    private fun parseEpoch(text: String): Instant? {
        if (!DIGITS.matches(text)) return null
        val value = text.toLongOrNull() ?: return null
        return try {
            if (text.length <= 10) Instant.ofEpochSecond(value) else Instant.ofEpochMilli(value)
        } catch (_: DateTimeException) {
            null
        }
    }
}
