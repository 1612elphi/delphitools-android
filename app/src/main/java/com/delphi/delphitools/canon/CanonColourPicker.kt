package com.delphi.delphitools.canon

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.fromHsb
import com.delphi.delphitools.colour.hsb
import com.delphi.delphitools.colour.hueRgb
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Hue, saturation and brightness picker, standing in for UIColorPickerViewController:
 * every change goes to `onColour` at once, as the system picker's continuous updates.
 */
@Composable
internal fun CanonColourPickerDialog(initial: Rgb, onColour: (Rgb) -> Unit, onDismiss: () -> Unit) {
    // ponytail: no eyedropper, grid, sliders or alpha of the system picker
    val c = LocalDelphiColours.current
    var hsb by remember { mutableStateOf(initial.hsb()) }
    val send by rememberUpdatedState(onColour)
    fun set(h: Double = hsb[0], s: Double = hsb[1], b: Double = hsb[2]) {
        hsb = doubleArrayOf(h, s.coerceIn(0.0, 1.0), b.coerceIn(0.0, 1.0))
        send(fromHsb(hsb[0], hsb[1], hsb[2]))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnBackPress = false)) {
        val back = rememberPickerBack(onDismiss)
        Column(
            Modifier.width(320.dp).then(back).background(c.border).padding(CanonMetrics.frame),
            verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
        ) {
            CanonHeading("Colour")
            val pure = hueRgb(hsb[0] / 360).toCanonColor()
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .semantics { contentDescription = "Saturation" }
                    .pointerInput(Unit) {
                        fun at(p: Offset) = set(s = p.x / size.width.toDouble(), b = 1 - p.y / size.height.toDouble())
                        detectTapGestures { at(it) }
                    }
                    .pointerInput(Unit) {
                        fun at(p: Offset) = set(s = p.x / size.width.toDouble(), b = 1 - p.y / size.height.toDouble())
                        detectDragGestures(onDragStart = { at(it) }) { change, _ -> at(change.position) }
                    },
            ) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val at = Offset((hsb[1] * size.width).toFloat(), ((1 - hsb[2]) * size.height).toFloat())
                val side = 18.dp.toPx()
                drawRect(Color.White, at - Offset(side / 2, side / 2), Size(side, side), style = Stroke(3.dp.toPx()))
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(CanonMetrics.controlHeight)
                    .semantics {
                        contentDescription = "Hue"
                        progressBarRangeInfo = ProgressBarRangeInfo(hsb[0].toFloat(), 0f..360f)
                        setProgress { set(h = it.toDouble()); true }
                    }
                    .pointerInput(Unit) {
                        fun at(p: Offset) = set(h = (p.x / size.width * 360.0).coerceIn(0.0, 359.99))
                        detectTapGestures { at(it) }
                    }
                    .pointerInput(Unit) {
                        fun at(p: Offset) = set(h = (p.x / size.width * 360.0).coerceIn(0.0, 359.99))
                        detectDragGestures(onDragStart = { at(it) }) { change, _ -> at(change.position) }
                    },
            ) {
                drawRect(Brush.horizontalGradient((0..6).map { hueRgb(it / 6.0).toCanonColor() }))
                val x = (hsb[0] / 360 * size.width).toFloat()
                drawRect(Color.White, Offset(x - 3.dp.toPx(), 0f), Size(6.dp.toPx(), size.height), style = Stroke(2.dp.toPx()))
            }
            CanonButton("Done", kind = CanonButtonKind.Primary, onClick = onDismiss)
        }
    }
}

/**
 * Follows the back gesture: the sheet shrinks and slides with it, fades out when the
 * gesture commits, and springs back when it is cancelled.
 */
@Composable
private fun rememberPickerBack(onDismiss: () -> Unit): Modifier {
    val progress = remember { Animatable(0f) }
    val gone = remember { Animatable(0f) }
    var fromLeft by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    PredictiveBackHandler { events ->
        try {
            events.collect {
                fromLeft = it.swipeEdge == BackEventCompat.EDGE_LEFT
                progress.snapTo(it.progress)
            }
            gone.animateTo(1f, tween(120))
            dismiss()
        } catch (cancelled: CancellationException) {
            scope.launch { progress.animateTo(0f, spring(dampingRatio = 0.8f)) }
            throw cancelled
        }
    }
    return Modifier.graphicsLayer {
        val p = progress.value
        val scale = 1f - 0.1f * p
        scaleX = scale
        scaleY = scale
        translationX = (if (fromLeft) 1f else -1f) * 24.dp.toPx() * p
        alpha = 1f - gone.value
    }
}
