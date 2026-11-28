package com.delphi.delphitools.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.core.graphics.ColorUtils
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.shell.PaneBar
import com.delphi.delphitools.tools.calculators.TimeCalcZones
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphitoolsTheme
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private const val HERE = "HERE"
private const val DAY_STARTS = 6
private const val NIGHT_STARTS = 18
private const val WORK_STARTS = 9
private const val WORK_ENDS = 17
private const val RULER_REFRESH_MS = 15 * 60_000L
private const val PASS_WIDTH = 260f
private const val PASS_HEIGHT = 104f
private const val PASS_PART = 110f
private const val MAX_DIAL = 180f

internal class City(val code: String, val name: String, val zone: String)

/** The away city of each placed clock, and the facts the clocks draw from two zones. */
internal object WorldClock {
    val cities = listOf(
        City("HNL", "Honolulu", "Pacific/Honolulu"), City("ANC", "Anchorage", "America/Anchorage"), City("LAX", "Los Angeles", "America/Los_Angeles"),
        City("DEN", "Denver", "America/Denver"), City("MEX", "Mexico City", "America/Mexico_City"), City("CHI", "Chicago", "America/Chicago"),
        City("NYC", "New York", "America/New_York"), City("TOR", "Toronto", "America/Toronto"), City("BOG", "Bogotá", "America/Bogota"),
        City("SAO", "São Paulo", "America/Sao_Paulo"), City("BUE", "Buenos Aires", "America/Argentina/Buenos_Aires"), City("REK", "Reykjavík", "Atlantic/Reykjavik"),
        City("LIS", "Lisbon", "Europe/Lisbon"), City("LON", "London", "Europe/London"), City("DUB", "Dublin", "Europe/Dublin"),
        City("PAR", "Paris", "Europe/Paris"), City("BER", "Berlin", "Europe/Berlin"), City("AMS", "Amsterdam", "Europe/Amsterdam"),
        City("MAD", "Madrid", "Europe/Madrid"), City("ROM", "Rome", "Europe/Rome"), City("STO", "Stockholm", "Europe/Stockholm"),
        City("HEL", "Helsinki", "Europe/Helsinki"), City("ATH", "Athens", "Europe/Athens"), City("IST", "Istanbul", "Europe/Istanbul"),
        City("CAI", "Cairo", "Africa/Cairo"), City("LOS", "Lagos", "Africa/Lagos"), City("NBO", "Nairobi", "Africa/Nairobi"),
        City("JNB", "Johannesburg", "Africa/Johannesburg"), City("DXB", "Dubai", "Asia/Dubai"), City("KHI", "Karachi", "Asia/Karachi"),
        City("DEL", "Delhi", "Asia/Kolkata"), City("DAC", "Dhaka", "Asia/Dhaka"), City("BKK", "Bangkok", "Asia/Bangkok"),
        City("SIN", "Singapore", "Asia/Singapore"), City("HKG", "Hong Kong", "Asia/Hong_Kong"), City("SHA", "Shanghai", "Asia/Shanghai"),
        City("SEL", "Seoul", "Asia/Seoul"), City("TYO", "Tokyo", "Asia/Tokyo"), City("PER", "Perth", "Australia/Perth"),
        City("SYD", "Sydney", "Australia/Sydney"), City("AKL", "Auckland", "Pacific/Auckland"), City("UTC", "UTC", "UTC"),
    )

    const val KEY = "city."

    private fun key(id: Int) = KEY + id

    fun away(context: Context, id: Int): City {
        val code = widgetPrefs(context).getString(key(id), null)
        return cities.firstOrNull { it.code == code } ?: cities.first { it.code == "TYO" }
    }

    fun set(context: Context, id: Int, city: City) {
        widgetPrefs(context).edit().putString(key(id), city.code).apply()
        refreshWidgets(context, TwoDialsWidget::class.java, BoardingPassWidget::class.java, SplitFlapWidget::class.java, OverlapRulerWidget::class.java)
    }

    fun isNight(zone: ZoneId, now: Instant = Instant.now()) = now.atZone(zone).hour.let { it < DAY_STARTS || it >= NIGHT_STARTS }

    /** Minutes [zone] is ahead of [here]. */
    fun offsetMinutes(zone: ZoneId, here: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()) =
        (zone.rules.getOffset(now).totalSeconds - here.rules.getOffset(now).totalSeconds) / 60

    /** The offset in hours as one flap shows it: `+8`, `-4`, `+5½`. */
    fun offsetLabel(minutes: Int): String {
        val part = when (abs(minutes) % 60) {
            15 -> "¼"
            30 -> "½"
            45 -> "¾"
            else -> ""
        }
        return (if (minutes < 0) "-" else "+") + abs(minutes) / 60 + part
    }

