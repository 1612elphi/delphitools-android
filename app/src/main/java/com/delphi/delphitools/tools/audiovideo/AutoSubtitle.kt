package com.delphi.delphitools.tools.audiovideo

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonBar
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMenuCell
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val PEAK_BUCKETS = 800
private val PICTURE_HEIGHT = 150.dp
private val CUE_LANE = 26.dp

private fun languageName(tag: String) = Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault()).ifEmpty { tag }

private const val SAVED_MODEL_KEY = "delphitools.autoSubtitleModel"
private const val OFFER_SEEN_KEY = "delphitools.whisperOfferSeen"
private val MODE_LABELS = listOf("Tiny", "Base", "Small", "Large")

/** dt-ios AutoSubtitleModel: a file, the engine and language to read it with, and the cues heard. */
class AutoSubtitleModel(application: Application) : AndroidViewModel(application) {
    val source = AVEditAudioSource(application, viewModelScope)
    private val prefs = application.getSharedPreferences("delphitools", Context.MODE_PRIVATE)
    var languages by mutableStateOf<List<String>>(emptyList())
        private set
    var language by mutableStateOf<String?>(null)
        private set
    private var installed = emptySet<String>()
    var available by mutableStateOf(true)
        private set
    /** The system recogniser exists on this device, whatever its languages. */
    var systemSupported by mutableStateOf(true)
        private set
    /** Even without an installed model. */
    var usesWhisper by mutableStateOf(false)
        private set
    var whisperModel by mutableStateOf<WhisperModel?>(null)
        private set
    /** null: detect the language. */
    var whisperLanguage by mutableStateOf<String?>(null)
    val whisperLanguages = AutoSubtitleEngine.mostSpokenFirst(WhisperEngine.languages, { it }, ::languageName)
    private var whisper: Pair<String, WhisperRuntime>? = null
    val actions = WhisperActions(
        download = { WhisperModelStore.download(application, it, viewModelScope) },
        cancel = WhisperModelStore::cancel,
        remove = { model ->
            if (whisper?.first == model.id) release()
            viewModelScope.launch { WhisperModelStore.remove(application, model) }
        },
    )
    var target by mutableStateOf(SubtitleFormat.Srt)
    /** One line per line of speech: no numbers, no timecodes, no header. Overrides [target]. */
    var plainText by mutableStateOf(false)
    var busy by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var problem by mutableStateOf<TranscribeFailure?>(null)
        private set
    var cues by mutableStateOf<List<SubtitleCue>>(emptyList())
        private set
    private var original = emptyList<SubtitleCue>()
    var finished by mutableStateOf(false)
        private set
    private var task: Job? = null
    private var scratch: File? = null

    init {
        viewModelScope.launch {
            val found = AutoSubtitleRecogniser.languages(application)
            if (found == null) {
                available = false
                systemSupported = false
                adoptModel()
                return@launch
            }
            installed = found.second
            languages = AutoSubtitleEngine.mostSpokenFirst(found.first, { Locale.forLanguageTag(it).language }, ::languageName)
            val current = Locale.getDefault()
            val device = Locale.Builder().setLanguage(current.language).setRegion(current.country).build().toLanguageTag()
            selectLanguage(
                languages.firstOrNull { it.equals(device, ignoreCase = true) }
                    ?: languages.firstOrNull { Locale.forLanguageTag(it).language == current.language }
                    ?: languages.firstOrNull()
            )
        }
    }

    fun selectLanguage(tag: String?) {
        language = tag
        available = tag != null && tag in installed
    }

    private fun save(choice: String) = prefs.edit().putString(SAVED_MODEL_KEY, choice).apply()

    fun selectEngine(whisper: Boolean) {
        usesWhisper = whisper
        if (whisper && whisperModel == null) whisperModel = WhisperModelStore.installed.lastOrNull()
        if (!whisper) whisperModel = null
        save(if (whisper) whisperModel?.id ?: "whisper" else "system")
    }

    fun selectWhisperModel(model: WhisperModel) {
        whisperModel = model
        save(model.id)
    }

    /** dt-ios adoptModel: the saved choice, else the largest installed model; Whisper when the system recogniser is missing. */
    fun adoptModel() {
        val installed = WhisperModelStore.installed
        val saved = prefs.getString(SAVED_MODEL_KEY, null) ?: ""
        if (whisperModel != null && whisperModel !in installed) whisperModel = null
        if (whisperModel == null && saved != "system") whisperModel = installed.firstOrNull { it.id == saved } ?: installed.lastOrNull()
        usesWhisper = !systemSupported || saved != "system" && (whisperModel != null || saved == "whisper" || installed.isEmpty())
        if (usesWhisper && whisperModel == null) whisperModel = installed.lastOrNull()
    }

