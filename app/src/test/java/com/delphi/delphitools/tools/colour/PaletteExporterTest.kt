package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.util.zip.ZipInputStream

class PaletteExporterTest {
    @Test
    fun aseHeaderAndLength() {
        val data = PaletteExporter.ase("Hi", listOf(Rgb.black, Rgb.white)) { "x" }
        assertEquals("ASEF", String(data, 0, 4))
        assertEquals(4, ByteBuffer.wrap(data, 8, 4).int)
        // header, group, colours, end
        assertEquals(12 + 14 + 60 + 6, data.size)
    }

    @Test
    fun procreateIsAZipOfJson() {
        val zip = ZipInputStream(PaletteExporter.procreate("A \"b\"", listOf(Rgb(1.0, 0.0, 0.0))).inputStream())
        assertEquals("Swatches.json", zip.nextEntry!!.name)
        val json = zip.readBytes().decodeToString()
        assertTrue(json, json.startsWith("[{\"name\":\"A \\\"b\\\"\",\"swatches\":[{\"hue\":0.0,"))
    }

    @Test
    fun css() {
        assertEquals("/* P */\n--palette-1: #000000;", PaletteExporter.css("P", listOf(Rgb.black)))
    }
}
