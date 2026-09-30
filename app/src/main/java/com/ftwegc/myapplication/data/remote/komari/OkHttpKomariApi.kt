package com.ftwegc.myapplication.data.remote.komari

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume

/**
 * [KomariApi] over OkHttp.
 *
 * Security:
 * - No logging interceptor. Errors carry only a classification, never exception text or headers.
 * - `Authorization: Bearer <key>` is added only to requests for the configured origin, and a
 *   network interceptor strips it from every hop (including redirects) whose scheme, host or port
 *   differs from the configured one.
 *
 * @param client shared client, normally [defaultClient]; this instance adds its own interceptor.
 * @param zone zone used to read Komari time values that carry no offset.
 */
class OkHttpKomariApi(
    private val endpoint: KomariEndpoint,
    client: OkHttpClient,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : KomariApi {

    private val base: HttpUrl? = KomariUrl.toHttpUrl(endpoint.baseUrl)

    private val http: OkHttpClient = if (base == null) client else client.newBuilder()
        .addNetworkInterceptor(AuthorizationScope(base))
        .build()

    override suspend fun getVersion(): KomariResult<KomariVersion> =
        get(listOf("api", "version")).then { data ->
            if (data !is JsonObject) return@then KomariResult.Err(KomariError.Parse)
            decode(data, KomariVersionDto.serializer())?.let { KomariResult.Ok(KomariVersion(it.version.trim(), it.hash.trim())) }
                ?: KomariResult.Err(KomariError.Parse)
        }

    override suspend fun getNodes(): KomariResult<KomariNodeList> =
        get(listOf("api", "nodes")).then { data ->
            val array = when (data) {
                null -> return@then KomariResult.Ok(KomariNodeList(emptyList()))
                is JsonArray -> data
                else -> return@then KomariResult.Err(KomariError.Parse)
            }
            var dropped = 0
            val nodes = array.mapNotNull { element ->
                val node = (element as? JsonObject)?.let { decode(it, KomariNodeDto.serializer()) }?.toModel(zone)
                if (node == null) dropped++
                node
            }
            KomariResult.Ok(KomariNodeList(nodes.distinctBy { it.uuid }, dropped))
        }

    override suspend fun getLatest(uuid: String): KomariResult<KomariLatest?> =
        getReports(uuid).then { reports -> KomariResult.Ok(latestOf(reports, zone)) }

    override suspend fun getRecent(uuid: String): KomariResult<List<KomariSample>> =
        getReports(uuid).then { reports -> KomariResult.Ok(samplesOf(reports, zone)) }

    /** The decoded reports of `/api/recent/{uuid}` in response order. */
    private suspend fun getReports(uuid: String): KomariResult<List<KomariReportDto>> {
        val id = uuid.trim()
        if (id.isEmpty()) return KomariResult.Err(KomariError.NotFound)
        return get(listOf("api", "recent", id)).then { data ->
            val elements = when (data) {
                null -> emptyList()
                is JsonArray -> data
                is JsonObject -> listOf(data)
                else -> return@then KomariResult.Err(KomariError.Parse)
            }
            val reports = elements.mapNotNull { (it as? JsonObject)?.let { o -> decode(o, KomariReportDto.serializer()) } }
            if (elements.isNotEmpty() && reports.isEmpty()) return@then KomariResult.Err(KomariError.Parse)
            KomariResult.Ok(reports)
        }
    }

    // ------------------------------------------------------------------ HTTP

    private suspend fun get(segments: List<String>): KomariResult<JsonElement?> {
        val baseUrl = base ?: return KomariResult.Err(KomariError.InvalidUrl)
        val url = baseUrl.newBuilder().apply { segments.forEach { addPathSegment(it) } }.build()
        val key = endpoint.apiKey
        val sendKey = key != null && sameOrigin(url, baseUrl)
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply { if (sendKey) header(AUTHORIZATION, "Bearer $key") }
            .get()
            .build()
        var raw = execute(http.newCall(request))
        // GETs are idempotent: one quiet retry for a dropped connection ("unexpected end of stream",
        // reset), which mobile networks and some proxies produce now and then. Timeouts, DNS and TLS
        // problems are not retried.
        if (raw is Raw.Failed && raw.kind == KomariError.NetworkKind.OTHER) raw = execute(http.newCall(request))
        return when (raw) {
            is Raw.Failed -> KomariResult.Err(KomariError.Network(raw.kind))
            is Raw.Done -> interpret(raw, keySent = sendKey)
        }
    }

    private fun interpret(raw: Raw.Done, keySent: Boolean): KomariResult<JsonElement?> = when {
        raw.code == 401 -> KomariResult.Err(KomariError.Unauthorized(keySent))
        raw.code == 403 -> KomariResult.Err(KomariError.Forbidden)
        raw.code == 404 -> KomariResult.Err(KomariError.NotFound)
        raw.code !in 200..299 -> KomariResult.Err(KomariError.Http(raw.code))
        raw.tooLarge -> KomariResult.Err(KomariError.Parse)
        raw.contentType?.contains("html", ignoreCase = true) == true -> KomariResult.Err(KomariError.NotKomari)
        else -> KomariJson.unwrap(raw.body)
    }

    private sealed interface Raw {
        class Done(val code: Int, val contentType: String?, val body: String, val tooLarge: Boolean) : Raw
        class Failed(val kind: KomariError.NetworkKind) : Raw
    }

    /** Runs [call] off the caller's thread; cancelling the coroutine cancels the call. */
    private suspend fun execute(call: Call): Raw = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // A cancelled call belongs to a cancelled coroutine; never report it as a network error.
                if (call.isCanceled()) {
                    cont.cancel()
                } else if (cont.isActive) {
                    cont.resume(Raw.Failed(classify(e)))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val raw = try {
                    response.use { r ->
                        val source = r.body.source()
                        val tooLarge = source.request(MAX_BODY_BYTES + 1)
                        val body = if (tooLarge) "" else source.buffer.readUtf8()
                        Raw.Done(r.code, r.header("Content-Type"), body, tooLarge)
                    }
                } catch (e: IOException) {
                    Raw.Failed(classify(e))
                }
                if (cont.isActive) cont.resume(raw)
            }
        })
    }

    private fun <T> decode(element: JsonElement, serializer: kotlinx.serialization.KSerializer<T>): T? = try {
        KomariJson.json.decodeFromJsonElement(serializer, element)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private inline fun <T, R> KomariResult<T>.then(block: (T) -> KomariResult<R>): KomariResult<R> =
        when (this) {
            is KomariResult.Ok -> block(value)
            is KomariResult.Err -> this
        }

    /** Strips `Authorization` from any hop that leaves the configured origin. */
    private class AuthorizationScope(private val origin: HttpUrl) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.header(AUTHORIZATION) != null && !sameOrigin(request.url, origin)) {
                return chain.proceed(request.newBuilder().removeHeader(AUTHORIZATION).build())
            }
            return chain.proceed(request)
        }
    }

    companion object {
        private const val AUTHORIZATION = "Authorization"

        /** Responses larger than this are rejected (a node list is a few KB). */
        const val MAX_BODY_BYTES: Long = 4L * 1024 * 1024

        const val CONNECT_TIMEOUT_SECONDS: Long = 10
        const val READ_TIMEOUT_SECONDS: Long = 15

        /** The shared client: 10 s connect, 15 s read, no interceptors, no cookies. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CONNECT_TIMEOUT_SECONDS + READ_TIMEOUT_SECONDS + 5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        /** Factory using one shared [client]. */
        fun factory(client: OkHttpClient, zone: () -> ZoneId = ZoneId::systemDefault): KomariApiFactory =
            KomariApiFactory { endpoint -> OkHttpKomariApi(endpoint, client, zone()) }

        internal fun sameOrigin(a: HttpUrl, b: HttpUrl): Boolean =
            a.scheme == b.scheme && a.host.equals(b.host, ignoreCase = true) && a.port == b.port

        /** Maps an I/O failure to a [KomariError.NetworkKind]; never looks at more than the type. */
        internal fun classify(e: IOException): KomariError.NetworkKind = when {
            e is SocketTimeoutException -> KomariError.NetworkKind.TIMEOUT
            e is InterruptedIOException && e.message?.contains("timeout", ignoreCase = true) == true ->
                KomariError.NetworkKind.TIMEOUT
            e is UnknownHostException -> KomariError.NetworkKind.DNS
            e is SSLException -> KomariError.NetworkKind.SSL
            e is UnknownServiceException && e.message?.contains("CLEARTEXT", ignoreCase = true) == true ->
                KomariError.NetworkKind.CLEARTEXT_BLOCKED
            e.cause is SSLException -> KomariError.NetworkKind.SSL
            else -> KomariError.NetworkKind.OTHER
        }

        /**
         * Reports as samples, oldest first: by parseable `updated_at` (undated reports first), ties
         * in response order (Komari lists oldest first).
         */
        internal fun samplesOf(reports: List<KomariReportDto>, zone: ZoneId): List<KomariSample> =
            reports.map { it.toSample(zone) }
                .withIndex()
                .sortedWith(compareBy<IndexedValue<KomariSample>>({ it.value.reportedAt ?: Instant.MIN }, { it.index }))
                .map { it.value }

        /**
         * Newest report: the latest parseable `updated_at`; reports without a time rank below any
         * dated one, and among equals the later array position wins (Komari lists oldest first).
         */
        internal fun latestOf(reports: List<KomariReportDto>, zone: ZoneId): KomariLatest? {
            val best = reports.withIndex().maxWithOrNull(
                compareBy<IndexedValue<KomariReportDto>>({ KomariTimeParser.parse(it.value.updatedAt, zone) ?: Instant.MIN }, { it.index }),
            )?.value ?: return null
            return KomariLatest(
                totalUp = best.network.totalUp,
                totalDown = best.network.totalDown,
                uptimeSec = best.uptime,
                reportedAt = KomariTimeParser.parse(best.updatedAt, zone),
            )
        }
    }
}
