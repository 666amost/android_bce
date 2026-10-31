package com.bce.courier.tracking

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gojek.courier.callback.SendMessageCallback
import com.gojek.mqtt.client.model.ConnectionState
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Opt-in network smoke test using the credentials compiled into the debug APK. */
@RunWith(AndroidJUnit4::class)
class CourierMqttBrokerTest {
    @Test
    fun bothBrokersRoundTripAndAvishaSurvivesHiveMqDisconnect() {
        val configured = CourierTrackingConfig.enabledBrokers
        assumeTrue("Enable and configure Avisha in gradle.properties", configured.any { it.id == "avisha" })
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = UUID.randomUUID().toString().take(8)
        val connections = configured.associate { original ->
            val root = original.locationTopicPrefix.removeSuffix("v1/courier/location/")
            val isolatedBroker = CourierMqttBroker(
                id = original.id,
                host = original.host,
                port = original.port,
                username = original.username,
                password = original.password,
                topicPrefix = "${root}_migration_test/$session",
            )
            original.id to CourierMqttConnection(context, isolatedBroker) {}
        }

        NearbyCourierStore.startSession()
        try {
            connections.forEach { (id, connection) -> connection.startNearbySubscription("test-$session-$id") }
            connections.forEach { (id, connection) ->
                await("$id connects and subscribes") {
                    connection.currentState() == ConnectionState.CONNECTED && connection.nearbySubscribed
                }
                roundTrip(connection, "test-$session-$id")
            }

            // Closing HiveMQ must leave Avisha's socket, alarm and subscription usable.
            connections["hivemq"]?.disconnect(clearState = true)
            val avisha = connections.getValue("avisha")
            assertEquals(ConnectionState.CONNECTED, avisha.currentState())
            roundTrip(avisha, "test-$session-avisha-after-cutover")
            Log.i("CourierMQTTTest", "Round-trips passed; checking Avisha beyond the broker keep-alive timeout")
            SystemClock.sleep(CourierTrackingConfig.keepAliveSeconds * 1_500L + 5_000L)
            assertEquals("Avisha survives HiveMQ shutdown and an idle keep-alive cycle", ConnectionState.CONNECTED, avisha.currentState())
            roundTrip(avisha, "test-$session-avisha-after-idle")
        } finally {
            connections.values.forEach { it.disconnect(clearState = true) }
            NearbyCourierStore.stopSession()
        }
    }

    private fun roundTrip(connection: CourierMqttConnection, courierId: String) {
        val sent = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val payload = CourierLocationMessage(
            lat = -2.0, lng = 109.0, bearing = 15f, speed = 0f, accuracy = 5f,
            timestamp = System.currentTimeMillis(), courierId = courierId, courierName = "MQTT migration test",
        )
        assertTrue("publish starts", connection.publish(courierId, payload, object : SendMessageCallback {
            override fun onMessageSendTrigger() = Unit
            override fun onMessageWrittenOnSocket() = Unit
            override fun onMessageSendSuccess() { sent.countDown() }
            override fun onMessageSendFailure(error: Throwable) { failure.set(error); sent.countDown() }
        }))
        assertTrue("publish callback arrives", sent.await(10, TimeUnit.SECONDS))
        assertNull("publish succeeds", failure.get())
        await("published location is received through the broker subscription") {
            NearbyCourierStore.state.value.locations[courierId]?.timestamp == payload.timestamp
        }
        assertEquals(payload.lat, NearbyCourierStore.state.value.locations.getValue(courierId).lat, 0.0)
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000L
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100L)
        assertTrue(description, condition())
    }
}
