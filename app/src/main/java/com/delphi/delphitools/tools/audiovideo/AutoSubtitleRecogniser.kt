package com.delphi.delphitools.tools.audiovideo

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionPart
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume

enum class TranscribeFailure(val message: String) {
    Unreadable("Couldn't read that file, try WAV or MP4"),
    Failed("Transcription failed. Try again"),
    Unavailable("No on-device speech model is installed. Add it in Settings."),
    Denied("Speech recognition is switched off for this app. Allow it in settings."),
}

class TranscribeException(val failure: TranscribeFailure) : Exception(failure.name)

/**
 * dt-ios SubtitleTranscriber over android.speech: on-device only, never requests a model download.
 * File input needs API 33 (EXTRA_AUDIO_SOURCE); word timing needs API 34 (RecognitionPart), and
 * without it each chunk's words are spread over the chunk.
 */
object AutoSubtitleRecogniser {
    // feeding share of the progress, after the decode
    private const val DECODE_SHARE = 0.1

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun intent(language: String?) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        if (language != null) putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, AutoSubtitleEngine.RATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_TIMING, true)
    }

    /** BCP 47 tags the on-device recogniser supports, and the ones of those installed; null when there is none. */
    suspend fun languages(context: Context): Pair<List<String>, Set<String>>? = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) return@withContext null
        val recogniser = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        try {
            suspendCancellableCoroutine { done ->
                recogniser.checkRecognitionSupport(intent(null), context.mainExecutor, object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        val installed = support.installedOnDeviceLanguages.toSet()
                        done.resume((installed + support.supportedOnDeviceLanguages).toList() to installed)
                    }

                    override fun onError(error: Int) = done.resume(null)
                })
            }
        } finally {
            recogniser.destroy()
        }
    }

    private class Heard {
        val parts = mutableListOf<HeardPart>()
        val texts = mutableListOf<String>()

        fun add(bundle: Bundle) {
            bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(texts::add)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
            val found = bundle.getParcelableArrayList(SpeechRecognizer.RECOGNITION_PARTS, RecognitionPart::class.java) ?: return
            found.mapTo(parts) { HeardPart(it.formattedText ?: it.rawText, it.timestampMillis) }
        }
    }

    /** Decodes [uri] to 16 kHz mono, then recognises it chunk by chunk; [progress] runs 0 to 1. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun transcribe(context: Context, uri: Uri, language: String, scratch: File, progress: (Double) -> Unit): List<SubtitleCue> {
        val durationUs = runCatching { AVEditAudio.durationUs(context, uri) }.getOrDefault(0L)
        var speech: SpeechPcm? = null
        try {
            withContext(Dispatchers.IO) {
                scratch.parentFile?.mkdirs()
                scratch.outputStream().buffered().use { out ->
                    AVEditMedia.decode(context, uri) { pcm, rate, channels, ptsUs ->
                        val converter = speech ?: SpeechPcm(rate, channels).also { speech = it }
                        val mono = converter.feed(pcm)
                        val bytes = ByteBuffer.allocate(mono.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                        bytes.asShortBuffer().put(mono)
                        out.write(bytes.array())
                        if (durationUs > 0) progress(DECODE_SHARE * (ptsUs.toDouble() / durationUs).coerceIn(0.0, 1.0))
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            throw TranscribeException(TranscribeFailure.Unreadable)
        }
        val energy = speech?.energy() ?: throw TranscribeException(TranscribeFailure.Unreadable)
        val chunks = AutoSubtitleEngine.chunks(energy)
        val total = energy.size.toDouble().coerceAtLeast(1.0)
        val frameSeconds = AutoSubtitleEngine.FRAME_SAMPLES.toDouble() / AutoSubtitleEngine.RATE
        val words = mutableListOf<SubtitleWord>()
        val recogniser = withContext(Dispatchers.Main) { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
        try {
            RandomAccessFile(scratch, "r").use { file ->
                for (chunk in chunks) {
                    val bytes = withContext(Dispatchers.IO) {
                        val from = chunk.first.toLong() * AutoSubtitleEngine.FRAME_SAMPLES * 2
                        val length = (minOf(file.length(), (chunk.last + 1L) * AutoSubtitleEngine.FRAME_SAMPLES * 2) - from).toInt()
                        ByteArray(length.coerceAtLeast(0)).also {
                            file.seek(from)
                            file.readFully(it)
                        }
                    }
                    val heard = session(recogniser, language, bytes) { fed ->
                        progress(DECODE_SHARE + (1 - DECODE_SHARE) * (chunk.first + fed * chunk.count()) / total)
                    }
                    val start = chunk.first * frameSeconds
                    val end = minOf((chunk.last + 1) * frameSeconds, bytes.size / 2.0 / AutoSubtitleEngine.RATE + start)
                    words += AutoSubtitleEngine.words(heard.parts, heard.texts, start, end)
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { recogniser.destroy() }
        }
        progress(1.0)
        return AutoSubtitleEngine.cues(words)
    }

    /** One segmented session over [pcm] through a pipe; [fed] gets the share of it the recogniser has read. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun session(recogniser: SpeechRecognizer, language: String, pcm: ByteArray, fed: (Double) -> Unit): Heard = withContext(Dispatchers.Main) {
        val heard = Heard()
        val done = CompletableDeferred<TranscribeFailure?>()
        val (read, write) = ParcelFileDescriptor.createPipe()
        recogniser.setRecognitionListener(object : RecognitionListener {
            override fun onSegmentResults(segmentResults: Bundle) = heard.add(segmentResults)
            override fun onEndOfSegmentedSession() {
                done.complete(null)
            }

            override fun onResults(results: Bundle) {
                heard.add(results)
                done.complete(null)
            }

            override fun onError(error: Int) {
                done.complete(
                    when (error) {
                        // silence
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> null
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> TranscribeFailure.Unavailable
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> TranscribeFailure.Denied
                        else -> if (heard.texts.isEmpty()) TranscribeFailure.Failed else null
                    }
                )
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recogniser.startListening(intent(language).putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read))
        // not a child: a write blocked on a stalled recogniser must not hold the session open; destroy() closes the far end
        val feeder = CoroutineScope(Dispatchers.IO).launch {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(write).use { out ->
                    var offset = 0
                    while (offset < pcm.size) {
                        ensureActive()
                        val length = minOf(8192, pcm.size - offset)
                        out.write(pcm, offset, length)
                        offset += length
                        fed(offset.toDouble() / pcm.size)
                    }
                }
            } catch (_: IOException) {
                // recogniser closed the pipe
            }
        }
        var finished = false
        try {
            // ponytail: a recogniser that never ends the session times out a minute past the chunk's length, keeping what it heard
            val failure = withTimeoutOrNull(pcm.size / 2L * 1000 / AutoSubtitleEngine.RATE + 60_000) { done.await() }
            finished = done.isCompleted
            failure?.let { throw TranscribeException(it) }
            heard
        } finally {
            if (!finished) recogniser.cancel()
            runCatching { read.close() }
            feeder.cancel()
        }
    }
}
