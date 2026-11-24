package com.delphi.delphitools.tools.images

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageAtlasEngineTest {
    private fun facts(tags: Map<String, String>, xmp: String? = null) = AtlasFacts(
        filename = "photo.jpg", byteCount = 2_000_000, sizeLabel = "2 MB", mimeType = "image/jpeg",
        width = 4032, height = 3024, colourModel = "RGB", depth = 8, hasAlpha = false, profile = null, tags = tags, xmp = xmp,
    )

    @Test
    fun labels() {
        assertEquals("F-Number", ImageAtlasReport.prettyLabel("FNumber"))
        assertEquals("ISO Speed Ratings", ImageAtlasReport.prettyLabel("ISOSpeedRatings"))
        assertEquals("Date Taken", ImageAtlasReport.prettyLabel("DateTimeOriginal"))
        assertEquals("35mm Equivalent", ImageAtlasReport.prettyLabel("FocalLenIn35mmFilm"))
        assertEquals("Lens Model", ImageAtlasReport.prettyLabel("LensModel"))
    }

    @Test
    fun values() {
        assertEquals("0.01 (1/100s)", ImageAtlasReport.prettyValue("exposuretime", 0.01))
        assertEquals("f/2.8", ImageAtlasReport.prettyValue("fnumber", 2.8))
        assertEquals("Spot", ImageAtlasReport.prettyValue("meteringmode", 3.0))
        assertEquals("ISO 400", ImageAtlasReport.prettyValue("isospeedratings", 400.0))
        assertEquals("Infinity", ImageAtlasReport.prettyValue("subjectdistance", 4_294_967_295.0))
        assertEquals("24, 70, 2.8, 2.8", ImageAtlasReport.prettyValue("lensspecification", ImageAtlasReport.raw("LensSpecification", "24/1,70/1,28/10,28/10")))
        assertEquals("12:30:05", ImageAtlasReport.raw("GPSTimeStamp", "12/1,30/1,5/1"))
    }

    @Test
    fun sections() {
        val atlas = ImageAtlasReport.build(
            facts(
                mapOf(
                    "Make" to "Apple", "Model" to "iPhone 15", "FNumber" to "178/100", "DateTimeOriginal" to "2024:05:01 10:00:00",
                    "GPSLatitude" to "51/1,30/1,0/1", "GPSLatitudeRef" to "S", "GPSLongitude" to "0/1,7/1,30/1", "GPSLongitudeRef" to "W",
                    "BodySerialNumber" to "123", "PhotographicSensitivity" to "100", "GPSImgDirection" to "90/1",
                ),
            ),
        )
        assertTrue(atlas.readable)
        assertEquals(listOf("File", "Colour", "Camera", "Capture", "Location", "Dates"), atlas.sections.map { it.title })
        assertEquals(listOf("Make", "Model"), atlas.sections.first { it.title == "Camera" }.rows.map { it.label })
        assertTrue(atlas.sections.first { it.title == "Capture" }.rows.any { it.value == "ISO 100" })
        assertEquals("JPEG", atlas.format)
        assertEquals(90.0, atlas.heading!!, 1e-9)
        val (lat, lon) = atlas.coordinate!!
        assertEquals(-51.5, lat, 1e-9)
        assertEquals(-0.125, lon, 1e-9)
        assertFalse(atlas.sections.any { s -> s.rows.any { "Serial" in it.label } })
        assertTrue(ImageAtlasReport.reportText(atlas).contains("GPS: -51.500000, -0.125000"))
        assertFalse(ImageAtlasReport.build(null).readable)
    }

    @Test
    fun xmp() {
        val packet = """<x:xmpmeta><rdf:RDF><rdf:Description xmp:CreatorTool="Lightroom" exif:FNumber="2"><dc:creator><rdf:Seq><rdf:li>Ada</rdf:li><rdf:li>Bea</rdf:li></rdf:Seq></dc:creator><photoshop:City>Kiel &amp; Co</photoshop:City></rdf:Description></rdf:RDF></x:xmpmeta>"""
        val rows = ImageAtlasReport.xmpRows(packet).associate { it.label to it.value }
        assertEquals("Lightroom", rows["Creator Tool"])
        assertEquals("Ada, Bea", rows["Creator"])
        assertEquals("Kiel & Co", rows["City"])
        assertNull(rows["F-Number"])
        val data = ("junk" + packet + "junk").toByteArray()
        assertEquals(packet, ImageAtlasReport.xmpPacket(data))
    }

    @Test
    fun scan() {
        val pixels = intArrayOf(0xFFFF0000.toInt(), 0xFF000000.toInt(), 0x00000000, 0x80FFFFFF.toInt())
        val scan = ImageColourScan.scan(pixels, hasAlpha = true)
        assertEquals(2, scan.histogram[0][255])
        assertEquals(0.5, scan.transparent!!, 1e-9)
        assertEquals(0.0, scan.outsideSRGB, 1e-9)
        assertEquals(1.0, scan.gamutOverSRGB!!, 1e-9)
        assertNotNull(scan.chroma)
        val (x, y) = ImageColourScan.chromaticity(1.0, 1.0, 1.0)!!
        assertEquals(0.3127, x, 0.001)
        assertEquals(0.3290, y, 0.001)
        // linear srgb red pushed past the gamut
        val wide = ImageColourScan.scan(intArrayOf(0xFFFF0000.toInt()), false, toLinearSrgb = doubleArrayOf(1.2, -0.2, 0.0, -0.04, 1.04, 0.0, -0.02, -0.08, 1.1))
        assertEquals(1.0, wide.outsideSRGB, 1e-9)
        assertNull(wide.transparent)
        assertEquals(30, ImageColourScan.peak(arrayOf(IntArray(256) { if (it == 0) 99 else 30 })))
        assertEquals("4:3  12 MP", imageAtlasCaption(4032, 3024))
    }
}
