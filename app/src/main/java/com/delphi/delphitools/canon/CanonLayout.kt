package com.delphi.delphitools.canon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours

/** A block of rows separated by hairlines; framed adds the 2 pt border round it. */
@Composable
fun CanonRows(
    modifier: Modifier = Modifier,
    framed: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalDelphiColours.current
    Column(
        modifier
            .fillMaxWidth()
            .background(c.border)
            .padding(if (framed) CanonMetrics.frame else 0.dp),
        verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
        content = content,
    )
}

@Composable
fun CanonRule(modifier: Modifier = Modifier, vertical: Boolean = false) {
    val c = LocalDelphiColours.current
    val line = if (vertical) Modifier.fillMaxHeight().width(CanonMetrics.hairline) else Modifier.fillMaxWidth().height(CanonMetrics.hairline)
    Box(modifier.then(line).background(c.border))
}

@Composable
fun CanonHeading(text: String, modifier: Modifier = Modifier, background: Color? = null) {
    val c = LocalDelphiColours.current
    Text(
        text,
        style = CanonText.footnoteSemibold,
        color = c.mutedForeground,
        modifier = modifier
            .fillMaxWidth()
            .background(background ?: c.muted)
            .semantics { heading() }
            .padding(horizontal = CanonMetrics.panelPadding, vertical = 8.dp),
    )
}

enum class CanonTone { Ok, Warn, Muted }

@Composable
fun CanonStatus(text: String, tone: CanonTone, modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    val colour = when (tone) {
        CanonTone.Ok -> c.primary
        CanonTone.Warn -> c.destructive
        CanonTone.Muted -> c.mutedForeground
    }
    Text(text, style = CanonText.caption, color = colour, modifier = modifier)
}

/** CanonStatus.row: the status as a padded card row. */
@Composable
fun CanonStatusRow(text: String, tone: CanonTone, modifier: Modifier = Modifier) {
    CanonStatus(
        text,
        tone,
        modifier
            .fillMaxWidth()
            .background(LocalDelphiColours.current.card)
            .padding(horizontal = CanonMetrics.panelPadding, vertical = 10.dp),
    )
}

@Composable
fun CanonEmptyRow(text: String, modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .background(c.card)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = CanonText.caption, color = c.mutedForeground, textAlign = TextAlign.Center)
    }
}

/** The row title, taken by a control that reads it to TalkBack; the title text is then hidden from TalkBack. */
internal class CanonRowLabel(val text: String) {
    var claimed by mutableStateOf(false)
}

internal val LocalCanonRowLabel = compositionLocalOf<CanonRowLabel?> { null }

/** For a control that is one TalkBack element: the enclosing row's title as its label. */
@Composable
internal fun claimRowLabel(): String? {
    val label = LocalCanonRowLabel.current ?: return null
    SideEffect { label.claimed = true }
    return label.text
}

/**
 * Label beside a control. Plain: one card, control at its own width.
 * Flush: label and control are separate cells; the control cell fills the row's height.
 * Stacked, or a row too narrow for label and control: the control goes below the label.
 * [sizingTitle] is the longest title among sibling rows, so a grid of rows stacks together.
 */
