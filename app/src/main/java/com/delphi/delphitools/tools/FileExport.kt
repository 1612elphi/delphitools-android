package com.delphi.delphitools.tools

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** dt-ios FileExportService: cache files offered through the share sheet. */
object FileExport {
    /** Writes [data] to the cache off the main thread, then opens the share sheet on it. */
    suspend fun share(context: Context, data: ByteArray, filename: String, mimeType: String) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports")
            // clears the previous export
            dir.deleteRecursively()
            val file = File(dir, safeFilename(filename))
            try {
                dir.mkdirs()
                file.writeBytes(data)
                file
            } catch (_: IOException) {
                null
            }
        } ?: return
        context.startActivity(chooser(context, file, mimeType))
    }

    /** A share-sheet intent for [file], which must be under a cache path in `res/xml/file_paths.xml`. */
    fun chooser(context: Context, file: File, mimeType: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        return Intent.createChooser(send, null)
    }

    /** A share-sheet intent for several [files], each under a cache path in `res/xml/file_paths.xml`. */
    fun chooser(context: Context, files: List<File>, mimeType: String): Intent {
        if (files.size == 1) return chooser(context, files[0], mimeType)
        val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it) }
        val send = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType(mimeType)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(files[0].name, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        return Intent.createChooser(send, null)
    }

    /** dt-ios FileExportService.share(text:): the share sheet on plain text. */
    fun shareText(context: Context, text: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
    }

    fun safeFilename(name: String): String {
        val cleaned = name.map { if (it in "/\\:" || it.isISOControl()) '-' else it }.joinToString("")
        return cleaned.ifBlank { "export" }
    }
}
