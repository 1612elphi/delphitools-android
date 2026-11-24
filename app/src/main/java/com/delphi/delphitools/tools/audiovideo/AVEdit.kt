package com.delphi.delphitools.tools.audiovideo

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.text.format.Formatter
import android.view.Surface
import android.view.TextureView
import android.webkit.MimeTypeMap
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.delphi.delphitools.canon.CanonBar
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.CanonTransport
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

private val cleanup = CoroutineScope(Dispatchers.IO + SupervisorJob())

internal fun avEditDiscard(dir: File?) {
    if (dir != null) cleanup.launch { dir.deleteRecursively() }
}

/** A fresh cache folder a video tool writes its results under; an earlier run's folders go. */
internal fun avEditRoot(context: Context, tool: String): File {
    val base = File(File(context.cacheDir, "media"), tool)
    val session = File(base, UUID.randomUUID().toString())
    cleanup.launch { base.listFiles()?.filter { it != session }?.forEach { it.deleteRecursively() } }
    return session
}

/** dt-ios AVEditVideo: the source's facts, thumbnails and, when asked for, the peaks of its first audio track. */
class AVEditSource(private val context: Context, private val scope: CoroutineScope) {
    var uri by mutableStateOf<Uri?>(null)
        private set
    var name by mutableStateOf("")
        private set
    var facts by mutableStateOf<AVEditFacts?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var thumbnails by mutableStateOf<List<ImageBitmap>>(emptyList())
        private set
    var peaks by mutableStateOf<AVEditPeaks?>(null)
        private set
    private var tasks = emptyList<Job>()

    val duration get() = facts?.duration ?: 0.0
    val baseName get() = AVEditEngine.baseName(name, "video")

    fun load(uri: Uri, thumbnailCount: Int, peakBuckets: Int = 0, then: (AVEditFacts) -> Unit = {}) {
        clear()
        this.uri = uri
        tasks = listOf(scope.launch {
            name = withContext(Dispatchers.IO) { context.contentResolver.canonDisplayName(uri) ?: "" }
            val loaded = AVEditMedia.facts(context, uri)
            if (loaded == null) {
                failed = true
                return@launch
            }
            facts = loaded
            then(loaded)
            if (peakBuckets > 0 && loaded.audioTracks > 0) {
                tasks += scope.launch { peaks = AVEditMedia.peaks(context, uri, loaded.duration, peakBuckets) }
            }
            thumbnails = AVEditMedia.thumbnails(context, uri, loaded.duration, thumbnailCount)
        })
    }

    fun clear() {
        tasks.forEach { it.cancel() }
        tasks = emptyList()
        uri = null
        name = ""
        facts = null
        failed = false
        thumbnails = emptyList()
        peaks = null
    }
}

/** dt-ios AVEditJob: one export at a time; a failed, cancelled or superseded run deletes its file. */
class AVEditJob(private val scope: CoroutineScope, private val root: File) {
    var busy by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var failed by mutableStateOf(false)
        private set
    var output by mutableStateOf<File?>(null)
        private set
    private var task: Job? = null
    private var run = 0

    /** [work] writes to the file it is given, off the main thread, and reports progress from 0 to 1. */
    fun start(filename: String, work: suspend (File, AVEditProgress) -> Unit) {
        reset()
        busy = true
        val run = run
        val dir = File(root, UUID.randomUUID().toString())
        val file = File(dir, FileExport.safeFilename(filename))
        task = scope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    dir.mkdirs()
                    work(file) { if (this@AVEditJob.run == run) progress = it.toFloat() }
                }
                true
            } catch (e: CancellationException) {
                withContext(NonCancellable) { avEditDiscard(dir) }
                throw e
            } catch (e: Exception) {
                android.util.Log.w("AVEdit", "export failed: $filename", e)
                false
            }
            if (!ok) avEditDiscard(dir)
            if (this@AVEditJob.run != run) return@launch avEditDiscard(dir)
            output = if (ok) file else null
            failed = !ok
            busy = false
        }
    }

    fun reset() {
        run++
        task?.cancel()
        task = null
        avEditDiscard(output?.parentFile)
        busy = false
        failed = false
        progress = 0f
        output = null
    }
}

