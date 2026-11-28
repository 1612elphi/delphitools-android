package com.delphi.delphitools.widgets

import android.content.Context
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextUtils
import com.delphi.delphitools.canon.colourNames
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.UserPalettes
import com.delphi.delphitools.colour.fromOklch
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.toArgb
import com.delphi.delphitools.shell.ShellStores
import com.delphi.delphitools.shell.Tool
import com.delphi.delphitools.shell.shellPreferences
import com.delphi.delphitools.tools.devtools.SCRATCHPAD_KEY
import com.delphi.delphitools.shell.greatestHits
import com.delphi.delphitools.shell.toolWithId
import com.delphi.delphitools.shell.tools
import com.delphi.delphitools.tools.devtools.QrCorrection
import com.delphi.delphitools.tools.devtools.QrEngine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// room for the shadow
private const val MARGIN = 6f
private const val PERFORATION_PITCH = 8f
private const val STAMP_WIDTH = 76f
private const val STAMP_HEIGHT = 84f
private const val RULE = 18f
private const val FAN_HALF_ANGLE = 46.0
private const val MAX_CHIPS = 7

/** dt-ios dayHash, DayColour and RecentPaletteEntry.generated: the same colour and palette on the same date as the iOS widgets. */
internal object Daily {
    private val schemes = listOf(
        listOf(0, 20, 40, 180, 200), listOf(0, 120, 240, 30, 150), listOf(0, 150, 210, 30, -30), listOf(0, 180, 15, 195, 0), listOf(0, 10, 20, 30, 40),
    )

    fun splitmix(value: ULong): ULong {
        var x = (value xor (value shr 30)) * 0xbf58476d1ce4e5b9uL
        x = (x xor (x shr 27)) * 0x94d049bb133111ebuL
        return x xor (x shr 31)
    }

    fun hash(date: LocalDate): ULong {
        var hash = 5381uL
        for (byte in date.format(DateTimeFormatter.BASIC_ISO_DATE).toByteArray()) hash = hash * 33uL + byte.toULong()
        return splitmix(hash)
    }

    /** Lightness, chroma and hue in OKLCH. */
    fun oklch(date: LocalDate): DoubleArray {
        val hash = hash(date)
        return doubleArrayOf(
            0.45 + (hash % 1000uL).toDouble() / 1000 * 0.40,
            0.08 + ((hash shr 10) % 1000uL).toDouble() / 1000 * 0.10,
            ((hash shr 20) % 3600uL).toDouble() / 10,
        )
    }

    fun colour(date: LocalDate): Rgb = oklch(date).let { fromOklch(it[0], it[1], it[2]).clamped }

    // ios permanentKirkKey
    private const val KIRK = "permanentCaptainKirk"
    val kirk = Rgb(0x9B / 255.0, 0x87 / 255.0, 0x0C / 255.0)

    fun isKirk(context: Context) = widgetPrefs(context).getBoolean(KIRK, false)

    fun setKirk(context: Context, on: Boolean) {
        widgetPrefs(context).edit().putBoolean(KIRK, on).apply()
        refreshWidgets(context, ColourOfDayWidget::class.java)
    }

    fun today(context: Context): Rgb = if (isKirk(context)) kirk else colour(LocalDate.now())

    fun palette(date: LocalDate): List<Rgb> {
        var state = hash(date)
        fun next(): Double {
            state = splitmix(state + 0x9e3779b97f4a7c15uL)
            return (state shr 11).toDouble() / (1L shl 53)
        }
        val hue = next() * 360
        val scheme = schemes[(next() * schemes.size).toInt()]
        val chroma = 0.05 + next() * 0.11
        return scheme.mapIndexed { index, offset ->
            val lightness = 0.3 + index * 0.145 + (next() - 0.5) * 0.06
            // srgb gamut narrows
            val fade = 1 - abs(lightness - 0.6) * 1.2
            fromOklch(lightness, chroma * fade * (0.8 + next() * 0.4), hue + offset).clamped
        }
    }
}

