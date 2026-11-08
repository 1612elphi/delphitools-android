package com.delphi.delphitools.canon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.takesBlackInk

/** One colour, its hex where it fits; a tap copies the hex. The swatch of a CanonColourStrip palette. */
@Composable
fun CanonSwatch(colour: Rgb, modifier: Modifier = Modifier) {
    val view = LocalView.current
    val name = colourNamesOrNull()?.closest(colour)
    val hex = colour.hex
    val measurer = rememberTextMeasurer()
    val hexWidth = with(LocalDensity.current) { measurer.measure(hex, CanonColourType.mono11).size.width.toDp() }
    BoxWithConstraints(
        modifier
            .semantics(mergeDescendants = true) { contentDescription = name?.let { "$it $hex" } ?: hex }
            .canonClickable { CanonCopyFeedback.copy(view, hex) }
            .background(colour.toCanonColor()),
        contentAlignment = Alignment.Center,
    ) {
        if (hexWidth + 8.dp <= maxWidth) {
            Text(hex, style = CanonColourType.mono11, color = if (colour.takesBlackInk) Color.Black else Color.White, maxLines = 1)
        }
    }
}
