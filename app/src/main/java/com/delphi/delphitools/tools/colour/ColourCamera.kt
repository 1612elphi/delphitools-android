package com.delphi.delphitools.tools.colour

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourStrip
import com.delphi.delphitools.canon.CanonColourType
import com.delphi.delphitools.canon.CanonCopyFeedback
import com.delphi.delphitools.canon.CanonDimensionPlacement
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonLoupe
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.MenuCellItem
import com.delphi.delphitools.canon.MenuCellPopup
import com.delphi.delphitools.canon.canonLoupeFrame
import com.delphi.delphitools.canon.canonPlaced
import com.delphi.delphitools.canon.toCanonColor
import com.delphi.delphitools.colour.LocalColourFormats
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.rememberUserPalettes
import com.delphi.delphitools.colour.rgbOf
import com.delphi.delphitools.colour.takesBlackInk
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

private val grey = Rgb(0.5, 0.5, 0.5)
private const val PALETTE_INTERVAL_MS = 350L
private const val MINIMUM_REGION = 0.08f
private val REACH = 22.dp
private val LOUPE_SIDE = 112.dp

enum class CameraPermission { Undetermined, Granted, Denied }

/** dt-ios ColourCameraViewModel: mode, sample point, selection, palette and the captured still. */
class ColourCameraModel(application: Application) : AndroidViewModel(application) {
    val device = ColourCameraDevice.backCamera(application)

    var palette by mutableStateOf(false)
    var selection by mutableStateOf(false)
    var colourCount by mutableStateOf(6)
    var permission by mutableStateOf(CameraPermission.Undetermined)
    var asked = false

    var live by mutableStateOf(true)
        private set
    var streaming by mutableStateOf(false)
        private set
    var still by mutableStateOf<ImageBitmap?>(null)
        private set
    var zoom by mutableStateOf(1f)
        private set

    var sampled by mutableStateOf(grey)
        private set
    var loupe by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Normalised image coordinates. */
    var point by mutableStateOf(Offset(0.5f, 0.5f))
        private set
    var region by mutableStateOf(Rect(0.3f, 0.35f, 0.7f, 0.65f))
        private set
    var extracted by mutableStateOf<List<Rgb>>(emptyList())
        private set

    /** Colours taken with Capture in colour mode, oldest first. */
    val captured = mutableStateListOf<Rgb>()

    private var latest: ColourFrame? = null
    private var stillFrame: ColourFrame? = null
    private var extracting = false
    private var lastExtraction = 0L
    private var extraction = 0

    val shownPalette get() = if (palette) extracted else captured.toList()

