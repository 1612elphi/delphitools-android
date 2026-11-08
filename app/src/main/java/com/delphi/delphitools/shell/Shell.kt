package com.delphi.delphitools.shell

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.LocalCanonPatternSymbol
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.tools.audiovideo.TimecodeCalc
import com.delphi.delphitools.tools.images.ImageCompressor
import com.delphi.delphitools.gallery.galleryPages
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// dt-ios Shell/FanMenu.swift FanItemView and Shell.swift StripBackdropView metrics
private val tabGap = 18.dp
private val tabOverhang = 8.dp
private val tabFillet = 12.dp
private val tabCorner = 16.dp
private val ruleWidth = 1.5.dp
private val nameLimit = 280.dp
private val pillHeight = 36.dp
private val pillName = CanonText.bodySemibold.copy(fontSize = 17.sp)
private const val SWAP_MS = 250

// toolbar row over the navigation bar; iOS is 49 pt over a 34 pt home indicator
private val bandRow = 60.dp
private val fadeHeight = 15.dp
private val swapTravel = 14.dp
private val previewCorner = 28.dp

// fits the 72 pt vertical item and the tab gap
private val sideBar = 96.dp
private val verticalPillWidth = 72.dp

// the home's buttons beside the tab, square chips on the strip
private val stripButton = 44.dp
private val stripButtonGap = 8.dp

/** The app frame: the page inset in the strip, the tab naming it, and the fan behind the tab. */
@Composable
fun Shell() {
    CompositionLocalProvider(LocalShellStores provides rememberShellStores()) { ShellFrame() }
}

