package com.delphi.delphitools.tools.calculators

import com.delphi.delphitools.canon.math.CanonMathExpression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan

class GraphCalcEngineTest {
    private val p = GraphPlot

    @Test
    fun expression() {
        assertEquals(9.0, MathExpression("x^2").evaluate(3.0), 0.0)
        assertEquals(7.0, MathExpression("2x + 1").evaluate(3.0), 0.0)
        assertEquals(2 * PI, MathExpression("2pi").evaluate(0.0), 1e-12)
        assertEquals(2.0, MathExpression("log(100)").evaluate(0.0), 1e-12)
        assertEquals(3.0, MathExpression("log(8, 2)").evaluate(0.0), 1e-12)
        assertEquals(-2.0, MathExpression("nthroot(-8, 3)").evaluate(0.0), 1e-12)
        assertEquals(-1.0, MathExpression("mod(-7, 3)").evaluate(0.0), 0.0)
        assertEquals(-3.0, MathExpression("round(-2.5)").evaluate(0.0), 0.0)
        assertEquals(512.0, MathExpression("2^3^2").evaluate(0.0), 0.0)
        assertTrue(MathExpression("y + 1").evaluate(0.0).isNaN())
        assertFalse(MathExpression("sin(x").isValid)
        assertFalse(MathExpression("min(1)").isValid)
    }

    @Test
    fun syntax() {
        // GraphSyntax.selfCheck: round trip evaluates the same
        for (source in listOf("x^2", "2x + 1", "sin(x)*3", "sqrt(x)", "log(x)", "ln(x)", "abs(x-1)", "exp(x)", "tan(x)", "x^2/4", "1/x", "2pi x", "e^x", "asin(x/2)+floor(x)")) {
            val typed = CanonMathExpression(GraphSyntax.typed(source)).text
            val back = GraphSyntax.plotted(typed)
            assertTrue("$source did not come back from $typed", back != null)
            val before = MathExpression(source)
            val after = MathExpression(back!!)
            for (x in listOf(-2.5, 0.5, 1.7, 3.0)) {
                val a = before.evaluate(x)
                val b = after.evaluate(x)
                assertTrue("$source at $x: $a against $b via $typed", a.isNaN() && b.isNaN() || abs(a - b) <= 1e-9 * maxOf(1.0, abs(a)))
            }
        }
        assertEquals("log10(x)+log(x)", GraphSyntax.typed("log(x)+ln(x)"))
        assertEquals("log(x)", GraphSyntax.plotted("log10(x)"))
        assertEquals("ln(x)", GraphSyntax.plotted("log(x)"))
        assertEquals(2.0, MathExpression(GraphSyntax.plotted("nthroot(8,3)")!!).evaluate(0.0), 1e-12)
        // MathExpression: -x^2 is (-x)^2, the keypad's is -(x^2)
        assertEquals(-4.0, MathExpression(GraphSyntax.plotted("-x^2")!!).evaluate(2.0), 0.0)
        for (text in listOf("x^", "y+1", "diff(x^2,x)", "")) assertNull(text, GraphSyntax.plotted(text))
        assertTrue(GraphCurve(0, "x^", 0).isInvalid)
        assertFalse(GraphCurve(0, "", 0).isInvalid)
        assertTrue(GraphCurve(0, "1/(x-1)", 0).isValid)
        assertEquals("y₁₂", GraphCurve.name(11))
    }

