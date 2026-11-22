package com.delphi.delphitools.tools.typography

import com.delphi.delphitools.tools.TYPING_DELAY_MS
import android.app.Application
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.TextRunShaper
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCopyFeedback
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonJumpGrid
import com.delphi.delphitools.canon.CanonJumpSection
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonRule
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonValueLayout
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val GLYPH_SIDE = 140f
private val collapsedHeight = (GLYPH_SIDE + 16 * 2).dp
private val stackingWidth = 640.dp
private val addressMono = TextStyle(fontFamily = DelphiMono, fontSize = 10.sp, fontWeight = FontWeight.Medium)
private val digitMono = TextStyle(fontFamily = DelphiMono, fontSize = 10.sp, fontWeight = FontWeight.Bold)
private val titleStyle = TextStyle(fontFamily = DelphiFont, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
private val searchMono = TextStyle(fontFamily = DelphiMono, fontSize = 15.sp)
private val nameStyle = CanonText.subheadline.copy(fontWeight = FontWeight.SemiBold)
private val factsStyle = TextStyle(fontFamily = DelphiFont, fontSize = 12.sp)

class GlyphBrowserModel(application: Application) : AndroidViewModel(application) {
    var blocks by mutableStateOf<List<UnicodeBlock>>(emptyList())
        private set
    var sections by mutableStateOf<List<GlyphSection>>(emptyList())
        private set
    var selected by mutableIntStateOf(GlyphEngine.FIRST_GLYPH)
        private set

    /** 1 after a later code point, -1 after an earlier one: the card pushes that way. */
    var direction by mutableIntStateOf(0)
        private set
    var query by mutableStateOf("")
        private set
    var notFound by mutableStateOf(false)
        private set
    var typed = false
        private set

    init {
        val assets = application.assets
        viewModelScope.launch {
            val (blocks, sections) = withContext(Dispatchers.Default) {
                val json = runCatching { assets.open("unicode-blocks.json").bufferedReader().use { it.readText() } }.getOrDefault("[]")
                GlyphEngine.chart(GlyphEngine.parseBlocks(json))
            }
            this@GlyphBrowserModel.blocks = blocks
            this@GlyphBrowserModel.sections = sections
        }
    }

    fun type(text: String) {
        query = text
        notFound = false
        typed = true
    }

    fun select(codePoint: Int) {
        direction = codePoint.compareTo(selected).coerceIn(-1, 1)
        selected = codePoint
    }

    /** The outcome of the query; a block selects its first glyph. */
    fun search(): GlyphEngine.Find? {
        typed = false
        if (blocks.isEmpty() || query.isBlank()) return null
        val found = GlyphEngine.find(query, blocks)
        when (found) {
            is GlyphEngine.Find.CodePoint -> select(found.value)
            is GlyphEngine.Find.Block -> {
                val range = blocks[found.index].range
                select(range.firstOrNull { sections[found.index].shows(it) } ?: range.first)
            }
            null -> notFound = true
        }
        return found
    }
}

@Composable
fun GlyphBrowser(model: GlyphBrowserModel = viewModel()) {
    val c = LocalDelphiColours.current
    val scope = rememberCoroutineScope()
    val chart = remember { GlyphChartState() }
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val statusBar = WindowInsets.statusBars.getTop(density)
    var table by rememberSaveable { mutableStateOf(false) }
    var jumpFrom by rememberSaveable { mutableStateOf<Int?>(null) }
    val blocks = model.blocks
    val block = GlyphEngine.index(model.selected, blocks)?.let { blocks[it].name }

    val clearance = with(density) {
        val stacked = window.width.toDp() < stackingWidth
        statusBar + (CanonMetrics.panelPadding + if (stacked) collapsedHeight + CanonMetrics.frame * 2 else 0.dp).toPx()
    }

    fun search(reveal: Boolean) {
        when (val found = model.search()) {
            is GlyphEngine.Find.CodePoint -> if (reveal) scope.launch { chart.reveal(found.value) }
            is GlyphEngine.Find.Block -> if (reveal) scope.launch { chart.showSection(found.index, clearance, window.height.toFloat()) }
            null -> {}
        }
    }

    LaunchedEffect(model.query) {
        if (!model.typed) return@LaunchedEffect
        delay(TYPING_DELAY_MS)
        search(reveal = false)
    }

    val card = CanonSection.canvas(CanonCanvasSize.Fitting) {
        GlyphCard(model.selected, block, model.direction)
    }
    val find = CanonSection.of {
        CanonRows {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(c.card)) {
                CanonTextCell(
                    model.query,
                    model::type,
                    Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Search" },
                    placeholder = "Block name or U+XXXX…",
                    width = 200.dp,
                    textStyle = searchMono,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    colour = if (model.notFound) c.destructive else null,
                    onEndEditing = { search(reveal = true) },
                )
                CanonRule(vertical = true)
                CanonButton("Table", icon = Icons.Outlined.GridOn) { if (blocks.isNotEmpty()) table = true }
            }
        }
    }
    val glyphs = CanonSection.of {
        CanonRows {
            GlyphChart(chart, model.sections, model.selected, onPick = model::select, onHeading = { jumpFrom = it })
        }
    }

    CanonSplit(sidebar = listOf(card), main = listOf(find, glyphs), halves = true, collapsesTo = collapsedHeight)

    jumpFrom?.let { current ->
        CanonJumpGrid(
            blocks.map { CanonJumpSection(it.name, GlyphEngine.jumpDetail(it)) },
            current,
            onPick = { scope.launch { chart.showSection(it, clearance, window.height.toFloat()) } },
            onDismiss = { jumpFrom = null },
        )
    }
    if (table) {
        GlyphTable(model, onDismiss = { table = false }) { scope.launch { chart.reveal(it) } }
    }
}

