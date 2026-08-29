package com.bce.courier.tracking

import kotlinx.serialization.Serializable

@Serializable
data class CourierLocationMessage(
    val lat: Double,
    val lng: Double,
    val bearing: Float,
    val speed: Float,
    val accuracy: Float,
    val timestamp: Long,
    val courierId: String = "",
    val courierName: String = ""
)

