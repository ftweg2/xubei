package com.ftwegc.myapplication.data.remote.komari

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class KomariTimeParserTest {

    private val shanghai = ZoneId.of("Asia/Shanghai")
    private fun parse(s: String?, zone: ZoneId = ZoneOffset.UTC) = KomariTimeParser.parse(s, zone)

    @Test
    fun rfc3339_variants() {
        val t = Instant.parse("2026-10-15T08:00:00Z")
        assertEquals(t, parse("2026-10-15T08:00:00Z"))
        assertEquals(t, parse("2026-10-15T16:00:00+08:00"))
        assertEquals(t, parse("2026-10-15T03:00:00-05:00"))
        assertEquals(t, parse("2026-10-15 08:00:00Z"))
        assertEquals(t, parse("  2026-10-15T08:00:00Z  "))
    }

    @Test
    fun rfc3339_fractions_upToNanos() {
        assertEquals(Instant.parse("2026-07-17T01:02:03.123456789Z"), parse("2026-07-17T01:02:03.123456789Z"))
        assertEquals(Instant.parse("2026-07-17T01:02:03.5Z"), parse("2026-07-17T01:02:03.5Z"))
        assertEquals(Instant.parse("2026-07-16T17:02:03.120Z"), parse("2026-07-17T01:02:03.12+08:00"))
    }

    @Test
    fun noOffset_isReadInLocalZone() {
        assertEquals(Instant.parse("2026-10-15T00:00:00Z"), parse("2026-10-15 08:00:00", shanghai))
        assertEquals(Instant.parse("2026-10-15T00:00:00Z"), parse("2026-10-15T08:00:00", shanghai))
        assertEquals(Instant.parse("2026-10-15T08:00:00.250Z"), parse("2026-10-15T08:00:00.25"))
    }

    @Test
    fun dateOnly_isStartOfLocalDay() {
        assertEquals(Instant.parse("2026-10-14T16:00:00Z"), parse("2026-10-15", shanghai))
        assertEquals(Instant.parse("2026-10-15T00:00:00Z"), parse("2026-10-15"))
    }

    @Test
    fun epochDigits() {
        assertEquals(Instant.ofEpochSecond(1_792_051_200), parse("1792051200"))
        assertEquals(Instant.ofEpochMilli(1_792_051_200_123), parse("1792051200123"))
        assertEquals(Instant.EPOCH, parse("0"))
        assertNull("too many digits", parse("17920512001234"))
    }

    @Test
    fun goZeroTime_parses() {
        assertEquals(Instant.parse("0001-01-01T00:00:00Z"), parse("0001-01-01T00:00:00Z"))
    }

    @Test
    fun junk_isNull() {
        listOf(null, "", "   ", "null", "NULL", "tomorrow", "2026-13-40", "2026/10/15", "-5").forEach {
            assertNull("'$it'", parse(it))
        }
    }
}

class KomariUrlTest {

    @Test
    fun addsHttpsWhenSchemeMissing() {
        assertEquals("https://status.example.com", KomariUrl.normalize("status.example.com"))
        assertEquals("https://1.2.3.4:25774", KomariUrl.normalize("1.2.3.4:25774"))
    }

    @Test
    fun trimsWhitespace_slashes_andApiSuffix() {
        assertEquals("https://a.com", KomariUrl.normalize("  https://a.com/  "))
        assertEquals("https://a.com", KomariUrl.normalize("https://a.com/api"))
        assertEquals("https://a.com", KomariUrl.normalize("https://a.com/api/"))
        assertEquals("https://a.com", KomariUrl.normalize("https://a.com//api//"))
        assertEquals("https://a.com", KomariUrl.normalize("https://a.com/API"))
        assertEquals("https://a.com", KomariUrl.normalize("https://a.com/api/api"))
        assertEquals("https://a.com", KomariUrl.normalize("https:// a.com /"))
    }

    @Test
    fun keepsSubPath_portAndHttp() {
        assertEquals("https://a.com/komari", KomariUrl.normalize("https://a.com/komari/"))
        assertEquals("https://a.com/komari", KomariUrl.normalize("https://a.com/komari/api"))
        assertEquals("http://10.0.2.2:8765", KomariUrl.normalize("HTTP://10.0.2.2:8765/api/"))
        assertEquals("https://a.com/apis", KomariUrl.normalize("https://a.com/apis"))
    }

