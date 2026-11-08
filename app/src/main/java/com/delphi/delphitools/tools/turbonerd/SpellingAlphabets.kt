package com.delphi.delphitools.tools.turbonerd

data class SpellingAlphabet(val id: String, val label: String, val words: Map<String, String>)

object SpellingAlphabets {
    private val digitsDE = mapOf(
        "0" to "Null", "1" to "Eins", "2" to "Zwei", "3" to "Drei", "4" to "Vier",
        "5" to "Fünf", "6" to "Sechs", "7" to "Sieben", "8" to "Acht", "9" to "Neun",
    )

    val all = listOf(
        SpellingAlphabet(
            "nato", "NATO",
            mapOf(
                "A" to "Alfa", "B" to "Bravo", "C" to "Charlie", "D" to "Delta", "E" to "Echo",
                "F" to "Foxtrot", "G" to "Golf", "H" to "Hotel", "I" to "India", "J" to "Juliett",
                "K" to "Kilo", "L" to "Lima", "M" to "Mike", "N" to "November", "O" to "Oscar",
                "P" to "Papa", "Q" to "Quebec", "R" to "Romeo", "S" to "Sierra", "T" to "Tango",
                "U" to "Uniform", "V" to "Victor", "W" to "Whiskey", "X" to "X-ray",
                "Y" to "Yankee", "Z" to "Zulu",
                "0" to "Zero", "1" to "One", "2" to "Two", "3" to "Three", "4" to "Four",
                "5" to "Five", "6" to "Six", "7" to "Seven", "8" to "Eight", "9" to "Nine",
            ),
        ),
        // DIN 5009:2022 city names
        SpellingAlphabet(
            "din5009", "DIN 5009",
            mapOf(
                "A" to "Aachen", "B" to "Berlin", "C" to "Chemnitz", "D" to "Düsseldorf",
                "E" to "Essen", "F" to "Frankfurt", "G" to "Goslar", "H" to "Hamburg",
                "I" to "Ingelheim", "J" to "Jena", "K" to "Köln", "L" to "Leipzig",
                "M" to "München", "N" to "Nürnberg", "O" to "Offenbach", "P" to "Potsdam",
                "Q" to "Quickborn", "R" to "Rostock", "S" to "Salzwedel", "T" to "Tübingen",
                "U" to "Unna", "V" to "Völklingen", "W" to "Wuppertal", "X" to "Xanten",
                "Y" to "Ypsilon", "Z" to "Zwickau",
                "Ä" to "Umlaut Aachen", "Ö" to "Umlaut Offenbach", "Ü" to "Umlaut Unna",
                "ß" to "Eszett",
            ) + digitsDE,
        ),
        // DIN 5009:1996 words
        SpellingAlphabet(
            "german", "German (1996)",
            mapOf(
                "A" to "Anton", "B" to "Berta", "C" to "Cäsar", "D" to "Dora", "E" to "Emil",
                "F" to "Friedrich", "G" to "Gustav", "H" to "Heinrich", "I" to "Ida",
                "J" to "Julius", "K" to "Kaufmann", "L" to "Ludwig", "M" to "Martha",
                "N" to "Nordpol", "O" to "Otto", "P" to "Paula", "Q" to "Quelle",
                "R" to "Richard", "S" to "Samuel", "T" to "Theodor", "U" to "Ulrich",
                "V" to "Viktor", "W" to "Wilhelm", "X" to "Xanthippe", "Y" to "Ypsilon",
                "Z" to "Zacharias",
                "Ä" to "Ärger", "Ö" to "Ökonom", "Ü" to "Übermut", "ß" to "Eszett",
            ) + digitsDE,
        ),
    )

    data class SpelledChar(val char: String, val word: String?)

    // ß uppercases to ss
    fun spell(text: String, alphabet: SpellingAlphabet): List<SpelledChar> =
        BrailleEngine.codePoints(text).map { SpelledChar(it, alphabet.words[it] ?: alphabet.words[it.uppercase()]) }

    fun spellText(text: String, alphabet: SpellingAlphabet): String =
        spell(text, alphabet).joinToString(" ") { it.word ?: if (it.char.isBlank()) "/" else it.char }

    fun unspell(words: String, alphabet: SpellingAlphabet): String = read(words, alphabet).joinToString("") { it.value }

    fun looksSpelled(text: String, alphabet: SpellingAlphabet): Boolean {
        val count = text.trim().split(whitespace).count { it.isNotEmpty() }
        return count >= 2 && read(text, alphabet).all { it.known }
    }

    private val whitespace = Regex("\\s+")

    private class ReadToken(val value: String, val known: Boolean)

    private fun key(word: String) = word.lowercase().replace("-", "")

    private fun reverse(alphabet: SpellingAlphabet): Map<String, String> =
        mapOf("/" to " ") + alphabet.words.entries.associate { (char, word) -> key(word) to char }

    private fun read(text: String, alphabet: SpellingAlphabet): List<ReadToken> {
        val map = reverse(alphabet)
        val tokens = text.split(whitespace).filter { it.isNotEmpty() }
        val out = mutableListOf<ReadToken>()
        var i = 0
        while (i < tokens.size) {
            // two-word entries win first
            val next = tokens.getOrElse(i + 1) { "" }
            val pair = map[key("${tokens[i]} $next")]
            if (pair != null) {
                out += ReadToken(pair, true)
                i += 2
                continue
            }
            val single = map[key(tokens[i])]
            out += ReadToken(single ?: tokens[i], single != null)
            i += 1
        }
        return out
    }
}
