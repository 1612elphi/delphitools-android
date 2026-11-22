package com.delphi.delphitools.tools.devtools

/** Removal offsets into the old list and insertion offsets into the new one. */
class Difference(val removals: List<Int>, val insertions: List<Int>)

/**
 * Swift's `difference(from:)`, ported from `_linearSpaceMyers` in swiftlang/swift stdlib/public/core/Diffing.swift,
 * so ties break as they do on Apple platforms.
 */
fun <T> difference(old: List<T>, new: List<T>): Difference = LinearMyers(old, new).run()

private class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    val size get() = width + height
    val delta get() = width - height
    val isOdd get() = delta % 2 != 0
    val max get() = (size + 1) / 2
}

private class LinearMyers<T>(private val old: List<T>, private val new: List<T>) {
    private val removals = mutableListOf<Int>()
    private val insertions = mutableListOf<Int>()
    private var offset = 0
    private var forward = IntArray(0)
    private var backward = IntArray(0)

    private fun shrunk(box: Box): Box {
        var left = box.left
        var top = box.top
        var right = box.right
        var bottom = box.bottom
        while (left < right && top < bottom && old[left] == new[top]) {
            left++
            top++
        }
        while (right > left && bottom > top && old[right - 1] == new[bottom - 1]) {
            right--
            bottom--
        }
        return Box(left, top, right, bottom)
    }

    private fun f(k: Int) = forward[offset + k]
    private fun b(c: Int) = backward[offset + c]

    fun run(): Difference {
        val initial = shrunk(Box(0, 0, old.size, new.size))
        offset = initial.size
        forward = IntArray(initial.size * 2 + 1)
        backward = IntArray(initial.size * 2 + 1)
        val stack = ArrayDeque<Box>()
        var current = initial
        while (true) {
            val snake = middleSnake(current)
            if (snake == null) {
                current = stack.removeLastOrNull() ?: return Difference(removals, insertions)
                continue
            }
            val head = shrunk(Box(current.left, current.top, snake.left, snake.top))
            val tail = shrunk(Box(snake.right, snake.bottom, current.right, current.bottom))
            current = head
            stack.addLast(tail)
        }
    }

    private fun edit(insertion: Boolean, index: Int) {
        if (insertion) insertions += index else removals += index
    }

    private fun middleSnake(box: Box): Box? {
        if (box.size <= 1) {
            if (box.size == 1) edit(box.width != 1, if (box.width == 1) box.left else box.top)
            return null
        }
        forward[offset + 1] = box.left
        backward[offset + 1] = box.bottom
        for (depth in 0..box.max) {
            if (box.isOdd) {
                forwardSearch(box, depth)?.let { return it }
                backwardSearch(box, depth)
            } else {
                forwardSearch(box, depth)
                backwardSearch(box, depth)?.let { return it }
            }
        }
        error("unreachable")
    }

    private fun forwardSearch(box: Box, depth: Int): Box? {
        for (k in depth downTo -depth step 2) {
            var newRight: Int
            var newBottom: Int
            val newLeft: Int
            val insertion: Boolean
            val index: Int
            if (k == -depth || (k != depth && f(k - 1) < f(k + 1))) {
                newRight = f(k + 1)
                newLeft = newRight
                newBottom = box.top + (newRight - box.left) - k
                insertion = true
                index = newBottom - 1
            } else {
                newLeft = f(k - 1)
                newRight = newLeft + 1
                newBottom = box.top + (newRight - box.left) - k
                insertion = false
                index = newLeft
            }
            val newTop = if (depth == 0 || newRight != newLeft) newBottom else newBottom - 1
            while (newRight < box.right && newBottom < box.bottom && old[newRight] == new[newBottom]) {
                newRight++
                newBottom++
            }
            forward[offset + k] = newRight
            if (!box.isOdd) continue
            val c = k - box.delta
            if (c >= -(depth - 1) && c <= depth - 1 && newBottom >= b(c)) {
                edit(insertion, index)
                return Box(newLeft, newTop, newRight, newBottom)
            }
        }
        return null
    }

    private fun backwardSearch(box: Box, depth: Int): Box? {
        for (c in depth downTo -depth step 2) {
            val k = c + box.delta
            var newLeft: Int
            var newTop: Int
            val newBottom: Int
            val insertion: Boolean
            val index: Int
            if (c == -depth || (c != depth && b(c - 1) > b(c + 1))) {
                newTop = b(c + 1)
                newBottom = newTop
                newLeft = box.left + (newTop - box.top) + k
                insertion = false
                index = newLeft
            } else {
                newBottom = b(c - 1)
                newTop = newBottom - 1
                newLeft = box.left + (newTop - box.top) + k
                insertion = true
                index = newTop
            }
            val newRight = if (depth == 0 || newTop != newBottom) newLeft else newLeft + 1
            while (newLeft > box.left && newTop > box.top && old[newLeft - 1] == new[newTop - 1]) {
                newLeft--
                newTop--
            }
            backward[offset + c] = newTop
            if (box.isOdd) continue
            if (k >= -depth && k <= depth && newLeft <= f(k)) {
                edit(insertion, index)
                return Box(newLeft, newTop, newRight, newBottom)
            }
        }
        return null
    }
}
