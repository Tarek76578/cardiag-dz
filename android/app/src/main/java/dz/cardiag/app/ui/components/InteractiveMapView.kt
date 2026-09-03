package dz.cardiag.app.ui.components

import android.content.Context
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straighten
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dz.cardiag.app.core.road.RouteResult
import dz.cardiag.app.core.road.RoutingEngine
import dz.cardiag.app.core.services.CoarseLocation
import dz.cardiag.app.core.services.LIVE_MAP_CATEGORIES
import dz.cardiag.app.core.services.NearbyResult
import dz.cardiag.app.core.services.NearbyService
import dz.cardiag.app.core.services.OverpassNearbyProvider
import dz.cardiag.app.core.services.ServiceCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import kotlin.math.roundToInt

private const val MAP_LOCATION_INITIALIZED = "location_initialized"
private const val ROUTE_LINE_ID = "route_line"

@Composable
fun InteractiveMapView(
    latitude: Double,
    longitude: Double,
    validLocation: Boolean,
    accuracyMeters: Double?,
    services: List<NearbyService>,
    modifier: Modifier = Modifier,
    contentDescriptionText: String = "Carte interactive"
) {
    val context = LocalContext.current
    var fullScreen by remember { mutableStateOf(false) }
    var measuring by remember { mutableStateOf(false) }
    var measurePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var route by remember { mutableStateOf<RouteResult?>(null) }
    var routingLoading by remember { mutableStateOf(false) }
    var routingError by remember { mutableStateOf<String?>(null) }
    var liveServices by remember { mutableStateOf<List<NearbyService>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    val provider = remember { OverpassNearbyProvider() }
    val cache = remember { ServiceCache(context) }
    val routing = remember { RoutingEngine() }

    LaunchedEffect(latitude, longitude, validLocation) {
        if (!validLocation) {
            liveServices = emptyList()
            loading = false
            return@LaunchedEffect
        }
        val radius = 5_000
        liveServices = cache.read(latitude, longitude, radius)
        loading = true
        val center = CoarseLocation(latitude, longitude, accuracyMeters ?: 0.0, System.currentTimeMillis(), "gps")
        when (val result = provider.search(center, LIVE_MAP_CATEGORIES, radius, "fr")) {
            is NearbyResult.Success -> {
                liveServices = result.services
                cache.write(latitude, longitude, radius, result.services)
            }
            is NearbyResult.Failure -> Unit
        }
        loading = false
    }

    val allServices = remember(services, liveServices) { (services + liveServices).distinctBy { it.id } }

    LaunchedEffect(measurePoints) {
        if (measurePoints.size != 2) {
            route = null
            routingError = null
            routingLoading = false
            return@LaunchedEffect
        }
        routingLoading = true
        routingError = null
        route = runCatching {
            withContext(Dispatchers.IO) { routing.route(measurePoints[0], measurePoints[1]) }
        }.onFailure {
            routingError = it.message ?: "Impossible de calculer l'itinéraire"
        }.getOrNull()
        routingLoading = false
    }

    val onMeasureTap = rememberUpdatedState<(GeoPoint) -> Unit> { point ->
        if (!measuring) return@rememberUpdatedState
        measurePoints = if (measurePoints.size >= 2) listOf(point) else measurePoints + point
    }

    MapSurface(
        latitude, longitude, validLocation, accuracyMeters, allServices, modifier,
        contentDescriptionText, false, loading, measuring, measurePoints, route,
        routingLoading, routingError, { onMeasureTap.value(it) },
        { measuring = true; measurePoints = emptyList(); route = null; routingError = null },
        { measurePoints = emptyList(); measuring = false; route = null; routingError = null },
        onOpenFullScreen = { fullScreen = true }
    )

    if (fullScreen) {
        Dialog(
            onDismissRequest = { fullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            MapSurface(
                latitude, longitude, validLocation, accuracyMeters, allServices, Modifier.fillMaxSize(),
                contentDescriptionText, true, loading, measuring, measurePoints, route,
                routingLoading, routingError, { onMeasureTap.value(it) },
                { measuring = true; measurePoints = emptyList(); route = null; routingError = null },
                { measurePoints = emptyList(); measuring = false; route = null; routingError = null },
                onCloseFullScreen = { fullScreen = false }
            )
        }
    }
}

@Composable
private fun MapSurface(
    latitude: Double,
    longitude: Double,
    validLocation: Boolean,
    accuracyMeters: Double?,
    services: List<NearbyService>,
    modifier: Modifier,
    contentDescriptionText: String,
    fullScreen: Boolean,
    loading: Boolean,
    measuring: Boolean,
    measurePoints: List<GeoPoint>,
    route: RouteResult?,
    routingLoading: Boolean,
    routingError: String?,
    onMeasureTap: (GeoPoint) -> Unit,
    onMeasureStart: () -> Unit,
    onMeasureClear: () -> Unit,
    onOpenFullScreen: (() -> Unit)? = null,
    onCloseFullScreen: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    val filteredServices = remember(services, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) services else services.filter { s ->
            s.name.lowercase().contains(q) ||
                s.category.key.lowercase().contains(q) ||
                s.address?.lowercase()?.contains(q) == true ||
                s.phone?.lowercase()?.contains(q) == true
        }
    }
    val mapView = remember(context, fullScreen) { createMapView(context, latitude, longitude) }
    val currentMeasureTap by rememberUpdatedState(onMeasureTap)

    DisposableEffect(mapView, measuring) {
        val listener = android.view.View.OnTouchListener { _, event ->
            if (measuring && event.action == MotionEvent.ACTION_UP) {
                val projection = mapView.projection
                currentMeasureTap(projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint)
            }
            false
        }
        mapView.setOnTouchListener(listener)
        onDispose { mapView.setOnTouchListener(null) }
    }
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }

    LaunchedEffect(latitude, longitude, accuracyMeters, filteredServices, measurePoints, route, fullScreen) {
        updateMapMarkers(mapView, latitude, longitude, validLocation, accuracyMeters, filteredServices, measurePoints, route)
        if (validLocation && mapView.tag != MAP_LOCATION_INITIALIZED) {
            mapView.controller.setCenter(GeoPoint(latitude, longitude))
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
                updateMapMarkers(view, latitude, longitude, validLocation, accuracyMeters, filteredServices, measurePoints, route)
            }
        )
        if (fullScreen) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 64.dp, vertical = 12.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (search.isNotEmpty()) IconButton({ search = "" }) { Icon(Icons.Default.Close, "Clear") }
                },
                placeholder = { Text("Rechercher sur la carte") },
                shape = RoundedCornerShape(14.dp)
            )
            IconButton({ onCloseFullScreen?.invoke() }, Modifier.align(Alignment.TopStart).padding(12.dp)) {
                Icon(Icons.Default.Close, "Fermer la carte")
            }
            IconButton({ mapView.controller.setMapOrientation(0f) }, Modifier.padding(top = 68.dp, start = 12.dp)) {
                Icon(Icons.Default.North, "Nord")
            }
            IconButton({ if (measuring) onMeasureClear() else onMeasureStart() }, Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                Icon(Icons.Default.Straighten, if (measuring) "Annuler la mesure" else "Mesurer une distance")
            }
            IconButton({
                if (validLocation) {
                    mapView.controller.animateTo(GeoPoint(latitude, longitude))
                    mapView.controller.setZoom(15.0)
                }
            }, Modifier.align(Alignment.BottomEnd).padding(18.dp)) {
                Icon(Icons.Default.LocationOn, "Position actuelle")
            }
            Text(
                statusLabel(measuring, measurePoints, route, routingLoading, routingError, loading, filteredServices.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).padding(bottom = 52.dp)
            )
        } else {
            Button({ onOpenFullScreen?.invoke() }, Modifier.align(Alignment.TopEnd).padding(6.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Fullscreen, null)
                Text("Ouvrir la carte")
            }
            IconButton({ mapView.controller.setMapOrientation(0f) }, Modifier.align(Alignment.TopStart).padding(6.dp)) {
                Icon(Icons.Default.North, "Nord")
            }
            IconButton({ if (measuring) onMeasureClear() else onMeasureStart() }, Modifier.align(Alignment.BottomStart).padding(bottom = 28.dp, start = 6.dp)) {
                Icon(Icons.Default.Straighten, if (measuring) "Annuler la mesure" else "Mesurer une distance")
            }
            IconButton({
                if (validLocation) {
                    mapView.controller.animateTo(GeoPoint(latitude, longitude))
                    mapView.controller.setZoom(13.0)
                }
            }, Modifier.align(Alignment.BottomEnd).padding(8.dp)) {
                Icon(Icons.Default.LocationOn, "Position actuelle")
            }
            Text(
                statusLabel(measuring, measurePoints, route, routingLoading, routingError, loading, filteredServices.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
            )
        }
    }
}

