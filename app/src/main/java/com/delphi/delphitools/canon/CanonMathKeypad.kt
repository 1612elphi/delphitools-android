package com.delphi.delphitools.canon

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.PredictiveBackHandler
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.canon.math.CanonMathExpression
import com.delphi.delphitools.canon.math.CanonMathFence
import com.delphi.delphitools.canon.math.CanonMathItem
import com.delphi.delphitools.canon.math.CanonMathKey
import com.delphi.delphitools.canon.math.CanonMathRow
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiDark
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Keypad colours after the Braun ET66, sRGB from delphicanon tokens.css (`--canon-math-*`). */
internal data class CanonMathColours(
    val mathUmber: Color,
    val mathCream: Color,
    val mathGreen: Color,
    val mathBlue: Color,
    val mathClay: Color,
    val mathYellow: Color,
    /** Share of the ink mixed into the card for a tinted key. */
    val tintShare: Float,
)

internal val MathLight = CanonMathColours(
    mathUmber = Color(0xFF3C3025),
    mathCream = Color(0xFFFAF5E6),
    mathGreen = Color(0xFF1E6626),
    mathBlue = Color(0xFF20558F),
    mathClay = Color(0xFFA84622),
    mathYellow = Color(0xFFF6C628),
    tintShare = 0.13f,
)

internal val MathDark = CanonMathColours(
    mathUmber = Color(0xFF403428),
    mathCream = Color(0xFFEBE4D2),
    mathGreen = Color(0xFF7DBD73),
    mathBlue = Color(0xFF89B4EB),
    mathClay = Color(0xFFEC8F6E),
    mathYellow = Color(0xFFE9BD33),
    tintShare = 0.2f,
)

/** The keypad's tint inks, for tools that colour with them (UIColor.mathBlue and its siblings on iOS). */
@Immutable
data class CanonMathInks(val blue: Color, val clay: Color, val green: Color, val yellow: Color)

@Composable
fun canonMathInks(): CanonMathInks {
    val m = if (LocalDelphiColours.current === DelphiDark) MathDark else MathLight
    return CanonMathInks(m.mathBlue, m.mathClay, m.mathGreen, m.mathYellow)
}

/** Mixed in sRGB, as UIKit mixes; Compose's own lerp goes through Oklab. */
private fun mix(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
    a.alpha + (b.alpha - a.alpha) * t,
)

/**
 * Key colours by family. From the Braun ET66: cream digit keys, dark umber operator keys and one yellow key, Done.
 * The tinted families keep their colour on every page, so a structure is green, calculus blue and a letter clay wherever it sits.
 */
internal enum class MathFamily { Digit, Operation, Structure, Calculus, Letter, Clear, Done }

private class FamilyColours(val fill: Color, val ink: Color, val accent: Color)

@Composable
private fun MathFamily.colours(): FamilyColours {
    val c = LocalDelphiColours.current
    val m = if (c === DelphiDark) MathDark else MathLight
    val ink = when (this) {
        MathFamily.Digit -> c.foreground
        MathFamily.Operation -> m.mathCream
        MathFamily.Structure -> m.mathGreen
        MathFamily.Calculus -> m.mathBlue
        MathFamily.Letter -> m.mathClay
        MathFamily.Clear -> c.destructive
        MathFamily.Done -> m.mathUmber
    }
    val fill = when (this) {
        MathFamily.Digit, MathFamily.Clear -> c.card
        MathFamily.Operation -> m.mathUmber
        MathFamily.Done -> m.mathYellow
        else -> mix(c.card, ink, m.tintShare)
    }
    val accent = when (this) {
        MathFamily.Digit, MathFamily.Operation, MathFamily.Clear -> c.foreground
        else -> ink
    }
    return FamilyColours(fill, ink, accent)
}

