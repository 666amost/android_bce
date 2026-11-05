package com.bce.courier.ui.component

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

data class ScannedItem(
    val awb: String,
    val status: String, // "success", "transferred", "error", "duplicate"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinuousScannerContent(
    onDismiss: () -> Unit,
    onBarcodeDetected: (String) -> Unit,
    scannedItems: List<ScannedItem>,
    lastScanStatus: String = "none",
    isActive: Boolean = true,
    startPreview: Boolean = true,
) {
    val isDarkMode = isSystemInDarkTheme()

    val neutralBorder = Color.White.copy(alpha = 0.85f)
    val borderColor = remember { Animatable(neutralBorder) }
    LaunchedEffect(lastScanStatus, scannedItems.firstOrNull()?.timestamp) {
        val feedbackColor = when (lastScanStatus) {
            "success" -> Color(0xFF4CAF50)
            "transferred" -> Color(0xFF2196F3)
            "error" -> Color(0xFFF44336)
            "duplicate" -> Color(0xFFFF9800)
            else -> neutralBorder
        }
        borderColor.snapTo(feedbackColor)
        if (lastScanStatus != "none") {
            kotlinx.coroutines.delay(80)
            borderColor.animateTo(neutralBorder, tween(220))
        }
    }

    var isFlashOn by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Scanner View Area (Top portion)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.2f)
            ) {
                ScannerView(
                    isFlashOn = isFlashOn,
                    isActive = isActive,
                    startPreview = startPreview,
                    onBarcodeDetected = onBarcodeDetected
                )

                ScannerToolbar(
                    title = "Continuous Scan",
                    subtitle = "Arahkan kamera ke barcode",
                    isFlashOn = isFlashOn,
                    onFlash = { isFlashOn = !isFlashOn },
                    onClose = onDismiss,
                    modifier = Modifier.zIndex(2f),
                )

                // Scanner Box Overlay with hole in dimmed background
                ScannerOverlay(
                    borderColor = borderColor.value,
                    modifier = Modifier.padding(top = 64.dp, bottom = 64.dp),
                )

                // Stats Overlay
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ScanStat(
                        label = "Sukses",
                        count = scannedItems.count { it.status == "success" || it.status == "transferred" },
                        color = Color(0xFF4CAF50)
                    )
                    ScanStat(label = "Gagal", count = scannedItems.count { it.status == "error" }, color = Color(0xFFF44336))
                    ScanStat(label = "Duplikat", count = scannedItems.count { it.status == "duplicate" }, color = Color(0xFFFF9800))
                }
            }

            // Results (Bottom Sheet style)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.8f),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = if (isDarkMode) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Riwayat Scan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (isDarkMode) MaterialTheme.colorScheme.onSurface else Color.Black
                            )
                        }
                        Text(
                            "${scannedItems.size} Total",
                            fontSize = 12.sp,
                            color = if (isDarkMode) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (scannedItems.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Belum ada data",
                                color = if (isDarkMode) MaterialTheme.colorScheme.onSurfaceVariant else Color.LightGray
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(scannedItems, key = { "${it.awb}_${it.status}" }) { item ->
                                ScannedItemRow(item)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScanStat(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "$count $label", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ScannedItemRow(item: ScannedItem) {
    val color = when (item.status) {
        "success" -> Color(0xFF4CAF50)
        "transferred" -> Color(0xFF2196F3)
        "error" -> Color(0xFFF44336)
        "duplicate" -> Color(0xFFFF9800)
        else -> Color.Gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    item.awb.takeLast(2),
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.awb,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Text(item.message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val isDarkMode = isSystemInDarkTheme()
            val sdf = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()) }
            Text(
                sdf.format(java.util.Date(item.timestamp)),
                fontSize = 10.sp,
                color = if (isDarkMode) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
            )
        }
    }
}
