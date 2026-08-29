package com.bce.courier.tracking

enum class CourierTrackingStatus {
    OFF,
    STARTING,
    ACTIVE,
    WAITING;

    companion object {
        fun fromValue(value: String?): CourierTrackingStatus {
            return entries.firstOrNull { it.name == value } ?: OFF
        }
    }
}

