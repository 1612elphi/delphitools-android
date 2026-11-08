package com.delphi.delphitools.tools.devtools

import android.content.ClipboardManager
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCodeBlock
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTextArea
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TYPING_PAUSE_MS = 400L
private const val DASH = "—"

class JwtDecoderModel : ViewModel() {
    var token by mutableStateOf("")
        private set
    var result by mutableStateOf<JwtResult>(JwtResult.Empty)
        private set
    var nowSeconds by mutableDoubleStateOf(0.0)
        private set
    private var typing: Job? = null

    fun type(text: String) {
        token = text
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_PAUSE_MS)
            refresh()
        }
    }

    fun set(text: String) {
        token = text
        refresh()
    }

    private fun refresh() {
        typing?.cancel()
        nowSeconds = System.currentTimeMillis() / 1000.0
        result = JwtEngine.decode(token, nowSeconds)
    }
}

@Composable
fun JwtDecoder(model: JwtDecoderModel = viewModel()) {
    val context = LocalContext.current
    val result = model.result
    val found = (result as? JwtResult.Segments)?.found

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Token")
            // jwt header prefix
            CanonTextArea(model.token, model::type, placeholder = "eyJhbGciOi…", height = 180.dp, label = "Token")
            if (found != null) CanonStatusRow("Segments $found / 3", CanonTone.Warn)
            CanonGrid {
                CanonButton("Paste", Modifier.weight(1f).fillMaxHeight(), icon = Icons.Outlined.ContentPaste) {
                    context.getSystemService(ClipboardManager::class.java)?.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)?.coerceToText(context)?.toString()
                        ?.let(model::set)
                }
                CanonButton("Clear", Modifier.weight(1f).fillMaxHeight(), enabled = model.token.isNotEmpty(), icon = Icons.Outlined.Delete) {
                    model.set("")
                }
            }
        }
    }

    val details = CanonSection.of {
        CanonRows {
            val decoded = (result as? JwtResult.Decoded)?.decoded
            if (decoded == null) {
                CanonEmptyRow("Paste a token here")
                return@CanonRows
            }
            val tiles = listOf(
                CanonTile("Algorithm", decoded.header.error?.label ?: decoded.algorithm ?: DASH),
                CanonTile("Type", decoded.header.error?.label ?: decoded.tokenType ?: DASH),
                CanonTile("Signature", decoded.signature.error?.label ?: "${decoded.signature.byteCount ?: 0} bytes"),
                CanonTile("Status", JwtEngine.status(decoded, model.nowSeconds)?.text ?: DASH),
            ) + decoded.timeClaims.map { CanonTile(it.label, it.absolute) }
            CanonTiles(tiles)
            for ((name, segment) in listOf("Header" to decoded.header, "Payload" to decoded.payload)) {
                val pretty = segment.pretty
                if (pretty != null) {
                    CanonCodeBlock(pretty, language = name)
                } else {
                    CanonHeading(name)
                    CanonStatusRow(segment.error?.label ?: "", CanonTone.Warn)
                }
            }
            val signatureError = decoded.signature.error
            if (signatureError != null) {
                CanonHeading("Signature")
                CanonStatusRow(signatureError.label, CanonTone.Warn)
            } else {
                CanonCodeBlock(decoded.signature.raw, language = "Signature")
            }
        }
    }

    val banner = CanonSection.canvas(CanonCanvasSize.Fitting) { JwtAnatomyView(result, model.nowSeconds) }

    CanonSplit(sidebar = listOf(input), main = listOf(details), banner = banner)
}
