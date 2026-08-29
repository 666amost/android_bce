package com.bce.courier.ui.component

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bce.courier.data.manager.SessionManager
import com.bce.courier.tracking.CourierTrackingConfig
import com.bce.courier.tracking.CourierTrackingLocationStore
import com.bce.courier.tracking.CourierTrackingService
import com.bce.courier.tracking.NearbyCourierFormatter
import com.bce.courier.tracking.NearbyCourierStore
import kotlinx.coroutines.delay

private data class NearbyCourierRow(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float,
    val speedMetersPerSecond: Float,
    val receivedAtMillis: Long,
)

@Composable
fun NearbyCouriersDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sessionManager = remember(context) { SessionManager(context) }
    val currentCourierId = remember { sessionManager.getUserId().orEmpty() }
    val currentCourierName = remember { sessionManager.getBestCourierName() }
    val state by NearbyCourierStore.state.collectAsStateWithLifecycle()
    val openedAtMillis = remember { System.currentTimeMillis() }
    var nowMillis by remember { mutableLongStateOf(openedAtMillis) }

    DisposableEffect(currentCourierId, currentCourierName) {
        NearbyCourierStore.startSession()
        CourierTrackingService.startNearby(
            context = context,
            courierId = currentCourierId,
            courierName = currentCourierName,
        )
        onDispose {
            CourierTrackingService.stopNearby(context)
            NearbyCourierStore.stopSession()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            NearbyCourierStore.pruneStale(nowMillis)
            delay(1_000L)
        }
    }

    val ownLocation = remember(nowMillis) {
        CourierTrackingLocationStore.getFreshLocation(
            context = context,
            maxAgeMs = CourierTrackingConfig.nearbyCourierTtlMs,
        )
    }
    val rows = remember(state.locations, ownLocation, currentCourierId, nowMillis) {
        if (ownLocation == null) {
            emptyList()
        } else {
            state.locations.values
                .asSequence()
                .filter { it.courierId != currentCourierId }
                .filter {
                    nowMillis - it.receivedAt <= CourierTrackingConfig.nearbyCourierTtlMs
                }
                .map {
                    NearbyCourierRow(
                        id = it.courierId,
                        name = it.courierName,
                        latitude = it.lat,
                        longitude = it.lng,
                        distanceMeters = NearbyCourierFormatter.distanceMeters(
                            fromLat = ownLocation.latitude,
                            fromLng = ownLocation.longitude,
                            toLat = it.lat,
                            toLng = it.lng,
                        ),
                        speedMetersPerSecond = it.speed,
                        receivedAtMillis = it.receivedAt,
                    )
                }
                .sortedBy { it.distanceMeters }
                .take(5)
                .toList()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = true,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Kurir terdekat",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Lokasi live dari kurir yang sedang aktif",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Tutup",
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when {
                    ownLocation == null -> WaitingMessage("Menunggu lokasi Anda...")
                    rows.isNotEmpty() -> rows.forEachIndexed { index, row ->
                        val moving = row.speedMetersPerSecond >=
                            CourierTrackingConfig.minMovingSpeedMps
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        color = if (moving) Color(0xFF22C55E) else Color(0xFF3B82F6),
                                        shape = CircleShape,
                                    ),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                            ) {
                                Text(
                                    text = row.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${NearbyCourierFormatter.motionLabel(row.speedMetersPerSecond)} • " +
                                        NearbyCourierFormatter.formatFreshness(
                                            nowMillis - row.receivedAtMillis,
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = NearbyCourierFormatter.formatDistance(row.distanceMeters),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            FilledTonalIconButton(
                                onClick = {
                                    openCourierNavigation(
                                        context = context,
                                        latitude = row.latitude,
                                        longitude = row.longitude,
                                    )
                                },
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(40.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Directions,
                                    contentDescription = "Navigasi ke ${row.name}",
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                        }
                        if (index < rows.lastIndex) {
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    state.errorMessage != null -> {
                        Text(
                            text = state.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 20.dp),
                        )
                    }
                    nowMillis - openedAtMillis < CourierTrackingConfig.nearbyCourierEmptyWaitMs -> {
                        WaitingMessage("Menunggu lokasi kurir...")
                    }
                    else -> {
                        Text(
                            text = "Belum ada kurir aktif yang terdeteksi",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 20.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun openCourierNavigation(
    context: Context,
    latitude: Double,
    longitude: Double,
) {
    val googleMapsIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("google.navigation:q=$latitude,$longitude&mode=l"),
    ).setPackage("com.google.android.apps.maps")
    val browserFallbackIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=$latitude,$longitude",
        ),
    )

    val opened = runCatching {
        context.startActivity(googleMapsIntent)
        true
    }.getOrDefault(false) || runCatching {
        context.startActivity(browserFallbackIntent)
        true
    }.getOrDefault(false)

    if (!opened) {
        Toast.makeText(
            context.applicationContext,
            "Tidak bisa membuka navigasi",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

@Composable
private fun WaitingMessage(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        Text(
            text = message,
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
