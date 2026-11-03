VERIFIED: green

# P12-FIX-VOICE-SCHED-PROMPTS — Codex second-eye findings for VOICE, SCHEDULES and PROMPTS

Date: 2026-11-03. Base: `native/p12-qa-wave4-2026-11-03` at `e61b9d22`. Branch: `claude/native-parity-push-w4-agq3jd`.
No PR, no CI, no signing, no release. Inputs: `docs/native/reviews/p12/{voice,schedules,prompts}.md`, the units'
reports (`P12-VOICE.md`, `P12-SCHEDULES.md`, `P12-PROMPTS.md`), the Flutter source (`lib/`, `android/app/`).

**Finish line:** every real finding of the three reviews is fixed with a test that fails before the fix, or named as
outside this unit's write set with the exact hand-off. **Non-goal:** chat, composer, HTTP client and migration code.

## Shipping state

| State | |
|---|---|
| Implemented | 19 of 22 findings fixed (3 BLOCKERs included); 1 partially (honest fallback, needs the composer); 2 hand-offs |
| Enabled | yes, all in the existing modules and wiring of `:androidApp` |
| Verified | Gradle on this container: module tests, `:androidApp:testPreviewDebugUnitTest`, `:androidApp:assemblePreviewDebug`, `:quality:test` (see Tests) |
| Ran on a device | no |
| Committed / pushed | yes, `[skip ci]` commits on the branch above |

## Findings

### VOICE (`reviews/p12/voice.md`)

| # | Sev | Verdict | Fix | Test |
|---|---|---|---|---|
| 1 | BLOCKER | real | Dictation uses **only** `SpeechRecognizer.createOnDeviceSpeechRecognizer` (API 31+, `isOnDeviceRecognitionAvailable`). The default-service path and the "language missing → default service" fallback are gone (plan 10 §17 S1). Without an on-device recognizer the mic is not offered (`micAvailable = false`, "absent, never dead"); a late start refuses with `SpeechModelMissing`. Settings copy now says the audio stays on the phone (en + ar). | `OnDeviceSpeechTest` "S1 dictation runs only where an on-device recognizer exists" |
| 2 | MAJOR | real | The auto-send countdown starts only when the **spoken words** pass `mayAutoSend` (Flutter `mayAutoSend(voice.draft)`), then guards the merged field as before. | `ComposerVoicePresenterTest` "B-MEDIA-016 a rejected transcript never auto-sends the draft already in the box" |
| 3 | MAJOR | real | `MicRequestRelay`: one stable registry key; an activity created while the request waits registers it again, so the dialog's answer reaches the waiting request after rotation. | `MicRequestRelayTest` (2, B-MEDIA-023) |
| 4 | MAJOR | real | `AndroidSystemTts.engine()` resets `initializing` and shuts the half-made engine down when cancelled during init. | `AndroidSystemTtsTest` "B-MEDIA-009 leaving the app while the voice starts does not break later readings" (Robolectric) |
| 5 | MAJOR | real | `AndroidVoiceSettings` (VoiceWiring.kt) feeds Settings › Voice: offered where dictation can run, the Voice typing row names the phone recognizer and opens `VoiceRoute.Settings` (language, models, Send when I stop talking); Voice notices opens THIRD_PARTY_NOTICES. Smallest edit in `SettingsWiring.kt` (inject, `voice =`, two `SettingsOutside` cases, `OPENS`). | compile + APK; the row/door logic is the settings unit's tested `SettingsStructure` |
| 6 | MAJOR | real | `SpeechProse` tracks the open fence's character and length (CommonMark): only the same character, at least as long, with nothing after it, closes. | `ReplyReaderPresenterTest` "B-CHAT-060 a four-backtick example holding a three-backtick block is skipped whole" |

### SCHEDULES (`reviews/p12/schedules.md`)

