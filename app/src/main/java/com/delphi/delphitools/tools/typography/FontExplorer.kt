package com.delphi.delphitools.tools.typography

import android.app.Application
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCodeBlock
import com.delphi.delphitools.canon.CanonCopyFeedback
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val DEFAULT_PREVIEW = "The quick brown fox jumps over the lazy dog"
private val waterfallSizes = listOf(12, 14, 16, 18, 24, 32, 48, 64)
private val fontTypes = arrayOf(
    "font/*", "application/font-sfnt", "application/x-font-ttf", "application/x-font-otf",
    "application/vnd.ms-opentype", "application/font-woff", "application/octet-stream",
)
private val tagMono = TextStyle(fontFamily = DelphiMono, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
private val sizeMono = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
private val labelName = TextStyle(fontFamily = DelphiFont, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
private val captionStyle = CanonText.subheadline.copy(fontWeight = FontWeight.SemiBold)
private val detailStyle = TextStyle(fontFamily = DelphiFont, fontSize = 12.sp)
private val footnote = TextStyle(fontFamily = DelphiFont, fontSize = 13.sp)

/** Em fractions, descent positive. */
internal data class EmMetrics(val ascent: Float, val capHeight: Float, val xHeight: Float, val descent: Float)

internal class LoadedFont(val info: FontInfo, val typeface: Typeface, val capHeight: Int, val xHeight: Int, blocks: List<UnicodeBlock>) {
    val specimen = FontEngine.specimen(info.coverage::covers)
    val chartRange = FontEngine.chartRange(specimen, blocks)
    val em = info.unitsPerEm.coerceAtLeast(1).toFloat()
    val metrics = EmMetrics(info.ascent / em, (info.capHeight ?: capHeight) / em, (info.xHeight ?: xHeight) / em, info.descent / em)
}

/** Ink height above the baseline of [text] in [typeface] at [size] px. */
private fun inkHeight(typeface: Typeface, text: String, size: Float): Int {
    val paint = Paint().apply {
        this.typeface = typeface
        textSize = size
    }
    return -android.graphics.Rect().also { paint.getTextBounds(text, 0, text.length, it) }.top
}

private val standIn: EmMetrics by lazy {
    val paint = Paint().apply { textSize = 100f }
    val metrics = paint.fontMetrics
    EmMetrics(-metrics.ascent / 100, inkHeight(Typeface.DEFAULT, "H", 100f) / 100f, inkHeight(Typeface.DEFAULT, "x", 100f) / 100f, metrics.descent / 100)
}

class FontExplorerModel(application: Application) : AndroidViewModel(application) {
    internal var font by mutableStateOf<LoadedFont?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var preview by mutableStateOf(DEFAULT_PREVIEW)
        private set
    var shownPreview by mutableStateOf(DEFAULT_PREVIEW)
        private set
    private var blocks: List<UnicodeBlock>? = null

    fun load(uri: Uri) {
        error = null
        val app = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val resolver = app.contentResolver
                val raw = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    ?: return@withContext Result.failure<LoadedFont>(Exception("Failed to read font file."))
                val name = resolver.canonDisplayName(uri) ?: uri.lastPathSegment ?: ""
                runCatching { open(raw, name) }.recoverCatching { throw Exception("Not a valid font file.") }
            }
            result.onSuccess { font = it }.onFailure { error = it.message }
        }
    }

    private fun open(raw: ByteArray, fileName: String): LoadedFont {
        val sfnt = FontEngine.decode(raw)
        val info = FontEngine.parse(sfnt, fileName)
        val buffer = ByteBuffer.allocateDirect(sfnt.size).put(sfnt).also { it.rewind() }
        val family = FontFamily.Builder(Font.Builder(buffer).build()).build()
        val typeface = Typeface.CustomFallbackBuilder(family).setSystemFallback("sans-serif").build()
        val upm = info.unitsPerEm.toFloat()
        val blocks = blocks ?: GlyphEngine.parseBlocks(
            getApplication<Application>().assets.open("unicode-blocks.json").bufferedReader().use { it.readText() },
        ).also { blocks = it }
        return LoadedFont(info, typeface, inkHeight(typeface, "H", upm), inkHeight(typeface, "x", upm), blocks)
    }

    fun clear() {
        font = null
        error = null
    }

    fun type(text: String) {
        preview = text
    }

    fun settlePreview() {
        shownPreview = preview.ifEmpty { DEFAULT_PREVIEW }
    }
}

