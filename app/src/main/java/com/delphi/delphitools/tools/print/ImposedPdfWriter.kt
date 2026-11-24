package com.delphi.delphitools.tools.print

import com.delphi.delphitools.tools.pdf.PdfCropEngine
import com.delphi.delphitools.tools.pdf.PdfDict
import com.delphi.delphitools.tools.pdf.PdfFile
import com.delphi.delphitools.tools.pdf.PdfFilters
import com.delphi.delphitools.tools.pdf.PdfName
import com.delphi.delphitools.tools.pdf.PdfPagesEngine
import com.delphi.delphitools.tools.pdf.PdfRect
import com.delphi.delphitools.tools.pdf.PdfRef
import com.delphi.delphitools.tools.pdf.PdfSerialiser
import com.delphi.delphitools.tools.pdf.PdfStampEngine
import com.delphi.delphitools.tools.pdf.PdfStream
import com.delphi.delphitools.tools.pdf.PdfWriter
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.cos
import kotlin.math.sin

/**
 * Copies objects of one [file] into [writer], each once. Links to pages, page trees and the catalog become null,
 * since only content travels.
 */
class PdfObjectCopier(private val file: PdfFile, private val writer: PdfWriter) {
    private val copied = HashMap<Int, Int>()
    private val queue = ArrayDeque<Pair<Int, Int>>()

    fun copy(value: Any?): Any? = when (value) {
        is PdfRef -> {
            val target = file.obj(value.num)
            val type = ((if (target is PdfStream) target.dict else target as? Map<*, *>)?.get("Type") as? PdfName)?.name
            if (target == null || file.isPage(value.num) || type == "Pages" || type == "Catalog") {
                null
            } else {
                PdfRef(copied.getOrPut(value.num) { writer.reserve().also { queue.add(value.num to it) } })
            }
        }
        is List<*> -> value.map(::copy)
        is Map<*, *> -> LinkedHashMap<String, Any?>().apply { for ((k, v) in value) put(k as String, copy(v)) }
        is PdfStream -> {
            @Suppress("UNCHECKED_CAST")
            PdfStream(copy(value.dict.filterKeys { it != "Length" }) as PdfDict, value.data)
        }
        else -> value
    }

    /** Writes everything [copy] has referenced so far. */
    fun drain() {
        while (queue.isNotEmpty()) {
            val (old, new) = queue.removeFirst()
            writer.write(new, copy(file.obj(old)))
        }
    }
}

/**
 * dt-ios ImposedPDFWriter, with each source page placed as a Form XObject so text and vectors stay as they are.
 * Pages are placed as viewed: their crop box, turned by their /Rotate.
 */
object ImposedPdfWriter {
    private fun r(value: Double) = PdfSerialiser.real(value)

    private fun inverse(m: List<Double>): List<Double> {
        val (a, b, c, d, e) = m
        val f = m[5]
        val det = a * d - b * c
        return listOf(d / det, -b / det, -c / det, a / det, (c * f - d * e) / det, (b * e - a * f) / det)
    }

    /** The page's content as one or more form XObjects drawn in display space, sized as viewed. */
    private class PageForm(val refs: List<PdfRef>, val width: Double, val height: Double)

    private fun form(file: PdfFile, index: Int, writer: PdfWriter, copier: PdfObjectCopier): PageForm {
        val page = file.pages[index]
        val box = PdfRect.of(file.cropBox(page))
        val rotation = file.rotation(page)
        val size = PdfCropEngine.displaySize(box, rotation)
        val parts = when (val contents = file.resolve(page.dict["Contents"])) {
            is List<*> -> contents.mapNotNull { file.resolve(it) as? PdfStream }
            is PdfStream -> listOf(contents)
            else -> emptyList()
        }
        val decoded = parts.map { PdfFilters.decode(it) }
        val streams: List<Pair<ByteArray, PdfDict>> = if (decoded.all { it != null }) {
            val joined = ByteArrayOutputStream()
            for (part in decoded) {
                joined.write(part!!)
                joined.write('\n'.code)
            }
            listOf(PdfFilters.deflate(joined.toByteArray()) to linkedMapOf("Filter" to PdfName("FlateDecode")))
        } else {
            // ponytail: a stream in another filter is kept as stored, one form per stream; an operator split across streams breaks
            parts.map { part -> part.data to LinkedHashMap<String, Any?>().apply { for (key in listOf("Filter", "DecodeParms")) part.dict[key]?.let { put(key, copier.copy(it)) } } }
        }
        val resources = copier.copy(file.resolve(page.attribute("Resources")) ?: emptyMap<String, Any?>())
        val matrix = inverse(PdfStampEngine.displayMatrix(box, rotation))
        val refs = streams.map { (data, filter) ->
            val dict: PdfDict = linkedMapOf(
                "Type" to PdfName("XObject"), "Subtype" to PdfName("Form"),
                "BBox" to listOf(box.x, box.y, box.maxX, box.maxY), "Matrix" to matrix, "Resources" to resources,
            )
            dict.putAll(filter)
            writer.add(PdfStream(dict, data))
        }
        copier.drain()
        return PageForm(refs, size.width, size.height)
    }

