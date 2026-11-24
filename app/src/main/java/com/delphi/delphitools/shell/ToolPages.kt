package com.delphi.delphitools.shell

import androidx.compose.runtime.Composable
import com.delphi.delphitools.tools.audiovideo.AudioAtlas
import com.delphi.delphitools.tools.audiovideo.AudioExtractor
import com.delphi.delphitools.tools.audiovideo.AudioNormaliser
import com.delphi.delphitools.tools.audiovideo.AudioTrimmer
import com.delphi.delphitools.tools.audiovideo.FrameExtractor
import com.delphi.delphitools.tools.audiovideo.SubtitleConverter
import com.delphi.delphitools.tools.audiovideo.SubtitleStudio
import com.delphi.delphitools.tools.audiovideo.TimecodeCalc
import com.delphi.delphitools.tools.audiovideo.VideoAtlas
import com.delphi.delphitools.tools.audiovideo.VideoMuter
import com.delphi.delphitools.tools.audiovideo.VideoToGif
import com.delphi.delphitools.tools.audiovideo.VideoTrimmer
import com.delphi.delphitools.tools.audiovideo.VoiceRecorder
import com.delphi.delphitools.tools.audiovideo.WaveformGenerator
import com.delphi.delphitools.tools.calculators.AlgebraCalc
import com.delphi.delphitools.tools.calculators.GraphCalc
import com.delphi.delphitools.tools.calculators.SciCalc
import com.delphi.delphitools.tools.calculators.TimeCalc
import com.delphi.delphitools.tools.calculators.UnitConverter
import com.delphi.delphitools.tools.colour.ColourAtlas
import com.delphi.delphitools.tools.colour.ColourBlindnessSimulator
import com.delphi.delphitools.tools.colour.ColourConverter
import com.delphi.delphitools.tools.colour.ContrastChecker
import com.delphi.delphitools.tools.colour.GradientGenerator
import com.delphi.delphitools.tools.colour.HarmonyGenerator
import com.delphi.delphitools.tools.colour.PaletteCollection
import com.delphi.delphitools.tools.colour.PaletteExtractor
import com.delphi.delphitools.tools.colour.PaletteGenerator
import com.delphi.delphitools.tools.colour.PixelPicker
import com.delphi.delphitools.tools.colour.TailwindShades
import com.delphi.delphitools.tools.devtools.BarcodeGenerator
import com.delphi.delphitools.tools.devtools.BaseConverter
import com.delphi.delphitools.tools.devtools.CipherDecoder
import com.delphi.delphitools.tools.devtools.CronBuilder
import com.delphi.delphitools.tools.devtools.Encoder
import com.delphi.delphitools.tools.devtools.HttpStatus
import com.delphi.delphitools.tools.devtools.JsonFormatter
import com.delphi.delphitools.tools.devtools.JwtDecoder
import com.delphi.delphitools.tools.devtools.MetaTagGenerator
import com.delphi.delphitools.tools.devtools.PasswordGenerator
import com.delphi.delphitools.tools.devtools.QrGenerator
import com.delphi.delphitools.tools.devtools.RegexTester
import com.delphi.delphitools.tools.devtools.RequestBuilder
import com.delphi.delphitools.tools.devtools.TailwindCheatsheet
import com.delphi.delphitools.tools.devtools.TextDiff
import com.delphi.delphitools.tools.devtools.TextScratchpad
import com.delphi.delphitools.tools.devtools.UuidGenerator
import com.delphi.delphitools.tools.images.Base64ImageEncoder
import com.delphi.delphitools.tools.images.FaviconGenerator
import com.delphi.delphitools.tools.images.ImageClipper
import com.delphi.delphitools.tools.images.ImageCompressor
import com.delphi.delphitools.tools.images.ImageConverter
import com.delphi.delphitools.tools.images.ImageSplitter
import com.delphi.delphitools.tools.images.MetadataStripper
import com.delphi.delphitools.tools.images.PasteImage
import com.delphi.delphitools.tools.images.PlaceholderGenerator
import com.delphi.delphitools.tools.images.SvgOptimiser
import com.delphi.delphitools.tools.pdf.ImageToPdf
import com.delphi.delphitools.tools.pdf.PdfCompressor
import com.delphi.delphitools.tools.pdf.PdfOrganiser
import com.delphi.delphitools.tools.pdf.PdfRotateCrop
import com.delphi.delphitools.tools.print.PaperSizes
import com.delphi.delphitools.tools.socialmedia.MatteGenerator
import com.delphi.delphitools.tools.socialmedia.SocialCropper
import com.delphi.delphitools.tools.socialmedia.Watermarker
import com.delphi.delphitools.tools.turbonerd.BrailleConverter
import com.delphi.delphitools.tools.turbonerd.IPATranscriber
import com.delphi.delphitools.tools.turbonerd.MorseCode
import com.delphi.delphitools.tools.turbonerd.NATOPhonetic
import com.delphi.delphitools.tools.turbonerd.ShavianTransliterator
import com.delphi.delphitools.tools.typography.DocConverter
import com.delphi.delphitools.tools.typography.FontExplorer
import com.delphi.delphitools.tools.typography.GlyphBrowser
import com.delphi.delphitools.tools.typography.LineHeightCalc
import com.delphi.delphitools.tools.typography.PxToRem
import com.delphi.delphitools.tools.typography.TypoCalc
import com.delphi.delphitools.tools.typography.WordCounter