// null before android 12
private fun renderingFamily(text: String, paint: Paint): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val glyphs = TextRunShaper.shapeTextRun(text, 0, text.length, 0, text.length, 0f, 0f, false, paint)
    if (glyphs.glyphCount() == 0) return null
    return glyphs.getFont(glyphs.glyphCount() - 1).file?.name?.let(GlyphEngine::familyFromFile)
}

@Composable
private fun GlyphCard(codePoint: Int, block: String?, direction: Int) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val topInset = LocalCanonCanvas.current.top
    val text = GlyphEngine.shown(codePoint)
    val label = GlyphEngine.label(codePoint)
    val name = GlyphEngine.name(codePoint)
    val family = remember(text) { renderingFamily(text, Paint().apply { textSize = 100f }) }
    val facts = listOfNotNull(GlyphEngine.category(codePoint), block).joinToString(" · ")

    fun Modifier.copies(title: String, value: String) = canonClickable { CanonCopyFeedback.copy(view, value) }
        .semantics {
            contentDescription = title
            stateDescription = value
        }

    Layout(
        content = {
            AnimatedContent(
                text,
                transitionSpec = {
                    if (direction == 0) {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                    } else {
                        slideInHorizontally(tween(280)) { it * direction } togetherWith slideOutHorizontally(tween(280)) { -it * direction }
                    }
                },
                modifier = Modifier
                    .background(c.background)
                    .border(CanonMetrics.hairline, c.border)
                    .clipToBounds(),
                label = "glyph",
            ) { shown -> GlyphSquare(shown, Modifier.fillMaxSize()) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, Modifier.copies("Name", name), style = nameStyle, color = c.foreground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                CanonFigure(label, Modifier.copies("Code Point", label).padding(bottom = 4.dp), size = 28.sp)
                Text(facts, style = factsStyle, color = c.mutedForeground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (family != null) Text(family, style = CanonText.caption, color = c.mutedForeground, maxLines = 1)
            }
            CanonRule()
            BoxWithConstraints {
                CanonGrid(if (maxWidth < 460.dp) 2 else if (maxWidth < 900.dp) 3 else 6) {
                    GlyphEngine.valueLines(codePoint).forEach { (title, value) -> CanonValueLine(title, value, layout = CanonValueLayout.Above) }
                }
            }
        },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = name },
    ) { measurables, constraints ->
        val (glyph, words, rule, grid) = measurables
        val width = constraints.maxWidth
        val pad = CanonMetrics.panelPadding.roundToPx()
        val top = topInset.roundToPx()
        val hair = CanonMetrics.hairline.roundToPx()
        val compactSide = GLYPH_SIDE.dp.roundToPx()
        val placedGrid = grid.measure(Constraints.fixedWidth(width))
        val compact = compactSide + pad * 2 + hair + placedGrid.height
        val bounded = constraints.hasBoundedHeight
        val height = if (bounded) constraints.maxHeight else top + compact
        val placedRule = rule.measure(Constraints.fixedWidth(width))
        if (!bounded || height - top < compact + 160.dp.roundToPx()) {
            val placedGlyph = glyph.measure(Constraints.fixed(compactSide, compactSide))
            val wordsX = pad + compactSide + pad
            val placedWords = words.measure(Constraints(maxWidth = maxOf(0, width - pad - wordsX), maxHeight = compactSide))
            layout(width, height) {
                placedGlyph.place(pad, top + pad)
                placedWords.place(wordsX, top + pad + (compactSide - placedWords.height) / 2)
                val ruleY = top + pad + compactSide + pad
                placedRule.place(0, ruleY)
                placedGrid.place(0, ruleY + hair)
            }
        } else {
            val inner = maxOf(0, width - pad * 2)
            val placedWords = words.measure(Constraints(maxWidth = inner))
            val gridTop = height - placedGrid.height
            val room = gridTop - hair - pad - placedWords.height - pad - (top + pad)
            val side = maxOf(compactSide, minOf(inner, room, 460.dp.roundToPx()))
            val placedGlyph = glyph.measure(Constraints.fixed(side, side))
            layout(width, height) {
                placedGlyph.place(pad + (inner - side) / 2, top + pad)
                placedWords.place(pad, top + pad + side + pad)
                placedRule.place(0, gridTop - hair)
                placedGrid.place(0, gridTop)
            }
        }
    }
}

