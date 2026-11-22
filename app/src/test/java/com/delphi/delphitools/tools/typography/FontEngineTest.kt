package com.delphi.delphitools.tools.typography

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.Deflater

class FontEngineTest {
    private val iosevka = File("src/main/res/font/iosevka_regular.ttf").readBytes()
    private val roboto = File("src/main/res/font/roboto_flex.ttf").readBytes()

    @Test
    fun parsesIosevka() {
        val info = FontEngine.parse(FontEngine.decode(iosevka), "iosevka_regular.ttf")
        assertEquals("Iosevka", info.familyName)
        assertEquals("Regular", info.styleName)
        assertEquals(1000, info.unitsPerEm)
        assertTrue(info.ascent > 0 && info.descent > 0)
        assertTrue(info.glyphCount > 100)
        assertTrue(info.tables.containsAll(listOf("cmap", "head", "hhea", "name")))
        assertEquals(info.tables.sorted(), info.tables)
        assertTrue(info.coverage.covers('A'.code))
        assertFalse(info.coverage.covers(0x10450))
        assertEquals("Hxdp", FontEngine.specimen(info.coverage::covers))
        assertTrue(info.capHeight != null && info.xHeight != null && info.capHeight!! > info.xHeight!!)
    }

    @Test
    fun parsesVariableRoboto() {
        val info = FontEngine.parse(FontEngine.decode(roboto), "roboto_flex.ttf")
        assertTrue(info.familyName.startsWith("Roboto"))
        assertTrue("fvar" in info.tables)
        assertEquals(0.0, info.italicAngle, 0.0)
    }

    @Test
    fun woffRoundTrip() {
        val woff = woff(iosevka)
        val decoded = FontEngine.parse(FontEngine.decode(woff), "x.woff")
        val original = FontEngine.parse(iosevka, "x.woff")
        assertEquals(original.copy(coverage = decoded.coverage), decoded)
        assertTrue(decoded.coverage.covers('g'.code))
    }

    @Test
    fun rejects() {
        assertThrows(FontFormatException::class.java) { FontEngine.decode(byteArrayOf(1, 2, 3, 4, 5)) }
        // woff2: no decoder
        assertThrows(FontFormatException::class.java) { FontEngine.decode("wOF2xxxx".toByteArray()) }
        val many = ByteBuffer.allocate(44 + 4096 * 20).putInt(0x774F4646).putInt(0x00010000).putInt(0).putShort(0x1000).array()
        assertThrows(FontFormatException::class.java) { FontEngine.decode(many) }
        assertThrows(FontFormatException::class.java) { FontEngine.parse(iosevka.copyOf(40), "cut.ttf") }
    }

    @Test
    fun selfCheck() {
        assertEquals("Hxdp", FontEngine.specimen { true })
        val shavian = FontEngine.specimen { it in 0x10450..0x1047F }
        // first four letters
        assertEquals(listOf(0x10450, 0x10451, 0x10452, 0x10453), shavian.codePoints().toArray().toList())
        assertEquals("Hxdp", FontEngine.specimen { false })
        assertTrue(FontEngine.css("Inter", "Inter.woff2").contains("format('woff2')"))
        assertTrue(FontEngine.css("Inter", "Inter.TTF").contains("format('truetype')"))
        assertEquals("−250", FontEngine.signed(-250))
        assertEquals("0", FontEngine.signed(0))
        assertEquals(0x20..0x17F, FontEngine.chartRange("Hxdp", emptyList()))
        assertEquals(0x10450..0x1047F, FontEngine.chartRange(shavian, listOf(UnicodeBlock("Shavian", 0x10450..0x1047F))))
    }

    @Test
    fun fit() {
        // fills the height
        assertEquals(100f to 106f, FontEngine.fit(0.8f, 0.2f, 1f, 10f, 1000f, 116f, 16f))
        // halves, then clips
        assertEquals(50f to 10f + 16 + 25 + 40, FontEngine.fit(0.8f, 0.2f, 20f, 10f, 1000f, 116f, 16f))
        // fits the width
        assertEquals(80f, FontEngine.fit(0.8f, 0.2f, 12.5f, 10f, 1000f, 116f, 16f).first)
    }

    @Test
    fun coverageMerges() {
        val coverage = FontCoverage.of(listOf(5..9, 1..3, 4..4, 20..20))
        assertTrue(coverage.covers(1) && coverage.covers(9) && coverage.covers(20))
        assertFalse(coverage.covers(0) || coverage.covers(10) || coverage.covers(21))
    }

    private fun woff(sfnt: ByteArray): ByteArray {
        val input = ByteBuffer.wrap(sfnt)
        val count = input.getShort(4).toInt()
        class Table(val tag: Int, val checksum: Int, val data: ByteArray)
        val tables = (0 until count).map {
            val base = 12 + it * 16
            val offset = input.getInt(base + 8)
            Table(input.getInt(base), input.getInt(base + 4), sfnt.copyOfRange(offset, offset + input.getInt(base + 12)))
        }
        val packed = tables.map { table ->
            val deflater = Deflater()
            deflater.setInput(table.data)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(65536)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            deflater.end()
            out.toByteArray().takeIf { it.size < table.data.size } ?: table.data
        }
        val header = 44 + count * 20
        val body = ByteArrayOutputStream()
        val directory = ByteBuffer.allocate(count * 20)
        var offset = header
        tables.forEachIndexed { i, table ->
            directory.putInt(table.tag).putInt(offset).putInt(packed[i].size).putInt(table.data.size).putInt(table.checksum)
            body.write(packed[i])
            val pad = (4 - packed[i].size % 4) % 4
            body.write(ByteArray(pad))
            offset += packed[i].size + pad
        }
        val head = ByteBuffer.allocate(44).putInt(0x774F4646).putInt(input.getInt(0)).putInt(header + body.size()).putShort(count.toShort())
        val out = ByteArrayOutputStream()
        out.write(head.array())
        out.write(directory.array())
        out.write(body.toByteArray())
        return out.toByteArray()
    }

    @Test
    fun woffBuildsSfntHeader() {
        val sfnt = FontEngine.decode(woff(iosevka))
        assertArrayEquals(iosevka.copyOfRange(0, 4), sfnt.copyOfRange(0, 4))
    }
}
