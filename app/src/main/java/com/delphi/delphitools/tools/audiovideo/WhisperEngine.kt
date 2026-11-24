package com.delphi.delphitools.tools.audiovideo

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The model-free half of Whisper: log-mel features, byte-level token text, and word timing from
 * cross-attention by dynamic time warping (openai/whisper audio.py and timing.py).
 */
object WhisperEngine {
    const val WINDOW_SAMPLES = 30 * AutoSubtitleEngine.RATE
    const val FRAMES = 3000
    private const val N_FFT = 400
    private const val HOP = 160
    private const val BINS = N_FFT / 2 + 1
    // seconds per encoder frame, two mel frames each
    const val FRAME_SECONDS = 0.02
    private const val MEDIAN_WIDTH = 7

    /** ISO 639 codes Whisper was trained on (openai/whisper tokenizer.py LANGUAGES). */
    val languages = (
        "en zh de es ru ko fr ja pt tr pl ca nl ar sv it id hi fi vi he uk el ms cs ro da hu ta no th ur hr bg lt " +
            "la mi ml cy sk te fa lv bn sr az sl kn et mk br eu is hy ne mn bs kk sq sw gl mr pa si km sn yo so af " +
            "oc ka be tg sd gu am yi lo uz fo ht ps tk nn mt sa lb my bo tl mg as tt haw ln ha ba jw su yue"
        ).split(' ')

    // openai split_tokens_on_unicode
    private val unspaced = setOf("zh", "ja", "th", "lo", "my", "yue")

    private fun hzToMel(hz: Double) = if (hz < 1000) 3 * hz / 200 else 15 + ln(hz / 1000) * 27 / ln(6.4)
    private fun melToHz(mel: Double) = if (mel < 15) 200 * mel / 3 else 1000 * exp(ln(6.4) / 27 * (mel - 15))

    /** Slaney-normalised triangular filters on the slaney mel scale, 0 to 8 kHz: [mels][BINS]. */
    fun melFilters(mels: Int): Array<FloatArray> {
        val top = hzToMel(AutoSubtitleEngine.RATE / 2.0)
        val edges = DoubleArray(mels + 2) { melToHz(top * it / (mels + 1)) }
        return Array(mels) { m ->
            val norm = 2 / (edges[m + 2] - edges[m])
            FloatArray(BINS) { bin ->
                val hz = bin * AutoSubtitleEngine.RATE / 2.0 / (BINS - 1)
                val down = (hz - edges[m]) / (edges[m + 1] - edges[m])
                val up = (edges[m + 2] - hz) / (edges[m + 2] - edges[m + 1])
                (max(0.0, min(down, up)) * norm).toFloat()
            }
        }
    }

    private val window = FloatArray(N_FFT) { (0.5 - 0.5 * cos(2 * PI * it / N_FFT)).toFloat() }
    private val cosines by lazy { FloatArray(BINS * N_FFT) { cos(2 * PI * (it / N_FFT) * (it % N_FFT) / N_FFT).toFloat() } }
    private val sines by lazy { FloatArray(BINS * N_FFT) { sin(2 * PI * (it / N_FFT) * (it % N_FFT) / N_FFT).toFloat() } }

    /**
     * Whisper's input features for up to 30 s of 16 kHz mono [samples], zero-padded:
     * [filters].size × [FRAMES] row-major, as the encoder takes them.
     */
    fun logMel(samples: FloatArray, filters: Array<FloatArray>): FloatArray {
        val mels = filters.size
        val out = FloatArray(mels * FRAMES)
        val length = min(samples.size, WINDOW_SAMPLES)
        fun sample(index: Int): Float {
            // reflect padding, 30 s
            val i = if (index < 0) -index else if (index >= WINDOW_SAMPLES) 2 * WINDOW_SAMPLES - 2 - index else index
            return if (i < length) samples[i] else 0f
        }
        val frame = FloatArray(N_FFT)
        val power = FloatArray(BINS)
        val reached = min(FRAMES, (length + N_FFT / 2) / HOP + 1)
        val silent = log10(1e-10).toFloat()
        var peak = silent
        for (t in 0 until FRAMES) {
            if (t >= reached) {
                for (m in 0 until mels) out[m * FRAMES + t] = silent
                continue
            }
            // ponytail: plain DFT, about 80 k multiply-adds a frame; a mixed-radix FFT if windows decode slowly
            for (n in 0 until N_FFT) frame[n] = sample(t * HOP - N_FFT / 2 + n) * window[n]
            for (k in 0 until BINS) {
                var re = 0f
                var im = 0f
                val row = k * N_FFT
                for (n in 0 until N_FFT) {
                    re += frame[n] * cosines[row + n]
                    im -= frame[n] * sines[row + n]
                }
                power[k] = re * re + im * im
            }
            for (m in 0 until mels) {
                val filter = filters[m]
                var sum = 0f
                for (k in 0 until BINS) sum += filter[k] * power[k]
                val value = log10(max(sum, 1e-10f))
                out[m * FRAMES + t] = value
                if (value > peak) peak = value
            }
        }
        val floor = peak - 8
        for (i in out.indices) out[i] = (max(out[i], floor) + 4) / 4
        return out
    }

