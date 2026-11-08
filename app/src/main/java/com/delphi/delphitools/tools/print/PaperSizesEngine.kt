package com.delphi.delphitools.tools.print

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/** Swift's rounded(): half away from zero. */
private fun Double.rounded() = if (this >= 0) floor(this + 0.5) else ceil(this - 0.5)

private fun fixed(value: Double, places: Int) = String.format(Locale.ROOT, "%.${places}f", value)

data class PaperSize(val id: String, val name: String, val widthMM: Double, val heightMM: Double) {
    val widthInches get() = widthMM / 25.4
    val heightInches get() = heightMM / 25.4
    val widthPoints get() = widthInches * 72.0
    val heightPoints get() = heightInches * 72.0

    val mmString get() = "${fmtMM(widthMM)} × ${fmtMM(heightMM)} mm"
    val pointsString get() = "${fmtMM(widthPoints)} × ${fmtMM(heightPoints)} pt"

    private fun fmtMM(v: Double) = if (v % 1.0 == 0.0) fixed(v, 0) else fixed(v, 1)
}

data class PaperSeries(val id: String, val name: String, val sizes: List<PaperSize>)

data class PaperPick(val series: String, val size: PaperSize) {
    val key get() = "$series/${size.id}"

    companion object {
        val all = PaperSizeDatabase.allSeries.flatMap { series -> series.sizes.map { PaperPick(series.id, it) } }

        fun named(id: String) = all.firstOrNull { it.size.id.lowercase() == id.lowercase() }
    }
}

enum class PaperUnit(val label: String) {
    Mm("mm"),
    Inches("in"),
    Fraction("in ½"),
    Points("pt"),
    Pixels("px");

    fun text(size: PaperSize, dpi: Double): String = when (this) {
        Mm -> size.mmString
        Inches -> "${decimal(size.widthInches)} × ${decimal(size.heightInches)} in"
        Fraction -> {
            val (width, widthExact) = eighths(size.widthInches)
            val (height, heightExact) = eighths(size.heightInches)
            (if (widthExact && heightExact) "" else "≈ ") + "$width × $height″"
        }
        Points -> size.pointsString
        Pixels -> "${pixels(size.widthMM, dpi)} × ${pixels(size.heightMM, dpi)} px"
    }

    companion object {
        val dpis = listOf(72.0, 150.0, 300.0, 600.0)

        fun pixels(mm: Double, dpi: Double) = (mm / 25.4 * dpi).rounded().toInt()

        fun decimal(value: Double) = fixed(value, 2).trimEnd('0').trimEnd('.')

        private val glyphs = listOf("", "⅛", "¼", "⅜", "½", "⅝", "¾", "⅞")

        /** Nearest eighth as a vulgar fraction; exact: within 0.01 in. */
        fun eighths(inches: Double): Pair<String, Boolean> {
            val rounded = (inches * 8).rounded() / 8
            val whole = rounded.toInt()
            val glyph = glyphs[((rounded - whole) * 8).rounded().toInt() % 8]
            val text = if (whole == 0 && glyph.isNotEmpty()) glyph else "$whole$glyph"
            return text to (abs(rounded - inches) < 0.01)
        }
    }
}

sealed interface PaperQuery {
    data object None : PaperQuery

    data class Name(val text: String) : PaperQuery

    /** Millimetres. */
    data class Dimensions(val width: Double, val height: Double, val dpi: Double?) : PaperQuery

    fun matches(pick: PaperPick): Boolean {
        if (this !is Name) return true
        return pick.size.name.lowercase().contains(text) || pick.size.id.lowercase().contains(text) ||
            pick.series.lowercase().contains(text)
    }

    data class Match(val pick: PaperPick, val dw: Double, val dh: Double)

