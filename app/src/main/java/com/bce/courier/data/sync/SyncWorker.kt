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
import com.bce.courier.data.manager.SessionManager
import com.bce.courier.data.repository.ShipmentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
                            if (cont.isActive) cont.resume(parts.joinToString(", ").ifBlank { null })
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    val address = addresses?.firstOrNull()
                    val parts = listOfNotNull(address?.subLocality, address?.subAdminArea, address?.adminArea)
                    parts.joinToString(", ").ifBlank { null }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        NetworkModule.init(applicationContext)
        val session = SessionManager(applicationContext)
        val courierId = session.getUserId() ?: return@withContext Result.success()
        val dao = AppDatabase.getInstance(applicationContext).pendingUpdateDao()
        dao.resetInterruptedSyncs()
        fun checkSession() {
            if (session.getUserId() != courierId) throw SessionChangedException()
        }
        val processor = DeliverySyncProcessor(
            uploadPhoto = { pending ->
                checkSession()
                val file = File(requireNotNull(pending.photoPath))
                require(file.isFile && file.length() > 0) { "Foto bukti tidak ditemukan; ambil foto kembali" }
                SupabaseStorageService.uploadImage(
                    applicationContext, file, pending.awb, pending.isCameraPhoto,
                    pending.courierName, pending.latitude?.let { "${it},${pending.longitude}" },
                ) ?: throw java.io.IOException("Upload foto belum berhasil")
            },
            savePhotoUrl = dao::savePhotoUrl,
            commitDelivery = { pending, photoUrl ->
                checkSession()
                val address = pending.locationAddress ?: if (pending.latitude != null && pending.longitude != null) {
                    withTimeoutOrNull(2_000) { reverseGeocode(pending.latitude, pending.longitude) }
                } else null
                NetworkModule.shipmentRepository.updateShipmentDirectWithUrl(
                    awb = pending.awb, status = pending.status, notes = pending.notes,
                    pendingReasonCode = pending.pendingReasonCode, pendingReasonNote = pending.pendingReasonNote,
                    lat = pending.latitude, lng = pending.longitude, photoUrl = photoUrl,
                    courierName = pending.courierName, courierId = courierId, locationAddress = address,
                ).getOrThrow()
            },
            markDeliverySynced = dao::markDeliverySynced,
            syncBranch = { pending, photoUrl ->
                checkSession()
                NetworkModule.shipmentRepository.syncDeliveredToBranch(
                    pending.awb, pending.status, photoUrl, pending.courierName,
                    pending.armada, pending.platArmada,
                ).getOrThrow()
            },
        )

        while (true) {
            if (session.getUserId() != courierId) return@withContext Result.success()
            val pending = dao.getNextPending(courierId) ?: return@withContext Result.success()
            if (session.needsReauthentication()) return@withContext Result.retry()
            dao.setSync(pending.id, true)
            try {
                processor.sync(pending)
                dao.delete(pending.id)
                pending.photoPath?.let { runCatching { File(it).delete() } }
                sendRefreshBroadcast()
            } catch (error: SessionChangedException) {
                dao.setSync(pending.id, false)
                return@withContext Result.success()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val permanent = error is IllegalArgumentException ||
                    (error is ShipmentRepository.HttpStatusException && !error.retryable)
                if (permanent || pending.retryCount + 1 >= MAX_RETRIES) {
                    // Keep failed data and evidence visible; continue with other parcels.
                    dao.markFailed(pending.id, error.message ?: "Sinkronisasi gagal")
                    sendRefreshBroadcast()
                } else {
                    dao.incrementRetry(pending.id, error.message)
                    return@withContext Result.retry()
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        Result.success()
    }

    private fun sendRefreshBroadcast() {
        val intent = Intent("com.bce.courier.SYNC_COMPLETED").apply {
            setPackage(applicationContext.packageName)
        }
        applicationContext.sendBroadcast(intent)
    }

    private class SessionChangedException : Exception()

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
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    syncRequest
                )
        }
    }
}
