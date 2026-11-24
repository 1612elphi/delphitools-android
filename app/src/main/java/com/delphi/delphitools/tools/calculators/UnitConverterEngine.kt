package com.delphi.delphitools.tools.calculators

import com.delphi.delphitools.tools.typography.PxToRemEngine
import com.delphi.delphitools.tools.typography.TypoCalcEngine
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/** A unit as a linear map to its dimension's base unit: base = value × coefficient + constant (Foundation's UnitConverterLinear). */
class UnitScale(val id: String, val label: String, val coefficient: Double, val constant: Double = 0.0) {
    val symbol = when (id) {
        "C" -> "°C"
        "F" -> "°F"
        else -> id
    }

    fun base(value: Double) = value * coefficient + constant
    fun value(base: Double) = (base - constant) / coefficient
}

enum class UnitInstrument { Tape, Thermometer, Speedometer, Manometer, Balance, Jug, Squares }

/** In base units. [span] is the scale's length; for squares it is the area of one result unit. */
data class UnitPose(val value: Double = 0.0, val lo: Double = 0.0, val span: Double = 1.0)

/** Weights for one side of the pan: [full] blocks of [denomination] units, then one cut to its [part]. */
data class UnitWeights(val denomination: Double, val full: Int, val part: Double)

class UnitTick(val offset: Float, val isMajor: Boolean, val label: String?)

/** dt-ios UnitConverterViewModel and UnitConverterPicture's numbers; coefficients are Foundation's. */
object UnitConverterEngine {
    val categories = listOf("Length", "Weight", "Temperature", "Data", "Speed", "Volume", "Area", "Time", "Pressure")

    /** The pair a category opens on, by unit id: from, to. */
    val pairs = mapOf(
        "Length" to ("km" to "mi"), "Weight" to ("kg" to "lb"), "Temperature" to ("C" to "F"), "Data" to ("GB" to "MB"),
        "Speed" to ("km/h" to "mph"), "Volume" to ("L" to "gal"), "Area" to ("m²" to "ft²"), "Time" to ("hr" to "min"),
        "Pressure" to ("bar" to "psi"),
    )

    // ponytail: stones as 6.35029318 kg; the Foundation coefficient on iOS is unverified
    fun units(category: String, ppi: Int): List<UnitScale> = when (category) {
        "Length" -> listOf(
            UnitScale("m", "Metres", 1.0),
            UnitScale("km", "Kilometres", 1000.0),
            UnitScale("cm", "Centimetres", 0.01),
            UnitScale("mm", "Millimetres", 0.001),
            UnitScale("mi", "Miles", 1609.34),
            UnitScale("yd", "Yards", 0.9144),
            UnitScale("ft", "Feet", 0.3048),
            UnitScale("in", "Inches", 0.0254),
            UnitScale("px", "Pixels", 0.0254 / ppi),
        )
        "Weight" -> listOf(
            UnitScale("kg", "Kilograms", 1.0),
            UnitScale("g", "Grams", 0.001),
            UnitScale("mg", "Milligrams", 0.000001),
            UnitScale("lb", "Pounds", 0.453592),
            UnitScale("oz", "Ounces", 0.0283495),
            UnitScale("st", "Stones", 6.35029318),
            UnitScale("t", "Tonnes", 1000.0),
        )
        "Temperature" -> listOf(
            UnitScale("C", "Celsius", 1.0, 273.15),
            UnitScale("F", "Fahrenheit", 0.55555555555556, 255.37222222222427),
            UnitScale("K", "Kelvin", 1.0),
        )
        "Data" -> listOf(
            UnitScale("B", "Bytes", 1.0),
            UnitScale("KB", "Kilobytes", 1e3),
            UnitScale("MB", "Megabytes", 1e6),
            UnitScale("GB", "Gigabytes", 1e9),
            UnitScale("TB", "Terabytes", 1e12),
            UnitScale("bit", "Bits", 0.125),
            UnitScale("Kbit", "Kilobits", 125.0),
            UnitScale("Mbit", "Megabits", 125_000.0),
        )
        "Speed" -> listOf(
            UnitScale("m/s", "Metres/sec", 1.0),
            UnitScale("km/h", "Kilometres/hr", 0.277778),
            UnitScale("mph", "Miles/hr", 0.44704),
            UnitScale("kn", "Knots", 0.514444),
        )
        "Volume" -> listOf(
            UnitScale("L", "Litres", 1.0),
            UnitScale("mL", "Millilitres", 0.001),
            UnitScale("gal", "Gallons (US)", 3.78541),
            UnitScale("qt", "Quarts (US)", 0.946353),
            UnitScale("pt", "Pints (US)", 0.473176),
            UnitScale("cup", "Cups (US)", 0.24),
            UnitScale("fl oz", "Fluid Ounces (US)", 0.0295735),
            UnitScale("tbsp", "Tablespoons", 0.015),
            UnitScale("tsp", "Teaspoons", 0.005),
        )
        "Area" -> listOf(
            UnitScale("m²", "Square Metres", 1.0),
            UnitScale("km²", "Square Kilometres", 1e6),
            UnitScale("ft²", "Square Feet", 0.092903),
            UnitScale("mi²", "Square Miles", 2.59e6),
            UnitScale("ha", "Hectares", 10_000.0),
            UnitScale("ac", "Acres", 4046.86),
        )
        "Time" -> listOf(
            UnitScale("s", "Seconds", 1.0),
            UnitScale("ms", "Milliseconds", 0.001),
            UnitScale("min", "Minutes", 60.0),
            UnitScale("hr", "Hours", 3600.0),
        )
        "Pressure" -> listOf(
            UnitScale("Pa", "Pascals", 1.0),
            UnitScale("hPa", "Hectopascals", 100.0),
            UnitScale("bar", "Bars", 100_000.0),
            UnitScale("mbar", "Millibars", 100.0),
            UnitScale("psi", "PSI", 6894.76),
            UnitScale("inHg", "Inches of Mercury", 3386.39),
            UnitScale("mmHg", "mmHg", 133.322),
        )
        else -> emptyList()
    }

