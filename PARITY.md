# Parity

Tools against dt-ios `Shell/ToolViewController.swift`. iOS lines are the Swift files in `Features/` that share the controller's name prefix; shared core code is not counted.

Status: `todo`, `wip`, `review` (ported, opens on device, awaiting Ruby), `done` (matches iOS).

## SocialMedia

| id | tool | iOS controller | iOS lines | status |
|---|---|---|---:|---|
| `matte-generator` | Matte Generator | `MatteGeneratorViewController` | 600 | todo |
| `scroll-generator` | Seamless Scroll Generator | `ScrollGeneratorViewController` | 579 | todo |
| `social-cropper` | Social Media Cropper | `SocialCropperViewController` | 589 | review |
| `watermarker` | Watermarker | `WatermarkerViewController` | 528 | review |

## Colour

| id | tool | iOS controller | iOS lines | status |
|---|---|---|---:|---|
| `colour-atlas` | Colour Atlas | `ColourAtlasViewController` | 318 | done |
| `colorblind-sim` | Colour Blindness Simulator | `ColourBlindnessSimulatorViewController` | 334 | done |
| `colour-camera` | Colour Camera | `ColourCameraViewController` | 949 | todo |
| `colour-converter` | Colour Converter | `ColourConverterViewController` | 734 | review |
| `contrast-checker` | Contrast Checker | `ContrastCheckerViewController` | 708 | review |
| `gradient-genny` | Gradient Generator | `GradientGeneratorViewController` | 1724 | todo |
| `harmony-genny` | Harmony Generator | `HarmonyGeneratorViewController` | 351 | done |
| `palette-collection` | Palette Collection | `PaletteCollectionViewController` | 829 | review |
| `palette-extractor` | Palette Extractor | `PaletteExtractorViewController` | 1010 | todo |
| `palette-genny` | Palette Generator | `PaletteGeneratorViewController` | 719 | review |
| `pixel-picker` | Pixel Picker | `PixelPickerViewController` | 709 | todo |
| `tailwind-shades` | Tailwind Shade Generator | `TailwindShadesViewController` | 383 | done |

## Images

| id | tool | iOS controller | iOS lines | status |
|---|---|---|---:|---|
| `artwork-enhancer` | Artwork Enhancer | `ArtworkEnhancerViewController` | 381 | todo |
| `background-remover` | Background Remover | `BackgroundRemoverViewController` | 348 | todo |
| `base64-image-encoder` | Base64 Image Encoder | `Base` | 991 | review |
| `document-scanner` | Document Scanner | `DocumentScannerViewController` | 654 | todo |
| `favicon-genny` | Favicon Generator | `FaviconGeneratorViewController` | 665 | review |
| `image-atlas` | Image Atlas | `ImageAtlasViewController` | 769 | todo |
| `image-clipper` | Image Clipper | `ImageClipperViewController` | 412 | review |
| `image-compressor` | Image Compressor | `ImageCompressorViewController` | 199 | done |
| `image-converter` | Image Converter | `ImageConverterViewController` | 754 | review |
| `image-deskewer` | Image De-skewer | `ImageDeskewerViewController` | 778 | todo |
| `image-masker` | Image Masker | `ImageMaskerViewController` | 843 | todo |
| `image-splitter` | Image Splitter | `ImageSplitterViewController` | 380 | done |
| `image-stitcher` | Image Stitcher | `ImageStitcherViewController` | 1385 | todo |
| `image-tracer` | Image Tracer | `ImageTracerViewController` | 500 | todo |
| `metadata-stripper` | Metadata Stripper | `MetadataStripperViewController` | 486 | review |
| `paste-image` | Paste Image | `PasteImageViewController` | 325 | done |
| `placeholder-genny` | Placeholder Generator | `PlaceholderGeneratorViewController` | 382 | done |
| `svg-optimiser` | SVG Optimiser | `SVGOptimiserViewController` | 147 | review |

## AudioVideo

| id | tool | iOS controller | iOS lines | status |
|---|---|---|---:|---|
| `audio-atlas` | Audio Atlas | `AudioAtlasViewController` | 667 | todo |
| `audio-extractor` | Audio Extractor | `AudioExtractorViewController` | 156 | todo |
| `audio-normaliser` | Audio Normaliser | `AudioNormaliserViewController` | 244 | todo |
| `audio-trimmer` | Audio Trimmer | `AudioTrimmerViewController` | 362 | todo |
| `auto-subtitle` | Auto Subtitle | `AutoSubtitleViewController` | 644 | todo |
| `frame-extractor` | Frame Extractor | `FrameExtractorViewController` | 401 | todo |
| `subtitle-converter` | Subtitle Converter | `SubtitleConverterViewController` | 191 | done |
| `subtitle-studio` | Subtitle Studio | `SubtitleStudioViewController` | 758 | todo |
| `timecode-calc` | Timecode Calculator | `TimecodeCalcViewController` | 253 | done |
| `video-atlas` | Video Atlas | `VideoAtlasViewController` | 280 | todo |
| `video-muter` | Video Muter | `VideoMuterViewController` | 210 | todo |
| `video-to-gif` | Video to GIF | `VideoToGIFViewController` | 321 | todo |
| `video-trimmer` | Video Trimmer | `VideoTrimmerViewController` | 257 | todo |
| `voice-recorder` | Voice Recorder | `VoiceRecorderViewController` | 570 | todo |
| `waveform-genny` | Waveform Generator | `WaveformGeneratorViewController` | 263 | todo |

