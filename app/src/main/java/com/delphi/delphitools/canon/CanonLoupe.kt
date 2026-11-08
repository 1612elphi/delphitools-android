package com.delphi.delphitools.canon

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

val CanonLoupeBorder = 6.dp

/**
 * The pixels around a sample point, magnified without smoothing, framed in the sampled colour.
 * The centre pixel is outlined in the ink that reads on `colour`.
 */
@Composable
fun CanonLoupe(pixels: ImageBitmap?, colour: Color, modifier: Modifier = Modifier) {
    val ink = if (takesBlackInk(colour)) Color.Black else Color.White
    Canvas(
        modifier
            .dropShadow(RectangleShape, Shadow(8.dp, Color.Black, offset = DpOffset(0.dp, 3.dp), alpha = 0.35f))
    ) {
        drawRect(colour)
        val border = CanonLoupeBorder.toPx()
        val inner = IntSize((size.width - 2 * border).roundToInt(), (size.height - 2 * border).roundToInt())
        if (pixels != null) {
            drawImage(
                pixels,
                dstOffset = IntOffset(border.roundToInt(), border.roundToInt()),
                dstSize = inner,
                filterQuality = FilterQuality.None,
            )
        }
        val count = pixels?.width ?: 31
        val side = inner.width / max(count, 1).toFloat()
        val line = 1.dp.toPx()
        drawRect(
            ink,
            Offset(center.x - side / 2 - line / 2, center.y - side / 2 - line / 2),
            Size(side + line, side + line),
            style = Stroke(line),
        )
    }
}

/**
 * Where a loupe of `side` goes near `centre` in a view of `size`, in pixels: up and to the left, `gap` away,
 * turned to the other side where it would come within `margin` of the edge or above `top`, then kept inside.
 * `sides` (left of, above the point) keeps the side it was on, so a dragged loupe does not jump.
 */
fun canonLoupeFrame(
    centre: Offset,
    side: Float,
    gap: Float,
    size: Size,
    top: Float,
    margin: Float,
    sides: Pair<Boolean, Boolean>? = null,
): Rect {
    val left = sides?.first ?: (centre.x - gap - side >= margin)
    val above = sides?.second ?: (centre.y - gap - side >= top)
    val x = if (left) centre.x - gap - side else centre.x + gap
    val y = if (above) centre.y - gap - side else centre.y + gap
    return Rect(
        Offset(min(max(x, margin), size.width - side - margin), min(max(y, top), size.height - side - margin)),
        Size(side, side),
    )
}

// WCAG contrast against black is at least that against white above relative luminance 0.179
private fun takesBlackInk(colour: Color) = colour.luminance() >= 0.179f
