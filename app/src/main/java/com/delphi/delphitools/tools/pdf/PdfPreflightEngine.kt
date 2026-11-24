package com.delphi.delphitools.tools.pdf

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * TaxiwayCore's PDFDocumentParser, PreflightEngine and the checks its built-in profiles use, read from [PdfFile].
 * Like the parser, only page resources and page content streams are read; form XObjects are not entered.
 */
object PdfPreflightEngine {
    enum class Category { File, Pdf, Pages, Marks, Colour, Fonts, Images, Lines }

    /** Ordered as TaxiwayCore sorts them: error first. */
    enum class Severity { Error, Warning, Info }

    enum class Status { Pass, Fail }

    /** [pages] null: the whole document. */
    data class Result(
        val typeId: String,
        val status: Status,
        val severity: Severity,
        val message: String,
        val detail: String? = null,
        val pages: Set<Int>? = null,
    ) {
        val category: Category
            get() = when (typeId.substringBefore('.')) {
                "pdf" -> Category.Pdf
                "pages" -> Category.Pages
                "marks" -> Category.Marks
                "colour" -> Category.Colour
                "fonts" -> Category.Fonts
                "images" -> Category.Images
                "lines" -> Category.Lines
                else -> Category.File
            }
        val isIssue get() = status == Status.Fail
        val isError get() = status == Status.Fail && severity == Severity.Error
    }

    class Report(val profile: Profile, val results: List<Result>) {
        val passed get() = results.none { it.isError }
    }

    // ---- document facts ----

    data class Page(val index: Int, val mediaBox: PdfBox, val trimBox: PdfBox?, val bleedBox: PdfBox?, val rotation: Int) {
        val effectiveTrimBox get() = trimBox ?: mediaBox

        /** left, right, top, bottom */
        val bleedMargins: List<Double>
            get() {
                val bleed = bleedBox ?: return listOf(0.0, 0.0, 0.0, 0.0)
                val trim = effectiveTrimBox
                return listOf(trim.x0 - bleed.x0, bleed.x1 - trim.x1, bleed.y1 - trim.y1, trim.y0 - bleed.y0)
            }
    }

    data class Font(val name: String, val type: String, val embedded: Boolean, val subset: Boolean, val pages: List<Int>)

    data class Image(val id: String, val page: Int, val width: Int, val height: Int, val widthPoints: Double, val heightPoints: Double) {
        val ppiX get() = if (widthPoints > 0) width / (widthPoints / 72.0) else 0.0
        val ppiY get() = if (heightPoints > 0) height / (heightPoints / 72.0) else 0.0
    }

    enum class Mode { Grey, Rgb, Cmyk }

    data class ColourUsage(val name: String, val mode: Mode, val components: List<Double>, val inkSum: Double?)

    data class Overprint(val page: Int, val context: String, val white: Boolean)

    data class Stroke(val page: Int, val width: Double)

    data class Document(
        val fileSize: Long,
        val pdfVersion: String,
        val producer: String?,
        val creator: String?,
        val linearised: Boolean,
        val tagged: Boolean,
        val layers: Boolean,
        val transparency: Boolean,
        val embeddedFiles: Boolean,
        val javaScript: Boolean,
        val outputIntent: String?,
        val outputIntentTypes: List<String>,
        val c2pa: Boolean,
        val genAI: Boolean,
        val pages: List<Page>,
        val fonts: List<Font>,
        val images: List<Image>,
        val colourSpaces: Map<String, List<Int>>,
        val spotColours: List<String>,
        val colourUsages: List<ColourUsage>,
        val overprints: List<Overprint>,
        val strokes: List<Stroke>,
        val annotations: List<Pair<String, Int>>,
    ) {
        val fileSizeMb get() = fileSize / 1_048_576.0
    }

    // ---- profiles ----

    /** One configured check: its id, what it is given, and the severity the profile sets. */
    data class Entry(val typeId: String, val severity: Severity, val number: Double = 0.0, val text: String = "")

    data class Profile(val name: String, val checks: List<Entry>)

    private val e = Severity.Error
    private val w = Severity.Warning

