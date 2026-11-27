package com.delphi.delphitools.tools.pdf

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.delphi.delphitools.canon.CanonBar
import com.delphi.delphitools.canon.CanonButton
import com.delphi.delphitools.canon.CanonButtonKind
import com.delphi.delphitools.canon.CanonGrid
import com.delphi.delphitools.canon.CanonHeading
import com.delphi.delphitools.canon.CanonLoader
import com.delphi.delphitools.canon.CanonPassAlongWhenDone
import com.delphi.delphitools.canon.CanonRow
import com.delphi.delphitools.canon.CanonStatusRow
import com.delphi.delphitools.canon.CanonTone
import com.delphi.delphitools.tools.FileExport
import com.delphi.delphitools.ui.theme.CanonText
import com.delphi.delphitools.ui.theme.LocalDelphiColours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** dt-ios AVEditJob: one file made in the background, with progress, cancel, and the finished file kept for sharing. */
class PdfJob(private val scope: CoroutineScope, private val context: Context) {
    var busy by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var failed by mutableStateOf(false)
        private set
    var output by mutableStateOf<File?>(null)
        private set
    private var task: Job? = null
    private var run = 0

    private fun failed(name: String, cause: Throwable): Boolean {
        android.util.Log.w("PdfJob", "export failed: $name", cause)
        return false
    }

    /** [work] writes the file it is given and reports progress from 0 to 1 from any thread. */
    fun start(name: String, work: suspend (File, (Float) -> Unit) -> Unit) {
        reset()
        busy = true
        val current = run
        val file = File(PdfDocuments.outputFolder(context), FileExport.safeFilename(name))
        task = scope.launch {
            val done = try {
                withContext(Dispatchers.Default) { work(file) { share -> scope.launch { if (run == current) progress = share } } }
                true
            } catch (e: CancellationException) {
                PdfDocuments.discard(file)
                throw e
            } catch (e: Exception) {
                failed(name, e)
            } catch (e: OutOfMemoryError) {
                failed(name, e)
            } catch (e: StackOverflowError) {
                failed(name, e)
            }
            busy = false
            if (done) output = file else PdfDocuments.discard(file)
            failed = !done
        }
    }

    fun reset() {
        run++
        task?.cancel()
        task = null
        PdfDocuments.discard(output)
        output = null
        busy = false
        failed = false
        progress = 0f
    }
}

/** dt-ios AVEditJobPanel: the export button, progress with Cancel, then the finished file with Preview and Share. */
@Composable
fun PdfJobPanel(job: PdfJob, title: String, icon: ImageVector, failure: String, enabled: Boolean, onStart: () -> Unit) {
    val c = LocalDelphiColours.current
    val context = LocalContext.current
    CanonPassAlongWhenDone(enabled, job.busy, job.output, job.failed, onStart) { FileExport.send(context, listOf(it), PdfDocuments.MIME) }
    if (job.busy) {
        CanonRow("Processing…") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CanonLoader(cell = 5.dp, gap = 1.dp, colour = c.mutedForeground)
                Text("${(job.progress * 100).roundToInt()}%", style = CanonText.footnoteSemibold, color = c.mutedForeground)
            }
        }
        CanonBar(job.progress, Modifier.fillMaxWidth().padding(vertical = 2.dp))
        CanonButton("Cancel", Modifier.fillMaxWidth(), icon = Icons.Outlined.Close, onClick = job::reset)
        return
    }
    CanonButton(title, kind = CanonButtonKind.Primary, enabled = enabled, icon = icon, oversized = true, followsWorkflow = false, onClick = onStart)
    if (job.failed) CanonStatusRow(failure, CanonTone.Warn)
    val output = job.output ?: return
    CanonHeading("Result")
    CanonRow(output.name, detail = PdfDocuments.byteLabel(context, output.length()))
    CanonGrid {
        val cell = Modifier.weight(1f).fillMaxHeight()
        CanonButton("Preview", cell, icon = Icons.Outlined.Visibility) { preview(context, output) }
        CanonButton("Share", cell, kind = CanonButtonKind.Primary, icon = Icons.Outlined.Share) {
            FileExport.send(context, listOf(output), PdfDocuments.MIME)
        }
    }
}

private fun preview(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, PdfDocuments.MIME).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(view)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(FileExport.chooser(context, file, PdfDocuments.MIME))
    }
}
