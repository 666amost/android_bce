package com.bce.courier.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class CourierMotionOrigin { Profile, Scan, Search, Update }
enum class CourierPanel { Hidden, Profile, ScanMenu, Update, Scanner }
typealias CloseMotionPanel = (() -> Unit) -> Unit

@Stable
class CourierMotionState {
    var origin by mutableStateOf(CourierMotionOrigin.Scan)
        private set
    var isOpen by mutableStateOf(false)
        internal set

    fun open(source: CourierMotionOrigin) {
        origin = source
        isOpen = true
    }
}

private val LocalMotion = staticCompositionLocalOf<CourierMotionState?> { null }
@OptIn(ExperimentalSharedTransitionApi::class)
private val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
private val LocalPanelScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }
@OptIn(ExperimentalSharedTransitionApi::class)
private val ContainerBounds = BoundsTransform { _, _ -> tween(280, easing = FastOutSlowInEasing) }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CourierMotionLayout(state: CourierMotionState, content: @Composable () -> Unit) {
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalMotion provides state, LocalSharedScope provides this) {
            content()
        }
    }
}

/** Keep the button's layout slot; animate the whole button and panel as one container. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CourierMotionSource(
    origin: CourierMotionOrigin,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val shared = LocalSharedScope.current
    val motion = LocalMotion.current
    Box(modifier) {
        if (shared == null || motion == null) {
            content(Modifier)
        } else {
            AnimatedVisibility(
                visible = !motion.isOpen || motion.origin != origin,
                modifier = Modifier.fillMaxSize(),
                enter = EnterTransition.None,
                exit = ExitTransition.None,
                label = "Source-$origin",
            ) {
                with(shared) {
                    content(Modifier.sharedBounds(
                        rememberSharedContentState("panel-$origin"),
                        animatedVisibilityScope = this@AnimatedVisibility,
                        boundsTransform = ContainerBounds,
                        // Restore the button only when the returning panel reaches its slot.
                        // Otherwise ScaleToBounds magnifies the icon across the closing panel.
                        enter = fadeIn(tween(80, delayMillis = 200, easing = FastOutSlowInEasing)),
                        exit = fadeOut(tween(80, easing = FastOutSlowInEasing)),
                        resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(ContentScale.Fit),
                    ))
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.panelBounds(): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val scope = LocalPanelScope.current ?: return this
    val motion = LocalMotion.current ?: return this
    return with(shared) {
        sharedBounds(
            rememberSharedContentState("panel-${motion.origin}"), scope,
            boundsTransform = ContainerBounds,
            enter = fadeIn(tween(140, easing = FastOutSlowInEasing)),
            exit = fadeOut(tween(80, delayMillis = 200, easing = FastOutSlowInEasing)),
            // Keep the complete panel visible without cropping text or remeasuring the camera.
            resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(ContentScale.Fit),
        )
    }
}

/** A single activity layout lets Compose match the real elements in both directions. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CourierMotionPanel(
    panel: CourierPanel,
    onDismiss: () -> Unit,
    dismissEnabled: Boolean = true,
    onBack: (() -> Unit)? = null,
    compactContent: @Composable (CloseMotionPanel) -> Unit = {},
    content: @Composable (ready: Boolean, close: CloseMotionPanel) -> Unit,
) {
    val motion = checkNotNull(LocalMotion.current)
    val shared = checkNotNull(LocalSharedScope.current)
    var closing by remember { mutableStateOf(false) }
    var completion by remember { mutableStateOf<() -> Unit>({}) }
    var didComplete by remember { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val close: CloseMotionPanel = { action ->
        if (!closing) {
            completion = action
            closing = true
            motion.isOpen = false
        }
    }
    BackHandler {
        if (dismissEnabled) {
            if (onBack != null) onBack() else close {}
        }
    }
    LaunchedEffect(panel, closing) {
        if (closing || panel == CourierPanel.Scanner || panel == CourierPanel.Profile) {
            focus.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    DisposableEffect(Unit) { onDispose { motion.isOpen = false } }
    val state = remember { MutableTransitionState(CourierPanel.Hidden) }
    state.targetState = if (closing) CourierPanel.Hidden else panel
    val transition = updateTransition(state, label = "CourierPanel")
    val sharedMoving = shared.isTransitionActive
    val ready = state.isIdle && state.currentState == CourierPanel.Scanner && !sharedMoving && !closing
    LaunchedEffect(closing, state.isIdle, sharedMoving) {
        if (closing && state.isIdle && !sharedMoving && state.currentState == CourierPanel.Hidden && !didComplete) {
            didComplete = true
            dismiss()
            completion()
        }
    }
    val scrim by transition.animateFloat(label = "PanelScrim", transitionSpec = { tween(280, easing = FastOutSlowInEasing) }) {
        if (it == CourierPanel.Hidden) 0f else 0.32f
    }
    val holder = rememberSaveableStateHolder()
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val maximumCardHeight = (maxHeight - 32.dp).coerceAtLeast(0.dp)
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = scrim }.background(Color.Black)
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    if (dismissEnabled) close {}
                },
        )
        transition.AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
        ) { frame ->
            CompositionLocalProvider(LocalPanelScope provides this@AnimatedContent) {
                holder.SaveableStateProvider(frame) {
                    when (frame) {
                        CourierPanel.Hidden -> Box(Modifier.fillMaxSize())
                        CourierPanel.Profile, CourierPanel.Scanner -> {
                            Box(
                                Modifier.fillMaxSize().panelBounds()
                                    .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                            ) { content(ready, close) }
                        }
                        else -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Surface(
                                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
                                    .heightIn(max = maximumCardHeight).panelBounds()
                                    .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                                shape = RoundedCornerShape(24.dp),
                            ) {
                                Column(Modifier.verticalScroll(rememberScrollState())) { compactContent(close) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CourierAvatar(userName: String, size: Dp, modifier: Modifier = Modifier) {
    val initials = userName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        .take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "K" }
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, 0.16f),
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(initials, fontSize = (size.value * 0.375f).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

