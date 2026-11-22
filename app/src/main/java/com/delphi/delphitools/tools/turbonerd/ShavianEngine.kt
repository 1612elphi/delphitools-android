package com.delphi.delphitools.tools.turbonerd

data class ShavianAlternative(val shavian: String, val name: String, val ipa: String)

data class ShavianPhoneme(val shavian: String, val ipa: String, val alternatives: List<ShavianAlternative>) {
    constructor(shavian: String, ipa: String) : this(shavian, ipa, ShavianAlternatives.of(shavian))
}

enum class GlossSource { Core, Full, Heuristic, Shorthand }

enum class GlossMarker(val prefix: String) { None(""), Namer("·"), Acroring("⸰"), Acroarc("꤮") }

data class GlossWord(
    val latin: String,
    val phonemes: List<ShavianPhoneme>,
    val shavian: String,
    val ipa: String,
    val source: GlossSource,
    val marker: GlossMarker = GlossMarker.None,
    val userEdited: Boolean = false,
) {
    val guessed get() = source == GlossSource.Heuristic && !userEdited

    /** The marker and phonemes respelled into [shavian] and [ipa]. */
    fun respelled(phonemes: List<ShavianPhoneme> = this.phonemes, marker: GlossMarker = this.marker, edited: Boolean = userEdited) = copy(
        phonemes = phonemes,
        marker = marker,
        shavian = marker.prefix + phonemes.joinToString("") { it.shavian },
        ipa = phonemes.joinToString("") { it.ipa },
        userEdited = edited,
    )
}

sealed interface GlossToken {
    data class Word(val word: GlossWord) : GlossToken

    data class Punctuation(val value: String) : GlossToken

    data class Whitespace(val value: String) : GlossToken
}

object ShavianAlternatives {
    private val groupOf = ShavianPhonemeMap.consonantGroups.flatMap { group -> group.map { it to group - it } }.toMap()
    private val vowels = ShavianPhonemeMap.letters.filter { it.vowel }.map { it.shavian }

    /** Every other vowel for a vowel; the voicing or articulation group for a consonant. */
    fun of(shavian: String): List<ShavianAlternative> {
        val letter = ShavianPhonemeMap.letter(shavian) ?: return emptyList()
        val candidates = if (letter.vowel) vowels - shavian else groupOf[shavian].orEmpty()
        return candidates.mapNotNull(ShavianPhonemeMap::letter).map { ShavianAlternative(it.shavian, it.name, it.ipa) }
    }
}