    @Test
    fun plotHelpers() {
        // GraphPlot.selfCheck
        assertEquals(0.5, p.niceCeil(0.3), 0.0)
        assertEquals(1.0, p.niceCeil(1.0), 0.0)
        assertEquals(10.0, p.niceCeil(7.0), 0.0)
        assertEquals(5.0, p.niceCeil(2.0002), 0.0)
        assertEquals(0.5, p.niceFloor(0.9999), 0.0)
        assertEquals(0.5, p.niceFloor(0.5), 0.0)
        assertEquals(0.2, p.niceFloor(0.49), 1e-12)
        assertEquals(4, p.minorDivisions(2.0))
        assertEquals(5, p.minorDivisions(0.5))
        assertEquals(5, p.minorDivisions(10.0))
        assertEquals(-2L..2L, p.ticks(-10.0, 10.0, 5.0))
        assertEquals(10_000_000_000L..10_000_000_010L, p.ticks(1e10, 1e10 + 10, 1.0))
        assertEquals("10000000005", p.tickLabel(10_000_000_005L, 1.0))
        assertTrue(p.ticks(0.1, 0.9, 1.0).isEmpty())
        assertEquals("0.3", p.tickLabel(3, 0.1))
        assertEquals("−0.2", p.tickLabel(-4, 0.05))
        assertEquals("−10", p.tickLabel(-2, 5.0))
        assertEquals("2.25", p.format(2.25))
        assertEquals("0.3333", p.format(1.0 / 3))
        assertEquals("−1E−5", p.format(-0.00001))
        assertEquals("—", p.format(Double.NaN))
        assertEquals("−4E−5", p.format(-0.00004))
        assertEquals("12", p.format(12.0))
        assertEquals("0", p.format(-0.00001 + 0.00001))
        assertEquals("1.235E10", p.format(12345678901.0))
        assertEquals(1.5, p.number("1,5")!!, 0.0)
        assertEquals(PI / 2, p.number("pi/2")!!, 1e-12)
        assertEquals(-2.0, p.number("−2")!!, 0.0)
        assertNull(p.number("2x"))
        assertNull(p.number(""))
        assertNull(p.number("1e999"))
        assertTrue(p.isValid(MathExpression("1/(x-1)")))
        assertTrue(p.isValid(MathExpression("x^2")))
        assertFalse(p.isValid(MathExpression("x^")))
    }

    @Test
    fun sampling() {
        // tan: split at asymptotes
        val tanRuns = p.runs(400, 0f, 400f, 8.0, { -PI + it / 400.0 * 2 * PI }, { (-it).toFloat() }, -1e4f, 1e4f) { tan(it) }
        assertEquals(3, tanRuns.size)
        val square = p.runs(100, 0f, 100f, 20.0, { it / 10.0 - 5 }, { it.toFloat() }, -1e4f, 1e4f) { it * it }
        assertEquals(1, square.size)
        assertEquals(101 * 2, square[0].size)
        val holes = p.runs(100, 0f, 100f, 20.0, { it / 10.0 - 5 }, { it.toFloat() }, -1e4f, 1e4f) { kotlin.math.sqrt(it) }
        assertEquals(1, holes.size)
        assertEquals(51 * 2, holes[0].size)
    }

    @Test
    fun windows() {
        assertEquals(GraphWindow(-10.0, 10.0, -5.0, 5.0), GraphWindow.standard.resized(400.0, 400.0, 400.0, 200.0))
        assertEquals(GraphWindow(0.0, 10.0, -5.0, 5.0), GraphWindow.standard.zoomed(0.5, 10.0, 0.0))
        assertEquals(GraphWindow.decimal, GraphWindow.blend(GraphWindow.standard, GraphWindow.decimal, 1.0))
        assertEquals(GraphWindow.standard, GraphWindow.blend(GraphWindow.standard, GraphWindow.decimal, 0.0))
        assertEquals(GraphWindow(-10.0, 10.0, -5.0, 5.0), GraphWindow.square(200.0, 100.0))
        assertEquals(listOf(50f, 70f, 200f), p.spread(listOf(50f, 52f, 200f), 20f, 0f, 300f))
        assertEquals(listOf(300f, 280f), p.spread(listOf(295f, 290f), 20f, 0f, 300f))
    }

    @Test
    fun stepping() {
        assertEquals(2.0 to true, p.steppedTrace(1.0, 1.0, 1))
        assertEquals(2.0 to false, p.steppedTrace(1.3, 1.0, 1))
        assertEquals(1.0 to false, p.steppedTrace(1.3, 1.0, -1))
        assertEquals("0.3", p.format(p.tableX(0.1, 0.1, 2)))
        assertEquals(-2.0, p.tableX(1.0, 1.0, -3), 0.0)
    }
}
