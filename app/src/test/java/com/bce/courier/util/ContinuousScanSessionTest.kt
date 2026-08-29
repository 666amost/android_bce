package com.bce.courier.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousScanSessionTest {
    @Test
    fun repeatedSuccessfulAwbNeedsOnlyOneRequestPerSession() {
        val session = ContinuousScanSession()
        var databaseRequests = 0

        repeat(3) {
            if (!session.contains("BCE123")) {
                databaseRequests++
                session.markSuccessful("BCE123")
            }
        }

        assertEquals(1, databaseRequests)
        assertTrue(session.contains("BCE123"))

        session.clear()
        assertFalse(session.contains("BCE123"))
    }
}