/** The family a key belongs to wherever it appears. */
private fun familyOf(key: CanonMathKey): MathFamily = when {
    key is CanonMathKey.Symbol && key.symbol.all { it.isDigit() || it == '.' } -> MathFamily.Digit
    key is CanonMathKey.Symbol && key.symbol in setOf("+", "−", "×", "=", ",") -> MathFamily.Operation
    key == CanonMathKey.Symbol("!") || key is CanonMathKey.Series || key is CanonMathKey.Integral || key == CanonMathKey.Derivative ||
        key == CanonMathKey.Limit || key == CanonMathKey.Binomial || key == CanonMathKey.Permutation -> MathFamily.Calculus
    key is CanonMathKey.Symbol -> MathFamily.Letter
    key == CanonMathKey.Divide || key == CanonMathKey.Delete -> MathFamily.Operation
    key == CanonMathKey.Clear -> MathFamily.Clear
    else -> MathFamily.Structure
}

/** Feedback by key role: a light tap for digits and letters, a firmer one for operators, a click for a structure, reject for clear, confirm for Done. */
private fun View.commit(family: MathFamily) = performHapticFeedback(
    when (family) {
        MathFamily.Digit, MathFamily.Letter -> HapticFeedbackConstants.KEYBOARD_TAP
        MathFamily.Operation -> HapticFeedbackConstants.VIRTUAL_KEY
        MathFamily.Structure, MathFamily.Calculus -> HapticFeedbackConstants.CONTEXT_CLICK
        MathFamily.Clear -> HapticFeedbackConstants.REJECT
        MathFamily.Done -> HapticFeedbackConstants.CONFIRM
    }
)

private sealed interface KeyFace {
    class Math(val box: MathBox) : KeyFace
    class Symbol(val vector: ImageVector) : KeyFace
    class Word(val text: String) : KeyFace
}

/** One key: a flush cell in its family's colours with one label, large and centred. A submenu key carries a chevron after its label. */
@Composable
private fun MathKeyCell(
    face: KeyFace,
    family: MathFamily,
    spoken: String,
    modifier: Modifier = Modifier,
    repeats: Boolean = false,
    opensMenu: Boolean = false,
    drift: GraphicsLayerScope.() -> Unit = {},
    onTap: () -> Unit,
) {
    val colours = family.colours()
    val view = LocalView.current
    val press = rememberCanonPress()
    // ponytail: lit while pressed, not on hover
    Box(
        modifier
            .canonPress(press, enabled = true, role = Role.Button, repeats = repeats) {
                view.commit(family)
                onTap()
            }
            .background(if (press.pressed) mix(colours.fill, colours.ink, 0.14f) else colours.fill)
            .clipToBounds()
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .padding(6.dp)
                .graphicsLayer(drift),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (face) {
                is KeyFace.Math -> Canvas(Modifier.weight(1f, fill = false).size(face.box.width.dp, face.box.height.dp)) {
                    drawMathBox(face.box) { colours.ink }
                }
                is KeyFace.Symbol -> Icon(face.vector, contentDescription = null, tint = colours.ink, modifier = Modifier.size(24.dp))
                is KeyFace.Word -> Text(face.text, style = CanonText.bodySemibold.copy(fontSize = 20.sp), color = colours.ink, maxLines = 1)
            }
            if (opensMenu) Icon(Icons.Filled.ExpandLess, contentDescription = null, tint = colours.ink.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
        }
    }
}

private class KeySpec(
    val key: CanonMathKey,
    val family: MathFamily,
    val x: Int,
    val y: Int,
    val w: Int = 1,
    val h: Int = 1,
    val scale: Float = 1f,
    /** A submenu key: its label, and the keys it offers. */
    val menu: Pair<String, List<CanonMathKey>>? = null,
)

private const val KEY_HEIGHT = 54f
private const val SHORT_KEY_HEIGHT = 44f
private const val TAB_HEIGHT = 40f
private const val SHORT_TAB_HEIGHT = 34f
private const val COLUMNS = 6
private const val ROWS = 4

/** Label size as a share of the key height. */
private const val LABEL_SHARE = 0.44f

/** Label scale for names and function keys, so a row of names shares one size: sin⁻¹ fits a narrow cell at it. */
private const val NAME_SCALE = 0.82f

/** Label scale for a submenu key's title. */
private const val MENU_SCALE = 0.72f

/** Width of the spine column (delete, clear, Done), in page columns. */
private const val SPINE_WEIGHT = 1.15f

/** Seconds before a slot key typed on a later page returns to the numbers. */
private const val RETURN_DELAY = 220L

