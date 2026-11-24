package com.delphi.delphitools.tools.typography

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.CanonSwitchGrid
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.creases
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import com.delphi.delphitools.tools.typography.LargeTypeEngine.CharClass
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Text and background for the display, with the colours that set digits and symbols apart. */
private class LargeTypeTheme(val name: String, val text: Color, val background: Color, val digit: Color, val special: Color) {
    fun colour(kind: CharClass, colours: Boolean) = when {
        kind == CharClass.Space -> text.copy(alpha = 0.35f)
        kind == CharClass.Digit && colours -> digit
        kind == CharClass.Special && colours -> special
        else -> text
    }
}

private fun rgb(red: Float, green: Float, blue: Float) = Color(red, green, blue)

/** The app's own colours first, then fixed pairs that read the same in light and dark. */
@Composable
private fun largeTypeThemes(): List<LargeTypeTheme> {
    val c = LocalDelphiColours.current
    val dark = isSystemInDarkTheme()
    return listOf(
        LargeTypeTheme(
            "Default", c.foreground, c.background,
            if (dark) rgb(0.22f, 0.74f, 0.97f) else rgb(0.01f, 0.52f, 0.78f),
            if (dark) rgb(0.98f, 0.75f, 0.14f) else rgb(0.85f, 0.47f, 0.02f),
        ),
        LargeTypeTheme("Paper", rgb(0.07f, 0.07f, 0.07f), Color.White, rgb(0.01f, 0.45f, 0.72f), rgb(0.8f, 0.36f, 0f)),
        LargeTypeTheme("Night", rgb(0.96f, 0.96f, 0.96f), Color.Black, rgb(0.22f, 0.74f, 0.97f), rgb(0.98f, 0.75f, 0.14f)),
        LargeTypeTheme("Sign", rgb(1f, 0.82f, 0.04f), Color.Black, Color.White, rgb(1f, 0.42f, 0.1f)),
        LargeTypeTheme("Terminal", rgb(0.24f, 1f, 0.42f), rgb(0.02f, 0.06f, 0.03f), rgb(0.45f, 0.9f, 1f), rgb(1f, 0.78f, 0.25f)),
    )
}

/** `size` null fits the largest size that shows every cell without scrolling, or the marquee's line to the height. */
internal data class LargeTypeSettings(
    val text: String,
    val size: Double?,
    val positions: Boolean,
    val colours: Boolean,
    val theme: Int,
    val marquee: Boolean,
    val speed: Double,
)

/** dt-ios LargeTypeViewController's state. */
class LargeTypeModel : ViewModel() {
    var text by mutableStateOf("")
        private set

    /** The text the display shows; typing settles first. */
    var shown by mutableStateOf("")
        private set
    var theme by mutableIntStateOf(0)
    var marquee by mutableStateOf(false)
    var speed by mutableDoubleStateOf(1.0)
    var positions by mutableStateOf(true)
    var colours by mutableStateOf(true)
    var maximum by mutableStateOf(true)
    var size by mutableDoubleStateOf(LargeTypeEngine.DEFAULT_SIZE)
        private set
    var fullScreen by mutableStateOf(false)

    private var typing: Job? = null

    fun type(next: String) {
        text = next
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            shown = next
        }
    }

    /** A drag turns maximum off. */
    fun drag(next: Double) {
        maximum = false
        size = next
    }

    fun fitted(next: Double) {
        if (maximum) size = next
    }

    internal val settings
        get() = LargeTypeSettings(shown, if (maximum) null else size, positions, colours, theme, marquee, speed)
}

