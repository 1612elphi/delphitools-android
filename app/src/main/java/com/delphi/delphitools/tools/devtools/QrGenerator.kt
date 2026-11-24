package com.delphi.delphitools.tools.devtools

import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourWell
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.toCanonColor
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.contrastFigure
import com.delphi.delphitools.colour.contrastRatio
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import com.delphi.delphitools.tools.images.ImageFiles
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DASH = "—"
private const val LOGO_MAX = 512
private val fieldMono = TextStyle(fontFamily = DelphiMono, fontSize = 16.sp)
private val swatchWidth = 76.dp

private fun placeholder(type: QrContentType) = when (type) {
    QrContentType.Text -> "Enter text"
    QrContentType.Url -> "https://example.com"
    QrContentType.Email -> "hello@example.com"
    QrContentType.Phone, QrContentType.Sms -> "+1234567890"
    QrContentType.Wifi -> "Network name"
    QrContentType.Event -> "Event title"
}

private fun keyboard(type: QrContentType) = when (type) {
    QrContentType.Text, QrContentType.Wifi, QrContentType.Event -> KeyboardType.Text
    QrContentType.Url -> KeyboardType.Uri
    QrContentType.Email -> KeyboardType.Email
    QrContentType.Phone, QrContentType.Sms -> KeyboardType.Phone
}

// ponytail: no undo stack; the Android shell has no undo bar to drive one
/** dt-ios QRGeneratorViewModel and its controller's state. */
class QrGeneratorModel(application: Application) : AndroidViewModel(application) {
    var type by mutableStateOf(QrContentType.Text)
        private set

    /** The text fields as shown; the code follows them after a typing pause. */
    var contentField by mutableStateOf("")
        private set
    var passwordField by mutableStateOf("")
        private set
    var messageField by mutableStateOf("")
        private set
    var locationField by mutableStateOf("")
        private set
    var notesField by mutableStateOf("")
        private set

    var encryption by mutableStateOf("WPA")
        private set
    var hidden by mutableStateOf(false)
        private set
    var event by mutableStateOf(QrEvent.startingAfter(Instant.now()))
        private set
    var correction by mutableStateOf(QrCorrection.Quartile)
        private set
    var foreground by mutableStateOf(Rgb.black)
        private set
    var background by mutableStateOf(Rgb.white)
        private set
    var size by mutableIntStateOf(600)
        private set
    var dot by mutableStateOf(QrDotStyle.Square)
        private set
    var eye by mutableStateOf(QrEyeStyle.Square)
        private set
    var pupil by mutableStateOf(QrPupilStyle.Square)
        private set
    var logo by mutableStateOf<QrLogo?>(null)
        private set
    var logoModules by mutableIntStateOf(9)
        private set
    var showInfo by mutableStateOf(false)
        private set
    var svg by mutableStateOf(false)

    var payload by mutableStateOf("")
        private set
    var matrix by mutableStateOf<List<BooleanArray>?>(null)
        private set
    var sheet by mutableStateOf<CodeSheet?>(null)
        private set
    var pixelsPerModule by mutableStateOf(1f)
        private set

    private var content = ""
    private var password = ""
    private var message = ""
    private var typing: Job? = null
    private var cached: Pair<Pair<String, QrCorrection>, List<BooleanArray>?>? = null
    private val measurer = TextMeasurer(createFontFamilyResolver(application), Density(1f), LayoutDirection.Ltr)

    /** A logo needs the highest level to survive the modules it covers. */
    val effectiveCorrection get() = if (logo != null) QrCorrection.High else correction

    val tooLong get() = payload.isNotEmpty() && matrix == null
    val backwards get() = type == QrContentType.Event && event.endsBeforeStart()

    init {
        refresh()
    }

