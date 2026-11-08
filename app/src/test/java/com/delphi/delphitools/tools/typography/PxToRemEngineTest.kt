package com.delphi.delphitools.tools.typography

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PxToRemEngineTest {
    @Test
    fun format() {
        assertEquals("0.5", PxToRemEngine.format(0.5))
        assertEquals("1", PxToRemEngine.format(1.0))
        assertEquals("0.6875", PxToRemEngine.format(0.6875))
        assertEquals("26.25", PxToRemEngine.format(26.25))
        assertEquals("0.3333", PxToRemEngine.format(1.0 / 3))
        assertEquals("1234.5", PxToRemEngine.format(1234.5))
    }

    @Test
    fun parse() {
        assertEquals(1.5, PxToRemEngine.parse("1,5")!!, 0.0)
        assertNull(PxToRemEngine.parse("x"))
        assertNull(PxToRemEngine.parse(""))
    }

    @Test
    fun convert() {
        assertEquals(1.5, PxToRemEngine.convert("24", 16.0, remToPixel = false)!!, 0.0)
        assertEquals(40.0, PxToRemEngine.convert("2.5", 16.0, remToPixel = true)!!, 0.0)
        assertNull(PxToRemEngine.convert("-1", 16.0, remToPixel = false))
    }
}
