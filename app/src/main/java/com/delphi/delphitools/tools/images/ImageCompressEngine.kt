package com.delphi.delphitools.tools.images

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.media.MediaCodecList
import android.media.MediaFormat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.heifwriter.HeifWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/** [bitmapFormat] null: encoded by HeifWriter. */
enum class ImageCompressFormat(
    val label: String,
    val fileExtension: String,
    val mimeType: String,
    val bitmapFormat: Bitmap.CompressFormat?,
) {
    Jpeg("JPEG", "jpg", "image/jpeg", Bitmap.CompressFormat.JPEG),
    Png("PNG", "png", "image/png", Bitmap.CompressFormat.PNG),
    Heic("HEIC", "heic", "image/heic", null),
    WebP("WebP", "webp", "image/webp", Bitmap.CompressFormat.WEBP_LOSSY);

    val hasQuality get() = this != Png
    val isOpaque get() = this == Jpeg

    companion object {
        // heifwriter needs hevc encoder
        val available: List<ImageCompressFormat> by lazy { entries.filter { it != Heic || hasHevcEncoder() } }
    }
}

private fun hasHevcEncoder() = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
    info.isEncoder && info.supportedTypes.any {
        it.equals(MediaFormat.MIMETYPE_IMAGE_ANDROID_HEIC, ignoreCase = true) ||
            it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)
    }
}

/**
 * dt-ios ImageCompressEngine. ImageDecoder applies the EXIF orientation to the pixels, so the output
 * carries no orientation tag; like iOS, no other metadata is written.
 */
object ImageCompressEngine {
    private const val HEIC_TIMEOUT_MS = 30_000L

    // ponytail: full-resolution software bitmap, 4 bytes a pixel; tile or subsample if large photos run out of memory
    fun decode(data: ByteArray): Bitmap? = runCatching {
        val image = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(data))) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        if (image.config == Bitmap.Config.ARGB_8888) image else image.copy(Bitmap.Config.ARGB_8888, false)
    }.getOrNull()

    /** [image] scaled down to [maxPixels] on its long side. */
    fun preview(image: Bitmap, maxPixels: Int): Bitmap {
        val scale = maxPixels.toFloat() / maxOf(image.width, image.height)
        if (scale >= 1f) return image
        return image.scale(maxOf(1, (image.width * scale).roundToInt()), maxOf(1, (image.height * scale).roundToInt()))
    }

    /** Decodes [data] straight to [maxPixels] on its long side; only for this engine's output, which has no orientation tag. */
    fun thumbnail(data: ByteArray, maxPixels: Int): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(data))) { decoder, info, _ ->
            val scale = maxPixels.toFloat() / maxOf(info.size.width, info.size.height)
            if (scale < 1f) {
                decoder.setTargetSize(maxOf(1, (info.size.width * scale).roundToInt()), maxOf(1, (info.size.height * scale).roundToInt()))
            }
        }
    }.getOrNull()

    fun encode(image: Bitmap, format: ImageCompressFormat, quality: Int, scratch: File): ByteArray? = runCatching {
        val source = if (format.isOpaque && image.hasAlpha()) flattenedOnWhite(image) else image
        val q = quality.coerceIn(1, 100)
        val bitmapFormat = format.bitmapFormat ?: return@runCatching encodeHeic(source, q, scratch)
        ByteArrayOutputStream().use { out ->
            check(source.compress(bitmapFormat, q, out))
            out.toByteArray()
        }
    }.getOrNull()

    // tiled encodes vary run to run (cbr on pixel 8); one frame is repeatable, tiles only past the encoder's size limit
    private fun encodeHeic(image: Bitmap, quality: Int, scratch: File): ByteArray =
        runCatching { encodeHeic(image, quality, scratch, grid = false) }.getOrNull()
            ?: encodeHeic(image, quality, scratch, grid = true)

    private fun encodeHeic(image: Bitmap, quality: Int, scratch: File, grid: Boolean): ByteArray {
        val file = File.createTempFile("compress", ".heic", scratch)
        try {
            HeifWriter.Builder(file.path, image.width, image.height, HeifWriter.INPUT_MODE_BITMAP)
                .setQuality(quality)
                .setGridEnabled(grid)
                .build()
                .use { writer ->
                    writer.start()
                    writer.addBitmap(image)
                    writer.stop(HEIC_TIMEOUT_MS)
                }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    private fun flattenedOnWhite(image: Bitmap): Bitmap {
        val out = createBitmap(image.width, image.height)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(image, 0f, 0f, null)
        }
        return out
    }
}
