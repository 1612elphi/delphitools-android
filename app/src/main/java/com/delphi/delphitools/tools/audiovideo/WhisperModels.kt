package com.delphi.delphitools.tools.audiovideo

/** One file of a model: its path in the Hugging Face repository, local name, size and SHA-256. */
data class WhisperFile(val path: String, val name: String, val bytes: Long, val sha256: String)

/**
 * dt-ios WhisperModel: the same four tiers, as onnx-community's `_timestamped` ONNX exports (the decoder
 * outputs cross-attention for word timing), pinned to a commit. int8 weights, except q4 for Large v3 Turbo.
 */
data class WhisperModel(val id: String, val label: String, val repo: String, val revision: String, val files: List<WhisperFile>) {
    val bytes get() = files.sumOf { it.bytes }

    fun url(file: WhisperFile) = "https://huggingface.co/$repo/resolve/$revision/${file.path}"

    companion object {
        const val ENCODER = "encoder.onnx"
        const val DECODER = "decoder.onnx"

        private fun files(encoder: String, decoder: String, quant: String, json: List<Pair<Long, String>>, weights: List<Pair<Long, String>>): List<WhisperFile> {
            val names = listOf("config.json", "generation_config.json", "added_tokens.json", "vocab.json")
            return names.mapIndexed { index, name -> WhisperFile(name, name, json[index].first, json[index].second) } + listOf(
                WhisperFile("onnx/${encoder}_$quant.onnx", ENCODER, weights[0].first, weights[0].second),
                WhisperFile("onnx/${decoder}_$quant.onnx", DECODER, weights[1].first, weights[1].second),
            )
        }

        private const val ADDED = "9715fd2243b6f06a5858b5e32950d2853f73dd5bc201aafcf76f5082a2d8acd1"
        private const val VOCAB = "50d6a919f0a0601d56a04eb583c780d18553aa388254ba3158eb6a00f13e2c1a"

        val all = listOf(
            WhisperModel(
                "whisper-tiny", "Tiny", "onnx-community/whisper-tiny_timestamped", "517244293732ee2d58139af5814231b7e6830a0d",
                files(
                    "encoder_model", "decoder_model_merged", "quantized",
                    listOf(
                        2243L to "46aeea0a406afbeb563fc8e59ca10609203df4299af6a83f73752fef369efd2d",
                        3772L to "f5c67e5a4f7102f8cb4d058bc95da276bbc19eeec997267c3bb0f25ef68facd1",
                        34604L to ADDED,
                        1036584L to VOCAB,
                    ),
                    listOf(
                        10097109L to "d4397fbfed1975bb5dd1bedb748808367331c85293f54cbc927774728daec2b2",
                        30730264L to "ced2053c1aa8ff7048927197416a5c169f50010e7b280e5d564b46a991fca011",
                    ),
                ),
            ),
            WhisperModel(
                "whisper-base", "Base", "onnx-community/whisper-base_timestamped", "608c49e61301901684bc36cac8f74b95ff6b5a8e",
                files(
                    "encoder_model", "decoder_model_merged", "quantized",
                    listOf(
                        2243L to "f4d0608f7d918166da7edb3e188de5ef1bfe70d9802e785d271fd88111e9cf4b",
                        3832L to "61070cf8de25b1e9256e8e102ded49d8d24a8369ed36ef84fdf21549e68125a0",
                        34604L to ADDED,
                        1036584L to VOCAB,
                    ),
                    listOf(
                        23159167L to "2714484ebe1bae7c1646e8eadb768bb9d415cf11763466d21f23039a29c62e6f",
                        53712708L to "cf9a8d5bcddc0917a0078135b484cedcaf44f28909cd91910abd29dced9171db",
                    ),
                ),
            ),
            WhisperModel(
                "whisper-small", "Small", "onnx-community/whisper-small_timestamped", "65caa70f294b46e1c33ff820aae6b16d048ab818",
                files(
                    "encoder_model", "decoder_model_merged", "quantized",
                    listOf(
                        2227L to "457854d452f17661e197d74aee12b8e74fb75ba30ebfaa7426d0d61ea1e08a18",
                        3893L to "f538b28220c6a6d6f1af1458d4141cacb4ef4963df3de98a19490440c412ddf0",
                        34604L to ADDED,
                        1036584L to VOCAB,
                    ),
                    listOf(
                        92240498L to "9f5635b596e6084045c7426ca6f6f99db8ce606ea895397c3b4a3c0aa3f9b070",
                        156795750L to "c831996a6d5a535cf17447b0d7ecc36da6b72696e6fb906852ba6136b9199abb",
                    ),
                ),
            ),
            WhisperModel(
                "whisper-large-turbo", "Large v3 Turbo", "onnx-community/whisper-large-v3-turbo_timestamped", "b3f77bf9a8c4d5ea3415827033d1ffea7955fd9a",
                files(
                    "encoder_model", "decoder_model_merged", "q4",
                    listOf(
                        1330L to "32c02d1dfb9a93ff236cdcf0f741f8f3a4cedb437c407a361a022451ac009131",
                        3797L to "8568ddf83476167b4607014956b87142763fa6923e71259a606734beb39bdfc8",
                        34648L to "3c51f66c4c21f9e126970078f11ae77a78c74aee8df606ee9daba86e467108e0",
                        1036558L to "e2aa043ef015641d363d8288e7c241c85e36a5c761fb303598e0710233344387",
                    ),
                    listOf(
                        425003939L to "41ad35ee01012d68837d142d74de34d3bb32dc040a61817de470011219b1b1eb",
                        334207912L to "6bf129462f6bc8722726291e3fdbd8bc07000f66336b4ef1387d5d8d636f4186",
                    ),
                ),
            ),
        )
    }
}

enum class WhisperFailure { Network, Checksum, Space }

/** dt-ios WhisperModelStore.State, with the reason a download failed. */
sealed interface WhisperState {
    data object Absent : WhisperState
    data class Downloading(val fraction: Double) : WhisperState
    data object Installed : WhisperState
    data class Failed(val reason: WhisperFailure) : WhisperState
}

/** The download decisions that need no network: where to resume, which answers to accept, how much room is needed. */
object WhisperDownloadPlan {
    /** Byte offset to request [expected] bytes from, given a partial file of [partial] bytes; 0 restarts. */
    fun resumeFrom(partial: Long, expected: Long): Long = if (partial in 1..expected) partial else 0

    /**
     * The offset the response body starts at, or null when it is unusable: a 206 must start where
     * asked, a 200 is the whole file again.
     */
    fun bodyStart(code: Int, requested: Long, contentRange: String?): Long? = when (code) {
        200 -> 0
        206 -> Regex("^bytes (\\d+)-").find(contentRange ?: "")?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it == requested }
        else -> null
    }

    /** Bytes still to fetch for [model]; [present] is how much of each file is on disk already. */
    fun remaining(model: WhisperModel, present: (WhisperFile) -> Long): Long =
        model.files.sumOf { file -> file.bytes - present(file).coerceIn(0, file.bytes) }

    fun fraction(done: Long, total: Long): Double = if (total <= 0) 0.0 else (done.toDouble() / total).coerceIn(0.0, 1.0)

    /** The state shown for [model] when no download runs: installed when every file is whole. */
    fun settled(model: WhisperModel, size: (WhisperFile) -> Long?, previous: WhisperState?): WhisperState = when {
        model.files.all { size(it) == it.bytes } -> WhisperState.Installed
        previous is WhisperState.Failed -> previous
        else -> WhisperState.Absent
    }

    fun hex(digest: ByteArray) = digest.joinToString("") { "%02x".format(it) }
}
