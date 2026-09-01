package com.kairos.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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

    @SuppressLint("MissingPermission") // guarded by hasPermission()
    suspend fun current(context: Context): Place? {
        if (!hasPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        val loc = suspendCancellableCoroutine<Location?> { cont ->
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
            cont.invokeOnCancellation { cts.cancel() }
        } ?: return null
        return Place(loc.latitude, loc.longitude, reverseGeocode(context, loc.latitude, loc.longitude))
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
