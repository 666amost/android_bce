package com.bce.courier.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ScanModePicker(onIncoming: () -> Unit, onDelivery: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pilih Mode Scan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Pilih jenis scan yang ingin digunakan.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onIncoming, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("SCAN MASUK") }
        OutlinedButton(onClick = onDelivery, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("SCAN DELIVERY") }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Tutup") }
    }
}
