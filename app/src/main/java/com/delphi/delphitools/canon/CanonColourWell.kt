package com.delphi.delphitools.canon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.colour.ColourFormat
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.formatted
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.parseHex
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import java.util.Locale

/**
 * Swatch cell that opens the picker, beside a hex field; `format`, the app's primary
 * colour format, is echoed under the hex unless it is hex.
 */
@Composable
fun CanonColourWell(
    colour: Rgb,
    onChange: (Rgb) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    format: ColourFormat = ColourFormat.HEX,
) {
    val c = LocalDelphiColours.current
    val focus = LocalFocusManager.current
    var picking by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf<String?>(null) }
    val digits = colour.hex.drop(1)
    fun read() {
        val text = typed ?: return
        typed = null
        parseHex(text.filter { it.isLetterOrDigit() })?.takeIf { it.hex != colour.hex }?.let(onChange)
    }
    Row(
        modifier
            .height(IntrinsicSize.Min)
            .heightIn(min = 56.dp)
            .background(c.border),
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        Box(
            Modifier
                .width(56.dp)
                .fillMaxHeight()
                .semantics { contentDescription = label ?: "Colour" }
                .canonClickable { picking = true }
                .background(colour.toCanonColor()),
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(c.card)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        ) {
            if (label != null) {
                Text(label.uppercase(Locale.getDefault()), style = CanonText.captionSemibold, color = c.mutedForeground)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("#", style = CanonColourType.mono16, color = c.mutedForeground)
                BasicTextField(
                    value = typed ?: digits,
                    onValueChange = { text -> typed = text.filter { it.isLetterOrDigit() }.take(6).uppercase(Locale.ROOT) },
                    singleLine = true,
                    textStyle = CanonColourType.mono16.copy(color = c.foreground),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = label ?: "Hex" }
                        .onFocusChanged { if (!it.isFocused) read() },
                )
            }
            if (format != ColourFormat.HEX) {
                Text(colour.formatted(format), style = CanonColourType.mono11, color = c.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (picking) CanonColourPickerDialog(colour, onChange) { picking = false }
}
