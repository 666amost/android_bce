package com.bce.courier.ui.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bce.courier.R
import com.bce.courier.data.model.ShipmentItem
import com.bce.courier.util.LocationHelper
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPreviewScreen(
    shipment: ShipmentItem,
    locationHelper: LocationHelper,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewHolder = remember { mutableStateOf<MapView?>(null) }

    val hasLocationPermission =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    var courierLatLng by remember { mutableStateOf<GeoPoint?>(null) }
    var destinationLatLng by remember { mutableStateOf<GeoPoint?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(shipment.awb) {
        isLoading = true
        errorMessage = null

        val dest = shipment.address.trim().takeIf { it.isNotBlank() }?.let { receiverAddress ->
            locationHelper.geocodeAddressToLatLngSuspend(receiverAddress)?.let { (lat, lng) ->
                GeoPoint(lat, lng)
            }
        }
        destinationLatLng = dest

        if (hasLocationPermission) {
            val loc = locationHelper.getCurrentLocationSuspend()
            courierLatLng = loc?.let { GeoPoint(it.latitude, it.longitude) }
        } else {
            courierLatLng = null
        }

        if (destinationLatLng == null) {
            errorMessage = "Preview alamat tujuan tidak ditemukan. Navigasi Google Maps tetap dapat digunakan."
        }

        isLoading = false
    }

    val distanceMeters = remember(courierLatLng, destinationLatLng) {
        val courier = courierLatLng ?: return@remember null
        val dest = destinationLatLng ?: return@remember null
        distanceMeters(courier, dest)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = shipment.awb,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (shipment.recipient_name.isNotBlank()) {
                            Text(
                                text = shipment.recipient_name,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val dest = destinationLatLng
                if (dest != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            Configuration.getInstance().userAgentValue = ctx.packageName
                            Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", 0))

                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                isTilesScaledToDpi = true
                                controller.setZoom(16.0)
                                controller.setCenter(dest)
                                mapViewHolder.value = this
                            }
                        },
                        update = { mapView ->
                            mapView.overlays.clear()

                            val courier = courierLatLng
                            if (courier != null) {
                                val courierMarker = Marker(mapView).apply {
                                    position = courier
                                    icon = ContextCompat.getDrawable(context, R.drawable.marker_courier)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    infoWindow = null
                                    setOnMarkerClickListener { _, _ -> true }
                                }
                                mapView.overlays.add(courierMarker)

                                val line = Polyline(mapView).apply {
                                    setPoints(listOf(courier, dest))
                                    outlinePaint.color = android.graphics.Color.parseColor("#2563EB")
                                    outlinePaint.strokeWidth = 7f
                                }
                                mapView.overlays.add(line)
                            }

                            val destMarker = Marker(mapView).apply {
                                position = dest
                                icon = ContextCompat.getDrawable(context, R.drawable.marker_destination)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                infoWindow = null
                                setOnMarkerClickListener { _, _ -> true }
                            }
                            mapView.overlays.add(destMarker)

                            if (courier != null) {
                                val bb = BoundingBox.fromGeoPoints(listOf(courier, dest))
                                mapView.zoomToBoundingBox(bb, true, 140)
                            } else {
                                mapView.controller.setZoom(16.0)
                                mapView.controller.setCenter(dest)
                            }

                            mapView.invalidate()
                        }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = errorMessage ?: "Tidak ada lokasi untuk ditampilkan.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("Memuat lokasi...")
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!hasLocationPermission) {
                        Text(
                            text = "Izin lokasi belum diberikan, jadi rute dari posisi kurir tidak bisa ditampilkan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (shipment.address.isNotBlank()) {
                        Text(
                            text = shipment.address,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (distanceMeters != null) {
                        Text(
                            text = "Perkiraan jarak garis lurus: ${formatDistance(distanceMeters)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val receiverAddress = shipment.address.trim()
                                if (receiverAddress.isBlank()) {
                                    Toast.makeText(context, "Alamat tujuan tidak tersedia", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val directionsUri = Uri.parse(
                                    "https://www.google.com/maps/dir/?api=1" +
                                        "&destination=${Uri.encode(receiverAddress)}" +
                                        "&travelmode=two-wheeler&dir_action=navigate"
                                )
                                val mapIntent = Intent(Intent.ACTION_VIEW, directionsUri)
                                    .setPackage("com.google.android.apps.maps")
                                val fallbackIntent = Intent(Intent.ACTION_VIEW, directionsUri)
                                val opened = runCatching { context.startActivity(mapIntent); true }.getOrDefault(false) ||
                                    runCatching { context.startActivity(fallbackIntent); true }.getOrDefault(false)
                                if (!opened) {
                                    Toast.makeText(context.applicationContext, "Tidak bisa membuka navigasi", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = shipment.address.isNotBlank(),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Default.Directions, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Navigasi")
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewHolder.value?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewHolder.value?.onPause()
                Lifecycle.Event.ON_DESTROY -> mapViewHolder.value?.onDetach()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapViewHolder.value?.onDetach()
        }
    }
}

private fun distanceMeters(from: GeoPoint, to: GeoPoint): Float {
    val results = FloatArray(1)
    Location.distanceBetween(from.latitude, from.longitude, to.latitude, to.longitude, results)
    return results[0]
}

private fun formatDistance(distanceMeters: Float): String {
    if (distanceMeters < 1000) return "${distanceMeters.roundToInt()} m"
    return String.format(java.util.Locale.US, "%.1f km", distanceMeters / 1000f)
}
