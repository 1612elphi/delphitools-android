package com.delphi.delphitools.tools.calculators

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

class TimeCalcEngineTest {
    private val utc = ZoneId.of("UTC")

    private fun at(iso: String) = Instant.parse(iso).epochSecond.toDouble()

    @Test
    fun sun() {
        // equinox noon: sun 0,0
        val noon = TimeCalcWorld.subsolar(at("2026-03-20T12:00:00Z"))
        assertTrue(abs(noon.first) < 1.5 && abs(noon.second) < 0.01)
        // june: terminator near arctic
        val june = TimeCalcWorld.subsolar(at("2026-06-21T12:00:00Z"))
        assertTrue(june.first > 23 && abs(TimeCalcWorld.terminator(180.0, june) - 66.6) < 1)
    }

    private fun abs(value: Double) = kotlin.math.abs(value)

    @Test
    fun axis() {
        val rungs = TimeCalcAxis.rungs
        // epoch-2038, 330 pt: decades
        val decades = rungs[TimeCalcAxis.rung(TimeCalcAxis.LIMIT, 330.0, 44.0)]
        assertEquals(TimeUnitRung.Year, decades.component)
        assertEquals(10, decades.count)
        val years = TimeCalcAxis.ticks(0.0, TimeCalcAxis.LIMIT, decades, utc, DayOfWeek.MONDAY)
        assertEquals(7, years.size)
        assertEquals(0.0, years.first(), 0.0)
        // week, 330 pt: days
        val days = rungs[TimeCalcAxis.rung(7 * 86_400.0, 330.0, 44.0)]
        assertEquals(TimeUnitRung.Day, days.component)
        assertEquals(1, days.count)
        val noon = 86_400.0 * 10 + 43_200
        val hours = TimeCalcAxis.ticks(noon, noon + 86_400, rungs[5], utc, DayOfWeek.MONDAY)
        assertEquals(9, hours.size)
        assertEquals(noon, hours[0], 0.0)
        assertTrue(TimeCalcAxis.template(hours[4], hours[3], rungs[5], utc).second)
        assertTrue(rungs.all { rung -> rung.minor?.let { rungs[it].seconds < rung.seconds } ?: true })
    }

    @Test
    fun zones() {
        assertEquals("+5:30", TimeCalcZones.signed(19_800))
        assertEquals("−8", TimeCalcZones.signed(-8 * 3600))
        assertEquals("0", TimeCalcZones.signed(0))
        assertEquals("Los Angeles", TimeCalcZones.city("America/Los_Angeles"))
        assertEquals("UTC+5:30", TimeCalcZones.offset("Asia/Kolkata", 0))
    }

    @Test
    fun arithmetic() {
        assertArrayEquals(intArrayOf(1, 2, 3, 4), TimeCalcMath.difference(0, (86_400L + 7_384) * 1000))
        assertEquals(0L, TimeCalcMath.instant(0, "UTC", "UTC"))
        // 00:00 in Kolkata is 18:30 the day before in UTC
        assertEquals(-19_800_000L, TimeCalcMath.instant(0, "Asia/Kolkata", "UTC"))
        assertEquals("1.5", TimeCalcMath.cleaned(" 1,5 "))
    }

    @Test
    fun instantAfterSourceDstChange() {
        // new york moves to edt at 07:00z; berlin is still on cet
        val wall = Instant.parse("2026-03-08T05:00:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-03-08T10:00:00Z").toEpochMilli(), TimeCalcMath.instant(wall, "America/New_York", "Europe/Berlin"))
    }
}
