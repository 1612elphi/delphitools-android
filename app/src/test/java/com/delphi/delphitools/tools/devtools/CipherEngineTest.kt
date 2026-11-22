package com.delphi.delphitools.tools.devtools

import com.delphi.delphitools.tools.devtools.CipherEngine.affine
import com.delphi.delphitools.tools.devtools.CipherEngine.classifyAndDecode
import com.delphi.delphitools.tools.devtools.CipherEngine.samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CipherEngineTest {
    @Test
    fun engine() {
        assertEquals("def", CipherEngine.caesarShift("abc", 3))
        assertEquals("THE", CipherEngine.caesarShift("AOL", -7))
        assertEquals("zyx", CipherEngine.atbash("abc"))
        assertEquals("Hi!", CipherEngine.rot47(CipherEngine.rot47("Hi!")))
        assertEquals("lxfopv", CipherEngine.vigenere("attack", "lemon", true))
        assertEquals("attack", CipherEngine.vigenere("lxfopv", "lemon", false))
        assertEquals("test", affine(affine("test", 5, 8, true), 5, 8, false))
        assertEquals(CipherFailure.AffineCoprime, runCatching { affine("x", 2, 0, false) }.exceptionOrNull().let { (it as CipherError).failure })
        assertEquals(21, CipherEngine.modInverse(5, 26))
        assertNull(CipherEngine.modInverse(2, 26))
        assertEquals("HELLO WORLD", CipherEngine.morseDecode(".... . .-.. .-.. --- / .-- --- .-. .-.. -.."))
        assertEquals("HELLO", CipherEngine.a1z26Decode("8-5-12-12-15"))
        assertEquals("HI", CipherEngine.baconDecode(CipherEngine.baconEncode("HI")))
        assertEquals("WEAREDISCOVERED", CipherEngine.railFenceDecode(CipherEngine.railFenceEncode("WEAREDISCOVERED", 3), 3))
        assertEquals("WECRLTEERDSOEEFEAOCAIVDEN", CipherEngine.railFenceEncode("WEAREDISCOVEREDFLEEATONCE", 3))
        assertEquals("crow", CipherEngine.base64Decode(EncoderEngine.base64Encode("crow")))
        assertTrue(runCatching { CipherEngine.base64Decode("_-8") }.exceptionOrNull() is CipherError)
        assertEquals("crow", CipherEngine.base32Decode(CipherEngine.base32Encode("crow")))
        assertEquals("crow", CipherEngine.hexDecode(CipherEngine.hexEncode("crow")))
        assertEquals("crow", CipherEngine.binaryDecode(CipherEngine.binaryEncode("crow")))
        assertEquals("AAAAA", CipherEngine.bacon['A'])
        assertEquals("BBAAB", CipherEngine.bacon['Z'])
        assertEquals(CipherId.Rot13, classifyAndDecode(samples[1].second).first().cipherId)
        assertTrue(classifyAndDecode("").isEmpty())
        assertEquals(CipherId.Caesar, classifyAndDecode(samples[0].second).first().cipherId)
        assertEquals(CipherId.Base64, classifyAndDecode(samples[5].second).first().cipherId)
        // scored on prefix
        val long = List(40) { samples[1].second }.joinToString(" ")
        assertEquals(long.length, classifyAndDecode(long).first { it.cipherId == CipherId.Rot13 }.output.length)
        // rails exceed characters
        assertEquals("hi", CipherEngine.railFenceEncode("hi", 900_000))
        assertEquals("hi", CipherEngine.railFenceDecode("hi", 900_000))
        assertEquals(listOf(0, 1, 2, 1, 0, 1), CipherEngine.railPattern(6, 3))
    }

    @Test
    fun pictures() {
        val params = CipherParams(vigenereKey = "lemon")
        val vigenere = cipherPairs(CipherId.Vigenere, true, params, "at tack", "lx fopv")
        assertEquals(listOf("L", "E", "M", "O", "N", "L"), vigenere.mapNotNull { it.key })
        assertEquals(CipherPair.Space, vigenere[2])
        val morse = cipherPairs(CipherId.Morse, false, params, "... --- / ...", "")
        assertEquals(4, morse.size)
        assertEquals(CipherPair.Space, morse[2])
        assertEquals(CipherFace.Text("O"), morse[1].bottom)
        assertEquals(listOf(CipherFace.Text("H"), CipherFace.Text("I")), cipherPairs(CipherId.A1z26, false, params, "8-9", "").map { it.bottom })
        assertEquals(listOf(CipherFace.Text("B"), CipherFace.Text("C")), cipherPairs(CipherId.Bacon, false, params, "AAAAB AAABA", "").map { it.bottom })
        assertEquals(listOf(true, false), cipherPairs(CipherId.Caesar, true, params, "a!", "d!").map { it.changes })

        fun encode(bytes: ByteArray, code: BitRegroup.Code) = BitRegroup.encoding(bytes, code).all
        fun decode(text: String, code: BitRegroup.Code) = BitRegroup.decoding(text.toList(), code).all
        assertEquals(listOf("M", "Y", "=", "=", "=", "=", "=", "="), encode("f".toByteArray(), BitRegroup.Code.Base32)[0].bottom.map { it.label })
        assertEquals(listOf("4", "8"), encode(byteArrayOf(0x48), BitRegroup.Code.Hex)[0].bottom.map { it.label })
        assertEquals(listOf("01001000"), encode(byteArrayOf(0x48), BitRegroup.Code.Binary)[0].bottom.map { it.label })
        assertEquals("J", decode("4A", BitRegroup.Code.Hex)[0].bottom[0].label)
        assertEquals(listOf("T", "W", "E", "="), encode("Ma".toByteArray(), BitRegroup.Code.Base64)[0].bottom.map { it.label })
        assertEquals("FF", decode("_w", BitRegroup.Code.Base64)[0].bottom[0].label)
        assertEquals(15, BitRegroup.Code.Hex.value('F'))
        assertEquals(1, BitRegroup.Code.Base32.value('b'))
        assertEquals(27, BitRegroup.Code.Base64.value('b'))
        assertNull(BitRegroup.Code.Binary.value('2'))
    }
}
