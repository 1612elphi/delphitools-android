package com.delphi.delphitools.tools.colour

import android.app.Application
import android.content.Context
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Stairs
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCodeBlock
import com.delphi.delphitools.canon.CanonColourControls
import com.delphi.delphitools.canon.CanonColourControlsState
import com.delphi.delphitools.canon.CanonColourSolid
import com.delphi.delphitools.canon.CanonColourSolidState
import com.delphi.delphitools.canon.CanonColourStrip
import com.delphi.delphitools.canon.CanonDecimalField
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonRule
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.colourNamesOrNull
import com.delphi.delphitools.canon.toCanonColor
import com.delphi.delphitools.colour.LocalColourFormats
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.SolidSpace
import com.delphi.delphitools.colour.fromOklch
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.parseColour
import com.delphi.delphitools.colour.parseHex
import com.delphi.delphitools.shell.WorkflowRun
import com.delphi.delphitools.colour.toArgb
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.tools.ToolUndoActions
import com.delphi.delphitools.tools.UndoGrouping
import com.delphi.delphitools.tools.UndoStack
import com.delphi.delphitools.tools.colour.GradientPaint.Shape
import com.delphi.delphitools.tools.colour.GradientPaint.Space
import com.delphi.delphitools.tools.images.ImageFiles
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class GradientType(val title: String) { Linear("Linear"), Radial("Radial"), Conic("Conic"), Mesh("Mesh") }

data class GradientStop(val id: Long, val colour: Rgb, val position: Double)

private class SizePreset(val label: String, val width: Int, val height: Int)

private val sizePresets = listOf(
    SizePreset("512", 512, 512), SizePreset("1K", 1024, 1024), SizePreset("2K", 2048, 2048), SizePreset("4K", 4096, 4096),
    SizePreset("HD", 1920, 1080), SizePreset("4K HD", 3840, 2160), SizePreset("Insta", 1080, 1080), SizePreset("Story", 1080, 1920),
)
private val exportSizes = 100..GradientPaint.MAX_EDGE
private val centred = Offset(0.5f, 0.5f)

// snap range in degrees
private const val SNAP = 4.0

data class GradientSnapshot(
    val type: GradientType,
    val angle: Double,
    val stops: List<GradientStop>,
    val meshPoints: List<MeshPoint>,
    val interpolation: Space,
    val centre: Offset,
)

/** dt-ios GradientGeneratorViewModel and the controller's selection and export. */
class GradientGeneratorModel(application: Application) : AndroidViewModel(application) {
    private var nextId = 0L
    private fun newId() = nextId++

    var type by mutableStateOf(GradientType.Linear)
        private set
    var angle by mutableDoubleStateOf(90.0)
    var stops by mutableStateOf(listOf(GradientStop(newId(), Rgb(0.231, 0.510, 0.965), 0.0), GradientStop(newId(), Rgb(0.965, 0.231, 0.510), 1.0)))
    var meshPoints by mutableStateOf(emptyList<MeshPoint>())
    var interpolation by mutableStateOf(Space.Srgb)
    var centre by mutableStateOf(centred)

    /** 0...100, image export and preview grain. */
    var noise by mutableFloatStateOf(0f)
    var meshImage by mutableStateOf<ImageBitmap?>(null)
        private set
    var selected by mutableStateOf<Long?>(null)
        private set
    var width by mutableIntStateOf(800)
    var height by mutableIntStateOf(600)
    var exporting by mutableStateOf(false)
        private set

    /** Set while a handle, hub or knob is held; code waits for the release. */
    var dragging by mutableStateOf(false)

    val solid = CanonColourSolidState(SolidSpace.OKLCH, Rgb(0.231, 0.510, 0.965))

    val controls = CanonColourControlsState(solid, activeRow = 2)

    private var meshJob: Job? = null
    private var shownColour: Rgb? = null
    val undoStack = UndoStack<GradientSnapshot>()
    val grouping = UndoGrouping()

    val isMesh get() = type == GradientType.Mesh
    val paintStops get() = stops.map { GradientPaint.Stop(it.colour, it.position) }
    val sortedStops get() = stops.sortedBy { it.position }
    val shape: Shape?
        get() = when (type) {
            GradientType.Linear -> Shape.Linear(angle)
            GradientType.Radial -> Shape.Radial(centre.x.toDouble(), centre.y.toDouble())
            GradientType.Conic -> Shape.Conic(angle, centre.x.toDouble(), centre.y.toDouble())
            GradientType.Mesh -> null
        }
    val css get() = shape?.let { GradientPaint.css(it, paintStops, interpolation) } ?: GradientMesh.css(meshPoints)
    val selectedColour get() = if (isMesh) meshPoints.firstOrNull { it.id == selected }?.colour else stops.firstOrNull { it.id == selected }?.colour
    val canRemove get() = (if (isMesh) meshPoints.size else stops.size) > 2

    init {
        seedMeshPoints()
        select(sortedStops.first().id)
        viewModelScope.launch { snapshotFlow { solid.colour }.collect(::colourChanged) }
    }

    private fun seedMeshPoints() {
        meshPoints = listOf(
            MeshPoint(newId(), 0.15f, 0.2f, fromOklch(0.65, 0.2, 280.0)),
            MeshPoint(newId(), 0.85f, 0.15f, fromOklch(0.7, 0.18, 30.0)),
            MeshPoint(newId(), 0.5f, 0.5f, fromOklch(0.8, 0.15, 150.0)),
            MeshPoint(newId(), 0.2f, 0.85f, fromOklch(0.6, 0.22, 200.0)),
            MeshPoint(newId(), 0.8f, 0.8f, fromOklch(0.75, 0.2, 60.0)),
        )
        renderMeshFast()
    }

    private fun snapshot() = GradientSnapshot(type, angle, stops, meshPoints, interpolation, centre)

    fun pushUndo() = undoStack.push(snapshot())

