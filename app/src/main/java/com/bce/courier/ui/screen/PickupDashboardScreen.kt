package com.bce.courier.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.set
import com.bce.courier.R
import com.bce.courier.data.model.ManifestItem
import com.google.zxing.BarcodeFormat
import com.google.zxing.Dimension
import com.google.zxing.EncodeHintType
import com.google.zxing.datamatrix.DataMatrixWriter
import com.google.zxing.datamatrix.encoder.SymbolShapeHint
import com.google.zxing.oned.Code128Writer
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupDashboardScreen(
    manifestList: List<ManifestItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<ManifestItem>,
    isSearching: Boolean,
    searchErrorMessage: String?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onCreateNew: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val isDarkMode = isSystemInDarkTheme()
    val isSearchMode = remember(searchQuery) { searchQuery.trim().isNotEmpty() }
    val displayedList = if (isSearchMode) searchResults else manifestList

    fun formatRupiah(amount: Int): String {
        return NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(amount)
    }

    fun formatDate(dateStr: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
            val date = inputFormat.parse(dateStr)
            outputFormat.format(date!!)
        } catch (_: Exception) {
            dateStr
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pickup AWB", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDarkMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                    titleContentColor = if (isDarkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = if (isDarkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = if (isDarkMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateNew,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Buat AWB Baru") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            PickupAwbSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                isSearchMode = isSearchMode,
                errorMessage = searchErrorMessage
            )

            if (isSearchMode && isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    !isSearchMode && isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    isSearchMode && isSearching && displayedList.isEmpty() -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    displayedList.isEmpty() -> {
                        val title = when {
                            isSearchMode -> "AWB tidak ditemukan"
                            else -> "Belum ada AWB"
                        }
                        val subtitle = when {
                            isSearchMode -> "Coba cek nomor AWB, atau ketik minimal 3 karakter"
                            else -> "Tap tombol + untuk membuat AWB baru"
                        }
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                title,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                subtitle,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(displayedList) { item ->
                                ManifestCard(
                                    item = item,
                                    formatRupiah = ::formatRupiah,
                                    formatDate = ::formatDate,
                                    context = context
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickupAwbSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearchMode: Boolean,
    errorMessage: String?
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Cari AWB / Agent", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            placeholder = { Text("contoh : BCEXXXX / nama agent") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            },
            supportingText = {
                if (errorMessage != null) {
                    Text(errorMessage)
                } else if (isSearchMode) {
                    Text("Cari di semua manifest (awb_no / agent_customer)")
                }
            }
        )
    }
}

@Composable
private fun ManifestCard(
    item: ManifestItem,
    formatRupiah: (Int) -> String,
    formatDate: (String) -> String,
    context: Context
) {
    var isGeneratingPdf by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.awbNo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatDate(item.awbDate),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pengirim",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = item.namaPengirim.ifBlank { "-" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Penerima",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = item.namaPenerima.ifBlank { "-" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wilayah",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = item.wilayah.ifBlank { "-" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatRupiah(item.total),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    isGeneratingPdf = true
                    generateAndOpenPdf(context, item) {
                        isGeneratingPdf = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isGeneratingPdf,
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isGeneratingPdf) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generating...")
                } else {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Print / Download PDF")
                }
            }
        }
    }
}

private fun generateAndOpenPdf(context: Context, item: ManifestItem, onComplete: () -> Unit) {
    try {
        val pdfDocument = PdfDocument()
        val pageWidth = 283
        val pageHeight = 283
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)

        drawWebStyleAwbLabel(
            context = context,
            canvas = page.canvas,
            item = item,
            pageWidth = pageWidth.toFloat(),
            pageHeight = pageHeight.toFloat()
        )

        pdfDocument.finishPage(page)

        val fileName = "AWB_${item.awbNo}.pdf"
        val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val outDir = docsDir ?: context.filesDir
        if (!outDir.exists()) outDir.mkdirs()
        val file = File(outDir, fileName)

        FileOutputStream(file).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(
                Intent.createChooser(intent, "Open with")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context.applicationContext,
                "PDF tersimpan: $fileName",
                Toast.LENGTH_LONG
            ).show()
        }
        onComplete()
    } catch (e: Exception) {
        Toast.makeText(
            context.applicationContext,
            "Error generate PDF: ${e.message}",
            Toast.LENGTH_LONG
        ).show()
        onComplete()
    }
}

