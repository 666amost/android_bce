package com.bce.courier.data.api

import com.bce.courier.data.model.ShipmentAssignmentRequest
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit

class ShipmentAssignmentSerializationTest {
    @Test
    fun assignmentPatchSendsExplicitNullsToClearPreviousPendingState() = runBlocking {
        var requestBody: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                assertEquals("PATCH", request.method)
                assertEquals("eq.BCE905747", request.url.queryParameter("awb_number"))
                requestBody = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(204)
                    .message("No Content")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        val api = Retrofit.Builder()
            .baseUrl("https://example.invalid/rest/v1/")
            .client(client)
            .addConverterFactory(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CourierApiService::class.java)

        val response = api.updateShipment("eq.BCE905747", ShipmentAssignmentRequest(
            currentStatus = "out_for_delivery",
            courierId = "courier-test",
            updatedAt = "2026-09-23T08:00:00.000Z",
            pendingReasonCode = null,
            pendingReasonNote = null,
            pendingAt = null,
        ))

        assertTrue(response.isSuccessful)
        val body = Json.parseToJsonElement(requireNotNull(requestBody)).jsonObject
        assertEquals("out_for_delivery", body["current_status"]!!.jsonPrimitive.content)
        assertEquals("courier-test", body["courier_id"]!!.jsonPrimitive.content)
        assertEquals("2026-09-23T08:00:00.000Z", body["updated_at"]!!.jsonPrimitive.content)
        // Missing keys would leave old pending values in the database.
        for (key in listOf("pending_reason_code", "pending_reason_note", "pending_at")) {
            assertTrue("Missing $key", body.containsKey(key))
            assertEquals(JsonNull, body[key])
        }
    }
}
