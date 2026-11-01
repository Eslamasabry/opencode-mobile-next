# 10. Media and voice

Written 2026-11-01. Binding inputs: `01-architecture-decision.md` (the ADR; it wins on any conflict), `02` (scope and
working rules), `03` (modules and seams), `05` (stored formats), `06` (kit), `07` (screens). Detail sources:
`../map/media-voice.md` (features `MEDIA-VOICE-01…50`), `../research/media.md`, the catalog rows `B-MEDIA-001…034` and
the 41 rows of other areas that cite media tests (§20), and the read-only Flutter tree
`/home/eslam/Storage/Code/wt/oc_app-batch1` plus the freeze branch `int/freeze-2026-11-01` (tip `fc5c1e78f`) for the
items merged after the catalog (clipboard image paste, Wi-Fi share of chat links and phone files).

Tags, same as the ADR: **[V]** checked today against a primary source (Maven metadata, published sources, official
docs). **[R]** read in the repo, a map, research or memory. **[U]** unverified; a proof in §22 settles it.

---

## For the owner, in five lines

1. Voice typing, read aloud, photos, files, PDF, QR codes and Wi-Fi sharing all move to native Kotlin with the same
   rules, limits and words as today. Your downloaded speech models, a photo waiting after a crash, and your voice
   settings carry over on the in-place update with no re-download.
2. Speech stays on the phone and stays in the APK (sherpa-onnx Whisper and Kokoro, about 10 MB). The native app keeps
   the speech model loaded while you talk instead of reloading it every 30 seconds, so long dictation gets faster.
3. Google's ML Kit is gone: QR scanning uses CameraX plus zxing-cpp (open source, about 1.7 MB smaller), which keeps
   the scanner F-Droid-ready as you decided on 2026-11-01.
4. Everything uses Android's own surfaces: the system photo picker, system camera, file dialogs and share sheet, with
   native ripples, haptics and predictive back. Untrusted PDFs still open in a sealed separate process.
5. Eleven small decisions for you are in §24, each with a default we work on until you answer (for example: keep a
   half-downloaded model when you tap Cancel, yes; accept pictures shared into the app, after the switch).

---

## 0. How to use this section

### 0.1 Who reads what

| Reader | Read first | Then |
|---|---|---|
| Coordinator | §0.2, §0.3, §21, §22, §24 | §1 scope table when writing `contracts/s3.md` and `contracts/s5.md` |
| Engine builder (Codex) | §3, §4, then the subsystem section of the brief (§5–§8, §10–§14) | §20 rows for that subsystem |
| Android builder (Sonnet/Opus) | §2 (packaging, R8), §3.3, the subsystem section | §17 invariants, §18 budgets |
| Kit-content and feature builders (Claude) | §19 (UI rules), the subsystem section's "UI" part | §4.4 state types they render |
| Test-writer lane | §20 | the Flutter `sources` of each row |
| Migration owner | §16 | `05` Appendices A, C, D |

### 0.2 Choices this section adds (decided here; reversible; each removes something)