@Composable
private fun GlyphSquare(text: String, modifier: Modifier) {
    val ink = LocalDelphiColours.current.foreground.toArgb()
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    Box(
        modifier
            .semantics { contentDescription = text }
            .drawBehind {
                if (text.isEmpty()) return@drawBehind
                paint.color = ink
                paint.textSize = (size.height * 0.62f).roundToInt().toFloat()
                val bounds = android.graphics.Rect().also { paint.getTextBounds(text, 0, text.length, it) }
                var inkRect = Rect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())
                if (inkRect.isEmpty) {
                    val metrics = paint.fontMetrics
                    inkRect = Rect(0f, metrics.ascent, paint.measureText(text), metrics.descent)
                }
                val scale = minOf(1f, size.width * 0.84f / maxOf(inkRect.width, 1f), size.height * 0.84f / maxOf(inkRect.height, 1f))
                drawIntoCanvas {
                    val canvas = it.nativeCanvas
                    canvas.save()
                    canvas.translate(size.width / 2, size.height / 2)
                    canvas.scale(scale, scale)
                    canvas.translate(-inkRect.center.x, -inkRect.center.y)
                    canvas.drawText(text, 0f, 0f, paint)
                    canvas.restore()
                }
            },
    )
}

/** Where the chart sits, for scrolling a code point or a section into view. */
@Stable
internal class GlyphChartState {
    var geometry: GlyphGeometry? = null
    var density = 1f
    val requester = BringIntoViewRequester()

