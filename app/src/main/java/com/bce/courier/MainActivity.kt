package com.bce.courier

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bce.courier.data.api.NetworkModule
import com.bce.courier.data.manager.SessionManager
import com.bce.courier.data.model.*
import com.bce.courier.tracking.CourierTrackingService
import com.bce.courier.tracking.CourierTrackingStatus
import com.bce.courier.ui.component.BulkUpdateDialog
import com.bce.courier.ui.component.ScannerView
import com.bce.courier.ui.component.ContinuousScannerDialog
import com.bce.courier.ui.component.ScannedItem
import com.bce.courier.ui.component.ScannerOverlay
import com.bce.courier.ui.component.ScannerOverlayScanEffect
import com.bce.courier.ui.component.ForceUpdateDialog
import com.bce.courier.ui.screen.DashboardScreen
import com.bce.courier.ui.screen.LoginScreen
import com.bce.courier.ui.screen.MapPreviewScreen
import com.bce.courier.ui.screen.PickupDashboardScreen
import com.bce.courier.ui.screen.PickupScreen
import com.bce.courier.ui.screen.UpdateShipmentScreen
import com.bce.courier.ui.theme.BCECourierTheme
import com.bce.courier.util.LocationHelper
import com.bce.courier.util.NetworkUtils
import com.bce.courier.util.SupabaseRealtimeSubscription
import com.bce.courier.util.StorageCleaner
import com.bce.courier.util.ContinuousScanSession
import com.bce.courier.util.UpdateDownloadResult
import com.bce.courier.util.UpdateInstallResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class ScanMode { NONE, SEARCH, CONTINUOUS, BULK_UPDATE }

private const val KEY_CORE_PERMISSIONS_REQUESTED = "core_permissions_requested"
private const val KEY_LAST_LOCATION_ENABLED = "last_location_enabled"
private const val KEY_LOCATION_DISABLED_PROMPT_SHOWN = "location_disabled_prompt_shown"

class MainActivity : ComponentActivity() {
    private lateinit var sessionManager: SessionManager
    private lateinit var locationHelper: LocationHelper
    private val permissionPrefs by lazy { getSharedPreferences("runtime_permissions", MODE_PRIVATE) }
    private var shouldRequestBackgroundLocationAfterLogin = false
    private var isLocationStateReceiverRegistered = false

    private val locationStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            syncLocationServicePromptState()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (::sessionManager.isInitialized) {
            syncCourierTracking()
            syncLocationServicePromptState(forceInitialize = true)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasTrackingLocationPermission(): Boolean {
        return hasLocationPermission() && hasBackgroundLocationPermission()
    }

    private fun syncCourierTracking() {
        val courierId = sessionManager.getUserId()
        if (sessionManager.isLoggedIn() && hasLocationPermission() && !courierId.isNullOrBlank()) {
            CourierTrackingService.start(this, courierId, sessionManager.getBestCourierName())
        } else {
            CourierTrackingService.stop(this)
        }
    }

    private fun requestBackgroundLocationPermissionAfterLoginIfNeeded() {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q || !hasLocationPermission() || hasBackgroundLocationPermission()) return
        requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    }

    private fun syncLocationServicePromptState(forceInitialize: Boolean = false) {
        if (!::sessionManager.isInitialized || !::locationHelper.isInitialized) return
        if (!sessionManager.isLoggedIn() || !hasLocationPermission()) return

        val isLocationEnabled = locationHelper.isLocationEnabled()
        val hasStoredState = permissionPrefs.contains(KEY_LAST_LOCATION_ENABLED)
        val previousLocationEnabled = permissionPrefs.getBoolean(KEY_LAST_LOCATION_ENABLED, isLocationEnabled)

        if (forceInitialize || !hasStoredState) {
            permissionPrefs.edit()
                .putBoolean(KEY_LAST_LOCATION_ENABLED, isLocationEnabled)
                .putBoolean(KEY_LOCATION_DISABLED_PROMPT_SHOWN, false)
                .apply()
            return
        }

        if (isLocationEnabled) {
            permissionPrefs.edit()
                .putBoolean(KEY_LAST_LOCATION_ENABLED, true)
                .putBoolean(KEY_LOCATION_DISABLED_PROMPT_SHOWN, false)
                .apply()
            return
        }

        val promptAlreadyShown = permissionPrefs.getBoolean(KEY_LOCATION_DISABLED_PROMPT_SHOWN, false)
        permissionPrefs.edit().putBoolean(KEY_LAST_LOCATION_ENABLED, false).apply()

        if (previousLocationEnabled && !promptAlreadyShown) {
            permissionPrefs.edit().putBoolean(KEY_LOCATION_DISABLED_PROMPT_SHOWN, true).apply()
            locationHelper.requestEnableLocation(this) { enabled ->
                if (enabled) {
                    syncLocationServicePromptState(forceInitialize = true)
                }
            }
        }
    }

    private fun playSound(resId: Int) {
        try {
            val mp = MediaPlayer.create(this, resId)
            mp.setOnCompletionListener { it.release() }
            mp.start()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error playing sound", e)
        }
    }

    private fun requestCorePermissions() {
        val needed = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        }

