package com.bce.courier.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val got_type: String = "password"
)

@Serializable
data class LoginResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int,
    val refresh_token: String? = null,
    val user: UserInfo
)

@Serializable
data class UserInfo(
    val id: String,
    val email: String,
    val user_metadata: UserMetadata? = null
)

@Serializable
data class UserMetadata(
    val full_name: String? = null,
    val role: String? = null
)

@Serializable
data class RefreshTokenRequest(
    val refresh_token: String
)

@Serializable
data class UserData(
    val id: String,
    val name: String? = null,
    val email: String? = null
)

