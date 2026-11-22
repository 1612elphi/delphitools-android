package com.delphi.delphitools.tools.devtools

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCopyFeedback
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.CanonValueLayout
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val BULK_COUNTS = listOf(5, 10, 20)
private val SEPARATORS = listOf("-" to "Hyphen", " " to "Space", "." to "Period", "_" to "Underscore")

// eff large list is fixed
private const val EFF_LIST_SIZE = 7776

/** dt-ios PasswordGeneratorViewModel; every settings change makes a new list. */
class PasswordGeneratorModel(application: Application) : AndroidViewModel(application) {
    var passphraseMode by mutableStateOf(false)
        private set
    var spec by mutableStateOf(PasswordEngine.PasswordSpec())
        private set
    var phraseSpec by mutableStateOf(PasswordEngine.PassphraseSpec())
        private set
    var bulkCount by mutableIntStateOf(10)
        private set
    var list by mutableStateOf<List<String>>(emptyList())
        private set
    var wordlist by mutableStateOf<List<String>>(emptyList())
        private set
    var rolls by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var selected by mutableIntStateOf(0)
        private set

    /** Bumped when the banner scrambles: a new list, or another row picked. */
    var scrambleKey by mutableIntStateOf(0)
        private set
    var fades by mutableStateOf(false)
        private set

    val bits
        get() = if (passphraseMode) {
            PasswordEngine.passphraseEntropy(phraseSpec.words, wordlist.size.takeIf { it > 0 } ?: EFF_LIST_SIZE, phraseSpec.trailingDigit)
        } else {
            PasswordEngine.passwordEntropy(spec.length, PasswordEngine.charset(spec).size)
        }

    init {
        // passwords skip word list
        regenerate(animated = false)
        val assets = application.assets
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                val text = runCatching { assets.open("password-eff-wordlist.txt").reader().use { it.readText() } }.getOrDefault("")
                PasswordEngine.parseRolls(text) to PasswordEngine.parseWordlist(text)
            }
            rolls = parsed.first
            wordlist = parsed.second
            regenerate(animated = false)
        }
    }

    fun regenerate(animated: Boolean = true) {
        list = when {
            !passphraseMode -> List(bulkCount) { PasswordEngine.password(spec) }
            wordlist.isEmpty() -> emptyList()
            else -> List(bulkCount) { PasswordEngine.passphrase(wordlist, phraseSpec) }
        }
        selected = if (list.isEmpty()) 0 else selected.coerceIn(0, list.size - 1)
        fades = animated
        if (animated) scrambleKey++
    }

    fun setMode(phrase: Boolean) {
        passphraseMode = phrase
        regenerate()
    }

    fun update(spec: PasswordEngine.PasswordSpec) {
        // at least one class stays on
        if (PasswordEngine.charset(spec).isEmpty()) return
        this.spec = spec
        regenerate()
    }

    fun updatePhrase(spec: PasswordEngine.PassphraseSpec) {
        phraseSpec = spec
        regenerate()
    }

    fun setCount(count: Int) {
        bulkCount = count
        regenerate()
    }

    fun select(index: Int) {
        if (index == selected) return
        selected = index
        fades = false
        scrambleKey++
    }
}

