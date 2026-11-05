package com.bce.courier.data.sync

import com.bce.courier.data.local.PendingUpdateEntity

/** Checkpoints survive retries and process restarts; local evidence is removed only by the caller after success. */
class DeliverySyncProcessor(
    private val uploadPhoto: suspend (PendingUpdateEntity) -> String,
    private val savePhotoUrl: suspend (Long, String) -> Unit,
    private val commitDelivery: suspend (PendingUpdateEntity, String) -> Unit,
    private val markDeliverySynced: suspend (Long) -> Unit,
    private val syncBranch: suspend (PendingUpdateEntity, String) -> Unit,
) {
    suspend fun sync(pending: PendingUpdateEntity) {
        val photoUrl = pending.photoUrl?.takeIf { it.isNotBlank() } ?: run {
            require(!pending.photoPath.isNullOrBlank()) { "Foto bukti tidak ditemukan; ambil foto kembali" }
            uploadPhoto(pending).also { url ->
                require(url.isNotBlank()) { "Foto bukti belum berhasil diunggah" }
                savePhotoUrl(pending.id, url)
            }
        }
        if (!pending.deliverySynced) {
            commitDelivery(pending, photoUrl)
            markDeliverySynced(pending.id)
        }
        syncBranch(pending, photoUrl)
    }
}
