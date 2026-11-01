# Media stack (research for the native rewrite)

Topic: `media`. Written 2026-11-01. Sources were read on 2026-11-01. The full list with URLs is at the end.

The topic covers images, video and audio playback, camera, QR, PDF, file picking/saving/sharing, speech (ASR + TTS), audio recording and audio focus. Targets are Android first, with iOS and Linux desktop to follow in Kotlin Multiplatform.

Labels used in this file (same scheme as `kmp-cmp-status.md`):

- **[V]**: verified on 2026-11-01 from a primary source. That means official docs or release notes, Maven Central or Google Maven metadata, a GitHub release page, or the Gradle module/POM of the published artifact.
- **[I]**: inspected by hand on 2026-11-01. I downloaded the published artifact and read its manifest, `.so` files, ELF headers, POM or classes. Scratch copies are in the session scratchpad and are not committed.
- **[S]**: secondary source, such as a vendor benchmark, a blog post or a forum thread.
- **[U]**: unverified, or my projection. Check it in slice 0 or on the device before relying on it.

---

## 0. Bottom line

| Need | Pick for Android (day 1) | Version | KMP path (iOS / Linux desktop later) |
|---|---|---|---|
| Images (chat, files, attachments) | **Coil 3** (`coil-compose`, `coil-svg`, `coil-gif`) | 3.6.3 [V] | Coil core/compose/svg are KMP. GIF animation is Android-only [V] |
| Zoomable image viewer | **Telephoto `zoomable`** (KMP modifier) + Coil | 0.19.0 [V] | `zoomable` publishes iOS/desktop/web [I]. `zoomable-image-coil3` (sub-sampling) is Android-only [I] |
| Photo picking | System **Photo Picker** (`PickVisualMedia`/`PickMultipleVisualMedia`) via FileKit or directly | activity 1.13.0 [V] | FileKit maps to PHPicker on iOS and a filtered dialog on desktop [V] |
| File open/save/share | **FileKit** 0.17.0 (`filekit-dialogs-compose`, `filekit-coil`) over SAF | 0.17.0 [V] | Same API on iOS and JVM desktop. Share is Android/iOS only [V] |
| Camera photo for a prompt | `ActivityResultContracts.TakePicture` + FileProvider (system camera app) | — | FileKit camera picker on iOS |
| QR scan (pairing) | **CameraX `CameraXViewfinder` + zxing-cpp** (`io.github.zxing-cpp:android`) | CameraX 1.6.2 (1.7.0 when stable), zxing-cpp 3.1.1 [V] | iOS: AVFoundation metadata output (built in). Desktop: decode a picked image with zxing core 3.5.4 |
| QR draw (session handoff) | **qrose** (Compose `Painter`, pure Kotlin) | 1.3.1 [V] | Same code everywhere |
| PDF preview | **Port the existing isolated-process `PdfRenderer` service** (`LocalPdfService.kt`, 102 lines). Evaluate `androidx.pdf` later | platform API. androidx.pdf 1.0.0-beta01 [V] | iOS PDFKit. JVM Apache PDFBox 3.0.8 [V] |
| Video/audio files | **None on day 1.** Add Media3 when a feature needs it | Media3 1.11.1 stable [V] | ComposeMediaPlayer 0.11.4 (Media3 / AVPlayer / GStreamer) if KMP video is ever needed [V] |
| Speech-to-text | **sherpa-onnx Kotlin API**, Whisper (same model files) | 2.0.1 [V] | JVM jar + Linux native jars exist [V]. iOS uses the C API/xcframework [U] |
| Natural reading voice | **sherpa-onnx Kokoro** (lexicon, int8), `generateWithCallback` → `AudioTrack` | 2.0.1 [V] | Same as ASR |
| System reading voice | `android.speech.tts.TextToSpeech` (port `ReadAloudBridge.kt`) | platform | iOS AVSpeechSynthesizer. Linux speech-dispatcher [U] |
| Microphone | `AudioRecord` 16 kHz mono PCM16, `setPrivacySensitive(true)` | platform | iOS AVAudioEngine. JVM `TargetDataLine` [U] |
| Audio focus | `AudioFocusRequest` (`GAIN_TRANSIENT`, speech attributes). Read aloud stays foreground-only | platform | No-op elsewhere |

Five decisions matter most:

1. **Drop ML Kit, use zxing-cpp.**
   - It is open source (Apache-2.0) and has no Google telemetry/ToS. It is F-Droid-compatible.
   - Its arm64 `.so` is about 0.73 MB compressed [I], against about 2.4 MB for bundled ML Kit [V].
   - It is 16 KB-page aligned [I], and it also publishes Kotlin/Native artifacts for iOS and Linux [V].
2. **Keep the speech runtime in the APK.**
   - Do not download `.so` files at runtime. Android 10+ W^X rules and store policies make downloaded native code a dead end [V].
   - For arm64-only, sherpa-onnx costs about **9.8 MB compressed** [I].
3. **sherpa-onnx's AAR has no consumer R8 rules [I].** Ship `-keep class com.k2fsa.sherpa.onnx.** { *; }`, or release builds break JNI field lookups. The Flutter plugin used FFI, so this risk is new.
4. **Android 17 silences background audio for all apps [V].** Read aloud and any "reply finished" sound stay foreground-only (parity with today). Background reading would need a `mediaPlayback` foreground service + `MediaSessionService`, which is an owner decision.
5. **Voice models stay where they are.** Flutter's `getApplicationSupportDirectory()` is Android `filesDir` [U, verify on device], so `filesDir/voice_models/*` can be read in place with no re-download.

---

## 1. What the Flutter app does today (inventory)

Read-only source: `/home/eslam/Storage/Code/wt/oc_app-batch1`.