    /** dt-ios WhisperTip: once, while no model is installed. */
    val offersWhisper get() = WhisperModelStore.installed.isEmpty() && !prefs.getBoolean(OFFER_SEEN_KEY, false)

    fun offerSeen() = prefs.edit().putBoolean(OFFER_SEEN_KEY, true).apply()

    val canRun get() = source.uri != null && !busy && if (usesWhisper) whisperModel != null else available && language != null

    private suspend fun runtime(model: WhisperModel): WhisperRuntime {
        whisper?.takeIf { it.first == model.id }?.let { return it.second }
        whisper?.second?.close()
        whisper = null
        val dir = WhisperModelStore.folder(getApplication(), model)
        return withContext(Dispatchers.Default) { WhisperRuntime(dir) }.also { whisper = model.id to it }
    }

    /** Frees the loaded model; a transcription in flight keeps it until it ends. */
    fun release() {
        val loaded = whisper?.second ?: return
        whisper = null
        val running = task
        // cancelled task may linger
        if (running != null && !running.isCompleted) running.invokeOnCompletion { loaded.close() } else loaded.close()
    }

    /** The page closed: the file, the downloads and the model go. */
    fun close() {
        clear()
        WhisperModelStore.cancelAll()
        release()
    }

    val output: String
        get() = when {
            cues.isEmpty() -> ""
            plainText -> cues.joinToString("\n") { it.text } + "\n"
            else -> SubtitleCodec.write(cues, target, kind = "captions")
        }

    val stats: String
        get() = cues.lastOrNull()?.let { "${cues.size} cues · ${SubtitleCodec.formatTimestamp(it.end, '.')}" } ?: ""

    private val downloadName get() = "${AVEditEngine.baseName(source.name, "subtitles")}.${if (plainText) "txt" else target.extension}"

    fun isAltered(index: Int) = index in cues.indices && (index !in original.indices || original[index] != cues[index])

    fun load(uri: Uri) {
        clear()
        source.load(uri, PEAK_BUCKETS)
    }

    fun clear() {
        reset()
        source.clear()
    }

    fun cancel() {
        task?.cancel()
        task = null
        avEditDiscard(scratch)
        scratch = null
        busy = false
        progress = 0f
    }

    private fun reset() {
        cancel()
        problem = null
        cues = emptyList()
        original = emptyList()
        finished = false
    }

    fun changeCue(index: Int, cue: SubtitleCue) {
        if (index in cues.indices && cues[index] != cue) cues = cues.toMutableList().also { it[index] = cue }
    }

    fun deny() {
        reset()
        problem = TranscribeFailure.Denied
    }

    fun run() {
        val uri = source.uri ?: return
        if (!canRun) return
        val chosen = whisperModel.takeIf { usesWhisper }
        if (chosen != null && WhisperModelStore.state(chosen) != WhisperState.Installed) {
            viewModelScope.launch { WhisperModelStore.refresh(getApplication()) }
            return
        }
        val language = language
        if (chosen == null && (language == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)) return
        reset()
        busy = true
        val app = getApplication<Application>()
        val dir = avEditRoot(app, "auto-subtitle").also { scratch = it }
        val spoken = whisperLanguage
        task = viewModelScope.launch {
            try {
                val pcm = File(dir, "speech.pcm")
                val result = if (chosen != null) {
                    AutoSubtitleRecogniser.transcribe(app, uri, runtime(chosen), spoken, pcm) { progress = it.toFloat() }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && language != null) {
                    AutoSubtitleRecogniser.transcribe(app, uri, language, pcm) { progress = it.toFloat() }
                } else {
                    emptyList()
                }
                cues = result
                original = result
                finished = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = (e as? TranscribeException)?.failure ?: TranscribeFailure.Failed
                if (failure == TranscribeFailure.Unavailable) available = false else problem = failure
            }
            busy = false
            avEditDiscard(dir)
            if (scratch == dir) scratch = null
        }
    }

    fun export(context: Context) {
        val text = output
        val mime = if (plainText) "text/plain" else target.mimeType
        viewModelScope.launch { FileExport.share(context, text.toByteArray(), downloadName, mime) }
    }

    override fun onCleared() = close()
}

