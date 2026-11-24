package com.delphi.delphitools.tools.images

import com.delphi.delphitools.colour.fromLinear
import com.delphi.delphitools.colour.toLinear
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class AtlasRow(val label: String, val value: String)

data class AtlasSection(val title: String, val rows: List<AtlasRow>)

/** dt-core ImageAtlasReport.Atlas. [heading] is the compass bearing the camera faced, 0 north, clockwise. */
data class AtlasData(
    val sections: List<AtlasSection> = emptyList(),
    val coordinate: Pair<Double, Double>? = null,
    val fieldCount: Int = 0,
    val readable: Boolean = false,
    val hasAlpha: Boolean = false,
    val heading: Double? = null,
    val byteCount: Int = 0,
    val format: String? = null,
    val profile: String? = null,
)

/**
 * What the platform reads from a file, for [ImageAtlasReport.build]. [tags] are android.media.ExifInterface names and
 * values; [xmp] is the file's XMP packet.
 */
class AtlasFacts(
    val filename: String,
    val byteCount: Int,
    val sizeLabel: String,
    val mimeType: String?,
    val width: Int?,
    val height: Int?,
    val colourModel: String?,
    val depth: Int?,
    val hasAlpha: Boolean?,
    val profile: String?,
    val tags: Map<String, String>,
    val xmp: String?,
)

/** dt-core ImageAtlasReport over ExifInterface tags instead of ImageIO's property dictionaries. */
object ImageAtlasReport {
    private val sectionOrder = listOf("Camera", "Capture", "Image", "Location", "Dates", "IPTC", "XMP", "Other")

    // web parity: camera first
    private val leadingLabels = listOf("Make", "Model", "Lens Make", "Lens Model")

    // exifinterface names differ
    private val imageIoNames = mapOf(
        "PhotographicSensitivity" to "ISOSpeedRatings",
        "FocalLengthIn35mmFilm" to "FocalLenIn35mmFilm",
        "SubSecTime" to "SubsecTime",
        "SubSecTimeOriginal" to "SubsecTimeOriginal",
        "SubSecTimeDigitized" to "SubsecTimeDigitized",
    )

    private val ignoredKeys = setOf(
        "componentsconfiguration", "cfapattern", "oecf", "spatialfrequencyresponse",
        "devicesettingdescription", "printim", "interopindex", "interoperabilityindex",
        "exifversion", "flashpixversion", "gpsversionid", "gpsprocessingmethod",
        "gpsareainformation", "subjectarea", "subjectlocation", "whitepoint",
        "primarychromaticities", "ycbcrcoefficients", "referenceblackwhite",
        "stripoffsets", "stripbytecounts", "tileoffsets", "tilebytecounts", "rowsperstrip",
        "thumbnail", "padding", "offsetschema", "relatedsoundfile", "usercomment",
        "jpeginterchangeformat", "jpeginterchangeformatlength", "xmp", "imagewidth", "imagelength",
        "versionid", "processingmethod", "areainformation",
    )

    // imageio synthesises these
    private val mirroredPrefixes = setOf("exif", "exifEX", "tiff", "aux", "iio", "xmlns", "rdf", "x", "stEvt", "stRef")

    private val acronyms = listOf(
        "Iso" to "ISO", "Gps" to "GPS", "Exif" to "Exif", "Xmp" to "XMP", "Iptc" to "IPTC",
        "F Number" to "F-Number", "Y Cb Cr" to "YCbCr", "Dpi" to "DPI", "S Rgb" to "sRGB",
    ).map { (word, acronym) -> Regex("\\b$word\\b") to acronym }

    private val labelOverrides = mapOf(
        "Date Time Original" to "Date Taken",
        "Focal Len In35mm Film" to "35mm Equivalent",
        "Pixel X Dimension" to "Pixel Width",
        "Pixel Y Dimension" to "Pixel Height",
    )

