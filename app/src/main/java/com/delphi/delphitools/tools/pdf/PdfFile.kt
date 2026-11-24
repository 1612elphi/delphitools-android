package com.delphi.delphitools.tools.pdf

import java.io.ByteArrayOutputStream
import java.util.Locale

/** PDF user space rectangle, normalised so x0 < x1 and y0 < y1. */
data class PdfBox(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
    val width get() = x1 - x0
    val height get() = y1 - y0

    fun intersect(other: PdfBox): PdfBox? {
        val box = PdfBox(maxOf(x0, other.x0), maxOf(y0, other.y0), minOf(x1, other.x1), minOf(y1, other.y1))
        return if (box.width > 0 && box.height > 0) box else null
    }

    fun array(): List<Any?> = listOf(x0, y0, x1, y1)

    companion object {
        fun of(value: Any?): PdfBox? {
            val n = (value as? List<*>)?.map { (it as? Number)?.toDouble() ?: return null } ?: return null
            if (n.size != 4) return null
            return PdfBox(minOf(n[0], n[2]), minOf(n[1], n[3]), maxOf(n[0], n[2]), maxOf(n[1], n[3]))
        }
    }
}

/** A page object with the attributes it inherits from the page tree filled in. */
class PdfPage(val ref: PdfRef, val dict: Map<String, Any?>, val inherited: Map<String, Any?>) {
    fun attribute(key: String): Any? = dict[key] ?: inherited[key]
}

private val inheritable = listOf("Resources", "MediaBox", "CropBox", "Rotate")

/**
 * An existing PDF read from [bytes]: the cross-reference chain (tables, streams, hybrids), objects in object streams,
 * and the page tree. A damaged chain is rebuilt by scanning for `obj` headers.
 * ponytail: whole file held in memory; map a RandomAccessFile if large PDFs run out of heap
 */
class PdfFile(val bytes: ByteArray) {
    private val offsets = HashMap<Int, Int>()
    private val gens = HashMap<Int, Int>()
    private val compressed = HashMap<Int, Pair<Int, Int>>()
    private val cache = HashMap<Int, Any?>()
    private val objectStreams = HashMap<Int, Map<Int, Any?>>()

    /** The newest trailer, or the dictionary of the newest xref stream. */
    val trailer: Map<String, Any?>
    val startXref: Int
    val usesXrefStream: Boolean
    val rebuilt: Boolean
    val pages: List<PdfPage>

    val size: Int get() = maxOf((trailer["Size"] as? Number)?.toInt() ?: 0, (offsets.keys + compressed.keys).maxOrNull()?.plus(1) ?: 0)

    init {
        val read = runCatching { readChain() }.getOrNull()
        rebuilt = read == null
        if (read != null) {
            trailer = read.first
            startXref = read.second
            usesXrefStream = read.third
        } else {
            offsets.clear()
            gens.clear()
            compressed.clear()
            trailer = rebuild()
            startXref = -1
            usesXrefStream = compressed.isNotEmpty()
        }
        if (trailer["Encrypt"] != null) throw PdfSyntaxException("encrypted")
        // ponytail: encrypted files refused; decrypting needs RC4/AES and the standard security handler
        pages = readPages()
        if (pages.isEmpty()) throw PdfSyntaxException("no pages")
    }

    fun isPage(num: Int) = pages.any { it.ref.num == num }

    fun gen(num: Int) = gens[num] ?: 0

    /** The object numbered [num], null when there is none. */
    fun obj(num: Int): Any? {
        if (cache.containsKey(num)) return cache[num]
        cache[num] = null
        val value = offsets[num]?.let { offset ->
            runCatching { lexer(offset).indirect().second }.getOrNull()
        } ?: compressed[num]?.let { (stream, _) ->
            objectStreams.getOrPut(stream) { runCatching { readObjectStream(stream) }.getOrDefault(emptyMap()) }[num]
        }
        cache[num] = value
        return value
    }