// iOS spring of response 0.46 s: stiffness (2π / 0.46)²
private val glideSpring = spring<Float>(dampingRatio = 0.84f, stiffness = 186.6f)

internal fun mathKeypadHeight(short: Boolean) =
    (if (short) SHORT_TAB_HEIGHT else TAB_HEIGHT) + CanonMetrics.hairline.value * ROWS + (if (short) SHORT_KEY_HEIGHT else KEY_HEIGHT) * ROWS

/** Letters offered, the variable first. */
private fun letters(variable: String) =
    (listOf(variable) + listOf("x", "y", "z", "t", "n", "k", "a", "b", "c", "m", "p", "r", "s").filter { it != variable }).take(12)

/** The four pages, keys placed on a six by four grid. */
private fun mathPages(variable: String): List<List<KeySpec>> {
    val letters = letters(variable)
    fun key(key: CanonMathKey, family: MathFamily, x: Int, y: Int, w: Int = 1, h: Int = 1, scale: Float = 1f) = KeySpec(key, family, x, y, w, h, scale)
    fun digit(digit: Int, x: Int, y: Int, w: Int = 1) = key(CanonMathKey.Symbol(digit.toString()), MathFamily.Digit, x, y, w, scale = 1.1f)
    fun sign(symbol: String, x: Int, y: Int, w: Int = 1) = key(CanonMathKey.Symbol(symbol), MathFamily.Operation, x, y, w, scale = 1.2f)
    fun name(key: CanonMathKey, family: MathFamily, x: Int, y: Int) = KeySpec(key, family, x, y, scale = NAME_SCALE)
    fun function(name: String, x: Int, y: Int, arity: Int = 1, family: MathFamily = MathFamily.Structure, w: Int = 1) =
        key(CanonMathKey.Function(name, arity), family, x, y, w, scale = NAME_SCALE)
    fun menu(title: String, names: List<String>, x: Int, y: Int): KeySpec {
        val keys = names.map { CanonMathKey.Function(it) }
        return KeySpec(keys[0], MathFamily.Structure, x, y, 3, menu = title to keys)
    }
    val pi = "π"
    val infinity = "∞"
    val numbers = listOf(
        key(CanonMathKey.Fraction, MathFamily.Structure, 0, 0), digit(7, 1, 0), digit(8, 2, 0), digit(9, 3, 0),
        key(CanonMathKey.Divide, MathFamily.Operation, 4, 0, scale = 1.2f), key(CanonMathKey.Open, MathFamily.Structure, 5, 0),
        key(CanonMathKey.Square, MathFamily.Structure, 0, 1), digit(4, 1, 1), digit(5, 2, 1), digit(6, 3, 1), sign("×", 4, 1),
        key(CanonMathKey.Symbol(letters[0]), MathFamily.Letter, 5, 1, scale = 1.1f),
        key(CanonMathKey.Root, MathFamily.Structure, 0, 2), digit(1, 1, 2), digit(2, 2, 2), digit(3, 3, 2), sign("−", 4, 2),
        key(CanonMathKey.Symbol(pi), MathFamily.Letter, 5, 2, scale = 1.1f),
        key(CanonMathKey.Power, MathFamily.Structure, 0, 3), digit(0, 1, 3, w = 2), key(CanonMathKey.Symbol("."), MathFamily.Digit, 3, 3, scale = 1.1f),
        sign("+", 4, 3), sign("=", 5, 3),
    )
    val functions = listOf(
        function("sin", 0, 0), function("cos", 1, 0), function("tan", 2, 0),
        function("log", 3, 0), function("log10", 4, 0), name(CanonMathKey.LogBase, MathFamily.Structure, 5, 0),
        function("asin", 0, 1), function("acos", 1, 1), function("atan", 2, 1),
        name(CanonMathKey.Exponential("e"), MathFamily.Structure, 3, 1), name(CanonMathKey.Exponential("10"), MathFamily.Structure, 4, 1),
        name(CanonMathKey.Inverse, MathFamily.Structure, 5, 1),
        name(CanonMathKey.Cube, MathFamily.Structure, 0, 2), name(CanonMathKey.CubeRoot, MathFamily.Structure, 1, 2),
        name(CanonMathKey.NthRoot, MathFamily.Structure, 2, 2),
        name(CanonMathKey.Fence(CanonMathFence.Abs), MathFamily.Structure, 3, 2), name(CanonMathKey.Fence(CanonMathFence.Floor), MathFamily.Structure, 4, 2),
        name(CanonMathKey.Fence(CanonMathFence.Ceil), MathFamily.Structure, 5, 2),
        menu("sinh cosh tanh", listOf("sinh", "cosh", "tanh", "asinh", "acosh", "atanh"), 0, 3),
        menu("sec csc cot", listOf("sec", "csc", "cot", "asec", "acsc", "acot"), 3, 3),
    )
    val calculus = listOf(
        key(CanonMathKey.Derivative, MathFamily.Calculus, 0, 0, h = 2, scale = 1.3f),
        key(CanonMathKey.Integral(false), MathFamily.Calculus, 1, 0, h = 2, scale = 1.3f),
        key(CanonMathKey.Integral(true), MathFamily.Calculus, 2, 0, h = 2, scale = 1.3f),
        key(CanonMathKey.Limit, MathFamily.Calculus, 3, 0, h = 2, scale = 1.3f),
        key(CanonMathKey.Series(false), MathFamily.Calculus, 4, 0, h = 2, scale = 1.3f),
        key(CanonMathKey.Series(true), MathFamily.Calculus, 5, 0, h = 2, scale = 1.3f),
        name(CanonMathKey.Symbol("!"), MathFamily.Calculus, 0, 2), name(CanonMathKey.Binomial, MathFamily.Calculus, 1, 2),
        name(CanonMathKey.Permutation, MathFamily.Calculus, 2, 2),
        function("mod", 3, 2, 2, MathFamily.Calculus), function("gcd", 4, 2, 2, MathFamily.Calculus), function("lcm", 5, 2, 2, MathFamily.Calculus),
        function("min", 0, 3, 2, MathFamily.Calculus), function("max", 1, 3, 2, MathFamily.Calculus),
        function("round", 2, 3, family = MathFamily.Calculus, w = 2), key(CanonMathKey.Symbol(infinity), MathFamily.Calculus, 4, 3, w = 2, scale = 1.2f),
    )
    val greek = listOf("theta", "alpha", "beta", "lambda", "mu", "omega")
    val variables = letters.mapIndexed { index, letter -> key(CanonMathKey.Symbol(letter), MathFamily.Letter, index % 6, index / 6, scale = 1.1f) } +
        greek.mapIndexed { index, name -> key(CanonMathKey.Symbol(name), MathFamily.Letter, index, 2, scale = 1.1f) } +
        listOf(
            key(CanonMathKey.Symbol(pi), MathFamily.Letter, 0, 3, scale = 1.1f), key(CanonMathKey.Symbol("e"), MathFamily.Letter, 1, 3, scale = 1.1f),
            key(CanonMathKey.Symbol("i"), MathFamily.Letter, 2, 3, scale = 1.1f), key(CanonMathKey.Symbol(infinity), MathFamily.Letter, 3, 3, scale = 1.1f),
            sign("=", 4, 3, w = 2),
        )
    return listOf(numbers, functions, calculus, variables)
}

