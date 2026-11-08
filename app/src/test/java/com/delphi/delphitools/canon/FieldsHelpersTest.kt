package com.delphi.delphitools.canon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FieldsHelpersTest {
    @Test
    fun decimalText() {
        assertEquals(1.5, decimalFieldParse(" 1,5 ")!!, 0.0)
        assertEquals(-0.25, decimalFieldParse("-.25")!!, 0.0)
        assertNull(decimalFieldParse("NaN"))
        assertNull(decimalFieldParse("1e3"))
        assertEquals("0.1", decimalFieldFormat(0.1, 3))
        assertEquals("2", decimalFieldFormat(2.0004, 3))
        assertEquals("0", decimalFieldFormat(-0.0001, 3))
        assertEquals("12345678", decimalFieldFormat(12345678.0, 3))
    }

    @Test
    fun textEncodings() {
        val word = "Grüße"
        assertEquals(word, decodeTextFile(word.toByteArray(Charsets.UTF_8)))
        assertEquals(word, decodeTextFile(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + word.toByteArray(Charsets.UTF_8)))
        assertEquals(word, decodeTextFile(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + word.toByteArray(Charsets.UTF_16LE)))
        assertEquals(word, decodeTextFile(byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + word.toByteArray(Charsets.UTF_16BE)))
        assertEquals(word, decodeTextFile(word.toByteArray(charset("windows-1252"))))
    }
}
