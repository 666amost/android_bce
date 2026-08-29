package com.bce.courier.util

import android.util.Log
import com.bce.courier.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicInteger

data class PostgresChange(
    val table: String,
    val eventType: String?,
    val awbNumber: String?,
    val newCourierId: String?,
    val oldCourierId: String?
)

private val JsonPrimitive.contentOrNull: String?
    get() = if (this == JsonNull) null else this.content

class SupabaseRealtimeSubscription {

    companion object {
        private const val TAG = "SupabaseRealtime"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val okHttpClient = OkHttpClient.Builder().build()
    private val refCounter = AtomicInteger(0)

    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt: Int = 0
    private var isRunning: Boolean = false

    private var lastScope: CoroutineScope? = null
    private var lastTable: String? = null
    private var lastFilter: String? = null
    private var lastEvents: List<String> = listOf("*")
    private var lastAccessToken: String? = null
    private var lastCallback: ((PostgresChange) -> Unit)? = null

    fun start(
        scope: CoroutineScope,
        table: String,
        filter: String?,
        events: List<String> = listOf("*"),
        accessToken: String?,
        onPostgresChange: (PostgresChange) -> Unit
    ) {
        stop()
        isRunning = true
        reconnectAttempt = 0
        lastScope = scope
        lastTable = table
        lastFilter = filter
        lastEvents = events.ifEmpty { listOf("*") }
        lastAccessToken = accessToken
        lastCallback = onPostgresChange

        connect()
    }

    fun stop() {
        isRunning = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        webSocket?.close(1000, "closed")
        webSocket = null
        lastScope = null
        lastTable = null
        lastFilter = null
        lastAccessToken = null
        lastCallback = null
    }

    private fun connect() {
        val scope = lastScope ?: return
        val table = lastTable ?: return
        val filter = lastFilter
        val events = lastEvents
        val accessToken = lastAccessToken
        val onPostgresChange = lastCallback ?: return

        val wsUrl = buildRealtimeWebSocketUrl(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer ${accessToken ?: BuildConfig.SUPABASE_ANON_KEY}")
            .build()

        val topic = "realtime:public:$table"

        webSocket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    reconnectAttempt = 0
                    Log.d(TAG, "WebSocket open (table=$table)")

                    val joinPayload = buildJoinPayload(table = table, filter = filter, events = events, accessToken = accessToken)
                    webSocket.send(buildMessage(topic = topic, event = "phx_join", payload = joinPayload))

                    heartbeatJob?.cancel()
                    heartbeatJob = scope.launch(Dispatchers.IO) {
                        while (isActive) {
                            delay(25_000)
                            webSocket.send(buildMessage(topic = "phoenix", event = "heartbeat", payload = emptyJsonObject()))
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                    val event = (root["event"] as? JsonPrimitive)?.contentOrNull ?: return
                    val payloadRaw = root["payload"]
                    val payloadObj = payloadRaw as? JsonObject

                    if (event == "phx_error" || event == "phx_close") {
                        Log.w(TAG, "Channel event=$event (table=$table) payload=$payloadRaw")
                        scheduleReconnect()
                        return
                    }

                    if (event == "phx_reply") {
                        val status = (payloadObj?.get("status") as? JsonPrimitive)?.contentOrNull
                        if (status != null && status != "ok") {
                            Log.w(TAG, "Join reply status=$status (table=$table) payload=$payloadRaw")
                            scheduleReconnect()
                        }
                        return
                    }

                    if (event != "postgres_changes") return

                    val data = (payloadObj?.get("data") as? JsonObject) ?: payloadObj
                    val record = data?.get("record") as? JsonObject
                    val oldRecord = data?.get("old_record") as? JsonObject

                    val awb = (record?.get("awb_number") as? JsonPrimitive)?.contentOrNull
                        ?: (record?.get("awb") as? JsonPrimitive)?.contentOrNull
                    val newCourierId = (record?.get("courier_id") as? JsonPrimitive)?.contentOrNull
                    val oldCourierId = (oldRecord?.get("courier_id") as? JsonPrimitive)?.contentOrNull
                    val eventType = (data?.get("type") as? JsonPrimitive)?.contentOrNull

                    onPostgresChange(
                        PostgresChange(
                            table = table,
                            eventType = eventType,
                            awbNumber = awb,
                            newCourierId = newCourierId,
                            oldCourierId = oldCourierId
                        )
                    )
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(
                        TAG,
                        "WebSocket failure (table=$table) code=${response?.code} msg=${response?.message}",
                        t
                    )
                    scheduleReconnect()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.w(TAG, "WebSocket closed (table=$table) code=$code reason=$reason")
                    scheduleReconnect()
                }
            }
        )
    }

    private fun scheduleReconnect() {
        if (!isRunning) return
        val scope = lastScope ?: return
        if (reconnectJob?.isActive == true) return

        reconnectAttempt += 1
        val delayMs = minOf(30_000L, 1_000L * (1L shl (reconnectAttempt - 1).coerceAtMost(5)))

        reconnectJob = scope.launch(Dispatchers.IO) {
            delay(delayMs)
            if (!isRunning) return@launch

            try {
                webSocket?.close(1000, "reconnect")
            } catch (_: Exception) {
            }
            webSocket = null

            connect()
        }
    }

    private fun buildMessage(topic: String, event: String, payload: JsonObject): String {
        val ref = refCounter.incrementAndGet().toString()
        val msg = buildString {
            append("{")
            append("\"topic\":\"").append(escape(topic)).append("\",")
            append("\"event\":\"").append(escape(event)).append("\",")
            append("\"payload\":").append(payload.toString()).append(",")
            append("\"ref\":\"").append(ref).append("\"")
            append("}")
        }
        return msg
    }

    private fun emptyJsonObject(): JsonObject = JsonObject(emptyMap())

    private fun buildJoinPayload(table: String, filter: String?, accessToken: String?): JsonObject {
        return buildJoinPayload(table = table, filter = filter, events = listOf("*"), accessToken = accessToken)
    }

    private fun buildJoinPayload(table: String, filter: String?, events: List<String>, accessToken: String?): JsonObject {
        val filterValue = filter?.trim().orEmpty()
        val normalizedEvents = (events.ifEmpty { listOf("*") }).map { it.trim().ifEmpty { "*" } }
        val changesArray = normalizedEvents.joinToString(prefix = "[", postfix = "]") { event ->
            buildString {
                append("{")
                append("\"event\":\"").append(escape(event.uppercase())).append("\",")
                append("\"schema\":\"public\",")
                append("\"table\":\"").append(escape(table)).append("\"")
                if (filterValue.isNotEmpty()) {
                    append(",\"filter\":\"").append(escape(filterValue)).append("\"")
                }
                append("}")
            }
        }

        val config = buildString {
            append("{")
            append("\"broadcast\":{\"ack\":false},")
            append("\"presence\":{\"key\":\"\"},")
            append("\"postgres_changes\":").append(changesArray)
            append("}")
        }

        return if (!accessToken.isNullOrBlank()) {
            json.parseToJsonElement("{\"config\":$config,\"access_token\":\"${escape(accessToken)}\"}").jsonObject
        } else {
            json.parseToJsonElement("{\"config\":$config}").jsonObject
        }
    }

    private fun buildRealtimeWebSocketUrl(baseUrl: String, apiKey: String): String {
        val trimmed = baseUrl.trimEnd('/')
        val wsBase = when {
            trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
            trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
            trimmed.startsWith("wss://") || trimmed.startsWith("ws://") -> trimmed
            else -> "wss://$trimmed"
        }
        return "$wsBase/realtime/v1/websocket?apikey=$apiKey&vsn=1.0.0"
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
