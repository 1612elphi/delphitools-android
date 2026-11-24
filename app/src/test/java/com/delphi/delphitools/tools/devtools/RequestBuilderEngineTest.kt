package com.delphi.delphitools.tools.devtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestBuilderEngineTest {
    private val e = RequestBuilderEngine

    @Test
    fun composes() {
        val spec = RequestSpec(
            method = "POST",
            url = "https://api.example.com/path?a=1",
            params = listOf(RequestPair("q", "a b&c"), RequestPair("  ", "skip")),
            headers = listOf(RequestPair(" Content-Type ", "application/json")),
            body = "{\"x\":1}",
        )
        assertEquals("https://api.example.com/path?a=1&q=a%20b%26c", e.fullUrl(spec))
        assertEquals(
            "curl -X POST 'https://api.example.com/path?a=1&q=a%20b%26c' \\\n  -H 'Content-Type: application/json' \\\n  --data-raw '{\"x\":1}'",
            e.toCurl(spec),
        )
        assertEquals(
            "POST /path?a=1&q=a%20b%26c HTTP/1.1\nHost: api.example.com\nContent-Type: application/json\nContent-Length: 7\n\n{\"x\":1}",
            e.toHttp(spec),
        )

        val bare = RequestSpec(url = "https://example.com")
        assertEquals("curl 'https://example.com'", e.toCurl(bare))
        assertEquals("GET / HTTP/1.1\nHost: example.com\n", e.toHttp(bare))

        val head = RequestSpec(method = "HEAD", url = "https://example.com/x", headers = listOf(RequestPair("Host", "other.example")))
        assertTrue(e.toCurl(head).startsWith("curl -I "))
        assertFalse("Host: example.com" in e.toHttp(head))

        assertTrue("'https://x.test/it'\\''s'" in e.toCurl(RequestSpec(url = "https://x.test/it's")))

        val awkward = RequestSpec(method = "POST", url = "http://[::1]:8080/a", body = "é'\n$(id)`x`")
        assertTrue(e.toCurl(awkward).endsWith("--data-raw 'é'\\''\n$(id)`x`'"))
        assertTrue("Host: [::1]:8080\nContent-Length: 12\n" in e.toHttp(awkward))
        assertEquals("GET / HTTP/1.1\n", e.toHttp(RequestSpec()))
    }

    @Test
    fun wire() {
        // RequestWire.selfCheck
        val (lines, bytes) = e.wireLines("POST /a HTTP/1.1\nHost: x.test\nContent-Length: 7\n\n{\"x\":1}")
        assertEquals(
            listOf(RequestPart.Start, RequestPart.Headers, RequestPart.Headers, RequestPart.Headers, RequestPart.Body),
            lines.map { it.part },
        )
        assertEquals(listOf(18, 14 + 19 + 2, 7), bytes)
        assertEquals(18, lines[1].offset)
        assertTrue(lines[3].text.isEmpty())
        assertEquals(18 + 35, lines[4].offset)
        val bare = e.wireLines("GET / HTTP/1.1\n")
        assertEquals(2, bare.first.size)
        assertEquals(listOf(16, 2, 0), bare.second)
        val multi = e.wireLines("PUT / HTTP/1.1\n\na\nb")
        assertEquals(listOf("\n", ""), multi.first.takeLast(2).map { it.ending })
        assertEquals(3, multi.second[2])
    }
}
