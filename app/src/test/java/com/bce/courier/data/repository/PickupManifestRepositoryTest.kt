package com.bce.courier.data.repository

import com.bce.courier.data.model.ManifestPayload
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class PickupManifestRepositoryTest {
    private val payload = ManifestPayload(
        "BCE172694JKT", "2026-11-04", "udara", "bangka", "Koba", "transfer", "AGENT",
        "Pengirim", "0811", "Penerima", "0822", "Alamat", 1, 1.0, 35000,
        35000, 0, 0, 0, 35000, "Pakaian", "courier",
    )

    private fun collision(): Response<List<JsonObject>> = Response.error(
        409, """{"code":"23505","details":"Key (awb_no) already exists"}""".toResponseBody(),
    )

    @Test
    fun repeatedCollisionsPrefixZerosAndPersistEachCandidateBeforeSending() = runBlocking {
        val attempted = mutableListOf<String>()
        val persisted = mutableListOf<String>()
        val repository = PickupManifestRepository(
            insert = { json ->
                val awb = json.getValue("awb_no").jsonPrimitive.content
                assertEquals(awb, persisted.last())
                attempted += awb
                if (attempted.size < 3) collision() else Response.success(listOf(json))
            },
            findByAwb = { awb -> Response.success(listOf(payload.copy(awbNo = awb, namaPenerima = "Paket lain").toJsonObject())) },
        )
        val saved = repository.save(payload) { persisted += it.awbNo }
        assertEquals(listOf("BCE172694JKT", "BCE0172694JKT", "BCE00172694JKT"), attempted)
        assertEquals(attempted, persisted)
        assertEquals("BCE00172694JKT", saved.awbNo)
        assertEquals("transfer", saved.metodePembayaran)
        assertEquals(payload.awbDate, saved.awbDate)
    }

    @Test
    fun freshParcelWithIdenticalDetailsStillGetsItsOwnAwb() = runBlocking {
        var requests = 0
        val repository = PickupManifestRepository(
            insert = { if (++requests == 1) collision() else Response.success(listOf(it)) },
            findByAwb = { Response.success(listOf(payload.toJsonObject())) },
        )
        assertEquals("BCE0172694JKT", repository.save(payload) {}.awbNo)
        assertEquals(2, requests)
    }

    @Test
    fun retryAfterAnInterruptedSaveReusesTheConfirmedExistingParcel() = runBlocking {
        var requests = 0
        val repository = PickupManifestRepository(
            insert = { requests++; collision() },
            findByAwb = { Response.success(listOf(payload.toJsonObject())) },
        )
        val saved = repository.save(payload.copy(isRetry = true)) {}
        assertEquals(payload.awbNo, saved.awbNo)
        assertEquals(1, requests)
    }

    @Test
    fun unrelatedConflictDoesNotChangeTheAwb() = runBlocking {
        val attempted = mutableListOf<String>()
        val repository = PickupManifestRepository(
            insert = { Response.error(409, """{"code":"23505","details":"Key (customer_reference) already exists"}""".toResponseBody()) },
            findByAwb = { Response.success(emptyList()) },
        )
        try {
            repository.save(payload) { attempted += it.awbNo }
            fail("Must report the unrelated conflict")
        } catch (_: IOException) { }
        assertEquals(listOf(payload.awbNo), attempted)
    }

    @Test
    fun serverErrorKeepsThePreparedAwbForRetry() = runBlocking {
        val prepared = mutableListOf<ManifestPayload>()
        val repository = PickupManifestRepository(
            insert = { Response.error(500, "Server error".toResponseBody()) },
            findByAwb = { error("Must not look up a server error as a collision") },
        )
        try {
            repository.save(payload) { prepared += it }
            fail("Must preserve the error for retry")
        } catch (_: IOException) { }
        assertEquals(listOf(payload), prepared)
    }

    @Test
    fun mismatchedServerDataCannotBeReportedAsSuccessfullySaved() = runBlocking {
        val repository = PickupManifestRepository(
            insert = { Response.success(listOf(payload.copy(metodePembayaran = "cash").toJsonObject())) },
            findByAwb = { error("Must inspect the returned data") },
        )
        try {
            repository.save(payload) {}
            fail("Must not accept a payment method changed by the server")
        } catch (_: IllegalStateException) { }
    }
}
