package com.bce.courier.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class CachedShipmentDao {
    @Query(
        "SELECT * FROM cached_shipments " +
            "WHERE ownerCourierId = :courierId ORDER BY cachedAt DESC, createdAt DESC",
    )
    abstract suspend fun getForCourier(courierId: String): List<CachedShipmentEntity>

    @Query("DELETE FROM cached_shipments WHERE ownerCourierId = :courierId")
    abstract suspend fun clearForCourier(courierId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(items: List<CachedShipmentEntity>)

    @Transaction
    open suspend fun replaceForCourier(
        courierId: String,
        items: List<CachedShipmentEntity>,
    ) {
        clearForCourier(courierId)
        if (items.isNotEmpty()) insertAll(items)
    }
}
