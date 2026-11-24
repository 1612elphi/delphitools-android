package com.delphi.delphitools.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellDataTest {
    @Test
    fun orderKeepsSavedNamesThenAppendsTheRest() {
        val order = ShellStores.resolveOrder(listOf("Colour", "Nonsense", "Colour", "Pdf"))
        assertEquals(listOf(ToolCategory.Colour, ToolCategory.Pdf), order.take(2))
        assertEquals(ToolCategory.entries.size, order.size)
        assertEquals(ToolCategory.entries, ShellStores.resolveOrder(null))
    }

    @Test
    fun searchMatchesNamesKeywordsAndCategories() {
        assertTrue(searchTools("deuteranopia").any { it.id == "colorblind-sim" })
        assertTrue(searchTools("WCAG").any { it.id == "contrast-checker" })
        assertTrue(searchTools("turbo-nerd").all { it.category == ToolCategory.TurboNerd || "turbo-nerd" in it.description.lowercase() })
        assertEquals(tools, searchTools(""))
    }

    @Test
    fun everyRouteNamesARegisteredTool() {
        assertEquals(emptySet<String>(), toolPages.keys - allTools.map { it.id }.toSet())
    }

    @Test
    fun favourEmphasisTurnsItalic() {
        val text = favourText("I'd *love* to")
        assertEquals("I'd love to", text.text)
        assertEquals(1, text.spanStyles.size)
        assertEquals(4, text.spanStyles.first().start)
    }
}
