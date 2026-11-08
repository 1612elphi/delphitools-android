package com.delphi.delphitools.canon

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.ui.theme.DelphiLight
import com.delphi.delphitools.ui.theme.LocalDelphiColours

/** The transparency checks: card and muted squares of [CanonChecksSide], a card square first. */
val CanonChecksSide = 8.dp

/** Light tones in both appearances, for pictures that keep dark art visible. */
val CanonChecksPaper = DelphiLight.card to DelphiLight.muted

fun DrawScope.drawCanonChecks(light: Color, dark: Color, side: Float = CanonChecksSide.toPx()) {
    drawRect(light)
    val columns = (size.width / side).toInt() + 1
    val rows = (size.height / side).toInt() + 1
    clipRect {
        for (row in 0 until rows) {
            for (column in (row + 1) % 2 until columns step 2) {
                drawRect(dark, Offset(column * side, row * side), Size(side, side))
            }
        }
    }
}

/** Draws the checks behind the content; `paper` keeps the light tones. */
fun Modifier.canonChecks(paper: Boolean = false): Modifier = composed {
    val c = LocalDelphiColours.current
    val (light, dark) = if (paper) CanonChecksPaper else c.card to c.muted
    drawBehind { drawCanonChecks(light, dark) }
}

@Composable
fun CanonChecks(modifier: Modifier = Modifier, paper: Boolean = false) {
    Spacer(modifier.canonChecks(paper))
}
