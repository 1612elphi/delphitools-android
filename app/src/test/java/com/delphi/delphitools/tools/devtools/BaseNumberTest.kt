package com.delphi.delphitools.tools.devtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseNumberTest {
    private fun value(v: ULong) = BaseReading.Value(v)

    @Test
    fun reading() {
        assertEquals(value(255uL), BaseNumber.read("0xff", Radix.Decimal))
        assertEquals(value(255uL), BaseNumber.read("ff", Radix.Hex))
        assertEquals(value(0xB12uL), BaseNumber.read("0b12", Radix.Hex))
        assertEquals(value(5uL), BaseNumber.read("0b101", Radix.Decimal))
        assertEquals(value(1_000_000uL), BaseNumber.read("1_000 000", Radix.Decimal))
        assertEquals(value(15uL), BaseNumber.read("0o17", Radix.Binary))
        assertEquals(BaseReading.Invalid('9'), BaseNumber.read("9", Radix.Octal))
        assertEquals(BaseReading.Empty, BaseNumber.read("0x", Radix.Decimal))
        assertEquals(BaseReading.TooLarge, BaseNumber.read("18446744073709551616", Radix.Decimal))
        assertEquals(value(ULong.MAX_VALUE), BaseNumber.read("18446744073709551615", Radix.Decimal))
        // other scripts' digits are not digits
        assertEquals(BaseReading.Invalid('١'), BaseNumber.read("١٢", Radix.Decimal))
        assertEquals(BaseReading.Invalid('+'), BaseNumber.read("+5", Radix.Decimal))
    }

    @Test
    fun formatting() {
        assertEquals("0xFF", BaseNumber.format(255uL, Radix.Hex, prefixed = true))
        assertEquals("101", BaseNumber.format(5uL, Radix.Binary))
        assertEquals("FFFFFFFFFFFFFFFF", BaseNumber.format(ULong.MAX_VALUE, Radix.Hex))
        assertEquals(8, BaseNumber.word(255uL))
        assertEquals(16, BaseNumber.word(256uL))
        assertEquals(64, BaseNumber.word(ULong.MAX_VALUE))
        assertEquals(listOf("9", "F", "1", "7"), Radix.entries.map { it.highestDigit })
    }

    @Test
    fun operations() {
        assertEquals(255uL, BaseNumber.apply(BitOperation.Not, 0uL, null, 0, 8))
        assertEquals(0uL, BaseNumber.apply(BitOperation.Left, 0x80uL, null, 1, 8))
        assertNull(BaseNumber.apply(BitOperation.And, 12uL, null, 0, 8))
        assertEquals(6uL, BaseNumber.apply(BitOperation.Xor, 12uL, 10uL, 0, 8))
        assertEquals(ULong.MAX_VALUE, BaseNumber.apply(BitOperation.Not, 0uL, null, 0, 64))
        assertEquals(1uL, BaseNumber.apply(BitOperation.Right, 0x8000_0000_0000_0000uL, null, 63, 64))
    }

    @Test
    fun layout() {
        val tall = BitRegisterLayout.layout(64, listOf(true), false, 340f, 400f)
        assertEquals(16, tall.perLine)
        assertTrue(tall.cell > 16)
        assertEquals(32, BitRegisterLayout.layout(32, listOf(true), false, 900f, 200f).perLine)
    }
}
