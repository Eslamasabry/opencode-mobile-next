# Unit S3-COMPUI: `:feature:chat-composer` (report)

Date: 2026-11-01. Branch: `native/compui-2026-11-01`, created from `claude/compui-2026-11-01-bjcvh7` at `7db496b2`
(the M0 tip with the kit, gates, runtime-engine and composer-engine merges). Write set:
`native/feature/chat-composer/**` except attachment files, plus this report. Nothing outside it was edited
(`native/tool/check_write_set.sh --base 7db496b2`: OK).

Inputs read: `native/AGENTS.md`, `docs/native/units/INTEGRATE-W1.md`, `S3-COMPE.md`, `S2-KCB.md`; plan 01 (binding);
plan 15 row S3-COMPUI; plan 09 §4.3 (steer), §4.4 (Stop), §6.3–6.4 (`ComposerHost`, layer slots), §10.1–10.3, §10.8–10.10;
plan 08 M8-12 and §5.2 (Stop hand-off), plan 06 rows `KitComposer`/`KitComposerChips`; plan 14 §3 (feature captures);
the catalog rows below; Flutter `lib/domain/model_display_name.dart`, `lib/ui/screens/library_screen.dart`
(`defaultModelLabel`), `lib/ui/widgets/pickers.dart` (`isNewModel`, `presentedEffort`) and their tests.

**Finish line used (one slice).** The composer for one conversation, arranged from existing kit parts, decides and
shows: the field, the one field button (mic → Send → steer forms; Stop handed to the chip row), the chip row (model,
"Ask first"/"Auto-approve", Stop at the trailing end), the mic slot, steer-never-queue, keyboard follow (IME), keys,
history, after-send focus, the failed-send line and inserts; tests and goldens green on the desktop JVM.
**Non-goals:** attachments (S3-MEDIAATT), voice (S5; the mic is a slot), the `/` `@` `!` command sheet, saved prompts and
the full-screen editor, the agent-features and tasks chips, the kit's `KitComposer`, glow and Stop motion
(S2-KCB/S3-COMPMOTION), Metro wiring.

**Shipping state:** implemented; verified locally (desktop tests, goldens, Android compile of this module); committed
and pushed to `native/compui-2026-11-01`. Not wired into the chat, not bound in Metro, not merged. No PR, CI run,
device or emulator run, or release. Plan 15's full finish line (`FullComposerHost` replaces the basic one with the command
sheet, editor, saved prompts and model/agent/thinking picker; E2E-11) is **not** met. §7 lists what is missing.

---

## 1. What was built

Package `io.github.eslamasabry.oc.feature.chatcomposer` (namespace from `:feature:chat-composer`).

