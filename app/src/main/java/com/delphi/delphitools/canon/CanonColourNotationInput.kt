package com.delphi.delphitools.canon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.colour.Rgb
import com.delphi.delphitools.colour.hex
import com.delphi.delphitools.colour.parseColour
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.delay

/** The text of a [CanonColourNotationInput]: `show` sets it unless it is being typed in, `commit` reads it now. */
@Stable
class CanonColourNotationState {
    var text by mutableStateOf("")
        internal set
    var fails by mutableStateOf(false)
        internal set
    internal var focused = false
    internal var typing by mutableIntStateOf(0)
    internal var parse: (String) -> Boolean = { false }
    internal var shown = Rgb.black

    /** Shows `colour` as hex, unless it is being typed. */
    fun show(colour: Rgb) {
        shown = colour
        show(colour.hex)
    }

    /** Shows `text`, unless the field is being typed in. */
    fun show(text: String) {
        if (focused) return
        this.text = text
        fails = false
    }

    /** Reads the typed text now instead of after the pause. */
    fun commit() {
        typing = 0
        fails = !parse(text) && text.isNotBlank()
    }

    internal fun type(text: String) {
        this.text = text
        fails = false
        typing++
    }
}

/**
 * A colour typed in any notation beside a picker cell. Typed text is read 0.4 s after
 * typing stops, and turns red when it does not read; a picked colour goes to `onColour`
 * at once. `parse` swaps in a tool's own notation: it reads the text, uses it, and returns
 * false when it does not read. `pickerColour` is the colour the picker opens on, the last
 * shown colour by default. `placeholder` shows while the field is empty.
 */
@Composable
fun CanonColourNotationInput(
    onColour: (Rgb) -> Unit,
    modifier: Modifier = Modifier,
    state: CanonColourNotationState = remember { CanonColourNotationState() },
    parse: ((String) -> Boolean)? = null,
    pickerColour: (() -> Rgb)? = null,
    placeholder: String = "oklch(0.64 0.15 264)",
) {
    val c = LocalDelphiColours.current
    var picking by remember { mutableStateOf<Rgb?>(null) }
    state.parse = parse ?: { text -> parseColour(text)?.also(onColour) != null }
    LaunchedEffect(state.typing) {
        if (state.typing == 0) return@LaunchedEffect
        delay(400)
        state.commit()
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = CanonMetrics.controlHeight)
            .background(c.border),
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(c.card)
                .padding(horizontal = CanonMetrics.panelPadding),
            contentAlignment = Alignment.CenterStart,
        ) {
            val style = CanonColourType.mono15.copy(color = if (state.fails) c.destructive else c.foreground)
            if (state.text.isEmpty()) Text(placeholder, style = style.copy(color = c.mutedForeground), maxLines = 1)
            BasicTextField(
                value = state.text,
                onValueChange = state::type,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { state.commit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "Any notation"
                        if (state.fails) error("Invalid")
                    }
                    .onFocusChanged {
                        if (state.focused && !it.isFocused && state.typing != 0) state.commit()
                        state.focused = it.isFocused
                    },
            )
        }
        CanonButton(
            "",
            Modifier
                .width(CanonMetrics.controlHeight)
                .fillMaxHeight()
                .semantics { contentDescription = "Colour" },
            icon = Icons.Outlined.Colorize,
        ) { picking = pickerColour?.invoke() ?: state.shown }
    }
    picking?.let { initial -> CanonColourPickerDialog(initial, onColour) { picking = null } }
}