    fun type(field: Int, text: String) {
        when (field) {
            0 -> contentField = text
            1 -> passwordField = text
            2 -> messageField = text
            3 -> locationField = text
            else -> notesField = text
        }
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            content = contentField
            password = passwordField
            message = messageField
            event = event.copy(location = locationField, notes = notesField)
            refresh()
        }
    }

    fun chooseType(value: QrContentType) = change { type = value }
    fun chooseEncryption(value: String) = change { encryption = value }
    fun changeHidden(value: Boolean) = change { hidden = value }
    fun changeAllDay(value: Boolean) = change { event = event.copy(allDay = value) }
    fun changeStart(value: Instant) = change { event = event.copy(start = value) }
    fun changeEnd(value: Instant) = change { event = event.copy(end = value) }
    fun changeForeground(value: Rgb) = change { foreground = value }
    fun changeBackground(value: Rgb) = change { background = value }
    fun chooseCorrection(value: QrCorrection) = change { correction = value }
    fun chooseDot(value: QrDotStyle) = change { dot = value }
    fun chooseEye(value: QrEyeStyle) = change { eye = value }
    fun choosePupil(value: QrPupilStyle) = change { pupil = value }
    fun changeInfo(value: Boolean) = change { showInfo = value }
    fun changeLogoModules(value: Int) = change { logoModules = value }
    fun removeLogo() = change { logo = null }

    fun applyPreset(preset: QrStylePreset) = change {
        dot = preset.dot
        eye = preset.eye
        pupil = preset.pupil
        foreground = preset.foreground
        background = preset.background
    }

    /** A typed size waits for the typing pause; a step applies at once. */
    fun changeSize(value: Int, typed: Boolean) {
        size = value
        typing?.cancel()
        if (!typed) return refresh()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            refresh()
        }
    }

    fun setLogo(image: ImageBitmap) {
        viewModelScope.launch {
            val made = withContext(Dispatchers.Default) {
                val source = image.asAndroidBitmap()
                val scale = min(1f, LOGO_MAX.toFloat() / max(source.width, source.height))
                val fitted = if (scale < 1f) {
                    Bitmap.createScaledBitmap(source, max(1, (source.width * scale).roundToInt()), max(1, (source.height * scale).roundToInt()), true)
                } else {
                    source
                }
                ImageFiles.png(fitted)?.let { QrLogo(fitted.asImageBitmap(), it, fitted.width, fitted.height) }
            } ?: return@launch
            change { logo = made }
        }
    }

    private fun change(block: () -> Unit) {
        block()
        refresh()
    }

    private fun refresh() {
        payload = QrEngine.payload(type, content, password, encryption, hidden, message, event)
        val key = payload to effectiveCorrection
        val found = cached?.takeIf { it.first == key }?.second ?: QrEngine.matrix(payload, effectiveCorrection)
        cached = key to found
        matrix = found
        if (found == null) {
            sheet = null
            return
        }
        logo?.let { logoModules = logoModules.coerceIn(5, max(5, QrEngine.largestLogo(found.size))) }
        val info = if (showInfo) QrEngine.infoLines(type, content, password, encryption, hidden, event) else emptyList()
        val look = QrEngine.Look(dot, eye, pupil, foreground, background, size, logo, logoModules, info)
        val (made, pixels) = QrEngine.sheet(found, look) { text, px ->
            measurer.measure(text, TextStyle(fontSize = px.sp, fontWeight = FontWeight.Medium)).size.width.toFloat()
        }
        sheet = made
        pixelsPerModule = pixels
    }

    fun copy(context: Context) {
        val written = sheet ?: return
        val pixels = pixelsPerModule
        viewModelScope.launch {
            if (svg) {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("SVG", written.svg(pixels)))
            } else {
                val data = withContext(Dispatchers.Default) { written.png(context, pixels) } ?: return@launch
                ImageFiles.copyPng(context, data, "qr-code.png")
            }
        }
    }

    fun export(context: Context) {
        val written = sheet ?: return
        val pixels = pixelsPerModule
        viewModelScope.launch {
            if (svg) {
                FileExport.share(context, written.svg(pixels).toByteArray(), "qr-code.svg", "image/svg+xml")
            } else {
                val data = withContext(Dispatchers.Default) { written.png(context, pixels) } ?: return@launch
                FileExport.share(context, data, "qr-code.png", "image/png")
            }
        }
    }
}

