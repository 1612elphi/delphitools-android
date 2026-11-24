package com.delphi.delphitools.tools.print

import com.delphi.delphitools.tools.pdf.PdfName
import com.delphi.delphitools.tools.pdf.PdfPagesEngine
import com.delphi.delphitools.tools.pdf.PdfPicture
import com.delphi.delphitools.tools.pdf.PdfRef
import com.delphi.delphitools.tools.pdf.PdfSerialiser
import com.delphi.delphitools.tools.pdf.PdfStream
import com.delphi.delphitools.tools.pdf.PdfWriter
import java.io.OutputStream

/** One page in a grid cell on one side of the sheet; [pageNumber] is the 1-based slot. */
data class PagePlacement(val pageNumber: Int, val col: Int, val row: Int, val rotation: Int)

/** A fold crease; [pos] is a fraction across the sheet (x for vertical, y for horizontal). */
data class FoldLine(val vertical: Boolean, val pos: Double, val valley: Boolean)

/** Fractions of the sheet. */
data class CutLine(val x1: Double, val y1: Double, val x2: Double, val y2: Double)

data class ZineSide(val back: Boolean, val placements: List<PagePlacement>)

data class ZineFoldLayout(
    val cols: Int,
    val rows: Int,
    /** Slots to fill, across all sides. */
    val pageCount: Int,
    val sides: List<ZineSide>,
    val foldLines: List<FoldLine>,
    val cutLines: List<CutLine>,
    val instructions: List<String>,
)

enum class ZineFold(val title: String, val tagline: String) {
    MiniEight("8-page mini-zine", "Classic slit & fold"),
    Accordion("Accordion / concertina", "Zig-zag fold-out");

    /** The accordion alone has a panel count, a double-sided and a split variant. */
    val configurable get() = this == Accordion
}

val accordionPanelOptions = listOf(4, 6, 8)

/** dt-ios ZineFoldBuilder and the parts of ZineImposerViewController that need no view. */
object ZineImposerEngine {
    /**
     *   [ p5↻ ][ p4↻ ][ p3↻ ][ p2↻ ]   top row, rotated 180°
     *   [ p6  ][ p7  ][ p8  ][ p1  ]   bottom row, upright
     * Folded and cut along the central slit (middle two columns), it reads 1 to 8.
     */
    fun miniEight() = ZineFoldLayout(
        cols = 4,
        rows = 2,
        pageCount = 8,
        sides = listOf(
            ZineSide(
                false,
                listOf(
                    PagePlacement(5, 0, 0, 180), PagePlacement(4, 1, 0, 180), PagePlacement(3, 2, 0, 180), PagePlacement(2, 3, 0, 180),
                    PagePlacement(6, 0, 1, 0), PagePlacement(7, 1, 1, 0), PagePlacement(8, 2, 1, 0), PagePlacement(1, 3, 1, 0),
                ),
            ),
        ),
        foldLines = listOf(FoldLine(true, 0.25, true), FoldLine(true, 0.5, false), FoldLine(true, 0.75, true), FoldLine(false, 0.5, false)),
        cutLines = listOf(CutLine(0.25, 0.5, 0.75, 0.5)),
        instructions = listOf(
            "Print the single page (single-sided, landscape).",
            "Fold in half lengthwise (top edge to bottom edge).",
            "Unfold, then fold in half widthwise (right edge to left).",
            "Fold in half widthwise again.",
            "Unfold completely — you'll see 8 panels.",
            "Cut along the red line (centre horizontal, middle two columns only).",
            "Fold lengthwise again, push ends together to form the booklet.",
        ),
    )

