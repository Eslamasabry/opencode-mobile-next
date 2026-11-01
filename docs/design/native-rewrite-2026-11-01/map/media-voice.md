# Map: media, voice, camera, files and sharing (area `media-voice`)

Date of reading: 2026-11-01. Source: `/home/eslam/Storage/Code/wt/oc_app-batch1` (branch `int/batch1-2026-11-01`, read-only). Owner memory read from `/home/eslam/.claude/projects/-home-eslam-Storage-Code-oc-app/memory`. Test catalog (area `media-voice`, 35 rows, plus 217 more rows from other areas that cite media tests) at `/home/eslam/Storage/Code/wt/native-plan/catalog`.

Purpose: a rewrite agent that reads only this file must not miss a user-visible feature, an internal capability, a stored key, a size/time limit, an owner rule, or a trap that was already paid for. Feature ids are `MEDIA-VOICE-NN` (50 of them). Sections: 0 scale, 1 owner rules, 2 feature catalog (voice input 01-18, read aloud 19-24, camera/photos/attachments 25-36, viewing/sharing/QR 37-50), 3 platform gates, 4 native bridges, 5 stored data, 6 packages and native replacements, 7 hard-won behaviours, 8 performance, 9 privacy and security invariants, 10 migration, 11 tests, 12 risks and open questions, 13 rewrite guidance (seams, slices), Appendix A file index, Appendix B copy families.

Evidence tags: **[code]** read in source; **[doc]** stated in a repo doc; **[owner]** a dated owner decision (memory or repo doc); **[commit]** from `git log` bodies; **[web]** checked on the web 2026-11-01; **[unread]** not opened in full (only class docs/greps), so details may be missing.

Precedence: where docs and code disagree, code wins. Known drifts in this area are marked "DRIFT".

---

## 0. Scale and shape

| Measure | Value | Evidence |
|---|---|---|
| `lib/voice/` | 19 files, 5,598 lines (controller 524, model_download 795, voice_ui 787, natural_voice 434, model_manager 405, automatic_setup 381, read_aloud 352, recognizer 219, notices 215, natural_voice_ui 210, natural_synthesis 191, audio 171, presentation 170, device 152, model_manifest 232, automatic_setup_platform 141, voice_settings_parts 114, silence 75, automatic_pack 30) | [code] |
| Other Dart in scope (platform, state, domain, chat part files, widgets, kit viewer/image/qr/scanner/level-meter/mic-hold, files viewer, setup voice component) | about 13,150 lines in the 50 files measured, spread over about 70 files (Appendix A) | [code] |
| Kotlin in scope | `ReadAloudBridge` 477, `MainActivityMedia` 162, `VoiceDownloadNotifications` 170, `LocalPdfBridge` 198, `LocalPdfService` ~90, `LaunchShareChannel` ~95 = about 1,200 lines (total Kotlin in the app: 38,441) | [code] |
| Native bridges | `oc/voice`, `oc/camera`, `oc/read-aloud`, `oc/local_pdf` (+ isolated `LocalPdfService`), `oc/share` (shared with launch/intent area) | [code] |
| Pure-Dart plugins doing the real native work | `record` 7.1.1 (mic), `sherpa_onnx` 2.0.1 (Whisper STT + Kokoro TTS), `image_picker` 1.2.3, `file_picker` 12.2.0, `mobile_scanner` 7.4.2 (CameraX + bundled ML Kit), `desktop_drop` 0.8.4, `flutter_svg` 2.3.0, `xml` 7.0.1, `qr` 3.0.2, `path_provider` 2.1.6, `shared_preferences`, `crypto` 3.0.7 | [code] pubspec |
| Tests | 1,022 files in `test/`; about 40 top-level files plus the `test/kit/*` files listed in Section 11 cover this area (Section 11). Catalog rows with area `media-voice`: 35 (logic level); the 7,967 widget tests come later | [code] [owner] |
| L10n | `voice*` 58 keys, `e7VoiceUi*` 71, `vptt*` 11, `readAloud*` 21, `naturalVoice*` 8, `photo*` 13, `fileShare*` 13, `kitVoice*` 17, `kitMarkup*` 17, `kitGallery*` 8, `kitViewer*` 19, `readerUi*` 81, `file*` 59 (Appendix B). 7 locales (en, ar, es, ja, pt, ru, zh); only Arabic is RTL | [code] |
| Android native strings | `native_voice_setup/downloading/preparing/ready/failed` in `res/values` and `res/values-ar` | [code] |

The area is three user stories: (1) **talk to the agent** (dictate, hold to talk, auto-send, a hands-free voice conversation, hear replies read aloud, all offline); (2) **give the agent things** (photos, camera, files, office documents, pasted images, dropped files, shared text), with hard size caps and crash-proof recovery; (3) **look at and hand over things** (one viewer for text/code/markdown/image/PDF/SVG/CSV, an image gallery with markup, Save, Wi-Fi share by QR, scanning a pairing QR with the camera).

### Most likely to be lost in a rewrite (read these first)

1. Chunked dictation with no recording cap, ordered 30 s chunks, words kept when a chunk fails (01, 05).
2. Hold-to-talk thresholds and the "never auto-send a hold / a command / a near-empty sound" rules (02, 03).
3. Total-RAM model gating, pinned revisions and SHA-256, resumable atomic installs with rollback (08, 09).
4. First-mic setup: per-attempt consent, metered/VPN/unknown network handling, never opening the mic from the background (11).
5. Read aloud: consent, offline voices only, foreground only, capture/playback exclusion with a 10 s guard, scope changes stop it (19-24).
6. Photos: 2048 px / q85 scaling, 5 files / 10 MB / 20 MB caps, pending-photo record written before the picker opens and recovered into the recorded conversation (26, 27).
7. The MIME gate that makes text attach as exact `text/plain`, and Office files attached as text (28, 29).
8. Isolated-process PDF rendering with its limits; SVG allowlist; CSV bounds (40, 41).
9. Wi-Fi share security shape (45) and the QR scanner that never keeps the value and has no retry when permission is permanent (46).
10. Existing on-disk data the update must keep: `voice_models/`, `draft-attachments-v1/`, `prompt-photo-recovery-v1/`, the `flutter.`-prefixed preference keys (Sections 5 and 10).

---

## 1. Owner rules that bind this area

All [owner] unless a doc is cited. The rewrite enforces them in every decision here.

| Rule | How it shows up in this area |
|---|---|
| Android-native feel to the maximum (2026-10-26): ripples, predictive back, stretch overscroll, haptics | Mic hold gives haptic feedback (`kit_mic_hold.dart`: commit/select/send); viewers are sheets/pages with predictive back; picker UIs are the system's (Photo Picker, document picker, camera app) |
| Smooth streaming; subtle 150-250 ms motion; reduced-motion support (2026-10-14) | Level meter, voice phases, tray open/close all animate with framework APIs; reduced motion removes them |
| Kit only (2026-09-26) | Every voice sheet, viewer, scanner frame, QR card is a kit part (`KitSheet`, `KitViewer`, `KitScanner`, `KitQr`, `KitLevelMeter`, `KitComposerVoice`). Feature code only arranges them |
| No raw errors (memory `no-raw-errors-rule`) | Voice, photo, PDF, share errors are typed and worded (`voiceErrorText`, `_photoError`, `localPdfMessage`, `FileShareFailure`). Exception text appears only under a "Technical details" fold (`KitDetailsFold`) |
| Plain words, sizes in plain MB | `voiceSizeText` gives "160 MB" / "1.2 GB" (decimal). Exact MiB, file names and the memory rule live only under Details (P10.4) |
| Nothing is red (2026-10-16) | Failures use the neutral failure tone; the markup editor's red pen ink is the one approved exception |
| Teach what is hidden (2026-10-21) | Hold-to-talk tip `TeachingIds.holdMic = 'hold-mic'` (anchor key `composer-voice-button`; shown after the mic was used by tapping and the box is empty again; done once the person holds the mic) |
| Obvious options first; no new bar/row level | Photos, Camera, File are first-page buttons in the "+" tray; Voice input and Voice conversation are rows in the "+" sheet; "Share on Wi-Fi" is a visible second action beside "Ask about this file" |
| Actions name their target | "Download Balanced (161 MB)", "Use Balanced", "Delete Balanced (160 MB)", "Re-download Balanced" |
| Steer, never queue | A voice conversation turn goes through the one send path (`_send`), so steering/queue rules of the connection apply unchanged; a conversation cannot send while the agent works (`canSendWhileBusy: !_voiceConversation`) |
| Text scale: frame text capped at 1.3x, reading text scales fully (2026-10-31) | Voice pill words and tray labels are frame text; viewer/reader body is reading text |
| Component hard max width | `KitLevelMeter` uses `KitMaxWidth.reading`; `KitQr` uses `KitMaxWidth.control` and `KitTokens.qrMaxSize`; viewer prose uses `KitLayout.readingWidth` |
| Owner: voice engine stays bundled; replace the ML Kit QR decoder with a FOSS one (2026-10-31, memory `roadmap-issues-2026-10-31`) | Rewrite uses a FOSS QR decoder (ZXing core or zxing-cpp), not ML Kit; sherpa-onnx + ONNX Runtime remain in the APK |
| Owner: prefers simple, "don't add new locks/stores" (2026-10-31) | Keep the media stores as they are; do not invent new ones |
| Test on emulator, not phone; no owner-phone screenshots | Camera/mic flows need an emulator proof recipe (virtual scene camera, host mic) before an APK reaches the owner |
| No users yet / plan as if real users exist | Day-1 in-place update must keep voice models (hundreds of MB) and pending photos |

---

## 2. Feature catalog

Every feature lists: what the person sees, how it works, key files, stored data, native bridge, traps. "Seam" means the interface a Kotlin Multiplatform rewrite should keep common and implement per platform.

### 2.1 Voice input (speech to text), features 01-18

#### MEDIA-VOICE-01  Mic in the composer: dictation into the draft

- **What the person sees.** In an empty composer the trailing control is a mic (accent circle, tooltip "Talk instead of typing", key `composer-voice-button`). While a reply runs the mic stays beside Stop (owner rule: the mic must stay usable while a reply runs). With text in the box, "Voice input" is a row in the "+" sheet. Tapping it turns the composer pill into **voice mode**: Leave (x) at the start, a level meter, "Listening…", an elapsed clock with **no cap**, and "Done" in the send slot. The words appear **in the draft as each 30-second chunk is written down**, inserted at the caret/selection (`mergeVoiceDraft`: trims the draft, adds a space before/after only when the neighbour is not whitespace, keeps the rest). "Done" writes the rest and returns to the field with the full draft and focus; **dictation never sends**. Leave (x, or Esc) stops and keeps the words already written. Nothing heard shows "nothing heard" note (`voiceModeNothingHeard`).
- **Voice mode phases** (`KitVoicePhase`): starting ("Getting the microphone ready…"), listening, transcribing ("Writing down what you said…"), waitingReply, speakingReply, replyReady, paused ("Paused · the agent needs you"), micDenied, failed, sendingSoon ("Sending in 3…" with Cancel/Send now). Dictation uses starting/listening/transcribing/micDenied/failed/sendingSoon; the rest belong to conversation (feature 04).
- **Lifecycle rules** (hard-won): leaving the app (`paused`/`hidden`, **not** `inactive`) stops the microphone at once and the recorded words are still written to the draft; a notification shade or dialog (`inactive`) keeps dictating; a page pushed over the chat also stops it; a scope change (different server/session/location/credentials) interrupts it with words kept. Going to Android settings for the microphone (`_voiceSettingsOpened`) does not count as leaving; coming back retries the microphone.
- **Guards.** `_openVoice` is a no-op when: the chat is isolated (demo/preview), the platform lacks voice, a voice open or a send is in flight, or a voice conversation cannot send. It first stops any read-aloud (`_stopReading`). It builds a `current()` predicate (mounted, same voice epoch, same speech scope, profile readable, route is current) and abandons work whenever it turns false.
- **Key files.** `lib/ui/screens/chat/chat_voice.dart` (313 lines: `_openVoice`, push-to-talk, auto-send), `lib/ui/screens/chat/voice_conversation.dart` (670: `_onVoiceChanged`, `_mergeDictation`, `_finishDictation`, `_leaveDictation`, `_pauseDictation`, `_composerVoice()`), `lib/voice/controller.dart` (`VoiceComposerController`, `mergeVoiceDraft`), `lib/ui/kit/chat/kit_composer*.dart` (`KitComposerVoice`), `lib/ui/screens/chat/composer.dart` (gating: `onVoice`/`micHold` null when isolated, in conversation mode, or `!supportsVoice`).
- **Stored data.** The draft text rides the normal session draft store (`oc.sessionDrafts`), saved like typed text, so words survive the app going away mid-recording. `_dictationBase` (the composer value before dictation) is memory only.
- **Traps.** The controller listens to the recorder AND to the model manager; the host listens to the controller once (`_listenToVoice` de-duplicates by identity). Words are replaced (not appended) on every chunk: `_mergeDictation` always merges `transcript` into the **original** `_dictationBase`, so earlier chunks are not duplicated. Cancel drops the words (discard) while Leave keeps them; these are different calls.

#### MEDIA-VOICE-02  Hold to talk (push-to-talk)

- **Behaviour.** Hold the mic ≥ 350 ms to record; let go to stop and write it down; drag **≥ 80 dp** away from where the finger landed and let go to throw the recording away. Words while holding: "Release to send" (`KitVoiceHold.holding`) / "Release to cancel" (`cancelArmed`). A touch that drifts > 18 dp before 350 ms is a scroll, not a hold. A touch the system takes away (scroll claimed, call) cancels the hold. A recording shorter than **400 ms** is a slip and is discarded (`_finishHold`).
- **Early release.** If the finger lifts while the microphone is still being acquired (permission prompt, model setup), `_pttEndedEarly` remembers cancelled/finished and `_applyEarlyRelease` acts the moment listening starts. A setup sheet in between turns the session into a plain tap recording (`_pttMode=false`).
- **A held mic and a tap differ on auto-send**: a hold never auto-sends and never stops on silence; `stopOnSilence = byTap && autoSend`.
- **Haptics** (`KitHaptics`): `commit` when the hold starts, `select` each time the drag crosses the cancel distance in or out, `send` on a committed (not cancelled) release. Use `HapticFeedbackConstants` (CONFIRM/GESTURE_START/SEGMENT_TICK equivalents) natively.
- **Pure logic** is `KitMicPress` (`lib/ui/kit/chat/kit_mic_hold.dart`): states idle/down/holding; outcomes tap/holdCommitted/holdCancelled/none; constants `holdAfter 350 ms`, `slop 18`, `cancelDistance 80`. Port it as a pure Kotlin class and test it first.
- **Teaching.** Tip `hold-mic` (max count from `teachingEntry`, 2-minute cool-down between tips); `_micTipWanted` set when a tap recording started; offered once the box is empty again; `markUsed` when the first hold starts. Replayable from Help > Tips.
- **Commit.** `d389f6a24` 2026-10-31 "hold the mic to talk, send when I stop talking, voice language follows the app" (Sonnet 5.5). Roadmap issues #35 #36 per memory.

#### MEDIA-VOICE-03  Send when I stop talking (auto-send)

- **Setting.** Settings › Voice typing › "Send when I stop talking" (`VoiceAutoSendRow`, switch key `voice-auto-send`), **off by default**, stored `voice.auto_send` (bool, global).
- **Behaviour.** Only for a **tap-started** dictation with the setting on: `SilenceDetector` ends the recording after **1.5 s of quiet that follows speech**; quiet before any speech never ends it; a click or cough shorter than **300 ms** of loud time is not speech; loudness is the 0..1 RMS of one 4096-byte buffer, speech threshold **0.015**; speaking again restarts the quiet timer. Then the words wait **3 s** in the pill ("Sending in 3…", `KitVoicePhase.sendingSoon`) with Cancel and Send now; the pill counts down to `sendAt` and calls `onSendNow` itself.
- **What may be sent.** `mayAutoSend(text)`: not starting with `/` or `!` (a mis-heard sound must never run a command) and `isSendableTranscript`: at least **2 letters or digits** after removing the model's notes `[..]`, `(..)`, `*..*`, `♪`, `♫` (any script counts). Otherwise the words stay in the box.
- **Cancel rules.** Any change to the text during the countdown cancels (compares `_composer.text == _autoSendText`); leaving the app cancels (words are never sent while the person is away); a send already in flight blocks it; the recorder is cancelled when the countdown starts (the recording is done).
- **Goes through the one send path** (`_send()`), "as if typed".
- **Pure logic to port first:** `SilenceDetector`, `isSendableTranscript`, `mayAutoSend` (`lib/voice/silence.dart`, 75 lines; tests `voice_auto_send_test.dart`).

#### MEDIA-VOICE-04  Voice conversation (hands-free loop)