@Composable
fun LargeType(model: LargeTypeModel = viewModel()) {
    val themes = largeTypeThemes()
    val theme = themes[model.theme]
    val settings = model.settings

    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(1f)) {
        LargeTypeDisplay(settings, theme, Modifier.fillMaxSize(), top = LocalCanonCanvas.current.top, onFit = model::fitted)
    }

    val input = CanonSection.of {
        CanonRows {
            CanonTextCell(
                model.text,
                model::type,
                Modifier.fillMaxWidth().semantics { contentDescription = "Text" },
                placeholder = "Type or paste",
                width = 200.dp,
                textStyle = TextStyle(fontFamily = DelphiMono, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
            )
            CanonStatusRow("${LargeTypeEngine.characters(model.text).size} characters", CanonTone.Muted)
        }
    }

    val controls = CanonSection.of {
        CanonRows {
            CanonHeading("Theme")
            ThemePicker(themes, model.theme) { model.theme = it }
            CanonHeading("Display")
            CanonRow("Marquee") { CanonSwitch(model.marquee) { model.marquee = it } }
            if (model.marquee) {
                CanonRow("Speed", flush = true) {
                    CanonSlider(
                        model.speed.toFloat(),
                        { model.speed = it.toDouble() },
                        range = 0.25f..4f,
                        step = 0.25f,
                        readout = PxToRemEngine.format(model.speed) + "×",
                    )
                }
            }
            CanonSwitchGrid(listOf("Positions" to model.positions, "Colours" to model.colours), columns = 2) { index, on ->
                if (index == 0) model.positions = on else model.colours = on
            }
            if (model.colours) Legend(theme)
            CanonHeading("Size")
            CanonRow("Maximum") { CanonSwitch(model.maximum) { model.maximum = it } }
            CanonSlider(
                model.size.toFloat(),
                { model.drag(it.toDouble()) },
                Modifier.semantics { contentDescription = "Size" },
                range = LargeTypeEngine.MIN_SIZE.toFloat()..LargeTypeEngine.MAX_SIZE.toFloat(),
                step = LargeTypeEngine.SIZE_STEP.toFloat(),
                readout = "${model.size.toInt()} pt",
            )
            CanonButton(
                "Full Screen",
                kind = CanonButtonKind.Primary,
                enabled = model.shown.isNotEmpty(),
                icon = Icons.Outlined.OpenInFull,
                oversized = true,
            ) { model.fullScreen = true }
        }
    }

    CanonSplit(sidebar = listOf(canvas), main = listOf(input, controls), halves = true)

    if (model.fullScreen) FullScreen(settings, theme) { model.fullScreen = false }
}

/** The themes as swatches set in their own colours; the chosen one is ringed. */
@Composable
private fun ThemePicker(themes: List<LargeTypeTheme>, selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalDelphiColours.current
    CanonGrid(columns = themes.size) {
        themes.forEachIndexed { index, theme ->
            Column(
                Modifier
                    .background(theme.background)
                    .then(if (index == selected) Modifier.border(3.dp, c.primary) else Modifier)
                    .canonClickable { if (index != selected) onSelect(index) }
                    .semantics {
                        contentDescription = theme.name
                        this.selected = index == selected
                    }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = theme.text)) { append("Aa") }
                        withStyle(SpanStyle(color = theme.digit)) { append("1") }
                    },
                    style = TextStyle(fontFamily = DelphiMono, fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                )
                Text(theme.name, style = CanonText.captionSemibold, color = theme.text.copy(alpha = 0.75f), maxLines = 1, softWrap = false)
            }
        }
    }
}

/** A sample glyph of each class in the theme's colour, over the class name, on the theme's background. */
@Composable
private fun Legend(theme: LargeTypeTheme) {
    val items = listOf(
        Triple("A", CharClass.Letter, "Letters"),
        Triple("1", CharClass.Digit, "Digits"),
        Triple("#", CharClass.Special, "Symbols"),
        Triple(LargeTypeEngine.SPACE_GLYPH, CharClass.Space, "Spaces"),
    )
    CanonGrid(columns = 4) {
        items.forEach { (glyph, kind, name) ->
            Column(
                Modifier
                    .background(theme.background)
                    .semantics { contentDescription = name }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // faint space lost here
                val ink = if (kind == CharClass.Space) theme.text.copy(alpha = 0.6f) else theme.colour(kind, true)
                Text(glyph, style = TextStyle(fontFamily = DelphiMono, fontSize = 22.sp, fontWeight = FontWeight.SemiBold), color = ink, maxLines = 1)
                Text(name, style = CanonText.captionSemibold, color = theme.text.copy(alpha = 0.7f), maxLines = 1, softWrap = false)
            }
        }
    }
}

private fun glyphStyle(size: Float) = TextStyle(fontFamily = DelphiMono, fontWeight = FontWeight.Normal, fontSize = size.sp, textAlign = TextAlign.Center)

/**
 * The text a character per cell, as large as the settings ask, in rows that wrap. Alternate cells are shaded and each
 * can carry its position. No cell crosses `exclusion`, a vertical band in dp. As a marquee the text is one line, moving right to left.
 */
