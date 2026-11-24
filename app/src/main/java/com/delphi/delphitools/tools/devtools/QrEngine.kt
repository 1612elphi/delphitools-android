package com.delphi.delphitools.tools.devtools

import androidx.compose.ui.graphics.ImageBitmap
import com.delphi.delphitools.colour.Rgb
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class QrDotStyle(val title: String) { Square("Square"), Rounded("Rounded"), Dots("Dots"), Diamond("Diamond"), Classy("Classy"), Cushion("Cushion") }

enum class QrEyeStyle(val title: String) { Square("Square"), Rounded("Rounded"), Circle("Circle"), Leaf("Leaf"), Shield("Shield"), Diamond("Diamond") }

enum class QrPupilStyle(val title: String) { Square("Square"), Rounded("Rounded"), Circle("Circle"), Diamond("Diamond"), Leaf("Leaf"), Cushion("Cushion") }

class QrStylePreset(val name: String, val dot: QrDotStyle, val eye: QrEyeStyle, val pupil: QrPupilStyle, val foreground: Rgb, val background: Rgb) {
    companion object {
        val all = listOf(
            QrStylePreset("Classic", QrDotStyle.Square, QrEyeStyle.Square, QrPupilStyle.Square, Rgb.black, Rgb.white),
            QrStylePreset("Rounded", QrDotStyle.Rounded, QrEyeStyle.Rounded, QrPupilStyle.Rounded, Rgb.black, Rgb.white),
            QrStylePreset("Dots", QrDotStyle.Dots, QrEyeStyle.Circle, QrPupilStyle.Circle, Rgb.black, Rgb.white),
            QrStylePreset("Classy", QrDotStyle.Classy, QrEyeStyle.Leaf, QrPupilStyle.Diamond, Rgb(0.2, 0.2, 0.3), Rgb.white),
            QrStylePreset("Elegant", QrDotStyle.Diamond, QrEyeStyle.Shield, QrPupilStyle.Circle, Rgb.black, Rgb.white),
            QrStylePreset("Ocean", QrDotStyle.Rounded, QrEyeStyle.Rounded, QrPupilStyle.Rounded, Rgb(0.1, 0.2, 0.5), Rgb(0.9, 0.95, 1.0)),
            QrStylePreset("Forest", QrDotStyle.Rounded, QrEyeStyle.Leaf, QrPupilStyle.Circle, Rgb(0.1, 0.35, 0.15), Rgb(0.97, 0.96, 0.9)),
            QrStylePreset("Midnight", QrDotStyle.Dots, QrEyeStyle.Circle, QrPupilStyle.Circle, Rgb.white, Rgb(0.08, 0.08, 0.12)),
            QrStylePreset("Soft", QrDotStyle.Cushion, QrEyeStyle.Rounded, QrPupilStyle.Cushion, Rgb(0.4, 0.2, 0.6), Rgb(0.98, 0.96, 1.0)),
        )
    }
}

enum class QrContentType(val title: String) { Text("Text"), Url("URL"), Email("Email"), Phone("Phone"), Wifi("WiFi"), Sms("SMS"), Event("Event") }

enum class QrCorrection(val code: String, val label: String, val level: ErrorCorrectionLevel) {
    Low("L", "Low (7%)", ErrorCorrectionLevel.L),
    Medium("M", "Medium (15%)", ErrorCorrectionLevel.M),
    Quartile("Q", "Quartile (25%)", ErrorCorrectionLevel.Q),
    High("H", "High (30%)", ErrorCorrectionLevel.H),
}

