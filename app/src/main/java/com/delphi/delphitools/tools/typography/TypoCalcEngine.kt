package com.delphi.delphitools.tools.typography

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/** dt-ios TypoCalcViewModel.TypoUnit; [ppi] is pixels per inch, which CSS fixes at 96. */
enum class TypoUnit(val symbol: String, val title: String, val description: String, val step: Double) {
    Px("px", "Pixels", "Screen pixels (96 per inch)", 1.0),
    Pt("pt", "Points", "Print points (72 per inch)", 1.0),
    Pc("pc", "Picas", "12 points per pica", 0.5),
    Ag("ag", "Agates", "14 agates per inch (US newspapers)", 1.0),
    Cc("cc", "Ciceros", "European unit (≈4.512mm)", 0.5),
    In("in", "Inches", "Imperial inch", 0.125),
    Mm("mm", "Millimetres", "Metric millimetre", 1.0),
    Cm("cm", "Centimetres", "Metric centimetre", 0.1),
    Em("em", "Em", "Relative to parent font-size", 0.125),
    Rem("rem", "Rem", "Relative to root font-size", 0.125);

    fun toPx(value: Double, base: Double, ppi: Double = 96.0): Double = when (this) {
        Px -> value
        Pt -> value * (ppi / 72.0)
        Pc -> value * 12.0 * (ppi / 72.0)
        Ag -> value * (ppi / 14.0)
        Cc -> value * 4.512 * (ppi / 25.4)
        In -> value * ppi
        Mm -> value * (ppi / 25.4)
        Cm -> value * (ppi / 2.54)
        Em, Rem -> value * base
    }

    fun fromPx(px: Double, base: Double, ppi: Double = 96.0): Double = when (this) {
        Px -> px
        Pt -> px * (72.0 / ppi)
        Pc -> px / 12.0 * (72.0 / ppi)
        Ag -> px * (14.0 / ppi)
        Cc -> px / (4.512 * (ppi / 25.4))
        In -> px / ppi
        Mm -> px * (25.4 / ppi)
        Cm -> px * (2.54 / ppi)
        Em, Rem -> px / base
    }
}

object TypoCalcEngine {
    val baseRange = 1.0..200.0
    val ppiRange = 1.0..2400.0

    /** Print at 72 pt per inch, CSS, and two print scans. */
    val ppiPresets = listOf(72.0, 96.0, 150.0, 300.0)

    /** Gauge scales, top to bottom; cm is mm by ten and rem reads as em. */
    val gaugeScales = listOf(TypoUnit.Px, TypoUnit.Pt, TypoUnit.Pc, TypoUnit.Ag, TypoUnit.Cc, TypoUnit.Mm, TypoUnit.In, TypoUnit.Em)

    fun gaugeUnit(unit: TypoUnit) = when (unit) {
        TypoUnit.Cm -> TypoUnit.Mm
        TypoUnit.Rem -> TypoUnit.Em
        else -> unit
    }

    /** One step from [value] in [unit], landing on the step grid, never below zero. */
    fun nudge(value: Double, unit: TypoUnit, direction: Int): Double =
        maxOf(0.0, ((value / unit.step).roundHalfAway() + direction) * unit.step)

    private fun Double.roundHalfAway() = if (this < 0) -(-this).roundToLong().toDouble() else roundToLong().toDouble()

    /** Smallest of 1, 2, 5 times a power of ten that is at least [minimum]. */
    fun niceStep(minimum: Double): Double {
        if (minimum <= 0 || !minimum.isFinite()) return 1.0
        val power = 10.0.pow(floor(log10(minimum)))
        for (mantissa in listOf(1.0, 2.0, 5.0, 10.0)) {
            if (mantissa * power >= minimum * (1 - 1e-9)) return mantissa * power
        }
        return 10 * power
    }

    /** Minor ticks per major: fifths of 1 and 5, quarters of 2. */
    fun subdivisions(major: Double): Double {
        val mantissa = Math.round(major / 10.0.pow(floor(log10(major))))
        return if (mantissa == 2L) 4.0 else 5.0
    }

    /** Gauge length in px: the value sits between 36 % and 90 % of it. */
    fun span(px: Double): Double = niceStep(maxOf(px, 1.0) / 0.9)
}
