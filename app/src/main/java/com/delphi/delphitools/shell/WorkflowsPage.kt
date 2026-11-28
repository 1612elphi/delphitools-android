package com.delphi.delphitools.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonNameDialog
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonPage
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.MenuCellItem
import com.delphi.delphitools.canon.MenuCellPopup
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours

// existing ios strings
private val ordinals = listOf("First,", "Then...", "Finally,")

/** dt-ios WorkflowsViewController: a pass to build a custom chain, the chains the person saved, and the presets. */
@Composable
internal fun WorkflowsPage() {
    val stores = LocalShellStores.current
    val run = WorkflowRun
    var picks by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val custom = WorkflowCatalog.custom(picks)
    var naming by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Workflow?>(null) }

    CanonPage {
        Pass(picks, custom != null, onPick = { picks = it }, onSave = { naming = true }) { custom?.let(run::start) }
        run.workflow?.let { current ->
            CanonRows {
                CanonHeading("Current")
                WorkflowRow(current, step = run.step) { run.goTo(run.step) }
                CanonButton("Exit", icon = pencil(R.drawable.pencil_x), onClick = run::exit)
            }
        }
        if (stores.savedWorkflows.isNotEmpty()) {
            CanonRows {
                CanonHeading("Saved")
                stores.savedWorkflows.forEach { saved ->
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        WorkflowRow(saved, Modifier.longPress("Rename") { menu = true }) { run.start(saved) }
                        MenuCellPopup(menu, { menu = false }) {
                            MenuCellItem("Rename", icon = pencil(R.drawable.pencil_pencil)) {
                                menu = false
                                renaming = saved
                            }
                            MenuCellItem("Delete", icon = pencil(R.drawable.pencil_trash_2)) {
                                menu = false
                                stores.removeWorkflow(saved)
                            }
                        }
                    }
                }
            }
        }
        CanonRows {
            CanonHeading("Workflows")
            WorkflowCatalog.presets.forEach { preset -> WorkflowRow(preset) { run.start(preset) } }
        }
    }

    if (naming && custom != null) {
        CanonNameDialog("Save workflow", custom.name, "Save", onDismiss = { naming = false }) { stores.saveWorkflow(custom, it.ifEmpty { custom.name }) }
    }
    renaming?.let { workflow ->
        CanonNameDialog("Rename", workflow.name, "Rename", onDismiss = { renaming = null }) { if (it.isNotEmpty()) stores.renameWorkflow(workflow, it) }
    }
}

@Composable
private fun Pass(picks: List<String>, valid: Boolean, onPick: (List<String>) -> Unit, onSave: () -> Unit, onStart: () -> Unit) {
    val c = LocalDelphiColours.current
    Box {
        CanonRows(Modifier.padding(top = 64.dp)) {
            Row(
                Modifier.fillMaxWidth().background(c.primary).padding(horizontal = CanonMetrics.panelPadding, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(pencil(R.drawable.pencil_workflow), contentDescription = null, tint = c.primaryForeground, modifier = Modifier.size(16.dp))
                Text("DELPHITOOLS WORKFLOW:", style = CanonText.captionSemibold.copy(letterSpacing = 1.4.sp), color = c.primaryForeground)
            }
            repeat(WorkflowCatalog.SLOTS) { index ->
                CanonRow(ordinals[index], flush = true, sizingTitle = ordinals.maxBy { it.length }) {
                    Slot(index, picks, Modifier.fillMaxSize().widthIn(min = 200.dp), onPick)
                }
            }
            Perforation()
            Barcode()
            CanonGrid {
                CanonButton("Save", Modifier.weight(1f).fillMaxHeight(), enabled = valid, icon = pencil(R.drawable.pencil_bookmark), onClick = onSave)
                CanonButton("Start", Modifier.weight(1f).fillMaxHeight(), kind = CanonButtonKind.Primary, enabled = valid, icon = pencil(R.drawable.pencil_arrow_right), onClick = onStart)
            }
        }
        Image(
            painterResource(R.drawable.workflow_delphi_alien),
            contentDescription = null,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 20.dp).offset(y = (-2).dp).height(112.dp),
        )
    }
}