    val profiles = listOf(
        Profile(
            "PDF/X-1a",
            listOf(
                Entry("fonts.not_embedded", e), Entry("colour.space_used", e, text = "DeviceRGB"), Entry("marks.trim_box_set", e),
                Entry("marks.bleed_zero", e), Entry("file.encryption", e), Entry("pages.size", w),
                Entry("images.resolution_below", w, 200.0),
            ),
        ),
        Profile(
            "PDF/X-4",
            listOf(
                Entry("fonts.not_embedded", e), Entry("marks.trim_box_set", e), Entry("marks.bleed_zero", e),
                Entry("file.encryption", e), Entry("images.resolution_below", w, 150.0),
            ),
        ),
        Profile(
            "PDF/X-3",
            listOf(
                Entry("pdf.conformance", e, text = "X-3"), Entry("fonts.not_embedded", e), Entry("marks.trim_box_set", e),
                Entry("marks.bleed_zero", e), Entry("file.encryption", e), Entry("pdf.output_intent", e),
                Entry("images.resolution_below", w, 150.0),
            ),
        ),
        Profile(
            "PDF/A-2b",
            listOf(
                Entry("pdf.conformance", e, text = "A-2b"), Entry("fonts.not_embedded", e), Entry("file.encryption", e),
                Entry("file.javascript", e), Entry("file.embedded_files", e), Entry("pdf.tagged", e), Entry("pdf.transparency", w),
            ),
        ),
        Profile(
            "Screen / Digital",
            listOf(
                Entry("file.size.max", w, 100.0), Entry("file.encryption", e), Entry("images.resolution_below", w, 72.0),
                Entry("fonts.not_embedded", w),
            ),
        ),
        Profile(
            "Digital Print",
            listOf(
                Entry("fonts.not_embedded", e), Entry("marks.trim_box_set", e), Entry("marks.bleed_less_than", w, 3.0),
                Entry("colour.overprint", e, text = "white"), Entry("colour.overprint", w, text = "fill"), Entry("colour.rich_black", w),
                Entry("images.resolution_below", w, 150.0), Entry("file.encryption", e), Entry("pdf.transparency", w),
            ),
        ),
        Profile(
            "Newspaper",
            listOf(
                Entry("fonts.not_embedded", e), Entry("colour.space_used", e, text = "DeviceRGB"), Entry("colour.ink_coverage", e, 240.0),
                Entry("colour.spot_used", e), Entry("pdf.transparency", e), Entry("images.resolution_below", w, 150.0),
                Entry("images.resolution_above", w, 400.0), Entry("lines.stroke_below", w, 0.25),
            ),
        ),
        Profile(
            "Large Format",
            listOf(
                Entry("fonts.not_embedded", e), Entry("colour.space_used", w, text = "DeviceRGB"), Entry("images.resolution_below", w, 100.0),
                Entry("file.encryption", e), Entry("lines.stroke_below", w, 0.5), Entry("marks.trim_box_set", w), Entry("pdf.transparency", w),
            ),
        ),
        Profile(
            "Loose",
            listOf(Entry("file.size.max", w, 500.0), Entry("file.encryption", w), Entry("pages.count", e)),
        ),
        Profile(
            "AI Content Audit",
            listOf(
                Entry("images.genai", e), Entry("images.c2pa", w), Entry("file.encryption", e), Entry("file.javascript", e),
                Entry("pdf.annotations", w), Entry("images.resolution_below", w, 72.0),
            ),
        ),
    )

    fun run(profile: Profile, document: Document): Report = Report(profile, profile.checks.map { check(it, document) })

    private fun f(format: String, vararg args: Any) = String.format(Locale.ROOT, format, *args)

    private fun points(value: Double) = if (value == Math.rint(value)) f("%.0f", value) else f("%.3f", value)