    /** When a clock's drawn parts next change: the next 06:00, 18:00 or offset change in either zone. */
    fun nextChange(zones: List<ZoneId>, now: Instant = Instant.now()): Long = zones.minOf { zone ->
        val local = now.atZone(zone)
        val turns = listOf(local.withHour(DAY_STARTS), local.withHour(NIGHT_STARTS), local.plusDays(1).withHour(DAY_STARTS))
            .map { it.withMinute(0).withSecond(0).withNano(0).toInstant() }
        (turns + listOfNotNull(zone.rules.nextTransition(now)?.instant)).filter { it > now }.min().toEpochMilli()
    }

    fun hereName(): String = TimeCalcZones.city(ZoneId.systemDefault().id)

    fun palette(zone: ZoneId) = if (isNight(zone)) WidgetColours.dark else WidgetColours.light

    fun open(context: Context): Intent = openTool(context, "time-calc")
}

/** A clock for here and one away city. The system's clock views keep the time; the app redraws what else changes. */
abstract class WorldClockWidget : ShapedWidget() {
    override fun Painter.draw(id: Int) = Hits(WorldClock.open(context))

    override fun refreshAt(context: Context, ids: IntArray): Long? =
        WorldClock.nextChange(ids.map { ZoneId.of(WorldClock.away(context, it).zone) } + ZoneId.systemDefault())

    override val prefKey get() = WorldClock.KEY
}

/** Two overlapping analogue dials, the larger one for here. A dial is dark while it is night in its city. */
@RequiresApi(Build.VERSION_CODES.S)
class TwoDialsWidget : WorldClockWidget() {
    override fun views(context: Context, id: Int, widthDp: Int, heightDp: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_dials)
        val city = WorldClock.away(context, id)
        // remoteviews bitmap limit
        val big = min(heightDp - 4f, (widthDp - 4f) / 1.45f).coerceIn(60f, MAX_DIAL)
        val small = big * 0.66f
        fun size(view: Int, wide: Float, tall: Float) {
            views.setViewLayoutWidth(view, wide, TypedValue.COMPLEX_UNIT_DIP)
            views.setViewLayoutHeight(view, tall, TypedValue.COMPLEX_UNIT_DIP)
        }
        size(R.id.dials_box, big * 1.45f, big)
        size(R.id.dial_a, big, big)
        size(R.id.dial_b, small, small)
        dial(context, views, R.id.dial_a, big, HERE, ZoneId.systemDefault())
        dial(context, views, R.id.dial_b, small, city.code, ZoneId.of(city.zone))
        views.setOnClickPendingIntent(R.id.widget_root, pending(context, WorldClock.open(context)))
        return views
    }

    private fun dial(context: Context, views: RemoteViews, view: Int, sizeDp: Float, label: String, zone: ZoneId) {
        val (face, hour, minute) = layers(context, sizeDp, label, WorldClock.palette(zone))
        views.setIcon(view, "setDial", Icon.createWithBitmap(face))
        views.setIcon(view, "setHourHand", Icon.createWithBitmap(hour))
        views.setIcon(view, "setMinuteHand", Icon.createWithBitmap(minute))
        views.setString(view, "setTimeZone", zone.id)
    }

    /** The dial, the hour hand and the minute hand, each the dial's size with the hand pointing up. */
    private fun layers(context: Context, sizeDp: Float, label: String, colours: WidgetColours): List<Bitmap> {
        val density = context.resources.displayMetrics.density
        val side = (sizeDp * density).roundToInt()
        val radius = side / 2f - 5 * density
        fun layer(draw: Painter.() -> Unit): Bitmap {
            val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            Painter(context, Canvas(bitmap), side.toFloat(), side.toFloat(), density, colours).draw()
            return bitmap
        }
        // view turns the hand
        fun Painter.hand(reach: Float, wide: Float) =
            canvas.drawLine(width / 2, height / 2, width / 2, height / 2 - radius * reach, stroke(c.foreground, wide).apply { strokeCap = Paint.Cap.ROUND })
        return listOf(
            layer {
                canvas.drawCircle(width / 2, height / 2, radius, shadowed(c.card))
                for (tick in 0 until 12) {
                    val long = tick % 3 == 0
                    canvas.save()
                    canvas.rotate(tick * 30f, width / 2, height / 2)
                    canvas.drawLine(width / 2, height / 2 - radius * 0.9f, width / 2, height / 2 - radius * (if (long) 0.76f else 0.82f), stroke(if (long) c.foreground else c.muted, if (long) 1.6f else 1f))
                    canvas.restore()
                }
                label(label, width / 2, height / 2 + radius * 0.5f, radius, mono(sizeDp * 0.075f, c.foreground))
            },
            layer { hand(0.48f, 3.2f) },
            layer {
                hand(0.72f, 2.2f)
                canvas.drawCircle(width / 2, height / 2, 3 * dp, flat(c.foreground))
            },
        )
    }

    /** Both dials at fixed times, for the picker's preview image. */
    internal fun preview(context: Context): Bitmap {
        val density = context.resources.displayMetrics.density
        val big = 145f
        val bitmap = Bitmap.createBitmap((big * 1.45f * density).roundToInt(), (big * density).roundToInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        fun place(sizeDp: Float, label: String, colours: WidgetColours, x: Float, y: Float, hourTurn: Float, minuteTurn: Float) {
            val (face, hour, minute) = layers(context, sizeDp, label, colours)
            canvas.drawBitmap(face, x, y, null)
            listOf(hour to hourTurn, minute to minuteTurn).forEach { (hand, turn) ->
                canvas.save()
                canvas.rotate(turn, x + face.width / 2f, y + face.height / 2f)
                canvas.drawBitmap(hand, x, y, null)
                canvas.restore()
            }
        }
        place(big, HERE, WidgetColours.light, 0f, 0f, 305f, 60f)
        val small = big * 0.66f
        place(small, "TYO", WidgetColours.dark, bitmap.width - small * density, bitmap.height - small * density, 95f, 60f)
        return bitmap
    }
}

