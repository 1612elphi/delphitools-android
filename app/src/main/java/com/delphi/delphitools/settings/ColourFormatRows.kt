package com.delphi.delphitools.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.delphi.delphitools.canon.CanonMenuCell
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.colour.ColourFormat
import com.delphi.delphitools.colour.LocalColourFormats
import com.delphi.delphitools.widgets.Daily

private val formatNames = ColourFormat.entries.map { it.name }

/** dt-ios SettingsView "Colour Display": the two format menus, as rows of the caller's CanonRows. */
@Composable
fun ColourFormatRows() {
    val formats = LocalColourFormats.current
    CanonRow("Primary Format", flush = true) {
        CanonMenuCell(formatNames, formats.primary.ordinal, { formats.primary = ColourFormat.entries[it] })
    }
    CanonRow("Secondary Format", flush = true) {
        CanonMenuCell(formatNames, formats.secondary.ordinal, { formats.secondary = ColourFormat.entries[it] })
    }
    val context = LocalContext.current
    var kirk by remember { mutableStateOf(Daily.isKirk(context)) }
    CanonRow("Permanent Captain Kirk") {
        CanonSwitch(kirk) {
            kirk = it
            Daily.setKirk(context, it)
        }
    }
}
