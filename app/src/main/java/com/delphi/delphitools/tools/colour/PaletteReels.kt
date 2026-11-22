package com.delphi.delphitools.tools.colour

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.canon.CanonCopyFeedback
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.MenuCellItem
import com.delphi.delphitools.canon.MenuCellPopup
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.toCanonColor
import com.delphi.delphitools.colour.ColourFormat
import com.delphi.delphitools.colour.ContrastLevel
import com.delphi.delphitools.colour.LocalColourFormats
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.contrastFigure
import com.delphi.delphitools.colour.contrastRatio
import com.delphi.delphitools.colour.formatted
import com.delphi.delphitools.colour.takesBlackInk
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.min

/** A regenerate: the unlocked columns that change, in the order they spin; `upward` for an undo. */
class ReelSpin(val key: Int, val upward: Boolean, val order: Map<Int, Int>)

/** Actions a column of [PaletteReels] offers, by index. */
class ReelActions(
    val onLock: (Int) -> Unit,
    val onEdit: (Int) -> Unit,
    val onRemove: ((Int) -> Unit)?,
    val fillers: (Int) -> List<Rgb>,
)

// reel timing: first spin, extra per later column, start delay per column, share before the overshoot lands
private const val SPIN_MS = 500
private const val EXTRA_MS = 80
private const val STAGGER_MS = 60
private const val LANDING = 0.84f
private const val FADE_MS = 300
private val sliver = 18.dp
private const val SLIVER_SHARE = 0.35f
private val narrow = 76.dp
private val bare = 36.dp
private val informs = 150.dp
private val spinEasing = CubicBezierEasing(0.45f, 0f, 0.3f, 1f)

/**
 * A generated palette as tall columns meeting at hairlines, each with its number, value, nearest name and lock lamp.
 * Regenerate spins the unlocked columns left to right like reels. A tap widens a column to show its values and
 * contrast; a tap on its name or value edits the colour; a long press opens its menu.
 */
@Composable
fun PaletteReels(colours: List<Rgb>, locked: Set<Int>, spin: ReelSpin?, actions: ReelActions, modifier: Modifier = Modifier, topInset: Dp = 0.dp) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    var widened by remember { mutableStateOf<Int?>(null) }
    if ((widened ?: -1) >= colours.size) widened = null
    BoxWithConstraints(modifier.background(c.border).clipToBounds()) {
        val count = colours.size
        val room = maxWidth - CanonMetrics.hairline * (count - 1)
        val others = count - 1
        val thin = if (others > 0) min(sliver, room * SLIVER_SHARE / others) else 0.dp
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
            colours.forEachIndexed { index, colour ->
                val target = when (widened) {
                    null -> room / count
                    index -> room - thin * others
                    else -> thin
                }
                val width by animateDpAsState(target, spring(dampingRatio = 0.82f, stiffness = 300f))
                ReelColumn(
                    index, colour, index in locked, index == widened, spin, actions, count, topInset,
                    Modifier.width(width).fillMaxHeight(),
                ) {
                    widened = if (widened == index) null else index
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
        }
    }
}

