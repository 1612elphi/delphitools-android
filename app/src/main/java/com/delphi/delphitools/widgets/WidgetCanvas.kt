package com.delphi.delphitools.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import com.delphi.delphitools.MainActivity
import androidx.compose.ui.graphics.toArgb
import com.delphi.delphitools.R
import com.delphi.delphitools.ui.theme.DelphiColours
import com.delphi.delphitools.ui.theme.DelphiDark
import com.delphi.delphitools.ui.theme.DelphiLight
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val REFRESH = "com.delphi.delphitools.widgets.REFRESH"

// limit: 1.5 screens, four bitmaps
private const val SCREEN_SHARE = 0.3

/** dt-ios WidgetColours: the Canon theme, whatever theme the app is set to. */
internal class WidgetColours(theme: DelphiColours) {
    val card = theme.card.toArgb()
    val border = theme.border.toArgb()
    val foreground = theme.foreground.toArgb()
    val muted = theme.mutedForeground.toArgb()
    val primary = theme.primary.toArgb()

    companion object {
        val light = WidgetColours(DelphiLight)
        val dark = WidgetColours(DelphiDark)
    }
}

private object Faces {
    private val made = HashMap<Int, Typeface>()

    fun of(context: Context, font: Int): Typeface = made.getOrPut(font) { context.resources.getFont(font) }
}

/** Where taps go: [whole] for the widget, or [cells] in rows of [columns] [inset] dp inside its bounds. */
internal class Hits(val whole: Intent? = null, val columns: Int = 0, val cells: List<Intent?> = emptyList(), val inset: Float = 0f)

/** One widget bitmap being drawn; [dp] is bitmap pixels per dp. */
/** [preview]: drawn for the widget picker, with fixed sample content. */
internal class Painter(val context: Context, val canvas: Canvas, val width: Float, val height: Float, val dp: Float, val c: WidgetColours, val preview: Boolean = false) {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

    fun shadowed(colour: Int) = fill.apply {
        color = colour
        setShadowLayer(3 * dp, 0f, 1.5f * dp, 0x40000000)
    }

    fun flat(colour: Int) = fill.apply {
        color = colour
        clearShadowLayer()
    }

    fun stroke(colour: Int, width: Float = 1f) = line.apply {
        color = colour
        strokeWidth = width * dp
    }

    /** Cuts through everything drawn in the current layer. */
    fun punch(x: Float, y: Float, radius: Float) = canvas.drawCircle(x, y, radius, hole)

    /** Draws [draw] in a layer, so [punch] leaves the launcher showing through. */
    inline fun layer(draw: () -> Unit) {
        val saved = canvas.saveLayer(null, null)
        draw()
        canvas.restoreToCount(saved)
    }

    inline fun turned(degrees: Float, draw: () -> Unit) {
        canvas.save()
        canvas.rotate(degrees, width / 2, height / 2)
        draw()
        canvas.restore()
    }

    fun condensed(size: Float, colour: Int, weight: Int = 600) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Faces.of(context, R.font.roboto_flex)
        // wdth as type.kt
        fontVariationSettings = "'wght' $weight, 'wdth' 25"
        textSize = size * dp
        color = colour
    }

    fun mono(size: Float, colour: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Faces.of(context, R.font.iosevka_semibold)
        textSize = size * dp
        color = colour
    }

    /** One line centred on [x] with its baseline at [y], shrunk to 70% and then cut to fit [room]. */
    fun label(text: String, x: Float, y: Float, room: Float, paint: TextPaint) {
        val wide = paint.measureText(text)
        if (wide > room) paint.textSize *= (room / wide).coerceAtLeast(0.7f)
        val shown = TextUtils.ellipsize(text, paint, room, TextUtils.TruncateAt.END).toString()
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(shown, x, y, paint)
    }

    fun icon(resource: Int, x: Float, y: Float, size: Float, tint: Int) {
        val drawable = context.getDrawable(resource)?.mutate() ?: return
        drawable.setTint(tint)
        drawable.setBounds((x - size / 2).roundToInt(), (y - size / 2).roundToInt(), (x + size / 2).roundToInt(), (y + size / 2).roundToInt())
        drawable.draw(canvas)
    }
}

