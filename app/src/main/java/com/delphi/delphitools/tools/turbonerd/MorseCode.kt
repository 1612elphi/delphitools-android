package com.delphi.delphitools.tools.turbonerd

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import com.delphi.delphitools.ui.theme.CanonText
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonGlyphFlow
import com.delphi.delphitools.canon.CanonGlyphKind
import com.delphi.delphitools.canon.CanonGlyphMetrics
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonReadout
import com.delphi.delphitools.canon.CanonReadoutBand
import com.delphi.delphitools.canon.CanonReadoutRowHeight
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTextInput
import com.delphi.delphitools.canon.CanonTextOutput
import com.delphi.delphitools.canon.CanonTransport
import com.delphi.delphitools.canon.rememberCanonGlyphFlowState
import com.delphi.delphitools.ui.theme.DelphiColours
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours

class MorseCodeModel : ViewModel() {
    var direction by mutableIntStateOf(0)
        private set
    var wpm by mutableIntStateOf(20)
        private set
    var looping by mutableStateOf(false)
        private set
    var text by mutableStateOf("")
    var settled by mutableStateOf("")
        private set
    val player = MorseTonePlayer(viewModelScope)

    val decoding get() = direction == 2 || (direction == 0 && MorseEngine.looksLikeMorse(settled))

    // empty auto input keeps the keypad
    val keying get() = decoding || (direction == 0 && settled.isEmpty())
    val morse get() = if (decoding) settled else MorseEngine.encode(settled)

    fun select(direction: Int) {
        this.direction = direction
        player.stop()
    }

    fun changeSpeed(wpm: Int) {
        if (wpm == this.wpm) return
        this.wpm = wpm
        player.stop()
    }

    fun settle(text: String) {
        settled = text
        player.stop()
    }

    fun press(key: MorseEngine.Key) {
        val next = MorseEngine.press(text, key)
        if (next == text) return
        text = next
        settle(next)
        if (key == MorseEngine.Key.Dit) player.key('.', wpm)
        if (key == MorseEngine.Key.Dah) player.key('-', wpm)
    }

    fun toggle() = when {
        player.isPaused -> player.resume()
        player.isPlaying -> player.pause()
        else -> player.play(morse, wpm, looping)
    }

    fun toggleLoop() {
        looping = !looping
        player.looping = looping
    }

    override fun onCleared() {
        player.stop()
        player.releaseKeys()
    }
}

private val STRIP_HEIGHT = 118.dp
private val COLLAPSED_STRIP = 70.dp

@Composable
fun MorseCode(model: MorseCodeModel = viewModel()) {
    val player = model.player
    val activity = LocalContext.current as? Activity
    DisposableEffect(model) {
        onDispose { if (activity?.isChangingConfigurations != true) player.stop() }
    }
    val decoding = model.decoding
    val morse = model.morse
    val converted = if (decoding) MorseEngine.decode(model.settled) else MorseEngine.encode(model.settled)
    val tape = remember(morse, model.wpm) { MorseTapeLayout(morse, model.wpm) }

    var transport by remember { mutableStateOf(CanonTransport()) }
    val running = player.isPlaying && !player.isPaused
    LaunchedEffect(player.isPlaying, player.isPaused, model.looping) {
        while (true) {
            val position = player.position
            transport = CanonTransport(
                playhead = position?.first,
                playing = running,
                paused = player.isPaused,
                looping = model.looping,
                loops = position?.second ?: 0,
            )
            if (!running) break
            withFrameNanos {}
        }
    }

    val controls = CanonSection.of {
        CanonRows {
            CanonHeading("Direction")
            CanonSegmented(listOf("Auto", "Encode", "Decode"), model.direction, onSelect = model::select)
            CanonHeading("Input")
            CanonTextInput(model.text, { model.text = it }, model::settle)
        }
    }
    val settings = CanonSection.of {
        CanonRows {
            CanonHeading("Speed")
            CanonSlider(
                model.wpm.toFloat(),
                { model.changeSpeed(it.toInt()) },
                Modifier.fillMaxWidth(),
                range = 5f..40f,
                step = 1f,
                readout = "${model.wpm} WPM",
            )
        }
    }
    val output = CanonSection.of {
        CanonTextOutput(
            converted,
            heading = if (decoding) "Text" else "Morse",
            fileName = "morse.txt",
            style = TextStyle(fontFamily = DelphiMono, fontSize = 17.sp),
        )
    }
    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(STRIP_HEIGHT + CanonReadoutRowHeight)) {
        CanonReadoutBand(
            transport,
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Morse"
                    stateDescription = if (decoding) converted else model.settled
                },
            readout = CanonReadout.Below,
            idle = tape.duration,
        ) { playhead -> MorseTape(tape, playhead, Modifier.fillMaxSize()) }
    }

    CanonSplit(
        sidebar = listOf(controls, settings),
        main = listOf(output),
        banner = banner,
        collapsesTo = COLLAPSED_STRIP + CanonReadoutRowHeight,
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
                if (model.keying) MorseKeypad(model::press, canDelete = model.text.isNotEmpty())
                CanonGrid {
                    CanonButton("Loop", Modifier.weight(1f).fillMaxHeight(), selected = model.looping, icon = Icons.Filled.Repeat, onClick = model::toggleLoop)
                    CanonButton(
                        if (running) "Pause" else "Play",
                        Modifier.weight(1f).fillMaxHeight(),
                        kind = CanonButtonKind.Primary,
                        enabled = MorseEngine.looksLikeMorse(morse),
                        icon = if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        onClick = model::toggle,
                    )
                    CanonButton("Stop", Modifier.weight(1f).fillMaxHeight(), enabled = player.isPlaying, icon = Icons.Filled.Stop, onClick = player::stop)
                }
            }
        },
    )
}

