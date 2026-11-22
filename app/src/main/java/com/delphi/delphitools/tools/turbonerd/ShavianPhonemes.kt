package com.delphi.delphitools.tools.turbonerd

/** dt-ios ShavianPhonemeMap and ShavianHeuristic. */
object ShavianPhonemeMap {
    /** Shavian letter to IPA, the 48 letters. */
    val ipaOfLetter: Map<String, String> = mapOf(
        "𐑐" to "p", "𐑑" to "t", "𐑒" to "k", "𐑓" to "f", "𐑔" to "θ", "𐑕" to "s", "𐑖" to "ʃ", "𐑗" to "tʃ",
        "𐑚" to "b", "𐑛" to "d", "𐑜" to "ɡ", "𐑝" to "v", "𐑞" to "ð", "𐑟" to "z", "𐑠" to "ʒ", "𐑡" to "dʒ",
        "𐑘" to "j", "𐑢" to "w", "𐑙" to "ŋ", "𐑣" to "h", "𐑥" to "m", "𐑯" to "n", "𐑤" to "l", "𐑮" to "r",
        "𐑨" to "æ", "𐑩" to "ə", "𐑪" to "ɒ", "𐑫" to "ʊ", "𐑦" to "ɪ", "𐑧" to "ɛ", "𐑳" to "ʌ",
        "𐑱" to "eɪ", "𐑰" to "iː", "𐑲" to "aɪ", "𐑴" to "oʊ", "𐑵" to "uː", "𐑶" to "ɔɪ", "𐑬" to "aʊ", "𐑷" to "ɔː",
        "𐑸" to "ɑːr", "𐑹" to "ɔːr", "𐑺" to "ɛər", "𐑻" to "ɜːr", "𐑼" to "ɚ", "𐑽" to "ɪər", "𐑾" to "ɪə", "𐑿" to "juː",
    )

    private val arpabetToIpa = mapOf(
        "P" to "p", "T" to "t", "K" to "k", "F" to "f",
        "TH" to "θ", "S" to "s", "SH" to "ʃ", "CH" to "tʃ",
        "B" to "b", "D" to "d", "G" to "ɡ", "V" to "v",
        "DH" to "ð", "Z" to "z", "ZH" to "ʒ", "JH" to "dʒ",
        "Y" to "j", "W" to "w", "NG" to "ŋ", "HH" to "h",
        "M" to "m", "N" to "n", "L" to "l", "R" to "r",
        "AE" to "æ", "AH0" to "ə", "AH" to "ʌ", "AA" to "ɒ", "AA_PALM" to "ɑː",
        "UH" to "ʊ", "IH" to "ɪ", "EH" to "ɛ",
        "EY" to "eɪ", "IY" to "iː", "IY0" to "i", "AY" to "aɪ",
        "OW" to "oʊ", "UW" to "uː", "OY" to "ɔɪ",
        "AW" to "aʊ", "AO" to "ɔː",
        "ER" to "ɚ",
        "YUW" to "juː",
    )

    /** AH0 stays AH0 (schwa), AH1 and AH2 become AH (strut), IY0 stays IY0; other trailing stress digits go. */
    fun normaliseArpabet(code: String): String = when {
        code.startsWith("AH") -> if (code.endsWith("0")) "AH0" else "AH"
        code.startsWith("IY") -> if (code.endsWith("0")) "IY0" else "IY"
        code.lastOrNull()?.let { it in "012" } == true -> code.dropLast(1)
        else -> code
    }

    fun ipaString(arpabet: String): String? = arpabetToIpa[normaliseArpabet(arpabet)]

    /** The 48 letters with their names, in dt-ios order: consonants, then short and long vowels. */
    val letters: List<ShavianLetter> = listOf(
        "𐑐" to "peep", "𐑑" to "tot", "𐑒" to "kick", "𐑓" to "fee", "𐑔" to "thigh", "𐑕" to "so", "𐑖" to "sure", "𐑗" to "church",
        "𐑚" to "bib", "𐑛" to "dead", "𐑜" to "gag", "𐑝" to "vow", "𐑞" to "they", "𐑟" to "zoo", "𐑠" to "measure", "𐑡" to "judge",
        "𐑘" to "yea", "𐑢" to "woe", "𐑙" to "hung", "𐑣" to "ha-ha", "𐑥" to "mime", "𐑯" to "nun", "𐑤" to "loll", "𐑮" to "roar",
    ).map { (shavian, name) -> ShavianLetter(shavian, name, ipaOfLetter.getValue(shavian), vowel = false) } + listOf(
        "𐑨" to "ash", "𐑩" to "ado", "𐑪" to "on", "𐑫" to "wool", "𐑦" to "if", "𐑧" to "egg", "𐑳" to "up",
        "𐑱" to "ate", "𐑰" to "eat", "𐑲" to "ice", "𐑴" to "oak", "𐑵" to "ooze", "𐑶" to "oil", "𐑬" to "out", "𐑷" to "awe",
        "𐑸" to "are", "𐑹" to "or", "𐑺" to "air", "𐑻" to "err", "𐑼" to "array", "𐑽" to "ear", "𐑾" to "ian", "𐑿" to "yew",
    ).map { (shavian, name) -> ShavianLetter(shavian, name, ipaOfLetter.getValue(shavian), vowel = true) }

    private val byLetter = letters.associateBy { it.shavian }

    fun letter(shavian: String): ShavianLetter? = byLetter[shavian]