    /** dt-ios edit: one undo step per touch sequence. */
    fun edit(change: () -> Unit) {
        grouping.record(::pushUndo)
        change()
    }

    /** A workflow's colours: several replace the stops, one replaces the first stop's colour. */
    fun carry(colours: List<Rgb>) {
        stops = when {
            colours.size > 1 -> colours.mapIndexed { index, colour -> GradientStop(newId(), colour, index.toDouble() / (colours.size - 1)) }
            colours.size == 1 -> sortedStops.first().let { first -> stops.map { if (it.id == first.id) it.copy(colour = colours[0]) else it } }
            else -> return
        }
        select(sortedStops.first().id)
    }

    private fun restore(step: GradientSnapshot?) {
        step ?: return
        type = step.type
        angle = step.angle
        stops = step.stops
        meshPoints = step.meshPoints
        interpolation = step.interpolation
        centre = step.centre
        if (isMesh) renderMeshFast()
        select(if (selectedColour != null) selected else if (isMesh) meshPoints.firstOrNull()?.id else sortedStops.firstOrNull()?.id)
    }

    fun undo() = restore(undoStack.undo(snapshot()))

    fun redo() = restore(undoStack.redo(snapshot()))

    fun choose(type: GradientType) {
        this.type = type
        select(if (isMesh) meshPoints.firstOrNull()?.id else sortedStops.firstOrNull()?.id)
    }

    /** The solid jumps to the selected colour; the eased iOS move would write eased colours back. */
    fun select(id: Long?) {
        selected = id
        val colour = selectedColour ?: return
        shownColour = colour
        solid.setPoint(solid.space.pointFor(colour), animated = false)
    }

    private fun colourChanged(colour: Rgb) {
        if (colour.hex == shownColour?.hex) return
        shownColour = colour
        if (selectedColour.let { it != null && it.hex != colour.hex }) grouping.record(::pushUndo)
        if (isMesh) {
            meshPoints = meshPoints.map { if (it.id == selected) it.copy(colour = colour) else it }
            renderMeshFast()
        } else {
            stops = stops.map { if (it.id == selected) it.copy(colour = colour) else it }
        }
    }

    fun setPosition(id: Long, position: Double) {
        stops = stops.map { if (it.id == id) it.copy(position = position.coerceIn(0.0, 1.0)) else it }
    }

    /** A stop at `position` in the colour the gradient already has there, so the picture does not change. */
    fun addStop(position: Double): Long {
        pushUndo()
        val stop = GradientStop(newId(), GradientPaint.colour(position, paintStops, interpolation), position)
        stops = stops + stop
        return stop.id
    }

    fun autoStep() {
        if (!GradientStops.canAutoStep(stops.size)) return
        pushUndo()
        stops = GradientStops.autoStep(sortedStops, { it.position }, paintStops, interpolation) { colour, at -> GradientStop(newId(), colour, at) }
    }

    fun addMeshPoint(x: Float, y: Float): Long {
        pushUndo()
        val point = MeshPoint(newId(), x, y, GradientMesh.colour(meshPoints, x.toDouble(), y.toDouble()))
        meshPoints = meshPoints + point
        renderMeshFast()
        return point.id
    }

    /** The web tool's grid seeds, `size` by `size`, inset so every handle can be reached. */
    fun seedMeshGrid(size: Int) {
        val hexes = if (size == 2) listOf("3b82f6", "8b5cf6", "10b981", "f59e0b")
        else listOf("3b82f6", "8b5cf6", "ec4899", "10b981", "6366f1", "f59e0b", "06b6d4", "84cc16", "ef4444")
        pushUndo()
        val step = 0.7f / (size - 1)
        meshPoints = (0 until size * size).map { index ->
            MeshPoint(newId(), 0.15f + index % size * step, 0.15f + index / size * step, parseHex(hexes[index % hexes.size])!!)
        }
        select(meshPoints.first().id)
        renderMeshFast()
    }

    fun removeSelected() {
        val id = selected ?: return
        if (!canRemove) return
        pushUndo()
        if (isMesh) {
            meshPoints = meshPoints.filter { it.id != id }
            renderMeshFast()
            select(meshPoints.firstOrNull()?.id)
        } else {
            val at = stops.firstOrNull { it.id == id }?.position ?: return
            stops = stops.filter { it.id != id }
            select(stops.minByOrNull { abs(it.position - at) }?.id)
        }
    }

    /** Low-res render for live feedback. */
    fun renderMeshFast() = renderMesh(100, 100)

    /** At most 400 px wide, at the export's aspect. */
    fun renderMeshFull() = renderMesh(400, maxOf(2, 400 * height / maxOf(width, 1)))

