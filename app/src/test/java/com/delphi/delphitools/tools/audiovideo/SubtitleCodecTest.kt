package com.delphi.delphitools.tools.audiovideo

import com.delphi.delphitools.tools.audiovideo.SubtitleCodec.detectFormat
import com.delphi.delphitools.tools.audiovideo.SubtitleCodec.formatTimestamp
import com.delphi.delphitools.tools.audiovideo.SubtitleCodec.parse
import com.delphi.delphitools.tools.audiovideo.SubtitleCodec.parseTimestamp
import com.delphi.delphitools.tools.audiovideo.SubtitleCodec.retime
import com.delphi.delphitools.tools.audiovideo.SubtitleCodec.scale
import com.delphi.delphitools.tools.audiovideo.SubtitleCodec.shift
import com.delphi.delphitools.tools.audiovideo.SubtitleCodec.writeSrt
import com.delphi.delphitools.tools.audiovideo.SubtitleCodec.writeVtt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleCodecTest {
    private val srt = "1\n00:00:01,000 --> 00:00:02,500\nFirst line\n\n2\n00:00:03,000 --> 00:00:04,000\nSecond cue\nwith two lines\n"
    private val vtt = "WEBVTT\n\nNOTE a comment block\n\nintro\n00:01.000 --> 00:02.500 position:10%,line-left align:center\nFirst line\n\n00:00:03.000 --> 00:00:04.000\nSecond cue\n"
    private val first = SubtitleCue(1000, 2500, "First line")

    @Test
    fun timestamps() {
        assertEquals(1000L, parseTimestamp("00:00:01,000"))
        assertEquals(3_723_004L, parseTimestamp("01:02:03.004"))
        assertEquals(123_004L, parseTimestamp("02:03.004"))
        assertEquals(1500L, parseTimestamp("00:00:01.5"))
        assertNull(parseTimestamp("nonsense"))
        assertNull(parseTimestamp("99999999999999999:00:00.000"))
        assertEquals("01:02:03.004", formatTimestamp(3_723_004, '.'))
        assertEquals("00:00:00,000", formatTimestamp(-5, ','))
    }

    @Test
    fun detection() {
        assertEquals(SubtitleFormat.Vtt, detectFormat(vtt))
        assertEquals(SubtitleFormat.Vtt, detectFormat("﻿WEBVTT\n"))
        assertEquals(SubtitleFormat.Srt, detectFormat(srt))
        assertNull(detectFormat("plain text"))
    }

    @Test
    fun parsing() {
        val parsed = parse(srt)
        assertEquals(listOf(first, SubtitleCue(3000, 4000, "Second cue\nwith two lines")), parsed)
        assertEquals(parsed, parse("﻿" + srt.replace("\n", "\r\n")))
        assertEquals(listOf(first, SubtitleCue(3000, 4000, "Second cue")), parse(vtt))
        assertEquals(listOf(first), parse("00:00:01,000 --> 00:00:02,500\nFirst line"))
    }

    @Test
    fun writing() {
        val parsed = parse(srt)
        assertEquals("1\n00:00:01,000 --> 00:00:02,500\nFirst line\n\n2\n00:00:03,000 --> 00:00:04,000\nSecond cue\n", writeSrt(parse(vtt)))
        val headed = writeVtt(parsed, "captions", "en-GB")
        assertTrue(headed.startsWith("WEBVTT\nKind: captions\nLanguage: en-GB\n\n"))
        assertEquals(parsed, parse(headed))
        assertTrue(writeVtt(parsed, "  ").startsWith("WEBVTT\n\n"))
    }

    @Test
    fun retiming() {
        val one = listOf(SubtitleCue(500, 1500, "a"))
        assertEquals(listOf(SubtitleCue(0, 500, "a")), shift(one, -1000))
        assertEquals(listOf(SubtitleCue(0, 0, "a")), shift(one, -2000))
        assertEquals(listOf(SubtitleCue(750, 2250, "a")), scale(one, 1.5))
        assertEquals(listOf(SubtitleCue(0, 0, "a")), retime(one, -1e300, 1e300))
    }
}
