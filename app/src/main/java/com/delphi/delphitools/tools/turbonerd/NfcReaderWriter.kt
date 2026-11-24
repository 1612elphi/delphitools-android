package com.delphi.delphitools.tools.turbonerd

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCodeBlock
import com.delphi.delphitools.canon.CanonCopyFeedback
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonGlyphFlow
import com.delphi.delphitools.canon.CanonGlyphKind
import com.delphi.delphitools.canon.CanonGlyphMetrics
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTextArea
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.CanonValueLayout
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.rememberCanonGlyphFlowState
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import com.delphi.delphitools.ui.theme.DelphiColours
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

private const val UNAVAILABLE = "NFC is not available on this device."
private const val READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
private val BAND_HEIGHT = 150.dp
private val COLLAPSED_HEIGHT = 88.dp

private class NfcFailure(message: String) : Exception(message)

/** dt-ios NFCReaderWriterViewModel and NFCManager: the form, the last read and the scan state. */
class NfcReaderWriterModel(application: Application) : AndroidViewModel(application) {
    private val adapter = NfcAdapter.getDefaultAdapter(application)

    var writing by mutableStateOf(false)
        private set
    var form by mutableStateOf(NfcWriteForm())
        private set

    /** The form as of the last pause in typing; the byte map follows it. */
    var settled by mutableStateOf(NfcWriteForm())
        private set
    var scanned by mutableStateOf<List<NdefShown>>(emptyList())
        private set
    var scanning by mutableStateOf(false)
        private set
    var written by mutableStateOf<Boolean?>(null)
        private set
    var writeFailure by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var available by mutableStateOf(adapter?.isEnabled == true)
        private set

    @Volatile
    private var pending: NdefRecordBytes? = null
    private var typing: Job? = null

    val language: String = Locale.getDefault().language.ifEmpty { "en" }

    fun selectMode(index: Int) {
        writing = index == 1
    }

    fun change(next: NfcWriteForm, settle: Boolean = false) {
        form = next
        typing?.cancel()
        if (settle) {
            settled = next
            return
        }
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            settled = form
        }
    }

    fun refreshAvailability() {
        available = adapter?.isEnabled == true
        if (!available) scanning = false
    }

    fun start() {
        typing?.cancel()
        settled = form
        error = null
        if (!available) {
            error = UNAVAILABLE
            return
        }
        if (writing) {
            written = null
            writeFailure = null
            pending = NfcReaderWriterEngine.build(form, language) ?: run {
                error = "Please fill in the required fields."
                return
            }
        } else {
            pending = null
        }
        scanning = true
    }

    fun cancel() {
        scanning = false
    }

    /** Reader mode's callback, on a binder thread. */
    fun onTag(tag: Tag) {
        val record = pending
        val write = writing
        val result = runCatching { if (write && record != null) write(tag, record) else read(tag) }
        viewModelScope.launch(Dispatchers.Main) {
            if (!scanning) return@launch
            scanning = false
            val failure = result.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
            if (write) {
                written = failure == null
                writeFailure = failure
            } else if (failure != null) {
                error = failure
            } else {
                scanned = result.getOrNull().orEmpty()
            }
        }
    }

    private fun read(tag: Tag): List<NdefShown> {
        val ndef = Ndef.get(tag) ?: throw NfcFailure("No NDEF data on tag.")
        return ndef.use {
            it.connect()
            val message = it.ndefMessage ?: throw NfcFailure("No NDEF data on tag.")
            message.records.map { r -> NfcReaderWriterEngine.describe(NdefRecordBytes(r.tnf.toInt(), r.type.toList(), r.id.toList(), r.payload.toList())) }
        }
    }

    private fun write(tag: Tag, record: NdefRecordBytes): List<NdefShown> {
        val message = NdefMessage(arrayOf(NdefRecord(record.tnf.toShort(), record.type.toByteArray(), record.identifier.toByteArray(), record.payload.toByteArray())))
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.use {
                it.connect()
                if (!it.isWritable) throw NfcFailure("Tag is read-only.")
                if (message.byteArrayLength > it.maxSize) throw NfcFailure("Message too large for this tag.")
                it.writeNdefMessage(message)
            }
        } else {
            val formatable = NdefFormatable.get(tag) ?: throw NfcFailure("Tag is not NDEF compatible.")
            formatable.use {
                it.connect()
                it.format(message)
            }
        }
        return emptyList()
    }

    /** What the byte map and tiles show. */
    val message: List<NdefRecordBytes>
        get() = if (!writing) scanned.map { it.raw } else listOfNotNull(NfcReaderWriterEngine.build(settled, language))
}

