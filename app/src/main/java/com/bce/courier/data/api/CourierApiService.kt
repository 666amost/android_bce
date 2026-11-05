package com.bce.courier.data.api

import com.bce.courier.data.model.AppConfigResponse
import com.bce.courier.data.model.DeliveryStatusUpdateRequest
import com.bce.courier.data.model.ShipmentAssignmentRequest
import com.bce.courier.data.model.ShipmentItem
import com.bce.courier.data.model.UserData
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.*

interface CourierApiService {
    @GET("config")
    suspend fun getConfig(
        @Query("app_id") appIdFilter: String = "eq.bce_courier_app",
        @Query("select") select: String = "*"
    ): Response<List<AppConfigResponse>>

    @GET("shipments")
    suspend fun getShipments(
        @QueryMap filters: Map<String, String>,
        @Query("select") select: String = "*"
    ): Response<List<ShipmentItem>>

    @PATCH("shipments")
    suspend fun updateShipment(
        @Query("awb_number") awbFilter: String,
        @Body updates: ShipmentAssignmentRequest
    ): Response<Unit>

    @POST("shipments")
    suspend fun createShipment(
        @Body shipment: Map<String, String>
    ): Response<Unit>

    @POST("shipment_history")
    suspend fun addHistory(
        @Body history: Map<String, String>
    ): Response<Unit>

    @POST("rpc/update_delivery_status")
    suspend fun updateDeliveryStatus(
        @Body request: DeliveryStatusUpdateRequest
    ): Response<JsonObject>

    @POST("courier_notifications")
    suspend fun insertCourierNotification(
        @Body payload: Map<String, String>
    ): Response<Unit>

    @POST("courier_current_locations")
    suspend fun updateLocation(
        @Body location: Map<String, String>
    ): Response<Unit>

    @GET("manifest")
    suspend fun getManifest(
        @Query("awb_no") awbFilter: String
    ): Response<List<JsonObject>>

    @GET("manifest_cabang")
    suspend fun getManifestCabang(
        @Query("awb_no") awbFilter: String
    ): Response<List<JsonObject>>

    @GET("users")
    suspend fun getUserById(
        @Query("id") idFilter: String,
        @Query("select") select: String = "id,name,email"
    ): Response<List<UserData>>

    @POST("manifest")
    @Headers("Prefer: return=representation")
    suspend fun insertManifest(
        @Body payload: JsonObject
    ): Response<List<JsonObject>>

    @GET("customers")
    suspend fun getCustomers(
        @Query("is_active") isActiveFilter: String = "eq.true",
        @Query("branch_origin") branchOriginFilter: String? = null,
        @Query("select") select: String = "*",
        @Query("order") order: String = "nama_pengirim.asc"
    ): Response<List<JsonObject>>

    @GET("manifest")
    suspend fun getManifestList(
        @Query("courier_id") courierIdFilter: String? = null,
        @Query("awb_no") awbNoFilter: String? = null,
        @Query("or") orFilter: String? = null,
        @Query("origin_branch") originBranchFilter: String? = null,
        @Query("order") order: String = "awb_date.desc,awb_no.desc",
        @Query("limit") limit: Int = 50,
        @Query("select") select: String = "*"
    ): Response<List<JsonObject>>
}
