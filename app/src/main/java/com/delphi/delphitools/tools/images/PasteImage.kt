package com.delphi.delphitools.tools.images

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDimensionPlacement
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonPlaced
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.time.LocalDate
import kotlin.math.min
import kotlin.math.roundToInt

private const val DECODE_PIXELS = 16384
private const val PREVIEW_PIXELS = 2048
private val caption = 30.dp

private class Pasted(val data: ByteArray, val mimeType: String, val preview: ImageBitmap, val width: Int, val height: Int) {
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "png"
    val whole get() = Rect(0f, 0f, width.toFloat(), height.toFloat())
}

private fun ClipDescription.hasImage() = (0 until mimeTypeCount).any { ClipDescription.compareMimeTypes(getMimeType(it), "image/*") }

private fun ClipData.imageUri(context: Context): Uri? = (0 until itemCount).mapNotNull { getItemAt(it).uri }
    .firstOrNull { uri -> context.contentResolver.getType(uri)?.let { ClipDescription.compareMimeTypes(it, "image/*") } == true }

/** dt-ios PasteImageViewController: one pasted or dropped image, cropped, copied or saved. */
class PasteImageModel(application: Application) : AndroidViewModel(application) {
    private var pasted by mutableStateOf<Pasted?>(null)
    val preview get() = pasted?.preview
    val pixels get() = pasted?.let { Size(it.width.toFloat(), it.height.toFloat()) } ?: Size.Zero
    val kind get() = pasted?.extension?.uppercase() ?: ""
    val sizeLabel get() = pasted?.let { "${it.width} × ${it.height} px" } ?: ""

    /** Image pixels, top-left origin. */
    var crop by mutableStateOf(Rect.Zero)
        private set

    /** The crop as dragged, before release. */
    var shownCrop by mutableStateOf(Rect.Zero)
        private set
    var exporting by mutableStateOf(false)
        private set
    var clipboardHasImage by mutableStateOf(false)
        private set
    val isCropped get() = pasted?.let { crop != it.whole } ?: false

    private val clipboard = application.getSystemService(ClipboardManager::class.java)
    private var loading: Job? = null

    // hasImage never shows the paste notice
    fun checkClipboard() {
        clipboardHasImage = clipboard?.primaryClipDescription?.hasImage() == true
    }

    fun paste() {
        clipboard?.primaryClip?.imageUri(getApplication())?.let(::load)
    }

    fun load(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        loading?.cancel()
        loading = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val data = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return@withContext null
                val decoded = ImageFiles.decode(ImageDecoder.createSource(ByteBuffer.wrap(data)), PREVIEW_PIXELS) ?: return@withContext null
                Pasted(data, resolver.getType(uri) ?: "image/png", decoded.image.asImageBitmap(), decoded.width, decoded.height)
            } ?: return@launch
            pasted = loaded
            commitCrop(loaded.whole)
        }
    }

    fun clear() {
        loading?.cancel()
        pasted = null
        crop = Rect.Zero
        shownCrop = Rect.Zero
    }

    fun drag(rect: Rect) {
        shownCrop = rect
    }

    fun commitCrop(rect: Rect) {
        crop = ImageCrop.integral(rect)
        shownCrop = crop
    }

    fun reset() {
        pasted?.let { commitCrop(it.whole) }
    }

    private fun cropped(pasted: Pasted, crop: Rect): Bitmap? {
        val image = ImageFiles.decode(ImageDecoder.createSource(ByteBuffer.wrap(pasted.data)), DECODE_PIXELS)?.image ?: return null
        val scale = image.width.toFloat() / maxOf(pasted.width, 1)
        val r = ImageCrop.integral(Rect(crop.left * scale, crop.top * scale, crop.right * scale, crop.bottom * scale))
        val left = r.left.toInt().coerceIn(0, image.width - 1)
        val top = r.top.toInt().coerceIn(0, image.height - 1)
        val width = r.width.toInt().coerceIn(1, image.width - left)
        val height = r.height.toInt().coerceIn(1, image.height - top)
        return Bitmap.createBitmap(image, left, top, width, height)
    }

    private fun withCroppedPng(use: suspend (ByteArray) -> Unit) {
        val pasted = pasted ?: return
        if (exporting) return
        val crop = crop
        exporting = true
        viewModelScope.launch {
            val data = withContext(Dispatchers.Default) { cropped(pasted, crop)?.let(ImageFiles::png) }
            if (data != null) use(data)
            exporting = false
        }
    }

    fun copy(context: Context) = withCroppedPng { ImageFiles.copyPng(context, it) }

    fun save(context: Context) {
        val pasted = pasted ?: return
        if (exporting) return
        val stem = "delphitools-paste-image-${LocalDate.now()}"
        if (!isCropped) {
            viewModelScope.launch { FileExport.share(context, pasted.data, "$stem.${pasted.extension}", pasted.mimeType) }
            return
        }
        withCroppedPng { FileExport.share(context, it, "$stem.png", "image/png") }
    }
}

