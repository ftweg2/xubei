package com.ftwegc.myapplication.data.remote.komari

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.math.BigDecimal

/** JSON configuration and envelope handling for Komari responses. */
object KomariJson {

    /** Ignores unknown fields, accepts nulls for defaulted fields, never needs explicit nulls. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    /**
     * Unwraps a Komari body:
     * - not JSON → [KomariError.NotKomari];
     * - an array root is the data itself;
     * - an object root with `status`: `"success"` gives its `data` (missing / null → `null`), any
     *   other status gives [KomariError.ApiFailure] with the `message`;
     * - an object root without `status` is the data itself;
     * - any other root → [KomariError.Parse].
     */
    fun unwrap(body: String): KomariResult<JsonElement?> {
        val start = body.trimStart().firstOrNull()
        if (start != '{' && start != '[') {
            // HTML, plain text, or nothing: not a Komari API. A bare JSON scalar is "wrong shape".
            return KomariResult.Err(if (JSON_SCALAR.matches(body.trim())) KomariError.Parse else KomariError.NotKomari)
        }
        val root = try {
            // Strict parsing for the root, so an HTML page never passes as a lenient string.
            Json.parseToJsonElement(body)
        } catch (_: SerializationException) {
            return KomariResult.Err(KomariError.NotKomari)
        } catch (_: IllegalArgumentException) {
            return KomariResult.Err(KomariError.NotKomari)
        }
        return when (root) {
            is JsonArray -> KomariResult.Ok(root)
            is JsonObject -> {
                val status = root["status"]
                if (status == null) {
                    KomariResult.Ok(root)
                } else if ((status as? JsonPrimitive)?.content?.trim().equals("success", ignoreCase = true)) {
                    KomariResult.Ok(root["data"]?.takeUnless { it is JsonNull })
                } else {
                    KomariResult.Err(KomariError.apiFailure((root["message"] as? JsonPrimitive)?.contentOrNull()))
                }
            }
            is JsonPrimitive -> KomariResult.Err(if (root.isString) KomariError.NotKomari else KomariError.Parse)
        }
    }

    private fun JsonPrimitive.contentOrNull(): String? = if (this is JsonNull) null else content

    private val JSON_SCALAR = Regex("""-?\d+(\.\d+)?([eE][+-]?\d+)?|true|false|null|"(?:[^"\\]|\\.)*"""")
}

// ------------------------------------------------------------------ Lenient field serializers

private fun Decoder.element(): JsonElement? = (this as? JsonDecoder)?.decodeJsonElement()

/** Number from an integer, a float (truncated) or a numeric string; anything else is 0. */
internal fun JsonElement?.lenientLong(): Long {
    val p = this as? JsonPrimitive ?: return 0
    if (p is JsonNull) return 0
    p.longOrNull?.let { return it }
    val decimal = p.content.trim().toBigDecimalOrNull() ?: p.doubleOrNull?.takeIf { it.isFinite() }?.let(BigDecimal::valueOf)
        ?: return 0
    return when {
        decimal > BigDecimal.valueOf(Long.MAX_VALUE) -> Long.MAX_VALUE
        decimal < BigDecimal.valueOf(Long.MIN_VALUE) -> Long.MIN_VALUE
        else -> decimal.toLong()
    }
}

/** Integers, floats and numeric strings as `Long`; missing / null / junk is 0. */
object LenientLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientLong", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): Long = decoder.element().lenientLong()
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

/** Like [LenientLongSerializer], negatives become 0 (byte counters, limits). */
object NonNegativeLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("NonNegativeLong", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): Long = decoder.element().lenientLong().coerceAtLeast(0)
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

/** Nullable variant: missing / null / junk is `null`, negatives are `null`. */
object NonNegativeLongOrNullSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("NonNegativeLongOrNull", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): Long? {
        val p = decoder.element() as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        if (p.longOrNull == null && p.content.trim().toBigDecimalOrNull() == null) return null
        return p.lenientLong().takeIf { it >= 0 }
    }
    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) encoder.encodeNull() else encoder.encodeLong(value)
    }
}

/** `Int` clamped from [LenientLongSerializer]. */
object LenientIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int =
        decoder.element().lenientLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

/** Floats, integers and numeric strings; junk is 0.0. */
object LenientDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientDouble", PrimitiveKind.DOUBLE)
    override fun deserialize(decoder: Decoder): Double {
        val p = decoder.element() as? JsonPrimitive ?: return 0.0
        if (p is JsonNull) return 0.0
        return (p.doubleOrNull ?: p.content.trim().toDoubleOrNull())?.takeIf { it.isFinite() } ?: 0.0
    }
    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
}

/** `true`/`false`, `"true"`/`"1"`, `1`; anything else is false. */
object LenientBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientBoolean", PrimitiveKind.BOOLEAN)
    override fun deserialize(decoder: Decoder): Boolean {
        val p = decoder.element() as? JsonPrimitive ?: return false
        if (p is JsonNull) return false
        p.booleanOrNull?.let { return it }
        val text = p.content.trim().lowercase()
        return text == "1" || text == "true" || text == "yes"
    }
    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}

/** Any primitive as its text (numbers included); null / objects are "". */
object LenientStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String {
        val p = decoder.element() as? JsonPrimitive ?: return ""
        return if (p is JsonNull) "" else p.content
    }
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/** Any primitive as its text; null / objects are `null` (time values). */
object LenientStringOrNullSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientStringOrNull", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String? {
        val p = decoder.element() as? JsonPrimitive ?: return null
        return if (p is JsonNull) null else p.content
    }
    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

/**
 * Decodes an object with [delegate]; anything that is not an object, or an object that does not
 * decode, becomes [default]. For report blocks that must never make the whole report unreadable.
 */
open class LenientObjectSerializer<T>(private val delegate: KSerializer<T>, private val default: T) : KSerializer<T> {
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun deserialize(decoder: Decoder): T {
        val element = decoder.element() as? JsonObject ?: return default
        return try {
            KomariJson.json.decodeFromJsonElement(delegate, element)
        } catch (_: SerializationException) {
            default
        } catch (_: IllegalArgumentException) {
            default
        }
    }
    override fun serialize(encoder: Encoder, value: T) = delegate.serialize(encoder, value)
}

object LenientCpuSerializer : LenientObjectSerializer<KomariCpuDto>(KomariCpuDto.serializer(), KomariCpuDto())
object LenientUsageSerializer : LenientObjectSerializer<KomariUsageDto>(KomariUsageDto.serializer(), KomariUsageDto())
object LenientLoadSerializer : LenientObjectSerializer<KomariLoadDto>(KomariLoadDto.serializer(), KomariLoadDto())
object LenientConnectionsSerializer :
    LenientObjectSerializer<KomariConnectionsDto>(KomariConnectionsDto.serializer(), KomariConnectionsDto())

/** Komari tags: a `;`-separated string (current) or an array of strings; blanks dropped. */
object LenientTagsSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientTags", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): List<String> = when (val e = decoder.element()) {
        is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.takeUnless { p -> p is JsonNull }?.content?.trim() }
        is JsonNull, null -> emptyList()
        is JsonPrimitive -> e.content.split(';')
        else -> emptyList()
    }.map { it.trim().replace(COLOR_SUFFIX, "").trim() }.filter { it.isNotEmpty() }

    /** Komari tags may end with a display color such as `<green>`. */
    private val COLOR_SUFFIX = Regex("""<\w+>$""")

    override fun serialize(encoder: Encoder, value: List<String>) = encoder.encodeString(value.joinToString(";"))
}