/** dt-ios ShavianEngine: dictionary lookup, the core dictionary before the full one, falling back to the grapheme heuristic. */
class ShavianEngine(
    var core: Map<String, List<String>> = emptyMap(),
    var full: Map<String, List<String>> = emptyMap(),
) {
    fun tokenise(text: String): List<GlossToken> = tokenPattern.findAll(text).map { match ->
        val (word, space, mark) = match.destructured
        when {
            word.isNotEmpty() -> GlossToken.Word(transliterate(word))
            space.isNotEmpty() -> GlossToken.Whitespace(space)
            else -> GlossToken.Punctuation(mark)
        }
    }.toList()

    /** Guessed words the full dictionary has since learnt. */
    fun reResolve(tokens: List<GlossToken>): List<GlossToken> = tokens.map { token ->
        val word = (token as? GlossToken.Word)?.word ?: return@map token
        if (word.userEdited || word.source != GlossSource.Heuristic) return@map token
        val (codes, source) = lookup(word.latin.lowercase()) ?: return@map token
        GlossToken.Word(word.respelled(phonemes(overridden(word.latin, codes))).copy(source = source))
    }

    private fun transliterate(word: String): GlossWord {
        val lower = word.lowercase()
        shorthands[lower]?.let { (shavian, ipa) ->
            return GlossWord(word, listOf(ShavianPhoneme(shavian, ipa)), shavian, ipa, GlossSource.Shorthand)
        }
        val found = lookup(lower)
        val phonemes = if (found != null) {
            phonemes(overridden(lower, found.first))
        } else {
            ShavianHeuristic.transliterate(word).map { ShavianPhoneme(it.shavian, it.ipa) }
        }
        return GlossWord(word, phonemes, "", "", found?.second ?: GlossSource.Heuristic).respelled()
    }

    private fun lookup(word: String): Pair<List<String>, GlossSource>? =
        core[word]?.let { it to GlossSource.Core } ?: full[word]?.let { it to GlossSource.Full }

    companion object {
        private val tokenPattern = Regex("""(?U)([a-zA-Z']+)|(\s+)|([^\sa-zA-Z']+)""")

        // Shavian spelling rule 2
        private val shorthands = mapOf(
            "the" to ("𐑞" to "ðə"),
            "of" to ("𐑝" to "əv"),
            "and" to ("𐑯" to "ənd"),
            "to" to ("𐑑" to "tuː"),
            "for" to ("𐑓" to "fɔːr"),
        )

        private val palmWords = listOf(
            "father", "rather", "lather", "calm", "palm", "psalm", "balm", "almond",
            "drama", "banana", "llama", "mama", "papa", "lava", "java", "guava",
            "rajah", "hurrah", "aha", "bah", "salami", "safari", "tsunami", "khaki",
            "spa", "bra", "pasta", "plaza", "taco", "cantata", "sonata", "aria",
        )
        private val thoughtWords = listOf("caught", "bought", "raw", "spawn", "cause")

        // palm 𐑭, thought 𐑷
        private val overrides: Map<String, Pair<String, String>> =
            palmWords.associateWith { "AA" to "AA_PALM" } + thoughtWords.associateWith { "AA" to "AO" }

        private fun overridden(word: String, codes: List<String>): List<String> {
            val (from, to) = overrides[word.lowercase()] ?: return codes
            return codes.map { if (ShavianPhonemeMap.normaliseArpabet(it) == from) to else it }
        }

        // y+uw: yew ligature
        private fun phonemes(codes: List<String>): List<ShavianPhoneme> {
            val merged = mutableListOf<String>()
            var i = 0
            while (i < codes.size) {
                if (codes[i] == "Y" && i + 1 < codes.size && ShavianPhonemeMap.normaliseArpabet(codes[i + 1]) == "UW") {
                    merged += "YUW"
                    i += 2
                } else {
                    merged += codes[i]
                    i += 1
                }
            }
            return merged.map { ShavianPhoneme(ShavianPhonemeMap.shavianChar(it) ?: "?", ShavianPhonemeMap.ipaString(it) ?: "?") }
        }

        /** Edited or marked words carry over a retokenise where the words before or after them match. */
        fun carryEdits(old: List<GlossToken>, new: List<GlossToken>): List<GlossToken> {
            val before = old.mapNotNull { (it as? GlossToken.Word)?.word }
            val slots = new.indices.filter { new[it] is GlossToken.Word }
            fun latin(slot: Int) = (new[slot] as GlossToken.Word).word.latin
            val out = new.toMutableList()
            fun carry(word: GlossWord, slot: Int) {
                if (word.userEdited || word.marker != GlossMarker.None) out[slot] = GlossToken.Word(word)
            }
            val shared = minOf(before.size, slots.size)
            var prefix = 0
            while (prefix < shared && before[prefix].latin == latin(slots[prefix])) {
                carry(before[prefix], slots[prefix])
                prefix++
            }
            var suffix = 0
            while (suffix < shared - prefix && before[before.size - 1 - suffix].latin == latin(slots[slots.size - 1 - suffix])) {
                carry(before[before.size - 1 - suffix], slots[slots.size - 1 - suffix])
                suffix++
            }
            return out
        }

        fun inserting(letter: ShavianLetter, at: Int, word: GlossWord): GlossWord {
            val phonemes = word.phonemes.toMutableList()
            phonemes.add(at.coerceIn(0, phonemes.size), ShavianPhoneme(letter.shavian, letter.ipa))
            return word.respelled(phonemes, edited = true)
        }

        /** Null for a word's sole letter. */
        fun removing(at: Int, word: GlossWord): GlossWord? {
            if (word.phonemes.size <= 1 || at !in word.phonemes.indices) return null
            return word.respelled(word.phonemes.filterIndexed { i, _ -> i != at }, edited = true)
        }

        fun swapping(at: Int, alternative: ShavianAlternative, word: GlossWord): GlossWord? {
            if (at !in word.phonemes.indices) return null
            val phonemes = word.phonemes.toMutableList()
            phonemes[at] = ShavianPhoneme(alternative.shavian, alternative.ipa)
            return word.respelled(phonemes, edited = true)
        }

        fun text(tokens: List<GlossToken>) = tokens.joinToString("") {
            when (it) {
                is GlossToken.Word -> it.word.shavian
                is GlossToken.Punctuation -> it.value
                is GlossToken.Whitespace -> it.value
            }
        }
    }
}