    private val enumLabels: Map<String, Map<Int, String>> = mapOf(
        "colorspace" to mapOf(1 to "sRGB", 65535 to "Uncalibrated"),
        "ycbcrpositioning" to mapOf(1 to "Centered", 2 to "Co-sited"),
        "contrast" to mapOf(0 to "Normal", 1 to "Soft", 2 to "Hard"),
        "saturation" to mapOf(0 to "Normal", 1 to "Low", 2 to "High"),
        "sharpness" to mapOf(0 to "Normal", 1 to "Soft", 2 to "Hard"),
        "customrendered" to mapOf(0 to "Normal", 1 to "Custom"),
        "exposuremode" to mapOf(0 to "Auto", 1 to "Manual", 2 to "Auto bracket"),
        "whitebalance" to mapOf(0 to "Auto", 1 to "Manual"),
        "scenecapturetype" to mapOf(0 to "Standard", 1 to "Landscape", 2 to "Portrait", 3 to "Night scene"),
        "gaincontrol" to mapOf(0 to "None", 1 to "Low gain up", 2 to "High gain up"),
        "sensingmethod" to mapOf(1 to "Not defined", 2 to "One-chip color area", 3 to "Two-chip color area"),
        "filesource" to mapOf(3 to "Digital camera"),
        "scenetype" to mapOf(1 to "Directly photographed"),
        "subjectdistancerange" to mapOf(0 to "Unknown", 1 to "Macro", 2 to "Close view", 3 to "Distant view"),
        "exposureprogram" to mapOf(
            0 to "Not defined", 1 to "Manual", 2 to "Normal program", 3 to "Aperture priority",
            4 to "Shutter priority", 5 to "Creative program", 6 to "Action program",
            7 to "Portrait mode", 8 to "Landscape mode",
        ),
        "meteringmode" to mapOf(
            0 to "Unknown", 1 to "Average", 2 to "Center-weighted average", 3 to "Spot",
            4 to "Multi-spot", 5 to "Multi-segment", 6 to "Partial", 255 to "Other",
        ),
        "lightsource" to mapOf(
            0 to "Unknown", 1 to "Daylight", 2 to "Fluorescent", 3 to "Tungsten (incandescent)",
            4 to "Flash", 9 to "Fine weather", 10 to "Cloudy weather", 11 to "Shade",
            12 to "Daylight fluorescent", 13 to "Day white fluorescent", 14 to "Cool white fluorescent",
            15 to "White fluorescent", 16 to "Warm white fluorescent", 17 to "Standard light A",
            18 to "Standard light B", 19 to "Standard light C", 20 to "D55", 21 to "D65",
            22 to "D75", 23 to "D50", 24 to "ISO studio tungsten", 255 to "Other",
        ),
        "resolutionunit" to mapOf(2 to "Inches", 3 to "cm"),
        "measuremode" to mapOf(2 to "2D measurement", 3 to "3D measurement"),
        "orientation" to mapOf(
            1 to "Normal", 2 to "Mirrored", 3 to "Rotated 180°", 4 to "Mirrored, 180°",
            5 to "Mirrored, 270°", 6 to "Rotated 90°", 7 to "Mirrored, 90°", 8 to "Rotated 270°",
        ),
    )

    // exif infinity sentinel
    private const val INFINITY_THRESHOLD = 4_294_967_245.0