@Suppress("unused")
private fun generateLegacyPdf(context: Context, item: ManifestItem, onComplete: () -> Unit) {
    try {
        val pdfDocument = PdfDocument()
        val pageWidth = 283
        val pageHeight = 283
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val paintAwbNumber = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintCorner = Paint().apply {
            color = Color.BLACK
            textSize = 6.5f
            isAntiAlias = true
        }
        val paintRecipientName = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintRecipientAddress = Paint().apply {
            color = Color.BLACK
            textSize = 8.5f
            isAntiAlias = true
        }
        val paintTitle = Paint().apply {
            color = Color.BLACK
            textSize = 8.5f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintValue = Paint().apply {
            color = Color.BLACK
            textSize = 7.5f
            isAntiAlias = true
        }
        val paintLabel = Paint().apply {
            color = Color.DKGRAY
            textSize = 6.5f
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val barcodePaint = Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
        }
        val whiteText = Paint().apply {
            color = Color.WHITE
            textSize = 10f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val outerMargin = 6f
        canvas.drawRect(outerMargin, outerMargin, pageWidth - outerMargin, pageHeight - outerMargin, borderPaint)

        var y = outerMargin + 10f
        val leftColumn = outerMargin + 8f
        val qrSize = 95
        val rightContentX = pageWidth - outerMargin - qrSize.toFloat() - 12f

        canvas.drawText("BCEEXPRESS", leftColumn, y, paintCorner)

        val barcodeBitmap = generateCode128Barcode(item.awbNo)
        val barcodeHeight = barcodeBitmap?.height ?: 18
        val barcodeTop = y + 3f
        if (barcodeBitmap != null) {
            val barcodeLeft = leftColumn
            canvas.drawBitmap(barcodeBitmap, barcodeLeft, barcodeTop, barcodePaint)
        }

        val awbTextWidth = paintAwbNumber.measureText(item.awbNo)
        val fm = paintAwbNumber.fontMetrics
        val barcodeCenterY = barcodeTop + barcodeHeight / 2f
        val awbBaseline = barcodeCenterY - (fm.ascent + fm.descent) / 2f
        val awbX = pageWidth - outerMargin - 6f - awbTextWidth
        canvas.drawText(item.awbNo, awbX, awbBaseline, paintAwbNumber)

        y += 26f
        canvas.drawLine(leftColumn - 2f, y, pageWidth - leftColumn + 2f, y, borderPaint)
        y += 8f

        val qrBitmap = generateQRCode(item.awbNo)
        val qrTop = y + 16f
        if (qrBitmap != null) {
            val qrLeft = pageWidth - outerMargin - qrSize.toFloat() - 4f
            canvas.drawBitmap(qrBitmap, qrLeft, qrTop, null)
        }

        canvas.drawText("TANGGAL", leftColumn, y, paintLabel)
        y += 8f
        canvas.drawText(item.awbDate, leftColumn, y, paintValue)
        y += 12f

        canvas.drawText("SHIP FROM", leftColumn, y, paintLabel)
        y += 8f
        canvas.drawText(item.namaPengirim.ifBlank { "-" }, leftColumn, y, paintTitle)
        y += 8f
        if (item.nomorPengirim.isNotBlank()) {
            canvas.drawText("Telp: ${item.nomorPengirim}", leftColumn, y, paintValue)
            y += 8f
        }
        y += 4f

        canvas.drawText("SHIP TO", leftColumn, y, paintLabel)
        y += 8f
        canvas.drawText(item.namaPenerima.ifBlank { "-" }, leftColumn, y, paintRecipientName)
        y += 9f
        val maxAddressWidth = (rightContentX - leftColumn - 8f).toInt()
        val addressLines = wrapTextByWidth(item.alamatPenerima.ifBlank { "-" }, paintRecipientAddress, maxAddressWidth)
        addressLines.take(2).forEach { line ->
            canvas.drawText(line, leftColumn, y, paintRecipientAddress)
            y += 9f
        }
        if (item.nomorPenerima.isNotBlank()) {
            canvas.drawText("Telp: ${item.nomorPenerima}", leftColumn, y, paintValue)
            y += 8f
        }
        y += 5f

        canvas.drawText("KOTA", leftColumn, y, paintLabel)
        canvas.drawText("BERAT", leftColumn + 85f, y, paintLabel)
        y += 7f
        canvas.drawText(item.wilayah.ifBlank { "-" }, leftColumn, y, paintValue)
        canvas.drawText("${item.beratKg} kg", leftColumn + 85f, y, paintValue)
        y += 9f

        canvas.drawText("KOLI", leftColumn, y, paintLabel)
        canvas.drawText("ISI BARANG", leftColumn + 85f, y, paintLabel)
        y += 7f
        canvas.drawText("${item.coli} pcs", leftColumn, y, paintValue)
        val isiBarangText = if (item.isiBarang.length > 15) item.isiBarang.take(12) + "..." else item.isiBarang.ifBlank { "-" }
        canvas.drawText(isiBarangText, leftColumn + 85f, y, paintValue)
        y += 14f

        if (item.subTotal > 0 || item.biayaAdmin > 0 || item.biayaPackaging > 0 || item.biayaTransit > 0) {
            canvas.drawLine(leftColumn - 2f, y, rightContentX - 2f, y, borderPaint)
            y += 10f
            canvas.drawText("RINCIAN BIAYA", leftColumn, y, paintLabel)
            y += 10f
            val biayaValueX = leftColumn + 95f
            listOf(
                "Sub Total" to item.subTotal,
                "Biaya Admin" to item.biayaAdmin,
                "Biaya Packaging" to item.biayaPackaging,
                "Biaya Transit" to item.biayaTransit
            ).forEach { (label, value) ->
                if (value > 0) {
                    canvas.drawText(label, leftColumn, y, paintValue)
                    val formatted = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(value)
                    canvas.drawText(formatted, biayaValueX, y, paintValue)
                    y += 8f
                }
            }
        }

        // payment method — place above total box
        if (item.metodePembayaran.isNotBlank()) {
            val metodeText = Paint().apply {
                color = Color.BLACK
                textSize = 7f
                isAntiAlias = true
            }
            val metodeY = pageHeight - outerMargin - 28f
            canvas.drawText("Metode: ${item.metodePembayaran.uppercase()}", leftColumn, metodeY, metodeText)
        }

        // total box: ensure it fits above outer margin and center text vertically
        val boxTop = pageHeight - outerMargin - 22f
        val boxBottom = pageHeight - outerMargin
        canvas.drawRect(leftColumn - 2f, boxTop, pageWidth - leftColumn + 2f, boxBottom, Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        })
        val totalFormatted = NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(item.total)
        val boxCenterY = (boxTop + boxBottom) / 2f
        val fmWhite = whiteText.fontMetrics
        val baselineWhite = boxCenterY - (fmWhite.ascent + fmWhite.descent) / 2f
        canvas.drawText("TOTAL BAYAR", leftColumn, baselineWhite, whiteText)
        val totalTextWidth = whiteText.measureText(totalFormatted)
        val totalX = pageWidth - leftColumn - 6f - totalTextWidth
        canvas.drawText(totalFormatted, totalX, baselineWhite, whiteText)

        // draw abbreviated agent_customer below QR code (jangan menimpa QR)
        val agentName = item.agentCustomer.ifBlank { "" }
        if (agentName.isNotBlank()) {
            val abbr = abbreviateAgent(agentName)
            val paintAbbr = Paint().apply {
                color = Color.BLACK
                textSize = 16f
                isFakeBoldText = true
                isAntiAlias = true
            }
            val qrBottom = qrTop + qrSize.toFloat()
            val abbrY = qrBottom + 10f
            val abbrWidth = paintAbbr.measureText(abbr)
            val abbrX = pageWidth - outerMargin - qrSize.toFloat() / 2f - abbrWidth / 2f
            canvas.drawText(abbr, abbrX, abbrY, paintAbbr)
        }

        pdfDocument.finishPage(page)

        val fileName = "AWB_${item.awbNo}.pdf"
        val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val outDir = docsDir ?: context.filesDir
        if (!outDir.exists()) outDir.mkdirs()
        val file = File(outDir, fileName)

        FileOutputStream(file).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context.applicationContext, "PDF tersimpan: $fileName", Toast.LENGTH_LONG).show()
        }

        onComplete()

    } catch (e: Exception) {
        Toast.makeText(context.applicationContext, "Error generate PDF: ${e.message}", Toast.LENGTH_LONG).show()
        onComplete()
    }
}