/** A sheet of [columns] by [rows] stamps filling [bounds], perforated along every stamp edge. */
private fun Painter.sheet(bounds: RectF, columns: Int, rows: Int) = layer {
    canvas.drawRect(bounds, shadowed(c.card))
    fun perforate(x0: Float, y0: Float, x1: Float, y1: Float) {
        val holes = max(2, ((abs(x1 - x0) + abs(y1 - y0)) / (PERFORATION_PITCH * dp)).roundToInt())
        for (index in 0..holes) punch(x0 + (x1 - x0) * index / holes, y0 + (y1 - y0) * index / holes, 2.2f * dp)
    }
    for (column in 0..columns) (bounds.left + bounds.width() * column / columns).let { perforate(it, bounds.top, it, bounds.bottom) }
    for (row in 0..rows) (bounds.top + bounds.height() * row / rows).let { perforate(bounds.left, it, bounds.right, it) }
}

private fun Painter.stamp(cell: RectF, tool: Tool) {
    val frame = RectF(cell).apply { inset(7 * dp, 7 * dp) }
    canvas.drawRect(frame, stroke(c.border))
    val size = min(28 * dp, min(frame.width(), frame.height()) * 0.42f)
    icon(tool.icon, frame.centerX(), frame.centerY() - 8 * dp, size, c.primary)
    label(tool.name, frame.centerX(), frame.bottom - 8 * dp, frame.width() - 8 * dp, condensed(11f, c.foreground))
}

/** dt-ios QuickLaunchWidget: one tool, as a postage stamp. */
class QuickLaunchWidget : ShapedWidget() {
    override fun Painter.draw(id: Int): Hits {
        val tool = tool(context, id)
        val tall = min(height - 2 * MARGIN * dp, (width - 2 * MARGIN * dp) / 0.82f) * 0.9f
        val bounds = RectF(width / 2 - tall * 0.41f, height / 2 - tall / 2, width / 2 + tall * 0.41f, height / 2 + tall / 2)
        turned(-4f) {
            sheet(bounds, 1, 1)
            stamp(bounds, tool)
        }
        return Hits(openTool(context, tool.id))
    }

    companion object {
        private const val KEY = "tool."

        private fun key(id: Int) = KEY + id

        fun tool(context: Context, id: Int): Tool = widgetPrefs(context).getString(key(id), null)?.let(::toolWithId) ?: toolWithId("qr-genny") ?: tools.first()

        fun set(context: Context, id: Int, tool: Tool) {
            widgetPrefs(context).edit().putString(key(id), tool.id).apply()
            refreshWidgets(context, QuickLaunchWidget::class.java)
        }
    }

    override val prefKey get() = KEY
}

/** dt-ios ToolGridWidget: a sheet of stamps, as many as fit the widget's size. */
class ToolGridWidget : ShapedWidget() {
    override fun Painter.draw(id: Int): Hits {
        val bounds = RectF(MARGIN * dp, MARGIN * dp, width - MARGIN * dp, height - MARGIN * dp)
        val columns = max(1, (bounds.width() / (STAMP_WIDTH * dp)).toInt())
        val rows = max(1, (bounds.height() / (STAMP_HEIGHT * dp)).toInt())
        // ponytail: favourites or the iOS default source; no source picker until one is asked for
        val shown = ShellStores.of(context).favouriteTools.ifEmpty { greatestHits + tools.filter { it !in greatestHits } }.take(columns * rows)
        sheet(bounds, columns, rows)
        shown.forEachIndexed { index, tool ->
            val left = bounds.left + bounds.width() * (index % columns) / columns
            val top = bounds.top + bounds.height() * (index / columns) / rows
            stamp(RectF(left, top, left + bounds.width() / columns, top + bounds.height() / rows), tool)
        }
        return Hits(columns = columns, cells = List(columns * rows) { index -> shown.getOrNull(index)?.let { openTool(context, it.id) } }, inset = MARGIN)
    }
}

/** dt-ios ColourOfTheDayWidget, as a paint chip. */
class ColourOfDayWidget : ShapedWidget() {
    override fun refreshAt(context: Context, ids: IntArray) = nextMidnight()