    fun instrument(category: String) = when (category) {
        "Temperature" -> UnitInstrument.Thermometer
        "Weight" -> UnitInstrument.Balance
        "Speed" -> UnitInstrument.Speedometer
        "Pressure" -> UnitInstrument.Manometer
        "Volume" -> UnitInstrument.Jug
        "Area" -> UnitInstrument.Squares
        else -> UnitInstrument.Tape
    }

    fun convert(value: Double, from: UnitScale, to: UnitScale) = to.value(from.base(value))

    private fun rounded(value: Double) = if (value < 0) -(-value).roundToLong().toDouble() else value.roundToLong().toDouble()

    /** Whole steps, tenths below one; never below [floor]. */
    fun nudge(current: Double, direction: Int, floor: Double): Double {
        val step = if (current != 0.0 && abs(current) < 1) 0.1 else 1.0
        return maxOf(floor, (rounded(current / step) + direction) * step)
    }

    private const val SUPERSCRIPTS = "⁰¹²³⁴⁵⁶⁷⁸⁹"

    /** Up to four decimals; a value that needs more, and one from a billion up, is a mantissa times a power of ten. */
    fun format(value: Double): String {
        if (!value.isFinite()) return "—"
        val magnitude = abs(value)
        if (magnitude == 0.0) return "0"
        val decimal = PxToRemEngine.format(value)
        // 0.00062 would lose digits
        if (magnitude < 1e9 && (magnitude >= 0.01 || magnitude >= 1e-4 && abs((decimal.toDoubleOrNull() ?: 0.0) - value) <= magnitude * 1e-9)) return decimal
        var exponent = floor(log10(magnitude)).toInt()
        var mantissa = value / 10.0.pow(exponent)
        // 9.99996 rounds to 10
        if (abs(rounded(mantissa * 1e4)) >= 1e5) {
            mantissa /= 10
            exponent += 1
        }
        val digits = exponent.toString().map { if (it == '-') '⁻' else SUPERSCRIPTS[it - '0'] }.joinToString("")
        return PxToRemEngine.format(mantissa) + "×10" + digits
    }

