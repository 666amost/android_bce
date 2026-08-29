package com.bce.courier.data.repository

import android.util.Log
import android.content.Context
import com.bce.courier.data.api.CourierApiService
import com.bce.courier.data.api.BranchApiService
import com.bce.courier.data.api.BranchScanKeluarRequest
import com.bce.courier.data.api.BranchScanTtdRequest
import com.bce.courier.data.model.ShipmentItem
import com.bce.courier.data.model.AppConfigResponse
import com.bce.courier.data.local.AppDatabase
import com.bce.courier.data.local.PendingUpdateEntity
import com.bce.courier.data.local.CachedShipmentEntity
import com.bce.courier.data.model.AssignmentOutcome
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
                        "sender_name" to (manifestData?.get("nama_pengirim") ?: "Auto Generated"),
                        "sender_address" to (manifestData?.get("alamat_pengirim") ?: "Auto Generated"),
                        "sender_phone" to (manifestData?.get("nomor_pengirim") ?: "Auto Generated"),
                        "receiver_name" to (manifestData?.get("nama_penerima") ?: "Auto Generated"),
                        "receiver_address" to (manifestData?.get("alamat_penerima") ?: "Auto Generated"),
                        "receiver_phone" to (manifestData?.get("nomor_penerima") ?: "Auto Generated"),
                        "weight" to (manifestData?.get("berat_kg") ?: "1"),
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
                    val updates = mapOf(
                        "current_status" to "out_for_delivery",
                        "courier_id" to courierId,
                        "updated_at" to timestamp
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
        lat: Double?,
        lng: Double?,
        photo: String?,
        courierName: String,
        courierId: String,
        armada: String,
        platArmada: String,
        locationAddress: String? = null
    ): Result<Unit> {
        return updateShipmentInternal(
            awb, status, notes, lat, lng, photo, courierName,
            courierId, armada, platArmada, locationAddress, saveToQueueOnError = true
        )
    }

    suspend fun updateShipmentDirect(
        awb: String,
        status: String,
        notes: String?,
        lat: Double?,
        lng: Double?,
        photo: String?,
        courierName: String,
        courierId: String,
        armada: String,
        platArmada: String,
        locationAddress: String? = null
    ): Result<Unit> {
        return updateShipmentInternal(
            awb, status, notes, lat, lng, photo, courierName,
            courierId, armada, platArmada, locationAddress, saveToQueueOnError = false
        )
    }

    suspend fun updateShipmentDirectWithUrl(
        awb: String,
        status: String,
        notes: String?,
        lat: Double?,
        lng: Double?,
        photoUrl: String?,
        courierName: String,
        courierId: String,
        armada: String,
        platArmada: String,
        locationAddress: String? = null
    ): Result<Unit> {
        return try {
            val cleanAwb = awb.trim().uppercase()

            val updates = mutableMapOf(
                "current_status" to status,
                "courier_id" to courierId,
                "updated_at" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date())
            )

            val supabaseResponse = apiService.updateShipment("eq.$cleanAwb", updates)
            if (!supabaseResponse.isSuccessful) {
                return Result.failure(Exception("Network error: ${supabaseResponse.code()}"))
            }

            try {
                val historyNotes = if (notes.isNullOrBlank()) {
                    "Updated by $courierName"
                } else {
                    "Updated by $courierName. $notes"
                }
                val historyData = mutableMapOf(
                    "awb_number" to cleanAwb,
                    "status" to status,
                    "location" to (locationAddress ?: courierName),
                    "notes" to historyNotes,
                    "created_at" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }.format(java.util.Date())
                )
                photoUrl?.let { historyData["photo_url"] = it }
                lat?.let { historyData["latitude"] = it.toString() }
                lng?.let { historyData["longitude"] = it.toString() }

                apiService.addHistory(historyData)
            } catch (_: Exception) { }

            if (status.lowercase() == "delivered") {
                if (cleanAwb.startsWith("BE") && !photoUrl.isNullOrBlank()) {
                    runCatching {
                        val branchRequest = BranchScanTtdRequest(
                            no_resi = cleanAwb,
                            nama_kurir = courierName,
                            armada = armada,
                            plat_armada = platArmada,
                            gambar = photoUrl,
                            pemindai = courierName
                        )
                        val branchRes = branchApiService.syncScanTtd(BuildConfig.BRANCH_API_KEY, branchRequest)
                        if (!branchRes.isSuccessful) {
                            val err = runCatching { branchRes.errorBody()?.string() }.getOrNull()
                            Log.w(tag, "Branch scanttd failed for $cleanAwb: ${branchRes.code()} ${err ?: ""}".trim())
                        }
                    }.onFailure { e ->
                        Log.w(tag, "Branch scanttd error for $cleanAwb: ${e.message}", e)
                    }
                } else if (cleanAwb.startsWith("BE")) {
                    Log.w(tag, "Skip branch scanttd for $cleanAwb: missing photoUrl")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun updateShipmentInternal(
        awb: String,
        status: String,
        notes: String?,
        lat: Double?,
        lng: Double?,
        photo: String?,
        courierName: String,
        courierId: String,
        armada: String,
        platArmada: String,
        locationAddress: String?,
        saveToQueueOnError: Boolean
    ): Result<Unit> {
        return try {
            val cleanAwb = awb.trim().uppercase()

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
                            status = status,
                            notes = notes,
                            latitude = lat,
                            longitude = lng,
                            photoPath = photo,
                            courierName = courierName,
                            courierId = courierId,
                            armada = armada,
                            platArmada = platArmada,
                            locationAddress = locationAddress,
                            priority = if (status.lowercase() == "delivered") 1 else 2
                        )
                        pendingDao.insert(pendingUpdate)
                        SyncWorker.enqueueSyncWork(context)
                        return Result.success(Unit)
                    }
                    return Result.failure(Exception("Photo upload failed"))
                }
            }

            val updates = mutableMapOf(
                "current_status" to status,
                "courier_id" to courierId,
                "updated_at" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date())
            )

            try {
                val supabaseResponse = apiService.updateShipment("eq.$cleanAwb", updates)
                if (!supabaseResponse.isSuccessful) {
                    throw Exception("Network error: ${supabaseResponse.code()}")
                }

                try {
                    val historyNotes = if (notes.isNullOrBlank()) {
                        "Updated by $courierName"
                    } else {
                        "Updated by $courierName. $notes"
                    }
                    val historyData = mutableMapOf(
                        "awb_number" to cleanAwb,
                        "status" to status,
                        "location" to (locationAddress ?: courierName),
                        "notes" to historyNotes,
                        "created_at" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }.format(java.util.Date())
                    )
                    photoUrl?.let { historyData["photo_url"] = it }
                    lat?.let { historyData["latitude"] = it.toString() }
                    lng?.let { historyData["longitude"] = it.toString() }

                    apiService.addHistory(historyData)
                } catch (_: Exception) { }

                if (status.lowercase() == "delivered") {
                    if (cleanAwb.startsWith("BE") && !photoUrl.isNullOrBlank()) {
                        runCatching {
                            val branchRequest = BranchScanTtdRequest(
                                no_resi = cleanAwb,
                                nama_kurir = courierName,
                                armada = armada,
                                plat_armada = platArmada,
                                gambar = photoUrl,
                                pemindai = courierName
                            )
                            val branchRes = branchApiService.syncScanTtd(BuildConfig.BRANCH_API_KEY, branchRequest)
                            if (!branchRes.isSuccessful) {
                                val err = runCatching { branchRes.errorBody()?.string() }.getOrNull()
                                Log.w(tag, "Branch scanttd failed for $cleanAwb: ${branchRes.code()} ${err ?: ""}".trim())
                            }
                        }.onFailure { e ->
                            Log.w(tag, "Branch scanttd error for $cleanAwb: ${e.message}", e)
                        }
                    } else if (cleanAwb.startsWith("BE")) {
                        Log.w(tag, "Skip branch scanttd for $cleanAwb: missing photoUrl")
                    }
                }

                if (photoFile != null) {
                    runCatching { photoFile.delete() }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                when {
                    saveToQueueOnError && (
                        e is SocketTimeoutException ||
                            e is UnknownHostException ||
                            e is ConnectException ||
                            e is UnknownServiceException ||
                            e is SSLException
                        ) -> {
                        val pendingUpdate = PendingUpdateEntity(
                            awb = cleanAwb,
                            status = status,
                            notes = notes,
                            latitude = lat,
                            longitude = lng,
                            photoPath = photo,
                            courierName = courierName,
                            courierId = courierId,
                            armada = armada,
                            platArmada = platArmada,
                            locationAddress = locationAddress,
                            priority = if (status.lowercase() == "delivered") 1 else 2
                        )
                        pendingDao.insert(pendingUpdate)
                        SyncWorker.enqueueSyncWork(context)
                        Result.success(Unit)
                    }
                    else -> throw e
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observePendingCount() = pendingDao.observePendingCount()
}
