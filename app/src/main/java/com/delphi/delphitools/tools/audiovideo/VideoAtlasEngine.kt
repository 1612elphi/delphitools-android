package com.delphi.delphitools.tools.audiovideo

import kotlin.math.max
import kotlin.math.min

/** BT.709 luma and chroma of a colour with components 0 to 1; chroma runs -0.5 to 0.5. */
data class ScopeComponents(val luma: Double, val cb: Double, val cr: Double)

/** dt-ios VideoAtlasModel, AVInfoProbe formatting and AVEditScopes maths, with no device in it. */
object VideoAtlasEngine {
    // MediaFormat colour constants
    private val standards = mapOf(1 to "BT.709", 2 to "BT.601 PAL", 4 to "BT.601 NTSC", 6 to "BT.2020")
    private val transfers = mapOf(1 to "Linear", 3 to "SDR", 6 to "PQ", 7 to "HLG")
    private val ranges = mapOf(1 to "Full range", 2 to "Limited range")

    private val textNames = mapOf(
        "text/vtt" to "WebVTT", "application/x-subrip" to "SubRip", "text/3gpp-tt" to "3GPP Timed Text",
        "text/cea-608" to "CEA-608", "text/cea-708" to "CEA-708", "application/ttml+xml" to "TTML",
    )

    fun isText(mime: String): Boolean = mime.startsWith("text/") || mime in textNames

    fun codecName(mime: String): String = textNames[mime] ?: AVEditEngine.codecName(mime)

    fun colour(standard: Int?, transfer: Int?, range: Int?): String? =
        listOfNotNull(standards[standard], transfers[transfer], ranges[range]).takeIf { it.isNotEmpty() }?.joinToString(" · ")

    /** The probe's sections as a plain-text report. */
    fun report(sections: List<AVEditProbeSection>): String =
        sections.joinToString("\n\n") { section ->
            (listOf(section.title) + section.tiles.map { "  ${it.label}: ${it.value}" }).joinToString("\n")
        }

    /** The tracks after General. */
    fun streamSummary(sections: List<AVEditProbeSection>): String? {
        val count = sections.size - 1
        return when {
            count <= 0 -> null
            count == 1 -> "1 stream"
            else -> "$count streams"
        }
    }

    // dt-ios AVEditScopes constants
    const val SAMPLE_WIDTH = 160
    const val SAMPLE_HEIGHT = 90
    const val CELLS = 128
    private const val GAIN = 56
    const val REACH = 0.8

    fun components(red: Double, green: Double, blue: Double): ScopeComponents {
        val luma = 0.2126 * red + 0.7152 * green + 0.0722 * blue
        return ScopeComponents(luma, (blue - luma) / 1.8556, (red - luma) / 1.5748)
    }

    /**
     * Plots [pixels] (ARGB, [width] × [height]) into [vector] (CELLS × CELLS, Cb across and Cr up)
     * and [wave] (SAMPLE_WIDTH × CELLS, column across and luma up); each cell holds 0 to 255.
     */
    fun plot(pixels: IntArray, width: Int, height: Int, vector: IntArray, wave: IntArray) {
        vector.fill(0)
        wave.fill(0)
        val columns = min(width, SAMPLE_WIDTH)
        val rows = min(height, SAMPLE_HEIGHT)
        if (columns <= 0 || rows <= 0) return
        val stepX = width / columns
        val stepY = height / rows
        val half = CELLS / 2.0
        for (y in 0 until rows) {
            for (x in 0 until columns) {
                val pixel = pixels[y * stepY * width + x * stepX]
                val part = components((pixel shr 16 and 0xFF) / 255.0, (pixel shr 8 and 0xFF) / 255.0, (pixel and 0xFF) / 255.0)
                val across = (half + part.cb * 2 * half * REACH).toInt()
                val up = (half - part.cr * 2 * half * REACH).toInt()
                if (across in 0 until CELLS && up in 0 until CELLS) {
                    val cell = up * CELLS + across
                    vector[cell] = min(255, vector[cell] + GAIN)
                }
                val level = ((1 - part.luma) * (CELLS - 1)).toInt().coerceIn(0, CELLS - 1)
                val cell = level * SAMPLE_WIDTH + x
                wave[cell] = min(255, wave[cell] + GAIN)
            }
        }
    }

    /** Each vectorscope cell's own hue at a fixed luma, as opaque RGB. */
    val vectorInk: IntArray by lazy {
        val half = CELLS / 2.0
        IntArray(CELLS * CELLS) { index ->
            val up = index / CELLS
            val across = index % CELLS
            val cb = (across - half) / (2 * half * REACH)
            val cr = (half - up) / (2 * half * REACH)
            val luma = 0.62
            val parts = listOf(luma + 1.5748 * cr, luma - 0.1873 * cb - 0.4681 * cr, luma + 1.8556 * cb)
                .map { (max(0.0, min(1.0, it)) * 255).toInt() }
            (parts[0] shl 16) or (parts[1] shl 8) or parts[2]
        }
    }

    /** ARGB pixels of a cell grid: each cell's value is its alpha; without [ink] the ink is near white. */
    fun image(cells: IntArray, ink: IntArray?, out: IntArray) {
        for (cell in cells.indices) {
            val alpha = cells[cell]
            out[cell] = if (alpha == 0) 0 else (alpha shl 24) or (ink?.get(cell) ?: 0xF0FFF0)
        }
    }
}
