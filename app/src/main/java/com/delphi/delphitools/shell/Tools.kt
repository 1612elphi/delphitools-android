package com.delphi.delphitools.shell

import androidx.annotation.DrawableRes
import com.delphi.delphitools.R

// dt-ios Core/Models/Tool.swift ToolCategory, in CategoryOrderStore.defaultOrder; icons for its SF Symbols
enum class ToolCategory(val title: String, @DrawableRes val icon: Int) {
    SocialMedia("Social Media", R.drawable.pencil_share),
    Colour("Colour", R.drawable.pencil_palette),
    Images("Images", R.drawable.pencil_image),
    AudioVideo("Audio & Video", R.drawable.pencil_clapperboard),
    Typography("Typography", R.drawable.pencil_type),
    Pdf("PDF", R.drawable.pencil_file),
    Print("Print", R.drawable.pencil_printer),
    DevTools("Dev Tools", R.drawable.pencil_wrench),
    Calculators("Calculators", R.drawable.pencil_variable),
    TurboNerd("Turbo-nerd Shit", R.drawable.pencil_brain),
}

/** One registry entry; `icon` is the iOS drawn icon. */
class Tool(
    val id: String,
    val name: String,
    val description: String,
    @DrawableRes val icon: Int,
    val category: ToolCategory,
)