@Composable
private fun ShellFrame() {
    val c = LocalDelphiColours.current
    val stores = LocalShellStores.current
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var favourOpen by remember { mutableStateOf(false) }
    var splashing by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    var whatsNewDue by remember { mutableStateOf(false) }
    LaunchedEffect(splashing) { if (!splashing && WhatsNew.shouldShow(context)) whatsNewDue = true }
    var toolId by rememberSaveable { mutableStateOf<String?>(null) }
    val tool = toolId?.let(::toolWithId)
    var fanOpen by remember { mutableStateOf(false) }
    val lattice = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val back = remember { Animatable(0f) }
    var backEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val previewing by remember { derivedStateOf { back.value > 0f } }
    PredictiveBackHandler(enabled = tool != null) { events ->
        try {
            events.collect {
                backEdge = it.swipeEdge
                back.snapTo(it.progress)
            }
            toolId = null
            scope.launch {
                delay(SWAP_MS.toLong())
                back.snapTo(0f)
            }
        } catch (cancelled: CancellationException) {
            scope.launch { back.animateTo(0f, spring()) }
            throw cancelled
        }
    }
    // ponytail: iOS also records the workflow visit here
    val open: (Tool) -> Unit = {
        stores.recordUsage(it.id)
        scope.launch { back.snapTo(0f) }
        toolId = it.id
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.accentBg)) {
        // iOS moves the toolbar to a side edge by the verticalBarEdge trait; here every landscape window does
        val vertical = maxWidth > maxHeight
        // ponytail: iOS takes the side from the system's verticalBarEdge, Android has none; the trailing edge
        val barOnLeft = vertical && LocalLayoutDirection.current == LayoutDirection.Rtl
        val edge = when {
            !vertical -> TabEdge.Bottom
            barOnLeft -> TabEdge.Left
            else -> TabEdge.Right
        }
        val safe = WindowInsets.safeDrawing.asPaddingValues()
        val navigation = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val left = safe.calculateLeftPadding(LayoutDirection.Ltr) + if (edge == TabEdge.Left) sideBar else 0.dp
        val right = safe.calculateRightPadding(LayoutDirection.Ltr) + if (edge == TabEdge.Right) sideBar else 0.dp
        val band = if (vertical) 0.dp else navigation + bandRow
        val travel = with(LocalDensity.current) { swapTravel.roundToPx() }

        val tape = stores.backdrop.tape?.let { ImageBitmap.imageResource(it) }
        val darkTheme = isSystemInDarkTheme()
        // ponytail: no workflow ticket over the backdrop, and the status bar glyphs ignore it (iOS: wantsLightGlyphs)
        Canvas(Modifier.fillMaxSize()) {
            val strip = when (edge) {
                TabEdge.Bottom -> Rect(0f, size.height - band.toPx(), size.width, size.height)
                TabEdge.Left -> Rect(0f, 0f, left.toPx(), size.height)
                TabEdge.Right -> Rect(size.width - right.toPx(), 0f, size.width, size.height)
            }
            drawBackdrop(stores.backdrop.base(c, darkTheme), tape, stores.grain, backdropWash(stores.tint), strip, turned = edge == TabEdge.Bottom)
        }
        Box(
            Modifier
                .absolutePadding(left = left, right = right, bottom = band)
                .fillMaxSize()
                .background(if (previewing) Color.Transparent else c.background)
        ) {
            AnimatedContent(
                tool,
                transitionSpec = {
                    (fadeIn(tween(SWAP_MS, easing = EaseOut)) + slideInVertically(tween(SWAP_MS, easing = EaseOut)) { travel })
                        .togetherWith(fadeOut(tween(SWAP_MS, easing = EaseOut)) + slideOutVertically(tween(SWAP_MS, easing = EaseOut)) { travel })
                },
                contentKey = { it?.id },
                label = "page",
            ) { page ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (page == null) return@graphicsLayer
                            // the system back-to-home preview: 90 % scale, a sideways shift of width / 20 - 8 dp
                            val progress = back.value
                            val away = if (backEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                            scaleX = 1f - 0.1f * progress
                            scaleY = 1f - 0.1f * progress
                            transformOrigin = when (edge) {
                                TabEdge.Bottom -> TransformOrigin(0.5f, 1f)
                                TabEdge.Left -> TransformOrigin(0f, 0.5f)
                                TabEdge.Right -> TransformOrigin(1f, 0.5f)
                            }
                            translationX = away * (size.width / 20f - 8.dp.toPx()) * progress
                            shape = RoundedCornerShape(previewCorner * progress)
                            clip = progress > 0f
                        }
                        .background(c.background)
                        .statusBarsPadding()
                        .padding(bottom = if (vertical) navigation else 0.dp)
                ) {
                    if (page == null) ToolLattice(lattice, open, onFavour = { favourOpen = true }) else ToolPage(page)
                }
            }
            if (!vertical && !previewing) Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(fadeHeight)
                    .background(Brush.verticalGradient(listOf(c.background.copy(alpha = 0f), c.background)))
            )
        }

        key(edge) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val name = tool?.name ?: "Tools"
            val measured = with(density) { measurer.measure(name, pillName, maxLines = 1).size.width.toDp() }
            // iOS FanItemView.nameRoom: the bar's margins, 53 pt per sibling button, the item's own padding
            val siblings = if (tool == null) 2 else 0
            val nameRoom = if (vertical) nameLimit else minOf(nameLimit, maxOf(60.dp, maxWidth - 146.dp - 53.dp * siblings))
            val nameLength = measured.coerceAtMost(nameRoom)
            // tab runs along the page edge, `depth` out of it towards the screen edge
            val tabEnd = (if (vertical) maxHeight else maxWidth) - tabGap
            val content = if (vertical) 10.dp + 28.dp + 8.dp + nameLength + 10.dp else 20.dp + 28.dp + 6.dp + nameLength + 20.dp
            val tabStart by animateDpAsState(tabEnd - content - tabOverhang * 2, tween(SWAP_MS, easing = EaseOut), label = "tab")
            val depth = when (edge) {
                TabEdge.Bottom -> band
                TabEdge.Left -> left
                TabEdge.Right -> right
            } - tabGap
            val pageEdge = when (edge) {
                TabEdge.Bottom -> maxHeight - band
                TabEdge.Left -> left
                TabEdge.Right -> maxWidth - right
            }
            val view = LocalView.current
            var displayCorner by remember { mutableFloatStateOf(0f) }
            val cornerPosition = if (edge == TabEdge.Left) RoundedCorner.POSITION_BOTTOM_LEFT else RoundedCorner.POSITION_BOTTOM_RIGHT

            // insets reach the view after the first layout and change with rotation and window size
            Canvas(Modifier.fillMaxSize().onGloballyPositioned { displayCorner = view.cornerRadius(cornerPosition) }) {
                val rule = c.foreground.copy(alpha = 0.22f)
                val stroke = ruleWidth.toPx()
                val sideEnd = if (vertical) size.height else pageEdge.toPx()
                if (!vertical) drawLine(rule, Offset(0f, pageEdge.toPx()), Offset(size.width, pageEdge.toPx()), stroke)
                if (left > 0.dp) drawLine(rule, Offset(left.toPx(), 0f), Offset(left.toPx(), sideEnd), stroke)
                if (right > 0.dp) drawLine(rule, Offset(size.width - right.toPx(), 0f), Offset(size.width - right.toPx(), sideEnd), stroke)

                val start = tabStart.toPx()
                val end = tabEnd.toPx()
                val d = depth.toPx()
                val corner = maxOf(tabCorner.toPx(), displayCorner - tabGap.toPx()).coerceAtMost(minOf(d, (end - start) / 2))
                val (outline, fill) = tabPaths(edge, start, end, pageEdge.toPx(), d, tabFillet.toPx(), corner)
                drawPath(fill, c.background)
                drawPath(outline, rule, style = Stroke(stroke))
            }

            Box(
                when (edge) {
                    TabEdge.Bottom -> Modifier.offset(x = tabStart, y = pageEdge).size(tabEnd - tabStart, depth)
                    TabEdge.Left -> Modifier.offset(x = pageEdge - depth, y = tabStart).size(depth, tabEnd - tabStart)
                    TabEdge.Right -> Modifier.offset(x = pageEdge, y = tabStart).size(depth, tabEnd - tabStart)
                }
                    .clickable(interactionSource = null, indication = null, role = Role.Button) { fanOpen = true }
                    .padding(start = if (vertical) 0.dp else tabOverhang, top = if (vertical) tabOverhang else 0.dp),
                contentAlignment = if (vertical) Alignment.TopCenter else Alignment.CenterStart,
            ) {
                Crossfade(tool, animationSpec = tween(SWAP_MS), label = "pill") { shown ->
                    val icon = shown?.icon ?: Icons.Outlined.GridView
                    val label = shown?.name ?: "Tools"
                    if (vertical) VerticalPill(icon, label, if (barOnLeft) 90f else -90f) else Pill(icon, label, nameRoom)
                }
            }

            // iOS shellBarItems: Settings beside the pill on the home; search sits with it here
            if (tool == null) {
                val buttons = listOf(
                    Triple(Icons.Outlined.Settings, "Settings") { settingsOpen = true },
                    Triple(Icons.Outlined.Search, "Search") { searchOpen = true },
                )
                buttons.forEachIndexed { index, (icon, label, onClick) ->
                    val along = tabStart - tabFillet - stripButtonGap - (stripButton + stripButtonGap) * index - stripButton
                    val across = when (edge) {
                        TabEdge.Bottom -> pageEdge + (bandRow - stripButton) / 2
                        TabEdge.Left -> pageEdge - depth / 2 - stripButton / 2
                        TabEdge.Right -> pageEdge + depth / 2 - stripButton / 2
                    }
                    StripButton(icon, label, if (vertical) Modifier.offset(x = across, y = along) else Modifier.offset(x = along, y = across), onClick)
                }
            }

            if (fanOpen) {
                val item = with(density) {
                    if (vertical) {
                        val middle = (if (barOnLeft) pageEdge - depth / 2 else pageEdge + depth / 2).toPx()
                        val half = verticalPillWidth.toPx() / 2
                        Rect(middle - half, (tabStart + tabOverhang).toPx(), middle + half, (tabEnd - tabOverhang).toPx())
                    } else {
                        val middle = (pageEdge + depth / 2).toPx()
                        val half = pillHeight.toPx() / 2
                        Rect((tabStart + tabOverhang).toPx(), middle - half, (tabEnd - tabOverhang).toPx(), middle + half)
                    }
                }
                FanMenu(item, vertical, tool, open) { fanOpen = false }
            }
        }

        SheetVisibility(settingsOpen) { SettingsPane { settingsOpen = false } }
        SheetVisibility(searchOpen) { SearchOverlay(open) { searchOpen = false } }
        SheetVisibility(favourOpen) { stores.favourDialogue?.let { FavourSheet(it) { favourOpen = false } } }
        if (whatsNewDue) WhatsNewSheet { whatsNewDue = false }
        if (splashing) LaunchSplash { splashing = false }
    }
}

