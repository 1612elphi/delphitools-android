package com.delphi.delphitools.tools.colour

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonNameDialog
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCodeBlock
import com.delphi.delphitools.canon.CanonColourPickerDialog
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.rememberUserPalettes
import com.delphi.delphitools.tools.ToolUndoActions
import com.delphi.delphitools.tools.UndoStack
import com.delphi.delphitools.ui.theme.LocalDelphiColours

class PaletteGeneratorModel : ViewModel() {
    var state by mutableStateOf(GennyState.initial())
        private set
    var spin by mutableStateOf<ReelSpin?>(null)
        private set
    val undoStack = UndoStack<GennyState>()

    /** Column whose colour the picker is changing. */
    var editing by mutableStateOf<Int?>(null)

    val paletteName get() = state.strategy.name

    private fun spinTo(next: GennyState, upward: Boolean = false) {
        val old = state.palette
        val changed = next.palette.indices.filter { it < old.size && next.palette[it] != old[it] }
        spin = ReelSpin((spin?.key ?: 0) + 1, upward = upward, order = changed.withIndex().associate { it.value to it.index })
        state = next
    }

    private fun change(next: GennyState) {
        if (next != state) undoStack.push(state)
        state = next
    }

    private fun spinChange(next: GennyState) {
        undoStack.push(state)
        spinTo(next)
    }

    fun undo() {
        undoStack.undo(state)?.let { spinTo(it, upward = true) }
    }

    fun redo() {
        undoStack.redo(state)?.let { spinTo(it) }
    }

    fun roll() = spinChange(state.generated())

    fun choose(category: PaletteCategory) = spinChange(state.choosing(category))

    fun select(strategy: PaletteStrategy) = spinChange(state.selecting(strategy))

    fun resize(count: Int) = change(state.resized(count))

    fun toggleLock(index: Int) {
        state = state.togglingLock(index)
    }

    fun remove(index: Int) = change(state.removing(index))

    /** One undo step per picker session, like dt-ios. */
    fun startEditing(index: Int) {
        undoStack.push(state)
        editing = index
    }

    fun set(index: Int, colour: Rgb) {
        state = state.editing(index, colour)
    }
}

@Composable
fun PaletteGenerator(model: PaletteGeneratorModel = viewModel()) {
    val state = model.state
    val view = LocalView.current
    ToolUndoActions(model.undoStack.canUndo, model.undoStack.canRedo, model::undo, model::redo)
    val store = rememberUserPalettes()
    var saving by remember { mutableStateOf(false) }
    val actions = ReelActions(
        onLock = model::toggleLock,
        onEdit = model::startEditing,
        onRemove = model::remove,
        fillers = { state.strategy.generator(it, null) },
    )

    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(1f), background = LocalDelphiColours.current.muted, maxHeight = 640.dp) {
        PaletteReels(state.palette, state.locked, model.spin, actions, Modifier.fillMaxSize(), topInset = LocalCanonCanvas.current.top)
    }

    val categories = PaletteCategory.entries
    val strategies = PaletteStrategies.strategies(state.category)
    val controls = CanonSection.of {
        CanonRows {
            CanonRow("Category", stacked = true) {
                CanonSegmented(categories.map { it.title }, state.category.ordinal, columns = 3) { model.choose(categories[it]) }
            }
            CanonRow("Strategy", stacked = true) {
                CanonSegmented(strategies.map { it.name }, strategies.indexOfFirst { it.id == state.strategy.id }.coerceAtLeast(0), columns = 2) {
                    model.select(strategies[it])
                }
            }
            CanonRow("Colours", flush = true) { CanonNumberField(state.count, model::resize, range = 3..12) }
            CanonGrid {
                CanonButton("Save", Modifier.weight(1f).fillMaxHeight(), icon = Icons.Outlined.FavoriteBorder) { saving = true }
                PaletteExportButton({ model.paletteName }, { model.state.palette }, Modifier.weight(1f).fillMaxHeight(), css = { model.state.css })
            }
        }
    }

    val code = CanonSection.of { CanonCodeBlock(state.css, language = "CSS") }

    CanonSplit(
        sidebar = listOf(canvas),
        main = listOf(controls, code),
        halves = true,
        collapsesTo = 240.dp,
        footer = {
            CanonButton("Regenerate", kind = CanonButtonKind.Primary, icon = Icons.Rounded.Refresh, oversized = true, onClick = model::roll)
        },
    )

    if (saving) {
        CanonNameDialog("Save Palette", model.paletteName, "Save", "Palette name", KeyboardCapitalization.Words, onDismiss = { saving = false }) { typed ->
            store.add(typed.ifEmpty { model.paletteName }, model.state.palette)
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    model.editing?.let { index ->
        state.palette.getOrNull(index)?.let { initial ->
            CanonColourPickerDialog(initial, { model.set(index, it) }) { model.editing = null }
        }
    }
}