// dt-ios Core/ToolRegistry.swift; descriptions are the existing iOS strings
val allTools: List<Tool> = listOf(
    Tool("audio-atlas", "Audio Atlas", "Everything about one audio file, on one page", R.drawable.pencil_audio_lines, ToolCategory.AudioVideo),
    Tool("audio-extractor", "Audio Extractor", "Extract the audio out of a video file", R.drawable.pencil_file_audio, ToolCategory.AudioVideo),
    Tool("audio-normaliser", "Audio Normaliser", "Normalise audio loudness", R.drawable.pencil_gauge, ToolCategory.AudioVideo),
    Tool("audio-trimmer", "Audio Trimmer", "Cut and fade audio, export as WAV", R.drawable.pencil_scissors, ToolCategory.AudioVideo),
    Tool("auto-subtitle", "Auto Subtitle", "Transcribe audio and video to subtitles", R.drawable.pencil_captions, ToolCategory.AudioVideo),
    Tool("frame-extractor", "Frame Extractor", "Grab stills and contact sheets from video", R.drawable.pencil_film, ToolCategory.AudioVideo),
    Tool("subtitle-converter", "Subtitle Converter", "Convert, shift and rescale SRT and VTT subtitles", R.drawable.pencil_captions, ToolCategory.AudioVideo),
    Tool("subtitle-studio", "Subtitle Studio", "Burn subtitles to video", R.drawable.pencil_subtitles, ToolCategory.AudioVideo),
    Tool("timecode-calc", "Timecode Calculator", "Add and subtract timecodes, drop-frame aware", R.drawable.pencil_clock, ToolCategory.AudioVideo),
    Tool("video-atlas", "Video Atlas", "Everything about one video file", R.drawable.pencil_clapperboard, ToolCategory.AudioVideo),
    Tool("video-muter", "Video Muter", "Strip the audio from a video", R.drawable.pencil_volume_x, ToolCategory.AudioVideo),
    Tool("video-to-gif", "Video to GIF", "Turn video clips into looping GIFs", R.drawable.pencil_clapperboard, ToolCategory.AudioVideo),
    Tool("video-trimmer", "Video Trimmer", "Cut a video in/out style", R.drawable.pencil_scissors, ToolCategory.AudioVideo),
    Tool("voice-recorder", "Voice Recorder", "Record voice memos", R.drawable.pencil_mic, ToolCategory.AudioVideo),
    Tool("waveform-genny", "Waveform Generator", "Render audio waveforms as PNG or SVG", R.drawable.pencil_audio_waveform, ToolCategory.AudioVideo),

    Tool("algebra-calc", "Algebra Calculator", "Symbolic algebra: simplify, factor, solve, derivatives", R.drawable.pencil_variable, ToolCategory.Calculators),
    Tool("graph-calc", "Graph Calculator", "Plot and visualise mathematical functions", R.drawable.pencil_line_chart, ToolCategory.Calculators),
    Tool("sci-calc", "Scientific Calculator", "Full-featured scientific calculator with history", R.drawable.pencil_calculator, ToolCategory.Calculators),
    Tool("time-calc", "Time Calculator", "Unix timestamps, date arithmetic, timezone conversion", R.drawable.pencil_clock, ToolCategory.Calculators),
    Tool("unit-converter", "Unit Converter", "Convert between units of length, weight, data, and more", R.drawable.pencil_scale, ToolCategory.Calculators),

    Tool("colour-atlas", "Colour Atlas", "Everything about one colour, on one page", R.drawable.pencil_swatch_book, ToolCategory.Colour),
    Tool("colorblind-sim", "Colour Blindness Simulator", "Simulate how colours appear to colour blind users", R.drawable.pencil_eye, ToolCategory.Colour),
    Tool("colour-camera", "Colour Camera", "Pick colours and extract palettes from the camera", R.drawable.pencil_aperture, ToolCategory.Colour),
    Tool("colour-converter", "Colour Converter", "Convert between colour formats", R.drawable.pencil_pipette, ToolCategory.Colour),
    Tool("contrast-checker", "Contrast Checker", "Check WCAG colour contrast compliance", R.drawable.pencil_contrast, ToolCategory.Colour),
    Tool("gradient-genny", "Gradient Generator", "Create linear, corner, and mesh gradients", R.drawable.pencil_blend, ToolCategory.Colour),
    Tool("harmony-genny", "Harmony Generator", "Generate colour harmonies", R.drawable.pencil_rainbow, ToolCategory.Colour),
    Tool("palette-collection", "Palette Collection", "Browse curated colour palettes", R.drawable.pencil_library, ToolCategory.Colour),
    Tool("palette-extractor", "Palette Extractor", "Extract colour palettes from images", R.drawable.pencil_palette, ToolCategory.Colour),
    Tool("palette-genny", "Palette Generator", "Generate beautiful colour palettes", R.drawable.pencil_pen_line, ToolCategory.Colour),
    Tool("pixel-picker", "Pixel Picker", "Sample colours from any image with a zoom loupe", R.drawable.pencil_crosshair, ToolCategory.Colour),
    Tool("tailwind-shades", "Tailwind Shade Generator", "Generate Tailwind colour scales", R.drawable.pencil_wind, ToolCategory.Colour),

    Tool("code-genny", "Barcode Generator", "Generate Data Matrix, Aztec, PDF417, Code 128, EAN-13, and more", R.drawable.pencil_barcode, ToolCategory.DevTools),
    Tool("base-converter", "Base Converter", "Convert between decimal, hex, binary, and octal", R.drawable.pencil_binary, ToolCategory.DevTools),
    Tool("decoder", "Cipher Decoder", "Decode classical ciphers manually or auto-detect the cipher", R.drawable.pencil_key_round, ToolCategory.DevTools),
    Tool("cron-builder", "Cron Builder", "Build a cron expression field by field or decode one", R.drawable.pencil_calendar_clock, ToolCategory.DevTools),
    Tool("encoder", "Encoding Tools", "Base64, URL encoding, and hash generation", R.drawable.pencil_file_code, ToolCategory.DevTools),
    Tool("http-status", "HTTP Status", "Search HTTP status codes with phrases and spec references", R.drawable.pencil_server, ToolCategory.DevTools),
    Tool("json-formatter", "JSON Formatter", "Format or minify JSON or look at it", R.drawable.pencil_braces, ToolCategory.DevTools),
    Tool("jwt-decoder", "JWT Decoder", "Decode a JWT's header, payload & claims", R.drawable.pencil_key_square, ToolCategory.DevTools),
    Tool("meta-tag-genny", "Meta Tag Generator", "Generate HTML meta tags", R.drawable.pencil_tag, ToolCategory.DevTools),
    Tool("password-genny", "Password Generator", "Generate strong passwords or multi-word passphrases", R.drawable.pencil_key_square, ToolCategory.DevTools),
    Tool("qr-genny", "QR Generator", "Generate styled QR codes with custom colours, shapes, and logos", R.drawable.pencil_qr_code, ToolCategory.DevTools),
    Tool("regex-tester", "Regex Tester", "Test regular expressions", R.drawable.pencil_regex, ToolCategory.DevTools),
    Tool("request-builder", "Request Builder", "Compose a cURL or HTTP request", R.drawable.pencil_terminal, ToolCategory.DevTools),
    Tool("tailwind-cheatsheet", "Tailwind Cheat Sheet", "Quick reference for Tailwind classes", R.drawable.pencil_book_open, ToolCategory.DevTools),
    Tool("text-diff", "Text Diff", "Compare two texts and highlight differences", R.drawable.pencil_git_compare, ToolCategory.DevTools),
    Tool("markdown-writer", "Text Scratchpad", "Text editor with manipulation tools", R.drawable.pencil_pen_line, ToolCategory.DevTools),
    Tool("uuid-genny", "UUID Generator", "Generate bulk UUID v4 or v7 and Nano IDs", R.drawable.pencil_fingerprint, ToolCategory.DevTools),

    Tool("artwork-enhancer", "Artwork Enhancer", "Add colour noise overlay to artwork", R.drawable.pencil_sparkles, ToolCategory.Images),
    Tool("background-remover", "Background Remover", "Remove backgrounds from images automatically", R.drawable.pencil_eraser, ToolCategory.Images),
    Tool("base64-image-encoder", "Base64 Image Encoder", "Convert images to Base64 strings for CSS/HTML embedding", R.drawable.pencil_file_code, ToolCategory.Images),
    Tool("document-scanner", "Document Scanner", "Scan documents with edge detection and OCR", R.drawable.pencil_scan_line, ToolCategory.Images),
    Tool("favicon-genny", "Favicon Generator", "Generate favicons from any image", R.drawable.pencil_image, ToolCategory.Images),
    Tool("image-atlas", "Image Atlas", "Everything about one image, on one page", R.drawable.pencil_image, ToolCategory.Images),
    Tool("image-clipper", "Image Clipper", "Trim transparent edges from PNGs", R.drawable.pencil_crop, ToolCategory.Images),
    Tool("image-compressor", "Image Compressor", "Shrink JPEG, WebP, PNG and AVIF files", R.drawable.pencil_shrink, ToolCategory.Images),
    Tool("image-converter", "Image Converter", "Convert between PNG, JPEG, HEIC, GIF, BMP, and TIFF", R.drawable.pencil_refresh_cw, ToolCategory.Images),
    Tool("image-deskewer", "Image De-skewer", "Straighten an image by the corners", R.drawable.pencil_vector_square, ToolCategory.Images),
    Tool("image-masker", "Image Masker", "Cut images into shapes", R.drawable.pencil_shapes, ToolCategory.Images),
    Tool("image-splitter", "Image Splitter", "Split images into tiles", R.drawable.pencil_scissors, ToolCategory.Images),
    Tool("image-stitcher", "Image Stitcher", "Stitch multiple images into a mosaic or grid", R.drawable.pencil_combine, ToolCategory.Images),
    Tool("image-tracer", "Image Tracer", "Trace raster images to SVG vectors", R.drawable.pencil_scan_line, ToolCategory.Images),
    Tool("metadata-stripper", "Metadata Stripper", "Strip EXIF and GPS metadata from images", R.drawable.pencil_shield_check, ToolCategory.Images),
    Tool("paste-image", "Paste Image", "Paste an image from your clipboard and save it", R.drawable.pencil_clipboard_paste, ToolCategory.Images),
    Tool("placeholder-genny", "Placeholder Generator", "Generate placeholder images", R.drawable.pencil_layout_grid, ToolCategory.Images),
    Tool("svg-optimiser", "SVG Optimiser", "Optimise and minify SVG files", R.drawable.pencil_file_image, ToolCategory.Images),

    Tool("image-to-pdf", "Images to PDF", "Turn images into a PDF, or pages into PNGs", R.drawable.pencil_file_image, ToolCategory.Pdf),
    Tool("pdf-compressor", "PDF Compressor", "Shrink PDF filesizes", R.drawable.pencil_shrink, ToolCategory.Pdf),
    Tool("pdf-organiser", "PDF Organiser", "Merge, split and rearrange PDF pages", R.drawable.pencil_file_stack, ToolCategory.Pdf),
    Tool("pdf-page-numberer", "PDF Page Numbers", "Add page numbers or stamps to documents", R.drawable.pencil_file_digit, ToolCategory.Pdf),
    Tool("pdf-preflight", "PDF Preflight", "Analyse PDFs for print-readiness issues", R.drawable.pencil_file_search, ToolCategory.Pdf),
    Tool("pdf-rotate-crop", "PDF Rotate & Crop", "Rotate or crop pages or entire documents", R.drawable.pencil_crop, ToolCategory.Pdf),

    Tool("paper-sizes", "Paper Sizes", "Reference for paper dimensions", R.drawable.pencil_file_text, ToolCategory.Print),
    Tool("imposer", "Print Imposer", "Impose PDF pages for booklet, saddle-stitch, and N-up printing", R.drawable.pencil_layers, ToolCategory.Print),
    Tool("zine-imposer", "Zine Imposer", "Create 8-page mini-zine imposition layouts", R.drawable.pencil_book_open, ToolCategory.Print),

    Tool("matte-generator", "Matte Generator", "Put non-square images on a square matte", R.drawable.pencil_square, ToolCategory.SocialMedia),
    Tool("scroll-generator", "Seamless Scroll Generator", "Split images for Instagram carousel scrolls", R.drawable.pencil_gallery_vertical, ToolCategory.SocialMedia),
    Tool("social-cropper", "Social Media Cropper", "Crop images for Instagram, Bluesky & Threads", R.drawable.pencil_crop, ToolCategory.SocialMedia),
    Tool("watermarker", "Watermarker", "Add watermarks to images", R.drawable.pencil_stamp, ToolCategory.SocialMedia),

    Tool("braille-converter", "Braille Converter", "Turn text into Braille cells", R.drawable.pencil_grip_vertical, ToolCategory.TurboNerd),
    Tool("ipa-transcriber", "IPA Transcription", "Transcribe English text into IPA", R.drawable.pencil_ear, ToolCategory.TurboNerd),
    Tool("morse-code", "Morse Code", "Encode, decode and play Morse code", R.drawable.pencil_radio, ToolCategory.TurboNerd),
    Tool("nato-phonetic", "NATO Phonetic", "Translate text to NATO or DIN-5009 calls", R.drawable.pencil_radio_tower, ToolCategory.TurboNerd),
    Tool("nfc-reader-writer", "NFC Reader/Writer", "Read and write NFC tags", R.drawable.pencil_audio_lines, ToolCategory.TurboNerd),
    Tool("shavian-transliterator", "Shavian Transliterator", "Transliterate English text to the Shavian alphabet", R.drawable.pencil_languages, ToolCategory.TurboNerd),

    Tool("doc-converter", "Document Converter", "Convert documents between Markdown, HTML, Word, LaTeX, EPUB and more", R.drawable.pencil_file_type_2, ToolCategory.Typography),
    Tool("font-explorer", "Font File Explorer", "Explore font file contents", R.drawable.pencil_file_type, ToolCategory.Typography),
    Tool("glyph-browser", "Glyph Browser", "Browse unicode glyphs", R.drawable.pencil_type, ToolCategory.Typography),
    Tool("large-type", "Large Type", "Text, but big", R.drawable.pencil_case_upper, ToolCategory.Typography),
    Tool("line-height-calc", "Line Height Calculator", "Calculate optimal line heights", R.drawable.pencil_type, ToolCategory.Typography),
    Tool("px-to-rem", "PX to REM", "Convert pixels to rem units", R.drawable.pencil_ruler, ToolCategory.Typography),
    Tool("text-editor", "Text Editor", "Distraction-free Markdown writer", R.drawable.pencil_pen_line, ToolCategory.Typography),
    Tool("typo-calc", "Typography Calculator", "Convert between typographic units", R.drawable.pencil_hash, ToolCategory.Typography),
    Tool("word-counter", "Word Counter", "Count words, characters and more", R.drawable.pencil_book_open, ToolCategory.Typography),
)

