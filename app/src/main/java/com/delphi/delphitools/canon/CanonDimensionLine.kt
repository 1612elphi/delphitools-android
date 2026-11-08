package com.delphi.delphitools.canon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

val CanonDimensionLineHeight = 32.dp

/** Room a picture leaves on its top and leading sides for a pair of lines and their figures. */
val CanonDimensionLineRoom = 44.dp

/**
 * A measured span: a rule with end ticks along the bottom edge, the pixel figure centred above it.
 * `blank` mutes the rule and hides the figure, so an empty picture keeps a loaded one's frame.
 */
@Composable
fun CanonDimensionLine(pixels: Int, modifier: Modifier = Modifier, blank: Boolean = false) {
    val c = LocalDelphiColours.current
    val ink = if (blank) c.mutedForeground else c.primary
    Box(
        modifier
            .height(CanonDimensionLineHeight)
            .drawBehind {
                val tick = 9.dp.toPx()
                val line = CanonMetrics.hairline.toPx()
                drawRect(ink, Offset(0f, size.height - tick / 2 - line / 2), Size(size.width, line))
                drawRect(ink, Offset(0f, size.height - tick), Size(line, tick))
                drawRect(ink, Offset(size.width - line, size.height - tick), Size(line, tick))
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (!blank) {
            CanonFigure("$pixels", Modifier.padding(bottom = 5.dp), unit = "px", size = 17.sp, colour = c.primary, halo = c.muted)
        }
    }
}

/** Where a line goes, in pixels: its unturned size, centred on `centre`, turned by `rotation` degrees. */
data class CanonDimensionPlacement(val centre: Offset, val size: Size, val rotation: Float = 0f)

/**
 * The across line over `span` and the down line, turned, along its leading side,
 * each `gap` outside `edge`. Rects are in pixels.
 */
fun Density.canonDimensionLines(
    span: Rect,
    rightToLeft: Boolean,
    edge: Rect = span,
    gap: Dp = 12.dp,
): Pair<CanonDimensionPlacement, CanonDimensionPlacement> {
    val height = CanonDimensionLineHeight.toPx()
    val gapPx = gap.toPx()
    val across = CanonDimensionPlacement(Offset(span.center.x, edge.top - gapPx - height / 2), Size(span.width, height))
    val downX = if (rightToLeft) edge.right + gapPx + height / 2 else edge.left - gapPx - height / 2
    val down = CanonDimensionPlacement(Offset(downX, span.center.y), Size(span.height, height), if (rightToLeft) 90f else -90f)
    return across to down
}

/** Frame, in pixels, for a picture of `size` fitted into `area`, leaving room for the lines on the leading and top sides. */
fun Density.canonDimensionSheet(size: Size, area: Rect, rightToLeft: Boolean): Rect {
    val room = CanonDimensionLineRoom.toPx()
    val roomWidth = area.width - room
    val roomHeight = area.height - room
    if (size.width <= 0f || size.height <= 0f || roomWidth <= 0f || roomHeight <= 0f) return Rect.Zero
    val scale = min(roomWidth / size.width, roomHeight / size.height)
    // keep degenerate sizes visible
    val fitted = Size(max(1f, size.width * scale), max(1f, size.height * scale))
    val left = area.center.x - (fitted.width + room) / 2
    val top = area.center.y - (fitted.height + room) / 2
    return Rect(Offset(if (rightToLeft) left else left + room, top + room), fitted)
}

/** Sizes and places the element as `placement` says, in its parent's pixel space. */
fun Modifier.canonPlaced(placement: CanonDimensionPlacement): Modifier = layout { measurable, _ ->
    val width = placement.size.width.roundToInt()
    val height = placement.size.height.roundToInt()
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) {
        placeable.placeWithLayer(
            (placement.centre.x - width / 2f).roundToInt(),
            (placement.centre.y - height / 2f).roundToInt(),
        ) { rotationZ = placement.rotation }
    }
}
