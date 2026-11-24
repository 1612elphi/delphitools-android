package com.delphi.delphitools.tools.devtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TailwindTableTest {
    private val table = TailwindTable.parse(File("src/main/assets/tailwind-cheatsheet.json").readText())

    @Test
    fun filters() {
        assertEquals(19, table.categories.size)
        assertEquals(308, table.count)
        assertEquals(table.categories.size, table.filter("").size)
        assertEquals(listOf("justify-between"), table.filter("JUSTIFY-BETWEEN").flatMap { it.items }.map { it.name })
        assertEquals(listOf("group-hover:"), table.filter("group-hover:visible").flatMap { it.items }.map { it.name })
        assertEquals("Alignment", table.filter("space-evenly").first().name)
        assertTrue(table.filter("zzz").isEmpty())
        assertEquals("Transforms", table.lookup("rotate-45")?.second?.name)
        assertNull(table.lookup("nope"))
        assertEquals(table.count, table.categories.flatMap { it.items }.map { it.name }.toSet().size)
    }

    @Test
    fun specimenNumbers() {
        assertEquals(16f, TailwindSpecimen.pixels("padding: 1rem (16px)"))
        assertEquals(24f, TailwindSpecimen.pixels("width: 1.5rem"))
        assertNull(TailwindSpecimen.pixels("display: flex"))
        assertEquals(45.0, TailwindSpecimen.number("rotate-45"), 0.0)
        assertTrue(TailwindSpecimen.motion(TailwindClass("animate-spin", "", null), "Transitions") == TailwindMotion.Spin)
        assertNull(TailwindSpecimen.motion(TailwindClass("flex", "", null), "Display"))
    }
}
