package com.bce.courier.data.api

import android.content.Context
import android.util.Log
import com.bce.courier.BuildConfig
import com.bce.courier.data.manager.SessionManager
import com.bce.courier.data.model.RefreshTokenRequest
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.bce.courier.data.repository.ShipmentRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.net.URI

object NetworkModule {
    private lateinit var appContext: Context
    @Volatile private var isRefreshing = false

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }

    private fun getSessionManager(): SessionManager = SessionManager(appContext)

    private fun tryRefreshTokenSync(sessionManager: SessionManager): Boolean {
        val refreshToken = sessionManager.getRefreshToken() ?: return false
        if (isRefreshing) return false
        synchronized(this) {
            if (isRefreshing) return false
            isRefreshing = true
        }
        return try {
            val refreshBody = RefreshTokenRequest(refreshToken)
            val jsonBody = json.encodeToString(RefreshTokenRequest.serializer(), refreshBody)
            val mediaType = "application/json".toMediaType()
            val requestBody = jsonBody.toRequestBody(mediaType)

            val request = okhttp3.Request.Builder()
                .url("${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token")
                .post(requestBody)
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .build()

            val client = OkHttpClient.Builder().build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val loginResponse = json.decodeFromString(com.bce.courier.data.model.LoginResponse.serializer(), body)
                    sessionManager.updateAccessToken(loginResponse.access_token, loginResponse.refresh_token)
                    Log.d("NetworkModule", "Token refreshed successfully")
                    true
                } else false
            } else {
                Log.w("NetworkModule", "Token refresh failed: ${response.code}")
                false
            }
        } catch (e: Exception) {
            Log.e("NetworkModule", "Token refresh error", e)
            false
        } finally {
            isRefreshing = false
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val sessionManager = getSessionManager()
            val requestBuilder = chain.request().newBuilder()
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)

            val token = sessionManager.getAccessToken()
            if (token != null) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            } else {
                requestBuilder.addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            }

            var response = chain.proceed(requestBuilder.build())

            // Jika 401 → coba refresh token, retry sekali
            if (response.code == 401 && token != null) {
                val refreshed = tryRefreshTokenSync(sessionManager)
                if (refreshed) {
                    response.close()
                    val newToken = sessionManager.getAccessToken()
                    val retryRequest = chain.request().newBuilder()
                        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .header("Authorization", "Bearer $newToken")
                        .build()
                    response = chain.proceed(retryRequest)
                }
                // Jika masih 401 setelah refresh gagal, baru clear session
                if (response.code == 401) {
                    sessionManager.clearSession()
                }
            }

            response
        }
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("${BuildConfig.SUPABASE_URL}/rest/v1/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val apiService: CourierApiService = retrofit.create(CourierApiService::class.java)

    // Separate retrofit for Auth (direct to Supabase Auth)
    private val authRetrofit = Retrofit.Builder()
        .baseUrl("${BuildConfig.SUPABASE_URL}/auth/v1/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val authApiService: AuthApiService = authRetrofit.create(AuthApiService::class.java)

    val shipmentRepository by lazy {
        ShipmentRepository(apiService, branchApiService, appContext)
    }

    // Separate client for Branch Sync
    private val branchBaseUrl = URI(BuildConfig.BRANCH_SYNC_URL).let { uri ->
        require(!uri.scheme.isNullOrBlank() && !uri.authority.isNullOrBlank()) {
            "BRANCH_SYNC_URL must be configured in the local gradle.properties file"
        }
        "${uri.scheme}://${uri.authority}/"
    }

    private val branchRetrofit = Retrofit.Builder()
        .baseUrl(branchBaseUrl)
        .client(OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
            .build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
    
    val branchApiService: BranchApiService = branchRetrofit.create(BranchApiService::class.java)
}