/** A ticket with a tear-off stub: here on the main part, the away city on the stub, the offset on the perforation. */
class BoardingPassWidget : WorldClockWidget() {
    override val layout get() = R.layout.widget_pass

    // fixed size, no scaling
    override fun canvas(widthDp: Int, heightDp: Int) = min(widthDp, (PASS_WIDTH + 20).toInt()) to min(heightDp, (PASS_HEIGHT + 20).toInt())

    override fun Painter.draw(id: Int): Hits {
        val city = WorldClock.away(context, id)
        val zone = ZoneId.of(city.zone)
        val pass = RectF(width / 2 - PASS_WIDTH / 2 * dp, height / 2 - PASS_HEIGHT / 2 * dp, width / 2 + PASS_WIDTH / 2 * dp, height / 2 + PASS_HEIGHT / 2 * dp)
        val outline = Path().apply { addRoundRect(pass, 8 * dp, 8 * dp, Path.Direction.CW) }
        val mainEnd = pass.left + PASS_PART * dp
        val stubStart = pass.right - PASS_PART * dp
        fun part(left: Float, right: Float, name: String, c: WidgetColours) {
            canvas.save()
            canvas.clipPath(outline)
            canvas.drawRect(left, pass.top, right, pass.bottom, flat(c.card))
            canvas.restore()
            val kicker = condensed(10f, c.muted).apply { letterSpacing = 0.14f }
            canvas.drawText(name.uppercase(), left + 14 * dp, pass.top + 24 * dp, kicker)
            if (preview) canvas.drawText(if (c === WidgetColours.light) "10:10" else "18:10", left + 14 * dp, pass.top + 62 * dp, condensed(32f, c.foreground, 700))
        }
        layer {
            canvas.drawPath(outline, shadowed(WidgetColours.light.card))
            part(pass.left, mainEnd, if (preview) HERE else WorldClock.hereName(), if (preview) WidgetColours.light else WorldClock.palette(ZoneId.systemDefault()))
            part(stubStart, pass.right, city.name, if (preview) WidgetColours.dark else WorldClock.palette(zone))
            punch(pass.centerX(), pass.top, 7 * dp)
            punch(pass.centerX(), pass.bottom, 7 * dp)
        }
        val tear = stroke(WidgetColours.light.border, 1.5f).apply { pathEffect = DashPathEffect(floatArrayOf(4 * dp, 4 * dp), 0f) }
        canvas.drawLine(pass.centerX(), pass.top + 12 * dp, pass.centerX(), pass.centerY() - 12 * dp, tear)
        canvas.drawLine(pass.centerX(), pass.centerY() + 12 * dp, pass.centerX(), pass.bottom - 12 * dp, tear)
        tear.pathEffect = null
        label(WorldClock.offsetLabel(WorldClock.offsetMinutes(zone)) + " h", pass.centerX(), pass.centerY() + 4 * dp, 36 * dp, mono(10f, WidgetColours.light.foreground))
        return Hits(WorldClock.open(context))
    }

    override fun decorate(context: Context, views: RemoteViews, id: Int) {
        val zone = ZoneId.of(WorldClock.away(context, id).zone)
        fun clock(time: Int, date: Int, of: ZoneId) {
            val c = WorldClock.palette(of)
            views.setString(time, "setTimeZone", of.id)
            views.setString(date, "setTimeZone", of.id)
            views.setTextColor(time, c.foreground)
            views.setTextColor(date, c.muted)
        }
        clock(R.id.pass_a_time, R.id.pass_a_date, ZoneId.systemDefault())
        clock(R.id.pass_b_time, R.id.pass_b_date, zone)
    }
}

