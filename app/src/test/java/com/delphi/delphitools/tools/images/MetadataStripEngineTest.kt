package com.delphi.delphitools.tools.images

import com.delphi.delphitools.tools.images.MetadataScan.Block
import com.delphi.delphitools.tools.images.MetadataScan.Lamp
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataStripEngineTest {
    private val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private fun chunk(type: String, body: ByteArray = ByteArray(0)) =
        byteArrayOf(0, 0, 0, body.size.toByte()) + type.toByteArray() + body + byteArrayOf(0, 0, 0, 0)

    @Test
    fun pngChunks() {
        val header = chunk("IHDR", ByteArray(13) { 1 })
        val png = signature + header + chunk("tEXt", byteArrayOf(65)) + chunk("IDAT", byteArrayOf(7)) + chunk("eXIf", byteArrayOf(1)) + chunk("IEND")
        val stripped = PngChunkStrip.strip(png)!!
        assertEquals(listOf("tEXt", "eXIf"), stripped.removed)
        assertArrayEquals(signature + header + chunk("IDAT", byteArrayOf(7)) + chunk("IEND"), stripped.data)
        assertNull(PngChunkStrip.strip(png.copyOf(40)))
        assertNull(PngChunkStrip.strip(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
    }

    @Test
    fun lampsAndMap() {
        val found = MetadataScan(setOf(Block.Exif, Block.Gps, Block.Tiff))
        val lamps = MetadataScan.lamps(found, MetadataScan())
        assertEquals(Lamp.Struck, lamps[Block.Gps.ordinal])
        assertEquals(Lamp.Off, lamps[Block.C2pa.ordinal])
        assertEquals(Lamp.On, MetadataScan.lamps(found, null)[Block.Gps.ordinal])
        assertEquals(180f to 80f, WorldMap.point(0.0, 0.0, 360f, 140f))
        assertEquals(360f to 140f, WorldMap.point(-89.0, 180.0, 360f, 140f))
    }

    private val tags = mapOf(
        "GPSLatitude" to "48/1,51/1,3000/100",
        "GPSLatitudeRef" to "N",
        "GPSLongitude" to "2/1,21/1,0/1",
        "GPSLongitudeRef" to "W",
        "GPSAltitude" to "355/10",
        "GPSAltitudeRef" to "1",
        "Make" to "Apple",
        "Model" to "iPhone",
        "DateTimeOriginal" to "2024:01:02 03:04:05",
        MetadataStripEngine.MAKER_NOTE to "",
    )

    @Test
    fun report() {
        val data = "x <x:xmpmeta y".toByteArray()
        val report = MetadataStripEngine.report(data, tags, null)
        assertTrue(report.readable)
        assertEquals(
            listOf(
                MetadataEntry("GPS coordinates", "48.858333° N, 2.350000° W · alt -36 m"),
                MetadataEntry("Camera", "Apple iPhone"),
                MetadataEntry("Taken", "2024:01:02 03:04:05"),
                MetadataEntry("MakerNote", "vendor data present"),
                MetadataEntry("XMP packet", "present"),
            ),
            report.entries,
        )
        assertFalse(MetadataStripEngine.report(data, null, null).readable)
    }

    @Test
    fun scan() {
        val scan = MetadataStripEngine.scan("acsp jumb c2pa".toByteArray(), tags)
        assertEquals(setOf(Block.Exif, Block.Gps, Block.Tiff, Block.Maker, Block.Icc, Block.C2pa), scan.blocks)
        assertEquals(48.858333, scan.location!!.latitude, 1e-6)
        assertEquals(-2.35, scan.location!!.longitude, 1e-9)
        assertEquals("48.8583° N, 2.3500° W", MetadataScan.label(scan.location!!))
        assertEquals(listOf("EXIF", "GPS", "TIFF", "MakerNote"), MetadataStripEngine.removed(scan))
    }

    @Test
    fun userComment() {
        assertEquals("hello", MetadataStripEngine.userComment("ASCII\u0000\u0000\u0000hello\u0000".toByteArray()))
        assertEquals("", MetadataStripEngine.userComment(ByteArray(8)))
    }
}
