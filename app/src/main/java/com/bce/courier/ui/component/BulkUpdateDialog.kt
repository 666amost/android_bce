package com.bce.courier.ui.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkUpdateDialog(
    onDismiss: () -> Unit,
    onUpdate: (List<String>) -> Unit,
    isProcessing: Boolean,
    processingStatus: String,
    onScanClick: () -> Unit,
    awbText: String,
    onAwbTextChange: (String) -> Unit
) {
    val isDarkMode = isSystemInDarkTheme()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Bulk Update Manual",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Masukkan nomor resi satu per baris atau dipisahkan dengan koma untuk mengupdate status ke Out For Delivery",
                    fontSize = 12.sp,
                    color = if (isDarkMode) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = awbText,
                    onValueChange = { if (!isProcessing) onAwbTextChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    placeholder = { Text("Masukkan nomor resi di sini...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF5F5F5),
                        unfocusedContainerColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF5F5F5)
                    ),
                    shape = MaterialTheme.shapes.medium,
                    enabled = !isProcessing
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Contoh: BCE558788, 558744",
                        fontSize = 11.sp,
                        color = if (isDarkMode) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
                    )

                    val awbCount = remember(awbText) {
                        awbText.split(Regex("[,\\s\\n]+")).filter { it.isNotBlank() }.size
                    }
                    Text(
                        text = "$awbCount resi",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (processingStatus.isNotBlank()) {
                    Surface(
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = processingStatus,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = onScanClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan QR")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val list = awbText.split(Regex("[,\\s\\n]+")).filter { it.isNotBlank() }
                        onUpdate(list)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBC02D)),
                    enabled = !isProcessing && awbText.isNotBlank()
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("Update For Delivery", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isProcessing
                ) {
                    Text("Tutup", color = if (isDarkMode) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray)
                }
            }
        }
    }
}

