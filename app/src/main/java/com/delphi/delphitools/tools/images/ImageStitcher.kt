package com.delphi.delphitools.tools.images

import android.app.Application
import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDimensionLine
import com.delphi.delphitools.canon.CanonDimensionPlacement
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonPageStrip
import com.delphi.delphitools.canon.CanonPageStripHeight
import com.delphi.delphitools.canon.CanonPageStripPage
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.MenuCellItem
import com.delphi.delphitools.canon.MenuCellPopup
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.canonDimensionLines
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.canon.canonPlaced
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DECODE_PIXELS = 3072
private const val PREVIEW_PIXELS = 1024
private val matteColour = Color.White
private val filtered = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

private enum class AddSource { Photos, Files, Paste }

private fun StitchRect.compose() = Rect(x.toFloat(), y.toFloat(), maxX.toFloat(), maxY.toFloat())

private fun pixelSize(width: Number, height: Number) = "${width.toDouble().roundToInt()} × ${height.toDouble().roundToInt()} px"

// dt-core StitchLayoutEngine.render
private fun render(layout: StitchLayout, images: Map<UUID, Bitmap>, opaque: Boolean): Bitmap? {
    val (width, height) = ImageStitcherEngine.exportSize(layout.width, layout.height)
    val scale = width / max(layout.width, 1.0)
    val out = try {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    } catch (_: OutOfMemoryError) {
        return null
    }
    val canvas = android.graphics.Canvas(out)
    if (opaque) canvas.drawColor(android.graphics.Color.WHITE)
    val white = Paint().apply { color = android.graphics.Color.WHITE }
    for (op in layout.ops) {
        val image = images[op.src.id] ?: continue
        val d = op.dest.scaled(scale)
        val dest = RectF(d.x.toFloat(), d.y.toFloat(), d.maxX.toFloat(), d.maxY.toFloat())
        when (op.fit) {
            StitchFit.Matte -> {
                canvas.drawRect(dest, white)
                val s = min(d.width / op.src.width, d.height / op.src.height)
                val w = op.src.width * s
                val h = op.src.height * s
                canvas.drawBitmap(image, null, RectF((d.midX - w / 2).toFloat(), (d.midY - h / 2).toFloat(), (d.midX + w / 2).toFloat(), (d.midY + h / 2).toFloat()), filtered)
            }
            StitchFit.Match -> {
                val s = op.source
                // whole pixels, CGRect.integral
                val crop = android.graphics.Rect(floor(s.x).toInt(), floor(s.y).toInt(), ceil(s.maxX).toInt().coerceAtMost(image.width), ceil(s.maxY).toInt().coerceAtMost(image.height))
                canvas.drawBitmap(image, crop, dest, filtered)
            }
            StitchFit.Cheese -> canvas.drawBitmap(image, null, dest, filtered)
        }
    }
    return out
}

/** dt-ios ImageStitcherViewController's state: an individual mosaic tree, or a batch grid. */
class ImageStitcherModel(application: Application) : AndroidViewModel(application) {
    var batch by mutableStateOf(false)
        private set
    private var sources by mutableStateOf(emptyMap<UUID, StitchSource>())
    private var images = emptyMap<UUID, Bitmap>()
    var previews by mutableStateOf(emptyMap<UUID, ImageBitmap>())
        private set
    var root by mutableStateOf<StitchNode?>(null)
        private set
    var baseId by mutableStateOf<UUID?>(null)
        private set
    var selected by mutableStateOf<UUID?>(null)
        private set
    var batchIds by mutableStateOf(emptyList<UUID>())
        private set
    var loads by mutableIntStateOf(0)
        private set
    var exporting by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var png by mutableIntStateOf(0)

    /** Where the open picker's images go. */
    var pending: StitchTarget? = null

    val batchSources get() = batchIds.mapNotNull { sources[it] }

    val layout: StitchLayout?
        get() = if (batch) ImageStitcherEngine.batchLayout(batchSources)
        else root?.let { r -> baseId?.let { ImageStitcherEngine.layout(r, sources, it) } }

    fun source(id: UUID?) = id?.let { sources[it] }

    val selectedLeaf get() = selected?.let { root?.findLeaf(it) }

    val defaultTarget: StitchTarget
        get() = when {
            batch -> StitchTarget.Batch(batchIds.size)
            root == null -> StitchTarget.First
            else -> StitchTarget.Add(selected, StitchSide.Right)
        }

