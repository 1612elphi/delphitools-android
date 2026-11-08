package com.delphi.delphitools.tools.devtools

import com.delphi.delphitools.tools.devtools.UuidAnatomy.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UuidEngineTest {
    @Test
    fun engine() {
        val v4 = UuidEngine.uuid4()
        assertEquals(36, v4.length)
        assertEquals('4', v4[14])
        assertTrue(v4[19] in "89ab")

        val v7 = UuidEngine.uuid7(0x018F_1234_5678)
        assertEquals('7', v7[14])
        assertTrue(v7[19] in "89ab")
        assertEquals(0x018F_1234_5678L, UuidEngine.uuid7Milliseconds(v7))

        assertEquals(UuidEngine.NANO_ID_LENGTH, UuidEngine.nanoId().length)
        assertTrue(UuidEngine.nanoId(5).all { it in UuidEngine.NANO_ID_ALPHABET })
        assertTrue(UuidEngine.nanoId(-1).isEmpty())

        assertEquals(1, UuidEngine.clamp(0))
        assertEquals(100, UuidEngine.clamp(999))
        assertTrue(UuidEngine.uuid7() <= UuidEngine.uuid7(System.currentTimeMillis() + 1000))
        assertEquals(3, UuidEngine.generate(IdentifierKind.Uuid4, 3).size)
        assertEquals(20, UuidEngine.generate(IdentifierKind.Uuid4, 20).toSet().size)

        assertEquals(v4.replace("-", "").uppercase(), UuidEngine.format(v4, IdentifierKind.Uuid4, uppercase = true, stripHyphens = true))
        assertEquals("abc", UuidEngine.format("abc", IdentifierKind.NanoId, uppercase = true, stripHyphens = true))
    }

    @Test
    fun anatomy() {
        val v7 = UuidAnatomy.columns("018f1234-5678-7abc-9def-0123456789ab", IdentifierKind.Uuid7)
        assertEquals(36, v7.size)
        assertNull(v7[8])
        assertEquals(32, v7.filterNotNull().size)
        assertEquals(List(4) { Field.Time }, v7[0]?.fields)
        assertEquals(List(4) { Field.Version }, v7[14]?.fields)
        assertEquals(listOf(false, true, true, true), v7[14]?.bits)
        assertEquals(listOf(Field.Variant, Field.Variant, Field.Random, Field.Random), v7[19]?.fields)
        assertEquals(listOf(true, false, false, true), v7[19]?.bits)
        val fieldBits = v7.filterNotNull().flatMap { it.fields }
        for ((field, count) in UuidAnatomy.bitCounts(IdentifierKind.Uuid7)) assertEquals(count, fieldBits.count { it == field })

        val v4 = UuidAnatomy.columns("0123456789ABCDEF0123456789ABCDEF", IdentifierKind.Uuid4)
        assertEquals(32, v4.size)
        assertEquals(Field.Random, v4[0]?.fields?.first())
        assertEquals(Field.Version, v4[12]?.fields?.first())
        assertEquals(List(4) { true }, v4[15]?.bits)

        val nano = UuidAnatomy.columns("_-A", IdentifierKind.NanoId)
        assertEquals(listOf(true, true, true, true, true, false), nano[0]?.bits)
        assertEquals(List(6) { true }, nano[1]?.bits)
        assertEquals(List(6) { false }, nano[2]?.bits)
        for (kind in IdentifierKind.entries) {
            assertEquals(if (kind == IdentifierKind.NanoId) 126 else 128, UuidAnatomy.bitCounts(kind).sumOf { it.second })
        }
        assertEquals("2001-09-09T01:46:40.123Z", UuidAnatomy.stamp(1_000_000_000_123))
    }
}
