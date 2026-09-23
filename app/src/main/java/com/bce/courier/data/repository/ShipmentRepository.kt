package com.bce.courier.data.repository

import android.util.Log
import android.content.Context
import com.bce.courier.data.api.CourierApiService
import com.bce.courier.data.api.BranchApiService
import com.bce.courier.data.api.BranchScanKeluarRequest
import com.bce.courier.data.api.BranchScanTtdRequest
import com.bce.courier.data.model.ShipmentItem
import com.bce.courier.data.model.ShipmentLookupResult
import com.bce.courier.data.model.AppConfigResponse
import com.bce.courier.data.local.AppDatabase
import com.bce.courier.data.local.PendingUpdateEntity
import com.bce.courier.data.local.CachedShipmentEntity
import com.bce.courier.data.model.AssignmentOutcome
import com.bce.courier.data.model.DeliveryStatus
import com.bce.courier.data.model.DeliveryStatusUpdateRequest
import com.bce.courier.data.model.DeliveryUpdateOutcome
import com.bce.courier.data.model.PendingReasons
import com.bce.courier.data.sync.SyncWorker
import com.bce.courier.BuildConfig
import retrofit2.Response
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.ConnectException
import javax.net.ssl.SSLException
import java.net.UnknownServiceException
import java.io.File
import java.io.IOException

