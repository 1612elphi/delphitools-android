package com.delphi.delphitools.canon

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** The name the provider shows for [uri], else its last path segment. */
internal fun ContentResolver.canonDisplayName(uri: Uri): String? =
    runCatching {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment

/** Text in its detected encoding: UTF-16 or UTF-8 by byte order mark, else UTF-8, else Windows-1252. */
internal fun decodeTextFile(bytes: ByteArray): String {
    fun has(vararg mark: Int) = bytes.size >= mark.size && mark.indices.all { bytes[it] == mark[it].toByte() }
    return when {
        has(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        has(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        has(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        else -> try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }
}

/** Opens a text file through the Storage Access Framework. [fileName] is the name of the file last read. */
@Stable
class CanonTextFilePicker internal constructor() {
    var fileName by mutableStateOf<String?>(null)
        internal set
    internal var launcher: ActivityResultLauncher<Array<String>>? = null
    internal var types: Array<String> = emptyArray()

    fun open() {
        launcher?.launch(types)
    }
}

/**
 * A picker whose [CanonTextFilePicker.open] lets the user choose one file of [mimeTypes]; its text,
 * read off the main thread, goes to [onText]. A file that cannot be read sends nothing.
 */
@Composable
fun rememberCanonTextFilePicker(
    mimeTypes: Array<String> = arrayOf("text/*"),
    onText: (String) -> Unit,
): CanonTextFilePicker {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val deliver by rememberUpdatedState(onText)
    val picker = remember { CanonTextFilePicker() }
    picker.types = mimeTypes
    picker.launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                    ?.let { decodeTextFile(it) to resolver.canonDisplayName(uri) }
            } ?: return@launch
            picker.fileName = read.second
            deliver(read.first)
        }
    }
    return picker
}
