package com.delphi.delphitools.shell

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.delphi.delphitools.canon.CanonHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.UUID

// pill stamp plays first
private const val LANDING_MS = 450L

/** A file a step exported, with the MIME type it was shared as. */
class WorkflowCapture(val file: File, val mimeType: String)

/**
 * dt-ios WorkflowRun: the workflow in progress. Exports on the current step's page are captured here instead
 * of shared, and the next step's drop zones take them through [pending].
 */
@Stable
object WorkflowRun {
    var workflow by mutableStateOf<Workflow?>(null)
        private set
    var step by mutableIntStateOf(0)
        private set
    private val captures = mutableStateMapOf<Int, List<WorkflowCapture>>()

    /** hex colours a colour tool shows or has picked */
    var colours: List<String> = emptyList()
        set(value) {
            field = value
            if (hasColour == value.isEmpty()) hasColour = value.isNotEmpty()
        }
    var hasColour by mutableStateOf(false)
        private set

    /** the page on screen is the current step's tool */
    var isOnStep by mutableStateOf(false)
        private set

    /** a capture is being written or stamped on the pill */
    var isLanding by mutableStateOf(false)
        private set

    /** bumped on each capture, for the pill's stamp */
    var captureCount by mutableIntStateOf(0)
        private set

    /** a start or exit waiting on the discard dialog */
    var pendingDiscard by mutableStateOf<(() -> Unit)?>(null)
        private set

    // save captures, then shares
    private var finishesOnLanding = false

    private var handedColours: List<String>? = null
    private val delivered = HashSet<File>()
    private var folder: File? = null
    private val scope = MainScope()

    private var root: File? = null
    private var open: (Tool) -> Unit = {}
    private var share: (List<WorkflowCapture>) -> Unit = {}

    /** The shell: [root] holds the captures, [open] shows a step's tool on a new page, [share] sends the last step's files. */
    fun attach(root: File, open: (Tool) -> Unit, share: (List<WorkflowCapture>) -> Unit) {
        this.root = root
        this.open = open
        this.share = share
    }

    /** The shell's composition left: its lambdas hold the activity. */
    fun detach() {
        open = {}
        share = {}
    }

    val isActive get() = workflow != null
    val tools get() = workflow?.tools.orEmpty()
    val currentTool get() = tools.getOrNull(step)
    val nextTool get() = tools.getOrNull(step + 1)
    val isLast get() = step >= tools.size - 1
    val captured get() = captures[step].orEmpty()
    val isFinished get() = isLast && captured.isNotEmpty()

    val canAdvance: Boolean
        get() {
            val next = nextTool ?: return false
            if (isLanding) return false
            return WorkflowCatalog.ports[next.id]?.takesColour == true && hasColour || captured.isNotEmpty()
        }

    /** Exports are captured only where the step hands on a file: a colour step moves on with Next. */
    val capturesFiles get() = isOnStep && !WorkflowCatalog.ports[currentTool?.id]?.produces.isNullOrEmpty()

    fun start(workflow: Workflow) = confirmDiscard {
        reset()
        this.workflow = workflow
        folder = root?.let { File(it, UUID.randomUUID().toString()) }
        go(0)
    }

    fun goTo(index: Int) {
        if (index in tools.indices && (index != step || !isOnStep)) go(index)
    }

    fun advance() {
        if (canAdvance) go(step + 1)
    }

    /** dt-ios ToolViewController.passAlong: the bar's Next and Save. false when the page has nothing to pass. */
    fun next(): Boolean {
        if (isFinished) {
            finish()
            return true
        }
        // colour legs skip capture
        if (!capturesFiles) {
            advance()
            return true
        }
        val passer = CanonHost.passers.firstOrNull()
        if (passer != null) {
            finishesOnLanding = isLast
            passer()
        } else if (!isLast && captured.isNotEmpty()) {
            advance()
        } else {
            return false
        }
        return true
    }

    private fun go(index: Int) {
        val tool = tools.getOrNull(index) ?: return
        step = index
        delivered.clear()
        val ports = WorkflowCatalog.ports[tool.id]
        handedColours = colours.takeIf { ports?.takesColour == true && it.isNotEmpty() }?.let { if (ports?.takesPalette == true) it else it.take(1) }
        open(tool)
    }

    /** The colours an earlier step handed to the tool now opening, once. */
    fun takeColours(): List<String>? = handedColours.also { handedColours = null }