@Composable
private fun LargeTypeDisplay(
    settings: LargeTypeSettings,
    theme: LargeTypeTheme,
    modifier: Modifier = Modifier,
    top: Dp = 0.dp,
    exclusion: ClosedFloatingPointRange<Float>? = null,
    onFit: (Double) -> Unit = {},
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val glyphs = remember(settings.text) { LargeTypeEngine.glyphs(settings.text) }
    // sizes are dp; font sizes divide out the user's font scale
    val fontScale = density.fontScale
    val widest = remember(glyphs, density) {
        val reference = glyphStyle(100f / fontScale)
        glyphs.toSet().fold(LargeTypeEngine.DEFAULT_ASPECT) { widest, glyph ->
            val shown = if (LargeTypeEngine.isSpace(glyph)) LargeTypeEngine.SPACE_GLYPH else glyph
            val advance = with(density) { measurer.measure(shown, reference).size.width.toDp().value } / 100.0
            max(widest, if (advance > 0) advance else LargeTypeEngine.DEFAULT_ASPECT)
        }
    }
    val fit by rememberUpdatedState(onFit)
    val padding = CanonMetrics.panelPadding.value.toDouble()

    BoxWithConstraints(
        modifier
            .background(theme.background)
            .clipToBounds()
            .semantics { contentDescription = settings.text },
    ) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value - top.value.toDouble()
        if (glyphs.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(top = top),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                Icon(Icons.Outlined.FormatSize, contentDescription = null, tint = theme.text.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
                Text("Text will appear here", style = CanonText.caption, color = theme.text.copy(alpha = 0.6f))
            }
            return@BoxWithConstraints
        }

        if (settings.marquee) {
            val band = max(0.0, height - padding * 2)
            // upright: three characters minimum
            val fitted = floor(min(band / LargeTypeEngine.LINE_HEIGHT, width / (3 * LargeTypeEngine.ADVANCE_SHARE)))
            val size = max(1.0, min(settings.size ?: fitted, fitted))
            LaunchedEffect(size) { fit(size) }
            Marquee(glyphs, size.toFloat() / fontScale, theme, settings.colours, settings.speed, Modifier.padding(top = top + CanonMetrics.panelPadding).fillMaxWidth().height(band.dp))
            return@BoxWithConstraints
        }

        val columns = run {
            val lower = padding
            val upper = width - padding
            if (upper <= lower) return@run emptyList()
            val band = exclusion
            if (band == null || band.start <= lower || band.endInclusive >= upper) listOf(lower..upper)
            else listOf(lower..(band.start - padding), (band.endInclusive + padding)..upper)
        }
        val inner = height - padding * 2
        val size = settings.size ?: LargeTypeEngine.fit(glyphs, widest, columns.map { it.endInclusive - it.start }, inner, settings.positions)
        LaunchedEffect(size) { fit(size) }
        val frames = LargeTypeEngine.frames(glyphs, size, widest, columns, settings.positions)
        val used = frames.maxOfOrNull { it.maxY } ?: 0.0
        val lead = padding + max(0.0, (inner - used) / 2)
        val cell = LargeTypeEngine.cell(size, widest, settings.positions)
        // one item per row band keeps every node far under Compose's size limit
        val bands = frames.indices.groupBy { frames[it].y }.toList()
        val glyphSize = size.toFloat() / fontScale
        LazyColumn(Modifier.fillMaxSize().padding(top = top)) {
            item { Spacer(Modifier.height(lead.dp)) }
            items(bands.size) { band ->
                Box(Modifier.fillMaxWidth().height(cell.height.dp)) {
                    // ponytail: cells jump to a new layout; iOS springs each from its old frame
                    bands[band].second.forEach { index ->
                        GlyphCell(glyphs[index], index + 1, frames[index], glyphSize, settings, theme)
                    }
                }
            }
            item { Spacer(Modifier.height(padding.dp)) }
        }
    }
}

@Composable
private fun GlyphCell(glyph: String, position: Int, frame: LargeTypeEngine.Frame, size: Float, settings: LargeTypeSettings, theme: LargeTypeTheme) {
    val kind = LargeTypeEngine.charClass(glyph)
    val label = if (settings.positions) LargeTypeEngine.POSITION_HEIGHT else 0.0
    val half = (LargeTypeEngine.CELL_PADDING / 2).dp
    Box(
        Modifier
            .offset(frame.x.dp, 0.dp)
            .size(frame.width.dp, frame.height.dp)
            .background(if (position % 2 == 0) Color.Transparent else theme.text.copy(alpha = 0.07f)),
    ) {
        Box(
            Modifier
                .padding(horizontal = half)
                .offset(y = half)
                .fillMaxWidth()
                .height((frame.height - label - LargeTypeEngine.CELL_PADDING).dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (kind == CharClass.Space) LargeTypeEngine.SPACE_GLYPH else glyph,
                style = glyphStyle(size),
                color = theme.colour(kind, settings.colours),
                maxLines = 1,
                softWrap = false,
            )
        }
        if (settings.positions) {
            Text(
                "$position",
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = half)
                    .height(label.dp),
                style = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp, textAlign = TextAlign.Center),
                color = theme.text.copy(alpha = 0.5f),
                maxLines = 1,
            )
        }
    }
}

