package com.delphi.delphitools.tools.print

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonJumpHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val QUERY_DELAY_MS = 400L
private const val COLUMNS = 3
private val glyphBox = 40.dp
private val cellMono = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp)
private val badgeMono = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
private val legendStyle = CanonText.footnoteSemibold.copy(fontWeight = FontWeight.Bold)

// CASpringAnimation stiffness 180, damping 22
private val sheetSpring = spring<Float>(dampingRatio = 0.82f, stiffness = 180f)
private val sheetRectSpring = spring<Rect>(dampingRatio = 0.82f, stiffness = 180f)
private val badgeSpring = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)

@Composable
private fun slotColours() = LocalDelphiColours.current.let { listOf(it.primary, it.foreground) }

class PaperSizesModel : ViewModel() {
    val slots = mutableStateListOf(PaperPick.named("A4"), null)
    private var nextSlot = 2
    var findText by mutableStateOf("")
        private set
    var query by mutableStateOf<PaperQuery>(PaperQuery.None)
        private set
    var unit by mutableStateOf(PaperUnit.Mm)
    var dpiIndex by mutableIntStateOf(2)
    var arrangement by mutableStateOf(PaperArrangement.Stacked)
        private set
    private var typing: Job? = null

    val dpi get() = (query as? PaperQuery.Dimensions)?.dpi ?: PaperUnit.dpis[dpiIndex]
    val marks get() = slots.withIndex().mapNotNull { (index, pick) -> pick?.let { it.key to index + 1 } }.toMap()

    fun find(text: String) {
        findText = text
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(QUERY_DELAY_MS)
            query = PaperQuery.parse(text)
        }
    }

    /** Fills slot 1, then 2, then 1. */
    fun pick(pick: PaperPick) {
        slots[nextSlot - 1] = pick
        nextSlot = if (nextSlot == 1) 2 else 1
    }

    fun cycle() {
        if (slots.count { it != null } < 2) return
        arrangement = PaperArrangement.entries[(arrangement.ordinal + 1) % PaperArrangement.entries.size]
    }
}

@Composable
fun PaperSizes(model: PaperSizesModel = viewModel()) {
    val c = LocalDelphiColours.current
    val unit = model.unit
    val dpi = model.dpi
    val marks = model.marks
    val query = model.query

    val picture = CanonSection.canvas(CanonCanvasSize.Aspect(0.8f), background = c.muted, maxHeight = 640.dp) {
        PaperSizesPicture(model.slots.map { it?.size }, unit, dpi, model.arrangement, model::cycle)
    }

    val find = CanonSection.of {
        CanonRows {
            CanonHeading("Find")
            CanonTextCell(model.findText, model::find, Modifier.fillMaxWidth(), placeholder = "A4 · 210×297 · 2480×3508@300dpi")
        }
    }

    val units = CanonSection.of {
        CanonRows {
            CanonHeading("Units")
            CanonSegmented(PaperUnit.entries.map { it.label }, unit.ordinal) { model.unit = PaperUnit.entries[it] }
            if (unit == PaperUnit.Pixels) {
                CanonSegmented(PaperUnit.dpis.map { "${it.toInt()} DPI" }, model.dpiIndex) { model.dpiIndex = it }
            }
        }
    }

    val matches = (query as? PaperQuery.Dimensions)?.let { PaperQuery.closest(it.width, it.height) }.orEmpty()
    val closest = CanonSection.of {
        CanonRows {
            CanonHeading("Closest")
            CanonGrid(COLUMNS) {
                matches.forEach { match ->
                    PaperSizeCell(match.pick.size, unit.text(match.pick.size, dpi), marks[match.pick.key], "${sign(match.dw)} × ${sign(match.dh)} mm") {
                        model.pick(match.pick)
                    }
                }
            }
        }
    }

    val series = CanonSection.of {
        CanonRows {
            PaperSizeDatabase.allSeries.forEach { data ->
                val picks = data.sizes.map { PaperPick(data.id, it) }.filter(query::matches)
                if (picks.isEmpty()) return@forEach
                CanonJumpHeading(data.name, detail = "${data.sizes.size}")
                CanonGrid(COLUMNS) {
                    picks.forEach { pick ->
                        PaperSizeCell(pick.size, unit.text(pick.size, dpi), marks[pick.key]) { model.pick(pick) }
                    }
                }
            }
        }
    }

    CanonSplit(
        sidebar = listOf(picture),
        main = listOfNotNull(find, units, closest.takeIf { matches.isNotEmpty() }, series),
        halves = true,
    )
}

