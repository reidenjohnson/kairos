package com.kairos.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Live device location via the fused location provider, with a reverse-geocoded
 * label. Returns null when permission is missing or no fix is available, so the
 * caller can fall back to a default place.
 */
object LocationProvider {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Best available location, resolved quickly and *never* blocking. Prefers the
     * fused provider's cached last-known fix (instant); only if there is none does
     * it wait briefly for a fresh fix. Every step is time-boxed so a phone that
     * can't get a GPS fix (indoors, weak signal, battery saver) falls back to the
     * caller's default instead of hanging the whole refresh.
     */
    @SuppressLint("MissingPermission") // guarded by hasPermission()
    suspend fun current(context: Context): Place? {
        if (!hasPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val loc = withTimeoutOrNull(2_000) { lastKnown(client) }
            ?: withTimeoutOrNull(6_000) { freshFix(client) }
            ?: return null
        val label = withTimeoutOrNull(4_000) { reverseGeocode(context, loc.latitude, loc.longitude) }
            ?: coords(loc.latitude, loc.longitude)
        return Place(loc.latitude, loc.longitude, label)
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastKnown(client: FusedLocationProviderClient): Location? =
        suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }

    @SuppressLint("MissingPermission")
    private suspend fun freshFix(client: FusedLocationProviderClient): Location? {
        val cts = CancellationTokenSource()
        return suspendCancellableCoroutine { cont ->
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
            cont.invokeOnCancellation { cts.cancel() }
        }
    }

    private suspend fun reverseGeocode(context: Context, lat: Double, lon: Double): String =
        withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                val hit = Geocoder(context, Locale.US).getFromLocation(lat, lon, 1)?.firstOrNull()
                val town = hit?.locality ?: hit?.subAdminArea
                val state = hit?.adminArea
                when {
                    town != null && state != null -> "$town, $state"
                    town != null -> town
                    else -> coords(lat, lon)
                }
            } catch (e: Exception) {
                coords(lat, lon)
            }
        }

    private fun coords(lat: Double, lon: Double) = "%.2f, %.2f".format(lat, lon)
}