- **Entry.** "+" › More › "Voice conversation" (`_PromptTool.conversation`; only when `supportsVoiceConversation` = voice **and** read aloud; blocked with the reason "available when the current run finishes" while the agent works). Refuses with a note if the box has text, attachments or review references (`voiceConversationDraftFirst`) or if the connection cannot send (`voiceConversationPausedDetail`).
- **Loop.** Listen → Send (in the send slot) → "Waiting for the reply…" → optionally "Reading the reply aloud" (Stop) → "The reply is ready" with Listen and "Read it aloud". A request on screen (permission, question, form) shows "Paused · the agent needs you" with no Listen so the request's answer is the only primary. Offline shows the pause reason with no action. Commands are refused in conversation ("conversation commands only" copy; text starting with `/`).
- **"Read replies aloud"** is a chip in the pill, turned on per conversation **never persisted**; turning it on runs the consent and voice-choice sheets right then (feature 19) so automatic playback can never be the first time the engine is touched. Exit, a scope change or a lifecycle pause revoke it.
- **Reply watch** (`_VoiceReplyWatch`). Armed immediately before dispatch; an auto reading happens only when the turn **verifiably** finished: connection live, `pending.requestComplete`, session seen busy then idle (`session.status` events: busy/retry then idle, or `session.idle`), exactly one live user message id equal to the dispatched message id and not in the pre-send id set, all assistant messages after it up to the next user message have `parentID == that id` (a mixed or parentless response is not read partially), every such message is done or errored, and prose is non-empty. Outcomes (`_VoiceReplyState`): idle, waiting, reviewNeeded ("could not be matched to your message"), interrupted (a request/question/form arrived), noProse (only code/tools), failed (engine refused). Never trusts `canonicalID` as proof of dispatch.
- **Utterance id.** `<sessionID>/voice-reply/<canonicalID>` (prefix `voiceReplyUtterancePrefix`) distinguishes an automatic reading from a manual one.
- **Speech scope tuple** (`_speechScopeNow`): (sessionID, promptShelfProfileID, locationRevision, baseUrl, username, password, profile readable, session known). Any change interrupts the conversation and revokes consent-to-auto-speak.
- **Epoch.** `_voiceEpoch` increments on every interruption so late async results from an old conversation are dropped.
- **Key files.** `voice_conversation.dart`, `chat_send.dart` (lines 206-640: claims before voice cancellation, `conversationSend`, `voiceSendCurrent()`, `_watchVoiceReply(pending)`, `_checkVoiceReply()`), `chat_page_cache.dart` (keeps `_voiceConversation`/`_voiceReplyWatch` in the cache signature), `phrase_chips.dart` and `chat_drafts.dart` (do not learn phrases or save drafts during a voice conversation).
- **Rewrite note.** This is chat-state logic that depends on the chat controller's event model (areas `chat-genui`, `protocols`). Keep it as a pure state machine with injected inputs (events, messages, connection state) so it is testable without audio.

#### MEDIA-VOICE-05  Recording pipeline and chunked transcription

- **Capture config** (`RecordVoiceRecorder`, `lib/voice/audio.dart`): PCM 16-bit little-endian, **16,000 Hz, mono**, `streamBufferSize 4096` bytes, Android audio source `mic`, `manageBluetooth: false`, audio interruption mode `pause`. Requests the permission first (feature 15), then checks the app is `resumed` (else "Voice input was interrupted."); a `generation` counter drops stale starts.
- **Interruption.** The recorder's `RecordState.pause` event emits `VoiceRecorderEvent.interrupted`; while listening that cancels with the reason "Recording was interrupted."
- **Accumulator** (`Pcm16Accumulator`): converts bytes to float samples (`signed/32768`), carries an odd trailing byte across buffers (`_pendingLowByte`), reports the RMS `level` of the last buffer (clamped 0..1), holds up to **480,000 samples (30 s)**; `add` returns how many bytes it took so a buffer crossing the chunk edge starts the next chunk.
- **Chunks.** Whisper hears 30 s at a time. Each time the accumulator is full the chunk is handed to the recognizer **while listening goes on**; chunks are recognized **one at a time in order** (`_recognitionTail` future chain; `_chunks[index]` filled as each finishes). No cap on the recording itself (P10.3). A 3-minute dictation = 6 chunks.
- **Stop.** `stopListening`: state → loading, `recorder.stop()`, wait for the stream's done up to **2 s**, enqueue the final partial chunk, await the tail, then `draft = transcript` (chunks joined with a space) and state `draft`. If no audio at all: error "No audio was captured." If a chunk fails: the recording stops, **words so far stay** in `transcript`, error shown; `VoiceRecognitionCancelled` returns to idle silently.
- **Cancel.** `cancel()` bumps the generation (stale results never become a draft), kills the recognition isolate, clears the buffers, awaits `recorder.cancel()`, and if a recognition was running goes through `finishingCancellation` until the isolate really exits (so the model memory is gone before the next start). Does **not** await `StreamSubscription.cancel()` (a root-zone future that never runs under a test fake clock).
- **States** (`VoiceComposerState`): modelRequired, downloading, verifying, initializing, loading, idle, listening, transcribing, finishingCancellation, draft, error. The controller mirrors the model manager's state while idle (`_onModelStateChanged`).
- **Clock.** A 100 ms timer updates `elapsed` (= previous chunks + current buffer duration) and `level` for the pill without rebuilding the field (`ValueNotifier<double> _voiceLevel`; the field itself updates ~30 times a second, PERF-4).
- **Key files.** `lib/voice/audio.dart`, `lib/voice/controller.dart`, `lib/voice/silence.dart`.
- **Native rewrite.** `AudioRecord` (VOICE_RECOGNITION or MIC source, 16 kHz mono PCM16) on a dedicated thread; handle audio-focus loss and `AudioRecordingCallback`/`onRecordingConfigChanged` for silencing; keep the 30 s chunking and ordered queue; keep the level calculation (RMS of the buffer) because the meter bars light at `level >= (i+1)/12` (`KitLevelMeter.litFor`, 9 bars).

#### MEDIA-VOICE-06  Speech recognizer (sherpa-onnx Whisper, on device)

- **Engine.** sherpa-onnx 2.0.1 (ONNX Runtime 1.28.2 inside) offline Whisper: `OfflineRecognizerConfig(model: OfflineModelConfig(whisper: OfflineWhisperModelConfig(encoder, decoder, language, task: 'transcribe'), tokens, numThreads, debug: false, provider: 'cpu', modelType: 'whisper'))`. Language `''` means detect.
- **Threads.** `conservativeVoiceThreadCount`: 2 when the device has ≥ 4 processors, else 1 (keeps the phone responsive).
- **Isolation.** Each chunk spawns a Dart **isolate**, which loads the models, decodes, returns text and exits (`errorsAreFatal`); decodes are serialized by a static `_decodeTail` so two chunks never decode at once; cancel kills the isolate with `Isolate.immediate` and `finished` completes only on isolate exit. Messages: `'loaded'` (moves state loading → transcribing), `{result}`, `{error}`, `'done'`.
- **Rewrite note.** In a native app run the recognizer on a worker thread, not per-chunk model reloads: the Flutter version pays model load every 30 s chunk (see Section 8). Keep **one warm `OfflineRecognizer` per pack/language** while a recording is active and free it a short time after (memory is the constraint: small pack ≈ 3.4 GB device RAM gate). Free native objects explicitly (`stream.free()`, `recognizer.free()`).
- **Sample hand-off.** Samples travel as `TransferableTypedData` (no copy).
- **Key files.** `lib/voice/recognizer.dart`, `lib/voice/model_manifest.dart`. Test: `voice_recognizer_config_test.dart`.

#### MEDIA-VOICE-07  Voice language

- **Choices.** `VoiceLanguage` has 36 values: **Follow the app** (`auto`, stored id `auto`, UI word `vpttLanguageFollowApp`), **Detect from speech** (`detect`), and 34 named languages with their own-script labels and Whisper codes: en, ar, es, fr, de, it, pt, nl, ru, uk, pl, cs, ro, el, tr, he, fa, ur, hi, bn, ta, zh, ja, ko, vi, th, id, ms, sv, no, da, fi, hu, sw.
- **Resolution** (`resolveVoiceLanguage`): a named choice is used as is; `auto` becomes the app locale's Whisper language when Whisper knows it, else `detect` (the model listens). `forLanguageCode` folds old/regional codes (`iw`→he, `nb`/`nn`→no, `in`→id).
- **Plain notes** under the language row (`voiceLanguageNote`): "Same as the app" falling back to listening ("<code> is not available offline, the model listens for the language") and "Compact is rough outside English" when pack = tiny and language is named and not `en`.
- **Picker.** `VoiceLanguageRow` (key `voice-model-language`) opens a kit sheet with a single-choice list (`voice-language-<name>` keys). Stored `voice.language` = the enum **name** (`auto`, `detect`, `english`, `arabic`, `spanish`, … `swahili`), not the Whisper code; `fromID` falls back to `auto` for anything unknown. Old installs stored only `auto`/`english`/`arabic`, which is why the stored value is the name; keep these exact strings for the migration.
- **Per-recording language.** `appLanguageCode` is set from the current locale just before listening.
- **Tests.** `voice_language_locale_test.dart` (14 catalog rows).

#### MEDIA-VOICE-08  Speech model packs and device gating

| Pack id | Label (UI word) | Repo (Hugging Face) | Pinned revision | Files and exact sizes | Min total RAM |
|---|---|---|---|---|---|
| `base` (default) | Balanced ("Recommended") | csukuangfj/sherpa-onnx-whisper-base | bb53ee204431c90d314c1cc08d28d23e5b7927cc | base-encoder.int8.onnx 29,120,534; base-decoder.int8.onnx 130,672,026; base-tokens.txt 816,730 = **160,609,290 B (~161 MB)** | 1536 MB |
| `small` | High accuracy | csukuangfj/sherpa-onnx-whisper-small | 8f3c18b358db4d1f2fc1eae49d75cd20989e4309 | small-encoder.int8.onnx 112,442,483; small-decoder.int8.onnx 262,226,114; small-tokens.txt 816,730 = **375,485,327 B (~375 MB)** | 3400 MB |
| `tiny` | Compact fallback | csukuangfj/sherpa-onnx-whisper-tiny | 65176e2deb88badc814a94058666cadccc29b61c | tiny-encoder.int8.onnx 12,937,772; tiny-decoder.int8.onnx 89,855,401; tiny-tokens.txt 816,730 = **103,609,903 B (~104 MB)** | 1024 MB |

SHA-256 of each file is pinned in `lib/voice/model_manifest.dart` (copy them from there; tokens.txt hash is `b34b360d…fa126` for all three). URL = `https://huggingface.co/<repo>/resolve/<revision>/<file>`. File order is fixed: encoder, decoder, tokens.

- **Gate = total physical RAM, never `ActivityManager.memoryClass`** (memory `voice-model-memory-gate`; commit `0c52d80f4`): thresholds are set below marketed size because a "4 GB" phone reports ~3.6 GB. Unknown RAM blocks nothing in the manual picker (the person may try) but **blocks the automatic choice** (feature 11).
- **Support check** (`supportFor(pack)`): ABI must be one of arm64-v8a, armeabi-v7a, x86, x86_64 (empty list passes); RAM ≥ pack minimum; free storage ≥ `size + max(128 MiB, size/4)` unless already installed (and not replacing). Reasons are typed (`VoicePackUnsupported.abi|memory|storage`) and worded via `voiceSupportReason`.
- **Automatic choice** (`automaticVoicePack`): keep the selected pack if installed and eligible (eligible = supported, or only short of storage); else the eligible pack with the highest RAM tier. Storage never changes the choice (it only blocks the download with its own reason).
- **Install layout:** `<filesDir>/voice_models/<packId>-<revision>/<files…>` plus `.installed.json` marker (Section 5).

#### MEDIA-VOICE-09  Verified, resumable model downloader

(`lib/voice/model_download.dart`, 795 lines; tests `voice_downloader_test.dart` 14 catalog rows; the same downloader serves the natural voice, feature 22.)

- **HTTPS only**, redirects followed **manually** up to 5 and each hop must be https (`unsafeRedirect` otherwise); `User-Agent: OpenCode-Mobile/voice-model`; connect timeout 15 s; per-step network timeout 15 s ("connecting", "waiting", "following the redirect"); body idle timeout 20 s; an aborted client is `close(force:true)`.
- **Resume.** Partial files are `<name>.part` in `<packDir>.installing/`. On restart it sends `Range: bytes=<offset>-`; it accepts `206` only when `Content-Range` equals `bytes <offset>-<len-1>/<len>` exactly, restarts on `200`, fails on anything else (typed `http`). Content-Length must equal the remaining pinned size or the download fails (`length`); a body that exceeds the pinned size aborts.
- **Verify.** SHA-256 and length of every staged file are checked **in a separate isolate** (cancellable) before moving into place; a bad file is deleted and the error is `checksum`; after all files, a second pass (`verification`), then the marker is written atomically (`.tmp` then rename) with pack id, revision and the file list.
- **Publish and rollback.** Files are moved from staging into the pack directory one by one; an existing target is moved to `<target>.voice-backup` first; on any failure the new files are deleted and backups restored; backups are removed on success. "Re-download" (`replaceExisting`) keeps the verified old model on failure.
- **Locking.** `_installLocks` is a static per-pack-directory future chain: same-pack installs and deletes serialize; delete waits for an in-flight install.
- **Cancel.** `VoiceCancellationToken` aborts a stalled body or TLS handshake promptly. On `VoiceDownloadCancelled` the downloader deletes the staged files, the `.part` files and the staged marker (test `voice_downloader_test.dart:275`: `expect(cancelledStore.files, isEmpty)`). DRIFT with the P10.4 doc and the setup-sheet comment ("a partial file is kept, so the next attempt resumes"): in the code a *cancel* leaves nothing; **resume only happens after a crash, kill or network failure**, where the `.part` survives. A rewrite should decide this deliberately (keeping the partial on cancel is cheap and matches what the docs promise).
- **Installed check** (`verifyInstalled`): marker exists, JSON matches pack id and revision, every file length+SHA matches; any mismatch deletes the marker. This SHA pass over 375 MB runs at start-up in an isolate (`initialize()` verifies all three packs) — see Section 8.
- **Failure kinds** (`VoiceDownloadFailure`): unknown, https, closed, redirect, unsafeRedirect, noResponse, timeout, checksum, verification, http, length, incomplete → each mapped to a plain sentence (`e7VoiceUi*`).
- **Native rewrite.** Use a Kotlin HTTP client (Ktor or OkHttp) with the same rules; keep byte-exact `Range` semantics; SHA-256 via `MessageDigest` on `Dispatchers.IO`; the downloads must tolerate process death (resume).

#### MEDIA-VOICE-10  Model manager (selection, readiness, preferences)

- **State machine** (`VoiceModelState`): checking → required | ready | error; downloading → verifying → ready; loading (while a transcription holds the model; `markLoading`/`markReady`). `downloadInProgress` includes the preflight window (`_preparingDownload`). A `_lifecycleGeneration` counter discards stale work after delete/select/dispose/cancel (tests `voice_model_manager_test.dart`).
- **Preferences** (global SharedPreferences, stored verbatim, not redacted): `voice.selected_pack` (pack id, default `base`), `voice.language` (enum name), `voice.auto_send` (bool).
- **Operations:** `initialize`, `selectPack` (blocked while downloading/verifying/loading), `setLanguage`, `setAutoSend`, `downloadSelected({replaceExisting})` (device probe → `supportFor` → download with progress; preflight failure → `VoiceModelPreflightException` with typed support), `cancelDownload`, `deletePack`, `redownloadPack`, `shared()` (singleton future, cleared on failure).
- **Probe** (`getDeviceInfo` on `oc/voice`, 5 s timeout): `availableStorageBytes`, `memoryClassMb` (diagnostics only), `totalMemoryMb`, `lowRamDevice`, `supportedAbis`, `hasMicrophone`. Failure → `VoiceDeviceInfo.unknown()` (capture assumed possible so the picker is not emptied); non-Android → `unsupported()` (no capture path; a desktop user must not walk into a download).

#### MEDIA-VOICE-11  First-mic automatic setup

(`lib/voice/automatic_setup.dart`, `automatic_setup_platform.dart`, sheet in `voice_ui.dart` `showVoiceAutomaticSetupSheet`; tests `voice_automatic_setup_test.dart` 33 rows, `voice_automatic_setup_platform_test.dart`, `voice_automatic_setup_sheet_test.dart`.)

