package com.delphi.delphitools.tools.colour

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourCard
import com.delphi.delphitools.canon.CanonColourCardLayout
import com.delphi.delphitools.canon.CanonColourControls
import com.delphi.delphitools.canon.CanonColourControlsState
import com.delphi.delphitools.canon.CanonColourSolid
import com.delphi.delphitools.canon.CanonColourSolidState
import com.delphi.delphitools.canon.CanonColourStrip
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonPaletteCard
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSharedText
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.toCanonColor
import com.delphi.delphitools.colour.ColourBlindness
import com.delphi.delphitools.colour.ContrastLevel
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.SolidSpace
import com.delphi.delphitools.colour.contrastFigure
import com.delphi.delphitools.colour.contrastRatio
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.oklch
import com.delphi.delphitools.colour.parseColour
import com.delphi.delphitools.colour.parseHex
import com.delphi.delphitools.colour.simulate
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

private val harmonies = listOf(
    "Complement" to listOf(0.0, 180.0),
    "Triad" to listOf(0.0, 120.0, 240.0),
    "Analogous" to listOf(-30.0, 0.0, 30.0),
    "Split" to listOf(0.0, 150.0, 210.0),
)
private val visionTypes = listOf(ColourBlindness.Protanopia, ColourBlindness.Deuteranopia, ColourBlindness.Tritanopia, ColourBlindness.Achromatopsia)

class ColourAtlasModel : ViewModel() {
    val solid = CanonColourSolidState(SolidSpace.OKLCH, parseHex("3B82F6")!!)

    // sliced row, oklch hue
    val controls = CanonColourControlsState(solid, activeRow = 2)
    var harmonyKind by mutableIntStateOf(0)
}

@Composable
fun ColourAtlas(model: ColourAtlasModel = viewModel()) {
    val colour = model.solid.colour
    CanonSharedText { parseColour(it)?.let(model.controls::use) }
    val offsets = harmonies[model.harmonyKind].second
    val rotated = ColourAtlasEngine.harmony(colour, offsets)
    LaunchedEffect(rotated.map { it.hex }) { model.solid.marks = rotated.zip(offsets).filter { it.second != 0.0 }.map { it.first } }

    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(1f), background = LocalDelphiColours.current.muted, maxHeight = 640.dp) {
        Column(Modifier.fillMaxSize()) {
            CanonColourSolid(model.solid, Modifier.fillMaxWidth().weight(1f).padding(top = LocalCanonCanvas.current.top))
            CanonColourStrip(colour)
        }
    }

    val lch = colour.oklch()
    val most = ColourAtlasEngine.maxChroma(lch[0], lch[2])
    val used = if (most > 0) min(1.0, lch[1] / most) else 0.0

    val main = listOf(
        CanonSection.of { CanonRows { CanonColourControls(model.controls) } },
        CanonSection.of { CanonColourCard(colour) },
        CanonSection.of {
            CanonRows {
                CanonHeading("Notations")
                ColourAtlasEngine.notations(colour).forEach { CanonValueLine(it.label, it.value, labelWidth = 92.dp) }
            }
        },
        CanonSection.of {
            CanonRows {
                CanonHeading("Gamut")
                CanonTiles(
                    listOf(
                        CanonTile("Chroma", String.format(Locale.ROOT, "%.3f", lch[1])),
                        CanonTile("Maximum", String.format(Locale.ROOT, "%.3f", most)),
                        CanonTile("Used", "${(used * 100).roundToInt()} %", used.toFloat()),
                    ),
                )
            }
        },
        CanonSection.of {
            CanonRows {
                CanonHeading("Contrast")
                ContrastRow(colour, white = true)
                ContrastRow(colour, white = false)
            }
        },
        CanonSection.of {
            CanonPaletteCard(
                "Harmonies",
                rotated,
                height = 96.dp,
                picker = { CanonSegmented(harmonies.map { it.first }, model.harmonyKind, columns = 2) { model.harmonyKind = it } },
            )
        },
        CanonSection.of { CanonPaletteCard("Tints", ColourAtlasEngine.scale(colour, 1.0), height = 96.dp) },
        CanonSection.of { CanonPaletteCard("Shades", ColourAtlasEngine.scale(colour, 0.0), height = 96.dp) },
        CanonSection.of {
            CanonRows {
                CanonHeading("Colour blindness")
                visionTypes.forEach { CanonColourCard(colour.simulate(it), layout = CanonColourCardLayout.Row, name = it.displayName) }
            }
        },
    )

    CanonSplit(sidebar = listOf(canvas), main = main, halves = true, collapsesTo = 240.dp)
}

// title3 bold, body mono and caption1 as scaled in CanonText
private val specimenStyle = TextStyle(fontFamily = DelphiFont, fontSize = 18.sp, fontWeight = FontWeight.Bold)
private val ratioStyle = TextStyle(fontFamily = DelphiMono, fontSize = 15.sp)

// wcag 2.2 1.4.3, 1.4.6
private val lamps = listOf("AA" to ContrastLevel.AA, "AAA" to ContrastLevel.AAA, "Large" to ContrastLevel.AA_LARGE)

@Composable
private fun ContrastRow(colour: Rgb, white: Boolean) {
    val c = LocalDelphiColours.current
    val ground = if (white) Rgb.white else Rgb.black
    val against = if (white) "on white" else "on black"
    val value = contrastRatio(colour, ground)
    val ratio = contrastFigure(value) + ":1"
    val passed = lamps.filter { value >= it.second }.map { it.first }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = CanonMetrics.rowHeight)
            .clearAndSetSemantics {
                contentDescription = against
                stateDescription = (listOf(ratio) + passed).joinToString(", ")
            },
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        Box(Modifier.width(56.dp).fillMaxHeight().background(if (white) Color.White else Color.Black), contentAlignment = Alignment.Center) {
            Text("Aa", style = specimenStyle, color = colour.toCanonColor())
        }
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(c.card)
                .padding(horizontal = CanonMetrics.panelPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(ratio, style = ratioStyle, color = c.foreground)
                Text(against, style = CanonText.caption, color = c.mutedForeground)
            }
            lamps.forEach { (label, threshold) ->
                val on = value >= threshold
                Text(
                    label,
                    style = CanonText.footnoteSemibold.copy(fontWeight = FontWeight.Bold),
                    color = if (on) c.primary else c.mutedForeground.copy(alpha = 0.3f),
                )
            }
        }
    }
}
