package com.delphi.delphitools.tools.images

import android.animation.ValueAnimator
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDimensionPlacement
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.MenuCellItem
import com.delphi.delphitools.canon.MenuCellPopup
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.canon.canonPlaced
import com.delphi.delphitools.tools.images.ImageSplitEngine.Cell
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val counts = 1..20

/** Longest edge decoded; larger photos split at this size. */
private const val MAX_PIXELS = 4096
private const val PREVIEW_PIXELS = 1600
private const val TYPING_MS = 400L

// widest seam between tiles, and the least tile width that still carries its label
private val seamWidth = 8.dp
private val labelledWidth = 44.dp
private val labelledHeight = 28.dp

private class SplitSource(val image: Bitmap, val preview: ImageBitmap, val name: String) {
    val stem get() = name.substringBeforeLast('.').ifEmpty { "image" }
}

/** dt-ios ImageSplitterViewController: one image cut into a grid of tiles, shared all at once or one by one. */
class ImageSplitterModel(application: Application) : AndroidViewModel(application) {
    private var source by mutableStateOf<SplitSource?>(null)
    val preview get() = source?.preview
    val name get() = source?.name ?: ""
    val pixels get() = source?.let { IntSize(it.image.width, it.image.height) }

    var columns by mutableStateOf(3)
        private set
    var rows by mutableStateOf(3)
        private set

    /** The grid the picture and the export use; typing settles first. */
    var grid by mutableStateOf(IntSize(3, 3))
        private set
    var exporting by mutableStateOf(false)
        private set

    private var loading: Job? = null
    private var typing: Job? = null

    fun load(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        loading?.cancel()
        loading = viewModelScope.launch {
            source = withContext(Dispatchers.IO) {
                val decoded = runCatching { ImageFiles.decode(ImageDecoder.createSource(resolver, uri), MAX_PIXELS) }.getOrNull()
                    ?: return@withContext null
                val preview = ImageCompressEngine.preview(decoded.image, PREVIEW_PIXELS).asImageBitmap()
                SplitSource(decoded.image, preview, resolver.canonDisplayName(uri) ?: "")
            }
        }
    }

    fun clear() {
        loading?.cancel()
        source = null
    }

    private fun refresh(typed: Boolean) {
        typing?.cancel()
        if (!typed) {
            grid = IntSize(columns, rows)
            return
        }
        typing = viewModelScope.launch {
            delay(TYPING_MS)
            grid = IntSize(columns, rows)
        }
    }

    fun changeColumns(value: Int, typed: Boolean) {
        columns = value
        refresh(typed)
    }

    fun changeRows(value: Int, typed: Boolean) {
        rows = value
        refresh(typed)
    }

    private fun crop(source: SplitSource, cell: Cell, grid: IntSize): Bitmap? {
        val r = ImageSplitEngine.rect(cell, source.image.width, source.image.height, grid.height, grid.width)
        if (r.width <= 0 || r.height <= 0) return null
        return Bitmap.createBitmap(source.image, r.left, r.top, r.width, r.height)
    }

    fun export(context: Context, cells: List<Cell> = ImageSplitEngine.cells(grid.height, grid.width)) {
        val source = source ?: return
        if (exporting || cells.isEmpty()) return
        val grid = grid
        exporting = true
        viewModelScope.launch {
            val files = withContext(Dispatchers.Default) {
                cells.map { cell ->
                    val data = crop(source, cell, grid)?.let(ImageFiles::png) ?: return@withContext null
                    "${source.stem}-${cell.label}.png" to data
                }
            }
            exporting = false
            if (files != null) ImageFiles.shareAll(context, files, "image/png")
        }
    }

    fun copy(context: Context, cell: Cell) {
        val source = source ?: return
        val grid = grid
        viewModelScope.launch {
            val data = withContext(Dispatchers.Default) { crop(source, cell, grid)?.let(ImageFiles::png) } ?: return@launch
            ImageFiles.copyPng(context, data, "${source.stem}-${cell.label}.png")
        }
    }
}

@Composable
fun ImageSplitter(model: ImageSplitterModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val grid = model.grid
    val pixels = model.pixels
    val dash = "—"

    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(1.25f), background = c.muted, showsPattern = true) {
        SplitterPicture(
            model.preview,
            pixels,
            grid.height,
            grid.width,
            onShare = { model.export(context, listOf(it)) },
            onCopy = { model.copy(context, it) },
        )
    }

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            // ponytail: no share-sheet import (iOS acceptShared); add an ACTION_SEND intent filter when wanted
            if (pixels == null) {
                CanonDropZone("Drop an image here", paste = false, onFiles = { uris -> uris.firstOrNull()?.let(model::load) })
            } else {
                CanonRow(model.name, detail = "${pixels.width} × ${pixels.height}", flush = true) {
                    CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = model::clear)
                }
            }
        }
    }

    val gridRows = CanonSection.of {
        CanonRows {
            var columnsTyped by remember { mutableStateOf(false) }
            var rowsTyped by remember { mutableStateOf(false) }
            CanonHeading("Grid")
            CanonRow("Columns", flush = true) {
                CanonNumberField(
                    model.columns,
                    { model.changeColumns(it, columnsTyped) },
                    Modifier.onFocusChanged { columnsTyped = it.hasFocus },
                    range = counts,
                )
            }
            CanonRow("Rows", flush = true) {
                CanonNumberField(
                    model.rows,
                    { model.changeRows(it, rowsTyped) },
                    Modifier.onFocusChanged { rowsTyped = it.hasFocus },
                    range = counts,
                )
            }
            CanonHeading("Tiles")
            var size = dash
            var trimmed = dash
            var cuttable = false
            if (pixels != null) {
                val tile = ImageSplitEngine.tileSize(pixels.width, pixels.height, grid.height, grid.width)
                val leftWidth = pixels.width - tile.width * grid.width
                val leftHeight = pixels.height - tile.height * grid.height
                size = "${tile.width} × ${tile.height} px"
                cuttable = tile.width > 0 && tile.height > 0
                trimmed = if (leftWidth == 0 && leftHeight == 0) "0 px" else "$leftWidth × $leftHeight px"
            }
            CanonTiles(
                listOf(
                    CanonTile("Tiles", "${grid.width * grid.height}"),
                    CanonTile("Size", size),
                    CanonTile("Trimmed", trimmed),
                )
            )
            CanonButton(
                "Export All",
                kind = CanonButtonKind.Primary,
                enabled = cuttable && !model.exporting,
                icon = Icons.Outlined.Share,
                oversized = true,
            ) { model.export(context) }
            if (model.exporting) {
                CanonRow("Processing…") { CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground) }
            }
        }
    }

    CanonSplit(sidebar = listOf(canvas), main = listOf(input, gridRows), halves = true, collapsesTo = 200.dp)
}