    private fun renderMesh(width: Int, height: Int) {
        val points = meshPoints
        if (points.isEmpty()) return
        meshJob?.cancel()
        meshJob = viewModelScope.launch {
            val pixels = withContext(Dispatchers.Default) { GradientMesh.render(points, width, height) { !isActive } } ?: return@launch
            meshImage = android.graphics.Bitmap.createBitmap(pixels, width, height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }

    fun export(context: Context) {
        if (exporting) return
        val (w, h) = GradientPaint.capped(width, height)
        val shape = shape
        val ramp = GradientPaint.ramp(paintStops, interpolation)
        val points = meshPoints
        val grain = noise / 100f
        val name = "gradient-${type.name.lowercase()}.png"
        exporting = true
        viewModelScope.launch {
            // ponytail: an 8192 px square is 256 MB of pixels; a failed allocation fails the export
            val data = withContext(Dispatchers.Default) {
                runCatching {
                    val bitmap = if (shape == null) {
                        android.graphics.Bitmap.createBitmap(GradientMesh.render(points, w, h)!!, w, h, android.graphics.Bitmap.Config.ARGB_8888)
                            .copy(android.graphics.Bitmap.Config.ARGB_8888, true)
                    } else {
                        android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888).also {
                            GradientDrawing.gradient(android.graphics.Canvas(it), shape, ramp, w.toFloat(), h.toFloat())
                        }
                    }
                    GradientDrawing.grain(android.graphics.Canvas(bitmap), grain, w.toFloat(), h.toFloat())
                    ImageFiles.png(bitmap)
                }.getOrNull()
            }
            exporting = false
            if (data != null) FileExport.share(context, data, name, "image/png")
        }
    }
}

// dt-ios GradientPaint.draw
private object GradientDrawing {
    private val tile by lazy {
        val levels = IntArray(GradientPaint.NOISE_SIDE * GradientPaint.NOISE_SIDE) {
            val v = GradientPaint.noise[it].toInt() and 0xFF
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        android.graphics.Bitmap.createBitmap(levels, GradientPaint.NOISE_SIDE, GradientPaint.NOISE_SIDE, android.graphics.Bitmap.Config.ARGB_8888)
    }

    fun gradient(canvas: android.graphics.Canvas, shape: Shape, ramp: List<GradientPaint.Stop>, w: Float, h: Float) {
        if (ramp.size < 2) return
        val colours = ramp.map { it.colour.toArgb() }.toIntArray()
        val positions = ramp.map { it.position.toFloat() }.toFloatArray()
        val shader = when (shape) {
            is Shape.Linear -> {
                val l = GradientPaint.line(shape.angle, w.toDouble(), h.toDouble())
                LinearGradient(l[0].toFloat(), l[1].toFloat(), l[2].toFloat(), l[3].toFloat(), colours, positions, Shader.TileMode.CLAMP)
            }
            is Shape.Radial -> {
                val x = (shape.x * w).toFloat()
                val y = (shape.y * h).toFloat()
                val radius = GradientPaint.farthestCorner(x.toDouble(), y.toDouble(), w.toDouble(), h.toDouble()).toFloat()
                RadialGradient(x, y, max(radius, 0.01f), colours, positions, Shader.TileMode.CLAMP)
            }
            is Shape.Conic -> {
                val x = (shape.x * w).toFloat()
                val y = (shape.y * h).toFloat()
                // sweep starts +x, clockwise
                SweepGradient(x, y, colours, positions).apply { setLocalMatrix(Matrix().apply { setRotate((shape.angle - 90).toFloat(), x, y) }) }
            }
        }
        canvas.drawRect(0f, 0f, w, h, Paint().apply { this.shader = shader })
    }

    fun grain(canvas: android.graphics.Canvas, amount: Float, w: Float, h: Float) {
        if (amount <= 0f) return
        val paint = Paint().apply {
            shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            blendMode = BlendMode.OVERLAY
            alpha = (amount * 0.4f * 255).roundToInt()
        }
        canvas.drawRect(0f, 0f, w, h, paint)
    }
}

@Composable
fun GradientGenerator(model: GradientGeneratorModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val view = LocalView.current
    val mesh = model.isMesh
    val type = model.type
    ToolUndoActions(model.undoStack.canUndo, model.undoStack.canRedo, model::undo, model::redo)
    LaunchedEffect(model) { WorkflowRun.takeColours()?.let { model.carry(it.mapNotNull(::parseColour)) } }

    LaunchedEffect(model) {
        snapshotFlow { Triple(model.meshPoints, model.width to model.height, model.dragging to model.type) }.collect { (_, _, state) ->
            if (state.second != GradientType.Mesh || state.first) return@collect
            delay(120)
            model.renderMeshFull()
        }
    }
    LaunchedEffect(model) {
        snapshotFlow { Triple(model.stops to model.interpolation, model.meshPoints, model.selected to model.type) }.collect {
            model.solid.marks = if (model.isMesh) {
                model.meshPoints.filter { it.id != model.selected }.map { it.colour }
            } else {
                (0..16).map { GradientPaint.colour(it / 16.0, model.paintStops, model.interpolation) }
            }
        }
    }

    val aspect = (model.width.toFloat() / model.height).coerceIn(0.8f, 1.6f)
    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(aspect), background = c.muted, maxHeight = 640.dp, showsPattern = true) {
        GradientPicture(model, Modifier.fillMaxSize())
    }