| Area | Flutter today | Native bridge already in Kotlin | Notes for the port |
|---|---|---|---|
| Photo attachments | `image_picker` 1.2.3. Gallery (multi) + camera. `maxEdge` 2048, JPEG quality 85, 10 MB cap. Recovery key `oc.pendingPromptPhoto`, dir `filesDir/prompt-photo-recovery-v1` (`lib/state/prompt_photos.dart`) | — | Must survive process death while the camera is open: the request is recorded before launch (catalog: `test/prompt_photos_test.dart:188`) |
| Prompt MIME rules | png/jpeg/gif/webp images, text-like MIME list (`lib/domain/prompt_attachment.dart`) | — | Pure logic → `commonMain` |
| File preview | text/markdown/code, images (png/jpeg/gif/webp/bmp), SVG, PDF, office (xlsx→CSV, docx→paragraphs), binary (`lib/ui/widgets/file_preview.dart`, `lib/domain/office_text.dart`) | — | The office parser is its own ZIP central-directory reader + inflate + XML (269 lines) → port to Kotlin with `java.util.zip.Inflater` (Android/JVM) |
| SVG | `flutter_svg` 2.3.0 behind an allowlist sanitizer `StaticSvg` (no network, no scripts) (`lib/domain/static_svg.dart`, 259 lines) | — | Port the sanitizer to `commonMain`, then render with `coil-svg` |
| PDF | `LocalPdf` channel: ≤10 MB, ≤200 pages, ≤1536 px edge, ≤2 MP, PNG header check (`lib/platform/local_pdf.dart`) | `LocalPdfService.kt` (isolated process, two FDs, 10 s watchdog, kills itself on unbind) | Keep as is. Return `Bitmap`/`HardwareBuffer` instead of PNG bytes if the IPC allows |
| QR scan | `mobile_scanner` 7.4.2 = CameraX 1.6.1 + **bundled ML Kit 17.3.0** (`lib/ui/kit/kit_scanner.dart`, `THIRD_PARTY_NOTICES.md:162`) | `oc/camera` permission channel in `MainActivityMedia.kt` | Replace ML Kit with zxing-cpp |
| QR draw | `qr` 3.0.2 + own `CustomPainter` | — | qrose |
| File save/open | `file_picker` 12.2.0: `saveFile(bytes)`, `pickFiles(json)` | `ProjectExportBridge.kt` | FileKit / SAF |
| Wi-Fi file share | Dart `HttpServer` on the Wi-Fi address, 192-bit token path, GET-only, 404 elsewhere (`lib/platform/wifi_file_share.dart`) | — | Ktor server (CIO) or a plain `ServerSocket` in `androidMain`/`jvmMain` |
| Dictation | `record` 7.1.1 PCM16 16 kHz mono, `AndroidAudioSource.mic`, `manageBluetooth: false` (`lib/voice/audio.dart`). RMS silence detector 1.5 s (`lib/voice/silence.dart`). 30 s Whisper chunks | `oc/voice` (permissions, device info incl. `totalMemoryMb`) | Same parameters |
| ASR | `sherpa_onnx` 2.0.1 (Dart FFI) Whisper tiny/base/small int8 from `csukuangfj/sherpa-onnx-whisper-*`. RAM gate by **total RAM** (memory `voice-model-memory-gate.md`) | — | sherpa-onnx Kotlin API |
| Natural voice | Kokoro v1.0 int8 multi-lang, voice `af_heart` (sid 3), lexicon `lexicon-us-en.txt`. Arabic not offered (espeak-ng removed in sherpa 2.0) (`lib/voice/natural_voice.dart`) | `ReadAloudBridge.kt` plays PCM16 via `AudioTrack` | Synthesize in Kotlin, play `PCM_FLOAT` directly |
| System voice | — | `ReadAloudBridge.kt` (477 lines): offline voices only, focus `GAIN_TRANSIENT`, `USAGE_MEDIA`/`CONTENT_TYPE_SPEECH`, cancels on route loss, on any other recording, and on background | Port almost verbatim |
| Video/audio playback | **none** | — | No day-1 need |

The logic-test catalog (`/home/eslam/Storage/Code/wt/native-plan/catalog`) has **35 `media-voice` behaviours**. Examples:

- PCM16 split-chunk normalisation
- the silence/auto-send rules
- checksum-verified resumable downloads
- the RAM gate
- language folding
- recognizer config mapping
- PDF transport budgets
- photo picker room/recovery
- Wi-Fi share

Other areas hold more (attachments: 27 hits, scanner: 6). These are the native test-first list for this module (§11).

---

## 2. Images: Coil 3

### Facts

- **Coil 3.6.3** (2026-09-18) is the latest [V]. It fixes AGP 9.4 + R8 module-name metadata.
- 3.6.0 (2026-08-26) moved Coil to Kotlin 2.4.10, Compose 1.12.0 and Android compile SDK 37 [V]. It also added `allowPartialImage`.
- 3.5.0 (2026-06-10) raised Coil's minSdk to 23 and removed `iosX64`/`macosX64` [V].
- KMP targets in the published module include android, jvm, iosArm64, iosSimulatorArm64, macosArm64, linuxX64, linuxArm64, js and wasmJs [I].
- `coil-svg`:
  - On Android it depends on **`com.caverock:androidsvg-aar:1.4`** [I].
  - On non-Android it uses **Skia (skiko 0.150.1)** [I].
  - It detects SVG by looking for `<svg` in the first 1 KB [V].
- `coil-gif` is **Android-only** [V]:
  - `AnimatedImageDecoder` (API 28+, also handles animated WebP/HEIF) is the one to use.
  - `GifDecoder` is the slow fallback.
- Network:
  - `coil-network-okhttp` (Android/JVM) and `coil-network-ktor3` (all targets) exist at 3.6.3 [V].
  - **Do not let Coil fetch server URLs.** See the recommendation below.

### Recommendations for this app

1. **One `ImageLoader` for the app**, built in `commonMain`, with a custom `Fetcher` that asks the domain gateway for bytes. The reasons:
   - Server files need per-profile auth (OC2 Basic auth). Credentials must never enter a shared HTTP cache or Coil's disk cache (security invariant: per-profile storage and the deletion sweep).
   - The gateway already enforces size caps (10 MB) and MIME checks.
   - Key cache entries as `profileId + path + etag/mtime`. **Disable Coil's disk cache.** Alternatively, put it under a per-profile directory that `deleteProfileAndLocalData` removes. Memory cache only is enough for a chat client.
2. **Reserve space before decode.** The attachment model should carry width/height when known, so `AsyncImage` gets a fixed aspect ratio. Streaming replies must not jump (smooth-streaming rule).
3. **Motion:** `crossfade(200)` (inside the 150–250 ms rule). Use `crossfade(false)` when the system animator scale is 0 (reduced motion).
4. **SVG:** run the ported `StaticSvg` allowlist first. Pass only the sanitised text to `SvgDecoder`. AndroidSVG 1.4 does not run scripts, but it can resolve external refs if a resolver is set. Never set one.
5. **Hardware bitmaps** are Coil's Android default and are good for scrolling. Set `allowHardware(false)` only where pixels are read, e.g. "copy image".
6. **Zoom viewer:** use Telephoto `Modifier.zoomable()`, which is KMP [I], around `AsyncImage`.
   - Telephoto's sub-sampling `ZoomableAsyncImage` (`zoomable-image-coil3`) is **Android-only** [I]. Use it in `androidMain` only if very large images (photos > 12 MP, big screenshots) stutter.
   - The KMP alternative with sub-sampling is `io.github.panpf.zoomimage:zoomimage-compose-coil3` 1.7.0 [V].
   - Telephoto's `flick` module (0.19.0) gives drag-to-dismiss [V].
   - **Predictive back:** the demo's lesson applies. Let the viewer's dismiss be driven by `PredictiveBackHandler` progress, *or* by the flick drag, never both at once. Do not run a shared-element transform while the back gesture is active.
7. **Kit-only:** expose this as `KitImage`, `KitZoomableImage` and `KitSvgImage` in the kit. Screens never call `AsyncImage` directly.

### Code sketch (commonMain + androidMain)

