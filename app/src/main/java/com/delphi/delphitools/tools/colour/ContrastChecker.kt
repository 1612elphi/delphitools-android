package com.delphi.delphitools.tools.colour

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourControls
import com.delphi.delphitools.canon.CanonColourControlsState
import com.delphi.delphitools.canon.CanonColourSolid
import com.delphi.delphitools.canon.CanonColourSolidState
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.toCanonColor
import com.delphi.delphitools.colour.ContrastLevel
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.SolidSpace
import com.delphi.delphitools.colour.contrastFigure
import com.delphi.delphitools.colour.contrastRatio
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.parseHex
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours

private const val MAX_COLOURS = 10

/**
 * The solid always edits one colour: a well, or the picked network colour. That colour is read from the solid;
 * the others are stored. [commit] writes the solid's colour back before the edited colour changes.
 */
class ContrastCheckerModel : ViewModel() {
    val solid = CanonColourSolidState(SolidSpace.OKLCH, parseHex("EAEAEA")!!)

    val controls = CanonColourControlsState(solid, activeRow = 2)
    private var storedForeground by mutableStateOf(parseHex("EAEAEA")!!)
    private var storedBackground by mutableStateOf(parseHex("1A1A2E")!!)
    private val stored = mutableStateListOf<Rgb>()
    var editsBackground by mutableStateOf(false)
        private set
    var network by mutableStateOf(false)
        private set
    var picked by mutableIntStateOf(0)
        private set
    var focus by mutableStateOf(0 to 1)
        private set

    val foreground get() = if (!network && !editsBackground) solid.colour else storedForeground
    val background get() = if (!network && editsBackground) solid.colour else storedBackground
    val colours: List<Rgb> get() = stored.mapIndexed { index, colour -> if (index == picked) solid.colour else colour }

    private fun commit() {
        when {
            network -> stored[picked] = solid.colour
            editsBackground -> storedBackground = solid.colour
            else -> storedForeground = solid.colour
        }
    }

    private fun showActive() {
        val colour = if (network) stored[picked] else if (editsBackground) storedBackground else storedForeground
        solid.setPoint(solid.space.pointFor(colour), animated = true)
    }

    fun showNetwork(on: Boolean) {
        if (on == network) return
        commit()
        if (on && stored.isEmpty()) stored.addAll(listOf(storedForeground, storedBackground))
        network = on
        showActive()
    }

    fun edit(background: Boolean) {
        if (background == editsBackground) return
        commit()
        editsBackground = background
        showActive()
    }

    fun flip() {
        commit()
        storedForeground = storedBackground.also { storedBackground = storedForeground }
        showActive()
    }

    fun pick(index: Int) {
        if (index == picked) return
        commit()
        picked = index
        showActive()
    }

    fun add() {
        if (stored.size >= MAX_COLOURS) return
        commit()
        stored.add(stored[picked])
        picked = stored.size - 1
        showActive()
    }

    fun remove(index: Int) {
        if (stored.size <= 2) return
        commit()
        stored.removeAt(index)
        picked = minOf(if (index < picked) picked - 1 else picked, stored.size - 1)
        fun fit(i: Int) = if (i == index) -1 else if (i > index) i - 1 else i
        val text = fit(focus.first)
        val ground = fit(focus.second)
        focus = if (text >= 0 && ground >= 0) text to ground else 0 to 1
        showActive()
    }

    fun focus(text: Int, ground: Int) {
        focus = text to ground
    }

    fun apply(background: Boolean, colour: Rgb) {
        commit()
        if (background) storedBackground = colour else storedForeground = colour
        if (background == editsBackground) showActive()
    }
}

private class Suggestion(val background: Boolean, val ratio: Double, val level: String)

private val suggestions = listOf(
    Suggestion(false, ContrastLevel.AA, "AA"),
    Suggestion(false, ContrastLevel.AAA, "AAA"),
    Suggestion(true, ContrastLevel.AA, "AA"),
    Suggestion(true, ContrastLevel.AAA, "AAA"),
)

private class ChangePace {
    private var last = 0L
    private var key: Any? = null
    var animated = false
        private set

    fun note(next: Any) {
        if (next == key) return
        key = next
        val now = SystemClock.uptimeMillis()
        animated = now - last > 120
        last = now
    }
}