    val settings = CanonSection.of {
        CanonRows {
            CanonSegmented(GradientType.entries.map { it.title }, type.ordinal) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                model.edit { model.choose(GradientType.entries[it]) }
            }
            if (!mesh) {
                CanonRow("Interpolation", flush = true) {
                    CanonSegmented(Space.entries.map { it.title }, model.interpolation.ordinal) { model.edit { model.interpolation = Space.entries[it] } }
                }
            }
            if (type == GradientType.Linear || type == GradientType.Conic) {
                CanonRow(if (type == GradientType.Conic) "Start" else "Angle", flush = true) {
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        GradientAngleDial(model.angle) { model.edit { model.angle = it } }
                        CanonRule(vertical = true)
                        CanonDecimalField(Math.round(model.angle).toDouble(), { model.edit { model.angle = it % 360 } }, unit = "°", step = 15.0, decimals = 0, range = 0.0..359.0)
                    }
                }
            }
            if (type == GradientType.Radial || type == GradientType.Conic) {
                val detail = "${(model.centre.x * 100).roundToInt()} %, ${(model.centre.y * 100).roundToInt()} %"
                CanonRow("Centre", detail = detail, flush = true) {
                    CanonButton("Reset", icon = Icons.Outlined.GpsFixed, enabled = model.centre != centred) { model.edit { model.centre = centred } }
                }
            }
            if (mesh) {
                CanonRow("Grid", flush = true) {
                    CanonSegmented(listOf("2×2", "3×3"), -1) { model.seedMeshGrid(if (it == 0) 2 else 3) }
                }
            }
        }
    }

    val solidCanvas = CanonSection.canvas(CanonCanvasSize.Aspect(1f), background = c.muted, boxing = CanonCanvasBoxing.UnlessWide) {
        Column(Modifier.fillMaxSize()) {
            CanonColourSolid(model.solid, Modifier.fillMaxWidth().weight(1f).padding(top = LocalCanonCanvas.current.top))
            CanonColourStrip(model.solid.colour, format = LocalColourFormats.current.primary)
        }
    }

    val stopsBlock = CanonSection.of {
        CanonRows {
            CanonHeading(if (mesh) "Points" else "Colour Stops")
            CanonColourControls(model.controls)
            val stop = model.stops.firstOrNull { it.id == model.selected }
            if (!mesh && stop != null) {
                CanonRow("Position", flush = true) {
                    CanonDecimalField(Math.round(stop.position * 100).toDouble(), { model.edit { model.setPosition(stop.id, it / 100) } }, unit = "%", step = 1.0, decimals = 0, range = 0.0..100.0)
                }
            }
            CanonGrid(columns = 2) {
                if (!mesh) {
                    CanonButton("Add Stop", icon = Icons.Rounded.Add) {
                        model.select(model.addStop(GradientStops.widestGap(model.stops.map { it.position })))
                    }
                }
                CanonButton("Remove", icon = Icons.Outlined.Delete, enabled = model.canRemove, onClick = model::removeSelected)
                if (!mesh) CanonButton("Auto Step", icon = Icons.Rounded.Stairs, enabled = GradientStops.canAutoStep(model.stops.size), onClick = model::autoStep)
            }
        }
    }

    val imageBlock = CanonSection.of {
        CanonRows {
            CanonHeading("Image")
            CanonSegmented(sizePresets.map { it.label }, sizePresets.indexOfFirst { it.width == model.width && it.height == model.height }, columns = 4) {
                model.width = sizePresets[it].width
                model.height = sizePresets[it].height
            }
            CanonRow("Width", flush = true) { CanonNumberField(model.width, { model.width = it }, range = exportSizes, step = 100, unit = "px") }
            CanonRow("Height", flush = true) { CanonNumberField(model.height, { model.height = it }, range = exportSizes, step = 100, unit = "px") }
            CanonRow("Noise", flush = true) {
                CanonSlider(model.noise, { model.noise = it }, Modifier.fillMaxSize(), range = 0f..100f, step = 1f, readout = "${model.noise.roundToInt()} %")
            }
            Box(contentAlignment = Alignment.CenterEnd) {
                CanonButton("Export PNG", kind = CanonButtonKind.Primary, enabled = !model.exporting, icon = Icons.Outlined.Share, oversized = true) {
                    model.export(context)
                }
                if (model.exporting) CanonLoader(Modifier.padding(end = CanonMetrics.panelPadding))
            }
        }
    }

    val code = remember { arrayOf("", "") }
    if (!model.dragging) {
        code[1] = model.shape?.let { GradientPaint.swiftUI(it, model.paintStops, model.interpolation, model.width.toDouble(), model.height.toDouble()) }.orEmpty()
        code[0] = model.css
    }
    val cssBlock = CanonSection.of {
        CanonRows {
            CanonCodeBlock(code[0], language = "CSS")
            if (mesh) CanonStatusRow("Approximate", CanonTone.Muted)
        }
    }
    val swiftBlock = CanonSection.of { CanonRows { CanonCodeBlock(code[1], language = "SwiftUI") } }

    // ponytail: the solid canvas stays square; iOS flattens it on wide panes
    CanonSplit(
        sidebar = listOf(canvas),
        main = listOf(settings, solidCanvas, stopsBlock, imageBlock, cssBlock) + if (mesh) emptyList() else listOf(swiftBlock),
        modifier = model.grouping.modifier,
        halves = true,
        collapsesTo = 240.dp,
    )
}

private sealed interface GradientRail {
    data class Line(val from: Offset, val to: Offset) : GradientRail

    /** Clockwise from `start` degrees, 0 up. */
    data class Ring(val centre: Offset, val radius: Float, val start: Double) : GradientRail

    fun point(t: Double): Offset = when (this) {
        is Line -> from + (to - from) * t.toFloat()
        is Ring -> Math.toRadians(start + t * 360).let { Offset(centre.x + radius * sin(it).toFloat(), centre.y - radius * cos(it).toFloat()) }
    }

    /** Unit vector off the rail at `t`: left of the line, or out of the ring. */
    fun normal(t: Double): Offset = when (this) {
        is Line -> {
            val length = max(1f, (to - from).getDistance())
            Offset(-(to.y - from.y) / length, (to.x - from.x) / length)
        }
        is Ring -> Math.toRadians(start + t * 360).let { Offset(sin(it).toFloat(), -cos(it).toFloat()) }
    }

    /** Position along the rail (ring: 0..<1), and the signed distance off it along `normal`. */
    fun project(p: Offset): Pair<Double, Float> = when (this) {
        is Line -> {
            val dx = to.x - from.x
            val dy = to.y - from.y
            val lengthSquared = max(1f, dx * dx + dy * dy)
            val t = ((p.x - from.x) * dx + (p.y - from.y) * dy) / lengthSquared
            t.toDouble() to ((p.x - from.x) * -dy + (p.y - from.y) * dx) / sqrt(lengthSquared)
        }
        is Ring -> {
            val degrees = Math.toDegrees(atan2((p.x - centre.x).toDouble(), -(p.y - centre.y).toDouble()))
            var t = (degrees - start) / 360
            t -= kotlin.math.floor(t)
            t to (hypot(p.x - centre.x, p.y - centre.y) - radius)
        }
    }
}

private sealed interface GradientGrab {
    data class Stop(val id: Long, val offset: Offset) : GradientGrab
    data class Point(val id: Long, val offset: Offset) : GradientGrab
    data object Turn : GradientGrab
    data object Seam : GradientGrab
    data class Centre(val offset: Offset) : GradientGrab
}

