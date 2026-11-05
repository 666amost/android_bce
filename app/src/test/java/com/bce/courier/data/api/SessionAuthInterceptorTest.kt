package com.bce.courier.data.api

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class SessionAuthInterceptorTest {
    @Test
    fun simultaneousExpiredRequestsAllRetryWithOneRefresh() {
        val token = AtomicReference("expired")
        val refreshes = AtomicInteger()
        val rejectedTogether = CountDownLatch(6)
        val coordinator = TokenRefreshCoordinator(token::get, {
            refreshes.incrementAndGet()
            token.set("renewed")
            true
        })
        val client = OkHttpClient.Builder()
            .addInterceptor(SessionAuthInterceptor("public-key", token::get, coordinator))
            .addInterceptor { chain ->
                val request = chain.request()
                assertEquals(1, request.headers.values("Authorization").size)
                val expired = request.header("Authorization") == "Bearer expired"
                if (expired) {
                    rejectedTogether.countDown()
                    check(rejectedTogether.await(5, TimeUnit.SECONDS))
                }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(if (expired) 401 else 200).message("test")
                    .body("{}".toResponseBody()).build()
            }.build()
        val executor = Executors.newFixedThreadPool(6)
        try {
            val results = (1..6).map {
                executor.submit<Int> {
                    client.newCall(Request.Builder().url("https://example.invalid/rest/v1/shipments")
                        .header("Authorization", "Bearer stale-header").build()).execute().use { it.code }
                }
            }
            assertEquals(List(6) { 200 }, results.map { it.get(10, TimeUnit.SECONDS) })
            assertEquals(1, refreshes.get())
            assertEquals("renewed", token.get())
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun temporaryRefreshFailureRetainsTokenAndCanRecoverLater() {
        val token = AtomicReference("expired")
        var now = 0L
        var online = false
        var attempts = 0
        val coordinator = TokenRefreshCoordinator(token::get, {
            attempts++
            if (online) token.set("renewed")
            online
        }, { now })
        assertNull(coordinator.refresh("expired"))
        assertNull(coordinator.refresh("expired"))
        assertEquals(1, attempts)
        assertEquals("expired", token.get())
        now = 10_001
        online = true
        assertEquals("renewed", coordinator.refresh("expired"))
        assertEquals(2, attempts)
    }

    @Test
    fun missingSessionDoesNotRefreshOrRestoreAnOldToken() {
        val coordinator = TokenRefreshCoordinator({ null }, { error("Must not refresh after logout") })
        assertNull(coordinator.refresh("old-session"))
    }
}
