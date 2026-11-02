# P12-VOICE — voice typing in the composer, read aloud on replies, Settings › Voice typing

Date: 2026-11-02/03. Base: `native/m1-2026-11-03` at `8a4eee69`. Pushed to `native/p12-voice-2026-11-03` and to the
session branch `claude/voice-composer-settings-9zhp01` (same commits). No PR, no CI, no signing, no release.

**Finish line:** on the phone, the composer has a mic (tap to talk until Done, hold to talk, slide away to cancel) with
the words shown live and merged into the box, an optional 3-second auto-send countdown with Send now / Don't send, a
"Read aloud" action on replies, and a routed Settings › Voice typing page (speech model download with progress and
SHA-256 verification, language, Send when I stop talking, read-aloud voice).
**Non-goal:** the voice conversation loop (speak replies automatically), the first-mic setup sheet, the download
notification, the hold-mic teaching tip (see §6).

## Shipping state — read this first

| State | |
|---|---|
| Implemented | yes: everything in §1 |
| Enabled in the app | yes: `:engine:voice`, `:android:voice`, `:feature:voice` are in `androidApp/build.gradle.kts`; the slots are bound through Metro |
| Verified by tests | the pure layer only (36 tests, §3), run in a scratch JVM build |
| **Compiled as an Android/Compose build** | **NO.** This cloud session's network policy refuses `dl.google.com` (Android SDK packages, and Google Maven, which `maven.google.com` redirects to). Gradle cannot resolve AGP or androidx, so neither `:androidApp:assemblePreviewDebug` nor any module of this repo could build here. The Compose screens, the kit parts and `VoiceWiring.kt` were reviewed by hand (plus an independent read-through) against the repo's own kit signatures, but **the integrator must compile them first** (§5). |
| Ran on a device | no |

## 1. Journeys now in the app

