package com.delphi.delphitools.canon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay

enum class CanonButtonKind { Primary, Secondary, Cell, Segment }

// CanonButton.oversized: side and inset are fractions of the height, turn in degrees
private const val MARK_SIDE = 1.5f
private const val MARK_INSET = 0.34f
private const val MARK_DROP = 0.62f
private const val MARK_TURN = -14f
private const val MARK_ALPHA = 0.06f
private const val MARK_ALPHA_FILLED = 0.12f
private const val COPIED_MS = 1500L

/** A grid's buttons: one filling button too narrow for title and symbol turns them all symbol-only. */
internal class CanonSymbolOnly {
    val tight = mutableStateMapOf<Any, Boolean>()
    val active get() = tight.containsValue(true)
}

internal val LocalCanonSymbolOnly = compositionLocalOf<CanonSymbolOnly?> { null }

/**
 * [oversized] draws [icon] large and faint behind the title, turned, cut off by the trailing and bottom edges.
 * [repeats]: holding repeats [onClick]. [flashesCopied]: the title shows [flashTitle] for 1.5 s after a click.
 * [contentDescription] is read by TalkBack when the button shows its symbol alone.
 * [textStyle] replaces the title's size and family; the weight still follows [kind].
 * On a workflow leg a button with the share symbol, or as [followsWorkflow] says, reads Pass along: its export is
 * handed to the next leg.
 */
