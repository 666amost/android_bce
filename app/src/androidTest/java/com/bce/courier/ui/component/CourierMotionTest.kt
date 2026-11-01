package com.bce.courier.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
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
}
