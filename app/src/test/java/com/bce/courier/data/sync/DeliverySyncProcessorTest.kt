package com.bce.courier.data.sync

import com.bce.courier.data.local.PendingUpdateEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DeliverySyncProcessorTest {
    private fun pending() = PendingUpdateEntity(
        id = 1, awb = "BE001", status = "delivered", notes = null,
        pendingReasonCode = null, pendingReasonNote = null, latitude = null, longitude = null,
        photoPath = "/private/photo.jpg", courierName = "Kurir", courierId = "courier-a",
        armada = "motor", platArmada = "BCE", locationAddress = null,
    )

    @Test
    fun branchRetryReusesUploadedPhotoAndCommittedStatus() = runBlocking {
        var stored = pending()
        var uploads = 0
        var commits = 0
        var branchAttempts = 0
        val processor = DeliverySyncProcessor(
            uploadPhoto = { uploads++; "https://storage.invalid/proof.jpg" },
            savePhotoUrl = { _, url -> stored = stored.copy(photoUrl = url) },
            commitDelivery = { _, _ -> commits++ },
            markDeliverySynced = { stored = stored.copy(deliverySynced = true) },
            syncBranch = { _, _ -> if (++branchAttempts == 1) throw IOException("Cabang offline") },
        )
        try {
            processor.sync(stored)
            fail("Must retain the job when the branch is offline")
        } catch (_: IOException) { }
        assertTrue(stored.deliverySynced)
        assertNotNull(stored.photoUrl)
        processor.sync(stored)
        assertEquals(1, uploads)
        assertEquals(1, commits)
        assertEquals(2, branchAttempts)
    }

    @Test
    fun statusRetryKeepsPhotoCheckpointAndDoesNotSendBranchEarly() = runBlocking {
        var stored = pending()
        var uploads = 0
        var commits = 0
        var branches = 0
        val processor = DeliverySyncProcessor(
            uploadPhoto = { uploads++; "https://storage.invalid/proof.jpg" },
            savePhotoUrl = { _, url -> stored = stored.copy(photoUrl = url) },
            commitDelivery = { _, _ -> if (++commits == 1) throw IOException("API offline") },
            markDeliverySynced = { stored = stored.copy(deliverySynced = true) },
            syncBranch = { _, _ -> branches++ },
        )
        try { processor.sync(stored); fail("Must report the failed commit") } catch (_: IOException) { }
        assertFalse(stored.deliverySynced)
        assertEquals(0, branches)
        processor.sync(stored)
        assertEquals(1, uploads)
        assertEquals(2, commits)
        assertEquals(1, branches)
    }

    @Test
    fun missingEvidenceCannotBeCommittedAsDelivered() = runBlocking {
        val processor = DeliverySyncProcessor(
            uploadPhoto = { error("No file to upload") },
            savePhotoUrl = { _, _ -> error("Must not checkpoint missing evidence") },
            commitDelivery = { _, _ -> error("Must not send delivered without evidence") },
            markDeliverySynced = { error("Must not mark missing evidence synced") },
            syncBranch = { _, _ -> error("Must not sync branch without evidence") },
        )
        try {
            processor.sync(pending().copy(photoPath = null))
            fail("Expected missing-photo validation")
        } catch (_: IllegalArgumentException) { }
    }
}
