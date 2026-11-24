package com.delphi.delphitools.tools.pdf

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSyntaxTest {
    private fun parse(text: String) = PdfLexer(text.toByteArray(Charsets.ISO_8859_1), 0).value()

    @Test
    fun parsesObjects() {
        @Suppress("UNCHECKED_CAST")
        val dict = parse("<< /A 1 /B [2 0 R 3 -4.5 /N] /C (a\\(b\\)\\101\\\nc) /D <48 49 5> /E null /F true >>") as Map<String, Any?>
        assertEquals(1, dict["A"])
        assertEquals(listOf(PdfRef(2), 3, -4.5, PdfName("N")), dict["B"])
        assertEquals("a(b)Ac", String((dict["C"] as PdfString).bytes))
        assertArrayEquals(byteArrayOf(0x48, 0x49, 0x50), (dict["D"] as PdfString).bytes)
        assertTrue("E" !in dict)
        assertEquals(true, dict["F"])
    }

    @Test
    fun roundTrips() {
        val value = linkedMapOf<String, Any?>(
            "S" to PdfString(byteArrayOf('('.code.toByte(), 13, '\\'.code.toByte())),
            "R" to listOf(0.5, 1.0 / 3, PdfRef(7, 1)),
        )
        @Suppress("UNCHECKED_CAST")
        val back = parse(String(PdfSerialiser.bytes(value), Charsets.ISO_8859_1)) as Map<String, Any?>
        assertArrayEquals(byteArrayOf('('.code.toByte(), 13, '\\'.code.toByte()), (back["S"] as PdfString).bytes)
        assertEquals(listOf(0.5, 0.3333, PdfRef(7, 1)), back["R"])
        assertEquals("12", PdfSerialiser.real(12.0))
        assertEquals("-0.25", PdfSerialiser.real(-0.25))
    }

    @Test
    fun undoesPngPredictors() {
        // rows of 2 bytes: none, sub, up
        val data = byteArrayOf(0, 1, 2, 1, 1, 1, 2, 1, 1)
        assertArrayEquals(byteArrayOf(1, 2, 1, 2, 2, 3), PdfFilters.unpredict(data, 1, 2))
        val raw = ByteArray(1000) { (it % 7).toByte() }
        assertArrayEquals(raw, PdfFilters.inflate(PdfFilters.deflate(raw)))
    }

    @Test
    fun readsStreamsWithAWrongLength() {
        val text = "1 0 obj\n<< /Length 99 >>\nstream\nhello\nendstream\nendobj\n"
        val (ref, value) = PdfLexer(text.toByteArray(), 0).indirect()
        assertEquals(PdfRef(1), ref)
        assertEquals("hello", String((value as PdfStream).data))
    }
}
