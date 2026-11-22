package com.delphi.delphitools.tools.devtools

import com.delphi.delphitools.tools.TYPING_DELAY_MS
import android.content.ClipData
import android.content.ClipboardManager
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonNumberField
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.CanonValueLayout
import com.delphi.delphitools.canon.CanonValueLine
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiColours
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private const val RUN_COUNT = 5
private const val ARITY = "A cron expression has five fields: minute, hour, day, month, weekday"
private val RAIL_NAMES = listOf("Minute", "Hour", "Day", "Month", "Weekday")
private val sourceStyle = TextStyle(fontFamily = DelphiMono, fontSize = 17.sp, fontWeight = FontWeight.Medium)
private val plainKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done)

enum class CronMode(val label: String) { Every("Every"), Value("Value"), Range("Range"), Step("Step") }

data class CronSubs(val value: Int, val lo: Int, val hi: Int, val step: Int)

enum class CronSub { Value, Lo, Hi, Step }

/** dt-ios CronBuilderViewModel: the expression box and the five field editors kept in step. */
class CronBuilderModel : ViewModel() {
    var boxText by mutableStateOf("30 9 * * MON-FRI")
        private set
    var fields by mutableStateOf(
        mapOf(CronFieldKind.Minute to "30", CronFieldKind.Hour to "9", CronFieldKind.Dom to "*", CronFieldKind.Month to "*", CronFieldKind.Dow to "MON-FRI"),
    )
        private set
    var subs by mutableStateOf(
        mapOf(
            CronFieldKind.Minute to CronSubs(30, 0, 15, 5),
            CronFieldKind.Hour to CronSubs(9, 9, 17, 2),
            CronFieldKind.Dom to CronSubs(1, 1, 15, 2),
            CronFieldKind.Month to CronSubs(1, 1, 6, 2),
            CronFieldKind.Dow to CronSubs(1, 1, 5, 2),
        ),
    )
        private set

    /** The open field editor, or null for none. */
    var open by mutableStateOf<Int?>(0)

    val parsed get() = CronEngine.parseCron(boxText)

    fun type(text: String) {
        boxText = text
        val parts = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size != 5) return
        val next = CronEngine.fieldRules.mapIndexed { index, rule -> rule.kind to parts[index] }.toMap()
        if (next == fields) return
        fields = next
        syncSubs()
    }

    fun source(kind: CronFieldKind) = fields[kind] ?: "*"

    fun parse(kind: CronFieldKind) = CronEngine.parseField(source(kind), CronEngine.rule(kind))

    fun mode(parse: CronFieldParse): CronMode? {
        if (!parse.ok) return null
        return when (val shape = parse.shape) {
            CronFieldShape.Star -> CronMode.Every
            is CronFieldShape.Single -> CronMode.Value
            is CronFieldShape.Step -> CronMode.Step
            is CronFieldShape.Range -> if (shape.step == 1) CronMode.Range else null
            else -> null
        }
    }

    fun subs(kind: CronFieldKind) = subs[kind] ?: CronSubs(0, 0, 0, 1)

    fun write(kind: CronFieldKind, source: String) {
        fields = fields + (kind to source)
        boxText = CronEngine.fieldRules.joinToString(" ") { fields[it.kind] ?: "*" }
        syncSubs()
    }

    fun setMode(kind: CronFieldKind, mode: CronMode) {
        val s = subs(kind)
        when (mode) {
            CronMode.Every -> write(kind, "*")
            CronMode.Value -> write(kind, s.value.toString())
            CronMode.Range -> write(kind, "${s.lo}-${s.hi}")
            CronMode.Step -> write(kind, "*/${maxOf(s.step, 2)}")
        }
    }

    fun setSub(kind: CronFieldKind, key: CronSub, raw: Int) {
        val rule = CronEngine.rule(kind)
        // */1 parses as every
        val low = if (key == CronSub.Step) 2 else rule.min
        // dow 7 normalises to 0
        val high = when (key) {
            CronSub.Step -> rule.rangeMax - rule.min + 1
            CronSub.Value -> rule.rangeMax
            else -> rule.max
        }
        val v = raw.coerceIn(low, high)
        var s = subs(kind)
        s = when (key) {
            CronSub.Value -> s.copy(value = v)
            CronSub.Lo -> s.copy(lo = v, hi = maxOf(s.hi, v))
            CronSub.Hi -> s.copy(hi = v, lo = minOf(s.lo, v))
            CronSub.Step -> s.copy(step = v)
        }
        subs = subs + (kind to s)
        when (mode(parse(kind))) {
            CronMode.Value -> write(kind, s.value.toString())
            CronMode.Range -> write(kind, "${s.lo}-${s.hi}")
            CronMode.Step -> write(kind, "*/${s.step}")
            else -> {}
        }
    }

    private fun syncSubs() {
        val next = subs.toMutableMap()
        for (rule in CronEngine.fieldRules) {
            val parse = CronEngine.parseField(source(rule.kind), rule)
            if (!parse.ok) continue
            val s = subs(rule.kind)
            next[rule.kind] = when (val shape = parse.shape) {
                is CronFieldShape.Single -> s.copy(value = shape.value)
                is CronFieldShape.Range -> s.copy(lo = shape.lo, hi = shape.hi, step = shape.step)
                is CronFieldShape.Step -> s.copy(step = shape.step)
                else -> continue
            }
        }
        subs = next
    }
}

