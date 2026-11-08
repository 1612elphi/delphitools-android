package com.delphi.delphitools.gallery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as BitmapCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.canon.CanonChecks
import com.delphi.delphitools.canon.CanonCompressionScale
import com.delphi.delphitools.canon.CanonDetentWheel
import com.delphi.delphitools.canon.CanonDimensionLine
import com.delphi.delphitools.canon.CanonDimensionPlacement
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonImageCompare
import com.delphi.delphitools.canon.CanonLamp
import com.delphi.delphitools.canon.CanonLoupe
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonReadout
import com.delphi.delphitools.canon.CanonReadoutBand
import com.delphi.delphitools.canon.CanonReadoutRowHeight
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonTransport
import com.delphi.delphitools.canon.canonChecks
import com.delphi.delphitools.canon.canonDimensionLines
import com.delphi.delphitools.canon.canonDimensionSheet
import com.delphi.delphitools.canon.canonLoupeFrame
import com.delphi.delphitools.canon.canonPlaced
import com.delphi.delphitools.canon.canonSmpte
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlin.math.abs
import kotlin.math.sin

private const val CLIP_SECONDS = 120.0

@Composable
fun MediaGallery(modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    GalleryPage(modifier) {
        CanonRows {
            CanonHeading("Readout band")
            CanonReadoutBand(
                CanonTransport(playhead = 83.4, playing = true, looping = true),
                Modifier.fillMaxWidth().height(120.dp),
            ) { WaveformStrip(it) }
            CanonReadoutBand(
                CanonTransport(playhead = 3723.16, paused = true),
                Modifier.fillMaxWidth().height(56.dp + CanonReadoutRowHeight),
                readout = CanonReadout.Below,
                format = canonSmpte(25),
            ) { FilmStrip(it) }
            CanonReadoutBand(
                CanonTransport(playhead = 4.2, playing = true, recording = true),
                Modifier.fillMaxWidth().height(120.dp),
                lamps = listOf(CanonLamp.Record, CanonLamp.Pause),
            ) { GalleryArt("Record lamp") }
        }
        CanonRows {
            CanonHeading("Image compare")
            val before = remember { hueBands(0.8f) }
            val after = remember { hueBands(0f) }
            Box(Modifier.fillMaxWidth().aspectRatio(2f).background(c.muted)) {
                CanonImageCompare(before, after, afterTitle = "Grey")
            }
            CanonHeading("Dimension line")
            GalleryDimensions()
            CanonHeading("Compression scale")
            Box(Modifier.fillMaxWidth().height(176.dp)) { CanonCompressionScale(2_400_000, 1_700_000, "JPEG") }
            Box(Modifier.fillMaxWidth().height(176.dp)) { CanonCompressionScale(1_000_000, 1_300_000, "PNG") }
            CanonHeading("Checks")
            Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
                CanonChecks(Modifier.weight(1f).fillMaxHeight())
                CanonChecks(Modifier.weight(1f).fillMaxHeight(), paper = true)
            }
        }
        CanonRows {
            CanonHeading("Loupe")
            GalleryLoupe()
            CanonHeading("Detent wheel")
            Row(
                Modifier.fillMaxWidth().height(200.dp).background(c.border),
                horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
            ) {
                val spaces = listOf("RGB", "HSL", "HSB", "OKLCH", "OKLab", "LCH")
                var space by remember { mutableIntStateOf(3) }
                CanonDetentWheel(
                    spaces.size,
                    space,
                    { space = it },
                    Modifier.weight(1f).fillMaxHeight().background(c.card),
                    accessibilityText = { spaces[it] },
                ) { index, focus -> WheelRow(spaces[index], focus) }
                var hour by remember { mutableIntStateOf(9) }
                CanonDetentWheel(
                    24,
                    hour,
                    { hour = it },
                    Modifier.weight(1f).fillMaxHeight().background(c.card),
                ) { index, focus -> WheelRow(index.toString().padStart(2, '0'), focus) }
            }
        }
    }
}

@Composable
private fun WheelRow(text: String, focus: Float) {
    Text(
        text,
        style = CanonText.bodySemibold,
        color = LocalDelphiColours.current.foreground.copy(alpha = 0.35f + 0.65f * focus),
        maxLines = 1,
    )
}

@Composable
private fun GalleryArt(label: String) {
    val c = LocalDelphiColours.current
    Box(
        Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(c.primary, c.destructive))),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = CanonText.body.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold), color = c.primaryForeground)
    }
}

