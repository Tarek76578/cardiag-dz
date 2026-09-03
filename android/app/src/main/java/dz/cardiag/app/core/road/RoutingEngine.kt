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

/** Real road routing for Map Engine V3. The provider is injectable for production/self-hosted routing. */
class RoutingEngine(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val client: HttpClient = HttpClient(Android)
) {
    suspend fun route(start: GeoPoint, end: GeoPoint): RouteResult {
        val coordinates = "${start.longitude},${start.latitude};${end.longitude},${end.latitude}"
        val response = client.get("${baseUrl.trimEnd('/')}/route/v1/driving/$coordinates") {
            parameter("overview", "full")
            parameter("geometries", "geojson")
            parameter("steps", "false")
        }
        val root = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        if (root["code"]?.jsonPrimitive?.content != "Ok") {
            error(root["message"]?.jsonPrimitive?.content ?: "Routing provider returned an error")
        }
        val route = root["routes"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("No driving route found")
        val distanceMeters = route["distance"]?.jsonPrimitive?.double
            ?: error("Routing response has no distance")
        val durationSeconds = route["duration"]?.jsonPrimitive?.double
            ?: error("Routing response has no duration")
        val coordinatesJson = route["geometry"]?.jsonObject?.get("coordinates")?.jsonArray
            ?: error("Routing response has no geometry")
        val points = coordinatesJson.mapNotNull { pair ->
            val values = pair.jsonArray
            if (values.size < 2) null else GeoPoint(values[1].jsonPrimitive.double, values[0].jsonPrimitive.double)
        }
        return RouteResult(distanceMeters, durationSeconds, points)
    }

    fun close() = client.close()

    companion object { const val DEFAULT_BASE_URL = "https://router.project-osrm.org" }
}

data class RouteResult(
    val distanceMeters: Double,
    val durationSeconds: Double,
    val geometry: List<GeoPoint>
)
