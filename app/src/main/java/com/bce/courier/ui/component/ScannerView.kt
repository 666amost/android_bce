package com.bce.courier.ui.component

import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.bce.courier.util.BarcodeScanSafetyGate
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

private const val FEEDBACK_VISIBLE_MS = 280L
private const val TRACKING_SMOOTHING = 0.48f

private data class NormalizedPoint(
    val x: Float,
    val y: Float,
)

private data class TrackedBarcode(
    val value: String,
    val points: List<NormalizedPoint>,
    val sourceAspectRatio: Float,
) {
    val left: Float get() = points.minOf { it.x }
    val top: Float get() = points.minOf { it.y }
    val right: Float get() = points.maxOf { it.x }
    val bottom: Float get() = points.maxOf { it.y }
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val area: Float get() = (right - left).coerceAtLeast(0f) * (bottom - top).coerceAtLeast(0f)

    fun smoothTowards(target: TrackedBarcode): TrackedBarcode {
        if (
            value != target.value ||
            points.size != target.points.size ||
            kotlin.math.abs(sourceAspectRatio - target.sourceAspectRatio) > 0.01f
        ) {
            return target
        }

        return target.copy(
            points = points.zip(target.points) { current, next ->
                NormalizedPoint(
                    x = current.x + ((next.x - current.x) * TRACKING_SMOOTHING),
                    y = current.y + ((next.y - current.y) * TRACKING_SMOOTHING),
                )
            }
        )
    }
}