/** Month and weekday values by name; null for the numeric fields. */
private fun cronName(value: Int, kind: CronFieldKind, locale: Locale): String? = when (kind) {
    CronFieldKind.Month -> if (value in 1..12) Month.of(value).getDisplayName(DateTextStyle.FULL_STANDALONE, locale) else null
    CronFieldKind.Dow -> weekday(value).getDisplayName(DateTextStyle.FULL_STANDALONE, locale)
    else -> null
}

private fun weekday(cron: Int) = DayOfWeek.of((cron % 7 + 6) % 7 + 1)

/** The field in a few words: the mode, or the names it keeps for month and weekday. */
private fun cronSummary(parse: CronFieldParse, kind: CronFieldKind, locale: Locale): String {
    val shape = parse.shape ?: return "—"
    fun name(value: Int) = cronName(value, kind, locale)
    return when (shape) {
        CronFieldShape.Star -> CronMode.Every.label
        is CronFieldShape.Single -> name(shape.value) ?: CronMode.Value.label
        is CronFieldShape.Step -> CronMode.Step.label
        is CronFieldShape.Range if shape.step == 1 -> {
            val first = name(shape.lo)
            val last = name(shape.hi)
            if (first != null && last != null) "$first–$last" else CronMode.Range.label
        }
        is CronFieldShape.Range, CronFieldShape.Set -> {
            val names = parse.values.mapNotNull(::name)
            if (names.isEmpty() || names.size > 3) "List" else names.joinToString(", ")
        }
    }
}

private fun runFormatter(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMMyyyyjmm"), locale)

private val spanUnits = listOf(MeasureUnit.DAY, MeasureUnit.HOUR, MeasureUnit.MINUTE, MeasureUnit.SECOND)

/** Time left in its largest whole unit, rounded down, split into the number and the unit as the system abbreviates them. */
private fun span(seconds: Long, locale: Locale): Pair<String, String> {
    val (count, unit) = CronEngine.spanUnit(seconds)
    val text = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.NARROW).format(Measure(count, spanUnits[unit]))
    val number = text.takeWhile { it.isDigit() || it == ',' || it == '.' }
    return number to text.drop(number.length).trim()
}

@Composable
fun CronBuilder(model: CronBuilderModel = viewModel()) {
    val context = LocalContext.current
    val locale = remember(context) { context.resources.configuration.locales[0] }
    val parsed = model.parsed
    val arityError = parsed.fieldCount != 5
    val canonical = parsed.expression
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val zone = ZoneId.systemDefault()
    val runs = remember(parsed.expression, now / 60_000) {
        if (parsed.ok) CronEngine.nextRuns(parsed.fields, RUN_COUNT, ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)) else emptyList()
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val formatter = remember(locale) { runFormatter(locale) }

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Expression")
            DraftCell(model.boxText, "* * * * *", Modifier.fillMaxWidth(), onSettle = model::type)
            if (arityError) CanonStatusRow(ARITY, CanonTone.Warn)
        }
    }

    @Composable
    fun editors(range: IntRange) {
        for (index in range) {
            val kind = CronEngine.fieldRules[index].kind
            CronFieldEditor(model, index, kind, editable = !arityError, locale)
        }
    }

    val time = CanonSection.of {
        CanonRows {
            CanonHeading("Time")
            editors(0..1)
        }
    }
    val date = CanonSection.of {
        CanonRows {
            CanonHeading("Date")
            editors(2..4)
        }
    }
    val action = CanonSection.of {
        CanonRows {
            CanonButton("Copy", kind = CanonButtonKind.Primary, icon = Icons.Outlined.ContentCopy, oversized = true, enabled = canonical != null, flashesCopied = true) {
                if (canonical != null) context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", canonical))
            }
        }
    }

    val schedule = CanonSection.of {
        CanonRows {
            CanonHeading("Schedule")
            Text(
                CronEngine.describe(parsed.fields),
                style = CanonText.body,
                color = LocalDelphiColours.current.foreground,
                modifier = Modifier.fillMaxWidth().background(LocalDelphiColours.current.card).padding(horizontal = CanonMetrics.panelPadding, vertical = 12.dp),
            )
            CanonValueLine("Canonical", canonical, layout = CanonValueLayout.Above)
            CanonHeading("Next runs · ${zone.id}")
            runs.forEachIndexed { index, run ->
                CanonValueLine(String.format(Locale.ROOT, "%02d", index + 1), run.format(formatter), layout = CanonValueLayout.Numbered)
            }
            if (runs.isEmpty()) CanonEmptyRow("Never")
        }
    }
    val problems = CanonSection.of {
        CanonRows {
            CanonHeading("Errors")
            if (arityError) {
                CanonStatusRow(ARITY, CanonTone.Warn)
            } else {
                parsed.fields.forEachIndexed { index, field ->
                    field.error?.let { CanonStatusRow("${CronEngine.fieldRules[index].label} · ${it.shown}", CanonTone.Warn) }
                }
            }
        }
    }

    val banner = CanonSection.canvas(CanonCanvasSize.Fitting) {
        CronRails(parsed, model.open ?: -1, runs.firstOrNull(), now, formatter, locale)
    }

    CanonSplit(
        sidebar = listOf(input, time, date, action),
        main = listOf(if (parsed.ok) schedule else problems),
        banner = banner,
    )
}

