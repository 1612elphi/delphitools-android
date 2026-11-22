package com.delphi.delphitools.tools.devtools

import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.canon.CanonCanvasInsets
import com.delphi.delphitools.canon.CanonFigure
import com.delphi.delphitools.canon.CanonGlyphFlow
import com.delphi.delphitools.canon.CanonGlyphKind
import com.delphi.delphitools.canon.CanonGlyphMetrics
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.rememberCanonGlyphFlowState
import com.delphi.delphitools.tools.turbonerd.MorseEngine
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiFont
import com.delphi.delphitools.ui.theme.DelphiMono
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp

val CIPHER_BAND_HEIGHT = 176.dp
val CIPHER_COLLAPSED_HEIGHT = 116.dp
private val captionRoom = 24.dp

/** dt-ios SymbolPairStrip.Pair.Face. */
sealed interface CipherFace {
    data class Text(val text: String) : CipherFace

    /** Dots and dashes, drawn as marks to scale. */
    data class Morse(val code: String) : CipherFace

    /** Five A/B letters, drawn as open and filled cells. */
    data class Bacon(val code: String) : CipherFace
}

data class CipherPair(
    val top: CipherFace,
    val bottom: CipherFace,
    val key: String? = null,
    val changes: Boolean = true,
    val kind: CanonGlyphKind = CanonGlyphKind.Glyph,
) {
    companion object {
        val Space = CipherPair(CipherFace.Text(" "), CipherFace.Text(" "), kind = CanonGlyphKind.Space)
        val Newline = CipherPair(CipherFace.Text(""), CipherFace.Text(""), kind = CanonGlyphKind.Newline)
    }
}

