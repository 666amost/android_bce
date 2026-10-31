package com.bce.courier.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class NearbyCourierLocation(
    val courierId: String,
    val courierName: String,
    val lat: Double,
    val lng: Double,
    val bearing: Float,
    val speed: Float,
    val accuracy: Float,
    val timestamp: Long,
    val receivedAt: Long
)

data class NearbyCourierState(
    val sessionStartedAt: Long? = null,
    val isSubscribed: Boolean = false,
    val errorMessage: String? = null,
    val locations: Map<String, NearbyCourierLocation> = emptyMap()
)

object NearbyCourierStore {
    private const val MAX_ENTRIES = 100

    private val mutableState = MutableStateFlow(NearbyCourierState())
    val state: StateFlow<NearbyCourierState> = mutableState.asStateFlow()

    @Synchronized
    fun startSession(now: Long = System.currentTimeMillis()) {
        mutableState.value = NearbyCourierState(sessionStartedAt = now)
    }

    @Synchronized
    fun markSubscribed() {
        mutableState.value = mutableState.value.copy(
            isSubscribed = true,
            errorMessage = null
        )
    }

    @Synchronized
    fun markSubscriptionError(message: String) {
        mutableState.value = mutableState.value.copy(
            isSubscribed = false,
            errorMessage = message
        )
    }

    @Synchronized
    fun onLocation(
        location: CourierLocationMessage,
        topicCourierId: String?,
        receivedAt: Long = System.currentTimeMillis()
    ) {
        val resolvedCourierId = location.courierId.ifBlank { topicCourierId.orEmpty() }
        if (resolvedCourierId.isBlank()) return
        if (!location.lat.isFinite() || !location.lng.isFinite()) return
        if (location.lat !in -90.0..90.0 || location.lng !in -180.0..180.0) return
        if (location.accuracy > CourierTrackingConfig.maxAcceptedAccuracyMeters) return
        // Both brokers carry the same samples; delayed copies must not move a courier back.
        val previous = mutableState.value.locations[resolvedCourierId]
        if (previous != null && location.timestamp <= previous.timestamp) return

        val fallbackName = "Kurir ${resolvedCourierId.takeLast(4).uppercase()}"
        val previousName = mutableState.value.locations[resolvedCourierId]
            ?.courierName
            ?.takeUnless { it == fallbackName }
        val displayName = location.courierName.trim()
            .ifBlank { previousName.orEmpty() }
            .ifBlank { fallbackName }
        val entry = NearbyCourierLocation(
            courierId = resolvedCourierId,
            courierName = displayName,
            lat = location.lat,
            lng = location.lng,
            bearing = location.bearing,
            speed = location.speed,
            accuracy = location.accuracy,
            timestamp = location.timestamp,
            receivedAt = receivedAt
        )

        val next = mutableState.value.locations
            .filterValues {
                receivedAt - it.receivedAt <= CourierTrackingConfig.nearbyCourierTtlMs
            }
            .toMutableMap()
            .apply { put(resolvedCourierId, entry) }
            .values
            .sortedByDescending { it.receivedAt }
            .take(MAX_ENTRIES)
            .associateBy { it.courierId }

        mutableState.value = mutableState.value.copy(
            errorMessage = null,
            locations = next
        )
    }

    @Synchronized
    fun pruneStale(now: Long = System.currentTimeMillis()) {
        val current = mutableState.value
        val fresh = current.locations.filterValues {
            now - it.receivedAt <= CourierTrackingConfig.nearbyCourierTtlMs
        }
        if (fresh.size != current.locations.size) {
            mutableState.value = current.copy(locations = fresh)
        }
    }

    @Synchronized
    fun stopSession() {
        mutableState.value = NearbyCourierState()
    }
}

object NearbyCourierFormatter {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun distanceMeters(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double
    ): Float {
        val lat1 = Math.toRadians(fromLat)
        val lat2 = Math.toRadians(toLat)
        val deltaLat = Math.toRadians(toLat - fromLat)
        val deltaLng = Math.toRadians(toLng - fromLng)
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) * sin(deltaLng / 2) * sin(deltaLng / 2)
        return (2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))).toFloat()
    }

    fun formatDistance(distanceMeters: Float): String =
        if (distanceMeters < 1_000f) {
            "${distanceMeters.roundToInt()} m"
        } else {
            String.format(java.util.Locale("id", "ID"), "%.1f km", distanceMeters / 1_000f)
        }

    fun formatFreshness(ageMs: Long): String {
        val seconds = (ageMs.coerceAtLeast(0L) / 1_000L)
        return if (seconds < 60L) {
            "$seconds detik lalu"
        } else {
            "${seconds / 60L} menit lalu"
        }
    }

    fun motionLabel(speedMetersPerSecond: Float): String =
        if (speedMetersPerSecond >= CourierTrackingConfig.minMovingSpeedMps) {
            "Bergerak"
        } else {
            "Diam"
        }
}
