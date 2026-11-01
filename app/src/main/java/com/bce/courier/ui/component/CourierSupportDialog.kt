package com.bce.courier.ui.component

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

private val SUPPORT_CONTACTS = listOf(
    "ROBERT" to "6282114097704",
    "RENA PGK" to "6282185998199",
    "AIDIL PNK" to "6289693418191",
)

@Composable
fun CourierSupportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.HeadsetMic, contentDescription = null) },
        title = { Text("Bantuan operasional") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Pilih kontak untuk menghubungi tim melalui WhatsApp.")
                SUPPORT_CONTACTS.forEach { (name, phone) ->
                    ListItem(
                        headlineContent = { Text(name) },
                        supportingContent = { Text("+$phone") },
                        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        modifier = Modifier.clickable { openWhatsAppContact(context, phone) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

private fun openWhatsAppContact(context: Context, phone: String) {
    val uri = "https://wa.me/$phone".toUri()
    val intents = listOf(
        Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp"),
        Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp.w4b"),
        Intent(Intent.ACTION_VIEW, uri),
    )
    val opened = intents.any { intent -> runCatching { context.startActivity(intent); true }.getOrDefault(false) }
    if (!opened) Toast.makeText(context, "Tidak bisa membuka WhatsApp", Toast.LENGTH_SHORT).show()
}
