package com.bce.courier.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeScanSafetyGateTest {

    @Test
    fun requiresTwoStableFrames() {
        val gate = BarcodeScanSafetyGate()

        assertFalse(gate.observe("BCE123", now = 1_000L))
        assertTrue(gate.observe("BCE123", now = 1_200L))
    }

    @Test
    fun differentBarcodeResetsStableFrameCount() {
        val gate = BarcodeScanSafetyGate()

        assertFalse(gate.observe("BCE123", now = 1_000L))
        assertFalse(gate.observe("BCE456", now = 1_100L))
        assertTrue(gate.observe("BCE456", now = 1_200L))
    }

    @Test
    fun appliesCooldownPerAwb() {
        val gate = BarcodeScanSafetyGate(perValueCooldownMs = 2_500L)

        assertFalse(gate.observe("BCE123", now = 1_000L))
        assertTrue(gate.observe("BCE123", now = 1_100L))
        assertFalse(gate.observe("BCE123", now = 2_000L))
        assertFalse(gate.observe("BCE123", now = 3_700L))
        assertTrue(gate.observe("BCE123", now = 3_800L))
    }
}
