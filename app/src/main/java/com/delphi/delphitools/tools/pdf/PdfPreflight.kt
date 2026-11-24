package com.delphi.delphitools.tools.pdf

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonMetrics
import com.delphi.delphitools.canon.CanonPageStrip
import com.delphi.delphitools.canon.CanonPageStripHeight
import com.delphi.delphitools.canon.CanonPageStripPage
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSegmented
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTile
import com.delphi.delphitools.canon.CanonTiles
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfPreflightModel(application: Application) : AndroidViewModel(application) {
    val pdf = PdfSourceLoader(viewModelScope, application)
    var profile by mutableStateOf(0)
        private set
    var document by mutableStateOf<PdfPreflightEngine.Document?>(null)
        private set
    var report by mutableStateOf<PdfPreflightEngine.Report?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Null: all pages. */
    var page by mutableStateOf<Int?>(null)
    private var task: Job? = null

    init {
        pdf.onLoad = { source -> parse(source) }
    }

    fun load(uri: Uri) {
        clear()
        loading = true
        pdf.load(uri)
    }

    fun clear() {
        task?.cancel()
        pdf.clear()
        page = null
        document = null
        report = null
        loading = false
        error = null
    }

    fun profile(index: Int) {
        profile = index
        val document = document ?: return
        task?.cancel()
        loading = true
        task = viewModelScope.launch {
            report = withContext(Dispatchers.Default) { PdfPreflightEngine.run(PdfPreflightEngine.profiles[index], document) }
            loading = false
        }
    }

    private fun parse(source: PdfSource) {
        task?.cancel()
        loading = true
        error = null
        task = viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    val file = PdfFile(source.file.readBytes())
                    val read = PdfPreflightEngine.read(file)
                    read to PdfPreflightEngine.run(PdfPreflightEngine.profiles[profile], read)
                }
            }
            if (pdf.source !== source) return@launch
            parsed.onSuccess { (read, run) ->
                document = read
                report = run
            }.onFailure {
                error = if (it.message == "encrypted") "This PDF is encrypted and cannot be analysed." else "Could not open this PDF."
            }
            loading = false
        }
    }

    override fun onCleared() = clear()
}

private fun label(category: PdfPreflightEngine.Category) = when (category) {
    PdfPreflightEngine.Category.File -> "File"
    PdfPreflightEngine.Category.Pdf -> "PDF"
    PdfPreflightEngine.Category.Pages -> "Pages"
    PdfPreflightEngine.Category.Marks -> "Marks & Bleed"
    PdfPreflightEngine.Category.Colour -> "Colour"
    PdfPreflightEngine.Category.Fonts -> "Fonts"
    PdfPreflightEngine.Category.Images -> "Images"
    PdfPreflightEngine.Category.Lines -> "Lines & Strokes"
}

