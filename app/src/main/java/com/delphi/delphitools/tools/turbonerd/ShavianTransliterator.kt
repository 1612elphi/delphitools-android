package com.delphi.delphitools.tools.turbonerd

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonGlyphFlow
import com.delphi.delphitools.canon.CanonGlyphKind
import com.delphi.delphitools.canon.CanonGlyphMetrics
import com.delphi.delphitools.canon.CanonGlyphRows
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTextInput
import com.delphi.delphitools.canon.CanonTextOutput
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.MenuCellPopup
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.rememberCanonGlyphFlowState
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.tools.ToolUndoActions
import com.delphi.delphitools.tools.UndoStack
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiColours
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiLight
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private const val SAMPLE = "Mankind, be vigilant; we loved you."
private const val DASH = "—"
private const val IMAGE_NAME = "shavian-gloss.png"
private const val IMAGE_WIDTH = 1080f

// untranslated terms of art
private val markerChoices = listOf(
    GlossMarker.None to "None",
    GlossMarker.Namer to "· Namer",
    GlossMarker.Acroring to "⸰ Acroring",
    GlossMarker.Acroarc to "꤮ Acroarc",
)

// system noto sans shavian
private fun shavian(size: Int) = TextStyle(fontSize = size.sp)
private val letterStyle = shavian(30)
private val outputStyle = shavian(24)
private val latinStyle = TextStyle(fontFamily = DelphiFont, fontSize = 12.sp, fontWeight = FontWeight.Medium)
private val footnote = TextStyle(fontFamily = DelphiFont, fontSize = 12.sp)

/** dt-ios ShavianTransliteratorViewModel: the core dictionary first, then the full one, which re-resolves guessed words. */
class ShavianTransliteratorModel(application: Application) : AndroidViewModel(application) {
    var field by mutableStateOf(SAMPLE)
    private var input = SAMPLE
    var tokens by mutableStateOf<List<GlossToken>>(emptyList())
        private set
    var ready by mutableStateOf(false)
        private set
    var selected by mutableStateOf<Int?>(null)
        private set
    val history = UndoStack<List<GlossToken>>()
    private val engine = ShavianEngine()
    private var coreLoaded = false

    init {
        val assets = application.assets
        viewModelScope.launch {
            engine.core = ShavianDictionary.core(assets)
            coreLoaded = true
            retokenise()
            engine.full = ShavianDictionary.full(assets)
            tokens = engine.reResolve(tokens)
            history.clear()
            ready = true
        }
    }

    val words get() = tokens.mapNotNull { (it as? GlossToken.Word)?.word }

    val text get() = ShavianEngine.text(tokens)

    fun settle(text: String) {
        if (text == input) return
        input = text
        selected = null
        history.clear()
        retokenise()
    }

    private fun retokenise() {
        if (!coreLoaded) return
        tokens = ShavianEngine.carryEdits(tokens, engine.tokenise(input))
    }

    fun select(slot: Int?) {
        selected = slot?.takeIf { tokens.getOrNull(it) is GlossToken.Word }
    }

    fun word(slot: Int) = (tokens.getOrNull(slot) as? GlossToken.Word)?.word

    private fun edit(slot: Int, change: (GlossWord) -> GlossWord?) {
        val word = word(slot) ?: return
        val changed = change(word) ?: return
        history.push(tokens)
        tokens = tokens.toMutableList().also { it[slot] = GlossToken.Word(changed) }
    }

    fun setMarker(slot: Int, marker: GlossMarker) = edit(slot) { if (it.marker == marker) null else it.respelled(marker = marker) }

    fun swap(slot: Int, index: Int, alternative: ShavianAlternative) = edit(slot) { ShavianEngine.swapping(index, alternative, it) }

    fun insert(slot: Int, index: Int, letter: ShavianLetter) = edit(slot) { ShavianEngine.inserting(letter, index, it) }

    fun remove(slot: Int, index: Int) = edit(slot) { ShavianEngine.removing(index, it) }

    fun undo(redo: Boolean) {
        tokens = (if (redo) history.redo(tokens) else history.undo(tokens)) ?: return
    }
}

private fun styled(tokens: List<GlossToken>, c: DelphiColours) = buildAnnotatedString {
    for (token in tokens) {
        when (token) {
            is GlossToken.Word -> {
                withStyle(SpanStyle(color = c.primary)) { append(token.word.marker.prefix) }
                val ink = if (token.word.guessed) c.mutedForeground else c.foreground
                withStyle(SpanStyle(color = ink)) { append(token.word.phonemes.joinToString("") { it.shavian }) }
            }
            is GlossToken.Punctuation -> withStyle(SpanStyle(color = c.foreground)) { append(token.value) }
            is GlossToken.Whitespace -> withStyle(SpanStyle(color = c.foreground)) { append(token.value) }
        }
    }
}

