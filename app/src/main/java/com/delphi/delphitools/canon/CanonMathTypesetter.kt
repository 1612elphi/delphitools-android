package com.delphi.delphitools.canon

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Rect
import com.delphi.delphitools.canon.math.CanonMathFence
import com.delphi.delphitools.canon.math.CanonMathItem
import com.delphi.delphitools.canon.math.CanonMathRow
import kotlin.math.max
import kotlin.math.min

internal enum class MathInk { Input, Result, Accent }

internal sealed interface MathMark {
    data class Glyphs(val text: String, val italic: Boolean) : MathMark
    data object Rule : MathMark
    data class Paren(val open: Boolean) : MathMark

    /** Half of a floor or ceiling bracket. */
    data class Corner(val open: Boolean, val floor: Boolean) : MathMark
    data object Radical : MathMark
    data object Hole : MathMark
}

/** Glyphs: left is the pen position, top the baseline and height the font size. Shapes: their bounds. Units are dp. */
internal data class MathAtom(val mark: MathMark, val frame: Rect, val weight: Float = 0f, val ink: MathInk)

/** A place the editing caret can stand: an index in an edited row, and the caret's line. */
internal data class MathCaret(val row: CanonMathRow, val index: Int, val rect: Rect)

/** Atoms placed around a baseline at y = 0, y growing downwards, and the caret stops among them when edited. */
internal class MathBox(
    var width: Float = 0f,
    var ascent: Float = 0f,
    var descent: Float = 0f,
    val atoms: MutableList<MathAtom> = mutableListOf(),
    val carets: MutableList<MathCaret> = mutableListOf(),
) {
    val height get() = ascent + descent

    fun append(other: MathBox, gap: Float = 0f): MathBox {
        embed(other, width + gap)
        width += gap + other.width
        ascent = max(ascent, other.ascent)
        descent = max(descent, other.descent)
        return this
    }

    /** Adds [other]'s atoms and carets moved by [x] and [y]; the size stays for the caller to set. */
    fun embed(other: MathBox, x: Float, y: Float = 0f) {
        other.atoms.mapTo(atoms) { it.copy(frame = it.frame.translate(x, y)) }
        other.carets.mapTo(carets) { it.copy(rect = it.rect.translate(x, y)) }
    }

    /** Moved down by [y]; a negative [y] raises it. */
    fun lowered(y: Float) = MathBox(width, ascent - y, descent + y).also { it.embed(this, 0f, y) }

    fun copy() = MathBox(width, ascent, descent).also { it.embed(this, 0f) }
}

/** Glyph measurements in the system serif (Noto Serif): advance width and ink bounds about the baseline, both positive. */
private object MathMetrics {
    private const val REFERENCE = 256f
    private val upright = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    private val slanted = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = REFERENCE }
    private val bounds = android.graphics.Rect()
    private val cache = HashMap<String, FloatArray>()

    fun typeface(italic: Boolean): Typeface = if (italic) slanted else upright

    /** width, ascent, descent per unit of font size, measured at a large size since bounds are whole pixels. */
    fun measure(text: String, italic: Boolean): FloatArray {
        val key = "$text|$italic"
        cache[key]?.let { return it }
        // ponytail: unbounded cache cleared wholesale; typing makes a few hundred entries at most
        if (cache.size > 2000) cache.clear()
        paint.typeface = typeface(italic)
        paint.getTextBounds(text, 0, text.length, bounds)
        val result = floatArrayOf(paint.measureText(text) / REFERENCE, -bounds.top / REFERENCE, bounds.bottom / REFERENCE)
        cache[key] = result
        return result
    }
}

internal fun mathTypeface(italic: Boolean) = MathMetrics.typeface(italic)

private val signs = mapOf(
    "pi" to "π", "Infinity" to "∞", "alpha" to "α", "beta" to "β", "gamma" to "γ",
    "delta" to "δ", "epsilon" to "ε", "theta" to "θ", "lambda" to "λ", "mu" to "μ",
    "sigma" to "σ", "tau" to "τ", "phi" to "φ", "omega" to "ω",
)

internal fun mathShown(symbol: String) = signs[symbol] ?: symbol

/** Inverse functions, set as the function with a raised −1. */
private val inverses = setOf("asin", "acos", "atan", "asec", "acsc", "acot", "asinh", "acosh", "atanh")

