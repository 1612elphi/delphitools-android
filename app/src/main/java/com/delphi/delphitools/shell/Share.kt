package com.delphi.delphitools.shell

import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.LayoutBackDismiss
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import com.delphi.delphitools.widgets.Shortcuts
import com.delphi.delphitools.canon.decodeTextFile
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ponytail: shared text files past this are refused, as on iOS; no streaming into the tools
private const val MAX_TEXT_BYTES = 8_000_000

/** dt-ios ShareKind: what a share holds, and the tools that take it. */
enum class ShareKind {
    Images, Pdfs, Movie, Audio, Subtitles, Fonts, Svg, Text, WebUrl;

    val allowsSeveral get() = this == Images || this == Pdfs || this == Fonts

    /** Offer order follows iOS; ids with no Android tool drop out at lookup. */
    fun toolIds(count: Int, text: String?): List<String> = when (this) {
        Images -> if (count > 1) {
            listOf("image-stitcher", "image-to-pdf", "zine-imposer", "document-scanner", "base64-image-encoder")
        } else {
            listOf(
                "image-converter", "image-compressor", "image-atlas", "metadata-stripper", "background-remover",
                "image-clipper", "image-masker", "image-deskewer", "image-splitter", "image-tracer", "artwork-enhancer",
                "favicon-genny", "base64-image-encoder", "image-stitcher", "document-scanner", "image-to-pdf",
                "palette-extractor", "pixel-picker", "colorblind-sim", "social-cropper", "matte-generator",
                "watermarker", "scroll-generator",
            )
        }
        Pdfs -> if (count > 1) {
            listOf("pdf-organiser")
        } else {
            listOf("pdf-compressor", "pdf-organiser", "pdf-page-numberer", "pdf-rotate-crop", "image-to-pdf", "pdf-preflight", "imposer")
        }
        Movie -> listOf("video-atlas", "video-trimmer", "video-muter", "video-to-gif", "frame-extractor", "audio-extractor", "auto-subtitle", "subtitle-studio")
        Audio -> listOf("audio-atlas", "audio-trimmer", "audio-normaliser", "waveform-genny", "auto-subtitle")
        Subtitles -> listOf("subtitle-converter", "subtitle-studio")
        Fonts -> if (count > 1) listOf("font-installer") else listOf("font-explorer", "font-installer")
        Svg -> listOf("svg-optimiser")
        WebUrl -> listOf("qr-genny", "meta-tag-genny", "request-builder")
        Text -> listOf(
            "word-counter", "text-editor", "markdown-writer", "doc-converter", "text-diff", "json-formatter",
            "jwt-decoder", "regex-tester", "encoder", "decoder", "qr-genny", "large-type", "braille-converter",
            "morse-code", "nato-phonetic", "ipa-transcriber", "shavian-transliterator",
        ) + if (mightBeColour(text)) listOf("colour-converter", "contrast-checker") else emptyList()
    }

    companion object {
        private val fontExtensions = setOf("ttf", "otf", "ttc", "woff", "woff2")

        /** The file name's extension decides before the MIME type: providers report subtitles and fonts as text or bytes. */
        fun of(mimeType: String?, extension: String?): ShareKind? {
            val mime = mimeType.orEmpty().lowercase()
            return when {
                extension == "svg" || mime == "image/svg+xml" -> Svg
                extension == "srt" || extension == "vtt" || mime == "application/x-subrip" || mime == "text/vtt" -> Subtitles
                extension in fontExtensions || mime.startsWith("font/") -> Fonts
                mime.startsWith("image/") -> Images
                mime == "application/pdf" -> Pdfs
                mime.startsWith("video/") -> Movie
                mime.startsWith("audio/") -> Audio
                mime.startsWith("text/") || mime == "application/json" -> Text
                else -> null
            }
        }

        fun isWebUrl(text: String): Boolean {
            val trimmed = text.trim()
            return trimmed.none(Char::isWhitespace) && (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true))
        }

        // ponytail: the colour tools parse it; a short single line is the cheap filter here
        private fun mightBeColour(text: String?): Boolean {
            val trimmed = text?.trim() ?: return false
            return trimmed.isNotEmpty() && trimmed.length <= 64 && '\n' !in trimmed
        }
    }
}

class SharedFile(val uri: Uri, val mimeType: String?)

/** dt-ios SharedContent: one share, reduced to the files of its first kind, or its text. */
class SharedContent(val kind: ShareKind, val files: List<SharedFile> = emptyList(), val text: String? = null) {
    val tools: List<Tool> get() = kind.toolIds(files.size, text).mapNotNull(::toolWithId)
}

/** dt-ios ShareHandoff and PendingToolData: a share waits here for the picker, then for the picked tool's page. */
object ShareInbox {
    /** the tools the picker lists for the waiting share; empty when no tool takes it, null when no picker shows */
    var offer by mutableStateOf<List<Tool>?>(null)
        private set

    /** a tool to open with no picker: a widget, a shortcut or a direct share target named it */
    var launch by mutableStateOf<Tool?>(null)
        private set