    /** The file's metadata in sections for display; null [facts] is a file the platform could not open. */
    fun build(facts: AtlasFacts?): AtlasData {
        facts ?: return AtlasData()
        val sections = mutableListOf<AtlasSection>()
        val fileRows = mutableListOf(AtlasRow("File Name", facts.filename), AtlasRow("File Size", facts.sizeLabel))
        val format = facts.mimeType?.let(::formatName)
        facts.mimeType?.let { fileRows.add(AtlasRow("MIME Type", it)) }
        if (facts.width != null && facts.height != null) fileRows.add(AtlasRow("Dimensions", "${facts.width} × ${facts.height}"))
        dpi(facts.tags)?.let { fileRows.add(AtlasRow("Resolution", "${it.roundToInt()} dpi")) }
        sections.add(AtlasSection("File", fileRows))

        val colourRows = buildList {
            facts.colourModel?.let { add(AtlasRow("Colour Model", it)) }
            facts.profile?.let { add(AtlasRow("Colour Profile", it)) }
            facts.depth?.let { add(AtlasRow("Bit Depth", "$it bits")) }
            facts.hasAlpha?.let { add(AtlasRow("Alpha", if (it) "Yes" else "No")) }
        }
        if (colourRows.isNotEmpty()) sections.add(AtlasSection("Colour", colourRows))

        // ponytail: no IPTC-IIM, PNG text or JFIF rows; ExifInterface reads none of them, add a parser when a file needs one
        val buckets = mutableMapOf<String, MutableList<AtlasRow>>()
        val seen = mutableSetOf<String>()
        for ((tag, text) in facts.tags) {
            val gps = tag.startsWith("GPS")
            val key = if (gps) tag.removePrefix("GPS") else imageIoNames[tag] ?: tag
            val lower = key.lowercase()
            if (lower in ignoredKeys || tag.lowercase() in ignoredKeys) continue
            if ("makernote" in lower || "serial" in lower) continue
            // imageio: undefined as data
            if (text.any { it.isISOControl() }) continue
            val value = prettyValue(lower, raw(tag, text))
            if (value.isEmpty()) continue
            val label = prettyLabel(key)
            if (!seen.add(label)) continue
            buckets.getOrPut(section(lower, gps)) { mutableListOf() }.add(AtlasRow(label, value))
        }
        for (row in facts.xmp?.let(::xmpRows).orEmpty()) {
            if (seen.add(row.label)) buckets.getOrPut("XMP") { mutableListOf() }.add(row)
        }
        for (title in sectionOrder) {
            val rows = buckets[title]?.takeIf { it.isNotEmpty() } ?: continue
            sections.add(AtlasSection(title, rows.sortedWith(compareBy({ leading(it) }, { it.label }))))
        }
        return AtlasData(
            sections = sections,
            coordinate = coordinate(facts.tags),
            fieldCount = seen.size,
            readable = true,
            hasAlpha = facts.hasAlpha == true,
            heading = facts.tags["GPSImgDirection"]?.let(::number),
            byteCount = facts.byteCount,
            format = format,
            profile = facts.profile,
        )
    }

    /** The atlas as plain text: a heading per section, a `label: value` line per row. */
    fun reportText(atlas: AtlasData): String {
        val lines = mutableListOf<String>()
        for (section in atlas.sections) {
            lines.add(section.title.uppercase())
            section.rows.forEach { lines.add("${it.label}: ${it.value}") }
            lines.add("")
        }
        atlas.coordinate?.let { (lat, lon) -> lines.add(String.format(Locale.ROOT, "GPS: %.6f, %.6f", lat, lon)) }
        return lines.joinToString("\n")
    }

    private fun formatName(mime: String): String = when (val type = mime.substringAfter('/').lowercase()) {
        "heif", "heic" -> "HEIC"
        "x-adobe-dng" -> "DNG"
        "svg+xml" -> "SVG"
        else -> type.removePrefix("x-").uppercase()
    }

    // exif default: inches
    private fun dpi(tags: Map<String, String>): Double? {
        val resolution = tags["XResolution"]?.let(::number) ?: return null
        return if (tags["ResolutionUnit"]?.let(::number)?.toInt() == 3) resolution * 2.54 else resolution
    }

    private fun leading(row: AtlasRow) = leadingLabels.indexOf(row.label).let { if (it < 0) leadingLabels.size else it }

    private fun section(key: String, gps: Boolean): String {
        if (gps) return "Location"
        return when {
            Regex("make|model|lens|camera|software|artist|hostcomputer|copyright|owner").containsMatchIn(key) -> "Camera"
            Regex("fnumber|exposure|iso|shutter|focal|flash|whitebalance|metering|brightness|lightsource|aperture").containsMatchIn(key) -> "Capture"
            Regex("datetime|createdate|modifydate|offsettime|subsec").containsMatchIn(key) -> "Dates"
            Regex("width|height|dimension|orientation|resolution|colorspace|bitspersample|photometric|compress").containsMatchIn(key) -> "Image"
            else -> "Other"
        }
    }

