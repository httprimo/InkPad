package com.personal.inkpad.data.repo

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class PublicExport(
    val displayName: String,
    val mimeType: String,
    /** Content URI safe to open/share; visible under Downloads/InkPad when possible. */
    val uri: Uri,
    val locationHint: String
)

object PublicExportWriter {
    fun publish(context: Context, source: File, mimeType: String, displayName: String): PublicExport {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishMediaStore(context, source, mimeType, displayName)
        } else {
            publishLegacyDownloads(context, source, mimeType, displayName)
        }
    }

    fun shareIntent(uri: Uri, mimeType: String, title: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun publishMediaStore(
        context: Context,
        source: File,
        mimeType: String,
        displayName: String
    ): PublicExport {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.IS_PENDING, 1)
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/InkPad"
            )
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: error("Could not create Downloads entry")
        resolver.openOutputStream(uri)?.use { output ->
            FileInputStream(source).use { input -> input.copyTo(output) }
        } ?: error("Could not write export")
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return PublicExport(
            displayName = displayName,
            mimeType = mimeType,
            uri = uri,
            locationHint = "Downloads/InkPad/$displayName"
        )
    }

    @Suppress("DEPRECATION")
    private fun publishLegacyDownloads(
        context: Context,
        source: File,
        mimeType: String,
        displayName: String
    ): PublicExport {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "InkPad"
        )
        if (!dir.exists()) dir.mkdirs()
        val dest = File(dir, displayName)
        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            dest
        )
        return PublicExport(
            displayName = displayName,
            mimeType = mimeType,
            uri = uri,
            locationHint = "Downloads/InkPad/$displayName"
        )
    }
}
