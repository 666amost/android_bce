package com.bce.courier.ui.component

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.Executors

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun ScannerView(
    isFlashOn: Boolean = false,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(appContext) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_DATA_MATRIX,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8
                )
                .build()
        )
    }
    val onBarcodeDetectedState = rememberUpdatedState(onBarcodeDetected)
    val isFlashOnState = rememberUpdatedState(isFlashOn)
    
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    LaunchedEffect(isFlashOn) {
        camera?.cameraControl?.enableTorch(isFlashOn)
    }
    
    val lastScannedTime = remember { AtomicLong(0L) }
    val throttleDelay = 1200L

    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
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

                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastScannedTime.get() < throttleDelay) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }

                                val image = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )

                                scanner.process(image)
                                    .addOnSuccessListener(mainExecutor) { barcodes ->
                                        if (barcodes.isNotEmpty()) {
                                            barcodes.firstOrNull()?.rawValue?.let { value ->
                                                lastScannedTime.set(System.currentTimeMillis())
                                                onBarcodeDetectedState.value(value)
                                            }
                                        }
                                    }
                                    .addOnFailureListener(mainExecutor) {
                                        Log.e("ScannerView", "Scan failed", it)
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            }
                        }
                    boundImageAnalysis = imageAnalysis

                    cameraProvider.unbindAll()
                    val boundCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    camera = boundCamera
                    boundCamera.cameraControl.enableTorch(isFlashOnState.value)
                } catch (e: Exception) {
                    Log.e("ScannerView", "Binding failed", e)
                }
            },
            ContextCompat.getMainExecutor(appContext)
        )

        onDispose {
            disposed = true
            camera = null
            try {
                boundImageAnalysis?.clearAnalyzer()
            } catch (_: Exception) {
            }
            try {
                boundProvider?.unbindAll()
            } catch (_: Exception) {
            }
            try {
                scanner.close()
            } catch (_: Exception) {
            }
            try {
                executor.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
    }
}
