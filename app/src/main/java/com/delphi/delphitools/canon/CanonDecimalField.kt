package com.delphi.delphitools.canon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToLong

private const val TYPING_PAUSE = 600L
private val decimalText = Regex("""[-+]?(\d+\.?\d*|\.\d+)""")

/** A decimal typed into a field; a comma counts as the decimal point. */
internal fun decimalFieldParse(text: String): Double? =
    text.trim().replace(',', '.').takeIf(decimalText::matches)?.toDouble()

/** Up to [decimals] places, trailing zeros dropped, no grouping, a full stop. */
internal fun decimalFieldFormat(value: Double, decimals: Int): String {
    val text = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString()
    return if (text == "-0") "0" else text
}

/**
 * A decimal number with step buttons and a unit; seconds to three places in tenths by default.
 * Typing commits after a pause or when the field loses focus.
 * [signed] gives the keyboard a minus key and lifts the lower bound.
 */
@Composable
fun CanonDecimalField(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    unit: String = "s",
    step: Double = 0.1,
    decimals: Int = 3,
    signed: Boolean = false,
    range: ClosedFloatingPointRange<Double> = (if (signed) Double.NEGATIVE_INFINITY else 0.0)..Double.POSITIVE_INFINITY,
    label: String? = null,
    enabled: Boolean = true,
) {
    val c = LocalDelphiColours.current
    val focus = remember { FocusRequester() }
    val name = label ?: claimRowLabel()
    val focusManager = LocalFocusManager.current
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    var draft by remember { mutableStateOf<String?>(null) }

    fun apply(raw: Double) {
        val grain = Math.pow(10.0, decimals.toDouble())
        val next = ((raw * grain).roundToLong() / grain).coerceIn(range)
        if (next != current) change(next)
    }

    fun commit() {
        draft?.let(::decimalFieldParse)?.let(::apply)
    }

    LaunchedEffect(draft) {
        if (draft == null) return@LaunchedEffect
        delay(TYPING_PAUSE)
        commit()
    }

    val text = draft ?: decimalFieldFormat(value, decimals)
    CanonFieldStepper(
        onStep = { direction ->
            val base = draft?.let(::decimalFieldParse) ?: current
            draft = null
            apply(base + direction * step)
        },
        focus = focus,
        modifier = modifier,
        enabled = enabled,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicTextField(
                value = text,
                onValueChange = { draft = it },
                enabled = enabled,
                singleLine = true,
                textStyle = CanonText.bodySemibold.copy(color = c.foreground, textAlign = TextAlign.End, fontFeatureSettings = TABULAR),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (signed) KeyboardType.Ascii else KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 72.dp)
                    .focusRequester(focus)
                    .onFocusChanged {
                        if (!it.isFocused && draft != null) {
                            commit()
                            draft = null
                        }
                    }
                    .semantics {
                        if (name != null) contentDescription = name
                        stateDescription = "$text $unit"
                    },
            )
            Text(unit, style = CanonText.caption, color = c.mutedForeground)
        }
    }
}