private fun drawWebStyleAwbLabel(
    context: Context,
    canvas: Canvas,
    item: ManifestItem,
    pageWidth: Float,
    pageHeight: Float
) {
    canvas.drawColor(Color.WHITE)

    val blackLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 1.15f
    }
    val heavyLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 1.7f
    }

    // Grid ported from the webapp's 100 mm x 100 mm AWB V2 layout.
    val left = 8.5f
    val right = pageWidth - 8.5f
    val top = 5.7f
    val bottom = pageHeight - 4.25f
    val headerBottom = top + 36.85f
    val awbBottom = headerBottom + 36.85f
    val partiesBottom = awbBottom + 65.2f
    val summaryBottom = partiesBottom + 25.5f
    val footerTop = bottom - 17f

    drawBceLogo(
        context,
        canvas,
        RectF(left, top + 1.4f, left + 110.5f, headerBottom - 0.5f)
    )

    val routeInfo = buildRouteInfo(item)
    val routeRect = RectF(right - 90.7f, top, right, top + 31.2f)
    canvas.drawRect(routeRect, heavyLine)
    if (routeInfo.secondary.isBlank()) {
        drawCenteredText(canvas, routeInfo.primary, routeRect, 14f, 8f, bold = true)
    } else {
        drawSingleLine(
            canvas = canvas,
            text = routeInfo.primary,
            left = routeRect.left,
            baseline = routeRect.top + 13.5f,
            maxWidth = routeRect.width(),
            maxSize = 13.5f,
            minSize = 8f,
            bold = true,
            align = Paint.Align.CENTER
        )
        drawSingleLine(
            canvas = canvas,
            text = routeInfo.secondary,
            left = routeRect.left,
            baseline = routeRect.top + 27f,
            maxWidth = routeRect.width(),
            maxSize = 12.75f,
            minSize = 8f,
            bold = true,
            align = Paint.Align.CENTER
        )
    }

    val normalizedAwb = normalizeBarcodeValue(item.awbNo)
    drawCenteredText(
        canvas,
        "AWB: $normalizedAwb",
        RectF(left, headerBottom, right, awbBottom),
        maxSize = 25.5f,
        minSize = 15f,
        bold = true
    )
    canvas.drawLine(left, awbBottom, right, awbBottom, heavyLine)

    val senderRight = left + (right - left) * 0.38f
    canvas.drawLine(senderRight, awbBottom, senderRight, partiesBottom, blackLine)
    canvas.drawLine(left, partiesBottom, right, partiesBottom, heavyLine)

    canvas.drawText("SHIP FROM", left, awbBottom + 14f, textPaint(11.25f, bold = true))
    drawUserIcon(canvas, senderRight - 10f, awbBottom + 9f, 8f, blackLine)

    val shipToTitleRect = RectF(senderRight, awbBottom, senderRight + 62f, awbBottom + 17f)
    canvas.drawRect(shipToTitleRect, blackLine)
    drawCenteredText(canvas, "SHIP TO", shipToTitleRect, 11.25f, 8f, bold = true)
    drawLocationIcon(canvas, right - 8f, awbBottom + 9f, 8f, blackLine)

    drawSingleLine(
        canvas,
        item.namaPengirim.ifBlank { "Nama pengirim" },
        left,
        awbBottom + 30f,
        senderRight - left - 4f,
        maxSize = 13.5f,
        minSize = 6f,
        bold = true
    )
    drawSingleLine(
        canvas,
        item.nomorPengirim.ifBlank { "-" },
        left,
        awbBottom + 40f,
        senderRight - left - 4f,
        maxSize = 9f,
        minSize = 6f
    )
    drawSingleLine(
        canvas,
        "Isi Barang: ${item.isiBarang.ifBlank { "-" }}",
        left,
        awbBottom + 50.5f,
        senderRight - left - 4f,
        maxSize = 9f,
        minSize = 5.5f
    )

    val receiverLeft = senderRight + 10f
    drawSingleLine(
        canvas,
        item.namaPenerima.ifBlank { "Nama penerima" },
        receiverLeft,
        awbBottom + 31.5f,
        right - receiverLeft - 3f,
        maxSize = 14.25f,
        minSize = 6f,
        bold = true
    )
    val addressPaint = textPaint(addressFontSize(item.alamatPenerima))
    val receiverWidth = right - receiverLeft - 3f
    val addressLines = wrapTextByWidth(
        item.alamatPenerima.ifBlank { "-" },
        addressPaint,
        receiverWidth.toInt()
    ).take(2)
    var addressY = awbBottom + 42f
    val addressLineHeight = (addressPaint.textSize * 1.06f).coerceAtLeast(6.2f)
    addressLines.forEach { line ->
        canvas.drawText(line, receiverLeft, addressY, addressPaint)
        addressY += addressLineHeight
    }
    if (item.nomorPenerima.isNotBlank()) {
        drawSingleLine(
            canvas,
            item.nomorPenerima,
            receiverLeft,
            partiesBottom - 4f,
            receiverWidth,
            maxSize = 7.8f,
            minSize = 5.5f
        )
    }

    val summaryWidths = floatArrayOf(0.16f, 0.15f, 0.18f, 0.22f, 0.29f)
    val summaryEdges = mutableListOf(left)
    summaryWidths.forEach { fraction ->
        summaryEdges += summaryEdges.last() + (right - left) * fraction
    }
    for (index in 1 until summaryEdges.lastIndex) {
        canvas.drawLine(
            summaryEdges[index],
            partiesBottom,
            summaryEdges[index],
            summaryBottom,
            blackLine
        )
    }
    canvas.drawLine(left, summaryBottom, right, summaryBottom, heavyLine)

    drawParcelIcon(canvas, summaryEdges[0] + 7f, partiesBottom + 12.7f, 8f, blackLine)
    val coli = item.coli.coerceAtLeast(1)
    drawSummaryText(
        canvas,
        "COLI",
        "$coli ${if (coli == 1) "Piece" else "Pieces"}",
        summaryEdges[0] + 14f,
        partiesBottom,
        summaryEdges[1] - summaryEdges[0] - 15f,
        valueMaxSize = 7f
    )
    drawScaleIcon(canvas, summaryEdges[1] + 7f, partiesBottom + 12.7f, 8f, blackLine)
    drawSummaryText(
        canvas,
        "BERAT",
        "${formatWeight(item.beratKg)} KG",
        summaryEdges[1] + 14f,
        partiesBottom,
        summaryEdges[2] - summaryEdges[1] - 15f,
        valueMaxSize = 7.5f
    )
    val additionalCost = item.biayaAdmin + item.biayaPackaging + item.biayaTransit
    drawSummaryText(
        canvas,
        "BIAYA LAIN",
        formatRupiahLabel(additionalCost),
        summaryEdges[2] + 3f,
        partiesBottom,
        summaryEdges[3] - summaryEdges[2] - 5f,
        valueMaxSize = 7.5f
    )
    drawCalendarIcon(canvas, summaryEdges[3] + 7f, partiesBottom + 12.7f, 8f, blackLine)
    drawSummaryText(
        canvas,
        "TANGGAL",
        item.awbDate.ifBlank { "-" },
        summaryEdges[3] + 14f,
        partiesBottom,
        summaryEdges[4] - summaryEdges[3] - 15f,
        valueMaxSize = 7.5f
    )
    val totalValue = if (item.total > 0) item.total else additionalCost
    drawSummaryText(
        canvas,
        "TOTAL",
        formatRupiahLabel(totalValue),
        summaryEdges[4] + 5f,
        partiesBottom,
        right - summaryEdges[4] - 6f,
        valueMaxSize = 12.75f,
        valueMinSize = 7.5f,
        valueBold = true
    )

    val codesTop = summaryBottom + 4.25f
    val agentRight = left + 59.5f
    val qrLeft = right - 76.5f
    val matrixLeft = agentRight + 5.7f
    val matrixRight = qrLeft - 5.7f

    val agentBox = RectF(left, codesTop, agentRight, codesTop + 68f)
    canvas.drawRect(agentBox, heavyLine)
    val agentCode = generateAgentCode(item.agentCustomer)
    drawCenteredText(
        canvas,
        agentCode,
        RectF(agentBox.left + 3f, agentBox.top, agentBox.right - 3f, agentBox.bottom),
        maxSize = if (agentCode.length >= 4) 17.25f else 21f,
        minSize = 11f,
        bold = true
    )
    if (isPerishable(item.isiBarang)) {
        val urgentRect = RectF(
            left,
            agentBox.bottom + 4.25f,
            left + 51f,
            agentBox.bottom + 18.5f
        )
        canvas.drawRect(urgentRect, heavyLine)
        drawCenteredText(canvas, "SEGERA", urgentRect, 10.5f, 8f, bold = true)
    }

    val matrixBitmap = generateDataMatrixBarcode(normalizedAwb)
    val matrixBounds = matrixBitmap?.let {
        val destination = drawBitmapContained(
            canvas,
            it,
            RectF(matrixLeft, codesTop + 1.4f, matrixRight, codesTop + 52f)
        )
        it.recycle()
        destination
    }
    matrixBounds?.let { bounds ->
        val detailsTop = bounds.bottom + 3f
        canvas.drawLine(matrixLeft, detailsTop, matrixRight, detailsTop, blackLine)
        drawSingleLine(
            canvas,
            item.kirimVia.ifBlank { "-" }.uppercase(),
            matrixLeft,
            detailsTop + 14f,
            matrixRight - matrixLeft,
            maxSize = 10f,
            minSize = 7f,
            bold = true,
            align = Paint.Align.CENTER
        )
    }

    val qrBitmap = generateQRCode(normalizedAwb)
    qrBitmap?.let {
        val qrSize = 68f
        val qrX = qrLeft + (right - qrLeft - qrSize) / 2f
        canvas.drawBitmap(
            it,
            null,
            RectF(qrX, codesTop, qrX + qrSize, codesTop + qrSize),
            barcodePaint()
        )
        it.recycle()
    }
    drawSingleLine(
        canvas,
        paymentMethodCode(item.metodePembayaran),
        qrLeft,
        codesTop + 82f,
        right - qrLeft,
        maxSize = 12.75f,
        minSize = 8f,
        bold = true,
        align = Paint.Align.CENTER
    )

    canvas.drawLine(left, footerTop, right, footerTop, blackLine)
    val termsPaint = textPaint(4.5f).apply { textSkewX = -0.15f }
    canvas.drawText("*Syarat dan ketentuan pengiriman", left, footerTop + 7f, termsPaint)
    canvas.drawText("sesuai dengan kebijakan BCE Express", left, footerTop + 12.5f, termsPaint)
    drawSingleLine(
        canvas,
        "www.bcexp.id",
        left + 150f,
        footerTop + 10f,
        48f,
        maxSize = 5.25f,
        minSize = 4.5f,
        align = Paint.Align.CENTER
    )
    drawPhoneIcon(canvas, right - 48f, footerTop + 8f, 7f, blackLine)
    drawSingleLine(
        canvas,
        "CS: 081290568532",
        right - 40f,
        footerTop + 10f,
        40f,
        maxSize = 5.25f,
        minSize = 4f
    )
}

