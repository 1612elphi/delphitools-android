package com.delphi.delphitools.tools.calculators

import com.delphi.delphitools.canon.math.CanonMathExpression
import com.delphi.delphitools.canon.math.CanonMathNode
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.acosh
import kotlin.math.asin
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.atanh
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

/** Half away from zero, as Swift's `rounded()`. */
internal fun Double.roundedAway(): Double = if (this < 0) -floor(-this + 0.5) else floor(this + 0.5)

// dt-core MathExpression: tokeniser, recursive descent parser and evaluator

internal sealed interface MathToken {
    data class Number(val value: Double) : MathToken
    data object Variable : MathToken
    data class Op(val char: Char) : MathToken
    data object LeftParen : MathToken
    data object RightParen : MathToken
    data class Function(val name: String) : MathToken
    data class Constant(val name: String) : MathToken
    data object Comma : MathToken
    data class Unknown(val text: String) : MathToken
}

private sealed interface MathNode {
    data class Number(val value: Double) : MathNode
    data object Variable : MathNode
    data class Binary(val left: MathNode, val op: Char, val right: MathNode) : MathNode
    data class Negate(val operand: MathNode) : MathNode
    data class Function(val name: String, val arguments: List<MathNode>) : MathNode
}

object MathTokeniser {
    /** Functions and how many arguments each takes; `log` is common with one, `log(a, b)` is to base b. */
    val functions: Map<String, IntRange> = buildMap {
        listOf(
            "sin", "cos", "tan", "sec", "csc", "cot", "asin", "acos", "atan", "asec", "acsc", "acot",
            "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
            "sqrt", "cbrt", "abs", "ln", "exp", "floor", "ceil", "round",
        ).forEach { put(it, 1..1) }
        put("log", 1..2)
        listOf("nthroot", "mod", "min", "max").forEach { put(it, 2..2) }
    }

    /** Whether [input] mentions x. */
    fun hasVariable(input: String) = tokenise(input).any { it == MathToken.Variable }

    internal fun tokenise(input: String): List<MathToken> {
        val tokens = mutableListOf<MathToken>()
        val chars = input.lowercase()
        var i = 0
        while (i < chars.length) {
            val c = chars[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || (c == '.' && i + 1 < chars.length && chars[i + 1].isDigit()) -> {
                    val start = i
                    i++
                    while (i < chars.length && (chars[i].isDigit() || chars[i] == '.')) i++
                    val text = chars.substring(start, i)
                    tokens += text.toDoubleOrNull()?.takeIf { text.count { it == '.' } <= 1 }?.let { MathToken.Number(it) } ?: MathToken.Unknown(text)
                }
                c in "+-*/^" -> {
                    tokens += MathToken.Op(c)
                    i++
                }
                c == '(' -> tokens += MathToken.LeftParen.also { i++ }
                c == ')' -> tokens += MathToken.RightParen.also { i++ }
                c == ',' -> tokens += MathToken.Comma.also { i++ }
                c.isLetter() -> {
                    val start = i
                    while (i < chars.length && chars[i].isLetter()) i++
                    val word = chars.substring(start, i)
                    tokens += when {
                        word in functions -> MathToken.Function(word)
                        word == "pi" || word == "e" -> MathToken.Constant(word)
                        word == "x" -> MathToken.Variable
                        else -> MathToken.Unknown(word)
                    }
                }
                else -> {
                    tokens += MathToken.Unknown(c.toString())
                    i++
                }
            }
        }
        return implicitMultiplication(tokens)
    }

    /** `*` where multiplication is implied: `2x`, `2(`, `)(`, `x(`, `)2`, `pi x`. */
    private fun implicitMultiplication(tokens: List<MathToken>): List<MathToken> {
        val result = mutableListOf<MathToken>()
        for (token in tokens) {
            val previous = result.lastOrNull()
            val after = previous is MathToken.Number || previous == MathToken.Variable || previous is MathToken.Constant || previous == MathToken.RightParen
            val before = token is MathToken.Number || token == MathToken.Variable || token is MathToken.Constant || token == MathToken.LeftParen || token is MathToken.Function
            if (after && before) result += MathToken.Op('*')
            result += token
        }
        return result
    }
}

private class MathParser(private val tokens: List<MathToken>) {
    private var pos = 0
    private val current get() = tokens.getOrNull(pos)

    fun parse(): MathNode? = expression()?.takeIf { pos == tokens.size }