    private fun coordinate(tags: Map<String, String>): Pair<Double, Double>? =
        MetadataStripEngine.location(tags)?.let { it.latitude to it.longitude }

    /** An ExifInterface value typed as ImageIO hands it over: a number, a list, or text. */
    fun raw(tag: String, text: String): Any {
        when (tag) {
            "GPSLatitude", "GPSLongitude", "GPSDestLatitude", "GPSDestLongitude" -> MetadataStripEngine.degrees(text)?.let { return it }
            "GPSTimeStamp" -> {
                val parts = text.split(',').map { number(it) ?: return text }
                if (parts.size == 3) return parts.joinToString(":") { String.format(Locale.ROOT, "%02d", it.toInt()) }
            }
        }
        number(text)?.let { return it }
        val parts = text.split(',')
        if (parts.size > 1) parts.map { number(it) ?: return text }.let { return it }
        return text
    }

    private val numeric = Regex("-?\\d+(\\.\\d+)?(/-?\\d+)?")

    private fun number(text: String): Double? {
        val trimmed = text.trim()
        if (!numeric.matches(trimmed)) return null
        val (top, bottom) = trimmed.split('/').let { it[0] to it.getOrNull(1) }
        val value = top.toDoubleOrNull() ?: return null
        val divisor = bottom?.toDoubleOrNull() ?: return value
        return if (divisor == 0.0) null else value / divisor
    }

    /** A key as a label: words split, acronyms kept, some renamed (`DateTimeOriginal` is Date Taken). */
    fun prettyLabel(key: String): String {
        var text = key.replace("_", " ")
        text = text.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        text = text.replace(Regex("([A-Z])([A-Z][a-z])"), "$1 $2")
        text = text.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { it.take(1).uppercase() + it.drop(1).lowercase() }
        for ((word, acronym) in acronyms) text = text.replace(word, acronym)
        return labelOverrides[text] ?: text
    }

    /** A metadata value as text, with units and EXIF enumerations named; [key] is lowercased. */
    fun prettyValue(key: String, raw: Any): String {
        val base = formatValue(raw)
        val number = raw as? Double
        if (number != null && number == Math.rint(number) && abs(number) < Int.MAX_VALUE) {
            enumLabels[key]?.get(number.toInt())?.let { return it }
        }
        if (number != null) {
            when (key) {
                "subjectdistance" -> return if (number >= INFINITY_THRESHOLD) "Infinity" else "$base m"
                "exposuretime" -> return if (number < 1e-6 || number >= 1) base else "$base (1/${(1 / number).roundToLong()}s)"
                "exposurebiasvalue", "exposurecompensation" -> return "$base EV"
                "fnumber", "aperturevalue" -> return if (base.startsWith("f/")) base else "f/$base"
                "focallength", "focallenin35mmfilm", "focallengthin35mmformat" -> return "$base mm"
                "altitude" -> return "$base m"
                "imgdirection", "destbearing", "track" -> return "$base°"
                "digitalzoomratio" -> return if (number == 0.0) "No zoom" else "$base×"
            }
        }
        if (key == "iso" || key == "isospeedratings") return "ISO $base"
        return base
    }

    private fun formatValue(value: Any): String = when (value) {
        is Double -> when {
            !value.isFinite() || abs(value) >= 1e15 -> String.format(Locale.ROOT, "%g", value)
            value == Math.rint(value) -> value.toLong().toString()
            else -> shortest(Math.rint(value * 1e6) / 1e6)
        }
        is List<*> -> value.joinToString(", ") { formatValue(it ?: "") }
        else -> value.toString()
    }

