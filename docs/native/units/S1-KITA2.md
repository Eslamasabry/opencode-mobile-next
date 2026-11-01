# S1-KITA2 · Kit part B

Date: 2026-11-01. Branch: `native/kita2-2026-11-01`, created from `claude/kita2-part-b-port-w608vs` at `6b9a2b21` (the
`native/m0-2026-11-01` tip).

Inputs read:
- `native/AGENTS.md` and `docs/native/slice0/P7-kit.md`;
- plan 06: §3.2, §4.9, §7.0–§7.6, §11, §16 and §17–§18;
- plan 15: the S1-KITA2 row;
- plan 08 §2.7, through the existing `KitMotion`;
- catalog rows B-KIT-011, 014, 015, 016, 019, 022, 025, 027, 031, 032 and 034.

Flutter sources: `lib/ui/kit/` `kit_buttons.dart`, `kit_chip.dart`, `kit_menu.dart`, `kit_segmented.dart`,
`kit_choice_list.dart`, `kit_progress.dart`, `kit_state_view.dart`, plus the l10n keys the receipt, needs-you, undo,
confirm, dialog and sheet files use.

**Finish line (plan 15).** Buttons, the action block, the icon button, chips, `KitField` in all kinds, segmented, the
choice list, the switch and expand rows, sheet, confirm, dialog, menu, undo, state view, loading bar, skeleton, progress,
receipt and needs-you meet 06 §16.3.

**Result.** Every named part exists. Each one compiles for desktop and Android and has its pure rules unit-tested by
catalog row. Each is driven headless on desktop, and has Roborazzi goldens in light and dark, en and ar. Sheets and
dialogs own Back through `NavigationBackHandler`, including the predictive gesture. Every haptic goes through
`KitPlatform.haptic`. Several §16.3 items are **not done**: the `api/<Part>.md` specs, the `KIT.md` rows, the
capacity-contract entries and scenes, `AccessibilityRulesTest`, previews, and VPS-recorded goldens. They are listed under
Open problems.

Shipping state: implemented and verified locally (desktop and Robolectric). Committed and pushed to the branch. No PR, no
CI, no release.

## 1. What was built

Every file is in `native/ui/kit/**`, and every file except two is new. **Edits to shared files:**
- `KitParts.kt`: the `KitButton` block is removed (one hunk), because `KitButton` now lives in its own file.
- `build.gradle.kts`: two dependency lines are added.
- `modal/KitSheet.kt`: rewritten. It is a part-B file.

No part-A part was edited.

