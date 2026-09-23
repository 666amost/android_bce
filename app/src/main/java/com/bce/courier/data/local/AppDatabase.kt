package com.bce.courier.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PendingUpdateEntity::class, CachedShipmentEntity::class],
    version = 3,
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `pending_updates` ADD COLUMN `pendingReasonCode` TEXT")
                db.execSQL("ALTER TABLE `pending_updates` ADD COLUMN `pendingReasonNote` TEXT")
                db.execSQL("ALTER TABLE `pending_updates` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'queued'")
                db.execSQL("ALTER TABLE `pending_updates` ADD COLUMN `lastError` TEXT")
                db.execSQL("UPDATE `pending_updates` SET `status` = 'pending', `pendingReasonCode` = 'other', `pendingReasonNote` = 'Migrasi antrean Pending lama' WHERE lower(`status`) = 'shipped'")
                db.execSQL("ALTER TABLE `cached_shipments` ADD COLUMN `pendingReasonCode` TEXT")
                db.execSQL("ALTER TABLE `cached_shipments` ADD COLUMN `pendingReasonNote` TEXT")
                db.execSQL("ALTER TABLE `cached_shipments` ADD COLUMN `pendingAt` TEXT")
            }
        }
    }
}

