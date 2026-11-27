package com.delphi.delphitools.tools.colour

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.shell.workflowColours
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourCard
import com.delphi.delphitools.canon.CanonColourStrip
import com.delphi.delphitools.canon.CanonCopyFeedback
import com.delphi.delphitools.canon.CanonDimensionPlacement
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoupe
import com.delphi.delphitools.canon.CanonLoupeBorder
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.canonLoupeFrame
import com.delphi.delphitools.canon.canonPlaced
import com.delphi.delphitools.canon.colourNamesOrNull
import com.delphi.delphitools.canon.toCanonColor
import com.delphi.delphitools.colour.LocalColourFormats
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.formatted
import com.delphi.delphitools.colour.rememberUserPalettes
import com.delphi.delphitools.colour.rgbOf
import com.delphi.delphitools.tools.images.ImageCompressEngine
import com.delphi.delphitools.tools.images.ImageFiles
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

// pixels each side
private const val LOUPE_RADIUS = 7

// ponytail: sampling reads a decode of at most 4096 px a side, not the full file; region-decode if exact pixels of larger photos matter
private const val SAMPLE_PIXELS = 4096
private const val SHOWN_PIXELS = 2000
private val grey = Rgb(0.5, 0.5, 0.5)

class PixelPick(val colour: Rgb, val pixel: IntOffset)

/** dt-ios PixelPickerViewController's state: the image, the sampled pixel and the picks. */
class PixelPickerModel(application: Application) : AndroidViewModel(application) {
    var shown by mutableStateOf<ImageBitmap?>(null)
        private set
    var imageSize by mutableStateOf(IntSize.Zero)
        private set
    var fileSize by mutableStateOf("")
        private set
    var pixel by mutableStateOf(IntOffset.Zero)
        private set
    var animatesMove by mutableStateOf(false)
        private set
    var sampled by mutableStateOf(grey)
        private set
    var loupe by mutableStateOf<ImageBitmap?>(null)
        private set
    var p3 by mutableStateOf("")
        private set
    val picks = mutableStateListOf<PixelPick>()

    private var image: Bitmap? = null
    private var loading: Job? = null
    private val p3Space = ColorSpace.get(ColorSpace.Named.DISPLAY_P3)

    val lit get() = picks.indexOfFirst { it.pixel == pixel }.takeIf { it >= 0 }

    fun load(uri: Uri?) {
        loading?.cancel()
        if (uri == null) {
            image = null
            shown = null
            imageSize = IntSize.Zero
            picks.clear()
            return
        }
        val resolver = getApplication<Application>().contentResolver
        loading = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val decoded = ImageFiles.decode(ImageDecoder.createSource(resolver, uri), SAMPLE_PIXELS) ?: return@withContext null
                ImageFiles.unlessOutOfMemory { decoded to ImageCompressEngine.preview(decoded.image, SHOWN_PIXELS).asImageBitmap() }
            } ?: return@launch
            val (decoded, preview) = loaded
            image = decoded.image
            imageSize = IntSize(decoded.image.width, decoded.image.height)
            fileSize = "${decoded.width} × ${decoded.height}"
            shown = preview
            picks.clear()
            pixel = IntOffset(-1, -1)
            move(IntOffset(decoded.image.width / 2, decoded.image.height / 2), animated = false)
        }
    }

    /** False when the clamped pixel is the one already sampled. */
    fun move(to: IntOffset, animated: Boolean): Boolean {
        val image = image ?: return false
        val clamped = IntOffset(to.x.coerceIn(0, image.width - 1), to.y.coerceIn(0, image.height - 1))
        if (clamped == pixel) return false
        pixel = clamped
        animatesMove = animated
        sample(image)
        return true
    }

    private fun sample(image: Bitmap) {
        val (x, y) = pixel
        // getColor keeps colour space
        sampled = rgbOf(image.getPixel(x, y))
        val patch = PixelPickerSampler.loupe(image.width, image.height, x, y, LOUPE_RADIUS, image::getPixel)
        val side = LOUPE_RADIUS * 2 + 1
        loupe = Bitmap.createBitmap(patch, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
        val wide = image.getColor(x, y).convert(p3Space)
        p3 = PixelPickerSampler.displayP3(wide.red(), wide.green(), wide.blue())
    }

    fun pick() {
        if (image == null) return
        picks += PixelPick(sampled, pixel)
    }

    fun recall(index: Int) {
        picks.getOrNull(index)?.let { move(it.pixel, animated = true) }
    }

    fun remove(index: Int) {
        if (index in picks.indices) picks.removeAt(index)
    }
}

