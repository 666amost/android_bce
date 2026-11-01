package com.bce.courier.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bce.courier.BuildConfig

@Composable
fun AppVersionDialog(
    updateAvailable: Boolean,
    latestVersionName: String,
    changelog: String,
    hasCheckedForUpdate: Boolean,
    isCheckingForUpdate: Boolean,
    updateCheckError: String?,
    isDownloading: Boolean,
    downloadProgressPercent: Int?,
    isUpdateDownloaded: Boolean,
    updateDownloadError: String?,
    onCheckForUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
        title = { Text("Versi aplikasi") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("BCE Courier", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Versi terpasang: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                        Text("Build ${BuildConfig.VERSION_CODE}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                when {
                    isCheckingForUpdate -> {
                        Text("Memeriksa pembaruan…")
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    updateCheckError != null -> Text(updateCheckError, color = MaterialTheme.colorScheme.error)
                    updateAvailable -> Text("Pembaruan tersedia: $latestVersionName", fontWeight = FontWeight.SemiBold)
                    hasCheckedForUpdate -> Text("Aplikasi sudah menggunakan versi terbaru.")
                    else -> Text("Periksa pembaruan untuk melihat versi terbaru yang tersedia.")
                }

                if (updateAvailable && changelog.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Catatan rilis", fontWeight = FontWeight.SemiBold)
                        Text(changelog, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (!updateDownloadError.isNullOrBlank()) {
                    Text(updateDownloadError, color = MaterialTheme.colorScheme.error)
                }
                when {
                    isDownloading -> {
                        Text("Mengunduh pembaruan" + (downloadProgressPercent?.let { " · ${it.coerceIn(0, 100)}%" } ?: "…"))
                        if (downloadProgressPercent != null) {
                            LinearProgressIndicator(
                                progress = { downloadProgressPercent.coerceIn(0, 100) / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    updateAvailable && isUpdateDownloaded -> Text("Unduhan selesai. Pembaruan siap dipasang.")
                }

                if (updateAvailable) {
                    TextButton(onClick = onCheckForUpdate, enabled = !isCheckingForUpdate && !isDownloading) {
                        Text("Periksa pembaruan lagi")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        updateAvailable && isUpdateDownloaded -> onInstallUpdate()
                        updateAvailable -> onDownloadUpdate()
                        else -> onCheckForUpdate()
                    }
                },
                enabled = !isCheckingForUpdate && !isDownloading,
            ) {
                Text(when {
                    isDownloading -> "Mengunduh…"
                    updateAvailable && isUpdateDownloaded -> "Pasang pembaruan"
                    updateAvailable -> "Unduh pembaruan"
                    else -> "Periksa pembaruan"
                })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}
