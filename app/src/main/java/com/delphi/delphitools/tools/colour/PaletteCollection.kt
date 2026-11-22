package com.delphi.delphitools.tools.colour

import android.app.Application
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowLeft
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourCard
import com.delphi.delphitools.canon.CanonColourCardLayout
import com.delphi.delphitools.canon.CanonDetentWheel
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonRule
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.MenuCellItem
import com.delphi.delphitools.canon.MenuCellPopup
import com.delphi.delphitools.canon.toCanonColor
import com.delphi.delphitools.colour.ColourFormat
import com.delphi.delphitools.colour.CuratedPalette
import com.delphi.delphitools.colour.LocalColourFormats
import com.delphi.delphitools.colour.UserPalettes
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay

private val wheelRow = 46.dp
private const val MINE = 0

/** Shelf 0 is the saved palettes; shelf `i` the curated category `i - 1`. */
class PaletteCollectionModel(app: Application) : AndroidViewModel(app) {
    val store = UserPalettes.of(app)
    var shelf by mutableIntStateOf(1)
        private set
    var query by mutableStateOf("")
        private set
    var typed by mutableStateOf("")
    private var picked by mutableIntStateOf(0)

    /** Where the palette wheel stood on each shelf. */
    private val stoodAt = mutableStateMapOf<Int, Int>()

    val shelves = 1 + PaletteCollectionData.categories.size

    fun title(shelf: Int) = if (shelf == MINE) "My Palettes" else PaletteCollectionData.categories[shelf - 1]

    fun palettes(shelf: Int) =
        if (shelf == MINE) store.palettes else PaletteCollectionData.all.filter { it.category == PaletteCollectionData.categories[shelf - 1] }

    /** Saved matches first, then curated ones. */
    fun found(format: ColourFormat): Pair<List<CuratedPalette>, Int> {
        if (query.isEmpty()) return palettes(shelf).let { it to if (shelf == MINE) it.size else 0 }
        val mine = store.palettes.filter { PaletteCollectionEngine.matches(it, query, format) }
        return mine + PaletteCollectionData.all.filter { PaletteCollectionEngine.matches(it, query, format) } to mine.size
    }

    fun selected(count: Int) = picked.coerceAtMost(maxOf(count - 1, 0))

    fun choose(shelf: Int) {
        if (shelf == this.shelf) return
        this.shelf = shelf
        if (query.isEmpty()) picked = stoodAt[shelf] ?: 0
    }

    fun pick(index: Int) {
        picked = index
        if (query.isEmpty()) stoodAt[shelf] = index
    }

    fun find(text: String) {
        val next = PaletteCollectionEngine.query(text)
        if (next == query) return
        query = next
        picked = if (next.isEmpty()) stoodAt[shelf] ?: 0 else 0
    }
}

@Composable
fun PaletteCollection(model: PaletteCollectionModel = viewModel()) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val format = LocalColourFormats.current.primary
    val store = model.store
    val (shown, shownMine) = model.found(format)
    val selected = model.selected(shown.size)
    val palette = shown.getOrNull(selected)
    val searching = model.query.isNotEmpty()
    var renaming by remember { mutableStateOf<CuratedPalette?>(null) }
    LaunchedEffect(model.typed) {
        delay(400)
        model.find(model.typed)
    }

    val deck = CanonSection.canvas(CanonCanvasSize.Aspect(1.2f), background = c.muted, maxHeight = 640.dp) {
        PaletteDeck(palette, if (searching) model.typed else model.title(model.shelf), Modifier.fillMaxSize(), LocalCanonCanvas.current.top)
    }

    val wheels = CanonSection.canvas(CanonCanvasSize.Fixed(wheelRow * 8), background = c.muted, boxing = CanonCanvasBoxing.UnlessWide) {
        val empty = when {
            shown.isNotEmpty() -> null
            searching -> "No matches"
            else -> "No saved palettes"
        }
        PaletteWheels(
            categories = { modifier ->
                CanonDetentWheel(
                    model.shelves,
                    model.shelf,
                    model::choose,
                    modifier,
                    rowHeight = wheelRow,
                    accessibilityText = model::title,
                ) { index, focus -> WheelTitleRow(model.title(index), "${model.palettes(index).size}", focus, leading = 20.dp, trailing = 12.dp) }
            },
            palettes = { modifier ->
                CanonDetentWheel(
                    shown.size,
                    selected,
                    model::pick,
                    modifier,
                    rowHeight = wheelRow,
                    accessibilityText = { shown.getOrNull(it)?.name.orEmpty() },
                ) { index, focus -> shown.getOrNull(index)?.let { PaletteWheelRow(it, focus) } }
            },
            searching = searching,
            empty = empty,
        )
    }

    val search = CanonSection.of {
        CanonRows {
            Row(Modifier.fillMaxWidth().background(c.card).padding(start = CanonMetrics.panelPadding), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, Modifier.size(16.dp), tint = c.mutedForeground)
                CanonTextCell(
                    model.typed,
                    { model.typed = it },
                    Modifier.weight(1f),
                    placeholder = "Search palettes…",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
                )
            }
        }
    }

    val details = palette?.let { shownPalette ->
        CanonSection.of {
            CanonRows {
                CanonHeading(shownPalette.name)
                shownPalette.rgb.forEach { CanonColourCard(it, layout = CanonColourCardLayout.Row) }
                val export = @Composable { modifier: Modifier -> PaletteExportButton({ shownPalette.name }, { shownPalette.rgb }, modifier) }
                CanonGrid {
                    val cell = Modifier.weight(1f).fillMaxHeight()
                    if (selected < shownMine) {
                        CanonButton("Rename", cell, icon = Icons.Outlined.Edit) { renaming = shownPalette }
                        export(cell)
                        DeleteButton(cell) { store.remove(shownPalette.id) }
                    } else {
                        val saved = store.contains(shownPalette.id)
                        CanonButton(if (saved) "Saved" else "Save", cell, enabled = !saved, icon = if (saved) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder) {
                            store.add(shownPalette)
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        }
                        export(cell)
                    }
                }
            }
        }
    }

    CanonSplit(sidebar = listOf(deck), main = listOfNotNull(wheels, search, details), halves = true, collapsesTo = 240.dp)

    renaming?.let { target ->
        PaletteNameDialog("Rename Palette", target.name, "Rename", onDismiss = { renaming = null }) { name ->
            if (name.isNotEmpty()) store.rename(target.id, name)
        }
    }
}