/** A key's one label at [size]: its symbol or its name, with letters where the key opens slots. */
private fun label(key: CanonMathKey, variable: String, size: Float): MathBox {
    val setter = MathTypesetter(size)
    fun row(vararg items: CanonMathItem) = setter.layout(CanonMathRow(items.toMutableList()), variable = variable)
    fun letters(text: String) = CanonMathRow(text.map { CanonMathItem.Symbol(it.toString()) }.toMutableList<CanonMathItem>())
    fun x() = CanonMathItem.Symbol("x")
    return when (key) {
        CanonMathKey.Symbol("!") -> row(CanonMathItem.Symbol("n"), CanonMathItem.Symbol("!"))
        is CanonMathKey.Symbol -> row(CanonMathItem.Symbol(key.symbol))
        CanonMathKey.Fraction -> row(CanonMathItem.Fraction(letters("a"), letters("b")))
        CanonMathKey.Divide -> setter.text("÷")
        CanonMathKey.Power -> row(x(), CanonMathItem.Power(letters("y")))
        CanonMathKey.Square -> row(x(), CanonMathItem.Power(letters("2")))
        CanonMathKey.Cube -> row(x(), CanonMathItem.Power(letters("3")))
        CanonMathKey.Inverse -> row(x(), CanonMathItem.Power(letters("−1")))
        CanonMathKey.Root -> row(CanonMathItem.Root(letters("x")))
        CanonMathKey.CubeRoot -> row(CanonMathItem.NthRoot(letters("3"), letters("x")))
        CanonMathKey.NthRoot -> row(CanonMathItem.NthRoot(letters("n"), letters("x")))
        CanonMathKey.LogBase -> setter.logBase(setter.text("b", italic = true, level = 2), 0)
        is CanonMathKey.Exponential -> row(*(key.base.map { CanonMathItem.Symbol(it.toString()) } + CanonMathItem.Power(letters("x"))).toTypedArray())
        CanonMathKey.Scientific -> {
            val box = setter.text("×10")
            box.append(setter.raised(setter.text("n", italic = true, level = 2), box.ascent, 0), size * 0.04f)
        }
        CanonMathKey.Open -> setter.text("( )")
        CanonMathKey.Close -> setter.text(")")
        is CanonMathKey.Fence -> row(CanonMathItem.Fence(key.kind, letters("x")))
        is CanonMathKey.Function -> setter.functionName(key.name, 0)
        is CanonMathKey.Series -> setter.text(if (key.product) "Π" else "Σ", scale = 1.1f)
        is CanonMathKey.Integral -> {
            fun bound(letter: String) = if (key.bounded) setter.text(letter, italic = true, level = 2) else null
            setter.integralSign(bound("a"), bound("b"), 0)
        }
        CanonMathKey.Derivative -> setter.derivativeOperator(setter.text(variable, mathIsItalic(variable), 1), 0)
        CanonMathKey.Limit -> setter.text("lim")
        CanonMathKey.Binomial, CanonMathKey.Permutation -> setter.text("n", italic = true)
            .append(setter.text(if (key == CanonMathKey.Binomial) "C" else "P"), size * 0.03f)
            .append(setter.text("r", italic = true), size * 0.03f)
        CanonMathKey.Clear -> setter.text("AC")
        else -> MathBox()
    }
}

