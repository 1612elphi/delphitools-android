package com.delphi.delphitools.tools.calculators

import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class SciConstantCategory(val title: String) { Mathematical("Mathematical"), Physical("Physical"), Chemical("Chemical") }

class SciConstant(val name: String, val symbol: String, val value: Double, val unit: String? = null, val category: SciConstantCategory = SciConstantCategory.Mathematical)

/** dt-core ScientificCalculator's evaluator and number format. */
object SciCalcEngine {
    // web lib/math-constants.ts
    val constants = listOf(
        SciConstant("Pi", "π", PI),
        SciConstant("Euler's number", "e", E),
        SciConstant("Golden ratio", "φ", 1.618033988749895),
        SciConstant("Square root of 2", "√2", sqrt(2.0)),
        SciConstant("Square root of 3", "√3", 1.7320508075688772),
        SciConstant("Natural log of 2", "ln(2)", ln(2.0)),
        SciConstant("Natural log of 10", "ln(10)", ln(10.0)),
        SciConstant("Euler-Mascheroni", "γ", 0.5772156649015329),
        SciConstant("Speed of light", "c", 299_792_458.0, "m/s", SciConstantCategory.Physical),
        SciConstant("Planck constant", "h", 6.62607015e-34, "J·s", SciConstantCategory.Physical),
        SciConstant("Gravitational constant", "G", 6.6743e-11, "m³/(kg·s²)", SciConstantCategory.Physical),
        SciConstant("Boltzmann constant", "kB", 1.380649e-23, "J/K", SciConstantCategory.Physical),
        SciConstant("Elementary charge", "e", 1.602176634e-19, "C", SciConstantCategory.Physical),
        SciConstant("Electron mass", "mₑ", 9.1093837015e-31, "kg", SciConstantCategory.Physical),
        SciConstant("Proton mass", "mₚ", 1.67262192369e-27, "kg", SciConstantCategory.Physical),
        SciConstant("Standard gravity", "g", 9.80665, "m/s²", SciConstantCategory.Physical),
        SciConstant("Avogadro constant", "Nₐ", 6.02214076e23, "mol⁻¹", SciConstantCategory.Chemical),
        SciConstant("Gas constant", "R", 8.314462618, "J/(mol·K)", SciConstantCategory.Chemical),
        SciConstant("Faraday constant", "F", 96485.33212, "C/mol", SciConstantCategory.Chemical),
    )

    /** C's `%g`, or `%.10g` between 1e-10 and 1e15. */
    fun format(value: Double): String {
        if (value == 0.0) return "0"
        if (abs(value) >= 1e15 || abs(value) < 1e-10) return AlgebraFormat.significant(value, 6)
        return AlgebraFormat.significant(value, 10)
    }

    /** NaN for text that does not parse to the end. */
    fun evaluate(expression: String, degrees: Boolean): Double = Parser(expression, degrees).run()

    private class Parser(text: String, val degrees: Boolean) {
        val chars = text.toCharArray()
        var pos = 0

        fun peek(): Char? = chars.getOrNull(pos)

        fun advance(): Char? = chars.getOrNull(pos)?.also { pos++ }

        fun skipSpaces() {
            while (peek() == ' ') pos++
        }

        fun run(): Double {
            val value = addSub()
            skipSpaces()
            return if (pos == chars.size) value else Double.NaN
        }

        fun number(): Double? {
            skipSpaces()
            val text = StringBuilder()
            while (peek()?.let { it.isDigit() || it == '.' } == true) text.append(advance())
            if (peek() == 'e' || peek() == 'E') {
                text.append(advance())
                if (peek() == '+' || peek() == '-') text.append(advance())
                while (peek()?.isDigit() == true) text.append(advance())
            }
            return text.toString().takeIf { s -> s.any { it.isDigit() } }?.toDoubleOrNull()
        }

        fun identifier(): String {
            val name = StringBuilder()
            while (peek()?.isLetter() == true) name.append(advance())
            return name.toString()
        }

        private fun radians(value: Double) = if (degrees) value * PI / 180 else value

        private fun fromRadians(value: Double) = if (degrees) value * 180 / PI else value

        private fun clean(value: Double) = when {
            abs(value) < 1e-12 -> 0.0
            abs(value - 1) < 1e-12 -> 1.0
            abs(value + 1) < 1e-12 -> -1.0
            abs(value - 0.5) < 1e-12 -> 0.5
            abs(value + 0.5) < 1e-12 -> -0.5
            else -> value
        }

