package com.delphi.delphitools.tools.images

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.io.File
import kotlin.math.roundToInt

/**
 * dt-core ImageOutputFormat, less the formats Android has no encoder for (BMP, TIFF, GIF).
 * [compress] is the ImageCompressEngine format that writes it.
 */
enum class ImageOutputFormat(val label: String, val fileExtension: String, val mimeType: String, val compress: ImageCompressFormat) {
    Png("PNG", "png", "image/png", ImageCompressFormat.Png),
    Jpeg("JPEG", "jpg", "image/jpeg", ImageCompressFormat.Jpeg),
    Heic("HEIC", "heic", "image/heic", ImageCompressFormat.Heic);

    val supportsQuality get() = this == Jpeg || this == Heic
    val supportsTransparency get() = this == Png

    companion object {
        val available: List<ImageOutputFormat> by lazy { entries.filter { it.compress in ImageCompressFormat.available } }
    }
}

enum class ImageResizeMode(val label: String) { Original("Original"), Percentage("Percentage"), Custom("Custom") }

/** The image converter's sizing arithmetic, after dt-ios ImageConverterViewModel and ConversionPicture. */
object ImageConvert {
    // ponytail: files decode at up to 8192 px on the long side and output stops there, as on iOS
    const val LONGEST_SIDE = 8192
    val percentagePresets = listOf(25, 50, 75, 150, 200)

    fun outputSize(mode: ImageResizeMode, width: Int, height: Int, percent: Double, customWidth: Int, customHeight: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        return when (mode) {
            ImageResizeMode.Original -> width to height
            ImageResizeMode.Percentage -> maxOf(1, (width * percent / 100).toInt()) to maxOf(1, (height * percent / 100).toInt())
            ImageResizeMode.Custom -> maxOf(1, customWidth) to maxOf(1, customHeight)
        }
    }

    /** The other side for [side] at the source's [from] over [to] proportion. */
    fun locked(side: Int, from: Int, to: Int): Int = maxOf(1, (side.toDouble() * to / from).roundToInt())

    /** The largest scale, in percent, that keeps the long side within [LONGEST_SIDE]. */
    fun percentLimit(width: Int, height: Int): Int {
        val long = maxOf(width, height)
        if (long <= 0) return 400
        return (LONGEST_SIDE * 100 / long).coerceIn(1, 400)
    }

    /** A custom size scaled down to fit [LONGEST_SIDE]; unchanged when it fits. */
    fun capped(width: Int, height: Int): Pair<Int, Int> {
        val long = maxOf(width, height)
        if (long <= LONGEST_SIDE) return width to height
        val scale = LONGEST_SIDE.toDouble() / long
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }

    /** The output and the source, to one scale and centred in [frame], the sheet for the larger of the two. */
    fun frames(source: Size, output: Size, frame: Rect): Pair<Rect, Rect> {
        val box = Size(maxOf(source.width, output.width), maxOf(source.height, output.height))
        if (box.width <= 0f || box.height <= 0f || frame.width <= 0f) return Rect.Zero to Rect.Zero
        val scale = frame.width / box.width
        fun centred(size: Size): Rect {
            val scaled = Size(maxOf(1f, size.width * scale), maxOf(1f, size.height * scale))
            return Rect(frame.center.x - scaled.width / 2, frame.center.y - scaled.height / 2, frame.center.x + scaled.width / 2, frame.center.y + scaled.height / 2)
        }
        return centred(output) to centred(source)
    }

    /** "2.4 MB" as its number and its unit. */
    fun figure(size: String): Pair<String, String> {
        val split = size.indexOfLast { it.isWhitespace() }
        return if (split < 0) size to "" else size.substring(0, split) to size.substring(split + 1)
    }
}

object ImageConvertEngine {
    /** [image] at [width] by [height], over [flatten] (ARGB) when set, encoded; null when the encoder fails. */
    fun encode(image: Bitmap, format: ImageOutputFormat, width: Int, height: Int, quality: Int, flatten: Int?, scratch: File): ByteArray? {
        val sized = if (width != image.width || height != image.height) image.scale(width, height) else image
        val source = if (flatten == null) sized else createBitmap(width, height).also { out ->
            Canvas(out).apply {
                drawColor(flatten)
                drawBitmap(sized, 0f, 0f, null)
            }
            out.setHasAlpha(false)
        }
        return ImageCompressEngine.encode(source, format.compress, quality, scratch)
    }
}