private val pairFace = 24.dp
private val pairMiddle = 16.dp
private val pairText = TextStyle(fontFamily = DelphiMono, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
private val keyText = TextStyle(fontFamily = DelphiFont, fontSize = 11.sp, fontWeight = FontWeight.Bold)
private const val MORSE_UNIT = 3f
private const val BACON_CELL = 7f

private fun morseUnits(code: String) = if (code.isEmpty()) 1 else code.sumOf { if (it == '-') 3 else 1 } + code.length - 1

/**
 * dt-ios SymbolPairStrip with every face: each symbol of the input over what it becomes, in wrapped rows. A letter
 * over its shifted letter, a letter over its Morse marks or Bacon cells; an unchanged symbol is muted, and the key
 * letter that did the change (Vigenère) sits between the two.
 */
@Composable
fun CipherPairStrip(pairs: List<CipherPair>, modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widths = remember { HashMap<String, Dp>() }
    fun width(face: CipherFace): Dp = when (face) {
        is CipherFace.Text -> widths.getOrPut(face.text) { with(density) { ceil(measurer.measure(face.text, pairText).size.width.toFloat()).toDp() } }
        is CipherFace.Morse -> (morseUnits(face.code) * MORSE_UNIT).dp
        is CipherFace.Bacon -> (face.code.length * (BACON_CELL + 1) - 1).dp
    }
    val state = rememberCanonGlyphFlowState<CipherPair>(
        CanonGlyphMetrics(pairFace * 2 + pairMiddle, 10.dp, 7.dp),
        measure = {
            when (it.kind) {
                CanonGlyphKind.Newline -> 0.dp
                CanonGlyphKind.Space -> 6.dp
                CanonGlyphKind.Glyph -> maxOf(width(it.top), width(it.bottom), if (it.key == null) 0.dp else 13.dp)
            }
        },
        kind = { it.kind },
    )
    LaunchedEffect(pairs) { state.show(pairs) }

    fun DrawScope.face(face: CipherFace, centre: Offset, colour: Color) {
        when (face) {
            is CipherFace.Text -> {
                val laid = measurer.measure(face.text, pairText.copy(color = colour))
                drawText(laid, topLeft = Offset(centre.x - laid.size.width / 2, centre.y - laid.size.height / 2))
            }
            is CipherFace.Morse -> {
                val unit = MORSE_UNIT.dp.toPx()
                var x = centre.x - morseUnits(face.code) * unit / 2
                for (symbol in face.code) {
                    val length = if (symbol == '-') 3 else 1
                    drawRect(colour, Offset(x, centre.y - 2.dp.toPx()), Size(length * unit, 4.dp.toPx()))
                    x += (length + 1) * unit
                }
            }
            is CipherFace.Bacon -> {
                val cell = BACON_CELL.dp.toPx()
                val hair = CanonMetrics.hairline.toPx()
                var x = centre.x - (face.code.length * (BACON_CELL + 1) - 1).dp.toPx() / 2
                for (letter in face.code) {
                    val origin = Offset(x, centre.y - cell / 2)
                    if (letter == 'B') {
                        drawRect(colour, origin, Size(cell, cell))
                    } else {
                        drawRect(colour, origin + Offset(hair / 2, hair / 2), Size(cell - hair, cell - hair), style = Stroke(hair))
                    }
                    x += cell + 1.dp.toPx()
                }
            }
        }
    }

    CanonGlyphFlow(
        state,
        modifier.fillMaxSize(),
        paintEmpty = { area ->
            val half = (pairFace * 2 + pairMiddle).toPx() / 2 - pairFace.toPx() / 2
            for (y in listOf(area.center.y - half, area.center.y + half)) {
                drawRect(c.mutedForeground.copy(alpha = 0.3f), Offset(area.left, y - 0.5f), Size(area.width, CanonMetrics.hairline.toPx()))
            }
        },
    ) { pair, _, frame ->
        if (pair.kind != CanonGlyphKind.Glyph) return@CanonGlyphFlow
        val lit = if (pair.changes) c.primary else c.mutedForeground
        val plain = if (pair.changes) c.foreground else c.mutedForeground
        val faceHeight = pairFace.toPx()
        face(pair.top, Offset(frame.center.x, frame.top + faceHeight / 2), plain)
        face(pair.bottom, Offset(frame.center.x, frame.bottom - faceHeight / 2), lit)
        val mid = frame.top + faceHeight + pairMiddle.toPx() / 2
        val key = pair.key
        when {
            key != null -> {
                val side = 13.dp.toPx()
                drawRect(c.accentBg, Offset(frame.center.x - side / 2, mid - side / 2), Size(side, side))
                val laid = measurer.measure(key, keyText.copy(color = c.primary))
                drawText(laid, topLeft = Offset(frame.center.x - laid.size.width / 2, mid - laid.size.height / 2))
            }
            pair.changes -> drawRect(c.mutedForeground.copy(alpha = 0.45f), Offset(frame.center.x - 0.5f, mid - 5.dp.toPx()), Size(CanonMetrics.hairline.toPx(), 10.dp.toPx()))
            else -> drawRect(c.mutedForeground.copy(alpha = 0.35f), Offset(frame.center.x - 1.dp.toPx(), mid - 1.dp.toPx()), Size(2.dp.toPx(), 2.dp.toPx()))
        }
    }
}

private const val RAIL_SETTLE_MS = 500
private const val RAIL_PITCH_LEAST = 11f
private const val RAIL_PITCH_MOST = 26f

/** dt-ios RailFenceZigzag: the text on its rails, joined by the zig-zag; a change of rails springs the characters to their new heights. */
@Composable
fun RailFenceZigzag(text: String, rails: Int, modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val top = LocalCanonCanvas.current.top
    val characters = remember(text) { EncoderEngine.characters(text) }
    val targets = remember(characters.size, rails) {
        val effective = maxOf(2, minOf(rails, maxOf(characters.size, 2)))
        CipherEngine.railPattern(characters.size, rails).map { it.toFloat() / (effective - 1) }
    }
    val memo = remember { CipherRailMemo() }
    val progress = remember { Animatable(1f) }
    fun eased(p: Float) = if (p >= 1f) 1f else (1 - exp(-7.0 * p) * cos(8.0 * p)).toFloat()
    fun presented(index: Int, p: Float): Float {
        val target = memo.target.getOrElse(index) { 0f }
        if (p >= 1f || index !in memo.from.indices) return target
        return memo.from[index] + (target - memo.from[index]) * eased(p)
    }
    if (targets != memo.target) {
        val p = progress.value
        val shown = memo.target.indices.map { presented(it, p) }
        val movesRails = rails != memo.rails && memo.rails != 0
        memo.target = targets
        memo.rails = rails
        if (movesRails) {
            memo.from = shown
            memo.moves++
        }
    }
    LaunchedEffect(memo.moves) {
        if (memo.moves == 0) return@LaunchedEffect
        val animates = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        if (!animates) return@LaunchedEffect progress.snapTo(1f)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(RAIL_SETTLE_MS, easing = LinearEasing))
    }

    Canvas(modifier.fillMaxSize()) {
        val p = progress.value
        val padding = CanonMetrics.panelPadding.toPx()
        val hair = CanonMetrics.hairline.toPx()
        val area = Rect(padding, top.toPx() + padding * 0.75f, size.width - padding, size.height - padding * 0.75f)
        if (area.width <= 0 || area.height <= 0) return@Canvas
        if (characters.isEmpty()) {
            for (rail in 0 until rails) {
                val y = area.top + area.height * rail / maxOf(rails - 1, 1)
                drawRect(c.mutedForeground.copy(alpha = 0.3f), Offset(area.left, y - 0.5f), Size(area.width, hair))
            }
            return@Canvas
        }
        val pitch = (area.width / characters.size).coerceIn(RAIL_PITCH_LEAST.dp.toPx(), RAIL_PITCH_MOST.dp.toPx())
        val columns = minOf(characters.size, (area.width / pitch).toInt())
        val style = TextStyle(fontFamily = DelphiMono, fontWeight = FontWeight.SemiBold, fontSize = minOf(16.dp.toPx(), pitch * 0.95f).toSp())
        val half = measurer.measure("0", style).size.height / 2f
        val track = Rect(area.left, area.top + half, area.right, area.top + half + maxOf(0f, area.height - half * 2))
        val effective = maxOf(2, minOf(rails, characters.size))
        val rail = CipherEngine.railPattern(characters.size, rails)
        for (index in 0 until effective) {
            val y = track.top + track.height * index / (effective - 1)
            drawRect(c.mutedForeground.copy(alpha = 0.14f), Offset(area.left, y - 0.5f), Size(area.width, hair))
        }
        val points = (0 until columns).map { Offset(area.left + (it + 0.5f) * pitch, track.top + track.height * presented(it, p)) }
        if (points.size > 1) {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (point in points.drop(1)) lineTo(point.x, point.y)
            }
            drawPath(path, c.mutedForeground.copy(alpha = 0.4f), style = Stroke(hair))
        }
        val fades = columns < characters.size
        points.forEachIndexed { index, point ->
            val character = characters[index]
            val ink = when {
                character == " " -> c.mutedForeground
                rail[index] % 2 == 0 -> c.foreground
                else -> c.primary
            }
            val fade = if (fades) minOf(1f, (columns - index) / 4f) else 1f
            drawRect(c.card, Offset(point.x - pitch / 2 + 1, point.y - half), Size(pitch - 2, half * 2))
            val laid = measurer.measure(if (character == " ") "·" else character, style.copy(color = ink.copy(alpha = ink.alpha * fade)))
            drawText(laid, topLeft = Offset(point.x - laid.size.width / 2, point.y - laid.size.height / 2))
        }
    }
}