@Composable
fun AutoSubtitle(model: AutoSubtitleModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val source = model.source
    AVEditPageEffects(null, model::close)
    var showModels by remember { mutableStateOf(false) }
    var showOffer by remember { mutableStateOf(false) }
    val installedIds = WhisperModelStore.installed.map { it.id }
    LaunchedEffect(Unit) {
        WhisperModelStore.refresh(context)
        model.adoptModel()
        showOffer = model.offersWhisper
    }
    LaunchedEffect(installedIds) { model.adoptModel() }
    val loaded = source.uri != null
    val busy = model.busy
    val cues = model.cues
    val formats = SubtitleFormat.entries
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) model.run() else model.deny() }

    val controls = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            if (!loaded) {
                CanonDropZone(
                    "Drop an audio or video file",
                    icon = pencil(R.drawable.pencil_audio_waveform),
                    mimeTypes = arrayOf("audio/*", "video/*"),
                    photos = PickVisualMedia.VideoOnly,
                    paste = false,
                    onFiles = { it.firstOrNull()?.let(model::load) },
                )
            } else {
                AVEditFileRow(source.name, source.scan?.let(AVEditAudioEngine::audioDetail) ?: "", null, model::clear)
            }
            CanonHeading("Model")
            CanonRow("Model", flush = true) {
                CanonSegmented(
                    listOf("System", "Whisper"),
                    if (model.usesWhisper) 1 else 0,
                    enabled = !busy,
                    disabledIndices = if (model.systemSupported) emptySet() else setOf(0),
                ) { model.selectEngine(it == 1) }
            }
            if (model.usesWhisper) {
                val installed = WhisperModelStore.installed
                CanonSegmented(
                    MODE_LABELS,
                    WhisperModel.all.indexOf(model.whisperModel),
                    enabled = !busy,
                    disabledIndices = WhisperModel.all.indices.filter { WhisperModel.all[it] !in installed }.toSet(),
                ) { model.selectWhisperModel(WhisperModel.all[it]) }
            }
            CanonHeading("Language")
            CanonRow("Language", flush = true) {
                if (model.usesWhisper) {
                    CanonMenuCell(
                        listOf("Auto") + model.whisperLanguages.map(::languageName),
                        model.whisperLanguages.indexOf(model.whisperLanguage) + 1,
                        { model.whisperLanguage = model.whisperLanguages.getOrNull(it - 1) },
                        Modifier.fillMaxSize(),
                        enabled = !busy,
                    )
                } else {
                    CanonMenuCell(
                        model.languages.map(::languageName),
                        model.languages.indexOf(model.language).takeIf { it >= 0 },
                        { model.selectLanguage(model.languages[it]) },
                        Modifier.fillMaxSize(),
                        placeholder = "…",
                        enabled = !busy,
                    )
                }
            }
            CanonHeading("Format")
            CanonSegmented(
                formats.map { it.label } + "TXT",
                if (model.plainText) formats.size else formats.indexOf(model.target),
                enabled = !busy,
            ) { index ->
                model.plainText = index !in formats.indices
                if (!model.plainText) model.target = formats[index]
            }
            CanonButton(
                "Transcribe",
                kind = CanonButtonKind.Primary,
                enabled = model.canRun,
                icon = pencil(R.drawable.pencil_captions),
                oversized = true,
            ) {
                if (model.usesWhisper) model.run()
                else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) model.run()
                else ask.launch(Manifest.permission.RECORD_AUDIO)
            }
            val failure = if (model.usesWhisper || model.available) model.problem else TranscribeFailure.Unavailable
            if (failure != null) CanonStatusRow(failure.message, CanonTone.Warn)
            CanonButton("Download models…", enabled = !busy, icon = pencil(R.drawable.pencil_chevron_right)) { showModels = true }
        }
    }

    val result = CanonSection.of {
        CanonRows {
            CanonHeading("Subtitles")
            if (busy) {
                CanonRow("Transcribing…") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground)
                        if (model.progress > 0) Text("${(model.progress * 100).roundToInt()}%", style = CanonText.footnoteSemibold, color = c.mutedForeground)
                    }
                }
                if (model.progress > 0) CanonBar(model.progress, Modifier.fillMaxWidth().height(3.dp))
                CanonButton("Cancel", icon = pencil(R.drawable.pencil_x), onClick = model::cancel)
                return@CanonRows
            }
            CanonRow(model.stats.ifEmpty { " " })
            if (cues.isEmpty()) CanonStatusRow("No cues found.", CanonTone.Warn)
            // ponytail: every cue is a row in one column, as on iOS; a LazyColumn pane if hour-long files make it slow
            cues.forEachIndexed { index, cue ->
                SubtitleCueRow(index, cue, model.isAltered(index), { model.changeCue(index, it) }) {}
            }
            CanonGrid {
                val cell = Modifier.weight(1f).fillMaxHeight()
                CanonButton("Copy", cell, enabled = cues.isNotEmpty(), icon = pencil(R.drawable.pencil_copy), flashesCopied = true, contentDescription = "Copy subtitles") {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", model.output))
                }
                CanonButton("Export", cell, kind = CanonButtonKind.Primary, enabled = cues.isNotEmpty(), icon = pencil(R.drawable.pencil_share)) { model.export(context) }
            }
        }
    }

    val picture = CanonSection.canvas(CanonCanvasSize.Fixed(PICTURE_HEIGHT), background = c.muted) {
        AutoSubtitlePicture(
            source.scan?.peaks,
            source.duration,
            if (busy) model.progress.toDouble() else if (model.finished) 1.0 else null,
            cues,
        )
    }

    CanonSplit(
        sidebar = listOf(controls),
        main = if (busy || model.finished) listOf(result) else emptyList(),
        banner = picture,
    )
    if (showModels) WhisperModelsPage(model.actions) { showModels = false }
    if (showOffer) {
        WhisperOffer(model.actions) {
            showOffer = false
            model.offerSeen()
        }
    }
}

