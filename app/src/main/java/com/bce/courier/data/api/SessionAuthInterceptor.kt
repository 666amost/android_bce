package com.bce.courier.data.api

import okhttp3.Interceptor
import okhttp3.Response

/** Requests rejected together share one refresh, including storage uploads. */
class TokenRefreshCoordinator(
    private val readAccessToken: () -> String?,
    private val refreshSession: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var failedToken: String? = null
    private var retryAfter = 0L

    @Synchronized
    fun refresh(rejectedToken: String): String? {
        val current = readAccessToken() ?: return null
        if (current != rejectedToken) return current
        if (failedToken == current && now() < retryAfter) return null
        return if (refreshSession()) {
            failedToken = null
            readAccessToken()
        } else {
            failedToken = current
            retryAfter = now() + 10_000L
            null
        }
    }
}

class SessionAuthInterceptor(
    private val apiKey: String,
    private val readAccessToken: () -> String?,
    private val refreshCoordinator: TokenRefreshCoordinator,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = readAccessToken()
        val request = chain.request().newBuilder()
            .header("apikey", apiKey)
            .header("Authorization", "Bearer ${token ?: apiKey}")
            .build()
        val response = chain.proceed(request)
        if (response.code != 401 || token == null) return response

        val refreshedToken = refreshCoordinator.refresh(token) ?: return response
        response.close()
        return chain.proceed(request.newBuilder()
            .header("Authorization", "Bearer $refreshedToken")
            .build())
    }
}
