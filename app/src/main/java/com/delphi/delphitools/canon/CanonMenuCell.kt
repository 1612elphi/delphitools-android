package com.delphi.delphitools.canon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.R
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours

/**
 * Popup list of choices in the Canon look: square, framed, card background.
 * ponytail: a restyled Material DropdownMenu; replace with the Canon menu once one is ported
 */
@Composable
internal fun MenuCellPopup(expanded: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalDelphiColours.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = c.card,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(CanonMetrics.hairline, c.border),
        modifier = Modifier.widthIn(min = 200.dp),
        content = content,
    )
}

@Composable
internal fun MenuCellItem(text: String, checked: Boolean = false, icon: ImageVector? = null, onClick: () -> Unit) {
    val c = LocalDelphiColours.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = CanonMetrics.controlHeight)
            .canonClickable(onClick = onClick)
            .semantics { selected = checked }
            .padding(horizontal = CanonMetrics.panelPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = c.foreground, modifier = Modifier.size(18.dp))
        Text(text, style = CanonText.body, color = c.foreground, modifier = Modifier.weight(1f))
        if (checked) Icon(pencil(R.drawable.pencil_check), contentDescription = null, tint = c.primary, modifier = Modifier.size(18.dp))
    }
}

/**
 * A flush cell that shows one choice and opens a menu of all of them.
 * A null [selected] shows [placeholder]: a value set elsewhere that matches no choice.
 */
@Composable
fun CanonMenuCell(
    items: List<String>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "—",
    label: String? = null,
    enabled: Boolean = true,
) {
    val c = LocalDelphiColours.current
    var open by remember { mutableStateOf(false) }
    val name = label ?: claimRowLabel()
    val title = selected?.let(items::getOrNull)
    val ink = if (enabled) c.foreground else c.foreground.copy(alpha = 0.4f)
    Box(modifier, propagateMinConstraints = true) {
        Row(
            Modifier
                .heightIn(min = CanonMetrics.controlHeight)
                .canonClickable(enabled, role = Role.DropdownList) { open = true }
                .background(c.card)
                .semantics {
                    if (name != null) contentDescription = name
                    if (title != null) stateDescription = title
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            Text(
                title ?: placeholder,
                style = CanonText.bodyMedium,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(pencil(R.drawable.pencil_chevrons_up_down), contentDescription = null, tint = ink, modifier = Modifier.size(14.dp))
        }
        MenuCellPopup(open, { open = false }) {
            items.forEachIndexed { index, item ->
                MenuCellItem(item, checked = index == selected) {
                    open = false
                    if (index != selected) onSelect(index)
                }
            }
        }
    }
}
