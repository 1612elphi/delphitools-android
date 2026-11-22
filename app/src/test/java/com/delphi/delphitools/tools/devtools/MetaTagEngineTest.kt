package com.delphi.delphitools.tools.devtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaTagEngineTest {
    @Test
    fun engine() {
        assertEquals("short", MetaTagEngine.cut("short", 60))
        assertEquals("one two …", MetaTagEngine.cut("one two three", 9))
        assertEquals("https://example.com › notes › today", MetaTagEngine.trail("https://example.com/notes/today"))
        assertTrue(MetaTagEngine.trail("").isEmpty())
        assertEquals("example.org", MetaTagEngine.host("https://www.example.org/a"))
        assertTrue(MetaTagEngine.host("nope").isEmpty())
        assertEquals("og.png", MetaTagEngine.fileName("https://example.com/i/og.png"))
        assertEquals("", MetaTagEngine.fileName("https://example.com/"))

        val html = MetaTagEngine.html(MetaTagFields(pageTitle = "A & \"B\"", twitterCard = "summary"))
        assertEquals(
            listOf(
                "<title>A &amp; &quot;B&quot;</title>",
                "",
                "<!-- Open Graph -->",
                "<meta property=\"og:title\" content=\"A &amp; &quot;B&quot;\">",
                "<meta property=\"og:type\" content=\"website\">",
                "",
                "<!-- Twitter Card -->",
                "<meta name=\"twitter:card\" content=\"summary\">",
                "<meta name=\"twitter:title\" content=\"A &amp; &quot;B&quot;\">",
            ).joinToString("\n"),
            html,
        )
    }
}
