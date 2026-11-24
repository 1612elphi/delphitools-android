package com.delphi.delphitools.tools.pdf

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.Inflater

/** A PDF name, kept as written after the slash. */
data class PdfName(val name: String)

data class PdfRef(val num: Int, val gen: Int = 0)

class PdfString(val bytes: ByteArray)

/** Dictionary keys without the slash; a null value is an absent key. */
typealias PdfDict = MutableMap<String, Any?>

/** [data] as stored, still encoded by its /Filter. */
class PdfStream(val dict: PdfDict, val data: ByteArray)

class PdfSyntaxException(message: String) : Exception(message)

private fun isWhite(b: Int) = b == 0 || b == 9 || b == 10 || b == 12 || b == 13 || b == 32
private fun isDelimiter(b: Int) = b in "()<>[]{}/%".map { it.code }
private fun isRegular(b: Int) = !isWhite(b) && !isDelimiter(b)

private val plainReal = Regex("""[+-]?(\d+\.?\d*|\.\d+)""")

/** Inflated bytes allowed for a stream when the caller sets no tighter limit. */
internal const val MAX_INFLATED = 256L shl 20

/** Reads PDF objects out of [bytes]; [resolveLength] turns an indirect /Length into a number. */
internal class PdfLexer(private val bytes: ByteArray, var pos: Int, private val resolveLength: (Any?) -> Any? = { it }) {
    private fun at(i: Int) = if (i < bytes.size) bytes[i].toInt() and 0xff else -1

    fun skipWhite() {
        while (pos < bytes.size) {
            val b = at(pos)
            if (isWhite(b)) {
                pos++
            } else if (b == '%'.code) {
                while (pos < bytes.size && at(pos) != 10 && at(pos) != 13) pos++
            } else {
                return
            }
        }
    }

    fun token(): String {
        skipWhite()
        val start = pos
        while (pos < bytes.size && isRegular(at(pos))) pos++
        return String(bytes, start, pos - start, Charsets.ISO_8859_1)
    }

    fun peekToken(): String {
        val saved = pos
        return token().also { pos = saved }
    }

    fun int(): Int = token().toIntOrNull() ?: throw PdfSyntaxException("integer expected at $pos")

    fun value(): Any? {
        skipWhite()
        val b = at(pos)
        return when {
            b < 0 -> throw PdfSyntaxException("end of file")
            b == '/'.code -> {
                pos++
                val start = pos
                while (pos < bytes.size && isRegular(at(pos))) pos++
                PdfName(String(bytes, start, pos - start, Charsets.ISO_8859_1))
            }
            b == '('.code -> literal()
            b == '<'.code && at(pos + 1) == '<'.code -> {
                pos += 2
                val dict: PdfDict = LinkedHashMap()
                while (true) {
                    skipWhite()
                    if (at(pos) == '>'.code && at(pos + 1) == '>'.code) {
                        pos += 2
                        break
                    }
                    val key = value() as? PdfName ?: throw PdfSyntaxException("name expected at $pos")
                    val v = value()
                    if (v != null) dict[key.name] = v
                }
                dict
            }
            b == '<'.code -> hex()
            b == '['.code -> {
                pos++
                val list = ArrayList<Any?>()
                while (true) {
                    skipWhite()
                    if (at(pos) == ']'.code) {
                        pos++
                        break
                    }
                    list.add(value())
                }
                list
            }
            b == ')'.code || b == '>'.code || b == ']'.code || b == '{'.code || b == '}'.code -> {
                pos++
                throw PdfSyntaxException("unexpected ${b.toChar()} at ${pos - 1}")
            }
            else -> {
                val word = token()
                when (word) {
                    "true" -> true
                    "false" -> false
                    "null" -> null
                    else -> number(word) ?: throw PdfSyntaxException("unexpected $word at $pos")
                }
            }
        }
    }

    private fun number(word: String): Any? {
        val whole = word.toLongOrNull()
        if (whole == null) {
            val plain = if (word.startsWith("--")) word.drop(1) else word
            if (!plainReal.matches(plain)) return null
            return plain.toDouble().takeIf { it.isFinite() }
        }
        val saved = pos
        val gen = token().toIntOrNull()
        if (gen != null && token() == "R" && whole in 0..Int.MAX_VALUE) return PdfRef(whole.toInt(), gen)
        pos = saved
        return if (whole in Int.MIN_VALUE..Int.MAX_VALUE) whole.toInt() else whole
    }

    private fun literal(): PdfString {
        pos++
        val out = ByteArrayOutputStream()
        var depth = 1
        while (pos < bytes.size) {
            var b = at(pos++)
            when (b) {
                '('.code -> depth++
                ')'.code -> if (--depth == 0) return PdfString(out.toByteArray())
                '\\'.code -> {
                    b = at(pos++)
                    when (b) {
                        'n'.code -> b = 10
                        'r'.code -> b = 13
                        't'.code -> b = 9
                        'b'.code -> b = 8
                        'f'.code -> b = 12
                        13 -> {
                            if (at(pos) == 10) pos++
                            continue
                        }
                        10 -> continue
                        in '0'.code..'7'.code -> {
                            var code = b - '0'.code
                            repeat(2) {
                                if (at(pos) in '0'.code..'7'.code) code = code * 8 + (at(pos++) - '0'.code)
                            }
                            b = code and 0xff
                        }
                    }
                }
                13 -> {
                    // an unescaped end of line reads as a line feed
                    if (at(pos) == 10) pos++
                    b = 10
                }
            }
            out.write(b)
        }
        throw PdfSyntaxException("unterminated string")
    }

