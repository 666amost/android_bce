package com.bce.courier.data.model

object DashboardShipmentGrouping {
    const val ASSIGNED_WINDOW_MS = 24L * 60L * 60L * 1000L

    fun isAssigned(
        status: String,
        assignedAtMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!DeliveryStatus.isAssigned(status)) return false
        if (assignedAtMs == null) return true
        return nowMs - assignedAtMs < ASSIGNED_WINDOW_MS
    }

    fun isPending(
        status: String,
        assignedAtMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (DeliveryStatus.isPending(status)) return true
        if (!DeliveryStatus.isAssigned(status) || assignedAtMs == null) return false
        return nowMs - assignedAtMs >= ASSIGNED_WINDOW_MS
    }
}
