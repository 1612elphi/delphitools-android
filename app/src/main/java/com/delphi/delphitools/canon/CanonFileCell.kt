package com.delphi.delphitools.canon

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.R
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours

/**
 * File name beside a Choose cell. Choose opens Files for [mimeTypes]; with [photos] set it first
 * offers the Photo Picker beside Files. [onFile] gets the content URI the picker granted.
 * ponytail: delivers the picker's URI, not a private copy; copy it when a tool needs the file past this activity
 */
@Composable
fun CanonFileCell(
    onFile: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    mimeTypes: Array<String> = arrayOf("*/*"),
    photos: PickVisualMedia.VisualMediaType? = null,
    enabled: Boolean = true,
) {
    val c = LocalDelphiColours.current
    val resolver = LocalContext.current.contentResolver
    val deliver by rememberUpdatedState(onFile)
    var name by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val picked = { uri: Uri? ->
        if (uri != null) {
            name = resolver.canonDisplayName(uri)
            deliver(uri)
        }
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), picked)
    val media = rememberLauncherForActivityResult(PickVisualMedia(), picked)
    val shown = name ?: "None"

    Row(
        modifier
            .height(IntrinsicSize.Min)
            .heightIn(min = CanonMetrics.controlHeight)
            .background(c.border),
        horizontalArrangement = Arrangement.spacedBy(CanonMetrics.hairline),
    ) {
        Box(
            Modifier
                .weight(1f)
                .widthIn(min = 64.dp)
                .fillMaxHeight()
                .background(c.card)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                shown,
                style = CanonText.caption,
                color = if (name == null) c.mutedForeground else c.foreground,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                textAlign = TextAlign.End,
            )
        }
        Box {
            CanonButton(
                "Choose",
                Modifier.fillMaxHeight().semantics { stateDescription = shown },
                enabled = enabled,
            ) {
                if (photos == null) files.launch(mimeTypes) else menu = true
            }
            if (photos != null) {
                MenuCellPopup(menu, { menu = false }) {
                    MenuCellItem("Photos", icon = pencil(R.drawable.pencil_images)) {
                        menu = false
                        media.launch(PickVisualMediaRequest(photos))
                    }
                    MenuCellItem("Files", icon = pencil(R.drawable.pencil_folder)) {
                        menu = false
                        files.launch(mimeTypes)
                    }
                }
            }
        }
    }
}
