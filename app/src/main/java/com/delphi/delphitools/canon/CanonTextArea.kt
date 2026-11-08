package com.delphi.delphitools.canon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import com.delphi.delphitools.ui.theme.DelphiMono

private val areaText = TextStyle(fontFamily = DelphiMono, fontSize = 13.sp, lineHeight = 18.sp)

/**
 * Multi-line monospaced input, wrapping, a fixed height with its own scrolling.
 * [onValueChange] runs for typed edits only. [onEditingChange] reports whether the keyboard is up for it.
 */
@Composable
fun CanonTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    height: Dp = 200.dp,
    label: String? = null,
    onEditingChange: ((Boolean) -> Unit)? = null,
) {
    val c = LocalDelphiColours.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = areaText.copy(color = c.foreground),
        cursorBrush = SolidColor(c.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(c.muted)
            .onFocusChanged { onEditingChange?.invoke(it.isFocused) }
            .semantics { if (label != null) contentDescription = label },
        decorationBox = { field ->
            Box(Modifier.padding(12.dp)) {
                if (value.isEmpty()) Text(placeholder, style = areaText, color = c.mutedForeground)
                field()
            }
        },
    )
}
