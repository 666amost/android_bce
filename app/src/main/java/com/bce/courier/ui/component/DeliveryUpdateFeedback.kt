package com.bce.courier.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bce.courier.data.model.DeliveryStatus
import com.bce.courier.data.model.DeliveryUpdateOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class DeliveryCompletion(val status: String, val outcome: DeliveryUpdateOutcome)

/** Success is shown only after the update and its photo have been durably saved. */
@Composable
fun DeliveryUpdateFeedback(
    isSaving: Boolean,
    completion: DeliveryCompletion?,
    awb: String,
    onFinished: () -> Unit,
) {
    if (!isSaving && completion == null) return

    val onFinishedLatest by rememberUpdatedState(onFinished)
    val haptics = LocalHapticFeedback.current
    val accessibility = LocalAccessibilityManager.current
    val successColor = Color(0xFF11A66C)
    var finished by remember(completion) { mutableStateOf(false) }
    val finishOnce = {
        if (completion != null && !finished) {
            finished = true
            onFinishedLatest()
        }
    }

    LaunchedEffect(completion) {
        if (completion != null) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val timeout = accessibility?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = 1800L,
                containsIcons = true,
                containsText = true,
                containsControls = true,
            ) ?: 1800L
            delay(timeout)
            finishOnce()
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 24.dp).widthIn(max = 360.dp).fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            AnimatedContent(
                targetState = completion,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(300)) { it / 12 }) togetherWith
                        fadeOut(tween(120))
                },
                label = "deliveryFeedback",
            ) { result ->
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)
                        .fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (result == null) {
                        Box(Modifier.size(144.dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(96.dp).background(successColor.copy(alpha = 0.08f), CircleShape))
                            CircularProgressIndicator(color = successColor, strokeWidth = 3.dp)
                        }
                        Text("Menyimpan...", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Sebentar, ya", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(48.dp))
                    } else {
                        AnimatedDeliveryCheck(successColor)
                        Text(
                            if (DeliveryStatus.isDelivered(result.status)) "Paket terkirim!" else "Status diperbarui",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(awb, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        val queued = result.outcome == DeliveryUpdateOutcome.QUEUED
                        Row(
                            modifier = Modifier.background(successColor.copy(alpha = 0.1f), CircleShape)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                if (queued) Icons.Default.CloudUpload else Icons.Default.CloudDone,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = successColor,
                            )
                            Text(
                                if (queued) "Syncing to cloud" else "Cloud synced",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = finishOnce) { Text("Lanjut", color = successColor) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedDeliveryCheck(color: Color) {
    val circleScale = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val check = remember { Animatable(0f) }
    val halo = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { circleScale.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 420f)) }
        launch { ring.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        launch { halo.animateTo(1f, tween(800)) }
        delay(180)
        check.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }
    Canvas(Modifier.size(144.dp)) {
        val center = this.center
        val radius = 46.dp.toPx()
        drawCircle(color.copy(alpha = 0.14f * (1f - halo.value)), radius * (1f + 0.55f * halo.value))
        val ringRadius = 60.dp.toPx()
        drawCircle(color.copy(alpha = 0.1f), ringRadius, style = Stroke(2.dp.toPx()))
        drawArc(
            color.copy(alpha = 0.45f), startAngle = -90f, sweepAngle = 360f * ring.value,
            useCenter = false,
            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
            size = Size(ringRadius * 2, ringRadius * 2),
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
        )
        scale(circleScale.value) {
            drawCircle(color, radius)
            val path = Path().apply {
                moveTo(center.x - 20.dp.toPx(), center.y)
                lineTo(center.x - 5.dp.toPx(), center.y + 14.dp.toPx())
                lineTo(center.x + 23.dp.toPx(), center.y - 16.dp.toPx())
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val segment = Path()
            measure.getSegment(0f, measure.length * check.value, segment, true)
            drawPath(segment, Color.White, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