    private fun check(entry: Entry, d: Document): Result {
        val id = entry.typeId
        fun pass(message: String) = Result(id, Status.Pass, entry.severity, message)
        fun fail(message: String, detail: String? = null, pages: Set<Int>? = null) = Result(id, Status.Fail, entry.severity, message, detail, pages)
        fun pagesWhere(test: (Page) -> Boolean) = d.pages.filter(test).map { it.index }.toSet()
        return when (id) {
            "fonts.not_embedded" -> {
                val missing = d.fonts.filter { !it.embedded }
                if (missing.isEmpty()) {
                    pass("All fonts are embedded")
                } else {
                    fail(
                        "${missing.size} font(s) not embedded",
                        "Unembedded fonts: ${missing.joinToString(", ") { it.name }}",
                        missing.flatMap { it.pages }.toSet(),
                    )
                }
            }
            "colour.space_used" -> {
                val pages = d.colourSpaces[entry.text]
                if (pages == null) {
                    pass("${entry.text} colour space not found")
                } else {
                    fail(
                        "${entry.text} colour space detected",
                        "Found on page(s): ${pages.sorted().joinToString(", ") { (it + 1).toString() }}",
                        pages.toSet(),
                    )
                }
            }
            "marks.trim_box_set" -> {
                if (d.pages.isEmpty()) return pass("No pages to check")
                val missing = pagesWhere { it.trimBox == null }
                if (missing.isEmpty()) pass("All pages have a trim box defined") else fail("${missing.size} page(s) missing trim box", pages = missing)
            }
            "marks.bleed_zero" -> {
                if (d.pages.isEmpty()) return pass("No pages to check")
                val zero = pagesWhere { page -> page.bleedMargins.all { it == 0.0 } }
                if (zero.isEmpty()) pass("All pages have bleed") else fail("${zero.size} page(s) have zero bleed", pages = zero)
            }
            "marks.bleed_less_than" -> {
                if (d.pages.isEmpty()) return pass("No pages to check")
                val threshold = entry.number * PdfPagesEngine.MM_TO_POINTS
                val short = pagesWhere { page -> page.bleedMargins.any { it > 0 && it < threshold } }
                val mm = f("%.1f", entry.number)
                if (short.isEmpty()) {
                    pass("All bleed margins meet minimum of $mm mm")
                } else {
                    fail("${short.size} page(s) have bleed less than $mm mm", pages = short)
                }
            }
            // encrypted files do not open, so what reaches a check is never encrypted
            "file.encryption" -> pass("File is not encrypted")
            "pages.size" -> {
                if (d.pages.isEmpty()) return pass("No pages to check")
                val (tw, th, tol) = Triple(595.276, 841.89, 1.0)
                val off = pagesWhere { page ->
                    val trim = page.effectiveTrimBox
                    val normal = abs(trim.width - tw) <= tol && abs(trim.height - th) <= tol
                    val rotated = abs(trim.width - th) <= tol && abs(trim.height - tw) <= tol
                    !normal && !rotated
                }
                val size = "(${f("%.1f", tw)} x ${f("%.1f", th)} pt)"
                if (off.isEmpty()) pass("All pages match target size $size") else fail("${off.size} page(s) do not match target size $size", pages = off)
            }
            "images.resolution_below", "images.resolution_above" -> {
                if (d.images.isEmpty()) return pass("No images in document")
                val below = id.endsWith("below")
                val ppi = { image: Image -> if (below) minOf(image.ppiX, image.ppiY) else maxOf(image.ppiX, image.ppiY) }
                val affected = d.images.filter { if (below) ppi(it) < entry.number else ppi(it) > entry.number }
                val threshold = f("%.0f", entry.number)
                when {
                    affected.isEmpty() && below -> pass("All images meet minimum resolution of $threshold PPI")
                    affected.isEmpty() -> pass("All images within maximum resolution of $threshold PPI")
                    else -> fail(
                        "${affected.size} image(s) ${if (below) "below" else "exceed"} $threshold PPI",
                        affected.joinToString(", ") { "${it.id}: ${f("%.0f", ppi(it))} PPI" },
                        affected.map { it.page }.toSet(),
                    )
                }
            }
            "file.size.max" -> {
                if (d.fileSizeMb > entry.number) {
                    fail("File exceeds ${f("%.1f", entry.number)} MB", f("File is %.1f MB", d.fileSizeMb))
                } else {
                    pass(f("File size OK (%.1f MB)", d.fileSizeMb))
                }
            }
            "pages.count" -> if (d.pages.isNotEmpty()) pass("Page count is ${d.pages.size}") else fail("Page count ${d.pages.size} is not more than 0")
            "pdf.conformance" -> {
                val name = "PDF/${entry.text}"
                val wanted = if (entry.text.startsWith("X")) "GTS_PDFX" else "GTS_PDFA1"
                when {
                    wanted in d.outputIntentTypes -> pass("Document conforms to $name")
                    d.outputIntentTypes.isEmpty() -> fail("No output intents found for $name", "Document has no output intents")
                    else -> fail(
                        "Document does not conform to $name",
                        "Expected output intent subtype \"$wanted\", found: ${d.outputIntentTypes.joinToString(", ")}",
                    )
                }
            }
            "pdf.output_intent" -> d.outputIntent?.let { pass("Output intent present: $it") }
                ?: fail("No output intent set", "Expected an output intent to be present")
            "pdf.tagged" -> if (d.tagged) pass("Document is tagged") else fail("Document is not tagged", "Expected document to be tagged")
            "pdf.transparency" -> if (d.transparency) fail("Transparency detected in document") else pass("No transparency detected")
            "pdf.annotations" -> {
                if (d.annotations.isEmpty()) return pass("No annotations found")
                val summary = d.annotations.groupBy { it.first }.map { "${it.value.size} ${it.key}" }.sorted().joinToString(", ")
                fail("Document contains ${d.annotations.size} annotation(s)", summary, d.annotations.map { it.second }.toSet())
            }
            "file.javascript" -> if (d.javaScript) fail("Document contains JavaScript", "JavaScript actions were found in the PDF") else pass("No JavaScript found")
            "file.embedded_files" -> if (d.embeddedFiles) {
                fail("Document contains embedded files", "Embedded file attachments are present in the PDF")
            } else {
                pass("No embedded files found")
            }
            "images.genai" -> if (d.genAI) fail("Generative AI metadata detected") else pass("No generative AI metadata found")
            "images.c2pa" -> if (d.c2pa) fail("C2PA content credentials detected") else pass("No C2PA content credentials found")
            "colour.spot_used" -> if (d.spotColours.isEmpty()) {
                pass("No spot colours found")
            } else {
                fail("${d.spotColours.size} spot colour(s) detected", "Spot colours: ${d.spotColours.joinToString(", ")}")
            }
            "colour.rich_black" -> {
                val rich = d.colourUsages.filter { u ->
                    u.mode == Mode.Cmyk && u.components.size == 4 && u.components[3] >= 0.99 && u.components.take(3).any { it > 0.001 }
                }
                if (rich.isEmpty()) {
                    pass("No rich black colours detected")
                } else {
                    fail("${rich.size} rich black colour(s) detected", "Colours: ${rich.joinToString(", ") { it.name }}")
                }
            }
            "colour.ink_coverage" -> {
                val over = d.colourUsages.filter { (it.inkSum ?: return@filter false) > entry.number }
                if (over.isEmpty()) {
                    pass("All ink coverage values within threshold (${entry.number.toInt()}%)")
                } else {
                    fail("${over.size} colour(s) exceed ink coverage threshold", over.joinToString(", ") { "${it.name} (${f("%.0f", it.inkSum ?: 0.0)}%)" })
                }
            }
            "colour.overprint" -> {
                val white = entry.text == "white"
                val matching = d.overprints.filter { if (white) it.white else it.context == entry.text }
                val label = "${entry.text} overprint"
                if (matching.isEmpty()) return pass("No $label detected")
                val pages = matching.map { it.page }.toSortedSet()
                fail(
                    "${label.replaceFirstChar { it.uppercase() }} detected on ${matching.size} usage(s)",
                    "Found on: ${pages.joinToString(", ") { "page ${it + 1}" }}",
                    pages,
                )
            }
            "lines.stroke_below" -> {
                val thin = d.strokes.filter { it.width > 0 && it.width < entry.number }
                if (thin.isEmpty()) return pass("No strokes below ${points(entry.number)} pt")
                val pages = thin.map { it.page }.toSortedSet()
                fail(
                    "${thin.size} stroke(s) below ${points(entry.number)} pt",
                    "Thinnest: ${points(thin.minOf { it.width })} pt on ${pages.joinToString(", ") { "page ${it + 1}" }}",
                    pages,
                )
            }
            else -> error("unknown check $id")
        }
    }

