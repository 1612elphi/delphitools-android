package com.delphi.delphitools.canon

import android.content.ClipData
import android.content.ClipDescription
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.window.Popup
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlin.math.roundToInt

/**
 * A page in a CanonPageStrip, in PDF points. `area` (the part shown) and `placement` (where `picture` is drawn)
 * default to the whole page and have their origin at the bottom left; `turn` is clockwise degrees for the picture;
 * `depth` stacks up to three sheets behind a page that stands for several.
 */
@Immutable
data class CanonPageStripPage(
    val size: Size,
    val picture: ImageBitmap? = null,
    val name: String? = null,
    val area: Rect? = null,
    val placement: Rect? = null,
    val depth: Int = 0,
    val turn: Int = 0,
)

val CanonPageStripHeight = 196.dp

private val caption = 34.dp
private val sheetOffset = 4.dp
private val stripGap = 14.dp
private val paper = Color.White

/**
 * Pages to scale in a sideways row, the number and name under each. With `reorders` a long press and drag moves a page
 * (`onMove` once it is let go) and pages from other apps of `dropTypes` drop in (`onDrop` with the insertion index);
 * a long press without a drag opens `menu`; a tap calls `onSelect`, and `selected` lights a page.
 */
@Composable
fun CanonPageStrip(
    pages: List<CanonPageStripPage>,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    onSelect: ((Int) -> Unit)? = null,
    reorders: Boolean = false,
    onMove: ((from: Int, to: Int) -> Unit)? = null,
    dropTypes: List<String> = listOf("image/*"),
    onDrop: ((index: Int, clip: ClipData) -> Unit)? = null,
    menu: ((Int) -> List<Pair<String, () -> Unit>>)? = null,
    empty: String = "Pages",
) {
    val c = LocalDelphiColours.current
    val activity = LocalActivity.current
    val list = rememberLazyListState()
    var order by remember { mutableStateOf<List<Int>?>(null) }
    var dragged by remember { mutableStateOf<Int?>(null) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var menuFor by remember { mutableStateOf<Int?>(null) }
    val menuBack = remember { Animatable(0f) }
    LayoutBackDismiss(menuFor != null, menuBack) {
        menuFor = null
        menuBack.snapTo(0f)
    }
    var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val drop by rememberUpdatedState(onDrop)
    val count by rememberUpdatedState(pages.size)
    val dropping = reorders && onDrop != null
    val types by rememberUpdatedState(dropTypes)
    val select by rememberUpdatedState(onSelect)
    val move by rememberUpdatedState(onMove)

    // ponytail: no auto-scroll while a dragged page is held at the strip's edge
    fun insertionAt(x: Float): Int {
        val items = list.layoutInfo.visibleItemsInfo
        return items.firstOrNull { x < it.offset + it.size / 2f }?.index ?: count
    }

    val target = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val android = event.toAndroidDragEvent()
                activity?.requestDragAndDropPermissions(android)
                val x = android.x - (root?.positionInRoot()?.x ?: 0f)
                drop?.invoke(insertionAt(x), android.clipData ?: return false)
                return true
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(c.muted)
            .onGloballyPositioned { root = it }
            .then(
                if (dropping) Modifier.dragAndDropTarget(
                    shouldStartDragAndDrop = { event -> event.mimeTypes().any { m -> types.any { ClipDescription.compareMimeTypes(m, it) } } },
                    target = target,
                ) else Modifier,
            )
            .padding(top = LocalCanonCanvas.current.top),
    ) {
        if (pages.isEmpty()) {
            Text(
                empty,
                style = CanonText.subheadline.copy(fontWeight = CanonText.bodySemibold.fontWeight),
                color = c.mutedForeground,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
            return@BoxWithConstraints
        }
        val pageHeight = maxOf(20.dp, maxHeight - 22.dp - caption)
        val widest = maxWidth * 0.7f
        val shown = order ?: pages.indices.toList()
        LazyRow(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(start = CanonMetrics.panelPadding, top = 14.dp, end = CanonMetrics.panelPadding, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(stripGap),
        ) {
            itemsIndexed(shown, key = { _, original -> original }) { position, original ->
                val page = pages[original]
                val width = maxOf(56.dp, minOf(widest, pageHeight * (page.size.width / maxOf(1f, page.size.height))))
                val isDragged = dragged == original
                val actions = menu?.invoke(original).orEmpty()
                val stripSpacing = stripGap
                Box(
                    Modifier
                        .then(if (isDragged) Modifier else Modifier.animateItem())
                        .graphicsLayer {
                            if (isDragged) {
                                translationX = dragX
                                scaleX = 1.04f
                                scaleY = 1.04f
                            }
                        }
                        .semantics(mergeDescendants = true) {
                            contentDescription = listOfNotNull("${position + 1}", page.name).joinToString(", ")
                            this.selected = selected == original
                            if (onSelect != null) onClick { onSelect(original); true }
                            customActions = actions.map { (title, act) -> CustomAccessibilityAction(title) { act(); true } }
                        }
                        .pointerInput(original, reorders, actions.isNotEmpty()) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                var up: Boolean? = null
                                val pressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    up = waitForUpOrCancellation() != null
                                }
                                if (pressed != null) {
                                    if (up == true) select?.invoke(original)
                                    return@awaitEachGesture
                                }
                                var moved = false
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }
                                    if (reorders && !moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                        moved = true
                                        order = List(count) { it }
                                        dragged = original
                                        dragX = 0f
                                    }
                                    if (moved) {
                                        dragX += change.positionChange().x
                                        val now = order ?: break
                                        val items = list.layoutInfo.visibleItemsInfo
                                        val at = items.firstOrNull { it.key == original }
                                        val index = now.indexOf(original)
                                        if (at != null) {
                                            val centre = at.offset + at.size / 2f + dragX
                                            val next = items.firstOrNull { it.index == index + 1 }
                                            val previous = items.firstOrNull { it.index == index - 1 }
                                            val spacing = stripSpacing.toPx()
                                            if (next != null && centre > next.offset + next.size / 2f) {
                                                order = now.toMutableList().apply { add(index + 1, removeAt(index)) }
                                                dragX -= next.size + spacing
                                            } else if (previous != null && centre < previous.offset + previous.size / 2f) {
                                                order = now.toMutableList().apply { add(index - 1, removeAt(index)) }
                                                dragX += previous.size + spacing
                                            }
                                        }
                                    }
                                    change.consume()
                                }
                                if (moved) {
                                    val to = order?.indexOf(original) ?: original
                                    order = null
                                    dragged = null
                                    dragX = 0f
                                    if (to != original) move?.invoke(original, to)
                                } else if (actions.isNotEmpty()) {
                                    menuFor = original
                                }
                            }
                        },
                ) {
                    PageCell(page, position + 1, selected == original, width, pageHeight)
                    if (menuFor == original) {
                        Popup(alignment = Alignment.TopCenter, onDismissRequest = { menuFor = null }) {
                            CanonRows(
                                Modifier
                                    .width(200.dp)
                                    .graphicsLayer {
                                        alpha = 1f - menuBack.value * 0.6f
                                        scaleX = 1f - menuBack.value * 0.1f
                                        scaleY = 1f - menuBack.value * 0.1f
                                    },
                            ) {
                                actions.forEach { (title, act) ->
                                    CanonButton(title, Modifier.fillMaxWidth()) {
                                        menuFor = null
                                        act()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageCell(page: CanonPageStripPage, number: Int, lit: Boolean, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    val c = LocalDelphiColours.current
    val depth = page.depth.coerceIn(0, 3)
    val room = sheetOffset * depth
    val scale = minOf((width - room) / maxOf(1f, page.size.width), (height - room) / maxOf(1f, page.size.height))
    val fittedW = scale * page.size.width
    val fittedH = scale * page.size.height
    val x = (width - fittedW - room) / 2
    val y = height - fittedH
    Box(Modifier.size(width, height + caption)) {
        for (index in depth - 1 downTo 0) {
            Box(
                Modifier
                    .offset(x + sheetOffset * (index + 1), y - sheetOffset * (index + 1))
                    .size(fittedW, fittedH)
                    .background(paper)
                    .border(CanonMetrics.hairline, c.border),
            )
        }
        PaperView(
            page,
            lit,
            Modifier
                .offset(x, y)
                .size(fittedW, fittedH)
                .dropShadow(RectangleShape, Shadow(radius = 3.dp, color = Color.Black, offset = DpOffset(0.dp, 1.dp), alpha = 0.12f)),
        )
        Text(
            "$number",
            style = LayoutText.mono,
            color = if (lit) c.primary else c.foreground,
            textAlign = if (page.name == null) TextAlign.Center else TextAlign.Start,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().offset(y = height + 4.dp),
        )
        if (page.name != null) {
            Text(
                page.name,
                style = CanonText.caption,
                color = c.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.fillMaxWidth().offset(y = height + 18.dp),
            )
        }
    }
}

@Composable
private fun PaperView(page: CanonPageStripPage, lit: Boolean, modifier: Modifier) {
    val c = LocalDelphiColours.current
    Canvas(modifier.background(paper)) {
        if (page.size.width <= 0f) return@Canvas
        val scale = size.width / page.size.width
        // pdf origin: bottom left
        fun flip(r: Rect) = Rect(r.left * scale, (page.size.height - r.bottom) * scale, r.right * scale, (page.size.height - r.top) * scale)
        val whole = Rect(Offset.Zero, page.size)
        val area = flip(page.area ?: whole)
        page.picture?.let { picture ->
            val into = flip(page.placement ?: whole)
            val quarter = ((page.turn / 90) % 4 + 4) % 4
            // a quarter turn draws the picture into the rect with its sides swapped, then turns it
            val drawn = if (quarter % 2 == 1) Rect(into.center - Offset(into.height / 2, into.width / 2), Size(into.height, into.width)) else into
            clipRect(area.left, area.top, area.right, area.bottom) {
                rotate(quarter * 90f, into.center) {
                    drawImage(picture, dstOffset = drawn.topLeft.round(), dstSize = IntSize(drawn.width.roundToInt(), drawn.height.roundToInt()))
                }
            }
        }
        if (page.area != null && page.area != whole) {
            drawRect(
                c.primary.copy(alpha = 0.4f), area.topLeft, area.size,
                style = Stroke(CanonMetrics.hairline.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
            )
        }
        val edge = (if (lit) CanonMetrics.frame else CanonMetrics.hairline).toPx()
        drawRect(if (lit) c.primary else c.border, Offset(edge / 2, edge / 2), Size(size.width - edge, size.height - edge), style = Stroke(edge))
    }
}