private fun ms(seconds: Double) = (seconds * 1000).roundToInt().toLong()

/**
 * dt-ios AVEditPlayback: MediaPlayer over [range], which Play plays and Stop returns to; a stopped player shows the range's first frame.
 * Scrubbing reaches [limits], or the range when null or looping.
 */
class AVEditPlayback(private val context: Context, private val scope: CoroutineScope) {
    var playing by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    var playhead by mutableStateOf<Double?>(null)
        private set
    var loops by mutableIntStateOf(0)
        private set
    var looping by mutableStateOf(false)
    var ready by mutableStateOf(false)
        private set
    var range = 0.0..0.0
    var limits: ClosedFloatingPointRange<Double>? = null
    var fps = 30.0
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var ticker: Job? = null
    private var wrapping = false

    val transport get() = CanonTransport(playhead, playing, paused, looping = looping, loops = loops)

    fun open(uri: Uri, range: ClosedFloatingPointRange<Double>) {
        release()
        this.range = range
        val created = MediaPlayer()
        player = created
        runCatching {
            created.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build()
            )
            created.setDataSource(context, uri)
            created.setSurface(surface)
            created.setOnPreparedListener {
                if (player !== it) return@setOnPreparedListener
                ready = true
                it.seekTo(ms(this.range.start), MediaPlayer.SEEK_CLOSEST)
            }
            created.setOnCompletionListener { if (player === it) ended() }
            created.prepareAsync()
        }.onFailure { release() }
    }

    fun attach(surface: Surface?) {
        this.surface = surface
        val player = player ?: return
        runCatching { player.setSurface(surface) }
        // a new surface stays black until the next frame
        if (surface != null && ready && !playing) player.seekTo(ms(playhead ?: range.start), MediaPlayer.SEEK_CLOSEST)
    }

    fun position(): Double? = player?.takeIf { ready }?.currentPosition?.div(1000.0)

    fun toggle() = if (playing) pause() else play()

    fun play() {
        val player = player?.takeIf { ready } ?: return
        val at = playhead?.takeIf { it >= range.start && it < range.endInclusive } ?: range.start
        if (abs(player.currentPosition / 1000.0 - at) > 0.05) player.seekTo(ms(at), MediaPlayer.SEEK_CLOSEST)
        player.start()
        playing = true
        paused = false
        playhead = at
        ticker?.cancel()
        ticker = scope.launch {
            while (playing) {
                val now = position() ?: break
                if (wrapping) wrapping = now >= range.endInclusive
                if (!wrapping && now >= range.endInclusive) ended() else if (!wrapping) playhead = now
                delay(33)
            }
        }
    }

    fun pause() {
        if (!playing) return
        ticker?.cancel()
        player?.pause()
        playing = false
        paused = true
        playhead = position()
    }

    fun stop() {
        ticker?.cancel()
        player?.takeIf { ready }?.let {
            if (it.isPlaying) it.pause()
            it.seekTo(ms(range.start), MediaPlayer.SEEK_CLOSEST)
        }
        playing = false
        paused = false
        playhead = null
    }

    fun scrub(seconds: Double) {
        val player = player?.takeIf { ready } ?: return
        val reach = if (looping) range else limits ?: range
        val target = seconds.coerceIn(reach.start, reach.endInclusive)
        player.seekTo(ms(target), MediaPlayer.SEEK_CLOSEST)
        playhead = target
        if (!playing) paused = true
    }

    fun step(frames: Int) {
        pause()
        scrub((playhead ?: range.start) + frames / fps)
    }

    private fun ended() {
        if (!playing) return
        val player = player ?: return
        if (!looping) return stop()
        wrapping = true
        player.seekTo(ms(range.start), MediaPlayer.SEEK_CLOSEST)
        if (!player.isPlaying) player.start()
        loops++
        playhead = range.start
    }

    fun release() {
        ticker?.cancel()
        player?.release()
        player = null
        ready = false
        playing = false
        paused = false
        playhead = null
        wrapping = false
    }
}

/** Closes the tool when its page goes, not on a rotation; pauses the player when the app leaves the screen. */
@Composable
internal fun AVEditPageEffects(playback: AVEditPlayback?, close: () -> Unit) {
    val activity = LocalContext.current as? Activity
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val onClose by rememberUpdatedState(close)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) playback?.pause() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (activity?.isChangingConfigurations != true) onClose()
        }
    }
}

