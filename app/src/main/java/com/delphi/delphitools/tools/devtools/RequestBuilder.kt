package com.delphi.delphitools.tools.devtools

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCodeBlock
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSharedText
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTextArea
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonValueLayout
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MAX_LINES = 12
private val pairFont = TextStyle(fontFamily = DelphiMono, fontSize = 14.sp)
private val urlFont = TextStyle(fontFamily = DelphiMono, fontSize = 15.sp)
private val wireMono = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp)
private val wireMarker = TextStyle(fontFamily = DelphiMono, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
private val wireSmall = TextStyle(fontFamily = DelphiFont, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
private val barHeight = 10.dp

internal class PairRow(val id: Int, key: String, value: String) {
    var key by mutableStateOf(key)
    var value by mutableStateOf(value)
}

/** dt-ios RequestBuilderViewController's state: the request as typed, and the spec the outputs show. */
class RequestBuilderModel : ViewModel() {
    var method by mutableIntStateOf(0)
        private set
    var url by mutableStateOf("")
        private set
    internal val query = mutableStateListOf<PairRow>()
    internal val headers = mutableStateListOf<PairRow>()
    var body by mutableStateOf("")
        private set
    var spec by mutableStateOf(RequestSpec())
        private set
    private var nextId = 0
    private var typing: Job? = null

    fun chooseMethod(index: Int) {
        method = index
        refresh()
    }

    fun typeUrl(text: String) {
        url = text
        typed()
    }

    fun takeUrl(text: String) {
        url = text
        refresh()
    }

    fun typeBody(text: String) {
        body = text
        typed()
    }

    internal fun add(list: MutableList<PairRow>) {
        list += PairRow(nextId++, "", "")
        typed()
    }

    internal fun remove(list: MutableList<PairRow>, row: PairRow) {
        list.remove(row)
        typed()
    }

    fun typed() {
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            refresh()
        }
    }

    private fun refresh() {
        typing?.cancel()
        spec = RequestSpec(
            RequestBuilderEngine.methods[method],
            url,
            query.map { RequestPair(it.key, it.value) },
            headers.map { RequestPair(it.key, it.value) },
            body,
        )
    }
}

private val noAutocorrect = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done)

// ponytail: a body pasted as one very long line can pass the layout size limit in the code blocks; iOS has no cap either
@Composable
fun RequestBuilder(model: RequestBuilderModel = viewModel()) {
    CanonSharedText(model::takeUrl)
    val spec = model.spec
    val wire = remember(spec) { RequestBuilderEngine.toHttp(spec) }
    val curl = remember(spec) { RequestBuilderEngine.toCurl(spec) }
    val full = remember(spec) { RequestBuilderEngine.fullUrl(spec) }

    val request = CanonSection.of {
        CanonRows {
            CanonHeading("Method")
            CanonSegmented(RequestBuilderEngine.methods, model.method, Modifier.semantics { contentDescription = "Method" }, columns = 4, onSelect = model::chooseMethod)
            CanonHeading("URL")
            CanonTextCell(
                model.url,
                model::typeUrl,
                Modifier.fillMaxWidth().semantics { contentDescription = "URL" },
                placeholder = "https://api.example.com/path",
                width = 240.dp,
                textStyle = urlFont,
                keyboardOptions = noAutocorrect.copy(keyboardType = KeyboardType.Uri),
            )
        }
    }
    val pairs = CanonSection.of {
        CanonRows {
            CanonHeading("Query")
            PairList(model.query, "Name", model)
            CanonHeading("Headers")
            PairList(model.headers, "Header", model)
        }
    }
    val payload = CanonSection.of {
        CanonRows {
            CanonHeading("Body")
            CanonTextArea(model.body, model::typeBody, placeholder = "{ }", height = 140.dp, label = "Body")
        }
    }
    val target = CanonSection.of {
        CanonRows {
            CanonHeading("URL")
            CanonValueLine(spec.method, full.ifEmpty { null }, layout = CanonValueLayout.Wrapping)
        }
    }
    val curlBlock = CanonSection.of {
        CanonRows {
            CanonHeading("cURL")
            CanonCodeBlock(curl)
        }
    }
    val httpBlock = CanonSection.of {
        CanonRows {
            CanonHeading("HTTP")
            CanonCodeBlock(wire)
        }
    }
    val banner = CanonSection.canvas(CanonCanvasSize.Fitting) { RequestWire(wire) }

    CanonSplit(sidebar = listOf(request, pairs, payload), main = listOf(target, curlBlock, httpBlock), banner = banner)
}