private class CipherRailMemo {
    var target: List<Float> = emptyList()
    var from: List<Float> = emptyList()
    var rails = 0
    var moves = 0
}

/** dt-ios CipherPicture.caption: the cipher's name and the setting that drives it. */
fun cipherCaption(cipher: CipherId, params: CipherParams): Pair<String, String> = when (cipher) {
    CipherId.Caesar -> "Caesar (shift ${params.caesarShift})" to ""
    CipherId.Vigenere -> cipher.label to if (params.vigenereKey.isEmpty()) "" else "key: ${params.vigenereKey.uppercase()}"
    CipherId.Affine -> cipher.label to "a=${params.affineA}, b=${params.affineB}"
    CipherId.RailFence -> cipher.label to "${params.rails} rails"
    else -> cipher.label to ""
}

private fun cipherCode(cipher: CipherId) = when (cipher) {
    CipherId.Base32 -> BitRegroup.Code.Base32
    CipherId.Hex -> BitRegroup.Code.Hex
    CipherId.Binary -> BitRegroup.Code.Binary
    else -> BitRegroup.Code.Base64
}

private fun encoded(text: String, cipher: CipherId): List<Char> {
    var cleaned = text.filterNot { it.isWhitespace() }
    if (cipher == CipherId.Hex && cleaned.lowercase().startsWith("0x")) cleaned = cleaned.drop(2)
    if (cipher == CipherId.Binary) cleaned = cleaned.filter { it == '0' || it == '1' }
    return cleaned.toList()
}

