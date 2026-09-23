package com.bce.courier.data.local

import androidx.room.Entity
import com.bce.courier.data.model.ShipmentItem

@Entity(
    tableName = "cached_shipments",
    primaryKeys = ["ownerCourierId", "awb"],
)
data class CachedShipmentEntity(
    val ownerCourierId: String,
    val awb: String,
    val recipientName: String,
    val recipientPhone: String,
    val address: String,
    val currentStatus: String,
    val codAmount: Double,
    val lat: Double?,
    val lng: Double?,
    val createdAt: String,
    val updatedAt: String?,
    val assignedCourierId: String?,
    val pendingReasonCode: String?,
    val pendingReasonNote: String?,
    val pendingAt: String?,
    val cachedAt: Long,
) {
    fun toShipmentItem() = ShipmentItem(
        awb = awb,
        recipient_name = recipientName,
        recipient_phone = recipientPhone,
        address = address,
        current_status = currentStatus,
        cod_amount = codAmount,
        lat = lat,
        lng = lng,
        created_at = createdAt,
        updated_at = updatedAt,
        courier_id = assignedCourierId,
        pending_reason_code = pendingReasonCode,
        pending_reason_note = pendingReasonNote,
        pending_at = pendingAt,
    )

    companion object {
        fun from(
            ownerCourierId: String,
            shipment: ShipmentItem,
            cachedAt: Long,
        ) = CachedShipmentEntity(
            ownerCourierId = ownerCourierId,
            awb = shipment.awb,
            recipientName = shipment.recipient_name,
            recipientPhone = shipment.recipient_phone,
            address = shipment.address,
            currentStatus = shipment.current_status,
            codAmount = shipment.cod_amount,
            lat = shipment.lat,
            lng = shipment.lng,
            createdAt = shipment.created_at,
            updatedAt = shipment.updated_at,
            assignedCourierId = shipment.courier_id,
            pendingReasonCode = shipment.pending_reason_code,
            pendingReasonNote = shipment.pending_reason_note,
            pendingAt = shipment.pending_at,
            cachedAt = cachedAt,
        )
    }
}
