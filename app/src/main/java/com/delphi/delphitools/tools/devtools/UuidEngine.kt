package com.delphi.delphitools.tools.devtools

import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

enum class IdentifierKind(val label: String) {
    Uuid4("UUID v4"),
    Uuid7("UUID v7"),
    NanoId("Nano ID"),
}

/** dt-ios UUIDEngine. */
object UuidEngine {
    const val NANO_ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-"
    const val NANO_ID_LENGTH = 21
    const val COUNT_MIN = 1
    const val COUNT_MAX = 100

    private val random = SecureRandom()

    private fun randomBytes(count: Int) = ByteArray(maxOf(0, count)).also(random::nextBytes)

    private fun uuidString(bytes: ByteArray): String {
        val hex = bytes.joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xFF) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }

    fun uuid4(): String = UUID.randomUUID().toString()

    fun uuid7(milliseconds: Long = System.currentTimeMillis()): String {
        val bytes = randomBytes(16)
        for (offset in 0 until 6) bytes[offset] = (milliseconds shr (8 * (5 - offset))).toByte()
        bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x70).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
        return uuidString(bytes)
    }

    fun uuid7Milliseconds(uuid: String): Long? = uuid.replace("-", "").take(12).toLongOrNull(16)

    // 64 avoids modulo bias
    fun nanoId(length: Int = NANO_ID_LENGTH): String =
        randomBytes(length).map { NANO_ID_ALPHABET[(it.toInt() and 0xFF) % 64] }.joinToString("")

    fun clamp(count: Int) = count.coerceIn(COUNT_MIN, COUNT_MAX)

    fun generate(kind: IdentifierKind): String = when (kind) {
        IdentifierKind.Uuid4 -> uuid4()
        IdentifierKind.Uuid7 -> uuid7()
        IdentifierKind.NanoId -> nanoId()
    }

    fun generate(kind: IdentifierKind, count: Int): List<String> = List(clamp(count)) { generate(kind) }

    fun format(id: String, kind: IdentifierKind, uppercase: Boolean, stripHyphens: Boolean): String {
        if (kind == IdentifierKind.NanoId) return id
        val text = if (stripHyphens) id.replace("-", "") else id
        return if (uppercase) text.uppercase(Locale.ROOT) else text
    }
}

/** dt-ios UUIDAnatomy's model: which field each bit of an identifier belongs to. */
object UuidAnatomy {
    enum class Field { Time, Version, Variant, Random }

    /** A character of the identifier and its bits, most significant first. */
    data class Column(val character: Char, val bits: List<Boolean>, val fields: List<Field>)

    fun bitCounts(kind: IdentifierKind): List<Pair<Field, Int>> = when (kind) {
        IdentifierKind.Uuid4 -> listOf(Field.Version to 4, Field.Variant to 2, Field.Random to 122)
        IdentifierKind.Uuid7 -> listOf(Field.Time to 48, Field.Version to 4, Field.Variant to 2, Field.Random to 74)
        IdentifierKind.NanoId -> listOf(Field.Random to 126)
    }

    /** RFC 9562 layout: nibble 12 is the version, the top two bits of nibble 16 the variant; v7 puts the time in nibbles 0 to 11. Null for a hyphen. */
    fun columns(text: String, kind: IdentifierKind): List<Column?> {
        if (kind == IdentifierKind.NanoId) {
            return text.map { character ->
                val value = UuidEngine.NANO_ID_ALPHABET.indexOf(character).coerceAtLeast(0)
                Column(character, List(6) { (value shr (5 - it)) and 1 == 1 }, List(6) { Field.Random })
            }
        }
        var nibble = 0
        return text.map { character ->
            val value = "0123456789abcdef".indexOf(character.lowercaseChar())
            if (value < 0) return@map null
            val fields = when {
                nibble == 12 -> List(4) { Field.Version }
                nibble == 16 -> listOf(Field.Variant, Field.Variant, Field.Random, Field.Random)
                nibble < 12 && kind == IdentifierKind.Uuid7 -> List(4) { Field.Time }
                else -> List(4) { Field.Random }
            }
            nibble++
            Column(character, List(4) { (value shr (3 - it)) and 1 == 1 }, fields)
        }
    }

    private val stampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC)

    fun stamp(milliseconds: Long): String = stampFormat.format(Instant.ofEpochMilli(milliseconds))
}
