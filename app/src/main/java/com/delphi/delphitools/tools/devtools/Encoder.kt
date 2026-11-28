package com.delphi.delphitools.tools.devtools

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSharedText
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTextInput
import com.delphi.delphitools.canon.CanonTextOutput
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.CanonValueLayout
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.ui.theme.DelphiMono

enum class EncoderScheme(val label: String) { Base64("Base64"), Url("URL"), Hash("Hash") }

private val outputStyle = TextStyle(fontFamily = DelphiMono, fontSize = 15.sp)

class EncoderModel : ViewModel() {
    var scheme by mutableStateOf(EncoderScheme.Base64)
    var decoding by mutableStateOf(false)
    var algorithm by mutableStateOf(HashAlgorithm.Sha256)

    /** What the input shows; [settled] follows it once typing pauses. */
    var text by mutableStateOf("")
    var settled by mutableStateOf("")
        private set

    fun settle(text: String) {
        settled = text
    }

    /** The output becomes the input, the direction turns round. */
    fun swap(converted: String) {
        if (converted.isEmpty()) return
        decoding = !decoding
        text = converted
        settled = converted
    }
}

@Composable
fun Encoder(model: EncoderModel = viewModel()) {
    CanonSharedText {
        model.text = it
        model.settle(it)
    }
    val text = model.settled
    val scheme = model.scheme
    val decoding = model.decoding
    val hashing = scheme == EncoderScheme.Hash

    val result = remember(text, scheme, decoding) {
        when (scheme) {
            EncoderScheme.Base64 -> if (decoding) EncoderEngine.base64Decode(text) else Result.success(EncoderEngine.base64Encode(text))
            EncoderScheme.Url -> if (decoding) EncoderEngine.urlDecode(text) else Result.success(EncoderEngine.urlEncode(text))
            EncoderScheme.Hash -> Result.success("")
        }
    }
    val failure = (result.exceptionOrNull() as? EncoderError)?.failure
    val converted = result.getOrDefault("")
    val heading = if (decoding) "Text" else scheme.label
    val digests = remember(text, hashing) { if (hashing && text.isNotEmpty()) EncoderEngine.digests(text) else emptyMap() }
    // both pictures stay current, so the one fading out keeps its content
    val reading = remember(text, decoding) {
        if (decoding) BitRegroup.decoding(EncoderEngine.base64Characters(text)) else BitRegroup.encoding(text.toByteArray())
    }
    val pairs = remember(text, decoding) {
        (if (decoding) EncoderEngine.urlDecodePairs(text) else EncoderEngine.urlEncodePairs(text)).map {
            if (it.newline) CipherPair.Newline else CipherPair(CipherFace.Text(it.top), CipherFace.Text(it.bottom), changes = it.changes)
        }
    }

    val controls = CanonSection.of {
        CanonRows {
            CanonHeading("Mode")
            val schemes = EncoderScheme.entries
            CanonSegmented(schemes.map { it.label }, schemes.indexOf(scheme)) { model.scheme = schemes[it] }
            if (!hashing) {
                CanonSegmented(listOf("Encode", "Decode"), if (decoding) 1 else 0) { model.decoding = it == 1 }
            } else {
                val algorithms = HashAlgorithm.entries
                CanonSegmented(algorithms.map { it.label }, algorithms.indexOf(model.algorithm)) { model.algorithm = algorithms[it] }
            }
            CanonHeading("Input")
            CanonTextInput(model.text, { model.text = it }, model::settle)
        }
    }

    val output = CanonSection.of {
        CanonTextOutput(
            converted,
            heading = heading,
            fileName = "encoded.txt",
            style = outputStyle,
            busy = failure != null,
        ) {
            if (failure != null) CanonStatusRow(failure.text, CanonTone.Warn)
            CanonGrid {
                CanonButton("Swap", Modifier.weight(1f).fillMaxHeight(), enabled = converted.isNotEmpty(), icon = pencil(R.drawable.pencil_arrow_up_down)) {
                    model.swap(converted)
                }
            }
        }
    }

    val hashes = CanonSection.of {
        CanonRows {
            CanonHeading("Hashes")
            for (algorithm in HashAlgorithm.entries) {
                CanonValueLine(
                    algorithm.label,
                    digests[algorithm]?.let(EncoderEngine::hex),
                    layout = CanonValueLayout.Above,
                    isLit = algorithm == model.algorithm,
                )
            }
        }
    }

    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(172.dp)) {
        Crossfade(
            scheme,
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = if (hashing) model.algorithm.label else scheme.label
                    if (!hashing) stateDescription = failure?.text ?: converted
                },
            tween(250),
            label = "picture",
        ) { shown ->
            when (shown) {
                EncoderScheme.Base64 -> BitRegroupStrip(reading)
                EncoderScheme.Url -> CipherPairStrip(pairs)
                EncoderScheme.Hash -> DigestGrid(model.algorithm.label, digests[model.algorithm], model.algorithm.bits)
            }
        }
    }

    CanonSplit(
        sidebar = listOf(controls),
        main = listOf(if (hashing) hashes else output),
        banner = banner,
        collapsesTo = 104.dp,
    )
}
