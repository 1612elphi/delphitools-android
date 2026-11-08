package com.delphi.delphitools.shell

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiColours
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** dt-ios BackdropStyle: a base colour per appearance (null: the theme's accent background) and a tape strip. */
@Immutable
class BackdropStyle(
    val id: String,
    val label: String,
    private val light: Color?,
    private val dark: Color?,
    // artwork ignores appearance; null follows it
    val isDark: Boolean?,
    @param:DrawableRes val tape: Int?,
) {
    fun base(c: DelphiColours, darkTheme: Boolean): Color = (if (darkTheme) dark else light) ?: c.accentBg
}

private fun tape(id: String, label: String, base: Color, isDark: Boolean, @DrawableRes art: Int) = BackdropStyle(id, label, base, base, isDark, art)

// dt-ios BackdropStore paper and sage, then Resources/backdrops.json in its order; the tapes are 84 by 951 pt strips
val backdropStyles: List<BackdropStyle> = listOf(
    BackdropStyle("paper", "Paper", null, null, null, null),
    BackdropStyle("sage", "Sage", Color(0.79f, 0.85f, 0.74f), Color(0.09f, 0.21f, 0.12f), null, null),
    tape("benedict", "Benedict", Color(0.406f, 0.51f, 0.35f), true, R.drawable.backdrop_benedict),
    tape("tilcayo", "Tilcayo", Color(0.46f, 0.496f, 0.281f), true, R.drawable.backdrop_tilcayo),
    tape("digital", "Digital", Color(0.278f, 0.234f, 0.201f), true, R.drawable.backdrop_digital),
    tape("flush", "Flush", Color(0.909f, 0.522f, 0.444f), false, R.drawable.backdrop_flush),
    tape("scribble", "Scribble", Color(0.836f, 0.732f, 0.625f), false, R.drawable.backdrop_scribble),
    tape("lakewood", "Lakewood", Color(0.395f, 0.569f, 0.533f), false, R.drawable.backdrop_lakewood),
    tape("barman", "Barman", Color(0.287f, 0.274f, 0.511f), true, R.drawable.backdrop_barman),
    tape("good-sandwich", "Good Sandwich", Color(0.919f, 0.522f, 0.278f), false, R.drawable.backdrop_good_sandwich),
    tape("bad-sandwich", "Bad Sandwich", Color(0.924f, 0.522f, 0.234f), false, R.drawable.backdrop_bad_sandwich),
    tape("newman", "Newman", Color(0.781f, 0.71f, 0.634f), false, R.drawable.backdrop_newman),
    tape("dansom", "Dansom", Color(0.555f, 0.288f, 0.461f), true, R.drawable.backdrop_dansom),
    tape("seafoam", "Seafoam", Color(0.699f, 0.422f, 0.368f), true, R.drawable.backdrop_seafoam),
    tape("gripp", "Gripp", Color(0.687f, 0.829f, 0.782f), false, R.drawable.backdrop_gripp),
    tape("silkmoth", "Silkmoth", Color(0.832f, 0.676f, 0.533f), false, R.drawable.backdrop_silkmoth),
    tape("zephyr", "Zephyr", Color(0.663f, 0.448f, 0.464f), false, R.drawable.backdrop_zephyr),
    tape("wirman", "Wirman", Color(0.462f, 0.333f, 0.308f), true, R.drawable.backdrop_wirman),
    tape("tapdole", "Tapdole", Color(0.646f, 0.723f, 0.696f), false, R.drawable.backdrop_tapdole),
    tape("mariana", "Mariana", Color(0.663f, 0.626f, 0.58f), false, R.drawable.backdrop_mariana),
    tape("yu", "Yu", Color(0.156f, 0.089f, 0.095f), true, R.drawable.backdrop_yu),
    tape("madeline", "Madeline", Color(0.791f, 0.627f, 0.852f), false, R.drawable.backdrop_madeline),
    tape("faden", "Faden", Color(0.729f, 0.0f, 0.0f), true, R.drawable.backdrop_faden),
    tape("murtaugh", "Murtaugh", Color(0.227f, 0.321f, 0.327f), true, R.drawable.backdrop_murtaugh),
    tape("lorelei", "Lorelei", Color(0.492f, 0.18f, 0.455f), true, R.drawable.backdrop_lorelei),
    tape("yefreitor", "Yefreitor", Color(0.356f, 0.368f, 0.288f), true, R.drawable.backdrop_yefreitor),
    tape("barry", "Barry", Color(0.889f, 0.89f, 0.89f), false, R.drawable.backdrop_barry),
    tape("birch", "Birch", Color(0.102f, 0.219f, 0.239f), true, R.drawable.backdrop_birch),
    tape("cameron", "Cameron", Color(0.247f, 0.416f, 0.003f), true, R.drawable.backdrop_cameron),
    tape("esquire", "Esquire", Color(0.828f, 0.727f, 0.538f), false, R.drawable.backdrop_esquire),
    tape("believe", "Believe", Color(0.19f, 0.157f, 0.278f), true, R.drawable.backdrop_believe),
    tape("dahlia", "Dahlia", Color(0.433f, 0.257f, 0.492f), true, R.drawable.backdrop_dahlia),
    tape("pfleumer", "Pfleumer", Color(0.268f, 0.19f, 0.16f), true, R.drawable.backdrop_pfleumer),
    tape("pride-rainbow", "Rainbow", Color(0.56f, 0.387f, 0.28f), true, R.drawable.backdrop_pride_rainbow),
    tape("pride-transgender", "Transgender", Color(0.729f, 0.788f, 0.88f), false, R.drawable.backdrop_pride_transgender),
    tape("pride-bisexual", "Bisexual", Color(0.459f, 0.153f, 0.557f), true, R.drawable.backdrop_pride_bisexual),
    tape("pride-non-binary", "Non-binary", Color(0.653f, 0.576f, 0.502f), false, R.drawable.backdrop_pride_non_binary),
    tape("pride-lesbian", "Lesbian", Color(0.849f, 0.425f, 0.44f), false, R.drawable.backdrop_pride_lesbian),
    tape("pride-pansexual", "Pansexual", Color(0.712f, 0.555f, 0.515f), false, R.drawable.backdrop_pride_pansexual),
    tape("pride-asexual", "Asexual", Color(0.534f, 0.409f, 0.534f), true, R.drawable.backdrop_pride_asexual),
    tape("pride-intersex", "Intersex", Color(0.934f, 0.742f, 0.084f), false, R.drawable.backdrop_pride_intersex),
    tape("pride-genderfluid", "Genderfluid", Color(0.591f, 0.361f, 0.644f), true, R.drawable.backdrop_pride_genderfluid),
    tape("pride-aromantic", "Aromantic", Color(0.512f, 0.628f, 0.479f), false, R.drawable.backdrop_pride_aromantic),
    tape("pride-agender", "Agender", Color(0.6f, 0.633f, 0.57f), false, R.drawable.backdrop_pride_agender),
    tape("pride-demisexual", "Demisexual", Color(0.697f, 0.648f, 0.698f), false, R.drawable.backdrop_pride_demisexual),
    tape("pride-queer", "Queer", Color(0.589f, 0.584f, 0.465f), false, R.drawable.backdrop_pride_queer),
    tape("pride-polyamory", "Polyamory", Color(0.466f, 0.4f, 0.58f), true, R.drawable.backdrop_pride_polyamory),
    tape("pride-demigirl", "Demigirl", Color(0.791f, 0.7f, 0.73f), false, R.drawable.backdrop_pride_demigirl),
    tape("pride-demiboy", "Demiboy", Color(0.677f, 0.748f, 0.769f), false, R.drawable.backdrop_pride_demiboy),
    tape("pride-transfem", "Transfem", Color(0.843f, 0.781f, 0.896f), false, R.drawable.backdrop_pride_transfem),
    tape("pride-transmasc", "Transmasc", Color(0.753f, 0.817f, 0.925f), false, R.drawable.backdrop_pride_transmasc),
)

// dt-ios BackdropStyle grain: a 192-speck tile, each speck 2 px, black or white at up to 30 % alpha
private const val GRAIN_SPECKS = 192
private const val SPECK_PX = 2
private const val GRAIN_STRENGTH = 0.3f

private val grainTile: ImageBitmap by lazy {
    val side = GRAIN_SPECKS * SPECK_PX
    val pixels = IntArray(side * side)
    for (speck in 0 until GRAIN_SPECKS * GRAIN_SPECKS) {
        val alpha = (Random.nextFloat() * GRAIN_STRENGTH * 255).roundToInt()
        val value = if (Random.nextBoolean()) 255 else 0
        val argb = (alpha shl 24) or (value shl 16) or (value shl 8) or value
        val x = speck % GRAIN_SPECKS * SPECK_PX
        val y = speck / GRAIN_SPECKS * SPECK_PX
        for (dy in 0 until SPECK_PX) for (dx in 0 until SPECK_PX) pixels[(y + dy) * side + x + dx] = argb
    }
    Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private val grainBrush by lazy { ShaderBrush(ImageShader(grainTile, TileMode.Repeated, TileMode.Repeated)) }

/** The wash over the backdrop: white below 0, black above, as strong as the tint. */
fun backdropWash(tint: Float): Color = (if (tint < 0) Color.White else Color.Black).copy(alpha = abs(tint))

/**
 * dt-ios StripBackdropView: base, the tape covering `strip` centred on it (turned a quarter clockwise when
 * `turned`, for a bottom band), grain and wash over the whole canvas; the page covers what is not strip.
 */
fun DrawScope.drawBackdrop(base: Color, tape: ImageBitmap?, grain: Float, wash: Color, strip: Rect, turned: Boolean) {
    drawRect(base)
    if (tape != null && strip.width > 0 && strip.height > 0) {
        clipRect(strip.left, strip.top, strip.right, strip.bottom) {
            val across = if (turned) strip.height else strip.width
            val along = if (turned) strip.width else strip.height
            val scale = max(across / tape.width, along / tape.height)
            val size = IntSize((tape.width * scale).roundToInt(), (tape.height * scale).roundToInt())
            val origin = IntOffset((strip.center.x - size.width / 2f).roundToInt(), (strip.center.y - size.height / 2f).roundToInt())
            rotate(if (turned) 90f else 0f, strip.center) {
                drawImage(tape, dstOffset = origin, dstSize = size, filterQuality = FilterQuality.Medium)
            }
        }
    }
    drawRect(grainBrush, alpha = grain)
    drawRect(wash)
}

// dt-ios BackdropGallery TapeEnd
private val tapeWidth = 42.dp
private val tapeResting = 112.dp
private val tapePulled = 146.dp

/** The backdrop picker: tape ends hanging from a dispenser edge, then the tint and grain sliders. */
@Composable
fun BackdropGallery(stores: ShellStores, darkTheme: Boolean) {
    val c = LocalDelphiColours.current
    val haptics = LocalHapticFeedback.current
    val selected = backdropStyles.indexOf(stores.backdrop)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 2).coerceAtLeast(0))
    Column(Modifier.fillMaxWidth().background(c.card)) {
        Box(Modifier.padding(top = 12.dp)) {
            LazyRow(
                state = list,
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.height(tapePulled + 6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                itemsIndexed(backdropStyles, key = { _, style -> style.id }) { index, style ->
                    TapeEnd(style, stores.grain, backdropWash(stores.tint), index == selected, darkTheme) {
                        if (index != selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        stores.backdrop = style
                    }
                }
            }
            // the dispenser's edge
            Box(Modifier.fillMaxWidth().height(5.dp).background(c.foreground.copy(alpha = 0.85f)))
        }
        CanonRows(framed = false) {
            CanonRow("Tint", flush = true, controlWeight = 2f) {
                CanonSlider(
                    stores.tint,
                    { value ->
                        // snap to the as-drawn middle
                        val snapped = if (abs(value) < 0.06f) 0f else value
                        if (snapped == 0f && stores.tint != 0f) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        stores.tint = snapped
                    },
                    range = -1f..1f,
                    readout = "${(stores.tint * 100).roundToInt()}",
                )
            }
            CanonRow("Grain", flush = true, controlWeight = 2f) {
                CanonSlider(stores.grain, { stores.grain = it }, readout = "${(stores.grain * 100).roundToInt()}")
            }
        }
    }
}

@Composable
private fun TapeEnd(style: BackdropStyle, grain: Float, wash: Color, isSelected: Boolean, darkTheme: Boolean, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    val length by animateDpAsState(if (isSelected) tapePulled else tapeResting, spring(0.65f, Spring.StiffnessMediumLow))
    var pressed by remember { mutableStateOf(false) }
    val pull by animateDpAsState(if (pressed) 6.dp else 0.dp, tween(150))
    val base = style.base(c, darkTheme)
    val art = style.tape?.let { ImageBitmap.imageResource(it) }
    Row(
        Modifier
            .offset(y = pull)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
            .selectable(isSelected, interactionSource = null, indication = null, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = style.label },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            style.label,
            style = CanonText.caption.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
            color = if (isSelected) c.primary else c.mutedForeground,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .padding(top = 10.dp)
                .layout { measurable, _ ->
                    val text = measurable.measure(Constraints(maxWidth = (tapeResting - 10.dp).roundToPx()))
                    layout(text.height, text.width) {
                        text.placeWithLayer((text.height - text.width) / 2, (text.width - text.height) / 2) { rotationZ = 90f }
                    }
                },
        )
        Canvas(Modifier.size(tapeWidth, length).clip(TornEnd)) {
            drawRect(base)
            if (art != null) {
                // the middle of the strip, as the band shows it
                val frame = Size(size.width, tapePulled.toPx())
                val scale = max(frame.width / art.width, frame.height / art.height)
                val drawn = IntSize((art.width * scale).roundToInt(), (art.height * scale).roundToInt())
                drawImage(
                    art,
                    dstOffset = IntOffset(((frame.width - drawn.width) / 2).roundToInt(), ((frame.height - drawn.height) / 2).roundToInt()),
                    dstSize = drawn,
                    filterQuality = FilterQuality.Medium,
                )
            }
            drawRect(grainBrush, alpha = grain)
            drawRect(wash)
            drawPath(tornEdge(size), c.foreground.copy(alpha = 0.22f), style = Stroke(1.dp.toPx()))
        }
    }
}

// serrated bottom edge: seven teeth, 4 dp deep
private fun tornEdge(size: Size, depth: Float = size.width / 10.5f) = Path().apply {
    val teeth = 7
    val pitch = size.width / teeth
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height - depth)
    for (tooth in 0 until teeth) {
        val right = size.width - tooth * pitch
        lineTo(right - pitch / 2, size.height)
        lineTo(right - pitch, size.height - depth)
    }
    close()
}

private object TornEnd : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
        Outline.Generic(tornEdge(size, with(density) { 4.dp.toPx() }))
}
