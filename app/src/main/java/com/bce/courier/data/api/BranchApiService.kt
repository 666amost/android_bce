@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)
package com.bce.courier.data.api

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

@Serializable
data class BranchScanTtdRequest(
    val no_resi: String,
    val nama_kurir: String,
    val armada: String,
    val plat_armada: String,
    val gambar: String, // Base64 or URL
    val pemindai: String
)

@Serializable
data class BranchScanKeluarRequest(
    val no_resi: String,
    val nama_kurir: String,
    val armada: String,
    val plat_armada: String,
    val pemindai: String
)

@Serializable
data class BranchManifestResponse(
    val success: Boolean = false,
    val manifest: kotlinx.serialization.json.JsonObject? = null,
    val data: kotlinx.serialization.json.JsonObject? = null,
    val error: String? = null,
    val awb_no: String? = null,
    val no_resi: String? = null,
    val penerima: kotlinx.serialization.json.JsonObject? = null,
    val pengirim: kotlinx.serialization.json.JsonObject? = null
)

interface BranchApiService {
    @POST("api/scankeluar")
    suspend fun syncScanKeluar(
        @Header("X-API-KEY") apiKey: String,
        @Body request: BranchScanKeluarRequest
    ): Response<Unit>

    @POST("api/scanttd")
    suspend fun syncScanTtd(
        @Header("X-API-KEY") apiKey: String,
        @Body request: BranchScanTtdRequest
    ): Response<Unit>

    @GET("api/trackings/{no_resi}")
    suspend fun getManifest(
        @Header("X-API-KEY") apiKey: String,
        @Path("no_resi") noResi: String
    ): Response<BranchManifestResponse>

    @GET("api/trackings/{no_resi}")
    suspend fun getManifestRaw(
        @Header("X-API-KEY") apiKey: String,
        @Path("no_resi") noResi: String
    ): Response<kotlinx.serialization.json.JsonObject>
}
