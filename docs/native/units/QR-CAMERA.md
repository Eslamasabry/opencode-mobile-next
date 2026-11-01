# Unit QR-CAMERA: `:android:camera` (CameraX capture, zxing-cpp pairing scanner)

Date: 2026-11-01. Branch: `native/qrcam-2026-11-01`, created from `claude/qrcam-zxing-cpp-of9bxe` at `48af726f`.
Write set: `native/android/camera/**` (new module) and this report. Nothing outside it is committed.

Inputs read: `native/AGENTS.md`; `docs/native/units/INTEGRATE-W1.md` (flat `Failure`, `Failure.Media(MediaProblem)`),
`S3-MEDIAATT.md`; plan 01 (ADR rows for QR, `:android:media`), plan 03 §5 and `ExternalRules`, plan 10 §2.1, §2.7,
§10.4, §10.5, §14, §15, plan 12 (registry: zxing-cpp/CameraX rows), plan 15 (no QR-CAMERA row; nearest are
S3-KCCONTENT, MV-9 and S2-SERVERS); catalog rows B-ANDR-049, B-PROTO-064…069, B-SEC-107, B-MEDIA-008; Flutter
`test/pairing_scanner_test.dart` and `test/pairing_payload_test.dart`.

**Result.** The scanner's and capture's rules are implemented in `:android:camera` and green on the host JVM: 41 tests,
including QR **image fixtures** decoded through the real crop/latch/reject pipeline. The CameraX + zxing-cpp adapters
are written and were **compile-verified** (release variant) against CameraX 1.6.2 and zxing-cpp 3.1.1, but they are not
part of the committed build yet, because build-logic forbids those libraries in this module (§5.1).

Shipping state: implemented, verified locally (module tests; adapters compiled under a temporary, reverted local
catalog/rules edit), committed and pushed to the branch. **Not wired** into any graph, kit slot or screen; not run on a
device or emulator; no PR, no CI, no signing, no release. `master` and tags untouched.

Finish line (brief, no plan-15 row): CameraX capture for attachments and a zxing-cpp QR scanner for pairing codes,
Google-free, with a plain-words permission flow and tests on QR image fixtures. **Met at module level**; the on-device
part (P-M3 numbers, emulator scan) and the kit/screen wiring are open (§7).

---

## 1. What was built

Package root `io.github.eslamasabry.oc.android.camera` (`oc.android.library`).

