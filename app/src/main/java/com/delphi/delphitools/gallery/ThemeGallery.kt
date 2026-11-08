package com.delphi.delphitools.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.delphi.delphitools.canon.CanonColourStrip
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.colour.rgbOf
import com.delphi.delphitools.settings.ThemePicker
import com.delphi.delphitools.ui.theme.LocalDelphiColours

/** The theme picker over the current theme's 11 roles. */
@Composable
fun ThemeGallery() {
    val c = LocalDelphiColours.current
    val roles = listOf(
        c.background, c.foreground, c.card, c.muted, c.mutedForeground, c.secondaryForeground,
        c.accentBg, c.border, c.primary, c.primaryForeground, c.destructive,
    ).map { rgbOf(it.toArgb()) }
    GalleryPage {
        CanonRows { ThemePicker(Modifier.fillMaxWidth().background(c.card)) }
        CanonColourStrip(rgbOf(c.primary.toArgb()), palette = roles)
    }
}
