package com.sdevprem.runtrack.ui.share

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ShareImageUtils {

    fun shareBitmap(
        context: Context,
        bitmap: Bitmap,
        name: String,
        title: String
    ) {
        val cacheDir = File(context.cacheDir, "shared_images").apply {
            if (!exists()) {
                mkdirs()
            }
        }
        val outFile = File(cacheDir, name)
        FileOutputStream(outFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "run_share_image", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            context.startActivity(chooser)
        } else {
            Handler(Looper.getMainLooper()).post {
                context.startActivity(chooser)
            }
        }
    }
}