/** Bare VEVENT, ZXing format. Times are instants; [zone] decides the days of an all-day event and the shown times. */
data class QrEvent(
    val location: String = "",
    val notes: String = "",
    val allDay: Boolean = false,
    val start: Instant,
    val end: Instant,
) {
    /** Minute or day granularity. */
    fun endsBeforeStart(zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (allDay) return end.atZone(zone).toLocalDate() < start.atZone(zone).toLocalDate()
        return end.epochSecond.floorDiv(60) < start.epochSecond.floorDiv(60)
    }

    fun vevent(title: String, zone: ZoneId = ZoneId.systemDefault()): String {
        if (title.isBlank() || endsBeforeStart(zone)) return ""
        val lines = mutableListOf("BEGIN:VEVENT", "SUMMARY:${escape(title)}")
        if (allDay) {
            val day = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
            // dtend exclusive for dates
            lines += "DTSTART;VALUE=DATE:${day.format(start.atZone(zone))}"
            lines += "DTEND;VALUE=DATE:${day.format(end.atZone(zone).plusDays(1))}"
        } else {
            // utc for other zones
            val utc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm'00Z'", Locale.ROOT).withZone(ZoneOffset.UTC)
            lines += "DTSTART:${utc.format(start)}"
            lines += "DTEND:${utc.format(end)}"
        }
        if (location.isNotEmpty()) lines += "LOCATION:${escape(location)}"
        if (notes.isNotEmpty()) lines += "DESCRIPTION:${escape(notes)}"
        lines += "END:VEVENT"
        return lines.joinToString("\n")
    }

    fun shown(date: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern(if (allDay) "yyyy-MM-dd" else "yyyy-MM-dd HH:mm", Locale.ROOT).format(date.atZone(zone))

    companion object {
        /** The next whole hour, for an hour. */
        fun startingAfter(now: Instant): QrEvent {
            val hour = now.truncatedTo(ChronoUnit.HOURS).plus(1, ChronoUnit.HOURS)
            return QrEvent(start = hour, end = hour.plus(1, ChronoUnit.HOURS))
        }

        /** RFC 5545 3.3.11 escaping. */
        fun escape(text: String): String {
            val out = StringBuilder()
            var index = 0
            while (index < text.length) {
                when (val char = text[index]) {
                    '\\', ';', ',' -> out.append('\\').append(char)
                    '\r' -> {
                        out.append("\\n")
                        if (text.getOrNull(index + 1) == '\n') index++
                    }
                    '\n' -> out.append("\\n")
                    else -> out.append(char)
                }
                index++
            }
            return out.toString()
        }
    }
}

/** A logo for the centre: what the screen draws, its PNG for SVG, and its size in pixels. */
class QrLogo(val image: ImageBitmap, val png: ByteArray, val width: Int, val height: Int) {
    val aspect get() = width.toFloat() / max(height, 1)
}

/** Inclusive bounds of the modules cleared for a logo. */
data class QrClearing(val minRow: Int, val maxRow: Int, val minColumn: Int, val maxColumn: Int)

/** dt-ios QRRenderer and the QR side of QRGeneratorViewModel. */
object QrEngine {
    val wifiEncryptions = listOf("WPA", "WEP", "None")

    /** Top-left module of each 7×7 finder pattern. */
    fun finderOrigins(n: Int) = listOf(0 to 0, 0 to n - 7, n - 7 to 0)

    fun isInFinder(row: Int, column: Int, n: Int) = finderOrigins(n).any { (r, c) -> row >= r && row < r + 7 && column >= c && column < c + 7 }

    /** Widest logo, in modules, that a code of this size still reads with: under a third of its side, measured with Vision. */
    fun largestLogo(n: Int) = max(3, (n * 0.3).toInt())

    /** Modules cleared for a centred logo: the logo's cells plus a one-module border. */
    fun logoClearing(n: Int, aspect: Float, logoModules: Int): QrClearing {
        val modules = min(logoModules, largestLogo(n))
        val clearWidth: Int
        val clearHeight: Int
        if (aspect >= 1) {
            clearWidth = min(modules, n - 2)
            clearHeight = max(1, (clearWidth / aspect).roundToInt())
        } else {
            clearHeight = min(modules, n - 2)
            clearWidth = max(1, (clearHeight * aspect).roundToInt())
        }
        val padWidth = clearWidth + 2
        val padHeight = clearHeight + 2
        val startColumn = (n - padWidth) / 2
        val startRow = (n - padHeight) / 2
        return QrClearing(startRow, startRow + padHeight - 1, startColumn, startColumn + padWidth - 1)
    }

    /** Where the logo is drawn inside its clearing, in module units. */
    fun logoFrame(clearing: QrClearing, aspect: Float): SheetRect {
        val innerWidth = (clearing.maxColumn - clearing.minColumn - 1).toFloat()
        val innerHeight = (clearing.maxRow - clearing.minRow - 1).toFloat()
        val (w, h) = if (aspect >= innerWidth / innerHeight) innerWidth to innerWidth / aspect else innerHeight * aspect to innerHeight
        return SheetRect(clearing.minColumn + 1 + (innerWidth - w) / 2, clearing.minRow + 1 + (innerHeight - h) / 2, w, h)
    }

    /** One data module. Neighbours that are dark shape the joined styles. */
    fun dotPath(rect: SheetRect, style: QrDotStyle, top: Boolean, bottom: Boolean, left: Boolean, right: Boolean): SheetPath {
        val r = rect.width * 0.4f
        return when (style) {
            QrDotStyle.Square -> SheetPath().rect(rect)
            QrDotStyle.Rounded -> SheetPath().roundRect(
                rect,
                if (!top && !left) r else 0f,
                if (!top && !right) r else 0f,
                if (!bottom && !right) r else 0f,
                if (!bottom && !left) r else 0f,
            )
            QrDotStyle.Dots -> SheetPath().ellipse(rect.inset(rect.width * 0.1f, rect.height * 0.1f))
            QrDotStyle.Diamond -> SheetPath().diamond(SheetRect(rect.midX - rect.width * 0.45f, rect.midY - rect.width * 0.45f, rect.width * 0.9f, rect.width * 0.9f))
            QrDotStyle.Classy -> SheetPath().roundRect(rect, if (!top && !left) r else 0f, 0f, r, 0f)
            QrDotStyle.Cushion -> SheetPath().roundRect(rect.inset(rect.width * 0.05f, rect.height * 0.05f), rect.width * 0.35f)
        }
    }

    /** A finder pattern: the 7×7 ring with its 5×5 hole (fill even-odd) and the 3×3 pupil. */
    fun eyePaths(rect: SheetRect, eye: QrEyeStyle, pupil: QrPupilStyle, module: Float): Pair<SheetPath, SheetPath> {
        fun shape(into: SheetPath, r: SheetRect, roundness: Float, shield: Float) {
            when (eye) {
                QrEyeStyle.Square -> into.rect(r)
                QrEyeStyle.Rounded -> into.roundRect(r, roundness)
                QrEyeStyle.Circle -> into.roundRect(r, r.width / 2)
                QrEyeStyle.Leaf -> into.roundRect(r, r.width / 2, 0f, r.width / 2, 0f)
                QrEyeStyle.Shield -> into.roundRect(r, shield, shield, 0f, 0f)
                QrEyeStyle.Diamond -> into.diamond(r)
            }
        }
        val ring = SheetPath()
        shape(ring, rect, module * 1.5f, module * 2)
        shape(ring, rect.inset(module, module), module, module * 1.2f)
        val p = rect.inset(2 * module, 2 * module)
        val pupilPath = when (pupil) {
            QrPupilStyle.Square -> SheetPath().rect(p)
            QrPupilStyle.Rounded -> SheetPath().roundRect(p, module * 0.8f)
            QrPupilStyle.Circle -> SheetPath().ellipse(p)
            QrPupilStyle.Diamond -> SheetPath().diamond(p)
            QrPupilStyle.Leaf -> SheetPath().roundRect(p, p.width / 2, 0f, p.width / 2, 0f)
            QrPupilStyle.Cushion -> SheetPath().roundRect(p.inset(p.width * 0.05f, p.height * 0.05f), p.width * 0.35f)
        }
        return ring to pupilPath
    }

    /** The ZXing WIFI: spec escapes \ ; , " : — unescaped quotes make scanners read the value as hex or quoted. */
    fun escapeWifi(value: String) = buildString { for (char in value) { if (char in "\\;,\":") append('\\'); append(char) } }

    fun payload(type: QrContentType, content: String, wifiPassword: String, wifiEncryption: String, wifiHidden: Boolean, smsBody: String, event: QrEvent, zone: ZoneId = ZoneId.systemDefault()): String {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return ""
        return when (type) {
            QrContentType.Text -> trimmed
            QrContentType.Url -> if ("://" in trimmed) trimmed else "https://$trimmed"
            QrContentType.Email -> "mailto:$trimmed"
            QrContentType.Phone -> "tel:$trimmed"
            QrContentType.Wifi -> {
                val encryption = if (wifiEncryption == "None") "nopass" else wifiEncryption
                // a secured network without a password would encode an unjoinable code
                if (encryption != "nopass" && wifiPassword.isEmpty()) return ""
                var wifi = "WIFI:T:$encryption;S:${escapeWifi(trimmed)}"
                if (encryption != "nopass") wifi += ";P:${escapeWifi(wifiPassword)}"
                if (wifiHidden) wifi += ";H:true"
                "$wifi;;"
            }
            QrContentType.Sms -> if (smsBody.isEmpty()) "sms:$trimmed" else "sms:$trimmed?body=$smsBody"
            QrContentType.Event -> event.vevent(trimmed, zone)
        }
    }

    /** Plain-text details shown under the code when Add information is on. */
    fun infoLines(type: QrContentType, content: String, wifiPassword: String, wifiEncryption: String, wifiHidden: Boolean, event: QrEvent): List<String> {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return emptyList()
        return when (type) {
            QrContentType.Wifi -> buildList {
                add("Network: $trimmed")
                if (wifiEncryption != "None" && wifiPassword.isNotEmpty()) add("Password: $wifiPassword")
                if (wifiHidden) add("Hidden network")
            }
            QrContentType.Event -> buildList {
                add("Event: $trimmed")
                add("Starts: ${event.shown(event.start)}")
                add("Ends: ${event.shown(event.end)}")
                event.location.trim().takeIf { it.isNotEmpty() }?.let { add("Location: $it") }
            }
            else -> listOf("Content: $trimmed")
        }
    }

    /** Greedy word wrap with hard breaks for words wider than the line (long URLs), a port of the web's. */
    fun wrap(text: String, maxWidth: Float, width: (String) -> Float): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ').filter { it.isNotEmpty() }) {
            var chunk = word
            while (width(chunk) > maxWidth && chunk.length > 1) {
                var cut = chunk.length - 1
                while (cut > 1 && width(chunk.take(cut)) > maxWidth) cut--
                if (line.isNotEmpty()) {
                    lines += line
                    line = ""
                }
                lines += chunk.take(cut)
                chunk = chunk.drop(cut)
            }
            val tried = if (line.isEmpty()) chunk else "$line $chunk"
            if (line.isEmpty() || width(tried) <= maxWidth) {
                line = tried
            } else {
                lines += line
                line = chunk
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    /** The module matrix for [payload], without its quiet zone; null when empty or too long. */
    fun matrix(payload: String, correction: QrCorrection): List<BooleanArray>? {
        if (payload.isEmpty()) return null
        // byte mode carries the utf-8 bytes as core image does, without an eci header
        val bytes = String(payload.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1)
        val code = try {
            Encoder.encode(bytes, correction.level)
        } catch (_: WriterException) {
            return null
        }
        val matrix = code.matrix
        return List(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix[x, y].toInt() == 1 } }
    }

    class Look(
        val dot: QrDotStyle,
        val eye: QrEyeStyle,
        val pupil: QrPupilStyle,
        val ink: Rgb,
        val paper: Rgb,
        val size: Int,
        val logo: QrLogo?,
        val logoModules: Int,
        val info: List<String>,
    )

    /** The sheet and its pixels per module. ISO/IEC 18004 quiet zone. [textWidth] measures a line at a pixel size. */
    fun sheet(matrix: List<BooleanArray>, look: Look, textWidth: (String, Float) -> Float): Pair<CodeSheet, Float> {
        val n = matrix.size
        val side = max(look.size.toFloat(), (n + 8).toFloat())
        val pixels = max(1f, floor(side / (n + 8)))
        val spare = side - pixels * n
        val before = floor(spare / 2)
        val margin = CodeSheet.Margin(before / pixels, before / pixels, (spare - before) / pixels, (spare - before) / pixels)

        val logo = look.logo
        val aspect = logo?.aspect ?: 1f
        val clearing = logo?.let { logoClearing(n, aspect, look.logoModules) }
        val cleared = matrix.mapIndexed { r, row ->
            BooleanArray(row.size) { c ->
                row[c] && !isInFinder(r, c, n) &&
                    (clearing == null || !(r in clearing.minRow..clearing.maxRow && c in clearing.minColumn..clearing.maxColumn))
            }
        }
        val marks = mutableListOf<CodeSheet.Mark>()
        for ((row, column) in finderOrigins(n)) {
            val (ring, pupil) = eyePaths(SheetRect(column.toFloat(), row.toFloat(), 7f, 7f), look.eye, look.pupil, 1f)
            marks += CodeSheet.Mark.Shape(ring, true, look.ink)
            marks += CodeSheet.Mark.Shape(pupil, false, look.ink)
        }
        if (logo != null && clearing != null) marks += CodeSheet.Mark.Image(logo.image, logo.png, logoFrame(clearing, aspect), look.ink)

        var footer = 0f
        if (look.info.isNotEmpty()) {
            // matches web export
            val fontPixels = max(13f, side * 0.055f)
            val lineHeight = (fontPixels * 1.5f).roundToInt().toFloat()
            val wrapped = look.info.flatMap { line -> wrap(line, side * 0.88f) { textWidth(it, fontPixels) } }
            footer = (wrapped.size * lineHeight + fontPixels) / pixels
            val top = n + margin.bottom
            wrapped.forEachIndexed { index, line ->
                val frame = SheetRect(-margin.left, top + index * lineHeight / pixels, side / pixels, lineHeight / pixels)
                marks += CodeSheet.Mark.Text(line, frame, fontPixels / pixels, false, look.ink)
            }
        }

        val style = look.dot
        val sheet = CodeSheet(
            cleared, 1f, margin, footer, look.ink, look.paper, 4f, CodeSheet.Sweep.Zigzag, CodeSheet.Anchor.None,
            isRectilinear = style == QrDotStyle.Square,
            shape = if (style == QrDotStyle.Square) null else { rect, r, c ->
                fun dark(r: Int, c: Int) = r in cleared.indices && c in cleared[r].indices && cleared[r][c]
                dotPath(rect, style, dark(r - 1, c), dark(r + 1, c), dark(r, c - 1), dark(r, c + 1))
            },
            marks = marks,
            marksKey = listOf(
                "$n", look.eye.name, look.pupil.name, CodeSheet.hex(look.ink), logo?.let { "${System.identityHashCode(it)}" } ?: "",
                "${look.logoModules}", "$footer", if (footer > 0) look.info.joinToString("") else "", "${margin.left}",
            ).joinToString("|"),
        )
        return sheet to pixels
    }
}
