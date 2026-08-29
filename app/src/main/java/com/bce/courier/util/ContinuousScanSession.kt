package com.bce.courier.util

class ContinuousScanSession {
    private val successfulAwbs = linkedSetOf<String>()

    fun contains(awb: String): Boolean = successfulAwbs.contains(awb)

    fun markSuccessful(awb: String) {
        successfulAwbs.add(awb)
    }

    fun clear() {
        successfulAwbs.clear()
    }
}
