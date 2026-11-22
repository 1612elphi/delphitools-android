package com.delphi.delphitools.tools.colour

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.canon.CanonCopyFeedback
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.toCanonColor
import com.delphi.delphitools.colour.CuratedPalette
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.takesBlackInk
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay
import kotlin.math.sin

private const val FOLD_MS = 130
private const val FAN_MS = 520
private const val STAGGER_MS = 35

/**
 * A palette as a paint fan deck held at its corner: the rivet near the top leading corner, the cover chip with the
 * palette's name lying nearly flat along the top, one chip per colour hanging down and out beneath it. A new palette
 * folds the chips back under the cover and fans them out again. A tap on a chip copies its hex.
 */
// ponytail: no press slide of a chip
@Composable
fun PaletteDeck(palette: CuratedPalette?, emptyTitle: String, modifier: Modifier = Modifier, topInset: Dp = 0.dp) {
    val c = LocalDelphiColours.current
    var shown by remember { mutableStateOf(palette to emptyTitle) }
    val fold = remember { Animatable(1f) }
    val time = remember { Animatable(0f) }
    LaunchedEffect(palette?.id, palette?.name, palette?.colours, emptyTitle) {
        if (shown != palette to emptyTitle) {
            fold.animateTo(0f, tween(FOLD_MS, easing = { it * it }))
            shown = palette to emptyTitle
            time.snapTo(0f)
            fold.snapTo(1f)
        } else {
            delay(150)
        }
        val total = FAN_MS + STAGGER_MS * (shown.first?.colours?.size ?: 0)
        time.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
    }
    val (deckPalette, title) = shown
    val colours = deckPalette?.rgb.orEmpty()
    BoxWithConstraints(modifier.padding(start = 16.dp, top = topInset + 14.dp, end = 16.dp, bottom = 12.dp)) {
        if (maxWidth <= 0.dp || maxHeight <= 0.dp) return@BoxWithConstraints
        val fan = PaletteCollectionEngine.fan(0.0, 0.0, maxWidth.value.toDouble(), maxHeight.value.toDouble(), colours.size + 1)
        val closed = fan.angles.last()
        fun spread(index: Int) =
            fold.value * PaletteCollectionEngine.easeOutBack(((time.value - index * STAGGER_MS) / FAN_MS).coerceIn(0f, 1f).toDouble()).toFloat()
        // later chips lie deeper
        for (index in colours.indices.reversed()) {
            val angle = closed + (fan.angles[index] - closed) * spread(index)
            DeckChip(fan, angle, colours[index], null, null)
        }
        val detail = deckPalette?.let { "${it.category} · ${colours.size}" }
        DeckChip(fan, closed, null, deckPalette?.name ?: title, detail)
        val side = maxOf(fan.width * 0.26, 8.0).dp
        Box(
            Modifier
                .offset(fan.pivotX.dp - side / 2, fan.pivotY.dp - side / 2)
                .size(side)
                .background(c.foreground.copy(alpha = 0.55f), CircleShape)
                .padding(side * 0.28f)
                .background(c.muted, CircleShape),
        )
    }
}

@Composable
private fun DeckChip(fan: PaletteCollectionEngine.Fan, angle: Double, colour: Rgb?, title: String?, detail: String?) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val names = colourNamesOrHex()
    val width = fan.width.dp
    val height = (fan.length + fan.tail).dp
    val isCover = colour == null
    val ink = when {
        colour == null -> c.foreground
        colour.takesBlackInk -> Color.Black
        else -> Color.White
    }
    val primary = colour?.hex ?: title.orEmpty()
    val secondary = colour?.let(names) ?: detail
    val w = fan.width.toFloat()
    val primarySize = if (isCover) (w * 0.3f).coerceIn(12f, 34f) else (w * 0.21f).coerceIn(9f, 22f)
    val secondarySize = if (isCover) (w * 0.17f).coerceIn(9f, 18f) else (w * 0.17f).coerceIn(8f, 18f)
    val readsDownward = sin(angle) < -0.001
    val length = minOf(height - fan.tail.dp - width * 0.7f, height * 0.72f)
    val twoLines = !secondary.isNullOrEmpty() && w >= 34
    Box(
        Modifier
            .offset(fan.pivotX.dp - width / 2, fan.pivotY.dp - fan.length.dp)
            .size(width, height)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, (fan.length / (fan.length + fan.tail)).toFloat())
                rotationZ = Math.toDegrees(angle).toFloat()
            }
            .shadow(3.dp, RectangleShape)
            .background(colour?.toCanonColor() ?: c.card)
            .border(if (isCover) CanonMetrics.hairline else 0.5.dp, if (isCover) c.border else Color.Black.copy(alpha = 0.14f))
            .then(
                if (colour == null) {
                    Modifier.semantics(mergeDescendants = true) {
                        heading()
                        contentDescription = primary
                        detail?.let { stateDescription = it }
                    }
                } else {
                    Modifier
                        .canonClickable { CanonCopyFeedback.copy(view, colour.hex) }
                        .semantics(mergeDescendants = true) {
                            contentDescription = secondary.orEmpty()
                            stateDescription = colour.hex
                        }
                },
            ),
    ) {
        if (length > 0.dp) {
            // upward text hugs tip
            val align = if (readsDownward) TextAlign.Start else TextAlign.End
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = width * 0.3f + length / 2 - (width - 8.dp) / 2)
                    .requiredSize(length, width - 8.dp)
                    .graphicsLayer { rotationZ = if (readsDownward) 90f else -90f },
                verticalArrangement = Arrangement.Center,
            ) {
                val weight = if (isCover) FontWeight.Bold else FontWeight.SemiBold
                val family = if (isCover) DelphiFont else DelphiMono
                Text(primary, Modifier.fillMaxWidth(), style = TextStyle(fontFamily = family, fontSize = primarySize.sp, fontWeight = weight, color = ink, textAlign = align), maxLines = 1, softWrap = false)
                if (twoLines) {
                    Text(
                        secondary.orEmpty(),
                        Modifier.fillMaxWidth(),
                        style = TextStyle(
                            fontFamily = DelphiFont,
                            fontSize = secondarySize.sp,
                            fontWeight = if (isCover) FontWeight.Normal else FontWeight.Medium,
                            color = if (isCover) c.mutedForeground else ink.copy(alpha = 0.75f),
                            textAlign = align,
                        ),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
