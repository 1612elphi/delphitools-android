package com.delphi.delphitools.colour

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit

// dt-ios ColourFormatSettings keys; values are the lower-case format names
private const val PRIMARY = "colourPrimaryFormat"
private const val SECONDARY = "colourSecondaryFormat"

/** The formats colour cards show first; persisted when [prefs] is set. */
@Stable
class ColourFormatSettings(private val prefs: SharedPreferences? = null) {
    private var primaryState by mutableStateOf(load(PRIMARY, ColourFormat.HEX))
    private var secondaryState by mutableStateOf(load(SECONDARY, ColourFormat.RGB))

    var primary: ColourFormat
        get() = primaryState
        set(value) {
            primaryState = value
            prefs?.edit { putString(PRIMARY, value.name.lowercase()) }
        }

    var secondary: ColourFormat
        get() = secondaryState
        set(value) {
            secondaryState = value
            prefs?.edit { putString(SECONDARY, value.name.lowercase()) }
        }

    private fun load(key: String, fallback: ColourFormat) =
        prefs?.getString(key, null)?.let { saved -> ColourFormat.entries.firstOrNull { it.name.lowercase() == saved } } ?: fallback

    companion object {
        private var shared: ColourFormatSettings? = null

        fun of(context: Context): ColourFormatSettings = shared
            ?: ColourFormatSettings(context.applicationContext.getSharedPreferences("delphitools", Context.MODE_PRIVATE)).also { shared = it }
    }
}

val LocalColourFormats = staticCompositionLocalOf { ColourFormatSettings() }
