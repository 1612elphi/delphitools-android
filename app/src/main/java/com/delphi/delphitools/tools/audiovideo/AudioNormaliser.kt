package com.delphi.delphitools.tools.audiovideo

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private const val PEAK_BUCKETS = 800
private val PICTURE_HEIGHT = 180.dp

// -1 dbfs as amplitude
private val CEILING = 10.0.pow(-1.0 / 20).toFloat()

private class LoudnessTarget(val lufs: Double, val hint: String)

private val targets = listOf(LoudnessTarget(-14.0, "Streaming"), LoudnessTarget(-16.0, "Podcast"), LoudnessTarget(-23.0, "Broadcast"))

/** dt-ios AudioNormaliserViewController: integrated loudness measured, then gain to a target with a -1 dBFS peak cap. */
class AudioNormaliserModel(application: Application) : AndroidViewModel(application) {
    val source = AVEditAudioSource(application, viewModelScope)
    val job = AVEditJob(viewModelScope, avEditRoot(application, "audio-normaliser"))
    var target by mutableIntStateOf(0)
        private set
    var format by mutableStateOf(AudioExtractFormat.Wav)
        private set

    val plan get() = source.scan?.let { AVEditAudioEngine.planGain(it.lufs, it.peakDb, targets[target].lufs) }

    fun load(uri: Uri) {
        clear()
        source.load(uri, PEAK_BUCKETS, loudness = true)
    }

    fun clear() {
        job.reset()
        source.clear()
    }

    fun selectTarget(index: Int) {
        target = index
        job.reset()
    }

    fun selectFormat(format: AudioExtractFormat) {
        this.format = format
        job.reset()
    }

    fun normalise() {
        val uri = source.uri ?: return
        val plan = plan ?: return
        val context = getApplication<Application>()
        val format = format
        job.start("${source.baseName}-normalised.${format.fileExtension}") { output, progress ->
            AVEditAudio.render(context, uri, output, format, gainDb = plan.gainDb, progress = progress)
        }
    }

    override fun onCleared() = clear()
}

@Composable
fun AudioNormaliser(model: AudioNormaliserModel = viewModel()) {
    val c = LocalDelphiColours.current
    val source = model.source
    AVEditPageEffects(null, model::clear)
    val scan = source.scan
    val plan = model.plan
    val loaded = source.uri != null
    val formats = AudioExtractFormat.entries

    val controls = CanonSection.of {
        CanonRows {
            if (!loaded) {
                AVEditAudioDrop(Icons.Outlined.Speed, model::load)
            } else {
                CanonHeading("Source")
                AVEditFileRow(source.name, scan?.let(AVEditAudioEngine::audioDetail) ?: "", if (source.failed) "Failed to decode audio." else null, model::clear)
                CanonRow("Target (LUFS)", detail = targets[model.target].hint, stacked = true) {
                    CanonSegmented(targets.map { String.format(Locale.ROOT, "%.0f", it.lufs) }, model.target, onSelect = model::selectTarget)
                }
                CanonHeading("Format")
                CanonSegmented(formats.map { it.label }, formats.indexOf(model.format)) { model.selectFormat(formats[it]) }
                AVEditJobPanel(model.job, "Normalise", Icons.Outlined.Speed, "Couldn't write the normalised file.", enabled = plan != null, onStart = model::normalise)
            }
        }
    }
    val readings = CanonSection.of {
        CanonRows {
            if (scan == null || plan == null) {
                CanonRow("Measuring…") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground)
                        Text("${(source.progress * 100).roundToInt()}%", style = CanonText.footnoteSemibold, color = c.mutedForeground)
                    }
                }
                return@CanonRows
            }
            fun level(db: Double?) = db?.takeIf { it.isFinite() }?.let(AVEditAudioEngine::levelShare)
            CanonHeading("Loudness")
            CanonTiles(
                listOf(
                    CanonTile("Loudness", AVEditAudioEngine.decibels(scan.lufs, "LUFS"), level(scan.lufs)),
                    CanonTile("Peak", AVEditAudioEngine.decibels(scan.peakDb, "dBFS"), level(scan.peakDb)),
                    CanonTile("Gain", AVEditAudioEngine.signedGain(plan.gainDb)),
                    CanonTile("Output peak", AVEditAudioEngine.decibels(plan.outPeakDb, "dBFS"), level(plan.outPeakDb)),
                )
            )
            if (plan.limited) CanonEmptyRow("Gain capped at -1 dBFS peak")
        }
    }
    val picture = CanonSection.canvas(CanonCanvasSize.Fixed(PICTURE_HEIGHT), background = c.muted) {
        NormaliserPicture(scan?.peaks, plan)
    }

    CanonSplit(
        sidebar = listOf(controls),
        main = if (loaded && !source.failed) listOf(readings) else emptyList(),
        banner = picture,
    )
}

// dt-ios AudioNormaliserPicture
@Composable
private fun NormaliserPicture(peaks: AVEditPeaks?, plan: AudioGainPlan?) {
    val c = LocalDelphiColours.current
    val top = LocalCanonCanvas.current.top
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    val gain = 10.0.pow((plan?.gainDb ?: 0.0) / 20).toFloat()
    val limited = plan?.limited == true
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val areaTop = top.toPx()
            val half = (size.height - areaTop) / 2
            val mid = areaTop + half
            val hairline = CanonMetrics.hairline.toPx()
            drawRect(c.border, Offset(0f, mid), Size(size.width, hairline))
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            val ceilingColour = (if (limited) c.destructive else c.mutedForeground).copy(alpha = 0.7f)
            for (side in listOf(-1f, 1f)) {
                val y = mid + side * half * CEILING
                drawLine(ceilingColour, Offset(0f, y), Offset(size.width, y), hairline, pathEffect = dash)
            }
            if (peaks == null || peaks.min.isEmpty() || size.width <= 0) return@Canvas
            val buckets = peaks.min.size
            val (columns, merge) = AVEditAudioEngine.merge(buckets, size.width)
            val width = max(1f, size.width / columns)
            val before = Path()
            val after = Path()
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
                val x = if (rightToLeft) size.width - left - width else left
                before.addRect(Rect(Offset(x, mid - high * half), Size(width, max(1f, (high - low) * half))))
                val boostedHigh = min(1f, high * gain)
                val boostedLow = max(-1f, low * gain)
                after.addRect(Rect(Offset(x, mid - boostedHigh * half), Size(width, max(1f, (boostedHigh - boostedLow) * half))))
            }
            val faded = c.mutedForeground.copy(alpha = 0.55f)
            if (gain >= 1) {
                drawPath(after, c.primary)
                drawPath(before, faded)
            } else {
                drawPath(before, faded)
                drawPath(after, c.primary)
            }
        }
        if (plan != null) {
            val db = plan.gainDb
            CanonFigure(
                String.format(Locale.ROOT, "%s%.1f", if (db >= 0) "+" else "−", abs(db)),
                Modifier.align(Alignment.BottomEnd).padding(end = CanonMetrics.panelPadding, bottom = 10.dp),
                unit = "dB",
                colour = if (limited) c.destructive else c.primary,
                halo = c.muted,
            )
        }
    }
}
