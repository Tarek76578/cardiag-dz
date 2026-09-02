package dz.cardiag.app.core.road

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Real-device Android location provider.
 *
 * Strategy:
 * 1) Reuse a recent cached fix when it is fresh enough.
 * 2) On Android 12+, ask Android's fused location provider first, then GPS/network.
 * 3) On older Android versions request a one-shot update from GPS/network.
 * 4) Always clean up listeners/cancellation so a timeout cannot leak callbacks.
 */
class AndroidLocationProvider(private val context: Context) : LocationProvider {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun manager(): LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @SuppressLint("MissingPermission")
    override suspend fun lastKnown(): CoarseLocation? {
        if (!hasPermission()) return null
        val manager = manager() ?: return null
        val now = System.currentTimeMillis()
        var best: Location? = null
        for (provider in enabledProviders(manager)) {
            val location = runCatching { manager.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (!isValid(location)) continue
            if (best == null || score(location, now) < score(best!!, now)) best = location
        }
        // Never display an old cached location as if it were current.
        return best?.takeIf { now - it.time <= MAX_LAST_KNOWN_AGE_MS }?.toCoarse("last_known_fresh")
    }

    @SuppressLint("MissingPermission")
    override suspend fun current(timeoutMs: Long): CoarseLocation? {
        if (!hasPermission()) return null
        val manager = manager() ?: return null
        if (enabledProviders(manager).isEmpty()) return null

        // A fresh cached fix is immediately useful and avoids unnecessary GPS delay.
        lastKnown()?.let { return it }

        val totalTimeout = timeoutMs.coerceIn(8_000L, 30_000L)
        val perProvider = (totalTimeout / 2L).coerceAtLeast(4_000L)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                requestCurrentLocation(manager, LocationManager.FUSED_PROVIDER, perProvider)?.let { return it }
            }
            requestCurrentLocation(manager, LocationManager.GPS_PROVIDER, perProvider)?.let { return it }
            requestCurrentLocation(manager, LocationManager.NETWORK_PROVIDER, perProvider)?.let { return it }
        } else {
            requestSingleUpdate(manager, LocationManager.GPS_PROVIDER, perProvider)?.let { return it }
            requestSingleUpdate(manager, LocationManager.NETWORK_PROVIDER, perProvider)?.let { return it }
        }

        // The location may have been populated while the live request was running.
        return lastKnown()?.copy(source = "last_known_after_request")
    }

    private fun enabledProviders(manager: LocationManager): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isEnabled(manager, LocationManager.FUSED_PROVIDER)) {
            add(LocationManager.FUSED_PROVIDER)
        }
        if (isEnabled(manager, LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
        if (isEnabled(manager, LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
    }.distinct()

    private fun isEnabled(manager: LocationManager, provider: String): Boolean =
        runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)

    @SuppressLint("MissingPermission", "NewApi")
    private suspend fun requestCurrentLocation(
        manager: LocationManager,
        provider: String,
        timeoutMs: Long
    ): CoarseLocation? {
        if (!isEnabled(manager, provider)) return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val cancellation = android.os.CancellationSignal()
                cont.invokeOnCancellation { cancellation.cancel() }
                try {
                    manager.getCurrentLocation(provider, cancellation, context.mainExecutor) { location ->
                        if (!cont.isActive) return@getCurrentLocation
                        cont.resume(location?.takeIf(::isValid)?.toCoarse(provider))
                    }
                } catch (_: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                } catch (_: IllegalArgumentException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleUpdate(
        manager: LocationManager,
        provider: String,
        timeoutMs: Long
    ): CoarseLocation? = withTimeoutOrNull(timeoutMs) {
        if (!isEnabled(manager, provider)) return@withTimeoutOrNull null
        suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    runCatching { manager.removeUpdates(this) }
                    if (cont.isActive && isValid(location)) cont.resume(location.toCoarse(provider))
                }

                @Deprecated("legacy")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            try {
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                cont.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
            } catch (_: SecurityException) {
                if (cont.isActive) cont.resume(null)
            } catch (_: IllegalArgumentException) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    private fun isValid(location: Location): Boolean =
        location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0 &&
            location.latitude != 0.0 && location.longitude != 0.0

    private fun score(location: Location, now: Long): Long =
        (now - location.time).coerceAtLeast(0L) + location.accuracy.toLong().coerceAtLeast(0L) * 1_000L
}

private fun Location.toCoarse(provider: String): CoarseLocation =
    CoarseLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else Double.NaN,
        capturedAtEpochMs = time,
        source = provider
    )

private const val MAX_LAST_KNOWN_AGE_MS = 2 * 60 * 1000L
