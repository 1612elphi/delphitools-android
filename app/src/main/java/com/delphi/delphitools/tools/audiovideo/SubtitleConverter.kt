package com.delphi.delphitools.tools.audiovideo

import com.delphi.delphitools.R
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCodeBlock
import com.delphi.delphitools.canon.CanonDecimalField
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonFromTo
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTextArea
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/** ntsc: nominal × 1000/1001 */
private val rates = listOf(
    "24 NTSC" to 24000.0 / 1001, "24" to 24.0, "25" to 25.0, "30 NTSC" to 30000.0 / 1001, "30" to 30.0,
    "50" to 50.0, "60 NTSC" to 60000.0 / 1001, "60" to 60.0,
)
private val vttKinds = listOf("", "captions", "subtitles", "descriptions")

/** dt-ios SubtitleConverterModel. */
class SubtitleConverterModel(application: Application) : AndroidViewModel(application) {
    /** What the text area shows; [input] follows it after a typing pause. */
    var draft by mutableStateOf("")
        private set
    var input by mutableStateOf("")
        private set
    var cues by mutableStateOf(emptyList<SubtitleCue>())
        private set
    var target by mutableStateOf(SubtitleFormat.Vtt)
    var shiftSeconds by mutableDoubleStateOf(0.0)
    var scaleFactor by mutableDoubleStateOf(1.0)
        private set
    var rateFrom by mutableStateOf<Int?>(null)
        private set
    var rateTo by mutableStateOf<Int?>(null)
        private set
    var vttKind by mutableStateOf("")
    var vttLanguage by mutableStateOf("")
    private var fileName = ""
    private var typing: Job? = null

    val detected get() = SubtitleCodec.detectFormat(input)
    val hasInput get() = input.any { !it.isWhitespace() }
    val adjusted get() = SubtitleCodec.retime(cues, shiftSeconds, scaleFactor)

    fun output(adjusted: List<SubtitleCue>) =
        if (adjusted.isEmpty()) "" else SubtitleCodec.write(adjusted, target, vttKind, vttLanguage)

    fun stats(adjusted: List<SubtitleCue>): String {
        val first = adjusted.firstOrNull() ?: return ""
        return "${adjusted.size} cues · ${timecode(first.start)} – ${timecode(adjusted.last().end)}"
    }

    private val downloadName: String
        get() {
            val base = if (fileName.isEmpty()) "subtitles" else fileName.replace(Regex("\\.(srt|vtt)$", RegexOption.IGNORE_CASE), "")
            return "$base.${target.extension}"
        }

    fun type(text: String) {
        draft = text
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            setInput(text, "")
        }
    }

    fun setInput(text: String, fileName: String) {
        typing?.cancel()
        draft = text
        input = text
        cues = SubtitleCodec.parse(text)
        this.fileName = fileName
    }

    fun load(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            // lossy utf-8, matches web
            val text = withContext(Dispatchers.IO) {
                runCatching { resolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } }.getOrNull()
            } ?: ""
            setInput(text, resolver.canonDisplayName(uri) ?: "")
            SubtitleCodec.detectFormat(text)?.let { target = if (it == SubtitleFormat.Srt) SubtitleFormat.Vtt else SubtitleFormat.Srt }
        }
    }

    fun typeScale(value: Double) {
        // typed scale clears pair
        rateFrom = null
        rateTo = null
        scaleFactor = value
    }

    fun pickRate(from: Int?, to: Int?) {
        // one side: same rate
        val f = from ?: to ?: 0
        val t = to ?: f
        rateFrom = f
        rateTo = t
        scaleFactor = rates[f].second / rates[t].second
    }

    fun export(context: Context, output: String) {
        viewModelScope.launch { FileExport.share(context, output.toByteArray(), downloadName, target.mimeType) }
    }

    private fun timecode(ms: Long) = SubtitleCodec.formatTimestamp(ms, '.')
}

