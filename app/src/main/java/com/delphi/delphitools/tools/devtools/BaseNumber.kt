package com.delphi.delphitools.tools.devtools

enum class Radix(val radix: Int, val prefix: String, val short: String, val placeholder: String, val label: String) {
    Decimal(10, "", "DEC", "255", "Decimal"),
    Hex(16, "0x", "HEX", "FF", "Hexadecimal"),
    Binary(2, "0b", "BIN", "11111111", "Binary"),
    Octal(8, "0o", "OCT", "377", "Octal"),
    ;

    val highestDigit: String get() = (radix - 1).toString(radix).uppercase()
}

sealed interface BaseReading {
    data object Empty : BaseReading
    data class Value(override val value: ULong) : BaseReading
    data class Invalid(val character: Char) : BaseReading
    data object TooLarge : BaseReading

    val value: ULong? get() = (this as? Value)?.value
}

enum class BitOperation(val title: String) {
    And("AND"), Or("OR"), Xor("XOR"), Not("NOT"), Left("<<"), Right(">>");

    val takesOperand get() = ordinal <= 2
    val takesShift get() = this == Left || this == Right
}

/** dt-ios BaseNumber: reading, formatting and bitwise arithmetic on unsigned 64-bit numbers. */
object BaseNumber {
    val words = listOf(8, 16, 32, 64)

    // ascii only, as ios
    private fun digit(char: Char): Int = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'z' -> char - 'a' + 10
        else -> -1
    }

    /**
     * Reads a number typed in [radix]. A 0x, 0b or 0o prefix picks its own base (hex keeps 0b as digits);
     * underscores, spaces and apostrophes between digits are separators.
     */
    fun read(text: String, radix: Radix): BaseReading {
        var body = text.lowercase().filter { it != '_' && it != '\'' && !it.isWhitespace() }
        val detected = base(body, radix)
        body = body.removePrefix(detected.prefix)
        if (body.isEmpty()) return BaseReading.Empty
        body.firstOrNull { digit(it) !in 0 until detected.radix }?.let { return BaseReading.Invalid(it) }
        return body.toULongOrNull(detected.radix)?.let { BaseReading.Value(it) } ?: BaseReading.TooLarge
    }

    /** The base the text is read in: its prefix's, else [radix]. */
    fun base(text: String, radix: Radix): Radix {
        val body = text.lowercase().filter { !it.isWhitespace() }
        return listOf(Radix.Hex, Radix.Binary, Radix.Octal).firstOrNull { body.startsWith(it.prefix) && (radix != Radix.Hex || it == Radix.Hex) } ?: radix
    }

    fun format(value: ULong, radix: Radix, prefixed: Boolean = false) =
        (if (prefixed) radix.prefix else "") + value.toString(radix.radix).uppercase()

    /** The smallest of 8, 16, 32 and 64 bits that holds the value. */
    fun word(fitting: ULong): Int {
        val bits = 64 - fitting.countLeadingZeroBits()
        return words.firstOrNull { bits <= it } ?: 64
    }

    /** The operation within a word of [word] bits; null while an operation still waits for B. */
    fun apply(operation: BitOperation, a: ULong, b: ULong?, shift: Int, word: Int): ULong? {
        val mask = if (word >= 64) ULong.MAX_VALUE else (1uL shl word) - 1uL
        return when (operation) {
            BitOperation.And -> b?.let { a and it and mask }
            BitOperation.Or -> b?.let { (a or it) and mask }
            BitOperation.Xor -> b?.let { (a xor it) and mask }
            BitOperation.Not -> a.inv() and mask
            BitOperation.Left -> if (shift >= word) 0uL else (a shl shift) and mask
            BitOperation.Right -> if (shift >= 64) 0uL else a shr shift
        }
    }
}

/** Bits per line and lamp size of the register picture, in dp. */
data class BitGeometry(val perLine: Int, val cell: Float, val width: Float, val height: Float)

object BitRegisterLayout {
    const val HEADER = 44f
    const val BIT_GAP = 3f
    const val NIBBLE_GAP = 9f
    const val HEX_ROW = 15f
    const val INDEX_ROW = 12f
    const val LINE_GAP = 8f
    const val OPERAND_GAP = 18f
    const val RULE_ROOM = 14f
    const val SIGN_WIDTH = 44f
    private const val LARGEST_CELL = 40f
    private const val COMFORTABLE_CELL = 22f

    /**
     * Bits per line and lamp size that make the largest lamps: [numbered] is one entry per operand,
     * true for the one that carries the bit numbers.
     */
    fun layout(word: Int, numbered: List<Boolean>, signs: Boolean, width: Float, height: Float): BitGeometry {
        val options = BaseNumber.words.filter { it <= word }.map { perLine ->
            val lines = word / perLine
            val nibbles = perLine / 4
            val fixedWidth = (if (signs) SIGN_WIDTH else 0f) + (nibbles - 1) * NIBBLE_GAP + (perLine - nibbles) * BIT_GAP
            var fixedHeight = 0f
            numbered.forEachIndexed { index, carries ->
                fixedHeight += lines * (HEX_ROW + if (carries) INDEX_ROW else 0f) + (lines - 1) * LINE_GAP
                if (index > 0) fixedHeight += if (index == numbered.size - 1 && signs) RULE_ROOM * 2 else OPERAND_GAP
            }
            val cell = minOf((width - fixedWidth) / perLine, (height - fixedHeight) / (lines * numbered.size), LARGEST_CELL)
            BitGeometry(perLine, cell, fixedWidth + cell * perLine, fixedHeight + cell * lines * numbered.size)
        }
        // longest tappable lines win
        return options.lastOrNull { it.cell >= COMFORTABLE_CELL } ?: options.maxBy { it.cell }
    }
}