@Composable
fun ContrastChecker(model: ContrastCheckerModel = viewModel()) {
    val view = LocalView.current
    val network = model.network
    val colours = model.colours
    val (focusText, focusGround) = model.focus
    val fg = if (network) colours[focusText] else model.foreground
    val bg = if (network) colours[focusGround] else model.background
    val ratio = contrastRatio(fg, bg)
    val pace = remember { ChangePace() }
    pace.note(fg.hex + bg.hex)
    val animated = pace.animated
    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    val marks = if (network) colours.filterIndexed { index, _ -> index != model.picked } else listOf(if (model.editsBackground) fg else bg)
    LaunchedEffect(marks.map { it.hex }) { model.solid.marks = marks }

    val modes = CanonSection.of {
        CanonRows {
            CanonSegmented(listOf("Pair", "Network"), if (network) 1 else 0, columns = 2) { model.showNetwork(it == 1) }
        }
    }

    val editor = CanonSection.of {
        CanonRows {
            if (network) {
                ContrastList(
                    colours,
                    model.picked,
                    canAdd = colours.size < MAX_COLOURS,
                    canRemove = colours.size > 2,
                    onPick = {
                        if (it != model.picked) tick()
                        model.pick(it)
                    },
                    onRemove = model::remove,
                    onAdd = model::add,
                )
            } else {
                ContrastWells(
                    fg,
                    bg,
                    model.editsBackground,
                    onSelect = {
                        if (it != model.editsBackground) tick()
                        model.edit(it)
                    },
                    onFlip = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        model.flip()
                    },
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                CanonColourSolid(
                    model.solid,
                    Modifier
                        .fillMaxWidth()
                        .height(min(maxWidth * 0.66f, 240.dp))
                        .background(LocalDelphiColours.current.muted),
                )
            }
            val title = when {
                network -> "Colour"
                model.editsBackground -> "Background"
                else -> "Foreground"
            }
            CanonColourControls(model.controls, notationTitle = title)
        }
    }

    val main = if (network) {
        listOf(
            CanonSection.of {
                CanonRows {
                    CanonHeading("Network")
                    ContrastNetwork(colours, model.focus) { text, ground ->
                        tick()
                        model.focus(text, ground)
                    }
                }
            },
        )
    } else {
        listOf(
            CanonSection.of {
                CanonRows {
                    CanonHeading("Contrast Ratio")
                    ContrastScale(ratio, animated)
                    ContrastLampTable(ratio)
                }
            },
            CanonSection.of {
                CanonRows {
                    CanonHeading("Suggestions")
                    suggestions.forEach { target ->
                        val (moving, fixed) = if (target.background) bg to fg else fg to bg
                        val candidate = ContrastCheckerEngine.suggestion(moving, fixed, target.ratio)
                        SuggestionRow(target, candidate, moving, fixed, met = ratio >= target.ratio) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            model.apply(target.background, it)
                        }
                    }
                }
            },
        )
    }

    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(SPECIMEN_HEIGHT)) {
        ContrastSpecimen(fg, bg, ratio, animated)
    }

    CanonSplit(sidebar = listOf(modes, editor), main = main, banner = banner)
}

private fun <T> pace(animated: Boolean, millis: Int): AnimationSpec<T> = if (animated) tween(millis) else snap()

private val wellHex = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp)
private val listHex = TextStyle(fontFamily = DelphiMono, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

@Composable
private fun Chip(colour: Rgb, side: Int) {
    Box(
        Modifier
            .size(side.dp)
            .background(colour.toCanonColor())
            .border(CanonMetrics.hairline, LocalDelphiColours.current.border),
    )
}

@Composable
private fun ContrastWells(fg: Rgb, bg: Rgb, editsBackground: Boolean, onSelect: (Boolean) -> Unit, onFlip: () -> Unit) {
    val c = LocalDelphiColours.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(CanonMetrics.rowHeight).background(c.border)) {
        val wellWidth = (maxWidth - 52.dp - CanonMetrics.hairline * 2) / 2
        val indicator by animateDpAsState(
            if (editsBackground) wellWidth + 52.dp + CanonMetrics.hairline * 2 else 0.dp,
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        )
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
            Well("Foreground", fg, !editsBackground, Modifier.weight(1f)) { onSelect(false) }
            CanonButton("", Modifier.width(52.dp).fillMaxHeight(), icon = Icons.AutoMirrored.Rounded.CompareArrows, contentDescription = "Flip", onClick = onFlip)
            Well("Background", bg, editsBackground, Modifier.weight(1f)) { onSelect(true) }
        }
        Box(Modifier.offset(x = indicator).width(wellWidth).height(3.dp).background(c.primary))
    }
}

