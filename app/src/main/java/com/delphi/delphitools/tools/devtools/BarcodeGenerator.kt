package com.delphi.delphitools.tools.devtools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourWell
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.contrastFigure
import com.delphi.delphitools.colour.contrastRatio
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import com.delphi.delphitools.tools.images.ImageFiles
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DASH = "—"
private val fieldMono = TextStyle(fontFamily = DelphiMono, fontSize = 16.sp)

/** dt-ios BarcodeGeneratorViewModel and its controller's state. */
class BarcodeGeneratorModel : ViewModel() {
    var kind by mutableStateOf(BarcodeKind.Code128)
        private set

    /** What the field shows; [content] follows it after a typing pause. */
    var field by mutableStateOf("")
        private set
    var content by mutableStateOf("")
        private set
    var foreground by mutableStateOf(Rgb.black)
        private set
    var background by mutableStateOf(Rgb.white)
        private set
    var transparent by mutableStateOf(false)
        private set
    var padding by mutableIntStateOf(BarcodeKind.Code128.defaultPadding)
        private set
    var module by mutableIntStateOf(6)
        private set
    var numbers by mutableStateOf(true)
        private set
    var svg by mutableStateOf(false)

    var sheet by mutableStateOf<CodeSheet?>(null)
        private set
    var failure by mutableStateOf<String?>(null)
        private set
    var shownText by mutableStateOf("")
        private set
    private var typing: Job? = null

    fun chooseKind(value: BarcodeKind) {
        kind = value
        padding = value.defaultPadding
        refresh()
    }

    fun type(text: String) {
        field = text
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            content = text
            refresh()
        }
    }

    fun changeForeground(value: Rgb) {
        foreground = value
        refresh()
    }

    fun changeBackground(value: Rgb) {
        background = value
        refresh()
    }

    fun changeTransparent(value: Boolean) {
        transparent = value
        refresh()
    }

    fun changeNumbers(value: Boolean) {
        numbers = value
        refresh()
    }

    /** A typed number waits for the typing pause; a step applies at once. */
    fun changePadding(value: Int, typed: Boolean) {
        padding = value
        later(typed)
    }

    fun changeModule(value: Int, typed: Boolean) {
        module = value
        later(typed)
    }

    private fun later(typed: Boolean) {
        typing?.cancel()
        if (!typed) return refresh()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            refresh()
        }
    }

    fun refresh() {
        when (val found = BarcodeEngine.modules(content, kind)) {
            BarcodeModules.Empty -> {
                sheet = null
                failure = null
            }
            is BarcodeModules.Failure -> {
                sheet = null
                failure = found.message
            }
            is BarcodeModules.Code -> {
                sheet = BarcodeEngine.sheet(found.rows, found.rowHeight, found.text, kind, padding, foreground, if (transparent) null else background, numbers)
                failure = null
                shownText = found.text
            }
        }
    }

    val pixels get() = sheet?.pixelSize(module.toFloat())

    fun copy(context: Context) {
        val written = sheet ?: return
        val pixels = module.toFloat()
        viewModelScope.launch {
            if (svg) {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("SVG", written.svg(pixels)))
            } else {
                val data = withContext(Dispatchers.Default) { written.png(context, pixels) } ?: return@launch
                ImageFiles.copyPng(context, data, "${kind.fileStem}.png")
            }
        }
    }

    fun export(context: Context) {
        val written = sheet ?: return
        val pixels = module.toFloat()
        viewModelScope.launch {
            if (svg) {
                FileExport.share(context, written.svg(pixels).toByteArray(), "${kind.fileStem}.svg", "image/svg+xml")
            } else {
                val data = withContext(Dispatchers.Default) { written.png(context, pixels) } ?: return@launch
                FileExport.share(context, data, "${kind.fileStem}.png", "image/png")
            }
        }
    }
}

