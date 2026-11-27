package com.delphi.delphitools.canon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.delphi.delphitools.ui.theme.LocalDelphiColours

/** Asks for a name; [onConfirm] gets the trimmed text. */
@Composable
fun CanonNameDialog(
    title: String,
    initial: String,
    confirm: String,
    placeholder: String = "Name",
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val c = LocalDelphiColours.current
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(320.dp).background(c.border).padding(CanonMetrics.frame), verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
            CanonHeading(title)
            CanonTextCell(
                text,
                { text = it },
                Modifier.fillMaxWidth(),
                placeholder = placeholder,
                keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Done),
            )
            CanonGrid {
                CanonButton("Cancel", Modifier.weight(1f).fillMaxHeight(), onClick = onDismiss)
                CanonButton(confirm, Modifier.weight(1f).fillMaxHeight(), kind = CanonButtonKind.Primary) {
                    onConfirm(text.trim())
                    onDismiss()
                }
            }
        }
    }
}
