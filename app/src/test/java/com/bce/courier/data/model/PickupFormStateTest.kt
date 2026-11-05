package com.bce.courier.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PickupFormStateTest {
    private val customer = CustomerData(
        "customer", "Pengirim", "0811", "Penerima", "0822", "Alamat",
        "bangka", "Koba", "udara", "cash", "UDR CASH", "Pakaian",
    )

    @Test
    fun manuallySelectedTransferSurvivesCustomerChangesAndDraftRestore() {
        val chosen = PickupFormState(metodePembayaran = "transfer", paymentManuallySelected = true,
            hargaPerKg = "55000", priceManuallyEdited = true)
        val restored = Json.decodeFromString<PickupFormState>(Json.encodeToString(chosen))
        val applied = restored.withCustomer(customer)
        assertEquals("transfer", applied.metodePembayaran)
        assertEquals("55000", applied.hargaPerKg)
        assertTrue(applied.priceManuallyEdited)
        assertEquals(customer.namaPenerima, applied.namaPenerima)
        assertEquals("transfer", applied.withCustomer(customer.copy(metodePembayaran = "COD")).metodePembayaran)
    }

    @Test
    fun customerTransferAliasIsNormalizedBeforeUserMakesAChoice() {
        assertEquals("transfer", PickupFormState().withCustomer(customer.copy(metodePembayaran = " TRF ")).metodePembayaran)
        assertEquals("cash", PickupFormState().withCustomer(customer.copy(metodePembayaran = "unknown")).metodePembayaran)
    }

    @Test
    fun newAwbAfterMidnightUpdatesAutomaticDateAndPreservesManualDate() {
        val tomorrow = LocalDate.of(2026, 11, 4)
        val previous = PickupFormState(awbDate = "2026-11-03")
        assertEquals("2026-11-04", previous.withNewAwb(tomorrow).awbDate)
        assertEquals("2026-11-03", previous.copy(dateManuallySelected = true).withNewAwb(tomorrow).awbDate)
        assertTrue(Regex("BCE[0-9]{6}JKT").matches(previous.withNewAwb(tomorrow).awbNo))
        val interruptedSave = previous.copy(submissionAttempted = true)
        assertEquals("2026-11-03", interruptedSave.withCurrentDate(tomorrow).awbDate)
        assertEquals("2026-11-04", interruptedSave.withNewAwb(tomorrow).awbDate)
        assertFalse(interruptedSave.withNewAwb(tomorrow).submissionAttempted)
    }

    @Test
    fun savedManifestMustMatchPaymentDateAndAwbBeforeDraftIsDiscarded() {
        val payload = ManifestPayload(
            "BCEABCJKT", "2026-11-03", "udara", "bangka", "Koba", "transfer", "AGENT",
            "Pengirim", "0811", "Penerima", "0822", "Alamat", 1, 1.5, 35000,
            52500, 0, 0, 0, 52500, "Pakaian", "courier",
        )
        val stored = payload.toJsonObject()
        assertTrue(payload.matchesStored(stored))
        listOf("metode_pembayaran" to "cod", "awb_date" to "2026-11-02", "awb_no" to "OTHER").forEach { (field, wrong) ->
            assertFalse(payload.matchesStored(kotlinx.serialization.json.JsonObject(stored + (field to JsonPrimitive(wrong)))))
        }
        assertTrue(payload.matchesStored(kotlinx.serialization.json.JsonObject(stored + ("metode_pembayaran" to JsonPrimitive("TRF")))))
    }
}
