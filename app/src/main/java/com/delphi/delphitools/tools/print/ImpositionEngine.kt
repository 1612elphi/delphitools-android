package com.delphi.delphitools.tools.print

import com.delphi.delphitools.tools.pdf.PdfPagesEngine.MM_TO_POINTS

/** A page placed on one side of a sheet. Millimetres, top-left origin; [pageNumber] is 1-based, 0 blank. */
data class ImpositionPlacement(
    val pageNumber: Int,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    /** 0, 90, 180 or 270 */
    val rotation: Int,
    val side: SheetSide,
)

enum class SheetSide { Front, Back }

/** One physical sheet; [signature] is 0-based. */
data class SheetDefinition(val sheetNumber: Int, val front: List<ImpositionPlacement>, val back: List<ImpositionPlacement>, val signature: Int = 0)

data class ImpositionResult(
    val sheets: List<SheetDefinition>,
    /** Pages after padding to the layout's multiple. */
    val totalPages: Int,
    val pagesUsed: Int,
) {
    val totalSheets get() = sheets.size
    val blanksAdded get() = maxOf(0, totalPages - pagesUsed)
}

enum class ImpositionScaling { Fit, Fill, Actual }

enum class DuplexFlip(val id: String) { LongEdge("long-edge"), ShortEdge("short-edge") }

enum class ImpositionOrientation { Portrait, Landscape }

enum class ImpositionLayoutId(val id: String, val title: String, val useCase: String) {
    SaddleStitch("saddle-stitch", "2-up Saddle Stitch", "Booklets, magazines, zines"),
    PerfectBind("perfect-bind", "2-up Perfect Bind", "Paperback books, catalogues"),
    StepAndRepeat("step-and-repeat", "2-up Step & Repeat", "Flyers, postcards, business cards"),
    FourUpBooklet("four-up-booklet", "4-up Booklet", "Pocket booklets, pamphlets, menus"),
    GangRun("gang-run", "N-up Gang Run", "Stickers, labels, short-run items"),
    CustomNUp("custom-nup", "Custom N-up", "Proof sheets, thumbnails");

    /** Has fold geometry, so the duplex flip matters. */
    val supportsDuplex get() = this == SaddleStitch || this == PerfectBind || this == FourUpBooklet
    val supportsCreep get() = this == SaddleStitch || this == PerfectBind

    fun calculate(totalSourcePages: Int, config: ImpositionConfig): ImpositionResult {
        val result = when (this) {
            SaddleStitch -> ImpositionEngine.saddleStitch(totalSourcePages, config)
            PerfectBind -> ImpositionEngine.perfectBind(totalSourcePages, config)
            StepAndRepeat -> ImpositionEngine.stepAndRepeat(totalSourcePages, config)
            FourUpBooklet -> ImpositionEngine.fourUpBooklet(totalSourcePages, config)
            GangRun -> ImpositionEngine.gangRun(totalSourcePages, config)
            CustomNUp -> ImpositionEngine.customNUp(totalSourcePages, config)
        }
        // layouts assume book turn
        return if (supportsDuplex && config.turnsTopOverBottom) ImpositionEngine.turnBacks(result, config) else result
    }
}

data class ImpositionConfig(
    val layoutId: ImpositionLayoutId,
    val paperWidthMm: Double,
    val paperHeightMm: Double,
    val orientation: ImpositionOrientation,
    val marginMm: Double,
    val gutterMm: Double,
    val creepMm: Double,
    val scaling: ImpositionScaling,
    val cropMarks: Boolean,
    val nUp: Int,
    val customRows: Int,
    val customCols: Int,
    val duplexFlip: DuplexFlip,
    /** multiple of 4 */
    val signaturePages: Int = 16,
) {
    val sheetW get() = if (orientation == ImpositionOrientation.Landscape) maxOf(paperWidthMm, paperHeightMm) else minOf(paperWidthMm, paperHeightMm)
    val sheetH get() = if (orientation == ImpositionOrientation.Landscape) minOf(paperWidthMm, paperHeightMm) else maxOf(paperWidthMm, paperHeightMm)

    /** landscape long edge tumbles */
    val turnsTopOverBottom get() = if (duplexFlip == DuplexFlip.LongEdge) sheetW > sheetH else sheetW <= sheetH
}