    private fun expression(): MathNode? {
        var left = term() ?: return null
        while (true) {
            val op = (current as? MathToken.Op)?.char?.takeIf { it == '+' || it == '-' } ?: return left
            pos++
            left = MathNode.Binary(left, op, term() ?: return null)
        }
    }

    private fun term(): MathNode? {
        var left = power() ?: return null
        while (true) {
            val op = (current as? MathToken.Op)?.char?.takeIf { it == '*' || it == '/' } ?: return left
            pos++
            left = MathNode.Binary(left, op, power() ?: return null)
        }
    }

    // right associative
    private fun power(): MathNode? {
        val base = unary() ?: return null
        if (current != MathToken.Op('^')) return base
        pos++
        return MathNode.Binary(base, '^', power() ?: return null)
    }

    private fun unary(): MathNode? {
        if (current == MathToken.Op('-')) {
            pos++
            return MathNode.Negate(unary() ?: return null)
        }
        return primary()
    }

    private fun primary(): MathNode? {
        when (val token = current ?: return null) {
            is MathToken.Number -> {
                pos++
                return MathNode.Number(token.value)
            }
            MathToken.Variable -> {
                pos++
                return MathNode.Variable
            }
            is MathToken.Constant -> {
                pos++
                return MathNode.Number(if (token.name == "pi") PI else E)
            }
            is MathToken.Function -> {
                pos++
                if (current != MathToken.LeftParen) return null
                pos++
                val arguments = mutableListOf<MathNode>()
                do {
                    if (arguments.isNotEmpty()) pos++
                    arguments += expression() ?: return null
                } while (current == MathToken.Comma)
                if (current != MathToken.RightParen || MathTokeniser.functions[token.name]?.contains(arguments.size) != true) return null
                pos++
                return MathNode.Function(token.name, arguments)
            }
            MathToken.LeftParen -> {
                pos++
                val inner = expression() ?: return null
                if (current != MathToken.RightParen) return null
                pos++
                return inner
            }
            else -> return null
        }
    }
}

private object MathEvaluator {
    fun evaluate(node: MathNode, x: Double): Double = when (node) {
        is MathNode.Number -> node.value
        MathNode.Variable -> x
        is MathNode.Negate -> -evaluate(node.operand, x)
        is MathNode.Binary -> {
            val l = evaluate(node.left, x)
            val r = evaluate(node.right, x)
            when (node.op) {
                '+' -> l + r
                '-' -> l - r
                '*' -> l * r
                '/' -> l / r
                '^' -> l.pow(r)
                else -> Double.NaN
            }
        }
        is MathNode.Function -> apply(node.name, node.arguments.map { evaluate(it, x) })
    }

    fun apply(name: String, values: List<Double>): Double {
        val a = values.firstOrNull() ?: return Double.NaN
        val b = values.getOrElse(1) { Double.NaN }
        return when (name) {
            "sin" -> sin(a)
            "cos" -> cos(a)
            "tan" -> tan(a)
            "sec" -> 1 / cos(a)
            "csc" -> 1 / sin(a)
            "cot" -> cos(a) / sin(a)
            "asin" -> asin(a)
            "acos" -> acos(a)
            "atan" -> atan(a)
            "asec" -> acos(1 / a)
            "acsc" -> asin(1 / a)
            // nerdamer's acot, continuous through 0
            "acot" -> PI / 2 - atan(a)
            "sinh" -> sinh(a)
            "cosh" -> cosh(a)
            "tanh" -> tanh(a)
            "asinh" -> asinh(a)
            "acosh" -> acosh(a)
            "atanh" -> atanh(a)
            "sqrt" -> sqrt(a)
            "cbrt" -> Math.cbrt(a)
            "nthroot" -> if (a < 0 && abs(b % 2) == 1.0) -(-a).pow(1 / b) else a.pow(1 / b)
            "abs" -> abs(a)
            "log" -> if (values.size > 1) ln(a) / ln(b) else log10(a)
            "ln" -> ln(a)
            "exp" -> exp(a)
            "floor" -> floor(a)
            "ceil" -> ceil(a)
            "round" -> a.roundedAway()
            // nerdamer's mod is JavaScript's %: the dividend's sign
            "mod" -> a % b
            "min" -> if (a.isNaN() || b.isNaN()) Double.NaN else minOf(a, b)
            "max" -> if (a.isNaN() || b.isNaN()) Double.NaN else maxOf(a, b)
            else -> Double.NaN
        }
    }
}

/** dt-core MathExpression: compiled once, evaluated at any x. */
class MathExpression(val source: String) {
    private val ast: MathNode? = MathParser(MathTokeniser.tokenise(source)).parse()

