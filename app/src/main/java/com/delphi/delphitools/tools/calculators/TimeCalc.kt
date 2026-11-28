package com.delphi.delphitools.tools.calculators

import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipboardManager
import android.icu.text.DateFormat
import android.icu.text.MeasureFormat
import android.icu.text.NumberFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.R
import com.delphi.delphitools.canon.CanonBigNumberField
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.CanonValueLayout
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.MenuCellPopup
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.canon.pencil
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import com.delphi.delphitools.tools.devtools.JwtEngine
import com.delphi.delphitools.tools.images.WorldMap
import com.delphi.delphitools.tools.typography.PxToRemEngine
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiColours
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DASH = "—"

enum class TimeMode(val title: String) { Timestamp("Timestamp"), Arithmetic("Arithmetic"), Timezone("Timezone") }

private val modes = TimeMode.entries

private class EndOffset(val title: String, val unit: java.time.temporal.ChronoUnit, val value: Long)

private val offsets = listOf(
    EndOffset("−1 day", java.time.temporal.ChronoUnit.DAYS, -1), EndOffset("−1 week", java.time.temporal.ChronoUnit.DAYS, -7),
    EndOffset("−1 month", java.time.temporal.ChronoUnit.MONTHS, -1), EndOffset("−1 year", java.time.temporal.ChronoUnit.YEARS, -1),
    EndOffset("+1 day", java.time.temporal.ChronoUnit.DAYS, 1), EndOffset("+1 week", java.time.temporal.ChronoUnit.DAYS, 7),
    EndOffset("+1 month", java.time.temporal.ChronoUnit.MONTHS, 1), EndOffset("+1 year", java.time.temporal.ChronoUnit.YEARS, 1),
)

/** What the banner shows; times in epoch seconds. */
sealed interface TimeScene {
    data class Epoch(val seconds: Double?) : TimeScene
    data class Span(val a: Double, val b: Double) : TimeScene
    data class Zones(val instant: Double, val from: String, val to: String) : TimeScene
}

/** dt-ios TimeCalcViewModel; times in epoch milliseconds. */
class TimeCalcModel(application: Application) : AndroidViewModel(application) {
    var mode by mutableStateOf(TimeMode.Timestamp)
        private set
    var epochText by mutableStateOf("")
        private set
    var epochInput by mutableStateOf("")
        private set
    var dateA by mutableLongStateOf(System.currentTimeMillis())
        private set
    var dateB by mutableLongStateOf(dateA)
        private set
    var tzDate by mutableLongStateOf(dateA)
        private set
    var fromZone by mutableStateOf(TimeZone.getDefault().id)
        private set
    var toZone by mutableStateOf("UTC")
        private set

    /** When the shown values last changed: the banner's axis is laid out at this time. */
    var changedAt by mutableLongStateOf(dateA)
        private set

    /** Natural Earth land rings in map units: x 0 to 360, y 0 to north − south. */
    var land by mutableStateOf<List<FloatArray>>(emptyList())
        private set
    private var typing: Job? = null

    init {
        offsetEnd(java.time.temporal.ChronoUnit.DAYS, 7)
        fillNow()
        val assets = application.assets
        viewModelScope.launch {
            land = withContext(Dispatchers.IO) { runCatching { parseLand(assets.open("world-land.json").use { String(it.readBytes()) }) }.getOrDefault(emptyList()) }
        }
    }

    val epoch get() = TimeCalcMath.epoch(epochInput)
    val tzInstant get() = TimeCalcMath.instant(tzDate, fromZone)

    private fun changed() {
        changedAt = System.currentTimeMillis()
    }

    fun select(mode: TimeMode) {
        this.mode = mode
        changed()
    }

    fun typeEpoch(text: String) {
        epochText = text
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            commitEpoch()
        }
    }

    fun commitEpoch() {
        typing?.cancel()
        epochInput = TimeCalcMath.cleaned(epochText)
        changed()
    }

    fun setEpoch(text: String) {
        epochText = TimeCalcMath.cleaned(text)
        commitEpoch()
    }

    fun fillNow() = setEpoch((System.currentTimeMillis() / 1000).toString())

    fun stepEpoch(direction: Int) {
        val seconds = floor(epoch ?: (System.currentTimeMillis() / 1000.0)).toLong()
        setEpoch((seconds + direction).toString())
    }

    fun changeStart(millis: Long) {
        dateA = millis
        changed()
    }

    fun changeEnd(millis: Long) {
        dateB = millis
        changed()
    }

    fun offsetEnd(unit: java.time.temporal.ChronoUnit, value: Long) {
        runCatching { Instant.ofEpochMilli(dateA).atZone(ZoneId.systemDefault()).plus(value, unit).toInstant().toEpochMilli() }
            .onSuccess { dateB = it }
        changed()
    }

    fun changeZoneDate(millis: Long) {
        tzDate = millis
        changed()
    }

    fun pickFrom(zone: String) {
        fromZone = zone
        changed()
    }

    fun pickTo(zone: String) {
        toZone = zone
        changed()
    }

    fun swapZones() {
        val from = fromZone
        fromZone = toZone
        toZone = from
        changed()
    }
}

