package com.delphi.delphitools.tools.images

import java.util.Locale
import kotlin.math.roundToInt

/** dt-core Base64Image's strings and dt-ios Base64Mosaic's arithmetic. */
object Base64ImageEngine {
    // web tool's ceiling
    const val MAX_BYTES = 5 * 1024 * 1024
    private const val PREVIEW_CHARS = 480

    fun dataUri(mime: String, raw: String) = "data:$mime;base64,$raw"

    fun uriBytes(mime: String, raw: String) = "data:$mime;base64,".length + raw.length

    /** The head of the data URI; the whole string stalls text layout. */
    fun preview(mime: String, raw: String): String {
        val head = "data:$mime;base64,${raw.take(PREVIEW_CHARS)}"
        return if (raw.length > PREVIEW_CHARS) "$head…" else head
    }

    /** [bytes] as a figure and its unit, in binary steps. */
    fun figure(bytes: Int): Pair<String, String> = when {
        bytes < 1024 -> "$bytes" to "B"
        bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f", bytes / 1024.0) to "KB"
        else -> String.format(Locale.ROOT, "%.1f", bytes / 1_048_576.0) to "MB"
    }

    /** Character cells that a picture covers in a [columns] by [rows] wall: aspect kept, centred, as x, y, columns, rows. */
    fun cover(columns: Int, rows: Int, cellWidth: Float, cellHeight: Float, width: Float, height: Float): List<Int> {
        if (width <= 0f || height <= 0f) return listOf(0, 0, 0, 0)
        val scale = minOf(columns * cellWidth / width, rows * cellHeight / height)
        val wide = (width * scale / cellWidth).roundToInt().coerceIn(1, columns)
        val tall = (height * scale / cellHeight).roundToInt().coerceIn(1, rows)
        return listOf((columns - wide) / 2, (rows - tall) / 2, wide, tall)
    }
}