    companion object {
        private val pixelPattern = Regex("""^(\d+)\s*[x×]\s*(\d+)\s*@?\s*(\d+)\s*dpi$""")
        private val sizePattern = Regex("""^([\d.,]+)\s*[x×]\s*([\d.,]+)\s*(mm|in|"|″)?$""")

        /** Small bare numbers are inches. */
        fun parse(text: String): PaperQuery {
            val query = text.trim(' ', '\t').lowercase()
            if (query.isEmpty()) return None
            pixelPattern.matchEntire(query)?.let { match ->
                val (w, h, dpi) = match.destructured
                val dots = dpi.toDouble()
                if (dots > 0) return Dimensions(w.toDouble() / dots * 25.4, h.toDouble() / dots * 25.4, dots)
            }
            sizePattern.matchEntire(query)?.let { match ->
                val w = match.groupValues[1].replace(',', '.').toDoubleOrNull()
                val h = match.groupValues[2].replace(',', '.').toDoubleOrNull()
                if (w != null && h != null) {
                    val unit = match.groupValues[3]
                    val inches = unit == "in" || unit == "\"" || unit == "″" || (unit.isEmpty() && w < 50 && h < 50)
                    return if (inches) Dimensions(w * 25.4, h * 25.4, null) else Dimensions(w, h, null)
                }
            }
            return Name(query)
        }

        /** Nearest by corner distance, either way up. */
        fun closest(width: Double, height: Double, limit: Int = 5): List<Match> =
            PaperPick.all.map { pick ->
                val (w, h) = pick.size.widthMM to pick.size.heightMM
                val upright = hypot(w - width, h - height)
                val turned = hypot(w - height, h - width)
                if (turned < upright) turned to Match(pick, w - height, h - width) else upright to Match(pick, w - width, h - height)
            }
                .sortedBy { it.first }
                .take(limit)
                .map { it.second }
    }
}

enum class PaperArrangement { Stacked, SideBySide, AboveBelow }

object PaperGeometry {
    /** Never scaled past A4. */
    val reference = Size(210f, 297f)
    const val GAP = 20f

    /** ISO sizes round to whole millimetres, so a ratio within 0.5% of a half step snaps to it. */
    fun ratio(value: Double): String {
        val nominal = (value * 2).rounded() / 2
        return PaperUnit.decimal(if (abs(value - nominal) < nominal * 0.005) nominal else value)
    }

    /** The sheets at one scale, the group centred in [area]; stacked sheets share their bottom-leading corner. */
    fun frames(sizes: List<Size>, arrangement: PaperArrangement, area: Rect, flipped: Boolean): List<Rect> {
        if (sizes.isEmpty()) return emptyList()
        val apart = if (sizes.size == 2) arrangement else PaperArrangement.Stacked
        val gap = if (apart == PaperArrangement.Stacked) 0f else GAP
        val across = if (apart == PaperArrangement.SideBySide) gap else 0f
        val down = if (apart == PaperArrangement.AboveBelow) gap else 0f
        val span = when (apart) {
            PaperArrangement.Stacked -> Size(sizes.maxOf { it.width }, sizes.maxOf { it.height })
            PaperArrangement.SideBySide -> Size(sizes.sumOf { it.width.toDouble() }.toFloat(), sizes.maxOf { it.height })
            PaperArrangement.AboveBelow -> Size(sizes.maxOf { it.width }, sizes.sumOf { it.height.toDouble() }.toFloat())
        }
        val floor = Size(maxOf(span.width, reference.width), maxOf(span.height, reference.height))
        val scale = maxOf(0f, minOf((area.width - across) / floor.width, (area.height - down) / floor.height))
        val group = Size(span.width * scale + across, span.height * scale + down)
        val left = area.center.x - group.width / 2
        val bottom = area.center.y + group.height / 2
        var x = left
        var top = bottom - group.height
        // rtl: first on the right
        val ordered = if (apart == PaperArrangement.SideBySide && flipped) sizes.reversed() else sizes
        val frames = ordered.map { size ->
            val w = size.width * scale
            val h = size.height * scale
            val leading = if (flipped) left + group.width - w else left
            when (apart) {
                PaperArrangement.Stacked -> Rect(leading, bottom - h, leading + w, bottom)
                PaperArrangement.SideBySide -> Rect(x, bottom - h, x + w, bottom).also { x += w + gap }
                // first on top
                PaperArrangement.AboveBelow -> Rect(leading, top, leading + w, top + h).also { top += h + gap }
            }
        }
        return if (apart == PaperArrangement.SideBySide && flipped) frames.reversed() else frames
    }
}