@Composable
private fun PairList(rows: MutableList<PairRow>, keyPlaceholder: String, model: RequestBuilderModel) {
    val c = LocalDelphiColours.current
    Column(Modifier.fillMaxWidth().background(c.border), verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
        rows.forEach { row ->
            androidx.compose.runtime.key(row.id) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
                    CanonTextCell(
                        row.key,
                        {
                            row.key = it
                            model.typed()
                        },
                        Modifier.weight(1f).semantics { contentDescription = keyPlaceholder },
                        placeholder = keyPlaceholder,
                        textStyle = pairFont,
                        keyboardOptions = noAutocorrect.copy(imeAction = ImeAction.Next),
                    )
                    CanonTextCell(
                        row.value,
                        {
                            row.value = it
                            model.typed()
                        },
                        Modifier.weight(1f).semantics { contentDescription = "Value" },
                        placeholder = "Value",
                        textStyle = pairFont,
                        keyboardOptions = noAutocorrect,
                    )
                    CanonButton(
                        "",
                        Modifier.width(CanonMetrics.controlHeight).fillMaxHeight(),
                        icon = pencil(R.drawable.pencil_square_minus),
                        contentDescription = "Remove",
                    ) { model.remove(rows, row) }
                }
            }
        }
        CanonButton("Add", Modifier.fillMaxWidth().height(CanonMetrics.controlHeight), icon = pencil(R.drawable.pencil_plus)) { model.add(rows) }
    }
}

private fun partName(part: RequestPart) = when (part) {
    RequestPart.Start -> "Request line"
    RequestPart.Headers -> "Headers"
    RequestPart.Body -> "Body"
}

