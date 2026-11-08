package com.delphi.delphitools.tools.devtools

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.tools.devtools.UuidAnatomy.Field
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

private const val PITCH_CAP = 24f
private const val FONT_CAP = 38f
private const val SETTLE_FIRST_MS = 180
private const val SETTLE_STEP_MS = 14
private const val FRAME_MS = 33L
private val small = TextStyle(fontFamily = DelphiMono, fontSize = 10.sp)
private val legendStyle = CanonText.captionSemibold
private val bigBase = TextStyle(fontFamily = DelphiMono, fontWeight = FontWeight.SemiBold)

private fun fieldName(field: Field) = when (field) {
    Field.Time -> "Time"
    Field.Version -> "Version"
    Field.Variant -> "Variant"
    Field.Random -> "Random"
}

/**
 * One identifier set large, a column of its bits under every character (lit for 1), coloured by the field each bit
 * belongs to. A bracket under the v7 time reads the moment it encodes; a legend counts each field's bits.
 * A change of [scrambleKey] scrambles the characters and bits, settling column by column.
 */
@Composable
fun UuidAnatomyView(text: String, raw: String, kind: IdentifierKind, scrambleKey: Int, modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val columns = remember(text, kind) { UuidAnatomy.columns(text, kind) }
    val top = LocalCanonCanvas.current.top

    var settleFrom by remember { mutableStateOf<Long?>(null) }
    var clock by remember { mutableLongStateOf(0L) }
    val count by rememberUpdatedState(columns.size)
    LaunchedEffect(scrambleKey) {
        val animates = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        if (scrambleKey == 0 || !animates) return@LaunchedEffect
        val start = withFrameMillis { it }
        settleFrom = start
        clock = start
        val end = start + SETTLE_FIRST_MS + (count - 1) * SETTLE_STEP_MS
        while (clock <= end) {
            delay(FRAME_MS)
            clock = withFrameMillis { it }
        }
        settleFrom = null
    }

    fun colour(field: Field) = when (field) {
        Field.Time -> c.foreground
        Field.Version, Field.Variant -> c.primary
        Field.Random -> c.mutedForeground
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = kind.label
                stateDescription = text
            },
    ) {
        if (columns.isEmpty()) return@BoxWithConstraints
        val padding = CanonMetrics.panelPadding
        val room = maxWidth - padding * 2
        val pitch = minOf(PITCH_CAP, floor(maxOf(0f, room.value) / columns.size))
        val advance = remember(measurer) { measurer.measure("0", bigBase.copy(fontSize = 100.sp)).size.width / with(density) { 100.sp.toPx() } }
        val bigStyle = bigBase.copy(fontSize = with(density) { minOf(FONT_CAP, pitch * 0.94f / advance).dp.toSp() })
        val cell = maxOf(3f, (pitch * 0.72f).roundToInt().toFloat())
        val gap = maxOf(1f, pitch - cell)
        val rows = if (kind == IdentifierKind.NanoId) 6 else 4
        val textHeight = with(density) { measurer.measure("0", bigStyle).size.height.toDp() }
        val smallHeight = with(density) { measurer.measure("0", small).size.height.toDp() }
        val bitsHeight = (rows * cell + (rows - 1) * gap).dp
        val bracketMs = if (kind == IdentifierKind.Uuid7) UuidEngine.uuid7Milliseconds(raw) else null
        val lastTime = columns.indexOfLast { it?.fields?.first() == Field.Time }
        val hasBracket = bracketMs != null && lastTime >= 0

        val items = UuidAnatomy.bitCounts(kind).map { (field, bits) ->
            buildAnnotatedString {
                withStyle(SpanStyle(color = colour(field))) { append("■ ") }
                withStyle(SpanStyle(color = c.foreground)) { append(fieldName(field)) }
                withStyle(SpanStyle(color = c.mutedForeground)) { append(" $bits bits") }
            }
        }
        fun joined(part: List<AnnotatedString>) = buildAnnotatedString {
            part.forEachIndexed { index, item ->
                if (index > 0) append("    ")
                append(item)
            }
        }
        val one = joined(items)
        val oneWidth = with(density) { measurer.measure(one, legendStyle).size.width.toDp() }
        val legend = if (oneWidth > room && items.size > 1) {
            val half = (items.size + 1) / 2
            listOf(joined(items.take(half)), joined(items.drop(half)))
        } else {
            listOf(one)
        }
        val legendHeight = with(density) { measurer.measure("0", legendStyle).size.height.toDp() }

        var height: Dp = top + padding * 0.75f + textHeight + 6.dp + bitsHeight
        if (hasBracket) height += 10.dp + 6.dp + smallHeight
        height += 12.dp + legendHeight * legend.size + padding

        Canvas(Modifier.fillMaxWidth().height(height)) {
            val pitchPx = pitch.dp.toPx()
            val cellPx = cell.dp.toPx()
            val gapPx = gap.dp.toPx()
            val padPx = padding.toPx()
            val left = (size.width / 2 - pitchPx * columns.size / 2).roundToInt().toFloat()
            var y = top.toPx() + padPx * 0.75f
            val from = settleFrom
            fun scrambling(index: Int) = from != null && clock < from + SETTLE_FIRST_MS + index * SETTLE_STEP_MS
            val alphabet = if (kind == IdentifierKind.NanoId) UuidEngine.NANO_ID_ALPHABET else "0123456789abcdef"

            columns.forEachIndexed { index, column ->
                val x = left + index * pitchPx
                val (character, ink) = when {
                    column == null -> '-' to c.mutedForeground
                    scrambling(index) -> alphabet.random() to c.mutedForeground
                    Field.Version in column.fields || Field.Variant in column.fields -> column.character to c.primary
                    else -> column.character to c.foreground
                }
                val glyph = measurer.measure(character.toString(), bigStyle.copy(color = ink))
                drawText(glyph, topLeft = Offset(x + (pitchPx - glyph.size.width) / 2, y))
            }
            y += textHeight.toPx() + 6.dp.toPx()

            val inset = ((pitchPx - cellPx) / 2).roundToInt().toFloat()
            columns.forEachIndexed { index, column ->
                if (column == null) return@forEachIndexed
                val x = left + index * pitchPx + inset
                val scrambles = scrambling(index)
                column.bits.zip(column.fields).forEachIndexed { row, (bit, field) ->
                    val on = if (scrambles) Random.nextBoolean() else bit
                    val base = colour(field)
                    drawRect(if (on) base else base.copy(alpha = 0.14f), Offset(x, y + row * (cellPx + gapPx)), Size(cellPx, cellPx))
                }
            }
            y += bitsHeight.toPx()

            if (hasBracket) {
                y += 10.dp.toPx()
                val hair = CanonMetrics.hairline.toPx()
                val start = left + inset
                val end = left + lastTime * pitchPx + pitchPx - inset
                val ink: Color = c.foreground
                drawRect(ink, Offset(start, y + 3.dp.toPx()), Size(end - start, hair))
                drawRect(ink, Offset(start, y), Size(hair, 6.dp.toPx()))
                drawRect(ink, Offset(end - hair, y), Size(hair, 6.dp.toPx()))
                val stamp = measurer.measure(UuidAnatomy.stamp(bracketMs!!), small.copy(color = ink))
                y += 6.dp.toPx()
                // starts under bracket
                drawText(stamp, topLeft = Offset(minOf(start, size.width - padPx - stamp.size.width), y))
                y += smallHeight.toPx()
            }

            y += 12.dp.toPx()
            for (line in legend) {
                val laid = measurer.measure(line, legendStyle)
                drawText(laid, topLeft = Offset(maxOf(padPx, size.width / 2 - laid.size.width / 2).roundToInt().toFloat(), y))
                y += legendHeight.toPx()
            }
        }
    }
}
