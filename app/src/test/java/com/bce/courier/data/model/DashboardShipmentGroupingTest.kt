package com.bce.courier.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardShipmentGroupingTest {
    private val now = 2_000_000_000L

    @Test
    fun `out for delivery stays assigned before 24 hours`() {
        assertTrue(
            DashboardShipmentGrouping.isAssigned(
                status = DeliveryStatus.OUT_FOR_DELIVERY,
                assignedAtMs = now - DashboardShipmentGrouping.ASSIGNED_WINDOW_MS + 1,
                nowMs = now,
            )
        )
        assertFalse(
            DashboardShipmentGrouping.isPending(
                status = DeliveryStatus.OUT_FOR_DELIVERY,
                assignedAtMs = now - DashboardShipmentGrouping.ASSIGNED_WINDOW_MS + 1,
                nowMs = now,
            )
        )
    }

    @Test
    fun `out for delivery appears pending after 24 hours without changing status`() {
        assertFalse(
            DashboardShipmentGrouping.isAssigned(
                status = DeliveryStatus.OUT_FOR_DELIVERY,
                assignedAtMs = now - DashboardShipmentGrouping.ASSIGNED_WINDOW_MS,
                nowMs = now,
            )
        )
        assertTrue(
            DashboardShipmentGrouping.isPending(
                status = DeliveryStatus.OUT_FOR_DELIVERY,
                assignedAtMs = now - DashboardShipmentGrouping.ASSIGNED_WINDOW_MS,
                nowMs = now,
            )
        )
    }

    @Test
    fun `literal and legacy pending always appear pending`() {
        assertTrue(DashboardShipmentGrouping.isPending(DeliveryStatus.PENDING, now, now))
        assertTrue(DashboardShipmentGrouping.isPending(DeliveryStatus.LEGACY_PENDING, now, now))
        assertFalse(DashboardShipmentGrouping.isAssigned(DeliveryStatus.PENDING, now, now))
    }

    @Test
    fun `missing timestamp keeps out for delivery visible in assigned`() {
        assertTrue(DashboardShipmentGrouping.isAssigned(DeliveryStatus.OUT_FOR_DELIVERY, null, now))
        assertFalse(DashboardShipmentGrouping.isPending(DeliveryStatus.OUT_FOR_DELIVERY, null, now))
    }
}
