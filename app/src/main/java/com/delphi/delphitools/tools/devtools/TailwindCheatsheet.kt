package com.delphi.delphitools.tools.devtools

import android.animation.ValueAnimator
import android.app.Application
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCopyFeedback
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonJumpHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private val gridFrom = 600.dp
private val listWidth = 720.dp
private val siblingsFrom = 760.dp
private val stageHeight = 196.dp
private val stageCollapsed = 116.dp
private val thumbSize = 66.dp to 45.dp
private val homeColumnWidth = 195.dp
private const val DESIGN_RATIO = TailwindSpecimen.DESIGN_WIDTH / TailwindSpecimen.DESIGN_HEIGHT

private val nameMono = TextStyle(fontFamily = DelphiMono, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
private val cssMono = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp)
private val exampleMono = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp)
private val searchMono = TextStyle(fontFamily = DelphiMono, fontSize = 16.sp)

/** dt-ios TailwindCheatsheetViewController's state: the table, the search and the lit class. */
class TailwindCheatsheetModel(application: Application) : AndroidViewModel(application) {
    var table by mutableStateOf(TailwindTable(emptyList()))
        private set
    var field by mutableStateOf("")
        private set
    var shown by mutableStateOf(emptyList<TailwindCategory>())
        private set
    var selected by mutableStateOf("p-4")
        private set
    private var typing: Job? = null
    private var search: Job? = null

    init {
        viewModelScope.launch {
            val assets = getApplication<Application>().assets
            table = withContext(Dispatchers.IO) {
                runCatching { TailwindTable.parse(assets.open("tailwind-cheatsheet.json").bufferedReader().use { it.readText() }) }.getOrDefault(TailwindTable(emptyList()))
            }
            shown = table.filter(field)
        }
    }

    fun type(text: String) {
        field = text
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            find(text)
        }
    }

    fun clear() {
        field = ""
        find("")
    }

    private fun find(text: String) {
        typing?.cancel()
        search?.cancel()
        val table = table
        search = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { table.filter(text) }
            shown = result
            result.firstOrNull()?.items?.firstOrNull()?.let { selected = it.name }
        }
    }

    fun select(name: String) {
        selected = name
    }
}

@Composable
private fun tailwindInks(): TailwindInks {
    val c = LocalDelphiColours.current
    return remember(c) { TailwindInks(c.primary, c.primaryForeground, c.muted, c.mutedForeground, c.foreground, c.card, c.destructive) }
}

@Composable
fun TailwindCheatsheet(model: TailwindCheatsheetModel = viewModel()) {
    val inks = tailwindInks()
    val measurer = rememberTextMeasurer()
    val symbols = rememberTailwindSymbols()
    val lit = model.table.lookup(model.selected)

    val browser = CanonSection.of(spansColumn = true) {
        TailwindBrowser(model, inks, measurer, symbols)
    }
    val stage = CanonSection.canvas(CanonCanvasSize.Fixed(stageHeight)) {
        if (lit != null) TailwindStage(lit.first, lit.second, inks, measurer, symbols)
    }
    CanonSplit(sidebar = listOf(browser), main = emptyList(), banner = stage, collapsesTo = stageCollapsed, oneColumn = true)
}

/**
 * dt-ios TailwindClassBrowser: the search row and every class in one framed block. Narrow, a list of rows;
 * from [gridFrom] wide, a grid of cards across the whole column. Categories head their classes with jump headings.
 */
