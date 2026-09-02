package dz.cardiag.app.core.road

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.contentOrNull
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Live OpenStreetMap nearby search through Overpass.
 *
 * The query is deliberately small (one request per refresh, capped by the
 * requested radius) and uses a descriptive User-Agent. Results are real OSM
 * objects; missing fields remain null rather than being fabricated.
 */
class OverpassNearbyProvider(
    private val client: HttpClient = HttpClient(Android)
) : NearbySearchProvider {
    override val displayName: String = "OpenStreetMap / Overpass"
    override val isLive: Boolean = true

    override suspend fun search(
        center: CoarseLocation,
        categories: Set<ServiceCategory>,
        radiusMeters: Int,
        language: String
    ): NearbyResult {
        if (categories.isEmpty()) return NearbyResult.Success(emptyList())
        val radius = radiusMeters.coerceIn(500, 20_000)
        val query = buildQuery(center.latitude, center.longitude, radius, categories)
        return runCatching {
            val response = client.post(ENDPOINT) {
                header("User-Agent", USER_AGENT)
                header("Accept", "application/json")
                setBody("data=${java.net.URLEncoder.encode(query, Charsets.UTF_8.name())}")
            }
            if (response.status.value !in 200..299) {
                error("Overpass HTTP ${response.status.value}")
            }
            parse(response.bodyAsText(), center, language)
        }.fold(
            onSuccess = { NearbyResult.Success(it) },
            onFailure = { NearbyResult.Failure(NearbyFailure.PROVIDER_ERROR, it.message) }
        )
    }

    private fun buildQuery(lat: Double, lon: Double, radius: Int, categories: Set<ServiceCategory>): String {
        val parts = mutableListOf<String>()
        categories.forEach { category ->
            when (category) {
                ServiceCategory.MECHANIC -> parts += "nwr(around:$radius,$lat,$lon)[shop=car_repair];nwr(around:$radius,$lat,$lon)[craft=car_repair];nwr(around:$radius,$lat,$lon)[\"service:vehicle:car_repair\"=yes];"
                ServiceCategory.AUTO_ELECTRICIAN -> parts += "nwr(around:$radius,$lat,$lon)[\"service:vehicle:electrical\"=yes];nwr(around:$radius,$lat,$lon)[craft=auto_electrician];"
                ServiceCategory.ROADSIDE_ASSISTANCE -> parts += "nwr(around:$radius,$lat,$lon)[\"service:vehicle:towing\"=yes];nwr(around:$radius,$lat,$lon)[shop=towing];nwr(around:$radius,$lat,$lon)[office=towing];"
                ServiceCategory.SPARE_PARTS -> parts += "nwr(around:$radius,$lat,$lon)[shop=car_parts];nwr(around:$radius,$lat,$lon)[shop=tyres];"
                ServiceCategory.FUEL_STATION -> parts += "nwr(around:$radius,$lat,$lon)[amenity=fuel];"
                ServiceCategory.HOSPITAL -> parts += "nwr(around:$radius,$lat,$lon)[amenity=hospital];"
                ServiceCategory.TOWING -> parts += "nwr(around:$radius,$lat,$lon)[\"service:vehicle:towing\"=yes];nwr(around:$radius,$lat,$lon)[shop=towing];nwr(around:$radius,$lat,$lon)[office=towing];"
                ServiceCategory.OTHER -> parts += "nwr(around:$radius,$lat,$lon)[shop];"
            }
        }
        return "[out:json][timeout:25];(${parts.joinToString(" ")});out center tags;"
    }

    private fun parse(body: String, center: CoarseLocation, language: String): List<NearbyService> {
        val elements = Json.parseToJsonElement(body).jsonObject["elements"]?.jsonArray.orEmpty()
        val requestedCategories = inferRequestedCategories(elements)
        return elements.mapNotNull { element ->
            val obj = element.jsonObject
            val id = "osm-${obj["type"]?.jsonPrimitive?.contentOrNull}-${obj["id"]?.jsonPrimitive?.contentOrNull}" 
            val tags = obj["tags"]?.jsonObject
            val lat = obj["lat"]?.jsonPrimitive?.doubleOrNull
                ?: obj["center"]?.jsonObject?.get("lat")?.jsonPrimitive?.doubleOrNull
            val lon = obj["lon"]?.jsonPrimitive?.doubleOrNull
                ?: obj["center"]?.jsonObject?.get("lon")?.jsonPrimitive?.doubleOrNull
            if (lat == null || lon == null) return@mapNotNull null
            val category = classify(tags, requestedCategories)
            val name = tags?.get("name")?.jsonPrimitive?.contentOrNull
                ?: tags?.get("brand")?.jsonPrimitive?.contentOrNull
                ?: categoryLabel(category, language)
            NearbyService(
                id = id,
                name = name,
                category = category,
                latitude = lat,
                longitude = lon,
                distanceMeters = haversineMeters(center.latitude, center.longitude, lat, lon),
                address = formatAddress(tags),
                phone = tags?.get("phone")?.jsonPrimitive?.contentOrNull
                    ?: tags?.get("contact:phone")?.jsonPrimitive?.contentOrNull,
                openingHours = tags?.get("opening_hours")?.jsonPrimitive?.contentOrNull,
                rating = null,
                source = displayName
            )
        }.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }.distinctBy { it.id }
    }

    private fun inferRequestedCategories(elements: List<kotlinx.serialization.json.JsonElement>): Set<ServiceCategory> =
        elements.mapNotNull { e -> classify(e.jsonObject["tags"]?.jsonObject, ServiceCategory.entries.toSet()) }.toSet()

    private fun classify(
        tags: kotlinx.serialization.json.JsonObject?,
        requested: Set<ServiceCategory>
    ): ServiceCategory {
        if (tags == null) return requested.firstOrNull() ?: ServiceCategory.OTHER
        val shop = tags["shop"]?.jsonPrimitive?.contentOrNull
        val amenity = tags["amenity"]?.jsonPrimitive?.contentOrNull
        val craft = tags["craft"]?.jsonPrimitive?.contentOrNull
        val towing = tags["service:vehicle:towing"]?.jsonPrimitive?.contentOrNull
        val electrical = tags["service:vehicle:electrical"]?.jsonPrimitive?.contentOrNull
        return when {
            amenity == "fuel" -> ServiceCategory.FUEL_STATION
            amenity == "hospital" -> ServiceCategory.HOSPITAL
            shop == "car_parts" || shop == "tyres" -> ServiceCategory.SPARE_PARTS
            towing == "yes" || shop == "towing" || tags["office"]?.jsonPrimitive?.contentOrNull == "towing" -> ServiceCategory.TOWING
            electrical == "yes" || craft == "auto_electrician" -> ServiceCategory.AUTO_ELECTRICIAN
            shop == "car_repair" || craft == "car_repair" || tags["service:vehicle:car_repair"] != null -> ServiceCategory.MECHANIC
            else -> requested.firstOrNull() ?: ServiceCategory.OTHER
        }
    }

    private fun formatAddress(tags: kotlinx.serialization.json.JsonObject?): String? {
        if (tags == null) return null
        val street = tags["addr:street"]?.jsonPrimitive?.contentOrNull
        val number = tags["addr:housenumber"]?.jsonPrimitive?.contentOrNull
        val city = tags["addr:city"]?.jsonPrimitive?.contentOrNull
        return listOfNotNull(
            listOfNotNull(street, number).joinToString(" ").ifBlank { null },
            city
        ).joinToString(", ").ifBlank { null }
    }

    private fun categoryLabel(category: ServiceCategory, language: String): String =
        OfflineRoadDataProvider.categoryDescriptions[category.key]?.get(if (language == "ar") "ar" else "fr")
            ?: category.key

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    companion object {
        private const val ENDPOINT = "https://overpass-api.de/api/interpreter"
        private const val USER_AGENT = "CarDiagDZ/1.0 (OpenStreetMap nearby services)"
    }
}
