package com.bce.courier.ui.screen

import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.scale
import com.bce.courier.tracking.CourierTrackingStatus
import com.bce.courier.tracking.CourierTrackingLocationStore
import com.bce.courier.ui.component.CourierDashboardHeader
import com.bce.courier.ui.component.CourierMotionSource
import com.bce.courier.ui.component.CourierMotionOrigin
import com.bce.courier.util.LocationHelper
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private enum class AssignmentSortMode { SCAN_ORDER, NEAREST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    userName: String,
    shipments: List<ShipmentItem>,
    assignedCount: Int,
    completedCount: Int,
    pendingCount: Int,
    pendingSyncCount: Int = 0,
    pointsScore: Int? = null,
    trackingStatus: CourierTrackingStatus = CourierTrackingStatus.OFF,
    overdue14dCount: Int = 0,
    showOverdueDialog: Boolean = false,
    isLoading: Boolean,
    isShowingCachedData: Boolean = false,
    cachedDataUpdatedAt: Long? = null,
    selectedTab: Int,
    updateAvailable: Boolean = false,
    onTabSelected: (Int) -> Unit,
    onRefresh: () -> Unit,
    onProfileClick: () -> Unit,
    onShipmentClick: (ShipmentItem) -> Unit,
    onShipmentMapClick: (ShipmentItem) -> Unit,
    onSearchScanClick: () -> Unit,
    onScanMenuClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onPickupClick: () -> Unit,
    onDismissOverdueDialog: () -> Unit = {}
) {
    val context = LocalContext.current
    val tabs = listOf("Assigned", "Completed", "Pending")
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val isDarkMode = isSystemInDarkTheme()
    var assignmentSortMode by rememberSaveable { mutableStateOf(AssignmentSortMode.SCAN_ORDER) }
    var showAssignmentSortMenu by remember { mutableStateOf(false) }
    var distanceByAwb by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }
    val geocodedDestinationCache = remember { mutableMapOf<String, Pair<Double, Double>?>() }
    var isResolvingDistances by remember { mutableStateOf(false) }
    var distanceSortMessage by remember { mutableStateOf<String?>(null) }

    var isOnline by remember { mutableStateOf(true) }
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

    LaunchedEffect(assignmentSortMode, selectedTab, shipments) {
        if (selectedTab != 0 || assignmentSortMode != AssignmentSortMode.NEAREST) return@LaunchedEffect

        isResolvingDistances = true
        distanceSortMessage = null
        val resolved = linkedMapOf<String, Float>()
        try {
            val locationHelper = LocationHelper(context.applicationContext)
            val courierLocation = CourierTrackingLocationStore.getFreshLocation(
                context = context.applicationContext,
                maxAgeMs = 60_000L,
            ) ?: locationHelper.getCurrentLocationSuspend()

            if (courierLocation == null) {
                distanceSortMessage = "Lokasi kurir belum tersedia"
            } else {
                shipments.forEach { shipment ->
                    val address = shipment.address.trim()
                    if (address.isBlank() || address.equals("Auto Generated", ignoreCase = true)) {
                        return@forEach
                    }
                    val destination = if (geocodedDestinationCache.containsKey(address)) {
                        geocodedDestinationCache[address]
                    } else {
                        locationHelper.geocodeAddressToLatLngSuspend(address).also {
                            geocodedDestinationCache[address] = it
                        }
                    } ?: return@forEach
                    val result = FloatArray(1)
                    Location.distanceBetween(
                        courierLocation.latitude,
                        courierLocation.longitude,
                        destination.first,
                        destination.second,
                        result,
                    )
                    resolved[shipment.awb] = result[0]
                }
                if (resolved.isEmpty()) {
                    distanceSortMessage = "Jarak alamat belum tersedia"
                }
            }
        } catch (_: SecurityException) {
            distanceSortMessage = "Izin lokasi diperlukan untuk urut jarak"
        } catch (_: Exception) {
            distanceSortMessage = "Jarak gagal dihitung"
        } finally {
            distanceByAwb = resolved
            isResolvingDistances = false
        }
    }

    val orderedShipments = remember(filteredShipments, selectedTab, assignmentSortMode, distanceByAwb) {
        if (selectedTab != 0 || assignmentSortMode == AssignmentSortMode.SCAN_ORDER) {
            filteredShipments
        } else {
            filteredShipments.withIndex()
                .sortedWith(
                    compareBy<IndexedValue<ShipmentItem>> { distanceByAwb[it.value.awb] == null }
                        .thenBy { distanceByAwb[it.value.awb] ?: Float.MAX_VALUE }
                        .thenBy { it.index }
                )
                .map { it.value }
        }
    }

    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                Column(
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    ),
                ) {
                    CourierDashboardHeader(
                        userName = userName,
                        isOnline = isOnline,
                        trackingStatus = trackingStatus,
                        pendingSyncCount = pendingSyncCount,
                        pointsScore = if (pendingCount > 0) pointsScore else null,
                        updateAvailable = updateAvailable,
                        isRefreshing = isLoading,
                        onProfileClick = onProfileClick,
                        onRefresh = onRefresh,
                    )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        placeholder = {
                            Text(
                                "Cari AWB / nama penerima",
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            CourierMotionSource(CourierMotionOrigin.Search, Modifier.size(48.dp)) { sharedModifier ->
                                IconButton(onClick = onSearchScanClick, modifier = sharedModifier.fillMaxSize()) {
                                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Cari dengan scan AWB", modifier = Modifier.size(22.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant else Color.White,
                            unfocusedContainerColor = if (isDarkMode) MaterialTheme.colorScheme.surfaceVariant else Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                    )

                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        indicator = { positions ->
                            if (selectedTab in positions.indices) {
                                TabRowDefaults.Indicator(
                                    modifier = Modifier.tabIndicatorOffset(positions[selectedTab]),
                                    height = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        },
                        divider = {},
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val count = when (index) {
                                0 -> assignedCount
                                1 -> completedCount
                                else -> pendingCount
                            }
                            val isSelected = selectedTab == index
                            Tab(
                                selected = isSelected,
                                onClick = { onTabSelected(index) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                selectedContentColor = MaterialTheme.colorScheme.onPrimary,
                                unselectedContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            title,
                                            modifier = Modifier.weight(1f, fill = false),
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        )
                                        if (count > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (isSelected) 0.22f else 0.12f),
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                            ) {
                                                Text(
                                                    formatCountModern(count),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                    }
                                },
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
                            CourierMotionSource(CourierMotionOrigin.Update, Modifier.width(sideButtonWidth).height(48.dp)) { sharedModifier ->
                                SideTonalButton(
                                    label = "Update",
                                    icon = Icons.Default.EditNote,
                                    containerColor = sideContainer,
                                    contentColor = sideContent,
                                    width = sideButtonWidth,
                                    onClick = onUpdateClick,
                                    modifier = sharedModifier,
                                )
                            }
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

                CourierMotionSource(
                    CourierMotionOrigin.Scan,
                    Modifier.align(Alignment.TopCenter).offset(y = -fabLift).size(fabSize),
                ) { sharedModifier ->
                    FloatingActionButton(
                        onClick = onScanMenuClick,
                        modifier = sharedModifier.fillMaxSize().scale(fabScale),
                        containerColor = scanBg,
                        contentColor = scanFg,
                        shape = RoundedCornerShape(18.dp),
                        interactionSource = fabInteraction,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp, pressedElevation = 10.dp),
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan", modifier = Modifier.size(26.dp), tint = scanIconTint)
                    }
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
                    if (selectedTab == 0) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Urutan assignment",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Box {
                                        TextButton(onClick = { showAssignmentSortMenu = true }) {
                                            if (isResolvingDistances) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            } else {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Sort,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                when (assignmentSortMode) {
                                                    AssignmentSortMode.SCAN_ORDER -> "Urutan scan"
                                                    AssignmentSortMode.NEAREST -> "Jarak terdekat"
                                                }
                                            )
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                        DropdownMenu(
                                            expanded = showAssignmentSortMenu,
                                            onDismissRequest = { showAssignmentSortMenu = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text("Urutan scan")
                                                        Text(
                                                            "Kembali ke urutan assignment awal",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                },
                                                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                                                onClick = {
                                                    assignmentSortMode = AssignmentSortMode.SCAN_ORDER
                                                    showAssignmentSortMenu = false
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text("Jarak terdekat")
                                                        Text(
                                                            "Dihitung dari alamat penerima",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                },
                                                leadingIcon = { Icon(Icons.Default.NearMe, contentDescription = null) },
                                                onClick = {
                                                    assignmentSortMode = AssignmentSortMode.NEAREST
                                                    showAssignmentSortMenu = false
                                                },
                                            )
                                        }
                                    }
                                }
                                distanceSortMessage?.takeIf {
                                    assignmentSortMode == AssignmentSortMode.NEAREST
                                }?.let { message ->
                                    Text(
                                        text = message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(orderedShipments, key = { it.awb }) { shipment ->
                                val isOverdueShipment = (selectedTab == 0 || selectedTab == 2) && isShipmentOverdue(shipment)
                                ShipmentCard(
                                    shipment = shipment,
                                    courierName = userName,
                                    isCompleted = selectedTab == 1,
                                    highlightUrgent = isOverdueShipment,
                                    distanceMeters = if (
                                        selectedTab == 0 && assignmentSortMode == AssignmentSortMode.NEAREST
                                    ) distanceByAwb[shipment.awb] else null,
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

@Composable
fun ShipmentCard(
    shipment: ShipmentItem,
    courierName: String,
    isCompleted: Boolean = false,
    highlightUrgent: Boolean = false,
    distanceMeters: Float? = null,
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
    val showMaps = remember(shipment.address) { shouldShowMaps(shipment.address) }
    val formattedPhone = remember(shipment.recipient_phone) { formatPhoneForWhatsApp(shipment.recipient_phone) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp)),
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
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                distanceMeters?.let { meters ->
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(99.dp),
                    ) {
                        Text(
                            text = formatDistance(meters),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
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
                                val query = shipment.address
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

private fun formatDistance(meters: Float): String = when {
    meters < 1_000f -> "${meters.toInt()} m"
    else -> String.format(Locale("id", "ID"), "%.1f km", meters / 1_000f)
}

@Composable
fun StatusBadge(status: String, highlightUrgent: Boolean = false) {
    val (color, label) = when (status.lowercase()) {
        "delivered" -> Color(0xFF4CAF50) to "SELESAI"
        "out_for_delivery" -> if (highlightUrgent) Color(0xFFFFC107) to "SEGERA ANTAR" else Color(0xFF2196F3) to "ANTAR"
        "failed" -> Color(0xFFF44336) to "GAGAL"
        "pending", "shipped" -> Color(0xFFFF9800) to "PENDING"
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "sideButtonScale"
    )

    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
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
