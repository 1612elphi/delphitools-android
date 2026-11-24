package com.delphi.delphitools.tools.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.format.Formatter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** dt-ios PDFSource: a PDF copied into the cache, its page count, file size and page sizes as viewed. */
class PdfSource(val file: File, val name: String, val bytes: Long, val sizes: List<PdfSize>) {
    val pageCount get() = sizes.size
    val stem get() = name.substringBeforeLast('.').ifEmpty { "document" }
}

/** dt-ios PDFDocumentLoader: opening, measuring and drawing PDFs through PdfRenderer. */
object PdfDocuments {
    const val MIME = "application/pdf"

    // ponytail: 40 MP cap, 160 MB of ARGB; tile the render if poster pages need full dpi
    const val MAX_BITMAP_PIXELS = 40_000_000.0

    private fun folder(context: Context, name: String) = File(context.cacheDir, name).apply { mkdirs() }

    /** A private copy of [uri], so the file outlives the picker's grant. */
    suspend fun copy(context: Context, uri: Uri, extension: String = "pdf"): Pair<File, String>? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.canonDisplayName(uri) ?: "document.$extension"
        val file = File(folder(context, "pdf-sources"), "${UUID.randomUUID()}.$extension")
        runCatching {
            resolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            file to name
        }.getOrElse {
            file.delete()
            null
        }
    }

    /** A fresh folder for files a tool writes, under a path the share sheet can read. */
    fun outputFolder(context: Context): File = File(folder(context, "jobs"), UUID.randomUUID().toString()).apply { mkdirs() }

    fun discard(file: File?) {
        file ?: return
        if (file.parentFile?.parentFile?.name == "jobs") file.parentFile?.deleteRecursively() else file.delete()
    }

    fun <T> open(file: File, body: (PdfRenderer) -> T): T? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use(body) }
    }.getOrNull()

    /** Null when PdfRenderer cannot open it: damaged, password protected, or no pages. */
    suspend fun source(file: File, name: String): PdfSource? = withContext(Dispatchers.IO) {
        open(file) { renderer ->
            (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { PdfSize(it.width.toDouble(), it.height.toDouble()) }
            }
        }?.takeIf { it.isNotEmpty() }?.let { PdfSource(file, name, file.length(), it) }
    }

    /** One page on white at [scale] pixels per point, the crop box as viewed, turned [turn] more degrees clockwise. */
    fun render(renderer: PdfRenderer, index: Int, scale: Double, turn: Int = 0): Bitmap? = runCatching {
        renderer.openPage(index).use { page ->
            val size = PdfSize(page.width.toDouble(), page.height.toDouble())
            val (w, h) = PdfPagesEngine.pixels(size, PdfPagesEngine.cappedScale(scale, size, MAX_BITMAP_PIXELS))
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            if (turn % 360 == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, w, h, Matrix().apply { setRotate(turn.toFloat()) }, true)
        }
    }.getOrNull()

    /** [render] sized so the long edge is [maxEdge] pixels. */
    fun thumbnail(renderer: PdfRenderer, index: Int, maxEdge: Int, turn: Int = 0): Bitmap? {
        val (w, h) = runCatching { renderer.openPage(index).use { it.width to it.height } }.getOrNull() ?: return null
        return render(renderer, index, maxEdge.toDouble() / maxOf(w, h, 1), turn)
    }

    /**
     * Thumbnails of every page, four at a time to [onBatch] on the caller's thread, until the caller is cancelled.
     * ponytail: 240 px long edge for every page held, about 160 MB for 500 pages; render only visible cells if that bites
     */
    suspend fun streamThumbnails(file: File, maxEdge: Int = 240, onBatch: (Map<Int, ImageBitmap>) -> Unit) {
        val fd = withContext(Dispatchers.IO) {
            runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
        } ?: return
        val renderer = withContext(Dispatchers.IO) { runCatching { PdfRenderer(fd) }.getOrNull() }
        try {
            renderer ?: return
            for (first in 0 until renderer.pageCount step 4) {
                val batch = withContext(Dispatchers.IO) {
                    (first until minOf(first + 4, renderer.pageCount)).mapNotNull { index ->
                        thumbnail(renderer, index, maxEdge)?.let { index to it.asImageBitmap() }
                    }.toMap()
                }
                onBatch(batch)
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                renderer?.close()
                fd.close()
            }
        }
    }

    /** JPEG for an opaque bitmap; zlib RGB with a zlib alpha mask otherwise. */
    fun picture(bitmap: Bitmap, quality: Int): PdfPicture? {
        if (!bitmap.hasAlpha()) {
            val jpeg = ByteArrayOutputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) return null
                out.toByteArray()
            }
            return PdfPicture(bitmap.width, bitmap.height, jpeg, dct = true)
        }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val rgb = ByteArray(pixels.size * 3)
        val alpha = ByteArray(pixels.size)
        for ((i, p) in pixels.withIndex()) {
            val a = p ushr 24
            alpha[i] = a.toByte()
            // getPixels is unpremultiplied
            rgb[i * 3] = (p shr 16).toByte()
            rgb[i * 3 + 1] = (p shr 8).toByte()
            rgb[i * 3 + 2] = p.toByte()
        }
        return PdfPicture(bitmap.width, bitmap.height, PdfFilters.deflate(rgb), dct = false, mask = PdfFilters.deflate(alpha))
    }

    fun byteLabel(context: Context, count: Long): String = Formatter.formatShortFileSize(context, count)
}

/** dt-ios PDFSourceLoader: one PDF opened by a tool, its facts, and thumbnails as they stream in. */
class PdfSourceLoader(private val scope: CoroutineScope, private val context: Context) {
    /** Set at once on [load], before the copy and the read finish. */
    var opened by mutableStateOf(false)
        private set
    var name by mutableStateOf("")
        private set
    var source by mutableStateOf<PdfSource?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    val thumbnails = mutableStateMapOf<Int, ImageBitmap>()

    /** Before the first thumbnail. */
    var onLoad: ((PdfSource) -> Unit)? = null
    private var task: Job? = null

    fun load(uri: Uri) {
        clear()
        opened = true
        task = scope.launch {
            val (file, shown) = PdfDocuments.copy(context, uri) ?: run {
                failed = true
                return@launch
            }
            name = shown
            val loaded = PdfDocuments.source(file, shown)
            if (loaded == null) {
                file.delete()
                failed = true
                return@launch
            }
            source = loaded
            onLoad?.invoke(loaded)
            PdfDocuments.streamThumbnails(file) { thumbnails.putAll(it) }
        }
    }

    fun clear() {
        task?.cancel()
        source?.file?.delete()
        opened = false
        name = ""
        source = null
        failed = false
        thumbnails.clear()
    }
}

/** dt-ios PDFSourceLoader.show(in:): the file's name over its page count and size, a Clear button beside. */
@Composable
fun PdfFileRow(source: PdfSource?, name: String, failed: Boolean, onClear: () -> Unit) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val detail = when {
        failed -> "Could not read $name"
        source != null -> "${source.pageCount} pages · ${PdfDocuments.byteLabel(context, source.bytes)}"
        else -> " "
    }
    CanonRow(name, detail = detail, detailColour = if (failed) c.destructive else null, flush = true) {
        CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = onClear)
    }
}
