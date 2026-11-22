package com.delphi.delphitools.tools.devtools

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.text.BreakIterator
import java.util.Base64

enum class HashAlgorithm(val label: String, val bits: Int, val javaName: String) {
    Md5("MD5", 128, "MD5"),
    Sha1("SHA-1", 160, "SHA-1"),
    Sha256("SHA-256", 256, "SHA-256"),
    Sha512("SHA-512", 512, "SHA-512"),
}

enum class EncoderFailure(val text: String) {
    Base64("Invalid Base64."),
    Escape("Invalid escape"),
}

/** One symbol of the input over what it becomes; [newline] breaks the strip's row. */
data class SymbolPair(val top: String, val bottom: String, val changes: Boolean = true, val newline: Boolean = false) {
    companion object {
        val Newline = SymbolPair("", "", changes = false, newline = true)
    }
}

/** dt-ios EncoderEngine with the CipherEngine Base64 it calls. */
object EncoderEngine {
    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.!~*'()"
    private const val VISIBLE_SPACE = "␣"
    private const val NEWLINES = "\n\r\u000B\u000C\u0085  "

    /** Swift Characters: grapheme clusters. */
    fun characters(text: String): List<String> {
        val breaks = BreakIterator.getCharacterInstance().apply { setText(text) }
        val out = mutableListOf<String>()
        var start = breaks.first()
        var end = breaks.next()
        while (end != BreakIterator.DONE) {
            out += text.substring(start, end)
            start = end
            end = breaks.next()
        }
        return out
    }

    private fun isNewline(character: String) = character.isNotEmpty() && character.all { it in NEWLINES }

    internal fun strictUtf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    fun base64Encode(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray())

    /** The characters of a Base64 text without whitespace. */
    fun base64Characters(text: String): List<Char> = text.filterNot { it.isWhitespace() }.toList()

    /** Accepts missing padding and, when [urlSafe], the URL-safe alphabet. */
    fun base64Decode(text: String, urlSafe: Boolean = true): Result<String> {
        val cleaned = String(base64Characters(text).toCharArray())
        val standard = if (urlSafe) cleaned.replace('-', '+').replace('_', '/') else cleaned
        if (standard.isEmpty()) return Result.success("")
        // atob tolerates missing padding
        if (standard.length % 4 == 1) return Result.failure(EncoderError(EncoderFailure.Base64))
        val padded = standard + "=".repeat((4 - standard.length % 4) % 4)
        val data = runCatching { Base64.getDecoder().decode(padded) }.getOrNull()
            ?: return Result.failure(EncoderError(EncoderFailure.Base64))
        return Result.success(String(data, Charsets.UTF_8))
    }

    /** `encodeURIComponent`'s unreserved set: everything else is escaped. */
    fun urlEncode(text: String): String {
        val out = StringBuilder()
        for (byte in text.toByteArray()) {
            val b = byte.toInt() and 0xFF
            if (b < 0x80 && b.toChar() in UNRESERVED) out.append(b.toChar()) else out.append("%%%02X".format(b))
        }
        return out.toString()
    }

    private fun hexValue(c: Char) = "0123456789abcdef".indexOf(c.lowercaseChar())

