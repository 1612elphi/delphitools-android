package com.delphi.delphitools.tools.devtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class JwtEngineTest {
    private fun decoded(token: String) = (JwtEngine.decode(token) as JwtResult.Decoded).decoded

    @Test
    fun decodes() {
        // alg hs256, exp 1e9
        val result = decoded(
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
                ".eyJzdWIiOiIxIiwiZXhwIjoxMDAwMDAwMDAwfQ" +
                ".dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
        )
        assertEquals("HS256", result.algorithm)
        assertEquals("JWT", result.tokenType)
        assertEquals(27, result.header.byteCount)
        assertEquals(32, result.signature.byteCount)
        assertEquals(1, result.timeClaims.size)
        assertEquals("Expires", result.timeClaims[0].label)
        assertTrue(result.timeClaims[0].isPast)
        assertTrue(result.payload.pretty!!.contains("\"sub\": \"1\""))
        assertEquals(JwtState.Expired, JwtEngine.status(result, 2e9))
        assertEquals(JwtState.Valid, JwtEngine.status(result, 1.0))
    }

    @Test
    fun failures() {
        assertEquals(JwtResult.Segments(2), JwtEngine.decode("a.b"))
        assertEquals(JwtResult.Empty, JwtEngine.decode("   "))
        assertEquals(JwtSegmentError.NotBase64Url, decoded("!!!.!!!.!!!").header.error)
        // non-object, broken json, stray
        val odd = decoded("WzFd.ew.a")
        assertEquals(JwtSegmentError.NotAnObject, odd.header.error)
        assertEquals(JwtSegmentError.NotJson, odd.payload.error)
        assertEquals(JwtSegmentError.NotBase64Url, odd.signature.error)
        assertEquals(1, JwtEngine.base64UrlToData("ew==")?.size)
    }

    @Test
    fun formats() {
        assertEquals("09 Sep 2001, 01:46:40", JwtEngine.formatAbsolute(1_000_000_000.0, ZoneOffset.UTC))
        assertEquals("3" to "d", JwtEngine.span(86_400.0 * 3 + 60))
        assertEquals("59", JwtEngine.span(59.0).first)
    }

    @Test
    fun json() {
        val value = Json.parse("""{"b":1,"a":[1,2],"big":12345678901234567890}""")
        assertNotNull(value)
        assertTrue(Json.format(value!!).startsWith("{\n  \"b\": 1,"))
        assertTrue(Json.format(value).contains("12345678901234567890"))
        assertEquals("{\n  \"a\": {},\n  \"b\": []\n}", Json.format(Json.parse("""{"a":{},"b":[]}""")!!))
        assertEquals("\"aé\\n😀😀é/\"", Json.format(Json.parse(""""aé\n😀\ud83d\ude00\u00e9\/"""")!!))
        for (invalid in listOf("[1,]", "[01]", "[1.]", "[-]", "[1e]", "[٣]", "[1] x", "\"\\ud83d\"", "\"\\ude00\"", "\"\\x\"", "\"a\nb\"", "nul", "")) {
            assertNull(invalid, Json.parse(invalid))
        }
        for (valid in listOf("-0.5e+10", " [ ] ", "\"\"", "[1E5,0,-0]")) assertNotNull(valid, Json.parse(valid))
        assertNotNull(Json.parse("[".repeat(128) + "]".repeat(128)))
        assertNull(Json.parse("[".repeat(100_000) + "]".repeat(100_000)))
    }
}
