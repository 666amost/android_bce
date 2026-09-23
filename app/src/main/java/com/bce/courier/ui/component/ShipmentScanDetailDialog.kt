package com.bce.courier.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bce.courier.data.model.DeliveryStatus
import com.bce.courier.data.model.ShipmentItem
import java.util.Locale

@Composable
fun ShipmentScanDetailDialog(
    awb: String,
    shipment: ShipmentItem?,
    isRegisteredShipment: Boolean,
    currentCourierId: String,
    ownerName: String?,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onAssignmentAction: () -> Unit,
    onUpdateStatus: () -> Unit,
) {
    val isMine = isRegisteredShipment && shipment?.courier_id == currentCourierId
    val isDelivered = shipment?.let { DeliveryStatus.isDelivered(it.current_status) } == true
    val actionLabel = when {
        !isRegisteredShipment || shipment == null || shipment.courier_id.isNullOrBlank() -> "Assign ke Saya"
        isMine -> "Update Status"
        else -> "Pindahkan ke Saya"
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
        title = {
            Text(
                text = awb,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("Penerima", shipment?.recipient_name?.ifBlank { "-" } ?: "Belum ditemukan")
                DetailRow("Alamat", shipment?.address?.ifBlank { "-" } ?: "Data belum tersedia")
                DetailRow(
                    "Status",
                    when {
                        isRegisteredShipment -> shipment?.current_status?.let(::deliveryStatusLabel) ?: "BELUM TERDAFTAR"
                        shipment != null -> "DATA MANIFEST"
                        else -> "BELUM DITEMUKAN"
                    },
                )
                DetailRow(
                    "Pemilik",
                    when {
                        !isRegisteredShipment || shipment == null || shipment.courier_id.isNullOrBlank() -> "Belum ditugaskan"
                        isMine -> "Anda${ownerName?.let { " ($it)" }.orEmpty()}"
                        else -> ownerName ?: shipment.courier_id.orEmpty()
                    },
                )
                if ((shipment?.cod_amount ?: 0.0) > 0.0) {
                    DetailRow("COD", "Rp ${String.format(Locale.US, "%,.0f", shipment?.cod_amount ?: 0.0)}")
                }
                if (isDelivered) {
                    Text(
                        text = "Shipment sudah Delivered dan tidak dapat dipindahkan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (!isDelivered) {
                Button(
                    onClick = if (isMine) onUpdateStatus else onAssignmentAction,
                    enabled = !isProcessing,
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                    Text(actionLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isProcessing) { Text("Tutup") }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.32f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.68f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun deliveryStatusLabel(status: String): String = when (DeliveryStatus.normalized(status)) {
    DeliveryStatus.OUT_FOR_DELIVERY -> "OUT FOR DELIVERY"
    DeliveryStatus.PENDING -> "PENDING"
    DeliveryStatus.DELIVERED -> "DELIVERED"
    else -> status.replace('_', ' ').uppercase()
}
