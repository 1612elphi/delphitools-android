package com.delphi.delphitools.shell

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiColours

private val holeRadius = 3.5.dp
private val holePitch = 14.dp
private val holeInset = 9.dp
private val wordDepth = 14.dp
private val legDepth = 20.dp
// from the page edge
private val wordOffset = 21.dp
private val legOffset = 40.dp
private val legSymbol = 17.dp
private val legArrow = 11.dp
private val legSpacing = 6.dp

/** What the ticket prints: [shown] is 0 torn off and 1 printed in full. */
internal class WorkflowTicket(val shown: State<Float>, val word: TextLayoutResult, val legs: List<Painter>, val arrow: Painter)

/** The ticket of the running workflow, or of the last one while it tears off; null with none to draw. */
@Composable
internal fun rememberWorkflowTicket(): WorkflowTicket? {
    val running = WorkflowRun.workflow?.tools
    val last = remember { arrayOf(emptyList<Tool>()) }
    if (running != null) last[0] = running
    val shown = animateFloatAsState(
        if (running != null) 1f else 0f,
        if (running != null) spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow) else tween(400, easing = FastOutLinearInEasing),
        label = "ticket",
    )
    val visible by remember { derivedStateOf { shown.value > 0f } }
    if (!visible) return null
    val measurer = rememberTextMeasurer()
    val word = remember(measurer) {
        measurer.measure("WORKFLOW", CanonText.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp))
    }
    val arrow = rememberVectorPainter(pencil(R.drawable.pencil_arrow_right))
    val legs = last[0].map { rememberVectorPainter(pencil(it.icon)) }
    return remember(word, legs, arrow) { WorkflowTicket(shown, word, legs, arrow) }
}

/**
 * dt-ios WorkflowTicketView: the strip in the accent colour while a workflow runs, punched along the page edge
 * and printed with WORKFLOW over the run's tool symbols, both repeated along the strip. [farInset] is the system
 * bar's depth at the strip's far edge.
 */
internal fun DrawScope.drawTicket(ticket: WorkflowTicket?, edge: TabEdge, strip: Rect, farInset: Float, c: DelphiColours) {
    if (ticket == null || strip.width <= 0f || strip.height <= 0f) return
    val vertical = edge != TabEdge.Bottom
    val across = if (vertical) strip.width else strip.height
    val away = (1f - ticket.shown.value) * across
    fun row(offset: Float, depth: Float) = when (edge) {
        TabEdge.Bottom -> Rect(strip.left, strip.top + offset, strip.right, strip.top + offset + depth)
        TabEdge.Left -> Rect(strip.right - offset - depth, strip.top, strip.right - offset, strip.bottom)
        TabEdge.Right -> Rect(strip.left + offset, strip.top, strip.left + offset + depth, strip.bottom)
    }
    clipRect(strip.left, strip.top, strip.right, strip.bottom) {
        translate(if (edge == TabEdge.Left) -away else if (edge == TabEdge.Right) away else 0f, if (vertical) 0f else away) {
            drawRect(c.primary, strip.topLeft, strip.size)
            val ink = c.primaryForeground
            tiles(row(wordOffset.toPx(), wordDepth.toPx()), vertical, ticket.word.size.width + 22.dp.toPx()) {
                drawText(ticket.word, ink, Offset(0f, (wordDepth.toPx() - ticket.word.size.height) / 2), alpha = 0.5f)
            }
            if (ticket.legs.isNotEmpty()) {
                val symbols = ticket.legs.flatMapIndexed { index, leg ->
                    if (index == 0) listOf(leg to legSymbol.toPx()) else listOf(ticket.arrow to legArrow.toPx(), leg to legSymbol.toPx())
                }
                val width = symbols.sumOf { it.second.toDouble() }.toFloat() + legSpacing.toPx() * (symbols.size - 1)
                // clear of bar buttons
                val legs = row(maxOf(legOffset.toPx(), across - farInset - legDepth.toPx() - 4.dp.toPx()), legDepth.toPx())
                tiles(legs, vertical, width + 30.dp.toPx()) {
                    var x = 0f
                    for ((painter, side) in symbols) {
                        translate(x, (legDepth.toPx() - side) / 2) {
                            with(painter) { draw(Size(side, side), alpha = 0.6f, colorFilter = ColorFilter.tint(ink)) }
                        }
                        x += side + legSpacing.toPx()
                    }
                }
            }
            val holes = row(holeInset.toPx(), 0f)
            var along = holePitch.toPx() / 2
            val length = if (vertical) strip.height else strip.width
            while (along < length) {
                drawCircle(c.background, holeRadius.toPx(), if (vertical) Offset(holes.left, strip.top + along) else Offset(strip.left + along, holes.top))
                along += holePitch.toPx()
            }
        }
    }
}

/** Repeats [draw] every [pitch] along [row]; a vertical row gets it turned a quarter clockwise. */
private fun DrawScope.tiles(row: Rect, vertical: Boolean, pitch: Float, draw: DrawScope.() -> Unit) {
    val length = if (vertical) row.height else row.width
    withTransform({
        clipRect(row.left, row.top, row.right, row.bottom)
        if (vertical) {
            translate(row.right, row.top)
            rotate(90f, Offset.Zero)
        } else {
            translate(row.left, row.top)
        }
    }) {
        var x = 0f
        while (x < length) {
            translate(x, 0f, draw)
            x += pitch
        }
    }
}

/** dt-ios WorkflowPassButton: Pass along for a tool whose export is not one share button; [pass] exports a file. */
@Composable
fun WorkflowPassButton(pass: () -> Unit) {
    if (WorkflowRun.capturesFiles) PassButton(enabled = true, followsWorkflow = true, pass)
}

/** A colour tool publishes [colours] (hex) to the run; the section is its Pass along button, on a colour leg. */
@Composable
fun workflowColours(colours: () -> List<String>): List<CanonSection> {
    val run = WorkflowRun
    if (!run.isOnStep) return emptyList()
    // composition read subscribes page
    val hexes = colours()
    SideEffect { run.colours = hexes }
    if (run.capturesFiles) return emptyList()
    return listOf(CanonSection.of { PassButton(run.hasColour, followsWorkflow = false, run::advance) })
}

@Composable
private fun PassButton(enabled: Boolean, followsWorkflow: Boolean, pass: () -> Unit) {
    CanonButton(
        "Pass along",
        kind = CanonButtonKind.Primary,
        enabled = enabled,
        icon = pencil(R.drawable.pencil_arrow_right),
        oversized = true,
        followsWorkflow = followsWorkflow,
        onClick = pass,
    )
}