    // gpt-2 bytes_to_unicode, reversed
    private val byteOf: Map<Char, Byte> = run {
        val direct = ('!'.code..'~'.code) + ('¡'.code..'¬'.code) + ('®'.code..'ÿ'.code)
        val map = HashMap<Char, Byte>()
        direct.forEach { map[it.toChar()] = it.toByte() }
        var shifted = 0
        for (b in 0 until 256) if (b !in direct) map[(256 + shifted++).toChar()] = b.toByte()
        map
    }

    /** The bytes a vocabulary entry stands for. */
    fun tokenBytes(token: String): ByteArray = ByteArray(token.length) { byteOf[token[it]] ?: '?'.code.toByte() }

    private fun utf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        null
    }

    /**
     * Words of one window from its text tokens' bytes and the time each token was spoken from:
     * [times] has one entry per token plus one for the end of the last, in seconds from [offset].
     */
    fun words(tokens: List<ByteArray>, times: FloatArray, language: String, offset: Double): List<SubtitleWord> {
        // split characters stay whole
        class Unit(val text: String, val first: Int, val last: Int)
        val units = mutableListOf<Unit>()
        var pending = ByteArray(0)
        var first = 0
        tokens.forEachIndexed { index, bytes ->
            pending += bytes
            val text = utf8(pending) ?: return@forEachIndexed
            units += Unit(text, first, index)
            pending = ByteArray(0)
            first = index + 1
        }
        val words = mutableListOf<SubtitleWord>()
        var text = StringBuilder()
        var start = -1
        var end = -1
        fun flush() {
            val word = text.toString().trim()
            if (word.isNotEmpty() && start >= 0) {
                words += SubtitleWord(word, offset + times[start], offset + times[min(end + 1, times.size - 1)])
            }
            text = StringBuilder()
            start = -1
        }
        for (unit in units) {
            if (language in unspaced || unit.text.startsWith(" ")) flush()
            if (start < 0) start = unit.first
            text.append(unit.text)
            end = unit.last
        }
        flush()
        return words
    }

    /**
     * Each row's time in seconds: [attention] is per alignment head, per decoder row (prompt rows
     * included), the attention over [frames] encoder frames. Rows from [skip] on are timed.
     */
    fun rowTimes(attention: List<List<FloatArray>>, frames: Int, skip: Int): FloatArray {
        val rows = attention.first().size
        val timed = rows - skip
        if (timed <= 0 || frames <= 0) return FloatArray(0)
        val matrix = Array(timed) { FloatArray(frames) }
        for (head in attention) {
            val weights = Array(rows) { head[it].copyOf(frames) }
            for (f in 0 until frames) {
                var mean = 0.0
                for (r in 0 until rows) mean += weights[r][f]
                mean /= rows
                var variance = 0.0
                for (r in 0 until rows) variance += (weights[r][f] - mean).let { it * it }
                val std = sqrt(variance / rows).takeIf { it > 0 } ?: 1.0
                for (r in 0 until rows) weights[r][f] = ((weights[r][f] - mean) / std).toFloat()
            }
            for (r in skip until rows) {
                val filtered = median(weights[r], MEDIAN_WIDTH)
                val target = matrix[r - skip]
                for (f in 0 until frames) target[f] += filtered[f] / attention.size
            }
        }
        val (textIndices, timeIndices) = dtw(Array(timed) { r -> FloatArray(frames) { -matrix[r][it] } })
        val times = FloatArray(timed)
        var row = -1
        for (step in textIndices.indices) {
            if (textIndices[step] != row && textIndices[step] >= 0) {
                row = textIndices[step]
                times[row] = (max(0, timeIndices[step]) * FRAME_SECONDS).toFloat()
            }
        }
        return times
    }

    /** Median over a [width] window, edges reflected (openai timing.median_filter). */
    fun median(row: FloatArray, width: Int): FloatArray {
        val pad = width / 2
        if (row.size <= pad) return row.copyOf()
        val window = FloatArray(width)
        return FloatArray(row.size) { i ->
            for (w in 0 until width) {
                var j = i - pad + w
                if (j < 0) j = -j
                if (j >= row.size) j = 2 * row.size - 2 - j
                window[w] = row[j]
            }
            window.sort()
            window[pad]
        }
    }

    /** openai timing.dtw_cpu: the cheapest monotonic path through [cost], as (row, column) index pairs. */
    fun dtw(cost: Array<FloatArray>): Pair<IntArray, IntArray> {
        val n = cost.size
        val m = cost.firstOrNull()?.size ?: 0
        val total = Array(n + 1) { FloatArray(m + 1) { Float.POSITIVE_INFINITY } }
        val trace = Array(n + 1) { ByteArray(m + 1) { -1 } }
        total[0][0] = 0f
        for (j in 1..m) for (i in 1..n) {
            val c0 = total[i - 1][j - 1]
            val c1 = total[i - 1][j]
            val c2 = total[i][j - 1]
            val (c, t) = when {
                c0 < c1 && c0 < c2 -> c0 to 0
                c1 < c0 && c1 < c2 -> c1 to 1
                else -> c2 to 2
            }
            total[i][j] = cost[i - 1][j - 1] + c
            trace[i][j] = t.toByte()
        }
        for (j in 0..m) trace[0][j] = 2
        for (i in 0..n) trace[i][0] = 1
        val rows = ArrayList<Int>()
        val columns = ArrayList<Int>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            rows += i - 1
            columns += j - 1
            when (trace[i][j].toInt()) {
                0 -> { i--; j-- }
                1 -> i--
                else -> j--
            }
        }
        return rows.reversed().toIntArray() to columns.reversed().toIntArray()
    }
}
