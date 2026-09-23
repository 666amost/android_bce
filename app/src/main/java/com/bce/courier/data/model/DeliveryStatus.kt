package com.bce.courier.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object DeliveryStatus {
    const val OUT_FOR_DELIVERY = "out_for_delivery"
    const val PENDING = "pending"
    const val DELIVERED = "delivered"
    const val LEGACY_PENDING = "shipped"

    fun normalized(status: String): String = when (status.trim().lowercase()) {
        LEGACY_PENDING -> PENDING
        else -> status.trim().lowercase()
    }

    fun isAssigned(status: String): Boolean = normalized(status) == OUT_FOR_DELIVERY
    fun isPending(status: String): Boolean = normalized(status) == PENDING
    fun isDelivered(status: String): Boolean = normalized(status) == DELIVERED
}

enum class DeliveryUpdateOutcome {
    UPDATED,
    QUEUED,
}

data class PendingReason(
    val code: String,
    val label: String,
)

object PendingReasons {
    const val RECIPIENT_UNAVAILABLE = "recipient_unavailable"
    const val ADDRESS_NOT_FOUND = "address_not_found"
    const val RECIPIENT_REJECTED = "recipient_rejected"
    const val RESCHEDULED = "rescheduled"
    const val PARCEL_ISSUE = "parcel_issue"
    const val OTHER = "other"

    val all = listOf(
        PendingReason(RECIPIENT_UNAVAILABLE, "Penerima tidak ada"),
        PendingReason(ADDRESS_NOT_FOUND, "Alamat tidak ditemukan"),
        PendingReason(RECIPIENT_REJECTED, "Ditolak penerima"),
        PendingReason(RESCHEDULED, "Reschedule"),
        PendingReason(PARCEL_ISSUE, "Paket bermasalah"),
        PendingReason(OTHER, "Lainnya"),
    )

    private val validCodes = all.mapTo(hashSetOf()) { it.code }

    fun isValid(code: String?): Boolean = code in validCodes
    fun labelFor(code: String?): String? = all.firstOrNull { it.code == code }?.label
}

@Serializable
data class DeliveryStatusUpdateRequest(
    @SerialName("p_awb") val awb: String,
    @SerialName("p_courier_id") val courierId: String,
    @SerialName("p_courier_name") val courierName: String,
    @SerialName("p_status") val status: String,
    @SerialName("p_reason_code") val reasonCode: String? = null,
    @SerialName("p_reason_note") val reasonNote: String? = null,
    @SerialName("p_notes") val notes: String? = null,
    @SerialName("p_location") val location: String? = null,
    @SerialName("p_latitude") val latitude: Double? = null,
    @SerialName("p_longitude") val longitude: Double? = null,
    @SerialName("p_photo_url") val photoUrl: String? = null,
)
