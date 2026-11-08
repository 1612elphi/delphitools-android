package com.delphi.delphitools.demo

import android.content.ClipData
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.canon.CanonBar
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSlider
import com.delphi.delphitools.canon.CanonStepper
import com.delphi.delphitools.canon.CanonSwitch
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val formats = listOf("JPEG", "WebP", "AVIF", "PNG")
private val formatFactor = listOf(1f, 0.75f, 0.55f, 2.2f)
private const val ORIGINAL_KB = 4300

/** Settings and fake result of a mock image-compressor. */
@Stable
private class CompressorState {
    var format by mutableIntStateOf(1)
    var quality by mutableFloatStateOf(82f)
    var strip by mutableStateOf(true)
    var maxWidth by mutableIntStateOf(2048)
    var running by mutableStateOf(false)
    val progress = Animatable(0f)

    val resultKb get() = (ORIGINAL_KB * (0.15f + 0.6f * quality / 100f) * formatFactor[format]).roundToInt()
    val saving get() = ((1f - resultKb.toFloat() / ORIGINAL_KB) * 100).roundToInt()
    val status get() = when {
        running -> "Compressing"
        progress.value >= 1f -> "Done"
        else -> "Ready"
    }
    val values get() = listOf(
        "Original" to "4.2 MB",
        "Compressed" to "$resultKb KB",
        "Saving" to "$saving%",
    )

    suspend fun compress() {
        running = true
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1600, easing = LinearEasing))
        running = false
    }

    suspend fun reset() {
        format = 1
        quality = 82f
        strip = true
        maxWidth = 2048
        progress.snapTo(0f)
    }
}

@Composable
fun CompressorDemo(modifier: Modifier = Modifier) {
    val state = remember { CompressorState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val c = LocalDelphiColours.current
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(CanonMetrics.panelPadding),
        verticalArrangement = Arrangement.spacedBy(CanonMetrics.panelSpacing),
    ) {
        CanonRows {
            CanonHeading("Output")
            CanonSegmented(formats, state.format) { state.format = it }
            CanonRow("Quality", flush = true, controlWeight = 1.4f) {
                CanonSlider(
                    state.quality,
                    { state.quality = it },
                    Modifier.fillMaxSize(),
                    range = 10f..100f,
                    step = 1f,
                    readout = state.quality.roundToInt().toString(),
                )
            }
            CanonRow("Strip metadata") {
                CanonSwitch(state.strip) { state.strip = it }
            }
            CanonRow("Max width", flush = true, controlWeight = 1.4f) {
                CanonStepper(state.maxWidth, { state.maxWidth = it }, "px", Modifier.fillMaxSize(), step = 64)
            }
        }
        CanonRows {
            CanonHeading("Result")
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = CanonMetrics.controlHeight)
                    .background(c.card)
                    .padding(horizontal = CanonMetrics.panelPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(state.status, style = CanonText.body, color = c.foreground, modifier = Modifier.weight(1f))
                CanonBar(state.progress.value, Modifier.width(120.dp))
            }
            state.values.forEach { (label, value) ->
                CanonValueLine(label, value) {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value))) }
                }
            }
            CanonGrid {
                listOf("Copy", "Share", "Save").forEach {
                    CanonButton(it, Modifier.weight(1f).fillMaxHeight()) {}
                }
            }
        }
        CanonRows {
            CanonButton("Compress", kind = CanonButtonKind.Primary) { scope.launch { state.compress() } }
            CanonButton("Reset", kind = CanonButtonKind.Secondary) { scope.launch { state.reset() } }
        }
    }
}