- **When.** First mic tap when no speech model is on the phone (`!voice.models.isReady`).
- **Flow.** Checking → pick pack by total RAM (feature 08) → if installed: start listening; else read network (`unmetered|metered|offline|unknown`) → **consent** sheet: "This phone's memory picked <Balanced>", size on the button ("Download 161 MB" / "Download 161 MB on mobile data"), notice on mobile data or unknown network, folded Details (file names, exact MiB, memory rule — the only place MiB/file names appear), tertiary "Choose another speech model" (opens Settings › Voice sheet). Consent is **per attempt and never persisted**; metered/unknown/cellular requires `allowMetered`; the network is re-read after the notification prompt (the prompt can stay open long enough for Wi-Fi to change) and again may demand consent.
- **Download.** Notification permission requested (Android 13+) but never blocks; progress shows in plain MB with Cancel download; a line says "also shows in the notification" only if the notification really posted; verifying shows a waiting bar and hides Cancel.
- **When ready.** Starts the microphone and **hands off** (`handOff()`) to the composer's own voice surface so cancel/dispose of the setup no longer cancels the recording. If the app is in the background when the download finishes, the stage stops at `ready` ("The speech model is on this phone", Start listening) — **never opens the microphone in the background** (`setForeground(false)` on paused/hidden; `inactive` does not count).
- **Problems** (`VoiceSetupProblem`): unavailable, unknownMemory ("Choose a speech model"), noSupportedPack (the smallest pack's own reason), offline (Try again), busy (another download: "Show the download"), downloadFailed (plain text + Try again, raw text under Details), listeningFailed (permission-permanent → "Allow microphone in Android settings").
- **Closing the sheet cancels the download**; leaving the app does not.
- **Notifications** are serialized through a static future tail so a queued progress update can never overtake completion/dismissal; the tail clears when idle so no settled future from a fake-async test zone is retained; at most one post per `phase:percent` key.

#### MEDIA-VOICE-12  Native bridge `oc/voice`

Methods (Kotlin: `MainActivityMedia.register` + `VoiceDownloadNotifications.handle`):
`getDeviceInfo`; `requestMicrophonePermission`; `openAppSettings`; `getVoiceSetupNetwork`; `requestVoiceDownloadNotificationPermission`; `showVoiceDownloadNotification({phase, receivedBytes, totalBytes})`; `dismissVoiceDownloadNotification`. Details in Section 4.

#### MEDIA-VOICE-13  Settings › Voice typing page and the model sheet

- **Entry points:** Settings › Voice typing (`VoiceSettingsScreen`, a page), found by Settings search (`search_index.dart` `_openVoice`, loader `voiceManagerLoader`); the sheet `showVoiceModelSetupSheet` for the chat's mic "Choose another model", the capability flow `KitEnableFlows.voiceModelSetup`, and "Choose a speech model" in setup problems.
- **Content, top to bottom:** running download (title, `KitProgressView` known bar with "X MB of Y MB" or waiting "Verifying", Cancel download), failure notice with Details fold, privacy notice with a lock icon ("Audio never leaves this phone.", `e7VoiceUiPrivacyDownload`), no-microphone notice, **model list** (single choice, `actsOnTap:false` — a tap only chooses; the pinned primary downloads, because a list that acts on tap would mark a not-installed pack "Current"), each row "Installed"/"Not downloaded · 161 MB" + description, dimmed with the reason when unsupported, Re-download and Delete (destructive, confirm sheet naming pack and size) for the selected installed pack, Language row, Auto-send switch, then the **Read-aloud voice** section (feature 22), all behind `platformCapabilities.supportsVoice`.
- **Pinned primary** follows the manager: "Download Balanced (161 MB)" (disabled with the reason if unsupported) → none while busy → "Use Balanced" once a download here finishes → nothing when opened with the model already installed and selected (secondary becomes "Done").
- **A download keeps running in the manager when the sheet is closed;** reopening shows progress.
- **Key files:** `voice_ui.dart` (787 lines), `voice_settings_parts.dart`, `presentation.dart`, `natural_voice_ui.dart`.

#### MEDIA-VOICE-14  Phone-setup voice component

`VoiceSetupComponent` (`lib/builtin/setup/voice_component.dart`, implements `SetupAppComponent`): voice typing's speech model is offered on phone setup's Customize sheet and "Add tools", **off by default**. Not part of Linux: the model lives in the app's storage and is fetched/verified/deleted by the same `VoiceModelManager`, so setup and Settings agree. `offer()` uses `automaticVoicePack` (unknown RAM → no offer); `install` refuses if Settings is downloading (busy); `remove()` deletes **every** pack including partials; `failureOf(error)` maps to `SetupAppFailureKind` (noSpace for errno 28, offline for socket/timeouts, checksum, unsupported, failed). 28 catalog rows in `setup_voice_component_test.dart`.

#### MEDIA-VOICE-15  Microphone permission and recovery

- **Native three-state answer** (`granted | denied | permanentlyDenied`) computed on the Kotlin side with a `voice_permissions` SharedPreferences flag `microphone_requested` plus `shouldShowRequestPermissionRationale` (Android cannot say "never ask again" otherwise).
- **Requesting** `RECORD_AUDIO` also asks `POST_NOTIFICATIONS` in the same dialog on Android 13+ when not granted (so the voice download notification can show). The call first runs `beforeMicrophoneCapture()` (stops read-aloud; Section 4); if that cannot stop playback it fails with `voice_unavailable`, surfaced as `VoiceDeviceUnavailable` ("Stop playback, check microphone settings, and try again").
- **In voice mode:** denied once → reason "Voice typing needs the microphone. Tap Allow microphone, then choose Allow." + fix "Allow microphone" (asks again); permanent → "Android blocks the microphone for this app. Turn it on in Android settings, then come back here." + fix "Allow microphone in Android settings" (opens app settings; coming back retries once). Other failures: plain `voiceErrorText` + "Try again".
- **Errors typed:** `VoicePermissionDenied(permanent)`, `VoiceDeviceUnavailable`, `StateError` strings mapped by exact message ("No audio was captured.", "Recording was interrupted.", "Voice input was interrupted.", prefixes "Microphone error:" and "Microphone state error:") — **DRIFT/trap:** UI copy is chosen by matching exception *strings*; the rewrite should use typed errors.

#### MEDIA-VOICE-16  Voice licenses (open-source parts)

Settings › About › "Voice licenses" group (`VoiceNoticesView`, key `about-voice-licences`): one row per component — OpenAI Whisper (MIT), Kokoro (hexgrad, Apache-2.0), sherpa-onnx (k2-fsa, Apache-2.0), ONNX Runtime (Microsoft, MIT), record (llfbandit, BSD-3-Clause) — each opens the license text in `KitViewer` (text loaded from bundled assets `LICENSES/*.txt`, reflowed from 80 columns into paragraphs by `reflowLicenseText`) with one action "Open <project> website" through `openExternalLink`. Bundled assets listed in `voiceNoticeAssets` (THIRD_PARTY_NOTICES.md + 5 license files). A native rewrite must ship the same license texts and update component names (e.g. `record` disappears; AudioRecord is platform).

#### MEDIA-VOICE-17  Capability enable flows

`lib/ui/capability_flows.dart`: when a feature says "not set up", `KitEnableFlows.voiceModelSetup` opens the voice sheet; `microphone` requests the permission and, if permanently denied, opens app settings; `camera` does the same through `cameraPlatform`. All behind `platformCapabilities.supportsVoice`.

#### MEDIA-VOICE-18  Level meter and the voice pill (UI contract for the rewrite)

- `KitLevelMeter`: 9 bars, bar *i* lit when `level >= (i+1)/12`, decorative (excluded from semantics), `active:false` rests every bar; `.listen(ValueListenable)` repaints that part alone at ~30 updates/s.
- `KitComposerVoice` carries: phase, hold, sendAt/onSendNow/onCancelSend, onExit, conversation flag, level, listeningSince (elapsed clock "m:ss" with no cap), onListen/onStopListening/onStopSpeaking/onReadReply, readRepliesAloud + change callback, reason, fix action. In listening the trailing circle is accent: "Send" (conversation) or "Done" (dictation). Speaking → Stop (neutral `text1` circle, `ground` square). Reply ready → "Read it aloud" tertiary + Listen mic circle.
- Keys: `voice-mode`, `voice-mode-try-again`, `voice-mode-open-settings`, `voice-mode-allow-mic`; copy keys `kitVoice*` (17).

### 2.2 Read aloud (text to speech), features 19-24

#### MEDIA-VOICE-19  Read a reply aloud (manual)

- **Where.** A reply's long-press/overflow menu has **"Read reply prose"** (`message-menu-read-aloud`) and, once consent was given, **"Read with another voice"** (`message-menu-read-aloud-voice`), both only when `_canReadReply`: not isolated, `supportsReadAloud`, not opening voice, not (conversation and unable to send), no request in flight, profile readable, role `assistant`, and the extracted prose is non-empty (commit `337db92bb` hides the action when a reply has no prose). In a voice conversation the strip's "Read it aloud" reads the newest readable reply. "Stop reading aloud" stops (`readAloudStop`).
- **Consent (once, remembered on this phone).** A kit confirm sheet "Read replies aloud?" ("Your phone's speech engine reads the reply aloud. Code and tool details are skipped. This phone remembers your answer."), with two consequence lines ("Only offline voices are offered, but the speech engine is separate software with its own privacy terms." and "People near you may hear it. Reading stops when you leave this conversation or the app."), button "Read aloud". Stored `oc.readAloudConsent` (bool) in the app preferences, **app-wide, not per server** (the engine belongs to the phone). The engine is never touched before consent.
- **Voice choice, in order.** (1) If the natural voice is the Settings choice and installed → marker `'natural'` (feature 22); (2) else the phone's **offline** voices via `voices()`; (3) pick by locale automatically (`readAloudVoiceForLocale`: exact language+region, else same language, else none → engine default; locale tags tolerate `_` and case); (4) only if no voice speaks the app's language, or for "Read with another voice", a kit choice sheet "Choose a reading voice" lists the voices (key `read-aloud-voices`, items `read-aloud-voice-<id>`, current one checked, tap chooses and closes). The chosen id lives in memory (`_readAloudVoiceID`) per chat screen and is cleared on scope change.
- **Failures worded** (`ReadAloudFailure`): unsupported, noOfflineVoice ("No installed voice marked offline is available. Configure an offline voice in your system speech settings…"), engineUnavailable ("The speech engine could not read this reply. Try again or choose another voice."), tooLong ("This reply is too long to read aloud…"), busy ("Speech playback is unavailable while audio capture or another audio interruption is active."). The composer note shows each failure once (`_lastReadAloudFailure`).
- **Key files.** `lib/ui/screens/chat/read_aloud.dart` (297), `chat_message_actions.dart` (menu), `lib/voice/read_aloud.dart` (352, `ReadAloudController`), `lib/state/interaction_defaults.dart` (`DefaultChoice.voice`, the locale rule duplicated for defaults).

#### MEDIA-VOICE-20  What gets spoken (prose extraction)

`KitMarkdown.proseForSpeech(source)` (`lib/ui/kit/chat/kit_markdown.dart:167`): input over **131,072** chars throws; fenced code blocks (``` or ~~~, with matching/longer closing fence) are skipped whole; horizontal rules and table delimiter rows are skipped; per line it removes heading markers (`#`..`######`), quote `>`, list bullets `- * +` and `1.` numbers, replaces an image with its alt text, then replaces inline code/emphasis/links by their visible text (link destinations never spoken; a bare URL is read as written); output over **12,000** chars throws `FormatException` (surfaces as `readAloudTooLong`). Tool detail parts are never read (only the message's text parts via `_messageText`); "Never fall back to copy/export text, which can include other part kinds". For a voice conversation the prose of every assistant message of the turn is joined with blank lines.

#### MEDIA-VOICE-21  Native read-aloud bridge (system TTS) — `oc/read-aloud`

Kotlin `ReadAloudBridge` (477 lines) — the whole contract is in Section 4. Behaviours that must be re-implemented in the native app:
- Lazy `TextToSpeech` engine: created on first `voices`/`speak` (init timeout **8 s**; a second request during init gets `busy`), kept for later requests, shut down on dispose, when init fails/aborts, and by `beforeMicrophoneCapture` when nothing was playing; pause cancels playback and aborts a pending init; no text is logged or kept (pending `speak` closures that retain text are released on cancel).
- Only **offline** voices: `!isNetworkConnectionRequired` and not `KEY_FEATURE_NOT_INSTALLED`, sorted by name; the Dart `ReadAloudVoice` id and label are both `Voice.name`; locale is a BCP-47 tag.
- Voice choice in the bridge when none given: default voice, else `Locale.getDefault()` match, else same language, else first.
- Audio attributes `USAGE_MEDIA` + `CONTENT_TYPE_SPEECH`; **audio focus** `AUDIOFOCUS_GAIN_TRANSIENT` with `setWillPauseWhenDucked(true)`; **any** focus change other than GAIN cancels the operation.
- Text is split into chunks of at most `min(TextToSpeech.getMaxSpeechInputLength(), 3000)` UTF-16 units without splitting a surrogate pair; chunks are spoken with `QUEUE_FLUSH` one after the other (`onDone` of one enqueues the next); each chunk has a **300 s** watchdog (`playbackTimeout`).
- Terminal status events `invokeMethod("status", {operationID, status})` with `completed | cancelled | engineUnavailable | busy`, sent only for an operation the Dart side was told was accepted.
- Operation ids are generated by Dart (`<micros>-<seq>`, ≤ 128 chars); a stale `stop(operationID)` cannot stop a newer operation.
- Everything runs on the main thread; a single-thread executor feeds the natural voice's `AudioTrack`.

#### MEDIA-VOICE-22  Natural voice (Kokoro) for read aloud

- **What.** Settings › Voice typing › "Read-aloud voice": **Phone's voice** (default, "Uses the speech voices installed on this phone") or **Natural voice** ("Natural voice (download, about 148 MB)", then "Natural voice" once installed). Choosing natural when not installed downloads then selects; "Remove natural voice (148 MB)" is a destructive confirm that deletes the files and returns to the phone's voice. Hidden where read aloud does not exist.
- **Pack** (`naturalVoicePacks`, one entry): id `kokoro-en`, Kokoro v1.0 **int8** (Apache-2.0), repository `csukuangfj/kokoro-int8-multi-lang-v1_0`, revision `57adb37fc0e8d4bd430cfc1b8f8c2294945b9af2`, files in fixed order: `model.int8.onnx` 114,202,993; `voices.bin` 28,200,960; `tokens.txt` 687; `lexicon-us-en.txt` 5,956,885 (total **148,361,525 B**); speaker id **3** (`af_heart`), output rate **24,000 Hz**, languages `{en}`, `minimumMemoryMb: 2000` (**declared but not enforced** by `NaturalVoiceController`, only free storage is checked — add the gate in the rewrite). Installed into the same `voice_models` folder with the same downloader (feature 09).
- **Arabic is not offered**: sherpa-onnx 2.0 removed espeak-ng and every published Arabic Kokoro-runtime model needs espeak G2P plus diacritization. Arabic replies are read by the phone's voice. Add a pack when a lexicon-based Arabic model is published.
- **Language routing** (`detectReadLanguage(text, appLanguageCode)`): counts runes in Arabic ranges (0600-06FF, 0750-077F, FB50-FDFF, FE70-FEFF), Latin (A-Z, a-z, 00C0-024F) and other scripts (0370-05FF, 0700-074F, 0780-1FFF, 3040-D7FF, FF00-FFEF); none → the app language; Arabic ≥ Latin and ≥ other → `ar`; other > Latin → `other`; Latin wins → the app language when it is written in Latin letters, else `en` (non-Latin app languages: ar fa ur he iw ru uk el hi bn ta zh ja ko th). `packFor(code)` returns the installed pack that speaks it, else the **phone's voice reads** and the **one-time** plain note `naturalVoiceFallbackNote` is shown for that language (`takeFallbackNote`).
- **Synthesis.** `NaturalSpeech` (paths, speaker, rate, threads: 2 if ≥ 4 cores else 1) goes to a worker isolate that builds `OfflineTts(kokoro: …, maxNumSentences: 1)`, **fails if `tts.sampleRate != 24000`**, splits the text by `splitForSynthesis` (sentence ends `.!?` and newlines, then the nearest space after **280** chars), generates each piece (`speed 1.0`), converts float samples to PCM16 LE and posts each as `TransferableTypedData`; after the last piece `'done'`; any failure `'failed'`.
- **Playback** is not the Dart side: Dart calls `naturalBegin {sampleRate}` → native creates a mono PCM16 `AudioTrack` (stream mode, buffer `max(minBuffer, rate)`), requests focus, accepts; then `naturalWrite {pcm}` per sentence (queued frames counted; watchdog = queued duration + 90 s); `naturalEnd` → native waits until `playbackHeadPosition >= queuedFrames` (polling every 100 ms) and reports `completed`. The writer thread uses `WRITE_NON_BLOCKING` with a 15 ms sleep so it never blocks in native code; cancel does `pause()+flush()` and releases the track behind queued writes. Initial watchdog **90 s** (loading the voice can take seconds before the first sound).
- **Same stop/focus/interruption path** as the phone voice: mic start, focus loss, output-device removal and app pause all cancel it; synthesis isolate is killed on `_clear`.
- **Key files:** `natural_voice.dart` (434), `natural_synthesis.dart` (191), `natural_voice_ui.dart` (210), `read_aloud.dart`, `ReadAloudBridge.kt`. Stored: `voice.read_aloud_natural` (bool, global). Test: `natural_voice_test.dart`. Commit `65e3f70a3` (2026-11-01).
- **Rewrite note.** In Kotlin this is simpler: synthesize on a worker coroutine and write straight to the `AudioTrack` — no PCM crossing a channel. Keep the same sentence splitter, rate check and 1-thread/2-thread rule.

#### MEDIA-VOICE-23  Read-aloud ownership, lifecycle and scope

- **One reader at a time** per app: a static `_owner`; `speak` stops the previous owner (without awaiting) before claiming; `await`-free claim so platform FIFO plus operation-scoped stop prevents an old controller stopping its replacement.
- **Foreground only.** `speak` fails with `busy` unless the app lifecycle is `resumed`; `didChangeAppLifecycleState != resumed` calls `stop()`; the native side also pauses on `onPause` (`foreground=false`, cancels) and disposes on engine cleanup. Reading never continues in the background (no foreground service).
- **Scope.** Reading belongs to a speech scope tuple (feature 04). A change stops reading, clears the chosen voice id, revokes automatic speaking, and (in a conversation) interrupts it. Route is current: if another route covers the chat, results are dropped.
- **Request counters.** `_readAloudRequest` increments on every request/stop; every `await` is followed by `current()`.
- **Timeouts.** voices 12 s, speak/begin 12 s, stop 3 s (errors swallowed — native pause/destroy stops independently).
- **Limits.** Text must be non-blank and ≤ **12,000** UTF-16 units (blank maps to `tooLong`; no invalid-input variant in the frozen API).
- **Unknown terminal status** from native is treated as `engineUnavailable` and the operation is stopped, so the UI can never stay "speaking" forever.

#### MEDIA-VOICE-24  Mutual exclusion of capture and playback, audio focus

- Starting the microphone (permission request, and again on grant) calls `ReadAloudBridge.beforeMicrophoneCapture()`: sets `micGuardUntil = now + 10 s`, cancels any operation with status `busy`, aborts a pending init, and releases the TTS engine if nothing was playing. While the guard is active, or while `AudioManager.activeRecordingConfigurations` is non-empty (API 24+; on exception assume busy), every speak/natural begin fails `busy`.
- While reading, an `AudioRecordingCallback` cancels playback when any recording starts (`busy`), an `AudioDeviceCallback` cancels when an output device is removed (headphones unplugged), and focus loss cancels.
- The microphone side relies on the `record` plugin's `audioInterruption: pause` (a system interruption pauses the recorder → event → cancel with "Recording was interrupted.").
- **Rewrite:** one `AudioSession` object (common interface, Android actual with `AudioFocusRequest` + callbacks) shared by capture and playback; keep the 10 s guard.

---

### 2.3 Camera, photos and attachments, features 25-36

#### MEDIA-VOICE-25  The "+" tray and the tools list

- **Tray (concept C, owner 2026-10-20).** "+" turns into "x" and the pill grows upward by **one compact row** of round matte buttons with a word under each, a hairline between it and the field; no sheet, no glass; KitReveal fold+fade (instant with reduced motion). Buttons: **Photos, Camera, File, More** (`composer_tray.dart` `_trayActions`; Photos and Camera only when `promptAttachmentsSupported && supportsPromptPhotos`; File only when attachments are supported and the agent is not images-only; no tray at all in the isolated demo or in conversation mode, or when there is nothing to attach — then "+" opens the sheet, which says why). While a run is going the three media buttons stay in place but disabled with the reason "available when the current run finishes". Keys `composer-tool-gallery`, `composer-tool-camera`, `composer-tool-attach`, `composer-tray-more`. Labels shrink together (same scale) until the longest fits its quarter of the row — whole words in one size, never "Ph…".
- **"More" tools sheet** (`composer-tools-sheet`, `_PromptToolsList`): conversation settings rows (model, approvals, plan, background) first; then Project file (mention), Attach file (when not in tray), Photos and Camera (when not in tray; only when attachments are supported and `supportsPromptPhotos`), **Voice input** ("Records and transcribes on this device", Android only), Commands; then fold "Prompts" (Reuse, Saved, Save for later, Clear text) and fold "More" (Web sources, **Voice conversation**). A tool the server or moment cannot run stays in place and says why: "available when the current run finishes", "this agent takes pictures only" (`composerToolsAgentPicturesOnly`), "text only" (`composerToolsAgentTextOnly`/`composerToolsTextOnly`).
- Keys: `composer-tool-<key>` (attach, gallery, camera, voice, commands, conversation, project-file, web-sources, history, saved, stash, clear), `composer-tools-button`, `kit-composer-tray`.
- **Files:** `chat/composer_tools.dart`, `chat/composer.dart`, `kit/chat/kit_composer_tray.dart`.

#### MEDIA-VOICE-26  Photos from the library and the camera

- **Library.** "Photos" opens a **multi-select** for as many photos as the draft still has room for (limit = 5 − current). Exactly one slot left uses the single picker (the platform rejects limit 1 for multi-pick). The system picker is the Android Photo Picker via `image_picker` (`pickMultiImage(limit)`, `pickImage`).
- **Camera.** "Camera" opens the system camera app for **one** photo (`ImageSource.camera`). No in-app camera preview for photos. **Trap (plugin behaviour, plugin source not opened here):** because the app manifest declares `CAMERA` (for the QR scanner), `image_picker_android` asks for the runtime CAMERA permission itself before launching the camera app (an app that declares CAMERA but has not been granted it makes `ACTION_IMAGE_CAPTURE` throw `SecurityException`); a denial arrives as a `PlatformException` whose code contains "denied" and is shown as `photoPermissionDenied`. A native rewrite must either request CAMERA before `TakePicture` or not declare it; QR scanning needs it anyway, so request it with the same three-state `PermissionGate` as the scanner.
- **Scaling happens in the platform picker before bytes are read:** `maxWidth = maxHeight = 2048`, `imageQuality = 85`, `requestFullMetadata = false`. Why (commit `7356ca5df`): full camera frames were several MB each and are re-sent on every step of a turn; five of them overflowed the server's model connection ("WebSocket inbound queue overflow"); models downscale to ≈ 2000 px anyway.
- **Read and validate** (`PromptPhotoStore.readPhoto`): file length ≤ **10 MiB** (`maxBytes`), stream-read with a running cap; format sniffed from **magic bytes** — PNG `89 50 4E 47 0D 0A 1A 0A`, JPEG `FF D8 FF`, GIF87a/GIF89a, WebP `RIFF….WEBP` — else `unsupported`; result is a `PromptAttachment(filename, mime, url: "data:<mime>;base64,<bytes>")`.
- **Draft limits** enforced here too: ≤ 5 attachments and ≤ **20 MiB** aggregate (decoded bytes computed from the base64 length and padding); gallery results skip duplicates by url and stop when full → "draft full" note (`photoDraftFull`: "Remove an attachment first. A draft holds up to 5 files and 20 MB in total.").
- **Errors worded** (`PromptPhotoFailure`): tooLarge, unsupported, storage, pending ("another photo is waiting"), unavailable; a `PlatformException` whose code contains "denied" → `photoPermissionDenied`.
- **Gating:** `platformCapabilities.supportsPromptPhotos` (Android), attachments supported by the server/agent (`capabilities.promptAttachments`; Codex shows `codexTextOnlyPrompt`), not isolated, prompt shelf not busy, not in conversation mode.
- **Key files:** `lib/state/prompt_photos.dart` (`PromptPhotoStore`, 313 lines), `chat/chat_attachments.dart` (`_pickPhoto`, `_pickGalleryPhotos`).

#### MEDIA-VOICE-27  Photo recovery after the app is killed, and the draft attachment vault

This is the most defensive code in the area: Android may **kill the app while the camera/picker is open**, so a photo must still land in the right conversation's draft.

- **Origin record committed before launch.** `pick`/`pickMany` write `oc.pendingPromptPhoto` (JSON `{id, profileID, sessionID, directory, workspace, ref?, path?, name?, failed}`) *before* the picker opens; a second picker cannot start while the key exists or `_busy`. On a normal result the record gets the file path, then the photo is read and copied into the vault and the record gets `ref`; discard on use.
- **Recovery at bootstrap** (`DraftPhotoRecovery.recover`, called before chat routes read drafts, serialized): `ImagePicker.retrieveLostData()` (the plugin clears its cache, so the returned path is saved first), else the saved `path`; completes the record; moves the photo into the **draft of the profile+session recorded in the record** (not whatever conversation is open) with a content-addressed blob (dedupes on restart); sets `oc.draftAttachmentVault=true` first (the profile-deletion sweep only collects vault files after this marker); enforces ≤ 5 attachments and ≤ 32 MiB per draft; results `nothingPending | attached | profileRemoved | retryRequired`. Profile removal always wins over recovery.
- **Opening a conversation with a waiting photo:** a photo for *this* conversation joins the composer first (no question); one for another conversation goes into *that* conversation's saved draft (`recoverPendingPhoto`); one that cannot move yet blocks a new pick with `photoPendingOther`.
- **Vault layout and rules** (`DraftAttachmentVault`): directory `<filesDir>/draft-attachments-v1/<sha256(ownerProfileId)>/<sha256(dataUrl)>.blob` (hex-64 names only; temp `.tmp` then atomic rename; the blob **content is the data-URL text as UTF-8**, not raw bytes); caps **256 MiB** total disk, **32 MiB** per draft, **5** attachments; integrity re-checked by length and SHA-256 with a *bounded streaming read* (a length check followed by a whole-file read is not a bound: the file can grow in between); `restore` refuses changed size/content and returns names it could not restore as `unavailable`; `file`/`http(s)` attachment URLs are stored as references (file URLs only restorable to the same location); `collect` deletes only unreferenced vault payloads, never user-picked files.
- **Safety filters.** Identities (ids, directory, workspace, blob) must survive `KitRedact.text` unchanged (`savedPromptIdentity` throws otherwise) so a record can never silently route to another owner; text attachments are redacted before persisting; **binary media is never rewritten**; a recovered photo whose data URL contains a secret (`KitRedact.containsSecret`) is not attached (`retryRequired`). Exceptions are never exposed (they can contain paths).
- **Profile deletion** (`connection/deletion.dart`): `promptPhotos.clearForProfile(profileId)` and the vault `collect({}, owner: profileId)`.
- **Key files:** `prompt_photos.dart`, `draft_attachments.dart` (336), `draft_photo_recovery.dart` (161), `saved_prompt_safety.dart` (71), `connection/drafts.dart`, `session_drafts.dart` (`oc.sessionDrafts`).
- **Tests:** `prompt_photos_test.dart` (13 catalog rows), `draft_attachments_test.dart` (5), `saved_prompts_photo_recovery_test.dart` (5), `stash_attachments_test.dart` (23).
- **Rewrite note.** Keep the idea (record before launch, recover into the recorded owner) but note the native Photo Picker/`ActivityResultContracts` return results to the Activity, which can also be recreated; persist the pending record the same way. Consider storing blobs as raw bytes with a sidecar (the data-URL-as-text format costs 33% extra disk and a full string in memory) but then the migration must read the old format (Section 10).

#### MEDIA-VOICE-28  File attach from the system picker

- "File" opens the system document picker (`FilePicker.pickFile`, dialog title "Attach to prompt"). Checks in order: count < **5**; aggregate so far < **20 MiB**; picked size ≤ **10 MiB** (`_maxAttachmentBytes`); size + so-far ≤ 20 MiB; then a **bounded stream read** that keeps at most `limit + 1` bytes so a file that grew after the picker reported its size still cannot allocate without bound (`readAttachmentBytesWithinLimit`, 8 catalog rows in `chat_attachment_reader_test.dart`); errors are distinct for "each attachment must be 10 MB or less" and "attachments must total no more than 20 MB".
- **MIME gate** (`promptAttachmentMime`, `lib/domain/prompt_attachment.dart`): declared media types png/jpeg/jpg/gif/webp/pdf are accepted and normalized; declared `text/*`, json, javascript, xml, yaml, toml, sh, sql, graphql, ndjson, `+json`, `+xml` become exactly `text/plain` **only if the bytes look like text** (no NUL, valid UTF-8, no control characters other than tab/LF/VT/FF/CR) because OpenCode expands exact `text/plain` data URLs into text instead of sending an arbitrary file part; other `image/*` declared types are refused; otherwise the extension decides (png jpg jpeg gif webp pdf; md markdown txt log html htm css csv json xml yaml yml toml dart js ts tsx jsx py go rs java kt kts c cc cpp h hpp sh sql → text/plain with the same text check); an unknown binary with no/generic declared type is accepted as text only if it looks like text; else `chatAttachmentUnsupported` ("Images, PDFs, text files, and Excel or Word files (.xlsx, .docx) can be attached.").
- **Representation:** `PromptAttachment {mime, filename, url: data:<mime>;base64,…}`; byte length computed from the URL.
- **Key files:** `chat/chat_attachments.dart` (`_chooseAttachment`), `lib/domain/prompt_attachment.dart`.

#### MEDIA-VOICE-29  Excel and Word files attached as the text they contain

(Commit `39213ee57`, 2026-09-21; `lib/domain/office_text.dart`, 269 lines; `office_text_test.dart`.) `.xlsx`, `.xlsm`, `.docx` are opened **on the phone** by a small hand-written ZIP reader (reads the central directory so entries written with data descriptors — as Excel does — work; inflate via `ZLibDecoder`) plus the `xml` package: every sheet becomes CSV under its name, a document its paragraphs, with a first line saying where it came from and that formatting, charts and formula definitions are not included; cut at **400,000** characters with a truncated note; run off the UI thread (`compute`). Result attaches as `text/plain` named `<file>.csv` or `<file>.txt`. The composer notes "2 sheets, 6 rows" or "document attached". Unreadable (password-protected, damaged, the older binary `.xls`/`.doc`) says what to do; empty says so. No new dependency. Proven against openpyxl and LibreOffice workbooks. Rewrite: pure Kotlin common code (`java.util.zip` is JVM-only: use `okio`/own inflate for iOS later) + an XML pull parser (xmlutil or kotlinx-io + a small parser).

#### MEDIA-VOICE-30  Images committed by the keyboard (paste, GIF, stickers)

The composer field accepts `KeyboardInsertedContent` (Android `InputConnection.commitContent`): the keyboard's GIF/sticker search and the **clipboard-image paste chip** arrive here (`_handleInsertedContent`). This is the only zero-plugin image-paste path on Android (the framework's `Clipboard` service reads `text/plain` only, so a manual "Paste image" menu entry cannot read image bytes). Content without inline bytes (URI-only commit) is ignored; mime defaults to `image/png`; filename `pasted-image-<epochMs>.<jpg|gif|webp|bmp|png>`; goes through the same `_addPreviewAttachment` limits and MIME gate. Wired as `contentInsertionConfiguration` on the field (`kit_field.dart:777`); `onContentInserted` is null when isolated or attachments unsupported. Rewrite: Compose `Modifier.contentReceiver` / `ReceiveContentListener` (Android; check Compose Multiplatform support per target [web: not confirmed]) or an `OnReceiveContentListener` on the underlying `EditText`; declare `android:imeOptions`/MIME types equivalent to the current config.

#### MEDIA-VOICE-31  Files dropped onto the composer (desktop)

`DesktopFileDropTarget` (`lib/ui/desktop/file_drop.dart`, uses `desktop_drop`): returns its child untouched off desktop; on desktop shows a drop highlight, passes `DroppedFile {name, mimeType, length(), readBytes()}` to `_handleDroppedFiles`, which checks the **size from the drop's metadata first** (oversize is refused without reading it) and goes through the same `_addPreviewAttachment` pipeline; a failure shows a generic alert `desktopDropFailedTitle`/`Recovery` (never exception text; no automatic retry because some files may already be added). Rewrite: Compose Multiplatform desktop `dragAndDropTarget` (`onExternalDrag` replaced); Linux desktop is a day-after target.

#### MEDIA-VOICE-32  Attachment chips, thumbnails and notes

- Composer chips (`KitComposerChips.attachments`): image attachments show a decoded thumbnail (cache `_thumbnailCache` Expando keyed by the attachment object, decoded once, only for `image/*` data URLs); other files a file chip; each has Remove; review references and project-file references (`PromptAttachment.directoryReferenceMime`, `@name` mention) share the strip; the pill's one muted line says what attachments do ("Attachments save with this draft on this device.", `draftAttachmentsLocal`) — shown **once per session** (`_attachmentNoteShownSessions` static set), not on every attachment; for a text-only agent it says `codexTextOnlyPrompt` instead.
- Sent photos in the transcript (`sent_photos.dart`, FC4, commit `1d811eb14`): a sent photo is a thumbnail under the prompt that opens the gallery. A photo comes back in one of two forms: inline `data:` URI (OpenCode 1; OpenCode 2 after it **re-encodes** the image; the app's own kept copy for an agent on the phone whose server keeps the text only) or a server file `file:///abs/path` (OpenCode 2) read through the app's own file transport (`_SentFileImage`, an `ImageProvider` keyed by (profile id, path) so two thumbnails share one cached image). A **web address is never fetched** (named chip). Drawable = `image/*` except SVG.
- Pictures in a reply's Markdown (`![alt](target)`): `file` kind → thumbnail read through the host's file transport (cap **8 MiB**, `_maxPathImageBytes`), `web` → chip with the host, tap goes through `openExternalLink`; `inert` (data:, other schemes) → chip not openable; content-hash file names (Paseo screenshots) are not shown as names.
- Keys: `sent-photo-<id>`, `sent-photo-open-<id>`.

#### MEDIA-VOICE-33  What each agent can take

`capabilities.promptAttachments` (false → text-only note `codexTextOnlyPrompt`), `promptImagesOnly` (Paseo/Claude Code: only pictures; tools sheet shows Photos and Camera and "Attach file" says "this agent takes pictures only"; the gateway maps data-URL pictures to Paseo `images` on create and send; other files are refused in words; commit `35e5553b1`, proven on the emulator: Claude answered "Red" about a red circle), `promptEchoTextOnly` (Paseo's timeline keeps text only, so the sent copy is matched by text and keeps its pictures until reload), `fileBrowsing` (project-file references need it). The protocol mapping lives in the `protocols`/`agents-backends` areas; this area owns the UI gating and limits.

#### MEDIA-VOICE-34  Photo asks from agent cards (generative UI)

`AgentCardPhotos` (`lib/ui/widgets/agent_card_photos.dart`): a card can ask for pictures; `StoreAgentCardPhotos` uses the same `PromptPhotoStore` (so a photo that outlives the app finds its way back to that conversation's draft), same limits (10 MB each, 20 MB together, picker downscaling), `choose(limit)` gallery / `take()` camera (takes the pending photo, reads, discards), errors in words (`agentCardPhotoError`). A card without a provider says photos cannot be added from there. Commits `e6ded6ac5`, `393beaa29`, `06ebb6503` (S1 is photo-only; file/voice to S2).

#### MEDIA-VOICE-35  Attaching from viewers, tool output and the project

- File viewer "More" › **"Attach to prompt"** (key `file-preview-attach`/`project-file-attach`) uploads the file's contents with the message; named apart from "Add to prompt" (a reference).
- Tool output files (`ToolOutputFile {path, url, mimeType, filename}`): images open the **gallery**; others open the preview sheet with Attach/Save.
- Project file reference: "@path" mention + `PromptAttachment.reference(name, path)` (`directoryReferenceMime`); duplicates are refused with a note; references fold into the prompt text as structured markdown just before send (`_applyStagedReferences`), then the chips clear.
- Web sources: "+ › More › Web sources" opens `WebSourcesScreen`, and appends a JSON appendix block to the draft (guarded: aborted if the draft or scope changed meanwhile).
- Prompt editor (full-screen, `fullscreenDialog`): edits text and attachments using the same `_chooseAttachment`.

#### MEDIA-VOICE-36  Text shared into the app, and text shared out

- **Share in.** Manifest filter `ACTION_SEND` + `text/plain`. `LaunchShareChannel.capture` accepts text shares only (`intent.type` starts with `text/`), builds `subject\nbody` (just one when the other is empty or the body already starts with the subject), **consumes the share** (`action = MAIN`, removes extras) so a configuration change does not replay it. Delivery waits for Dart readiness: Dart installs its inbound handler **before** calling `consumeSharedText` (`dartReady=true`, returns and clears `pending`); live shares afterwards are pushed with `invokeMethod("shared", text)`. Dart `ShareIntent.pending` holds the text until a connected shell turns it into a new session; a live share that arrives during the cold-start consume wins over the stale one (`_acceptGeneration`). Images/files shared **into** the app are not supported (no `image/*` filter).
- **Share out.** `ShareOut.text(text, subject)` → `shareText` opens the chooser with `EXTRA_EXCLUDE_COMPONENTS = MainActivity` (so the app never offers itself: it would start a session with its own report); returns false on `ActivityNotFoundException`; callers hide the action when unsupported (STATE-13: never a dead button).
- Tests: `share_intent_test.dart` (11 rows), `share_intent_native_contract_test.dart`, `share_routing_test.dart`.

### 2.4 Viewing, saving, sharing and scanning, features 37-50

#### MEDIA-VOICE-37  KitViewer: the one viewer

(`lib/ui/kit/kit_viewer.dart` 745 + `_content` 257 + `_build` 545 + `_bodies` 463 lines; spec `docs/ux-system/kit-api/KitViewer.md`, kit-v2 §1.14, §8.2; tests `test/kit/kit_viewer_test.dart`, `kit_viewer_r1_test.dart`.)

- **Frame (always the same).** Name (file-name style, never squeezed), muted path (`path`, with `:line` when opened at a line), **one labelled primary action** at the bottom where the thumb is (`primary`, e.g. "Ask about this file", "Attach to prompt"), an optional **secondary** (e.g. "Share on Wi-Fi"), **More (⋯)** overflow menu (`more`), **Close**. Reader mode (`reader: true`): the primary is the solid main button, **Copy** sits beside it for text (Copy contents moves into ⋯ at ≥ 1.6x text scale so the bar holds the main action alone), the secondary is an expanded secondary button.
- **Presentation.** `asPage: null` picks by window: a **full-height bottom sheet** (`KitLayout.sheetFullHeight` = 0.95 of the window) on compact windows (handle, rounded top, scrim, `KitMotion.standard` with enter/exit curves, no animation under reduced motion), the same sheet capped at `KitLayout.sheetMaxWidth` (640 dp) on medium, a **pushed full-screen dialog page** (Close at the end) from expanded up. The sheet holds the viewer's own frame because the generic sheet frame has no slot for action+More and its body cannot hold a virtualised file.
- **Body kinds** (`KitViewerKind`): `text`, `code` (line numbers, syntax colour from `highlight`, scroll-sideways by default, `initialLine` scrolled to and marked), `markdown` (reflowed prose on the reading rails `KitLayout.readingWidth` (720 dp); "Show source" swaps to text), `image` (pinch/pan `KitZoom`), `pdf` (stacked pages, each rendered only while on screen, `scrollCacheExtent: 0`, page width = min(available − 2·space3, readingWidth), zoom controls, per-page loading/error with Try again, page caption), `svg` (sanitised, Image/Source), `delimited` (table, header row, scroll both ways, Table/Source), `binary` ("Can't show this file", type and size in words like "2.4 MB", the caller's action stays).
- **Tools.** In ⋯ More: **Find** (Ctrl+F on hardware keyboards; inline find bar: field `kit-viewer-find`, previous/next/close, match count `kit-viewer-find-count`, marks drawn in the text with an active mark), **Copy contents** (verbatim, not redacted), **Wrap lines** (checked item; in reader mode it is a header toggle button instead), **Show source** (checked item, markdown/svg/csv), then the host items. A right-click / secondary tap on the body opens the same menu (desktop). Esc closes the viewer or the find bar.
- **States:** loading (six skeleton rows), error with Try again (plain title "Could not load <name>", redacted details under a fold) (`KitViewerSource.load(() async => KitViewerContent)` re-runs the loader), empty, content. `KitViewerSource(content)` is the instant form. (`KitViewerSource.load` is the retryable async form.)
- **Truncation:** text over 200,000 characters is cut without splitting a surrogate pair and the viewer says "truncated"; Copy/Save keep the whole file when the caller supplies `original`.
- **Native rewrite:** Compose screen with `LazyColumn` for code (virtualised lines), `Modifier.transformable` or `zoomable` for images, own find index over a `String`. Syntax highlighting: a KMP highlighter (e.g. `highlights` by SnipMeow) or port `highlight`'s language set (dart js ts tsx jsx py go rs java kt swift c cpp cs shell html css scss xml yaml toml sql gradle diff json). Markdown: `multiplatform-markdown-renderer`-class library, but the app has agent block types (choices, checklist…) that belong to `chat-genui`.

#### MEDIA-VOICE-38  File preview adapters (`FilePreviewData`)

(`lib/ui/widgets/file_preview.dart`, 880 lines; tests `common_file_preview_test.dart`.) `FilePreviewData {name, mimeType, bytes, text, originalText, truncated, error}` normalizes content for the viewer:
- MIME: lowercase, parameters stripped; falls back to the **extension map** (png jpg jpeg gif webp bmp svg pdf json csv tsv xml md txt log dart js ts tsx jsx py go rs yaml yml → types).
- Text decode is **strict UTF-8** for text-ish MIMEs; a NUL or invalid UTF-8 means *no text* (never replacement characters), the original bytes stay for Save.
- Flags: `isRasterImage` (png jpeg gif webp bmp), `isPdf` (bytes non-empty), `isMarkdown` (type, `.md`/`.mdx`, or text with headings/fences/tables), `prettyJson` (two-space indent, only when not truncated), `language` (extension → highlight language), `separator` (csv/tsv by MIME or extension), `exportBytes` (bytes, else UTF-8 of the original text).
- `FilePreviewData.fromDataUrl(name, mime, url)`: errors in words for missing content ("The attachment content is not included in this message."), remote URLs ("Remote attachment previews are not available."), undecodable data.
- Entry points: `showFilePreviewSheet(context, data, {onAttach, onDownload, path, initialLine, onOpenInFiles, onOpenInReview})` and `showFilePreviewSheetLoading(context, name, load, …)` (opens at once with the name, loading, failed fetch says so with Try again). More menu items: Copy original (`file-preview-copy-original`), Save to device (`file-preview-download`), Copy path (`file-preview-copy-path`), Open in Files (`file-preview-open-files`), Open in Review (`file-preview-open-review`); primary Attach to prompt (`file-preview-attach`); Rendered/Source segmented (`file-preview-rendered-mode`/`file-preview-raw-mode`), Wrap (`file-preview-wrap`). A line outside the file's range shows the notice `fileLineOutsidePreview(n)` instead of marking. `FilePreviewBody` (stateful) is the embedded form used inside tool cards (with "open full").
- **Isolated previews** (`MarkdownInteractionScope` disabled — the demo/preview scope) never render PDFs ("PDF pages do not render here").
- Limit constant: `filePreviewMaxSourceChars = 200000`.

#### MEDIA-VOICE-39  Files screen viewer (project files)

(`lib/ui/screens/files/files_viewer.dart`, 377 lines; `files_screen.dart` 539.) The project file browser's tap on a file opens `showKitViewer(reader: true)` with:
- **Primary: "Ask about this file"** (`project-file-ask`): from a chat it stages the file as a reference (with the line when one was opened) and returns to that composer; from anywhere else it starts a new chat in this project with `@path ` in the field, unsent.
- **Secondary: "Share on Wi-Fi"** (`project-file-share-wifi`; absent where the gateway is unsupported, i.e. a browser) → feature 45.
- **More:** Attach to prompt (`project-file-attach`, when opened from a chat; uploads contents; returns to the tree with "Attached"), Save (`project-file-download`), Reload (embedded only), Open review (when the file has a change; `project-file-review`), Copy path.
- **Fetch scoping.** Every read is checked against the profile id + location revision + transport identity captured at the start; if the scope changed (server or project switched) the result is dropped and the viewer says `filesViewerScopeChanged`. Content caches per `_ViewerSession` so a reload or share does not refetch.
- **Content mapping** (`_viewerContent`): raster image → image; text (cut at 200,000 chars) → delimited table (csv/tsv, when no line was requested), SVG sanitised, markdown (`.md`/`.markdown`), code (extension in code/data icon sets, or a line was requested), else text; PDF → `LocalPdf` first page (count) then lazily per page; anything else → binary (type, size). `initialLine` forces code view so the line can be marked.
- Wrap preference saved via `ReaderPreferencesScope` (feature 49).

#### MEDIA-VOICE-40  Local PDF rendering (isolated process)

(Dart `lib/platform/local_pdf.dart` 95 lines, `lib/ui/widgets/pdf_file_preview.dart` 227; Kotlin `LocalPdfBridge` 198 + `LocalPdfService` ~90; doc `docs/common-file-previews.md`; tests `local_pdf_test.dart`.)
- **Limits:** input ≤ **10 MiB**, first **200** pages, one request at a time (`busy` error otherwise), page raster longest edge ≤ **1,536 px** and ≤ **2,000,000 px²**, PNG ≤ 10 MiB, Android 10 / API 29+ (`unavailable` below; the viewer then shows "Can't show this file" with Save).
- **Architecture.** App side `LocalPdfBridge` creates two private temp files in `cacheDir`, opens read/write `ParcelFileDescriptor`s on each, **unlinks the paths before writing any bytes** (only descriptors survive, even on a crash), writes the PDF on an IO executor, binds a **fresh isolated service instance per request** (`bindIsolatedService`, unique instance name `pdf<uuid>`) and sends the two descriptors plus the page number over a `Messenger`; the reply carries only page count/width/height (PNG bytes never travel in a Binder bundle — they are read back from the output descriptor, size-checked, ≤ 10 MiB). **`LocalPdfService`** is `android:isolatedProcess="true"` `android:process=":local_pdf"` non-exported, no permissions: uses `PdfRenderer`, scales by `min(1536/max(w,h), sqrt(2e6/(w·h)))`, white background, `RENDER_MODE_FOR_DISPLAY`, PNG compress through a size-limiting stream; a **10 s watchdog** kills only its own process; `onUnbind`/`onDestroy` kill the process. Client deadline **12 s**. Error codes: `encrypted` (SecurityException), `invalid_pdf`, `timeout`, `io_error`, `service_died`, `unavailable`, `limit`, `invalid_result`, `busy`, `cancelled`.
- **Dart side validation:** page index and size limits again; the result's `png`, `pageCount`, width/height must be sane and `value['page'] == page`; **the PNG signature + IHDR width/height are checked** against the reported ones before the image codec allocates.
- **Page manager** `LocalPdfPages`: serialized queue (one platform request at a time), a page scrolled away before its turn never reaches the platform, all requests cancelled when the app leaves the foreground (viewer then offers Try again per page; background return requires explicit Retry), first page cached, dispose retires request identities so a late reply cannot install an old page.
- **Failure words** (`localPdfMessage`): encrypted ("requires a password or uses unsupported protection. Save the original…"), limit, unavailable ("PDF viewing is available on Android 10 or newer…"), cancelled, failed.
- **Native rewrite.** Keep the **isolated-process** model (it is the security answer to untrusted PDFs). Kotlin can keep `LocalPdfService` as is. Compare `androidx.pdf` (Jetpack PDF; 1.0.0-beta01 dated 2026-08-26 per [web], needs AGP 9.2 for Compose, minSdk backport to 28) — it parses in-process; the owner's design explicitly chose isolation, so decide deliberately. On iOS/desktop later: PDFKit / PDFBox-or-pdfium behind the same `PdfPageRenderer` seam.

#### MEDIA-VOICE-41  SVG, CSV/TSV and other renderers with their limits

- **CSV/TSV** (`lib/domain/delimited_text.dart`, 105 lines; `delimited_file_preview.dart`): UTF-8; ≤ **256 KiB** input, **200** displayed rows, **32** columns, **4,096** chars per cell; quoted separators, doubled quotes and multiline cells supported; ragged rows stay ragged; no guessed header, no total count; malformed or oversized input shows Source; the row limit shows an explicit prefix notice ("showing the first 200 rows"). Recognition by explicit matching MIME or extension when MIME is missing/generic; a contradictory explicit MIME does not become a table. Table direction stays LTR for data while surrounding controls follow RTL. Tests: `delimited_text_test.dart` (6 rows).
- **SVG** (`lib/domain/static_svg.dart`, 259 lines; `svg_file_preview.dart`; rendered by `flutter_svg` `SvgPicture.string` only after validation with `xml`): ≤ **256 KiB**, ≤ **1,000** elements, depth ≤ **32**, attribute length ≤ **16,384**, finite bounded geometry, dimensions ≤ **16,384**; allowlist of elements/attributes (paths, shapes, groups, text, local gradients); **rejected**: script, animation, external resources, `<image>`, `<use>`, stylesheets, clip/mask, dashed strokes, foreign content. Source/Save keep the unchanged original; a truncated SVG is never interpreted as complete. Tests: `static_svg_test.dart`. Rewrite: Coil's SVG decoder is NOT a safe substitute unless fed only the validated output; keep the validator in common Kotlin and render with a vector-path renderer you control (or Android `VectorDrawable` conversion / Skia SVG DOM in Compose Multiplatform).
- **Markdown** documents render through the shared Markdown renderer (feature 37); **code** through `highlight`; **JSON** pretty-printed. Binary shows type and size.
- **Surrogate pairs:** every truncation helper avoids splitting a pair.

#### MEDIA-VOICE-42  Image gallery, pinch-zoom and send-to-composer

(`kit_image_gallery.dart` 391, `kit_image.dart` 1,433 incl. `KitZoom`, `chat/chat_gallery.dart`; commits `7d7c8ae0f`, `0d548a486` 2026-10-11; tests `kit_image_gallery_test.dart`, `kit_image_test.dart`.)
- **What it does.** Tapping any picture in a chat (a sent photo thumbnail, a Markdown picture the host can read, a tool card image) opens a **full-screen swipeable gallery of every picture in the transcript in transcript order**, starting at the tapped one with a "2 of 5" label. Sources: `part:<messageId>:<index>` (file parts with drawable mime: inline data bytes or a server `file://` path), `path:<path>` (Markdown pictures with `kind == file` and drawable; skips paths already known missing), `tool:<identity>` (tool output image files: data-URL or fetched through `_loadToolOutputFile`). Reading goes through the existing loaders; **only the shown picture and its neighbours load**; an unreadable one shows the gallery's own plain error page with Try again; opens at once (does not wait on file checks).
- **Gestures.** Pinch/pan per picture (`KitZoom`: scale 0.2-5×, zoom-step buttons zoom out/reset ("Fit to screen")/zoom in for accessibility (A11Y-5), keyboard zoom, reset key changes view); **a swipe changes picture only when the shown one is not zoomed in**.
- **Edit** opens the markup editor (feature 43); **Send** puts the (edited or original) picture in the composer's attachments via `onSend` (edited → PNG, name's extension replaced with `.png`, "…-edited.png"), closes the gallery, focuses the field and says "<name> attached"; it **never sends the prompt**; not offered when the agent cannot take pictures (`_supportsPromptAttachments`).
- **Decode safety (`KitImage`):** decode target = box size × dpr (`ResizeImage` fit/cover, never upscales); unconstrained sources capped at 4,096 px on the longer side; cross-fade on load; failure state shows icon (words from 120 dp wide).
- **Isolated** chats cannot open it.

#### MEDIA-VOICE-43  Image markup editor

(`kit_image_markup.dart` 733; tests `kit_image_markup_test.dart`.) Full-screen editor opened by Edit: **finger pen in four inks** (red, yellow, white, black — `MarkupInk`, the one place red is allowed), pen width 4 dp on screen (scaled to image pixels), **Undo**, **Clear** (strokes and crop), and a **crop rectangle with corner handles** (minimum 48 dp, 24 dp handle room, drag a corner or move the rectangle, Apply/Cancel). One pointer draws at a time; drawing is blocked while cropping or busy. Result is rendered **at the picture's own resolution** (not the screen's) to PNG; if over **9 MiB** (`kitMarkupMaxBytes`, under the 10 MB composer cap) it is scaled down until it fits. `KitMarkupResult {bytes, send, changed}`; `changed:false` returns the original bytes untouched; "Send" vs "Done" (Send hidden when `canSend` false). States: loading (decode via `instantiateImageCodec`), error, working. Native rewrite: Compose `Canvas` + `drawIntoCanvas` for the export bitmap (`ImageBitmap`) — the export must replay strokes at full resolution.

#### MEDIA-VOICE-44  Save to device and Copy

- **Save** everywhere (viewer More, tool output): `FilePicker.saveFile(dialogTitle: "Save <name>", fileName, bytes)` — the system "create document" dialog; returns the saved path or null (cancel); success note "Saved <name> on this device". Bytes are the file's original bytes (or its text as UTF-8). Rewrite: `ActivityResultContracts.CreateDocument(mime)` and stream the bytes into the returned `Uri` (no path is returned on Android; word the confirmation by name).
- **Copy original** keeps exact original spacing/line endings (`KitCopy.copy(..., redact: false)`; the normal Copy of chat text redacts secrets); a failed copy offers Retry.
- Other `FilePicker.saveFile`/`pickFile` callers outside this area: session export/import, chat session menu — owned by the chat/sessions areas.

#### MEDIA-VOICE-45  Share a file on the local Wi-Fi (QR or link)

(Commit `1be94da76` 2026-11-01, merged `285ba13b3`; `lib/platform/wifi_file_share.dart` 262 lines, `lib/domain/file_share_gateway.dart`, `lib/ui/widgets/file_share_sheet.dart` 171; tests `wifi_file_share_test.dart` (4 rows). **Not in the Android channel list: it is pure Dart `dart:io` `HttpServer`.**)
- **What the person sees.** Open a file in the project viewer → "Share on Wi-Fi" → a sheet "Share on Wi-Fi" with a **QR code** of the URL (`KitQr`, up to `qrMaxSize`, spoken label "QR code to download <name>"), the warning "Anyone on this Wi-Fi with the link can download this file.", the **link** in a copyable code block ("Copy link"), "Downloaded N times" (plural; 0 → "Not downloaded yet"), "Stops sharing in m:ss" (ticks each second), and **Stop**. Failure notices: no Wi-Fi ("This phone is not on Wi-Fi. Connect to Wi-Fi to share this file with a nearby device."), private file ("The app keeps this file private…"), unavailable ("Sharing could not start. Try again in a moment."), ended ("Sharing has stopped. Nobody can download this file now."). Keys `file-share-sheet`, `file-share-qr`, `file-share-link`, `file-share-copy`, `file-share-downloads`, `file-share-time`, `file-share-stop`, `file-share-live`, `file-share-ended`, `file-share-failure`.
- **Server rules (security shape).** One share at a time (`start` stops the previous). Binds **only** to the phone's Wi-Fi/hotspot IPv4 address, never `0.0.0.0`, never mobile data: interface name starts with `wlan`, `swlan` or `ap` and the address is private LAN (10/8, 172.16/12, 192.168/16); ephemeral port. URL `http://<ip>:<port>/<token>/<name>` where token = 24 random bytes from `Random.secure` base64url without padding (192-bit); every other path → 404; only GET and HEAD (others 405 with `Allow: GET, HEAD`); token compared in constant time; headers `Content-Type` (given or `application/octet-stream`), `Content-Disposition: attachment; filename="<ascii fallback>"; filename*=UTF-8''<pct>`, `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`, exact `Content-Length`; body **streamed** (64 KiB slices from bytes, or `File.openRead`); download count increments only after a full stream. File names: base name only, control chars and `"` replaced by `_`, empty → `file`.
- **Lifetime.** 15 minutes (`lifetime`), then stops; also on Stop, on the sheet closing (`finally` in `showFileShareSheet`), on a new share, and when the app is **detached**. It is **not** stopped when merely backgrounded (no service keeps it alive; Android may freeze the process).
- **Private files.** `fileSource(path)` refuses anything under `getApplicationSupportDirectory` / `getApplicationDocumentsDirectory` (resolves symlinks) with `privateFile`. The viewer shares a project file's **already fetched bytes** (`FileShareSource.bytes`), so the check is for path-based callers.
- **Rewrite.** Ktor server (CIO engine works on Android; JVM-only, so an `expect`/`actual` `LocalFileServer` seam: Android+desktop JVM now, iOS later via its own server). Keep every rule above. **Android 17 / target API 37 local-network permission:** apps targeting 37 must declare/request `ACCESS_LOCAL_NETWORK` for LAN connections [web]; whether an *inbound* listener on the LAN needs it was not stated in the sources found — verify on an API 37 emulator before shipping. The same permission affects the app's LAN server connections (protocols/net areas).

#### MEDIA-VOICE-46  Scan a pairing QR with the camera

(`lib/ui/screens/pairing_scanner_screen.dart` 250, `lib/ui/kit/kit_scanner.dart` 558, `lib/state/pairing.dart`, `lib/platform/camera.dart`; commits `62f85faf7` (2026-08-30), `734c8e921`, `57e1e58cd` (2026-09-27); tests `pairing_scanner_test.dart` (6 rows), `pairing_payload_test.dart`, `kit_scanner_test.dart`.)
- **Entry.** Server profile editor → "Scan pairing QR" (shown only when `supportsQrPairing`), with "Paste the code instead" always available (clipboard path uses the same parser). The screen pops with a `PairingPayload` on success and `null` on **every** other exit.
- **Stages** (`_ScanStage`): starting (progress; after 8 s slow with "Paste it instead"), scanning, denied ("Allow camera" retry), permanentlyDenied ("Open app settings", **no** retry button because Android will not ask again), noCamera (`hasCamera` false: tablets/emulators), failed ("Camera failed" + device message under Details + Retry). Every non-scanning state offers "Paste it instead", so no dead end is a real dead end.
- **KitScanner** (camera frame): live preview with a square window (`KitTokens.scannerWindow` 240 dp), dim outside, four corner brackets, one instruction line ("Point the camera at the QR code printed by opencode2 pair.") and an in-place rejected line ("that QR is not a pairing code") while scanning continues (the likely cause is the wrong QR). Camera: back, **QR only**, `DetectionSpeed.noDuplicates` (each code once per sighting), `autoStart:false`. Once `onCode` returns true it **latches**, stops the camera before the host pops and ignores queued values. Stops while the app is inactive/hidden/paused and restarts on resume; stops on dispose; start attempts counted so a late start after a pause is ignored; window never below 160 dp (text block scrolls at 200% text); short windows lay out in a row.
- **Never keeps the value.** The decoded text is not stored, rendered, put in semantics or logged (SEC-2): any QR in the world can be pointed at the screen, but the one we want carries the serve password. `PairingPayload.toString` redacts; the password is held behind `consume()`.
- **Parser** (`parsePairingPayload`): JSON `{urls:[…], username:"opencode", password}` printed by `opencode2 pair` — ≤ **8,192** chars, ≤ **32** urls, each ≤ 2,048, deduped; `username` defaults to `opencode`; failures are product sentences that never quote the input. (Failure *strings* are mapped to localized copy by exact match in `setupUiMessage` — typed errors in the rewrite.)
- **Camera permission** is the three-state native answer (feature 48). Desktop: no scan affordance at all.
- **Rewrite.** CameraX `ImageAnalysis` + a FOSS decoder (ZXing core or zxing-cpp; owner decision 2026-10-31 to drop ML Kit, ~3 MB per ABI, for the F-Droid build). KMP: no confirmed KMP wrapper for ZXing/CameraX [web]; third-party KMP scanners exist (`dev.code93` using ML Kit/Vision, MIT, updated 2026-07-14) but use ML Kit on Android, which conflicts with the owner decision. Plan an `expect class QrScanner` with Android CameraX + ZXing, iOS AVFoundation later, no desktop affordance.

#### MEDIA-VOICE-47  Showing a QR code (`KitQr`)

(`lib/ui/kit/kit_qr.dart` 200 lines; `qr` 3.0.2 encoder; used by Wi-Fi share and the session-handoff sheets `session_handoff_sheets.dart`; tests `kit_qr_test.dart`.) Dark modules on a **light card with a four-module quiet zone whatever the theme** (a scanner reads contrast, not palette); error correction fixed at **M**; each module snapped to whole physical pixels (side = floor(ideal·dpr / totalModules) modules·dpr — LOOK-21); width ≤ `KitTokens.qrMaxSize` (240 dp) and ≤ the given width, square, no `size:` parameter; data never shown as text, logged or put in semantics (only the host's `semanticsLabel`); data too long (`InputTooLongException`) shows the notice "This is too long for a QR code. Copy the link instead."; `KitQr.fits(data)` lets a host decide first. Rewrite: `qrcode-kotlin` (KMP) or ZXing `QRCodeWriter` for the matrix + own Canvas painter (keep pixel snapping).

#### MEDIA-VOICE-48  Camera platform seam (`oc/camera`)

`CameraPlatform` (`lib/platform/camera.dart`): `hasCamera()` (`PackageManager.FEATURE_CAMERA_ANY`; Android with no handler = false, never claim a camera that cannot open), `requestCameraPermission()` → `granted | denied | permanentlyDenied` (computed natively from `checkSelfPermission` + `camera_permissions` prefs flag `camera_requested` + `shouldShowRequestPermissionRationale`; a second concurrent request → error `permission_in_progress`), `openAppSettings()` (`ACTION_APPLICATION_DETAILS_SETTINGS`, `package:<id>`). Reason it exists: mobile_scanner reports only "denied", but Android's retryable and permanent denial need opposite recoveries. Replaceable in tests via `cameraPlatform = …`. Gate: `supportsQrPairing` (Android only). The same three-state pattern is used for the microphone (feature 15) — share one `PermissionGate` abstraction in the rewrite.

#### MEDIA-VOICE-49  Reader preferences (wrap, source-first)

`ReaderPreferencesStore` (`lib/state/reader_preferences.dart`): per-profile JSON `{sourceFirst?, wrapCode?}` at `oc.readerPreferences.<profileId>`; `ReaderPreferencesScope` provides it; `saveReaderPreferences(context, wrapCode:)` from viewers; the viewer's default wrap is `KitCodeBlock.defaultWrap` (code scrolls sideways on every window so a long line is never broken mid-identifier; text/markdown source/other wrap on a compact window).

#### MEDIA-VOICE-50  Files a turn changed and generated files

- **Rows under a finished turn** (`chat_files.dart` `_turnChangedFiles`, concept 9, owner 2026-10-20): the turn's edited files (`RunResult.editedFileStats`, at most `_maxTurnFileRows` shown, "+N more") as flat rows `chat-turn-file-<path>` with `+added` / `−removed` counts, tapping opens `showFilePreviewSheetLoading` (loads through `_loadToolOutputFile`); a quiet "All files" link to the project Files page when the project can browse (not a temporary directory). Absent for isolated chats, watched team workers, or turns with no edits.
- **Tool output files** (`ToolOutputFile`): fetched with the connection's action transport (`api.fileContent(path)`); base64/binary → bytes (empty bytes → "The server returned empty image data."); text → text; when the file is not available: "The generated file is not available from this server." Attach (`_attachToolOutputFile`) and Save (`_downloadToolOutputFile`, `FilePicker.saveFile`) are offered from the viewer.
- **Path links** in replies are validated against the directory listing (`_checkPathLink`, listing cached with a negative TTL; failures forget the cached future so a later rebuild can retry). Owned by chat; listed because pictures/links feed the gallery and viewer.

---

## 3. Platform gates (what turns each feature on)

All through `platformCapabilities` (`lib/platform/platform_capabilities.dart`), never `Platform.isAndroid`. The rewrite keeps one capability object per target; Android is the full set.

| Flag | Meaning | Features gated |
|---|---|---|
| `supportsVoice` (Android) | `oc/voice`, microphone, on-device Whisper | 01-18, mic button, "Voice input" row, Settings › Voice typing, voice component in phone setup |
| `supportsReadAloud` (Android) | `oc/read-aloud` system TTS | 19-24, "Read reply prose", natural voice section |
| `supportsVoiceConversation` = voice **and** read aloud | hands-free loop | 04 |
| `supportsLocalPdf` (Android; native needs API 29+) | `oc/local_pdf` | 40 |
| `supportsPromptPhotos` (Android) | library + camera tray buttons | 26, 34 |
| `supportsQrPairing` (Android) | `oc/camera` + scanner | 46, 48 |
| `ShareIntent.supported` (Android, not web) | text share in/out | 36 |
| `FileShareGateway.supported` (`!kIsWeb`) | Wi-Fi share | 45 |
| desktop (`isDesktop`) | file drop | 31 |
| server/agent capabilities | `promptAttachments`, `promptImagesOnly`, `promptEchoTextOnly`, `fileBrowsing`, `webSearch` | 25, 28, 33, 35 |

Off Android, voice reports `VoiceDeviceInfo.unsupported()` (no capture path) and **refuses** the permission request (`permanentlyDenied`) instead of claiming a grant (commit `883a5eb69`, 2026-08-30: a desktop user had been able to walk into model download and record into Android-shaped paths).

---

## 4. Native bridges (exact contracts)

All channels are Flutter `MethodChannel`s today. The rewrite replaces them with direct Kotlin calls behind the seams in Section 13, but the **behaviour** below must be kept.

### 4.1 `oc/voice` — `MainActivityMedia` + `VoiceDownloadNotifications`

| Method | Arguments | Result / errors | Notes |
|---|---|---|---|
| `getDeviceInfo` | none | `{availableStorageBytes: StatFs(filesDir).availableBytes, memoryClassMb, totalMemoryMb: MemoryInfo.totalMem / 1 MiB, lowRamDevice, supportedAbis: Build.SUPPORTED_ABIS, hasMicrophone: FEATURE_MICROPHONE}` | Dart timeout 5 s → `VoiceDeviceInfo.unknown()` |
| `requestMicrophonePermission` | none | `"granted" \| "denied" \| "permanentlyDenied"`; error `voice_unavailable` (playback could not be stopped), `permission_in_progress` | Calls `beforeMicrophoneCapture()` first and again on grant. Request code 4702. Adds `POST_NOTIFICATIONS` to the request on API 33+ when not granted. Prefs file `voice_permissions` flag `microphone_requested`: asked before AND no rationale to show → answers `permanentlyDenied` without prompting; after a result: not granted AND no rationale → `permanentlyDenied`, else `denied`. Dart timeout 60 s |
| `openAppSettings` | none | null | `ACTION_APPLICATION_DETAILS_SETTINGS` for `package:<applicationId>` |
| `getVoiceSetupNetwork` | none | `"unmetered" \| "metered" \| "offline" \| "unknown"` | no connectivity manager → unknown; no active network → offline; no INTERNET capability → offline; not VALIDATED → unknown; **cellular → metered always** (even if the plan is unmetered); `isActiveNetworkMetered` → metered; **VPN → unknown**; else unmetered. Dart timeout 5 s → unknown |
| `requestVoiceDownloadNotificationPermission` | none | bool (granted AND notifications enabled AND channel not `IMPORTANCE_NONE`) | Request code 4710; concurrent callers queue and are all answered by one result; `dispose` answers false; Dart timeout 60 s (the `askNotificationsIfWired` form has none: the system prompt waits for the person and `MissingPluginException` counts as "nothing to ask") |
| `showVoiceDownloadNotification` | `{phase: downloading\|verifying\|complete\|failed, receivedBytes, totalBytes}` | bool | Channel `opencode_voice_setup` (name from `native_voice_setup`, `IMPORTANCE_LOW`), id `0x701CE`, small icon `stat_sys_download`, `setOnlyAlertOnce`, ongoing for downloading/verifying (progress bar determinate by %, indeterminate for verifying or total 0), auto-cancel otherwise, `VISIBILITY_PRIVATE`, `CATEGORY_PROGRESS`, `setTimeoutAfter(15 min)` renewed by every progress update (a killed Dart process must not leave an ongoing notification forever), tap opens `MainActivity` (CLEAR_TOP\|SINGLE_TOP, `FLAG_IMMUTABLE`). **Fixed copy from string resources only** (`native_voice_downloading/preparing/ready/failed`, English + Arabic): no Dart text is posted or persisted. Not a service: it does not keep the download alive |
| `dismissVoiceDownloadNotification` | none | null | cancels id `0x701CE` |

### 4.2 `oc/camera` — `MainActivityMedia`

`hasCamera` → bool (`FEATURE_CAMERA_ANY`); `requestCameraPermission` → three-state string (code 4704; prefs file `camera_permissions`, key `camera_requested`; a second concurrent call → error `permission_in_progress`; already granted → `"granted"`); `openAppSettings`.

### 4.3 `oc/read-aloud` — `ReadAloudBridge` (Dart `ReadAloudController`)

Dart → native:

| Method | Arguments | Result |
|---|---|---|
| `voices` | none | list of `{id: Voice.name, label: Voice.name, locale: languageTag}` (offline only, sorted by name); errors `noOfflineVoice`, `engineUnavailable`, `busy` |
| `speak` | `{operationID, text, voiceID?}` | `{operationID, status:"accepted"}`; errors `busy` (not foreground, capture active, init pending), `tooLong` (blank or > 12,000), `engineUnavailable`, `noOfflineVoice` |
| `stop` | `{operationID}` | null (only cancels when the id is the current operation) |
| `naturalBegin` | `{operationID, sampleRate}` (8,000-48,000) | `{operationID, status:"accepted"}` |
| `naturalWrite` | `{operationID, pcm: ByteArray}` | null (PCM16 LE mono; ignored for a stale operation) |
| `naturalEnd` | `{operationID}` | null (completes when the speaker has played every queued frame) |

Native → Dart: `status {operationID, status: completed\|cancelled\|engineUnavailable\|busy}`. Error codes are the `ReadAloudFailure` names exactly (Dart decodes `PlatformException.code`).

### 4.4 `oc/local_pdf` — `LocalPdfBridge` + isolated `LocalPdfService`

`render {bytes, page, requestID}` → `{png, pageCount, page, width, height}`; `cancel {requestID}` → null. Limits and errors in feature 40. Service is declared `<service android:name=".LocalPdfService" android:exported="false" android:isolatedProcess="true" android:process=":local_pdf"/>`.

### 4.5 `oc/share` — `LaunchShareChannel` (Dart `ShareIntent`, `ShareOut`)

`consumeSharedText` → String? (sets `dartReady`); `shareText {text, subject?}` → bool; native → Dart `shared` (String). Shared with the intent-routing area (`LaunchActivityBridge`).

### 4.6 Activity wiring to preserve

`MainActivity.onPause → readAloud.pause()` (cancels playback, aborts init); `onResume → readAloud.resume()`; `media = MainActivityMedia(this) { readAloud?.beforeMicrophoneCapture() == true }`; `onRequestPermissionsResult → media.onPermissionResult` (order: voice-download notifications, storage access, microphone, camera); `cleanUpFlutterEngine`/`onDestroy` dispose `readAloud`, `localPdf`, `media`. Manifest: `RECORD_AUDIO`, `CAMERA`, `POST_NOTIFICATIONS`, `<uses-feature microphone/camera required="false">` (a device without a camera can still install and pair by pasting), `<queries><intent><action android:name="android.intent.action.TTS_SERVICE"/></intent></queries>` (without it Android 11+ package visibility hides the TTS engines), `android:usesCleartextTraffic="false"`, `android:allowBackup="false"` with `dataExtractionRules`/`fullBackupContent`, `enableOnBackInvokedCallback="true"` (predictive back), the `ACTION_SEND text/plain` filter.

**There are no Kotlin unit tests for `ReadAloudBridge`, `MainActivityMedia`, `VoiceDownloadNotifications`, `LocalPdfBridge` or `LocalPdfService`** (no matching files in `android/app/src/test` or `androidTest`). Their only coverage is the Dart-side channel mocks and `share_intent_native_contract_test.dart` (which greps Kotlin source). The rewrite must add real JVM/instrumented tests for them.

---

## 5. Stored data (exact keys, paths, formats)

`SharedPreferences.getInstance()` (legacy API) is the Flutter store: Android file `shared_prefs/FlutterSharedPreferences.xml`, every key prefixed `flutter.`. Values below are listed without the prefix. All keys are device-wide (not per profile) unless marked.

| Key / path | Type and format | Written by | Notes |
|---|---|---|---|
| `voice.selected_pack` | string: `base` \| `small` \| `tiny` (default `base`; unknown id → first pack) | `VoiceModelManager.selectPack` | |
| `voice.language` | string: `VoiceLanguage.name` (`auto`, `detect`, `english`, `arabic`, `spanish`, … 36 values) | `setLanguage` | unknown → `auto` |
| `voice.auto_send` | bool, default false | `setAutoSend` | |
| `voice.read_aloud_natural` | bool, default false | `NaturalVoiceController` | reset to false when the last natural pack is removed |
| `oc.readAloudConsent` | bool | chat `_ensureSpeechReady` | app-wide consent for the phone's speech engine |
| `oc.pendingPromptPhoto` | string: JSON `{id, profileID, sessionID, directory, workspace, ref:{filename,mime,blob?,url?,bytes}?, path?, name?, failed}`; `id` = microseconds since epoch as text | `PromptPhotoStore` | exists only while a picker is open or a photo waits; identities must survive `KitRedact.text` unchanged |
| `oc.draftAttachmentVault` | bool marker | `DraftPhotoRecovery`, `connection/drafts.dart` | the profile-deletion sweep collects vault files only after this is set |
| `oc.sessionDrafts` | string: JSON map keyed by `SessionDraft.keyFor(profileID, sessionID)` → `{sessionID, profileID, text, updatedAt, attachments:[DraftAttachmentRef], directory, workspace}`; ≤ 50 drafts; a full store refuses new entries instead of dropping another unsent draft | `SessionDraftStore` | attachment payloads are NOT here, only refs |
| `oc.readerPreferences.<profileId>` | string: JSON `{sourceFirst?: bool, wrapCode?: bool}` | `ReaderPreferencesStore` | per profile (swept by `profileScopedPreferenceKeys`) |
| `oc.tips` | JSON (teaching state incl. `hold-mic` counts/cool-down) | `TipsStore` | owned by the teaching area; listed because the mic tip depends on it |
| `<filesDir>/voice_models/<packId>-<revision>/<file>…` | model files; marker `.installed.json` = `{"pack":id,"revision":rev,"files":[{"name","length","sha256"}]}` | downloader | `getApplicationSupportDirectory()` = `Context.getFilesDir()` on Android [code: path_provider; verify on device]. Staging dir `<packDir>.installing/` with `<name>.part`; backups `<name>.voice-backup`; `<marker>.tmp`. Natural voice shares this folder (`kokoro-en-57adb37f…`) |
| `<filesDir>/draft-attachments-v1/<sha256hex(profileID)>/<sha256hex(dataUrl)>.blob` | file content = the attachment's full `data:` URL as UTF-8 text; `.tmp` during write | `DraftAttachmentVault` | caps: 256 MiB disk, 32 MiB per draft, 5 attachments; only hex-64 names are ever read or collected |
| `<filesDir>/prompt-photo-recovery-v1/…` | same vault class, same layout | `PromptPhotoStore` | pending-photo payload while recovery is possible |
| `<cacheDir>/oc-pdf-*.input / .output` | unlinked immediately after open; only descriptors survive | `LocalPdfBridge` | nothing persists |
| image_picker's cache (plugin-owned) | scaled JPEG/PNG copy of the picked/captured photo; its path goes into `oc.pendingPromptPhoto.path` | `image_picker_android` | transient; the native rewrite owns its own cache dir |
| Android `shared_prefs/voice_permissions.xml` | `microphone_requested` bool | Kotlin | native, no Flutter prefix |
| Android `shared_prefs/camera_permissions.xml` | `camera_requested` bool | Kotlin | native |
| Notification | id `0x701CE`, channel `opencode_voice_setup` | Kotlin | |

Per-profile sweep: deleting a server profile calls `promptPhotos.clearForProfile(profileId)` and `DraftAttachmentVault.collect({}, owner: profileId)` (`lib/state/connection/deletion.dart`) and removes `oc.readerPreferences.<id>` through `profileScopedPreferenceKeys`. Voice models are device-wide and are NOT removed by profile deletion (removed by Settings › Voice, phone-setup removal, or Clear data).

---

## 6. Packages and their native replacements

| Today (Flutter) | Role in this area | Native / KMP replacement to evaluate | Notes |
|---|---|---|---|
| `record` 7.1.1 | microphone stream, PCM16 16 kHz mono, interruption events | `android.media.AudioRecord` (+ `AudioRecordingCallback`, audio focus) behind `AudioCapture` | desktop later: javax.sound / PipeWire; iOS: AVAudioEngine |
| `sherpa_onnx` 2.0.1 (bundles ONNX Runtime 1.28.2 and the C API per ABI) | Whisper STT, Kokoro TTS | sherpa-onnx **Kotlin API** from the v2.0.1 GitHub release assets: `sherpa-onnx-2.0.1.aar` (also `-rknn.aar` and a JVM jar) [web]; **not on Maven Central** per the release page; classes `OfflineRecognizer`, `OfflineWhisperModelConfig`, `OfflineTts`, `OfflineTtsKokoroModelConfig` | vendor the AAR (hash-pinned) or build from the tag; arm64-v8a ≈ 9.1 MiB of the APK (libonnxruntime 7.3 + sherpa C API 1.8, compressed) [doc `docs/design/apk-size-2026-10-16.md`]; keep ABI splits; F-Droid builds from source |
| `image_picker` 1.2.3 | gallery multi-pick, camera, lost-data retrieval, platform downscale | AndroidX Photo Picker (`PickMultipleVisualMedia(maxItems)` needs ≥ 2; `PickVisualMedia` for 1), `TakePicture` with a `FileProvider` target Uri; **the app must scale (2048 px, JPEG q85) and handle EXIF rotation itself** | persist the pending record + target Uri before launching (process death) |
| `file_picker` 12.2.0 | open document, save document | `OpenDocument` / `CreateDocument` (SAF) via `ActivityResultContracts`; KMP: FileKit | no path on Android; stream bytes |
| `mobile_scanner` 7.4.2 | CameraX + bundled ML Kit barcode (`com.google.mlkit:barcode-scanning` 17.3.0) | CameraX `ImageAnalysis` + ZXing core / zxing-cpp | owner decision 2026-10-31: drop ML Kit; saves ≈ 3.0 MiB per ABI |
| `qr` 3.0.2 | encode QR modules | `qrcode-kotlin` (KMP) or ZXing `QRCodeWriter` | keep EC level M and the 4-module quiet zone |
| `desktop_drop` 0.8.4 | desktop drop | Compose Multiplatform `dragAndDropTarget` | exact-pinned because it registers a platform plugin and applies KGP |
| `flutter_svg` 2.3.0 + `xml` 7.0.1 | validated static SVG | own validator (common Kotlin) + renderer | no WebView |
| `highlight` 0.7.0 | syntax colour | KMP highlighter | language list in feature 37 |
| `path_provider` 2.1.6 | support dir | `Context.filesDir` (`expect/actual`) | |
| `shared_preferences` | all keys above | Jetpack DataStore or multiplatform-settings, **with a one-time import of `FlutterSharedPreferences.xml`** | Section 10 |
| `crypto` 3.0.7 | SHA-256 | `MessageDigest` / KotlinCrypto | |
| `dart:io HttpClient` / `HttpServer` | model download; Wi-Fi share | Ktor client (OkHttp engine) / Ktor server (CIO) | Ktor version not checked [web fetch returned no version] |
| `desktop`/`window_manager`, `local_auth` etc. | not in this area | — | |

---

## 7. Hard-won behaviours a rewrite must not lose

Each is already shipped, tested or paid for in a commit. Numbered for review checklists.

1. **RAM, not Java heap, gates speech models** (`0c52d80f4`, owner report 2026-09-20): totalMem thresholds 3400/1536/1024 MB; unknown RAM never blocks a manual pick but blocks the automatic one.
2. **Pictures are scaled before reading** (`7356ca5df`): 2048 px, q85, else the model connection overflowed ("WebSocket inbound queue overflow").
3. **Pending-photo record is written before the picker opens**, recovery goes to the *recorded* profile+session, profile removal always wins (`525baca20`, `e2a30576d`, `0e89eb113`).
4. **Bounded reads everywhere**: attachment read keeps `limit+1` bytes; vault reads stream with the expected length; a length check followed by `readAsBytes` is not a bound.
5. **Surrogate-safe truncation** (viewer 200,000 chars, SVG/CSV caps, vault, text splitting for TTS, Android `splitText` high/low surrogate).
6. **Chunk order and failure semantics** in dictation: ordered queue, earlier words survive a failed chunk, generation counters retire late results, cancel waits (`finishingCancellation`) until the decoder isolate is really gone.
7. **Dictation survives leaving the app**: `paused/hidden` stop the mic and write the words; `inactive` (permission dialog, shade) does not.
8. **Capture vs playback exclusion**: `beforeMicrophoneCapture` (10 s guard), recording-config callback, device-removal callback, focus loss.
9. **No microphone behind the person's back**: a model download that finishes in the background stops at "ready".
10. **Per-attempt download consent**, mobile/unknown/VPN needs a yes, and the network is re-read after the notification prompt.
11. **Notification discipline**: fixed native copy, serialized updates (a progress post must never overtake completion), no retained settled futures, 15-minute auto-timeout on the ongoing notification.
12. **Download integrity**: https-only (every redirect hop too), exact `Content-Range` resume, pinned length, SHA-256 in a worker, atomic marker, rollback of partially published installs, per-pack lock, delete removes every temp variant.
13. **Auto-send safety**: never `/` or `!`, ≥ 2 letters/digits after stripping model notes, 3 s visible countdown, any edit cancels, never while away, a hold never auto-sends.
14. **Hold-to-talk**: 350 ms / 18 dp slop / 80 dp cancel / discard under 400 ms / early release handled / system-taken touch cancels.
15. **Automatic reply reading** only after the turn is *proved* to be this turn's (single live user id, parent ids, busy→idle seen); never on a guess.
16. **Read aloud is foreground-only**, consent first, offline voices only, unknown terminal status = failure, stop is operation-scoped, speech scope change stops it.
17. **Natural voice**: English only; one-time fallback note for other languages; sample-rate check; Arabic uses the phone's voice.
18. **PDF isolation** exactly as Section 2 feature 40 (unlink before write, per-request isolated instance, watchdogs, PNG header check, API 29).
19. **SVG allowlist and CSV bounds**; truncated documents are never interpreted as complete.
20. **QR scan never retains the value**; the camera stops before the host pops; stops on pause; permanent denial has no retry button.
21. **Wi-Fi share**: Wi-Fi/hotspot interface only, 192-bit token, GET/HEAD, constant-time compare, `nosniff`, `no-store`, 15 minutes, stops on sheet close/new share/detach, refuses app-private files.
22. **Keyboard-inserted content is the image-paste path** (no clipboard-image API exists in the framework).
23. **Gallery**: swipe only when not zoomed, load neighbours only, Send never sends the prompt, edited picture is `…-edited.png` ≤ 9 MiB.
24. **Copy of file contents is verbatim** (`redact:false`), chat text copy redacts.
25. **Share-in handshake**: Dart handler installed before `consumeSharedText`; the consumed intent is neutralized (`ACTION_MAIN`, extras removed); newer live share beats the cold-start value; share-out excludes this app from the chooser.
26. **Typed device answers, not claims**: `hasCamera` false when the handler is missing; voice `unsupported` vs `unknown`; mic permission off Android = permanently denied.
27. **No raw errors**: typed failures with words; exception text only inside a Details fold (redacted); never logged; paths never shown.
28. **Secrets**: recovered photo whose payload contains a secret is not auto-attached; saved-prompt identities must survive redaction unchanged; pairing payload password behind `consume()`.
29. **Hold-mic tip** registered and cool-down respected.
30. **`<queries>` for `TTS_SERVICE`** and `uses-feature required="false"` for mic/camera.
31. **Stale-result discipline** (`_generation`, `_lifecycleGeneration`, `current()` closures, `_voiceEpoch`): every await in voice/setup/read-aloud/model code is followed by a check; a rewrite using coroutines must cancel scopes the same way.
32. **CAMERA permission and the system camera**: the manifest declares `CAMERA` for the QR scanner, so the photo "Camera" button must ensure the runtime grant first (Flutter's `image_picker_android` does this silently; a native `TakePicture` does not).
33. **DRIFTs to settle deliberately**: (a) download cancel deletes `.part` though docs promise resume; (b) copy chosen by matching exception strings (voice errors, pairing errors); (c) natural voice `minimumMemoryMb` declared but not enforced; (d) docs call the setup controller "foreground-only download"; there is no service keeping a download alive when Android kills the process.

---

## 8. Performance notes

- **No measured speech timings exist in the repo** (searched docs/qa and docs/verification). The rewrite must measure real-time factor of Whisper tiny/base/small and Kokoro on the ZTE nubia (NX721J, 16 GB) and on a 4 GB phone before choosing defaults.
- Flutter's recognizer spawns an isolate and **reloads the model for every 30 s chunk** (and per stop). A native recognizer should keep a warm session while recording and release it after a short idle (memory is the constraint: small ≈ 375 MB of weights; gate 3.4 GB device RAM).
- `VoiceModelManager.initialize()` hashes every installed pack (up to ~540 MB of SHA-256 for all three) in isolates each time the shared manager is created (first mic tap, Settings › Voice, phone setup). Cache a verified-marker stat (size+mtime) and re-hash only on mismatch or on demand.
- Threads: recognizer and TTS use 2 threads on devices with ≥ 4 cores, else 1 (responsiveness over speed).
- Level meter: ~30 updates/s through a `ValueNotifier`, the field does not rebuild (PERF-4); the native version should publish level as a `StateFlow` sampled for the UI.
- Attachments are `data:` URL **strings**: a 20 MiB cap becomes ≈ 27 MB of base64 in memory per draft, hashed and written whole (UTF-8) on every draft save with attachments; thumbnails are decoded once per attachment (`Expando`). Prefer byte arrays + Uri references natively, encode to base64 only at send.
- PDF: pages render only while visible (`scrollCacheExtent: 0`), one request at a time, longest edge 1,536 px.
- Images decode at box size × dpr; unbounded sources capped at 4,096 px.
- Natural voice: first sound waits for voice-model load (the native watchdog allows 90 s), then sentence by sentence; stop must be immediate (isolate kill → coroutine cancel + `AudioTrack.pause/flush`).
- APK: voice runtime ≈ 9.1 MiB per ABI compressed; QR decoder ≈ 3.0 MiB per ABI (ML Kit) → drop. The native demo APK was 2.5 MB; the voice engine alone will be several times that, so keep ABI splits (arm64 download ≈ 36 MB for Flutter today).
- Download progress: the Flutter code notifies listeners on every body chunk; in the native app throttle progress to the UI frame rate (and to the notification's 1% steps, which the setup controller already dedupes by `phase:percent`).

---

## 9. Privacy and security invariants (breaking these is a regression)

- Audio never leaves the phone: recognition is offline; the only network use is the pinned HTTPS model download (host `huggingface.co`, fixed revisions, SHA-256 verified). Copy: "Audio never leaves this phone."
- Transcripts, drafts, URLs, file names and credentials are never logged or put into notification text; native notification copy is fixed string resources.
- Read-aloud consent is explicit and remembered; only offline voices; the engine's own privacy terms are disclosed in the consent sheet.
- Every URL the app did not author goes through `openExternalLink` (Markdown picture on the web = chip, never fetched; license "Open website").
- Photos stay on the phone until Send; the vault lives in app-private storage with names derived from SHA-256; `allowBackup=false`.
- Untrusted PDF parsing only in the isolated no-permission process; SVG only after allowlist validation; no WebView anywhere in previews.
- The Wi-Fi share is explicit per file, short-lived, token-protected, Wi-Fi-only, read-only, and shows a warning that anyone on the Wi-Fi with the link can download it.
- A scanned or pasted pairing code carries the server password: never stored in state, rendered, semantics or logs.
- Exceptions are never displayed (they carry local paths); Details folds redact (`KitRedact`).
- `KitRedact.containsSecret` blocks attaching a recovered photo whose payload looks like a secret; provider credentials from `/config/providers` must never reach logs or diagnostics (AGENTS.md).

---

## 10. Day-1 migration (Flutter → native, same package, same signing key)

1. **Keep model files in place.** `files/voice_models/<id>-<revision>/…` and `.installed.json` need no migration if the native app uses the same layout and marker. A change would force a 160-375 MB re-download (or lose the Kokoro 148 MB natural voice). Keep: pack ids `base|small|tiny|kokoro-en`, folder name `<id>-<revision>`, marker JSON shape, file names.
2. **Import `shared_prefs/FlutterSharedPreferences.xml`** keys above on first launch (strip `flutter.`). Values: bool/string only here. Do not delete the Flutter file until the import is confirmed (needed for rollback/Shorebird-less reinstall).
3. **Draft attachments and pending photo**: keep reading `files/draft-attachments-v1/…` (blob content = data URL text; file name = SHA-256 of that text; owner dir = SHA-256 of profile id) and `files/prompt-photo-recovery-v1/…`; on first launch run the recovery for any `oc.pendingPromptPhoto` record before chats open. If the native app changes the blob format, write a converter that runs once and leaves the old file until the new one verifies.
4. **Consent and preferences**: carry `oc.readAloudConsent`, `voice.*`, `oc.readerPreferences.*`, `oc.tips` so no one is asked again or re-taught.
5. **Native prefs** `voice_permissions`, `camera_permissions` already native: untouched.
6. **Nothing to migrate** for the Wi-Fi share, scanner, PDF service (stateless).
7. **Runtime permissions** (RECORD_AUDIO, CAMERA, POST_NOTIFICATIONS) survive an in-place update; the three-state logic depends on the prefs flags above — keep them or `permanentlyDenied` will read as `denied` once.
8. **The sherpa runtime changes owner** (Flutter plugin → Kotlin AAR). Models are plain ONNX files and stay valid as long as the sherpa-onnx version supports them; re-run the checksum verification after update (cheap on first use) and surface a repair path if a file is missing.
9. **Update notice** replaces Shorebird for voice-engine fixes: model pins and native libs ship with the APK.
10. Emulator proof recipe before the owner gets an APK: install the Flutter build, set a language/pack, download tiny, leave a pending photo record, then `install -r` the native build and check each of 1-4.

---

## 11. Tests: what exists and what to port first

Catalog rows tagged `media-voice` (35) are logic-level behaviours; the catalog also holds 217 rows from other areas citing these files (counts in parentheses = rows citing that file).

| Test file(s) | Features | Port as |
|---|---|---|
| `voice_audio_test.dart` (3), `voice_auto_send_test.dart` (11), `voice_hold_to_talk_test.dart`, `kit/kit_level_meter_test.dart` | 02, 03, 05, 18 | common unit tests (pure logic) — first |
| `voice_language_locale_test.dart` (14), `voice_recognizer_config_test.dart` (2), `voice_model_manifest_test.dart` (3), `interaction_defaults_test.dart` (voice rows), `voice_platform_support_test.dart` (6) | 06, 07, 08 | common unit tests |
| `voice_downloader_test.dart` (14) | 09 | contract tests with a fake HTTP server (Range, redirect, stall, rollback, locks) |
| `voice_model_manager_test.dart` (6), `voice_controller_test.dart` (10), `voice_composer_test.dart`, `revamp/slice_p10_3_test.dart` (chunked dictation) | 05, 10 | integration with fakes for recorder/recognizer |
| `voice_automatic_setup_test.dart` (33), `voice_automatic_setup_platform_test.dart` (10), `voice_automatic_setup_sheet_test.dart`, `setup_voice_component_test.dart` (28), `phone_setup_voice_item_test.dart` | 11, 14 | state-machine tests (fake network/notification platform) |
| `voice_reply_pipeline_test.dart`, `voice_settings_page_test.dart`, `voice_model_localization_test.dart` | 04, 13 | later (widget level) |
| `read_aloud_test.dart` (5), `natural_voice_test.dart` (3) | 19-23 | unit + bridge instrumented tests |
| `prompt_photos_test.dart` (13), `draft_attachments_test.dart` (5), `saved_prompts_photo_recovery_test.dart` (5), `stash_attachments_test.dart` (23), `chat_attachment_reader_test.dart` (8), `office_text_test.dart` (4), `sent_photo_thumbnails_test.dart` | 26-30, 32 | unit/integration; vault and recovery before UI |
| `common_file_preview_test.dart` (2), `local_pdf_test.dart` (1), `delimited_text_test.dart` (6), `static_svg_test.dart` (4), `kit/kit_viewer*_test.dart` (6) | 37-41 | common unit tests for parsers/validators; PDF contract test on device |
| `kit/kit_image_gallery_test.dart`, `kit_image_markup_test.dart`, `kit_image_test.dart` | 42, 43 | UI tests later |
| `wifi_file_share_test.dart` (4), `kit/kit_qr_test.dart` (1) | 45, 47 | JVM tests with Ktor test host |
| `pairing_scanner_test.dart` (6), `pairing_payload_test.dart`, `kit/kit_scanner_test.dart`, `server_pairing_paste_test.dart` | 46, 48 | parser first; scanner with a fake camera |
| `share_intent_test.dart` (11), `share_intent_native_contract_test.dart` (3), `share_routing_test.dart` | 36 | Kotlin JVM tests (Robolectric or intent-only) |

The owner's rule: native tests are written from these first; the 7,967 widget tests come later.

---

## 12. Risks and open questions

**Risks**
1. **sherpa-onnx on Android is not on Maven Central**; the 2.0.1 release ships an AAR on GitHub [web]. Vendoring a prebuilt binary conflicts with F-Droid's build-from-source rule (owner has an F-Droid branch). Decide: vendor AAR with hash pin for the Play-less build, or build the JNI libs from the tag in the repo's build.
2. **Native memory**: Whisper small (≈ 375 MB weights) + Kokoro (≈ 148 MB) + the app on a 4 GB phone; the Flutter code reloads the model per chunk, which hides memory growth. A warm native recognizer changes the memory profile — test the Android low-memory killer while recording.
3. **Android 17 / target SDK 37 local-network permission**: apps targeting 37 need `ACCESS_LOCAL_NETWORK` to connect to LAN devices [web]. It affects pairing to LAN servers and possibly the Wi-Fi share listener (inbound behaviour unconfirmed). The native app already needs compileSdk 37; check on an API 37 emulator.
4. **TTS variability**: the "offline voice" filter can leave zero voices on some engines/devices (`noOfflineVoice`); Google's engine reports `isNetworkConnectionRequired` per voice. Keep the explicit guidance sentence and the manual voice list.
5. **FOSS QR decoder quality**: ML Kit handles curved/low-light/dense codes better than ZXing; pairing QRs are dense (JSON with a 32-byte password and several URLs, hundreds of bytes). Test real `opencode2 pair` output in a dark terminal on the target phone.
6. **Photo pipeline semantics change**: the Flutter plugin scaled, rotated by EXIF and wrote a cache copy; the Photo Picker returns content Uris and no scaling. Orientation and HEIC (Samsung/ZTE camera) conversion need explicit handling; magic-byte sniffing accepts only PNG/JPEG/GIF/WebP so HEIC must be transcoded or refused in words.
7. **Process death while the camera is open** is the reason for the pending-photo record; `ActivityResultContracts.TakePicture` needs a stable target Uri saved before launch, and the result callback must be re-registered after recreation.
8. **Data-URL attachments** are memory-heavy and the vault stores them as text; the protocol areas (OC1/OC2/Paseo/Codex) decide what the server needs. Changing representation touches the migration (Section 10).
9. **Redaction code lives in the UI kit** (`KitRedact`) and is used by `lib/state` and `lib/voice`; AGENTS.md notes `lib/voice` avoids kit imports for prefs. The rewrite needs a non-UI redaction module in the common core.
10. **Typed errors vs string matching** (voice and pairing) means the first rewrite can silently break user copy; add explicit error classes and tests.
11. **No Kotlin tests today** for the native bridges (Section 4.6).
12. **Wi-Fi share keeps running in the background** (no service) and Android may freeze the process; the 15-minute limit is not enforced against an OS-frozen server. Decide whether to stop on pause (safer, matches "foreground only" elsewhere).
13. **Hands-free voice with the screen off** is not supported (read aloud and capture are foreground-only). If wanted, Android 14+ needs `FOREGROUND_SERVICE_MICROPHONE` and a visible notification — a product decision, not a port.
14. **minSdk 26**: PDF preview needs API 29; Photo Picker backport needs Google Play services below API 33 or falls back to `OpenDocument`.
15. **Gemma on-device assistant** (memory `gemma-assistant`, LiteRT-LM, ≈ 3.7 GB, Wi-Fi-only manual download, RAM gate) wants the same model-pack machinery as voice. Build one generic `ModelPack` store/downloader/manager (the Dart one is already generic apart from the Whisper naming) instead of a second copy.
16. **Unread/unverified parts**: `KitImage` beyond the zoom/decode contract, `KitViewer` find/mark internals, `session_handoff_sheets.dart` (QR show), `web_sources_screen.dart`, `prompt_editor.dart`, `chat_send.dart` beyond the voice lines, the full `kit_composer.dart` voice rendering, `ToolOutputFile` model, Paseo/ACP attachment mapping, `ManageSpaceActivity` accounting of `voice_models`.

**Questions for the owner**
- Keep the download partial on *cancel* (docs say so, code deletes it)?
- Natural voice for Arabic: wait for a lexicon-based model, or use another engine?
- Should dictation/conversation survive the screen turning off (foreground service)?
- Accept images/files shared *into* the app (`ACTION_SEND image/*`)? Today only text is accepted.
- In-app camera for photos (CameraX) or keep the system camera app?
- PDF: keep the isolated renderer or move to `androidx.pdf` (in-process, beta)?
- FOSS-only QR decoder for all builds (decided 2026-10-31) — confirm it applies to the native rewrite.

---

## 13. Rewrite guidance

### 13.1 Seams (common interfaces; Android actuals first)

Pure logic in `commonMain` (portable, test first): `SilenceDetector`, `Pcm16Chunker` (accumulator), `VoiceLanguage` + `resolveVoiceLanguage` + `voiceLanguageNote`, `VoiceModelCatalog` (the three packs + Kokoro with hashes), `PackSupport` (ABI/RAM/storage), `automaticVoicePack`, `ProseForSpeech`, `ReadLanguageDetector`, `SentenceSplitter`, `PromptAttachmentMime`, `OfficeText`, `DelimitedText`, `StaticSvgValidator`, `PairingParser`, `MicPress`, `AutoSendPolicy` (`mayAutoSend`), `VoiceReplyWatch` (state machine over events), `AttachmentLimits` (5 / 10 MiB / 20 MiB / 32 MiB / 256 MiB).

Platform interfaces (frozen before parallel work):
- `AudioCapture { start(): Flow<ByteArray>; stop(); cancel(); events: Flow<Interrupted> }`
- `SpeechRecognizer { load(pack, language); transcribe(samples: FloatArray): String; release() }` (sherpa Whisper)
- `ModelStore`/`ModelDownloader` (Ktor/okio; `download(pack, progress, cancel)`; same atomic/rollback/lock rules) + `DeviceProbe`
- `PermissionGate { status(permission): Granted|Denied|PermanentlyDenied; request(); openSettings() }` for mic, camera, notifications
- `SystemTts { voices(): List<Voice>; speak(op, text, voice); stop(op); events }` and `PcmPlayer { begin(rate); write(pcm); end() }`; `AudioSession { beforeCapture(): Boolean; … }`
- `ImageSource { pickMany(limit); takePhoto() }` returning scaled bytes + the recovery record contract; `AttachmentVault`
- `FileSaver`/`FileOpener` (SAF), `QrScanner`, `QrEncoder`, `PdfPageRenderer`, `LocalFileServer`, `ShareService`

### 13.2 Slices (one worktree and agent each; shared libraries single-owner)

| Slice | Writes | Reads | Depends on | Acceptance |
|---|---|---|---|---|
| M1 pure logic + ported tests | `core-media/commonMain`, `commonTest` | this file, catalog rows | none | all rows tagged `media-voice` with level unit/contract pass on JVM |
| M2 voice stack | capture, recognizer, downloader, manager, setup, `oc/voice` equivalents, Settings › Voice, composer voice mode | M1 | M1; composer owner for the pill integration | dictation of 3 minutes → 6 ordered chunks into the draft; model download resumes after kill; background finish does not open the mic |
| M3 read aloud | system TTS, natural voice, audio session | M1 | M1; M2 for `beforeCapture` | speak/stop/focus/capture-exclusion on emulator; Kokoro stop is immediate |
| M4 attachments | picker, camera, vault, recovery, office, paste, drop, tray/tools rows | M1 | M1; composer owner | kill the app with the camera open → photo lands in the right draft after restart |
| M5 viewer | viewer, previews, PDF service, SVG/CSV, gallery, markup, Save | M1 | M1; kit owner for parts | each format opens; PDF in the isolated process; gallery swipe rules |
| M6 sharing and scanning | Wi-Fi server, QR show/scan, camera gate, share in/out | M1 | M1 | QR scan of a real `opencode2 pair` code; Wi-Fi download from a second device; share-in handshake test |

Composer integration (voice pill, tray, chips) is a single-owner unit in the chat/composer area: freeze `ComposerVoiceState`, `ComposerAttachments` and the tray action list before M2/M4 start.

### 13.3 UI to build on the kit (names to keep)

Voice pill and level meter; mic hold with haptics and the tip; first-mic setup sheet; Settings › Voice typing page (model list single-choice, language row, auto-send switch, read-aloud voice choice); consent confirm; voice choice sheet; "+" tray (Photos, Camera, File, More) and tools list; attachment chips with thumbnails; viewer (sheet/page); gallery; markup editor; Wi-Fi share sheet; pairing scanner states; QR card. Predictive back must not fight the row-to-page transform (demo lesson): disable it during the gesture on the viewer/gallery route transforms. Apply the 1.3x frame-text cap to pill/tray/chips; reading text (viewer body, replies) scales fully; honour reduced motion; RTL mirror (level meter and progress are direction-neutral; table data stays LTR).

---

## Appendix A. File index (every file read, to its feature ids)

**Dart, `lib/voice/`:** `controller.dart` 01 04 05 · `audio.dart` 05 · `silence.dart` 03 · `recognizer.dart` 06 · `device.dart` 08 10 12 15 · `model_manifest.dart` 07 08 22 · `automatic_pack.dart` 08 11 14 · `model_manager.dart` 08 10 · `model_download.dart` 09 22 · `automatic_setup.dart` 11 · `automatic_setup_platform.dart` 11 12 · `voice_ui.dart` 11 13 · `voice_settings_parts.dart` 03 07 13 · `presentation.dart` 07 08 13 15 · `notices.dart` 16 · `read_aloud.dart` 19 21 23 · `natural_voice.dart` 22 · `natural_synthesis.dart` 22 · `natural_voice_ui.dart` 13 22.

**Dart, chat part files:** `chat/chat_voice.dart` 01 02 03 · `chat/voice_conversation.dart` 01 03 04 18 · `chat/read_aloud.dart` 19 23 · `chat/chat_attachments.dart` 26 28 29 30 32 · `chat/composer_tools.dart` 25 32 · `chat/composer_tray.dart` 25 · `chat/composer.dart` 25 · `chat/chat_files.dart` 31 35 50 · `chat/chat_gallery.dart` 42 · `chat/sent_photos.dart` 32 · `chat/chat_message_actions.dart` 19 · `chat/chat_send.dart` 04 · `chat/prompt_editor.dart` 35 [unread] · `chat/phrase_chips.dart`, `chat/chat_drafts.dart`, `chat/chat_page_cache.dart` (voice-conversation guards).

**Dart, state/domain/platform:** `state/prompt_photos.dart` 26 27 · `state/draft_attachments.dart` 27 · `state/draft_photo_recovery.dart` 27 · `state/saved_prompt_safety.dart` 27 · `state/session_drafts.dart` 27 · `state/reader_preferences.dart` 49 · `state/pairing.dart` 46 · `state/interaction_defaults.dart` 08 19 · `domain/prompt_attachment.dart` 28 · `domain/office_text.dart` 29 · `domain/file_share_gateway.dart` 45 · `domain/static_svg.dart` 41 · `domain/delimited_text.dart` 41 · `platform/camera.dart` 48 · `platform/local_pdf.dart` 40 · `platform/share_intent.dart` 36 · `platform/wifi_file_share.dart` 45 · `platform/platform_capabilities.dart` §3 · `builtin/setup/voice_component.dart` 14 · `ui/capability_flows.dart` 17 · `ui/search/search_index.dart` 13.

**Dart, UI:** `ui/kit/kit_viewer*.dart` 37 · `ui/kit/kit_image.dart` 42 · `ui/kit/kit_image_gallery.dart` 42 · `ui/kit/kit_image_markup.dart` 43 · `ui/kit/kit_qr.dart` 47 · `ui/kit/kit_scanner.dart` 46 · `ui/kit/kit_level_meter.dart` 18 · `ui/kit/chat/kit_mic_hold.dart` 02 · `ui/kit/chat/kit_composer_types.dart` 01 18 · `ui/kit/chat/kit_composer_tray.dart` 25 · `ui/kit/chat/kit_markdown.dart` 20 · `ui/kit/chat/kit_markdown_image.dart` 32 · `ui/widgets/file_preview.dart` 38 · `ui/widgets/pdf_file_preview.dart` 40 · `ui/widgets/svg_file_preview.dart`, `delimited_file_preview.dart` 41 · `ui/widgets/file_share_sheet.dart` 45 · `ui/widgets/agent_card_photos.dart` 34 · `ui/widgets/markdown.dart` 20 · `ui/screens/files/files_viewer.dart` 39 · `ui/screens/pairing_scanner_screen.dart` 46 · `ui/screens/servers/profile_editor_pairing.dart` 46 · `ui/desktop/file_drop.dart` 31 · `ui/teaching/teaching.dart` 02.

**Kotlin:** `ReadAloudBridge.kt` 21 23 24 · `MainActivityMedia.kt` 12 15 48 · `VoiceDownloadNotifications.kt` 12 · `LocalPdfBridge.kt`, `LocalPdfService.kt` 40 · `LaunchShareChannel.kt` 36 · `MainActivity.kt` (wiring §4.6) · `res/values/strings.xml`, `values-ar` (`native_voice_*`) · `AndroidManifest.xml`.

**Docs read:** `docs/common-file-previews.md`, `docs/qa/slice-P10.3-P6.3-2026-09-28/README.md`, `docs/qa/slice-P10.4-2026-09-27/README.md`, `docs/ux-system/kit-api/KitComposer.md`, `docs/design/apk-size-2026-10-16.md`, `THIRD_PARTY_NOTICES.md`, `pubspec.yaml`. **Memory read:** `voice-model-memory-gate`, `gemma-assistant`, `roadmap-issues-2026-10-31`, `teach-what-is-hidden`, `no-raw-errors-rule`, `android-native-feel`, `vision-decisions-2026-10-20` (via MEMORY.md index lines), `no-glass-rule`, `micro-animations-wanted`.

## Appendix B. Copy families (ARB keys, en + ar + 5 more locales)

`voice*` (58: `voiceSetup*`, `voiceAutoSetup*` 22 keys, `voiceMode*`, `voiceConversation*`, `voiceNotices*`, `voiceAllowMicInSettings`, `voiceInputUnavailable`) · `e7VoiceUi*` (71: errors, progress, privacy, pack labels `e7VoiceUiBalanced/Accurate/Compact` + `…Detail`, support reasons) · `vptt*` (11: language follow/detect/unavailable/compact-rough, auto-send title/detail) · `kitVoice*` (17) · `readAloud*` (21: consent, failures, voice, menu) · `naturalVoice*` (8) · `photo*` (13) · `chatAttachment*` (7) · `chatUiAttach*` (7) · `draftAttachments*` · `codexTextOnlyPrompt` · `composerTool*`/`composerTray*` · `fileShare*` (13) · `filePdf*` (5) · `filePreview*` (6) · `file*` (59) · `readerUi*` (81) · `kitViewer*` (19) · `kitMarkup*` (17) · `kitGallery*` (8) · `kitScanner*` (4) · `kitQr*` · `e7SetupScan*`/`e7SetupCamera*`/`pairingScanner*` · `tipHoldMic*` · `desktopDrop*`. Native: `native_voice_*` in `res/values` and `res/values-ar`. Language names in the picker are authored in their own script (not translated).
