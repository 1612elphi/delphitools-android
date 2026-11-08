package com.delphi.delphitools.canon

import org.junit.Assert.assertEquals
import org.junit.Test

// CanonSelfCheck in the UIKit port
class CanonLogicTest {
    @Test
    fun gridRowsAreBalanced() {
        assertEquals(listOf(3, 2), canonGridRows(5, 3))
        assertEquals(listOf(3, 2, 2), canonGridRows(7, 3))
        assertEquals(listOf(2, 2), canonGridRows(4, 3))
        assertEquals(listOf(4), canonGridRows(4, 9))
        assertEquals(emptyList<Int>(), canonGridRows(0, 3))
    }

    @Test
    fun thinkingLoaderSwings() {
        val lit = { step: Int -> canonLoaderLit(CanonLoaderMode.Thinking, false, 0, step) }
        assertEquals(1f, lit(0))
        assertEquals(1f, lit(15))
        assertEquals(0f, lit(8))
    }
}