    /** Writes front then back of every sheet; [progress] after each sheet, from any thread. */
    fun write(file: PdfFile, result: ImpositionResult, config: ImpositionConfig, out: OutputStream, progress: (Double) -> Unit = {}) {
        val k = PdfPagesEngine.MM_TO_POINTS
        val sheetW = config.sheetW * k
        val sheetH = config.sheetH * k
        val writer = PdfWriter(out)
        val tree = writer.reserve()
        val copier = PdfObjectCopier(file, writer)
        val forms = HashMap<Int, PageForm>()
        val fold = writer.add(linkedMapOf("Type" to PdfName("ExtGState"), "CA" to 0.4))
        val kids = ArrayList<PdfRef>()

        fun side(placements: List<ImpositionPlacement>) {
            val names = LinkedHashMap<String, Any?>()
            val ops = StringBuilder()
            for (p in placements) {
                if (p.pageNumber <= 0 || p.pageNumber > file.pages.size) continue
                val index = p.pageNumber - 1
                val form = forms.getOrPut(index) { form(file, index, writer, copier) }
                val (px, py, pw, ph) = listOf(p.x * k, p.y * k, p.width * k, p.height * k)
                // pdf origin bottom-left
                val bottom = sheetH - py - ph
                val fitX = pw / form.width
                val fitY = ph / form.height
                val scale = when (config.scaling) {
                    ImpositionScaling.Fit -> minOf(fitX, fitY)
                    ImpositionScaling.Fill -> maxOf(fitX, fitY)
                    ImpositionScaling.Actual -> 1.0
                }
                val angle = -Math.toRadians(p.rotation.toDouble())
                ops.append("q ${r(px)} ${r(bottom)} ${r(pw)} ${r(ph)} re W n\n")
                ops.append("1 0 0 1 ${r(px + pw / 2)} ${r(bottom + ph / 2)} cm\n")
                if (p.rotation != 0) ops.append("${r(cos(angle))} ${r(sin(angle))} ${r(-sin(angle))} ${r(cos(angle))} 0 0 cm\n")
                ops.append("${r(scale)} 0 0 ${r(scale)} ${r(-form.width * scale / 2)} ${r(-form.height * scale / 2)} cm\n")
                form.refs.forEachIndexed { part, ref ->
                    val name = "P${index}_$part"
                    names[name] = ref
                    ops.append("/$name Do\n")
                }
                ops.append("Q\n")
            }
            if (config.cropMarks && placements.isNotEmpty()) {
                ops.append("q 0.333 G 0.5 w\n")
                for (line in ImpositionEngine.marks(placements)) {
                    ops.append("${r(line.x1 * k)} ${r(sheetH - line.y1 * k)} m ${r(line.x2 * k)} ${r(sheetH - line.y2 * k)} l\n")
                }
                ops.append("S Q\n")
            }
            val creases = ImpositionEngine.creases(config.layoutId, config.sheetW, config.sheetH)
            if (creases.isNotEmpty()) {
                ops.append("q /Fold gs 0 0.478 1 RG 0.5 w [4 3] 0 d\n")
                for (line in creases) ops.append("${r(line.x1 * k)} ${r(sheetH - line.y1 * k)} m ${r(line.x2 * k)} ${r(sheetH - line.y2 * k)} l S\n")
                ops.append("Q\n")
            }
            val content = writer.add(PdfStream(linkedMapOf("Filter" to PdfName("FlateDecode")), PdfFilters.deflate(ops.toString().toByteArray(Charsets.ISO_8859_1))))
            kids.add(
                writer.add(
                    linkedMapOf(
                        "Type" to PdfName("Page"), "Parent" to PdfRef(tree), "MediaBox" to listOf(0, 0, sheetW, sheetH),
                        "Resources" to linkedMapOf("XObject" to names, "ExtGState" to linkedMapOf("Fold" to fold)),
                        "Contents" to content,
                    ),
                ),
            )
        }

        result.sheets.forEachIndexed { index, sheet ->
            side(sheet.front)
            side(sheet.back)
            progress((index + 1).toDouble() / maxOf(1, result.sheets.size))
        }
        writer.write(tree, linkedMapOf("Type" to PdfName("Pages"), "Kids" to kids, "Count" to kids.size))
        writer.finish(writer.add(linkedMapOf("Type" to PdfName("Catalog"), "Pages" to PdfRef(tree))))
    }
}
