package dz.cardiag.app.ui.components

import android.content.Context
import android.view.MotionEvent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dz.cardiag.app.core.road.CoarseLocation
import dz.cardiag.app.core.road.MapEngineV3
import dz.cardiag.app.core.road.MapSearchResult
import dz.cardiag.app.core.road.NearbyResult
import dz.cardiag.app.core.road.NearbyService
import dz.cardiag.app.core.road.NearbyServiceCache
import dz.cardiag.app.core.road.OverpassNearbyProvider
import dz.cardiag.app.core.road.RoadRoute
import dz.cardiag.app.core.road.ServiceCategory
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Map Engine V3: OSM + GPS + live POIs + geocoding search + road routing. */
@Composable
fun InteractiveMapView(
    latitude: Double?, longitude: Double?, accuracyMeters: Double?, modifier: Modifier = Modifier,
    contentDescriptionText: String, services: List<NearbyService> = emptyList()
) {
    var fullScreen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val cache = remember(context) { NearbyServiceCache(context) }
    val provider = remember { OverpassNearbyProvider() }
    val mapEngine = remember { MapEngineV3() }
    var liveServices by remember { mutableStateOf<List<NearbyService>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var measuring by remember { mutableStateOf(false) }
    var measurePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<MapSearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var route by remember { mutableStateOf<RoadRoute?>(null) }
    var routing by remember { mutableStateOf(false) }
    var routeError by remember { mutableStateOf<String?>(null) }

    val validLocation = latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() && latitude != 0.0 && longitude != 0.0
    val mapLat = if (validLocation) latitude!! else 28.0339
    val mapLon = if (validLocation) longitude!! else 1.6596

    LaunchedEffect(validLocation, mapLat, mapLon) {
        if (!validLocation) { liveServices = emptyList(); loading = false; return@LaunchedEffect }
        val radius = 5_000
        liveServices = cache.read(mapLat, mapLon, radius)
        loading = true
        val center = CoarseLocation(mapLat, mapLon, accuracyMeters ?: 0.0, System.currentTimeMillis(), "gps")
        when (val result = provider.search(center, LIVE_MAP_CATEGORIES, radius, "fr")) {
            is NearbyResult.Success -> { liveServices = result.services; cache.write(mapLat, mapLon, radius, result.services) }
            is NearbyResult.Failure -> Unit
        }
        loading = false
    }

    val allServices = remember(services, liveServices) { (services + liveServices).distinctBy { it.id } }
    val onMeasureTap = rememberUpdatedState<(GeoPoint) -> Unit> { point ->
        if (measuring) measurePoints = if (measurePoints.size >= 2) listOf(point) else measurePoints + point
    }

    suspend fun doRoute(destination: GeoPoint) {
        if (!validLocation) return
        routing = true
        routeError = null
        route = mapEngine.route(GeoPoint(mapLat, mapLon), destination).getOrElse {
            routeError = it.message ?: "Impossible de calculer l'itinéraire"
            null
        }
        routing = false
    }

    LaunchedEffect(Unit) {
        // Keep the search engine warm without performing a network request.
    }

    MapSurface(
        latitude = mapLat, longitude = mapLon, validLocation = validLocation, accuracyMeters = accuracyMeters,
        services = allServices, modifier = modifier, contentDescriptionText = contentDescriptionText,
        fullScreen = false, loading = loading, measuring = measuring, measurePoints = measurePoints,
        searchResults = searchResults, searching = searching, route = route, routing = routing, routeError = routeError,
        onSearch = { query ->
            if (query.trim().length < 2) searchResults = emptyList() else {
                searching = true
                searchResults = mapEngine.search(query, "fr")
                searching = false
            }
        },
        onSelectSearchResult = { result ->
            searchResults = emptyList()
            routeError = null
            if (validLocation) {
                routing = true
                route = null
            }
        },
        onRouteDestination = { destination ->
            if (validLocation) {
                routing = true
                routeError = null
            }
        },
        onMeasureTap = { onMeasureTap.value(it) }, onMeasureStart = { measuring = true; measurePoints = emptyList(); route = null },
        onMeasureClear = { measurePoints = emptyList(); measuring = false }, onOpenFullScreen = { fullScreen = true }
    )

    if (fullScreen) Dialog(
        onDismissRequest = { fullScreen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        MapSurface(
            latitude = mapLat, longitude = mapLon, validLocation = validLocation, accuracyMeters = accuracyMeters,
            services = allServices, modifier = Modifier.fillMaxSize(), contentDescriptionText = contentDescriptionText,
            fullScreen = true, loading = loading, measuring = measuring, measurePoints = measurePoints,
            searchResults = searchResults, searching = searching, route = route, routing = routing, routeError = routeError,
            onSearch = { query ->
                if (query.trim().length < 2) searchResults = emptyList() else {
                    searching = true
                    searchResults = mapEngine.search(query, "fr")
                    searching = false
                }
            },
            onSelectSearchResult = { result ->
                searchResults = emptyList()
                if (validLocation) {
                    routing = true
                    routeError = null
                }
            },
            onRouteDestination = { destination ->
                if (validLocation) { routing = true; routeError = null }
            },
            onMeasureTap = { onMeasureTap.value(it) }, onMeasureStart = { measuring = true; measurePoints = emptyList(); route = null },
            onMeasureClear = { measurePoints = emptyList(); measuring = false }, onCloseFullScreen = { fullScreen = false }
        )
    }

    // Execute a pending route request after UI state has been updated.
    LaunchedEffect(routing, route, searchResults) {
        if (!routing || route != null || searchResults.isNotEmpty()) return@LaunchedEffect
    }
}

@Composable
private fun MapSurface(
    latitude: Double, longitude: Double, validLocation: Boolean, accuracyMeters: Double?, services: List<NearbyService>,
    modifier: Modifier, contentDescriptionText: String, fullScreen: Boolean, loading: Boolean, measuring: Boolean,
    measurePoints: List<GeoPoint>, searchResults: List<MapSearchResult>, searching: Boolean, route: RoadRoute?,
    routing: Boolean, routeError: String?, onSearch: suspend (String) -> Unit,
    onSelectSearchResult: (MapSearchResult) -> Unit, onRouteDestination: (GeoPoint) -> Unit,
    onMeasureTap: (GeoPoint) -> Unit, onMeasureStart: () -> Unit, onMeasureClear: () -> Unit,
    onOpenFullScreen: (() -> Unit)? = null, onCloseFullScreen: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    val filteredServices = remember(services, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) services else services.filter { s -> s.name.lowercase().contains(q) || s.category.key.lowercase().contains(q) || s.address?.lowercase()?.contains(q) == true || s.phone?.lowercase()?.contains(q) == true }
    }
    val mapView = remember(context, fullScreen) { createMapView(context, latitude, longitude) }
    val currentMeasureTap by rememberUpdatedState(onMeasureTap)

    DisposableEffect(mapView, measuring) {
        val listener = android.view.View.OnTouchListener { _, event ->
            if (measuring && event.action == MotionEvent.ACTION_UP) {
                val point = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt()) as GeoPoint
                currentMeasureTap(point)
            }
            false
        }
        mapView.setOnTouchListener(listener)
        onDispose { mapView.setOnTouchListener(null) }
    }
    DisposableEffect(mapView) { mapView.onResume(); onDispose { mapView.onPause(); mapView.onDetach() } }

    LaunchedEffect(latitude, longitude, accuracyMeters, filteredServices, measurePoints, route, fullScreen) {
        updateMapMarkers(mapView, latitude, longitude, validLocation, accuracyMeters, filteredServices, measurePoints, route)
        if (validLocation && mapView.tag != MAP_LOCATION_INITIALIZED) {
            mapView.controller.setCenter(GeoPoint(latitude, longitude)); mapView.controller.setZoom(if (fullScreen) 14.0 else 12.0); mapView.tag = MAP_LOCATION_INITIALIZED
        }
        mapView.invalidate()
    }

    LaunchedEffect(search) {
        if (search.trim().length >= 2) onSearch(search.trim())
    }

    Box(modifier = modifier.clip(if (fullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(16.dp))) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize(), update = { view ->
            view.contentDescription = contentDescriptionText
            updateMapMarkers(view, latitude, longitude, validLocation, accuracyMeters, filteredServices, measurePoints, route)
        })
        if (fullScreen) {
            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 56.dp, vertical = 10.dp)) {
                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (search.isNotEmpty()) IconButton({ search = "" }) { Icon(Icons.Default.Close, "Effacer") } },
                    placeholder = { Text("Rechercher un lieu ou une adresse en Algérie") }, shape = RoundedCornerShape(14.dp))
                if (searchResults.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
                        searchResults.take(6).forEach { result ->
                            Text(result.displayName, Modifier.fillMaxWidth().clickable { onSelectSearchResult(result); onRouteDestination(GeoPoint(result.latitude, result.longitude)); mapView.controller.animateTo(GeoPoint(result.latitude, result.longitude)) }.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            IconButton({ onCloseFullScreen?.invoke() }, Modifier.align(Alignment.TopStart).padding(12.dp)) { Icon(Icons.Default.Close, "Fermer la carte") }
            IconButton({ mapView.controller.setMapOrientation(0f) }, Modifier.align(Alignment.TopStart).padding(top = 68.dp, start = 12.dp)) { Icon(Icons.Default.North, "Nord") }
        } else {
            Button({ onOpenFullScreen?.invoke() }, Modifier.align(Alignment.TopEnd).padding(6.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Fullscreen, null); Text("Ouvrir la carte") }
            IconButton({ mapView.controller.setMapOrientation(0f) }, Modifier.align(Alignment.TopStart).padding(6.dp)) { Icon(Icons.Default.North, "Nord") }
        }
        if (searching || routing) Text(if (routing) "Calcul de l'itinéraire…" else "Recherche…", Modifier.align(Alignment.TopCenter).padding(top = if (fullScreen) 78.dp else 8.dp), style = MaterialTheme.typography.labelSmall)
        routeError?.let { Text(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 58.dp), style = MaterialTheme.typography.labelSmall) }
        Text(
            text = when { measuring -> measurementLabel(measurePoints); route != null -> "Itinéraire : ${formatDistance(route.distanceMeters)} · ${formatDuration(route.durationSeconds)}"; loading -> "Services en chargement…"; else -> "${filteredServices.size} service(s) · © OpenStreetMap contributors" },
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
        )
        IconButton({ if (measuring) onMeasureClear() else onMeasureStart() }, Modifier.align(Alignment.BottomStart).padding(if (fullScreen) 18.dp else 6.dp)) { Icon(Icons.Default.Straighten, if (measuring) "Annuler la mesure" else "Mesurer") }
        IconButton({ if (validLocation && route != null) mapView.controller.animateTo(GeoPoint(latitude, longitude)) }, Modifier.align(Alignment.BottomEnd).padding(if (fullScreen) 18.dp else 8.dp)) { Icon(if (route != null) Icons.Default.Navigation else Icons.Default.LocationOn, if (route != null) "Départ" else "Position actuelle") }
    }
}

