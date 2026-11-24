package com.delphi.delphitools.tools.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
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

    /** A private copy of [uri], so the file outlives the picker's grant; deleted again when the caller is cancelled. */
    suspend fun copy(context: Context, uri: Uri, extension: String = "pdf"): Pair<File, String>? {
        val copied = withContext(NonCancellable + Dispatchers.IO) {
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
        if (!currentCoroutineContext().isActive) copied?.first?.delete()
        currentCoroutineContext().ensureActive()
        return copied
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

    /** [body] with one renderer for [file], closed afterwards even on cancellation; null when it cannot be opened. */
    suspend fun <T> session(file: File, body: suspend (PdfRenderer) -> T): T? {
        val fd = withContext(Dispatchers.IO) {
            runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
        } ?: return null
        val renderer = withContext(Dispatchers.IO) { runCatching { PdfRenderer(fd) }.getOrNull() }
        try {
            return body(renderer ?: return null)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                renderer?.close()
                fd.close()
            }
        }
    }

    /** Page sizes as viewed; null when PdfRenderer cannot read them or there are none. */
    suspend fun measure(renderer: PdfRenderer): List<PdfSize>? = withContext(Dispatchers.IO) {
        runCatching {
            (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { PdfSize(it.width.toDouble(), it.height.toDouble()) }
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Null when PdfRenderer cannot open it: damaged, password protected, or no pages. */
    suspend fun source(file: File, name: String): PdfSource? =
        session(file) { measure(it) }?.let { PdfSource(file, name, file.length(), it) }

    /** One page on white at [scale] pixels per point, the crop box as viewed, turned [turn] more degrees clockwise. */
    fun render(renderer: PdfRenderer, index: Int, scale: Double, turn: Int = 0): Bitmap? = runCatching {
        renderer.openPage(index).use { page ->
            val size = PdfSize(page.width.toDouble(), page.height.toDouble())
            val (w, h) = PdfPagesEngine.pixels(size, PdfPagesEngine.cappedScale(scale, size, MAX_BITMAP_PIXELS))
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            if (turn % 360 == 0) {
                bitmap
            } else {
                Bitmap.createBitmap(bitmap, 0, 0, w, h, Matrix().apply { setRotate(turn.toFloat()) }, true).also { bitmap.recycle() }
            }
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
        session(file) { streamThumbnails(it, maxEdge, onBatch) }
    }

    suspend fun streamThumbnails(renderer: PdfRenderer, maxEdge: Int = 240, onBatch: (Map<Int, ImageBitmap>) -> Unit) {
        for (first in 0 until renderer.pageCount step 4) {
            val batch = withContext(Dispatchers.IO) {
                (first until minOf(first + 4, renderer.pageCount)).mapNotNull { index ->
                    thumbnail(renderer, index, maxEdge)?.let { index to it.asImageBitmap() }
                }.toMap()
            }
            onBatch(batch)
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
            var kept = false
            try {
                PdfDocuments.session(file) { renderer ->
                    val sizes = PdfDocuments.measure(renderer) ?: return@session
                    val loaded = PdfSource(file, shown, file.length(), sizes)
                    source = loaded
                    kept = true
                    onLoad?.invoke(loaded)
                    PdfDocuments.streamThumbnails(renderer) { thumbnails.putAll(it) }
                }
            } finally {
                if (!kept) file.delete()
            }
            if (!kept) failed = true
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

/** A canvas section with [size]'s aspect, for a page preview. */
fun pageCanvas(size: PdfSize, background: androidx.compose.ui.graphics.Color,content: @Composable BoxScope.() -> Unit) = CanonSection.canvas(
    CanonCanvasSize.Aspect(maxOf(0.2, size.width / maxOf(size.height, 1.0)).toFloat()),
    background = background,
    maxHeight = 640.dp,
    boxing = CanonCanvasBoxing.UnlessWide,
    content = content,
)

/** dt-ios PDFPager: previous, "page / count", next. */
@Composable
fun PdfPager(page: Int, count: Int, onStep: (Int) -> Unit) {
    val c = LocalDelphiColours.current
    CanonGrid {
        CanonButton("", Modifier.fillMaxHeight(), icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Previous page", enabled = page > 0) {
            onStep(-1)
        }
        Box(Modifier.weight(1f).fillMaxHeight().background(c.card), contentAlignment = Alignment.Center) {
            Text(if (count == 0) "" else "${page + 1} / $count", style = CanonText.bodySemibold, color = c.foreground)
        }
        CanonButton("", Modifier.fillMaxHeight(), icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Next page", enabled = page < count - 1) {
            onStep(1)
        }
    }
}

/** dt-ios PDFSourceLoader.show(in:): the file's name over its page count and size, a Clear button beside. */
@Composable
fun PdfFileRow(source: PdfSource?, name: String, failed: Boolean, onClear: () -> Unit) = PdfFileRow(source, name, failed, null, onClear)

/** [suffix] follows the size in the detail line. */
@Composable
fun PdfFileRow(source: PdfSource?, name: String, failed: Boolean, suffix: String?, onClear: () -> Unit) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val detail = when {
        failed -> "Could not read $name"
        source != null -> "${source.pageCount} pages · ${PdfDocuments.byteLabel(context, source.bytes)}" + (suffix?.let { " · $it" } ?: "")
        else -> " "
    }
    CanonRow(name, detail = detail, detailColour = if (failed) c.destructive else null, flush = true) {
        CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = onClear)
    }
}
