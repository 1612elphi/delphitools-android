package com.delphi.delphitools.tools.audiovideo

import com.delphi.delphitools.tools.audiovideo.AutoSubtitleEngine.chunks
import com.delphi.delphitools.tools.audiovideo.AutoSubtitleEngine.cues
import com.delphi.delphitools.tools.audiovideo.AutoSubtitleEngine.mostSpokenFirst
import com.delphi.delphitools.tools.audiovideo.AutoSubtitleEngine.words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSubtitleEngineTest {
    @Test
    fun mostSpokenFirstOrder() {
        assertEquals(listOf("en", "zh", "pt", "ur", "af", "de"), mostSpokenFirst(listOf("de", "ur", "af", "en", "zh", "pt"), { it }, { it }))
        assertEquals(
            listOf("en-AU", "en-US", "fr-FR", "nl-NL"),
            mostSpokenFirst(listOf("en-US", "fr-FR", "en-AU", "nl-NL"), { it.take(2) }, { it }),
        )
    }

    @Test
    fun cuesFromWords() {
        assertTrue(cues(emptyList()).isEmpty())
        assertEquals(1, cues(listOf(SubtitleWord("a", Double.NaN, Double.POSITIVE_INFINITY))).size)
        assertEquals(
            listOf(SubtitleCue(0, 900, "Hello there.")),
            cues(listOf(SubtitleWord("Hello", 0.0, 0.4), SubtitleWord("there.", 0.5, 0.9))),
        )
        assertEquals(2, cues(listOf(SubtitleWord("one", 0.0, 0.3), SubtitleWord("two", 2.0, 2.3))).size)
        val long = cues((0 until 20).map { SubtitleWord("wordy", it * 0.2, it * 0.2 + 0.1) })
        assertTrue(long.size > 1 && long.all { it.text.length <= 42 })
        val slow = cues((0 until 10).map { SubtitleWord("w", it * 0.7, it * 0.7 + 0.6) })
        assertTrue(slow.all { it.end - it.start <= 6000 })
    }

    @Test
    fun timedPartsOffsetByChunk() {
        val heard = words(listOf(HeardPart("Hi", 100), HeardPart("there.", 400), HeardPart("Later", 5000)), emptyList(), 20.0, 30.0)
        listOf(20.1, 20.4, 25.0).zip(heard) { want, word -> assertEquals(want, word.start, 1e-9) }
        assertEquals(20.4, heard[0].end, 1e-9)
        assertEquals(21.2, heard[1].end, 1e-9)
        assertEquals(25.8, heard[2].end, 1e-9)
    }

    @Test
    fun untimedWordsSpreadOverChunk() {
        val heard = words(listOf(HeardPart("a", 0), HeardPart("b", 0)), listOf("one two", "three four"), 10.0, 14.0)
        assertEquals(listOf("one", "two", "three", "four"), heard.map { it.text })
        assertEquals(listOf(10.0, 11.0, 12.0, 13.0), heard.map { it.start })
        assertEquals(14.0, heard.last().end, 1e-9)
        assertTrue(words(emptyList(), listOf(" "), 0.0, 1.0).isEmpty())
    }

    @Test
    fun chunksCutAtQuietestFrame() {
        val energy = FloatArray(600) { 1f }
        energy[230] = 0f
        energy[400] = 0f
        assertEquals(listOf(0 until 230, 230 until 400, 400 until 600), chunks(energy))
        assertEquals(listOf(0 until 240), chunks(FloatArray(240) { 1f }))
        assertTrue(chunks(FloatArray(0)).isEmpty())
        val covered = chunks(FloatArray(5000) { (it % 37).toFloat() })
        assertEquals(5000, covered.sumOf { it.count() })
        assertTrue(covered.all { it.count() in 1..250 })
    }

    @Test
    fun speechPcmDownmixesAndResamples() {
        val stereo = SpeechPcm(48_000, 2)
        val out = stereo.feed(ShortArray(48_000 * 2) { if (it % 2 == 0) 1000 else 3000 })
        assertEquals(16_000, out.size)
        assertTrue(out.all { it.toInt() == 2000 })
        assertEquals(10, stereo.energy().size)
        assertEquals(4_000_000f, stereo.energy()[0], 1f)

        val split = SpeechPcm(44_100, 1)
        val total = (0 until 10).sumOf { split.feed(ShortArray(4410)).size }
        assertEquals(16_000, total)

        val up = SpeechPcm(8000, 1)
        val doubled = up.feed(shortArrayOf(100, 200))
        assertEquals(listOf<Short>(100, 100, 200, 200), doubled.toList())
    }
}
