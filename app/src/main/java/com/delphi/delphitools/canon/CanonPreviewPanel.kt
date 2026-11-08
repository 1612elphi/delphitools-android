package com.delphi.delphitools.canon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours

/** Framed preview: caption row, hairline, then the content flush beneath. [detail] trails the caption. */
@Composable
fun CanonPreviewPanel(
    caption: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalDelphiColours.current
    Column(
        modifier
            .fillMaxWidth()
            .background(c.border)
            .padding(CanonMetrics.frame),
        verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.muted)
                .padding(horizontal = CanonMetrics.panelPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(caption, style = CanonText.footnoteSemibold, color = c.mutedForeground, modifier = Modifier.weight(1f))
            if (detail != null) {
                Text(detail, style = CanonText.footnoteSemibold.copy(fontWeight = FontWeight.Normal), color = c.mutedForeground, textAlign = TextAlign.End)
            }
        }
        Column(Modifier.fillMaxWidth().background(c.card), content = content)
    }
}