@Composable
private fun TailwindBrowser(model: TailwindCheatsheetModel, inks: TailwindInks, measurer: TextMeasurer, symbols: Map<String, Painter>) {
    val c = LocalDelphiColours.current
    val pad = CanonMetrics.panelPadding
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val isGrid = maxWidth >= gridFrom
        val blockWidth = if (isGrid) maxWidth - pad * 2 else minOf(listWidth, maxWidth - pad * 2)
        val inner = blockWidth - CanonMetrics.frame * 2
        val columns = min(6, max(2, (inner / homeColumnWidth).toInt()))
        CanonRows(Modifier.width(blockWidth)) {
            SearchRow(model.field, model.table.count, model::type, model::clear)
            if (model.shown.isEmpty()) CanonEmptyRow("No matches")
            for (category in model.shown) {
                val count = category.items.size
                CanonJumpHeading("${category.name} · $count", detail = "$count", jumpTitle = category.name)
                if (isGrid) {
                    category.items.chunked(columns).forEach { chunk ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
                            for (item in chunk) {
                                ClassCard(item, category.name, item.name == model.selected, inks, measurer, symbols, Modifier.weight(1f)) { model.select(item.name) }
                            }
                            if (chunk.size < columns) Box(Modifier.weight((columns - chunk.size).toFloat()).fillMaxHeight().background(c.background))
                        }
                    }
                } else {
                    for (item in category.items) {
                        ClassRow(item, category.name, item.name == model.selected, inks, measurer, symbols) { model.select(item.name) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchRow(field: String, count: Int, onType: (String) -> Unit, onClear: () -> Unit) {
    val c = LocalDelphiColours.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = CanonMetrics.controlHeight).background(c.card).padding(start = CanonMetrics.panelPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(pencil(R.drawable.pencil_search), null, tint = c.mutedForeground, modifier = Modifier.size(16.dp))
        CanonTextCell(
            field,
            onType,
            Modifier.weight(1f).semantics { contentDescription = "Search" },
            placeholder = "Search $count classes…",
            textStyle = searchMono,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
        )
        if (field.isNotEmpty()) CanonButton("", icon = pencil(R.drawable.pencil_circle_x), contentDescription = "Clear", onClick = onClear)
    }
}

@Composable
private fun Thumb(item: TailwindClass, category: String, inks: TailwindInks, measurer: TextMeasurer, symbols: Map<String, Painter>, modifier: Modifier) {
    Canvas(modifier) {
        drawTailwindSpecimen(item, category, Rect(Offset.Zero, size), inks, measurer, symbols)
    }
}

private fun Modifier.classButton(item: TailwindClass, isLit: Boolean, onSelect: () -> Unit) = this
    .canonClickable(onClick = onSelect)
    .clearAndSetSemantics {
        contentDescription = item.name
        stateDescription = item.css
        role = Role.Button
        selected = isLit
    }

@Composable
private fun ClassRow(item: TailwindClass, category: String, isLit: Boolean, inks: TailwindInks, measurer: TextMeasurer, symbols: Map<String, Painter>, onSelect: () -> Unit) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    // ponytail: no hover tint; add with a pointer hover modifier if mice matter
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(c.border)) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .heightIn(min = CanonMetrics.rowHeight)
                .classButton(item, isLit, onSelect)
                .background(if (isLit) c.accentBg else c.card)
                .padding(start = 10.dp, top = 6.dp, end = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Thumb(item, category, inks, measurer, symbols, Modifier.size(thumbSize.first, thumbSize.second).background(c.background))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, style = nameMono, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.css, style = cssMono, color = c.mutedForeground)
                if (item.example != null) Text("e.g. ${item.example}", style = exampleMono, color = c.primary)
            }
        }
        CanonButton(
            "",
            Modifier.padding(start = CanonMetrics.hairline).width(CanonMetrics.controlHeight + 8.dp).fillMaxHeight(),
            icon = pencil(R.drawable.pencil_copy),
            contentDescription = "Copy",
        ) { CanonCopyFeedback.copy(view, item.name) }
    }
}

@Composable
private fun ClassCard(
    item: TailwindClass,
    category: String,
    isLit: Boolean,
    inks: TailwindInks,
    measurer: TextMeasurer,
    symbols: Map<String, Painter>,
    modifier: Modifier,
    onSelect: () -> Unit,
) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    BoxWithConstraints(modifier.background(c.border)) {
        val drawing = (maxWidth * 0.5f).value.let { Dp(Math.round(it).toFloat()) }
        Column(verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
            Thumb(item, category, inks, measurer, symbols, Modifier.fillMaxWidth().height(drawing).classButton(item, isLit, onSelect).background(if (isLit) c.accentBg else c.background))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
                Column(
                    Modifier
                        .weight(1f)
                        .classButton(item, isLit, onSelect)
                        .background(if (isLit) c.accentBg else c.card)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(item.name, style = nameMono, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.css, style = cssMono, color = c.mutedForeground, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                CanonButton(
                    "",
                    Modifier.width(CanonMetrics.controlHeight).fillMaxHeight(),
                    icon = pencil(R.drawable.pencil_copy),
                    contentDescription = "Copy",
                ) { CanonCopyFeedback.copy(view, item.name) }
            }
        }
    }
}

/**
 * dt-ios TailwindStage: the lit class, large: its specimen, moving where the class is about motion, beside its name,
 * CSS and example. Wide stages add the rest of its category as a grid of small specimens, the lit one framed.
 */