private class GradientDrag {
    var grab by mutableStateOf<GradientGrab?>(null)
    var pulled by mutableStateOf<GradientStop?>(null)
    var pulledPoint by mutableStateOf<MeshPoint?>(null)
    var lifted by mutableStateOf(Offset.Zero)
    var tetherFrom by mutableStateOf<Offset?>(null)
    var figure by mutableStateOf<Pair<String, String>?>(null)
    var figureNear by mutableStateOf(Offset.Zero)
    var lastRingT = 0.0
    var snapped = false
    var turnFrom = 0.0 to 0.0
}

private fun bearing(from: Offset, to: Offset) = Math.toDegrees(atan2((to.x - from.x).toDouble(), -(to.y - from.y).toDouble()))

/**
 * GradientPicture: the gradient at the export's aspect with its stops as handles. Handles drag along the CSS line
 * (linear), centre-to-corner ray (radial) or ring (conic); pulled off, a stop is removed on release. A tap adds a
 * stop; the hub or ring knob turns the gradient; a radial or conic centre drags.
 */
@Composable
private fun GradientPicture(model: GradientGeneratorModel, modifier: Modifier) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val d = density.density
    val animates = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
    val top by rememberUpdatedState(with(density) { LocalCanonCanvas.current.top.toPx() })
    val drag = remember { GradientDrag() }
    val names = colourNamesOrNull()

    BoxWithConstraints(modifier.clipToBounds()) {
        val bounds = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val margin = 30 * d
        val hintRoom = 22 * d
        val image = run {
            val area = Rect(margin, top + margin, bounds.width - margin, bounds.height - margin - hintRoom)
            if (area.width <= 0 || area.height <= 0) return@run Rect.Zero
            val scale = min(area.width / model.width, area.height / model.height)
            val size = Size((model.width * scale).roundToInt().toFloat(), (model.height * scale).roundToInt().toFloat())
            Rect(Offset((area.center.x - size.width / 2).roundToInt().toFloat(), (area.center.y - size.height / 2).roundToInt().toFloat()), size)
        }
        val imageNow by rememberUpdatedState(image)
        val boundsNow by rememberUpdatedState(bounds)

        fun view(unit: Offset) = Offset(imageNow.left + unit.x * imageNow.width, imageNow.top + unit.y * imageNow.height)
        fun centre() = if (model.type == GradientType.Linear) imageNow.center else view(model.centre)
        fun inside(p: Offset) = Offset(p.x.coerceIn(16 * d, max(16 * d, boundsNow.width - 16 * d)), p.y.coerceIn(16 * d, max(16 * d, boundsNow.height - 16 * d)))
        fun rail(): GradientRail {
            val img = imageNow
            return when (model.type) {
                GradientType.Linear, GradientType.Mesh -> {
                    val l = GradientPaint.line(model.angle, img.width.toDouble(), img.height.toDouble())
                    GradientRail.Line(img.topLeft + Offset(l[0].toFloat(), l[1].toFloat()), img.topLeft + Offset(l[2].toFloat(), l[3].toFloat()))
                }
                GradientType.Radial -> {
                    val c0 = centre()
                    GradientRail.Line(c0, listOf(img.topLeft, img.topRight, img.bottomLeft, img.bottomRight).maxBy { (it - c0).getDistance() })
                }
                GradientType.Conic -> {
                    val c0 = centre()
                    val edge = minOf(c0.x, boundsNow.width - c0.x, c0.y - top, boundsNow.height - c0.y)
                    GradientRail.Ring(c0, max(28 * d, min(min(img.width, img.height) * 0.32f, edge - 60 * d)), model.angle)
                }
            }
        }
        // tether avoids stops
        fun hubPoint(rail: GradientRail): Offset {
            val away = rail.normal(0.0)
            val reach = 44 * d
            val c0 = centre()
            return when (model.type) {
                GradientType.Linear -> inside(c0 + away * reach)
                GradientType.Radial -> inside(Offset(c0.x - away.y * reach, c0.y + away.x * reach))
                else -> c0
            }
        }
        fun knobPoint(rail: GradientRail.Ring) = Math.toRadians(rail.start).let { rail.centre + Offset(sin(it).toFloat(), -cos(it).toFloat()) * (rail.radius + 38 * d) }
        fun handlePoints(): Map<Long, Offset> {
            val rail = rail()
            val grab = drag.grab
            val shown = if (model.isMesh) model.meshPoints.associate { it.id to view(Offset(it.x, it.y)) }
            else model.stops.associate { it.id to inside(rail.point(it.position)) }
            val held = (grab as? GradientGrab.Stop)?.id ?: (grab as? GradientGrab.Point)?.id
            return if (held != null && (held in shown || drag.pulled?.id == held || drag.pulledPoint?.id == held)) shown + (held to drag.lifted) else shown
        }

        fun showFigure(text: String, unit: String, near: Offset) {
            drag.figure = text to unit
            drag.figureNear = near
        }

        fun target(at: Offset): GradientGrab? {
            val reach = 24 * d
            val near = handlePoints().filter { (it.value - at).getDistance() < reach }
                .minByOrNull { if (it.key == model.selected) -1f else (it.value - at).getDistance() }
            if (near != null) return if (model.isMesh) GradientGrab.Point(near.key, at - near.value) else GradientGrab.Stop(near.key, at - near.value)
            val rail = rail()
            if (rail is GradientRail.Ring && (knobPoint(rail) - at).getDistance() < reach) return GradientGrab.Seam
            if (!model.isMesh && (hubPoint(rail) - at).getDistance() < reach) {
                return if (model.type == GradientType.Linear) GradientGrab.Turn else GradientGrab.Centre(at - centre())
            }
            return null
        }

        fun canRemoveHeld() = if (model.isMesh) model.meshPoints.size + (if (drag.pulledPoint == null) 0 else 1) > 2
        else model.stops.size + (if (drag.pulled == null) 0 else 1) > 2

        fun turn(point: Offset) {
            val c0 = centre()
            if ((point - c0).getDistance() <= 8 * d) return
            val turned = drag.turnFrom.first + bearing(c0, point) - drag.turnFrom.second
            var degrees = (turned % 360 + 360) % 360
            val nearest = Math.round(degrees / 45) * 45.0
            val snaps = abs(degrees - nearest) < SNAP
            if (snaps) degrees = nearest % 360
            if (snaps != drag.snapped) {
                drag.snapped = snaps
                if (snaps) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            model.angle = Math.round(degrees).toDouble()
            showFigure("${model.angle.toInt()}", "°", point)
        }

        fun moveStop(id: Long, point: Offset) {
            val rail = rail()
            var (t, off) = rail.project(point)
            if (rail is GradientRail.Ring) {
                t = (t + Math.round(drag.lastRingT - t)).coerceIn(0.0, 1.0)
                drag.lastRingT = t
            } else {
                t = t.coerceIn(0.0, 1.0)
            }
            val removing = abs(off) > 64 * d && canRemoveHeld()
            val held = model.stops.firstOrNull { it.id == id }
            if (removing && drag.pulled == null && held != null) {
                drag.pulled = held
                model.stops = model.stops.filter { it.id != id }
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } else if (!removing && drag.pulled != null) {
                model.stops = model.stops + drag.pulled!!
                drag.pulled = null
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            model.setPosition(id, t)
            drag.pulled = drag.pulled?.copy(position = t)
            val lift = if (canRemoveHeld()) max(0f, abs(off) - 16 * d) else min(max(0f, abs(off) - 16 * d), 10 * d)
            val base = inside(rail.point(t))
            val side = if (off < 0) -1f else 1f
            drag.lifted = inside(base + rail.normal(t) * (lift * side))
            drag.tetherFrom = if (lift > 0) base else null
            if (drag.pulled == null) showFigure("${Math.round(t * 100)}", "%", drag.lifted) else drag.figure = null
        }

        fun movePoint(id: Long, point: Offset) {
            val img = imageNow
            val outside = hypot(maxOf(img.left - point.x, 0f, point.x - img.right), maxOf(img.top - point.y, 0f, point.y - img.bottom))
            val removing = outside > 36 * d && canRemoveHeld()
            val held = model.meshPoints.firstOrNull { it.id == id }
            if (removing && drag.pulledPoint == null && held != null) {
                drag.pulledPoint = held
                model.meshPoints = model.meshPoints.filter { it.id != id }
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } else if (!removing && drag.pulledPoint != null) {
                model.meshPoints = model.meshPoints + drag.pulledPoint!!
                drag.pulledPoint = null
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            val unit = Offset(((point.x - img.left) / max(1f, img.width)).coerceIn(0f, 1f), ((point.y - img.top) / max(1f, img.height)).coerceIn(0f, 1f))
            model.meshPoints = model.meshPoints.map { if (it.id == id) it.copy(x = unit.x, y = unit.y) else it }
            model.renderMeshFast()
            drag.lifted = inside(point)
            val base = view(unit)
            drag.tetherFrom = if ((drag.lifted - base).getDistance() > 2 * d) base else null
            if (drag.pulledPoint == null) showFigure("${(unit.x * 100).roundToInt()} %, ${(unit.y * 100).roundToInt()} %", "", drag.lifted) else drag.figure = null
        }

        fun move(point: Offset) {
            when (val grab = drag.grab ?: return) {
                is GradientGrab.Stop -> moveStop(grab.id, point - grab.offset)
                is GradientGrab.Point -> movePoint(grab.id, point - grab.offset)
                GradientGrab.Turn, GradientGrab.Seam -> turn(point)
                is GradientGrab.Centre -> {
                    val img = imageNow
                    var unit = Offset(
                        ((point.x - grab.offset.x - img.left) / max(1f, img.width)).coerceIn(0f, 1f),
                        ((point.y - grab.offset.y - img.top) / max(1f, img.height)).coerceIn(0f, 1f),
                    )
                    val snaps = abs(unit.x - 0.5f) < 0.02f && abs(unit.y - 0.5f) < 0.02f
                    if (snaps) unit = centred
                    if (snaps != drag.snapped) {
                        drag.snapped = snaps
                        if (snaps) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    model.centre = unit
                    showFigure("${(unit.x * 100).roundToInt()} %, ${(unit.y * 100).roundToInt()} %", "", view(unit))
                }
            }
        }

        fun begin(found: GradientGrab, point: Offset) {
            model.pushUndo()
            drag.grab = found
            drag.snapped = false
            model.dragging = true
            when (found) {
                is GradientGrab.Stop -> {
                    model.select(found.id)
                    drag.lastRingT = model.stops.firstOrNull { it.id == found.id }?.position ?: 0.0
                }
                is GradientGrab.Point -> model.select(found.id)
                GradientGrab.Turn, GradientGrab.Seam -> drag.turnFrom = model.angle to bearing(centre(), point)
                is GradientGrab.Centre -> {}
            }
            move(point)
        }

        fun end(cancelled: Boolean) {
            if (drag.grab == null) return
            if (cancelled) {
                drag.pulled?.let { model.stops = model.stops + it }
                drag.pulledPoint?.let { model.meshPoints = model.meshPoints + it }
            }
            val removed = drag.pulled != null || drag.pulledPoint != null
            drag.pulled = null
            drag.pulledPoint = null
            drag.grab = null
            drag.tetherFrom = null
            drag.figure = null
            model.dragging = false
            if (removed && !cancelled) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                val present = if (model.isMesh) model.meshPoints.any { it.id == model.selected } else model.stops.any { it.id == model.selected }
                if (!present) model.select(if (model.isMesh) model.meshPoints.firstOrNull()?.id else model.sortedStops.firstOrNull()?.id)
            }
        }

        fun tapped(point: Offset) {
            when (val found = target(point)) {
                is GradientGrab.Stop -> return model.select(found.id)
                is GradientGrab.Point -> return model.select(found.id)
                null -> {}
                else -> return
            }
            val img = imageNow
            if (!img.inflate(margin).contains(point)) return
            if (model.isMesh) {
                model.select(model.addMeshPoint(((point.x - img.left) / img.width).coerceIn(0f, 1f), ((point.y - img.top) / img.height).coerceIn(0f, 1f)))
            } else {
                val rail = rail()
                var t = rail.project(point).first.coerceIn(0.0, 1.0)
                if (rail is GradientRail.Ring) t %= 1
                model.select(model.addStop(t))
            }
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }

        val rail = rail()
        val targets = handlePoints()
        val dragging = drag.grab != null
        val shownPoints = targets.mapValues { (id, target) ->
            key(id) {
                val spec = if (dragging || !animates) snap<Offset>() else spring(dampingRatio = 0.8f, stiffness = 300f)
                animateOffsetAsState(target, spec).value
            }
        }
        val hintAlpha by animateFloatAsState(if (dragging) 0f else 1f, tween(200))
        val meshImage = model.meshImage
        val ramp = GradientPaint.ramp(model.paintStops, model.interpolation)
        val shape = model.shape
        val grain = model.noise / 100f

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val found = target(down.position)
                        val moved = if (found == null) null else awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        if (found == null || moved == null) {
                            if (found == null) {
                                val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                                if ((up.position - down.position).getDistance() < viewConfiguration.touchSlop) tapped(up.position)
                            } else {
                                tapped(down.position)
                            }
                            return@awaitEachGesture
                        }
                        begin(found, down.position)
                        move(moved.position)
                        val finished = drag(moved.id) { change ->
                            change.consume()
                            move(change.position)
                        }
                        end(cancelled = !finished)
                    }
                },
        ) {
            if (image.width <= 0) return@Canvas
            drawRect(c.border, image.topLeft - Offset(d, d), Size(image.width + 2 * d, image.height + 2 * d))
            if (shape == null) {
                if (meshImage != null) {
                    drawImage(
                        meshImage,
                        dstOffset = IntOffset(image.left.roundToInt(), image.top.roundToInt()),
                        dstSize = IntSize(image.width.roundToInt(), image.height.roundToInt()),
                        filterQuality = FilterQuality.Low,
                    )
                }
            } else {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.translate(image.left, image.top)
                    native.clipRect(0f, 0f, image.width, image.height)
                    GradientDrawing.gradient(native, shape, ramp, image.width, image.height)
                    GradientDrawing.grain(native, grain, image.width, image.height)
                    native.restore()
                }
            }
            drawLines(model.type, rail, centre(), hubPoint(rail), drag.tetherFrom.takeIf { dragging }, drag.lifted, d)
            if (!model.isMesh) drawHub(if (model.type == GradientType.Linear) HubMark.Needle else HubMark.Centre, hubPoint(rail), if (model.type == GradientType.Linear) model.angle else 0.0, d)
            if (rail is GradientRail.Ring) drawHub(HubMark.Knob, knobPoint(rail), rail.start, d)
            val colours = if (model.isMesh) (model.meshPoints + listOfNotNull(drag.pulledPoint)).associate { it.id to it.colour }
            else (model.stops + listOfNotNull(drag.pulled)).associate { it.id to it.colour }
            val held = drag.pulled?.id ?: drag.pulledPoint?.id
            for ((id, point) in shownPoints.entries.sortedBy { it.key == model.selected }) {
                drawHandle(colours[id] ?: continue, point, id == model.selected, id == held, d)
            }
        }

        // talkback: element per handle
        for ((id, point) in shownPoints) {
            val stop = model.stops.firstOrNull { it.id == id }
            val colour = stop?.colour ?: model.meshPoints.firstOrNull { it.id == id }?.colour ?: continue
            Box(
                Modifier
                    .layout { measurable, _ ->
                        val side = (24 * d).roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(side, side))
                        layout(0, 0) { placeable.place((point.x - side / 2).roundToInt(), (point.y - side / 2).roundToInt()) }
                    }
                    .semantics {
                        contentDescription = names?.closest(colour) ?: colour.hex
                        selected = id == model.selected
                        onClick { model.select(id); true }
                        if (stop != null) {
                            stateDescription = "${Math.round(stop.position * 100)} %"
                            progressBarRangeInfo = ProgressBarRangeInfo(stop.position.toFloat(), 0f..1f, 99)
                            setProgress {
                                model.pushUndo()
                                model.setPosition(id, it.toDouble())
                                true
                            }
                        }
                    },
            )
        }

        Text(
            "Tap to add a stop, drag one off to remove it",
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, (image.bottom + 6 * d).roundToInt()) }
                }
                .alpha(hintAlpha),
            style = CanonText.caption,
            color = c.mutedForeground,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        drag.figure?.let { (text, unit) ->
            val below = drag.figureNear.y <= image.center.y
            Box(Modifier.fillMaxSize(), contentAlignment = if (below) Alignment.BottomCenter else Alignment.TopCenter) {
                val gap = with(density) { (if (below) bounds.height - image.bottom + 10 * d else image.top + 10 * d).toDp() }
                CanonFigure(text, Modifier.padding(top = if (below) 0.dp else gap, bottom = if (below) gap else 0.dp), unit = unit, size = 28.sp, halo = c.card)
            }
        }
    }
}

