package com.delphi.delphitools.tools.devtools

import com.delphi.delphitools.tools.TYPING_DELAY_MS
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCodeBlock
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonTextArea
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay

private val urlStyle = TextStyle(fontFamily = DelphiMono, fontSize = 15.sp)
private val urlKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Uri,
    imeAction = ImeAction.Done,
)
private val textKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done)

private fun font(size: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = DelphiFont, fontSize = size.sp, fontWeight = weight)
private val caption2Semibold = font(11, FontWeight.SemiBold)
private val subheadlineSemibold = font(15, FontWeight.SemiBold)
private val footnoteStyle = font(12)
private val footnoteMedium = font(12, FontWeight.Medium)
private val footnoteBold = font(12, FontWeight.Bold)
private val caption1 = font(11)
private val title3 = font(18)
private val imageLabel = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

// card width in one column and beside the result, the result's widest, and the room from which the result joins the banner
private val cardWidth = 272.dp
private val cardBesideWidth = 300.dp
private val resultWidth = 420.dp
private val besideRoom = 620.dp
private val boardGap = 32.dp
private val sheetInset = 14.dp
private val cardInset = 12.dp
private const val CARD_ASPECT = 1.91f
private val thumbnail = 96.dp
private val markSide = 28.dp

/** dt-ios MetaTagGeneratorViewController's fields; the preview and code follow [fields] once typing pauses. */
class MetaTagGeneratorModel : ViewModel() {
    var fields by mutableStateOf(MetaTagFields())
    var shown by mutableStateOf(MetaTagFields())

    /** The banner has room for the search result, so the page leaves it out. */
    var resultInBanner by mutableStateOf(false)
}

@Composable
fun MetaTagGenerator(model: MetaTagGeneratorModel = viewModel()) {
    val context = LocalContext.current
    val c = LocalDelphiColours.current
    val f = model.fields
    val shown = model.shown
    LaunchedEffect(f) {
        if (f.ogType != shown.ogType || f.twitterCard != shown.twitterCard) {
            model.shown = f
            return@LaunchedEffect
        }
        delay(TYPING_DELAY_MS)
        model.shown = f
    }
    val html = remember(shown) { MetaTagEngine.html(shown) }

    fun edit(change: (MetaTagFields) -> MetaTagFields) {
        model.fields = change(model.fields)
    }

    @Composable
    fun cell(value: String, url: Boolean = false, placeholder: String = "", change: (String) -> MetaTagFields) {
        // urls stack when narrow
        CanonTextCell(
            value,
            { model.fields = change(it) },
            Modifier.fillMaxHeight(),
            placeholder = placeholder,
            width = if (url) 300.dp else 200.dp,
            textStyle = if (url) urlStyle else CanonText.body,
            keyboardOptions = if (url) urlKeyboard else textKeyboard,
        )
    }

    fun count(length: Int, limit: Int) = "$length / $limit"
    fun countColour(length: Int, limit: Int) = if (length > limit) c.destructive else c.mutedForeground

    val page = CanonSection.of {
        CanonRows {
            CanonHeading("Basic Meta Tags")
            val titleLength = shown.pageTitle.length
            CanonRow(
                "Page Title",
                flush = true,
                detail = count(titleLength, MetaTagEngine.TITLE_LIMIT),
                detailColour = countColour(titleLength, MetaTagEngine.TITLE_LIMIT),
            ) { cell(f.pageTitle) { f.copy(pageTitle = it) } }
            val descriptionLength = shown.description.length
            CanonRow(
                "Description",
                stacked = true,
                detail = count(descriptionLength, MetaTagEngine.DESCRIPTION_LIMIT),
                detailColour = countColour(descriptionLength, MetaTagEngine.DESCRIPTION_LIMIT),
            ) { CanonTextArea(f.description, { text -> edit { it.copy(description = text) } }, height = 96.dp, label = "Description") }
            CanonRow("Keywords", flush = true) { cell(f.keywords) { f.copy(keywords = it) } }
            CanonRow("Author", flush = true) { cell(f.author) { f.copy(author = it) } }
            CanonRow("Canonical URL", flush = true) { cell(f.canonicalUrl, url = true, placeholder = "https://example.com") { f.copy(canonicalUrl = it) } }
        }
    }

    val social = CanonSection.of {
        CanonRows {
            CanonHeading("Social Sharing")
            CanonRow("Site name", flush = true) { cell(f.siteName) { f.copy(siteName = it) } }
            CanonRow("OG Image URL", flush = true) { cell(f.ogImageUrl, url = true, placeholder = "https://example.com/og.png") { f.copy(ogImageUrl = it) } }
            CanonRow("Creator", flush = true) { cell(f.twitterHandle, placeholder = "@handle") { f.copy(twitterHandle = it) } }
            CanonHeading("OG Type")
            CanonSegmented(MetaTagEngine.ogTypes, MetaTagEngine.ogTypes.indexOf(f.ogType), columns = 3) { i -> edit { it.copy(ogType = MetaTagEngine.ogTypes[i]) } }
            CanonHeading("Twitter Card")
            CanonSegmented(MetaTagEngine.twitterCards, MetaTagEngine.twitterCards.indexOf(f.twitterCard), columns = 2) { i ->
                edit { it.copy(twitterCard = MetaTagEngine.twitterCards[i]) }
            }
        }
    }

    val action = CanonSection.of {
        CanonRows {
            CanonButton("Copy", kind = CanonButtonKind.Primary, icon = Icons.Outlined.ContentCopy, oversized = true, flashesCopied = true) {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", html))
            }
        }
    }

    val search = CanonSection.of {
        CanonRows {
            CanonHeading("Search")
            Box(Modifier.fillMaxWidth().background(c.card).padding(CanonMetrics.panelPadding)) { LinkPreviewResult(shown) }
        }
    }

    val output = CanonSection.of {
        CanonRows {
            CanonHeading("Generated Tags")
            CanonCodeBlock(html, language = "HTML")
        }
    }

    val banner = CanonSection.canvas(CanonCanvasSize.Fitting) {
        LinkPreviewBoard(shown) { model.resultInBanner = it }
    }

    CanonSplit(
        sidebar = listOf(page, social, action),
        main = if (model.resultInBanner) listOf(output) else listOf(search, output),
        banner = banner,
    )
}