@Composable
fun PixelPicker(model: PixelPickerModel = viewModel()) {
    val view = LocalView.current
    val store = rememberUserPalettes()
    val loaded = model.shown != null
    val size = model.imageSize
    val aspect = if (loaded) (size.width.toFloat() / size.height).coerceIn(0.8f, 1.6f) else 4f / 3

    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(aspect), background = LocalDelphiColours.current.muted, maxHeight = 640.dp) {
        Column(Modifier.fillMaxSize()) {
            PixelPickerPicture(model, Modifier.fillMaxWidth().weight(1f))
            if (loaded) CanonColourStrip(model.sampled, format = LocalColourFormats.current.primary)
        }
    }

    val source = CanonSection.of {
        CanonRows {
            if (!loaded) {
                CanonDropZone("Drop an image", onFiles = { uris -> model.load(uris.firstOrNull()) })
            } else {
                CanonRow("Image", detail = model.fileSize, flush = true) {
                    CanonButton("Clear", icon = Icons.Outlined.Delete) { model.load(null) }
                }
            }
        }
    }

    val card = CanonSection.of { CanonColourCard(model.sampled) }
    val p3 = CanonSection.of { CanonRows { CanonValueLine("P3", model.p3) } }

    val picked = CanonSection.of {
        CanonRows {
            CanonHeading("Picked")
            PickedColourList(model)
            CanonGrid {
                val cell = Modifier.weight(1f).fillMaxHeight()
                CanonButton("Save", cell, icon = Icons.Outlined.FavoriteBorder, flashesCopied = true, flashTitle = "Saved") {
                    store.add("Pixel Picker", model.picks.map { it.colour })
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
                PaletteExportButton({ "Pixel Picker" }, { model.picks.map { it.colour } }, cell)
                CanonButton("Clear", cell, icon = Icons.Outlined.Delete) { model.picks.clear() }
            }
        }
    }

    val pass = workflowColours {
        if (model.picks.isEmpty()) (if (loaded) listOf(model.sampled.hex) else emptyList()) else model.picks.map { it.colour.hex }
    }
    CanonSplit(
        sidebar = listOf(canvas),
        main = listOf(source) + (if (loaded) listOf(card, p3) else emptyList()) + pass + (if (model.picks.isNotEmpty()) listOf(picked) else emptyList()),
        halves = true,
        collapsesTo = 240.dp,
        isFooterHidden = !loaded,
        footer = {
            CanonButton("Pick", kind = CanonButtonKind.Primary, icon = Icons.Outlined.Colorize, oversized = true, flashesCopied = true, flashTitle = "Picked") {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                model.pick()
            }
        },
    )
}

private sealed interface Grab {
    data class Crosshair(val offset: Offset) : Grab
    data class Loupe(val from: IntOffset, val at: Offset) : Grab
}

