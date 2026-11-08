package com.delphi.delphitools.tools.colour

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonColourControls
import com.delphi.delphitools.canon.CanonColourControlsState
import com.delphi.delphitools.canon.CanonColourSolid
import com.delphi.delphitools.canon.CanonColourSolidState
import com.delphi.delphitools.canon.CanonColourStrip
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonImageCompare
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.toCanonColor
import com.delphi.delphitools.colour.ColourBlindness
import com.delphi.delphitools.colour.LocalColourFormats
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.SolidSpace
import com.delphi.delphitools.colour.formatted
import com.delphi.delphitools.colour.parseHex
import com.delphi.delphitools.colour.simulate
import com.delphi.delphitools.colour.takesBlackInk
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.tools.images.ImageCompressEngine
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private fun simulated(image: Bitmap, type: ColourBlindness): Bitmap {
    val pixels = IntArray(image.width * image.height)
    image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
    val out = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
    out.setPixels(ColourBlindnessImage.simulate(pixels, type), 0, image.width, 0, 0, image.width, image.height)
    return out
}

class ColourBlindnessSimulatorModel : ViewModel() {
    val solid = CanonColourSolidState(SolidSpace.OKLCH, parseHex("E63946")!!)

    // sliced row, oklch hue
    val controls = CanonColourControlsState(solid, activeRow = 2)
    var selected by mutableStateOf(ColourBlindness.Deuteranopia)
        private set
    var mode by mutableIntStateOf(0)
        private set
    var image by mutableStateOf<Bitmap?>(null)
        private set
    val simulations = mutableStateMapOf<ColourBlindness, Bitmap>()
    val thumbnails = mutableStateMapOf<ColourBlindness, ImageBitmap>()
    var processing by mutableStateOf<ColourBlindness?>(null)
        private set
    private val failed = mutableSetOf<ColourBlindness>()

    val isImageMode get() = mode == 1

    init {
        solid.setVision(selected.matrix, animated = false)
    }

    fun choose(type: ColourBlindness) {
        if (type == selected) return
        selected = type
        solid.setVision(type.matrix, animated = true)
        simulateSelected()
    }

    fun select(mode: Int) {
        this.mode = mode
        simulateSelected()
    }

    fun load(picked: ImageBitmap?) {
        val source = picked?.let { ImageCompressEngine.preview(it.asAndroidBitmap(), 2048) }
        image = source
        simulations.clear()
        thumbnails.clear()
        failed.clear()
        processing = null
        if (source == null) return
        viewModelScope.launch {
            val made = withContext(Dispatchers.Default) {
                val small = ImageCompressEngine.preview(source, 160)
                ColourBlindness.entries.associateWith { (if (it == ColourBlindness.Normal) small else simulated(small, it)).asImageBitmap() }
            }
            if (image !== source) return@launch
            thumbnails.putAll(made)
        }
        simulateSelected()
    }

    // cached until image changes
    private fun simulateSelected() {
        val image = image ?: return
        if (!isImageMode || simulations.containsKey(selected) || selected in failed || processing != null) return
        if (selected == ColourBlindness.Normal) {
            simulations[ColourBlindness.Normal] = image
            return
        }
        val type = selected
        processing = type
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { runCatching { simulated(image, type) }.getOrNull() }
            if (this@ColourBlindnessSimulatorModel.image !== image) return@launch
            processing = null
            if (result != null) simulations[type] = result else failed += type
            // selection may have changed
            simulateSelected()
        }
    }

    fun share(context: Context) {
        val result = simulations[selected] ?: return
        val name = selected.displayName.lowercase()
        viewModelScope.launch {
            val data = withContext(Dispatchers.Default) {
                ByteArrayOutputStream().also { result.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            }
            FileExport.share(context, data, "$name.png", "image/png")
        }
    }
}

