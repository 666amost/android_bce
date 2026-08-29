package com.bce.courier.tracking

enum class LocationSamplingMode {
    MOVING,
    STATIONARY
}

class AdaptiveTrackingStateMachine(
    private val stationaryAfterMs: Long = CourierTrackingConfig.stationaryAfterMs,
    private val movementDistanceMeters: Float = CourierTrackingConfig.minMovementMeters,
    private val movingSpeedMetersPerSecond: Float = CourierTrackingConfig.minMovingSpeedMps
) {
    private var referenceLat: Double? = null
    private var referenceLng: Double? = null
    private var stationarySince: Long? = null

    var mode: LocationSamplingMode = LocationSamplingMode.MOVING
        private set

    fun onSample(
        lat: Double,
        lng: Double,
        speedMetersPerSecond: Float,
        now: Long
    ): LocationSamplingMode {
        val previousLat = referenceLat
        val previousLng = referenceLng
        val distance = if (previousLat == null || previousLng == null) {
            0f
        } else {
            NearbyCourierFormatter.distanceMeters(previousLat, previousLng, lat, lng)
        }
        val moving = speedMetersPerSecond >= movingSpeedMetersPerSecond ||
            distance >= movementDistanceMeters

        if (moving) {
            referenceLat = lat
            referenceLng = lng
            stationarySince = null
            mode = LocationSamplingMode.MOVING
            return mode
        }

        if (referenceLat == null || referenceLng == null) {
            referenceLat = lat
            referenceLng = lng
        }
        val idleSince = stationarySince ?: now.also { stationarySince = it }
        if (now - idleSince >= stationaryAfterMs) {
            mode = LocationSamplingMode.STATIONARY
        }
        return mode
    }

    fun reset() {
        referenceLat = null
        referenceLng = null
        stationarySince = null
        mode = LocationSamplingMode.MOVING
    }
}
