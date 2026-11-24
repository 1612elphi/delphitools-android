package com.delphi.delphitools.tools.images

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentScanEngineTest {
    @Test
    fun pageSize() {
        assertEquals(595.0 to 842.0, DocumentScanEngine.pageSize(2480, 3508))
        assertEquals(842.0 to 421.0, DocumentScanEngine.pageSize(2000, 1000))
        assertEquals(0.0 to 0.0, DocumentScanEngine.pageSize(0, 0))
    }

    @Test
    fun luma() {
        assertEquals(255, DocumentScanEngine.luma(0xFFFFFFFF.toInt()))
        assertEquals(0, DocumentScanEngine.luma(0xFF000000.toInt()))
        assertEquals(76, DocumentScanEngine.luma(0xFFFF0000.toInt()))
    }

    @Test
    fun stretch() {
        assertEquals(200, DocumentScanEngine.stretch(200, 0))
        assertEquals(255, DocumentScanEngine.stretch(200, 100))
        assertEquals(0, DocumentScanEngine.stretch(60, 100))
        assertEquals(173, DocumentScanEngine.stretch(150, 50))
    }

    @Test
    fun filters() {
        val pixels = intArrayOf(0xFF102030.toInt(), 0x80F0E0D0.toInt(), 0xFF808080.toInt())
        assertArrayEquals(intArrayOf(0xFF102030.toInt(), 0xFFF0E0D0.toInt(), 0xFF808080.toInt()), DocumentScanEngine.filter(pixels, ScanFilter.Colour, 0, 50))
        val grey = DocumentScanEngine.filter(pixels, ScanFilter.Greyscale, 0, 50)
        assertEquals(0xFF1D1D1D.toInt(), grey[0])
        assertEquals(0xFF808080.toInt(), grey[2])
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        assertArrayEquals(intArrayOf(black, white, white), DocumentScanEngine.filter(pixels, ScanFilter.BlackWhite, 0, 50))
        assertArrayEquals(intArrayOf(black, white, black), DocumentScanEngine.filter(pixels, ScanFilter.BlackWhite, 0, 51))
    }
}
