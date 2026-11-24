package com.delphi.delphitools.tools.images

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.FloatBuffer

class BackgroundRemoveEngineTest {
    @Test
    fun planesAreRedGreenBlueCentredOnZero() {
        val planes = BackgroundRemoveEngine.planes(intArrayOf(0xffff0000.toInt(), 0xff0000ff.toInt()))
        assertArrayEquals(floatArrayOf(0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f), planes, 1e-6f)
    }

    @Test
    fun matteBecomesAlphaAndShare() {
        val (pixels, share) = BackgroundRemoveEngine.alpha(FloatBuffer.wrap(floatArrayOf(0f, 1f, 1.5f, -1f)))
        assertArrayEquals(intArrayOf(0, 0xff shl 24, 0xff shl 24, 0), pixels)
        assertEquals(0.5f, share, 1e-6f)
    }
}
