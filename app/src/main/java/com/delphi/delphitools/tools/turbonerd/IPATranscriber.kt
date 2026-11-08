package com.delphi.delphitools.tools.turbonerd

import android.app.Application
import android.util.JsonReader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonGlyphFlow
import com.delphi.delphitools.canon.CanonGlyphKind
import com.delphi.delphitools.canon.CanonGlyphMetrics
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTextInput
import com.delphi.delphitools.canon.CanonTextOutput
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.rememberCanonGlyphFlowState
import com.delphi.delphitools.tools.turbonerd.IPAEngine.Token
import com.delphi.delphitools.ui.theme.DelphiColours
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max

private const val SAMPLE = "Mankind, be vigilant; we loved you."

/** dt-ios IPATranscriberViewModel: the dictionaries load once, then every settled input is transcribed. */
class IPATranscriberModel(application: Application) : AndroidViewModel(application) {
    var field by mutableStateOf(SAMPLE)
    var input = SAMPLE
        private set
    var tokens by mutableStateOf<List<Token>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set
    private var dictionary: Map<String, List<String>> = emptyMap()

    val text get() = "/" + IPAEngine.text(tokens) + "/"

    init {
        val assets = application.assets
        viewModelScope.launch {
            dictionary = withContext(Dispatchers.IO) {
                // full first: it is 10x the core and wins on any shared word
                val merged = HashMap<String, List<String>>(150_000)
                for (name in listOf("shavian-dictionary-full.json", "shavian-dictionary-core.json")) {
                    runCatching { assets.open(name).reader().use { read(it, merged) } }
                }
                merged
            }
            loading = false
            transcribe()
        }
    }

    fun settle(text: String) {
        input = text
        transcribe()
    }

    private fun transcribe() {
        if (loading) return
        tokens = IPAEngine.transcribe(input) { dictionary[it] }
    }

    private fun read(reader: java.io.Reader, into: MutableMap<String, List<String>>) {
        val json = JsonReader(reader)
        json.beginObject()
        while (json.hasNext()) {
            val word = json.nextName()
            val phones = ArrayList<String>(8)
            json.beginArray()
            while (json.hasNext()) phones += json.nextString()
            json.endArray()
            into.putIfAbsent(word, phones)
        }
        json.endObject()
    }
}

private const val DASH = "—"
private val stressMarks = setOf('ˈ', 'ˌ')
private fun serif(size: Int) = TextStyle(fontFamily = FontFamily.Serif, fontSize = size.sp)
private val outputStyle = serif(20)
private val ipaStyle = serif(24)
private val wordStyle = TextStyle(fontFamily = DelphiFont, fontSize = 13.sp, fontWeight = FontWeight.Medium)
private val footnote = TextStyle(fontFamily = DelphiFont, fontSize = 12.sp)

// ponytail: guessed words get a solid underline in text; Compose has no dashed one, the banner draws its own dashes
private fun AnnotatedString.Builder.appendIpa(ipa: String, guess: Boolean, c: DelphiColours, underline: Boolean) {
    val ink = if (guess) c.mutedForeground else c.foreground
    withStyle(SpanStyle(color = ink, textDecoration = if (guess && underline) TextDecoration.Underline else null)) {
        for (char in ipa) {
            if (char in stressMarks) withStyle(SpanStyle(color = c.primary)) { append(char) } else append(char)
        }
    }
}

/** The IPA with stress marks in the primary colour; guessed words lighter and underlined. */
private fun styled(tokens: List<Token>, c: DelphiColours) = buildAnnotatedString {
    withStyle(SpanStyle(color = c.mutedForeground)) { append("/") }
    for (token in tokens) {
        when (token) {
            is Token.Word -> appendIpa(token.ipa, token.guess, c, underline = true)
            is Token.Other -> withStyle(SpanStyle(color = c.foreground)) { append(token.value) }
        }
    }
    withStyle(SpanStyle(color = c.mutedForeground)) { append("/") }
}

/** Stress mark in the primary colour, a guessed word underlined. */
private fun legend(c: DelphiColours) = buildAnnotatedString {
    withStyle(serif(17).toSpanStyle().copy(color = c.primary)) { append("ˈ ") }
    withStyle(SpanStyle(color = c.mutedForeground)) { append("Stress      ") }
    withStyle(SpanStyle(color = c.mutedForeground, textDecoration = TextDecoration.Underline)) { append("Guessed") }
}

