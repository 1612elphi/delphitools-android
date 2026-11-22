package com.delphi.delphitools.tools.devtools

import com.delphi.delphitools.tools.turbonerd.MorseEngine

enum class CipherFailure(val text: String) {
    AffineCoprime("'a' must be coprime with 26 (try 1,3,5,7,9,11,15,17,19,21,23,25)."),
    Base32Character("Invalid Base32 character."),
    HexLength("Hex must have even length."),
    HexCharacter("Invalid hex character."),
    BinaryLength("Binary must be a multiple of 8 bits."),
    Base64Invalid("Invalid Base64."),
}

class CipherError(val failure: CipherFailure) : Exception(failure.text)

enum class CipherId(val label: String) {
    Caesar("Caesar"),
    Rot13("ROT13"),
    Rot47("ROT47"),
    Atbash("Atbash"),
    Vigenere("Vigenère"),
    Affine("Affine"),
    RailFence("Rail fence"),
    Morse("Morse code"),
    A1z26("A1Z26"),
    Bacon("Bacon's cipher"),
    Base64("Base64"),
    Base32("Base32"),
    Hex("Hex"),
    Binary("Binary"),
}

data class CipherParams(
    val caesarShift: Int = 3,
    val vigenereKey: String = "",
    val affineA: Int = 5,
    val affineB: Int = 8,
    val rails: Int = 3,
)

data class CipherCandidate(
    val cipher: String,
    val cipherId: CipherId,
    val detail: String,
    val output: String,
    val score: Double,
    val params: CipherParams? = null,
)

/** dt-ios CipherEngine. */
object CipherEngine {
    val affineCoprimes = listOf(1, 3, 5, 7, 9, 11, 15, 17, 19, 21, 23, 25)

    val samples = listOf(
        "Caesar" to "AOL XBPJR IYVDU MVE QBTWZ VCLY AOL SHGF KVN",
        "ROT13" to "Abj vf gur jvagre bs bhe qvfpbagrag znqr tybevbhf fhzzre",
        "Vigenère" to "Vsxt tmq wpt aplrrh ha wlpn hzh eys han asn vbkich kzhex ioqqkd ioiw rpklz zict hvg edz dpb rzi",
        "Morse" to ".... . .-.. .-.. --- / .-- --- .-. .-.. -..",
        "Hex" to "50 72 69 76 61 63 79 20 69 73 20 70 6f 77 65 72 2e",
        "Base64" to "VGhlIGNyb3cgZmxpZXMgYXQgbWlkbmlnaHQu",
    )

    private val englishFrequency = mapOf(
        'A' to 8.167, 'B' to 1.492, 'C' to 2.782, 'D' to 4.253, 'E' to 12.702, 'F' to 2.228,
        'G' to 2.015, 'H' to 6.094, 'I' to 6.966, 'J' to 0.153, 'K' to 0.772, 'L' to 4.025,
        'M' to 2.406, 'N' to 6.749, 'O' to 7.507, 'P' to 1.929, 'Q' to 0.095, 'R' to 5.987,
        'S' to 6.327, 'T' to 9.056, 'U' to 2.758, 'V' to 0.978, 'W' to 2.36, 'X' to 0.15,
        'Y' to 1.974, 'Z' to 0.074,
    )

    private val commonWords = setOf(
        "THE", "BE", "TO", "OF", "AND", "A", "IN", "THAT", "HAVE", "I", "IT",
        "FOR", "NOT", "ON", "WITH", "HE", "AS", "YOU", "DO", "AT", "THIS",
        "BUT", "HIS", "BY", "FROM", "THEY", "WE", "SAY", "HER", "SHE", "OR",
        "AN", "WILL", "MY", "ONE", "ALL", "WOULD", "THERE", "THEIR", "WHAT",
        "SO", "UP", "OUT", "IF", "ABOUT", "WHO", "GET", "WHICH", "GO", "ME",
        "WHEN", "MAKE", "CAN", "LIKE", "TIME", "NO", "JUST", "HIM", "KNOW",
        "TAKE", "PEOPLE", "INTO", "YEAR", "YOUR", "GOOD", "SOME", "COULD",
        "THEM", "SEE", "OTHER", "THAN", "THEN", "NOW", "LOOK", "ONLY", "COME",
        "ITS", "OVER", "THINK", "ALSO", "BACK", "AFTER", "USE", "TWO", "HOW",
        "OUR", "WORK", "FIRST", "WELL", "WAY", "EVEN", "NEW", "WANT", "BECAUSE",
        "ANY", "THESE", "GIVE", "DAY", "MOST", "US", "IS", "WAS", "ARE", "BEEN",
        "HAS", "HAD", "WERE", "SAID",
    )