/** A departure board, one row per city. The away row starts with its offset on one flap, so both rows are four flaps wide. */
class SplitFlapWidget : WorldClockWidget() {
    override fun views(context: Context, id: Int, widthDp: Int, heightDp: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_flaps)
        val city = WorldClock.away(context, id)
        val offset = WorldClock.offsetLabel(WorldClock.offsetMinutes(ZoneId.of(city.zone)))
        listOf(R.id.flap_a1, R.id.flap_a2, R.id.flap_a3, R.id.flap_a4).forEachIndexed { index, flap -> views.setTextViewText(flap, HERE[index].toString()) }
        views.setTextViewText(R.id.flap_b1, offset)
        // several characters, one flap
        views.setTextViewTextSize(R.id.flap_b1, TypedValue.COMPLEX_UNIT_DIP, if (offset.length > 2) 8f else 11f)
        listOf(R.id.flap_b2, R.id.flap_b3, R.id.flap_b4).forEachIndexed { index, flap -> views.setTextViewText(flap, city.code[index].toString()) }
        views.setString(R.id.flap_b_h, "setTimeZone", city.zone)
        views.setString(R.id.flap_b_m, "setTimeZone", city.zone)
        views.setOnClickPendingIntent(R.id.widget_root, pending(context, WorldClock.open(context)))
        return views
    }
}

/** Two 24-hour rulers, the away one slid by the offset. Working hours are tinted, the hours both share are solid, a line marks now. */
class OverlapRulerWidget : WorldClockWidget() {
    override fun refreshAt(context: Context, ids: IntArray): Long = System.currentTimeMillis() + RULER_REFRESH_MS

    override fun Painter.draw(id: Int): Hits {
        val city = WorldClock.away(context, id)
        val now = Instant.now()
        val offset = WorldClock.offsetMinutes(ZoneId.of(city.zone), now = now)
        val tall = min(40 * dp, (height - 22 * dp) / 2)
        val tab = 38 * dp
        val left = 6 * dp
        val scale = RectF(left + tab, 0f, width - 6 * dp, 0f)
        val tint = ColorUtils.blendARGB(c.card, c.primary, 0.22f)
        fun works(minuteOfDay: Int) = Math.floorMod(minuteOfDay, 1440) / 60 in WORK_STARTS until WORK_ENDS

        // shift: midnight offset, minutes
        fun ruler(top: Float, name: String, shift: Int, otherAhead: Int) {
            canvas.drawRect(left, top, scale.right, top + tall, shadowed(c.card))
            label(name, left + tab / 2, top + tall / 2 + 4 * dp, tab - 6 * dp, mono(11f, c.foreground))
            canvas.save()
            canvas.clipRect(scale.left, top, scale.right, top + tall)
            val hour = scale.width() / 24
            // a shifted ruler wraps
            for (cell in -24..47) {
                val x = scale.left + hour * (cell + shift / 60f)
                val shown = Math.floorMod(cell, 24)
                val both = works(shown * 60 + 30) && works(shown * 60 + 30 + otherAhead)
                if (works(shown * 60)) canvas.drawRect(x, top, x + hour, top + tall, flat(if (both) c.primary else tint))
                canvas.drawLine(x, top, x, top + tall, stroke(c.border))
                if (shown % 3 == 0) canvas.drawText(shown.toString(), x + 2 * dp, top + 9 * dp, mono(7f, if (both) c.card else c.muted))
            }
            canvas.restore()
        }
        val first = height / 2 - tall - 2.5f * dp
        ruler(first, HERE, 0, offset)
        ruler(first + tall + 5 * dp, city.code, Math.floorMod(-offset, 1440).let { if (it > 720) it - 1440 else it }, -offset)
        val here = now.atZone(ZoneId.systemDefault())
        val x = scale.left + scale.width() * (here.hour * 60 + here.minute) / 1440f
        canvas.drawLine(x, first - 5 * dp, x, first + 2 * tall + 10 * dp, stroke(c.foreground, 2f).apply { strokeCap = Paint.Cap.ROUND })
        line.strokeCap = Paint.Cap.BUTT
        return Hits(WorldClock.open(context))
    }
}

/** Picks the away city of one placed clock. */
class WidgetCityPicker : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // back keeps the city
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        enableEdgeToEdge()
        setContent {
            DelphitoolsTheme {
                val c = LocalDelphiColours.current
                Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
                    PaneBar("City", done = ::finish, doneLabel = "Cancel")
                    LazyColumn {
                        items(WorldClock.cities, key = { it.code }) { city ->
                            CanonRow(
                                city.name,
                                Modifier.canonClickable {
                                    WorldClock.set(this@WidgetCityPicker, id, city)
                                    finish()
                                },
                            ) { Text(city.code, style = CanonText.body, color = c.mutedForeground) }
                        }
                    }
                }
            }
        }
    }
}
