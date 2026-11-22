package com.delphi.delphitools.tools.devtools

import java.time.LocalTime
import java.time.ZonedDateTime

// vixie cron semantics

enum class CronFieldKind { Minute, Hour, Dom, Month, Dow }

data class CronFieldRule(val kind: CronFieldKind, val label: String, val min: Int, val max: Int, val rangeMax: Int, val names: Map<String, Int>?)

sealed interface CronFieldShape {
    data object Star : CronFieldShape
    data class Step(val step: Int) : CronFieldShape
    data class Single(val value: Int) : CronFieldShape
    data class Range(val lo: Int, val hi: Int, val step: Int) : CronFieldShape
    data object Set : CronFieldShape
}

data class CronIssue(val text: String, val data: String?) {
    val shown get() = if (data == null) text else "$text: $data"
}

data class CronFieldParse(
    val source: String,
    val values: List<Int>,
    val any: Boolean,
    val shape: CronFieldShape?,
    val canon: String,
    val error: CronIssue?,
) {
    val ok get() = error == null
}

data class CronParse(val fields: List<CronFieldParse>, val fieldCount: Int, val expression: String?) {
    val ok get() = expression != null
}

/** dt-ios CronEngine. */
object CronEngine {
    val monthNames = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )
    val dowNames = mapOf("sun" to 0, "mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4, "fri" to 5, "sat" to 6)
    private val monthLong = listOf(
        "", "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    private val dowLong = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    val fieldRules = listOf(
        CronFieldRule(CronFieldKind.Minute, "Minute", 0, 59, 59, null),
        CronFieldRule(CronFieldKind.Hour, "Hour", 0, 23, 23, null),
        CronFieldRule(CronFieldKind.Dom, "Day of month", 1, 31, 31, null),
        CronFieldRule(CronFieldKind.Month, "Month", 1, 12, 12, monthNames),
        CronFieldRule(CronFieldKind.Dow, "Day of week", 0, 7, 6, dowNames),
    )

    fun rule(kind: CronFieldKind) = fieldRules.first { it.kind == kind }

    private fun isDigits(token: String) = token.isNotEmpty() && token.all { it in '0'..'9' }

    private fun isLetters(token: String) = token.isNotEmpty() && token.all { it in 'a'..'z' || it in 'A'..'Z' }

    private class Failed(val issue: CronIssue) : Exception()

    private fun atom(token: String, rule: CronFieldRule): Int {
        if (isDigits(token)) {
            val n = token.toIntOrNull() ?: throw Failed(CronIssue("Bad syntax", token))
            if (n < rule.min || n > rule.max) throw Failed(CronIssue("Out of range", "$token (${rule.min}–${rule.max})"))
            return n
        }
        if (isLetters(token)) {
            val lower = token.lowercase()
            rule.names?.get(lower)?.takeIf { lower.length == 3 }?.let { return it }
            val other = when (rule.kind) {
                CronFieldKind.Month -> dowNames
                CronFieldKind.Dow -> monthNames
                else -> null
            }
            if (lower.length == 3 && other?.get(lower) != null) throw Failed(CronIssue("Wrong field", token.uppercase()))
            throw Failed(CronIssue("Unknown name", token.uppercase()))
        }
        throw Failed(CronIssue("Bad syntax", token))
    }

    private fun norm(rule: CronFieldRule, n: Int) = if (rule.kind == CronFieldKind.Dow && n == 7) 0 else n

    fun parseField(source: String, rule: CronFieldRule): CronFieldParse = try {
        parseFieldOrThrow(source, rule)
    } catch (failure: Failed) {
        CronFieldParse(source, emptyList(), false, null, "", failure.issue)
    }

    private fun parseFieldOrThrow(source: String, rule: CronFieldRule): CronFieldParse {
        val items = source.split(',')
        val values = sortedSetOf<Int>()
        val canonParts = mutableListOf<String>()
        val canonSingles = mutableListOf<Int>()
        var singleShape: CronFieldShape? = null
        var mixed = false

        for (item in items) {
            if (item.isEmpty()) throw Failed(CronIssue("Empty item", source))
            val slash = item.split('/')
            if (slash.size > 2 || slash[0].isEmpty()) throw Failed(CronIssue("Bad syntax", item))
            val base = slash[0]
            var step = 1
            if (slash.size == 2) {
                // huge step, as swift
                val parsed = if (isDigits(slash[1])) slash[1].toLongOrNull()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() else null
                if (parsed == null || parsed == 0) throw Failed(CronIssue("Bad step", slash[1]))
                step = parsed
            }

            val lo: Int
            val hi: Int
            var star = false
            var explicitRange = false
            if (base == "*") {
                star = true
                lo = rule.min
                hi = rule.rangeMax
            } else if ('-' in base) {
                val seg = base.split('-')
                if (seg.size != 2 || seg[0].isEmpty() || seg[1].isEmpty()) throw Failed(CronIssue("Bad syntax", item))
                val a = atom(seg[0], rule)
                val b = atom(seg[1], rule)
                if (a > b) throw Failed(CronIssue("Reversed range", base.uppercase()))
                lo = a
                hi = b
                explicitRange = true
            } else {
                val a = atom(base, rule)
                lo = a
                // vixie single-value step
                hi = if (step > 1) rule.rangeMax else a
            }

            // huge steps overflow
            for (v in lo..hi step minOf(step, rule.max + 1)) values += norm(rule, v)

            val stepText = slash.getOrNull(1)?.trimStart('0') ?: ""
            when {
                star -> canonParts += if (step > 1) "*/$stepText" else "*"
                explicitRange -> canonParts += if (step > 1) "$lo-$hi/$stepText" else "$lo-$hi"
                step > 1 -> canonParts += "$lo-${rule.rangeMax}/$stepText"
                else -> {
                    val value = norm(rule, lo)
                    if (value !in canonSingles) {
                        canonSingles += value
                        canonParts += value.toString()
                    }
                }
            }

            if (items.size == 1) {
                singleShape = when {
                    star && step == 1 -> CronFieldShape.Star
                    star -> CronFieldShape.Step(step)
                    explicitRange -> CronFieldShape.Range(lo, hi, step)
                    step > 1 -> CronFieldShape.Range(lo, rule.rangeMax, step)
                    else -> CronFieldShape.Single(norm(rule, lo))
                }
            } else {
                mixed = true
            }
        }
        return CronFieldParse(source, values.toList(), source == "*", if (mixed) CronFieldShape.Set else singleShape, canonParts.joinToString(","), null)
    }

    fun parseCron(input: String): CronParse {
        val parts = input.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size != 5) return CronParse(emptyList(), parts.size, null)
        val fields = parts.mapIndexed { index, part -> parseField(part, fieldRules[index]) }
        if (!fields.all { it.ok }) return CronParse(fields, 5, null)
        return CronParse(fields, 5, fields.joinToString(" ") { it.canon })
    }

    private fun joinList(parts: List<String>) = when (parts.size) {
        1 -> parts[0]
        2 -> "${parts[0]} and ${parts[1]}"
        else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
    }

    private fun joinValues(values: List<Int>, label: (Int) -> String = Int::toString): String {
        if (values.size >= 2 && values.zipWithNext().all { (a, b) -> b == a + 1 }) return "${label(values.first())}–${label(values.last())}"
        return joinList(values.map(label))
    }

    private fun hourClause(field: CronFieldParse): String {
        if (!field.ok || field.any) return ""
        return when (val s = field.shape ?: return "") {
            is CronFieldShape.Single -> "during hour ${s.value}"
            is CronFieldShape.Range -> if (s.step > 1) "every ${s.step} hours from ${s.lo} to ${s.hi}" else "during hours ${s.lo}–${s.hi}"
            else -> "during hours ${joinValues(field.values)}"
        }
    }

    private fun pad2(n: Int) = n.toString().padStart(2, '0')

    private fun timeClause(minute: CronFieldParse, hour: CronFieldParse): String {
        if (!minute.ok) return ""
        val m = minute.shape ?: return ""
        val hourOk = hour.ok && !hour.any
        val hc = hourClause(hour)
        return when (m) {
            CronFieldShape.Star -> if (hourOk) "Every minute $hc" else "Every minute"
            is CronFieldShape.Step -> when {
                m.step == 1 -> if (hourOk) "Every minute $hc" else "Every minute"
                else -> if (hourOk) "Every ${m.step} minutes $hc" else "Every ${m.step} minutes"
            }
            is CronFieldShape.Single -> {
                val h = hour.shape
                if (hour.ok && h is CronFieldShape.Single) "At ${pad2(h.value)}:${pad2(m.value)}"
                else if (hourOk) "At minute ${m.value} $hc" else "At minute ${m.value} past every hour"
            }
            is CronFieldShape.Range -> {
                val base = if (m.step > 1) "Every ${m.step} minutes from ${m.lo} to ${m.hi}" else "Minutes ${m.lo}–${m.hi}"
                if (hourOk) "$base $hc" else "$base past every hour"
            }
            CronFieldShape.Set -> {
                val base = "Minutes ${joinValues(minute.values)}"
                if (hourOk) "$base $hc" else "$base past every hour"
            }
        }
    }

    private fun domText(dom: CronFieldParse): String {
        if (!dom.ok || dom.any) return ""
        return "on ${if (dom.values.size == 1) "day" else "days"} ${joinValues(dom.values)} of the month"
    }

    private fun dowText(dow: CronFieldParse): String {
        if (!dow.ok || dow.any) return ""
        val values = dow.values
        if (values == listOf(1, 2, 3, 4, 5)) return "on weekdays"
        if (values == listOf(0, 6)) return "on weekends"
        return "on ${joinList(values.map { dowLong[it] })}"
    }

    private fun monthText(month: CronFieldParse) = if (!month.ok || month.any) "" else "in ${joinValues(month.values) { monthLong[it] }}"

    fun describe(fields: List<CronFieldParse>): String {
        if (fields.size != 5) return ""
        val parts = mutableListOf(timeClause(fields[0], fields[1]))
        val domPart = domText(fields[2])
        val dowPart = dowText(fields[4])
        if (domPart.isNotEmpty() && dowPart.isNotEmpty()) {
            parts += "$domPart or ${dowPart.drop(3)}"
        } else if (domPart.isNotEmpty() || dowPart.isNotEmpty()) {
            parts += domPart.ifEmpty { dowPart }
        }
        val monthPart = monthText(fields[3])
        if (monthPart.isNotEmpty()) parts += monthPart
        return parts.joinToString(" ")
    }

    private const val MAX_SCAN_DAYS = 366 * 5

    /** Runs after [from], in its zone. A time in a DST gap moves forward by the gap; one that repeats an earlier run is skipped. */
    fun nextRuns(fields: List<CronFieldParse>, count: Int, from: ZonedDateTime): List<ZonedDateTime> {
        if (fields.size != 5 || !fields.all { it.ok }) return emptyList()
        val minutes = fields[0].values
        val hours = fields[1].values
        val domSet = fields[2].values.toSet()
        val monthSet = fields[3].values.toSet()
        val dowSet = fields[4].values.toSet()
        val domAny = fields[2].any
        val dowAny = fields[4].any
        val zone = from.zone

        val out = mutableListOf<ZonedDateTime>()
        var day = from.toLocalDate()
        repeat(MAX_SCAN_DAYS) {
            if (out.size >= count) return out
            if (day.monthValue !in monthSet) {
                day = day.withDayOfMonth(1).plusMonths(1)
                return@repeat
            }
            val domHit = domAny || day.dayOfMonth in domSet
            val dowHit = dowAny || day.dayOfWeek.value % 7 in dowSet
            // cron days use or
            val runsToday = when {
                domAny && dowAny -> true
                domAny -> dowHit
                dowAny -> domHit
                else -> domHit || dowHit
            }
            if (runsToday) {
                for (h in hours) {
                    for (m in minutes) {
                        val t = ZonedDateTime.of(day, LocalTime.of(h, m), zone)
                        // dst gap times repeat
                        if (t.isAfter(out.lastOrNull() ?: from)) {
                            out += t
                            if (out.size == count) return out
                        }
                    }
                }
            }
            day = day.plusDays(1)
        }
        return out
    }

    /** Seconds rounded down to their largest whole unit (days, hours, minutes, seconds), with the unit's index in that list. */
    fun spanUnit(seconds: Long): Pair<Long, Int> {
        val sizes = longArrayOf(86_400, 3_600, 60, 1)
        val index = sizes.indexOfFirst { seconds >= it }.takeIf { it >= 0 } ?: 3
        return seconds / sizes[index] to index
    }
}
