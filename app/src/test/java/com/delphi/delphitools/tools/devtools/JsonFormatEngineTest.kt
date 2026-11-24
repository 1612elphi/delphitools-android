package com.delphi.delphitools.tools.devtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonFormatEngineTest {
    private fun valid(source: String) = (JsonFormatEngine.parse(source) as JsonParsed.Valid).value
    private fun invalid(source: String) = (JsonFormatEngine.parse(source) as JsonParsed.Invalid).error

    @Test
    fun formats() {
        val value = valid("""{"b":1,"a":[1,2],"big":12345678901234567890}""")
        assertEquals("""{"b":1,"a":[1,2],"big":12345678901234567890}""", JsonFormatEngine.format(value, JsonIndent.Minify))
        assertTrue(JsonFormatEngine.format(value, JsonIndent.Two).startsWith("{\n  \"b\": 1,"))
        assertEquals("{\n  \"a\": {},\n  \"b\": []\n}", JsonFormatEngine.format(valid("""{"a":{},"b":[]}"""), JsonIndent.Two))
        assertEquals("[\n\t1\n]", JsonFormatEngine.format(valid("[1]"), JsonIndent.Tab))
        val escapes = valid("\"aé\\n😀\\ud83d\\ude00\\u00e9\\/\"")
        assertEquals("\"aé\\n😀😀é/\"", JsonFormatEngine.format(escapes, JsonIndent.Minify))
    }

    @Test
    fun reportsPositions() {
        val error = invalid("{\r\n  \"é😀\": x\r\n}")
        assertEquals(2, error.line)
        assertEquals(9, error.column)
        assertEquals("  \"é😀\": x", error.excerpt)
        assertEquals(8, error.caret)
        assertEquals("Unexpected end", invalid("{\"a\":").message)
    }

    @Test
    fun rejectsAndAccepts() {
        for (bad in listOf("[1,]", "[01]", "[1.]", "[-]", "[1e]", "[٣]", "[1] x", "\"\\ud83d\"", "\"\\ude00\"", "\"\\x\"", "\"a\nb\"", "nul", "")) {
            assertTrue(bad, JsonFormatEngine.parse(bad) is JsonParsed.Invalid)
        }
        for (good in listOf("-0.5e+10", " [ ] ", "\"\"", "[1E5,0,-0]")) {
            assertTrue(good, JsonFormatEngine.parse(good) is JsonParsed.Valid)
        }
        val deep = { depth: Int -> "[".repeat(depth) + "]".repeat(depth) }
        assertTrue(JsonFormatEngine.parse(deep(JsonFormatEngine.MAX_DEPTH)) is JsonParsed.Valid)
        assertEquals("Nesting too deep", invalid(deep(100_000)).message)
    }

    @Test
    fun rows() {
        val rows = JsonFormatEngine.rows(valid("""{"a":[1],"a":2}"""), setOf("root.0"))
        assertEquals(3, rows.size)
        assertTrue(rows[1].isCollapsed)
        assertEquals("[1]", rows[1].display)
        assertEquals(3, rows.map { it.id }.toSet().size)
    }

    @Test
    fun tokens() {
        val kinds = JsonListing.tokens("{\"k\": [1.5e3, \"s\\\"\", true]}").map { it.second }
        val p = JsonToken.Punctuation
        assertEquals(
            listOf(p, JsonToken.Key, p, p, JsonToken.Value(JsonKind.Number), p, JsonToken.Value(JsonKind.Str), p, JsonToken.Value(JsonKind.Literal), p, p),
            kinds,
        )
        assertNull(JsonListing.caretRange(JsonParseError("x", 1, 3, "[1,", 3)))
        assertEquals(3, JsonListing.caretOffset(JsonParseError("x", 1, 3, "[1,", 3)))
        assertEquals(TextSpan(1, 3), JsonListing.caretRange(JsonParseError("x", 1, 2, "é😀x", 1)))
    }

    @Test
    fun shape() {
        val value = valid("""{"a":[1,2],"b":"xy","c":{"d":null}}""")
        val shape = JsonShape.of(value)
        assertEquals(listOf(2, 4, 4), listOf(shape.depth, shape.keys, shape.values))
        assertEquals(7, shape.cells.size)
        assertEquals(3, shape.levels)
        val top = shape.cells.filter { it.depth == 1 }
        assertEquals(listOf("a", "b", "c"), top.map { it.label })
        assertEquals(0.0, top.first().start, 0.0)
        assertEquals(1.0, top.last().end, 1e-9)
        assertTrue("wider value, wider cell", top[0].end - top[0].start > top[1].end - top[1].start)
        assertEquals(listOf(0, 1, 2, 3, 5, 6, 7), shape.cells.map { it.line })
        val zoomed = JsonShape.of(value, "root.2")
        assertEquals("root.2", zoomed.focus)
        assertEquals("c", zoomed.focusCell?.label)
        assertEquals(2, zoomed.levels)
        assertEquals(listOf("root", "root.2", "root.2.0"), zoomed.cells.map { it.path })
        assertEquals(top[2].start, zoomed.cells[2].start, 0.0)
        assertEquals(top[2].end, zoomed.cells[2].end, 0.0)
        assertEquals("root", JsonShape.of(value, "root.9").focus)
        assertEquals("root.2", JsonShape.parent("root.2.0"))
        val scalar = JsonShape.of(valid("42"))
        assertEquals(0, scalar.depth)
        assertEquals(1, scalar.cells.size)
    }
}