    private var picked: SharedContent? = null
    private var staged: SharedContent? = null
    private var reading: Job? = null
    private val scope = MainScope()

    /** Takes a share or a `delphitools://open?tool=` link from [intent]; the picker or the tool shows once it is read. */
    fun read(context: Context, intent: Intent) {
        val link = intent.data?.takeIf { it.scheme == "delphitools" }
        if (link != null) {
            val tool = link.getQueryParameter("tool")?.let(::toolWithId) ?: return
            staged = (link.getQueryParameter("text") ?: intent.getStringExtra(Intent.EXTRA_TEXT))?.let { SharedContent(ShareKind.Text, text = it) }
            launch = tool
            return
        }
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return
        val app = context.applicationContext
        // would read private files
        val uris = (IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)))
            .filter { it.scheme == "content" && it.authority != "${app.packageName}.files" }
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        reading?.cancel()
        reading = scope.launch {
            // ipc and disk
            val content = withContext(Dispatchers.IO) { runCatching { content(app, uris, intent.type, text) }.getOrNull() }
            val direct = Shortcuts.toolId(intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID))?.let(::toolWithId)?.takeIf { content?.tools?.contains(it) == true }
            staged = content
            if (direct != null) launch = direct else offer = content?.tools.orEmpty()
        }
    }

    private fun content(context: Context, uris: List<Uri>, intentType: String?, text: String?): SharedContent? {
        val resolver = context.contentResolver
        val files = uris.mapNotNull { uri ->
            val mime = resolver.getType(uri) ?: intentType?.takeUnless { '*' in it }
            val extension = resolver.canonDisplayName(uri)?.substringAfterLast('.', "")?.lowercase()
            ShareKind.of(mime, extension)?.let { it to SharedFile(uri, mime) }
        }
        // priority: file, url, text
        val kind = files.firstOrNull { it.first != ShareKind.Text }?.first ?: files.firstOrNull()?.first
        if (kind == null) {
            val shared = text?.takeIf { it.isNotBlank() } ?: return null
            return if (ShareKind.isWebUrl(shared)) SharedContent(ShareKind.WebUrl, text = shared.trim()) else SharedContent(ShareKind.Text, text = shared)
        }
        val same = files.filter { it.first == kind }.map { it.second }
        if (kind != ShareKind.Text) return SharedContent(kind, if (kind.allowsSeveral) same else same.take(1))
        val bytes = resolver.openInputStream(same.first().uri)?.use { input ->
            // readNBytes is api 33
            val read = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (read.size() <= MAX_TEXT_BYTES) read.write(buffer, 0, input.read(buffer).takeIf { it >= 0 } ?: break)
            read.toByteArray()
        } ?: return null
        return if (bytes.size > MAX_TEXT_BYTES) null else SharedContent(kind, text = decodeTextFile(bytes))
    }

    fun dismiss() {
        offer = null
        staged = null
    }

    /** The shell opened the picked or launched tool; its page takes the share as it composes. */
    fun opened() {
        picked = staged
        staged = null
        offer = null
        launch = null
    }

    /** A page other than the picked tool's is opening. */
    fun drop() {
        picked = null
    }

    fun takeText(): String? = picked?.text?.also { picked = null }

    /** Files a drop zone of [accepting] MIME types takes; a file of unknown type goes to the first zone asking. */
    fun takeFiles(accepting: Array<String>, multiple: Boolean): List<Uri> {
        val files = picked?.files.orEmpty().filter { file ->
            file.mimeType == null || accepting.any { ClipDescription.compareMimeTypes(file.mimeType, it) }
        }
        if (files.isEmpty()) return emptyList()
        picked = null
        return (if (multiple) files else files.take(1)).map { it.uri }
    }
}

/** dt-ios ShareToolPicker: the tools that take the shared content, by category in offer order. */
@Composable
internal fun ShareOverlay(tools: List<Tool>, onOpen: (Tool) -> Unit, onDismiss: () -> Unit) {
    val c = LocalDelphiColours.current
    val back = remember { Animatable(0f) }
    LayoutBackDismiss(true, back) { onDismiss() }
    val sections = remember(tools) { tools.groupBy { it.category }.toList() }
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = 1f - 0.1f * back.value
                scaleY = 1f - 0.1f * back.value
                alpha = 1f - 0.5f * back.value
            }
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PaneBar("Open in delphitools", done = onDismiss, doneLabel = "Cancel")
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (tools.isEmpty()) {
                item {
                    // existing ios string
                    Text(
                        "No delphitools take this kind of content. Sorry!",
                        style = CanonText.body,
                        color = c.mutedForeground,
                        modifier = Modifier.padding(CanonMetrics.panelPadding),
                    )
                }
            }
            sections.forEach { (category, members) ->
                item(key = category) { CanonHeading(category.title) }
                items(members, key = { it.id }) { tool -> SearchRow(tool) { onOpen(tool) } }
            }
        }
    }
}
