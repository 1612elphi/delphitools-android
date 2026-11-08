package com.delphi.delphitools.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.delphi.delphitools.colour.ColourFormatSettings
import com.delphi.delphitools.colour.LocalColourFormats

@Composable
fun DelphitoolsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colours = selectedTheme().colours(darkTheme)
    val context = LocalContext.current
    val formats = remember { ColourFormatSettings.of(context) }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val light = colours.background.luminance() > 0.5f
            WindowCompat.getInsetsController(window, view).run {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }
    CompositionLocalProvider(LocalDelphiColours provides colours, LocalColourFormats provides formats) {
        MaterialTheme(
            colorScheme = colours.toColorScheme(darkTheme),
            typography = Typography,
            content = content
        )
    }
}
