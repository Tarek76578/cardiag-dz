package dz.cardiag.app.core.road

import android.content.Context
import java.io.File
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.views.MapView

/** Visual map layers supported by the Map Engine. */
enum class MapLayer(val label: String) {
    STANDARD("Carte"),
    SATELLITE("Satellite"),
    TERRAIN("Terrain")
}

enum class LiveLayerStatus {
    AVAILABLE,
    PROVIDER_REQUIRED
}

/**
 * Centralizes tile-source definitions so the UI never embeds provider URLs.
 * Satellite/terrain are intentionally conservative on maximum zoom to reduce
 * tile pressure on mobile devices.
 */
object MapEngineLayers {
    val standard: ITileSource = TileSourceFactory.MAPNIK

    val satellite: ITileSource = XYTileSource(
        "CarDiag-Satellite",
        0,
        18,
        256,
        ".jpg",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
        "© Esri"
    )

    val terrain: ITileSource = XYTileSource(
        "CarDiag-Terrain",
        0,
        16,
        256,
        ".png",
        arrayOf("https://a.tile.opentopomap.org/"),
        "© OpenTopoMap (CC-BY-SA) © OpenStreetMap contributors"
    )

    fun source(layer: MapLayer): ITileSource = when (layer) {
        MapLayer.STANDARD -> standard
        MapLayer.SATELLITE -> satellite
        MapLayer.TERRAIN -> terrain
    }
}

/**
 * Controls network access for the map and makes the osmdroid tile cache
 * persistent. Offline mode therefore works immediately with tiles already
 * cached on the device; a complete Algeria package can be dropped into the
 * same storage later without changing the UI contract.
 */
class OfflineMapController(context: Context) {
    private val appContext = context.applicationContext
    private val offlineRoot = File(appContext.filesDir, "cardiag/offline-map")
    private val tileCache = File(offlineRoot, "tiles")

    var enabled: Boolean = false
        private set

    init {
        prepareStorage()
    }

    private fun prepareStorage() {
        if (!offlineRoot.exists()) offlineRoot.mkdirs()
        if (!tileCache.exists()) tileCache.mkdirs()
        val configuration = Configuration.getInstance()
        configuration.osmdroidBasePath = offlineRoot
        configuration.osmdroidTileCache = tileCache
    }

    fun apply(mapView: MapView, offline: Boolean) {
        prepareStorage()
        enabled = offline
        mapView.setUseDataConnection(!offline)
        mapView.invalidate()
    }

    /** True when the persistent osmdroid tile cache contains usable data. */
    fun hasCachedMapData(): Boolean = tileCache.walkTopDown().any { it.isFile && it.length() > 0L }

    fun storagePath(): File = offlineRoot
}
