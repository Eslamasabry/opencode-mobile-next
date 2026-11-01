# VOICE — voice typing, model packs, first-mic setup and read aloud

Date: 2026-11-01. Branch: `native/voice-2026-11-01`, created from `claude/native-voice-implementation-nk3ete` at
`2c8b8b00` (the M0 tip after REVIEW-FIX-W1). Write set: `native/engine/voice/**`, `native/android/voice/**` (both new
modules) and this report. Plan sections: 10 §4–§8, §17, §20 (binding with plan 01); map `media-voice.md`; the Flutter
`lib/voice/` code and its tests.

**Finish line:** speech to text, model download and verification, first-mic setup and read aloud work as pure Kotlin
engine logic over platform seams, with Android implementations of those seams, all tested by catalog row.
**Non-goal:** UI (kit pill, composer, Settings › Voice, setup sheet), the voice conversation loop, the download
notification, and the app-graph wiring. Those belong to the kit, feature, background and coordinator owners (§7).

**Shipping state:** implemented; tests green on the desktop JVM and on Android host (Robolectric); committed (9 commits)
and pushed to the branch. **Not enabled:** `:androidApp` does not depend on either module yet, and the sherpa-onnx
adapter is not built (§5, §8). Nothing ran on a device or emulator. No PR, no CI run, no signing, no release.
`master` and tags untouched.

---

## 1. What was built

### `:engine:voice` (KMP, commonMain only, `explicitApi`; depends on `:core:common`, `:core:data`, kotlinx-io)

| Package | What | Plan |
|---|---|---|
| `audio` | `Pcm16Chunker` (30 s chunks, odd-byte carry, RMS level), `SilenceDetector` (1.5 s after ≥ 300 ms of speech at 0.015), `LevelMeter` (9 bars, `(i+1)/12`), and the seams `AudioSession`, `AudioCapture`, `CaptureEvent`, `PcmPlayer`, `PlaybackLease`, `AudioInterruption` | §4.5, §6.1 |
| `dictation` | `VoiceController`; `isSendableTranscript`, `mayAutoSend`, `AutoSendCountdown` (3 s, sends only if the field still holds the captured text, in the foreground, nothing in flight), `stopsOnSilence` (tap + setting only; **off by default**); `DraftMerge`; the seams `SpeechRecognizer`, `RecognizerSession`, `MicPermission`, `SpeechPackSource`, `CaptureFailed` | §6.1–§6.7 |
| `language` | `VoiceLanguage` (36 values, stored as the Dart enum names, `iw`/`nb`/`nn`/`in` folding, `resolve`, `recognizerCode`) and `VoiceLanguageNote` (offline-unknown, Compact-rough) | §6.1 |
| `models` | `ModelCatalog` (base/small/tiny/kokoro-en pinned byte for byte), `PackSupport` (ABI, **total RAM**, storage counting only remaining bytes + `max(128 MiB, size/4)`), `AutomaticPack` (`choose`, `defaultFor`), `PackInstaller`, `ModelPacks`, `PureSha256`/`DigestFactory`, `ModelFiles`/`SystemModelFiles`, `ModelFetcher`, the Flutter-shaped `.installed.json` and the new `.native-verified.json`, `DeviceProbe`, `SetupNetworkSource` | §7.1–§7.4, §7.8 |
| `setup` | `VoiceSetup` / `VoiceSetupRun` / `VoiceSetupStage` / `VoiceSetupHost` (first-mic flow) | §7.7 |
| `readaloud` | `ReadAloud`; `ReadLanguage.detect`, `SentenceSplitter` (280-char cut), `VoicePick`; the seams `SystemTts`, `SpeechSynthesizer`, `SynthesisSession`, `TtsVoice` | §8.1–§8.6 |
| `prefs` | `VoicePrefs` with Flutter's keys (`voice.selected_pack`, `voice.language`, `voice.auto_send`, `voice.read_aloud_natural`, `oc.readAloudConsent`), `KeyValueVoicePrefs` over the one `KeyValueStore`, `InMemoryVoicePrefs` | 05 App. A |
| `state` | `VoiceState`, `PrepStep`, `VoiceMode`, `VoiceEnd`, `VoiceHost`, `VoicePhase`, `PackId`, `PackStatus`, `PackJob`, `ModelPacksState`, `ReadAloudState`, `ReadEngine` | §4.4 |

