package com.bce.courier.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.bce.courier.BuildConfig
import com.bce.courier.data.model.VersioningConfig
import java.io.File
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.app.PendingIntent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class UpdateInfo(
    val isAvailable: Boolean,
    val latestVersionCode: Int,
    val latestVersionName: String,
    val apkUrl: String,
    val changelog: String
)

data class UpdateDownloadProgress(
    val percent: Int?,
    val bytesDownloaded: Long,
    val totalBytes: Long
)

sealed class UpdateDownloadResult {
    data class Success(val apkUri: Uri) : UpdateDownloadResult()
    data class Failed(val reason: String? = null) : UpdateDownloadResult()
}

sealed class UpdateInstallResult {
    data object Started : UpdateInstallResult()
    data class NeedsPermission(val settingsIntent: Intent) : UpdateInstallResult()
    data class Failed(val reason: String? = null) : UpdateInstallResult()
}

class UpdateHelper(private val context: Context) {

    companion object {
        private const val TAG = "UpdateHelper"
        private const val APK_FILE_NAME = "BCECourier-update.apk"
        private const val CHANNEL_ID = "bce_update_channel"
        private const val CHANNEL_NAME = "App Update"
        private const val NOTIF_ID = 4201
    }

    private val appContext = context.applicationContext

    private var downloadId: Long = -1
    private var progressJob: Job? = null
    private var downloadReceiver: BroadcastReceiver? = null

    fun checkForUpdate(versioning: VersioningConfig): UpdateInfo? {
        val latestCode = versioning.latest_version_code ?: return null
        val latestName = versioning.latest_version_name ?: return null
        val apkUrl = versioning.apk_url ?: return null

        val currentCode = BuildConfig.VERSION_CODE

        if (latestCode > currentCode) {
            return UpdateInfo(
                isAvailable = true,
                latestVersionCode = latestCode,
                latestVersionName = latestName,
                apkUrl = apkUrl,
                changelog = versioning.changelog ?: ""
            )
        }
        return null
    }

