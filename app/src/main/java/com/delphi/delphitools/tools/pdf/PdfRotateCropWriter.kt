package com.delphi.delphitools.tools.pdf

/** dt-ios PDFRotateCropWriter: the page's /Rotate and /CropBox changed in an incremental update; nothing is redrawn. */
object PdfRotateCropWriter {
    data class Geometry(val box: PdfRect, val rotation: Int)

    /** [rotation] is 90 degree steps on top of the page's own; [crop] is page space, null keeps the whole page. */
    data class Edit(val rotation: Int = 0, val crop: PdfRect? = null)

    fun geometry(file: PdfFile): List<Geometry> = file.pages.map { Geometry(PdfRect.of(file.cropBox(it)), file.rotation(it)) }

    fun apply(file: PdfFile, edits: List<Edit>): ByteArray {
        val changes = LinkedHashMap<PdfRef, Any?>()
        edits.forEachIndexed { index, edit ->
            val page = file.pages.getOrNull(index) ?: return@forEachIndexed
            val crop = edit.crop?.let { PdfCropEngine.intersectBox(it, PdfRect.of(file.cropBox(page))) }
            if (edit.rotation == 0 && crop == null) return@forEachIndexed
            val dict = LinkedHashMap(page.dict)
            if (edit.rotation != 0) dict["Rotate"] = PdfPagesEngine.normaliseRotation(file.rotation(page) + edit.rotation)
            if (crop != null) dict["CropBox"] = listOf(crop.x, crop.y, crop.maxX, crop.maxY)
            changes[page.ref] = dict
        }
        return file.update(changes)
    }
}
