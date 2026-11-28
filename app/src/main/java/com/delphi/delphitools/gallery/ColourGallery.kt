package com.delphi.delphitools.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonColourCard
import com.delphi.delphitools.canon.CanonColourCardLayout
import com.delphi.delphitools.canon.CanonColourControls
import com.delphi.delphitools.canon.CanonColourControlsState
import com.delphi.delphitools.canon.CanonColourNotationInput
import com.delphi.delphitools.canon.CanonColourNotationState
import com.delphi.delphitools.canon.CanonColourSolid
import com.delphi.delphitools.canon.CanonColourSolidState
import com.delphi.delphitools.canon.CanonColourStrip
import com.delphi.delphitools.canon.CanonColourWell
import com.delphi.delphitools.canon.CanonCopyFeedback
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonPaletteCard
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.fromOklch
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.luminance
import com.delphi.delphitools.colour.oklabToLinear
import com.delphi.delphitools.colour.oklch
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// colorblind-sim's deuteranopia (DelphiCore ColourBlindnessType.visionMatrix)
private val deuteranopia = doubleArrayOf(0.625, 0.375, 0.0, 0.7, 0.3, 0.0, 0.0, 0.3, 0.7)

/** The colour page of the canon gallery (iOS CanonColourGalleryViewController), driven by one colour. */
@Composable
fun ColourGallery() {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val solid = remember { CanonColourSolidState() }
    val controls = remember { CanonColourControlsState(solid) }
    val notation = remember { CanonColourNotationState() }
    var viewMode by remember { mutableIntStateOf(0) }
    var stripPalette by remember { mutableStateOf(false) }
    var harmonyKind by remember { mutableIntStateOf(0) }
    var well by remember { mutableStateOf(Rgb(0.118, 0.4, 0.149)) }
    val colour = solid.colour
    val harmony = harmony(colour, split = harmonyKind == 1)
    LaunchedEffect(harmony.map { it.hex }) { solid.marks = harmony.drop(1) }
    LaunchedEffect(colour.hex) { notation.show(colour) }

    GalleryPage(Modifier.background(c.background)) {
        Column {
            CanonColourSolid(
                solid,
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .aspectRatio(1f)
                    .background(c.muted),
            )
            CanonColourStrip(colour, palette = if (stripPalette) harmony else emptyList())
        }
        CanonRows {
            CanonHeading("Solid")
            CanonColourControls(controls, between = {
                CanonRow("View", flush = true, controlWeight = 2f) {
                    CanonSegmented(listOf("Slice", "Ortho", "Cut"), viewMode) {
                        viewMode = it
                        // cut: debug-only view
                        controls.slices = it != 2
                        controls.viewPlane = it == 1
                    }
                }
            })
            CanonRow("Strip palette") { CanonSwitch(stripPalette) { stripPalette = it } }
            CanonRow("Deuteranopia") {
                var on by remember { mutableStateOf(false) }
                CanonSwitch(on) {
                    on = it
                    solid.setVision(if (it) deuteranopia else CanonColourSolidState.IDENTITY, animated = true)
                }
            }
        }
        CanonColourCard(colour)
        CanonColourCard(colour, layout = CanonColourCardLayout.Row)
        // ponytail: the above, wrapping and numbered value line layouts belong to the display group's CanonValueLine
        CanonRows {
            CanonHeading("Value lines")
            val decimal = String.format(Locale.ROOT, "rgb(%.4f, %.4f, %.4f)", colour.r, colour.g, colour.b)
            val luminance = String.format(Locale.ROOT, "%.3f", colour.luminance)
            CanonValueLine("Decimal RGB", decimal, labelWidth = 92.dp)
            CanonValueLine("Luminance", luminance, labelWidth = 92.dp)
        }
        // ponytail: the loupe and detent wheel blocks of the iOS page belong to other groups
        CanonRows {
            CanonHeading("Notation input")
            CanonRow("Any notation", flush = true, controlWeight = 2f) {
                CanonColourNotationInput(controls::use, state = notation)
            }
            CanonHeading("Colour well")
            CanonColourWell(well, { well = it }, label = "Background")
        }
        CanonPaletteCard(
            "Harmony",
            harmony,
            picker = { CanonSegmented(listOf("Analogous", "Split"), harmonyKind) { harmonyKind = it } },
            actions = {
                CanonButton("Copy", Modifier.weight(1f).fillMaxHeight(), icon = pencil(R.drawable.pencil_copy)) {
                    CanonCopyFeedback.copy(view, harmony.joinToString("\n") { it.hex })
                }
                CanonButton("Export", Modifier.weight(1f).fillMaxHeight(), icon = pencil(R.drawable.pencil_share)) {}
                CanonButton("Save", Modifier.weight(1f).fillMaxHeight(), icon = pencil(R.drawable.pencil_download)) {}
            },
        )
        CanonPaletteCard("Shades", shades(colour), height = 120.dp)
    }
}

// stand-in for harmony-genny
private fun harmony(base: Rgb, split: Boolean): List<Rgb> {
    val (l, c, h) = base.oklch().toList()
    return (if (split) listOf(0.0, 150.0, 210.0) else listOf(0.0, 30.0, -30.0)).map { inGamut(l, c, h + it) }
}

private fun shades(base: Rgb): List<Rgb> {
    val (_, c, h) = base.oklch().toList()
    return (0 until 9).map { inGamut(0.95 - it * 0.085, c, h) }
}

private fun inGamut(l: Double, c: Double, h: Double): Rgb {
    val hue = (h + 360) % 360
    val angle = Math.toRadians(hue)
    fun fits(chroma: Double) = oklabToLinear(l, chroma * cos(angle), chroma * sin(angle)).all { it in -1e-4..1.0001 }
    if (fits(c)) return fromOklch(l, c, hue).clamped
    var lo = 0.0
    var hi = c
    repeat(20) {
        val mid = (lo + hi) / 2
        if (fits(mid)) lo = mid else hi = mid
    }
    return fromOklch(l, lo, hue).clamped
}
