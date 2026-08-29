package com.bce.courier.tracking

import android.content.Context
import com.bce.courier.BuildConfig
import com.gojek.mqtt.model.KeepAlive
import com.gojek.mqtt.model.MqttConnectOptions
import com.gojek.mqtt.model.ServerUri
import java.util.Locale
import java.util.UUID

internal object CourierTrackingConfig {
    const val notificationChannelId = "courier_tracking"
    const val notificationChannelName = "Courier Tracking"
    const val notificationId = 3201

    const val updateIntervalMs = 3_000L
    const val minUpdateIntervalMs = 2_000L
    const val maxUpdateDelayMs = 6_000L
    const val stationaryUpdateIntervalMs = 15_000L
    const val stationaryMaxUpdateDelayMs = 20_000L
    const val stationaryAfterMs = 60_000L
    const val minUpdateDistanceMeters = 8f

    const val minPublishIntervalMs = 2_500L
    const val forceMovingPublishMs = 5_000L
    const val idleHeartbeatMs = 30_000L
    const val minMovementMeters = 10f
    const val minMovingSpeedMps = 2f
    const val maxAcceptedAccuracyMeters = 120f
    const val warmLocationMaxAgeMs = 30_000L
    const val sharedLocationMaxAgeMs = 45_000L
    const val connectionMonitorIntervalMs = 15_000L
    const val connectionMonitorWarmLocationIntervalMs = 20_000L
    const val keepAliveSeconds = 90
    const val nearbyCourierTtlMs = 90_000L
    const val nearbyCourierEmptyWaitMs = 30_000L
    const val nearbyTopic = "v1/courier/location/+"

    val isEnabled: Boolean
        get() = BuildConfig.MQTT_ENABLED &&
            BuildConfig.MQTT_HOST.isNotBlank() &&
            BuildConfig.MQTT_USERNAME.isNotBlank() &&
            BuildConfig.MQTT_PASSWORD.isNotBlank()

    fun createConnectOptions(context: Context, courierId: String): MqttConnectOptions {
        val deviceId = context
            .getSharedPreferences("courier_tracking", Context.MODE_PRIVATE)
            .let { prefs ->
                prefs.getString("device_instance_id", null)
                    ?: UUID.randomUUID().toString().also {
                        prefs.edit().putString("device_instance_id", it).apply()
                    }
            }

        return MqttConnectOptions.Builder()
            .serverUris(listOf(ServerUri(BuildConfig.MQTT_HOST, BuildConfig.MQTT_PORT)))
            .clientId(buildClientId(courierId, deviceId))
            .userName(BuildConfig.MQTT_USERNAME)
            .password(BuildConfig.MQTT_PASSWORD)
            .cleanSession(true)
            .keepAlive(KeepAlive(keepAliveSeconds))
            .build()
    }

    private fun buildClientId(courierId: String, deviceId: String): String {
        val courierPart = sanitize(courierId, 36)
        val devicePart = sanitize(deviceId, 16)
        return "bce-$courierPart-$devicePart".take(64)
    }

    private fun sanitize(value: String, maxLength: Int): String {
        val sanitized = value
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_-]"), "")
            .ifBlank { "kurir" }
        return sanitized.take(maxLength)
    }
}