private enum class TabEdge { Left, Right, Bottom }

/** The tab's outline and fill: concave fillets where it leaves the page edge, convex corners at its far end. */
private fun tabPaths(edge: TabEdge, start: Float, end: Float, pageEdge: Float, depth: Float, fillet: Float, corner: Float): Pair<Path, Path> {
    val length = end - start
    // built with x running out of the page and y along it, then placed on `edge`
    val outline = Path().apply {
        moveTo(0f, -fillet)
        arcTo(Rect(Offset(fillet, -fillet), fillet), 180f, -90f, false)
        arcTo(Rect(Offset(depth - corner, corner), corner), -90f, 90f, false)
        arcTo(Rect(Offset(depth - corner, length - corner), corner), 0f, 90f, false)
        arcTo(Rect(Offset(fillet, length + fillet), fillet), -90f, -90f, false)
    }
    val fill = Path().apply {
        addPath(outline)
        // one pixel past the page edge hides the seam
        lineTo(-1f, length + fillet)
        lineTo(-1f, -fillet)
        close()
    }
    val place = Matrix().apply {
        val across = edge == TabEdge.Bottom
        values[Matrix.ScaleX] = if (across) 0f else if (edge == TabEdge.Left) -1f else 1f
        values[Matrix.SkewY] = if (across) 1f else 0f
        values[Matrix.SkewX] = if (across) 1f else 0f
        values[Matrix.ScaleY] = if (across) 0f else 1f
        values[Matrix.TranslateX] = if (across) start else pageEdge
        values[Matrix.TranslateY] = if (across) pageEdge else start
    }
    outline.transform(place)
    fill.transform(place)
    return outline to fill
}