@Composable
internal fun AVEditDrop(onFile: (Uri) -> Unit) {
    CanonDropZone(
        "Choose video file",
        icon = Icons.Outlined.Movie,
        mimeTypes = arrayOf("video/*"),
        photos = PickVisualMedia.VideoOnly,
        paste = false,
        onFiles = { it.firstOrNull()?.let(onFile) },
    )
}

/** CanonRow.file: the source's name and facts, or a warning in red, with Clear. */
@Composable
internal fun AVEditFileRow(name: String, detail: String, warning: String?, onClear: () -> Unit) {
    val c = LocalDelphiColours.current
    CanonRow(
        name,
        detail = warning ?: detail.ifEmpty { " " },
        detailColour = if (warning != null) c.destructive else null,
        flush = true,
    ) { CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = onClear) }
}

@Composable
internal fun AVEditPlayerView(playback: AVEditPlayback, aspect: Float, modifier: Modifier = Modifier, onTexture: ((TextureView?) -> Unit)? = null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { context ->
                TextureView(context).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        private var surface: Surface? = null

                        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                            surface = Surface(texture).also(playback::attach)
                            onTexture?.invoke(this@apply)
                        }

                        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                            onTexture?.invoke(null)
                            playback.attach(null)
                            surface?.release()
                            surface = null
                            return true
                        }

                        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

                        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                    }
                }
            },
            modifier = Modifier.aspectRatio(aspect.coerceAtLeast(0.01f), matchHeightConstraintsFirst = aspect < 1),
        )
    }
}

/** A tap or a sideways drag reports the time under the finger. */
private fun Modifier.scrubs(at: (Float) -> Double?, onScrub: ((Double) -> Unit)?): Modifier =
    if (onScrub == null) this
    else pointerInput(Unit) { detectTapGestures { offset -> at(offset.x)?.let(onScrub) } }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(onDragStart = { offset -> at(offset.x)?.let(onScrub) }) { change, _ ->
                change.consume()
                at(change.position.x)?.let(onScrub)
            }
        }

/** Time axis of a strip [width] px wide with [inset] px either side, mirrored right to left. */
internal class AVEditAxis(val duration: Double, val width: Float, val inset: Float, val rightToLeft: Boolean) {
    val strip get() = max(1f, width - inset * 2)

    fun x(seconds: Double): Float {
        val share = if (duration > 0) (seconds / duration).coerceIn(0.0, 1.0).toFloat() else 0f
        val x = inset + share * strip
        return if (rightToLeft) width - x else x
    }

    fun seconds(x: Float): Double? {
        if (duration <= 0 || width <= 0) return null
        val share = ((x - inset) / strip).coerceIn(0f, 1f)
        return (if (rightToLeft) 1 - share else share) * duration
    }
}

private class AxisRef {
    var axis: AVEditAxis? = null
}

private fun DrawScope.head(axis: AVEditAxis, playhead: Double?, colour: androidx.compose.ui.graphics.Color) {
    if (playhead == null || axis.duration <= 0) return
    val frame = CanonMetrics.frame.toPx()
    drawRect(colour, Offset(axis.x(playhead) - frame / 2, 0f), Size(frame, size.height))
}

/**
 * dt-ios AVEditFilmstrip: thumbnails of the whole file side by side, as many as fit, under a playhead.
 * [lit] marks are flagged full-height ticks, [dim] marks short ticks along the bottom; frames outside [selection] are dimmed.
 */