@Composable
private fun Well(name: String, colour: Rgb, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    Row(
        modifier
            .fillMaxHeight()
            .background(if (isSelected) c.accentBg else c.card)
            .canonClickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = name
                stateDescription = colour.hex
                selected = isSelected
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Chip(colour, 28)
        Column {
            Text(name, style = CanonText.subheadline.copy(fontWeight = FontWeight.SemiBold), color = if (isSelected) c.primary else c.foreground, maxLines = 1)
            Text(colour.hex, style = wellHex, color = c.mutedForeground, maxLines = 1)
        }
    }
}

@Composable
private fun ContrastList(
    colours: List<Rgb>,
    picked: Int,
    canAdd: Boolean,
    canRemove: Boolean,
    onPick: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    val c = LocalDelphiColours.current
    colours.forEachIndexed { index, colour ->
        val isSelected = index == picked
        Row(
            Modifier.fillMaxWidth().height(CanonMetrics.rowHeight).background(c.border),
            horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (isSelected) c.accentBg else c.card)
                    .canonClickable { onPick(index) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = colour.hex
                        selected = isSelected
                    }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Chip(colour, 28)
                Text(colour.hex, style = listHex, color = if (isSelected) c.primary else c.foreground, maxLines = 1)
            }
            CanonButton("", Modifier.width(52.dp).fillMaxHeight(), enabled = canRemove, icon = Icons.Rounded.Close, contentDescription = "Remove") {
                onRemove(index)
            }
        }
    }
    CanonButton("Add", Modifier.fillMaxWidth(), enabled = canAdd, icon = Icons.Rounded.Add, onClick = onAdd)
}