```kotlin
// commonMain
fun appImageLoader(ctx: PlatformContext, files: ServerFileSource, reducedMotion: Boolean) =
    ImageLoader.Builder(ctx)
        .components {
            add(ServerFileFetcher.Factory(files))   // bytes come from the gateway (auth, caps)
            add(SvgDecoder.Factory())               // only fed sanitised SVG text
            addPlatformDecoders()                   // expect fun: AnimatedImageDecoder on Android 28+
        }
        .diskCache(null)                            // no shared cache for server content
        .crossfade(if (reducedMotion) 0 else 200)
        .build()

// androidMain
actual fun ComponentRegistry.Builder.addPlatformDecoders() {
    if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
}
```

---

## 3. Photo picking, camera capture, file open/save/share

### Facts

- **Photo Picker** (docs updated 2026-10-28) [V]:
  - Contracts: `PickVisualMedia` and `PickMultipleVisualMedia(n)`, with AndroidX Activity ≥ 1.7.
  - Availability: native on Android 11+ via Google System Updates. Backported to API 19–29 through Play services, which needs a `ModuleDependencies` manifest entry.
  - Without either, it falls back to `ACTION_OPEN_DOCUMENT`.
  - The limit comes from `MediaStore.getPickImagesMaxLimit()`.
  - Grants last until restart unless `takePersistableUriPermission`. The cap is 5,000 grants.
  - HDR→SDR transcoding is opt-in on activity ≥ 1.11.0-alpha01.
  - No storage permission is needed.
- **Embedded photo picker:**
  - Needs Android 14 + SDK Extensions 15 [V].
  - It ships as `androidx.photopicker:photopicker-compose`. Google Maven has 1.0.0-alpha02 [V]; the docs page still shows alpha01.
  - It is a `SurfaceView` hosting system UI.
- **FileKit 0.17.0** (2026-10-28, MIT) [V]:
  - Android minSdk 24. Built with Kotlin 2.4.21 and CMP 1.12.2.
  - Image/video types open the gallery picker (Photo Picker on Android). Files go through SAF. `Multiple(maxItems)` is 1–50.
  - `openFileSaver` handles saving, and `filekit-coil` loads a `PlatformFile` into Coil.
  - `shareFile` is **Android/iOS only**. It needs no FileProvider for `filesDir`/`cacheDir` and `content://` files, and needs your own provider elsewhere.
  - Linux desktop dialogs use the XDG Desktop Portal (0.16.0). This needs a portal file chooser and `libdbus-1` at runtime [V].
- **`ACTION_IMAGE_CAPTURE` + a declared `CAMERA` permission:**
  - If the app *declares* CAMERA (it does, for QR) and the user has not granted it, launching the capture intent throws `SecurityException` [S: MediaStore docs as quoted by several sources].
  - Flutter's `image_picker` hid this by requesting the permission first [U].
  - **The native port must request CAMERA before `TakePicture`.**
- Location metadata:
  - The Photo Picker redacts location by default [S: Google issue tracker, secondhand news of an Aug-2026 opt-in prompt].
  - Re-encoding through `Bitmap.compress` drops all EXIF anyway.

### Recommendations

1. **Attachments (parity):**
   - Use FileKit's gallery launcher (`FileKitType.Image`, `Multiple(roomLeft)`), or the raw `PickMultipleVisualMedia(roomLeft)`. When only one slot is left, use the single picker, as today.
   - Decode with `ImageDecoder` and `setTargetSize` so the long edge is ≤ 2048. Do it off the main thread.
   - Re-encode as JPEG 85, except PNG/WebP/GIF that are already small and within the cap.
   - Enforce the 10 MB cap after encode. Write to `filesDir/prompt-photo-recovery-v1` (same dir and key as today, so a pending photo survives the switch).
2. **Camera photo:**
   - Request CAMERA, then `TakePicture` into a FileProvider URI under `cacheDir/camera/`. Persist the pending request **before** launch.
   - Register the launcher unconditionally so the result is re-delivered after process death. This matches the catalog behaviour `prompt_photos_test.dart:188`.
3. **Save/export:** use FileKit `openFileSaver` (SAF `ACTION_CREATE_DOCUMENT`).
   - Stream large exports with `contentResolver.openOutputStream(uri, "wt")`. Use "wt", not "w": some providers do not truncate on "w" [U, add a test that overwrites a longer file].
4. **Open/import:** `OpenDocument(arrayOf("application/json"))` for session import. FileKit `File(extensions=["json"])` maps extensions to MIME types [V].
5. **Share (new, small):** "Share" next to "Save" on file/image viewers (obvious options as visible rows).
   - Use FileKit `shareFile` or `Intent.ACTION_SEND` with a FileProvider.
   - Share only from `cacheDir/share/`, and clear that dir on start.
   - Today the app has no FileProvider. Add one with a narrow `file_paths.xml`.
6. **Do not use the embedded picker on day 1.** It is alpha, Android 14+ with an extension only, and a second picker UI to keep consistent. Revisit when it is stable: it removes a screen hop in the composer.

---

## 4. QR: scanning and drawing

### Decoder options (Android)

| Option | Size (arm64) | Licence / F-Droid | Speed | KMP |
|---|---|---|---|---|
| **zxing-cpp `io.github.zxing-cpp:android` 3.1.1** (2026-07-29) [V] | `.so` 1.75 MB raw, **0.73 MB deflated** [I]. AAR 2.9 MB for 4 ABIs [I] | Apache-2.0 [I]. F-Droid apps such as Binary Eye use zxing-cpp [S] | v3.1.0: "considerable performance improvements, 10–40 % on ARM" [V]. An F-Droid dev reports it is much faster than Java ZXing, especially on dense/inverted codes [S] | Kotlin/Native artifacts for iosArm64, iosSimulatorArm64, linuxX64/Arm64, macOS [V] |
| ML Kit bundled `com.google.mlkit:barcode-scanning` 17.3.0 (today) [V] | ~2.4 MB [V] | Proprietary terms. Flagged NonFreeDep/Tracking by IzzyOnDroid [S] | No public Android benchmark found. A vendor iOS test has zxing-cpp finding 346 codes vs ML Kit 120 on 83 images [S, vendor] | Android only |
| ML Kit unbundled 18.3.1 [V] | ~200 KB [V] | Needs Play services | — | Android only |
| Java ZXing core 3.5.4 (2025-11-11) [V] | ~0.5 MB jar [U] | Apache-2.0 | Slowest | JVM/Android only |

zxing-cpp details [I]:

- ELF `p_align` is `0x4000` (16 KB pages OK) on all ABIs.
- minSdk 21.
- Its consumer R8 rule is `-keep class zxingcpp.** { *; }`.
- It depends on `androidx.camera:camera-core` ≥ 1.5.2.
- API: `BarcodeReader(Options(formats = setOf(Format.QR_CODE), tryInvert, tryRotate, tryHarder, tryDownscale, tryDenoise, binarizer …))`.
- `read(image: ImageProxy)` reads only the Y plane, honours `cropRect` and `rotationDegrees`, and accepts `YUV_420_888`.
- `read(bitmap, cropRect, rotation)` decodes from a picture.
- There is **no encoder** in the Android wrapper.

### Camera

