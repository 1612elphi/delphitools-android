package com.delphi.delphitools.tools.images

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.core.content.FileProvider
import com.delphi.delphitools.tools.FileExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

/** dt-core SplitSource: an image decoded upright, no side over the decode limit, and the file's own upright size. */
class DecodedImage(val image: Bitmap, val width: Int, val height: Int)

/** Every pixel, row by row. */
fun Bitmap.argb(): IntArray = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

/** Decoding, PNG encoding and handing images out, for the image tools. */
object ImageFiles {
    /** [body], or null when it runs out of memory. */
    inline fun <T> unlessOutOfMemory(body: () -> T): T? = try {
        body()
    } catch (_: OutOfMemoryError) {
        null
    }

    // ImageDecoder reports and applies the EXIF orientation
    fun decode(source: ImageDecoder.Source, maxPixels: Int): DecodedImage? = runCatching {
        var width = 0
        var height = 0
        val image = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            width = info.size.width
            height = info.size.height
            val scale = maxPixels.toFloat() / maxOf(width, height)
            if (scale < 1f) decoder.setTargetSize(maxOf(1, (width * scale).roundToInt()), maxOf(1, (height * scale).roundToInt()))
        }
        val software = if (image.config == Bitmap.Config.ARGB_8888) image else image.copy(Bitmap.Config.ARGB_8888, false)
        DecodedImage(software, width, height)
    }.getOrNull()

    fun png(image: Bitmap): ByteArray? = runCatching {
        ByteArrayOutputStream().use { out ->
            check(image.compress(Bitmap.CompressFormat.PNG, 100, out))
            out.toByteArray()
        }
    }.getOrNull()

    /** Puts a PNG on the clipboard; the file stays in the cache until the next copy. */
    suspend fun copyPng(context: Context, data: ByteArray, name: String = "image.png") {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "clipboard")
            dir.deleteRecursively()
            try {
                dir.mkdirs()
                File(dir, FileExport.safeFilename(name)).apply { writeBytes(data) }
            } catch (_: IOException) {
                null
            }
        } ?: return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val clip = ClipData.newUri(context.contentResolver, file.name, uri)
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
    }

    /** Writes the files to the export cache off the main thread, then opens the share sheet on all of them. */
    suspend fun shareAll(context: Context, files: List<Pair<String, ByteArray>>, mimeType: String) {
        val written = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports")
            // clears the previous export
            dir.deleteRecursively()
            try {
                dir.mkdirs()
                files.map { (name, data) -> File(dir, FileExport.safeFilename(name)).apply { writeBytes(data) } }
            } catch (_: IOException) {
                null
            }
        } ?: return
        context.startActivity(FileExport.chooser(context, written, mimeType))
    }
}
