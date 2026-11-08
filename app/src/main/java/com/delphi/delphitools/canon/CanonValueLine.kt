package com.delphi.delphitools.canon

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import com.delphi.delphitools.ui.theme.DelphiMono

// delphiMono(14) and delphiMono(11, semibold)
internal val CanonValueMono = TextStyle(fontFamily = DelphiMono, fontSize = 14.sp)
private val indexMono = TextStyle(fontFamily = DelphiMono, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

enum class CanonValueLayout {
    /** The name in a column labelWidth wide, the value on one line beside it, shrinking to fit. */
    Beside,

    /** The name above the value. */
    Above,

    /** The name as wide as it is, the value beside it wrapping by character. */
    Wrapping,

    /** A list index as the name, the value on one line, shrinking to fit. */
    Numbered,

    /** A list index as the name, the value wrapping by character. */
    NumberedWraps,
}

/**
 * Name and value in a flush cell; a tap copies the value with [CanonCopyFeedback], or calls [onCopy] when set.
 * A null value shows a dash and turns copying off. [isLit] marks the chosen line of a list; [tint] colours the
 * value character by character; [fadeDelayMillis] fades a changed value in after that delay.
 */
@Composable
fun CanonValueLine(
    label: String,
    value: String?,
    labelWidth: Dp = 88.dp,
    modifier: Modifier = Modifier,
    layout: CanonValueLayout = CanonValueLayout.Beside,
    isLit: Boolean = false,
    fadeDelayMillis: Int? = null,
    tint: ((Char) -> Color)? = null,
    onCopy: (() -> Unit)? = null,
) {
    val c = LocalDelphiColours.current
    val view = LocalView.current
    val shown = value ?: "—"
    val wraps = layout == CanonValueLayout.Wrapping || layout == CanonValueLayout.NumberedWraps
    val painted = remember(shown, tint, wraps) {
        buildAnnotatedString {
            shown.forEach { char ->
                if (tint == null) append(char) else withStyle(SpanStyle(color = tint(char))) { append(char) }
                // a zero-width space after each character lets a line break anywhere
                if (wraps) append('​')
            }
        }
    }
    val style = CanonValueMono.copy(color = if (isLit) c.primary else c.foreground)
    val valueText: @Composable (Modifier) -> Unit = { mod ->
        val text: @Composable (AnnotatedString) -> Unit = { shownText ->
            when {
                wraps || layout == CanonValueLayout.Above -> BasicText(shownText, style = style)
                else -> BasicText(
                    shownText,
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = FitFirst(
                        style.fontSize,
                        TextAutoSize.StepBased(
                            minFontSize = style.fontSize * if (layout == CanonValueLayout.Numbered) 0.6f else 0.7f,
                            maxFontSize = style.fontSize,
                            stepSize = 0.5.sp,
                        ),
                    ),
                )
            }
        }
        if (fadeDelayMillis == null) {
            Row(mod) { text(painted) }
        } else {
            Crossfade(painted, mod, tween(250, delayMillis = fadeDelayMillis), label = "value") { text(it) }
        }
    }
    val name: @Composable (Modifier) -> Unit = { mod ->
        val numbered = layout == CanonValueLayout.Numbered || layout == CanonValueLayout.NumberedWraps
        Text(
            label,
            style = if (numbered) indexMono else CanonText.captionSemibold,
            color = c.mutedForeground,
            modifier = mod,
        )
    }
    val glyph: @Composable () -> Unit = {
        if (value != null) Icon(Icons.Rounded.ContentCopy, null, tint = c.mutedForeground, modifier = Modifier.size(12.dp))
    }
    val (gap, inset, minHeight) = when (layout) {
        CanonValueLayout.Beside -> Triple(8.dp, 0.dp, CanonMetrics.controlHeight)
        CanonValueLayout.Above -> Triple(12.dp, 10.dp, CanonMetrics.rowHeight)
        CanonValueLayout.Wrapping -> Triple(12.dp, 13.dp, CanonMetrics.controlHeight)
        CanonValueLayout.Numbered -> Triple(10.dp, 0.dp, CanonMetrics.controlHeight)
        CanonValueLayout.NumberedWraps -> Triple(10.dp, 10.dp, CanonMetrics.controlHeight)
    }
    val press = rememberCanonPress()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .canonPress(press, enabled = value != null, role = Role.Button, onClickLabel = "Copy") {
                if (onCopy != null) onCopy() else CanonCopyFeedback.copy(view, shown)
            }
            .background(if (isLit) c.accentBg else c.card)
            .semantics { if (isLit) selected = true }
            .padding(horizontal = CanonMetrics.panelPadding, vertical = inset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        when (layout) {
            CanonValueLayout.Beside -> name(Modifier.width(labelWidth))
            CanonValueLayout.Above -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                name(Modifier)
                valueText(Modifier.fillMaxWidth())
            }
            CanonValueLayout.Wrapping -> name(Modifier.alignByBaseline())
            else -> name(Modifier.widthIn(min = 26.dp))
        }
        when (layout) {
            CanonValueLayout.Above -> Unit
            CanonValueLayout.Wrapping -> valueText(Modifier.weight(1f).alignByBaseline())
            else -> valueText(Modifier.weight(1f))
        }
        glyph()
    }
}

// avoids stepbased relayouts
private data class FitFirst(val full: TextUnit, val search: TextAutoSize) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        if (!performLayout(constraints, text, full).hasVisualOverflow) return full
        return with(search) { getFontSize(constraints, text) }
    }
}
