package com.bce.courier.tracking

import android.content.Context
import android.location.Location

object CourierTrackingLocationStore {
    private const val PREF_NAME = "courier_tracking_location"
    private const val KEY_LAT = "lat"
    private const val KEY_LNG = "lng"
    private const val KEY_BEARING = "bearing"
    private const val KEY_SPEED = "speed"
    private const val KEY_ACCURACY = "accuracy"
    private const val KEY_TIMESTAMP = "timestamp"

    fun save(context: Context, location: Location) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAT, location.latitude.toString())
            .putString(KEY_LNG, location.longitude.toString())
            .putFloat(KEY_BEARING, location.bearing)
            .putFloat(KEY_SPEED, location.speed)
            .putFloat(KEY_ACCURACY, location.accuracy)
            .putLong(KEY_TIMESTAMP, location.time.takeIf { it > 0L } ?: System.currentTimeMillis())
            .apply()
    }

    fun getFreshLocation(context: Context, maxAgeMs: Long): Location? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lat = prefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return null
        val lng = prefs.getString(KEY_LNG, null)?.toDoubleOrNull() ?: return null
        val timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
        if (timestamp <= 0L || System.currentTimeMillis() - timestamp > maxAgeMs) {
            return null
        }

        return Location("courier_tracking_cache").apply {
            latitude = lat
            longitude = lng
            bearing = prefs.getFloat(KEY_BEARING, 0f)
            speed = prefs.getFloat(KEY_SPEED, 0f)
            accuracy = prefs.getFloat(KEY_ACCURACY, 0f)
            time = timestamp
        }
    }
}

