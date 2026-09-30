package com.ftwegc.myapplication.data.remote.komari

/**
 * Read-only access to one Komari panel. Implementations never throw for network, HTTP or
 * parsing problems; they return [KomariResult.Err].
 */
interface KomariApi {

    /** `GET /api/version`. */
    suspend fun getVersion(): KomariResult<KomariVersion>

    /** `GET /api/nodes`: nodes without a uuid are dropped and counted. */
    suspend fun getNodes(): KomariResult<KomariNodeList>

    /**
     * Newest report of [uuid] from `GET /api/recent/{uuid}`; `Ok(null)` when the panel has no recent
     * report (agent offline), which is not an error.
     */
    suspend fun getLatest(uuid: String): KomariResult<KomariLatest?>

    /**
     * Every report of [uuid] from `GET /api/recent/{uuid}` (about the last minute), oldest first;
     * an empty list when the agent is offline. Implementations without live data (test fakes)
     * answer [KomariError.NotFound].
     */
    suspend fun getRecent(uuid: String): KomariResult<List<KomariSample>> = KomariResult.Err(KomariError.NotFound)
}

/**
 * Where and how to reach the panel.
 *
 * @property baseUrl normalized panel address ([KomariUrl.normalize]).
 * @property apiKey optional API key sent as `Authorization: Bearer`; `null` is guest mode.
 */
class KomariEndpoint(val baseUrl: String, apiKey: String?) {
    val apiKey: String? = apiKey?.trim()?.takeIf { it.isNotEmpty() }

    val hasKey: Boolean get() = apiKey != null

    override fun equals(other: Any?): Boolean =
        other is KomariEndpoint && other.baseUrl == baseUrl && other.apiKey == apiKey

    override fun hashCode(): Int = baseUrl.hashCode() * 31 + (apiKey?.hashCode() ?: 0)

    /** Never prints the key. */
    override fun toString(): String = "KomariEndpoint(baseUrl=$baseUrl, apiKey=${if (hasKey) "<set>" else "<none>"})"
}

/** Creates a [KomariApi] for an endpoint (replaced by fakes in tests). */
fun interface KomariApiFactory {
    fun create(endpoint: KomariEndpoint): KomariApi
}