        private fun factorial(n: Double): Double {
            if (n !in 0.0..170.0 || Math.rint(n) != n) return Double.NaN
            var result = 1.0
            for (i in 2..n.toInt()) result *= i
            return result
        }

        fun factor(): Double {
            skipSpaces()
            val c = peek() ?: return Double.NaN
            if (c == '(') {
                advance()
                val value = addSub()
                if (peek() == ')') advance()
                return value
            }
            if (c.isLetter()) {
                val name = identifier()
                when (name) {
                    "pi", "π" -> return PI
                    "e" -> return E
                }
                if (peek() != '(') return Double.NaN
                advance()
                val arg = addSub()
                if (peek() == ')') advance()
                return when (name) {
                    "sin" -> clean(sin(radians(arg)))
                    "cos" -> clean(cos(radians(arg)))
                    "tan" -> {
                        val rad = radians(arg)
                        val cosine = cos(rad)
                        if (abs(cosine) < 1e-12) Double.POSITIVE_INFINITY else clean(sin(rad) / cosine)
                    }
                    "asin" -> fromRadians(asin(arg))
                    "acos" -> fromRadians(acos(arg))
                    "atan" -> fromRadians(atan(arg))
                    "sqrt" -> sqrt(arg)
                    "log" -> log10(arg)
                    "ln" -> ln(arg)
                    "abs" -> abs(arg)
                    // root(n, x)
                    "root" -> if (peek() == ',') {
                        advance()
                        val x = addSub()
                        if (peek() == ')') advance()
                        x.pow(1.0 / arg)
                    } else {
                        sqrt(arg)
                    }
                    else -> Double.NaN
                }
            }
            if (c.isDigit() || c == '.') return number() ?: Double.NaN
            if (c == '|') {
                advance()
                val value = addSub()
                if (peek() == '|') advance()
                return abs(value)
            }
            return Double.NaN
        }

        fun postfix(): Double {
            var value = factor()
            skipSpaces()
            if (peek() == '!') {
                advance()
                value = factorial(value)
            }
            return value
        }

        fun power(): Double {
            val base = postfix()
            skipSpaces()
            if (peek() != '^') return base
            advance()
            return base.pow(unary())
        }

        /** Binds looser than ^: -2^2 is -4. */
        fun unary(): Double {
            skipSpaces()
            if (peek() != '-' && peek() != '−') return power()
            advance()
            return -unary()
        }

        fun mulDiv(): Double {
            var left = unary()
            while (true) {
                skipSpaces()
                when (peek()) {
                    '×', '*' -> {
                        advance()
                        left *= unary()
                    }
                    '÷', '/' -> {
                        advance()
                        left /= unary()
                    }
                    '%' -> {
                        advance()
                        left %= unary()
                    }
                    else -> return left
                }
            }
        }

        fun addSub(): Double {
            var left = mulDiv()
            while (true) {
                skipSpaces()
                when (peek()) {
                    '+' -> {
                        advance()
                        left += mulDiv()
                    }
                    '-', '−' -> {
                        advance()
                        left -= mulDiv()
                    }
                    else -> return left
                }
            }
        }
    }
}

enum class SciKind { Digit, Factor, Other }

/** dt-core SciCalcEntry: how a key press changes the expression text, apart from the evaluator. */
object SciCalcEntry {
    private val factors = setOf("(", "sin", "cos", "tan", "asin", "acos", "atan", "log", "ln", "π", "e", "√", "ʸ√x", "10ˣ", "eˣ", "Ans", "Const")

    private fun Char.numeric() = isDigit() || this == '.'

    fun kind(key: String): SciKind = when {
        key.length == 1 && key[0].numeric() -> SciKind.Digit
        key in factors -> SciKind.Factor
        else -> SciKind.Other
    }

    /** The evaluator's text for a key. */
    fun text(key: String): String = when (key) {
        "x²" -> "^2"
        "xʸ" -> "^"
        "√" -> "sqrt("
        "ʸ√x" -> "root("
        "10ˣ" -> "10^"
        "eˣ" -> "e^"
        "EE" -> "e"
        "|x|" -> "|"
        "mod" -> "%"
        "−" -> "-"
        "sin", "cos", "tan", "asin", "acos", "atan", "log", "ln" -> "$key("
        else -> key
    }

    /** Appends [text], with a multiplication sign where two values would otherwise meet: 2π is 2×π. */
    fun join(expression: String, text: String, kind: SciKind): String {
        val last = expression.lastOrNull()
        if (last == null || kind == SciKind.Other) return expression + text
        val before = expression.dropLast(1).lastOrNull()
        // e after digit: EE
        val euler = last == 'e' && before?.numeric() != true
        val closes = last == ')' || last == 'π' || last == '!' || euler
        val ends = closes || last.numeric()
        return expression + (if (if (kind == SciKind.Digit) closes else ends) "×" else "") + text
    }