    fun pickMode(index: Int) {
        batch = index == 1
    }

    fun load(uris: List<Uri>, target: StitchTarget) {
        if (uris.isEmpty()) return
        val resolver = getApplication<Application>().contentResolver
        loads++
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val decoded = runCatching { ImageFiles.decode(ImageDecoder.createSource(resolver, uri), DECODE_PIXELS) }.getOrNull() ?: return@mapNotNull null
                    val preview = ImageFiles.unlessOutOfMemory { ImageCompressEngine.preview(decoded.image, PREVIEW_PIXELS).asImageBitmap() }
                        ?: return@mapNotNull null
                    val stem = resolver.canonDisplayName(uri)?.substringBeforeLast('.')?.ifEmpty { null } ?: "image"
                    Triple(StitchSource(UUID.randomUUID(), decoded.image.width, decoded.image.height, stem), decoded.image, preview)
                }
            }
            loads--
            place(loaded, target)
        }
    }

    private fun place(added: List<Triple<StitchSource, Bitmap, ImageBitmap>>, target: StitchTarget) {
        if (added.isEmpty()) return
        sources = sources + added.associate { it.first.id to it.first }
        images = images + added.associate { it.first.id to it.second }
        previews = previews + added.associate { it.first.id to it.third }
        val ids = added.map { it.first.id }
        val leaves = ids.map { StitchNode.leaf(it) }
        val current = root
        when {
            target is StitchTarget.Batch -> batchIds = batchIds.toMutableList().apply { addAll(target.at.coerceIn(0, size), ids) }
            current == null -> {
                baseId = ids[0]
                root = if (leaves.size == 1) leaves[0] else StitchNode.Split(UUID.randomUUID(), StitchDir.Row, leaves)
            }
            target is StitchTarget.First -> root = current.wrapping(null, StitchSide.Right, leaves)
            target is StitchTarget.Add -> root = current.wrapping(target.node, target.side, leaves)
            target is StitchTarget.Insert -> root = current.inserting(leaves, target.split, target.at)
        }
        val now = root
        if (target !is StitchTarget.Batch && now != null && ids[0] !in now.leafSrcIds) root = now.wrapping(null, StitchSide.Right, leaves)
    }

    private fun forget(ids: Collection<UUID>) {
        sources = sources - ids.toSet()
        images = images - ids.toSet()
        previews = previews - ids.toSet()
    }

    fun select(key: UUID?) {
        selected = if (key == selected) null else key
    }

    fun move(moved: UUID, drop: StitchDrop) {
        val current = root ?: return
        when (drop) {
            is StitchDrop.Swap -> {
                root = current.swappingContent(moved, drop.key)
                if (selected == moved) selected = drop.key else if (selected == drop.key) selected = moved
            }
            is StitchDrop.Place -> root = current.movingLeaf(moved, drop.side, drop.key)
        }
    }

    fun fit(fit: StitchFit) {
        val key = selected ?: return
        root = root?.settingFit(fit, key)
    }

    fun setBase() {
        baseId = selectedLeaf?.srcId ?: return
    }

    fun removeSelected() {
        val key = selected ?: return
        val leaf = root?.findLeaf(key) ?: return
        root = root?.removingLeaf(key)
        selected = null
        forget(listOf(leaf.srcId))
        if (baseId == leaf.srcId) baseId = root?.leafSrcIds?.firstOrNull()
    }

    fun clear() {
        if (batch) {
            forget(batchIds)
            batchIds = emptyList()
        } else {
            root?.leafSrcIds?.let(::forget)
            root = null
            baseId = null
            selected = null
        }
    }

    fun sort(ascending: Boolean) {
        val names = batchIds.associateWith { sources[it]?.name.orEmpty() }
        val sorted = batchIds.sortedWith { a, b -> ImageStitcherEngine.naturalCompare(names.getValue(a), names.getValue(b)) }
        batchIds = if (ascending) sorted else sorted.reversed()
    }

    fun reorder(from: Int, to: Int) {
        if (from !in batchIds.indices || to !in batchIds.indices) return
        batchIds = batchIds.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun moveToEnd(id: UUID, start: Boolean) {
        val rest = batchIds - id
        batchIds = if (start) listOf(id) + rest else rest + id
    }

    fun removeFromBatch(id: UUID) {
        batchIds = batchIds - id
        forget(listOf(id))
    }

    private val stem: String
        get() = (if (batch) batchSources.firstOrNull()?.name else source(baseId)?.name)?.ifEmpty { null } ?: "image"

    private fun output(opaque: Boolean, encode: (Bitmap) -> ByteArray?, finish: suspend (ByteArray) -> Unit) {
        val layout = layout ?: return
        if (exporting) return
        val images = images
        exporting = true
        failed = false
        viewModelScope.launch {
            val data = withContext(Dispatchers.Default) {
                try {
                    render(layout, images, opaque)?.let { rendered -> encode(rendered).also { rendered.recycle() } }
                } catch (_: OutOfMemoryError) {
                    null
                }
            }
            exporting = false
            if (data == null) failed = true else finish(data)
        }
    }

    fun copy(context: Context) = output(false, ImageFiles::png) { ImageFiles.copyPng(context, it, "$stem-stitched.png") }

    fun export(context: Context) {
        val isPng = png == 0
        val name = "$stem-stitched.${if (isPng) "png" else "jpg"}"
        output(!isPng, { image ->
            if (isPng) ImageFiles.png(image)
            else ByteArrayOutputStream().use { out -> if (image.compress(Bitmap.CompressFormat.JPEG, 90, out)) out.toByteArray() else null }
        }) { FileExport.share(context, it, name, if (isPng) "image/png" else "image/jpeg") }
    }
}

