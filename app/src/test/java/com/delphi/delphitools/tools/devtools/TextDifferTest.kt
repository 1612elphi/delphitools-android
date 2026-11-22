package com.delphi.delphitools.tools.devtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Expected strings are the output of the iOS TextDiffer and DiffWords, built with Swift 6.4. */
class TextDifferTest {
    private fun show(diff: List<DiffLine>) = diff.joinToString("|") {
        when (it) {
            is DiffLine.Unchanged -> "=${it.leftIndex},${it.rightIndex}:${it.text}"
            is DiffLine.Removed -> "-${it.leftIndex}:${it.text}"
            is DiffLine.Added -> "+${it.rightIndex}:${it.text}"
        }
    }

    private fun spans(list: List<TextSpan>) = list.joinToString(";") { "${it.start},${it.length}" }

    private val sampleOld = "# delphitools\nPrivacy-first tools for designers.\nNothing leaves the device.\n\nTools:\n- Colour converter\n" +
        "- Regex tester\n- Paper sizes\n- Unit converter\n\nEvery tool works offline, on iPhone and iPad.\nVersion 1.0, built in Karlsruhe."
    private val sampleNew = "# delphitools\nPrivacy-first tools for designers and developers.\nNothing leaves the device.\n\nTools:\n" +
        "- Colour converter\n- Regex tester\n- Text diff\n- JSON formatter\n- Paper sizes\n\n" +
        "Every tool works offline, on iPhone, iPad and iPhone Duo.\nVersion 1.1, built in Karlsruhe."

    @Test
    fun lines() {
        val cases = listOf(
            "a\nb\nc" to "a\nB\nc\nd" to "=0,0:a|-1:b|+1:B|=2,2:c|+3:d",
            "a\nb\nc\na\nb\nb\na" to "c\nb\na\nb\na\nc" to "-0:a|-1:b|=2,0:c|-3:a|=4,1:b|+2:a|=5,3:b|=6,4:a|+5:c",
            "x\ny\nz" to "" to "-0:x|-1:y|-2:z|+0:",
            "" to "p\nq" to "-0:|+0:p|+1:q",
            "one\r\ntwo\rthree" to "one\ntwo\nfour" to "=0,0:one|=1,1:two|-2:three|+2:four",
            "a\na\na\nb" to "b\na\na\na" to "+0:b|=0,1:a|=1,2:a|=2,3:a|-3:b",
            sampleOld to sampleNew to "=0,0:# delphitools|-1:Privacy-first tools for designers.|" +
                "+1:Privacy-first tools for designers and developers.|=2,2:Nothing leaves the device.|=3,3:|=4,4:Tools:|" +
                "=5,5:- Colour converter|=6,6:- Regex tester|+7:- Text diff|+8:- JSON formatter|=7,9:- Paper sizes|" +
                "-8:- Unit converter|=9,10:|-10:Every tool works offline, on iPhone and iPad.|-11:Version 1.0, built in Karlsruhe.|" +
                "+11:Every tool works offline, on iPhone, iPad and iPhone Duo.|+12:Version 1.1, built in Karlsruhe.",
        )
        cases.forEach { (texts, expected) -> assertEquals(expected, show(TextDiffer.diff(texts.first, texts.second))) }
        assertEquals("  a\n- b\n+ B\n  c\n+ d", TextDiffer.patch(TextDiffer.diff("a\nb\nc", "a\nB\nc\nd")))
    }

    @Test
    fun listing() {
        val ink = androidx.compose.ui.graphics.Color.Black
        val listing = buildListing(TextDiffer.diff("a\nb\nc", "a\nB\nc\nd"), ink, ink, ink)
        assertEquals("a\nb\nB\nc\nd", listing.text)
        assertEquals(5, listing.marks.gutter.size)
        assertEquals(3, listing.marks.lines.size)
        assertEquals("1 1  ", listing.marks.gutter[0].text)
        assertEquals("2   −", listing.marks.gutter[1].text)
        assertEquals(5, listing.gutterColumns)
    }

    @Test
    fun words() {
        val cases = listOf(
            "Privacy-first tools for designers." to "Privacy-first tools for designers and developers." to " / 33,15",
            "Every tool works offline, on iPhone and iPad." to "Every tool works offline, on iPhone, iPad and iPhone Duo." to "40,4 / 35,6;46,10",
            "Version 1.0, built in Karlsruhe." to "Version 1.1, built in Karlsruhe." to "10,1 / 10,1",
        )
        cases.forEach { (lines, expected) ->
            val changes = DiffWords.changes(lines.first, lines.second)!!
            assertEquals(expected, spans(changes.first) + " / " + spans(changes.second))
        }
        assertNull(DiffWords.changes("alpha beta", "gamma"))
        assertEquals(
            "é@0,1|😀@1,2|x@3,1| @4,1|a_b@5,3|  @8,2|-@10,1|c@11,1",
            DiffWords.tokens("é😀x a_b  -c").joinToString("|") { "${it.text}@${it.range.start},${it.range.length}" },
        )
    }
}
