package com.delphi.delphitools.tools.images

import java.util.Base64

/** dt-core SVGMarkup: reading SVG markup without an XML parser, its size, and an outline of it for display. */
object SvgMarkup {
    const val VALUE_LIMIT = 48
    const val LINE_LIMIT = 240

    /** Whether [text] starts, after whitespace, as SVG or XML markup does. */
    fun looksLikeSvg(text: String): Boolean {
        val head = text.trimStart().take(5)
        return head.startsWith("<svg") || head.startsWith("<?xml") || head.startsWith("<!")
    }

    private fun rootTag(svg: String): String? {
        val open = svg.indexOf("<svg")
        if (open < 0) return null
        val end = svg.indexOf('>', open)
        return if (end < 0) svg.substring(open) else svg.substring(open, end)
    }

    /** The root element's width and height in user units: the viewBox, else numeric width and height. */
    fun size(svg: String): Pair<Double, Double>? {
        val tag = rootTag(svg) ?: return null
        attribute("viewBox", tag)?.let { box ->
            val numbers = box.split(' ', ',').mapNotNull { it.toDoubleOrNull() }
            if (numbers.size == 4 && numbers[2] > 0 && numbers[3] > 0) return numbers[2] to numbers[3]
        }
        fun length(name: String) = attribute(name, tag)?.removeSuffix("px")?.toDoubleOrNull()
        val width = length("width") ?: return null
        val height = length("height") ?: return null
        return if (width > 0 && height > 0) width to height else null
    }

    /** A quoted attribute's value in a tag, found by ` name="` so `width` does not match `stroke-width`. */
    fun attribute(name: String, tag: String): String? {
        for (quote in listOf('"', '\'')) {
            val marker = "$name=$quote"
            val at = tag.indexOf(" $marker").takeIf { it >= 0 } ?: tag.indexOf("\n$marker").takeIf { it >= 0 } ?: continue
            val start = at + 1 + marker.length
            val end = tag.indexOf(quote, start)
            if (end < 0) continue
            return tag.substring(start, end)
        }
        return null
    }

    /** [svg] with a viewBox from its width and height when it has none, so it scales to its frame. */
    fun sizedForScaling(svg: String): String {
        val open = svg.indexOf("<svg")
        val tag = rootTag(svg) ?: return svg
        if (attribute("viewBox", tag) != null) return svg
        val (width, height) = size(svg) ?: return svg
        val at = open + 4
        return svg.substring(0, at) + " viewBox=\"0 0 $width $height\"" + svg.substring(at)
    }

    /**
     * The SVG as a data URL in an `<img>` that fills the page and contains the drawing, under a policy that loads
     * nothing else, so the markup runs no script and reaches no network.
     */
    fun imagePage(svg: String, background: String): String {
        val data = Base64.getEncoder().encodeToString(sizedForScaling(svg).toByteArray())
        return """<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'">
<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:$background}
img{display:block;width:100%;height:100%;object-fit:contain}</style></head>
<body><img alt="" src="data:image/svg+xml;base64,$data"></body></html>"""
    }

    /** The markup indented, a line per tag or text run, long attribute values cut, at most [LINE_LIMIT] lines. */
    fun outline(svg: String): String {
        val lines = mutableListOf<String>()
        var hidden = 0
        var depth = 0
        var index = 0

        fun emit(text: String, level: Int) {
            if (lines.size >= LINE_LIMIT) {
                hidden++
                return
            }
            lines.add("  ".repeat(maxOf(level, 0)) + text)
        }
        fun find(marker: String, from: Int) = svg.indexOf(marker, from).let { if (it < 0) svg.length else it + marker.length }

        while (index < svg.length) {
            if (svg[index] != '<') {
                val end = svg.indexOf('<', index).let { if (it < 0) svg.length else it }
                val text = svg.substring(index, end).trim()
                if (text.isNotEmpty()) emit(cut(text.replace('\n', ' ')), depth)
                index = end
                continue
            }
            var end: Int
            if (svg.startsWith("<!--", index)) {
                end = find("-->", index + 4)
            } else if (svg.startsWith("<![CDATA[", index)) {
                end = find("]]>", index + 9)
            } else {
                end = index + 1
                var quote: Char? = null
                while (end < svg.length) {
                    val char = svg[end]
                    if (quote != null) {
                        if (char == quote) quote = null
                    } else if (char == '"' || char == '\'') {
                        quote = char
                    } else if (char == '>') {
                        break
                    }
                    end++
                }
                end = minOf(end + 1, svg.length)
            }
            val tag = svg.substring(index, end)
            val isClosing = svg.startsWith("</", index)
            val isLeaf = svg.startsWith("<!", index) || svg.startsWith("<?", index) || tag.endsWith("/>")
            if (isClosing) depth--
            emit(shortened(tag), depth)
            if (!isClosing && !isLeaf) depth++
            index = end
        }
        if (hidden > 0) lines.add("<!-- $hidden more -->")
        return lines.joinToString("\n")
    }

    private fun shortened(tag: String): String {
        val result = StringBuilder()
        var rest = tag.replace('\n', ' ').replace('\t', ' ')
        while (true) {
            val open = rest.indexOfFirst { it == '"' || it == '\'' }
            if (open < 0) break
            val quote = rest[open]
            result.append(rest, 0, open + 1)
            val close = rest.indexOf(quote, open + 1)
            if (close < 0) {
                rest = rest.substring(open + 1)
                break
            }
            result.append(cut(rest.substring(open + 1, close))).append(quote)
            rest = rest.substring(close + 1)
        }
        return result.append(rest).toString()
    }

    private fun cut(text: String) = if (text.length > VALUE_LIMIT) text.take(VALUE_LIMIT - 8) + "…" else text
}
