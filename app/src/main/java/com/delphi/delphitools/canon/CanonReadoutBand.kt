package com.delphi.delphitools.canon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.R
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val FluoWrite = FontFamily(Font(R.font.fluo_write))

// FluoWrite's full stop is 8.2 units wide against the colon's 21.8 and the digits have no side bearing;
// padding both sides seats it in a slot a little wider than a colon's
private val stopSpacing = (2 * ((21.8 - 8.2) / 2 + 4) / 100).em

/** MM:SS.hh, minutes capped at 99. */
fun canonHundredths(seconds: Double): String {
    val hundredths = (max(0.0, seconds) * 100).roundToInt()
    return String.format(Locale.ROOT, "%02d:%02d.%02d", min(99, hundredths / 6000), hundredths / 100 % 60, hundredths % 100)
}

/** HH:MM:SS:FF at a whole frame rate, for [CanonTimecode]'s `format`. */
// ponytail: non-drop-frame only; drop-frame rates need the 29.97/59.94 frame skipping
fun canonSmpte(framesPerSecond: Int): (Double) -> String = { seconds ->
    // 3723.16 s at 25 fps multiplies to 93078.99999
    val frames = (max(0.0, seconds) * framesPerSecond + 1e-6).toLong()
    val whole = frames / framesPerSecond
    String.format(Locale.ROOT, "%02d:%02d:%02d:%02d", whole / 3600 % 100, whole / 60 % 60, whole % 60, frames % framesPerSecond)
}

/**
 * Counter in the FluoWrite segment font. Leading zeros that carry no value are dimmed;
 * `null` shows hyphens in the format's shape. `format` replaces MM:SS.hh, for SMPTE timecode.
 */
@Composable
fun CanonTimecode(
    seconds: Double?,
    modifier: Modifier = Modifier,
    size: TextUnit = 44.sp,
    halo: Color = LocalDelphiColours.current.muted,
    format: ((Double) -> String)? = null,
) {
    TimecodeText((format ?: ::canonHundredths)(seconds ?: 0.0), seconds == null, modifier, size, halo)
}

/** An already formatted timecode; one without digits, or `null`, shows hyphens in that shape. */
@Composable
fun CanonTimecode(
    text: String?,
    modifier: Modifier = Modifier,
    size: TextUnit = 44.sp,
    halo: Color = LocalDelphiColours.current.muted,
) {
    TimecodeText(text ?: canonHundredths(0.0), text?.any { it.isDigit() } != true, modifier, size, halo)
}

