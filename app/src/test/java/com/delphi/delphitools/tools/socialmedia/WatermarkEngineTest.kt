package com.delphi.delphitools.tools.socialmedia

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatermarkEngineTest {
    private val rect = Rect(Offset(10f, 20f), Size(300f, 150f))

    @Test
    fun zones() {
        assertEquals(WatermarkPosition.TopLeft, WatermarkEngine.zone(Offset(11f, 21f), rect))
        assertEquals(WatermarkPosition.Centre, WatermarkEngine.zone(Offset(160f, 95f), rect))
        assertEquals(WatermarkPosition.BottomRight, WatermarkEngine.zone(Offset(309f, 169f), rect))
        assertEquals(WatermarkPosition.BottomLeft, WatermarkEngine.zone(Offset(-50f, 400f), rect))
        assertNull(WatermarkEngine.zone(Offset.Zero, Rect.Zero))
        val corner = WatermarkEngine.anchor(WatermarkPosition.BottomRight, rect)
        assertEquals(280f, corner.x, 0.001f)
        assertEquals(155f, corner.y, 0.001f)
        WatermarkPosition.entries.forEach { assertEquals(it, WatermarkEngine.zone(WatermarkEngine.anchor(it, rect), rect)) }
    }

    @Test
    fun stamp() {
        assertEquals(Size(250f, 125f), WatermarkEngine.stampSize(Size(400f, 200f), Size(1000f, 2000f), 0.25f))
        assertEquals(Size(40f, 20f), WatermarkEngine.stampSize(Size(40f, 20f), Size(1000f, 2000f), 0.25f))
    }
}