/** What TalkBack reads for a key. */
private fun spoken(key: CanonMathKey, variable: String): String = when (key) {
    CanonMathKey.Symbol("×") -> "Multiply"
    CanonMathKey.Divide -> "Divide"
    CanonMathKey.Symbol("!") -> "Factorial"
    CanonMathKey.Symbol("π") -> "Pi"
    CanonMathKey.Fraction -> "Fraction"
    CanonMathKey.Open -> "Brackets"
    CanonMathKey.Power -> "Power"
    CanonMathKey.Square -> "Square"
    CanonMathKey.Root -> "Square root"
    CanonMathKey.Fence(CanonMathFence.Abs) -> "Absolute value"
    CanonMathKey.Clear -> "Clear"
    else -> CanonMathExpression("", variable).also { it.apply(key) }.text.replace("()", "").replace("(,)", "")
}

/** A family's variants in a panel over the key that opened it, framed in the family's colour. Typing one closes it, as does a touch anywhere else. */
@Composable
private fun MathSubmenu(anchor: Rect, keys: List<CanonMathKey>, family: MathFamily, cellWidth: Dp, cellHeight: Dp, labelSize: Float, variable: String, onType: (CanonMathKey) -> Unit, onClose: () -> Unit) {
    val c = LocalDelphiColours.current
    val accent = family.colours().accent
    val faces = remember(keys, labelSize, variable) { keys.map { label(it, variable, labelSize * NAME_SCALE) } }
    val grow = remember { Animatable(0f) }
    val shrink = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val latestClose by rememberUpdatedState(onClose)
    // iOS spring of 0.34 s, damping 0.74
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(0.74f, 341f)) }
    fun close() {
        scope.launch {
            shrink.animateTo(1f, tween(140))
            latestClose()
        }
    }
    // registered after the sheet's, so a back gesture closes the panel first
    PredictiveBackHandler { events ->
        try {
            events.collect { shrink.snapTo(it.progress * 0.5f) }
            close()
        } catch (cancelled: CancellationException) {
            scope.launch { shrink.animateTo(0f, spring(0.8f, 400f)) }
            throw cancelled
        }
    }
    MathOverlay {
        MathScrim { close() }
        Column(
            Modifier
                .layout { measurable, constraints ->
                    // above the key, inside the window
                    val panel = measurable.measure(Constraints())
                    val gap = 6.dp.roundToPx()
                    val x = (anchor.center.x - panel.width / 2f).roundToInt().coerceIn(gap, max(gap, constraints.maxWidth - panel.width - gap))
                    val y = max((anchor.top - panel.height - gap).roundToInt(), gap)
                    layout(constraints.maxWidth, constraints.maxHeight) { panel.place(x, y) }
                }
                .graphicsLayer {
                    val g = grow.value
                    val s = shrink.value
                    scaleX = (0.4f + 0.6f * g) * (1 - 0.08f * s)
                    scaleY = scaleX
                    alpha = g.coerceIn(0f, 1f) * (1 - s)
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .background(accent)
                .padding(2.dp)
                .background(c.border),
            verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
        ) {
            keys.indices.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
                    row.forEach { i ->
                        MathKeyCell(KeyFace.Math(faces[i]), family, spoken(keys[i], variable), Modifier.size(cellWidth, cellHeight)) { onType(keys[i]) }
                    }
                }
            }
        }
    }
}