private fun generateDataMatrixBarcode(content: String): Bitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.DATA_MATRIX_SHAPE to SymbolShapeHint.FORCE_RECTANGLE,
            EncodeHintType.MIN_SIZE to Dimension(48, 16)
        )
        val matrix = DataMatrixWriter().encode(
            content,
            BarcodeFormat.DATA_MATRIX,
            0,
            0,
            hints
        )
        val quietZone = 2
        val bitmap = createBitmap(matrix.width + quietZone * 2, matrix.height + quietZone * 2)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val matrixX = x - quietZone
                val matrixY = y - quietZone
                bitmap[x, y] = if (
                    matrixX in 0 until matrix.width &&
                    matrixY in 0 until matrix.height &&
                    matrix[matrixX, matrixY]
                ) {
                    Color.BLACK
                } else {
                    Color.WHITE
                }
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}

private fun generateCode128Barcode(content: String): Bitmap? {
    return try {
        val width = 150
        val height = 22
        val hints = mapOf(EncodeHintType.MARGIN to 2)

        val bitMatrix = Code128Writer().encode(
            content,
            BarcodeFormat.CODE_128,
            width,
            height,
            hints
        )

        val bitmap = createBitmap(bitMatrix.width, bitMatrix.height)
        for (y in 0 until bitMatrix.height) {
            for (x in 0 until bitMatrix.width) {
                bitmap[x, y] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }

        bitmap
    } catch (_: Exception) {
        null
    }
}

private fun generateQRCode(content: String): Bitmap? {
    return try {
        val size = 95
        val qrWriter = QRCodeWriter()
        val bitMatrix = qrWriter.encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(EncodeHintType.MARGIN to 1)
        )
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = createBitmap(width, height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap[x, y] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}

private fun textPaint(
    size: Float,
    bold: Boolean = false,
    align: Paint.Align = Paint.Align.LEFT
): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.BLACK
    textSize = size
    isFakeBoldText = bold
    textAlign = align
}

private fun barcodePaint(): Paint = Paint().apply {
    isAntiAlias = false
    isFilterBitmap = false
}

private fun drawSingleLine(
    canvas: Canvas,
    text: String,
    left: Float,
    baseline: Float,
    maxWidth: Float,
    maxSize: Float,
    minSize: Float,
    bold: Boolean = false,
    align: Paint.Align = Paint.Align.LEFT
) {
    if (maxWidth <= 0f) return
    val normalized = text.trim().ifEmpty { "-" }
    val paint = textPaint(maxSize, bold, align)
    while (paint.textSize > minSize && paint.measureText(normalized) > maxWidth) {
        paint.textSize = (paint.textSize - 0.5f).coerceAtLeast(minSize)
    }
    val fitted = ellipsizeToWidth(normalized, paint, maxWidth)
    val x = when (align) {
        Paint.Align.CENTER -> left + maxWidth / 2f
        Paint.Align.RIGHT -> left + maxWidth
        else -> left
    }
    canvas.save()
    canvas.clipRect(left, baseline + paint.fontMetrics.top, left + maxWidth, baseline + paint.fontMetrics.bottom)
    canvas.drawText(fitted, x, baseline, paint)
    canvas.restore()
}

private fun drawCenteredText(
    canvas: Canvas,
    text: String,
    rect: RectF,
    maxSize: Float,
    minSize: Float,
    bold: Boolean = false
) {
    val paint = textPaint(maxSize, bold, Paint.Align.CENTER)
    while (paint.textSize > minSize && paint.measureText(text) > rect.width() - 4f) {
        paint.textSize = (paint.textSize - 0.5f).coerceAtLeast(minSize)
    }
    val fitted = ellipsizeToWidth(text, paint, rect.width() - 4f)
    val metrics = paint.fontMetrics
    val baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2f
    canvas.save()
    canvas.clipRect(rect)
    canvas.drawText(fitted, rect.centerX(), baseline, paint)
    canvas.restore()
}

private fun ellipsizeToWidth(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    val ellipsis = "…"
    val allowed = (maxWidth - paint.measureText(ellipsis)).coerceAtLeast(0f)
    val count = paint.breakText(text, true, allowed, null).coerceAtLeast(0)
    return text.take(count).trimEnd() + ellipsis
}

private fun drawSummaryText(
    canvas: Canvas,
    label: String,
    value: String,
    left: Float,
    top: Float,
    width: Float,
    valueMaxSize: Float,
    valueMinSize: Float = 5.25f,
    valueBold: Boolean = false
) {
    drawSingleLine(
        canvas,
        label,
        left,
        top + 8f,
        width,
        maxSize = 5.25f,
        minSize = 4.5f
    )
    drawSingleLine(
        canvas,
        value,
        left,
        top + 19f,
        width,
        maxSize = valueMaxSize,
        minSize = valueMinSize,
        bold = valueBold
    )
}

private fun drawBceLogo(context: Context, canvas: Canvas, destination: RectF) {
    val logo = BitmapFactory.decodeResource(context.resources, R.drawable.splash_logo) ?: return
    val source = visibleAlphaBounds(logo) ?: Rect(0, 0, logo.width, logo.height)
    canvas.drawBitmap(logo, source, destination, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    })
    logo.recycle()
}