@Composable
private fun DeleteButton(modifier: Modifier, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        CanonButton("Delete", Modifier.fillMaxSize(), icon = Icons.Outlined.Delete) { open = true }
        MenuCellPopup(open, { open = false }) {
            MenuCellItem("Delete", icon = Icons.Outlined.Delete) {
                open = false
                onDelete()
            }
        }
    }
}

/** The two wheels side by side behind one reading window: categories on the leading side, their palettes beside. */
@Composable
private fun PaletteWheels(
    categories: @Composable (Modifier) -> Unit,
    palettes: @Composable (Modifier) -> Unit,
    searching: Boolean,
    empty: String?,
) {
    val c = LocalDelphiColours.current
    // the category wheel steps back while a search fills the palette wheel
    val dim by animateFloatAsState(if (searching) 0.25f else 1f, tween(250))
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val split = (maxWidth * 0.36f).coerceIn(112.dp, 210.dp)
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(wheelRow).background(c.card))
        CanonRule(Modifier.align(Alignment.Center).offset(y = -(wheelRow + CanonMetrics.hairline) / 2))
        CanonRule(Modifier.align(Alignment.Center).offset(y = (wheelRow + CanonMetrics.hairline) / 2))
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(split).fillMaxHeight().alpha(dim)) {
                categories(Modifier.fillMaxSize())
                if (searching) Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitEachGesture { awaitFirstDown().consume() } })
            }
            CanonRule(vertical = true)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                palettes(Modifier.fillMaxSize())
                if (empty != null) {
                    Text(
                        empty,
                        Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
                        style = TextStyle(fontFamily = DelphiFont, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.mutedForeground, textAlign = TextAlign.Center),
                    )
                }
            }
        }
        Icon(Icons.AutoMirrored.Filled.ArrowRight, null, Modifier.align(Alignment.CenterStart).size(14.dp), tint = c.primary)
        Icon(Icons.AutoMirrored.Filled.ArrowLeft, null, Modifier.align(Alignment.CenterEnd).size(14.dp), tint = c.primary)
    }
}

/** A title that grows, darkens and gains weight towards the centre line, with a figure at the trailing edge. */
@Composable
private fun WheelTitleRow(title: String, detail: String?, focus: Float, leading: Dp, trailing: Dp, lift: Dp = 0.dp) {
    val c = LocalDelphiColours.current
    val step = Math.round(focus * 12) / 12f
    Row(
        Modifier.fillMaxSize().padding(start = leading, end = trailing).offset(y = -lift),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            Modifier.weight(1f),
            style = TextStyle(
                fontFamily = DelphiFont,
                fontSize = (15 + 5 * step).sp,
                fontWeight = FontWeight((400 + 200 * step).toInt()),
                color = lerp(c.mutedForeground, c.foreground, step),
            ),
            maxLines = 1,
        )
        if (!detail.isNullOrEmpty()) {
            Text(detail, Modifier.alpha(0.5f + 0.5f * focus), style = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp, color = c.mutedForeground), maxLines = 1)
        }
    }
}

/** A palette on the wheel: its name over a ribbon of its colours that thickens at the centre. */
@Composable
private fun PaletteWheelRow(palette: CuratedPalette, focus: Float) {
    val step = Math.round(focus * 12) / 12f
    val colours = palette.rgb
    Box(Modifier.fillMaxSize()) {
        WheelTitleRow(palette.name, null, focus, leading = 16.dp, trailing = 20.dp, lift = 5.dp)
        Row(
            Modifier
                .align(Alignment.Center)
                .offset(y = (15 + 5 * step).dp * 0.65f)
                .padding(start = 16.dp, end = 20.dp)
                .fillMaxWidth()
                .height((3 + 4 * step).dp),
        ) {
            colours.forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it.toCanonColor())) }
        }
    }
}

/** Asks for a palette's name; `onConfirm` gets the trimmed text. */
@Composable
internal fun PaletteNameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val c = LocalDelphiColours.current
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(320.dp).background(c.border).padding(CanonMetrics.frame), verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
            CanonHeading(title)
            CanonTextCell(
                text,
                { text = it },
                Modifier.fillMaxWidth(),
                placeholder = "Palette name",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            )
            CanonGrid {
                CanonButton("Cancel", Modifier.weight(1f).fillMaxHeight(), onClick = onDismiss)
                CanonButton(confirm, Modifier.weight(1f).fillMaxHeight(), kind = CanonButtonKind.Primary) {
                    onConfirm(text.trim())
                    onDismiss()
                }
            }
        }
    }
}
