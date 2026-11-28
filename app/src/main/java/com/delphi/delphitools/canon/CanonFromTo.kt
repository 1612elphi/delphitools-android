package com.delphi.delphitools.canon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.R
import com.delphi.delphitools.ui.theme.LocalDelphiColours

/**
 * Two menu cells with an arrow between: a conversion from one choice to another.
 * [targets] gives the second menu its own choices.
 */
@Composable
fun CanonFromTo(
    items: List<String>,
    from: Int?,
    to: Int?,
    onFromChange: (Int) -> Unit,
    onToChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    targets: List<String>? = null,
    fromLabel: String = "From",
    toLabel: String = "To",
    enabled: Boolean = true,
) {
    val c = LocalDelphiColours.current
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(c.border),
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        CanonMenuCell(items, from, onFromChange, Modifier.weight(1f).fillMaxHeight(), label = fromLabel, enabled = enabled)
        Box(
            Modifier
                .width(CanonMetrics.controlHeight)
                .fillMaxHeight()
                .background(c.card),
            contentAlignment = Alignment.Center,
        ) {
            Icon(pencil(R.drawable.pencil_arrow_right), contentDescription = null, tint = c.mutedForeground, modifier = Modifier.size(14.dp))
        }
        CanonMenuCell(targets ?: items, to, onToChange, Modifier.weight(1f).fillMaxHeight(), label = toLabel, enabled = enabled)
    }
}