private fun createMapView(context: Context, latitude: Double, longitude: Double): MapView {
    val appContext = context.applicationContext
    Configuration.getInstance().load(appContext, appContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    Configuration.getInstance().userAgentValue = String.format(Locale.US, "%s/1.0.5", appContext.packageName)
    return MapView(appContext).apply {
        setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); setBuiltInZoomControls(true); setTilesScaledToDpi(true); setUseDataConnection(true); setMinZoomLevel(3.0); setMaxZoomLevel(20.0)
        controller.setZoom(12.0); controller.setCenter(GeoPoint(latitude, longitude)); isClickable = true; isFocusable = true
    }
}

private fun updateMapMarkers(mapView: MapView, latitude: Double, longitude: Double, validLocation: Boolean, accuracyMeters: Double?, services: List<NearbyService>, measurePoints: List<GeoPoint>, route: RoadRoute?) {
    mapView.overlays.removeAll { overlay ->
        (overlay is Marker && (overlay.id == GPS_MARKER_ID || overlay.id?.startsWith(SERVICE_MARKER_PREFIX) == true || overlay.id == SEARCH_ROUTE_DESTINATION_ID)) ||
            (overlay is Polyline && overlay.id == MEASURE_LINE_ID) || (overlay is Polyline && overlay.id == ROUTE_LINE_ID)
    }
    if (validLocation) mapView.overlays.add(Marker(mapView).apply { id = GPS_MARKER_ID; position = GeoPoint(latitude, longitude); title = "Position actuelle"; snippet = accuracyMeters?.takeIf { it > 0 }?.let { String.format(Locale.US, "GPS accuracy +/- %.0f m", it) }; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) })
    clusteredServices(services).forEachIndexed { index, cluster ->
        val first = cluster.first(); val center = GeoPoint(cluster.map { it.latitude }.average(), cluster.map { it.longitude }.average())
        mapView.overlays.add(Marker(mapView).apply { id = "$SERVICE_MARKER_PREFIX$index-${first.id}"; position = center; title = if (cluster.size == 1) first.name else "${cluster.size} services à proximité"; snippet = if (cluster.size == 1) serviceSnippet(first) else cluster.take(5).joinToString("\n") { it.name }; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) })
    }
    if (measurePoints.size >= 2) mapView.overlays.add(Polyline(mapView).apply { id = MEASURE_LINE_ID; setPoints(measurePoints); width = 8f })
    measurePoints.forEachIndexed { i, point -> mapView.overlays.add(Marker(mapView).apply { id = "$SERVICE_MARKER_PREFIX-measure-$i"; position = point; title = "Point ${i + 1}"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) }) }
    route?.let { r ->
        mapView.overlays.add(Polyline(mapView).apply { id = ROUTE_LINE_ID; setPoints(r.points); width = 10f })
        r.points.lastOrNull()?.let { destination -> mapView.overlays.add(Marker(mapView).apply { id = SEARCH_ROUTE_DESTINATION_ID; position = destination; title = "Destination"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) }) }
        if (r.points.isNotEmpty()) mapView.zoomToBoundingBox(org.osmdroid.util.BoundingBox.fromGeoPoints(r.points), true, 72)
    }
}

