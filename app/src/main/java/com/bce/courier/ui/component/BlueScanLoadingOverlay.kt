package com.bce.courier.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BlueScanLoadingOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    tintColor: Color = Color(0xFF1E88E5),
    dimAlpha: Float = 0.18f,
    bandHeightFraction: Float = 0.18f,
    bandTravelFraction: Float = 0.10f
) {
    if (!visible) return

    val transition = rememberInfiniteTransition(label = "BlueScanLoadingOverlay")
    val stripeShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "StripeShift"
    )
    val bandCenter by transition.animateFloat(
        initialValue = 0.5f - (bandTravelFraction / 2f),
        targetValue = 0.5f + (bandTravelFraction / 2f),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BandCenter"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = dimAlpha))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bandHeight = size.height * bandHeightFraction
            val bandTop = (size.height * bandCenter) - (bandHeight / 2f)
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        tintColor.copy(alpha = 0.10f),
                        tintColor.copy(alpha = 0.24f),
                        tintColor.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    startY = bandTop,
                    endY = bandTop + bandHeight
                ),
                topLeft = Offset(0f, bandTop),
                size = Size(size.width, bandHeight)
            )

            val spacing = 6.dp.toPx()
            val thickness = 1.dp.toPx()
            val shiftPx = stripeShift * spacing

            var y = bandTop + shiftPx
            while (y < bandTop + bandHeight) {
                drawRect(
                    color = tintColor.copy(alpha = 0.55f),
                    topLeft = Offset(0f, y),
                    size = Size(size.width, thickness)
                )
                y += spacing
            }
        }
    }
}