@Composable
internal fun AVEditFilmstrip(
    thumbnails: List<ImageBitmap>,
    duration: Double,
    playhead: Double?,
    modifier: Modifier = Modifier,
    inset: Dp = 0.dp,
    selection: ClosedFloatingPointRange<Double>? = null,
    lit: List<Double> = emptyList(),
    dim: List<Double> = emptyList(),
    onScrub: ((Double) -> Unit)? = null,
) {
    val c = LocalDelphiColours.current
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val axis = remember { AxisRef() }
    val scrub by rememberUpdatedState(onScrub)
    Canvas(
        modifier
            .fillMaxSize()
            .background(c.muted)
            .scrubs({ axis.axis?.seconds(it) }, onScrub?.let { { seconds: Double -> scrub?.invoke(seconds) } })
    ) {
        val current = AVEditAxis(duration, size.width, inset.toPx(), rightToLeft).also { axis.axis = it }
        val first = thumbnails.firstOrNull()
        if (first != null && size.height > 0) {
            val aspect = first.width.toFloat() / max(1, first.height)
            val count = max(1, ceil(current.strip / max(1f, size.height * aspect)).toInt())
            val cell = current.strip / count
            AVEditEngine.spread(count, thumbnails.size).forEachIndexed { index, pick ->
                val image = thumbnails[pick]
                val slot = if (rightToLeft) count - 1 - index else index
                // aspect fill: crop the source to the cell's shape
                val scale = max(cell / image.width, size.height / image.height)
                val srcWidth = (cell / scale).roundToInt().coerceIn(1, image.width)
                val srcHeight = (size.height / scale).roundToInt().coerceIn(1, image.height)
                drawImage(
                    image,
                    srcOffset = IntOffset((image.width - srcWidth) / 2, (image.height - srcHeight) / 2),
                    srcSize = IntSize(srcWidth, srcHeight),
                    dstOffset = IntOffset((current.inset + slot * cell).roundToInt(), 0),
                    dstSize = IntSize(ceil(cell).toInt(), size.height.roundToInt()),
                )
            }
        }
        if (duration > 0 && selection != null) {
            val (left, right) = listOf(current.x(selection.start), current.x(selection.endInclusive)).sorted()
            val shade = c.background.copy(alpha = 0.7f)
            drawRect(shade, Offset.Zero, Size(left, size.height))
            drawRect(shade, Offset(right, 0f), Size(size.width - right, size.height))
        }
        if (duration > 0) {
            val short = (size.height * 0.3f).roundToInt().toFloat()
            val tick = 2.dp.toPx()
            for (seconds in dim) drawRect(c.background.copy(alpha = 0.85f), Offset(current.x(seconds) - tick / 2, size.height - short), Size(tick, short))
            val flag = 8.dp.toPx()
            for ((colour, grow) in listOf(c.background to 1.dp.toPx(), c.primary to 0f)) {
                for (seconds in lit) {
                    val x = current.x(seconds)
                    drawRect(colour, Offset(x - tick / 2 - grow, 0f), Size(tick + grow * 2, size.height))
                    val flagX = if (rightToLeft) x + tick / 2 - flag else x - tick / 2
                    drawRect(colour, Offset(flagX - grow, -grow), Size(flag + grow * 2, flag + grow * 2))
                }
            }
        }
        head(current, playhead, c.foreground)
    }
}

/** AudioWaveformView: each peak span as a bar, played spans in the accent; [removed] dims it and rules it through. */
@Composable
internal fun AVEditWaveform(
    peaks: AVEditPeaks?,
    duration: Double,
    playhead: Double?,
    modifier: Modifier = Modifier,
    removed: Boolean = false,
    onScrub: ((Double) -> Unit)? = null,
) {
    val c = LocalDelphiColours.current
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val axis = remember { AxisRef() }
    val scrub by rememberUpdatedState(onScrub)
    Box(
        modifier
            .fillMaxSize()
            .background(c.muted)
            .scrubs({ axis.axis?.seconds(it) }, onScrub?.let { { seconds: Double -> scrub?.invoke(seconds) } })
    ) {
        Canvas(Modifier.fillMaxSize().alpha(if (removed) 0.3f else 1f)) {
            val current = AVEditAxis(duration, size.width, 0f, rightToLeft).also { axis.axis = it }
            val count = peaks?.min?.size ?: 0
            if (peaks != null && count > 0) {
                val bar = size.width / count
                val middle = size.height / 2
                val played = playhead?.let(current::x)
                for (index in 0 until count) {
                    val slot = if (rightToLeft) count - 1 - index else index
                    val x = slot * bar
                    val top = middle - peaks.max[index] * middle * 0.9f
                    val bottom = middle - peaks.min[index] * middle * 0.9f
                    val isPlayed = played != null && if (rightToLeft) x >= played else x + bar <= played
                    drawRect(
                        if (isPlayed) c.primary else c.mutedForeground,
                        Offset(x, top),
                        Size(max(1f, bar * 0.8f), max(1f, bottom - top)),
                    )
                }
            }
            head(current, playhead, c.foreground)
        }
        if (removed) {
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(CanonMetrics.frame).background(c.destructive))
        }
    }
}

