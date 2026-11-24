package com.delphi.delphitools.tools.audiovideo

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLamp
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonReadoutBand
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.CanonTransport
import com.delphi.delphitools.canon.MenuCellItem
import com.delphi.delphitools.canon.MenuCellPopup
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// a take finishes writing after its page has gone
private val saves = CoroutineScope(Dispatchers.IO + SupervisorJob())

/** The picture runs this far behind capture, so the strip always has peaks up to its head. */
private const val LATENCY_NANOS = 120_000_000L
private const val FOLDER = "VoiceRecordings"

enum class VoicePermission { Undetermined, Granted, Denied }

class VoiceTake(val file: File, val date: Long, val duration: Double) {
    val name: String get() = file.nameWithoutExtension
}

/**
 * dt-ios AVInfoRecorder: microphone capture and the take library. The take is held in memory until Stop, so a scrub back can
 * record over its tail; Stop writes it as AAC (WAV when the encoder fails) into the app's files. A move to the background ends it.
 */
class VoiceRecorderModel(application: Application) : AndroidViewModel(application) {
    var permission by mutableStateOf(VoicePermission.Undetermined)
        private set
    var recording by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    /** Recording position, in seconds; [positionStamp] is System.nanoTime() when it was set. */
    var position by mutableDoubleStateOf(0.0)
        private set
    var positionStamp = 0L
        private set
    var tail = FloatArray(0)
        private set
    var tailStart = 0
        private set
    var takes by mutableStateOf<List<VoiceTake>>(emptyList())
        private set
    var playing by mutableStateOf<File?>(null)
        private set
    var playbackPaused by mutableStateOf(false)
        private set
    var playbackPeaks by mutableStateOf(FloatArray(0))
        private set
    /** Bumped by a seek while playback is paused, which no frame redraws. */
    var seeks by mutableIntStateOf(0)
        private set
    var held: VoiceHeld? = null
        private set
    private var record: AudioRecord? = null
    private var reader: Job? = null
    private var player: MediaPlayer? = null
    private var scan: Job? = null
    private val writing = mutableSetOf<String>()
    private val folder = File(application.filesDir, FOLDER)

    val playbackTime get() = player?.currentPosition?.div(1000.0) ?: 0.0

    init {
        checkPermission()
        refresh()
    }

