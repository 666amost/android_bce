package com.bce.courier.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

object ImageCompressor {

    fun compressImage(
        context: Context,
        imageFile: File,
        maxSizeKb: Int = 300,
        quality: Int = 85
    ): File? {
        return try {
            val originalBitmap = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return null

            val rotatedBitmap = rotateImageIfRequired(originalBitmap, imageFile.absolutePath)

            val scale = calculateScale(rotatedBitmap.width, rotatedBitmap.height)
            val scaledBitmap = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    rotatedBitmap,
                    (rotatedBitmap.width * scale).toInt(),
                    (rotatedBitmap.height * scale).toInt(),
                    true
                )
            } else {
                rotatedBitmap
            }

            val compressedFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
            var currentQuality = quality
            var outputSize: Long

            do {
                FileOutputStream(compressedFile).use { out ->
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, out)
                }
                outputSize = compressedFile.length() / 1024
                currentQuality -= 5
            } while (outputSize > maxSizeKb && currentQuality > 50)

            if (scaledBitmap != originalBitmap) scaledBitmap.recycle()
            if (rotatedBitmap != originalBitmap) rotatedBitmap.recycle()
            originalBitmap.recycle()

            compressedFile
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateScale(width: Int, height: Int): Float {
        val maxDimension = 1920
        val maxSize = min(width, height)
        return if (maxSize > maxDimension) {
            maxDimension.toFloat() / maxSize
        } else {
            1f
        }
    }

    private fun rotateImageIfRequired(bitmap: Bitmap, path: String): Bitmap {
        return try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun rotateImage(bitmap: Bitmap, degree: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

