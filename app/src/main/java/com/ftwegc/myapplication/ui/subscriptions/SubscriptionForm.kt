package com.ftwegc.myapplication.ui.subscriptions

import androidx.compose.runtime.Immutable
import com.ftwegc.myapplication.domain.model.BillingCycle
import com.ftwegc.myapplication.domain.model.Category
import com.ftwegc.myapplication.domain.model.CountingMode
import com.ftwegc.myapplication.domain.model.CycleUnit
import com.ftwegc.myapplication.domain.model.ReminderDays
import com.ftwegc.myapplication.domain.model.Subscription
import com.ftwegc.myapplication.domain.model.TrafficLimitType
import com.ftwegc.myapplication.domain.model.VpsConfig
import com.ftwegc.myapplication.ui.common.needsBillingInfo
import com.ftwegc.myapplication.ui.components.CyclePreset
import com.ftwegc.myapplication.ui.format.Formatters
import com.ftwegc.myapplication.ui.format.SizeUnit
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** ISO date text, for keeping the form in a SavedStateHandle. */
object LocalDateIsoSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDateIso", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

/**
 * Raw, editable form values (text fields stay strings until validation). Serializable, so the
 * edit page survives process death (see [SubscriptionEditViewModel]).
 */
@Immutable
@Serializable
data class SubscriptionForm(
    val name: String = "",
    val category: Category = Category.AI,
    val price: String = "",
    val currency: String = "CNY",
    /** `null` while no cycle was ever chosen (a Komari import without one). */
    val cyclePreset: CyclePreset? = CyclePreset.MONTHLY,
    val customCount: String = "30",
    val customUnit: CycleUnit = CycleUnit.DAY,
    @Serializable(with = LocalDateIsoSerializer::class) val nextDue: LocalDate? = null,
    val isPermanent: Boolean = false,
    val autoRenew: Boolean = false,
    val note: String = "",
    /** Use own reminder days ([reminderDays]) instead of the app default. */
    val customReminders: Boolean = false,
    val reminderDays: List<Int> = emptyList(),
    // VPS only
    val limitValue: String = "",
    val limitUnit: SizeUnit = SizeUnit.GB,
    val limitType: TrafficLimitType = TrafficLimitType.DEFAULT,
    val countingMode: CountingMode = CountingMode.KOMARI_CUMULATIVE,
    val resetDay: String = VpsConfig.DEFAULT_RESET_DAY.toString(),
    val alertPercent: Int = VpsConfig.DEFAULT_ALERT_PERCENT,
    /** Mode 2: usage of the current period before the app started counting. */
    val initialUsedValue: String = "",
    val initialUsedUnit: SizeUnit = SizeUnit.GB,
    /** The node's agent runs with `--month-rotate`. */
    val agentMonthRotate: Boolean = false,
    /**
     * Icon picked by hand: a brand id, "letter", or a picked image ("image:<file>"); `null`
     * matches the name automatically.
     */
    val iconKey: String? = null,
    /** Base color picked by hand (ARGB), `null` for the brand's or the name's color. */
    val iconColor: Int? = null,
    /** Free: costs nothing, the price is not asked for and not counted. */
    val isFree: Boolean = false,
) {
    val isOneTime: Boolean get() = cyclePreset == CyclePreset.ONCE

    /** Due date is required unless the subscription is permanent or a one-time purchase. */
    val dueRequired: Boolean get() = !isPermanent && !isOneTime

    companion object {
        /**
         * Form pre-filled from a stored subscription. [initialUsedBytes] is the current period's
         * entered usage (mode 2), `null` when none.
         */
        fun from(sub: Subscription, vps: VpsConfig?, initialUsedBytes: Long? = null): SubscriptionForm {
            val preset = CyclePreset.of(sub.cycle)
            val (limitValue, limitUnit) = Formatters.bytesToSizeInput(vps?.trafficLimitBytes ?: 0)
            val (initialValue, initialUnit) = Formatters.bytesToSizeInput(initialUsedBytes ?: 0)
            val config = vps ?: VpsConfig()
            return SubscriptionForm(
                name = sub.name,
                category = sub.category,
                // A Komari import without billing data starts with an empty price to fill in, not "0".
                price = if (sub.needsBillingInfo()) "" else Formatters.minorToInput(sub.priceMinor),
                currency = sub.currency,
                cyclePreset = if (sub.cycleKnown) preset else null,
                customCount = if (preset == CyclePreset.CUSTOM) sub.cycle.count.toString() else "30",
                customUnit = if (preset == CyclePreset.CUSTOM && sub.cycle.unit == CycleUnit.MONTH) CycleUnit.MONTH else CycleUnit.DAY,
                nextDue = sub.nextDue,
                isPermanent = sub.isPermanent,
                autoRenew = sub.autoRenew,
                note = sub.note,
                customReminders = sub.reminderDays != null,
                reminderDays = sub.reminderDays.orEmpty(),
                limitValue = limitValue,
                limitUnit = limitUnit,
                limitType = config.trafficLimitType,
                countingMode = config.countingMode,
                resetDay = config.resetDay.toString(),
                alertPercent = config.alertPercent,
                initialUsedValue = if ((initialUsedBytes ?: 0) > 0) initialValue else "",
                initialUsedUnit = initialUnit,
                agentMonthRotate = config.agentMonthRotate,
                iconKey = sub.iconKey,
                iconColor = sub.iconColor,
                isFree = sub.isFree,
            )
        }
    }
}

