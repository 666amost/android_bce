package com.bce.courier.tracking

import android.content.Context
import android.util.Log
import com.bce.courier.BuildConfig
import com.gojek.courier.callback.SendMessageCallback
import com.gojek.mqtt.client.model.ConnectionState

internal class CourierTrackingClient(context: Context) {
    @Volatile
    private var nearbySubscriptionRequested = false

    private val connections = CourierTrackingConfig.enabledBrokers.map { broker ->
        CourierMqttConnection(context, broker, ::updateNearbySubscriptionState)
    }

    init {
        if (BuildConfig.MQTT_AVISHA_ENABLED && BuildConfig.MQTT_AVISHA_PASSWORD.isBlank()) {
            Log.w("CourierMQTT", "avisha: disabled because MQTT_AVISHA_PASSWORD is empty")
        }
    }

    fun reconnectIfNeeded(courierId: String) {
        connections.forEach { it.reconnectIfNeeded(courierId) }
    }

    fun publish(
        courierId: String,
        location: CourierLocationMessage,
        callback: SendMessageCallback,
    ): Boolean {
        reconnectIfNeeded(courierId)
        val connected = connections.filter { it.currentState() == ConnectionState.CONNECTED }
        if (connected.isEmpty()) return false

        val result = CourierPublishCallback(connected.size, callback)
        var published = false
        connected.forEach { connection ->
            val brokerCallback = result.forBroker()
            if (connection.publish(courierId, location, brokerCallback)) {
                published = true
            } else {
                // The connection can go offline between the state check and send.
                brokerCallback.onMessageSendFailure(IllegalStateException("MQTT connection unavailable"))
            }
        }
        return published
    }

    fun startNearbySubscription(courierId: String) {
        nearbySubscriptionRequested = true
        connections.forEach { it.startNearbySubscription(courierId) }
    }

    fun stopNearbySubscription() {
        nearbySubscriptionRequested = false
        connections.forEach { it.stopNearbySubscription() }
    }

    private fun updateNearbySubscriptionState() {
        if (!nearbySubscriptionRequested) return
        if (connections.any { it.nearbySubscribed }) {
            NearbyCourierStore.markSubscribed()
        } else {
            NearbyCourierStore.markSubscriptionError(
                connections.firstNotNullOfOrNull { it.nearbyError }
                    ?: "Menghubungkan ulang lokasi kurir"
            )
        }
    }

    fun disconnect(clearState: Boolean = false) {
        nearbySubscriptionRequested = false
        connections.forEach { it.disconnect(clearState) }
    }

    fun currentState(): ConnectionState = combinedConnectionState(connections.map { it.currentState() })

    internal companion object {
        fun combinedConnectionState(states: List<ConnectionState>): ConnectionState = when {
            ConnectionState.CONNECTED in states -> ConnectionState.CONNECTED
            ConnectionState.CONNECTING in states -> ConnectionState.CONNECTING
            else -> ConnectionState.DISCONNECTED
        }
    }
}
