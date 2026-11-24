package com.delphi.delphitools.tools.colour

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.scale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonBar
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourStrip
import com.delphi.delphitools.canon.CanonCopyFeedback
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonImageCompare
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.MenuCellItem
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.canon.colourNamesOrNull
import com.delphi.delphitools.canon.toCanonColor
import com.delphi.delphitools.colour.LocalColourFormats
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.formatted
import com.delphi.delphitools.colour.rememberUserPalettes
import com.delphi.delphitools.colour.takesBlackInk
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.tools.colour.PaletteExtractorEngine.Strategy
import com.delphi.delphitools.tools.images.ImageFiles
import com.delphi.delphitools.tools.images.argb
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

// ImageProcessingService.normalizeConstrained
private const val IMAGE_PIXELS = 3072
private const val PAINT_PIXELS = 720

// ponytail: iOS exports up to 8192 px a side; 4096 keeps the decode and its pixel array under ~140 MB
private const val EXPORT_PIXELS = 4096

private class Pixels(val argb: IntArray, val width: Int, val height: Int)

private fun Bitmap.fitted(edge: Int): Bitmap {
    val scale = edge.toFloat() / maxOf(width, height)
    return if (scale >= 1f) this else scale(maxOf(1, (width * scale).roundToInt()), maxOf(1, (height * scale).roundToInt()))
}

/** dt-ios PaletteExtractorViewModel and the controller's painting and export. */
class PaletteExtractorModel(application: Application) : AndroidViewModel(application) {
    var preview by mutableStateOf<ImageBitmap?>(null)
        private set
    var imageSize by mutableStateOf(IntSize.Zero)
        private set
    var clusters by mutableStateOf(emptyList<PaletteCluster>())
        private set
    var extracting by mutableStateOf(false)
        private set
    var strategy by mutableStateOf(Strategy.Dominant)
        private set
    var count by mutableIntStateOf(5)
        private set
    var painted by mutableStateOf<ImageBitmap?>(null)
        private set
    var exporting by mutableStateOf(false)
        private set

    val palette get() = PaletteExtractorEngine.rank(clusters, strategy, count)

    private var image: Bitmap? = null
    private var uri: Uri? = null
    private var stem = "image"
    private var paintSource: Pixels? = null
    private var paintedColours = emptyList<Rgb>()
    private var loading: Job? = null
    private var extraction: Job? = null
    private var painting: Job? = null
    private var export: Job? = null

    fun load(uri: Uri?) {
        loading?.cancel()
        extraction?.cancel()
        painting?.cancel()
        export?.cancel()
        exporting = false
        image = null
        this.uri = null
        preview = null
        clusters = emptyList()
        extracting = false
        painted = null
        paintSource = null
        paintedColours = emptyList()
        if (uri == null) return
        val resolver = getApplication<Application>().contentResolver
        loading = viewModelScope.launch {
            val decoded = withContext(Dispatchers.IO) { ImageFiles.decode(ImageDecoder.createSource(resolver, uri), IMAGE_PIXELS) } ?: return@launch
            image = decoded.image
            this@PaletteExtractorModel.uri = uri
            stem = resolver.canonDisplayName(uri)?.substringBeforeLast('.')?.ifEmpty { null } ?: "image"
            imageSize = IntSize(decoded.image.width, decoded.image.height)
            preview = decoded.image.asImageBitmap()
            extract()
        }
    }

    fun extract() {
        val image = image ?: return
        extraction?.cancel()
        extracting = true
        extraction = viewModelScope.launch {
            val found = withContext(Dispatchers.Default) {
                val pixels = PaletteExtractorEngine.oklabPixels(image.fitted(PaletteExtractorEngine.SAMPLE_EDGE).argb())
                PaletteExtractorEngine.cluster(pixels, PaletteExtractorEngine.MAX_CLUSTERS)
            }
            clusters = found
            extracting = false
            repaint()
        }
    }

    fun select(strategy: Strategy) {
        this.strategy = strategy
        repaint()
    }

    fun resize(count: Int) {
        this.count = count
        repaint()
    }