/** One line of the text moving right to left in a loop, as on an LED sign; nothing is laid out per frame. */
@Composable
private fun Marquee(glyphs: List<String>, size: Float, theme: LargeTypeTheme, colours: Boolean, speed: Double, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = glyphStyle(size)
    val advance = remember(size, density) { measurer.measure("M", style).size.width.toDouble() }
    val layouts = remember(glyphs, size, theme, colours, density) {
        glyphs.distinct().associateWith { glyph ->
            val kind = LargeTypeEngine.charClass(glyph)
            if (kind == CharClass.Space) null else measurer.measure(glyph, style.copy(color = theme.colour(kind, colours)))
        }
    }
    val pace by rememberUpdatedState(LargeTypeEngine.MARQUEE_PACE * speed * advance)
    // distance moved in px: index g of the endless repeat sits at width + g × advance − scrolled
    var scrolled by remember { mutableDoubleStateOf(Double.NaN) }
    val place = remember { object { var glyphs: List<String>? = null; var advance = 0.0 } }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                // stalls do not jump
                val elapsed = if (last == 0L) 0.0 else min(0.1, (now - last) / 1e9)
                last = now
                if (!scrolled.isNaN()) scrolled += pace * elapsed
            }
        }
    }
    Canvas(modifier.clipToBounds()) {
        val width = this.size.width.toDouble()
        if (place.glyphs != glyphs || scrolled.isNaN()) {
            // new text enters leading
            scrolled = width - CanonMetrics.panelPadding.toPx()
        } else if (place.advance != advance && place.advance > 0) {
            scrolled = scrolled / place.advance * advance
        }
        place.glyphs = glyphs
        place.advance = advance
        val visible = LargeTypeEngine.visible(scrolled, width, advance) ?: return@Canvas
        val period = glyphs.size + LargeTypeEngine.MARQUEE_GAP
        for (g in visible) {
            val layout = glyphs.getOrNull(g % period)?.let { layouts[it] } ?: continue
            val x = width + g * advance - scrolled + (advance - layout.size.width) / 2
            translate(x.toFloat(), (this.size.height - layout.size.height) / 2) { drawText(layout) }
        }
    }
}

/** The vertical fold, in dp from the window's leading edge, on a wide unfolded screen; the rows keep off it. */
@Composable
private fun rememberVerticalCrease(): ClosedFloatingPointRange<Float>? {
    val activity = LocalActivity.current ?: return null
    val density = LocalDensity.current
    val flow = remember(activity) { WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity) }
    val info by flow.collectAsState(null)
    val fold = info?.displayFeatures?.firstNotNullOfOrNull { it as? FoldingFeature } ?: return null
    val decor = activity.window.decorView
    val hinge = fold.bounds.let { Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
    val width = decor.width.toFloat()
    // same guards as canonsplit
    if (with(density) { width.toDp() } < 600.dp) return null
    val crease = creases(hinge, width, decor.height.toFloat()).first ?: return null
    return with(density) { crease.left.toDp().value..crease.right.toDp().value }
}

/** The display alone, edge to edge, bars hidden, screen kept awake. A tap anywhere closes it. */
@Composable
private fun FullScreen(settings: LargeTypeSettings, theme: LargeTypeTheme, onClose: () -> Unit) {
    val crease = rememberVerticalCrease()
    Dialog(onClose, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            view.keepScreenOn = true
            if (window != null) {
                window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
                WindowCompat.getInsetsController(window, view).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose { view.keepScreenOn = false }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(theme.background)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        ) {
            LargeTypeDisplay(
                settings,
                theme,
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout),
                exclusion = crease,
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(top = 4.dp, end = 8.dp)
                    .size(44.dp)
                    .canonClickable(onClick = onClose)
                    .semantics {
                        contentDescription = "Close"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, contentDescription = null, tint = theme.text.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            }
        }
    }
}
