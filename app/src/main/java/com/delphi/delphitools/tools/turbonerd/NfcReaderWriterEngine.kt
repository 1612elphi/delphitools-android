package com.delphi.delphitools.tools.turbonerd

import java.net.URI

/** One NDEF record's fields as bytes; tnf is the 3-bit type name format. */
class NdefRecordBytes(val tnf: Int, val type: List<Byte>, val identifier: List<Byte> = emptyList(), val payload: List<Byte>) {
    override fun equals(other: Any?) =
        other is NdefRecordBytes && tnf == other.tnf && type == other.type && identifier == other.identifier && payload == other.payload

    override fun hashCode() = listOf(tnf, type, identifier, payload).hashCode()
}

enum class NdefRole { Frame, Header, Length, Type, Identifier, Payload }

data class NdefSegment(val role: NdefRole, val bytes: List<Byte>)

/** A parsed record for display. */
data class NdefShown(val typeDescription: String, val payload: String, val raw: NdefRecordBytes)

enum class NfcContentType(val label: String) { Url("URL"), Text("Text"), VCard("vCard"), Wifi("Wi-Fi") }

enum class WifiSecurity(val label: String, val code: String) { Wpa2("WPA2", "WPA2"), Wpa3("WPA3", "SAE"), Wep("WEP", "WEP"), Open("Open", "OPEN") }

/** Fields of the Write form. */
data class NfcWriteForm(
    val contentType: NfcContentType = NfcContentType.Url,
    val url: String = "",
    val text: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val organisation: String = "",
    val ssid: String = "",
    val password: String = "",
    val security: WifiSecurity = WifiSecurity.Wpa2,
)

/** dt-ios NDEFBytes and NFCManager's parsing: Type 2 Tag 2.3 framing, NDEF 1.0 records, URI and Text RTDs. */
object NfcReaderWriterEngine {
    const val TNF_WELL_KNOWN = 1
    const val TNF_MEDIA = 2
    const val TNF_ABSOLUTE_URI = 3

    /** NXP NTAG21x user memory. */
    val tags = listOf("NTAG213" to 144, "NTAG215" to 504, "NTAG216" to 888)

    /** URI RTD 3.2.2 prefix codes. */
    val uriPrefixes = listOf(
        "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:", "ftp://anonymous:anonymous@",
        "ftp://ftp.", "ftps://", "sftp://", "smb://", "nfs://", "ftp://", "dav://", "news:",
        "telnet://", "imap:", "rtsp://", "urn:", "pop:", "sip:", "sips:", "tftp:",
        "btspp://", "btl2cap://", "btgoep://", "tcpobex://", "irdaobex://", "file://",
        "urn:epc:id:", "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:",
    )

    fun segments(records: List<NdefRecordBytes>): List<NdefSegment> {
        val body = mutableListOf<NdefSegment>()
        records.forEachIndexed { index, record ->
            val short = record.payload.size < 256
            var header = record.tnf and 0x07
            if (index == 0) header = header or 0x80
            if (index == records.size - 1) header = header or 0x40
            if (short) header = header or 0x10
            if (record.identifier.isNotEmpty()) header = header or 0x08
            val count = record.payload.size
            val lengths = mutableListOf(record.type.size.toByte())
            lengths += if (short) listOf(count.toByte()) else listOf((count ushr 24).toByte(), (count ushr 16).toByte(), (count ushr 8).toByte(), count.toByte())
            if (record.identifier.isNotEmpty()) lengths += record.identifier.size.toByte()
            body += NdefSegment(NdefRole.Header, listOf(header.toByte()))
            body += NdefSegment(NdefRole.Length, lengths)
            if (record.type.isNotEmpty()) body += NdefSegment(NdefRole.Type, record.type)
            if (record.identifier.isNotEmpty()) body += NdefSegment(NdefRole.Identifier, record.identifier)
            if (record.payload.isNotEmpty()) body += NdefSegment(NdefRole.Payload, record.payload)
        }
        val length = body.sumOf { it.bytes.size }
        val tlv = if (length < 0xff) listOf(0x03, length) else listOf(0x03, 0xff, length ushr 8 and 0xff, length and 0xff)
        return listOf(NdefSegment(NdefRole.Frame, tlv.map { it.toByte() })) + body + NdefSegment(NdefRole.Frame, listOf(0xfe.toByte()))
    }

    fun count(segments: List<NdefSegment>) = segments.sumOf { it.bytes.size }