@Composable
fun QrGenerator(model: QrGeneratorModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val types = QrContentType.entries
    val type = model.type
    val sheet = model.sheet
    val keyboardOptions = { kind: KeyboardType, words: Boolean ->
        KeyboardOptions(
            capitalization = if (words) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = kind,
            imeAction = ImeAction.Done,
        )
    }

    val contentBlock = CanonSection.of {
        CanonRows {
            CanonHeading("Content")
            CanonSegmented(types.map { it.title }, types.indexOf(type), Modifier.semantics { contentDescription = "Type" }, columns = 4) { model.chooseType(types[it]) }
            CanonTextCell(
                model.contentField,
                { model.type(0, it) },
                Modifier.fillMaxWidth().semantics { contentDescription = type.title },
                placeholder = placeholder(type),
                textStyle = fieldMono,
                keyboardOptions = keyboardOptions(keyboard(type), false),
            )
            if (type == QrContentType.Wifi) {
                CanonRow("Password", flush = true) {
                    CanonTextCell(model.passwordField, { model.type(1, it) }, Modifier.fillMaxWidth(), textStyle = fieldMono, keyboardOptions = keyboardOptions(KeyboardType.Password, false))
                }
                CanonHeading("Encryption")
                CanonSegmented(
                    QrEngine.wifiEncryptions,
                    QrEngine.wifiEncryptions.indexOf(model.encryption),
                    Modifier.semantics { contentDescription = "Encryption" },
                ) { model.chooseEncryption(QrEngine.wifiEncryptions[it]) }
                CanonRow("Hidden network") { CanonSwitch(model.hidden, onCheckedChange = model::changeHidden) }
            }
            if (type == QrContentType.Sms) {
                CanonRow("Message", flush = true) {
                    CanonTextCell(
                        model.messageField,
                        { model.type(2, it) },
                        Modifier.fillMaxWidth(),
                        placeholder = "Message body (optional)",
                        textStyle = fieldMono,
                        keyboardOptions = keyboardOptions(KeyboardType.Text, false),
                    )
                }
            }
            if (type == QrContentType.Event) {
                val event = model.event
                CanonRow("Location", flush = true) {
                    CanonTextCell(model.locationField, { model.type(3, it) }, Modifier.fillMaxWidth(), textStyle = fieldMono, keyboardOptions = keyboardOptions(KeyboardType.Text, true))
                }
                CanonRow("All day") { CanonSwitch(event.allDay, onCheckedChange = model::changeAllDay) }
                CanonRow("Starts", flush = true) { DateCell(event.start, event.allDay, "Starts", model::changeStart) }
                CanonRow("Ends", flush = true) { DateCell(event.end, event.allDay, "Ends", model::changeEnd) }
                CanonRow("Notes", flush = true) {
                    CanonTextCell(model.notesField, { model.type(4, it) }, Modifier.fillMaxWidth(), textStyle = fieldMono, keyboardOptions = keyboardOptions(KeyboardType.Text, true))
                }
            }
            if (model.backwards) CanonStatusRow("Causality violation: End date before start date.", CanonTone.Warn)
            if (model.tooLong) CanonStatusRow("Too long for a QR code. Shorten your text!", CanonTone.Warn)
        }
    }

    val styleBlock = CanonSection.of {
        CanonRows {
            val ink = model.foreground
            val paper = model.background
            CanonHeading("Presets")
            Swatches(
                QrStylePreset.all.map { preset -> Swatch(preset.name, preset.background) { drawSample(it, preset.dot, preset.eye, preset.pupil, preset.foreground) } },
                QrStylePreset.all.indexOfFirst { p ->
                    p.dot == model.dot && p.eye == model.eye && p.pupil == model.pupil &&
                        CodeSheet.hex(p.foreground) == CodeSheet.hex(ink) && CodeSheet.hex(p.background) == CodeSheet.hex(paper)
                }.takeIf { it >= 0 },
            ) { model.applyPreset(QrStylePreset.all[it]) }
            CanonHeading("Dots")
            Swatches(QrDotStyle.entries.map { style -> Swatch(style.title, paper) { drawDots(it, style, ink) } }, model.dot.ordinal) {
                model.chooseDot(QrDotStyle.entries[it])
            }
            CanonHeading("Eyes")
            Swatches(QrEyeStyle.entries.map { style -> Swatch(style.title, paper) { drawEye(it, style, model.pupil, ink) } }, model.eye.ordinal) {
                model.chooseEye(QrEyeStyle.entries[it])
            }
            CanonHeading("Pupils")
            Swatches(QrPupilStyle.entries.map { style -> Swatch(style.title, paper) { drawEye(it, model.eye, style, ink) } }, model.pupil.ordinal) {
                model.choosePupil(QrPupilStyle.entries[it])
            }
        }
    }

    val logoBlock = CanonSection.of {
        CanonRows {
            val logo = model.logo
            CanonHeading("Logo")
            if (logo == null) {
                CanonDropZone("Add a logo to the centre", icon = Icons.Outlined.Verified, onImages = { images, _ -> images.firstOrNull()?.let(model::setLogo) })
            } else {
                CanonRow("Logo", detail = "${logo.width} × ${logo.height} px", flush = true) {
                    CanonButton("", icon = Icons.Outlined.Delete, contentDescription = "Remove", onClick = model::removeLogo)
                }
                val largest = max(5, QrEngine.largestLogo(model.matrix?.size ?: 21))
                CanonRow("Logo size", flush = true) {
                    CanonNumberField(model.logoModules, model::changeLogoModules, range = 5..largest)
                }
            }
        }
    }

    val outputBlock = CanonSection.of {
        CanonRows {
            var sizeTyped by remember { mutableStateOf(false) }
            val corrections = QrCorrection.entries
            val level = model.effectiveCorrection
            val n = model.matrix?.size ?: 0
            CanonHeading("Colours")
            CanonColourWell(model.foreground, model::changeForeground, label = "Foreground")
            CanonColourWell(model.background, model::changeBackground, label = "Background")
            CanonHeading("Error Correction")
            CanonSegmented(
                corrections.map { it.label },
                corrections.indexOf(level),
                Modifier.semantics { contentDescription = "Error Correction" },
                columns = 2,
                // logo forces level h
                disabledIndices = if (model.logo != null) setOf(0, 1, 2) else emptySet(),
            ) { model.chooseCorrection(corrections[it]) }
            CanonRow("Size", flush = true) {
                CanonNumberField(model.size, { model.changeSize(it, sizeTyped) }, Modifier.onFocusChanged { sizeTyped = it.hasFocus }, range = 200..1200, step = 50, unit = "px")
            }
            CanonRow("Add information") { CanonSwitch(model.showInfo, onCheckedChange = model::changeInfo) }
            CanonHeading("Code")
            CanonTiles(
                listOf(
                    CanonTile("Version", if (n > 0) "${(n - 17) / 4}" else DASH),
                    CanonTile("Modules", if (n > 0) "$n × $n" else DASH),
                    CanonTile("Error Correction", level.code + " · " + level.label.filter { it.isDigit() || it == '%' }),
                    CanonTile("Payload", "${model.payload.toByteArray().size} B"),
                    CanonTile("Contrast", contrastFigure(contrastRatio(model.foreground, model.background)) + ":1"),
                ),
            )
            CanonHeading("Format")
            CanonSegmented(listOf("PNG", "SVG"), if (model.svg) 1 else 0, Modifier.semantics { contentDescription = "Format" }) { model.svg = it == 1 }
            CanonGrid {
                CanonButton("Copy", Modifier.weight(1f).fillMaxHeight(), enabled = sheet != null, icon = Icons.Outlined.ContentCopy, flashesCopied = true) {
                    model.copy(context)
                }
                CanonButton("Export", Modifier.weight(1f).fillMaxHeight(), kind = CanonButtonKind.Primary, enabled = sheet != null, icon = Icons.Outlined.Share, oversized = true) {
                    model.export(context)
                }
            }
        }
    }

    // stacked: not full screen
    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(1f), background = c.card, maxHeight = 520.dp) {
        CodeSheetPicture(sheet, "QR code", sheet?.pixelSize(model.pixelsPerModule), Icons.Outlined.QrCode, model.payload.ifEmpty { null })
    }

    CanonSplit(sidebar = listOf(canvas), main = listOf(contentBlock, styleBlock, logoBlock, outputBlock), halves = true, collapsesTo = 240.dp)
}

