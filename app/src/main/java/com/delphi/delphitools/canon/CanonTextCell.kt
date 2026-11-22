package com.delphi.delphitools.canon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours

/**
 * Text field that meets its row's edges. [onValueChange] runs on every edit, [onEndEditing] when the field loses focus.
 * [colour] replaces the text colour, as for a query that matches nothing.
 */
@Composable
fun CanonTextCell(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    width: Dp = 180.dp,
    enabled: Boolean = true,
    textStyle: TextStyle = CanonText.body,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    colour: Color? = null,
    onEndEditing: () -> Unit = {},
) {
    val c = LocalDelphiColours.current
    val focusManager = LocalFocusManager.current
    val focused = remember { booleanArrayOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle.copy(color = colour ?: c.foreground),
        cursorBrush = SolidColor(c.primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier
            .width(width)
            .heightIn(min = CanonMetrics.controlHeight)
            .background(c.card)
            .onFocusChanged {
                if (focused[0] && !it.isFocused) onEndEditing()
                focused[0] = it.isFocused
            },
        decorationBox = { field ->
            Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = textStyle, color = c.mutedForeground, maxLines = 1)
                field()
            }
        },
    )
}
