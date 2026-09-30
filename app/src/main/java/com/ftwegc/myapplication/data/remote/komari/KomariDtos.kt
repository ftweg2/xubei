package com.ftwegc.myapplication.data.remote.komari

import com.ftwegc.myapplication.domain.model.KomariNodeSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

/*
 * Wire formats (verified against komari-monitor/komari 1.5.1 and main):
 *
 * GET /api/version       → { status, message, data: { version, hash } }
 * GET /api/nodes         → { status, message, data: [ models.Client ... ] }
 *                          hidden nodes are only listed for an admin / API key; ipv4, ipv6,
 *                          remark, version and token are blanked for everyone.
 * GET /api/recent/{uuid} → { status, message, data: [ v2.Report ... ] }
 *                          reports of the last minute, oldest first; `[]` when the agent is
 *                          offline. network.totalUp / totalDown are cumulative bytes.
 *
 * Every field has a default and every number is parsed leniently, so a missing, renamed or
 * retyped field degrades to "not set" instead of failing the whole response.
 */

/** One entry of `GET /api/nodes` (Komari `models.Client`). */
@Serializable
data class KomariNodeDto(
    @Serializable(with = LenientStringSerializer::class) val uuid: String = "",
    @Serializable(with = LenientStringSerializer::class) val name: String = "",
    @Serializable(with = LenientStringSerializer::class) val group: String = "",
    @Serializable(with = LenientTagsSerializer::class) val tags: List<String> = emptyList(),
    @Serializable(with = LenientBooleanSerializer::class) val hidden: Boolean = false,
    @Serializable(with = LenientIntSerializer::class) val weight: Int = 0,
    @SerialName("public_remark")
    @Serializable(with = LenientStringSerializer::class) val publicRemark: String = "",
    @Serializable(with = LenientDoubleSerializer::class) val price: Double = 0.0,
    @SerialName("billing_cycle")
    @Serializable(with = LenientIntSerializer::class) val billingCycle: Int = 0,
    @Serializable(with = LenientStringSerializer::class) val currency: String = "",
    @SerialName("auto_renewal")
    @Serializable(with = LenientBooleanSerializer::class) val autoRenewal: Boolean = false,
    @SerialName("expired_at")
    @Serializable(with = LenientStringOrNullSerializer::class) val expiredAt: String? = null,
    @SerialName("traffic_limit")
    @Serializable(with = NonNegativeLongSerializer::class) val trafficLimit: Long = 0,
    @SerialName("traffic_limit_type")
    @Serializable(with = LenientStringSerializer::class) val trafficLimitType: String = "",
    /** Flag emoji of the node's location, for example "🇩🇪"; empty when not set. */
    @Serializable(with = LenientStringSerializer::class) val region: String = "",
    // Machine facts for the detail page's node info (read only; empty / 0 when not reported).
    @SerialName("cpu_name")
    @Serializable(with = LenientStringSerializer::class) val cpuName: String = "",
    @SerialName("cpu_cores")
    @Serializable(with = LenientIntSerializer::class) val cpuCores: Int = 0,
    @Serializable(with = LenientStringSerializer::class) val virtualization: String = "",
    @Serializable(with = LenientStringSerializer::class) val arch: String = "",
    @Serializable(with = LenientStringSerializer::class) val os: String = "",
    @SerialName("kernel_version")
    @Serializable(with = LenientStringSerializer::class) val kernelVersion: String = "",
    @SerialName("mem_total")
    @Serializable(with = NonNegativeLongSerializer::class) val memTotal: Long = 0,
    @SerialName("disk_total")
    @Serializable(with = NonNegativeLongSerializer::class) val diskTotal: Long = 0,
)

/** `network` block of a report. Byte values; negatives become 0. */
@Serializable
data class KomariNetworkDto(
    @Serializable(with = NonNegativeLongSerializer::class) val up: Long = 0,
    @Serializable(with = NonNegativeLongSerializer::class) val down: Long = 0,
    @Serializable(with = NonNegativeLongSerializer::class) val totalUp: Long = 0,
    @Serializable(with = NonNegativeLongSerializer::class) val totalDown: Long = 0,
)

/** `cpu` block of a report: usage in percent. */
@Serializable
data class KomariCpuDto(
    @Serializable(with = LenientDoubleSerializer::class) val usage: Double = 0.0,
)