/** A date, or a date and time, that opens the system pickers. */
@Composable
private fun DateCell(value: Instant, dateOnly: Boolean, label: String, onChange: (Instant) -> Unit) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val shown = QrEvent(allDay = dateOnly, start = value, end = value).shown(value)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CanonMetrics.controlHeight)
            .background(c.card)
            .canonClickable {
                val at = LocalDateTime.ofInstant(value, zone)
                DatePickerDialog(context, { _, year, month, day ->
                    val date = at.withYear(year).withMonth(month + 1).withDayOfMonth(day)
                    if (dateOnly) {
                        onChange(date.atZone(zone).toInstant())
                    } else {
                        TimePickerDialog(context, { _, hour, minute ->
                            onChange(date.withHour(hour).withMinute(minute).withSecond(0).withNano(0).atZone(zone).toInstant())
                        }, at.hour, at.minute, DateFormat.is24HourFormat(context)).show()
                    }
                }, at.year, at.monthValue - 1, at.dayOfMonth).show()
            }
            .semantics { contentDescription = label }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(shown, style = TextStyle(fontFamily = DelphiMono, fontSize = 15.sp), color = c.foreground)
    }
}

private class Swatch(val title: String, val paper: Rgb, val draw: DrawScope.(SheetRect) -> Unit)

