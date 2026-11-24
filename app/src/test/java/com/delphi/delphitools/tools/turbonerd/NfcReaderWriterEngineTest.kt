package com.delphi.delphitools.tools.turbonerd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NfcReaderWriterEngineTest {
    private fun bytes(vararg values: Int) = values.map { it.toByte() }

    private val uri = NdefRecordBytes(1, bytes(0x55), payload = bytes(0x04) + "delphi.tools".toByteArray().toList())

    @Test
    fun shortRecordFraming() {
        val all = NfcReaderWriterEngine.segments(listOf(uri)).flatMap { it.bytes }
        assertEquals(bytes(0x03, 0x11, 0xd1, 0x01, 0x0d, 0x55, 0x04), all.take(7))
        assertEquals(0xfe.toByte(), all.last())
        assertEquals(20, all.size)
    }

    @Test
    fun messageBeginAndEndFlags() {
        val headers = NfcReaderWriterEngine.segments(listOf(uri, uri)).filter { it.role == NdefRole.Header }.map { it.bytes[0] }
        assertEquals(bytes(0x91, 0x51), headers)
    }

    @Test
    fun longFormLengths() {
        val long = NfcReaderWriterEngine.segments(listOf(NdefRecordBytes(2, bytes(0x61), payload = List(300) { 0 })))
        assertEquals(bytes(0x03, 0xff, 0x01, 0x33), long[0].bytes)
        assertEquals(bytes(0x01, 0x00, 0x00, 0x01, 0x2c), long[2].bytes)
    }

    @Test
    fun emptyMessage() {
        assertEquals(bytes(0x03, 0x00, 0xfe), NfcReaderWriterEngine.segments(emptyList()).flatMap { it.bytes })
    }

    @Test
    fun uriRoundTrip() {
        val record = NfcReaderWriterEngine.uriRecord("https://www.delphi.tools/a")
        assertEquals(0x02.toByte(), record.payload[0])
        val shown = NfcReaderWriterEngine.describe(record)
        assertEquals("URI", shown.typeDescription)
        assertEquals("https://www.delphi.tools/a", shown.payload)
        assertEquals(uri, NfcReaderWriterEngine.uriRecord("https://delphi.tools"))
    }

    @Test
    fun textRoundTrip() {
        val record = NfcReaderWriterEngine.textRecord("Grüße", "en")
        assertEquals(bytes(0x02, 'e'.code, 'n'.code), record.payload.take(3))
        assertEquals(NdefShown("Text", "Grüße", record), NfcReaderWriterEngine.describe(record))
        val utf16 = bytes(0x82, 'e'.code, 'n'.code) + "hi".toByteArray(Charsets.UTF_16).toList()
        assertEquals("hi", NfcReaderWriterEngine.parseText(utf16))
    }

    @Test
    fun otherFormats() {
        val media = NdefRecordBytes(2, "text/plain".toByteArray().toList(), payload = bytes(0xff, 0x00))
        assertEquals(NdefShown("Media (text/plain)", "FF 00", media), NfcReaderWriterEngine.describe(media))
        assertEquals("4", NfcReaderWriterEngine.describe(NdefRecordBytes(4, bytes(0x61), payload = bytes(1))).typeDescription)
    }

    @Test
    fun buildsForms() {
        assertNull(NfcReaderWriterEngine.build(NfcWriteForm(url = "  "), "en"))
        assertNull(NfcReaderWriterEngine.build(NfcWriteForm(contentType = NfcContentType.VCard, phone = "1"), "en"))
        val card = NfcReaderWriterEngine.build(NfcWriteForm(contentType = NfcContentType.VCard, name = " Ada ", email = "a@b.c"), "en")!!
        assertEquals("BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Ada\r\nEMAIL:a@b.c\r\nEND:VCARD", String(card.payload.toByteArray()))
        val wifi = NfcReaderWriterEngine.build(NfcWriteForm(contentType = NfcContentType.Wifi, ssid = "Home", password = "pw", security = WifiSecurity.Wpa3), "en")!!
        assertEquals("application/vnd.wfa.wsc", String(wifi.type.toByteArray()))
        assertEquals("WIFI:T:SAE;S:Home;P:pw;;", String(wifi.payload.toByteArray()))
    }

    @Test
    fun hexLinesWrapAtSixteen() {
        assertEquals("00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F\n10", NfcReaderWriterEngine.hexLines(List(17) { it.toByte() }))
    }
}