internal fun mathRailHeight(short: Boolean) = if (short) 38f else 44f

/** Sideways drag, in dp, per caret place. */
private const val RAIL_STRIDE = 16f
private const val RIB_SPACING = 7f

/** The caret's controls, under the expression: ribs that slide with a sideways drag, one caret place per stride, then the four arrow keys. */
@Composable
internal fun MathRail(short: Boolean, onStep: (CanonMathKey) -> Boolean) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val latestStep by rememberUpdatedState(onStep)
    fun step(key: CanonMathKey) {
        view.performHapticFeedback(if (latestStep(key)) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.KEYBOARD_RELEASE)
    }
    var phase by remember { mutableFloatStateOf(0f) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(mathRailHeight(short).dp)
            .background(c.border),
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        Canvas(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(c.card)
                .semantics {
                    contentDescription = "Cursor"
                    customActions = listOf(
                        CustomAccessibilityAction("Left") { step(CanonMathKey.Left); true },
                        CustomAccessibilityAction("Right") { step(CanonMathKey.Right); true },
                    )
                }
                .pointerInput(Unit) {
                    var travel = 0f
                    var stepped = 0f
                    detectHorizontalDragGestures(onDragStart = {
                        travel = 0f
                        stepped = 0f
                    }) { change, dx ->
                        change.consume()
                        phase += dx
                        travel += dx / density
                        while (travel - stepped > RAIL_STRIDE) {
                            stepped += RAIL_STRIDE
                            step(CanonMathKey.Right)
                        }
                        while (travel - stepped < -RAIL_STRIDE) {
                            stepped -= RAIL_STRIDE
                            step(CanonMathKey.Left)
                        }
                    }
                }
        ) {
            val spacing = RIB_SPACING.dp.toPx()
            val height = size.height * 0.42f
            var x = phase % spacing
            if (x < 0) x += spacing
            while (x < size.width) {
                val alpha = sin(PI * x / size.width).toFloat().pow(1.6f) * 0.55f
                drawRect(c.mutedForeground.copy(alpha = alpha), Offset(x, center.y - height / 2), Size(1.dp.toPx(), height))
                x += spacing
            }
        }
        listOf(
            Triple(Icons.Filled.ChevronLeft, CanonMathKey.Left, "Left"),
            Triple(Icons.Filled.ExpandLess, CanonMathKey.Up, "Up"),
            Triple(Icons.Filled.ExpandMore, CanonMathKey.Down, "Down"),
            Triple(Icons.Filled.ChevronRight, CanonMathKey.Right, "Right"),
        ).forEach { (icon, key, name) ->
            Box(
                Modifier
                    .width(50.dp)
                    .fillMaxHeight()
                    .canonPress(rememberCanonPress(), enabled = true, role = Role.Button, repeats = true) { step(key) }
                    .background(c.card)
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = c.foreground, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private const val LAMP_HEIGHT = 3f

/** The pages' names as maths, each lit in its page's colour as the page comes into view, over a lamp that slides and changes colour with the swipe. */
@Composable
private fun MathTabs(height: Dp, current: Int, progress: () -> Float, onSelect: (Int) -> Unit) {
    val c = LocalDelphiColours.current
    val m = if (c === DelphiDark) MathDark else MathLight
    val accents = listOf(c.foreground, m.mathGreen, m.mathBlue, m.mathClay)
    val names = listOf("Numbers", "Functions", "Calculus", "Letters")
    val faces = remember {
        val setter = MathTypesetter(18f)
        listOf(
            setter.text("123"),
            setter.text("f", italic = true).append(setter.parens(setter.text("x", italic = true), 0), 1f),
            setter.text("∫").append(setter.text("Σ"), 3f),
            setter.text("xyz", italic = true),
        )
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(c.border)
            .drawWithContent {
                drawContent()
                val hairline = CanonMetrics.hairline.toPx()
                val cell = (size.width - hairline * (faces.size - 1)) / faces.size
                val p = progress().coerceIn(0f, faces.size - 1f)
                val low = floor(p).toInt()
                val high = min(low + 1, faces.size - 1)
                val share = p - low
                // lamp narrows mid-swipe
                val width = cell * 0.62f * (1 - 0.35f * sin(PI * share).toFloat())
                val midX = p * (cell + hairline) + cell / 2
                val lamp = LAMP_HEIGHT.dp.toPx()
                drawRect(mix(accents[low], accents[high], share), Offset(midX - width / 2, size.height - lamp), Size(width, lamp))
            },
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        faces.forEachIndexed { index, face ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .canonClickable(role = Role.Tab) { onSelect(index) }
                    .background(c.card)
                    .semantics {
                        contentDescription = names[index]
                        selected = current == index
                    },
            ) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .offset(y = (-1).dp)
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    val near = max(0f, 1 - abs(progress() - index))
                    drawMathBox(face) { mix(c.mutedForeground, accents[index], near) }
                }
            }
        }
    }
}

