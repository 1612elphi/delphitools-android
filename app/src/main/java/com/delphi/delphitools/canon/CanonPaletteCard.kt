package com.delphi.delphitools.canon

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.colour.ColourFormat
import com.delphi.delphitools.colour.LocalColourFormats
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.formatted
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.takesBlackInk
import com.delphi.delphitools.ui.theme.LocalDelphiColours

/**
 * A palette as swatches meeting at hairlines, under a heading. A tap widens a swatch to
 * show its name and values; a tap on the widened swatch copies its hex. `picker` is a
 * choice between palettes under the heading, `actions` flush cells in a bar under the
 * swatches. The widened swatch shows the first of the colour formats that is not hex.
 */
@Composable
fun CanonPaletteCard(
    title: String,
    colours: List<Rgb>,
    modifier: Modifier = Modifier,
    height: Dp = 156.dp,
    selected: Int? = null,
    onSelect: (Int?) -> Unit = {},
    primaryFormat: ColourFormat = LocalColourFormats.current.primary,
    secondaryFormat: ColourFormat = LocalColourFormats.current.secondary,
    picker: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val names = colourNamesOrNull()
    // a tap holds until the selected argument changes
    var picked by remember { mutableStateOf<Pair<Int, Int?>?>(null) }
    val wide = (picked?.takeIf { it.second == selected }?.first ?: selected)?.takeIf { it < colours.size }
    val details = listOf(primaryFormat, secondaryFormat).firstOrNull { it != ColourFormat.HEX } ?: ColourFormat.OKLCH
    Column(
        modifier
            .fillMaxWidth()
            .background(c.border)
            .padding(CanonMetrics.frame),
        verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        CanonHeading(title)
        picker?.invoke()
        Row(
            Modifier.fillMaxWidth().height(height).clipToBounds(),
            horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
        ) {
            colours.forEachIndexed { index, colour ->
                val isWide = index == wide
                // UIView spring, damping 0.82 over 0.5 s
                val weight by animateFloatAsState(if (isWide) WIDENED else 1f, spring(dampingRatio = 0.82f, stiffness = 158f))
                PaletteSwatch(
                    colour,
                    isWide,
                    name = if (isWide) names?.closest(colour) else null,
                    details = colour.formatted(details),
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .semantics {
                            contentDescription = names?.closest(colour) ?: colour.hex
                            this.selected = isWide
                        }
                        .canonClickable(role = Role.Button) {
                            if (isWide) {
                                CanonCopyFeedback.copy(view, colour.hex)
                            } else {
                                picked = index to selected
                                onSelect(index)
                            }
                        },
                )
            }
        }
        if (actions != null) CanonGrid(content = actions)
    }
}

private const val WIDENED = 3f

/** Narrower swatches turn their hex on its side (PaletteStrip.sidewaysBelow). */
private val sidewaysBelow = 72.dp

@Composable
private fun PaletteSwatch(colour: Rgb, wide: Boolean, name: String?, details: String, modifier: Modifier) {
    val ink = if (colour.takesBlackInk) Color.Black else Color.White
    val shown by animateFloatAsState(if (wide) 1f else 0f, tween(200))
    BoxWithConstraints(modifier.background(colour.toCanonColor())) {
        val sideways = !wide && maxWidth < sidewaysBelow
        Column(Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(10.dp)) {
            Text(
                name.orEmpty(),
                style = CanonColourType.subheadlineSemibold,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(22.dp).alpha(shown),
            )
            Text(details, style = CanonColourType.mono11, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(16.dp).alpha(shown))
            if (sideways) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Text(colour.hex, style = CanonColourType.mono12, color = ink, maxLines = 1, softWrap = false, modifier = Modifier.turned())
                }
            } else {
                Text(colour.hex, style = CanonColourType.mono12, color = ink, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Lays the text out on its side, reading upwards. */
private fun Modifier.turned() = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(placeable.height, placeable.width) {
        placeable.placeWithLayer(
            x = -(placeable.width - placeable.height) / 2,
            y = (placeable.width - placeable.height) / 2,
        ) { rotationZ = -90f }
    }
}