    fun resolve(value: Any?): Any? {
        var v = value
        var hops = 0
        while (v is PdfRef && hops++ < 32) v = obj(v.num)
        return v
    }

    @Suppress("UNCHECKED_CAST")
    fun dict(value: Any?): Map<String, Any?>? = when (val v = resolve(value)) {
        is PdfStream -> v.dict
        is Map<*, *> -> v as Map<String, Any?>
        else -> null
    }

    private fun lexer(offset: Int) = PdfLexer(bytes, offset) { resolve(it) }

    private fun readChain(): Triple<Map<String, Any?>, Int, Boolean> {
        val marker = lastIndexOf(bytes, "startxref".toByteArray(Charsets.ISO_8859_1))
        if (marker < 0) throw PdfSyntaxException("startxref missing")
        val start = PdfLexer(bytes, marker + 9).int()
        var newest: Map<String, Any?>? = null
        var stream = false
        var next: Int? = start
        val seen = HashSet<Int>()
        while (next != null && seen.add(next)) {
            val lexer = lexer(next)
            val section = if (lexer.peekToken() == "xref") {
                lexer.token()
                classicSection(lexer).also { trailer ->
                    (trailer["XRefStm"] as? Number)?.toInt()?.let { streamSection(it) }
                }
            } else {
                if (newest == null) stream = true
                streamSection(next)
            }
            if (newest == null) newest = section
            next = (section["Prev"] as? Number)?.toInt()
        }
        val trailer = newest ?: throw PdfSyntaxException("no trailer")
        if (trailer["Root"] !is PdfRef) throw PdfSyntaxException("no root")
        if (dict(trailer["Root"]) == null) throw PdfSyntaxException("root unreadable")
        return Triple(trailer, start, stream)
    }

    @Suppress("UNCHECKED_CAST")
    private fun classicSection(lexer: PdfLexer): Map<String, Any?> {
        while (true) {
            val word = lexer.token()
            if (word == "trailer") return lexer.value() as? Map<String, Any?> ?: throw PdfSyntaxException("bad trailer")
            val first = word.toIntOrNull() ?: throw PdfSyntaxException("bad xref")
            val count = lexer.int()
            for (i in 0 until count) {
                val offset = lexer.token().toIntOrNull() ?: throw PdfSyntaxException("bad xref entry")
                val gen = lexer.int()
                val kind = lexer.token()
                val num = first + i
                if (kind == "n" && num !in offsets && num !in compressed && offset > 0) {
                    offsets[num] = offset
                    gens[num] = gen
                }
            }
        }
    }

    private fun streamSection(offset: Int): Map<String, Any?> {
        val stream = lexer(offset).indirect().second as? PdfStream ?: throw PdfSyntaxException("xref stream expected")
        val data = PdfFilters.decode(stream) ?: throw PdfSyntaxException("xref stream filter")
        val widths = (stream.dict["W"] as? List<*>)?.map { (it as? Number)?.toInt() ?: 0 } ?: throw PdfSyntaxException("no W")
        if (widths.size < 3) throw PdfSyntaxException("bad W")
        val size = (stream.dict["Size"] as? Number)?.toInt() ?: 0
        val index = (stream.dict["Index"] as? List<*>)?.map { (it as? Number)?.toInt() ?: 0 } ?: listOf(0, size)
        val entry = widths.sum()
        var at = 0
        fun field(width: Int): Long {
            var v = 0L
            repeat(width) { v = v shl 8 or (data[at++].toLong() and 0xff) }
            return v
        }
        for (pair in index.chunked(2)) {
            if (pair.size < 2) break
            for (i in 0 until pair[1]) {
                if (at + entry > data.size) return stream.dict
                val kind = if (widths[0] == 0) 1L else field(widths[0])
                val a = field(widths[1])
                val b = field(widths[2])
                val num = pair[0] + i
                if (num in offsets || num in compressed) continue
                when (kind) {
                    1L -> if (a > 0) {
                        offsets[num] = a.toInt()
                        gens[num] = b.toInt()
                    }
                    2L -> compressed[num] = a.toInt() to b.toInt()
                }
            }
        }
        return stream.dict
    }

