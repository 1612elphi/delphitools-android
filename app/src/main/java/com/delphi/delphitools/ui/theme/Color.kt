package com.delphi.delphitools.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// sRGB values from delphicanon/src/styles/tokens.css (the iOS delphi*.colorset)
@Immutable
data class DelphiColours(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val muted: Color,
    val mutedForeground: Color,
    val secondaryForeground: Color,
    val accentBg: Color,
    val border: Color,
    val primary: Color,
    val primaryForeground: Color,
    val destructive: Color,
)

val DelphiLight = DelphiColours(
    background = Color(0xFFFAF5E6),
    foreground = Color(0xFF14270F),
    card = Color(0xFFFCF8ED),
    muted = Color(0xFFEDE8D9),
    mutedForeground = Color(0xFF576953),
    secondaryForeground = Color(0xFF1B451E),
    accentBg = Color(0xFFE9E2C4),
    border = Color(0xFFDDD8C2),
    primary = Color(0xFF1E6626),
    primaryForeground = Color(0xFFFAF5E6),
    destructive = Color(0xFFCC272E),
)

val DelphiDark = DelphiColours(
    background = Color(0xFF091509),
    foreground = Color(0xFFEBE4D2),
    card = Color(0xFF101F10),
    muted = Color(0xFF182619),
    mutedForeground = Color(0xFF959074),
    secondaryForeground = Color(0xFFDCD7C9),
    accentBg = Color(0xFF1C2E1C),
    border = Color(0xFF213321),
    primary = Color(0xFFC2AD61),
    primaryForeground = Color(0xFF091B0A),
    destructive = Color(0xFFD74745),
)

val LocalDelphiColours = staticCompositionLocalOf { DelphiLight }

fun DelphiColours.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = primaryForeground,
        primaryContainer = accentBg,
        onPrimaryContainer = secondaryForeground,
        secondary = secondaryForeground,
        onSecondary = background,
        secondaryContainer = accentBg,
        onSecondaryContainer = secondaryForeground,
        tertiary = primary,
        onTertiary = primaryForeground,
        tertiaryContainer = accentBg,
        onTertiaryContainer = secondaryForeground,
        background = background,
        onBackground = foreground,
        surface = background,
        onSurface = foreground,
        surfaceVariant = muted,
        onSurfaceVariant = mutedForeground,
        surfaceContainerLowest = card,
        surfaceContainerLow = card,
        surfaceContainer = muted,
        surfaceContainerHigh = muted,
        surfaceContainerHighest = accentBg,
        outline = mutedForeground,
        outlineVariant = border,
        error = destructive,
        onError = primaryForeground,
    )
}
