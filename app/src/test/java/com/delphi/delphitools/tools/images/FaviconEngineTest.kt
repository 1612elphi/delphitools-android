package com.delphi.delphitools.tools.images

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FaviconEngineTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun square() {
        assertEquals(IntRect(100, 0, 300, 200), FaviconEngine.centred(400, 200))
        assertEquals(IntRect(150, 50, 250, 150), FaviconEngine.centred(400, 200, 100))
        assertEquals(IntRect(310, 0, 400, 90), FaviconEngine.clamp(350f, -20f, 90f, 400, 200))
        assertEquals(IntRect(10, 10, 30, 30), FaviconEngine.clamp(10f, 10f, 3f, 400, 200))
        assertEquals(16, FaviconEngine.clamp(0f, 0f, 3f, 100, 100).width)
        assertEquals(200, FaviconEngine.clamp(0f, 0f, 900f, 400, 200).width)
        assertEquals(8, FaviconEngine.clamp(0f, 0f, 1f, 10, 8).width)
    }

    @Test
    fun ico() {
        val file = FaviconEngine.ico(listOf(32 to bytes(9, 9), 16 to bytes(7)))
        assertArrayEquals(bytes(0, 0, 1, 0, 2, 0), file.copyOfRange(0, 6))
        assertArrayEquals(bytes(16, 16, 0, 0, 1, 0, 32, 0, 1, 0, 0, 0, 38, 0, 0, 0), file.copyOfRange(6, 22))
        assertEquals(32, file[22].toInt())
        assertEquals(39, file[34].toInt())
        assertArrayEquals(bytes(7, 9, 9), file.copyOfRange(file.size - 3, file.size))
        assertEquals(0, FaviconEngine.ico(listOf(256 to bytes(1)))[6].toInt())
    }
}