- **CameraX 1.6.2** stable (2026-08-26), **1.7.0-rc01** (2026-10-21) [V].
- `CameraXViewfinder` (artifact `androidx.camera:camera-compose`):
  - It renders a `SurfaceRequest`.
  - **Built-in pinch-to-zoom, tap-to-focus, screen flash and stream state arrived in 1.7.0-alpha02** [V]. They are not in 1.6.x.
- The 1.7 Compose libraries need compileSdk 37 (AGP ≥ 9.2) [V]. The RC note says "37.1" [V], which may mean a minor SDK level. Check in slice 0 [U].
- Default minSdk is 23 since 1.5.0 [V].

### Drawing (session handoff QR)

**qrose 1.3.1** (2026-10-05, MIT) [V] is pure Kotlin.

- Painters: `rememberQrCodePainter`.
- Encoders without Compose: `qrose-encoder-*`.
- Raster export: `toByteArray` (PNG/JPEG/WEBP).
- Targets: Android, iOS, JVM, web.

It replaces the Dart `qr` package + custom painter in one step and works in `commonMain`.

### Recommendations

1. **Android scanner:**
   - Use `CameraXViewfinder` + `ImageAnalysis` (`STRATEGY_KEEP_ONLY_LATEST`, ~1280×720, YUV) + zxing-cpp with `formats = {QR_CODE}`, `tryInvert = true` (dark-mode terminals print inverted codes) and `tryHarder = false`.
   - Decode on a single-thread executor.
   - Crop the analysis to the viewfinder window.
   - On the first valid result: unbind, play `HapticFeedbackType.Confirm`, show the result. Do not keep scanning.
   - Start on 1.6.2. Move to 1.7.0 stable for built-in tap-to-focus/pinch instead of hand-written gestures [U: 1.7.0 stable date unknown].
2. **"Scan from a picture" row next to the camera** (obvious options as visible rows).
   - Use `read(bitmap)` on a Photo Picker image.
   - This covers pairing when the QR is a screenshot or on the same phone, e.g. `opencode2 pair` run in the built-in Linux.
   - It also covers devices with no camera, and it is the desktop path.
3. **Remove ML Kit** and its notice section in `THIRD_PARTY_NOTICES.md`. Add zxing-cpp (Apache-2.0).
4. **Measure, don't assume:** use zxing-cpp's `lastReadTime` (ms per read). Acceptance: p95 < 30 ms per 720p frame on NX721J, and first lock < 1 s on the pairing QR [U, targets are mine].
5. **iOS later:** `AVCaptureMetadataOutput` (QR built in), no library. **Desktop:** "pick an image" + zxing core (JVM), or zxing-cpp Kotlin/Native if desktop ever goes native.

### Code sketch (androidMain)

```kotlin
val reader = BarcodeReader(BarcodeReader.Options(
    formats = setOf(BarcodeReader.Format.QR_CODE), tryInvert = true))
val analysis = ImageAnalysis.Builder()
    .setResolutionSelector(ResolutionSelector.Builder()
        .setResolutionStrategy(ResolutionStrategy(Size(1280, 720),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build()
analysis.setAnalyzer(decodeExecutor) { image ->
    image.use { proxy ->
        proxy.setCropRect(viewfinderWindowIn(proxy))      // only decode what the user aims at
        reader.read(proxy).firstOrNull { it.text != null }?.let { onFound(it.text!!) }
    }
}
// Preview: Preview.Builder().build().also { it.setSurfaceProvider { req -> surfaceRequests.value = req } }
// UI: surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
```

---

## 5. PDF

### Facts

- Today: a **non-exported `isolatedProcess` service** renders one page per bind from two FDs. Limits are ≤10 MB in, ≤200 pages, ≤1536 px, ≤2 MP. It has a 10 s watchdog and kills itself on unbind. Errors map to `encrypted` or `invalid_pdf`. It has good security properties for untrusted server files.
- **androidx.pdf 1.0.0-beta01** (2026-08-26) [V]:
  - Artifacts: `pdf-viewer`, `pdf-viewer-fragment`, `pdf-compose` (Compose `PdfViewer`/`PdfViewerState`, `@ExperimentalPdfApi`), `pdf-document-service`, `pdf-core`, `pdf-ink`, `pdf-ocr-play-services` [V/I].
  - Features: search, text selection, links, password dialog, forms, annotations (ink).
  - **minSdk 28** (backport since alpha18) [V/I].
  - `pdf-document-service` declares `PdfDocumentServiceImpl` as `exported=false`, `isolatedProcess=true` [I]. Rendering goes through platform `PdfRenderer`/`PdfRendererPreV` adapters, with **no bundled pdfium** [I].
  - `pdf-viewer` pulls in **Material Components 1.13.0** (Views) and RecyclerView [I]. That is size and a second design language.
- Platform `PdfRenderer` V-APIs (selection/search) need Android 15, or `PdfRendererPreV` on API 30–34 via SDK extension 13 [V].
- Desktop: **Apache PDFBox 3.0.8** (2026-07-15) [V], Apache-2.0, `PDFRenderer.renderImageWithDPI`. iOS: PDFKit.

### Recommendations

1. **Day 1: port `LocalPdfService.kt` unchanged.**
   - Put it behind a `PdfRasterizer` interface (`suspend fun page(bytes, index): PdfPage`).
   - Show pages in a lazy column with `KitZoomableImage`, and render visible pages only.
   - Keep the limits and the plain-words errors: "This PDF is password-protected" and "This file can't be shown as a PDF; Save keeps it."
   - Optional speed-up: bind once per document instead of once per page [U, measure the spawn cost first].
2. **Evaluate androidx.pdf `PdfViewer` (Compose) when it reaches stable.**
   - It brings search, text selection and links for free. It is already isolated.
   - Costs: Material Views dependency, minSdk 28, and UI it owns (selection menus, fast scroller). It would have to be wrapped as one kit part (`KitPdfViewer`) and themed.
   - Decide with the owner. It is not a parity requirement.
3. **minSdk:** the demo is at 26. androidx.pdf needs 28. If androidx.pdf is ever adopted, raise minSdk to 28. Do not use `overrideLibrary`.

---

## 6. Video and audio playback (Media3)

### Facts

- **Media3 1.11.1** stable (2026-09-10) [V]. **1.12.0-alpha02** (2026-10-26) raises minSdk to 24 [V].
- 1.10 (2026-03-30) added Material3 Compose playback widgets (`media3-ui-compose-material3`), `ProgressSlider` and a `Player` composable [V].
- 1.12.0-alpha01 reshaped those Compose slot APIs. That is a breaking change in an unstable API [V].
- 1.10 changed ExoPlayer to request **delayed audio focus during phone calls** [V].
- Media3 is Android-only. No official KMP target was found [U].
- KMP wrappers:
  - **ComposeMediaPlayer 0.11.4** (2026-08-07, MIT) [V]: Media3 on Android, AVPlayer on iOS/macOS, Media Foundation on Windows, GStreamer on Linux, HTML5 on web. Format gaps per platform are listed in its README.
  - `mediamp` is another option [S].

### Recommendation

