package com.bce.courier.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.sp

enum class CourierMotionOrigin { Profile, Scan, Search, Update }
enum class CourierPanel { Hidden, Profile, ScanMenu, Update, Scanner }
typealias CloseMotionPanel = (() -> Unit) -> Unit

internal data class MotionSource(val bounds: Rect, val layer: GraphicsLayer, val restBounds: Rect = bounds)

@Stable
class CourierMotionState {
    var origin by mutableStateOf(CourierMotionOrigin.Scan)
        private set
    var isOpen by mutableStateOf(false)
        internal set
    internal var overlayReady by mutableStateOf(false)
    internal var openId = 0
        private set
    private val sources = mutableMapOf<CourierMotionOrigin, MotionSource>()

    internal fun recordSource(origin: CourierMotionOrigin, source: MotionSource, imeVisible: Boolean) {
        sources[origin] = source.copy(restBounds = if (imeVisible) sources[origin]?.restBounds ?: source.bounds else source.bounds)
    }

    internal fun source(): MotionSource? = sources[origin]

    fun open(source: CourierMotionOrigin) {
        if (isOpen) return
        origin = source
        overlayReady = false
        openId++
        isOpen = true
    }
}

internal val LocalMotion = staticCompositionLocalOf<CourierMotionState?> { null }

/** The dashboard stays outside lookahead measurement while a panel moves. */
@Composable
fun CourierMotionLayout(state: CourierMotionState, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMotion provides state) {
        Box(Modifier.fillMaxSize()) { content() }
    }
}

/** Keep the source measured, including while the keyboard or window size changes. */
@Composable
fun CourierMotionSource(
    origin: CourierMotionOrigin,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val motion = LocalMotion.current
    val layer = rememberGraphicsLayer()
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val hidden = motion != null && motion.isOpen && motion.overlayReady && motion.origin == origin
    Box(modifier) {
        content(Modifier
            .onGloballyPositioned { coordinates ->
                motion?.recordSource(origin, MotionSource(Rect(coordinates.positionInRoot(), coordinates.size.toSize()), layer), ime.getBottom(density) > 0)
            }
            .then(if (hidden) Modifier.clearAndSetSemantics {} else Modifier)
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                if (!hidden) drawLayer(layer)
            })
    }
}

@Composable
fun CourierMotionPanel(
    panel: CourierPanel,
    onDismiss: () -> Unit,
    dismissEnabled: Boolean = true,
    onBack: (() -> Unit)? = null,
    compactContent: @Composable (CloseMotionPanel) -> Unit = {},
    content: @Composable (ready: Boolean, close: CloseMotionPanel) -> Unit,
) {
    CourierPanelLayer(panel, onDismiss, dismissEnabled, onBack, compactContent, content)
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