// ponytail: guessed gets a solid underline; Compose has no dashed one, the banner draws its own dashes
private fun legend(c: DelphiColours) = buildAnnotatedString {
    withStyle(SpanStyle(color = c.primary, fontWeight = FontWeight.Bold)) { append("· ") }
    withStyle(SpanStyle(color = c.mutedForeground)) { append("Marked      ") }
    withStyle(SpanStyle(color = c.mutedForeground, textDecoration = TextDecoration.Underline)) { append("Guessed") }
}

@Composable
fun ShavianTransliterator(model: ShavianTransliteratorModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokens = model.tokens
    val words = model.words
    val text = if (words.isEmpty()) "" else model.text
    ToolUndoActions(model.history.canUndo, model.history.canRedo, { model.undo(false) }, { model.undo(true) })
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) scope.launch { writeGloss(context, tokens, uri) }
    }

    val wordBlock = model.selected?.let { slot -> model.word(slot)?.let { word -> CanonSection.of { WordBlock(model, slot, word) } } }
    val controls = CanonSection.of {
        CanonRows {
            CanonHeading("English")
            CanonTextInput(model.field, { model.field = it }, model::settle, placeholder = "Type or paste")
        }
    }
    val output = CanonSection.of {
        CanonTextOutput(
            text,
            heading = "Shavian",
            fileName = "shavian.txt",
            style = outputStyle,
            styled = if (words.isEmpty()) null else styled(tokens, c),
            extra = {
                if (!model.ready) CanonRow("Loading") { CanonLoader() }
                if (words.isNotEmpty()) {
                    Text(
                        legend(c),
                        style = footnote,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.card)
                            .padding(horizontal = CanonMetrics.panelPadding, vertical = 10.dp),
                    )
                    CanonGrid {
                        CanonButton("Share image", Modifier.weight(1f).fillMaxHeight(), icon = Icons.Outlined.Photo, followsWorkflow = true) {
                            scope.launch { FileExport.share(context, glossPng(context, tokens), IMAGE_NAME, "image/png") }
                        }
                        CanonButton("Save to Files…", Modifier.weight(1f).fillMaxHeight(), icon = Icons.Outlined.Folder) {
                            save.launch(IMAGE_NAME)
                        }
                    }
                }
            },
        )
    }
    val counts = CanonSection.of {
        val guessed = words.count { it.guessed }
        val latin = words.sumOf { word -> word.latin.count { it.isLetter() } }
        val letters = words.sumOf { it.phonemes.size }
        fun shown(n: Int) = "%,d".format(n)
        CanonRows {
            CanonTiles(
                listOf(
                    CanonTile("Words", if (words.isEmpty()) DASH else shown(words.size)),
                    CanonTile("Dictionary", if (words.isEmpty()) DASH else shown(words.size - guessed)),
                    CanonTile("Guessed", if (words.isEmpty()) DASH else shown(guessed)),
                    CanonTile("Letters", if (words.isEmpty()) DASH else "${shown(latin)} → ${shown(letters)}"),
                ),
            )
        }
    }
    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(160.dp)) {
        ShavianGloss(tokens, model.selected, text, Modifier.fillMaxSize()) { slot -> model.select(if (slot == model.selected) null else slot) }
    }

    // ponytail: the word block appears and goes without the iOS slide and fade
    CanonSplit(sidebar = listOfNotNull(wordBlock, controls), main = listOf(output, counts), banner = banner, collapsesTo = 96.dp)
}

@Composable
private fun WordBlock(model: ShavianTransliteratorModel, slot: Int, word: GlossWord) {
    var tip by remember { mutableStateOf(false) }
    CanonRows {
        CanonHeading("Word")
        CanonRow(word.latin, detail = "/${word.ipa}/", flush = true) {
            CanonButton("", Modifier.width(52.dp), icon = Icons.Rounded.Close, contentDescription = "Close") { model.select(null) }
        }
        CanonGrid(columns = 5) {
            word.phonemes.forEachIndexed { index, phoneme ->
                LetterCell(
                    phoneme,
                    canRemove = word.phonemes.size > 1,
                    onPick = { model.swap(slot, index, it) },
                    onInsert = { after, letter -> model.insert(slot, if (after) index + 1 else index, letter) },
                    onRemove = { model.remove(slot, index) },
                )
            }
        }
        CanonRow("Marker", flush = true) {
            Box(propagateMinConstraints = true) {
                CanonButton("", Modifier.width(52.dp), icon = Icons.Outlined.Info, contentDescription = "Marker") { tip = true }
                MenuCellPopup(tip, { tip = false }) { MarkerTip() }
            }
        }
        CanonSegmented(markerChoices.map { it.second }, markerChoices.indexOfFirst { it.first == word.marker }, columns = 2) {
            model.setMarker(slot, markerChoices[it].first)
        }
    }
}

