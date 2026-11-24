package com.delphi.delphitools.tools.images

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ImageStitcherEngineTest {
    private val e = ImageStitcherEngine

    @Test
    fun picture() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        val tiles = listOf(a to StitchRect(0.0, 0.0, 100.0, 100.0), b to StitchRect(100.0, 0.0, 200.0, 100.0))
        assertEquals(StitchDrop.Swap(b), e.drop(200.0, 50.0, tiles, a))
        assertEquals(StitchDrop.Place(b, StitchSide.Left), e.drop(105.0, 50.0, tiles, a))
        assertEquals(StitchDrop.Place(b, StitchSide.Bottom), e.drop(200.0, 95.0, tiles, a))
        assertNull(e.drop(50.0, 50.0, tiles, a))
        assertEquals(StitchSide.Right, e.nearest(90.0, 50.0, tiles[0].second).first)
        val centres = e.seamCentres(listOf("a" to StitchRect(100.0, 0.0, 0.0, 200.0), "b" to StitchRect(60.0, 100.0, 100.0, 0.0)), 26.0)
        assertEquals(listOf("b" to (110.0 to 100.0), "a" to (100.0 to 130.0)), centres)
        assertEquals(StitchRect(70.0, 84.5, 338.0, 169.0), e.frame(2000.0, 1000.0, StitchRect(0.0, 0.0, 440.0, 300.0), gutters = true, rightToLeft = false))
        val crop = StitchRect(100.0, 0.0, 200.0, 300.0)
        val match = e.imageFrame(StitchFit.Match, crop, 400.0, 300.0, 20.0, 30.0)
        assertEquals(listOf(-10.0, 0.0, 40.0, 30.0), listOf(match.x, match.y + 0.0, match.width, match.height))
        assertEquals(StitchRect(0.0, 25.0, 100.0, 50.0), e.imageFrame(StitchFit.Matte, StitchRect.Zero, 400.0, 200.0, 100.0, 100.0))
    }

    @Test
    fun layout() {
        val wide = StitchSource(UUID.randomUUID(), 200, 100)
        val tall = StitchSource(UUID.randomUUID(), 100, 200)
        val sources = mapOf(wide.id to wide, tall.id to tall)
        val root = StitchNode.Split(UUID.randomUUID(), StitchDir.Row, listOf(StitchNode.leaf(wide.id), StitchNode.leaf(tall.id)))
        val layout = e.layout(root, sources, wide.id)!!
        assertEquals(250.0, layout.width, 1e-9)
        assertEquals(100.0, layout.height, 1e-9)
        assertEquals(1, layout.seams.size)
        assertEquals(200.0, layout.seams[0].x, 1e-9)

        val matched = root.settingFit(StitchFit.Match, root.leafKey(tall.id)!!)
        val m = e.layout(matched, sources, wide.id)!!
        assertEquals(400.0, m.width, 1e-9)
        assertEquals(StitchRect(0.0, 75.0, 100.0, 50.0), m.ops[1].source)

        val stacked = root.wrapping(null, StitchSide.Bottom, listOf(StitchNode.leaf(wide.id)))
        assertTrue(stacked is StitchNode.Split && stacked.dir == StitchDir.Col)
        val removed = root.removingLeaf(root.leafKey(tall.id)!!)
        assertTrue(removed is StitchNode.Leaf)
        val swapped = root.swappingContent(root.leafKey(wide.id)!!, root.leafKey(tall.id)!!)
        assertEquals(listOf(tall.id, wide.id), swapped.leafSrcIds)
        val inserted = root.inserting(listOf(StitchNode.leaf(wide.id)), root.key, 1)
        assertEquals(listOf(wide.id, wide.id, tall.id), inserted.leafSrcIds)

        val batch = e.batchLayout(listOf(wide, tall, wide))!!
        assertEquals(400.0, batch.width, 0.0)
        assertEquals(200.0, batch.height, 0.0)
        assertEquals(StitchRect(0.0, 75.0, 100.0, 50.0), batch.ops[1].source)
    }

    @Test
    fun export() {
        assertEquals(100 to 50, e.exportSize(100.0, 50.0))
        assertEquals(16384 to 2048, e.exportSize(32768.0, 4096.0))
        val (w, h) = e.exportSize(10000.0, 10000.0)
        assertTrue(w.toLong() * h <= 64_000_000L + 20_000)
        assertTrue(e.naturalCompare("img2", "img10") < 0)
        assertTrue(e.naturalCompare("B", "a") > 0)
        assertEquals(0, e.naturalCompare("a1", "a01"))
    }
}
