package com.bce.courier.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "pending_updates")
data class PendingUpdateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val awb: String,
    val status: String,
    val notes: String?,
    val pendingReasonCode: String?,
    val pendingReasonNote: String?,
    val latitude: Double?,
    val longitude: Double?,
    val photoPath: String?,
    val courierName: String,
    val courierId: String,
    val armada: String,
    val platArmada: String,
    val locationAddress: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val priority: Int = if (status.lowercase() == "delivered") 1 else 2,
    val retryCount: Int = 0,
    val isSyncing: Boolean = false,
    val syncState: String = "queued",
    val lastError: String? = null,
    val photoUrl: String? = null,
    @ColumnInfo(defaultValue = "0") val deliverySynced: Boolean = false,
    @ColumnInfo(defaultValue = "1") val isCameraPhoto: Boolean = true,
)

