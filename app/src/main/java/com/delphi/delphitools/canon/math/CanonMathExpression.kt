package com.delphi.delphitools.canon.math

/** A run of items in an edited expression; some items hold rows of their own, their slots. Compared by identity. */
class CanonMathRow(val items: MutableList<CanonMathItem> = mutableListOf())

sealed interface CanonMathItem {
    /** In the order the caret walks them. */
    val slots: List<CanonMathRow>

    /** A digit, a letter, an operator sign, π, e, i or ∞. */
    data class Symbol(val symbol: String) : CanonMathItem {
        override val slots get() = emptyList<CanonMathRow>()
    }

    class Fraction(val top: CanonMathRow, val bottom: CanonMathRow) : CanonMathItem {
        override val slots get() = listOf(top, bottom)
    }

    /** An exponent on the item before it. */
    class Power(val exponent: CanonMathRow) : CanonMathItem {
        override val slots get() = listOf(exponent)
    }

    class Root(val radicand: CanonMathRow) : CanonMathItem {
        override val slots get() = listOf(radicand)
    }

    class NthRoot(val index: CanonMathRow, val radicand: CanonMathRow) : CanonMathItem {
        override val slots get() = listOf(index, radicand)
    }

    class Group(val inner: CanonMathRow) : CanonMathItem {
        override val slots get() = listOf(inner)
    }

    class Fence(val kind: CanonMathFence, val inner: CanonMathRow) : CanonMathItem {
        override val slots get() = listOf(inner)
    }

    /** A function under nerdamer's name (`log` is ln, `log10` is log), one slot per argument. */
    class Function(val name: String, val arguments: List<CanonMathRow>) : CanonMathItem {
        override val slots get() = arguments
    }

    class LogBase(val base: CanonMathRow, val argument: CanonMathRow) : CanonMathItem {
        override val slots get() = listOf(base, argument)
    }

    class Series(
        val product: Boolean,
        val index: CanonMathRow,
        val from: CanonMathRow,
        val to: CanonMathRow,
        val body: CanonMathRow,
    ) : CanonMathItem {
        override val slots get() = listOf(index, from, to, body)
    }

    /** Bounds are null for an indefinite integral. */
    class Integral(val lower: CanonMathRow?, val upper: CanonMathRow?, val body: CanonMathRow) : CanonMathItem {
        override val slots get() = listOfNotNull(lower, upper) + body
    }

    class Derivative(val body: CanonMathRow) : CanonMathItem {
        override val slots get() = listOf(body)
    }

    class Limit(val to: CanonMathRow, val body: CanonMathRow) : CanonMathItem {
        override val slots get() = listOf(to, body)
    }

    class Binomial(val n: CanonMathRow, val r: CanonMathRow) : CanonMathItem {
        override val slots get() = listOf(n, r)
    }

    class Permutation(val n: CanonMathRow, val r: CanonMathRow) : CanonMathItem {
        override val slots get() = listOf(n, r)
    }
}

/** The slot above or below [slot], where the layout has one. */
private fun CanonMathItem.slot(slot: Int, up: Boolean): Int? = when (this) {
    is CanonMathItem.Fraction, is CanonMathItem.Binomial, is CanonMathItem.NthRoot ->
        if (up) (if (slot == 1) 0 else null) else (if (slot == 0) 1 else null)
    is CanonMathItem.LogBase, is CanonMathItem.Limit ->
        if (up) (if (slot == 0) 1 else null) else (if (slot == 1) 0 else null)
    is CanonMathItem.Series -> if (up) (if (slot == 2) null else 2) else (if (slot == 2 || slot == 3) 1 else null)
    is CanonMathItem.Integral -> when {
        lower == null -> null
        up -> if (slot == 1) null else 1
        else -> if (slot == 0) null else 0
    }
    else -> null
}

sealed interface CanonMathKey {
    data class Symbol(val symbol: String) : CanonMathKey

    /** An empty fraction. */
    data object Fraction : CanonMathKey

    /** A fraction over the operand before the caret. */
    data object Divide : CanonMathKey
    data object Power : CanonMathKey
    data object Square : CanonMathKey
    data object Cube : CanonMathKey
    data object Inverse : CanonMathKey
    data object Root : CanonMathKey
    data object CubeRoot : CanonMathKey
    data object NthRoot : CanonMathKey
    data object LogBase : CanonMathKey

    /** The base, then an exponent slot: 10^□, e^□. */
    data class Exponential(val base: String) : CanonMathKey

    /** ×10^□ */
    data object Scientific : CanonMathKey
    data object Open : CanonMathKey

