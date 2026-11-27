package com.delphi.delphitools.shell

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.activity.BackEventCompat
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHost
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.LocalCanonPatternSymbol
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.gallery.galleryPages
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.tools.LocalToolUndo
import com.delphi.delphitools.tools.ToolUndo
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

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

private val barButton = 44.dp
private val barButtonGap = 8.dp
private val barMargin = 16.dp
private val barBlur = 14.dp
private const val BAR_GLASS = 0.55f

/** The app frame: the page inset in the strip, the tab naming it, and the fan behind the tab. */
@Composable
fun Shell(startTool: String? = null) {
    CompositionLocalProvider(LocalShellStores provides rememberShellStores()) { ShellFrame(startTool) }
}

@Composable
private fun ShellFrame(startTool: String?) {
    val c = LocalDelphiColours.current
    val stores = LocalShellStores.current
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var favourOpen by remember { mutableStateOf(false) }
    var splashing by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    var whatsNewDue by remember { mutableStateOf(false) }
    LaunchedEffect(splashing) { if (!splashing && WhatsNew.shouldShow(context)) whatsNewDue = true }
    var toolId by rememberSaveable { mutableStateOf(startTool) }
    val tool = toolId?.let(::toolWithId)
    // rebuilds an open page
    var generation by rememberSaveable { mutableIntStateOf(0) }
    val undo = remember { ToolUndo() }
    val run = WorkflowRun
    val haptics = LocalHapticFeedback.current
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
            run.visit(null)
            scope.launch {
                delay(SWAP_MS.toLong())
                back.snapTo(0f)
            }
        } catch (cancelled: CancellationException) {
            scope.launch { back.animateTo(0f, spring()) }
            throw cancelled
        }
    }
    val open: (Tool) -> Unit = {
        stores.recordUsage(it.id)
        scope.launch { back.snapTo(0f) }
        toolId = it.id
        // before the page composes
        run.visit(it)
    }
    DisposableEffect(context) {
        val app = context.applicationContext
        run.attach(
            File(app.cacheDir, "workflow"),
            open = {
                generation++
                open(it)
            },
            share = { files -> context.startActivity(FileExport.chooser(context, files.map { it.file }, files.first().mimeType)) },
        )
        CanonHost.capturesFiles = { run.capturesFiles }
        CanonHost.pendingFiles = { types, multiple ->
            run.pending(types, multiple).map { FileProvider.getUriForFile(app, "${app.packageName}.files", it) }
        }
        onDispose { run.detach() }
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
        val ticket = rememberWorkflowTicket()
        // ponytail: the status bar glyphs ignore the backdrop (iOS: wantsLightGlyphs)
        val drawStrip: DrawScope.() -> Unit = {
            val strip = when (edge) {
                TabEdge.Bottom -> Rect(0f, size.height - band.toPx(), size.width, size.height)
                TabEdge.Left -> Rect(0f, 0f, left.toPx(), size.height)
                TabEdge.Right -> Rect(size.width - right.toPx(), 0f, size.width, size.height)
            }
            drawBackdrop(stores.backdrop.base(c, darkTheme), tape, stores.grain, backdropWash(stores.tint), strip, turned = edge == TabEdge.Bottom)
            drawTicket(ticket, edge, strip, if (vertical) 0f else navigation.toPx(), c)
        }
        Canvas(Modifier.fillMaxSize(), drawStrip)
        Box(
            Modifier
                .absolutePadding(left = left, right = right, bottom = band)
                .fillMaxSize()
                .background(if (previewing) Color.Transparent else c.background)
        ) {
            val pageFrame = Modifier.fillMaxSize().background(c.background).statusBarsPadding().padding(bottom = if (vertical) navigation else 0.dp)
            if (previewing && tool != null) Box(pageFrame) { ToolLattice(lattice, open, onFavour = { favourOpen = true }) }
            AnimatedContent(
                tool to generation,
                transitionSpec = {
                    // the home is already drawn behind the preview
                    if (back.value > 0f) (EnterTransition.None togetherWith fadeOut(tween(SWAP_MS, easing = EaseOut))).apply { targetContentZIndex = -1f }
                    else (fadeIn(tween(SWAP_MS, easing = EaseOut)) + slideInVertically(tween(SWAP_MS, easing = EaseOut)) { travel })
                        .togetherWith(fadeOut(tween(SWAP_MS, easing = EaseOut)) + slideOutVertically(tween(SWAP_MS, easing = EaseOut)) { travel })
                },
                contentKey = { (shown, fresh) -> shown?.id to fresh },
                label = "page",
            ) { (page, fresh) ->
                Box(
                    Modifier
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
                        .then(pageFrame)
                ) {
                    if (page == null) ToolLattice(lattice, open, onFavour = { favourOpen = true })
                    else ToolPage(page, fresh, undo, isOpen = { toolId == page.id && generation == fresh })
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
            val items = buildList {
                if (tool == null) {
                    add(BarItem(Icons.Outlined.Search, "Search") { searchOpen = true })
                    add(BarItem(Icons.Outlined.Settings, "Settings") { settingsOpen = true })
                    return@buildList
                }
                if (!run.isOnStep && tool.id in toolPages) {
                    val starred = tool.id in stores.favourites
                    add(
                        BarItem(
                            if (starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            if (starred) "Remove from favourites" else "Add to favourites",
                            tint = if (starred) starYellow else null,
                        ) { stores.toggleFavourite(tool.id) }
                    )
                }
                undo.undo?.let { add(BarItem(Icons.AutoMirrored.Outlined.Undo, "Undo", enabled = undo.canUndo, onClick = it)) }
                if (undo.canRedo) undo.redo?.let { add(BarItem(Icons.AutoMirrored.Outlined.Redo, "Redo", onClick = it)) }
                if (run.isOnStep) {
                    add(
                        BarItem(
                            if (run.isLast) Icons.Outlined.SaveAlt else Icons.AutoMirrored.Outlined.ArrowForward,
                            if (run.isLast) "Save" else "Next",
                            enabled = !run.isLanding && (run.capturesFiles || run.canAdvance),
                            prominent = true,
                        ) { if (!run.next()) haptics.performHapticFeedback(HapticFeedbackType.Reject) }
                    )
                }
            }
            val barStart = (if (vertical) safe.calculateTopPadding() else safe.calculateLeftPadding(LayoutDirection.Ltr)) + barMargin
            // iOS FanItemView.nameRoom: the bar's margins, 53 pt per sibling button, the item's own padding
            val nameRoom = minOf(nameLimit, maxOf(60.dp, (if (vertical) maxHeight - barStart - 110.dp else maxWidth - 146.dp) - 53.dp * items.size))
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
            // ponytail: iOS flies file chips to the pill and bursts symbols out of it at the end; here the pill only stamps
            val stamp = remember { Animatable(1f) }
            var stamped by remember { mutableIntStateOf(run.captureCount) }
            LaunchedEffect(run.captureCount) {
                if (run.captureCount == stamped) return@LaunchedEffect
                stamped = run.captureCount
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                stamp.snapTo(1.14f)
                stamp.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow))
            }
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
                    .graphicsLayer {
                        scaleX = stamp.value
                        scaleY = stamp.value
                    }
                    .padding(start = if (vertical) 0.dp else tabOverhang, top = if (vertical) tabOverhang else 0.dp),
                contentAlignment = if (vertical) Alignment.TopCenter else Alignment.CenterStart,
            ) {
                Crossfade(tool, animationSpec = tween(SWAP_MS), label = "pill") { shown ->
                    val leg = run.tools.indexOfFirst { it.id == shown?.id }.takeIf { it >= 0 }
                    val icon = leg?.let { legIcon(it, it) } ?: shown?.icon ?: Icons.Outlined.GridView
                    val label = shown?.name ?: "Tools"
                    if (vertical) VerticalPill(icon, label, if (barOnLeft) 90f else -90f, nameRoom) else Pill(icon, label, nameRoom)
                }
            }

            val window = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            items.forEachIndexed { index, item ->
                val along = barStart + (barButton + barButtonGap) * index
                val across = when (edge) {
                    TabEdge.Bottom -> pageEdge + (bandRow - barButton) / 2
                    TabEdge.Left -> pageEdge - depth / 2 - barButton / 2
                    TabEdge.Right -> pageEdge + depth / 2 - barButton / 2
                }
                val x = if (vertical) across else along
                val y = if (vertical) along else across
                BarButton(item, Modifier.offset(x, y)) {
                    // strip in window coordinates
                    inset(-x.toPx(), -y.toPx(), x.toPx() + size.width - window.width, y.toPx() + size.height - window.height, drawStrip)
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
        if (run.pendingDiscard != null) DiscardDialog(run::answerDiscard)
        if (whatsNewDue) WhatsNewSheet { whatsNewDue = false }
        if (splashing) LaunchSplash { splashing = false }
    }
}

internal enum class TabEdge { Left, Right, Bottom }

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
private fun VerticalPill(icon: ImageVector, name: String, turn: Float, nameRoom: Dp) {
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
                val text = measurable.measure(Constraints(maxWidth = nameRoom.roundToPx()))
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

/** One ViewModelStore per open tool, kept across a configuration change and cleared when the tool closes. */
internal class ToolStores : ViewModel() {
    private val stores = HashMap<String, ViewModelStore>()

    fun of(id: String) = stores.getOrPut(id) { ViewModelStore() }

    fun clear(id: String) {
        stores.remove(id)?.clear()
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

@Composable
private fun ToolPage(tool: Tool, generation: Int, undo: ToolUndo, isOpen: () -> Boolean) {
    val activity = checkNotNull(LocalViewModelStoreOwner.current)
    val stores: ToolStores = viewModel()
    val key = "${tool.id}#$generation"
    val owner = remember(key, activity) {
        object : ViewModelStoreOwner, HasDefaultViewModelProviderFactory by (activity as HasDefaultViewModelProviderFactory) {
            override val viewModelStore get() = stores.of(key)
        }
    }
    val changingConfigurations = LocalActivity.current
    DisposableEffect(key) {
        onDispose {
            // reopened tool shares store
            if (!isOpen() && changingConfigurations?.isChangingConfigurations != true) stores.clear(key)
        }
    }
    CompositionLocalProvider(LocalCanonPatternSymbol provides tool.icon, LocalViewModelStoreOwner provides owner, LocalToolUndo provides undo) {
        when {
            tool.id == workflowsTool.id -> WorkflowsPage()
            tool.id == canonGallery.id && galleryPages.isNotEmpty() -> GalleryPages()
            else -> toolPages[tool.id]?.invoke() ?: EmptyPage(tool)
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

private class BarItem(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val tint: Color? = null,
    val prominent: Boolean = false,
    val onClick: () -> Unit,
)

/** A circle on the strip: the strip behind it blurred under a wash of the card colour, a rule, the symbol. */
@Composable
private fun BarButton(item: BarItem, modifier: Modifier, strip: DrawScope.() -> Unit) {
    val c = LocalDelphiColours.current
    val ink = if (item.prominent) c.primary else item.tint ?: c.foreground
    Box(
        modifier
            .size(barButton)
            .canonClickable(enabled = item.enabled, onClick = item.onClick)
            .clip(CircleShape)
            .border(ruleWidth, c.foreground.copy(alpha = if (item.prominent) 1f else 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // ponytail: Modifier.blur draws nothing extra before API 31; the wash alone backs the button there
        Canvas(Modifier.matchParentSize().blur(barBlur), strip)
        Box(Modifier.matchParentSize().background(if (item.prominent) c.background else c.card.copy(alpha = BAR_GLASS)))
        Icon(item.icon, contentDescription = item.label, tint = ink.copy(alpha = if (item.enabled) 1f else 0.3f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DiscardDialog(answer: (Boolean) -> Unit) {
    val c = LocalDelphiColours.current
    Dialog(onDismissRequest = { answer(false) }) {
        Column(Modifier.width(320.dp).background(c.border).padding(CanonMetrics.frame), verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
            CanonHeading("Discard captures?")
            CanonGrid {
                CanonButton("Cancel", Modifier.weight(1f).fillMaxHeight()) { answer(false) }
                CanonButton("Discard", Modifier.weight(1f).fillMaxHeight(), kind = CanonButtonKind.Primary) { answer(true) }
            }
        }
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
