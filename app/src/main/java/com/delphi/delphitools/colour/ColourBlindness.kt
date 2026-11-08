package com.delphi.delphitools.colour

/** DelphiCore ColourBlindnessType: a 3×3 sRGB matrix per vision type, row-major. */
enum class ColourBlindness(val displayName: String, val severity: String, val shortDescription: String, val matrix: DoubleArray) {
    Normal("Normal Vision", "None", "Full colour vision", doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)),
    Protanopia("Protanopia", "Full", "Red-blind (~1% AMAB)", doubleArrayOf(0.567, 0.433, 0.0, 0.558, 0.442, 0.0, 0.0, 0.242, 0.758)),
    Deuteranopia("Deuteranopia", "Full", "Green-blind (~1% AMAB)", doubleArrayOf(0.625, 0.375, 0.0, 0.7, 0.3, 0.0, 0.0, 0.3, 0.7)),
    Tritanopia("Tritanopia", "Full", "Blue-blind (~0.003%)", doubleArrayOf(0.95, 0.05, 0.0, 0.0, 0.433, 0.567, 0.0, 0.475, 0.525)),
    Protanomaly("Protanomaly", "Partial", "Red-weak (~1% AMAB)", doubleArrayOf(0.817, 0.183, 0.0, 0.333, 0.667, 0.0, 0.0, 0.125, 0.875)),
    Deuteranomaly("Deuteranomaly", "Partial", "Green-weak (~5% AMAB)", doubleArrayOf(0.8, 0.2, 0.0, 0.258, 0.742, 0.0, 0.0, 0.142, 0.858)),
    Tritanomaly("Tritanomaly", "Partial", "Blue-weak (~0.01%)", doubleArrayOf(0.967, 0.033, 0.0, 0.0, 0.733, 0.267, 0.0, 0.183, 0.817)),
    Achromatopsia(
        "Achromatopsia", "Full", "Total colour blindness (~0.003%)",
        doubleArrayOf(0.299, 0.587, 0.114, 0.299, 0.587, 0.114, 0.299, 0.587, 0.114),
    ),
    Achromatomaly(
        "Achromatomaly", "Partial", "Partial colour blindness (very rare)",
        doubleArrayOf(0.618, 0.320, 0.062, 0.163, 0.775, 0.062, 0.163, 0.320, 0.516),
    ),
}

fun Rgb.simulate(type: ColourBlindness): Rgb {
    val m = type.matrix
    val (r, g, b) = clamped
    return Rgb(
        clamp01(m[0] * r + m[1] * g + m[2] * b),
        clamp01(m[3] * r + m[4] * g + m[5] * b),
        clamp01(m[6] * r + m[7] * g + m[8] * b),
    )
}
