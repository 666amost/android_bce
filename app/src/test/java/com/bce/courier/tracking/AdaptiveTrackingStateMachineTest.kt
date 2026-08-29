package com.bce.courier.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveTrackingStateMachineTest {
    @Test
    fun switchesToStationaryAfterTimeoutAndBackOnSpeed() {
        val tracker = AdaptiveTrackingStateMachine(stationaryAfterMs = 60_000L)

        assertEquals(
            LocationSamplingMode.MOVING,
            tracker.onSample(-2.0, 109.0, 0f, now = 0L),
        )
        assertEquals(
            LocationSamplingMode.MOVING,
            tracker.onSample(-2.0, 109.0, 0f, now = 59_999L),
        )
        assertEquals(
            LocationSamplingMode.STATIONARY,
            tracker.onSample(-2.0, 109.0, 0f, now = 60_000L),
        )
        assertEquals(
            LocationSamplingMode.MOVING,
            tracker.onSample(-2.0, 109.0, 2f, now = 61_000L),
        )
    }

    @Test
    fun returnsToMovingAfterTenMeterDisplacement() {
        val tracker = AdaptiveTrackingStateMachine(stationaryAfterMs = 1_000L)
        tracker.onSample(0.0, 109.0, 0f, now = 0L)
        assertEquals(
            LocationSamplingMode.STATIONARY,
            tracker.onSample(0.0, 109.0, 0f, now = 1_000L),
        )

        assertEquals(
            LocationSamplingMode.MOVING,
            tracker.onSample(0.0001, 109.0, 0f, now = 2_000L),
        )
    }
}