@Composable
private fun WaveformStrip(playhead: Double?) {
    val c = LocalDelphiColours.current
    Canvas(Modifier.fillMaxSize()) {
        val bar = 3.dp.toPx()
        val gap = 1.dp.toPx()
        val count = (size.width / (bar + gap)).toInt()
        val played = ((playhead ?: 0.0) / CLIP_SECONDS).toFloat() * size.width
        for (index in 0 until count) {
            val t = index.toFloat() / count
            val level = 0.15f + 0.75f * abs(sin(t * 37f) * sin(t * 11f + 1f))
            val height = level * size.height * 0.8f
            val x = index * (bar + gap)
            drawRect(
                if (x < played) c.primary else c.mutedForeground.copy(alpha = 0.5f),
                Offset(x, (size.height - height) / 2),
                Size(bar, height),
            )
        }
        if (playhead != null) drawRect(c.foreground, Offset(played - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
    }
}

// ponytail: frames are hue blocks; a real filmstrip draws decoded video frames
@Composable
private fun FilmStrip(playhead: Double?) {
    val c = LocalDelphiColours.current
    Canvas(Modifier.fillMaxSize()) {
        val frame = size.height * 16 / 9
        val count = (size.width / frame).toInt() + 1
        for (index in 0 until count) {
            val tone = Color.hsv(index * 360f / count, 0.45f, 0.75f)
            drawRect(
                Brush.verticalGradient(listOf(tone, lerp(tone, Color.Black, 0.4f))),
                Offset(index * frame, 0f),
                Size(frame - CanonMetrics.hairline.toPx(), size.height),
            )
        }
        val played = ((playhead ?: 0.0) % CLIP_SECONDS / CLIP_SECONDS).toFloat() * size.width
        if (playhead != null) drawRect(c.foreground, Offset(played - 1.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
    }
}

@Composable
private fun GalleryDimensions() {
    val c = LocalDelphiColours.current
    val density = LocalDensity.current
    val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
    var blank by remember { mutableStateOf(false) }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(c.muted)
            .clickable { blank = !blank }
    ) {
        with(density) {
            val area = Rect(16.dp.toPx(), 12.dp.toPx(), constraints.maxWidth - 16.dp.toPx(), constraints.maxHeight - 12.dp.toPx())
            val sheet = canonDimensionSheet(Size(1600f, 1200f), area, rightToLeft)
            val (across, down) = canonDimensionLines(sheet, rightToLeft)
            Box(Modifier.canonPlaced(CanonDimensionPlacement(sheet.center, sheet.size)).canonChecks())
            CanonDimensionLine(1600, Modifier.canonPlaced(across), blank)
            CanonDimensionLine(1200, Modifier.canonPlaced(down), blank)
        }
    }
}

@Composable
private fun GalleryLoupe() {
    val c = LocalDelphiColours.current
    val colour = c.primary
    val pixels = remember(colour) { loupePixels(colour) }
    BoxWithConstraints(Modifier.fillMaxWidth().height(200.dp).background(c.muted)) {
        val density = LocalDensity.current
        with(density) {
            val size = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
            val frame = canonLoupeFrame(
                Offset(size.width / 2, size.height / 2 + 60.dp.toPx()),
                112.dp.toPx(),
                24.dp.toPx(),
                size,
                top = 8.dp.toPx(),
                margin = 8.dp.toPx(),
            )
            CanonLoupe(
                pixels,
                colour,
                Modifier.canonPlaced(CanonDimensionPlacement(frame.center, frame.size)),
            )
        }
    }
}

private fun hueBands(saturation: Float): ImageBitmap {
    val image = ImageBitmap(320, 160)
    val canvas = BitmapCanvas(image)
    val paint = Paint()
    for (x in 0 until 32) {
        paint.color = Color.hsv(x * 360f / 32, saturation, 0.9f)
        canvas.drawRect(Rect(x * 10f, 0f, x * 10f + 10f, 160f), paint)
    }
    return image
}

private fun loupePixels(colour: Color): ImageBitmap {
    val side = 15
    val image = ImageBitmap(side, side)
    val canvas = BitmapCanvas(image)
    val paint = Paint()
    for (x in 0 until side) {
        for (y in 0 until side) {
            val towards = if ((x + y) % 2 == 0) Color.White else Color.Black
            paint.color = lerp(colour, towards, (abs(x - 7) + abs(y - 7)) / 16f)
            canvas.drawRect(Rect(x.toFloat(), y.toFloat(), x + 1f, y + 1f), paint)
        }
    }
    return image
}