private fun parseLand(json: String): List<FloatArray> {
    val rings = JSONArray(json)
    return (0 until rings.length()).mapNotNull { r ->
        val ring = rings.getJSONArray(r)
        if (ring.length() <= 2) return@mapNotNull null
        FloatArray(ring.length() * 2).also { points ->
            for (i in 0 until ring.length()) {
                val point = ring.getJSONArray(i)
                points[i * 2] = (point.getDouble(0) + 180).toFloat()
                points[i * 2 + 1] = (WorldMap.NORTH - point.getDouble(1)).toFloat()
            }
        }
    }
}

private val formatters = HashMap<String, DateFormat>()

private fun formatter(skeleton: String, zone: String = TimeZone.getDefault().id): DateFormat =
    formatters.getOrPut("$skeleton|$zone|${Locale.getDefault()}") {
        DateFormat.getInstanceForSkeleton(skeleton, Locale.getDefault()).apply { timeZone = android.icu.util.TimeZone.getTimeZone(zone) }
    }

private fun formatted(skeleton: String, seconds: Double, zone: String = TimeZone.getDefault().id) =
    runCatching { formatter(skeleton, zone).format(Date((seconds * 1000).toLong())) }.getOrDefault(DASH)

// DateFormatter .full, .long
private fun fullDate(millis: Long, zone: String): String = runCatching {
    DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.LONG, Locale.getDefault())
        .apply { timeZone = android.icu.util.TimeZone.getTimeZone(zone) }
        .format(Date(millis))
}.getOrDefault(DASH)

private fun epochMillis(seconds: Double): Long? = (seconds * 1000).takeIf { abs(it) < 8.64e15 }?.toLong()

// DateComponentsFormatter .abbreviated
private fun breakdown(parts: IntArray): String {
    val units = listOf(MeasureUnit.DAY, MeasureUnit.HOUR, MeasureUnit.MINUTE, MeasureUnit.SECOND)
    val measures = parts.indices.filter { parts[it] > 0 }.map { Measure(parts[it], units[it]) }
    val format = MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.NARROW)
    return if (measures.isEmpty()) format.formatMeasures(Measure(0, MeasureUnit.SECOND)) else format.formatMeasures(*measures.toTypedArray())
}

private fun decimal(value: Double, places: Int): String =
    NumberFormat.getInstance(Locale.getDefault()).apply { maximumFractionDigits = places }.format(value)

@Composable
fun TimeCalc(model: TimeCalcModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val mode = model.mode
    val epoch = model.epoch
    val epochDate = epoch?.let(::epochMillis)

    val controls = CanonSection.of {
        CanonRows {
            CanonSegmented(modes.map { it.title }, modes.indexOf(mode)) { model.select(modes[it]) }
            when (mode) {
                TimeMode.Timestamp -> {
                    CanonHeading("Epoch Seconds")
                    CanonBigNumberField(
                        model.epochText,
                        model::typeEpoch,
                        onStep = model::stepEpoch,
                        placeholder = "1700000000",
                        label = "Epoch Seconds",
                        onEndEditing = model::commitEpoch,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    )
                    if (epoch == null && model.epochInput.isNotEmpty()) CanonStatusRow("Numbers only", CanonTone.Warn)
                    CanonGrid {
                        CanonButton("Now", Modifier.weight(1f), icon = pencil(R.drawable.pencil_clock), onClick = model::fillNow)
                        CanonButton("Paste", Modifier.weight(1f), icon = pencil(R.drawable.pencil_clipboard)) {
                            val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString() ?: return@CanonButton
                            model.setEpoch(text.trim())
                        }
                    }
                }
                TimeMode.Arithmetic -> {
                    CanonHeading("Start Date")
                    TimeDateCell(model.dateA, "Start Date", model::changeStart)
                    CanonHeading("End Date")
                    TimeDateCell(model.dateB, "End Date", model::changeEnd)
                    CanonHeading("Quick Offsets")
                    CanonGrid(columns = 4) {
                        offsets.forEach { offset -> CanonButton(offset.title) { model.offsetEnd(offset.unit, offset.value) } }
                    }
                }
                TimeMode.Timezone -> {
                    val instant = model.tzInstant
                    CanonHeading("Date & Time")
                    TimeDateCell(model.tzDate, "Date & Time", model::changeZoneDate)
                    CanonRow("From", flush = true, detail = TimeCalcZones.detail(model.fromZone, instant)) {
                        TimeZoneCell(model.fromZone, instant, "From", model::pickFrom)
                    }
                    CanonRow("To", flush = true, detail = TimeCalcZones.detail(model.toZone, instant)) {
                        TimeZoneCell(model.toZone, instant, "To", model::pickTo)
                    }
                    CanonGrid {
                        CanonButton("Swap", Modifier.weight(1f), icon = pencil(R.drawable.pencil_arrow_up_down), onClick = model::swapZones)
                    }
                }
            }
        }
    }

    val results = CanonSection.of {
        CanonRows {
            CanonHeading("Result")
            when (mode) {
                TimeMode.Timestamp -> {
                    CanonValueLine("Local Time", epochDate?.let { fullDate(it, TimeZone.getDefault().id) }, layout = CanonValueLayout.Above)
                    CanonValueLine("UTC", epochDate?.let { fullDate(it, "UTC") }, layout = CanonValueLayout.Above)
                    CanonValueLine(
                        "ISO 8601",
                        epochDate?.let { runCatching { Instant.ofEpochMilli(Math.floorDiv(it, 1000L) * 1000).toString() }.getOrNull() },
                        layout = CanonValueLayout.Above,
                    )
                }
                TimeMode.Arithmetic -> {
                    val seconds = abs(model.dateB - model.dateA) / 1000.0
                    CanonTiles(
                        listOf(
                            CanonTile("Difference", breakdown(TimeCalcMath.difference(model.dateA, model.dateB))),
                            CanonTile("Total Days", decimal(seconds / 86_400, 2)),
                            CanonTile("Total Hours", decimal(seconds / 3600, 1)),
                        )
                    )
                }
                TimeMode.Timezone -> {
                    val instant = model.tzInstant
                    for (zone in listOf(model.fromZone, model.toZone)) {
                        CanonValueLine(TimeCalcZones.title(zone, instant), fullDate(TimeCalcMath.instant(model.tzDate, model.fromZone), zone), layout = CanonValueLayout.Above)
                    }
                }
            }
        }
    }

    val scene = when (mode) {
        TimeMode.Timestamp -> TimeScene.Epoch(epoch)
        TimeMode.Arithmetic -> TimeScene.Span(model.dateA / 1000.0, model.dateB / 1000.0)
        TimeMode.Timezone -> TimeScene.Zones(model.tzInstant / 1000.0, model.fromZone, model.toZone)
    }
    val picture = CanonSection.canvas(CanonCanvasSize.Fitting, background = c.card) {
        TimePicture(scene, model.changedAt / 1000.0, model.land)
    }

    CanonSplit(sidebar = listOf(controls), main = listOf(results), banner = picture)
}