private fun sign(value: Double): String {
    val mm = value.roundToInt()
    return when {
        mm > 0 -> "+$mm"
        mm < 0 -> "−${-mm}"
        else -> "0"
    }
}

@Composable
private fun PaperSizeCell(size: PaperSize, text: String, mark: Int?, detail: String? = null, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    val colour = mark?.let { slotColours()[(it - 1) % 2] }
    val long = maxOf(size.widthMM, size.heightMM, 1.0)
    val badge = remember { Animatable(1f) }
    val previous = remember { intArrayOf(mark ?: 0) }
    LaunchedEffect(mark) {
        val changed = (mark ?: 0) != previous[0]
        previous[0] = mark ?: 0
        if (mark == null || !changed) return@LaunchedEffect
        // badge pops
        badge.snapTo(0.3f)
        badge.animateTo(1f, badgeSpring)
    }
    Box(
        Modifier
            .canonClickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = size.name
                stateDescription = text
                selected = mark != null
                role = Role.Button
            }
            .background(c.card),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.size(glyphBox)) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .size(
                            (glyphBox.value * size.widthMM / long).roundToInt().dp,
                            (glyphBox.value * size.heightMM / long).roundToInt().dp,
                        )
                        .background(colour?.copy(alpha = 0.15f) ?: c.background)
                        .border(1.dp, colour ?: c.mutedForeground),
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(size.name, style = CanonText.bodySemibold, color = c.foreground)
            Text(text, style = cellMono, color = c.mutedForeground, maxLines = 2)
            if (detail != null) Text(detail, style = CanonText.captionSemibold, color = c.primary)
        }
        if (mark != null && colour != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(20.dp)
                    .graphicsLayer {
                        scaleX = badge.value
                        scaleY = badge.value
                    }
                    .background(colour),
                contentAlignment = Alignment.Center,
            ) {
                Text("$mark", style = badgeMono, color = if (mark == 2) c.background else c.primaryForeground)
            }
        }
    }
}

/** A shape's frame and opacity, springing to each new target; one that is hidden, or does not morph, appears in place. */
private class Shape(rect: Rect, opacity: Float) {
    val rect = Animatable(rect, Rect.VectorConverter)
    val alpha = Animatable(opacity)
}

@Composable
private fun rememberShape(target: Rect?, opacity: Float, animate: Boolean, morphs: Boolean = true): Shape {
    val shape = remember { Shape(target ?: Rect.Zero, opacity) }
    LaunchedEffect(target, opacity, morphs, animate) {
        val goal = target ?: shape.rect.value
        if (!animate) {
            shape.rect.snapTo(goal)
            shape.alpha.snapTo(opacity)
            return@LaunchedEffect
        }
        if (!morphs || shape.alpha.value == 0f) {
            shape.rect.snapTo(goal)
            shape.alpha.snapTo(0f)
        } else {
            launch { shape.rect.animateTo(goal, sheetRectSpring) }
        }
        shape.alpha.animateTo(opacity, sheetSpring)
    }
    return shape
}

