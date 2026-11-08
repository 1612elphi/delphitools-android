package com.delphi.delphitools.tools.turbonerd

/** English to IPA over the Shavian tool's ARPABET dictionaries, falling back to its grapheme heuristic for words that are not listed. */
object IPAEngine {
    sealed interface Token {
        val ipa: String

        data class Word(val value: String, override val ipa: String, val guess: Boolean) : Token

        data class Other(val value: String) : Token {
            override val ipa get() = value
        }
    }

    private val stress = mapOf('1' to "ˈ", '2' to "ˌ")

    private fun isVowel(phone: String) = phone.lastOrNull()?.isDigit() == true

    // ponytail: maximal onset — every consonant since the previous vowel goes with the stressed syllable,
    // so "abstain" gives əˈbsteɪn for əbˈsteɪn; sonority-sequencing syllabification is the upgrade
    fun wordToIpa(phones: List<String>): String {
        val polysyllabic = phones.count(::isVowel) > 1
        // cmu marks both compound halves
        val lastPrimary = phones.indexOfLast { it.endsWith("1") }
        val out = StringBuilder()
        val onset = StringBuilder()
        phones.forEachIndexed { index, phone ->
            val ipa = ShavianPhonemeMap.ipaString(phone) ?: "?"
            if (!isVowel(phone)) {
                onset.append(ipa)
                return@forEachIndexed
            }
            val mark = if (phone.endsWith("1") && index != lastPrimary) '2' else phone.last()
            out.append(if (polysyllabic) stress[mark] ?: "" else "").append(onset).append(ipa)
            onset.clear()
        }
        return out.append(onset).toString()
    }

    private fun isWordChar(char: Char) = char in 'a'..'z' || char in 'A'..'Z' || char == '\''

    fun transcribe(text: String, lookup: (String) -> List<String>?): List<Token> = tokenise(text).map { value ->
        if (!isWordChar(value[0])) return@map Token.Other(value)
        val phones = lookup(value.lowercase())
        if (phones != null) return@map Token.Word(value, wordToIpa(phones), guess = false)
        Token.Word(value, ShavianHeuristic.transliterate(value).joinToString("") { it.ipa }, guess = true)
    }

    fun text(tokens: List<Token>) = tokens.joinToString("") { it.ipa }

    // letter and apostrophe runs
    private fun tokenise(text: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var inWord: Boolean? = null
        for (char in text) {
            val word = isWordChar(char)
            if (inWord != word && current.isNotEmpty()) {
                out += current.toString()
                current.clear()
            }
            inWord = word
            current.append(char)
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }
}
