package com.delphi.delphitools.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

// StickerPeelView.selfCheck in the iOS app
class ShellArtTest {
    private fun area(points: List<Offset>) = abs(points.indices.sumOf { i ->
        val a = points[i]
        val b = points[(i + 1) % points.size]
        (a.x * b.y - b.x * a.y).toDouble()
    }) / 2

    private val square = listOf(Offset.Zero, Offset(2f, 0f), Offset(2f, 2f), Offset(0f, 2f))
    private val inward = Offset(-1 / sqrt(2f), -1 / sqrt(2f))

    @Test
    fun foldClipsAtTheLine() {
        assertEquals(2.0, area(clipPolygon(square, Offset(1f, 1f), inward)), 1e-5)
        assertEquals(0.5, area(clipPolygon(square, Offset(1.5f, 1.5f), -inward)), 1e-5)
    }

    @Test
    fun turnOverMirrorsAndHinges() {
        // a flat fold lands the corner on the far one
        val flat = turnOver(Offset(1f, 1f), inward, 1f).apply(Offset(2f, 2f))
        assertEquals(0f, flat.getDistance(), 1e-5f)
        // points on the fold stay put
        val hinge = turnOver(Offset(1f, 1f), inward, 0.9f).apply(Offset(2f, 0f))
        assertEquals(0f, (hinge - Offset(2f, 0f)).getDistance(), 1e-5f)
        assertEquals(Offset(2f, 2f), peelGeometry(Size(2f, 2f), 0f).foldPoint)
    }

    @Test
    fun glyphNamesMarkRepeats() {
        assertEquals(
            listOf(listOf("D", "E", "L", "P", "H", "I"), listOf("dot", "T", "O", "O-2", "L-2", "S")),
            glyphNames(listOf("DELPHI", ".TOOLS")),
        )
    }

    @Test
    fun splashBlockSitsInTheMiddle() {
        val grid = SplashGrid(1280f, 800f, 44f)
        assertEquals(0, grid.columns % 2)
        assertEquals(0, grid.rows % 2)
        val block = (0 until grid.columns * grid.rows).filter { grid.blockPosition(it) != null }
        assertEquals(12, block.size)
        assertEquals(grid.columns / 2 - 3, block.first() % grid.columns)
    }

    @Test
    fun heroIntroShowsDelphiFirst() {
        assertEquals(0, HeroArt.pick(0))
        assertEquals(0, HeroArt.pick(3))
        repeat(50) { assert(HeroArt.shuffled(4) != 4) }
    }
}
