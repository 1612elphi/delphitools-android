package com.delphi.delphitools.tools.images

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

/** dt-ios ImageSplitEngine: a grid of whole-pixel tiles; the remainder on the trailing and bottom edges is left out. */
object ImageSplitEngine {
    data class Cell(val row: Int, val column: Int) {
        /** `row-column`, counted from 1, as the web tool names the files. */
        val label get() = "${row + 1}-${column + 1}"
    }

    fun tileSize(width: Int, height: Int, rows: Int, columns: Int) = IntSize(width / max(1, columns), height / max(1, rows))

    fun cells(rows: Int, columns: Int) = (0 until rows).flatMap { row -> (0 until columns).map { Cell(row, it) } }

    /** The cell's crop in image pixels, top-left origin. */
    fun rect(cell: Cell, width: Int, height: Int, rows: Int, columns: Int): IntRect {
        val size = tileSize(width, height, rows, columns)
        return IntRect(cell.column * size.width, cell.row * size.height, (cell.column + 1) * size.width, (cell.row + 1) * size.height)
    }

    /** Tile frames for a grid of [tile]-sized pixels in [area], seams included, centred. */
    fun frames(rows: Int, columns: Int, tile: Size, seam: Float, area: Rect): List<Rect> {
        if (rows <= 0 || columns <= 0 || tile.width <= 0f || tile.height <= 0f) return emptyList()
        val roomWidth = area.width - seam * (columns - 1)
        val roomHeight = area.height - seam * (rows - 1)
        val scale = max(0f, min(roomWidth / (tile.width * columns), roomHeight / (tile.height * rows)))
        val side = Size(tile.width * scale, tile.height * scale)
        val wholeWidth = side.width * columns + seam * (columns - 1)
        val wholeHeight = side.height * rows + seam * (rows - 1)
        val left = area.center.x - wholeWidth / 2
        val top = area.center.y - wholeHeight / 2
        return (0 until rows).flatMap { row ->
            (0 until columns).map { column ->
                val x = left + column * (side.width + seam)
                val y = top + row * (side.height + seam)
                Rect(x, y, x + side.width, y + side.height)
            }
        }
    }
}
