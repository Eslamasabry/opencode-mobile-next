# Assistant on this phone (Gemma 4 E4B, LiteRT-LM) - 2026-10-31

Finish line: on a phone, a person turns on "Assistant on this phone", downloads
the model with progress, and taps "Summarize on this phone" in a conversation
to get a short summary written on the device, offline.
Non-goals: coding or tool use by the local model, any cloud fallback, automatic download.

## Feasibility (checked 2026-10-31)

| Fact | Value |
|---|---|
| Runtime | `com.google.ai.edge.litertlm:litertlm-android:0.19.0` (Google Maven, Apache 2.0) |
| minSdk | 24 (the app's is 26) |
| ABIs | `arm64-v8a` (22.7 MB .so), `x86_64` (26.8 MB .so); no 32-bit ARM |
| AAR size | 21.2 MB |
| Kotlin | the AAR needs 2.3+; the app builds with 2.4.0 |
| Model | `litert-community/gemma-4-E4B-it-litert-lm`, file `gemma-4-E4B-it.litertlm`, 3,659,530,240 bytes, sha256 `0b2a8980...45e0` |
| Pinned revision | `e6a3892a8c22cf87ff99583996149cf3cb32452b` |
| Gated | no (anonymous `resolve/` returns 200), Apache 2.0 |
| Model card memory (S26 Ultra) | CPU about 3.3 GB resident, GPU about 0.7 GB; decode 17.7 / 22.1 tokens/s |
| GPU manifest | optional `libvndksupport.so`, `libOpenCL.so` via `uses-native-library required=false` |

Gate: 64-bit (`arm64-v8a`, plus `x86_64` for the emulator) and total RAM
at least 5400 MB (a "6 GB" phone reports about 5.5 GB). Total RAM only, never
`memoryClass`. Storage: the file plus an eighth (at least 512 MB) free.

## Native contract - `oc/local_llm` (both halves: LocalLlmBridge.kt, lib/local_llm/local_llm_platform.dart)

MethodChannel `oc/local_llm`:

| Method | Arguments | Result | Errors |
|---|---|---|---|
| `status` | - | `{state: unloaded\|loading\|ready\|generating, backend: gpu\|cpu\|null, supported: true}` | - |
| `loadModel` | `path` (inside app files), `backend: gpu\|cpu` | `{backend, fellBack}` | `model_missing`, `busy`, `load_failed` |
| `generate` | `id`, `prompt`, `maxTokens` | null; words arrive on the event channel | `not_loaded`, `bad_prompt`, `busy` |
| `cancel` | - | null; never waits behind the worker | - |
| `unload` | - | null; emits `unloaded` | - |

`loadModel` tries the GPU, then the CPU; a `cpu` request never tries the GPU.
Context window 4096 tokens (`LocalLlmPolicy.CONTEXT_TOKENS`); output capped by
`maxTokens` (16..1024).

EventChannel `oc/local_llm/events` (maps):
`{id, type: token, text}`, `{id, type: done, firstTokenMs, totalMs, chunks, backend}`,
`{id, type: cancelled}`, `{id, type: error, code}`, `{type: unloaded, reason: requested|memory}`.

Threading: one worker thread owns load and generation; events post to the main
thread. Memory: `MainActivity.onTrimMemory` calls `LocalLlmBridge.onTrimMemory`;
the model is released at `RUNNING_CRITICAL`/`COMPLETE`, and at `BACKGROUND`
when idle (backgrounding alone never stops a summary in progress). The path is
refused unless it is inside the app's private files.

## Dart

- `local_llm_pack.dart`: the pinned pack (a `VoiceModelPack`, so the voice
  downloader's HTTPS-only, resume (Range), size and sha256 checks apply) and
  the summary prompt (keeps a long conversation's opening and end).
- `local_llm_controller.dart`: `LocalAssistantController` (switch, gate,
  Wi-Fi rule, storage check, download, delete, load, summary run) and
  `LocalSummaryRun` (streaming text, Stop).
- `local_llm_ui.dart`: the settings page and the summary sheet, kit parts only.
- Wi-Fi only by default: off an unmetered Wi-Fi the page asks first and offers
  "Download on mobile data". Nothing downloads without a tap.
- Storage: `<app support>/local_llm_models/gemma-4-e4b-<rev>/`; Delete removes it.
- Startup check is marker plus file length (hashing 3.7 GB each launch would
  take a minute); the sha256 is checked when the download completes.

## UI

- Settings > Conversations > "Assistant on this phone" (a row under Voice
  typing on the Conversations page, `settings-local-assistant`; also found by
  search): switch, size before download, progress with Cancel, backend line,
  Delete. A phone that fails the gate sees one plain line saying why. The row is
  Android-only (the same gate as Voice typing).
- Conversation menu (Do group): "Summarize on this phone", only when the
  switch is on and the model is downloaded. Opens a sheet that streams the
  summary, with Stop while it runs and Copy after.
- Strings: `localAssistant*` in app_en.arb and app_ar.arb.

## What is stored, and what deletes it

| Data | Where | Scope | Removed by |
|---|---|---|---|
| The model file and its marker | `<app support>/local_llm_models/gemma-4-e4b-<rev>/` (app-private files) | the phone, not a server profile | "Delete from this phone" on the settings page; Android Clear storage / uninstall |
| The switch | `local_assistant.enabled` in preferences (global, like `voice.language`) | the phone | turned off on the same page; Clear storage |
| A summary | nowhere: shown in the sheet, copied only on "Copy summary" | - | closing the sheet |

Removing a server profile therefore has nothing of the assistant to delete: the
model is device-level and no conversation text is kept (the profile sweep in
`ConnectionController.deleteProfileAndLocalData` is not extended on purpose).
Android backup is off (`allowBackup=false`, both rule files exclude everything),
so a 3.7 GB file never enters a backup. PRIVACY.md describes all of this.

## Release build

LiteRT-LM 0.19.0 ships no consumer ProGuard rules and its native library finds
Java classes by name (JNI), so `android/app/litert-lm.pro` keeps
`com.google.ai.edge.litertlm.**` and is added to the release `proguardFiles`.
Without it the model would load in a debug build and fail only in the release
APK that the Shorebird-pinned engine requires. The library is 16 KB page
aligned (LOAD align 0x4000) and the Kotlin calls in `LocalLlmBridge.kt` match
the 0.19.0 class signatures (checked with `javap` on the AAR).

## Status (2026-11-01, dev frozen)

| State | Where it stands |
|---|---|
| Implemented | native bridge, Dart controller, settings page (reachable from Settings > Conversations), summary sheet, menu entry, en + ar copy, R8 rules, PRIVACY.md |
| Enabled | off by default; the switch and the download are each a tap, nothing downloads by itself |
| Verified | `flutter analyze` clean on the touched files. Tests are written and NOT run (owner freeze: no `flutter test`, no APK, no emulator): `test/local_llm/` (controller, sheet, menu entry), the Conversations row in `test/settings_hub_test.dart`, `LocalLlmPolicyTest.kt` |
| Committed | local branch `feat/gemma-assistant-2026-10-31`, not pushed |
| Released | no. Native code, manifest and a Gradle dependency changed, so it needs a new Shorebird release, not a patch |

## Proof still owed (needs the owner's go to build and run)

On the x86_64 emulator, then an arm64 phone with at least 6 GB, with a release APK:

1. Settings > Conversations > Assistant on this phone: the row is there; a phone
   under about 5.4 GB sees the one plain line and no download button.
2. Turn the switch on, tap Download on Wi-Fi: progress moves, Cancel resumes
   from the same bytes (Range), the checksum passes, "On this phone" shows.
   Off Wi-Fi the page asks first.
3. Open a conversation > menu > "Summarize on this phone": words stream, Stop
   keeps what was written, Copy works. Record first word time and words per
   second on the emulator (CPU) and on the phone (GPU line says which).
4. Stop mid-summary, then start another: the native worker must answer the
   cancel with `onDone` or `onError`. If it does not, the worker thread waits
   forever and the next load never starts (`LocalLlmBridge.run` waits on a latch
   with no timeout). Add a bounded wait after a cancel if this shows.
5. Force low memory (`am send-trim-memory`) while idle and while summarizing:
   idle unloads, a running summary survives background but not
   `RUNNING_CRITICAL`; the page shows "Loads when you ask for a summary".
6. Delete from this phone frees the 3.7 GB; reopening shows "Not downloaded".
7. Release APK: R8 kept the library (load succeeds, no `load_failed`).

Evidence goes in `docs/qa/local-assistant-<date>/README.md` with real
screenshots, one screen per image.

## Open

- Quality and speed on a real arm64 phone (the emulator measures x86_64 CPU only).
- Per-ABI APK split would drop about 27 MB (x86_64) from phone installs.
- The Conversations row has no value text ("On") because the controller loads
  lazily; showing one needs the controller read at Settings open.
