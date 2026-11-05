package com.bce.courier.ui.component

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import com.bce.courier.debug.MotionPreviewActivity
import org.junit.Rule
import org.junit.Test

class DashboardMotionInteractionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MotionPreviewActivity>()

    @Test
    fun realDashboardButtonsOpenProfileAndQrMenuThenReturn() {
        compose.onNodeWithContentDescription("Profil & pengaturan").performClick()
        compose.onNodeWithText("Profil & pengaturan").assertIsDisplayed()
        compose.onNodeWithText("Sinkronisasi data").performClick()
        compose.onNodeWithText("Sinkronisasi pengiriman").assertIsDisplayed()
        compose.onNodeWithText("Semua pengiriman sudah tersinkron.").assertIsDisplayed()
        compose.onNodeWithText("Tutup").performClick()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithContentDescription("Scan").performClick()
        compose.onNodeWithText("Pilih Mode Scan").assertIsDisplayed()
        compose.onNodeWithText("Tutup").performClick()
        compose.onNodeWithContentDescription("Profil & pengaturan").assertIsDisplayed()
    }

    @Test
    fun realManualFormKeepsEditedAwbAfterCameraCloses() {
        compose.onNode(hasSetTextAction()).performTextReplacement("Amos")
        val buttons = compose.onAllNodesWithText("Update")
        val index = buttons.fetchSemanticsNodes().withIndex().maxBy { it.value.boundsInRoot.bottom }.index
        buttons[index].performClick()
        compose.onNodeWithText("Bulk Update Manual").assertIsDisplayed()
        compose.onNode(hasText("BCE172694JKT") and hasSetTextAction()).performTextReplacement("BCE0172694JKT")
        compose.onNodeWithText("Scan QR").performClick()
        compose.onNodeWithText("Pratinjau scan QR").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNode(hasText("BCE0172694JKT") and hasSetTextAction()).assertIsDisplayed()
        compose.onNodeWithText("Tutup").performClick()
        compose.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")),
        )
        compose.onNodeWithContentDescription("Scan").assertIsDisplayed()
    }
}
