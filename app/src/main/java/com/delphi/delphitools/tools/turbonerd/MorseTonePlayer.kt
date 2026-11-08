package com.delphi.delphitools.tools.turbonerd

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Plays a Morse string as a synthesised sine tone, streamed from one PCM buffer. Nothing is recorded or written. */
class MorseTonePlayer(private val scope: CoroutineScope) {
    var isPlaying by mutableStateOf(false)
        private set
    var isPaused by mutableStateOf(false)
        private set

    /** Plays the message again and again, a word gap between repeats; read at the end of each pass. */
    // ponytail: switching loop on while the last pass drains takes effect on the next play
    var looping = false

    private var track: AudioTrack? = null
    private var job: Job? = null
    private var length = 0

    // keypad tones, one static track per symbol at keyedWpm
    private val keyed = mutableMapOf<Char, AudioTrack>()
    private var keyedWpm = 0

    private fun build(mode: Int, bytes: Int): AudioTrack? = runCatching {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(MorseEngine.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(mode)
            .setBufferSizeInBytes(bytes)
            .build()
    }.getOrNull()

    /** Sounds one keyed dot or dash ([symbol] `.` or `-`) at [wpm], over any earlier key tone. */
    fun key(symbol: Char, wpm: Int) {
        if (wpm != keyedWpm) {
            releaseKeys()
            keyedWpm = wpm
        }
        val track = keyed[symbol] ?: run {
            val samples = MorseEngine.render(symbol.toString(), wpm) ?: return
            build(AudioTrack.MODE_STATIC, samples.size * 2)?.also {
                it.write(samples, 0, samples.size)
                keyed[symbol] = it
            } ?: return
        }
        track.stop()
        track.reloadStaticData()
        track.play()
    }

    fun releaseKeys() {
        keyed.values.forEach { it.release() }
        keyed.clear()
    }

    fun play(morse: String, wpm: Int, loops: Boolean = false) {
        stop()
        looping = loops
        val samples = MorseEngine.render(morse, wpm) ?: return
        val minimum = AudioTrack.getMinBufferSize(MorseEngine.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = build(AudioTrack.MODE_STREAM, minimum * 2)?.also { it.play() } ?: return
        this.track = track
        length = samples.size
        isPlaying = true
        job = scope.launch {
            try {
                var written = 0L
                var offset = 0
                while (true) {
                    if (offset == samples.size) {
                        if (!looping) break
                        offset = 0
                    }
                    val count = track.write(samples, offset, samples.size - offset, AudioTrack.WRITE_NON_BLOCKING)
                    if (count < 0) return@launch finish()
                    offset += count
                    written += count
                    if (count == 0) delay(10)
                }
                // a stream track holds back a part-filled buffer; a buffer of silence pushes the tail out
                val silence = ShortArray(minimum)
                var padded = 0
                while (padded < silence.size) {
                    val count = track.write(silence, padded, silence.size - padded, AudioTrack.WRITE_NON_BLOCKING)
                    if (count < 0) break
                    padded += count
                    if (count == 0) delay(10)
                }
                while (head(track) < written) delay(10)
                finish()
            } finally {
                track.release()
            }
        }
    }

    private fun head(track: AudioTrack) = track.playbackHeadPosition.toLong() and 0xffffffffL

    /** Seconds into the message and the passes completed, while playing or paused. */
    val position: Pair<Double, Int>?
        get() {
            val track = track ?: return null
            if (!isPlaying || length == 0) return null
            val total = head(track)
            return (total % length).toDouble() / MorseEngine.SAMPLE_RATE to (total / length).toInt()
        }

    fun pause() {
        if (!isPlaying || isPaused) return
        track?.pause()
        isPaused = true
    }

    fun resume() {
        if (!isPlaying || !isPaused) return
        isPaused = false
        track?.play()
    }

    fun stop() {
        if (!isPlaying) return
        job?.cancel()
        finish()
    }

    private fun finish() {
        job = null
        track = null
        isPlaying = false
        isPaused = false
    }
}
