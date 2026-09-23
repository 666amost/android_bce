package com.bce.courier.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryStatusTest {

    @Test
    fun legacyShippedIsReadAsPending() {
        assertEquals(DeliveryStatus.PENDING, DeliveryStatus.normalized("shipped"))
        assertTrue(DeliveryStatus.isPending("shipped"))
        assertFalse(DeliveryStatus.isAssigned("shipped"))
    }

    @Test
    fun literalStatusRecognizesOutForDelivery() {
        assertTrue(DeliveryStatus.isAssigned("out_for_delivery"))
        assertFalse(DeliveryStatus.isPending("out_for_delivery"))
    }

    @Test
    fun pendingReasonsExposeStableDatabaseCodes() {
        assertEquals(6, PendingReasons.all.size)
        assertTrue(PendingReasons.isValid(PendingReasons.RECIPIENT_UNAVAILABLE))
        assertTrue(PendingReasons.isValid(PendingReasons.OTHER))
        assertFalse(PendingReasons.isValid("unknown"))
    }

    @Test
    fun rpcRequestKeepsPendingReasonFields() {
        val encoded = Json.encodeToString(
            DeliveryStatusUpdateRequest(
                awb = "BCE123",
                courierId = "courier-a",
                courierName = "Kurir",
                status = DeliveryStatus.PENDING,
                reasonCode = PendingReasons.RECIPIENT_UNAVAILABLE,
                reasonNote = "Rumah kosong",
            )
        )

        assertTrue(encoded.contains("\"p_status\":\"pending\""))
        assertTrue(encoded.contains("\"p_reason_code\":\"recipient_unavailable\""))
        assertTrue(encoded.contains("\"p_reason_note\":\"Rumah kosong\""))
    }
}