private fun DrawScope.shadowed(path: Path, d: Float, width: Float = 1.25f, shade: Float = 3f) {
    drawPath(path, Color.Black.copy(alpha = 0.3f), style = Stroke(shade * d, cap = StrokeCap.Round))
    drawPath(path, Color.White, style = Stroke(width * d, cap = StrokeCap.Round))
}

private fun DrawScope.drawLines(type: GradientType, rail: GradientRail, centre: Offset, grip: Offset, tetherFrom: Offset?, lifted: Offset, d: Float) {
    val path = Path()
    when (rail) {
        is GradientRail.Line -> {
            if (type != GradientType.Mesh) {
                path.moveTo(rail.from.x, rail.from.y)
                path.lineTo(rail.to.x, rail.to.y)
            }
            if (type == GradientType.Radial || type == GradientType.Linear) {
                val length = max(1f, (grip - centre).getDistance())
                val end = grip - (grip - centre) / length * (9 * d)
                path.moveTo(centre.x, centre.y)
                path.lineTo(end.x, end.y)
            }
        }
        is GradientRail.Ring -> {
            path.addOval(Rect(rail.centre, rail.radius))
            val radians = Math.toRadians(rail.start)
            path.moveTo(rail.centre.x, rail.centre.y)
            path.lineTo(rail.centre.x + (rail.radius + 26 * d) * sin(radians).toFloat(), rail.centre.y - (rail.radius + 26 * d) * cos(radians).toFloat())
        }
    }
    if (tetherFrom != null) {
        path.moveTo(tetherFrom.x, tetherFrom.y)
        path.lineTo(lifted.x, lifted.y)
    }
    shadowed(path, d)
}

