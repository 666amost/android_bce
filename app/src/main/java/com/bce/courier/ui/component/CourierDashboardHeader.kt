package com.bce.courier.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bce.courier.tracking.CourierTrackingStatus

@Composable
fun CourierDashboardHeader(
    userName: String,
    isOnline: Boolean,
    trackingStatus: CourierTrackingStatus,
    pendingSyncCount: Int,
    pointsScore: Int?,
    updateAvailable: Boolean,
    isRefreshing: Boolean,
    onProfileClick: () -> Unit,
    onRefresh: () -> Unit,
) {
    val contentColor = MaterialTheme.colorScheme.onPrimary
    val statusLabel = when {
        !isOnline -> "Offline"
        trackingStatus == CourierTrackingStatus.ACTIVE -> "Online Track"
        trackingStatus == CourierTrackingStatus.STARTING -> "Online Track..."
        trackingStatus == CourierTrackingStatus.WAITING -> "Online Track wait"
        else -> "Online"
    }
    val statusColor = when {
        !isOnline -> Color(0xFFFFC7C2)
        trackingStatus == CourierTrackingStatus.ACTIVE -> Color(0xFFB5F2CD)
        else -> contentColor.copy(alpha = 0.8f)
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = userName,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = contentColor)
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Perbarui pengiriman",
                        modifier = Modifier.size(22.dp),
                        tint = contentColor.copy(alpha = 0.9f),
                    )
                }
            }
            IconButton(
                onClick = onProfileClick,
                modifier = Modifier.semantics {
                    contentDescription = if (updateAvailable) "Profil & pengaturan, pembaruan tersedia" else "Profil & pengaturan"
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CourierMotionSource(CourierMotionOrigin.Profile, Modifier.size(36.dp)) { sharedModifier ->
                        CourierAvatar(userName, size = 36.dp, modifier = sharedModifier)
                    }
                    // The profile owns the version entry; the dot only signals an update.
                    if (updateAvailable) {
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd).size(8.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(6.dp).background(statusColor, CircleShape))
            Text(
                text = statusLabel,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = contentColor.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (pointsScore != null) {
                Text(
                    text = "Point ${pointsScore.coerceIn(1, 10)}",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.9f),
                )
            }
            if (pendingSyncCount > 0) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = contentColor.copy(alpha = 0.9f),
                )
                Text(
                    text = "$pendingSyncCount belum terkirim",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = contentColor.copy(alpha = 0.85f),
                )
            }
        }
    }
}
