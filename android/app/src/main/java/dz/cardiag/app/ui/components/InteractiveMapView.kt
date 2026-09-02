package dz.cardiag.app.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

/** Real OpenStreetMap map with native pan/zoom and a supplied GPS marker. */
@Composable
fun InteractiveMapView(
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Double?,
    modifier: Modifier = Modifier,
    contentDescriptionText: String
) {
    val context = LocalContext.current
    val defaultLat = 28.0339
    val defaultLon = 1.6596
    val validLocation = latitude != null && longitude != null &&
        latitude.isFinite() && longitude.isFinite() &&
        latitude != 0.0 && longitude != 0.0
    val mapLat = if (validLocation) latitude!! else defaultLat
    val mapLon = if (validLocation) longitude!! else defaultLon
    val mapView = remember(context) { createMapView(context, mapLat, mapLon) }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(mapLat, mapLon, validLocation) {
        updateGpsMarker(mapView, mapLat, mapLon, validLocation, accuracyMeters)
        if (validLocation && mapView.tag != MAP_LOCATION_INITIALIZED) {
            mapView.controller.setCenter(GeoPoint(mapLat, mapLon))
            mapView.controller.setZoom(12.0)
            mapView.tag = MAP_LOCATION_INITIALIZED
        }
        mapView.invalidate()
    }

    Box(
        modifier = modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.contentDescription = contentDescriptionText
                updateGpsMarker(view, mapLat, mapLon, validLocation, accuracyMeters)
            }
        )
        Text(
            text = "© OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
        )
        if (!validLocation) {
            Text(
                text = "Algeria · GPS unavailable",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            )
        }
    }
}

private fun createMapView(context: Context, latitude: Double, longitude: Double): MapView {
    val appContext = context.applicationContext
    val preferences = appContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
    Configuration.getInstance().load(appContext, preferences)
    Configuration.getInstance().userAgentValue =
        String.format(Locale.US, "%s/1.0.3", appContext.packageName)

    return MapView(appContext).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        setBuiltInZoomControls(false)
        setTilesScaledToDpi(false)
        setUseDataConnection(true)
        setMinZoomLevel(3.0)
        setMaxZoomLevel(20.0)
        controller.setZoom(if (latitude.isFinite() && longitude.isFinite()) 12.0 else 5.0)
        controller.setCenter(GeoPoint(latitude, longitude))
        isClickable = true
        isFocusable = true
        contentDescription = "OpenStreetMap"
    }
}

private fun updateGpsMarker(
    mapView: MapView,
    latitude: Double,
    longitude: Double,
    validLocation: Boolean,
    accuracyMeters: Double?
) {
    val marker = mapView.overlays.filterIsInstance<Marker>()
        .firstOrNull { it.id == GPS_MARKER_ID }
    if (!validLocation) {
        if (marker != null) mapView.overlays.remove(marker)
        return
    }
    val gpsMarker = marker ?: Marker(mapView).also {
        it.id = GPS_MARKER_ID
        it.title = "CarDiag GPS"
        it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mapView.overlays.add(it)
    }
    gpsMarker.position = GeoPoint(latitude, longitude)
    gpsMarker.snippet = accuracyMeters?.takeIf { it > 0.0 }?.let {
        String.format(Locale.US, "GPS accuracy +/- %.0f m", it)
    }
}

private const val GPS_MARKER_ID = "cardiag-gps-marker"
private const val MAP_LOCATION_INITIALIZED = "cardiag-map-location-initialized"
