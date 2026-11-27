package com.delphi.delphitools.shell

/** dt-ios Workflow: two or three tools run in order, each one's export handed to the next. */
data class Workflow(val id: String, val name: String, val steps: List<String>) {
    val tools: List<Tool> = steps.mapNotNull(::toolWithId)
    val category: ToolCategory? get() = tools.firstOrNull()?.category
}

/** What a tool takes in through its drop zone and what its export writes, as MIME types. */
class WorkflowPorts(
    val accepts: List<String> = emptyList(),
    val produces: List<String> = emptyList(),
    val takesColour: Boolean = false,
    // every colour, not first
    val takesPalette: Boolean = false,
    val makesColour: Boolean = false,
)

private const val IMAGE = "image/*"
private const val VIDEO = "video/*"
private const val AUDIO = "audio/*"
private const val PDF = "application/pdf"
private const val SVG = "image/svg+xml"
private const val PNG = "image/png"
private const val JPEG = "image/jpeg"
private const val MP4 = "video/mp4"
private const val WAV = "audio/wav"
private const val M4A = "audio/mp4"
private const val SRT = "application/x-subrip"
private const val VTT = "text/vtt"

/** dt-ios WorkflowCatalog. */
object WorkflowCatalog {
    const val SLOTS = 3

    // existing ios strings
    val presets: List<Workflow> = listOf(
        Workflow("trim-caption-burn", "Trim, caption, burn", listOf("video-trimmer", "auto-subtitle", "subtitle-studio")),
        Workflow("trim-and-mute", "Trim and mute", listOf("video-trimmer", "video-muter")),
        Workflow("frame-and-cut-out", "Frame, cut out", listOf("frame-extractor", "background-remover")),
        Workflow("audio-to-subtitles", "Audio to subtitles", listOf("audio-extractor", "audio-trimmer", "auto-subtitle")),
        Workflow("extract-and-normalise", "Extract and normalise", listOf("audio-extractor", "audio-normaliser")),
        Workflow("record-level-transcribe", "Record, level, transcribe", listOf("voice-recorder", "audio-normaliser", "auto-subtitle")),
        Workflow("paste-and-strip", "Paste and strip", listOf("paste-image", "metadata-stripper")),
        Workflow("cut-out-crop-compress", "Cut out, crop, compress", listOf("background-remover", "social-cropper", "image-compressor")),
        Workflow("watermark-and-compress", "Watermark and compress", listOf("watermarker", "image-compressor")),
        Workflow("trace-and-optimise", "Trace and optimise", listOf("image-tracer", "svg-optimiser")),
        Workflow("straighten-to-pdf", "Straighten, then PDF", listOf("image-deskewer", "image-to-pdf", "pdf-compressor")),
        Workflow("images-to-pdf", "Images to PDF", listOf("image-to-pdf", "pdf-compressor")),
        Workflow("scan-and-compress", "Scan and compress", listOf("document-scanner", "pdf-compressor")),
        Workflow("organise-number-compress", "Organise, number, compress", listOf("pdf-organiser", "pdf-page-numberer", "pdf-compressor")),
        Workflow("crop-and-impose", "Crop and impose", listOf("pdf-rotate-crop", "imposer")),
        Workflow("colour-to-gradient", "Colour to gradient", listOf("colour-converter", "gradient-genny")),
        Workflow("pick-then-gradient", "Pick, then gradient", listOf("pixel-picker", "gradient-genny")),
    )

    private val subtitles = listOf("text/*", SRT)

