package com.delphi.delphitools.tools.typography

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.cos
import kotlin.math.exp

/** dt-ios PxToRemViewModel: the conversion and the number format the typography tools share. */
object PxToRemEngine {
    val commonPxValues = listOf(8.0, 10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 18.0, 20.0, 24.0, 28.0, 32.0, 36.0, 40.0, 48.0, 56.0, 64.0, 72.0, 80.0, 96.0)

    private val number = Regex("""[-+]?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?""")

    /** Decimal pads type a comma in many locales. */
    fun parse(text: String): Double? = text.replace(',', '.').takeIf(number::matches)?.toDouble()?.takeIf { it.isFinite() }

    /** At most four decimals, trailing zeros dropped: 0.5, 0.6875, 26.25. */
    fun format(value: Double): String {
        if (!value.isFinite()) return value.toString()
        val text = BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString()
        return if (text == "-0") "0" else text
    }

    /** Damped spring with one overshoot, 0 to 1: the specimens' settle easing. */
    fun settle(progress: Float): Float = if (progress >= 1f) 1f else 1f - exp(-7f * progress) * cos(9f * progress)

    fun convert(text: String, base: Double, remToPixel: Boolean): Double? {
        val value = parse(text)?.takeIf { it >= 0 && it.isFinite() } ?: return null
        return if (remToPixel) value * base else value / base
    }
}