private fun ClipData.imageUris(context: Context): List<Uri> =
    (0 until itemCount).mapNotNull { getItemAt(it).uri }.filter { context.contentResolver.getType(it)?.startsWith("image/") == true }

@Composable
fun ImageStitcher(model: ImageStitcherModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val layout = model.layout
    val individual = !model.batch
    val dash = "—"

    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        model.pending?.let { model.load(uris, it) }
        model.pending = null
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        model.pending?.let { model.load(uris, it) }
        model.pending = null
    }
    val add: (StitchTarget, AddSource) -> Unit = { target, source ->
        when (source) {
            AddSource.Photos -> {
                model.pending = target
                photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            AddSource.Files -> {
                model.pending = target
                files.launch(arrayOf("image/*"))
            }
            AddSource.Paste -> scope.launch {
                clipboard.getClipEntry()?.clipData?.imageUris(context)?.let { model.load(it, target) }
            }
        }
    }

    val aspect = layout?.let { (it.width / max(it.height, 1.0)).toFloat().coerceIn(0.75f, 1.5f) } ?: (4f / 3f)
    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(aspect), background = c.muted, showsPattern = true) {
        StitcherPicture(model, layout, add)
    }

    val modeRows = CanonSection.of {
        CanonRows {
            CanonHeading("Mode")
            CanonSegmented(listOf("Individual", "Batch"), if (model.batch) 1 else 0, onSelect = model::pickMode)
        }
    }

    val imagesRows = CanonSection.of {
        CanonRows {
            CanonHeading("Images")
            val drop = @Composable {
                CanonDropZone("Drop images here", multiple = true, onFiles = { model.load(it, model.defaultTarget) })
            }
            if (individual) {
                val base = model.source(model.baseId)
                if (model.root == null || base == null) drop()
                else CanonRow(base.name, detail = "Base · ${pixelSize(base.width, base.height)}", flush = true) {
                    CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = model::clear)
                }
            } else if (model.batchIds.isEmpty()) {
                drop()
            } else {
                val sources = model.batchSources
                val cell = sources.first()
                val pages = sources.map { source ->
                    val cover = ImageStitcherEngine.coverCrop(source, cell.width.toDouble() / max(cell.height, 1))
                    val scale = cell.width / max(cover.width, 1.0)
                    val w = source.width * scale
                    val h = source.height * scale
                    val top = -cover.y * scale
                    // pdf origin bottom left
                    val bottom = cell.height - (top + h)
                    CanonPageStripPage(
                        Size(cell.width.toFloat(), cell.height.toFloat()),
                        model.previews[source.id],
                        source.name,
                        placement = Rect((-cover.x * scale).toFloat(), bottom.toFloat(), (-cover.x * scale + w).toFloat(), (bottom + h).toFloat()),
                    )
                }
                CanonPageStrip(
                    pages,
                    Modifier.fillMaxWidth().height(CanonPageStripHeight).background(c.muted),
                    reorders = true,
                    onMove = model::reorder,
                    onDrop = { index, clip -> model.load(clip.imageUris(context), StitchTarget.Batch(index)) },
                    menu = { index ->
                        val id = model.batchIds.getOrNull(index) ?: return@CanonPageStrip emptyList()
                        listOf(
                            "Move to start" to { model.moveToEnd(id, start = true) },
                            "Move to end" to { model.moveToEnd(id, start = false) },
                            "Remove" to { model.removeFromBatch(id) },
                        )
                    },
                )
                drop()
                CanonGrid {
                    val cellModifier = Modifier.weight(1f).fillMaxHeight()
                    CanonButton("A–Z", cellModifier, icon = Icons.Rounded.ArrowDownward) { model.sort(true) }
                    CanonButton("Z–A", cellModifier, icon = Icons.Rounded.ArrowUpward) { model.sort(false) }
                    CanonButton("Clear", cellModifier, icon = Icons.Outlined.Delete, onClick = model::clear)
                }
            }
        }
    }

    val tileRows = CanonSection.of {
        CanonRows {
            CanonHeading("Tile")
            val leaf = model.selectedLeaf
            val source = model.source(leaf?.srcId)
            if (leaf == null || source == null) {
                CanonStatusRow("Tap a tile", CanonTone.Muted)
            } else {
                val isBase = leaf.srcId == model.baseId
                CanonRow(source.name, detail = pixelSize(source.width, source.height))
                CanonHeading("Fit")
                CanonSegmented(listOf("Match", "Cheese", "Matte"), leaf.fit.ordinal, enabled = !isBase) { model.fit(StitchFit.entries[it]) }
                CanonGrid {
                    val cellModifier = Modifier.weight(1f).fillMaxHeight()
                    CanonButton(
                        if (isBase) "Base" else "Make base",
                        cellModifier,
                        enabled = !isBase,
                        icon = if (isBase) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        onClick = model::setBase,
                    )
                    CanonButton("Remove", cellModifier, icon = Icons.Outlined.Delete, onClick = model::removeSelected)
                }
            }
        }
    }

    val outputRows = CanonSection.of {
        CanonRows {
            CanonHeading("Output")
            val size = layout?.let { ImageStitcherEngine.exportSize(it.width, it.height).let { (w, h) -> pixelSize(w, h) } } ?: dash
            if (individual) {
                CanonTiles(listOf(CanonTile("Size", size), CanonTile("Tiles", layout?.let { "${it.ops.size}" } ?: dash)))
            } else {
                val cell = layout?.ops?.firstOrNull()?.dest
                val grid = if (layout == null || cell == null) dash
                else "${(layout.width / cell.width).roundToInt()} × ${(layout.height / cell.height).roundToInt()}"
                CanonTiles(listOf(CanonTile("Size", size), CanonTile("Grid", grid), CanonTile("Cell", cell?.let { pixelSize(it.width, it.height) } ?: dash)))
            }
            CanonHeading("Format")
            CanonSegmented(listOf("PNG", "JPEG"), model.png) { model.png = it }
            CanonGrid {
                val ready = layout != null && !model.exporting
                CanonButton("Copy", Modifier.weight(1f).fillMaxHeight(), enabled = ready, icon = Icons.Outlined.ContentCopy, flashesCopied = true) { model.copy(context) }
                CanonButton(
                    "Export",
                    Modifier.weight(1f).fillMaxHeight(),
                    kind = CanonButtonKind.Primary,
                    enabled = ready,
                    icon = Icons.Outlined.Share,
                    oversized = true,
                ) { model.export(context) }
            }
            if (model.exporting) {
                CanonRow("Processing…") { CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground) }
            }
            if (model.failed) {
                CanonStatusRow(
                    "Too large to stitch on this device",
                    CanonTone.Warn,
                )
            }
        }
    }

    CanonSplit(
        sidebar = listOf(canvas),
        main = listOfNotNull(modeRows, imagesRows, tileRows.takeIf { individual && model.root != null }, outputRows),
        halves = true,
        collapsesTo = 240.dp,
    )
}