/** One leg of the pass: its tool, and a menu of the tools that can stand there. */
@Composable
private fun Slot(index: Int, picks: List<String>, modifier: Modifier, onPick: (List<String>) -> Unit) {
    val c = LocalDelphiColours.current
    val tool = picks.getOrNull(index)?.let(::toolWithId)
    val options = when {
        index == 0 -> WorkflowCatalog.startTools
        picks.size >= index -> toolWithId(picks[index - 1])?.let(WorkflowCatalog::nextTools).orEmpty()
        else -> emptyList()
    }
    val enabled = options.isNotEmpty()
    var open by remember { mutableStateOf(false) }
    Box(modifier, propagateMinConstraints = true) {
        Row(
            Modifier
                .heightIn(min = CanonMetrics.controlHeight)
                .canonClickable(enabled, role = Role.DropdownList) { open = true }
                .background(c.card)
                .semantics {
                    contentDescription = ordinals[index]
                    tool?.let { stateDescription = it.name }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            val ink = if (enabled) c.foreground else c.foreground.copy(alpha = 0.4f)
            Text(tool?.name ?: "—", style = CanonText.bodyMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(pencil(R.drawable.pencil_chevrons_up_down), contentDescription = null, tint = ink, modifier = Modifier.size(14.dp))
        }
        MenuCellPopup(open, { open = false }) {
            if (index >= 2 && tool != null) {
                MenuCellItem("None", icon = pencil(R.drawable.pencil_x)) {
                    open = false
                    onPick(picks.take(index))
                }
            }
            LocalShellStores.current.categoryOrder.forEach { category ->
                val inCategory = options.filter { it.category == category }
                if (inCategory.isEmpty()) return@forEach
                Text(
                    category.title.uppercase(),
                    style = CanonText.captionSemibold.copy(letterSpacing = 1.4.sp),
                    color = c.mutedForeground,
                    modifier = Modifier.padding(start = CanonMetrics.panelPadding, top = 12.dp, bottom = 4.dp),
                )
                inCategory.forEach { option ->
                    MenuCellItem(option.name, checked = option.id == tool?.id, icon = pencil(option.icon)) {
                        open = false
                        onPick(picks.take(index) + option.id)
                    }
                }
            }
        }
    }
}

/** dt-ios WorkflowRowView: name and category, then the tools' symbols joined by arrows. The whole row starts it. */
@Composable
private fun WorkflowRow(workflow: Workflow, modifier: Modifier = Modifier, step: Int? = null, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    val chain = workflow.tools
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CanonMetrics.rowHeight)
            .canonClickable(onClick = onClick)
            .then(modifier)
            .background(c.card)
            .semantics(mergeDescendants = true) { stateDescription = chain.joinToString(", ") { it.name } }
            .padding(horizontal = CanonMetrics.panelPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(workflow.name, style = CanonText.bodySemibold, color = c.foreground)
            workflow.category?.let { Text(it.title, style = CanonText.caption, color = c.mutedForeground) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chain.forEachIndexed { index, tool ->
                if (index > 0) Icon(pencil(R.drawable.pencil_arrow_right), contentDescription = null, tint = c.mutedForeground, modifier = Modifier.size(11.dp))
                val reached = step == null || index <= step
                Icon(pencil(tool.icon), contentDescription = null, tint = if (reached) c.primary else c.mutedForeground, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Tear line between the pass and its stub: dashes, with a notch punched at each end. */
@Composable
private fun Perforation() {
    val c = LocalDelphiColours.current
    Canvas(Modifier.fillMaxWidth().height(18.dp).background(c.card)) {
        val middle = size.height / 2
        drawLine(
            c.border,
            Offset(14.dp.toPx(), middle),
            Offset(size.width - 14.dp.toPx(), middle),
            1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )
        drawCircle(c.background, 8.dp.toPx(), Offset(0f, middle))
        drawCircle(c.background, 8.dp.toPx(), Offset(size.width, middle))
    }
}

// glyphs from web workflow.gts
private val barcodeGlyphs = mapOf(
    'D' to listOf("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
    'E' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
    'L' to listOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
    'P' to listOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
    'H' to listOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
    'I' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "11111"),
)

/** One bar per vertical run of lit cells, in glyph units: x, y, height. */
internal val barcodeBars: List<Triple<Float, Float, Float>> = "DELPHI".flatMapIndexed { letter, character ->
    val rows = barcodeGlyphs.getValue(character)
    buildList {
        for (column in 0 until 5) {
            var start = -1
            for (row in 0..rows.size) {
                val lit = row < rows.size && rows[row][column] == '1'
                if (lit && start < 0) start = row
                if (!lit && start >= 0) {
                    add(Triple(letter * 6f + column, start.toFloat(), row - start - 0.15f))
                    start = -1
                }
            }
        }
    }
}

/** The DELPHI barcode from the web pass, stretched across the stub. */
@Composable
private fun Barcode() {
    val c = LocalDelphiColours.current
    Canvas(Modifier.fillMaxWidth().height(40.dp).background(c.card).padding(horizontal = CanonMetrics.panelPadding, vertical = 10.dp)) {
        val unitX = size.width / (6 * 6 - 1)
        val unitY = size.height / 7
        for ((x, y, height) in barcodeBars) drawRect(c.foreground, Offset(x * unitX, y * unitY), Size(0.7f * unitX, height * unitY))
    }
}
