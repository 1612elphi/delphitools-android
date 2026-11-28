package com.delphi.delphitools.tools.typography

import com.delphi.delphitools.R
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import android.content.ClipData
import android.content.ClipboardManager
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonBar
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSharedText
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTextArea
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.rememberCanonTextFilePicker
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private const val FOLDED = 5
private const val UNFOLDED = 25
private val pictureHeight = 180.dp
private val wordMono = TextStyle(fontFamily = DelphiMono, fontSize = 16.sp)

private fun Int.grouped(): String = NumberFormat.getIntegerInstance().format(this)

/** Localised minutes and seconds; under a second is zero. */
private fun duration(minutes: Double): String {
    val seconds = (minutes * 60).roundToInt()
    val format = MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.SHORT)
    return when {
        seconds < 60 -> format.formatMeasures(Measure(seconds, MeasureUnit.SECOND))
        seconds % 60 == 0 -> format.formatMeasures(Measure(seconds / 60, MeasureUnit.MINUTE))
        else -> format.formatMeasures(Measure(seconds / 60, MeasureUnit.MINUTE), Measure(seconds % 60, MeasureUnit.SECOND))
    }
}

class WordCounterModel : ViewModel() {
    var text by mutableStateOf("")
        private set

    /** The counts as last refreshed; typing refreshes after a pause. */
    var stats by mutableStateOf(WordStats(""))
        private set
    var unfolded by mutableStateOf(false)
        private set
    private var typing: Job? = null

    val top get() = stats.topWords(if (unfolded) UNFOLDED else FOLDED + 1)

    val report: String
        get() = with(stats) {
            listOf(
                "Words" to "$words",
                "Characters" to "$characters",
                "No Spaces" to "$charactersNoSpaces",
                "Sentences" to "$sentences",
                "Paragraphs" to "$paragraphs",
                "Lines" to "$lines",
                "Reading time" to duration(readingMinutes),
                "Speaking time" to duration(speakingMinutes),
            ).joinToString("\n") { (label, value) -> "$label: $value" }
        }

    fun type(text: String) {
        this.text = text
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            refresh()
        }
    }

    fun set(text: String) {
        this.text = text
        refresh()
    }

    fun toggle() {
        unfolded = !unfolded
    }

    private fun refresh() {
        typing?.cancel()
        stats = WordStats(text)
    }
}

@Composable
fun WordCounter(model: WordCounterModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val picker = rememberCanonTextFilePicker(arrayOf("text/plain"), model::set)
    CanonSharedText(model::set)
    val stats = model.stats
    val isEmpty = stats.text.isEmpty()
    val top = model.top
    val shown = if (model.unfolded) top else top.take(FOLDED)

    val input = CanonSection.of {
        CanonRows {
            CanonTextArea(model.text, model::type, placeholder = "Paste or type text here…", height = 320.dp, label = "Text")
            CanonGrid {
                CanonButton("Paste", Modifier.weight(1f), icon = pencil(R.drawable.pencil_clipboard)) {
                    clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.let { model.set(it.toString()) }
                }
                CanonButton("Files", Modifier.weight(1f), icon = pencil(R.drawable.pencil_folder)) { picker.open() }
                CanonButton("Clear", Modifier.weight(1f), enabled = !isEmpty, icon = pencil(R.drawable.pencil_trash_2)) { model.set("") }
            }
            CanonButton(
                "Copy Stats",
                kind = CanonButtonKind.Primary,
                enabled = !isEmpty,
                icon = pencil(R.drawable.pencil_copy),
                oversized = true,
                flashesCopied = true,
            ) { clipboard?.setPrimaryClip(ClipData.newPlainText("", model.report)) }
        }
    }

    val counts = CanonSection.of {
        CanonRows {
            CanonGrid(2) {
                listOf(
                    "Words" to stats.words,
                    "Characters" to stats.characters,
                    "Sentences" to stats.sentences,
                    "Paragraphs" to stats.paragraphs,
                ).forEach { (label, value) -> WordCounterFigure(label, value) }
            }
            CanonTiles(
                listOf(
                    CanonTile("No Spaces", stats.charactersNoSpaces.grouped()),
                    CanonTile("Lines", stats.lines.grouped()),
                    CanonTile("Avg Word", PxToRemEngine.format((stats.averageWordLength * 10).roundToInt() / 10.0) + " chars"),
                    CanonTile("Reading time", duration(stats.readingMinutes)),
                    CanonTile("Speaking time", duration(stats.speakingMinutes)),
                ),
            )
        }
    }

    val words = CanonSection.of {
        CanonRows {
            CanonHeading("Most Frequent")
            val most = top.firstOrNull()?.second ?: 1
            shown.forEach { (word, count) -> FrequentRow(word, count, count.toFloat() / most) }
            if (top.size > FOLDED) {
                CanonButton(
                    if (model.unfolded) "Show fewer" else "Show more",
                    icon = if (model.unfolded) pencil(R.drawable.pencil_chevron_up) else pencil(R.drawable.pencil_chevron_down),
                    onClick = model::toggle,
                )
            }
        }
    }

    val picture = CanonSection.canvas(CanonCanvasSize.Fixed(pictureHeight), background = c.card) {
        WordCounterPicture(stats.text, top.take(FOLDED).map { it.first }.toSet(), stats.words)
    }

    CanonSplit(
        sidebar = listOf(input),
        main = listOfNotNull(counts, words.takeIf { top.isNotEmpty() }),
        banner = picture,
        halves = true,
    )
}

