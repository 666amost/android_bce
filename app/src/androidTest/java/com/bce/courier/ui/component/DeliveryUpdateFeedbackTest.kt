package com.bce.courier.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bce.courier.data.model.DeliveryStatus
import com.bce.courier.data.model.DeliveryUpdateOutcome
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DeliveryUpdateFeedbackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun savingWaitsForDurableSuccessThenReturnsOnlyOnce() {
        val result = mutableStateOf<DeliveryCompletion?>(null)
        val finished = AtomicInteger()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                DeliveryUpdateFeedback(true, result.value, "BCE172694JKT", { finished.incrementAndGet() })
            }
        }
        compose.mainClock.advanceTimeBy(3000)
        compose.onNodeWithText("Menyimpan...").assertIsDisplayed()
        compose.onNodeWithText("Paket terkirim!").assertDoesNotExist()
        assertEquals(0, finished.get())

        compose.runOnIdle {
            result.value = DeliveryCompletion(DeliveryStatus.DELIVERED, DeliveryUpdateOutcome.QUEUED)
        }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithText("Paket terkirim!").assertIsDisplayed()
        compose.onNodeWithText("Syncing to cloud").assertIsDisplayed()
        compose.onNodeWithText("Cloud synced").assertDoesNotExist()
        assertEquals(0, finished.get())
        compose.mainClock.advanceTimeBy(2500)
        compose.waitUntil(5000) { finished.get() == 1 }
        compose.mainClock.advanceTimeBy(3000)
        assertEquals(1, finished.get())
    }

    @Test
    fun continueAndAutomaticReturnCannotNavigateTwice() {
        val finished = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                DeliveryUpdateFeedback(
                    false, DeliveryCompletion(DeliveryStatus.DELIVERED, DeliveryUpdateOutcome.UPDATED),
                    "BCE172694JKT", { finished.incrementAndGet() },
                )
            }
        }
        compose.onNodeWithText("Cloud synced").assertIsDisplayed()
        compose.onNodeWithText("Syncing to cloud").assertDoesNotExist()
        compose.onNodeWithText("Lanjut").performClick()
        compose.onNodeWithText("Lanjut").performClick()
        compose.mainClock.advanceTimeBy(3000)
        assertEquals(1, finished.get())
    }

    @Test
    fun pendingUpdateDoesNotClaimTheParcelWasDelivered() {
        compose.setContent {
            MaterialTheme {
                DeliveryUpdateFeedback(
                    false, DeliveryCompletion(DeliveryStatus.PENDING, DeliveryUpdateOutcome.QUEUED),
                    "BCE172694JKT", {},
                )
            }
        }
        compose.onNodeWithText("Status diperbarui").assertIsDisplayed()
        compose.onNodeWithText("Paket terkirim!").assertDoesNotExist()
    }
}