    private fun readObjectStream(num: Int): Map<Int, Any?> {
        val stream = obj(num) as? PdfStream ?: return emptyMap()
        val data = PdfFilters.decode(stream) ?: return emptyMap()
        val count = (stream.dict["N"] as? Number)?.toInt() ?: return emptyMap()
        val first = (stream.dict["First"] as? Number)?.toInt() ?: return emptyMap()
        val header = PdfLexer(data, 0)
        val entries = (0 until count).map { header.int() to header.int() }
        return entries.associate { (objNum, offset) ->
            objNum to runCatching { PdfLexer(data, first + offset) { resolve(it) }.value() }.getOrNull()
        }
    }

    /** Every `n g obj` in the file, the later of two winning, and the trailer from the last `trailer` or xref stream. */
    @Suppress("UNCHECKED_CAST")
    private fun rebuild(): Map<String, Any?> {
        val text = String(bytes, Charsets.ISO_8859_1)
        val header = Regex("""(?<![0-9])(\d+)[ \t\r\n\f]+(\d+)[ \t\r\n\f]+obj\b""")
        for (match in header.findAll(text)) {
            val num = match.groupValues[1].toIntOrNull() ?: continue
            offsets[num] = match.range.first
            gens[num] = match.groupValues[2].toIntOrNull() ?: 0
        }
        var trailer: Map<String, Any?>? = null
        val word = lastIndexOf(bytes, "trailer".toByteArray(Charsets.ISO_8859_1))
        if (word >= 0) trailer = runCatching { PdfLexer(bytes, word + 7).value() as? Map<String, Any?> }.getOrNull()
        for (num in offsets.keys.sorted()) {
            val stream = obj(num) as? PdfStream ?: continue
            when ((stream.dict["Type"] as? PdfName)?.name) {
                "XRef" -> if (trailer?.get("Root") == null) trailer = stream.dict
                "ObjStm" -> {
                    val members = runCatching { readObjectStream(num) }.getOrDefault(emptyMap())
                    for ((index, member) in members.keys.withIndex()) if (member !in offsets) compressed.putIfAbsent(member, num to index)
                }
            }
        }
        cache.clear()
        if (trailer?.get("Root") == null) {
            val catalog = offsets.keys.sorted().lastOrNull { (dict(PdfRef(it))?.get("Type") as? PdfName)?.name == "Catalog" }
                ?: throw PdfSyntaxException("no catalog")
            trailer = linkedMapOf("Root" to PdfRef(catalog, gen(catalog)))
        }
        return trailer
    }

    private fun readPages(): List<PdfPage> {
        val root = dict(trailer["Root"]) ?: return emptyList()
        val out = ArrayList<PdfPage>()
        val seen = HashSet<Int>()
        fun walk(ref: Any?, inherited: Map<String, Any?>) {
            if (ref !is PdfRef || !seen.add(ref.num)) return
            val node = dict(ref) ?: return
            val kids = resolve(node["Kids"]) as? List<*>
            val type = (node["Type"] as? PdfName)?.name
            if (type == "Page" || (type != "Pages" && kids == null)) {
                out.add(PdfPage(ref, node, inherited))
                return
            }
            val passed = HashMap(inherited)
            for (key in inheritable) node[key]?.let { passed[key] = it }
            kids?.forEach { walk(it, passed) }
        }
        walk(root["Pages"], emptyMap())
        return out
    }

    fun mediaBox(page: PdfPage): PdfBox = PdfBox.of(resolveArray(page.attribute("MediaBox"))) ?: PdfBox(0.0, 0.0, 612.0, 792.0)

    /** The crop box clipped to the media box, as viewers show it. */
    fun cropBox(page: PdfPage): PdfBox {
        val media = mediaBox(page)
        return PdfBox.of(resolveArray(page.attribute("CropBox")))?.intersect(media) ?: media
    }