/** dt-ios ImpositionEngine. */
object ImpositionEngine {
    private fun padToMultiple(n: Int, multiple: Int): Int {
        val rem = n % multiple
        return if (rem == 0) n else n + (multiple - rem)
    }

    private fun pageOrBlank(p: Int, source: Int) = if (p <= source) p else 0

    private fun buildGrid(
        pageNumbers: List<Int>,
        rows: Int,
        cols: Int,
        config: ImpositionConfig,
        side: SheetSide,
        rotations: List<Int>? = null,
    ): List<ImpositionPlacement> {
        val usableW = config.sheetW - config.marginMm * 2 - config.gutterMm * (cols - 1)
        val usableH = config.sheetH - config.marginMm * 2 - config.gutterMm * (rows - 1)
        val cellW = usableW / cols
        val cellH = usableH / rows
        return pageNumbers.mapIndexed { i, page ->
            val col = i % cols
            val row = i / cols
            ImpositionPlacement(
                page,
                config.marginMm + col * (cellW + config.gutterMm),
                config.marginMm + row * (cellH + config.gutterMm),
                cellW, cellH, rotations?.get(i) ?: 0, side,
            )
        }
    }

    fun turnBacks(result: ImpositionResult, config: ImpositionConfig): ImpositionResult {
        val (width, height) = config.sheetW to config.sheetH
        val sheets = result.sheets.map { sheet ->
            sheet.copy(
                back = sheet.back.map { p ->
                    p.copy(x = width - p.x - p.width, y = height - p.y - p.height, rotation = (p.rotation + 180) % 360)
                },
            )
        }
        return result.copy(sheets = sheets)
    }

    fun saddleStitch(totalSourcePages: Int, config: ImpositionConfig): ImpositionResult {
        val totalPages = padToMultiple(maxOf(totalSourcePages, 4), 4)
        return ImpositionResult(nestedSheets(totalPages, 0, 1, 0, totalSourcePages, config), totalPages, totalSourcePages)
    }

    /** [totalPages]: a multiple of 4. */
    private fun nestedSheets(totalPages: Int, base: Int, firstId: Int, signature: Int, source: Int, config: ImpositionConfig): List<SheetDefinition> {
        val numSheets = totalPages / 4
        val usableW = config.sheetW - config.marginMm * 2
        val usableH = config.sheetH - config.marginMm * 2
        val halfW = (usableW - config.gutterMm) / 2
        return (0 until numSheets).map { s ->
            val creep = minOf((numSheets - 1 - s) * config.creepMm, config.marginMm)
            val left = config.marginMm - creep
            val right = config.marginMm + halfW + config.gutterMm + creep
            fun place(page: Int, x: Double, side: SheetSide) =
                ImpositionPlacement(pageOrBlank(page, source), x, config.marginMm, halfW, usableH, 0, side)
            SheetDefinition(
                firstId + s,
                listOf(place(base + totalPages - 2 * s, left, SheetSide.Front), place(base + 2 * s + 1, right, SheetSide.Front)),
                listOf(place(base + 2 * s + 2, left, SheetSide.Back), place(base + totalPages - 2 * s - 1, right, SheetSide.Back)),
                signature,
            )
        }
    }

    fun perfectBind(totalSourcePages: Int, config: ImpositionConfig): ImpositionResult {
        val size = padToMultiple(maxOf(4, config.signaturePages), 4)
        val totalPages = padToMultiple(maxOf(totalSourcePages, 1), size)
        val sheets = ArrayList<SheetDefinition>()
        for (base in 0 until totalPages step size) {
            sheets += nestedSheets(size, base, sheets.size + 1, base / size, totalSourcePages, config)
        }
        return ImpositionResult(sheets, totalPages, totalSourcePages)
    }

    fun stepAndRepeat(totalSourcePages: Int, config: ImpositionConfig): ImpositionResult {
        val sourcePage = if (totalSourcePages >= 1) 1 else 0
        val backPage = if (totalSourcePages >= 2) 2 else sourcePage
        val sheet = SheetDefinition(
            1,
            buildGrid(listOf(sourcePage, sourcePage), 1, 2, config, SheetSide.Front),
            buildGrid(listOf(backPage, backPage), 1, 2, config, SheetSide.Back),
        )
        return ImpositionResult(listOf(sheet), 2, minOf(totalSourcePages, 2))
    }

