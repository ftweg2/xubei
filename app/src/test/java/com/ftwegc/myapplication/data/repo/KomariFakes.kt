package com.ftwegc.myapplication.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.ftwegc.myapplication.data.prefs.SecretCipher
import com.ftwegc.myapplication.data.remote.komari.KomariApi
import com.ftwegc.myapplication.data.remote.komari.KomariApiFactory
import com.ftwegc.myapplication.data.remote.komari.KomariEndpoint
import com.ftwegc.myapplication.data.remote.komari.KomariError
import com.ftwegc.myapplication.data.remote.komari.KomariLatest
import com.ftwegc.myapplication.data.remote.komari.KomariNode
import com.ftwegc.myapplication.data.remote.komari.KomariNodeList
import com.ftwegc.myapplication.data.remote.komari.KomariResult
import com.ftwegc.myapplication.data.remote.komari.KomariVersion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.security.GeneralSecurityException
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/** In-memory Preferences DataStore. */
class FakePreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }

    fun snapshot(): Preferences = state.value
}

/** Reversible, obviously-not-secure "cipher" for JVM tests; [broken] simulates a lost key. */
class FakeCipher : SecretCipher {
    var broken = false

    override fun encrypt(plain: ByteArray): ByteArray = byteArrayOf(7) + plain.map { (it.toInt() xor 0x5A).toByte() }

    override fun decrypt(data: ByteArray): ByteArray {
        if (broken || data.isEmpty() || data[0] != 7.toByte()) throw GeneralSecurityException("cannot decrypt")
        return data.drop(1).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }
}

/** Scripted [KomariApi]; records calls and the highest number of concurrent `getLatest` calls. */
class FakeKomariApi(
    var nodes: KomariResult<KomariNodeList> = KomariResult.Ok(KomariNodeList(emptyList())),
    var version: KomariResult<KomariVersion> = KomariResult.Ok(KomariVersion("1.5.1", "abc")),
    var latest: (String) -> KomariResult<KomariLatest?> = { KomariResult.Ok(null) },
    var latestDelayMillis: Long = 0,
) : KomariApi {
    val latestCalls = mutableListOf<String>()
    var nodeCalls = 0
    private val running = AtomicInteger(0)
    val maxConcurrent = AtomicInteger(0)

    override suspend fun getVersion(): KomariResult<KomariVersion> = version

    override suspend fun getNodes(): KomariResult<KomariNodeList> {
        nodeCalls++
        return nodes
    }

    override suspend fun getLatest(uuid: String): KomariResult<KomariLatest?> {
        val now = running.incrementAndGet()
        maxConcurrent.accumulateAndGet(now) { a, b -> maxOf(a, b) }
        try {
            synchronized(latestCalls) { latestCalls += uuid }
            if (latestDelayMillis > 0) delay(latestDelayMillis)
            return latest(uuid)
        } finally {
            running.decrementAndGet()
        }
    }

    fun setNodes(vararg list: KomariNode) {
        nodes = KomariResult.Ok(KomariNodeList(list.toList()))
    }

    fun failNodes(error: KomariError) {
        nodes = KomariResult.Err(error)
    }
}

/** Factory returning [api] and remembering every endpoint it was asked for. */
class RecordingFactory(val api: FakeKomariApi) : KomariApiFactory {
    val endpoints = mutableListOf<KomariEndpoint>()
    override fun create(endpoint: KomariEndpoint): KomariApi {
        endpoints += endpoint
        return api
    }
}

fun komariNode(
    uuid: String,
    name: String = "Node $uuid",
    price: Double = 10.0,
    cycle: Int = 30,
    currency: String = "¥",
    expiredAt: Instant? = Instant.parse("2026-10-15T00:00:00Z"),
    limit: Long = 1L shl 40,
    limitType: String = "max",
    group: String = "",
    autoRenewal: Boolean = false,
) = KomariNode(
    uuid = uuid, name = name, group = group, tags = emptyList(), hidden = false, weight = 0, publicRemark = "",
    price = price, billingCycleDays = cycle, currency = currency, autoRenewal = autoRenewal, expiredAt = expiredAt,
    trafficLimitBytes = limit, trafficLimitType = limitType,
)
