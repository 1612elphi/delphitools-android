package com.delphi.delphitools.shell

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// dt-ios Shell/StickerPeel.swift: fold travel, then the lift
private const val FOLD_MS = 700
private const val LIFT_MS = 400
private const val FOLD_REACH = 0.55f
private const val CURL = 0.9f
private const val LIFT_TURN = -0.12f * 180f / Math.PI.toFloat()
private val flapShadowRadius = 6.dp
private val liftShadowDrop = 8.dp
private val paper = Color(0.97f, 0.97f, 0.97f)
private val sheen = arrayOf(
    0f to Color.Black.copy(alpha = 0.2f),
    0.1f to Color.White.copy(alpha = 0.7f),
    0.4f to Color.White.copy(alpha = 0f),
    1f to Color.Black.copy(alpha = 0.08f),
)
private val easeInOutCubic = Easing { t -> if (t < 0.5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2 }

// dt-ios Shell/ToolSticker.swift: the web's lousy sticker, -2.5°, at most 360 pt wide
private const val TOOL_TURN = -2.5f
private val toolStickerWidth = 360.dp
private val heroMaxWidth = 672.dp

private enum class Align { Top, Centre, Bottom }

// min, share, max: the web's clamp(min, share of width, max)
private class WallSticker(val file: String, val turn: Float, val min: Float, val share: Float, val max: Float, val align: Align)

// dt-ios Shell/HomeWeb.swift HomeStickerWallCell, from the web's sticker-wall.gts
private val wallStickers = listOf(
    WallSticker("chant", 4f, 180f, 0.42f, 260f, Align.Centre),
    WallSticker("trans", -7f, 96f, 0.22f, 132f, Align.Top),
    WallSticker("marker", 6f, 120f, 0.30f, 158f, Align.Bottom),
    WallSticker("policy", -4f, 170f, 0.40f, 240f, Align.Top),
    WallSticker("saas-h8r", 5f, 120f, 0.30f, 158f, Align.Bottom),
)

// ponytail: one region; iOS splits the stickers either side of a vertical fold crease
/** The web's sticker wall: tap one and it peels off, then its PNG goes to the share sheet. */
@Composable
fun HomeStickerWall(modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    Column(modifier.fillMaxWidth()) {
        Text("Stickers", style = CanonText.body.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), color = c.foreground)
        Spacer(Modifier.height(4.dp))
        Text(
            "Free for the taking. Peel one off and it'll hand you a high-res PNG to print yourself!",
            style = footnote,
            color = c.mutedForeground,
            modifier = Modifier.widthIn(max = heroMaxWidth),
        )
        Spacer(Modifier.height(40.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = maxWidth.value
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (width < 640) 12.dp else 24.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                for (sticker in wallStickers) {
                    val path = "stickers/${sticker.file}.png"
                    val aspect = rememberAssetAspect(path) ?: continue
                    PeelSticker(
                        path,
                        filename = "delphitools-${sticker.file}-sticker.png",
                        description = "${sticker.file} sticker",
                        toolName = null,
                        modifier = Modifier
                            .align(
                                when (sticker.align) {
                                    Align.Top -> Alignment.Top
                                    Align.Centre -> Alignment.CenterVertically
                                    Align.Bottom -> Alignment.Bottom
                                }
                            )
                            .width(min(width, (width * sticker.share).coerceIn(sticker.min, sticker.max)).dp)
                            .aspectRatio(aspect)
                            .rotate(sticker.turn),
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** The web's per-tool sticker: tap to peel it off into the share sheet. Draws nothing for a tool without one. */
@Composable
fun ToolSticker(tool: Tool, modifier: Modifier = Modifier) {
    val path = "tool-stickers/${tool.id}.png"
    val aspect = rememberAssetAspect(path) ?: return
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        PeelSticker(
            path,
            filename = "delphitools-${tool.id}-sticker.png",
            description = "Sticker",
            toolName = tool.name,
            modifier = Modifier
                .padding(horizontal = CanonMetrics.panelPadding)
                .widthIn(max = toolStickerWidth)
                .fillMaxWidth()
                .aspectRatio(aspect)
                .rotate(TOOL_TURN),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Have a sticker! Tap to peel off.",
            style = footnote,
            color = LocalDelphiColours.current.mutedForeground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }
}

private val footnote = CanonText.footnoteSemibold.copy(fontWeight = FontWeight.Normal)

/** Width over height of a PNG in assets, or null when there is none. */
@Composable
private fun rememberAssetAspect(path: String): Float? {
    val context = LocalContext.current
    return remember(path) {
        try {
            context.assets.open(path).use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)
                options.outWidth.toFloat() / max(options.outHeight, 1)
            }
        } catch (_: IOException) {
            null
        }
    }
}

/** The sticker at display size, and its alpha blurred for the flap's shadow. */
private class StickerArt(val image: ImageBitmap, val shadow: ImageBitmap, val shadowOffset: IntOffset)

// one bitmap at display size: the full PNG took seconds to decode on iOS
private fun decodeSticker(context: Context, path: String, width: Int, blur: Float): StickerArt? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= width) sample *= 2
    val decoded = context.assets.open(path).use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val height = max(1, decoded.height * width / decoded.width)
    val image = Bitmap.createScaledBitmap(decoded, width, height, true)
    val offset = IntArray(2)
    val shadow = image.extractAlpha(android.graphics.Paint().apply { maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) }, offset)
    return StickerArt(image.asImageBitmap(), shadow.asImageBitmap(), IntOffset(offset[0], offset[1]))
}

/** The peel's moving parts: fold travel, lift, and the drop back onto the sheet. */
private class PeelState(private val scope: CoroutineScope, private val haptics: HapticFeedback) {
    val fold = Animatable(0f)
    val lift = Animatable(0f)
    val drop = Animatable(1f)
    val alpha = Animatable(1f)
    var folding by mutableStateOf(false)
    var silhouette by mutableStateOf(false)
    var peeling = false
        private set
    private var job: Job? = null

    fun peel(onLift: () -> Unit) {
        if (peeling) return
        peeling = true
        job = scope.launch {
            if (!ValueAnimator.areAnimatorsEnabled()) {
                alpha.animateTo(0f, tween(300))
                onLift()
                return@launch
            }
            silhouette = true
            folding = true
            fold.snapTo(0f)
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            fold.animateTo(FOLD_REACH, tween(FOLD_MS, easing = easeInOutCubic))
            onLift()
            lift.animateTo(1f, tween(LIFT_MS, easing = EaseIn))
        }
    }

    /** Back on the sheet: the sticker drops into its silhouette. */
    fun restick() {
        if (!peeling) return
        peeling = false
        job?.cancel()
        job = scope.launch {
            folding = false
            lift.snapTo(0f)
            fold.snapTo(0f)
            if (!ValueAnimator.areAnimatorsEnabled()) {
                silhouette = false
                alpha.animateTo(1f, tween(300))
                return@launch
            }
            launch {
                drop.snapTo(1.12f)
                // CASpringAnimation damping 12, stiffness 100, mass 1
                drop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 100f))
            }
            launch {
                alpha.snapTo(0f)
                alpha.animateTo(1f, tween(150))
            }
            delay(200)
            silhouette = false
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }
}

