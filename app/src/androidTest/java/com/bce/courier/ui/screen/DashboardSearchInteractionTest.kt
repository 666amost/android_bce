package com.bce.courier.ui.screen

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bce.courier.data.model.DeliveryStatus
import com.bce.courier.data.model.DeliveryUpdateOutcome
import com.bce.courier.data.model.ShipmentItem
import com.bce.courier.ui.component.DeliveryCompletion
import com.bce.courier.ui.component.DeliveryUpdateFeedback
import org.junit.Rule
import org.junit.Test

class DashboardSearchInteractionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val completion = mutableStateOf<DeliveryCompletion?>(null)
    private val shipments = mutableStateOf(listOf(
        ShipmentItem("BCE001", "Penerima Pertama", address = "Jakarta", current_status = DeliveryStatus.OUT_FOR_DELIVERY),
        ShipmentItem("BCE002", "Penerima Kedua", address = "Jakarta", current_status = DeliveryStatus.OUT_FOR_DELIVERY),
    ))

    @Test
    fun returningFromTheUpdatePageWithBackClearsTheAwbSearch() {
        showDashboard()
        openFilteredShipment()
        compose.onNodeWithText("Kembali tanpa update").performClick()
        assertQuery("")
        compose.onNodeWithText("Penerima Pertama").assertIsDisplayed()
    }

    @Test
    fun cloudSavedUpdateReturnsToAnEmptySearchAndTheRemainingShipment() {
        assertSuccessfulReturn(DeliveryUpdateOutcome.UPDATED)
    }

    @Test
    fun offlineQueuedUpdateAlsoReturnsToAnEmptySearchAndTheRemainingShipment() {
        assertSuccessfulReturn(DeliveryUpdateOutcome.QUEUED)
    }

    @Test
    fun refreshingDataWhileStayingOnTheDashboardKeepsTheCurrentSearch() {
        showDashboard()
        compose.onNode(hasSetTextAction()).performTextReplacement("BCE001")
        compose.runOnIdle {
            shipments.value = shipments.value.map { it.copy(updated_at = "2026-11-05T12:00:00Z") }
        }
        assertQuery("BCE001")
        compose.onNodeWithText("Penerima Pertama").assertIsDisplayed()
        compose.onNodeWithText("Penerima Kedua").assertDoesNotExist()
    }

    private fun assertSuccessfulReturn(outcome: DeliveryUpdateOutcome) {
        showDashboard()
        openFilteredShipment()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            shipments.value = shipments.value.drop(1)
            completion.value = DeliveryCompletion(DeliveryStatus.DELIVERED, outcome)
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Lanjut").performClick()
        compose.mainClock.autoAdvance = true
        assertQuery("")
        compose.onNodeWithText("Penerima Kedua").assertIsDisplayed()
        compose.onNodeWithText("Tidak ada pengiriman").assertDoesNotExist()
    }

    private fun showDashboard() {
        compose.setContent {
            MaterialTheme {
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                NavHost(nav, startDestination = "dashboard") {
                    composable("dashboard") {
                        DashboardScreen(
                            userName = "Kurir", shipments = shipments.value,
                            assignedCount = shipments.value.size, completedCount = 0, pendingCount = 0,
                            isLoading = false, selectedTab = 0,
                            isActive = entry?.destination?.route == "dashboard",
                            onTabSelected = {}, onRefresh = {}, onProfileClick = {},
                            onShipmentClick = { nav.navigate("update") }, onShipmentMapClick = {},
                            onSearchScanClick = {}, onScanMenuClick = {}, onUpdateClick = {}, onPickupClick = {},
                        )
                    }
                    composable("update") {
                        TextButton(onClick = { nav.popBackStack() }) { Text("Kembali tanpa update") }
                        DeliveryUpdateFeedback(
                            isSaving = false, completion = completion.value, awb = "BCE001",
                            onFinished = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    private fun openFilteredShipment() {
        compose.onNode(hasSetTextAction()).performTextReplacement("BCE001")
        compose.onNodeWithText("Penerima Kedua").assertDoesNotExist()
        val buttons = compose.onAllNodesWithText("Update")
        val cardButton = buttons.fetchSemanticsNodes().withIndex().minBy { it.value.boundsInRoot.top }.index
        buttons[cardButton].performClick()
        compose.onNodeWithText("Kembali tanpa update").assertIsDisplayed()
    }

    private fun assertQuery(expected: String) {
        compose.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(expected)),
        )
    }
}