    fun dispose() {
        progressJob?.cancel()
        progressJob = null
        downloadReceiver?.let { receiver ->
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
        downloadReceiver = null
    }

    fun downloadApk(
        scope: CoroutineScope,
        apkUrl: String,
        onProgress: (UpdateDownloadProgress) -> Unit,
        onComplete: (UpdateDownloadResult) -> Unit
    ) {
        dispose()

        val oldFile = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)
        if (oldFile.exists()) oldFile.delete()

        val request = DownloadManager.Request(apkUrl.toUri())
            .setTitle("BCE Courier Update")
            .setDescription("Mengunduh versi terbaru...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setMimeType("application/vnd.android.package-archive")

        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)

        val finished = AtomicBoolean(false)

        fun finishWithStatus(status: Int, reason: Int?) {
            if (!finished.compareAndSet(false, true)) return

            progressJob?.cancel()
            progressJob = null

            downloadReceiver?.let { receiver ->
                try {
                    appContext.unregisterReceiver(receiver)
                } catch (_: Exception) {
                }
            }
            downloadReceiver = null

            scope.launch(Dispatchers.Main.immediate) {
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val apkUri = buildDownloadedApkUri()
                    if (apkUri == null) {
                        onComplete(UpdateDownloadResult.Failed("APK file not found"))
                        return@launch
                    }
                    onProgress(UpdateDownloadProgress(100, 0, 0))
                    showInstallNotification(buildInstallIntent(apkUri), needsUnknownSourcesPermission())
                    onComplete(UpdateDownloadResult.Success(apkUri))
                } else {
                    val message = when (reason) {
                        DownloadManager.ERROR_CANNOT_RESUME -> "Tidak bisa melanjutkan download"
                        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage tidak ditemukan"
                        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File sudah ada"
                        DownloadManager.ERROR_FILE_ERROR -> "Gagal menyimpan file"
                        DownloadManager.ERROR_HTTP_DATA_ERROR -> "Koneksi bermasalah"
                        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Storage tidak cukup"
                        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Terlalu banyak redirect"
                        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Server error"
                        DownloadManager.ERROR_UNKNOWN -> "Error tidak diketahui"
                        else -> "Download gagal"
                    }
                    onComplete(UpdateDownloadResult.Failed(message))
                }
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return

                val (status, reason) = queryStatus(downloadManager, downloadId)
                finishWithStatus(status, reason)
            }
        }
        downloadReceiver = receiver

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(receiver, filter)
        }

        progressJob = scope.launch(Dispatchers.IO) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            while (isActive && !finished.get()) {
                val cursor = downloadManager.query(query)
                try {
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val bytesDownloaded = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        )
                        val totalBytes = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        )
                        val percent = if (totalBytes > 0) {
                            ((bytesDownloaded * 100f) / totalBytes).toInt().coerceIn(0, 100)
                        } else null

                        withContext(Dispatchers.Main.immediate) {
                            onProgress(UpdateDownloadProgress(percent, bytesDownloaded, totalBytes))
                        }

                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            finishWithStatus(status, reason)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to query download progress", e)
                } finally {
                    cursor.close()
                }
                delay(450)
            }
        }
    }

    @Deprecated("Use downloadApk(...) + installDownloadedApk() for in-app progress & install button.")
    fun downloadAndInstall(apkUrl: String, onComplete: (Boolean) -> Unit) {
        val scope = MainScope()
        downloadApk(
            scope = scope,
            apkUrl = apkUrl,
            onProgress = {},
            onComplete = { result ->
                scope.cancel()
                when (result) {
                    is UpdateDownloadResult.Success -> {
                        handleInstallFlow()
                        onComplete(true)
                    }
                    is UpdateDownloadResult.Failed -> onComplete(false)
                }
            }
        )
    }

    fun installDownloadedApk(): UpdateInstallResult {
        val apkUri = buildDownloadedApkUri() ?: return UpdateInstallResult.Failed("APK file not found")
        val pm = appContext.packageManager
        val needsUnknownApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            !pm.canRequestPackageInstalls()
        } else false

        if (needsUnknownApp) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return UpdateInstallResult.NeedsPermission(settingsIntent)
        }

        return try {
            appContext.startActivity(buildInstallIntent(apkUri))
            UpdateInstallResult.Started
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start install intent", e)
            UpdateInstallResult.Failed(e.message)
        }
    }

    fun needsUnknownSourcesPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return !appContext.packageManager.canRequestPackageInstalls()
    }

    private fun queryStatus(dm: DownloadManager, id: Long): Pair<Int, Int?> {
        val query = DownloadManager.Query().setFilterById(id)
        val cursor = dm.query(query)
        return try {
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                status to reason
            } else {
                DownloadManager.STATUS_FAILED to null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query download status", e)
            DownloadManager.STATUS_FAILED to null
        } finally {
            cursor.close()
        }
    }

    private fun buildDownloadedApkUri(): Uri? {
        val apkFile = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)
        if (!apkFile.exists()) return null
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.provider",
            apkFile
        )
    }

    private fun buildInstallIntent(uri: Uri): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }

    private fun handleInstallFlow() {
        val apkFile = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file not found")
            return
        }

        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.provider",
            apkFile
        )

        val intent = buildInstallIntent(uri)

        val pm = appContext.packageManager
        val needsUnknownApp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            !pm.canRequestPackageInstalls()
        } else false

        showInstallNotification(intent, needsUnknownApp)

        if (!needsUnknownApp) {
            try {
                appContext.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start install intent", e)
            }
        }
    }

    private fun showInstallNotification(installIntent: Intent, needsUnknownApp: Boolean) {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }

        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE

        val installPi = PendingIntent.getActivity(appContext, 1001, installIntent, flags)

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(com.bce.courier.R.mipmap.ic_launcher)
            .setContentTitle("BCE Courier")
            .setContentText("Download selesai. Ketuk untuk menginstal.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Install", installPi)

        if (needsUnknownApp) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}")
            )
            val settingsPi = PendingIntent.getActivity(appContext, 1002, settingsIntent, flags)
            builder.addAction(0, "Allow install", settingsPi)
        }

        nm.notify(NOTIF_ID, builder.build())
    }
}
