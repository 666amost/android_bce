package com.bce.courier.ui.screen

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.AnnotatedString
import com.bce.courier.data.local.PickupDraftStore
import com.bce.courier.data.model.PickupFormState
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PickupFormResetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val courierId = "pickup-reset-${UUID.randomUUID()}"
    private lateinit var drafts: PickupDraftStore
    private val draft = PickupFormState(
        awbNo = "BCE123456JKT", kotaTujuan = "bangka", wilayah = "Koba",
        agentCustomer = "UDR CASH", namaPengirim = "Pengirim Lama", nomorPengirim = "0811",
        namaPenerima = "Penerima Lama", nomorPenerima = "0822", alamatPenerima = "Alamat Lama",
        isiBarang = "Pakaian",
    )

    @Before
    fun prepareDraft() {
        drafts = PickupDraftStore(compose.activity, courierId)
        drafts.save(draft)
    }

    @After
    fun clearDraft() { drafts.clear() }

    @Test
    fun manualResetRequiresConfirmationAndClearsTheDraft() {
        compose.setContent {
            MaterialTheme {
                PickupScreen(
                    onBack = {}, courierId = courierId,
                    onSubmit = {},
                )
            }
        }
        compose.onNodeWithText("Reset form").performScrollTo().performClick()
        compose.onNodeWithText("Batal").performClick()
        assertField("Nama Pengirim", draft.namaPengirim)
        compose.onNodeWithText("Reset form").performScrollTo().performClick()
        compose.onNodeWithText("Reset").performClick()
        compose.runOnIdle {
            assertEquals("", drafts.load().namaPengirim)
            assertEquals("", drafts.load().namaPenerima)
            assertEquals("", drafts.load().awbNo)
        }
        assertField("Nama Pengirim", "")
        assertField("Nama Penerima", "")
    }

    @Test
    fun failedSavePreservesNamesAndTheAwbForRetry() {
        val calls = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                PickupScreen(onBack = {}, courierId = courierId, onSubmit = { calls.incrementAndGet() })
            }
        }
        compose.onNodeWithText("Simpan AWB").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, calls.get())
            val saved = drafts.load()
            assertEquals(draft.namaPengirim, saved.namaPengirim)
            assertEquals(draft.namaPenerima, saved.namaPenerima)
            assertEquals(draft.awbNo, saved.awbNo)
        }
        assertField("Nama Pengirim", draft.namaPengirim)
    }

    @Test
    fun leavingAnUnfinishedFormPreservesItsNamesWhenReopened() {
        val showing = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (showing.value) PickupScreen(onBack = {}, onSubmit = {}, courierId = courierId)
            }
        }
        assertField("Nama Pengirim", draft.namaPengirim)
        compose.runOnIdle { showing.value = false }
        compose.runOnIdle { showing.value = true }
        assertField("Nama Pengirim", draft.namaPengirim)
        assertField("Nama Penerima", draft.namaPenerima)
    }

    private fun assertField(label: String, expected: String) {
        compose.onNode(hasText(label) and hasSetTextAction()).performScrollTo().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(expected)),
        )
    }
}