private fun statusLabel(
    measuring: Boolean,
    points: List<GeoPoint>,
    route: RouteResult?,
    routingLoading: Boolean,
    routingError: String?,
    loading: Boolean,
    count: Int
): String = when {
    routingLoading -> "Calcul de la distance par route…"
    route != null -> "Route : ${formatDistance(route.distanceMeters)} · ${formatDuration(route.durationSeconds)}"
    routingError != null -> "Itinéraire indisponible · ${routingError.take(70)}"
    measuring && points.isEmpty() -> "Touchez la carte pour le point 1"
    measuring && points.size == 1 -> "Point 1 placé · touchez pour le point 2"
    loading -> "Services en chargement…"
    else -> "$count service(s) · © OpenStreetMap contributors"
}

private fun formatDuration(seconds: Double): String {
    val totalMinutes = (seconds / 60.0).roundToInt()
    return if (totalMinutes < 60) "$totalMinutes min" else "${totalMinutes / 60} h ${totalMinutes % 60} min"
}

private fun createMapView(context: Context, latitude: Double, longitude: Double): MapView {
    val appContext = context.applicationContext
    Configuration.getInstance().load(appContext, appContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    Configuration.getInstance().userAgentValue = String.format(Locale.US, "%s/1.0.5", appContext.packageName)
    return MapView(appContext).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        setBuiltInZoomControls(true)
        setTilesScaledToDpi(true)
        setUseDataConnection(true)
        setMinZoomLevel(3.0)
        setMaxZoomLevel(20.0)
        controller.setZoom(12.0)
        controller.setCenter(GeoPoint(latitude, longitude))
        isClickable = true
        isFocusable = true
    }
}

private fun updateMapMarkers(
    mapView: MapView,
    latitude: Double,
    longitude: Double,
    validLocation: Boolean,
    accuracyMeters: Double?,
    services: List<NearbyService>,
    measurePoints: List<GeoPoint>,
    route: RouteResult?
) {
    // Existing marker/cluster rendering is kept in the project implementation.
    // Route overlay is managed separately so the V3 routing geometry remains distinct.
    mapView.overlays.removeAll { overlay ->
        overlay is Polyline && overlay.id == ROUTE_LINE_ID
    }
    if (route?.geometry?.size ?: 0 >= 2) {
        val line = Polyline(mapView).apply {
            id = ROUTE_LINE_ID
            setPoints(route.geometry)
            width = 8f
        }
        mapView.overlays.add(line)
    }
    updateExistingMapMarkers(mapView, latitude, longitude, validLocation, accuracyMeters, services, measurePoints)
}
