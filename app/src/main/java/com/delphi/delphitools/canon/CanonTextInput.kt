package com.delphi.delphitools.canon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TYPING_DELAY = 400L

/**
 * The input block of a text tool: a text area with Paste and Clear under it.
 * [onTextChange] gets every edit, for the caller's [text]; [onChange] gets typing once it pauses,
 * and Paste and Clear at once. Text the caller sets from code does not reach [onChange].
 */
@Composable
fun CanonTextInput(
    text: String,
    onTextChange: (String) -> Unit,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Type or paste",
    height: Dp = 128.dp,
    label: String = "Input",
) {
    val c = LocalDelphiColours.current
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settled by rememberUpdatedState(onChange)
    var typed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(typed) {
        val pending = typed ?: return@LaunchedEffect
        delay(TYPING_DELAY)
        typed = null
        settled(pending)
    }

    fun set(new: String) {
        typed = null
        onTextChange(new)
        onChange(new)
    }

    Column(modifier.fillMaxWidth().background(c.border), verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
        CanonTextArea(
            text,
            onValueChange = {
                onTextChange(it)
                typed = it
            },
            placeholder = placeholder,
            height = height,
            label = label,
        )
        CanonGrid {
            CanonButton("Paste", Modifier.weight(1f).fillMaxHeight(), icon = Icons.Outlined.ContentPaste) {
                scope.launch {
                    val clip = clipboard.getClipEntry()?.clipData ?: return@launch
                    if (clip.itemCount > 0) set(clip.getItemAt(0).coerceToText(context).toString())
                }
            }
            CanonButton("Clear", Modifier.weight(1f).fillMaxHeight(), enabled = text.isNotEmpty(), icon = Icons.Outlined.Delete) {
                set("")
            }
        }
    }
}