@Composable
fun PasteImage(model: PasteImageModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { if (focused) model.checkClipboard() }
    DisposableEffect(model) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val listener = ClipboardManager.OnPrimaryClipChangedListener { model.checkClipboard() }
        clipboard?.addPrimaryClipChangedListener(listener)
        onDispose { clipboard?.removePrimaryClipChangedListener(listener) }
    }
    val loaded = model.preview != null
    val pixels = model.pixels
    val shown = model.shownCrop
    val dash = "—"

    val aspect = if (pixels.height > 0f) (pixels.width / pixels.height).coerceIn(0.75f, 1.5f) else 4f / 3f
    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(aspect), background = c.muted, showsPattern = true) {
        PasteImagePicture(model.preview, pixels, model.crop, shown, model::drag, model::commitCrop, model::load)
    }

    val source = CanonSection.of {
        CanonRows {
            CanonHeading("Pasted Image")
            if (loaded) {
                CanonRow(model.kind, detail = model.sizeLabel, flush = true) {
                    CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = model::clear)
                }
            }
            CanonGrid {
                CanonButton(
                    "Paste",
                    Modifier.weight(1f).fillMaxHeight(),
                    enabled = model.clipboardHasImage,
                    icon = Icons.Outlined.ContentPaste,
                    onClick = model::paste,
                )
            }
        }
    }

    val cropRows = CanonSection.of {
        CanonRows {
            CanonHeading("Crop")
            CanonTiles(
                listOf(
                    CanonTile("Crop size", if (loaded) "${shown.width.roundToInt()} × ${shown.height.roundToInt()} px" else dash),
                    CanonTile("Offset", if (loaded) "${shown.left.roundToInt()}, ${shown.top.roundToInt()}" else dash),
                )
            )
            CanonGrid {
                CanonButton(
                    "Reset",
                    Modifier.weight(1f).fillMaxHeight(),
                    enabled = model.isCropped,
                    icon = Icons.Rounded.Replay,
                    onClick = model::reset,
                )
            }
        }
    }

    val output = CanonSection.of {
        CanonRows {
            CanonHeading("Output")
            CanonGrid {
                CanonButton(
                    "Copy",
                    Modifier.weight(1f).fillMaxHeight(),
                    enabled = loaded && !model.exporting,
                    icon = Icons.Rounded.ContentCopy,
                    flashesCopied = true,
                ) { model.copy(context) }
                CanonButton(
                    "Save",
                    Modifier.weight(1f).fillMaxHeight(),
                    kind = CanonButtonKind.Primary,
                    enabled = loaded && !model.exporting,
                    icon = Icons.Outlined.Download,
                    oversized = true,
                ) { model.save(context) }
            }
            if (model.exporting) {
                CanonRow("Processing…") { CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground) }
            }
        }
    }

    CanonSplit(sidebar = listOf(canvas), main = listOf(source, cropRows, output), halves = true, collapsesTo = 240.dp)
}

@Composable
private fun PasteImagePicture(
    preview: ImageBitmap?,
    pixels: Size,
    crop: Rect,
    shownCrop: Rect,
    onDrag: (Rect) -> Unit,
    onChange: (Rect) -> Unit,
    onDrop: (Uri) -> Unit,
) {
    val c = LocalDelphiColours.current
    val activity = LocalActivity.current
    val density = LocalDensity.current
    val top = LocalCanonCanvas.current.top
    val drop = remember(activity) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val drag = event.toAndroidDragEvent()
                // content URIs from another app need the drop's grant
                activity?.requestDragAndDropPermissions(drag)
                val uri = activity?.let { drag.clipData?.imageUri(it) } ?: return false
                onDrop(uri)
                return true
            }
        }
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event -> event.mimeTypes().any { ClipDescription.compareMimeTypes(it, "image/*") } },
                target = drop,
            )
    ) {
        with(density) {
            val padding = CanonMetrics.panelPadding.toPx()
            val reach = ImageCropReach.toPx()
            val captionPx = caption.toPx()
            val area = Rect(padding, top.toPx() + padding * 0.5f, constraints.maxWidth - padding, constraints.maxHeight - padding)
            if (preview == null) {
                val box = ImageCrop.fit(Size(4f, 3f), area.deflate(24.dp.toPx()))
                val line = 1.5.dp.toPx()
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(
                        c.mutedForeground.copy(alpha = 0.45f),
                        box.topLeft,
                        box.size,
                        style = Stroke(line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                    )
                }
                Icon(
                    Icons.Outlined.ContentPaste,
                    contentDescription = null,
                    tint = c.mutedForeground.copy(alpha = 0.6f),
                    modifier = Modifier.canonPlaced(CanonDimensionPlacement(box.center, Size(44.dp.toPx(), 44.dp.toPx()))),
                )
                return@with
            }
            // cropper needs touch margin
            val frame = Rect(area.left - padding, area.top - padding, area.right + padding, area.top + area.height - captionPx)
            ImageCropCanvas(
                preview,
                pixels,
                crop,
                onDrag,
                onChange,
                Modifier.canonPlaced(CanonDimensionPlacement(frame.center, frame.size)),
            )
            // below image, not pane
            val shown = ImageCrop.fit(pixels, frame.deflate(reach))
            val centre = min(shown.bottom + reach / 2 + captionPx / 2, area.bottom - captionPx / 2)
            CanonFigure(
                "${shownCrop.width.roundToInt()} × ${shownCrop.height.roundToInt()}",
                Modifier
                    .align(Alignment.TopCenter)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, (centre - placeable.height / 2f).roundToInt()) }
                    },
                unit = "px",
                size = 17.sp,
                halo = c.muted,
            )
        }
    }
}