/**
 * dt-ios LinkPreviewBoard: the page as it shows when the link is shared, as a finished object. A caption, then a card at
 * the size a feed shows it, and where there is room the search result beside it. Generic on purpose: no service's look.
 * The image is never fetched; its frame names the file. Empty parts are grey bars where their text will go.
 */
@Composable
private fun LinkPreviewBoard(content: MetaTagFields, onResultShown: (Boolean) -> Unit) {
    // ponytail: no crossfade between contents (iOS dissolves for 0.2 s)
    val c = LocalDelphiColours.current
    val top = LocalCanonCanvas.current.top
    val host = MetaTagEngine.host(content.canonicalUrl)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "Preview"
                stateDescription = listOf(content.siteName, host, content.pageTitle, content.description).filter { it.isNotEmpty() }.joinToString(", ")
            },
    ) {
        val room = maxWidth - CanonMetrics.panelPadding * 2
        val beside = room >= besideRoom
        SideEffect { onResultShown(beside) }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = CanonMetrics.panelPadding, end = CanonMetrics.panelPadding, top = top + CanonMetrics.panelPadding * 0.75f, bottom = CanonMetrics.panelPadding + 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                (listOf("Preview".uppercase()) + listOfNotNull(host.ifEmpty { null })).joinToString("  ·  "),
                style = caption2Semibold,
                color = c.mutedForeground,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            if (beside) {
                val card = minOf(room, cardBesideWidth)
                val result = minOf(room - card - boardGap - sheetInset * 2, resultWidth)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinkPreviewCard(content, card)
                    Spacer(Modifier.width(boardGap))
                    Box(Modifier.padding(vertical = sheetInset).previewSheet().padding(sheetInset).width(result)) { LinkPreviewResult(content) }
                }
            } else {
                LinkPreviewCard(content, minOf(room, cardWidth))
            }
        }
    }
}