/** The ported tools; the home, the fan, search, favourites and recents list only these. */
val tools: List<Tool> = allTools.filter { it.id in toolPages }

// iOS debug builds only; the shell shows it in every build
val canonGallery = Tool("canon-gallery", "Canon Gallery", "Every canon component", R.drawable.pencil_grid_3x3, ToolCategory.DevTools)

/** The Workflows page, opened like a tool; in no list of tools, so the fan and search skip it. */
val workflowsTool = Tool("workflows", "Workflows", "Execute multi-tool sequences", R.drawable.pencil_workflow, ToolCategory.DevTools)

val greatestHits = listOf(workflowsTool) + listOf("qr-genny", "auto-subtitle", "background-remover").mapNotNull { id -> tools.firstOrNull { it.id == id } }

fun toolWithId(id: String): Tool? = when (id) {
    canonGallery.id -> canonGallery
    workflowsTool.id -> workflowsTool
    else -> allTools.firstOrNull { it.id == id }
}

fun toolsIn(category: ToolCategory): List<Tool> = tools.filter { it.category == category }

/** Each category in `order` that has a ported tool, with its tools. */
fun shownCategories(order: List<ToolCategory>): List<Pair<ToolCategory, List<Tool>>> =
    order.map { it to toolsIn(it) }.filter { it.second.isNotEmpty() }