/** Letters are italic; constants shown as signs (π, ∞, ∅) are upright. */
internal fun mathIsItalic(shown: String) = shown !in setOf("π", "∞", "∅") && shown.all { it.isLetter() }

private val spaced = setOf("+", "−", "×")
private val relations = setOf("=", "≈", "∈")

// ponytail: sets edited rows only; a CanonMath.Node tree (the algebra-calc result banner) goes through its rows instead
/** Sets rows in the system serif at [size] dp. Levels follow TeX: 0 display, 1 text (a fraction's parts, same size), 2 script, 3 script-script. */
internal class MathTypesetter(val size: Float, val ink: MathInk = MathInk.Result) {
    fun points(level: Int) = size * floatArrayOf(1f, 1f, 0.7f, 0.55f)[min(level, 3)]

    fun script(level: Int) = if (level < 2) 2 else 3

    fun text(string: String, italic: Boolean = false, level: Int = 0, scale: Float = 1f, ink: MathInk? = null): MathBox {
        val points = points(level) * scale
        if (string.isEmpty()) return MathBox()
        val (width, ascent, descent) = MathMetrics.measure(string, italic)
        val xHeight = MathMetrics.measure("x", italic)[1]
        return MathBox(
            width * points, max(xHeight, ascent) * points, max(0f, descent) * points,
            mutableListOf(MathAtom(MathMark.Glyphs(string, italic), Rect(0f, 0f, width * points, points), ink = ink ?: this.ink)),
        )
    }

    /** A function's name as a calculator prints it: `ln` for nerdamer's natural `log`, `log` for `log10`, sin⁻¹ for `asin`. */
    fun functionName(name: String, level: Int): MathBox = when (name) {
        "log" -> text("ln", level = level)
        "log10" -> text("log", level = level)
        in inverses -> {
            val box = text(name.drop(1), level = level)
            box.append(raised(text("−1", level = script(level)), box.ascent, level), points(level) * 0.03f)
        }
        else -> text(name, level = level)
    }

    /** Absolute value bars, or floor or ceiling brackets, around [content]. */
    fun fence(kind: CanonMathFence, content: MathBox, level: Int): MathBox {
        if (kind == CanonMathFence.Abs) return bars(content, level)
        val s = points(level)
        val top = -max(content.ascent, s * 0.72f) - s * 0.08f
        val bottom = max(content.descent, s * 0.2f) + s * 0.08f
        val width = s * 0.26f
        fun side(open: Boolean) = MathBox(
            width, -top, bottom,
            mutableListOf(MathAtom(MathMark.Corner(open, kind == CanonMathFence.Floor), Rect(0f, top, width, bottom), max(1f, s * 0.05f), ink)),
        )
        return side(true).append(content, s * 0.05f).append(side(false), s * 0.05f)
    }

    /** A radical with its index over the sign's short stroke. */
    fun indexedRadical(index: MathBox, content: MathBox, level: Int): MathBox {
        val root = radical(content, level)
        val hook = points(level) * 0.15f
        val shift = max(0f, index.width - hook)
        val lift = root.ascent * 0.42f
        val box = MathBox(shift + root.width, max(root.ascent, lift + index.ascent), root.descent)
        box.embed(index, shift + hook - index.width, -lift)
        box.embed(root, shift)
        return box
    }

    /** `log` with its base lowered after it. */
    fun logBase(base: MathBox, level: Int) = text("log", level = level).append(base.lowered(points(level) * 0.25f), points(level) * 0.02f)

    /** Σ or Π on the axis with its limits centred above and below. */
    fun series(glyph: String, below: MathBox, above: MathBox, level: Int): MathBox {
        val s = points(level)
        val sign = text(glyph, level = level, scale = 1.4f)
        val centred = sign.lowered((sign.ascent - sign.descent) / 2 - s * 0.26f)
        val width = maxOf(centred.width, below.width, above.width)
        val gap = s * 0.1f
        val aboveBaseline = -centred.ascent - gap - above.descent
        val belowBaseline = centred.descent + gap + below.ascent
        val box = MathBox(width + s * 0.06f, -aboveBaseline + above.ascent, belowBaseline + below.descent)
        box.embed(centred, (width - centred.width) / 2)
        box.embed(above, (width - above.width) / 2, aboveBaseline)
        box.embed(below, (width - below.width) / 2, belowBaseline)
        return box
    }