    suspend fun reveal(codePoint: Int) {
        val geometry = geometry ?: return
        val index = geometry.row(codePoint) ?: return
        val row = geometry.items[index] as GlyphGeometry.Item.Row
        val x = GlyphGeometry.ROW_HEADER + (codePoint - row.start) * geometry.cell
        val top = geometry.tops[index]
        requester.bringIntoView(Rect(x, top - 80, x + geometry.cell, top + geometry.cell + 80).scaled(density))
    }

    /** The section's heading to [clearance] px under the top of a pane [viewport] px high. */
    suspend fun showSection(section: Int, clearance: Float, viewport: Float) {
        val geometry = geometry ?: return
        val index = geometry.heading(section) ?: return
        val top = geometry.tops[index] * density - clearance
        requester.bringIntoView(Rect(0f, top, geometry.width * density, top + viewport))
    }

    fun offset(codePoint: Int): Float? {
        val geometry = geometry ?: return null
        val index = geometry.row(codePoint) ?: return null
        return (geometry.tops[index] + geometry.cell / 2) * density
    }

    private fun Rect.scaled(by: Float) = Rect(left * by, top * by, right * by, bottom * by)
}

/**
 * dt-ios GlyphChart: each section under a heading and a row of column digits, 16 code points to a row (8 when narrow),
 * an address before each row. Only rows near the visible part of the window are drawn.
 */
@Composable
internal fun GlyphChart(
    state: GlyphChartState,
    sections: List<GlyphSection>,
    selected: Int?,
    onPick: (Int) -> Unit,
    onHeading: (Int) -> Unit,
    typeface: Typeface = Typeface.DEFAULT,
    covers: ((Int) -> Boolean)? = null,
) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 64)
    var width by remember { mutableFloatStateOf(0f) }
    var visibleTop by remember { mutableFloatStateOf(0f) }
    var visibleBottom by remember { mutableFloatStateOf(0f) }
    val geometry = remember(sections, width) { if (width > 0f) GlyphGeometry(sections, width) else null }
    state.geometry = geometry
    state.density = density.density
    val glyphPaint = remember(typeface) { Paint(Paint.ANTI_ALIAS_FLAG).also { it.typeface = typeface } }
    val covered = remember(typeface) { HashMap<Int, Boolean>() }
    fun drawn(codePoint: Int, section: GlyphSection) = section.shows(codePoint) && covers?.invoke(codePoint) != false

    Box(
        Modifier
            .fillMaxWidth()
            .height(with(density) { (geometry?.height ?: GlyphGeometry.DIGITS).dp })
            .background(c.card)
            .bringIntoViewRequester(state.requester)
            .onGloballyPositioned {
                width = it.size.width / density.density
                val origin = it.positionInWindow().y
                val bounds = it.boundsInWindow()
                visibleTop = bounds.top - origin
                visibleBottom = bounds.bottom - origin
            }
            .pointerInput(geometry, sections, covers) {
                val laid = geometry ?: return@pointerInput
                detectTapGestures { point ->
                    val x = point.x / density.density
                    val index = laid.item(point.y / density.density) ?: return@detectTapGestures
                    when (val item = laid.items[index]) {
                        is GlyphGeometry.Item.Heading -> if (sections[item.section].title != null) onHeading(item.section)
                        is GlyphGeometry.Item.Row -> {
                            if (x < GlyphGeometry.ROW_HEADER) return@detectTapGestures
                            val column = ((x - GlyphGeometry.ROW_HEADER) / laid.cell).toInt()
                            val codePoint = item.start + column
                            if (column < laid.columns && drawn(codePoint, sections[item.section])) onPick(codePoint)
                        }
                    }
                }
            }
            .semantics {
                contentDescription = "Glyphs"
                if (selected != null) stateDescription = GlyphEngine.label(selected)
            }
            .drawBehind {
                val laid = geometry ?: return@drawBehind
                val margin = maxOf(visibleBottom - visibleTop, 200.dp.toPx())
                val first = laid.item(maxOf(0f, (visibleTop - margin) / density.density)) ?: 0
                val last = laid.item(minOf(laid.height - 0.5f, (visibleBottom + margin) / density.density)) ?: (laid.items.size - 1)
                drawChart(laid, sections, first..last, selected, measurer, glyphPaint, covered, c, covers)
            },
    )
}

