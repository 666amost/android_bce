package com.bce.courier.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.core.net.toUri
import com.bce.courier.data.model.ShipmentItem
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.lerp
import com.bce.courier.tracking.CourierTrackingStatus
import com.bce.courier.ui.component.NearbyCouriersDialog
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val SUPPORT_CONTACTS = listOf(
    "ROBERT" to "6282114097704",
    "RENA PGK" to "6282185998199",
    "AIDIL PNK" to "6289693418191"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userName: String,
    shipments: List<ShipmentItem>,
    assignedCount: Int,
    completedCount: Int,
    pendingCount: Int,
    pendingSyncCount: Int = 0,
    trackingStatus: CourierTrackingStatus = CourierTrackingStatus.OFF,
    pointsScore: Int? = null,
    overdue14dCount: Int = 0,
    showOverdueDialog: Boolean = false,
    isLoading: Boolean,
    isShowingCachedData: Boolean = false,
    cachedDataUpdatedAt: Long? = null,
    selectedTab: Int,
    updateAvailable: Boolean = false,
    latestVersionName: String = "",
    changelog: String = "",
    isDownloading: Boolean = false,
    downloadProgressPercent: Int? = null,
    isUpdateDownloaded: Boolean = false,
    updateDownloadError: String? = null,
    onTabSelected: (Int) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onShipmentClick: (ShipmentItem) -> Unit,
    onShipmentMapClick: (ShipmentItem) -> Unit,
    onSearchScanClick: () -> Unit,
    onContinuousScanClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onPickupClick: () -> Unit,
    onClearStorage: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    onDismissOverdueDialog: () -> Unit = {}
) {
    val context = LocalContext.current
    val tabs = listOf("Assigned", "Completed", "Pending")
    var searchQuery by remember { mutableStateOf("") }
    val isDarkMode = isSystemInDarkTheme()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showProfileMenu by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    var showNearbyCouriers by remember { mutableStateOf(false) }
    var showScanModeDialog by remember { mutableStateOf(false) }

    var isOnline by remember { mutableStateOf(true) }
    val isTrackingActive = isOnline && trackingStatus == CourierTrackingStatus.ACTIVE
    val trackingPulse = rememberInfiniteTransition(label = "trackingPulse")
    val trackingDotAlpha by trackingPulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trackingDotAlpha"
    )

    val onlineStatusLabel = when {
        !isOnline -> "Offline"
        trackingStatus == CourierTrackingStatus.ACTIVE -> "Online Track"
        trackingStatus == CourierTrackingStatus.STARTING -> "Online Track..."
        trackingStatus == CourierTrackingStatus.WAITING -> "Online Track wait"
        else -> "Online"
    }

    DisposableEffect(Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                isOnline = true
            }
            override fun onLost(network: android.net.Network) {
                isOnline = false
            }
        }
        cm.registerDefaultNetworkCallback(callback)

        val network = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(network)
        isOnline = capabilities != null && (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                                            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR))

        onDispose {
            cm.unregisterNetworkCallback(callback)
        }
    }

    val filteredShipments = remember(shipments, searchQuery, selectedTab) {
        val query = searchQuery.trim()
        if (query.isBlank()) shipments
        else shipments.filter {
            it.awb.contains(query, ignoreCase = true) ||
            it.recipient_name.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            val topContainerColor = if (isDarkMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary
            val topContentColor = if (isDarkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary

            Surface(
                color = topContainerColor,
                contentColor = topContentColor
            ) {
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showProfileMenu = true }
                                    .padding(vertical = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Hi,",
                                    fontSize = 12.sp,
                                    color = topContentColor.copy(alpha = 0.75f),
                                    maxLines = 1
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = userName,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = topContentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = "Menu",
                                        tint = topContentColor.copy(alpha = 0.9f)
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .graphicsLayer {
                                                alpha = if (isTrackingActive) trackingDotAlpha else 1f
                                                scaleX = if (isTrackingActive) 1.08f else 1f
                                                scaleY = if (isTrackingActive) 1.08f else 1f
                                            }
                                            .background(
                                                if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336),
                                                shape = RoundedCornerShape(99.dp)
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = onlineStatusLabel,
                                        fontSize = 11.sp,
                                        color = topContentColor.copy(alpha = 0.85f),
                                        maxLines = 1
                                    )

                                    if (pendingCount > 0 && pointsScore != null) {
                                        Spacer(modifier = Modifier.width(10.dp))
                                        val clampedScore = pointsScore.coerceIn(1, 10)
                                        val t = (clampedScore - 1) / 9f
                                        val chipColor = lerp(Color(0xFFD32F2F), Color(0xFFFFC107), t)
                                        Surface(
                                            color = chipColor.copy(alpha = 0.18f),
                                            contentColor = chipColor,
                                            shape = RoundedCornerShape(99.dp),
                                            border = BorderStroke(1.dp, chipColor.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = "Point $clampedScore",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            DropdownMenu(
                                expanded = showProfileMenu,
                                onDismissRequest = { showProfileMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Bersihkan cache") },
                                    onClick = {
                                        showProfileMenu = false
                                        onClearStorage()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Kurir terdekat") },
                                    onClick = {
                                        showProfileMenu = false
                                        showNearbyCouriers = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Support") },
                                    onClick = {
                                        showProfileMenu = false
                                        showSupportDialog = true
                                    }
                                )
                            }
                        }

                        if (pendingSyncCount > 0) {
                            Surface(
                                color = topContentColor.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CloudUpload,
                                        contentDescription = "Syncing",
                                        modifier = Modifier.size(14.dp),
                                        tint = topContentColor
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = formatCountModern(pendingSyncCount),
                                        fontSize = 11.sp,
                                        color = topContentColor
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        if (updateAvailable) {
                            IconButton(onClick = { showUpdateDialog = true }) {
                                Box {
                                    Icon(
                                        Icons.Default.SystemUpdate,
                                        contentDescription = "Update Available",
                                        tint = topContentColor
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(10.dp)
                                            .background(Color(0xFFF44336), shape = RoundedCornerShape(99.dp))
                                    )
                                }
                            }
                        }

                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = topContentColor)
                        }
                        IconButton(onClick = onLogout) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = topContentColor)
                        }
                    }

                    val searchContainerColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant else Color.White
                    val searchTextColor = if (isDarkMode) MaterialTheme.colorScheme.onSurface else Color.Black

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Cari AWB / nama penerima", fontSize = 14.sp, color = searchTextColor.copy(alpha = 0.6f)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = searchTextColor.copy(alpha = 0.8f)) },
                        trailingIcon = {
                            IconButton(onClick = onSearchScanClick) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan", tint = searchTextColor.copy(alpha = 0.9f))
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = searchContainerColor,
                            unfocusedContainerColor = searchContainerColor,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = searchTextColor,
                            unfocusedTextColor = searchTextColor,
                            cursorColor = searchTextColor
                        )
                    )

                    val indicatorColor = if (isDarkMode) MaterialTheme.colorScheme.primary else topContentColor

                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = topContentColor,
                        indicator = { tabPositions ->
                            if (tabPositions.isNotEmpty() && selectedTab < tabPositions.size) {
                                val currentTabPosition = tabPositions[selectedTab]
                                val currentTabWidth by animateDpAsState(
                                    targetValue = currentTabPosition.width,
                                    animationSpec = tween(
                                        durationMillis = 250,
                                        easing = FastOutSlowInEasing
                                    ),
                                    label = "tabWidth"
                                )
                                val indicatorOffset by animateDpAsState(
                                    targetValue = currentTabPosition.left,
                                    animationSpec = tween(
                                        durationMillis = 250,
                                        easing = FastOutSlowInEasing
                                    ),
                                    label = "tabIndicatorOffset"
                                )
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .wrapContentSize(Alignment.BottomStart)
                                        .offset(x = indicatorOffset)
                                        .width(currentTabWidth)
                                        .height(2.dp)
                                        .background(
                                            color = indicatorColor,
                                            shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                                        )
                                )
                            }
                        },
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val count = when (index) {
                                0 -> assignedCount
                                1 -> completedCount
                                2 -> pendingCount
                                else -> 0
                            }
                            val formattedCount = formatCountModern(count)
                            val isSelected = selectedTab == index
                            val isPendingTab = index == 2

                            Tab(
                                selected = isSelected,
                                onClick = { onTabSelected(index) },
                                modifier = Modifier.heightIn(min = 44.dp),
                                unselectedContentColor = topContentColor.copy(alpha = 0.75f),
                                selectedContentColor = topContentColor,
                                text = {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                        )
                                        if (formattedCount.isNotEmpty()) {
                                            val warningBase = Color(0xFFFFC107)

                                            val chipBg = when {
                                                isPendingTab && isSelected -> warningBase.copy(alpha = 0.26f)
                                                isPendingTab -> warningBase.copy(alpha = 0.18f)
                                                isSelected -> topContentColor.copy(alpha = 0.16f)
                                                else -> topContentColor.copy(alpha = 0.10f)
                                            }

                                            val chipFg = when {
                                                isPendingTab -> warningBase
                                                isSelected -> topContentColor
                                                else -> topContentColor.copy(alpha = 0.92f)
                                            }

                                            Surface(
                                                color = chipBg,
                                                shape = RoundedCornerShape(999.dp)
                                            ) {
                                                Text(
                                                    text = formattedCount,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = chipFg,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            val dockShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            val dockColor = MaterialTheme.colorScheme.surface
            val dockBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)

            val sideContainer = MaterialTheme.colorScheme.surfaceVariant
            val sideContent = MaterialTheme.colorScheme.onSurface

            val scanBg = if (isDarkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
            val scanFg = if (isDarkMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onPrimary
            val scanIconTint = if (isDarkMode) MaterialTheme.colorScheme.primary else scanFg

            val fabSize = 56.dp
            val fabLift = 10.dp
            val sideButtonWidth = 118.dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    color = dockColor,
                    shape = dockShape,
                    border = BorderStroke(1.dp, dockBorder),
                    tonalElevation = 3.dp,
                    shadowElevation = 14.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            SideTonalButton(
                                label = "Update",
                                icon = Icons.Default.EditNote,
                                containerColor = sideContainer,
                                contentColor = sideContent,
                                width = sideButtonWidth,
                                onClick = onUpdateClick
                            )
                        }

                        Spacer(modifier = Modifier.width(fabSize + 12.dp))

                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            SideTonalButton(
                                label = "Pickup",
                                icon = Icons.Default.FileUpload,
                                containerColor = sideContainer,
                                contentColor = sideContent,
                                width = sideButtonWidth,
                                onClick = onPickupClick
                            )
                        }
                    }
                }

                val fabInteraction = remember { MutableInteractionSource() }
                val fabPressed by fabInteraction.collectIsPressedAsState()
                val fabScale by animateFloatAsState(
                    targetValue = if (fabPressed) 0.96f else 1f,
                    label = "fabScale"
                )

                FloatingActionButton(
                    onClick = { showScanModeDialog = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-fabLift))
                        .size(fabSize)
                        .scale(fabScale),
                    containerColor = scanBg,
                    contentColor = scanFg,
                    shape = RoundedCornerShape(18.dp),
                    interactionSource = fabInteraction,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 10.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan",
                        modifier = Modifier.size(26.dp),
                        tint = scanIconTint
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isShowingCachedData) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    val updatedLabel = cachedDataUpdatedAt?.let {
                        SimpleDateFormat("HH:mm", Locale("id", "ID")).format(java.util.Date(it))
                    } ?: "sebelumnya"
                    Text(
                        text = "Data terakhir diperbarui $updatedLabel",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (filteredShipments.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Tidak ada pengiriman", color = Color.Gray)
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                    if (selectedTab == 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Selesai",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "$completedCount resi (1 hari terakhir)",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(filteredShipments, key = { it.awb }) { shipment ->
                                val isOverduePending = selectedTab == 2 && isShipmentOverdue(shipment)
                                ShipmentCard(
                                    shipment = shipment,
                                    courierName = userName,
                                    isCompleted = selectedTab == 1,
                                    highlightUrgent = isOverduePending,
                                    onClick = { onShipmentClick(shipment) },
                                    onMapClick = { onShipmentMapClick(shipment) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showScanModeDialog) {
        AlertDialog(
            onDismissRequest = { showScanModeDialog = false },
            icon = {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Pilih Mode Scan",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Pilih jenis scan yang ingin digunakan.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            showScanModeDialog = false
                            onContinuousScanClick()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SCAN MASUK")
                    }

                    OutlinedButton(
                        onClick = {
                            showScanModeDialog = false
                            onSearchScanClick()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SCAN DELIVERY")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showScanModeDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    if (showUpdateDialog && updateAvailable) {
        AlertDialog(
            onDismissRequest = { if (!isDownloading) showUpdateDialog = false },
            icon = {
                Icon(
                    Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "Update Tersedia",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Versi Terbaru",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = latestVersionName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    if (changelog.isNotBlank()) {
                        Text(
                            text = "Catatan Rilis:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = changelog,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!updateDownloadError.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = updateDownloadError,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    if (isUpdateDownloaded) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Download selesai",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "Klik Install untuk memasang update dari aplikasi.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    } else if (isDownloading) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Mengunduh update...",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (downloadProgressPercent != null) {
                                        Text(
                                            text = "${downloadProgressPercent.coerceIn(0, 100)}%",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (downloadProgressPercent != null) {
                                    LinearProgressIndicator(
                                        progress = { downloadProgressPercent.coerceIn(0, 100) / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isUpdateDownloaded) onInstallUpdate() else onDownloadUpdate()
                    },
                    enabled = !isDownloading
                ) {
                    val label = when {
                        isUpdateDownloaded -> "Install"
                        isDownloading -> "Mengunduh..."
                        else -> "Download"
                    }
                    Text(label)
                }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("Nanti")
                    }
                }
            }
        )
    }

    if (showNearbyCouriers) {
        NearbyCouriersDialog(
            onDismiss = { showNearbyCouriers = false },
        )
    }

    if (showSupportDialog) {
        AlertDialog(
            onDismissRequest = { showSupportDialog = false },
            icon = {
                Icon(
                    Icons.Default.HeadsetMic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Support") },
            text = {
                Column {
                    SUPPORT_CONTACTS.forEach { (name, phone) ->
                        ListItem(
                            headlineContent = { Text(name) },
                            supportingContent = { Text("+$phone") },
                            modifier = Modifier.clickable {
                                showSupportDialog = false
                                openWhatsAppContact(context, phone)
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSupportDialog = false }) {
                    Text("Tutup")
                }
            }
        )
    }

    if (showOverdueDialog && overdue14dCount > 0) {
        AlertDialog(
            onDismissRequest = onDismissOverdueDialog,
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF9800)
                )
            },
            title = { Text("Pending Lama") },
            text = {
                Text("Ada $overdue14dCount resi pending lebih dari 14 hari. Harap diselesaikan.")
            },
            confirmButton = {
                TextButton(onClick = onDismissOverdueDialog) {
                    Text("Tutup")
                }
            }
        )
    }
}

private fun openWhatsAppContact(context: Context, phoneE164: String) {
    val digits = phoneE164.replace(Regex("\\D"), "")
    if (digits.isBlank()) {
        Toast.makeText(context.applicationContext, "Nomor WhatsApp tidak valid", Toast.LENGTH_SHORT).show()
        return
    }

    val uri = "https://wa.me/$digits".toUri()
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK

    val whatsappIntents = listOf(
        Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp").addFlags(flags),
        Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp.w4b").addFlags(flags),
        Intent(Intent.ACTION_VIEW, uri).addFlags(flags)
    )

    val opened = whatsappIntents.any { intent ->
        runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    if (!opened) {
        Toast.makeText(context.applicationContext, "Tidak bisa membuka WhatsApp", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun ShipmentCard(
    shipment: ShipmentItem,
    courierName: String,
    isCompleted: Boolean = false,
    highlightUrgent: Boolean = false,
    onClick: () -> Unit,
    onMapClick: (() -> Unit)? = null
) {
    val isDarkMode = isSystemInDarkTheme()
    val recipientName = shipment.recipient_name.ifBlank { "Customer" }

    val cardColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val titleColor = MaterialTheme.colorScheme.onSurface
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant

    if (isCompleted) {
        val context = LocalContext.current
        val showWhatsApp = remember(shipment.recipient_phone) { shouldShowWhatsApp(shipment.recipient_phone) }
        val dateStr = remember(shipment.updated_at, shipment.created_at) {
            try {
                val sdfInput = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                sdfInput.timeZone = TimeZone.getTimeZone("UTC")
                val date = (shipment.updated_at ?: shipment.created_at).let { dateStr ->
                    val cleanDate = dateStr.replace("Z", "+0000").substringBefore(".")
                    sdfInput.parse(cleanDate)
                }
                date?.let {
                    val sdfOutput = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    sdfOutput.format(it)
                } ?: "Tanggal tidak tersedia"
            } catch (_: Exception) {
                "Tanggal tidak tersedia"
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(
                    enabled = shipment.current_status.lowercase() != "delivered",
                    onClick = onClick
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = shipment.awb,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusBadge(status = shipment.current_status, highlightUrgent = false)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dateStr,
                        fontSize = 11.sp,
                        color = if (isDarkMode) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = recipientName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (shipment.cod_amount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFFFFEBEE),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "COD Rp ${String.format(Locale.US, "%,.0f", shipment.cod_amount)}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = Color.Red,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (showWhatsApp) {
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                val formattedPhone = formatPhoneForWhatsApp(shipment.recipient_phone)
                                if (formattedPhone.isBlank()) return@OutlinedButton
                                val message =
                                    "Halo pak $recipientName, saya $courierName kurir bce express. Yg akan mengirimkan paket bapak. Apakah alamat sudah sesuai di alamat pak? Atau boleh di kirimkan share lokasi nya?"
                                val uri = "https://wa.me/$formattedPhone?text=${Uri.encode(message)}".toUri()
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                runCatching { context.startActivity(intent) }
                                    .onFailure {
                                        Toast.makeText(
                                            context.applicationContext,
                                            "Tidak bisa membuka WhatsApp",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(99.dp),
                            border = BorderStroke(1.dp, Color(0xFF2E7D32))
                        ) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF2E7D32)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("WA", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        return
    }

    val context = LocalContext.current
    val showWhatsApp = remember(shipment.recipient_phone) { shouldShowWhatsApp(shipment.recipient_phone) }
    val showMaps = remember(shipment.lat, shipment.lng, shipment.address) {
        shipment.lat != null && shipment.lng != null || shouldShowMaps(shipment.address)
    }
    val formattedPhone = remember(shipment.recipient_phone) { formatPhoneForWhatsApp(shipment.recipient_phone) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                enabled = shipment.current_status.lowercase() != "delivered",
                onClick = onClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = shipment.awb,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                StatusBadge(status = shipment.current_status, highlightUrgent = highlightUrgent)
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = shipment.recipient_name.ifBlank { "Auto Generated" },
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = shipment.address.ifBlank { "Auto Generated" },
                fontSize = 13.sp,
                color = subColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (shipment.cod_amount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFFFEBEE),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "COD: Rp ${String.format(Locale.US, "%,.0f", shipment.cod_amount)}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color.Red,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showWhatsApp) {
                    OutlinedButton(
                        onClick = {
                            if (formattedPhone.isBlank()) return@OutlinedButton
                            val message = "Halo pak $recipientName, saya $courierName kurir bce express. Yg akan mengirimkan paket bapak. Apakah alamat sudah sesuai di alamat pak? Atau boleh di kirimkan share lokasi nya?"
                            val uri = "https://wa.me/$formattedPhone?text=${Uri.encode(message)}".toUri()
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            runCatching { context.startActivity(intent) }
                                .onFailure {
                                    Toast.makeText(context.applicationContext, "Tidak bisa membuka WhatsApp", Toast.LENGTH_SHORT).show()
                                }
                        },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, Color(0xFF2E7D32))
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF2E7D32))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 12.sp, color = Color(0xFF2E7D32))
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (showMaps) {
                    OutlinedButton(
                        onClick = {
                            if (onMapClick != null) {
                                onMapClick()
                            } else {
                                val query = shipment.address.ifBlank {
                                    listOfNotNull(shipment.lat, shipment.lng).joinToString(",")
                                }
                                val mapsUri = "https://www.google.com/maps/search/?api=1&query=${Uri.encode(query)}".toUri()
                                val mapIntent = Intent(Intent.ACTION_VIEW, mapsUri)
                                runCatching { context.startActivity(mapIntent) }
                                    .onFailure {
                                        Toast.makeText(context.applicationContext, "Tidak bisa membuka Maps", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Maps", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (shipment.current_status.lowercase() != "delivered") {
                    Button(
                        onClick = onClick,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBC02D))
                    ) {
                        Text("Update", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String, highlightUrgent: Boolean = false) {
    val (color, label) = when (status.lowercase()) {
        "delivered" -> Color(0xFF4CAF50) to "SELESAI"
        "out_for_delivery" -> if (highlightUrgent) Color(0xFFFFC107) to "SEGERA ANTAR" else Color(0xFF2196F3) to "ANTAR"
        "failed" -> Color(0xFFF44336) to "GAGAL"
        "shipped" -> Color(0xFFFF9800) to "TRANSIT"
        else -> Color.Gray to status.uppercase()
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun shouldShowWhatsApp(phoneNumber: String): Boolean {
    val normalized = phoneNumber.trim()
    if (normalized.isBlank()) return false
    if (isAutoGeneratedValue(normalized) || normalized.equals("n/a", ignoreCase = true)) return false
    val digits = normalized.replace(Regex("\\D"), "")
    return digits.length >= 10
}

private fun formatPhoneForWhatsApp(phoneNumber: String): String {
    val digits = phoneNumber.replace(Regex("\\D"), "")
    if (digits.isBlank()) return ""
    return when {
        digits.startsWith("0") -> "62" + digits.drop(1)
        digits.startsWith("62") -> digits
        else -> "62$digits"
    }
}

private fun shouldShowMaps(address: String): Boolean {
    val normalized = address.trim()
    if (normalized.isBlank()) return false
    return !isAutoGeneratedValue(normalized)
}

private fun isAutoGeneratedValue(value: String): Boolean {
    val lower = value.lowercase()
    return lower.contains("auto generated") || lower.contains("autogenerated")
}

private fun formatCountModern(count: Int): String {
    if (count <= 0) return ""
    return if (count >= 1000) "1K" else count.toString()
}

@Composable
private fun SideTonalButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    width: Dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "sideButtonScale"
    )

    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .height(48.dp)
            .width(width)
            .scale(scale),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun isShipmentOverdue(shipment: ShipmentItem): Boolean {
    val raw = (shipment.updated_at ?: shipment.created_at).trim()
    if (raw.isBlank()) return false

    val epochMillis = parseApiDateToEpochMillis(raw) ?: return false
    val diff = System.currentTimeMillis() - epochMillis
    return diff >= 24L * 60L * 60L * 1000L
}

private fun parseApiDateToEpochMillis(value: String): Long? {
    val trimmed = value.trim()
    val candidates = listOf(
        trimmed,
        trimmed.replace("Z", "+0000"),
        trimmed.substringBefore(".")
    ).distinct()

    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd HH:mm:ss"
    )

    for (c in candidates) {
        for (p in patterns) {
            runCatching {
                val sdf = SimpleDateFormat(p, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val d = sdf.parse(c) ?: return@runCatching
                return d.time
            }
        }
    }
    return null
}
