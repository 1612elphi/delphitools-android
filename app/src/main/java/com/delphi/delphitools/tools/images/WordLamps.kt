package com.delphi.delphitools.tools.images

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours

enum class WordLamp { Off, On, Struck }

val WordLampRow = 24.dp
private val lampGap = 6.dp

/** Height of [count] lamps in rows of [columns]. */
fun wordLampsHeight(count: Int, columns: Int): Dp {
    val rows = (count + columns - 1) / maxOf(1, columns)
    return if (rows == 0) 0.dp else WordLampRow * rows + lampGap * (rows - 1)
}

/**
 * dt-ios WordLamps: named lamps in rows. On is lit in the primary colour, off is dim, struck is crossed out in red
 * and dimmed. [stagger] is milliseconds between one changed lamp and the next, in reading order.
 */
@Composable
fun WordLamps(names: List<String>, states: List<WordLamp>, columns: Int, modifier: Modifier = Modifier, stagger: Long = 80) {
    var previous by remember { mutableStateOf(states) }
    val order = remember(states) {
        var rank = 0
        states.indices.map { if (states[it] != previous.getOrNull(it)) rank++ else 0 }
    }
    LaunchedEffect(states) { previous = states }
    fun delay(index: Int) = (order[index] * stagger).toInt()
    if (names.size <= columns) {
        Row(modifier.height(WordLampRow), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            names.forEachIndexed { index, name -> Lamp(name, states[index], delay(index)) }
        }
        return
    }
    Column(modifier.height(wordLampsHeight(names.size, columns)), verticalArrangement = Arrangement.spacedBy(lampGap)) {
        names.indices.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth().height(WordLampRow)) {
                row.forEach { index -> Lamp(names[index], states[index], delay(index), Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Lamp(name: String, state: WordLamp, delayMs: Int, modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    val lit = state == WordLamp.On
    val struck = state == WordLamp.Struck
    val fade = tween<androidx.compose.ui.graphics.Color>(250, delayMs + if (struck) 180 else 0)
    val lamp by animateColorAsState(if (lit) c.primary else c.mutedForeground.copy(alpha = 0.25f), fade)
    val ink by animateColorAsState(
        when {
            lit -> c.primary
            struck -> c.mutedForeground.copy(alpha = 0.7f)
            else -> c.mutedForeground.copy(alpha = 0.4f)
        },
        fade,
    )
    val strike by animateFloatAsState(if (struck) 1f else 0f, tween(if (struck) 220 else 150, delayMs))
    val destructive = c.destructive
    // ponytail: no glow round a lit lamp; add a blurred shadow if it reads flat
    Row(
        modifier.height(WordLampRow).semantics(mergeDescendants = true) {
            selected = lit
            if (struck) stateDescription = "Removed"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(7.dp).background(lamp))
        Text(
            name.uppercase(),
            style = CanonText.caption.copy(fontWeight = FontWeight.Bold),
            color = ink,
            maxLines = 1,
            modifier = Modifier.drawWithContent {
                drawContent()
                if (strike > 0f) {
                    val start = -3.dp.toPx()
                    val end = size.width + 3.dp.toPx()
                    val y = size.height / 2 + 0.5f
                    drawLine(destructive, Offset(start, y), Offset(start + (end - start) * strike, y), 2.dp.toPx())
                }
            },
        )
    }
}
