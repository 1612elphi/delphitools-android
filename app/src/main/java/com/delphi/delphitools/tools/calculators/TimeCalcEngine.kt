package com.delphi.delphitools.tools.calculators

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.tan

/** dt-core TimeCalcZones: names for time zone identifiers. */
object TimeCalcZones {
    /** UTC first, then round the world westwards from Honolulu. */
    val common = listOf(
        "UTC", "Pacific/Honolulu", "America/Anchorage", "America/Los_Angeles", "America/Denver", "America/Chicago",
        "America/New_York", "America/Sao_Paulo", "Atlantic/Reykjavik", "Europe/London", "Europe/Paris", "Europe/Berlin",
        "Europe/Moscow", "Africa/Cairo", "Asia/Dubai", "Asia/Kolkata", "Asia/Shanghai", "Asia/Tokyo", "Australia/Sydney",
        "Pacific/Auckland",
    )

    val sorted: List<String> by lazy { ZoneId.getAvailableZoneIds().sorted() }

    /** Last part of the identifier, e.g. "Los Angeles". */
    fun city(zone: String) = zone.substringAfterLast('/').replace('_', ' ')

    fun secondsFromGmt(zone: String, millis: Long) = TimeZone.getTimeZone(zone).getOffset(millis) / 1000

    fun abbreviation(zone: String, millis: Long): String {
        val time = TimeZone.getTimeZone(zone)
        return time.getDisplayName(time.inDaylightTime(java.util.Date(millis)), TimeZone.SHORT, Locale.getDefault())
    }

    /** Offset from UTC as "UTC+5:30", "UTC−8" or "UTC". */
    fun offset(zone: String, millis: Long): String {
        val seconds = secondsFromGmt(zone, millis)
        return if (seconds == 0) "UTC" else "UTC" + signed(seconds)
    }

    /** Whole hours, or hours and minutes, with a true minus: "+9", "−3:30", "0". */
    fun signed(seconds: Int): String {
        if (seconds == 0) return "0"
        val hours = abs(seconds) / 3600
        val minutes = abs(seconds) % 3600 / 60
        return (if (seconds < 0) "−" else "+") + if (minutes == 0) "$hours" else "%d:%02d".format(Locale.ROOT, hours, minutes)
    }

    /** The zone's name in the user's language and its offset. */
    fun detail(zone: String, millis: Long): String {
        val name = runCatching { ZoneId.of(zone).getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()) }.getOrNull()
        return listOfNotNull(name, offset(zone, millis)).joinToString(" · ")
    }

    /** "Berlin · CET", for result rows. */
    fun title(zone: String, millis: Long) = city(zone) + " · " + abbreviation(zone, millis)
}

/** dt-core TimeCalcWorld: cities to mark and where the sun stands. */
object TimeCalcWorld {
    /** Where a city of each common zone sits, as (lat, lon). */
    val places = mapOf(
        "Europe/Paris" to (48.9 to 2.3), "Europe/Berlin" to (52.5 to 13.4), "Europe/Moscow" to (55.8 to 37.6), "Europe/London" to (51.5 to -0.1),
        "Africa/Cairo" to (30.0 to 31.2), "Africa/Lagos" to (6.5 to 3.4), "Africa/Nairobi" to (-1.3 to 36.8),
        "Africa/Johannesburg" to (-26.2 to 28.0), "Africa/Casablanca" to (33.6 to -7.6), "Asia/Dubai" to (25.3 to 55.3),
        "Asia/Kolkata" to (19.1 to 72.9), "Asia/Singapore" to (1.3 to 103.8), "Asia/Shanghai" to (39.9 to 116.4), "Asia/Tokyo" to (35.7 to 139.7),
        "Australia/Sydney" to (-33.9 to 151.2), "Pacific/Auckland" to (-36.8 to 174.8), "America/New_York" to (40.7 to -74.0),
        "America/Chicago" to (41.9 to -87.6), "America/Denver" to (39.7 to -105.0), "America/Los_Angeles" to (34.1 to -118.2),
        "America/Anchorage" to (61.2 to -149.9), "Pacific/Honolulu" to (21.3 to -157.8), "America/Sao_Paulo" to (-23.5 to -46.6),
        "America/Argentina/Buenos_Aires" to (-34.6 to -58.4), "America/Lima" to (-12.0 to -77.0), "America/Bogota" to (4.7 to -74.1),
        "America/Santiago" to (-33.4 to -70.7), "Atlantic/Reykjavik" to (64.1 to -21.9), "UTC" to (51.5 to 0.0), "GMT" to (51.5 to 0.0),
    )

