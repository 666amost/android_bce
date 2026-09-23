package com.bce.courier.util

internal class BarcodeScanSafetyGate(
    private val stableFrameWindowMs: Long = 700L,
    private val perValueCooldownMs: Long = 2_500L,
) {
    private var candidate: String? = null
    private var consecutiveFrames: Int = 0
    private var lastSeenAt: Long = 0L
    private val lastEmittedAt = mutableMapOf<String, Long>()

    fun observe(value: String, now: Long): Boolean {
        if (candidate == value && now - lastSeenAt <= stableFrameWindowMs) {
            consecutiveFrames += 1
        } else {
            candidate = value
            consecutiveFrames = 1
        }
        lastSeenAt = now

        val previousEmission = lastEmittedAt[value]
        val canEmit = consecutiveFrames >= 2 &&
            (previousEmission == null || now - previousEmission >= perValueCooldownMs)
        if (canEmit) {
            lastEmittedAt[value] = now
            lastEmittedAt.entries.removeAll { now - it.value > 60_000L }
        }
        return canEmit
    }

    fun resetCandidate() {
        candidate = null
        consecutiveFrames = 0
        lastSeenAt = 0L
    }
}
