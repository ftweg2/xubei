package com.ftwegc.myapplication.data.backup

import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.CycleUnit
import com.ftwegc.myapplication.domain.model.LockedField
import com.ftwegc.myapplication.domain.model.ReminderDays
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.SubscriptionWithVps
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.VpsConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate

/*
 * Export file format (UTF-8 JSON), saved as `xubei-backup-<date>.json` ([BackupCodec.fileName]):
 *
 * {
 *   "format": "vluer-subscriptions",
 *   "version": 1,
 *   "exported_at": "2026-09-28T08:00:00Z",
 *   "subscriptions": [ { "name": …, "category": "VPS", "price_minor": 4990, …, "vps": { … } } ]
 * }
 *
 * The file holds subscriptions and their VPS settings only. It never contains the Komari API key,
 * the panel address, traffic readings or notification history.
 */

@Serializable
data class BackupFile(
    val format: String = BackupCodec.FORMAT,
    val version: Int = BackupCodec.VERSION,
    @SerialName("exported_at") val exportedAt: String = "",
    val subscriptions: List<BackupSubscription> = emptyList(),
)

@Serializable
data class BackupSubscription(
    val name: String = "",
    val category: String = Category.OTHER.name,
    @SerialName("price_minor") val priceMinor: Long = 0,
    @SerialName("is_free") val isFree: Boolean = false,
    val currency: String = "",
    @SerialName("cycle_unit") val cycleUnit: String = CycleUnit.MONTH.name,
    @SerialName("cycle_count") val cycleCount: Int = 1,
    @SerialName("cycle_known") val cycleKnown: Boolean = true,
    @SerialName("next_due") val nextDue: String? = null,
    @SerialName("anchor_day") val anchorDay: Int? = null,
    @SerialName("is_permanent") val isPermanent: Boolean = false,
    @SerialName("auto_renew") val autoRenew: Boolean = false,
    val note: String = "",
    val archived: Boolean = false,
    @SerialName("reminder_days") val reminderDays: List<Int>? = null,
    /** Icon picked by hand (a brand id, "letter", or a picked image); absent in older files. */
    @SerialName("icon_key") val iconKey: String? = null,
    /** Base color picked by hand (ARGB); absent in older files. */
    @SerialName("icon_color") val iconColor: Int? = null,
    val vps: BackupVps? = null,
)

@Serializable
data class BackupVps(
    @SerialName("komari_uuid") val komariUuid: String? = null,
    @SerialName("komari_name") val komariName: String? = null,
    @SerialName("komari_group") val komariGroup: String? = null,
    @SerialName("traffic_limit_bytes") val trafficLimitBytes: Long = 0,
    @SerialName("traffic_limit_type") val trafficLimitType: String = TrafficLimitType.DEFAULT.name,
    @SerialName("counting_mode") val countingMode: String = CountingMode.KOMARI_CUMULATIVE.name,
    @SerialName("reset_day") val resetDay: Int = VpsConfig.DEFAULT_RESET_DAY,
    @SerialName("reset_zone_id") val resetZoneId: String? = null,
    @SerialName("alert_percent") val alertPercent: Int = VpsConfig.DEFAULT_ALERT_PERCENT,
    @SerialName("locked_fields") val lockedFields: Int = LockedField.NONE,
    @SerialName("agent_month_rotate") val agentMonthRotate: Boolean = false,
    /** The node's flag emoji; absent in older files. */
    @SerialName("komari_region") val komariRegion: String? = null,
)

/** Result of reading an export file. */
sealed interface BackupReadResult {
    /**
     * @property items valid subscriptions (ids 0, VPS settings without readings).
     * @property invalidCount entries that were skipped because they could not be read.
     */
    data class Ok(val items: List<SubscriptionWithVps>, val invalidCount: Int) : BackupReadResult

    /** Not JSON, or JSON that is not an export of this app. */
    data object NotBackup : BackupReadResult

    /** An export from a newer app version. */
    data class NewerVersion(val version: Int) : BackupReadResult
}

/** Encodes subscriptions to the export format and reads it back, leniently. */
object BackupCodec {
    /**
     * Marks the file as an export of this app. It keeps the app's first (internal) name on purpose:
     * files exported before the app was renamed 续杯 carry it too and must keep importing, and
     * older app versions can still read new files.
     */
    const val FORMAT: String = "vluer-subscriptions"
    const val VERSION: Int = 1

    /** Suggested name of an export made on [date]: `xubei-backup-2026-09-29.json`. */
    fun fileName(date: LocalDate): String = "xubei-backup-$date.json"

    /** Largest file accepted by [read] (a few thousand subscriptions are well below). */
    const val MAX_BYTES: Int = 5 * 1024 * 1024

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun write(items: List<SubscriptionWithVps>, exportedAt: Instant): String =
        json.encodeToString(
            BackupFile.serializer(),
            BackupFile(exportedAt = exportedAt.toString(), subscriptions = items.map { it.toBackup() }),
        )

