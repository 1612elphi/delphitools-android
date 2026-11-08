package com.delphi.delphitools.tools.audiovideo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours

private val labelWidth = 44.dp

/** Two lanes of cue blocks on one time axis: the cues as they came in, dim, over the cues as they go out, lit. */
@Composable
fun SubtitleTimeline(
    source: List<SubtitleCue>,
    result: List<SubtitleCue>,
    sourceFormat: String?,
    resultFormat: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalDelphiColours.current
    val flipped = LocalLayoutDirection.current == LayoutDirection.Rtl
    // lanes sit in the lower part: a banner's top runs under the status bar
    val top = LocalCanonCanvas.current.top
    BoxWithConstraints(modifier.fillMaxSize().clearAndSetSemantics {}) {
        val areaTop = top + 16.dp
        val stripStart = CanonMetrics.panelPadding + labelWidth
        val stripWidth = maxOf(1.dp, maxWidth - CanonMetrics.panelPadding * 2 - labelWidth)
        val stripHeight = maxHeight - areaTop - 16.dp
        val lane = maxOf(8.dp, (stripHeight - 10.dp) / 2)
        val lanes = listOf(areaTop, areaTop + stripHeight - lane)

        Canvas(Modifier.fillMaxSize()) {
            val span = maxOf(1L, (source + result).maxOfOrNull { it.end } ?: 1L).toDouble()
            val x0 = stripStart.toPx()
            val width = stripWidth.toPx()
            val height = lane.toPx()
            val hairline = CanonMetrics.hairline.toPx()
            val inks = listOf(c.mutedForeground.copy(alpha = 0.45f), c.primary)
            listOf(source, result).forEachIndexed { index, cues ->
                val y = lanes[index].toPx()
                val laneLeft = if (flipped) size.width - x0 - width else x0
                drawRect(c.border, Offset(laneLeft, y + height - hairline), Size(width, hairline))
                for (cue in cues) {
                    val from = (maxOf(0L, cue.start) / span * width).toFloat()
                    val w = maxOf(1f, ((cue.end - cue.start) / span * width).toFloat())
                    val x = if (flipped) laneLeft + width - from - w else laneLeft + from
                    drawRect(inks[index], Offset(x, y), Size(w, height - 2.dp.toPx()))
                }
            }
        }
        listOf(sourceFormat to c.mutedForeground, resultFormat to c.primary).forEachIndexed { index, (label, ink) ->
            Box(
                Modifier.offset(CanonMetrics.panelPadding, lanes[index]).size(labelWidth, lane),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(label ?: "", style = CanonText.footnoteSemibold, color = ink, maxLines = 1)
            }
        }
    }
}
