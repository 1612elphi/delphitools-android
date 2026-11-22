package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.ContrastLevel
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.contrastRatio
import com.delphi.delphitools.colour.fromHsl
import com.delphi.delphitools.colour.hsl
import com.delphi.delphitools.colour.luminance
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** dt-ios ContrastCheckerViewModel and ContrastRatio.grade. */
object ContrastCheckerEngine {
    fun grade(ratio: Double): String? = when {
        ratio >= ContrastLevel.AAA -> "AAA"
        ratio >= ContrastLevel.AA -> "AA"
        ratio >= ContrastLevel.AA_LARGE -> "AA Large"
        else -> null
    }

    /** Where `ratio` sits on a log scale from 1 to 21, 0...1. */
    fun share(ratio: Double) = ln(max(ratio, 1.0)) / ln(21.0)

    /** Steps `fg`'s HSL lightness, away from `bg` first, until the pair reaches `target`; `fg` when no step does. */
    fun adjustToMeetContrast(fg: Rgb, bg: Rgb, target: Double): Rgb {
        val (h, s, l) = fg.hsl()
        val directions = if (bg.luminance > 0.5) listOf(-1, 1) else listOf(1, -1)
        for (direction in directions) {
            for (step in 0..100) {
                val lightness = if (direction == -1) max(0.0, l - step) else min(100.0, l + step)
                val candidate = fromHsl(h, s, lightness)
                if (contrastRatio(candidate, bg) >= target) return candidate
            }
        }
        return fg
    }

    /** The moved colour for a suggestion: null when the pair already passes or no lightness reaches `target`. */
    fun suggestion(moving: Rgb, fixed: Rgb, target: Double): Rgb? {
        if (contrastRatio(moving, fixed) >= target) return null
        return adjustToMeetContrast(moving, fixed, target).takeIf { contrastRatio(it, fixed) >= target }
    }
}
