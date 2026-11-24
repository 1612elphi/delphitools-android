package com.delphi.delphitools.tools.pdf

/** dt-ios ImageToPDFEngine: where each image lands on its page. */
object ImageToPdfEngine {
    data class Options(
        /** Null matches each image. */
        val paperMm: PdfSize? = null,
        val orientation: PdfPagesEngine.PageOrientation = PdfPagesEngine.PageOrientation.Auto,
        val fit: PdfPagesEngine.FitMode = PdfPagesEngine.FitMode.Contain,
        val marginMm: Double = 0.0,
    )

    data class Layout(val page: PdfSize, val area: PdfRect, val placement: PdfRect)

    /** PDF points, bottom-left origin; for the preview and the build. */
    fun layout(image: PdfSize, options: Options): Layout? {
        val margin = options.marginMm * PdfPagesEngine.MM_TO_POINTS
        val page = PdfPagesEngine.pageSetup(options.paperMm, options.orientation, image, margin)
        val inner = PdfSize(page.width - margin * 2, page.height - margin * 2)
        if (image.width <= 0 || image.height <= 0 || inner.width <= 0 || inner.height <= 0) return null
        val mode = if (options.paperMm == null) PdfPagesEngine.FitMode.Contain else options.fit
        val placement = PdfPagesEngine.fitPlacement(image, inner, mode)
        return Layout(page, PdfRect(margin, margin, inner.width, inner.height), placement.offset(margin, margin))
    }

    /** Finder order: case ignored, digit runs compared as numbers. */
    val nameOrder: Comparator<String> = Comparator { a, b ->
        val x = a.lowercase()
        val y = b.lowercase()
        var i = 0
        var j = 0
        while (i < x.length && j < y.length) {
            if (x[i].isDigit() && y[j].isDigit()) {
                val si = i
                val sj = j
                while (i < x.length && x[i].isDigit()) i++
                while (j < y.length && y[j].isDigit()) j++
                val n = x.substring(si, i).trimStart('0')
                val m = y.substring(sj, j).trimStart('0')
                if (n.length != m.length) return@Comparator n.length - m.length
                val order = n.compareTo(m)
                if (order != 0) return@Comparator order
            } else {
                if (x[i] != y[j]) return@Comparator x[i] - y[j]
                i++
                j++
            }
        }
        (x.length - i) - (y.length - j)
    }

    fun <T> sortedByName(items: List<T>, ascending: Boolean, name: (T) -> String): List<T> =
        items.sortedWith(compareBy(if (ascending) nameOrder else nameOrder.reversed(), name))
}