// dt-ios AutoSubtitlePicture
@Composable
private fun AutoSubtitlePicture(peaks: AVEditPeaks?, duration: Double, progress: Double?, cues: List<SubtitleCue>) {
    val c = LocalDelphiColours.current
    val top = LocalCanonCanvas.current.top
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val lane = CUE_LANE.toPx()
            val laneTop = size.height - lane
            val waveTop = top.toPx()
            val wave = Rect(0f, waveTop, size.width, laneTop)
            val hairline = CanonMetrics.hairline.toPx()
            fun x(seconds: Double): Float {
                val share = if (duration > 0) (seconds / duration).coerceIn(0.0, 1.0).toFloat() else 0f
                return if (rightToLeft) size.width * (1 - share) else size.width * share
            }
            drawRect(c.border, Offset(0f, wave.center.y), Size(size.width, hairline))
            drawRect(c.border, Offset(0f, laneTop), Size(size.width, hairline))

            if (peaks != null && peaks.min.isNotEmpty() && size.width > 0) {
                val inset = 6.dp.toPx()
                val half = (wave.height - inset * 2) / 2
                val mid = wave.center.y
                val buckets = peaks.min.size
                val (columns, merge) = AVEditAudioEngine.merge(buckets, size.width)
                val width = max(1f, size.width / columns)
                val bars = Path()
                for (column in 0 until columns) {
                    val from = column * merge
                    val to = min(buckets, (column + 1) * merge)
                    var high = peaks.max[from]
                    var low = peaks.min[from]
                    for (index in from + 1 until to) {
                        high = max(high, peaks.max[index])
                        low = min(low, peaks.min[index])
                    }
                    val left = column.toFloat() / columns * size.width
                    val bx = if (rightToLeft) size.width - left - width else left
                    bars.addRect(Rect(Offset(bx, mid - high * half), Size(width, max(1f, (high - low) * half))))
                }
                drawPath(bars, c.mutedForeground.copy(alpha = 0.45f))
                val reached = progress?.let { x(duration * it) } ?: if (rightToLeft) size.width else 0f
                val (litLeft, litRight) = if (rightToLeft) reached to size.width else 0f to reached
                clipRect(litLeft, wave.top, litRight, wave.bottom) { drawPath(bars, c.primary) }
            }

            if (duration > 0 && cues.isNotEmpty()) {
                val blocks = Path()
                for (cue in cues) {
                    val from = x(cue.start / 1000.0)
                    val to = x(cue.end / 1000.0)
                    blocks.addRect(Rect(Offset(min(from, to) + 0.5f, laneTop + 7.dp.toPx()), Size(max(1f, abs(to - from) - 1), lane - 14.dp.toPx())))
                }
                drawPath(blocks, c.primary)
            }
        }
        val figure = Modifier.align(Alignment.BottomEnd).padding(end = CanonMetrics.panelPadding, bottom = CUE_LANE + 6.dp)
        if (progress != null && (progress < 1 || cues.isEmpty())) {
            // engine may never report
            if (progress > 0) CanonFigure("${(progress * 100).roundToInt()}", figure, unit = "%", halo = c.muted)
        } else if (cues.isNotEmpty()) {
            CanonFigure("${cues.size}", figure, unit = "cues", halo = c.muted)
        }
    }
}
