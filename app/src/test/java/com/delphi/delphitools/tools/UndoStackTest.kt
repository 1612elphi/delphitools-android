package com.delphi.delphitools.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoStackTest {
    @Test
    fun undoThenRedoReturnsTheStates() {
        val stack = UndoStack<String>(maxDepth = 2)
        assertNull(stack.undo("a"))
        stack.push("a")
        stack.push("b")
        stack.push("c")
        assertEquals("c", stack.undo("d"))
        assertTrue(stack.canRedo)
        assertEquals("d", stack.redo("c"))
        assertEquals("c", stack.undo("d"))
        assertEquals("b", stack.undo("c"))
        // "a" fell off at depth 2
        assertFalse(stack.canUndo)
        stack.push("x")
        assertFalse(stack.canRedo)
    }
}
