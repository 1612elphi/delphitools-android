package com.delphi.delphitools.tools.audiovideo

import com.delphi.delphitools.tools.audiovideo.Timecode.ParseError
import com.delphi.delphitools.tools.audiovideo.Timecode.ParseResult
import com.delphi.delphitools.tools.audiovideo.Timecode.Parts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimecodeTest {
    private val df30 = Timecode.rate("29.97df")
    private val ndf25 = Timecode.rate("25")
    private val df60 = Timecode.rate("59.94df")

    private fun ok(input: String, rate: Timecode.Rate) = Timecode.parse(input, rate) as ParseResult.Ok

    @Test
    fun dropFrame() {
        assertEquals(107892, Timecode.frames(Parts(1, 0, 0, 0), df30))
        assertEquals(Parts(1, 0, 0, 0), Timecode.parts(107892, df30))
        assertEquals(1800, Timecode.frames(Parts(0, 1, 0, 2), df30))
        assertEquals("00:01:00;02", Timecode.format(Timecode.parts(1800, df30), df30))
        assertEquals(215784, Timecode.frames(Parts(1, 0, 0, 0), df60))
        assertEquals(Parts(1, 0, 0, 0), Timecode.parts(215784, df60))
        assertEquals(17982, Timecode.frames(Parts(0, 10, 0, 0), df30))
        assertEquals("00:10:00;00", Timecode.format(Timecode.parts(17982, df30), df30))
        val before = ok("00:00:59;29", df30).frames
        assertEquals("00:01:00;02", Timecode.format(Timecode.parts(before + 1, df30), df30))
        assertEquals(-1800, Timecode.frames(Timecode.parts(-1800, df30), df30))
    }

    @Test
    fun roundTrips() {
        for (rate in listOf(df30, df60, ndf25)) {
            val limit = Math.round(rate.exact * 1200).toInt()
            for (frame in 0..limit) assertEquals("${rate.id} at $frame", frame, Timecode.frames(Timecode.parts(frame, rate), rate))
        }
    }

    @Test
    fun saturates() {
        val max = Int.MAX_VALUE
        assertEquals(Timecode.FRAME_LIMIT, Timecode.frames(Parts(max, max, max, max), df30))
        assertTrue(Timecode.parts(Int.MIN_VALUE, df30).negative)
    }

    @Test
    fun clock() {
        assertEquals("00:00:02.000", Timecode.clock(1.9999))
        assertEquals("—", Timecode.clock(Double.POSITIVE_INFINITY))
        assertEquals("01:01:01.500", Timecode.clock(3661.5))
        assertEquals("-00:00:01.250", Timecode.clock(-1.25))
    }

    @Test
    fun parse() {
        assertEquals(262, Timecode.frames(Parts(0, 0, 10, 12), ndf25))
        assertEquals("-00:00:10:12", Timecode.format(Timecode.parts(-262, ndf25), ndf25))
        assertEquals(262, ok("10:12", ndf25).frames)
        val snapped = ok("00:01:00;00", df30)
        assertEquals("00:01:00;00", snapped.snappedFrom)
        assertEquals(2, snapped.parts.f)
        assertNull(ok("00:10:00;00", df30).snappedFrom)
        assertEquals(ParseResult.Failed(ParseError.Empty), Timecode.parse("", ndf25))
        assertEquals(ParseResult.Failed(ParseError.TooMany), Timecode.parse("1:2:3:4:5", ndf25))
        assertEquals(ParseResult.Failed(ParseError.NotNumeric), Timecode.parse("ab:cd", ndf25))
        assertEquals(ParseResult.Failed(ParseError.FrameRange(24)), Timecode.parse("00:00:00:25", ndf25))
        assertEquals(ParseResult.Failed(ParseError.SecondsRange), Timecode.parse("00:00:60:00", ndf25))
        assertEquals(ParseResult.Failed(ParseError.MinutesRange), Timecode.parse("00:60:00:00", ndf25))
    }

    @Test
    fun field() {
        assertEquals("", Timecode.fieldFormat("", false))
        assertEquals("00:00:00:01", Timecode.fieldFormat("1", false))
        assertEquals("00:00:01;23", Timecode.fieldFormat("123", true))
        assertEquals("23:45:67:89", Timecode.fieldFormat("123456789", false))
    }
}
