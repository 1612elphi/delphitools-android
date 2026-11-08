package com.delphi.delphitools.canon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Height
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.launch

// ∑CG: shown over the dimmed original when the result could not be encoded
//   spec: one short sentence, centred in the picture, plain and factual, no blame, under 45 characters
//   sample: "This format couldn't encode this image"
private const val ENCODE_FAILED = "∑CG"

/**
 * One picture: the original on the leading side of a draggable divider, the result on the other.
 * A tap springs the divider there. Without both pictures only the original shows, dimmed when `failed`.
 */
@Composable
fun CanonImageCompare(
    before: ImageBitmap?,
    after: ImageBitmap?,
    modifier: Modifier = Modifier,
    beforeTitle: String = "Before",
    afterTitle: String = "After",
    failed: Boolean = false,
) {
    val c = LocalDelphiColours.current
    val flipped = LocalLayoutDirection.current == LayoutDirection.Rtl
    val comparing = before != null && after != null
    // the original's width fraction
    val split = remember { Animatable(0.5f) }
    val share = split.value
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clipToBounds()
            .then(
                if (!comparing) Modifier else Modifier
                    .pointerInput(flipped) {
                        fun at(x: Float) = (x / size.width).let { if (flipped) 1 - it else it }.coerceIn(0f, 1f)
                        detectTapGestures { scope.launch { split.animateTo(at(it.x), spring(0.7f, 160f)) } }
                    }
                    .pointerInput(flipped) {
                        fun at(x: Float) = (x / size.width).let { if (flipped) 1 - it else it }.coerceIn(0f, 1f)
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            scope.launch { split.snapTo(at(change.position.x)) }
                        }
                    }
                    .semantics {
                        contentDescription = "$beforeTitle, $afterTitle"
                        stateDescription = "${(share * 100).toInt()}%"
                        progressBarRangeInfo = ProgressBarRangeInfo(share, 0f..1f, steps = 9)
                        setProgress { scope.launch { split.snapTo(it.coerceIn(0f, 1f)) }; true }
                    }
            ),
    ) {
        if (before != null) {
            Image(
                before,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().alpha(if (failed) 0.25f else 1f),
            )
        }
        if (failed) {
            Text(
                ENCODE_FAILED,
                style = CanonText.subheadline,
                color = c.mutedForeground,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
        if (before == null || after == null) return@BoxWithConstraints
        val x = maxWidth * (if (flipped) 1 - share else share)
        Image(
            after,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val edge = x.toPx()
                    if (flipped) clipRect(right = edge) { this@drawWithContent.drawContent() }
                    else clipRect(left = edge) { this@drawWithContent.drawContent() }
                },
        )
        Box(
            Modifier
                .absoluteOffset(x = x - 1.dp)
                .width(2.dp)
                .fillMaxHeight()
                .background(c.foreground)
        )
        Box(
            Modifier
                .align(AbsoluteAlignment.CenterLeft)
                .absoluteOffset(x = x - 22.dp)
                .size(44.dp)
                .background(c.card)
                .border(CanonMetrics.frame, c.foreground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Height, contentDescription = null, tint = c.foreground, modifier = Modifier.size(18.dp).rotate(90f))
        }
        CompareTag(beforeTitle, Modifier.align(Alignment.TopStart))
        CompareTag(afterTitle, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun CompareTag(text: String, modifier: Modifier) {
    val c = LocalDelphiColours.current
    Text(
        text,
        style = CanonText.footnoteSemibold,
        color = c.mutedForeground,
        maxLines = 1,
        modifier = modifier
            .padding(10.dp)
            .background(c.card)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
