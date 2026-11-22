package com.delphi.delphitools.tools.typography

import org.junit.Assert.assertEquals
import org.junit.Test

class TypoCalcEngineTest {
    @Test
    fun niceStep() {
        assertEquals(0.5, TypoCalcEngine.niceStep(0.3), 1e-12)
        assertEquals(1.0, TypoCalcEngine.niceStep(1.0), 0.0)
        assertEquals(2.0, TypoCalcEngine.niceStep(1.1), 0.0)
        assertEquals(100.0, TypoCalcEngine.niceStep(60.0), 0.0)
    }

    @Test
    fun subdivisions() {
        assertEquals(4.0, TypoCalcEngine.subdivisions(0.2), 0.0)
        assertEquals(5.0, TypoCalcEngine.subdivisions(50.0), 0.0)
        assertEquals(5.0, TypoCalcEngine.subdivisions(1.0), 0.0)
    }

    @Test
    fun span() {
        assertEquals(20.0, TypoCalcEngine.span(16.0), 0.0)
        assertEquals(200.0, TypoCalcEngine.span(96.0), 0.0)
        assertEquals(2.0, TypoCalcEngine.span(0.0), 0.0)
    }

    @Test
    fun conversions() {
        assertEquals(12.0, TypoUnit.Pt.fromPx(16.0, 16.0), 1e-9)
        assertEquals(1.0, TypoUnit.In.fromPx(96.0, 16.0), 1e-9)
        assertEquals(25.4, TypoUnit.Mm.fromPx(96.0, 16.0), 1e-9)
        assertEquals(2.0, TypoUnit.Rem.fromPx(32.0, 16.0), 1e-9)
        assertEquals(300.0, TypoUnit.In.toPx(1.0, 16.0, ppi = 300.0), 1e-9)
        for (unit in TypoUnit.entries) assertEquals(unit.name, 7.5, unit.fromPx(unit.toPx(7.5, 18.0, 150.0), 18.0, 150.0), 1e-9)
    }

    @Test
    fun nudge() {
        assertEquals(17.0, TypoCalcEngine.nudge(16.0, TypoUnit.Px, 1), 0.0)
        assertEquals(1.25, TypoCalcEngine.nudge(1.1, TypoUnit.In, 1), 0.0)
        assertEquals(0.0, TypoCalcEngine.nudge(0.0, TypoUnit.Pc, -1), 0.0)
    }
}