    fun checkPermission() {
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            permission = VoicePermission.Granted
        }
    }

    fun permissionResult(granted: Boolean) {
        permission = if (granted) VoicePermission.Granted else VoicePermission.Denied
        if (granted) start()
    }

    fun refresh() {
        viewModelScope.launch {
            takes = withContext(Dispatchers.IO) {
                (folder.listFiles() ?: emptyArray())
                    .filter { it.extension.lowercase() in setOf("m4a", "wav") }
                    .map { VoiceTake(it, it.lastModified(), duration(it)) }
                    .sortedByDescending { it.date }
            }
        }
    }

    private fun duration(file: File): Double {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toDoubleOrNull() ?: 0.0) / 1000
        } catch (_: RuntimeException) {
            0.0
        } finally {
            retriever.release()
        }
    }

    fun rename(take: VoiceTake, name: String) {
        val safe = VoiceRecorderEngine.safeName(name, take.name) ?: return
        val target = File(folder, free(safe, take.file.extension))
        if (playing == take.file) stopPlayback()
        take.file.renameTo(target)
        refresh()
    }

    // never overwrite a sibling; in-flight writes count
    private fun free(name: String, extension: String) =
        VoiceRecorderEngine.freeName(name, extension) { File(folder, it).exists() || it in writing }

    fun delete(take: VoiceTake) {
        if (playing == take.file) stopPlayback()
        take.file.delete()
        refresh()
    }

    // MARK: recording

    @SuppressLint("MissingPermission")
    fun start() {
        if (permission != VoicePermission.Granted || recording) return
        stopPlayback()
        val created = listOf(48_000, 44_100).firstNotNullOfOrNull { rate ->
            val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            if (minimum <= 0) return@firstNotNullOfOrNull null
            runCatching {
                AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, max(minimum, rate / 5 * 4))
            }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        } ?: return
        record = created
        held = VoiceHeld(created.sampleRate)
        tail = FloatArray(0)
        tailStart = 0
        if (!capture()) {
            created.release()
            record = null
            held = null
            return
        }
        recording = true
        paused = false
        publish()
    }

    private fun capture(): Boolean {
        val record = record ?: return false
        if (runCatching { record.startRecording() }.isFailure || record.recordingState != AudioRecord.RECORDSTATE_RECORDING) return false
        reader = viewModelScope.launch(Dispatchers.IO) {
            // device buffers of up to 20 ms
            val buffer = FloatArray(max(1, record.sampleRate / 50))
            while (isActive) {
                val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read < 0) break
                if (read == 0) continue
                val floats = buffer.copyOf(read)
                withContext(Dispatchers.Main) { absorb(floats) }
            }
        }
        return true
    }

    private fun absorb(floats: FloatArray) {
        val held = held ?: return
        if (!recording || paused) return
        held.append(floats)
        publish()
        if (held.duration >= VoiceRecorderEngine.MAX_SECONDS) stop()
    }

    private fun publish() {
        val held = held ?: return
        position = held.position
        positionStamp = System.nanoTime()
    }

    fun togglePause() {
        if (!recording) return
        if (paused) {
            if (!capture()) return
            val held = held
            if (held != null && held.head < held.count) {
                tailStart = held.head / held.window
                tail = held.peaks.copyOfRange(min(tailStart, held.peakCount), held.peakCount)
            }
            paused = false
            publish()
        } else {
            reader?.cancel()
            reader = null
            runCatching { record?.stop() }
            paused = true
        }
    }

    /** Moves the recording position through the held take while paused; resuming records over what lies after it. */
    fun scrub(seconds: Double) {
        if (!recording || !paused) return
        held?.scrub(seconds)
        publish()
    }

    fun stop() {
        if (!recording) return
        reader?.cancel()
        reader = null
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null
        recording = false
        paused = false
        position = 0.0
        val held = held ?: return
        this.held = null
        if (held.count == 0) return
        val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        folder.mkdirs()
        val name = free("recording-$stamp", "m4a")
        writing += name
        saves.launch {
            save(held, File(folder, name))
            withContext(Dispatchers.Main) {
                writing -= name
                refresh()
            }
        }
    }

    /** Stops capture and playback when the app goes to the background or the page closes; the take is kept. */
    fun leave() {
        stopPlayback()
        stop()
    }

    // MARK: playback

    fun play(take: VoiceTake) {
        if (recording) return
        if (playing == take.file) return stopPlayback()
        stopPlayback()
        val created = MediaPlayer()
        val ok = runCatching {
            created.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            created.setDataSource(take.file.path)
            created.prepare()
            created.setOnCompletionListener { if (player === it) stopPlayback() }
            created.start()
        }.isSuccess
        if (!ok) return created.release()
        player = created
        playing = take.file
        playbackPaused = false
        val file = take.file
        scan = viewModelScope.launch {
            val buckets = max(1, (take.duration / VoiceRecorderEngine.PEAK_INTERVAL).toInt())
            val scanned = AudioAtlasMedia.scan(getApplication(), Uri.fromFile(file), null, buckets)?.first
            if (playing != file) return@launch
            playbackPeaks = scanned?.peaks?.let { peaks -> FloatArray(peaks.min.size) { max(abs(peaks.min[it]), abs(peaks.max[it])) } } ?: FloatArray(0)
        }
    }

    fun pausePlayback() {
        if (playing == null || playbackPaused) return
        player?.pause()
        playbackPaused = true
    }

    fun resumePlayback() {
        if (playing == null || !playbackPaused) return
        player?.start()
        playbackPaused = false
    }

    /** Clamped short of the end, where the player would stop. */
    fun seekPlayback(seconds: Double) {
        val player = player ?: return
        val end = max(0.0, player.duration / 1000.0 - 0.05)
        player.seekTo((seconds.coerceIn(0.0, end) * 1000).roundToInt().toLong(), MediaPlayer.SEEK_CLOSEST)
        seeks++
    }

    fun stopPlayback() {
        scan?.cancel()
        scan = null
        player?.release()
        player = null
        playing = null
        playbackPaused = false
        playbackPeaks = FloatArray(0)
    }

    override fun onCleared() = leave()

    private companion object {
        /** AAC, or uncompressed WAV beside it when the encoder fails, so the take is kept. */
        fun save(held: VoiceHeld, file: File) {
            try {
                writeAac(held, file)
            } catch (_: Exception) {
                file.delete()
                runCatching { writeWav(held, File(file.parentFile, file.nameWithoutExtension + ".wav")) }
            }
        }

        fun writeAac(held: VoiceHeld, file: File) {
            val format = MediaFormat.createAudioFormat(AVEditEngine.AAC_MIME, held.rate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            }
            val encoder = MediaCodec.createEncoderByType(AVEditEngine.AAC_MIME)
            var muxer: MediaMuxer? = null
            try {
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                encoder.start()
                val mux = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                muxer = mux
                var track = -1
                var offset = 0
                var inputDone = false
                val info = MediaCodec.BufferInfo()
                while (true) {
                    if (!inputDone) {
                        val index = encoder.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            val buffer = encoder.getInputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                            val frames = min(buffer.remaining() / 2, held.count - offset)
                            val pts = offset * 1_000_000L / held.rate
                            if (frames <= 0) {
                                encoder.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                buffer.asShortBuffer().put(held.samples, offset, frames)
                                encoder.queueInputBuffer(index, 0, frames * 2, pts, 0)
                                offset += frames
                            }
                        }
                    }
                    val index = encoder.dequeueOutputBuffer(info, 10_000)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        track = mux.addTrack(encoder.outputFormat)
                        mux.start()
                    } else if (index >= 0) {
                        val data = encoder.getOutputBuffer(index)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0 && track >= 0) {
                            data.position(info.offset)
                            data.limit(info.offset + info.size)
                            mux.writeSampleData(track, data, info)
                        }
                        encoder.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
                mux.stop()
            } finally {
                runCatching { encoder.stop() }
                encoder.release()
                runCatching { muxer?.release() }
            }
        }

        fun writeWav(held: VoiceHeld, file: File) {
            RandomAccessFile(file, "rw").use { out ->
                out.setLength(0)
                out.write(AVEditEngine.wavHeader(held.rate, 1, held.count * 2L))
                val chunk = ByteBuffer.allocate(held.rate * 2).order(ByteOrder.LITTLE_ENDIAN)
                var start = 0
                while (start < held.count) {
                    val count = min(held.rate, held.count - start)
                    chunk.clear()
                    chunk.asShortBuffer().put(held.samples, start, count)
                    out.write(chunk.array(), 0, count * 2)
                    start += count
                }
            }
        }
    }
}

