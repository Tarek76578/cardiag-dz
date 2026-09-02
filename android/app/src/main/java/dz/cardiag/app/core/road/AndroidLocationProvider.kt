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
 * Android [LocationManager]-based coarse location provider. Used only when
 * the user has accepted the location permission.
 *
 * `current()` deliberately requests a fresh bounded fix instead of returning
 * an arbitrary cached location. This keeps the GPS map honest: a marker is
 * rendered only after Android supplies a real location for this request.
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
            val location = runCatching { manager.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (best == null || location.accuracy < best.accuracy) best = location
        }
        return best?.toCoarse(provider = "last_known")
    }

    @SuppressLint("MissingPermission")
    override suspend fun current(timeoutMs: Long): CoarseLocation? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val provider = selectProvider(manager) ?: return null

        return withTimeoutOrNull(timeoutMs.coerceAtLeast(1L)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requestCurrentLocation(manager, provider)
            } else {
                requestSingleUpdate(manager, provider)
            }
        }
    }

    private fun selectProvider(manager: LocationManager): String? = when {
        runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
            LocationManager.GPS_PROVIDER
        runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
            LocationManager.NETWORK_PROVIDER
        else -> null
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(
        manager: LocationManager,
        provider: String
    ): CoarseLocation? = suspendCancellableCoroutine { cont ->
        val cancellation = android.os.CancellationSignal()
        cont.invokeOnCancellation { cancellation.cancel() }
        try {
            manager.getCurrentLocation(
                provider,
                cancellation,
                context.mainExecutor
            ) { location ->
                if (cont.isActive) cont.resume(location?.toCoarse(provider))
            }
        } catch (e: SecurityException) {
            if (cont.isActive) cont.resume(null)
        } catch (e: IllegalArgumentException) {
            if (cont.isActive) cont.resume(null)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleUpdate(
        manager: LocationManager,
        provider: String
    ): CoarseLocation? = suspendCancellableCoroutine { cont ->
        try {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    runCatching { manager.removeUpdates(this) }
                    if (cont.isActive) cont.resume(location.toCoarse(provider))
                }

                @Deprecated("legacy")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            cont.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
        } catch (e: SecurityException) {
            if (cont.isActive) cont.resume(null)
        } catch (e: IllegalArgumentException) {
            if (cont.isActive) cont.resume(null)
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
