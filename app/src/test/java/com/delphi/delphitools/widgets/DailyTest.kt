package com.delphi.delphitools.widgets

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DailyTest {
    // values from ios code
    @Test
    fun matchesIosForADate() {
        val date = LocalDate.of(2026, 11, 28)
        assertEquals(12220541312171946253uL, Daily.hash(date))
        val (lightness, chroma, hue) = Daily.oklch(date)
        assertEquals(0.5512, lightness, 1e-9)
        assertEquals(0.1716, chroma, 1e-9)
        assertEquals(319.9, hue, 1e-9)
        assertEquals(5, Daily.palette(date).size)
    }
}