    // ---- parsing ----

    /** A PDF text string: UTF-16BE or UTF-8 with a byte order mark, else PDFDocEncoding read as Latin-1. */
    fun text(value: Any?): String? {
        val bytes = (value as? PdfString)?.bytes ?: return null
        return when {
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            else -> String(bytes, Charsets.ISO_8859_1)
        }
    }

    private fun name(value: Any?) = (value as? PdfName)?.name

    private fun differs(a: PdfBox, b: PdfBox) =
        abs(a.x0 - b.x0) >= 0.01 || abs(a.y0 - b.y0) >= 0.01 || abs(a.width - b.width) >= 0.01 || abs(a.height - b.height) >= 0.01

    /** The page's content streams decoded and joined; a stream in a filter other than Flate is skipped. */
    fun content(file: PdfFile, page: PdfPage): ByteArray {
        val parts = when (val c = file.resolve(page.dict["Contents"])) {
            is List<*> -> c.map { file.resolve(it) }
            else -> listOf(c)
        }
        val out = java.io.ByteArrayOutputStream()
        for (part in parts) {
            val stream = part as? PdfStream ?: continue
            out.write(PdfFilters.decode(stream) ?: continue)
            out.write('\n'.code)
        }
        return out.toByteArray()
    }

    /** Operands then operator, for each operator in [data]; inline image data is skipped. */
    fun scan(data: ByteArray, onOperator: (String, List<Any?>) -> Unit) {
        val lexer = PdfLexer(data, 0)
        val operands = ArrayList<Any?>()
        while (true) {
            lexer.skipWhite()
            if (lexer.pos >= data.size) return
            val b = data[lexer.pos].toInt() and 0xff
            val start = lexer.pos
            val operand = b == '/'.code || b == '('.code || b == '<'.code || b == '['.code || b.toChar() in "+-.0123456789"
            if (operand) {
                val value = runCatching { lexer.value() }
                if (value.isSuccess) operands.add(value.getOrNull()) else lexer.pos = start + 1
                continue
            }
            val word = lexer.token()
            if (word.isEmpty()) {
                lexer.pos = start + 1
                operands.clear()
                continue
            }
            when (word) {
                "true" -> operands.add(true)
                "false" -> operands.add(false)
                "null" -> operands.add(null)
                "BI" -> {
                    val marker = indexOf(data, "ID".toByteArray(), lexer.pos)
                    if (marker < 0) return
                    var at = marker + 3
                    while (true) {
                        at = indexOf(data, "EI".toByteArray(), at)
                        if (at < 0) return
                        val before = data[at - 1].toInt()
                        val after = if (at + 2 < data.size) data[at + 2].toInt() else 32
                        if ((before == 32 || before == 10 || before == 13) && (after == 32 || after == 10 || after == 13)) break
                        at++
                    }
                    lexer.pos = at + 2
                    operands.clear()
                }
                else -> {
                    onOperator(word, operands.toList())
                    operands.clear()
                }
            }
        }
    }