@Composable
private fun MarkerTip() {
    val c = LocalDelphiColours.current
    Column(
        Modifier
            .widthIn(max = 300.dp)
            .padding(CanonMetrics.panelPadding),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Marker", style = CanonText.bodySemibold, color = c.foreground)
        Text(
            "A namer dot · marks a proper noun. An acroring ⸰ marks an initialism (like FBI), an acroarc ꤮ an acronym said as a word (like NASA).",
            style = CanonText.body,
            color = c.mutedForeground,
        )
    }
}

private enum class LetterMenu { Root, Before, After }

@Composable
private fun LetterCell(
    phoneme: ShavianPhoneme,
    canRemove: Boolean,
    onPick: (ShavianAlternative) -> Unit,
    onInsert: (Boolean, ShavianLetter) -> Unit,
    onRemove: () -> Unit,
) {
    val c = LocalDelphiColours.current
    val name = ShavianPhonemeMap.letter(phoneme.shavian)?.name ?: ""
    var menu by remember { mutableStateOf<LetterMenu?>(null) }
    fun close() {
        menu = null
    }
    Box(propagateMinConstraints = true) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = CanonMetrics.controlHeight)
                .canonClickable { menu = LetterMenu.Root }
                .semantics(mergeDescendants = true) {
                    contentDescription = name
                    stateDescription = phoneme.ipa
                }
                .background(c.card)
                .padding(start = 2.dp, top = 6.dp, end = 2.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(phoneme.shavian, style = shavian(28), color = c.foreground)
            Text("$name\n/${phoneme.ipa}/", style = latinStyle, color = c.mutedForeground, textAlign = TextAlign.Center)
        }
        MenuCellPopup(menu != null, ::close) {
            when (val page = menu) {
                LetterMenu.Before, LetterMenu.After -> {
                    for ((vowel, title) in listOf(false to "Consonants", true to "Vowels")) {
                        CanonHeading(title)
                        ShavianPhonemeMap.letters.filter { it.vowel == vowel }.forEach { letter ->
                            LetterItem(letter.shavian, letter.name, letter.ipa) {
                                close()
                                onInsert(page == LetterMenu.After, letter)
                            }
                        }
                    }
                }
                else -> {
                    MenuRow("Insert before", trailing = Icons.AutoMirrored.Rounded.KeyboardArrowRight) { menu = LetterMenu.Before }
                    MenuRow("Insert after", trailing = Icons.AutoMirrored.Rounded.KeyboardArrowRight) { menu = LetterMenu.After }
                    MenuRow("Remove", trailing = Icons.Outlined.Delete, destructive = true, enabled = canRemove) {
                        close()
                        onRemove()
                    }
                    LetterItem(phoneme.shavian, name, phoneme.ipa, checked = true) { close() }
                    phoneme.alternatives.forEach { alternative ->
                        LetterItem(alternative.shavian, alternative.name, alternative.ipa) {
                            close()
                            onPick(alternative)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    title: String,
    trailing: androidx.compose.ui.graphics.vector.ImageVector,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = LocalDelphiColours.current
    val ink = (if (destructive) c.destructive else c.foreground).let { if (enabled) it else it.copy(alpha = 0.4f) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CanonMetrics.controlHeight)
            .canonClickable(enabled, onClick = onClick)
            .padding(horizontal = CanonMetrics.panelPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = CanonText.body, color = ink)
        Icon(trailing, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun LetterItem(shavian: String, name: String, ipa: String, checked: Boolean = false, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CanonMetrics.controlHeight)
            .canonClickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = CanonMetrics.panelPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(shavian, Modifier.width(28.dp), style = shavian(22), color = c.foreground, textAlign = TextAlign.Center)
        Column(Modifier.weight(1f)) {
            Text(name, style = CanonText.body, color = c.foreground)
            Text("/$ipa/", style = CanonText.caption, color = c.mutedForeground)
        }
        if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = c.primary, modifier = Modifier.size(18.dp))
    }
}

private enum class ShavianTone { Word, Guess, Mark, Space, Newline }

private data class ShavianGlyph(val shavian: String, val tone: ShavianTone, val marker: String = "", val latin: String = "", val lit: Boolean = false)

private fun ShavianTone.kind() = when (this) {
    ShavianTone.Space -> CanonGlyphKind.Space
    ShavianTone.Newline -> CanonGlyphKind.Newline
    else -> CanonGlyphKind.Glyph
}

private fun glyphs(tokens: List<GlossToken>, lit: Int? = null): Triple<List<ShavianGlyph>, List<Int?>, Int?> {
    val glyphs = mutableListOf<ShavianGlyph>()
    val slots = mutableListOf<Int?>()
    var focus: Int? = null
    tokens.forEachIndexed { index, token ->
        when (token) {
            is GlossToken.Word -> {
                if (index == lit) focus = glyphs.size
                val word = token.word
                glyphs += ShavianGlyph(
                    word.phonemes.joinToString("") { it.shavian },
                    if (word.guessed) ShavianTone.Guess else ShavianTone.Word,
                    word.marker.prefix,
                    word.latin,
                    index == lit,
                )
                slots += index
            }
            is GlossToken.Punctuation -> {
                glyphs += ShavianGlyph(token.value, ShavianTone.Mark)
                slots += null
            }
            is GlossToken.Whitespace -> for (char in token.value) {
                glyphs += ShavianGlyph("", if (char == '\n') ShavianTone.Newline else ShavianTone.Space)
                slots += null
            }
        }
    }
    return Triple(glyphs, slots, focus)
}

private class GlossPainter(private val measurer: TextMeasurer, density: Density, private val c: DelphiColours) {
    private val px = density.density
    private val tall: Float
    private val short: Float
    private val deep: Float
    private val latinLine = measurer.measure("Ag", latinStyle).size.height.toFloat()
    private val padTop = 3 * px
    private val padSpelling = 5 * px
    val rowHeight: Float

    init {
        val paint = android.graphics.Paint().apply { textSize = with(density) { letterStyle.fontSize.toPx() } }
        fun top(letter: String) = android.graphics.Rect().also { paint.getTextBounds(letter, 0, letter.length, it) }.top.toFloat()
        // peep is tall, ash short
        tall = -top("𐑐")
        short = -top("𐑨")
        deep = tall - short
        rowHeight = ceil(padTop + tall + deep + padSpelling + latinLine)
    }

    private fun width(text: String, style: TextStyle) = if (text.isEmpty()) 0f else ceil(measurer.measure(text, style).size.width.toFloat())

    fun measure(glyph: ShavianGlyph): Float = when (glyph.tone) {
        ShavianTone.Newline -> 0f
        ShavianTone.Space -> 12 * px
        ShavianTone.Mark -> width(glyph.shavian, letterStyle)
        ShavianTone.Word, ShavianTone.Guess -> max(width(glyph.marker + glyph.shavian, letterStyle), width(glyph.latin, latinStyle))
    }

    fun DrawScope.draw(glyph: ShavianGlyph, frame: Rect) {
        if (glyph.tone == ShavianTone.Newline) return
        ruling(frame.left, frame.right, frame.top)
        val baseline = frame.top + padTop + tall
        when (glyph.tone) {
            ShavianTone.Space, ShavianTone.Newline -> return
            ShavianTone.Mark -> {
                val laid = measurer.measure(glyph.shavian, letterStyle.copy(color = c.mutedForeground))
                drawText(laid, topLeft = Offset(frame.left, baseline - laid.firstBaseline))
            }
            ShavianTone.Word, ShavianTone.Guess -> {
                val ink = if (glyph.lit) c.primary else if (glyph.tone == ShavianTone.Guess) c.mutedForeground else c.foreground
                val letters = buildAnnotatedString {
                    withStyle(SpanStyle(color = c.primary)) { append(glyph.marker) }
                    withStyle(SpanStyle(color = ink)) { append(glyph.shavian) }
                }
                val laid = measurer.measure(letters, letterStyle)
                drawText(laid, topLeft = Offset(frame.left, baseline - laid.firstBaseline))
                val top = baseline + deep + padSpelling
                val spelling = measurer.measure(glyph.latin, latinStyle.copy(color = if (glyph.lit) c.primary else c.mutedForeground))
                drawText(spelling, topLeft = Offset(frame.left, top))
                if (glyph.tone == ShavianTone.Guess) {
                    val y = top + spelling.firstBaseline + 2 * px
                    drawLine(
                        c.mutedForeground,
                        Offset(frame.left, y),
                        Offset(frame.left + spelling.size.width, y),
                        px,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * px, 2 * px)),
                    )
                }
                if (glyph.lit) drawRect(c.primary, Offset(frame.left, top + latinLine + px), Size(frame.width, 2 * px))
            }
        }
    }

    /** Opaque, so overlaps never darken. */
    fun DrawScope.ruling(left: Float, right: Float, top: Float) {
        if (right <= left) return
        val baseline = top + padTop + tall
        drawRect(c.muted, Offset(left, baseline - short), Size(right - left, short))
        drawRect(lerp(c.mutedForeground, c.card, 0.55f), Offset(left, baseline), Size(right - left, px))
        val period = 4 * px
        // dots align across glyphs
        val dots = PathEffect.dashPathEffect(floatArrayOf(px, period - px), left % period)
        for (y in listOf(baseline - tall, baseline + deep)) {
            drawLine(lerp(c.mutedForeground, c.card, 0.6f), Offset(left, y + px / 2), Offset(right, y + px / 2), px, pathEffect = dots)
        }
    }
}

@Composable
private fun ShavianGloss(tokens: List<GlossToken>, lit: Int?, value: String, modifier: Modifier, onWord: (Int) -> Unit) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val painter = remember(measurer, density, c) { GlossPainter(measurer, density, c) }
    val (glyphs, slots, focus) = remember(tokens, lit) { glyphs(tokens, lit) }
    val flow = rememberCanonGlyphFlowState<ShavianGlyph>(
        CanonGlyphMetrics(height = (painter.rowHeight / density.density).dp, rowGap = 12.dp, gap = 0.dp),
        measure = { (painter.measure(it) / density.density).dp },
        kind = { it.tone.kind() },
    )
    LaunchedEffect(glyphs, focus) {
        flow.show(glyphs)
        flow.focus = focus
    }
    CanonGlyphFlow(
        flow,
        modifier
            .background(c.card)
            .pointerInput(slots) {
                detectTapGestures { point -> flow.glyphIndex(point, slop = 12.dp)?.let(slots::getOrNull)?.let(onWord) }
            }
            .semantics {
                contentDescription = "Shavian"
                stateDescription = value
                customActions = tokens.indices.mapNotNull { index ->
                    val word = (tokens[index] as? GlossToken.Word)?.word ?: return@mapNotNull null
                    CustomAccessibilityAction(word.latin) {
                        onWord(index)
                        true
                    }
                }
            },
        paintEmpty = { area -> with(painter) { ruling(area.left, area.right, area.center.y - painter.rowHeight / 2) } },
    ) { glyph, _, frame -> with(painter) { draw(glyph, frame) } }
}

