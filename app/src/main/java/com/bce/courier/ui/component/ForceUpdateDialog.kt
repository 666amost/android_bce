package com.bce.courier.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForceUpdateDialog(
    latestVersionName: String,
    changelog: String,
    isDownloading: Boolean,
    downloadProgressPercent: Int?,
    isUpdateDownloaded: Boolean,
    updateDownloadError: String?,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    message: String? = null
) {
    AlertDialog(
        onDismissRequest = { /* forced: no-op */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        icon = {
            Icon(
                imageVector = if (updateDownloadError.isNullOrBlank()) Icons.Default.SystemUpdate else Icons.Default.Error,
                contentDescription = null,
                tint = if (updateDownloadError.isNullOrBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = "Update Wajib",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (message.isNullOrBlank()) {
                    if (latestVersionName.isNotBlank()) {
                        Text("Versi terbaru: $latestVersionName")
                    }
                    if (changelog.isNotBlank()) {
                        Text(
                            text = changelog,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Text(message)
                }

                if (!updateDownloadError.isNullOrBlank()) {
                    Text(
                        text = updateDownloadError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isDownloading) {
                    val progress = downloadProgressPercent
                    if (progress != null) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = { progress.coerceIn(0, 100) / 100f }
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            val hasAction = message.isNullOrBlank()
            val buttonEnabled = hasAction && !isDownloading

            when {
                isUpdateDownloaded -> Button(
                    onClick = onInstall,
                    enabled = buttonEnabled
                ) {
                    Text("INSTALL")
                }
                else -> Button(
                    onClick = onDownload,
                    enabled = buttonEnabled
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isDownloading) "MENGUNDUH..." else "DOWNLOAD")
                }
            }
        },
        dismissButton = null
    )
}