@Composable
fun FontExplorer(model: FontExplorerModel = viewModel()) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val font = model.font
    val info = font?.info

    LaunchedEffect(model.preview) {
        delay(TYPING_DELAY_MS)
        model.settlePreview()
    }

    val source = CanonSection.of {
        CanonRows {
            if (info == null) {
                CanonDropZone("Drop a font", icon = Icons.Outlined.TextFormat, mimeTypes = fontTypes, photos = null, paste = false) { uris ->
                    uris.firstOrNull()?.let(model::load)
                }
            } else {
                CanonHeading("Font")
                CanonRow(info.fullName, detail = info.fileName, flush = true) {
                    CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = model::clear)
                }
                CanonHeading("Preview")
                CanonTextCell(
                    model.preview,
                    model::type,
                    Modifier.fillMaxWidth().semantics { contentDescription = "Preview" },
                    placeholder = "Type to preview…",
                    width = 200.dp,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    onEndEditing = model::settlePreview,
                )
            }
            model.error?.let { CanonStatusRow(it, CanonTone.Warn) }
        }
    }

    val picture = CanonSection.canvas(CanonCanvasSize.Fixed(260.dp), background = c.card) {
        if (font == null) {
            FontMetricsPicture(FontEngine.SPECIMEN_LETTERS, Typeface.DEFAULT, standIn, null, "Font", null, "—", "")
        } else {
            FontMetricsPicture(font.specimen, font.typeface, font.metrics, font.info.unitsPerEm, font.info.fullName, font.info.fileName, "${font.info.unitsPerEm}", "UPM")
        }
    }

    if (font == null || info == null) {
        val placeholder = CanonSection.of {
            CanonRows { CanonEmptyRow("Load a .otf or .ttf file to inspect its metadata, metrics, glyphs, and OpenType tables.") }
        }
        CanonSplit(sidebar = listOf(source), main = listOf(placeholder), banner = picture)
        return
    }

    val overview = CanonSection.of {
        CanonRows {
            CanonHeading("Overview")
            FontEngine.overviewLines(info).forEach { (label, value) -> CanonValueLine(label, value, labelWidth = 112.dp) }
        }
    }
    val metrics = CanonSection.of {
        CanonRows {
            CanonHeading("Metrics")
            CanonTiles(FontEngine.metricLines(info, font.capHeight, font.xHeight).map { (label, value) -> CanonTile(label, value) })
        }
    }
    val tables = CanonSection.of {
        CanonRows {
            CanonHeading("Tables")
            info.tables.forEach { tag -> TableRow(tag, FontEngine.tableDescriptions[tag]) }
        }
    }
    val waterfall = CanonSection.of {
        CanonRows {
            CanonHeading("Waterfall")
            FontWaterfall(model.shownPreview, font)
        }
    }
    val chart = CanonSection.of {
        val range = font.chartRange
        val state = remember { GlyphChartState() }
        val sections = remember(range) { listOf(GlyphSection(null, range)) }
        CanonRows {
            CanonHeading("Glyphs · " + GlyphEngine.label(range.first) + "–%04X".format(range.last))
            GlyphChart(
                state,
                sections,
                selected = null,
                onPick = { CanonCopyFeedback.copy(view, String(Character.toChars(it))) },
                onHeading = {},
                typeface = font.typeface,
                covers = info.coverage::covers,
            )
        }
    }
    val css = CanonSection.of { CanonCodeBlock(FontEngine.css(info.familyName, info.fileName), language = "CSS") }

    CanonSplit(sidebar = listOf(source, overview, metrics, tables), main = listOf(waterfall, chart, css), banner = picture)
}