Key behaviour, as the brief asks:

- **Model kept loaded while talking (M2).** `VoiceController` opens one recognizer session when listening starts and
  reuses it for every 30 s chunk and for the next recording; it closes 20 s after the last recording, on
  `releaseModel()` (pack change/remove), and after each chunk when **total RAM is under 6 GB or unknown**, or when
  `memoryNow` is low (`availMem < threshold + 256 MiB`). Capture starts at once and frames buffer while the model loads.
- **Memory gate on total RAM.** Every pack decision reads `DeviceProbeResult.Known.totalRamMb`
  (`ActivityManager.MemoryInfo.totalMem`); `memoryClassMb` is carried for diagnostics only and a test proves a large
  heap class cannot qualify a small phone. Kokoro's 2000 MB gate is enforced (M5).
- **Hold-to-talk.** `VoiceMode.Hold` never stops on silence and never auto-sends (`stopsOnSilence`), `finish()` ends it
  and `cancel(keepWords = false)` drops it. The 350 ms / 18 dp / 80 dp press rules (`KitMicPress`) and the 400 ms slip
  rule are kit-chat's (06 §7.8), not in this write set.
- **Auto-send off by default with a 3 s countdown.** `ModelPacksState.autoSend` defaults to false (persisted under
  `voice.auto_send`); `AutoSendCountdown` is the pure countdown the pill ticks and the ViewModel asks.
- **Read aloud.** Consent once per phone, no engine touched before it; one reader app-wide; foreground only; playback
  focus through the `AudioSession` lease; interruptions stop it (another recording → `ReadAloudBusy`); Kokoro reads
  English replies sentence by sentence (speaker 3, 24 kHz, first-sound watchdog 90 s, warm session 20 s), other scripts
  fall back to the phone's voice with the note once per language per run.
- **Audio focus.** `AndroidAudioSession` (below) implements A1–A8.
- **Download and verify with pinned SHA-256.** `PackInstaller`: HTTPS only, at most 5 manual redirects each `https`,
  `User-Agent: OpenCode-Mobile/voice-model`, Range resume accepted only on the exact `Content-Range`, `200` restarts,
  a missing `Content-Length` allowed and a present one must equal the remaining bytes, a body past the pin aborts,
  15 s step / 20 s idle timeouts, two SHA-256 passes before publishing, publish with `.voice-backup` rollback that keeps
  the old verified model, atomic marker in Flutter's exact JSON shape, then the head/tail stamp (M4). Cancel while
  downloading keeps the `.part` files (M3); cancel later cleans the staging. One mutex per pack directory.

### `:android:voice` (plain Android library; depends on `:engine:voice`, `androidx.core`, coroutines-android)

