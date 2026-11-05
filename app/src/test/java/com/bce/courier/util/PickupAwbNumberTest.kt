package com.bce.courier.util

import org.junit.Assert.*
import org.junit.Test

class PickupAwbNumberTest {
    @Test
    fun defaultRetainsTheOriginalSixDigitNumericFormat() {
        assertEquals("BCE172694JKT", PickupAwbNumber.generate(1_793_000_172_694L))
        assertEquals("BCE000001JKT", PickupAwbNumber.generate(1_793_000_000_001L))
    }

    @Test
    fun duplicatesAddOneLeadingZeroWithoutChangingTheOriginalDigits() {
        val once = PickupAwbNumber.withLeadingZero("BCE172694JKT")
        assertEquals("BCE0172694JKT", once)
        assertEquals("BCE00172694JKT", PickupAwbNumber.withLeadingZero(once))
    }

    @Test
    fun amountInputRemovesLeadingZerosAndRemainsEditable() {
        assertEquals("1234", normalizePickupNumber("01234", false))
        assertEquals("1234", normalizePickupNumber("0001234", false))
        assertEquals("0", normalizePickupNumber("000", false))
        assertEquals("", normalizePickupNumber("", false))
    }

    @Test
    fun weightInputPreservesDecimalsAndRejectsMalformedAmounts() {
        assertEquals("0.50", normalizePickupNumber("00,50", true))
        assertEquals("0.5", normalizePickupNumber(".5", true))
        assertEquals("0.", normalizePickupNumber("0,", true))
        assertNull(normalizePickupNumber("1.2.3", true))
        assertNull(normalizePickupNumber("1.5", false))
        assertNull(normalizePickupNumber("Rp1234", false))
    }
}
