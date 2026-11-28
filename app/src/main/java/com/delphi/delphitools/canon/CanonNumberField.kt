package com.delphi.delphitools.canon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.R
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours

/**
 * Minus and plus cells either side of a field's cell, divided by hairlines.
 * A tap anywhere in the centre focuses [focus]. [onStep] gets -1 or 1.
 */
@Composable
internal fun CanonFieldStepper(
    onStep: (Int) -> Unit,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    centrePadding: PaddingValues = PaddingValues(horizontal = 10.dp),
    centre: @Composable () -> Unit,
) {
    val c = LocalDelphiColours.current
    Row(
        modifier
            .height(IntrinsicSize.Min)
            .heightIn(min = CanonMetrics.controlHeight)
            .background(c.border),
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        CanonButton(
            "",
            Modifier.width(CanonMetrics.controlHeight).fillMaxHeight(),
            enabled = enabled,
            icon = pencil(R.drawable.pencil_minus),
            repeats = true,
            contentDescription = "Decrease",
        ) { onStep(-1) }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(c.card)
                .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled) {
                    focus.requestFocus()
                }
                .padding(centrePadding),
            contentAlignment = Alignment.Center,
        ) { centre() }
        CanonButton(
            "",
            Modifier.width(CanonMetrics.controlHeight).fillMaxHeight(),
            enabled = enabled,
            icon = pencil(R.drawable.pencil_plus),
            repeats = true,
            contentDescription = "Increase",
        ) { onStep(1) }
    }
}

/** Integer between step buttons, with an optional unit. Typing commits each valid value at once. */
@Composable
fun CanonNumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..Int.MAX_VALUE,
    step: Int = 1,
    unit: String? = null,
    label: String? = null,
    enabled: Boolean = true,
) {
    val c = LocalDelphiColours.current
    val focus = remember { FocusRequester() }
    val name = label ?: claimRowLabel()
    val focusManager = LocalFocusManager.current
    var draft by remember { mutableStateOf<String?>(null) }
    val shown = value.coerceIn(range)
    CanonFieldStepper(
        onStep = { direction ->
            draft = null
            val next = (shown.toLong() + direction.toLong() * step).coerceIn(range.first.toLong(), range.last.toLong()).toInt()
            if (next != shown) onValueChange(next)
        },
        focus = focus,
        modifier = modifier,
        enabled = enabled,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicTextField(
                value = draft ?: shown.toString(),
                onValueChange = { text ->
                    draft = text
                    text.trim().toIntOrNull()?.takeIf { it in range && it != shown }?.let(onValueChange)
                },
                enabled = enabled,
                singleLine = true,
                textStyle = CanonText.bodySemibold.copy(color = c.foreground, textAlign = TextAlign.End, fontFeatureSettings = TABULAR),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (range.first < 0) KeyboardType.Ascii else KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 48.dp)
                    .focusRequester(focus)
                    .onFocusChanged { if (!it.isFocused) draft = null }
                    .semantics { if (name != null) contentDescription = name },
            )
            if (unit != null) Text(unit, style = CanonText.caption, color = c.mutedForeground)
        }
    }
}