| Journey | How it works | Where |
|---|---|---|
| Tap the mic, talk, tap Done | `KitMicButton` beside Send → `ComposerVoicePresenter.tapMic()` → `Dictation.start(Tap)`; the pill above the field shows "Listening…", 0:07, the level meter and the live words; Done writes the words into the box around the cursor/selection (`DraftMerge`) | chat composer |
| Hold to talk, let go | hold 350 ms → hold starts (LongPress haptic); the pill says "Let go when you're done"; letting go writes the words down; a touch shorter than 400 ms is a slip and is dropped | chat composer |
| Slide to cancel | while holding, sliding 80 dp away arms cancel ("Let go to cancel", amber pill); letting go drops the words and leaves the box as it was | chat composer |
| Cancel | the pill's Cancel drops the words (also while the mic is still starting) | chat composer |
| Auto-send countdown (off by default) | with Settings › Send when I stop talking on, a tap recording stops after a pause; sendable words (B-MEDIA-016: not "/…" or "!…", at least two letters) wait 3 s in an amber pill "Sending in 3…" with **Send now** and **Don't send**; typing in the box or leaving the app cancels; at zero they go through the composer's one send path; unsendable words stay with the note "Stopped after a pause. Your words are in the box." | chat composer |
| Microphone permission | first use asks through the activity; denied → "Allow microphone" asks again; denied for good → "Allow microphone in Android settings" opens the app's settings page | chat composer note |
| Read a reply aloud | message menu (long-press / footer of a reply) → **Read aloud**; the first time asks once per phone ("Read replies aloud?" with the heard-by-others and engine lines), then reads the reply's prose (code fences, tables, link targets skipped); a strip "Reading the reply aloud" with **Stop reading**; leaving the chat or the app stops it; failures are one plain line | chat screen |
| Settings › Voice typing | the three speech models (Balanced / High accuracy / Compact) with sizes and notes; a tap only chooses; the primary button downloads; progress bar with "x MB of y MB" and Cancel download; then "Verifying downloaded model"; installed → Download again / Delete (with confirm); transcription language sheet (Same as the app, Detect from speech, 34 languages by their own names); Send when I stop talking (off by default); read-aloud voice (phone's voice / natural voice) | route `VoiceRoute.Settings` |

**How to reach Settings › Voice typing today:** from any voice note in the composer ("Voice settings" action on a
failure or "A local model is required"). The coordinator's Settings unit should map its existing Voice row
(`SettingsOutside.VoiceSetup` in `:feature:settings`) to `nav.open(VoiceRoute.Settings)` — one line where it handles
`SettingsOutside`. `:feature:settings` is not in the app yet, so I did not add that line.

### What types the words (honest deviation)

Flutter types with sherpa-onnx Whisper on the phone. That native engine cannot run in this tree: the sherpa AAR is not
vendored and ExternalRules allow it only in `:android:media` (VOICE.md §5), and its download hosts are blocked here too.
So that the mic is **real** now, `VoiceWiring.kt` types with **the phone's own speech recognizer**
(`android.speech.SpeechRecognizer`, on-device on Android 12+ when the phone offers it, `EXTRA_PREFER_OFFLINE`, falling
back to the default service if the on-device one lacks the language). It gives live partial words, the level meter,
and keeps listening across pauses (restarted per phrase) unless auto-send is on. Settings says this plainly at the top
("Voice typing uses your phone's speech recognition… may go to its speech service… models below are for the offline
engine, which this version does not include yet"), instead of "Audio never leaves this phone".

The speech-model rows still download and verify for real (`ModelPacks`/`PackInstaller`, Flutter's folder and marker),
so a later build with the engine finds them. The natural voice is shown but **disabled with its reason** ("Needs the
offline speech engine…"), and a stored natural-voice choice reads as the phone's voice until the engine exists.

**To switch to the offline engine (one file):** in `VoiceWiring.kt`, pass the real `SherpaRuntime` instead of
`NoOfflineSpeechEngine`, give `VoiceParts` `components.voice.asDictation()` with `SpeechEngineKind.OfflineModel`, set
`naturalVoiceAvailable = true` and `OfflineEngineAwarePrefs(…, naturalVoiceRuns = true)`. The composer then also gets
`VoiceNote.NeedsModel` ("A local model is required" + Voice settings) when no model is installed. Nothing in
`:feature:voice` changes.

## 2. Catalog rows covered

| Row | Test (file) | Notes |
|---|---|---|
| B-MEDIA-017 | `ComposerVoicePresenterTest`: off by default / a pause counts down 3 s then sends / Send now and Don't send / typing cancels / leaving the app ends the countdown; `VoiceSettingsPresenterTest` (off by default) | the pill countdown UI is `VoicePanel` (not run: no Compose build) |
| B-MEDIA-016 | `ComposerVoicePresenterTest`: a spoken slash command is never auto-sent | |
| B-MEDIA-024 | `ComposerVoicePresenterTest`: words merge around the selection | |
| B-MEDIA-023 | `ComposerVoicePresenterTest`: slide-to-cancel drops the words; a slip under 400 ms; let go writes; let go while starting; a stale result never becomes a draft; an error keeps the words written | |
| B-MEDIA-010 | `ComposerVoicePresenterTest`: live words while listening, written on Done | |
| B-MEDIA-032 | `ComposerVoicePresenterTest`: refused mic asks again / opens Android settings; no mic where voice cannot start | |
| B-MEDIA-012, 020 | `VoiceSettingsPresenterTest`: three models, a tap only chooses; progress → verifying → installed | |
| B-MEDIA-013 | `VoiceSettingsPresenterTest`: failures as kinds, busy shows nothing; delete asks first | |
| B-MEDIA-022 | `VoiceSettingsPresenterTest`: language and auto-send go to the persisting manager | persistence itself is `:engine:voice`'s (VOICE.md) |
| B-MEDIA-005 | `VoiceSettingsPresenterTest`: natural voice not offered while the engine is missing | |
| B-MEDIA-009 | `ReplyReaderPresenterTest`: consent once, nothing read before it; Not now; failure note; `ChatPresenterTest`: Read aloud hands the reply over once | `ChatPresenterTest` not run (§3) |
| B-CHAT-060 | `ReplyReaderPresenterTest`: speech prose drops fences, tables and link targets; code-only reply has no prose | interim `SpeechProse` until `:core:markdown` has `forSpeech` |
| (plan 06 §7.8 mic press) | `KitMicPressTest` (kit-chat): tap / hold / 80 dp arm-disarm / slop / system cancel | |
| B-MEDIA-003 | `KitLevelMeterRulesTest` (kit-chat): bar i lit at (i + 1) / 12 | the RTL draw is in `KitLevelMeter` (not run) |

Still missing: B-MEDIA-019/020/021 UI (first-mic setup sheet), B-MEDIA-028/029 language note lines in Settings (the note types exist in
the engine; the page shows the follow-app/detect detail only), B-ANDR-102 (download notification), Compose screen tests
by visible text (no Compose build possible here).

## 3. Commands and results

- Android SDK install: `commandlinetools-linux-16111833_latest.zip` from `dl.google.com` → **403 from the egress proxy**
  (policy denial). `maven.google.com` redirects to `dl.google.com`; third-party Google mirrors are also refused.
  Maven Central answered 429 until a container-only init script put Google's Central mirror first
  (`~/.gradle/init.d/central-mirror.gradle.kts`, not committed).
- `./gradlew :engine:voice:desktopTest` → fails at configuration: AGP `com.android.application:9.3.3` cannot be
  resolved (Google Maven blocked). So no module of this repo builds here; `assemblePreviewDebug` was not possible.
- Scratch JVM build (scratchpad, not committed): Kotlin 2.4.21 KMP `jvm("desktop")` over the real sources of
  `:core:common` (common + desktop), `:core:model`, `:core:security`, `:core:data`, `:engine:voice`,
  `:feature:voice`'s `presentation` package and kit-chat's `KitMicPress.kt` and `KitLevelMeterRules.kt`, with the module's real tests:
  **36 tests, 0 failures** (`ComposerVoicePresenterTest` 18, `VoiceSettingsPresenterTest` 7,
  `ReplyReaderPresenterTest` 5, `KitMicPressTest` 5, `KitLevelMeterRulesTest` 1). Two were red first (prose line breaks; a busy refusal showing a
  problem) and fixed.
- Not run: `:feature:chat` tests (edited `ChatRulesTest` reply lists and added one `ChatPresenterTest`), `:quality:test`,
  any Compose UI test, the app build.
- `tool/check_commits.sh` → OK for each commit. Diff scanned for IP addresses and e-mail addresses: none (only Kotlin
  `this@label`).

## 4. Files (write set)

- New module `native/feature/voice/**`: `presentation/` (pure: ports, three presenters, `SpeechProse`), `screen/`
  (three ViewModels), `ui/` (composer slot, reply reader slot, settings screen, words), `VoiceEntries.kt`, tests.
- New kit-chat files: `composer/KitComposerVoiceSlot.kt` (the `KitComposerVoice` / `KitReplyReader` slot contracts,
  `@Multibinds(allowEmpty = true)`), `KitMicPress.kt`, `KitLevelMeterRules.kt`, `KitMicButton.kt` (gesture, ripple, LongPress haptic,
  screen-reader tap), `KitVoicePill.kt` (pill + `KitLevelMeter` drawn in the draw phase, RTL-aware, polite live region,
  no animation under reduced motion), `KitMicPressTest.kt`.
- New route file `ui/routes/VoiceRoute.kt`; new words files `strings_voice_native.xml` (English and Arabic).
- App wiring: `graph/VoiceWiring.kt` (new), three module lines in `androidApp/build.gradle.kts`, and a
  `<queries>` entry for `android.speech.RecognitionService` in `androidApp/src/main/AndroidManifest.xml` (Android 11+
  hides the recognizer without it; RECORD_AUDIO comes from `:android:voice`'s manifest).
- Smallest reach edits in `:feature:chat` (single-owner library — the chat lead should look): `BasicComposerHost`
  injects `Set<KitComposerVoice>` and draws `Panel` above the field row and `Mic` before Stop/Send; `MessageAction`
  gains `ReadAloud` (replies with text), `ChatUi.readAloud` + `readAloudDone` (state + ack, like `clip`),
  `ChatEntries` injects `Set<KitReplyReader>`, `ChatScreen` draws the reader above the request slot and hides the
  menu item when no reader is bound; `ChatRulesTest` reply lists updated, one `ChatPresenterTest` added.

## 5. For the integrator

1. **Compile first** on a machine with the SDK: `./gradlew :feature:voice:compileKotlinDesktop :ui:kit-chat:compileKotlinDesktop :feature:chat:desktopTest :feature:voice:desktopTest :androidApp:assemblePreviewDebug`, then `:quality:test`. Expect small fixes in the Compose/Android files only.
2. `MODULE_OWNERS`: add `:feature:voice	F	(slice brief names the agent)	slice-12` (the file says only the coordinator edits it); `ModuleOwnersTest` is red until then.
3. Settings: map `SettingsOutside.VoiceSetup` → `VoiceRoute.Settings` when `:feature:settings` is routed (its
   `VoiceSettings` port can then read `ModelPacksState` the same way `VoiceSettingsPresenter` does).
4. `ChatEntries`' merge note still applies; my edits there are additive.
5. Offline engine: §1 "To switch to the offline engine".

## 6. Deviations and gaps

- Typing uses the phone's recognizer, not on-device Whisper (§1); stated in the UI.
- No hold-mic teaching tip yet (owner rule "teach what is hidden"): the hold gesture's hint is the mic's spoken hint
  (`tipHoldMic`) and holding is never the only way (tap + Done does the same). The tip belongs with the native teaching
  system when it lands.
- No first-mic setup sheet: with the phone recognizer no model is needed to start; with the offline engine the composer
  points to Voice settings instead of the Flutter sheet.
- No voice conversation (auto-speak replies), no download notification.
- Read aloud reads prose from the transcript text with an interim markdown stripper (`SpeechProse`).
- `ComposerVoicePresenter` and the countdown tick through `VoiceHub.ticks()` because Konsist K1 allows features only
  `kotlinx.coroutines.flow.*`, `launch` and `CoroutineScope` (no `delay`/`Job`).
