package com.delphi.delphitools.tools.typography

import kotlin.math.abs

/** dt-ios LineHeightCalcViewModel.LineHeightRatio; Custom's multiplier is only its starting value. */
enum class LineHeightRatio(val label: String, val multiplier: Double) {
    Tight("Tight", 1.2),
    Normal("Normal", 1.5),
    Relaxed("Relaxed", 1.75),
    Loose("Loose", 2.0),
    Custom("Custom", 1.5);

    companion object {
        val presets = entries.filter { it != Custom }

        /** The preset a typed or stepped multiplier matches, else Custom. */
        fun matching(multiplier: Double) = presets.firstOrNull { abs(it.multiplier - multiplier) < 0.0005 } ?: Custom
    }
}

object LineHeightEngine {
    val sizeRange = 1.0..200.0
    val ratioRange = 0.8..3.0
    val commonSizes = listOf(10.0, 11.0, 12.0, 13.0, 14.0, 16.0, 18.0, 20.0, 24.0, 28.0, 32.0, 36.0, 40.0, 48.0, 56.0, 64.0, 72.0)

    /** Words greedily filled into lines no wider than [width]; a word wider than a line gets a line of its own. */
    fun wrap(words: List<String>, width: Float, measure: (String) -> Float): List<String> {
        val lines = mutableListOf<String>()
        val current = mutableListOf<String>()
        var used = 0f
        for (word in words) {
            val advance = measure(word)
            if (current.isNotEmpty() && used + advance > width) {
                lines += current.joinToString(" ")
                current.clear()
                used = 0f
            }
            current += word
            used += advance
        }
        if (current.isNotEmpty()) lines += current.joinToString(" ")
        return lines
    }
}