private fun visibleAlphaBounds(bitmap: Bitmap): Rect? {
    var minX = bitmap.width
    var minY = bitmap.height
    var maxX = -1
    var maxY = -1
    for (y in 0 until bitmap.height) {
        for (x in 0 until bitmap.width) {
            if (Color.alpha(bitmap[x, y]) > 8) {
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
        }
    }
    return if (maxX >= minX && maxY >= minY) {
        Rect(minX, minY, maxX + 1, maxY + 1)
    } else {
        null
    }
}

private fun drawBitmapContained(canvas: Canvas, bitmap: Bitmap, bounds: RectF): RectF {
    val scale = minOf(bounds.width() / bitmap.width, bounds.height() / bitmap.height)
    val width = bitmap.width * scale
    val height = bitmap.height * scale
    val destination = RectF(
        bounds.centerX() - width / 2f,
        bounds.top,
        bounds.centerX() + width / 2f,
        bounds.top + height
    )
    canvas.drawBitmap(bitmap, null, destination, barcodePaint())
    return destination
}

private fun normalizeBarcodeValue(value: String): String =
    value.trim().uppercase().filter { it.isLetterOrDigit() }

private fun addressFontSize(address: String): Float = when {
    address.length > 180 -> 4.05f
    address.length > 140 -> 4.35f
    address.length > 100 -> 4.9f
    address.length > 72 -> 5.55f
    address.length > 48 -> 6.4f
    address.length > 30 -> 7.5f
    address.length > 18 -> 8.6f
    else -> 9.75f
}

private fun formatWeight(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString().trimEnd('0').trimEnd('.')

private fun formatRupiahLabel(value: Int): String {
    val formatted = NumberFormat.getNumberInstance(Locale("id", "ID")).format(value)
    return "Rp $formatted"
}

private fun generateAgentCode(agentName: String): String {
    val normalized = agentName.trim().uppercase()
    if (normalized.isBlank()) return "AGEN"
    val compact = normalized.filter { it.isLetterOrDigit() }
    if (compact.length <= 4) return compact
    val initials = normalized
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .mapNotNull { it.firstOrNull() }
        .joinToString("")
    return if (initials.length >= 2) initials.take(4) else compact.take(4)
}

private fun paymentMethodCode(method: String): String = when (method.trim().uppercase()) {
    "TRANSFER" -> "TRF"
    "CASH" -> "CASH"
    "COD" -> "COD"
    else -> method.trim().uppercase()
}

private fun isPerishable(goods: String): Boolean {
    val upper = goods.uppercase()
    return listOf(
        "MB",
        "MAKANAN BASAH",
        "IKAN",
        "DAGING",
        "SEAFOOD",
        "UDANG",
        "KEPITING",
        "CUMI",
        "AYAM"
    ).any { upper.contains(it) }
}

private fun drawUserIcon(canvas: Canvas, centerX: Float, centerY: Float, size: Float, paint: Paint) {
    val fill = Paint(paint).apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    canvas.drawCircle(centerX, centerY - size * 0.25f, size * 0.23f, fill)
    val shoulders = Path().apply {
        moveTo(centerX - size * 0.48f, centerY + size * 0.48f)
        cubicTo(
            centerX - size * 0.45f,
            centerY + size * 0.16f,
            centerX - size * 0.22f,
            centerY + size * 0.04f,
            centerX,
            centerY + size * 0.04f
        )
        cubicTo(
            centerX + size * 0.22f,
            centerY + size * 0.04f,
            centerX + size * 0.45f,
            centerY + size * 0.16f,
            centerX + size * 0.48f,
            centerY + size * 0.48f
        )
        close()
    }
    canvas.drawPath(shoulders, fill)
}

private fun drawLocationIcon(canvas: Canvas, centerX: Float, centerY: Float, size: Float, paint: Paint) {
    canvas.drawCircle(centerX, centerY - size * 0.12f, size * 0.34f, paint)
    canvas.drawCircle(centerX, centerY - size * 0.12f, size * 0.1f, paint)
    canvas.drawLine(
        centerX - size * 0.24f,
        centerY + size * 0.12f,
        centerX,
        centerY + size * 0.55f,
        paint
    )
    canvas.drawLine(
        centerX + size * 0.24f,
        centerY + size * 0.12f,
        centerX,
        centerY + size * 0.55f,
        paint
    )
}

private fun drawParcelIcon(canvas: Canvas, centerX: Float, centerY: Float, size: Float, paint: Paint) {
    val rect = RectF(centerX - size / 2f, centerY - size * 0.35f, centerX + size / 2f, centerY + size * 0.35f)
    canvas.drawRect(rect, paint)
    canvas.drawLine(centerX, rect.top, centerX, rect.bottom, paint)
    canvas.drawLine(rect.left, rect.top, centerX, rect.top + size * 0.22f, paint)
    canvas.drawLine(rect.right, rect.top, centerX, rect.top + size * 0.22f, paint)
}

private fun drawScaleIcon(canvas: Canvas, centerX: Float, centerY: Float, size: Float, paint: Paint) {
    val outline = Paint(paint).apply {
        style = Paint.Style.STROKE
        strokeWidth = (size * 0.12f).coerceAtLeast(0.85f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    canvas.drawArc(
        RectF(
            centerX - size * 0.23f,
            centerY - size * 0.5f,
            centerX + size * 0.23f,
            centerY - size * 0.04f
        ),
        180f,
        180f,
        false,
        outline
    )
    val weightBody = Path().apply {
        moveTo(centerX - size * 0.3f, centerY - size * 0.08f)
        lineTo(centerX - size * 0.48f, centerY + size * 0.46f)
        lineTo(centerX + size * 0.48f, centerY + size * 0.46f)
        lineTo(centerX + size * 0.3f, centerY - size * 0.08f)
        close()
    }
    canvas.drawPath(weightBody, outline)
}

private fun drawCalendarIcon(canvas: Canvas, centerX: Float, centerY: Float, size: Float, paint: Paint) {
    val rect = RectF(centerX - size / 2f, centerY - size * 0.42f, centerX + size / 2f, centerY + size * 0.42f)
    canvas.drawRect(rect, paint)
    canvas.drawLine(rect.left, centerY - size * 0.15f, rect.right, centerY - size * 0.15f, paint)
    canvas.drawLine(centerX - size * 0.25f, rect.top - size * 0.15f, centerX - size * 0.25f, rect.top + size * 0.15f, paint)
    canvas.drawLine(centerX + size * 0.25f, rect.top - size * 0.15f, centerX + size * 0.25f, rect.top + size * 0.15f, paint)
}

private fun drawPhoneIcon(canvas: Canvas, centerX: Float, centerY: Float, size: Float, paint: Paint) {
    val scale = size / 24f
    val originX = centerX - size / 2f
    val originY = centerY - size / 2f
    fun px(value: Float) = originX + value * scale
    fun py(value: Float) = originY + value * scale

    val handset = Path().apply {
        moveTo(px(6.62f), py(10.79f))
        cubicTo(px(8.06f), py(13.62f), px(10.38f), py(15.94f), px(13.21f), py(17.38f))
        lineTo(px(15.41f), py(15.18f))
        cubicTo(px(15.68f), py(14.91f), px(16.08f), py(14.82f), px(16.43f), py(14.94f))
        cubicTo(px(17.55f), py(15.31f), px(18.76f), py(15.51f), px(20f), py(15.51f))
        cubicTo(px(20.55f), py(15.51f), px(21f), py(15.96f), px(21f), py(16.51f))
        lineTo(px(21f), py(20f))
        cubicTo(px(21f), py(20.55f), px(20.55f), py(21f), px(20f), py(21f))
        cubicTo(px(10.61f), py(21f), px(3f), py(13.39f), px(3f), py(4f))
        cubicTo(px(3f), py(3.45f), px(3.45f), py(3f), px(4f), py(3f))
        lineTo(px(7.5f), py(3f))
        cubicTo(px(8.05f), py(3f), px(8.5f), py(3.45f), px(8.5f), py(4f))
        cubicTo(px(8.5f), py(5.25f), px(8.7f), py(6.45f), px(9.07f), py(7.57f))
        cubicTo(px(9.18f), py(7.92f), px(9.1f), py(8.31f), px(8.82f), py(8.59f))
        close()
    }
    canvas.drawPath(handset, Paint(paint).apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    })
}

private data class AwbRouteInfo(val primary: String, val secondary: String)

private val airportRouteCodes = mapOf(
    "PANGKAL PINANG" to "PGK",
    "SUNGAILIAT" to "SLT",
    "BELINYU" to "BLY",
    "JEBUS" to "JBS",
    "KOBA" to "KBA",
    "TOBOALI" to "TBL",
    "MENTOK" to "MNT",
    "PONTIANAK" to "PNK",
    "SINGKAWANG" to "SKW",
    "SUNGAI PINYUH" to "SPY",
    "TJ PANDAN" to "TJQ",
    "TANJUNG PANDAN" to "TJQ",
    "DENPASAR" to "DPS",
    "JAKARTA BARAT" to "JKB",
    "JAKARTA PUSAT" to "JKP",
    "JAKARTA TIMUR" to "JKT",
    "JAKARTA SELATAN" to "JKS",
    "JAKARTA UTARA" to "JKU",
    "TANGERANG" to "TNG",
    "TANGERANG SELATAN" to "TGS",
    "TANGERANG KABUPATEN" to "TGK",
    "BEKASI KOTA" to "BKK",
    "BEKASI KABUPATEN" to "BKB",
    "DEPOK" to "DPK",
    "BOGOR KOTA" to "BGR",
    "BOGOR KABUPATEN" to "BGB",
    "JKT" to "JKT",
    "TGT" to "TGR",
    "BKS" to "BKS",
    "DPK" to "DPK",
    "BGR" to "BGR"
)

private val kmyAreaNames = setOf(
    "BABAKAN MADANG", "BABELAN", "BANTARGEBANG", "BEJI", "BEKASI BARAT",
    "BEKASI SELATAN", "BEKASI TIMUR", "BEKASI UTARA", "BOGOR SELATAN",
    "BOGOR TENGAH", "BOGOR TIMUR", "BOGOR UTARA", "BOJONGGEDE", "CAKUNG",
    "CEMPAKA PUTIH", "CIBARUSAH", "CIBINONG", "CIBITUNG", "CIKARANG BARAT",
    "CIKARANG PUSAT", "CIKARANG SELATAN", "CIKARANG TIMUR", "CIKARANG UTARA",
    "CILEUNGSI", "CILINCING", "CILODONG", "CIMANGGIS", "CINERE", "CIPAYUNG",
    "CIRACAS", "DUREN SAWIT", "GAMBIR", "GUNUNG PUTRI", "JATIASIH",
    "JATISAMPURNA", "JATINEGARA", "JOHAR BARU", "KARANGBAHAGIA",
    "KEDUNGWARINGIN", "KELAPA GADING", "KEMAYORAN", "KOJA", "KRAMAT JATI",
    "KREKOT / PUSAT", "LIMO", "MAKASAR", "MATRAMAN", "MEDAN SATRIA",
    "MENTENG", "MUSTIKAJAYA", "PADEMANGAN", "PAL MERAH", "PANCORAN",
    "PANCORAN MAS", "PASAR MINGGU", "PASAR REBO", "PENJARINGAN",
    "PONDOKGEDE", "PONDOKMELATI", "PULO GADUNG", "RAWALUMBU", "SAWAH BESAR",
    "SAWANGAN", "SENEN", "SERANG BARU", "SETIABUDI", "SETU", "SUKMAJAYA",
    "TAMAN SARI", "TAMBORA", "TAMBUN SELATAN", "TAMBUN UTARA", "TANAH ABANG",
    "TANAH SEREAL", "TANJUNG PRIOK", "KEBON BAWANG", "PAPANGGO",
    "SUNGAI BAMBU", "TJ PRIOK", "WARAKAS", "TAPOS", "TARUMAJAYA", "TEBET",
    "SUNTER AGUNG", "SUNTER JAYA"
)

private val glcAreaNames = setOf(
    "BALARAJA", "BATUCEPER", "BEKASI KABUPATEN", "BENDA", "BOGOR BARAT",
    "BOGOR KABUPATEN", "BOGOR KOTA", "BOGOR KABUPATEN", "CENGKARENG",
    "CIBODAS", "CIKUPA", "CILANDAK", "CILEDUG", "CIPONDOH", "CIPUTAT",
    "CIPUTAT TIMUR", "CISAUK", "CURUG", "GREENLAKE CITY / BARAT", "GROGOL",
    "GUNUNG SINDUR", "JAGAKARSA", "JATIUWUNG", "KALI DERES", "KARANGTENGAH",
    "KARAWACI", "KEBAYORAN BARU", "KEBAYORAN LAMA", "KEBON JERUK", "KELAPA DUA",
    "KEMBANGAN", "KOSAMBI", "LARANGAN", "LEGOK", "MAMPANG PRAPATAN",
    "NEGLASARI", "PAGEDANGAN", "PAKUHAJI", "PAMULANG", "PANONGAN",
    "PASAR KEMIS", "PESANGGRAHAN", "PINANG", "PONDOK AREN", "RAJEG",
    "SEPATAN", "SEPATAN TIMUR", "SERPONG", "SERPONG UTARA", "SINDANG JAYA",
    "SOLEAR", "TANAH ABANG (GELORA)", "TANGERANG", "TANGERANG KABUPATEN",
    "TANGERANG SELATAN", "TELUKNAGA", "TELUK NAGA", "MAUK", "TIGARAKSA"
)

private fun buildRouteInfo(item: ManifestItem): AwbRouteInfo {
    val city = normalizeRouteName(item.kotaTujuan)
    val locality = normalizeRouteName(item.wilayah.ifBlank { item.kotaTujuan })
    val destinationCode = airportRouteCodes[locality]
        ?: airportRouteCodes[city]
        ?: compactRouteCode(item.wilayah)
    val localityCode = locality.filter { it.isLetterOrDigit() }.take(3)
    val primaryCodes = listOf(destinationCode, localityCode)
        .filter { it.isNotBlank() }
        .distinct()
    val areaCode = when {
        locality in kmyAreaNames || city in kmyAreaNames -> "KMY"
        locality in glcAreaNames || city in glcAreaNames -> "GLC"
        else -> ""
    }
    val primary = primaryCodes.joinToString(" | ").ifBlank {
        locality.ifBlank { city.ifBlank { "-" } }
    }
    val secondary = areaCode.takeIf { it !in primaryCodes }.orEmpty()
    return AwbRouteInfo(primary, secondary)
}

private fun normalizeRouteName(value: String): String =
    value.trim().uppercase().replace(Regex("\\s+"), " ")

private fun compactRouteCode(value: String): String {
    val normalized = normalizeRouteName(value)
    return if (normalized.matches(Regex("[A-Z0-9]{2,5}"))) normalized else ""
}

private fun wrapTextByWidth(text: String, paint: Paint, maxWidth: Int): List<String> {
    if (paint.measureText(text) <= maxWidth) return listOf(text)
    val lines = mutableListOf<String>()
    val words = text.split(" ")
    var currentLine = StringBuilder()
    for (word in words) {
        val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
        if (paint.measureText(testLine) <= maxWidth) {
            currentLine = StringBuilder(testLine)
        } else {
            if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
            currentLine = StringBuilder(word)
        }
    }
    if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
    return lines
}

private fun abbreviateAgent(name: String): String {
    val words = name.trim().split(Regex("\\s+"))
    val abbr = when {
        words.size >= 3 -> words.take(3).map { it.firstOrNull()?.uppercaseChar() ?: 'X' }.joinToString("")
        words.size == 2 -> {
            val first = words[0].firstOrNull()?.uppercaseChar() ?: 'X'
            val second = words[1]
            val secondFirst = second.firstOrNull()?.uppercaseChar() ?: 'X'
            val secondLast = second.lastOrNull()?.uppercaseChar() ?: 'X'
            "$first$secondFirst$secondLast"
        }
        words.size == 1 -> {
            val w = words[0].filter { it.isLetter() }
            if (w.length >= 3) w.substring(0, 3).uppercase() else w.uppercase().padEnd(3, 'X')
        }
        else -> "XXX"
    }
    return abbr
}