/** One page of keys on a grid of six columns and four rows, keys spanning cells, hairline seams between them. */
@Composable
private fun MathPage(specs: List<KeySpec>, cell: @Composable (Int) -> Unit) {
    val c = LocalDelphiColours.current
    Layout(
        content = { specs.indices.forEach { Box { cell(it) } } },
        modifier = Modifier
            .fillMaxSize()
            .background(c.border),
    ) { measurables, constraints ->
        val hairline = CanonMetrics.hairline.toPx()
        val width = (constraints.maxWidth - hairline * (COLUMNS - 1)) / COLUMNS
        val height = (constraints.maxHeight - hairline * (ROWS - 1)) / ROWS
        fun x(column: Int) = (column * (width + hairline)).roundToInt()
        fun y(row: Int) = (row * (height + hairline)).roundToInt()
        val placeables = measurables.mapIndexed { index, measurable ->
            val s = specs[index]
            val right = (x(s.x) + s.w * width + (s.w - 1) * hairline).roundToInt()
            val bottom = (y(s.y) + s.h * height + (s.h - 1) * hairline).roundToInt()
            measurable.measure(Constraints.fixed(right - x(s.x), bottom - y(s.y)))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable -> placeable.place(x(specs[index].x), y(specs[index].y)) }
        }
    }
}

/**
 * Maths keys on four swipeable pages (numbers and operators, functions, calculus, letters and constants), with delete,
 * clear and Done beside every page. Hyperbolic and reciprocal trig sit in submenus. A slot key typed on a later page
 * returns to the numbers, since the slot usually takes a number next.
 */