    fun checkPermission() {
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            permission = CameraPermission.Granted
        }
    }

    fun permissionResult(granted: Boolean) {
        permission = if (granted) CameraPermission.Granted else CameraPermission.Denied
    }

    /** Camera thread. */
    fun receive(frame: YuvFrame) {
        viewModelScope.launch {
            // late frame after stop
            if (!live || device?.isOpen != true) return@launch
            latest = frame
            streaming = true
            if (!palette) {
                sample(frame)
            } else {
                val now = System.currentTimeMillis()
                if (extracting || now - lastExtraction < PALETTE_INTERVAL_MS) return@launch
                lastExtraction = now
                extract(frame)
            }
        }
    }

    fun stopped() {
        streaming = false
    }

    fun selectZoom(ratio: Float) {
        zoom = ratio
        device?.setZoom(ratio)
    }

    fun capture() {
        val frame = latest ?: return
        stillFrame = frame
        live = false
        streaming = false
        reprocess()
        viewModelScope.launch {
            still = withContext(Dispatchers.Default) {
                val pixels = IntArray(frame.width * frame.height) { frame.pixel(it % frame.width, it / frame.width) }
                Bitmap.createBitmap(pixels, frame.width, frame.height, Bitmap.Config.ARGB_8888).asImageBitmap()
            }
        }
    }

    fun retake() {
        live = true
        still = null
        stillFrame = null
        latest = null
        point = Offset(0.5f, 0.5f)
    }

    /** Samples or extracts the still again after a setting changed. */
    fun reprocess() {
        val frame = stillFrame ?: return
        if (palette) extract(frame) else sample(frame)
    }

    fun movePoint(to: Offset) {
        point = to
        stillFrame?.let(::sample)
    }

    fun moveRegion(to: Rect) {
        region = to
        if (!live && palette && selection) stillFrame?.let(::extract)
    }

    private fun sample(frame: ColourFrame) {
        val (x, y) = ColourCameraEngine.pixelAt(frame, point.x.toDouble(), point.y.toDouble())
        sampled = rgbOf(frame.pixel(x, y))
        val side = ColourCameraEngine.LOUPE_RADIUS * 2 + 1
        val patch = PixelPickerSampler.loupe(frame.width, frame.height, x, y, ColourCameraEngine.LOUPE_RADIUS, frame::pixel)
        loupe = Bitmap.createBitmap(patch, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private fun extract(frame: ColourFrame) {
        val rect = if (selection) region.let { doubleArrayOf(it.left.toDouble(), it.top.toDouble(), it.right.toDouble(), it.bottom.toDouble()) } else null
        val count = colourCount
        extracting = true
        val run = ++extraction
        viewModelScope.launch {
            val colours = withContext(Dispatchers.Default) { ColourCameraEngine.extract(frame, rect, count) }
            if (run != extraction) return@launch
            extracting = false
            extracted = colours
        }
    }
}

@Composable
fun ColourCamera(model: ColourCameraModel = viewModel()) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val store = rememberUserPalettes()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), model::permissionResult)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    foreground = true
                    model.checkPermission()
                    if (model.permission == CameraPermission.Undetermined && !model.asked) {
                        model.asked = true
                        ask.launch(Manifest.permission.CAMERA)
                    }
                }
                Lifecycle.Event.ON_STOP -> foreground = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val colourMode = !model.palette
    val hasStill = !model.live
    fun shoot() {
        when {
            colourMode -> {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                model.captured += model.sampled
            }
            model.live -> {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                model.capture()
            }
            else -> model.retake()
        }
    }

    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(1f), background = c.muted, maxHeight = 640.dp) {
        Column(Modifier.fillMaxSize()) {
            ColourCameraViewfinder(model, foreground, Modifier.fillMaxWidth().weight(1f))
            CanonColourStrip(model.sampled, palette = if (colourMode) emptyList() else model.extracted, format = LocalColourFormats.current.primary)
        }
    }

    val shown = model.shownPalette
    val controls = CanonSection.of {
        CanonRows {
            CanonSegmented(listOf("Colour", "Palette"), if (colourMode) 0 else 1) {
                model.palette = it == 1
                if (!model.palette && hasStill) model.retake()
                model.reprocess()
            }
            if (colourMode && model.captured.isNotEmpty()) CapturedColourGrid(model.captured)
            if (!colourMode) {
                CanonRow("Area", flush = true) {
                    CanonSegmented(listOf("Frame", "Selection"), if (model.selection) 1 else 0) {
                        model.selection = it == 1
                        model.reprocess()
                    }
                }
                CanonRow("Colours", flush = true) {
                    CanonNumberField(model.colourCount, {
                        model.colourCount = it
                        model.reprocess()
                    }, range = 2..12)
                }
            }
            if (!(colourMode && model.captured.isEmpty())) {
                CanonGrid {
                    val cell = Modifier.weight(1f).fillMaxHeight()
                    CanonButton("Save", cell, icon = Icons.Outlined.FavoriteBorder, enabled = shown.isNotEmpty(), flashesCopied = true, flashTitle = "Saved") {
                        store.add("Camera Palette", model.shownPalette, "Camera")
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    PaletteExportButton({ "Camera Palette" }, { model.shownPalette }, cell)
                    if (colourMode) CanonButton("Clear", cell, icon = Icons.Outlined.Delete) { model.captured.clear() }
                }
            }
        }
    }

    val zooms = model.device?.zooms.orEmpty()
    CanonSplit(
        sidebar = listOf(canvas),
        main = listOf(controls),
        halves = true,
        collapsesTo = 240.dp,
        footer = {
            Row(Modifier.fillMaxWidth().background(c.border), horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
                if (zooms.size > 1 && !hasStill) {
                    CanonSegmented(
                        zooms.map { if (it == 0.5f) "0.5×" else "${it.toInt()}×" },
                        zooms.indexOf(model.zoom).coerceAtLeast(0),
                        Modifier.width(56.dp * zooms.size),
                    ) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        model.selectZoom(zooms[it])
                    }
                }
                val retakes = !colourMode && hasStill
                CanonButton(
                    if (retakes) "Retake" else "Capture",
                    Modifier.weight(1f),
                    kind = CanonButtonKind.Primary,
                    icon = if (retakes) Icons.Outlined.Refresh else Icons.Outlined.Camera,
                    oversized = true,
                    enabled = hasStill || model.streaming,
                ) { shoot() }
            }
        },
    )
}