/** Form fields that can carry an error. */
enum class FormField { NAME, PRICE, CURRENCY, CYCLE, CYCLE_COUNT, DUE_DATE, TRAFFIC_LIMIT, RESET_DAY, INITIAL_USED }

/** Why a field is invalid; the screen maps it to a string resource. */
sealed interface FieldError {
    data object Required : FieldError
    data class TooLong(val max: Int) : FieldError
    data object InvalidAmount : FieldError
    data class CycleCount(val max: Int) : FieldError
    data object CycleRequired : FieldError
    data object DueRequired : FieldError
    data object InvalidLimit : FieldError
    data object InvalidResetDay : FieldError
    data object InvalidInitialUsed : FieldError
}

/** Validation outcome: either the models to save or the per-field errors. */
sealed interface FormResult {
    /**
     * @property initialUsedBytes mode 2: the entered usage of the current period (0 when the field
     *   is empty), `null` outside mode 2.
     */
    data class Valid(val subscription: Subscription, val vps: VpsConfig?, val initialUsedBytes: Long? = null) : FormResult
    data class Invalid(val errors: Map<FormField, FieldError>) : FormResult
}

/** Pure validation and conversion of [SubscriptionForm]. */
object SubscriptionFormValidator {
    const val NAME_MAX: Int = 40
    const val CURRENCY_MAX: Int = 8
    const val NOTE_MAX: Int = 500
    const val MAX_CYCLE_DAYS: Int = 3650
    const val MAX_CYCLE_MONTHS: Int = 120

    /**
     * True when [base] may stay "to be completed": it has no price, no due date or no known
     * cycle yet (typically a Komari import). Such a subscription can be saved with those fields
     * still empty.
     */
    fun allowsIncomplete(base: Subscription?): Boolean =
        base != null && (base.needsBillingInfo() || !base.cycleKnown)

