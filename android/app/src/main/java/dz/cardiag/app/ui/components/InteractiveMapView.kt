package dz.cardiag.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dz.cardiag.app.core.road.MapDefaults
import dz.cardiag.app.core.road.MAP_MAX_ZOOM
import dz.cardiag.app.core.road.MAP_MIN_ZOOM
import dz.cardiag.app.core.road.MapProjection
import dz.cardiag.app.core.road.MapSize
import kotlin.math.PI
import kotlin.math.cos

/**
 * Lightweight interactive map widget rendered with Compose `Canvas`.
 *
 * The widget has no external SDK dependency: it draws a coarse
 * graticule, an Algeria bounding-box hint, the current location dot
 * (with an accuracy halo), and a center crosshair. The user can pan
 * with a single finger and zoom in / out with a pinch gesture.
 *
 * Intentionally minimal: this is a companion view that helps the
 * driver confirm where the device thinks they are while the app
 * collects a real GPS fix. It does **not** render roads, buildings,
 * or business data — those live in external map applications that
 * the driver can hand off to via the existing `ra_open_map` actions.
 */
@Composable
fun InteractiveMapView(
    latitude: Double?,
    longitude: Double?,
    accuracyMeters: Double?,
    modifier: Modifier = Modifier,
    contentDescriptionText: String
) {
    val resolvedLat = latitude ?: MapDefaults.DEFAULT_LATITUDE
    val resolvedLon = longitude ?: MapDefaults.DEFAULT_LONGITUDE
    val usingDefault = latitude == null || longitude == null ||
        latitude.isNaN() || longitude.isNaN() ||
        latitude == 0.0 && longitude == 0.0

    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    val textMeasurer = rememberTextMeasurer()

    val surfaceColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .semantics { contentDescription = contentDescriptionText }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(MAP_MIN_ZOOM, MAP_MAX_ZOOM)
                    panX += pan.x
                    panY += pan.y
                }
            }
    ) {
        drawMapContent(
            resolvedLat = resolvedLat,
            resolvedLon = resolvedLon,
            usingDefault = usingDefault,
            accuracyMeters = accuracyMeters,
            zoom = zoom,
            panX = panX,
            panY = panY,
            outlineColor = outlineColor,
            primaryColor = primaryColor,
            onPrimary = onPrimary,
            onSurfaceVariant = onSurfaceVariant,
            label = if (usingDefault) MapDefaults.DEFAULT_REGION_NAME else null,
            textMeasurer = textMeasurer
        )
    }
}

/**
 * Pure (but Compose-bound) drawing routine. Split out so the gesture
 * + state block above stays small and the visual logic is one screenful.
 */
