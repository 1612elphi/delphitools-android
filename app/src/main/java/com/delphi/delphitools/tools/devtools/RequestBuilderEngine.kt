package com.delphi.delphitools.tools.devtools

import java.net.URI
import java.net.URISyntaxException

data class RequestPair(val key: String = "", val value: String = "")

data class RequestSpec(
    val method: String = "GET",
    val url: String = "",
    val params: List<RequestPair> = emptyList(),
    val headers: List<RequestPair> = emptyList(),
    val body: String = "",
)

enum class RequestPart { Start, Headers, Body }

/** [ending]: CRLF in the head, LF between body lines. */
data class RequestLine(val part: RequestPart, val offset: Int, val text: String, val ending: String)

/** dt-ios RequestBuilderEngine: composes request text only. Nothing here opens a connection. */
object RequestBuilderEngine {
    val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

    // matches encodeURIComponent
    private const val COMPONENT_ALLOWED = "-_.!~*'()"

    private fun String.trimSpaces() = trim { it == ' ' || it == '\t' }

    private fun live(pairs: List<RequestPair>) = pairs.filter { it.key.trimSpaces().isNotEmpty() }

    private fun hasHeader(headers: List<RequestPair>, name: String) = headers.any { it.key.trimSpaces().lowercase() == name }

    private fun encode(text: String): String {
        val out = StringBuilder()
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            val value = byte.toInt() and 0xFF
            val char = value.toChar()
            val plain = char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in COMPONENT_ALLOWED
            if (plain) out.append(char) else out.append("%%%02X".format(value))
        }
        return out.toString()
    }

    // escape posix quotes
    private fun quote(text: String) = "'" + text.replace("'", "'\\''") + "'"

    fun fullUrl(spec: RequestSpec): String {
        val extra = live(spec.params).joinToString("&") { "${encode(it.key.trimSpaces())}=${encode(it.value)}" }
        if (extra.isEmpty()) return spec.url
        return spec.url + (if ('?' in spec.url) "&" else "?") + extra
    }

    fun toCurl(spec: RequestSpec): String {
        val url = quote(fullUrl(spec))
        val parts = mutableListOf(
            when (spec.method) {
                "GET" -> "curl $url"
                "HEAD" -> "curl -I $url"
                else -> "curl -X ${spec.method} $url"
            },
        )
        for (pair in live(spec.headers)) parts += "-H " + quote("${pair.key.trimSpaces()}: ${pair.value}")
        if (spec.body.isNotEmpty()) parts += "--data-raw " + quote(spec.body)
        return parts.joinToString(" \\\n  ")
    }

    fun toHttp(spec: RequestSpec): String {
        val full = fullUrl(spec)
        var host = ""
        var target = full.ifEmpty { "/" }
        // ponytail: java.net.URI rejects what iOS URL(string:) percent-encodes (a space); such a URL goes out as typed, with no Host
        val url = try {
            URI(full)
        } catch (_: URISyntaxException) {
            null
        }
        val urlHost = url?.host
        if (url != null && urlHost != null && url.scheme != null) {
            val bracketed = if (':' in urlHost && !urlHost.startsWith("[")) "[$urlHost]" else urlHost
            host = if (url.port >= 0) "$bracketed:${url.port}" else bracketed
            val path = url.rawPath.orEmpty()
            target = path.ifEmpty { "/" } + (url.rawQuery?.let { "?$it" } ?: "")
        }
        val headers = live(spec.headers)
        val lines = mutableListOf("${spec.method} ${target.ifEmpty { "/" }} HTTP/1.1")
        if (host.isNotEmpty() && !hasHeader(headers, "host")) lines += "Host: $host"
        for (pair in headers) lines += "${pair.key.trimSpaces()}: ${pair.value}"
        if (spec.body.isNotEmpty() && !hasHeader(headers, "content-length")) {
            lines += "Content-Length: ${spec.body.toByteArray(Charsets.UTF_8).size}"
        }
        return lines.joinToString("\n") + if (spec.body.isEmpty()) "\n" else "\n\n${spec.body}"
    }

    private fun utf8(text: String) = text.toByteArray(Charsets.UTF_8).size

    /** dt-ios RequestWire.lines: the message as sent, CRLF after each head line; bytes per part. */
    fun wireLines(message: String): Pair<List<RequestLine>, List<Int>> {
        val split = message.indexOf("\n\n")
        val head = if (split >= 0) message.substring(0, split) else message.removeSuffix("\n")
        val body = if (split >= 0) message.substring(split + 2) else ""
        val lines = mutableListOf<RequestLine>()
        val bytes = intArrayOf(0, 0, 0)
        var offset = 0
        head.split("\n").forEachIndexed { index, text ->
            val part = if (index == 0) RequestPart.Start else RequestPart.Headers
            lines += RequestLine(part, offset, text, "\r\n")
            val size = utf8(text) + 2
            bytes[part.ordinal] += size
            offset += size
        }
        lines += RequestLine(RequestPart.Headers, offset, "", "\r\n")
        bytes[RequestPart.Headers.ordinal] += 2
        offset += 2
        if (body.isNotEmpty()) {
            val parts = body.split("\n")
            parts.forEachIndexed { index, text ->
                val ending = if (index < parts.size - 1) "\n" else ""
                lines += RequestLine(RequestPart.Body, offset, text, ending)
                offset += utf8(text) + ending.length
            }
            bytes[RequestPart.Body.ordinal] = utf8(body)
        }
        return lines to bytes.toList()
    }
}
