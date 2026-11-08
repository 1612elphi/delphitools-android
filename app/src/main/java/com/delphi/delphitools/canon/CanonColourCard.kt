package com.delphi.delphitools.canon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.colour.ColourFormat
import com.delphi.delphitools.colour.LocalColourFormats
import com.delphi.delphitools.colour.ContrastLevel
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.contrastFigure
import com.delphi.delphitools.colour.contrastRatio
import com.delphi.delphitools.colour.formatted
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours

enum class CanonColourCardLayout {
    /** Swatch above, name and three values below. */
    Chip,

    /** Swatch at the leading edge, name and one value beside it, for lists. */
    Row,
}

/**
 * A paint chip: the colour over its name and values. The chip's swatch carries white and
 * black type with their contrast ratios; each value line copies itself on a tap.
 */
@Composable
fun CanonColourCard(
    colour: Rgb,
    modifier: Modifier = Modifier,
    layout: CanonColourCardLayout = CanonColourCardLayout.Chip,
    name: String? = null,
    primaryFormat: ColourFormat = LocalColourFormats.current.primary,
    secondaryFormat: ColourFormat = LocalColourFormats.current.secondary,
) {
    val c = LocalDelphiColours.current
    val title = name ?: colourNamesOrNull()?.closest(colour) ?: colour.hex
    val formats = listOf(ColourFormat.HEX, primaryFormat, secondaryFormat, ColourFormat.OKLCH, ColourFormat.RGB).distinct()
    val chip = layout == CanonColourCardLayout.Chip
    val info: @Composable (Modifier) -> Unit = { infoModifier ->
        Column(infoModifier, verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
            Text(
                title,
                style = CanonText.bodySemibold,
                color = c.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.card)
                    .then(
                        if (chip) Modifier.padding(horizontal = CanonMetrics.panelPadding, vertical = 12.dp)
                        else Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    ),
            )
            formats.take(if (chip) 3 else 1).forEach { format ->
                CanonValueLine(format.name, colour.formatted(format), labelWidth = 48.dp)
            }
        }
    }
    if (chip) {
        Column(
            modifier
                .fillMaxWidth()
                .background(c.border)
                .padding(CanonMetrics.frame),
            verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .background(colour.toCanonColor())
                    .padding(start = CanonMetrics.panelPadding, end = CanonMetrics.panelPadding, bottom = 12.dp)
                    .clearAndSetSemantics {},
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                InkSample(colour, white = true)
                InkSample(colour, white = false)
            }
            info(Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .heightIn(min = 56.dp)
                .background(c.border),
            horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
        ) {
            Box(Modifier.width(56.dp).fillMaxHeight().background(colour.toCanonColor()))
            info(Modifier.weight(1f))
        }
    }
}

/** "Aa" in white or black with its contrast ratio against the swatch under it. */
@Composable
private fun InkSample(colour: Rgb, white: Boolean) {
    val level = if (white) 1.0 else 0.0
    val ratio = contrastRatio(colour, Rgb(level, level, level))
    val ink = if (white) Color.White else Color.Black
    Column(
        Modifier.alpha(if (ratio >= ContrastLevel.AA) 1f else 0.45f),
        horizontalAlignment = if (white) Alignment.Start else Alignment.End,
    ) {
        Text("Aa", style = CanonColourType.title2Semibold, color = ink)
        Text(contrastFigure(ratio), style = CanonColourType.mono11, color = ink)
    }
}
