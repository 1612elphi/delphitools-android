package com.delphi.delphitools.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareKindTest {
    @Test
    fun extensionDecidesBeforeMimeType() {
        assertEquals(ShareKind.Svg, ShareKind.of("image/svg+xml", "svg"))
        assertEquals(ShareKind.Subtitles, ShareKind.of("text/plain", "srt"))
        assertEquals(ShareKind.Fonts, ShareKind.of("application/octet-stream", "woff2"))
        assertEquals(ShareKind.Images, ShareKind.of("image/png", "png"))
        assertEquals(ShareKind.Text, ShareKind.of("application/json", "json"))
        assertNull(ShareKind.of("application/zip", "zip"))
    }

    @Test
    fun webUrlIsOneAddress() {
        assertTrue(ShareKind.isWebUrl(" https://example.org/a?b=c\n"))
        assertFalse(ShareKind.isWebUrl("see https://example.org"))
        assertFalse(ShareKind.isWebUrl("mailto:someone"))
    }

    @Test
    fun offeredToolsExist() {
        // no android font-installer
        val missing = ShareKind.entries.flatMap { it.toolIds(1, "#fff") + it.toolIds(2, null) }.distinct().filter { toolWithId(it) == null }
        assertEquals(listOf("font-installer"), missing)
    }

    @Test
    fun shortSingleLineTextOffersColourTools() {
        assertTrue("colour-converter" in ShareKind.Text.toolIds(1, "#336699"))
        assertFalse("colour-converter" in ShareKind.Text.toolIds(1, "two\nlines"))
    }
}
