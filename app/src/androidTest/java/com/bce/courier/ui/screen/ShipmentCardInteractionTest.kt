package com.bce.courier.ui.screen

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bce.courier.data.model.ShipmentItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ShipmentCardInteractionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun assignmentCardOnlyOpensUpdateFromExplicitButton() {
        var updateClicks = 0
        composeRule.setContent {
            MaterialTheme {
                ShipmentCard(
                    shipment = ShipmentItem(
                        awb = "BCE123",
                        recipient_name = "Penerima",
                        address = "Pontianak",
                        current_status = "out_for_delivery",
                    ),
                    courierName = "Kurir",
                    onClick = { updateClicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("BCE123")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        composeRule.runOnIdle { assertEquals(0, updateClicks) }

        composeRule.onNodeWithText("Update").assertHasClickAction().performClick()
        composeRule.runOnIdle { assertEquals(1, updateClicks) }
    }
}