/** `ram`, `swap` and `disk` blocks: bytes; negatives become 0. */
@Serializable
data class KomariUsageDto(
    @Serializable(with = NonNegativeLongSerializer::class) val total: Long = 0,
    @Serializable(with = NonNegativeLongSerializer::class) val used: Long = 0,
)

/** `load` block: system load averages. */
@Serializable
data class KomariLoadDto(
    @Serializable(with = LenientDoubleSerializer::class) val load1: Double = 0.0,
    @Serializable(with = LenientDoubleSerializer::class) val load5: Double = 0.0,
    @Serializable(with = LenientDoubleSerializer::class) val load15: Double = 0.0,
)

/** `connections` block: open TCP and UDP connections. */
@Serializable
data class KomariConnectionsDto(
    @Serializable(with = LenientIntSerializer::class) val tcp: Int = 0,
    @Serializable(with = LenientIntSerializer::class) val udp: Int = 0,
)

/**
 * One entry of `GET /api/recent/{uuid}` (Komari `v2.Report`).
 *
 * `network`, `uptime` and `updated_at` feed the traffic counters and are parsed as before. The
 * other blocks only feed the live charts: each of them falls back to its defaults when it is
 * missing or has the wrong shape ([LenientObjectSerializer]), so they never make a report unreadable.
 */
@Serializable
data class KomariReportDto(
    val network: KomariNetworkDto = KomariNetworkDto(),
    @Serializable(with = NonNegativeLongOrNullSerializer::class) val uptime: Long? = null,
    @SerialName("updated_at")
    @Serializable(with = LenientStringOrNullSerializer::class) val updatedAt: String? = null,
    @Serializable(with = LenientCpuSerializer::class) val cpu: KomariCpuDto = KomariCpuDto(),
    @Serializable(with = LenientUsageSerializer::class) val ram: KomariUsageDto = KomariUsageDto(),
    @Serializable(with = LenientUsageSerializer::class) val swap: KomariUsageDto = KomariUsageDto(),
    @Serializable(with = LenientUsageSerializer::class) val disk: KomariUsageDto = KomariUsageDto(),
    @Serializable(with = LenientLoadSerializer::class) val load: KomariLoadDto = KomariLoadDto(),
    @Serializable(with = LenientConnectionsSerializer::class) val connections: KomariConnectionsDto = KomariConnectionsDto(),
    @Serializable(with = LenientIntSerializer::class) val process: Int = 0,
)

/** `GET /api/version` data. */
@Serializable
data class KomariVersionDto(
    @Serializable(with = LenientStringSerializer::class) val version: String = "",
    @Serializable(with = LenientStringSerializer::class) val hash: String = "",
)

// ------------------------------------------------------------------ Mapped models

/** Panel version. */
data class KomariVersion(val version: String, val hash: String)

/** A node from `/api/nodes`, times parsed. */
data class KomariNode(
    val uuid: String,
    val name: String,
    val group: String,
    val tags: List<String>,
    val hidden: Boolean,
    val weight: Int,
    val publicRemark: String,
    val price: Double,
    val billingCycleDays: Int,
    val currency: String,
    val autoRenewal: Boolean,
    val expiredAt: Instant?,
    val trafficLimitBytes: Long,
    val trafficLimitType: String,
    /** Flag emoji of the node's location ("" when not set). */
    val region: String = "",
    /** Machine facts (detail page "节点信息"); empty / 0 when the panel does not report them. */
    val machine: KomariMachine = KomariMachine(),
) {
    /** Input for [com.ftwegc.myapplication.domain.calc.KomariMerge]. */
    fun toSnapshot(): KomariNodeSnapshot = KomariNodeSnapshot(
        uuid = uuid,
        name = name,
        group = group,
        price = price,
        currency = KomariCurrency.normalize(currency),
        billingCycleDays = billingCycleDays,
        autoRenewal = autoRenewal,
        expiredAt = expiredAt,
        trafficLimitBytes = trafficLimitBytes,
        trafficLimitType = trafficLimitType,
    )
}

/**
 * One report of `/api/recent/{uuid}`, for the live charts.
 *
 * @property reportedAt `null` when the report had no parseable time.
 * @property cpuPercent CPU usage in percent (not capped).
 * @property netUp / [netDown] current transfer speed in bytes per second.
 * @property totalUp / [totalDown] cumulative counters in bytes.
 */