| Area | Files | What it does |
|---|---|---|
| `scan/` | `ScanWindow.kt` | `cropFor(w, h, fraction)`: the centred square of `min(w,h) × fraction` (clamped 0.3…1, NaN-safe) in frame pixels — rotation-invariant, so it applies to the sensor buffer directly. `fractionFor(windowPx, previewShortSidePx)` maps the 240 dp window. |
| | `QrFrameDecoder.kt` | `fun interface QrFrameDecoder<F> { decode(frame): String? }` (never throws). `QrScanOptions.Pairing` = QR only, `tryInvert` (dark terminals), `tryRotate`, `tryHarder = false` (plan 10 §14.2). |
| | `ScanSession.kt` | The scanner's rules without CameraX: **latch** (first text `parse` accepts → analysis stops → `releaseCamera()` → only then `onLatched(value)`; queued frames `Ignored`, never decoded; a failing release still reaches the host); **rejects once per sighting** (not again until out of view ≥ 2 s; bounded memory of 16); **start attempts** counted, so frames of a paused/stopped start are ignored; a throwing parser is a rejection, never a crash on the camera thread; **never keeps the value** — only SHA-256 digests of rejected texts, nothing logged, `toString` and `FrameVerdict` carry no text. |
| `access/` | `ScannerStage.kt` | `Starting, Scanning, Denied, PermanentlyDenied, NoCamera, Failed`, each with its `MediaProblem` and actions; **Paste instead is on every stage**; Denied → Allow camera; PermanentlyDenied → Open settings and **no retry** (Android will not ask); Failed → Try again. |
| | `CameraAccessFlow.kt` | Over core's `PermissionGate`: `open()` (no camera → NoCamera with **no prompt**; granted → Starting with no prompt; else ask), `allowCamera()`, `openSettings()`, `recheck()` (ON_RESUME after settings, never prompts). A failing probe is "no camera"; a failing request is Denied — never an invented grant. `CameraFailures.classify` → `Failure.Media(CameraDenied | CameraFailed, Redacted.shape(e))` (class name only, never the message); `stageFor(failure)`. `StartingNotice`: "taking longer than usual" after 8 s (injected `Clock`). |
| | `CameraHardware.kt` | `AndroidCameraHardware`: `FEATURE_CAMERA_ANY`, a throwing probe answers false. Source for `PlatformCapabilities.qrPairing`. |
| `capture/` | `CaptureFiles.kt` | `files/camera-capture-v1/<captureId>.jpg` (C10-22): id = epoch microseconds as text; only 1–20 ASCII digits name a file (no traversal); `hasPhoto` (non-empty), `discard` (idempotent), `sweepStale(keep)` for start-up. `PhotoCapturer` seam. |
| `camerax/` (src/camerax, §5.1) | `ZxingCppQrDecoder.kt` | `zxingcpp.BarcodeReader` with the options above on the `ImageProxy` (its crop rect); native errors → null; exposes `lastReadTimeMs` for P-M3. |
| | `CameraXBinding.kt` | Back camera + `Preview` + caller use cases bound to a lifecycle; `surfaceRequests: StateFlow<SurfaceRequest?>` for the kit's `CameraXViewfinder`; a bind that completes after `unbind()` is dropped (generation count). |
| | `CameraXScanner.kt` | `ImageAnalysis` 1280×720 `FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER`, `STRATEGY_KEEP_ONLY_LATEST`, one analysis thread; per frame: current-attempt check, `setCropRect(ScanWindow…)`, `ScanSession.analyze`. ON_RESUME start, ON_PAUSE stop, ON_DESTROY close. Unbind and the host callback are posted to the main executor in that order (FIFO), so the camera is released before the host pops. |
| | `CameraXPhotoCapturer.kt` | `ImageCapture` (minimise latency) into a `CaptureFiles` target; `Ok(true)` saved, `Ok(false)` nothing written, failure → `Failure.Media`; the file is deleted on error or cancellation. |
| tests | `src/test/...`, `src/test/resources/qr/*.png` | 9 PNG fixtures (pairing payload, two-address payload, inverted, rotated 90°, URL, Wi-Fi, a 1280×720 scene with the pairing code in the window and a URL code outside it, a scene with only an outside code, blank). `ZxingCoreDecoder` is the host stand-in for zxing-cpp with the same option meaning; `tools/GenQrFixtures.java` regenerates the fixtures byte for byte. |

Plain words: the module carries no copy (rule 3). The stages map to strings already in `:ui:words` —
`e7SetupCameraNeeded`, `e7SetupCameraDisabled`/`e7SetupCameraSettingsDetail`, `e7SetupNoCamera`/`e7SetupNoCameraDetail`,
`e7SetupCameraFailed`/`e7SetupCameraFailedDetail`, `pairingScannerAllowCamera`, `pairingScannerStarting`,
`e7SetupScanInstruction` — and to each `MediaProblem`'s sentence (`CameraDenied`, `CameraDeniedForever`, `NoCamera`,
`CameraFailed`).

## 2. Catalog rows covered

| Row | Status | Tests |
|---|---|---|
| B-ANDR-049 | camera side covered: denial explained with Allow camera + Paste; permanent denial → settings, no retry; no camera → says so and never prompts; desktop/`NotApplicable` reaches no camera code; failing probe/request never invents an answer; no code → nothing decoded. The screen and the "scan only where supported" gate are `:feature:servers` / `PlatformCapabilities` | `CameraAccessFlowTest` (12), `AndroidCameraHardwareTest` (2, Robolectric), `QrFixtureDecodeTest` |
| B-PROTO-069 | scanner side covered: a scanned pairing QR yields exactly the pasted text (one and two addresses, inverted, sideways, aimed inside a busy frame); a non-pairing QR (URL, Wi-Fi) is rejected and scanning continues. The parser itself is 04's (B-PROTO-064…066), injected as `parse` | `QrFixtureDecodeTest` (6), `ScanWindowTest` (5), `ScanSessionTest` |
| B-SEC-107 | scanner part: the session holds no decoded text (reflection walk over production objects), prints none | `ScanSessionTest` |
| B-MEDIA-008 | capture-file part: id format, `camera-capture-v1` location, cancel/failure leaves no file, empty write = no photo, stale sweep. Record-before-launch and recovery are `:engine:media` (S3-MEDIAATT) | `CaptureFilesTest` (6) |