@Composable
private fun TimeDateCell(millis: Long, label: String, onChange: (Long) -> Unit) {
    val context = LocalContext.current
    val text = runCatching {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date(millis))
    }.getOrDefault(DASH)
    CanonGrid {
        CanonButton(text, Modifier.weight(1f), icon = pencil(R.drawable.pencil_calendar), contentDescription = label) {
            val current = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
            // ponytail: the platform dialogs, date then time; iOS shows one inline calendar picker in a popover
            DatePickerDialog(context, { _, year, month, day ->
                val dated = current.withYear(year).withMonth(month + 1).withDayOfMonth(day)
                TimePickerDialog(context, { _, hour, minute ->
                    onChange(dated.withHour(hour).withMinute(minute).toInstant().toEpochMilli())
                }, current.hour, current.minute, android.text.format.DateFormat.is24HourFormat(context)).show()
            }, current.year, current.monthValue - 1, current.dayOfMonth).show()
        }
        CanonButton("Now") { onChange(System.currentTimeMillis()) }
    }
}

@Composable
private fun TimeZoneCell(zone: String, millis: Long, label: String, onPick: (String) -> Unit) {
    val c = LocalDelphiColours.current
    var open by remember { mutableStateOf(false) }
    // null common, "" regions
    var level by remember { mutableStateOf<String?>(null) }
    val title = TimeCalcZones.title(zone, millis)
    Box(propagateMinConstraints = true) {
        Row(
            Modifier
                .heightIn(min = CanonMetrics.controlHeight)
                .canonClickable {
                    level = null
                    open = true
                }
                .background(c.card)
                .semantics {
                    contentDescription = label
                    stateDescription = title
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = CanonText.bodyMedium, color = c.foreground, maxLines = 1)
            Icon(pencil(R.drawable.pencil_chevrons_up_down), contentDescription = null, tint = c.foreground, modifier = Modifier.size(14.dp))
        }
        MenuCellPopup(open, { open = false }) {
            fun pick(id: String) {
                open = false
                if (id != zone) onPick(id)
            }
            val all = TimeCalcZones.sorted
            when (val shown = level) {
                null -> {
                    TimeCalcZones.common.forEach { ZoneItem(TimeCalcZones.city(it), TimeCalcZones.detail(it, millis), it == zone) { pick(it) } }
                    ZoneFolder("All Timezones", forward = true) { level = "" }
                }
                "" -> {
                    ZoneFolder("All Timezones", forward = false) { level = null }
                    all.filter { '/' in it }.map { it.substringBefore('/') }.distinct().sorted().forEach { region ->
                        ZoneFolder(region, forward = true) { level = region }
                    }
                    all.filter { '/' !in it }.forEach { ZoneItem(it, TimeCalcZones.detail(it, millis), it == zone) { pick(it) } }
                }
                else -> {
                    ZoneFolder(shown, forward = false) { level = "" }
                    all.filter { it.startsWith("$shown/") }.forEach {
                        ZoneItem(it.drop(shown.length + 1).replace('_', ' '), TimeCalcZones.detail(it, millis), it == zone) { pick(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneItem(title: String, detail: String, checked: Boolean, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CanonMetrics.controlHeight)
            .canonClickable(onClick = onClick)
            .semantics { selected = checked }
            .padding(horizontal = CanonMetrics.panelPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = CanonText.body, color = c.foreground)
            Text(detail, style = CanonText.caption, color = c.mutedForeground)
        }
        if (checked) Icon(pencil(R.drawable.pencil_check), contentDescription = null, tint = c.primary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ZoneFolder(title: String, forward: Boolean, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CanonMetrics.controlHeight)
            .canonClickable(onClick = onClick)
            .padding(horizontal = CanonMetrics.panelPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!forward) Icon(pencil(R.drawable.pencil_arrow_left), contentDescription = null, tint = c.foreground, modifier = Modifier.size(18.dp))
        Text(title, style = if (forward) CanonText.body else CanonText.bodySemibold, color = c.foreground, modifier = Modifier.weight(1f))
        if (forward) Icon(pencil(R.drawable.pencil_chevron_right), contentDescription = null, tint = c.mutedForeground, modifier = Modifier.size(18.dp))
    }
}

private data class TimePose(val lo: Double = 0.0, val hi: Double = 1.0, val a: Double = 0.0, val b: Double = 0.0)

private const val SETTLE_MILLIS = 600
// about 317,000 years
private const val REACH = 1e13
private const val STRIP = 24f
private const val LEAD = 0.3
private const val LINE = 14f
private const val FIGURE_ROW = 40f
private const val BESIDE_ROOM = 560f
private const val MAP_CAP = 190f
private const val STRIPS = (LINE + 4 + STRIP) * 2 + 10

private fun pose(scene: TimeScene, now: Double): TimePose = when (scene) {
    is TimeScene.Epoch -> {
        val seconds = scene.seconds?.coerceIn(-REACH, REACH) ?: now
        val lo = min(0.0, seconds)
        val hi = maxOf(TimeCalcAxis.LIMIT, seconds, now)
        TimePose(lo - (hi - lo) * 0.01, hi + (hi - lo) * 0.01, seconds)
    }
    is TimeScene.Span -> {
        val half = max(abs(scene.b - scene.a), 3600.0) / 2 * 1.45
        TimePose((scene.a + scene.b) / 2 - half, (scene.a + scene.b) / 2 + half, scene.a, scene.b)
    }
    is TimeScene.Zones -> TimePose(scene.instant - LEAD * 86_400, scene.instant + (1 - LEAD) * 86_400, scene.instant)
}

private class ZoneLayout(val map: Rect, val stripsTop: Float, val stripsWidth: Float)

/**
 * Where the Timezone picture goes: the map above the strips, or beside them on the trailing side when the band would
 * otherwise take more than 40 % of a wide window's height.
 */
private fun zoneLayout(top: Float, left: Float, room: Float, windowHeight: Float): ZoneLayout {
    val aspect = WorldMap.ASPECT
    val above = min(room / aspect, MAP_CAP) + 6 + STRIPS + 16
    if (room >= BESIDE_ROOM && above > windowHeight * 0.4f) {
        val height = FIGURE_ROW + 4 + STRIPS - 10
        val width = height * aspect
        return ZoneLayout(Rect(left + room - width, top, left + room, top + height), top + FIGURE_ROW + 4, room - width - 20)
    }
    val height = min(room / aspect, MAP_CAP).roundToInt().toFloat()
    val width = height * aspect
    return ZoneLayout(Rect(left + (room - width) / 2, top, left + (room + width) / 2, top + height), top + height + 6, room)
}

private class Figure(val number: String?, val unit: String, val note: String?)

private fun figure(scene: TimeScene, now: Double): Figure = when (scene) {
    is TimeScene.Epoch -> {
        val date = scene.seconds
        if (date == null || abs(date) >= REACH) {
            Figure(null, "", null)
        } else {
            val distance = date - now
            val (number, unit) = JwtEngine.span(abs(distance))
            Figure(number, unit, if (abs(distance) < 1) "Now" else if (distance < 0) "ago" else "from now")
        }
    }
    is TimeScene.Span -> {
        val distance = scene.b - scene.a
        val parts = TimeCalcMath.difference(0, (distance * 1000).toLong())
        val first = parts.indexOfFirst { it > 0 }.takeIf { it >= 0 } ?: 3
        val text = breakdown(IntArray(4).also { it[first] = parts[first] })
        val digits = text.takeWhile { it.isDigit() || it == ',' || it == '.' }
        val exact = breakdown(parts)
        Figure((if (distance <= -1) "−" else "") + digits, text.drop(digits.length).trim(), if (exact == text) null else exact)
    }
    is TimeScene.Zones -> {
        val millis = (scene.instant * 1000).toLong()
        Figure(TimeCalcZones.signed(TimeCalcZones.secondsFromGmt(scene.to, millis) - TimeCalcZones.secondsFromGmt(scene.from, millis)), "h", null)
    }
}

/**
 * The result on a time axis, its difference as a figure. Timestamp: the epoch from 1970 to the signed 32-bit limit in
 * 2038, counted seconds lit, the date and now marked. Arithmetic: a calendar ruler, weekends shaded, the span lit.
 * Timezone: a day strip per zone, hours shaded day and night, the instant across both.
 */
@Composable
private fun TimePicture(scene: TimeScene, changedAt: Double, land: List<FloatArray>) {
    val c = LocalDelphiColours.current
    val top = LocalCanonCanvas.current.top.value
    val density = LocalDensity.current.density
    val resolver = LocalFontFamilyResolver.current
    val windowHeight = LocalWindowInfo.current.containerSize.height / density
    // measures in dp
    val measurer = remember(resolver) { TextMeasurer(resolver, Density(1f), LayoutDirection.Ltr) }
    val sun = rememberVectorPainter(pencil(R.drawable.pencil_sun))

    var now by remember { mutableDoubleStateOf(System.currentTimeMillis() / 1000.0) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis() / 1000.0
            delay(1000)
        }
    }

    val target = pose(scene, changedAt)
    var start by remember { mutableStateOf(target) }
    var goal by remember { mutableStateOf(target) }
    var kind by remember { mutableStateOf(scene::class) }
    val progress = remember { Animatable(1f) }
    fun shown(t: Float): TimePose {
        val eased = PxToRemEngine.settle(t).toDouble()
        fun mix(a: Double, b: Double) = a + (b - a) * eased
        val width = exp(ln(start.hi - start.lo) + (ln(goal.hi - goal.lo) - ln(start.hi - start.lo)) * min(1.0, eased))
        val centre = mix((start.lo + start.hi) / 2, (goal.lo + goal.hi) / 2)
        return TimePose(centre - width / 2, centre + width / 2, mix(start.a, goal.a), mix(start.b, goal.b))
    }
    LaunchedEffect(target, scene::class) {
        if (target == goal && kind == scene::class) return@LaunchedEffect
        // ponytail: a new mode snaps; iOS cross-fades it
        if (kind != scene::class) {
            kind = scene::class
            start = target
            goal = target
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        start = shown(progress.value)
        goal = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(SETTLE_MILLIS, easing = LinearEasing))
    }

    val figure = figure(scene, now)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val padding = CanonMetrics.panelPadding.value
        val room = max(0f, maxWidth.value - padding * 2)
        val contentTop = top + 2
        val body = when (scene) {
            is TimeScene.Epoch -> FIGURE_ROW + 6 + LINE + 12 + 10 + LINE + 2 + LINE
            is TimeScene.Span -> FIGURE_ROW + 6 + LINE * 2 + 14 + 12 + LINE + 2 + LINE
            is TimeScene.Zones -> zoneLayout(0f, padding, room, windowHeight).stripsTop + STRIPS - 10
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(ceil(contentTop + body + 14).dp)
                .clearAndSetSemantics {
                    contentDescription = when (scene) {
                        is TimeScene.Epoch -> "Timestamp"
                        is TimeScene.Span -> "Difference"
                        is TimeScene.Zones -> "Offset"
                    }
                    figure.number?.let { number -> stateDescription = listOfNotNull(number + figure.unit, figure.note).joinToString(" ") }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (room <= 60) return@Canvas
                scale(density, Offset.Zero) {
                    TimeDrawing(this, measurer, c, padding, room, shown(progress.value), goal, now, sun).draw(scene, contentTop, windowHeight, land)
                }
            }
            if (figure.number != null) {
                Row(
                    Modifier.padding(start = CanonMetrics.panelPadding, top = contentTop.dp, end = CanonMetrics.panelPadding).height(FIGURE_ROW.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CanonFigure(figure.number, unit = figure.unit, size = 30.sp, halo = c.card)
                    if (figure.note != null) {
                        Text(figure.note, Modifier.padding(bottom = 7.dp), style = CanonText.footnoteSemibold, color = c.mutedForeground, maxLines = 1)
                    }
                }
            }
        }
    }
}

private val small = CanonText.captionSemibold
private val plain = CanonText.caption
private val timeMono = TextStyle(fontFamily = DelphiMono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

private class TimeDrawing(
    val scope: DrawScope,
    val measurer: TextMeasurer,
    val c: DelphiColours,
    val left: Float,
    val room: Float,
    val shown: TimePose,
    val target: TimePose,
    val now: Double,
    val sun: Painter,
) {
    private val hairline = CanonMetrics.hairline.value
    private val zone = TimeZone.getDefault().id
    private val zoneId = ZoneId.systemDefault()

    private var axisRoom = room

    private fun x(seconds: Double): Float {
        val inset = 6f
        val share = (seconds - shown.lo) / max(shown.hi - shown.lo, 1.0)
        return left + inset + share.toFloat() * (axisRoom - inset * 2)
    }

    fun draw(scene: TimeScene, contentTop: Float, windowHeight: Float, land: List<FloatArray>) {
        val top = contentTop + FIGURE_ROW
        when (scene) {
            is TimeScene.Epoch -> drawEpoch(top, scene.seconds != null && abs(shown.a) < REACH)
            is TimeScene.Span -> drawSpan(top)
            is TimeScene.Zones -> drawZones(zoneLayout(contentTop, left, room, windowHeight), listOf(scene.from, scene.to), land)
        }
    }

    private fun text(string: String, style: TextStyle) = measurer.measure(string, style)

    private fun TextLayoutResult.draw(colour: Color, at: Offset, halo: Color? = null) {
        if (halo != null) scope.drawText(this, halo, at, drawStyle = Stroke(4f))
        scope.drawText(this, colour, at)
    }

    private fun place(layout: TextLayoutResult, colour: Color, centre: Float, y: Float): Rect {
        val width = layout.size.width.toFloat()
        val origin = Offset(min(max(centre - width / 2, left), left + room - width), y)
        layout.draw(colour, origin)
        return Rect(origin, Size(width, layout.size.height.toFloat()))
    }

    private fun mark(x: Float, top: Float, bottom: Float, colour: Color, width: Float) {
        scope.drawRect(colour, Offset(x - width / 2, top), Size(width, bottom - top))
    }

    private fun drawEpoch(top: Float, marked: Boolean) {
        val labelY = top + 6
        val axis = labelY + LINE + 12
        val right = left + room
        scope.drawRect(c.muted, Offset(left, axis - 3), Size(room, 6f))
        if (marked) {
            val from = x(min(0.0, shown.a))
            val to = x(max(0.0, shown.a))
            scope.drawRect(c.primary.copy(alpha = if (shown.a < 0) 0.5f else 1f), Offset(from, axis - 3), Size(max(2f, to - from), 6f))
        }
        drawTicks(axis + 3, axis + 10)

        val end = x(TimeCalcAxis.LIMIT)
        scope.drawLine(c.mutedForeground, Offset(end, axis - 12), Offset(end, axis + 3), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 2f)))
        val limit = text("2³¹", small)
        var limitBox: Rect? = Rect(Offset(end - limit.size.width - 3, axis - 12 - LINE + 2), Size(limit.size.width.toFloat(), limit.size.height.toFloat()))

        mark(x(now), axis - 7, axis + 7, c.foreground, 1.5f)
        place(text("Now", small), c.foreground, x(now), axis + 10 + LINE + 2)

        if (marked) {
            val at = x(shown.a)
            mark(at, labelY + LINE + 2, axis + 7, c.primary, 2f)
            val box = place(text(formatted("yMMMd", target.a), small), c.primary, at, labelY)
            if (limitBox != null && Rect(box.left - 4, box.top, box.right + 4, box.bottom).overlaps(limitBox)) limitBox = null
        }
        if (limitBox != null && limitBox.left > left && limitBox.right < right) limit.draw(c.mutedForeground, limitBox.topLeft)
    }

    private fun drawSpan(top: Float) {
        val labelY = top + 6
        val axis = labelY + LINE * 2 + 14
        val span = shown.hi - shown.lo
        if (86_400 / span * room >= 6) {
            var day = runCatching { Instant.ofEpochSecond(floor(shown.lo).toLong()).atZone(zoneId).toLocalDate().atStartOfDay(zoneId) }.getOrNull()
            var count = 0
            while (day != null && day.toEpochSecond() < shown.hi && count < 500) {
                val next: ZonedDateTime = day.plusDays(1)
                // ponytail: Saturday and Sunday; iOS asks the calendar which days are the weekend
                if (day.dayOfWeek.value >= 6) {
                    val from = max(x(day.toEpochSecond().toDouble()), left)
                    val to = min(x(next.toEpochSecond().toDouble()), left + room)
                    if (to > from) scope.drawRect(c.muted, Offset(from, axis - 10), Size(to - from, 20f))
                }
                day = next
                count++
            }
        }
        scope.drawRect(c.border, Offset(left, axis - hairline / 2), Size(room, hairline))
        drawTicks(axis + 1, axis + 12)

        if (now in shown.lo..shown.hi) {
            mark(x(now), axis - 10, axis + 10, c.foreground, 1.5f)
            place(text("Now", small), c.foreground, x(now), axis + 12 + LINE + 2)
        }

        val early = min(shown.a, shown.b)
        val late = max(shown.a, shown.b)
        scope.drawRect(c.primary, Offset(x(early), axis - 3), Size(max(2f, x(late) - x(early)), 6f))
        for (seconds in listOf(shown.a, shown.b)) mark(x(seconds), labelY + LINE * 2 + 2, axis + 8, c.primary, 2f)

        val middle = (x(early) + x(late)) / 2
        val startFirst = shown.a <= shown.b
        val names = listOf("Start", "End")
        val dates = listOf(target.a, target.b).map { formatted("yMMMdjmm", it) }
        listOf(early, late).forEachIndexed { index, seconds ->
            val which = if ((index == 0) == startFirst) 0 else 1
            val name = text(names[which], small)
            val date = text(dates[which], plain)
            val width = max(name.size.width, date.size.width).toFloat()
            var origin = x(seconds) - width / 2
            origin = if (index == 0) min(origin, middle - 4 - width) else max(origin, middle + 4)
            origin = min(max(origin, left), left + room - width)
            val alignRight = index == 0 && origin + width <= x(seconds) + 1
            listOf(name to c.foreground, date to c.mutedForeground).forEachIndexed { row, (layout, colour) ->
                val offset = if (alignRight) width - layout.size.width else 0f
                layout.draw(colour, Offset(origin + offset, labelY + row * LINE))
            }
        }
    }

    private fun drawZones(layout: ZoneLayout, zones: List<String>, land: List<FloatArray>) {
        drawMap(layout.map, zones, land)
        axisRoom = layout.stripsWidth
        val room = layout.stripsWidth
        var y = layout.stripsTop
        val cursor = x(shown.a)
        var stripTop = 0f
        var stripBottom = 0f
        val millis = (target.a * 1000).toLong()
        zones.forEachIndexed { index, zone ->
            val colour = if (index == 0) c.primary else c.foreground
            val city = text(TimeCalcZones.city(zone), small)
            city.draw(colour, Offset(left, y))
            text(TimeCalcZones.abbreviation(zone, millis), plain).draw(c.mutedForeground, Offset(left + city.size.width + 6, y))
            val time = text(formatted("jmm", target.a, zone), timeMono)
            val day = text(formatted("EEEdMMM", target.a, zone), plain)
            day.draw(c.mutedForeground, Offset(left + room - day.size.width, y))
            time.draw(colour, Offset(left + room - day.size.width - 6 - time.size.width, y + (LINE - time.size.height) / 2))
            y += LINE + 4
            val strip = Rect(left, y, left + room, y + STRIP)
            if (index == 0) stripTop = strip.top
            stripBottom = strip.bottom
            drawStrip(strip, zone)
            y += STRIP + 10
        }
        mark(cursor, stripTop - 3, stripBottom + 3, c.card, 4f)
        mark(cursor, stripTop - 3, stripBottom + 3, c.primary, 2f)
        axisRoom = this.room
    }

    /**
     * A faint world: night shaded behind the day and night line at the shown instant, land in outline, the sun where it
     * stands overhead, and the From and To cities marked as the strips mark them.
     */
    private fun drawMap(map: Rect, zones: List<String>, land: List<FloatArray>) {
        val span = WorldMap.NORTH - WorldMap.SOUTH
        fun point(lat: Double, lon: Double) =
            Offset(map.left + ((lon + 180) / 360).toFloat() * map.width, map.top + ((WorldMap.NORTH - lat) / span).toFloat() * map.height)
        val sunAt = TimeCalcWorld.subsolar(shown.a)
        scope.drawIntoCanvas { it.saveLayer(map, Paint()) }
        val night = Path().apply {
            var lon = -180.0
            var first = true
            while (lon <= 180) {
                val p = point(TimeCalcWorld.terminator(lon, sunAt), lon)
                if (first) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                first = false
                lon += 2
            }
            val edge = if (sunAt.first >= 0) map.bottom + 1 else map.top - 1
            lineTo(map.right, edge)
            lineTo(map.left, edge)
            close()
        }
        scope.drawPath(night, c.muted.copy(alpha = 0.7f))
        val coast = Path()
        val kx = map.width / 360
        val ky = map.height / span.toFloat()
        for (ring in land) {
            coast.moveTo(map.left + ring[0] * kx, map.top + ring[1] * ky)
            var i = 2
            while (i < ring.size) {
                coast.lineTo(map.left + ring[i] * kx, map.top + ring[i + 1] * ky)
                i += 2
            }
            coast.close()
        }
        scope.drawPath(coast, c.foreground.copy(alpha = 0.07f))
        scope.drawPath(coast, c.foreground.copy(alpha = 0.2f), style = Stroke(0.5f))
        val spot = point(sunAt.first, sunAt.second)
        scope.translate(spot.x - 5, spot.y - 5) {
            with(sun) { draw(Size(10f, 10f), colorFilter = ColorFilter.tint(c.mutedForeground.copy(alpha = 0.7f))) }
        }
        val across = map.width * 0.08f
        val down = map.height * 0.18f
        val clear = Color.Black.copy(alpha = 0f)
        scope.drawRect(Brush.horizontalGradient(listOf(clear, Color.Black), map.left, map.left + across), map.topLeft, Size(across, map.height), blendMode = BlendMode.DstIn)
        scope.drawRect(Brush.horizontalGradient(listOf(Color.Black, clear), map.right - across, map.right), Offset(map.right - across, map.top), Size(across, map.height), blendMode = BlendMode.DstIn)
        scope.drawRect(Brush.verticalGradient(listOf(clear, Color.Black), map.top, map.top + down), map.topLeft, Size(map.width, down), blendMode = BlendMode.DstIn)
        scope.drawRect(Brush.verticalGradient(listOf(Color.Black, clear), map.bottom - down, map.bottom), Offset(map.left, map.bottom - down), Size(map.width, down), blendMode = BlendMode.DstIn)
        scope.drawIntoCanvas { it.restore() }

        val millis = (shown.a * 1000).toLong()
        scope.clipRect(map.left - left, map.top, map.right + left, map.bottom) {
            val taken = mutableListOf<Rect>()
            for (index in zones.indices.reversed()) {
                val zone = zones[index]
                val colour = if (index == 0) c.primary else c.foreground
                val place = TimeCalcWorld.places[zone]
                if (place == null) {
                    val lon = TimeCalcZones.secondsFromGmt(zone, millis) / 240.0
                    val x = point(0.0, lon).x
                    drawLine(colour.copy(alpha = 0.5f), Offset(x, map.top), Offset(x, map.bottom), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f)))
                    val name = text(TimeCalcZones.offset(zone, millis), small)
                    val width = name.size.width
                    val leftward = x + 5 + width > map.right - map.width * 0.08f
                    name.draw(colour, Offset(if (leftward) x - 5 - width else x + 5, map.top + map.height * (if (index == 0) 0.3f else 0.45f)), halo = c.card)
                    continue
                }
                val spotAt = point(place.first, place.second)
                drawCircle(c.card, 5f, spotAt)
                if (index == 0) {
                    drawCircle(colour, 3.5f, spotAt)
                } else {
                    drawCircle(colour, 2.75f, spotAt, style = Stroke(1.5f))
                    drawCircle(colour, 1.25f, spotAt)
                }
                val name = text(TimeCalcZones.city(zone), small)
                val size = name.size
                val leftward = spotAt.x + 7 + size.width > map.right - map.width * 0.08f
                var box = Rect(Offset(if (leftward) spotAt.x - 7 - size.width else spotAt.x + 7, spotAt.y - size.height / 2f), Size(size.width.toFloat(), size.height.toFloat()))
                if (taken.any { it.overlaps(box) }) box = box.translate(0f, size.height.toFloat())
                taken += box
                name.draw(colour, box.topLeft, halo = c.card)
            }
        }
    }

    private fun drawStrip(strip: Rect, zone: String) {
        val zoneId = runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.of("UTC"))
        scope.clipRect(strip.left, strip.top, strip.right, strip.bottom) {
            // half-hour offsets
            val offset = TimeCalcZones.secondsFromGmt(zone, (shown.lo * 1000).toLong())
            var hour = floor((shown.lo + offset) / 3600) * 3600 - offset
            var count = 0
            // drawn last: labels overflow
            val labels = mutableListOf<Pair<TextLayoutResult, Pair<Offset, Color>>>()
            while (hour < shown.hi && count < 30) {
                val start = x(hour)
                val end = x(hour + 3600)
                val local = Instant.ofEpochSecond(hour.toLong()).atZone(zoneId).hour
                drawRect(if (local in 6 until 18) c.card else c.muted, Offset(start, strip.top), Size(end - start, strip.height))
                drawRect(c.border, Offset(start, strip.top), Size(hairline, strip.height))
                if (local == 0) drawRect(c.foreground, Offset(start - 1, strip.top), Size(2f, strip.height))
                if (local % (if (end - start >= 30) 1 else 3) == 0 && start >= strip.left) {
                    val layout = if (local == 0) text(formatted("EEE", hour, zone), small) else text(formatted("j", hour, zone), plain)
                    val colour = if (local == 0) c.foreground else c.mutedForeground
                    labels += layout to (Offset(start + 3, strip.center.y - layout.size.height / 2f) to colour)
                }
                hour += 3600
                count++
            }
            for ((layout, at) in labels) drawText(layout, at.second, at.first)
        }
        scope.drawRect(c.border, strip.deflate(0.5f).topLeft, strip.deflate(0.5f).size, style = Stroke(hairline))
    }

    private fun drawTicks(axis: Float, y: Float) {
        val span = shown.hi - shown.lo
        val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val rung = TimeCalcAxis.rungs[TimeCalcAxis.rung(span, room.toDouble(), 44.0)]
        val minor = rung.minor?.let { TimeCalcAxis.rungs[it] }
        if (minor != null && minor.seconds / span * room >= 4) {
            for (tick in TimeCalcAxis.ticks(shown.lo, shown.hi, minor, zoneId, firstDay)) {
                scope.drawRect(c.mutedForeground, Offset(x(tick), axis), Size(hairline, 3f))
            }
        }
        var lastRight = -Float.MAX_VALUE
        var labelled: Double? = null
        for (tick in TimeCalcAxis.ticks(shown.lo, shown.hi, rung, zoneId, firstDay)) {
            val at = x(tick)
            scope.drawRect(c.mutedForeground, Offset(at, axis), Size(hairline, 7f))
            val (template, strong) = TimeCalcAxis.template(tick, labelled, rung, zoneId)
            val layout = text(formatted(template, tick, zone), if (strong) small else plain)
            val width = layout.size.width
            val origin = min(max(at - width / 2, left), left + room - width)
            if (origin <= lastRight + 6) continue
            layout.draw(if (strong) c.foreground else c.mutedForeground, Offset(origin, y))
            lastRight = origin + width
            labelled = tick
        }
    }
}