/**
 * ImageStitcherPicture: the stitched image to scale under its dimension lines. Individual: a tap selects a tile, a held tile
 * drags onto another to swap or to one of its edges to place, and plus buttons add images at the edges, the seams and the
 * selected tile. Batch: the grid with each cell's number and its empty cells dashed.
 * ponytail: no drop of images from other apps onto the picture (the drop zone and the strip take them)
 */
@Composable
private fun StitcherPicture(model: ImageStitcherModel, layout: StitchLayout?, onAdd: (StitchTarget, AddSource) -> Unit) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val top = LocalCanonCanvas.current.top
    val batch = model.batch
    val selected = if (batch) null else model.selected
    var dragKey by remember { mutableStateOf<UUID?>(null) }
    var dragDrop by remember { mutableStateOf<StitchDrop?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val unit = density.density.toDouble()
        val padding = with(density) { CanonMetrics.panelPadding.toPx() }.toDouble()
        val area = StitchRect(padding, with(density) { top.toPx() } + padding * 0.5, constraints.maxWidth - padding * 2, constraints.maxHeight - with(density) { top.toPx() } - padding * 1.5)
        val gutters = !batch && layout != null
        val composite = layout?.let { ImageStitcherEngine.frame(it.width, it.height, area, gutters, rightToLeft, unit) } ?: StitchRect.Zero
        val scale = layout?.let { composite.width / max(it.width, 1.0) } ?: 0.0
        fun shown(r: StitchRect) = StitchRect(composite.x + r.x * scale, composite.y + r.y * scale, r.width * scale, r.height * scale)
        val frames = layout?.ops?.map { it.leafKey to shown(it.dest) }.orEmpty()
        val frameState by rememberUpdatedState(frames)
        val idle = dragKey == null

        val muted = c.mutedForeground
        Canvas(Modifier.fillMaxSize()) {
            val dashes = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            fun dashed(r: Rect, ink: Color) = drawRect(ink, r.topLeft, r.size, style = Stroke(1.5.dp.toPx(), pathEffect = dashes))
            if (layout == null) {
                val inset = 12 * unit
                val dimension = ImageStitcherEngine.DIMENSION * unit
                val box = ImageStitcherEngine.frame(4.0, 3.0, StitchRect(area.x + inset, area.y + inset, area.width - inset * 2, area.height - inset * 2), false, false, unit)
                    .let { StitchRect(it.x - dimension / 2, it.y - dimension / 2, it.width, it.height) }
                val shares = if (batch) (0 until 9).map { StitchRect((it % 3) / 3.0, (it / 3) / 3.0, 1.0 / 3, 1.0 / 3) }
                else listOf(StitchRect(0.0, 0.0, 0.58, 0.62), StitchRect(0.58, 0.0, 0.42, 0.62), StitchRect(0.0, 0.62, 1.0, 0.38))
                for (share in shares) {
                    val cell = Rect(
                        (box.x + share.x * box.width).toFloat(), (box.y + share.y * box.height).toFloat(),
                        (box.x + share.maxX * box.width).toFloat(), (box.y + share.maxY * box.height).toFloat(),
                    ).deflate(4.dp.toPx())
                    dashed(cell, muted.copy(alpha = 0.45f))
                }
            } else if (batch) {
                val cell = layout.ops.firstOrNull()?.dest
                if (cell != null && cell.width > 0 && cell.height > 0) {
                    val columns = (layout.width / cell.width).roundToInt()
                    val rows = (layout.height / cell.height).roundToInt()
                    for (index in layout.ops.size until max(layout.ops.size, columns * rows)) {
                        val blank = shown(StitchRect((index % columns) * cell.width, (index / columns) * cell.height, cell.width, cell.height))
                        dashed(blank.compose().deflate(4.dp.toPx()), muted.copy(alpha = 0.6f))
                    }
                }
            }
        }

        layout?.ops?.forEachIndexed { index, op ->
            androidx.compose.runtime.key(op.src.id) {
                StitchTile(
                    op,
                    model.previews[op.src.id],
                    shown(op.dest).compose(),
                    number = if (batch) index + 1 else null,
                    isBase = !batch && op.isBase && layout.ops.size > 1,
                    lit = !batch && op.leafKey == selected,
                    lifted = op.leafKey == dragKey,
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(batch, layout == null) {
                    detectTapGestures { at ->
                        if (batch || layout == null) return@detectTapGestures
                        model.select(frameState.firstOrNull { it.second.contains(at.x.toDouble(), at.y.toDouble()) }?.first)
                    }
                }
                .pointerInput(batch) {
                    if (batch) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { at ->
                            dragKey = frameState.firstOrNull { it.second.contains(at.x.toDouble(), at.y.toDouble()) }?.first
                            pointer = at
                            dragDrop = null
                        },
                        onDrag = { change, _ ->
                            val key = dragKey ?: return@detectDragGesturesAfterLongPress
                            change.consume()
                            pointer = change.position
                            dragDrop = ImageStitcherEngine.drop(pointer.x.toDouble(), pointer.y.toDouble(), frameState, key, unit)
                        },
                        onDragEnd = {
                            val key = dragKey
                            val drop = dragDrop
                            dragKey = null
                            dragDrop = null
                            if (key != null && drop != null) model.move(key, drop)
                        },
                        onDragCancel = {
                            dragKey = null
                            dragDrop = null
                        },
                    )
                },
        )

        with(density) {
            val gap = ImageStitcherEngine.GAP * unit
            val gutter = ImageStitcherEngine.GUTTER * unit
            if (gutters && selected == null && idle && composite.width > 0) {
                val edges = listOf(
                    StitchRect(composite.x, composite.y - gap - gutter, composite.width, gutter),
                    StitchRect(composite.x - gap - gutter, composite.y, gutter, composite.height),
                    StitchRect(composite.maxX + gap, composite.y, gutter, composite.height),
                    StitchRect(composite.x, composite.maxY + gap, composite.width, gutter),
                )
                StitchSide.entries.zip(edges).forEach { (side, edge) ->
                    AddButton(AddStyle.Gutter, edge.compose(), StitchTarget.Add(null, side), onAdd)
                }
            }
            if (!batch && selected == null && idle && layout != null) {
                val side = 26 * unit
                val lines = layout.seams.map { seam ->
                    seam.id to shown(StitchRect(seam.x, seam.y, if (seam.vertical) 0.0 else seam.length, if (seam.vertical) seam.length else 0.0))
                }
                val byId = layout.seams.associateBy { it.id }
                for ((id, centre) in ImageStitcherEngine.seamCentres(lines, side)) {
                    val seam = byId.getValue(id)
                    val rect = Rect(Offset((centre.first - side / 2).toFloat(), (centre.second - side / 2).toFloat()), Size(side.toFloat(), side.toFloat()))
                    AddButton(AddStyle.Plain, rect, StitchTarget.Insert(seam.splitKey, seam.at), onAdd)
                }
            }
            val lit = frames.firstOrNull { it.first == selected }?.second
            if (lit != null && idle) {
                val roomy = ImageStitcherEngine.ROOMY * unit
                val across = if (lit.width >= roomy) 18 * unit else -16 * unit
                val down = if (lit.height >= roomy) 18 * unit else -16 * unit
                val centres = listOf(
                    lit.midX to lit.y + down, lit.x + across to lit.midY,
                    lit.maxX - across to lit.midY, lit.midX to lit.maxY - down,
                )
                val side = 28 * unit
                StitchSide.entries.zip(centres).forEach { (edge, centre) ->
                    val rect = Rect(Offset((centre.first - side / 2).toFloat(), (centre.second - side / 2).toFloat()), Size(side.toFloat(), side.toFloat()))
                    AddButton(AddStyle.Filled, rect, StitchTarget.Add(selected, edge), onAdd)
                }
            }

            if (layout != null && composite.width > 0) {
                val (w, h) = ImageStitcherEngine.exportSize(layout.width, layout.height)
                val lead = if (gutters) gutter + gap else 0.0
                val (acrossLine, downLine) = canonDimensionLines(composite.compose(), rightToLeft, gap = ((lead + 2 * unit) / unit).toFloat().dp)
                CanonDimensionLine(w, Modifier.canonPlaced(acrossLine))
                CanonDimensionLine(h, Modifier.canonPlaced(downLine))
            }

            val drop = dragDrop
            if (drop != null) {
                val (zone, edge, symbol) = when (drop) {
                    is StitchDrop.Swap -> Triple(frames.firstOrNull { it.first == drop.key }?.second, null, Icons.Rounded.SwapHoriz)
                    is StitchDrop.Place -> Triple(
                        frames.firstOrNull { it.first == drop.key }?.second?.let { ImageStitcherEngine.zone(drop.side, it, unit) },
                        drop.side,
                        when (drop.side) {
                            StitchSide.Top -> Icons.Rounded.ArrowUpward
                            StitchSide.Left -> Icons.AutoMirrored.Rounded.ArrowBack
                            StitchSide.Right -> Icons.AutoMirrored.Rounded.ArrowForward
                            StitchSide.Bottom -> Icons.Rounded.ArrowDownward
                        },
                    )
                }
                if (zone != null) DropMark(zone.compose(), edge, symbol)
            }

            val held = dragKey?.let { key -> layout?.ops?.firstOrNull { it.leafKey == key } }
            if (held != null) {
                val rect = shown(held.dest)
                val longest = max(max(rect.width, rect.height), 1.0)
                val ghost = ImageStitcherEngine.GHOST * unit
                val size = Size((rect.width / longest * ghost).toFloat(), (rect.height / longest * ghost).toFloat())
                val centre = Offset(pointer.x, (pointer.y - size.height / 2 - ImageStitcherEngine.LIFT * unit).toFloat())
                Box(
                    Modifier
                        .canonPlaced(CanonDimensionPlacement(centre, size))
                        .graphicsLayer {
                            rotationZ = 4f
                            scaleX = 1.06f
                            scaleY = 1.06f
                        }
                        .shadow(10.dp)
                        .background(if (held.fit == StitchFit.Matte) matteColour else Color.Transparent)
                        .border(CanonMetrics.frame, c.primary),
                ) {
                    model.previews[held.src.id]?.let { picture ->
                        Canvas(Modifier.fillMaxSize().clipToBounds()) { drawFitted(picture, held.fit == StitchFit.Matte) }
                    }
                }
            }

            if (model.loads > 0) {
                val side = 40.dp.toPx()
                Box(
                    Modifier.canonPlaced(CanonDimensionPlacement(Offset((area.maxX - side / 2).toFloat(), (area.maxY - side / 2).toFloat()), Size(side, side))),
                    contentAlignment = Alignment.Center,
                ) { CanonLoader() }
            }
        }
    }
}