    /** Parses, and is not infinite at x = 1; NaN there is fine (sqrt(-1)). */
    val isValid: Boolean get() = ast != null && !evaluate(1.0).isInfinite()

    fun evaluate(x: Double): Double = ast?.let { MathEvaluator.evaluate(it, x) } ?: Double.NaN
}

enum class GraphRelation(val symbol: String) {
    Equal("="),
    LessThan("<"),
    GreaterThan(">"),
    LessOrEqual("≤"),
    GreaterOrEqual("≥"),
}

/** Between the keypad's syntax (nerdamer: `log` natural, `log10` common) and MathExpression's (`ln` natural, `log` common). */
object GraphSyntax {
    private val logNames = Regex("(?<![A-Za-z])(ln|log)(?![A-Za-z0-9])")

    /** Keypad text for MathExpression text, with the implied products written out. */
    fun typed(plotted: String): String {
        val typed = logNames.replace(plotted) { if (it.value == "ln") "log" else "log10" }
        return CanonMathExpression(typed).text
    }

    /** MathExpression text for keypad text, or null for what it cannot plot: unfinished input, other letters, calculus. */
    fun plotted(text: String): String? {
        if (text.isBlank()) return null
        return plotted(CanonMathNode.parse(text) ?: return null)
    }

    private fun wrap(node: CanonMathNode) = plotted(node)?.let { "($it)" }

    private fun all(nodes: List<CanonMathNode>, separator: String): String? {
        val parts = nodes.mapNotNull(::wrap)
        return if (parts.size == nodes.size && parts.isNotEmpty()) parts.joinToString(separator) else null
    }

    private fun plotted(node: CanonMathNode): String? = when (node) {
        is CanonMathNode.Number -> node.digits.takeIf { it.toDoubleOrNull() != null && it.count { c -> c == '.' } <= 1 }
        is CanonMathNode.Symbol -> node.name.takeIf { it in setOf("x", "pi", "e") }
        is CanonMathNode.Negate -> wrap(node.inner)?.let { "-$it" }
        is CanonMathNode.Sum -> all(node.terms, "+")
        is CanonMathNode.Product -> all(node.factors, "*")
        is CanonMathNode.Quotient -> all(listOf(node.top, node.bottom), "/")
        is CanonMathNode.Power -> all(listOf(node.base, node.exponent), "^")
        is CanonMathNode.Call -> {
            val parts = all(node.arguments, ",")
            // each argument is bracketed already
            fun call(name: String) = if (node.arguments.size == 1) name + parts else "$name($parts)"
            when {
                parts == null -> null
                node.name == "log" && node.arguments.size == 1 -> call("ln")
                node.name == "log10" && node.arguments.size == 1 -> call("log")
                // not keypad syntax: ln, and log10 with a base
                node.name == "ln" || node.name == "log10" -> null
                MathTokeniser.functions[node.name]?.contains(node.arguments.size) == true -> call(node.name)
                else -> null
            }
        }
        else -> null
    }
}

/** dt-ios GraphCurve: a function as the keypad writes it and as it is plotted. */
data class GraphCurve(
    val id: Long,
    val text: String,
    val colour: Int,
    val relation: GraphRelation = GraphRelation.Equal,
    val isVisible: Boolean = true,
) {
    private val plotted = GraphSyntax.plotted(text)
    val expression = MathExpression(plotted ?: "")
    val isValid = plotted != null && GraphPlot.isValid(expression)
    val isEmpty get() = text.isBlank()

    /** Typed text that does not parse; an empty field is not an error. */
    val isInvalid get() = !isEmpty && !isValid
    val isPlotted get() = isVisible && isValid && !isEmpty

    companion object {
        fun name(index: Int) = "y" + (index + 1).toString().map { ('₀' + (it - '0')) }.joinToString("")
    }
}

/** Visible part of the plane, in plane units. */
data class GraphWindow(val xMin: Double, val xMax: Double, val yMin: Double, val yMax: Double) {
    val spanX get() = xMax - xMin
    val spanY get() = yMax - yMin

    /** Same x range, y at the x scale around its centre. */
    fun squared(width: Double, height: Double): GraphWindow {
        val half = spanX * height / maxOf(width, 1.0) / 2
        val centre = (yMin + yMax) / 2
        return GraphWindow(xMin, xMax, centre - half, centre + half)
    }

