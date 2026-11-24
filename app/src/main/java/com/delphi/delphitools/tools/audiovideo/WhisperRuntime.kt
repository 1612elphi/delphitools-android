package com.delphi.delphitools.tools.audiovideo

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/**
 * One downloaded Whisper model in ONNX Runtime: the encoder, the merged decoder with its key-value
 * cache, and the token tables, read from the files of [WhisperModel] in [dir]. Not thread-safe.
 */
class WhisperRuntime(dir: File) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val encoder: OrtSession
    private val decoder: OrtSession
    private val filters: Array<FloatArray>
    private val layers: Int
    private val heads: Int
    private val headSize: Int
    private val alignment: List<Pair<Int, Int>>
    private val vocab: Array<ByteArray?>
    private val start: Long
    private val end: Int
    private val transcribe: Long
    private val noTimestamps: Long
    private val noSpeech: Int?
    private val suppressed: Set<Int>
    private val firstSuppressed: Set<Int>
    private val languageTokens: Map<String, Int>

    init {
        fun json(name: String) = JSONObject(File(dir, name).readText())
        val config = json("config.json")
        val generation = json("generation_config.json")
        val added = json("added_tokens.json")
        val words = json("vocab.json")
        filters = WhisperEngine.melFilters(config.getInt("num_mel_bins"))
        layers = config.getInt("decoder_layers")
        heads = config.getInt("decoder_attention_heads")
        headSize = config.getInt("d_model") / heads
        alignment = generation.getJSONArray("alignment_heads").let { all ->
            List(all.length()) { all.getJSONArray(it).getInt(0) to all.getJSONArray(it).getInt(1) }
        }
        val size = config.getInt("vocab_size")
        vocab = arrayOfNulls(size)
        words.keys().forEach { token -> words.getInt(token).takeIf { it < size }?.let { vocab[it] = WhisperEngine.tokenBytes(token) } }
        start = generation.getLong("decoder_start_token_id")
        end = generation.getInt("eos_token_id")
        transcribe = generation.getJSONObject("task_to_id").getLong("transcribe")
        noTimestamps = generation.getLong("no_timestamps_token_id")
        noSpeech = listOf("<|nospeech|>", "<|nocaptions|>").firstNotNullOfOrNull { added.optInt(it, -1).takeIf { id -> id >= 0 } }
        suppressed = generation.getJSONArray("suppress_tokens").let { all -> List(all.length()) { all.getInt(it) }.toSet() }
        firstSuppressed = suppressed + generation.getJSONArray("begin_suppress_tokens").let { all -> List(all.length()) { all.getInt(it) } }
        val languages = generation.getJSONObject("lang_to_id")
        languageTokens = languages.keys().asSequence().associate { it.removePrefix("<|").removeSuffix("|>") to languages.getInt(it) }
        val options = OrtSession.SessionOptions()
        encoder = env.createSession(File(dir, WhisperModel.ENCODER).path, options)
        decoder = env.createSession(File(dir, WhisperModel.DECODER).path, options)
    }

    fun supports(language: String) = language in languageTokens

    private class Step(val result: OrtSession.Result, val rows: Int) : AutoCloseable {
        fun floats(name: String): FloatBuffer = (result.get(name).get() as OnnxTensor).floatBuffer
        fun tensor(name: String) = result.get(name).get() as OnnxTensor
        override fun close() = result.close()
    }

    private fun empty() = OnnxTensor.createTensor(env, FloatBuffer.allocate(0), longArrayOf(1, heads.toLong(), 0, headSize.toLong()))

    /** One decoder pass over [ids]; [cache] is the previous pass for self-attention and [first] for cross-attention. */
    private fun step(ids: LongArray, hidden: OnnxTensor, cache: Step?, first: Step?): Step {
        val owned = mutableListOf<OnnxTensor>()
        fun own(tensor: OnnxTensor) = tensor.also { owned += it }
        try {
            val inputs = HashMap<String, OnnxTensor>()
            inputs["input_ids"] = own(OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong())))
            inputs["encoder_hidden_states"] = hidden
            inputs["use_cache_branch"] = own(OnnxTensor.createTensor(env, booleanArrayOf(cache != null)))
            for (layer in 0 until layers) {
                for (kind in listOf("key", "value")) {
                    inputs["past_key_values.$layer.decoder.$kind"] = cache?.tensor("present.$layer.decoder.$kind") ?: own(empty())
                    inputs["past_key_values.$layer.encoder.$kind"] = first?.tensor("present.$layer.encoder.$kind") ?: own(empty())
                }
            }
            return Step(decoder.run(inputs), ids.size)
        } finally {
            owned.forEach { it.close() }
        }
    }

    private fun lastRow(step: Step): FloatArray {
        val logits = step.floats("logits")
        val size = logits.capacity() / step.rows
        return FloatArray(size).also { logits.position((step.rows - 1) * size); logits.get(it) }
    }

    private fun logSoftmax(row: FloatArray, index: Int): Double {
        val top = row.max()
        var sum = 0.0
        for (value in row) sum += exp((value - top).toDouble())
        return row[index] - top - ln(sum)
    }

    private fun collect(step: Step, into: List<MutableList<FloatArray>>) {
        for ((index, head) in alignment.withIndex()) {
            val weights = step.floats("cross_attentions.${head.first}")
            val frames = weights.capacity() / heads / step.rows
            for (row in 0 until step.rows) {
                val out = FloatArray(frames)
                weights.position((head.second * step.rows + row) * frames)
                weights.get(out)
                into[index] += out
            }
        }
    }

    private fun encode(samples: FloatArray): OrtSession.Result {
        val features = WhisperEngine.logMel(samples, filters)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(features), longArrayOf(1, filters.size.toLong(), WhisperEngine.FRAMES.toLong())).use {
            return encoder.run(mapOf("input_features" to it))
        }
    }

    private fun hidden(encoded: OrtSession.Result) = encoded.get(0) as OnnxTensor

    /**
     * Words of up to 30 s of 16 kHz mono [samples], timed from [offset] seconds, and the language they
     * were read as: [language], or when that is null or unknown to the model, the most likely one.
     * No words when the window is most likely silence. [check] runs between decoder passes and throws to stop.
     */
    fun words(samples: FloatArray, language: String?, offset: Double, check: () -> Unit): Pair<List<SubtitleWord>, String> {
        val tokens = mutableListOf<Int>()
        val attention = List(alignment.size) { mutableListOf<FloatArray>() }
        var logProb = 0.0
        var silence = 0.0
        var read = language?.takeIf(::supports) ?: ""
        encode(samples).use { encoded ->
            if (read.isEmpty()) {
                read = step(longArrayOf(start), hidden(encoded), null, null).use { step ->
                    val row = lastRow(step)
                    languageTokens.maxBy { row[it.value] }.key
                }
            }
            val prompt = longArrayOf(start, languageTokens.getValue(read).toLong(), transcribe, noTimestamps)
            step(prompt, hidden(encoded), null, null).use { first ->
                collect(first, attention)
                noSpeech?.let { id ->
                    val logits = first.floats("logits")
                    val row = FloatArray(logits.capacity() / first.rows).also { logits.position(0); logits.get(it) }
                    silence = exp(logSoftmax(row, id))
                }
                var previous: Step? = null
                try {
                    var row = lastRow(first)
                    while (tokens.size < MAX_TOKENS) {
                        val next = pick(row, if (tokens.isEmpty()) firstSuppressed else suppressed)
                        if (next == end) break
                        logProb += logSoftmax(row, next)
                        tokens += next
                        check()
                        val step = step(longArrayOf(next.toLong()), hidden(encoded), previous ?: first, first)
                        previous?.close()
                        previous = step
                        collect(step, attention)
                        row = lastRow(step)
                    }
                } finally {
                    previous?.close()
                }
            }
        }
        // openai/whisper transcribe.py: no_speech_threshold 0.6, logprob_threshold -1
        if (tokens.isEmpty() || silence > 0.6 && logProb / tokens.size < -1) return emptyList<SubtitleWord>() to read
        val frames = min(WhisperEngine.FRAMES / 2, (samples.size + 319) / 320)
        // rows of sot, language and task are untimed; the no-timestamps row predicts the first word
        val times = WhisperEngine.rowTimes(attention, frames, 3)
        return WhisperEngine.words(tokens.map { vocab[it] ?: ByteArray(0) }, times, read, offset) to read
    }

    private fun pick(row: FloatArray, suppress: Set<Int>): Int {
        var best = end
        var score = Float.NEGATIVE_INFINITY
        for (id in 0..min(end, row.size - 1)) if (id !in suppress && row[id] > score) {
            best = id
            score = row[id]
        }
        return best
    }

    override fun close() {
        encoder.close()
        decoder.close()
    }

    private companion object {
        // openai/whisper decoding.py sample_len: half the 448-token context
        const val MAX_TOKENS = 224
    }
}
