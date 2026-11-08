package com.delphi.delphitools.tools.images

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageCropTest {
    @Test
    fun geometry() {
        val page = Size(200f, 100f)
        val pageRect = ImageCrop.pageRect(page, Size(244f, 144f), 22f)
        assertEquals(Rect(22f, 22f, 222f, 122f), pageRect)
        val shown = ImageCrop.shown(Rect(50f, 25f, 150f, 75f), page, pageRect)
        assertEquals(Rect(72f, 47f, 172f, 97f), shown)
        assertEquals(CropEdges(left = true, top = true, right = false, bottom = false), ImageCrop.edges(Offset(74f, 48f), shown, 22f))
        assertNull(ImageCrop.edges(Offset(122f, 72f), shown, 22f))
        assertNull(ImageCrop.edges(Offset(0f, 0f), shown, 22f))
    }

    @Test
    fun dragging() {
        val crop = Rect(50f, 25f, 150f, 75f)
        assertEquals(Rect(142f, 25f, 150f, 75f), ImageCrop.resized(crop, CropEdges(true, false, false, false), 190f, 0f))
        assertEquals(Rect(50f, 0f, 150f, 75f), ImageCrop.resized(crop, CropEdges(false, true, false, false), 0f, 0f))
        assertEquals(Rect(100f, 50f, 200f, 100f), ImageCrop.moved(crop, Size(200f, 100f), Offset(10f, 10f), 500f, 500f))
        assertEquals(Rect(1f, 2f, 4f, 5f), ImageCrop.integral(Rect(1.5f, 2.2f, 3.1f, 4.9f)))
    }
}
