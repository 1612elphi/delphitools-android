package com.delphi.delphitools.settings

import androidx.compose.runtime.Composable
import com.delphi.delphitools.canon.CanonMenuCell
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.colour.ColourFormat
import com.delphi.delphitools.colour.LocalColourFormats

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
}
