package com.delphi.delphitools.tools.socialmedia

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.min

/** dt-ios WatermarkerViewModel.WatermarkPosition, in reading order; the labels are PDFStampEngine.Anchor's. */
enum class WatermarkPosition(val label: String) {
    TopLeft("Top left"),
    TopCentre("Top centre"),
    TopRight("Top right"),
    MiddleLeft("Middle left"),
    Centre("Centre"),
    MiddleRight("Middle right"),
    BottomLeft("Bottom left"),
    BottomCentre("Bottom centre"),
    BottomRight("Bottom right");

    /** 10, 50, 90 % across and down. */
    val alignment get() = Offset(spots[ordinal % 3], spots[ordinal / 3])

    private companion object {
        val spots = floatArrayOf(0.1f, 0.5f, 0.9f)
    }
}

enum class WatermarkKind(val label: String) { Text("Text"), Image("Image") }

object WatermarkEngine {
    fun anchor(position: WatermarkPosition, rect: Rect): Offset {
        val share = position.alignment
        return Offset(rect.left + rect.width * share.x, rect.top + rect.height * share.y)
    }

    /** Thirds of [rect]; a point outside takes the nearest. */
    fun zone(point: Offset, rect: Rect): WatermarkPosition? {
        if (rect.width <= 0f || rect.height <= 0f) return null
        val column = ((point.x - rect.left) / rect.width * 3).toInt().coerceIn(0, 2)
        val row = ((point.y - rect.top) / rect.height * 3).toInt().coerceIn(0, 2)
        return WatermarkPosition.entries[row * 3 + column]
    }

    /** A stamp image's drawn size: at most [share] of the canvas's short side, never enlarged. */
    fun stampSize(stamp: Size, canvas: Size, share: Float): Size {
        val most = min(canvas.width, canvas.height) * share
        val scale = minOf(most / stamp.width, most / stamp.height, 1f)
        return Size(stamp.width * scale, stamp.height * scale)
    }
}