private enum class HubMark { Needle, Centre, Knob }

private fun DrawScope.drawHub(mark: HubMark, at: Offset, angle: Double, d: Float) {
    val path = Path()
    fun circle(r: Float) = path.addOval(Rect(at, r * d))
    fun line(x0: Float, y0: Float, x1: Float, y1: Float) {
        path.moveTo(at.x + x0 * d, at.y + y0 * d)
        path.lineTo(at.x + x1 * d, at.y + y1 * d)
    }
    when (mark) {
        HubMark.Knob -> {
            circle(9f)
            line(0f, 9f, 0f, 14f)
        }
        HubMark.Centre -> {
            circle(8f)
            for ((dx, dy) in listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f)) line(dx * 11, dy * 11, dx * 16, dy * 16)
        }
        HubMark.Needle -> {
            circle(7f)
            line(0f, -7f, 0f, -20f)
            line(-5f, -15f, 0f, -20f)
            line(0f, -20f, 5f, -15f)
        }
    }
    rotate(angle.toFloat(), at) { shadowed(path, d, width = 2f, shade = 4f) }
}

private fun DrawScope.drawHandle(colour: Rgb, at: Offset, chosen: Boolean, pulled: Boolean, d: Float) {
    val half = 12 * d
    val alpha = if (pulled) 0.45f else 1f
    withTransform({ scale(if (chosen) 1.3f else 1f, if (chosen) 1.3f else 1f, at) }) {
        drawRect(Color.Black.copy(alpha = 0.25f * alpha), at - Offset(half + d, half + d), Size((half + d) * 2, (half + d) * 2))
        drawRect(colour.toCanonColor(), at - Offset(half, half), Size(half * 2, half * 2), alpha = alpha)
        drawRect(Color.White, at - Offset(half - 1.25f * d, half - 1.25f * d), Size((half - 1.25f * d) * 2, (half - 1.25f * d) * 2), alpha = alpha, style = Stroke(2.5f * d))
        if (chosen) drawRect(Color.White, at - Offset(half + 4 * d, half + 4 * d), Size((half + 4 * d) * 2, (half + 4 * d) * 2), alpha = alpha, style = Stroke(d))
    }
}