    /** The point on Earth with the sun overhead, as (lat, lon) in degrees: declination by day of year, longitude by UTC time. */
    fun subsolar(seconds: Double): Pair<Double, Double> {
        val utc = Instant.ofEpochSecond(floor(seconds).toLong()).atZone(ZoneId.of("UTC"))
        val day = utc.dayOfYear.toDouble()
        val hours = utc.hour + utc.minute / 60.0 + utc.second / 3600.0
        val declination = -23.44 * cos(2 * PI / 365 * (day + 10))
        return declination to -15 * (hours - 12)
    }

    /** Latitude of the day and night line at [lon], for the sun over [sun] (lat, lon). */
    fun terminator(lon: Double, sun: Pair<Double, Double>): Double {
        val declination = maxOf(abs(sun.first), 0.1) * (if (sun.first < 0) -1 else 1) * PI / 180
        val angle = (lon - sun.second) * PI / 180
        return atan(-cos(angle) / tan(declination)) * 180 / PI
    }
}

enum class TimeUnitRung { Minute, Hour, Day, Week, Month, Year }

/** A tick spacing: [count] [component]s, typically [seconds] long, with the rung at [minor] for the finer ticks between. */
class TimeRung(val component: TimeUnitRung, val count: Int, val seconds: Double, val minor: Int?)

/** dt-core TimeCalcAxis: calendar ticks for a time axis, a ladder of units from a minute to a million years. */
object TimeCalcAxis {
    /** 2038-01-19 03:14:07 UTC, the last second a signed 32-bit count holds. */
    const val LIMIT = Int.MAX_VALUE.toDouble()

    val rungs: List<TimeRung> = run {
        val minute = 60.0
        val hour = 3600.0
        val day = 86_400.0
        val month = 2_629_746.0
        val year = 31_556_952.0
        val rungs = mutableListOf(
            TimeRung(TimeUnitRung.Minute, 1, minute, null),
            TimeRung(TimeUnitRung.Minute, 5, 5 * minute, 0),
            TimeRung(TimeUnitRung.Minute, 15, 15 * minute, 1),
            TimeRung(TimeUnitRung.Minute, 30, 30 * minute, 1),
            TimeRung(TimeUnitRung.Hour, 1, hour, 2),
            TimeRung(TimeUnitRung.Hour, 3, 3 * hour, 4),
            TimeRung(TimeUnitRung.Hour, 6, 6 * hour, 4),
            TimeRung(TimeUnitRung.Hour, 12, 12 * hour, 5),
            TimeRung(TimeUnitRung.Day, 1, day, 6),
            TimeRung(TimeUnitRung.Day, 2, 2 * day, 8),
            TimeRung(TimeUnitRung.Week, 1, 7 * day, 8),
            TimeRung(TimeUnitRung.Month, 1, month, 8),
            TimeRung(TimeUnitRung.Month, 3, 3 * month, 11),
            TimeRung(TimeUnitRung.Month, 6, 6 * month, 11),
            TimeRung(TimeUnitRung.Year, 1, year, 11),
        )
        // 1-2-5 years upwards
        var power = 1
        while (power <= 100_000) {
            for (mantissa in listOf(2, 5, 10)) {
                if (power == 100_000 && mantissa > 2) continue
                val count = mantissa * power
                val finer = rungs.indexOfLast { it.component == TimeUnitRung.Year && count / it.count >= 5 }.takeIf { it >= 0 } ?: 14
                rungs += TimeRung(TimeUnitRung.Year, count, count * year, finer)
            }
            power *= 10
        }
        rungs
    }

    /** Index of the finest rung whose ticks sit at least [gap] apart. */
    fun rung(span: Double, width: Double, gap: Double): Int {
        if (span <= 0 || width <= 0) return 0
        return rungs.indexOfFirst { it.seconds / span * width >= gap }.takeIf { it >= 0 } ?: rungs.lastIndex
    }