        if (!hasLocationPermission()) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (needed.isNotEmpty()) {
            if (!permissionPrefs.getBoolean(KEY_CORE_PERMISSIONS_REQUESTED, false)) {
                permissionPrefs.edit().putBoolean(KEY_CORE_PERMISSIONS_REQUESTED, true).apply()
                requestPermissionLauncher.launch(needed.distinct().toTypedArray())
            }
            return
        }
    }

    override fun onStart() {
        super.onStart()
        if (isLocationStateReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(LocationManager.MODE_CHANGED_ACTION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(locationStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(locationStateReceiver, filter)
        }
        isLocationStateReceiverRegistered = true
    }

    override fun onStop() {
        if (isLocationStateReceiverRegistered) {
            unregisterReceiver(locationStateReceiver)
            isLocationStateReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        Log.d("MainActivity", "onPause - app minimize/background")
    }

    override fun onResume() {
        super.onResume()
        syncLocationServicePromptState()
        Log.d("MainActivity", "onResume - app restored")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.d("MainActivity", "onNewIntent - reuse existing activity")
    }

    private val utcTz: TimeZone = TimeZone.getTimeZone("UTC")

    private fun parseUtcTimestamp(value: String?): Date? {
        if (value.isNullOrBlank()) return null
        return try {
            val cleanDate = value.replace("Z", "+0000").substringBefore(".")
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = utcTz }.parse(cleanDate)
        } catch (_: Exception) {
            null
        }
    }

    private fun startOfTodayUtc(): Date = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

    private fun oneDayAgoIso(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = utcTz }.format(cal.time)
    }

    private fun isAssignedToday(item: ShipmentItem, todayStart: Date): Boolean =
        item.current_status == "out_for_delivery" && parseUtcTimestamp(item.created_at)?.after(todayStart) == true

    private fun isPendingPrevious(item: ShipmentItem, todayStart: Date): Boolean =
        (item.current_status == "out_for_delivery" || item.current_status == "shipped") &&
            parseUtcTimestamp(item.created_at)?.before(todayStart) == true

    private fun buildShipmentFilters(userId: String): Map<String, String> = mapOf(
        "courier_id" to "eq.$userId",
        "or" to "(and(current_status.eq.delivered,updated_at.gte.${oneDayAgoIso()}),current_status.neq.delivered)"
    )

    private fun normalizeAwb(rawAwb: String): String? {
        val awb = rawAwb.trim().uppercase()
        return if (awb.startsWith("BCE") || awb.startsWith("BE")) awb else null
    }

    private fun cleanName(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    private fun emailLocalPart(email: String?): String? {
        val cleanEmail = cleanName(email) ?: return null
        val local = cleanEmail.substringBefore("@").trim()
        return local.takeIf { it.isNotEmpty() }
    }

    private suspend fun fetchUserNameFromUsersTable(userId: String): String? {
        return try {
            val userResponse = NetworkModule.apiService.getUserById("eq.$userId")
            if (userResponse.isSuccessful) {
                cleanName(userResponse.body()?.firstOrNull()?.name)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun resolveCourierName(userId: String, email: String?, metadataFullName: String?): String? {
        return fetchUserNameFromUsersTable(userId)
            ?: cleanName(metadataFullName)
            ?: emailLocalPart(email)
    }

    private data class FetchShipmentResult(
        val shipment: ShipmentItem?,
        val debug: String? = null
    )

    private suspend fun fetchShipmentByAwb(awb: String): FetchShipmentResult {
        return try {
            val userId = sessionManager.getUserId() ?: return FetchShipmentResult(null, debug = "missing_user_id")
            val cleanAwb = normalizeAwb(awb) ?: return FetchShipmentResult(null, debug = "invalid_awb")
            val filters = buildShipmentFilters(userId).toMutableMap()
            // Column name in Supabase is `awb_number` (not `awb`).
            filters["awb_number"] = "eq.$cleanAwb"
            val response = NetworkModule.apiService.getShipments(filters)
            if (response.isSuccessful) {
                FetchShipmentResult(response.body()?.firstOrNull())
            } else {
                val err = runCatching { response.errorBody()?.string() }.getOrNull()
                val debug = "getShipments failed code=${response.code()} body=${err.orEmpty()} filters=$filters"
                Log.w("MainActivity", "fetchShipmentByAwb failed: $debug")
                FetchShipmentResult(null, debug = debug)
            }
        } catch (e: Exception) {
            FetchShipmentResult(null, debug = e.message ?: e::class.java.simpleName)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContext = applicationContext

        // Initialize Network and Session
        NetworkModule.init(appContext)
        sessionManager = SessionManager(appContext)
        locationHelper = LocationHelper(appContext)

        val rememberedEmail = sessionManager.getRememberedEmail() ?: ""
        val rememberedPassword = sessionManager.getRememberedPassword() ?: ""
        val rememberChecked = sessionManager.isRememberMeEnabled()

        requestCorePermissions()
        syncCourierTracking()

        setContent {
            BCECourierTheme(dynamicColor = false) {
                val navController = rememberNavController()

                var isLoggedIn by rememberSaveable { mutableStateOf(sessionManager.isLoggedIn()) }
                var isLoginLoading by remember { mutableStateOf(false) }
                var loginErrorMessage by remember { mutableStateOf<String?>(null) }
                var trackingStatus by remember { mutableStateOf(CourierTrackingStatus.OFF) }

                LaunchedEffect(isLoggedIn) {
                    if (!isLoggedIn) {
                        trackingStatus = CourierTrackingStatus.OFF
                    } else {
                        if (shouldRequestBackgroundLocationAfterLogin) {
                            shouldRequestBackgroundLocationAfterLogin = false
                            requestBackgroundLocationPermissionAfterLoginIfNeeded()
                        }
                        syncLocationServicePromptState(forceInitialize = true)
                    }
                    syncCourierTracking()
                }

                LaunchedEffect(isLoggedIn) {
                    while (isActive && isLoggedIn) {
                        delay(30_000)
                        if (sessionManager.getAccessToken() == null) {
                            Log.d("MainActivity", "Session lost - attempting recovery")
                            // Coba auto re-login jika remembered credentials ada
                            val email = sessionManager.getRememberedEmail()
                            val password = sessionManager.getRememberedPassword()
                            if (!email.isNullOrBlank() && !password.isNullOrBlank()) {
                                try {
                                    val resp = NetworkModule.authApiService.login(
                                        request = LoginRequest(email, password)
                                    )
                                    if (resp.isSuccessful && resp.body() != null) {
                                        val body = resp.body()!!
                                        val resolvedName = resolveCourierName(
                                            userId = body.user.id,
                                            email = body.user.email,
                                            metadataFullName = body.user.user_metadata?.full_name
                                        )
                                        sessionManager.saveSession(
                                            token = body.access_token,
                                            email = body.user.email,
                                            userId = body.user.id,
                                            fullName = resolvedName,
                                            refreshToken = body.refresh_token
                                        )
                                        Log.d("MainActivity", "Auto re-login successful")
                                        continue
                                    }
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Auto re-login failed", e)
                                }
                            }
                            isLoggedIn = false
                            navController.navigate("login") {
                                popUpTo("dashboard") { inclusive = true }
                            }
                            break
                        }
                    }
                }

                // Refresh courier name from users table on startup
                LaunchedEffect(isLoggedIn) {
                    if (isLoggedIn) {
                        val userId = sessionManager.getUserId()
                        if (userId != null) {
                            try {
                                val fetchedName = fetchUserNameFromUsersTable(userId)
                                if (!fetchedName.isNullOrBlank() && fetchedName != sessionManager.getFullName()) {
                                    sessionManager.saveFullName(fetchedName)
                                    Log.d("MainActivity", "Refreshed courier name: $fetchedName")
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Failed to refresh courier name", e)
                            }
                        }
                    }
                }

                // Dashboard State
                var allShipments by remember { mutableStateOf<List<ShipmentItem>>(emptyList()) }
                var isDashboardLoading by remember { mutableStateOf(false) }
                var isShowingDashboardCache by remember { mutableStateOf(false) }
                var dashboardCacheUpdatedAt by remember { mutableStateOf<Long?>(null) }
                var selectedTab by remember { mutableIntStateOf(0) }

                // Tab Counts
                var assignedCount by remember { mutableIntStateOf(0) }
                var completedCount by remember { mutableIntStateOf(0) }
                var pendingCount by remember { mutableIntStateOf(0) }
                var overdue7dCount by remember { mutableIntStateOf(0) }
                var overdue14dCount by remember { mutableIntStateOf(0) }
                var pointsScore by remember { mutableStateOf<Int?>(null) }
                var showOverdueDialog by remember { mutableStateOf(false) }

                // Sync Count
                val pendingSyncCount by NetworkModule.shipmentRepository.observePendingCount()
                    .collectAsState(initial = 0)

                // New Features State
                var showBulkDialog by remember { mutableStateOf(false) }
                var scanMode by remember { mutableStateOf(ScanMode.NONE) }
                var scannedItems by remember { mutableStateOf<List<ScannedItem>>(emptyList()) }
                var lastScanStatus by remember { mutableStateOf("none") }
                var isLockedToDelivered by remember { mutableStateOf(false) }
                var isBulkProcessing by remember { mutableStateOf(false) }
                var bulkProcessingStatus by remember { mutableStateOf("") }
                var bulkAwbText by remember { mutableStateOf("") }
                var isSearchScanProcessing by remember { mutableStateOf(false) }

                val processingAwbs = remember { mutableStateListOf<String>() }
                val successfulAwbs = remember { ContinuousScanSession() }

                // Update Checker State
                var updateAvailable by remember { mutableStateOf(false) }
                var latestVersionName by remember { mutableStateOf("") }
                var updateChangelog by remember { mutableStateOf("") }
                var updateApkUrl by remember { mutableStateOf("") }
                var isDownloadingUpdate by remember { mutableStateOf(false) }
                var updateDownloadProgressPercent by remember { mutableStateOf<Int?>(null) }
                var isUpdateDownloaded by remember { mutableStateOf(false) }
                var updateDownloadError by remember { mutableStateOf<String?>(null) }
                val updateHelper = remember { com.bce.courier.util.UpdateHelper(this@MainActivity) }
                var forceUpdateRequired by remember { mutableStateOf(false) }
                var forceUpdateMessage by remember { mutableStateOf<String?>(null) }

                // Dynamic pickup master data (optional, from config)
                var pickupAgentListCentral by remember { mutableStateOf<List<String>>(emptyList()) }
                var pickupKotaWilayahPusat by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
                var pickupHargaPerKgMap by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
                var isPickupConfigLoading by remember { mutableStateOf(false) }

                DisposableEffect(updateHelper) {
                    onDispose { updateHelper.dispose() }
                }

                suspend fun applyVersioningConfig(versioning: VersioningConfig?) {
                    if (versioning == null) return

                    val currentCode = BuildConfig.VERSION_CODE
                    val latestCode = versioning.latest_version_code
                    val latestIsNewer = latestCode != null && latestCode > currentCode

                    val updateInfo = updateHelper.checkForUpdate(versioning)
                    if (updateInfo != null) {
                        updateAvailable = updateInfo.isAvailable
                        latestVersionName = updateInfo.latestVersionName
                        updateChangelog = updateInfo.changelog
                        updateApkUrl = updateInfo.apkUrl
                    } else {
                        updateAvailable = false
                        latestVersionName = ""
                        updateChangelog = ""
                        updateApkUrl = ""
                    }

                    val belowMin = currentCode < versioning.min_version_code
                    val shouldForce = belowMin || (versioning.force_update && latestIsNewer)

                    if (shouldForce) {
                        if (latestIsNewer && updateInfo != null) {
                            forceUpdateRequired = true
                            forceUpdateMessage = null
                        } else {
                            forceUpdateRequired = true
                            forceUpdateMessage = "Update wajib, tapi konfigurasi update belum lengkap. Hubungi admin."
                        }
                    } else {
                        forceUpdateRequired = false
                        forceUpdateMessage = null
                    }
                }

                // Check update policy on app start (even before login)
                LaunchedEffect(Unit) {
                    try {
                        val config = NetworkModule.shipmentRepository.getConfig()
                        applyVersioningConfig(config?.versioning)
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to check update policy", e)
                    }
                }

                // Load pickup config (after login) and refresh versioning
                LaunchedEffect(isLoggedIn) {
                    if (isLoggedIn) {
                        isPickupConfigLoading = true
                        try {
                            val config = NetworkModule.shipmentRepository.getConfig()
                            pickupAgentListCentral = config?.pickup?.agent_list_central
                                ?.map { it.trim() }
                                ?.filter { it.isNotEmpty() }
                                ?.distinct()
                                ?: emptyList()
                            pickupKotaWilayahPusat = config?.pickup?.kota_wilayah_pusat ?: emptyMap()
                            pickupHargaPerKgMap = config?.pickup?.harga_per_kg_map ?: emptyMap()
                            applyVersioningConfig(config?.versioning)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Failed to load config", e)
                        } finally {
                            isPickupConfigLoading = false
                        }
                    } else {
                        isPickupConfigLoading = false
                        pickupAgentListCentral = emptyList()
                        pickupKotaWilayahPusat = emptyMap()
                        pickupHargaPerKgMap = emptyMap()
                    }
                }

                val startDownloadUpdate = {
                    if (updateApkUrl.isNotBlank()) {
                        isDownloadingUpdate = true
                        updateDownloadError = null
                        isUpdateDownloaded = false
                        updateDownloadProgressPercent = 0

                        updateHelper.downloadApk(
                            scope = lifecycleScope,
                            apkUrl = updateApkUrl,
                            onProgress = { p -> updateDownloadProgressPercent = p.percent },
                            onComplete = { result ->
                                isDownloadingUpdate = false
                                when (result) {
                                    is UpdateDownloadResult.Success -> {
                                        isUpdateDownloaded = true
                                        updateDownloadProgressPercent = 100
                                    }
                                    is UpdateDownloadResult.Failed -> {
                                        updateDownloadError = result.reason ?: "Gagal mengunduh update"
                                        Toast.makeText(appContext, updateDownloadError, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }

                val startInstallUpdate = {
                    when (val result = updateHelper.installDownloadedApk()) {
                        is UpdateInstallResult.Started -> Unit
                        is UpdateInstallResult.NeedsPermission -> {
                            Toast.makeText(
                                appContext,
                                "Izinkan instalasi dari sumber tidak dikenal, lalu klik Install lagi.",
                                Toast.LENGTH_LONG
                            ).show()
                            startActivity(result.settingsIntent)
                        }
                        is UpdateInstallResult.Failed -> {
                            Toast.makeText(
                                appContext,
                                result.reason ?: "Gagal membuka installer",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                fun applyDashboardShipments(
                    body: List<ShipmentItem>,
                    fromCache: Boolean,
                    cacheUpdatedAt: Long? = null,
                ) {
                    allShipments = body
                    isShowingDashboardCache = fromCache
                    dashboardCacheUpdatedAt = cacheUpdatedAt

                    val todayStart = startOfTodayUtc()
                    assignedCount = body.count { isAssignedToday(it, todayStart) }
                    completedCount = body.count { it.current_status == "delivered" }
                    val pendingList = body.filter { isPendingPrevious(it, todayStart) }
                    pendingCount = pendingList.size

                    val nowMs = System.currentTimeMillis()
                    fun ageDays(item: ShipmentItem): Long {
                        val created = parseUtcTimestamp(item.created_at)?.time ?: return 0L
                        return (nowMs - created) / (24L * 60L * 60L * 1000L)
                    }

                    overdue7dCount = pendingList.count { ageDays(it) >= 7L }
                    overdue14dCount = pendingList.count { ageDays(it) >= 14L }
                    pointsScore = if (pendingCount > 0) {
                        (10 - pendingCount - overdue7dCount - 2 * overdue14dCount).coerceIn(1, 10)
                    } else {
                        null
                    }

                    val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        .apply { timeZone = utcTz }
                        .format(Date())
                    showOverdueDialog = overdue14dCount > 0 && (
                        sessionManager.getOverduePopupDismissedDate() != todayKey ||
                            overdue14dCount > sessionManager.getOverduePopupDismissedCount()
                        )
                }

                fun markDashboardDataStale() {
                    if (allShipments.isNotEmpty()) {
                        isShowingDashboardCache = true
                    }
                }

                val loadShipments: suspend (Boolean) -> Unit = { showLoading ->
                    if (showLoading) isDashboardLoading = true
                    val userId = sessionManager.getUserId()
                    if (userId != null) {
                        if (showLoading && allShipments.isEmpty()) {
                            try {
                                val cached = NetworkModule.shipmentRepository.getDashboardCache(userId)
                                if (cached.shipments.isNotEmpty()) {
                                    applyDashboardShipments(
                                        body = cached.shipments,
                                        fromCache = true,
                                        cacheUpdatedAt = cached.updatedAt,
                                    )
                                    isDashboardLoading = false
                                }
                            } catch (e: Exception) {
                                Log.w("MainActivity", "Unable to read dashboard cache", e)
                            }
                        }
                        try {
                            val response = NetworkModule.apiService.getShipments(buildShipmentFilters(userId))
                            if (response.isSuccessful) {
                                val body = response.body() ?: emptyList()
                                val cachedAt = System.currentTimeMillis()
                                applyDashboardShipments(body, fromCache = false, cacheUpdatedAt = cachedAt)
                                try {
                                    NetworkModule.shipmentRepository.replaceDashboardCache(
                                        courierId = userId,
                                        shipments = body,
                                        cachedAt = cachedAt,
                                    )
                                } catch (e: Exception) {
                                    Log.w("MainActivity", "Unable to update dashboard cache", e)
                                }

                                Log.d("MainActivity", "Fetched ${'$'}{body.size} shipments. A:${'$'}assignedCount C:${'$'}completedCount P:${'$'}pendingCount")
                            } else if (response.code() == 401) {
                                Log.e("MainActivity", "Fetch failed: 401 - attempting re-login recovery")
                                val email = sessionManager.getRememberedEmail()
                                val password = sessionManager.getRememberedPassword()
                                var recovered = false
                                if (!email.isNullOrBlank() && !password.isNullOrBlank()) {
                                    try {
                                        val resp = NetworkModule.authApiService.login(
                                            request = LoginRequest(email, password)
                                        )
                                        if (resp.isSuccessful && resp.body() != null) {
                                            val body = resp.body()!!
                                                val resolvedName = resolveCourierName(
                                                    userId = body.user.id,
                                                    email = body.user.email,
                                                    metadataFullName = body.user.user_metadata?.full_name
                                                )
                                            sessionManager.saveSession(
                                                token = body.access_token,
                                                email = body.user.email,
                                                userId = body.user.id,
                                                    fullName = resolvedName,
                                                refreshToken = body.refresh_token
                                            )
                                            Log.d("MainActivity", "Session recovered, retrying fetch")
                                            recovered = true
                                            val retryResponse = NetworkModule.apiService.getShipments(buildShipmentFilters(body.user.id))
                                            if (retryResponse.isSuccessful) {
                                                val retryBody = retryResponse.body() ?: emptyList()
                                                val cachedAt = System.currentTimeMillis()
                                                applyDashboardShipments(
                                                    retryBody,
                                                    fromCache = false,
                                                    cacheUpdatedAt = cachedAt,
                                                )
                                                try {
                                                    NetworkModule.shipmentRepository.replaceDashboardCache(
                                                        courierId = body.user.id,
                                                        shipments = retryBody,
                                                        cachedAt = cachedAt,
                                                    )
                                                } catch (e: Exception) {
                                                    Log.w("MainActivity", "Unable to update recovered dashboard cache", e)
                                                }
                                            } else {
                                                markDashboardDataStale()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "Recovery re-login failed", e)
                                    }
                                }
                                if (!recovered) {
                                    sessionManager.clearSession()
                                    isLoggedIn = false
                                    navController.navigate("login") {
                                        popUpTo("dashboard") { inclusive = true }
                                    }
                                }
                            } else {
                                Log.e("MainActivity", "Fetch failed: ${'$'}{response.code()}")
                                markDashboardDataStale()
                                Toast.makeText(appContext, "Gagal mengambil data", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Connection error", e)
                            markDashboardDataStale()
                        }
                    }
                    if (showLoading) isDashboardLoading = false
                }

                val fetchShipments: () -> Unit = {
                    lifecycleScope.launch { loadShipments(true) }
                }

                val shipmentsRealtime = remember { SupabaseRealtimeSubscription() }
                var realtimeRefreshJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val lifecycleOwner = LocalLifecycleOwner.current

                fun startShipmentsRealtimeIfNeeded() {
                    if (!isLoggedIn) return
                    if (currentRoute != "dashboard") return

                    val userId = sessionManager.getUserId() ?: return
                    val token = sessionManager.getAccessToken()

                    shipmentsRealtime.start(
                        scope = lifecycleScope,
                        table = "shipments",
                        filter = null,
                        events = listOf("UPDATE"),
                        accessToken = token
                    ) { change ->
                        val awb = change.awbNumber
                        val wasInList = awb != null && allShipments.any { it.awb == awb }

                        if (awb != null && change.newCourierId != null && change.newCourierId != userId && wasInList) {
                            allShipments = allShipments.filterNot { it.awb == awb }
                        }

                        val affectsCurrentCourier =
                            change.newCourierId == userId ||
                                change.oldCourierId == userId ||
                                wasInList

                        if (!affectsCurrentCourier) return@start

                        realtimeRefreshJob?.cancel()
                        realtimeRefreshJob = lifecycleScope.launch {
                            delay(400)
                            loadShipments(false)
                        }
                    }
                }

                fun stopShipmentsRealtime() {
                    realtimeRefreshJob?.cancel()
                    realtimeRefreshJob = null
                    shipmentsRealtime.stop()
                }

                DisposableEffect(isLoggedIn, currentRoute, lifecycleOwner) {
                    if (!isLoggedIn || currentRoute != "dashboard") {
                        stopShipmentsRealtime()
                        return@DisposableEffect onDispose { }
                    }

                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> startShipmentsRealtimeIfNeeded()
                            Lifecycle.Event.ON_PAUSE,
                            Lifecycle.Event.ON_STOP,
                            Lifecycle.Event.ON_DESTROY -> stopShipmentsRealtime()
                            else -> Unit
                        }
                    }

                    lifecycleOwner.lifecycle.addObserver(observer)

                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        startShipmentsRealtimeIfNeeded()
                    }

                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        stopShipmentsRealtime()
                    }
                }

                var previousSyncCount by remember { mutableIntStateOf(pendingSyncCount) }
                LaunchedEffect(pendingSyncCount) {
                    if (previousSyncCount > 0 && pendingSyncCount == 0) {
                        loadShipments(false)
                    }
                    previousSyncCount = pendingSyncCount
                }

                // Filter shipments for current tab display
                val displayedShipments = remember(allShipments, selectedTab) {
                    val todayStart = startOfTodayUtc()
                    when (selectedTab) {
                        0 -> allShipments.filter { isAssignedToday(it, todayStart) }
                        1 -> allShipments.filter { it.current_status == "delivered" }
                            .sortedByDescending { it.updated_at ?: it.created_at }
                        2 -> allShipments.filter { isPendingPrevious(it, todayStart) }
                        else -> allShipments
                    }
                }

                // Fetch shipments when logged in
                LaunchedEffect(isLoggedIn) {
                    if (isLoggedIn) {
                        loadShipments(true)
                    }
                }

                // Silent background refresh for ASSIGNED tab (tab 0) every 60 seconds (fallback)
                LaunchedEffect(selectedTab, isLoggedIn, showBulkDialog, scanMode) {
                    if (isLoggedIn && selectedTab == 0 && !showBulkDialog && scanMode == ScanMode.NONE) {
                        while (selectedTab == 0 && isLoggedIn && !showBulkDialog && scanMode == ScanMode.NONE) {
                            delay(60000)
                            if (selectedTab == 0 && isLoggedIn && !showBulkDialog && scanMode == ScanMode.NONE && !isDashboardLoading) {
                                loadShipments(false)
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = if (sessionManager.isLoggedIn()) "dashboard" else "login"
                    ) {

                        composable("login") {
                            LoginScreen(
                                onLoginClick = { email, password, remember ->
                                    isLoginLoading = true
                                    loginErrorMessage = null
                                    lifecycleScope.launch {
                                        try {
                                            Log.d("MainActivity", "Login attempt: remember=$remember")
                                            val response = NetworkModule.authApiService.login(
                                                request = LoginRequest(email, password)
                                            )
                                            if (response.isSuccessful && response.body() != null) {
                                                Log.d("MainActivity", "Login success: ${response.code()}")
                                                val body = response.body()!!

                                                val courierName = resolveCourierName(
                                                    userId = body.user.id,
                                                    email = body.user.email,
                                                    metadataFullName = body.user.user_metadata?.full_name
                                                )

                                                Log.d("MainActivity", "Saving session to EncryptedSharedPreferences...")
                                                sessionManager.saveSession(
                                                    token = body.access_token,
                                                    email = body.user.email,
                                                    userId = body.user.id,
                                                    fullName = courierName,
                                                    refreshToken = body.refresh_token
                                                )

                                                sessionManager.saveRememberedCredentials(email, password, remember)
                                                Log.d("MainActivity", "Session saved, navigating to dashboard")

                                                shouldRequestBackgroundLocationAfterLogin = true
                                                isLoggedIn = true
                                                navController.navigate("dashboard") {
                                                    popUpTo("login") { inclusive = true }
                                                }
                                            } else {
                                                val errorBody = runCatching { response.errorBody()?.string() }.getOrNull()
                                                Log.e("MainActivity", "Login failed: code=${response.code()} message=${response.message()} body=$errorBody")
                                                loginErrorMessage = "Email atau password salah"
                                            }
                                        } catch (e: Exception) {
                                            Log.e("MainActivity", "Login exception", e)
                                            loginErrorMessage = "Terjadi kesalahan jaringan"
                                        } finally {
                                            isLoginLoading = false
                                        }
                                    }
                                },
                                isLoading = isLoginLoading,
                                errorMessage = loginErrorMessage,
                                initialEmail = rememberedEmail,
                                initialPassword = rememberedPassword,
                                initialRemember = rememberChecked
                            )
                        }
                        
                        composable("dashboard") {

                            DisposableEffect(Unit) {
                                val receiver = object : BroadcastReceiver() {
                                    override fun onReceive(context: Context?, intent: Intent?) {
                                        trackingStatus = CourierTrackingStatus.fromValue(
                                            intent?.getStringExtra(CourierTrackingService.EXTRA_TRACKING_STATUS)
                                        )
                                    }
                                }
                                val filter = IntentFilter(CourierTrackingService.ACTION_TRACKING_STATUS)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
                                } else {
                                    @Suppress("UnspecifiedRegisterReceiverFlag")
                                    registerReceiver(receiver, filter)
                                }
                                onDispose {
                                    unregisterReceiver(receiver)
                                }
                            }

                            DisposableEffect(Unit) {
                                val receiver = object : BroadcastReceiver() {
                                    override fun onReceive(context: Context?, intent: Intent?) {
                                        fetchShipments()
                                    }
                                }
                                val filter = IntentFilter("com.bce.courier.SYNC_COMPLETED")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
                                } else {
                                    @Suppress("UnspecifiedRegisterReceiverFlag")
                                    registerReceiver(receiver, filter)
                                }
                                onDispose {
                                    unregisterReceiver(receiver)
                                }
                            }

                            DashboardScreen(
                                userName = sessionManager.getBestCourierName(),
                                shipments = displayedShipments,
                                assignedCount = assignedCount,
                                completedCount = completedCount,
                                pendingCount = pendingCount,
                                pendingSyncCount = pendingSyncCount,
                                trackingStatus = trackingStatus,
                                pointsScore = pointsScore,
                                overdue14dCount = overdue14dCount,
                                showOverdueDialog = showOverdueDialog,
                                isLoading = isDashboardLoading,
                                isShowingCachedData = isShowingDashboardCache,
                                cachedDataUpdatedAt = dashboardCacheUpdatedAt,
                                selectedTab = selectedTab,
                                updateAvailable = updateAvailable,
                                latestVersionName = latestVersionName,
                                changelog = updateChangelog,
                                isDownloading = isDownloadingUpdate,
                                downloadProgressPercent = updateDownloadProgressPercent,
                                isUpdateDownloaded = isUpdateDownloaded,
                                updateDownloadError = updateDownloadError,
                                onTabSelected = { selectedTab = it },
                                onRefresh = { fetchShipments() },
                                onLogout = {
                                    sessionManager.clearSession()
                                    isLoggedIn = false
                                    navController.navigate("login") {
                                        popUpTo("dashboard") { inclusive = true }
                                    }
                                },
                                onShipmentClick = { shipmentItem ->
                                    isLockedToDelivered = false
                                    val normalized = normalizeAwb(shipmentItem.awb) ?: shipmentItem.awb
                                    navController.navigate("update_shipment/$normalized")
                                },
                                onShipmentMapClick = { shipmentItem ->
                                    val normalized = normalizeAwb(shipmentItem.awb) ?: shipmentItem.awb
                                    navController.navigate("map_preview/$normalized")
                                },
                                onSearchScanClick = { 
                                    scanMode = ScanMode.SEARCH 
                                },
                                onContinuousScanClick = { 
                                    scannedItems = emptyList()
                                    successfulAwbs.clear()
                                    scanMode = ScanMode.CONTINUOUS 
                                },
                                onUpdateClick = { showBulkDialog = true },
                                onPickupClick = { navController.navigate("pickup") },
                                 onClearStorage = {
                                     val result = StorageCleaner.clearAppStorage(appContext)
                                     Toast.makeText(
                                         appContext,
                                         "Cache dibersihkan: ${result.deletedFiles} file",
                                         Toast.LENGTH_SHORT
                                     ).show()
                                 },
                                onDismissOverdueDialog = {
                                     val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = utcTz }.format(Date())
                                     sessionManager.setOverduePopupDismissed(todayKey, overdue14dCount)
                                     showOverdueDialog = false
                                 },
                                onDownloadUpdate = {
                                    startDownloadUpdate()
                                },
                                onInstallUpdate = {
                                    startInstallUpdate()
                                }
                            )

                            if (showBulkDialog) {
                                BulkUpdateDialog(
                                    onDismiss = { showBulkDialog = false },
                                    onUpdate = { awbs ->
                                        lifecycleScope.launch {
                                            isBulkProcessing = true
                                            val userId = sessionManager.getUserId()
                                            if (userId == null) {
                                                bulkProcessingStatus = "Sesi berakhir, silakan login ulang"
                                                playSound(R.raw.scan_error)
                                                isBulkProcessing = false
                                                return@launch
                                            }
                                            val courierName = sessionManager.getBestCourierName()
                                            var successCount = 0
                                            try {
                                                awbs.forEachIndexed { index, awb ->
                                                    bulkProcessingStatus = "Updating $awb (${index + 1}/${awbs.size})..."
                                                    val result = NetworkModule.shipmentRepository.assignShipmentToCourier(awb, userId, courierName)
                                                    if (
                                                        result is AssignmentOutcome.NewAssignment ||
                                                        result is AssignmentOutcome.Transferred ||
                                                        result is AssignmentOutcome.AlreadyMine
                                                    ) {
                                                        successCount++
                                                    }
                                                    delay(50)
                                                }
                                                bulkProcessingStatus = "Berhasil memperbarui $successCount resi"
                                                playSound(R.raw.scan_success)
                                                delay(1000)
                                                showBulkDialog = false
                                                bulkAwbText = ""
                                                loadShipments(false)
                                            } catch (e: Exception) {
                                                bulkProcessingStatus = "Error: ${e.message}"
                                                playSound(R.raw.scan_error)
                                            } finally {
                                                isBulkProcessing = false
                                                bulkProcessingStatus = ""
                                            }
                                        }
                                    },
                                    isProcessing = isBulkProcessing,
                                    processingStatus = bulkProcessingStatus,
                                    onScanClick = {
                                        showBulkDialog = false
                                        scanMode = ScanMode.BULK_UPDATE
                                    },
                                    awbText = bulkAwbText,
                                    onAwbTextChange = { bulkAwbText = it }
                                )
                            }

                            if (scanMode == ScanMode.SEARCH) {
                                var searchScanStatus by remember { mutableStateOf("none") }
                                val searchBorderColor by animateColorAsState(
                                    targetValue = when (searchScanStatus) {
                                        "success" -> Color(0xFF4CAF50)
                                        "error" -> Color(0xFFF44336)
                                        else -> Color.White.copy(alpha = 0.5f)
                                    },
                                    label = "SearchBorderColor"
                                )

                                var isFlashOn by remember { mutableStateOf(false) }

                                Dialog(
                                    onDismissRequest = {
                                        if (!isSearchScanProcessing) scanMode = ScanMode.NONE
                                    },
                                    properties = DialogProperties(usePlatformDefaultWidth = false)
                                ) {
                                    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            ScannerView(
                                                isFlashOn = isFlashOn,
                                                onBarcodeDetected = { rawAwb ->
                                                    if (isSearchScanProcessing) return@ScannerView

                                                    val awb = normalizeAwb(rawAwb)
                                                    if (awb == null) {
                                                        searchScanStatus = "error"
                                                        playSound(R.raw.scan_error)
                                                        Toast.makeText(appContext, "Format AWB tidak valid (harus BE/BCE)", Toast.LENGTH_SHORT).show()
                                                        return@ScannerView
                                                    }

                                                    isSearchScanProcessing = true
                                                    lifecycleScope.launch {
                                                        try {
                                                            searchScanStatus = "loading"

                                                            val userId = sessionManager.getUserId()
                                                            if (userId.isNullOrBlank()) {
                                                                searchScanStatus = "error"
                                                                playSound(R.raw.scan_error)
                                                                Toast.makeText(appContext, "Sesi berakhir, silakan login ulang", Toast.LENGTH_SHORT).show()
                                                                return@launch
                                                            }

                                                            val courierName = sessionManager.getBestCourierName()

                                                            val cached = allShipments.find { it.awb == awb }
                                                            if (cached?.current_status == "delivered") {
                                                                searchScanStatus = "error"
                                                                playSound(R.raw.scan_error)
                                                                Toast.makeText(
                                                                    appContext,
                                                                    "Resi sudah DELIVERED dan tidak bisa discan lagi",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                                return@launch
                                                            }

                                                            if (cached == null) {
                                                                val result = NetworkModule.shipmentRepository.assignShipmentToCourier(awb, userId, courierName)
                                                                when (result) {
                                                                    is AssignmentOutcome.Delivered -> {
                                                                        searchScanStatus = "error"
                                                                        playSound(R.raw.scan_error)
                                                                        Toast.makeText(
                                                                            appContext,
                                                                            "Resi sudah DELIVERED dan tidak bisa discan lagi",
                                                                            Toast.LENGTH_LONG
                                                                        ).show()
                                                                        return@launch
                                                                    }
                                                                    is AssignmentOutcome.Failed -> {
                                                                        searchScanStatus = "error"
                                                                        playSound(R.raw.scan_error)
                                                                        Toast.makeText(
                                                                            appContext,
                                                                            "Gagal menambahkan resi: ${result.message}",
                                                                            Toast.LENGTH_LONG
                                                                        ).show()
                                                                        return@launch
                                                                    }
                                                                    else -> Unit
                                                                }
                                                            }

                                                            searchScanStatus = "success"
                                                            playSound(R.raw.scan_success)
                                                            scanMode = ScanMode.NONE
                                                            isLockedToDelivered = (cached?.current_status == "out_for_delivery")
                                                            navController.navigate("update_shipment/$awb")
                                                            loadShipments(false)
                                                        } catch (e: Exception) {
                                                            searchScanStatus = "error"
                                                            playSound(R.raw.scan_error)
                                                            Log.e("MainActivity", "Error in search scan", e)
                                                            Toast.makeText(appContext, "Terjadi kesalahan: ${'$'}{e.message}", Toast.LENGTH_LONG).show()
                                                        } finally {
                                                            isSearchScanProcessing = false
                                                        }
                                                    }
                                                }
                                            )

                                            ScannerOverlay(
                                                borderColor = searchBorderColor,
                                                scanEffect = ScannerOverlayScanEffect.GoPay
                                            )
                                            
                                            // Header
                                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                                Row(
                                                    modifier = Modifier.align(Alignment.TopEnd),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    IconButton(
                                                        onClick = { isFlashOn = !isFlashOn },
                                                        modifier = Modifier
                                                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                                                    ) {
                                                        Icon(
                                                            if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                                            contentDescription = "Toggle Flash",
                                                            tint = if (isFlashOn) Color.Yellow else Color.White
                                                        )
                                                    }
                                                    
                                                    IconButton(
                                                        onClick = { scanMode = ScanMode.NONE },
                                                        modifier = Modifier
                                                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                                                    }
                                                }
                                                
                                                Column(modifier = Modifier.align(Alignment.TopStart)) {
                                                    Text("Cari & Update AWB", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                                    Text("Scan resi untuk update status", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (scanMode == ScanMode.BULK_UPDATE) {
                                var bulkUpdateScanStatus by remember { mutableStateOf("none") }
                                val bulkUpdateBorderColor by animateColorAsState(
                                    targetValue = when (bulkUpdateScanStatus) {
                                        "success" -> Color(0xFF4CAF50)
                                        "error" -> Color(0xFFF44336)
                                        else -> Color.White.copy(alpha = 0.5f)
                                    },
                                    label = "BulkUpdateBorderColor"
                                )
                                var isFlashOn by remember { mutableStateOf(false) }

                                Dialog(
                                    onDismissRequest = {
                                        scanMode = ScanMode.NONE
                                        showBulkDialog = true
                                    },
                                    properties = DialogProperties(usePlatformDefaultWidth = false)
                                ) {
                                    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            ScannerView(
                                                isFlashOn = isFlashOn,
                                                onBarcodeDetected = { rawAwb ->
                                                    val awb = normalizeAwb(rawAwb)
                                                    if (awb == null) {
                                                        bulkUpdateScanStatus = "error"
                                                        playSound(R.raw.scan_error)
                                                        return@ScannerView
                                                    }

                                                    bulkUpdateScanStatus = "success"
                                                    playSound(R.raw.scan_success)

                                                    val currentAwbs = bulkAwbText.split(Regex("[,\\s\\n]+")).filter { it.isNotBlank() }
                                                    if (!currentAwbs.contains(awb)) {
                                                        bulkAwbText = if (bulkAwbText.isBlank()) {
                                                            awb
                                                        } else {
                                                            bulkAwbText + "\n" + awb
                                                        }
                                                    }

                                                    lifecycleScope.launch {
                                                        delay(300)
                                                        bulkUpdateScanStatus = "none"
                                                    }
                                                }
                                            )

                                            ScannerOverlay(
                                                borderColor = bulkUpdateBorderColor,
                                                scanEffect = ScannerOverlayScanEffect.GoPay
                                            )

                                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                                Row(
                                                    modifier = Modifier.align(Alignment.TopEnd),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    IconButton(
                                                        onClick = { isFlashOn = !isFlashOn },
                                                        modifier = Modifier
                                                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                                                    ) {
                                                        Icon(
                                                            if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                                            contentDescription = "Toggle Flash",
                                                            tint = if (isFlashOn) Color.Yellow else Color.White
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            scanMode = ScanMode.NONE
                                                            showBulkDialog = true
                                                        },
                                                        modifier = Modifier
                                                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                                                    }
                                                }
                                                Column(modifier = Modifier.align(Alignment.TopStart)) {
                                                    Text("Scan QR - Bulk Update", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                                    Text("Scan resi untuk mengisi field", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (scanMode == ScanMode.CONTINUOUS) {
                                ContinuousScannerDialog(
                                    onDismiss = { 
                                        scanMode = ScanMode.NONE
                                        lastScanStatus = "none"
                                        lifecycleScope.launch { loadShipments(false) }
                                    },
                                    onBarcodeDetected = { rawAwb ->
                                        val awb = normalizeAwb(rawAwb)
                                        if (awb == null) {
                                            lastScanStatus = "error"
                                            playSound(R.raw.scan_error)
                                            return@ContinuousScannerDialog
                                        }

                                        // Skip if currently processing this AWB
                                        if (processingAwbs.contains(awb)) return@ContinuousScannerDialog

                                        // Reset status flash
                                        lastScanStatus = "none"
                                        
                                        if (successfulAwbs.contains(awb)) {
                                            lastScanStatus = "duplicate"
                                            playSound(R.raw.scan_error)
                                            val item = ScannedItem(
                                                awb = awb,
                                                status = "duplicate",
                                                message = "Sudah discan di sesi ini"
                                            )
                                            scannedItems = listOf(item) + scannedItems.filter { it.awb != awb }
                                            return@ContinuousScannerDialog
                                        }

                                        processingAwbs.add(awb)
                                        lifecycleScope.launch {
                                            try {
                                                val userId = sessionManager.getUserId()
                                                if (userId.isNullOrBlank()) {
                                                    lastScanStatus = "error"
                                                    playSound(R.raw.scan_error)
                                                    scannedItems = listOf(
                                                        ScannedItem(awb, "error", "Sesi berakhir, silakan login ulang")
                                                    ) + scannedItems.filter { it.awb != awb }
                                                    return@launch
                                                }

                                                val courierName = sessionManager.getBestCourierName()
                                                when (
                                                    val result = NetworkModule.shipmentRepository.assignShipmentToCourier(
                                                        awb,
                                                        userId,
                                                        courierName
                                                    )
                                                ) {
                                                    is AssignmentOutcome.NewAssignment -> {
                                                        successfulAwbs.markSuccessful(awb)
                                                        lastScanStatus = "success"
                                                        playSound(R.raw.scan_success)
                                                        scannedItems = listOf(
                                                            ScannedItem(awb, "success", "Berhasil masuk")
                                                        ) + scannedItems.filter { it.awb != awb }
                                                    }
                                                    is AssignmentOutcome.Transferred -> {
                                                        successfulAwbs.markSuccessful(awb)
                                                        lastScanStatus = "transferred"
                                                        playSound(R.raw.scan_success)
                                                        scannedItems = listOf(
                                                            ScannedItem(awb, "transferred", "Dialihkan ke Anda")
                                                        ) + scannedItems.filter { it.awb != awb }
                                                    }
                                                    is AssignmentOutcome.AlreadyMine -> {
                                                        successfulAwbs.markSuccessful(awb)
                                                        lastScanStatus = "duplicate"
                                                        playSound(R.raw.scan_error)
                                                        scannedItems = listOf(
                                                            ScannedItem(awb, "duplicate", "Sudah berada pada Anda")
                                                        ) + scannedItems.filter { it.awb != awb }
                                                    }
                                                    is AssignmentOutcome.Delivered -> {
                                                        lastScanStatus = "error"
                                                        playSound(R.raw.scan_error)
                                                        scannedItems = listOf(
                                                            ScannedItem(awb, "error", "AWB sudah delivered")
                                                        ) + scannedItems.filter { it.awb != awb }
                                                    }
                                                    is AssignmentOutcome.Failed -> {
                                                        lastScanStatus = "error"
                                                        playSound(R.raw.scan_error)
                                                        scannedItems = listOf(
                                                            ScannedItem(awb, "error", result.message)
                                                        ) + scannedItems.filter { it.awb != awb }
                                                    }
                                                }
                                            } finally {
                                                processingAwbs.remove(awb)
                                            }

                                            delay(1000)
                                            if (lastScanStatus != "none") {
                                                lastScanStatus = "none"
                                            }
                                        }
                                    },
                                    scannedItems = scannedItems,
                                    lastScanStatus = lastScanStatus
                                )
                            }
                        }

                        composable("update_shipment/{awb}") { backStackEntry ->
                            val awb = backStackEntry.arguments?.getString("awb")
                            var shipment by remember(awb) { mutableStateOf<ShipmentItem?>(null) }
                            var isResolving by remember(awb) { mutableStateOf(false) }
                            var resolveAttempted by remember(awb) { mutableStateOf(false) }
                            var retryCount by remember(awb) { mutableIntStateOf(0) }
                            var showReportDialog by remember(awb) { mutableStateOf(false) }
                            var lastResolveDebug by remember(awb) { mutableStateOf<String?>(null) }
                            var isUpdatingStatus by remember { mutableStateOf(false) }

                            LaunchedEffect(awb, allShipments, resolveAttempted) {
                                if (awb.isNullOrBlank()) return@LaunchedEffect
                                val cached = allShipments.find { it.awb == awb }
                                if (cached != null) {
                                    shipment = cached
                                    return@LaunchedEffect
                                }

                                if (!resolveAttempted && !isResolving) {
                                    isResolving = true
                                    lastResolveDebug = null
                                    val fetched = fetchShipmentByAwb(awb)
                                    lastResolveDebug = fetched.debug
                                    if (fetched.shipment != null) {
                                        shipment = fetched.shipment
                                    }
                                    resolveAttempted = true
                                    isResolving = false
                                }
                            }

                            val resolvedShipment = shipment

                            LaunchedEffect(resolvedShipment, isResolving, resolveAttempted, retryCount) {
                                if (resolvedShipment == null && resolveAttempted && !isResolving && retryCount >= 2) {
                                    showReportDialog = true
                                }
                            }

                            if (resolvedShipment != null) {
                                UpdateShipmentScreen(
                                    shipment = resolvedShipment,
                                    onBack = { navController.popBackStack() },
                                    onUpdate = { status, notes, location, photo, isCameraPhoto ->
                                        lifecycleScope.launch {
                                            isUpdatingStatus = true
                                            try {
                                                if (!locationHelper.isLocationEnabled()) {
                                                    locationHelper.requestEnableLocation(this@MainActivity) { }
                                                }

                                                var photoPath: String? = null
                                                if (photo != null) {
                                                    val photoFile = File(cacheDir, "pending_${'$'}{resolvedShipment.awb}_${'$'}{System.currentTimeMillis()}.jpg")
                                                    photoPath = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                        try {
                                                            contentResolver.openInputStream(photo)?.use { input ->
                                                                photoFile.outputStream().use { output ->
                                                                    input.copyTo(output)
                                                                }
                                                            }
                                                            if (isCameraPhoto) {
                                                                runCatching { contentResolver.delete(photo, null, null) }
                                                            }
                                                            photoFile.absolutePath
                                                        } catch (e: Exception) {
                                                            Log.e("MainActivity", "Failed to save photo locally", e)
                                                            null
                                                        }
                                                    }
                                                }

                                                val courierId = sessionManager.getUserId() ?: "unknown"
                                                val courierName = sessionManager.getBestCourierName()

                                                val isOnline = NetworkUtils.isOnline(this@MainActivity)

                                                val locationAddress: String? = if (isOnline && location?.latitude != null) {
                                                    withTimeoutOrNull(2000L) {
                                                        locationHelper.getAddressFromLocationSuspend(
                                                            location.latitude,
                                                            location.longitude
                                                        )
                                                    }
                                                } else {
                                                    null
                                                }

                                                 val result = NetworkModule.shipmentRepository.updateShipment(
                                                     awb = resolvedShipment.awb,
                                                     status = status,
                                                     notes = notes,
                                                     lat = location?.latitude,
                                                     lng = location?.longitude,
                                                     photo = photoPath,
                                                     courierName = courierName,
                                                     courierId = courierId,
                                                     armada = "motor",
                                                     platArmada = "BCEJKT",
                                                     locationAddress = locationAddress
                                                 )

                                                if (result.isSuccess) {
                                                    if (status.lowercase() == "delivered") {
                                                        playSound(R.raw.scan_delivered)
                                                    }
                                                    Toast.makeText(appContext, "Berhasil update status", Toast.LENGTH_SHORT).show()
                                                    Log.d("MainActivity", "Successfully updated shipment $awb to status $status")
                                                    loadShipments(false)
                                                    navController.popBackStack()
                                                } else {
                                                    val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                                                    Log.e("MainActivity", "Failed to update shipment $awb: $errorMessage")
                                                    Toast.makeText(appContext, "Gagal update: $errorMessage", Toast.LENGTH_LONG).show()
                                                }
                                            } catch (e: Exception) {
                                                Log.e("MainActivity", "Update shipment error", e)
                                                Toast.makeText(appContext, "Error: ${'$'}{e.message}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isUpdatingStatus = false
                                            }
                                        }
                                    },
                                    isUpdating = isUpdatingStatus,
                                    isLockedToDelivered = isLockedToDelivered,
                                    onRefresh = {
                                        lifecycleScope.launch {
                                            loadShipments(false)
                                        }
                                    }
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        if (isResolving) {
                                            CircularProgressIndicator()
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "Memuat data resi...",
                                                fontSize = 14.sp,
                                                color = Color.Gray
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Error,
                                                contentDescription = null,
                                                modifier = Modifier.size(64.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "Resi tidak ditemukan",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "AWB: ${awb ?: "-"}",
                                                fontSize = 14.sp,
                                                color = Color.Gray
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                OutlinedButton(onClick = { navController.popBackStack() }) {
                                                    Text("Kembali")
                                                }
                                                Button(
                                                    onClick = {
                                                        if (!awb.isNullOrBlank() && !isResolving) {
                                                            retryCount += 1
                                                            resolveAttempted = false
                                                        }
                                                    }
                                                ) {
                                                    Text("Coba lagi")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (showReportDialog) {
                                val courierName = sessionManager.getBestCourierName()
                                val courierId = sessionManager.getUserId() ?: ""

                                fun buildReportText(): String {
                                    val phoneModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                                    val version = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"
                                    val debug = lastResolveDebug ?: "no_debug"
                                    return """
                                        BCE Courier - AWB update gagal (Resi tidak ditemukan)
                                        awb=${awb.orEmpty()}
                                        retryCount=$retryCount
                                        courierId=$courierId
                                        courierName=$courierName
                                        appVersion=$version
                                        androidSdk=${Build.VERSION.SDK_INT}
                                        device=$phoneModel
                                        debug=$debug
                                    """.trimIndent()
                                }

                                fun copyToClipboard(text: String) {
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("bce_courier_error", text))
                                    Toast.makeText(appContext, "Error copied", Toast.LENGTH_SHORT).show()
                                }

                                fun openWhatsApp(text: String) {
                                    val phone = BuildConfig.SUPPORT_WA_NUMBER.filter { it.isDigit() }
                                    if (phone.isBlank()) {
                                        copyToClipboard(text)
                                        Toast.makeText(appContext, "SUPPORT_WA_NUMBER belum diset", Toast.LENGTH_LONG).show()
                                        return
                                    }

                                    val encoded = Uri.encode(text)
                                    val url = "https://wa.me/$phone?text=$encoded"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    runCatching { startActivity(intent) }
                                        .onFailure { e ->
                                            copyToClipboard(text)
                                            Toast.makeText(appContext, "Gagal membuka WhatsApp: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                }

                                AlertDialog(
                                    onDismissRequest = { showReportDialog = false },
                                    title = { Text("Laporkan Error") },
                                    text = {
                                        Text(
                                            "Sudah 2x retry tapi masih gagal. Kamu bisa copy detail error dan kirim ke admin via WhatsApp."
                                        )
                                    },
                                    confirmButton = {
                                        Button(onClick = {
                                            val report = buildReportText()
                                            copyToClipboard(report)
                                            openWhatsApp(report)
                                        }) { Text("WhatsApp Admin") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = {
                                            val report = buildReportText()
                                            copyToClipboard(report)
                                        }) { Text("Copy") }
                                    }
                                )
                            }
                        }

                        composable("pickup") {
                            var manifestList by remember { mutableStateOf<List<ManifestItem>>(emptyList()) }
                            var isLoading by remember { mutableStateOf(true) }
                            var searchQuery by rememberSaveable { mutableStateOf("") }
                            var searchResults by remember { mutableStateOf<List<ManifestItem>>(emptyList()) }
                            var isSearching by remember { mutableStateOf(false) }
                            var searchErrorMessage by remember { mutableStateOf<String?>(null) }
                            val courierId = sessionManager.getUserId() ?: ""

                            val loadManifestList: suspend () -> Unit = {
                                isLoading = true
                                try {
                                    val response = NetworkModule.apiService.getManifestList(
                                        courierIdFilter = if (courierId.isNotBlank()) "eq.$courierId" else null,
                                        originBranchFilter = "is.null"
                                    )
                                    if (response.isSuccessful && response.body() != null) {
                                        manifestList = response.body()!!.map { ManifestItem.fromJsonObject(it) }
                                    }
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Failed to load manifest list", e)
                                }
                                isLoading = false
                            }

                            fun normalizeManifestSearchQuery(raw: String): String {
                                return raw
                                    .trim()
                                    .replace(Regex("\\s+"), " ")
                                    .filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' || it == '/' }
                            }

                            val loadSearchResults: suspend (String) -> Unit = loadSearchResults@{ rawQuery ->
                                val normalized = normalizeManifestSearchQuery(rawQuery)
                                if (normalized.isBlank()) {
                                    searchResults = emptyList()
                                    searchErrorMessage = null
                                    return@loadSearchResults
                                }
                                val searchableChars = normalized.count { it.isLetterOrDigit() }
                                if (searchableChars < 3) {
                                    searchResults = emptyList()
                                    searchErrorMessage = "Minimal 3 karakter"
                                    return@loadSearchResults
                                }

                                isSearching = true
                                searchErrorMessage = null
                                try {
                                    val response = NetworkModule.apiService.getManifestList(
                                        courierIdFilter = null,
                                        orFilter = "(awb_no.ilike.*$normalized*,agent_customer.ilike.*$normalized*)",
                                        originBranchFilter = "is.null",
                                        limit = 50
                                    )
                                    if (response.isSuccessful && response.body() != null) {
                                        searchResults = response.body()!!.map { ManifestItem.fromJsonObject(it) }
                                    } else {
                                        searchResults = emptyList()
                                        searchErrorMessage = "Gagal memuat hasil (${response.code()})"
                                    }
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Failed to search manifest", e)
                                    searchResults = emptyList()
                                    searchErrorMessage = "Error: ${e.message ?: "unknown"}"
                                } finally {
                                    isSearching = false
                                }
                            }

                            LaunchedEffect(Unit) {
                                loadManifestList()
                            }

                            LaunchedEffect(searchQuery) {
                                val normalized = normalizeManifestSearchQuery(searchQuery)
                                if (normalized.isBlank()) {
                                    searchResults = emptyList()
                                    searchErrorMessage = null
                                    return@LaunchedEffect
                                }
                                val searchableChars = normalized.count { it.isLetterOrDigit() }
                                if (searchableChars < 3) {
                                    searchResults = emptyList()
                                    searchErrorMessage = "Minimal 3 karakter"
                                    return@LaunchedEffect
                                }

                                delay(350)
                                loadSearchResults(searchQuery)
                            }

                            PickupDashboardScreen(
                                manifestList = manifestList,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                searchResults = searchResults,
                                isSearching = isSearching,
                                searchErrorMessage = searchErrorMessage,
                                isLoading = isLoading,
                                onBack = { navController.popBackStack() },
                                onCreateNew = { navController.navigate("pickup_form") },
                                onRefresh = {
                                    lifecycleScope.launch {
                                        if (normalizeManifestSearchQuery(searchQuery).isBlank()) {
                                            loadManifestList()
                                        } else {
                                            loadSearchResults(searchQuery)
                                        }
                                    }
                                }
                            )
                        }

                        composable("pickup_form") {
                            var isSubmitting by remember { mutableStateOf(false) }
                            var customers by remember { mutableStateOf<List<CustomerData>>(emptyList()) }
                            var isLoadingCustomers by remember { mutableStateOf(false) }
                            var showCustomerSelector by remember { mutableStateOf(false) }
                            val courierId = sessionManager.getUserId() ?: ""

                            val loadCustomers: () -> Unit = {
                                lifecycleScope.launch {
                                    isLoadingCustomers = true
                                    try {
                                        val response = NetworkModule.apiService.getCustomers(
                                            branchOriginFilter = "is.null"
                                        )
                                        if (response.isSuccessful && response.body() != null) {
                                            customers = response.body()!!.map { CustomerData.fromJsonObject(it) }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "Failed to load customers", e)
                                    }
                                    isLoadingCustomers = false
                                }
                            }

                            PickupScreen(
                                onBack = { navController.popBackStack() },
                                onSubmit = { payload ->
                                    lifecycleScope.launch {
                                        isSubmitting = true
                                        try {
                                            val response = NetworkModule.apiService.insertManifest(payload.toJsonObject())
                                            if (response.isSuccessful) {
                                                playSound(R.raw.scan_success)
                                                Toast.makeText(
                                                    appContext,
                                                    "AWB ${payload.awbNo} berhasil disimpan!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                navController.popBackStack()
                                            } else {
                                                playSound(R.raw.scan_error)
                                                Toast.makeText(
                                                    appContext,
                                                    "Gagal menyimpan: ${response.code()}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        } catch (e: Exception) {
                                            playSound(R.raw.scan_error)
                                            Toast.makeText(
                                                appContext,
                                                "Error: ${e.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            Log.e("MainActivity", "Pickup submit error", e)
                                        } finally {
                                            isSubmitting = false
                                        }
                                    }
                                },
                                isSubmitting = isSubmitting,
                                customers = customers,
                                isLoadingCustomers = isLoadingCustomers,
                                onLoadCustomers = loadCustomers,
                                showCustomerSelector = showCustomerSelector,
                                onShowCustomerSelector = { showCustomerSelector = it },
                                courierId = courierId,
                                isConfigLoading = isPickupConfigLoading,
                                agentOptions = pickupAgentListCentral,
                                kotaWilayahPusatOptions = pickupKotaWilayahPusat,
                                hargaPerKgMapOptions = pickupHargaPerKgMap
                            )
                        }

                        composable("map_preview/{awb}") { backStackEntry ->
                            val awb = backStackEntry.arguments?.getString("awb")
                            var shipment by remember(awb) { mutableStateOf<ShipmentItem?>(null) }

                            LaunchedEffect(awb, allShipments) {
                                if (awb.isNullOrBlank()) return@LaunchedEffect
                                shipment = allShipments.find { it.awb == awb } ?: fetchShipmentByAwb(awb).shipment
                            }

                            val resolved = shipment
                            if (awb.isNullOrBlank()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("AWB tidak valid")
                                }
                            } else if (resolved == null) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                MapPreviewScreen(
                                    shipment = resolved,
                                    locationHelper = locationHelper,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }

                    if (forceUpdateRequired) {
                        ForceUpdateDialog(
                            latestVersionName = latestVersionName,
                            changelog = updateChangelog,
                            isDownloading = isDownloadingUpdate,
                            downloadProgressPercent = updateDownloadProgressPercent,
                            isUpdateDownloaded = isUpdateDownloaded,
                            updateDownloadError = updateDownloadError,
                            onDownload = startDownloadUpdate,
                            onInstall = startInstallUpdate,
                            message = forceUpdateMessage
                        )
                    }
                }
            }
        }
    }
}
