package com.delphi.delphitools.tools.audiovideo

import com.delphi.delphitools.tools.TYPING_DELAY_MS
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMenuCell
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTimecode
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.tools.audiovideo.Timecode.ParseError
import com.delphi.delphitools.tools.audiovideo.Timecode.ParseResult
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

private const val DEFAULT_RATE = 4
private val pictureSize = 30.sp
private const val DASH = "—"

@Composable
fun TimecodeCalc() {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    var rateIndex by rememberSaveable { mutableStateOf(DEFAULT_RATE) }
    var subtracts by rememberSaveable { mutableStateOf(false) }
    var digitsA by rememberSaveable { mutableStateOf("") }
    var digitsB by rememberSaveable { mutableStateOf("") }
    var shown by remember { mutableStateOf(digitsA to digitsB) }
    LaunchedEffect(digitsA, digitsB) {
        if (shown == digitsA to digitsB) return@LaunchedEffect
        delay(TYPING_DELAY_MS)
        shown = digitsA to digitsB
    }
    val rate = Timecode.rates[rateIndex]
    val parsedA = Timecode.parse(Timecode.fieldFormat(shown.first, rate.drop), rate)
    val parsedB = Timecode.parse(Timecode.fieldFormat(shown.second, rate.drop), rate)
    val a = (parsedA as? ParseResult.Ok)?.frames
    val b = (parsedB as? ParseResult.Ok)?.frames
    // B alone gives nothing, A alone gives A
    val frames = a?.let { if (b == null) it else if (subtracts) it - b else it + b }
    val result = frames?.let { Timecode.format(Timecode.parts(it, rate), rate) }
    fun line(parsed: ParseResult) = (parsed as? ParseResult.Ok)?.let { Timecode.format(it.parts, rate) } ?: Timecode.blank(rate)

    val controls = CanonSection.of {
        CanonRows {
            CanonHeading("Frame rate")
            CanonRow("Frame rate", flush = true) {
                CanonMenuCell(Timecode.rates.map { it.label }, rateIndex, {
                    rateIndex = it
                    shown = digitsA to digitsB
                })
            }
            CanonHeading("Timecode A")
            TimecodeField(digitsA, rate.drop, "Timecode A") { digitsA = it }
            Note(parsedA, rate)
            CanonHeading("Operation")
            CanonSegmented(listOf("+", "−"), if (subtracts) 1 else 0) {
                subtracts = it == 1
                shown = digitsA to digitsB
            }
            CanonHeading("Timecode B")
            TimecodeField(digitsB, rate.drop, "Timecode B") { digitsB = it }
            Note(parsedB, rate)
        }
    }

    val readings = CanonSection.of {
        CanonRows {
            CanonHeading("Result")
            CanonTiles(
                listOf(
                    CanonTile("Frames", frames?.let { NumberFormat.getIntegerInstance().format(it) } ?: DASH),
                    CanonTile("Real time", frames?.let { Timecode.clock(Timecode.seconds(it, rate)) } ?: DASH),
                )
            )
            CanonButton(
                "Copy",
                kind = CanonButtonKind.Primary,
                enabled = frames != null,
                icon = Icons.Rounded.ContentCopy,
                oversized = true,
                flashesCopied = true,
            ) {
                result ?: return@CanonButton
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", result))
            }
        }
    }

    // three lines, the rule and the gaps, plus 8 dp below
    val height = with(LocalDensity.current) { (pictureSize * 1.2f).toDp() } * 3 + 6.dp * 2 + 10.dp + CanonMetrics.frame + 8.dp
    val picture = CanonSection.canvas(CanonCanvasSize.Fixed(height), background = c.card) {
        TimecodePicture(line(parsedA), line(parsedB), subtracts, result ?: Timecode.blank(rate), Modifier.align(Alignment.TopEnd))
    }

    CanonSplit(sidebar = listOf(controls), main = listOf(readings), banner = picture)
}

/** The sum as it is written out: A, then the sign and B, a rule, and the result under it. */
@Composable
private fun TimecodePicture(a: String, b: String, subtracts: Boolean, result: String, modifier: Modifier) {
    val c = LocalDelphiColours.current
    Column(
        modifier
            .padding(top = LocalCanonCanvas.current.top, end = CanonMetrics.panelPadding)
            .width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CanonTimecode(a, Modifier.alpha(0.7f).semantics { contentDescription = "Timecode A" }, pictureSize, c.card)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (subtracts) "−" else "+",
                style = TextStyle(fontFamily = DelphiFont, fontSize = 20.sp, fontWeight = FontWeight.Bold),
                color = c.mutedForeground,
                modifier = Modifier.alignByBaseline(),
            )
            CanonTimecode(b, Modifier.alpha(0.7f).alignByBaseline().semantics { contentDescription = "Timecode B" }, pictureSize, c.card)
        }
        Box(Modifier.fillMaxWidth().height(CanonMetrics.frame).background(c.primary))
        CanonTimecode(result, Modifier.padding(top = 4.dp).semantics { contentDescription = "Result" }, pictureSize, c.card)
    }
}

@Composable
private fun Note(parsed: ParseResult, rate: Timecode.Rate) {
    val (text, tone) = when (parsed) {
        is ParseResult.Failed -> when (val error = parsed.error) {
            ParseError.Empty -> null
            ParseError.NotNumeric -> "Numbers only"
            ParseError.TooMany -> "Too many fields"
            is ParseError.FrameRange -> "Frame too high" + String.format(Locale.ROOT, " · 00–%02d", error.max)
            ParseError.SecondsRange -> "Seconds too high"
            ParseError.MinutesRange -> "Minutes too high"
        } to CanonTone.Warn
        is ParseResult.Ok -> parsed.snappedFrom?.let { "Dropped frame · $it → ${Timecode.format(parsed.parts, rate)}" } to CanonTone.Muted
    }
    if (text != null) CanonStatusRow(text, tone)
}

/** Timecode typed as on a cash register: digits come in from the right, the separators are put in. */
@Composable
private fun TimecodeField(digits: String, drop: Boolean, label: String, onChange: (String) -> Unit) {
    val c = LocalDelphiColours.current
    val focusManager = LocalFocusManager.current
    val text = Timecode.fieldFormat(digits, drop)
    val style = TextStyle(fontFamily = DelphiMono, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    BasicTextField(
        value = TextFieldValue(text, TextRange(text.length)),
        onValueChange = { onChange(Timecode.fieldDigits(it.text)) },
        singleLine = true,
        textStyle = style.copy(color = c.foreground),
        cursorBrush = SolidColor(c.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CanonMetrics.controlHeight)
            .background(c.card)
            .semantics { contentDescription = label },
        decorationBox = { field ->
            Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.CenterEnd) {
                if (text.isEmpty()) {
                    Text(if (drop) "00:00:00;00" else "00:00:00:00", style = style, color = c.mutedForeground, modifier = Modifier.fillMaxWidth())
                }
                field()
            }
        },
    )
}