    fun hex(bytes: List<Byte>) = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xff) }

    fun hexLines(bytes: List<Byte>) = bytes.chunked(16).joinToString("\n") { hex(it) }

    private fun utf8(bytes: List<Byte>): String? {
        val decoder = Charsets.UTF_8.newDecoder()
        return try {
            decoder.decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            null
        }
    }

    fun describe(record: NdefRecordBytes): NdefShown {
        val hex = hex(record.payload)
        val (type, payload) = when (record.tnf) {
            TNF_WELL_KNOWN -> {
                val typeString = utf8(record.type).orEmpty()
                when (typeString) {
                    "U" -> "URI" to parseUri(record.payload)
                    "T" -> "Text" to parseText(record.payload)
                    else -> "Well Known ($typeString)" to (utf8(record.payload) ?: hex)
                }
            }
            TNF_MEDIA -> "Media (${utf8(record.type) ?: "unknown"})" to (utf8(record.payload) ?: hex)
            TNF_ABSOLUTE_URI -> "Absolute URI" to (utf8(record.payload) ?: hex)
            else -> record.tnf.toString() to hex
        }
        return NdefShown(type, payload, record)
    }

    fun parseUri(payload: List<Byte>): String {
        val first = payload.firstOrNull() ?: return ""
        return uriPrefixes.getOrElse(first.toInt() and 0xff) { "" } + utf8(payload.drop(1)).orEmpty()
    }

    fun parseText(payload: List<Byte>): String {
        if (payload.size <= 1) return ""
        val status = payload[0].toInt() and 0xff
        val languageLength = status and 0x3f
        if (payload.size <= 1 + languageLength) return ""
        val text = payload.drop(1 + languageLength).toByteArray()
        // nfc forum text rtd 3.2.1
        return if (status and 0x80 == 0) utf8(text.toList()).orEmpty() else String(text, Charsets.UTF_16)
    }

    fun uriRecord(uri: String): NdefRecordBytes {
        val code = uriPrefixes.indices.drop(1).filter { uri.startsWith(uriPrefixes[it]) }.maxByOrNull { uriPrefixes[it].length } ?: 0
        val rest = uri.substring(uriPrefixes[code].length).toByteArray(Charsets.UTF_8)
        return NdefRecordBytes(TNF_WELL_KNOWN, listOf('U'.code.toByte()), payload = listOf(code.toByte()) + rest.toList())
    }

    fun textRecord(text: String, language: String): NdefRecordBytes {
        val code = language.toByteArray(Charsets.US_ASCII).take(0x3f)
        return NdefRecordBytes(TNF_WELL_KNOWN, listOf('T'.code.toByte()), payload = listOf(code.size.toByte()) + code + text.toByteArray(Charsets.UTF_8).toList())
    }

    private fun mediaRecord(type: String, body: String) =
        NdefRecordBytes(TNF_MEDIA, type.toByteArray(Charsets.US_ASCII).toList(), payload = body.toByteArray(Charsets.UTF_8).toList())

    /** The form's record, or null when a required field is empty. */
    fun build(form: NfcWriteForm, language: String): NdefRecordBytes? = when (form.contentType) {
        NfcContentType.Url -> form.url.trim().takeIf { it.isNotEmpty() && runCatching { URI(it) }.isSuccess }?.let(::uriRecord)
        NfcContentType.Text -> form.text.trim().takeIf { it.isNotEmpty() }?.let { textRecord(it, language) }
        NfcContentType.VCard -> form.name.trim().takeIf { it.isNotEmpty() }?.let { name ->
            val lines = mutableListOf("BEGIN:VCARD", "VERSION:3.0", "FN:$name")
            form.phone.trim().takeIf { it.isNotEmpty() }?.let { lines += "TEL:$it" }
            form.email.trim().takeIf { it.isNotEmpty() }?.let { lines += "EMAIL:$it" }
            form.organisation.trim().takeIf { it.isNotEmpty() }?.let { lines += "ORG:$it" }
            lines += "END:VCARD"
            mediaRecord("text/vcard", lines.joinToString("\r\n"))
        }
        // ponytail: text payload under the WSC type, as dt-ios writes it; binary WSC TLVs if Android's Wi-Fi tag reader must accept it
        NfcContentType.Wifi -> form.ssid.trim().takeIf { it.isNotEmpty() }?.let { ssid ->
            mediaRecord("application/vnd.wfa.wsc", "WIFI:T:${form.security.code};S:$ssid;P:${form.password.trim()};;")
        }
    }
}
