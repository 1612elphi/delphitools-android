package com.delphi.delphitools.tools.pdf

import java.io.OutputStream

/** An image for a PDF page. [data] is a JPEG when [dct], else zlib-compressed 8-bit samples; [mask] likewise for alpha. */
class PdfPicture(val width: Int, val height: Int, val data: ByteArray, val dct: Boolean, val grey: Boolean = false, val mask: ByteArray? = null)

/** A PDF of one picture per page, written page by page. */
class PdfImageDocument(out: OutputStream) {
    private val writer = PdfWriter(out)
    private val tree = writer.reserve()
    private val kids = ArrayList<PdfRef>()

    /** [clip] and [placement] in PDF points, bottom-left origin. */
    fun add(page: PdfSize, placement: PdfRect, picture: PdfPicture, clip: PdfRect? = null) {
        val image: PdfDict = linkedMapOf(
            "Type" to PdfName("XObject"),
            "Subtype" to PdfName("Image"),
            "Width" to picture.width,
            "Height" to picture.height,
            "ColorSpace" to PdfName(if (picture.grey) "DeviceGray" else "DeviceRGB"),
            "BitsPerComponent" to 8,
            "Filter" to PdfName(if (picture.dct) "DCTDecode" else "FlateDecode"),
        )
        picture.mask?.let { mask ->
            image["SMask"] = writer.add(
                PdfStream(
                    linkedMapOf(
                        "Type" to PdfName("XObject"), "Subtype" to PdfName("Image"), "Width" to picture.width,
                        "Height" to picture.height, "ColorSpace" to PdfName("DeviceGray"), "BitsPerComponent" to 8,
                        "Filter" to PdfName("FlateDecode"),
                    ),
                    mask,
                ),
            )
        }
        val imageRef = writer.add(PdfStream(image, picture.data))
        val r = PdfSerialiser::real
        val clipping = clip?.let { "${r(it.x)} ${r(it.y)} ${r(it.width)} ${r(it.height)} re W n\n" } ?: ""
        val content = "q\n$clipping${r(placement.width)} 0 0 ${r(placement.height)} ${r(placement.x)} ${r(placement.y)} cm\n/Im0 Do\nQ\n"
        val contents = writer.add(PdfStream(linkedMapOf(), content.toByteArray(Charsets.ISO_8859_1)))
        kids.add(
            writer.add(
                linkedMapOf(
                    "Type" to PdfName("Page"),
                    "Parent" to PdfRef(tree),
                    "MediaBox" to listOf(0, 0, page.width, page.height),
                    "Resources" to linkedMapOf("XObject" to linkedMapOf("Im0" to imageRef)),
                    "Contents" to contents,
                ),
            ),
        )
    }

    fun finish() {
        writer.write(tree, linkedMapOf("Type" to PdfName("Pages"), "Kids" to kids, "Count" to kids.size))
        writer.finish(writer.add(linkedMapOf("Type" to PdfName("Catalog"), "Pages" to PdfRef(tree))))
    }
}
