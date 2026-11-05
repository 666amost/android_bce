package com.bce.courier.debug

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bce.courier.data.model.DeliveryStatus
import com.bce.courier.data.model.ShipmentItem
import com.bce.courier.tracking.CourierTrackingStatus
import com.bce.courier.ui.component.*
import com.bce.courier.ui.screen.CourierAccountScreen
import com.bce.courier.ui.screen.DashboardScreen
import com.bce.courier.ui.screen.PickupScreen
import com.bce.courier.ui.theme.BCECourierTheme
import kotlinx.coroutines.delay

/** Uses the production dashboard, panels, and camera with sample data and no API mutations. */
class MotionPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BCECourierTheme {
                val motion = remember { CourierMotionState() }
                var panel by remember { mutableStateOf(CourierPanel.Hidden) }
                var manualScanner by remember { mutableStateOf(false) }
                var draft by remember { mutableStateOf("BCE172694JKT") }
                var selectedTab by remember { mutableIntStateOf(0) }
                var showPickupForm by remember { mutableStateOf(intent.getBooleanExtra("pickup", false)) }
                var showSyncQueue by remember { mutableStateOf(false) }
                var updatePreviewOpen by remember { mutableStateOf(false) }
                val updatePreviewLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    updatePreviewOpen = false
                }
                val closeRef = remember { arrayOf<CloseMotionPanel>({}) }
                fun open(origin: CourierMotionOrigin, target: CourierPanel) {
                    motion.open(origin)
                    manualScanner = false
                    panel = target
                }
                val sampleShipments = remember {
                    listOf(
                        ShipmentItem(awb = "BCE172694JKT", recipient_name = "Amos · Data contoh", address = "Jakarta", current_status = DeliveryStatus.OUT_FOR_DELIVERY),
                        ShipmentItem(awb = "BCE0172694JKT", recipient_name = "Contoh penerima", address = "Jakarta", current_status = DeliveryStatus.OUT_FOR_DELIVERY),
                    )
                }
                if (showPickupForm) {
                    Column(Modifier.fillMaxSize()) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text("PRATINJAU PICKUP · Data contoh", modifier = Modifier.fillMaxWidth().padding(8.dp))
                        }
                        Box(Modifier.weight(1f)) {
                            PickupScreen(
                                onBack = { showPickupForm = false },
                                onSubmit = {},
                                courierId = "pickup-ui-preview",
                            )
                        }
                    }
                    return@BCECourierTheme
                }
                Column(Modifier.fillMaxSize()) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("PRATINJAU TRANSISI · Data contoh", modifier = Modifier.fillMaxWidth().padding(8.dp))
                    }
                    Box(Modifier.weight(1f)) {
                        CourierMotionLayout(motion) {
                            DashboardScreen(
                                userName = "Kurir BCE",
                                shipments = if (selectedTab == 0) sampleShipments else emptyList(),
                                assignedCount = 2, completedCount = 0, pendingCount = 0,
                                trackingStatus = CourierTrackingStatus.OFF,
                                isLoading = false, selectedTab = selectedTab,
                                isActive = panel == CourierPanel.Hidden && !updatePreviewOpen,
                                onTabSelected = { selectedTab = it }, onRefresh = {},
                                onProfileClick = { open(CourierMotionOrigin.Profile, CourierPanel.Profile) },
                                onShipmentClick = { shipment ->
                                    updatePreviewOpen = true
                                    updatePreviewLauncher.launch(
                                        Intent(this@MotionPreviewActivity, DeliveryFeedbackPreviewActivity::class.java)
                                            .putExtra("awb", shipment.awb),
                                    )
                                }, onShipmentMapClick = {},
                                onSearchScanClick = { open(CourierMotionOrigin.Search, CourierPanel.Scanner) },
                                onScanMenuClick = { open(CourierMotionOrigin.Scan, CourierPanel.ScanMenu) },
                                onUpdateClick = { open(CourierMotionOrigin.Update, CourierPanel.Update) },
                                onPickupClick = { showPickupForm = true },
                            )
                            if (panel != CourierPanel.Hidden) {
                                CourierMotionPanel(
                                    panel = panel,
                                    onDismiss = { panel = CourierPanel.Hidden },
                                    onBack = if (manualScanner && panel == CourierPanel.Scanner) ({ panel = CourierPanel.Update }) else null,
                                    compactContent = { close ->
                                        SideEffect { closeRef[0] = close }
                                        if (panel == CourierPanel.Update) {
                                            BulkUpdateContent(
                                                onDismiss = { close {} }, onUpdate = {}, isProcessing = false, processingStatus = "",
                                                onScanClick = { manualScanner = true; panel = CourierPanel.Scanner },
                                                awbText = draft, onAwbTextChange = { draft = it },
                                            )
                                        } else {
                                            ScanModePicker(
                                                onIncoming = { panel = CourierPanel.Scanner },
                                                onDelivery = { panel = CourierPanel.Scanner },
                                                onDismiss = { close {} },
                                            )
                                        }
                                    },
                                ) { ready, close ->
                                    SideEffect { closeRef[0] = close }
                                    if (panel == CourierPanel.Profile) {
                                        CourierAccountScreen(
                                            userName = "Kurir BCE", userEmail = "", trackingStatus = CourierTrackingStatus.OFF,
                                            pendingSyncCount = 0, updateAvailable = false, isDownloadingUpdate = false,
                                            onBack = { close {} }, onNearbyCouriersClick = {}, onAppSettingsClick = {},
                                            onSyncQueueClick = { showSyncQueue = true },
                                            onClearCache = {}, onSupportClick = {}, onVersionClick = {}, onLogout = {},
                                        )
                                    } else {
                                        Surface(color = androidx.compose.ui.graphics.Color.Black, modifier = Modifier.fillMaxSize()) {
                                            Box(Modifier.fillMaxSize()) {
                                                ScannerView(isActive = ready, startPreview = ready, onBarcodeDetected = {})
                                                ScannerOverlay(borderColor = androidx.compose.ui.graphics.Color.White)
                                                ScannerToolbar(
                                                    title = "Pratinjau scan QR", subtitle = "Data contoh", isFlashOn = false, onFlash = {},
                                                    onClose = { if (manualScanner) panel = CourierPanel.Update else close {} },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (showSyncQueue) {
                    SyncQueueDialog(updates = emptyList(), onDismiss = { showSyncQueue = false }, onRetry = {})
                }
                LaunchedEffect(Unit) {
                    if (intent.getBooleanExtra("autoplay", false)) {
                        delay(2500)
                        repeat(2) {
                            open(CourierMotionOrigin.Profile, CourierPanel.Profile)
                            delay(1000); closeRef[0] {}; delay(500)
                            open(CourierMotionOrigin.Update, CourierPanel.Update)
                            delay(1000)
                            manualScanner = true; panel = CourierPanel.Scanner
                            delay(1800); panel = CourierPanel.Update
                            delay(700); closeRef[0] {}; delay(500)
                            open(CourierMotionOrigin.Scan, CourierPanel.ScanMenu)
                            delay(1000); closeRef[0] {}; delay(500)
                        }
                    }
                }
            }
        }
    }
}
