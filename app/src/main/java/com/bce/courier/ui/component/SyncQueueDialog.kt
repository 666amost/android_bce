package com.bce.courier.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bce.courier.data.local.PendingUpdateEntity

@Composable
fun SyncQueueDialog(updates: List<PendingUpdateEntity>, onDismiss: () -> Unit, onRetry: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sinkronisasi pengiriman") },
        text = {
            if (updates.isEmpty()) Text("Semua pengiriman sudah tersinkron.")
            else LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(updates, key = { it.id }) { update ->
                    Column {
                        Text(update.awb, fontWeight = FontWeight.Bold)
                        Text(when {
                            update.syncState == "failed" -> "Perlu dicoba ulang"
                            update.deliverySynced -> "Status terkirim, menunggu sinkronisasi cabang"
                            update.isSyncing -> "Sedang dikirim"
                            else -> "Tersimpan di perangkat, menunggu dikirim"
                        })
                        update.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onRetry, enabled = updates.isNotEmpty()) { Text("Coba sinkronkan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}