Red-first evidence: the tests were written before the main sources; the first run had **1 real failure**
(`B-ANDR-049 a camera probe that fails…` — the flow trusted `CameraHardware` not to throw; fixed in the flow). Mutation
pass (6 mutations, reverted): 5 went red (release/notify order ×3 tests, digest → raw text, quiet window, Denied →
Starting ×3, failed request → Granted); the first-line latch check survived because the latch also clears `running`,
which the stale-start guard catches (same behaviour).

## 3. Commands and results

Environment: Android command-line tools `commandlinetools-linux-16111833_latest.zip` in
`/opt/android-sdk/cmdline-tools/latest`, licences accepted, `sdkmanager "platform-tools" "platforms;android-37.1"
"build-tools;37.0.0"`; `native/local.properties` `sdk.dir=/opt/android-sdk` (gitignored). JDK 21 runs Gradle; JDK 17
toolchain via foojay. Container-only (not committed): `~/.gradle/init.d/central-mirror.gradle.kts` (Google's Maven
Central mirror first, set in `beforeSettings` — setting it in `settingsEvaluated` is too late for the foojay settings
plugin and Central answers 429; also `robolectric.dependency.repo.url` on test tasks); `~/.gradle/gradle.properties`
`-Xmx6g`, 4 workers.

All from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/opt/android-sdk`:

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :android:camera:testDebugUnitTest` | FAILED: `compileDebugKotlin` "Cannot inline bytecode built with JVM target 17 into … 11" (core's `attempt {}`) → module sets Java 17 (§5.3) |
| 2 | same | test compile FAILED: `.`/`;` in two test names → renamed |
| 3 | same | 41 tests, 1 failed (§2 red-first) → fixed |
| 4 | same | **BUILD SUCCESSFUL**, 41/0 |
| 5 | mutation pass (6 × `testDebugUnitTest`, exit codes) | 5 red, 1 equivalent survivor |
| 6 | temporary local edit (catalog: `camerax-core/-camera2/-lifecycle` 1.6.2, `zxing-cpp` 3.1.1; `ExternalRules`: allow `:android:camera`) then `./gradlew :android:camera:compileReleaseKotlin :android:camera:testDebugUnitTest` | **BUILD SUCCESSFUL**, no warnings in the module; edit reverted with `git checkout --` |
| 7 | same temporary edit, `./gradlew :android:camera:dependencies --configuration releaseRuntimeClasspath \| grep -ciE 'mlkit\|com.google.android.gms\|firebase\|play-services'` | **0** (zxing-cpp pulls only `camera-core`) |
| 8 | **Final, committed tree:** `./gradlew --rerun-tasks :android:camera:testDebugUnitTest` | **BUILD SUCCESSFUL**; 41 / 0: ScanSession 10, CameraAccessFlow 12, CaptureFiles 6, QrFixtureDecode 6, ScanWindow 5, AndroidCameraHardware 2 |
| 9 | `java -cp core-3.5.4.jar tools/GenQrFixtures.java /tmp/regen` + `cmp` | all 9 fixtures identical |
| 10 | `git diff 48af726f \| grep` for RFC 1918 / CGNAT addresses and e-mail addresses | no match (fixtures use `127.0.0.1` and `192.0.2.20`, the documentation range) |

Not run: `:quality:test` (needs the whole tree; Konsist K4 checked by hand — no `Dispatchers.*`, `Clock.System`,
`GlobalScope`, `runBlocking`; no logging at all), `build`, any device or emulator, P-M3, E2E-02. No formatter: there
is no `tool/format-changed.sh` in this tree.

## 4. Libraries and versions

Committed build: `:core:domain` (api), `androidx-core-ktx`; tests: `kotlin("test-junit")`, `junit4`, `robolectric`
4.17, `kotlinx-coroutines-test`, and **ZXing core 3.5.4 test-only** through `catalogOr("zxing-core", …)` (the
`:android:linux` pattern: the literal coordinate is used only until the alias exists). Adapters (not in the committed
build until §6): CameraX `camera-core`, `camera-camera2`, `camera-lifecycle` 1.6.2 and `io.github.zxing-cpp:android`
3.1.1 (ships its own `-keep class zxingcpp.** { *; }`; ABIs arm64-v8a, armeabi-v7a, x86, x86_64). `camera-compose`
is not needed here: the kit (`:ui:kit-content`) renders `surfaceRequests` with `CameraXViewfinder`.

## 5. Deviations and decisions

1. **Module placement.** Plans 01/10/11 put CameraX + zxing-cpp in `:android:media` and the scanner slot in
   `:ui:kit-content`; this brief created `:android:camera`. Build-logic `ExternalRules` allows `androidx.camera` and
   `io.github.zxing-cpp` only in those two modules, so the CameraX/zxing-cpp sources live in `src/camerax/kotlin` and
   join the main source set **only when** the catalog has all four aliases. Adding the aliases without the rule change
   fails configuration on purpose (no bypass, no file dependency). Everything without a camera library builds and is
   tested today. If the coordinator prefers plan 10's placement, `scan/`, `access/` and `capture/` move as they are and
   `ScanSession` is what `AndroidScannerSlot` drives.
2. **Fixture decoding on the host uses ZXing core, not zxing-cpp.** zxing-cpp is a native AAR with no host build, so
   the fixtures prove the crop, options, latch and reject rules through the `QrFrameDecoder` seam; zxing-cpp itself is
   only compile-verified. Its numbers (p95 < 30 ms, first lock < 1 s, inverted code) are P-M3 on a device.
3. **JVM 17 set in the module.** `oc.android.library` leaves the Android Kotlin target at 11, so any module calling
   core's inline `attempt {}` fails to compile. The module sets `compileOptions` 17 (build-logic request below).
4. **Capture is CameraX `ImageCapture`, as the brief asks.** Plan 10 §10.4 takes attachment photos with the system
   camera (`ActivityResults.takePhoto` → `TakePicture`), which stays the default. `CameraXPhotoCapturer` is an in-app
   alternative behind the `PhotoCapturer` seam, writing to the same `camera-capture-v1` targets, so the pending-photo
   record and recovery (S3-MEDIAATT) apply unchanged. It is not wired anywhere.
5. **The pairing parser is injected** (`parse: (String) -> T?`), not depended on: it belongs to plan 04
   (`B-PROTO-064…066`) and no compiling module exposes it yet. Tests use a minimal stand-in.
6. **Never keep the value** is enforced structurally (digests only) and by a reflection test rather than by review.

## 6. Requests

Catalog (owner: build-logic):

```toml
[versions]
camerax = "1.6.2"
zxing-cpp = "3.1.1"
zxing-core = "3.5.4"          # tests only
[libraries]
camerax-core = { module = "androidx.camera:camera-core", version.ref = "camerax" }
camerax-camera2 = { module = "androidx.camera:camera-camera2", version.ref = "camerax" }
camerax-lifecycle = { module = "androidx.camera:camera-lifecycle", version.ref = "camerax" }
zxing-cpp = { module = "io.github.zxing-cpp:android", version.ref = "zxing-cpp" }
zxing-core = { module = "com.google.zxing:core", version.ref = "zxing-core" }
```

Build-logic:
- `ExternalRules`: add `|| p == ":android:camera"` to the `androidx.camera` and `io.github.zxing-cpp` rows (or move
  this module's sources into `:android:media`, §5.1); add the planned `"com.google.zxing"` test-configurations-only rule.
- `oc.android.library`: `compileOptions` Java 17 (and Kotlin `jvmTarget` 17), so Android modules can inline core.

Coordinator: `MODULE_OWNERS` row `:android:camera	F	(QR-CAMERA)	slice-3` (`ModuleOwnersTest` will fail without it).

`:androidApp` (C10-10, coordinator): `CAMERA` permission and `<uses-feature android:name="android.hardware.camera"
android:required="false"/>` as plan 10 §15.2; the `camera` FileProvider root (C10-14) if `CameraXPhotoCapturer` or
`TakePicture` writes to `camera-capture-v1`.

## 7. Open problems

- **Adapters not in the build** until §6 lands; then run `./gradlew :android:camera:compileReleaseKotlin` again.
- **No device evidence:** P-M3 (emulator virtual scene and NX721J with a real `opencode2 pair` code in a dark
  terminal), `lastReadTimeMs` p95, first-lock time, and that `STRATEGY_KEEP_ONLY_LATEST` at 720p holds the budget.
- **Wiring:** `:ui:kit-content` `AndroidScannerSlot` (render `surfaceRequests`, pass `windowFraction`, `KitHaptic.Done`
  on latch), `:feature:servers` `PairingScannerScreen` stages from `CameraAccessFlow`, `PlatformCapabilities.qrPairing`
  from `AndroidCameraHardware`, and the S2-PLATFORM `PermissionGate` (three-state camera answer).
- `ScanWindow.fractionFor` assumes the preview fills the view with `FILL_CENTER`; with another scale type the crop
  should be computed from the viewfinder's transform (CameraX `CoordinateTransform`) instead.
