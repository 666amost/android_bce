package com.bce.courier.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

enum class ScannerOverlayScanEffect { Classic, GoPay }

@Composable
fun ScannerOverlay(
    borderColor: Color = Color.White.copy(alpha = 0.5f),
    scanLineColor: Color = Color(0xFF1976D2),
    scanEffect: ScannerOverlayScanEffect = ScannerOverlayScanEffect.Classic
) {
    val introAnimation = remember { Animatable(0f) }

    val sensingPulse = rememberInfiniteTransition(label = "SensingPulse")
    val sensingAlpha by sensingPulse.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SensingAlpha"
    )

    LaunchedEffect(Unit) {
        introAnimation.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val boxWidth = 340.dp
        val boxHeight = 230.dp
        val cornerRadiusDp = 18.dp

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cornerRadius = cornerRadiusDp.toPx()
            val boxWidthPx = boxWidth.toPx() * introAnimation.value
            val boxHeightPx = boxHeight.toPx() * introAnimation.value

            val left = (size.width - boxWidthPx) / 2
            val top = (size.height - boxHeightPx) / 2
            val right = left + boxWidthPx
            val bottom = top + boxHeightPx

            val rectPath = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
            val holePath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(left, top, right, bottom),
                        cornerRadius = CornerRadius(cornerRadius)
                    )
                )
            }

            val backgroundPath = Path.combine(
                operation = androidx.compose.ui.graphics.PathOperation.Difference,
                path1 = rectPath,
                path2 = holePath
            )

            drawPath(
                path = backgroundPath,
                color = Color.Black.copy(alpha = 0.65f * introAnimation.value)
            )

            val cornerStroke = 6.dp.toPx()
            val cornerLen = 34.dp.toPx()
            val inset = 12.dp.toPx()

            val sensing = borderColor.copy(alpha = 0.90f * introAnimation.value * sensingAlpha)

            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(
                    color = sensing,
                    start = Offset(x, y),
                    end = Offset(x + dx * cornerLen, y),
                    strokeWidth = cornerStroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = sensing,
                    start = Offset(x, y),
                    end = Offset(x, y + dy * cornerLen),
                    strokeWidth = cornerStroke,
                    cap = StrokeCap.Round
                )
            }

            corner(left + inset, top + inset, 1f, 1f)
            corner(right - inset, top + inset, -1f, 1f)
            corner(left + inset, bottom - inset, 1f, -1f)
            corner(right - inset, bottom - inset, -1f, -1f)
        }

        Box(
            modifier = Modifier
                .size(width = boxWidth * introAnimation.value, height = boxHeight * introAnimation.value)
                .clip(RoundedCornerShape(cornerRadiusDp))
        ) {
            if (introAnimation.value >= 0.9f) {
                val infiniteTransition = rememberInfiniteTransition(label = "ScanLine")
                val scanDurationMs = when (scanEffect) {
                    ScannerOverlayScanEffect.GoPay -> 1300
                    ScannerOverlayScanEffect.Classic -> 1900
                }
                val rawYOffset by infiniteTransition.animateFloat(
                    initialValue = 0.06f,
                    targetValue = 0.94f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(scanDurationMs, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "LinePosition"
                )

                when (scanEffect) {
                    ScannerOverlayScanEffect.Classic -> {
                        val currentY = boxHeight * rawYOffset

                        var directionDown by remember { mutableStateOf(true) }
                        val epsilon = 0.015f
                        val tailAlpha = remember { Animatable(1f) }

                        LaunchedEffect(rawYOffset) {
                            val newDirectionDown = when {
                                rawYOffset <= (0.06f + epsilon) -> true
                                rawYOffset >= (0.94f - epsilon) -> false
                                else -> directionDown
                            }

                            if (newDirectionDown != directionDown) {
                                directionDown = newDirectionDown
                                tailAlpha.snapTo(0.25f)
                                tailAlpha.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 140, easing = LinearEasing)
                                )
                            }
                        }

                        val edgePadding = 8.dp
                        val lineHeight = 3.dp

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = edgePadding)
                                .height(lineHeight)
                                .align(Alignment.TopCenter)
                                .offset(y = currentY)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            scanLineColor.copy(alpha = 0.15f),
                                            scanLineColor.copy(alpha = 0.85f),
                                            scanLineColor,
                                            scanLineColor.copy(alpha = 0.85f),
                                            scanLineColor.copy(alpha = 0.15f)
                                        )
                                    )
                                )
                        )

                        val tailHeight = 96.dp
                        val tailStrength = tailAlpha.value

                        if (directionDown) {
                            // Down -> tail above
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = edgePadding)
                                    .height(tailHeight)
                                    .align(Alignment.TopCenter)
                                    .offset(y = currentY - tailHeight)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                scanLineColor.copy(alpha = 0.06f * tailStrength),
                                                scanLineColor.copy(alpha = 0.16f * tailStrength),
                                                scanLineColor.copy(alpha = 0.30f * tailStrength)
                                            )
                                        )
                                    )
                            )
                        } else {
                            // Up -> tail below
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = edgePadding)
                                    .height(tailHeight)
                                    .align(Alignment.TopCenter)
                                    .offset(y = currentY + lineHeight)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                scanLineColor.copy(alpha = 0.30f * tailStrength),
                                                scanLineColor.copy(alpha = 0.16f * tailStrength),
                                                scanLineColor.copy(alpha = 0.06f * tailStrength),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    ScannerOverlayScanEffect.GoPay -> {
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.80f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 850, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "PulseAlpha"
                        )

                        var directionDown by remember { mutableStateOf(true) }
                        val epsilon = 0.015f
                        val travelStart = 0.06f
                        val travelEnd = 0.94f

                        LaunchedEffect(rawYOffset) {
                            directionDown = when {
                                rawYOffset <= (travelStart + epsilon) -> true
                                rawYOffset >= (travelEnd - epsilon) -> false
                                else -> directionDown
                            }
                        }

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val normalized = ((rawYOffset - travelStart) / (travelEnd - travelStart)).coerceIn(0f, 1f)
                            val edgeFadeRange = 0.12f
                            val edgeFactor = (kotlin.math.min(normalized, 1f - normalized) / edgeFadeRange).coerceIn(0f, 1f)
                            val alphaMul = (edgeFactor * pulseAlpha).coerceIn(0f, 1f)

                            val tailHeightPx = 86.dp.toPx()
                            val lineHeightPx = 2.dp.toPx()
                            val stripeSpacingPx = 6.dp.toPx()
                            val stripeThicknessPx = 1.dp.toPx()

                            val yPx = size.height * rawYOffset
                            val bandLeft = 0f
                            val bandWidth = size.width

                            val tailTop = if (directionDown) yPx - tailHeightPx else yPx
                            val tailHeight = tailHeightPx

                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = if (directionDown) {
                                        listOf(
                                            Color.Transparent,
                                            scanLineColor.copy(alpha = 0.06f * alphaMul),
                                            scanLineColor.copy(alpha = 0.16f * alphaMul),
                                            scanLineColor.copy(alpha = 0.28f * alphaMul)
                                        )
                                    } else {
                                        listOf(
                                            scanLineColor.copy(alpha = 0.28f * alphaMul),
                                            scanLineColor.copy(alpha = 0.16f * alphaMul),
                                            scanLineColor.copy(alpha = 0.06f * alphaMul),
                                            Color.Transparent
                                        )
                                    },
                                    startY = tailTop,
                                    endY = tailTop + tailHeight
                                ),
                                topLeft = Offset(bandLeft, tailTop),
                                size = Size(bandWidth, tailHeight)
                            )

                            var stripeY = tailTop
                            val stripeBottom = tailTop + tailHeight
                            while (stripeY < stripeBottom) {
                                val dist = if (directionDown) (yPx - stripeY) else (stripeY - yPx)
                                val t = (dist / tailHeight).coerceIn(0f, 1f)
                                val stripeAlpha = (0.44f * alphaMul) * (1f - (t * t))
                                drawRect(
                                    color = scanLineColor.copy(alpha = stripeAlpha),
                                    topLeft = Offset(bandLeft, stripeY),
                                    size = Size(bandWidth, stripeThicknessPx)
                                )
                                stripeY += stripeSpacingPx
                            }

                            val lineTop = yPx - (lineHeightPx / 2f)
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        scanLineColor.copy(alpha = 0.24f * alphaMul),
                                        scanLineColor.copy(alpha = 0.78f * alphaMul),
                                        scanLineColor.copy(alpha = 0.95f * alphaMul),
                                        scanLineColor.copy(alpha = 0.78f * alphaMul),
                                        scanLineColor.copy(alpha = 0.24f * alphaMul)
                                    )
                                ),
                                topLeft = Offset(bandLeft, lineTop),
                                size = Size(bandWidth, lineHeightPx)
                            )
                        }
                    }
                }
            }
        }
    }
}
