package com.delphi.delphitools.tools.turbonerd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShavianEngineTest {
    private fun word(latin: String, marker: GlossMarker = GlossMarker.None): GlossToken =
        GlossToken.Word(GlossWord(latin, emptyList(), latin, "", GlossSource.Core, marker))

    private fun markers(tokens: List<GlossToken>) = tokens.mapNotNull { (it as? GlossToken.Word)?.word?.marker }

    private val space = GlossToken.Whitespace(" ")

    private fun letter(shavian: String) = ShavianPhonemeMap.letter(shavian)!!

    @Test
    fun carryEdits() {
        val old = listOf(word("Shaw", GlossMarker.Namer), space, word("wrote"), space, word("NATO", GlossMarker.Acroarc))
        val typed = listOf(word("Shaw"), space, word("never"), space, word("wrote"), space, word("NATO"))
        assertEquals(
            listOf(GlossMarker.Namer, GlossMarker.None, GlossMarker.None, GlossMarker.Acroarc),
            markers(ShavianEngine.carryEdits(old, typed)),
        )
        val changed = listOf(word("Shah"), space, word("wrote"), space, word("NATO"))
        assertEquals(listOf(GlossMarker.None, GlossMarker.None, GlossMarker.Acroarc), markers(ShavianEngine.carryEdits(old, changed)))
        assertTrue(ShavianEngine.carryEdits(old, emptyList()).isEmpty())
    }

    @Test
    fun letterEdits() {
        val shaw = ShavianEngine.inserting(letter("𐑖"), 0, GlossWord("Shaw", emptyList(), "", "", GlossSource.Core, GlossMarker.Namer))
        val shaws = ShavianEngine.inserting(letter("𐑟"), 2, ShavianEngine.inserting(letter("𐑷"), 1, shaw))
        assertEquals("·𐑖𐑷𐑟", shaws.shavian)
        assertEquals("ʃɔːz", shaws.ipa)
        assertTrue(shaws.userEdited)
        assertEquals("·𐑩𐑖𐑷𐑟", ShavianEngine.inserting(letter("𐑩"), 0, shaws).shavian)
        assertEquals("·𐑖𐑩𐑷𐑟", ShavianEngine.inserting(letter("𐑩"), 1, shaws).shavian)
        assertEquals("·𐑖𐑷", ShavianEngine.removing(2, shaws)?.shavian)
        assertNull(ShavianEngine.removing(0, shaw))
        val retyped = ShavianEngine.carryEdits(listOf(GlossToken.Word(shaws)), listOf(GlossToken.Word(shaw), space, GlossToken.Word(shaw)))
        assertEquals("·𐑖𐑷𐑟", (retyped[0] as GlossToken.Word).word.shavian)
    }

    @Test
    fun transliterate() {
        val engine = ShavianEngine(core = mapOf("cute" to listOf("K", "Y", "UW1", "T")), full = mapOf("father" to listOf("F", "AA1", "DH", "ER0")))
        val tokens = engine.tokenise("The cute father, zog")
        assertEquals(8, tokens.size)
        val words = tokens.mapNotNull { (it as? GlossToken.Word)?.word }
        assertEquals(GlossSource.Shorthand, words[0].source)
        assertEquals("𐑞", words[0].shavian)
        // yew ligature
        assertEquals("𐑒𐑿𐑑", words[1].shavian)
        // palm override
        assertEquals("𐑓𐑭𐑞𐑼", words[2].shavian)
        assertEquals(GlossSource.Full, words[2].source)
        assertTrue(words[3].guessed)
        assertEquals(GlossToken.Punctuation(","), tokens[5])
        assertEquals("𐑞 𐑒𐑿𐑑 𐑓𐑭𐑞𐑼, 𐑟𐑪𐑜", ShavianEngine.text(tokens))
    }

    @Test
    fun reResolve() {
        val engine = ShavianEngine()
        val guessed = engine.tokenise("cat")
        engine.full = mapOf("cat" to listOf("K", "AE1", "T"))
        val word = (engine.reResolve(guessed)[0] as GlossToken.Word).word
        assertEquals(GlossSource.Full, word.source)
        assertEquals("kæt", word.ipa)
    }

    @Test
    fun alternatives() {
        assertEquals(listOf("𐑚"), ShavianAlternatives.of("𐑐").map { it.shavian })
        assertEquals(22, ShavianAlternatives.of("𐑩").size)
        assertTrue(ShavianAlternatives.of("𐑣").isEmpty())
    }
}
