package com.delphi.delphitools.canon

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlin.math.roundToInt

sealed interface CanonCanvasSize {
    /** Band height, frame included. */
    data class Fixed(val height: Dp) : CanonCanvasSize

    /** Width over height of the content. */
    data class Aspect(val ratio: Float) : CanonCanvasSize

    /** The content's own height plus the frame; a change animates. */
    data object Fitting : CanonCanvasSize
}

enum class CanonCanvasBoxing {
    Never,

    /** A band beside a sidebar; a framed box the width of the other sections everywhere else. */
    UnlessWide,
}

/** Room a canvas's content keeps clear of at its top: the status bar when the canvas is the first thing on screen. */
@Immutable
data class CanonCanvasInsets(val top: Dp = 0.dp)

val LocalCanonCanvas = compositionLocalOf { CanonCanvasInsets() }

/** The open tool's symbol, tiled faintly into a banner. The tool host provides it. */
val LocalCanonPatternSymbol = staticCompositionLocalOf<ImageVector?> { null }

/** Set by CanonSplit per section; null boxing means the canvas decides from its own `boxing`. */
@Immutable
internal data class CanvasPlacement(
    val boxed: Boolean? = null,
    val bleedTop: Dp = 0.dp,
    val fillsPane: Boolean = false,
    val showsPattern: Boolean = false,
)

internal val LocalCanvasPlacement = compositionLocalOf { CanvasPlacement() }

/** Text styles the layout group needs beyond CanonText. */
internal object LayoutText {
    val title2 = TextStyle(fontFamily = DelphiFont, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    val title3 = TextStyle(fontFamily = DelphiFont, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

private val patternTile = 92.dp
private val patternSymbol = 24.dp
private const val PATTERN_ALPHA = 0.04f

/** Band height for `width`, frame included; null for a fitting band, which only knows it once measured. */
internal fun CanonCanvasSize.band(width: Dp, maxHeight: Dp?): Dp? = when (this) {
    is CanonCanvasSize.Fixed -> height
    is CanonCanvasSize.Aspect -> (width / ratio + CanonMetrics.frame * 2).let { if (maxHeight != null) minOf(it, maxHeight) else it }
    CanonCanvasSize.Fitting -> null
}

/**
 * Edge-to-edge band for a picture, a preview or a drawing surface, ruled above and below.
 * Put it between blocks of CanonRows, never inside one. Inside a CanonSplit the split decides
 * whether it bleeds under the status bar (then it drops its top rule) or is boxed.
 */
@Composable
fun CanonCanvas(
    size: CanonCanvasSize,
    modifier: Modifier = Modifier,
    background: Color = Color.Unspecified,
    maxHeight: Dp? = null,
    boxing: CanonCanvasBoxing = CanonCanvasBoxing.Never,
    showsPattern: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalDelphiColours.current
    val placement = LocalCanvasPlacement.current
    val boxed = placement.boxed ?: (boxing == CanonCanvasBoxing.UnlessWide)
    val bleed = if (boxed) 0.dp else placement.bleedTop
    val frame = CanonMetrics.frame
    val sized = when {
        placement.fillsPane -> Modifier.fillMaxSize()
        size == CanonCanvasSize.Fitting -> Modifier.fillMaxWidth()
        else -> Modifier.fillMaxWidth().layout { measurable, constraints ->
            val width = constraints.maxWidth
            val band = size.band(width.toDp(), null)!! + bleed
            val capped = if (maxHeight != null) minOf(band, maxHeight) else band
            val height = capped.roundToPx().coerceIn(constraints.minHeight, constraints.maxHeight)
            val placeable = measurable.measure(Constraints.fixed(width, height))
            layout(width, height) { placeable.place(0, 0) }
        }
    }
    val symbol = LocalCanonPatternSymbol.current
    val pattern = if ((showsPattern || placement.showsPattern) && symbol != null) rememberVectorPainter(symbol) else null
    val ink = c.foreground
    Box(
        modifier
            .then(sized)
            .background(c.border)
            .padding(
                top = if (bleed > 0.dp) 0.dp else frame,
                bottom = frame,
                start = if (boxed) frame else 0.dp,
                end = if (boxed) frame else 0.dp,
            ),
    ) {
        CompositionLocalProvider(LocalCanonCanvas provides CanonCanvasInsets(top = bleed)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (size == CanonCanvasSize.Fitting && !placement.fillsPane) Modifier.animateContentSize() else Modifier.fillMaxHeight())
                    .clipToBounds()
                    .background(background.takeOrElse { c.card })
                    .then(
                        if (pattern == null) Modifier else Modifier.drawBehind {
                            val tile = patternTile.toPx()
                            val glyph = patternSymbol.toPx()
                            val filter = ColorFilter.tint(ink.copy(alpha = PATTERN_ALPHA))
                            var y = 0f
                            while (y < this.size.height) {
                                var x = 0f
                                while (x < this.size.width) {
                                    for (centre in listOf(Offset(x + tile * 0.25f, y + tile * 0.25f), Offset(x + tile * 0.75f, y + tile * 0.75f))) {
                                        translate(centre.x - glyph / 2, centre.y - glyph / 2) {
                                            with(pattern) { draw(Size(glyph, glyph), colorFilter = filter) }
                                        }
                                    }
                                    x += tile
                                }
                                y += tile
                            }
                        },
                    ),
                content = content,
            )
        }
    }
}
