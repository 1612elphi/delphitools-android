package com.delphi.delphitools.canon

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.R
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours

/**
 * The output block of a text tool: a heading, the text (selectable) or [empty] when blank,
 * and Share beside a large Copy. [busy] keeps the empty row away while output is still to come.
 * [styled], the output with its marks, shows in place of [text].
 * [extra] rows sit between the text and the actions.
 */
@Composable
fun CanonTextOutput(
    text: String,
    heading: String,
    fileName: String,
    modifier: Modifier = Modifier,
    style: TextStyle = CanonText.body,
    busy: Boolean = false,
    empty: String = "None",
    styled: AnnotatedString? = null,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val blank = text.isBlank()
    CanonRows(modifier) {
        CanonHeading(heading)
        if (!blank) {
            SelectionContainer {
                Text(
                    styled ?: AnnotatedString(text),
                    style = style,
                    color = c.foreground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.card)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        } else if (!busy) {
            CanonEmptyRow(empty)
        }
        extra()
        CanonGrid {
            CanonButton(
                "Share",
                Modifier.weight(1f).fillMaxHeight(),
                kind = CanonButtonKind.Secondary,
                enabled = !blank,
                icon = pencil(R.drawable.pencil_share),
                followsWorkflow = false,
            ) {
                // ponytail: shares the text, not a file named fileName; a file needs a FileProvider in the manifest
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text)
                    .putExtra(Intent.EXTRA_TITLE, fileName)
                context.startActivity(Intent.createChooser(send, null))
            }
            CanonButton(
                "Copy",
                Modifier.weight(1f).fillMaxHeight(),
                kind = CanonButtonKind.Primary,
                enabled = !blank,
                icon = pencil(R.drawable.pencil_copy),
                oversized = true,
                flashesCopied = true,
            ) {
                // the flashed title confirms; no toast (CANON.md, CanonToast)
                clipboard?.setPrimaryClip(ClipData.newPlainText(heading, text))
            }
        }
    }
}