/** dt-ios QRStyleSwatches: looks as small drawings on their own paper over their names, three to six to a row. */
@Composable
private fun Swatches(items: List<Swatch>, selected: Int?, onPick: (Int) -> Unit) {
    val c = LocalDelphiColours.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = min(6, max(3, (maxWidth / swatchWidth).toInt()))
        CanonGrid(columns) {
            items.forEachIndexed { index, item ->
                val lit = index == selected
                Column(
                    Modifier
                        .background(if (lit) c.accentBg else c.card)
                        .canonClickable { if (!lit) onPick(index) }
                        .clearAndSetSemantics {
                            contentDescription = item.title
                            role = Role.Button
                            this.selected = lit
                        }
                        .padding(start = 4.dp, top = 10.dp, end = 4.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val border = if (lit) c.primary else c.border
                    Canvas(Modifier.size(40.dp)) {
                        drawRect(item.paper.toCanonColor())
                        val inset = 6.dp.toPx()
                        item.draw(this, SheetRect(inset, inset, size.width - inset * 2, size.height - inset * 2))
                        val width = (if (lit) CanonMetrics.frame else CanonMetrics.hairline).toPx()
                        drawRect(border, Offset(width / 2, width / 2), Size(size.width - width, size.height - width), style = Stroke(width))
                    }
                    Text(
                        item.title,
                        style = if (lit) CanonText.captionSemibold else CanonText.caption,
                        color = if (lit) c.primary else c.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private val patch = listOf(booleanArrayOf(true, true, false), booleanArrayOf(true, false, true), booleanArrayOf(false, true, true))

private fun DrawScope.drawDots(rect: SheetRect, style: QrDotStyle, ink: Rgb) {
    val module = rect.width / 3
    fun dark(r: Int, c: Int) = r in patch.indices && c in 0..2 && patch[r][c]
    val path = Path()
    for (r in 0 until 3) for (c in 0 until 3) {
        if (!patch[r][c]) continue
        val cell = SheetRect(rect.x + c * module, rect.y + r * module, module, module)
        QrEngine.dotPath(cell, style, dark(r - 1, c), dark(r + 1, c), dark(r, c - 1), dark(r, c + 1)).toPath(path)
    }
    drawPath(path, ink.toCanonColor())
}

private fun DrawScope.drawEye(rect: SheetRect, eye: QrEyeStyle, pupil: QrPupilStyle, ink: Rgb) {
    val (ring, centre) = QrEngine.eyePaths(rect, eye, pupil, rect.width / 7)
    val colour = ink.toCanonColor()
    drawPath(ring.toPath().apply { fillType = PathFillType.EvenOdd }, colour)
    drawPath(centre.toPath(), colour)
}

private fun DrawScope.drawSample(rect: SheetRect, dot: QrDotStyle, eye: QrEyeStyle, pupil: QrPupilStyle, ink: Rgb) {
    val module = rect.width / 10
    drawEye(SheetRect(rect.x, rect.y, module * 7, module * 7), eye, pupil, ink)
    val cells = listOf(0 to 8, 1 to 8, 1 to 9, 3 to 9, 8 to 0, 8 to 1, 9 to 1, 9 to 3, 8 to 5, 9 to 5, 9 to 6, 7 to 8, 8 to 8, 8 to 9)
    val set = cells.toSet()
    val path = Path()
    for ((r, c) in cells) {
        val cell = SheetRect(rect.x + c * module, rect.y + r * module, module, module)
        QrEngine.dotPath(cell, dot, (r - 1 to c) in set, (r + 1 to c) in set, (r to c - 1) in set, (r to c + 1) in set).toPath(path)
    }
    drawPath(path, ink.toCanonColor())
}