private val keyHeight = 72.dp

@Composable
private fun MorseKeypad(onPress: (MorseEngine.Key) -> Unit, canDelete: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
        CanonGrid(Modifier.heightIn(min = keyHeight)) {
            val cell = Modifier.weight(1f).fillMaxHeight()
            CanonButton("Dit", cell, icon = MorseKeyIcons.dit, textStyle = CanonText.title2) { onPress(MorseEngine.Key.Dit) }
            CanonButton("Dah", cell, icon = Icons.Filled.Remove, textStyle = CanonText.title2) { onPress(MorseEngine.Key.Dah) }
        }
        CanonGrid {
            val cell = Modifier.weight(1f).fillMaxHeight()
            CanonButton("Letter gap", cell, icon = Icons.Filled.SpaceBar) { onPress(MorseEngine.Key.Letter) }
            CanonButton("Word gap", cell, icon = MorseKeyIcons.slash) { onPress(MorseEngine.Key.Word) }
            CanonButton(
                "Backspace",
                cell,
                enabled = canDelete,
                icon = Icons.AutoMirrored.Filled.Backspace,
                repeats = true,
            ) { onPress(MorseEngine.Key.Back) }
        }
    }
}

// lucide dot and slash, which material lacks
private object MorseKeyIcons {
    val dit = ImageVector.Builder("Dit", 24.dp, 24.dp, 24f, 24f)
        .path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 10f)
            arcToRelative(2f, 2f, 0f, true, true, 0f, 4f)
            arcToRelative(2f, 2f, 0f, true, true, 0f, -4f)
            close()
        }
        .build()
    val slash = ImageVector.Builder("Slash", 24.dp, 24.dp, 24f, 24f)
        .path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(22f, 2f)
            lineTo(2f, 22f)
        }
        .build()
}

/** empty code: word space */
private data class MorseGlyph(val code: String, val label: String)

/** dot 1, dash 3 */
private fun units(code: String) = if (code.isEmpty()) 1 else code.sumOf { if (it == '-') 3L else 1L }.toInt() + code.length - 1

private class MorseTapeLayout(morse: String, wpm: Int) {
    val unitSeconds = 1.2 / wpm
    val glyphs: List<MorseGlyph>

    /** unit offsets per glyph */
    val starts: List<Int>
    val totalUnits: Int

    /** seconds at the chosen speed */
    val duration: Double?

