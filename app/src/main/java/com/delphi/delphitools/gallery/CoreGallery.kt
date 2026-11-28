package com.delphi.delphitools.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonBar
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonLoaderMode
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonRule
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.canon.CanonStatus
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonStepper
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.CanonSwitchGrid
import com.delphi.delphitools.canon.CanonToast
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.CanonValueLayout
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlin.math.roundToInt

private val modes = listOf("Region", "Window", "Screen", "Timed")

@Composable
fun CoreGallery() {
    GalleryPage {
        Rows()
        Classics()
        ValueLines()
    }
}

@Composable
private fun Rows() {
    var plain by remember { mutableStateOf(true) }
    var detailed by remember { mutableStateOf(false) }
    var number by remember { mutableIntStateOf(200) }
    var slider by remember { mutableFloatStateOf(0.65f) }
    var stepped by remember { mutableFloatStateOf(80f) }
    var trailing by remember { mutableFloatStateOf(0.3f) }
    val switches = remember { mutableStateListOf(true, false, true) }
    var mode by remember { mutableIntStateOf(0) }
    var five by remember { mutableIntStateOf(0) }
    var inline by remember { mutableIntStateOf(1) }
    var size by remember { mutableIntStateOf(1) }
    var anchor by remember { mutableIntStateOf(7) }
    CanonRows {
        CanonHeading("Rows")
        CanonRow("Plain") { CanonSwitch(plain) { plain = it } }
        CanonRow("Detail and icon", detail = "Caption under title", icon = pencil(R.drawable.pencil_bug)) {
            CanonSwitch(detailed) { detailed = it }
        }
        CanonRow("No control")
        CanonHeading("Components")
        CanonRow("Number", flush = true, controlWeight = 1.4f) {
            CanonStepper(number, { number = it.coerceIn(0, 5000) }, "items", Modifier.fillMaxSize(), step = 50)
        }
        CanonRow("Slider", flush = true, controlWeight = 1.4f) {
            CanonSlider(slider, { slider = it }, Modifier.fillMaxSize(), readout = "${(slider * 100).roundToInt()}%")
        }
        CanonRow("Stepped slider", flush = true, controlWeight = 1.4f) {
            CanonSlider(stepped, { stepped = it }, Modifier.fillMaxSize(), 1f..100f, 1f, stepped.roundToInt().toString())
        }
        CanonRow("Trailing fill", flush = true, controlWeight = 1.4f) {
            CanonSlider(
                trailing,
                { trailing = it },
                Modifier.fillMaxSize(),
                readout = "${(trailing * 100).roundToInt()}%",
                fillsFromTrailing = true,
            )
        }
        CanonSwitchGrid(listOf("Images", "Files", "Sound").zip(switches)) { i, on -> switches[i] = on }
        CanonRow("Status") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CanonStatus("Granted", CanonTone.Ok)
                CanonStatus("Not granted", CanonTone.Warn)
                CanonStatus("Stopped", CanonTone.Muted)
            }
        }
        CanonStatusRow("Status row", CanonTone.Warn)
        CanonRow("Loader") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CanonLoader()
                CanonLoader(mode = CanonLoaderMode.Comet, reverse = true)
                CanonLoader(mode = CanonLoaderMode.Dot)
                CanonLoader(mode = CanonLoaderMode.Twin)
                CanonLoader(mode = CanonLoaderMode.Thinking)
                CanonLoader(cell = 4.dp, gap = 1.dp)
            }
        }
        CanonRow("Bar") { CanonBar(0.6f, Modifier.width(120.dp)) }
        CanonRow("Mode", stacked = true) { CanonSegmented(modes, mode) { mode = it } }
        CanonRow("Five in three", stacked = true) {
            CanonSegmented(listOf("One", "Two", "Three", "Four", "Five"), five, columns = 3) { five = it }
        }
        CanonRow("Inline picker", flush = true) { CanonSegmented(modes, inline) { inline = it } }
        CanonSegmented(listOf("Tiny", "Base", "Small", "Large"), size, disabledIndices = setOf(2, 3)) { size = it }
        CanonRow("Symbols", stacked = true) {
            CanonSegmented(
                listOf("Top left", "Top", "Top right", "Left", "Centre", "Right", "Bottom left", "Bottom", "Bottom right"),
                anchor,
                columns = 3,
                icons = listOf(pencil(R.drawable.pencil_arrow_up_left), pencil(R.drawable.pencil_arrow_up), pencil(R.drawable.pencil_arrow_up_right), pencil(R.drawable.pencil_arrow_left), Icons.Rounded.Circle, pencil(R.drawable.pencil_arrow_right), pencil(R.drawable.pencil_arrow_down_left), pencil(R.drawable.pencil_arrow_down), pencil(R.drawable.pencil_arrow_down_right)),
            ) { anchor = it }
        }
        CanonRow("Actions", stacked = true) {
            CanonGrid(columns = null) {
                CanonButton("Cancel", kind = CanonButtonKind.Secondary) {}
                CanonButton("Apply", kind = CanonButtonKind.Primary) {}
            }
        }
        CanonRow("Inline actions", flush = true) {
            CanonGrid(columns = null) {
                CanonButton("Cancel") {}
                CanonButton("Apply") {}
                CanonButton("Disabled", enabled = false) {}
            }
        }
        CanonRow("Icon actions", detail = "3.60 – 6.00", flush = true) {
            CanonGrid(columns = null) {
                CanonButton("", icon = pencil(R.drawable.pencil_zoom_out), contentDescription = "Zoom out") {}
                CanonButton("", icon = pencil(R.drawable.pencil_zoom_in), contentDescription = "Zoom in") {}
                CanonButton("", icon = pencil(R.drawable.pencil_arrow_left_right), contentDescription = "Fit width") {}
            }
        }
        CanonRow("Rules") {
            Row(Modifier.height(IntrinsicSize.Min).width(120.dp), verticalAlignment = Alignment.CenterVertically) {
                CanonRule(Modifier.weight(1f))
                CanonRule(Modifier.height(24.dp), vertical = true)
                CanonRule(Modifier.weight(1f))
            }
        }
        CanonEmptyRow("Nothing here")
    }
}