// dt-ios ToolRegistry searchKeywords
private val searchKeywords: Map<String, List<String>> = mapOf(
    "colorblind-sim" to listOf("deuteranopia", "protanopia", "tritanopia", "accessibility", "a11y"),
    "colour-camera" to listOf("eyedropper", "color picker", "sample"),
    "colour-converter" to listOf("hex", "rgb", "hsl", "oklch", "color", "cmyk"),
    "contrast-checker" to listOf("WCAG", "accessibility", "a11y"),
    "tailwind-shades" to listOf("css", "design tokens"),
    "background-remover" to listOf("cutout", "transparent", "remove bg"),
    "favicon-genny" to listOf("icon", "website", "tab icon"),
    "image-clipper" to listOf("crop", "transparent", "PNG", "trim"),
    "image-stitcher" to listOf("stitch", "mosaic", "combine", "merge", "collage", "grid"),
    "image-tracer" to listOf("vectorise", "bitmap to vector", "trace"),
    "paste-image" to listOf("clipboard", "save image"),
    "placeholder-genny" to listOf("dummy image", "wireframe"),
    "svg-optimiser" to listOf("minify", "compress", "vector"),
    "social-cropper" to listOf("instagram", "twitter", "facebook", "resize"),
    "px-to-rem" to listOf("pixel", "em", "css", "web"),
    "code-genny" to listOf("barcode", "EAN", "UPC", "Code 128"),
    "base-converter" to listOf("binary", "octal", "decimal", "hexadecimal"),
    "encoder" to listOf("base64", "URL encode", "hash", "SHA", "MD5"),
    "meta-tag-genny" to listOf("SEO", "open graph", "twitter card", "og:image"),
    "qr-genny" to listOf("QR code", "barcode", "scan"),
    "regex-tester" to listOf("regular expression", "pattern match"),
    "text-diff" to listOf("compare", "diff", "merge"),
    "nfc-reader-writer" to listOf("NFC", "NDEF", "tag"),
    "shavian-transliterator" to listOf("Shaw alphabet", "transliterate"),
)

// dt-ios ToolRegistry isBeta
val Tool.isBeta get() = id == "background-remover"

/** dt-ios ToolRegistry.search: name, description, category or a keyword contains the query, ignoring case. */
fun searchTools(query: String): List<Tool> {
    if (query.isEmpty()) return tools
    return tools.filter { tool ->
        listOf(tool.name, tool.description, tool.category.title).plus(searchKeywords[tool.id].orEmpty())
            .any { it.contains(query, ignoreCase = true) }
    }
}
