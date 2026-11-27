package com.delphi.delphitools.tools.devtools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScratchpadUndoStepTest {
    @Test
    fun typingRunsUntilASecondPause() {
        val e = TextScratchpadEngine
        assertTrue(e.startsUndoStep(null, 5_000))
        assertFalse(e.startsUndoStep(5_000, 5_999))
        assertTrue(e.startsUndoStep(5_000, 6_000))
    }
}
