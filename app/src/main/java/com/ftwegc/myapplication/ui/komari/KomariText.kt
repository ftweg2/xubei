package com.ftwegc.myapplication.ui.komari

import androidx.compose.ui.platform.LocalResources
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import com.ftwegc.myapplication.R
import com.ftwegc.myapplication.data.remote.komari.KomariError
import com.ftwegc.myapplication.data.remote.komari.KomariError.NetworkKind

/** Chinese explanation of [error] for messages and banners. */
fun komariErrorMessage(resources: Resources, error: KomariError): String = when (error) {
    KomariError.NotConfigured -> resources.getString(R.string.komari_error_not_configured)
    KomariError.InvalidUrl -> resources.getString(R.string.komari_error_invalid_url)
    is KomariError.Unauthorized -> resources.getString(
        if (error.keySent) R.string.komari_error_unauthorized_key else R.string.komari_error_unauthorized,
    )
    KomariError.Forbidden -> resources.getString(R.string.komari_error_forbidden)
    KomariError.NotFound -> resources.getString(R.string.komari_error_not_found)
    is KomariError.Http -> resources.getString(R.string.komari_error_http, error.status)
    is KomariError.Network -> resources.getString(
        when (error.kind) {
            NetworkKind.TIMEOUT -> R.string.komari_error_timeout
            NetworkKind.DNS -> R.string.komari_error_dns
            NetworkKind.SSL -> R.string.komari_error_ssl
            NetworkKind.CLEARTEXT_BLOCKED -> R.string.komari_error_cleartext
            NetworkKind.OTHER -> R.string.komari_error_network
        },
    )
    KomariError.NotKomari -> resources.getString(R.string.komari_error_not_komari)
    is KomariError.ApiFailure -> if (error.message.isBlank()) {
        resources.getString(R.string.komari_error_api_plain)
    } else {
        resources.getString(R.string.komari_error_api, error.message)
    }
    KomariError.Parse -> resources.getString(R.string.komari_error_parse)
}

@Composable
@ReadOnlyComposable
fun komariErrorText(error: KomariError): String = komariErrorMessage(LocalResources.current, error)