    /** Steps out of the bracket, function or fence the caret is in. */
    data object Close : CanonMathKey
    data class Fence(val kind: CanonMathFence) : CanonMathKey
    data class Function(val name: String, val arity: Int = 1) : CanonMathKey
    data class Series(val product: Boolean) : CanonMathKey
    data class Integral(val bounded: Boolean) : CanonMathKey
    data object Derivative : CanonMathKey
    data object Limit : CanonMathKey
    data object Binomial : CanonMathKey
    data object Permutation : CanonMathKey
    data object Left : CanonMathKey
    data object Right : CanonMathKey
    data object Up : CanonMathKey
    data object Down : CanonMathKey
    data object Delete : CanonMathKey
    data object Clear : CanonMathKey

    /** True when the key opens an empty slot for what is typed next. */
    val opensSlot: Boolean
        get() = when (this) {
            Fraction, Divide, Power, Root, CubeRoot, NthRoot, LogBase, Scientific, Open, Derivative, Limit,
            Binomial, Permutation, is Exponential, is Fence, is Function, is Series, is Integral -> true
            else -> false
        }
}

/** A place the caret can stand: an index in a row. */
data class CanonMathPlace(val row: CanonMathRow, val index: Int)

/**
 * An expression edited in two dimensions, as on a MathPrint calculator: a fraction has numerator and denominator slots,
 * an exponent is raised, a root has a slot under it, brackets grow. It reads and writes the text syntax nerdamer takes.
 */
class CanonMathExpression(text: String, var variable: String = "x") {
    val root = CanonMathRow(items(text).toMutableList())
    var caret = CanonMathPlace(root, root.items.size)

    val text: String get() = text(root, variable)

    fun apply(key: CanonMathKey) {
        val row = caret.row
        val index = caret.index
        fun empty() = CanonMathRow()
        fun symbols(text: String) = CanonMathRow(text.map { CanonMathItem.Symbol(it.toString()) }.toMutableList<CanonMathItem>())
        when (key) {
            is CanonMathKey.Symbol -> {
                row.items.add(index, CanonMathItem.Symbol(key.symbol))
                caret = caret.copy(index = index + 1)
            }
            CanonMathKey.Fraction -> insert(CanonMathItem.Fraction(empty(), empty()), 0)
            CanonMathKey.Divide -> {
                var start = index
                while (start > 0 && !isOperator(row.items[start - 1])) start--
                val top = CanonMathRow(row.items.subList(start, index).toMutableList())
                row.items.subList(start, index).clear()
                caret = CanonMathPlace(row, start)
                insert(CanonMathItem.Fraction(top, empty()), if (top.items.isEmpty()) 0 else 1)
            }
            CanonMathKey.Power -> insert(CanonMathItem.Power(empty()), 0)
            CanonMathKey.Square, CanonMathKey.Cube, CanonMathKey.Inverse -> {
                val exponent = when (key) {
                    CanonMathKey.Square -> "2"
                    CanonMathKey.Cube -> "3"
                    else -> "−1"
                }
                row.items.add(index, CanonMathItem.Power(symbols(exponent)))
                caret = caret.copy(index = index + 1)
            }
            CanonMathKey.Root -> insert(CanonMathItem.Root(empty()), 0)
            CanonMathKey.CubeRoot -> insert(CanonMathItem.NthRoot(symbols("3"), empty()), 1)
            CanonMathKey.NthRoot -> insert(CanonMathItem.NthRoot(empty(), empty()), 0)
            CanonMathKey.LogBase -> insert(CanonMathItem.LogBase(empty(), empty()), 0)
            is CanonMathKey.Exponential -> {
                for (c in key.base) apply(CanonMathKey.Symbol(c.toString()))
                apply(CanonMathKey.Power)
            }
            CanonMathKey.Scientific -> {
                for (symbol in listOf("×", "1", "0")) apply(CanonMathKey.Symbol(symbol))
                apply(CanonMathKey.Power)
            }
            CanonMathKey.Open -> insert(CanonMathItem.Group(empty()), 0)
            is CanonMathKey.Fence -> insert(CanonMathItem.Fence(key.kind, empty()), 0)
            is CanonMathKey.Function -> insert(CanonMathItem.Function(key.name, List(maxOf(1, key.arity)) { empty() }), 0)
            is CanonMathKey.Series -> insert(CanonMathItem.Series(key.product, symbols("k"), empty(), empty(), empty()), 1)
            is CanonMathKey.Integral -> insert(
                if (key.bounded) CanonMathItem.Integral(empty(), empty(), empty()) else CanonMathItem.Integral(null, null, empty()),
                0,
            )
            CanonMathKey.Derivative -> insert(CanonMathItem.Derivative(empty()), 0)
            CanonMathKey.Limit -> insert(CanonMathItem.Limit(empty(), empty()), 0)
            CanonMathKey.Binomial -> insert(CanonMathItem.Binomial(empty(), empty()), 0)
            CanonMathKey.Permutation -> insert(CanonMathItem.Permutation(empty(), empty()), 0)
            CanonMathKey.Close -> {
                var current = row
                while (true) {
                    val found = parent(current) ?: break
                    when (found.row.items[found.index]) {
                        is CanonMathItem.Group, is CanonMathItem.Function, is CanonMathItem.Fence,
                        is CanonMathItem.LogBase, is CanonMathItem.Derivative -> {
                            caret = CanonMathPlace(found.row, found.index + 1)
                            return
                        }
                        else -> current = found.row
                    }
                }
            }
            CanonMathKey.Left -> moveLeft()
            CanonMathKey.Right -> moveRight()
            CanonMathKey.Up -> moveVertically(true)
            CanonMathKey.Down -> moveVertically(false)
            CanonMathKey.Delete -> delete()
            CanonMathKey.Clear -> {
                root.items.clear()
                caret = CanonMathPlace(root, 0)
            }
        }
    }

