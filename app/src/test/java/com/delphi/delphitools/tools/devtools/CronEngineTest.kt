package com.delphi.delphitools.tools.devtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class CronEngineTest {
    private fun field(source: String, kind: CronFieldKind) = CronEngine.parseField(source, CronEngine.rule(kind))

    @Test
    fun engine() {
        val weekly = CronEngine.parseCron("30 9 * * MON-FRI")
        assertEquals("30 9 * * 1-5", weekly.expression)
        assertEquals("At 09:30 on weekdays", CronEngine.describe(weekly.fields))

        val every = CronEngine.parseCron("*/15 * * * *")
        assertEquals("*/15 * * * *", every.expression)
        assertEquals("Every 15 minutes", CronEngine.describe(every.fields))
        assertEquals(listOf(0, 15, 30, 45), every.fields[0].values)

        assertEquals(4, CronEngine.parseCron("* * * *").fieldCount)
        assertFalse(CronEngine.parseCron("60 * * * *").ok)
        assertEquals("Reversed range", field("5-1", CronFieldKind.Hour).error?.text)
        assertEquals("Wrong field", field("MON", CronFieldKind.Month).error?.text)
        assertEquals("Bad step", field("*/0", CronFieldKind.Minute).error?.text)
        // dow 7 is sunday
        assertEquals(listOf(0), field("7", CronFieldKind.Dow).values)
        assertEquals(listOf(5, 15, 25, 35, 45, 55), field("5/10", CronFieldKind.Minute).values)
        assertEquals("At 00:00 on day 1 of the month or Sunday in January", CronEngine.describe(CronEngine.parseCron("0 0 1 1 0").fields))

        val utc = ZoneId.of("UTC")
        val start = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, utc)
        val runs = CronEngine.nextRuns(every.fields, 3, start)
        assertEquals(listOf(15, 30, 45), runs.map { it.minute })

        assertEquals(listOf(59), field("59/9223372036854775807", CronFieldKind.Minute).values)
        assertEquals("*/9223372036854775807", field("*/9223372036854775807", CronFieldKind.Minute).canon)
        assertTrue(CronEngine.nextRuns(CronEngine.parseCron("0 0 31 2 *").fields, 1, start).isEmpty())
        assertEquals(listOf(2028, 2032), CronEngine.nextRuns(CronEngine.parseCron("0 0 29 2 *").fields, 2, start).map { it.year })
        // feb 2026 starts sunday
        assertEquals(listOf(1, 8, 13), CronEngine.nextRuns(CronEngine.parseCron("0 0 13 2 SUN").fields, 3, start).map { it.dayOfMonth })

        // berlin dst gap 02:00
        val beforeGap = ZonedDateTime.of(2026, 3, 29, 1, 0, 0, 0, ZoneId.of("Europe/Berlin"))
        val gap = CronEngine.nextRuns(CronEngine.parseCron("*/30 2,3 * * *").fields, 3, beforeGap)
        assertEquals(listOf(3, 3, 2), gap.map { it.hour })
        assertEquals(listOf(29, 29, 30), gap.map { it.dayOfMonth })

        assertEquals(2L to 0, CronEngine.spanUnit((86_400 * 2.5).toLong()))
        assertEquals(59L to 3, CronEngine.spanUnit(59))
        assertEquals(23L to 1, CronEngine.spanUnit((3_600 * 23.9).toLong()))

        val week = cronCells(CronFieldKind.Dow, DayOfWeek.MONDAY)
        assertEquals((0..6).toSet(), week.toSet())
        assertEquals(1, week.first())
        assertEquals(0, cronCells(CronFieldKind.Dow, DayOfWeek.SUNDAY).first())
        assertEquals(60, cronCells(CronFieldKind.Minute, DayOfWeek.MONDAY).size)
    }

    @Test
    fun model() {
        val model = CronBuilderModel()
        model.setMode(CronFieldKind.Hour, CronMode.Range)
        assertEquals("30 9-17 * * MON-FRI", model.boxText)
        model.setSub(CronFieldKind.Hour, CronSub.Lo, 20)
        assertEquals("30 20-20 * * MON-FRI", model.boxText)
        model.setMode(CronFieldKind.Minute, CronMode.Step)
        assertEquals("*/5 20-20 * * MON-FRI", model.boxText)
        model.type("0 12 * * *")
        assertEquals("12", model.source(CronFieldKind.Hour))
        assertEquals(12, model.subs(CronFieldKind.Hour).value)
    }
}
