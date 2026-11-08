package com.delphi.delphitools.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

private const val THEME = "theme"

/** dt-ios DelphiTheme: the 11 delphi colours, light and dark. */
@Immutable
data class DelphiTheme(val id: String, val name: String, val light: DelphiColours, val dark: DelphiColours) {
    fun colours(dark: Boolean) = if (dark) this.dark else light
}

val DelphiStandard = DelphiTheme("default", "Delphi", DelphiLight, DelphiDark)

/** Delphi, then the themes.json library; You is added at runtime on API 31+. */
val delphiThemes = listOf(DelphiStandard) + themeLibrary

/** The device's Material You palette as a delphi theme; null below API 31. */
@Composable
fun youTheme(): DelphiTheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val context = LocalContext.current
    return DelphiTheme("you", "You", dynamicLightColorScheme(context).toDelphi(false), dynamicDarkColorScheme(context).toDelphi(true))
}

@Composable
fun availableThemes(): List<DelphiTheme> = listOfNotNull(youTheme()) + delphiThemes

// M3 tones, light/dark: background 96/6, card 98/10, muted 92/12, accentBg secondary container 90/surface 17, border 87/22
private fun ColorScheme.toDelphi(dark: Boolean) = DelphiColours(
    background = if (dark) surface else surfaceContainerLow,
    foreground = onSurface,
    card = if (dark) surfaceContainerLow else surface,
    muted = if (dark) surfaceContainer else surfaceContainerHigh,
    mutedForeground = if (dark) outline else onSurfaceVariant,
    secondaryForeground = onSecondaryContainer,
    accentBg = if (dark) surfaceContainerHigh else secondaryContainer,
    border = if (dark) surfaceContainerHighest else surfaceDim,
    primary = primary,
    primaryForeground = onPrimary,
    destructive = error,
)

/** dt-ios ThemeStore: the chosen theme's id, kept in SharedPreferences. */
@Stable
class ThemeStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("delphitools", Context.MODE_PRIVATE)

    private var idState by mutableStateOf(prefs.getString(THEME, null) ?: DelphiStandard.id)

    var id: String
        get() = idState
        set(value) {
            idState = value
            prefs.edit { putString(THEME, value) }
        }

    companion object {
        private var shared: ThemeStore? = null

        fun of(context: Context): ThemeStore = shared ?: ThemeStore(context.applicationContext).also { shared = it }
    }
}

/** The stored theme, or Delphi when it is missing here (You below API 31). */
@Composable
fun selectedTheme(): DelphiTheme {
    val id = ThemeStore.of(LocalContext.current).id
    return availableThemes().firstOrNull { it.id == id } ?: DelphiStandard
}
