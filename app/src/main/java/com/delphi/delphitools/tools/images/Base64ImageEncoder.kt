package com.delphi.delphitools.tools.images

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.scale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Base64
import java.util.UUID
import kotlin.math.roundToInt

private const val THUMBNAIL_PIXELS = 240
private val mosaicHeight = 216.dp

// ponytail: binder caps a clip near 1 MB of UTF-16; longer strings go out as a text file through the share sheet
private const val CLIPBOARD_CHARS = 400_000

private const val FAINT = 0.28f
private val idle = Base64.getEncoder().encodeToString("delphitools base64 ".repeat(60).toByteArray())

/** dt-core Base64Image: one image file as Base64, with a thumbnail for the mosaic. */
class Base64Image(val name: String, val mime: String, val bytes: Int, val raw: String, val thumbnail: Bitmap?) {
    val id: String = UUID.randomUUID().toString()
    val dataUri get() = Base64ImageEngine.dataUri(mime, raw)
    val uriBytes get() = Base64ImageEngine.uriBytes(mime, raw)
    val preview = Base64ImageEngine.preview(mime, raw)
    val thumbnailImage: ImageBitmap? = thumbnail?.asImageBitmap()
}

private sealed interface Loaded {
    class Image(val image: Base64Image) : Loaded
    data object NotImage : Loaded
    class TooLarge(val bytes: Long) : Loaded
}

class Base64ImageEncoderModel(application: Application) : AndroidViewModel(application) {
    var images by mutableStateOf(emptyList<Base64Image>())
        private set
    var selected by mutableStateOf<String?>(null)
        private set
    var skipped by mutableStateOf(emptyList<String>())
        private set

    fun load(uris: List<Uri>) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            for (uri in uris) {
                val (name, loaded) = withContext(Dispatchers.IO) {
                    val name = app.contentResolver.canonDisplayName(uri) ?: "image"
                    name to read(app, uri, name)
                }
                when (loaded) {
                    is Loaded.Image -> {
                        images = listOf(loaded.image) + images
                        selected = loaded.image.id
                    }
                    Loaded.NotImage -> skipped = skipped + "$name is not an image"
                    is Loaded.TooLarge -> skipped = skipped + "$name is ${Formatter.formatShortFileSize(app, loaded.bytes)}, over the 5 MB limit"
                }
            }
        }
    }

    private fun read(context: Context, uri: Uri, name: String): Loaded {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.takeIf { it.startsWith("image/") }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())?.takeIf { it.startsWith("image/") }
            ?: return Loaded.NotImage
        val size = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else 0L }
        }.getOrNull() ?: 0L
        if (size > Base64ImageEngine.MAX_BYTES) return Loaded.TooLarge(size)
        val data = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return Loaded.NotImage
        if (data.size > Base64ImageEngine.MAX_BYTES) return Loaded.TooLarge(data.size.toLong())
        val thumbnail = ImageFiles.decode(ImageDecoder.createSource(data), THUMBNAIL_PIXELS)?.image
        return Loaded.Image(Base64Image(name, mime, data.size, Base64.getEncoder().encodeToString(data), thumbnail))
    }

    fun select(id: String) {
        selected = id
    }

    fun remove(id: String) {
        images = images.filter { it.id != id }
        if (selected == id) selected = images.firstOrNull()?.id
    }

    fun clearAll() {
        images = emptyList()
        skipped = emptyList()
    }

    fun copy(context: Context, text: String, name: String) {
        if (text.length <= CLIPBOARD_CHARS) {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(name, text))
            return
        }
        viewModelScope.launch { FileExport.share(context, text.toByteArray(), "${name.substringBeforeLast('.')}.txt", "text/plain") }
    }
}

@Composable
fun Base64ImageEncoder(model: Base64ImageEncoderModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val images = model.images

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Images")
            CanonDropZone("Drop images here", mimeTypes = arrayOf("*/*"), multiple = true, paste = false, onFiles = model::load)
            if (model.skipped.isNotEmpty()) {
                CanonHeading("Skipped")
                model.skipped.forEach { CanonStatusRow(it, CanonTone.Warn) }
            }
            if (images.isNotEmpty() || model.skipped.isNotEmpty()) {
                CanonButton("Clear all", icon = Icons.Outlined.Delete, onClick = model::clearAll)
            }
        }
    }

    val main = if (images.isEmpty()) {
        listOf(CanonSection.of { CanonRows { CanonHeading("Encoded"); CanonEmptyRow("Data URI") } })
    } else {
        images.map { image ->
            CanonSection.of {
                EncodedBlock(
                    image,
                    lit = image.id == model.selected && images.size > 1,
                    onSelect = { model.select(image.id) },
                    onRemove = { model.remove(image.id) },
                    onCopy = { text -> model.copy(context, text, image.name) },
                )
            }
        }
    }

    val shown = images.firstOrNull { it.id == model.selected }
    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(mosaicHeight), background = c.card) {
        Base64Mosaic(shown, Modifier.fillMaxSize().padding(top = LocalCanonCanvas.current.top))
        val (value, unit) = Base64ImageEngine.figure(shown?.uriBytes ?: 0)
        CanonFigure(
            value,
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = CanonMetrics.panelPadding, bottom = 10.dp)
                .alpha(if (shown == null) 0f else 1f),
            unit = unit,
            size = 30.sp,
            halo = c.card,
        )
    }

    CanonSplit(sidebar = listOf(input), main = main, banner = banner)
}

