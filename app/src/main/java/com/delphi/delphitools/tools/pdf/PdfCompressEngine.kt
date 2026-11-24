package com.delphi.delphitools.tools.pdf

import java.io.OutputStream

/** dt-ios PDFCompressEngine, the parts that need no Android: the rewrite and the choice of images to re-encode. */
object PdfCompressEngine {
    sealed interface Mode {
        /** Rewrites the file; images untouched. */
        data object Keep : Mode

        /** Images re-encoded as JPEG, optionally downsampled; text and vectors kept. */
        data class Recompress(val downsample: Boolean) : Mode

        /** Every page becomes one JPEG; text stops being text. */
        data class Rasterise(val dpi: Double, val quality: Double) : Mode
    }

    /** Larger images are kept as they are; 40 MP is 160 MB as a bitmap. */
    const val MAX_IMAGE_PIXELS = 40_000_000L

    /** An image's samples to re-encode: a JPEG as stored, or 8-bit RGB rows. */
    class ImageSamples(val width: Int, val height: Int, val jpeg: ByteArray?, val rgb: ByteArray?)

    /**
     * The whole of [file] written anew into [out], unfiltered streams deflated. With [encode] each RGB image it accepts
     * comes back as a JPEG; a JPEG no smaller than the image as stored is dropped and the original kept.
     */
    fun rewrite(file: PdfFile, out: OutputStream, encode: ((ImageSamples) -> PdfPicture?)? = null, step: (Int) -> Unit = {}) {
        PdfOrganiserEngine.build(
            file.pages.indices.map { PdfOrganiserEngine.PageRef(file, it) },
            out,
            info = file,
            transform = { source, original, copied ->
                encode?.let { reencoded(source, original, copied, it) } ?: deflated(copied)
            },
            step = step,
        )
    }

    fun deflated(stream: PdfStream): PdfStream {
        if (stream.dict["Filter"] != null || stream.data.size < 64) return stream
        val packed = PdfFilters.deflate(stream.data)
        if (packed.size >= stream.data.size) return stream
        val dict = LinkedHashMap(stream.dict)
        dict.remove("DecodeParms")
        dict["Filter"] = PdfName("FlateDecode")
        return PdfStream(dict, packed)
    }

    /**
     * An image XObject's samples when it is 8-bit, three-component (DeviceRGB or a three-channel ICC profile),
     * plain (no decode array, colour key mask or matte) and stored as a JPEG or with Flate.
     * ponytail: grey, CMYK, indexed and 16-bit images are left as they are; widen when files full of them stay large
     */
    fun samples(file: PdfFile, stream: PdfStream): ImageSamples? {
        val dict = stream.dict
        if ((dict["Subtype"] as? PdfName)?.name != "Image" || file.resolve(dict["ImageMask"]) == true) return null
        if ((file.resolve(dict["BitsPerComponent"]) as? Number)?.toInt() != 8) return null
        if (dict["Decode"] != null || file.resolve(dict["Mask"]) is List<*> || dict["SMaskInData"] != null) return null
        // a matte needs the mask at the image's size
        if (file.dict(dict["SMask"])?.get("Matte") != null) return null
        if (!rgb(file, file.resolve(dict["ColorSpace"]))) return null
        val width = (file.resolve(dict["Width"]) as? Number)?.toInt() ?: return null
        val height = (file.resolve(dict["Height"]) as? Number)?.toInt() ?: return null
        if (width <= 0 || height <= 0 || width.toLong() * height > MAX_IMAGE_PIXELS) return null
        val bytes = width.toLong() * height * 3
        return when (PdfFilters.filters(dict)) {
            listOf("DCTDecode") -> ImageSamples(width, height, stream.data, null)
            // a predictor adds one byte a row
            listOf("FlateDecode") -> runCatching { PdfFilters.decode(stream, limit = bytes + height + 4096) }.getOrNull()
                ?.takeIf { it.size >= bytes }
                ?.let { ImageSamples(width, height, null, it) }
            else -> null
        }
    }

    private fun rgb(file: PdfFile, space: Any?): Boolean = when (space) {
        is PdfName -> space.name == "DeviceRGB"
        is List<*> -> (space.firstOrNull() as? PdfName)?.name == "ICCBased" &&
            (file.dict(space.getOrNull(1))?.get("N")?.let(file::resolve) as? Number)?.toInt() == 3
        else -> false
    }

    private fun reencoded(file: PdfFile, original: PdfStream, copied: PdfStream, encode: (ImageSamples) -> PdfPicture?): PdfStream? {
        val samples = samples(file, original) ?: return null
        val picture = encode(samples) ?: return null
        if (!picture.dct || picture.data.size >= original.data.size) return null
        val dict = LinkedHashMap(copied.dict)
        dict.remove("DecodeParms")
        dict["Filter"] = PdfName("DCTDecode")
        dict["Width"] = picture.width
        dict["Height"] = picture.height
        return PdfStream(dict, picture.data)
    }
}
