package com.scanrift.android.service.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File

object ShareHelper {

    fun shareFile(
        context: Context,
        content: String,
        fileName: String,
        mimeType: String = "text/plain"
    ) {
        try {
            val sharedDir = File(context.cacheDir, "shared")
            sharedDir.mkdirs()
            val file = File(sharedDir, fileName)
            file.writeText(content)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "Share $fileName"))
        } catch (e: Exception) {
            Timber.e(e, "Failed to share file: %s", fileName)
        }
    }

    fun shareCSV(context: Context, content: String, fileName: String = "collection.csv") {
        shareFile(context, content, fileName, "text/csv")
    }

    fun shareJSON(context: Context, content: String, fileName: String = "collection.json") {
        shareFile(context, content, fileName, "application/json")
    }
}