// dt-ios RequestWire
@Composable
private fun RequestWire(message: String) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val pad = CanonMetrics.panelPadding
    val (lines, bytes) = remember(message) { RequestBuilderEngine.wireLines(message) }
    val total = bytes.sum()
    val sized = Formatter.formatFileSize(context, total.toLong())
    val number = sized.takeWhile { it.isDigit() || it == ',' || it == '.' }
    val unit = sized.drop(number.length).trim()
    val hostLine = lines.firstOrNull { it.part == RequestPart.Headers && it.text.lowercase().startsWith("host:") }
    val drawn = minOf(lines.size, MAX_LINES + if (lines.size > MAX_LINES + 1) 0 else 1)
    val inks = mapOf(RequestPart.Start to c.primary, RequestPart.Headers to c.foreground, RequestPart.Body to c.mutedForeground)

    val small = remember(density) { measurer.measure("Ag", wireSmall).size.height }
    val pitch = remember(density) { measurer.measure("Ag", wireMono).size.height + with(density) { 3.dp.roundToPx() } }
    val listRows = drawn + if (lines.size > drawn) 1 else 0
    val pictureHeight = with(density) { (barHeight.toPx() + 6.dp.toPx() + small * 2 + 14.dp.toPx() + listRows * pitch).toDp() }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = LocalCanonCanvas.current.top + pad * 0.75f, bottom = pad)
            .semantics(mergeDescendants = true) {
                contentDescription = lines.firstOrNull()?.text ?: ""
                stateDescription = sized
            },
    ) {
        Row(Modifier.padding(horizontal = pad), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CanonFigure(number, unit = unit, size = 34.sp, halo = c.card)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("HTTP/1.1", style = CanonText.captionSemibold, color = c.mutedForeground)
                Text(
                    hostLine?.text?.drop(5)?.trim() ?: "—",
                    style = CanonText.subheadline,
                    color = if (hostLine == null) c.mutedForeground else c.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
        Canvas(Modifier.padding(top = 14.dp).padding(horizontal = pad).fillMaxWidth().height(pictureHeight)) {
            val gap = 8.dp.toPx()
            val room = size.width
            val usable = room - gap * 2
            val sum = maxOf(1, total).toFloat()
            val least = minOf(84.dp.toPx(), usable / 3)
            val widths = RequestPart.entries.map { maxOf(least, usable * bytes[it.ordinal] / sum) }.toMutableList()
            val over = widths.sum() - usable
            if (over > 0) widths.indices.maxBy { widths[it] }.let { widths[it] -= over }
            var x = 0f
            val bar = barHeight.toPx()
            val hairline = CanonMetrics.hairline.toPx()
            for (part in RequestPart.entries) {
                val width = widths[part.ordinal]
                val count = bytes[part.ordinal]
                if (count == 0) {
                    drawRect(
                        c.mutedForeground,
                        Offset(x + hairline / 2, hairline / 2),
                        Size(width - hairline, bar - hairline),
                        style = Stroke(hairline, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
                    )
                } else {
                    drawRect(inks.getValue(part), Offset(x, 0f), Size(width, bar))
                }
                val labelWidth = Constraints(maxWidth = maxOf(1, width.toInt()))
                val name = measurer.measure(partName(part), wireSmall.copy(color = if (count == 0) c.mutedForeground else c.foreground), maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = labelWidth)
                drawText(name, topLeft = Offset(x, bar + 6.dp.toPx()))
                val counted = measurer.measure("$count bytes", wireSmall.copy(color = c.mutedForeground), maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = labelWidth)
                drawText(counted, topLeft = Offset(x, bar + 6.dp.toPx() + small))
                x += width + gap
            }

            var y = bar + 6.dp.toPx() + small * 2 + 14.dp.toPx()
            val offsetWidth = measurer.measure("0000", wireMono).size.width
            val textLeft = 6.dp.toPx() + offsetWidth + 10.dp.toPx()
            val textWidth = room - textLeft
            for (line in lines.take(drawn)) {
                drawRect(inks.getValue(line.part), Offset(0f, y + 2.dp.toPx()), Size(2.dp.toPx(), pitch - 4.dp.toPx()))
                drawText(measurer.measure("%04x".format(line.offset), wireMono.copy(color = c.mutedForeground)), topLeft = Offset(6.dp.toPx(), y + 1.dp.toPx()))
                val ending = when (line.ending) {
                    "\r\n" -> " ␍␊"
                    "" -> ""
                    else -> " ␊"
                }
                val marker = measurer.measure(ending, wireMarker.copy(color = c.mutedForeground))
                val styled = styledLine(line, c.primary, c.foreground, c.mutedForeground)
                val natural = measurer.measure(styled, wireMono, softWrap = false, maxLines = 1)
                val fits = minOf(natural.size.width.toFloat(), maxOf(0f, textWidth - marker.size.width))
                val text = measurer.measure(styled, wireMono, softWrap = false, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = (fits + 1).toInt().coerceAtLeast(1)))
                drawText(text, topLeft = Offset(textLeft, y + 1.dp.toPx()))
                drawText(marker, topLeft = Offset(textLeft + fits, y + 1.dp.toPx() + text.firstBaseline - marker.firstBaseline))
                y += pitch
            }
            if (lines.size > drawn) {
                drawText(measurer.measure("⋯ +${lines.size - drawn}", wireMono.copy(color = c.mutedForeground)), topLeft = Offset(textLeft, y + 1.dp.toPx()))
            }
        }
    }
}

private fun styledLine(line: RequestLine, primary: Color, foreground: Color, muted: Color) = buildAnnotatedString {
    val bold = SpanStyle(fontWeight = FontWeight.SemiBold)
    when (line.part) {
        RequestPart.Start -> {
            val space = line.text.indexOf(' ')
            val first = if (space < 0) line.text else line.text.substring(0, space)
            withStyle(bold.copy(color = primary)) { append(first) }
            if (space >= 0) {
                val rest = line.text.substring(space + 1)
                val last = rest.lastIndexOf(' ')
                if (last >= 0) {
                    withStyle(SpanStyle(color = foreground)) { append(" " + rest.substring(0, last)) }
                    withStyle(SpanStyle(color = muted)) { append(rest.substring(last)) }
                } else {
                    withStyle(SpanStyle(color = foreground)) { append(" $rest") }
                }
            }
        }
        RequestPart.Headers -> {
            val colon = line.text.indexOf(':')
            if (colon >= 0) {
                withStyle(bold.copy(color = foreground)) { append(line.text.substring(0, colon)) }
                withStyle(SpanStyle(color = foreground)) { append(line.text.substring(colon)) }
            } else {
                withStyle(SpanStyle(color = foreground)) { append(line.text) }
            }
        }
        RequestPart.Body -> withStyle(SpanStyle(color = foreground)) { append(line.text) }
    }
}