@Composable
private fun Classics() {
    val view = LocalView.current
    var count by remember { mutableIntStateOf(0) }
    CanonRows {
        CanonHeading("Classics")
        CanonButton("Generate", kind = CanonButtonKind.Primary, icon = pencil(R.drawable.pencil_rotate_cw)) {}
        CanonGrid {
            CanonButton("Copy", Modifier.weight(1f).fillMaxHeight(), CanonButtonKind.Secondary, icon = pencil(R.drawable.pencil_copy), flashesCopied = true) {}
            CanonButton("Export", Modifier.weight(1f).fillMaxHeight(), CanonButtonKind.Primary, icon = pencil(R.drawable.pencil_share)) {}
        }
        CanonGrid(columns = null) {
            CanonButton("Copy", kind = CanonButtonKind.Secondary, enabled = false, icon = pencil(R.drawable.pencil_copy)) {}
            CanonButton("Export", kind = CanonButtonKind.Primary, enabled = false, icon = pencil(R.drawable.pencil_share)) {}
        }
        CanonButton("Generate", kind = CanonButtonKind.Primary, icon = pencil(R.drawable.pencil_rotate_cw), oversized = true) {}
        CanonGrid(columns = null) {
            CanonButton("Wipe", kind = CanonButtonKind.Secondary, icon = pencil(R.drawable.pencil_eraser), oversized = true) {}
            CanonButton("Copy", kind = CanonButtonKind.Secondary, icon = pencil(R.drawable.pencil_copy), oversized = true) {}
            CanonButton("Export", kind = CanonButtonKind.Primary, icon = pencil(R.drawable.pencil_share), oversized = true) {}
        }
        CanonGrid(columns = null) {
            CanonButton("Loop", kind = CanonButtonKind.Secondary, icon = pencil(R.drawable.pencil_repeat)) {}
            CanonButton("Play", kind = CanonButtonKind.Primary, icon = pencil(R.drawable.pencil_play)) {}
            CanonButton("Stop", kind = CanonButtonKind.Secondary, icon = pencil(R.drawable.pencil_square)) {}
            CanonButton("Shuffle", kind = CanonButtonKind.Secondary, icon = pencil(R.drawable.pencil_shuffle)) {}
            CanonButton("Export", kind = CanonButtonKind.Secondary, icon = pencil(R.drawable.pencil_share)) {}
            CanonButton("Delete", kind = CanonButtonKind.Secondary, icon = pencil(R.drawable.pencil_trash_2)) {}
        }
        CanonGrid(columns = null) {
            CanonButton("Toast", kind = CanonButtonKind.Secondary, icon = pencil(R.drawable.pencil_message_square)) {
                CanonToast.show(view, "#3B82F6")
            }
            CanonButton("Held $count", kind = CanonButtonKind.Secondary, repeats = true) { count++ }
        }
    }
}

@Composable
private fun ValueLines() {
    var lit by remember { mutableIntStateOf(1) }
    var hex by remember { mutableIntStateOf(0x3B82F6) }
    val c = LocalDelphiColours.current
    CanonRows {
        CanonHeading("Value lines")
        CanonValueLine("HEX", "#%06X".format(hex), 48.dp, fadeDelayMillis = 300)
        CanonValueLine("RGB", "59, 130, 246", 48.dp)
        CanonValueLine("None", null, 48.dp)
        CanonValueLine("Elapsed", "3 h 12 min", layout = CanonValueLayout.Above)
        CanonValueLine(
            "x =",
            "3y² + 2y − 14 / (y + 1) + 0.000012345678901234",
            layout = CanonValueLayout.Wrapping,
            tint = { if (it.isDigit()) c.primary else c.foreground },
        )
        listOf("8f2c6a10-4b1e", "a91d3e77-0c52", "5be0f4c9-7d18").forEachIndexed { i, id ->
            CanonValueLine("${i + 1}", id, layout = CanonValueLayout.Numbered, isLit = i == lit) { lit = i }
        }
        CanonValueLine(
            "4",
            "Gx7#qPz!m2@VwL9\$kR4^tY8&nB1*cD6(eF3)hJ5",
            layout = CanonValueLayout.NumberedWraps,
        )
        CanonButton("Change HEX", Modifier.fillMaxWidth()) { hex = (hex * 31 + 0x1F3A5) and 0xFFFFFF }
    }
}