data class KomariSample(
    val reportedAt: Instant?,
    val cpuPercent: Double,
    val ramUsed: Long,
    val ramTotal: Long,
    val swapUsed: Long,
    val swapTotal: Long,
    val diskUsed: Long,
    val diskTotal: Long,
    val load1: Double,
    val load5: Double,
    val load15: Double,
    val netUp: Long,
    val netDown: Long,
    val totalUp: Long,
    val totalDown: Long,
    val tcp: Int,
    val udp: Int,
    val uptimeSec: Long?,
    val process: Int,
)

internal fun KomariReportDto.toSample(zone: ZoneId): KomariSample = KomariSample(
    reportedAt = KomariTimeParser.parse(updatedAt, zone),
    cpuPercent = cpu.usage.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0,
    ramUsed = ram.used,
    ramTotal = ram.total,
    swapUsed = swap.used,
    swapTotal = swap.total,
    diskUsed = disk.used,
    diskTotal = disk.total,
    load1 = load.load1.coerceAtLeast(0.0),
    load5 = load.load5.coerceAtLeast(0.0),
    load15 = load.load15.coerceAtLeast(0.0),
    netUp = network.up,
    netDown = network.down,
    totalUp = network.totalUp,
    totalDown = network.totalDown,
    tcp = connections.tcp.coerceAtLeast(0),
    udp = connections.udp.coerceAtLeast(0),
    uptimeSec = uptime,
    process = process.coerceAtLeast(0),
)

/**
 * What a node's agent reported about its machine (`/api/nodes`). Only shown, never stored.
 *
 * @property cpuCores 0 when unknown; [memTotal] / [diskTotal] bytes, 0 when unknown.
 */
data class KomariMachine(
    val cpuName: String = "",
    val cpuCores: Int = 0,
    val virtualization: String = "",
    val arch: String = "",
    val os: String = "",
    val kernelVersion: String = "",
    val memTotal: Long = 0,
    val diskTotal: Long = 0,
)

/** Result of `/api/nodes`: usable nodes and how many entries were dropped (no uuid / malformed). */
data class KomariNodeList(val nodes: List<KomariNode>, val droppedCount: Int = 0)

/**
 * Newest report of a node.
 *
 * @property uptimeSec `null` when not reported.
 * @property reportedAt `null` when the report had no parseable time.
 */
data class KomariLatest(
    val totalUp: Long,
    val totalDown: Long,
    val uptimeSec: Long?,
    val reportedAt: Instant?,
)

internal fun KomariNodeDto.toModel(zone: ZoneId): KomariNode? {
    val id = uuid.trim()
    if (id.isEmpty()) return null
    return KomariNode(
        uuid = id,
        name = name.trim(),
        group = group.trim(),
        tags = tags,
        hidden = hidden,
        weight = weight,
        publicRemark = publicRemark,
        price = price,
        billingCycleDays = billingCycle,
        currency = currency.trim(),
        autoRenewal = autoRenewal,
        expiredAt = KomariTimeParser.parse(expiredAt, zone),
        trafficLimitBytes = trafficLimit,
        trafficLimitType = trafficLimitType.trim(),
        region = region.trim(),
        machine = KomariMachine(
            cpuName = cpuName.trim(),
            cpuCores = cpuCores.coerceAtLeast(0),
            virtualization = virtualization.trim(),
            arch = arch.trim(),
            os = os.trim(),
            kernelVersion = kernelVersion.trim(),
            memTotal = memTotal,
            diskTotal = diskTotal,
        ),
    )
}

/**
 * Komari stores a currency *symbol* ("$", "¥", "€"), the app groups spend by code. Unambiguous
 * symbols map to their ISO code so that a node priced in "¥" adds up with the user's own CNY
 * subscriptions; anything else is kept as typed.
 */
object KomariCurrency {
    private val SYMBOLS = mapOf(
        "$" to "USD", "US$" to "USD", "¥" to "CNY", "￥" to "CNY", "RMB" to "CNY", "元" to "CNY",
        "€" to "EUR", "£" to "GBP", "HK$" to "HKD", "NT$" to "TWD", "S$" to "SGD", "A$" to "AUD",
        "C$" to "CAD", "₩" to "KRW", "₹" to "INR", "₽" to "RUB",
    )

    fun normalize(raw: String): String {
        val value = raw.trim()
        return SYMBOLS[value] ?: SYMBOLS[value.uppercase()] ?: value
    }
}