    private fun insert(item: CanonMathItem, entering: Int) {
        caret.row.items.add(caret.index, item)
        val slot = item.slots[entering]
        caret = CanonMathPlace(slot, slot.items.size)
    }

    private class Found(val row: CanonMathRow, val index: Int, val slot: Int)

    /** The item holding [row]: the row it is in, its index there, and which of its slots [row] is. */
    private fun parent(row: CanonMathRow): Found? {
        fun search(current: CanonMathRow): Found? {
            current.items.forEachIndexed { index, item ->
                item.slots.forEachIndexed { slot, child ->
                    if (child === row) return Found(current, index, slot)
                    search(child)?.let { return it }
                }
            }
            return null
        }
        return search(root)
    }

    private fun moveLeft() {
        val (row, index) = caret
        if (index > 0) {
            val last = row.items[index - 1].slots.lastOrNull()
            caret = if (last != null) CanonMathPlace(last, last.items.size) else caret.copy(index = index - 1)
        } else {
            val found = parent(row) ?: return
            val slots = found.row.items[found.index].slots
            caret = if (found.slot > 0) CanonMathPlace(slots[found.slot - 1], slots[found.slot - 1].items.size)
            else CanonMathPlace(found.row, found.index)
        }
    }

    private fun moveRight() {
        val (row, index) = caret
        if (index < row.items.size) {
            val first = row.items[index].slots.firstOrNull()
            caret = if (first != null) CanonMathPlace(first, 0) else caret.copy(index = index + 1)
        } else {
            val found = parent(row) ?: return
            val slots = found.row.items[found.index].slots
            caret = if (found.slot < slots.size - 1) CanonMathPlace(slots[found.slot + 1], 0)
            else CanonMathPlace(found.row, found.index + 1)
        }
    }

    /** To the slot above or below in the nearest structure that has one; up into a neighbouring exponent, down out of one. */
    private fun moveVertically(up: Boolean) {
        var current = caret.row
        while (true) {
            val found = parent(current) ?: break
            val item = found.row.items[found.index]
            val target = item.slot(found.slot, up)
            if (target != null) {
                val row = item.slots[target]
                caret = CanonMathPlace(row, row.items.size)
                return
            }
            if (item is CanonMathItem.Power && !up) {
                caret = CanonMathPlace(found.row, found.index + 1)
                return
            }
            current = found.row
        }
        if (!up) return
        val (row, index) = caret
        val before = row.items.getOrNull(index - 1)
        val after = row.items.getOrNull(index)
        if (before is CanonMathItem.Power) caret = CanonMathPlace(before.exponent, before.exponent.items.size)
        else if (after is CanonMathItem.Power) caret = CanonMathPlace(after.exponent, 0)
    }

    /**
     * A symbol or an empty structure goes; a filled structure is entered from its end. At the start of a first slot,
     * the structure is taken apart and its contents stay in its place.
     */
    private fun delete() {
        val (row, index) = caret
        if (index > 0) {
            val item = row.items[index - 1]
            if (item.slots.all { it.items.isEmpty() }) {
                row.items.removeAt(index - 1)
                caret = caret.copy(index = index - 1)
            } else {
                val last = item.slots.last()
                caret = CanonMathPlace(last, last.items.size)
            }
        } else {
            val found = parent(row) ?: return
            val item = found.row.items[found.index]
            if (found.slot > 0) {
                val previous = item.slots[found.slot - 1]
                caret = CanonMathPlace(previous, previous.items.size)
            } else {
                found.row.items.removeAt(found.index)
                found.row.items.addAll(found.index, item.slots.flatMap { it.items })
                caret = CanonMathPlace(found.row, found.index)
            }
        }
    }

