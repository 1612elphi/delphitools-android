package com.delphi.delphitools.tools.devtools

import com.delphi.delphitools.R
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCopyFeedback
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonFigure
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
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val COPIED_MS = 1500L
private const val LEAST_PITCH = 28f
private const val MOST_PITCH = 46f
private const val MOST_COLUMNS = 29
private const val LABEL_WIDTH = 34f
private const val FADED = 0.4f
private val codeMono = TextStyle(fontFamily = DelphiMono, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
private val phraseStyle = CanonText.title2.copy(fontWeight = FontWeight.Bold)
private val metaStyle = CanonText.footnoteSemibold.copy(fontWeight = FontWeight.Normal)

class HttpStatusModel : ViewModel() {
    var field by mutableStateOf("")
        private set
    var query by mutableStateOf("")
        private set
    var statusClass by mutableStateOf<Int?>(null)
    var selected by mutableIntStateOf(200)
    private var typing: Job? = null

    val groups get() = HttpStatusTable.groups(query, statusClass)

    fun type(text: String) {
        field = text
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            query = text
        }
    }

    fun clear() {
        typing?.cancel()
        field = ""
        query = ""
    }
}

@Composable
fun HttpStatus(model: HttpStatusModel = viewModel()) {
    val c = LocalDelphiColours.current
    val groups = model.groups
    val matches = groups.flatMap { group -> group.items.map { it.code } }.toSet()

    val controls = CanonSection.of {
        CanonRows {
            CanonHeading("Search")
            Row(
                Modifier.fillMaxWidth().background(c.card).padding(start = CanonMetrics.panelPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(pencil(R.drawable.pencil_search), null, tint = c.mutedForeground, modifier = Modifier.size(16.dp))
                CanonTextCell(
                    model.field,
                    model::type,
                    Modifier.weight(1f),
                    placeholder = "Code or phrase",
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                )
                if (model.field.isNotEmpty()) {
                    CanonButton("", icon = pencil(R.drawable.pencil_circle_x), contentDescription = "Clear", onClick = model::clear)
                }
            }
            CanonHeading("Class")
            val classes = listOf("All") + HttpStatusTable.classNames.map { "${it.statusClass}xx" }
            CanonSegmented(classes, model.statusClass ?: 0, columns = 3) { model.statusClass = it.takeIf { it > 0 } }
        }
    }

    val list = CanonSection.of {
        CanonRows {
            if (groups.isEmpty()) CanonEmptyRow("No matches")
            groups.forEach { group ->
                CanonJumpHeading(
                    "${group.statusClass}xx · ${group.name}",
                    detail = "${group.name} · ${group.items.size}",
                    jumpTitle = "${group.statusClass}xx",
                )
                group.items.forEach { entry ->
                    HttpStatusRow(entry, entry.code == model.selected) { model.selected = entry.code }
                }
            }
        }
    }

    val banner = CanonSection.canvas(CanonCanvasSize.Fitting) {
        HttpStatusChart(HttpStatusTable.lookup(model.selected), matches)
    }

    CanonSplit(sidebar = listOf(controls), main = listOf(list), banner = banner)
}

@Composable
private fun HttpStatusRow(entry: HttpStatusEntry, isLit: Boolean, onSelect: () -> Unit) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    var copies by remember { mutableIntStateOf(0) }
    LaunchedEffect(copies) {
        if (copies == 0) return@LaunchedEffect
        delay(COPIED_MS)
        copies = 0
    }
    // ponytail: no hover tint; add with a pointer hover modifier if mice matter
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(c.border)) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .heightIn(min = CanonMetrics.controlHeight)
                .canonClickable(onClick = onSelect)
                .clearAndSetSemantics {
                    contentDescription = "${entry.code} ${entry.phrase}"
                    role = Role.Button
                    selected = isLit
                }
                .background(if (isLit) c.accentBg else c.card)
                .padding(start = CanonMetrics.panelPadding, top = 8.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${entry.code}", style = codeMono, color = c.primary)
            Text(entry.phrase, Modifier.weight(1f), style = CanonText.body, color = c.foreground, maxLines = 2)
            if (entry.registry != HttpRegistryState.Standard) {
                Text(entry.registry.label.uppercase(), style = CanonText.captionSemibold, color = c.mutedForeground)
            }
        }
        CanonButton(
            "",
            Modifier.padding(start = CanonMetrics.hairline).width(CanonMetrics.controlHeight + 8.dp).fillMaxHeight(),
            icon = if (copies > 0) pencil(R.drawable.pencil_check) else pencil(R.drawable.pencil_copy),
            contentDescription = "Copy",
        ) {
            CanonCopyFeedback.copy(view, "${entry.code}")
            copies++
        }
    }
}