    fun rotation(page: PdfPage): Int = PdfPagesEngine.normaliseRotation((resolve(page.attribute("Rotate")) as? Number)?.toInt() ?: 0)

    private fun resolveArray(value: Any?): List<Any?>? = (resolve(value) as? List<*>)?.map { resolve(it) }

    /**
     * The original bytes with [changes] appended as an incremental update: the changed objects, then an xref section
     * of the file's own kind pointing back at the previous one. A rebuilt file gets a section listing every object.
     */
    fun update(changes: Map<PdfRef, Any?>): ByteArray {
        val out = ByteArrayOutputStream(bytes.size + 4096)
        out.write(bytes)
        if (bytes.isNotEmpty() && bytes.last() != '\n'.code.toByte()) out.ascii("\n")
        val written = sortedMapOf<Int, Pair<Long, Int>>()
        for ((ref, value) in changes) {
            written[ref.num] = out.size().toLong() to ref.gen
            out.ascii("${ref.num} ${ref.gen} obj\n")
            PdfSerialiser.write(value, out)
            out.ascii("\nendobj\n")
        }
        val keep = LinkedHashMap<String, Any?>()
        for (key in listOf("Root", "Info", "ID")) trailer[key]?.let { keep[key] = it }
        val xref = out.size().toLong()
        if (usesXrefStream) {
            val self = maxOf(size, (written.keys.maxOrNull() ?: 0) + 1)
            written[self] = xref to 0
            val entries = sortedMapOf<Int, Triple<Int, Long, Int>>()
            if (rebuilt) {
                for ((num, offset) in offsets) entries[num] = Triple(1, offset.toLong(), gen(num))
                for ((num, place) in compressed) entries[num] = Triple(2, place.first.toLong(), place.second)
            }
            for ((num, at) in written) entries[num] = Triple(1, at.first, at.second)
            val data = ByteArrayOutputStream()
            for ((_, e) in entries) {
                data.write(e.first)
                for (shift in 24 downTo 0 step 8) data.write((e.second shr shift).toInt() and 0xff)
                data.write(e.third shr 8 and 0xff)
                data.write(e.third and 0xff)
            }
            val dict: PdfDict = linkedMapOf("Type" to PdfName("XRef"), "Size" to self + 1, "W" to listOf(1, 4, 2))
            dict["Index"] = runs(entries.keys.toList()).flatMap { listOf(it.first, it.second) }
            dict.putAll(keep)
            if (!rebuilt) dict["Prev"] = startXref
            out.ascii("$self 0 obj\n")
            PdfSerialiser.write(PdfStream(dict, data.toByteArray()), out)
            out.ascii("\nendobj\n")
        } else {
            val entries = sortedMapOf<Int, Pair<Long, Int>>()
            if (rebuilt) for ((num, offset) in offsets) entries[num] = offset.toLong() to gen(num)
            entries.putAll(written)
            out.ascii("xref\n")
            if (rebuilt) entries[0] = -1L to 65535
            for ((first, count) in runs(entries.keys.toList())) {
                out.ascii("$first $count\n")
                for (num in first until first + count) {
                    val (offset, gen) = entries.getValue(num)
                    out.ascii(
                        if (offset < 0) "0000000000 65535 f\r\n"
                        else String.format(Locale.ROOT, "%010d %05d n\r\n", offset, gen),
                    )
                }
            }
            val dict: PdfDict = linkedMapOf("Size" to maxOf(size, (entries.keys.maxOrNull() ?: 0) + 1))
            dict.putAll(keep)
            if (!rebuilt) dict["Prev"] = startXref
            out.ascii("trailer\n")
            PdfSerialiser.write(dict, out)
        }
        out.ascii("\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }

    private fun runs(sorted: List<Int>): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (num in sorted) {
            val last = out.lastOrNull()
            if (last != null && last.first + last.second == num) out[out.size - 1] = last.first to last.second + 1 else out.add(num to 1)
        }
        return out
    }
}