/** The window's corner radius at `position` in px, so the tab corner runs concentric to it; 0 away from a display corner. */
private fun View.cornerRadius(position: Int): Float {
    // ponytail: API 30 has no RoundedCorner, the tab keeps tabCorner there
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0f
    return rootWindowInsets?.getRoundedCorner(position)?.radius?.toFloat() ?: 0f
}

/** The 72 pt side-bar item: symbol on top, the name turned so its baseline faces the screen edge. */
@Composable
private fun VerticalPill(icon: ImageVector, name: String, turn: Float) {
    val c = LocalDelphiColours.current
    Column(
        Modifier.width(verticalPillWidth).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(20.dp))
        }
        Text(
            name,
            style = pillName,
            color = c.foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.layout { measurable, _ ->
                val text = measurable.measure(Constraints(maxWidth = nameLimit.roundToPx()))
                layout(text.height, text.width) {
                    text.placeWithLayer((text.height - text.width) / 2, (text.width - text.height) / 2) { rotationZ = turn }
                }
            },
        )
    }
}

@Composable
private fun Pill(icon: ImageVector, name: String, nameRoom: Dp) {
    val c = LocalDelphiColours.current
    Row(
        Modifier.height(pillHeight).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c.primary, modifier = Modifier.size(20.dp))
        }
        Text(name, style = pillName, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = nameRoom))
    }
}

@Composable
private fun ToolPage(tool: Tool) {
    CompositionLocalProvider(LocalCanonPatternSymbol provides tool.icon) {
        when (tool.id) {
            "image-compressor" -> ImageCompressor()
            "timecode-calc" -> TimecodeCalc()
            canonGallery.id -> if (galleryPages.isEmpty()) EmptyPage(tool) else GalleryPages()
            else -> EmptyPage(tool)
        }
    }
}

// ponytail: iOS hangs the sticker as the CanonSplit end piece; CanonSplit has none yet, so only placeholder pages show it
@Composable
private fun EmptyPage(tool: Tool) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tool.icon, contentDescription = null, tint = LocalDelphiColours.current.border, modifier = Modifier.padding(top = 96.dp).size(96.dp))
        ToolSticker(tool, Modifier.fillMaxWidth())
    }
}

/** A square chip on the strip: card, hairline, symbol. */
@Composable
private fun StripButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    Box(
        modifier
            .size(stripButton)
            .canonClickable(onClick = onClick)
            .background(c.card)
            .border(CanonMetrics.hairline, c.border),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = c.foreground, modifier = Modifier.size(20.dp))
    }
}

/** A page over the shell that rises in and falls away. */
@Composable
private fun SheetVisibility(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(250, easing = EaseOut)) { it / 10 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { it / 10 },
    ) { content() }
}

@Composable
private fun GalleryPages() {
    val pager = rememberPagerState { galleryPages.size }
    HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
        val (title, content) = galleryPages[page]
        Column(Modifier.fillMaxSize()) {
            CanonHeading("${page + 1}/${galleryPages.size}  $title")
            Box(Modifier.weight(1f)) { content() }
        }
    }
}
