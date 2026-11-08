package com.delphi.delphitools.canon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours

/** A read-only value; [level], 0 to 1, draws a bar along the tile's bottom edge. */
@Immutable
data class CanonTile(val label: String, val value: String, val level: Float? = null)

private val tileWidth = 168.dp
private val tileValue = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)

@Composable
private fun Tile(tile: CanonTile, modifier: Modifier) {
    val c = LocalDelphiColours.current
    Box(modifier.background(c.card).semantics(mergeDescendants = true) {}) {
        Column(
            Modifier.padding(start = CanonMetrics.panelPadding, top = 10.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(tile.label, style = CanonText.captionSemibold, color = c.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tile.value, style = tileValue, color = c.foreground, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
        if (tile.level != null) {
            CanonBar(tile.level, Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp))
        }
    }
}

/**
 * Read-only values as a hairline grid of tiles, label over value.
 * As many columns as fit, never fewer than two; a short last row stretches its last tile.
 */
@Composable
fun CanonTiles(items: List<CanonTile>, modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = maxOf(2, (maxWidth / tileWidth).toInt())
        Column(Modifier.background(c.border), verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
            items.chunked(columns).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
                ) {
                    row.forEachIndexed { index, tile ->
                        val span = if (index == row.lastIndex) columns - row.size + 1 else 1
                        Tile(tile, Modifier.weight(span.toFloat()).fillMaxHeight())
                    }
                }
            }
        }
    }
}
