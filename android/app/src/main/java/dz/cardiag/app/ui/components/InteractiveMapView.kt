package dz.cardiag.app.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dz.cardiag.app.core.road.CoarseLocation
import dz.cardiag.app.core.road.NearbyResult
import dz.cardiag.app.core.road.NearbyService
import dz.cardiag.app.core.road.OverpassNearbyProvider
import dz.cardiag.app.core.road.ServiceCategory
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

/** Real OpenStreetMap map with live nearby services and full-screen search. */
@Composable
fun InteractiveMapView(
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Double?,
    modifier: Modifier = Modifier,
    contentDescriptionText: String,
    services: List<NearbyService> = emptyList()
) {
    var fullScreen by remember { mutableStateOf(false) }

    MapSurface(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        services = services,
        modifier = modifier,
        contentDescriptionText = contentDescriptionText,
        fullScreen = false,
        onOpenFullScreen = { fullScreen = true }
    )

    if (fullScreen) {
        Dialog(
            onDismissRequest = { fullScreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            MapSurface(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
                services = services,
                modifier = Modifier.fillMaxSize(),
                contentDescriptionText = contentDescriptionText,
                fullScreen = true,
                onCloseFullScreen = { fullScreen = false }
            )
        }
    }
}

@Composable
private fun MapSurface(
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Double?,
    services: List<NearbyService>,
    modifier: Modifier,
    contentDescriptionText: String,
    fullScreen: Boolean,
    onOpenFullScreen: (() -> Unit)? = null,
    onCloseFullScreen: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val defaultLat = 28.0339
    val defaultLon = 1.6596
    val validLocation = latitude != null && longitude != null &&
        latitude.isFinite() && longitude.isFinite() &&
        latitude != 0.0 && longitude != 0.0
    val mapLat = if (validLocation) latitude!! else defaultLat
    val mapLon = if (validLocation) longitude!! else defaultLon

    var search by remember { mutableStateOf("") }
    var liveServices by remember { mutableStateOf<List<NearbyService>>(emptyList()) }
    val overpass = remember { OverpassNearbyProvider() }

    LaunchedEffect(validLocation, mapLat, mapLon) {
        if (!validLocation) {
            liveServices = emptyList()
            return@LaunchedEffect
        }
        val center = CoarseLocation(
            latitude = mapLat,
            longitude = mapLon,
            accuracyMeters = accuracyMeters ?: 0.0,
            capturedAtEpochMs = System.currentTimeMillis(),
            source = "gps"
        )
        liveServices = when (
            val result = overpass.search(
                center = center,
                categories = LIVE_MAP_CATEGORIES,
                radiusMeters = 10_000,
                language = "fr"
            )
        ) {
            is NearbyResult.Success -> result.services
            is NearbyResult.Failure -> emptyList()
        }
    }

    val allServices = remember(services, liveServices) {
        (services + liveServices).distinctBy { it.id }
    }
    val filteredServices = remember(allServices, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) allServices else allServices.filter { service ->
            service.name.lowercase().contains(q) ||
                service.category.key.lowercase().contains(q) ||
                service.address?.lowercase()?.contains(q) == true ||
                service.phone?.lowercase()?.contains(q) == true
        }
    }

    val mapView = remember(context, fullScreen) { createMapView(context, mapLat, mapLon) }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(mapLat, mapLon, validLocation, filteredServices, fullScreen) {
        updateMapMarkers(mapView, mapLat, mapLon, validLocation, accuracyMeters, filteredServices)
        if (validLocation && mapView.tag != MAP_LOCATION_INITIALIZED) {
            mapView.controller.setCenter(GeoPoint(mapLat, mapLon))
            mapView.controller.setZoom(if (fullScreen) 14.0 else 12.0)
            mapView.tag = MAP_LOCATION_INITIALIZED
        }
        mapView.invalidate()
    }

    Box(modifier = modifier.clip(if (fullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(16.dp))) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.contentDescription = contentDescriptionText
                updateMapMarkers(view, mapLat, mapLon, validLocation, accuracyMeters, filteredServices)
            }
        )

        if (fullScreen) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 64.dp, vertical = 12.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (search.isNotEmpty()) {
                        IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                placeholder = { Text("Rechercher sur la carte") },
                shape = RoundedCornerShape(14.dp)
            )
            IconButton(
                onClick = { onCloseFullScreen?.invoke() },
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Fermer la carte")
            }
            Text(
                text = if (search.isBlank()) {
                    "${filteredServices.size} service(s) · © OpenStreetMap contributors"
                } else {
                    "${filteredServices.size} résultat(s) · © OpenStreetMap contributors"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
            )
        } else {
            Button(
                onClick = { onOpenFullScreen?.invoke() },
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Fullscreen, contentDescription = null)
                Text("Ouvrir la carte")
            }
            Text(
                text = "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
            )
        }

        IconButton(
            onClick = {
                if (validLocation) {
                    mapView.controller.animateTo(GeoPoint(mapLat, mapLon))
                    mapView.controller.setZoom(if (fullScreen) 15.0 else 13.0)
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(if (fullScreen) 18.dp else 8.dp)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = "Position actuelle")
        }

        if (!validLocation && !fullScreen) {
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
        String.format(Locale.US, "%s/1.0.5", appContext.packageName)

    return MapView(appContext).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        setBuiltInZoomControls(true)
        setTilesScaledToDpi(true)
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

private fun updateMapMarkers(
    mapView: MapView,
    latitude: Double,
    longitude: Double,
    validLocation: Boolean,
    accuracyMeters: Double?,
    services: List<NearbyService>
) {
    val overlays = mapView.overlays
    overlays.removeAll { it is Marker && (it.id == GPS_MARKER_ID || it.id?.startsWith(SERVICE_MARKER_PREFIX) == true) }

    if (validLocation) {
        overlays.add(
            Marker(mapView).apply {
                id = GPS_MARKER_ID
                position = GeoPoint(latitude, longitude)
                title = "Position actuelle"
                snippet = accuracyMeters?.takeIf { it > 0.0 }?.let {
                    String.format(Locale.US, "GPS accuracy +/- %.0f m", it)
                }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
        )
    }

    services.forEachIndexed { index, service ->
        overlays.add(
            Marker(mapView).apply {
                id = "$SERVICE_MARKER_PREFIX$index-${service.id}"
                position = GeoPoint(service.latitude, service.longitude)
                title = service.name
                snippet = buildString {
                    append(service.category.key)
                    service.distanceMeters?.let { append(" · ").append(formatDistance(it)) }
                    service.address?.let { append("\n").append(it) }
                    service.phone?.let { append("\n").append(it) }
                }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
        )
    }
}

private fun formatDistance(meters: Double): String = when {
    meters < 1_000 -> String.format(Locale.US, "%.0f m", meters)
    else -> String.format(Locale.US, "%.1f km", meters / 1_000.0)
}

private val LIVE_MAP_CATEGORIES = setOf(
    ServiceCategory.MECHANIC,
    ServiceCategory.AUTO_ELECTRICIAN,
    ServiceCategory.ROADSIDE_ASSISTANCE,
    ServiceCategory.SPARE_PARTS,
    ServiceCategory.FUEL_STATION,
    ServiceCategory.HOSPITAL,
    ServiceCategory.TOWING
)

private const val GPS_MARKER_ID = "cardiag-gps-marker"
private const val SERVICE_MARKER_PREFIX = "cardiag-service-marker-"
private const val MAP_LOCATION_INITIALIZED = "cardiag-map-location-initialized"
