package com.bce.courier.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Animatable as ColorAnimatable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private data class PanelPlacement(val panel: CourierPanel, val bounds: Rect)
private val OpenEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Measure only the active destination; animate its layer without remeasuring the dashboard. */
@Composable
internal fun CourierPanelLayer(
    panel: CourierPanel,
    onDismiss: () -> Unit,
    dismissEnabled: Boolean,
    onBack: (() -> Unit)?,
    compactContent: @Composable (CloseMotionPanel) -> Unit,
    content: @Composable (Boolean, CloseMotionPanel) -> Unit,
) {
    val motion = checkNotNull(LocalMotion.current)
    val openId = remember { motion.openId }
    val dismiss by rememberUpdatedState(onDismiss)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val sourceColor = when (motion.origin) {
        CourierMotionOrigin.Profile -> MaterialTheme.colorScheme.primary
        CourierMotionOrigin.Scan, CourierMotionOrigin.Search -> MaterialTheme.colorScheme.primaryContainer
        CourierMotionOrigin.Update -> MaterialTheme.colorScheme.secondaryContainer
    }
    val sourceRadius = with(density) { if (motion.origin == CourierMotionOrigin.Update) 12.dp.toPx() else 24.dp.toPx() }
    val cardRadius = with(density) { 24.dp.toPx() }
    var closing by remember { mutableStateOf(false) }
    var completion by remember { mutableStateOf<() -> Unit>({}) }
    var didComplete by remember { mutableStateOf(false) }
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    var placement by remember { mutableStateOf<PanelPlacement?>(null) }
    var settledPlacement by remember { mutableStateOf<PanelPlacement?>(null) }
    var positioned by remember { mutableStateOf(false) }
    var previousPanel by remember { mutableStateOf(CourierPanel.Hidden) }
    var exitPlacement by remember { mutableStateOf<PanelPlacement?>(null) }
    var exitRoot by remember { mutableStateOf(Offset.Zero) }
    var exitBounds by remember { mutableStateOf<Rect?>(null) }
    val bounds = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val corner = remember { Animatable(sourceRadius) }
    val scrim = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }
    val sourceAlpha = remember { Animatable(1f) }
    val fillColor = remember { ColorAnimatable(sourceColor) }
    val compact = panel == CourierPanel.Update || panel == CourierPanel.ScanMenu
    val interactive = !closing && positioned && placement?.panel == panel && settledPlacement == placement
    val ready = !closing && panel == CourierPanel.Scanner && settledPlacement == placement && placement?.panel == panel
    val close: CloseMotionPanel = { action ->
        if (!closing) {
            completion = action
            exitPlacement = placement
            exitRoot = rootPosition
            exitBounds = motion.source()?.restBounds?.translate(-rootPosition)
            closing = true
            settledPlacement = null
        }
    }
    BackHandler {
        if (dismissEnabled && !closing) {
            if (onBack != null) onBack() else close {}
        }
    }
    LaunchedEffect(panel, closing) {
        if (closing || !compact) {
            focus.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (motion.openId == openId) {
                motion.overlayReady = false
                motion.isOpen = false
            }
        }
    }
    val animationPlacement = if (closing) exitPlacement ?: placement else placement
    val animationRoot = if (closing) exitRoot else rootPosition
    LaunchedEffect(panel, animationPlacement, closing, animationRoot) {
        val destination = animationPlacement?.takeIf { it.panel == panel && it.bounds.width > 0f && it.bounds.height > 0f }
            ?: return@LaunchedEffect
        if (closing && exitPlacement == null) {
            exitPlacement = destination
            exitRoot = rootPosition
            exitBounds = motion.source()?.restBounds?.translate(-rootPosition)
        }
        // Follow the system's IME/window animation directly once the panel is open.
        // Starting a second tween on each inset frame would make the form chase the keyboard.
        if (positioned && !closing && previousPanel == panel && settledPlacement?.panel == panel) {
            bounds.snapTo(destination.bounds)
            settledPlacement = destination
            return@LaunchedEffect
        }
        settledPlacement = null
        val source = (if (closing) exitBounds else motion.source()?.bounds?.translate(-rootPosition)) ?: Rect(
            destination.bounds.center - Offset(cardRadius, cardRadius),
            Size(cardRadius * 2f, cardRadius * 2f),
        )
        val firstOpen = !positioned
        if (firstOpen) {
            bounds.snapTo(source)
            positioned = true
            motion.overlayReady = true
        }
        if (previousPanel != panel && !closing) contentAlpha.snapTo(0f)
        previousPanel = panel
        val duration = if (closing) 220 else if (firstOpen) 320 else 260
        val easing = if (closing) FastOutLinearInEasing else OpenEasing
        coroutineScope {
            launch { scrim.animateTo(if (closing) 0f else 0.32f, tween(duration, easing = easing)) }
            launch { corner.animateTo(if (closing) sourceRadius else if (compact) cardRadius else 0f, tween(duration, easing = easing)) }
            launch { fillColor.animateTo(if (closing) sourceColor else if (panel == CourierPanel.Scanner) Color.Black else surfaceColor, tween(duration, easing = easing)) }
            launch { sourceAlpha.animateTo(if (closing) 1f else 0f, tween(80, delayMillis = if (closing) duration - 80 else 0)) }
            launch { contentAlpha.animateTo(if (closing) 0f else 1f, tween(if (closing) 80 else 170, delayMillis = if (closing) 0 else 80)) }
            bounds.animateTo(if (closing) source else destination.bounds, tween(duration, easing = easing))
        }
        if (closing && !didComplete) {
            didComplete = true
            motion.overlayReady = false
            motion.isOpen = false
            dismiss()
            completion()
        } else if (!closing) {
            settledPlacement = destination
        }
    }

    val holder = rememberSaveableStateHolder()
    Box(Modifier.fillMaxSize().onGloballyPositioned { rootPosition = it.positionInRoot() }) {
        Box(
            Modifier.matchParentSize().graphicsLayer { alpha = scrim.value }.background(Color.Black)
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    if (dismissEnabled) close {}
                },
        )
        // The keyboard only constrains the manual form, never the camera or profile destination.
        Box(
            Modifier.fillMaxSize().then(if (compact && !closing) Modifier.imePadding().padding(16.dp) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                (if (compact) Modifier.widthIn(max = 420.dp).fillMaxWidth() else Modifier.fillMaxSize())
                    .onGloballyPositioned { coordinates ->
                        placement = PanelPlacement(panel, Rect(coordinates.positionInRoot() - rootPosition, coordinates.size.toSize()))
                    },
            ) {
                Surface(
                    modifier = (if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                        .graphicsLayer {
                            val target = placement?.bounds
                            val current = bounds.value
                            if (!positioned || target == null || target.width == 0f || target.height == 0f) {
                                alpha = 0f
                            } else {
                                transformOrigin = TransformOrigin(0f, 0f)
                                scaleX = (current.width / target.width).coerceAtLeast(0.001f)
                                scaleY = (current.height / target.height).coerceAtLeast(0.001f)
                                translationX = current.left - target.left
                                translationY = current.top - target.top
                                shape = RoundedCornerShape((corner.value / minOf(scaleX, scaleY)).toDp())
                                clip = true
                            }
                        }
                        .drawWithContent {
                            drawRect(fillColor.value)
                            drawContent()
                            val source = motion.source()
                            val current = bounds.value
                            if (source != null && sourceAlpha.value > 0f && current.width > 0f && current.height > 0f) {
                                val sx = size.width / current.width
                                val sy = size.height / current.height
                                // Keep the original icon at its own size; do not magnify it across the panel.
                                scale(sx, sy, pivot = center) {
                                    translate(center.x - source.layer.size.width / 2f, center.y - source.layer.size.height / 2f) {
                                        drawIntoCanvas { canvas ->
                                            val paint = Paint().apply { alpha = sourceAlpha.value }
                                            canvas.saveLayer(Rect(Offset.Zero, source.layer.size.toSize()), paint)
                                            drawLayer(source.layer)
                                            canvas.restore()
                                        }
                                    }
                                }
                            }
                        }
                        .semantics { paneTitle = if (panel == CourierPanel.Profile) "Profil & pengaturan" else "Operasional kurir" }
                        .pointerInput(interactive) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (!interactive) event.changes.forEach { it.consume() }
                                }
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                    color = Color.Transparent,
                ) {
                    Box(Modifier.graphicsLayer { alpha = if (previousPanel == panel) contentAlpha.value else 0f }) {
                        holder.SaveableStateProvider(panel) {
                            if (compact) {
                                Column(Modifier.verticalScroll(rememberScrollState())) { compactContent(close) }
                            } else {
                                content(ready, close)
                            }
                        }
                    }
                }
            }
        }
    }
}