    /** Reads an export; one broken entry is skipped and counted instead of failing the whole file. */
    fun read(text: String): BackupReadResult {
        val root = try {
            json.parseToJsonElement(text.removePrefix("\uFEFF"))
        } catch (_: SerializationException) {
            return BackupReadResult.NotBackup
        } catch (_: IllegalArgumentException) {
            return BackupReadResult.NotBackup
        }
        val obj = root as? JsonObject ?: return BackupReadResult.NotBackup
        if ((obj["format"] as? JsonPrimitive)?.content != FORMAT) return BackupReadResult.NotBackup
        val version = (obj["version"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return BackupReadResult.NotBackup
        if (version > VERSION) return BackupReadResult.NewerVersion(version)
        val entries = obj["subscriptions"] as? JsonArray ?: return BackupReadResult.NotBackup
        var invalid = 0
        val items = entries.mapNotNull { element ->
            decodeEntry(element).also { if (it == null) invalid++ }
        }
        return BackupReadResult.Ok(items, invalid)
    }

    private fun decodeEntry(element: JsonElement): SubscriptionWithVps? = try {
        json.decodeFromJsonElement<BackupSubscription>(element).toDomain()
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun SubscriptionWithVps.toBackup(): BackupSubscription {
        val s = subscription
        return BackupSubscription(
            name = s.name,
            category = s.category.name,
            priceMinor = s.priceMinor,
            isFree = s.isFree,
            currency = s.currency,
            cycleUnit = s.cycle.unit.name,
            cycleCount = s.cycle.count,
            cycleKnown = s.cycleKnown,
            nextDue = s.nextDue?.toString(),
            anchorDay = s.anchorDay,
            isPermanent = s.isPermanent,
            autoRenew = s.autoRenew,
            note = s.note,
            archived = s.archived,
            reminderDays = s.reminderDays,
            iconKey = s.iconKey,
            iconColor = s.iconColor,
            vps = vps?.takeIf { s.category == Category.VPS }?.let { v ->
                BackupVps(
                    komariUuid = v.komariUuid,
                    komariName = v.komariName,
                    komariGroup = v.komariGroup,
                    trafficLimitBytes = v.trafficLimitBytes,
                    trafficLimitType = v.trafficLimitType.name,
                    countingMode = v.countingMode.name,
                    resetDay = v.resetDay,
                    resetZoneId = v.resetZoneId,
                    alertPercent = v.alertPercent,
                    lockedFields = v.lockedFields,
                    agentMonthRotate = v.agentMonthRotate,
                    komariRegion = v.komariRegion,
                )
            },
        )
    }

    /** Validates one entry; `null` when a required value is missing or out of range. */
    private fun BackupSubscription.toDomain(): SubscriptionWithVps? {
        val name = name.trim().take(NAME_MAX)
        if (name.isEmpty()) return null
        val category = Category.entries.firstOrNull { it.name == category } ?: return null
        val unit = CycleUnit.entries.firstOrNull { it.name == cycleUnit } ?: return null
        if (unit != CycleUnit.ONCE && cycleCount < 1) return null
        if (priceMinor < 0) return null
        val due = nextDue?.let {
            try {
                LocalDate.parse(it)
            } catch (_: DateTimeException) {
                return null
            }
        }
        val subscription = Subscription(
            name = name,
            category = category,
            priceMinor = priceMinor,
            isFree = isFree,
            currency = currency.trim().take(CURRENCY_MAX),
            cycle = BillingCycle.of(unit, cycleCount),
            cycleKnown = cycleKnown || unit == CycleUnit.ONCE,
            nextDue = if (isPermanent) null else due,
            anchorDay = anchorDay?.takeIf { it in 1..31 },
            isPermanent = isPermanent,
            autoRenew = autoRenew,
            note = note.take(NOTE_MAX),
            archived = archived,
            reminderDays = reminderDays?.let(ReminderDays::normalize),
            iconKey = iconKey?.trim()?.takeIf { it.isNotEmpty() && it.length <= ICON_KEY_MAX },
            iconColor = iconColor,
        )
        val vps = if (category == Category.VPS) {
            val v = vps ?: BackupVps()
            VpsConfig(
                komariUuid = v.komariUuid?.trim()?.ifEmpty { null },
                komariName = v.komariName,
                komariGroup = v.komariGroup,
                lockedFields = v.lockedFields and LockedField.ALL,
                trafficLimitBytes = v.trafficLimitBytes.coerceAtLeast(0),
                trafficLimitType = TrafficLimitType.parse(v.trafficLimitType),
                countingMode = CountingMode.entries.firstOrNull { it.name == v.countingMode } ?: CountingMode.KOMARI_CUMULATIVE,
                resetDay = v.resetDay.coerceIn(1, 31),
                resetZoneId = v.resetZoneId?.takeIf { it.isNotBlank() },
                alertPercent = v.alertPercent.coerceIn(VpsConfig.MIN_ALERT_PERCENT, VpsConfig.MAX_ALERT_PERCENT),
                agentMonthRotate = v.agentMonthRotate,
                komariRegion = v.komariRegion?.trim()?.takeIf { it.isNotEmpty() && it.length <= REGION_MAX },
            )
        } else {
            null
        }
        return SubscriptionWithVps(subscription, vps)
    }

    private const val NAME_MAX = 60
    private const val CURRENCY_MAX = 8
    private const val NOTE_MAX = 500
    private const val ICON_KEY_MAX = 200
    private const val REGION_MAX = 16
}