    private fun repaint() {
        val image = image ?: return
        val colours = palette.map { it.colour }
        if (colours == paintedColours && painted != null) return
        paintedColours = colours
        painting?.cancel()
        if (colours.isEmpty()) {
            painted = null
            return
        }
        val kept = paintSource
        painting = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                val source = kept ?: image.fitted(PAINT_PIXELS).let { Pixels(it.argb(), it.width, it.height) }
                val out = source.argb.copyOf()
                if (!PalettePainter.paint(out, source.width, colours) { !isActive }) return@withContext null
                source to Bitmap.createBitmap(out, source.width, source.height, Bitmap.Config.ARGB_8888)
            } ?: return@launch
            if (this@PaletteExtractorModel.image !== image) return@launch
            paintSource = result.first
            painted = result.second.asImageBitmap()
        }
    }

    fun exportPalettised(context: Context) {
        val uri = uri ?: return
        val colours = palette.map { it.colour }
        if (export?.isActive == true || colours.isEmpty()) return
        val name = "$stem-palette.png"
        val resolver = context.contentResolver
        exporting = true
        export = viewModelScope.launch {
            val data = withContext(Dispatchers.Default) {
                val full = withContext(Dispatchers.IO) { ImageFiles.decode(ImageDecoder.createSource(resolver, uri), EXPORT_PIXELS) }?.image
                    ?: return@withContext null
                val (width, height) = full.width to full.height
                ImageFiles.unlessOutOfMemory {
                    val pixels = full.argb()
                    full.recycle()
                    if (!PalettePainter.paint(pixels, width, colours) { !isActive }) return@withContext null
                    ensureActive()
                    ImageFiles.png(Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888))
                }
            }
            export = null
            exporting = false
            if (data != null) FileExport.share(context, data, name, "image/png")
        }
    }
}

@Composable
fun PaletteExtractor(model: PaletteExtractorModel = viewModel()) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val context = LocalContext.current
    val store = rememberUserPalettes()
    val preview = model.preview
    val loaded = preview != null
    val palette = model.palette
    val colours = palette.map { it.colour }
    val shares = remember(model.clusters, palette) { PalettePainter.coverage(model.clusters, palette) }
    val size = model.imageSize
    val aspect = if (loaded) (size.width.toFloat() / maxOf(size.height, 1)).coerceIn(0.8f, 1.6f) else 4f / 3

    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(aspect), background = c.muted, maxHeight = 640.dp) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(top = LocalCanonCanvas.current.top), contentAlignment = Alignment.Center) {
                if (!loaded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Palette, null, tint = c.mutedForeground, modifier = Modifier.size(34.dp))
                        Text("Image", style = CanonText.bodySemibold.copy(fontSize = 17.sp), color = c.mutedForeground)
                    }
                } else {
                    CanonImageCompare(preview, model.painted, Modifier.fillMaxSize(), beforeTitle = "Original", afterTitle = "Palette")
                    if (model.extracting && model.clusters.isEmpty()) CanonLoader()
                }
            }
            if (colours.isNotEmpty()) CanonColourStrip(colours[0], palette = colours)
        }
    }

    val source = CanonSection.of {
        CanonRows {
            if (!loaded) {
                CanonDropZone("Drop an image", onFiles = { uris -> model.load(uris.firstOrNull()) })
            } else {
                CanonRow("Image", detail = "${size.width} × ${size.height}", flush = true) {
                    CanonButton("Clear", icon = Icons.Outlined.Delete) { model.load(null) }
                }
            }
        }
    }

    val strategies = Strategy.entries
    val controls = CanonSection.of {
        CanonRows {
            CanonHeading("Strategy")
            CanonSegmented(strategies.map { it.title }, model.strategy.ordinal, columns = 4) {
                if (strategies[it] != model.strategy) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                model.select(strategies[it])
            }
            CanonStatusRow(model.strategy.detail, CanonTone.Muted)
            CanonRow("Colours", flush = true) {
                CanonNumberField(model.count, model::resize, range = PaletteExtractorEngine.MIN_COLOURS..PaletteExtractorEngine.MAX_COLOURS)
            }
        }
    }

    val result = CanonSection.of {
        CanonRows {
            CanonHeading("Palette")
            PaletteShareBand(colours, shares, Modifier.alpha(if (model.extracting) 0.5f else 1f))
            colours.forEachIndexed { index, colour -> PaletteShareRow(colour, shares.getOrElse(index) { 0.0 }) }
            CanonGrid {
                val cell = Modifier.weight(1f).fillMaxHeight()
                CanonButton("Shuffle", cell, enabled = !model.extracting, icon = Icons.Rounded.Shuffle) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    model.extract()
                }
                CanonButton("Save", cell, icon = Icons.Outlined.FavoriteBorder, flashesCopied = true, flashTitle = "Saved") {
                    store.add(model.strategy.title, model.palette.map { it.colour })
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
                PaletteExportButton(
                    { model.strategy.title },
                    { if (model.exporting) emptyList() else model.palette.map { it.colour } },
                    cell,
                    extra = { close ->
                        MenuCellItem("Palettised Image", icon = Icons.Outlined.PhotoLibrary) {
                            close()
                            model.exportPalettised(context)
                        }
                    },
                )
            }
            if (model.exporting) {
                CanonRow("Processing…") { CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground) }
            }
        }
    }

    CanonSplit(
        sidebar = listOf(canvas),
        main = listOf(source) + (if (loaded) listOf(controls) else emptyList()) + (if (colours.isNotEmpty()) listOf(result) else emptyList()),
        halves = true,
        collapsesTo = 240.dp,
    )
}

