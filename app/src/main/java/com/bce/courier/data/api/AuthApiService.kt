package com.bce.courier.data.api

import com.bce.courier.data.model.LoginRequest
import com.bce.courier.data.model.LoginResponse
import com.bce.courier.data.model.RefreshTokenRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthApiService {
    @POST("token")
    suspend fun login(
        @Query("grant_type") grantType: String = "password",
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("token")
    suspend fun refreshToken(
        @Query("grant_type") grantType: String = "refresh_token",
        @Body request: RefreshTokenRequest
    ): Response<LoginResponse>
}
