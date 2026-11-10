package it.traghetti.maddalena.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import it.traghetti.maddalena.domain.Port
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Calcoli geografici puri (testabili senza Android). */
object Geo {
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * 6371.0 * asin(sqrt(a))
    }

    fun nearestPort(lat: Double, lon: Double): Port =
        Port.entries.minBy { distanceKm(lat, lon, it.latitude, it.longitude) }
}

fun interface PortLocator {
    /** Porto più vicino alla posizione attuale, o null se non disponibile (permesso negato, GPS spento, timeout). */
    suspend fun nearestPort(): Port?
}

/**
 * Usa direttamente LocationManager (nessuna dipendenza da Google Play Services):
 * prima l'ultima posizione nota e recente, altrimenti una lettura singola con timeout.
 */
class AndroidPortLocator(private val context: Context) : PortLocator {

    fun hasPermission(): Boolean = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
    ).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    override suspend fun nearestPort(): Port? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!LocationManagerCompat.isLocationEnabled(lm)) return null
        val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())

        val recent = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 10 * 60 * 1000 }
            .maxByOrNull { it.time }
        val location = recent ?: withTimeoutOrNull(10_000) { currentLocation(lm, providers) }
            ?: providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        return location?.let { Geo.nearestPort(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(lm: LocationManager, providers: List<String>): Location? {
        val provider = when {
            Build.VERSION.SDK_INT >= 31 && LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        return suspendCancellableCoroutine { cont ->
            val signal = androidx.core.os.CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(context)) {
                if (cont.isActive) cont.resume(it)
            }
        }
    }
}