private sealed interface CameraGrab {
    data class Point(val offset: Offset) : CameraGrab
    data class Move(val offset: Offset) : CameraGrab
    data class Edges(val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean) : CameraGrab
}

/**
 * dt-ios ColourCameraViewfinder: the camera or the still, filled edge to edge, with the sample point and loupe
 * in colour mode and the selection box in palette mode.
 */
@Composable
private fun ColourCameraViewfinder(model: ColourCameraModel, foreground: Boolean, modifier: Modifier) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val device = model.device
    val displayRotation = view.display?.rotation ?: Surface.ROTATION_0
    val degrees = displayRotation * 90
    val streams = device != null && model.permission == CameraPermission.Granted && foreground && model.live
    val upright = device?.uprightFrame(degrees)
    val hasPicture = model.still != null || (streams && model.streaming)
    val imageSize = model.still?.let { IntSize(it.width, it.height) } ?: upright?.let { IntSize(it.width, it.height) } ?: IntSize.Zero
    val sampling = !model.palette && hasPicture
    val boxed = model.palette && model.selection && hasPicture
    var texture by remember { mutableStateOf<SurfaceTexture?>(null) }

    DisposableEffect(device, texture, streams, degrees) {
        val surface = texture
        if (device != null && surface != null && streams) device.open(surface, degrees, model::receive)
        onDispose {
            device?.close()
            model.stopped()
        }
    }

    BoxWithConstraints(modifier.clipToBounds().background(c.muted).semantics { contentDescription = "Camera" }) {
        val bounds = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val covered = with(density) { LocalCanonCanvas.current.top.toPx() }
        val fill = remember(bounds, imageSize) {
            if (imageSize.width <= 0 || imageSize.height <= 0) Rect(Offset.Zero, bounds) else {
                val scale = max(bounds.width / imageSize.width, bounds.height / imageSize.height)
                val size = Size(imageSize.width * scale, imageSize.height * scale)
                Rect(Offset(bounds.width / 2 - size.width / 2, bounds.height / 2 - size.height / 2), size)
            }
        }
        fun place(normal: Offset) = Offset(fill.left + normal.x * fill.width, fill.top + normal.y * fill.height)
        fun place(normal: Rect) = Rect(place(normal.topLeft), place(normal.bottomRight))
        fun normal(at: Offset): Offset {
            val visible = fill.intersect(Rect(Offset.Zero, bounds))
            val x = at.x.coerceIn(visible.left, visible.right)
            val y = at.y.coerceIn(visible.top, visible.bottom)
            return Offset((x - fill.left) / max(fill.width, 1f), (y - fill.top) / max(fill.height, 1f))
        }

        if (streams && device != null) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                texture = surface
                            }

                            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                texture = null
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                        }
                    }
                },
                modifier = Modifier.canonPlaced(CanonDimensionPlacement(fill.center, fill.size)),
                update = { it.setTransform(ColourCameraDevice.transform(fill.width, fill.height, device.previewSize, displayRotation)) },
            )
        }
        model.still?.let { still ->
            Canvas(Modifier.fillMaxSize()) {
                drawImage(still, dstOffset = IntOffset(fill.left.toInt(), fill.top.toInt()), dstSize = IntSize(fill.width.toInt(), fill.height.toInt()))
            }
        }
        if (!hasPicture) {
            val denied = model.permission == CameraPermission.Denied
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.PhotoCamera, null, tint = c.mutedForeground, modifier = Modifier.size(34.dp))
                Text(if (denied) "Camera Access Required" else "Camera", style = CanonText.bodySemibold.copy(fontSize = 17.sp), color = c.mutedForeground, textAlign = TextAlign.Center)
                if (denied) {
                    Text(
                        "Colour Camera needs access to your camera to pick colours from the world around you.",
                        style = CanonText.caption,
                        color = c.mutedForeground,
                        textAlign = TextAlign.Center,
                    )
                    CanonButton("Open Settings", icon = Icons.Outlined.Settings) {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    }
                }
            }
        }

        // refresh keeps the drag
        var dragged by remember { mutableStateOf<Rect?>(null) }
        val point = model.point
        val region = dragged ?: model.region
        val reach = with(density) { REACH.toPx() }
        val loupeSide = with(density) { LOUPE_SIDE.toPx() }
        val centre = place(point)
        val loupeFrame = with(density) { canonLoupeFrame(centre, loupeSide, 24.dp.toPx(), bounds, covered + 8.dp.toPx(), 8.dp.toPx()) }
        val current by rememberUpdatedState(Triple(sampling, boxed, loupeFrame))
        val latest by rememberUpdatedState(point to region)

        fun grab(at: Offset): CameraGrab? {
            val (isSampling, isBoxed, frame) = current
            val (point, region) = latest
            if (isSampling) {
                val now = place(point)
                return if (hypot(at.x - now.x, at.y - now.y) < reach * 2 || frame.contains(at)) CameraGrab.Point(at - now) else null
            }
            if (!isBoxed) return null
            val box = place(region)
            if (!box.inflate(reach).contains(at)) return null
            val left = abs(at.x - box.left) < reach && abs(at.x - box.left) <= abs(at.x - box.right)
            val right = !left && abs(at.x - box.right) < reach
            val top = abs(at.y - box.top) < reach && abs(at.y - box.top) <= abs(at.y - box.bottom)
            val bottom = !top && abs(at.y - box.bottom) < reach
            if (left || right || top || bottom) return CameraGrab.Edges(left, top, right, bottom)
            return CameraGrab.Move(normal(at) - region.topLeft)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(fill) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val found = grab(down.position)
                        val moved = if (found == null) null else awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        if (found == null || moved == null) {
                            if (current.first) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                model.movePoint(normal(down.position))
                            }
                            return@awaitEachGesture
                        }
                        drag(moved.id) { change ->
                            change.consume()
                            val at = change.position
                            val region = latest.second
                            when (found) {
                                is CameraGrab.Point -> model.movePoint(normal(at - found.offset))
                                is CameraGrab.Move -> {
                                    val to = normal(at) - found.offset
                                    val x = to.x.coerceIn(0f, 1 - region.width)
                                    val y = to.y.coerceIn(0f, 1 - region.height)
                                    dragged = Rect(Offset(x, y), region.size)
                                }
                                is CameraGrab.Edges -> {
                                    val n = normal(at)
                                    var (l, t, r, b) = listOf(region.left, region.top, region.right, region.bottom)
                                    if (found.left) l = n.x.coerceIn(0f, r - MINIMUM_REGION)
                                    if (found.right) r = n.x.coerceIn(l + MINIMUM_REGION, 1f)
                                    if (found.top) t = n.y.coerceIn(0f, b - MINIMUM_REGION)
                                    if (found.bottom) b = n.y.coerceIn(t + MINIMUM_REGION, 1f)
                                    dragged = Rect(l, t, r, b)
                                }
                            }
                        }
                        dragged?.let(model::moveRegion)
                        dragged = null
                    }
                },
        ) {
            if (sampling) {
                val line = 1.5.dp.toPx()
                for ((dx, dy) in listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)) {
                    val from = centre + Offset(dx, dy) * 5.dp.toPx()
                    val to = centre + Offset(dx, dy) * 14.dp.toPx()
                    drawLine(Color.Black.copy(alpha = 0.35f), from, to, line * 2.5f, StrokeCap.Round)
                    drawLine(Color.White, from, to, line)
                }
            }
            if (boxed) {
                val box = place(region)
                val shade = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addRect(box)
                }
                drawPath(shade, Color.Black.copy(alpha = 0.45f))
                drawRect(c.primary, box.topLeft, box.size, style = Stroke(CanonMetrics.frame.toPx()))
                val mark = 7.dp.toPx()
                for (corner in listOf(box.topLeft, box.topRight, box.bottomLeft, box.bottomRight)) {
                    drawRect(c.primary, Offset(corner.x - mark, corner.y - mark), Size(mark * 2, mark * 2))
                }
            }
        }
        if (sampling) {
            CanonLoupe(model.loupe, model.sampled.toCanonColor(), Modifier.canonPlaced(CanonDimensionPlacement(loupeFrame.center, loupeFrame.size)))
        }
    }
}

