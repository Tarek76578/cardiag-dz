package dz.cardiag.app.core.road

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Android [LocationManager]-based coarse location provider. Used only when
 * the user has accepted the location permission. Falls back to the last
 * known fix if the system has one cached.
 */
class AndroidLocationProvider(private val context: Context) : LocationProvider {

    fun hasPermission(): Boolean {
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return coarse || fine
    }

    @SuppressLint("MissingPermission")
    override suspend fun lastKnown(): CoarseLocation? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = manager.getProviders(true)
        var best: Location? = null
        for (provider in providers) {
            val l = runCatching { manager.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (best == null || l.accuracy < best.accuracy) best = l
        }
        return best?.toCoarse(provider = "last_known")
    }

    @SuppressLint("MissingPermission")
    override suspend fun current(timeoutMs: Long): CoarseLocation? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = manager.getProviders(true)
        // First, try last known quickly.
        lastKnown()?.let { return it }
        // Otherwise, request a single update with timeout.
        val firstProvider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
            ?: return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                try {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            runCatching { manager.removeUpdates(this) }
                            if (cont.isActive) cont.resume(location.toCoarse(firstProvider))
                        }
                        @Deprecated("legacy")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                    }
                    manager.requestSingleUpdate(firstProvider, listener, Looper.getMainLooper())
                    cont.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
                } catch (e: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                } catch (e: IllegalArgumentException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }
}

private fun Location.toCoarse(provider: String): CoarseLocation =
    CoarseLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else Double.NaN,
        capturedAtEpochMs = time,
        source = provider
    )
