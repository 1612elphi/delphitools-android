package com.delphi.delphitools.tools.pdf

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonPageStrip
import com.delphi.delphitools.canon.CanonPageStripHeight
import com.delphi.delphitools.canon.CanonPageStripPage
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTextCell
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.tools.TYPING_DELAY_MS
import com.delphi.delphitools.tools.images.ImageFiles
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** A PDF added to the organiser: its facts, its parsed objects, and thumbnails as they stream in. */
class OrganiserSource(val info: PdfSource, val pdf: PdfFile) {
    val thumbnails = mutableStateMapOf<Int, ImageBitmap>()
}

class OrganiserEntry(val source: OrganiserSource, val page: Int, val rotation: Int = 0) {
    val id: String = UUID.randomUUID().toString()
}

class PdfOrganiserModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<Application>()
    val sources = mutableStateListOf<OrganiserSource>()
    val entries = mutableStateListOf<OrganiserEntry>()
    var reading by mutableIntStateOf(0)
        private set
    var readFailure by mutableStateOf<String?>(null)
        private set
    val job = PdfJob(viewModelScope, application)
    var splitting by mutableStateOf(false)
        private set
    var splitFailed by mutableStateOf(false)
        private set
    var everyPage by mutableStateOf(false)
    var rangeText by mutableStateOf("")
        private set
    var rangeSpec by mutableStateOf("")
        private set
    private var typing: Job? = null
    private val streams = HashMap<OrganiserSource, Job>()

    private val baseName get() = sources.firstOrNull()?.info?.stem ?: "merged"

    fun typeRange(text: String) {
        rangeText = text
        typing?.cancel()
        typing = viewModelScope.launch {
            delay(TYPING_DELAY_MS)
            rangeSpec = text
        }
    }

    fun add(uris: List<Uri>, at: Int) {
        if (uris.isEmpty()) return
        readFailure = null
        reading += uris.size
        viewModelScope.launch {
            var insertAt = minOf(at, entries.size)
            // keeps the picked order
            for (uri in uris) {
                val copied = PdfDocuments.copy(app, uri)
                val loaded = copied?.let { (file, name) ->
                    withContext(Dispatchers.IO) {
                        val pdf = runCatching { PdfFile(file.readBytes()) }.getOrNull()
                        if (pdf == null) {
                            file.delete()
                            return@withContext null
                        }
                        val sizes = pdf.pages.map { PdfCropEngine.displaySize(PdfRect.of(pdf.cropBox(it)), pdf.rotation(it)) }
                        OrganiserSource(PdfSource(file, name, file.length(), sizes), pdf)
                    }
                }
                reading -= 1
                if (loaded == null) {
                    readFailure = "Could not read ${copied?.second ?: uri.lastPathSegment.orEmpty()}"
                    continue
                }
                sources.add(loaded)
                val added = (0 until loaded.info.pageCount).map { OrganiserEntry(loaded, it) }
                entries.addAll(minOf(insertAt, entries.size), added)
                insertAt += added.size
                job.reset()
                streams[loaded] = launch { PdfDocuments.streamThumbnails(loaded.info.file) { loaded.thumbnails.putAll(it) } }
            }
        }
    }

    fun remove(source: OrganiserSource) {
        streams.remove(source)?.cancel()
        sources.remove(source)
        entries.removeAll { it.source === source }
        source.info.file.delete()
        job.reset()
    }

    fun move(from: Int, to: Int) {
        if (from !in entries.indices || to !in entries.indices) return
        entries.add(to, entries.removeAt(from))
        job.reset()
    }

    private fun act(id: String, body: (Int) -> Unit) {
        val live = entries.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        body(live)
        job.reset()
    }

    fun rotate(id: String) = act(id) { live ->
        val entry = entries[live]
        entries[live] = OrganiserEntry(entry.source, entry.page, PdfPagesEngine.normaliseRotation(entry.rotation + 90))
    }

    fun duplicate(id: String) = act(id) { live ->
        val entry = entries[live]
        entries.add(live + 1, OrganiserEntry(entry.source, entry.page, entry.rotation))
    }

    fun moveToEnd(id: String, toStart: Boolean) = act(id) { live ->
        val entry = entries.removeAt(live)
        entries.add(if (toStart) 0 else entries.size, entry)
    }

    fun delete(id: String) = act(id) { entries.removeAt(it) }

    private val refs get() = entries.map { PdfOrganiserEngine.PageRef(it.source.pdf, it.page, it.rotation) }

    fun exportMerged() {
        if (entries.isEmpty() || splitting) return
        val refs = refs
        job.start("$baseName-merged.pdf") { output, progress ->
            val context = coroutineContext
            output.outputStream().buffered().use { out ->
                PdfOrganiserEngine.build(refs, out) { index ->
                    context.ensureActive()
                    progress(index.toFloat() / refs.size)
                }
            }
            progress(1f)
        }
    }

    /** Groups of entry indices, or why the ranges do not parse; null for an empty field. */
    val parsed: Result<List<List<Int>>>?
        get() {
            if (everyPage) return Result.success(PdfPagesEngine.everyPageAlone(entries.size))
            if (rangeSpec.isBlank()) return null
            return runCatching { PdfPagesEngine.parsePageRanges(rangeSpec, entries.size) }
        }

    fun exportSplit(context: Context) {
        val groups = parsed?.getOrNull()?.takeIf { it.isNotEmpty() } ?: return
        if (splitting) return
        val base = baseName
        val every = everyPage
        val refs = refs
        val named = groups.mapIndexed { index, group ->
            (if (every) "$base-page-${group[0] + 1}.pdf" else "$base-part-${index + 1}.pdf") to group.map { refs[it] }
        }
        splitting = true
        splitFailed = false
        viewModelScope.launch {
            // ponytail: every part held in memory until shared; stream them to the export folder if large splits run out
            val files = withContext(Dispatchers.Default) {
                runCatching {
                    named.map { (name, part) -> name to ByteArrayOutputStream().also { PdfOrganiserEngine.build(part, it) }.toByteArray() }
                }.getOrNull()
            }
            splitting = false
            splitFailed = files == null
            if (files != null) ImageFiles.shareAll(context, files, PdfDocuments.MIME)
        }
    }

    override fun onCleared() {
        sources.forEach { it.info.file.delete() }
        job.reset()
    }
}

