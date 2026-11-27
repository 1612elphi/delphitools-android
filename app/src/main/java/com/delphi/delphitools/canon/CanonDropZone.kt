package com.delphi.delphitools.canon

import android.content.ClipData
import android.content.ClipDescription
import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun accepts(type: String?, mimeTypes: Array<String>) =
    type != null && mimeTypes.any { ClipDescription.compareMimeTypes(type, it) }

private fun ClipData.uris(): List<Uri> = (0 until itemCount).mapNotNull { getItemAt(it).uri }

private fun decodeImage(resolver: ContentResolver, uri: Uri): ImageBitmap? = runCatching {
    // software pixels: tools read them back; ImageDecoder applies the EXIF orientation
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, _, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }.asImageBitmap()
}.getOrNull()

/**
 * Import area: a drop target over a flush bar of sources (Photos, Files, Paste).
 * Images go to [onImages], decoded with their file names less the extension (no names after a
 * paste); without [onImages], the URIs go to [onFiles]. [photos] null leaves out the Photo Picker.
 */
@Composable
fun CanonDropZone(
    prompt: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Collections,
    mimeTypes: Array<String> = arrayOf("image/*"),
    photos: PickVisualMedia.VisualMediaType? = PickVisualMedia.ImageOnly,
    multiple: Boolean = false,
    paste: Boolean = true,
    onImages: ((images: List<ImageBitmap>, names: List<String>) -> Unit)? = null,
    onFiles: ((List<Uri>) -> Unit)? = null,
) {
    val c = LocalDelphiColours.current
    val resolver = LocalContext.current.contentResolver
    val activity = LocalActivity.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val images by rememberUpdatedState(onImages)
    val files by rememberUpdatedState(onFiles)
    var over by remember { mutableStateOf(false) }

    fun deliver(all: List<Uri>, pasted: Boolean) {
        val uris = if (multiple) all else all.take(1)
        if (uris.isEmpty()) return
        val decode = images
        if (decode == null) {
            files?.invoke(uris)
            return
        }
        scope.launch {
            val decoded = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    decodeImage(resolver, uri)?.let { it to (resolver.canonDisplayName(uri)?.substringBeforeLast('.') ?: "") }
                }
            }
            if (decoded.isNotEmpty()) decode(decoded.map { it.first }, if (pasted) emptyList() else decoded.map { it.second })
        }
    }

    LaunchedEffect(Unit) { deliver(CanonHost.pendingFiles(mimeTypes, multiple), false) }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { deliver(listOfNotNull(it), false) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { deliver(it, false) }
    val pickPhoto = rememberLauncherForActivityResult(PickVisualMedia()) { deliver(listOfNotNull(it), false) }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { deliver(it, false) }

    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                over = true
            }

            override fun onExited(event: DragAndDropEvent) {
                over = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                over = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                over = false
                val drag = event.toAndroidDragEvent()
                // content URIs from another app need the drop's grant
                activity?.requestDragAndDropPermissions(drag)
                deliver(drag.clipData.uris().filter { accepts(resolver.getType(it), mimeTypes) }, false)
                return true
            }
        }
    }

    Column(modifier.fillMaxWidth().background(c.border), verticalArrangement = Arrangement.spacedBy(CanonMetrics.hairline)) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .background(if (over) c.accentBg else c.card)
                .dragAndDropTarget(
                    shouldStartDragAndDrop = { event -> event.mimeTypes().any { accepts(it, mimeTypes) } },
                    target = target,
                )
                .semantics(mergeDescendants = true) { contentDescription = prompt }
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, tint = if (over) c.primary else c.primary.copy(alpha = 0.5f), modifier = Modifier.size(32.dp))
                Text(prompt, style = CanonText.subheadline.copy(fontWeight = FontWeight.Normal), color = c.mutedForeground, textAlign = TextAlign.Center)
            }
        }
        CanonGrid {
            val cell = Modifier.weight(1f).fillMaxHeight()
            if (photos != null) {
                CanonButton("Photos", cell, icon = Icons.Outlined.PhotoLibrary) {
                    val request = PickVisualMediaRequest(photos)
                    if (multiple) pickPhotos.launch(request) else pickPhoto.launch(request)
                }
            }
            CanonButton("Files", cell, icon = Icons.Outlined.Folder) {
                if (multiple) pickFiles.launch(mimeTypes) else pickFile.launch(mimeTypes)
            }
            if (paste) {
                CanonButton("Paste", cell, icon = Icons.Outlined.ContentPaste) {
                    scope.launch { pasteFrom(clipboard.getClipEntry()?.clipData, resolver, mimeTypes)?.let { deliver(it, true) } }
                }
            }
        }
    }
}

private fun pasteFrom(clip: ClipData?, resolver: ContentResolver, mimeTypes: Array<String>): List<Uri>? =
    clip?.uris()?.filter { accepts(resolver.getType(it), mimeTypes) }?.takeIf { it.isNotEmpty() }