internal val FILMSTRIP_HEIGHT = 40.dp
internal val WAVEFORM_HEIGHT = 56.dp

/** dt-ios AVEditTrackStrips: a filmstrip over the audio waveform on one time axis, with one playhead and one scrub. */
@Composable
internal fun AVEditTrackStrips(source: AVEditSource, playhead: Double?, removed: Boolean, onScrub: (Double) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        AVEditFilmstrip(source.thumbnails, source.duration, playhead, Modifier.height(FILMSTRIP_HEIGHT), onScrub = onScrub)
        AVEditWaveform(source.peaks, source.duration, playhead, Modifier.weight(1f), removed, onScrub)
    }
}

private fun mimeType(file: File) = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "application/octet-stream"

internal fun avEditShare(context: Context, file: File) {
    context.startActivity(FileExport.chooser(context, file, mimeType(file)))
}

private fun preview(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType(file)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(view)
    } catch (_: ActivityNotFoundException) {
        avEditShare(context, file)
    }
}

/** dt-ios AVEditJobPanel: the export button, progress with Cancel, and the finished file with Preview and Share, as rows. */
@Composable
internal fun AVEditJobPanel(job: AVEditJob, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, failure: String, enabled: Boolean, oversized: Boolean = true, onStart: () -> Unit) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    if (job.busy) {
        CanonRow("Processing…") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground)
                Text("${(job.progress * 100).roundToInt()}%", style = CanonText.footnoteSemibold, color = c.mutedForeground)
            }
        }
        CanonBar(job.progress, Modifier.fillMaxWidth().height(8.dp))
        CanonButton("Cancel", icon = Icons.Outlined.Close, onClick = job::reset)
        return
    }
    CanonButton(title, kind = CanonButtonKind.Primary, enabled = enabled, icon = icon, oversized = oversized, onClick = onStart)
    if (job.failed) CanonStatusRow(failure, CanonTone.Warn)
    val output = job.output ?: return
    CanonHeading("Result")
    CanonRow(output.name, detail = "${output.extension} · ${Formatter.formatShortFileSize(context, output.length())}")
    CanonGrid {
        CanonButton("Preview", Modifier.weight(1f).fillMaxHeight(), icon = Icons.Outlined.Visibility) { preview(context, output) }
        CanonButton("Share", Modifier.weight(1f).fillMaxHeight(), kind = CanonButtonKind.Primary, icon = Icons.Outlined.Share) { avEditShare(context, output) }
    }
}

/** CanonRows.setSections: a probe's sections as headings over tiles, or a None row. */
@Composable
internal fun AVEditDetails(sections: List<AVEditProbeSection>) {
    if (sections.isEmpty()) {
        CanonHeading("Details")
        CanonEmptyRow("None")
        return
    }
    for (section in sections) {
        CanonHeading(section.title)
        CanonTiles(section.tiles)
    }
}

@Composable
internal fun RowScope.AVEditLoopButton(playback: AVEditPlayback, enabled: Boolean) {
    CanonButton("Loop", Modifier.weight(1f).fillMaxHeight(), selected = playback.looping, enabled = enabled, icon = Icons.Filled.Repeat) {
        playback.looping = !playback.looping
    }
}

@Composable
internal fun RowScope.AVEditPlayButton(playback: AVEditPlayback, enabled: Boolean, onPlay: () -> Unit = playback::toggle) {
    CanonButton(
        if (playback.playing) "Pause" else "Play",
        Modifier.weight(1f).fillMaxHeight(),
        kind = CanonButtonKind.Primary,
        enabled = enabled,
        icon = if (playback.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
        onClick = onPlay,
    )
}

@Composable
internal fun RowScope.AVEditStopButton(playback: AVEditPlayback) {
    CanonButton("Stop", Modifier.weight(1f).fillMaxHeight(), enabled = playback.playing || playback.paused, icon = Icons.Filled.Stop, onClick = playback::stop)
}