    companion object {
        private val operators = setOf("+", "−", "×", "=", ",")

        private fun isOperator(item: CanonMathItem) = item is CanonMathItem.Symbol && item.symbol in operators

        private fun isNumeral(item: CanonMathItem) =
            item is CanonMathItem.Symbol && item.symbol.all { it.isDigit() || it == '.' }

        private fun isName(symbol: String) = symbol == "π" || symbol == "∞" || symbol.firstOrNull()?.isLetter() == true

        private fun endsOperand(item: CanonMathItem) =
            if (item is CanonMathItem.Symbol) isNumeral(item) || isName(item.symbol) || item.symbol == "!" else true

        private fun startsOperand(item: CanonMathItem) = when (item) {
            is CanonMathItem.Symbol -> isNumeral(item) || isName(item.symbol)
            is CanonMathItem.Power -> false
            else -> true
        }

        /** nerdamer's syntax, with `*` wherever two operands meet. */
        fun text(row: CanonMathRow, variable: String): String = buildString {
            row.items.forEachIndexed { index, item ->
                val previous = row.items.getOrNull(index - 1)
                if (previous != null && endsOperand(previous) && startsOperand(item) && !(isNumeral(previous) && isNumeral(item))) {
                    append("*")
                }
                val piece = text(item, variable)
                // exponent covers whole fraction
                if (item is CanonMathItem.Fraction && row.items.getOrNull(index + 1) is CanonMathItem.Power) append("($piece)")
                else append(piece)
            }
        }

        private fun text(item: CanonMathItem, v: String): String {
            fun t(row: CanonMathRow) = text(row, v)
            return when (item) {
                is CanonMathItem.Symbol -> when (item.symbol) {
                    "×" -> "*"
                    "−" -> "-"
                    "π" -> "pi"
                    "∞" -> "Infinity"
                    else -> item.symbol
                }
                is CanonMathItem.Fraction -> "(${t(item.top)})/(${t(item.bottom)})"
                is CanonMathItem.Power -> {
                    val only = item.exponent.items.singleOrNull()
                    if (only != null && only.slots.isEmpty() && startsOperand(only)) "^" + t(item.exponent) else "^(${t(item.exponent)})"
                }
                is CanonMathItem.Root -> "sqrt(${t(item.radicand)})"
                is CanonMathItem.NthRoot -> "nthroot(${t(item.radicand)},${t(item.index)})"
                is CanonMathItem.Group -> "(${t(item.inner)})"
                is CanonMathItem.Fence -> item.kind.name.lowercase() + "(${t(item.inner)})"
                is CanonMathItem.Function -> item.name + "(" + item.arguments.joinToString(",") { t(it) } + ")"
                is CanonMathItem.LogBase -> "log(${t(item.argument)},${t(item.base)})"
                is CanonMathItem.Series ->
                    (if (item.product) "product(" else "sum(") + listOf(item.body, item.index, item.from, item.to).joinToString(",") { t(it) } + ")"
                is CanonMathItem.Integral -> {
                    val lower = item.lower
                    val upper = item.upper
                    if (lower == null || upper == null) "integrate(${t(item.body)},$v)"
                    else "defint(${t(item.body)},${t(lower)},${t(upper)},$v)"
                }
                is CanonMathItem.Derivative -> "diff(${t(item.body)},$v)"
                is CanonMathItem.Limit -> "limit(${t(item.body)},$v,${t(item.to)})"
                // ponytail: nerdamer has no nCr or nPr, so they go out as factorials and come back as them
                is CanonMathItem.Binomial ->
                    "(factorial(${t(item.n)})/(factorial(${t(item.r)})*factorial((${t(item.n)})-(${t(item.r)}))))"
                is CanonMathItem.Permutation -> "(factorial(${t(item.n)})/factorial((${t(item.n)})-(${t(item.r)})))"
            }
        }

        /** Items for text in nerdamer's syntax; text that does not parse comes in as one symbol per character. */
        fun items(text: String): List<CanonMathItem> {
            val node = CanonMathNode.parse(text)
                ?: return text.filter { !it.isWhitespace() }.map { CanonMathItem.Symbol(it.toString()) }
            return items(node)
        }

        private fun row(node: CanonMathNode) = CanonMathRow(items(node).toMutableList())

        private fun precedence(node: CanonMathNode) = when (node) {
            is CanonMathNode.Relation, is CanonMathNode.Items -> 0
            is CanonMathNode.Sum, is CanonMathNode.Negate -> 1
            is CanonMathNode.Product -> 2
            is CanonMathNode.Power -> 3
            is CanonMathNode.Quotient, is CanonMathNode.Factorial -> 4
            else -> 5
        }

        private fun operand(node: CanonMathNode, below: Int): List<CanonMathItem> =
            if (precedence(node) < below) listOf(CanonMathItem.Group(row(node))) else items(node)

        private fun items(node: CanonMathNode): List<CanonMathItem> = when (node) {
            is CanonMathNode.Number -> node.digits.map { CanonMathItem.Symbol(it.toString()) }
            is CanonMathNode.Symbol -> listOf(
                CanonMathItem.Symbol(
                    when (node.name) {
                        "pi" -> "π"
                        "Infinity" -> "∞"
                        else -> node.name
                    }
                )
            )
            CanonMathNode.Hole -> emptyList()
            is CanonMathNode.Negate -> listOf(CanonMathItem.Symbol("−")) + operand(node.inner, 2)
            is CanonMathNode.Sum -> node.terms.flatMapIndexed { index, term ->
                if (term is CanonMathNode.Negate) listOf(CanonMathItem.Symbol("−")) + operand(term.inner, 2)
                else (if (index > 0) listOf(CanonMathItem.Symbol("+")) else emptyList()) + operand(term, 1)
            }
            is CanonMathNode.Product -> node.factors.flatMapIndexed { index, factor ->
                val set = operand(factor, 2)
                val first = set.firstOrNull()
                if (index > 0 && first != null && isNumeral(first)) listOf(CanonMathItem.Symbol("×")) + set else set
            }
            is CanonMathNode.Quotient -> listOf(CanonMathItem.Fraction(row(node.top), row(node.bottom)))
            is CanonMathNode.Power -> operand(node.base, 5) + CanonMathItem.Power(row(node.exponent))
            is CanonMathNode.Call -> call(node.name, node.arguments)
            is CanonMathNode.Factorial -> operand(node.inner, 5) + CanonMathItem.Symbol("!")
            is CanonMathNode.Relation -> items(node.left) + CanonMathItem.Symbol(node.op) + items(node.right)
            is CanonMathNode.Items -> node.items.flatMapIndexed { index, entry ->
                (if (index > 0) listOf(CanonMathItem.Symbol(",")) else emptyList()) + items(entry)
            }
        }

        private fun call(name: String, arguments: List<CanonMathNode>): List<CanonMathItem> {
            val rows = arguments.map(::row)
            val first = rows.firstOrNull() ?: CanonMathRow()
            return when {
                name == "sqrt" -> listOf(CanonMathItem.Root(first))
                name == "abs" -> listOf(CanonMathItem.Fence(CanonMathFence.Abs, first))
                name == "floor" -> listOf(CanonMathItem.Fence(CanonMathFence.Floor, first))
                name == "ceil" -> listOf(CanonMathItem.Fence(CanonMathFence.Ceil, first))
                name == "nthroot" && rows.size == 2 -> listOf(CanonMathItem.NthRoot(rows[1], rows[0]))
                name == "cbrt" -> listOf(CanonMathItem.NthRoot(CanonMathRow(mutableListOf(CanonMathItem.Symbol("3"))), first))
                name == "log" && rows.size == 2 -> listOf(CanonMathItem.LogBase(rows[1], rows[0]))
                name == "factorial" -> operand(arguments.firstOrNull() ?: CanonMathNode.Hole, 5) + CanonMathItem.Symbol("!")
                (name == "sum" || name == "product") && rows.size == 4 ->
                    listOf(CanonMathItem.Series(name == "product", rows[1], rows[2], rows[3], rows[0]))
                name == "defint" && rows.size == 4 -> listOf(CanonMathItem.Integral(rows[1], rows[2], rows[0]))
                name == "integrate" && rows.size == 2 -> listOf(CanonMathItem.Integral(null, null, rows[0]))
                name == "diff" && rows.size == 2 -> listOf(CanonMathItem.Derivative(rows[0]))
                name == "limit" && rows.size == 3 -> listOf(CanonMathItem.Limit(rows[2], rows[0]))
                else -> listOf(CanonMathItem.Function(name, rows.ifEmpty { listOf(CanonMathRow()) }))
            }
        }
    }
}