@Composable
fun NfcReaderWriter(model: NfcReaderWriterModel = viewModel()) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = LocalActivity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val adapter = remember { NfcAdapter.getDefaultAdapter(context) }
    val scanning = model.scanning

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = model.refreshAvailability()
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    // reader mode polls only while a scan waits and the page is in front
    DisposableEffect(lifecycle, activity, scanning) {
        fun enable() {
            if (scanning && activity != null) runCatching { adapter?.enableReaderMode(activity, model::onTag, READER_FLAGS, null) }
        }
        fun disable() {
            if (activity != null) runCatching { adapter?.disableReaderMode(activity) }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    model.refreshAvailability()
                    enable()
                }
                Lifecycle.Event.ON_PAUSE -> disable()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            disable()
        }
    }

    val form = model.form
    val reading = !model.writing
    val modeBlock = CanonSection.of {
        CanonRows {
            CanonHeading("Mode")
            CanonSegmented(listOf("Read", "Write"), if (reading) 0 else 1, Modifier.semantics { contentDescription = "Mode" }, onSelect = model::selectMode)
        }
    }
    val contentBlock = CanonSection.of {
        CanonRows {
            CanonHeading("Content Type")
            CanonSegmented(
                NfcContentType.entries.map { it.label },
                form.contentType.ordinal,
                Modifier.semantics { contentDescription = "Content Type" },
                columns = 4,
            ) { model.change(form.copy(contentType = NfcContentType.entries[it]), settle = true) }
            val plain = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done)
            when (form.contentType) {
                NfcContentType.Url -> CanonRow("URL", flush = true) {
                    CanonTextCell(form.url, { model.change(form.copy(url = it)) }, placeholder = "https://example.com", width = 220.dp, keyboardOptions = plain.copy(keyboardType = KeyboardType.Uri))
                }
                NfcContentType.Text -> CanonTextArea(form.text, { model.change(form.copy(text = it)) }, placeholder = "Type or paste", height = 128.dp)
                NfcContentType.VCard -> {
                    CanonRow("Name", flush = true) {
                        CanonTextCell(form.name, { model.change(form.copy(name = it)) }, width = 220.dp, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done))
                    }
                    CanonRow("Phone", flush = true) {
                        CanonTextCell(form.phone, { model.change(form.copy(phone = it)) }, width = 220.dp, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done))
                    }
                    CanonRow("Email", flush = true) {
                        CanonTextCell(form.email, { model.change(form.copy(email = it)) }, width = 220.dp, keyboardOptions = plain.copy(keyboardType = KeyboardType.Email))
                    }
                    CanonRow("Organisation", flush = true) {
                        CanonTextCell(form.organisation, { model.change(form.copy(organisation = it)) }, width = 220.dp)
                    }
                }
                NfcContentType.Wifi -> {
                    CanonRow("Network", flush = true) {
                        CanonTextCell(form.ssid, { model.change(form.copy(ssid = it)) }, width = 220.dp, keyboardOptions = plain)
                    }
                    CanonRow("Password", flush = true) {
                        PasswordCell(form.password) { model.change(form.copy(password = it)) }
                    }
                    CanonHeading("Security")
                    CanonSegmented(
                        WifiSecurity.entries.map { it.label },
                        form.security.ordinal,
                        Modifier.semantics { contentDescription = "Security" },
                        columns = 4,
                    ) { model.change(form.copy(security = WifiSecurity.entries[it]), settle = true) }
                }
            }
        }
    }
    val action = CanonSection.of {
        CanonRows {
            val status: Pair<String, CanonTone>? = when {
                model.error != null -> model.error!! to CanonTone.Warn
                !reading && model.written == true -> "Tag written successfully." to CanonTone.Ok
                !reading && model.writeFailure != null -> model.writeFailure!! to CanonTone.Warn
                !model.available -> UNAVAILABLE to CanonTone.Muted
                else -> null
            }
            status?.let { CanonStatusRow(it.first, it.second) }
            if (scanning) CanonRow(if (reading) "Scanning..." else "Waiting for Tag...") { CanonLoader() }
            // ponytail: no system scan sheet on Android, so the button cancels a waiting scan; no 60 s session timeout
            CanonButton(
                when {
                    scanning -> "Cancel"
                    reading -> "Scan NFC Tag"
                    else -> "Write to Tag"
                },
                kind = CanonButtonKind.Primary,
                icon = when {
                    scanning -> Icons.Outlined.Close
                    reading -> Icons.Outlined.Nfc
                    else -> Icons.Outlined.Contactless
                },
                oversized = true,
                enabled = model.available,
            ) { if (scanning) model.cancel() else model.start() }
        }
    }

    val message = model.message
    val segments = remember(message) { NfcReaderWriterEngine.segments(message) }
    val size = NfcReaderWriterEngine.count(segments)
    val records = CanonSection.of {
        CanonRows {
            val shown = model.scanned
            if (shown.isEmpty()) {
                CanonHeading("Message")
                CanonEmptyRow("No Records")
                return@CanonRows
            }
            shown.forEach { record ->
                CanonHeading(record.typeDescription)
                CanonValueLine("Payload", record.payload, layout = CanonValueLayout.Wrapping)
                CanonCodeBlock(NfcReaderWriterEngine.hexLines(record.raw.payload), language = "Hex", maxHeight = 160.dp)
            }
            if (shown.size > 1) {
                val all = shown.joinToString("\n") { "${it.typeDescription}: ${it.payload}" }
                CanonGrid {
                    CanonButton("Copy all", Modifier.weight(1f).fillMaxHeight(), icon = Icons.Outlined.ContentCopy) { CanonCopyFeedback.copy(view, all) }
                }
            }
        }
    }
    val tag = CanonSection.of {
        CanonRows {
            CanonTiles(
                listOf(CanonTile("Size", "${"%,d".format(size)} B")) + NfcReaderWriterEngine.tags.map { (name, bytes) ->
                    val share = size.toDouble() / bytes
                    CanonTile("$name · $bytes B", "${(share * 100).roundToInt()}%", minOf(1f, share.toFloat()))
                },
            )
        }
    }
    val banner = CanonSection.canvas(CanonCanvasSize.Fixed(BAND_HEIGHT)) {
        NdefByteMap(segments, fromStart = reading)
    }

    CanonSplit(
        sidebar = listOfNotNull(modeBlock, contentBlock.takeIf { !reading }, action),
        main = listOfNotNull(records.takeIf { reading }, tag),
        banner = banner,
        collapsesTo = COLLAPSED_HEIGHT,
    )
}

