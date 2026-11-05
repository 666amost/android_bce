package com.bce.courier.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CourierMotionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun wholeContainerReturnsToEachSourceAndCompletesNavigationOnlyOnce() {
        val open = mutableStateOf(false)
        val motion = CourierMotionState()
        val events = mutableListOf<String>()
        var ready = false
        var closePanel: CloseMotionPanel = {}
        composeRule.setContent {
            MaterialTheme {
                CourierMotionLayout(motion) {
                    Box(Modifier.fillMaxSize().background(Color.White)) {
                        Row(Modifier.align(Alignment.BottomCenter)) {
                            CourierMotionOrigin.entries.forEach { origin ->
                                CourierMotionSource(origin, Modifier.size(64.dp)) { sharedModifier ->
                                    Surface(
                                        modifier = sharedModifier.fillMaxSize().testTag(origin.name),
                                        onClick = { motion.open(origin); open.value = true },
                                    ) { Text(origin.name) }
                                }
                            }
                        }
                        if (open.value) {
                            CourierMotionPanel(CourierPanel.Scanner, onDismiss = { events += "dismiss"; open.value = false }) { active, close ->
                                SideEffect { ready = active; closePanel = close }
                                Box(Modifier.fillMaxSize().background(Color.White))
                            }
                        }
                    }
                }
            }
        }
        CourierMotionOrigin.entries.forEach { origin ->
            val source = composeRule.onNodeWithTag(origin.name)
            val originalBounds = source.fetchSemanticsNode().boundsInRoot
            source.performClick()
            composeRule.runOnIdle {
                assertTrue(ready)
                assertEquals(origin, motion.origin)
                assertTrue(events.isEmpty())
                closePanel { events += "navigate" }
                closePanel { events += "duplicate" }
            }
            composeRule.runOnIdle {
                assertFalse(ready)
                assertFalse(open.value)
                assertFalse(motion.isOpen)
                assertEquals(listOf("dismiss", "navigate"), events)
            }
            assertEquals(originalBounds, source.fetchSemanticsNode().boundsInRoot)
            composeRule.runOnIdle { events.clear() }
        }
    }

    @Test
    fun sharedScannerReturnsToTheUpdateDraftBeforeClosingToDashboard() {
        val panel = mutableStateOf(CourierPanel.Update)
        val open = mutableStateOf(false)
        val draft = mutableStateOf("DRAFT")
        val motion = CourierMotionState()
        var ready = false
        var closePanel: CloseMotionPanel = {}
        composeRule.setContent {
            MaterialTheme {
                CourierMotionLayout(motion) {
                    Box(Modifier.fillMaxSize()) {
                        CourierMotionSource(CourierMotionOrigin.Update, Modifier.align(Alignment.BottomStart).width(118.dp).height(48.dp)) { sharedModifier ->
                            Surface(modifier = sharedModifier.fillMaxSize()) { Text("Update") }
                        }
                        if (open.value) {
                            CourierMotionPanel(
                                panel = panel.value,
                                onDismiss = { open.value = false },
                                compactContent = { close ->
                                    SideEffect { ready = false; closePanel = close }
                                    BulkUpdateContent(
                                        onDismiss = { close {} }, onUpdate = {}, isProcessing = false, processingStatus = "",
                                        onScanClick = { panel.value = CourierPanel.Scanner },
                                        awbText = draft.value, onAwbTextChange = { draft.value = it },
                                    )
                                },
                            ) { active, close ->
                                SideEffect { ready = active; closePanel = close }
                                Box(Modifier.fillMaxSize().background(Color.Black))
                            }
                        }
                    }
                }
            }
        }
        composeRule.runOnIdle { motion.open(CourierMotionOrigin.Update); open.value = true }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onNodeWithText("DRAFT").assertExists()
        composeRule.runOnIdle { assertFalse(ready); panel.value = CourierPanel.Scanner }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.runOnIdle { assertTrue(ready); panel.value = CourierPanel.Update }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.onNodeWithText("DRAFT").assertExists()
        composeRule.runOnIdle { assertFalse(ready); closePanel {} }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.runOnIdle { assertFalse(open.value) }
    }

    @Test
    fun cameraIsCreatedAfterEntryAndRetainedUntilThePanelFinishesClosing() {
        val open = mutableStateOf(false)
        val motion = CourierMotionState()
        var created = 0
        var disposed = 0
        var closed = 0
        var closePanel: CloseMotionPanel = {}
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                CourierMotionLayout(motion) {
                    Box(Modifier.fillMaxSize()) {
                        CourierMotionSource(CourierMotionOrigin.Scan, Modifier.align(Alignment.BottomCenter).size(56.dp)) { source ->
                            Surface(modifier = source.fillMaxSize().testTag("scan"), onClick = { motion.open(CourierMotionOrigin.Scan); open.value = true }) { Text("QR") }
                        }
                        if (open.value) {
                            CourierMotionPanel(CourierPanel.Scanner, onDismiss = { open.value = false; closed++ }) { ready, close ->
                                SideEffect { closePanel = close }
                                DeferredScannerPreview(ready) {
                                    DisposableEffect(Unit) { created++; onDispose { disposed++ } }
                                    Box(Modifier.fillMaxSize().background(Color.Black))
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("scan").performClick()
        composeRule.mainClock.advanceTimeBy(160)
        composeRule.runOnIdle { assertEquals(0, created) }
        composeRule.mainClock.advanceTimeBy(550)
        composeRule.runOnIdle {
            assertEquals(1, created)
            closePanel {}
        }
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.runOnIdle { assertEquals(0, disposed); assertEquals(0, closed) }
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.runOnIdle { assertEquals(1, disposed); assertEquals(1, closed) }
    }

    @Test
    fun closingDuringEntryNeverStartsCameraAndNavigatesOnce() {
        val open = mutableStateOf(false)
        val motion = CourierMotionState()
        val events = mutableListOf<String>()
        var created = 0
        var closePanel: CloseMotionPanel = {}
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                CourierMotionLayout(motion) {
                    Box(Modifier.fillMaxSize()) {
                        CourierMotionSource(CourierMotionOrigin.Search, Modifier.size(48.dp)) { source ->
                            Surface(modifier = source.fillMaxSize().testTag("search"), onClick = { motion.open(CourierMotionOrigin.Search); open.value = true }) { Text("Search") }
                        }
                        if (open.value) {
                            CourierMotionPanel(CourierPanel.Scanner, onDismiss = { open.value = false; events += "dismiss" }) { ready, close ->
                                SideEffect { closePanel = close }
                                DeferredScannerPreview(ready) {
                                    DisposableEffect(Unit) { created++; onDispose {} }
                                    Box(Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("search").performClick()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.runOnIdle { closePanel { events += "navigate" }; closePanel { events += "duplicate" } }
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.runOnIdle {
            assertEquals(0, created)
            assertEquals(listOf("dismiss", "navigate"), events)
            assertFalse(motion.isOpen)
        }
        composeRule.onNodeWithTag("search").assertExists()
    }

    @Test
    fun movingTheContainerDoesNotRecomposeItsContentsEveryFrame() {
        val open = mutableStateOf(false)
        val motion = CourierMotionState()
        var compositions = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                CourierMotionLayout(motion) {
                    Box(Modifier.fillMaxSize()) {
                        CourierMotionSource(CourierMotionOrigin.Profile, Modifier.size(40.dp)) { source ->
                            Surface(modifier = source.fillMaxSize().testTag("profile"), onClick = { motion.open(CourierMotionOrigin.Profile); open.value = true }) { Text("P") }
                        }
                        if (open.value) {
                            CourierMotionPanel(CourierPanel.Profile, onDismiss = { open.value = false }) { _, _ ->
                                SideEffect { compositions++ }
                                Box(Modifier.fillMaxSize()) { Text("Profil & pengaturan") }
                            }
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("profile").performClick()
        composeRule.mainClock.advanceTimeBy(100)
        val before = compositions
        composeRule.mainClock.advanceTimeBy(160)
        composeRule.runOnIdle { assertTrue("Content was recomposed for animation frames", compositions - before <= 2) }
    }

    @Test
    fun resizedManualFormKeepsItsDraftAndReturnsToTheMovedButton() {
        val height = mutableStateOf(600.dp)
        val panel = mutableStateOf(CourierPanel.Update)
        val open = mutableStateOf(false)
        val motion = CourierMotionState()
        val draft = mutableStateOf("BCE172694JKT")
        var closePanel: CloseMotionPanel = {}
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(height.value)) {
                        CourierMotionLayout(motion) {
                            Box(Modifier.fillMaxSize()) {
                                CourierMotionSource(CourierMotionOrigin.Update, Modifier.align(Alignment.BottomStart).width(118.dp).height(48.dp)) { source ->
                                    Surface(modifier = source.fillMaxSize().testTag("manual"), onClick = { motion.open(CourierMotionOrigin.Update); open.value = true }) { Text("Update") }
                                }
                                if (open.value) {
                                    CourierMotionPanel(
                                        panel.value, onDismiss = { open.value = false },
                                        compactContent = { close ->
                                            SideEffect { closePanel = close }
                                            BulkUpdateContent({}, {}, false, "", { panel.value = CourierPanel.Scanner }, draft.value, { draft.value = it })
                                        },
                                    ) { _, close ->
                                        SideEffect { closePanel = close }
                                        Box(Modifier.fillMaxSize())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val original = composeRule.onNodeWithTag("manual").fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag("manual").performClick()
        composeRule.runOnIdle { height.value = 400.dp }
        composeRule.onNodeWithText("BCE172694JKT").assertExists()
        composeRule.runOnIdle { panel.value = CourierPanel.Scanner }
        composeRule.runOnIdle { panel.value = CourierPanel.Update }
        composeRule.onNodeWithText("BCE172694JKT").assertExists()
        composeRule.runOnIdle { closePanel {} }
        composeRule.runOnIdle { assertFalse(open.value) }
        val returned = composeRule.onNodeWithTag("manual").fetchSemanticsNode().boundsInRoot
        assertEquals(original.width, returned.width)
        assertTrue(returned.top < original.top)
    }

    @Test
    fun windowResizeDuringDismissalDoesNotRestartTheClosingAnimation() {
        val height = mutableStateOf(600.dp)
        val open = mutableStateOf(false)
        val motion = CourierMotionState()
        var closePanel: CloseMotionPanel = {}
        var closed = 0
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxWidth().height(height.value)) {
                    CourierMotionLayout(motion) {
                        Box(Modifier.fillMaxSize()) {
                            CourierMotionSource(CourierMotionOrigin.Update, Modifier.align(Alignment.BottomStart).size(48.dp)) { source ->
                                Surface(modifier = source.testTag("resize"), onClick = { motion.open(CourierMotionOrigin.Update); open.value = true }) { Text("U") }
                            }
                            if (open.value) {
                                CourierMotionPanel(CourierPanel.Update, onDismiss = { closed++; open.value = false }, compactContent = { close ->
                                    SideEffect { closePanel = close }
                                    Text("Manual", Modifier.padding(24.dp))
                                }) { _, _ -> }
                            }
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag("resize").performClick()
        composeRule.mainClock.advanceTimeBy(600)
        composeRule.runOnIdle { closePanel {} }
        repeat(6) {
            composeRule.runOnIdle { height.value -= 20.dp }
            composeRule.mainClock.advanceTimeBy(32)
        }
        composeRule.mainClock.advanceTimeBy(128)
        composeRule.runOnIdle { assertEquals(1, closed); assertFalse(motion.isOpen) }
    }
}
