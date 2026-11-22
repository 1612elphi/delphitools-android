package com.delphi.delphitools.tools.colour

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonToast
import com.delphi.delphitools.canon.MenuCellItem
import com.delphi.delphitools.canon.MenuCellPopup
import com.delphi.delphitools.canon.colourNamesOrNull
import com.delphi.delphitools.colour.ColourFormat
import com.delphi.delphitools.colour.LocalColourFormats
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.formatted
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.hsb
import com.delphi.delphitools.colour.toArgb
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.tools.images.ImageFiles
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** dt-ios PaletteExporter: Adobe Swatch Exchange, Procreate Swatches, CSS custom properties and a palette image. */
object PaletteExporter {
    /** `names` gives each colour's swatch name. */
    fun ase(name: String, colours: List<Rgb>, names: (Rgb) -> String): ByteArray {
        val out = ByteArrayOutputStream()
        fun u16(v: Int) = out.write(ByteBuffer.allocate(2).putShort(v.toShort()).array())
        fun u32(v: Int) = out.write(ByteBuffer.allocate(4).putInt(v).array())
        fun f32(v: Double) = out.write(ByteBuffer.allocate(4).putFloat(v.toFloat()).array())
        fun utf16(text: String): ByteArray {
            // utf-16be, length counts null
            val units = ByteBuffer.allocate(2 + text.length * 2 + 2).putShort((text.length + 1).toShort())
            text.forEach { units.putChar(it) }
            return units.putShort(0).array()
        }
        out.write("ASEF".toByteArray())
        u16(1)
        u16(0)
        u32(2 + colours.size)
        val group = utf16(name)
        u16(0xC001)
        u32(group.size)
        out.write(group)
        colours.forEach { colour ->
            val label = utf16(names(colour))
            u16(0x0001)
            u32(label.size + 4 + 12 + 2)
            out.write(label)
            out.write("RGB ".toByteArray())
            f32(colour.r)
            f32(colour.g)
            f32(colour.b)
            // normal process colour
            u16(2)
        }
        u16(0xC002)
        u32(0)
        return out.toByteArray()
    }

    /** A zip holding Swatches.json, at most 30 colours as Procreate allows. */
    fun procreate(name: String, colours: List<Rgb>): ByteArray {
        val swatches = colours.take(30).joinToString(",") { colour ->
            val (h, s, b) = colour.hsb().toList()
            String.format(Locale.ROOT, "{\"hue\":%s,\"saturation\":%s,\"brightness\":%s,\"alpha\":1,\"colorSpace\":0}", h / 360, s, b)
        }
        val json = "[{\"name\":${jsonString(name)},\"swatches\":[$swatches]}]".toByteArray()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val entry = ZipEntry("Swatches.json").apply {
                method = ZipEntry.STORED
                size = json.size.toLong()
                crc = CRC32().apply { update(json) }.value
            }
            zip.putNextEntry(entry)
            zip.write(json)
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    fun css(name: String, colours: List<Rgb>) =
        (listOf("/* $name */") + colours.mapIndexed { i, c -> "--palette-${i + 1}: ${c.hex};" }).joinToString("\n")

    fun jsonString(text: String) = buildString {
        append('"')
        text.forEach {
            when {
                it == '"' -> append("\\\"")
                it == '\\' -> append("\\\\")
                it < ' ' -> append(String.format(Locale.ROOT, "\\u%04x", it.code))
                else -> append(it)
            }
        }
        append('"')
    }

    /** The palette as swatches over their names and values in two formats, with a line naming the app and palette. */
    fun image(name: String, colours: List<Rgb>, names: (Rgb) -> String, primary: ColourFormat, secondary: ColourFormat, density: Float): Bitmap {
        val unit = density
        val swatch = 120 * unit
        val info = 56 * unit
        val mark = 32 * unit
        val width = maxOf(swatch * colours.size, 400 * unit)
        val bitmap = Bitmap.createBitmap(width.toInt(), (swatch + info + mark).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val fill = Paint()
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10 * unit
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.BLACK
        }
        val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9 * unit
            typeface = Typeface.MONOSPACE
            color = 0x99000000.toInt()
        }
        colours.forEachIndexed { i, colour ->
            val x = i * swatch
            fill.color = colour.toArgb()
            canvas.drawRect(x, 0f, x + swatch, swatch, fill)
            canvas.save()
            canvas.clipRect(x + 4 * unit, swatch, x + swatch - 4 * unit, swatch + info)
            canvas.drawText(names(colour), x + 4 * unit, swatch + 15 * unit, title)
            canvas.drawText(colour.formatted(primary), x + 4 * unit, swatch + 29 * unit, value)
            canvas.drawText(colour.formatted(secondary), x + 4 * unit, swatch + 43 * unit, value)
            canvas.restore()
        }
        val watermark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11 * unit
            color = 0x4D000000
        }
        canvas.drawText("delphitools — $name", 8 * unit, swatch + info + 20 * unit, watermark)
        return bitmap
    }
}

/** The nearest colour name, or the hex where the names are missing. */
@Composable
fun colourNamesOrHex(): (Rgb) -> String {
    val names = colourNamesOrNull()
    return { colour -> names?.closest(colour) ?: colour.hex }
}

/**
 * The palette tools' Export menu: Copy CSS and Copy Image, then `extra`, Export ASE and Export Procreate. The name and
 * colours are read when an item is chosen; `css` replaces the custom properties.
 */
@Composable
fun PaletteExportButton(
    name: () -> String,
    colours: () -> List<Rgb>,
    modifier: Modifier = Modifier,
    css: (() -> String)? = null,
    extra: @Composable (close: () -> Unit) -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val formats = LocalColourFormats.current
    val names = colourNamesOrHex()
    var open by remember { mutableStateOf(false) }
    fun chosen(action: suspend (Context, List<Rgb>) -> Unit) {
        open = false
        val palette = colours()
        if (palette.isEmpty()) return
        scope.launch { action(context, palette) }
    }
    Box(modifier) {
        CanonButton("Export", Modifier.fillMaxSize(), icon = Icons.Outlined.IosShare) { open = true }
        MenuCellPopup(open, { open = false }) {
            MenuCellItem("Copy CSS", icon = Icons.Outlined.Code) {
                chosen { _, palette ->
                    CanonToast.show(view)
                    view.context.getSystemService(android.content.ClipboardManager::class.java)
                        ?.setPrimaryClip(android.content.ClipData.newPlainText("", css?.invoke() ?: PaletteExporter.css(name(), palette)))
                }
            }
            MenuCellItem("Copy Image", icon = Icons.Outlined.Image) {
                chosen { context, palette ->
                    val image = PaletteExporter.image(name(), palette, names, formats.primary, formats.secondary, view.resources.displayMetrics.density)
                    ImageFiles.png(image)?.let { ImageFiles.copyPng(context, it, "${name()}.png") }
                    CanonToast.show(view)
                }
            }
            extra { open = false }
            MenuCellItem("Export ASE", icon = Icons.Outlined.IosShare) {
                chosen { context, palette -> FileExport.share(context, PaletteExporter.ase(name(), palette, names), "${name()}.ase", "application/octet-stream") }
            }
            MenuCellItem("Export Procreate", icon = Icons.Outlined.Brush) {
                chosen { context, palette -> FileExport.share(context, PaletteExporter.procreate(name(), palette), "${name()}.swatches", "application/zip") }
            }
        }
    }
}