internal fun openTool(context: Context, tool: String, text: String? = null): Intent {
    val link = Uri.Builder().scheme("delphitools").authority("open").appendQueryParameter("tool", tool)
    if (text != null) link.appendQueryParameter("text", text)
    return Intent(Intent.ACTION_VIEW, link.build(), context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

internal fun pending(context: Context, intent: Intent): PendingIntent =
    PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

internal fun widgetPrefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

internal fun nextMidnight(): Long = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Redraws every placed widget of [providers]. */
internal fun refreshWidgets(context: Context, vararg providers: Class<out ShapedWidget>) =
    providers.forEach { context.sendBroadcast(Intent(REFRESH).setClass(context, it)) }

/**
 * A home screen widget drawn as one bitmap with alpha, so its outline is whatever [draw] paints. The launcher gets a bitmap
 * for each orientation, in light and dark.
 */
abstract class ShapedWidget : AppWidgetProvider() {
    internal abstract fun Painter.draw(id: Int): Hits

    /** Epoch ms of the next redraw this widget needs, or null. */
    internal open fun refreshAt(context: Context, ids: IntArray): Long? = null

    /** The prefix of this widget's per-id key in [widgetPrefs], removed with the widget. */
    internal open val prefKey: String? get() = null

    override fun onDeleted(context: Context, ids: IntArray) {
        val prefix = prefKey ?: return
        widgetPrefs(context).edit().apply { ids.forEach { remove(prefix + it) } }.apply()
    }

    /** The widget as the picker's preview image shows it. */
    internal fun preview(context: Context, widthDp: Int, heightDp: Int): Bitmap {
        val dp = context.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap((widthDp * dp).roundToInt(), (heightDp * dp).roundToInt(), Bitmap.Config.ARGB_8888)
        Painter(context, Canvas(bitmap), bitmap.width.toFloat(), bitmap.height.toFloat(), dp, WidgetColours.light, preview = true).draw(AppWidgetManager.INVALID_APPWIDGET_ID)
        return bitmap
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != REFRESH) return super.onReceive(context, intent)
        val manager = AppWidgetManager.getInstance(context)
        onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context, javaClass)))
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
        // ponytail: a time zone change is not followed until the next redraw; inexact, can run minutes late
        // alarm covers every id
        val at = manager.getAppWidgetIds(ComponentName(context, javaClass)).takeIf { it.isNotEmpty() }?.let { refreshAt(context, it) } ?: return
        val refresh = PendingIntent.getBroadcast(context, 0, Intent(REFRESH).setClass(context, javaClass), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, at, refresh)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = update(context, manager, id)

    private fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val options = manager.getAppWidgetOptions(id)
        fun size(key: String) = options.getInt(key).takeIf { it > 0 } ?: 160
        val landscape = views(context, id, size(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH), size(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT))
        val portrait = views(context, id, size(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH), size(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT))
        manager.updateAppWidget(id, RemoteViews(landscape, portrait))
    }

    /** The layout the bitmap goes in; it holds widget_root, widget_image and widget_hits. */
    internal open val layout get() = R.layout.widget

    /** Views of [layout] beside the bitmap. */
    internal open fun decorate(context: Context, views: RemoteViews, id: Int) = Unit

    /** The bitmap's size in dp for a widget of this size. */
    internal open fun canvas(widthDp: Int, heightDp: Int) = widthDp to heightDp

    internal open fun views(context: Context, id: Int, widgetWidthDp: Int, widgetHeightDp: Int): RemoteViews {
        val (widthDp, heightDp) = canvas(widgetWidthDp, widgetHeightDp)
        val metrics = context.resources.displayMetrics
        val full = widthDp.toDouble() * heightDp * metrics.density * metrics.density
        val dp = metrics.density * min(1.0, sqrt(metrics.widthPixels.toDouble() * metrics.heightPixels * SCREEN_SHARE / full)).toFloat()
        lateinit var hits: Hits
        fun render(colours: WidgetColours): Bitmap {
            val bitmap = Bitmap.createBitmap((widthDp * dp).roundToInt(), (heightDp * dp).roundToInt(), Bitmap.Config.ARGB_8888)
            hits = Painter(context, Canvas(bitmap), bitmap.width.toFloat(), bitmap.height.toFloat(), dp, colours).draw(id)
            return bitmap
        }

        val views = RemoteViews(context.packageName, layout)
        decorate(context, views, id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setIcon(R.id.widget_image, "setImageIcon", Icon.createWithBitmap(render(WidgetColours.light)), Icon.createWithBitmap(render(WidgetColours.dark)))
        } else {
            // ponytail: api 30 keeps the theme it was drawn in until the next redraw
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            views.setImageViewBitmap(R.id.widget_image, render(if (night) WidgetColours.dark else WidgetColours.light))
        }
        hits.whole?.let { views.setOnClickPendingIntent(R.id.widget_root, pending(context, it)) }
        if (hits.columns > 0) {
            val inset = (hits.inset * metrics.density).roundToInt()
            views.setViewPadding(R.id.widget_hits, inset, inset, inset, inset)
            hits.cells.chunked(hits.columns).forEach { cells ->
                val row = RemoteViews(context.packageName, R.layout.widget_hit_row)
                // short rows keep columns
                (cells + arrayOfNulls<Intent>(hits.columns - cells.size)).forEach { intent ->
                    val cell = RemoteViews(context.packageName, R.layout.widget_hit)
                    if (intent != null) cell.setOnClickPendingIntent(R.id.widget_hit, pending(context, intent))
                    row.addView(R.id.widget_hit_row, cell)
                }
                views.addView(R.id.widget_hits, row)
            }
        }
        return views
    }
}
