package com.bce.courier.tracking

import com.gojek.mqtt.client.model.ConnectionState
import org.junit.Assert.*
import org.junit.Test

class CourierMqttMigrationTest {
    private fun broker(prefix: String = "") = CourierMqttBroker(
        "test", "broker.avisha.id", 8883, "kurir_bce", "test-password", prefix
    )

    @Test
    fun preservesHiveMqTopicsAndUsesAvishaAccountNamespace() {
        assertEquals("v1/courier/location/courier-a", broker().locationTopic("courier-a"))
        val avisha = broker("/kurir_bce/")
        assertEquals("kurir_bce/v1/courier/location/courier-a", avisha.locationTopic("courier-a"))
        assertEquals("kurir_bce/v1/courier/location/+", avisha.nearbyTopic)
        assertEquals("courier-a", avisha.courierIdFromTopic(avisha.locationTopic("courier-a")))
        assertNull(avisha.courierIdFromTopic("v1/courier/location/courier-a"))
        assertNull(avisha.courierIdFromTopic("other/v1/courier/location/courier-a"))
        assertNull(avisha.courierIdFromTopic("kurir_bce/v1/courier/location/courier-a/extra"))
    }

    @Test
    fun rejectsMissingCredentialsAndWildcardPublishNamespaces() {
        assertFalse(CourierMqttBroker("test", "host", 8883, "user", "").isConfigured)
        assertFalse(broker("kurir_bce/#").isConfigured)
        assertFalse(broker("kurir_bce/+").isConfigured)
        assertTrue(broker("kurir_bce").isConfigured)
    }

    @Test
    fun staysActiveWhenHiveMqShutsDownOrIsRemoved() {
        assertEquals(ConnectionState.CONNECTED, CourierTrackingClient.combinedConnectionState(
            listOf(ConnectionState.DISCONNECTED, ConnectionState.CONNECTED)
        ))
        assertEquals(ConnectionState.CONNECTED, CourierTrackingClient.combinedConnectionState(
            listOf(ConnectionState.CONNECTED)
        ))
        assertEquals(ConnectionState.CONNECTING, CourierTrackingClient.combinedConnectionState(
            listOf(ConnectionState.DISCONNECTED, ConnectionState.CONNECTING)
        ))
        assertEquals(ConnectionState.DISCONNECTED, CourierTrackingClient.combinedConnectionState(emptyList()))
    }
}