@Composable
private fun ReelColumn(
    index: Int,
    colour: Rgb,
    isLocked: Boolean,
    isWidened: Boolean,
    spin: ReelSpin?,
    actions: ReelActions,
    count: Int,
    topInset: Dp,
    modifier: Modifier,
    onWiden: () -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val format = LocalColourFormats.current.primary
    val names = colourNamesOrHex()
    var landed by remember { mutableStateOf(colour) }
    var reel by remember { mutableStateOf<List<Rgb>?>(null) }
    var upward by remember { mutableStateOf(false) }
    var handled by remember { mutableStateOf(spin?.key) }
    var heightPx by remember { mutableStateOf(0f) }
    var menu by remember { mutableStateOf(false) }
    val travel = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(1f) }
    val knock = remember { Animatable(0f) }
    val spinNow by rememberUpdatedState(spin)
    val ground by animateColorAsState(landed.toCanonColor(), tween(FADE_MS))

    LaunchedEffect(colour) {
        if (colour == landed) return@LaunchedEffect
        val event = spinNow
        val order = event?.order?.get(index)
        if (event == null || order == null || event.key == handled || heightPx <= 0) {
            landed = colour
            return@LaunchedEffect
        }
        handled = event.key
        val old = landed
        val fillers = actions.fillers(3 + order)
        upward = event.upward
        reel = if (upward) listOf(old) + fillers + listOf(colour) else listOf(colour) + fillers.reversed() + listOf(old)
        landed = colour
        textAlpha.snapTo(0f)
        travel.snapTo(0f)
        val duration = SPIN_MS + order * EXTRA_MS
        val wait = (order * STAGGER_MS).toLong()
        launch {
            delay(wait + (duration * LANDING).toLong())
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            textAlpha.animateTo(1f, tween(180))
        }
        val span = heightPx * ((reel?.size ?: 2) - 1)
        val overshoot = min(with(density) { 18.dp.toPx() }, heightPx * 0.05f) / span
        delay(wait)
        travel.animateTo(1f, keyframes {
            durationMillis = duration
            0f at 0 using spinEasing
            (1f + overshoot) at (duration * LANDING).toInt() using FastOutSlowInEasing
        })
        reel = null
    }
    // the lamp jumps as a clamp does when it holds
    var lockShown by remember { mutableStateOf(isLocked) }
    LaunchedEffect(isLocked, spin?.key) {
        val toggled = lockShown != isLocked
        lockShown = isLocked
        if (toggled || (isLocked && spin != null && !spin.upward && spin.key != handled)) {
            knock.snapTo(1f)
            knock.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 300f))
        }
    }

    val ink = if (landed.takesBlackInk) Color.Black else Color.White
    val name = names(landed)
    val value = landed.formatted(format)
    BoxWithConstraints(
        modifier
            .clipToBounds()
            .background(ground)
            .drawBehind {
                heightPx = size.height
                val panels = reel ?: return@drawBehind
                val span = size.height * (panels.size - 1)
                val top = if (upward) -span * travel.value else -span * (1 - travel.value)
                panels.forEachIndexed { i, panel ->
                    drawRect(panel.toCanonColor(), Offset(0f, top + i * size.height), Size(size.width, size.height + 1))
                }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { onWiden() }, onLongPress = { menu = true }) }
            .semantics(mergeDescendants = true) {
                contentDescription = name
                stateDescription = if (isLocked) "$value, Locked" else value
                onClick { onWiden(); true }
                customActions = listOf(
                    CustomAccessibilityAction(if (isLocked) "Unlock" else "Lock") { actions.onLock(index); true },
                    CustomAccessibilityAction("Edit colour") { actions.onEdit(index); true },
                )
            },
    ) {
        val width = maxWidth
        val isBare = width < bare
        val isNarrow = width < narrow
        val valueSize = (width.value * 0.1f).coerceIn(13f, 22f)
        val nameSize = (width.value * 0.085f).coerceIn(12f, 18f)
        val numberSize = (width.value * 0.07f).coerceIn(11f, 15f)
        val textAlphaValue = textAlpha.value
        if (!isBare) {
            Text(
                String.format(Locale.ROOT, "%02d", index + 1),
                Modifier.align(Alignment.TopCenter).padding(top = topInset + 10.dp).alpha(textAlphaValue),
                style = TextStyle(fontFamily = DelphiMono, fontSize = numberSize.sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = 0.5f)),
                maxLines = 1,
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .size(44.dp)
                    .alpha(textAlphaValue)
                    .graphicsLayer {
                        val s = 1 + 0.35f * knock.value
                        scaleX = s
                        scaleY = s
                        rotationZ = 11.25f * knock.value
                    }
                    .canonClickable { actions.onLock(index) }
                    .semantics { contentDescription = if (isLocked) "Unlock" else "Lock" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (isLocked) Icons.Rounded.Lock else Icons.Outlined.LockOpen, null, Modifier.size(20.dp), tint = ink.copy(alpha = if (isLocked) 1f else 0.4f))
            }
        }
        val valueStyle = TextStyle(fontFamily = DelphiMono, fontSize = valueSize.sp, fontWeight = FontWeight.SemiBold, color = ink, textAlign = TextAlign.Center)
        val readoutTap = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { if (isNarrow && !isWidened) onWiden() else actions.onEdit(index) }, onLongPress = { menu = true })
        }
        if (!isBare && isNarrow) {
            val length = min(maxHeight * 0.4f, 140.dp)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp + 44.dp + 6.dp + length / 2 - 8.dp)
                    .requiredWidth(length)
                    .height(16.dp)
                    .alpha(textAlphaValue)
                    .graphicsLayer { rotationZ = -90f },
            ) {
                Text(value, style = valueStyle.copy(fontSize = 13.sp, textAlign = TextAlign.Start), maxLines = 1, softWrap = false)
            }
        } else if (!isNarrow) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp + 44.dp + 4.dp)
                    .alpha(textAlphaValue)
                    .then(readoutTap),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(value, style = valueStyle, maxLines = 1, softWrap = false)
                    if (isWidened) Icon(Icons.Outlined.Colorize, null, Modifier.padding(start = 4.dp).size(14.dp), tint = ink.copy(alpha = 0.7f))
                }
                Text(name, style = TextStyle(fontFamily = DelphiFont, fontSize = nameSize.sp, fontWeight = FontWeight.Medium, color = ink, textAlign = TextAlign.Center), maxLines = 1)
            }
        }
        if (isWidened && width >= informs) {
            val above = topInset + 10.dp + (numberSize * 1.3f).dp + 16.dp
            val below = 8.dp + 44.dp + 4.dp + (nameSize * 1.3f).dp + (valueSize * 1.3f).dp + 16.dp
            ColourInfo(
                landed,
                ink,
                Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = above, bottom = below)
                    .alpha(textAlphaValue),
            )
        }
        MenuCellPopup(menu, { menu = false }) {
            CanonHeading(name)
            MenuCellItem("Copy", icon = Icons.Rounded.ContentCopy) {
                menu = false
                CanonCopyFeedback.copy(view, value)
            }
            MenuCellItem("Edit colour", icon = Icons.Outlined.Colorize) {
                menu = false
                actions.onEdit(index)
            }
            MenuCellItem(if (isLocked) "Unlock" else "Lock", icon = if (isLocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock) {
                menu = false
                actions.onLock(index)
            }
            // ponytail: iOS shows Remove dimmed at three colours; MenuCellItem has no disabled state, so it is left out
            if (actions.onRemove != null && count > 3) {
                MenuCellItem("Remove", icon = Icons.Outlined.Delete) {
                    menu = false
                    actions.onRemove.invoke(index)
                }
            }
        }
    }
}

