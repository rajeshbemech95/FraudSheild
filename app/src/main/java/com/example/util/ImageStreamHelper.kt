package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.InputStream

sealed class ImageValidationResult {
    data class Valid(val bytes: ByteArray, val mimeType: String, val bitmap: Bitmap?) : ImageValidationResult()
    data class Error(val message: String) : ImageValidationResult()
}

object ImageStreamHelper {
    private const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024 // 5MB

    fun readAndValidateImageUri(context: Context, uri: Uri): ImageValidationResult {
        return try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            val inputStream: InputStream? = contentResolver.openInputStream(uri)

            if (inputStream == null) {
                return ImageValidationResult.Error("Unable to open image stream")
            }

            val bytes = inputStream.use { it.readBytes() }
            if (bytes.size > MAX_FILE_SIZE_BYTES) {
                return ImageValidationResult.Error("Image exceeds 5MB size limit")
            }

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ImageValidationResult.Valid(bytes, mimeType, bitmap)
        } catch (e: Exception) {
            ImageValidationResult.Error("Failed to read image: ${e.message}")
        }
    }
}
