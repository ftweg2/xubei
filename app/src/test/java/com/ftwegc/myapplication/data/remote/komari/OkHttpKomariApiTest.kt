package com.ftwegc.myapplication.data.remote.komari

import com.ftwegc.myapplication.data.repo.KeyInput
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

class OkHttpKomariApiTest {

    private lateinit var server: MockWebServer
    private val client: OkHttpClient = OkHttpKomariApi.defaultClient()

    /** A made-up key used only by these tests. */
    private val key = "test-key-9f3c1a7e5b2d"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    /** Base URL spelled with "localhost" so that 127.0.0.1 counts as another host. */
    private fun baseUrl(s: MockWebServer = server) = "http://localhost:${s.port}"

    private fun api(apiKey: String? = null, http: OkHttpClient = client, base: String = baseUrl()) =
        OkHttpKomariApi(KomariEndpoint(base, apiKey), http, ZoneOffset.UTC)

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).setHeader("Content-Type", "application/json; charset=utf-8").body(body).build()

    private fun wrap(data: String) = """{"status":"success","message":"","data":$data}"""

    private fun <T> ok(result: KomariResult<T>): T {
        assertTrue("expected Ok, was $result", result is KomariResult.Ok)
        return (result as KomariResult.Ok).value
    }

    private fun err(result: KomariResult<*>): KomariError {
        assertTrue("expected Err, was $result", result is KomariResult.Err)
        return (result as KomariResult.Err).error
    }

    private val fullNode = """
        {"uuid":"n-1","name":"Tokyo","group":"asia","tags":"cn2;gia<green>","hidden":false,"weight":2,
         "public_remark":"hello","price":12.5,"billing_cycle":30,"currency":"¥","auto_renewal":true,
         "expired_at":"2026-10-15T08:00:00Z","traffic_limit":1099511627776,"traffic_limit_type":"sum",
         "cpu_name":"EPYC","mem_total":1024,"created_at":"2025-01-01T00:00:00Z"}
    """.trimIndent()

    // ------------------------------------------------------------------ nodes

    @Test
    fun nodes_wrappedResponse_parsesEveryField() = runBlocking {
        server.enqueue(json(wrap("[$fullNode]")))
        val list = ok(api().getNodes())
        assertEquals(0, list.droppedCount)
        val n = list.nodes.single()
        assertEquals("n-1", n.uuid)
        assertEquals("Tokyo", n.name)
        assertEquals("asia", n.group)
        assertEquals(listOf("cn2", "gia"), n.tags)
        assertEquals(2, n.weight)
        assertEquals("hello", n.publicRemark)
        assertEquals(12.5, n.price, 0.0)
        assertEquals(30, n.billingCycleDays)
        assertEquals("¥", n.currency)
        assertTrue(n.autoRenewal)
        assertEquals(Instant.parse("2026-10-15T08:00:00Z"), n.expiredAt)
        assertEquals(1L shl 40, n.trafficLimitBytes)
        assertEquals("sum", n.trafficLimitType)
        val request = server.takeRequest()
        assertEquals("/api/nodes", request.url.encodedPath)
        assertEquals("GET", request.method)
    }

    @Test
    fun nodes_bareArrayRoot_isData() = runBlocking {
        server.enqueue(json("[$fullNode]"))
        assertEquals("n-1", ok(api().getNodes()).nodes.single().uuid)
    }

    @Test
    fun nodes_nullOrMissingData_isEmptyList() = runBlocking {
        server.enqueue(json("""{"status":"success","message":"","data":null}"""))
        server.enqueue(json("""{"status":"success","message":""}"""))
        assertTrue(ok(api().getNodes()).nodes.isEmpty())
        assertTrue(ok(api().getNodes()).nodes.isEmpty())
    }

    @Test
    fun nodes_missingFields_useDefaults_andBlankUuidIsDropped() = runBlocking {
        server.enqueue(json(wrap("""[{"uuid":"only-id"},{"name":"no id"},{"uuid":"  "},"junk",{"uuid":"x2","name":null}]""")))
        val list = ok(api().getNodes())
        assertEquals(listOf("only-id", "x2"), list.nodes.map { it.uuid })
        assertEquals(3, list.droppedCount)
        val n = list.nodes.first()
        assertEquals("", n.name)
        assertEquals(0.0, n.price, 0.0)
        assertEquals(0, n.billingCycleDays)
        assertNull(n.expiredAt)
        assertEquals(0L, n.trafficLimitBytes)
        assertEquals("", n.trafficLimitType)
        assertFalse(n.autoRenewal)
        assertTrue(n.tags.isEmpty())
    }

    @Test
    fun nodes_unknownFieldsAreIgnored() = runBlocking {
        server.enqueue(json(wrap("""[{"uuid":"a","future_field":{"x":[1,2]},"another":true,"name":"A"}]"""), code = 200))
        assertEquals("A", ok(api().getNodes()).nodes.single().name)
    }

    @Test
    fun nodes_numbersAreLenient() = runBlocking {
        server.enqueue(
            json(
                wrap(
                    """[
                    {"uuid":"f","traffic_limit":1.5E12,"price":"9.99","billing_cycle":"90"},
                    {"uuid":"s","traffic_limit":"2048","price":-1,"billing_cycle":-1},
                    {"uuid":"n","traffic_limit":-100,"price":"abc","billing_cycle":30.7},
                    {"uuid":"t","tags":["a"," b ",""],"auto_renewal":"true","hidden":1}
                    ]""",
                ),
            ),
        )
        val nodes = ok(api().getNodes()).nodes.associateBy { it.uuid }
        assertEquals(1_500_000_000_000L, nodes.getValue("f").trafficLimitBytes)
        assertEquals(9.99, nodes.getValue("f").price, 0.0)
        assertEquals(90, nodes.getValue("f").billingCycleDays)
        assertEquals(2048L, nodes.getValue("s").trafficLimitBytes)
        assertEquals(-1.0, nodes.getValue("s").price, 0.0)
        assertEquals(-1, nodes.getValue("s").billingCycleDays)
        assertEquals("negative limit becomes 0", 0L, nodes.getValue("n").trafficLimitBytes)
        assertEquals(0.0, nodes.getValue("n").price, 0.0)
        assertEquals(30, nodes.getValue("n").billingCycleDays)
        assertEquals(listOf("a", "b"), nodes.getValue("t").tags)
        assertTrue(nodes.getValue("t").autoRenewal)
        assertTrue(nodes.getValue("t").hidden)
    }

    @Test
    fun nodes_expiredAtShapes() = runBlocking {
        server.enqueue(
            json(
                wrap(
                    """[
                    {"uuid":"z","expired_at":"2026-10-15T08:00:00Z"},
                    {"uuid":"o","expired_at":"2026-10-15T16:00:00+08:00"},
                    {"uuid":"ns","expired_at":"2026-07-17T01:02:03.123456789Z"},
                    {"uuid":"local","expired_at":"2026-10-15 08:00:00"},
                    {"uuid":"null","expired_at":null},
                    {"uuid":"zero","expired_at":"0001-01-01T00:00:00Z"},
                    {"uuid":"sec","expired_at":1792051200},
                    {"uuid":"junk","expired_at":"soon"}
                    ]""",
                ),
            ),
        )
        val nodes = ok(api().getNodes()).nodes.associateBy { it.uuid }
        val oct15 = Instant.parse("2026-10-15T08:00:00Z")
        assertEquals(oct15, nodes.getValue("z").expiredAt)
        assertEquals(oct15, nodes.getValue("o").expiredAt)
        assertEquals(Instant.parse("2026-07-17T01:02:03.123456789Z"), nodes.getValue("ns").expiredAt)
        assertEquals("no zone: read in the api's zone (UTC here)", oct15, nodes.getValue("local").expiredAt)
        assertNull(nodes.getValue("null").expiredAt)
        assertEquals(Instant.parse("0001-01-01T00:00:00Z"), nodes.getValue("zero").expiredAt)
        assertEquals(Instant.ofEpochSecond(1_792_051_200), nodes.getValue("sec").expiredAt)
        assertNull(nodes.getValue("junk").expiredAt)
    }

    @Test
    fun nodes_dataNotAnArray_isParseError() = runBlocking {
        server.enqueue(json(wrap("""{"uuid":"x"}""")))
        assertEquals(KomariError.Parse, err(api().getNodes()))
    }

    // ------------------------------------------------------------------ errors

    @Test
    fun unauthorized_withoutKey() = runBlocking {
        server.enqueue(json("""{"status":"error","message":"Private site is enabled, please login first."}""", code = 401))
        assertEquals(KomariError.Unauthorized(keySent = false), err(api().getNodes()))
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun unauthorized_withKey() = runBlocking {
        server.enqueue(json("""{"status":"error","message":"Unauthorized."}""", code = 401))
        assertEquals(KomariError.Unauthorized(keySent = true), err(api(apiKey = key).getNodes()))
        assertEquals("Bearer $key", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun key_isSentAsBearerOnEveryEndpoint_andTrimmed() = runBlocking {
        server.enqueue(json(wrap("[]")))
        server.enqueue(json(wrap("""{"version":"1.5.1","hash":"abc"}""")))
        server.enqueue(json(wrap("[]")))
        val a = api(apiKey = "  $key  ")
        a.getNodes()
        a.getVersion()
        a.getLatest("n-1")
        repeat(3) { assertEquals("Bearer $key", server.takeRequest().headers["Authorization"]) }
    }

    @Test
    fun guest_sendsNoAuthorization() = runBlocking {
        server.enqueue(json(wrap("[]")))
        api(apiKey = "   ").getNodes()
        assertNull(server.takeRequest().headers["Authorization"])
    }

    @Test
    fun forbidden_notFound_serverError() = runBlocking {
        server.enqueue(json("{}", code = 403))
        server.enqueue(MockResponse.Builder().code(404).body("404 page not found").build())
        server.enqueue(json("""{"status":"error","message":"boom"}""", code = 500))
        server.enqueue(json("{}", code = 502))
        assertEquals(KomariError.Forbidden, err(api().getNodes()))
        assertEquals(KomariError.NotFound, err(api().getNodes()))
        assertEquals(KomariError.Http(500), err(api().getNodes()))
        assertEquals(KomariError.Http(502), err(api().getNodes()))
    }

    @Test
    fun html_isNotKomari() = runBlocking {
        server.enqueue(
            MockResponse.Builder().code(200).setHeader("Content-Type", "text/html; charset=utf-8")
                .body("<!doctype html><html><body>Just a moment...</body></html>").build(),
        )
        // Same page with a wrong content type: rejected because it is not JSON.
        server.enqueue(MockResponse.Builder().code(200).body("<html><title>login</title></html>").build())
        assertEquals(KomariError.NotKomari, err(api().getNodes()))
        assertEquals(KomariError.NotKomari, err(api().getNodes()))
    }

    @Test
    fun statusError_isApiFailure_withTruncatedMessage() = runBlocking {
        server.enqueue(json("""{"status":"error","message":"Failed to retrieve client information: db locked"}"""))
        assertEquals(
            KomariError.ApiFailure("Failed to retrieve client information: db locked"),
            err(api().getNodes()),
        )
        server.enqueue(json("""{"status":"error","message":"${"x".repeat(500)}"}"""))
        val long = err(api().getNodes()) as KomariError.ApiFailure
        assertEquals(KomariError.MAX_MESSAGE_LENGTH, long.message.length)
        assertTrue(long.message.endsWith("…"))
    }

    @Test
    fun readTimeout_isNetworkTimeout() = runBlocking {
        server.enqueue(json(wrap("[]")).newBuilder().headersDelay(3, TimeUnit.SECONDS).build())
        val fast = client.newBuilder().readTimeout(300, TimeUnit.MILLISECONDS).build()
        assertEquals(KomariError.Network(KomariError.NetworkKind.TIMEOUT), err(api(http = fast).getNodes()))
    }

    @Test
    fun connectionRefused_isNetworkOther() = runBlocking {
        val port = server.port
        server.close()
        val result = api(base = "http://localhost:$port").getNodes()
        assertEquals(KomariError.Network(KomariError.NetworkKind.OTHER), err(result))
    }

    @Test
    fun droppedConnection_isRetriedOnce() = runBlocking {
        server.enqueue(json(wrap("[$fullNode]")).newBuilder().onResponseBody(SocketEffect.CloseSocket()).build())
        server.enqueue(json(wrap("[$fullNode]")))
        assertEquals("n-1", ok(api().getNodes()).nodes.single().uuid)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun droppedConnectionTwice_isNetworkOther() = runBlocking {
        repeat(2) { server.enqueue(json(wrap("[$fullNode]")).newBuilder().onResponseBody(SocketEffect.CloseSocket()).build()) }
        assertEquals(KomariError.Network(KomariError.NetworkKind.OTHER), err(api().getNodes()))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun invalidBaseUrl_isInvalidUrl_withoutRequest() = runBlocking {
        assertEquals(KomariError.InvalidUrl, err(api(base = "ftp://nope").getNodes()))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun defaultClient_timeouts() {
        assertEquals(10_000, client.connectTimeoutMillis)
        assertEquals(15_000, client.readTimeoutMillis)
        assertTrue("no interceptors in the shared client", client.interceptors.isEmpty() && client.networkInterceptors.isEmpty())
    }

    // ------------------------------------------------------------------ redirects

    @Test
    fun redirectToOtherHost_dropsKey() = runBlocking {
        MockWebServer().use { other ->
            other.start()
            other.enqueue(json(wrap("[]")))
            server.enqueue(
                MockResponse.Builder().code(302).setHeader("Location", "http://127.0.0.1:${other.port}/api/nodes").build(),
            )
            ok(api(apiKey = key).getNodes())
            assertEquals("Bearer $key", server.takeRequest().headers["Authorization"])
            val redirected = other.takeRequest()
            assertEquals("127.0.0.1", redirected.url.host)
            assertNull("the key must not follow a redirect to another host", redirected.headers["Authorization"])
        }
    }

    @Test
    fun redirectToOtherPortOnSameHost_dropsKey() = runBlocking {
        MockWebServer().use { other ->
            other.start()
            other.enqueue(json(wrap("[]")))
            server.enqueue(MockResponse.Builder().code(301).setHeader("Location", "${baseUrl(other)}/api/nodes").build())
            ok(api(apiKey = key).getNodes())
            assertNull(other.takeRequest().headers["Authorization"])
        }
    }

    @Test
    fun redirectWithinSameOrigin_keepsKey() = runBlocking {
        server.enqueue(MockResponse.Builder().code(307).setHeader("Location", "/komari/api/nodes").build())
        server.enqueue(json(wrap("[]")))
        ok(api(apiKey = key).getNodes())
        server.takeRequest()
        val second = server.takeRequest()
        assertEquals("/komari/api/nodes", second.url.encodedPath)
        assertEquals("Bearer $key", second.headers["Authorization"])
    }

    // ------------------------------------------------------------------ recent / version

    @Test
    fun recent_empty_isNullNotError() = runBlocking {
        server.enqueue(json(wrap("[]")))
        assertNull(ok(api().getLatest("n-1")))
        assertEquals("/api/recent/n-1", server.takeRequest().url.encodedPath)
    }

    @Test
    fun recent_picksNewestReport_regardlessOfOrder() = runBlocking {
        val older = """{"network":{"up":1,"down":2,"totalUp":100,"totalDown":200},"uptime":50,"updated_at":"2026-09-28T08:00:00Z"}"""
        val newest = """{"network":{"up":1,"down":2,"totalUp":150,"totalDown":260},"uptime":110,"updated_at":"2026-09-28T08:01:00.5Z","cpu":{"usage":3.2}}"""
        val middle = """{"network":{"totalUp":120,"totalDown":230},"uptime":80,"updated_at":"2026-09-28T08:00:30Z"}"""
        server.enqueue(json(wrap("[$older,$newest,$middle]")))
        val latest = ok(api().getLatest("n-1"))!!
        assertEquals(150L, latest.totalUp)
        assertEquals(260L, latest.totalDown)
        assertEquals(110L, latest.uptimeSec)
        assertEquals(Instant.parse("2026-09-28T08:01:00.5Z"), latest.reportedAt)
    }

    @Test
    fun recent_komariOrder_oldestFirst_lastWins() = runBlocking {
        val a = """{"network":{"totalUp":1,"totalDown":1},"updated_at":"2026-09-28T08:00:00Z"}"""
        val b = """{"network":{"totalUp":2,"totalDown":2},"updated_at":"2026-09-28T08:00:02Z"}"""
        server.enqueue(json(wrap("[$a,$b]")))
        assertEquals(2L, ok(api().getLatest("x"))!!.totalUp)
    }

    @Test
    fun recent_countersAreLenient_andMissingPartsDefault() = runBlocking {
        server.enqueue(json(wrap("""[{"network":{"totalUp":"5.0E3","totalDown":-9},"uptime":"oops"}]""")))
        val latest = ok(api().getLatest("x"))!!
        assertEquals(5_000L, latest.totalUp)
        assertEquals(0L, latest.totalDown)
        assertNull(latest.uptimeSec)
        assertNull(latest.reportedAt)
    }

    @Test
    fun recent_uuidIsPathEncoded() = runBlocking {
        server.enqueue(json(wrap("[]")))
        api().getLatest("a/b c")
        assertEquals("/api/recent/a%2Fb%20c", server.takeRequest().url.encodedPath)
    }

    @Test
    fun version_parsed_andSubPathKept() = runBlocking {
        server.enqueue(json(wrap("""{"version":"1.5.1","hash":"8f2c"}""")))
        val v = ok(api(base = "${baseUrl()}/komari/api/").getVersion())
        assertEquals(KomariVersion("1.5.1", "8f2c"), v)
        assertEquals("/komari/api/version", server.takeRequest().url.encodedPath)
    }

    // ------------------------------------------------------------------ secrecy

    @Test
    fun keyNeverAppearsInErrorsOrEndpoint() = runBlocking {
        server.enqueue(json("{}", code = 401))
        server.enqueue(MockResponse.Builder().code(200).body("<html>$key</html>").build())
        val errors = listOf(err(api(apiKey = key).getNodes()), err(api(apiKey = key).getNodes()))
        val endpoint = KomariEndpoint(baseUrl(), key)
        val texts = errors.map { it.toString() } + errors.map { it.code } + endpoint.toString() + KeyInput.New(key).toString()
        texts.forEach { assertFalse("leaked in: $it", it.contains(key)) }
        assertTrue(endpoint.toString().contains("<set>"))
    }

    @Test
    fun classify_mapsExceptionTypes() {
        assertEquals(KomariError.NetworkKind.TIMEOUT, OkHttpKomariApi.classify(SocketTimeoutException()))
        assertEquals(KomariError.NetworkKind.TIMEOUT, OkHttpKomariApi.classify(InterruptedIOException("timeout")))
        assertEquals(KomariError.NetworkKind.DNS, OkHttpKomariApi.classify(UnknownHostException("x")))
        assertEquals(KomariError.NetworkKind.SSL, OkHttpKomariApi.classify(SSLHandshakeException("bad cert")))
        assertEquals(
            KomariError.NetworkKind.CLEARTEXT_BLOCKED,
            OkHttpKomariApi.classify(UnknownServiceException("CLEARTEXT communication to x not permitted by network security policy")),
        )
        assertEquals(KomariError.NetworkKind.OTHER, OkHttpKomariApi.classify(ConnectException("refused")))
        assertEquals(KomariError.NetworkKind.OTHER, OkHttpKomariApi.classify(IOException("?")))
    }
}