    @Test
    fun dropsQueryFragmentAndUserInfo() {
        assertEquals("https://a.com/x", KomariUrl.normalize("https://user:pw@a.com/x/?q=1#frag"))
    }

    @Test
    fun rejectsInvalid() {
        listOf("", "   ", "ftp://a.com", "javascript:alert(1)", "http://", "https://exa mple .com:99999", "://a.com").forEach {
            assertNull("'$it'", KomariUrl.normalize(it))
        }
    }

    @Test
    fun cleartextDetection() {
        assertTrue(KomariUrl.isCleartext("http://10.0.2.2:8765"))
        assertFalse(KomariUrl.isCleartext("https://a.com"))
        assertFalse(KomariUrl.isCleartext(null))
    }
}

class KomariJsonTest {

    @Test
    fun unwrap_successEnvelope() {
        val r = KomariJson.unwrap("""{"status":"success","message":"","data":[1,2]}""")
        assertTrue((r as KomariResult.Ok).value is JsonArray)
    }

    @Test
    fun unwrap_bareArray_andObjectWithoutStatus() {
        assertTrue((KomariJson.unwrap("[]") as KomariResult.Ok).value is JsonArray)
        assertTrue((KomariJson.unwrap("""{"version":"1"}""") as KomariResult.Ok).value is JsonObject)
    }

    @Test
    fun unwrap_missingData_isNull() {
        assertNull((KomariJson.unwrap("""{"status":"success","message":""}""") as KomariResult.Ok).value)
        assertNull((KomariJson.unwrap("""{"status":"SUCCESS","data":null}""") as KomariResult.Ok).value)
    }

    @Test
    fun unwrap_errorStatus() {
        assertEquals(
            KomariResult.Err(KomariError.ApiFailure("nope")),
            KomariJson.unwrap("""{"status":"error","message":"  nope "}"""),
        )
        assertEquals(KomariResult.Err(KomariError.ApiFailure("")), KomariJson.unwrap("""{"status":"fail"}"""))
    }

    @Test
    fun unwrap_notJson_andScalarRoot() {
        assertEquals(KomariResult.Err(KomariError.NotKomari), KomariJson.unwrap("<html></html>"))
        assertEquals(KomariResult.Err(KomariError.NotKomari), KomariJson.unwrap(""))
        assertEquals(KomariResult.Err(KomariError.Parse), KomariJson.unwrap("42"))
    }

    @Test
    fun errorCodes_roundTrip() {
        val all = listOf(
            KomariError.NotConfigured, KomariError.InvalidUrl, KomariError.Unauthorized(false), KomariError.Unauthorized(true),
            KomariError.Forbidden, KomariError.NotFound, KomariError.Http(502), KomariError.NotKomari, KomariError.Parse,
        ) + KomariError.NetworkKind.entries.map { KomariError.Network(it) }
        all.forEach { assertEquals(it, KomariError.fromCode(it.code)) }
        assertEquals(KomariError.ApiFailure(""), KomariError.fromCode(KomariError.ApiFailure("x").code))
        assertNull(KomariError.fromCode("WHATEVER"))
        assertNull(KomariError.fromCode(null))
        assertEquals("all codes are distinct", all.size, all.map { it.code }.toSet().size)
    }

    @Test
    fun currencySymbols_mapToCodes() {
        assertEquals("USD", KomariCurrency.normalize("$"))
        assertEquals("CNY", KomariCurrency.normalize(" ¥ "))
        assertEquals("CNY", KomariCurrency.normalize("￥"))
        assertEquals("EUR", KomariCurrency.normalize("€"))
        assertEquals("HKD", KomariCurrency.normalize("hk$"))
        assertEquals("USDT", KomariCurrency.normalize("USDT"))
        assertEquals("", KomariCurrency.normalize(""))
    }

    @Test
    fun apiFailure_isTruncatedAndCollapsed() {
        assertEquals("a b", KomariError.apiFailure(" a \n\t b ").message)
        assertEquals(200, KomariError.apiFailure("y".repeat(1000)).message.length)
        assertEquals("", KomariError.apiFailure(null).message)
    }
}