@Composable
private fun TableRow(tag: String, description: String?) {
    val c = LocalDelphiColours.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CanonMetrics.controlHeight)
            .background(c.card)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = CanonMetrics.panelPadding, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(tag, Modifier.width(52.dp).alignByBaseline(), style = tagMono, color = c.primary)
        Text(description ?: "—", Modifier.alignByBaseline(), style = footnote, color = c.secondaryForeground)
    }
}

/**
 * dt-ios FontMetricsPicture: the specimen set as large as the band allows on its vertical metrics, each line
 * labelled at the trailing edge with its value in font units. [unitsPerEm] null shows the names alone.
 */
@Composable
private fun FontMetricsPicture(
    text: String,
    typeface: Typeface,
    metrics: EmMetrics,
    unitsPerEm: Int?,
    caption: String,
    detail: String?,
    figure: String,
    unit: String,
) {
    val c = LocalDelphiColours.current
    val topInset = LocalCanonCanvas.current.top
    val measurer = rememberTextMeasurer()
    val paint = remember(typeface) { Paint(Paint.ANTI_ALIAS_FLAG).also { it.typeface = typeface } }
    var from by remember { mutableStateOf(metrics) }
    var to by remember { mutableStateOf(metrics) }
    val progress = remember { Animatable(1f) }
    fun shown(t: Float): EmMetrics {
        val e = PxToRemEngine.settle(t)
        fun mix(a: Float, b: Float) = a + (b - a) * e
        return EmMetrics(mix(from.ascent, to.ascent), mix(from.capHeight, to.capHeight), mix(from.xHeight, to.xHeight), mix(from.descent, to.descent))
    }
    LaunchedEffect(metrics) {
        if (metrics == to) return@LaunchedEffect
        from = shown(progress.value)
        to = metrics
        progress.snapTo(0f)
        progress.animateTo(1f, tween(600, easing = LinearEasing))
    }

    Box(
        Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = caption
                stateDescription = text
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val now = shown(progress.value)
            val padding = CanonMetrics.panelPadding.toPx()
            val top = topInset.toPx() + 48.dp.toPx()
            val labelRoom = 16.dp.toPx()
            fun units(share: Float) = unitsPerEm?.let { FontEngine.signed((share * it).roundToInt()) }
            val lines = listOf(
                Triple("Ascent", now.ascent, units(to.ascent)),
                Triple("Cap Height", now.capHeight, units(to.capHeight)),
                Triple("x-Height", now.xHeight, units(to.xHeight)),
                Triple("Baseline", 0f, units(0f)),
                Triple("Descent", -now.descent, units(-to.descent)),
            )
            val labels = lines.map { (name, _, value) ->
                measurer.measure(
                    buildAnnotatedString {
                        withStyle(labelName.toSpanStyle().copy(color = c.mutedForeground)) { append(name) }
                        if (value != null) withStyle(sizeMono.toSpanStyle().copy(color = c.foreground)) { append("  $value") }
                    },
                )
            }
            val column = labels.maxOf { it.size.width }
            val areaWidth = size.width - padding * 2
            val typeWidth = maxOf(0f, areaWidth - column - 12.dp.toPx())
            paint.textSize = 100f
            val advance = paint.measureText(text) / 100f
            val (em, baseline) = FontEngine.fit(now.ascent, now.descent, advance, top, typeWidth, size.height - padding - top, labelRoom)
            val ys = lines.map { baseline - it.second * em }

            drawRect(c.primary.copy(alpha = 0.07f), Offset(0f, ys[2]), Size(size.width, maxOf(0f, baseline - ys[2])))
            val rule = c.mutedForeground.copy(alpha = 0.55f)
            val hairline = CanonMetrics.hairline.toPx()
            ys.forEachIndexed { i, y ->
                when (i) {
                    3 -> drawRect(c.primary, Offset(0f, y - CanonMetrics.frame.toPx() / 2), Size(size.width, CanonMetrics.frame.toPx()))
                    1, 2 -> drawLine(rule, Offset(0f, y), Offset(size.width, y), hairline, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                    else -> drawRect(rule, Offset(0f, y - hairline / 2), Size(size.width, hairline))
                }
            }

            paint.textSize = maxOf(1f, em.roundToInt().toFloat())
            paint.color = (if (unitsPerEm != null) c.foreground else c.mutedForeground).toArgb()
            fadedText(text, paint, padding, baseline, padding + typeWidth)

            // overlapping labels are dropped
            val trailing = size.width - padding
            var ceiling = Float.MAX_VALUE
            val captionBottom = topInset.toPx() + 44.dp.toPx()
            for (i in ys.indices.sortedByDescending { ys[it] }) {
                val label = labels[i]
                val y = ys[i] - label.size.height - 1
                if (y + label.size.height > ceiling || y < captionBottom) continue
                ceiling = y
                drawText(label, topLeft = Offset(trailing - label.size.width, y))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = topInset + 4.dp)
                .height(44.dp)
                .padding(horizontal = CanonMetrics.panelPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(caption, style = captionStyle, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!detail.isNullOrEmpty()) Text(detail, style = detailStyle, color = c.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CanonFigure(figure, unit = unit, size = 28.sp, halo = c.card)
        }
    }
}

/** One line of [text] from [x] on [baseline], fading out over the last 40 px before [right] when it runs past. */
private fun DrawScope.fadedText(text: String, paint: Paint, x: Float, baseline: Float, right: Float) {
    val overflows = paint.measureText(text) > right - x + 0.5f
    clipRect(right = right) {
        if (!overflows) {
            drawIntoCanvas { it.nativeCanvas.drawText(text, x, baseline, paint) }
            return@clipRect
        }
        val fade = minOf(40.dp.toPx(), (right - x) / 3)
        val metrics = paint.fontMetrics
        val top = baseline + metrics.top
        val bottom = baseline + metrics.bottom
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(x, top, right, bottom), androidx.compose.ui.graphics.Paint())
            canvas.nativeCanvas.drawText(text, x, baseline, paint)
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), right - fade, right),
                Offset(right - fade, top),
                Size(fade, bottom - top),
                blendMode = BlendMode.DstOut,
            )
            canvas.restore()
        }
    }
}