/** A text cell that keeps what is typed while it has focus and hands it on once typing pauses or focus leaves. */
@Composable
private fun DraftCell(
    value: String,
    placeholder: String,
    modifier: Modifier,
    width: Dp = 240.dp,
    enabled: Boolean = true,
    onFocus: () -> Unit = {},
    onSettle: (String) -> Unit,
) {
    var draft by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(draft) {
        val pending = draft ?: return@LaunchedEffect
        delay(TYPING_DELAY_MS)
        onSettle(pending)
    }
    CanonTextCell(
        draft ?: value,
        { draft = it },
        modifier.onFocusChanged { state ->
            if (state.isFocused) {
                onFocus()
            } else {
                draft?.let(onSettle)
                draft = null
            }
        },
        placeholder = placeholder,
        width = width,
        enabled = enabled,
        textStyle = sourceStyle,
        keyboardOptions = plainKeyboard,
    )
}

/**
 * dt-ios CronFieldEditor: a row with the field's name, what it keeps in words and its text to type into; open, the mode
 * as a segmented row and the numbers that mode takes under it. Only the chosen field is open.
 */
@Composable
private fun CronFieldEditor(model: CronBuilderModel, index: Int, kind: CronFieldKind, editable: Boolean, locale: Locale) {
    val c = LocalDelphiColours.current
    val rule = CronEngine.rule(kind)
    val parse = model.parse(kind)
    val subs = model.subs(kind)
    val mode = if (editable) model.mode(parse) else null
    val isOpen = model.open == index
    val problem = if (editable) parse.error?.shown else null
    val detail = problem ?: if (editable) cronSummary(parse, kind, locale) else "—"
    val name = RAIL_NAMES[index]
    val chevron by animateFloatAsState(if (isOpen) 180f else 0f, tween(250), label = "chevron")

    // ponytail: the field's text stays the foreground colour on an error; the row's detail turns red
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CanonMetrics.controlHeight + 4.dp)
            .height(IntrinsicSize.Min)
            .background(c.border),
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .canonClickable(role = Role.Button) { model.open = if (isOpen) null else index }
                .background(if (isOpen) c.accentBg else c.card)
                .semantics {
                    contentDescription = name
                    stateDescription = detail
                    selected = isOpen
                }
                .padding(start = CanonMetrics.panelPadding, top = 8.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(name, style = CanonText.bodySemibold, color = if (isOpen) c.primary else c.foreground)
                Text(detail, style = CanonText.caption, color = if (problem != null) c.destructive else c.mutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.ExpandMore, null, tint = c.mutedForeground, modifier = Modifier.size(16.dp).rotate(chevron))
        }
        DraftCell(
            if (editable) model.source(kind) else "",
            "*",
            Modifier.fillMaxHeight(),
            width = 132.dp,
            enabled = editable,
            onFocus = { model.open = index },
        ) { model.write(kind, it) }
    }
    if (!isOpen) return
    CanonSegmented(CronMode.entries.map { it.label }, mode?.ordinal ?: -1, columns = 4, enabled = editable) { model.setMode(kind, CronMode.entries[it]) }
    @Composable
    fun number(title: String, value: Int, range: IntRange, key: CronSub, named: Boolean) {
        CanonRow(title, flush = true, detail = if (named) cronName(value, kind, locale) else null) {
            CanonNumberField(value, { model.setSub(kind, key, it) }, Modifier.fillMaxSize(), range = range)
        }
    }
    when (mode) {
        CronMode.Value -> number("Value", subs.value, rule.min..rule.rangeMax, CronSub.Value, true)
        CronMode.Range -> {
            number("From", subs.lo, rule.min..rule.max, CronSub.Lo, true)
            number("To", subs.hi, rule.min..rule.max, CronSub.Hi, true)
        }
        CronMode.Step -> number("Every", subs.step, 2..(rule.rangeMax - rule.min + 1), CronSub.Step, false)
        else -> {}
    }
}

