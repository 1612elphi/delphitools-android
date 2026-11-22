package com.delphi.delphitools.tools.images

import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class MetadataEntry(val label: String, val detail: String)

/** [readable] is false when the image could not be opened. */
class MetadataReport(val entries: List<MetadataEntry> = emptyList(), val readable: Boolean = false)

class MetadataStripResult(val data: ByteArray, val removed: List<String>)

/** dt-core MetadataScan: which blocks of metadata a file carries, and where it says it was taken. */
data class MetadataScan(val blocks: Set<Block> = emptySet(), val location: Location? = null) {
    enum class Block(val label: String) {
        Exif("EXIF"), Gps("GPS"), Tiff("TIFF"), Iptc("IPTC"), Xmp("XMP"), Maker("MakerNote"), Icc("ICC"), C2pa("C2PA"),
    }

    data class Location(val latitude: Double, val longitude: Double)

    companion object {
        /** [location] in degrees to four places, with hemisphere letters. */
        fun label(location: Location): String = String.format(
            Locale.ROOT,
            "%.4f° %s, %.4f° %s",
            abs(location.latitude), if (location.latitude < 0) "S" else "N",
            abs(location.longitude), if (location.longitude < 0) "W" else "E",
        )

        /** A lamp per block: off when [found] lacks it, struck when [left] is known and lacks it. */
        fun lamps(found: MetadataScan, left: MetadataScan?): List<WordLamp> = Block.entries.map { block ->
            when {
                block !in found.blocks -> WordLamp.Off
                left == null || block in left.blocks -> WordLamp.On
                else -> WordLamp.Struck
            }
        }
    }
}

/**
 * dt-core MetadataStripEngine and MetadataScan over tags as android.media.ExifInterface names and reads them.
 * [tags] null: the image did not open.
 */
object MetadataStripEngine {
    const val MAKER_NOTE = "MakerNote"
    const val USER_COMMENT = "UserComment"

    private val tiffTags = listOf("Make", "Model", "Software", "DateTime", "Artist", "Copyright", "ImageDescription", "HostComputer")
    // ponytail: a fixed tag list stands in for ImageIO's whole EXIF dictionary; extend it if a file's EXIF goes unseen
    private val exifTags = listOf(
        "DateTimeOriginal", "DateTimeDigitized", "OffsetTime", "OffsetTimeOriginal", "OffsetTimeDigitized", "SubSecTime",
        "SubSecTimeOriginal", "SubSecTimeDigitized", "ExposureTime", "FNumber", "ExposureProgram", "PhotographicSensitivity",
        "ShutterSpeedValue", "ApertureValue", "BrightnessValue", "ExposureBiasValue", "MaxApertureValue", "MeteringMode",
        "LightSource", "Flash", "FocalLength", "FocalLengthIn35mmFilm", "SubjectArea", "SensingMethod", "ExposureMode",
        "WhiteBalance", "DigitalZoomRatio", "Contrast", "Saturation", "Sharpness", "SubjectDistanceRange", "ImageUniqueID",
        "CameraOwnerName", "BodySerialNumber", "LensSpecification", "LensMake", "LensModel", "LensSerialNumber", USER_COMMENT,
    )
    private val gpsTags = listOf(
        "GPSVersionID", "GPSLatitude", "GPSLongitude", "GPSAltitude", "GPSTimeStamp", "GPSDateStamp", "GPSSpeed",
        "GPSImgDirection", "GPSDestBearing", "GPSProcessingMethod", "GPSHPositioningError",
    )

    /** Every tag the report and the scan read. */
    val tagNames = tiffTags + exifTags + gpsTags + listOf("GPSLatitudeRef", "GPSLongitudeRef", "GPSAltitudeRef", MAKER_NOTE)

