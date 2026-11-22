package com.delphi.delphitools.tools.typography

import com.delphi.delphitools.tools.TYPING_DELAY_MS
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonBigNumberField
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Milliseconds without a keystroke before the picture follows the typed value. */

internal val settleTween = tween<Float>(600, easing = Easing(PxToRemEngine::settle))

private val rowMono = TextStyle(fontFamily = DelphiMono, fontSize = 17.sp)
private val headerMono = TextStyle(fontFamily = DelphiMono, fontSize = 13.sp, fontWeight = FontWeight.Bold)

class PxToRemModel : ViewModel() {
    var input by mutableStateOf("16")
        private set

    /** The input as the picture and the table last took it. */
    var shown by mutableStateOf("16")
        private set
    var base by mutableIntStateOf(16)
        private set
    var remToPixel by mutableStateOf(false)
        private set
    private var typing: Job? = null

    val inputUnit get() = if (remToPixel) "rem" else "px"
    val outputUnit get() = if (remToPixel) "px" else "rem"
    val result get() = PxToRemEngine.convert(input, base.toDouble(), remToPixel)
    val shownResult get() = PxToRemEngine.convert(shown, base.toDouble(), remToPixel)
    val shownPixels get() = PxToRemEngine.parse(shown)?.let { if (remToPixel) it * base else it }

    fun type(text: String) {
        input = text
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            refresh()
        }
    }

    fun refresh() {
        typing?.cancel()
        shown = input
    }

    // px: 1, rem: 0.25
    fun nudge(direction: Int) {
        val step = if (remToPixel) 0.25 else 1.0
        set(PxToRemEngine.format(maxOf(0.0, (PxToRemEngine.parse(input) ?: 0.0) + direction * step)))
    }

    fun use(px: Double) = set(PxToRemEngine.format(if (remToPixel) px / base else px))

    fun direction(remToPixel: Boolean) {
        this.remToPixel = remToPixel
        refresh()
    }

    fun changeBase(base: Int) {
        this.base = base
        refresh()
    }

    private fun set(text: String) {
        input = text
        refresh()
    }
}

@Composable
fun PxToRem(model: PxToRemModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val pixels = model.shownPixels
    val result = model.shownResult

    val controls = CanonSection.of {
        CanonRows {
            CanonSegmented(listOf("PX → REM", "REM → PX"), if (model.remToPixel) 1 else 0) { model.direction(it == 1) }
            CanonBigNumberField(
                model.input,
                model::type,
                onStep = model::nudge,
                placeholder = "0",
                unit = model.inputUnit.uppercase(),
                label = "Value",
                onEndEditing = model::refresh,
            )
            CanonRow("Base", flush = true) {
                CanonNumberField(model.base, model::changeBase, Modifier.fillMaxSize(), range = 1..48, unit = "px")
            }
            CanonButton(
                "Copy",
                kind = CanonButtonKind.Secondary,
                enabled = result != null,
                icon = Icons.Outlined.ContentCopy,
                flashesCopied = true,
            ) {
                model.result?.let {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("", PxToRemEngine.format(it)))
                }
            }
        }
    }

    val table = CanonSection.of {
        CanonRows {
            CanonHeading("Quick Reference")
            ReferenceHeader(listOf("Pixels", "REM"), headerMono)
            PxToRemEngine.commonPxValues.forEach { px ->
                val rem = PxToRemEngine.format(px / model.base)
                ReferenceRow(
                    listOf("${px.toInt()}px" to c.foreground, rem to c.primary),
                    "${px.toInt()}px, ${rem}rem",
                    isCurrent = px == pixels,
                ) { model.use(px) }
            }
        }
    }

    val specimen = CanonSection.canvas(CanonCanvasSize.Fixed(200.dp), background = c.card) {
        PxToRemSpecimen(
            pixels = maxOf(0.0, pixels ?: 0.0).toFloat(),
            base = model.base.toFloat(),
            result = result?.let(PxToRemEngine::format) ?: "—",
            unit = if (result == null) "" else model.outputUnit,
        )
    }

    CanonSplit(sidebar = listOf(controls), main = listOf(table), banner = specimen)
}

/** The value set as type on a grid of one line per rem, with the converted result beside it. */
@Composable
private fun PxToRemSpecimen(pixels: Float, base: Float, result: String, unit: String) {
    val c = LocalDelphiColours.current
    val shownPixels by animateFloatAsState(pixels, settleTween, label = "pixels")
    val shownBase by animateFloatAsState(base, settleTween, label = "base")
    val measurer = rememberTextMeasurer()
    Box(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics {
                contentDescription = "Result"
                stateDescription = result + unit
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val padding = CanonMetrics.panelPadding.toPx()
            val baseline = size.height - padding - 8.dp.toPx()
            val shownBase = maxOf(1f, shownBase)
            // one line per rem
            val spacing = (shownBase * ceil(4f / shownBase)).dp.toPx()
            var y = baseline
            while (y > 0f) {
                val thickness = if (y == baseline) CanonMetrics.frame else CanonMetrics.hairline
                drawRect(c.border, Offset(0f, y), Size(size.width, thickness.toPx()))
                y -= spacing
            }
            val glyphs = maxOf(0f, shownPixels)
            if (glyphs <= 0f) return@Canvas
            // ponytail: glyphs over 600 pt are clamped; the band clips long before that
            val fontSize = minOf(glyphs, 600f).roundToInt().dp.toSp()
            val style = TextStyle(fontFamily = DelphiFont, fontSize = fontSize, fontWeight = FontWeight.Bold, color = c.foreground)
            val layout = measurer.measure("Ag", style, softWrap = false)
            drawText(layout, topLeft = Offset(padding, baseline - layout.firstBaseline))
        }
        CanonFigure(
            result,
            Modifier
                .align(Alignment.BottomEnd)
                .padding(CanonMetrics.panelPadding),
            unit = unit,
            halo = c.card,
        )
    }
}

/** A tappable row of a reference table: equal columns, the first leading, the last trailing, any between centred. */
@Composable
internal fun ReferenceRow(cells: List<Pair<String, Color>>, description: String, isCurrent: Boolean, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    Row(
        Modifier
            .fillMaxWidth()
            .canonClickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                selected = isCurrent
            }
            .background(if (isCurrent) c.accentBg else c.card)
            .heightIn(min = CanonMetrics.controlHeight)
            .padding(horizontal = CanonMetrics.panelPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { index, (text, colour) ->
            Text(text, Modifier.weight(1f), style = rowMono, color = colour, textAlign = cellAlign(index, cells.size))
        }
    }
}

@Composable
internal fun ReferenceHeader(titles: List<String>, style: TextStyle) {
    val c = LocalDelphiColours.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.card)
            .heightIn(min = CanonMetrics.controlHeight)
            .padding(horizontal = CanonMetrics.panelPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        titles.forEachIndexed { index, title ->
            Text(title, Modifier.weight(1f), style = style, color = c.mutedForeground, textAlign = cellAlign(index, titles.size))
        }
    }
}

private fun cellAlign(index: Int, count: Int) = when (index) {
    0 -> TextAlign.Start
    count - 1 -> TextAlign.End
    else -> TextAlign.Center
}
