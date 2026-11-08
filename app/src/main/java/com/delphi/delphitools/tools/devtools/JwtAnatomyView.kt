package com.delphi.delphitools.tools.devtools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlin.math.abs

private val small = CanonText.captionSemibold
private val bar = 10.dp
private val figureRow = 40.dp
private val partNames = listOf("Header", "Payload", "Signature")

private class Part(val name: String, val length: Int, val detail: String, val failed: Boolean, val colour: Color)

/** dt-ios JWTAnatomy: the token's three parts to scale, a timeline of its time claims, and how long until or since it expires. */
@Composable
fun JwtAnatomyView(result: JwtResult, nowSeconds: Double, modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val top = LocalCanonCanvas.current.top

    val decoded = (result as? JwtResult.Decoded)?.decoded
    val colours = listOf(c.foreground, c.primary, c.mutedForeground)
    val parts = if (decoded == null) emptyList() else {
        val segments = listOf(
            decoded.header.byteCount to decoded.header.error,
            decoded.payload.byteCount to decoded.payload.error,
            decoded.signature.byteCount to decoded.signature.error,
        )
        segments.mapIndexed { index, (bytes, error) ->
            Part(partNames[index], maxOf(1, bytes ?: 1), error?.label ?: "${bytes ?: 0} bytes", error != null, colours[index])
        }
    }
    val claims = decoded?.timeClaims ?: emptyList()
    val state = decoded?.let { JwtEngine.status(it, nowSeconds) }
    fun stateColour(state: JwtState?) = when (state) {
        JwtState.Expired -> c.destructive
        JwtState.Early -> c.mutedForeground
        else -> c.primary
    }
    val span = state?.let {
        val target = claims.firstOrNull { it.key == if (state == JwtState.Early) "nbf" else "exp" }?.seconds ?: nowSeconds
        JwtEngine.span(abs(target - nowSeconds))
    }

    val padding = CanonMetrics.panelPadding
    val lineHeight = with(density) { measurer.measure("0", small).size.height.toDp() }
    val tokenHeight = bar + 6.dp + lineHeight * 2
    val timelineHeight = if (claims.isEmpty()) 0.dp else 18.dp + lineHeight * 2 + 4.dp + 12.dp + 4.dp + lineHeight
    val contentHeight = tokenHeight + timelineHeight + if (state == null) 0.dp else 12.dp + figureRow
    val contentTop = top + padding * 0.75f

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(contentTop + contentHeight + padding * 0.75f)
            .semantics {
                contentDescription = parts.joinToString(", ") { "${it.name} ${it.detail}" }
                if (state != null && span != null) stateDescription = "${state.text}, ${span.first} ${span.second}"
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(maxHeight)) {
            val left = padding.toPx()
            val room = maxOf(0f, size.width - left * 2)
            val gap = 8.dp.toPx()
            val barPx = bar.toPx()
            val line = lineHeight.toPx()
            val y = contentTop.toPx()
            if (parts.isEmpty()) {
                val width = (room - gap * 2) / 3
                partNames.forEachIndexed { index, name ->
                    val x = left + index * (width + gap)
                    dashed(Offset(x, y), Size(width, barPx), c.mutedForeground)
                    label(measurer, name, Offset(x, y + barPx + 6.dp.toPx()), c.mutedForeground)
                }
            } else {
                val total = parts.sumOf { it.length }.toFloat()
                val usable = room - gap * (parts.size - 1)
                // room for part names
                val least = minOf(64.dp.toPx(), usable / parts.size)
                val widths = parts.map { maxOf(least, usable * it.length / total) }.toMutableList()
                val over = widths.sum() - usable
                if (over > 0) widths.indices.maxBy { widths[it] }.let { widths[it] -= over }
                var x = left
                parts.zip(widths).forEach { (part, width) ->
                    if (part.failed) dashed(Offset(x, y), Size(width, barPx), c.destructive) else drawRect(part.colour, Offset(x, y), Size(width, barPx))
                    val textY = y + barPx + 6.dp.toPx()
                    label(measurer, part.name, Offset(x, textY), if (part.failed) c.destructive else c.foreground)
                    label(measurer, part.detail, Offset(x, textY + line), if (part.failed) c.destructive else c.mutedForeground)
                    // jwt dot separator
                    if (x > left) drawRect(c.mutedForeground, Offset(x - gap / 2 - 1.5.dp.toPx(), y + barPx / 2 - 1.5.dp.toPx()), Size(3.dp.toPx(), 3.dp.toPx()))
                    x += width + gap
                }
            }
            if (claims.isNotEmpty()) timeline(measurer, claims, nowSeconds, state, left, room, y + tokenHeight.toPx() + 18.dp.toPx(), line, ::stateColour, c.border, c.foreground)
        }
        if (state != null && span != null) {
            val ink = stateColour(state)
            Row(
                Modifier
                    .offset(x = padding, y = contentTop + contentHeight - figureRow)
                    .height(figureRow),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CanonFigure(span.first, unit = span.second, size = 26.sp, colour = ink, halo = c.card)
                Text(state.text, style = CanonText.footnoteSemibold, color = ink, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

private fun DrawScope.dashed(origin: Offset, size: Size, colour: Color) {
    val hair = CanonMetrics.hairline.toPx()
    drawRect(
        colour,
        origin + Offset(hair / 2, hair / 2),
        Size(size.width - hair, size.height - hair),
        style = Stroke(hair, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
    )
}

private fun DrawScope.label(measurer: TextMeasurer, text: String, at: Offset, colour: Color) {
    drawText(measurer, text, at, small.copy(color = colour))
}

private fun DrawScope.timeline(
    measurer: TextMeasurer,
    claims: List<JwtTimeClaim>,
    now: Double,
    state: JwtState?,
    left: Float,
    room: Float,
    y: Float,
    line: Float,
    stateColour: (JwtState?) -> Color,
    border: Color,
    ink: Color,
) {
    val times = claims.map { it.seconds } + now
    val low = times.min()
    val span = maxOf(times.max() - low, 60.0)
    // label room either side
    val inset = 8.dp.toPx()
    fun x(seconds: Double) = left + inset + ((seconds - low) / span).toFloat() * (room - inset * 2)
    val axis = y + line * 2 + 4.dp.toPx() + 6.dp.toPx()
    val hair = CanonMetrics.hairline.toPx()

    drawRect(border, Offset(left, axis - hair / 2), Size(room, hair))
    val start = claims.firstOrNull { it.key == "nbf" }?.seconds ?: claims.firstOrNull { it.key == "iat" }?.seconds
    val end = claims.firstOrNull { it.key == "exp" }?.seconds
    if (start != null && end != null) {
        val colour = stateColour(state ?: JwtState.Valid)
        drawRect(
            colour.copy(alpha = if (state == JwtState.Valid) 1f else 0.5f),
            Offset(x(start), axis - 3.dp.toPx()),
            Size(maxOf(2.dp.toPx(), x(end) - x(start)), 6.dp.toPx()),
        )
    }

    // stagger colliding labels
    val lastRight = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE)
    for ((seconds, group) in claims.groupBy { it.seconds }.toSortedMap()) {
        val tick = x(seconds)
        drawRect(ink, Offset(tick - hair / 2, axis - 8.dp.toPx()), Size(hair, 16.dp.toPx()))
        val text = group.joinToString(" · ") { it.label } + "  " + group[0].absolute.substringBefore(',')
        val laid = measurer.measure(text, small.copy(color = ink))
        val width = laid.size.width
        val origin = minOf(maxOf(tick - width / 2, left), left + room - width)
        val row = if (origin > lastRight[1] + 8.dp.toPx()) 1 else 0
        lastRight[row] = origin + width
        drawText(laid, topLeft = Offset(origin, y + row * line))
    }

    val mark = x(now)
    val colour = stateColour(state ?: JwtState.Valid)
    drawRect(colour, Offset(mark - 1.dp.toPx(), axis - 10.dp.toPx()), Size(2.dp.toPx(), 20.dp.toPx()))
    val laid = measurer.measure("Now", small.copy(color = colour))
    drawText(laid, topLeft = Offset(minOf(maxOf(mark - laid.size.width / 2, left), left + room - laid.size.width), axis + 12.dp.toPx()))
}