@Composable
private fun TimecodeText(shape: String, blank: Boolean, modifier: Modifier, size: TextUnit, halo: Color) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    // the font has no semicolon
    var text = shape.replace(';', '.')
    val dimmed = if (blank) {
        text = text.map { if (it.isDigit()) '-' else it }.joinToString("")
        text.length
    } else {
        max(0, min(text.length - 4, text.takeWhile { it == '0' || it == ':' || it == '.' }.length))
    }
    val styled = buildAnnotatedString {
        text.forEachIndexed { index, character ->
            val colour = if (index < dimmed) c.primary.copy(alpha = 0.28f) else Color.Unspecified
            val spacing = if (character == '.') stopSpacing else TextUnit.Unspecified
            withStyle(SpanStyle(color = colour, letterSpacing = spacing)) { append(character) }
        }
    }
    val style = TextStyle(fontFamily = FluoWrite, fontSize = size, lineHeight = size)
    val (stroke, lift) = with(density) { size.toPx() * 0.12f to 3.dp.toPx() }
    Box(
        modifier.semantics { if (!blank) stateDescription = text },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            styled,
            style = style.copy(drawStyle = Stroke(stroke), shadow = Shadow(halo, Offset(0f, lift), with(density) { 5.dp.toPx() })),
            color = halo,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            styled,
            style = style.copy(shadow = Shadow(c.primary.copy(alpha = 0.55f), Offset.Zero, with(density) { 7.dp.toPx() })),
            color = c.primary,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

enum class CanonLamp(internal val icon: ImageVector) {
    Play(Icons.Filled.PlayArrow),
    Pause(Icons.Filled.Pause),
    Loop(Icons.Filled.Repeat),

    /** Lit in red. */
    Record(Icons.Filled.RadioButtonChecked);

    companion object {
        val transport = listOf(Play, Pause, Loop)
    }
}

/**
 * Transport lamps: every lamp in `lamps` is always present, lit while it is in `lit`.
 * A change of a lamp's count in `flashes` turns it off for 0.12 s.
 */
@Composable
fun CanonLamps(
    lit: Set<CanonLamp>,
    modifier: Modifier = Modifier,
    lamps: List<CanonLamp> = CanonLamp.transport,
    flashes: Map<CanonLamp, Int> = emptyMap(),
) {
    val c = LocalDelphiColours.current
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        for (lamp in lamps) {
            val count = flashes[lamp] ?: 0
            var seen by remember { mutableIntStateOf(count) }
            LaunchedEffect(count) {
                delay(120)
                seen = count
            }
            val on = lamp in lit && count == seen
            val colour = if (lamp == CanonLamp.Record) c.destructive else c.primary
            Box(Modifier.size(20.dp)) {
                if (on) {
                    Icon(
                        lamp.icon,
                        contentDescription = null,
                        tint = colour.copy(alpha = 0.6f),
                        // no blur below API 31
                        modifier = Modifier.fillMaxSize().blur(3.dp, BlurredEdgeTreatment.Unbounded),
                    )
                }
                Icon(
                    lamp.icon,
                    contentDescription = null,
                    tint = if (on) colour else c.mutedForeground.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** What a readout band shows of a player. `loops` counts wraps; a change blinks the loop lamp. */
@Immutable
data class CanonTransport(
    val playhead: Double? = null,
    val playing: Boolean = false,
    val paused: Boolean = false,
    val recording: Boolean = false,
    val looping: Boolean = false,
    val loops: Int = 0,
)

enum class CanonReadout {
    /** Counter and lamps over the strip's bottom corners: a waveform reads through them. */
    Over,

    /** Counter and lamps in a row of their own under the strip: a filmstrip's frames stay clear. */
    Below,
}

val CanonReadoutRowHeight = 44.dp

/** The picture of the whole file inside a readout band, a waveform or a filmstrip, given the playhead. */
typealias CanonReadoutStrip = @Composable (playhead: Double?) -> Unit

/**
 * A strip of the whole file under a counter and transport lamps: the banner of a media tool.
 * `idle` is what the counter shows without a playhead; `null` shows dashes.
 */
// ponytail: the strip stops at the band's top; no runsUnderStatusBar, the caller pads for insets
@Composable
fun CanonReadoutBand(
    transport: CanonTransport,
    modifier: Modifier = Modifier,
    readout: CanonReadout = CanonReadout.Over,
    lamps: List<CanonLamp> = CanonLamp.transport,
    format: ((Double) -> String)? = null,
    idle: Double? = null,
    strip: CanonReadoutStrip,
) {
    val c = LocalDelphiColours.current
    val lit = buildSet {
        if (transport.playing) add(CanonLamp.Play)
        if (transport.paused) add(CanonLamp.Pause)
        if (transport.looping) add(CanonLamp.Loop)
        if (transport.recording) add(CanonLamp.Record)
    }
    val flashes = mapOf(CanonLamp.Loop to transport.loops)
    val counter = Modifier.semantics { contentDescription = if (transport.playhead != null) "Position" else "Length" }
    val seconds = transport.playhead ?: idle
    Box(modifier.background(c.muted)) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = if (readout == CanonReadout.Below) CanonReadoutRowHeight else 0.dp)
                .clipToBounds()
        ) { strip(transport.playhead) }
        if (readout == CanonReadout.Over) {
            CanonLamps(
                lit,
                Modifier.align(Alignment.BottomStart).padding(start = CanonMetrics.panelPadding, bottom = CanonMetrics.panelPadding),
                lamps,
                flashes,
            )
            CanonTimecode(
                seconds,
                counter.align(Alignment.BottomEnd).padding(end = CanonMetrics.panelPadding, bottom = 10.dp),
                format = format,
            )
        } else {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(CanonReadoutRowHeight)
                    .padding(horizontal = CanonMetrics.panelPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CanonLamps(lit, lamps = lamps, flashes = flashes)
                CanonTimecode(seconds, counter, size = 30.sp, format = format)
            }
        }
    }
}