private val infoFormats = listOf(ColourFormat.HEX, ColourFormat.RGB, ColourFormat.HSL, ColourFormat.OKLCH, ColourFormat.LAB)

// rows by what stays longest when the column is short: hex, rgb, oklch, both contrasts, hsl, lab
private val keep = listOf(0, 1, 3, 5, 6, 2, 4)
private const val LINE = 32f
private const val CONTRAST = 44f
private const val GROUP_GAP = 14f

/**
 * What the widened column tells about its colour: the value in the main formats, each copied on a tap, and its
 * contrast as type on white and on black. Rows that do not fit the height are left out, least useful first.
 */
@Composable
private fun ColourInfo(colour: Rgb, ink: Color, modifier: Modifier) {
    val view = LocalView.current
    BoxWithConstraints(modifier) {
        val scale = (maxWidth.value / 300f).coerceIn(1f, 1.6f)
        val lines = infoFormats.size
        fun height(index: Int) = if (index < lines) LINE * scale else CONTRAST * scale + if (index == lines) GROUP_GAP else 0f
        val kept = mutableSetOf<Int>()
        var used = 0f
        for (index in keep) {
            // gap precedes contrast rows
            val extra = if (index > lines && lines !in kept) GROUP_GAP else 0f
            if (used + height(index) + extra > maxHeight.value) continue
            kept += index
            used += height(index) + extra
        }
        Column(Modifier.fillMaxWidth()) {
            var gapped = false
            for (index in 0 until lines + 2) {
                if (index !in kept) continue
                if (index >= lines && !gapped) {
                    Spacer(Modifier.height(GROUP_GAP.dp))
                    gapped = true
                }
                if (index < lines) {
                    val format = infoFormats[index]
                    val text = colour.formatted(format)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height((LINE * scale).dp)
                            .canonClickable { CanonCopyFeedback.copy(view, text) }
                            .semantics(mergeDescendants = true) {
                                contentDescription = format.name
                                stateDescription = text
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            format.name,
                            Modifier.width((52 * scale).dp),
                            style = TextStyle(fontFamily = DelphiFont, fontSize = (11 * scale).sp, fontWeight = FontWeight.SemiBold, color = ink.copy(alpha = 0.6f)),
                        )
                        Text(text, style = TextStyle(fontFamily = DelphiMono, fontSize = (14 * scale).sp, color = ink), maxLines = 1)
                    }
                } else {
                    InkContrast(colour, ink, white = index == lines, scale)
                }
            }
        }
    }
}

// wcag 2.2 1.4.3, 1.4.6
private val thresholds = listOf("AA" to ContrastLevel.AA, "AAA" to ContrastLevel.AAA)

/** The colour as type on a white or black chip, the contrast ratio, and AA and AAA lamps. */
@Composable
private fun InkContrast(colour: Rgb, ink: Color, white: Boolean, scale: Float) {
    val level = if (white) 1.0 else 0.0
    val value = contrastRatio(colour, Rgb(level, level, level))
    val figure = contrastFigure(value) + ":1"
    val against = if (white) "on white" else "on black"
    val passed = thresholds.filter { value >= it.second }.map { it.first }
    Row(
        Modifier
            .fillMaxWidth()
            .height((CONTRAST * scale).dp)
            .semantics(mergeDescendants = true) {
                contentDescription = against
                stateDescription = (listOf(figure) + passed).joinToString(", ")
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size((36 * scale).dp)
                .background(if (white) Color.White else Color.Black)
                .border(CanonMetrics.hairline, ink.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Aa", style = TextStyle(fontFamily = DelphiFont, fontSize = (17 * scale).sp, fontWeight = FontWeight.Bold, color = colour.toCanonColor()))
        }
        Column(Modifier.weight(1f)) {
            Text(figure, style = TextStyle(fontFamily = DelphiMono, fontSize = (15 * scale).sp, fontWeight = FontWeight.SemiBold, color = ink), maxLines = 1)
            Text(against, style = TextStyle(fontFamily = DelphiFont, fontSize = (11 * scale).sp, color = ink.copy(alpha = 0.6f)), maxLines = 1)
        }
        thresholds.forEach { (title, ratio) ->
            Text(title, style = TextStyle(fontFamily = DelphiFont, fontSize = (12 * scale).sp, fontWeight = FontWeight.Bold, color = ink.copy(alpha = if (value >= ratio) 1f else 0.3f)))
        }
    }
}
