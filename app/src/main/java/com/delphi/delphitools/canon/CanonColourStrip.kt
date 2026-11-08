package com.delphi.delphitools.canon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.colour.ColourFormat
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.formatted
import com.delphi.delphitools.ui.theme.LocalDelphiColours

/**
 * A strip along the bottom of a colour picture: swatch, nearest name, the colour in
 * `format` (the app's primary format); a tap copies the value. With a `palette` it shows
 * those colours as swatches instead, each copying its hex on a tap.
 */
@Composable
fun CanonColourStrip(
    colour: Rgb,
    modifier: Modifier = Modifier,
    palette: List<Rgb> = emptyList(),
    format: ColourFormat = ColourFormat.HEX,
) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val name = colourNamesOrNull()?.closest(colour)
    val value = colour.formatted(format)
    Row(
        modifier
            .fillMaxWidth()
            .height(CanonMetrics.rowHeight)
            .background(c.border)
            .padding(top = CanonMetrics.hairline),
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        if (palette.isNotEmpty()) {
            palette.forEach { CanonSwatch(it, Modifier.weight(1f).fillMaxHeight()) }
            return@Row
        }
        Row(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics {
                    contentDescription = name ?: value
                    stateDescription = value
                }
                .canonClickable { CanonCopyFeedback.copy(view, value) }
                .background(c.border),
            horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
        ) {
            Box(Modifier.width(56.dp).fillMaxHeight().background(colour.toCanonColor()))
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(c.card)
                    .padding(horizontal = CanonMetrics.panelPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    name.orEmpty(),
                    style = CanonColourType.subheadlineSemibold,
                    color = c.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(value, style = CanonColourType.mono13, color = c.mutedForeground, maxLines = 1)
            }
        }
    }
}
