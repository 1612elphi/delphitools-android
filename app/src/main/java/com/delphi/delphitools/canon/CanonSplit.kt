package com.delphi.delphitools.canon

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.delphi.delphitools.ui.theme.LocalDelphiColours

/** A pane section: a canvas, or anything else, which keeps the panel padding unless it spans its column. */
@Immutable
class CanonSection internal constructor(
    internal val canvas: CanonCanvasSize?,
    internal val boxing: CanonCanvasBoxing,
    internal val spansColumn: Boolean,
    internal val content: @Composable () -> Unit,
) {
    internal fun boxed(layout: SplitLayout) =
        canvas != null && boxing == CanonCanvasBoxing.UnlessWide && layout != SplitLayout.Sidebar

    internal fun spans(layout: SplitLayout) = if (canvas != null) !boxed(layout) else spansColumn

    companion object {
        fun of(spansColumn: Boolean = false, content: @Composable () -> Unit) =
            CanonSection(null, CanonCanvasBoxing.Never, spansColumn, content)

        fun canvas(
            size: CanonCanvasSize,
            background: Color = Color.Unspecified,
            maxHeight: Dp? = null,
            boxing: CanonCanvasBoxing = CanonCanvasBoxing.Never,
            showsPattern: Boolean = false,
            content: @Composable BoxScope.() -> Unit,
        ) = CanonSection(size, boxing, true) {
            CanonCanvas(size, background = background, maxHeight = maxHeight, boxing = boxing, showsPattern = showsPattern, content = content)
        }
    }
}

internal enum class SplitLayout { Stacked, Sidebar, Equal, Laptop }

private val stackingWidth = 640.dp
private const val BANNER_LIMIT = 0.45f
private const val COLLAPSE_LIMIT = 0.6f
private val footerMaxWidth = 480.dp
private val footerInset = 32.dp
private val stackedMaxWidth = 720.dp

internal fun splitLayout(width: Dp, height: Dp, beside: Boolean, across: Boolean, halves: Boolean): SplitLayout {
    if (across) return SplitLayout.Laptop
    // compact width (< 600 dp) is inside the stacking width
    val stacks = height > width || !beside && width < stackingWidth
    return when {
        stacks -> SplitLayout.Stacked
        !beside && !halves -> SplitLayout.Sidebar
        else -> SplitLayout.Equal
    }
}

/** The fold split into a crease the panes meet at side by side or one above the other; near an edge it is neither. */
internal fun creases(hinge: Rect?, width: Float, height: Float): Pair<Rect?, Rect?> {
    if (hinge == null) return null to null
    return if (hinge.height >= hinge.width) {
        (if (hinge.center.x / width in 0.25f..0.75f) hinge else null) to null
    } else {
        null to (if (hinge.center.y / height in 0.25f..0.75f) hinge else null)
    }
}

@Composable
private fun rememberFold(): FoldingFeature? {
    val context = LocalContext.current
    val flow = remember(context) { WindowInfoTracker.getOrCreate(context).windowLayoutInfo(context) }
    val info by flow.collectAsState(null)
    return info?.displayFeatures?.firstNotNullOfOrNull { it as? FoldingFeature }
}

/**
 * Sidebar beside a main pane: the layout every tool starts from.
 *
 * Wide and no hinge, a 340 dp sidebar on the leading side; a vertical fold or `halves` splits equally at the crease
 * or the middle. Narrower than 640 dp, or portrait, one column, sidebar first (`mainLeadsStacked` swaps them).
 * Half-opened with the fold across the middle, a `halves` sidebar that is one canvas fills the top half and the page
 * scrolls in the bottom half. The banner spans every column, at most 45% of the height; `collapsesTo` shrinks the
 * one-column picture as the page scrolls. The footer floats over the bottom of the page, over the main pane beside a sidebar.
 */
