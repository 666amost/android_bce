package com.bce.courier.tracking

import com.gojek.courier.callback.SendMessageCallback
import org.junit.Assert.assertEquals
import org.junit.Test

class CourierPublishCallbackTest {
    private class Result : SendMessageCallback {
        var successes = 0
        var failures = 0
        override fun onMessageSendTrigger() = Unit
        override fun onMessageWrittenOnSocket() = Unit
        override fun onMessageSendSuccess() { successes++ }
        override fun onMessageSendFailure(error: Throwable) { failures++ }
    }

    @Test
    fun hiveMqFailureDoesNotPreventAvishaSuccess() {
        val result = Result()
        val publish = CourierPublishCallback(2, result)
        publish.forBroker().onMessageSendFailure(IllegalStateException("HiveMQ offline"))
        assertEquals(0, result.failures)
        publish.forBroker().onMessageSendSuccess()
        assertEquals(1, result.successes)
        assertEquals(0, result.failures)
    }

    @Test
    fun laterBrokerFailureDoesNotUndoSuccess() {
        val result = Result()
        val publish = CourierPublishCallback(2, result)
        publish.forBroker().onMessageSendSuccess()
        publish.forBroker().onMessageSendFailure(IllegalStateException("offline"))
        assertEquals(1, result.successes)
        assertEquals(0, result.failures)
    }

    @Test
    fun reportsFailureOnlyAfterBothBrokersFail() {
        val result = Result()
        val publish = CourierPublishCallback(2, result)
        val first = publish.forBroker()
        val second = publish.forBroker()
        first.onMessageSendFailure(IllegalStateException("offline"))
        first.onMessageSendFailure(IllegalStateException("duplicate callback"))
        assertEquals(0, result.failures)
        second.onMessageSendFailure(IllegalStateException("offline"))
        assertEquals(1, result.failures)
        assertEquals(0, result.successes)
    }

    @Test
    fun twoSuccessfulBrokersReportOneSuccessfulLocation() {
        val result = Result()
        val publish = CourierPublishCallback(2, result)
        publish.forBroker().onMessageSendSuccess()
        publish.forBroker().onMessageSendSuccess()
        assertEquals(1, result.successes)
    }

    @Test
    fun supportsAvishaAsTheOnlyBroker() {
        val result = Result()
        CourierPublishCallback(1, result).forBroker().onMessageSendSuccess()
        assertEquals(1, result.successes)
        assertEquals(0, result.failures)
    }
}
