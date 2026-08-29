package com.bce.courier.ui.screen

import android.location.Location
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.bce.courier.data.model.ShipmentItem
import com.bce.courier.tracking.CourierTrackingConfig
import com.bce.courier.tracking.CourierTrackingLocationStore
import com.bce.courier.util.LocationHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateShipmentScreen(
    shipment: ShipmentItem,
    onBack: () -> Unit,
    onUpdate: (status: String, notes: String, location: Location?, photoUri: Uri?, isCameraPhoto: Boolean) -> Unit,
    isUpdating: Boolean,
    isLockedToDelivered: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val isDarkTheme = isSystemInDarkTheme()

    val photoBoxBg = if (isDarkTheme) Color(0xFF2D2D2D) else Color(0xFFF5F5F5)
    val photoBoxBorder = if (isDarkTheme) Color(0xFF555555) else Color.LightGray
    val successGreen = Color(0xFF2E7D32)
    val successGreenLight = Color(0xFF4CAF50)
    val warningCardBg = if (isDarkTheme) Color(0xFF3E3527) else Color(0xFFFFF8E1)
    val warningIconColor = if (isDarkTheme) Color(0xFFFFCA28) else Color(0xFFF9A825)
    val warningTextColor = if (isDarkTheme) Color(0xFFE0C9A6) else Color(0xFF5D4037)
    val textPrimary = if (isDarkTheme) Color.White else Color(0xFF1A1A1A)
    val textSecondary = if (isDarkTheme) Color(0xFFB0B0B0) else Color.Gray
    val cardBg = if (isDarkTheme) MaterialTheme.colorScheme.surfaceVariant else Color.White
    val overlayCardBg = if (isDarkTheme) MaterialTheme.colorScheme.surface else Color.White
    val cameraButtonColor = Color(0xFF0091EA)
    val galleryButtonColor = if (isDarkTheme) Color(0xFF616161) else Color(0xFF757575)

    var selectedStatus by remember {
        mutableStateOf("delivered")
    }
    var notes by remember { mutableStateOf("") }
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var location by remember { mutableStateOf<Location?>(null) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val locationHelper = remember { LocationHelper(appContext) }
    
    var tempPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var isCameraPhoto by rememberSaveable { mutableStateOf(false) }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri = tempPhotoUri
            isCameraPhoto = true
        }
    }

    // Gallery picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            photoUri = it
            isCameraPhoto = false
        }
    }

    fun createImageUri(): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", context.cacheDir)
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }
    
    val statuses = if (isLockedToDelivered) listOf("delivered") else listOf("shipped", "delivered")
    
    LaunchedEffect(Unit) {
        val cachedTrackingLocation = CourierTrackingLocationStore.getFreshLocation(
            appContext,
            CourierTrackingConfig.sharedLocationMaxAgeMs
        )

        if (cachedTrackingLocation != null) {
            location = cachedTrackingLocation
        } else {
            locationHelper.getCurrentLocation { loc ->
                location = loc
            }
        }
    }

    var wasUpdating by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(isUpdating) {
        if (isUpdating) {
            wasUpdating = true
            showSuccess = false
        } else if (wasUpdating) {
            showSuccess = true
            wasUpdating = false
            kotlinx.coroutines.delay(1500)
            showSuccess = false
            onRefresh()
            onBack()
        }
    }

    val overlayVisible = isUpdating

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Update Status", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { if (!isUpdating) onBack() }, enabled = !isUpdating) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("AWB Number", fontSize = 11.sp, color = Color.Gray)
                        Text(shipment.awb, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Penerima", fontSize = 11.sp, color = Color.Gray)
                        Text(shipment.recipient_name, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (shipment.recipient_phone.isNotBlank() && shipment.recipient_phone != "Auto Generated") {
                            Text(shipment.recipient_phone, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(shipment.address, fontSize = 12.sp, color = Color.DarkGray, maxLines = 2)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Pilih Status", modifier = Modifier.align(Alignment.Start), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    statuses.forEach { status ->
                        FilterChip(
                            selected = selectedStatus == status,
                            onClick = { if (!isLockedToDelivered) selectedStatus = status },
                            label = {
                                val label = when(status) {
                                    "shipped" -> "PENDING"
                                    "delivered" -> "DELIVERED"
                                    else -> status.replace("_", " ").uppercase()
                                }
                                Text(label, fontSize = 10.sp)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Catatan (Opsional)", modifier = Modifier.align(Alignment.Start), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))

                val noteTemplates = listOf(
                    "Diterima yang bersangkutan / Keluarga",
                    "Titip Kerabat / Tetangga",
                    "Titip Resepsionis / Lobby",
                    "Depan Pintu / Teras",
                    "PopBox / Mailroom",
                    "Lain-lain"
                )

                var noteMenuExpanded by remember { mutableStateOf(false) }
                var isCustomNoteMode by remember { mutableStateOf(false) }
                var customNote by remember { mutableStateOf("") }

                val selectedNoteLabel = when {
                    isCustomNoteMode && notes.isNotBlank() -> "Lain-lain: ${notes.take(24)}${if (notes.length > 24) "…" else ""}"
                    isCustomNoteMode -> "Lain-lain"
                    notes.isBlank() -> "Pilih catatan"
                    else -> notes
                }

                ExposedDropdownMenuBox(
                    expanded = noteMenuExpanded,
                    onExpandedChange = { noteMenuExpanded = !noteMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedNoteLabel,
                        onValueChange = {},
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = noteMenuExpanded)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedBorderColor = if (isDarkTheme) Color(0xFF555555) else Color.LightGray,
                            unfocusedBorderColor = if (isDarkTheme) Color(0xFF555555) else Color.LightGray,
                            focusedTrailingIconColor = textSecondary,
                            unfocusedTrailingIconColor = textSecondary
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = noteMenuExpanded,
                        onDismissRequest = { noteMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 260.dp)
                    ) {
                        noteTemplates.forEach { template ->
                            DropdownMenuItem(
                                text = { Text(template, fontSize = 13.sp) },
                                onClick = {
                                    noteMenuExpanded = false
                                    if (template == "Lain-lain") {
                                        isCustomNoteMode = true
                                    } else {
                                        isCustomNoteMode = false
                                        customNote = ""
                                        notes = template
                                    }
                                }
                            )
                        }
                    }
                }

                if (isCustomNoteMode) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customNote,
                        onValueChange = {
                            customNote = it
                            notes = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Catatan Lain-lain") },
                        minLines = 2,
                        maxLines = 2,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Bukti Foto", modifier = Modifier.align(Alignment.Start), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))


                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(photoBoxBg, MaterialTheme.shapes.medium)
                        .border(1.dp, photoBoxBorder, MaterialTheme.shapes.medium)
                        .clickable {
                            showPhotoSourceDialog = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUri != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = photoUri,
                                contentDescription = "Bukti Foto",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // Remove photo button
                            IconButton(
                                onClick = {
                                    photoUri = null
                                    isCameraPhoto = false
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Hapus foto", tint = Color.White)
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color.Gray)
                            Text("Tap untuk ambil foto", color = Color.Gray, fontSize = 13.sp)
                            Text("Kamera atau Galeri", fontSize = 11.sp, color = Color.Gray.copy(alpha = 0.7f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (location != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Green, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lokasi terdeteksi: ${location?.latitude}, ${location?.longitude}", fontSize = 11.sp, color = Color.Gray)
                    }
                } else {
                    Text("Mencari lokasi GPS...", fontSize = 11.sp, color = Color.Red)
                }

                Spacer(modifier = Modifier.height(14.dp))

                val canSubmit = photoUri != null

                AnimatedVisibility(
                    visible = canSubmit,
                    enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200))
                ) {
                    Column {
                        val buttonEnabled = !isUpdating
                        val buttonContainer = when {
                            showSuccess -> Color(0xFF2E7D32)
                            else -> Color(0xFF2E7D32)
                        }

                        Button(
                            onClick = { if (!isUpdating) onUpdate(selectedStatus, notes, location, photoUri, isCameraPhoto) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = buttonEnabled,
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = buttonContainer)
                        ) {
                            AnimatedContent(
                                targetState = when {
                                    showSuccess -> "success"
                                    isUpdating -> "loading"
                                    else -> "idle"
                                },
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
                                },
                                label = "buttonState"
                            ) { state ->
                                when (state) {
                                    "success" -> Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                                        Text("BERHASIL", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                    }
                                    "loading" -> Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {

                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.5.dp, color = Color.White)
                                        Text("MEMPROSES...", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    }
                                    else -> Text("UPDATE STATUS", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                }
                            }
                        }

                        if (isUpdating) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(MaterialTheme.shapes.small),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        }
                    }
                }

                if (!canSubmit) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = warningCardBg)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = warningIconColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Upload foto terlebih dahulu untuk melanjutkan",
                                fontSize = 12.sp,
                                color = warningTextColor
                            )
                        }
                    }
                }
            }
        }

        val showOverlay = overlayVisible || showSuccess
        val currentOverlayAlpha by animateFloatAsState(
            targetValue = if (showOverlay) 1f else 0f,
            animationSpec = tween(durationMillis = 250),
            label = "overlayAlphaAnim"
        )

        if (showOverlay || currentOverlayAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(currentOverlayAlpha)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {},
                contentAlignment = Alignment.Center
            ) {
                val successScale by animateFloatAsState(
                    targetValue = if (showSuccess) 1f else 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "successScale"
                )

                val checkmarkRotation by animateFloatAsState(
                    targetValue = if (showSuccess) 0f else -90f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "checkRotation"
                )

                val pulseAnim = rememberInfiniteTransition(label = "pulse")
                val pulseScale by pulseAnim.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )

                AnimatedContent(
                    targetState = showSuccess,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) + scaleIn(
                            initialScale = 0.8f,
                            animationSpec = tween(300)
                        ) togetherWith fadeOut(animationSpec = tween(200)) + scaleOut(
                            targetScale = 0.9f,
                            animationSpec = tween(200)
                        )
                    },
                    label = "overlayContent",
                    modifier = Modifier.wrapContentSize(Alignment.Center)
                ) { isSuccess ->
                    if (isSuccess) {
                        Surface(
                            color = overlayCardBg,
                            shape = MaterialTheme.shapes.extraLarge,
                            tonalElevation = 12.dp,
                            modifier = Modifier
                                .wrapContentSize(Alignment.Center)
                                .scale(successScale)
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .scale(pulseScale)
                                        .background(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    successGreenLight,
                                                    successGreen
                                                )
                                            ),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .graphicsLayer { rotationZ = checkmarkRotation }
                                    )
                                }
                                Text(
                                    "Berhasil!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = successGreen
                                )
                                Text(
                                    "Status berhasil diupdate",
                                    fontSize = 14.sp,
                                    color = textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = overlayCardBg,
                            shape = MaterialTheme.shapes.large,
                            tonalElevation = 8.dp,
                            modifier = Modifier.wrapContentSize(Alignment.Center)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(36.dp),
                                        strokeWidth = 3.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "Mengupload Status",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        color = textPrimary
                                    )
                                    Text(
                                        "Mohon tunggu sebentar...",
                                        fontSize = 13.sp,
                                        color = textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showPhotoSourceDialog) {
            ModalBottomSheet(
                onDismissRequest = { showPhotoSourceDialog = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .background(
                                    if (isDarkTheme) Color(0xFF555555) else Color.LightGray,
                                    MaterialTheme.shapes.small
                                )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        "Pilih Sumber Foto",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = textPrimary,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    Button(
                        onClick = {
                            showPhotoSourceDialog = false
                            try {
                                val uri = createImageUri()
                                tempPhotoUri = uri
                                cameraLauncher.launch(uri)
                            } catch (_: Exception) {
                                Toast.makeText(appContext, "Gagal membuka kamera", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = cameraButtonColor)
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Ambil dari Kamera", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            showPhotoSourceDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = galleryButtonColor)
                    ) {
                        Icon(
                            Icons.Default.Photo,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Pilih dari Galeri", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = { showPhotoSourceDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isDarkTheme) Color(0xFF64B5F6) else Color(0xFF1976D2)
                        )
                    ) {
                        Text("Batal", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
