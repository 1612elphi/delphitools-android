package com.delphi.delphitools.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.R

// wdth 25 measures 84% of wdth 100 on device, near SF Condensed on iOS
private const val CONDENSED_WIDTH = 25f

@OptIn(ExperimentalTextApi::class)
private fun flex(weight: FontWeight) = Font(
    R.font.roboto_flex,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.width(CONDENSED_WIDTH),
    ),
)

val DelphiFont = FontFamily(
    flex(FontWeight.Normal),
    flex(FontWeight.Medium),
    flex(FontWeight.SemiBold),
    flex(FontWeight.Bold),
)

// iosevka 34.9.1, latin subset
val DelphiMono = FontFamily(
    Font(R.font.iosevka_regular, FontWeight.Normal),
    Font(R.font.iosevka_semibold, FontWeight.SemiBold),
    Font(R.font.iosevka_bold, FontWeight.Bold),
)

private val base = Typography()

val Typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = DelphiFont),
    displayMedium = base.displayMedium.copy(fontFamily = DelphiFont),
    displaySmall = base.displaySmall.copy(fontFamily = DelphiFont),
    headlineLarge = base.headlineLarge.copy(fontFamily = DelphiFont),
    headlineMedium = base.headlineMedium.copy(fontFamily = DelphiFont),
    headlineSmall = base.headlineSmall.copy(fontFamily = DelphiFont),
    titleLarge = base.titleLarge.copy(fontFamily = DelphiFont),
    titleMedium = base.titleMedium.copy(fontFamily = DelphiFont),
    titleSmall = base.titleSmall.copy(fontFamily = DelphiFont),
    bodyLarge = base.bodyLarge.copy(fontFamily = DelphiFont),
    bodyMedium = base.bodyMedium.copy(fontFamily = DelphiFont),
    bodySmall = base.bodySmall.copy(fontFamily = DelphiFont),
    labelLarge = base.labelLarge.copy(fontFamily = DelphiFont),
    labelMedium = base.labelMedium.copy(fontFamily = DelphiFont),
    labelSmall = base.labelSmall.copy(fontFamily = DelphiFont),
)

// iOS text styles as scaled in delphicanon tokens.css
object CanonText {
    private fun style(size: Int, weight: FontWeight) =
        TextStyle(fontFamily = DelphiFont, fontSize = size.sp, fontWeight = weight)

    val title2 = style(20, FontWeight.Normal)
    val body = style(15, FontWeight.Normal)
    val bodyMedium = style(15, FontWeight.Medium)
    val bodySemibold = style(15, FontWeight.SemiBold)
    val subheadline = style(13, FontWeight.Medium)
    val footnoteSemibold = style(12, FontWeight.SemiBold)
    val caption = style(11, FontWeight.Normal)
    val captionSemibold = style(11, FontWeight.SemiBold)
}
