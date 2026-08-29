package com.bce.courier.tracking

import com.gojek.courier.QoS
import com.gojek.courier.annotation.Callback
import com.gojek.courier.annotation.Data
import com.gojek.courier.annotation.Path
import com.gojek.courier.annotation.Send
import com.gojek.courier.callback.SendMessageCallback

internal interface CourierLocationPublisher {
    @Send(topic = "v1/courier/location/{courierId}", qos = QoS.ZERO)
    fun sendLocation(
        @Path("courierId") courierId: String,
        @Data location: CourierLocationMessage,
        @Callback callback: SendMessageCallback
    )
}

