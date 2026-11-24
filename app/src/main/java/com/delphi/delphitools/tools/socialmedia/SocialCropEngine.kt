package com.delphi.delphitools.tools.socialmedia

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class CropRatio(val id: String, val name: String, val widthRatio: Double, val heightRatio: Double) {
    val aspectRatio get() = widthRatio / heightRatio

    /** e.g. 1.91:1 */
    val label get() = "${figure(widthRatio)}:${figure(heightRatio)}"

    private fun figure(value: Double) = if (value == floor(value)) value.toLong().toString() else value.toString()
}

data class CropPlatform(val id: String, val name: String, val ratios: List<CropRatio>)

/** dt-ios CropPresets. */
object CropPresets {
    private const val SQUARE = "Square"
    private const val PORTRAIT = "Portrait"
    private const val LANDSCAPE = "Landscape"

    private class Preset(val key: String, val name: String, val width: Double, val height: Double)

    private fun r(key: String, name: String, width: Number, height: Number) = Preset(key, name, width.toDouble(), height.toDouble())

    private fun platform(id: String, name: String, vararg ratios: Preset) =
        CropPlatform(id, name, ratios.map { CropRatio("$id-${it.key}", it.name, it.width, it.height) })

    // x omitted deliberately
    val all = listOf(
        // from the web presets
        platform(
            "bluesky", "Bluesky",
            r("square", SQUARE, 1, 1), r("landscape", LANDSCAPE, 16, 9),
            r("portrait", PORTRAIT, 3, 4), r("wide", "Wide", 2, 1),
        ),
        // facebook.com/business/ads-guide/update/image (2026-09-26)
        platform(
            "facebook", "Facebook",
            r("portrait", PORTRAIT, 4, 5), r("square", SQUARE, 1, 1),
            r("landscape", LANDSCAPE, 1.91, 1), r("stories", "Stories", 9, 16),
        ),
        // 3:4 since 2025: influencermarketinghub.com/instagram-image-sizes
        platform(
            "instagram", "Instagram",
            r("square", SQUARE, 1, 1), r("portrait", PORTRAIT, 3, 4), r("classic", "Classic", 4, 5),
            r("landscape", LANDSCAPE, 1.91, 1), r("reels", "Reels", 9, 16),
        ),
        // linkedin.com/help/lms/answer/a426534 (2026-09-26)
        platform(
            "linkedin", "LinkedIn",
            r("square", SQUARE, 1, 1), r("portrait", PORTRAIT, 4, 5), r("landscape", LANDSCAPE, 1.91, 1),
        ),
        // docs.joinmastodon.org/user/posting (2026-09-26)
        platform("mastodon", "Mastodon", r("preview", "Preview", 16, 9)),
        // help.pinterest.com/en/business/article/pinterest-product-specs (2026-09-26)
        platform("pinterest", "Pinterest", r("portrait", PORTRAIT, 2, 3), r("square", SQUARE, 1, 1)),
        // from the web presets
        platform(
            "threads", "Threads",
            r("square", SQUARE, 1, 1), r("portrait", PORTRAIT, 4, 5),
            r("landscape", LANDSCAPE, 1.91, 1), r("stories", "Stories", 9, 16),
        ),
        // ads.tiktok.com/help/article/tiktok-auction-in-feed-ads (2026-09-26)
        platform(
            "tiktok", "TikTok",
            r("portrait", PORTRAIT, 9, 16), r("square", SQUARE, 1, 1), r("landscape", LANDSCAPE, 16, 9),
        ),
        // support.google.com/youtube/answer/72431, /7124474
        platform(
            "youtube", "YouTube",
            r("thumbnail", "Thumbnail", 16, 9), r("shorts", "Shorts", 9, 16), r("post", "Post", 1, 1),
        ),
    )

    val instagram = all.first { it.id == "instagram" }
}

// swift rounded() behaviour
private fun rounded(value: Float) = if (value >= 0) floor(value + 0.5f) else -floor(-value + 0.5f)

/** dt-ios SocialCropperViewModel: the largest crop of the ratio, its centre clamped inside the image, in image pixels. */
data class SocialCrop(
    val imageSize: Size = Size.Zero,
    val platform: CropPlatform = CropPresets.instagram,
    val ratio: CropRatio = CropPresets.instagram.ratios[0],
    val centre: Offset = Offset.Zero,
) {
    val rect get() = crop(ratio.aspectRatio.toFloat(), imageSize, centre)

    private fun clamped() = copy(centre = rect.center)

    fun withImage(size: Size) = copy(imageSize = size, centre = size.center)

    /** Keeps the same-named ratio. */
    fun select(platform: CropPlatform) =
        copy(platform = platform, ratio = platform.ratios.firstOrNull { it.name == ratio.name } ?: platform.ratios[0]).clamped()

    fun select(ratio: CropRatio) = copy(ratio = ratio).clamped()

    /** [delta] in the view's units, over a picture [viewSize] across. */
    fun dragged(delta: Offset, viewSize: Size): SocialCrop {
        if (viewSize.width <= 0f) return this
        val scale = imageSize.width / viewSize.width
        return copy(centre = Offset(centre.x + delta.x * scale, centre.y + delta.y * scale)).clamped()
    }

    companion object {
        /** Largest crop, clamped centre; cropping rounds fractions outward. */
        fun crop(aspect: Float, size: Size, centre: Offset): Rect {
            if (size.width <= 0f || size.height <= 0f || aspect <= 0f) return Rect.Zero
            val width = rounded(min(size.width, size.height * aspect))
            val height = rounded(min(size.height, width / aspect))
            val x = min(max(rounded(centre.x - width / 2), 0f), size.width - width)
            val y = min(max(rounded(centre.y - height / 2), 0f), size.height - height)
            return Rect(Offset(x, y), Size(width, height))
        }
    }
}