@Composable
fun SubtitleConverter(model: SubtitleConverterModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val adjusted = model.adjusted
    val detected = model.detected
    val output = model.output(adjusted)
    val formats = SubtitleFormat.entries

    val input = CanonSection.of {
        CanonRows {
            CanonHeading(detected?.let { "Input · ${it.label}" } ?: "Input")
            if (!model.hasInput) {
                CanonDropZone(
                    "SRT or VTT",
                    icon = pencil(R.drawable.pencil_captions),
                    mimeTypes = arrayOf("text/*", "application/x-subrip", "application/octet-stream"),
                    photos = null,
                    paste = false,
                    onFiles = { uris -> uris.firstOrNull()?.let(model::load) },
                )
            }
            CanonTextArea(model.draft, model::type, placeholder = "Paste subtitles here…", label = "Subtitle input")
            CanonGrid {
                CanonButton("Paste", Modifier.weight(1f).fillMaxHeight(), icon = pencil(R.drawable.pencil_clipboard)) {
                    context.getSystemService(ClipboardManager::class.java)?.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)?.coerceToText(context)?.toString()
                        ?.let { model.setInput(it, "") }
                }
                CanonButton("Clear", Modifier.weight(1f).fillMaxHeight(), enabled = model.hasInput, icon = pencil(R.drawable.pencil_trash_2)) {
                    model.setInput("", "")
                }
            }
        }
    }

    val settings = CanonSection.of {
        CanonRows {
            CanonHeading("Target")
            CanonSegmented(formats.map { it.label }, formats.indexOf(model.target)) { model.target = formats[it] }
            CanonRow("Shift (s)", flush = true) {
                CanonDecimalField(
                    model.shiftSeconds,
                    { model.shiftSeconds = it },
                    Modifier.fillMaxSize(),
                    signed = true,
                    range = -86_400.0..86_400.0,
                )
            }
            CanonRow("Scale (×)", flush = true) {
                CanonDecimalField(
                    model.scaleFactor,
                    model::typeScale,
                    Modifier.fillMaxSize(),
                    unit = "×",
                    step = 0.001,
                    decimals = 6,
                    range = 0.000_001..1000.0,
                )
            }
            CanonHeading("Rate")
            CanonFromTo(
                rates.map { it.first },
                model.rateFrom,
                model.rateTo,
                { model.pickRate(it, model.rateTo) },
                { model.pickRate(model.rateFrom, it) },
                fromLabel = "Source",
                toLabel = "Target",
            )
            if (model.target == SubtitleFormat.Vtt) {
                CanonHeading("Kind")
                CanonSegmented(
                    vttKinds.map { it.ifEmpty { "—" } },
                    vttKinds.indexOf(model.vttKind),
                    columns = 2,
                ) { model.vttKind = vttKinds[it] }
                CanonRow("Language", flush = true) {
                    CanonTextCell(model.vttLanguage, { model.vttLanguage = it }, placeholder = "en", width = 120.dp)
                }
            }
        }
    }

    val result = CanonSection.of {
        CanonRows {
            CanonHeading("Output")
            CanonRow(model.stats(adjusted).ifEmpty { " " })
            if (model.hasInput && adjusted.isEmpty()) CanonStatusRow("No cues found.", CanonTone.Warn)
            CanonCodeBlock(output, Modifier.semantics { contentDescription = "Converted output" }, maxHeight = 420.dp)
            CanonButton(
                "Export",
                kind = CanonButtonKind.Primary,
                enabled = output.isNotEmpty(),
                icon = pencil(R.drawable.pencil_share),
                oversized = true,
            ) { model.export(context, output) }
        }
    }

    val timeline = CanonSection.canvas(CanonCanvasSize.Fixed(132.dp), background = c.muted) {
        SubtitleTimeline(model.cues, adjusted, detected?.label, model.target.label)
    }

    CanonSplit(sidebar = listOf(input, settings), main = listOf(result), banner = timeline)
}
