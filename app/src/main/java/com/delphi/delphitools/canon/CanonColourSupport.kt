package com.delphi.delphitools.canon

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.colour.ColourNames
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.toArgb
import com.delphi.delphitools.ui.theme.DelphiFont
import org.json.JSONArray
import com.delphi.delphitools.ui.theme.DelphiMono

internal fun Rgb.toCanonColor() = Color(toArgb())

/** Text styles of the colour components missing from CanonText; mono is delphiMono. */
internal object CanonColourType {
    private fun mono(size: Int) = TextStyle(fontFamily = DelphiMono, fontSize = size.sp)

    val mono11 = mono(11)
    val mono12 = mono(12)
    val mono13 = mono(13)
    val mono15 = mono(15)
    val mono16 = mono(16)
    val title2Semibold = TextStyle(fontFamily = DelphiFont, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val subheadlineSemibold = TextStyle(fontFamily = DelphiFont, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

private object ColourNamesAsset {
    @Volatile
    var names: ColourNames? = null
}

/** The nearest-name table from assets/colour-names.json, read once; null where the asset is missing. */
internal fun colourNames(context: Context): ColourNames? = ColourNamesAsset.names ?: runCatching {
    val entries = JSONArray(context.assets.open("colour-names.json").bufferedReader().use { it.readText() })
    ColourNames(
        (0 until entries.length()).map {
            val entry = entries.getJSONObject(it)
            entry.getString("name") to entry.getString("hex").removePrefix("#").toInt(16)
        },
    )
}.getOrNull()?.also { ColourNamesAsset.names = it }

@Composable
internal fun colourNamesOrNull(): ColourNames? {
    val context = LocalContext.current
    return remember { colourNames(context) }
}

