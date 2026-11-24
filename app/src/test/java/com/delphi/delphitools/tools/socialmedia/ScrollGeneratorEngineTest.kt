package com.delphi.delphitools.tools.socialmedia

import com.delphi.delphitools.tools.socialmedia.ScrollGeneratorEngine.Slice
import com.delphi.delphitools.tools.socialmedia.ScrollGeneratorEngine.geometry
import com.delphi.delphitools.tools.socialmedia.ScrollGeneratorEngine.slices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollGeneratorEngineTest {
    @Test
    fun selfCheck() {
        val fills = geometry(3000, 1350, 0.8)!!
        assertEquals(1080, fills.tileWidth)
        assertEquals(3, fills.slideCount)
        assertTrue(fills.needsFill)
        assertEquals(120.0, fills.imageStart, 0.0)
        assertEquals(
            listOf(
                Slice(0.0, 960.0, 120.0, true),
                Slice(960.0, 1080.0, 0.0, false),
                Slice(2040.0, 960.0, 0.0, true),
            ),
            slices(fills),
        )
        assertFalse(geometry(3240, 1350, 0.8)!!.needsFill)
        val over = geometry(3600, 1350, 0.8)!!
        assertEquals(4, over.slideCount)
        assertEquals(360.0, over.imageStart, 0.0)
        assertEquals(Slice(0.0, 720.0, 360.0, true), slices(over)[0])
        val square = geometry(3000, 2000, 1.0)!!
        assertEquals(2, square.slideCount)
        assertEquals(500.0, square.imageStart, 0.0)
        assertEquals(Slice(1500.0, 1500.0, 0.0, true), slices(square)[1])
        val narrow = geometry(300, 2000, 0.8)!!
        assertEquals(listOf(Slice(0.0, 300.0, 650.0, true)), slices(narrow))
        assertEquals(4, geometry(3241, 1350, 0.8)!!.slideCount)
        val tall = geometry(500, 2000, 1.0)!!
        assertEquals(1, tall.slideCount)
        assertEquals(750.0, slices(tall)[0].drawX, 0.0)
        assertNull(geometry(0, 10, 1.0))

        val placed = ScrollGeneratorEngine.place(over, 0.0, 0.0, 432.0, 1000.0)
        assertEquals(0.1, placed.scale, 1e-9)
        assertEquals(0.0, placed.stripX, 0.0)
        assertEquals(432.5, placed.y, 1e-9)
    }

    @Test
    fun sizes() {
        assertEquals(108 to 135, ScrollGeneratorEngine.slideSize(geometry(3000, 1350, 0.8)!!, 135))
        assertEquals(8192, ScrollGeneratorEngine.longestSide(8192, 8192))
        assertEquals(8192, ScrollGeneratorEngine.longestSide(0, 10))
        assertEquals(8192 * 2, ScrollGeneratorEngine.longestSide(16384, 4096))
        assertEquals(8192, ScrollGeneratorEngine.longestSide(16384, 16384))
    }
}