@Composable
private fun EncodedBlock(image: Base64Image, lit: Boolean, onSelect: () -> Unit, onRemove: () -> Unit, onCopy: (String) -> Unit) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val fits = image.uriBytes <= CLIPBOARD_CHARS
    CanonRows {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (lit) c.accentBg else c.card)
                    .canonClickable(onClick = onSelect)
                    .semantics(mergeDescendants = true) {
                        contentDescription = image.name
                        role = Role.Button
                    }
                    .padding(start = CanonMetrics.panelPadding, top = 10.dp, end = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(44.dp).background(c.muted)) {
                    image.thumbnailImage?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(image.name, style = CanonText.subheadline, color = c.foreground, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    Text(
                        "${Formatter.formatShortFileSize(context, image.bytes.toLong())} → ${Formatter.formatShortFileSize(context, image.uriBytes.toLong())}",
                        style = CanonText.caption,
                        color = c.mutedForeground,
                    )
                }
            }
            CanonButton("", Modifier.width(56.dp).fillMaxHeight(), icon = Icons.Outlined.Close, contentDescription = "Remove", onClick = onRemove)
        }
        Text(
            image.preview,
            style = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp),
            color = c.foreground,
            maxLines = 4,
            overflow = TextOverflow.Clip,
            modifier = Modifier.fillMaxWidth().background(c.card).padding(horizontal = CanonMetrics.panelPadding, vertical = 12.dp),
        )
        CanonGrid {
            val cell = Modifier.weight(1f).fillMaxHeight()
            CanonButton("Copy Raw Base64", cell, icon = Icons.Rounded.ContentCopy, flashesCopied = fits) { onCopy(image.raw) }
            CanonButton("Copy Data URI", cell, kind = CanonButtonKind.Primary, icon = Icons.Rounded.ContentCopy, flashesCopied = fits) {
                onCopy(image.dataUri)
            }
        }
    }
}

private class Sampled(val id: String, val columns: Int, val rows: Int, val colours: IntArray)

/** dt-ios Base64Mosaic: the Base64 text as a wall of characters, those over the picture tinted by its pixels. */
@Composable
private fun Base64Mosaic(image: Base64Image?, modifier: Modifier) {
    val c = LocalDelphiColours.current
    val faint = c.mutedForeground.copy(alpha = FAINT).toArgb()
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) } }
    // read in draw: a state here would redraw again
    val sampled = remember { arrayOfNulls<Sampled>(1) }
    Canvas(modifier) {
        paint.textSize = 7.sp.toPx()
        val cellWidth = paint.measureText("M")
        val metrics = paint.fontMetrics
        val cellHeight = metrics.descent - metrics.ascent
        val top = 6.dp.toPx()
        val side = 8.dp.toPx()
        val columns = maxOf(1, ((size.width - side * 2) / cellWidth).toInt())
        val rows = maxOf(1, ((size.height - top - 6.dp.toPx()) / cellHeight).toInt())
        val text = (image?.raw?.take(columns * rows) ?: idle).ifEmpty { " " }
        val originX = size.width / 2 - columns * cellWidth / 2

        val thumbnail = image?.thumbnail
        val cover = thumbnail?.let { Base64ImageEngine.cover(columns, rows, cellWidth, cellHeight, it.width.toFloat(), it.height.toFloat()) }
        val pixels = if (thumbnail != null && cover != null) {
            val cached = sampled[0]?.takeIf { it.id == image.id && it.columns == cover[2] && it.rows == cover[3] }
            (cached ?: Sampled(image.id, cover[2], cover[3], sample(thumbnail, cover[2], cover[3], faint)).also { sampled[0] = it }).colours
        } else {
            null
        }

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            for (row in 0 until rows) {
                val y = top + row * cellHeight - metrics.ascent
                for (column in 0 until columns) {
                    val index = row * columns + column
                    var colour = faint
                    if (cover != null && pixels != null && column - cover[0] in 0 until cover[2] && row - cover[1] in 0 until cover[3]) {
                        colour = pixels[(row - cover[1]) * cover[2] + (column - cover[0])]
                    }
                    paint.color = colour
                    native.drawText(text, index % text.length, index % text.length + 1, originX + column * cellWidth, y, paint)
                }
            }
        }
    }
}

/** One pixel per character; clear pixels take [faint], the rest keep at least its opacity. */
private fun sample(image: Bitmap, columns: Int, rows: Int, faint: Int): IntArray {
    val small = image.scale(columns, rows)
    val pixels = IntArray(columns * rows)
    small.getPixels(pixels, 0, columns, 0, 0, columns, rows)
    val floor = (FAINT * 255).roundToInt()
    return IntArray(pixels.size) { i ->
        val alpha = pixels[i] ushr 24
        if (alpha == 0) faint else ColorUtils.setAlphaComponent(pixels[i], maxOf(floor, alpha))
    }
}
