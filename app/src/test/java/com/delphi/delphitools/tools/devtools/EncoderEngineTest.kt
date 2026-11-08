package com.delphi.delphitools.tools.devtools

import com.delphi.delphitools.tools.devtools.BitRegroup.Slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class EncoderEngineTest {
    @Test
    fun url() {
        assertEquals("a%20b%E2%82%AC%26", EncoderEngine.urlEncode("a b€&"))
        assertEquals("a b€", EncoderEngine.urlDecode("a%20b%E2%82%AC").getOrNull())
        assertTrue(EncoderEngine.urlDecode("100%").isFailure)
        assertTrue(EncoderEngine.urlDecode("%FF").isFailure)
        val decoded = EncoderEngine.urlDecodePairs("x%E2%82%ACy")
        assertEquals(3, decoded.size)
        assertEquals(SymbolPair("%E2%82%AC", "€"), decoded[1])
        assertEquals(listOf(false, true), EncoderEngine.urlEncodePairs("a€").map { it.changes })
        assertEquals(SymbolPair.Newline, EncoderEngine.urlEncodePairs("a\nb")[1])
    }

    @Test
    fun base64() {
        assertEquals("TWFu", EncoderEngine.base64Encode("Man"))
        assertEquals("Man", EncoderEngine.base64Decode("TWFu").getOrNull())
        assertEquals("Ma", EncoderEngine.base64Decode("TWE").getOrNull())
        assertEquals(String(byteArrayOf(0xFF.toByte(), 0xEF.toByte()), Charsets.UTF_8), EncoderEngine.base64Decode("_-8").getOrNull())
        assertTrue(EncoderEngine.base64Decode("T").isFailure)
        assertEquals("", EncoderEngine.base64Decode(" \n").getOrNull())
    }

    @Test
    fun digests() {
        val abc = EncoderEngine.digests("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", EncoderEngine.hex(abc.getValue(HashAlgorithm.Sha256)))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", EncoderEngine.hex(abc.getValue(HashAlgorithm.Md5)))
        for (algorithm in HashAlgorithm.entries) assertEquals(algorithm.bits / 8, abc.getValue(algorithm).size)
    }

    @Test
    fun regroup() {
        val ma = BitRegroup.encoding("Ma".toByteArray()).all
        assertEquals(1, ma.size)
        assertEquals(listOf("T", "W", "E", "="), ma[0].bottom.map { it.label })
        assertEquals(2, ma[0].slots.count { it == Slot.Pad })
        assertEquals(6, ma[0].slots.count { it == Slot.Absent })
        assertEquals(listOf("M", "a", ""), ma[0].top.map { it.label })
        assertTrue(ma[0].top[2].faint)
        val back = BitRegroup.decoding("TWE=".toList()).all
        assertEquals(1, back.size)
        assertEquals(listOf("M", "a", ""), back[0].bottom.map { it.label })
        assertEquals(2, back[0].slots.count { it == Slot.Pad })
        // url-safe, unpadded
        assertEquals("FF", BitRegroup.decoding("_w".toList()).all[0].bottom[0].label)
        assertEquals(listOf("é", "", "␣"), BitRegroup.labels("é ".toByteArray()))
        // stops at foreign character
        assertEquals(2, BitRegroup.decoding("TW!E".toList()).all[0].top.count { !it.faint })
        assertEquals(2, BitRegroup.decoding("TWFu====".toList()).all.size)
        // range equals whole's tail
        val text = "a€😀b€é".toByteArray()
        val whole = BitRegroup.encoding(text).all
        assertEquals(whole.drop(2), BitRegroup.encoding(text).groups(2 until whole.size))
        val coded = Base64.getEncoder().encodeToString(text).toList()
        val decoded = BitRegroup.decoding(coded).all
        assertEquals(decoded.drop(1), BitRegroup.decoding(coded).groups(1 until decoded.size))
        assertEquals(whole.map { it.top }, decoded.map { it.bottom })
    }
}

class DigestGridTest {
    @Test
    fun grid() {
        val wide = digestGrid(128, 640f, 160f)
        assertEquals(32, wide.first)
        assertEquals(20f, wide.second.height)
        assertEquals(androidx.compose.ui.geometry.Size(20f, 20f), digestGrid(64, 320f, 200f).second)
        assertEquals(10f, digestGrid(128, 320f, 80f).second.height)
    }
}
