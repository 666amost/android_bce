package com.bce.courier.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UpdateShipmentResponse(
    val success: Boolean,
    val awb: String,
    val new_status: String,
    val photo_url: String? = null
)
