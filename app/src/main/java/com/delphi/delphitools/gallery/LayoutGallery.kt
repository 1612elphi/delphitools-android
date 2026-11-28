package com.delphi.delphitools.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonGlyphFlow
import com.delphi.delphitools.canon.CanonGlyphKind
import com.delphi.delphitools.canon.CanonGlyphMetrics
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonJumpGroup
import com.delphi.delphitools.canon.CanonJumpHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonPageStrip
import com.delphi.delphitools.canon.CanonPageStripHeight
import com.delphi.delphitools.canon.CanonPageStripPage
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStepper
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.LocalCanonPatternSymbol
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.canon.rememberCanonGlyphFlowState
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours

private const val TYPED = "Delphi tools canon"
private val a4 = Size(595f, 842f)

@Composable
private fun GalleryArt(label: String) {
    val c = LocalDelphiColours.current
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(c.primary, c.destructive)))) {
        Text(
            label,
            style = CanonText.bodySemibold,
            color = c.primaryForeground,
            modifier = Modifier.align(Alignment.Center).padding(top = LocalCanonCanvas.current.top),
        )
    }
}

/** The layout group on one CanonSplit, as the iOS gallery is: switches in the sidebar change the split itself. */
@Composable
fun LayoutGallery() {
    val c = LocalDelphiColours.current
    var halves by remember { mutableStateOf(false) }
    var oneColumn by remember { mutableStateOf(false) }
    var bannerSticks by remember { mutableStateOf(false) }
    var mainLeads by remember { mutableStateOf(false) }
    var collapses by remember { mutableStateOf(false) }
    var footerHidden by remember { mutableStateOf(false) }
    var picture by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf(true) }
    var number by remember { mutableIntStateOf(200) }
    var lines by remember { mutableIntStateOf(1) }
    var text by remember { mutableStateOf("Glyph flow") }
    var selectedPage by remember { mutableStateOf<Int?>(1) }
    var pages by remember {
        mutableStateOf(
            listOf(
                CanonPageStripPage(a4, name = "Plain"),
                CanonPageStripPage(Size(842f, 595f), name = "Lit"),
                CanonPageStripPage(a4, name = "Depth 2", depth = 2),
                CanonPageStripPage(a4, name = "Area", area = androidx.compose.ui.geometry.Rect(60f, 80f, 535f, 760f)),
            ),
        )
    }
    val glyphs = rememberCanonGlyphFlowState<Char>(
        CanonGlyphMetrics(height = 36.dp, rowGap = 8.dp, gap = 4.dp),
        measure = { 28.dp },
        kind = { if (it == '\n') CanonGlyphKind.Newline else if (it.isWhitespace()) CanonGlyphKind.Space else CanonGlyphKind.Glyph },
    )
    LaunchedEffect(text) { glyphs.show(text.toList()) }
    val measurer = rememberTextMeasurer()
    val glyphStyle = CanonText.bodySemibold.copy(color = c.foreground)

    val rows = CanonSection.of {
        CanonRows {
            CanonJumpHeading("Rows")
            CanonRow("Plain") { CanonSwitch(true) {} }
            CanonRow("Number", flush = true, controlWeight = 1.4f) {
                CanonStepper(number, { number = it }, "px", Modifier.fillMaxSize(), step = 50)
            }
            CanonJumpHeading("Split")
            CanonRow("Halves") { CanonSwitch(halves) { halves = it } }
            CanonRow("Picture") { CanonSwitch(picture) { picture = it } }
            CanonRow("One column") { CanonSwitch(oneColumn) { oneColumn = it } }
            CanonRow("Main leads") { CanonSwitch(mainLeads) { mainLeads = it } }
            CanonJumpHeading("Banner")
            CanonRow("Banner") { CanonSwitch(banner) { banner = it } }
            CanonRow("Banner sticks") { CanonSwitch(bannerSticks) { bannerSticks = it } }
            CanonRow("Collapses") { CanonSwitch(collapses) { collapses = it } }
            CanonRow("Hide footer") { CanonSwitch(footerHidden) { footerHidden = it } }
        }
    }
    val pictureCanvas = CanonSection.canvas(CanonCanvasSize.Aspect(1.4f)) { GalleryArt("Picture") }
    val groups = ('A'..'F').mapIndexed { index, letter -> CanonJumpGroup("$letter", itemIndex = index * 6, detail = "5") }
    val list = rememberLazyListState()
    val main = listOf(
        CanonSection.canvas(CanonCanvasSize.Aspect(4f)) { GalleryArt("Column canvas") },
        CanonSection.canvas(CanonCanvasSize.Fitting) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { lines = lines % 4 + 1 }
                    .padding(16.dp),
            ) {
                for (line in 1..lines) Text("Line $line", style = CanonText.bodySemibold, color = c.foreground)
            }
        },
        CanonSection.canvas(CanonCanvasSize.Fixed(CanonPageStripHeight), background = c.muted) {
            CanonPageStrip(
                pages,
                selected = selectedPage,
                onSelect = { selectedPage = it },
                reorders = true,
                onMove = { from, to -> pages = pages.toMutableList().apply { add(to, removeAt(from)) } },
                menu = { index ->
                    listOf(
                        "Duplicate" to { pages = pages.toMutableList().apply { add(index + 1, pages[index]) } },
                        "Delete" to { pages = pages.toMutableList().apply { removeAt(index) } },
                    )
                },
            )
        },
        CanonSection.canvas(CanonCanvasSize.Fixed(120.dp)) {
            CanonGlyphFlow(
                glyphs,
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { point ->
                            val index = glyphs.glyphIndex(point, slop = 4.dp) ?: return@detectTapGestures
                            text = text.removeRange(index, index + 1)
                        }
                    },
            ) { glyph, _, frame ->
                if (glyph.isWhitespace()) return@CanonGlyphFlow
                val inset = 0.5.dp.toPx()
                drawRect(c.border, Offset(frame.left + inset, frame.top + inset), Size(frame.width - inset * 2, frame.height - inset * 2), style = Stroke(1.dp.toPx()))
                val laid = measurer.measure("$glyph", glyphStyle)
                drawText(laid, topLeft = Offset(frame.center.x - laid.size.width / 2f, frame.center.y - laid.size.height / 2f))
            }
        },
        CanonSection.of {
            CanonRows {
                CanonHeading("Glyph flow")
                CanonGrid {
                    CanonButton("Type", Modifier.weight(1f).fillMaxHeight()) {
                        text += TYPED[text.length % TYPED.length]
                    }
                    CanonButton("Delete", Modifier.weight(1f).fillMaxHeight()) { text = text.dropLast(1) }
                    CanonButton("Reset", Modifier.weight(1f).fillMaxHeight()) { text = "Glyph flow" }
                }
            }
        },
        CanonSection.canvas(CanonCanvasSize.Aspect(3f), boxing = CanonCanvasBoxing.UnlessWide) { GalleryArt("Unless wide") },
        CanonSection.of {
            CanonRows {
                CanonHeading("Lazy list")
                LazyColumn(Modifier.height(260.dp).background(c.card), state = list) {
                    groups.forEach { group ->
                        stickyHeader(key = group.title) { CanonJumpHeading(group.title, list, groups) }
                        items((1..5).toList(), key = { "${group.title}$it" }) { item ->
                            Text(
                                "${group.title}$item",
                                style = CanonText.body,
                                color = c.foreground,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = CanonMetrics.controlHeight)
                                    .padding(horizontal = CanonMetrics.panelPadding, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
    )
    CompositionLocalProvider(LocalCanonPatternSymbol provides pencil(R.drawable.pencil_layout_grid)) {
        CanonSplit(
            sidebar = if (picture) listOf(pictureCanvas) else listOf(rows),
            main = if (picture) listOf(rows) + main else main,
            banner = if (banner) {
                CanonSection.canvas(CanonCanvasSize.Fixed(120.dp)) {
                    Text(
                        "Banner",
                        style = CanonText.bodySemibold,
                        color = c.foreground,
                        modifier = Modifier.align(Alignment.Center).padding(top = LocalCanonCanvas.current.top),
                    )
                }
            } else null,
            halves = halves,
            bannerSticks = bannerSticks,
            mainLeadsStacked = mainLeads,
            collapsesTo = if (collapses) 56.dp else null,
            oneColumn = oneColumn,
            isFooterHidden = footerHidden,
            footer = {
                CanonGrid {
                    CanonButton("Loop", Modifier.weight(1f).fillMaxHeight(), icon = pencil(R.drawable.pencil_repeat)) {}
                    CanonButton("Play", Modifier.weight(1f).fillMaxHeight(), kind = CanonButtonKind.Primary, icon = pencil(R.drawable.pencil_play)) {}
                    CanonButton("Stop", Modifier.weight(1f).fillMaxHeight(), icon = pencil(R.drawable.pencil_square)) {}
                }
            },
        )
    }
}