    init {
        glyphs = buildList {
            MorseEngine.words(morse).forEachIndexed { index, word ->
                if (index > 0) add(MorseGlyph("", " "))
                word.forEach { add(MorseGlyph(it, MorseEngine.reverse[it]?.toString() ?: "?")) }
            }
        }
        var offset = 0
        starts = glyphs.mapIndexed { index, glyph ->
            // word gap: 3+1+3
            if (index > 0) offset += 3
            offset.also { offset += units(glyph.code) }
        }
        totalUnits = offset
        duration = if (glyphs.isEmpty()) null else offset * unitSeconds
    }

    fun glyph(atUnit: Double) = starts.indexOfLast { it <= atUnit }.takeIf { it >= 0 }
}

private const val MARK = 7f
private val labelStyle = TextStyle(fontFamily = DelphiFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

@Composable
private fun MorseTape(tape: MorseTapeLayout, playhead: Double?, modifier: Modifier) {
    val c = LocalDelphiColours.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val width = maxWidth - CanonMetrics.panelPadding * 2
        // exact fit wraps (rounding)
        val unit = if (tape.totalUnits > 0 && width > 0.dp) ((width.value - 1) / tape.totalUnits).coerceIn(3f, 12f) else 8f
        val unitHolder = remember { floatArrayOf(unit) }
        unitHolder[0] = unit
        val flow = rememberCanonGlyphFlowState<MorseGlyph>(
            CanonGlyphMetrics(height = (MARK + 22).dp, rowGap = 12.dp, gap = (3 * unit).dp),
            measure = { (units(it.code) * unitHolder[0]).dp },
            kind = { if (it.code.isEmpty()) CanonGlyphKind.Space else CanonGlyphKind.Glyph },
        )
        LaunchedEffect(tape) { flow.show(tape.glyphs) }
        SideEffect { flow.focus = playhead?.let { tape.glyph(it / tape.unitSeconds) } }
        CanonGlyphFlow(
            flow,
            Modifier.fillMaxSize(),
            paintEmpty = { area ->
                drawRect(c.mutedForeground.copy(alpha = 0.3f), Offset(area.left, area.center.y - 0.5.dp.toPx()), Size(area.width, 1.dp.toPx()))
            },
        ) { glyph, index, frame ->
            drawMorse(glyph, index, frame, tape, playhead, (3 * unit).dp, c, measurer)
        }
    }
}

private fun DrawScope.drawMorse(
    glyph: MorseGlyph,
    index: Int?,
    frame: Rect,
    tape: MorseTapeLayout,
    playhead: Double?,
    gap: Dp,
    c: DelphiColours,
    measurer: TextMeasurer,
) {
    val mark = MARK.dp.toPx()
    val lineY = frame.top + mark / 2
    // tape spans the gaps
    val reach = gap.toPx() / 2
    drawRect(c.mutedForeground.copy(alpha = 0.25f), Offset(frame.left - reach, lineY - 0.5.dp.toPx()), Size(frame.width + reach * 2, 1.dp.toPx()))
    if (glyph.code.isEmpty()) return

    val scale = frame.width / units(glyph.code)
    val played = if (index != null && playhead != null) playhead / tape.unitSeconds - tape.starts[index] else null
    var x = frame.left
    var at = 0.0
    for (symbol in glyph.code) {
        val length = if (symbol == '-') 3.0 else 1.0
        val rect = Rect(x, frame.top, x + scale * length.toFloat(), frame.top + mark)
        val lit = played != null && played >= at
        val sounding = played != null && played >= at && played < at + length
        val ink = when {
            lit -> c.primary
            playhead == null -> c.foreground
            else -> c.mutedForeground.copy(alpha = 0.5f)
        }
        if (sounding) {
            val glow = android.graphics.Paint().apply {
                color = ink.toArgb()
                setShadowLayer(4.dp.toPx(), 0f, 0f, c.primary.toArgb())
            }
            drawIntoCanvas { it.nativeCanvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, glow) }
        } else {
            drawRect(ink, rect.topLeft, rect.size)
        }
        x += scale * (length + 1).toFloat()
        at += length + 1
    }

    val colour = when {
        MorseEngine.reverse[glyph.code] == null -> c.destructive
        (played ?: -1.0) >= 0 -> c.primary
        else -> c.mutedForeground
    }
    val laid = measurer.measure(glyph.label, labelStyle.copy(color = colour))
    drawText(laid, topLeft = Offset(frame.center.x - laid.size.width / 2f, frame.top + mark + 5.dp.toPx()))
}
