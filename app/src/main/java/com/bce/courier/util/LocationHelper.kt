package com.bce.courier.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import android.util.Log
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.coroutines.resume

class LocationHelper(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val geocoder = android.location.Geocoder(context, Locale("id", "ID"))
    private val settingsClient: SettingsClient = LocationServices.getSettingsClient(context)

    @SuppressLint("MissingPermission")
    fun getLastLocation(onSuccess: (android.location.Location?) -> Unit) {
        val locationTask: Task<android.location.Location> = fusedLocationClient.lastLocation
        locationTask.addOnSuccessListener { location ->
            onSuccess(location)
        }.addOnFailureListener {
            onSuccess(null)
        }
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(onSuccess: (android.location.Location?) -> Unit) {
        val cancellationTokenSource = com.google.android.gms.tasks.CancellationTokenSource()

        val priority = if (isLocationEnabled()) {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        fusedLocationClient.getCurrentLocation(
            priority,
            cancellationTokenSource.token
        ).addOnSuccessListener { location ->
            onSuccess(location)
        }.addOnFailureListener { _ ->
            getLastLocation(onSuccess)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocationSuspend(): android.location.Location? {
        return suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = com.google.android.gms.tasks.CancellationTokenSource()
            continuation.invokeOnCancellation { cancellationTokenSource.cancel() }

            val priority = if (isLocationEnabled()) {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }

            fusedLocationClient.getCurrentLocation(priority, cancellationTokenSource.token)
                .addOnSuccessListener { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
                .addOnFailureListener {
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { lastLocation ->
                            if (continuation.isActive) continuation.resume(lastLocation)
                        }
                        .addOnFailureListener {
                            if (continuation.isActive) continuation.resume(null)
                        }
                }
        }
    }

    /**
     * Check if GPS is enabled and prompt user if not
     */
    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Request to enable location - will open settings if location is disabled
     */
    fun requestEnableLocation(activity: Activity, onResult: (Boolean) -> Unit) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        settingsClient.checkLocationSettings(builder.build())
            .addOnSuccessListener {
                onResult(true)
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        exception.startResolutionForResult(activity, REQUEST_CHECK_SETTINGS)
                    } catch (_: Exception) {
                        openLocationSettings()
                        onResult(false)
                    }
                } else {
                    openLocationSettings()
                    onResult(false)
                }
            }
    }

    /**
     * Open location settings page
     */
    fun openLocationSettings() {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("LocationHelper", "Failed to open location settings", e)
        }
    }

    // Callback-based reverse geocoding
    fun getAddressFromLocation(latitude: Double, longitude: Double, onResult: (String?) -> Unit) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                    if (addresses.isNotEmpty()) {
                        val address = addresses[0]
                        val addressText = buildString {
                            address.thoroughfare?.let { append(it) }
                            address.subThoroughfare?.let { if (isNotEmpty()) append(" No. $it") else append("No. $it") }
                            address.subLocality?.let { if (isNotEmpty()) append(", $it") else append(it) }
                            address.locality?.let { if (isNotEmpty()) append(", $it") else append(it) }
                            address.adminArea?.let { if (isNotEmpty()) append(", $it") else append(it) }
                        }
                        onResult(addressText.ifEmpty { null })
                    } else {
                        onResult(null)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val addressText = buildString {
                        address.thoroughfare?.let { append(it) }
                        address.subThoroughfare?.let { if (isNotEmpty()) append(" No. $it") else append("No. $it") }
                        address.subLocality?.let { if (isNotEmpty()) append(", $it") else append(it) }
                        address.locality?.let { if (isNotEmpty()) append(", $it") else append(it) }
                        address.adminArea?.let { if (isNotEmpty()) append(", $it") else append(it) }
                    }
                    onResult(addressText.ifEmpty { null })
                } else {
                    onResult(null)
                }
            }
        } catch (_: Exception) {
            onResult(null)
        }
    }

    // Suspending function for reverse geocoding (better for coroutines)
    suspend fun getAddressFromLocationSuspend(latitude: Double, longitude: Double): String? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    try {
                        geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                            val address = addresses.firstOrNull()
                            val addressText = address?.let {
                                buildString {
                                    it.thoroughfare?.let { append(it) }
                                    it.subThoroughfare?.let { if (isNotEmpty()) append(" No. $it") else append("No. $it") }
                                    it.subLocality?.let { if (isNotEmpty()) append(", $it") else append(it) }
                                    it.locality?.let { if (isNotEmpty()) append(", $it") else append(it) }
                                    it.adminArea?.let { if (isNotEmpty()) append(", $it") else append(it) }
                                }.ifEmpty { null }
                            }
                            continuation.resume(addressText)
                        }
                    } catch (_: Exception) {
                        continuation.resume(null)
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    val address = addresses?.firstOrNull()
                    address?.let {
                        buildString {
                            it.thoroughfare?.let { append(it) }
                            it.subThoroughfare?.let { if (isNotEmpty()) append(" No. $it") else append("No. $it") }
                            it.subLocality?.let { if (isNotEmpty()) append(", $it") else append(it) }
                            it.locality?.let { if (isNotEmpty()) append(", $it") else append(it) }
                            it.adminArea?.let { if (isNotEmpty()) append(", $it") else append(it) }
                        }.ifEmpty { null }
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun geocodeAddressToLatLngSuspend(address: String): Pair<Double, Double>? {
        val normalized = address.trim()
        if (normalized.isBlank()) return null

        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocationName(normalized, 1) { addresses ->
                        val first = addresses.firstOrNull()
                        val result = first?.let { it.latitude to it.longitude }
                        continuation.resume(result)
                    }
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(normalized, 1)
                    val first = addresses?.firstOrNull()
                    first?.let { it.latitude to it.longitude }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val REQUEST_CHECK_SETTINGS = 9001
    }
}