- **No Media3 on day 1.** The Flutter app plays no video or audio files, and read aloud uses `AudioTrack`. Adding Media3 costs APK size for no user value.
- **When needed:** a "play this file" viewer for audio/video in Files, or background read aloud with lock-screen controls.
  - Use Media3 1.11.x (`media3-exoplayer`, `media3-ui-compose-material3`) wrapped in a kit part.
  - Let ExoPlayer own audio focus (`setAudioAttributes(attrs, handleAudioFocus = true)`).
  - Use `MediaSessionService` for background playback (Android 17 rule, §9).
  - Pick ComposeMediaPlayer only when iOS/desktop video becomes a goal.
- Until then, unknown media opens with **"Open with another app"** (`ACTION_VIEW` + FileProvider grant) and "Save". These are plain, obvious rows.

---

## 7. Speech: sherpa-onnx on Kotlin

### Facts

- **sherpa-onnx v2.0.1** (2026-10-26) [V]. GitHub release assets [V/I]:
  - `sherpa-onnx-2.0.1.aar`: 49,441,341 B, 4 ABIs, minSdk 21.
  - `sherpa-onnx-jvm-2.0.1.jar` plus native-lib jars for Linux x64/aarch64, macOS and Windows.
  - v2.0.0 (2026-10-23) **removed espeak-ng and piper-phonemize** [V]. Kokoro now uses `lexicon` (or pre-phonemised `phonemeCodepoints`). `dataDir` is "deprecated, ignored" [V, `Tts.kt`].
  - onnxruntime is 1.28.2 since v1.13.8 [V].
- **Distribution:**
  - **Not on Maven Central** (repo1 path 404) [I].
  - Sonatype shows a `com.k2fsa.sherpa.onnx:sherpa-onnx-android` page with no version [V].
  - JitPack lists v2.0.1 with status "Error" [I].
  - The documented way is to build the AAR from the release's `jniLibs` tarball via `android/SherpaOnnxAar` [V]. That README is written for 2.0.1.
- **AAR contents** [I]:
  - Kotlin API classes `com.k2fsa.sherpa.onnx.*` (124 classes, including `OfflineRecognizer`, `OfflineWhisperModelConfig`, `OfflineTts`, `OfflineTtsKokoroModelConfig`, `Vad`, `OfflineSpeechDenoiser`).
  - Native libs per ABI: `libonnxruntime.so`, `libsherpa-onnx-jni.so`, `libsherpa-onnx-c-api.so`, `libsherpa-onnx-cxx-api.so`.
  - `libsherpa-onnx-jni.so` NEEDS only `libonnxruntime.so` plus system libs. **c-api and cxx-api are not needed by Kotlin** → exclude them.
  - arm64 sizes: onnxruntime 22.2 MB raw / **7.9 MB deflated**, jni 4.6 MB / **1.9 MB deflated**.
  - All 16 KB aligned (`p_align 0x4000`).
  - **`proguard.txt` is empty** (no consumer rules).
- **Kotlin API** (`sherpa-onnx/kotlin-api`) [V]:
  - `OfflineRecognizer(assetManager: AssetManager? = null, config)`: pass `null` to load from file paths.
  - Recognizer methods: `createStream()`, `decode(stream)`, `getResult(stream).text`, `release()`.
  - `OfflineStream.acceptWaveform(samples: FloatArray, sampleRate: Int)`.
  - `OfflineWhisperModelConfig(encoder, decoder, language, task = "transcribe", tailPaddings, …)`.
  - `OfflineTts(assetManager = null, config)` with `sampleRate()`, `generate(text, sid, speed)`, `generateWithCallback(text, sid, speed) { samples: FloatArray -> Int }`, `generateWithConfigAndCallback`, `free()`/`release()`.
  - The classes use `finalize()`. Always call `release()` explicitly on the owning thread.
  - These Kotlin files import `android.content.res.AssetManager`, so they are **Android-only**. The JVM uses the separate Java API jar.
- **Alternative seen:** Google MediaPipe `com.google.mediapipe:tasks-audio` 1.2.0 (Google Maven) [V/I]:
  - It has a **Text-to-Speech task running Kokoro as a `.litertlm` bundle (`kokoro_int8_gpu_fp16`)**, GPU path, minSdk 26, arm64, 24 kHz.
  - It has streaming `synthesizeStream()` as `Flow<AudioChunk>` and `openStream()` with `pushText()` for LLM tokens [V].
  - It is interesting because the Gemma assistant already plans LiteRT-LM.
  - It has **no Whisper ASR on that page** [V]. Its status and size are unknown [U].

### Recommendations

1. **Keep sherpa-onnx (parity, one runtime for ASR + TTS, same model files).**
   - Vendor the AAR as a **SHA-256-pinned file** in a single-owner `:speech-native` module, or build it on the VPS from the release tarball with the documented script. Record the hash in the package registry.
   - Pin to exactly 2.0.1, the same version the Flutter app ships today.
2. **Packaging** (`androidApp/build.gradle.kts`):
   ```kotlin
   android {
       defaultConfig { ndk { abiFilters += listOf("arm64-v8a") } }   // + "x86_64" for the emulator build only
       packaging { jniLibs {
           excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
           useLegacyPackaging = true   // already required for proot; also compresses the 27 MB of speech libs to ~9.8 MB
       } }
   }
   ```
   `proguard-rules.pro`:
   ```
   -keep class com.k2fsa.sherpa.onnx.** { *; }   # JNI reads config fields by name; AAR ships no rules
   ```
   Add a **release-build smoke test** that constructs `OfflineRecognizer` and `OfflineTts` from tiny fixture paths, or catches the expected file error. It catches R8 breakage before an APK reaches the owner.
3. **Do not download the engine at runtime.**
   - Android 10+ forbids running code from the writable app home. Google says to load only code embedded in the APK [V].
   - Whether `System.load` from `filesDir` still works is unclear [U]. Either way it is a policy and maintenance trap. The ~9.8 MB stays in the APK.
4. **Threads and process:**
   - Run recognition and synthesis on one dedicated single-thread dispatcher each.
   - Keep `numThreads` (2 on ≥4 cores, else 1) and the RAM gate on `totalMem`, never `memoryClass`.
   - **Consider a separate `:speech` process** (`android:process=":speech"`) for Whisper small. A native OOM or crash then kills only speech, not the live session stream. PCM over a `ParcelFileDescriptor` pipe is cheap. [U, decide after measuring peak RSS of small on NX721J]
5. **ASR flow (parity):**
   - Use `AudioRecord` frames (§8) → `FloatArray` (/32768f) → 30 s chunks (as today).
   - Per chunk: `createStream()`, `acceptWaveform(chunk, 16000)`, `decode`, `getResult().text`, `release()`.
   - Cancel by dropping results whose generation is stale (catalog: stale results never become a draft).
   - Language: `""` for auto, else the Whisper code. Keep `VoiceLanguage` folding in `commonMain`.