// dt-ios PixelPickerPicture
@Composable
private fun PixelPickerPicture(model: PixelPickerModel, modifier: Modifier) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val image = model.shown
    if (image == null) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Colorize, null, tint = c.mutedForeground, modifier = Modifier.size(34.dp))
            Text("Image", style = CanonText.bodySemibold.copy(fontSize = 17.sp), color = c.mutedForeground)
        }
        return
    }
    val animates = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
    val covered = with(density) { LocalCanonCanvas.current.top.toPx() }
    val pixels = model.imageSize
    var sides by remember { mutableStateOf<Pair<Boolean, Boolean>?>(null) }
    var drags by remember { mutableIntStateOf(0) }
    val name = colourNamesOrNull()?.closest(model.sampled)
    BoxWithConstraints(
        modifier
            .clipToBounds()
            .semantics {
                contentDescription = "Image"
                if (name != null) stateDescription = name
            },
    ) {
        val bounds = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val fit = remember(bounds, covered, pixels) {
            val area = Rect(0f, covered, bounds.width, bounds.height)
            val scale = min(area.width / pixels.width, area.height / pixels.height)
            val shownSize = Size(pixels.width * scale, pixels.height * scale)
            Rect(Offset(area.center.x - shownSize.width / 2, area.center.y - shownSize.height / 2), shownSize)
        }
        fun place(pixel: IntOffset) = Offset(
            fit.left + (pixel.x + 0.5f) / pixels.width * fit.width,
            fit.top + (pixel.y + 0.5f) / pixels.height * fit.height,
        )
        fun pixelAt(location: Offset) = IntOffset(
            ((location.x - fit.left) / maxOf(fit.width, 1f) * pixels.width).toInt(),
            ((location.y - fit.top) / maxOf(fit.height, 1f) * pixels.height).toInt(),
        )
        val target = place(model.pixel)
        val spec = if (model.animatesMove && animates && drags == 0) spring<Offset>(dampingRatio = 0.82f, stiffness = 250f) else snap()
        val centre by animateOffsetAsState(target, spec)
        val loupeSide = with(density) { (bounds.height * 0.5f).coerceIn(84.dp.toPx(), 128.dp.toPx()) }
        val loupeFrame = with(density) {
            canonLoupeFrame(centre, loupeSide, 28.dp.toPx(), bounds, covered + 8.dp.toPx(), 8.dp.toPx(), sides)
        }
        val reach = with(density) { 36.dp.toPx() }
        val cell = (loupeSide - with(density) { CanonLoupeBorder.toPx() } * 2) / (LOUPE_RADIUS * 2 + 1)
        val current by rememberUpdatedState(Triple(loupeFrame, target, cell))

        fun grab(at: Offset): Grab? {
            val (frame, now, _) = current
            if (frame.contains(at)) return Grab.Loupe(model.pixel, at)
            if (hypot(at.x - now.x, at.y - now.y) >= reach) return null
            return Grab.Crosshair(at - now)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(fit, pixels) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val grabbed = grab(down.position)
                        val moved = if (grabbed == null) null else awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        if (grabbed == null || moved == null) {
                            val (frame, _, _) = current
                            if (!fit.contains(down.position) || frame.contains(down.position)) return@awaitEachGesture
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            model.move(pixelAt(down.position), animated = true)
                            return@awaitEachGesture
                        }
                        val (frame, now, _) = current
                        sides = (frame.center.x < now.x) to (frame.center.y < now.y)
                        drags++
                        drag(moved.id) { change ->
                            change.consume()
                            val location = change.position
                            when (grabbed) {
                                is Grab.Crosshair -> model.move(pixelAt(location - grabbed.offset), animated = false)
                                is Grab.Loupe -> {
                                    val step = current.third
                                    val to = IntOffset(
                                        grabbed.from.x + ((location.x - grabbed.at.x) / step).roundToInt(),
                                        grabbed.from.y + ((location.y - grabbed.at.y) / step).roundToInt(),
                                    )
                                    if (model.move(to, animated = false)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }
                        }
                        drags--
                        sides = null
                    }
                },
        ) {
            drawImage(
                image,
                dstOffset = IntOffset(fit.left.roundToInt(), fit.top.roundToInt()),
                dstSize = IntSize(fit.width.roundToInt(), fit.height.roundToInt()),
                filterQuality = FilterQuality.None,
            )
            val line = 1.5.dp.toPx()
            for ((dx, dy) in listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)) {
                val from = centre + Offset(dx, dy) * 5.dp.toPx()
                val to = centre + Offset(dx, dy) * 15.dp.toPx()
                drawLine(Color.Black.copy(alpha = 0.35f), from, to, line * 2.5f, StrokeCap.Round)
                drawLine(Color.White, from, to, line)
            }
        }
        CanonLoupe(
            model.loupe,
            model.sampled.toCanonColor(),
            Modifier.canonPlaced(CanonDimensionPlacement(loupeFrame.center, loupeFrame.size)),
        )
    }
}

private val valueMono = CanonText.caption.copy(fontFamily = DelphiMono, fontSize = 12.sp)

@Composable
private fun PickedColourList(model: PixelPickerModel) {
    val lit = model.lit
    model.picks.forEachIndexed { index, pick -> PickedColourRow(pick.colour, index == lit, { model.recall(index) }) { model.remove(index) } }
}

@Composable
private fun PickedColourRow(colour: Rgb, selected: Boolean, onRecall: () -> Unit, onRemove: () -> Unit) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val name = colourNamesOrNull()?.closest(colour).orEmpty()
    val value = colour.formatted(LocalColourFormats.current.primary)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = CanonMetrics.rowHeight),
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        Box(Modifier.width(56.dp).fillMaxHeight().background(colour.toCanonColor()))
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .canonClickable(onClick = onRecall)
                .semantics(mergeDescendants = true) {
                    contentDescription = name
                    stateDescription = value
                }
                .background(if (selected) c.accentBg else c.card)
                .padding(horizontal = CanonMetrics.panelPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Text(
                name,
                style = CanonText.body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                color = if (selected) c.primary else c.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(value, style = valueMono, color = c.mutedForeground, maxLines = 1)
        }
        CanonButton("", Modifier.width(CanonMetrics.rowHeight).fillMaxHeight(), icon = Icons.Outlined.ContentCopy, contentDescription = "Copy") {
            CanonCopyFeedback.copy(view, value)
        }
        CanonButton("", Modifier.width(CanonMetrics.rowHeight).fillMaxHeight(), icon = Icons.Outlined.Delete, contentDescription = "Remove", onClick = onRemove)
    }
}