@Composable
fun VoiceRecorder(model: VoiceRecorderModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), model::permissionResult)
    val leave by rememberUpdatedState(model::leave)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (activity?.isChangingConfigurations != true) leave()
                Lifecycle.Event.ON_START -> {
                    model.checkPermission()
                    model.refresh()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (activity?.isChangingConfigurations != true) leave()
        }
    }

    val playing = model.playing != null
    val active = model.recording || playing
    val held = if (playing) model.playbackPaused else model.paused
    // every display frame while capturing or playing
    var frame by remember { mutableLongStateOf(0L) }
    val running = model.recording && !model.paused || playing && !model.playbackPaused
    LaunchedEffect(running) {
        while (running) withFrameNanos { frame = it }
    }
    var renaming by remember { mutableStateOf<VoiceTake?>(null) }
    var platterPaused by remember { mutableStateOf(false) }

    /** What the strip, the platter and the counter show now. */
    fun shown(): Double {
        frame
        model.seeks
        if (playing) return model.playbackTime
        var position = model.position
        if (model.recording && !model.paused) {
            val ahead = (System.nanoTime() - model.positionStamp - LATENCY_NANOS) / 1e9
            position = max(0.0, min(position, position + ahead))
        }
        return position
    }

    val library = CanonSection.of {
        CanonRows {
            if (model.permission == VoicePermission.Denied) {
                CanonStatusRow("Microphone access is off. Turn it on in Settings to record.", CanonTone.Warn)
                CanonButton("Open Settings", kind = CanonButtonKind.Primary, icon = Icons.Outlined.Settings) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }
            }
            CanonHeading("Takes")
            if (model.takes.isEmpty()) CanonEmptyRow("No takes")
            for (take in model.takes) {
                VoiceTakeRow(
                    take,
                    take.file == model.playing,
                    onPlay = { model.play(take) },
                    onRename = { renaming = take },
                    onShare = { avEditShare(context, take.file) },
                    onDelete = { model.delete(take) },
                )
            }
        }
    }
    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(150.dp)) {
        val at = shown()
        val transport = if (playing) {
            CanonTransport(at, playing = !model.playbackPaused, paused = model.playbackPaused)
        } else {
            CanonTransport(if (model.recording) at else null, paused = model.paused, recording = model.recording)
        }
        CanonReadoutBand(transport, Modifier.fillMaxSize(), lamps = listOf(CanonLamp.Record, CanonLamp.Play, CanonLamp.Pause)) { playhead ->
            VoiceTakeStrip(model, at, playhead != null)
        }
    }

    CanonSplit(
        sidebar = listOf(library),
        main = emptyList(),
        banner = banner,
        bannerSticks = true,
        footer = {
            Column(Modifier.fillMaxWidth().background(c.border), verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
                if (model.recording) {
                    Text(
                        "Hold here to pause or scrub to overwrite",
                        Modifier.fillMaxWidth().background(c.card).padding(horizontal = 12.dp, vertical = 6.dp),
                        style = CanonText.caption,
                        color = c.mutedForeground,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                CanonGrid {
                    if (!active) {
                        CanonButton("Record", Modifier.weight(1f).fillMaxHeight(), kind = CanonButtonKind.Primary, icon = Icons.Filled.RadioButtonChecked) {
                            if (model.permission == VoicePermission.Granted) model.start() else ask.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        CanonButton("", Modifier.width(64.dp).fillMaxHeight(), icon = Icons.Filled.Stop, contentDescription = "Stop") {
                            if (playing) model.stopPlayback() else model.stop()
                        }
                        VoicePlatter(
                            shown(),
                            !held,
                            Modifier.weight(1f).fillMaxHeight().heightIn(min = CanonMetrics.primaryHeight),
                            onTouchDown = {
                                if (model.playing != null) {
                                    platterPaused = !model.playbackPaused
                                    model.pausePlayback()
                                } else {
                                    platterPaused = model.recording && !model.paused
                                    if (platterPaused) model.togglePause()
                                }
                            },
                            onDrag = { seconds ->
                                if (model.playing != null) model.seekPlayback(model.playbackTime + seconds) else model.scrub(model.position + seconds)
                            },
                            onTick = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
                            onTouchUp = {
                                if (platterPaused) {
                                    if (model.playing != null) model.resumePlayback() else if (model.recording && model.paused) model.togglePause()
                                }
                                platterPaused = false
                            },
                        )
                        CanonButton(
                            "",
                            Modifier.width(64.dp).fillMaxHeight(),
                            icon = if (held) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (held) "Resume" else "Pause",
                        ) {
                            when {
                                !playing -> model.togglePause()
                                model.playbackPaused -> model.resumePlayback()
                                else -> model.pausePlayback()
                            }
                        }
                    }
                }
            }
        },
    )

    renaming?.let { take -> VoiceRenameDialog(take.name, onDismiss = { renaming = null }) { model.rename(take, it) } }
}

/** dt-ios VoiceTakeStrip: the held take as a waveform under a fixed head; after a scrub back, what lies ahead of the head is dim. */
@Composable
private fun VoiceTakeStrip(model: VoiceRecorderModel, position: Double, showsHead: Boolean) {
    val c = LocalDelphiColours.current
    val flipped = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(Modifier.fillMaxSize().background(c.muted).clearAndSetSemantics {}) {
        val step = VoiceRecorderEngine.POINTS_PER_PEAK.dp.toPx().roundToInt().toFloat().coerceAtLeast(1f)
        val room = (size.width / step).toInt() + 2
        val held = model.held
        val strip = when {
            model.playing != null -> VoiceRecorderEngine.strip(model.playbackPeaks, model.playbackPeaks.size, position, position, FloatArray(0), 0, room, step)
            held != null -> VoiceRecorderEngine.strip(held.peaks, held.peakCount, position, model.position, model.tail, model.tailStart, room, step)
            else -> VoiceStripFrame(FloatArray(0), FloatArray(0), 0, 0f)
        }
        val mid = (size.height / 2).roundToInt().toFloat()
        val half = (size.height / 2 - 8.dp.toPx()).roundToInt()
        val headX = (size.width * VoiceRecorderEngine.HEAD_SHARE).roundToInt().toFloat().let { if (flipped) size.width - it else it }
        val lit = c.primary
        val dim = c.mutedForeground.copy(alpha = 0.45f)
        fun column(offset: Int, peak: Float, colour: androidx.compose.ui.graphics.Color) {
            val x = if (flipped) headX - (offset + 1) * step + strip.slip else headX + offset * step - strip.slip
            val height = max(1f, (peak * half).roundToInt() * 2f)
            drawRect(colour, Offset(x, mid - height / 2), Size(step, height))
        }
        // overwrite target, grey
        drawRect(if (flipped) dim else lit, Offset(0f, mid - 0.5f), Size(headX, 1f))
        drawRect(if (flipped) lit else dim, Offset(headX, mid - 0.5f), Size(size.width - headX, 1f))
        strip.behind.forEachIndexed { index, peak -> column(index - strip.behind.size, peak, lit) }
        strip.ahead.forEachIndexed { index, peak -> column(strip.gap + index, peak, dim) }
        if (showsHead) {
            val frame = CanonMetrics.frame.toPx()
            drawRect(c.foreground, Offset(headX - frame / 2, 0f), Size(frame, size.height))
        }
    }
}

/**
 * dt-ios VoicePlatter: stripes that run with the take at the strip's rate. Touch down pauses, a drag moves the position through
 * the held take (the tape follows the finger), touch up resumes.
 */
@Composable
private fun VoicePlatter(
    position: Double,
    isActive: Boolean,
    modifier: Modifier,
    onTouchDown: () -> Unit,
    onDrag: (Double) -> Unit,
    onTick: () -> Unit,
    onTouchUp: () -> Unit,
) {
    val c = LocalDelphiColours.current
    val backwards = LocalLayoutDirection.current == LayoutDirection.Rtl
    val down by rememberUpdatedState(onTouchDown)
    val drag by rememberUpdatedState(onDrag)
    val tick by rememberUpdatedState(onTick)
    val up by rememberUpdatedState(onTouchUp)
    val at by rememberUpdatedState(position)
    Canvas(
        modifier
            .background(c.card)
            .semantics { contentDescription = "Position" }
            .pointerInput(backwards) {
                awaitEachGesture {
                    val first = awaitFirstDown()
                    first.consume()
                    val speed = VoiceRecorderEngine.PLATTER_SPEED.dp.toPx()
                    val period = VoiceRecorderEngine.STRIPE_PERIOD
                    var ticked = (at * VoiceRecorderEngine.PLATTER_SPEED / period).toInt()
                    var lastX = first.position.x
                    down()
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == first.id } ?: break
                            if (!change.pressed) break
                            var dx = change.position.x - lastX
                            lastX = change.position.x
                            if (backwards) dx = -dx
                            change.consume()
                            if (dx == 0f) continue
                            drag(-dx / speed.toDouble())
                            val stripes = (at * VoiceRecorderEngine.PLATTER_SPEED / period).toInt()
                            if (stripes != ticked) {
                                ticked = stripes
                                tick()
                            }
                        }
                    } finally {
                        up()
                    }
                }
            }
    ) {
        val period = VoiceRecorderEngine.STRIPE_PERIOD.dp.toPx()
        var offset = (position * VoiceRecorderEngine.PLATTER_SPEED.dp.toPx()).mod(period.toDouble()).toFloat()
        if (backwards) offset = -offset
        val height = size.height
        val path = Path()
        var x = -offset - period - height
        // 45 degree hatch
        while (x < size.width + period) {
            path.moveTo(x, height)
            path.lineTo(x + height, 0f)
            x += period
        }
        drawPath(path, c.mutedForeground.copy(alpha = if (isActive) 0.35f else 0.15f), style = Stroke(VoiceRecorderEngine.STRIPE_WIDTH.dp.toPx()))
    }
}