@Composable
fun PdfPreflight(model: PdfPreflightModel = viewModel()) {
    val c = LocalDelphiColours.current
    val loader = model.pdf
    val loaded = loader.opened
    val page = model.page
    val document = model.document
    val report = if (model.loading) null else model.report
    val all = report?.results.orEmpty()

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Source")
            if (!loaded) {
                CanonDropZone(
                    "Drop PDF here",
                    icon = Icons.Outlined.Description,
                    mimeTypes = arrayOf(PdfDocuments.MIME),
                    photos = null,
                    paste = false,
                    onFiles = { uris -> uris.firstOrNull()?.let(model::load) },
                )
            } else {
                PdfFileRow(loader.source, loader.name, loader.failed, document?.let { "PDF ${it.pdfVersion}" }, model::clear)
            }
        }
    }

    val profile = CanonSection.of {
        CanonRows {
            CanonHeading("Profile")
            CanonSegmented(PdfPreflightEngine.profiles.map { it.name }, model.profile, columns = 2, onSelect = model::profile)
        }
    }

    val filter = CanonSection.of {
        CanonRows {
            CanonHeading("Page")
            CanonRow("Page ${(page ?: 0) + 1}", flush = true) {
                CanonButton("All pages", icon = Icons.Outlined.Close) { model.page = null }
            }
        }
    }

    val summary = CanonSection.of {
        CanonRows {
            CanonHeading("Result")
            if (model.loading && !loader.failed) {
                CanonRow("Analysing…") { CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground) }
            }
            model.error?.let { CanonStatusRow(it, CanonTone.Warn) }
            if (report != null) {
                CanonTiles(
                    listOf(
                        CanonTile("Result", if (report.passed) "✓ Pass" else "✕ Fail"),
                        CanonTile("Errors", "${all.count { it.isError }}"),
                        CanonTile("Warnings", "${all.count { it.isIssue && !it.isError }}"),
                        CanonTile("Passed", "${all.count { it.status == PdfPreflightEngine.Status.Pass }}"),
                    ),
                )
            }
        }
    }

    val visible = all.filter { result -> page == null || result.pages?.contains(page) != false }
    val clean = ArrayList<CanonTile>()
    val groups = PdfPreflightEngine.Category.entries.mapNotNull { category ->
        val group = visible.filter { it.category == category }
        if (group.isEmpty()) return@mapNotNull null
        val issues = group.filter { it.isIssue }
        if (issues.isEmpty()) {
            clean.add(CanonTile(label(category), "✓ ${group.count { it.status == PdfPreflightEngine.Status.Pass }}"))
            return@mapNotNull null
        }
        category to issues.sortedWith(compareBy({ it.severity }, { it.message }))
    }
    val results = CanonSection.of {
        Column(verticalArrangement = Arrangement.spacedBy(CanonMetrics.panelPadding)) {
            for ((category, issues) in groups) {
                CanonRows {
                    CanonHeading(label(category))
                    issues.forEach { ResultRow(it) }
                }
            }
            if (clean.isNotEmpty()) {
                CanonRows {
                    CanonHeading("Passed")
                    CanonTiles(clean)
                }
            }
        }
    }

    val inventory = CanonSection.of {
        CanonRows {
            CanonHeading("Font Inventory")
            for (font in document?.fonts.orEmpty()) {
                val state = listOfNotNull(font.type, if (font.embedded) "Embedded" else "Not embedded", if (font.subset) "Subset" else null)
                CanonRow(font.name, detail = state.joinToString(" · "), detailColour = if (font.embedded) null else c.destructive)
            }
        }
    }

    val properties = CanonSection.of {
        val info = document ?: return@of
        val yes = { on: Boolean -> if (on) "Yes" else "No" }
        val items = listOfNotNull(
            CanonTile("PDF version", info.pdfVersion),
            CanonTile("Tagged", yes(info.tagged)),
            CanonTile("Transparency", yes(info.transparency)),
            CanonTile("Layers", yes(info.layers)),
            CanonTile("Linearised", yes(info.linearised)),
            info.outputIntent?.let { CanonTile("Output Intent", it) },
            info.producer?.let { CanonTile("Producer", it) },
            info.creator?.let { CanonTile("Creator", it) },
        )
        CanonRows {
            CanonHeading("Document Properties")
            CanonTiles(items)
            Text(
                "Powered by TaxiwayCore",
                Modifier.fillMaxWidth().background(c.card).padding(horizontal = 12.dp, vertical = 10.dp),
                style = CanonText.caption,
                color = c.mutedForeground,
                textAlign = TextAlign.Center,
            )
        }
    }

    val issues = PdfPreflightEngine.pageIssues(all)
    val strip = CanonSection.canvas(CanonCanvasSize.Fixed(CanonPageStripHeight), background = c.muted) {
        CanonPageStrip(
            document?.pages.orEmpty().map { info ->
                val media = info.mediaBox
                val size = PdfCropEngine.displaySize(PdfRect.of(media), info.rotation)
                val trim = info.effectiveTrimBox.takeIf { info.rotation % 360 == 0 }
                val (errors, count) = issues[info.index] ?: (0 to 0)
                val caption = listOfNotNull(if (errors > 0) "✕ $errors" else null, if (count > errors) "! ${count - errors}" else null)
                CanonPageStripPage(
                    size.compose(),
                    loader.thumbnails[info.index],
                    caption.joinToString("  ").ifEmpty { null },
                    area = trim?.let {
                        Rect((it.x0 - media.x0).toFloat(), (it.y0 - media.y0).toFloat(), (it.x1 - media.x0).toFloat(), (it.y1 - media.y0).toFloat())
                    },
                )
            },
            selected = page,
            onSelect = { index -> model.page = if (index == page) null else index },
        )
    }

    CanonSplit(
        sidebar = listOfNotNull(input, profile.takeIf { loaded }, filter.takeIf { page != null }),
        main = listOfNotNull(
            summary.takeIf { loaded },
            results.takeIf { report != null },
            inventory.takeIf { document?.fonts?.isNotEmpty() == true },
            properties.takeIf { document != null },
        ),
        banner = strip,
    )
}

// dt-ios PreflightResultRow
@Composable
private fun ResultRow(result: PdfPreflightEngine.Result) {
    val c = LocalDelphiColours.current
    val error = result.isError
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.card)
            .padding(horizontal = CanonMetrics.panelPadding, vertical = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(result.message, result.detail).joinToString(", ") },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (error) Icons.Filled.Dangerous else Icons.Filled.Warning,
            null,
            tint = if (error) c.destructive else c.foreground,
            modifier = Modifier.padding(top = 1.dp).size(16.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(result.message, style = CanonText.subheadline, color = c.foreground)
            result.detail?.let { Text(it, style = CanonText.caption, color = c.mutedForeground) }
        }
    }
}
