package com.bce.courier.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bce.courier.data.local.PickupDraftStore
import com.bce.courier.data.model.PickupFormState
import com.bce.courier.data.model.paymentMethodLabel
import com.bce.courier.util.normalizePickupNumber
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bce.courier.data.model.CustomerData
import com.bce.courier.data.model.ManifestPayload
import com.bce.courier.ui.component.CustomerSelectorDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.*

@Composable
private fun RequiredLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontWeight = FontWeight.Medium)
        Text(" *", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectAllOnFocusOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    isError: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    emptyValueOnBlur: String = "0",
    shape: androidx.compose.ui.graphics.Shape = OutlinedTextFieldDefaults.shape
) {
    var hasFocus by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(value)) }

    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(text = value, selection = TextRange(value.length))
        }
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { proposed ->
            val normalized = normalizePickupNumber(proposed.text, keyboardOptions.keyboardType == KeyboardType.Decimal)
            if (normalized != null) {
                val removed = proposed.text.length - normalized.length
                textFieldValue = if (normalized == proposed.text) proposed else proposed.copy(
                    text = normalized,
                    selection = TextRange(
                        (proposed.selection.start - removed).coerceIn(0, normalized.length),
                        (proposed.selection.end - removed).coerceIn(0, normalized.length),
                    ),
                    composition = null,
                )
                onValueChange(normalized)
            }
        },
        modifier = modifier.onFocusChanged { focusState ->
            val isFocused = focusState.isFocused
            if (!hasFocus && isFocused) {
                textFieldValue = if (textFieldValue.text == "0") TextFieldValue("")
                    else textFieldValue.copy(selection = TextRange(0, textFieldValue.text.length))
            } else if (hasFocus && !isFocused && textFieldValue.text.isBlank()) {
                textFieldValue = TextFieldValue(emptyValueOnBlur)
                if (value != emptyValueOnBlur) onValueChange(emptyValueOnBlur)
            }
            hasFocus = isFocused
        },
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        isError = isError,
        label = label,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        colors = colors,
        shape = shape
    )
}

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    action: (@Composable RowScope.() -> Unit)? = null
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.weight(1f))
            action?.invoke(this)
        }
    }
}

private val defaultKotaWilayahPusat = mapOf(
    "bangka" to listOf("Pangkal Pinang", "Sungailiat", "Belinyu", "Jebus", "Koba", "Toboali", "Mentok"),
    "kalimantan barat" to listOf("Pontianak", "Sungai Pinyuh", "Mempawah", "Singkawang", "Pemangkat", "Sambas"),
    "belitung" to listOf("Tj Pandan"),
    "bali" to listOf("Denpasar")
)

private val defaultHargaPerKgMap = mapOf(
    "Pangkal Pinang" to 35000,
    "Sungailiat" to 37000,
    "Koba" to 38000,
    "Toboali" to 39000,
    "Belinyu" to 35000,
    "Jebus" to 35000,
    "Mentok" to 39000,
    "Pontianak" to 32000,
    "Sungai Pinyuh" to 35000,
    "Tj Pandan" to 28000,
    "Mempawah" to 35000,
    "Singkawang" to 35000,
    "Pemangkat" to 37000,
    "Sambas" to 39000,
    "Denpasar" to 30000
)

