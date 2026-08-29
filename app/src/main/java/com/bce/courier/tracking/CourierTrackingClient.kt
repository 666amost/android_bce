package com.bce.courier.tracking

import android.content.Context
import com.gojek.alarm.pingsender.AlarmPingSenderConfig
import com.gojek.alarm.pingsender.AlarmPingSenderFactory
import com.gojek.courier.Courier
import com.gojek.courier.Message
import com.gojek.courier.QoS
import com.gojek.courier.callback.SendMessageCallback
import com.gojek.courier.messageadapter.gson.GsonMessageAdapterFactory
import com.gojek.mqtt.auth.Authenticator
import com.gojek.mqtt.client.listener.MessageListener
import com.gojek.mqtt.client.config.ExperimentConfigs
import com.gojek.mqtt.client.config.PersistenceOptions.PahoPersistenceOptions
import com.gojek.mqtt.client.config.v3.MqttV3Configuration
import com.gojek.mqtt.client.factory.MqttClientFactory
import com.gojek.mqtt.client.model.ConnectionState
import com.gojek.mqtt.event.EventHandler
import com.gojek.mqtt.event.MqttEvent
import com.gojek.mqtt.model.MqttConnectOptions
import kotlinx.serialization.json.Json

internal class CourierTrackingClient(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    @Volatile
    private var nearbySubscriptionRequested = false
    @Volatile
    private var nearbySubscribed = false

    private val mqttClient = MqttClientFactory.create(
        appContext,
        MqttV3Configuration(
            authenticator = object : Authenticator {
                override fun authenticate(
                    connectOptions: MqttConnectOptions,
                    forceRefresh: Boolean
                ): MqttConnectOptions = connectOptions
            },
            pingSender = AlarmPingSenderFactory.createMqttPingSender(
                appContext,
                AlarmPingSenderConfig(sendForcePing = true)
            ),
            experimentConfigs = ExperimentConfigs(
                inactivityTimeoutSeconds = 120,
                connectPacketTimeoutSeconds = 10,
                maxInflightMessagesLimit = 128
            ),
            persistenceOptions = PahoPersistenceOptions(
                bufferCapacity = 512,
                isDeleteOldestMessages = true
            )
        )
    )

    private val publisher = Courier(
        Courier.Configuration(
            client = mqttClient,
            messageAdapterFactories = listOf(GsonMessageAdapterFactory())
        )
    ).create<CourierLocationPublisher>()

    private val nearbyMessageListener = object : MessageListener {
        override fun onMessageReceived(message: com.gojek.mqtt.client.model.MqttMessage) {
            if (!nearbySubscriptionRequested || !message.topic.startsWith(LOCATION_TOPIC_PREFIX)) return
            val bytes = (message.message as? Message.Bytes)?.value ?: return
            val payload = runCatching {
                json.decodeFromString(
                    CourierLocationMessage.serializer(),
                    bytes.toString(Charsets.UTF_8)
                )
            }.getOrNull() ?: return
            val topicCourierId = message.topic
                .removePrefix(LOCATION_TOPIC_PREFIX)
                .substringBefore("/")
                .takeIf { it.isNotBlank() }
            NearbyCourierStore.onLocation(payload, topicCourierId)
        }
    }

    private val mqttEventHandler = object : EventHandler {
        override fun onEvent(mqttEvent: MqttEvent) {
            when (mqttEvent) {
                is MqttEvent.MqttConnectSuccessEvent -> subscribeNearbyIfPossible()
                is MqttEvent.MqttSubscribeSuccessEvent -> {
                    if (mqttEvent.topics.containsKey(CourierTrackingConfig.nearbyTopic)) {
                        nearbySubscribed = true
                        NearbyCourierStore.markSubscribed()
                    }
                }
                is MqttEvent.MqttSubscribeFailureEvent -> {
                    if (mqttEvent.topics.containsKey(CourierTrackingConfig.nearbyTopic)) {
                        nearbySubscribed = false
                        NearbyCourierStore.markSubscriptionError(
                            mqttEvent.exception.message ?: "Gagal membaca lokasi kurir"
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    init {
        mqttClient.addGlobalMessageListener(nearbyMessageListener)
        mqttClient.addEventHandler(mqttEventHandler)
    }

    fun connect(courierId: String) {
        val connectOptions = CourierTrackingConfig.createConnectOptions(appContext, courierId)
        if (mqttClient.getCurrentState() == ConnectionState.CONNECTED) {
            return
        }
        mqttClient.connect(connectOptions)
    }

    fun reconnectIfNeeded(courierId: String) {
        when (mqttClient.getCurrentState()) {
            ConnectionState.CONNECTED, ConnectionState.CONNECTING -> Unit
            else -> connect(courierId)
        }
    }

    fun publish(
        courierId: String,
        location: CourierLocationMessage,
        callback: SendMessageCallback
    ): Boolean {
        if (mqttClient.getCurrentState() != ConnectionState.CONNECTED) {
            reconnectIfNeeded(courierId)
            return false
        }

        return runCatching {
            publisher.sendLocation(courierId, location, callback)
            true
        }.getOrDefault(false)
    }

    fun startNearbySubscription(courierId: String) {
        nearbySubscriptionRequested = true
        reconnectIfNeeded(courierId)
        subscribeNearbyIfPossible()
    }

    fun stopNearbySubscription() {
        nearbySubscriptionRequested = false
        if (nearbySubscribed && mqttClient.getCurrentState() == ConnectionState.CONNECTED) {
            runCatching { mqttClient.unsubscribe(CourierTrackingConfig.nearbyTopic) }
        }
        nearbySubscribed = false
    }

    private fun subscribeNearbyIfPossible() {
        if (!nearbySubscriptionRequested || nearbySubscribed) return
        if (mqttClient.getCurrentState() != ConnectionState.CONNECTED) return
        runCatching {
            mqttClient.subscribe(CourierTrackingConfig.nearbyTopic to QoS.ZERO)
        }.onFailure {
            NearbyCourierStore.markSubscriptionError(
                it.message ?: "Gagal membaca lokasi kurir"
            )
        }
    }

    fun disconnect(clearState: Boolean = false) {
        stopNearbySubscription()
        mqttClient.disconnect(clearState)
    }

    fun currentState(): ConnectionState = mqttClient.getCurrentState()

    private companion object {
        const val LOCATION_TOPIC_PREFIX = "v1/courier/location/"
    }
}