object PaperSizeDatabase {
    val allSeries = listOf(
        PaperSeries("iso-a", "ISO A", listOf(
            PaperSize("4A0", "4A0", 1682.0, 2378.0),
            PaperSize("2A0", "2A0", 1189.0, 1682.0),
            PaperSize("A0", "A0", 841.0, 1189.0),
            PaperSize("A0+", "A0+", 914.0, 1292.0),
            PaperSize("A1", "A1", 594.0, 841.0),
            PaperSize("A1+", "A1+", 609.0, 914.0),
            PaperSize("A2", "A2", 420.0, 594.0),
            PaperSize("A3", "A3", 297.0, 420.0),
            PaperSize("A3+", "A3+", 329.0, 483.0),
            PaperSize("A4", "A4", 210.0, 297.0),
            PaperSize("A5", "A5", 148.0, 210.0),
            PaperSize("A6", "A6", 105.0, 148.0),
            PaperSize("A7", "A7", 74.0, 105.0),
            PaperSize("A8", "A8", 52.0, 74.0),
            PaperSize("A9", "A9", 37.0, 52.0),
            PaperSize("A10", "A10", 26.0, 37.0),
        )),
        PaperSeries("iso-b", "ISO B", listOf(
            PaperSize("B0", "B0", 1000.0, 1414.0),
            PaperSize("B1", "B1", 707.0, 1000.0),
            PaperSize("B2", "B2", 500.0, 707.0),
            PaperSize("B3", "B3", 353.0, 500.0),
            PaperSize("B4", "B4", 250.0, 353.0),
            PaperSize("B5", "B5", 176.0, 250.0),
            PaperSize("B6", "B6", 125.0, 176.0),
            PaperSize("B7", "B7", 88.0, 125.0),
            PaperSize("B8", "B8", 62.0, 88.0),
            PaperSize("B9", "B9", 44.0, 62.0),
            PaperSize("B10", "B10", 31.0, 44.0),
        )),
        PaperSeries("iso-c", "ISO C", listOf(
            PaperSize("C0", "C0", 917.0, 1297.0),
            PaperSize("C1", "C1", 648.0, 917.0),
            PaperSize("C2", "C2", 458.0, 648.0),
            PaperSize("C3", "C3", 324.0, 458.0),
            PaperSize("C4", "C4", 229.0, 324.0),
            PaperSize("C5", "C5", 162.0, 229.0),
            PaperSize("C6", "C6", 114.0, 162.0),
            PaperSize("C7", "C7", 81.0, 114.0),
            PaperSize("C8", "C8", 57.0, 81.0),
        )),
        PaperSeries("ra", "RA", listOf(
            PaperSize("RA0", "RA0", 860.0, 1220.0),
            PaperSize("RA1", "RA1", 610.0, 860.0),
            PaperSize("RA2", "RA2", 430.0, 610.0),
            PaperSize("RA3", "RA3", 305.0, 430.0),
            PaperSize("RA4", "RA4", 215.0, 305.0),
        )),
        PaperSeries("sra", "SRA", listOf(
            PaperSize("SRA0", "SRA0", 900.0, 1280.0),
            PaperSize("SRA1", "SRA1", 640.0, 900.0),
            PaperSize("SRA1+", "SRA1+", 660.0, 920.0),
            PaperSize("SRA2", "SRA2", 450.0, 640.0),
            PaperSize("SRA2+", "SRA2+", 480.0, 650.0),
            PaperSize("SRA3", "SRA3", 320.0, 450.0),
            PaperSize("SRA3+", "SRA3+", 320.0, 460.0),
            PaperSize("SRA4", "SRA4", 225.0, 320.0),
        )),
        PaperSeries("us-classic", "US Classic", listOf(
            PaperSize("letter", "Letter", 215.9, 279.4),
            PaperSize("legal", "Legal", 215.9, 355.6),
            PaperSize("tabloid", "Tabloid", 279.4, 431.8),
            PaperSize("ledger", "Ledger", 431.8, 279.4),
            PaperSize("junior-legal", "Junior Legal", 127.0, 203.2),
            PaperSize("half-letter", "Half Letter", 139.7, 215.9),
            PaperSize("gov-letter", "Gov Letter", 203.2, 266.7),
            PaperSize("gov-legal", "Gov Legal", 215.9, 330.2),
        )),
        PaperSeries("us-ansi", "US ANSI", listOf(
            PaperSize("ansi-a", "ANSI A", 216.0, 279.0),
            PaperSize("ansi-b", "ANSI B", 279.0, 432.0),
            PaperSize("ansi-c", "ANSI C", 432.0, 559.0),
            PaperSize("ansi-d", "ANSI D", 559.0, 864.0),
            PaperSize("ansi-e", "ANSI E", 864.0, 1118.0),
        )),
        PaperSeries("us-arch", "US Architectural", listOf(
            PaperSize("arch-a", "Arch A", 229.0, 305.0),
            PaperSize("arch-b", "Arch B", 305.0, 457.0),
            PaperSize("arch-c", "Arch C", 457.0, 610.0),
            PaperSize("arch-d", "Arch D", 610.0, 914.0),
            PaperSize("arch-e", "Arch E", 914.0, 1219.0),
            PaperSize("arch-e1", "Arch E1", 762.0, 1067.0),
        )),
        PaperSeries("japanese", "Japanese JIS", listOf(
            PaperSize("jb0", "JIS B0", 1030.0, 1456.0),
            PaperSize("jb1", "JIS B1", 728.0, 1030.0),
            PaperSize("jb2", "JIS B2", 515.0, 728.0),
            PaperSize("jb3", "JIS B3", 364.0, 515.0),
            PaperSize("jb4", "JIS B4", 257.0, 364.0),
            PaperSize("jb5", "JIS B5", 182.0, 257.0),
            PaperSize("jb6", "JIS B6", 128.0, 182.0),
            PaperSize("shiroku-ban-4", "Shiroku ban 4", 264.0, 379.0),
            PaperSize("shiroku-ban-5", "Shiroku ban 5", 189.0, 262.0),
            PaperSize("kiku-4", "Kiku 4", 227.0, 306.0),
            PaperSize("kiku-5", "Kiku 5", 151.0, 227.0),
        )),
        PaperSeries("chinese", "Chinese", listOf(
            PaperSize("d0", "D0", 764.0, 1064.0),
            PaperSize("d1", "D1", 532.0, 760.0),
            PaperSize("d2", "D2", 380.0, 532.0),
            PaperSize("d3", "D3", 266.0, 380.0),
            PaperSize("d4", "D4", 190.0, 266.0),
            PaperSize("d5", "D5", 133.0, 190.0),
            PaperSize("d6", "D6", 95.0, 133.0),
        )),
        PaperSeries("swedish", "Swedish SIS", listOf(
            PaperSize("sis-d0", "SIS D0", 1091.0, 1542.0),
            PaperSize("sis-d1", "SIS D1", 771.0, 1091.0),
            PaperSize("sis-d2", "SIS D2", 545.0, 771.0),
            PaperSize("sis-d3", "SIS D3", 386.0, 545.0),
            PaperSize("sis-d4", "SIS D4", 273.0, 386.0),
            PaperSize("sis-e0", "SIS E0", 878.0, 1242.0),
            PaperSize("sis-e1", "SIS E1", 621.0, 878.0),
            PaperSize("sis-e2", "SIS E2", 439.0, 621.0),
            PaperSize("sis-e3", "SIS E3", 310.0, 439.0),
            PaperSize("sis-e4", "SIS E4", 220.0, 310.0),
        )),
        PaperSeries("french", "French Traditional", listOf(
            PaperSize("cloche", "Cloche", 300.0, 400.0),
            PaperSize("pot-ecolier", "Pot, écolier", 310.0, 400.0),
            PaperSize("telliere", "Tellière", 340.0, 440.0),
            PaperSize("couronne-ecriture", "Couronne écriture", 360.0, 360.0),
            PaperSize("couronne-edition", "Couronne édition", 370.0, 470.0),
            PaperSize("raisin", "Raisin", 500.0, 650.0),
            PaperSize("double-raisin", "Double Raisin", 650.0, 1000.0),
            PaperSize("jesus", "Jésus", 560.0, 760.0),
            PaperSize("soleil", "Soleil", 600.0, 800.0),
            PaperSize("colombier-affiche", "Colombier affiche", 600.0, 800.0),
            PaperSize("grand-aigle", "Grand Aigle", 750.0, 1050.0),
            PaperSize("univers", "Univers", 1000.0, 1130.0),
        )),
        PaperSeries("imperial", "Imperial", listOf(
            PaperSize("antiquarian", "Antiquarian", 787.0, 1346.0),
            PaperSize("atlas", "Atlas", 660.0, 864.0),
            PaperSize("broadsheet", "Broadsheet", 457.0, 610.0),
            PaperSize("crown", "Crown", 381.0, 508.0),
            PaperSize("demy", "Demy", 445.0, 572.0),
            PaperSize("double-demy", "Double Demy", 572.0, 902.0),
            PaperSize("elephant", "Elephant", 584.0, 711.0),
            PaperSize("emperor", "Emperor", 1219.0, 1829.0),
            PaperSize("foolscap", "Foolscap", 343.0, 432.0),
            PaperSize("imperial-p", "Imperial", 559.0, 762.0),
            PaperSize("medium", "Medium", 470.0, 584.0),
            PaperSize("royal", "Royal", 508.0, 635.0),
        )),
    )
}
