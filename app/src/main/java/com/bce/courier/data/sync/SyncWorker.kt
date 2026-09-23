package com.bce.courier.data.sync

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bce.courier.data.api.NetworkModule
import com.bce.courier.data.api.SupabaseStorageService
import com.bce.courier.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private suspend fun reverseGeocode(latitude: Double, longitude: Double): String? {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(applicationContext, Locale("id", "ID"))
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                            val address = addresses.firstOrNull()
                            val parts = listOfNotNull(address?.subLocality, address?.subAdminArea, address?.adminArea)
                            cont.resume(parts.joinToString(", ").ifBlank { null })
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    val address = addresses?.firstOrNull()
                    val parts = listOfNotNull(address?.subLocality, address?.subAdminArea, address?.adminArea)
                    parts.joinToString(", ").ifBlank { null }
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(applicationContext)
            val dao = db.pendingUpdateDao()
            dao.resetInterruptedSyncs()

            val pending = dao.getNextPending() ?: return@withContext Result.success()

            dao.setSync(pending.id, true)

            try {
                var photoUrl: String? = null
                val hasPhoto = !pending.photoPath.isNullOrBlank() && File(pending.photoPath).exists()

                if (hasPhoto) {
                    val file = File(pending.photoPath!!)

                    photoUrl = SupabaseStorageService.uploadImage(
                        applicationContext,
                        file,
                        pending.awb,
                        true,
                        pending.courierName,
                        pending.latitude?.let { "${it},${pending.longitude}" }
                    )

                    if (photoUrl == null) {
                        val error = "Upload foto gagal"
                        val nextRetryCount = pending.retryCount + 1
                        return@withContext if (nextRetryCount < MAX_RETRIES) {
                            dao.incrementRetry(pending.id, error)
                            Result.retry()
                        } else {
                            dao.markFailed(pending.id, error)
                            Result.failure()
                        }
                    }
                }

                var finalLocationAddress = pending.locationAddress
                if (finalLocationAddress.isNullOrBlank() && pending.latitude != null && pending.longitude != null) {
                    finalLocationAddress = reverseGeocode(pending.latitude, pending.longitude)
                }

                val result = NetworkModule.shipmentRepository.updateShipmentDirectWithUrl(
                    awb = pending.awb,
                    status = pending.status,
                    notes = pending.notes,
                    pendingReasonCode = pending.pendingReasonCode,
                    pendingReasonNote = pending.pendingReasonNote,
                    lat = pending.latitude,
                    lng = pending.longitude,
                    photoUrl = photoUrl,
                    courierName = pending.courierName,
                    courierId = pending.courierId,
                    armada = pending.armada,
                    platArmada = pending.platArmada,
                    locationAddress = finalLocationAddress
                )

                if (result.isSuccess) {
                    dao.delete(pending.id)
                    pending.photoPath?.let { runCatching { File(it).delete() } }

                    sendRefreshBroadcast()

                    if (dao.getNextPending() != null) {
                        enqueueSyncWork(applicationContext)
                    }
                    Result.success()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Sinkronisasi gagal"
                    val nextRetryCount = pending.retryCount + 1
                    if (nextRetryCount < MAX_RETRIES) {
                        dao.incrementRetry(pending.id, error)
                        Result.retry()
                    } else {
                        dao.markFailed(pending.id, error)
                        Result.failure()
                    }
                }
            } catch (error: Exception) {
                val nextRetryCount = pending.retryCount + 1
                if (nextRetryCount < MAX_RETRIES) {
                    dao.incrementRetry(pending.id, error.message)
                    Result.retry()
                } else {
                    dao.markFailed(pending.id, error.message)
                    Result.failure()
                }
            }
        } catch (_: Exception) {
            Result.failure()
        }
    }

    private fun sendRefreshBroadcast() {
        val intent = Intent("com.bce.courier.SYNC_COMPLETED").apply {
            setPackage(applicationContext.packageName)
        }
        applicationContext.sendBroadcast(intent)
    }

    companion object {
        private const val WORK_NAME = "shipment_sync"
        private const val MAX_RETRIES = 5

        fun enqueueSyncWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    syncRequest
                )
        }
    }
}
