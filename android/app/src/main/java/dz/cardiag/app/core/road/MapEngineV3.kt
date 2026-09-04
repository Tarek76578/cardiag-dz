package dz.cardiag.app.core.road

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.osmdroid.util.GeoPoint
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

data class TurnInstruction(
    val text: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val latitude: Double,
    val longitude: Double,
    val maneuverType: String,
    val modifier: String? = null,
    val roadName: String? = null,
    val exit: Int? = null
)

data class RoadRoute(
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val steps: List<TurnInstruction> = emptyList()
)

/**
 * Network map services for Map Engine V3.
 * Nominatim provides place/geocoding search; OSRM provides road geometry,
 * distance, duration and turn-by-turn maneuver data.
 */
class MapEngineV3(private val client: HttpClient = HttpClient(Android)) {
    private var lastSearchAtMs = 0L
    private val searchCache = LinkedHashMap<String, List<MapSearchResult>>(32, 0.75f, true)

    suspend fun search(query: String, language: String = "fr", limit: Int = 8): List<MapSearchResult> {
        val normalized = query.trim().replace(Regex("\\s+"), " ")
        if (normalized.length < 2) return emptyList()
        val key = "${language.lowercase()}:$normalized:${limit.coerceIn(1, 10)}"
        searchCache[key]?.let { return it }

        val wait = 1_000L - (System.currentTimeMillis() - lastSearchAtMs)
        if (wait > 0) delay(wait)

        return runCatching {
            val body = client.get(NOMINATIM_URL) {
                parameter("q", normalized)
                parameter("format", "jsonv2")
                parameter("limit", limit.coerceIn(1, 10))
                parameter("countrycodes", "dz")
                parameter("addressdetails", 1)
                parameter("accept-language", if (language == "ar") "ar,fr;q=0.8" else "fr,ar;q=0.8")
                header("User-Agent", USER_AGENT)
            }.bodyAsText()
            lastSearchAtMs = System.currentTimeMillis()
            val results = Json.parseToJsonElement(body).jsonArray.mapNotNull { item ->
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
            searchCache[key] = results
            while (searchCache.size > 32) searchCache.remove(searchCache.entries.first().key)
            results
        }.getOrDefault(emptyList())
    }

    suspend fun route(from: GeoPoint, to: GeoPoint): Result<RoadRoute> = runCatching {
        val body = client.get("$OSRM_URL/${from.longitude},${from.latitude};${to.longitude},${to.latitude}") {
            parameter("overview", "full")
            parameter("geometries", "geojson")
            parameter("steps", "true")
            parameter("language", "fr")
            header("User-Agent", USER_AGENT)
        }.bodyAsText()
        val root = Json.parseToJsonElement(body).jsonObject
        if (root["code"]?.jsonPrimitive?.content != "Ok") {
            error("Routing provider returned ${root["code"]?.jsonPrimitive?.content ?: "unknown error"}")
        }
        val route = root["routes"]?.jsonArray?.firstOrNull()?.jsonObject ?: error("No route")
        val distance = route["distance"]?.jsonPrimitive?.doubleOrNull ?: error("Missing route distance")
        val duration = route["duration"]?.jsonPrimitive?.doubleOrNull ?: error("Missing route duration")
        val coordinates = route["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: error("Missing route geometry")
        val points = coordinates.mapNotNull { pair ->
            val values = pair.jsonArray
            val lon = values.getOrNull(0)?.jsonPrimitive?.doubleOrNull
            val lat = values.getOrNull(1)?.jsonPrimitive?.doubleOrNull
            if (lat == null || lon == null) null else GeoPoint(lat, lon)
        }
        if (points.size < 2) error("Route geometry is too short")

        val steps = route["legs"]?.jsonArray.orEmpty().flatMap { leg ->
            leg.jsonObject["steps"]?.jsonArray.orEmpty().mapNotNull { step ->
                val obj = step.jsonObject
                val maneuver = obj["maneuver"]?.jsonObject ?: return@mapNotNull null
                val location = maneuver["location"]?.jsonArray ?: return@mapNotNull null
                val lon = location.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val lat = location.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val type = maneuver["type"]?.jsonPrimitive?.content ?: "turn"
                val modifier = maneuver["modifier"]?.jsonPrimitive?.content
                val distanceMeters = obj["distance"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val durationSeconds = obj["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                val roadName = obj["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                val exit = maneuver["exit"]?.jsonPrimitive?.intOrNull
                TurnInstruction(
                    text = buildInstruction(type, modifier, roadName, exit),
                    distanceMeters = distanceMeters,
                    durationSeconds = durationSeconds,
                    latitude = lat,
                    longitude = lon,
                    maneuverType = type,
                    modifier = modifier,
                    roadName = roadName,
                    exit = exit
                )
            }
        }
        RoadRoute(points, distance, duration, steps)
    }

    fun close() = client.close()

    companion object {
        private const val NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"
        private const val OSRM_URL = "https://router.project-osrm.org/route/v1/driving"
        private const val USER_AGENT = "CarDiagDZ/1.0 MapEngineV3"

        private fun buildInstruction(type: String, modifier: String?, roadName: String?, exit: Int?): String {
            val road = roadName?.let { " — $it" } ?: ""
            return when (type.lowercase()) {
                "depart" -> "Départ$road"
                "arrive" -> "Vous êtes arrivé"
                "roundabout", "rotary" -> if (exit != null) "Rond-point, sortie $exit$road" else "Rond-point$road"
                "merge" -> "Rejoignez la voie${modifierText(modifier)}$road"
                "on ramp" -> "Prenez la bretelle${modifierText(modifier)}$road"
                "off ramp" -> "Prenez la sortie${modifierText(modifier)}$road"
                "fork" -> "À la bifurcation, ${direction(modifier)}$road"
                "end of road" -> "Au bout de la route, ${direction(modifier)}$road"
                "new name" -> "Continuez$road"
                "continue" -> "Continuez ${direction(modifier)}$road"
                else -> "Tournez ${direction(modifier)}$road"
            }
        }

        private fun modifierText(modifier: String?): String = modifier?.let { " ${direction(it)}" } ?: ""

        private fun direction(modifier: String?): String = when (modifier?.lowercase()) {
            "left", "sharp left", "slight left" -> "à gauche"
            "right", "sharp right", "slight right" -> "à droite"
            "uturn" -> "faites demi-tour"
            "straight" -> "tout droit"
            else -> "la direction indiquée"
        }
    }
}

fun roadDistanceFallbackMeters(a: GeoPoint, b: GeoPoint): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val x = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * atan2(sqrt(x), sqrt(1 - x))
}
