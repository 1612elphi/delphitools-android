package com.delphi.delphitools.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.Abc
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Attachment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.CropRotate
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DataArray
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.FilterCenterFocus
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Gradient
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Looks
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Polyline
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Transform
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VideoLabel
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.ui.graphics.vector.ImageVector

// dt-ios Core/Models/Tool.swift ToolCategory, in CategoryOrderStore.defaultOrder; icons for its SF Symbols
enum class ToolCategory(val title: String, val icon: ImageVector) {
    SocialMedia("Social Media", Icons.Outlined.IosShare),
    Colour("Colour", Icons.Outlined.Palette),
    Images("Images", Icons.Outlined.Image),
    AudioVideo("Audio & Video", Icons.Outlined.SmartDisplay),
    Typography("Typography", Icons.Outlined.TextFields),
    Pdf("PDF", Icons.Outlined.Description),
    Print("Print", Icons.Outlined.Print),
    DevTools("Dev Tools", Icons.Outlined.Build),
    Calculators("Calculators", Icons.Outlined.Functions),
    TurboNerd("Turbo-nerd Shit", Icons.Outlined.Psychology),
}

/** One registry entry; `icon` is the closest Material icon to the iOS SF Symbol. */
class Tool(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val category: ToolCategory,
)

// dt-ios Core/ToolRegistry.swift; descriptions are the existing iOS strings
val allTools: List<Tool> = with(Icons.Outlined) {
    listOf(
        Tool("audio-atlas", "Audio Atlas", "Everything about one audio file, on one page", GraphicEq, ToolCategory.AudioVideo),
        Tool("audio-extractor", "Audio Extractor", "Extract the audio out of a video file", MusicNote, ToolCategory.AudioVideo),
        Tool("audio-normaliser", "Audio Normaliser", "Normalise audio loudness", Speed, ToolCategory.AudioVideo),
        Tool("audio-trimmer", "Audio Trimmer", "Cut and fade audio, export as WAV", ContentCut, ToolCategory.AudioVideo),
        Tool("auto-subtitle", "Auto Subtitle", "Transcribe audio and video to subtitles", ClosedCaption, ToolCategory.AudioVideo),
        Tool("frame-extractor", "Frame Extractor", "Grab stills and contact sheets from video", Movie, ToolCategory.AudioVideo),
        Tool("subtitle-converter", "Subtitle Converter", "Convert, shift and rescale SRT and VTT subtitles", Subtitles, ToolCategory.AudioVideo),
        Tool("subtitle-studio", "Subtitle Studio", "Burn subtitles to video", VideoLabel, ToolCategory.AudioVideo),
        Tool("timecode-calc", "Timecode Calculator", "Add and subtract timecodes, drop-frame aware", Timer, ToolCategory.AudioVideo),
        Tool("video-atlas", "Video Atlas", "Everything about one video file", MovieCreation, ToolCategory.AudioVideo),
        Tool("video-muter", "Video Muter", "Strip the audio from a video", Icons.AutoMirrored.Outlined.VolumeOff, ToolCategory.AudioVideo),
        Tool("video-to-gif", "Video to GIF", "Turn video clips into looping GIFs", Repeat, ToolCategory.AudioVideo),
        Tool("video-trimmer", "Video Trimmer", "Cut a video in/out style", Timeline, ToolCategory.AudioVideo),
        Tool("voice-recorder", "Voice Recorder", "Record voice memos", Mic, ToolCategory.AudioVideo),
        Tool("waveform-genny", "Waveform Generator", "Render audio waveforms as PNG or SVG", Waves, ToolCategory.AudioVideo),

        Tool("algebra-calc", "Algebra Calculator", "Symbolic algebra: simplify, factor, solve, derivatives", Functions, ToolCategory.Calculators),
        Tool("graph-calc", "Graph Calculator", "Plot and visualise mathematical functions", Icons.AutoMirrored.Outlined.ShowChart, ToolCategory.Calculators),
        Tool("sci-calc", "Scientific Calculator", "Full-featured scientific calculator with history", Calculate, ToolCategory.Calculators),
        Tool("time-calc", "Time Calculator", "Unix timestamps, date arithmetic, timezone conversion", Schedule, ToolCategory.Calculators),
        Tool("unit-converter", "Unit Converter", "Convert between units of length, weight, data, and more", Scale, ToolCategory.Calculators),

        Tool("colour-atlas", "Colour Atlas", "Everything about one colour, on one page", Palette, ToolCategory.Colour),
        Tool("colorblind-sim", "Colour Blindness Simulator", "Simulate how colours appear to colour blind users", Visibility, ToolCategory.Colour),
        Tool("colour-camera", "Colour Camera", "Pick colours and extract palettes from the camera", Camera, ToolCategory.Colour),
        Tool("colour-converter", "Colour Converter", "Convert between colour formats", Colorize, ToolCategory.Colour),
        Tool("contrast-checker", "Contrast Checker", "Check WCAG colour contrast compliance", Contrast, ToolCategory.Colour),
        Tool("gradient-genny", "Gradient Generator", "Create linear, corner, and mesh gradients", Gradient, ToolCategory.Colour),
        Tool("harmony-genny", "Harmony Generator", "Generate colour harmonies", Looks, ToolCategory.Colour),
        Tool("palette-collection", "Palette Collection", "Browse curated colour palettes", CollectionsBookmark, ToolCategory.Colour),
        Tool("palette-extractor", "Palette Extractor", "Extract colour palettes from images", ColorLens, ToolCategory.Colour),
        Tool("palette-genny", "Palette Generator", "Generate beautiful colour palettes", Brush, ToolCategory.Colour),
        Tool("pixel-picker", "Pixel Picker", "Sample colours from any image with a zoom loupe", FilterCenterFocus, ToolCategory.Colour),
        Tool("tailwind-shades", "Tailwind Shade Generator", "Generate Tailwind colour scales", ColorLens, ToolCategory.Colour),

        Tool("code-genny", "Barcode Generator", "Generate Data Matrix, Aztec, PDF417, Code 128, EAN-13, and more", ViewWeek, ToolCategory.DevTools),
        Tool("base-converter", "Base Converter", "Convert between decimal, hex, binary, and octal", Numbers, ToolCategory.DevTools),
        Tool("decoder", "Cipher Decoder", "Decode classical ciphers manually or auto-detect the cipher", Key, ToolCategory.DevTools),
        Tool("cron-builder", "Cron Builder", "Build a cron expression field by field or decode one", EventRepeat, ToolCategory.DevTools),
        Tool("encoder", "Encoding Tools", "Base64, URL encoding, and hash generation", Lock, ToolCategory.DevTools),
        Tool("http-status", "HTTP Status", "Search HTTP status codes with phrases and spec references", Dns, ToolCategory.DevTools),
        Tool("json-formatter", "JSON Formatter", "Format or minify JSON or look at it", DataObject, ToolCategory.DevTools),
        Tool("jwt-decoder", "JWT Decoder", "Decode a JWT's header, payload & claims", VpnKey, ToolCategory.DevTools),
        Tool("meta-tag-genny", "Meta Tag Generator", "Generate HTML meta tags", LocalOffer, ToolCategory.DevTools),
        Tool("password-genny", "Password Generator", "Generate strong passwords or multi-word passphrases", Password, ToolCategory.DevTools),
        Tool("qr-genny", "QR Generator", "Generate styled QR codes with custom colours, shapes, and logos", QrCode, ToolCategory.DevTools),
        Tool("regex-tester", "Regex Tester", "Test regular expressions", Code, ToolCategory.DevTools),
        Tool("request-builder", "Request Builder", "Compose a cURL or HTTP request", Terminal, ToolCategory.DevTools),
        Tool("tailwind-cheatsheet", "Tailwind Cheat Sheet", "Quick reference for Tailwind classes", Icons.AutoMirrored.Outlined.MenuBook, ToolCategory.DevTools),
        Tool("text-diff", "Text Diff", "Compare two texts and highlight differences", Difference, ToolCategory.DevTools),
        Tool("markdown-writer", "Text Scratchpad", "Text editor with manipulation tools", Edit, ToolCategory.DevTools),
        Tool("uuid-genny", "UUID Generator", "Generate bulk UUID v4 or v7 and Nano IDs", Fingerprint, ToolCategory.DevTools),

        Tool("artwork-enhancer", "Artwork Enhancer", "Add colour noise overlay to artwork", AutoAwesome, ToolCategory.Images),
        Tool("background-remover", "Background Remover", "Remove backgrounds from images automatically", AutoFixHigh, ToolCategory.Images),
        Tool("base64-image-encoder", "Base64 Image Encoder", "Convert images to Base64 strings for CSS/HTML embedding", DataArray, ToolCategory.Images),
        Tool("document-scanner", "Document Scanner", "Scan documents with edge detection and OCR", DocumentScanner, ToolCategory.Images),
        Tool("favicon-genny", "Favicon Generator", "Generate favicons from any image", Image, ToolCategory.Images),
        Tool("image-atlas", "Image Atlas", "Everything about one image, on one page", PhotoLibrary, ToolCategory.Images),
        Tool("image-clipper", "Image Clipper", "Trim transparent edges from PNGs", Attachment, ToolCategory.Images),
        Tool("image-compressor", "Image Compressor", "Shrink JPEG, WebP, PNG and AVIF files", CloseFullscreen, ToolCategory.Images),
        Tool("image-converter", "Image Converter", "Convert between PNG, JPEG, HEIC, GIF, BMP, and TIFF", Autorenew, ToolCategory.Images),
        Tool("image-deskewer", "Image De-skewer", "Straighten an image by the corners", Transform, ToolCategory.Images),
        Tool("image-masker", "Image Masker", "Cut images into shapes", Category, ToolCategory.Images),
        Tool("image-splitter", "Image Splitter", "Split images into tiles", ContentCut, ToolCategory.Images),
        Tool("image-stitcher", "Image Stitcher", "Stitch multiple images into a mosaic or grid", Dashboard, ToolCategory.Images),
        Tool("image-tracer", "Image Tracer", "Trace raster images to SVG vectors", Draw, ToolCategory.Images),
        Tool("metadata-stripper", "Metadata Stripper", "Strip EXIF and GPS metadata from images", VerifiedUser, ToolCategory.Images),
        Tool("paste-image", "Paste Image", "Paste an image from your clipboard and save it", ContentPaste, ToolCategory.Images),
        Tool("placeholder-genny", "Placeholder Generator", "Generate placeholder images", GridView, ToolCategory.Images),
        Tool("svg-optimiser", "SVG Optimiser", "Optimise and minify SVG files", Polyline, ToolCategory.Images),

        Tool("image-to-pdf", "Images to PDF", "Turn images into a PDF, or pages into PNGs", PictureAsPdf, ToolCategory.Pdf),
        Tool("pdf-compressor", "PDF Compressor", "Shrink PDF filesizes", Compress, ToolCategory.Pdf),
        Tool("pdf-organiser", "PDF Organiser", "Merge, split and rearrange PDF pages", FileCopy, ToolCategory.Pdf),
        Tool("pdf-page-numberer", "PDF Page Numbers", "Add page numbers or stamps to documents", FormatListNumbered, ToolCategory.Pdf),
        Tool("pdf-preflight", "PDF Preflight", "Analyse PDFs for print-readiness issues", FindInPage, ToolCategory.Pdf),
        Tool("pdf-rotate-crop", "PDF Rotate & Crop", "Rotate or crop pages or entire documents", CropRotate, ToolCategory.Pdf),

        Tool("paper-sizes", "Paper Sizes", "Reference for paper dimensions", AspectRatio, ToolCategory.Print),
        Tool("imposer", "Print Imposer", "Impose PDF pages for booklet, saddle-stitch, and N-up printing", GridOn, ToolCategory.Print),
        Tool("zine-imposer", "Zine Imposer", "Create 8-page mini-zine imposition layouts", AutoStories, ToolCategory.Print),

        Tool("matte-generator", "Matte Generator", "Put non-square images on a square matte", CropSquare, ToolCategory.SocialMedia),
        Tool("scroll-generator", "Seamless Scroll Generator", "Split images for Instagram carousel scrolls", ViewColumn, ToolCategory.SocialMedia),
        Tool("social-cropper", "Social Media Cropper", "Crop images for Instagram, Bluesky & Threads", Crop, ToolCategory.SocialMedia),
        Tool("watermarker", "Watermarker", "Add watermarks to images", Verified, ToolCategory.SocialMedia),

        Tool("braille-converter", "Braille Converter", "Turn text into Braille cells", TouchApp, ToolCategory.TurboNerd),
        Tool("ipa-transcriber", "IPA Transcription", "Transcribe English text into IPA", Hearing, ToolCategory.TurboNerd),
        Tool("morse-code", "Morse Code", "Encode, decode and play Morse code", Sensors, ToolCategory.TurboNerd),
        Tool("nato-phonetic", "NATO Phonetic", "Translate text to NATO or DIN-5009 calls", SettingsInputAntenna, ToolCategory.TurboNerd),
        Tool("nfc-reader-writer", "NFC Reader/Writer", "Read and write NFC tags", Nfc, ToolCategory.TurboNerd),
        Tool("shavian-transliterator", "Shavian Transliterator", "Transliterate English text to the Shavian alphabet", Translate, ToolCategory.TurboNerd),

        Tool("doc-converter", "Document Converter", "Convert documents between Markdown, HTML, Word, LaTeX, EPUB and more", Description, ToolCategory.Typography),
        Tool("font-explorer", "Font File Explorer", "Explore font file contents", FontDownload, ToolCategory.Typography),
        Tool("glyph-browser", "Glyph Browser", "Browse unicode glyphs", Abc, ToolCategory.Typography),
        Tool("large-type", "Large Type", "Text, but big", FormatSize, ToolCategory.Typography),
        Tool("line-height-calc", "Line Height Calculator", "Calculate optimal line heights", FormatLineSpacing, ToolCategory.Typography),
        Tool("px-to-rem", "PX to REM", "Convert pixels to rem units", Straighten, ToolCategory.Typography),
        Tool("text-editor", "Text Editor", "Distraction-free Markdown writer", EditNote, ToolCategory.Typography),
        Tool("typo-calc", "Typography Calculator", "Convert between typographic units", Tag, ToolCategory.Typography),
        Tool("word-counter", "Word Counter", "Count words, characters and more", Icons.AutoMirrored.Outlined.MenuBook, ToolCategory.Typography),
    )
}

/** The ported tools; the home, the fan, search, favourites and recents list only these. */
val tools: List<Tool> = allTools.filter { it.id in toolPages }

// iOS debug builds only; the shell shows it in every build
val canonGallery = Tool("canon-gallery", "Canon Gallery", "Every canon component", Icons.Outlined.Apps, ToolCategory.DevTools)

/** The Workflows page, opened like a tool; in no list of tools, so the fan and search skip it. */
val workflowsTool = Tool("workflows", "Workflows", "Execute multi-tool sequences", Icons.Outlined.AccountTree, ToolCategory.DevTools)

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