@Composable
fun ColourBlindnessSimulator(model: ColourBlindnessSimulatorModel = viewModel()) {
    val context = LocalContext.current
    val view = LocalView.current
    val colour = model.solid.colour
    val imageMode = model.isImageMode
    val image = model.image
    val selected = model.selected
    val shown = remember(image) { image?.asImageBitmap() }
    val after = model.simulations[selected]
    val afterShown = remember(after) { after?.asImageBitmap() }

    val canvas = CanonSection.canvas(CanonCanvasSize.Aspect(1f), background = LocalDelphiColours.current.muted, maxHeight = 640.dp) {
        if (imageMode) {
            CanonImageCompare(shown, afterShown, Modifier.fillMaxSize(), beforeTitle = "Original", afterTitle = selected.displayName)
        } else {
            Column(Modifier.fillMaxSize()) {
                CanonColourSolid(model.solid, Modifier.fillMaxWidth().weight(1f).padding(top = LocalCanonCanvas.current.top))
                CanonColourStrip(colour)
            }
        }
        if (imageMode && model.processing != null) CanonLoader(Modifier.align(Alignment.Center))
    }

    val controls = CanonSection.of {
        CanonRows {
            CanonSegmented(listOf("Colour", "Image"), model.mode, onSelect = model::select)
            if (!imageMode) {
                CanonColourControls(model.controls)
            } else if (image == null) {
                CanonDropZone("Drop an image", onImages = { images, _ -> model.load(images.firstOrNull()) })
            } else {
                CanonRow("Image", detail = "${image.width} × ${image.height}", flush = true) {
                    CanonButton("Clear", icon = Icons.Outlined.Delete) { model.load(null) }
                }
                CanonGrid {
                    CanonButton("Share", Modifier.weight(1f).fillMaxHeight(), enabled = after != null, icon = Icons.Outlined.Share) {
                        model.share(context)
                    }
                }
            }
        }
    }

    val types = CanonSection.of {
        CanonRows {
            CanonHeading("Simulation Types")
            ColourBlindness.entries.forEach { type ->
                VisionTypeRow(
                    type,
                    type == selected,
                    if (imageMode) null else colour.simulate(type),
                    if (imageMode) model.thumbnails[type] else null,
                ) {
                    if (type != selected) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    model.choose(type)
                }
            }
        }
    }

    val comparison = CanonSection.of {
        CanonRows {
            CanonHeading("Comparison")
            VisionComparison(colour, colour.simulate(selected), selected)
        }
    }

    CanonSplit(
        sidebar = listOf(canvas),
        main = listOf(controls, types) + if (imageMode) emptyList() else listOf(comparison),
        halves = true,
        collapsesTo = 240.dp,
    )
}

// delphiMono(13) as scaled in CanonText
private val valueMono = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp)

@Composable
private fun VisionTypeRow(type: ColourBlindness, isSelected: Boolean, colour: Rgb?, thumbnail: ImageBitmap?, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    val value = colour?.formatted(LocalColourFormats.current.primary)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = CanonMetrics.rowHeight)
            .canonClickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = type.displayName
                this.selected = isSelected
                if (value != null) stateDescription = value
            },
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        Box(Modifier.width(56.dp).fillMaxHeight().background(colour?.toCanonColor() ?: c.muted)) {
            if (thumbnail != null) Image(thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(if (isSelected) c.accentBg else c.card)
                .padding(horizontal = CanonMetrics.panelPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    type.displayName,
                    style = if (isSelected) CanonText.bodySemibold else CanonText.body,
                    color = if (isSelected) c.primary else c.foreground,
                )
                Text(type.shortDescription, style = CanonText.caption, color = c.mutedForeground)
            }
            if (value != null) Text(value, style = valueMono, color = c.mutedForeground, maxLines = 1)
        }
    }
}

@Composable
private fun VisionComparison(original: Rgb, simulated: Rgb, type: ColourBlindness) {
    var shownType by remember { mutableStateOf(type) }
    // drags follow at once
    val animated = shownType != type
    SideEffect { shownType = type }
    Row(
        Modifier.fillMaxWidth().height(132.dp),
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        ComparisonPane("Original", original, animated, Modifier.weight(1f))
        ComparisonPane(type.displayName, simulated, animated, Modifier.weight(1f))
    }
}

private val paneTitle = CanonText.subheadline.copy(fontWeight = FontWeight.SemiBold)

@Composable
private fun ComparisonPane(name: String, colour: Rgb, animated: Boolean, modifier: Modifier) {
    val value = colour.formatted(LocalColourFormats.current.primary)
    val spec = if (animated) tween<Color>(300) else snap()
    val ground by animateColorAsState(colour.toCanonColor(), spec)
    val ink by animateColorAsState(if (colour.takesBlackInk) Color.Black else Color.White, spec)
    Box(
        modifier
            .fillMaxHeight()
            .background(ground)
            .semantics(mergeDescendants = true) {
                contentDescription = name
                stateDescription = value
            }
            .padding(horizontal = CanonMetrics.panelPadding, vertical = 12.dp),
    ) {
        Text(name, Modifier.align(Alignment.TopStart), style = paneTitle, color = ink, maxLines = 1)
        Text(value, Modifier.align(Alignment.BottomStart), style = valueMono, color = ink, maxLines = 1)
    }
}
