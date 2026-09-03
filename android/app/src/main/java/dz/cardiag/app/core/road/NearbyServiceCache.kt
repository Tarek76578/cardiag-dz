package dz.cardiag.app.core.road

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Small persistent TTL cache so the map can render recent services immediately. */
class NearbyServiceCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun read(latitude: Double, longitude: Double, radiusMeters: Int, maxAgeMs: Long = DEFAULT_MAX_AGE_MS): List<NearbyService> {
        val key = key(latitude, longitude, radiusMeters)
        val timestamp = prefs.getLong("$key.time", 0L)
        if (timestamp <= 0L || System.currentTimeMillis() - timestamp > maxAgeMs) return emptyList()
        return runCatching { json.decodeFromString<List<CachedService>>(prefs.getString("$key.data", "[]") ?: "[]").map { it.toNearbyService() } }.getOrDefault(emptyList())
    }

    fun write(latitude: Double, longitude: Double, radiusMeters: Int, services: List<NearbyService>) {
        val key = key(latitude, longitude, radiusMeters)
        val cached = services.map { CachedService.from(it) }
        prefs.edit().putLong("$key.time", System.currentTimeMillis()).putString("$key.data", json.encodeToString(cached)).apply()
    }

    private fun key(latitude: Double, longitude: Double, radiusMeters: Int): String {
        val lat = (latitude * 100).toInt()
        val lon = (longitude * 100).toInt()
        return "cell_${lat}_${lon}_$radiusMeters"
    }

    @Serializable
    private data class CachedService(
        val id: String, val name: String, val category: String,
        val latitude: Double, val longitude: Double, val distanceMeters: Double?,
        val address: String?, val phone: String?, val openingHours: String?, val source: String
    ) {
        fun toNearbyService() = NearbyService(
            id, name, ServiceCategory.entries.firstOrNull { it.key == category } ?: ServiceCategory.OTHER,
            latitude, longitude, distanceMeters, address, phone, openingHours, null, source
        )
        companion object { fun from(s: NearbyService) = CachedService(s.id, s.name, s.category.key, s.latitude, s.longitude, s.distanceMeters, s.address, s.phone, s.openingHours, s.source) }
    }

    companion object { private const val PREFS = "cardiag-nearby-cache"; private const val DEFAULT_MAX_AGE_MS = 15 * 60 * 1000L }
}
