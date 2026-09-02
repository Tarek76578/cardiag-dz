package dz.cardiag.app.ui.components

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale

/** Real OpenStreetMap map rendered from the device's current coordinates. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InteractiveMapView(
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Double?,
    modifier: Modifier = Modifier,
    contentDescriptionText: String
) {
    val validFix = latitude != null && longitude != null &&
        latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        !(latitude == 0.0 && longitude == 0.0)

    val url = if (validFix) {
        val lat = latitude!!
        val lon = longitude!!
        val delta = if (accuracyMeters != null && accuracyMeters.isFinite()) {
            (accuracyMeters / 111_000.0 * 2.5).coerceIn(0.01, 0.08)
        } else 0.025
        String.format(
            Locale.US,
            "https://www.openstreetmap.org/export/embed.html?bbox=%.6f%%2C%.6f%%2C%.6f%%2C%.6f&layer=mapnik&marker=%.6f%%2C%.6f",
            lon - delta, lat - delta, lon + delta, lat + delta, lat, lon
        )
    } else {
        "https://www.openstreetmap.org/export/embed.html?bbox=-8.7%2C19.0%2C12.0%2C37.1&layer=mapnik"
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                contentDescription = contentDescriptionText
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.userAgentString = "CarDiag-DZ/1.0 Android OSM"
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        },
        update = { webView ->
            webView.contentDescription = contentDescriptionText
            if (webView.url != url) webView.loadUrl(url)
        }
    )
}
