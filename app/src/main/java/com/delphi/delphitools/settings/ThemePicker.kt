package com.delphi.delphitools.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.canon.canonClickable
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.DelphiColours
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import com.delphi.delphitools.ui.theme.ThemeStore
import com.delphi.delphitools.ui.theme.availableThemes

/** dt-ios SettingsView.themePicker: every theme as a small page in its own colours. */
@Composable
fun ThemePicker(modifier: Modifier = Modifier) {
    val c = LocalDelphiColours.current
    val haptics = LocalHapticFeedback.current
    val store = ThemeStore.of(LocalContext.current)
    val themes = availableThemes()
    val dark = isSystemInDarkTheme()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = remember { (themes.indexOfFirst { it.id == store.id } - 2).coerceAtLeast(0) })
    Column(modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Colours", style = CanonText.body, color = c.foreground, modifier = Modifier.padding(horizontal = 20.dp))
        LazyRow(
            state = list,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().selectableGroup(),
        ) {
            items(themes, key = { it.id }) { theme ->
                val isSelected = theme.id == store.id
                ThemeSwatch(theme.name, theme.colours(dark), isSelected) {
                    if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    store.id = theme.id
                }
            }
        }
    }
}

/** A theme drawn as a small page: headline, caption, a card, a primary bar. */
@Composable
private fun ThemeSwatch(name: String, colours: DelphiColours, isSelected: Boolean, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    Column(
        Modifier
            .width(72.dp)
            .semantics { selected = isSelected }
            .canonClickable(role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(
            Modifier
                .size(66.dp, 84.dp)
                .background(colours.background)
                .border(if (isSelected) 2.dp else 1.dp, if (isSelected) c.foreground else c.border)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.size(34.dp, 4.dp).background(colours.foreground))
            Box(Modifier.size(24.dp, 3.dp).background(colours.mutedForeground))
            Box(
                Modifier
                    .padding(top = 3.dp)
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(colours.card)
                    .border(1.dp, colours.border),
            )
            Box(Modifier.fillMaxWidth().height(12.dp).background(colours.primary))
        }
        Text(
            name,
            style = CanonText.caption.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (isSelected) c.foreground else c.mutedForeground,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

