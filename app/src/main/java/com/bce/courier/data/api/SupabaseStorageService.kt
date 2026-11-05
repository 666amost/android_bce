package com.bce.courier.data.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.bce.courier.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

object SupabaseStorageService {
    private const val TAG = "SupabaseStorage"
    private const val BUCKET_NAME = "shipment-photos"
    private const val FOLDER_PATH = "proof-of-delivery"

    // Max image dimensions and quality for compression
    private const val MAX_WIDTH = 1200
    private const val MAX_HEIGHT = 1200
    private const val JPEG_QUALITY = 70

    // Watermark settings
    private const val WATERMARK_TEXT_SIZE = 14f
    private const val WATERMARK_PADDING = 12f
    private const val WATERMARK_COLOR = 0xB3FFFFFF.toInt() // White with ~70% opacity
    private const val WATERMARK_SHADOW_COLOR = 0x66000000 // Semi-transparent black shadow

    /**
     * Upload image to Supabase Storage with compression
     * @return Public URL of uploaded image, or null if failed
     */
    suspend fun uploadImage(
        context: Context,
        uri: Uri,
        awbNumber: String,
        isCameraPhoto: Boolean = false,
        courierName: String? = null,
        locationText: String? = null
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val compressedBytes = compressImage(context, uri, isCameraPhoto, courierName, locationText)
                if (compressedBytes == null) {
                    Log.e(TAG, "Failed to compress image")
                    return@withContext null
                }

                Log.d(TAG, "Compressed image size: ${compressedBytes.size / 1024}KB")

                uploadToSupabase(compressedBytes, awbNumber)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading image", e)
                return@withContext null
            }
        }
    }

    suspend fun uploadImage(
        context: Context,
        file: java.io.File,
        awbNumber: String,
        isCameraPhoto: Boolean = false,
        courierName: String? = null,
        locationText: String? = null
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val compressedBytes = compressImageFromFile(file, isCameraPhoto, courierName, locationText)
                if (compressedBytes == null) {
                    Log.e(TAG, "Failed to compress image from file")
                    return@withContext null
                }

                Log.d(TAG, "Compressed image size: ${compressedBytes.size / 1024}KB")

                uploadToSupabase(compressedBytes, awbNumber)
            } catch (e: CancellationException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading image from file", e)
                return@withContext null
            }
        }
    }

    private suspend fun uploadToSupabase(
        compressedBytes: ByteArray,
        awbNumber: String,
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                val fileName = "$awbNumber-$timestamp.jpg"
                val filePath = "$FOLDER_PATH/$fileName"

                val uploadUrl = "${BuildConfig.SUPABASE_URL}/storage/v1/object/$BUCKET_NAME/$filePath"

                val requestBody = compressedBytes.toRequestBody("image/jpeg".toMediaType())

                val requestBuilder = Request.Builder()
                    .url(uploadUrl)
                    .post(requestBody)
                    .addHeader("Content-Type", "image/jpeg")
                    .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)

                val request = requestBuilder.build()

                Log.d(TAG, "Uploading to: $uploadUrl")
                NetworkModule.authenticatedClient.newCall(request).execute().use { response ->
                  if (response.isSuccessful) {
                    val publicUrl = "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/$BUCKET_NAME/$filePath"
                    Log.d(TAG, "Image uploaded successfully: $publicUrl")
                    return@withContext publicUrl
                  } else {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "Upload failed: ${response.code} - $errorBody")
                    return@withContext null
                  }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading to supabase", e)
                return@withContext null
            }
        }
    }

    private fun compressImageFromFile(
        file: java.io.File,
        isCameraPhoto: Boolean,
        courierName: String?,
        locationText: String?
    ): ByteArray? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)

            val (width, height) = options.outWidth to options.outHeight
            var sampleSize = 1
            while (width / sampleSize > MAX_WIDTH * 2 || height / sampleSize > MAX_HEIGHT * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            var bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from file")
                return null
            }

            if (isCameraPhoto) {
                val rotatedBitmap = fixOrientationFromFile(file.absolutePath, bitmap)
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = rotatedBitmap
                }
            }

            val scaleFactor = minOf(
                MAX_WIDTH.toFloat() / bitmap.width,
                MAX_HEIGHT.toFloat() / bitmap.height,
                1f
            )

            if (scaleFactor < 1f) {
                val newWidth = (bitmap.width * scaleFactor).toInt()
                val newHeight = (bitmap.height * scaleFactor).toInt()
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                if (scaledBitmap != bitmap) {
                    bitmap.recycle()
                }
                bitmap = scaledBitmap
            }

            if (isCameraPhoto) {
                bitmap = addWatermark(bitmap, courierName, locationText)
            }

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            bitmap.recycle()

            val result = outputStream.toByteArray()
            Log.d(TAG, "Final compressed size from file: ${result.size / 1024}KB")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image from file", e)
            null
        }
    }

    private fun fixOrientationFromFile(filePath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap
            }

            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            Log.d(TAG, "Fixed orientation from file: $orientation")
            rotated
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fix orientation from file, using original", e)
            bitmap
        }
    }

    /**
     * Compress image from URI with optional watermark for camera photos
     * @return Compressed JPEG bytes, or null if failed
     */
    private fun compressImage(
        context: Context,
        uri: Uri,
        isCameraPhoto: Boolean,
        courierName: String?,
        locationText: String?
    ): ByteArray? {
        return try {
            // Read bitmap dimensions first
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            // Calculate sample size for memory efficiency
            val (width, height) = options.outWidth to options.outHeight
            var sampleSize = 1
            while (width / sampleSize > MAX_WIDTH * 2 || height / sampleSize > MAX_HEIGHT * 2) {
                sampleSize *= 2
            }

            // Decode with sample size
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            var bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap")
                return null
            }

            // Fix orientation from EXIF if this is a camera photo
            if (isCameraPhoto) {
                val rotatedBitmap = fixOrientation(context, uri, bitmap)
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = rotatedBitmap
                }
            }

            // Scale down if still too large
            val scaleFactor = minOf(
                MAX_WIDTH.toFloat() / bitmap.width,
                MAX_HEIGHT.toFloat() / bitmap.height,
                1f
            )

            if (scaleFactor < 1f) {
                val newWidth = (bitmap.width * scaleFactor).toInt()
                val newHeight = (bitmap.height * scaleFactor).toInt()
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                if (scaledBitmap != bitmap) {
                    bitmap.recycle()
                }
                bitmap = scaledBitmap
            }

            // Apply watermark only for camera photos
            if (isCameraPhoto) {
                bitmap = addWatermark(bitmap, courierName, locationText)
            }

            // Compress to JPEG
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            bitmap.recycle()

            val result = outputStream.toByteArray()
            Log.d(TAG, "Final compressed size: ${result.size / 1024}KB (camera=$isCameraPhoto)")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image", e)
            null
        }
    }

    /**
     * Fix image orientation based on EXIF data
     */
    private fun fixOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                else -> return bitmap // No rotation needed
            }

            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            Log.d(TAG, "Fixed orientation: $orientation")
            rotated
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fix orientation, using original", e)
            bitmap
        }
    }

    /**
     * Draw watermark on bottom-right with courier name, location and timestamp
     */
    private fun addWatermark(
        source: Bitmap,
        courierName: String?,
        locationText: String?
    ): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WATERMARK_COLOR
            textSize = WATERMARK_TEXT_SIZE * (result.width / 1080f).coerceAtLeast(1f)
            setShadowLayer(4f, 2f, 2f, WATERMARK_SHADOW_COLOR)
        }

        val name = courierName ?: "Kurir"
        val location = locationText ?: "Lokasi tidak tersedia"
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

        val lines = listOf(
            "POD by $name",
            location,
            timestamp
        )

        val textHeights = lines.map { paint.fontMetrics.run { bottom - top } }
        val lineHeight = textHeights.maxOrNull() ?: 0f
        val totalHeight = lines.size * lineHeight + (lines.size - 1) * 4f
        val maxWidth = lines.maxOf { paint.measureText(it) }

        val padding = WATERMARK_PADDING * (result.width / 1080f).coerceAtLeast(1f)
        var y = result.height - padding - totalHeight + lineHeight
        val x = result.width - padding - maxWidth

        lines.forEach { text ->
            canvas.drawText(text, x, y, paint)
            y += lineHeight + 4f
        }

        return result
    }
}
