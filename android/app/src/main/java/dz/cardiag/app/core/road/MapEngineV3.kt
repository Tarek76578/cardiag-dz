package dz.cardiag.app.core.road

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class MapSearchResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val displayName: String,
    val type: String? = null
)

data class RoadRoute(
    val points: List<org.osmdroid.util.GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double
)

/**
 * Network map services for Map Engine V3.
 * Nominatim provides place/geocoding search; OSRM provides road geometry,
 * driving distance and duration. UI code depends only on these models.
 */
class MapEngineV3(private val client: HttpClient = HttpClient(Android)) {
    suspend fun search(query: String, language: String = "fr", limit: Int = 8): List<MapSearchResult> {
        if (query.isBlank()) return emptyList()
        return runCatching {
            val body = client.get(NOMINATIM_URL) {
                parameter("q", query.trim())
                parameter("format", "jsonv2")
                parameter("limit", limit.coerceIn(1, 10))
                parameter("countrycodes", "dz")
                parameter("addressdetails", 1)
                header("Accept-Language", if (language == "ar") "ar,fr;q=0.8" else "fr,ar;q=0.8")
                header("User-Agent", USER_AGENT)
            }.bodyAsText()
            Json.parseToJsonElement(body).jsonArray.mapNotNull { item ->
                val obj = item.jsonObject
                val lat = obj["lat"]?.jsonPrimitive?.doubleOrNull
                val lon = obj["lon"]?.jsonPrimitive?.doubleOrNull
                val name = obj["name"]?.jsonPrimitive?.content
                    ?: obj["display_name"]?.jsonPrimitive?.content
                    ?: return@mapNotNull null
                if (lat == null || lon == null) null else MapSearchResult(
                    name = name,
                    latitude = lat,
                    longitude = lon,
                    displayName = obj["display_name"]?.jsonPrimitive?.content ?: name,
                    type = obj["type"]?.jsonPrimitive?.content
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun route(from: org.osmdroid.util.GeoPoint, to: org.osmdroid.util.GeoPoint): Result<RoadRoute> = runCatching {
        val body = client.get("$OSRM_URL/${from.longitude},${from.latitude};${to.longitude},${to.latitude}") {
            parameter("overview", "full")
            parameter("geometries", "geojson")
            parameter("steps", "false")
            header("User-Agent", USER_AGENT)
        }.bodyAsText()
        val root = Json.parseToJsonElement(body).jsonObject
        if (root["code"]?.jsonPrimitive?.content != "Ok") error("Routing provider returned ${root["code"]?.jsonPrimitive?.content}")
        val route = root["routes"]?.jsonArray?.firstOrNull()?.jsonObject ?: error("No route")
        val distance = route["distance"]?.jsonPrimitive?.doubleOrNull ?: error("Missing route distance")
        val duration = route["duration"]?.jsonPrimitive?.doubleOrNull ?: error("Missing route duration")
        val coordinates = route["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: error("Missing route geometry")
        val points = coordinates.mapNotNull { pair ->
            val values = pair.jsonArray
            val lon = values.getOrNull(0)?.jsonPrimitive?.doubleOrNull
            val lat = values.getOrNull(1)?.jsonPrimitive?.doubleOrNull
            if (lat == null || lon == null) null else org.osmdroid.util.GeoPoint(lat, lon)
        }
        if (points.size < 2) error("Route geometry is too short")
        RoadRoute(points, distance, duration)
    }

    companion object {
        private const val NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"
        private const val OSRM_URL = "https://router.project-osrm.org/route/v1/driving"
        private const val USER_AGENT = "CarDiagDZ/1.0 MapEngineV3"
    }
}

fun roadDistanceFallbackMeters(a: org.osmdroid.util.GeoPoint, b: org.osmdroid.util.GeoPoint): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val x = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(x), sqrt(1 - x))
}
