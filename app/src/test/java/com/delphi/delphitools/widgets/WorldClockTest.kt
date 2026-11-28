package com.delphi.delphitools.widgets

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class WorldClockTest {
    @Test
    fun labelsOffsets() {
        assertEquals("+8", WorldClock.offsetLabel(480))
        assertEquals("-4", WorldClock.offsetLabel(-240))
        assertEquals("+5½", WorldClock.offsetLabel(330))
        assertEquals("+0", WorldClock.offsetLabel(0))
    }

    @Test
    fun findsOffsetAndNextTurn() {
        val now = Instant.parse("2026-11-28T00:44:00Z")
        val berlin = ZoneId.of("Europe/Berlin")
        val tokyo = ZoneId.of("Asia/Tokyo")
        assertEquals(480, WorldClock.offsetMinutes(tokyo, berlin, now))
        // berlin 06:00 is 05:00z
        assertEquals(Instant.parse("2026-11-28T05:00:00Z").toEpochMilli(), WorldClock.nextChange(listOf(berlin, tokyo), now))
    }
}