    override fun Painter.draw(id: Int): Hits {
        val colour = if (preview) Daily.kirk else Daily.today(context)
        val wide = min(width - 2 * MARGIN * dp, (height - 2 * MARGIN * dp) / 1.4f) * 0.9f
        val chip = RectF(width / 2 - wide / 2, height / 2 - wide * 0.7f, width / 2 + wide / 2, height / 2 + wide * 0.7f)
        val corner = 5 * dp
        val split = chip.top + chip.height() * 0.64f
        turned(3f) {
            layer {
                canvas.drawRoundRect(chip, corner, corner, shadowed(c.card))
                canvas.save()
                canvas.clipRect(chip.left, chip.top, chip.right, split)
                canvas.drawRoundRect(chip, corner, corner, flat(colour.toArgb()))
                canvas.restore()
                punch(chip.centerX(), chip.top + 13 * dp, 4.5f * dp)
            }
            val room = chip.width() - 12 * dp
            val name = colourNames(context)?.closest(colour)
            if (name != null) label(name, chip.centerX(), split + 17 * dp, room, condensed(13f, c.foreground, 700))
            label(colour.hex, chip.centerX(), chip.bottom - 8 * dp, room, mono(10f, c.muted))
        }
        return Hits(openTool(context, "colour-converter", colour.hex))
    }
}

/** dt-ios RecentPaletteWidget: the newest saved palette or the day's generated one, as a fan deck. */
class PaletteWidget : ShapedWidget() {
    override fun refreshAt(context: Context, ids: IntArray) = nextMidnight()

    override fun Painter.draw(id: Int): Hits {
        val colours = UserPalettes.of(context).palettes.lastOrNull()?.rgb.orEmpty().ifEmpty { Daily.palette(LocalDate.now()) }.take(MAX_CHIPS)
        val pivotY = height - 30 * dp
        val reach = sin(Math.toRadians(FAN_HALF_ANGLE)).toFloat()
        val long = min(pivotY - MARGIN * dp, (width / 2 - MARGIN * dp) / (reach + 0.17f * cos(Math.toRadians(FAN_HALF_ANGLE)).toFloat()))
        val chip = RectF(width / 2 - long * 0.17f, pivotY - long, width / 2 + long * 0.17f, pivotY + 10 * dp)
        colours.forEachIndexed { index, colour ->
            val turn = if (colours.size == 1) 0.0 else -FAN_HALF_ANGLE + 2 * FAN_HALF_ANGLE * index / (colours.size - 1)
            canvas.save()
            canvas.rotate(turn.toFloat(), width / 2, pivotY)
            canvas.drawRoundRect(chip, 5 * dp, 5 * dp, shadowed(colour.toArgb()))
            canvas.drawRoundRect(chip, 5 * dp, 5 * dp, stroke(c.card, 1.5f))
            canvas.restore()
        }
        canvas.drawCircle(width / 2, pivotY, 4.5f * dp, flat(c.foreground))
        canvas.drawCircle(width / 2, pivotY, 1.5f * dp, flat(c.card))
        // ponytail: taps are equal columns; a chip's lower half belongs to its neighbours
        return Hits(columns = colours.size, cells = colours.map { openTool(context, "colour-converter", it.hex) })
    }
}

