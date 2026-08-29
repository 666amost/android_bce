package com.bce.courier.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bce.courier.MainActivity
import com.bce.courier.R
import com.bce.courier.data.manager.SessionManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.gojek.courier.callback.SendMessageCallback
import com.gojek.mqtt.client.model.ConnectionState
import kotlin.math.abs

class CourierTrackingService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var trackingClient: CourierTrackingClient? = null
    private var courierId: String? = null
    private var courierName: String = ""
    private var lastPublishedLocation: Location? = null
    private var lastPublishedAt: Long = 0L
    private var lastConnectAttemptAt: Long = 0L
    private var locationUpdatesStarted = false
    private val adaptiveTracking = AdaptiveTrackingStateMachine()
    private var locationSamplingMode = LocationSamplingMode.MOVING
    private val connectionMonitorHandler = Handler(Looper.getMainLooper())
    private var connectionMonitorStarted = false

    private val connectionMonitorRunnable = object : Runnable {
        override fun run() {
            if (!connectionMonitorStarted) return
            monitorTrackingHealth()
            connectionMonitorHandler.postDelayed(this, CourierTrackingConfig.connectionMonitorIntervalMs)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation ?: return
            handleLocation(location)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        trackingClient = CourierTrackingClient(applicationContext)
        ensureNotificationChannel()
        startConnectionMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            CourierTrackingConfig.notificationId,
            buildNotification("Tracking aktif • menyiapkan koneksi")
        )

        if (intent?.action == ACTION_STOP_NEARBY) {
            val trackingWasActive = !courierId.isNullOrBlank()
            trackingClient?.stopNearbySubscription()
            NearbyCourierStore.stopSession()
            if (!trackingWasActive) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            return START_STICKY
        }

        val nextCourierId = intent?.getStringExtra(EXTRA_COURIER_ID)
            ?.trim()
            .orEmpty()
            .ifBlank { courierId.orEmpty() }
        if (!CourierTrackingConfig.isEnabled || nextCourierId.isBlank() || !hasLocationPermission()) {
            broadcastStatus(CourierTrackingStatus.OFF)
            stopTracking()
            stopSelf()
            return START_NOT_STICKY
        }

        courierId = nextCourierId
        courierName = intent?.getStringExtra(EXTRA_COURIER_NAME)
            ?.trim()
            .orEmpty()
            .ifBlank { SessionManager(applicationContext).getBestCourierName() }
        startConnectionMonitor()
        broadcastStatus(CourierTrackingStatus.STARTING)
        updateNotification()
        trackingClient?.reconnectIfNeeded(nextCourierId)
        requestWarmLocation()
        startLocationUpdates()
        if (intent?.action == ACTION_START_NEARBY) {
            NearbyCourierStore.startSession()
            trackingClient?.startNearbySubscription(nextCourierId)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopConnectionMonitor()
        stopTracking()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    private fun requestWarmLocation() {
        if (!hasLocationPermission()) return
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                val age = location?.let { System.currentTimeMillis() - it.time } ?: Long.MAX_VALUE
                if (location != null && age <= CourierTrackingConfig.warmLocationMaxAgeMs) {
                    handleLocation(location)
                }
            }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(forceRestart: Boolean = false) {
        if (!hasLocationPermission()) return
        if (locationUpdatesStarted && !forceRestart) return
        if (locationUpdatesStarted) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            locationUpdatesStarted = false
        }

        val intervalMs = when (locationSamplingMode) {
            LocationSamplingMode.MOVING -> CourierTrackingConfig.updateIntervalMs
            LocationSamplingMode.STATIONARY -> CourierTrackingConfig.stationaryUpdateIntervalMs
        }
        val maxDelayMs = when (locationSamplingMode) {
            LocationSamplingMode.MOVING -> CourierTrackingConfig.maxUpdateDelayMs
            LocationSamplingMode.STATIONARY -> CourierTrackingConfig.stationaryMaxUpdateDelayMs
        }
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            intervalMs
        )
            .setMinUpdateIntervalMillis(
                if (locationSamplingMode == LocationSamplingMode.MOVING) {
                    CourierTrackingConfig.minUpdateIntervalMs
                } else {
                    CourierTrackingConfig.stationaryUpdateIntervalMs
                }
            )
            .setMaxUpdateDelayMillis(maxDelayMs)
            .setMinUpdateDistanceMeters(CourierTrackingConfig.minUpdateDistanceMeters)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            locationUpdatesStarted = true
        } catch (error: SecurityException) {
            Log.e(TAG, "Location permission revoked while starting tracking", error)
            stopSelf()
        }
    }

    private fun stopTracking() {
        if (locationUpdatesStarted) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            locationUpdatesStarted = false
        }
        trackingClient?.disconnect()
        NearbyCourierStore.stopSession()
        adaptiveTracking.reset()
        locationSamplingMode = LocationSamplingMode.MOVING
        broadcastStatus(CourierTrackingStatus.OFF)
    }

    private fun startConnectionMonitor() {
        if (connectionMonitorStarted) return
        connectionMonitorStarted = true
        connectionMonitorHandler.removeCallbacks(connectionMonitorRunnable)
        connectionMonitorHandler.post(connectionMonitorRunnable)
    }

    private fun stopConnectionMonitor() {
        connectionMonitorStarted = false
        connectionMonitorHandler.removeCallbacks(connectionMonitorRunnable)
    }

    private fun monitorTrackingHealth() {
        val activeCourierId = courierId
        if (!CourierTrackingConfig.isEnabled || activeCourierId.isNullOrBlank() || !hasLocationPermission()) {
            updateNotification()
            return
        }

        if (!locationUpdatesStarted) {
            startLocationUpdates()
        }

        val state = trackingClient?.currentState()
        if (state != ConnectionState.CONNECTED && state != ConnectionState.CONNECTING) {
            trackingClient?.reconnectIfNeeded(activeCourierId)
        }

        val now = System.currentTimeMillis()
        if (lastPublishedAt == 0L || now - lastPublishedAt >= CourierTrackingConfig.connectionMonitorWarmLocationIntervalMs) {
            requestWarmLocation()
        }

        updateNotification()
    }

    private fun handleLocation(location: Location) {
        val activeCourierId = courierId ?: return
        val accuracy = location.accuracy
        if (accuracy > CourierTrackingConfig.maxAcceptedAccuracyMeters) return
        courierName = SessionManager(applicationContext).getBestCourierName(courierName.ifBlank { "Kurir" })

        val nextSamplingMode = adaptiveTracking.onSample(
            lat = location.latitude,
            lng = location.longitude,
            speedMetersPerSecond = location.speed,
            now = System.currentTimeMillis()
        )
        if (nextSamplingMode != locationSamplingMode) {
            locationSamplingMode = nextSamplingMode
            startLocationUpdates(forceRestart = true)
        }

        CourierTrackingLocationStore.save(applicationContext, location)
        if (!shouldPublish(location)) return

        val now = System.currentTimeMillis()
        if (now - lastConnectAttemptAt >= CourierTrackingConfig.minPublishIntervalMs) {
            trackingClient?.reconnectIfNeeded(activeCourierId)
            lastConnectAttemptAt = now
        }

        val payload = CourierLocationMessage(
            lat = location.latitude,
            lng = location.longitude,
            bearing = location.bearing,
            speed = location.speed,
            accuracy = accuracy,
            timestamp = now,
            courierId = activeCourierId,
            courierName = courierName
        )

        val published = trackingClient?.publish(
            courierId = activeCourierId,
            location = payload,
            callback = object : SendMessageCallback {
                override fun onMessageSendTrigger() = Unit

                override fun onMessageWrittenOnSocket() = Unit

                override fun onMessageSendSuccess() {
                    lastPublishedLocation = Location(location)
                    lastPublishedAt = now
                    updateNotification()
                }

                override fun onMessageSendFailure(error: Throwable) {
                    Log.w(TAG, "MQTT publish failed: ${error.message}", error)
                    updateNotification()
                }
            }
        ) ?: false

        if (!published) {
            updateNotification()
        }
    }

    private fun shouldPublish(location: Location): Boolean {
        val previousLocation = lastPublishedLocation ?: return true
        val elapsed = System.currentTimeMillis() - lastPublishedAt
        if (elapsed < CourierTrackingConfig.minPublishIntervalMs) return false

        val distance = previousLocation.distanceTo(location)
        val bearingDelta = abs(previousLocation.bearing - location.bearing)
        val speedDelta = abs(previousLocation.speed - location.speed)
        val moving = distance >= CourierTrackingConfig.minMovementMeters ||
            location.speed >= CourierTrackingConfig.minMovingSpeedMps ||
            previousLocation.speed >= CourierTrackingConfig.minMovingSpeedMps

        return if (moving) {
            distance >= CourierTrackingConfig.minMovementMeters ||
                bearingDelta >= 25f ||
                speedDelta >= 1.5f ||
                elapsed >= CourierTrackingConfig.forceMovingPublishMs
        } else {
            elapsed >= CourierTrackingConfig.idleHeartbeatMs
        }
    }

    @SuppressLint("MissingPermission", "NotificationPermission")
    private fun updateNotification() {
        val trackingStatus = currentTrackingStatus()
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return
        manager.notify(
            CourierTrackingConfig.notificationId,
            buildNotification(
                when (trackingStatus) {
                    CourierTrackingStatus.ACTIVE -> "Tracking aktif • MQTT tersambung"
                    CourierTrackingStatus.STARTING -> "Tracking aktif • menghubungkan MQTT"
                    CourierTrackingStatus.WAITING -> "Tracking aktif • menunggu lokasi / jaringan"
                    CourierTrackingStatus.OFF -> "Tracking nonaktif"
                }
            )
        )
        broadcastStatus(trackingStatus)
    }

    private fun currentTrackingStatus(): CourierTrackingStatus {
        return when (trackingClient?.currentState()) {
            ConnectionState.CONNECTED -> CourierTrackingStatus.ACTIVE
            ConnectionState.CONNECTING -> CourierTrackingStatus.STARTING
            ConnectionState.INITIALISED,
            ConnectionState.DISCONNECTED,
            ConnectionState.DISCONNECTING,
            null -> CourierTrackingStatus.WAITING
        }
    }

    private fun broadcastStatus(status: CourierTrackingStatus) {
        sendBroadcast(
            Intent(ACTION_TRACKING_STATUS).apply {
                setPackage(packageName)
                putExtra(EXTRA_TRACKING_STATUS, status.name)
            }
        )
    }

    private fun buildNotification(contentText: String) = NotificationCompat.Builder(
        this,
        CourierTrackingConfig.notificationChannelId
    )
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("BCE Courier Live Tracking")
        .setContentText(contentText)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(createContentIntent())
        .build()

    private fun createContentIntent(): PendingIntent {
        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            pendingIntentFlags
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CourierTrackingConfig.notificationChannelId,
            CourierTrackingConfig.notificationChannelName,
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "CourierTrackingService"
        private const val ACTION_START = "com.bce.courier.tracking.START"
        private const val ACTION_START_NEARBY = "com.bce.courier.tracking.START_NEARBY"
        private const val ACTION_STOP_NEARBY = "com.bce.courier.tracking.STOP_NEARBY"
        private const val EXTRA_COURIER_ID = "extra_courier_id"
        private const val EXTRA_COURIER_NAME = "extra_courier_name"
        const val ACTION_TRACKING_STATUS = "com.bce.courier.tracking.STATUS"
        const val EXTRA_TRACKING_STATUS = "extra_tracking_status"

        fun start(context: Context, courierId: String, courierName: String = "") {
            val intent = Intent(context, CourierTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_COURIER_ID, courierId)
                putExtra(EXTRA_COURIER_NAME, courierName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startNearby(context: Context, courierId: String, courierName: String) {
            val intent = Intent(context, CourierTrackingService::class.java).apply {
                action = ACTION_START_NEARBY
                putExtra(EXTRA_COURIER_ID, courierId)
                putExtra(EXTRA_COURIER_NAME, courierName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopNearby(context: Context) {
            context.startService(
                Intent(context, CourierTrackingService::class.java).apply {
                    action = ACTION_STOP_NEARBY
                }
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CourierTrackingService::class.java))
        }
    }
}



