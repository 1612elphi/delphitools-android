package com.delphi.delphitools.tools.images

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.delphi.delphitools.tools.images.ImageSplitEngine.Cell
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSplitEngineTest {
    @Test
    fun grid() {
        assertEquals(IntSize(333, 200), ImageSplitEngine.tileSize(1000, 601, 3, 3))
        assertEquals(6, ImageSplitEngine.cells(2, 3).size)
        assertEquals(Cell(1, 1), ImageSplitEngine.cells(2, 3)[4])
        assertEquals(IntRect(666, 200, 999, 400), ImageSplitEngine.rect(Cell(1, 2), 1000, 600, 3, 3))
        assertEquals("1-10", Cell(0, 9).label)
    }

    @Test
    fun frames() {
        val frames = ImageSplitEngine.frames(1, 2, Size(100f, 100f), 10f, Rect(0f, 0f, 210f, 300f))
        assertEquals(listOf(Rect(0f, 100f, 100f, 200f), Rect(110f, 100f, 210f, 200f)), frames)
    }
}