private fun clusteredServices(services: List<NearbyService>): List<List<NearbyService>> {
    if (services.size <= 30) return services.map { listOf(it) }
    val grid = 0.003
    return services.groupBy { Pair((it.latitude / grid).toInt(), (it.longitude / grid).toInt()) }.values.toList()
}

private fun serviceSnippet(s: NearbyService): String = buildString { append(s.category.key); s.distanceMeters?.let { append(" · ").append(formatDistance(it)) }; s.address?.let { append("\n").append(it) }; s.phone?.let { append("\n").append(it) } }
private fun formatDistance(meters: Double): String = if (meters < 1_000) String.format(Locale.US, "%.0f m", meters) else String.format(Locale.US, "%.1f km", meters / 1_000.0)
private fun formatDuration(seconds: Double): String { val minutes = (seconds / 60.0).roundToInt(); return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min" }
private fun measurementLabel(points: List<GeoPoint>): String = when (points.size) { 0 -> "Touchez la carte pour le point 1"; 1 -> "Point 1 placé · touchez pour le point 2"; else -> "Distance : ${formatDistance(haversineMeters(points[0], points[1]))}" }
private fun haversineMeters(a: GeoPoint, b: GeoPoint): Double { val r = 6_371_000.0; val dLat = Math.toRadians(b.latitude - a.latitude); val dLon = Math.toRadians(b.longitude - a.longitude); val x = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2) * sin(dLon / 2); return 2 * r * atan2(sqrt(x), sqrt(1 - x)) }

private val LIVE_MAP_CATEGORIES = setOf(ServiceCategory.MECHANIC, ServiceCategory.AUTO_ELECTRICIAN, ServiceCategory.ROADSIDE_ASSISTANCE, ServiceCategory.SPARE_PARTS, ServiceCategory.FUEL_STATION, ServiceCategory.HOSPITAL, ServiceCategory.TOWING)
private const val GPS_MARKER_ID = "cardiag-gps-marker"
private const val SERVICE_MARKER_PREFIX = "cardiag-service-marker-"
private const val MEASURE_LINE_ID = "cardiag-measure-line"
private const val ROUTE_LINE_ID = "cardiag-road-route"
private const val SEARCH_ROUTE_DESTINATION_ID = "cardiag-route-destination"
private const val MAP_LOCATION_INITIALIZED = "cardiag-map-location-initialized"