    /** ∫ on the axis, with its bounds at the top and bottom of its right side when given. */
    fun integralSign(lower: MathBox?, upper: MathBox?, level: Int): MathBox {
        val s = points(level)
        val sign = text("∫", level = level, scale = 1.55f)
        val box = sign.lowered((sign.ascent - sign.descent) / 2 - s * 0.26f)
        if (lower == null && upper == null) return box
        val signWidth = box.width
        val up = upper ?: MathBox()
        val low = lower ?: MathBox()
        val upperBaseline = -box.ascent + up.ascent
        val lowerBaseline = box.descent - low.descent
        box.embed(up, signWidth + s * 0.02f, upperBaseline)
        box.embed(low, signWidth - s * 0.14f, lowerBaseline)
        box.width = max(signWidth + s * 0.02f + up.width, signWidth - s * 0.14f + low.width) + s * 0.04f
        box.ascent = max(box.ascent, -upperBaseline + up.ascent)
        box.descent = max(box.descent, lowerBaseline + low.descent)
        return box
    }

    /** The `dx` closing an integral. */
    fun differential(variable: MathBox, level: Int) = text("d", level = level).append(variable, points(level) * 0.03f)

    /** d over d and the variable, as a fraction. */
    fun derivativeOperator(variable: MathBox, level: Int): MathBox {
        val bottom = text("d", level = level + 1).append(variable, points(level) * 0.03f)
        return fraction(text("d", level = level + 1), bottom, level)
    }

    /** `lim` with the variable's approach set small under it. */
    fun limitOperator(variable: MathBox, approach: MathBox, level: Int): MathBox {
        val s = points(level)
        val lim = text("lim", level = level)
        val below = variable.copy()
        below.append(text("→", level = script(level)), s * 0.03f)
        below.append(approach, s * 0.03f)
        val width = max(lim.width, below.width)
        val belowBaseline = lim.descent + s * 0.14f + below.ascent
        val box = MathBox(width, lim.ascent, belowBaseline + below.descent)
        box.embed(lim, (width - lim.width) / 2)
        box.embed(below, (width - below.width) / 2, belowBaseline)
        return box
    }

    /** Two lines centred on the axis with no bar: a binomial coefficient's inside. */
    fun stacked(top: MathBox, bottom: MathBox, level: Int): MathBox {
        val s = points(level)
        val width = max(top.width, bottom.width)
        val above = top.lowered(-s * 0.26f - s * 0.1f - top.descent)
        val below = bottom.lowered(-s * 0.26f + s * 0.1f + bottom.ascent)
        val box = MathBox(width, above.ascent, below.descent)
        box.embed(above, (width - top.width) / 2)
        box.embed(below, (width - bottom.width) / 2)
        return box
    }

    /** ₙPᵣ, as a calculator prints it. */
    fun permutation(n: MathBox, r: MathBox, level: Int): MathBox {
        val s = points(level)
        return n.lowered(s * 0.2f).append(text("P", level = level), s * 0.02f).append(r.lowered(s * 0.2f), s * 0.02f)
    }

    /** A dashed box for an operand still to be typed. */
    fun hole(level: Int, ink: MathInk? = null): MathBox {
        val s = points(level)
        val side = s * 0.52f
        val height = s * 0.64f
        return MathBox(
            side + s * 0.12f, height, 0f,
            mutableListOf(MathAtom(MathMark.Hole, Rect(s * 0.06f, -height, s * 0.06f + side, 0f), max(1f, s * 0.04f), ink ?: this.ink)),
        )
    }

    /** Absolute value bars around [content]. */
    fun bars(content: MathBox, level: Int): MathBox {
        val s = points(level)
        val weight = max(1f, s * 0.05f)
        val top = -content.ascent - s * 0.08f
        val bar = MathBox(
            weight, -top, content.descent + s * 0.08f,
            mutableListOf(MathAtom(MathMark.Rule, Rect(0f, top, weight, top + content.height + s * 0.16f), ink = ink)),
        )
        val box = MathBox(s * 0.06f).append(bar).append(content, s * 0.1f).append(bar, s * 0.1f)
        box.width += s * 0.06f
        return box
    }

    /** An exponent lifted to sit over a base whose ascent is [ascent]. */
    fun raised(exponent: MathBox, ascent: Float, level: Int): MathBox {
        val lift = max(points(level) * 0.38f, ascent - exponent.height * 0.45f)
        return exponent.lowered(-lift)
    }