@Composable
fun IPATranscriber(model: IPATranscriberModel = viewModel()) {
    val c = LocalDelphiColours.current
    val tokens = model.tokens
    val words = tokens.filterIsInstance<Token.Word>()
    val text = if (words.isEmpty()) "" else model.text

    val controls = CanonSection.of {
        CanonRows {
            CanonHeading("English")
            CanonTextInput(model.field, { model.field = it }, model::settle, placeholder = "Type or paste")
        }
    }
    val output = CanonSection.of {
        CanonTextOutput(
            text,
            heading = "IPA",
            fileName = "ipa.txt",
            style = outputStyle,
            busy = model.loading,
            styled = if (words.isEmpty()) null else styled(tokens, c),
            extra = {
                if (model.loading) CanonRow("Loading") { CanonLoader() }
                if (words.isNotEmpty()) {
                    Text(
                        legend(c),
                        style = footnote,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.card)
                            .padding(horizontal = CanonMetrics.panelPadding, vertical = 10.dp),
                    )
                }
            },
        )
    }
    val counts = CanonSection.of {
        val guessed = words.count { it.guess }
        fun shown(n: Int) = if (words.isEmpty()) DASH else "%,d".format(n)
        CanonRows {
            CanonTiles(
                listOf(
                    CanonTile("Words", shown(words.size)),
                    CanonTile("Dictionary", shown(words.size - guessed)),
                    CanonTile("Guessed", shown(guessed)),
                ),
            )
        }
    }
    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(150.dp)) { IPAGloss(tokens, text, Modifier.fillMaxSize()) }

    CanonSplit(sidebar = listOf(controls), main = listOf(output, counts), banner = banner, collapsesTo = 88.dp)
}

private enum class GlossTone { Word, Guess, Mark, Space, Newline }

private data class GlossGlyph(val ipa: String, val word: String, val tone: GlossTone)

private fun glossGlyphs(tokens: List<Token>): List<GlossGlyph> {
    val glyphs = buildList {
        for (token in tokens) {
            when (token) {
                is Token.Word -> add(GlossGlyph(token.ipa, token.value, if (token.guess) GlossTone.Guess else GlossTone.Word))
                is Token.Other -> for (char in BrailleEngine.codePoints(token.value)) {
                    val tone = if (char == "\n") GlossTone.Newline else if (char.isBlank()) GlossTone.Space else GlossTone.Mark
                    add(GlossGlyph(char, "", tone))
                }
            }
        }
    }
    if (glyphs.none { it.tone == GlossTone.Word || it.tone == GlossTone.Guess }) return emptyList()
    val slash = GlossGlyph("/", "", GlossTone.Mark)
    return listOf(slash) + glyphs + slash
}

/**
 * The transcription set interlinear, as in a phonetics text: each word's IPA over its spelling, between phonemic slashes.
 * Stress marks in the primary colour; a guessed word is lighter, with a dashed rule under it.
 */
@Composable
private fun IPAGloss(tokens: List<Token>, value: String, modifier: Modifier) {
    val c = LocalDelphiColours.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    fun width(text: String, style: TextStyle) =
        if (text.isEmpty()) 0f else ceil(measurer.measure(text, style).size.width / density.density)
    val ipaLine = measurer.measure("/", ipaStyle).size.height / density.density
    val flow = rememberCanonGlyphFlowState<GlossGlyph>(
        CanonGlyphMetrics(height = (ipaLine + 18).dp, rowGap = 12.dp, gap = 0.dp),
        measure = { glyph ->
            when (glyph.tone) {
                GlossTone.Newline -> 0.dp
                GlossTone.Space -> 12.dp
                else -> max(width(glyph.ipa, ipaStyle), width(glyph.word, wordStyle)).dp
            }
        },
        kind = {
            when (it.tone) {
                GlossTone.Space -> CanonGlyphKind.Space
                GlossTone.Newline -> CanonGlyphKind.Newline
                else -> CanonGlyphKind.Glyph
            }
        },
    )
    LaunchedEffect(tokens) { flow.show(glossGlyphs(tokens)) }
    CanonGlyphFlow(
        flow,
        modifier
            .background(c.card)
            .semantics {
                contentDescription = "IPA"
                stateDescription = value
            },
        paintEmpty = { area ->
            val laid = measurer.measure("/  /", ipaStyle.copy(color = c.mutedForeground.copy(alpha = 0.4f)))
            drawText(laid, topLeft = Offset(area.center.x - laid.size.width / 2f, area.center.y - laid.size.height / 2f))
        },
    ) { glyph, _, frame -> drawGloss(glyph, frame, c, measurer) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGloss(glyph: GlossGlyph, frame: Rect, c: DelphiColours, measurer: TextMeasurer) {
    when (glyph.tone) {
        GlossTone.Space, GlossTone.Newline -> return
        GlossTone.Mark -> drawText(measurer.measure(glyph.ipa, ipaStyle.copy(color = c.mutedForeground)), topLeft = frame.topLeft)
        GlossTone.Word, GlossTone.Guess -> {
            val guess = glyph.tone == GlossTone.Guess
            val ipa = buildAnnotatedString { appendIpa(glyph.ipa, guess, c, underline = false) }
            val laid = measurer.measure(ipa, ipaStyle)
            drawText(laid, topLeft = frame.topLeft)
            if (guess) {
                val y = frame.top + laid.firstBaseline + 2.dp.toPx()
                drawLine(
                    c.mutedForeground,
                    Offset(frame.left, y),
                    Offset(frame.left + laid.size.width, y),
                    1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                )
            }
            val word = measurer.measure(glyph.word, wordStyle.copy(color = c.mutedForeground))
            drawText(word, topLeft = Offset(frame.left, frame.top + laid.size.height + 2.dp.toPx()))
        }
    }
}
