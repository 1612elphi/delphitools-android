package com.delphi.delphitools.canon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours

@Immutable
data class CanonDualItem(val id: String, val name: String, val caption: String? = null)

private enum class Side { Left, Right }

@Composable
private fun DualColumn(
    title: String,
    items: List<CanonDualItem>,
    selectedId: String?,
    height: Dp,
    modifier: Modifier,
    onSelect: (String) -> Unit,
) {
    val c = LocalDelphiColours.current
    Column(modifier) {
        CanonHeading(title)
        Column(
            Modifier
                .fillMaxWidth()
                .height(height)
                .background(c.card)
                .verticalScroll(rememberScrollState()),
        ) {
            items.forEachIndexed { index, item ->
                val selected = item.id == selectedId
                val ink = if (selected) c.primaryForeground else c.foreground
                if (index > 0) Spacer(Modifier.fillMaxWidth().height(CanonMetrics.hairline).background(c.border))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = CanonMetrics.controlHeight)
                        .canonClickable { onSelect(item.id) }
                        .background(if (selected) c.primary else c.card)
                        .semantics { this.selected = selected }
                        .padding(horizontal = CanonMetrics.panelPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(item.name, style = CanonText.body, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (item.caption != null) {
                        Text(item.caption, style = CanonText.footnoteSemibold.copy(fontWeight = CanonText.body.fontWeight), color = ink.copy(alpha = 0.7f), maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Two columns with one selection between them; the right column can be reordered.
 * Show moves the left selection right, Hide moves it back; [onReorder] gets the id and -1 or 1.
 */
@Composable
fun CanonDualList(
    left: List<CanonDualItem>,
    right: List<CanonDualItem>,
    leftTitle: String,
    rightTitle: String,
    onMoveRight: (String) -> Unit,
    onMoveLeft: (String) -> Unit,
    onReorder: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
    listHeight: Dp = 240.dp,
) {
    val c = LocalDelphiColours.current
    var picked by remember { mutableStateOf<Pair<Side, String>?>(null) }
    // the pick, while its item is still on that side
    val selection = picked?.takeIf { (side, id) -> (if (side == Side.Left) left else right).any { it.id == id } }
    val rightIndex = if (selection?.first == Side.Right) right.indexOfFirst { it.id == selection.second } else -1

    fun move(from: Side) {
        val (side, id) = selection ?: return
        if (side != from) return
        picked = (if (from == Side.Left) Side.Right else Side.Left) to id
        if (from == Side.Left) onMoveRight(id) else onMoveLeft(id)
    }

    Column(modifier.fillMaxWidth().background(c.border), verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
            DualColumn(leftTitle, left, selection?.takeIf { it.first == Side.Left }?.second, listHeight, Modifier.weight(1f)) {
                picked = Side.Left to it
            }
            DualColumn(rightTitle, right, selection?.takeIf { it.first == Side.Right }?.second, listHeight, Modifier.weight(1f)) {
                picked = Side.Right to it
            }
        }
        CanonGrid {
            val cell = Modifier.weight(1f).fillMaxHeight()
            CanonButton("Show", cell, enabled = selection?.first == Side.Left) { move(Side.Left) }
            CanonButton("Hide", cell, enabled = rightIndex >= 0) { move(Side.Right) }
            CanonButton("Up", cell, enabled = rightIndex > 0) { selection?.let { onReorder(it.second, -1) } }
            CanonButton("Down", cell, enabled = rightIndex in 0 until right.lastIndex) { selection?.let { onReorder(it.second, 1) } }
        }
    }
}