    /** The right side of `to = from × m + c`, or of `to = (from − a) ÷ k` when the slope is below one. */
    fun formula(from: UnitScale, to: UnitScale): String {
        val offset = to.value(from.base(0.0))
        val slope = to.value(from.base(1.0)) - offset
        fun number(value: Double) = PxToRemEngine.format(abs(value))
        fun signed(value: Double) = if (value < 0) " − " + number(value) else " + " + number(value)
        fun shows(value: Double) = abs(value) >= 0.00005
        if (abs(slope) >= 1 - 1e-9) {
            val scaled = if (abs(slope - 1) < 1e-9) from.symbol else from.symbol + " × " + number(slope)
            return scaled + if (shows(offset)) signed(offset) else ""
        }
        val shift = -offset / slope
        val inner = if (shows(shift)) "(" + from.symbol + signed(-shift) + ")" else from.symbol
        return inner + " ÷ " + number(1 / slope)
    }

    /** Where the scale runs: the value sits between 36 % and 90 % of a 1-2-5 length; a thermometer shows a window around it. */
    fun pose(instrument: UnitInstrument, value: Double, from: UnitScale, to: UnitScale): UnitPose = when (instrument) {
        UnitInstrument.Thermometer -> {
            val span = TypoCalcEngine.niceStep(maxOf(abs(value) * 1.2, 50.0))
            val grid = span / 5
            val lo = floor((value - span * 0.55) / grid) * grid
            UnitPose(from.base(value), from.base(lo), from.base(lo + span) - from.base(lo))
        }
        UnitInstrument.Squares -> UnitPose(from.base(maxOf(0.0, value)), 0.0, to.base(1.0))
        else -> {
            val shown = maxOf(0.0, value)
            val span = if (shown > 0) TypoCalcEngine.niceStep(shown / 0.9) else 1.0
            UnitPose(from.base(shown), 0.0, from.base(span))
        }
    }

    /** Scale numbers: plain decimals from 0.0001 up to ten million, which are shorter there than a power of ten. */
    fun tickLabel(value: Double): String {
        val magnitude = abs(value)
        if (!(magnitude == 0.0 || magnitude >= 1e-4 && magnitude < 1e7)) return format(value)
        if (magnitude == 0.0) return "0"
        return BigDecimal(value).round(MathContext(6, RoundingMode.HALF_EVEN)).stripTrailingZeros().toPlainString()
    }

    /**
     * Graduations of [scale] over lo ... lo + span (base units) laid along [length]: 1-2-5 majors at least [spacing]
     * apart and wide enough for their labels as [width] measures them, minors where they are 4 apart.
     */
    fun ticks(scale: UnitScale, lo: Double, span: Double, length: Float, spacing: Float, width: (String) -> Float): List<UnitTick> {
        val a = scale.value(lo)
        val b = scale.value(lo + span)
        if (length <= 0 || b <= a || !(b - a).isFinite()) return emptyList()
        val perUnit = length / (b - a)
        var major = TypoCalcEngine.niceStep(spacing / perUnit)
        for (attempt in 0 until 6) {
            val widest = listOf(a, b).maxOf { width(tickLabel(rounded(it / major) * major)) }
            if (major * perUnit >= widest + 10) break
            major = TypoCalcEngine.niceStep(major * 1.5)
        }
        val minor = major / TypoCalcEngine.subdivisions(major)
        val step = if (minor * perUnit >= 4) minor else major
        val first = kotlin.math.ceil(a / step - 1e-9)
        val last = floor(b / step + 1e-9)
        if (last < first || last - first >= 1000) return emptyList()
        return (first.toLong()..last.toLong()).map { index ->
            val value = index * step
            val isMajor = abs(rounded(value / major) * major - value) < step * 1e-6
            UnitTick(((value - a) * perUnit).toFloat(), isMajor, if (isMajor) tickLabel(value) else null)
        }
    }

    /** A power of ten per block, so a stack holds one to ten of them. */
    fun weights(value: Double): UnitWeights? {
        if (value <= 0 || !value.isFinite()) return null
        val denomination = 10.0.pow(floor(log10(value)))
        var count = value / denomination
        // 9.99999 becomes next size
        if (count > 9.995) return UnitWeights(denomination * 10, 1, 0.0)
        count = rounded(count * 100) / 100
        val full = floor(count).toInt()
        val part = count - full
        return UnitWeights(denomination, full, if (part < 0.02) 0.0 else part)
    }
}