    /**
     * N equal vertical panels on a landscape sheet. Double-sided: the back carries N+1 to 2N left to right, for a
     * short-edge flip. Split: the panels repeat in two lanes with one horizontal cut, the slot count unchanged.
     */
    fun accordion(panels: Int, doubleSided: Boolean, split: Boolean): ZineFoldLayout {
        val n = maxOf(2, panels)
        val rows = if (split) 2 else 1
        fun side(back: Boolean) = ZineSide(
            back,
            (0 until rows).flatMap { r -> (0 until n).map { col -> PagePlacement(if (back) n + 1 + col else col + 1, col, r, 0) } },
        )
        val sides = if (doubleSided) listOf(side(false), side(true)) else listOf(side(false))
        val folds = (1 until n).map { FoldLine(true, it.toDouble() / n, it % 2 == 1) }
        val cuts = if (split) listOf(CutLine(0.0, 0.5, 1.0, 0.5)) else emptyList()
        val foldStep = "Fold ${if (split) "each strip" else "the sheet"} into $n equal vertical panels " +
            "with an alternating zig-zag (concertina) fold — first crease toward you, next away, and so on."
        val cutStep = "Cut the sheet in half along the horizontal red line — two identical strips."
        val instructions = if (doubleSided) {
            listOf("Print both pages double-sided. In the print dialog choose Two-Sided and “Flip on short edge” so the back lines up correctly.") +
                (if (split) listOf(cutStep) else emptyList()) +
                listOf(
                    foldStep,
                    "Crease each fold firmly.",
                    "Read the front panels left to right (pages 1–$n), then flip the strip over its right edge and continue on the back " +
                        "(pages ${n + 1}–${n * 2})." + if (split) " You now have two identical copies." else "",
                )
        } else {
            listOf("Print the single page (single-sided, landscape).") +
                (if (split) listOf(cutStep) else emptyList()) +
                listOf(
                    foldStep,
                    "Crease each fold firmly.",
                    "The panels read left to right (page 1 is the leftmost panel). Unfold " +
                        if (split) "to view as a fold-out — you now have two identical copies." else "completely to view it as a fold-out panorama.",
                )
        }
        return ZineFoldLayout(n, rows, if (doubleSided) n * 2 else n, sides, folds, cuts, instructions)
    }

    fun build(fold: ZineFold, panels: Int, doubleSided: Boolean, split: Boolean) =
        if (fold == ZineFold.Accordion) accordion(panels, doubleSided, split) else miniEight()

    /** Empty slots from [start] to the end, then from the beginning. */
    fun fillOrder(filled: List<Boolean>, start: Int): List<Int> {
        if (filled.isEmpty()) return emptyList()
        val begin = start.coerceIn(0, filled.size - 1)
        return ((begin until filled.size) + (0 until begin)).filter { !filled[it] }
    }

    /** dt-ios ZineImposerViewController.sheet; [fills] by 0-based slot. */
    fun sheet(side: ZineSide, layout: ZineFoldLayout, width: Double, height: Double, fills: (Int) -> Boolean = { false }): PrintSheet {
        val cellW = width / layout.cols
        val cellH = height / layout.rows
        val cells = side.placements.map {
            PrintSheet.Cell(
                SheetRect(it.col * cellW, it.row * cellH, cellW, cellH), it.pageNumber, it.rotation,
                if (fills(it.pageNumber - 1)) PrintSheet.Fit.Fill else PrintSheet.Fit.Fit,
            )
        }
        val creases = layout.foldLines.map { fold ->
            val line = if (fold.vertical) SheetLine(fold.pos * width, 0.0, fold.pos * width, height) else SheetLine(0.0, fold.pos * height, width, fold.pos * height)
            line to if (fold.valley) PrintSheet.Crease.Valley else PrintSheet.Crease.Mountain
        }
        // front only, like pdf
        val cuts = if (!side.back) layout.cutLines.map { SheetLine(it.x1 * width, it.y1 * height, it.x2 * width, it.y2 * height) } else emptyList()
        return PrintSheet(width, height, cells, creases, cuts, isBack = side.back)
    }

    /** An image for a slot and whether it fills its cell. */
    class Slot(val picture: PdfPicture, val fill: Boolean)

    private fun r(value: Double) = PdfSerialiser.real(value)