    /** The privacy fields of the image file [data], labelled for display; [profile] names its embedded colour profile. */
    fun report(data: ByteArray, tags: Map<String, String>?, profile: String?): MetadataReport {
        tags ?: return MetadataReport()
        val entries = mutableListOf<MetadataEntry>()
        fun add(label: String, tag: String) {
            tags[tag]?.takeIf { it.isNotEmpty() }?.let { entries.add(MetadataEntry(label, it)) }
        }

        coordinate(tags)?.let { entries.add(MetadataEntry("GPS coordinates", it)) }
        add("GPS timestamp", "GPSDateStamp")
        listOfNotNull(tags["Make"], tags["Model"]).joinToString(" ").takeIf { it.isNotEmpty() }?.let {
            entries.add(MetadataEntry("Camera", it))
        }
        add("Lens", "LensModel")
        add("Camera serial", "BodySerialNumber")
        add("Lens serial", "LensSerialNumber")
        add("Owner name", "CameraOwnerName")
        add("Taken", "DateTimeOriginal")
        add("Digitised", "DateTimeDigitized")
        add("Modified", "DateTime")
        add("Software", "Software")
        add("Artist", "Artist")
        add("Copyright", "Copyright")
        add("Description", "ImageDescription")
        add("Comment", USER_COMMENT)
        if (MAKER_NOTE in tags) entries.add(MetadataEntry("MakerNote", "vendor data present"))
        if (hasIptc(data)) entries.add(MetadataEntry("IPTC", "Photoshop resource block"))
        if (hasXmp(data)) entries.add(MetadataEntry("XMP packet", "present"))
        profile?.let { entries.add(MetadataEntry("Colour profile", it)) }
        return MetadataReport(entries, readable = true)
    }

    fun scan(data: ByteArray, tags: Map<String, String>?): MetadataScan {
        val tags = tags.orEmpty()
        val blocks = buildSet {
            if (exifTags.any { it in tags }) add(MetadataScan.Block.Exif)
            if (gpsTags.any { it in tags }) add(MetadataScan.Block.Gps)
            if (tiffTags.any { it in tags }) add(MetadataScan.Block.Tiff)
            if (hasIptc(data)) add(MetadataScan.Block.Iptc)
            if (hasXmp(data)) add(MetadataScan.Block.Xmp)
            if (MAKER_NOTE in tags) add(MetadataScan.Block.Maker)
            // acsp: icc header magic
            if (contains(data, "acsp")) add(MetadataScan.Block.Icc)
            if (hasContentCredentials(data)) add(MetadataScan.Block.C2pa)
        }
        return MetadataScan(blocks, location(tags))
    }

    /** What a strip removes from a file of [scan]'s blocks, in the order iOS lists it. */
    fun removed(scan: MetadataScan): List<String> = listOf(
        MetadataScan.Block.Exif, MetadataScan.Block.Gps, MetadataScan.Block.Tiff, MetadataScan.Block.Iptc,
        MetadataScan.Block.Maker, MetadataScan.Block.Xmp,
    ).filter { it in scan.blocks }.map { it.label }

    fun location(tags: Map<String, String>): MetadataScan.Location? {
        val latitude = tags["GPSLatitude"]?.let(::degrees) ?: return null
        val longitude = tags["GPSLongitude"]?.let(::degrees) ?: return null
        return MetadataScan.Location(
            if (tags["GPSLatitudeRef"] == "S") -latitude else latitude,
            if (tags["GPSLongitudeRef"] == "W") -longitude else longitude,
        )
    }

    private fun coordinate(tags: Map<String, String>): String? {
        val latitude = tags["GPSLatitude"]?.let(::degrees) ?: return null
        val longitude = tags["GPSLongitude"]?.let(::degrees) ?: return null
        val text = String.format(
            Locale.ROOT, "%.6f° %s, %.6f° %s",
            latitude, tags["GPSLatitudeRef"] ?: "N", longitude, tags["GPSLongitudeRef"] ?: "E",
        )
        val altitude = tags["GPSAltitude"]?.let(::rational) ?: return text
        val below = tags["GPSAltitudeRef"] == "1"
        // swift rounded(): halves away from zero
        val metres = altitude.roundToInt()
        return "$text · alt ${if (below) -metres else metres} m"
    }

    /** ExifInterface's `d/1,m/1,s/100` as decimal degrees. */
    fun degrees(text: String): Double? {
        val parts = text.split(',').map { rational(it) ?: return null }
        if (parts.size != 3) return null
        return parts[0] + parts[1] / 60 + parts[2] / 3600
    }