private fun stripPage(entry: OrganiserEntry, depth: Int = 0, name: String?): CanonPageStripPage? {
    val size = entry.source.info.sizes.getOrNull(entry.page) ?: return null
    val turned = PdfCropEngine.displaySize(PdfRect(0.0, 0.0, size.width, size.height), entry.rotation)
    return CanonPageStripPage(turned.compose(), entry.source.thumbnails[entry.page], name, depth = depth, turn = entry.rotation)
}

@Composable
fun PdfOrganiser(model: PdfOrganiserModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val busy = model.job.busy || model.splitting

    val files = CanonSection.of {
        CanonRows {
            CanonHeading("Files")
            model.sources.forEach { source -> PdfFileRow(source.info, source.info.name, false) { model.remove(source) } }
            if (model.reading > 0) CanonRow("Processing…") { CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground) }
            model.readFailure?.let { CanonStatusRow(it, CanonTone.Warn) }
            CanonDropZone(
                "Drop PDFs here",
                icon = Icons.Outlined.FileCopy,
                mimeTypes = arrayOf(PdfDocuments.MIME),
                photos = null,
                multiple = true,
                paste = false,
                onFiles = { if (!busy) model.add(it, model.entries.size) },
            )
        }
    }

    val merge = CanonSection.of {
        CanonRows {
            CanonHeading("Merge")
            PdfJobPanel(model.job, "Export Merged", Icons.Outlined.Share, "Merge failed", enabled = model.entries.isNotEmpty() && !model.splitting, onStart = model::exportMerged)
        }
    }

    val parsed = model.parsed
    val groups = parsed?.getOrNull().orEmpty()
    val rangeError = (parsed?.exceptionOrNull() as? PdfPagesEngine.RangeException)?.error

    val split = CanonSection.of {
        CanonRows {
            CanonHeading("Split")
            CanonSegmented(listOf("By ranges", "Every page"), if (model.everyPage) 1 else 0) { model.everyPage = it == 1 }
            if (!model.everyPage) {
                CanonRow("Ranges", flush = true) {
                    CanonTextCell(
                        model.rangeText,
                        model::typeRange,
                        placeholder = "1-3, 7-9",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    )
                }
                if (rangeError != null) CanonStatusRow(rangeError.message(model.entries.size), CanonTone.Warn)
            }
        }
    }

    val parts = CanonSection.canvas(CanonCanvasSize.Fixed(CanonPageStripHeight), background = c.muted, boxing = CanonCanvasBoxing.UnlessWide) {
        CanonPageStrip(
            groups.mapNotNull { group ->
                val first = group.first()
                val last = group.last()
                model.entries.getOrNull(first)?.let { stripPage(it, group.size - 1, if (first == last) "${first + 1}" else "${first + 1}–${last + 1}") }
            },
            empty = "Parts",
        )
    }

    val splitAction = CanonSection.of {
        CanonRows {
            CanonButton(
                if (groups.size == 1) "Export part" else "Export ${groups.size} parts",
                Modifier.fillMaxWidth(),
                icon = Icons.AutoMirrored.Outlined.CallSplit,
                enabled = groups.isNotEmpty() && !busy,
            ) { model.exportSplit(context) }
            if (model.splitting) CanonRow("Processing…") { CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground) }
            if (model.splitFailed) CanonStatusRow("Split failed", CanonTone.Warn)
        }
    }

    val strip = CanonSection.canvas(CanonCanvasSize.Fixed(CanonPageStripHeight), background = c.muted) {
        CanonPageStrip(
            model.entries.mapNotNull { stripPage(it, name = "${it.source.info.stem} p. ${it.page + 1}") },
            reorders = !busy,
            onMove = model::move,
            dropTypes = listOf(PdfDocuments.MIME),
            onDrop = { index, clip -> model.add(clip.uris(), index) },
            menu = { index ->
                val id = model.entries.getOrNull(index)?.id
                if (busy || id == null) {
                    emptyList()
                } else {
                    listOf(
                        "Rotate" to { model.rotate(id) },
                        "Duplicate" to { model.duplicate(id) },
                        "Move to start" to { model.moveToEnd(id, true) },
                        "Move to end" to { model.moveToEnd(id, false) },
                        "Remove" to { model.delete(id) },
                    )
                }
            },
        )
    }

    CanonSplit(sidebar = listOf(files, merge), main = listOf(split, parts, splitAction), banner = strip)
}
