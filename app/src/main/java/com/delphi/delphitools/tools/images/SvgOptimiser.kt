package com.delphi.delphitools.tools.images

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.text.format.Formatter
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonCanvasBoxing
import com.delphi.delphitools.canon.CanonCanvasSize
import com.delphi.delphitools.canon.CanonCodeBlock
import com.delphi.delphitools.canon.CanonCompressionScale
import com.delphi.delphitools.canon.CanonDropZone
import com.delphi.delphitools.canon.CanonEmptyRow
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonRows
import com.delphi.delphitools.canon.CanonSection
import com.delphi.delphitools.canon.CanonSplit
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTextInput
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.canon.LocalCanonCanvas
import com.delphi.delphitools.canon.canonChecks
import com.delphi.delphitools.canon.canonDisplayName
import com.delphi.delphitools.canon.decodeTextFile
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class SvgFile(val name: String, val text: String)

/** dt-ios SVGOptimiserViewController's state: a file or typed markup, optimised by svgo on every change. */
class SvgOptimiserModel(application: Application) : AndroidViewModel(application) {
    var typed by mutableStateOf("")
    private var file by mutableStateOf<SvgFile?>(null)
    val fileName get() = file?.name
    var output by mutableStateOf<String?>(null)
        private set
    var outline by mutableStateOf("")
        private set
    var failure by mutableStateOf<String?>(null)
        private set
    var working by mutableStateOf(false)
        private set

    private var engine: Deferred<SvgOptimiserEngine?>? = null
    private var started: SvgOptimiserEngine? = null
    // drops stale runs
    private var run = 0

    val currentInput get() = file?.text ?: typed
    val inputBytes get() = currentInput.toByteArray().size.toLong()

    fun read(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val (name, contents) = withContext(Dispatchers.IO) {
                runCatching { app.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()?.let(::decodeTextFile)?.let { app.contentResolver.canonDisplayName(uri) to it }
            } ?: return@launch
            file = SvgFile(name ?: "image.svg", contents)
            optimise(contents)
        }
    }

    fun clear() {
        file = null
        typed = ""
        optimise("")
    }

    fun optimise(svg: String) {
        val current = ++run
        val trimmed = svg.trim()
        if (!SvgMarkup.looksLikeSvg(trimmed)) {
            output = null
            failure = null
            working = false
            return
        }
        working = true
        val app = getApplication<Application>()
        val engine = engine ?: viewModelScope.async { SvgOptimiserEngine.start(app).also { started = it } }.also { engine = it }
        viewModelScope.launch {
            val result = engine.await()?.optimise(trimmed)
            if (current != run) return@launch
            val optimised = result?.getOrNull()
            outline = optimised?.let { withContext(Dispatchers.Default) { SvgMarkup.outline(it) } } ?: ""
            if (current != run) return@launch
            output = optimised
            failure = when {
                result == null -> "The optimiser could not start"
                else -> result.exceptionOrNull()?.message
            }
            working = false
        }
    }

    fun export(context: Context) {
        val output = output ?: return
        val name = file?.name?.replace(".svg", "-optimised.svg") ?: "optimised.svg"
        viewModelScope.launch { FileExport.share(context, output.toByteArray(), name, "image/svg+xml") }
    }

    override fun onCleared() {
        started?.close()
    }
}

@Composable
fun SvgOptimiser(model: SvgOptimiserModel = viewModel()) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    val output = model.output
    val fileName = model.fileName

    val input = CanonSection.of {
        CanonRows {
            CanonHeading("Input SVG")
            if (fileName == null) {
                CanonDropZone(
                    "Drop an SVG",
                    icon = Icons.Outlined.Description,
                    mimeTypes = arrayOf("image/svg+xml"),
                    photos = null,
                    paste = false,
                    onFiles = { uris -> uris.firstOrNull()?.let(model::read) },
                )
                CanonTextInput(model.typed, { model.typed = it }, model::optimise)
            } else {
                CanonRow(fileName, detail = Formatter.formatShortFileSize(context, model.inputBytes), flush = true) {
                    CanonButton("Clear", icon = Icons.Outlined.Delete, onClick = model::clear)
                }
            }
        }
    }

    val main = buildList {
        if (output != null) {
            val ratio = SvgMarkup.size(output)?.let { (it.first / it.second).toFloat() } ?: 1f
            add(
                CanonSection.canvas(
                    CanonCanvasSize.Aspect(ratio.coerceIn(0.75f, 2f)),
                    background = c.muted,
                    maxHeight = 420.dp,
                    boxing = CanonCanvasBoxing.UnlessWide,
                ) { SvgPreview(output, ratio) },
            )
        }
        val failure = model.failure
        if (output != null || failure != null) {
            add(
                CanonSection.of {
                    CanonRows {
                        CanonHeading("Optimised SVG")
                        if (failure != null) CanonStatusRow(failure, CanonTone.Warn)
                        if (output != null) {
                            CanonCodeBlock(model.outline, maxHeight = 320.dp, copyText = output)
                            CanonButton("Export", kind = CanonButtonKind.Primary, icon = Icons.Outlined.Share, oversized = true) {
                                model.export(context)
                            }
                        }
                    }
                },
            )
        } else {
            add(CanonSection.of { CanonRows { CanonEmptyRow("Optimised SVG") } })
        }
    }

    val scale = CanonSection.canvas(CanonCanvasSize.Fixed(176.dp), background = c.card) {
        CanonCompressionScale(
            if (SvgMarkup.looksLikeSvg(model.currentInput)) model.inputBytes else 0,
            output?.toByteArray()?.size?.toLong() ?: 0,
            "Optimised",
            Modifier.padding(top = LocalCanonCanvas.current.top),
            isWorking = model.working,
        )
    }

    CanonSplit(sidebar = listOf(input), main = main, banner = scale)
}

/**
 * dt-ios SVGPreviewView: the SVG rendered as a file renders, an `<img>` in a WebView with scripts off,
 * fitted to its own aspect on the light transparency checks.
 */
@Composable
private fun SvgPreview(svg: String, ratio: Float) {
    Box(Modifier.fillMaxSize().padding(16.dp).semantics { contentDescription = "Preview" }, contentAlignment = Alignment.Center) {
        // ponytail: no fade-in on load; add a page-finished alpha animation if the swap flickers
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    settings.javaScriptEnabled = false
                    settings.blockNetworkLoads = true
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    isFocusable = false
                }
            },
            update = { web ->
                if (web.tag != svg) {
                    web.tag = svg
                    web.loadDataWithBaseURL(null, SvgMarkup.imagePage(svg, "transparent"), "text/html", "utf-8", null)
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.aspectRatio(ratio).canonChecks(paper = true),
        )
    }
}
