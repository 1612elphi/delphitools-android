package com.delphi.delphitools.canon.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonMathExpressionTest {
    private fun typed(vararg keys: CanonMathKey, variable: String = "x") =
        CanonMathExpression("", variable).also { e -> keys.forEach(e::apply) }

    private fun sym(s: String) = CanonMathKey.Symbol(s)

    @Test
    fun roundTripKeepsTree() {
        for (text in listOf(
            "(x+1)^2 - x^2", "sin(x)^2 + cos(x)^2", "(a*b)/(a*c)", "x^2 + 5*x + 6", "2*x + 3 = 7", "e^x * x^2", "1/x",
            "sqrt(x)+abs(x-1)", "x^(-1/2)", "-x^2", "2*3", "nthroot(x,3)+log(8,2)+floor(x)", "sum(k^2,k,1,n)",
            "defint(x^2,0,3,x)", "diff(sin(x),x)", "limit(1/x,x,Infinity)", "asin(x)+log10(x)+log(x)", "gcd(12,18)",
        )) {
            val round = CanonMathExpression(text).text
            assertEquals("$text came back as $round", CanonMathNode.parse(text), CanonMathNode.parse(round))
        }
    }

    @Test
    fun typingAndDeleting() {
        val e = typed(sym("x"), CanonMathKey.Power, sym("2"), CanonMathKey.Right, sym("+"), sym("1"), CanonMathKey.Divide, sym("2"))
        assertEquals("x^2+(1)/(2)", e.text)
        e.apply(CanonMathKey.Delete)
        assertEquals("x^2+(1)/()", e.text)
        repeat(3) { e.apply(CanonMathKey.Delete) }
        assertEquals("x^2+", e.text)
        e.apply(CanonMathKey.Left)
        e.apply(CanonMathKey.Left)
        e.apply(sym("3"))
        assertEquals("x^(23)+", e.text)
    }

    @Test
    fun closeStepsOutOfBrackets() {
        assertEquals("2*(x)^2", typed(sym("2"), CanonMathKey.Open, sym("x"), CanonMathKey.Close, CanonMathKey.Square).text)
    }

    @Test
    fun calculusUsesTheVariable() {
        val e = typed(
            CanonMathKey.Series(false), sym("1"), CanonMathKey.Up, sym("5"), CanonMathKey.Right, sym("k"), CanonMathKey.Right, sym("+"),
            CanonMathKey.Integral(true), sym("0"), CanonMathKey.Up, sym("1"), CanonMathKey.Right, sym("t"),
            variable = "t",
        )
        assertEquals("sum(k,k,1,5)+defint(t,0,1,t)", e.text)
    }

    @Test
    fun clearEmptiesAndUnparsableTextComesInAsSymbols() {
        val e = CanonMathExpression("2+")
        assertEquals("2+", e.text)
        e.apply(CanonMathKey.Clear)
        assertEquals("", e.text)
        assertTrue(CanonMathKey.Fraction.opensSlot)
        assertFalse(CanonMathKey.Square.opensSlot || sym("x").opensSlot)
    }
}