    private fun Char.isAsciiLetter() = this in 'A'..'Z' || this in 'a'..'z'

    fun chiSquaredScore(text: String): Double {
        val counts = IntArray(26)
        var total = 0
        for (char in text) {
            if (!char.isAsciiLetter()) continue
            counts[char.uppercaseChar() - 'A']++
            total++
        }
        if (total == 0) return Double.POSITIVE_INFINITY
        var chi = 0.0
        for ((letter, frequency) in englishFrequency) {
            val observed = counts[letter - 'A'].toDouble()
            val expected = frequency / 100 * total
            chi += (observed - expected) * (observed - expected) / expected
        }
        return chi
    }

    fun commonWordRatio(text: String): Double {
        val words = text.uppercase().split(Regex("[^A-Z]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return 0.0
        return words.count { it in commonWords }.toDouble() / words.size
    }

    fun englishLikeness(text: String): Double {
        if (text.count { it.isAsciiLetter() } < 3) return 0.0
        // hand-picked chi ceiling
        val chiScore = maxOf(0.0, 1 - chiSquaredScore(text) / 100)
        val wordScore = minOf(1.0, commonWordRatio(text) * 3)
        return chiScore * 0.6 + wordScore * 0.4
    }

    private inline fun mapLetters(text: String, transform: (code: Int, base: Int) -> Int): String {
        val out = StringBuilder(text.length)
        for (char in text) {
            val base = when (char) {
                in 'A'..'Z' -> 'A'.code
                in 'a'..'z' -> 'a'.code
                else -> {
                    out.append(char)
                    continue
                }
            }
            out.append((transform(char.code - base, base) + base).toChar())
        }
        return out.toString()
    }

    fun caesarShift(text: String, shift: Int): String {
        val s = ((shift % 26) + 26) % 26
        return mapLetters(text) { x, _ -> (x + s) % 26 }
    }

    fun rot47(text: String): String = text.map { if (it.code in 33..126) (33 + (it.code - 33 + 47) % 94).toChar() else it }.joinToString("")

    fun atbash(text: String) = mapLetters(text) { x, _ -> 25 - x }

    fun vigenere(text: String, key: String, encode: Boolean): String {
        val cleanKey = key.uppercase().filter { it in 'A'..'Z' }
        if (cleanKey.isEmpty()) return text
        var ki = 0
        return mapLetters(text) { x, _ ->
            val shift = cleanKey[ki % cleanKey.length] - 'A'
            ki++
            (x + (if (encode) shift else -shift) + 26) % 26
        }
    }

    fun modInverse(a: Int, m: Int): Int? {
        val base = ((a % m) + m) % m
        return (1 until m).firstOrNull { (base * it) % m == 1 }
    }

    fun affine(text: String, a: Int, b: Int, encode: Boolean): String {
        val inverse = if (encode) 0 else modInverse(a, 26) ?: throw CipherError(CipherFailure.AffineCoprime)
        return mapLetters(text) { x, _ -> if (encode) (a * x + b) % 26 else (inverse * ((x - b + 26 * 26) % 26)) % 26 }
    }

    private class Chunk(val text: String, val isWhitespaceRun: Boolean)

    private fun chunks(text: String): List<Chunk> {
        val out = mutableListOf<Chunk>()
        val current = StringBuilder()
        var isSpace: Boolean? = null
        for (char in text) {
            if (isSpace != char.isWhitespace() && current.isNotEmpty()) {
                out += Chunk(current.toString(), isSpace == true)
                current.clear()
            }
            isSpace = char.isWhitespace()
            current.append(char)
        }
        if (current.isNotEmpty()) out += Chunk(current.toString(), isSpace == true)
        return out
    }

    fun morseEncode(text: String) = chunks(text).joinToString(" ") { chunk ->
        if (chunk.isWhitespaceRun) "/" else chunk.text.uppercase().mapNotNull { MorseEngine.table[it] }.joinToString(" ")
    }

    fun morseDecode(text: String) = MorseEngine.words(text, minimumGap = 2)
        .joinToString(" ") { word -> word.mapNotNull { MorseEngine.reverse[it] }.joinToString("") }

    fun a1z26Encode(text: String) = chunks(text).joinToString("") { chunk ->
        if (chunk.isWhitespaceRun) " / " else chunk.text.uppercase().filter { it in 'A'..'Z' }.map { (it.code - 64).toString() }.joinToString("-")
    }

    /** One A1Z26 piece: parseInt semantics, leading digits only; null outside 1 to 26. */
    internal fun a1z26Letter(piece: String): Char? = piece.takeWhile { it in '0'..'9' }.toIntOrNull()?.takeIf { it in 1..26 }?.let { (64 + it).toChar() }

    internal fun a1z26Pieces(word: List<String>) = word.flatMap { it.split('-', ',') }.filter { it.isNotEmpty() }

    fun a1z26Decode(text: String) = MorseEngine.words(text, minimumGap = 2)
        .joinToString(" ") { word -> a1z26Pieces(word).mapNotNull(::a1z26Letter).joinToString("") }

    val bacon: Map<Char, String> = ('A'..'Z').withIndex().associate { (index, letter) ->
        letter to (0 until 5).joinToString("") { if ((index shr (4 - it)) and 1 == 1) "B" else "A" }
    }

    private val baconReverse = bacon.entries.associate { (letter, code) -> code to letter }

    fun baconLetter(code: String) = baconReverse[code]

    fun baconEncode(text: String) = chunks(text).joinToString("") { chunk ->
        if (chunk.isWhitespaceRun) " " else chunk.text.uppercase().filter { it in 'A'..'Z' }.mapNotNull { bacon[it] }.joinToString(" ")
    }

    fun baconDecode(text: String): String {
        val cleaned = text.uppercase().filter { it == 'A' || it == 'B' }
        return (0..cleaned.length - 5 step 5).map { baconReverse[cleaned.substring(it, it + 5)] ?: '?' }.joinToString("")
    }

    /** Rail per character, the engine's zig-zag; more rails than characters is one rail per character. */
    fun railPattern(count: Int, rails: Int): List<Int> {
        val effective = maxOf(2, minOf(rails, maxOf(count, 2)))
        var rail = 0
        var step = 1
        return List(count) {
            val here = rail
            if (rail == 0) step = 1 else if (rail == effective - 1) step = -1
            rail += step
            here
        }
    }

    fun railFenceEncode(text: String, rails: Int): String {
        val characters = EncoderEngine.characters(text)
        if (rails < 2 || characters.size <= 1) return text
        val pattern = railPattern(characters.size, rails)
        return (0 until maxOf(2, minOf(rails, characters.size))).joinToString("") { rail ->
            characters.indices.filter { pattern[it] == rail }.joinToString("") { characters[it] }
        }
    }

    fun railFenceDecode(text: String, rails: Int): String {
        val characters = EncoderEngine.characters(text)
        if (rails < 2 || characters.size <= 1) return text
        val pattern = railPattern(characters.size, rails)
        val count = maxOf(2, minOf(rails, characters.size))
        val rows = mutableListOf<List<String>>()
        var index = 0
        for (rail in 0 until count) {
            val length = pattern.count { it == rail }
            rows += characters.subList(index, index + length)
            index += length
        }
        val cursors = IntArray(count)
        return pattern.joinToString("") { rows[it][cursors[it]++] }
    }

    fun base64Decode(text: String): String =
        EncoderEngine.base64Decode(text, urlSafe = false).getOrElse { throw CipherError(CipherFailure.Base64Invalid) }

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private fun eightBits(byte: Byte) = Integer.toBinaryString(byte.toInt() and 0xFF).padStart(8, '0')

    fun base32Encode(text: String): String {
        val bits = StringBuilder(text.toByteArray().joinToString("") { eightBits(it) })
        while (bits.length % 5 != 0) bits.append('0')
        val out = StringBuilder()
        for (i in bits.indices step 5) out.append(BASE32_ALPHABET[bits.substring(i, i + 5).toInt(2)])
        while (out.length % 8 != 0) out.append('=')
        return out.toString()
    }

    fun base32Decode(text: String): String {
        val cleaned = text.uppercase().filterNot { it.isWhitespace() }.trimEnd('=')
        val bits = StringBuilder()
        for (char in cleaned) {
            val index = BASE32_ALPHABET.indexOf(char)
            if (index < 0) throw CipherError(CipherFailure.Base32Character)
            bits.append(Integer.toBinaryString(index).padStart(5, '0'))
        }
        val bytes = ByteArray(bits.length / 8) { bits.substring(it * 8, it * 8 + 8).toInt(2).toByte() }
        return String(bytes, Charsets.UTF_8)
    }

    fun hexEncode(text: String) = text.toByteArray().joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }

    fun hexDecode(text: String): String {
        var cleaned = text.filterNot { it.isWhitespace() }
        if (cleaned.lowercase().startsWith("0x")) cleaned = cleaned.drop(2)
        if (cleaned.length % 2 != 0) throw CipherError(CipherFailure.HexLength)
        val bytes = ByteArray(cleaned.length / 2) {
            val pair = cleaned.substring(it * 2, it * 2 + 2)
            if (!pair.all(::isHexDigit)) throw CipherError(CipherFailure.HexCharacter)
            pair.toInt(16).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }

    fun binaryEncode(text: String) = text.toByteArray().joinToString(" ") { eightBits(it) }

    fun binaryDecode(text: String): String {
        val cleaned = text.filter { it == '0' || it == '1' }
        if (cleaned.isEmpty() || cleaned.length % 8 != 0) throw CipherError(CipherFailure.BinaryLength)
        val bytes = ByteArray(cleaned.length / 8) { cleaned.substring(it * 8, it * 8 + 8).toInt(2).toByte() }
        return String(bytes, Charsets.UTF_8)
    }

    fun indexOfCoincidence(text: String): Double {
        val counts = IntArray(26)
        var n = 0
        for (char in text) {
            val upper = char.uppercaseChar()
            if (upper !in 'A'..'Z') continue
            counts[upper - 'A']++
            n++
        }
        if (n < 2) return 0.0
        return counts.sumOf { it * (it - 1) }.toDouble() / (n * (n - 1))
    }

    private fun bestCaesarShiftForGroup(group: String): Int {
        var bestShift = 0
        var bestChi = Double.POSITIVE_INFINITY
        for (s in 0 until 26) {
            val chi = chiSquaredScore(caesarShift(group, -s))
            if (chi < bestChi) {
                bestChi = chi
                bestShift = s
            }
        }
        return bestShift
    }

    private fun stride(letters: String, offset: Int, length: Int) = (offset until letters.length step length).map { letters[it] }.joinToString("")

    fun guessVigenereKey(text: String, maxLength: Int = 10): Pair<String, Double>? {
        val letters = text.uppercase().filter { it in 'A'..'Z' }
        if (letters.length < 20) return null
        val upper = minOf(maxLength, letters.length / 4)
        if (upper < 2) return null
        val iocs = mutableListOf(0.0, 0.0)
        for (length in 2..upper) {
            iocs += (0 until length).sumOf { indexOfCoincidence(stride(letters, it, length)) } / length
        }
        // shortest key near best
        val maxIoC = iocs.max()
        if (maxIoC < 0.055) return null
        val bestLength = (2..upper).firstOrNull { iocs[it] >= maxIoC - 0.008 } ?: return null
        val key = (0 until bestLength).map { 'A' + bestCaesarShiftForGroup(stride(letters, it, bestLength)) }.joinToString("")
        return key to iocs[bestLength]
    }

    fun runManual(input: String, cipher: CipherId, encode: Boolean, params: CipherParams): String {
        if (input.isEmpty()) return ""
        return when (cipher) {
            CipherId.Caesar -> caesarShift(input, if (encode) params.caesarShift else -params.caesarShift)
            CipherId.Rot13 -> caesarShift(input, 13)
            CipherId.Rot47 -> rot47(input)
            CipherId.Atbash -> atbash(input)
            CipherId.Vigenere -> vigenere(input, params.vigenereKey, encode)
            CipherId.Affine -> affine(input, params.affineA, params.affineB, encode)
            CipherId.RailFence -> if (encode) railFenceEncode(input, params.rails) else railFenceDecode(input, params.rails)
            CipherId.Morse -> if (encode) morseEncode(input) else morseDecode(input)
            CipherId.A1z26 -> if (encode) a1z26Encode(input) else a1z26Decode(input)
            CipherId.Bacon -> if (encode) baconEncode(input) else baconDecode(input)
            CipherId.Base64 -> if (encode) EncoderEngine.base64Encode(input) else base64Decode(input)
            CipherId.Base32 -> if (encode) base32Encode(input) else base32Decode(input)
            CipherId.Hex -> if (encode) hexEncode(input) else hexDecode(input)
            CipherId.Binary -> if (encode) binaryEncode(input) else binaryDecode(input)
        }
    }

    // scores settle on prefix
    private const val PROBE_LIMIT = 1500

    private fun attempt(decode: () -> String): String? = try {
        decode()
    } catch (_: CipherError) {
        null
    }

    private fun isHexDigit(char: Char) = char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'

    private fun isAsciiAlphanumeric(char: Char) = char in '0'..'9' || char.isAsciiLetter()

    fun classifyAndDecode(input: String): List<CipherCandidate> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return emptyList()
        val candidates = mutableListOf<CipherCandidate>()
        val bare = trimmed.filterNot { it.isWhitespace() }

        if (bare.isNotEmpty() && trimmed.all { it in ".-/|" || it.isWhitespace() }) {
            val out = morseDecode(trimmed)
            if (out.isNotEmpty()) candidates += CipherCandidate("Morse code", CipherId.Morse, "", out, 0.95)
        }

        if (bare.length >= 8 && trimmed.all { it == '0' || it == '1' || it.isWhitespace() }) {
            attempt { binaryDecode(trimmed) }?.takeIf { isReadable(it) && englishLikeness(it) > 0.05 }?.let {
                candidates += CipherCandidate("Binary", CipherId.Binary, "8-bit", it, 0.9)
            }
        }

        val hexBody = if (trimmed.lowercase().startsWith("0x")) trimmed.drop(2) else trimmed
        if (hexBody.count { !it.isWhitespace() } >= 4 && hexBody.all { isHexDigit(it) || it.isWhitespace() }) {
            attempt { hexDecode(trimmed) }?.takeIf { isReadable(it) && englishLikeness(it) > 0.05 }?.let {
                candidates += CipherCandidate("Hex", CipherId.Hex, "", it, 0.88)
            }
        }

        if (bare.length >= 4 && bare.length % 4 == 0 && trimmed.all { isAsciiAlphanumeric(it) || it in "+/=" || it.isWhitespace() }) {
            attempt { base64Decode(trimmed) }?.takeIf { isReadable(it) && englishLikeness(it) > 0.05 }?.let {
                candidates += CipherCandidate("Base64", CipherId.Base64, "", it, 0.85)
            }
        }

        val cleanedB32 = bare.uppercase().trimEnd('=')
        if (cleanedB32.length >= 8 && cleanedB32.all { it in 'A'..'Z' || it in '2'..'7' }) {
            attempt { base32Decode(trimmed) }?.takeIf { isReadable(it) && englishLikeness(it) > 0.05 }?.let {
                candidates += CipherCandidate("Base32", CipherId.Base32, "", it, 0.82)
            }
        }

        if (trimmed.all { it in "ABab" || it.isWhitespace() } && bare.length >= 10) {
            val out = baconDecode(trimmed)
            if (englishLikeness(out) > 0.1) candidates += CipherCandidate("Bacon's cipher", CipherId.Bacon, "", out, 0.78)
        }

        if (trimmed.any { it in '0'..'9' } && trimmed.all { it in '0'..'9' || it.isWhitespace() || it in "-/,." }) {
            val out = a1z26Decode(trimmed)
            if (out.isNotEmpty() && englishLikeness(out) > 0.1) candidates += CipherCandidate("A1Z26", CipherId.A1z26, "", out, 0.75)
        }

        if (trimmed.any { it.isAsciiLetter() }) candidates += letterCiphers(trimmed, trimmed.take(PROBE_LIMIT))

        val best = LinkedHashMap<String, CipherCandidate>()
        for (candidate in candidates) {
            val previous = best[candidate.output]
            if (previous == null || candidate.score > previous.score) best[candidate.output] = candidate
        }
        return best.values.sortedByDescending { it.score }.take(8)
    }

    // probe bounds trial decodes
    private fun letterCiphers(trimmed: String, probe: String): List<CipherCandidate> {
        val candidates = mutableListOf<CipherCandidate>()

        val caesarTries = (1 until 26).map { it to englishLikeness(caesarShift(probe, -it)) }.sortedByDescending { it.second }
        for ((shift, score) in caesarTries.take(3)) {
            if (score <= 0.2) continue
            val isRot13 = shift == 13
            candidates += CipherCandidate(
                if (isRot13) "ROT13" else "Caesar (shift $shift)",
                if (isRot13) CipherId.Rot13 else CipherId.Caesar,
                "",
                caesarShift(trimmed, -shift),
                score,
                if (isRot13) null else CipherParams(caesarShift = shift),
            )
        }

        val atbashScore = englishLikeness(atbash(probe))
        if (atbashScore > 0.2) candidates += CipherCandidate("Atbash", CipherId.Atbash, "", atbash(trimmed), atbashScore)

        val rottedScore = englishLikeness(rot47(probe))
        if (rottedScore > 0.25) candidates += CipherCandidate("ROT47", CipherId.Rot47, "", rot47(trimmed), rottedScore)

        guessVigenereKey(probe)?.let { (key, _) ->
            val score = englishLikeness(vigenere(probe, key, false))
            if (score > 0.25) {
                candidates += CipherCandidate("Vigenère", CipherId.Vigenere, "key: $key", vigenere(trimmed, key, false), score * 0.95, CipherParams(vigenereKey = key))
            }
        }

        var bestAffine: Triple<Int, Int, Double>? = null
        for (a in affineCoprimes) {
            if (a == 1) continue
            for (b in 0 until 26) {
                val score = englishLikeness(affine(probe, a, b, false))
                if (bestAffine == null || score > bestAffine.third) bestAffine = Triple(a, b, score)
            }
        }
        if (bestAffine != null && bestAffine.third > 0.3) {
            val (a, b, score) = bestAffine
            candidates += CipherCandidate("Affine", CipherId.Affine, "a=$a, b=$b", affine(trimmed, a, b, false), score * 0.9, CipherParams(affineA = a, affineB = b))
        }

        var bestRail: Triple<Int, String, Double>? = null
        for (r in 2..8) {
            val out = railFenceDecode(trimmed, r)
            val score = englishLikeness(out)
            if (bestRail == null || score > bestRail.third) bestRail = Triple(r, out, score)
        }
        if (bestRail != null && bestRail.third > 0.3) {
            val (rails, out, score) = bestRail
            candidates += CipherCandidate("Rail fence", CipherId.RailFence, "$rails rails", out, score * 0.85, CipherParams(rails = rails))
        }

        return candidates
    }

    /**
     * Decoded bytes that are text: valid UTF-8 with no control characters but whitespace. Letters in random bytes
     * otherwise scored above the real reading (the Caesar sample read as Base32).
     */
    fun isReadable(text: String) = text.codePoints().noneMatch {
        it == 0xFFFD || (Character.getType(it) == Character.CONTROL.toInt() && it !in 0x09..0x0D && it != 0x85)
    }
}