    private class Colour(val mode: Mode, val components: List<Double>, val spot: String? = null)

    private class State(
        val ctm: DoubleArray = doubleArrayOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0),
        var lineWidth: Double = 1.0,
        var fill: Colour = Colour(Mode.Grey, listOf(0.0)),
        var stroke: Colour = Colour(Mode.Grey, listOf(0.0)),
        var overprintFill: Boolean = false,
        var overprintStroke: Boolean = false,
    ) {
        fun copy() = State(ctm.copyOf(), lineWidth, fill, stroke, overprintFill, overprintStroke)
    }

    private val spaceFamilies = setOf(
        "DeviceGray", "DeviceRGB", "DeviceCMYK", "ICCBased", "CalGray", "CalRGB", "Lab", "Indexed", "Separation", "DeviceN", "Pattern",
    )
    private val annotationTypes = setOf(
        "Link", "Widget", "Text", "FreeText", "Highlight", "Underline", "StrikeOut", "Stamp", "Ink", "Popup", "FileAttachment",
    )

    private val processNames = setOf("Cyan", "Magenta", "Yellow", "Black", "Red", "Green", "Blue", "None", "All")

    private class Usage(val colour: Colour, val context: String, val page: Int, val overprint: Boolean)

    private class PageScan(val placements: Map<String, Pair<Double, Double>>, val usages: List<Usage>, val strokes: List<Double>)

    private fun Map<String, Any?>.dict(file: PdfFile, key: String) = file.dict(this[key])

    private fun scanPage(file: PdfFile, page: PdfPage, index: Int, resources: Map<String, Any?>?): PageScan {
        val spaces = HashMap<String, Colour>()
        resources?.dict(file, "ColorSpace")?.forEach { (key, value) ->
            spaceColour(file, file.resolve(value))?.let { spaces[key] = it }
        }
        val states = HashMap<String, Pair<Boolean?, Boolean?>>()
        resources?.dict(file, "ExtGState")?.forEach { (key, value) ->
            val gs = file.dict(value) ?: return@forEach
            val stroke = file.resolve(gs["OP"]) as? Boolean
            val fill = file.resolve(gs["op"]) as? Boolean ?: stroke
            if (stroke != null || fill != null) states[key] = fill to stroke
        }
        val stack = ArrayList<State>()
        var s = State()
        val placements = LinkedHashMap<String, Pair<Double, Double>>()
        val usages = ArrayList<Usage>()
        val strokes = ArrayList<Double>()
        fun use(fill: Boolean, context: String) {
            usages.add(Usage(if (fill) s.fill else s.stroke, context, index, if (fill) s.overprintFill else s.overprintStroke))
        }
        fun stroked() {
            strokes.add(s.lineWidth)
            use(false, "stroke")
        }
        fun numbers(operands: List<Any?>) = operands.mapNotNull { (it as? Number)?.toDouble() }
        fun space(name: String): Colour? = spaces[name] ?: when (name) {
            "DeviceGray" -> Colour(Mode.Grey, listOf(0.0))
            "DeviceRGB" -> Colour(Mode.Rgb, listOf(0.0, 0.0, 0.0))
            "DeviceCMYK" -> Colour(Mode.Cmyk, listOf(0.0, 0.0, 0.0, 0.0))
            else -> null
        }
        fun components(current: Colour, operands: List<Any?>): Colour {
            val values = numbers(operands).takeLast(current.components.size)
            return if (values.isEmpty()) current else Colour(current.mode, values, current.spot)
        }
        scan(content(file, page)) { op, operands ->
            val n = numbers(operands)
            when (op) {
                "q" -> stack.add(s.copy())
                "Q" -> if (stack.isNotEmpty()) s = stack.removeAt(stack.size - 1)
                "cm" -> if (n.size == 6) {
                    val m = s.ctm.copyOf()
                    s.ctm[0] = n[0] * m[0] + n[1] * m[2]
                    s.ctm[1] = n[0] * m[1] + n[1] * m[3]
                    s.ctm[2] = n[2] * m[0] + n[3] * m[2]
                    s.ctm[3] = n[2] * m[1] + n[3] * m[3]
                    s.ctm[4] = n[4] * m[0] + n[5] * m[2] + m[4]
                    s.ctm[5] = n[4] * m[1] + n[5] * m[3] + m[5]
                }
                "Do" -> name(operands.lastOrNull())?.let { key ->
                    val m = s.ctm
                    val xs = listOf(m[4], m[0] + m[4], m[0] + m[2] + m[4], m[2] + m[4])
                    val ys = listOf(m[5], m[1] + m[5], m[1] + m[3] + m[5], m[3] + m[5])
                    placements.putIfAbsent(key, (xs.max() - xs.min()) to (ys.max() - ys.min()))
                }
                "w" -> n.lastOrNull()?.let { s.lineWidth = it }
                "gs" -> states[name(operands.lastOrNull())]?.let { (fill, stroke) ->
                    fill?.let { s.overprintFill = it }
                    stroke?.let { s.overprintStroke = it }
                }
                "g" -> if (n.isNotEmpty()) s.fill = Colour(Mode.Grey, n.takeLast(1))
                "G" -> if (n.isNotEmpty()) s.stroke = Colour(Mode.Grey, n.takeLast(1))
                "rg" -> if (n.size >= 3) s.fill = Colour(Mode.Rgb, n.takeLast(3))
                "RG" -> if (n.size >= 3) s.stroke = Colour(Mode.Rgb, n.takeLast(3))
                "k" -> if (n.size >= 4) s.fill = Colour(Mode.Cmyk, n.takeLast(4))
                "K" -> if (n.size >= 4) s.stroke = Colour(Mode.Cmyk, n.takeLast(4))
                "cs" -> name(operands.lastOrNull())?.let(::space)?.let { s.fill = it }
                "CS" -> name(operands.lastOrNull())?.let(::space)?.let { s.stroke = it }
                "sc", "scn" -> s.fill = components(s.fill, operands)
                "SC", "SCN" -> s.stroke = components(s.stroke, operands)
                "Tj", "TJ", "'", "\"" -> use(true, "text")
                "f", "F", "f*" -> use(true, "fill")
                "S", "s" -> stroked()
                "B", "B*", "b", "b*" -> {
                    use(true, "fill")
                    stroked()
                }
            }
        }
        return PageScan(placements, usages, strokes)
    }

    private fun spaceColour(file: PdfFile, value: Any?): Colour? {
        val family = name(value) ?: name((value as? List<*>)?.firstOrNull()?.let(file::resolve))
        val list = value as? List<*>
        return when (family) {
            "DeviceGray", "CalGray" -> Colour(Mode.Grey, listOf(0.0))
            "DeviceRGB", "CalRGB" -> Colour(Mode.Rgb, listOf(0.0, 0.0, 0.0))
            "DeviceCMYK" -> Colour(Mode.Cmyk, listOf(0.0, 0.0, 0.0, 0.0))
            "ICCBased" -> {
                if (list == null) return null
                val count = (file.resolve(file.dict(list.getOrNull(1))?.get("N")) as? Number)?.toInt() ?: return null
                Colour(if (count == 1) Mode.Grey else if (count == 3) Mode.Rgb else Mode.Cmyk, List(count) { 0.0 })
            }
            "Separation" -> {
                if (list == null) return null
                val spot = name(file.resolve(list.getOrNull(1))) ?: return null
                Colour(Mode.Cmyk, listOf(0.0), spot.takeIf { it !in processNames })
            }
            "DeviceN" -> {
                val names = file.resolve(list?.getOrNull(1)) as? List<*> ?: return null
                Colour(Mode.Cmyk, List(names.size) { 0.0 })
            }
            else -> null
        }
    }

    private fun colourName(colour: Colour): String {
        colour.spot?.let { return it }
        val c = colour.components
        fun pc(v: Double, scale: Int) = (v * scale).roundToInt()
        return when (colour.mode) {
            Mode.Cmyk -> when {
                c.size != 4 -> "CMYK"
                c == listOf(0.0, 0.0, 0.0, 1.0) -> "[Black]"
                c.all { it == 0.0 } -> "[Paper]"
                else -> "C=${pc(c[0], 100)} M=${pc(c[1], 100)} Y=${pc(c[2], 100)} K=${pc(c[3], 100)}"
            }
            Mode.Rgb -> if (c.size != 3) "RGB" else "R=${pc(c[0], 255)} G=${pc(c[1], 255)} B=${pc(c[2], 255)}"
            Mode.Grey -> when (val g = c.firstOrNull()) {
                null -> "Gray"
                0.0 -> "[Black]"
                1.0 -> "[White]"
                else -> "Gray ${pc(g, 100)}%"
            }
        }
    }

    private fun white(colour: Colour) = when (colour.mode) {
        Mode.Cmyk -> colour.components.size >= 4 && colour.components.all { it < 0.001 }
        Mode.Rgb -> colour.components.size >= 3 && colour.components.all { it > 0.999 }
        Mode.Grey -> (colour.components.firstOrNull() ?: 0.0) > 0.999
    }

    private fun fontType(file: PdfFile, font: Map<String, Any?>): String = when (val subtype = name(file.resolve(font["Subtype"]))) {
        "Type1", "TrueType", "Type3", "MMType1", "CIDFontType0", "CIDFontType2" -> subtype
        "Type0" -> when (val cid = name(descendant(file, font)?.let { file.resolve(it["Subtype"]) })) {
            "CIDFontType0", "CIDFontType2" -> cid
            else -> "OpenType CFF"
        }
        else -> "Unknown"
    }

    private fun descendant(file: PdfFile, font: Map<String, Any?>) = (file.resolve(font["DescendantFonts"]) as? List<*>)?.firstOrNull()?.let(file::dict)

    /**
     * A font file in the descriptor, the Type0 descendant's descriptor, or a Type3 font (its glyphs are content).
     * TaxiwayCore reads only the top-level descriptor, so it lists every Type0 and Type3 font as not embedded.
     */
    private fun embedded(file: PdfFile, font: Map<String, Any?>): Boolean {
        if (name(file.resolve(font["Subtype"])) == "Type3") return true
        val holder = if (name(file.resolve(font["Subtype"])) == "Type0") descendant(file, font) ?: return false else font
        val descriptor = file.dict(holder["FontDescriptor"]) ?: return false
        return listOf("FontFile", "FontFile2", "FontFile3").any { file.resolve(descriptor[it]) is PdfStream }
    }

    private fun subset(name: String) = name.length > 7 && name[6] == '+' && name.take(6).all { it in 'A'..'Z' }

    /** Everything the built-in profiles check. */
    fun read(file: PdfFile, onPage: (Int) -> Unit = {}): Document {
        val bytes = file.bytes
        val catalog = file.dict(file.trailer["Root"]) ?: emptyMap()
        val info = file.dict(file.trailer["Info"]) ?: emptyMap()
        val header = Regex("""%PDF-(\d\.\d)""").find(String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1))?.groupValues?.get(1) ?: "1.0"
        val catalogVersion = name(file.resolve(catalog["Version"]))
        val version = if (catalogVersion != null && catalogVersion > header) catalogVersion else header
        val names = file.dict(catalog["Names"])
        val openAction = file.dict(catalog["OpenAction"])
        val intents = (file.resolve(catalog["OutputIntents"]) as? List<*>)?.mapNotNull(file::dict).orEmpty()
        val xmp = (file.resolve(catalog["Metadata"]) as? PdfStream)?.let(PdfFilters::decode)?.toString(Charsets.UTF_8)

        val pages = ArrayList<Page>()
        val fonts = LinkedHashMap<String, Font>()
        val images = ArrayList<Image>()
        val colourSpaces = LinkedHashMap<String, MutableSet<Int>>()
        val spots = sortedSetOf<String>()
        val usages = ArrayList<Usage>()
        val strokes = LinkedHashMap<String, Stroke>()
        val annotations = ArrayList<Pair<String, Int>>()
        var transparency = false

        file.pages.forEachIndexed { index, page ->
            onPage(index)
            val media = file.mediaBox(page)
            val crop = file.cropBox(page)
            fun box(key: String) = (PdfBox.of((file.resolve(page.dict[key]) as? List<*>)?.map(file::resolve))?.intersect(media) ?: crop)
                .takeIf { differs(it, media) }
            pages.add(Page(index, media, box("TrimBox"), box("BleedBox"), file.rotation(page)))

            val resources = file.dict(page.attribute("Resources"))
            file.dict(page.dict["Group"])?.let { if (name(file.resolve(it["S"])) == "Transparency") transparency = true }
            resources?.dict(file, "ExtGState")?.values?.forEach { value ->
                val gs = file.dict(value) ?: return@forEach
                val ca = (file.resolve(gs["ca"]) as? Number)?.toDouble() ?: 1.0
                val bigCa = (file.resolve(gs["CA"]) as? Number)?.toDouble() ?: 1.0
                val blend = name(file.resolve(gs["BM"]))
                val mask = file.resolve(gs["SMask"])
                if (ca < 1 || bigCa < 1 || (blend != null && blend != "Normal") || (mask != null && name(mask) != "None")) transparency = true
            }

            resources?.dict(file, "Font")?.forEach { (key, value) ->
                val font = file.dict(value) ?: return@forEach
                val fontName = name(file.resolve(font["BaseFont"])) ?: key
                val known = fonts[fontName]
                fonts[fontName] = known?.copy(pages = known.pages + index)
                    ?: Font(fontName, fontType(file, font), embedded(file, font), subset(fontName), listOf(index))
            }

            resources?.dict(file, "ColorSpace")?.values?.forEach { value ->
                val space = file.resolve(value)
                val family = name(space) ?: name((space as? List<*>)?.firstOrNull()?.let(file::resolve)) ?: return@forEach
                colourSpaces.getOrPut(if (family in spaceFamilies) family else "Unknown") { sortedSetOf() }.add(index)
                val list = space as? List<*> ?: return@forEach
                when (family) {
                    "Separation" -> name(file.resolve(list.getOrNull(1)))?.takeIf { it != "All" && it != "None" }?.let(spots::add)
                    "DeviceN" -> (file.resolve(list.getOrNull(1)) as? List<*>)?.forEach { item ->
                        name(file.resolve(item))?.takeIf { it !in processNames }?.let(spots::add)
                    }
                }
            }

            val scanned = runCatching { scanPage(file, page, index, resources) }.getOrNull()
            scanned?.usages?.let(usages::addAll)
            scanned?.strokes?.forEach { width -> strokes.putIfAbsent("$index:${(width * 1000).roundToInt()}", Stroke(index, width)) }
            resources?.dict(file, "XObject")?.forEach { (key, value) ->
                val stream = file.resolve(value) as? PdfStream ?: return@forEach
                if (name(file.resolve(stream.dict["Subtype"])) != "Image") return@forEach
                val width = (file.resolve(stream.dict["Width"]) as? Number)?.toInt() ?: 0
                val height = (file.resolve(stream.dict["Height"]) as? Number)?.toInt() ?: 0
                val placed = scanned?.placements?.get(key)
                images.add(Image("img_${index}_${images.size}", index, width, height, placed?.first ?: width.toDouble(), placed?.second ?: height.toDouble()))
            }

            (file.resolve(page.dict["Annots"]) as? List<*>)?.forEach { annot ->
                val subtype = name(file.dict(annot)?.get("Subtype")?.let(file::resolve)) ?: "Unknown"
                annotations.add((if (subtype in annotationTypes) subtype else "Other") to index)
            }
        }

        val merged = LinkedHashMap<String, ColourUsage>()
        for (usage in usages) {
            val c = usage.colour
            val key = c.spot?.let { "spot:$it" } ?: "${c.mode}:${c.components.joinToString(",") { (it * 100).roundToInt().toString() }}"
            merged.getOrPut(key) {
                ColourUsage(colourName(c), c.mode, c.components, if (c.mode == Mode.Cmyk && c.components.size == 4) c.components.sum() * 100 else null)
            }
        }
        val overprints = LinkedHashMap<String, Overprint>()
        for (usage in usages.filter { it.overprint }) {
            val isWhite = white(usage.colour)
            overprints.putIfAbsent("${usage.page}:${usage.context}:$isWhite", Overprint(usage.page, usage.context, isWhite))
        }

        return Document(
            fileSize = bytes.size.toLong(),
            pdfVersion = version,
            producer = text(file.resolve(info["Producer"])),
            creator = text(file.resolve(info["Creator"])),
            linearised = String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1).contains("Linearized"),
            tagged = file.resolve(file.dict(catalog["MarkInfo"])?.get("Marked")) == true,
            layers = file.dict(catalog["OCProperties"]) != null,
            transparency = transparency,
            embeddedFiles = names?.let { file.dict(it["EmbeddedFiles"]) } != null,
            javaScript = names?.let { file.dict(it["JavaScript"]) } != null || name(openAction?.get("S")?.let(file::resolve)) == "JavaScript",
            outputIntent = intents.firstOrNull()?.let { text(file.resolve(it["OutputConditionIdentifier"])) },
            outputIntentTypes = intents.map { name(file.resolve(it["S"])) ?: "Unknown" },
            c2pa = xmp?.contains("c2pa") == true,
            genAI = xmp != null && (xmp.contains("DigitalSourceType") || xmp.contains("aig:")),
            pages = pages,
            fonts = fonts.values.sortedBy { it.name },
            images = images,
            colourSpaces = colourSpaces.mapValues { it.value.toList() },
            spotColours = spots.toList(),
            colourUsages = merged.values.sortedBy { it.name },
            overprints = overprints.values.toList(),
            strokes = strokes.values.toList(),
            annotations = annotations,
        )
    }

    /** dt-ios PDFPreflightViewController.pageIssues: per page, errors and all issues. */
    fun pageIssues(results: List<Result>): Map<Int, Pair<Int, Int>> {
        val out = HashMap<Int, Pair<Int, Int>>()
        for (result in results.filter { it.isIssue }) {
            for (page in result.pages.orEmpty()) {
                val (errors, issues) = out[page] ?: (0 to 0)
                out[page] = (errors + if (result.isError) 1 else 0) to issues + 1
            }
        }
        return out
    }
}