    // %g: six significant digits
    private fun shortest(value: Double): String {
        val text = String.format(Locale.ROOT, "%g", value)
        if ('e' in text) {
            val (mantissa, exponent) = text.split('e')
            return mantissa.trimEnd('0').trimEnd('.') + "e" + exponent
        }
        return if ('.' in text) text.trimEnd('0').trimEnd('.') else text
    }

    private val describedAttribute = Regex("\\s([A-Za-z][\\w-]*):([A-Za-z][\\w-]*)=\"([^\"]*)\"")
    private val simpleElement = Regex("<([A-Za-z][\\w-]*):([A-Za-z][\\w-]*)(\\s[^>]*)?>([^<]+)</\\1:\\2>")
    private val arrayElement = Regex("<([A-Za-z][\\w-]*):([A-Za-z][\\w-]*)>\\s*<rdf:(Seq|Bag|Alt)>([\\s\\S]*?)</rdf:\\3>")
    private val listItem = Regex("<rdf:li[^>]*>([^<]*)</rdf:li>")
    private val descriptionTag = Regex("<rdf:Description\\b[^>]*>")

    /** Rows of an XMP [packet]'s own properties, the ones ImageIO does not mirror from EXIF or TIFF. */
    fun xmpRows(packet: String): List<AtlasRow> {
        val found = mutableListOf<Pair<Int, AtlasRow>>()
        fun add(at: Int, prefix: String, name: String, value: String) {
            val text = unescape(value).trim()
            if (prefix in mirroredPrefixes || text.isEmpty()) return
            found.add(at to AtlasRow(prettyLabel(name), text))
        }
        for (tag in descriptionTag.findAll(packet)) {
            for (match in describedAttribute.findAll(tag.value)) {
                add(tag.range.first + match.range.first, match.groupValues[1], match.groupValues[2], match.groupValues[3])
            }
        }
        for (match in simpleElement.findAll(packet)) add(match.range.first, match.groupValues[1], match.groupValues[2], match.groupValues[4])
        for (match in arrayElement.findAll(packet)) {
            val items = listItem.findAll(match.groupValues[4]).map { unescape(it.groupValues[1]).trim() }.filter { it.isNotEmpty() }
            add(match.range.first, match.groupValues[1], match.groupValues[2], items.joinToString(", "))
        }
        return found.sortedBy { it.first }.map { it.second }
    }

    private fun unescape(text: String) = text
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    /** The XMP packet in [data], from `<x:xmpmeta` to its close; null when there is none. */
    fun xmpPacket(data: ByteArray): String? {
        val text = String(data, Charsets.ISO_8859_1)
        val start = text.indexOf("<x:xmpmeta").takeIf { it >= 0 } ?: return null
        val end = text.indexOf("</x:xmpmeta>", start).takeIf { it >= 0 } ?: return null
        return String(data, start, end + "</x:xmpmeta>".length - start, Charsets.UTF_8)
    }
}

/**
 * dt-core ImageColourScan: channel histograms in the image's own encoding, where its colours fall on the CIE 1931
 * chromaticity diagram, and how much of it lies outside sRGB or lets the background through.
 * [chroma] is [CELLS]² non-premultiplied ARGB, row 0 at the top; [primaries] are the image space's red, green and blue xy.
 */
