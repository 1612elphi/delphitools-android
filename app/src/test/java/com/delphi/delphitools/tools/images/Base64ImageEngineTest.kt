package com.delphi.delphitools.tools.images

import org.junit.Assert.assertEquals
import org.junit.Test

class Base64ImageEngineTest {
    @Test
    fun mosaic() {
        assertEquals(listOf(10, 0, 20, 10), Base64ImageEngine.cover(40, 10, 5f, 10f, 100f, 100f))
        assertEquals(listOf(0, 3, 40, 3), Base64ImageEngine.cover(40, 10, 5f, 10f, 400f, 50f))
        assertEquals("512" to "B", Base64ImageEngine.figure(512))
        assertEquals("2.0" to "KB", Base64ImageEngine.figure(2048))
        assertEquals("3.0" to "MB", Base64ImageEngine.figure(3 * 1_048_576))
    }

    @Test
    fun strings() {
        assertEquals("data:image/png;base64,QUJD", Base64ImageEngine.dataUri("image/png", "QUJD"))
        assertEquals(26, Base64ImageEngine.uriBytes("image/png", "QUJD"))
        val long = "A".repeat(500)
        assertEquals("data:image/gif;base64,${"A".repeat(480)}…", Base64ImageEngine.preview("image/gif", long))
        assertEquals("data:image/gif;base64,QQ", Base64ImageEngine.preview("image/gif", "QQ"))
    }
}
