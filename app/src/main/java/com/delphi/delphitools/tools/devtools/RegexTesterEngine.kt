package com.delphi.delphitools.tools.devtools

import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** A UTF-16 range, as `NSRange` on iOS and String indices here. */
data class TextSpan(val start: Int, val end: Int) {
    val length get() = end - start
}

/** [groups]: one per capture group; null for a group that took no part in the match. */
data class RegexMatch(val range: TextSpan, val groups: List<TextSpan?>)

sealed interface RegexOutcome {
    data object Empty : RegexOutcome
    data class Invalid(val message: String) : RegexOutcome
    data object TimedOut : RegexOutcome
    data class Matched(val matches: List<RegexMatch>, val truncated: Boolean, val groups: Int) : RegexOutcome

    val failed get() = this is Invalid || this is TimedOut
}

/** dt-core RegexTesterEngine on java.util.regex, which Android backs with ICU as NSRegularExpression is. */
object RegexTesterEngine {
    private const val RUNAWAY_LIMIT = 4
    private val runaways = mutableListOf<Thread>()

    /**
     * Flags as JavaScript writes them: g every match, i case insensitive, m line anchors, s dot matches newlines.
     * Matching stops after [limit] matches, and gives up after [timeLimitMs] (catastrophic backtracking).
     */
    fun run(pattern: String, flags: String, text: String, limit: Int = 1000, timeLimitMs: Long = 400): RegexOutcome {
        if (pattern.isEmpty()) return RegexOutcome.Empty
        var options = 0
        // jvm needs unicode_case
        if ('i' in flags) options = options or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        if ('m' in flags) options = options or Pattern.MULTILINE
        if ('s' in flags) options = options or Pattern.DOTALL
        val regex = try {
            Pattern.compile(pattern, options)
        } catch (_: PatternSyntaxException) {
            // ios message: the pattern
            return RegexOutcome.Invalid("Invalid pattern · $pattern")
        }
        val global = 'g' in flags
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeLimitMs)
        val abandoned = AtomicBoolean(false)
        val work = FutureTask {
            val matcher = regex.matcher(text)
            val found = mutableListOf<RegexMatch>()
            var truncated = false
            var late = false
            while (!abandoned.get() && matcher.find()) {
                if (System.nanoTime() >= deadline) {
                    late = true
                    break
                }
                if (found.size >= limit) {
                    truncated = true
                    break
                }
                val groups = (1..matcher.groupCount()).map { index ->
                    matcher.start(index).takeIf { it >= 0 }?.let { TextSpan(it, matcher.end(index)) }
                }
                found += RegexMatch(TextSpan(matcher.start(), matcher.end()), groups)
                if (!global) break
            }
            if (late) null else RegexOutcome.Matched(found, truncated, matcher.groupCount())
        }
        // ponytail: neither ICU on Android nor the JVM can stop a find() in flight, so a runaway one keeps its thread until it ends;
        // past RUNAWAY_LIMIT of them every run times out until one ends. A killable process would lift that
        synchronized(runaways) {
            runaways.removeAll { !it.isAlive }
            if (runaways.size >= RUNAWAY_LIMIT) return RegexOutcome.TimedOut
        }
        val thread = Thread(work, "regex-tester").apply { isDaemon = true }
        thread.start()
        return try {
            work.get(maxOf(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS) ?: RegexOutcome.TimedOut
        } catch (_: TimeoutException) {
            synchronized(runaways) { runaways += thread }
            RegexOutcome.TimedOut
        } catch (_: ExecutionException) {
            // matcher stack overflow
            RegexOutcome.TimedOut
        } finally {
            abandoned.set(true)
        }
    }
}