/** The image cut into its tiles and pulled apart at the seams, each tile a button with Share and Copy. */
@Composable
private fun SplitterPicture(
    preview: ImageBitmap?,
    pixels: IntSize?,
    rows: Int,
    columns: Int,
    onShare: (Cell) -> Unit,
    onCopy: (Cell) -> Unit,
) {
    val density = LocalDensity.current
    val top = LocalCanonCanvas.current.top
    val shape = pixels ?: IntSize(4, 3)
    // seams are closed while a new cut is laid out, then spring open
    val open = remember { Animatable(1f) }
    var cut by remember { mutableStateOf(Triple(preview, rows, columns)) }
    LaunchedEffect(preview, rows, columns) {
        val next = Triple(preview, rows, columns)
        if (next == cut) return@LaunchedEffect
        cut = next
        if (preview == null || !ValueAnimator.areAnimatorsEnabled()) return@LaunchedEffect
        open.snapTo(0f)
        delay(50)
        open.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 110f))
    }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        with(density) {
            val padding = CanonMetrics.panelPadding.toPx()
            val area = Rect(padding, top.toPx() + padding, constraints.maxWidth - padding, constraints.maxHeight - padding)
            val size = ImageSplitEngine.tileSize(shape.width, shape.height, rows, columns)
            val tile = Size(max(1, size.width).toFloat(), max(1, size.height).toFloat())
            // many tiles: thinner seams
            val seam = min(seamWidth.toPx(), min(area.width, area.height) / (max(rows, columns) * 6)) * open.value
            val frames = ImageSplitEngine.frames(rows, columns, tile, seam, area)
            // images never mirror
            ImageSplitEngine.cells(rows, columns).zip(frames).forEach { (cell, frame) ->
                SplitTile(
                    cell,
                    preview?.let { ImageSplitEngine.rect(cell, shape.width, shape.height, rows, columns) },
                    shape,
                    preview,
                    frame.width >= labelledWidth.toPx() && frame.height >= labelledHeight.toPx(),
                    Modifier.canonPlaced(CanonDimensionPlacement(frame.center, frame.size)),
                    onShare,
                    onCopy,
                )
            }
        }
    }
}

@Composable
private fun SplitTile(
    cell: Cell,
    crop: IntRect?,
    pixels: IntSize,
    preview: ImageBitmap?,
    showsLabel: Boolean,
    modifier: Modifier,
    onShare: (Cell) -> Unit,
    onCopy: (Cell) -> Unit,
) {
    val c = LocalDelphiColours.current
    var menu by remember { mutableStateOf(false) }
    val interactive = preview != null && crop != null
    Box(
        modifier
            .background(c.card)
            .border(CanonMetrics.hairline, c.border)
            .then(if (interactive) Modifier.canonClickable { menu = true } else Modifier)
            .semantics { contentDescription = cell.label },
    ) {
        if (interactive) {
            Canvas(Modifier.fillMaxSize()) {
                val sx = preview.width.toFloat() / pixels.width
                val sy = preview.height.toFloat() / pixels.height
                drawImage(
                    preview,
                    srcOffset = IntOffset((crop.left * sx).roundToInt(), (crop.top * sy).roundToInt()),
                    srcSize = IntSize(max(1, (crop.width * sx).roundToInt()), max(1, (crop.height * sy).roundToInt())),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                )
            }
        }
        if (showsLabel) {
            Text(
                cell.label,
                style = TextStyle(fontFamily = DelphiMono, fontSize = 10.sp),
                color = c.foreground,
                maxLines = 1,
                modifier = Modifier
                    .padding(3.dp)
                    .background(c.card.copy(alpha = 0.85f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        MenuCellPopup(menu, { menu = false }) {
            CanonHeading(cell.label)
            MenuCellItem("Share", icon = Icons.Outlined.Share) {
                menu = false
                onShare(cell)
            }
            MenuCellItem("Copy", icon = Icons.Rounded.ContentCopy) {
                menu = false
                onCopy(cell)
            }
        }
    }
}
