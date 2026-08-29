package com.bce.courier.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PendingUpdateEntity::class, CachedShipmentEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pendingUpdateDao(): PendingUpdateDao
    abstract fun cachedShipmentDao(): CachedShipmentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bce_courier_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_shipments` (
                        `ownerCourierId` TEXT NOT NULL,
                        `awb` TEXT NOT NULL,
                        `recipientName` TEXT NOT NULL,
                        `recipientPhone` TEXT NOT NULL,
                        `address` TEXT NOT NULL,
                        `currentStatus` TEXT NOT NULL,
                        `codAmount` REAL NOT NULL,
                        `lat` REAL,
                        `lng` REAL,
                        `createdAt` TEXT NOT NULL,
                        `updatedAt` TEXT,
                        `assignedCourierId` TEXT,
                        `cachedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`ownerCourierId`, `awb`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}

