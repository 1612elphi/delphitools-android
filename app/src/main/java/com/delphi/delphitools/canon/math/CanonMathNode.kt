package com.delphi.delphitools.canon.math

/** The brackets of `abs`, `floor` and `ceil`. */
enum class CanonMathFence { Abs, Floor, Ceil }

// ponytail: no Word, Sub, Group, Derivative or Integral nodes and no `present` (result ordering); the parser makes none of them
/** An expression in nerdamer's syntax as a tree. */
sealed interface CanonMathNode {
    data class Number(val digits: String) : CanonMathNode
    data class Symbol(val name: String) : CanonMathNode

    /** A missing operand in unfinished input. */
    data object Hole : CanonMathNode
    data class Negate(val inner: CanonMathNode) : CanonMathNode

    /** Terms; a subtracted term is a [Negate]. */
    data class Sum(val terms: List<CanonMathNode>) : CanonMathNode
    data class Product(val factors: List<CanonMathNode>) : CanonMathNode
    data class Quotient(val top: CanonMathNode, val bottom: CanonMathNode) : CanonMathNode
    data class Power(val base: CanonMathNode, val exponent: CanonMathNode) : CanonMathNode
    data class Call(val name: String, val arguments: List<CanonMathNode>) : CanonMathNode
    data class Factorial(val inner: CanonMathNode) : CanonMathNode
    data class Relation(val op: String, val left: CanonMathNode, val right: CanonMathNode) : CanonMathNode
    data class Items(val items: List<CanonMathNode>) : CanonMathNode

    companion object {
        /**
         * The tree of [text]; null when there is nothing, or when input is left over that does not parse,
         * such as an unopened bracket. An operand still to be typed is a [Hole], a bracket still open is closed.
         */
        fun parse(text: String): CanonMathNode? {
            val tokens = tokenise(text)
            if (tokens.isEmpty()) return null
            val parser = Parser(tokens)
            val node = parser.statement()
            return if (parser.at == tokens.size) node else null
        }
    }
}

private sealed interface Token {
    data class Number(val digits: String) : Token
    data class Name(val name: String) : Token
    data class Op(val char: Char) : Token
}

private val functions = setOf(
    "sin", "cos", "tan", "sec", "csc", "cot", "asin", "acos", "atan", "sinh", "cosh", "tanh",
    "log", "ln", "log10", "exp", "sqrt", "abs",
)

private fun isDigit(c: Char) = c in '0'..'9' || c == '.'

private fun tokenise(text: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var index = 0
    fun scan(test: (Char) -> Boolean): String {
        val start = index
        while (index < text.length && test(text[index])) index++
        return text.substring(start, index)
    }
    while (index < text.length) {
        val c = text[index]
        when {
            c.isWhitespace() -> index++
            isDigit(c) -> tokens += Token.Number(scan(::isDigit))
            c.isLetter() || c == '_' -> tokens += Token.Name(scan { it.isLetter() || it == '_' || it in '0'..'9' })
            else -> {
                index++
                tokens += when (c) {
                    '−' -> Token.Op('-')
                    '×', '·', '⋅' -> Token.Op('*')
                    '÷' -> Token.Op('/')
                    '√' -> Token.Name("sqrt")
                    else -> Token.Op(c)
                }
            }
        }
    }
    return tokens
}

private class Parser(val tokens: List<Token>) {
    var at = 0

    private val peek get() = tokens.getOrNull(at)

    private fun take(op: Char): Boolean {
        if (peek != Token.Op(op)) return false
        at++
        return true
    }

    /** Starts an operand that may follow another with no sign between: `2x`, `2(x+1)`. */
    private val startsImplicitFactor
        get() = when (val p = peek) {
            is Token.Number, is Token.Name -> true
            is Token.Op -> p.char == '(' || p.char == '['
            null -> false
        }

    fun statement(): CanonMathNode {
        val left = expression()
        return if (take('=')) CanonMathNode.Relation("=", left, expression()) else left
    }

    private fun expression(): CanonMathNode {
        val terms = mutableListOf(term())
        while (true) {
            if (take('+')) terms += term()
            else if (take('-')) terms += CanonMathNode.Negate(term())
            else break
        }
        return if (terms.size == 1) terms[0] else CanonMathNode.Sum(terms)
    }

    private fun term(): CanonMathNode {
        var node = unary()
        while (true) {
            node = when {
                take('*') -> multiply(node, unary())
                take('/') -> CanonMathNode.Quotient(node, unary())
                startsImplicitFactor -> multiply(node, power())
                else -> return node
            }
        }
    }

    private fun multiply(left: CanonMathNode, right: CanonMathNode) =
        if (left is CanonMathNode.Product) CanonMathNode.Product(left.factors + right)
        else CanonMathNode.Product(listOf(left, right))

    private fun unary(): CanonMathNode {
        if (take('-')) return CanonMathNode.Negate(unary())
        if (take('+')) return unary()
        return power()
    }

    // right associative
    private fun power(): CanonMathNode {
        val base = postfix()
        return if (take('^')) CanonMathNode.Power(base, unary()) else base
    }

    private fun postfix(): CanonMathNode {
        var node = primary()
        while (take('!')) node = CanonMathNode.Factorial(node)
        return node
    }

    private fun primary(): CanonMathNode {
        when (val p = peek) {
            is Token.Number -> {
                at++
                return CanonMathNode.Number(p.digits)
            }
            is Token.Name -> {
                at++
                if (take('(')) return CanonMathNode.Call(p.name, arguments(')'))
                if (p.name in functions && startsImplicitFactor) return CanonMathNode.Call(p.name, listOf(power()))
                return CanonMathNode.Symbol(p.name)
            }
            Token.Op('(') -> {
                at++
                val inner = statement()
                take(')')
                return inner
            }
            Token.Op('[') -> {
                at++
                return CanonMathNode.Items(arguments(']'))
            }
            Token.Op('|') -> {
                at++
                val inner = expression()
                take('|')
                return CanonMathNode.Call("abs", listOf(inner))
            }
            else -> return CanonMathNode.Hole
        }
    }

    private fun arguments(closing: Char): List<CanonMathNode> {
        if (take(closing)) return emptyList()
        val items = mutableListOf(statement())
        while (take(',')) items += statement()
        take(closing)
        return items
    }
}