@Composable
fun BarcodeGenerator(model: BarcodeGeneratorModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val kinds = BarcodeKind.entries
    val kind = model.kind
    val sheet = model.sheet

    val contentBlock = CanonSection.of {
        CanonRows {
            CanonHeading("Content")
            CanonSegmented(kinds.map { it.label }, kinds.indexOf(kind), Modifier.semantics { contentDescription = "Barcode Type" }) { model.chooseKind(kinds[it]) }
            CanonStatusRow(kind.description, CanonTone.Muted)
            CanonTextCell(
                model.field,
                model::type,
                Modifier.fillMaxWidth().semantics { contentDescription = "Content" },
                placeholder = kind.placeholder,
                textStyle = fieldMono,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = if (kind == BarcodeKind.Ean13) KeyboardType.Number else KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
            )
            model.failure?.let { CanonStatusRow(it, CanonTone.Warn) }
        }
    }

    val lookBlock = CanonSection.of {
        CanonRows {
            var paddingTyped by remember { mutableStateOf(false) }
            var moduleTyped by remember { mutableStateOf(false) }
            CanonHeading("Colours")
            CanonColourWell(model.foreground, model::changeForeground, label = "Foreground")
            // ponytail: dimmed while transparent but still editable; the iOS well also stops taking input
            CanonColourWell(model.background, model::changeBackground, Modifier.alpha(if (model.transparent) 0.4f else 1f), label = "Background")
            CanonRow("Transparent") { CanonSwitch(model.transparent, onCheckedChange = model::changeTransparent) }
            CanonRow("Padding", detail = "Modules", flush = true) {
                CanonNumberField(model.padding, { model.changePadding(it, paddingTyped) }, Modifier.onFocusChanged { paddingTyped = it.hasFocus }, range = 0..40)
            }
            CanonRow("Module", flush = true) {
                CanonNumberField(model.module, { model.changeModule(it, moduleTyped) }, Modifier.onFocusChanged { moduleTyped = it.hasFocus }, range = 1..20, unit = "px")
            }
            if (kind.is1D) CanonRow("Show numbers") { CanonSwitch(model.numbers, onCheckedChange = model::changeNumbers) }
        }
    }

    val outputBlock = CanonSection.of {
        CanonRows {
            val paper = if (model.transparent) Rgb.white else model.background
            val tiles = mutableListOf(
                CanonTile("Modules", sheet?.let { if (it.rows == 1) "${it.columns}" else "${it.columns} × ${it.rows}" } ?: DASH),
                CanonTile("Characters", "${model.content.codePointCount(0, model.content.length)}"),
                CanonTile("Contrast", contrastFigure(contrastRatio(model.foreground, paper)) + ":1"),
            )
            if (kind == BarcodeKind.Ean13) tiles += CanonTile("Check digit", if (sheet == null) DASH else model.shownText.takeLast(1))
            CanonHeading("Code")
            CanonTiles(tiles)
            CanonHeading("Format")
            CanonSegmented(listOf("PNG", "SVG"), if (model.svg) 1 else 0, Modifier.semantics { contentDescription = "Format" }) { model.svg = it == 1 }
            CanonGrid {
                CanonButton("Copy", Modifier.weight(1f).fillMaxHeight(), enabled = sheet != null, icon = Icons.Outlined.ContentCopy, flashesCopied = true) {
                    model.copy(context)
                }
                CanonButton("Export", Modifier.weight(1f).fillMaxHeight(), kind = CanonButtonKind.Primary, enabled = sheet != null, icon = Icons.Outlined.Share, oversized = true) {
                    model.export(context)
                }
            }
        }
    }

    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(1f), background = c.card, maxHeight = 520.dp) {
        CodeSheetPicture(sheet, kind.label, model.pixels, Icons.Outlined.ViewWeek, if (sheet == null) null else model.shownText)
    }

    CanonSplit(sidebar = listOf(canvas), main = listOf(contentBlock, lookBlock, outputBlock), halves = true, collapsesTo = 240.dp)
}
