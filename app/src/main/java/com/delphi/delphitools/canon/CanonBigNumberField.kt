package com.delphi.delphitools.canon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours

private val bigNumber = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 36.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

/**
 * A tool's main number, large and centred over its unit, between step buttons that repeat while held.
 * The tool parses [text] and sets it; [onStep] gets -1 or 1; [onEndEditing] runs when typing ends.
 */
@Composable
fun CanonBigNumberField(
    text: String,
    onTextChange: (String) -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    unit: String? = null,
    label: String? = null,
    enabled: Boolean = true,
    onEndEditing: () -> Unit = {},
) {
    val c = LocalDelphiColours.current
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val editing = remember { booleanArrayOf(false) }
    CanonFieldStepper(
        onStep = onStep,
        focus = focus,
        modifier = modifier,
        enabled = enabled,
        centrePadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                enabled = enabled,
                singleLine = true,
                textStyle = bigNumber.copy(color = c.foreground),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged {
                        if (editing[0] && !it.isFocused) onEndEditing()
                        editing[0] = it.isFocused
                    }
                    .semantics { if (label != null) contentDescription = label },
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.Center) {
                        if (text.isEmpty()) Text(placeholder, style = bigNumber, color = c.mutedForeground)
                        field()
                    }
                },
            )
            if (unit != null) {
                Text(unit, style = CanonText.footnoteSemibold.copy(fontWeight = FontWeight.Bold), color = c.mutedForeground)
            }
        }
    }
}
