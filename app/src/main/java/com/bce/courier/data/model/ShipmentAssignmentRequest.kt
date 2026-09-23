package com.bce.courier.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Use a generated serializer: Retrofit's Java Map type loses nullable value metadata.
@Serializable
data class ShipmentAssignmentRequest(
    @SerialName("current_status") val currentStatus: String,
    @SerialName("courier_id") val courierId: String,
    @SerialName("updated_at") val updatedAt: String,
    // No defaults: nulls must be sent explicitly to clear the previous pending state.
    @SerialName("pending_reason_code") val pendingReasonCode: String?,
    @SerialName("pending_reason_note") val pendingReasonNote: String?,
    @SerialName("pending_at") val pendingAt: String?,
)
