package com.scanrift.android.ui.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

fun Context.shareFile(file: File, mimeType: String) {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, null))
}