    fun fourUpBooklet(totalSourcePages: Int, config: ImpositionConfig): ImpositionResult {
        val n = padToMultiple(maxOf(totalSourcePages, 8), 8)
        val rotations = listOf(180, 180, 0, 0)
        val sheets = (0 until n / 8).map { s ->
            val base = s * 4
            // right-angle fold, nested sheets
            val front = listOf(n - base - 3, base + 4, n - base, base + 1).map { pageOrBlank(it, totalSourcePages) }
            val back = listOf(base + 3, n - base - 2, base + 2, n - base - 1).map { pageOrBlank(it, totalSourcePages) }
            SheetDefinition(
                s + 1,
                buildGrid(front, 2, 2, config, SheetSide.Front, rotations),
                buildGrid(back, 2, 2, config, SheetSide.Back, rotations),
            )
        }
        return ImpositionResult(sheets, n, totalSourcePages)
    }

    private val gangRunGrids = mapOf(2 to (1 to 2), 4 to (2 to 2), 6 to (2 to 3), 8 to (2 to 4), 9 to (3 to 3))

    fun gangRun(totalSourcePages: Int, config: ImpositionConfig): ImpositionResult {
        val (rows, cols) = gangRunGrids[config.nUp] ?: (1 to 2)
        val count = maxOf(totalSourcePages, 1)
        val sheets = (0 until count).map { s ->
            val pages = List(rows * cols) { s + 1 }
            SheetDefinition(s + 1, buildGrid(pages, rows, cols, config, SheetSide.Front), buildGrid(pages, rows, cols, config, SheetSide.Back))
        }
        return ImpositionResult(sheets, count, count)
    }

    fun customNUp(totalSourcePages: Int, config: ImpositionConfig): ImpositionResult {
        val (rows, cols) = config.customRows to config.customCols
        val perSide = rows * cols
        val perSheet = perSide * 2
        val total = padToMultiple(maxOf(totalSourcePages, perSheet), perSheet)
        val sheets = (0 until total / perSheet).map { s ->
            val frontStart = s * perSheet + 1
            val backStart = frontStart + perSide
            SheetDefinition(
                s + 1,
                buildGrid((0 until perSide).map { pageOrBlank(frontStart + it, totalSourcePages) }, rows, cols, config, SheetSide.Front),
                buildGrid((0 until perSide).map { pageOrBlank(backStart + it, totalSourcePages) }, rows, cols, config, SheetSide.Back),
            )
        }
        return ImpositionResult(sheets, total, totalSourcePages)
    }

    data class OuterEdges(val left: Boolean, val right: Boolean, val top: Boolean, val bottom: Boolean)

    fun outerEdges(placements: List<ImpositionPlacement>): List<OuterEdges> {
        if (placements.isEmpty()) return emptyList()
        val eps = 0.5
        val minX = placements.minOf { it.x }
        val maxX = placements.maxOf { it.x + it.width }
        val minY = placements.minOf { it.y }
        val maxY = placements.maxOf { it.y + it.height }
        return placements.map { p ->
            OuterEdges(
                kotlin.math.abs(p.x - minX) < eps,
                kotlin.math.abs(p.x + p.width - maxX) < eps,
                kotlin.math.abs(p.y - minY) < eps,
                kotlin.math.abs(p.y + p.height - maxY) < eps,
            )
        }
    }

    /** Crop marks, 10 pt long, two at each outer edge of a cell; millimetres, top-left origin. */
    fun marks(placements: List<ImpositionPlacement>): List<SheetLine> {
        val length = 10 / MM_TO_POINTS
        return placements.zip(outerEdges(placements)).flatMap { (p, e) ->
            val (left, top, right, bottom) = listOf(p.x, p.y, p.x + p.width, p.y + p.height)
            buildList {
                if (e.left) addAll(listOf(SheetLine(left - length, top, left, top), SheetLine(left - length, bottom, left, bottom)))
                if (e.right) addAll(listOf(SheetLine(right, top, right + length, top), SheetLine(right, bottom, right + length, bottom)))
                if (e.top) addAll(listOf(SheetLine(left, top - length, left, top), SheetLine(right, top - length, right, top)))
                if (e.bottom) addAll(listOf(SheetLine(left, bottom, left, bottom + length), SheetLine(right, bottom, right, bottom + length)))
            }
        }
    }