    private fun ChronoUnit(rung: TimeUnitRung) = when (rung) {
        TimeUnitRung.Minute -> ChronoUnit.MINUTES
        TimeUnitRung.Hour -> ChronoUnit.HOURS
        TimeUnitRung.Day -> ChronoUnit.DAYS
        TimeUnitRung.Week -> ChronoUnit.WEEKS
        TimeUnitRung.Month -> ChronoUnit.MONTHS
        TimeUnitRung.Year -> ChronoUnit.YEARS
    }

    /** Ticks from [lo] to [hi] in epoch seconds, counted from the start of the enclosing unit so they land on round values. */
    fun ticks(lo: Double, hi: Double, rung: TimeRung, zone: ZoneId, firstDay: DayOfWeek): List<Double> {
        val start = runCatching { Instant.ofEpochSecond(floor(lo).toLong()).atZone(zone) }.getOrNull() ?: return emptyList()
        val day = start.truncatedTo(ChronoUnit.DAYS)
        val anchor: ZonedDateTime = when (rung.component) {
            TimeUnitRung.Year -> start.withYear(Math.floorDiv(start.year, rung.count) * rung.count).withDayOfYear(1).truncatedTo(ChronoUnit.DAYS)
            TimeUnitRung.Minute -> start.truncatedTo(ChronoUnit.HOURS)
            TimeUnitRung.Hour -> day
            TimeUnitRung.Day -> day.withDayOfMonth(1)
            TimeUnitRung.Week -> day.with(TemporalAdjusters.previousOrSame(firstDay))
            TimeUnitRung.Month -> day.withDayOfYear(1)
        }
        val unit = ChronoUnit(rung.component)
        val ticks = mutableListOf<Double>()
        var step = 0L
        while (ticks.size < 400 && step < 1000) {
            val tick = runCatching { anchor.plus(step * rung.count, unit).toEpochSecond().toDouble() }.getOrNull() ?: break
            if (tick > hi) break
            if (tick >= lo) ticks += tick
            step++
        }
        return ticks
    }

    /**
     * Date skeleton for a tick's label, and whether it opens a larger unit (a new day under hours, a new year under months).
     * Day labels name the month on the first label and wherever the month changes after [previous].
     */
    fun template(tick: Double, previous: Double?, rung: TimeRung, zone: ZoneId): Pair<String, Boolean> {
        val time = Instant.ofEpochSecond(floor(tick).toLong()).atZone(zone)
        return when (rung.component) {
            TimeUnitRung.Minute, TimeUnitRung.Hour -> if (time.truncatedTo(ChronoUnit.DAYS) == time) "MMMd" to true else "jmm" to false
            TimeUnitRung.Day, TimeUnitRung.Week -> {
                val before = previous?.let { Instant.ofEpochSecond(floor(it).toLong()).atZone(zone) }
                if (before != null && before.year == time.year && before.month == time.month) "d" to false else "MMMd" to true
            }
            TimeUnitRung.Month -> if (time.monthValue == 1) "y" to true else "MMM" to false
            TimeUnitRung.Year -> "y" to false
        }
    }
}

/** dt-ios TimeCalcViewModel's arithmetic. */
object TimeCalcMath {
    /** Digits as typed or pasted, a decimal comma read as a point. */
    fun cleaned(text: String) = text.trim().replace(',', '.')

    /** Seconds since 1970, or null when [text] is not a number. */
    fun epoch(text: String): Double? = text.toDoubleOrNull()?.takeIf { it.isFinite() }

    /** Days, hours, minutes and seconds between two instants in milliseconds, in whole seconds. */
    fun difference(a: Long, b: Long): IntArray {
        val total = abs(b - a) / 1000
        return intArrayOf((total / 86_400).toInt(), (total % 86_400 / 3600).toInt(), (total % 3600 / 60).toInt(), (total % 60).toInt())
    }

    /** [millis] read as a wall-clock time in [zone]: the instant whose [zone] wall clock reads what the device clock reads at [millis]. */
    fun instant(millis: Long, zone: String, local: String = TimeZone.getDefault().id): Long {
        val wall = Instant.ofEpochMilli(millis).atZone(ZoneId.of(local)).toLocalDateTime()
        // atZone moves a time in a gap forward by the gap and takes the earlier offset in an overlap
        return wall.atZone(ZoneId.of(zone)).toInstant().toEpochMilli()
    }
}