    private val arpabetToShavian = mapOf(
        "P" to "𐑐", "T" to "𐑑", "K" to "𐑒", "F" to "𐑓",
        "TH" to "𐑔", "S" to "𐑕", "SH" to "𐑖", "CH" to "𐑗",
        "B" to "𐑚", "D" to "𐑛", "G" to "𐑜", "V" to "𐑝",
        "DH" to "𐑞", "Z" to "𐑟", "ZH" to "𐑠", "JH" to "𐑡",
        "Y" to "𐑘", "W" to "𐑢", "NG" to "𐑙", "HH" to "𐑣",
        "M" to "𐑥", "N" to "𐑯", "L" to "𐑤", "R" to "𐑮",
        "AE" to "𐑨", "AH0" to "𐑩", "AH" to "𐑳", "AA" to "𐑪", "AA_PALM" to "𐑭",
        "UH" to "𐑫", "IH" to "𐑦", "EH" to "𐑧",
        "EY" to "𐑱", "IY" to "𐑰", "IY0" to "𐑦", "AY" to "𐑲",
        "OW" to "𐑴", "UW" to "𐑵", "OY" to "𐑶",
        "AW" to "𐑬", "AO" to "𐑷",
        "ER" to "𐑼",
        "YUW" to "𐑿",
    )

    fun shavianChar(arpabet: String): String? = arpabetToShavian[normaliseArpabet(arpabet)]

    /** Consonant voicing and articulation groups. */
    val consonantGroups = listOf(
        listOf("𐑐", "𐑚"), listOf("𐑑", "𐑛"), listOf("𐑒", "𐑜"), listOf("𐑓", "𐑝"),
        listOf("𐑔", "𐑞"), listOf("𐑕", "𐑟"), listOf("𐑖", "𐑠"), listOf("𐑗", "𐑡"),
        listOf("𐑥", "𐑯", "𐑙"), listOf("𐑤", "𐑮"), listOf("𐑘", "𐑢"), listOf("𐑣"),
    )
}

data class ShavianLetter(val shavian: String, val name: String, val ipa: String, val vowel: Boolean)

object ShavianHeuristic {
    // longest first for greedy matching
    private val graphemeRules = listOf(
        "tion" to "𐑖𐑩𐑯", "sion" to "𐑠𐑩𐑯", "ture" to "𐑗𐑼",
        // most common: "though"
        "ough" to "𐑴",
        "ight" to "𐑲𐑑", "ould" to "𐑫𐑛", "ious" to "𐑾𐑕", "eous" to "𐑾𐑕",
        "tch" to "𐑗", "dge" to "𐑡", "sch" to "𐑕𐑒", "scr" to "𐑕𐑒𐑮", "shr" to "𐑖𐑮", "thr" to "𐑔𐑮",
        "str" to "𐑕𐑑𐑮", "spl" to "𐑕𐑐𐑤", "spr" to "𐑕𐑐𐑮",
        "kn" to "𐑯", "wr" to "𐑮", "gn" to "𐑯", "ph" to "𐑓", "wh" to "𐑢",
        // silent in most positions
        "gh" to "",
        // unvoiced by default
        "th" to "𐑔",
        "sh" to "𐑖", "ch" to "𐑗", "ng" to "𐑙", "nk" to "𐑙𐑒", "qu" to "𐑒𐑢", "ck" to "𐑒",
        "ee" to "𐑰", "ea" to "𐑰", "oo" to "𐑵", "ou" to "𐑬", "ow" to "𐑬", "oi" to "𐑶", "oy" to "𐑶",
        "ai" to "𐑱", "ay" to "𐑱", "ei" to "𐑱", "ey" to "𐑱", "ie" to "𐑰", "aw" to "𐑷", "au" to "𐑷",
        "er" to "𐑼", "ir" to "𐑻", "ur" to "𐑻", "or" to "𐑹", "ar" to "𐑸", "ew" to "𐑿",
        "a" to "𐑨", "b" to "𐑚", "c" to "𐑒", "d" to "𐑛", "e" to "𐑧", "f" to "𐑓", "g" to "𐑜",
        "h" to "𐑣", "i" to "𐑦", "j" to "𐑡", "k" to "𐑒", "l" to "𐑤", "m" to "𐑥", "n" to "𐑯",
        "o" to "𐑪", "p" to "𐑐", "r" to "𐑮", "s" to "𐑕", "t" to "𐑑", "u" to "𐑳", "v" to "𐑝",
        "w" to "𐑢", "x" to "𐑒𐑕", "y" to "𐑘", "z" to "𐑟",
    )

    data class Phoneme(val shavian: String, val ipa: String)

    /** A rough English-to-Shavian guess from grapheme rules, one phoneme per Shavian letter. */
    fun transliterate(word: String): List<Phoneme> {
        val lower = word.lowercase()
        // silent trailing e, roughly
        val effective = if (lower.length > 2 && lower.endsWith("e") && lower[lower.length - 2] !in "aeiouy") lower.dropLast(1) else lower
        val result = mutableListOf<Phoneme>()
        var i = 0
        while (i < effective.length) {
            val rule = graphemeRules.firstOrNull { effective.startsWith(it.first, i) }
            if (rule == null) {
                // numbers, hyphens and the like
                i += 1
                continue
            }
            for (letter in BrailleEngine.codePoints(rule.second)) {
                ShavianPhonemeMap.ipaOfLetter[letter]?.let { result += Phoneme(letter, it) }
            }
            i += rule.first.length
        }
        return result
    }
}