private fun percent(share: Double) = if (share > 0 && share < 0.01) "<1%" else String.format(Locale.ROOT, "%.0f%%", share * 100)

private val shareMono = CanonText.caption.copy(fontFamily = DelphiMono, fontSize = 12.sp)

// dt-ios PaletteShareBand
@Composable
private fun PaletteShareBand(colours: List<Rgb>, shares: List<Double>, modifier: Modifier) {
    val view = LocalView.current
    val format = LocalColourFormats.current.primary
    val names = colourNamesOrNull()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val summary = colours.zip(shares).joinToString(", ") { (colour, share) -> "${names?.closest(colour) ?: ""} ${percent(share)}".trim() }
    Row(
        modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(LocalDelphiColours.current.border)
            .semantics { stateDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        colours.forEachIndexed { index, colour ->
            val weight by animateFloatAsState(maxOf(shares.getOrElse(index) { 0.0 }, 0.012).toFloat(), spring(dampingRatio = 0.78f, stiffness = 200f))
            val label = percent(shares.getOrElse(index) { 0.0 })
            val ink = if (colour.takesBlackInk) Color.Black else Color.White
            BoxWithConstraints(
                Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .background(colour.toCanonColor())
                    .canonClickable { CanonCopyFeedback.copy(view, colour.formatted(format)) },
                contentAlignment = Alignment.BottomStart,
            ) {
                val textWidth = with(density) { measurer.measure(label, shareMono).size.width.toDp() }
                if (textWidth + 16.dp <= maxWidth) Text(label, Modifier.padding(8.dp), style = shareMono, color = ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PaletteShareRow(colour: Rgb, share: Double) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val name = colourNamesOrNull()?.closest(colour).orEmpty()
    val value = colour.formatted(LocalColourFormats.current.primary)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = CanonMetrics.rowHeight)
            .canonClickable { CanonCopyFeedback.copy(view, value) }
            .semantics(mergeDescendants = true) {
                contentDescription = name
                stateDescription = "$value, ${percent(share)}"
            },
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        Box(Modifier.width(56.dp).fillMaxHeight().background(colour.toCanonColor()))
        Box(Modifier.weight(1f).fillMaxHeight().background(c.card)) {
            Row(
                Modifier.fillMaxWidth().padding(start = CanonMetrics.panelPadding, end = CanonMetrics.panelPadding, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = CanonText.body, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(value, style = shareMono.copy(fontSize = 13.sp), color = c.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                CanonFigure(String.format(Locale.ROOT, "%.1f", share * 100), unit = "%", size = 22.sp, colour = c.foreground)
            }
            CanonBar(share.toFloat(), Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp), track = Color.Transparent)
        }
    }
}
