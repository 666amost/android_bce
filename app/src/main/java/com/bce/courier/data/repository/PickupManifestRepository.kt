package com.bce.courier.data.repository

import com.bce.courier.data.model.ManifestPayload
import com.bce.courier.util.PickupAwbNumber
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.Response
import java.io.IOException

class PickupManifestRepository(
    private val insert: suspend (JsonObject) -> Response<List<JsonObject>>,
    private val findByAwb: suspend (String) -> Response<List<JsonObject>>,
) {
    suspend fun save(
        payload: ManifestPayload,
        onCandidate: suspend (ManifestPayload) -> Unit,
    ): ManifestPayload {
        var candidate = payload
        repeat(9) {
            // Persist the exact candidate before sending so an interrupted save can be retried.
            onCandidate(candidate)
            val response = insert(candidate.toJsonObject())
            if (response.isSuccessful) {
                val stored = response.body()?.firstOrNull() ?: findByAwb(candidate.awbNo).let {
                    if (!it.isSuccessful) throw IOException("Belum dapat memeriksa resi yang tersimpan")
                    it.body()?.firstOrNull()
                }
                check(stored != null && candidate.matchesStored(stored)) {
                    "Data resi belum dapat dipastikan sesuai. Draf tetap tersimpan; periksa AWB ${candidate.awbNo}."
                }
                return candidate
            }
            val error = response.errorBody()?.string().orEmpty()
            if (response.code() != 409) throw IOException("Gagal menyimpan resi (${response.code()})")

            val existing = findByAwb(candidate.awbNo)
            if (!existing.isSuccessful) throw IOException("Belum dapat memeriksa nomor resi duplikat. Draf tetap tersimpan.")
            val stored = existing.body()?.firstOrNull()
            // An initial collision is a new parcel, even if all its details happen to match.
            val retryingPreviousSave = candidate.isRetry || !PickupAwbNumber.isNumeric(candidate.awbNo)
            if (retryingPreviousSave && stored != null && candidate.matchesStored(stored)) return candidate

            val awbCollision = stored != null || isAwbUniqueViolation(error)
            if (!awbCollision) throw IOException("Gagal menyimpan resi: konflik data (${response.code()})")
            candidate = candidate.copy(awbNo = PickupAwbNumber.withLeadingZero(candidate.awbNo), isRetry = false)
        }
        throw IOException("Nomor resi masih duplikat. Tekan Generate untuk memilih nomor baru. Draf tetap tersimpan.")
    }

    private fun isAwbUniqueViolation(error: String): Boolean = runCatching {
        val body = Json.decodeFromString<JsonObject>(error)
        body["code"]?.jsonPrimitive?.contentOrNull == "23505" &&
            listOf("message", "details").any { body[it]?.jsonPrimitive?.contentOrNull?.contains("awb_no", ignoreCase = true) == true }
    }.getOrDefault(false)
}
