package com.delphi.delphitools.tools.devtools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonBar
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCopyFeedback
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMenuCell
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonTextInput
import com.delphi.delphitools.canon.CanonTextOutput
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val outputStyle = TextStyle(fontFamily = DelphiMono, fontSize = 15.sp)
private val keyStyle = TextStyle(fontFamily = DelphiMono, fontSize = 17.sp)
private val candidateText = TextStyle(fontFamily = DelphiMono, fontSize = 14.sp)
private val scoreText = TextStyle(fontFamily = DelphiMono, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
private val nameText = TextStyle(fontFamily = DelphiFont, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
private val footnote = TextStyle(fontFamily = DelphiFont, fontSize = 12.sp)

private val reference = listOf(
    "Classical (alphabet) ciphers" to listOf(
        "Caesar — fixed shift along the alphabet (key: 1–25).",
        "ROT13 — Caesar with shift 13 (self-inverse).",
        "ROT47 — shift 47 across printable ASCII (33–126).",
        "Atbash — A↔Z, B↔Y, … (no key).",
        "Vigenère — repeating-keyword polyalphabetic shift.",
        "Affine — y = (a·x + b) mod 26; a coprime with 26.",
        "Rail fence — zig-zag transposition over N rails.",
    ),
    "Codes" to listOf(
        "Morse code — dots and dashes; / separates words.",
        "A1Z26 — A=1, B=2, …, Z=26.",
        "Bacon's cipher — five-bit A/B groups per letter.",
    ),
    "Encodings" to listOf(
        "Base64 / Base32 / Hex / Binary — byte-level encodings, not ciphers.",
    ),
    "Not included" to listOf(
        "Playfair, Hill, Enigma, custom monoalphabetic substitution, and columnar transposition are not in this tool. They each need substantial setup (5×5 grids, matrix maths, rotor wirings, custom key tables) and are best served by dedicated tools.",
    ),
)

/** dt-ios CipherDecoderViewController's state: auto-decode classifies off the main thread, manual runs one cipher. */
class CipherDecoderModel : ViewModel() {
    var text by mutableStateOf("")
    var input by mutableStateOf("")
        private set
    var manual by mutableStateOf(false)
        private set
    var cipher by mutableStateOf(CipherId.Caesar)
    var encoding by mutableStateOf(false)
    var params by mutableStateOf(CipherParams())
    var key by mutableStateOf("")
        private set
    var candidates by mutableStateOf<List<CipherCandidate>>(emptyList())
        private set
    var referenceOpen by mutableStateOf(false)
    private var classifying: Job? = null
    private var typing: Job? = null

    fun settle(text: String) {
        input = text
        if (!manual) classify()
    }

    fun set(text: String) {
        this.text = text
        settle(text)
    }

    fun pickMode(manual: Boolean) {
        this.manual = manual
        if (!manual) classify()
    }

    fun typeKey(key: String) {
        this.key = key
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(400)
            params = params.copy(vigenereKey = key)
        }
    }

    // 300 decodes: off main
    private fun classify() {
        classifying?.cancel()
        val text = input
        if (text.isBlank()) {
            candidates = emptyList()
            return
        }
        classifying = viewModelScope.launch {
            candidates = withContext(Dispatchers.Default) { CipherEngine.classifyAndDecode(text) }
        }
    }

    fun tweak(candidate: CipherCandidate) {
        cipher = candidate.cipherId
        encoding = false
        candidate.params?.let { params = it }
        typing?.cancel()
        key = params.vigenereKey
        manual = true
    }
}

@Composable
fun CipherDecoder(model: CipherDecoderModel = viewModel()) {
    val text = model.input
    val cipher = model.cipher
    val params = model.params
    val manual = model.manual
    val trimmed = text.trim()

    val result = remember(text, cipher, model.encoding, params, manual) {
        if (!manual) null else runCatching { CipherEngine.runManual(text, cipher, model.encoding, params) }
    }
    val failure = (result?.exceptionOrNull() as? CipherError)?.failure
    val converted = result?.getOrNull() ?: ""

    val source = CanonSection.of {
        CanonRows {
            CanonHeading("Input")
            CanonTextInput(model.text, { model.text = it }, model::settle)
        }
    }

    val settings = CanonSection.of {
        CanonRows {
            CanonHeading("Mode")
            CanonSegmented(listOf("Auto-decode", "Manual"), if (manual) 1 else 0) { model.pickMode(it == 1) }
            if (manual) {
                val ciphers = CipherId.entries
                CanonRow("Cipher", flush = true) {
                    CanonMenuCell(ciphers.map { it.label }, ciphers.indexOf(cipher), { model.cipher = ciphers[it] })
                }
                CanonSegmented(listOf("Decode", "Encode"), if (model.encoding) 1 else 0) { model.encoding = it == 1 }
                when (cipher) {
                    CipherId.Caesar -> CanonRow("Shift", flush = true) {
                        CanonNumberField(params.caesarShift, { model.params = params.copy(caesarShift = it) }, Modifier.fillMaxSize(), range = 1..25)
                    }
                    CipherId.Vigenere -> CanonRow("Key", flush = true) {
                        CanonTextCell(
                            model.key,
                            model::typeKey,
                            Modifier.fillMaxSize(),
                            placeholder = "LEMON",
                            textStyle = keyStyle,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                autoCorrectEnabled = false,
                                imeAction = ImeAction.Done,
                            ),
                        )
                    }
                    CipherId.Affine -> {
                        CanonRow("a", stacked = true) {
                            CanonSegmented(
                                CipherEngine.affineCoprimes.map(Int::toString),
                                CipherEngine.affineCoprimes.indexOf(params.affineA),
                                columns = 6,
                            ) { model.params = params.copy(affineA = CipherEngine.affineCoprimes[it]) }
                        }
                        CanonRow("b", flush = true) {
                            CanonNumberField(params.affineB, { model.params = params.copy(affineB = it) }, Modifier.fillMaxSize(), range = 0..25)
                        }
                    }
                    CipherId.RailFence -> CanonRow("Rails", flush = true) {
                        CanonNumberField(params.rails, { model.params = params.copy(rails = it) }, Modifier.fillMaxSize(), range = 2..10)
                    }
                    else -> {}
                }
            }
        }
    }

    val candidateBlock = CanonSection.of {
        CanonRows {
            CanonHeading(if (trimmed.isEmpty()) "Examples" else "Candidates")
            when {
                trimmed.isEmpty() -> CanonGrid(columns = 3) {
                    for ((label, sample) in CipherEngine.samples) CanonButton(label) { model.set(sample) }
                }
                model.candidates.isEmpty() -> CanonEmptyRow("No confident matches found. Try the Manual tab if you know the cipher.")
                else -> {
                    model.candidates.forEachIndexed { index, candidate ->
                        CandidateRow(candidate, isLit = index == 0, onTweak = if (candidate.params == null) null else ({ model.tweak(candidate) }))
                    }
                    CanonStatusRow(
                        "Candidates are ranked by English-likeness (letter frequency + common words). Vigenère key recovery uses the Index of Coincidence and works best on ciphertext longer than ~50 letters.",
                        CanonTone.Muted,
                    )
                }
            }
        }
    }

    val output = CanonSection.of {
        CanonTextOutput(converted, heading = "Output", fileName = "decoded.txt", style = outputStyle, busy = failure != null) {
            if (failure != null) CanonStatusRow(failure.text, CanonTone.Warn)
        }
    }

    val referenceBlock = CanonSection.of {
        CanonRows {
            CanonButton(
                "Cipher reference",
                icon = if (model.referenceOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            ) { model.referenceOpen = !model.referenceOpen }
            if (model.referenceOpen) {
                for ((title, entries) in reference) {
                    CanonHeading(title)
                    CanonStatusRow(entries.joinToString("\n"), CanonTone.Muted)
                }
            }
        }
    }

    val hero = model.candidates.firstOrNull()
    val scene = when {
        manual -> {
            val (name, detail) = cipherCaption(cipher, params)
            CipherScene(cipher, model.encoding, params, text, converted, name, detail, null)
        }
        hero == null || trimmed.isEmpty() -> CipherScene(CipherId.Caesar, false, CipherParams(), "", "", "Auto-decode", "", null)
        else -> CipherScene(hero.cipherId, false, hero.params ?: CipherParams(), trimmed, hero.output, hero.cipher, hero.detail, hero.score)
    }
    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(CIPHER_BAND_HEIGHT)) { CipherPicture(scene) }

    CanonSplit(
        sidebar = listOf(source, settings),
        main = listOf(if (manual) output else candidateBlock, referenceBlock),
        banner = banner,
        collapsesTo = CIPHER_COLLAPSED_HEIGHT,
    )
}