## Typography

| id | tool | iOS controller | iOS lines | status |
|---|---|---|---:|---|
| `doc-converter` | Document Converter | `DocConverterViewController` | 1136 | todo |
| `font-explorer` | Font File Explorer | `FontExplorerViewController` | 364 | review |
| `font-installer` | Font Installer | `FontInstallerViewController` | 721 | todo |
| `glyph-browser` | Glyph Browser | `GlyphBrowserViewController` | 389 | review |
| `large-type` | Large Type | `LargeTypeViewController` | 921 | todo |
| `line-height-calc` | Line Height Calculator | `LineHeightCalcViewController` | 447 | done |
| `px-to-rem` | PX to REM | `PxToRemViewController` | 318 | done |
| `text-editor` | Text Editor | `TextEditorViewController` | 604 | todo |
| `typo-calc` | Typography Calculator | `TypoCalcViewController` | 527 | review |
| `word-counter` | Word Counter | `WordCounterViewController` | 398 | done |

## Pdf

| id | tool | iOS controller | iOS lines | status |
|---|---|---|---:|---|
| `image-to-pdf` | Images to PDF | `ImageToPDFViewController` | 468 | todo |
| `pdf-compressor` | PDF Compressor | `PDFCompressorViewController` | 180 | todo |
| `pdf-organiser` | PDF Organiser | `PDFOrganiserViewController` | 391 | todo |
| `pdf-page-numberer` | PDF Page Numbers | `PDFPageNumbererViewController` | 620 | todo |
| `pdf-preflight` | PDF Preflight | `PDFPreflightViewController` | 402 | todo |
| `pdf-rotate-crop` | PDF Rotate & Crop | `PDFRotateCropViewController` | 487 | todo |

## Print

| id | tool | iOS controller | iOS lines | status |
|---|---|---|---:|---|
| `paper-sizes` | Paper Sizes | `PaperSizesViewController` | 354 | done |
| `imposer` | Print Imposer | `ImposerViewController` | 567 | todo |
| `zine-imposer` | Zine Imposer | `ZineImposerViewController` | 938 | todo |

## DevTools

| id | tool | iOS controller | iOS lines | status |
|---|---|---|---:|---|
| `code-genny` | Barcode Generator | `BarcodeGeneratorViewController` | 515 | todo |
| `base-converter` | Base Converter | `BaseConverterViewController` | 644 | review |
| `decoder` | Cipher Decoder | `CipherDecoderViewController` | 698 | review |
| `cron-builder` | Cron Builder | `CronBuilderViewController` | 862 | review |
| `encoder` | Encoding Tools | `EncoderViewController` | 435 | done |
| `http-status` | HTTP Status | `HTTPStatusViewController` | 520 | review |
| `json-formatter` | JSON Formatter | `JSONFormatterViewController` | 1000 | todo |
| `jwt-decoder` | JWT Decoder | `JWTDecoderViewController` | 402 | done |
| `meta-tag-genny` | Meta Tag Generator | `MetaTagGeneratorViewController` | 706 | review |
| `password-genny` | Password Generator | `PasswordGeneratorViewController` | 652 | review |
| `qr-genny` | QR Generator | `QRGeneratorViewController` | 996 | todo |
| `regex-tester` | Regex Tester | `RegexTesterViewController` | 517 | review |
| `request-builder` | Request Builder | `RequestBuilderViewController` | 626 | todo |
| `tailwind-cheatsheet` | Tailwind Cheat Sheet | `TailwindCheatsheetViewController` | 723 | todo |
| `text-diff` | Text Diff | `TextDiffViewController` | 607 | review |
| `markdown-writer` | Text Scratchpad | `TextScratchpadViewController` | 549 | todo |
| `uuid-genny` | UUID Generator | `UUIDGeneratorViewController` | 475 | done |

## Calculators

| id | tool | iOS controller | iOS lines | status |
|---|---|---|---:|---|
| `algebra-calc` | Algebra Calculator | `AlgebraCalcViewController` | 495 | todo |
| `graph-calc` | Graph Calculator | `GraphCalcViewController` | 2073 | todo |
| `sci-calc` | Scientific Calculator | `ScientificCalculatorViewController` | 1548 | todo |
| `time-calc` | Time Calculator | `TimeCalcViewController` | 1465 | todo |
| `unit-converter` | Unit Converter | `UnitConverterViewController` | 1342 | todo |

## TurboNerd

| id | tool | iOS controller | iOS lines | status |
|---|---|---|---:|---|
| `braille-converter` | Braille Converter | `BrailleConverterViewController` | 223 | done |
| `ipa-transcriber` | IPA Transcription | `IPATranscriberViewController` | 249 | done |
| `morse-code` | Morse Code | `MorseCodeViewController` | 278 | done |
| `nato-phonetic` | NATO Phonetic | `NATOPhoneticViewController` | 333 | done |
| `nfc-reader-writer` | NFC Reader/Writer | `NFCReaderWriterViewController` | 595 | todo |
| `shavian-transliterator` | Shavian Transliterator | `ShavianTransliteratorViewController` | 545 | review |

## Extensions

| iOS | Android | status |
|---|---|---|
| Shortcuts intents | none, out of scope | - |
| App shortcuts, QS tiles | none, out of scope | - |
| Share extension | share target (`ACTION_SEND` intent filter) | todo |
| Widgets | Glance widgets, a subset to pick | todo |
| Workflows | tool chains, port of `Features/Workflows` (1,152 iOS lines) | todo |