| File (`ui/kit/src/commonMain/.../ui/kit/`) | Parts and types |
|---|---|
| `KitButton.kt` (same package as before, so the `:feature:chats` and `:ui:shell` imports are unchanged) | `KitButton`, which has roles Primary, Secondary and Tertiary, plus destructive, icon, `working`, `KitEnabled` and haptic. It eases its fill on QUICK. `KitButtonRole`. `KitAction`. A bridge overload keeps the slice-0 call shape `enabled: Boolean, disabledReason`, and a `KitButton(action, role)` form runs an action as a button. |
| `foundation/KitEnabled.kt`, `foundation/KitAssert.kt` | `KitEnabled` (Enabled / Disabled(reason)), and `kitAssert`, which throws `KitRuleBroken` when `isDebug` and logs through `AppLog` in release (plan 06 §16.1). |
| `foundation/KitPartGlyphs.kt` | Internal stand-in glyphs: check, close, chevrons, more, eye, warning, ring, undo, copy and paste. `KitGlyph` mirrors the directional ones in RTL. They will be replaced by the generated `KitGlyphs`. |
| `action/KitActionBlock.kt`, `action/KitIconButton.kt`, `action/KitWorkingMark.kt` | `KitActionBlock`: it stacks on compact, puts the primary at the end of one row from 600 dp, lets a destructive tertiary force the stack, shows two tertiary actions and puts the rest in "More" (`kit-actions-more`), and shows a disabled reason as a muted line. `ActionBlockRules`. `KitIconButton`: label required, glyph cross-fade, `stateLabel`. The working mark is a still dot when loops are off. |
| `chip/KitChip.kt` | `KitChip`: kinds Plain, Action, Removable and Count; tones Neutral, Attention and Active; `selected`. The remove target is `kit-chip-remove` and keeps 48 dp on a 32 dp pill. `KitChipWrap`. |
| `input/KitField.kt` | `KitField` in Text, Multiline, Mono, Path, Url, Number and Secret: label above, a live helper or error line, and a counter from 80 % of the limit. Number maps Arabic-Indic digits and the Arabic decimal separator. Secret uses `BasicSecureTextField` with reveal and Paste, no autocorrect, LTR, and is never prefilled: `SecretFieldPresence.Saved` shows "Saved · Replace". "Checking…" escalates at 8 s. Also `KitSecretSink` / `LocalKitSecretSink` (every secret keystroke) and `KitFieldRules`. |
| `choice/KitSegmented.kt`, `choice/KitChoiceList.kt`, `choice/KitSelectTap.kt` | `KitSegmented` (2–4 segments; stacks into radio rows when a word does not fit or at 2× text; `Select` only on a real change) and `SegmentedRules`. `KitChoiceList` (single), `KitChoiceListMulti`, `KitChoiceRow`, `KitChoiceMark` and `ChoiceRules`, with Current / Recommended tags, loading skeletons and an empty slot. The internal `kitSelectTap` (`selectable`/`toggleable`) gives the kit's press feedback and sends haptics through the seam. |
| `row/KitSwitchRow.kt`, `row/KitExpandRow.kt` | `KitSwitchRow`: `ToggleOn` / `ToggleOff`; the risk fold with Turn on / Not now; locked refuses false. `SwitchRules`. `KitExpandRow` reveals its content, turns its chevron, and has expand/collapse semantics actions. |
| `modal/KitSheet.kt` | `KitSheet` keeps the P7 call shape; `closeLabel` is now optional and comes from `:ui:words`. New: icon tile, pinned `KitActionBlock`, `dirty` / `draft` (asks "Discard your changes?"), `dismissible`, `framed`, and placement by window (bottom ≤ 640, centred 560 from 840 dp, end sheet 440 for Full). The host gained a dialog layer. Also `SheetLayout`. |
| `modal/KitPredictiveBack.kt` | The topmost sheet or dialog registers `NavigationBackHandler`. It reads the gesture progress in the draw phase, eased on `KitEasing.PredictiveBack`, and shrinks toward its edge. With no dispatcher owner it does nothing. Under Off, modals have no transitions (`ModalMotion`). |
| `modal/KitConfirm.kt` | `KitConfirm(spec, onConfirm, onCancel)`, `KitConfirmSpec`, `KitConfirmKind`, `KitConsequences` and `ConfirmRules`. Shape: a sheet on compact, a 480 dp dialog on wide. The confirm is the strong neutral fill. Typed name. Cancel is Keep running / Keep editing / Cancel by kind. `Commit` for stop, destructive and discard. Debug asserts the title is a question and the label is not OK/Yes. |
| `modal/KitDialog.kt` | `KitAlert`; `KitInputDialog` (opens focused with its words selected, Done submits, answers null when unchanged); `InputDialogRules`; the in-window dialog layer. |
| `modal/KitMenu.kt` | `KitMenu(expanded, items, onDismissRequest, …, anchor)` on a foundation `Popup`; `KitMenuPanel` (≤ 320 dp, headings, check, disabled reason); `KitMenuItem`, `KitMenuGroup`; `MenuLayout.arrange` (pure). |
| `modal/KitUndoBar.kt` | `KitUndoBar(offer, onUndo, onTimeout)`, `KitUndoOffer`, `UndoRules`: 8 s stretched by `calculateRecommendedTimeoutMillis`; no timeout and a Dismiss action under `accessibleNavigation`; Undoing and failed states. |
| `state/KitLoadingBar.kt`, `state/KitSkeletonRows.kt` | A 2 dp bar that always takes its height, sweeps, and is a still 30 % segment when loops are off. Skeletons are `text1` at 12 / 9 %, cleared from semantics, with no motion. |
| `state/KitProgress.kt` | `KitProgress` (Waiting, Known, Staged), `KitProgressView`, `KitProgressTone`, `ProgressRules` (value, ranges, forward-only within a job, ETA rounded up) and `KitProgress.line()`. |
| `state/KitReceipt.kt`, `state/KitNeedsYou.kt` | `KitReceipt`: Sending, Sent, Not confirmed yet at 8 s, Done, Refused (with Try again) and Answered elsewhere; `ReceiptRules`. `KitNeedsYou`: an amber dot always with its word, for three reasons. |
| `state/KitStateView.kt` | `KitStateView(kind, title, …, failure, since, onSlow, …)`. The error defaults: a network failure offers Try again and Switch server; any other offers Try again and Report a problem; both offer Copy details when the `Failure` has redacted details. It escalates once at 8 s and unfolds the `onSlow` actions. Working → Finished plays `Done` (not under Off). A prerequisite with no enable action is refused. `StateViewRules`. |

