package com.delphi.delphitools.canon

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import com.delphi.delphitools.ui.theme.DelphiDark
import kotlinx.coroutines.delay

private val codeText = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)

// --canon-copied in delphicanon tokens.css, light and dark
private val copiedLight = Color(0xFF34C759)
private val copiedDark = Color(0xFF30D158)

/**
 * Monospaced, selectable code with a copy button. Lines never wrap; the block scrolls sideways,
 * and code taller than [maxHeight] scrolls inside it. [language] titles a heading bar that holds
 * the copy button; without it the button sits below. The button copies [copyText] when set,
 * for a block that shows the code shortened.
 */
@Composable
fun CanonCodeBlock(
    code: String,
    modifier: Modifier = Modifier,
    language: String? = null,
    maxHeight: Dp? = null,
    copyText: String? = null,
) {
    val c = LocalDelphiColours.current
    val clipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)
    var copies by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copies) {
        if (copies == 0) return@LaunchedEffect
        copied = true
        delay(1500)
        copied = false
    }
    val copiedInk = if (c == DelphiDark) copiedDark else copiedLight

    val bar = @Composable {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (language != null) c.muted else c.card)
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                language?.uppercase() ?: "",
                style = CanonText.footnoteSemibold,
                color = c.mutedForeground,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .canonClickable {
                        // the check icon confirms; no toast (CANON.md, CanonToast)
                        clipboard?.setPrimaryClip(ClipData.newPlainText(language ?: "code", copyText ?: code))
                        copies++
                    }
                    .semantics { contentDescription = "Copy code" }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    tint = if (copied) copiedInk else c.mutedForeground,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }

    Column(modifier.fillMaxWidth().background(c.card)) {
        if (language != null) bar()
        SelectionContainer {
            if (maxHeight == null) {
                Text(
                    code,
                    style = codeText,
                    color = c.foreground,
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                )
            } else {
                CappedCode(code, maxHeight)
            }
        }
        if (language == null) bar()
    }
}

/** Lays out only the lines in view, as the iOS block does when capped. */
@Composable
private fun CappedCode(code: String, maxHeight: Dp) {
    val c = LocalDelphiColours.current
    val lines = remember(code) { code.split('\n') }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // unwrapped monospace: the longest line sets the width
    val width = remember(code, density) {
        with(density) { measurer.measure(lines.maxBy { it.length }, codeText, softWrap = false).size.width.toDp() } + 24.dp
    }
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = maxHeight).horizontalScroll(rememberScrollState())) {
        LazyColumn(Modifier.width(maxOf(width, maxWidth)), contentPadding = PaddingValues(12.dp)) {
            items(lines) { line -> Text(line, style = codeText, color = c.foreground, softWrap = false, maxLines = 1) }
        }
    }
}