    /** Changes the sign of the number being typed: 3+4 becomes 3−4, 3×4 becomes 3×−4. Null when the expression does not end in a number. */
    fun toggledSign(expression: String): String? {
        val characters = expression.toMutableList()
        if (characters.lastOrNull()?.numeric() != true) return null
        var start = characters.size
        while (start > 0 && characters[start - 1].numeric()) start--
        if (start == 0) return "-$expression"
        when (characters[start - 1]) {
            '-' -> {
                val lead = characters.getOrNull(start - 2)
                val exponent = lead == 'e' && start >= 3 && characters[start - 3].numeric()
                if (lead == null || exponent || lead in "+-×÷^(%,|") characters.removeAt(start - 1) else characters[start - 1] = '+'
            }
            '+' -> characters[start - 1] = '-'
            else -> characters.add(start, '-')
        }
        return characters.joinToString("")
    }

    fun openParens(expression: String) = maxOf(0, expression.count { it == '(' } - expression.count { it == ')' })

    private val raised = mapOf('0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '−' to '⁻')

    /** `%g` output with a real minus, and a power of ten written as ×10 with a raised exponent. */
    fun written(text: String): String {
        val (mantissa, exponent) = parts(text)
        exponent ?: return mantissa
        return "$mantissa × 10" + exponent.map { raised[it] ?: it }.joinToString("")
    }

    /** Splits `%g` output at its exponent: "1.5e+15" is 1.5 and 15. Minus signs become the real minus. */
    fun parts(text: String): Pair<String, String?> {
        val halves = text.split('e', limit = 2)
        val power = halves.getOrNull(1)?.removePrefix("+")?.toIntOrNull()
        if (halves.size != 2 || power == null) return text.replace('-', '−') to null
        return halves[0].replace('-', '−') to power.toString().replace('-', '−')
    }

    /** Characters followed by a thousands gap: 1234567.5 has gaps after 1 and 4. */
    fun groupBreaks(mantissa: String): List<Int> {
        val lead = if (mantissa.firstOrNull() == '−') 1 else 0
        val digits = mantissa.drop(lead).takeWhile { it.isDigit() }.length
        if (digits < 5) return emptyList()
        return (0 until digits - 1).filter { (digits - 1 - it) % 3 == 0 }.map { it + lead }
    }
}

enum class SciPieceKind { Number, Function, Operation, Bracket, Ghost }

data class SciPiece(val text: String, val kind: SciPieceKind, val raised: Boolean = false)

/** dt-core SciCalcSyntax: the expression as a calculator prints it, real minus and times signs, √ for sqrt, exponents raised. */
object SciCalcSyntax {
    fun pieces(raw: String): List<SciPiece> {
        fun numeric(index: Int) = raw.getOrNull(index)?.let { it.isDigit() || it == '.' } == true
        fun run(start: Int, test: (Int) -> Boolean): Int {
            var end = start
            while (end < raw.length && test(end)) end++
            return end
        }
        val pieces = mutableListOf<SciPiece>()
        var index = 0
        while (index < raw.length) {
            val character = raw[index]
            if (numeric(index)) {
                val end = run(index, ::numeric)
                pieces += SciPiece(raw.substring(index, end), SciPieceKind.Number)
                index = end
            } else if (character.isLetter()) {
                val end = run(index) { raw[it].isLetter() }
                val word = raw.substring(index, end)
                pieces += when {
                    word == "e" && numeric(index - 1) -> SciPiece("E", SciPieceKind.Operation)
                    word == "e" || word == "π" -> SciPiece(word, SciPieceKind.Number)
                    else -> SciPiece(mapOf("sqrt" to "√", "root" to "ʸ√")[word] ?: word, SciPieceKind.Function)
                }
                index = end
            } else if (character == '^' && (numeric(index + 1) || raw.getOrNull(index + 1) == '-' && numeric(index + 2))) {
                val end = run(index + if (raw[index + 1] == '-') 2 else 1, ::numeric)
                pieces += SciPiece(raw.substring(index + 1, end).replace('-', '−'), SciPieceKind.Number, raised = true)
                index = end
            } else {
                val text = mapOf('-' to "−", '*' to "×", '/' to "÷", '%' to " mod ")[character] ?: character.toString()
                pieces += SciPiece(text, if (character in "()|,") SciPieceKind.Bracket else SciPieceKind.Operation)
                index++
            }
        }
        return pieces
    }
}