/** dt-ios VoiceTakeRow: a play cell, the name over its length and date, and a menu cell. */
@Composable
private fun VoiceTakeRow(
    take: VoiceTake,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalDelphiColours.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = CanonMetrics.rowHeight).background(c.border),
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        CanonButton(
            "",
            Modifier.width(52.dp).fillMaxHeight(),
            icon = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Stop" else "Play",
            onClick = onPlay,
        )
        Column(
            Modifier.weight(1f).fillMaxHeight().background(c.card).padding(start = CanonMetrics.panelPadding, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, androidx.compose.ui.Alignment.CenterVertically),
        ) {
            Text(take.name, style = CanonText.bodyMedium, color = c.foreground, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(
                VoiceRecorderEngine.length(take.duration) + " · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(take.date)),
                style = CanonText.caption,
                color = c.mutedForeground,
                maxLines = 1,
            )
        }
        Box(Modifier.width(52.dp).fillMaxHeight()) {
            CanonButton("", Modifier.fillMaxSize(), icon = Icons.Outlined.MoreHoriz, contentDescription = "More") { menu = true }
            MenuCellPopup(menu, { menu = false }) {
                MenuCellItem("Rename", icon = Icons.Outlined.Edit) {
                    menu = false
                    onRename()
                }
                MenuCellItem("Share", icon = Icons.Outlined.Share) {
                    menu = false
                    onShare()
                }
                MenuCellItem("Delete", icon = Icons.Outlined.Delete) {
                    menu = false
                    onDelete()
                }
            }
        }
    }
}

@Composable
private fun VoiceRenameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = LocalDelphiColours.current
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(320.dp).background(c.border).padding(CanonMetrics.frame), verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
            CanonHeading("Rename")
            CanonTextCell(text, { text = it }, Modifier.fillMaxWidth(), placeholder = "Name", keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done))
            CanonGrid {
                CanonButton("Cancel", Modifier.weight(1f).fillMaxHeight(), onClick = onDismiss)
                CanonButton("Save", Modifier.weight(1f).fillMaxHeight(), kind = CanonButtonKind.Primary) {
                    onSave(text)
                    onDismiss()
                }
            }
        }
    }
}