    private fun hex(): PdfString {
        pos++
        val out = ByteArrayOutputStream()
        var high = -1
        while (pos < bytes.size) {
            val b = at(pos++)
            if (b == '>'.code) break
            val digit = Character.digit(b, 16)
            if (digit < 0) continue
            if (high < 0) {
                high = digit
            } else {
                out.write(high * 16 + digit)
                high = -1
            }
        }
        if (high >= 0) out.write(high * 16)
        return PdfString(out.toByteArray())
    }

    /** `num gen obj value endobj`, a stream when `stream` follows the dictionary. */
    fun indirect(): Pair<PdfRef, Any?> {
        val num = int()
        val gen = int()
        if (token() != "obj") throw PdfSyntaxException("obj expected at $pos")
        val value = value()
        val saved = pos
        if (value !is MutableMap<*, *> || token() != "stream") {
            pos = saved
            return PdfRef(num, gen) to value
        }
        @Suppress("UNCHECKED_CAST")
        val dict = value as PdfDict
        if (at(pos) == 13) pos++
        if (at(pos) == 10) pos++
        val start = pos
        val length = (resolveLength(dict["Length"]) as? Number)?.toLong()
        val end = if (length != null && length in 0..(bytes.size - start).toLong() && endsStream(start + length.toInt())) {
            start + length.toInt()
        } else {
            val found = indexOf(bytes, "endstream".toByteArray(Charsets.ISO_8859_1), start)
            if (found < 0) throw PdfSyntaxException("endstream missing")
            var e = found
            if (e > start && at(e - 1) == 10) e--
            if (e > start && at(e - 1) == 13) e--
            e
        }
        pos = end
        val data = bytes.copyOfRange(start, end)
        token()
        return PdfRef(num, gen) to PdfStream(dict, data)
    }

    private fun endsStream(end: Int): Boolean {
        val saved = pos
        pos = end
        val word = token()
        pos = saved
        return word == "endstream"
    }
}

internal fun indexOf(bytes: ByteArray, needle: ByteArray, from: Int): Int {
    outer@ for (i in from..bytes.size - needle.size) {
        for (j in needle.indices) if (bytes[i + j] != needle[j]) continue@outer
        return i
    }
    return -1
}

internal fun lastIndexOf(bytes: ByteArray, needle: ByteArray, before: Int = bytes.size): Int {
    outer@ for (i in minOf(before, bytes.size - needle.size) downTo 0) {
        for (j in needle.indices) if (bytes[i + j] != needle[j]) continue@outer
        return i
    }
    return -1
}

object PdfFilters {
    fun filters(dict: Map<String, Any?>): List<String> = when (val f = dict["Filter"]) {
        is PdfName -> listOf(f.name)
        is List<*> -> f.mapNotNull { (it as? PdfName)?.name }
        else -> emptyList()
    }

    /** Undoes FlateDecode with any PNG predictor; null for any other filter. Throws past [limit] inflated bytes. */
    fun decode(stream: PdfStream, limit: Long = MAX_INFLATED): ByteArray? {
        val filters = filters(stream.dict)
        if (filters.isEmpty()) return stream.data
        if (filters != listOf("FlateDecode")) return null
        val inflated = inflate(stream.data, limit)
        val parms = when (val p = stream.dict["DecodeParms"]) {
            is Map<*, *> -> p
            is List<*> -> p.firstOrNull() as? Map<*, *>
            else -> null
        }
        val predictor = (parms?.get("Predictor") as? Number)?.toInt() ?: 1
        if (predictor < 10) return if (predictor == 1) inflated else null
        val colours = (parms?.get("Colors") as? Number)?.toInt() ?: 1
        val bits = (parms?.get("BitsPerComponent") as? Number)?.toInt() ?: 8
        val columns = (parms?.get("Columns") as? Number)?.toInt() ?: 1
        return unpredict(inflated, maxOf(1, colours * bits / 8), (colours * bits * columns + 7) / 8)
    }