    /** Accepts mirror each tool's CanonDropZone types; a tool without a drop zone can only start. */
    val ports: Map<String, WorkflowPorts> = mapOf(
        "audio-extractor" to WorkflowPorts(listOf(VIDEO), listOf(WAV, M4A)),
        "audio-normaliser" to WorkflowPorts(listOf(AUDIO), listOf(WAV)),
        "audio-trimmer" to WorkflowPorts(listOf(AUDIO), listOf(WAV)),
        "auto-subtitle" to WorkflowPorts(listOf(AUDIO, VIDEO), listOf(SRT, VTT)),
        "frame-extractor" to WorkflowPorts(listOf(VIDEO), listOf(PNG)),
        "subtitle-converter" to WorkflowPorts(subtitles, listOf(SRT, VTT)),
        "subtitle-studio" to WorkflowPorts(listOf(VIDEO) + subtitles, listOf(MP4)),
        "video-muter" to WorkflowPorts(listOf(VIDEO), listOf(MP4)),
        "video-to-gif" to WorkflowPorts(listOf(VIDEO), listOf("image/gif")),
        "video-trimmer" to WorkflowPorts(listOf(VIDEO), listOf(MP4)),
        "voice-recorder" to WorkflowPorts(produces = listOf(M4A)),
        "waveform-genny" to WorkflowPorts(listOf(AUDIO), listOf(PNG, SVG)),

        "image-to-pdf" to WorkflowPorts(listOf(IMAGE, PDF), listOf(PDF, PNG)),
        "pdf-compressor" to WorkflowPorts(listOf(PDF), listOf(PDF)),
        "pdf-organiser" to WorkflowPorts(listOf(PDF), listOf(PDF)),
        "pdf-page-numberer" to WorkflowPorts(listOf(PDF), listOf(PDF)),
        "pdf-rotate-crop" to WorkflowPorts(listOf(PDF), listOf(PDF)),
        "imposer" to WorkflowPorts(listOf(PDF), listOf(PDF)),
        "zine-imposer" to WorkflowPorts(listOf(IMAGE), listOf(PDF)),

        "colour-converter" to WorkflowPorts(takesColour = true, makesColour = true),
        "gradient-genny" to WorkflowPorts(produces = listOf(PNG), takesColour = true, takesPalette = true),
        "pixel-picker" to WorkflowPorts(listOf(IMAGE), makesColour = true),

        "artwork-enhancer" to WorkflowPorts(listOf(IMAGE), listOf(PNG)),
        "background-remover" to WorkflowPorts(listOf(IMAGE), listOf(PNG)),
        "document-scanner" to WorkflowPorts(listOf(IMAGE), listOf(PDF)),
        "favicon-genny" to WorkflowPorts(listOf(IMAGE), listOf(PNG, "image/x-icon")),
        "image-clipper" to WorkflowPorts(listOf(IMAGE), listOf(PNG)),
        "image-compressor" to WorkflowPorts(listOf(IMAGE), listOf(JPEG, PNG, "image/webp")),
        "image-converter" to WorkflowPorts(listOf(IMAGE), listOf(PNG, JPEG, "image/webp")),
        "image-deskewer" to WorkflowPorts(listOf(IMAGE), listOf(PNG)),
        "image-masker" to WorkflowPorts(listOf(IMAGE), listOf(PNG)),
        "image-splitter" to WorkflowPorts(listOf(IMAGE), listOf(PNG)),
        "image-stitcher" to WorkflowPorts(listOf(IMAGE), listOf(PNG, JPEG)),
        "image-tracer" to WorkflowPorts(listOf(IMAGE), listOf(SVG)),
        "metadata-stripper" to WorkflowPorts(listOf(IMAGE), listOf(JPEG, PNG)),
        "paste-image" to WorkflowPorts(produces = listOf(PNG)),
        "placeholder-genny" to WorkflowPorts(produces = listOf(PNG, SVG)),
        "svg-optimiser" to WorkflowPorts(listOf(SVG), listOf(SVG)),

        "matte-generator" to WorkflowPorts(listOf(IMAGE), listOf(PNG)),
        "scroll-generator" to WorkflowPorts(listOf(IMAGE), listOf(PNG)),
        "social-cropper" to WorkflowPorts(listOf(IMAGE), listOf(PNG)),
        "watermarker" to WorkflowPorts(listOf(IMAGE), listOf(PNG)),

        "code-genny" to WorkflowPorts(produces = listOf(PNG, SVG)),
        "json-formatter" to WorkflowPorts(produces = listOf("application/json")),
        "markdown-writer" to WorkflowPorts(produces = listOf("text/plain")),
        "qr-genny" to WorkflowPorts(produces = listOf(PNG, SVG)),
        "shavian-transliterator" to WorkflowPorts(produces = listOf(PNG)),
    )

    /** A step must hand something on. */
    fun isEligible(tool: Tool): Boolean {
        val ports = ports[tool.id] ?: return false
        return ports.produces.isNotEmpty() || ports.makesColour
    }

    fun canFollow(previous: Tool, next: Tool): Boolean {
        if (!isEligible(next)) return false
        val out = ports[previous.id] ?: return false
        val into = ports[next.id] ?: return false
        if (into.takesColour && out.makesColour) return true
        return out.produces.any { made -> into.accepts.any { matches(made, it) } }
    }

    /** SVG is an image type, but the image drop zones decode bitmaps and cannot read it. */
    fun matches(made: String, accepted: String): Boolean {
        val fits = accepted == "*/*" || accepted == made || accepted.endsWith("/*") && made.startsWith(accepted.dropLast(1))
        return fits && (made != SVG || accepted == SVG)
    }

    val startTools: List<Tool> get() = tools.filter(::isEligible)

    fun nextTools(after: Tool): List<Tool> = tools.filter { canFollow(after, it) }

    /** null unless every step follows the one before and the last writes a file. */
    fun custom(steps: List<String>, name: String? = null): Workflow? {
        val chain = steps.mapNotNull { id -> tools.firstOrNull { it.id == id } }
        if (steps.size !in 2..SLOTS || chain.size != steps.size || !isEligible(chain[0])) return null
        if (!chain.zipWithNext().all { (previous, next) -> canFollow(previous, next) }) return null
        if (ports[chain.last().id]?.produces.isNullOrEmpty()) return null
        return Workflow("custom:" + steps.joinToString("."), name ?: chain.joinToString(", ") { it.name }, steps)
    }
}
