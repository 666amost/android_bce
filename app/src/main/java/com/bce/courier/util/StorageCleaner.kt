package com.bce.courier.util

import android.content.Context
import android.os.Environment
import java.io.File

data class StorageCleanupResult(
    val deletedFiles: Int,
    val deletedBytes: Long
)

object StorageCleaner {

    private const val UPDATE_APK_FILE_NAME = "BCECourier-update.apk"

    fun clearAppStorage(context: Context): StorageCleanupResult {
        var deletedFiles = 0
        var deletedBytes = 0L

        fun deleteIfExists(file: File): Boolean {
            if (!file.exists()) return false
            val size = runCatching { file.length() }.getOrDefault(0L)
            val deleted = runCatching { file.delete() }.getOrDefault(false)
            if (deleted) {
                deletedFiles += 1
                deletedBytes += size
            }
            return deleted
        }

        fun deleteMatching(dir: File?, predicate: (File) -> Boolean) {
            val files = runCatching { dir?.listFiles()?.toList() }.getOrNull().orEmpty()
            files.forEach { file ->
                if (file.isFile && predicate(file)) {
                    deleteIfExists(file)
                }
            }
        }

        val appContext = context.applicationContext

        // `pending_*.jpg` may be referenced by the offline upload queue. Never delete it here.
        deleteMatching(appContext.cacheDir) { it.name.startsWith("JPEG_") && it.name.endsWith(".jpg") }
        deleteMatching(appContext.cacheDir) { it.name.startsWith("compressed_") && it.name.endsWith(".jpg") }

        val picturesDir = appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        deleteMatching(picturesDir) { it.name.startsWith("JPEG_") && it.name.endsWith(".jpg") }

        val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        deleteIfExists(File(downloadsDir, UPDATE_APK_FILE_NAME))

        return StorageCleanupResult(deletedFiles = deletedFiles, deletedBytes = deletedBytes)
    }
}