/** Every ported tool by id. */
internal val toolPages: Map<String, @Composable () -> Unit> = mapOf(
    "image-compressor" to { ImageCompressor() },
    "timecode-calc" to { TimecodeCalc() },
    "placeholder-genny" to { PlaceholderGenerator() },
    "paste-image" to { PasteImage() },
    "image-splitter" to { ImageSplitter() },
    "px-to-rem" to { PxToRem() },
    "paper-sizes" to { PaperSizes() },
    "word-counter" to { WordCounter() },
    "line-height-calc" to { LineHeightCalc() },
    "colour-atlas" to { ColourAtlas() },
    "harmony-genny" to { HarmonyGenerator() },
    "tailwind-shades" to { TailwindShades() },
    "colorblind-sim" to { ColourBlindnessSimulator() },
    "braille-converter" to { BrailleConverter() },
    "morse-code" to { MorseCode() },
    "nato-phonetic" to { NATOPhonetic() },
    "ipa-transcriber" to { IPATranscriber() },
    "subtitle-converter" to { SubtitleConverter() },
    "uuid-genny" to { UuidGenerator() },
    "jwt-decoder" to { JwtDecoder() },
    "encoder" to { Encoder() },
    "contrast-checker" to { ContrastChecker() },
    "colour-converter" to { ColourConverter() },
    "palette-genny" to { PaletteGenerator() },
    "palette-collection" to { PaletteCollection() },
    "typo-calc" to { TypoCalc() },
    "shavian-transliterator" to { ShavianTransliterator() },
    "glyph-browser" to { GlyphBrowser() },
    "font-explorer" to { FontExplorer() },
    "http-status" to { HttpStatus() },
    "base-converter" to { BaseConverter() },
    "regex-tester" to { RegexTester() },
    "text-diff" to { TextDiff() },
    "password-genny" to { PasswordGenerator() },
    "decoder" to { CipherDecoder() },
    "meta-tag-genny" to { MetaTagGenerator() },
    "cron-builder" to { CronBuilder() },
    "svg-optimiser" to { SvgOptimiser() },
    "metadata-stripper" to { MetadataStripper() },
    "image-converter" to { ImageConverter() },
    "base64-image-encoder" to { Base64ImageEncoder() },
    "image-clipper" to { ImageClipper() },
    "favicon-genny" to { FaviconGenerator() },
    "watermarker" to { Watermarker() },
    "social-cropper" to { SocialCropper() },
    "video-muter" to { VideoMuter() },
    "audio-extractor" to { AudioExtractor() },
    "video-trimmer" to { VideoTrimmer() },
    "frame-extractor" to { FrameExtractor() },
    "pixel-picker" to { PixelPicker() },
    "palette-extractor" to { PaletteExtractor() },
    "gradient-genny" to { GradientGenerator() },
    "matte-generator" to { MatteGenerator() },
    "image-to-pdf" to { ImageToPdf() },
    "pdf-organiser" to { PdfOrganiser() },
    "pdf-rotate-crop" to { PdfRotateCrop() },
    "pdf-compressor" to { PdfCompressor() },
    "algebra-calc" to { AlgebraCalc() },
    "unit-converter" to { UnitConverter() },
    "time-calc" to { TimeCalc() },
    "sci-calc" to { SciCalc() },
    "json-formatter" to { JsonFormatter() },
    "tailwind-cheatsheet" to { TailwindCheatsheet() },
    "code-genny" to { BarcodeGenerator() },
    "qr-genny" to { QrGenerator() },
    "waveform-genny" to { WaveformGenerator() },
    "audio-trimmer" to { AudioTrimmer() },
    "audio-normaliser" to { AudioNormaliser() },
    "video-to-gif" to { VideoToGif() },
    "video-atlas" to { VideoAtlas() },
    "audio-atlas" to { AudioAtlas() },
    "voice-recorder" to { VoiceRecorder() },
    "subtitle-studio" to { SubtitleStudio() },
    "markdown-writer" to { TextScratchpad() },
    "request-builder" to { RequestBuilder() },
    "doc-converter" to { DocConverter() },
    "graph-calc" to { GraphCalc() },
)