    /**
     * Validates [form]. [base] / [baseVps] are the stored values when editing: their id, archived
     * flag, free flag and Komari data are kept, and the anchor day is kept when the due date did
     * not change.
     *
     * For a "to be completed" [base] ([allowsIncomplete]) an empty price means 0, the due date
     * may stay empty, and the cycle may stay unset while price and due date are empty.
     */
    fun validate(form: SubscriptionForm, base: Subscription?, baseVps: VpsConfig?): FormResult {
        val errors = linkedMapOf<FormField, FieldError>()
        val incompleteOk = allowsIncomplete(base)

        val name = form.name.trim()
        when {
            name.isEmpty() -> errors[FormField.NAME] = FieldError.Required
            name.length > NAME_MAX -> errors[FormField.NAME] = FieldError.TooLong(NAME_MAX)
        }

        val priceMinor = when {
            form.isFree -> 0L
            incompleteOk && form.price.isBlank() -> 0L
            else -> Formatters.parseAmountToMinor(form.price)
        }
        if (priceMinor == null) errors[FormField.PRICE] = FieldError.InvalidAmount

        val currency = form.currency.trim()
        when {
            currency.isEmpty() -> errors[FormField.CURRENCY] = FieldError.Required
            currency.length > CURRENCY_MAX -> errors[FormField.CURRENCY] = FieldError.TooLong(CURRENCY_MAX)
        }

        val due = if (form.isPermanent) null else form.nextDue
        if (form.dueRequired && due == null && !incompleteOk) errors[FormField.DUE_DATE] = FieldError.DueRequired

        val preset = form.cyclePreset
        var cycleValid = true
        val cycle: BillingCycle? = when {
            preset == null -> {
                // Only a still incomplete subscription may keep an unknown cycle.
                if (!incompleteOk || (priceMinor ?: 0L) > 0L || due != null || form.isPermanent) {
                    errors[FormField.CYCLE] = FieldError.CycleRequired
                    cycleValid = false
                }
                null
            }
            preset.cycle != null -> preset.cycle
            else -> {
                val max = if (form.customUnit == CycleUnit.MONTH) MAX_CYCLE_MONTHS else MAX_CYCLE_DAYS
                val count = form.customCount.trim().toIntOrNull()
                if (count == null || count !in 1..max) {
                    errors[FormField.CYCLE_COUNT] = FieldError.CycleCount(max)
                    cycleValid = false
                    null
                } else {
                    BillingCycle.of(form.customUnit, count)
                }
            }
        }

        var limitBytes = 0L
        var resetDay = VpsConfig.DEFAULT_RESET_DAY
        var initialUsed: Long? = null
        if (form.category == Category.VPS) {
            if (form.limitValue.isNotBlank()) {
                val parsed = Formatters.parseSizeToBytes(form.limitValue, form.limitUnit)
                if (parsed == null) errors[FormField.TRAFFIC_LIMIT] = FieldError.InvalidLimit else limitBytes = parsed
            }
            val day = form.resetDay.trim().toIntOrNull()
            if (day == null || day !in 1..31) errors[FormField.RESET_DAY] = FieldError.InvalidResetDay else resetDay = day
            if (form.countingMode == CountingMode.APP_ACCUMULATED) {
                initialUsed = parseInitialUsed(form.initialUsedValue, form.initialUsedUnit)
                if (initialUsed == null) errors[FormField.INITIAL_USED] = FieldError.InvalidInitialUsed
            }
        }

        if (errors.isNotEmpty() || !cycleValid || priceMinor == null) return FormResult.Invalid(errors)

        val storedCycle = cycle ?: base?.cycle ?: BillingCycle.MONTHLY
        val anchorDay = when {
            storedCycle.unit != CycleUnit.MONTH || due == null -> null
            base != null && base.nextDue == due && base.anchorDay != null -> base.anchorDay
            else -> due.dayOfMonth
        }
        val subscription = Subscription(
            id = base?.id ?: 0,
            name = name,
            category = form.category,
            priceMinor = priceMinor,
            isFree = form.isFree,
            currency = currency,
            cycle = storedCycle,
            cycleKnown = cycle != null,
            nextDue = due,
            anchorDay = anchorDay,
            isPermanent = form.isPermanent,
            autoRenew = form.autoRenew,
            note = form.note.trim().take(NOTE_MAX),
            archived = base?.archived ?: false,
            reminderDays = if (form.customReminders) ReminderDays.normalize(form.reminderDays) else null,
            iconKey = form.iconKey?.trim()?.ifEmpty { null },
            iconColor = form.iconColor,
        )
        val vps = if (form.category == Category.VPS) {
            (baseVps ?: VpsConfig(subscriptionId = subscription.id)).copy(
                trafficLimitBytes = limitBytes,
                trafficLimitType = form.limitType,
                countingMode = form.countingMode,
                resetDay = resetDay,
                alertPercent = form.alertPercent.coerceIn(VpsConfig.MIN_ALERT_PERCENT, VpsConfig.MAX_ALERT_PERCENT),
                agentMonthRotate = form.agentMonthRotate,
            )
        } else {
            null
        }
        return FormResult.Valid(subscription, vps, initialUsed)
    }

    /** Empty means 0; otherwise a non-negative size in [unit]; `null` when malformed. */
    internal fun parseInitialUsed(text: String, unit: SizeUnit): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0L
        val value = trimmed.replace(",", "").replace('。', '.').toBigDecimalOrNull() ?: return null
        if (value.signum() < 0) return null
        if (value.signum() == 0) return 0L
        val bytes = value.multiply(BigDecimal(unit.bytes)).setScale(0, RoundingMode.HALF_UP)
        if (bytes > BigDecimal.valueOf(Long.MAX_VALUE)) return null
        return bytes.longValueExact()
    }
}