/** Up to two sheets at one scale with a legend and their area ratio; a tap cycles stacked, side by side and above-below. */
@Composable
private fun PaperSizesPicture(
    sizes: List<PaperSize?>,
    unit: PaperUnit,
    dpi: Double,
    arrangement: PaperArrangement,
    onCycle: () -> Unit,
) {
    val c = LocalDelphiColours.current
    val colours = slotColours()
    val density = LocalDensity.current
    val flipped = LocalLayoutDirection.current == LayoutDirection.Rtl
    val top = LocalCanonCanvas.current.top + 14.dp
    val measurer = rememberTextMeasurer()
    val legends = sizes.mapIndexed { index, size -> size?.let { "${index + 1} · ${it.name} · ${unit.text(it, dpi)}" } }
    val present = sizes.withIndex().mapNotNull { (index, size) -> size?.let { index to Size(it.widthMM.toFloat(), it.heightMM.toFloat()) } }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onCycle)
            .clearAndSetSemantics {
                contentDescription = legends.filterNotNull().joinToString(", ")
                role = Role.Button
            },
    ) {
        val area: Rect
        val inner: Rect
        with(density) {
            val pad = CanonMetrics.panelPadding.toPx()
            inner = Rect(pad, top.toPx(), constraints.maxWidth - pad, constraints.maxHeight - 18.dp.toPx())
            val line = measurer.measure("1", legendStyle).size.height
            val legendHeight = legends.count { it != null } * (line + 2.dp.toPx())
            val areaTop = maxOf(inner.top + legendHeight + 16.dp.toPx(), inner.top + 48.dp.toPx())
            area = Rect(inner.left, areaTop, inner.right, maxOf(areaTop, inner.bottom))
        }
        // resizing lands at once
        val lastArea = remember { arrayOf(area) }
        val animate = lastArea[0] == area
        lastArea[0] = area

        val frames = PaperGeometry.frames(present.map { it.second }, arrangement, area, flipped)
        val apart = present.size == 2 && arrangement != PaperArrangement.Stacked
        val guidesDown = arrangement == PaperArrangement.AboveBelow
        val lastGuidesDown = remember { booleanArrayOf(guidesDown) }
        val guidesMorph = lastGuidesDown[0] == guidesDown
        lastGuidesDown[0] = guidesDown

        val sheets = (0..1).map { index ->
            val slot = present.indexOfFirst { it.first == index }
            val frame = frames.getOrNull(slot)
            val sheet = rememberShape(frame, if (frame == null) 0f else 1f, animate)
            // far edges carried across
            val guide = frame?.let {
                if (guidesDown) {
                    val edge = if (flipped) it.left else it.right
                    Rect(edge, area.top, edge, area.bottom)
                } else {
                    Rect(area.left, it.top, area.right, it.top)
                }
            }
            val line = rememberShape(guide, if (frame != null && apart) 0.22f else 0f, animate, guidesMorph)
            sheet to line
        }
        val ghost = PaperGeometry.frames(listOf(PaperGeometry.reference), PaperArrangement.Stacked, area, flipped).first()
        val areas = sizes.map { (it?.widthMM ?: 0.0) * (it?.heightMM ?: 0.0) }
        // larger behind
        val order = if (areas[0] >= areas[1]) listOf(0, 1) else listOf(1, 0)

        Canvas(Modifier.fillMaxSize()) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()))
            if (present.isEmpty()) {
                drawRect(
                    c.mutedForeground.copy(alpha = 0.5f),
                    ghost.topLeft,
                    ghost.size,
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
                )
            }
            sheets.forEachIndexed { index, (_, guide) ->
                val alpha = guide.alpha.value
                if (alpha <= 0f) return@forEachIndexed
                val r = guide.rect.value
                drawLine(colours[index].copy(alpha = alpha), r.topLeft, r.bottomRight, 1.dp.toPx(), pathEffect = if (index == 1) dash else null)
            }
            order.forEach { index ->
                val sheet = sheets[index].first
                val alpha = sheet.alpha.value
                if (alpha <= 0f) return@forEach
                drawSheet(sheet.rect.value, colours[index], alpha, if (index == 0) 0.14f else 0.06f, if (index == 1) dash else null)
            }
        }

        Column(
            Modifier
                .padding(start = CanonMetrics.panelPadding, top = top)
                .width(with(density) { maxOf(0f, inner.width - 110.dp.toPx()).toDp() }),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            legends.forEachIndexed { index, legend ->
                if (legend != null) Text(legend, style = legendStyle, color = colours[index], maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (present.isEmpty()) {
            Box(
                Modifier
                    .offset { IntOffset(area.left.roundToInt(), area.top.roundToInt()) }
                    .size(with(density) { area.width.toDp() }, with(density) { area.height.toDp() }),
                contentAlignment = Alignment.Center,
            ) {
                Text("Paper", style = CanonText.subheadline.copy(fontWeight = FontWeight.SemiBold), color = c.mutedForeground)
            }
        }

        if (present.size == 2) {
            val shares = present.map { (_, size) -> size.width.toDouble() * size.height }.sorted()
            CanonFigure(
                PaperGeometry.ratio(shares[1] / shares[0]),
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = top - 6.dp, end = CanonMetrics.panelPadding),
                unit = "×",
                size = 34.sp,
                halo = c.muted,
            )
        }
    }
}

private fun DrawScope.drawSheet(rect: Rect, colour: Color, alpha: Float, fill: Float, dash: PathEffect?) {
    drawRect(colour.copy(alpha = fill * alpha), rect.topLeft, rect.size)
    drawRect(colour.copy(alpha = alpha), rect.topLeft, rect.size, style = Stroke(1.5.dp.toPx(), pathEffect = dash))
}
