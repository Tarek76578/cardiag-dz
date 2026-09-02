package dz.cardiag.app.core.road

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.*

/**
 * Live nearby-services provider backed by OpenStreetMap data through Overpass.
 * No account or AI provider is required. Results are returned only when the
 * upstream response contains real OSM objects with coordinates.
 */
class OverpassNearbyProvider(
    private val client: HttpClient = HttpClient(Android),
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : NearbySearchProvider {

    override val displayName: String = "OpenStreetMap / Overpass"
    override val isLive: Boolean = true

    override suspend fun search(
        center: CoarseLocation,
        categories: Set<ServiceCategory>,
        radiusMeters: Int,
        language: String
    ): NearbyResult = withContext(Dispatchers.IO) {
        if (categories.isEmpty()) return@withContext NearbyResult.Success(emptyList())
        val radius = radiusMeters.coerceIn(250, 25_000)
        val query = buildQuery(center, categories, radius)

        runCatching {
            val response = client.post(endpoint) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(listOf("data" to query).formUrlEncode())
            }
            val payload = response.bodyAsText()
            if (response.status.value !in 200..299) {
                return@runCatching NearbyResult.Failure(
                    NearbyFailure.PROVIDER_ERROR,
                    "Overpass HTTP ${response.status.value}"
                )
            }
            parse(payload, center, categories, language)
        }.getOrElse { error ->
            NearbyResult.Failure(
                NearbyFailure.NETWORK_UNAVAILABLE,
                error.message?.take(240)
            )
        }
    }

    private fun buildQuery(
        center: CoarseLocation,
        categories: Set<ServiceCategory>,
        radius: Int
    ): String {
        val lat = center.latitude
        val lon = center.longitude
        val clauses = buildList {
            if (ServiceCategory.MECHANIC in categories) {
                add("node(around:$radius,$lat,$lon)[shop=car_repair];")
                add("way(around:$radius,$lat,$lon)[shop=car_repair];")
                add("relation(around:$radius,$lat,$lon)[shop=car_repair];")
            }
            if (ServiceCategory.AUTO_ELECTRICIAN in categories) {
                add("node(around:$radius,$lat,$lon)[shop=car_repair][car_repair=auto_electrician];")
                add("way(around:$radius,$lat,$lon)[shop=car_repair][car_repair=auto_electrician];")
                add("relation(around:$radius,$lat,$lon)[shop=car_repair][car_repair=auto_electrician];")
            }
            if (ServiceCategory.ROADSIDE_ASSISTANCE in categories || ServiceCategory.TOWING in categories) {
                add("node(around:$radius,$lat,$lon)[emergency=roadside_assistance];")
                add("way(around:$radius,$lat,$lon)[emergency=roadside_assistance];")
                add("relation(around:$radius,$lat,$lon)[emergency=roadside_assistance];")
                add("node(around:$radius,$lat,$lon)[amenity=car_rental][service=towing];")
            }
            if (ServiceCategory.SPARE_PARTS in categories) {
                add("node(around:$radius,$lat,$lon)[shop=car_parts];")
                add("way(around:$radius,$lat,$lon)[shop=car_parts];")
                add("relation(around:$radius,$lat,$lon)[shop=car_parts];")
            }
            if (ServiceCategory.FUEL_STATION in categories) {
                add("node(around:$radius,$lat,$lon)[amenity=fuel];")
                add("way(around:$radius,$lat,$lon)[amenity=fuel];")
                add("relation(around:$radius,$lat,$lon)[amenity=fuel];")
            }
            if (ServiceCategory.HOSPITAL in categories) {
                add("node(around:$radius,$lat,$lon)[amenity=hospital];")
                add("way(around:$radius,$lat,$lon)[amenity=hospital];")
                add("relation(around:$radius,$lat,$lon)[amenity=hospital];")
            }
        }
        return "[out:json][timeout:20];(${clauses.joinToString("\n")});out center tags;"
    }

    private fun parse(
        payload: String,
        center: CoarseLocation,
        requested: Set<ServiceCategory>,
        language: String
    ): NearbyResult {
        val root = json.parseToJsonElement(payload).jsonObject
        val elements = root["elements"]?.jsonArray ?: return NearbyResult.Failure(NearbyFailure.PROVIDER_ERROR, "Invalid Overpass response")
        val results = mutableListOf<NearbyService>()
        val seen = mutableSetOf<String>()

        for (element in elements) {
            val obj = element.jsonObject
            val id = "osm-${obj["type"]?.jsonPrimitive?.contentOrNull}-${obj["id"]?.jsonPrimitive?.contentOrNull}"
            if (!seen.add(id)) continue
            val tags = obj["tags"]?.jsonObject ?: JsonObject(emptyMap())
            val coords = coordinates(obj) ?: continue
            val category = classify(tags, requested) ?: continue
            val distance = distanceMeters(center.latitude, center.longitude, coords.first, coords.second)
            results += NearbyService(
                id = id,
                name = nameFromTags(tags, language) ?: categoryLabel(category, language),
                category = category,
                latitude = coords.first,
                longitude = coords.second,
                distanceMeters = distance,
                address = addressFromTags(tags),
                phone = tag(tags, "phone") ?: tag(tags, "contact:phone"),
                openingHours = tag(tags, "opening_hours"),
                rating = null,
                source = displayName
            )
        }

        return NearbyResult.Success(results.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }.take(100))
    }

    private fun coordinates(obj: JsonObject): Pair<Double, Double>? {
        val lat = obj["lat"]?.jsonPrimitive?.doubleOrNull
        val lon = obj["lon"]?.jsonPrimitive?.doubleOrNull
        if (lat != null && lon != null) return lat to lon
        val center = obj["center"]?.jsonObject ?: return null
        return center["lat"]?.jsonPrimitive?.doubleOrNull?.let { la ->
            center["lon"]?.jsonPrimitive?.doubleOrNull?.let { lo -> la to lo }
        }
    }

    private fun classify(tags: JsonObject, requested: Set<ServiceCategory>): ServiceCategory? {
        val shop = tag(tags, "shop")
        val amenity = tag(tags, "amenity")
        val emergency = tag(tags, "emergency")
        val repair = tag(tags, "car_repair")
        return when {
            ServiceCategory.SPARE_PARTS in requested && shop == "car_parts" -> ServiceCategory.SPARE_PARTS
            ServiceCategory.AUTO_ELECTRICIAN in requested && repair == "auto_electrician" -> ServiceCategory.AUTO_ELECTRICIAN
            ServiceCategory.MECHANIC in requested && shop == "car_repair" -> ServiceCategory.MECHANIC
            ServiceCategory.ROADSIDE_ASSISTANCE in requested && emergency == "roadside_assistance" -> ServiceCategory.ROADSIDE_ASSISTANCE
            ServiceCategory.TOWING in requested && emergency == "roadside_assistance" -> ServiceCategory.TOWING
            ServiceCategory.FUEL_STATION in requested && amenity == "fuel" -> ServiceCategory.FUEL_STATION
            ServiceCategory.HOSPITAL in requested && amenity == "hospital" -> ServiceCategory.HOSPITAL
            else -> null
        }
    }

    private fun nameFromTags(tags: JsonObject, language: String): String? =
        if (language == "ar") {
            tag(tags, "name:ar") ?: tag(tags, "name:fr") ?: tag(tags, "name")
        } else {
            tag(tags, "name:fr") ?: tag(tags, "name") ?: tag(tags, "name:ar")
        }

    private fun addressFromTags(tags: JsonObject): String? {
        val parts = listOf(
            tag(tags, "addr:housenumber"), tag(tags, "addr:street"),
            tag(tags, "addr:city"), tag(tags, "addr:postcode")
        ).filterNotNull().filter { it.isNotBlank() }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }

    private fun tag(tags: JsonObject, key: String): String? =
        (tags[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun categoryLabel(category: ServiceCategory, language: String): String = when (category) {
        ServiceCategory.MECHANIC -> if (language == "ar") "ميكانيكي" else "Garage / mécanicien"
        ServiceCategory.AUTO_ELECTRICIAN -> if (language == "ar") "كهربائي سيارات" else "Électricien automobile"
        ServiceCategory.ROADSIDE_ASSISTANCE -> if (language == "ar") "مساعدة على الطريق" else "Dépannage"
        ServiceCategory.SPARE_PARTS -> if (language == "ar") "قطع غيار" else "Pièces automobiles"
        ServiceCategory.FUEL_STATION -> if (language == "ar") "محطة وقود" else "Station-service"
        ServiceCategory.HOSPITAL -> if (language == "ar") "مستشفى" else "Hôpital"
        ServiceCategory.TOWING -> if (language == "ar") "سحب المركبات" else "Remorquage"
        ServiceCategory.OTHER -> if (language == "ar") "خدمة سيارات" else "Service automobile"
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://overpass-api.de/api/interpreter"
    }
}