private val railSmall = TextStyle(fontFamily = DelphiFont, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
private val railMono = TextStyle(fontFamily = DelphiMono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
private val railCell = 18.dp
private val railGap = 10.dp

/** The cells a rail draws, in drawing order: every value the field takes, weekdays from the locale's first day. */
internal fun cronCells(kind: CronFieldKind, firstWeekday: DayOfWeek): List<Int> {
    val rule = CronEngine.rule(kind)
    if (kind != CronFieldKind.Dow) return (rule.min..rule.rangeMax).toList()
    val first = firstWeekday.value % 7
    return (0 until 7).map { (it + first) % 7 }
}

/**
 * dt-ios CronRails: the expression as a punch card. One rail per field, every value it can take as a cell, the values
 * the field keeps lit and the next run's value in the primary colour, so the next run is where all five rails line up.
 * A bracket joins the two day rails when both are restricted, because cron then fires on either. Above it the time to the next run.
 */
@Composable
private fun CronRails(parsed: CronParse, field: Int, next: ZonedDateTime?, now: Long, formatter: DateTimeFormatter, locale: Locale) {
    // ponytail: no crossfade between expressions (iOS dissolves for 0.2 s)
    val c = LocalDelphiColours.current
    val top = LocalCanonCanvas.current.top
    val measurer = rememberTextMeasurer()
    val fields = if (parsed.fieldCount == 5) parsed.fields else emptyList()
    val nextValues = next?.let { listOf(it.minute, it.hour, it.dayOfMonth, it.monthValue, it.dayOfWeek.value % 7) }
    val firstWeekday = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val (number, unit) = if (next != null) span(maxOf(0, next.toEpochSecond() - now / 1000), locale) else "—" to ""
    val whenText = next?.format(formatter) ?: if (parsed.ok) "Never" else "—"

    Column(
        Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = if (parsed.ok) CronEngine.describe(parsed.fields) else "Errors"
                stateDescription = if (next != null) "Next run, $number $unit, $whenText" else whenText
            }
            .padding(top = top + CanonMetrics.panelPadding * 0.75f, bottom = CanonMetrics.panelPadding),
    ) {
        Row(
            Modifier.padding(horizontal = CanonMetrics.panelPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CanonFigure(number, unit = unit, size = 34.sp, colour = if (next != null) c.primary else c.mutedForeground, halo = c.card)
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Next run", style = CanonText.captionSemibold, color = c.mutedForeground)
                Text(
                    whenText,
                    style = CanonText.subheadline,
                    color = when {
                        next != null -> c.foreground
                        parsed.ok -> c.destructive
                        else -> c.mutedForeground
                    },
                    maxLines = 2,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val smallLine = remember(measurer) { measurer.measure("A", railSmall).size.height }
            Canvas(Modifier.fillMaxWidth().height(with(LocalDensity.current) { (smallLine.toDp() + 4.dp + railCell) * 5 + railGap * 4 })) {
                drawRails(measurer, c, fields, field, nextValues, firstWeekday, locale, smallLine.toFloat())
            }
        }
    }
}

private fun DrawScope.drawRails(
    measurer: TextMeasurer,
    c: DelphiColours,
    fields: List<CronFieldParse>,
    field: Int,
    nextValues: List<Int>?,
    firstWeekday: DayOfWeek,
    locale: Locale,
    smallLine: Float,
) {
    val pad = CanonMetrics.panelPadding.toPx()
    val left = pad
    val room = maxOf(0f, size.width - pad * 2)
    val cell = railCell.toPx()
    val hair = CanonMetrics.hairline.toPx()
    var y = 0f
    val middles = mutableListOf<Float>()

    CronEngine.fieldRules.forEachIndexed { index, rule ->
        val parse = fields.getOrNull(index)
        val lit = if (parse?.ok == true) parse.values.toSet() else emptySet()
        val failed = parse != null && !parse.ok
        val chosen = index == field
        val titleColour = when {
            failed -> c.destructive
            chosen -> c.primary
            else -> c.mutedForeground
        }
        drawText(measurer.measure(RAIL_NAMES[index].uppercase(), railSmall.copy(color = titleColour)), topLeft = Offset(left, y))
        val detail = when {
            parse == null -> ""
            parse.ok -> parse.canon
            else -> parse.error?.let { "${it.text} ${it.data ?: ""}" } ?: parse.source
        }
        val detailInk = when {
            failed -> c.destructive
            chosen -> c.primary
            else -> c.foreground
        }
        val maxWidth = (room * 0.6f).toInt().coerceAtLeast(0)
        val laid = measurer.measure(detail, railMono.copy(color = detailInk), maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = maxWidth))
        drawText(laid, topLeft = Offset(left + room - laid.size.width, y + 1.dp.toPx()))

        val railTop = y + smallLine + 4.dp.toPx()
        val values = cronCells(rule.kind, firstWeekday)
        val pitch = room / values.size
        val gap = if (pitch > 8.dp.toPx()) 2.dp.toPx() else 1.dp.toPx()
        val labels = railLabels(measurer, rule.kind, values, pitch - gap, locale)
        val blank = parse == null || failed
        drawRect(c.card, Offset(left, railTop), Size(room, cell))
        if (blank) {
            drawRect(
                if (failed) c.destructive else c.mutedForeground,
                Offset(left + hair / 2, railTop + hair / 2),
                Size(room - hair, cell - hair),
                style = Stroke(hair, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
            )
        }
        values.forEachIndexed { slot, value ->
            val box = Rect(Offset(left + slot * pitch, railTop), Size(pitch - gap, cell))
            val isNext = nextValues?.get(index) == value
            val isLit = value in lit
            if (!blank) drawRect(if (isNext) c.primary else if (isLit) c.foreground else c.muted, box.topLeft, box.size)
            val label = labels?.get(slot) ?: return@forEachIndexed
            val ink = when {
                blank -> c.mutedForeground
                isNext -> c.primaryForeground
                isLit -> c.card
                else -> c.mutedForeground
            }
            val text = measurer.measure(label, railMono.copy(color = ink))
            drawText(text, topLeft = Offset(box.center.x - text.size.width / 2, box.center.y - text.size.height / 2))
        }
        middles += railTop + cell / 2
        y += smallLine + 4.dp.toPx() + cell + railGap.toPx()
    }

    // cron ORs day fields
    if (fields.size == 5 && fields[2].ok && fields[4].ok && !fields[2].any && !fields[4].any && left >= 12.dp.toPx()) {
        val x = left - 8.dp.toPx()
        val (from, to) = middles[2] to middles[4]
        val path = Path().apply {
            moveTo(left - 2.dp.toPx(), from)
            lineTo(x, from)
            lineTo(x, to)
            lineTo(left - 2.dp.toPx(), to)
        }
        drawPath(path, c.primary, style = Stroke(1.5.dp.toPx()))
        val word = measurer.measure("or", railSmall.copy(color = c.primary, letterSpacing = 0.sp))
        rotate(-90f, Offset(x, (from + to) / 2)) {
            val w = word.size.width
            val h = word.size.height
            drawRect(c.card, Offset(x - w / 2 - 3.dp.toPx(), (from + to) / 2 - h / 2), Size(w + 6.dp.toPx(), h.toFloat()))
            drawText(word, topLeft = Offset(x - w / 2, (from + to) / 2 - h / 2))
        }
    }
}

/** Cell labels when every one fits, else null: names shortest last, numbers as they are. */
private fun DrawScope.railLabels(measurer: TextMeasurer, kind: CronFieldKind, values: List<Int>, width: Float, locale: Locale): List<String>? {
    val sets = when (kind) {
        CronFieldKind.Month -> listOf(DateTextStyle.SHORT_STANDALONE, DateTextStyle.NARROW_STANDALONE).map { style -> values.map { Month.of(it).getDisplayName(style, locale) } }
        CronFieldKind.Dow -> listOf(DateTextStyle.SHORT_STANDALONE, DateTextStyle.NARROW_STANDALONE).map { style -> values.map { weekday(it).getDisplayName(style, locale) } }
        else -> listOf(values.map(Int::toString))
    }
    return sets.firstOrNull { set -> set.all { measurer.measure(it, railMono).size.width + 3 * density <= width } }
}
