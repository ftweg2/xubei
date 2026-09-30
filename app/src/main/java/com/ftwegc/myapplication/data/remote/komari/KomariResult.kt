package com.ftwegc.myapplication.data.remote.komari

/**
 * Outcome of a Komari call. Never throws for network, HTTP or parsing problems; those become
 * [Err] with a classified [KomariError].
 */
sealed interface KomariResult<out T> {
    data class Ok<out T>(val value: T) : KomariResult<T>
    data class Err(val error: KomariError) : KomariResult<Nothing>

    companion object {
        fun <T> ok(value: T): KomariResult<T> = Ok(value)
        fun err(error: KomariError): KomariResult<Nothing> = Err(error)
    }
}

/** The value, or `null` for an error. */
fun <T> KomariResult<T>.valueOrNull(): T? = (this as? KomariResult.Ok)?.value

/** The error, or `null` on success. */
fun KomariResult<*>.errorOrNull(): KomariError? = (this as? KomariResult.Err)?.error

inline fun <T, R> KomariResult<T>.map(transform: (T) -> R): KomariResult<R> = when (this) {
    is KomariResult.Ok -> KomariResult.Ok(transform(value))
    is KomariResult.Err -> this
}

/**
 * Why a Komari call failed. Every case has a stable [code] (stored in `vps_configs.last_error`)
 * and a Chinese message in the UI (`komariErrorText`).
 *
 * Security: no case ever holds the API key, a request header or an exception message; the only
 * free text is the panel's own error [ApiFailure.message], truncated.
 */
sealed class KomariError(val code: String) {

    /** No panel address is configured. */
    data object NotConfigured : KomariError("NOT_CONFIGURED")

    /** The configured address is not a valid http(s) URL. */
    data object InvalidUrl : KomariError("INVALID_URL")

    /** HTTP 401. [keySent] tells whether an API key was sent (wrong key vs. private site). */
    data class Unauthorized(val keySent: Boolean) : KomariError(if (keySent) "UNAUTHORIZED_KEY" else "UNAUTHORIZED")

    /** HTTP 403. */
    data object Forbidden : KomariError("FORBIDDEN")

    /** HTTP 404: the address is probably not the panel root, or the node does not exist. */
    data object NotFound : KomariError("NOT_FOUND")

    /** Any other non-2xx HTTP status. */
    data class Http(val status: Int) : KomariError("HTTP_$status")

    /** The request did not complete. */
    data class Network(val kind: NetworkKind) : KomariError("NETWORK_${kind.name}")

    /** A 2xx response that is not JSON (HTML login page, Cloudflare challenge, reverse proxy page). */
    data object NotKomari : KomariError("NOT_KOMARI")

    /** The panel answered `{"status":"error"}`; [message] is the panel's text, at most 200 chars. */
    data class ApiFailure(val message: String) : KomariError("API_FAILURE") {
        override fun toString(): String = "ApiFailure(message=$message)"
    }

    /** JSON that does not have the expected shape. */
    data object Parse : KomariError("PARSE")

    override fun toString(): String = code

    enum class NetworkKind { TIMEOUT, DNS, SSL, CLEARTEXT_BLOCKED, OTHER }

    companion object {
        /** Max length of a panel error message kept in [ApiFailure]. */
        const val MAX_MESSAGE_LENGTH: Int = 200

        /** Builds an [ApiFailure] with [message] trimmed and truncated to [MAX_MESSAGE_LENGTH]. */
        fun apiFailure(message: String?): ApiFailure {
            val text = message.orEmpty().trim().replace(Regex("\\s+"), " ")
            return ApiFailure(if (text.length > MAX_MESSAGE_LENGTH) text.take(MAX_MESSAGE_LENGTH - 1) + "…" else text)
        }

        /**
         * Parses a stored [code] back into an error ([ApiFailure] loses its message).
         * Unknown codes give `null`.
         */
        fun fromCode(code: String?): KomariError? {
            if (code.isNullOrBlank()) return null
            return when {
                code == NotConfigured.code -> NotConfigured
                code == InvalidUrl.code -> InvalidUrl
                code == "UNAUTHORIZED" -> Unauthorized(keySent = false)
                code == "UNAUTHORIZED_KEY" -> Unauthorized(keySent = true)
                code == Forbidden.code -> Forbidden
                code == NotFound.code -> NotFound
                code == NotKomari.code -> NotKomari
                code == Parse.code -> Parse
                code == "API_FAILURE" -> ApiFailure("")
                code.startsWith("HTTP_") -> code.removePrefix("HTTP_").toIntOrNull()?.let(::Http)
                code.startsWith("NETWORK_") ->
                    NetworkKind.entries.firstOrNull { it.name == code.removePrefix("NETWORK_") }?.let(::Network)
                else -> null
            }
        }
    }
}
