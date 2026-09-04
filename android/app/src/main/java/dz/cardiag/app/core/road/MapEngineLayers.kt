package dz.cardiag.app.core.road

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource

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
 * Satellite and terrain providers are deliberately isolated and can be replaced
 * by a licensed commercial provider without changing the map UI.
 */
object MapEngineLayers {
    val standard: ITileSource = TileSourceFactory.MAPNIK

    val satellite: ITileSource = XYTileSource(
        "CarDiag-Satellite",
        0,
        19,
        256,
        ".jpg",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
        "© Esri"
    )

    val terrain: ITileSource = XYTileSource(
        "CarDiag-Terrain",
        0,
        17,
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

/** Offline mode controls network tile downloads. Existing osmdroid cache/archive data remains usable. */
class OfflineMapController {
    var enabled: Boolean = false
        private set

    fun apply(mapView: org.osmdroid.views.MapView, offline: Boolean) {
        enabled = offline
        mapView.setUseDataConnection(!offline)
        mapView.invalidate()
    }
}
