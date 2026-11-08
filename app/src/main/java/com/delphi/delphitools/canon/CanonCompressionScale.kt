package com.delphi.delphitools.canon

import android.text.format.Formatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Two bars to one scale: the original's bytes over the result's, the saving as a figure.
 * Sizes of zero or less are unknown; the result bar and its figure turn red when it grew.
 * [isWorking] shows a loader beside the figure.
 */
@Composable
fun CanonCompressionScale(
    before: Long,
    after: Long,
    format: String,
    modifier: Modifier = Modifier,
    isWorking: Boolean = false,
) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val bytes = { count: Long -> Formatter.formatShortFileSize(context, count) }
    val known = before > 0 && after > 0
    val grew = after > before
    val tone by animateColorAsState(if (grew) c.destructive else c.primary, tween(200))
    val figure = if (known) savingsFigure(before, after) else "—"
    Column(
        modifier
            .fillMaxSize()
            .background(c.card)
            .padding(CanonMetrics.panelPadding),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            Modifier.align(Alignment.End).padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isWorking) CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground)
            CanonFigure(figure, unit = if (known) "%" else "", size = 40.sp, colour = tone)
        }
        ScaleBar("Original", if (before > 0) bytes(before) else null, 1f, c.muted, c.foreground)
        Box(Modifier.height(CanonMetrics.hairline))
        ScaleBar(
            format,
            if (known) bytes(after) else null,
            if (known) minOf(1f, after.toFloat() / before) else 0f,
            tone,
            c.primaryForeground,
        )
    }
}

private fun savingsFigure(before: Long, after: Long): String {
    val change = (after - before).toDouble() / before * 100
    // swift rounds halves away from zero
    val saving = (abs(change).roundToLong() * if (change < 0) -1 else 1).toInt()
    return when {
        saving == 0 -> "±0"
        saving < 0 -> "−${-saving}"
        else -> "+$saving"
    }
}

@Composable
private fun ScaleBar(label: String, size: String?, share: Float, fill: Color, ink: Color) {
    val c = LocalDelphiColours.current
    val text = listOfNotNull(label, size).joinToString("  ")
    val shown by animateFloatAsState(share, spring(dampingRatio = 0.7f, stiffness = 110f))
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        val density = LocalDensity.current
        val filled = maxWidth * shown
        val textWidth = with(density) { measurer.measure(text, CanonText.footnoteSemibold).size.width.toDp() }
        val inside = filled >= textWidth + 20.dp
        Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(fill))
        Text(
            text,
            style = CanonText.footnoteSemibold,
            color = if (inside) ink else c.foreground,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = if (inside) 10.dp else filled + 10.dp),
        )
    }
}