/** dt-ios FontWaterfall: the preview at eight sizes, each row as deep as the font's ascent and descent need. */
@Composable
private fun FontWaterfall(text: String, font: LoadedFont) {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val paint = remember(font) { Paint(Paint.ANTI_ALIAS_FLAG).also { it.typeface = font.typeface } }
    val depth = font.metrics.ascent + font.metrics.descent
    val rows = waterfallSizes.map { maxOf(CanonMetrics.controlHeight.value, ceil(depth * it) + 20) }
    val total = rows.sum() + CanonMetrics.hairline.value * (rows.size - 1)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(total.dp)
            .background(c.card)
            .semantics {
                contentDescription = "Waterfall"
                stateDescription = text
            },
    ) {
        val padding = CanonMetrics.panelPadding.toPx()
        val textX = padding + 44.dp.toPx()
        val right = size.width - padding
        paint.color = c.foreground.toArgb()
        var y = 0f
        waterfallSizes.forEachIndexed { i, points ->
            val rowHeight = rows[i].dp.toPx()
            val textSize = with(density) { points.dp.toPx() }
            val ascent = font.metrics.ascent * textSize
            val descent = font.metrics.descent * textSize
            val baseline = y + (rowHeight + ascent - descent) / 2
            val name = measurer.measure("$points", sizeMono.copy(color = c.mutedForeground))
            drawText(name, topLeft = Offset(padding, baseline - name.size.height + 2.dp.toPx()))
            paint.textSize = textSize
            clipRect(textX, y, maxOf(textX, right), y + rowHeight) { fadedText(text, paint, textX, baseline, right) }
            y += rowHeight
            if (i != waterfallSizes.lastIndex) {
                drawRect(c.border, Offset(0f, y), Size(size.width, CanonMetrics.hairline.toPx()))
                y += CanonMetrics.hairline.toPx()
            }
        }
    }
}