@Composable
private fun CandidateRow(candidate: CipherCandidate, isLit: Boolean, onTweak: (() -> Unit)?) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val score = "${Math.round(candidate.score * 100)}%"
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(c.border),
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        Column(
            Modifier
                .weight(1f)
                .canonClickable { CanonCopyFeedback.copy(view, candidate.output) }
                .background(if (isLit) c.accentBg else c.card)
                .semantics {
                    contentDescription = listOf(candidate.cipher, candidate.detail, score).filter { it.isNotEmpty() }.joinToString(", ")
                    stateDescription = candidate.output
                }
                .padding(start = CanonMetrics.panelPadding, top = 12.dp, end = CanonMetrics.panelPadding, bottom = 14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(candidate.cipher, style = nameText, color = c.foreground, maxLines = 1, modifier = Modifier.alignByBaseline())
                Text(
                    candidate.detail,
                    style = footnote,
                    color = c.mutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
                Text(score, style = scoreText, color = if (isLit) c.primary else c.mutedForeground, modifier = Modifier.alignByBaseline())
            }
            Spacer(Modifier.height(8.dp))
            CanonBar(candidate.score.toFloat(), Modifier.fillMaxWidth().height(3.dp), fill = if (isLit) c.primary else c.mutedForeground)
            Spacer(Modifier.height(10.dp))
            Text(candidate.output, style = candidateText, color = c.foreground, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (onTweak != null) {
            CanonButton("", Modifier.width(56.dp).fillMaxHeight(), icon = Icons.Outlined.Tune, contentDescription = "Tweak", onClick = onTweak)
        }
    }
}