private fun blank(character: String) = when {
    character == "\n" || character == "\r\n" || character == "\r" -> CipherPair.Newline
    character.isNotEmpty() && character.all { it.isWhitespace() } -> CipherPair.Space
    else -> null
}

/** dt-ios CipherPicture.pairs: each symbol of [input] over what it becomes. */
fun cipherPairs(cipher: CipherId, encode: Boolean, params: CipherParams, input: String, output: String): List<CipherPair> {
    fun text(s: String) = CipherFace.Text(s)
    fun passes(character: String) = CipherPair(text(character), text(""), changes = false)
    return when (cipher) {
        CipherId.Morse -> if (encode) {
            EncoderEngine.characters(input).map { character ->
                blank(character) ?: MorseEngine.table[character.uppercase().singleOrNull() ?: ' ']
                    ?.let { CipherPair(text(character), CipherFace.Morse(it)) } ?: passes(character)
            }
        } else {
            MorseEngine.words(input, minimumGap = 2).flatMapIndexed { index, word ->
                (if (index > 0) listOf(CipherPair.Space) else emptyList()) +
                    word.map { CipherPair(CipherFace.Morse(it), text(MorseEngine.reverse[it]?.toString() ?: "?")) }
            }
        }
        CipherId.A1z26 -> if (encode) {
            EncoderEngine.characters(input).map { character ->
                val letter = character.singleOrNull()?.uppercaseChar()
                blank(character) ?: if (letter != null && letter in 'A'..'Z') CipherPair(text(character), text((letter.code - 64).toString())) else passes(character)
            }
        } else {
            MorseEngine.words(input, minimumGap = 2).flatMapIndexed { index, word ->
                (if (index > 0) listOf(CipherPair.Space) else emptyList()) +
                    CipherEngine.a1z26Pieces(word).map { CipherPair(text(it), text(CipherEngine.a1z26Letter(it)?.toString() ?: "?")) }
            }
        }
        CipherId.Bacon -> if (encode) {
            EncoderEngine.characters(input).map { character ->
                blank(character) ?: CipherEngine.bacon[character.uppercase().singleOrNull() ?: ' ']
                    ?.let { CipherPair(text(character), CipherFace.Bacon(it)) } ?: passes(character)
            }
        } else {
            val letters = input.uppercase().filter { it == 'A' || it == 'B' }
            (0..letters.length - 5 step 5).map { start ->
                val code = letters.substring(start, start + 5)
                CipherPair(CipherFace.Bacon(code), text(CipherEngine.baconLetter(code)?.toString() ?: "?"))
            }
        }
        else -> {
            // scalars map one-to-one
            val from = input.codePoints().toArray()
            val to = output.codePoints().toArray()
            if (from.size != to.size) return emptyList()
            val key = params.vigenereKey.uppercase().filter { it in 'A'..'Z' }
            var keyIndex = 0
            from.indices.map { i ->
                val character = String(Character.toChars(from[i]))
                blank(character) ?: run {
                    var letter: String? = null
                    if (cipher == CipherId.Vigenere && key.isNotEmpty() && from[i] < 0x80 && Character.isLetter(from[i])) {
                        letter = key[keyIndex % key.length].toString()
                        keyIndex++
                    }
                    CipherPair(text(character), text(String(Character.toChars(to[i]))), letter, from[i] != to[i])
                }
            }
        }
    }
}

