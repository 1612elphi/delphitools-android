package com.delphi.delphitools.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// DelphiTheme.selfCheck in delphicanon
class ThemeLibraryTest {
    private fun DelphiColours.roles() = listOf(
        background, foreground, card, muted, mutedForeground, secondaryForeground,
        accentBg, border, primary, primaryForeground, destructive,
    )

    @Test
    fun everyThemeSetsElevenOpaqueRoles() {
        assertEquals(29, delphiThemes.size)
        for (theme in delphiThemes) {
            for (colours in listOf(theme.light, theme.dark)) {
                val roles = colours.roles()
                assertEquals(theme.id, 11, roles.size)
                assertTrue(theme.id, roles.all { it != Color.Unspecified && it.alpha == 1f })
            }
        }
    }

    @Test
    fun idsAreUnique() {
        assertEquals(delphiThemes.size, delphiThemes.map { it.id }.toSet().size)
    }

    @Test
    fun delphiIsTheDefaultColours() {
        val delphi = delphiThemes.first()
        assertEquals("Delphi", delphi.name)
        assertEquals(DelphiLight, delphi.light)
        assertEquals(DelphiDark, delphi.dark)
    }
}