// dt-ios GradientAngleDial
@Composable
private fun GradientAngleDial(value: Double, onChange: (Double) -> Unit) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val current by rememberUpdatedState(onChange)
    val shown by rememberUpdatedState(value)
    val snapped = remember { mutableStateOf(false) }
    fun set(degrees: Double) {
        val next = (degrees + 360) % 360
        if (next != shown) current(next)
    }
    fun point(at: Offset, size: IntSize) {
        val dx = at.x - size.width / 2f
        val dy = at.y - size.height / 2f
        if (hypot(dx, dy) <= 4 * view.resources.displayMetrics.density) return
        var degrees = (Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())) + 360) % 360
        val nearest = Math.round(degrees / 45) * 45.0
        val snaps = abs(degrees - nearest) < SNAP
        if (snaps) degrees = nearest % 360
        if (snaps != snapped.value) {
            snapped.value = snaps
            if (snaps) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        set(Math.round(degrees).toDouble())
    }
    Canvas(
        Modifier
            .width(72.dp)
            .fillMaxHeight()
            .heightIn(min = 60.dp)
            .semantics {
                contentDescription = "Angle"
                stateDescription = "${Math.round(value)}°"
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 0f..359f)
                setProgress { set(Math.round(it / 15.0) * 15.0); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    point(down.position, size)
                    drag(down.id) { change ->
                        change.consume()
                        point(change.position, size)
                    }
                }
            },
    ) {
        val d = density
        val centre = center
        val face = Path().apply {
            addOval(Rect(centre, 21 * d))
            for (tick in 0 until 8) {
                val radians = tick * Math.PI / 4
                val inner = if (tick % 2 == 0) 15f else 17f
                moveTo(centre.x + inner * d * sin(radians).toFloat(), centre.y - inner * d * cos(radians).toFloat())
                lineTo(centre.x + 20 * d * sin(radians).toFloat(), centre.y - 20 * d * cos(radians).toFloat())
            }
        }
        drawPath(face, c.mutedForeground, style = Stroke(1 * d))
        rotate(value.toFloat(), centre) {
            drawCircle(c.primary, 3 * d, centre)
            drawLine(c.primary, centre, centre - Offset(0f, 17 * d), 2 * d, StrokeCap.Round)
        }
    }
}
