package com.bce.courier.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingUpdateDao {
    @Insert
    suspend fun insert(update: PendingUpdateEntity): Long

    @Update
    suspend fun update(update: PendingUpdateEntity)

    @Query("SELECT * FROM pending_updates WHERE isSyncing = 0 AND syncState = 'queued' ORDER BY priority ASC, timestamp ASC LIMIT 1")
    suspend fun getNextPending(): PendingUpdateEntity?

    @Query("SELECT * FROM pending_updates ORDER BY priority ASC, timestamp ASC")
    fun observePendingUpdates(): Flow<List<PendingUpdateEntity>>

    @Query("SELECT COUNT(*) FROM pending_updates")
    fun observePendingCount(): Flow<Int>

    @Query("DELETE FROM pending_updates WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_updates SET isSyncing = :syncing, syncState = CASE WHEN :syncing = 1 THEN 'syncing' ELSE 'queued' END WHERE id = :id")
    suspend fun setSync(id: Long, syncing: Boolean)

    @Query("UPDATE pending_updates SET retryCount = retryCount + 1, isSyncing = 0, syncState = 'queued', lastError = :error WHERE id = :id")
    suspend fun incrementRetry(id: Long, error: String?)

    @Query("UPDATE pending_updates SET isSyncing = 0, syncState = 'failed', lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String?)

    @Query("UPDATE pending_updates SET isSyncing = 0, syncState = 'queued' WHERE isSyncing = 1")
    suspend fun resetInterruptedSyncs()
}