@Composable
fun CanonRow(
    label: String,
    modifier: Modifier = Modifier,
    flush: Boolean = false,
    controlWeight: Float? = null,
    detail: String? = null,
    icon: ImageVector? = null,
    stacked: Boolean = false,
    sizingTitle: String? = null,
    control: (@Composable () -> Unit)? = null,
) {
    val c = LocalDelphiColours.current
    val plain = !flush && !stacked
    val rowLabel = remember(label) { CanonRowLabel(label) }
    val measurer = rememberTextMeasurer()
    val sizing = remember(sizingTitle) { sizingTitle?.let { measurer.measure(it, CanonText.body).size.width } ?: 0 }
    val shownIcon = icon.takeIf { plain }
    Layout(
        content = {
            Row(
                Modifier
                    .background(c.card)
                    .padding(start = CanonMetrics.panelPadding, top = 10.dp, end = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (shownIcon != null) Icon(shownIcon, null, tint = c.mutedForeground, modifier = Modifier.size(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        label,
                        style = CanonText.body,
                        color = c.foreground,
                        modifier = if (rowLabel.claimed) Modifier.clearAndSetSemantics {} else Modifier,
                    )
                    if (detail != null) Text(detail, style = CanonText.caption, color = c.mutedForeground)
                }
            }
            if (control != null) {
                Box(Modifier.background(c.card), contentAlignment = Alignment.Center, propagateMinConstraints = !plain) {
                    CompositionLocalProvider(LocalCanonRowLabel provides rowLabel, content = control)
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .background(if (plain) c.card else c.border),
    ) { measurables, constraints ->
        val text = measurables[0]
        val ctl = measurables.getOrNull(1)
        val pad = CanonMetrics.panelPadding.roundToPx()
        val hair = CanonMetrics.hairline.roundToPx()
        val minHeight = CanonMetrics.rowHeight.roundToPx()
        val textWidth = maxOf(
            text.maxIntrinsicWidth(Constraints.Infinity),
            sizing + (CanonMetrics.panelPadding + 12.dp + if (shownIcon != null) 32.dp else 0.dp).roundToPx(),
        )
        val ctlWidth = ctl?.minIntrinsicWidth(Constraints.Infinity) ?: 0
        val needed = textWidth + ctlWidth + if (plain) pad else hair
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else needed
        if (ctl == null) {
            val cell = text.measure(Constraints.fixed(width, maxOf(minHeight, text.minIntrinsicHeight(width))))
            return@Layout layout(width, cell.height) { cell.placeRelative(0, 0) }
        }
        val below = stacked || needed > width
        when {
            below -> {
                val cellHeight = text.minIntrinsicHeight(width)
                val cell = text.measure(Constraints.fixed(width, cellHeight))
                val inset = if (plain) pad else 0
                val placed = ctl.measure(Constraints(minWidth = if (plain) 0 else width, maxWidth = width - inset * 2))
                val top = cellHeight + if (plain) 0 else hair
                val height = maxOf(minHeight, top + placed.height + if (plain) 10.dp.roundToPx() else 0)
                layout(width, height) {
                    cell.placeRelative(0, 0)
                    placed.placeRelative(inset, top)
                }
            }
            plain -> {
                val placed = ctl.measure(Constraints(maxWidth = width - pad - textWidth))
                val cellWidth = width - pad - placed.width
                val height = maxOf(minHeight, text.minIntrinsicHeight(cellWidth), placed.height + 12.dp.roundToPx())
                val cell = text.measure(Constraints.fixed(cellWidth, height))
                layout(width, height) {
                    cell.placeRelative(0, 0)
                    placed.placeRelative(cellWidth, (height - placed.height) / 2)
                }
            }
            else -> {
                val share = controlWeight?.let { ((width - hair) * it / (1 + it)).toInt() }
                val ctlCell = share ?: minOf(ctl.maxIntrinsicWidth(Constraints.Infinity), width - hair - textWidth)
                val cellWidth = width - hair - ctlCell
                val height = maxOf(minHeight, text.minIntrinsicHeight(cellWidth), ctl.minIntrinsicHeight(ctlCell))
                val cell = text.measure(Constraints.fixed(cellWidth, height))
                val placed = ctl.measure(Constraints.fixed(ctlCell, height))
                layout(width, height) {
                    cell.placeRelative(0, 0)
                    placed.placeRelative(cellWidth + hair, 0)
                }
            }
        }
    }
}

private object SpansColumn : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?) = SpansColumn
}

/** A CanonPage section that spans the page edge to edge, with no panel padding (a canvas band). */
fun Modifier.canonSpansColumn(): Modifier = this.then(SpansColumn)

/**
 * Scrolling tool body. Sections are centred, capped at [maxWidth] when set, and inset by the panel padding;
 * a [canonSpansColumn] section spans the full width, and as the first section it reaches the top edge.
 */
@Composable
fun CanonPage(
    modifier: Modifier = Modifier,
    maxWidth: Dp? = 720.dp,
    content: @Composable () -> Unit,
) {
    Layout(
        content,
        modifier
            .fillMaxSize()
            .background(LocalDelphiColours.current.background)
            .verticalScroll(rememberScrollState()),
    ) { measurables, constraints ->
        val pad = CanonMetrics.panelPadding.roundToPx()
        val width = constraints.maxWidth
        val inner = minOf(width - pad * 2, maxWidth?.roundToPx() ?: Int.MAX_VALUE).coerceAtLeast(0)
        val spans = measurables.map { it.parentData === SpansColumn }
        val placeables = measurables.mapIndexed { i, m ->
            m.measure(Constraints.fixedWidth(if (spans[i]) width else inner))
        }
        val top = if (spans.firstOrNull() == true) 0 else pad
        val height = top + placeables.sumOf { it.height } + pad * maxOf(placeables.size - 1, 0) + pad
        layout(width, height) {
            var y = top
            placeables.forEachIndexed { i, p ->
                p.placeRelative(if (spans[i]) 0 else (width - inner) / 2, y)
                y += p.height + pad
            }
        }
    }
}
