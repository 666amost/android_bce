package com.bce.courier.data.local

import com.bce.courier.data.model.ShipmentItem
import org.junit.Assert.assertEquals
import org.junit.Test

class CachedShipmentEntityTest {
    @Test
    fun preservesShipmentFieldsAcrossRoomMapping() {
        val source = ShipmentItem(
            awb = "BCE123",
            recipient_name = "Penerima",
            recipient_phone = "0812",
            address = "Pontianak",
            current_status = "out_for_delivery",
            cod_amount = 12_500.0,
            lat = -0.02,
            lng = 109.34,
            created_at = "2026-07-26T01:00:00Z",
            updated_at = "2026-07-26T02:00:00Z",
            courier_id = "courier-a",
            pending_reason_code = "recipient_unavailable",
            pending_reason_note = "Rumah kosong",
            pending_at = "2026-07-26T02:00:00Z",
        )

        val cached = CachedShipmentEntity.from("courier-a", source, cachedAt = 123L)

        assertEquals(source, cached.toShipmentItem())
        assertEquals(123L, cached.cachedAt)
    }
}