@Composable
fun CanonSplit(
    sidebar: List<CanonSection>,
    main: List<CanonSection>,
    modifier: Modifier = Modifier,
    banner: CanonSection? = null,
    sidebarWidth: Dp = 340.dp,
    bannerSticks: Boolean = false,
    mainLeadsStacked: Boolean = false,
    halves: Boolean = false,
    pictureShare: Float = 0.5f,
    collapsesTo: Dp? = null,
    oneColumn: Boolean = false,
    isFooterHidden: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
) {
    // ponytail: sections are recomposed when the layout changes, so state they remember resets; movableContentOf keeps it
    // ponytail: no pull-down stretch of a collapsing picture, no keyboard-height guard, no endPiece or toast band
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val fold = rememberFold()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val sides = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    val safeTop = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues().calculateTopPadding()
    val below = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val leadingScroll = rememberScrollState()
    val trailingScroll = rememberScrollState()

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(c.background)
            .windowInsetsPadding(sides)
            .onGloballyPositioned { origin = it.positionInWindow() },
    ) {
        val width = maxWidth
        val height = maxHeight
        val hinge = fold?.bounds?.let { Rect(it.left - origin.x, it.top - origin.y, it.right - origin.x, it.bottom - origin.y) }
        val (beside, acrossLine) = creases(hinge, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val picture = if (halves && sidebar.size == 1) sidebar[0].takeIf { it.canvas != null } else null
        val across = acrossLine?.takeIf { fold?.state == FoldingFeature.State.HALF_OPENED && picture != null && banner == null }
        val layout = if (oneColumn) SplitLayout.Stacked else splitLayout(width, height, beside != null, across != null, halves)
        val room = height - safeTop
        val gap = maxOf(CanonMetrics.panelPadding, below * 0.6f)

        @Composable
        fun Banner(section: CanonSection) {
            Box(Modifier.fillMaxWidth().heightIn(max = room * BANNER_LIMIT + safeTop)) {
                CompositionLocalProvider(LocalCanvasPlacement provides CanvasPlacement(boxed = false, bleedTop = safeTop, showsPattern = true)) {
                    section.content()
                }
            }
        }

        @Composable
        fun Pane(sections: List<CanonSection>, scroll: ScrollState, top: Dp, clearance: Dp, cap: Dp?, hasFooter: Boolean, paneModifier: Modifier) {
            var footerHeight by remember { mutableIntStateOf(0) }
            val floats = hasFooter && footer != null && !isFooterHidden
            BoxWithConstraints(paneModifier) {
                val fits = maxWidth > footerInset * 2
                Page(
                    sections, layout, cap, top, clearance,
                    bottom = if (floats) with(density) { footerHeight.toDp() } + gap else below,
                    scroll,
                )
                if (floats && fits) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = footerInset)
                            .padding(bottom = gap)
                            .widthIn(max = footerMaxWidth)
                            .fillMaxWidth()
                            .onSizeChanged { footerHeight = it.height }
                            .dropShadow(RectangleShape, Shadow(radius = 8.dp, color = Color.Black, offset = DpOffset(0.dp, 2.dp), alpha = 0.1f))
                            .background(c.border)
                            .padding(CanonMetrics.frame),
                    ) { footer!!() }
                }
            }
        }

        when (layout) {
            SplitLayout.Stacked -> {
                val collapsing = if (collapsesTo != null) banner ?: picture else null
                val floats = bannerSticks || collapsesTo != null
                val rest = sidebar.filter { it !== collapsing }
                val sections = (if (!floats && banner != null) listOf(banner) else emptyList()) +
                    (if (mainLeadsStacked) main + rest else rest + main)
                if (collapsing != null && collapsesTo != null) {
                    // ponytail: a fitting picture collapses from the collapse limit, not its measured height
                    val full = minOf(collapsing.canvas?.band(width, null) ?: (room * COLLAPSE_LIMIT), room * COLLAPSE_LIMIT)
                    Box(Modifier.fillMaxSize()) {
                        Pane(sections, leadingScroll, 0.dp, safeTop + full, stackedMaxWidth, true, Modifier.fillMaxSize())
                        val scrolled = with(density) { leadingScroll.value.toDp() }
                        val band = maxOf(minOf(collapsesTo, full), full - scrolled)
                        Box(Modifier.fillMaxWidth().height(safeTop + band)) {
                            CompositionLocalProvider(
                                LocalCanvasPlacement provides CanvasPlacement(boxed = false, bleedTop = safeTop, fillsPane = true, showsPattern = collapsing === banner),
                            ) { collapsing.content() }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        if (floats && banner != null) Banner(banner)
                        Pane(sections, leadingScroll, if (floats && banner != null) 0.dp else safeTop, 0.dp, stackedMaxWidth, true, Modifier.weight(1f))
                    }
                }
            }
            SplitLayout.Laptop -> Column(Modifier.fillMaxSize()) {
                val seam = with(density) { across!!.center.y.toDp() } - CanonMetrics.hairline / 2
                Box(Modifier.fillMaxWidth().height(seam)) {
                    CompositionLocalProvider(LocalCanvasPlacement provides CanvasPlacement(boxed = false, bleedTop = safeTop, fillsPane = true)) {
                        picture!!.content()
                    }
                }
                Box(Modifier.fillMaxWidth().height(CanonMetrics.hairline).background(c.border))
                Pane(main, trailingScroll, 0.dp, 0.dp, null, true, Modifier.weight(1f))
            }
            else -> Column(Modifier.fillMaxSize()) {
                if (banner != null) Banner(banner)
                val top = if (banner != null) 0.dp else safeTop
                val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                val hairline = CanonMetrics.hairline
                // seam's left edge, in physical coordinates
                val seamLeft = when {
                    beside != null -> with(density) { beside.center.x.toDp() } - hairline / 2
                    halves -> ((width - hairline) * pictureShare).let { if (rtl) width - hairline - it else it }
                    else -> minOf(sidebarWidth, width * 0.4f).let { if (rtl) width - it - hairline else it }
                }
                val leadingWidth = if (rtl) width - seamLeft - hairline else seamLeft
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Box(Modifier.width(leadingWidth).fillMaxHeight()) {
                        if (picture != null) {
                            CompositionLocalProvider(LocalCanvasPlacement provides CanvasPlacement(boxed = false, bleedTop = top, fillsPane = true)) {
                                picture.content()
                            }
                        } else {
                            Pane(sidebar, leadingScroll, top, 0.dp, null, false, Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.width(hairline).fillMaxHeight().background(c.border))
                    Pane(main, trailingScroll, top, 0.dp, null, true, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/** One scrolling column of sections; the first, if it is a canvas spanning the column, reaches up under the status bar. */
@Composable
private fun Page(
    sections: List<CanonSection>,
    layout: SplitLayout,
    maxWidth: Dp?,
    top: Dp,
    clearance: Dp,
    bottom: Dp,
    scroll: ScrollState,
) {
    val first = sections.firstOrNull()
    val bleeds = clearance == 0.dp && first?.canvas != null && first.spans(layout)
    CanonJumpColumn(Modifier.fillMaxSize(), scroll, top = top + clearance) {
        Spacer(Modifier.height(if (bleeds) 0.dp else top + clearance + CanonMetrics.panelPadding))
        sections.forEachIndexed { index, section ->
            if (index > 0) Spacer(Modifier.height(CanonMetrics.panelPadding))
            val placement = CanvasPlacement(
                boxed = if (section.canvas != null) section.boxed(layout) else null,
                bleedTop = if (index == 0 && bleeds) top else 0.dp,
            )
            CompositionLocalProvider(LocalCanvasPlacement provides placement) {
                if (section.spans(layout)) {
                    section.content()
                } else {
                    Box(Modifier.fillMaxWidth().padding(horizontal = CanonMetrics.panelPadding), contentAlignment = Alignment.TopCenter) {
                        Box((if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier).fillMaxWidth()) { section.content() }
                    }
                }
            }
        }
        Spacer(Modifier.height(bottom + CanonMetrics.panelPadding))
    }
}
