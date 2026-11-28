package com.delphi.delphitools.tools.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonDecimalField
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import com.delphi.delphitools.tools.print.PaperPick
import com.delphi.delphitools.tools.print.PaperQuery
import com.delphi.delphitools.tools.print.PaperSizeDatabase
import com.delphi.delphitools.tools.print.PaperUnit
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay

/** dt-ios PaperSizePickerViewController.Choice. */
sealed interface PaperChoice {
    /** The caller's own choice. */
    data object Extra : PaperChoice
    data class Paper(val pick: PaperPick) : PaperChoice
    data class Custom(val width: Double, val height: Double) : PaperChoice

    /** Null for the extra. */
    val millimetres: PdfSize?
        get() = when (this) {
            Extra -> null
            is Paper -> PdfSize(pick.size.widthMM, pick.size.heightMM)
            is Custom -> PdfSize(width, height)
        }

    fun title(extra: String?): String = when (this) {
        Extra -> extra ?: ""
        is Paper -> "${pick.size.name} · ${pick.size.mmString}"
        is Custom -> "Custom · ${PaperUnit.decimal(width)} × ${PaperUnit.decimal(height)} mm"
    }
}

private val customRange = 10.0..5000.0

/** dt-ios PaperSizePickerViewController: find a paper size, take the caller's extra, or type a custom one. */
@Composable
fun PaperSizePicker(extra: String?, selected: PaperChoice?, onDismiss: () -> Unit, onPick: (PaperChoice) -> Unit) {
    val c = LocalDelphiColours.current
    val start = selected?.millimetres ?: PdfSize(210.0, 297.0)
    var findText by remember { mutableStateOf("") }
    var query by remember { mutableStateOf<PaperQuery>(PaperQuery.None) }
    var width by remember { mutableDoubleStateOf(start.width) }
    var height by remember { mutableDoubleStateOf(start.height) }
    LaunchedEffect(findText) {
        delay(TYPING_DELAY_MS)
        query = PaperQuery.parse(findText)
    }
    val choose = { choice: PaperChoice ->
        onPick(choice)
        onDismiss()
    }
    val chosenKey = (selected as? PaperChoice.Paper)?.pick?.key
    val picks = (query as? PaperQuery.Dimensions)?.let { q -> PaperQuery.closest(q.width, q.height).map { it.pick } }
    val series = PaperSizeDatabase.allSeries.map { data ->
        data.name to data.sizes.map { PaperPick(data.id, it) }.filter(query::matches)
    }.filter { it.second.isNotEmpty() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .background(c.background)
                .systemBarsPadding()
                .padding(horizontal = CanonMetrics.panelPadding, vertical = 16.dp),
        ) {
            item {
                CanonRows(Modifier.padding(bottom = 16.dp)) {
                    CanonHeading("Page size")
                    CanonTextCell(findText, { findText = it }, Modifier.fillMaxWidth(), placeholder = "A4 · 210×297 · 8.5x11in")
                }
            }
            if (extra != null) {
                item {
                    CanonRows(Modifier.padding(bottom = 16.dp)) {
                        CanonGrid {
                            CanonButton(extra, Modifier.weight(1f).fillMaxHeight(), kind = CanonButtonKind.Segment, selected = selected == PaperChoice.Extra) {
                                choose(PaperChoice.Extra)
                            }
                        }
                    }
                }
            }
            item {
                CanonRows(Modifier.padding(bottom = 16.dp)) {
                    CanonHeading("Custom")
                    CanonRow("Width", flush = true) { CanonDecimalField(width, { width = it }, unit = "mm", step = 1.0, decimals = 1, range = customRange) }
                    CanonRow("Height", flush = true) { CanonDecimalField(height, { height = it }, unit = "mm", step = 1.0, decimals = 1, range = customRange) }
                    CanonGrid {
                        CanonButton("Use", Modifier.weight(1f).fillMaxHeight(), kind = CanonButtonKind.Primary, icon = pencil(R.drawable.pencil_check)) {
                            choose(PaperChoice.Custom(width, height))
                        }
                    }
                }
            }
            val groups = if (picks != null) listOf("Closest" to picks) else series
            for ((name, list) in groups) {
                item(key = "heading-$name") { CanonHeading(name) }
                items(list, key = { "$name/${it.key}" }) { pick ->
                    CanonRow(
                        pick.size.name,
                        Modifier.padding(top = CanonMetrics.hairline).canonClickable { choose(PaperChoice.Paper(pick)) },
                        detail = pick.size.mmString,
                    ) {
                        if (pick.key == chosenKey) Icon(pencil(R.drawable.pencil_check), null, tint = c.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
