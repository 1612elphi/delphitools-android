package com.delphi.delphitools.tools.devtools

import com.delphi.delphitools.tools.devtools.PasswordEngine.CharKind
import com.delphi.delphitools.tools.devtools.PasswordEngine.PassphraseSpec
import com.delphi.delphitools.tools.devtools.PasswordEngine.PasswordSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PasswordEngineTest {
    @Test
    fun engine() {
        assertEquals(listOf("abacus", "abdomen"), PasswordEngine.parseWordlist("11111\tabacus\n11112\tabdomen\n"))
        assertEquals(emptyList<String>(), PasswordEngine.parseWordlist("not a word\n"))
        assertEquals(20, PasswordEngine.password(PasswordSpec(length = 20)).length)
        assertEquals(PasswordEngine.MINIMUM_LENGTH, PasswordEngine.password(PasswordSpec(length = 2)).length)
        assertEquals(PasswordEngine.MAXIMUM_LENGTH, PasswordEngine.password(PasswordSpec(length = 500)).length)
        val bare = PasswordSpec(uppercase = false, digits = false, symbols = false, excludeAmbiguous = false)
        assertEquals(26, PasswordEngine.charset(bare).size)
        assertTrue(PasswordEngine.password(bare).all { it in 'a'..'z' })
        assertTrue(PasswordEngine.charset(PasswordSpec()).none { it in PasswordEngine.AMBIGUOUS })
        assertTrue(abs(PasswordEngine.passwordEntropy(16, 64) - 96) < 0.001)
        assertEquals("Weak", PasswordEngine.strengthBand(0.0))
        assertEquals("Very strong", PasswordEngine.strengthBand(100.0))
        val phrase = PasswordEngine.passphrase(listOf("alpha", "beta"), PassphraseSpec())
        assertEquals(5, phrase.split("-").size)
        assertTrue(phrase.first().isUpperCase() && phrase.last().isDigit())
        assertEquals(CharKind.Digit, PasswordEngine.kind('3'))
        assertEquals(CharKind.Symbol, PasswordEngine.kind('!'))
        assertEquals(CharKind.Letter, PasswordEngine.kind('a'))
        assertEquals(mapOf("abacus" to "11111", "zoom" to "66666"), PasswordEngine.parseRolls("11111\tabacus\n66666\tzoom\n12\tbad\n"))
        assertEquals(5, diceColumns(80f, 10f))
        assertEquals(3, diceColumns(40f, 10f))
    }
}
