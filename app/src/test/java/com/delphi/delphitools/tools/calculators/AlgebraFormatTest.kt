package com.delphi.delphitools.tools.calculators

import org.junit.Assert.assertEquals
import org.junit.Test

class AlgebraFormatTest {
    @Test
    fun significantMatchesPrintfG() {
        assertEquals("2", AlgebraFormat.significant(2.000000000))
        assertEquals("-1.414213562", AlgebraFormat.significant(-1.4142135623730951))
        assertEquals("0.3333333333", AlgebraFormat.significant(1.0 / 3))
        assertEquals("1e-05", AlgebraFormat.significant(0.00001))
        assertEquals("0.0001", AlgebraFormat.significant(0.0001))
        assertEquals("1234567890", AlgebraFormat.significant(1234567890.0))
        assertEquals("1.5e+12", AlgebraFormat.significant(1.5e12))
        assertEquals("0", AlgebraFormat.significant(0.0))
    }

    @Test
    fun formattedKeepsNonNumbers() {
        assertEquals(listOf("2", "sqrt(2)"), AlgebraFormat.formatted(listOf("2.000000000", "sqrt(2)")))
    }

    @Test
    fun solutionNames() {
        assertEquals("x", AlgebraFormat.solutionName("x", 0, 1))
        assertEquals("x₂", AlgebraFormat.solutionName("x", 1, 2))
        assertEquals("t₁₂", AlgebraFormat.solutionName("t", 11, 12))
    }

    @Test
    fun constantDropped() {
        assertEquals("x^3/3", AlgebraFormat.withoutConstant("x^3/3 + C"))
        assertEquals("x", AlgebraFormat.withoutConstant("x"))
    }
}
