package com.delphi.delphitools.tools.images

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.canonGrabPan
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Touch reach of the crop's edges, and the page's margin. */
val ImageCropReach: Dp = 22.dp

/** Which crop edges a drag holds. */
data class CropEdges(val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean)

/** dt-ios PDFCropCanvas geometry, in the canvas's pixels and the page's pixels. */
object ImageCrop {
    private const val MIN_SIDE = 8f

    /** [page] fitted inside [bounds] inset by [reach]. */
    fun pageRect(page: Size, bounds: Size, reach: Float): Rect {
        if (page.width <= 0f || page.height <= 0f) return Rect.Zero
        return fit(page, Rect(reach, reach, bounds.width - reach, bounds.height - reach))
    }

    fun fit(aspect: Size, area: Rect): Rect {
        if (aspect.width <= 0f || aspect.height <= 0f || area.width <= 0f || area.height <= 0f) return Rect.Zero
        val scale = min(area.width / aspect.width, area.height / aspect.height)
        val size = Size(aspect.width * scale, aspect.height * scale)
        return Rect(Offset(area.center.x - size.width / 2, area.center.y - size.height / 2), size)
    }

    fun shown(crop: Rect, page: Size, pageRect: Rect): Rect {
        val scale = pageRect.width / max(page.width, 1f)
        return Rect(pageRect.left + crop.left * scale, pageRect.top + crop.top * scale, pageRect.left + crop.right * scale, pageRect.top + crop.bottom * scale)
    }

    fun edges(point: Offset, shown: Rect, reach: Float): CropEdges? {
        if (shown.isEmpty || !shown.inflate(reach).contains(point)) return null
        val left = abs(point.x - shown.left) < reach && abs(point.x - shown.left) <= abs(point.x - shown.right)
        val right = !left && abs(point.x - shown.right) < reach
        val top = abs(point.y - shown.top) < reach && abs(point.y - shown.top) <= abs(point.y - shown.bottom)
        val bottom = !top && abs(point.y - shown.bottom) < reach
        return if (left || right || top || bottom) CropEdges(left, top, right, bottom) else null
    }

    /** The crop with the held edges at page point ([x], [y]), at least 8 pixels a side. */
    fun resized(crop: Rect, edges: CropEdges, x: Float, y: Float): Rect {
        var left = crop.left
        var top = crop.top
        var right = crop.right
        var bottom = crop.bottom
        if (edges.left) left = min(x, right - MIN_SIDE)
        if (edges.right) right = max(x, left + MIN_SIDE)
        if (edges.top) top = min(y, bottom - MIN_SIDE)
        if (edges.bottom) bottom = max(y, top + MIN_SIDE)
        return Rect(left, top, right, bottom)
    }

    /** The crop moved so its origin is page point ([x], [y]) less [grab], kept on the page. */
    fun moved(crop: Rect, page: Size, grab: Offset, x: Float, y: Float): Rect {
        val origin = Offset(
            (x - grab.x).coerceIn(0f, max(0f, page.width - crop.width)),
            (y - grab.y).coerceIn(0f, max(0f, page.height - crop.height)),
        )
        return Rect(origin, crop.size)
    }

    /** CGRect.integral: the smallest whole-pixel rect holding [rect]. */
    fun integral(rect: Rect) = Rect(floor(rect.left), floor(rect.top), ceil(rect.right), ceil(rect.bottom))
}

/**
 * The page picture under a crop rectangle that is dragged by its edges and corners, or moved from inside.
 * [crop] is in page pixels, top-left origin; [onDrag] follows the finger, [onChange] comes on release.
 */
@Composable
fun ImageCropCanvas(
    preview: ImageBitmap?,
    page: Size,
    crop: Rect,
    onDrag: (Rect) -> Unit,
    onChange: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalDelphiColours.current
    var dragged by remember { mutableStateOf<Rect?>(null) }
    var grabbed by remember { mutableStateOf<CropEdges?>(null) }
    var grab by remember { mutableStateOf<Offset?>(null) }
    val current = dragged ?: crop
    BoxWithConstraints(modifier.clipToBounds()) {
        val bounds = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val density = LocalDensity.current
        val reach = with(density) { ImageCropReach.toPx() }
        val frame = with(density) { CanonMetrics.frame.toPx() }
        val mark = with(density) { 7.dp.toPx() }
        val pageRect = ImageCrop.pageRect(page, bounds, reach)
        val shown = ImageCrop.shown(current, page, pageRect)
        fun pagePoint(point: Offset): Offset {
            val scale = pageRect.width / max(page.width, 1f)
            return Offset(
                ((point.x - pageRect.left) / scale).coerceIn(0f, page.width),
                ((point.y - pageRect.top) / scale).coerceIn(0f, page.height),
            )
        }
        Canvas(
            Modifier
                .matchParentSize()
                .canonGrabPan(
                    grabs = { point ->
                        // whole page keeps scrolling
                        val movable = current.width < page.width - 1 || current.height < page.height - 1
                        preview != null && (ImageCrop.edges(point, shown, reach) != null || (movable && shown.contains(point)))
                    },
                    onStart = { point ->
                        dragged = crop
                        grabbed = ImageCrop.edges(point, shown, reach)
                        if (grabbed == null) {
                            val at = pagePoint(point)
                            grab = Offset(at.x - crop.left, at.y - crop.top)
                        }
                    },
                    onEnd = {
                        val last = dragged
                        dragged = null
                        grabbed = null
                        grab = null
                        if (last != null) onChange(last)
                    },
                ) { change, _ ->
                    val start = dragged ?: return@canonGrabPan
                    val at = pagePoint(change.position)
                    val moving = grab
                    val edges = grabbed
                    val next = when {
                        moving != null -> ImageCrop.moved(start, page, moving, at.x, at.y)
                        edges != null -> ImageCrop.resized(start, edges, at.x, at.y)
                        else -> return@canonGrabPan
                    }
                    dragged = next
                    onDrag(next)
                },
        ) {
            if (preview == null || pageRect.isEmpty) return@Canvas
            drawImage(
                preview,
                dstOffset = IntOffset(pageRect.left.roundToInt(), pageRect.top.roundToInt()),
                dstSize = IntSize(pageRect.width.roundToInt(), pageRect.height.roundToInt()),
            )
            val shade = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(pageRect)
                addRect(shown)
            }
            drawPath(shade, Color.Black.copy(alpha = 0.45f))
            drawRect(c.primary, shown.topLeft, shown.size, style = Stroke(frame))
            for (corner in listOf(shown.topLeft, shown.topRight, shown.bottomLeft, shown.bottomRight)) {
                drawRect(c.primary, Offset(corner.x - mark, corner.y - mark), Size(mark * 2, mark * 2))
            }
        }
        if (preview == null && page != Size.Zero) Box(Modifier.align(Alignment.Center)) { CanonLoader() }
    }
}