@Composable
private fun PeelSticker(path: String, filename: String, description: String, toolName: String?, modifier: Modifier) {
    val context = LocalContext.current
    val c = LocalDelphiColours.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val state = remember { PeelState(scope, haptics) }
    val share = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { state.restick() }
    val dark = c.background.luminance() < 0.5f
    BoxWithConstraints(
        modifier
            .clickable(interactionSource = null, indication = null, role = Role.Button) {
                state.peel {
                    val intent = shareIntent(context, path, filename)
                    if (intent == null) state.restick() else share.launch(intent)
                }
            }
            .semantics {
                contentDescription = description
                if (toolName != null) stateDescription = toolName
            }
    ) {
        val width = constraints.maxWidth
        val blur = with(LocalDensity.current) { flapShadowRadius.toPx() }
        val art by produceState<StickerArt?>(null, path, width) {
            if (width > 0) value = withContext(Dispatchers.IO) { decodeSticker(context, path, width, blur) }
        }
        Canvas(Modifier.matchParentSize()) {
            art?.let { drawPeel(it, state, c.foreground, if (dark) 0.6f else 0.3f) }
        }
    }
}

private fun DrawScope.drawPeel(art: StickerArt, state: PeelState, foreground: Color, flapShadow: Float) {
    val dst = IntSize(size.width.toInt(), size.height.toInt())
    if (state.silhouette) drawImage(art.image, dstSize = dst, colorFilter = ColorFilter.tint(foreground.copy(alpha = 0.1f)))
    val lift = state.lift.value
    val alpha = state.alpha.value * (1 - lift)
    val geometry = if (state.folding) peelGeometry(size, state.fold.value, layoutDirection == LayoutDirection.Rtl) else null
    withTransform({
        translate(0f, -size.height * 0.5f * lift)
        rotate(LIFT_TURN * lift)
        scale(1 + 0.08f * lift, 1 + 0.08f * lift)
    }) {
        if (geometry != null && lift > 0f) {
            clipPath(halfPlane(geometry.foldPoint, geometry.inward)) {
                drawImage(art.shadow, topLeft = art.shadowOffset.toOffset() + Offset(0f, liftShadowDrop.toPx()), alpha = 0.3f * lift * alpha, colorFilter = ColorFilter.tint(Color.Black))
            }
        }
        scale(state.drop.value) {
            if (geometry == null) {
                drawImage(art.image, dstSize = dst, alpha = alpha)
            } else {
                clipPath(polygon(geometry.stuck)) { drawImage(art.image, dstSize = dst, alpha = alpha) }
            }
        }
        if (geometry == null) return@withTransform
        withTransform({ transform(geometry.turn.toMatrix()) }) {
            val outward = Offset(-geometry.inward.x, -geometry.inward.y)
            clipPath(halfPlane(geometry.foldPoint, outward)) {
                drawImage(art.shadow, topLeft = art.shadowOffset.toOffset(), alpha = flapShadow * alpha, colorFilter = ColorFilter.tint(Color.Black))
            }
            clipPath(polygon(geometry.flap)) {
                val canvas = drawContext.canvas
                canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { this.alpha = alpha })
                drawImage(art.image, dstSize = dst, colorFilter = ColorFilter.tint(paper))
                drawImage(art.image, dstSize = dst, alpha = 0.05f)
                drawRect(Brush.linearGradient(*sheen, start = geometry.foldPoint, end = geometry.corner), blendMode = BlendMode.SrcAtop)
                canvas.restore()
            }
        }
    }
}