private suspend fun glossPng(context: Context, tokens: List<GlossToken>): ByteArray {
    val glyphs = glyphs(tokens).first
    // ponytail: 24 MP cap (about 96 MB) keeps a pasted book from exhausting memory; long texts export softer
    val probe = GlossPainter(TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr), Density(1f), DelphiLight)
    val margin = 40f
    val rowGap = 16f
    fun layout(painter: GlossPainter, scale: Float) = CanonGlyphRows.layout(
        glyphs.map(painter::measure), glyphs.map { it.tone.kind() }, (IMAGE_WIDTH - margin * 2) * scale, 0f,
        painter.rowHeight, painter.rowHeight + rowGap * scale,
    )
    val rows = max(1, layout(probe, 1f).rows)
    val height = margin * 2 + rows * (probe.rowHeight + rowGap) - rowGap
    val scale = min(2f, sqrt(24_000_000f / (IMAGE_WIDTH * height)))
    val density = Density(scale)
    val painter = GlossPainter(TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr), density, DelphiLight)
    val (frames, laidRows) = layout(painter, scale)
    val size = Size(IMAGE_WIDTH * scale, ceil(margin * 2 * scale + max(1, laidRows) * (painter.rowHeight + rowGap * scale) - rowGap * scale))
    val bitmap = ImageBitmap(size.width.toInt(), size.height.toInt())
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), size) {
        drawRect(DelphiLight.card)
        glyphs.zip(frames).forEach { (glyph, frame) ->
            if (frame.width > 0f) with(painter) { draw(glyph, frame.translate(margin * scale, margin * scale)) }
        }
    }
    return withContext(Dispatchers.Default) {
        ByteArrayOutputStream().also { bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}

private suspend fun writeGloss(context: Context, tokens: List<GlossToken>, uri: Uri) {
    val data = glossPng(context, tokens)
    withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(data) } }
    }
}