| File | What | Plan |
|---|---|---|
| `audio/AndroidAudioSession` | Single owner of `requestAudioFocus`, the recording callback and the device callback: A1 (stop playback, 10 s guard), A2 (refuse playback during the guard or any active recording; exceptions = busy), A3 (`GAIN_TRANSIENT`, `USAGE_MEDIA` + `CONTENT_TYPE_SPEECH`, `setWillPauseWhenDucked(true)`, any non-gain → `FocusLost`), A4 (another app's recording → `OtherRecordingStarted`), A5 (sink removed → `OutputDeviceRemoved`), A6 (`onHostPaused` → `AppLeftForeground`), A7 (`isClientSilenced` for our own session), A8 (`AUDIOFOCUS_GAIN` with delayed gain on capture, abandoned on end, loss → capture interrupted) | §5 |
| `audio/AndroidAudioCapture` | `AudioRecord` MIC 16 kHz mono PCM16, privacy-sensitive on API 30+, 4096-byte frames never dropped, `SecurityException` → `MicDenied`, busy → `MicBusy` | §6.2 |
| `audio/AndroidPcmPlayer` | `AudioTrack` float mono stream, non-blocking writes with 15 ms back-off, head polling, cancel = pause + flush + release | §8.5 |
| `tts/AndroidSystemTts` | Port of `ReadAloudBridge.kt`: lazy engine, 8 s init, `ReadAloudBusy` during init, offline voices only sorted by name, default voice → device locale → language → first, surrogate-safe chunks ≤ `min(maxInput, 3000)`, 300 s watchdog, stop on cancel, `releaseIfIdle` for A1. Manifest `<queries>` for `TTS_SERVICE` | §8.4 |
| `device/AndroidDeviceProbe` | total RAM, `StatFs` free bytes, ABIs, `FEATURE_MICROPHONE`, low-RAM flag, heap class (diagnostics), CPU count; 5 s → `Unknown`; `memoryNow()` | §7.3 |
| `device/AndroidSetupNetwork` | `SetupNetworkRules.classify` (no manager → Unknown, no network/internet → Offline, not validated → Unknown, cellular → Metered always, VPN → Unknown, metered → Metered) and the default-network callback flow | §7.6 |
| `net/HttpsModelFetcher` | `HttpURLConnection`, no redirects followed, no caches/cookies/credentials, `Accept-Encoding: identity`, 15 s connect / 20 s read, failures as kinds with a class-name shape only | §7.4 |
| `files/AndroidModelFiles`, `PlatformDigests` | `java.io` files with seeking reads (the stamp reads ~2 MiB per file) and `ENOSPC` → `StorageFullException` by errno, never by message; `MessageDigest` SHA-256 | §7.2, §7.4 |
| `speech/SherpaConfigs`, `SherpaRuntime`, `KokoroSink`, `SherpaSpeechRecognizer`, `SherpaSpeechSynthesizer` | The exact sherpa config fields as data; a port for the four native calls; one speech lane; a cancelled transcribe stops waiting while the JNI decode finishes and is discarded; Kokoro streams through a **named** `KokoroSink` that returns 0 once the reading is cancelled | §2.4, §6.3, §8.5 |
| `AndroidVoiceComponents` | Builds the whole stack (models in `files/voice_models`, read in place) for the app graph | §3.3 |
| `AndroidManifest.xml` | `RECORD_AUDIO`, `INTERNET`, `ACCESS_NETWORK_STATE`, optional `android.hardware.microphone`, the TTS `<queries>` | §15.2 |

## 2. Catalog rows covered

144 tests, 0 failures. Every test name starts with its row (`B-AREA-NNN …`) or a plan-10 native check (`N1`, `N5`).

| Row | Test classes (module) | Notes |
|---|---|---|
| B-MEDIA-002 | `PackSupportTest`, `ReadTextTest` (engine) | RAM thresholds + explicit choice; voice by full locale then language |
| B-MEDIA-003 | `LevelMeterTest` | the kit RTL draw is kit-chat's `KitLevelMeterTest` |
| B-MEDIA-005 | `ModelCatalogTest`, `ReadTextTest`, `ReadAloudTest`, `ModelPacksTest` (engine); `SherpaSpeechTest` (android) | pins, script routing, splitter, natural voice on only once installed, RAM gate, sample-rate check |
| B-MEDIA-009 | `ReadAloudTest` (engine); `RulesTest` (android) | all five Flutter cases + consent, one reader, offline voices |
| B-MEDIA-010 | `VoiceControllerTest` | 3-minute dictation in order; chunk 4 fails keeping w1–w3; leaving 20 s into chunk 2; "nothing heard"; warm-session rules |
| B-MEDIA-011 | `PackSupportTest`, `VoiceSetupTest` | |
| B-MEDIA-012 | `PackInstallerTest`, `ModelPacksTest` | resume after stop; idempotent install |
| B-MEDIA-013 | `PackInstallerTest`, `ModelPacksTest` (engine); `AndroidPlatformTest`, `HttpsModelFetcherTest` (android) | kinds not text; no space by errno; remove refused while downloading |
| B-MEDIA-014, 015, 016 | `Pcm16ChunkerTest`, `SilenceDetectorTest`, `AutoSendPolicyTest` | the Flutter vectors |
| B-MEDIA-017 | `AutoSendPolicyTest`, `VoiceControllerTest` | engine part; the pill countdown UI test (`AutoSendFlowTest`) is the composer feature's |
| B-MEDIA-018 | `ReadTextTest` | |
| B-MEDIA-019, 020, 021 | `VoiceSetupTest` (16 tests) | incl. N15's network-change pause |
| B-MEDIA-022 | `ModelPacksTest` | round trip through a real `PrefsKeyValueStore` and its backend (a "restart"); the Robolectric adoption of a seeded `FlutterSharedPreferences.xml` is `:core:data`'s |
| B-MEDIA-023 | `VoiceControllerTest` (engine); `SherpaSpeechTest` (android) | |
| B-MEDIA-024 | `DraftMergeTest`, `PackSupportTest` | |
| B-MEDIA-025, 026, 027 | `PackInstallerTest`, `PackInstallerTransportTest`, `Sha256Test` (engine); `HttpsModelFetcherTest`, `AndroidPlatformTest` (android) | incl. N15's length rules; B-MEDIA-025 uses the §20.3 amendment (cancel keeps the `.part`) |
| B-MEDIA-028, 029 | `VoiceLanguageTest`, `VoiceControllerTest` | |
| B-MEDIA-030 | `ModelPacksTest` | |
| B-MEDIA-031 | `ModelCatalogTest` | model pins and totals; the AAR `SHA256SUMS` pin and the notices are not in this write set |
| B-MEDIA-032 | `VoiceControllerTest` (engine); `AndroidPlatformTest` (android) | `Unavailable` off-platform, `MicPermission.Refused`, Android probe reads total RAM |
| B-MEDIA-033 | `ModelCatalogTest` (engine); `SherpaSpeechTest` (android) | |
| B-ANDR-101 | `RulesTest` | the classifier; permission reporting is `:android:platform`'s |
| N1 (logic) | `SherpaSpeechTest` | sink returns 0 after cancel; a real thread proves synthesis stops early. The R8 release smoke is not done (§8) |
| N5 | `AndroidPlatformTest` | A1, A2, A3, A6, A8 on Robolectric; A4/A5/A7 have code but no test (§8) |

Not covered (outside this unit or blocked): B-MEDIA-001, 004, 006, 007, 008, 034 (not voice); B-ANDR-102 (download
notification, `:android:background`); B-LINUX-220 (phone-setup component, needs `:engine:runtime`'s
`SetupAppComponent`); B-L10N-024 (`:ui:words`).

## 3. Commands and results

From `native/`, `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, `ANDROID_HOME=/opt/android-sdk`
(`commandlinetools-linux-16111833_latest.zip` in `cmdline-tools/latest`; licences accepted; `platform-tools`,
`platforms;android-37.1`, `build-tools;37.0.0`). Container: 4 vCPUs, 15 GB RAM; `~/.gradle/gradle.properties` at
`-Xmx6g`, 4 workers. Container-only, not committed: `~/.gradle/init.d/central-mirror.gradle.kts` adds Google's Maven
Central mirror in `beforeSettings` (so it comes first; Central answered 429 through the proxy) and sets
`robolectric.dependency.repo.url` on every `Test` task.

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :engine:runtime:desktopTest` (toolchain smoke, before any edit) | BUILD SUCCESSFUL |
| 2 | `./gradlew :engine:voice:desktopTest` after each slice | red first where the test exposed a bug (a wrong storage expectation in my test; a rollback hook that also blocked the restore; `ModelPacks` resolving the global catalog; `advanceUntilIdle` skipping `backgroundScope` work), then green |
| 3 | `./gradlew :android:voice:compileDebugKotlin -Pkotlin.incremental=false` | first: "Cannot inline bytecode built with JVM target 17 into … 11" → module sets JVM 17 (§6) → BUILD SUCCESSFUL |
| 4 | `./gradlew :android:voice:testDebugUnitTest -Pkotlin.incremental=false` | first: 4 red (Robolectric not sandboxing without `@Config(sdk = [35])`; a Kokoro-stop test that could not interleave on one thread) → fixed → green |
| 5 | **Final:** `./gradlew :engine:voice:desktopTest :android:voice:testDebugUnitTest -Pkotlin.incremental=false` | **BUILD SUCCESSFUL. 144 tests, 0 failures**: engine 124 (desktop JVM), android 20 (Robolectric / JVM) |
| 6 | `bash tool/check_commits.sh 2c8b8b00 HEAD` | `native commits: OK, 9 commit(s)` (after shortening one subject) |
| 7 | `bash tool/check_write_set.sh --globs 'native/engine/voice/** native/android/voice/**' --base 2c8b8b00 --also docs/native/units/VOICE.md` | `write-set: OK` |
| 8 | `git diff 2c8b8b00` scanned for private IPv4 ranges, loopback literals and e-mail addresses | none (only Kotlin `this@label` matches; the commit trailers' noreply address) |

The Kotlin incremental cache failed once ("FqNames can't be derived from DirtyData", as S0-CORE and INTEGRATE-W1 saw);
`-Pkotlin.incremental=false` cleared it. Not run, by the brief: `:quality:test`, `build-logic` tests, the app, other
modules' tests. The Konsist rules were checked by reading them: the engine code has no `Dispatchers.*`,
`Clock.System`, `runCatching` beside suspend code, `java.*` imports or `System.`/`Thread.` in commonMain, no
`RedactorApi` opt-in, no `println`/`runBlocking`/`GlobalScope` (tests use `runBlocking`/`Dispatchers.IO` only where a
real thread or socket is the point).

## 4. API decisions

| # | Decision | Why |
|---|---|---|
| V1 | `VoiceController` depends on a `SpeechPackSource` seam (`ModelPacks.asSpeechPackSource()`), not on `ModelPacks` itself | controller tests need no files; the manager stays the single owner of pack state |
| V2 | `PackInstaller` sits over a `ModelFetcher` (one GET, never follows redirects) and a `ModelFiles` seam, not `:core:net`'s `Downloader` | `Downloader` does not exist yet and C10-7's options are not in `:core:net`; the redirect, Range, length-pin and timeout rules live in the installer, so swapping the transport later keeps them |
| V3 | Pure-Kotlin `PureSha256` behind `DigestFactory`; Android binds `MessageDigest` | the core `Sha256` primitive (C10-2) is still missing (INTEGRATE-W1 §7); marked `TODO(core-bytes)` |
| V4 | `StorageFullException` and `FetchFailure(problem)` are the only exceptions the installer maps; `ENOSPC` is detected by errno on Android | plan 10 §17 S11: no copy chosen from exception text |
| V5 | `ModelPacks.download` runs in the app scope and the caller may stop waiting; `cancelDownload()` is the only way to stop it | "a download keeps running when the page or sheet closes" (§7.8) |
| V6 | `select`, `remove`, `discardPartial` and a second download are `Failed(DownloadBusy)` while a download runs; `close()` drops stale work through a generation counter | Dart returns early; native callers get a value (B-MEDIA-013, 030) |
| V7 | `VoiceSetupStage.NeedsConsent.attemptId`; `consent(attemptId, allowMetered)` ignores an older attempt | "a cancelled consent cannot authorize" (B-MEDIA-019) without a stored flag |
| V8 | `ReadAloud.read` takes prose text (`ReadRequest.text`), not markdown | `MarkdownProse.forSpeech` belongs to `:core:markdown` (chat lead), which does not exist yet |
| V9 | A stopped reading returns `Ok` to its caller; only the caller's own cancellation is rethrown | the VM treats Stop / leaving / a newer reading as an end, not a failure; the composer note shows only real failures |
| V10 | `cancel(keepWords = true)` with no words ends at `Idle`, not `Done("")` | Flutter "startup cancellation returns to idle" (B-MEDIA-023); `Done("")` stays for `finish()` ("Nothing was heard") |
| V11 | `VoiceLanguageNote` is a sealed type, not text | no copy in engines (AGENTS rule 3); `:ui:words` words it |
| V12 | The sherpa calls are a `SherpaRuntime` port with config data classes | the AAR is not vendored and ExternalRules allow `com.k2fsa.sherpa.onnx` only in `:android:media` (§5) |
| V13 | No Metro annotations; `AndroidVoiceComponents` builds the stack | `:androidApp` does not depend on the modules yet; the coordinator chooses the bindings when wiring (§7) |

## 5. Deviations from the plan and the brief

- **Module homes.** Plan 10 M1 and plan 15 put this code in `:engine:media` (packages `voice`, `models`, `setup`,
  `readaloud`) and `:android:media` (`audio`, `speech`, `tts`, `device`). The brief's write set is
  `native/engine/voice/**` and `native/android/voice/**`, so the same packages live there. Consequences:
  `:engine:voice` is not in `ModuleRules.engineOrder`, so it may use `:core:*` only and **no engine can depend on it**
  (features can); `:android:voice` may not declare the sherpa AAR.
- **No sherpa-onnx adapter.** The ~60-line `com.k2fsa.sherpa.onnx` adapter implementing `SherpaRuntime` is not
  written: the AAR is not vendored (no `tools/vendor/fetch.sh`, no `third_party/maven`), it is not in the version
  catalog, and ExternalRules refuse it here. Everything above the port is built and tested.
- **The keep rule's class name moves.** `KokoroSink` is `io.github.eslamasabry.oc.android.voice.speech.KokoroSink`
  (plan 10 §2.4 names the `android.media` package).
- **No microphone permission implementation.** `MicPermission` is a seam; the three-state `PermissionGate` with
  `alsoAsk = Notifications` is `:android:platform`'s (C10-15).
- **JVM target 17 in `:android:voice`'s build file.** `oc.android.library` leaves Kotlin at JVM 11, which cannot inline
  `:core:common`'s `attempt {}`; the module sets 17 itself (a build-logic request, §6).
- **Robolectric runs at `@Config(sdk = [35])`**, as `:android:platform` and `:core:data` do (4.17 has no 37 image).
- **Read aloud language fallback.** The fallback note is a field on `ReadAloudState.Speaking`, once per language per
  `ReadAloud` instance (= per app run in the App scope).
- **Formatting.** `tool/format-changed.sh` (AGENTS rule 13) is not in this tree and no formatter is wired; files follow
  the existing style by hand.

## 6. Catalog requests

No new library was added; the modules use existing aliases only (`kotlinx-io-core`, `kotlinx-coroutines-android`,
`androidx-core-ktx`, `junit4`, `robolectric`, `kotlinx-coroutines-test`).

- **sherpa-onnx (vendored):** `sherpa-onnx = "2.0.1"` and a library alias for
  `com.k2fsa.sherpa.onnx:sherpa-onnx-android:2.0.1` from `native/third_party/maven` (plan 10 §2.1–§2.2, hash
  `a8d09d1d…0ee7`), plus `tools/vendor/fetch.sh`. With it, **ExternalRules** must allow `:android:voice` (or the adapter
  moves to `:android:media`).
- **build-logic:** `oc.android.library` should set Kotlin `jvmTarget` 17 and Java 17 compile options (every Android
  library that inlines `:core:common` hits this); then `:android:voice` drops its local override.

## 7. Requests to other owners

- **Coordinator:** add `MODULE_OWNERS` rows for `:engine:voice` (S, media lead) and `:android:voice` (S, media lead) —
  `ModuleOwnersTest` in `:quality` fails until then; decide whether `voice` joins `ModuleRules.engineOrder` (e.g. right
  before `media`, so `:engine:composer` and the conversation engine can use it) or the code moves into
  `:engine:media`/`:android:media` as plan 10 M1 says; wire `AndroidVoiceComponents` into the app graph (App scope)
  and bind `UnsupportedDeviceProbe`, `MicPermission.Refused`, `voiceAvailable = false` on desktop; add to
  `sherpa.keep`:
  `-keepclassmembers class io.github.eslamasabry.oc.android.voice.speech.KokoroSink { java.lang.Integer invoke(float[]); }`
  and `-keep class com.k2fsa.sherpa.onnx.** { *; }`; feed `AndroidAudioSession.onHostPaused()` from `MainActivity`
  `ON_PAUSE` (A6).
- **Media lead / whoever owns the AAR:** write the `SherpaRuntime` adapter (`OfflineRecognizer(null, config)` →
  `createStream/acceptWaveform/decode/getResult/release`; `OfflineTts(null, config).generateWithCallback(text, sid,
  speed, KokoroSink)`), copying `WhisperRecognizerConfig`/`KokoroTtsConfig` field by field; then the N1 R8 release smoke.
- **Core owner:** the `Sha256` primitive (C10-2); `PureSha256` and `DigestFactory.Pure` then go.
- **`:core:net`:** `Downloader` with C10-7's options; `HttpsModelFetcher` can then be replaced without touching
  `PackInstaller`'s rules.
- **`:android:platform`:** `PermissionGate.request(Microphone, alsoAsk = setOf(Notifications))` (C10-15) and its
  `MicPermission` adapter; retry once on `ON_START` after "Allow microphone in Android settings".
- **`:android:background`:** the model-download notification (C10-12, B-ANDR-102) reading `ModelPacks.state.job`.
- **`:ui:words`:** one sentence per `MediaProblem` used here, the `VoiceLanguageNote` lines, sizes in decimal MB.
- **`:ui:kit-chat`:** `KitComposerVoice`, `KitLevelMeter` reading `VoiceController.level` in the draw phase with
  `LevelMeter.isLit`, `KitMicPress` (350 ms / 18 dp / 80 dp) and the 400 ms slip rule.
- **`:feature:chat-composer`:** voice mode wiring (`VoiceController`, `AutoSendCountdown`, `DraftMerge`,
  `VoiceSetupRun` as the first-mic sheet with `handOff()`), the `hold-mic` tip, `AutoSendFlowTest` (B-MEDIA-017 UI).
- **`:feature:settings`:** Settings › Voice typing and the model sheet over `ModelPacks` (rows from `PackStatus`,
  partial resume/discard, natural voice choice via `setNaturalVoice`).
- **Chat lead:** `MarkdownProse.forSpeech` in `:core:markdown` to feed `ReadRequest.text`; the voice conversation
  (`VoiceConversation`, `ReplyWatch`) over `ReadAloud` and `VoiceController`.
- **`:engine:runtime`:** `SetupAppComponent` so the phone-setup voice component (B-LINUX-220) can be built on
  `ModelPacks`.

## 8. Open problems

1. Nothing ran on an emulator or a phone: no real capture, no real decode, no Kokoro audio, no audio-focus check
   against another app, no Android 17 `throw` hardening check (N4). P-M1/P-M2/P-M4 are still open.
2. The sherpa adapter and the AAR are missing (§5), so speech to text and the natural voice cannot run in an APK yet.
3. A4, A5 and A7 (`onRecordingConfigs`, `onDevicesRemoved`, `isClientSilenced`) have code but no Robolectric test:
   `AudioRecordingConfiguration` and `AudioDeviceInfo` could not be built in the 4.17 shadows without a mocking library.
4. `AndroidSystemTts`, `AndroidAudioCapture` and `AndroidPcmPlayer` platform glue is compile-checked only; their pure
   rules (offline filter, default voice, chunks) are tested.
5. `PureSha256` on a 375 MB pack in commonMain is slow; Android binds `MessageDigest`, the desktop has no voice.
6. `VoiceController` publishes `Listening.since` but no 100 ms ticker; the pill computes elapsed time from `since`.
7. B-MEDIA-022's Robolectric adoption test, B-MEDIA-031's AAR/notice parts, B-ANDR-102, B-LINUX-220 and B-L10N-024 are
   left to their owners (§2, §7).
8. `:quality:test` will report `ModuleOwnersTest` red until the coordinator adds the two `MODULE_OWNERS` rows.