    fun inflate(data: ByteArray, limit: Long = MAX_INFLATED): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream(minOf(data.size.toLong() * 3, limit).toInt())
        val buffer = ByteArray(16 * 1024)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                if (out.size() + n.toLong() > limit) throw PdfSyntaxException("inflated past $limit bytes")
                out.write(buffer, 0, n)
            }
        } catch (_: java.util.zip.DataFormatException) {
            // a damaged tail keeps what came before it
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }

    fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream(data.size / 2 + 64)
        val buffer = ByteArray(16 * 1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    /** PNG row filters, one type byte per row. */
    fun unpredict(data: ByteArray, bpp: Int, rowLength: Int): ByteArray {
        val rows = data.size / (rowLength + 1)
        val out = ByteArray(rows * rowLength)
        for (r in 0 until rows) {
            val type = data[r * (rowLength + 1)].toInt()
            val src = r * (rowLength + 1) + 1
            val dst = r * rowLength
            for (i in 0 until rowLength) {
                val raw = data[src + i].toInt() and 0xff
                val left = if (i >= bpp) out[dst + i - bpp].toInt() and 0xff else 0
                val up = if (r > 0) out[dst - rowLength + i].toInt() and 0xff else 0
                val corner = if (r > 0 && i >= bpp) out[dst - rowLength + i - bpp].toInt() and 0xff else 0
                val value = when (type) {
                    1 -> raw + left
                    2 -> raw + up
                    3 -> raw + (left + up) / 2
                    4 -> {
                        val p = left + up - corner
                        val pa = Math.abs(p - left)
                        val pb = Math.abs(p - up)
                        val pc = Math.abs(p - corner)
                        raw + if (pa <= pb && pa <= pc) left else if (pb <= pc) up else corner
                    }
                    else -> raw
                }
                out[dst + i] = value.toByte()
            }
        }
        return out
    }
}

/** Writes PDF syntax for objects built from the types above. */
object PdfSerialiser {
    fun write(value: Any?, out: OutputStream) {
        when (value) {
            null -> out.ascii("null")
            is Boolean -> out.ascii(value.toString())
            is Int, is Long -> out.ascii(value.toString())
            is Number -> out.ascii(real(value.toDouble()))
            is PdfName -> out.ascii("/${value.name}")
            is PdfRef -> out.ascii("${value.num} ${value.gen} R")
            is PdfString -> {
                out.write('('.code)
                for (byte in value.bytes) {
                    val b = byte.toInt() and 0xff
                    when (b) {
                        '('.code, ')'.code, '\\'.code -> out.write(byteArrayOf('\\'.code.toByte(), b.toByte()))
                        13 -> out.ascii("\\r")
                        else -> out.write(b)
                    }
                }
                out.write(')'.code)
            }
            is List<*> -> {
                out.write('['.code)
                value.forEachIndexed { index, item ->
                    if (index > 0) out.write(' '.code)
                    write(item, out)
                }
                out.write(']'.code)
            }
            is Map<*, *> -> {
                out.ascii("<<")
                for ((key, item) in value) {
                    if (item == null) continue
                    out.ascii("/$key ")
                    write(item, out)
                    out.write('\n'.code)
                }
                out.ascii(">>")
            }
            is PdfStream -> {
                val dict = LinkedHashMap(value.dict)
                dict["Length"] = value.data.size
                write(dict, out)
                out.ascii("\nstream\n")
                out.write(value.data)
                out.ascii("\nendstream")
            }
            else -> throw IllegalArgumentException("not a PDF object: $value")
        }
    }

    fun real(value: Double): String {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) return value.toLong().toString()
        val text = BigDecimal(value).setScale(4, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString()
        return if (text == "-0") "0" else text
    }

    fun bytes(value: Any?): ByteArray = ByteArrayOutputStream().also { write(value, it) }.toByteArray()
}

internal fun OutputStream.ascii(text: String) = write(text.toByteArray(Charsets.ISO_8859_1))

/** Counts what goes through, for xref offsets. */
private class CountingStream(private val out: OutputStream) : OutputStream() {
    var count = 0L
        private set

    override fun write(b: Int) {
        out.write(b)
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        count += len
    }

    override fun flush() = out.flush()
}

/** A new PDF written front to back into [out]: objects as they come, then one classic xref table. */
class PdfWriter(out: OutputStream) {
    private val out = CountingStream(out)
    private val offsets = ArrayList<Long>()

    init {
        this.out.ascii("%PDF-1.7\n%âãÏÓ\n")
    }

    fun reserve(): Int {
        offsets.add(-1)
        return offsets.size
    }

    fun write(num: Int, value: Any?) {
        offsets[num - 1] = out.count
        out.ascii("$num 0 obj\n")
        PdfSerialiser.write(value, out)
        out.ascii("\nendobj\n")
    }

    fun add(value: Any?): PdfRef = PdfRef(reserve().also { write(it, value) })

    fun finish(root: PdfRef, info: PdfRef? = null) {
        val start = out.count
        out.ascii("xref\n0 ${offsets.size + 1}\n0000000000 65535 f\r\n")
        for (offset in offsets) {
            if (offset < 0) out.ascii("0000000000 65535 f\r\n") else out.ascii(String.format(Locale.ROOT, "%010d 00000 n\r\n", offset))
        }
        val trailer: PdfDict = linkedMapOf("Size" to offsets.size + 1, "Root" to root)
        if (info != null) trailer["Info"] = info
        out.ascii("trailer\n")
        PdfSerialiser.write(trailer, out)
        out.ascii("\nstartxref\n$start\n%%EOF\n")
        out.flush()
    }
}
