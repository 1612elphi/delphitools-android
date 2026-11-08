package com.delphi.delphitools.canon

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlin.math.roundToInt
import com.delphi.delphitools.ui.theme.DelphiMono

private const val ROLL = 350
private val rollEasing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)

private class Roll(var text: String, var up: Boolean)

/** The number a figure's text starts with, which sets the roll direction; 0 when none. */
private fun leadingNumber(text: String) = text.takeWhile { it.isDigit() || it == '.' || it == '-' }.toDoubleOrNull() ?: 0.0

/** Text drawn twice: an outline with a soft shadow in [halo], then the fill. */
@Composable
private fun HaloText(text: String, style: TextStyle, halo: Color?) {
    val density = LocalDensity.current
    Box {
        if (halo != null) {
            val shadow = with(density) { Shadow(halo, Offset(0f, 3.dp.toPx()), 5.dp.toPx()) }
            Text(text, style = style.copy(color = halo, drawStyle = Stroke(with(density) { 2.dp.toPx() }), shadow = shadow), maxLines = 1)
        }
        Text(text, style = style, maxLines = 1)
    }
}

/**
 * A calculated figure whose changed characters roll in the direction the number moved,
 * compared from the end so units line up with units. [unit] is set smaller after it.
 * [halo] outlines the figure for one that sits over a picture.
 */
@Composable
fun CanonFigure(
    text: String,
    modifier: Modifier = Modifier,
    unit: String = "",
    size: TextUnit = 40.sp,
    colour: Color = LocalDelphiColours.current.primary,
    halo: Color? = null,
    animated: Boolean = true,
) {
    val ink by animateColorAsState(colour, tween(200))
    val roll = remember { Roll(text, true) }
    if (roll.text != text) {
        roll.up = leadingNumber(text) >= leadingNumber(roll.text)
        roll.text = text
    }
    val up = roll.up
    val figure = TextStyle(fontFamily = DelphiMono, fontSize = size, fontWeight = FontWeight.Bold, color = ink, fontFeatureSettings = TABULAR)
    val measurer = rememberTextMeasurer()
    val baseline = remember(figure.fontSize) { measurer.measure("0", figure).firstBaseline.roundToInt() }
    // ponytail: no shrink to fit (iOS scales down to half before truncating)
    Row(
        modifier.clearAndSetSemantics { contentDescription = text + unit },
        horizontalArrangement = Arrangement.spacedBy(with(LocalDensity.current) { (size * 0.08f).toDp() }),
        verticalAlignment = Alignment.Bottom,
    ) {
        // animatedcontent drops baseline mid-roll
        Row(Modifier.alignBy { baseline }) {
            text.forEachIndexed { index, char ->
                // ponytail: a new leading place appears without rolling in
                key(text.length - index) {
                    AnimatedContent(
                        char,
                        transitionSpec = {
                            if (!animated) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                val travel = if (up) 0.6f else -0.6f
                                (slideInVertically(tween(ROLL, easing = rollEasing)) { (it * travel).toInt() } + fadeIn(tween(ROLL))) togetherWith
                                    (slideOutVertically(tween(ROLL, easing = rollEasing)) { (-it * travel).toInt() } + fadeOut(tween(ROLL))) using
                                    SizeTransform(clip = false)
                            }
                        },
                        label = "figure",
                    ) { shown -> HaloText(shown.toString(), figure, halo) }
                }
            }
        }
        if (unit.isNotEmpty()) {
            Box(Modifier.alignByBaseline()) {
                HaloText(unit, TextStyle(fontFamily = DelphiFont, fontSize = size * 0.55f, fontWeight = FontWeight.SemiBold, color = ink), halo)
            }
        }
    }
}