// dt-ios CapturedColourGrid
@Composable
private fun CapturedColourGrid(colours: MutableList<Rgb>) {
    val c = LocalDelphiColours.current
    // ponytail: no spring-in of the newest swatch
    BoxWithConstraints(Modifier.fillMaxWidth().background(c.border)) {
        val hairline = CanonMetrics.hairline
        val columns = max(1, ((maxWidth + hairline) / (56.dp + hairline)).toInt())
        val side = (maxWidth - hairline * (columns - 1)) / columns
        Column(verticalArrangement = Arrangement.spacedBy(hairline)) {
            colours.toList().withIndex().chunked(columns).forEach { row ->
                Row(Modifier.height(side), horizontalArrangement = Arrangement.spacedBy(hairline)) {
                    row.forEach { (index, colour) -> CapturedSwatch(colour, Modifier.width(side).fillMaxHeight()) { colours.removeAt(index) } }
                    if (row.size < columns) Box(Modifier.weight(1f).fillMaxHeight().background(c.card))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CapturedSwatch(colour: Rgb, modifier: Modifier, onRemove: () -> Unit) {
    val view = LocalView.current
    var menu by remember { mutableStateOf(false) }
    val hex = colour.hex
    Box(
        modifier
            .semantics(mergeDescendants = true) { contentDescription = hex }
            .combinedClickable(onLongClick = { menu = true }) { CanonCopyFeedback.copy(view, hex) }
            .background(colour.toCanonColor()),
        contentAlignment = Alignment.Center,
    ) {
        Text(hex, style = CanonColourType.mono11, color = if (colour.takesBlackInk) Color.Black else Color.White, maxLines = 1)
        MenuCellPopup(menu, { menu = false }) {
            MenuCellItem("Copy", icon = Icons.Outlined.ContentCopy) {
                menu = false
                CanonCopyFeedback.copy(view, hex)
            }
            MenuCellItem("Remove", icon = Icons.Outlined.Delete) {
                menu = false
                onRemove()
            }
        }
    }
}
