package com.delphi.delphitools.tools.devtools

import com.delphi.delphitools.colour.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class QrEngineTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun event() {
        val start = ZonedDateTime.of(2026, 11, 8, 9, 30, 0, 0, berlin).toInstant()
        var event = QrEvent(notes = "line one\nline two", start = start, end = start.plusSeconds(5400))
        val title = "Launch; party, v2"
        assertEquals(
            "BEGIN:VEVENT\nSUMMARY:Launch\\; party\\, v2\nDTSTART:20261108T083000Z\nDTEND:20261108T100000Z\nDESCRIPTION:line one\\nline two\nEND:VEVENT",
            event.vevent(title, berlin),
        )
        event = event.copy(allDay = true, notes = "", end = start)
        assertTrue(event.vevent(title, berlin).contains("DTSTART;VALUE=DATE:20261108\nDTEND;VALUE=DATE:20261109\n"))
        event = event.copy(end = start.minusSeconds(86400))
        assertTrue(event.endsBeforeStart(berlin))
        assertEquals("", event.vevent(title, berlin))
        event = event.copy(allDay = false, end = start.minusSeconds(60))
        assertTrue(event.endsBeforeStart(berlin))
        assertEquals("", event.copy(end = start).vevent(" ", berlin))
    }

    @Test
    fun payloads() {
        val event = QrEvent.startingAfter(java.time.Instant.EPOCH)
        fun payload(type: QrContentType, content: String, password: String = "", encryption: String = "WPA") =
            QrEngine.payload(type, content, password, encryption, false, "", event)
        assertEquals("https://example.com", payload(QrContentType.Url, " example.com "))
        assertEquals("ftp://x", payload(QrContentType.Url, "ftp://x"))
        assertEquals("mailto:a@b.c", payload(QrContentType.Email, "a@b.c"))
        assertEquals("", payload(QrContentType.Wifi, "Home"))
        assertEquals("WIFI:T:WPA;S:My\\;Net;P:p\\\"w;;", payload(QrContentType.Wifi, "My;Net", "p\"w"))
        assertEquals("WIFI:T:nopass;S:Cafe;;", payload(QrContentType.Wifi, "Cafe", encryption = "None"))
        assertEquals("sms:123?body=a%26b%20%23c%3F", QrEngine.payload(QrContentType.Sms, "123", "", "WPA", false, "a&b #c?", event))
        assertEquals(listOf("Content: hi"), QrEngine.infoLines(QrContentType.Text, "hi", "", "WPA", false, event))
    }

    @Test
    fun matrix() {
        // 23 bytes at h: v3
        assertEquals(21, QrEngine.matrix("hello", QrCorrection.Quartile)?.size)
        assertEquals(29, QrEngine.matrix("https://delphi.tools/qr", QrCorrection.High)?.size)
        assertNull(QrEngine.matrix("", QrCorrection.Low))
        assertNull(QrEngine.matrix("x".repeat(4000), QrCorrection.High))
        val finder = QrEngine.matrix("hello", QrCorrection.Low)!!
        assertTrue((0 until 7).all { finder[0][it] && finder[6][it] })
    }

    @Test
    fun logoAndSheet() {
        assertEquals(6, QrEngine.largestLogo(21))
        assertEquals(QrClearing(8, 16, 8, 16), QrEngine.logoClearing(25, 1f, 7))
        val frame = QrEngine.logoFrame(QrClearing(8, 16, 8, 16), 2f)
        assertEquals(7f, frame.width)
        assertEquals(3.5f, frame.height)
        val matrix = QrEngine.matrix("hello", QrCorrection.Quartile)!!
        val look = QrEngine.Look(QrDotStyle.Rounded, QrEyeStyle.Leaf, QrPupilStyle.Circle, Rgb.black, Rgb.white, 600, null, 9, emptyList())
        val (sheet, pixels) = QrEngine.sheet(matrix, look) { text, size -> text.length * size * 0.5f }
        assertEquals(20f, pixels)
        assertEquals(600 to 600, sheet.pixelSize(pixels))
        assertTrue(!sheet.modules[0][0])
        assertEquals(6, sheet.marks.size)
        assertTrue(sheet.svg(pixels).contains("fill-rule=\"evenodd\""))
    }

    @Test
    fun wraps() {
        val lines = QrEngine.wrap("Content: https://example.com/a/very/long/path", 10f) { it.length.toFloat() }
        assertTrue(lines.all { it.length <= 10 })
        assertEquals("Content:", lines[0])
    }

    @Test
    fun colourSteps() {
        assertFalse(QrEngine.continuesColour(null, true, 0))
        assertTrue(QrEngine.continuesColour(true to 0L, true, 999))
        assertFalse(QrEngine.continuesColour(true to 0L, true, 1000))
        assertFalse(QrEngine.continuesColour(true to 0L, false, 10))
    }
}