| File | What it does |
|---|---|
| `presentation/ComposerFacts.kt` | `ComposerFacts`: what the composer reads per conversation, as plain values: busy, request waiting, `steering` (= `capabilities.promptSteering`), `midTurn: MidTurnPrompt?`, offline, read-only reason, sending, stuck, `ModelFacts`, `ApprovalMode`, glow setting, mic available. |
| `presentation/ComposerUi.kt` | `ComposerUi` (button, chips, glow, note, sends, edit, fieldEnabled), `FieldButton`, `SendHow` (Start, Steer, JoinTurn, StopThenSend, WhenOnline: **no queue value**), `ComposerChip` (Model, Approval, Stop(stuck)), `ModelLabel`, `EffortWord`, `ComposerNote`, `FieldEdit` (Insert / Replace / Clear, applied once per id). No Compose, no copy (K2). |
| `presentation/FieldButtons.kt` | The plan 09 §4.3 table: read-only → none; empty → mic; sending → Sending; offline → "Send when back online"; idle → Send; running + steering → "Add to this turn" (Steer); running + `JoinsTurn` → "Add to this turn" (ordinary send); running + `RestartsTurn` → "Stop and send"; running + unknown → disabled "You can send when this reply finishes". Steering always wins over the mid-turn effect. |
| `presentation/ModelChipLabel.kt` | `ModelNames.fromId` (line-for-line port of Dart `modelNameFromId`, runtime prefixes dropped); `ModelChipLabel.label` (catalog name → name seen before → name from id; never a raw id or `provider/model`; no model picked → server default, else "Loading", or "No model signed in"); `isNew` (< 30 days, a future date is not new); `effort` (words); effort shown only when it is a real choice. |
| `presentation/ComposerKeys.kt` | Ctrl/Cmd+Enter sends; with a fine pointer Enter sends and Shift+Enter is a newline; on touch Enter is a newline. After a send a phone lets the keyboard go; a wide window with a hardware keyboard keeps focus. |
| `presentation/ComposerPort.kt` | `ComposerPort` (facts, `draftChanged`, `flushDraft`, `history`, `send(text, how)`, `stop`) and `FieldValue`. The KDoc maps each call to its engine call (§6). |
| `presentation/ComposerActions.kt` | `ComposerActions` (text changed, send, retry, stop, history key, edit applied, model chip, approval chip, mic) and `ComposerOpeners` (model picker, approvals sheet, voice). |
| `presentation/ComposerPresenter.kt` | The logic, testable under a test scope: builds `ComposerUi` from facts plus local state; text changes go to the draft autosave once per change; Send clears the field at once (bubble first), hands the text to the port with the `SendHow`, flushes the draft on success, and on failure brings the words back (unless the person typed since) with the neutral failed-send note; Try again resends; Stop ignores taps for `MotionTokens.STOP_IGNORE_AFTER_SEND` (800 ms) after a send; history keys become a `Replace` edit; inserts apply once per id. Glow = running && setting on && no request waiting && not read-only. |
| `presentation/ComposerViewModel.kt` | Thin `ViewModel` owning one presenter on `viewModelScope`. |
| `ui/ComposerScreen.kt` | `ComposerScreen(state, actions, field: TextFieldState, …, finePointer, keepFocusOnSend, mic)`, built only from `KitField` (Multiline), `KitChip`, `KitButton` and `KitText`, plus layout, focus and key modifiers: note line, then the chip row (model and approval scroll; Stop is outside the scroller at the trailing end, so it never moves mid-row), then field + button. `navigationBarsPadding().imePadding()` makes it ride the keyboard. Words reach the presenter through `snapshotFlow`; edits are applied to the field's own `TextFieldState` (insert at selection, replace with caret, clear). Keys go through `onPreviewKeyEvent`: Enter per `ComposerKeys`; arrow up/down without modifiers go to history and are consumed only when history moved. The mic slot is a parameter (S5 fills it); without it, a plain "Talk instead of typing" button. |
| `ui/ComposerWords.kt` | Enum → `:ui:words` copy (model chip "Sonnet 5 · High · New", effort words, Loading, No model signed in). Every string comes from existing `:ui:words` keys (`kitComposer*`, `approvalModeAskTitle`, `chatStripAutoApprove*`, `kitTurnLiveStopShort`, `chatSendFailed*`, `kitTryAgain`, `chatSubagentReadOnly`, `modelEffort*`, `modelNewBadge`, `composerFieldLabel`). |
| `ui/FullComposerHost.kt` | `FullComposerHost.Composer(viewModel, chrome, modifier, mic)`: collects state, owns the `TextFieldState`, feeds `pendingInsert` (state, not a flow; plan 09 §6.3) into the presenter once per id, calls `insertDone` and `onSent`. `ComposerChromeInputs` is a feature-local stand-in for the kit-chat `ComposerChrome` (§5 D3). |
| `build.gradle.kts` | `oc.kmp.feature`; `compose-desktop-currentos` in `desktopTest` (Skiko natives); golden directory and record switch as test system properties. No dependency on `:engine:composer` (§5 D1). |