/** What the banner draws: one cipher at work on one text. */
data class CipherScene(
    val cipher: CipherId,
    val encode: Boolean,
    val params: CipherParams,
    val input: String,
    val output: String,
    val name: String,
    val detail: String,
    val score: Double?,
)

private enum class CipherStrip { Pairs, Bits, Fence }

/**
 * dt-ios CipherPicture: the cipher at work under a caption with the candidate's score. Rail fence draws its zig-zag,
 * the byte encodings their regrouped bits, every other cipher each symbol over what it becomes.
 */
@Composable
fun CipherPicture(scene: CipherScene, modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    val top = LocalCanonCanvas.current.top
    val strip = when (scene.cipher) {
        CipherId.RailFence -> CipherStrip.Fence
        CipherId.Base64, CipherId.Base32, CipherId.Hex, CipherId.Binary -> CipherStrip.Bits
        else -> CipherStrip.Pairs
    }
    val code = cipherCode(scene.cipher)
    // fading face keeps content
    val reading = remember(scene.input, scene.encode, scene.cipher) {
        if (scene.encode) BitRegroup.encoding(scene.input.toByteArray(), code) else BitRegroup.decoding(encoded(scene.input, scene.cipher), code)
    }
    val pairs = remember(scene) { cipherPairs(scene.cipher, scene.encode, scene.params, scene.input, scene.output) }
    val figureAlpha by animateFloatAsState(if (scene.score != null) 1f else 0f, tween(200), label = "figure")
    val figure = remember { CipherFigureMemo() }
    scene.score?.let { figure.text = Math.round(it * 100).toString() }

    Box(
        modifier
            .fillMaxSize()
            .semantics {
                contentDescription = listOf(scene.name, scene.detail).filter { it.isNotEmpty() }.joinToString(", ")
                stateDescription = scene.output
            },
    ) {
        CompositionLocalProvider(LocalCanonCanvas provides CanonCanvasInsets(top = top + captionRoom)) {
            Crossfade(strip, Modifier.fillMaxSize(), tween(250), label = "strip") { shown ->
                when (shown) {
                    CipherStrip.Pairs -> CipherPairStrip(pairs)
                    CipherStrip.Bits -> BitRegroupStrip(reading, code = code, codeWeight = FontWeight.Medium)
                    CipherStrip.Fence -> RailFenceZigzag(if (scene.encode) scene.input else scene.output, scene.params.rails)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = CanonMetrics.panelPadding, end = CanonMetrics.panelPadding, top = top + CanonMetrics.panelPadding * 0.6f),
        ) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = c.foreground)) { append(scene.name) }
                    if (scene.detail.isNotEmpty()) withStyle(SpanStyle(color = c.mutedForeground)) { append("  " + scene.detail) }
                },
                style = CanonText.footnoteSemibold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp).alignByBaseline(),
            )
            CanonFigure(figure.text, Modifier.alpha(figureAlpha).alignByBaseline(), unit = "%", size = 20.sp)
        }
    }
}

private class CipherFigureMemo {
    var text = ""
}
