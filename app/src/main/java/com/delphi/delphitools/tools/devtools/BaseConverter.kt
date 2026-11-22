package com.delphi.delphitools.tools.devtools

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonBigNumberField
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
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.CanonValueLayout
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.tools.devtools.BitRegisterLayout.BIT_GAP
import com.delphi.delphitools.tools.devtools.BitRegisterLayout.HEADER
import com.delphi.delphitools.tools.devtools.BitRegisterLayout.HEX_ROW
import com.delphi.delphitools.tools.devtools.BitRegisterLayout.INDEX_ROW
import com.delphi.delphitools.tools.devtools.BitRegisterLayout.LINE_GAP
import com.delphi.delphitools.tools.devtools.BitRegisterLayout.NIBBLE_GAP
import com.delphi.delphitools.tools.devtools.BitRegisterLayout.OPERAND_GAP
import com.delphi.delphitools.tools.devtools.BitRegisterLayout.RULE_ROOM
import com.delphi.delphitools.tools.devtools.BitRegisterLayout.SIGN_WIDTH
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow

private const val TYPING_DELAY_MS = 400L
private const val PAD = 16f
private const val LIMIT = 460f
private const val SHORTEST = 200f
private const val CHANGE_MS = 220f
private const val RIPPLE_MS = 6f
private val operandMono = TextStyle(fontFamily = DelphiMono, fontSize = 17.sp)

/** Mixed in sRGB, as UIKit mixes; Compose's lerp goes through Oklab. */
private fun mix(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
    a.alpha + (b.alpha - a.alpha) * t,
)

enum class RegisterRole { Only, Operand, Result }

data class RegisterOperand(val value: ULong, val sign: String?, val editable: Boolean, val role: RegisterRole)

class BaseConverterModel : ViewModel() {
    var radix by mutableStateOf(Radix.Decimal)
        private set
    var word by mutableIntStateOf(16)
        private set
    var entry by mutableStateOf("")
        private set
    var operand by mutableStateOf("")
        private set
    var reading by mutableStateOf<BaseReading>(BaseReading.Empty)
        private set
    var operandReading by mutableStateOf<BaseReading>(BaseReading.Empty)
        private set
    var bitwise by mutableStateOf(false)
        private set
    var operation by mutableStateOf(BitOperation.And)
        private set
    var shift by mutableIntStateOf(1)
        private set
    private var typing: Job? = null

    val takesOperand get() = bitwise && operation.takesOperand
    val needed get() = BaseNumber.word(maxOf(reading.value ?: 0uL, (if (takesOperand) operandReading.value else null) ?: 0uL))
    val result get() = if (bitwise) reading.value?.let { BaseNumber.apply(operation, it, if (takesOperand) operandReading.value else null, shift, word) } else null

    fun typeEntry(text: String) {
        entry = text
        debounce()
    }

    fun typeOperand(text: String) {
        operand = text
        debounce()
    }

    private fun debounce() {
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            read()
        }
    }

    fun read() {
        typing?.cancel()
        reading = BaseNumber.read(entry, radix)
        operandReading = BaseNumber.read(operand, radix)
        fit()
    }

    /** The word grows to hold A and B; it never shrinks on its own. */
    private fun fit() {
        if (word < needed) word = needed
        shift = shift.coerceAtMost(word - 1)
    }

    fun pick(radix: Radix) {
        this.radix = radix
        entry = reading.value?.let { BaseNumber.format(it, radix) } ?: ""
        operand = operandReading.value?.let { BaseNumber.format(it, radix) } ?: ""
        read()
    }

    fun pickWord(word: Int) {
        this.word = word
        fit()
    }

    fun turnBitwise(on: Boolean) {
        bitwise = on
        fit()
    }

    fun pick(operation: BitOperation) {
        this.operation = operation
        fit()
    }

    fun shiftBy(amount: Int) {
        shift = amount.coerceIn(0, word - 1)
    }

    fun step(direction: Int) {
        val value = reading.value ?: 0uL
        val next = if (direction > 0) (if (value == ULong.MAX_VALUE) value else value + 1uL) else (if (value == 0uL) 0uL else value - 1uL)
        typing?.cancel()
        reading = BaseReading.Value(next)
        entry = BaseNumber.format(next, radix)
        fit()
    }

    fun toggle(operandIndex: Int, bit: Int) {
        val flipped = 1uL shl bit
        typing?.cancel()
        if (operandIndex == 0) {
            val next = (reading.value ?: 0uL) xor flipped
            reading = BaseReading.Value(next)
            entry = BaseNumber.format(next, radix)
        } else {
            val next = (operandReading.value ?: 0uL) xor flipped
            operandReading = BaseReading.Value(next)
            operand = BaseNumber.format(next, radix)
        }
        fit()
    }

    fun errorText(reading: BaseReading, text: String): String? = when (reading) {
        BaseReading.Empty, is BaseReading.Value -> null
        is BaseReading.Invalid -> BaseNumber.base(text, radix).let { "${reading.character} is not a digit in ${it.label}, which takes 0 to ${it.highestDigit}" }
        BaseReading.TooLarge -> "Larger than 64 bits"
    }

    val operands: List<RegisterOperand>
        get() {
            val a = reading.value ?: 0uL
            if (!bitwise) return listOf(RegisterOperand(a, null, true, RegisterRole.Only))
            val list = mutableListOf(RegisterOperand(a, null, true, RegisterRole.Operand))
            if (takesOperand) list += RegisterOperand(operandReading.value ?: 0uL, operation.title, true, RegisterRole.Operand)
            val sign = when {
                operation.takesOperand -> "="
                operation == BitOperation.Not -> operation.title
                else -> "${operation.title} $shift"
            }
            list += RegisterOperand(result ?: 0uL, sign, false, RegisterRole.Result)
            return list
        }
}