Tests: `commonTest` 4 classes / 30 tests (`FieldButtonsTest` 9, `ModelChipLabelTest` 7, `ComposerPresenterTest` 11,
`ComposerKeysTest` 3) plus `FakeComposerPort`; `desktopTest` `ComposerScreenUiTest` 9 (real presenter, real kit theme,
fake port) and `ComposerGoldensTest` 6 scenes × 3 variants = 18 PNGs (`src/desktopTest/goldens/`, 412 dp wide, motion off,
over the theme's ground).

## 2. Catalog rows and plan tests covered

| Row / plan test | Status | Tests |
|---|---|---|
| **B-AGENT-069** (a default send while busy steers; explicit queue never dispatches) | covered at the composer: steer wins, `SendHow` has no queue | `FieldButtonsTest`, `ComposerPresenterTest`, `ComposerScreenUiTest`, golden `running-steer` |
| **B-CHAT-066** (model chip names from id while loading, previously seen name first, catalog name once read) | covered | `ModelChipLabelTest` (3), `ComposerScreenUiTest` (no raw id on screen) |
| **B-CHAT-067** (new < 30 days; effort as words) | covered | `ModelChipLabelTest` (2), goldens show "· High · New" |
| **B-KIT-057** (ids tidied to short readable names) | covered for the name half (the 15 Dart cases + runtime prefix + `-latest`); the provider half is the model sheet's | `ModelChipLabelTest` |
| **B-CHAT-089** (history navigation keeps the field's own moves) | covered for the key wiring; the navigation itself is `:engine:composer`'s (S3-COMPE) behind the port | `ComposerPresenterTest`, `ComposerScreenUiTest` (arrow up at the start fills the field) |
| **B-CHAT-006** (glow on by default, stored off stays off) | composer half: glow state follows running/setting/request; the setting's storage is settings', the painter is S3-COMPMOTION's | `ComposerPresenterTest` |
| **B-STATE-100** (drafts per conversation) | composer half: committed words go to the autosave port on each change, flush after a send | `ComposerPresenterTest` |
| Plan 09 §4.3 table, `NoQueueChoiceTest` | covered | `FieldButtonsTest` (6 rows of the table + no queue mode) |
| Plan 09 §4.4 Stop (trailing end, absent while a request waits or read-only, amber when stuck), `StopGuardTest` (800 ms) | covered | `ComposerPresenterTest`, `ComposerScreenUiTest`, goldens `running-*` |
| Plan 09 §10.1 keys and after-send focus | covered (desktop key events) | `ComposerKeysTest`, `ComposerScreenUiTest` (Shift+Enter no send, Enter sends, focus kept/released) |
| Plan 09 §10.2 states (idle empty/with text, sending, busy cannot send yet, offline, read-only, failed send with Try again) | covered except voice phases | `FieldButtonsTest`, `ComposerPresenterTest`, `ComposerScreenUiTest`, goldens |
| Plan 09 §6.3 insert once per id at the selection | covered | `ComposerPresenterTest`, `ComposerScreenUiTest` |
| Map CHAT-GENUI 26 (field/send), 27 (chips) | partly: model and approval chips; no agent-features or tasks chip | above |
| Map CHAT-GENUI 30, 31 (commands, saved prompts, editor), 81 | **not done** | — |
| `ImeClearanceTest`, "rides the IME frame by frame" | **not verified**: `imePadding` is applied, but no emulator ran (§7) | — |
| E2E-11 | **not done** (needs drafts/stash/queue engines wired and a device) | — |

Test names start with the row id; plan-only checks use `P09-<section>` (no catalog row exists for them).

## 3. Commands and results

Environment: Android command-line tools `commandlinetools-linux-16111833_latest.zip` from dl.google.com into
`/opt/android-sdk/cmdline-tools/latest`, licences accepted, `sdkmanager "platform-tools" "platforms;android-37.1"
"build-tools;37.0.0"`; `native/local.properties` (gitignored) `sdk.dir=/opt/android-sdk`; Gradle on JDK 21.0.12, JDK 17
toolchain provisioned by foojay (no apt package in the container). `BASH_DEFAULT_TIMEOUT_MS` raised to 600000.
Container-only (not committed): `~/.gradle/gradle.properties` `-Xmx6g`, 4 workers; `~/.gradle/init.d/central-mirror.gradle.kts`
putting Google's Central mirror first in `beforeSettings` (plugin and dependency repositories, with `google()` and the
plugin portal kept) and setting `robolectric.dependency.repo.url`.

All from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/opt/android-sdk`; only this
module's tasks (plus `:quality` Konsist once):

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :engine:composer:compileKotlinDesktop :ui:kit:compileKotlinDesktop :ui:words:compileKotlinDesktop` (dependency check) | **`:engine:composer` FAILED**: `Unresolved reference 'StorageProblem'` (`Drafts.kt`, `AttachmentVault.kt`; the flat-Failure change of INTEGRATE-W1 did not reach the composer engine merged after it). `:ui:words` compiled. → §5 D1. |
| 2 | `./gradlew :feature:chat-composer:desktopTest` (tests only, red first) | FAILED as intended: unresolved `ComposerKeys`, `ComposerPresenter`, … The build file passed the module rules. |
| 3 | same, after the presentation code | BUILD SUCCESSFUL, 30 tests, 0 failures (first green). |
| 4 | `./gradlew :feature:chat-composer:compileKotlinDesktop` (screen) | BUILD SUCCESSFUL. |
| 5 | `./gradlew :feature:chat-composer:desktopTest -Poc.recordGoldens=true` | 45 tests, 0 failures; 18 goldens recorded. Looked at by eye: the dark captures had no ground behind them (white canvas, near-invisible tertiary buttons), a harness fault; fixed by painting `LocalKitColors.ground` in the harness, then re-recorded and re-checked (§4). |
| 6 | `./gradlew -Poc.werror=true :feature:chat-composer:desktopTest :feature:chat-composer:compileAndroidMain --rerun-tasks` | FAILED in **`:ui:kit`** (3 warnings there: deprecated `maxLength`, two needless `!!` in `KitRow.kt`); nothing from this module. `-Poc.werror` is project-wide, so it cannot be applied to one module here. |
| 7 | `./gradlew :feature:chat-composer:compileAndroidMain :feature:chat-composer:compileKotlinDesktop :feature:chat-composer:desktopTest --rerun` | **BUILD SUCCESSFUL**, no warnings from this module; **45 tests, 0 failures** (Goldens 6, Keys 3, Presenter 11, ScreenUi 9, FieldButtons 9, ModelChipLabel 7). |
| 8 | Mutation check: copy `idle-text-light-en.png` over `idle-empty-light-en.png`, run `--tests '*ComposerGoldensTest*'` | FAILED as intended: "idle-empty-light-en: 11.854 % of pixels differ (limit 0.5 %)"; golden restored. |
| 9 | `./gradlew :feature:chat-composer:desktopTest` (final committed tree) | BUILD SUCCESSFUL, 45/45. |
| 10 | `./gradlew :quality:test --tests '*KonsistRulesTest*'` | 11 tests, **2 failures**: **K1** on this module's 4 **test** files (`ComposerPresenterTest`, `ComposerHarness`, `ComposerScreenUiTest`, `ComposerGoldensTest`; every production file passes K1 and K2), and K4 on `:ui:kit-chat` `KitChatLocals.kt` (not mine). → §5 D5. |
| 11 | `tool/check_write_set.sh --base 7db496b2 --globs 'native/feature/chat-composer/**,docs/native/units/S3-COMPUI.md'` | OK, 39 paths before this report. |
| 12 | `tool/check_commits.sh 7db496b2 HEAD` | OK (the first three local commits failed the subject rules and were rebuilt as two before any push). |
| 13 | `tool/scan_push.sh 7db496b2` and `git diff 7db496b2 \| grep` for RFC 1918/CGNAT addresses and e-mail addresses | clean / no match. |

Not run: `build` (rule 12), `:androidApp`, other modules' tests, iOS, any device or emulator.

## 4. Goldens, by eye

`src/desktopTest/goldens/<scene>-<light-en|dark-en|dark-ar>.png`:

- **idle-empty**: model chip "Sonnet 5 · High · New", "Ask first", the field with its "Message" hint, "Talk instead of typing" (the mic slot's stand-in).
- **idle-text**: "Auto-approve" in the active tone, a two-line field, accent "Send".
- **running-steer**: Stop at the trailing end of the chip row, accent "Add to this turn".
- **running-stuck-restart**: Stop in the attention tone, "Auto-approve paused", "Stop and send" as a secondary button. In Arabic the row mirrors (Stop on the left) and the copy is Arabic.
- **failed-offline**: "Your message wasn't sent · It's back in the message box." with "Try again", and "Send when back online".
- **read-only**: the sub-agent sentence and a disabled field, no button.

Seen and left for the kit owner: the multiline `KitField` keeps its label above the box ("Message to the agent"), which a composer does not want; in Arabic a Latin draft in the field does not sit at the start edge.

## 5. Deviations and decisions

| # | Decision | Why |
|---|---|---|
| D1 | **No dependency on `:engine:composer`.** The presentation talks to `ComposerPort`; tests use `FakeComposerPort`. | The engine does not compile on this tip (§3 #1); the brief says to depend only on modules that compile. `:engine:delivery` (S2-DLV), which owns send/steer/stop, does not exist at all. |
| D2 | `FieldButtons` re-states the §4.3 order that `:engine:composer` `SendDecision` also holds. | Needed without D1's dependency. It covers only the field button and the `SendHow`; commands, shell and attachments stay in `SendDecision`. Once the engine compiles, `FieldButtons.how` should delegate to `SendDecision` (one source of truth). |
| D3 | `FullComposerHost` is an `object` with a `Composer(viewModel, chrome, …)` function and a feature-local `ComposerChromeInputs`; it is not `@ContributesBinding` and implements no interface. | `ComposerHost`, `ComposerChrome` and `InsertRequest` (plan 09 §6.3) do not exist in `:ui:kit-chat`, which is not this unit's. `BasicComposerHost` does not exist in `:feature:chat` either, so there is nothing to replace yet. |
| D4 | **Arranged from existing kit parts; no `KitComposer`.** Glow is state only (`ComposerUi.glow`), "Stop and send" is a secondary `KitButton` (not the amber pill), the field is `KitField(Multiline)` with its visible label, the field button is a text button (kit glyphs are `internal`, and features cannot build an `ImageVector`). | Rule 2: a missing part is added to the kit by its owner, never drawn in a feature. `KitComposer` v1 is S2-KCB's; the glow, the Stop chip motion and the "Stop and send" pill are S3-COMPMOTION's. When they land, `ComposerScreen` swaps parts; `ComposerUi` already carries everything they need (button, chips with `stuck`, glow). |
| D5 | **UI tests and goldens in this module's `desktopTest`, importing `compose.ui.test`, `kotlinx.coroutines.test`, `:core:testing`, `DesktopKitPlatform` and AWT.** K1 fails on those 4 test files. | Plan 14 §3 puts feature captures in `:ui:screenshots`, which does not exist, and plan 03 §6.5 gives feature `commonTest` `compose-ui-test` and `:ui:testing`, which K1 as written (it scans every file under `feature/`, tests included) forbids. **Coordinator decision needed:** either slice K1 to production source sets (the same `.slice { "/src/commonMain/" in it.path … }` that `production` uses), or move these 3 desktop test files into `:ui:screenshots` when it exists. Until then `:quality:test` K1 is red with this branch merged. |
| D6 | Goldens are desktop (Skiko software raster) captures compared in-test (≤ 0.5 % of pixels may differ by more than 24 per channel), not Roborazzi. | Feature modules have no `androidHostTest` (ADR §7.2 list), so Roborazzi cannot run here (plan 14 §3). |
| D7 | Read-only hides the whole chip row, not only Stop. | Plan 09 §10.2: read-only shows the reason in the pill and hides Send, + and the mic; the model and approval chips change nothing in a chat that takes no prompts. Easy to revisit. |
| D8 | "Loading…" for the model chip uses `agentsModelLoading` ("Reading the models…"), marked `TODO(words)`. | `:ui:words` has no plain "Loading…" key; this unit may not edit it. |
| D9 | A send's words come back only if the field is empty or unchanged since the send. | "Text is always kept": if the person typed something new while the send failed, overwriting it would lose words; Try again still resends the failed text. |
| D10 | No formatter run. | `tool/format-changed.sh` (rule 13) does not exist in the tree; the code follows the surrounding style by hand. |

## 6. Catalog requests and requests to other owners

**Catalog:** none new. `compose-desktop-currentos` already exists and is used in `desktopTest` only.

- **Coordinator:** add `:feature:chat-composer	F	(slice brief names the agent)	slice-3` (or the owner you name) to
  `native/MODULE_OWNERS` (`ModuleOwnersTest` gates new modules; not run here); decide D5 (K1 scope).
- **INTEGRATE-W2 / composer-engine owner:** make `:engine:composer` compile on the flat `Failure` (replace
  `Failure.Storage(StorageProblem.*)` with `StorageRefused` and the like). Then this unit's first follow-up is the port
  adapter (about 60 lines): `facts` from capabilities + `MidTurnPromptGateway` + delivery + requests + settings;
  `draftChanged`/`flushDraft` → `DraftAutosave`; `history` → `PromptHistoryKeys.isHistoryKey` + `PromptHistoryNavigation.move`
  over `PromptShelf` history (map `FieldValue` ↔ `ComposerValue` field for field); `send`/`stop` → `PromptDelivery`; and
  `FieldButtons.how` → `SendDecision` (D2).
- **S2-DLV:** `PromptDelivery.send(text, how)` taking the four mid-turn modes plus offline, and `stop()` that calls off a
  waiting send or stops the turn (plan 09 §4.4).
- **Kit-chat owner (S2-KCB, S3-COMPMOTION):** `KitComposer` (field without a visible label, Send/mic/steer button with
  glyphs, the amber "Stop and send" pill, frost), `KitComposerStop` chip with stop glyph and hold menu, `KitComposerGlow`
  reading `ComposerUi.glow`, and `ComposerHost`/`ComposerChrome`/`InsertRequest` (plan 09 §6.3). Also a public send/mic/stop
  glyph set (kit glyphs are `internal`).
- **`:ui:words`:** a plain "Loading…" for the model chip (D8); "Stops the current reply and starts again with your message."
  for the "Stop and send" explanation (plan 09 §4.3), not found in the words files.
- **Kit owner:** `-Poc.werror` fails in `:ui:kit` (deprecated `InputTransformation.maxLength`, two needless `!!` in
  `KitRow.kt`), so no module that depends on the kit can be checked with warnings as errors.
- **Chat lead:** inject `FullComposerHost` in the chat entry and hand `ComposerChromeInputs` (or the kit-chat chrome) the
  `pendingInsert`/`insertDone`/`onSent`, fine-pointer and wide-window facts.

## 7. Open problems

1. Plan 15's finish line is not met: the `/` `@` `!` command sheet, saved prompts, the full-screen editor, the
   model/agent/thinking picker sheet, the agent-features and tasks chips, phrase chips and the "+" tray are not built,
   and nothing is wired into the chat or Metro. E2E-11 cannot run.
2. **IME-follow is unverified on a device.** The composer applies `navigationBarsPadding().imePadding()`; desktop tests
   cannot show the keyboard, and no emulator was available. `ImeClearanceTest` (S3-COMPMOTION) and the plan's "rides the
   IME frame by frame" check are still owed.
3. `:quality:test` K1 is red on this module's test files until the coordinator decides D5; `ModuleOwnersTest` (not run
   here) needs the new row.
4. Read-only reasons: only the sub-agent reason exists (`ReadOnlyReason.SubAgent`); watched and demo chats need their
   own words when their units land.
5. The field reads the IME composing range through `TextFieldState.composition` and hands it to history; the composing
   guard itself lives in the engine's `PromptHistoryKeys`, so it is untested here until the adapter exists.
6. Teaching (plan 09 §10.10): no tips yet for hold Send (`send-later`) or hold Stop (`stop-after-step`), because neither
   hold gesture exists without the kit parts. Per the teach-what-is-hidden rule they ship together with those gestures.
7. Goldens were recorded in this container (Skiko software raster, bundled fonts). Another host may need re-recording
   or a looser per-image limit.
