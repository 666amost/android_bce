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
import java.util.concurrent.TimeUnit

object NetworkModule {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }

    private val sessionManager by lazy { SessionManager(appContext) }

    private val authHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY).build())
        }
        .build()

    private fun tryRefreshTokenSync(sessionManager: SessionManager): Boolean {
        val refreshToken = sessionManager.getRefreshToken() ?: run {
            sessionManager.setNeedsReauthentication(true)
            return false
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

            authHttpClient.newCall(request).execute().use { response ->
              if (response.isSuccessful) {
                val body = response.body?.string()
                if (body != null) {
                    val loginResponse = json.decodeFromString(com.bce.courier.data.model.LoginResponse.serializer(), body)
                    // A logout or a different login must not be overwritten by an old refresh.
                    if (sessionManager.getRefreshToken() != refreshToken) return false
                    sessionManager.updateAccessToken(loginResponse.access_token, loginResponse.refresh_token)
                    Log.d("NetworkModule", "Token refreshed successfully")
                    true
                } else false
              } else {
                Log.w("NetworkModule", "Token refresh failed: ${response.code}")
                if (response.code in listOf(400, 401) && sessionManager.getRefreshToken() == refreshToken) {
                    sessionManager.setNeedsReauthentication(true)
                }
                false
              }
            }
        } catch (e: Exception) {
            Log.e("NetworkModule", "Token refresh error", e)
            false
        }
    }

    private val refreshCoordinator = TokenRefreshCoordinator(
        readAccessToken = { sessionManager.getAccessToken() },
        refreshSession = { tryRefreshTokenSync(sessionManager) },
    )

    val authenticatedClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .addInterceptor(SessionAuthInterceptor(
            BuildConfig.SUPABASE_ANON_KEY,
            { sessionManager.getAccessToken() },
            refreshCoordinator,
        ))
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
        .client(authenticatedClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val apiService: CourierApiService = retrofit.create(CourierApiService::class.java)

    // Separate retrofit for Auth (direct to Supabase Auth)
    private val authRetrofit = Retrofit.Builder()
        .baseUrl("${BuildConfig.SUPABASE_URL}/auth/v1/")
        .client(authHttpClient)
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
            .connectTimeout(10, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
            .build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
    
    val branchApiService: BranchApiService = branchRetrofit.create(BranchApiService::class.java)
}