@Composable
fun BaseConverter(model: BaseConverterModel = viewModel()) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val radix = model.radix
    val a = model.reading.value
    val keyboard = KeyboardOptions(
        capitalization = KeyboardCapitalization.Characters,
        autoCorrectEnabled = false,
        keyboardType = if (radix == Radix.Hex) KeyboardType.Ascii else KeyboardType.Number,
        imeAction = ImeAction.Done,
    )
    val operands = model.operands
    val figure = (if (model.bitwise) model.result else a)?.toString()

    val number = CanonSection.of {
        CanonRows {
            CanonHeading("Number")
            CanonSegmented(Radix.entries.map { it.label }, radix.ordinal, columns = 2) { model.pick(Radix.entries[it]) }
            val error = model.errorText(model.reading, model.entry)
            // ponytail: long binary numbers scroll in the field; iOS shrinks the font down to 15 pt first
            CanonBigNumberField(
                model.entry,
                model::typeEntry,
                model::step,
                placeholder = radix.placeholder,
                unit = BaseNumber.base(model.entry, radix).short,
                label = "Number",
                onEndEditing = model::read,
                keyboardOptions = keyboard,
                textColour = if (error != null) c.destructive else null,
            )
            if (error != null) CanonStatusRow(error, CanonTone.Warn)
            CanonHeading("Word size")
            val needed = model.needed
            CanonSegmented(
                BaseNumber.words.map { "$it" },
                BaseNumber.words.indexOf(model.word),
                disabledIndices = BaseNumber.words.indices.filter { BaseNumber.words[it] < needed }.toSet(),
            ) { model.pickWord(BaseNumber.words[it]) }
        }
    }

    val values = CanonSection.of {
        CanonRows {
            CanonHeading("Values")
            Radix.entries.forEach { base ->
                CanonValueLine(base.label, a?.let { BaseNumber.format(it, base, prefixed = true) }, layout = CanonValueLayout.Above)
            }
        }
    }

    val operation = CanonSection.of {
        CanonRows {
            CanonRow("Bitwise") { CanonSwitch(model.bitwise, onCheckedChange = model::turnBitwise) }
            if (model.bitwise) {
                CanonSegmented(BitOperation.entries.map { it.title }, model.operation.ordinal, columns = 6) { model.pick(BitOperation.entries[it]) }
            }
            if (model.takesOperand) {
                CanonRow("B", flush = true) {
                    CanonTextCell(
                        model.operand,
                        model::typeOperand,
                        Modifier.fillMaxSize(),
                        placeholder = radix.placeholder,
                        textStyle = operandMono,
                        keyboardOptions = keyboard,
                    )
                }
                model.errorText(model.operandReading, model.operand)?.let { CanonStatusRow(it, CanonTone.Warn) }
            }
            if (model.bitwise && model.operation.takesShift) {
                CanonRow("Shift", flush = true) {
                    CanonNumberField(model.shift, model::shiftBy, Modifier.fillMaxSize(), range = 0..model.word - 1)
                }
            }
            if (model.bitwise) {
                CanonHeading("Result")
                val result = model.result
                Radix.entries.forEach { base ->
                    CanonValueLine(base.label, result?.let { BaseNumber.format(it, base, prefixed = true) }, layout = CanonValueLayout.Above)
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ratio = maxWidth.value / idealHeight(maxWidth.value, operands, model.word).coerceIn(SHORTEST, LIMIT)
        val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(ratio), maxHeight = 560.dp, showsPattern = true) {
            BitRegister(operands, model.word, figure) { operand, bit ->
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                model.toggle(operand, bit)
            }
        }
        CanonSplit(sidebar = listOf(canvas), main = listOf(number, values, operation), halves = true, collapsesTo = 200.dp)
    }
}

/** Height that shows the register at [width], the lamps laid out as they would be in a band [LIMIT] tall. */
private fun idealHeight(width: Float, operands: List<RegisterOperand>, word: Int): Float {
    val geometry = BitRegisterLayout.layout(
        word,
        operands.indices.map { it == 0 },
        operands.any { it.sign != null },
        width - PAD * 2,
        LIMIT - PAD * 1.5f - HEADER,
    )
    return PAD * 1.5f + HEADER + geometry.height
}

private class Lamp(val operand: Int, val bit: Int, val x: Float, val y: Float)

/** Lamp brightness where the last change began, per operand and bit, and the operands it changed to. */
private class LampClock {
    var operands: List<RegisterOperand> = emptyList()
    var word = 0
    var from: List<FloatArray> = emptyList()
    var restart = 0
    var zeroed = false
    val elapsed = Animatable(Float.MAX_VALUE)

    fun goal(operand: Int, bit: Int) = if ((operands[operand].value shr bit) and 1uL == 1uL) 1f else 0f

    fun level(operand: Int, bit: Int): Float {
        val goal = goal(operand, bit)
        val start = from.getOrNull(operand)?.getOrNull(bit) ?: return goal
        val time = if (zeroed) 0f else elapsed.value
        val p = ((time - RIPPLE_MS * bit) / CHANGE_MS).coerceIn(0f, 1f)
        return start + (goal - start) * (1 - (1 - p).pow(3))
    }

    /** Moves to new operands; a change of shape lands at once, a change of value fades the lamps from where they are. */
    fun show(operands: List<RegisterOperand>, word: Int) {
        if (operands == this.operands && word == this.word) return
        val reshaped = operands.size != this.operands.size || word != this.word ||
            operands.zip(this.operands).any { (new, old) -> new.role != old.role }
        from = if (reshaped) emptyList() else this.operands.indices.map { operand -> FloatArray(64) { bit -> if (bit < word) level(operand, bit) else 0f } }
        this.operands = operands
        this.word = word
        if (!reshaped) {
            zeroed = true
            restart++
        }
    }
}

/**
 * dt-ios BitRegister: the number as a register of lamps, most significant bit first, in nibbles with each nibble's
 * hex digit under it and the bit numbers over the first operand; the decimal value is the figure. With an operation
 * it is set as a sum: A, the sign and B, a rule in the primary colour, and the result. A tap on a lamp of A or B flips that bit.
 */
@Composable
private fun BitRegister(operands: List<RegisterOperand>, word: Int, figure: String?, onToggle: (Int, Int) -> Unit) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val clock = remember { LampClock() }
    // ponytail: a change of shape lands at once; iOS cross-dissolves it over 0.25 s
    clock.show(operands, word)
    LaunchedEffect(clock.restart) {
        if (clock.restart == 0) return@LaunchedEffect
        clock.elapsed.snapTo(0f)
        clock.zeroed = false
        val total = CHANGE_MS + RIPPLE_MS * word
        clock.elapsed.animateTo(total, tween(total.toInt(), easing = LinearEasing))
    }
    val top = LocalCanonCanvas.current.top + (PAD * 0.75f).dp

    BoxWithConstraints(Modifier.fillMaxSize().semantics { contentDescription = "Bits" }) {
        Row(
            Modifier.fillMaxWidth().padding(start = PAD.dp, top = top, end = PAD.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("$word bits", Modifier.weight(1f).padding(bottom = 6.dp), style = CanonText.footnoteSemibold, color = c.mutedForeground, maxLines = 1)
            CanonFigure(figure ?: "—", size = 30.sp, colour = if (figure == null) c.mutedForeground else c.primary)
        }

        val width = maxWidth.value
        val height = maxHeight.value
        val areaX = PAD
        val areaY = top.value + HEADER
        val areaW = maxOf(0f, width - PAD * 2)
        val areaH = maxOf(0f, height - top.value - HEADER - PAD * 0.75f)
        if (areaW <= 20 || areaH <= 20) return@BoxWithConstraints
        val signs = operands.any { it.sign != null }
        val numbered = operands.indices.map { it == 0 }
        val geometry = BitRegisterLayout.layout(word, numbered, signs, areaW, areaH)
        val cell = maxOf(4f, geometry.cell)
        val perLine = geometry.perLine
        val lines = word / perLine
        val left = areaX + maxOf(0f, (areaW - geometry.width) / 2) + if (signs) SIGN_WIDTH else 0f
        fun x(column: Int) = left + column * (cell + BIT_GAP) + (column / 4) * (NIBBLE_GAP - BIT_GAP)

        // positions first, so drawing and the tap targets agree
        val lamps = mutableListOf<Lamp>()
        val signRows = mutableListOf<Pair<Int, Float>>()
        val rules = mutableListOf<Float>()
        val indexRows = mutableListOf<Pair<Int, Float>>()
        val hexRows = mutableListOf<Triple<Int, Int, Float>>()
        var y = areaY + maxOf(0f, (areaH - geometry.height) / 2)
        operands.forEachIndexed { index, operand ->
            if (index > 0 && operand.role == RegisterRole.Result && signs) {
                y += RULE_ROOM
                rules += y - 1
                y += RULE_ROOM
            } else if (index > 0) {
                y += OPERAND_GAP
            }
            if (operand.sign != null) signRows += index to y + if (numbered[index]) INDEX_ROW else 0f
            for (line in 0 until lines) {
                if (line > 0) y += LINE_GAP
                val highest = word - 1 - line * perLine
                if (numbered[index]) {
                    indexRows += highest to y
                    y += INDEX_ROW
                }
                for (column in 0 until perLine) lamps += Lamp(index, highest - column, x(column), y)
                hexRows += Triple(index, highest, y + cell + 2)
                y += cell + HEX_ROW
            }
        }

        val hexStyle = TextStyle(fontFamily = DelphiMono, fontWeight = FontWeight.SemiBold, fontSize = with(density) { cell.times(0.5f).coerceIn(9f, 13f).dp.toSp() })
        val indexStyle = TextStyle(fontFamily = DelphiMono, fontWeight = FontWeight.Medium, fontSize = with(density) { 9.dp.toSp() }, color = c.mutedForeground)
        val signStyle = TextStyle(fontFamily = DelphiFont, fontWeight = FontWeight.Bold, fontSize = with(density) { cell.times(0.6f).coerceIn(12f, 17f).dp.toSp() })
        Canvas(Modifier.fillMaxSize()) {
            fun px(v: Float) = v.dp.toPx()
            val side = px(cell)
            val hairline = CanonMetrics.hairline.toPx()
            rules.forEach { ry ->
                drawRect(c.primary, Offset(px(left - SIGN_WIDTH), px(ry)), Size(px(x(perLine - 1) + cell - left + SIGN_WIDTH), px(2f)))
            }
            signRows.forEach { (index, lampTop) ->
                val operand = operands[index]
                val text = measurer.measure(operand.sign!!, signStyle.copy(color = if (operand.role == RegisterRole.Result) c.primary else c.mutedForeground))
                drawText(text, topLeft = Offset(px(left - SIGN_WIDTH), px(lampTop) + (side - text.size.height) / 2))
            }
            indexRows.forEach { (highest, iy) ->
                for (column in 0 until perLine step 4) {
                    drawText(measurer.measure("${highest - column}", indexStyle), topLeft = Offset(px(x(column)), px(iy)))
                }
            }
            lamps.forEach { lamp ->
                val operand = operands[lamp.operand]
                val lit = if (operand.role == RegisterRole.Operand) c.foreground else c.primary
                val on = clock.level(lamp.operand, lamp.bit)
                val origin = Offset(px(lamp.x), px(lamp.y))
                drawRect(mix(c.muted, lit, on), origin, Size(side, side))
                if (on < 1f) {
                    drawRect(
                        c.border.copy(alpha = 1 - on),
                        origin + Offset(hairline / 2, hairline / 2),
                        Size(side - hairline, side - hairline),
                        style = Stroke(hairline),
                    )
                }
            }
            hexRows.forEach { (index, highest, hy) ->
                val operand = operands[index]
                val ink = if (operand.role == RegisterRole.Result) c.primary else c.mutedForeground
                for (nibble in 0 until perLine / 4) {
                    val digit = ((operand.value shr (highest - nibble * 4 - 3)) and 0xFuL).toString(16).uppercase()
                    val text = measurer.measure(digit, hexStyle.copy(color = ink))
                    val span = px(x(nibble * 4 + 3) + cell - x(nibble * 4))
                    drawText(text, topLeft = Offset(px(x(nibble * 4)) + (span - text.size.width) / 2, px(hy)))
                }
            }
        }

        // tap targets: the lamp plus half the gap around it, as on iOS
        val reach: Dp = (BIT_GAP / 2 + 1).dp
        lamps.filter { operands[it.operand].editable }.forEach { lamp ->
            val on = clock.goal(lamp.operand, lamp.bit) == 1f
            Box(
                Modifier
                    .offset(lamp.x.dp - reach, lamp.y.dp - 4.dp)
                    .size(cell.dp + reach * 2, cell.dp + 8.dp)
                    .clearAndSetSemantics {
                        contentDescription = (if (lamp.operand == 0) "A " else "B ") + "Bit ${lamp.bit}"
                        stateDescription = if (on) "1" else "0"
                        role = Role.Button
                    }
                    .clickable(interactionSource = null, indication = null) { onToggle(lamp.operand, lamp.bit) },
            )
        }
    }
}