private fun DrawScope.drawFitted(picture: ImageBitmap, fits: Boolean) {
    val scale = if (fits) min(size.width / picture.width, size.height / picture.height) else max(size.width / picture.width, size.height / picture.height)
    val w = picture.width * scale
    val h = picture.height * scale
    drawImage(
        picture,
        dstOffset = IntOffset(((size.width - w) / 2).roundToInt(), ((size.height - h) / 2).roundToInt()),
        dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
    )
}

@Composable
private fun StitchTile(op: StitchOp, picture: ImageBitmap?, target: Rect, number: Int?, isBase: Boolean, lit: Boolean, lifted: Boolean) {
    val c = LocalDelphiColours.current
    val rect by animateRectAsState(target, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow), label = "tile")
    Box(
        Modifier
            .canonPlaced(CanonDimensionPlacement(rect.center, rect.size))
            .graphicsLayer { alpha = if (lifted) 0.35f else 1f }
            .clipToBounds()
            .background(if (op.fit == StitchFit.Matte) matteColour else Color.Transparent)
            .semantics { contentDescription = op.src.name },
    ) {
        if (picture != null) {
            Canvas(Modifier.fillMaxSize()) {
                val frame = ImageStitcherEngine.imageFrame(op.fit, op.source, op.src.width.toDouble(), op.src.height.toDouble(), size.width.toDouble(), size.height.toDouble())
                drawImage(
                    picture,
                    dstOffset = IntOffset(frame.x.roundToInt(), frame.y.roundToInt()),
                    dstSize = IntSize(frame.width.roundToInt().coerceAtLeast(1), frame.height.roundToInt().coerceAtLeast(1)),
                )
            }
        }
        if (lit) {
            Box(Modifier.fillMaxSize().border(CanonMetrics.frame, c.primary))
            Box(Modifier.fillMaxSize().padding(CanonMetrics.frame).border(CanonMetrics.hairline, c.background))
        }
        val marked = number != null || isBase
        val plateWidth = if (number != null) max(18, "$number".length * 7 + 8).dp else 18.dp
        val plateHeight = if (number != null) 16.dp else 18.dp
        val room = with(LocalDensity.current) { rect.width.toDp() >= plateWidth + 12.dp && rect.height.toDp() >= plateHeight + 12.dp }
        if (marked && room) {
            Box(
                Modifier
                    .padding(3.dp)
                    .size(plateWidth, plateHeight)
                    .background(c.card.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center,
            ) {
                if (number != null) {
                    Text("$number", style = TextStyle(fontFamily = DelphiMono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold), color = c.foreground, maxLines = 1)
                } else {
                    Icon(Icons.Rounded.PushPin, contentDescription = null, tint = c.foreground, modifier = Modifier.size(10.dp))
                }
            }
        }
    }
}