@Composable
fun CanonButton(
    text: String,
    modifier: Modifier = Modifier,
    kind: CanonButtonKind = CanonButtonKind.Cell,
    selected: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    oversized: Boolean = false,
    repeats: Boolean = false,
    flashesCopied: Boolean = false,
    flashTitle: String = "Copied",
    contentDescription: String? = null,
    textStyle: TextStyle? = null,
    followsWorkflow: Boolean? = null,
    onClick: () -> Unit,
) {
    val c = LocalDelphiColours.current
    val passes = (followsWorkflow ?: (icon == Icons.Outlined.Share)) && CanonHost.capturesFiles()
    CanonPassAlong(passes && enabled, first = kind == CanonButtonKind.Primary, onClick)
    var flashes by remember { mutableIntStateOf(0) }
    LaunchedEffect(flashes) {
        if (flashes == 0) return@LaunchedEffect
        delay(COPIED_MS)
        flashes = 0
    }
    val title = when {
        flashes > 0 -> flashTitle
        passes && text.isNotEmpty() -> "Pass along"
        else -> text
    }
    val symbol = when {
        flashes > 0 -> Icons.Rounded.Check
        passes -> Icons.AutoMirrored.Outlined.ArrowForward
        else -> icon
    }
    val fills = kind == CanonButtonKind.Primary || kind == CanonButtonKind.Secondary
    val filled = kind == CanonButtonKind.Primary ||
        (selected && (kind == CanonButtonKind.Segment || kind == CanonButtonKind.Secondary))
    val minHeight = when (kind) {
        CanonButtonKind.Primary -> CanonMetrics.primaryHeight
        CanonButtonKind.Secondary -> CanonMetrics.secondaryHeight
        else -> CanonMetrics.controlHeight
    }
    val style = (textStyle ?: if (kind == CanonButtonKind.Segment) CanonText.subheadline else CanonText.body)
        .copy(fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Medium)
    val ink = when {
        !filled -> c.foreground
        enabled -> c.primaryForeground
        else -> c.mutedForeground
    }
    val tint = if (enabled || filled) ink else ink.copy(alpha = 0.4f)
    val background = when {
        filled && !enabled -> c.muted
        filled -> c.primary
        else -> c.card
    }

    val group = LocalCanonSymbolOnly.current
    val mark = if (oversized && symbol != null) rememberVectorPainter(symbol) else null
    val showsTitle = title.isNotEmpty() && !(group?.active == true && symbol != null && mark == null)
    var fit: Modifier = Modifier
    if (group != null && fills && text.isNotEmpty() && icon != null && !oversized) {
        val key = remember { Any() }
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        // CanonButton.titleFits: insets, padding and slack are 38 pt
        val needed = remember(text, density) {
            measurer.measure(text, CanonText.bodySemibold).size.width + with(density) { (14 + 38).dp.roundToPx() }
        }
        DisposableEffect(group, key) { onDispose { group.tight.remove(key) } }
        fit = Modifier.onSizeChanged { group.tight[key] = needed > it.width }
    }

    val press = rememberCanonPress()
    val label = if (passes) "Pass along" else contentDescription ?: text
    Box(
        modifier
            .then(if (fills) Modifier.fillMaxWidth() else Modifier)
            .then(fit)
            .heightIn(min = minHeight)
            .canonPress(
                press,
                enabled,
                role = if (kind == CanonButtonKind.Segment) Role.RadioButton else Role.Button,
                repeats = repeats,
            ) {
                onClick()
                if (flashesCopied) flashes++
            }
            .graphicsLayer { alpha = if (press.pressed) 0.75f else 1f }
            .background(background)
            .then(
                if (mark == null) Modifier else Modifier.clipToBounds().drawBehind {
                    val side = size.height * MARK_SIDE
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    val x = if (rtl) side * MARK_INSET else size.width - side * MARK_INSET
                    val y = size.height * MARK_DROP
                    rotate(if (rtl) -MARK_TURN else MARK_TURN, pivot = Offset(x, y)) {
                        translate(x - side / 2, y - side / 2) {
                            with(mark) {
                                draw(
                                    Size(side, side),
                                    alpha = if (filled) MARK_ALPHA_FILLED else MARK_ALPHA,
                                    colorFilter = ColorFilter.tint(ink),
                                )
                            }
                        }
                    }
                }
            )
            .semantics {
                if (!showsTitle && label.isNotEmpty()) this.contentDescription = label
                if (kind == CanonButtonKind.Segment) this.selected = selected
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (symbol != null && mark == null) {
                Icon(symbol, contentDescription = null, tint = tint, modifier = Modifier.size(if (showsTitle) 14.dp else 20.dp))
            }
            if (showsTitle) Text(title, style = style, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Equal cells meeting at hairlines in one row; the action bar. Children size themselves, usually with weight. */
@Composable
fun CanonGrid(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val c = LocalDelphiColours.current
    CompositionLocalProvider(LocalCanonSymbolOnly provides remember { CanonSymbolOnly() }) {
        Row(
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(c.border),
            horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
            content = content,
        )
    }
}

/**
 * Equal cells divided by hairlines, at most [columns] to a row (all in one row when null). Rows are balanced,
 * so four cells at three columns give two rows of two; the last cell of a short row spans the rest.
 * All rows share the tallest cell's height. [boxed] draws the outer hairline; leave it off inside CanonRows.
 */
@Composable
fun CanonGrid(
    columns: Int?,
    modifier: Modifier = Modifier,
    boxed: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = LocalDelphiColours.current
    val policy = remember(columns) { GridPolicy(columns) }
    CompositionLocalProvider(LocalCanonSymbolOnly provides remember { CanonSymbolOnly() }) {
        Layout(
            content,
            modifier
                .fillMaxWidth()
                .background(c.border)
                .padding(if (boxed) CanonMetrics.hairline else 0.dp),
            policy,
        )
    }
}

/** CanonGrid.balanced: row sizes that differ by at most one, larger rows first. */
internal fun canonGridRows(count: Int, perRow: Int): List<Int> {
    if (count <= 0) return emptyList()
    val per = perRow.coerceIn(1, count)
    val rows = (count + per - 1) / per
    return List(rows) { count / rows + if (it < count % rows) 1 else 0 }
}

private class GridPolicy(private val columns: Int?) : MeasurePolicy {
    private fun rows(count: Int) = canonGridRows(count, columns ?: count)

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val rows = rows(measurables.size)
        val widest = rows.maxOrNull() ?: return layout(constraints.minWidth, constraints.minHeight) {}
        val gap = CanonMetrics.hairline.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else widthOf(measurables, max = true)
        val unit = (width - gap * (widest - 1)) / widest
        val widths = IntArray(measurables.size)
        var index = 0
        for (size in rows) {
            for (k in 0 until size) widths[index + k] = if (k < size - 1) unit else width - (size - 1) * (unit + gap)
            index += size
        }
        val gaps = gap * (rows.size - 1)
        val stretched = (constraints.minHeight - gaps) / rows.size
        val rowHeight = maxOf(stretched, measurables.indices.maxOf { measurables[it].minIntrinsicHeight(widths[it]) })
        val placeables = measurables.mapIndexed { i, m -> m.measure(Constraints.fixed(widths[i], rowHeight)) }
        return layout(width, rowHeight * rows.size + gaps) {
            var i = 0
            rows.forEachIndexed { r, size ->
                var x = 0
                repeat(size) {
                    placeables[i].placeRelative(x, r * (rowHeight + gap))
                    x += widths[i] + gap
                    i++
                }
            }
        }
    }

    private fun Density.widthOf(measurables: List<IntrinsicMeasurable>, max: Boolean): Int {
        val widest = rows(measurables.size).maxOrNull() ?: return 0
        val cell = measurables.maxOf {
            if (max) it.maxIntrinsicWidth(Constraints.Infinity) else it.minIntrinsicWidth(Constraints.Infinity)
        }
        return cell * widest + CanonMetrics.hairline.roundToPx() * (widest - 1)
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
        widthOf(measurables, max = false)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
        widthOf(measurables, max = true)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int {
        val rows = rows(measurables.size)
        val widest = rows.maxOrNull() ?: return 0
        val gap = CanonMetrics.hairline.roundToPx()
        val cell = (width - gap * (widest - 1)) / widest
        return measurables.maxOf { it.minIntrinsicHeight(cell) } * rows.size + gap * (rows.size - 1)
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) =
        minIntrinsicHeight(measurables, width)
}

/**
 * One choice of several, as segment cells in a grid. With [icons], each cell shows its symbol and
 * [items] are the labels TalkBack reads. [disabledIndices] greys single choices on top of [enabled].
 */
@Composable
fun CanonSegmented(
    items: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    columns: Int? = null,
    boxed: Boolean = false,
    enabled: Boolean = true,
    disabledIndices: Set<Int> = emptySet(),
    icons: List<ImageVector>? = null,
    onSelect: (Int) -> Unit,
) {
    CanonGrid(columns, modifier.selectableGroup(), boxed) {
        items.forEachIndexed { index, title ->
            CanonButton(
                if (icons == null) title else "",
                kind = CanonButtonKind.Segment,
                selected = index == selected,
                enabled = enabled && index !in disabledIndices,
                icon = icons?.get(index),
                contentDescription = title,
            ) { if (index != selected) onSelect(index) }
        }
    }
}

/** CanonGrid.switches: a titled switch per option; the rows stack together when one is too narrow. */
@Composable
fun CanonSwitchGrid(
    options: List<Pair<String, Boolean>>,
    modifier: Modifier = Modifier,
    columns: Int? = null,
    onChange: (Int, Boolean) -> Unit,
) {
    val longest = options.maxByOrNull { it.first.length }?.first
    CanonGrid(columns, modifier) {
        options.forEachIndexed { index, (title, on) ->
            CanonRow(title, sizingTitle = longest) { CanonSwitch(on) { onChange(index, it) } }
        }
    }
}