Kit-internal words come from `:ui:words` (`strings_kit.xml` / `strings_common.xml`): Close, More, Paste, Saved,
Replace, Undo, Dismiss, Try again, Copy details, receipts, needs-you, discard, typed-name and progress.

## 2. Catalog rows covered

Tests are named after their rows: `PartBRulesTest` (27, common), `PartBDesktopTest` (21, desktop compose) and
`PartBGoldensTest` (10 scenes × 4 variants).

| Row | Logic (common) | Behaviour (desktop) | Golden |
|---|---|---|---|
| B-KIT-011 actions | layout and More split; runnable | working ignores taps and is not enabled; disabled says why; extras in More; empty label refused | `KitActionBlock/roles-*` |
| B-KIT-014 choice list | toggle; Current rule | one tap, one callback; disabled choice inert, with its reason | `KitChoiceList/segmented-choices-switches-*` |
| B-KIT-015 confirm | question title; act label; typed name; Enter; Commit | typed name gates confirm, Commit plays; Back cancels via the gesture | `KitConfirm/destructive-typed-name-*` |
| B-KIT-016 dialogs | null when unchanged or cancelled | null, then the new text | `KitInputDialog/open-*` |
| B-KIT-019 icon button | — | named by its label, silent on a plain tap, disabled state | in `roles-*` |
| B-KIT-022 menu | order; destructive last after one divider; none if all destructive; headings | runs the chosen item once; disabled runs nothing | `KitMenu/panel-*` |
| B-KIT-023 reduced motion (part-B half) | — | under Off, a reveal and a sheet are final on their first frame | — |
| B-KIT-025 progress | staged value, ranges, forward-only, ETA | — | `KitStateView/progress-receipt-error-*` |
| B-KIT-027 rows (switch and expand half) | locked, risk, flip, haptics | ToggleOff and ToggleOn; risk asks; locked stays on; expand unfolds | in `segmented-choices-switches-*` |
| B-KIT-031 segmented | 2–4; tap rules; stacking | Select only on change; stacks at 2× | same |
| B-KIT-032 sheet | placement by window; asks before losing input | cancelled gesture keeps it, completed closes it; dirty asks; Keep editing / Discard | `KitSheet/v2-actions-*` |
| B-KIT-034 state view | 8 s; error ways; missing prerequisite | escalates at 8 s with the title kept; network error offers no Report | in `progress-receipt-error-*` |
| B-L10N-012 (input half) | Arabic-Indic digits → ASCII | — | — |
| B-SEC-204 | — | secret keystrokes reach the sink; Saved shows no input | `KitField/kinds-*` |

Not covered here:
- B-KIT-032 "closes when answered elsewhere": a ViewModel test for the owning screen (plan 06 §17).
- B-KIT-051: this unit only consumes `KitTappable`; it is not changed.

## 3. Commands and results

Environment: commandlinetools 16111833 in `/opt/android-sdk/cmdline-tools/latest`, with platform-tools,
`platforms;android-37.1/37.0/36` and `build-tools;37.0.0/36.1.0`. JDK 21 (Gradle) and JDK 17 (toolchain).
`native/local.properties` is gitignored. Maven Central answered 429 through the proxy, so the container-only
`~/.gradle/init.d/central-mirror.gradle.kts` puts Google's Central mirror first and the plugin portal last. The Robolectric
`android-all-instrumented` jars for 17 (`42dc8bf9…5b74`) and 11 (`2685e41b…5ac6`) were seeded into `~/.m2`, and each
SHA-1 matched the published `.sha1`.

All runs are from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and `ANDROID_HOME=/opt/android-sdk`.