6. **TTS flow:** `generateWithCallback`, returning 0 to stop as soon as the user taps Stop, 1 to continue [U: confirm the stop semantics against sherpa's Android TTS demo].
   - Play **`ENCODING_PCM_FLOAT`** directly in a `MODE_STREAM` `AudioTrack`. This drops today's float→PCM16 conversion.
   - Buffer through a small channel so synthesis is not blocked by `WRITE_BLOCKING` writes.
   - Keep the reply-language rule (script → app language) and the sentence splitter in `commonMain`.
7. **Later, not parity:**
   - Silero/TEN **VAD** via sherpa's `Vad` class instead of the RMS silence detector. It is more robust in noise, but its thresholds and tests change.
   - MediaPipe Kokoro-GPU, if sherpa CPU synthesis is too slow on mid-range phones (measure the RTF first).
   - An Arabic natural voice when a lexicon-based model appears (code comment in `natural_voice.dart`).

### Code sketch (androidMain)

```kotlin
val recognizer = OfflineRecognizer(config = OfflineRecognizerConfig(
    modelConfig = OfflineModelConfig(
        whisper = OfflineWhisperModelConfig(encoder = enc, decoder = dec, language = lang, task = "transcribe"),
        tokens = tokens, numThreads = threads, provider = "cpu", modelType = "whisper")))
fun transcribe(chunk: FloatArray): String {
    val s = recognizer.createStream()
    try { s.acceptWaveform(chunk, 16_000); recognizer.decode(s); return recognizer.getResult(s).text }
    finally { s.release() }
}

val tts = OfflineTts(config = OfflineTtsConfig(
    model = OfflineTtsModelConfig(
        kokoro = OfflineTtsKokoroModelConfig(model = m, voices = v, tokens = t, lexicon = lex),
        numThreads = threads, provider = "cpu"),
    maxNumSentences = 1))
tts.generateWithCallback(text, sid = 3, speed = 1f) { samples ->
    if (cancelled.get()) 0 else { pcm.trySend(samples); 1 }
}
```

---

## 8. Audio recording

### Facts

- **Mic sharing rules (Android 10+)** [V, docs updated 2026-10-28]:
  - A visible app beats a background one. A privacy-sensitive source wins. Two ordinary apps never capture together.
  - **The losing app keeps running and gets silence.**
  - Register `AudioManager.AudioRecordingCallback` **before** `startRecording()` and check `AudioRecordingConfiguration.isClientSilenced()`.
  - `AudioRecord.Builder.setPrivacySensitive(true)` (API 30) keeps the capture private even from a privileged Assistant.
- **`VOICE_RECOGNITION` source:** the CDD requires noise reduction and AGC **off** by default [V]. The Flutter app uses `MIC`. Its RMS threshold (0.015) was tuned on MIC levels.

### Recommendations

1. Port the capture parameters exactly: `MIC`, 16 kHz, mono, PCM16, ~100 ms reads, no Bluetooth SCO management. Read on a dedicated thread and emit `Flow<ShortArray>`.
2. Add `setPrivacySensitive(true)` (dictation is private) and an `AudioRecordingCallback` → plain-words notice: "Another app is using the microphone. Recording paused." This replaces silent garbage chunks.
3. Keep the existing coordination: starting the mic first cancels read aloud (`beforeMicrophoneCapture`), and any other app's recording cancels read aloud. Put both in **one single-owner `AudioCoordinator`**.
4. Try `VOICE_RECOGNITION` only as a measured experiment, after the silence detector tests pass with recorded fixtures from NX721J.
5. Recording is foreground-only, as today. A background dictation would need a `microphone` foreground service and while-in-use rules, which is a non-goal.

```kotlin
val rec = AudioRecord.Builder()
    .setAudioSource(MediaRecorder.AudioSource.MIC)
    .setAudioFormat(AudioFormat.Builder().setSampleRate(16_000)
        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
    .setBufferSizeInBytes(maxOf(AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT), 16_000 * 2 / 5))
    .apply { if (Build.VERSION.SDK_INT >= 30) setPrivacySensitive(true) }
    .build()
audioManager.registerAudioRecordingCallback(silencedWatcher, mainHandler)   // before start
rec.startRecording()
```

---

## 9. Audio focus and the Android 15/17 rules

### Facts

- **Android 15**, for apps targeting 35+: `requestAudioFocus` only works for the **top app or a foreground service**. Otherwise it returns `AUDIOFOCUS_REQUEST_FAILED` [V].
- **Android 17 background audio hardening** (page updated 2026-10-28) applies to **all apps on Android 17, whatever their target** [V]:
  - Background `AudioTrack.write`/AAudio/OpenSL playback is **silenced**.
  - Focus requests fail.
  - Volume/ringer calls are ignored.
- Exemptions: a visible activity (including PiP) or a foreground service that is **not `shortService`**. Apps targeting 37 also need while-in-use capability in the background.
- The recommended route is `mediaPlayback` + Media3 `MediaSessionService`.
- Test it with `adb shell cmd audio set-enable-hardening throw` (Android 17 Beta 3+).
- **This affects the demo's lesson.** The finished-reply short foreground service is a `shortService`. It must **not** play sound itself. The notification channel's sound is played by the system and is fine [U: confirm with the `throw` mode on an Android 17 emulator].

### Recommendations

1. **Read aloud (system and natural):**
   - Use `AudioFocusRequest(AUDIOFOCUS_GAIN_TRANSIENT)` with attributes `USAGE_MEDIA` + `CONTENT_TYPE_SPEECH` (parity) and `setWillPauseWhenDucked(true)`. Speech should pause, not be ducked into mush.
   - Abandon focus on stop, finish or error.
2. Keep **foreground-only** read aloud: `onStop` cancels it, as today. That satisfies Android 15 and 17 with no service.
3. If the owner wants background listening ("read this reply while I walk"), that is a separate slice.
   - Use a `mediaPlayback` foreground service + `MediaSession` (lock-screen Stop).
   - Media3 `SimpleBasePlayer` can wrap the TTS stream so controls and focus are standard.
4. Add the `throw` hardening mode to the emulator pass for Android 17 images. Silent failures are otherwise invisible.

---

## 10. KMP module layout (media slice)

`commonMain` gets interfaces plus all pure logic. Each platform gets one `actual` per interface.

| Interface (commonMain) | Android actual | iOS (later) | Linux desktop JVM (later) |
|---|---|---|---|
| `ImageLoading` (Coil `ImageLoader` factory) | Coil + AnimatedImageDecoder | Coil (Skia) | Coil (Skia) |
| `FileDialogs` (open/save/share) | FileKit (SAF, Photo Picker, Sharesheet) | FileKit (UIDocumentPicker, PHPicker) | FileKit (XDG portal), no share |
| `CameraPhoto` | `TakePicture` + FileProvider | FileKit camera picker | — (hide the row) |
| `QrScanner` (composable + `decodeImage(bytes)`) | CameraX + zxing-cpp | AVFoundation metadata output | zxing core on a picked image |
| `QrPainter` | qrose (common) | qrose | qrose |
| `PdfRasterizer` | isolated `PdfRenderer` service | PDFKit | PDFBox 3.0.8 |
| `SpeechToText` | sherpa-onnx Kotlin API | sherpa-onnx C API / xcframework [U] | sherpa-onnx Java API jar [U] |
| `NaturalVoice` | sherpa-onnx Kokoro | same [U] | same [U] |
| `SystemVoice` | `TextToSpeech` | AVSpeechSynthesizer | speech-dispatcher, or hide [U] |
| `MicCapture` (`Flow<ShortArray>`) | `AudioRecord` | AVAudioEngine | `TargetDataLine` [U] |
| `PcmPlayer` | `AudioTrack` PCM_FLOAT | AVAudioEngine | `SourceDataLine` |
| `AudioCoordinator` (focus, mic ↔ voice) | AudioManager | AVAudioSession | no-op |

Pure logic to port first into `commonMain`, test-first from the catalog:

- `StaticSvg` sanitizer
- MIME rules
- office text (needs an `Inflater` expect/actual)
- PCM normalisation and chunking
- `SilenceDetector`
- auto-send word filter
- `VoiceLanguage` folding
- model manifest and RAM gate
- the downloader state machine (checksum, `Content-Range` resume, atomic marker)
- PDF result budgets
- the Wi-Fi share token/path rules

Ownership (AGENTS.md style): `AudioCoordinator` and the `:speech-native` module are **single-owner** units. Both halves of any platform bridge stay with one agent.

---

## 11. Test-first mapping (logic tests now, widget tests later)

From the `media-voice` catalog entries (35) plus the attachment and scanner entries:

- **unit (commonMain):**
  - silence rules (`voice_auto_send_test.dart:34`)
  - auto-send filter (`:77`)
  - PCM16 split-chunk normalisation (`voice_audio_test.dart:7`)
  - language folding (`voice_language_locale_test.dart:18`)
  - RAM gate (`voice_automatic_setup_test.dart:253`, `setup_voice_component_test.dart:52`)
  - recognizer config mapping (`voice_recognizer_config_test.dart:8`)
  - natural voice split/pinning (`natural_voice_test.dart:6`)
  - office text (`office_text_test.dart:14`)
  - strict UTF-8 preview vs exact-byte save (`common_file_preview_test.dart:74`)
  - level meter thresholds
- **contract:**
  - PDF transport budgets (`local_pdf_test.dart:66`): keep the same caps on the Kotlin side
  - model manifest pins (`voice_model_manifest_test.dart:8`): update the expected runtime from Dart FFI to the vendored AAR hash
  - downloader (`voice_downloader_test.dart:171`)
- **integration (JVM + Robolectric or instrumented):**
  - photo picker room/recovery (`prompt_photos_test.dart:115/188`)
  - read-aloud lifecycle (`read_aloud_test.dart:160`)
  - 3-minute dictation in 30 s chunks (`revamp/slice_p10_3_test.dart:264`), using a recorded WAV fixture and `tiny`
  - Wi-Fi share (`wifi_file_share_test.dart:16`)
- **New native-only checks** (no Flutter equivalent):
  1. R8 release smoke: sherpa classes constructible.
  2. APK inspection: `lib/arm64-v8a/` contains no `libsherpa-onnx-c-api.so` and no ML Kit, and every `.so` has 16 KB alignment. Extend the existing `verify_*_apk.py` idea.
  3. TakePicture with CAMERA denied leaves no stuck request and shows plain words.
  4. Android 17 `throw` hardening mode: read aloud in the background fails closed, without a crash.
  5. QR p95 decode time printed from `lastReadTime`.

---

## 12. Size budget (arm64, release)

| Item | Added (compressed) | Source |
|---|---|---|
| Native app baseline (demo) | 2.5 MB | owner's demo |
| sherpa-onnx (onnxruntime + jni only) | ~9.8 MB | [I] deflate measurement |
| zxing-cpp | ~0.73 MB | [I] |
| CameraX core/camera2/lifecycle/compose | unmeasured, est. ~1 MB after R8 | [U] |
| Coil 3 + svg (+ AndroidSVG) + gif | unmeasured, est. ≤1 MB | [U] |
| FileKit | small | [U] |
| Removed vs Flutter: ML Kit (~2.4 MB) | −2.4 MB | [V] |
| Not added on day 1: Media3, androidx.pdf (+ Material Views) | 0 | — |

The total media cost is about 12–14 MB, against the Flutter app's 107 MB. The built-in Linux payload (proot, AI Team engine) is outside this topic.

---

## 13. Owner design rules as they apply to media UI

- **Native feel:** use the system Photo Picker, system camera app, SAF and Sharesheet, which are the real Android surfaces.
  - The viewfinder uses CameraX's own gestures (1.7).
  - Haptic `Confirm` on QR lock. Haptic tick on dictation start/stop.
  - Predictive back closes the viewer, scanner and PDF pages with the system animation.
- **Motion:** 200 ms crossfade on images, 150–250 ms for the scanner result sheet, all off with reduced motion. No pulsing level meters when reduced motion is on.
- **Kit-only:** add `KitImage`, `KitZoomableImage`, `KitSvgImage`, `KitScanner`, `KitQr`, `KitPdfPages` and `KitLevelMeter` to the kit. Media and file viewers are kit parts with hard max widths.
- **Plain words, never raw errors.** Every CameraX, sherpa, PdfRenderer and SAF failure maps to a kind. Kinds already exist: `PromptPhotoFailure`, `KitScannerFailure`, `encrypted`/`invalid_pdf`, voice install kinds. Technical text goes under Details.
- **Obvious options as rows:** Scan with camera / Scan from a picture; Save / Share / Open with another app; Take photo / Choose photos.
- **Actions name their target:** "Save PDF", "Share screenshot.png", "Stop reading".
- **RTL:** mirror layout but **not** playback/seek glyphs or the QR. Spoken-language choice is separate from layout direction.
- **Teach hidden gestures:** pinch/double-tap zoom in the viewer and tap-to-focus in the scanner get a one-time tip.

---

## 14. Unknowns and risks

1. **sherpa-onnx distribution.** There is no Maven Central artifact. The JitPack build shows "Error" [I]. Plan: vendor the AAR, hash-pinned, or build it on the VPS. Who owns the bumps, and how long does a rebuild take on the VPS?
2. **R8 + sherpa JNI.** The keep rule is needed. Verify with a minified release build and a JNI call before any APK goes to the owner.
3. **CameraX 1.7.0 stable date** and the "compileSdk 37.1" wording (minor SDK level?) [U].
4. **FileKit Android save/write mode** ("w" vs "wt") and its behaviour with Google Drive/OneDrive providers [U]. Test overwrite-truncation.
5. **`ACTION_IMAGE_CAPTURE` + declared CAMERA:** the exact current wording is not read from the official page [S]. Implement the permission request regardless.
6. **Photo Picker location opt-in** (Aug 2026 system update) [S]. Re-encoding drops EXIF anyway.
7. **Android 17 hardening vs notification sounds** played while a `shortService` is running [U]. Test in `throw` mode.
8. **Flutter `getApplicationSupportDirectory()` = `filesDir`** [U]. Confirm on the device before relying on in-place model reuse. If it differs, move the files once in the first-launch migration. Never re-download 500 MB on mobile data without consent.
9. **Whisper small peak RSS** on NX721J, and whether a `:speech` process is worth the complexity [U].
10. **Kokoro synthesis speed on CPU** (RTF) on mid-range phones. The only public Android number is an emulator RTF of 0.58 with plain ONNX Runtime [S]. Measure before considering MediaPipe GPU Kokoro.
11. **iOS sherpa packaging** (xcframework + cinterop) and **Linux JVM** sherpa jar wiring are untested here [U].
12. **androidx.pdf** stability and the kit-only conflict. Its own selection menus and scroller must be themed or wrapped, which is an owner decision.
13. **Downloaded native libs** (`System.load` from `filesDir`): whether this still works on Android 16/17 for targetSdk 36+ is unknown [U]. Not planned.

---

## Sources (read 2026-11-01)

- Coil changelog: https://github.com/coil-kt/coil/blob/main/CHANGELOG.md · GIFs: https://coil-kt.github.io/coil/gifs/ · SVGs: https://coil-kt.github.io/coil/svgs/ · Maven: https://repo1.maven.org/maven2/io/coil-kt/coil3/
- Telephoto: https://github.com/saket/telephoto · https://saket.github.io/telephoto/zoomableimage/ · module metadata https://repo1.maven.org/maven2/me/saket/telephoto/
- zoomimage (panpf): https://repo1.maven.org/maven2/io/github/panpf/zoomimage/
- Media3 release notes: https://developer.android.com/jetpack/androidx/releases/media3 · Media3 1.10 blog (2026-03-30): https://android-developers.googleblog.com/2026/03/media3-110-is-out.html
- ComposeMediaPlayer: https://github.com/kdroidFilter/ComposeMediaPlayer · https://github.com/kdroidFilter/ComposeMediaPlayer/blob/master/README_VIDEO.MD
- CameraX release notes: https://developer.android.com/jetpack/androidx/releases/camera · Viewfinder: https://developer.android.com/jetpack/androidx/releases/camera-viewfinder · CameraXViewfinder ref: https://developer.android.com/reference/kotlin/androidx/camera/compose/CameraXViewfinder.composable · Spotlight blog: https://developer.android.com/blog/posts/create-a-spotlight-effect-with-camera-x-and-jetpack-compose
- zxing-cpp releases: https://github.com/zxing-cpp/zxing-cpp/releases · Android wrapper: https://github.com/zxing-cpp/zxing-cpp/tree/master/wrappers/android · Maven: https://repo1.maven.org/maven2/io/github/zxing-cpp/
- ML Kit barcode (Android): https://developers.google.com/ml-kit/vision/barcode-scanning/android
- F-Droid context: https://forum.f-droid.org/t/ml-kit-for-qr-code-scanning/32971 · https://f-droid.org/en/packages/de.markusfisch.android.binaryeye · https://gitlab.com/tans1/tFileTransporter/-/issues/8 · https://f-droid.org/fa/docs/Anti-Features/
- Vendor benchmark (iOS, treat with care): https://dynamsoft.com/codepool/ios-barcode-scanner-benchmark-app-swiftui.html
- ZXing core Maven: https://repo1.maven.org/maven2/com/google/zxing/core/
- qrose: https://github.com/alexzhirkevich/qrose · Maven https://repo1.maven.org/maven2/io/github/alexzhirkevich/qrose/
- androidx.pdf release notes: https://developer.android.com/jetpack/androidx/releases/pdf · PDF viewer guide: https://developer.android.com/develop/ui/views/layout/pdf/pdf-viewer · Google Maven https://dl.google.com/android/maven2/androidx/pdf/
- Apache PDFBox Maven: https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox/
- Photo picker: https://developer.android.com/training/data-storage/shared/photo-picker · Embedded: https://developer.android.com/training/data-storage/shared/photo-picker/embedded · Compose ref: https://developer.android.com/reference/kotlin/androidx/photopicker/embedded/compose/package-summary
- Photo picker location: https://issuetracker.google.com/issues/243294058 · https://helentech.jp/news-android-photo-picker-location-metadata-90295/
- ACTION_IMAGE_CAPTURE permission note (secondary): https://zenn.dev/tonionagauzzi/articles/6c04fab431e6e4 · https://github.com/Automattic/Gravatar-SDK-Android/pull/394 · official ref https://developer.android.com/reference/android/provider/MediaStore#ACTION_IMAGE_CAPTURE
- FileKit: https://github.com/vinceglb/FileKit · releases https://github.com/vinceglb/FileKit/releases · picker https://filekit.mintlify.app/dialogs/file-picker · share https://filekit.mintlify.app/dialogs/share-file
- sherpa-onnx releases: https://github.com/k2-fsa/sherpa-onnx/releases · CHANGELOG https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/master/CHANGELOG.md · Kotlin API https://github.com/k2-fsa/sherpa-onnx/tree/master/sherpa-onnx/kotlin-api · AAR build https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxAar · Kotlin docs https://k2-fsa.github.io/sherpa/onnx/kotlin-api/examples.html · Java API https://k2-fsa.github.io/sherpa/onnx/java-api/index.html · Sonatype page https://central.sonatype.com/artifact/com.k2fsa.sherpa.onnx/sherpa-onnx-android
- MediaPipe TTS (Kokoro LiteRT): https://developers.google.com/edge/mediapipe/solutions/audio/text_to_speech/android
- Kokoro Android emulator benchmark (secondary): https://soniqo.audio/benchmarks/android
- Android 10 W^X / executable code: https://developer.android.com/about/versions/10/behavior-changes-10
- Android 15 behaviour changes (audio focus, FGS limits): https://developer.android.com/about/versions/15/behavior-changes-15
- Android 17 background audio hardening: https://developer.android.com/about/versions/17/changes/bg-audio
- Android 16 behaviour changes (predictive back, edge-to-edge): https://developer.android.com/about/versions/16/behavior-changes-16
- Sharing audio input: https://developer.android.com/media/platform/sharing-audio-input
- CDD 5.4 audio recording: https://android.googlesource.com/platform/compatibility/cdd/+/refs/tags/android-mainline-12.0.0_r98/5_multimedia/5_4_audio-recording.md · pre-processing: https://source.android.com/docs/core/audio/implement-pre-processing
- AudioFocusRequest ref: https://developer.android.com/reference/kotlin/android/media/AudioFocusRequest
- Repo facts: `/home/eslam/Storage/Code/wt/oc_app-batch1` (pubspec.yaml, lib/voice/*, lib/state/prompt_photos.dart, lib/platform/local_pdf.dart, android/.../LocalPdfService.kt, ReadAloudBridge.kt, MainActivityMedia.kt, AndroidManifest.xml, THIRD_PARTY_NOTICES.md); catalog `/home/eslam/Storage/Code/wt/native-plan/catalog`; demo `/home/eslam/Storage/Code/kmp-demo/gradle/libs.versions.toml`.