    /** Fold lines the writer and the preview draw. */
    fun creases(layout: ImpositionLayoutId, width: Double, height: Double): List<SheetLine> {
        val across = SheetLine(width / 2, 0.0, width / 2, height)
        val down = SheetLine(0.0, height / 2, width, height / 2)
        return when (layout) {
            ImpositionLayoutId.SaddleStitch, ImpositionLayoutId.PerfectBind, ImpositionLayoutId.StepAndRepeat -> listOf(across)
            ImpositionLayoutId.FourUpBooklet -> listOf(across, down)
            ImpositionLayoutId.GangRun, ImpositionLayoutId.CustomNUp -> emptyList()
        }
    }

    /** dt-ios ImposerViewController.sheet: one side as a PrintSheet. */
    fun sheet(placements: List<ImpositionPlacement>, config: ImpositionConfig, isBack: Boolean, pageSizes: List<Pair<Double, Double>> = emptyList()): PrintSheet {
        val cells = placements.map { p ->
            val fit = when (config.scaling) {
                ImpositionScaling.Fit -> PrintSheet.Fit.Fit
                ImpositionScaling.Fill -> PrintSheet.Fit.Fill
                ImpositionScaling.Actual -> pageSizes.getOrNull(p.pageNumber - 1)
                    ?.let { PrintSheet.Fit.Actual(it.first / MM_TO_POINTS, it.second / MM_TO_POINTS) } ?: PrintSheet.Fit.Fit
            }
            PrintSheet.Cell(SheetRect(p.x, p.y, p.width, p.height), p.pageNumber, p.rotation, fit)
        }
        return PrintSheet(
            config.sheetW, config.sheetH, cells,
            creases = creases(config.layoutId, config.sheetW, config.sheetH).map { it to PrintSheet.Crease.Fold },
            marks = if (config.cropMarks) marks(placements) else emptyList(),
            isBack = isBack,
        )
    }

    data class Signature(val sheets: Int, val pages: IntRange)

    fun signatures(sheets: List<SheetDefinition>): List<Signature> {
        val groups = ArrayList<Signature>()
        for (sheet in sheets) {
            if (groups.size == sheet.signature + 1) {
                groups[sheet.signature] = groups[sheet.signature].copy(sheets = groups[sheet.signature].sheets + 1)
            } else {
                val start = (groups.lastOrNull()?.pages?.last ?: 0) + 1
                groups.add(Signature(1, start..start))
            }
            val group = groups[sheet.signature]
            groups[sheet.signature] = group.copy(pages = group.pages.first..(group.pages.first + group.sheets * 4 - 1))
        }
        return groups
    }
}

/** Millimetres, top-left origin. */
data class SheetRect(val x: Double, val y: Double, val width: Double, val height: Double) {
    val maxX get() = x + width
    val maxY get() = y + height
    val midX get() = x + width / 2
    val midY get() = y + height / 2
}

data class SheetLine(val x1: Double, val y1: Double, val x2: Double, val y2: Double)

/** dt-ios PrintSheet: one side of a sheet to draw, in millimetres. */
data class PrintSheet(
    val width: Double,
    val height: Double,
    val cells: List<Cell>,
    val creases: List<Pair<SheetLine, Crease>> = emptyList(),
    val cuts: List<SheetLine> = emptyList(),
    val marks: List<SheetLine> = emptyList(),
    val isBack: Boolean = false,
) {
    sealed interface Fit {
        data object Fit : PrintSheet.Fit
        data object Fill : PrintSheet.Fit

        /** The page's own size in millimetres. */
        data class Actual(val width: Double, val height: Double) : PrintSheet.Fit
    }

    /** [page] 1-based, 0 blank; [turn] clockwise degrees. */
    data class Cell(val frame: SheetRect, val page: Int, val turn: Int = 0, val fit: Fit = Fit.Fit)

    enum class Crease { Fold, Valley, Mountain }

    /** The paper fitted and centred in [area], on whole units. */
    fun paperRect(area: SheetRect): SheetRect {
        if (width <= 0 || height <= 0 || area.width <= 0 || area.height <= 0) return SheetRect(0.0, 0.0, 0.0, 0.0)
        val scale = minOf(area.width / width, area.height / height)
        val w = width * scale
        val h = height * scale
        val x = area.midX - w / 2
        val y = area.midY - h / 2
        val x0 = kotlin.math.floor(x)
        val y0 = kotlin.math.floor(y)
        return SheetRect(x0, y0, kotlin.math.ceil(x + w) - x0, kotlin.math.ceil(y + h) - y0)
    }
}