    private fun rational(text: String): Double? {
        val (top, bottom) = text.trim().split('/').takeIf { it.size == 2 } ?: return text.trim().toDoubleOrNull()
        val denominator = bottom.toDoubleOrNull()?.takeIf { it != 0.0 } ?: return null
        return top.toDoubleOrNull()?.div(denominator)
    }

    /** EXIF UserComment bytes: an 8-byte character code, then the text. */
    fun userComment(bytes: ByteArray): String {
        if (bytes.size <= 8) return ""
        val code = String(bytes, 0, 8, Charsets.US_ASCII).trimEnd('\u0000', ' ')
        val charset = when (code) {
            "UNICODE" -> if (bytes.size > 9 && bytes[8] == 0.toByte()) Charsets.UTF_16BE else Charsets.UTF_16LE
            "JIS" -> Charsets.ISO_8859_1
            else -> Charsets.UTF_8
        }
        return String(bytes, 8, bytes.size - 8, charset).trim('\u0000', ' ')
    }

    /** Whether [data] holds an XMP packet. */
    fun hasXmp(data: ByteArray) = contains(data, "<x:xmpmeta") || contains(data, "<rdf:RDF")

    fun hasContentCredentials(data: ByteArray) = contains(data, "jumb") && contains(data, "c2pa")

    // jpeg app13 header
    private fun hasIptc(data: ByteArray) = contains(data, "Photoshop 3.0")

    /** Whether [marker]'s UTF-8 bytes occur in [data]. */
    fun contains(data: ByteArray, marker: String): Boolean {
        val target = marker.toByteArray()
        val last = data.size - target.size
        var at = 0
        outer@ while (at <= last) {
            if (data[at] == target[0]) {
                for (i in 1 until target.size) if (data[at + i] != target[i]) {
                    at++
                    continue@outer
                }
                return true
            }
            at++
        }
        return false
    }
}

/** dt-core TimeCalcWorld's projection: equirectangular, from 80° north to 60° south. */
object WorldMap {
    const val NORTH = 80.0
    const val SOUTH = -60.0
    const val ASPECT = (360 / (NORTH - SOUTH)).toFloat()

    /** Where a place falls in a map [width] by [height], its latitude clamped to the latitudes drawn. */
    fun point(latitude: Double, longitude: Double, width: Float, height: Float): Pair<Float, Float> {
        val clamped = latitude.coerceIn(SOUTH, NORTH)
        return ((longitude + 180) / 360 * width).toFloat() to ((NORTH - clamped) / (NORTH - SOUTH) * height).toFloat()
    }
}

/** dt-core PNGChunkStrip: drops a PNG's ancillary chunks byte for byte; a re-encode would add chunks of its own. */
object PngChunkStrip {
    private val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val kept = setOf(
        "IHDR", "PLTE", "IDAT", "IEND", "tRNS",
        "gAMA", "cHRM", "sRGB", "iCCP", "sBIT", "cICP", "mDCV", "cLLI",
        "pHYs", "acTL", "fcTL", "fdAT",
    )

    fun isPng(data: ByteArray) = data.size > 8 && data.copyOfRange(0, 8).contentEquals(signature)

    /** Null for anything but a well-formed PNG. */
    fun strip(data: ByteArray): MetadataStripResult? {
        if (!isPng(data)) return null
        val out = ByteArrayOutputStream().apply { write(signature) }
        val removed = mutableListOf<String>()
        var cursor = 8
        while (cursor + 12 <= data.size) {
            val length = (0 until 4).fold(0L) { sum, i -> sum shl 8 or (data[cursor + i].toLong() and 0xFF) }
            val end = cursor + 12 + length
            if (end > data.size) return null
            val type = String(data, cursor + 4, 4, Charsets.US_ASCII)
            if (type in kept) {
                out.write(data, cursor, (end - cursor).toInt())
            } else if (type !in removed) {
                removed.add(type)
            }
            cursor = end.toInt()
            if (type == "IEND") return MetadataStripResult(out.toByteArray(), removed)
        }
        return null
    }
}
