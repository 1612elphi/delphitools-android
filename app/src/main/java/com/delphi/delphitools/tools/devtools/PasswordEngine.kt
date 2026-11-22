package com.delphi.delphitools.tools.devtools

import java.security.SecureRandom
import kotlin.math.log2

/** dt-ios PasswordEngine. */
object PasswordEngine {
    const val LOWERCASE_SET = "abcdefghijklmnopqrstuvwxyz"
    const val UPPERCASE_SET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val DIGIT_SET = "0123456789"

    // safe to paste anywhere
    const val SYMBOL_SET = "!@#$%^&*()-=_+?[]{};:,.<>/~"

    // keepassxc ambiguous set
    const val AMBIGUOUS = "Il1O0"

    const val MINIMUM_LENGTH = 8
    const val MAXIMUM_LENGTH = 128
    const val MINIMUM_WORDS = 3
    const val MAXIMUM_WORDS = 10

    data class PasswordSpec(
        val length: Int = 16,
        val lowercase: Boolean = true,
        val uppercase: Boolean = true,
        val digits: Boolean = true,
        val symbols: Boolean = true,
        val excludeAmbiguous: Boolean = true,
    )

    data class PassphraseSpec(
        val words: Int = 5,
        val separator: String = "-",
        val capitaliseFirst: Boolean = true,
        val trailingDigit: Boolean = true,
    )

    enum class CharKind { Letter, Digit, Symbol }

    private val random = SecureRandom()

    fun kind(char: Char) = when (char) {
        in DIGIT_SET -> CharKind.Digit
        in SYMBOL_SET -> CharKind.Symbol
        else -> CharKind.Letter
    }

    fun charset(spec: PasswordSpec): List<Char> {
        var pool = ""
        if (spec.lowercase) pool += LOWERCASE_SET
        if (spec.uppercase) pool += UPPERCASE_SET
        if (spec.digits) pool += DIGIT_SET
        if (spec.symbols) pool += SYMBOL_SET
        if (spec.excludeAmbiguous) pool = pool.filterNot { it in AMBIGUOUS }
        return pool.toList()
    }

    // nextInt(bound) is unbiased
    private fun <T> List<T>.pick() = this[random.nextInt(size)]

    fun password(spec: PasswordSpec): String {
        val pool = charset(spec)
        if (pool.isEmpty()) return ""
        val length = spec.length.coerceIn(MINIMUM_LENGTH, MAXIMUM_LENGTH)
        return String(CharArray(length) { pool.pick() })
    }

    fun passphrase(wordlist: List<String>, spec: PassphraseSpec): String {
        if (wordlist.isEmpty()) return ""
        val count = spec.words.coerceIn(MINIMUM_WORDS, MAXIMUM_WORDS)
        val picked = MutableList(count) { wordlist.pick() }
        if (spec.capitaliseFirst) picked[0] = picked[0].replaceFirstChar { it.uppercaseChar() }
        var out = picked.joinToString(spec.separator)
        if (spec.trailingDigit) out += random.nextInt(10)
        return out
    }

    fun passwordEntropy(length: Int, poolSize: Int): Double =
        if (poolSize < 2) 0.0 else length * log2(poolSize.toDouble())

    fun passphraseEntropy(words: Int, wordlistSize: Int, trailingDigit: Boolean): Double =
        if (wordlistSize < 2) 0.0 else words * log2(wordlistSize.toDouble()) + if (trailingDigit) log2(10.0) else 0.0

    fun strengthBand(bits: Double) = when {
        bits >= 95 -> "Very strong"
        bits >= 60 -> "Strong"
        bits >= 40 -> "Fair"
        else -> "Weak"
    }

    /** EFF dice wordlist: a roll code, a tab, then the word. */
    fun parseWordlist(text: String): List<String> = text.split('\n').mapNotNull { line ->
        val word = line.trimStart().dropWhile { it.isDigit() }.trim().lowercase()
        word.takeIf { it.isNotEmpty() && it.all { char -> char in 'a'..'z' } }
    }

    /** Word to its five-dice roll code, for the dice in the banner. */
    fun parseRolls(text: String): Map<String, String> = buildMap {
        for (line in text.split('\n')) {
            val parts = line.split('\t')
            if (parts.size != 2 || parts[0].length != 5 || !parts[0].all { it in '1'..'6' }) continue
            put(parts[1].trim(), parts[0])
        }
    }
}