class ImageColourScan(
    val histogram: Array<IntArray>,
    val chroma: IntArray?,
    val outsideSRGB: Double,
    val transparent: Double?,
    val primaries: List<Pair<Double, Double>>?,
) {
    /** Area of the space's triangle over sRGB's, on the xy diagram. */
    val gamutOverSRGB get() = primaries?.let { area(it) / area(SRGB) }

    companion object {
        const val PLOT_WIDTH = 0.8
        const val PLOT_HEIGHT = 0.9
        const val CELLS = 128

        val SRGB = listOf(0.64 to 0.33, 0.30 to 0.60, 0.15 to 0.06)
        val WHITE = 0.3127 to 0.3290

        /** Gamma-encoded sRGB to linear, by byte. */
        val srgbEotf = DoubleArray(256) { toLinear(it / 255.0) }

        /** Identity: the image is sRGB. */
        val identity = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)

        fun area(triangle: List<Pair<Double, Double>>): Double {
            if (triangle.size != 3) return 0.0
            val (a, b, c) = triangle
            return abs((b.first - a.first) * (c.second - a.second) - (c.first - a.first) * (b.second - a.second)) / 2
        }

        /**
         * Scans [argb], non-premultiplied pixels of a small copy in the file's own space. [eotf] decodes a byte of that
         * space to linear light and [toLinearSrgb], row-major 3 × 3, takes its linear RGB to linear sRGB, unclamped.
         */
        fun scan(
            argb: IntArray,
            hasAlpha: Boolean,
            eotf: DoubleArray = srgbEotf,
            toLinearSrgb: DoubleArray = identity,
            primaries: List<Pair<Double, Double>>? = SRGB,
        ): ImageColourScan {
            val bins = Array(3) { IntArray(256) }
            val counts = IntArray(CELLS * CELLS)
            var seeThrough = 0
            var coloured = 0
            var outside = 0
            val m = toLinearSrgb
            for (pixel in argb) {
                val alpha = pixel ushr 24
                if (alpha < 255) seeThrough++
                if (alpha == 0) continue
                val r8 = pixel shr 16 and 0xFF
                val g8 = pixel shr 8 and 0xFF
                val b8 = pixel and 0xFF
                bins[0][r8]++
                bins[1][g8]++
                bins[2][b8]++
                if (alpha / 255.0 <= 0.004) continue
                val lr = eotf[r8]
                val lg = eotf[g8]
                val lb = eotf[b8]
                val red = m[0] * lr + m[1] * lg + m[2] * lb
                val green = m[3] * lr + m[4] * lg + m[5] * lb
                val blue = m[6] * lr + m[7] * lg + m[8] * lb
                val brightest = max(red, max(green, blue))
                // black has no chromaticity
                if (brightest <= 0.002) continue
                coloured++
                if (min(red, min(green, blue)) < -0.01 * brightest) outside++
                val (x, y) = chromaticity(red, green, blue) ?: continue
                val across = (x / PLOT_WIDTH * CELLS).toInt()
                val down = ((1 - y / PLOT_HEIGHT) * CELLS).toInt()
                if (across !in 0 until CELLS || down !in 0 until CELLS) continue
                counts[down * CELLS + across]++
            }
            return ImageColourScan(
                histogram = bins,
                chroma = densityImage(counts),
                outsideSRGB = outside.toDouble() / max(1, coloured),
                transparent = if (hasAlpha) seeThrough.toDouble() / max(1, argb.size) else null,
                primaries = primaries,
            )
        }

        /** CIE xy of a linear sRGB colour; null for black. */
        fun chromaticity(red: Double, green: Double, blue: Double): Pair<Double, Double>? {
            val x = 0.4124 * red + 0.3576 * green + 0.1805 * blue
            val y = 0.2126 * red + 0.7152 * green + 0.0722 * blue
            val z = 0.0193 * red + 0.1192 * green + 0.9505 * blue
            val sum = x + y + z
            if (sum <= 1e-6) return null
            return x / sum to y / sum
        }

        private fun densityImage(counts: IntArray): IntArray? {
            val most = counts.max()
            if (most <= 0) return null
            val scale = ln1p(most.toDouble())
            return IntArray(CELLS * CELLS) { cell ->
                if (counts[cell] == 0) return@IntArray 0
                val alpha = 0.3 + 0.7 * ln1p(counts[cell].toDouble()) / scale
                val x = (cell % CELLS + 0.5) / CELLS * PLOT_WIDTH
                val y = (1 - (cell / CELLS + 0.5) / CELLS) * PLOT_HEIGHT
                val ink = display(x, y)
                ((alpha * 255).toInt() shl 24) or ((ink[0] * 255).toInt() shl 16) or ((ink[1] * 255).toInt() shl 8) or (ink[2] * 255).toInt()
            }
        }

        /** Gamma-encoded sRGB, 0 to 1, of chromaticity [x], [y] at full brightness, for drawing the diagram. */
        fun display(x: Double, y: Double): DoubleArray {
            if (y <= 1e-4) return doubleArrayOf(0.0, 0.0, 0.0)
            val bx = x / y
            val bz = (1 - x - y) / y
            val linear = doubleArrayOf(
                3.2406 * bx - 1.5372 - 0.4986 * bz,
                -0.9689 * bx + 1.8758 + 0.0415 * bz,
                0.0557 * bx - 0.2040 + 1.0570 * bz,
            ).map { max(0.0, it) }
            val top = max(linear.max(), 1e-6)
            return linear.map { fromLinear(it / top) }.toDoubleArray()
        }

        /** ImageColourScopes.peak: end bins hold clipping. */
        fun peak(bins: Array<IntArray>): Int {
            val inner = bins.maxOf { if (it.size > 2) it.slice(1 until it.size - 1).max() else 0 }
            return if (inner > 0) inner else bins.maxOf { it.max() }
        }

        /** Nanometres of `locus[index]`. */
        fun wavelength(index: Int) = 380 + index * 5

        /** CIE 1931 2° spectral locus, 380 to 700 nm in 5 nm steps, from the CVRL colour matching functions. */
        val locus: List<Pair<Double, Double>> = doubleArrayOf(
            0.1741, 0.0050, 0.1740, 0.0050, 0.1738, 0.0049, 0.1736, 0.0049, 0.1733, 0.0048, 0.1730, 0.0048,
            0.1726, 0.0048, 0.1721, 0.0048, 0.1714, 0.0051, 0.1703, 0.0058, 0.1689, 0.0069, 0.1669, 0.0086,
            0.1644, 0.0109, 0.1611, 0.0138, 0.1566, 0.0177, 0.1510, 0.0227, 0.1440, 0.0297, 0.1355, 0.0399,
            0.1241, 0.0578, 0.1096, 0.0868, 0.0913, 0.1327, 0.0687, 0.2007, 0.0454, 0.2950, 0.0235, 0.4127,
            0.0082, 0.5384, 0.0039, 0.6548, 0.0139, 0.7502, 0.0389, 0.8120, 0.0743, 0.8338, 0.1142, 0.8262,
            0.1547, 0.8059, 0.1929, 0.7816, 0.2296, 0.7543, 0.2658, 0.7243, 0.3016, 0.6923, 0.3374, 0.6588,
            0.3731, 0.6245, 0.4087, 0.5896, 0.4441, 0.5547, 0.4788, 0.5202, 0.5125, 0.4866, 0.5448, 0.4544,
            0.5752, 0.4242, 0.6029, 0.3965, 0.6270, 0.3725, 0.6482, 0.3514, 0.6658, 0.3340, 0.6801, 0.3197,
            0.6915, 0.3083, 0.7006, 0.2993, 0.7079, 0.2920, 0.7140, 0.2859, 0.7190, 0.2809, 0.7230, 0.2769,
            0.7260, 0.2740, 0.7283, 0.2717, 0.7300, 0.2700, 0.7311, 0.2689, 0.7320, 0.2680, 0.7327, 0.2673,
            0.7334, 0.2666, 0.7340, 0.2660, 0.7344, 0.2656, 0.7346, 0.2654, 0.7347, 0.2653,
        ).toList().chunked(2) { it[0] to it[1] }
    }
}

/** ImageAtlasPicture.caption: aspect ratio and megapixels, `16:9  8.3 MP`. */
fun imageAtlasCaption(width: Int, height: Int): String {
    val megapixels = width.toDouble() * height / 1_000_000
    val figure = when {
        megapixels >= 10 -> String.format(Locale.ROOT, "%.0f", megapixels)
        megapixels >= 0.1 -> String.format(Locale.ROOT, "%.1f", megapixels)
        else -> String.format(Locale.ROOT, "%.2f", megapixels)
    }
    return "${PlaceholderSpec.aspect(width, height)}  $figure MP"
}
