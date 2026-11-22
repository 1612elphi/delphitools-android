package com.delphi.delphitools.tools.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** Reading and re-encoding image files for the metadata stripper, through the platform ExifInterface. */
object MetadataFiles {
    private const val QUALITY = 90

    class Read(val report: MetadataReport, val scan: MetadataScan)

    fun read(data: ByteArray): Read {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val tags = if (bounds.outWidth > 0) tags(data) else null
        // imageio: embedded icc only
        val profile = bounds.outColorSpace?.name?.takeIf { MetadataStripEngine.contains(data, "acsp") }
        return Read(MetadataStripEngine.report(data, tags, profile), MetadataStripEngine.scan(data, tags))
    }

    private fun tags(data: ByteArray): Map<String, String> {
        val exif = runCatching { ExifInterface(ByteArrayInputStream(data)) }.getOrNull() ?: return emptyMap()
        return MetadataStripEngine.tagNames.mapNotNull { tag ->
            when (tag) {
                MetadataStripEngine.MAKER_NOTE -> if (exif.hasAttribute(tag)) tag to "" else null
                MetadataStripEngine.USER_COMMENT -> exif.getAttributeBytes(tag)?.let { tag to MetadataStripEngine.userComment(it) }
                else -> exif.getAttribute(tag)?.let { tag to it.trim() }
            }
        }.toMap()
    }

    private enum class Kind { Jpeg, WebP, WebPLossless, Heic }

    private fun kind(data: ByteArray): Kind? {
        fun ascii(from: Int, length: Int) = if (data.size >= from + length) String(data, from, length, Charsets.US_ASCII) else ""
        return when {
            data.size > 3 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() -> Kind.Jpeg
            ascii(0, 4) == "RIFF" && ascii(8, 4) == "WEBP" -> when {
                // vp8x animation flag
                ascii(12, 4) == "VP8X" && data.size > 20 && data[20].toInt() and 0x02 != 0 -> null
                ascii(12, 4) == "VP8L" -> Kind.WebPLossless
                else -> Kind.WebP
            }
            ascii(4, 4) == "ftyp" && ascii(8, 4) in setOf("heic", "heix", "mif1") && !ascii(16, 16).contains("avif") -> Kind.Heic
            else -> null
        }
    }

    /**
     * dt-core MetadataStripEngine.strip: a PNG loses its ancillary chunks; JPEG, WebP and HEIC are re-encoded from
     * the pixels, upright, with no metadata. Null for any other format, or an animated one.
     */
    fun strip(data: ByteArray, scan: MetadataScan, scratch: File): MetadataStripResult? {
        if (PngChunkStrip.isPng(data)) return PngChunkStrip.strip(data)
        val kind = kind(data) ?: return null
        val image = ImageCompressEngine.decode(data) ?: return null
        val out = when (kind) {
            Kind.Jpeg -> ImageCompressEngine.encode(image, ImageCompressFormat.Jpeg, QUALITY, scratch)
            Kind.WebP -> ImageCompressEngine.encode(image, ImageCompressFormat.WebP, QUALITY, scratch)
            Kind.WebPLossless -> ByteArrayOutputStream().use { stream ->
                image.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, stream).takeIf { it }?.let { stream.toByteArray() }
            }
            Kind.Heic -> if (ImageCompressFormat.Heic in ImageCompressFormat.available) {
                ImageCompressEngine.encode(image, ImageCompressFormat.Heic, QUALITY, scratch)
            } else {
                null
            }
        } ?: return null
        return MetadataStripResult(out, MetadataStripEngine.removed(scan))
    }
}
