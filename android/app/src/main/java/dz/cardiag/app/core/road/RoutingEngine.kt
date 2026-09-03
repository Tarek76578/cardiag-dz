package dz.cardiag.app.core.road

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.osmdroid.util.GeoPoint

/** Real road routing for Map Engine V3 with a public OSRM fallback. */
class RoutingEngine(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = HttpClient(Android)
) {
    suspend fun route(start: GeoPoint, end: GeoPoint): RouteResult {
        val primaryError = runCatching { requestRoute(baseUrl, start, end) }.exceptionOrNull()
        if (primaryError == null) return requestRoute(baseUrl, start, end)

        // routing.openstreetmap.de is another OSRM server using worldwide OSM car data.
        // It is a fallback only; production should use a controlled routing backend.
        return runCatching { requestRoute(FALLBACK_BASE_URL, start, end) }.getOrElse { fallbackError ->
            error("Routing failed: ${primaryError.message ?: "primary provider unavailable"}; fallback: ${fallbackError.message ?: "unavailable"}")
        }
    }

    private suspend fun requestRoute(server: String, start: GeoPoint, end: GeoPoint): RouteResult {
        val coordinates = "${start.longitude},${start.latitude};${end.longitude},${end.latitude}"
        val response = client.get("${server.trimEnd('/')}/route/v1/driving/$coordinates") {
            parameter("overview", "full")
            parameter("geometries", "geojson")
            parameter("steps", "false")
            parameter("alternatives", "false")
        }
        val body = response.bodyAsText()
        val root = Json.parseToJsonElement(body).jsonObject
        if (root["code"]?.jsonPrimitive?.content != "Ok") {
            error(root["message"]?.jsonPrimitive?.content ?: "Routing provider returned an error")
        }
        val route = root["routes"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("No driving route found")
        val distanceMeters = route["distance"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: error("Routing response has no distance")
        val durationSeconds = route["duration"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: error("Routing response has no duration")
        val coordinatesJson = route["geometry"]?.jsonObject?.get("coordinates")?.jsonArray
            ?: error("Routing response has no geometry")
        val points = coordinatesJson.mapNotNull { pair ->
            val values = pair.jsonArray
            if (values.size < 2) null else {
                val lon = values[0].jsonPrimitive.content.toDoubleOrNull()
                val lat = values[1].jsonPrimitive.content.toDoubleOrNull()
                if (lon == null || lat == null) null else GeoPoint(lat, lon)
            }
        }
        if (points.size < 2) error("Routing response has no usable road geometry")
        return RouteResult(distanceMeters, durationSeconds, points)
    }

    fun close() = client.close()

    companion object {
        const val DEFAULT_BASE_URL = "https://router.project-osrm.org"
        const val FALLBACK_BASE_URL = "https://routing.openstreetmap.de/routed-car"
    }
}

data class RouteResult(
    val distanceMeters: Double,
    val durationSeconds: Double,
    val geometry: List<GeoPoint>
)