    fun fraction(top: MathBox, bottom: MathBox, level: Int): MathBox {
        val s = points(level)
        val weight = max(1f, s * 0.05f)
        val gap = s * 0.16f
        val axis = s * 0.26f
        val margin = s * 0.08f
        val bar = max(top.width, bottom.width) + s * 0.16f
        val box = MathBox(bar + margin * 2)
        val above = top.lowered(-axis - weight / 2 - gap - top.descent)
        val below = bottom.lowered(-axis + weight / 2 + gap + bottom.ascent)
        box.embed(above, margin + (bar - top.width) / 2)
        box.embed(below, margin + (bar - bottom.width) / 2)
        box.atoms += MathAtom(MathMark.Rule, Rect(margin, -axis - weight / 2, margin + bar, -axis + weight / 2), ink = ink)
        box.ascent = above.ascent
        box.descent = below.descent
        return box
    }

    fun radical(content: MathBox, level: Int): MathBox {
        val s = points(level)
        val weight = max(1f, s * 0.05f)
        val top = content.ascent + s * 0.1f + weight
        val signTop = -top + weight / 2
        val sign = Rect(s * 0.04f, signTop, s * 0.54f, signTop + top + content.descent + s * 0.04f - weight / 2)
        val start = sign.right + s * 0.06f
        val end = start + content.width + s * 0.06f
        val box = MathBox(
            end + s * 0.04f, top + s * 0.02f, max(content.descent, sign.bottom),
            mutableListOf(
                MathAtom(MathMark.Radical, sign, weight, ink),
                MathAtom(MathMark.Rule, Rect(sign.right - weight / 2, -top, end, -top + weight), ink = ink),
            ),
        )
        box.embed(content, start)
        return box
    }

    /** Font brackets around content of about one line, drawn brackets around taller content. */
    fun parens(content: MathBox, level: Int): MathBox {
        val s = points(level)
        if (content.height <= s * 1.05f) {
            return text("(", level = level).append(content, s * 0.03f).append(text(")", level = level), s * 0.03f)
        }
        val weight = max(1f, s * 0.07f)
        val width = s * 0.26f + content.height * 0.03f
        val top = -content.ascent - s * 0.08f
        val height = content.height + s * 0.16f
        fun bracket(open: Boolean) = MathBox(
            width, -top, height + top,
            mutableListOf(MathAtom(MathMark.Paren(open), Rect(0f, top, width, top + height), weight, ink)),
        )
        return bracket(true).append(content, s * 0.04f).append(bracket(false), s * 0.04f)
    }