private fun DrawScope.drawChart(
    geometry: GlyphGeometry,
    sections: List<GlyphSection>,
    items: IntRange,
    selected: Int?,
    measurer: androidx.compose.ui.text.TextMeasurer,
    glyphPaint: Paint,
    covered: HashMap<Int, Boolean>,
    c: com.delphi.delphitools.ui.theme.DelphiColours,
    covers: ((Int) -> Boolean)?,
) {
    val unit = density
    val cell = geometry.cell * unit
    val rowHeader = GlyphGeometry.ROW_HEADER * unit
    val digits = GlyphGeometry.DIGITS * unit
    val hairline = CanonMetrics.hairline.toPx()
    val padding = CanonMetrics.panelPadding.toPx()
    glyphPaint.textSize = (geometry.cell * 0.56f).roundToInt().dp.toPx()
    val metrics = glyphPaint.fontMetrics
    val baselineShift = (-metrics.ascent - metrics.descent) / 2
    val ink = c.foreground.toArgb()
    val dim = c.mutedForeground.copy(alpha = 0.5f).toArgb()
    val litInk = c.primaryForeground.toArgb()

    for (index in items) {
        val y = geometry.tops[index] * unit
        when (val item = geometry.items[index]) {
            is GlyphGeometry.Item.Heading -> {
                val section = sections[item.section]
                val band = geometry.height(index) * unit
                val title = section.title
                if (title != null) {
                    val titleBand = band - digits
                    drawRect(c.muted, Offset(0f, y), Size(size.width, titleBand))
                    drawRect(c.border, Offset(0f, y), Size(size.width, hairline))
                    drawRect(c.border, Offset(0f, y + titleBand - hairline), Size(size.width, hairline))
                    val range = measurer.measure("%04X–%04X".format(section.range.first, section.range.last), addressMono.copy(color = c.mutedForeground))
                    val nameWidth = maxOf(0, (size.width - padding * 3 - range.size.width).toInt())
                    val name = measurer.measure(
                        "$title ›",
                        titleStyle.copy(color = c.mutedForeground),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        constraints = Constraints(maxWidth = nameWidth),
                    )
                    drawText(name, topLeft = Offset(padding, y + (titleBand - name.size.height) / 2))
                    drawText(range, topLeft = Offset(size.width - padding - range.size.width, y + (titleBand - range.size.height) / 2))
                }
                val digitsTop = y + band - digits
                for (column in 0 until geometry.columns) {
                    val digit = measurer.measure("%X".format(column), digitMono.copy(color = c.mutedForeground))
                    val x = rowHeader + (column + 0.5f) * cell - digit.size.width / 2
                    drawText(digit, topLeft = Offset(x, digitsTop + (digits - digit.size.height) / 2))
                }
            }
            is GlyphGeometry.Item.Row -> {
                val section = sections[item.section]
                val address = measurer.measure("%04X".format(item.start), addressMono.copy(color = c.mutedForeground))
                drawText(address, topLeft = Offset(rowHeader - address.size.width - 8.dp.toPx(), y + (cell - address.size.height) / 2))
                for (column in 0 until geometry.columns) {
                    val codePoint = item.start + column
                    val left = rowHeader + column * cell
                    if (!section.shows(codePoint) || covers?.invoke(codePoint) == false) {
                        drawRect(c.muted, Offset(left, y), Size(cell, cell))
                        continue
                    }
                    val isSelected = codePoint == selected
                    if (isSelected) drawRect(c.primary, Offset(left, y), Size(cell, cell))
                    val text = GlyphEngine.shown(codePoint)
                    val drawn = covers != null || covered.getOrPut(codePoint) { glyphPaint.hasGlyph(String(Character.toChars(codePoint))) }
                    glyphPaint.color = if (isSelected) litInk else if (drawn) ink else dim
                    val textWidth = glyphPaint.measureText(text)
                    clipRect(left, y, left + cell, y + cell) {
                        drawIntoCanvas { it.nativeCanvas.drawText(text, left + cell / 2 - textWidth / 2, y + cell / 2 + baselineShift, glyphPaint) }
                    }
                }
                for (column in 0..geometry.columns) {
                    drawRect(c.border, Offset(rowHeader + column * cell - hairline / 2, y), Size(hairline, cell))
                }
                drawRect(c.border, Offset(rowHeader, y - hairline / 2), Size(size.width - rowHeader, hairline))
                drawRect(c.border, Offset(rowHeader, y + cell - hairline / 2), Size(size.width - rowHeader, hairline))
            }
        }
    }
}