    /** Foundation `removingPercentEncoding`: fails on a stray `%` or bytes that are not UTF-8. */
    fun urlDecode(text: String): Result<String> {
        if (text.isEmpty()) return Result.success("")
        val bytes = text.toByteArray()
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i]
            if (b == '%'.code.toByte()) {
                val hi = bytes.getOrNull(i + 1)?.let { hexValue(it.toInt().toChar()) } ?: -1
                val lo = bytes.getOrNull(i + 2)?.let { hexValue(it.toInt().toChar()) } ?: -1
                if (hi < 0 || lo < 0) return Result.failure(EncoderError(EncoderFailure.Escape))
                out.write(hi * 16 + lo)
                i += 3
            } else {
                out.write(b.toInt())
                i++
            }
        }
        return strictUtf8(out.toByteArray())?.let { Result.success(it) } ?: Result.failure(EncoderError(EncoderFailure.Escape))
    }

    fun urlEncodePairs(text: String): List<SymbolPair> = characters(text).map { character ->
        if (isNewline(character)) return@map SymbolPair.Newline
        val escaped = urlEncode(character)
        SymbolPair(if (character == " ") VISIBLE_SPACE else character, escaped, changes = escaped != character)
    }

    /** Runs of escapes read as the characters they spell; a run that is not UTF-8 reads as the replacement character. */
    fun urlDecodePairs(text: String): List<SymbolPair> {
        val characters = characters(text)
        val pairs = mutableListOf<SymbolPair>()
        fun byte(index: Int): Int? {
            if (index + 2 >= characters.size || characters[index] != "%") return null
            val hi = characters[index + 1].singleOrNull()?.let(::hexValue) ?: -1
            val lo = characters[index + 2].singleOrNull()?.let(::hexValue) ?: -1
            return if (hi < 0 || lo < 0) null else hi * 16 + lo
        }
        var i = 0
        while (i < characters.size) {
            if (byte(i) == null) {
                val character = characters[i]
                pairs += if (isNewline(character)) SymbolPair.Newline else SymbolPair(character, character, changes = false)
                i++
                continue
            }
            val run = mutableListOf<Int>()
            while (true) run += byte(i + run.size * 3) ?: break
            val bytes = ByteArray(run.size) { run[it].toByte() }
            val labels = BitRegroup.labels(bytes)
            var start = 0
            while (start < run.size) {
                var end = start + 1
                while (end < run.size && labels[end].isEmpty()) end++
                val escapes = run.subList(start, end).joinToString("") { "%%%02X".format(it) }
                val spelled = strictUtf8(bytes.copyOfRange(start, end)) ?: "�"
                pairs += SymbolPair(escapes, if (spelled == " ") VISIBLE_SPACE else spelled)
                start = end
            }
            i += run.size * 3
        }
        return pairs
    }

    /** Every digest of the text's UTF-8 bytes, exactly as typed. */
    fun digests(text: String): Map<HashAlgorithm, ByteArray> {
        val data = text.toByteArray()
        return HashAlgorithm.entries.associateWith { MessageDigest.getInstance(it.javaName).digest(data) }
    }

    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

class EncoderError(val failure: EncoderFailure) : Exception(failure.text)

/**
 * dt-ios BitRegroup: bytes regrouped into the characters of an encoding. A group is the bits of a whole number of
 * bytes and of characters (Base64: three bytes, four sixes); the input's units are bracketed over the bits, the output's under them.
 */
object BitRegroup {
    enum class Slot {
        One,
        Zero,

        /** A zero added to fill the last character, or a bit dropped because it makes no whole byte. */
        Pad,

        /** A place the input does not reach: the room `=` padding stands for. */
        Absent,
    }

    data class Unit(val label: String, val span: Int, val faint: Boolean = false)

    data class Group(val slots: List<Slot>, val top: List<Unit>, val bottom: List<Unit>)

    class Code(val bits: Int, val alphabet: String, val bytesPerGroup: Int, val padding: Char?) {
        val charsPerGroup get() = bytesPerGroup * 8 / bits

        /** The character's value; Base64 also reads the URL-safe `-` and `_`, hex and Base32 either case. */
        fun value(of: Char): Int? {
            if (bits == 6) {
                return when (of) {
                    '-' -> 62
                    '_' -> 63
                    else -> alphabet.indexOf(of).takeIf { it >= 0 }
                }
            }
            return alphabet.indexOf(of, ignoreCase = true).takeIf { it >= 0 }
        }

        companion object {
            val Base64 = Code(6, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/", 3, '=')
            val Base32 = Code(5, "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567", 5, '=')
            val Hex = Code(4, "0123456789abcdef", 1, null)
            val Binary = Code(1, "01", 1, null)
        }
    }

    /** Byte labels: a character over its first byte and nothing over the rest of it, a visible sign for space, tab and newline, the hex of anything else. */
    fun labels(bytes: ByteArray): List<String> {
        val out = MutableList(bytes.size) { "" }
        var i = 0
        while (i < bytes.size) {
            val byte = bytes[i].toInt() and 0xFF
            val length = when {
                byte < 0x80 -> 1
                byte >= 0xF8 -> 0
                byte >= 0xF0 -> 4
                byte >= 0xE0 -> 3
                byte >= 0xC0 -> 2
                else -> 0
            }
            if (length > 1 && i + length <= bytes.size) {
                val text = EncoderEngine.strictUtf8(bytes.copyOfRange(i, i + length))
                if (text != null) {
                    out[i] = text
                    i += length
                    continue
                }
            }
            out[i] = when (byte) {
                0x20 -> "␣"
                0x0A -> "↵"
                0x09 -> "⇥"
                in 0x21..0x7E -> byte.toChar().toString()
                else -> "%02X".format(byte)
            }
            i++
        }
        return out
    }

    /** Labels for [range] of [bytes], read from three bytes before it so a character cut by the range labels as in the whole. */
    fun labels(bytes: ByteArray, range: IntRange): List<String> {
        val from = maxOf(0, range.first - 3)
        val to = minOf(bytes.size, range.last + 1 + 3)
        return labels(bytes.copyOfRange(from, to)).subList(range.first - from, range.last + 1 - from)
    }