    /** A resized plot keeps its centre and its units per point: a shorter plot shows less, it does not squash. */
    fun resized(oldWidth: Double, oldHeight: Double, newWidth: Double, newHeight: Double): GraphWindow {
        if (oldWidth <= 0 || oldHeight <= 0) return this
        val halfX = spanX * newWidth / oldWidth / 2
        val halfY = spanY * newHeight / oldHeight / 2
        val cx = (xMin + xMax) / 2
        val cy = (yMin + yMax) / 2
        return GraphWindow(cx - halfX, cx + halfX, cy - halfY, cy + halfY)
    }

    /** Scales both spans by [factor] about a plane point, which stays where it is on screen. */
    fun zoomed(factor: Double, x: Double, y: Double): GraphWindow {
        val f = minOf(maxOf(factor, SPAN_MIN / spanX, SPAN_MIN / spanY), SPAN_MAX / spanX, SPAN_MAX / spanY)
        return GraphWindow(x - (x - xMin) * f, x + (xMax - x) * f, y - (y - yMin) * f, y + (yMax - y) * f)
    }

    fun moved(dx: Double, dy: Double) = GraphWindow(xMin + dx, xMax + dx, yMin + dy, yMax + dy)

    companion object {
        /** Narrowest and widest span a pinch reaches. */
        const val SPAN_MIN = 1e-4
        const val SPAN_MAX = 1e6

        val standard = GraphWindow(-10.0, 10.0, -10.0, 10.0)
        val trig = GraphWindow(-2 * PI, 2 * PI, -4.0, 4.0)
        val decimal = GraphWindow(-1.0, 1.0, -1.0, 1.0)

        /** x from -10 to 10, y around 0 at the same scale. */
        fun square(width: Double, height: Double): GraphWindow {
            val half = 10 * height / maxOf(width, 1.0)
            return GraphWindow(-10.0, 10.0, -half, half)
        }

        /** Centres move straight, spans change geometrically, so a zoom out and back in reads as one motion. */
        fun blend(a: GraphWindow, b: GraphWindow, t: Double): GraphWindow {
            val cx = (a.xMin + a.xMax) / 2 + ((b.xMin + b.xMax) / 2 - (a.xMin + a.xMax) / 2) * t
            val cy = (a.yMin + a.yMax) / 2 + ((b.yMin + b.yMax) / 2 - (a.yMin + a.yMax) / 2) * t
            val sx = a.spanX * (b.spanX / a.spanX).pow(t) / 2
            val sy = a.spanY * (b.spanY / a.spanY).pow(t) / 2
            return GraphWindow(cx - sx, cx + sx, cy - sy, cy + sy)
        }
    }
}

object GraphPlot {
    /** The web tool's curve colours. */
    val palette = listOf(0x3B82F6, 0xEF4444, 0x22C55E, 0xF59E0B, 0x8B5CF6, 0xEC4899, 0x06B6D4, 0xF97316)

    /** 1, 2 or 5 times a power of ten, at least [value]. */
    fun niceCeil(value: Double): Double {
        if (value <= 0 || !value.isFinite()) return 1.0
        val magnitude = 10.0.pow(floor(log10(value)))
        val norm = value / magnitude
        return when {
            norm <= 1 + 1e-9 -> 1.0
            norm <= 2 + 1e-9 -> 2.0
            norm <= 5 + 1e-9 -> 5.0
            else -> 10.0
        } * magnitude
    }

    /** 1, 2 or 5 times a power of ten, at most [value]. */
    fun niceFloor(value: Double): Double {
        if (value <= 0 || !value.isFinite()) return 1.0
        val magnitude = 10.0.pow(floor(log10(value)))
        val norm = value / magnitude
        return when {
            norm >= 5 - 1e-9 -> 5.0
            norm >= 2 - 1e-9 -> 2.0
            else -> 1.0
        } * magnitude
    }

    /** Minor divisions of a major step: fifths of a 1 or a 5, quarters of a 2. */
    fun minorDivisions(step: Double): Int {
        val norm = step / 10.0.pow(floor(log10(step)))
        return if (abs(norm - 2) < 1e-6) 4 else 5
    }

    /** Multiples of [step] from [min] to [max], as whole counts of [step]. */
    fun ticks(min: Double, max: Double, step: Double): LongRange {
        if (step <= 0 || !min.isFinite() || !max.isFinite() || (max - min) / step >= 4000) return LongRange.EMPTY
        return ceil(min / step).toLong()..floor(max / step).toLong()
    }

    private fun minus(text: String) = text.replace('-', '−')

