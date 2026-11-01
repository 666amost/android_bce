package com.bce.courier.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
fun ScannerOverlay(borderColor: Color = Color.White.copy(alpha = 0.85f), modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val width = min(340.dp.toPx(), size.width - 24.dp.toPx()).coerceAtLeast(0f)
        val height = min(230.dp.toPx(), size.height * 0.68f)
        val left = (size.width - width) / 2
        val top = (size.height - height) / 2
        val right = left + width
        val bottom = top + height
        val outside = Path().apply { addRect(Rect(Offset.Zero, size)) }
        val window = Path().apply {
            addRoundRect(RoundRect(Rect(left, top, right, bottom), CornerRadius(16.dp.toPx())))
        }
        drawPath(Path.combine(PathOperation.Difference, outside, window), Color.Black.copy(alpha = 0.48f))
        val length = 24.dp.toPx()
        val inset = 10.dp.toPx()
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(borderColor, Offset(x, y), Offset(x + dx * length, y), 3.dp.toPx(), StrokeCap.Round)
            drawLine(borderColor, Offset(x, y), Offset(x, y + dy * length), 3.dp.toPx(), StrokeCap.Round)
        }
        corner(left + inset, top + inset, 1f, 1f)
        corner(right - inset, top + inset, -1f, 1f)
        corner(left + inset, bottom - inset, 1f, -1f)
        corner(right - inset, bottom - inset, -1f, -1f)
    }
}
