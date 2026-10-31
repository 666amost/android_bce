package com.bce.courier.tracking

internal class CourierMqttBroker(
    val id: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    topicPrefix: String = "",
) {
    private val root = topicPrefix.trim().trim('/')
    val locationTopicPrefix = listOf(root, "v1/courier/location")
        .filter { it.isNotBlank() }
        .joinToString("/") + "/"
    val nearbyTopic = "${locationTopicPrefix}+"
    val isConfigured: Boolean
        get() = host.isNotBlank() && port in 1..65535 &&
            username.isNotBlank() && password.isNotBlank() &&
            root.none { it == '+' || it == '#' }

    fun locationTopic(courierId: String): String = locationTopicPrefix + courierId

    fun courierIdFromTopic(topic: String): String? = topic
        .takeIf { it.startsWith(locationTopicPrefix) }
        ?.removePrefix(locationTopicPrefix)
        ?.takeIf { it.isNotBlank() && '/' !in it }
}
