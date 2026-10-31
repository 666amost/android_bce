package com.bce.courier.tracking

import com.gojek.courier.callback.SendMessageCallback
import java.util.concurrent.atomic.AtomicBoolean

/** A location succeeds once any broker accepts it; failure requires every attempt to fail. */
internal class CourierPublishCallback(
    private var remaining: Int,
    private val callback: SendMessageCallback,
) {
    private var succeeded = false
    private val triggered = AtomicBoolean()
    private val written = AtomicBoolean()

    fun forBroker(): SendMessageCallback = object : SendMessageCallback {
        private val completed = AtomicBoolean()

        override fun onMessageSendTrigger() {
            if (triggered.compareAndSet(false, true)) callback.onMessageSendTrigger()
        }

        override fun onMessageWrittenOnSocket() {
            if (written.compareAndSet(false, true)) callback.onMessageWrittenOnSocket()
        }

        override fun onMessageSendSuccess() {
            if (completed.compareAndSet(false, true)) complete(null)
        }

        override fun onMessageSendFailure(error: Throwable) {
            if (completed.compareAndSet(false, true)) complete(error)
        }
    }

    @Synchronized
    private fun complete(error: Throwable?) {
        remaining--
        if (error == null && !succeeded) {
            succeeded = true
            callback.onMessageSendSuccess()
        } else if (error != null && remaining == 0 && !succeeded) {
            callback.onMessageSendFailure(error)
        }
    }
}