@Composable
private fun PasswordCell(value: String, onChange: (String) -> Unit) {
    CanonTextCell(
        value,
        onChange,
        width = 220.dp,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        visualTransformation = PasswordVisualTransformation(),
    )
}

private data class NdefGlyph(val value: Byte, val role: NdefRole?, val caption: String = "")

private val cellSide = 24.dp
private val hexStyle = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp, fontWeight = FontWeight.Medium)
private val payloadCaption = TextStyle(fontFamily = DelphiMono, fontSize = 10.sp, fontWeight = FontWeight.Medium)
private val roleCaption = TextStyle(fontFamily = DelphiFont, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)

/** NDEF spec names, untranslated. */
private fun roleName(role: NdefRole, last: Boolean) = when (role) {
    NdefRole.Frame -> if (last) "END" else "TLV"
    NdefRole.Header -> "HDR"
    NdefRole.Length -> "LEN"
    NdefRole.Type -> "TYPE"
    NdefRole.Identifier -> "ID"
    NdefRole.Payload -> ""
}

/** dt-ios NDEFByteMap: the message's bytes as captioned cells, coloured by role, in wrapping rows. */
@Composable
private fun NdefByteMap(segments: List<NdefSegment>, fromStart: Boolean) {
    val c = LocalDelphiColours.current
    val measurer = rememberTextMeasurer()
    val flow = rememberCanonGlyphFlowState<NdefGlyph>(
        CanonGlyphMetrics(height = cellSide + 3.dp + 12.dp, rowGap = 8.dp, gap = 2.dp),
        measure = { if (it.role == null) 6.dp else cellSide },
        kind = { if (it.role == null) CanonGlyphKind.Space else CanonGlyphKind.Glyph },
    )
    val glyphs = remember(segments) {
        buildList {
            segments.forEachIndexed { index, segment ->
                if (index > 0) add(NdefGlyph(0, null))
                segment.bytes.forEachIndexed { position, byte ->
                    val code = byte.toInt() and 0xff
                    val caption = when {
                        segment.role == NdefRole.Payload -> if (code in 0x20..0x7e) code.toChar().toString() else "·"
                        position == 0 -> roleName(segment.role, index == segments.size - 1)
                        else -> ""
                    }
                    add(NdefGlyph(byte, segment.role, caption))
                }
            }
        }
    }
    LaunchedEffect(glyphs, fromStart) {
        flow.show(glyphs)
        flow.focus = if (fromStart) 0 else null
    }
    CanonGlyphFlow(
        flow,
        Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Message"
                stateDescription = NfcReaderWriterEngine.hex(segments.flatMap { it.bytes })
            },
    ) { glyph, _, frame -> drawByte(glyph, frame, c, measurer) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawByte(glyph: NdefGlyph, frame: Rect, c: DelphiColours, measurer: TextMeasurer) {
    val role = glyph.role ?: return
    val side = cellSide.toPx()
    val box = Rect(frame.topLeft, Size(side, side))
    val inset = Rect(box.left + 0.5f, box.top + 0.5f, box.right - 0.5f, box.bottom - 0.5f)
    val ink = when (role) {
        NdefRole.Header -> {
            drawRect(c.primary, box.topLeft, box.size)
            c.primaryForeground
        }
        NdefRole.Length -> {
            drawRect(c.muted, box.topLeft, box.size)
            c.mutedForeground
        }
        NdefRole.Type, NdefRole.Identifier -> {
            drawRect(c.accentBg, box.topLeft, box.size)
            c.foreground
        }
        NdefRole.Payload -> {
            drawRect(c.border, inset.topLeft, inset.size, style = Stroke(1f))
            c.foreground
        }
        NdefRole.Frame -> {
            drawRect(c.mutedForeground, inset.topLeft, inset.size, style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 2f))))
            c.mutedForeground
        }
    }
    val hex = measurer.measure("%02X".format(glyph.value.toInt() and 0xff), hexStyle.copy(color = ink))
    drawText(hex, topLeft = Offset(box.center.x - hex.size.width / 2f, box.center.y - hex.size.height / 2f))
    if (glyph.caption.isEmpty()) return
    val captionStyle = (if (role == NdefRole.Payload) payloadCaption else roleCaption).copy(color = if (role == NdefRole.Header) c.primary else c.mutedForeground)
    val caption = measurer.measure(glyph.caption, captionStyle)
    val x = if (role == NdefRole.Payload) box.center.x - caption.size.width / 2f else box.left
    drawText(caption, topLeft = Offset(x, box.bottom + 3.dp.toPx()))
}