private fun DrawScope.drawMapContent(
    resolvedLat: Double,
    resolvedLon: Double,
    usingDefault: Boolean,
    accuracyMeters: Double?,
    zoom: Float,
    panX: Float,
    panY: Float,
    outlineColor: Color,
    primaryColor: Color,
    onPrimary: Color,
    onSurfaceVariant: Color,
    label: String?,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val sizeInfo = MapSize(widthPx = size.width, heightPx = size.height)
    val centerLat = resolvedLat - panY / (4f * zoom)
    val centerLon = resolvedLon - panX / (4f * zoom * cos(centerLat * PI / 180.0).coerceAtLeast(0.01))

    // Background graticule: thin grid every ~5° latitude / longitude.
    val graticuleColor = outlineColor.copy(alpha = 0.35f)
    val gridStep = 5.0
    val latStart = ((centerLat - 30).toInt() / 5) * 5
    val latEnd = ((centerLat + 30).toInt() / 5 + 1) * 5
    var lat = latStart.toDouble()
    while (lat <= latEnd) {
        val p = MapProjection.worldToScreen(
            latitude = lat, longitude = centerLon,
            centerLat = centerLat, centerLon = centerLon,
            zoom = zoom, size = sizeInfo
        )
        if (p != null) {
            drawLine(
                color = graticuleColor,
                start = Offset(0f, p.y),
                end = Offset(size.width, p.y),
                strokeWidth = 1f
            )
        }
        lat += gridStep
    }
    val lonStart = ((centerLon - 60).toInt() / 5) * 5
    val lonEnd = ((centerLon + 60).toInt() / 5 + 1) * 5
    var lon = lonStart.toDouble()
    while (lon <= lonEnd) {
        val p = MapProjection.worldToScreen(
            latitude = centerLat, longitude = lon,
            centerLat = centerLat, centerLon = centerLon,
            zoom = zoom, size = sizeInfo
        )
        if (p != null) {
            drawLine(
                color = graticuleColor,
                start = Offset(p.x, 0f),
                end = Offset(p.x, size.height),
                strokeWidth = 1f
            )
        }
        lon += gridStep
    }

    // Algeria-region hint rectangle (approximate country bounds).
    val algeriaNw = MapProjection.worldToScreen(
        latitude = 37.1, longitude = -8.7,
        centerLat = centerLat, centerLon = centerLon,
        zoom = zoom, size = sizeInfo
    )
    val algeriaSe = MapProjection.worldToScreen(
        latitude = 19.0, longitude = 12.0,
        centerLat = centerLat, centerLon = centerLon,
        zoom = zoom, size = sizeInfo
    )
    if (algeriaNw != null && algeriaSe != null) {
        val left = minOf(algeriaNw.x, algeriaSe.x)
        val right = maxOf(algeriaNw.x, algeriaSe.x)
        val top = minOf(algeriaNw.y, algeriaSe.y)
        val bottom = maxOf(algeriaNw.y, algeriaSe.y)
        drawRect(
            color = primaryColor.copy(alpha = 0.12f),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top)
        )
        drawRect(
            color = primaryColor.copy(alpha = 0.55f),
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            style = Stroke(width = 2f)
        )
    }

    // Accuracy halo + location dot (drawn only when we have a real fix).
    if (!usingDefault && accuracyMeters != null && accuracyMeters > 0.0) {
        val haloRadiusPx = (accuracyMeters / 1000.0 / 111.0 * 4f * zoom)
            .toFloat()
            .coerceAtMost(size.minDimension / 2f)
        val user = MapProjection.worldToScreen(
            latitude = resolvedLat, longitude = resolvedLon,
            centerLat = centerLat, centerLon = centerLon,
            zoom = zoom, size = sizeInfo
        )
        if (user != null && haloRadiusPx > 2f) {
            drawCircle(
                color = primaryColor.copy(alpha = 0.18f),
                radius = haloRadiusPx,
                center = Offset(user.x, user.y)
            )
        }
    }
    if (!usingDefault) {
        val user = MapProjection.worldToScreen(
            latitude = resolvedLat, longitude = resolvedLon,
            centerLat = centerLat, centerLon = centerLon,
            zoom = zoom, size = sizeInfo
        )
        if (user != null) {
            drawCircle(
                color = primaryColor,
                radius = 10f,
                center = Offset(user.x, user.y)
            )
            drawCircle(
                color = onPrimary,
                radius = 4f,
                center = Offset(user.x, user.y)
            )
        }
    }

    // Center crosshair (visual reference for the canvas origin).
    val cx = size.width / 2f
    val cy = size.height / 2f
    val crossColor = onSurfaceVariant.copy(alpha = 0.7f)
    drawLine(crossColor, Offset(cx - 8f, cy), Offset(cx + 8f, cy), strokeWidth = 2f)
    drawLine(crossColor, Offset(cx, cy - 8f), Offset(cx, cy + 8f), strokeWidth = 2f)

    // Subtle "default region" label so the driver understands what
    // they are looking at when there is no GPS fix.
    if (label != null) {
        val layout = textMeasurer.measure(
            text = AnnotatedString(label),
            style = TextStyle(
                color = onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(12f, 12f)
        )
    }
}