private enum class AddStyle { Gutter, Plain, Filled }

@Composable
private fun AddButton(style: AddStyle, rect: Rect, target: StitchTarget, onAdd: (StitchTarget, AddSource) -> Unit) {
    val c = LocalDelphiColours.current
    val clipboard = LocalContext.current.getSystemService(android.content.ClipboardManager::class.java)
    var open by remember { mutableStateOf(false) }
    val shape = if (style == AddStyle.Gutter) androidx.compose.ui.graphics.RectangleShape else CircleShape
    Box(
        Modifier
            .canonPlaced(CanonDimensionPlacement(rect.center, rect.size))
            .clip(shape)
            .then(
                when (style) {
                    AddStyle.Gutter -> Modifier
                        .background(c.card.copy(alpha = 0.5f))
                        .drawBehind {
                            val inset = 0.75.dp.toPx()
                            drawRect(
                                c.border,
                                Offset(inset, inset),
                                Size(size.width - inset * 2, size.height - inset * 2),
                                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                            )
                        }
                    AddStyle.Plain -> Modifier.background(c.card).border(CanonMetrics.hairline, c.primary, shape)
                    AddStyle.Filled -> Modifier.background(c.primary).border(CanonMetrics.hairline, c.background, shape)
                },
            )
            .canonClickable { open = true }
            .semantics { contentDescription = "Add" },
        contentAlignment = Alignment.Center,
    ) {
        val tint = when (style) {
            AddStyle.Gutter -> c.mutedForeground
            AddStyle.Plain -> c.primary
            AddStyle.Filled -> c.primaryForeground
        }
        Icon(Icons.Rounded.Add, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        MenuCellPopup(open, { open = false }) {
            val items: List<Triple<String, ImageVector, AddSource>> = listOf(
                Triple("Photos", Icons.Outlined.PhotoLibrary, AddSource.Photos),
                Triple("Files", Icons.Outlined.Folder, AddSource.Files),
                Triple("Paste", Icons.Outlined.ContentPaste, AddSource.Paste),
            )
            for ((title, icon, source) in items) {
                // ponytail: hidden, not greyed, with no image on the clipboard
                if (source == AddSource.Paste && clipboard?.primaryClipDescription?.hasMimeType("image/*") != true) continue
                MenuCellItem(title, icon = icon) {
                    open = false
                    onAdd(target, source)
                }
            }
        }
    }
}

@Composable
private fun DropMark(zone: Rect, edge: StitchSide?, symbol: ImageVector) {
    val c = LocalDelphiColours.current
    Box(
        Modifier
            .canonPlaced(CanonDimensionPlacement(zone.center, zone.size))
            .background(c.primary.copy(alpha = 0.24f))
            .drawBehind {
                val thick = 3.dp.toPx()
                when (edge) {
                    StitchSide.Top -> drawRect(c.primary, Offset.Zero, Size(size.width, thick))
                    StitchSide.Left -> drawRect(c.primary, Offset.Zero, Size(thick, size.height))
                    StitchSide.Right -> drawRect(c.primary, Offset(size.width - thick, 0f), Size(thick, size.height))
                    StitchSide.Bottom -> drawRect(c.primary, Offset(0f, size.height - thick), Size(size.width, thick))
                    null -> {}
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val side = with(LocalDensity.current) { min(24.dp.toPx(), min(zone.width, zone.height) - 2).toDp() }
        if (side >= 12.dp) {
            Box(Modifier.size(side).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                Icon(symbol, contentDescription = null, tint = c.primaryForeground, modifier = Modifier.size(side * 0.6f))
            }
        }
    }
}