private fun IntOffset.toOffset() = Offset(x.toFloat(), y.toFloat())

private fun polygon(points: List<Offset>) = Path().apply {
    points.firstOrNull()?.let { moveTo(it.x, it.y) }
    points.drop(1).forEach { lineTo(it.x, it.y) }
    close()
}

/** The side of the line through [p] that [normal] points to, far enough out to keep the shadow's blur. */
private fun DrawScope.halfPlane(p: Offset, normal: Offset): Path {
    val reach = size.maxDimension
    val big = listOf(Offset(-reach, -reach), Offset(size.width + reach, -reach), Offset(size.width + reach, size.height + reach), Offset(-reach, size.height + reach))
    return polygon(clipPolygon(big, p, normal))
}

/** Writes the sticker's PNG to the cache and wraps it for the share sheet; null when the copy fails. */
private fun shareIntent(context: Context, path: String, filename: String): Intent? {
    val file = File(context.cacheDir, "stickers/$filename")
    try {
        file.parentFile?.mkdirs()
        context.assets.open(path).use { input -> file.outputStream().use { input.copyTo(it) } }
    } catch (_: IOException) {
        return null
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.stickers", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(filename, uri)
    return Intent.createChooser(send, null)
}

/** A 2D affine map: x' = a x + c y + tx, y' = b x + d y + ty. */
internal class Affine(val a: Float, val b: Float, val c: Float, val d: Float, val tx: Float, val ty: Float) {
    fun apply(p: Offset) = Offset(a * p.x + c * p.y + tx, b * p.x + d * p.y + ty)

    fun toMatrix() = Matrix().apply {
        values[Matrix.ScaleX] = a
        values[Matrix.SkewY] = b
        values[Matrix.SkewX] = c
        values[Matrix.ScaleY] = d
        values[Matrix.TranslateX] = tx
        values[Matrix.TranslateY] = ty
    }
}

/**
 * A fold from the lower trailing corner, [progress] of the way along the diagonal: [stuck] is the part still on the sheet,
 * [flap] the part past the fold before it turns over, [turn] turns it over the fold, shortened by the curl.
 */
internal class PeelGeometry(val corner: Offset, val foldPoint: Offset, val inward: Offset, val stuck: List<Offset>, val flap: List<Offset>, val turn: Affine)

internal fun peelGeometry(size: Size, progress: Float, flipped: Boolean = false): PeelGeometry {
    val corner = Offset(if (flipped) 0f else size.width, size.height)
    val opposite = Offset(if (flipped) size.width else 0f, 0f)
    val length = max(hypot(opposite.x - corner.x, opposite.y - corner.y), 1f)
    val u = (opposite - corner) / length
    val p = corner + u * (length * progress)
    val rect = listOf(Offset.Zero, Offset(size.width, 0f), Offset(size.width, size.height), Offset(0f, size.height))
    return PeelGeometry(corner, p, u, clipPolygon(rect, p, u), clipPolygon(rect, p, -u), turnOver(p, u, CURL))
}

/** Mirror across the line through [p] normal to [u], then shorten along [u] by [curl]: I - (1 + curl) u uᵀ about p. */
internal fun turnOver(p: Offset, u: Offset, curl: Float): Affine {
    val s = 1 + curl
    val a = 1 - s * u.x * u.x
    val b = -s * u.x * u.y
    val d = 1 - s * u.y * u.y
    return Affine(a, b, b, d, p.x - (a * p.x + b * p.y), p.y - (b * p.x + d * p.y))
}

/** The part of [polygon] where (x - p) · normal ≥ 0. */
internal fun clipPolygon(polygon: List<Offset>, p: Offset, normal: Offset): List<Offset> {
    fun side(x: Offset) = (x.x - p.x) * normal.x + (x.y - p.y) * normal.y
    val result = mutableListOf<Offset>()
    for ((index, a) in polygon.withIndex()) {
        val b = polygon[(index + 1) % polygon.size]
        val sa = side(a)
        val sb = side(b)
        if (sa >= 0) result += a
        if ((sa >= 0) != (sb >= 0)) result += a + (b - a) * (sa / (sa - sb))
    }
    return result
}