private data class BarcodeFeedback(
    val barcode: TrackedBarcode,
    val confirmed: Boolean,
    val timestamp: Long,
)

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun ScannerView(
    isFlashOn: Boolean = false,
    onBarcodeDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(appContext) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val scanSafetyGate = remember { BarcodeScanSafetyGate() }

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_DATA_MATRIX,
                    Barcode.FORMAT_CODE_128,
                )
                .build()
        )
    }
    val onBarcodeDetectedState = rememberUpdatedState(onBarcodeDetected)
    val isFlashOnState = rememberUpdatedState(isFlashOn)

    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var feedback by remember { mutableStateOf<BarcodeFeedback?>(null) }

    LaunchedEffect(isFlashOn) {
        camera?.cameraControl?.enableTorch(isFlashOn)
    }

    LaunchedEffect(feedback?.timestamp) {
        val timestamp = feedback?.timestamp ?: return@LaunchedEffect
        delay(FEEDBACK_VISIBLE_MS)
        if (feedback?.timestamp == timestamp) feedback = null
    }

    val previewView = remember(context) {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(Unit) {
        var disposed = false
        var boundProvider: ProcessCameraProvider? = null
        var boundImageAnalysis: ImageAnalysis? = null

        cameraProviderFuture.addListener(
            {
                if (disposed) return@addListener

                try {
                    val cameraProvider = cameraProviderFuture.get()
                    boundProvider = cameraProvider

                    val preview = Preview.Builder()
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(android.util.Size(1280, 720))
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(executor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage == null) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }

                                val rotation = imageProxy.imageInfo.rotationDegrees
                                val uprightWidth = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
                                val uprightHeight = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height
                                val image = InputImage.fromMediaImage(mediaImage, rotation)

                                scanner.process(image)
                                    .addOnSuccessListener(mainExecutor) { barcodes ->
                                        val candidates = barcodes.mapNotNull { barcode ->
                                            val value = barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                                            val bounds = barcode.boundingBox ?: return@mapNotNull null

                                            val rawPoints = barcode.cornerPoints
                                                ?.takeIf { it.size >= 4 }
                                                ?.take(4)
                                                ?.map { point ->
                                                    NormalizedPoint(
                                                        x = (point.x.toFloat() / uprightWidth).coerceIn(0f, 1f),
                                                        y = (point.y.toFloat() / uprightHeight).coerceIn(0f, 1f),
                                                    )
                                                }
                                                ?: listOf(
                                                    NormalizedPoint(bounds.left.toFloat() / uprightWidth, bounds.top.toFloat() / uprightHeight),
                                                    NormalizedPoint(bounds.right.toFloat() / uprightWidth, bounds.top.toFloat() / uprightHeight),
                                                    NormalizedPoint(bounds.right.toFloat() / uprightWidth, bounds.bottom.toFloat() / uprightHeight),
                                                    NormalizedPoint(bounds.left.toFloat() / uprightWidth, bounds.bottom.toFloat() / uprightHeight),
                                                ).map { point ->
                                                    point.copy(
                                                        x = point.x.coerceIn(0f, 1f),
                                                        y = point.y.coerceIn(0f, 1f),
                                                    )
                                                }

                                            TrackedBarcode(
                                                value = value,
                                                points = rawPoints,
                                                sourceAspectRatio = uprightWidth.toFloat() / uprightHeight.toFloat(),
                                            )
                                        }.filter { barcode ->
                                            barcode.centerX in 0.08f..0.92f && barcode.centerY in 0.28f..0.72f
                                        }

                                        val previousFeedback = feedback
                                        val candidate = candidates.maxByOrNull { barcode ->
                                            val distanceFromCenter = kotlin.math.abs(barcode.centerX - 0.5f) +
                                                kotlin.math.abs(barcode.centerY - 0.5f)
                                            val sameTargetBonus = if (previousFeedback?.barcode?.value == barcode.value) 0.20f else 0f
                                            barcode.area - (distanceFromCenter * 0.12f) + sameTargetBonus
                                        }

                                        if (candidate == null) {
                                            scanSafetyGate.resetCandidate()
                                            return@addOnSuccessListener
                                        }

                                        val now = SystemClock.elapsedRealtime()
                                        val canEmit = scanSafetyGate.observe(candidate.value, now)
                                        val sameTarget = previousFeedback?.barcode?.value == candidate.value
                                        val trackedCandidate = if (sameTarget) {
                                            previousFeedback?.barcode?.smoothTowards(candidate) ?: candidate
                                        } else {
                                            candidate
                                        }

                                        feedback = BarcodeFeedback(
                                            barcode = trackedCandidate,
                                            confirmed = canEmit || (sameTarget && previousFeedback?.confirmed == true),
                                            timestamp = now,
                                        )

                                        if (canEmit) {
                                            previewView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onBarcodeDetectedState.value(candidate.value)
                                        }
                                    }
                                    .addOnFailureListener(mainExecutor) { error ->
                                        Log.e("ScannerView", "Scan failed", error)
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            }
                        }
                    boundImageAnalysis = imageAnalysis

                    cameraProvider.unbindAll()
                    val boundCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                    )
                    camera = boundCamera
                    boundCamera.cameraControl.enableTorch(isFlashOnState.value)
                } catch (error: Exception) {
                    Log.e("ScannerView", "Binding failed", error)
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )

        onDispose {
            disposed = true
            camera = null
            runCatching { boundImageAnalysis?.clearAnalyzer() }
            runCatching { boundProvider?.unbindAll() }
            runCatching { scanner.close() }
            runCatching { executor.shutdown() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        feedback?.let { current ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val tracked = current.barcode
                val canvasAspectRatio = size.width / size.height
                val displayedWidth: Float
                val displayedHeight: Float
                val offsetX: Float
                val offsetY: Float

                if (canvasAspectRatio > tracked.sourceAspectRatio) {
                    displayedWidth = size.width
                    displayedHeight = size.width / tracked.sourceAspectRatio
                    offsetX = 0f
                    offsetY = (size.height - displayedHeight) / 2f
                } else {
                    displayedHeight = size.height
                    displayedWidth = size.height * tracked.sourceAspectRatio
                    offsetX = (size.width - displayedWidth) / 2f
                    offsetY = 0f
                }

                fun mapToPreview(point: NormalizedPoint) = Offset(
                    x = offsetX + (point.x * displayedWidth),
                    y = offsetY + (point.y * displayedHeight),
                )

                val previewPoints = tracked.points.map(::mapToPreview)
                if (previewPoints.size >= 4) {
                    val trackingPath = Path().apply {
                        moveTo(previewPoints.first().x, previewPoints.first().y)
                        previewPoints.drop(1).forEach { point -> lineTo(point.x, point.y) }
                        close()
                    }
                    val trackingColor = if (current.confirmed) Color(0xFF34C759) else Color(0xFF2196F3)

                    drawPath(
                        path = trackingPath,
                        color = trackingColor.copy(alpha = if (current.confirmed) 0.10f else 0.06f),
                    )
                    drawPath(
                        path = trackingPath,
                        color = trackingColor.copy(alpha = 0.20f),
                        style = Stroke(
                            width = if (current.confirmed) 9.dp.toPx() else 7.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                    drawPath(
                        path = trackingPath,
                        color = trackingColor,
                        style = Stroke(
                            width = if (current.confirmed) 3.5.dp.toPx() else 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
        }
    }
}
