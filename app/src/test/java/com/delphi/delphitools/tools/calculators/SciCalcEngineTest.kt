package com.delphi.delphitools.tools.calculators

import com.delphi.delphitools.tools.calculators.SciCalcEntry.groupBreaks
import com.delphi.delphitools.tools.calculators.SciCalcEntry.join
import com.delphi.delphitools.tools.calculators.SciCalcEntry.kind
import com.delphi.delphitools.tools.calculators.SciCalcEntry.openParens
import com.delphi.delphitools.tools.calculators.SciCalcEntry.parts
import com.delphi.delphitools.tools.calculators.SciCalcEntry.toggledSign
import com.delphi.delphitools.tools.calculators.SciCalcEntry.written
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SciCalcEngineTest {
    @Test
    fun entry() {
        assertEquals("2×π", join("2", "π", SciKind.Factor))
        assertEquals("2×sin(", join("2", "sin(", SciKind.Factor))
        assertEquals("(3)×4", join("(3)", "4", SciKind.Digit))
        assertEquals("π×2", join("π", "2", SciKind.Digit))
        assertEquals("2e3", join("2e", "3", SciKind.Digit))
        assertEquals("e×2", join("e", "2", SciKind.Digit))
        assertEquals("2+π", join("2+", "π", SciKind.Factor))
        assertEquals("12+", join("12", "+", SciKind.Other))
        assertEquals("π", join("", "π", SciKind.Factor))
        assertEquals(SciKind.Digit, kind("7"))
        assertEquals(SciKind.Factor, kind("sin"))
        assertEquals(SciKind.Other, kind("+"))
    }

    @Test
    fun sign() {
        assertEquals("3-4", toggledSign("3+4"))
        assertEquals("3+4", toggledSign("3-4"))
        assertEquals("3×-4", toggledSign("3×4"))
        assertEquals("3×4", toggledSign("3×-4"))
        assertEquals("-12", toggledSign("12"))
        assertEquals("12", toggledSign("-12"))
        assertEquals("1.5e-3", toggledSign("1.5e3"))
        assertEquals("1.5e3", toggledSign("1.5e-3"))
        assertNull(toggledSign("sin("))
        assertEquals(2, openParens("sin(cos(4"))
        assertEquals(0, openParens("(1))"))
    }

    @Test
    fun numbers() {
        assertEquals("6.62607015 × 10⁻³⁴", written("6.62607015e-34"))
        assertEquals("−9.80665", written("-9.80665"))
        assertEquals("1.234567891" to "15", parts("1.234567891e+15"))
        assertEquals("−2" to "−5", parts("-2e-05"))
        assertEquals("0.25" to null, parts("0.25"))
        assertEquals("−7" to null, parts("-7"))
        assertEquals("Error" to null, parts("Error"))
        assertEquals(listOf(0, 3), groupBreaks("1234567.5"))
        assertEquals(listOf(2), groupBreaks("−12345"))
        assertEquals(emptyList<Int>(), groupBreaks("1234"))
        assertEquals("6.62607e-34", SciCalcEngine.format(6.62607015e-34))
        assertEquals("1e+15", SciCalcEngine.format(1e15))
        assertEquals("0.1", SciCalcEngine.format(0.1))
    }

    @Test
    fun syntax() {
        fun plain(raw: String) = SciCalcSyntax.pieces(raw).joinToString("") { it.text }
        assertEquals("√(2)×3−1", plain("sqrt(2)*3-1"))
        assertEquals("5 mod 3", plain("5%3"))
        assertEquals("ʸ√(3,8)", plain("root(3,8)"))
        assertEquals("1.5E−3", plain("1.5e-3"))
        assertEquals("2×e2", plain("2×e^2"))
        assertEquals("2−3", plain("2^-3"))
        assertEquals("2^", plain("2^"))
        assertEquals(SciPiece("10", SciPieceKind.Number, raised = true), SciCalcSyntax.pieces("2^10").last())
        assertEquals(listOf(SciPieceKind.Function, SciPieceKind.Bracket, SciPieceKind.Number), SciCalcSyntax.pieces("sin(π").map { it.kind })
    }

    @Test
    fun evaluates() {
        assertEquals(0.5, SciCalcEngine.evaluate("sin(30)", degrees = true), 0.0)
        assertEquals(0.0, SciCalcEngine.evaluate("cos(90)", degrees = true), 0.0)
        assertEquals(Double.POSITIVE_INFINITY, SciCalcEngine.evaluate("tan(90)", degrees = true), 0.0)
        assertEquals(2.0, SciCalcEngine.evaluate("root(3,8)", degrees = true), 1e-12)
        assertEquals(120.0, SciCalcEngine.evaluate("5!", degrees = true), 0.0)
        assertEquals(1.0, SciCalcEngine.evaluate("10%3", degrees = true), 0.0)
        assertEquals(5.0, SciCalcEngine.evaluate("|2-7|", degrees = true), 0.0)
        assertEquals(1500.0, SciCalcEngine.evaluate("1.5e3", degrees = true), 0.0)
        assertEquals(14.0, SciCalcEngine.evaluate("2+3×4", degrees = true), 0.0)
        assertTrue(SciCalcEngine.evaluate("2+", degrees = true).isNaN())
        assertTrue(SciCalcEngine.evaluate("foo(2)", degrees = true).isNaN())
        assertEquals("0.5", SciCalcEngine.format(SciCalcEngine.evaluate("sin(π/6)", degrees = false)))
    }

    @Test
    fun unaryMinus() {
        fun eval(text: String) = SciCalcEngine.evaluate(text, degrees = true)
        assertEquals(-4.0, eval("-2^2"), 0.0)
        assertEquals(0.25, eval("2^-2"), 0.0)
        assertEquals(-5.0, eval("-(2+3)"), 0.0)
        assertEquals(-5.0, eval("−(2+3)"), 0.0)
        assertEquals(-0.5, eval("-sin(30)"), 0.0)
        assertEquals(-Math.PI, eval("-π"), 0.0)
        assertEquals(-2.0, eval("2×-(1)"), 0.0)
        assertEquals(1.0, eval("3−2"), 0.0)
        assertEquals(5.0, eval("2--3"), 0.0)
        assertEquals(-0.0015, eval("-1.5e-3"), 0.0)
    }

    @Test
    fun powerIsRightAssociative() {
        fun eval(text: String) = SciCalcEngine.evaluate(text, degrees = true)
        assertEquals(512.0, eval("2^3^2"), 0.0)
        assertEquals(81.0, eval("3^2^2"), 0.0)
        assertEquals(64.0, eval("2^3!"), 0.0)
    }
}
