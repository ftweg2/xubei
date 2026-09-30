package com.ftwegc.myapplication.data.remote.komari

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Panel address handling. */
object KomariUrl {

    /**
     * Normalizes a user-typed panel address to `scheme://host[:port][/sub/path]` (no trailing slash):
     *
     * - all whitespace is removed;
     * - `https://` is added when no scheme is given; only `http` and `https` are accepted;
     * - query, fragment and user info are dropped;
     * - trailing slashes and a trailing `/api` segment are removed (repeatedly), so both
     *   `https://a.com/api/` and `https://a.com/` give `https://a.com`;
     * - a sub path such as `https://a.com/komari` is kept (panels behind a reverse proxy).
     *
     * Returns `null` when the result is not a valid http(s) URL.
     */
    fun normalize(input: String): String? {
        val compact = input.filterNot(Char::isWhitespace)
        if (compact.isEmpty()) return null
        val schemeEnd = compact.indexOf("://")
        val withScheme = when {
            schemeEnd < 0 -> "https://$compact"
            else -> {
                val scheme = compact.substring(0, schemeEnd).lowercase()
                if (scheme != "http" && scheme != "https") return null
                scheme + compact.substring(schemeEnd)
            }
        }
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (url.host.isEmpty()) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }.toMutableList()
        while (segments.isNotEmpty() && segments.last().equals("api", ignoreCase = true)) {
            segments.removeAt(segments.lastIndex)
        }
        val builder = HttpUrl.Builder()
            .scheme(url.scheme)
            .host(url.host)
            .port(url.port)
        segments.forEach { builder.addPathSegment(it) }
        return builder.build().toString().trimEnd('/')
    }

    /** True when the normalized [url] uses plain HTTP (the API key would travel unencrypted). */
    fun isCleartext(url: String?): Boolean = url?.startsWith("http://", ignoreCase = true) == true

    /** The normalized [base] parsed for requests, or `null`. */
    fun toHttpUrl(base: String): HttpUrl? = normalize(base)?.toHttpUrlOrNull()
}