    /** An edited row set with its caret stops. An empty slot shows a dashed box, lit while the caret is in it. */
    fun layout(row: CanonMathRow, level: Int = 0, caret: CanonMathRow? = null, variable: String = "x", isSlot: Boolean = false): MathBox {
        val s = points(level)
        fun slot(row: CanonMathRow, level: Int) = layout(row, level, caret, variable, true)

        // multi-term bodies show end
        fun bodied(body: CanonMathRow): MathBox {
            val terms = body.items.drop(1).any { it is CanonMathItem.Symbol && it.symbol in setOf("+", "−", "=") }
            return if (terms) parens(slot(body, level), level) else slot(body, level)
        }

        fun variableBox(level: Int) = text(variable, mathIsItalic(variable), level)
        var box = MathBox()
        val stops = mutableListOf<Float>()
        if (row.items.isEmpty()) {
            if (isSlot) {
                box = hole(level, if (caret === row) MathInk.Accent else null)
            } else {
                box.ascent = s * 0.72f
                box.descent = s * 0.2f
                stops += 0f
            }
        }
        var previousAscent: Float? = null
        var previousItalic = false
        row.items.forEachIndexed { index, item ->
            val gap = gap(row.items.getOrNull(index - 1), item, row, index, s, level)
            stops += box.width + gap / 2
            val set: MathBox = when (item) {
                is CanonMathItem.Symbol -> {
                    val shown = mathShown(item.symbol)
                    text(shown, mathIsItalic(shown), level)
                }
                is CanonMathItem.Fraction -> fraction(slot(item.top, level + 1), slot(item.bottom, level + 1), level)
                is CanonMathItem.Power -> raised(slot(item.exponent, script(level)), previousAscent ?: (s * 0.5f), level)
                is CanonMathItem.Root -> radical(slot(item.radicand, level), level)
                is CanonMathItem.NthRoot -> indexedRadical(slot(item.index, script(level)), slot(item.radicand, level), level)
                is CanonMathItem.Group -> parens(slot(item.inner, level), level)
                is CanonMathItem.Fence -> fence(item.kind, slot(item.inner, level), level)
                is CanonMathItem.Function -> {
                    val arguments = MathBox()
                    item.arguments.forEachIndexed { i, argument ->
                        if (i > 0) arguments.append(text(",", level = level), s * 0.02f)
                        arguments.append(slot(argument, level), if (i > 0) s * 0.2f else 0f)
                    }
                    functionName(item.name, level).append(parens(arguments, level), s * 0.03f)
                }
                is CanonMathItem.LogBase ->
                    logBase(slot(item.base, script(level)), level).append(parens(slot(item.argument, level), level), s * 0.03f)
                is CanonMathItem.Series -> {
                    val below = slot(item.index, script(level))
                    below.append(text("=", level = script(level)), s * 0.04f)
                    below.append(slot(item.from, script(level)), s * 0.04f)
                    series(if (item.product) "Π" else "Σ", below, slot(item.to, script(level)), level)
                        .append(bodied(item.body), s * 0.1f)
                }
                is CanonMathItem.Integral ->
                    integralSign(item.lower?.let { slot(it, script(level)) }, item.upper?.let { slot(it, script(level)) }, level)
                        .append(bodied(item.body), s * 0.1f)
                        .append(differential(variableBox(level), level), s * 0.17f)
                is CanonMathItem.Derivative ->
                    derivativeOperator(variableBox(level + 1), level).append(parens(slot(item.body, level), level), s * 0.1f)
                is CanonMathItem.Limit ->
                    limitOperator(variableBox(script(level)), slot(item.to, script(level)), level).append(bodied(item.body), s * 0.17f)
                is CanonMathItem.Binomial -> parens(stacked(slot(item.n, level + 1), slot(item.r, level + 1), level), level)
                is CanonMathItem.Permutation -> permutation(slot(item.n, script(level)), slot(item.r, script(level)), level)
            }
            val italicGap = if (item is CanonMathItem.Power && previousItalic) s * 0.04f else 0f
            box.append(set, gap + italicGap)
            if (item !is CanonMathItem.Power) {
                previousAscent = set.ascent
                previousItalic = item is CanonMathItem.Symbol && mathIsItalic(mathShown(item.symbol))
            }
        }
        // extra gap after italics
        val last = row.items.lastOrNull()
        val endsItalic = last is CanonMathItem.Symbol && mathIsItalic(mathShown(last.symbol))
        if (row.items.isNotEmpty()) stops += box.width + if (endsItalic) s * 0.1f else s * 0.04f
        val hole = box.atoms.firstOrNull()
        if (row.items.isEmpty() && isSlot && hole != null) {
            // empty slot is caret
            box.carets += MathCaret(row, 0, hole.frame)
        } else {
            stops.forEachIndexed { index, x -> box.carets += MathCaret(row, index, Rect(x, -s * 0.78f, x, s * 0.22f)) }
        }
        return box
    }

    /** Space before [item]: around binary operators and relations, none inside a number, a hair between letters. */
    private fun gap(previous: CanonMathItem?, item: CanonMathItem, row: CanonMathRow, index: Int, s: Float, level: Int): Float {
        if (previous == null) return 0f
        val operatorGap = if (level >= 2) s * 0.12f else s * 0.22f
        fun symbol(item: CanonMathItem?) = (item as? CanonMathItem.Symbol)?.symbol
        if (symbol(previous) in relations || symbol(item) in relations) return if (level >= 2) s * 0.14f else s * 0.28f
        if (symbol(item) in spaced) return operatorGap
        if (symbol(previous) in spaced) {
            // sign after sign: unary
            val before = if (index >= 2) symbol(row.items[index - 2]) else "="
            return if (before != null && (before in spaced || before == "=" || before == ",")) s * 0.04f else operatorGap
        }
        if (item is CanonMathItem.Power) return s * 0.03f
        if (symbol(previous) == ",") return s * 0.2f
        // sin(x) x, (a+b)(a−b)
        if (symbol(previous) == null) return s * 0.1f
        fun digits(item: CanonMathItem) = symbol(item)?.all { it.isDigit() || it == '.' } == true
        return if (digits(previous) && digits(item)) 0f else s * 0.05f
    }
}