| # | Choice | Why, and what it removes |
|---|---|---|
| M1 | No new Gradle modules for media. Logic is `:engine:media` (commonMain only); Android work is `:android:media`; UI parts are `:ui:kit-content` / `:ui:kit-chat`; screens live in the features the ADR already names. | ADR §5.2 already has the homes. Removes a `:speech-native` module (research §7 idea) and a Wi-Fi server module. |
| M2 | **One warm recognizer per recording.** The Whisper model loads once when listening starts and is released 20 s after the recording ends (or at once on memory pressure). | Flutter reloads the model for every 30 s chunk [R map §8]. Removes per-chunk load time and the isolate machinery. |
| M3 | **Cancel keeps the `.part` files** of a model download (resume next time); **Remove** deletes every staged, partial and backup file. A cancel during verification or publishing still cleans the staged files. | Settles DRIFT 33a in favour of what the docs and B-MEDIA-012 promise. Removes a re-download of up to 375 MB after an accidental Cancel. Amends B-MEDIA-025 clause "cancelled downloads leave no files" to "leave no marker" (§20.3). |
| M4 | **Verified stamp.** After a full SHA-256 check, `.native-verified.json` beside `.installed.json` records each file's length and mtime. Start-up checks the stamp only; a full re-hash runs on mismatch, on a recognizer load failure, and once per pack on the first native launch. | Flutter re-hashes up to 540 MB whenever the voice manager is created [R map §8]. Removes that work from every first mic tap. |
| M5 | **The natural voice (Kokoro) gate is enforced:** total RAM ≥ 2000 MB. | DRIFT 33c: declared, never enforced. |
| M6 | **Typed failures only.** Every media failure is `Failure.Media(problem: MediaProblem, details)` (§4.1). No copy is chosen by matching exception text. | DRIFT 33b (voice and pairing copy chosen by exact message strings). Removes a whole class of silent copy breakage. |
| M7 | **Our own small XML pull reader** in `:engine:media` commonMain (no DTD, no external entities, caps on size and depth), shared by the SVG validator and the Office reader. | Removes `xmlutil`/`xml` dependencies and XXE risk by construction. |
| M8 | **Three primitives in `:core:common`** (`expect`, the only module allowed to): `Sha256` (incremental), `RawInflate` (ZIP "deflate" entries) and `secureRandomBytes`. Android and desktop actuals use `java.security.MessageDigest`, `java.util.zip.Inflater` and `java.security.SecureRandom`. | `DartJson.sha256Hex` (05 §2.5) needs SHA-256 anyway; a 375 MB file needs the platform's fast digest. Removes a hand-written inflater. |
| M9 | **One Coil `ImageLoader`, no disk cache**, memory cache keyed by `ImageRef` (profile + source + version). Bytes come only through `KitImageResolver` (the gateway's file transport with its caps). Coil never fetches a URL. | Removes a shared HTTP/disk cache that could hold one server's files after the server is deleted. |
| M10 | **Photos are always re-encoded in-app** (decode at target size, long edge ≤ 2048 px, JPEG quality 85), except GIF and animated WebP that already fit the caps, which are kept byte for byte. EXIF (including location) is dropped. | `image_picker` did the scaling before; the Photo Picker does not [R map §12 risk 6]. One rule instead of per-format guesses. |
| M11 | **Camera photos go through the system camera app** (`TakePicture`) into `files/camera-capture-v1/<id>.jpg`, after a `PermissionGate` CAMERA request. | Parity; removes an in-app camera screen (owner decision D10-8 can add one later). |
| M12 | **PDF: keep the isolated `LocalPdfService`** (same FQCN, same limits). `androidx.pdf` is not used. | Keeps the security answer for untrusted PDFs; removes Material Views + RecyclerView from the APK. |
| M13 | **QR drawing uses `qrose-encoder-matrix` 1.3.1** (pure Kotlin matrix) and a kit `Canvas` painter with whole-pixel modules, not the `qrose` Compose painter. | Keeps the LOOK-21 pixel snapping and EC level M; removes a Compose painter dependency. |
| M14 | **The Wi-Fi share server is a ~250-line `ServerSocket` server** in `:android:media` (GET/HEAD only), not Ktor server. | ADR ExternalRules allow Ktor only in net/protocol modules; removes `ktor-server-cio` (and its size) for one file at a time. |
| M15 | **Read aloud and dictation stay foreground-only.** No `mediaPlayback` or `microphone` foreground service. | Parity, Android 15/17 audio rules satisfied with no service [R research §9]; background listening is owner decision D10-5. |
| M16 | **Recovery reads the image_picker leftovers once.** If `oc.pendingPromptPhoto` has no `path` on the first native launch, the migration fills it from `flutter_image_picker_shared_preference` (§16.3). | Closes the one gap where a photo taken just before the update would be lost. |

### 0.3 Change requests to other sections (the coordinator routes them; work proceeds on these defaults)

| # | To | Request | Default until answered |
|---|---|---|---|
| C10-1 | 03 §10.1 (core owner) | Add `Failure.Media(val problem: MediaProblem, override val details: Redacted?)` and the append-only `enum class MediaProblem` (§4.1). `:ui:words` gets one exhaustive `when` for it. | Builders code against §4.1. |
| C10-2 | 03 §10.1 | Add `expect` primitives `Sha256`, `RawInflate` and `secureRandomBytes(n)` to `:core:common` (§4.2). `DartJson.sha256Hex` uses `Sha256`. | As §4.2. |
| C10-3 | ADR §5.2 "Starts" column | `:engine:media` and `:android:media` start in **slice 3**, not 5: the composer's attachments (slice 3) need the MIME gate, photo caps and photo re-encoding. Voice, read aloud, viewer, PDF and Wi-Fi share stay in slice 5. | Slice 3. |
| C10-4 | 03 §10.4 (`:core:domain`) | Define the types 03 names but does not spell out: `PickedFile`, `PhotoTarget`, `DocumentTarget`, `SharedFile` (§4.3). Add `AppPermission.LocalNetwork` for the targetSdk-37 slice. | As §4.3. |
| C10-5 | 03 §12.1 | `AndroidPermissionGate` and `AndroidActivityResults` attach to **whichever host activity is resumed** (`MainActivity` or `WidgetOverlayActivity`), not only `MainActivity`: the widget voice panel asks for the microphone (07 M03). | As stated. |
| C10-6 | 03 §11.2 media row | Add to the media engine's public API: `SpeechSynthesizer` (Kokoro), `ImageCodec`, `QrImageDecoder`, `LocalFileServer`, `AttachmentIntake`, `PendingPhotoRecovery`, `ModelPacks`, `VoiceSetup`, `ReadAloud`, `VoiceConversation`, `FilePreview`, `WifiShare`. | §4.5. |
| C10-7 | 04 §3.10 (`Downloader`) | `DownloadRequest` options the voice packs need: manual HTTPS-only redirects (max 5, each hop `https`), exact `Content-Range` check on 206, `Content-Length` must equal the remaining pinned bytes, connect 15 s / per-step 15 s / body-idle 20 s timeouts, `User-Agent`, a `keepPartialOnCancel` flag (M3), and a progress throttle. | §7.4 lists them; `:core:net` owner adds them. |
| C10-8 | 03 §11.2 `composer` row | An app-scoped `DraftStore.appendAttachment(ref: ConversationRef, attachment: DraftAttachmentRef): Outcome<AppendResult>` so photo recovery can reach the recorded conversation's draft while that connection is not live. | §10.5. |
| C10-9 | 05 Appendix D | `flutter_image_picker_shared_preference` is read once by migration step M16 (§16.3) before v3 deletes it. | §16.3. |
| C10-10 | ADR §9.9 component freeze | Intentionally removed components: `io.flutter.plugins.imagepicker.ImagePickerFileProvider` (authority `${applicationId}.flutter.image_provider`) and every `mobile_scanner`/ML Kit component. Added: `androidx.core.content.FileProvider` with authority `${applicationId}.localfiles` (same as the freeze-tip build). `.LocalPdfService` stays at its FQCN with `:local_pdf`. | §15.2. |
| C10-11 | 06 §7.7 | `KitQr` built on `qrose-encoder-matrix` (M13); `KitImage` takes an `ImageRef`, never bytes or a URL; `KitImageMarkup` returns a `MarkupDocument`, the engine rasterizes it (§9.4). | As stated. |
| C10-12 | 11 (notifications) | `NotificationSurface.showModelDownload(phase, received, total): Boolean` and `dismissModelDownload()`, keeping channel `opencode_voice_setup`, id `0x701CE` and the rules of §7.5. | §7.5. |
| C10-13 | 07 (routes) | `ProjectRoute.FileView` gains `transientSource: String?` for previews without a server path (§11.1). | §11.1. |

### 0.4 What this section does not decide

Dates and slice contents (15), the kit's visual specs (06), route keys and screen inventory (07), the transcript and
composer frame (09), notification channels other than voice download (11), the analytics core (16), Room timing (ADR
§9.8). It gives media its contracts, rules, limits, tests and work packages.

---

## 1. Scope: every map feature, where it lives, when it lands

`Pkg` = the `:engine:media` package (`….oc.engine.media.<pkg>`). `Android` = the `:android:media` package unless
another module is named. Slices are the ADR's (0 skeleton … 6 switch). Every feature ships at the switch
(02 §3.2: media has no after-switch rows), except what §24 defers.

| Feature | What | Pkg | Android | UI (module) | WP | Slice |
|---|---|---|---|---|---|---|
| 01 Mic dictation into the draft | voice mode, chunked words at the caret, never sends | `voice` | `audio`, `speech` | `KitComposerVoice` (kit-chat); `:feature:chat-composer` | MV-5 | 5 |
| 02 Hold to talk | 350 ms / 18 dp / 80 dp / 400 ms rules, haptics, tip | `voice` (`MicPress`) | — | `KitMicHoldDetector` (kit-chat) | MV-5 | 5 |
| 03 Send when I stop talking | silence detector, 3 s countdown, safety filter | `voice` | — | pill countdown | MV-5 | 5 |
| 04 Voice conversation | listen → send → wait → read → ready loop, reply watch | `conversation` | — | `:feature:chat-composer` + `:feature:chat` | MV-7 | 5 |
| 05 Recording pipeline | `AudioRecord` 16 kHz mono PCM16, 30 s chunks, ordered queue | `voice` | `audio` | — | MV-5 | 5 |
| 06 Recognizer | sherpa-onnx Whisper, warm per recording (M2) | `voice` (interface) | `speech` | — | MV-5 | 5 |
| 07 Voice language | 36 choices, folding, notes | `voice` | — | Settings › Voice row | MV-5 | 5 |
| 08 Model packs + RAM gate | pins, support check, automatic choice | `models` | `device` | — | MV-6 | 5 |
| 09 Resumable verified downloader | HTTPS, Range, SHA-256, atomic marker, rollback, lock | `models` (+ `:core:net` `Downloader`) | — | — | MV-6 | 5 |
| 10 Model manager | selection, readiness, prefs, stale-work guards | `models` | — | — | MV-6 | 5 |
| 11 First-mic automatic setup | consent per attempt, network classes, hand-off | `setup` | `device` | setup sheet (`:feature:chat-composer`) | MV-6 | 5 |
| 12 `oc/voice` bridge behaviours | device probe, network class, notification | `models`, `setup` | `device`; notification in `:android:background` | — | MV-6 | 5 |
| 13 Settings › Voice typing | page + model sheet | `models` | — | `:feature:settings` (`SettingsRoute.Voice`, `SheetKey.VoiceModelSetup`) | MV-6 | 5 |
| 14 Phone-setup voice component | optional, off by default | `setup` | — | phone setup (`:feature:phone`) | MV-6 | 5 |
| 15 Mic permission | three-state answer, recovery copy | — | `PermissionGate` in `:android:platform` | voice pill reasons | MV-5 | 5 |
| 16 Voice licences | notices viewer rows | `notices` | assets | About (`:feature:about`) | MV-10 | 6 |
| 17 Capability enable flows | voice setup, mic, camera | — | — | kit `KitEnableFlows` | MV-6 | 5 |
| 18 Level meter and pill contract | 9 bars, `litFor`, phases | `voice` (`LevelMeter.litFor`) | — | `KitLevelMeter`, `KitComposerVoice` | MV-5 | 5 |
| 19 Read a reply aloud | consent, voice choice, menu items | `readaloud` | `tts` | message menu (`:feature:chat`) | MV-7 | 5 |
| 20 What gets spoken | `proseForSpeech` | `:core:markdown` (owner: chat lead) | — | — | MV-7 | 5 |
| 21 System TTS | port of `ReadAloudBridge` | `readaloud` (interface) | `tts` | — | MV-7 | 5 |
| 22 Natural voice (Kokoro) | pack, language routing, synthesis, playback | `readaloud`, `models` | `speech`, `audio` | Settings › Voice section | MV-7 | 5 |
| 23 Ownership, lifecycle, scope | one reader, foreground only, scope tuple | `readaloud` | — | — | MV-7 | 5 |
| 24 Capture/playback exclusion | 10 s guard, callbacks, focus | `audio` (interface) | `audio` (`AndroidAudioSession`) | — | MV-5 | 5 |
| 25 "+" tray and tools list | Photos, Camera, File, More | — | — | `KitComposerTray` (kit-chat); `:feature:chat-composer` | MV-2 | 3 |
| 26 Photos and camera | Photo Picker, `TakePicture`, re-encode | `attach` | `image`; contracts in `:android:platform` | tray | MV-2 | 3 |
| 27 Photo recovery + vault | record before launch, recover into the recorded draft | `attach` (`PendingPhotoRecovery`) | — | composer note | MV-2 | 3 |
| 28 File attach | SAF open, bounded read, MIME gate | `attach` | — | tray | MV-2 | 3 |
| 29 Excel/Word as text | own ZIP + XML readers | `office` | — | composer note | MV-2 | 3 |
| 30 Keyboard-inserted images and clipboard paste | `contentReceiver`; "Paste image from clipboard" row | `attach` | `clipboard` | composer field + "+" sheet | MV-2 | 3 |
| 31 Desktop file drop | `dragAndDropTarget` | `attach` | — | desktop phase only | — | desktop |
| 32 Chips, thumbnails, sent photos, reply pictures | `KitImage` with `ImageRef` | `images` | `image` | `KitComposerChips`, transcript | MV-3 | 3 |
| 33 What each agent can take | capability gating | — | — | `:feature:chat-composer` | MV-2 | 3 |
| 34 Photo asks from agent cards | same store and limits | `attach` | — | card renderer (09) | MV-2 | 3 |
| 35 Attach from viewers, tool output, project | Attach vs Add to prompt | `attach` | — | viewer actions | MV-4 | 5 |
| 36 Text shared in and out | share-in handshake, chooser excluding the app | — | `:android:platform` (owner 11) | — | — (11) | 4 |
| 37 `KitViewer` | one viewer, body kinds, Find, states | `preview` | — | `KitViewer` (kit-content); `ProjectRoute.FileView` | MV-4 | 5 |
| 38 Preview adapters | strict UTF-8, MIME map, flags | `preview` | — | — | MV-4 | 5 |
| 39 Files screen viewer | Ask about this file, Share on Wi-Fi | `preview` | — | `:feature:project` | MV-4 | 5 |
| 40 Local PDF (isolated) | `LocalPdfService` | `preview` (interface) | `pdf` + `:androidApp` manifest | `KitPdfPage` | MV-4 | 5 |
| 41 SVG / CSV limits | validator, table parser | `preview`, `xml` | — | viewer bodies | MV-4 | 5 |
| 42 Gallery + zoom + send to composer | swipe only when not zoomed | `images` | — | `KitImageGallery`, `KitZoom` | MV-3 | 5 |
| 43 Markup editor | four inks, crop, full-resolution export | `images` (`MarkupRaster`) | `image` | `KitImageMarkup` | MV-3 | 5 |
| 44 Save and Copy | SAF create document, verbatim copy | `preview` | contracts in `:android:platform` | viewer More | MV-4 | 5 |
| 45 Wi-Fi share | token server, rules, QR + link sheet | `share` | `share` (`LanFileServer`) | `:feature:project` sheet | MV-8 | 5 |
| 46 Scan a pairing QR | CameraX + zxing-cpp, latch, never keep the value | `qr` (rules) | scanner slot in `:ui:kit-content` androidMain | `KitScanner`; `ServersRoute.PairingScanner` | MV-9 | 3 |
| 47 Show a QR (`KitQr`) | EC M, quiet zone 4, pixel snap | `qr` | — | `KitQr` (kit-content) | MV-8 | 4–5 |
| 48 Camera seam | `hasCamera`, three-state permission | — | `:android:platform` | — | MV-9 | 3 |
| 49 Reader preferences | `oc.readerPreferences.<id>` (owner `:engine:settings`, 05) | — | — | viewer | MV-4 | 5 |
| 50 Files a turn changed, tool output files | rows under a turn, viewer | `preview` | — | `:feature:chat` (09) | MV-4 | 5 |

Freeze-tip additions (02 §3.5, "Switch"): **clipboard image paste** (with 30, MV-2), **Wi-Fi share of chat file links
and phone files streamed from disk** (with 45, MV-8), **open a phone file with another app / install an `.apk` through
the system installer** (with 44, MV-4).

---

## 2. Libraries: choices, versions, multiplatform notes

### 2.1 Chosen (pin in `native/gradle/libs.versions.toml`; checked 2026-11-01)

| Need | Library | Pin | Module(s) allowed (ExternalRules) | Android | iOS later | Linux desktop later |
|---|---|---|---|---|---|---|
| Image loading | Coil 3: `coil-compose`, `coil-svg`, `coil-gif` | **3.6.3** [V Maven, 2026-09-18] | `coil-compose`: `:ui:kit`, `:ui:kit-content`; svg/gif: `:ui:kit-content` | `AnimatedImageDecoder` (API 28+), `GifDecoder` below; `coil-svg` → AndroidSVG 1.4 | Coil (Skia); GIF stills only | Coil (Skia); GIF stills only |
| Zoom | Telephoto `zoomable` | **0.19.0** [V; 1.0.0-alpha02 exists, not used] | `:ui:kit-content` | `Modifier.zoomable()` | same (KMP) | same |
| QR matrix | `io.github.alexzhirkevich:qrose-encoder-matrix` | **1.3.1** [V, 2026-10-05] | `:ui:kit-content`, `:engine:media` | pure Kotlin | same | same |
| QR scan, camera | CameraX `camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-compose` | **1.6.2** [V]; move to 1.7.0 when stable (1.7.0-rc01 on 2026-10-21 [V]) | `:ui:kit-content` (a), `:android:media` | `CameraXViewfinder` + `ImageAnalysis` | AVFoundation metadata output (no library) | none (no scan row; paste only) |
| QR decode | `io.github.zxing-cpp:android` | **3.1.1** [V, 2026-07-29] | `:ui:kit-content` (a), `:android:media` | `BarcodeReader` on the Y plane; 16 KB aligned [I research] | not needed | ZXing core 3.5.4 on a picked image (desktop phase) |
| Speech (ASR + TTS) | sherpa-onnx Kotlin API, **vendored AAR** `sherpa-onnx-2.0.1.aar` | **2.0.1** [V GitHub release 2026-10-26; not on Maven Central] | `:android:media` only | `OfflineRecognizer`, `OfflineTts` | C API xcframework [U] | `sherpa-onnx-jvm-2.0.1.jar` + Linux native jar [U] |
| Photo orientation (API 26–27 only) | `androidx.exifinterface:exifinterface` | **1.4.2** [V] | `:android:media` | used only below API 28 (`ImageDecoder` applies EXIF itself) | — | — |
| FileProvider | `androidx.core:core` | **1.19.1** (ADR §8) | `:androidApp`, `:android:platform` | `FileProvider` (manifest in `:androidApp`) | — | — |
| Pickers, camera, SAF, permissions | `androidx.activity:activity` result contracts | **1.13.0** (ADR §8) | `:android:platform` | `PickVisualMedia`, `PickMultipleVisualMedia`, `TakePicture`, `OpenDocument`, `CreateDocument`, `RequestPermission` | FileKit 0.17.0 (iOS phase) | FileKit 0.17.0 (desktop phase) |
| System voice | platform `android.speech.tts.TextToSpeech` | — | `:android:media` | port of `ReadAloudBridge` | `AVSpeechSynthesizer` | speech-dispatcher or capability off [U] |
| Microphone | platform `AudioRecord` | — | `:android:media` | 16 kHz mono PCM16, `MIC` source | `AVAudioEngine` | `TargetDataLine` [U] |
| Playback (natural voice) | platform `AudioTrack` | — | `:android:media` | `ENCODING_PCM_FLOAT`, `MODE_STREAM` | `AVAudioEngine` | `SourceDataLine` |
| PDF | platform `PdfRenderer` in an isolated service | — | `:android:media` + `:androidApp` | API 29+ (`bindIsolatedService`) | PDFKit | Apache PDFBox 3.0.8 [V] |
| Test-only QR decode on the JVM | `com.google.zxing:core` | **3.5.4** [V] | `:ui:kit-content` and `:engine:media` test configurations | — | — | — |

`libs.versions.toml` lines this section adds or changes (03 §5 owns the file; the `build-logic` owner applies them):

```toml
[versions]
coil = "3.6.3"
telephoto = "0.19.0"
qrose = "1.3.1"
camerax = "1.6.2"
zxing-cpp = "3.1.1"
sherpa-onnx = "2.0.1"
exifinterface = "1.4.2"
zxing-core = "3.5.4"

[libraries]
qrose-encoder-matrix = { module = "io.github.alexzhirkevich:qrose-encoder-matrix", version.ref = "qrose" }   # replaces the `qrose` painter row (M13)
exifinterface = { module = "androidx.exifinterface:exifinterface", version.ref = "exifinterface" }
zxing-core = { module = "com.google.zxing:core", version.ref = "zxing-core" }                                    # tests only
# unchanged rows already in 03 §5: coil-*, telephoto-zoomable, camerax-*, zxing-cpp, sherpa-onnx (vendored)
```

`ExternalRules` additions (03 §6.6): `"io.github.alexzhirkevich" to { p -> p == ":ui:kit-content" || p == ":engine:media" }`,
`"androidx.exifinterface" to { p -> p == ":android:media" }`, `"com.google.zxing" to` test configurations only.
`telephoto-zoomable-image-coil3` is removed from the catalog (Android-only sub-sampling; not needed until a measured
stutter, §18).

### 2.2 The vendored sherpa-onnx AAR (single owner: the media lead)

1. Source: `https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar` (49,441,341 bytes [I
   research]). Path: `native/third_party/maven/com/k2fsa/sherpa/onnx/sherpa-onnx-android/2.0.1/sherpa-onnx-android-2.0.1.aar`
   plus a hand-written `.pom` (no dependencies). Its SHA-256 goes into `native/third_party/SHA256SUMS`; `:quality`
   fails the build on a mismatch (03 §3.1 already routes the group there).
2. Never fetched at build time from JitPack (its 2.0.1 build shows "Error" [I research]).
3. A version bump is a single-owner change: new AAR, new hash, the R8 smoke (§2.4 step 4) and the RTF benchmark (§18)
   on NX721J. The Flutter build pins the same 2.0.1, so the model files are byte-compatible across the switch.

### 2.3 Packaging (`:androidApp/build.gradle.kts`, owned by the coordinator)

```kotlin
android {
    packaging {
        jniLibs {
            // Kotlin JNI needs only libonnxruntime.so + libsherpa-onnx-jni.so [I research §7]
            excludes += listOf("**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so")
            useLegacyPackaging = true   // already required for proot (ADR §9.9); also deflates ~27 MB of speech libs to ~9.8 MB
        }
    }
}
```

The ABI splits of ADR §9.9 filter the sherpa-onnx and zxing-cpp `.so` files with everything else. Every shipped `.so`
must have `p_align 0x4000` (16 KB); `tool/native/check_so_alignment.py` runs at T2 over the arm64 APK.

### 2.4 R8 (`:android:media/consumer-rules.pro`, published to the app by the convention plugin)

```
# sherpa-onnx: JNI reads config fields by name; the AAR ships an empty proguard.txt [I research §7]
-keep class com.k2fsa.sherpa.onnx.** { *; }
# zxing-cpp ships its own consumer rule (-keep class zxingcpp.** { *; }) [I]; nothing to add.
```

Checks, in order, before any APK leaves the PC:

1. `analyzeReleaseR8Config` (ADR §9.9) shows the sherpa keep rule applied.
2. `apkanalyzer files list` on the arm64 APK: `lib/arm64-v8a/libonnxruntime.so` and `libsherpa-onnx-jni.so` present;
   no `libsherpa-onnx-c-api.so`, no `libbarhopper*`, no `mlkit` assets, no `com/google/mlkit` classes.
3. `./gradlew :androidApp:dependencies --configuration stableReleaseRuntimeClasspath | grep -E "mlkit|play-services|firebase"` prints nothing.
4. **Release smoke** (`:android:benchmark` or an instrumented test on the release build): construct
   `OfflineRecognizer(null, config)` with the `tiny` fixture paths pushed to the emulator and decode a 2 s WAV; construct
   `OfflineTts` with a missing-file config and expect `IllegalArgumentException` from its `require` (proves JNI field
   lookup survives R8).

### 2.5 Size budget (arm64 APK, release, compressed)

| Item | Budget | Source |
|---|---|---|
| sherpa-onnx (onnxruntime 7.9 MB + jni 1.9 MB) | ≤ 9.9 MB | [I research §7] |
| zxing-cpp | ≤ 0.8 MB | [I research §4] |
| CameraX (core, camera2, lifecycle, compose) | ≤ 1.2 MB after R8 | [U] measured in MV-9 |
| Coil 3 + svg (AndroidSVG) + gif | ≤ 1.0 MB after R8 | [U] measured in MV-3 |
| qrose-encoder-matrix | ≤ 0.1 MB | [U] |
| Licence texts | ≤ 0.1 MB | — |
| **Media total** | **≤ 13 MB** | the T2 size gate (03, 16) reports media growth separately |

### 2.6 Considered and rejected

| Rejected | Why | Instead |
|---|---|---|
| ML Kit barcode (bundled 17.3.0 or unbundled 18.3.1) | owner decision 2026-10-31/11-01 (Google-free, F-Droid); 2.4 MB | zxing-cpp |
| Java ZXing core on Android | slowest decoder; zxing-cpp is 10–40 % faster again in 3.1 [V research] | zxing-cpp (ZXing core only in JVM tests and the desktop phase) |
| `androidx.pdf` 1.0.0-beta01 | beta; in-process parse; pulls Material Components Views + RecyclerView; minSdk 28 | isolated `LocalPdfService` (M12); owner decision D10-7 |
| Media3 / ExoPlayer 1.11.1 | the app plays no audio or video files; read aloud uses `AudioTrack` | none on day 1; "Open with another app" for media files |
| FileKit 0.17.0 on Android | a second abstraction over the same contracts the ADR puts in `:android:platform` | platform contracts; FileKit in the desktop and iOS phases |
| Embedded photo picker (`androidx.photopicker` 1.0.0-alpha02) | alpha, Android 14 + SDK extension 15 only | system Photo Picker |
| MediaPipe `tasks-audio` Kokoro (LiteRT, GPU) | no ASR, second runtime, size unknown | sherpa-onnx for both; revisit only if Kokoro RTF fails §18 |
| Downloading the speech runtime | W^X and dynamic-code rules; a new failure mode [V research §7] | bundled `.so` |
| A separate `:speech` process | ADR §9.5: the PDF service is the only extra process | in-process; memory gate + low-memory release (§6.4) |
| Ktor server (CIO) for Wi-Fi share | ExternalRules confine Ktor to net/protocol; size | `ServerSocket` server (M14) |
| `xmlutil`, `ksoup` or similar XML libraries | not on the commonMain allowlist (ADR §7.2); DTD/entity surface | own pull reader (M7) |
| Telephoto `zoomable-image-coil3` (sub-sampling) | Android-only; images are capped at 2048 px for prompts and 4096 px for decode | plain `zoomable`; revisit if §18 shows stutter |
| Telephoto `flick` (drag to dismiss) | two dismiss gestures fight predictive back (demo lesson) | predictive back is the one dismiss gesture |
| sherpa `Vad` (Silero/TEN) instead of the RMS detector | changes thresholds and the catalog rows | RMS `SilenceDetector`; VAD is a post-switch experiment |
| `security-crypto` for media secrets | none needed: media stores no secrets | — |

### 2.7 F-Droid readiness (owner, 2026-11-01: Google-free QR, F-Droid submission after the rewrite)

| Item | `stable` flavour | `fdroid` flavour (defined by section 16; built later from source by F-Droid) |
|---|---|---|
| QR decoder | zxing-cpp 3.1.1 from Maven Central (a trusted binary source in F-Droid's policy [R `docs/release/fdroid-2026-10-31.md` on `feat/fdroid-2026-10-31`]) | same |
| Google SDKs | none; the T2 grep (§2.4 step 3) fails on `mlkit`, `play-services`, `firebase` | same guard |
| Photo Picker backport entry (`com.google.android.gms.metadata.ModuleDependencies`, a manifest string, no dependency) | present | removed by the flavour manifest (`tools:node="remove"`); API < 30 without the system picker falls back to `OpenDocument` |
| sherpa-onnx | vendored AAR (a binary in the repo, which F-Droid's scanner refuses) | **D10-6 Lite:** the AAR is not on the classpath; `:android:media`'s `speech` package binds `UnavailableSpeechRecognizer` and `UnavailableSpeechSynthesizer` (`voice = false`, natural voice hidden); the phone's own voices still read aloud. **Full** (later): build sherpa-onnx and ONNX Runtime from source in the recipe |
| `.apk` **Install** action | shown | hidden (`REQUEST_INSTALL_PACKAGES` removed by the flavour manifest) |
| Model downloads from `huggingface.co` | on a tap, with consent | disclosed in the metadata as a network download of model data |

---

## 3. Where the code lives

### 3.1 `:engine:media` (KMP, commonMain only, App scope; ADR §6.2 position 13)

It may use engines 1–12 (settings, diagnostics, profiles, connection, transcript, requests, delivery, composer,
runtime, agents, attention, schedule) through their public APIs, `:core:*` except `:core:net-okhttp`, and nothing from
`:ui:*`, `:protocol:*` or Compose. `explicitApi()` is on.

```
native/engine/media/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/media/
  MediaGraph.kt                 @ContributesTo(AppScope) accessors; Unavailable* defaults are bound by DesktopAppGraph
  MediaFailures.kt              helpers that build Failure.Media with Redacted details (never raw text)
  audio/      AudioSession.kt  AudioCapture.kt  PcmPlayer.kt                      (interfaces, §5)
  voice/      VoiceController.kt  VoiceState.kt  Pcm16Chunker.kt  ChunkQueue.kt  SilenceDetector.kt
              AutoSendPolicy.kt  DraftMerge.kt  VoiceLanguage.kt  LevelMeter.kt  SpeechRecognizer.kt
  models/     ModelPackSpec.kt  ModelCatalog.kt (pins)  PackSupport.kt  AutomaticPack.kt  ModelPacks.kt
              PackInstaller.kt  InstalledMarker.kt  VerifiedStamp.kt  DeviceProbe.kt  SetupNetwork.kt
  setup/      VoiceSetup.kt (first-mic state machine)  VoiceSetupComponent.kt (phone setup)
  readaloud/  ReadAloud.kt  SystemTts.kt  SpeechSynthesizer.kt  ReadLanguage.kt  SentenceSplitter.kt  VoicePick.kt
  conversation/ VoiceConversation.kt  ReplyWatch.kt  SpeechScope.kt
  attach/     AttachmentIntake.kt  AttachmentLimits.kt  PromptAttachmentMime.kt  BoundedRead.kt  MagicBytes.kt
              PhotoRules.kt  PendingPhotoRecovery.kt  ClipboardImages.kt  ImageCodec.kt
  office/     OfficeText.kt  ZipCentralDirectory.kt
  xml/        XmlPull.kt
  preview/    FilePreview.kt  PreviewData.kt  MimeMap.kt  StrictUtf8.kt  Truncate.kt  DelimitedText.kt  StaticSvg.kt
              PdfPageRenderer.kt  PdfPages.kt  ViewerFind.kt
  images/     ImageResolver.kt  GalleryIndex.kt  MarkupDocument.kt  MarkupRaster.kt
  share/      WifiShare.kt  ShareHttp.kt (request parse + response headers, pure)  PathSafety.kt  LocalFileServer.kt
  qr/         QrRules.kt  QrImageDecoder.kt
  notices/    VoiceNotices.kt
native/engine/media/src/commonTest/…          one test class per catalog row family (§20)
native/engine/media/src/commonTest/resources/ fixtures: pcm/, office/, svg/, csv/, pdf-results/, http/ (copied from the Flutter tests)
```

### 3.2 Kit and features

| Module | Media parts |
|---|---|
| `:ui:kit` | `KitImage` (Coil `AsyncImage` + `KitImageResolver` seam, §9.1), `KitSheetHost` (used by every media sheet) |
| `:ui:kit-chat` | `KitComposerVoice`, `KitVoicePhase`, `KitMicHoldDetector`, `KitLevelMeter`, `KitComposerTray`, `KitComposerChips.attachments` |
| `:ui:kit-content` | `KitViewer` (+ bodies), `KitPdfPage`, `KitZoom`, `KitImageGallery`, `KitImageMarkup`, `KitSvgImage`, `KitQr`, `KitScanner` (common frame) + `androidMain` `AndroidScannerSlot` (CameraX + zxing-cpp) |
| `:feature:chat-composer` | voice mode wiring, first-mic setup sheet, tray, attachments, paste row, voice conversation strip |
| `:feature:chat` | message menu "Read reply prose" / "Read with another voice", gallery overlay, sent photos, turn file rows |
| `:feature:settings` | `SettingsRoute.Voice` page, `SheetKey.VoiceModelSetup` sheet, read-aloud voice section |
| `:feature:project` | `ProjectRoute.FileView`, Save / Share / Open with, Wi-Fi share sheet |
| `:feature:servers` | `ServersRoute.PairingScanner` (stages, paste instead) |
| `:feature:phone` | voice component row in phone setup |
| `:feature:about` | "Voice licences" rows |
| `:feature:widget-panels` | voice panel (`WidgetPanel.Steer(widgetId, voice = true)`) reusing `VoiceController` |

### 3.3 `:android:media` (plain Android library)

```
native/android/media/src/main/kotlin/io/github/eslamasabry/oc/android/media/
  MediaBindings.kt              @ContributesBinding(AppScope) for every interface below
  audio/      AndroidAudioSession.kt  AndroidAudioCapture.kt  AndroidPcmPlayer.kt
  speech/     SherpaSpeechRecognizer.kt  SherpaSpeechSynthesizer.kt  SherpaConfigs.kt
  tts/        AndroidSystemTts.kt                 (port of ReadAloudBridge.kt, 477 lines)
  device/     AndroidDeviceProbe.kt  AndroidSetupNetwork.kt
  image/      AndroidImageCodec.kt  AndroidMarkupRaster.kt
  clipboard/  AndroidClipboardImages.kt           (port of ClipboardImageBridge.kt + ClipboardImagePolicy.kt)
  qr/         ZxingQrImageDecoder.kt
  pdf/        PdfPageRasterizer.kt (runs inside the isolated process)  AndroidPdfPageRenderer.kt (client, port of LocalPdfBridge.kt)
  share/      AndroidLanFileServer.kt  WifiAddress.kt
native/android/media/src/test/…                 Robolectric + JVM tests (§20.4); there were none for these bridges in Flutter
```

OS-visible classes stay in `:androidApp` at their frozen FQCNs (ADR §5.2): `io.github.eslamasabry.opencode_mobile.LocalPdfService`
(a 40-line shell that calls `PdfPageRasterizer`), and the manifest entries of §15.2. `:android:media` is
**single-owner** for its build file, manifest fragment and `MediaBindings.kt` (the media lead). Work packages may own
separate packages inside it in parallel (§21); `AndroidAudioSession` is single-owner on its own (§5).

---

## 4. Contracts (frozen in `contracts/s3.md` for attachments and images, `contracts/s5.md` for the rest)

Rules for every signature below: commands are `suspend` and return `Outcome`; state is `StateFlow`; cancellation is
rethrown (never a `Failure`); no copy strings, only ids, enums and `Failure`; collections are `kotlinx.collections.immutable`.

### 4.1 `Failure.Media` (C10-1, `:core:common`)

```kotlin
public data class Media(val problem: MediaProblem, override val details: Redacted? = null) : Failure

/** Append-only. Each value has exactly one sentence (plus an optional fix label) in :ui:words `strings_media_failures.xml`. */
public enum class MediaProblem {
    // microphone and capture (MEDIA-VOICE-05, 15, 24)
    MicDenied, MicDeniedForever, MicBusy, MicSilenced, NoMicrophone, NoAudioCaptured, RecordingInterrupted, LeftTheApp,
    // recognizer (06)
    SpeechModelMissing, SpeechModelBroken, ChunkNotTranscribed,
    // packs and downloads (08, 09, 11, 14)
    PackAbiUnsupported, PackMemoryTooSmall, PackStorageTooSmall, MemoryUnknown, DownloadOffline, DownloadBusy,
    DownloadHttpsRequired, DownloadClosed, DownloadRedirect, DownloadUnsafeRedirect, DownloadNoResponse, DownloadTimedOut,
    DownloadChecksum, DownloadVerification, DownloadHttp, DownloadLength, DownloadIncomplete, DownloadNoSpace,
    // read aloud (19-24)
    ReadAloudUnsupported, NoOfflineVoice, SpeechEngineUnavailable, ReadAloudTooLong, ReadAloudBusy,
    // photos and attachments (26-34)
    PhotoTooLarge, PhotoUnsupported, PhotoStorage, PhotoPending, PhotoUnavailable, CameraDenied, CameraDeniedForever,
    AttachmentTooLarge, AttachmentsTotalTooLarge, DraftFull, AttachmentUnsupported, OfficeUnreadable, OfficeEmpty,
    ClipboardImageTooLarge, AttachmentContentMissing, RemoteAttachmentPreview,
    // previews (37-41)
    PdfEncrypted, PdfInvalid, PdfTooLarge, PdfNeedsAndroid10, PdfTimedOut, PdfFailed, SvgNotStatic, FileNotShowable,
    // save, share, open (44, 45)
    SaveFailed, NoAppToOpen, ShareUnavailable, WifiShareNoWifi, WifiSharePrivateFile, WifiShareUnavailable, WifiShareEnded,
    // camera (46, 48)
    NoCamera, CameraFailed,
}
```

`Redacted` details for media carry only `Redacted.shape(error, status)` (class name and HTTP status) or fixed
technical tokens such as `pdf:service_died`, `download:http_416`, `sherpa:load` — never paths, file names, URLs or
transcript text (§17).

### 4.2 Primitives in `:core:common` (C10-2)

```kotlin
/** Incremental SHA-256. Android/desktop actual: java.security.MessageDigest("SHA-256"). iOS: CommonCrypto (later). */
public expect class Sha256() {
    public fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size)
    public fun digestHex(): String          // lowercase hex, 64 chars; the object is spent afterwards
}
/** Raw DEFLATE (ZIP method 8). Android/desktop actual: java.util.zip.Inflater(nowrap = true). Throws only on bugs. */
public expect object RawInflate {
    /** null when the stream is corrupt, ends early, or would exceed [maxOutput] bytes (a zip bomb guard). */
    public fun inflate(input: ByteArray, offset: Int, length: Int, maxOutput: Int): ByteArray?
}
/** Android/desktop actual: java.security.SecureRandom. Used for the Wi-Fi share token (§13.1). */
public expect fun secureRandomBytes(count: Int): ByteArray
```

### 4.3 Shared types (C10-4: `:core:domain` and `:core:model`)

```kotlin
// :core:domain — results of ActivityResults (03 §10.4)
public data class PickedFile(
    val handle: String,            // opaque: a content:// URI or an app-owned absolute path; never shown, never logged
    val displayName: String?,      // OpenableColumns.DISPLAY_NAME, already reduced to a base name
    val declaredMime: String?,     // ContentResolver.getType, lowercased, parameters stripped
    val sizeBytes: Long?,          // OpenableColumns.SIZE; a hint only (the read is bounded anyway, MEDIA-VOICE-28)
)
public data class PhotoTarget(val captureId: String)          // file = files/camera-capture-v1/<captureId>.jpg
public data class DocumentTarget(val handle: String, val displayName: String?)
public sealed interface SharedFile {
    public data class Bytes(val name: String, val mime: String, val bytes: ByteArray) : SharedFile   // written to cache/share/ first
    public data class OnDisk(val path: String, val mime: String?) : SharedFile                     // phone file, already PathSafety-checked
}
/** Bounded access to picked documents and SAF targets. Impl: AndroidContentAccess (:android:platform). */
public interface ContentAccess {
    /** Reads at most [limit] + 1 bytes; Failure.Media(AttachmentTooLarge) when more exist (MEDIA-VOICE-28 trap). */
    public suspend fun readBounded(file: PickedFile, limit: Long): Outcome<ByteArray>
    /** Opens with mode "wt" (truncate) and streams; never "w" [U research §3: some providers do not truncate on "w"]. */
    public suspend fun write(target: DocumentTarget, bytes: ByteArray): Outcome<Unit>
    /** Keyboard- or paste-inserted content (§10.8); reads while the platform object in [content] is still referenced. */
    public suspend fun readInserted(content: InsertedContent, limit: Long): Outcome<ByteArray>
}
/** Same idea as PlatformViewHandle (03 §10.1): only :ui:kit-chat androidMain creates it, only :android:platform casts it. */
public interface PlatformContentHandle { public val native: Any }
public data class InsertedContent(val handle: PlatformContentHandle, val mime: String?)
public interface ExternalOpen {                                 // impl: :android:platform (port of openLocalFile)
    public fun openWithChooser(file: SharedFile.OnDisk): Outcome<Unit>     // Failure.Media(NoAppToOpen) when nothing can open it
    public fun installPackage(file: SharedFile.OnDisk): Outcome<Unit>      // system installer screen; never silent
}

// :core:model
@Serializable public sealed interface ImageRef {
    public val cacheKey: String                                  // includes the profile; used as Coil memory-cache key
    @Serializable public data class MessagePart(val conversation: ConversationRef, val messageId: MessageId, val partIndex: Int) : ImageRef
    @Serializable public data class ServerFile(val profileId: ProfileId, val path: String, val version: String?) : ImageRef
    @Serializable public data class ToolOutput(val conversation: ConversationRef, val callId: String, val path: String) : ImageRef
    @Serializable public data class DraftBlob(val conversation: ConversationRef, val blobSha256: String) : ImageRef
    @Serializable public data class AppFile(val area: String, val relative: String) : ImageRef   // camera capture, markup result
}
```

`cacheKey` = `"<variant>|<profileId>|<id fields joined by |>"`. A profile deletion calls `ImageResolver.evict(profileId)`,
which removes every memory-cache entry whose key contains that profile (registered as a `ProfileCloser`, 03 §11.2).

### 4.4 State types the UI renders (`:engine:media` public, `@Immutable` through `compose-stability.conf`)

```kotlin
public sealed interface VoiceState {
    public data object Idle : VoiceState
    public data object NeedsModel : VoiceState                                          // Flutter modelRequired
    public data class PreparingModel(val pack: PackId, val step: PrepStep) : VoiceState  // downloading / verifying / initializing / loading
    public data object Starting : VoiceState
    public data class Listening(val since: Instant, val mode: VoiceMode, val chunksDone: Int, val words: String) : VoiceState
    public data class Writing(val words: String) : VoiceState                            // Flutter transcribing after stop
    public data object Cancelling : VoiceState                                           // Flutter finishingCancellation
    public data class Done(val words: String, val endedBy: VoiceEnd) : VoiceState        // Flutter draft
    public data class Failed(val problem: MediaProblem, val wordsKept: String) : VoiceState
}
public sealed interface PrepStep { data class Downloading(val received: Long, val total: Long) : PrepStep; data object Verifying : PrepStep; data object Loading : PrepStep }
public enum class VoiceMode { Tap, Hold, Conversation, WidgetPanel }
public enum class VoiceEnd { Person, Silence, LeftApp, PageCovered, ScopeChanged, ChunkFailed }

/** What KitComposerVoice draws (MEDIA-VOICE-18). Built by the composer ViewModel from VoiceState + ConversationState. */
public enum class VoicePhase { Starting, Listening, Writing, WaitingReply, SpeakingReply, ReplyReady, Paused, MicDenied, Failed, SendingSoon }
```

The level is **not** part of `VoiceState`: `VoiceController.level: StateFlow<Float>` (0..1, the RMS of the newest 4096-byte
buffer, published at most every 33 ms) is read by `KitLevelMeter` in the draw phase, so 30 updates a second never
recompose the composer (PERF-4 parity).

### 4.5 Platform seams (`:engine:media`; Android impl in `:android:media` as `Android<Name>`; desktop binds `Unavailable<Name>`)

```kotlin
// ---- audio (single owner: AudioSession, §5)
public enum class AudioInterruption { FocusLost, OutputDeviceRemoved, OtherRecordingStarted, AppLeftForeground }
public interface AudioSession {
    /** Before any microphone start: stops reading aloud, arms the 10 s capture guard. false = playback would not stop. */
    public suspend fun beforeCapture(): Boolean
    /** Playback asks for focus (GAIN_TRANSIENT). Failed(ReadAloudBusy) while the guard or any recording is active. */
    public suspend fun acquirePlayback(): Outcome<PlaybackLease>
    public val interruptions: SharedFlow<AudioInterruption>
}
public interface PlaybackLease { public suspend fun release() }

public sealed interface CaptureEvent {
    public class Frame(public val pcm16le: ByteArray) : CaptureEvent      // 4096 bytes = 128 ms at 16 kHz mono
    public data object Silenced : CaptureEvent                            // AudioRecordingConfiguration.isClientSilenced
    public data object Interrupted : CaptureEvent                         // focus/route taken by a call etc.
}
public interface AudioCapture {
    /** Cold: collecting starts AudioRecord (MIC, 16 kHz, mono, PCM16); cancelling stops and releases it. */
    public fun frames(): Flow<CaptureEvent>
}

// ---- speech
public interface SpeechRecognizer {
    public suspend fun open(pack: InstalledPack, language: WhisperLanguage, threads: Int): Outcome<RecognizerSession>
}
public interface RecognizerSession {
    /** One ≤ 30 s chunk of 16 kHz float samples. Runs on the speech lane; cancellation stops waiting, the decode finishes. */
    public suspend fun transcribe(samples: FloatArray): Outcome<String>
    public suspend fun close()
}
public interface SpeechSynthesizer {                                      // Kokoro
    public suspend fun open(pack: InstalledPack, threads: Int): Outcome<SynthesisSession>
}
public interface SynthesisSession {
    public val sampleRate: Int                                            // must equal the pack's 24,000 or open() fails
    /** Calls [sink] with float samples as they are made; returns when the text is done. Cancel stops at the next callback. */
    public suspend fun synthesize(text: String, speakerId: Int, speed: Float, sink: suspend (FloatArray) -> Unit): Outcome<Unit>
    public suspend fun close()
}
public interface PcmPlayer {
    /** Plays float mono PCM from [chunks] until it is closed and every frame has played. Cancel = pause + flush at once. */
    public suspend fun play(sampleRate: Int, chunks: ReceiveChannel<FloatArray>): Outcome<Unit>
}
public data class TtsVoice(val id: String, val label: String, val localeTag: String)    // id = label = Voice.name (parity)
public interface SystemTts {
    public suspend fun voices(): Outcome<ImmutableList<TtsVoice>>         // offline voices only, sorted by name
    /** Suspends until spoken. Cancelling the caller stops it (operation-scoped by construction). */
    public suspend fun speak(text: String, voiceId: String?): Outcome<Unit>
}

// ---- device and network
public sealed interface DeviceProbeResult {
    public data class Known(val totalRamMb: Long?, val availableStorageBytes: Long?, val supportedAbis: ImmutableList<String>,
                            val hasMicrophone: Boolean, val lowRamDevice: Boolean, val memoryClassMb: Int?, val cpuCount: Int) : DeviceProbeResult
    public data object Unknown : DeviceProbeResult        // probe failed or timed out (5 s): capture assumed possible
    public data object Unsupported : DeviceProbeResult    // platform with no capture path (desktop now)
}
public interface DeviceProbe { public suspend fun probe(): DeviceProbeResult }
public enum class SetupNetwork { Unmetered, Metered, Offline, Unknown }
public interface SetupNetworkSource { public suspend fun current(): SetupNetwork }

// ---- images, clipboard, pdf, qr, wifi
public data class PreparedImage(val bytes: ByteArray, val mime: String, val width: Int, val height: Int)
public interface ImageCodec {
    public suspend fun preparePhoto(source: PickedFile, rules: PhotoRules): Outcome<PreparedImage>
    public suspend fun prepareBytes(bytes: ByteArray, declaredMime: String?, rules: PhotoRules): Outcome<PreparedImage>
}
public interface MarkupRaster {
    /** Replays [doc] at the source's own resolution; scales down until ≤ maxBytes (9 MiB). Always PNG. */
    public suspend fun render(source: ByteArray, doc: MarkupDocument, maxBytes: Int): Outcome<PreparedImage>
}
public data class ClipboardImage(val mime: String, val bytes: ByteArray)
public interface ClipboardImages {
    public suspend fun hasImage(): Boolean                                // clip description only: no system toast
    public suspend fun read(maxBytes: Int): Outcome<ClipboardImage?>      // Android 12+ shows its own toast here
}
public class PdfPageImage(public val png: ByteArray, public val width: Int, public val height: Int, public val pageCount: Int)
public interface PdfPageRenderer {
    /** One isolated-service request (bytes ≤ 10 MiB, page < 200). Deadline 12 s. */
    public suspend fun render(bytes: ByteArray, page: Int): Outcome<PdfPageImage>
}
public interface QrImageDecoder { public suspend fun decode(image: ByteArray): Outcome<String?> }   // built only if D10-3 = yes
public data class LanAddress(val ipv4: String)
public interface WifiAddressSource { public suspend fun current(): LanAddress? }
public interface LocalFileServer {
    /** Binds [address]:0, accepts until stopped; each request goes to [handler] (pure, in commonMain). */
    public suspend fun start(address: LanAddress, handler: ShareHttpHandler): Outcome<RunningServer>
}
public interface RunningServer { public val port: Int; public suspend fun stop() }
```

### 4.6 Engine services (what ViewModels call; all `@SingleIn(AppScope::class)`)

```kotlin
public interface VoiceController {
    public val state: StateFlow<VoiceState>
    public val level: StateFlow<Float>
    public suspend fun start(request: VoiceStart): Outcome<Unit>   // VoiceStart(scope: SpeechScope, mode: VoiceMode, stopOnSilence: Boolean)
    public suspend fun finish(): Outcome<String>                   // stop the mic, write the rest, return every word
    public suspend fun cancel(keepWords: Boolean)                  // Leave keeps, Cancel discards (two different calls in Flutter)
    public fun onHostChanged(host: VoiceHost)                      // Foreground | Inactive | Background | PageCovered | ScopeChanged
}
public interface ModelPacks {
    public val state: StateFlow<ModelPacksState>                   // per pack: Installed | Partial(bytes) | Missing | Unsupported(reason); selection; running job
    public suspend fun select(pack: PackId): Outcome<Unit>         // refused while downloading, verifying or loading
    public suspend fun setLanguage(choice: VoiceLanguage): Outcome<Unit>
    public suspend fun setAutoSend(on: Boolean): Outcome<Unit>
    public suspend fun download(pack: PackId, consent: DownloadConsent, replaceExisting: Boolean = false): Outcome<InstalledPack>
    public suspend fun cancelDownload()
    public suspend fun remove(pack: PackId): Outcome<Unit>          // deletes every file of the pack, partials included
    public suspend fun installed(pack: PackId): InstalledPack?      // stamp check only (M4)
}
/** A pack whose marker and stamp check passed (§7.2). file(role) = absolute path of "encoder" | "decoder" | "tokens" | "model" | "voices" | "lexicon". */
public data class InstalledPack(val spec: ModelPackSpec, val dir: kotlinx.io.files.Path) { public fun file(role: String): String }
/** Consent is per attempt and never stored (§7.7). attemptId ties it to one sheet so a cancelled consent cannot authorize a later start. */
public data class DownloadConsent(val attemptId: Long, val allowMetered: Boolean)
public data class PhotoRules(val maxEdgePx: Int, val jpegQuality: Int, val maxBytes: Int) {
    public companion object { public val Prompt: PhotoRules = PhotoRules(maxEdgePx = 2048, jpegQuality = 85, maxBytes = 10 * 1024 * 1024) }
}
public interface VoiceSetup { public fun begin(trigger: SetupTrigger): VoiceSetupRun }   // first-mic flow, §7.7
public interface ReadAloud {
    public val state: StateFlow<ReadAloudState>                    // Idle | Speaking(scope, utterance, engine) | Failed(problem)
    public suspend fun consentGiven(): Boolean
    public suspend fun giveConsent(): Outcome<Unit>
    public suspend fun read(request: ReadRequest): Outcome<Unit>   // ReadRequest(scope, utteranceId, markdown, voice: VoiceChoice)
    public suspend fun stop()
}
public interface AttachmentIntake {
    public suspend fun pickPhotos(target: ConversationRef, current: DraftAttachments): Outcome<IntakeResult>
    public suspend fun takePhoto(target: ConversationRef, current: DraftAttachments): Outcome<IntakeResult>
    public suspend fun pickFile(target: ConversationRef, current: DraftAttachments): Outcome<IntakeResult>
    public suspend fun fromKeyboard(target: ConversationRef, current: DraftAttachments, content: InsertedContent): Outcome<IntakeResult>
    public suspend fun fromClipboard(target: ConversationRef, current: DraftAttachments): Outcome<IntakeResult>
    public suspend fun fromBytes(target: ConversationRef, current: DraftAttachments, name: String, mime: String?, bytes: ByteArray): Outcome<IntakeResult>
}
public interface PendingPhotoRecovery { public suspend fun recover(): RecoveryResult }   // NothingPending | Attached | ProfileRemoved | RetryRequired
public interface FilePreview { public suspend fun prepare(name: String, declaredMime: String?, bytes: ByteArray, wantedLine: Int?): PreviewData }
public interface WifiShare {
    public val state: StateFlow<WifiShareState>                     // Off | Live(url, name, sizeBytes, downloads, endsAt) | Ended | Failed(problem)
    public suspend fun start(source: ShareSource): Outcome<Unit>    // stops the previous share first
    public suspend fun stop()
}
public interface ImageResolver { public suspend fun bytes(ref: ImageRef, maxBytes: Long): Outcome<ByteArray>; public fun evict(profile: ProfileId) }
```

---

## 5. Audio core: one `AudioSession` for capture and playback (MEDIA-VOICE-24)

**Single owner.** `AndroidAudioSession` (`:android:media/audio/`) is the only class that calls `requestAudioFocus`,
`registerAudioRecordingCallback` and `registerAudioDeviceCallback`. Capture, system TTS and the natural voice all go
through it. Port source: `ReadAloudBridge.kt` (`beforeMicrophoneCapture`, `micGuardUntil`, the callbacks).

### 5.1 Rules (each is a test in `AndroidAudioSessionTest`, Robolectric `ShadowAudioManager`)

| # | Rule | Exact behaviour |
|---|---|---|
| A1 | Capture beats playback | `beforeCapture()`: cancel the current playback with `ReadAloudBusy`; abort a pending TTS init; release the TTS engine if nothing was playing; set `guardUntil = now + 10 s`. Returns false only if playback could not be stopped (→ `MicBusy`, "Stop playback, check microphone settings, and try again"). |
| A2 | No playback during capture | `acquirePlayback()` fails `ReadAloudBusy` while `now < guardUntil`, or while `AudioManager.activeRecordingConfigurations` is non-empty (on any exception there, assume busy). |
| A3 | Focus | `AudioFocusRequest(AUDIOFOCUS_GAIN_TRANSIENT)`, attributes `USAGE_MEDIA` + `CONTENT_TYPE_SPEECH`, `setWillPauseWhenDucked(true)`. Any focus change other than `AUDIOFOCUS_GAIN` emits `FocusLost`, and the reader stops (never ducks into mush). Focus is abandoned on stop, finish and error. |
| A4 | Another recording | While a lease is held, an `AudioRecordingCallback` seeing any configuration that is not our own `AudioRecord` session emits `OtherRecordingStarted` → playback stops with `ReadAloudBusy`. |
| A5 | Output removed | `AudioDeviceCallback.onAudioDevicesRemoved` with an output device while a lease is held emits `OutputDeviceRemoved` (headphones unplugged) → playback stops (`Cancelled`, no failure note). |
| A6 | Foreground only | `AppLeftForeground` is emitted on the host activity's `ON_PAUSE` (parity: `MainActivity.onPause → readAloud.pause()`). Playback stops. Capture uses `ON_STOP` instead (§6.5). |
| A7 | Our own silencing | Capture registers its own `AudioRecordingCallback` **before** `startRecording()` and maps `AudioRecordingConfiguration.isClientSilenced() == true` (API 29+) to `CaptureEvent.Silenced` → the pill says "Another app is using the microphone. Recording paused." and the controller treats it as `RecordingInterrupted` after 2 s of silence (new: Flutter recorded silent garbage) [R research §8]. |

### 5.2 Android 15 and 17 audio rules

- Android 15+ (targetSdk ≥ 35): `requestAudioFocus` works only for the top app or a foreground service; A6 keeps
  playback foreground-only, so it always holds [V research §9].
- Android 17 background audio hardening applies to every app: background `AudioTrack.write` is silenced and focus
  fails [V research §9]. Nothing here plays in the background. The reply-watch `dataSync` FGS (ADR §9.5) never plays
  sound; notification sounds are the system's.
- **Emulator check (T3, API 37 image):** `adb shell cmd audio set-enable-hardening throw`, start reading a reply, press
  Home: reading stops, no crash, no `SecurityException` in logcat; return: the reply shows "Read it aloud" again.

---

## 6. Voice typing (MEDIA-VOICE-01…07, 15, 18)

### 6.1 Pure logic, ported first (commonMain, catalog rows in §20)

| Unit | Port from | Exact constants and rules |
|---|---|---|
| `Pcm16Chunker` | `lib/voice/audio.dart` `Pcm16Accumulator` | sample = `signed16 / 32768f`; an odd trailing byte is carried to the next buffer; `level` = RMS of the last buffer clamped 0..1; capacity **480,000 samples (30 s)**; `add(bytes)` returns how many bytes it took so a buffer crossing the edge starts the next chunk (B-MEDIA-014) |
| `SilenceDetector` | `lib/voice/silence.dart` | quiet that ends = **1,500 ms** after speech; speech level **0.015**; min loud time **300 ms**; quiet before speech never ends; speaking again resets quiet (B-MEDIA-015) |
| `isSendableTranscript` | same | remove `\[[^\]]*\]`, `\([^)]*\)`, `\*[^*]*\*`, `♪`, `♫`; then keep only `\p{L}\p{N}`; ≥ **2** code points (B-MEDIA-016). Kotlin: `Regex("[^\\p{L}\\p{N}]+")`, count with `codePointCount`. |
| `mayAutoSend` | same | `trimStart()`; refuse if it starts with `/` or `!`; else `isSendableTranscript` (B-MEDIA-016) |
| `DraftMerge.merge(existing, selStartUtf16, selEndUtf16, words)` | `controller.dart` `mergeVoiceDraft` | words trimmed; empty → existing unchanged; invalid selection → end; a space before only when the left neighbour is not whitespace, after only when the right neighbour is not whitespace (B-MEDIA-024) |
| `KitMicPress` | `kit_mic_hold.dart` `KitMicPress` | `holdAfter` **350 ms**, `slop` **18 dp**, `cancelDistance` **80 dp** (distance from the landing point, any direction, so RTL needs nothing); outcomes Tap / HoldCommitted / HoldCancelled / None; a system-taken touch while holding → HoldCancelled. Pure class in `:ui:kit-chat` commonMain (06 §7.8 places it there), unit-tested in kit-chat `commonTest` with a virtual clock; `KitMicHoldDetector` feeds it pointer events and reports outcomes up as callbacks. |
| Slip rule | `chat_voice.dart` `_finishHold` | a committed hold shorter than **400 ms** of recording is discarded |
| `LevelMeter.litFor(level)` | `kit_level_meter.dart` | **9** bars; bar *i* (0-based) lit when `level >= (i + 1) / 12` (B-MEDIA-003) |
| `VoiceLanguage` | `lib/voice/model_manifest.dart` / `presentation.dart` | 36 values: `auto` (Follow the app), `detect`, 34 named (en ar es fr de it pt nl ru uk pl cs ro el tr he fa ur hi bn ta zh ja ko vi th id ms sv no da fi hu sw); stored as the **enum name** (`auto`, `detect`, `english`, `arabic`, …); unknown → `auto`; folding `iw→he`, `nb`/`nn→no`, `in→id`; `auto` → app locale's Whisper code if Whisper knows it, else `detect` (B-MEDIA-028) |
| `voiceLanguageNote` | `presentation.dart` | the two notes of MEDIA-VOICE-07 (B-MEDIA-029) |
| `WhisperLanguage.code` | recognizer config | `detect` → `""`; named → its code (B-MEDIA-033) |

### 6.2 Capture: `AndroidAudioCapture` (`:android:media/audio/`)

```kotlin
override fun frames(): Flow<CaptureEvent> = callbackFlow {
    val minBuffer = AudioRecord.getMinBufferSize(16_000, CHANNEL_IN_MONO, ENCODING_PCM_16BIT)
    val record = AudioRecord.Builder()
        .setAudioSource(MediaRecorder.AudioSource.MIC)               // parity; 0.015 was tuned on MIC levels
        .setAudioFormat(AudioFormat.Builder().setSampleRate(16_000)
            .setChannelMask(CHANNEL_IN_MONO).setEncoding(ENCODING_PCM_16BIT).build())
        .setBufferSizeInBytes(maxOf(minBuffer, 16_384))
        .apply { if (Build.VERSION.SDK_INT >= 30) setPrivacySensitive(true) }
        .build()
    val silenced = session.watchOwnRecording(record.audioSessionId) { trySend(CaptureEvent.Silenced) }   // A7, before start
    record.startRecording()
    val reader = launch(micLane) {                                  // micLane = dispatchers.io.limitedParallelism(1)
        val buffer = ByteArray(4096)
        while (isActive) {
            val n = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
            if (n < 0) { trySend(CaptureEvent.Interrupted); break }
            if (n > 0) send(CaptureEvent.Frame(buffer.copyOf(n)))     // send, not trySend: audio frames are never dropped
        }
    }
    awaitClose {
        reader.cancel(); silenced.close()
        try { record.stop() } catch (_: IllegalStateException) { /* never started */ }
        record.release()
    }
}
```

- No Bluetooth SCO management (parity `manageBluetooth: false`). No `VOICE_RECOGNITION` source on day 1 (§22 P-M8 may
  measure it).
- `RECORD_AUDIO` is granted before `frames()` is collected (§6.8). `SecurityException` from `startRecording` maps to
  `MicDenied`; `IllegalStateException` (device busy) to `MicBusy`.
- The mic never opens from the background: `VoiceController.start` refuses unless the host is `Foreground` (§6.5).

### 6.3 Recognizer: `SherpaSpeechRecognizer` (`:android:media/speech/`)

```kotlin
private fun config(pack: InstalledPack, language: WhisperLanguage, threads: Int) = OfflineRecognizerConfig(
    modelConfig = OfflineModelConfig(
        whisper = OfflineWhisperModelConfig(encoder = pack.file("encoder"), decoder = pack.file("decoder"),
                                            language = language.code, task = "transcribe"),
        tokens = pack.file("tokens"), numThreads = threads, debug = false, provider = "cpu", modelType = "whisper"))

override suspend fun open(pack: InstalledPack, language: WhisperLanguage, threads: Int): Outcome<RecognizerSession> =
    withContext(speechLane) {                                       // dispatchers.io.limitedParallelism(1): one decode at a time, app-wide
        attempt({ Failure.Media(MediaProblem.SpeechModelBroken, Redacted.shape(it)) }) {
            SherpaSession(OfflineRecognizer(assetManager = null, config = config(pack, language, threads)))
        }
    }
// SherpaSession.transcribe(samples): createStream() → acceptWaveform(samples, 16_000) → decode(stream) →
//   getResult(stream).text.trim() → stream.release() in finally. close(): recognizer.release() on speechLane.
```

| Rule | Value |
|---|---|
| Threads | `threads = if (cpuCount >= 4) 2 else 1` (`conservativeVoiceThreadCount` parity) |
| Warm session (M2) | `VoiceController` keeps one `RecognizerSession` for (pack, language) while recording; closes it **20 s** after the recording ends, at once on `ComponentCallbacks2.onTrimMemory(TRIM_MEMORY_RUNNING_LOW or higher)`, on pack change, and on remove |
| Load overlap | capture starts at once; frames buffer in the chunker while the model loads, so the first words are never lost; the pill shows `Starting` until the session is open, then `Listening` |
| Ordered chunks | `ChunkQueue`: chunk *n* is transcribed only after chunk *n − 1*; results land in `words[n]`; the draft shows `words.joinToString(" ")` merged into the **original** draft base each time (never appended twice) |
| Chunk failure | stop capture; keep `words[0 until n]`; `Failed(ChunkNotTranscribed, wordsKept)` (B-MEDIA-010) |
| Load failure | full SHA re-check of the pack (M4); mismatch → pack state `Missing`, `SpeechModelBroken` with "Re-download" fix; match → `SpeechModelBroken` with Report a problem |
| Cancellation | the generation counter of Flutter becomes a child `Job` per recording; a late result from a cancelled job is dropped because its job is cancelled; a decode already inside JNI finishes on the lane, then its result is discarded (state `Cancelling` until then; B-MEDIA-023) |
| Memory | a warm `small` session holds ≈ 375 MB of weights plus activations [U]; §22 P-M2 measures peak RSS on NX721J and a 4 GB AVD before `small` is offered automatically |

### 6.4 `VoiceController` (`:engine:media/voice/`), step by step

`start(VoiceStart(scope, mode, stopOnSilence))`:

1. `PlatformCapabilities.voice == false` → `Failure.Unavailable(FeatureId("voice"))`.
2. State not `Idle`/`Done`/`Failed` → return `Ok` (duplicate taps are no-ops).
3. `readAloud.stop()`; `audioSession.beforeCapture()` false → `Failed(MicBusy)`.
4. `modelPacks.installed(selectedEligible)` null → state `NeedsModel`, return `Failed(SpeechModelMissing)`; the composer
   ViewModel then opens the first-mic setup (§7.7).
5. `permissionGate.request(Microphone)` (§6.8): `Denied` → `Failed(MicDenied)`; `PermanentlyDenied` →
   `Failed(MicDeniedForever)`. After a grant, call `beforeCapture()` again (parity: called "first and again on grant").
6. Host is not `Foreground` → `Failed(LeftTheApp)` ("Voice input was interrupted.").
7. State `Starting`; launch the recording job: open/reuse the session (async) and collect `capture.frames()`:
   - `Frame` → chunker; publish `level` (≤ 30 Hz); if `stopOnSilence`, feed `SilenceDetector` with the buffer's
     duration (16 kHz × 2 bytes = 32 bytes per ms, so a 4096-byte buffer is 128 ms); a full chunk → `ChunkQueue`.
   - `Silenced` → pill note; after 2 s continuous → treat as `Interrupted`.
   - `Interrupted` → stop with `RecordingInterrupted`, words kept.
   - silence detected → `finish()` with `VoiceEnd.Silence`.
8. A 100 ms ticker updates the elapsed clock (`since`), with **no cap** on duration (P10.3).

`finish()`: cancel the frame collector; wait ≤ **2 s** for the reader to stop; enqueue the final partial chunk if it
has any samples; state `Writing`; await the queue; zero chunks with zero samples → `Failed(NoAudioCaptured)`;
else `Done(words, endedBy)`.

`cancel(keepWords)`: cancel the recording job; state `Cancelling` while a decode is in JNI; then `Done(words,
endedBy)` if `keepWords` (Leave, Esc, lifecycle) else `Idle` (Cancel, slip, drag-to-cancel).

| Flutter `VoiceComposerState` | Native `VoiceState` | Test |
|---|---|---|
| modelRequired | `NeedsModel` | B-MEDIA-020 |
| downloading / verifying / initializing / loading | `PreparingModel(step)` | B-MEDIA-020 |
| idle | `Idle` | — |
| listening | `Listening` | B-MEDIA-010 |
| transcribing | `Writing` (after stop) or `Listening` with `chunksDone` rising (during) | B-MEDIA-010 |
| finishingCancellation | `Cancelling` | B-MEDIA-023 |
| draft | `Done` | B-MEDIA-024 |
| error | `Failed` | B-MEDIA-023 |

### 6.5 Lifecycle and guards (hard-won behaviour 7, 9, 31)

| Event (from the host screen) | Dictation | Read aloud |
|---|---|---|
| `ON_PAUSE` without `ON_STOP` (permission dialog, multi-window focus) — Flutter `inactive` | keeps recording | stops (A6) |
| Notification shade pulled down | keeps recording (no lifecycle event) | keeps reading |
| `ON_STOP` (app or overlay activity no longer visible) — Flutter `paused`/`hidden` | stops the mic at once, writes the words already recorded, `VoiceEnd.LeftApp` | stopped already |
| A page pushed over the chat (the chat entry is no longer the top Nav3 entry; the shell exposes `isTopEntry` to the screen, 07) | stops, words kept, `PageCovered` | stops |
| Speech scope changes (server, session, location revision, base URL, credentials, profile readability) | stops, words kept, `ScopeChanged` | stops, forgets the chosen voice id |
| Return from Android settings opened by "Allow microphone in Android settings" | not a "leave": on `ON_START` the controller retries the permission once | — |

The chat screen feeds these through `LifecycleEventEffect` (allowlisted `androidx.lifecycle.*`) into
`actions.hostChanged(VoiceHost)`; the ViewModel forwards an enum, never a platform type.

### 6.6 Hold to talk (MEDIA-VOICE-02) and its UI

- `KitMicHoldDetector` (`:ui:kit-chat`): `Modifier.pointerInput { awaitEachGesture { … } }` feeds down, move (in dp),
  up and cancel to its `KitMicPress`, and calls `onTap`, `onHoldStart`, `onArmedChanged(armed)` and
  `onHoldEnd(cancelled)`; the ViewModel maps them to `VoiceController.start(mode = Hold)`, `finish()` and
  `cancel(keepWords = false)`.
- Haptics through `KitHaptic` (06 §12.2): `Commit` when the hold starts, `Select` each time the finger crosses the
  80 dp line in or out, `Send` on a committed release. Nothing on a cancelled release.
- Words while holding: "Release to send" / "Release to cancel" (frame text, 1.3× cap).
- Early release: a lift while the permission prompt or setup is still in progress is remembered and applied the
  moment listening starts (`Cancelled` → cancel; `Finished` → finish). A setup sheet appearing in between turns the
  session into a tap recording (`mode = Tap`).
- **A hold never auto-sends and never stops on silence:** `stopOnSilence = mode == Tap && settings.autoSend` (the
  conversation mode never stops on silence either; Flutter `byTap = !pushToTalk && !_voiceConversation`).
- **Teaching (owner rule):** tip id `hold-mic`, anchor test tag `composer-voice-button`; offered after a tap recording
  once the box is empty again; marked used when the first hold starts; at most twice, 2-minute cool-down; replayable
  from Help › Tips; registered with `TeachingRegistry` (`@ContributesIntoSet`).

### 6.7 Send when I stop talking (MEDIA-VOICE-03)

1. Setting `voice.auto_send` (bool, default false, Settings › Voice typing, switch test tag `voice-auto-send`).
2. Only a tap dictation with the setting on stops by silence (§6.6). Then the composer shows `SendingSoon` with the
   words and a **3 s** countdown ("Sending in 3…", Cancel, Send now); the countdown ticks in the pill, not in the VM.
3. At zero, or on Send now, the ViewModel sends **only if** the field text equals the text captured when the countdown
   began **and** `mayAutoSend(text)`, the app is in the foreground, and no send is in flight. Anything else cancels the
   countdown and leaves the words in the field.
4. It goes through the one send path (`PromptDelivery`, 09) "as if typed", so steering rules apply unchanged.

### 6.8 Microphone permission (MEDIA-VOICE-15, 48)

`PermissionGate` (03 §10.4, implemented in `:android:platform`) owns the three-state answer for `Microphone` and
`Camera`, ported from `MainActivityMedia.kt`:

| Step | Rule |
|---|---|
| Already granted | `Granted` (no prompt) |
| Asked before (`voice_permissions.microphone_requested == true`) and no rationale to show | `PermanentlyDenied` without prompting |
| Prompt | `RECORD_AUDIO`, plus `POST_NOTIFICATIONS` in the same request on API 33+ when not granted (so the download notification can show) |
| After the result | granted → `Granted`; not granted and no rationale → `PermanentlyDenied`; else `Denied`; write `microphone_requested = true` |
| Concurrent request | the second caller awaits the first answer (Flutter returned `permission_in_progress`; awaiting is simpler and safe) |
| Off Android | `PermanentlyDenied` for the microphone, never a claimed grant (B-MEDIA-032, commit `883a5eb69`) |

The prefs files `voice_permissions` (`microphone_requested`) and `camera_permissions` (`camera_requested`) keep their
names and keys (05 Appendix D); both are read and written only by `AndroidPermissionGate` through `KotlinPrefsFile`.

Recovery copy in voice mode (`:ui:words` `strings_chat_composer.xml`, ported from `voiceMode*`):

- `MicDenied`: "Voice typing needs the microphone. Tap Allow microphone, then choose Allow." Fix: **Allow microphone**
  (asks again).
- `MicDeniedForever`: "Android blocks the microphone for this app. Turn it on in Android settings, then come back
  here." Fix: **Allow microphone in Android settings** (`openSettings`; coming back retries once, §6.5).

### 6.9 The voice pill (`KitComposerVoice`, MEDIA-VOICE-18) — what the kit draws

| Phase | Leading | Middle | Trailing (send slot) |
|---|---|---|---|
| Starting | Leave (×) | "Getting the microphone ready…" | disabled Done |
| Listening (dictation) | Leave (×) | level meter (9 bars) + "Listening…" + `m:ss` (no cap) | **Done** (accent circle) |
| Listening (conversation) | Leave (×) | same + "Read replies aloud" chip | **Send** (accent circle) |
| Writing | Leave (×) | "Writing down what you said…" | waiting ring |
| SendingSoon | Cancel | words + "Sending in 3…" | **Send now** |
| WaitingReply / SpeakingReply / ReplyReady / Paused | see §8.6 | | |
| MicDenied / Failed | Leave (×) | one plain sentence | the fix button (`voice-mode-allow-mic`, `voice-mode-open-settings`, `voice-mode-try-again`) |

Kit rules: frame text (1.3× cap); the level meter is decorative (`clearAndSetSemantics {}`), reads `level` in
`drawBehind`, uses `KitMaxWidth.reading`, and under `MotionPolicy.Off` draws static bars at rest (the words say
"Listening…"); phase changes cross-fade in 150–200 ms (`KitMotion.standard`), none under reduced motion. Test tags:
`voice-mode`, `voice-mode-try-again`, `voice-mode-open-settings`, `voice-mode-allow-mic`.

### 6.10 The widget voice panel

`WidgetPanel.Steer(widgetId, voice = true)` (07 M03) starts dictation at once in `WidgetOverlayActivity` with
`mode = WidgetPanel`. The same `VoiceController` (App scope) serves it; the permission prompt attaches to the overlay
activity (C10-5). The panel's `ON_STOP` ends the recording like the chat's. If no pack is installed, the panel says
"Set up voice typing in the app first" with an **Open the app** button (never a download from the overlay).

---

## 7. Model packs, downloads and setup (MEDIA-VOICE-08…14, 16, 17, 22)

### 7.1 The catalog (`ModelCatalog.kt`; copied byte for byte; B-MEDIA-031 checks every value)

URL rule: `https://huggingface.co/<repo>/resolve/<revision>/<file>`. File order is fixed. `.int8.onnx` everywhere.

| Pack id | UI word | Repo | Revision | Files: bytes, SHA-256 | Total | Min total RAM |
|---|---|---|---|---|---|---|
| `base` (default) | Balanced ("Recommended") | `csukuangfj/sherpa-onnx-whisper-base` | `bb53ee204431c90d314c1cc08d28d23e5b7927cc` | `base-encoder.int8.onnx` 29,120,534 `0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11`; `base-decoder.int8.onnx` 130,672,026 `9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d`; `base-tokens.txt` 816,730 `b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126` | 160,609,290 | 1536 MB |
| `small` | High accuracy | `csukuangfj/sherpa-onnx-whisper-small` | `8f3c18b358db4d1f2fc1eae49d75cd20989e4309` | `small-encoder.int8.onnx` 112,442,483 `4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9`; `small-decoder.int8.onnx` 262,226,114 `acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee`; `small-tokens.txt` 816,730 (same hash as base) | 375,485,327 | 3400 MB |
| `tiny` | Compact fallback | `csukuangfj/sherpa-onnx-whisper-tiny` | `65176e2deb88badc814a94058666cadccc29b61c` | `tiny-encoder.int8.onnx` 12,937,772 `d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434`; `tiny-decoder.int8.onnx` 89,855,401 `d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925`; `tiny-tokens.txt` 816,730 (same hash) | 103,609,903 | 1024 MB |
| `kokoro-en` | Natural voice | `csukuangfj/kokoro-int8-multi-lang-v1_0` | `57adb37fc0e8d4bd430cfc1b8f8c2294945b9af2` | `model.int8.onnx` 114,202,993 `75635ea3b85a32d00094dcdd61a258aba87c160f7c264c304a7b3ae9d734195e`; `voices.bin` 28,200,960 `1c5a5b983d3d50d8586d437a51f3faa2da7919ce76a013c081e65671a3447c29`; `tokens.txt` 687 `6ebb6bb288f20f3ae8d004d3c2ca27697da27c037d75e81a60e2a6a663f95425`; `lexicon-us-en.txt` 5,956,885 `7daaab53a181be9885b853a8582bf1838186317e5dadacbcef9c426d6fa0da14` | 148,361,525 | **2000 MB, enforced (M5)** |

Kokoro extras: speaker id **3** (`af_heart`), output **24,000 Hz** (open fails otherwise), languages `{en}`, config
`OfflineTtsKokoroModelConfig(model, voices, tokens, lexicon)` with `lang` empty, `maxNumSentences = 1`.

```kotlin
public enum class PackKind { Speech, NaturalVoice /* later: Assistant (Gemma, after the switch) */ }
@JvmInline public value class PackId(public val raw: String)          // "base" | "small" | "tiny" | "kokoro-en"
public data class ModelFile(val name: String, val bytes: Long, val sha256: String)
public data class ModelPackSpec(val id: PackId, val kind: PackKind, val repo: String, val revision: String,
                                val files: ImmutableList<ModelFile>, val minTotalRamMb: Long) {
    val totalBytes: Long get() = files.sumOf { it.bytes }
    val dirName: String get() = "${id.raw}-$revision"
}
```

The same `ModelPacks` machinery takes the Gemma assistant pack after the switch (02 §3.5) with a new `PackKind`; do not
build a second downloader (map §12 risk 15).

### 7.2 On-disk layout (unchanged from Flutter; never moved, ADR §9.7)

```
files/voice_models/<packId>-<revision>/<file>…        the model files
files/voice_models/<packId>-<revision>/.installed.json {"pack":id,"revision":rev,"files":[{"name","length","sha256"}]}  — Flutter's shape, written exactly so
files/voice_models/<packId>-<revision>/.native-verified.json  NEW (M4): {"v":1,"files":[{"name","length","mtimeMs"}],"verifiedAtMs":…}
files/voice_models/<packId>-<revision>.installing/<file>.part   staging (resume source)
files/voice_models/<packId>-<revision>/<file>.voice-backup     during a replace only
files/voice_models/<packId>-<revision>/.installed.json.tmp     during the atomic marker write
```

The root comes from `BlobStore.path(BlobArea.VoiceModels)` (03 §10.5). Flutter's `getApplicationSupportDirectory()` is
`Context.filesDir` on Android [U research §0 item 5]; §22 P-M6 proves it on the emulator with a Flutter-installed pack
before MV-6 merges. If it differs, the first-launch migration moves the folder once (never a re-download).

`installed(pack)` = marker exists and matches id + revision **and** the stamp matches every file's length and mtime.
If the stamp is missing or stale: full SHA-256 pass on `dispatchers.io` with `Sha256` (64 KiB reads), write the stamp,
then report installed. A mismatching file deletes the marker and the stamp (B-MEDIA-025 parity).

### 7.3 Support check and automatic choice (`PackSupport`, `AutomaticPack`; B-MEDIA-002, 011, 024)

```
supportFor(pack, device):
  device == Unsupported                      → Unsupported(platform)
  abis non-empty and none of {arm64-v8a, armeabi-v7a, x86, x86_64} → Unsupported(PackAbiUnsupported)
  totalRamMb != null and totalRamMb < pack.minTotalRamMb → Unsupported(PackMemoryTooSmall)
  not installed and availableStorage != null and availableStorage < size + max(128 MiB, size / 4) → Unsupported(PackStorageTooSmall)
  else Supported
  (totalRamMb == null blocks nothing here: the person may try a manual pick)

automaticPack(selected, device):
  device unknown, or totalRamMb == null or 0 → none (MemoryUnknown: "Choose a speech model")
  selected installed and eligible            → selected        (eligible = Supported, or only short of storage)
  else the eligible pack with the highest minTotalRamMb (small > base > tiny); storage never changes the choice
```

The gate is **total RAM** (`ActivityManager.MemoryInfo.totalMem / 1 MiB`), never `memoryClass` (memory
`voice-model-memory-gate`; a test fails if the gate reads `memoryClassMb`). `memoryClassMb` is kept for diagnostics.

`AndroidDeviceProbe` (5 s timeout → `Unknown`): `availableStorageBytes = StatFs(filesDir).availableBytes`,
`totalRamMb`, `lowRamDevice = ActivityManager.isLowRamDevice`, `supportedAbis = Build.SUPPORTED_ABIS`,
`hasMicrophone = PackageManager.FEATURE_MICROPHONE`, `cpuCount = Runtime.availableProcessors()`.

### 7.4 Downloading one pack (`PackInstaller` over `:core:net` `Downloader`)

Per-file request (C10-7 lists the `DownloadRequest` options the `:core:net` owner adds):

| Rule | Value |
|---|---|
| Scheme | HTTPS only; redirects followed **manually**, at most **5**, every hop must be `https` (else `DownloadUnsafeRedirect`) |
| Headers | `User-Agent: OpenCode-Mobile/voice-model`; never credentials or cookies |
| Timeouts | connect 15 s; each step (connect, first byte, redirect) 15 s; body idle 20 s |
| Resume | `.part` of length *k* > 0 → `Range: bytes=k-`; accept **206 only if `Content-Range` equals `bytes k-(len-1)/len` exactly**; **200** → restart from zero; anything else → `DownloadHttp` |
| Length | `Content-Length` must equal the remaining pinned bytes (else `DownloadLength`); a body longer than the pin aborts |
| Already complete | `.part` length == pinned length → no request; go to verify |
| Verify | SHA-256 + length of each staged file on `dispatchers.io`, cancellable; mismatch deletes that `.part` → `DownloadChecksum` |
| Progress | throttled to ≥ 100 ms and ≥ 1 % steps (the UI and the notification never see every chunk) |

Pack algorithm (one `Mutex` per pack directory, app-wide, so same-pack installs and removes serialize; B-MEDIA-027):

1. Preflight: `DeviceProbe` → `supportFor`; refused → `Failure.Media(reason)` (no network used).
2. For each file in order: download into `<dir>.installing/<name>.part`.
3. Verify all staged files; then a second verification pass right before publishing (`DownloadVerification`).
4. Publish file by file: an existing target is first renamed to `<target>.voice-backup`; the staged file is
   `atomicMove`d into place. On any failure: delete the newly published files, rename the backups back, keep the old
   verified model ("Re-download keeps the verified old model on failure").
5. Write the marker atomically (`.installed.json.tmp` → rename) with Flutter's exact JSON shape; write the stamp.
6. Delete backups and the staging directory.

Cancel (M3): during step 2 the `.part` files **stay** (the next attempt resumes); during steps 3–5 the staged files
are deleted and the backups restored. `remove(pack)` waits for the pack's mutex, then deletes the pack directory, the
staging directory, every `.part`, `.voice-backup` and `.tmp`; if the removed pack was the read-aloud choice,
`voice.read_aloud_natural` becomes false. Remove is refused while that pack downloads (`DownloadBusy`, B-MEDIA-013).

| Flutter `VoiceDownloadFailure` | `MediaProblem` |
|---|---|
| unknown | `DownloadIncomplete` with `Redacted` shape |
| https | `DownloadHttpsRequired` |
| closed | `DownloadClosed` |
| redirect | `DownloadRedirect` |
| unsafeRedirect | `DownloadUnsafeRedirect` |
| noResponse | `DownloadNoResponse` |
| timeout | `DownloadTimedOut` |
| checksum | `DownloadChecksum` |
| verification | `DownloadVerification` |
| http | `DownloadHttp` (status in `Redacted`) |
| length | `DownloadLength` |
| incomplete | `DownloadIncomplete` |
| errno 28 / `ENOSPC` (`IOException` with "No space") | `DownloadNoSpace` |

### 7.5 The download notification (behaviour of `VoiceDownloadNotifications.kt`, posted by `:android:background`)

C10-12 (to section 11): `NotificationSurface` gets `showModelDownload(phase: DownloadPhase, received: Long, total: Long):
Boolean` and `dismissModelDownload()`. Rules to keep exactly:

| Item | Value |
|---|---|
| Channel | id `opencode_voice_setup` (frozen, ADR §9.9), name from `strings_os.xml` `native_voice_setup`, `IMPORTANCE_LOW` |
| Notification id | `0x701CE` |
| Phases | `Downloading` (determinate %), `Verifying` (indeterminate), `Complete`, `Failed` |
| Copy | only the fixed `strings_os.xml` strings `native_voice_downloading/preparing/ready/failed` (en + ar + the 5 others); **no** file names, sizes in text, URLs or errors |
| Flags | small icon `android.R.drawable.stat_sys_download`; `setOnlyAlertOnce(true)`; ongoing for Downloading/Verifying; auto-cancel otherwise; `VISIBILITY_PRIVATE`; `CATEGORY_PROGRESS`; tap opens `MainActivity` (`FLAG_IMMUTABLE`, `CLEAR_TOP or SINGLE_TOP`) |
| Self-expiry | `setTimeoutAfter(15 min)`, renewed by every update (a killed process never leaves an ongoing row) |
| Ordering | updates go through one `Mutex` + "latest wins" conflation per `phase:percent`, so a queued progress post can never overtake Complete or a dismissal (B-ANDR-102) |
| Not a service | the notification does not keep the download alive (parity; D10-4) |
| Permission | `POST_NOTIFICATIONS` requested with the mic (§6.8) or by the setup sheet; a denial never blocks setup; the sheet says "also shows in the notification" **only** when the post returned true |

### 7.6 Network class for consent (`AndroidSetupNetwork`, B-ANDR-101)

No `ConnectivityManager` → `Unknown`; no active network → `Offline`; no `NET_CAPABILITY_INTERNET` → `Offline`; not
`VALIDATED` → `Unknown`; **cellular transport → `Metered` always** (even on an unmetered plan); `isActiveNetworkMetered`
→ `Metered`; **VPN → `Unknown`**; else `Unmetered`. 5 s timeout → `Unknown`.

### 7.7 First-mic automatic setup (`VoiceSetup`, MEDIA-VOICE-11; B-MEDIA-019…021)

Started by the composer when `VoiceController.start` returns `SpeechModelMissing`. Its state is a sealed
`VoiceSetupStage`; the sheet (`:feature:chat-composer`, kit `KitSheetHost`) only renders it.

| Stage | Shown | Next |
|---|---|---|
| `Checking` | progress | probe device → `automaticPack`; none → `Problem(MemoryUnknown or the smallest pack's reason)` |
| (installed) | — | start listening; hand off |
| `NeedsConsent(pack, network)` | "This phone's memory picked Balanced"; primary **Download 161 MB** or **Download 161 MB on mobile data**; a notice on mobile/unknown; Details fold (file names, exact MiB, the memory rule — the only place they appear); tertiary **Choose another speech model** (opens `SheetKey.VoiceModelSetup`) | consent is **per attempt, never stored**; `Metered`/`Unknown` need `allowMetered = true`; `Offline` → `Problem(DownloadOffline)` with Try again |
| `AskingNotifications` | system prompt | **re-read the network after the prompt**; if it changed to Metered/Unknown/Offline, go back to `NeedsConsent`/`Problem` |
| `Downloading(received, total, notified)` | plain MB bar ("Downloading 48 MB of 161 MB"), **Cancel download** | a network change to Metered mid-download pauses and asks again (E2E-13 step 2) |
| `Verifying` | waiting bar, no Cancel | → `Ready` or `Problem(DownloadChecksum)` |
| `Ready` | "The speech model is on this phone" + **Start listening** | in the foreground: start the mic and `handOff()`; if the app is in the background when it finishes: stay at `Ready` — **never open the mic from the background** |
| `Problem(kind)` | plain words + fix: Try again / Show the download (busy) / Allow microphone in Android settings / Choose a speech model | — |

- `handOff()` transfers the recording to the composer's own voice surface, so closing or cancelling the setup no
  longer cancels the recording (B-MEDIA-021).
- **Closing the sheet cancels the download** (the `.part` files stay, M3); leaving the app does not.
- A download already started from Settings is `DownloadBusy` here with **Show the download**.
- Creating the setup without a mic tap does no work (no probe, no network) (B-MEDIA-019).

### 7.8 Settings › Voice typing (`SettingsRoute.Voice`) and the model sheet (`SheetKey.VoiceModelSetup`)

Gated by `PlatformCapabilities.voice` (the route is absent, never disabled, where false). Top to bottom:

1. A running download: title, `KitProgressView` ("48 MB of 161 MB" or "Verifying"), **Cancel download**.
2. A failure notice with a Details fold (redacted).
3. Privacy line with a lock glyph: "Audio never leaves this phone."
4. No-microphone notice when `hasMicrophone == false`.
5. **Speech model** single-choice list (`actsOnTap = false`: a tap only chooses). Rows: "Installed" or
   "Not downloaded · 161 MB" + description; unsupported rows are dimmed **with the reason** in the row. For the selected
   installed pack: **Re-download Balanced** and **Delete Balanced (160 MB)** (destructive, confirm sheet naming pack
   and size).
6. Pinned primary that follows the manager: **Download Balanced (161 MB)** → nothing while busy → **Use Balanced**
   once a download here finishes → nothing (secondary becomes **Done**) when the selected pack is installed.
7. **Language** row (`voice-model-language`) → choice sheet (`voice-language-<name>` tags); names in their own script.
8. **Send when I stop talking** switch (`voice-auto-send`) with its detail line.
9. **Read-aloud voice** section, only when `PlatformCapabilities.readAloud`: **Phone's voice** (default) / **Natural voice
   (download, about 148 MB)** → "Natural voice" once installed; choosing it when missing downloads then selects (same
   consent rules); **Remove natural voice (148 MB)** is a destructive confirm.

A download keeps running in `ModelPacks` when the page or sheet closes; reopening shows its progress. Sizes in words are
decimal MB/GB ("160 MB", "1.2 GB", `voiceSizeText` parity); exact MiB only under Details.

### 7.9 Phone-setup voice component (MEDIA-VOICE-14; B-LINUX-220)

`VoiceSetupComponent` implements the phone-setup engine's `SetupAppComponent` (owner: `:engine:runtime`, 03 §11.2;
this section provides the class in `:engine:media` and contributes it with `@ContributesIntoSet`):

- Offered on the Customize sheet and "Add tools", **off by default**; `offer()` uses `automaticPack` (unknown RAM → no
  offer); `downloadSize` is exact (0 when already installed).
- `install` refuses while Settings is downloading (`DownloadBusy`); `remove()` deletes **every** pack including
  partials; `failureOf` maps to `SetupAppFailureKind` (`noSpace` for `DownloadNoSpace`, `offline` for network classes
  and timeouts, `checksum`, `unsupported`, `failed`).
- Not part of Linux: the model lives in app storage and the same `ModelPacks` fetches it, so setup and Settings agree.
  During phone setup the download runs under the setup's own foreground service (`ForegroundKind.Setup`).

---

## 8. Read aloud and the voice conversation (MEDIA-VOICE-04, 19…24)

### 8.1 Consent (once per phone)

- Stored `oc.readAloudConsent` (bool, app-wide, owner `:engine:media`; 05 Appendix A). The speech engine is **never
  touched** before consent (B-MEDIA-009: construction and disposal without playback never access the engine).
- Kit confirm sheet "Read replies aloud?": body "Your phone's speech engine reads the reply aloud. Code and tool
  details are skipped. This phone remembers your answer." Consequence lines: "Only offline voices are offered, but the
  speech engine is separate software with its own privacy terms." and "People near you may hear it. Reading stops
  when you leave this conversation or the app." Button **Read aloud**.

### 8.2 Choosing the voice (`VoicePick`, B-MEDIA-018)

1. Settings choice = natural **and** `kokoro-en` installed **and** the text routes to `en` (§8.5) → natural voice.
2. Else the phone's offline voices from `SystemTts.voices()`.
3. Pick by the app locale: exact language + region, then same language, else none (engine default). Locale tags accept
   `_` or `-` in any case.
4. Only when no voice speaks the app's language, or for **Read with another voice**, show "Choose a reading voice"
   (`read-aloud-voices`, items `read-aloud-voice-<id>`, current one checked; a tap chooses and closes). The chosen id is
   remembered in the chat ViewModel only, and cleared on a scope change.

### 8.3 What gets spoken (MEDIA-VOICE-20)

`MarkdownProse.forSpeech(markdown)` lives in `:core:markdown` (owner: chat lead; this section owns its tests' list).
Input > **131,072** chars → refused; fenced code (``` or ~~~, matching or longer closing fence) skipped whole; rules
and table delimiter rows skipped; heading markers, `>`, bullets and `1.` numbers removed; an image becomes its alt
text; inline code, emphasis and links become their visible text (link targets never spoken; a bare URL is read as
written); output > **12,000** chars → `ReadAloudTooLong`. Only the message's **text parts** are read, never tool parts
and never copy/export text. In a conversation, the prose of every assistant message of the turn is joined with blank
lines.

### 8.4 System voice: `AndroidSystemTts` (port of `ReadAloudBridge.kt`)

| Rule | Value |
|---|---|
| Engine | `TextToSpeech` created lazily on the first `voices()`/`speak()`; init timeout **8 s**; a second request during init → `ReadAloudBusy`; kept for later; shut down on dispose, on init failure, and by `beforeCapture()` when nothing plays |
| Package visibility | manifest `<queries><intent><action android:name="android.intent.action.TTS_SERVICE"/></intent></queries>` (without it Android 11+ hides the engines) |
| Offline only | `!voice.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features`; sorted by name; id = label = `Voice.name`; `localeTag = voice.locale.toLanguageTag()`; empty → `NoOfflineVoice` |
| No voice id given | default voice → `Locale.getDefault()` match → same language → first |
| Chunks | ≤ `min(TextToSpeech.getMaxSpeechInputLength(), 3000)` UTF-16 units, never splitting a surrogate pair; spoken one after another with `QUEUE_FLUSH` (each `onDone` enqueues the next) |
| Watchdog | **300 s** per chunk → `SpeechEngineUnavailable` |
| Focus and interruptions | through `AudioSession` (§5): lease before the first chunk; any interruption → stop |
| Limits | blank or > **12,000** UTF-16 units → `ReadAloudTooLong` |
| Privacy | text is never logged or kept; pending closures that hold text are released on cancel |
| Unknown terminal status | treated as `SpeechEngineUnavailable` and the operation stops (the UI never stays "speaking") |
| Threading | `TextToSpeech` calls on the main thread (`dispatchers.main`); `UtteranceProgressListener` callbacks resume the suspended `speak` |

### 8.5 Natural voice (Kokoro) (MEDIA-VOICE-22; B-MEDIA-005)

- **Language routing** (`ReadLanguage.detect(text, appLanguage)`): count code points in Arabic
  (0600–06FF, 0750–077F, FB50–FDFF, FE70–FEFF), Latin (A–Z, a–z, 00C0–024F) and other scripts (0370–05FF, 0700–074F,
  0780–1FFF, 3040–D7FF, FF00–FFEF). None → the app language. Arabic ≥ Latin and ≥ other → `ar`. Other > Latin → `other`.
  Latin wins → the app language if it is written in Latin letters, else `en` (non-Latin app languages: ar fa ur he iw ru
  uk el hi bn ta zh ja ko th). The installed pack that speaks the result reads it; else the phone's voice reads and
  the plain note `naturalVoiceFallbackNote` shows **once per language** per app run.
- **Arabic is not offered** (sherpa-onnx 2.0 removed espeak-ng; D10-9).
- **Synthesis** (`SherpaSpeechSynthesizer`, `speechLane`): `OfflineTts(null, OfflineTtsConfig(model =
  OfflineTtsModelConfig(kokoro = …, numThreads = threads, provider = "cpu"), maxNumSentences = 1))`; fail if
  `sampleRate() != 24_000`; split with `SentenceSplitter` (regex `[^.!?\n]+[.!?]*\s*|\n+`, then cut pieces longer than
  **280** chars at the last space before 280, else at 280); for each piece
  `generateWithCallback(piece, sid = 3, speed = 1f) { samples -> if (job.isActive) { chunks.trySendBlocking(samples); 1 } else 0 }`
  [U: the "0 stops" semantics, proof P-M4]; `free()` on close.
- **Playback** (`AndroidPcmPlayer`): `AudioTrack.Builder()` with `ENCODING_PCM_FLOAT`, mono, 24,000 Hz, `MODE_STREAM`,
  buffer `max(minBuffer, sampleRate * 4)` bytes, attributes `USAGE_MEDIA`/`CONTENT_TYPE_SPEECH`; writes
  `WRITE_NON_BLOCKING` with `delay(15)` when the track is full (cancellable, never stuck in native code); after the
  channel closes, poll `playbackHeadPosition` every 100 ms until it reaches the frames written → `Ok`. Cancel =
  `pause()` + `flush()` + `release()` at once.
- **Watchdogs:** first sound within **90 s** (the model load can take seconds), then queued duration + 90 s.
- **Threads:** 2 when `cpuCount >= 4`, else 1. The synthesis session is closed 20 s after the last reading.
- `voice.read_aloud_natural` (bool, default false) stores the Settings choice; removing the pack resets it.

### 8.6 Ownership, scope, timeouts (MEDIA-VOICE-23)

- **One reader app-wide.** `ReadAloud.read()` cancels the previous reading's job before starting (structured
  concurrency replaces Flutter's `_owner` and operation ids; a stale stop cannot stop a newer reading because it holds
  an old `Job`).
- **Foreground only:** refused with `ReadAloudBusy` unless the host is resumed; `AudioInterruption.AppLeftForeground`
  stops it (§5 A6).
- **Scope:** a reading carries the `SpeechScope` (sessionId, profileId, location revision, base URL, username,
  password fingerprint, profile readable, session known). Any change stops it, clears the chosen voice and revokes
  "read replies aloud" in a conversation.
- **Timeouts:** voices 12 s, start 12 s, stop 3 s (stop errors are swallowed; the platform stops independently).
- **Composer note:** each `ReadAloud` failure shows once (the ViewModel remembers the last problem shown).
- **Menu:** "Read reply prose" (`message-menu-read-aloud`) and, after consent, "Read with another voice"
  (`message-menu-read-aloud-voice`) appear only when: not isolated, `readAloud` capability, not opening voice, not in a
  conversation that cannot send, no request in flight, profile readable, role assistant, prose non-empty. "Stop reading
  aloud" in the top bar while speaking (07 C25).

### 8.7 Voice conversation (MEDIA-VOICE-04) — `VoiceConversation` + `ReplyWatch` (pure state machines)

Entry: "+" › More › **Voice conversation** (only when `voice && readAloud`); while the agent works the row stays in place
with "available when the current run finishes"; refused with a note if the box has text, attachments or review
references, or the connection cannot send.

| Conversation state | Pill (`VoicePhase`) | Primary | Exit to |
|---|---|---|---|
| Listening | Listening + "Read replies aloud" chip | **Send** | Sending |
| Sending | Writing | — | WaitingReply (armed watch) or Failed |
| WaitingReply | "Waiting for the reply…" | — | SpeakingReply (auto read on) / ReplyReady / Paused / Review |
| SpeakingReply | "Reading the reply aloud" | **Stop** (neutral circle) | ReplyReady |
| ReplyReady | "The reply is ready" | **Listen** (mic) + tertiary **Read it aloud** | Listening |
| Paused | "Paused · the agent needs you" | none (the request card is the only primary) | WaitingReply after the answer |
| Offline | the pause reason | none | — |

`ReplyWatch` (B-CHAT rows of 09 cover the event model; this machine is media's): armed **immediately before dispatch**
with the pre-send set of user message ids; reads automatically **only** when all hold: the connection is live;
`requestComplete`; `session.status` went busy (or retry) then idle, or `session.idle` arrived; exactly one new user
message id equal to the dispatched id (`clientPromptMessageID` when the server supports it) and not in the pre-send
set; every assistant message after it up to the next user message has `parentID ==` that id; each is done or
errored; prose is non-empty. Outcomes: `Idle`, `Waiting`, `ReviewNeeded` ("could not be matched to your message"),
`Interrupted` (a request, question or form arrived), `NoProse` (only code or tools), `Failed` (engine refused). A
`canonicalID` is never trusted as proof of dispatch.

- "Read replies aloud" is a chip, per conversation, **never persisted**; turning it on runs consent and voice choice
  right then, so automatic playback is never the first time the engine is touched.
- Utterance id `<sessionId>/voice-reply/<canonicalId>` marks an automatic reading.
- Commands (`/…`) are refused in conversation mode; phrases are not learned and drafts are not saved during it (09).
- An epoch `Job` per conversation drops late results after any interruption.

---

## 9. Images (MEDIA-VOICE-32, 42, 43)

### 9.1 One loader, bytes only through the gateway (M9)

```kotlin
// :ui:kit commonMain — the only seam; features never see Coil
public fun interface KitImageResolver { public suspend fun bytes(ref: ImageRef, maxBytes: Long): Outcome<ByteArray> }
public val LocalKitImageLoader: ProvidableCompositionLocal<ImageLoader> = staticCompositionLocalOf { error("provided at the root") }

@Composable public fun KitImage(
    ref: ImageRef, contentDescription: String?, modifier: Modifier = Modifier,
    aspectRatio: Float? = null,          // known width/height reserves space before decode: streaming never jumps
    fit: KitImageFit = KitImageFit.Cover, maxBytes: Long = 8L * 1024 * 1024,
)
```

`:androidApp` builds the loader once (`AppImageLoader.create(context, resolver, motion)`) and provides it beside
`LocalKitPlatform`:

```kotlin
ImageLoader.Builder(context)
    .components {
        add(ImageRefKeyer())                                  // memory-cache key = ImageRef.cacheKey
        add(ImageRefFetcher.Factory(resolver))                // calls KitImageResolver; never a network fetch
        add(SvgDecoder.Factory())                             // fed only SanitizedSvg bytes (§11.4)
        if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
    }
    .diskCache(null)                                          // no shared disk cache for server content (M9)
    .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
    .crossfade(if (motion.reduced) 0 else 200)                // 150–250 ms rule; off under reduced motion
    .build()
```

- `KitImageResolver` is `ImageResolver.bytes` (`:engine:media`), which routes each `ImageRef`:
  `MessagePart` → the transcript handle's part (`data:` URL decoded, or `file:///abs` → the connection's
  `fileContent(path)`); `ServerFile`/`ToolOutput` → `fileContent(path)` with the cap; `DraftBlob` → the composer vault
  (`BlobStore.read`, bounded); `AppFile` → app files. **A web address is never fetched**: a Markdown picture with
  `http(s)` is a chip with the host that opens through `openExternalLink` (`ExternalLinkPolicy`).
- Caps: reply pictures and path pictures **8 MiB** (`_maxPathImageBytes`), attachments 10 MiB, gallery items 10 MiB.
  Over the cap → a named chip that still opens the viewer.
- Decode safety (`KitImage` parity): decode target = box size × density (Coil `size` from constraints, `Scale.FIT`/`FILL`,
  never upscaled); unconstrained sources capped at **4096 px** on the longer side; `allowHardware(true)` except where
  pixels are read (markup, copy image).
- Under `MotionPolicy.Off`, animated GIF/WebP show their first frame (decoder factory swapped for the request).
- Failure state: a neutral glyph; words from 120 dp wide ("Can't show this picture"), never red.
- Profile deletion → `ImageResolver.evict(profileId)` clears that profile's memory-cache keys (§4.3).
- A stored attachment id reads "Image", never the id (`3f695ef01`).

### 9.2 Where pictures appear

| Place | `ImageRef` | Rule |
|---|---|---|
| Composer chips | `DraftBlob` | thumbnail decoded once per attachment (the memory cache does it); other files get a file chip; each has Remove |
| Sent photos under a prompt (`sent-photo-<id>`) | `MessagePart` | inline `data:` (OC2 re-encodes images; the app's kept copy for Paseo text-only echo) or `file:///abs` read through the file transport; two thumbnails of one path share one cache entry |
| Reply Markdown pictures | `ServerFile` (kind `file`) | web → chip, `inert` (other schemes) → chip not openable; content-hash file names are not shown as names |
| Tool output images | `ToolOutput` | open the gallery; other files open the viewer |

### 9.3 Gallery (`KitImageGallery`, MEDIA-VOICE-42)

- Opened by tapping any picture; shows **every picture of the transcript in transcript order**, starting at the tapped
  one, "2 of 5". `GalleryIndex.build(transcript)` (pure, `:engine:media/images`) produces the ordered refs with keys
  `part:<messageId>:<index>`, `path:<path>` (skipping paths already known missing), `tool:<identity>`.
- `HorizontalPager` (`beyondViewportPageCount = 1`: only the shown picture and its neighbours load); each page is
  `Modifier.zoomable(state)` (Telephoto 0.19.0) around `KitImage`.
- **A swipe changes the picture only when the shown one is not zoomed:**
  `userScrollEnabled = (zoomState.zoomFraction ?: 0f) < 0.01f`. Zoom range 0.2×–5×; visible **Zoom out / Fit to screen /
  Zoom in** buttons (A11Y-5) and keyboard zoom; the zoom buttons satisfy "teach what is hidden" (pinch is never the only
  path).
- An unreadable picture shows the gallery's own plain error page with **Try again**; the gallery opens at once and does
  not wait on file checks.
- **Edit** opens the markup editor; **Send** puts the picture (edited or original) into the composer's attachments,
  closes the gallery, focuses the field and announces "<name> attached"; it **never sends the prompt**; hidden when the
  agent takes no pictures.
- Presentation: a local full-screen overlay of the chat (07 C21), closed by predictive back with the system animation;
  no shared-element transform while the back gesture runs (demo lesson); no drag-to-dismiss (§2.6). Isolated chats
  cannot open it.

### 9.4 Markup editor (`KitImageMarkup`, MEDIA-VOICE-43)

- Kit part edits a `MarkupDocument` (pure data in `:engine:media/images`): strokes in **image-pixel** coordinates
  (`Stroke(ink: MarkupInk, widthPx, points)`), an optional crop rect, an undo stack. Inks: red, yellow, white, black
  (`MarkupInk`; red is the one approved exception to "nothing is red"). Pen 4 dp on screen, scaled to image pixels.
- Undo; Clear (strokes and crop); crop rectangle with corner handles (minimum **48 dp**, **24 dp** handle room, drag a
  corner or move the rectangle, Apply / Cancel). One pointer draws at a time; drawing is blocked while cropping or
  busy.
- Export (`MarkupRaster.render`, `:android:media/image/`): decode the source at full resolution (software bitmap), replay
  the strokes on an `android.graphics.Canvas`, crop, encode **PNG**; if > **9 MiB** (`kitMarkupMaxBytes`), scale down by
  0.85 steps until it fits. `changed == false` returns the original bytes untouched. The result name is
  `<base>-edited.png`.
- States: loading, error, working; **Send** vs **Done** (Send hidden when the agent cannot take pictures).

---

## 10. Attachments (MEDIA-VOICE-25…35)

### 10.1 Limits (`AttachmentLimits`, one place)

| Limit | Value | Words when hit |
|---|---|---|
| Attachments per draft | **5** | `DraftFull`: "Remove an attachment first. A draft holds up to 5 files and 20 MB in total." |
| Bytes per attachment | **10 MiB** | `AttachmentTooLarge`: "Each attachment must be 10 MB or less." |
| Bytes per draft (decoded) | **20 MiB** | `AttachmentsTotalTooLarge`: "Attachments must total no more than 20 MB." |
| Vault per draft / per root | 32 MiB / 256 MiB | (05 §5; recovery enforces 32 MiB) |
| Office text | **400,000** chars, then cut with a truncated note | — |
| Clipboard image | 10 MiB (read stops one byte past) | `ClipboardImageTooLarge` |
| Markup export | 9 MiB | scaled down to fit |

Representation stays `PromptAttachment(mime, filename, url = "data:<mime>;base64,…")` and the vault keeps the data-URL
text (05 §5, unchanged layout). Byte length is computed from the base64 length and padding. Base64 encoding happens on
`dispatchers.default`, never on the main thread.

### 10.2 The "+" tray and tools list (MEDIA-VOICE-25, owner concept C 2026-10-20)

- "+" turns into "×"; the pill grows **upward by one compact row** of round matte buttons with a word under each and a
  hairline above the field: **Photos**, **Camera**, **File**, **More**. No sheet, no glass. Fold + fade 150–200 ms
  (instant under reduced motion). Test tags `composer-tool-gallery`, `composer-tool-camera`, `composer-tool-attach`,
  `composer-tray-more`, `kit-composer-tray`.
- Photos and Camera only when `serverCapabilities.promptAttachments && platform.promptPhotos`; File only when
  attachments are supported and the agent is not pictures-only; no tray in an isolated chat, in conversation mode, or
  when nothing can be attached ("+" then opens the sheet, which says why).
- While a run is going the three media buttons **stay in place, disabled, with the reason** "available when the
  current run finishes" (never vanish).
- Labels shrink together (one scale) until the longest fits its quarter of the row; whole words, never "Ph…"; frame
  text cap 1.3×.
- "More" (`composer-tools-sheet`): conversation settings rows first (model, approvals, plan, background), then Project
  file, Attach file (when not in the tray), Photos and Camera (when not in the tray), **Voice input** ("Records and
  transcribes on this device"), Commands; folds "Prompts" (Reuse, Saved, Save for later, Clear text) and "More" (Web
  sources, **Voice conversation**), plus **Paste image from clipboard** when `ClipboardImages.hasImage()` is true at the
  moment the sheet opens. A tool that cannot run stays in place and says why ("this agent takes pictures only",
  "text only").

### 10.3 Photos from the library (MEDIA-VOICE-26; B-MEDIA-007)

1. `room = 5 − current attachments`. `room >= 2` → `ActivityResults.pickImages(limit = room)` (`PickMultipleVisualMedia(room)`);
   `room == 1` → single `PickVisualMedia` (the multi contract rejects 1). Cancel → nothing pending.
2. For each `PickedFile`: `ImageCodec.preparePhoto(file, PhotoRules.Prompt)` on `dispatchers.io`:
   - API ≥ 28: `ImageDecoder.createSource(contentResolver, uri)` with `setTargetSize` so the long edge ≤ **2048**
     (aspect kept, never upscaled), `allocator = ALLOCATOR_SOFTWARE`; EXIF orientation is applied by the decoder.
   - API 26–27: `BitmapFactory` with `inSampleSize` + exact scale, rotation from `ExifInterface` 1.4.2.
   - Encode JPEG **quality 85** (M10). Kept byte for byte instead: GIF, and animated WebP, when ≤ 10 MiB and ≤ 2048 px.
   - Result over 10 MiB → `PhotoTooLarge`. Undecodable or HEIC below API 28 → `PhotoUnsupported` ("This picture's
     format can't be attached. Try a screenshot or a JPEG.").
3. Sniff the **output** bytes by magic (`MagicBytes`): PNG `89 50 4E 47 0D 0A 1A 0A`, JPEG `FF D8 FF`, GIF87a/89a,
   WebP `RIFF????WEBP`; anything else → `PhotoUnsupported` (B-MEDIA-008 "fake extensions are rejected").
4. Skip duplicates (same data URL); stop when the draft is full (`DraftFull` note).
5. Hand to the composer (`Drafts.addAttachments`), which writes the vault blob before the chip appears.

### 10.4 Camera (MEDIA-VOICE-26, trap 32)

1. `PermissionGate.request(Camera)` first. The manifest declares `CAMERA` (for the scanner), so `ACTION_IMAGE_CAPTURE`
   without the grant throws `SecurityException` [S research §3]. `Denied` → `CameraDenied` ("Allow camera");
   `PermanentlyDenied` → `CameraDeniedForever` ("Allow camera in Android settings").
2. `captureId = <epochMicros>`; **write the pending record first** (§10.5) with `path =
   files/camera-capture-v1/<captureId>.jpg`; if that write fails, the camera is **not** launched (`PhotoStorage`;
   B-MEDIA-008 "failed request persistence never launches the camera").
3. `ActivityResults.takePhoto(PhotoTarget(captureId))` → `TakePicture` with the `FileProvider` URI
   `content://${applicationId}.localfiles/camera/<captureId>.jpg` (§15.2). The launcher is registered in
   `MainActivity.onCreate` with the fixed key `oc.takePhoto`, so a result after process death is delivered to the new
   activity.
4. `false` (cancelled) → delete the file and the record. `true` → `preparePhoto` on the file, attach into the
   **recorded** conversation, delete the capture file, clear the record.

### 10.5 The pending-photo record and recovery (MEDIA-VOICE-27; B-MEDIA-008, B-STATE-088)

- Key `oc.pendingPromptPhoto` (owner `:engine:composer`, format unchanged, 05 Appendix A): JSON
  `{id, profileID, sessionID, directory, workspace, ref?, path?, name?, failed}`; `id` = microseconds since epoch as
  text. It is written **before** any picker or camera opens; a second pick is refused while it exists
  (`PhotoPending`: "Another photo is still waiting. Open its conversation first.").
- Identities (ids, directory, workspace, blob) must survive the `Redactor` unchanged, else the record is refused
  (`savedPromptIdentity` parity), so a record can never route to another owner.
- **Recovery** (`PendingPhotoRecovery.recover()`), run by bootstrap before any chat route reads drafts (07 shell
  order), serialized:
  1. No record → `NothingPending`.
  2. Profile gone → delete the capture file and record → `ProfileRemoved` (profile removal always wins).
  3. `ref` present (already in the vault) → append the ref to the recorded conversation's draft via
     `DraftStore.appendAttachment(ref, …)` (C10-8; app-scoped, works without a live connection).
  4. Else `path` present and the file exists and is non-empty → `preparePhoto`, write the vault blob
     (content-addressed, dedupes on restart; set `oc.draftAttachmentVault = true` first), append, delete the capture
     file.
  5. Enforce ≤ 5 attachments and ≤ 32 MiB per draft; a payload where `Redactor.containsSecret` is true is not attached
     (`RetryRequired`).
  6. Any failure keeps the record and returns `RetryRequired`; never exposes exception text (it can hold paths).
- **Opening a conversation with a waiting photo:** a photo for this conversation joins the composer at once; one for
  another conversation goes into that conversation's saved draft; one that cannot move blocks a new pick with
  `PhotoPending`.
- Profile deletion: `clearForProfile(profileId)` drops a record of that profile and its capture file; the vault
  `collect` runs as 05 §5 says.
- Stale captures: at start, files in `files/camera-capture-v1/` with no record are deleted.

### 10.6 Files (MEDIA-VOICE-28, 29; B-CHAT-009, B-CHAT-010, B-MEDIA-006)

1. `ActivityResults.openDocument(listOf("*/*"))` (dialog title "Attach to prompt" is the system's).
2. Checks in order: count < 5; aggregate < 20 MiB; reported size ≤ 10 MiB (a hint); then
   `ContentAccess.readBounded(file, limit = min(10 MiB, 20 MiB − aggregate))` keeps at most limit + 1 bytes (a file
   that grew after the picker reported its size cannot allocate without bound).
3. `.xlsx`, `.xlsm`, `.docx` → `OfficeText` (§10.7); result attaches as `text/plain` named `<file>.csv` or `<file>.txt`;
   the composer note says "2 sheets, 6 rows" or "document attached".
4. Else `PromptAttachmentMime.of(declaredMime, name, bytes)`:
   - declared png/jpeg (`image/jpg` → `image/jpeg`)/gif/webp and `application/pdf` keep their type;
   - declared `text/*`, json, javascript, xml, yaml, toml, sh, sql, graphql, ndjson, `+json`, `+xml` → exactly
     `text/plain` **only if the bytes look like text** (no NUL; valid UTF-8; no control characters except tab, LF, VT,
     FF, CR) — OpenCode expands exact `text/plain` data URLs into text;
   - other `image/*` declared types are refused;
   - otherwise the extension decides: png jpg jpeg gif webp pdf keep their types; md markdown txt log html htm css csv
     json xml yaml yml toml dart js ts tsx jsx py go rs java kt kts c cc cpp h hpp sh sql → `text/plain` with the same
     text check;
   - an unknown binary with no or a generic declared type is accepted as text only if it looks like text;
   - else `AttachmentUnsupported`: "Images, PDFs, text files, and Excel or Word files (.xlsx, .docx) can be attached."
5. File names: base name only; control characters and `"` → `_`; empty → `file`.

### 10.7 Office files as text (`OfficeText`, pure commonMain)

- `ZipCentralDirectory`: find the end-of-central-directory record in the last 64 KiB + 22 bytes; read entries from the
  **central directory** (so data-descriptor entries, as Excel writes them, work); method 0 (stored) or 8 (deflate via
  `RawInflate`, M8); flag bit 0 (encrypted) → `OfficeUnreadable` ("password-protected"); any other method → refused.
  New guard: an entry inflating beyond **64 MiB** is refused (zip bomb), and at most 2,000 entries are read.
- Workbook: `xl/workbook.xml` → sheets in order with their names; targets from `xl/_rels/workbook.xml.rels`, falling
  back to `xl/worksheets/sheet<n>.xml`; shared strings from `xl/sharedStrings.xml`; each sheet becomes CSV under its
  name; the first line says where the text came from and that formatting, charts and formula definitions are not
  included. Zero sheets → `OfficeEmpty`.
- Document: `word/document.xml` → paragraphs.
- Cut at 400,000 characters (never splitting a surrogate pair) with a truncated note.
- Old binary `.xls`/`.doc`, damaged or encrypted → `OfficeUnreadable` with what to do. Runs on `dispatchers.default`.
- XML through `XmlPull` (M7): no DOCTYPE, no external entities (a DOCTYPE fails the read), only the five predefined
  entities and numeric character references, depth ≤ 64, element count ≤ 2,000,000.

### 10.8 Keyboard images and clipboard paste (MEDIA-VOICE-30 + freeze tip)

- **Keyboard (IME `commitContent`: GIFs, stickers, the keyboard's clipboard chip):** `KitComposer` attaches
  `Modifier.contentReceiver` in `:ui:kit-chat` **androidMain** (the listener needs `ClipData`; `JETPACK_AHEAD` only if
  CMP's common API lacks it). For each item with an `image/*` type and a URI, the kit emits
  `onContentInserted(InsertedContent(handle = PlatformContentHandle(item), mime))` and marks it consumed; text items are
  returned to the field. The ViewModel calls `AttachmentIntake.fromKeyboard`, which reads through
  `ContentAccess.readInserted(handle, 10 MiB)` (`:android:platform`) while the `TransferableContent` is still
  referenced. Name `pasted-image-<epochMs>.<jpg|gif|webp|bmp|png>`; mime defaults to `image/png`; then the same
  re-encode, MIME gate and limits as §10.3. Not wired in isolated chats or when attachments are unsupported.
- **Clipboard (freeze-tip `oc/clipboard` port, `AndroidClipboardImages`):** `hasImage()` reads only
  `primaryClipDescription` (no Android 12+ toast); `read(maxBytes)` opens the first item's URI, picks the first
  `image/*` type from the description or `ContentResolver.getType`, reads with the one-byte-past cap
  (`ClipboardImagePolicy.readLimited` parity), and maps `SecurityException`/`IOException` to "no picture".
  Entry points: the "+" sheet row **Paste image from clipboard** (always visible when the clipboard holds a picture;
  the obvious path), and the text field's own **Paste** when proof P-M7 shows Compose offers it for an image clip with a
  `contentReceiver` attached (§22). If it does not, the kit adds a **Paste image** item to the field's text menu
  (`:ui:kit-chat` androidMain, `JETPACK_AHEAD` row) [U: the Compose 1.13 text-menu extension API].
- Desktop drop (MEDIA-VOICE-31) is a desktop-phase item: `Modifier.dragAndDropTarget` with the drop's size checked
  before reading.

### 10.9 What each agent can take (MEDIA-VOICE-33) and agent-card photos (34)

- `promptAttachments == false` → no tray, the note `codexTextOnlyPrompt`. `promptImagesOnly` (Paseo/Claude Code) →
  Photos and Camera only; "Attach file" says "this agent takes pictures only"; the gateway maps data-URL pictures to
  Paseo `images` (04). `promptEchoTextOnly` → the sent copy is matched by text and keeps its pictures until reload (09).
- Agent cards that ask for pictures (09 card renderer) call the same `AttachmentIntake` (`pickPhotos` / `takePhoto`)
  with the card's conversation as the target, so a photo that outlives the app reaches that conversation's draft. A
  card without the capability says photos cannot be added from there.

### 10.10 Attaching from viewers, tool output and the project (MEDIA-VOICE-35)

- Viewer More › **Attach to prompt** (`file-preview-attach`, `project-file-attach`) uploads the file's contents with the
  message (through `AttachmentIntake.fromBytes`), named apart from **Add to prompt** / "@path" (a reference,
  `application/x-directory` or a project-file reference, 04 §7.9).
- The composer note "Attachments save with this draft on this device." shows **once per session**, not per attachment.

---

## 11. Viewing files (MEDIA-VOICE-37…41, 49, 50)

### 11.1 Where the viewer lives

- `ProjectRoute.FileView(profileId, projectDir?, path, line?)`: a page on compact windows, the detail pane from medium
  up (07 O-07-3, default yes). Predictive back closes it with the system animation; it survives process death because
  it reloads by path.
- Content that has no server path (a `data:` attachment, tool output bytes, a phone file) is registered in
  `PreviewSources` (App scope, memory only, LRU of 8) and the route carries its id
  (`FileView(…, transientSource = id)`, C10-13 to 07). After process death such a viewer says "This preview is no
  longer open. Open it again from the chat." with **Back**.
- `FilePreviewBody` (embedded in tool cards, with "Open full") uses the same adapters and bodies.
- Isolated (demo/preview) chats never render PDFs ("PDF pages do not render here").

### 11.2 `FilePreview.prepare` → `PreviewData` (MEDIA-VOICE-38; B-MEDIA-001)

| Field / rule | Exact behaviour |
|---|---|
| MIME | lowercased, parameters stripped; missing/generic → extension map: png jpg jpeg gif webp bmp svg pdf json csv tsv xml md txt log dart js ts tsx jsx py go rs yaml yml |
| Text | **strict UTF-8** for text-ish types; a NUL or invalid UTF-8 → no text (never replacement characters); original bytes kept for Save |
| Flags | `isRasterImage` (png jpeg gif webp bmp), `isPdf` (bytes non-empty), `isMarkdown` (type, `.md`/`.mdx`, or text with headings/fences/tables), `prettyJson` (two-space indent, only when not truncated), `language` (extension → highlighter id), `separator` (csv/tsv by MIME or extension), `exportBytes` (bytes, else UTF-8 of the original text) |
| Truncation | text over **200,000** chars is cut without splitting a surrogate pair; the viewer says "truncated"; Copy and Save keep the whole original |
| From a data URL | missing content → `AttachmentContentMissing` ("The attachment content is not included in this message."); remote URL → `RemoteAttachmentPreview` ("Remote attachment previews are not available."); undecodable → `FileNotShowable` |
| Line out of range | the notice "Line N is outside this preview" instead of a mark |

### 11.3 `KitViewer` (kit-content; MEDIA-VOICE-37)

- **Frame (always the same):** name in file-name style (never squeezed), muted parent path (`B-NAV-028`: parent folder
  with a trailing slash; nothing for a root file; `:line` when opened at a line), **one labelled primary** at the bottom
  where the thumb is (e.g. **Ask about this file**, **Attach to prompt**), an optional **secondary** (e.g. **Share on
  Wi-Fi**), **More (⋯)**, **Close**. Reader mode: the primary is the solid main button and **Copy** sits beside it for
  text (Copy moves into ⋯ at ≥ 1.6× text scale so the bar holds the main action alone).
- **Bodies:** `Text`, `Code` (line numbers; highlighting through `CodeTokenizer`/SyntaxMP, 06; scrolls sideways by
  default; `initialLine` scrolled to and marked), `Markdown` (reflowed on the reading rails, 720 dp; **Show source**
  swaps to text), `Image` (`KitZoom`), `Pdf` (§11.6), `Svg` (Image / Source), `Delimited` (table, Table / Source),
  `Binary` ("Can't show this file", type and size in words such as "2.4 MB"; the caller's action stays).
- **Lazy text:** `Text` and `Code` are a `LazyColumn` of lines (stable keys = line index, `contentType` per kind), so a
  200,000-character file never lays out at once.
- **More (⋯):** **Find** (Ctrl+F on hardware keyboards), **Copy contents** (verbatim, not redacted; B-MEDIA-001),
  **Wrap lines** (checked item; a header toggle in reader mode), **Show source** (checked item for Markdown, SVG,
  CSV), then the host's items. A secondary click on the body opens the same menu (desktop). Esc closes find, then the
  viewer.
- **Find:** `ViewerFind` (pure) builds an index of matches over the text once per query (case-insensitive, literal);
  inline bar with field `kit-viewer-find`, previous / next / close, count `kit-viewer-find-count`; marks are drawn in
  the draw phase (`KitFindMark`: wash .18 passive, .38 active, never a text colour); next/previous scroll the line
  into view.
- **States:** loading (six skeleton rows), error (plain "Could not load <name>" + Try again + redacted Details),
  empty, content. `KitViewerSource.Load` is the retryable async form.
- **Widths:** prose on 720 dp rails; the viewer itself capped by 06's viewer max width; code and tables scroll inside.
- **Text scale:** the frame (name, path, buttons) is frame text (1.3× cap); the body is reading text (full scale).

### 11.4 SVG (`StaticSvg`, MEDIA-VOICE-41; B-CHAT-107)

- Input ≤ **256 KiB**, ≤ **1,000** elements, depth ≤ **32**, attribute value ≤ **16,384** chars, finite bounded
  geometry, dimensions ≤ **16,384**.
- Allowlist: paths, basic shapes, groups, text, local gradients and their attributes. **Rejected:** `script`,
  animation elements, external resources and `href` to anything but a local `#id` gradient, `<image>`, `<use>`,
  stylesheets (`<style>`, `style=` with `url(` or `@import`), clip paths, masks, dashed strokes, foreign content, any
  DOCTYPE or entity.
- The validator returns `SanitizedSvg` (a value class only `StaticSvg` constructs); `KitSvgImage` accepts only that
  type and renders it through Coil's `SvgDecoder` (AndroidSVG 1.4 on Android; no resolver is ever set). Source and Save
  keep the unchanged original. A truncated SVG is never interpreted as complete → `SvgNotStatic` with Source.

### 11.5 CSV and TSV (`DelimitedText`, B-CHAT-021…024)

UTF-8; input ≤ **256 KiB**, **200** shown rows, **32** columns, **4,096** chars per cell; quoted separators, doubled
quotes and multi-line cells; ragged rows stay ragged; no guessed header and no total count; a blank input and a
trailing line terminator add no rows; malformed or oversized input shows Source; the row limit shows "showing the first
200 rows". Recognised by an explicit matching MIME, or by extension when the MIME is missing or generic; a contradictory
explicit MIME never becomes a table. **Table data stays LTR**; the surrounding controls follow RTL.

### 11.6 PDF (MEDIA-VOICE-40; B-MEDIA-004)

**Service** (`io.github.eslamasabry.opencode_mobile.LocalPdfService` in `:androidApp`, rendering in
`:android:media/pdf/PdfPageRasterizer`), manifest unchanged:
`<service android:name=".LocalPdfService" android:exported="false" android:isolatedProcess="true" android:process=":local_pdf"/>`.

| Step | Rule |
|---|---|
| Client (`AndroidPdfPageRenderer`, port of `LocalPdfBridge.kt`) | input ≤ **10 MiB**; page < **200**; API < 29 → `PdfNeedsAndroid10`; one request at a time app-wide (a second waits) |
| Descriptors | create two private temp files in `cacheDir` (`oc-pdf-*.input`/`.output`), open read/write `ParcelFileDescriptor`s, **unlink both paths before writing any byte**; write the PDF on `dispatchers.io` |
| Bind | `bindIsolatedService(intent, BIND_AUTO_CREATE, "pdf<uuid>", executor, connection)`: a **fresh instance per request** (default) |
| Message | `Messenger` with the two descriptors and the page; the reply carries only `pageCount`, `width`, `height`, `page` (no PNG in a Binder bundle) |
| Service | `PdfRenderer` on the input FD; scale = `min(1536 / max(w, h), sqrt(2_000_000 / (w * h)))`; white background; `RENDER_MODE_FOR_DISPLAY`; PNG compressed through a stream that stops at 10 MiB; a **10 s** watchdog kills only its own process; `onUnbind`/`onDestroy` kill the process |
| Read back | client deadline **12 s**; read the output FD (≤ 10 MiB); check the PNG signature and the IHDR width/height equal the reported ones and `page` matches **before** decoding |
| Errors | `SecurityException` → `PdfEncrypted` ("This PDF needs a password or uses protection the app can't open. Save keeps the original."); invalid → `PdfInvalid`; timeout → `PdfTimedOut`; size → `PdfTooLarge`; binder death → `PdfFailed` (`Redacted` token `pdf:service_died`); cancel → silent |

**Pages UI** (`PdfPages`, pure queue + `KitPdfPage`): pages stacked in a `LazyColumn`; a page renders only while on
screen (a page scrolled away before its turn never reaches the service); page width = `min(available − 2 × space3,
720 dp)`; zoom buttons; per-page loading and error with **Try again**; page caption "Page 3 of 12"; every request is
cancelled when the app leaves the foreground (back in the foreground, pages need an explicit Try again, parity); the
first page is cached; disposing retires request ids so a late reply cannot install an old page. One bind per document
instead of per page is allowed only after P-M9 measures the spawn cost and shows the same security properties.

### 11.7 The Files screen's viewer (MEDIA-VOICE-39)

- Primary **Ask about this file** (`project-file-ask`): from a chat, stage the file as a reference (with the line when
  one was opened) and return to that composer; from anywhere else, start a new chat in this project with `@path ` in the
  field, unsent.
- Secondary **Share on Wi-Fi** (`project-file-share-wifi`) when `PlatformCapabilities.wifiFileShare`.
- More: **Attach to prompt** (from a chat; returns to the tree with "Attached"), **Save** (`project-file-download`),
  **Reload** (embedded only), **Open review** (when the file has a change; `project-file-review`), **Copy path**.
- **Fetch scoping:** every read captures (profile id, location revision, transport identity) at start; a changed scope
  drops the result and the viewer says "The project changed while this file was loading."
- Content mapping: raster image → Image; text (cut at 200,000) → Delimited (csv/tsv, when no line was requested), SVG,
  Markdown (`.md`, `.markdown`), Code (extension in the code set, or a line was requested), else Text; PDF → §11.6;
  else Binary. `initialLine` forces Code so the line can be marked.
- The wrap choice is saved through `ReaderPreferences` (`oc.readerPreferences.<profileId>` = `{sourceFirst?, wrapCode?}`,
  owner `:engine:settings`, swept with the profile). Default wrap: code scrolls sideways on every window; text, Markdown
  source and others wrap on compact windows.

### 11.8 Files a turn changed and tool output files (MEDIA-VOICE-50)

Rows under a finished turn are 09's (chat). This section supplies the loader: `ToolOutputFile(path, url, mime, name)`
is fetched with the connection's file transport; base64/binary → bytes (empty → "The server returned empty image
data."); text → text; unavailable → "The generated file is not available from this server." Attach and Save come from
the viewer.

---

## 12. Save, copy, share and open with (MEDIA-VOICE-36, 44 + freeze tip)

| Action | Where | How | Words |
|---|---|---|---|
| **Save <name>** | viewer More, tool output, gallery | `ActivityResults.createDocument(name, mime)` → `ContentAccess.write(target, bytes)` (mode `"wt"`) | "Saved <name> on this device"; cancel says nothing; failure `SaveFailed` |
| **Copy contents** / **Copy original** | viewer | verbatim (`redact = false`, B-MEDIA-001); a failed copy offers Retry | "Copied" |
| Copy chat text | message menu (09) | redacted copy | — |
| **Share <name>** (system sheet) | viewer More (D10-11) | `ShareService.shareFile(SharedFile.Bytes)` → write to `cache/share/<uuid>/<name>`, `FileProvider` URI, `ACTION_SEND` chooser with `EXTRA_EXCLUDE_COMPONENTS = MainActivity`; `cache/share/` is emptied at every app start | `ShareUnavailable` when no target |
| **Open with another app** | phone-file sheet for a tapped path in shared storage (freeze tip) | `ExternalOpen.openWithChooser(OnDisk)`: `FileProvider` URI under `external-path`, MIME from the extension (`*/*` fallback), `FLAG_GRANT_READ_URI_PERMISSION`, chooser | `NoAppToOpen` |
| **Install** (an `.apk` an agent saved) | same sheet, only for `.apk` | `ExternalOpen.installPackage(OnDisk)`: `ACTION_VIEW` with `application/vnd.android.package-archive`; the system installer screen is the confirmation; never silent; hidden when `REQUEST_INSTALL_PACKAGES` is absent (F-Droid flavour) | — |
| Share text out | reply menu, report | `ShareService.shareText(text, subject)`, excludes this app (B-ANDR-074, 077) | — |
| Text shared in | `ACTION_SEND text/plain` | owned by section 11 and `EntryRouter` (B-ANDR-074…076); images shared in are D10-2 | — |

Every URL the app did not author still goes through `ExternalLinkPolicy`/`openExternalLink`; the actions above open
files, never URLs.

---

## 13. Share on Wi-Fi (MEDIA-VOICE-45 + freeze tip; B-MEDIA-034, E2E-14)

### 13.1 Rules (pure, `:engine:media/share/`)

| Rule | Exact behaviour |
|---|---|
| One share at a time | `start` stops the previous share first |
| Address | the first IPv4 of an interface whose name starts with `wlan`, `swlan` or `ap` **and** that is private LAN (10/8, 172.16/12, 192.168/16); never `0.0.0.0`, never mobile data (`rmnet*`, `ccmni*`), never VPN; none → `WifiShareNoWifi` ("This phone is not on Wi-Fi. Connect to Wi-Fi to share this file with a nearby device.") |
| Port | ephemeral (`0`) |
| URL | `http://<ip>:<port>/<token>/<DartJson.encodeComponent(name)>`; token = **24** secure-random bytes, base64url without padding (192-bit) (C10-2 adds `secureRandomBytes(n)` to `:core:common`) |
| Paths | exactly `/<token>/<name>`; token compared in constant time; anything else **404** |
| Methods | GET and HEAD; others **405** with `Allow: GET, HEAD` |
| Headers | `Content-Type` (given or `application/octet-stream`); `Content-Disposition: attachment; filename="<ASCII fallback>"; filename*=UTF-8''<pct>`; `X-Content-Type-Options: nosniff`; `Cache-Control: no-store`; exact `Content-Length`; `Connection: close` |
| Body | streamed in **64 KiB** slices from bytes or from the file; the download count rises only after a full stream |
| Name | base name only; control characters and `"` → `_`; empty → `file` |
| Lifetime | **15 min**, then stop; also on **Stop**, when the sheet closes, on a new share, and when the process ends |
| Background | parity: not stopped when the app is merely backgrounded (no service keeps it alive; Android may freeze the process) — D10-10 |

### 13.2 What may be shared (`PathSafety`, freeze-tip `6ce60d6f0`, `637ffea4c`)

- **Already fetched bytes** (a project file opened in the viewer, a chat link's fetched file) are always allowed.
- **A phone file streamed from disk** (shared storage, the built-in Linux and its projects) is allowed only when, on the
  **real path** (symlinks resolved):
  1. `looksSecret(path)` is false: no folder in the path is `shared_prefs`, `databases`, `no_backup`, `app_flutter`,
     `code_cache`, `.ssh`, `.gnupg`, `.codex`, `.claude`, `.aws`, `.kube`, `.docker`, `.gcloud`, or starts with `.oc-`
     (phone server password, agent sign-ins); no `.config/opencode`, `.config/gh`, `.local/share/opencode`, `etc/ssh`;
     the file name does not start with `.env`, `id_rsa`, `id_ed25519`, `id_ecdsa`, is not `auth.json`, `shadow`,
     `.netrc`, `.npmrc`, `.git-credentials`, does not end with `.pem`, `.key`, `.p12`, `.pfx`, `.keystore`, `.jks`,
     does not contain `password`, `secret` or `token`, and is not a `*credentials*.json`;
  2. it is outside the app's private storage (`filesDir`, app documents), **or** inside the allowed roots
     `files/linux/ubuntu` and `files/projects`;
  3. a link inside the allowed roots does not lead out of them.
- Otherwise `WifiSharePrivateFile` ("The app keeps this file private, so it can't be shared on Wi-Fi.").

### 13.3 The server (`AndroidLanFileServer`, M14)

- `ServerSocket()` bound to `InetSocketAddress(lanAddress, 0)`, backlog 4; accept loop on its own
  `dispatchers.io.limitedParallelism(4)` lane; at most 4 connections at once (more are closed at once).
- Per connection: `soTimeout = 10 s`; read the request head up to **8 KiB** (longer → 431 and close); hand the request
  line and headers to `ShareHttp.handle(request): ShareResponse` (pure, commonMain); write the status line and headers;
  for GET stream the body; close.
- `stop()` closes the server socket and every open connection. No keep-alive, no Range, no chunked encoding, no
  compression.
- Desktop phase: this class moves to a new `oc.jvm.library` module (ADR §7.4) unchanged.
- **Android 17 (targetSdk 37):** accepting an incoming TCP connection on the LAN needs `ACCESS_LOCAL_NETWORK`
  [V developer.android.com local-network-permission, 2026-11-01]. At the switch targetSdk is 36 (ADR §8), so nothing
  changes; the targetSdk-37 slice adds `AppPermission.LocalNetwork` (C10-4) and asks before `start`, with
  `WifiShareUnavailable` and an **Allow** fix when refused.

### 13.4 The sheet (`:feature:project`, local sheet of the viewer)

Title "Share on Wi-Fi"; the file name and size; `KitQr` of the URL (semantics label "QR code to download <name>"); the
warning "Anyone on this Wi-Fi with the link can download this file."; the link in a copyable code block with **Copy
link**; "Downloaded N times" (plural; 0 → "Not downloaded yet"); "Stops sharing in m:ss" (ticks each second);
**Stop sharing**. Ended: "Sharing has stopped. Nobody can download this file now." Unavailable: "Sharing could not start.
Try again in a moment." Test tags: `file-share-sheet`, `file-share-qr`, `file-share-link`, `file-share-copy`,
`file-share-downloads`, `file-share-time`, `file-share-stop`, `file-share-live`, `file-share-ended`,
`file-share-failure`. Closing the sheet stops sharing.

---

## 14. QR codes (MEDIA-VOICE-46…48)

### 14.1 Showing one: `KitQr` (kit-content)

- Matrix from `QroseEncoders.QR(errorCorrection = QrErrorCorrection.M)` (`qrose-encoder-matrix` 1.3.1, M13), computed in
  `remember(data) { encodeOrNull(data) }` with the exception caught inside the lambda (composition never throws).
- Drawn on a **light card in every theme** with a **4-module quiet zone**; dark modules only; module side =
  `floor(idealPx / totalModules)` whole physical pixels (LOOK-21); width ≤ 240 dp (`qrMaxSize`) and ≤ the given width,
  square, no `size` parameter.
- The data is never shown as text, logged or put in semantics; only the host's label is. Empty payload refused
  (B-KIT-026). Too long → the notice "This is too long for a QR code. Copy the link instead." `KitQr.fits(data)` lets a
  host decide first.
- Readability test: every QR the app draws for 50 fixture payloads (Wi-Fi URLs, handoff links up to 1,000 chars) is
  rendered by Roborazzi and decoded with ZXing core 3.5.4 in a JVM test. If any fails, add penalty-based mask selection
  (ISO 18004 rules N1–N4) over qrose's eight `QrMaskPattern`s.

### 14.2 Scanning one: pairing (MEDIA-VOICE-46)

**Screen** `ServersRoute.PairingScanner` (`:feature:servers`, full-screen dialog, 07 E03). Entry "Scan pairing QR" only
when `PlatformCapabilities.qrPairing`; **Paste the code instead** is always available and uses the same parser.

| Stage | Shown | Actions |
|---|---|---|
| Starting | progress; after **8 s** "This is taking longer than usual" | Paste it instead |
| Scanning | `KitScanner` live preview | Paste it instead |
| Denied | "The camera is needed to scan the code." | **Allow camera**, Paste it instead |
| PermanentlyDenied | "Android blocks the camera for this app." | **Open app settings** (no retry button: Android will not ask again), Paste it instead |
| NoCamera (`FEATURE_CAMERA_ANY` false) | "This device has no camera." | Paste it instead |
| Failed | "Camera failed" + Details (redacted) | **Try again**, Paste it instead |

**`KitScanner`** (common frame + `AndroidScannerSlot` in `:ui:kit-content` androidMain through `KitContentSlots.Scanner`,
03 S11): square window **240 dp** (never below 160 dp; short windows lay out in a row), dim outside, four corner
brackets, one instruction line ("Point the camera at the QR code printed by opencode2 pair."), an in-place line "That
QR is not a pairing code" while scanning continues.

```kotlin
// :ui:kit-content androidMain — AndroidScannerSlot (sketch)
val reader = BarcodeReader(BarcodeReader.Options(formats = setOf(BarcodeReader.Format.QR_CODE),
    tryInvert = true, tryRotate = true, tryHarder = false))          // dark terminals print inverted codes
val analysis = ImageAnalysis.Builder()
    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
        ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build()
analysis.setAnalyzer(decodeExecutor) { proxy ->                       // one single-thread executor
    proxy.use {
        it.setCropRect(windowRectIn(it))                              // decode only what the person aims at
        reader.read(it).firstOrNull { r -> r.text != null }?.text?.let(onFrameCode)
    }
}
// Preview → SurfaceRequest → CameraXViewfinder(surfaceRequest); back camera; bindToLifecycle(host lifecycle)
```

- **Latch:** the first text the host accepts stops analysis, unbinds the camera **before** the host pops, and plays
  `KitHaptic.Done`; queued values are ignored. A rejected text is not reported again for 2 s (each code once per
  sighting).
- **Never keep the value** (SEC-2): the decoded text is handed to the pairing parser (owned by 04: `B-PROTO-064…069`,
  `B-SEC-107`) and never stored, rendered, put in semantics or logged; `PairingPayload.toString` redacts.
- Lifecycle: stop on `ON_PAUSE`/`ON_STOP`, restart on `ON_RESUME`; start attempts are counted so a late start after a
  pause is ignored; stop on dispose.
- Camera permission and `hasCamera` come from `PermissionGate`/`PlatformCapabilities` (three-state, §6.8 table applies
  with `camera_permissions.camera_requested`).
- Budgets (P-M3): decode p95 < 30 ms per 720p frame on NX721J (`BarcodeReader` timing), first lock < 1 s on a real
  `opencode2 pair` code in a dark terminal.
- Desktop: no scan affordance (paste only). iOS later: `AVCaptureMetadataOutput`.
- **Scan from a picture** (decode a Photo Picker image with zxing-cpp `read(bitmap)`) is D10-3.

---

## 15. Manifest, permissions and capability flags

### 15.1 Capability flags this section reads (`PlatformCapabilities`, 03 §10.4)

| Flag | Android | Desktop now | Gates |
|---|---|---|---|
| `voice` | true | false | mic button, "Voice input" row, Settings › Voice typing, phone-setup voice component, widget voice panel |
| `readAloud` | true | false | "Read reply prose", the read-aloud voice section, voice conversation |
| `voice && readAloud` | true | false | "Voice conversation" |
| `promptPhotos` | true | false | Photos and Camera in the tray and the tools list; agent-card photo asks |
| `localPdf` | true on API ≥ 29 | false | PDF bodies (else Binary with Save) |
| `qrPairing` | `FEATURE_CAMERA_ANY` | false | "Scan pairing QR" |
| `wifiFileShare` | true | false (desktop phase may turn it on) | "Share on Wi-Fi" |
| `inAppUpdater` (03) | stable flavour only | — | the `.apk` **Install** action (§12) |

Off-Android answers are honest: the device probe says `Unsupported`, the microphone permission is
`PermanentlyDenied`, opening settings is a no-op (B-MEDIA-032).

### 15.2 Manifest entries (`:androidApp/src/main/AndroidManifest.xml`; C10-10)

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-feature android:name="android.hardware.microphone" android:required="false"/>
<uses-feature android:name="android.hardware.camera" android:required="false"/>   <!-- a phone without a camera still installs and pairs by pasting -->
<queries>
    <intent><action android:name="android.intent.action.TTS_SERVICE"/></intent>         <!-- Android 11+ package visibility -->
</queries>
<application …>
    <service android:name=".LocalPdfService" android:exported="false"
             android:isolatedProcess="true" android:process=":local_pdf"/>               <!-- FQCN frozen -->
    <provider android:name="androidx.core.content.FileProvider"
              android:authorities="${applicationId}.localfiles"
              android:exported="false" android:grantUriPermissions="true">
        <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/local_file_paths"/>
    </provider>
    <!-- Photo Picker backport through Google Play services on API 19–29 (removed in the fdroid overlay with tools:node="remove") -->
    <service android:name="com.google.android.gms.metadata.ModuleDependencies" android:enabled="false"
             android:exported="false" tools:ignore="MissingClass">
        <intent-filter><action android:name="com.google.android.gms.metadata.MODULE_DEPENDENCIES"/></intent-filter>
        <meta-data android:name="photopicker_activity:0:required" android:value=""/>
    </service>
</application>
```

`res/xml/local_file_paths.xml`:

```xml
<paths>
    <external-path name="shared_storage" path="."/>          <!-- open a phone file with another app (freeze tip) -->
    <files-path name="camera" path="camera-capture-v1/"/>     <!-- TakePicture targets (§10.4) -->
    <cache-path name="share" path="share/"/>                  <!-- system share of fetched bytes (§12) -->
</paths>
```

Removed on purpose (listed in the component-freeze allowlist): `io.flutter.plugins.imagepicker.ImagePickerFileProvider`
(`${applicationId}.flutter.image_provider`) and every `mobile_scanner`/ML Kit component. `allowBackup="false"` and the
data-extraction rules stay as they are (11), so models, vault files and captures never leave the phone through backup.

---

## 16. Data and the day-1 migration (MEDIA-VOICE §10 of the map; 05 binds formats)

### 16.1 What the native app reads in place (nothing is moved, nothing is re-downloaded)

| Data | Format | Owner | Note |
|---|---|---|---|
| `flutter.voice.selected_pack` | string `base`/`small`/`tiny` (default `base`, unknown → `base`) | `:engine:media` | adopted prefs file (05 §3) |
| `flutter.voice.language` | enum **name** (`auto`, `detect`, `english`, `arabic`, …) | `:engine:media` | old installs stored only `auto`/`english`/`arabic`; unknown → `auto` (B-MEDIA-022, 028) |
| `flutter.voice.auto_send` | bool, default false | `:engine:media` | |
| `flutter.voice.read_aloud_natural` | bool, default false | `:engine:media` | reset when the last natural pack is removed |
| `flutter.oc.readAloudConsent` | bool | `:engine:media` | nobody is asked again |
| `flutter.oc.pendingPromptPhoto` | JSON (§10.5) | `:engine:composer` | recovered before chats open |
| `flutter.oc.draftAttachmentVault` | bool marker | `:engine:composer` | the sweep collects vault files only after it is set |
| `flutter.oc.sessionDrafts` | JSON map (refs only, no payloads) | `:engine:composer` | |
| `flutter.oc.readerPreferences.<id>` | JSON `{sourceFirst?, wrapCode?}` | `:engine:settings` | swept with the profile |
| `flutter.oc.tips` | JSON (includes `hold-mic`) | `:engine:settings` | nobody is re-taught |
| `files/voice_models/**` | §7.2 | `:engine:media` | marker shape kept exactly; Flutter can still read it after a rollback |
| `files/draft-attachments-v1/**`, `files/prompt-photo-recovery-v1/**`, `files/prompt-stash-attachments-v1/**` | data-URL text blobs (05 §5) | `:engine:composer` | |
| `cache/…` image_picker copies | JPEG/PNG | migration step M16 | the migration never clears `cache/` (05 §7.7) |
| `shared_prefs/voice_permissions.xml`, `camera_permissions.xml` | `microphone_requested`, `camera_requested` | `:android:platform` | untouched; without them a permanent denial would read as "denied" once |
| Runtime grants (`RECORD_AUDIO`, `CAMERA`, `POST_NOTIFICATIONS`) | OS-held | — | survive `install -r` with the same signer |

New, native-only: `files/voice_models/<pack>-<rev>/.native-verified.json` (M4), `files/camera-capture-v1/`,
`cache/share/`. Flutter ignores all three, so a rollback to 2.1.0+57 still works.

### 16.2 First native launch

- No synchronous media work in `OcApplication.onCreate` (ADR §9.7); nothing media-related is in the migration gate's
  critical path except M16 below.
- The audit (ADR §9.7 step 5) counts voice-model bytes per pack and vault files (05 already lists them).
- Each pack's first `installed()` call after the update runs the full SHA-256 pass once and writes the stamp (M4); the
  Settings row shows "Checking the speech model…" while it runs.

### 16.3 M16: the image_picker leftovers (C10-9; runs inside `MigrationRunner`, `:android:migration`)

Only when `flutter.oc.pendingPromptPhoto` exists **and** has no `path` **and** no `ref`:

1. Read `shared_prefs/flutter_image_picker_shared_preference.xml` (plugin file, `getSharedPreferences` is allowed in
   `:android:migration`, Konsist rule 7).
2. Take the first existing, non-empty file among: the string set `flutter_image_picker_image_path`, then the string
   `flutter_image_picker_pending_image_uri` (a path under `cache/`).
3. Write it into the record's `path` (through the adopted prefs writer lane) and set a marker
   `native.migration.v1.pendingPhoto = done`. Never delete the plugin file (05 deletes it at v3).
4. If nothing is found, set `failed = true` in the record so the composer says once: "The photo you took before the
   update could not be found." (`PhotoUnavailable`), then clears it.

### 16.4 Upgrade-journey additions (ADR §10.2; 05 J1)

Seed on the Flutter 2.1.0+57 AVD: `tiny` installed and selected, language `arabic`, auto-send on, read-aloud consent
given, natural voice off; a draft with two photo attachments; a `oc.pendingPromptPhoto` record whose `path` points into
`cache/`; microphone permanently denied (two denials) and camera granted. Then `adb install -r` the native APK and
check, **with the network turned off** (proves no re-download):

1. Settings › Voice typing shows Compact as "Installed", language Arabic, auto-send on (B-MEDIA-022; B-MIGR-009).
2. Tapping the mic shows the "Android blocks the microphone…" state with **Allow microphone in Android settings** (the
   three-state memory survived).
3. The pending photo is in the recorded conversation's draft (B-STATE-088), and the two draft photos show thumbnails
   (B-STATE-016).
4. "Read reply prose" reads without asking for consent again.
5. `files/voice_models/**` SHA-256 manifest is byte-identical before and after (02 S-4); only `.native-verified.json`
   is new.

---

## 17. Privacy and security invariants (each has a test; breaking one is a security regression)

| # | Invariant | Enforced by |
|---|---|---|
| S1 | Audio never leaves the phone; the only network use is the pinned HTTPS model download from `huggingface.co` at fixed revisions, SHA-256 verified | `ModelCatalogTest` (B-MEDIA-031), `PackInstallerTest` (B-MEDIA-025), Konsist: no `io.ktor` import in `:engine:media` except through `Downloader` |
| S2 | Transcripts, drafts, file names, paths, URLs and credentials never reach logs, diagnostics or notification text | `RedactingWriter` (03); `VoiceDownloadNotificationTest` (fixed strings only; B-ANDR-102); `MediaFailuresTest` (details are shapes or tokens) |
| S3 | Read aloud is consented once, offline voices only, never touches the engine before consent | `ReadAloudConsentTest` (B-MEDIA-009) |
| S4 | Untrusted PDFs are parsed only in the isolated, permission-less process; descriptors are unlinked before writing; the PNG header is checked before decoding | `LocalPdfContractTest` (B-MEDIA-004), instrumented `LocalPdfServiceTest` (§20.4) |
| S5 | SVG renders only through `SanitizedSvg`; no WebView in previews | `StaticSvgTest` (B-CHAT-107); Konsist: `SvgDecoder` only in `:androidApp`'s loader; `KitSvgImage(SanitizedSvg)` signature |
| S6 | XML input never resolves a DTD or an external entity | `XmlPullTest` (billion-laughs, external entity, DOCTYPE fixtures all refused) |
| S7 | Coil never fetches a URL; no disk cache; a deleted profile's images leave the memory cache | `AppImageLoaderTest`, `ImageResolverEvictTest` |
| S8 | A scanned or pasted pairing code is never stored, rendered, put in semantics or logged | `PairingScannerSecretTest` (B-ANDR-049, B-PROTO-069, B-SEC-107) |
| S9 | Wi-Fi share is explicit per file, 15 minutes, Wi-Fi only, GET/HEAD only, 192-bit token, constant-time compare, `nosniff`, `no-store`, never app-private files or secrets | `ShareHttpTest`, `PathSafetyTest`, `WifiShareTest` (B-MEDIA-034) |
| S10 | A recovered photo whose payload looks like a secret is not attached; identities that change under redaction are refused | `PendingPhotoRecoveryTest` (B-MEDIA-008, B-SEC-151) |
| S11 | No raw exception text in any media copy; technical text only under Details, redacted | `MediaFailureWordsTest` (exhaustive `when` over `MediaProblem`), Konsist rule: no `.message` read in `:engine:media` |
| S12 | Copy of file contents is verbatim; chat text copy is redacted | `ViewerCopyTest` (B-MEDIA-001) |
| S13 | The microphone never opens while the app is in the background or behind another page | `VoiceControllerLifecycleTest` (B-MEDIA-020 "a background-finished download waits at ready") |
| S14 | Nothing in media phones home; the analytics hooks (§19.5) carry no content | `MediaAnalyticsTest` (field allowlist) |

---

## 18. Performance budgets and how each is measured

Targets marked [U] are first guesses; §22 proofs replace them with measured numbers before the slice ends.

| What | Budget | Measured by |
|---|---|---|
| Cold start cost of media | **0 ms**: no media class touched in `Application.onCreate`; sherpa's `System.loadLibrary` runs on first `OfflineRecognizer`/`OfflineTts` construction | Macrobenchmark `StartupTimingMetric` with and without `:android:media` bindings (identical medians ± 5 ms) |
| Whisper `base` real-time factor on NX721J | ≤ 0.35 (a 30 s chunk in ≤ 10.5 s) [U] | `SpeechBenchmark` (instrumented, release) on the 3-minute WAV fixture; reports RTF per pack, load time, peak RSS |
| Whisper `small` peak RSS | ≤ 1.0 GB [U]; if over, `small` is never chosen automatically below 6 GB total RAM | same, `Debug.getMemoryInfo` + `dumpsys meminfo` |
| Warm model load | `base` ≤ 1.5 s, `small` ≤ 4 s on NX721J [U] | same |
| Words after **Done** | ≤ the decode time of the final partial chunk (no reload) | `VoiceControllerTimingTest` with a fake recognizer + the device benchmark |
| Kokoro first sound | ≤ 3 s for a 200-character sentence on NX721J (cold), ≤ 1 s warm [U] | `SynthesisBenchmark` |
| Kokoro RTF | ≤ 0.5 [U]; over 1.0 → evaluate MediaPipe Kokoro-GPU after the switch | same |
| QR decode | p95 < 30 ms per 720p frame; first lock < 1 s | `ScannerBenchmark` (zxing-cpp timing) |
| PDF page | p95 ≤ 1.5 s including bind on NX721J [U] | `PdfPageBenchmark` |
| Photo prepare | ≤ 400 ms for a 12 MP JPEG on NX721J [U] | `ImageCodecBenchmark` |
| Model re-hash (375 MB) | ≤ 4 s on NX721J [U]; never on the main thread; never on the start path | `VerifiedStampBenchmark` |
| Level meter | 0 recompositions of `KitComposer` per level update | recomposition-count test (ADR §11 P4 harness) |
| Gallery swipe and PDF scroll | no frame over budget (`FrameTimingMetric` p95 < 8.3 ms at 120 Hz) | Macrobenchmark journeys "open gallery, swipe 10" and "scroll a 50-page PDF" |
| Download progress | ≤ 10 UI updates/s, ≤ 1 notification per 1 % | `PackInstallerTest` with a fake clock |
| APK | §2.5 | T2 size gate |

---

## 19. UI rules applied to media (06 owns the parts; these are the media-specific decisions)

### 19.1 Owner rules, one line each

| Rule | In this section |
|---|---|
| Android-native feel to the maximum | system Photo Picker, system camera app, SAF dialogs, system share sheet; platform ripple on tray buttons; `KitHaptic` on hold start/cross/send and on QR lock; predictive back closes viewer, gallery, scanner and sheets with the system animation |
| Smooth streaming | `KitImage` reserves space from known dimensions; level meter in the draw phase; transcript pictures never jump |
| 150–250 ms motion, reduced motion | tray fold + fade, pill phase cross-fades, image cross-fade 200 ms; all off under `MotionPolicy.Off`; GIFs show a still |
| Kit only | every media surface is a kit part (`KitComposerVoice`, `KitComposerTray`, `KitLevelMeter`, `KitViewer`, `KitPdfPage`, `KitImage`, `KitZoom`, `KitImageGallery`, `KitImageMarkup`, `KitQr`, `KitScanner`, `KitSheetHost`) |
| No glass | the tray, pill and viewer are solid; no blur on the scanner (dim only) |
| Simple, judge by what it removes | §0.2 lists what each choice removes; no in-app camera, no second picker, no extra process |
| Plain words, never raw errors | `Failure.Media` → `strings_media_failures.xml`; sizes in decimal MB; Details fold for the rest |
| Obvious options as rows | Photos / Camera / File in the tray; Voice input and Voice conversation as rows; Save / Share / Open with as rows; Paste image from clipboard as a row; Paste it instead on every scanner stage |
| No new bar or row level | the voice pill replaces the composer field in place; the tray grows the existing pill by one row (owner concept C); the Wi-Fi sheet is a local sheet |
| Actions name their target | "Download Balanced (161 MB)", "Delete Balanced (160 MB)", "Save report.pdf", "Share on Wi-Fi", "Stop reading aloud", "Remove natural voice (148 MB)" |
| Steer, never queue | dictation and auto-send go through the one send path; a conversation cannot send while the agent works |
| Hard max widths | level meter `KitMaxWidth.reading`; `KitQr` ≤ 240 dp; viewer prose 720 dp; sheets ≤ 640 dp on medium |
| Frame text 1.3×, reading text full | pill words, tray labels, chips, viewer frame = frame text; viewer body, transcripts, licence texts = reading text |
| Arabic RTL | layout mirrors; level meter, progress bars and the QR do **not** mirror; table data stays LTR; mic hold cancel distance is direction-free; spoken language is independent of layout direction |
| Teach hidden gestures | `hold-mic` tip; pinch is never the only zoom path (zoom buttons) |

### 19.2 Copy (one-off ARB conversion, ADR §7.5; files per feature)

| ARB family (count) | XML file in `:ui:words` | Owner |
|---|---|---|
| `kitVoice*` (17), `kitViewer*` (19), `kitMarkup*` (17), `kitGallery*` (8), `kitScanner*` (4), `kitQr*` | `strings_kit_content.xml`, `strings_kit_chat.xml` | kit owners |
| `voiceMode*`, `voiceConversation*`, `voiceAutoSetup*` (22), `photo*` (13), `chatAttachment*` (7), `chatUiAttach*` (7), `draftAttachments*`, `composerTool*`, `composerTray*`, `codexTextOnlyPrompt`, `tipHoldMic*` | `strings_chat_composer.xml` | composer feature |
| `readAloud*` (21) | `strings_chat.xml` | chat lead |
| `voiceSetup*`, `e7VoiceUi*` (71), `vptt*` (11), `naturalVoice*` (8), `voiceNotices*` | `strings_settings.xml` (voice section) | settings feature |
| `file*` (59), `filePdf*` (5), `filePreview*` (6), `readerUi*` (81), `fileShare*` (13) | `strings_project.xml` | project feature |
| `e7SetupScan*`, `e7SetupCamera*`, `pairingScanner*` | `strings_servers.xml` | servers feature |
| every `MediaProblem` sentence and fix label | `strings_media_failures.xml` | l10n owner (exhaustive `when` in `MediaFailureWords`) |
| `native_voice_setup/downloading/preparing/ready/failed` | `strings_os.xml` (feeds Android `res`) | l10n owner |

Seven languages (en, ar, es, ja, pt, ru, zh). Language names in the voice-language picker are authored in their own
script and never translated.

### 19.3 Accessibility

- Mic button: label "Talk instead of typing"; a TalkBack custom action "Hold to talk" is **not** added (tap dictation is
  the accessible path; hold is a shortcut).
- Level meter excluded from semantics; the pill announces phase changes politely ("Listening", "Writing down what you
  said", "Sending in 3 seconds").
- Gallery: "2 of 5" is a live region; zoom buttons have labels; each picture's description is its name.
- QR: semantics label from the host only (data never spoken).
- Viewer: Find count is a live region; code lines are selectable text.

### 19.4 Licences (MEDIA-VOICE-16)

Settings › About › "Voice licences" (`about-voice-licences`): OpenAI Whisper (MIT), Kokoro (hexgrad, Apache-2.0),
sherpa-onnx (k2-fsa, Apache-2.0), ONNX Runtime (Microsoft, MIT). The Flutter `record` row disappears (AudioRecord is the
platform); new rows: zxing-cpp (Apache-2.0), Coil (Apache-2.0), Telephoto (Apache-2.0), qrose (MIT), CameraX
(Apache-2.0). Texts live in `:ui:words` `composeResources/files/notices/<id>.txt`; the viewer reflows 80-column text into
paragraphs (`reflowLicenseText` parity); one action **Open <project> website** through `openExternalLink`.
`THIRD_PARTY_NOTICES.md` drops the ML Kit section.

### 19.5 Analytics hooks (owner decision 2026-11-01: analytics core with BASIC and opt-in DETAILED tiers; core owned by 16)

Media emits only these events, all **DETAILED** (opt-in), with no content, names, paths, sizes of user files or
languages of speech:

| Event | Allowed fields |
|---|---|
| `voice_dictation_done` | `mode` (tap/hold/conversation/widget), `chunks` (bucket 1, 2–5, 6+), `ended_by` (`VoiceEnd`) |
| `voice_model_installed` | `pack` (`base`/`small`/`tiny`/`kokoro-en`), `resumed` (bool) |
| `read_aloud_started` | `engine` (system/natural), `automatic` (bool) |
| `attachment_added` | `kind` (photo/camera/file/office/keyboard/clipboard) |
| `qr_scan_result` | `result` (ok/wrong_code/denied/no_camera) |
| `wifi_share_started` | — |
| `media_failure` | `problem` (`MediaProblem` name only) |

---

## 20. Tests: native tests written first from the catalog

Naming (ADR §6.3 rule 12): `` `B-MEDIA-015 quiet after speech ends the recording after 1.5 s`() ``. Common tests run as
`desktopTest` on the VPS (T1); Android-only behaviour is Robolectric in `:android:media/src/test` or
`:android:platform/src/test`; device rows are Maestro flows on the release build (T3). Fixtures are copied from the
Flutter tests named in each row's `sources` (`catalog/behaviors.jsonl`).

### 20.1 The 34 media rows

| Row | Level | Native test class (module) | Fakes / fixtures |
|---|---|---|---|
| B-MEDIA-001 strict UTF-8 preview, Save keeps bytes | unit | `FilePreviewTest` (engine) | invalid UTF-8 and NUL fixtures |
| B-MEDIA-002 pack by RAM; voice by locale | unit | `AutomaticPackTest`, `VoicePickTest` (engine) | device probe values |
| B-MEDIA-003 level meter thresholds | unit | `LevelMeterTest` (engine) | — |
| B-MEDIA-004 PDF transport budgets | contract | `PdfResultValidatorTest` (engine) + `AndroidPdfPageRendererTest` (Robolectric) | recorded results: wrong page, oversize PNG, bad IHDR |
| B-MEDIA-005 natural pins, script routing, splitter | unit | `ModelCatalogTest`, `ReadLanguageTest`, `SentenceSplitterTest` (engine) | — |
| B-MEDIA-006 Office as text | unit (+E2E-12) | `OfficeTextTest` (engine) | the Flutter `.xlsx`/`.docx` fixtures (openpyxl, LibreOffice), name-only stub |
| B-MEDIA-007 gallery picking, room, single picker, cancel | integration (+E2E-12) | `AttachmentIntakePickTest` (engine) | `FakeActivityResults`, `FakeImageCodec` |
| B-MEDIA-008 picker lifecycle, recovery, profile removal | integration | `PendingPhotoRecoveryTest`, `AttachmentIntakeCameraTest` (engine) | `FakeKeyValueStore`, `FakeDraftStore`, temp files |
| B-MEDIA-009 read-aloud lifecycle | integration | `ReadAloudTest` (engine) | `FakeSystemTts`, `FakeAudioSession` |
| B-MEDIA-010 3-minute dictation in ordered chunks; failure keeps words | integration (+E2E-13) | `VoiceControllerChunkTest` (engine) | `WavCapture` (the Flutter 3-minute fixture as PCM), `FakeRecognizer` that fails chunk 4 |
| B-MEDIA-011 RAM gate, offer, automatic blocks | unit | `PackSupportTest` (engine) | — |
| B-MEDIA-012 install resumes after a stop | integration | `PackInstallerResumeTest`, `VoiceSetupComponentTest` (engine) | `MockEngine` via `:core:testing` |
| B-MEDIA-013 failures as kinds; remove rules | unit | `VoiceInstallFailureTest` (engine) | — |
| B-MEDIA-014 PCM16 normalisation, odd bytes, 30 s | unit | `Pcm16ChunkerTest` (engine) | split-buffer vectors |
| B-MEDIA-015 silence end rules | unit | `SilenceDetectorTest` (engine) | — |
| B-MEDIA-016 sendable words; no commands | unit | `AutoSendPolicyTest` (engine) | multilingual vectors from `voice_auto_send_test.dart` |
| B-MEDIA-017 auto-send off by default, countdown | integration (+E2E-13) | `AutoSendFlowTest` (`:feature:chat-composer` presentation) | `TestClock` |
| B-MEDIA-018 reading voice by locale | unit | `VoicePickTest` (engine) | — |
| B-MEDIA-019 consent and network gating | integration (+E2E-13) | `VoiceSetupConsentTest` (engine) | `FakeSetupNetworkSource` |
| B-MEDIA-020 setup states, background finish waits | integration | `VoiceSetupFlowTest` (engine) | fake host lifecycle |
| B-MEDIA-021 restart, hand-off, ownership | unit | `VoiceSetupHandOffTest` (engine) | — |
| B-MEDIA-022 pack and language persist | migration | `VoicePrefsAdoptionTest` (Robolectric, `:core:data` a) + J1 | a seeded `FlutterSharedPreferences.xml` |
| B-MEDIA-023 robust recording, stale generations | unit | `VoiceControllerCancelTest` (engine) | — |
| B-MEDIA-024 preflight; merge around selection | unit | `DraftMergeTest`, `PackSupportTest` (engine) | — |
| B-MEDIA-025 checksum, exact `Content-Range`, marker only when verified | contract | `PackInstallerTest` (engine) | `MockEngine`: 206 good/bad range, 200 restart, corrupt body |
| B-MEDIA-026 stalls, idle timeout, cancel in finalization | integration | `PackInstallerStallTest` (engine) | `MockEngine` with a stalled body, `TestDispatchers` |
| B-MEDIA-027 rollback, same-pack lock, remove waits | integration | `PackInstallerPublishTest` (engine) | failing `atomicMove` fake |
| B-MEDIA-028 language resolution | unit | `VoiceLanguageTest` (engine) | 14 vectors |
| B-MEDIA-029 language notes | unit | `VoiceLanguageNoteTest` (engine) | — |
| B-MEDIA-030 stale manager work | unit | `ModelPacksStaleWorkTest` (engine) | — |
| B-MEDIA-031 pins and notices | contract | `ModelCatalogTest` (engine), `VoiceNoticesTest` (engine), `SherpaAarPinTest` (`:quality`) | `third_party/SHA256SUMS` |
| B-MEDIA-032 honest answers without capture | unit | `UnavailableMediaBindingsTest` (`:desktopApp` test), `AndroidDeviceProbeTest` (Robolectric) | — |
| B-MEDIA-033 recognizer config mapping | unit | `SherpaConfigsTest` (`:android:media` JVM test; config data classes only, no native load) | — |
| B-MEDIA-034 Wi-Fi share | integration (+E2E-14) | `ShareHttpTest`, `PathSafetyTest`, `WifiShareTest` (engine); `AndroidLanFileServerTest` (JVM, real socket on 127.0.0.1 through a test `WifiAddressSource`) | — |

### 20.2 Rows of other areas that this section's work packages turn green

| Rows | Test class | WP |
|---|---|---|
| B-CHAT-009, B-CHAT-010 (MIME gate, bounded read) | `PromptAttachmentMimeTest`, `BoundedReadTest` (engine) | MV-2 |
| B-CHAT-021…024 (CSV/TSV) | `DelimitedTextTest` (engine) | MV-4 |
| B-CHAT-107 (static SVG) | `StaticSvgTest` (engine) | MV-4 |
| B-KIT-026 (empty QR refused) | `KitQrTest` (kit-content) | MV-8 |
| B-NAV-028 (viewer header path) | `KitViewerHeaderTest` (kit-content) | MV-4 |
| B-ANDR-049 (scanner permission, no camera, never invents answers) | `PairingScannerStagesTest` (`:feature:servers` presentation) | MV-9 |
| B-ANDR-101, B-ANDR-102 (voice probes, setup notification) | `AndroidSetupNetworkTest`, `AndroidDeviceProbeTest` (Robolectric); `ModelDownloadNotificationTest` (`:android:background`) | MV-6 |
| B-L10N-024 (voice failures translated) | `MediaFailureWordsTest` (`:ui:words`, 7 languages) | MV-6 |
| B-LINUX-220 (voice component optional, off by default) | `VoiceSetupComponentTest` (engine) | MV-6 |
| B-STATE-088 (recovered photo keeps its origin) | `PendingPhotoRecoveryTest` (engine) | MV-2 |
| B-PROTO-064…069, B-SEC-107, 108 (pairing parser) | owned by 04 | — (MV-9 only hands text over) |
| B-ANDR-074…077 (share in/out) | owned by 11 | — |
| B-STATE-016…018, 134…139, B-SEC-061, 151 (vault, stash, saved prompts) | owned by 05 / 09 (`:engine:composer`) | — (MV-2 depends on them) |
| B-STATE-021…023 (interaction defaults incl. the voice locale rule) | owned by 05 / settings | — (MV-7 reuses `VoicePick`) |

### 20.3 Row amendments for `native/catalog/scope.jsonl` (02 §3.6; the test-writer lead records them)

| Row | Amendment | Why |
|---|---|---|
| B-MEDIA-025 | "cancelled downloads never create a marker; a cancel **during download keeps the `.part`** for resume" (the Flutter assertion `cancelledStore.files isEmpty` at `voice_downloader_test.dart:278` no longer holds) | M3 / D10-1 |
| B-MEDIA-007 | "photos are scaled down **by the app before the bytes are attached**" (not by the picker) | M10 |
| B-MEDIA-008 | "a lost native result retains the original destination after restart" now means: the record's `path` names the capture file | §10.5 |
| B-MEDIA-031 | "the manifest pins runtime dependencies" means the vendored AAR's SHA-256 and version 2.0.1, plus `RECORD_AUDIO` in the merged manifest | §2.2 |
| B-MEDIA-032 | "on Android the probe uses the channel" means the `AndroidDeviceProbe` binding | §4.5 |

Catalog top-up from the freeze tip (02 §3.5; next free ids, written by the test-writer lane before slice 1 closes):
**B-MEDIA-035** clipboard image paste (sources `test/composer_paste_image_test.dart`,
`android/…/ClipboardImagePolicyTest.kt`), **B-MEDIA-036** Wi-Fi share path safety for phone files (`wifi_file_share_test.dart`
additions in `6ce60d6f0`, `637ffea4c`), **B-MEDIA-037** open a phone file with another app and install an `.apk` through the
system installer (`b4954ae4f`).

### 20.4 Native-only checks (no Flutter equivalent; the Flutter bridges had no Kotlin tests at all)

| # | Check | Where | Tier |
|---|---|---|---|
| N1 | Release smoke: sherpa classes survive R8 and `tiny` decodes a 2 s WAV | instrumented, release build | T3 |
| N2 | APK inspection: no `libsherpa-onnx-c-api.so`, no ML Kit, every `.so` 16 KB aligned | `tool/native/check_so_alignment.py` + `apkanalyzer` | T2 |
| N3 | `TakePicture` with CAMERA denied leaves no pending record and shows plain words | `AttachmentIntakeCameraTest` + Robolectric `ActivityResults` | T1 |
| N4 | Android 17 `throw` hardening: background read aloud fails closed, no crash | emulator API 37 | T3 |
| N5 | `AudioSession` rules A1–A7 | `AndroidAudioSessionTest` (Robolectric `ShadowAudioManager`) | T1 |
| N6 | `LocalPdfService`: encrypted fixture → `PdfEncrypted`; 201-page fixture → page 200 refused; a pathological PDF trips the 10 s watchdog; killing `:local_pdf` → `PdfFailed`; the input path is gone from `cacheDir` while rendering | instrumented `LocalPdfServiceTest` | T3 |
| N7 | `ShareHttp` fuzz: 10,000 random request heads never crash, never answer 200 without the exact token path | `ShareHttpFuzzTest` (engine) | T1 |
| N8 | `XmlPull` hostile input: DOCTYPE, billion laughs, external entity, 10,000-deep nesting all refused | `XmlPullTest` (engine) | T1 |
| N9 | Office zip bomb (one entry inflating to 1 GiB) refused within 64 MiB | `OfficeTextTest` | T1 |
| N10 | `ImageCodec`: EXIF-rotated JPEG comes out upright on API 26 (Robolectric) and API 28+ (instrumented); HEIC below 28 → `PhotoUnsupported` | `AndroidImageCodecTest` | T1/T3 |
| N11 | Level updates do not recompose `KitComposer` | recomposition-count harness | T1 |
| N12 | Component freeze lists the removed image_picker provider and ML Kit components as intentional | `:quality` freeze test | T2 |
| N13 | QR readability: 50 fixture payloads drawn by `KitQr` decode with ZXing core | `KitQrDecodeTest` (kit-content desktop test) | T1 |

### 20.5 Device recipes for the emulator (PC, one lock holder; never the owner's phone for basic passes)

- **Microphone from a WAV** [U: exact emulator flags confirmed in MV-0]: start the AVD with host audio (`-allow-host-audio`, then `adb emu avd hostmicon`); on the
  PC create a virtual source and play the fixture into it:
  `pactl load-module module-null-sink sink_name=ocmic` ·
  `pactl load-module module-remap-source master=ocmic.monitor source_name=ocmic_src` ·
  `pactl set-default-source ocmic_src` · `paplay --device=ocmic fixtures/dictation-3min.wav`. Restore the default
  source after the run (`pactl unload-module` by the exact module ids printed).
- **Camera with a QR:** `-camera-back virtualscene`, then Extended controls › Camera › Virtual scene images › add the
  pairing QR PNG (generated by `opencode2 pair` in a dark terminal) as a wall poster [U: P-M3 confirms; fallback
  `-camera-back webcam0` with a `v4l2loopback` device fed by `ffmpeg -loop 1 -re -i pair-qr.png -f v4l2 /dev/videoN`].
- **Network classes:** `adb shell svc wifi disable|enable`, `adb shell svc data disable|enable`.
- **Process death with the camera open:** open Camera from the tray; while the camera app is in front run
  `adb shell am kill io.github.eslamasabry.opencode_mobile` (kills only a background process); take the picture; the
  photo must land in the recorded conversation.
- **Wi-Fi share from inside the device:**
  `adb shell 'printf "GET /<token>/<name> HTTP/1.1\r\nHost: x\r\n\r\n" | toybox nc <wlan-ip> <port>'` (and the same with
  `POST`, a wrong token, and after Stop).
- **Android 17 audio:** `adb shell cmd audio set-enable-hardening throw` before the read-aloud background step.

---

## 21. Work packages

Owners follow the ADR recipe: Codex builds engine logic against the contract, Claude (Sonnet) builds kit parts and
features, Opus takes the hardest state machines. One agent per package, its own worktree; `:android:media`'s build file,
manifest fragment and `MediaBindings.kt` belong to the media lead. Estimates in agent-weeks (aw) [U, re-planned at M0].

| WP | Slice | Owner (model) | Contents | Write set | Depends on | Acceptance | Est. |
|---|---|---|---|---|---|---|---|
| MV-0 | 1–3 | media lead (Opus) | proofs P-M1…P-M7 (§22) | `native/tools/proofs/media/**`, `docs/qa/native/media-proofs-*` | ADR P1 skeleton | each proof's acceptance line recorded with numbers | 1.5 |
| MV-1a | 3 | Codex | pure logic for attachments and previews: `attach/` rules, `MagicBytes`, `PromptAttachmentMime`, `BoundedRead`, `AttachmentLimits`, `office/`, `xml/`, `preview/` adapters, `DelimitedText`, `StaticSvg`, `ViewerFind`, `Truncate`; C10-1/C10-2 primitives with the core owner | `engine/media/src/commonMain/**/{attach,office,xml,preview}/**`, tests | contracts s3 | B-MEDIA-001, 006, B-CHAT-009/010/021–024/107 green at T1; N7–N9 | 2 |
| MV-1b | 5 | Codex | pure logic for voice, models, read aloud, conversation, share, QR rules | `engine/media/src/commonMain/**/{voice,models,setup,readaloud,conversation,share,qr}/**`, tests | contracts s5 | B-MEDIA-002, 003, 005, 011, 013–016, 018, 021, 023–025, 028–031, 034 (pure parts) green at T1 | 3 |
| MV-2 | 3 | Sonnet (Android), Claude (UI) | `AttachmentIntake`, `PendingPhotoRecovery`, `AndroidImageCodec`, `AndroidClipboardImages`, `ContentAccess`/`ActivityResults` use, tray + tools rows + chips + paste row, camera flow, keyboard content | `android/media/**/{image,clipboard}/**`, `feature/chat-composer/**` (attachment files only), `engine/media/**/attach/**` | MV-1a, `:engine:composer` drafts + vault (05/09), 03 `ActivityResults` | B-MEDIA-007, 008, B-STATE-088 green; E2E-12 steps 1–2 green on the release build; N3, N10 | 3 |
| MV-3 | 3 / 5 | kit-content owner (Sonnet) | slice 3: `KitImage`, `KitImageResolver`, `AppImageLoader`, `ImageResolver`; slice 5: `GalleryIndex`, `KitImageGallery`, `KitZoom`, `KitImageMarkup`, `MarkupRaster` | `ui/kit/**/image/**`, `ui/kit-content/**/{gallery,markup,zoom}/**`, `android/media/**/image/AndroidMarkupRaster.kt`, `androidApp/**/AppImageLoader.kt` | KN3 / KN5 (06) | chips and sent photos load through the resolver (S7 tests); gallery swipe-only-when-not-zoomed test; markup export at full resolution ≤ 9 MiB | 2.5 |
| MV-4 | 5 | Sonnet; Opus reviews PDF | viewer adapters wiring, `KitViewer` bodies, find, reader prefs, PDF client + service port, Save / Copy / Share / Open with, turn and tool output loaders | `ui/kit-content/**/viewer/**`, `android/media/**/pdf/**`, `androidApp/**/LocalPdfService.kt`, `feature/project/**` (viewer files) | MV-1a, MV-3, 07 `ProjectRoute.FileView` | B-MEDIA-004, B-NAV-028 green; N6; E2E-14 step 1 | 3 |
| MV-5 | 5 | Opus (`VoiceController`, `AudioSession`), Sonnet (capture, recognizer, pill wiring) | `AndroidAudioSession`, `AndroidAudioCapture`, `SherpaSpeechRecognizer`, `VoiceController`, hold detector wiring, auto-send flow, permission recovery, widget voice panel | `android/media/**/{audio,speech}/**`, `engine/media/**/voice/**`, `feature/chat-composer/**` (voice files), `feature/widget-panels/**` (voice panel) | MV-1b, MV-0 P-M1/P-M2, KN5 mic hold + level meter | B-MEDIA-010, 017, 020, 023, 033 green; N1, N5, N11; E2E-13 steps 3–4 | 4 |
| MV-6 | 5 | Codex (engine), Sonnet (UI, notification) | `ModelPacks`, `PackInstaller` (over `Downloader`), stamp, `AndroidDeviceProbe`, `AndroidSetupNetwork`, `VoiceSetup`, Settings › Voice + model sheet, setup sheet, download notification (C10-12), phone-setup component | `engine/media/**/{models,setup}/**`, `android/media/**/device/**`, `feature/settings/**` (voice), `android/background/**` (model download notice only) | MV-1b, C10-7 in `:core:net` | B-MEDIA-012, 019–022, 025–027, 030, 032, B-ANDR-101/102, B-L10N-024, B-LINUX-220 green; E2E-13 steps 1–2 | 3.5 |
| MV-7 | 5 | Opus (`ReplyWatch`, `VoiceConversation`), Sonnet (TTS port, Kokoro) | `AndroidSystemTts`, `SherpaSpeechSynthesizer`, `AndroidPcmPlayer`, `ReadAloud`, consent and voice sheets, message menu items, conversation strip | `android/media/**/{tts,speech/SherpaSpeechSynthesizer.kt,audio/AndroidPcmPlayer.kt}`, `engine/media/**/{readaloud,conversation}/**`, `feature/chat/**` (read-aloud files) | MV-5 (`AudioSession`), MV-6 (packs), `:core:markdown` `forSpeech` | B-MEDIA-005, 009, 018 green; N4; Kokoro stop is immediate on the emulator | 3 |
| MV-8 | 5 | Codex (rules), Sonnet (server, sheet), kit-content owner (`KitQr`) | `WifiShare`, `PathSafety`, `ShareHttp`, `AndroidLanFileServer`, `WifiAddress`, the sheet, `KitQr` | `engine/media/**/share/**`, `android/media/**/share/**`, `feature/project/**` (share sheet), `ui/kit-content/**/qr/KitQr.kt` | MV-1b | B-MEDIA-034, B-KIT-026 green; N13; E2E-14 step 2 | 2 |
| MV-9 | 3 | kit-content owner (Sonnet) | `KitScanner` + `AndroidScannerSlot` (CameraX + zxing-cpp), pairing screen stages, camera permission use | `ui/kit-content/**/scanner/**`, `feature/servers/**` (scanner files) | KN3, 03 `PermissionGate`, 04 pairing parser | B-ANDR-049 green; P-M3 numbers met; `dependencyInsight` shows no ML Kit | 1.5 |
| MV-10 | 6 | migration owner (Opus) + Sonnet | M16, upgrade-journey additions (§16.4), licence texts and rows, `THIRD_PARTY_NOTICES.md`, the device recipes as Maestro flows | `android/migration/**` (M16 only), `ui/words/composeResources/files/notices/**`, `feature/about/**` (licence rows), `e2e/media/**` | all MV | J1 media checks green; E2E-12/13/14 green on the release build | 1.5 |

Total ≈ 30.5 aw [U]. Critical path: MV-0 P-M1 → MV-5 → MV-7. MV-2 and MV-9 are the only slice-3 media work.

---

## 22. Proofs (each ≤ 2 days, each with a fallback)

| # | Proof | Acceptance | Fallback |
|---|---|---|---|
| P-M1 | Vendored sherpa-onnx 2.0.1 AAR in `third_party/maven`, the keep rule, `useLegacyPackaging`, excludes; release build on the API 37 emulator decodes a 2 s WAV with `tiny`; `OfflineTts` throws `IllegalArgumentException` on a missing file | N1 and N2 pass; arm64 speech libs ≤ 9.9 MB compressed | build the AAR on the VPS from the release `jniLibs` tarball with `android/SherpaOnnxAar` (README for 2.0.1) and pin that hash |
| P-M2 | Whisper tiny/base/small on a 4 GB AVD and on NX721J (the `.native` preview flavour, installed only while the owner is present): RTF, warm load time, peak RSS, low-memory-killer behaviour during a 3-minute recording | numbers recorded; §18 targets confirmed or replaced | release the session between chunks on < 6 GB phones; never choose `small` automatically below 6 GB |
| P-M3 | CameraX 1.6.2 `CameraXViewfinder` + zxing-cpp on the emulator virtual scene and NX721J with a real `opencode2 pair` QR in a dark terminal (inverted) | p95 < 30 ms; first lock < 1 s; inverted code read | `tryHarder = true`; then 1.7.0 for built-in focus; last resort ZXing core 3.5.4 |
| P-M4 | Kokoro: `generateWithCallback` returning 0 stops synthesis; first sound and RTF on NX721J; stop latency | stop ≤ 150 ms; first sound ≤ 3 s cold | synthesize sentence by sentence with `generate()` and stop between sentences |
| P-M5 | Android 17 `throw` hardening on an API 37 image: background reading stops cleanly; the reply-finished notification sound (system-played) under the `shortService` FGS still plays | no exception, sound plays | stop reading on `ON_STOP` as well as `ON_PAUSE` (already true); notification sound unaffected by design |
| P-M6 | Flutter `getApplicationSupportDirectory()` is `filesDir`: install Flutter 2.1.0+57 on the emulator, download `tiny`, `adb shell run-as … ls files/voice_models` | the pack dir is under `files/` | M-step in `MigrationRunner` moves the folder once (`atomicMove` per pack dir), never re-downloads |
| P-M7 | Keyboard content and paste: Gboard GIF commit read after `onReceive` returns; whether the text field offers **Paste** for an image-only clip with `contentReceiver` attached (Compose 1.13) | both observed on API 37 | copy into `cache/inserted/` inside the receiver; add the "Paste image" text-menu item (`JETPACK_AHEAD`) |
| P-M8 | (after the switch, optional) `VOICE_RECOGNITION` source vs `MIC`: levels and silence detection with NX721J recordings | detector rows still pass with re-tuned constants | stay on `MIC` |
| P-M9 | PDF: spawn cost of one isolated bind per page vs one per document | numbers recorded | stay per page (parity) |

---

## 23. Multiplatform notes (what changes when iOS or Linux desktop start)

| Seam | iOS phase | Linux desktop phase |
|---|---|---|
| `AudioCapture`, `PcmPlayer`, `AudioSession` | `AVAudioEngine` + `AVAudioSession` (category `playAndRecord` only while dictating) | `javax.sound` `TargetDataLine` / `SourceDataLine`; `AudioSession` no-op [U] |
| `SpeechRecognizer`, `SpeechSynthesizer` | sherpa-onnx C API xcframework through cinterop [U] | `sherpa-onnx-jvm-2.0.1.jar` + the Linux native-lib jar [U] |
| `SystemTts` | `AVSpeechSynthesizer` | speech-dispatcher, else `readAloud = false` |
| `ImageCodec`, `MarkupRaster` | `UIImage`/`CGImage` | Skia (`org.jetbrains.skia.Image`) |
| Pickers, save, share | FileKit 0.17.0 | FileKit 0.17.0 (XDG portal); no system share |
| `PdfPageRenderer` | PDFKit | Apache PDFBox 3.0.8 in a child JVM process (keeps the isolation idea) [U] |
| Scanner | `AVCaptureMetadataOutput` (QR built in) | none (paste only) |
| `LocalFileServer` | `Network.framework` listener | the same `ServerSocket` code moved to an `oc.jvm.library` module |
| `ClipboardImages` | `UIPasteboard` | AWT clipboard image flavor [U] |
| Primitives (`Sha256`, `RawInflate`, `secureRandomBytes`) | CommonCrypto, `zlib` via cinterop, `SecRandomCopyBytes` | same JVM actuals as Android |
| Everything in `:engine:media` commonMain | unchanged | unchanged |

Nothing in this section uses an Android type in commonMain; the nightly iOS klib compile (ADR §6.3) keeps it that way.

---

## 24. Decisions for the owner (work proceeds on the default)

| # | Decision | Default | Why the default |
|---|---|---|---|
| D10-1 | Keep a half-downloaded speech model when you tap **Cancel** (resume next time)? | **Yes** | A 375 MB model on mobile data should never restart from zero; **Remove** still deletes everything |
| D10-2 | Accept pictures and files shared **into** the app from other apps (today only text)? | **After the switch** | Parity first; it is a natural phone action, so it is the first media item after the switch |
| D10-3 | Add "Scan from a picture" next to the camera scanner (for a QR screenshot)? | **After the switch** | "Paste the code instead" already covers it |
| D10-4 | Keep model downloads alive when Android closes the app (a background download job)? | **No** | Downloads resume where they stopped (D10-1); one less background service |
| D10-5 | Dictation or reading aloud with the screen off or the app in the background? | **No** | Needs microphone/media foreground services and lock-screen controls; Android 17 silences background audio |
| D10-6 | F-Droid build: drop voice typing and the natural voice (Lite), or build sherpa-onnx and ONNX Runtime from source (Full)? | **Lite** for the first submission (after the rewrite); phone voices still read aloud | Building ONNX Runtime from source is weeks of work; the QR scanner is already Google-free |
| D10-7 | Use Google's new PDF viewer (search, select text) instead of the sealed renderer? | **No** | It parses untrusted PDFs inside the app and adds a second design language |
| D10-8 | An in-app camera instead of the system camera app for photos? | **No** | The system camera is the most native and removes a screen |
| D10-9 | Arabic natural voice? | **Wait**; the phone's voice reads Arabic | No lexicon-based Arabic Kokoro model exists yet |
| D10-10 | Stop sharing on Wi-Fi when the app goes to the background? | **No** (as today: up to 15 minutes) | Matches today; the sheet shows the time left |
| D10-11 | Add **Share <name>** (the system share sheet) to the file viewer's More, beside Save? | **Yes, at the switch** | The Android-natural way to hand a file to another app; about 40 lines |

---

## 25. Sources

- Plan: `01-architecture-decision.md` (§2.2, §3.3, §5.2, §6, §7, §8, §9.4–§9.9, §10.2, §11, §12), `02-principles-scope.md`
  (§0.1, §3.2, §3.5, §6.3, §7.9, §7.13), `03-architecture-modules.md` (§0.2 S11, §3.1, §6.6, §7.1, §10.1, §10.4, §10.5,
  §10.7, §11.2, §12), `04-protocols-networking.md` (§3.10, §7.9), `05-data-migration.md` (§2.5, §5, §7.7, Appendices A,
  C, D), `06-ui-kit-design-system.md` (§7.6–§7.8, §12.2, §18), `07-screens-navigation.md` (C21, C24, C25, D03, D05, E03,
  I11, M03, O-07-3).
- Map: `../map/media-voice.md` (all 50 features, §3–§13); `../map/android-platform.md` (14, 25, 26, 47);
  `../map/chat-genui.md` (19, 41).
- Research: `../research/media.md` (§0–§14), `../research/testing-perf.md` (§10 size, download module).
- Catalog: `../catalog/BEHAVIORS.md` (MEDIA rows), `behaviors.jsonl` (sources of every row in §20), `e2e-flows.md`
  (E2E-12, 13, 14).
- Flutter source (read-only): `/home/eslam/Storage/Code/wt/oc_app-batch1` — `lib/voice/*` (silence, controller,
  model_manifest, natural_voice, natural_synthesis), `lib/ui/kit/chat/kit_mic_hold.dart`, `lib/domain/office_text.dart`,
  `test/voice_downloader_test.dart:235-278`, `test/setup_voice_component_test.dart:100-170`,
  `android/app/src/main/AndroidManifest.xml`; freeze branch `int/freeze-2026-11-01` commits `9ac39cd68`, `92ed57977`
  (clipboard image), `7febdd764`, `637ffea4c`, `6ce60d6f0` (Wi-Fi share), `b4954ae4f` (open local file), `0ae129579`
  (F-Droid feasibility), `724806fdc` (Google-free scanner, unmerged by owner decision).
- Plugin source: `image_picker_android-0.8.13+25` `ImagePickerCache.java` (preference names), its manifest (provider
  authority, Photo Picker backport service).
- Checked today [V], 2026-11-01: Maven Central / Google Maven metadata — Coil 3.6.3, Telephoto 0.19.0 (1.0.0-alpha02
  latest), qrose and `qrose-encoder-matrix` 1.3.1 (sources read: `QroseEncoders.QR(errorCorrection, maskPattern)`,
  `QrErrorCorrection.M`, `Matrix2D`), CameraX `camera-compose` 1.6.2 / 1.7.0-rc01, zxing-cpp `android` 3.1.1,
  `exifinterface` 1.4.2, androidx.core 1.19.1; GitHub API — sherpa-onnx v2.0.1 published 2026-10-26; sherpa-onnx
  `kotlin-api` at tag v2.0.1 (`OfflineRecognizer(assetManager, config)`, `OfflineStream.release()`,
  `OfflineWhisperModelConfig`, `OfflineTts.generateWithCallback`, `OfflineTtsKokoroModelConfig.lexicon`, `free()`);
  Telephoto `ZoomableState.zoomFraction` at tag 0.19.0; developer.android.com "Copy and paste" (Compose
  `contentReceiver`, `hasMediaType(MediaType.Image)`); developer.android.com "Local network permission" (accepting an
  incoming TCP connection needs it at targetSdk 37).
- Owner memory: `freeze-owner-decisions-2026-11-01` (Google-free QR, F-Droid after the rewrite, analytics tiers),
  `dev-freeze-native-rewrite-2026-11-01`, `voice-model-memory-gate`, `gemma-assistant`, `roadmap-issues-2026-10-31`,
  `android-native-feel`, `micro-animations-wanted`, `text-scale-cap`, `no-raw-errors-rule`, `component-max-width-rule`,
  `teach-what-is-hidden`, `no-glass-rule`, `test-on-emulator-not-phone`, `no-owner-phone-screenshots`.