/** One QR code from QR Generator, as a luggage tag. Each placed tag holds its own payload. */
class PinnedCodeWidget : ShapedWidget() {
    override fun Painter.draw(id: Int): Hits {
        val payload = if (preview) "delphitools" else widgetPrefs(context).getString(key(id), null)
        val wide = min(width - 2 * MARGIN * dp, (height - 2 * MARGIN * dp - 10 * dp) / 1.36f) * 0.92f
        val tag = RectF(width / 2 - wide / 2, height / 2 - wide * 0.68f + 6 * dp, width / 2 + wide / 2, height / 2 + wide * 0.68f + 6 * dp)
        val cut = wide * 0.2f
        val eyelet = tag.top + cut * 0.6f
        val outline = Path().apply {
            moveTo(tag.left + cut, tag.top)
            lineTo(tag.right - cut, tag.top)
            lineTo(tag.right, tag.top + cut)
            lineTo(tag.right, tag.bottom)
            lineTo(tag.left, tag.bottom)
            lineTo(tag.left, tag.top + cut)
            close()
        }
        turned(-5f) {
            layer {
                canvas.drawPath(outline, shadowed(c.card))
                punch(tag.centerX(), eyelet, 4 * dp)
            }
            canvas.drawCircle(tag.centerX(), eyelet, 6 * dp, stroke(c.border, 1.5f))
            val string = Path().apply {
                moveTo(tag.centerX(), eyelet)
                quadTo(tag.centerX() - 10 * dp, eyelet - 12 * dp, tag.centerX() + 14 * dp, -4 * dp)
            }
            canvas.drawPath(string, stroke(c.muted, 1.5f))
            val side = wide - 20 * dp
            val code = RectF(tag.centerX() - side / 2, tag.top + cut + 6 * dp, tag.centerX() + side / 2, tag.top + cut + 6 * dp + side)
            val matrix = payload?.let { QrEngine.matrix(it, QrCorrection.Medium) }
            if (matrix == null) {
                icon(toolWithId("qr-genny")!!.icon, code.centerX(), code.centerY(), side * 0.5f, c.muted)
            } else {
                // scanners expect dark-on-light
                canvas.drawRect(code, flat(WidgetColours.light.card))
                // iso 18004 quiet zone
                val quiet = 4
                val module = side / (matrix.size + 2 * quiet)
                flat(WidgetColours.light.foreground)
                matrix.forEachIndexed { row, cells ->
                    cells.forEachIndexed { column, on ->
                        val left = code.left + module * (column + quiet)
                        val top = code.top + module * (row + quiet)
                        if (on) canvas.drawRect(left, top, left + module + 0.5f, top + module + 0.5f, fill)
                    }
                }
                label(payload.lineSequence().first(), tag.centerX(), tag.bottom - 7 * dp, wide - 14 * dp, mono(8f, c.muted))
            }
        }
        return Hits(openTool(context, "qr-genny", payload))
    }

    override val prefKey get() = KEY

    companion object {
        private const val KEY = "code."

        private fun key(id: Int) = KEY + id

        fun set(context: Context, id: Int, payload: String) {
            widgetPrefs(context).edit().putString(key(id), payload).apply()
            refreshWidgets(context, PinnedCodeWidget::class.java)
        }
    }
}

/** The Text Scratchpad note on a torn ruled sheet under a strip of tape. */
class ScratchpadWidget : ShapedWidget() {
    override fun Painter.draw(id: Int): Hits {
        val note = if (preview) "" else context.shellPreferences().getString(SCRATCHPAD_KEY, "").orEmpty()
        val page = RectF(MARGIN * dp + 2 * dp, 14 * dp, width - MARGIN * dp - 2 * dp, height - MARGIN * dp - 4 * dp)
        val torn = Path().apply {
            moveTo(page.left, page.top)
            lineTo(page.right, page.top)
            lineTo(page.right, page.bottom)
            var x = page.right
            var tooth = 0
            while (x > page.left) {
                x = max(page.left, x - 7 * dp)
                // fixed, uneven teeth
                lineTo(x, page.bottom - (tooth++ * 37 % 11) * 0.45f * dp)
            }
            close()
        }
        turned(-1.5f) {
            canvas.drawPath(torn, shadowed(c.card))
            canvas.save()
            canvas.clipPath(torn)
            val first = page.top + 30 * dp
            var rule = first
            while (rule < page.bottom) {
                canvas.drawLine(page.left, rule, page.right, rule, stroke(c.border))
                rule += RULE * dp
            }
            val ink = mono(11.5f, c.foreground)
            val lines = ((page.bottom - 8 * dp - first) / (RULE * dp)).toInt() + 1
            val layout = StaticLayout.Builder.obtain(note, 0, note.length, ink, (page.width() - 24 * dp).toInt().coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(RULE * dp - ink.fontMetrics.run { descent - ascent }, 1f)
                .setIncludePad(false)
                .setMaxLines(lines.coerceAtLeast(1))
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            canvas.translate(page.left + 12 * dp, first - RULE * dp + 3 * dp)
            layout.draw(canvas)
            canvas.restore()
        }
        turned(2.5f) {
            canvas.drawRect(width / 2 - 34 * dp, 5 * dp, width / 2 + 34 * dp, 23 * dp, flat(c.primary and 0x00FFFFFF or 0x8C000000.toInt()))
        }
        return Hits(openTool(context, "markdown-writer"))
    }
}