    /** One page per printed side, front then back; slots by 0-based page. */
    fun write(layout: ZineFoldLayout, slots: List<Slot?>, sheetWidthMm: Double, sheetHeightMm: Double, out: OutputStream) {
        val w = sheetWidthMm * PdfPagesEngine.MM_TO_POINTS
        val h = sheetHeightMm * PdfPagesEngine.MM_TO_POINTS
        val cellW = w / layout.cols
        val cellH = h / layout.rows
        val writer = PdfWriter(out)
        val tree = writer.reserve()
        val images = HashMap<Int, PdfRef>()
        fun image(index: Int, picture: PdfPicture) = images.getOrPut(index) {
            val dict: com.delphi.delphitools.tools.pdf.PdfDict = linkedMapOf(
                "Type" to PdfName("XObject"), "Subtype" to PdfName("Image"), "Width" to picture.width, "Height" to picture.height,
                "ColorSpace" to PdfName(if (picture.grey) "DeviceGray" else "DeviceRGB"), "BitsPerComponent" to 8,
                "Filter" to PdfName(if (picture.dct) "DCTDecode" else "FlateDecode"),
            )
            picture.mask?.let { mask ->
                dict["SMask"] = writer.add(
                    PdfStream(
                        linkedMapOf(
                            "Type" to PdfName("XObject"), "Subtype" to PdfName("Image"), "Width" to picture.width, "Height" to picture.height,
                            "ColorSpace" to PdfName("DeviceGray"), "BitsPerComponent" to 8, "Filter" to PdfName("FlateDecode"),
                        ),
                        mask,
                    ),
                )
            }
            writer.add(PdfStream(dict, picture.data))
        }
        val states = writer.add(
            linkedMapOf(
                "Fold" to linkedMapOf("Type" to PdfName("ExtGState"), "CA" to 0.5),
                "Cut" to linkedMapOf("Type" to PdfName("ExtGState"), "CA" to 0.7),
            ),
        )
        val kids = ArrayList<PdfRef>()
        for (side in layout.sides) {
            val ops = StringBuilder()
            val names = LinkedHashMap<String, Any?>()
            for (placement in side.placements) {
                val index = placement.pageNumber - 1
                val slot = slots.getOrNull(index) ?: continue
                val picture = slot.picture
                val left = placement.col * cellW
                // pdf origin bottom-left
                val bottom = h - (placement.row + 1) * cellH
                val scaleX = cellW / picture.width
                val scaleY = cellH / picture.height
                val scale = if (slot.fill) maxOf(scaleX, scaleY) else minOf(scaleX, scaleY)
                val dw = picture.width * scale
                val dh = picture.height * scale
                val name = "Im$index"
                names[name] = image(index, picture)
                ops.append("q ${r(left)} ${r(bottom)} ${r(cellW)} ${r(cellH)} re W n\n")
                ops.append("1 0 0 1 ${r(left + cellW / 2)} ${r(bottom + cellH / 2)} cm\n")
                if (placement.rotation == 180) ops.append("-1 0 0 -1 0 0 cm\n")
                ops.append("${r(dw)} 0 0 ${r(dh)} ${r(-dw / 2)} ${r(-dh / 2)} cm /$name Do Q\n")
            }
            ops.append("q /Fold gs 0.5 G 0.5 w [4 4] 0 d\n")
            for (fold in layout.foldLines) {
                if (fold.vertical) ops.append("${r(fold.pos * w)} 0 m ${r(fold.pos * w)} ${r(h)} l\n") else ops.append("0 ${r(h - fold.pos * h)} m ${r(w)} ${r(h - fold.pos * h)} l\n")
            }
            ops.append("S Q\n")
            // cuts are a single-sided concept, drawn on the front only
            if (!side.back && layout.cutLines.isNotEmpty()) {
                ops.append("q /Cut gs 1 0 0 RG 1 w\n")
                for (cut in layout.cutLines) ops.append("${r(cut.x1 * w)} ${r(h - cut.y1 * h)} m ${r(cut.x2 * w)} ${r(h - cut.y2 * h)} l\n")
                ops.append("S Q\n")
            }
            val content = writer.add(PdfStream(linkedMapOf(), ops.toString().toByteArray(Charsets.ISO_8859_1)))
            kids.add(
                writer.add(
                    linkedMapOf(
                        "Type" to PdfName("Page"), "Parent" to PdfRef(tree), "MediaBox" to listOf(0, 0, w, h),
                        "Resources" to linkedMapOf("XObject" to names, "ExtGState" to states), "Contents" to content,
                    ),
                ),
            )
        }
        writer.write(tree, linkedMapOf("Type" to PdfName("Pages"), "Kids" to kids, "Count" to kids.size))
        writer.finish(writer.add(linkedMapOf("Type" to PdfName("Catalog"), "Pages" to PdfRef(tree))))
    }
}
