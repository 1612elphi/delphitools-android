package com.delphi.delphitools.tools.calculators

import com.delphi.delphitools.tools.calculators.UnitConverterEngine.format
import com.delphi.delphitools.tools.calculators.UnitConverterEngine.formula
import com.delphi.delphitools.tools.calculators.UnitConverterEngine.tickLabel
import com.delphi.delphitools.tools.calculators.UnitConverterEngine.weights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitConverterEngineTest {
    private val celsius = UnitScale("C", "Celsius", 1.0, 273.15)
    private val fahrenheit = UnitScale("F", "Fahrenheit", 0.55555555555556, 255.37222222222427)
    private val kelvin = UnitScale("K", "Kelvin", 1.0)
    private val km = UnitScale("km", "Kilometres", 1000.0)
    private val mi = UnitScale("mi", "Miles", 1609.34)

    @Test
    fun formats() {
        assertEquals("0.6214", format(0.6214))
        assertEquals("1", format(1.0))
        assertEquals("0", format(0.0))
        assertEquals("-40", format(-40.0))
        assertEquals("0.001", format(0.001))
        assertEquals("6.2×10⁻⁴", format(0.00062))
        assertEquals("6.2137×10⁻⁷", format(6.2137e-7))
        assertEquals("1.5×10¹²", format(1.5e12))
        assertEquals("1×10⁻⁴", format(9.99999e-5))
    }

    @Test
    fun tickLabels() {
        assertEquals("0.001", tickLabel(0.001))
        assertEquals("2000000", tickLabel(2_000_000.0))
        assertEquals("0", tickLabel(-0.0))
        assertEquals("3×10¹²", tickLabel(3e12))
    }

    @Test
    fun formulas() {
        assertEquals("°C × 1.8 + 32", formula(celsius, fahrenheit))
        assertEquals("(°F − 32) ÷ 1.8", formula(fahrenheit, celsius))
        assertEquals("°C + 273.15", formula(celsius, kelvin))
        assertEquals("K − 273.15", formula(kelvin, celsius))
    }

    @Test
    fun poses() {
        val tape = UnitConverterEngine.pose(UnitInstrument.Tape, 5.0, km, mi)
        assertEquals(0.0, tape.lo, 0.0)
        assertEquals(10_000.0, tape.span, 1e-6)
        assertEquals(5000.0, tape.value, 1e-6)
        val warm = UnitConverterEngine.pose(UnitInstrument.Thermometer, 20.0, celsius, fahrenheit)
        assertEquals(263.15, warm.lo, 1e-6)
        assertEquals(50.0, warm.span, 1e-6)
    }

    @Test
    fun weightsPerBlock() {
        assertEquals(UnitWeights(1.0, 2, 0.5), weights(2.5))
        assertEquals(UnitWeights(1000.0, 5, 0.0), weights(5000.0))
        assertEquals(UnitWeights(0.1, 3, 0.0), weights(0.3))
        assertEquals(UnitWeights(10.0, 1, 0.0), weights(9.999))
        assertNull(weights(0.0))
    }

    @Test
    fun graduations() {
        val ticks = UnitConverterEngine.ticks(km, 0.0, 10_000.0, 400f, 40f) { it.length * 6f }
        assertEquals("0", ticks.first().label)
        assertEquals("10", ticks.last().label)
        assertEquals(11, ticks.count { it.isMajor })
    }

    @Test
    fun nudgeStopsAtFloor() {
        assertEquals(2.0, UnitConverterEngine.nudge(1.0, 1, 0.0), 0.0)
        assertEquals(0.6, UnitConverterEngine.nudge(0.5, 1, 0.0), 1e-12)
        assertEquals(0.0, UnitConverterEngine.nudge(0.0, -1, 0.0), 0.0)
        assertTrue(UnitConverterEngine.nudge(-273.0, -1, -273.15) >= -273.15)
    }
}