@Composable
fun PasswordGenerator(model: PasswordGeneratorModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val phrase = model.passphraseMode
    val spec = model.spec
    val phraseSpec = model.phraseSpec
    val values = model.list
    val joined = values.joinToString("\n")

    val controls = CanonSection.of {
        CanonRows {
            CanonHeading("Mode")
            CanonSegmented(listOf("Password", "Passphrase"), if (phrase) 1 else 0) { model.setMode(it == 1) }
            if (!phrase) {
                CanonRow("Length", flush = true) {
                    CanonNumberField(
                        spec.length,
                        { model.update(spec.copy(length = it)) },
                        Modifier.fillMaxSize(),
                        range = PasswordEngine.MINIMUM_LENGTH..PasswordEngine.MAXIMUM_LENGTH,
                    )
                }
                CanonHeading("Characters")
                CanonGrid(columns = 2) {
                    val classes = listOf(
                        Triple("Lowercase", spec.lowercase) { on: Boolean -> spec.copy(lowercase = on) },
                        Triple("Uppercase", spec.uppercase) { on: Boolean -> spec.copy(uppercase = on) },
                        Triple("Digits", spec.digits) { on: Boolean -> spec.copy(digits = on) },
                        Triple("Symbols", spec.symbols) { on: Boolean -> spec.copy(symbols = on) },
                    )
                    for ((name, on, change) in classes) {
                        CanonRow(name, sizingTitle = "Lowercase") { CanonSwitch(on) { model.update(change(it)) } }
                    }
                }
                // ponytail: detail is in the caption face; iOS sets the ambiguous set in mono
                CanonRow("Exclude ambiguous", detail = PasswordEngine.AMBIGUOUS) {
                    CanonSwitch(spec.excludeAmbiguous) { model.update(spec.copy(excludeAmbiguous = it)) }
                }
            } else {
                CanonRow("Words", flush = true) {
                    CanonNumberField(
                        phraseSpec.words,
                        { model.updatePhrase(phraseSpec.copy(words = it)) },
                        Modifier.fillMaxSize(),
                        range = PasswordEngine.MINIMUM_WORDS..PasswordEngine.MAXIMUM_WORDS,
                    )
                }
                CanonHeading("Separator")
                // labels match web tool
                CanonSegmented(
                    SEPARATORS.map { (value, label) -> if (value == " ") label else value },
                    SEPARATORS.indexOfFirst { it.first == phraseSpec.separator },
                ) { model.updatePhrase(phraseSpec.copy(separator = SEPARATORS[it].first)) }
                CanonRow("Capitalise first") {
                    CanonSwitch(phraseSpec.capitaliseFirst) { model.updatePhrase(phraseSpec.copy(capitaliseFirst = it)) }
                }
                CanonRow("Trailing digit") {
                    CanonSwitch(phraseSpec.trailingDigit) { model.updatePhrase(phraseSpec.copy(trailingDigit = it)) }
                }
                if (model.wordlist.isEmpty()) CanonStatusRow("Loading…", CanonTone.Muted)
            }
            CanonHeading("Count")
            CanonSegmented(BULK_COUNTS.map(Int::toString), BULK_COUNTS.indexOf(model.bulkCount)) { model.setCount(BULK_COUNTS[it]) }
            CanonButton(
                "Generate",
                kind = CanonButtonKind.Primary,
                icon = Icons.Outlined.Refresh,
                oversized = true,
                enabled = !phrase || model.wordlist.isNotEmpty(),
            ) { model.regenerate() }
        }
    }

    val list = CanonSection.of {
        CanonRows {
            CanonHeading("Generated")
            CanonGrid {
                CanonButton(
                    "Copy all",
                    Modifier.weight(1f).fillMaxHeight(),
                    enabled = values.isNotEmpty(),
                    icon = Icons.Outlined.ContentCopy,
                    flashesCopied = true,
                ) {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", joined))
                }
                CanonButton("Export", Modifier.weight(1f).fillMaxHeight(), enabled = values.isNotEmpty(), icon = Icons.Outlined.Share) {
                    scope.launch { FileExport.share(context, joined.toByteArray(), "passwords.txt", "text/plain") }
                }
            }
            values.forEachIndexed { index, value ->
                CanonValueLine(
                    String.format(Locale.ROOT, "%02d", index + 1),
                    value,
                    layout = CanonValueLayout.NumberedWraps,
                    isLit = index == model.selected,
                    fadeDelayMillis = if (model.fades) index * 18 else null,
                    tint = if (phrase) { char -> passphraseTint(c, char) } else { char -> passwordTint(c, char) },
                    onCopy = {
                        CanonCopyFeedback.copy(view, value)
                        model.select(index)
                    },
                )
            }
        }
    }

    val banner = CanonSection.canvas(CanonCanvasSize.Fitting) {
        PasswordPicture(
            values.getOrNull(model.selected),
            phrase,
            spec,
            phraseSpec,
            model.rolls,
            model.bits,
            model.scrambleKey,
        )
    }

    CanonSplit(sidebar = listOf(controls), main = listOf(list), banner = banner)
}
