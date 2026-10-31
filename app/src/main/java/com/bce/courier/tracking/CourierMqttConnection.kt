package com.bce.courier.tracking

import android.content.Context
import android.util.Log
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

internal class CourierMqttConnection(
    context: Context,
    private val broker: CourierMqttBroker,
    private val onSubscriptionChanged: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    @Volatile
    private var nearbySubscriptionRequested = false
    @Volatile
    var nearbySubscribed = false
        private set
    @Volatile
    var nearbyError: String? = null
        private set
    @Volatile
    private var nearbySubscribePending = false

    private val mqttClient = MqttClientFactory.create(
        appContext,
        MqttV3Configuration(
            authenticator = object : Authenticator {
                override fun authenticate(
                    connectOptions: MqttConnectOptions,
                    forceRefresh: Boolean
                ): MqttConnectOptions = connectOptions
            },
            pingSender = CourierAlarmPingSender(appContext, broker.id),
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
        override fun onMessageReceived(mqttMessage: com.gojek.mqtt.client.model.MqttMessage) {
            if (!nearbySubscriptionRequested) return
            val topicCourierId = broker.courierIdFromTopic(mqttMessage.topic) ?: return
            val bytes = (mqttMessage.message as? Message.Bytes)?.value ?: return
            val payload = runCatching {
                json.decodeFromString(
                    CourierLocationMessage.serializer(),
                    bytes.toString(Charsets.UTF_8)
                )
            }.getOrNull() ?: return
            NearbyCourierStore.onLocation(payload, topicCourierId)
        }
    }

    private val mqttEventHandler = object : EventHandler {
        override fun onEvent(mqttEvent: MqttEvent) {
            when (mqttEvent) {
                is MqttEvent.MqttConnectSuccessEvent -> {
                    Log.i(TAG, "${broker.id}: connected to ${broker.host}:${broker.port}")
                    resetNearbySubscription()
                    subscribeNearbyIfPossible()
                }
                is MqttEvent.MqttConnectionLostEvent,
                is MqttEvent.MqttDisconnectCompleteEvent,
                is MqttEvent.MqttConnectFailureEvent -> {
                    Log.w(TAG, "${broker.id}: connection unavailable")
                    resetNearbySubscription()
                }
                is MqttEvent.MqttSubscribeSuccessEvent -> {
                    if (nearbySubscriptionRequested && mqttEvent.topics.containsKey(broker.nearbyTopic)) {
                        nearbySubscribePending = false
                        nearbySubscribed = true
                        nearbyError = null
                        onSubscriptionChanged()
                    }
                }
                is MqttEvent.MqttSubscribeFailureEvent -> {
                    if (nearbySubscriptionRequested && mqttEvent.topics.containsKey(broker.nearbyTopic)) {
                        nearbySubscribePending = false
                        nearbySubscribed = false
                        nearbyError = "${broker.id}: gagal membaca lokasi kurir"
                        onSubscriptionChanged()
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
        val connectOptions = CourierTrackingConfig.createConnectOptions(appContext, courierId, broker)
        if (mqttClient.getCurrentState() == ConnectionState.CONNECTED) {
            return
        }
        runCatching { mqttClient.connect(connectOptions) }
            .onFailure { Log.w(TAG, "${broker.id}: could not start connection") }
    }

    fun reconnectIfNeeded(courierId: String) {
        when (mqttClient.getCurrentState()) {
            ConnectionState.CONNECTED -> subscribeNearbyIfPossible()
            ConnectionState.CONNECTING -> Unit
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
            publisher.sendLocation(broker.locationTopic(courierId), location, callback)
            true
        }.getOrElse {
            callback.onMessageSendFailure(it)
            false
        }
    }

    fun startNearbySubscription(courierId: String) {
        nearbySubscriptionRequested = true
        reconnectIfNeeded(courierId)
        subscribeNearbyIfPossible()
    }

    fun stopNearbySubscription() {
        nearbySubscriptionRequested = false
        if ((nearbySubscribed || nearbySubscribePending) && mqttClient.getCurrentState() == ConnectionState.CONNECTED) {
            runCatching { mqttClient.unsubscribe(broker.nearbyTopic) }
        }
        resetNearbySubscription()
    }

    private fun subscribeNearbyIfPossible() {
        if (!nearbySubscriptionRequested || nearbySubscribed || nearbySubscribePending) return
        if (mqttClient.getCurrentState() != ConnectionState.CONNECTED) return
        nearbySubscribePending = true
        runCatching {
            mqttClient.subscribe(broker.nearbyTopic to QoS.ZERO)
        }.onFailure {
            nearbySubscribePending = false
            nearbyError = "${broker.id}: gagal membaca lokasi kurir"
            onSubscriptionChanged()
        }
    }

    private fun resetNearbySubscription() {
        nearbySubscribed = false
        nearbySubscribePending = false
        nearbyError = null
        onSubscriptionChanged()
    }

    fun disconnect(clearState: Boolean = false) {
        stopNearbySubscription()
        runCatching { mqttClient.disconnect(clearState) }
            .onFailure { Log.w(TAG, "${broker.id}: could not disconnect") }
    }

    fun currentState(): ConnectionState = mqttClient.getCurrentState()

    private companion object {
        const val TAG = "CourierMQTT"
    }
}

