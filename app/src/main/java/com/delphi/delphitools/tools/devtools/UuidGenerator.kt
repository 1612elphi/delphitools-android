package com.delphi.delphitools.tools.devtools

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.ViewModel
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
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.CanonValueLayout
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.tools.FileExport
import java.util.Locale

/** dt-ios UUIDGeneratorViewModel; toggles never regenerate ids, a resize keeps the existing ones. */
class UuidGeneratorModel : ViewModel() {
    var kind by mutableStateOf(IdentifierKind.Uuid4)
        private set
    var count by mutableIntStateOf(10)
        private set
    var uppercase by mutableStateOf(false)
        private set
    var stripHyphens by mutableStateOf(false)
        private set
    var ids by mutableStateOf(UuidEngine.generate(IdentifierKind.Uuid4, 10))
        private set

    /** The identifier the banner draws; a tap on a row moves it. */
    var selected by mutableIntStateOf(0)
        private set

    /** Bumped when the banner scrambles: new identifiers, or another row picked. */
    var scrambleKey by mutableIntStateOf(0)
        private set

    /** The last change made new identifiers: the rows fade in. */
    var fades by mutableStateOf(false)
        private set

    val isUuid get() = kind != IdentifierKind.NanoId
    val rows get() = ids.map { UuidEngine.format(it, kind, uppercase, stripHyphens) }

    fun pick(kind: IdentifierKind) {
        if (kind == this.kind) return
        this.kind = kind
        regenerate()
    }

    fun regenerate() {
        ids = UuidEngine.generate(kind, count)
        selected = selected.coerceIn(0, ids.size - 1)
        scrambleKey++
        fades = true
    }

    fun format(uppercase: Boolean = this.uppercase, stripHyphens: Boolean = this.stripHyphens) {
        this.uppercase = uppercase
        this.stripHyphens = stripHyphens
        fades = false
    }

    fun resize(count: Int) {
        this.count = UuidEngine.clamp(count)
        ids = if (ids.size >= this.count) ids.take(this.count) else ids + List(this.count - ids.size) { UuidEngine.generate(kind) }
        selected = selected.coerceIn(0, ids.size - 1)
        fades = false
    }

    fun select(index: Int) {
        if (index == selected) return
        selected = index
        scrambleKey++
        fades = false
    }
}

@Composable
fun UuidGenerator(model: UuidGeneratorModel = viewModel()) {
    val context = LocalContext.current
    val view = LocalView.current
    val kinds = IdentifierKind.entries
    val values = model.rows
    val joined = values.joinToString("\n")

    val controls = CanonSection.of {
        CanonRows {
            CanonHeading("Kind")
            CanonSegmented(kinds.map { it.label }, kinds.indexOf(model.kind)) { model.pick(kinds[it]) }
            CanonHeading("Options")
            // ponytail: nano id dims the switches only; CanonRow has no muted title yet
            CanonGrid(columns = 1) {
                CanonRow("Uppercase") { CanonSwitch(model.uppercase, enabled = model.isUuid) { model.format(uppercase = it) } }
                CanonRow("Strip hyphens") { CanonSwitch(model.stripHyphens, enabled = model.isUuid) { model.format(stripHyphens = it) } }
            }
            CanonButton(
                "Regenerate",
                kind = CanonButtonKind.Primary,
                icon = Icons.Outlined.Refresh,
                oversized = true,
                onClick = model::regenerate,
            )
        }
    }

    val list = CanonSection.of {
        CanonRows {
            CanonHeading(model.kind.label)
            CanonRow("Count", flush = true) {
                CanonNumberField(model.count, model::resize, Modifier.fillMaxSize(), range = UuidEngine.COUNT_MIN..UuidEngine.COUNT_MAX)
            }
            CanonGrid {
                CanonButton("Copy all", Modifier.weight(1f).fillMaxHeight(), icon = Icons.Outlined.ContentCopy, flashesCopied = true) {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", joined))
                }
                CanonButton("Share", Modifier.weight(1f).fillMaxHeight(), icon = Icons.Outlined.Share) {
                    FileExport.shareText(context, joined)
                }
            }
            values.forEachIndexed { index, value ->
                CanonValueLine(
                    String.format(Locale.ROOT, "%02d", index + 1),
                    value,
                    layout = CanonValueLayout.Numbered,
                    isLit = index == model.selected,
                    // stagger: first screenful only
                    fadeDelayMillis = if (model.fades && index < 24) index * 18 else null,
                    onCopy = {
                        CanonCopyFeedback.copy(view, value)
                        model.select(index)
                    },
                )
            }
        }
    }

    val selected = model.selected.coerceIn(0, values.size - 1)
    val banner = CanonSection.canvas(CanonCanvasSize.Fitting) {
        UuidAnatomyView(values[selected], model.ids[selected], model.kind, model.scrambleKey)
    }

    CanonSplit(sidebar = listOf(controls), main = listOf(list), banner = banner)
}
