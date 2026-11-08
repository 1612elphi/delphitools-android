package com.delphi.delphitools.tools.images

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderSpecTest {
    @Test
    fun spec() {
        var spec = PlaceholderSpec(800, 600, "#e2e2e2", "#666666", "")
        assertEquals("800 × 600", spec.label)
        assertEquals(75.0, spec.fontSize, 0.0)
        assertTrue(spec.svg.contains("font-size=\"75\""))
        assertTrue(spec.svg.contains(">800 × 600</text>"))
        spec = spec.copy(text = "a<b & \"c\"")
        assertTrue(spec.svg.contains(">a&lt;b &amp; &quot;c&quot;</text>"))
        assertEquals("placeholder-800x600", spec.baseName)
    }

    @Test
    fun formatting() {
        assertEquals("16:9", PlaceholderSpec.aspect(1920, 1080))
        assertEquals("40:21", PlaceholderSpec.aspect(1200, 630))
        assertEquals("1.00:1", PlaceholderSpec.aspect(4096, 4095))
        assertEquals("12.50", PlaceholderSpec.number(12.5))
        assertEquals("125", PlaceholderSpec.number(125.0))
    }
}