@Composable
fun CanonMathKeypad(
    onKey: (CanonMathKey) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    variable: String = "x",
    isShort: Boolean = false,
) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val keyHeight = if (isShort) SHORT_KEY_HEIGHT else KEY_HEIGHT
    val labelSize = (keyHeight * LABEL_SHARE).roundToInt().toFloat()
    val pages = remember(variable) { mathPages(variable) }
    val faces = remember(pages, labelSize) {
        pages.map { specs ->
            specs.map { spec ->
                spec.menu?.let { MathTypesetter(labelSize * MENU_SCALE).text(it.first) } ?: label(spec.key, variable, labelSize * spec.scale)
            }
        }
    }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    var openMenu by remember { mutableStateOf<KeySpec?>(null) }
    val anchors = remember { HashMap<KeySpec, Rect>() }
    var pageWidth by remember { mutableFloatStateOf(0f) }
    val progress = { pager.currentPage + pager.currentPageOffsetFraction }
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.drop(1).collect { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } }
    LaunchedEffect(pager.isScrollInProgress) { if (pager.isScrollInProgress) openMenu = null }
    fun type(key: CanonMathKey, page: Int) {
        openMenu = null
        onKey(key)
        if (page == 0 || !key.opensSlot) return
        scope.launch {
            delay(RETURN_DELAY)
            if (pager.currentPage == page && !pager.isScrollInProgress) pager.animateScrollToPage(0, animationSpec = glideSpring)
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .height(mathKeypadHeight(isShort).dp)
            .background(c.border),
        verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        MathTabs((if (isShort) SHORT_TAB_HEIGHT else TAB_HEIGHT).dp, pager.currentPage, progress) { index ->
            openMenu = null
            scope.launch { pager.animateScrollToPage(index, animationSpec = glideSpring) }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
            HorizontalPager(
                pager,
                Modifier
                    .weight(COLUMNS.toFloat())
                    .fillMaxHeight()
                    .onSizeChanged { pageWidth = it.width.toFloat() },
                beyondViewportPageCount = pages.size - 1,
            ) { index ->
                val specs = pages[index]
                MathPage(specs) { i ->
                    val spec = specs[i]
                    val menu = spec.menu
                    MathKeyCell(
                        KeyFace.Math(faces[index][i]), spec.family, menu?.first ?: spoken(spec.key, variable),
                        Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { if (menu != null) anchors[spec] = it.boundsInWindow() },
                        opensMenu = menu != null,
                        drift = {
                            // labels trail their cells, the columns furthest from the leading edge most, and fade
                            val offset = index - progress()
                            val column = if (offset > 0) spec.x else COLUMNS - spec.x - spec.w
                            translationX = offset * pageWidth * (0.05f + 0.035f * column)
                            alpha = 1 - 0.7f * min(1f, abs(offset))
                        },
                    ) {
                        if (menu == null) type(spec.key, index) else openMenu = spec
                    }
                    if (menu != null && openMenu === spec) {
                        val cellWidth = max(64f, pageWidth / view.resources.displayMetrics.density / COLUMNS + 12f)
                        MathSubmenu(anchors[spec] ?: Rect.Zero, menu.second, spec.family, cellWidth.dp, keyHeight.dp, labelSize, variable, { type(it, index) }) { openMenu = null }
                    }
                }
            }
            Column(
                Modifier
                    .weight(SPINE_WEIGHT)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
            ) {
                val fill = Modifier.fillMaxWidth()
                MathKeyCell(KeyFace.Symbol(Icons.AutoMirrored.Outlined.Backspace), MathFamily.Operation, "Delete", fill.weight(1f), repeats = true) {
                    onKey(CanonMathKey.Delete)
                }
                MathKeyCell(KeyFace.Math(label(CanonMathKey.Clear, variable, labelSize * 0.9f)), MathFamily.Clear, "Clear", fill.weight(1f)) {
                    onKey(CanonMathKey.Clear)
                }
                MathKeyCell(KeyFace.Word("Done"), MathFamily.Done, "Done", fill.weight(2f)) { onDone() }
            }
        }
    }
}

object CanonMathKeypad {
    /** A key cell outside the keypad, for insert keys beside a field: the key's label in its family's colours. */
    @Composable
    fun cell(key: CanonMathKey, modifier: Modifier = Modifier, variable: String = "x", onClick: () -> Unit) {
        val face = remember(key, variable) { label(key, variable, KEY_HEIGHT * LABEL_SHARE) }
        MathKeyCell(KeyFace.Math(face), familyOf(key), spoken(key, variable), modifier.height(KEY_HEIGHT.dp), onTap = onClick)
    }
}
