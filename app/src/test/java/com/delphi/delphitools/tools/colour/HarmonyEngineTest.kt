package com.delphi.delphitools.tools.colour

import com.delphi.delphitools.colour.fromOklch
import com.delphi.delphitools.colour.oklch
import com.delphi.delphitools.colour.parseHex
import com.delphi.delphitools.tools.UndoGrouping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HarmonyEngineTest {
    @Test
    fun undoGroupingPushesOncePerPress() {
        val grouping = UndoGrouping()
        var pushes = 0
        val edit = { grouping.record { pushes++ } }
        edit()
        edit()
        assertEquals(2, pushes)
        val first = grouping.press()
        edit()
        edit()
        assertEquals(3, pushes)
        val second = grouping.press()
        grouping.lift(first)
        edit()
        edit()
        assertEquals(4, pushes)
        grouping.lift(second)
        edit()
        assertEquals(5, pushes)
    }

    @Test
    fun wheelSpaces() {
        val blue = parseHex("#3B82F6")!!
        for (space in HarmonyWheelSpace.entries) {
            val (l, r, h) = space.parts(blue).toList()
            val back = space.colour(l, r, h)
            assertTrue(space.name, abs(back.r - blue.r) < 0.002 && abs(back.g - blue.g) < 0.002 && abs(back.b - blue.b) < 0.002)
            assertTrue(space.name, r <= space.maxRadius(l, h) + 0.002)
            val triad = space.harmony(l, r, h, HarmonyMode.Triadic)
            assertEquals(space.name, (h + 120) % 360, space.parts(triad[1])[2], 1.0)
        }
        val model = HarmonyWheelSpace.OKLCH.harmony(0.6, 0.1 / 0.33, 40.0, HarmonyMode.Complementary)
        assertEquals(fromOklch(0.6, 0.1, 220.0).g, model[1].g, 1e-9)
        // mono softens past 0.6
        val teal = fromOklch(0.5, 0.05, 200.0)
        val mono = HarmonyWheelSpace.OKLCH.harmony(teal, HarmonyMode.Monochromatic)
        assertEquals(5, mono.size)
        assertEquals(0.035, mono[0].oklch()[1], 0.002)
        assertEquals(0.05, mono[3].oklch()[1], 0.002)
        assertEquals(teal.r, mono[2].r, 0.002)
        assertEquals(teal.b, mono[2].b, 0.002)
        assertEquals(3, HarmonyWheelSpace.OKLCH.harmony(teal, HarmonyMode.Triadic).size)
        assertTrue(HarmonyWheelSpace.LCH.maxRadius(0.5, 40.0) * 134 > 60 && HarmonyWheelSpace.LCH.maxRadius(0.99, 40.0) * 134 < 10)
    }

    @Test
    fun turntable() {
        val (rx, ry) = HarmonyTable.point(90.0, 50.0, 100.0, 100.0)
        assertEquals(150.0, rx, 1e-9)
        assertEquals(100.0, ry, 1e-9)
        val (tx, ty) = HarmonyTable.point(0.0, 50.0, 100.0, 100.0)
        assertEquals(100.0, tx, 1e-9)
        assertEquals(50.0, ty, 1e-9)
        assertEquals(30.0, HarmonyTable.catchNotch(22.0, 200.0), 0.0)
        assertEquals(15.0, HarmonyTable.catchNotch(22.0, -200.0), 0.0)
        assertEquals(-15.0, HarmonyTable.catchNotch(-7.0, -1.0), 0.0)
        assertEquals(330.0, wrappedDegrees(-30.0), 0.0)
        assertEquals(5.0, wrappedDegrees(725.0), 0.0)

        var value = 0.0
        var velocity = 0.0
        repeat(120) { HarmonyTable.catchSpring.step(value, velocity, 1.0, 1.0 / 60).let { value = it.first; velocity = it.second } }
        assertEquals(1.0, value, 0.001)

        val spacing = 0.12
        for (base in listOf(0.0, 0.3, 1.0)) {
            val placed = listOf(-2.0, -1.0, 0.0, 1.0, 2.0).map { HarmonyTable.ladder(base, it, spacing) }
            assertTrue(placed.first() >= spacing * 0.8 - 1e-9 && placed.last() <= 0.96 + 1e-9)
            assertTrue(placed.zipWithNext().all { (a, b) -> abs(b - a - spacing) < 1e-9 })
        }
        val (triadic, triadicBase) = HarmonyTable.slots(HarmonyMode.Triadic)
        assertEquals(0, triadicBase)
        assertEquals(listOf(120.0, 240.0), triadic.map { it.first })
        val (analogous, analogousBase) = HarmonyTable.slots(HarmonyMode.Analogous)
        assertEquals(1, analogousBase)
        assertEquals(listOf(-30.0, 30.0), analogous.map { it.first })
    }

    @Test
    fun planeHasHue90AtRight() {
        val pixels = HarmonyTable.plane(HarmonyWheelSpace.OKLCH, 0.7, 64)
        val pixel = pixels[31 * 64 + 38]
        val expected = fromOklch(0.7, 0.33 * 6 / 27.52, 90.0)
        assertEquals(255, pixel ushr 24)
        assertEquals(expected.r, (pixel shr 16 and 0xFF) / 255.0, 0.06)
        assertEquals(expected.g, (pixel shr 8 and 0xFF) / 255.0, 0.06)
        assertEquals(0, pixels[0])
    }
}