/** The surface a preview sits on: the page colour, a hairline and a soft shadow, so it reads as a finished object. */
@Composable
private fun Modifier.previewSheet(): Modifier {
    val c = LocalDelphiColours.current
    // ponytail: elevation shadow in place of iOS's 12 pt radius at 12% opacity
    return shadow(6.dp, ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
        .background(c.background)
        .border(CanonMetrics.hairline, c.border)
}

/** A text, or grey bars where it will go while it is empty: one bar per line, at the given shares of the width. */
@Composable
private fun PreviewText(text: String, style: TextStyle, colour: Color, maxLines: Int, empty: List<Float>, modifier: Modifier = Modifier) {
    if (text.isNotBlank()) {
        Text(text, style = style, color = colour, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier.fillMaxWidth())
        return
    }
    val c = LocalDelphiColours.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val line = remember(style) { with(density) { measurer.measure("A", style).size.height.toDp() } }
    Canvas(modifier.fillMaxWidth().height(line * empty.size)) {
        val lineHeight = line.toPx()
        val bar = Math.round(lineHeight * 0.52f).toFloat()
        empty.forEachIndexed { index, share ->
            drawRect(c.muted, Offset(0f, index * lineHeight + (lineHeight - bar) / 2), Size(size.width * share, bar))
        }
    }
}

/** The shared link. A large card puts the image above the text; a summary card puts a square beside it. */
@Composable
private fun LinkPreviewCard(content: MetaTagFields, width: Dp) {
    val c = LocalDelphiColours.current
    val site = content.siteName.trim().ifEmpty { MetaTagEngine.host(content.canonicalUrl) }.uppercase()
    val texts = @Composable {
        PreviewText(site, caption2Semibold, c.mutedForeground, 1, listOf(0.4f))
        Spacer(Modifier.height(4.dp))
        PreviewText(content.pageTitle, subheadlineSemibold, c.foreground, 2, listOf(0.95f, 0.6f))
        Spacer(Modifier.height(3.dp))
        PreviewText(content.description, footnoteStyle, c.mutedForeground, 2, listOf(1f, 0.75f))
    }
    Box(Modifier.width(width).previewSheet()) {
        if (content.isLarge) {
            Column {
                PreviewImage(content.ogImageUrl, true, Modifier.fillMaxWidth().aspectRatio(CARD_ASPECT))
                Column(Modifier.padding(cardInset)) { texts() }
            }
        } else {
            Row(Modifier.heightIn(min = thumbnail).height(IntrinsicSize.Min)) {
                PreviewImage(content.ogImageUrl, false, Modifier.width(thumbnail).fillMaxHeight())
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(cardInset),
                    verticalArrangement = Arrangement.Center,
                ) { texts() }
            }
        }
    }
}

/** Where the shared image goes: a muted frame with the picture symbol and the file name, or the size to make it. */
@Composable
private fun PreviewImage(address: String, isLarge: Boolean, modifier: Modifier) {
    val c = LocalDelphiColours.current
    val file = MetaTagEngine.fileName(address)
    // og and summary sizes
    val label = file.ifEmpty { if (isLarge) "1200 × 630" else "144 × 144" }
    Column(
        modifier.background(c.muted).padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Image, null, tint = c.mutedForeground, modifier = Modifier.size(22.dp))
        Text(label, style = imageLabel, color = c.mutedForeground, maxLines = 2, overflow = TextOverflow.MiddleEllipsis, textAlign = TextAlign.Center)
    }
}

/** The page as a search result: a mark with the site's initial, the site and the trail, the title in the link colour and the description, cut at the lengths results show. */
@Composable
private fun LinkPreviewResult(content: MetaTagFields) {
    val c = LocalDelphiColours.current
    val site = content.siteName.trim().ifEmpty { MetaTagEngine.host(content.canonicalUrl) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.heightIn(min = markSide), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(markSide).background(c.muted), contentAlignment = Alignment.Center) {
                Text(site.take(1).uppercase(), style = footnoteBold, color = c.mutedForeground)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                PreviewText(site, footnoteMedium, c.foreground, 1, listOf(0.35f))
                PreviewText(MetaTagEngine.trail(content.canonicalUrl), caption1, c.mutedForeground, 1, listOf(0.6f))
            }
        }
        Spacer(Modifier.height(8.dp))
        PreviewText(MetaTagEngine.cut(content.pageTitle.trim(), MetaTagEngine.TITLE_LIMIT), title3, c.primary, 2, listOf(0.8f))
        Spacer(Modifier.height(4.dp))
        PreviewText(MetaTagEngine.cut(content.description.trim(), MetaTagEngine.DESCRIPTION_LIMIT), footnoteStyle, c.mutedForeground, 3, listOf(1f, 0.9f, 0.45f))
    }
}