@Composable
private fun TailwindStage(item: TailwindClass, category: TailwindCategory, inks: TailwindInks, measurer: TextMeasurer, symbols: Map<String, Painter>) {
    val c = LocalDelphiColours.current
    val pad = CanonMetrics.panelPadding
    val top = LocalCanonCanvas.current.top
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Crossfade(item to category, Modifier.fillMaxSize().semantics(mergeDescendants = true) {
        contentDescription = item.name
        stateDescription = item.css
    }, animationSpec = tween(220), label = "stage") { (shownItem, shownCategory) ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = top).padding(horizontal = pad, vertical = pad * 0.75f)) {
            if (maxWidth <= 0.dp || maxHeight <= 0.dp) return@BoxWithConstraints
            val specimenWidth = minOf(maxHeight * DESIGN_RATIO, maxWidth * 0.42f)
            val specimenHeight = minOf(maxHeight, specimenWidth / DESIGN_RATIO + 12.dp)
            val showsSiblings = maxWidth >= siblingsFrom && shownCategory.items.size > 1
            val gap = 20.dp
            var textWidth = maxWidth - specimenWidth - gap
            if (showsSiblings) textWidth = minOf(textWidth * 0.45f, 360.dp)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                StageSpecimen(shownItem, shownCategory.name, inks, measurer, symbols, Modifier.width(specimenWidth).height(specimenHeight).background(c.background))
                Column(Modifier.padding(start = gap).width(textWidth), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(shownCategory.name.uppercase(), style = CanonText.captionSemibold, color = c.mutedForeground, maxLines = 1)
                    Text(
                        shownItem.name,
                        Modifier.padding(bottom = 3.dp),
                        style = TextStyle(fontFamily = DelphiMono, fontSize = 26.sp, fontWeight = FontWeight.Bold),
                        color = c.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(shownItem.css, style = TextStyle(fontFamily = DelphiMono, fontSize = 14.sp), color = c.foreground, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (shownItem.example != null) {
                        Text("e.g. ${shownItem.example}", style = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp), color = c.mutedForeground)
                    }
                }
                if (showsSiblings) {
                    Siblings(shownCategory, shownItem, inks, measurer, symbols, rtl, Modifier.padding(start = gap).weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun StageSpecimen(item: TailwindClass, category: String, inks: TailwindInks, measurer: TextMeasurer, symbols: Map<String, Painter>, modifier: Modifier) {
    val moves = remember { ValueAnimator.areAnimatorsEnabled() }
    val motion = remember(item, category) { if (moves) TailwindSpecimen.motion(item, category) else null }
    val millis by produceState(0L, motion) {
        if (motion == null) return@produceState
        val start = withFrameMillis { it }
        while (true) withFrameMillis { value = it - start }
    }
    Canvas(modifier) {
        val inset = 6.dp.toPx()
        val box = Rect(inset, inset, size.width - inset, size.height - inset)
        drawTailwindSpecimen(item, category, box, inks, measurer, symbols, omitsMotion = moves)
        if (motion != null) drawTailwindMotion(motion, box, inks, millis)
    }
}

@Composable
private fun Siblings(
    category: TailwindCategory,
    lit: TailwindClass,
    inks: TailwindInks,
    measurer: TextMeasurer,
    symbols: Map<String, Painter>,
    rtl: Boolean,
    modifier: Modifier,
) {
    val c = LocalDelphiColours.current
    Canvas(modifier.clearAndSetSemantics {}) {
        val items = category.items
        val gap = 4.dp.toPx()
        var columns = items.size
        var cell = Size(1f, 1f)
        for (rows in 1..max(1, items.size)) {
            val across = ceil(items.size.toDouble() / rows).toInt()
            val height = (size.height - gap * (rows - 1)) / rows
            val width = height * DESIGN_RATIO
            if (across * (width + gap) - gap <= size.width) {
                columns = across
                cell = Size(width, height)
                break
            }
        }
        items.forEachIndexed { index, item ->
            val column = index % columns
            val x = if (rtl) size.width - (column + 1) * (cell.width + gap) + gap else column * (cell.width + gap)
            val box = Rect(Offset(x, (index / columns) * (cell.height + gap)), cell)
            drawRect(c.background, box.topLeft, box.size)
            val isLit = item == lit
            val two = 2.dp.toPx()
            val inner = Rect(box.left + two, box.top + two, box.right - two, box.bottom - two)
            if (isLit) {
                drawTailwindSpecimen(item, category.name, inner, inks, measurer, symbols)
                val stroke = 1.5.dp.toPx()
                drawRect(c.primary, Offset(box.left + stroke / 2, box.top + stroke / 2), Size(box.width - stroke, box.height - stroke), style = Stroke(stroke))
            } else {
                drawContext.canvas.saveLayer(box, androidx.compose.ui.graphics.Paint().apply { alpha = 0.55f })
                drawTailwindSpecimen(item, category.name, inner, inks, measurer, symbols)
                drawContext.canvas.restore()
            }
        }
    }
}
