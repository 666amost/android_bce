package com.bce.courier.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyCourierFormatterTest {
    @Test
    fun calculatesAndFormatsNearbyDistance() {
        val distance = NearbyCourierFormatter.distanceMeters(
            fromLat = 0.0,
            fromLng = 109.0,
            toLat = 0.0,
            toLng = 109.01,
        )

        assertTrue(distance in 1_100f..1_120f)
        assertEquals("1,1 km", NearbyCourierFormatter.formatDistance(distance))
        assertEquals("650 m", NearbyCourierFormatter.formatDistance(650f))
    }

    @Test
    fun formatsMotionAndFreshness() {
        assertEquals("Diam", NearbyCourierFormatter.motionLabel(0f))
        assertEquals("Bergerak", NearbyCourierFormatter.motionLabel(2f))
        assertEquals("8 detik lalu", NearbyCourierFormatter.formatFreshness(8_500L))
    }

    @Test
    fun removesPeerLocationsAfterNinetySeconds() {
        NearbyCourierStore.startSession(now = 0L)
        NearbyCourierStore.onLocation(
            location = CourierLocationMessage(
                lat = 0.0,
                lng = 109.0,
                bearing = 0f,
                speed = 0f,
                accuracy = 5f,
                timestamp = 0L,
                courierId = "courier-a",
                courierName = "Kurir A",
            ),
            topicCourierId = "courier-a",
            receivedAt = 0L,
        )

        NearbyCourierStore.pruneStale(CourierTrackingConfig.nearbyCourierTtlMs + 1L)

        assertTrue(NearbyCourierStore.state.value.locations.isEmpty())
        NearbyCourierStore.stopSession()
    }

    @Test
    fun keepsKnownCourierNameWhenLegacyPayloadHasNoName() {
        NearbyCourierStore.startSession(now = 0L)
        val base = CourierLocationMessage(
            lat = 0.0,
            lng = 109.0,
            bearing = 0f,
            speed = 0f,
            accuracy = 5f,
            timestamp = 0L,
            courierId = "courier-a",
            courierName = "Amos",
        )
        NearbyCourierStore.onLocation(base, "courier-a", receivedAt = 0L)
        NearbyCourierStore.onLocation(
            base.copy(courierName = "", timestamp = 1_000L),
            "courier-a",
            receivedAt = 1_000L,
        )

        assertEquals(
            "Amos",
            NearbyCourierStore.state.value.locations.getValue("courier-a").courierName,
        )
        NearbyCourierStore.stopSession()
    }
}
