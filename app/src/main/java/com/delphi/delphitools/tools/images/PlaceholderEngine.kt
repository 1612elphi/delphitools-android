package com.delphi.delphitools.tools.images

import java.util.Base64
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** dt-ios PlaceholderSpec: the web tool's placeholder, a filled rectangle with its label centred in bold monospace. */
data class PlaceholderSpec(
    val width: Int,
    val height: Int,
    /** `#rrggbb` */
    val background: String,
    val foreground: String,
    val text: String,
) {
    val label get() = text.ifEmpty { "$width × $height" }
    val fontSize get() = min(width, height) / 8.0
    val baseName get() = "placeholder-${width}x$height"

    val svg: String
        get() = """
            <svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height">
              <rect fill="$background" width="$width" height="$height"/>
              <text fill="$foreground" font-family="monospace" font-size="${number(fontSize)}" font-weight="bold" x="50%" y="50%" text-anchor="middle" dominant-baseline="middle">${escapeXml(label)}</text>
            </svg>
        """.trimIndent()

    fun svgDataUri() = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.toByteArray())

    companion object {
        val sizes = 1..4096

        fun pngDataUri(data: ByteArray) = "data:image/png;base64," + Base64.getEncoder().encodeToString(data)

        fun escapeXml(value: String) = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        /** Whole numbers without a point, others to two places. */
        fun number(value: Double) =
            if (value == Math.rint(value)) value.roundToLong().toString() else String.format(Locale.ROOT, "%.2f", value)

        /** Width to height in lowest terms, or to two decimals against 1 when the terms stay large. */
        fun aspect(width: Int, height: Int): String {
            if (width <= 0 || height <= 0) return "—"
            tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
            val divisor = gcd(width, height)
            val w = width / divisor
            val h = height / divisor
            return if (max(w, h) <= 64) "$w:$h" else String.format(Locale.ROOT, "%.2f:1", width.toDouble() / height)
        }
    }
}
