package com.sdevprem.runtrack.data.storage

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class StoredRunImage(
    val path: String,
    val thumbnail: Bitmap
)

@Singleton
class RunImageStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun save(bitmap: Bitmap): StoredRunImage {
        val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        file.outputStream().buffered().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                "Unable to encode run map image"
            }
        }
        return StoredRunImage(
            path = file.absolutePath,
            thumbnail = createThumbnail(bitmap)
        )
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        val target = File(path)
        val allowedDirectory = File(context.filesDir, DIRECTORY_NAME).canonicalFile
        if (target.canonicalFile.parentFile == allowedDirectory) {
            target.delete()
        }
    }

    private fun createThumbnail(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= THUMBNAIL_WIDTH_PX) return bitmap
        val scale = THUMBNAIL_WIDTH_PX.toFloat() / bitmap.width
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, THUMBNAIL_WIDTH_PX, height, true)
    }

    private companion object {
        const val DIRECTORY_NAME = "run_images"
        const val JPEG_QUALITY = 88
        const val THUMBNAIL_WIDTH_PX = 360
    }
}