    private fun slots(byte: Int) = List(8) { if ((byte shr (7 - it)) and 1 == 1) Slot.One else Slot.Zero }

    /** An input read once; its groups are built only for the range asked for, so a long input costs no more than its tail. */
    class Reading(val count: Int, val groups: (IntRange) -> List<Group>) {
        val all get() = groups(0 until count)
    }

    // binary: label per byte
    private fun merged(units: List<Unit>, code: Code) =
        if (code.bits != 1 || units.isEmpty()) units else listOf(Unit(units.joinToString("") { it.label }, units.sumOf { it.span }))

    fun encoding(bytes: ByteArray, code: Code = Code.Base64): Reading {
        val per = code.bytesPerGroup
        val bits = code.bits
        val total = per * 8
        return Reading((bytes.size + per - 1) / per) { range ->
            val first = minOf(bytes.size, range.first * per)
            val last = minOf(bytes.size, (range.last + 1) * per)
            val names = labels(bytes, first until last)
            (first until last step per).map { start ->
                val chunk = bytes.copyOfRange(start, minOf(start + per, bytes.size))
                val real = chunk.size * 8
                val used = (real + bits - 1) / bits * bits
                val slots = chunk.flatMap { slots(it.toInt() and 0xFF) } + List(used - real) { Slot.Pad } + List(total - used) { Slot.Absent }
                val top = List(per) { i -> if (i < chunk.size) Unit(names[start - first + i], 8) else Unit("", 8, faint = true) }
                val bottom = List(code.charsPerGroup) { k ->
                    val from = k * bits
                    if (from >= used) return@List Unit(code.padding?.toString() ?: "", bits, faint = true)
                    val value = (0 until bits).fold(0) { acc, b -> acc shl 1 or if (slots[from + b] == Slot.One) 1 else 0 }
                    Unit(code.alphabet[value].toString(), bits)
                }
                Group(slots, top, merged(bottom, code))
            }
        }
    }

    /** Encoded text, whitespace already removed. Reading stops at the first character the code does not have. */
    fun decoding(characters: List<Char>, code: Code = Code.Base64): Reading {
        val bits = code.bits
        val values = mutableListOf<Int>()
        var padding = 0
        for (character in characters) {
            val value = code.value(character)
            when {
                character == code.padding -> padding++
                padding == 0 && value != null -> values += value
                else -> break
            }
        }
        val realBits = values.size * bits
        val decoded = java.io.ByteArrayOutputStream()
        var accumulator = 0
        var filled = 0
        for (value in values) {
            accumulator = accumulator shl bits or value
            filled += bits
            if (filled >= 8) {
                decoded.write((accumulator shr (filled - 8)) and 0xFF)
                filled -= 8
                accumulator = accumulator and ((1 shl filled) - 1)
            }
        }
        val bytes = decoded.toByteArray()
        val kept = bytes.size * 8
        val perGroup = code.charsPerGroup
        val per = code.bytesPerGroup
        val length = values.size + padding
        return Reading((length + perGroup - 1) / perGroup) { range ->
            val firstByte = minOf(bytes.size, range.first * per)
            val names = labels(bytes, firstByte until minOf(bytes.size, (range.last + 1) * per))
            (range.first * perGroup until minOf(length, (range.last + 1) * perGroup) step perGroup).map { start ->
                val slots = mutableListOf<Slot>()
                val top = mutableListOf<Unit>()
                for (index in start until start + perGroup) {
                    if (index >= values.size) {
                        repeat(bits) { slots += Slot.Absent }
                        top += Unit(if (index < length) code.padding?.toString() ?: "" else "", bits, faint = true)
                        continue
                    }
                    val value = values[index]
                    val bitBase = index * bits
                    slots += List(bits) { bit ->
                        when {
                            bitBase + bit in kept until realBits -> Slot.Pad
                            (value shr (bits - 1 - bit)) and 1 == 1 -> Slot.One
                            else -> Slot.Zero
                        }
                    }
                    top += Unit(characters[index].toString(), bits)
                }
                val byte = start / perGroup * per
                val bottom = List(per) { j -> if (byte + j < bytes.size) Unit(names[byte + j - firstByte], 8) else Unit("", 8, faint = true) }
                Group(slots, merged(top, code), bottom)
            }
        }
    }

    /** One group of the code, all zeros, no labels: the strip's picture with no input. */
    fun ghost(code: Code): Group {
        val group = encoding(ByteArray(code.bytesPerGroup), code).all.first()
        return group.copy(top = group.top.map { Unit("", it.span) }, bottom = group.bottom.map { Unit("", it.span) })
    }
}