@Composable
private fun WordCounterFigure(label: String, value: Int) {
    val c = LocalDelphiColours.current
    Column(
        Modifier
            .background(c.card)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = value.grouped()
            }
            .padding(horizontal = CanonMetrics.panelPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CanonFigure(value.grouped(), size = 32.sp)
        Text(label, style = CanonText.captionSemibold, color = c.mutedForeground, maxLines = 1)
    }
}

@Composable
private fun FrequentRow(word: String, count: Int, share: Float) {
    val c = LocalDelphiColours.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(c.card)
            .clearAndSetSemantics {
                contentDescription = word
                stateDescription = "×$count"
            },
    ) {
        Row(
            Modifier.padding(start = CanonMetrics.panelPadding, top = 12.dp, end = CanonMetrics.panelPadding, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(word, Modifier.weight(1f), style = wordMono, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("×$count", style = CanonText.bodySemibold, color = c.primary)
        }
        CanonBar(share, Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp))
    }
}

/** Each word a bar as long as its characters, the most frequent lit, with the word count beside them. */
@Composable
private fun WordCounterPicture(text: String, lit: Set<String>, words: Int) {
    val c = LocalDelphiColours.current
    val topInset = LocalCanonCanvas.current.top
    val lines = text.split('\n').map { line ->
        line.split(Regex("""\s+""")).filter { it.isNotEmpty() }.map { it.length to (WordStats.key(it) in lit) }
    }.takeUnless { all -> all.all { it.isEmpty() } }.orEmpty()

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val padding = CanonMetrics.panelPadding.toPx()
            val left = padding
            val top = topInset.toPx() + padding
            val width = size.width - padding * 2
            val height = size.height - top - padding
            if (width <= 0f || height <= 0f) return@Canvas
            val isEmpty = lines.isEmpty()
            val shown = if (isEmpty) WordBars.ghost.map { line -> line.map { it to false } } else lines
            // fit in points, drawn in pixels
            val fit = WordBars.fit(shown.map { line -> line.map { it.first } }, width / density, height / density)
            val unit = density
            val bar = fit.bar * unit
            val character = fit.character * unit
            val pitch = bar * (1 + WordBars.GAP)
            val plain = c.foreground.copy(alpha = if (isEmpty) 0.07f else 0.22f)
            var row = 0
            fun origin(): Offset? {
                val column = row / fit.perColumn
                if (column >= fit.columns) return null
                return Offset(left + column * (fit.columnWidth + WordBars.COLUMN_GAP) * unit, top + (row % fit.perColumn) * pitch)
            }
            var overflows = false
            outer@ for (line in shown) {
                var used = 0
                for ((length, isLit) in line) {
                    if (used > 0 && used + 1 + length > fit.columnCharacters) {
                        row++
                        used = 0
                    }
                    val start = origin()
                    if (start == null) {
                        overflows = true
                        break@outer
                    }
                    val offset = if (used == 0) 0 else used + 1
                    val span = minOf(length, fit.columnCharacters - offset) * character
                    drawRect(if (isLit) c.primary else plain, Offset(start.x + offset * character, start.y), Size(maxOf(character, span), bar))
                    used = offset + length
                }
                row++
            }
            if (overflows) {
                val fade = 40.dp.toPx()
                val bottom = top + height
                drawRect(
                    Brush.verticalGradient(listOf(c.card.copy(alpha = 0f), c.card), startY = bottom - fade, endY = bottom),
                    Offset(0f, bottom - fade),
                    Size(size.width, size.height - bottom + fade),
                )
            }
        }
        CanonFigure(
            words.grouped(),
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = CanonMetrics.panelPadding, bottom = CanonMetrics.panelPadding - 4.dp),
            unit = "words",
            halo = c.card,
        )
    }
}