    /** The shell reports every page it shows. Opening a step's tool from elsewhere moves the run to that step. */
    fun visit(tool: Tool?) {
        if (!isActive) return
        val index = tools.indexOfFirst { it.id == tool?.id }
        if (tool != null && tool.id != currentTool?.id && index >= 0) {
            step = index
            delivered.clear()
        }
        isOnStep = tool != null && tool.id == currentTool?.id
    }

    fun exit() {
        if (isActive) confirmDiscard(::reset)
    }

    /** Ends the run. The open page may still read the run's files; the launch sweep deletes them. */
    fun reset() {
        folder = null
        workflow = null
        step = 0
        captures.clear()
        colours = emptyList()
        handedColours = null
        finishesOnLanding = false
        isOnStep = false
        isLanding = false
        delivered.clear()
    }

    private fun confirmDiscard(go: () -> Unit) {
        if (captures.values.none { it.isNotEmpty() }) go() else pendingDiscard = go
    }

    fun answerDiscard(discards: Boolean) {
        val go = pendingDiscard
        pendingDiscard = null
        if (discards) go?.invoke()
    }

    /** Takes exported files on the current step's page. false when the caller should share them as usual. */
    fun capture(files: List<File>, mimeType: String): Boolean {
        if (files.isEmpty()) return false
        // press during a landing
        if (isLanding) return capturesFiles
        val target = stepFolder() ?: return false
        val run = workflow
        val at = step
        isLanding = true
        scope.launch {
            val copies = withContext(Dispatchers.IO) {
                target.mkdirs()
                files.mapNotNull { file ->
                    val copy = File(target, file.name)
                    // link cost ignores size
                    runCatching { Files.createLink(copy.toPath(), file.toPath()).toFile() }
                        .recoverCatching { file.copyTo(copy, overwrite = true) }
                        .getOrNull()
                }
            }
            if (workflow !== run) return@launch
            if (copies.isEmpty()) isLanding = false else land(at, copies.map { WorkflowCapture(it, mimeType) })
        }
        return true
    }

    /** Takes an export that is still in memory. false when the caller should share it as usual. */
    suspend fun capture(data: ByteArray, filename: String, mimeType: String): Boolean {
        if (isLanding) return capturesFiles
        val target = stepFolder() ?: return false
        val run = workflow
        val at = step
        isLanding = true
        val file = try {
            withContext(Dispatchers.IO) {
                runCatching {
                    target.mkdirs()
                    File(target, filename).apply { writeBytes(data) }
                }.getOrNull()
            }
        } catch (cancelled: CancellationException) {
            // page left mid-write
            isLanding = false
            throw cancelled
        }
        if (workflow !== run) return true
        if (file == null) isLanding = false else land(at, listOf(WorkflowCapture(file, mimeType)))
        return true
    }

    private fun stepFolder(): File? = folder?.takeIf { capturesFiles }?.let { File(it, "$step-${UUID.randomUUID()}") }

    private fun land(at: Int, files: List<WorkflowCapture>) {
        val run = workflow
        // made from replaced export
        val old = captures.keys.filter { it >= at }.flatMap { captures.remove(it).orEmpty() }
        captures[at] = files
        captureCount++
        isLanding = true
        scope.launch {
            withContext(Dispatchers.IO) { old.forEach { it.file.parentFile?.deleteRecursively() } }
            delay(LANDING_MS)
            if (workflow !== run) return@launch
            isLanding = false
            if (at != step || !isOnStep) return@launch
            if (!isLast) {
                advance()
            } else if (finishesOnLanding) {
                finishesOnLanding = false
                finish()
            }
        }
    }

    /** Earlier steps' files for a drop zone of [accepting] MIME types, newest step first, each handed once per visit. */
    fun pending(accepting: Array<String>, multiple: Boolean): List<File> {
        if (!isOnStep) return emptyList()
        for (index in step - 1 downTo 0) {
            val matching = captures[index].orEmpty().filter { capture ->
                capture.file !in delivered && accepting.any { WorkflowCatalog.matches(capture.mimeType, it) }
            }
            val picked = if (multiple) matching else matching.takeLast(1)
            if (picked.isEmpty()) continue
            delivered += picked.map { it.file }
            return picked.map { it.file }
        }
        return emptyList()
    }

    /** Shares the last step's files and ends the run. */
    fun finish() {
        if (!isFinished) return
        share(captured)
        reset()
    }
}
