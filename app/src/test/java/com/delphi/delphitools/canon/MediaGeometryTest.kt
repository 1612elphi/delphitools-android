package com.delphi.delphitools.canon

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaGeometryTest {
    @Test
    fun dimensionLines() = with(Density(1f)) {
        val span = Rect(Offset(44f, 44f), Size(200f, 100f))
        val (across, down) = canonDimensionLines(span, rightToLeft = false)
        assertEquals(CanonDimensionPlacement(Offset(144f, 16f), Size(200f, 32f)), across)
        assertEquals(CanonDimensionPlacement(Offset(16f, 94f), Size(100f, 32f), -90f), down)
        assertEquals(Offset(272f, 94f), canonDimensionLines(span, rightToLeft = true).second.centre)
        val inset = Rect(94f, 64f, 194f, 124f)
        assertEquals(Offset(144f, 20f), canonDimensionLines(inset, false, edge = span, gap = 8.dp).first.centre)
        val sheet = canonDimensionSheet(Size(1600f, 1200f), Rect(0f, 0f, 444f, 344f), rightToLeft = false)
        assertEquals(Rect(44f, 44f, 444f, 344f), sheet)
    }

    @Test
    fun detentMotion() {
        fun settle(start: CanonDetentMotion, range: ClosedFloatingPointRange<Float>?): Pair<CanonDetentMotion, Float> {
            var motion = start
            var time = 0f
            while (!motion.isSettled && time < 10f) {
                motion = motion.advance(1f / 60, range)
                time += 1f / 60
            }
            return motion to time
        }
        val (flung, time) = settle(CanonDetentMotion(0.3f, 30f), null)
        assertTrue(flung.isSettled && flung.position == Math.round(flung.position).toFloat() && flung.position > 4 && time < 3)
        assertEquals(4f, settle(CanonDetentMotion(4.6f, 12f), 0f..4f).first.position)
        assertEquals(0f, settle(CanonDetentMotion(-0.8f, -5f), 0f..4f).first.position)
        assertEquals(3f, settle(CanonDetentMotion(2.45f, 2f), null).first.position)
    }

    @Test
    fun loupeFrame() {
        val view = Size(300f, 200f)
        assertEquals(Rect(76f, 16f, 176f, 116f), canonLoupeFrame(Offset(200f, 140f), 100f, 24f, view, top = 8f, margin = 8f))
        assertEquals(Rect(44f, 44f, 144f, 144f), canonLoupeFrame(Offset(20f, 20f), 100f, 24f, view, top = 8f, margin = 8f))
    }

    @Test
    fun timecode() {
        assertEquals("01:23.40", canonHundredths(83.4))
        assertEquals("99:00.00", canonHundredths(99.0 * 60 + 3600))
        assertEquals("01:02:03:04", canonSmpte(25)(3723.16))
    }
}