private val headWidth = 44.dp
private val minCell = 64.dp
private val sampleStyle = TextStyle(fontFamily = DelphiFont, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
private val cellRatio = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
private val cellGrade = TextStyle(fontFamily = DelphiMono, fontSize = 10.sp, textAlign = TextAlign.Center)

@Composable
private fun ContrastNetwork(colours: List<Rgb>, focus: Pair<Int, Int>, onFocus: (Int, Int) -> Unit) {
    val c = LocalDelphiColours.current
    BoxWithConstraints(Modifier.fillMaxWidth().background(c.card).semantics { contentDescription = "Network" }) {
        val count = colours.size
        val cell = max(minCell, (maxWidth - headWidth - CanonMetrics.hairline * count) / count)
        Column(
            Modifier.horizontalScroll(rememberScrollState()).background(c.border),
            verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
        ) {
            Row(Modifier.height(headWidth), horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
                Box(Modifier.width(headWidth).fillMaxHeight().background(c.card))
                colours.forEach { Head(it, Modifier.width(cell).fillMaxHeight()) }
            }
            colours.forEachIndexed { t, text ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
                    Head(text, Modifier.width(headWidth).fillMaxHeight())
                    colours.forEachIndexed { g, ground ->
                        if (t == g) {
                            Box(Modifier.width(cell).fillMaxHeight().background(c.muted))
                        } else {
                            NetworkCell(text, ground, focus.first == t && focus.second == g, Modifier.width(cell)) { onFocus(t, g) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Head(colour: Rgb, modifier: Modifier) {
    Box(
        modifier.background(LocalDelphiColours.current.card).semantics { contentDescription = colour.hex },
        contentAlignment = Alignment.Center,
    ) { Chip(colour, 24) }
}

@Composable
private fun NetworkCell(text: Rgb, ground: Rgb, focused: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    val value = contrastRatio(text, ground)
    val level = ContrastCheckerEngine.grade(value)
    val figure = contrastFigure(value) + ":1"
    val grade = level ?: "Fail"
    Column(
        modifier
            .background(if (focused) c.accentBg else c.card)
            .canonClickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${text.hex} on ${ground.hex}"
                stateDescription = "$figure, $grade"
                selected = focused
            }
            .padding(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(34.dp).background(ground.toCanonColor()), contentAlignment = Alignment.Center) {
            Text("Aa", style = sampleStyle, color = text.toCanonColor())
        }
        Text(figure, Modifier.fillMaxWidth(), style = cellRatio, color = if (focused) c.primary else c.foreground, maxLines = 1)
        Text(grade, Modifier.fillMaxWidth(), style = cellGrade, color = if (level == null) c.destructive else c.mutedForeground, maxLines = 1)
    }
}

private val SPECIMEN_HEIGHT = 276.dp
private val sideBySide = 600.dp
private val specimenNote = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp)

// wcag 2.2: 4/3 px/pt
private val specimenLines = listOf(
    Triple("Large", TextStyle(fontFamily = DelphiFont, fontSize = 24.sp), "18 pt"),
    Triple("Bold", TextStyle(fontFamily = DelphiFont, fontSize = 18.67.sp, fontWeight = FontWeight.Bold), "14 pt"),
    Triple("Normal", TextStyle(fontFamily = DelphiFont, fontSize = 16.sp), "12 pt"),
    Triple("Aa Bb Cc Dd Ee Ff Gg 0123456789", TextStyle(fontFamily = DelphiFont, fontSize = 16.sp), ""),
)
private val specimenGlyphs = listOf(
    Icons.Rounded.CheckBox,
    Icons.Rounded.RadioButtonChecked,
    Icons.Rounded.ToggleOn,
    Icons.Outlined.StarOutline,
    Icons.Outlined.Search,
    Icons.Outlined.Notifications,
)

@Composable
private fun ContrastSpecimen(fg: Rgb, bg: Rgb, ratio: Double, animated: Boolean) {
    val ground by animateColorAsState(bg.toCanonColor(), pace(animated, 250))
    val ink by animateColorAsState(fg.toCanonColor(), pace(animated, 250))
    val figure = contrastFigure(ratio)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(ground)
            .padding(top = LocalCanonCanvas.current.top)
            .semantics(mergeDescendants = true) {
                contentDescription = "Preview"
                stateDescription = "$figure:1"
            },
        contentAlignment = Alignment.Center,
    ) {
        val wide = maxWidth >= sideBySide
        val type = @Composable { modifier: Modifier ->
            Column(modifier.widthIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                specimenLines.forEach { (text, style, note) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text, Modifier.weight(1f, fill = false).alignByBaseline(), style = style, color = ink, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false)
                        if (note.isNotEmpty()) {
                            Spacer(Modifier.weight(1f))
                            Text(note, Modifier.alignByBaseline(), style = specimenNote, color = ink, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        specimenGlyphs.forEach { Icon(it, null, Modifier.size(22.dp), tint = ink) }
                    }
                    Text("Controls", style = specimenNote, color = ink, maxLines = 1)
                }
            }
        }
        val number = @Composable { CanonFigure(figure, unit = ":1", size = 56.sp, colour = ink, animated = animated) }
        if (wide) {
            Row(Modifier.padding(CanonMetrics.panelPadding), horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
                number()
                type(Modifier)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(CanonMetrics.panelPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                number()
                type(Modifier.fillMaxWidth())
            }
        }
    }
}

private val scaleNotches = listOf(ContrastLevel.AA_LARGE, ContrastLevel.AA, ContrastLevel.AAA)
private val scaleLabel = TextStyle(fontFamily = DelphiMono, fontSize = 10.sp)

@Composable
private fun ContrastScale(ratio: Double, animated: Boolean) {
    val c = LocalDelphiColours.current
    val measurer = rememberTextMeasurer()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val target = ContrastCheckerEngine.share(ratio).toFloat()
    val share by animateFloatAsState(target, if (animated) spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow) else snap())
    val fill by animateColorAsState(if (ratio < ContrastLevel.AA_LARGE) c.destructive else c.primary, pace(animated, 300))
    val labels = listOf("1") + scaleNotches.map(::contrastFigure) + listOf("21")
    val shares = listOf(0f) + scaleNotches.map { ContrastCheckerEngine.share(it).toFloat() } + listOf(1f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(c.card)
            .clearAndSetSemantics {
                contentDescription = "Ratio"
                stateDescription = contrastFigure(ratio) + ":1"
            },
    ) {
        val padding = CanonMetrics.panelPadding.toPx()
        val top = 14.dp.toPx()
        val height = 10.dp.toPx()
        val width = size.width - padding * 2
        fun x(at: Float) = if (rtl) padding + width * (1 - at) else padding + width * at
        drawRect(c.muted, Offset(padding, top), Size(width, height))
        val filled = width * share.coerceIn(0f, 1f)
        drawRect(fill, Offset(if (rtl) padding + width - filled else padding, top), Size(filled, height))
        scaleNotches.forEach {
            val at = x(ContrastCheckerEngine.share(it).toFloat())
            drawRect(c.foreground, Offset(at - 0.5.dp.toPx(), top - 4.dp.toPx()), Size(1.dp.toPx(), height + 8.dp.toPx()))
        }
        labels.zip(shares).forEach { (text, at) ->
            val label = measurer.measure(text, scaleLabel.copy(color = c.mutedForeground))
            val half = label.size.width / 2f
            val centre = x(at).coerceIn(padding + half, padding + width - half)
            drawText(label, topLeft = Offset(centre - half, top + height + 6.dp.toPx()))
        }
    }
}

private class Rule(val name: String, val aa: Double, val aaa: Double?)

// wcag 2.2: 1.4.3, 1.4.6, 1.4.11
private val rules = listOf(
    Rule("Normal", ContrastLevel.AA, ContrastLevel.AAA),
    Rule("Large", ContrastLevel.AA_LARGE, ContrastLevel.AA),
    Rule("Controls", ContrastLevel.AA_LARGE, null),
)
private val lampWidth = 96.dp
private val lampLabel = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp)

@Composable
private fun ContrastLampTable(ratio: Double) {
    val c = LocalDelphiColours.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
        CanonHeading("", Modifier.weight(1f).fillMaxHeight())
        CanonHeading("AA", Modifier.width(lampWidth).fillMaxHeight())
        CanonHeading("AAA", Modifier.width(lampWidth).fillMaxHeight())
    }
    rules.forEach { rule ->
        Row(Modifier.fillMaxWidth().height(CanonMetrics.rowHeight), horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
            Box(Modifier.weight(1f).fillMaxHeight().background(c.card).padding(start = CanonMetrics.panelPadding, end = 8.dp), contentAlignment = Alignment.CenterStart) {
                Text(rule.name, style = CanonText.body, color = c.foreground)
            }
            Lamp("AA", rule.name, rule.aa, ratio)
            Lamp("AAA", rule.name, rule.aaa, ratio)
        }
    }
}

@Composable
private fun Lamp(level: String, rule: String, threshold: Double?, ratio: Double) {
    val c = LocalDelphiColours.current
    val lit = threshold != null && ratio >= threshold
    val dot by animateColorAsState(if (lit) c.primary else c.mutedForeground.copy(alpha = 0.25f), tween(200))
    val glow by animateFloatAsState(if (lit) 0.8f else 0f, tween(200))
    val text by animateColorAsState(if (lit) c.foreground else c.mutedForeground, tween(200))
    Row(
        Modifier
            .width(lampWidth)
            .fillMaxHeight()
            .background(c.card)
            .then(
                if (threshold == null) {
                    Modifier.clearAndSetSemantics {}
                } else {
                    Modifier.clearAndSetSemantics {
                        contentDescription = "$level, $rule"
                        stateDescription = if (lit) "Pass" else "Fail"
                    }
                },
            )
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (threshold != null) {
            Box(
                Modifier.size(10.dp).drawBehind {
                    if (glow > 0) drawCircle(c.primary.copy(alpha = glow * 0.35f), size.minDimension / 2 + 4.dp.toPx())
                    drawCircle(dot)
                },
            )
        }
        Text(threshold?.let { contrastFigure(it) + ":1" } ?: "—", style = lampLabel, color = text)
    }
}

private val suggestionDetail = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp)
private val suggestionRatio = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp)

@Composable
private fun SuggestionRow(target: Suggestion, candidate: Rgb?, current: Rgb, fixed: Rgb, met: Boolean, onApply: (Rgb) -> Unit) {
    val c = LocalDelphiColours.current
    val shown = candidate ?: current
    val (fg, bg) = if (target.background) fixed to shown else shown to fixed
    val title = if (target.background) "Background" else "Foreground"
    val ratioText = candidate?.let { contrastFigure(contrastRatio(it, fixed)) + ":1" } ?: if (met) "✓" else "—"
    val enabled = candidate != null
    val dim = if (enabled) 1f else 0.45f
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = CanonMetrics.rowHeight)
            .canonClickable(enabled = enabled) { candidate?.let(onApply) }
            .semantics(mergeDescendants = true) {
                contentDescription = "$title, ${target.level}"
                stateDescription = "${shown.hex}, $ratioText"
            },
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        Box(Modifier.width(56.dp).fillMaxHeight().alpha(dim).background(bg.toCanonColor()), contentAlignment = Alignment.Center) {
            Text("Aa", style = sampleStyle, color = fg.toCanonColor())
        }
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .alpha(dim)
                .background(c.card)
                .padding(horizontal = CanonMetrics.panelPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = CanonText.body, color = c.foreground)
                Text("${target.level} · ${shown.hex}", style = suggestionDetail, color = c.mutedForeground)
            }
            Text(ratioText, style = suggestionRatio, color = c.foreground)
            if (enabled) Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(16.dp), tint = c.primary)
        }
    }
}
