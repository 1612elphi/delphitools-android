package com.delphi.delphitools.tools.images

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MaskShapesTest {
    @Test
    fun mulberry() {
        // web mulberry32, seed 7
        val rnd = MaskRandom(7)
        assertEquals(0.011704753153026104, rnd.next(), 0.0)
        assertEquals(0.06195825757458806, rnd.next(), 0.0)
        assertEquals(0.97690763277933, rnd.next(), 0.0)
    }

    // from MaskShapes.swift, swiftc
    @Test
    fun matchesDtCore() {
        val expected = javaClass.getResource("/mask-shapes-expected.txt")!!.readText().trim().lines()
        val actual = MaskShapes.all.map { shape ->
            val numbers = shape.commands.flatMap { (_, values) -> values.toList() }
            val xs = numbers.filterIndexed { i, _ -> i % 2 == 0 }
            val ys = numbers.filterIndexed { i, _ -> i % 2 == 1 }
            String.format(Locale.ROOT, "%s %.4f %.4f %.4f %.4f %d", shape.id, xs.min(), ys.min(), xs.max(), ys.max(), shape.commands.size)
        }
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (want, got) -> assertEquals(want, got) }
        assertEquals("squircle-4", MaskShapes.default.id)
        assertEquals("Squircle 4", MaskShapes.default.title)
        assertEquals(28, MaskShapes.grouped.size)
        assertTrue(MaskShapes.grouped.all { it.second.size == MaskShapes.VARIANTS })
    }

    // dt-core MaskSpec.selfCheck
    @Test
    fun spec() {
        var spec = MaskSpec()
        assertEquals(1024, spec.side(null, null))
        assertEquals(2000, spec.side(3000, 2000))
        spec = spec.copy(zoom = 2.0, scale = 3.0)
        assertEquals(3000, spec.side(3000, 2000))
        spec = spec.copy(scale = 0.5, zoom = 400.0)
        assertEquals(16, spec.side(3000, 2000))
        spec = MaskSpec(offsetX = 0.25)
        assertArrayEquals(doubleArrayOf(-25.0, 0.0, 200.0, 100.0), spec.imageFrame(2, 1, 100.0), 1e-9)
        assertTrue(MaskSpec().covers(2, 1, 100.0))
        assertFalse(MaskSpec(zoom = 0.5).covers(10, 10, 100.0))
    }
}
