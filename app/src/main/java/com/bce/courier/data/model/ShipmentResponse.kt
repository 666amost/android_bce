package com.bce.courier.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ShipmentResponse(
    val items: List<ShipmentItem>
)

@Serializable
data class ShipmentItem(
    @SerialName("awb_number")
    val awb: String = "",
    @SerialName("receiver_name")
    val recipient_name: String = "",
    @SerialName("receiver_phone")
    val recipient_phone: String = "",
    @SerialName("receiver_address")
    val address: String = "",
    val current_status: String = "",
    @SerialName("ongkos_kirim")
    val cod_amount: Double = 0.0,
    val lat: Double? = null,
    val lng: Double? = null,
    val created_at: String = "",
    val updated_at: String? = null,
    val courier_id: String? = null
)
