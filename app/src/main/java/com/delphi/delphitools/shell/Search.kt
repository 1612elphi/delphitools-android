package com.delphi.delphitools.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.LayoutBackDismiss
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours

// dt-ios SearchOverlay: recents shown before typing
private const val SHOWN_RECENTS = 8

/** dt-ios SearchOverlay: a field over the tool list; recents before typing, matches after. */
@Composable
internal fun SearchOverlay(onOpen: (Tool) -> Unit, onDismiss: () -> Unit) {
    val c = LocalDelphiColours.current
    val stores = LocalShellStores.current
    val back = remember { Animatable(0f) }
    LayoutBackDismiss(true, back) { onDismiss() }
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val trimmed = query.trim()
    val results = remember(trimmed) { searchTools(trimmed) }

    fun pick(tool: Tool) {
        onOpen(tool)
        onDismiss()
    }

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = 1f - 0.1f * back.value
                scaleY = 1f - 0.1f * back.value
                alpha = 1f - 0.5f * back.value
            }
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PaneBar("Search", done = onDismiss, doneLabel = "Cancel")
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            Row(
                Modifier
                    .padding(horizontal = CanonMetrics.panelPadding, vertical = 8.dp)
                    .fillMaxWidth()
                    .background(c.muted)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(pencil(R.drawable.pencil_search), contentDescription = null, tint = c.mutedForeground, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search tools…", style = CanonText.body, color = c.mutedForeground)
                    BasicTextField(
                        query,
                        { query = it },
                        singleLine = true,
                        textStyle = CanonText.body.copy(color = c.foreground),
                        cursorBrush = SolidColor(c.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                when {
                    trimmed.isEmpty() -> {
                        val recents = stores.recentTools.take(SHOWN_RECENTS)
                        if (recents.isNotEmpty()) {
                            item { CanonHeading("Recent") }
                            items(recents, key = { "recent/${it.id}" }) { SearchRow(it) { pick(it) } }
                        }
                    }
                    results.isEmpty() -> item {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(pencil(R.drawable.pencil_search), contentDescription = null, tint = c.mutedForeground, modifier = Modifier.size(36.dp))
                            Text("No Results", style = CanonText.bodySemibold, color = c.foreground)
                            Text("No tools match \"$trimmed\".", style = CanonText.caption, color = c.mutedForeground)
                        }
                    }
                    else -> items(results, key = { it.id }) { SearchRow(it) { pick(it) } }
                }
            }
        }
    }
}

@Composable
internal fun SearchRow(tool: Tool, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CanonMetrics.rowHeight)
            .canonClickable(onClick = onClick)
            .padding(horizontal = CanonMetrics.panelPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(pencil(tool.icon), contentDescription = null, tint = c.primary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(tool.name, style = CanonText.bodyMedium, color = c.foreground)
            Text(tool.description, style = CanonText.caption, color = c.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(tool.category.title, style = CanonText.caption, color = c.mutedForeground)
    }
}