class ShipmentRepository(
    private val apiService: CourierApiService,
    private val branchApiService: BranchApiService,
    private val context: Context
) {
    private val tag = "ShipmentRepository"
    private val db = AppDatabase.getInstance(context)
    private val pendingDao = db.pendingUpdateDao()
    private val cachedShipmentDao = db.cachedShipmentDao()

    data class DashboardCache(
        val shipments: List<ShipmentItem>,
        val updatedAt: Long?,
    )

    suspend fun getDashboardCache(courierId: String): DashboardCache =
        withContext(Dispatchers.IO) {
            val rows = cachedShipmentDao.getForCourier(courierId)
            DashboardCache(
                shipments = rows.map(CachedShipmentEntity::toShipmentItem),
                updatedAt = rows.maxOfOrNull(CachedShipmentEntity::cachedAt),
            )
        }

    suspend fun replaceDashboardCache(
        courierId: String,
        shipments: List<ShipmentItem>,
        cachedAt: Long = System.currentTimeMillis(),
    ) = withContext(Dispatchers.IO) {
        cachedShipmentDao.replaceForCourier(
            courierId = courierId,
            items = shipments.map {
                CachedShipmentEntity.from(
                    ownerCourierId = courierId,
                    shipment = it,
                    cachedAt = cachedAt,
                )
            },
        )
    }

    suspend fun getConfig(): AppConfigResponse? {
        val response = apiService.getConfig()
        return if (response.isSuccessful) response.body()?.firstOrNull() else null
    }

    suspend fun getShipments(filters: Map<String, String>): Response<List<ShipmentItem>> {
        return apiService.getShipments(filters)
    }

    suspend fun lookupShipmentByAwb(awb: String): Result<ShipmentItem?> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanAwb = awb.trim().uppercase()
            val response = apiService.getShipments(mapOf("awb_number" to "eq.$cleanAwb"))
            if (!response.isSuccessful) {
                throw HttpStatusException(response.code(), "Gagal mencari AWB")
            }
            response.body()?.firstOrNull()
        }
    }

    suspend fun lookupShipmentDetailsByAwb(awb: String): Result<ShipmentLookupResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanAwb = awb.trim().uppercase()
                val response = apiService.getShipments(mapOf("awb_number" to "eq.$cleanAwb"))
                if (!response.isSuccessful) {
                    throw HttpStatusException(response.code(), "Gagal mencari AWB")
                }

                val registered = response.body()?.firstOrNull()
                val manifest = checkManifest(cleanAwb)
                val manifestName = manifest.firstValue("nama_penerima", "receiver_name", "penerima")
                val manifestPhone = manifest.firstValue(
                    "nomor_penerima",
                    "no_penerima",
                    "telepon_penerima",
                    "receiver_phone",
                )
                val manifestAddress = manifest.firstValue("alamat_penerima", "receiver_address")
                val manifestCod = manifest.codAmount()

                val enriched = when {
                    registered != null -> registered.copy(
                        recipient_name = registered.recipient_name.preferRealValue(manifestName),
                        recipient_phone = registered.recipient_phone.preferRealValue(manifestPhone),
                        address = registered.address.preferRealValue(manifestAddress),
                        cod_amount = registered.cod_amount.takeIf { it > 0.0 } ?: manifestCod,
                    )
                    manifest != null -> ShipmentItem(
                        awb = cleanAwb,
                        recipient_name = manifestName.orEmpty(),
                        recipient_phone = manifestPhone.orEmpty(),
                        address = manifestAddress.orEmpty(),
                        cod_amount = manifestCod,
                    )
                    else -> null
                }

                ShipmentLookupResult(
                    shipment = enriched,
                    isRegisteredShipment = registered != null,
                    hasManifestData = manifest != null,
                )
            }
        }

    suspend fun checkManifest(awb: String): Map<String, String>? {
        val cleanAwb = awb.trim().uppercase()
        
        // Helper function to convert JsonObject to Map<String, String>
        fun jsonObjectToMap(jsonObject: JsonObject): Map<String, String> {
            val result = mutableMapOf<String, String>()
            jsonObject.forEach { (key, value) ->
                when (value) {
                    is JsonPrimitive -> result[key] = value.content
                    else -> if (value !is JsonObject && value !is JsonArray) result[key] = value.toString()
                }
            }
            return result
        }

        // 1. Check local manifest
        try {
            val response = apiService.getManifest("eq.$cleanAwb")
            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                return jsonObjectToMap(response.body()!!.first())
            }
        } catch (_: Exception) { }

        // 2. Check manifest_cabang
        try {
            val response = apiService.getManifestCabang("eq.$cleanAwb")
            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                return jsonObjectToMap(response.body()!!.first())
            }
        } catch (_: Exception) { }

        // 3. Check Branch API (Borneo)
        if (cleanAwb.startsWith("BE")) {
            try {
                val response = branchApiService.getManifestRaw(BuildConfig.BRANCH_API_KEY, cleanAwb)
                if (response.isSuccessful && response.body() != null) {
                    val rawJson = response.body()!!
                    val result = mutableMapOf<String, String>()

                    fun extractFromPenerima(obj: JsonObject) {
                        obj["nama_penerima"]?.let { if (it is JsonPrimitive) result["nama_penerima"] = it.content }
                        obj["alamat_penerima"]?.let { if (it is JsonPrimitive) result["alamat_penerima"] = it.content }
                        obj["no_penerima"]?.let { if (it is JsonPrimitive) result["nomor_penerima"] = it.content }
                    }

                    fun extractFromPengirim(obj: JsonObject) {
                        obj["nama_pengirim"]?.let { if (it is JsonPrimitive) result["nama_pengirim"] = it.content }
                        obj["alamat_pengirim"]?.let { if (it is JsonPrimitive) result["alamat_pengirim"] = it.content }
                        obj["no_pengirim"]?.let { if (it is JsonPrimitive) result["nomor_pengirim"] = it.content }
                    }

                    fun parseManifestObject(manifest: JsonObject) {
                        manifest.forEach { (k, v) ->
                            when {
                                v is JsonPrimitive -> result[k] = v.content
                                v is JsonObject && k == "penerima" -> extractFromPenerima(v)
                                v is JsonObject && k == "pengirim" -> extractFromPengirim(v)
                            }
                        }
                    }

                    // Format 1: { success: true, manifest: {...} }
                    val successVal = rawJson["success"]
                    if (successVal is JsonPrimitive && successVal.booleanOrNull == true) {
                        val manifest = rawJson["manifest"] ?: rawJson["data"]
                        if (manifest is JsonObject) parseManifestObject(manifest)
                    }

                    // Format 2: Direct { penerima: {...}, pengirim: {...} }
                    if (result.isEmpty()) {
                        val penerima = rawJson["penerima"]
                        val pengirim = rawJson["pengirim"]
                        if (penerima is JsonObject) extractFromPenerima(penerima)
                        if (pengirim is JsonObject) extractFromPengirim(pengirim)
                    }

                    // Format 3: Flat structure
                    if (result.isEmpty()) {
                        rawJson["nama_penerima"]?.let { if (it is JsonPrimitive) result["nama_penerima"] = it.content }
                        rawJson["alamat_penerima"]?.let { if (it is JsonPrimitive) result["alamat_penerima"] = it.content }
                        rawJson["no_penerima"]?.let { if (it is JsonPrimitive) result["nomor_penerima"] = it.content }
                        rawJson["telepon_penerima"]?.let { if (it is JsonPrimitive) result["nomor_penerima"] = it.content }
                        rawJson["nama_pengirim"]?.let { if (it is JsonPrimitive) result["nama_pengirim"] = it.content }
                        rawJson["alamat_pengirim"]?.let { if (it is JsonPrimitive) result["alamat_pengirim"] = it.content }
                        rawJson["no_pengirim"]?.let { if (it is JsonPrimitive) result["nomor_pengirim"] = it.content }
                    }

                    if (result.isNotEmpty()) return result
                }
            } catch (_: Exception) { }
        }
        
        return null
    }

    suspend fun assignShipmentToCourier(
        awb: String,
        courierId: String,
        courierName: String
    ): AssignmentOutcome {
        return withContext(Dispatchers.IO) {
            val cleanAwb = awb.trim().uppercase()
            try {
                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date())

                val response = apiService.getShipments(mapOf("awb_number" to "eq.$cleanAwb"))
                if (!response.isSuccessful) {
                    return@withContext AssignmentOutcome.Failed(
                        awb = cleanAwb,
                        message = "Gagal memeriksa resi"
                    )
                }
                val existing = if (response.isSuccessful) response.body()?.firstOrNull() else null
                if (existing != null && existing.current_status.lowercase() == "delivered") {
                    return@withContext AssignmentOutcome.Delivered(cleanAwb)
                }
                if (existing?.courier_id == courierId) {
                    return@withContext AssignmentOutcome.AlreadyMine(cleanAwb)
                }

                val outcome = if (existing == null || existing.courier_id.isNullOrBlank()) {
                    AssignmentOutcome.NewAssignment(cleanAwb)
                } else {
                    AssignmentOutcome.Transferred(cleanAwb)
                }

                if (existing == null) {
                    val manifestData = checkManifest(cleanAwb)

                    val shipment = mutableMapOf(
                        "awb_number" to cleanAwb,
                        "sender_name" to (manifestData.firstValue("nama_pengirim", "sender_name") ?: "Auto Generated"),
                        "sender_address" to (manifestData.firstValue("alamat_pengirim", "sender_address") ?: "Auto Generated"),
                        "sender_phone" to (manifestData.firstValue("nomor_pengirim", "no_pengirim", "sender_phone") ?: "Auto Generated"),
                        "receiver_name" to (manifestData.firstValue("nama_penerima", "receiver_name", "penerima") ?: "Auto Generated"),
                        "receiver_address" to (manifestData.firstValue("alamat_penerima", "receiver_address") ?: "Auto Generated"),
                        "receiver_phone" to (manifestData.firstValue("nomor_penerima", "no_penerima", "telepon_penerima", "receiver_phone") ?: "Auto Generated"),
                        "weight" to (manifestData.firstValue("berat_kg", "weight") ?: "1"),
                        "dimensions" to "10x10x10",
                        "service_type" to "Standard",
                        "current_status" to "out_for_delivery",
                        "created_at" to timestamp,
                        "updated_at" to timestamp,
                        "courier_id" to courierId
                    )
                    val createRes = apiService.createShipment(shipment)
                    if (!createRes.isSuccessful) {
                        return@withContext AssignmentOutcome.Failed(cleanAwb, "Gagal membuat resi")
                    }
                } else {
                    val updates = mapOf<String, String?>(
                        "current_status" to "out_for_delivery",
                        "courier_id" to courierId,
                        "updated_at" to timestamp,
                        "pending_reason_code" to null,
                        "pending_reason_note" to null,
                        "pending_at" to null,
                    )
                    val updateRes = apiService.updateShipment("eq.$cleanAwb", updates)
                    if (!updateRes.isSuccessful) {
                        return@withContext AssignmentOutcome.Failed(cleanAwb, "Gagal update resi")
                    }
                }

                apiService.addHistory(mapOf(
                    "awb_number" to cleanAwb,
                    "status" to "out_for_delivery",
                    "location" to "Sorting Center",
                    "notes" to "Assigned to $courierName",
                    "created_at" to timestamp
                ))

                // Branch sync for Borneo system (BE resi only) - best effort
                if (cleanAwb.startsWith("BE")) {
                    runCatching {
                        val branchRequest = BranchScanKeluarRequest(
                            no_resi = cleanAwb,
                            nama_kurir = courierName,
                            armada = "motor",
                            plat_armada = "BCEJKT",
                            pemindai = courierName
                        )
                        val branchRes = branchApiService.syncScanKeluar(BuildConfig.BRANCH_API_KEY, branchRequest)
                        if (!branchRes.isSuccessful) {
                            val err = runCatching { branchRes.errorBody()?.string() }.getOrNull()
                            Log.w(tag, "Branch scankeluar failed for $cleanAwb: ${branchRes.code()} ${err ?: ""}".trim())
                        }
                    }.onFailure { e ->
                        Log.w(tag, "Branch scankeluar error for $cleanAwb: ${e.message}", e)
                    }
                }

                outcome
            } catch (e: Exception) {
                AssignmentOutcome.Failed(
                    awb = cleanAwb,
                    message = e.message ?: "Gagal memproses resi"
                )
            }
        }
    }

    suspend fun updateShipment(
        awb: String,
        status: String,
        notes: String?,
        pendingReasonCode: String?,
        pendingReasonNote: String?,
        lat: Double?,
        lng: Double?,
        photo: String?,
        courierName: String,
        courierId: String,
        armada: String,
        platArmada: String,
        locationAddress: String? = null
    ): Result<DeliveryUpdateOutcome> {
        return updateShipmentInternal(
            awb, status, notes, pendingReasonCode, pendingReasonNote, lat, lng, photo, courierName,
            courierId, armada, platArmada, locationAddress, saveToQueueOnError = true
        )
    }

    suspend fun updateShipmentDirect(
        awb: String,
        status: String,
        notes: String?,
        pendingReasonCode: String?,
        pendingReasonNote: String?,
        lat: Double?,
        lng: Double?,
        photo: String?,
        courierName: String,
        courierId: String,
        armada: String,
        platArmada: String,
        locationAddress: String? = null
    ): Result<DeliveryUpdateOutcome> {
        return updateShipmentInternal(
            awb, status, notes, pendingReasonCode, pendingReasonNote, lat, lng, photo, courierName,
            courierId, armada, platArmada, locationAddress, saveToQueueOnError = false
        )
    }

    suspend fun updateShipmentDirectWithUrl(
        awb: String,
        status: String,
        notes: String?,
        pendingReasonCode: String?,
        pendingReasonNote: String?,
        lat: Double?,
        lng: Double?,
        photoUrl: String?,
        courierName: String,
        courierId: String,
        armada: String,
        platArmada: String,
        locationAddress: String? = null
    ): Result<Unit> {
        val cleanStatus = DeliveryStatus.normalized(status)
        val validationError = validateDeliveryUpdate(cleanStatus, pendingReasonCode, pendingReasonNote)
        if (validationError != null) return Result.failure(IllegalArgumentException(validationError))

        return commitDeliveryStatus(
            awb = awb,
            status = cleanStatus,
            notes = notes,
            pendingReasonCode = pendingReasonCode,
            pendingReasonNote = pendingReasonNote,
            lat = lat,
            lng = lng,
            photoUrl = photoUrl,
            courierName = courierName,
            courierId = courierId,
            locationAddress = locationAddress,
        ).onSuccess {
            syncDeliveredToBranch(awb, cleanStatus, photoUrl, courierName, armada, platArmada)
        }
    }

    private suspend fun updateShipmentInternal(
        awb: String,
        status: String,
        notes: String?,
        pendingReasonCode: String?,
        pendingReasonNote: String?,
        lat: Double?,
        lng: Double?,
        photo: String?,
        courierName: String,
        courierId: String,
        armada: String,
        platArmada: String,
        locationAddress: String?,
        saveToQueueOnError: Boolean
    ): Result<DeliveryUpdateOutcome> {
        return try {
            val cleanAwb = awb.trim().uppercase()
            val cleanStatus = DeliveryStatus.normalized(status)
            val validationError = validateDeliveryUpdate(cleanStatus, pendingReasonCode, pendingReasonNote)
            if (validationError != null) return Result.failure(IllegalArgumentException(validationError))

            val photoFile = photo?.let { File(it) }?.takeIf { it.exists() }
            var photoUrl: String? = null

            if (photoFile != null) {
                photoUrl = try {
                    com.bce.courier.data.api.SupabaseStorageService.uploadImage(
                        context,
                        photoFile,
                        cleanAwb,
                        true,
                        courierName,
                        lat?.let { "${it},${lng}" }
                    )
                } catch (_: Exception) {
                    null
                }

                if (photoUrl == null) {
                    if (saveToQueueOnError) {
                        val pendingUpdate = PendingUpdateEntity(
                            awb = cleanAwb,
                            status = cleanStatus,
                            notes = notes,
                            pendingReasonCode = pendingReasonCode,
                            pendingReasonNote = pendingReasonNote,
                            latitude = lat,
                            longitude = lng,
                            photoPath = photo,
                            courierName = courierName,
                            courierId = courierId,
                            armada = armada,
                            platArmada = platArmada,
                            locationAddress = locationAddress,
                            priority = if (cleanStatus == DeliveryStatus.DELIVERED) 1 else 2
                        )
                        pendingDao.insert(pendingUpdate)
                        SyncWorker.enqueueSyncWork(context)
                        return Result.success(DeliveryUpdateOutcome.QUEUED)
                    }
                    return Result.failure(Exception("Photo upload failed"))
                }
            }

            try {
                commitDeliveryStatus(
                    awb = cleanAwb,
                    status = cleanStatus,
                    notes = notes,
                    pendingReasonCode = pendingReasonCode,
                    pendingReasonNote = pendingReasonNote,
                    lat = lat,
                    lng = lng,
                    photoUrl = photoUrl,
                    courierName = courierName,
                    courierId = courierId,
                    locationAddress = locationAddress,
                ).getOrThrow()

                syncDeliveredToBranch(cleanAwb, cleanStatus, photoUrl, courierName, armada, platArmada)

                if (photoFile != null) {
                    runCatching { photoFile.delete() }
                }
                Result.success(DeliveryUpdateOutcome.UPDATED)
            } catch (e: Exception) {
                when {
                    saveToQueueOnError && (
                        e is SocketTimeoutException ||
                            e is UnknownHostException ||
                            e is ConnectException ||
                            e is UnknownServiceException ||
                            e is SSLException ||
                            e is IOException ||
                            (e is HttpStatusException && e.retryable)
                        ) -> {
                        val pendingUpdate = PendingUpdateEntity(
                            awb = cleanAwb,
                            status = cleanStatus,
                            notes = notes,
                            pendingReasonCode = pendingReasonCode,
                            pendingReasonNote = pendingReasonNote,
                            latitude = lat,
                            longitude = lng,
                            photoPath = photo,
                            courierName = courierName,
                            courierId = courierId,
                            armada = armada,
                            platArmada = platArmada,
                            locationAddress = locationAddress,
                            priority = if (cleanStatus == DeliveryStatus.DELIVERED) 1 else 2
                        )
                        pendingDao.insert(pendingUpdate)
                        SyncWorker.enqueueSyncWork(context)
                        Result.success(DeliveryUpdateOutcome.QUEUED)
                    }
                    else -> throw e
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observePendingCount() = pendingDao.observePendingCount()

    private fun validateDeliveryUpdate(status: String, reasonCode: String?, reasonNote: String?): String? {
        if (status != DeliveryStatus.PENDING && status != DeliveryStatus.DELIVERED) {
            return "Status delivery tidak valid"
        }
        if (status == DeliveryStatus.PENDING && !PendingReasons.isValid(reasonCode)) {
            return "Alasan Pending wajib dipilih"
        }
        if (status == DeliveryStatus.PENDING && reasonCode == PendingReasons.OTHER && reasonNote.isNullOrBlank()) {
            return "Keterangan alasan Lainnya wajib diisi"
        }
        return null
    }

    private suspend fun commitDeliveryStatus(
        awb: String,
        status: String,
        notes: String?,
        pendingReasonCode: String?,
        pendingReasonNote: String?,
        lat: Double?,
        lng: Double?,
        photoUrl: String?,
        courierName: String,
        courierId: String,
        locationAddress: String?,
    ): Result<Unit> = runCatching {
        val response = apiService.updateDeliveryStatus(
            DeliveryStatusUpdateRequest(
                awb = awb.trim().uppercase(),
                courierId = courierId,
                courierName = courierName,
                status = status,
                reasonCode = pendingReasonCode,
                reasonNote = pendingReasonNote?.trim()?.ifBlank { null },
                notes = notes?.trim()?.ifBlank { null },
                location = locationAddress,
                latitude = lat,
                longitude = lng,
                photoUrl = photoUrl,
            )
        )
        if (!response.isSuccessful) {
            val error = runCatching { response.errorBody()?.string() }.getOrNull()
            throw HttpStatusException(response.code(), error ?: "Gagal update status")
        }
    }

    private suspend fun syncDeliveredToBranch(
        awb: String,
        status: String,
        photoUrl: String?,
        courierName: String,
        armada: String,
        platArmada: String,
    ) {
        val cleanAwb = awb.trim().uppercase()
        if (status != DeliveryStatus.DELIVERED || !cleanAwb.startsWith("BE")) return
        if (photoUrl.isNullOrBlank()) {
            Log.w(tag, "Skip branch scanttd for $cleanAwb: missing photoUrl")
            return
        }
        runCatching {
            val branchRes = branchApiService.syncScanTtd(
                BuildConfig.BRANCH_API_KEY,
                BranchScanTtdRequest(
                    no_resi = cleanAwb,
                    nama_kurir = courierName,
                    armada = armada,
                    plat_armada = platArmada,
                    gambar = photoUrl,
                    pemindai = courierName,
                )
            )
            if (!branchRes.isSuccessful) {
                val error = runCatching { branchRes.errorBody()?.string() }.getOrNull()
                Log.w(tag, "Branch scanttd failed for $cleanAwb: ${branchRes.code()} ${error.orEmpty()}".trim())
            }
        }.onFailure { error ->
            Log.w(tag, "Branch scanttd error for $cleanAwb: ${error.message}", error)
        }
    }

    private class HttpStatusException(
        val statusCode: Int,
        message: String,
    ) : Exception(message) {
        val retryable: Boolean = statusCode == 408 || statusCode == 429 || statusCode >= 500
    }
}

private fun Map<String, String>?.firstValue(vararg keys: String): String? {
    if (this == null) return null
    return keys.firstNotNullOfOrNull { key ->
        get(key)?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }
}

private fun String.preferRealValue(fallback: String?): String {
    val current = trim()
    return if (
        current.isBlank() ||
        current.equals("Auto Generated", ignoreCase = true) ||
        current.equals("N/A", ignoreCase = true)
    ) {
        fallback?.takeIf { it.isNotBlank() } ?: this
    } else {
        this
    }
}

private fun Map<String, String>?.codAmount(): Double {
    if (this == null) return 0.0
    val direct = firstValue("ongkos_kirim", "cod_amount", "nominal_cod", "nilai_cod")
    if (direct != null) return direct.toMoneyDouble()

    val paymentMethod = firstValue("metode_pembayaran", "payment_method")
    return if (paymentMethod?.contains("cod", ignoreCase = true) == true) {
        firstValue("total", "grand_total")?.toMoneyDouble() ?: 0.0
    } else {
        0.0
    }
}

private fun String.toMoneyDouble(): Double {
    val compact = trim()
        .replace(Regex("(?i)rp"), "")
        .replace(" ", "")
    if (compact.matches(Regex("^\\d{1,3}([.,]\\d{3})+$"))) {
        return compact.filter(Char::isDigit).toDoubleOrNull() ?: 0.0
    }
    compact.toDoubleOrNull()?.let { return it }
    compact.replace(',', '.').toDoubleOrNull()?.let { return it }

    val digits = compact.filter(Char::isDigit)
    return digits.toDoubleOrNull() ?: 0.0
}