| # | Sev | Verdict | Fix | Test |
|---|---|---|---|---|
| 1 | BLOCKER | real | Migration re-stamps a legacy fingerprint **only when it equals** the profile's current Flutter fingerprint (`ScheduleProfiles.legacyFingerprint`: SHA-256 of the Dart `toJson()` without `name`, built with `StoredProfileJson.encode` + `DartJson`). Unknown → nothing migrated; a schedule made for another server stays refused, as in Flutter. | `ScheduleEngineTest` "B-AGENT-218 a Flutter schedule made for another server is not migrated onto the edited profile", "B-MIGR-013 without a known Flutter fingerprint nothing is migrated" (+ the existing B-MIGR-013 keep-firing test) |
| 2 | MAJOR | real | `AppScheduleProfiles.open` builds a **private** connection (the backend's `GatewayFactory`, the credential source, its own scope) pinned to `ServerLocation(projectPath, workspace)` and closes exactly it (Flutter `ScheduledAgentConnection`). The runner also refuses to prompt a newly created conversation whose directory is another project. | `ScheduleEngineTest` "B-AGENT-218 a new conversation the server made in another project never gets the prompt" |
| 3 | MAJOR | real | "Run now" is named by the tap's time, carried in the work request (`DUE`): a restarted worker finds its in-flight record, closes it as `interrupted` and never sends; a further start does nothing. | "B-AGENT-215 a Run now worker restarted after process death never sends again" |
| 4 | MAJOR | real | On `Claim.Duplicate` the engine advances a schedule still pointing at the settled occurrence (the attempt died or its advance write failed) and re-arms. | "B-STATE-090 a run that died after settling still advances and re-arms the next occurrence" |
| 5 | MAJOR | real | `sync()` runs in its own `syncLane` and reads the store inside it, so an older read can never overwrite a newer index/alarm set. | "B-STATE-096 a sync that read an older revision never overwrites a newer one" |
| 6 | MAJOR | real | `FlutterSchedules.kt` restores the frozen Flutter class names: `io.github.eslamasabry.opencode_mobile.ScheduledPromptWorker` (Flutter-era work re-arms everything as native work, sends nothing itself) and `…ScheduledPromptReceiver` (boot, `MY_PACKAGE_REPLACED`, time/zone change, exact-alarm grant change, Flutter-era alarms → `restoreAll`), declared in `:android:schedules`' manifest. | `FlutterWorkerNameTest` (B-MIGR-013) |
| 7 | MAJOR | real | The editor reports each change (`ScheduleEditActions.edited`); the view model keeps the fields (rotation) and the prompt as Flutter's draft `oc.draft.schedule.<id|new>.<profile>` (swept with the profile), restored on reopen and cleared on save. | `SchedulesViewModelTest` "B-STATE-024 typed editor fields survive recreation and the prompt comes back after leaving" |
| 8 | MAJOR | real | `ScheduleExactAlarms`: where exact alarms are allowed, every future armed occurrence also gets `setExactAndAllowWhileIdle` (RTC_WAKEUP) whose broadcast starts the same worker at once under `<workName>.alarm`; the durable claim keeps alarm and fallback to one send. Permissions `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`. | `ScheduleExactAlarmsTest` (3, B-STATE-094; Robolectric) |

### PROMPTS (`reviews/p12/prompts.md`)

| # | Sev | Verdict | Fix | Test |
|---|---|---|---|---|
| 1 | BLOCKER | real | Edit saves the replacement **first** and removes the original after (at worst both exist, never neither); a refused removal takes the copy back out. A full shelf refuses the edit plainly. | `SavedPromptsTest` "B-STATE-135 an edit interrupted before the new prompt is saved keeps the original" (the process dies on the replacement write) |
| 2 | MAJOR | real, **partly fixed** | `ComposerInbox` (kit-chat, composer-owned) carries text only, so files and review references cannot be restored from this unit. No longer silent: a prompt with files/references shows "This prompt has files or review notes that can't go into the message in this version. They stay saved here." with **Add only the words**. Full restore (files, references, location check) is the composer owner's: see Hand-offs. | "B-CHAT-088 a prompt with files never drops them silently and its words can still be added" |
| 3 | MAJOR | real, **hand-off** | `BasicComposerHost` (feature/chat composer) appends every inbox insert; slash commands need replace. Outside this write set. | — |
| 4 | MAJOR | real | `StashDeletionCleaner` (PromptsWiring.kt, a `ProfileResourceCleaner` at `DeletionStep.STASH`, `guardsSweep`) clears the shelf records, attachment files and owner marker inside the deletion; a failure keeps the server and reopens its shelf. `PreviewRuntime` passes the cleaner set to `ProfileDeletion` (one parameter). | `StashDeletionCleanerTest` (2, B-SEC-172) |
| 5 | MAJOR | real | The editor reports its text (`SavedPromptsActions.editorChanged`); the view model keeps it as the editor's starting text without resetting the open field, so a recreated page shows it. | "typed editor words survive the page being recreated" |
| 6 | MAJOR | real | A failed Undo keeps Undo offered, and the failure notice has **Retry** (Flutter's Try again). | "B-STATE-085 a failed Undo keeps Undo offered so it can be tried again" |
| 7 | MAJOR | real | `ShelfPrefs` wraps the shelf's preference adapter: a wrong-typed shelf record reads as unreadable (the page's existing "can't read" notice + Retry) instead of throwing; other keys unchanged. | `ShelfPrefsTest` (2) |
| 8 | MAJOR | real, **hand-off** | Older unattributed drafts → shelf is Flutter's `migrateOlderDrafts` (bootstrap gate + drafts engine): composer/migration-owned, not implemented in `:engine:composer`. | — |

## How to reach each screen

- Settings › Voice (new door) → Voice typing → Voice typing settings (`VoiceRoute.Settings`); also from composer voice notes.
- Chat composer mic (only on phones with on-device speech recognition, Android 12+).
- Read aloud: reply menu › Read aloud.
- Scheduled prompts: Servers › a server › Scheduled prompts → list → New / a schedule (editor).
- Saved prompts: chat ⋯ › Saved prompts.

## Tests and results

Final run (one invocation, JDK 21, Android SDK 37.1, `cd native && ./gradlew --continue`): `:engine:voice:desktopTest
:feature:voice:desktopTest :android:voice:testDebugUnitTest :engine:schedules:desktopTest :feature:schedules:desktopTest
:android:schedules:testDebugUnitTest :feature:prompts:desktopTest :feature:settings:desktopTest
:engine:profiles:desktopTest :androidApp:testPreviewDebugUnitTest :androidApp:assemblePreviewDebug :quality:test` →
**BUILD SUCCESSFUL**.

| Module | Tests | Failed |
|---|---|---|
| :engine:voice | 125 | 0 |
| :feature:voice | 32 | 0 |
| :android:voice (Robolectric) | 21 | 0 |
| :engine:schedules | 51 | 0 |
| :feature:schedules | 19 | 0 |
| :android:schedules (Robolectric) | 4 | 0 |
| :feature:prompts | 25 | 0 |
| :feature:settings | 66 | 0 |
| :engine:profiles | 59 | 0 |
| :androidApp (unit) | 22 | 0 |
| :quality | 59 | 0 |
| :androidApp:assemblePreviewDebug | APK built | — |

Red before the fix (checked by restoring the old code with the new test in place): prompts "B-STATE-135 an edit
interrupted…" and schedules "B-AGENT-218 a Flutter schedule made for another server…" both FAILED, then passed with
the fix. The other new tests fail on the old code by construction (they exercise the removed behaviour).

Environment note: Maven Central answered 429 through the proxy, so a session-local, **uncommitted**
`~/.gradle/init.d/central-mirror.gradle` puts Google's Central mirror first (as P12V-PROMPTS/SCHEDULES did).

## Deviations

- New file `androidApp/.../opencode_mobile/FlutterSchedules.kt` (package `io.github.eslamasabry.opencode_mobile`): the
  frozen Flutter FQCNs cannot live in `graph/SchedulesWiring.kt`. The receiver is declared in `:android:schedules`'
  manifest, like the unit's trampoline activity.
- Small edits outside the units' own files, each the smallest possible: `SettingsWiring.kt` (voice door),
  `PreviewRuntime.kt` (pass `Set<ProfileResourceCleaner>` to `ProfileDeletion`).
- New words files `strings_prompts_fix.xml` (en, ar); `strings_voice_native.xml` gained one string and new copy.
- `:android:schedules` gains `testImplementation(libs.robolectric)`.
- Voice dictation on phones **without** on-device recognition (most Android 11 and older, some 12+) is now off until
  the offline engine (sherpa) lands — the price of S1.

## Hand-offs for the integrator

1. **Composer owner (kit-chat `ComposerInbox` + `BasicComposerHost`):** add `replace: Boolean` to `ComposerInsert`
   and honour it; then `CommandsViewModel` offers `/name ` with `replace = true` (prompts #3), and a full restore
   (files, references, location check) can replace the "Add only the words" fallback (prompts #2).
2. **Composer/migration owner:** port Flutter `migrateOlderDrafts` (prompts #8, B-STATE-087).
3. Settings › Voice reuses the settings unit's Voice page; its Read aloud switch is hidden (`readAloudSupported =
   false`) because read-aloud consent is asked at first use.