    private fun plain(value: BigDecimal): String = value.stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }

    /** A tick at [count] steps, to the step's decimals, with a true minus sign. */
    fun tickLabel(count: Long, step: Double): String {
        val decimals = if (step < 1) ceil(-log10(step) - 1e-9).toInt() else 0
        return minus(plain(BigDecimal(count * step).setScale(decimals, RoundingMode.HALF_EVEN)))
    }

    /** At most four decimals, no trailing zeros, exponent notation at the extremes. */
    fun format(value: Double): String {
        if (!value.isFinite()) return "—"
        val size = abs(value)
        if (size != 0.0 && (size < 1e-4 || size >= 1e9)) {
            val rounded = BigDecimal(value).round(MathContext(4, RoundingMode.HALF_EVEN))
            val exponent = rounded.precision() - rounded.scale() - 1
            return minus(plain(rounded.movePointLeft(exponent)) + "E" + exponent)
        }
        val rounded = (value * 1e4).roundedAway() / 1e4
        return minus(plain(BigDecimal(if (rounded == 0.0) 0.0 else rounded).setScale(4, RoundingMode.HALF_EVEN)))
    }

    private val decimalNumber = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")

    /** A typed number: decimal comma accepted, and a constant expression such as `pi/2` or `-2e`. */
    fun number(text: String): Double? {
        val trimmed = text.trim().replace('−', '-')
        val comma = trimmed.replace(',', '.')
        if (decimalNumber.matches(comma)) return comma.toDouble().takeIf { it.isFinite() }
        if (trimmed.isEmpty() || MathTokeniser.hasVariable(trimmed)) return null
        return MathExpression(trimmed).evaluate(0.0).takeIf { it.isFinite() }
    }

    /** A parsed expression; isValid alone rejects one that is infinite at x = 1, such as `1/(x-1)`. */
    fun isValid(expression: MathExpression) = expression.isValid || expression.evaluate(1.0).isInfinite()

    /**
     * The curve as unbroken runs of points (x, y pairs), split where it is undefined and across vertical asymptotes.
     * [x] maps a sample's screen x to the plane; [y] maps a plane y to the screen.
     */
    fun runs(
        samples: Int,
        minX: Float,
        maxX: Float,
        spanY: Double,
        x: (Float) -> Double,
        y: (Double) -> Float,
        clampLow: Float,
        clampHigh: Float,
        f: (Double) -> Double,
    ): List<FloatArray> {
        val runs = mutableListOf<FloatArray>()
        val count = maxOf(samples, 1)
        val run = FloatArray((count + 1) * 2)
        var length = 0
        fun close() {
            if (length > 2) runs += run.copyOf(length)
            length = 0
        }
        var previous = Double.NaN
        for (index in 0..count) {
            val px = minX + (maxX - minX) * index / count
            val value = f(x(px))
            if (!value.isFinite()) {
                close()
                previous = Double.NaN
                continue
            }
            // sign flip: asymptote
            if (previous * value < 0 && abs(value - previous) > spanY) close()
            run[length++] = px
            run[length++] = y(value).coerceIn(clampLow, clampHigh)
            previous = value
        }
        close()
        return runs
    }

    /** Centres at least [gap] apart, each as close to its wish as the others allow, kept inside the range where they fit. */
    fun spread(wishes: List<Float>, gap: Float, low: Float, high: Float): List<Float> {
        val order = wishes.indices.sortedBy { wishes[it] }
        val centres = order.map { wishes[it] }.toMutableList()
        for (index in 1 until centres.size) centres[index] = maxOf(centres[index], centres[index - 1] + gap)
        if (centres.isNotEmpty() && centres.last() > high) {
            centres[centres.size - 1] = high
            for (index in centres.size - 2 downTo 0) centres[index] = minOf(centres[index], centres[index + 1] - gap)
        }
        val result = wishes.toMutableList()
        order.forEachIndexed { slot, index -> result[index] = centres[slot] }
        return result
    }

    /** dt-ios stepTrace: onto the next multiple of the step, or one step on from one. */
    fun steppedTrace(trace: Double, step: Double, direction: Int): Pair<Double, Boolean> {
        val count = trace / step
        val onGrid = abs(count - count.roundedAway()) < 1e-9
        val next = when {
            onGrid -> count.roundedAway() + direction
            direction > 0 -> ceil(count)
            else -> floor(count)
        } * step
        return (next / step).roundedAway() * step to onGrid
    }

    /** A table row's x, [offset] steps from the trace, without drift such as 0.30000000000000004. */
    fun tableX(trace: Double, step: Double, offset: Int): Double {
        val value = trace + offset * step
        val clean = (value / step).roundedAway() * step
        return if (abs(value - clean) < step * 1e-9) clean else value
    }
}
