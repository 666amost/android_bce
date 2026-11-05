package com.bce.courier.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingUpdateDao {
    @Insert
    suspend fun insert(update: PendingUpdateEntity): Long

    @Transaction
    suspend fun enqueue(update: PendingUpdateEntity): Long {
        supersedeFailed(update.courierId, update.awb)
        return insert(update)
    }

    @Query("UPDATE pending_updates SET syncState = 'superseded' WHERE courierId = :courierId AND awb = :awb AND syncState = 'failed'")
    suspend fun supersedeFailed(courierId: String, awb: String)

    @Update
    suspend fun update(update: PendingUpdateEntity)

    @Query("SELECT * FROM pending_updates WHERE courierId = :courierId AND isSyncing = 0 AND syncState = 'queued' ORDER BY id ASC LIMIT 1")
    suspend fun getNextPending(courierId: String): PendingUpdateEntity?

    @Query("SELECT * FROM pending_updates WHERE courierId = :courierId AND syncState != 'superseded' ORDER BY id ASC")
    suspend fun getForCourier(courierId: String): List<PendingUpdateEntity>

    @Query("SELECT * FROM pending_updates WHERE syncState != 'superseded' ORDER BY id ASC")
    fun observePendingUpdates(): Flow<List<PendingUpdateEntity>>

    @Query("SELECT COUNT(*) FROM pending_updates WHERE syncState != 'superseded'")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM pending_updates WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_updates SET isSyncing = :syncing, syncState = CASE WHEN :syncing = 1 THEN 'syncing' ELSE 'queued' END WHERE id = :id")
    suspend fun setSync(id: Long, syncing: Boolean)

    @Query("UPDATE pending_updates SET retryCount = retryCount + 1, isSyncing = 0, syncState = 'queued', lastError = :error WHERE id = :id")
    suspend fun incrementRetry(id: Long, error: String?)

    @Query("UPDATE pending_updates SET isSyncing = 0, syncState = CASE WHEN EXISTS (SELECT 1 FROM pending_updates newer WHERE newer.courierId = pending_updates.courierId AND newer.awb = pending_updates.awb AND newer.id > :id AND newer.syncState != 'superseded') THEN 'superseded' ELSE 'failed' END, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String?)

    @Query("UPDATE pending_updates SET photoUrl = :url WHERE id = :id")
    suspend fun savePhotoUrl(id: Long, url: String)

    @Query("UPDATE pending_updates SET deliverySynced = 1 WHERE id = :id")
    suspend fun markDeliverySynced(id: Long)

    @Query("UPDATE pending_updates SET retryCount = 0, isSyncing = 0, syncState = 'queued', lastError = NULL WHERE courierId = :courierId AND syncState = 'failed'")
    suspend fun retryFailed(courierId: String)

    @Query("UPDATE pending_updates SET isSyncing = 0, syncState = 'queued' WHERE isSyncing = 1")
    suspend fun resetInterruptedSyncs()
}

