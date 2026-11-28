package com.delphi.delphitools.gallery

import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonBigNumberField
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCodeBlock
import com.delphi.delphitools.canon.CanonDecimalField
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonDualItem
import com.delphi.delphitools.canon.CanonDualList
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonFileCell
import com.delphi.delphitools.canon.CanonFromTo
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMenuCell
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonPreviewPanel
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonTextArea
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonTextInput
import com.delphi.delphitools.canon.CanonTextOutput
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.canon.rememberCanonTextFilePicker
import com.delphi.delphitools.ui.theme.LocalDelphiColours

private const val DROP_PROMPT = "Drop an image here"

private const val CSS = ".button {\n    border-radius: 0;\n    color: oklch(0.45 0.12 145);\n}"
private val long = (1..400).joinToString("\n") { "line $it: ${"lorem ipsum ".repeat(12)}" }

@Composable
fun FieldsGallery() {
    val c = LocalDelphiColours.current
    var number by remember { mutableIntStateOf(200) }
    var text by remember { mutableStateOf("") }
    var big by remember { mutableStateOf("16") }
    var decimal by remember { mutableDoubleStateOf(0.0) }
    var signed by remember { mutableDoubleStateOf(0.0) }
    var area by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf<Int?>(0) }
    var rate by remember { mutableStateOf<Int?>(0) }
    var rateTo by remember { mutableStateOf<Int?>(2) }
    var doc by remember { mutableStateOf<Int?>(0) }
    var docTo by remember { mutableStateOf<Int?>(1) }
    val hidden = remember { mutableStateListOf(CanonDualItem("b", "Beta", "two"), CanonDualItem("d", "Delta", "four")) }
    val shown = remember { mutableStateListOf(CanonDualItem("a", "Alpha", "one"), CanonDualItem("c", "Gamma", "three")) }
    var input by remember { mutableStateOf("Glyph flow") }
    var output by remember { mutableStateOf("GLYPH FLOW") }
    var detail by remember { mutableStateOf("1600 x 900") }
    val picker = rememberCanonTextFilePicker { input = it; output = it.uppercase() }

    GalleryPage(Modifier.fillMaxSize().background(c.background)) {
        CanonRows {
            CanonHeading("Components")
            CanonRow("Number", flush = true, controlWeight = 1.4f) {
                CanonNumberField(number, { number = it }, Modifier.fillMaxSize(), range = 0..5000, step = 50, unit = "items", label = "Number")
            }
            CanonRow("Figure", detail = "Follows Number") { CanonFigure("$number", unit = "px", size = 28.sp) }
            CanonRow("Text", flush = true, controlWeight = 1.4f) {
                CanonTextCell(text, { text = it }, Modifier.fillMaxSize(), placeholder = "Trigger")
            }
            CanonRow("File", flush = true, controlWeight = 1.4f) {
                CanonFileCell({}, Modifier.fillMaxSize())
            }
            CanonRow("Image", flush = true, controlWeight = 1.4f) {
                CanonFileCell({}, Modifier.fillMaxSize(), mimeTypes = arrayOf("image/*"), photos = PickVisualMedia.ImageOnly)
            }
            CanonHeading("Big number")
            CanonBigNumberField(
                big,
                { big = it },
                onStep = { big = maxOf(0, (big.toIntOrNull() ?: 0) + it).toString() },
                placeholder = "0",
                unit = "PX",
                label = "Size",
            )
            CanonRow("Decimal", flush = true, controlWeight = 1.4f) {
                CanonDecimalField(decimal, { decimal = it }, Modifier.fillMaxSize(), label = "Decimal")
            }
            CanonRow("Signed, six places", flush = true, controlWeight = 1.4f) {
                CanonDecimalField(signed, { signed = it }, Modifier.fillMaxSize(), unit = "×", step = 0.001, decimals = 6, signed = true)
            }
            CanonTextArea(area, { area = it }, placeholder = "Text area", height = 120.dp)
            CanonRow("Menu", flush = true) {
                CanonMenuCell(listOf("One", "Two", "Three"), menu, { menu = it }, label = "Menu")
            }
            CanonFromTo(listOf("23.976", "24", "25"), rate, rateTo, { rate = it }, { rateTo = it })
            CanonFromTo(listOf("Markdown", "HTML"), doc, docTo, { doc = it }, { docTo = it }, targets = listOf("HTML", "PDF", "Rich Text"))
            CanonTiles(
                listOf(
                    CanonTile("Codec", "AAC"),
                    CanonTile("Sample rate", "44100 Hz"),
                    CanonTile("Peak", "-4.3 dBFS", 0.93f),
                    CanonTile("RMS", "-13.6 dBFS", 0.77f),
                    CanonTile("Encoded with", "Lavf62.12.101"),
                ),
            )
            CanonRow("Dual list", stacked = true) {
                CanonDualList(
                    hidden,
                    shown,
                    leftTitle = "Hidden",
                    rightTitle = "Shown",
                    onMoveRight = { id -> hidden.firstOrNull { it.id == id }?.let { hidden.remove(it); shown.add(it) } },
                    onMoveLeft = { id -> shown.firstOrNull { it.id == id }?.let { shown.remove(it); hidden.add(it) } },
                    onReorder = { id, delta ->
                        val index = shown.indexOfFirst { it.id == id }
                        if (index + delta in shown.indices) shown.add(index + delta, shown.removeAt(index))
                    },
                    listHeight = 140.dp,
                )
            }
        }

        CanonPreviewPanel("Preview", detail = detail) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(c.primary))
        }

        CanonRows {
            CanonHeading("Text input")
            CanonTextInput(input, { input = it }, { output = it.uppercase() })
            CanonButton("Open", icon = pencil(R.drawable.pencil_folder), kind = CanonButtonKind.Secondary) { picker.open() }
        }
        CanonTextOutput(output, heading = picker.fileName ?: "Text output", fileName = "gallery.txt")

        CanonRows {
            CanonHeading("Classics")
            CanonDropZone(DROP_PROMPT, onImages = { images, names ->
                images.firstOrNull()?.let { detail = "${names.firstOrNull() ?: ""} ${it.width} x ${it.height}".trim() }
            })
            CanonCodeBlock(CSS, language = "css")
            CanonCodeBlock("print(\"no language\")")
            CanonCodeBlock(long, language = "capped at 160", maxHeight = 160.dp)
            CanonCodeBlock(
                "<path d=\"M 0 0 L 12 0 …\" />",
                language = "copies the whole",
                copyText = "<path d=\"M 0 0 L 12 0 L 12 4 Z\" />",
            )
            CanonDropZone(DROP_PROMPT, icon = pencil(R.drawable.pencil_file), mimeTypes = arrayOf("*/*"), photos = null, multiple = true, paste = false, onFiles = {})
        }
    }
}