@Composable
private fun HttpStatusChart(entry: HttpStatusEntry?, matches: Set<Int>) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val pad = CanonMetrics.panelPadding
    val top = LocalCanonCanvas.current.top + pad * 0.75f
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                if (entry != null) {
                    contentDescription = "${entry.code} ${entry.phrase}"
                    stateDescription = entry.facts
                }
            },
    ) {
        val room = max(0f, maxWidth.value - pad.value * 2 - LABEL_WIDTH)
        val columns = (room / LEAST_PITCH).toInt().coerceIn(1, MOST_COLUMNS)
        val pitch = min(MOST_PITCH, floor(room / columns))
        val rows = HttpStatusTable.classNames.sumOf { (HttpStatusTable.count(it.statusClass) + columns - 1) / columns }
        Column(Modifier.padding(start = pad, top = top, end = pad, bottom = pad)) {
            if (entry != null) {
                Row(Modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CanonFigure("${entry.code}", size = 44.sp, halo = c.card)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Crossfade(entry.phrase, animationSpec = tween(200), label = "phrase") {
                            Text(it, style = phraseStyle, color = c.foreground, maxLines = 2)
                        }
                        Text(entry.facts, style = metaStyle, color = c.mutedForeground)
                    }
                }
            }
            // ponytail: tiles repaint at once; iOS cross-dissolves the grid over 0.2 s
            Canvas(Modifier.padding(top = 16.dp).fillMaxWidth().height((rows * pitch).dp)) {
                val step = pitch.dp.toPx()
                val tile = (pitch - 3).dp.toPx()
                val label = LABEL_WIDTH.dp.toPx()
                val codeStyle = TextStyle(fontFamily = DelphiMono, fontSize = with(density) { max(9f, pitch * 0.34f).dp.toSp() }, fontWeight = FontWeight.SemiBold)
                val hairline = CanonMetrics.hairline.toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))
                var y = 0f
                HttpStatusTable.classNames.forEach { info ->
                    val entries = HttpStatusTable.all.filter { it.statusClass == info.statusClass }
                    val heading = measurer.measure("${info.statusClass}xx", CanonText.captionSemibold.copy(color = c.mutedForeground))
                    drawText(heading, topLeft = Offset(0f, y + (tile - heading.size.height) / 2))
                    entries.forEachIndexed { index, item ->
                        val origin = Offset(label + (index % columns) * step, y + (index / columns) * step)
                        val isMatch = item.code in matches
                        val ink = when {
                            item.code == entry?.code -> {
                                drawRect(c.primary, origin, Size(tile, tile))
                                c.primaryForeground
                            }
                            item.registry == HttpRegistryState.Standard -> {
                                drawRect(if (isMatch) c.muted else c.muted.copy(alpha = FADED), origin, Size(tile, tile))
                                if (isMatch) c.foreground else c.mutedForeground.copy(alpha = FADED)
                            }
                            else -> {
                                val ink = c.mutedForeground.copy(alpha = if (isMatch) 1f else FADED)
                                drawRect(
                                    ink,
                                    origin + Offset(hairline / 2, hairline / 2),
                                    Size(tile - hairline, tile - hairline),
                                    style = Stroke(hairline, pathEffect = dash),
                                )
                                ink
                            }
                        }
                        val text = measurer.measure("${item.code}", codeStyle.copy(color = ink))
                        drawText(text, topLeft = origin + Offset((tile - text.size.width) / 2, (tile - text.size.height) / 2))
                    }
                    y += ((entries.size + columns - 1) / columns) * step
                }
            }
        }
    }
}