| Command | Result |
|---|---|
| `./gradlew :ui:kit:compileKotlinDesktop` | BUILD SUCCESSFUL |
| `./gradlew :ui:kit:compileAndroidMain` (run as part of the host-test tasks) | BUILD SUCCESSFUL |
| `./gradlew :ui:kit:desktopTest` | 55 passed: 48 new and P7's 7 |
| `./gradlew :ui:kit:recordRoborazziAndroidHostTest --tests '*PartBGoldensTest*'` | 40 PNGs |
| `./gradlew :ui:kit:recordRoborazziAndroidHostTest --tests '*KitGoldensTest.kitButton*' --tests '*KitGoldensTest.kitSheet*'` | Re-recorded P7's `KitButton/states-*-en` and `KitSheet/open-*-en`. Verify had shown a sub-pixel shift of the centred label; both are part-B parts. |
| `./gradlew :ui:kit:verifyRoborazziAndroidHostTest` | 90 host tests, 0 failures. This includes P7's frame-text, frost, text-scale and platform tests, unchanged. |
| `./gradlew :ui:shell:compileKotlinDesktop` | BUILD SUCCESSFUL against the new kit |
| `./gradlew :feature:chats:compileKotlinDesktop` | Fails in `:engine:connection` (`ConnectionRegistry.kt`: `GatewayFactory.backend`). Not kit code; the core contract is being reconciled in parallel. |
| `./gradlew :ui:shell:compileTestKotlinDesktop` | Fails in `ShellHarness.kt` (`TestPlatform` lacks P7's `KitPlatform` members; `KitTheme(dark = …)`). This existed before this branch and is outside my write set. |

The goldens were looked at in light-en, dark-ar and others. Three fixes came from that review:
1. The kit's words were in English in the Arabic captures. Fixed by setting the Robolectric qualifiers and the default
   locale.
2. A locked-on switch read as off.
3. The removable chip grew to 48 dp.

A privacy grep of the diff found no private IPs and no email addresses. The sample address is `office.example.com`.

## 4. Deviations

1. **`KitButton` stays in the root package** (`io.github.eslamasabry.oc.ui.kit`), in its own `KitButton.kt`, because
   `:feature:chats`, `:ui:shell` and `KitOnlyPlantedTest` import it there. The other new parts use the plan's
   sub-packages.
2. **`KitMenu` takes `items` with an optional `group` on each item**, as Dart does. Plan 06 §7.4 writes `groups`. The
   ordering rule is the same.
3. **`KitStateView` takes `body` words from the screen.** `:ui:words` has no `Failure` → sentence mapper yet. The
   `failure` decides the ways out and supplies Copy details.
4. **`KitProgressTone` instead of `StatusTone`**: `:ui:design` has no `StatusTone` yet; it belongs to S1-KITA1.
5. **Switch haptics are `ToggleOn` / `ToggleOff`** (plan 06 §4.9 table) rather than §7.5's "`Select`". Segmented and
   wheel-like choices keep `Select`.
6. **Glyphs are internal stand-ins** (`KitPartGlyphs`). `KitIconButton` takes an `ImageVector` until `KitGlyphs`
   exists.
7. **Paste and Copy details use `LocalClipboardManager`**, which is deprecated, because CMP 1.13 has no common
   plain-text API on `LocalClipboard`. Warnings fail the build only under `-Poc.werror`. `IME_FLAG_NO_PERSONALIZED_LEARNING`
   is not set: that needs an Android-only `InterceptPlatformTextInput` hook.
8. **`KitEnabled` and `kitAssert` are defined here** under `foundation/`. S1-KITA1 may need the same names; the
   coordinator should keep one copy at merge.
9. **Goldens were recorded in this container**, not on the VPS (plan 06 §15.3), the same as P7.

## 5. Catalog requests (build files)

Already made, in `ui/kit/build.gradle.kts` (kit files, existing catalog aliases, no catalog edit):
- `implementation(project(":ui:words"))` (plan 06 §13.5; ModuleRules allows it).
- `implementation(libs.navigationevent.compose)` (not visible transitively).

Wanted:
- None from the version catalog.
- For the l10n owner: a `Failure` → sentence mapper in `:ui:words`.
- A `strings_kit` key for the disabled reason of a working action. Today a no-callback action falls back to its label.

## 6. Open problems

- **Plan 06 §16.3 items not done:**
  - `api/<Part>.md` specs;
  - `KIT.md` rows (shared index; left for the kit owner to avoid conflicts with S1-KITA1);
  - `kit_capacity.json` entries and capacity scenes at 280 / 412 / 1600 dp × 1.0 / 1.3 / 2.0 × LTR / RTL;
  - previews per state;
  - `AccessibilityRulesTest`;
  - the `JETPACK_AHEAD.md` check.
- **Formatting:** `tool/format-changed.sh` does not exist yet, so the files are hand-formatted at the repo's 120-column
  style.
- **Not yet checked on an emulator:**
  - the predictive back gesture on Android;
  - the keyboard lifting sheet actions (`imePadding`);
  - the menu `Popup` position.
  The desktop tests drive the same `NavigationEventDispatcher`.
- **`KitField` focus ring:** it is drawn from focus state, but `KitTappable`'s own focus ring (LOOK-21) is still
  S1-KITA1's.
- **`KitUndoBar` placement:** the shell must place it once in the root overlay; it reads no `LocalKitBottomInset` yet,
  because `KitInsets` is not built.
- **Merge note:** `KitParts.kt` lost its `KitButton` block here. If S1-KITA1 moves `KitScreen` out and deletes
  `KitParts.kt`, the merge is a simple modify/delete.
