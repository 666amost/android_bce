package com.bce.courier.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex

data class ScannedItem(
    val awb: String,
    val status: String, // "success", "transferred", "error", "duplicate"
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinuousScannerDialog(
    onDismiss: () -> Unit,
    onBarcodeDetected: (String) -> Unit,
    scannedItems: List<ScannedItem>,
    lastScanStatus: String = "none"
) {
    val isDarkMode = isSystemInDarkTheme()

    val borderColor by animateColorAsState(
        targetValue = when (lastScanStatus) {
            "success" -> Color(0xFF4CAF50)
            "transferred" -> Color(0xFF2196F3)
            "error" -> Color(0xFFF44336)
            "duplicate" -> Color(0xFFFF9800)
            else -> Color.White.copy(alpha = 0.5f)
        },
        animationSpec = tween(durationMillis = 300),
        label = "BorderColor"
    )

    val flashOpacity by animateFloatAsState(
        targetValue = if (lastScanStatus != "none") 0.3f else 0f,
        animationSpec = if (lastScanStatus != "none") {
            snap()
        } else {
            tween(500)
        },
        label = "FlashOpacity"
    )

    var isFlashOn by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
                        onBarcodeDetected = onBarcodeDetected
                    )
                    
                    // Flash Feedback Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(borderColor.copy(alpha = flashOpacity))
                    )
                    
                    // Header (Overlay on top of Camera)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .zIndex(2f)
                    ) {
                         Row(
                             modifier = Modifier.align(Alignment.TopEnd),
                             horizontalArrangement = Arrangement.spacedBy(8.dp)
                         ) {
                             IconButton(
                                 onClick = { isFlashOn = !isFlashOn },
                                 colors = IconButtonDefaults.iconButtonColors(
                                     containerColor = Color.Transparent,
                                     contentColor = Color.White
                                 )
                             ) {
                                 Icon(
                                     if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                     contentDescription = "Toggle Flash",
                                     tint = if (isFlashOn) Color.Yellow else Color.White
                                 )
                             }

                             IconButton(
                                 onClick = onDismiss,
                                 colors = IconButtonDefaults.iconButtonColors(
                                     containerColor = Color.Transparent,
                                     contentColor = Color.White
                                 )
                             ) {
                                 Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                             }
                         }

                         Column(modifier = Modifier.align(Alignment.TopStart)) {
                             Text("Continuous Scan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                             Text("Arahkan kamera ke barcode", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                         }
                     }

                    // Scanner Box Overlay with hole in dimmed background
                    ScannerOverlay(
                        borderColor = borderColor,
                        scanEffect = ScannerOverlayScanEffect.GoPay
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
