package com.delphi.delphitools.tools.images

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageConvertTest {
    // the sheet dt-ios fits a 200 × 100 box to in a 244 × 144 area
    private val sheet = Rect(44f, 44f, 244f, 144f)

    @Test
    fun frames() {
        val same = ImageConvert.frames(Size(200f, 100f), Size(200f, 100f), sheet)
        assertEquals(sheet, same.first)
        assertEquals(same.first, same.second)
        val half = ImageConvert.frames(Size(200f, 100f), Size(100f, 50f), sheet)
        assertEquals(sheet, half.second)
        assertEquals(Rect(94f, 69f, 194f, 119f), half.first)
        val double = ImageConvert.frames(Size(100f, 50f), Size(200f, 100f), sheet)
        assertEquals(sheet, double.first)
        assertEquals(100f, double.second.width)
        val stretch = ImageConvert.frames(Size(100f, 100f), Size(200f, 50f), Rect(0f, 0f, 200f, 100f))
        assertEquals(2 * stretch.second.width, stretch.first.width)
        assertEquals(stretch.second.height / 2, stretch.first.height)
    }

    @Test
    fun limits() {
        assertEquals(400, ImageConvert.percentLimit(1600, 1200))
        assertEquals(203, ImageConvert.percentLimit(4032, 3024))
        assertEquals(400, ImageConvert.percentLimit(0, 0))
        assertEquals(8192 to 4096, ImageConvert.capped(10000, 5000))
        assertEquals(100 to 50, ImageConvert.capped(100, 50))
    }

    @Test
    fun sizes() {
        assertEquals(3 to 1, ImageConvert.outputSize(ImageResizeMode.Percentage, 7, 3, 50.0, 0, 0))
        assertEquals(1 to 9, ImageConvert.outputSize(ImageResizeMode.Custom, 7, 3, 100.0, 0, 9))
        assertEquals(0 to 0, ImageConvert.outputSize(ImageResizeMode.Original, 0, 3, 100.0, 0, 0))
        assertEquals(75, ImageConvert.locked(100, 4032, 3024))
        assertEquals("2.4" to "MB", ImageConvert.figure("2.4 MB"))
        assertEquals("12" to "kB", ImageConvert.figure("12 kB"))
    }
}