private val defaultAgentList = listOf(
    "UDR CASH", "SEA CASH", "GLC UDR TRF", "GLC SEA TRF", "COD UDR", "COD SEA",
    "KMY UDR TRF", "KMY SEA TRF", "KARTINI KIKI", "DUTA GARDEN FRENITA", "FELLISIA PORIS EX 3",
    "OTTY OFFICIAL", "CITRA 3 RENY", "HENDI", "PRALITA", "SALIM", "ISKANDAR", "IMAM",
    "DONI", "HERFAN", "EZZA", "YANDRI", "DIKY", "YOS", "INDAH SUSHI TIME",
    "CENTRAL NURSERY BANGKA", "MAMAPIA", "AMELIA PEDINDANG", "HENDRY LIMIA", "JESS DOT",
    "SEPIRING RASA BASO", "CHRISTINE PADEMANGAN", "AMERTHA / HOLAI RESTO", "DIMAS"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PickupScreen(
    onBack: () -> Unit,
    onSubmit: (ManifestPayload) -> Unit,
    isSubmitting: Boolean = false,
    customers: List<CustomerData> = emptyList(),
    isLoadingCustomers: Boolean = false,
    onLoadCustomers: () -> Unit = {},
    showCustomerSelector: Boolean = false,
    onShowCustomerSelector: (Boolean) -> Unit = {},
    courierId: String = "",
    isConfigLoading: Boolean = false,
    agentOptions: List<String> = emptyList(),
    kotaWilayahPusatOptions: Map<String, List<String>> = emptyMap(),
    hargaPerKgMapOptions: Map<String, Int> = emptyMap(),
    submittedAwb: String? = null,
) {
    val isDarkMode = isSystemInDarkTheme()

    if (isConfigLoading) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Memuat...", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack, enabled = !isSubmitting) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isDarkMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                        titleContentColor = if (isDarkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = if (isDarkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Memuat konfigurasi pickup...")
                }
            }
        }
        return
    }

    val resolvedAgentList = remember(agentOptions) {
        agentOptions
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .ifEmpty { defaultAgentList }
    }

    val resolvedKotaWilayahPusat = remember(kotaWilayahPusatOptions) {
        val merged = linkedMapOf<String, MutableList<String>>()

        kotaWilayahPusatOptions.forEach { (rawKey, rawValues) ->
            val key = rawKey.trim().lowercase(Locale.ROOT)
            if (key.isBlank()) return@forEach

            val values = rawValues
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            if (values.isEmpty()) return@forEach

            val current = merged.getOrPut(key) { mutableListOf() }
            current.addAll(values)
        }

        merged
            .mapValues { (_, values) ->
                values.distinctBy { it.lowercase(Locale.ROOT) }
            }
            .filterValues { it.isNotEmpty() }
            .ifEmpty { defaultKotaWilayahPusat }
    }

    val resolvedHargaPerKgMap = remember(hargaPerKgMapOptions) {
        val normalizedInput = hargaPerKgMapOptions
            .mapKeys { (k, _) -> k.trim().lowercase(Locale.ROOT) }
            .filterKeys { it.isNotEmpty() }

        val normalizedDefault = defaultHargaPerKgMap
            .mapKeys { (k, _) -> k.trim().lowercase(Locale.ROOT) }

        normalizedInput.ifEmpty { normalizedDefault }
    }

    val context = LocalContext.current
    val draftStore = remember(courierId) { PickupDraftStore(context, courierId) }
    val formSaver = remember {
        Saver<PickupFormState, String>(
            save = { Json.encodeToString(it) },
            restore = { runCatching { Json.decodeFromString<PickupFormState>(it) }.getOrNull() },
        )
    }
    var form by rememberSaveable(courierId, stateSaver = formSaver) { mutableStateOf(draftStore.load()) }
    LaunchedEffect(submittedAwb) {
        submittedAwb?.let { form = form.copy(awbNo = it, submissionAttempted = true) }
    }
    LaunchedEffect(form) { draftStore.save(form) }
    LaunchedEffect(courierId) {
        while (isActive) {
            form = form.withCurrentDate()
            delay(30_000)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) form = form.withCurrentDate()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showResetDraft by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val sectionCardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
    val sectionCardBorder = BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDarkMode) 0.35f else 0.6f)
    )
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        disabledContainerColor = MaterialTheme.colorScheme.surface,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        cursorColor = MaterialTheme.colorScheme.primary
    )
    val fieldShape = RoundedCornerShape(12.dp)

    var showValidationErrors by remember { mutableStateOf(false) }

    var kotaTujuanExpanded by remember { mutableStateOf(false) }
    var wilayahExpanded by remember { mutableStateOf(false) }
    var kirimViaExpanded by remember { mutableStateOf(false) }
    var metodePembayaranExpanded by remember { mutableStateOf(false) }
    var agentExpanded by remember { mutableStateOf(false) }
    var lastWilayahKeyForHargaSync by rememberSaveable(courierId) {
        mutableStateOf(form.wilayah.trim().lowercase(Locale.ROOT))
    }

    val awbBringIntoViewRequester = remember { BringIntoViewRequester() }
    val awbFocusRequester = remember { FocusRequester() }

    val kotaBringIntoViewRequester = remember { BringIntoViewRequester() }
    val kotaFocusRequester = remember { FocusRequester() }

    val wilayahBringIntoViewRequester = remember { BringIntoViewRequester() }
    val wilayahFocusRequester = remember { FocusRequester() }

    val agentBringIntoViewRequester = remember { BringIntoViewRequester() }
    val agentFocusRequester = remember { FocusRequester() }

    val pengirimBringIntoViewRequester = remember { BringIntoViewRequester() }
    val pengirimFocusRequester = remember { FocusRequester() }

    val penerimaBringIntoViewRequester = remember { BringIntoViewRequester() }
    val penerimaFocusRequester = remember { FocusRequester() }

    val nomorPenerimaBringIntoViewRequester = remember { BringIntoViewRequester() }
    val nomorPenerimaFocusRequester = remember { FocusRequester() }

    val alamatPenerimaBringIntoViewRequester = remember { BringIntoViewRequester() }
    val alamatPenerimaFocusRequester = remember { FocusRequester() }

    val isiBarangBringIntoViewRequester = remember { BringIntoViewRequester() }
    val isiBarangFocusRequester = remember { FocusRequester() }

    val kirimViaBringIntoViewRequester = remember { BringIntoViewRequester() }
    val kirimViaFocusRequester = remember { FocusRequester() }

    val metodePembayaranBringIntoViewRequester = remember { BringIntoViewRequester() }
    val metodePembayaranFocusRequester = remember { FocusRequester() }

    val kotaTujuanList = resolvedKotaWilayahPusat.keys.toList()
    val wilayahOptions = remember(form.kotaTujuan, resolvedKotaWilayahPusat) {
        resolvedKotaWilayahPusat[form.kotaTujuan] ?: emptyList()
    }

    LaunchedEffect(form.wilayah, resolvedHargaPerKgMap) {
        val key = form.wilayah.trim().lowercase(Locale.ROOT)
        if (lastWilayahKeyForHargaSync != key) {
            lastWilayahKeyForHargaSync = key
            form = form.copy(priceManuallyEdited = false)
        }

        if (form.priceManuallyEdited) return@LaunchedEffect

        val harga = resolvedHargaPerKgMap[key] ?: 0
        if (harga > 0 && form.hargaPerKg != harga.toString()) {
            form = form.copy(hargaPerKg = harga.toString())
        }
    }

    val subTotal = remember(form.beratKg, form.hargaPerKg) {
        val berat = form.beratKg.toDoubleOrNull() ?: 0.0
        val harga = form.hargaPerKg.toIntOrNull() ?: 0
        (berat * harga).toInt()
    }

    val total = remember(subTotal, form.biayaAdmin, form.biayaPackaging, form.biayaTransit) {
        val admin = form.biayaAdmin.toIntOrNull() ?: 0
        val packaging = form.biayaPackaging.toIntOrNull() ?: 0
        val transit = form.biayaTransit.toIntOrNull() ?: 0
        subTotal + admin + packaging + transit
    }

    fun formatRupiah(amount: Int): String {
        return NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(amount)
    }

    fun applyCustomerData(customer: CustomerData) {
        form = form.withCustomer(customer)
    }

    fun validateAndSubmit() {
        if (isSubmitting) return
        form = form.withCurrentDate()
        showValidationErrors = true

        val firstMissing = when {
            form.awbNo.isBlank() -> "awb"
            form.kotaTujuan.isBlank() -> "kota"
            form.wilayah.isBlank() -> "wilayah"
            form.agentCustomer.isBlank() -> "agent"
            form.namaPengirim.isBlank() -> "pengirim"
            form.namaPenerima.isBlank() -> "penerima"
            form.nomorPenerima.isBlank() -> "nomorPenerima"
            form.alamatPenerima.isBlank() -> "alamatPenerima"
            form.isiBarang.isBlank() -> "isiBarang"
            form.kirimVia.isBlank() -> "kirimVia"
            form.metodePembayaran.isBlank() -> "metodePembayaran"
            else -> null
        }

        if (firstMissing != null) {
            scope.launch {
                when (firstMissing) {
                    "awb" -> {
                        awbBringIntoViewRequester.bringIntoView()
                        awbFocusRequester.requestFocus()
                    }
                    "kota" -> {
                        kotaBringIntoViewRequester.bringIntoView()
                        kotaFocusRequester.requestFocus()
                        kotaTujuanExpanded = true
                    }
                    "wilayah" -> {
                        wilayahBringIntoViewRequester.bringIntoView()
                        wilayahFocusRequester.requestFocus()
                        if (wilayahOptions.isNotEmpty()) wilayahExpanded = true else kotaTujuanExpanded = true
                    }
                    "agent" -> {
                        agentBringIntoViewRequester.bringIntoView()
                        agentFocusRequester.requestFocus()
                        agentExpanded = true
                    }
                    "pengirim" -> {
                        pengirimBringIntoViewRequester.bringIntoView()
                        pengirimFocusRequester.requestFocus()
                    }
                    "penerima" -> {
                        penerimaBringIntoViewRequester.bringIntoView()
                        penerimaFocusRequester.requestFocus()
                    }
                    "nomorPenerima" -> {
                        nomorPenerimaBringIntoViewRequester.bringIntoView()
                        nomorPenerimaFocusRequester.requestFocus()
                    }
                    "alamatPenerima" -> {
                        alamatPenerimaBringIntoViewRequester.bringIntoView()
                        alamatPenerimaFocusRequester.requestFocus()
                    }
                    "isiBarang" -> {
                        isiBarangBringIntoViewRequester.bringIntoView()
                        isiBarangFocusRequester.requestFocus()
                    }
                    "kirimVia" -> {
                        kirimViaBringIntoViewRequester.bringIntoView()
                        kirimViaFocusRequester.requestFocus()
                        kirimViaExpanded = true
                    }
                    "metodePembayaran" -> {
                        metodePembayaranBringIntoViewRequester.bringIntoView()
                        metodePembayaranFocusRequester.requestFocus()
                        metodePembayaranExpanded = true
                    }
                }
            }
            return
        }

        val payload = ManifestPayload(
            awbNo = form.awbNo,
            awbDate = form.awbDate,
            kirimVia = form.kirimVia,
            kotaTujuan = form.kotaTujuan,
            wilayah = form.wilayah,
            metodePembayaran = form.metodePembayaran,
            agentCustomer = form.agentCustomer,
            namaPengirim = form.namaPengirim,
            nomorPengirim = form.nomorPengirim,
            namaPenerima = form.namaPenerima,
            nomorPenerima = form.nomorPenerima,
            alamatPenerima = form.alamatPenerima,
            coli = form.coli.toIntOrNull() ?: 1,
            beratKg = form.beratKg.toDoubleOrNull() ?: 1.0,
            hargaPerKg = form.hargaPerKg.toIntOrNull() ?: 0,
            subTotal = subTotal,
            biayaAdmin = form.biayaAdmin.toIntOrNull() ?: 0,
            biayaPackaging = form.biayaPackaging.toIntOrNull() ?: 0,
            biayaTransit = form.biayaTransit.toIntOrNull() ?: 0,
            total = total,
            isiBarang = form.isiBarang,
            courierId = courierId,
            isRetry = form.submissionAttempted,
        )
        form = form.copy(submissionAttempted = true)
        draftStore.save(form)
        onSubmit(payload)
    }

    if (isSubmitting) {
        Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
            Surface(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Menyimpan resi...")
                }
            }
        }
    }
    if (showDatePicker) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                LocalDate.parse(form.awbDate).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            }.getOrNull(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let {
                        form = form.copy(
                            awbDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString(),
                            dateManuallySelected = true,
                        )
                    }
                    showDatePicker = false
                }, enabled = dateState.selectedDateMillis != null) { Text("Pilih tanggal") }
            },
            dismissButton = {
                TextButton(onClick = {
                    form = form.copy(dateManuallySelected = false).withCurrentDate()
                    showDatePicker = false
                }) { Text("Hari ini") }
            },
        ) { DatePicker(state = dateState) }
    }
    if (showResetDraft) {
        AlertDialog(
            onDismissRequest = { showResetDraft = false },
            title = { Text("Reset formulir?") },
            text = { Text("Isian draf saat ini akan dikosongkan.") },
            confirmButton = { TextButton(onClick = {
                form = PickupFormState()
                showValidationErrors = false
                draftStore.clear()
                showResetDraft = false
            }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("Reset")
            } },
            dismissButton = { TextButton(onClick = { showResetDraft = false }) { Text("Batal") } },
        )
    }

    if (showCustomerSelector) {
        CustomerSelectorDialog(
            customers = customers,
            isLoading = isLoadingCustomers,
            onDismiss = { onShowCustomerSelector(false) },
            onSelect = { customer ->
                applyCustomerData(customer)
                onShowCustomerSelector(false)
            },
            onRefresh = onLoadCustomers
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Pickup AWB", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isSubmitting) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDarkMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                    titleContentColor = if (isDarkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = if (isDarkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = sectionCardColors,
                border = sectionCardBorder
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SectionHeader(title = "No. AWB", icon = Icons.Default.ConfirmationNumber)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = form.awbNo,
                            onValueChange = { form = form.copy(awbNo = it) },
                            modifier = Modifier
                                .weight(1f)
                                .bringIntoViewRequester(awbBringIntoViewRequester)
                                .focusRequester(awbFocusRequester),
                            label = { RequiredLabel("No. AWB") },
                            placeholder = { Text("Tekan Generate untuk membuat AWB") },
                            readOnly = true,
                            isError = showValidationErrors && form.awbNo.isBlank(),
                            singleLine = true,
                            shape = fieldShape,
                            colors = fieldColors,
                            trailingIcon = {
                                if (form.awbNo.isNotBlank()) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                }
                            }
                        )
                        OutlinedButton(
                            onClick = { form = form.withNewAwb() },
                            enabled = !isSubmitting,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Generate", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Generate", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { showDatePicker = true },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Tanggal resi: ${form.awbDate}")
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Draf tersimpan otomatis",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { showResetDraft = true },
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset form", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = sectionCardColors,
                border = sectionCardBorder
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Pengirim", icon = Icons.Default.Person) {
                        OutlinedButton(
                            onClick = {
                                onLoadCustomers()
                                onShowCustomerSelector(true)
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PersonSearch, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    OutlinedTextField(
                        value = form.namaPengirim,
                        onValueChange = { form = form.copy(namaPengirim = it) },
                        label = { RequiredLabel("Nama Pengirim") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(pengirimBringIntoViewRequester)
                            .focusRequester(pengirimFocusRequester),
                        singleLine = true,
                        isError = showValidationErrors && form.namaPengirim.isBlank(),
                        shape = fieldShape,
                        colors = fieldColors,
                        trailingIcon = {
                            if (form.namaPengirim.isNotBlank()) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = form.nomorPengirim,
                        onValueChange = { form = form.copy(nomorPengirim = it) },
                        label = { Text("Nomor Telepon (Opsional)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("08xxxxxxxxxx (opsional)") },
                        singleLine = true,
                        shape = fieldShape,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = sectionCardColors,
                border = sectionCardBorder
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Penerima", icon = Icons.Default.LocationOn)

                    OutlinedTextField(
                        value = form.namaPenerima,
                        onValueChange = { form = form.copy(namaPenerima = it) },
                        label = { RequiredLabel("Nama Penerima") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(penerimaBringIntoViewRequester)
                            .focusRequester(penerimaFocusRequester),
                        singleLine = true,
                        isError = showValidationErrors && form.namaPenerima.isBlank(),
                        shape = fieldShape,
                        colors = fieldColors,
                        trailingIcon = {
                            if (form.namaPenerima.isNotBlank()) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = form.nomorPenerima,
                        onValueChange = { form = form.copy(nomorPenerima = it) },
                        label = { RequiredLabel("Nomor Telepon") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(nomorPenerimaBringIntoViewRequester)
                            .focusRequester(nomorPenerimaFocusRequester),
                        placeholder = { Text("08xxxxxxxxxx") },
                        singleLine = true,
                        isError = showValidationErrors && form.nomorPenerima.isBlank(),
                        shape = fieldShape,
                        colors = fieldColors,
                        trailingIcon = {
                            if (form.nomorPenerima.isNotBlank()) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = form.alamatPenerima,
                        onValueChange = { form = form.copy(alamatPenerima = it) },
                        label = { RequiredLabel("Alamat Lengkap") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(alamatPenerimaBringIntoViewRequester)
                            .focusRequester(alamatPenerimaFocusRequester),
                        placeholder = { Text("Nama jalan, nomor rumah, patokan") },
                        minLines = 2,
                        maxLines = 4,
                        isError = showValidationErrors && form.alamatPenerima.isBlank(),
                        shape = fieldShape,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = sectionCardColors,
                border = sectionCardBorder
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Tujuan", icon = Icons.Default.LocalShipping)

                    ExposedDropdownMenuBox(
                        expanded = kotaTujuanExpanded,
                        onExpandedChange = { kotaTujuanExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = form.kotaTujuan.replaceFirstChar { it.uppercase() },
                            onValueChange = {},
                            readOnly = true,
                            label = { RequiredLabel("Kota Tujuan") },
                            isError = showValidationErrors && form.kotaTujuan.isBlank(),
                            placeholder = { Text("Pilih Kota Tujuan") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kotaTujuanExpanded) },
                            shape = fieldShape,
                            colors = fieldColors,
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .bringIntoViewRequester(kotaBringIntoViewRequester)
                                .focusRequester(kotaFocusRequester)
                        )
                        ExposedDropdownMenu(
                            expanded = kotaTujuanExpanded,
                            onDismissRequest = { kotaTujuanExpanded = false }
                        ) {
                            kotaTujuanList.forEach { kota ->
                                DropdownMenuItem(
                                    text = { Text(kota.replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        form = form.copy(kotaTujuan = kota, wilayah = "")
                                        kotaTujuanExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = wilayahExpanded,
                        onExpandedChange = { wilayahExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = form.wilayah,
                            onValueChange = {},
                            readOnly = true,
                            label = { RequiredLabel("Wilayah") },
                            isError = showValidationErrors && form.wilayah.isBlank(),
                            placeholder = {
                                val text = when {
                                    form.kotaTujuan.isBlank() -> "Pilih Kota Tujuan dulu"
                                    wilayahOptions.isEmpty() -> "Belum ada pilihan wilayah"
                                    else -> "Pilih Wilayah"
                                }
                                Text(text)
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = wilayahExpanded) },
                            shape = fieldShape,
                            colors = fieldColors,
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .bringIntoViewRequester(wilayahBringIntoViewRequester)
                                .focusRequester(wilayahFocusRequester),
                            enabled = wilayahOptions.isNotEmpty()
                        )
                        ExposedDropdownMenu(
                            expanded = wilayahExpanded,
                            onDismissRequest = { wilayahExpanded = false }
                        ) {
                            wilayahOptions.forEach { wil ->
                                DropdownMenuItem(
                                    text = { Text(wil) },
                                    onClick = {
                                        form = form.copy(wilayah = wil)
                                        wilayahExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = sectionCardColors,
                border = sectionCardBorder
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Detail Kiriman", icon = Icons.Default.Inventory)

                    OutlinedTextField(
                        value = form.isiBarang,
                        onValueChange = { form = form.copy(isiBarang = it) },
                        label = { RequiredLabel("Isi Barang") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(isiBarangBringIntoViewRequester)
                            .focusRequester(isiBarangFocusRequester),
                        placeholder = { Text("Pilih template atau ketik") },
                        singleLine = true,
                        isError = showValidationErrors && form.isiBarang.isBlank(),
                        shape = fieldShape,
                        colors = fieldColors,
                        trailingIcon = {
                            if (form.isiBarang.isNotBlank()) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    val isiBarangTemplates = listOf(
                        "Pakaian",
                        "Makanan",
                        "Elektronik",
                        "Dokumen",
                        "Spare Part",
                        "Obat-obatan",
                        "Kosmetik",
                        "Aksesoris"
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(isiBarangTemplates) { template ->
                            FilterChip(
                                selected = form.isiBarang.equals(template, ignoreCase = true),
                                onClick = { form = form.copy(isiBarang = template) },
                                label = { Text(template) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SelectAllOnFocusOutlinedTextField(
                            value = form.coli,
                            emptyValueOnBlur = "1",
                            onValueChange = { form = form.copy(coli = it) },
                            label = { Text("Coli") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = fieldShape,
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                        )

                        SelectAllOnFocusOutlinedTextField(
                            value = form.beratKg,
                            emptyValueOnBlur = "1",
                            onValueChange = { form = form.copy(beratKg = it) },
                            label = { Text("Berat (kg)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = fieldShape,
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                        )
                    }

                    SelectAllOnFocusOutlinedTextField(
                        value = form.hargaPerKg,
                        onValueChange = {
                            form = form.copy(hargaPerKg = it, priceManuallyEdited = true)
                        },
                        label = { Text("Harga per kg") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = fieldShape,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = sectionCardColors,
                border = sectionCardBorder
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Pembayaran & Layanan", icon = Icons.Default.CreditCard)

                    ExposedDropdownMenuBox(
                        expanded = kirimViaExpanded,
                        onExpandedChange = { kirimViaExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = form.kirimVia.replaceFirstChar { it.uppercase() },
                            onValueChange = {},
                            readOnly = true,
                            label = { RequiredLabel("Kirim Via") },
                            isError = showValidationErrors && form.kirimVia.isBlank(),
                            placeholder = { Text("Pilih layanan") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = kirimViaExpanded) },
                            shape = fieldShape,
                            colors = fieldColors,
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .bringIntoViewRequester(kirimViaBringIntoViewRequester)
                                .focusRequester(kirimViaFocusRequester)
                        )
                        ExposedDropdownMenu(
                            expanded = kirimViaExpanded,
                            onDismissRequest = { kirimViaExpanded = false }
                        ) {
                            listOf("udara", "darat").forEach { via ->
                                DropdownMenuItem(
                                    text = { Text(via.replaceFirstChar { it.uppercase() }) },
                                    onClick = {
                                        form = form.copy(kirimVia = via)
                                        kirimViaExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = metodePembayaranExpanded,
                        onExpandedChange = { metodePembayaranExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = paymentMethodLabel(form.metodePembayaran),
                            onValueChange = {},
                            readOnly = true,
                            label = { RequiredLabel("Metode Pembayaran") },
                            isError = showValidationErrors && form.metodePembayaran.isBlank(),
                            placeholder = { Text("Pilih metode") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = metodePembayaranExpanded) },
                            shape = fieldShape,
                            colors = fieldColors,
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .bringIntoViewRequester(metodePembayaranBringIntoViewRequester)
                                .focusRequester(metodePembayaranFocusRequester)
                        )
                        ExposedDropdownMenu(
                            expanded = metodePembayaranExpanded,
                            onDismissRequest = { metodePembayaranExpanded = false }
                        ) {
                            listOf("cash", "transfer", "cod").forEach { metode ->
                                DropdownMenuItem(
                                    text = { Text(metode.uppercase()) },
                                    onClick = {
                                        form = form.copy(metodePembayaran = metode, paymentManuallySelected = true)
                                        metodePembayaranExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = agentExpanded,
                        onExpandedChange = { agentExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = form.agentCustomer,
                            onValueChange = {},
                            readOnly = true,
                            label = { RequiredLabel("Agent") },
                            isError = showValidationErrors && form.agentCustomer.isBlank(),
                            placeholder = { Text("Pilih agent") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = agentExpanded) },
                            shape = fieldShape,
                            colors = fieldColors,
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .bringIntoViewRequester(agentBringIntoViewRequester)
                                .focusRequester(agentFocusRequester)
                        )
                        ExposedDropdownMenu(
                            expanded = agentExpanded,
                            onDismissRequest = { agentExpanded = false }
                        ) {
                            resolvedAgentList.forEach { agent ->
                                DropdownMenuItem(
                                    text = { Text(agent) },
                                    onClick = {
                                        form = form.copy(agentCustomer = agent)
                                        agentExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = sectionCardColors,
                border = sectionCardBorder
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(title = "Biaya Tambahan", icon = Icons.Default.Receipt)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectAllOnFocusOutlinedTextField(
                            value = form.biayaAdmin,
                            onValueChange = { form = form.copy(biayaAdmin = it) },
                            label = { Text("Admin") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = fieldShape,
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                        )

                        SelectAllOnFocusOutlinedTextField(
                            value = form.biayaPackaging,
                            onValueChange = { form = form.copy(biayaPackaging = it) },
                            label = { Text("Packaging") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = fieldShape,
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                        )

                        SelectAllOnFocusOutlinedTextField(
                            value = form.biayaTransit,
                            onValueChange = { form = form.copy(biayaTransit = it) },
                            label = { Text("Transit") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = fieldShape,
                            colors = fieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = (form.hargaPerKg.toIntOrNull() ?: 0) > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ringkasan Biaya", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Harga per kg:", fontSize = 13.sp)
                            Text(formatRupiah(form.hargaPerKg.toIntOrNull() ?: 0), fontSize = 13.sp)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Sub total (${form.beratKg} kg):", fontSize = 13.sp)
                            Text(formatRupiah(subTotal), fontSize = 13.sp)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Biaya admin:", fontSize = 13.sp)
                            Text(formatRupiah(form.biayaAdmin.toIntOrNull() ?: 0), fontSize = 13.sp)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Biaya packaging:", fontSize = 13.sp)
                            Text(formatRupiah(form.biayaPackaging.toIntOrNull() ?: 0), fontSize = 13.sp)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Biaya transit:", fontSize = 13.sp)
                            Text(formatRupiah(form.biayaTransit.toIntOrNull() ?: 0), fontSize = 13.sp)
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(formatRupiah(total), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(form.awbNo.ifBlank { "Nomor resi belum dibuat" }, fontWeight = FontWeight.Bold)
                    Text("Tanggal: ${form.awbDate}")
                    Text("Pembayaran: ${paymentMethodLabel(form.metodePembayaran)}", fontWeight = FontWeight.Bold)
                    Text("Penerima: ${form.namaPenerima.ifBlank { "-" }}")
                }
            }

            Button(
                onClick = { validateAndSubmit() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Menyimpan...")
                } else {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simpan AWB", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            } // Column
        } // CompositionLocalProvider
    }
}