@Composable
private fun GlyphTable(model: GlyphBrowserModel, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val chart = remember { GlyphChartState() }
    val scroll = rememberScrollState()
    var jumpFrom by remember { mutableStateOf<Int?>(null) }
    var viewport by remember { mutableIntStateOf(0) }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(c.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(
                Modifier.fillMaxWidth().height(CanonMetrics.controlHeight).background(c.card).padding(start = CanonMetrics.panelPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Glyphs", Modifier.weight(1f), style = TextStyle(fontFamily = DelphiFont, fontSize = 17.sp, fontWeight = FontWeight.Bold), color = c.foreground)
                CanonButton("", icon = Icons.Rounded.Close, contentDescription = "Close", onClick = onDismiss)
            }
            Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { viewport = it.size.height }) {
                Column(Modifier.fillMaxSize().background(c.card).verticalScroll(scroll)) {
                    GlyphChart(chart, model.sections, model.selected, onPick = {
                        model.select(it)
                        onPick(it)
                    }, onHeading = { jumpFrom = it })
                    Spacer(Modifier.height(100.dp))
                }
                GlyphStrip(
                    model.selected,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(CanonMetrics.panelPadding)
                        .widthIn(max = 520.dp)
                        .fillMaxWidth(),
                ) { CanonCopyFeedback.copy(view, String(Character.toChars(model.selected))) }
            }
        }
    }
    LaunchedEffect(viewport) {
        if (viewport == 0) return@LaunchedEffect
        // chart height lands a frame later
        delay(16)
        chart.offset(model.selected)?.let { scroll.scrollTo(maxOf(0f, it - viewport / 2f).roundToInt()) }
    }
    jumpFrom?.let { current ->
        CanonJumpGrid(
            model.blocks.map { CanonJumpSection(it.name, GlyphEngine.jumpDetail(it)) },
            current,
            onPick = { section -> scope.launch { chart.headingTop(section)?.let { scroll.animateScrollTo(it.roundToInt()) } } },
            onDismiss = { jumpFrom = null },
        )
    }
}

private fun GlyphChartState.headingTop(section: Int): Float? {
    val geometry = geometry ?: return null
    return geometry.heading(section)?.let { geometry.tops[it] * density }
}

@Composable
private fun GlyphStrip(codePoint: Int, modifier: Modifier, onCopy: () -> Unit) {
    val c = LocalDelphiColours.current
    Row(
        modifier
            .background(c.border)
            .padding(CanonMetrics.frame)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        Row(
            Modifier.weight(1f).background(c.card).padding(start = 10.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlyphSquare(
                GlyphEngine.shown(codePoint),
                Modifier.size(64.dp).background(c.background).border(CanonMetrics.hairline, c.border),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(GlyphEngine.label(codePoint), style = TextStyle(fontFamily = DelphiMono, fontSize = 17.sp, fontWeight = FontWeight.Bold), color = c.foreground)
                Text(GlyphEngine.name(codePoint), style = factsStyle, color = c.mutedForeground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        CanonButton("Copy", Modifier.background(c.card), icon = Icons.Outlined.ContentCopy, onClick = onCopy)
    }
}
