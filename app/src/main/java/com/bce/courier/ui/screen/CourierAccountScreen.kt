package com.bce.courier.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bce.courier.ui.component.CourierAvatar
import com.bce.courier.BuildConfig
import com.bce.courier.tracking.CourierTrackingStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourierAccountScreen(
    userName: String,
    userEmail: String,
    trackingStatus: CourierTrackingStatus,
    pendingSyncCount: Int,
    updateAvailable: Boolean,
    isDownloadingUpdate: Boolean,
    onBack: () -> Unit,
    onNearbyCouriersClick: () -> Unit,
    onAppSettingsClick: () -> Unit,
    onClearCache: () -> Unit,
    onSupportClick: () -> Unit,
    onVersionClick: () -> Unit,
    onLogout: () -> Unit,
) {
    var showLogoutConfirmation by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil & pengaturan", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CourierAvatar(userName, size = 48.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(userName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (userEmail.isNotBlank()) {
                                Text(userEmail, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("Akun kurir BCE", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            item {
                AccountSection("Operasional") {
                    ListItem(
                        headlineContent = { Text("Pelacakan lokasi") },
                        supportingContent = {
                            Text(when (trackingStatus) {
                                CourierTrackingStatus.ACTIVE -> "Aktif saat Anda bertugas"
                                CourierTrackingStatus.STARTING -> "Sedang menghubungkan lokasi"
                                CourierTrackingStatus.WAITING -> "Menunggu lokasi atau koneksi"
                                CourierTrackingStatus.OFF -> "Pelacakan belum aktif"
                            })
                        },
                        leadingContent = { Icon(Icons.Default.MyLocation, contentDescription = null) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text("Sinkronisasi data") },
                        supportingContent = {
                            Text(if (pendingSyncCount > 0) "$pendingSyncCount pembaruan menunggu dikirim" else "Tidak ada antrean sinkronisasi")
                        },
                        leadingContent = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    AccountAction("Kurir terdekat", "Lihat posisi tim di sekitar", Icons.Default.PeopleAlt, onNearbyCouriersClick)
                }
            }

            item {
                AccountSection("Pengaturan aplikasi") {
                    AccountAction("Izin aplikasi", "Kelola izin lokasi, kamera, dan notifikasi", Icons.Default.AdminPanelSettings, onAppSettingsClick)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    AccountAction(
                        title = "Bersihkan cache",
                        subtitle = if (isDownloadingUpdate) "Tunggu unduhan pembaruan selesai" else "Antrean offline dan foto POD tetap tersimpan",
                        icon = Icons.Default.CleaningServices,
                        onClick = onClearCache,
                        enabled = !isDownloadingUpdate,
                    )
                }
            }

            item {
                AccountSection("Tentang & bantuan") {
                    AccountAction(
                        title = "Versi aplikasi",
                        subtitle = BuildConfig.VERSION_NAME + if (updateAvailable) " · Pembaruan tersedia" else " · Detail & pembaruan",
                        icon = Icons.Default.Info,
                        onClick = onVersionClick,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    AccountAction("Bantuan", "Hubungi tim operasional", Icons.Default.HeadsetMic, onSupportClick)
                }
            }

            item {
                OutlinedButton(
                    onClick = { showLogoutConfirmation = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Keluar dari akun")
                }
            }
        }
    }

    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            title = { Text("Keluar dari akun?") },
            text = {
                Text(if (pendingSyncCount > 0) "Masih ada $pendingSyncCount pembaruan yang belum terkirim. Sebaiknya tunggu sinkronisasi selesai sebelum keluar." else "Anda perlu masuk kembali untuk melanjutkan tugas kurir.")
            },
            confirmButton = {
                TextButton(
                    onClick = { showLogoutConfirmation = false; onLogout() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Keluar") }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirmation = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun AccountSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) { Column(content = content) }
    }
}

@Composable
private fun AccountAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(onClick = onClick, enabled = enabled) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            leadingContent = { Icon(icon, contentDescription = null) },
            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            colors = ListItemDefaults.colors(
                headlineColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            ),
        )
    }
}
