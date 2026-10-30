# Scheduled prompts: a calm list and one creation sheet (2026-10-20)

Finish line: Scheduled prompts is one calm list (each schedule one compact row:
the prompt's words, one state line saying when it runs in plain words, a quiet
on/off switch) with one mint "New scheduled prompt" at the thumb, and one
bottom sheet that creates or edits a schedule: the prompt first, then when
(Once / Every day / Weekdays / Custom) on a time wheel, then where, and one main
action that names the moment ("Schedule for 9:00 AM").

Non-goals: the long-press "Send later" on the chat's Send (the chat library
belongs to another agent; described in the report), the run engine itself
(`lib/scheduling/runner.dart`, the worker, notifications), any server-side
scheduling.

Decision: `docs/design/vision-decisions-2026-10-20.md` and the vision page,
section "Scheduled prompts": pick "A list + B sheet".

## Technical-honesty audit (before editing)

Sources read: `lib/scheduling/models.dart`, `recurrence.dart`, `controller.dart`,
`store.dart`, `runner.dart`, `platform.dart`; the native half
(`ScheduledPromptScheduler.kt`, `ScheduledPromptRecovery.kt`,
`ScheduledPromptCalendar.kt`, `ScheduledPromptPolicy.kt`);
`lib/ui/screens/scheduling/*`; `ServerCapabilities.scheduledPrompts` and the
gateways that set it (OpenCode 2 `api2/gateway_mappers.dart`, Paseo
`paseo/gateway/capabilities.dart`, Codex `codex/gateway.dart`);
`docs/opencode2-protocol-notes.md` (no server-side schedule API: OpenCode 2
only admits prompts, so scheduling is the phone's own alarm engine).

The schedule lives on the phone. Android alarms (exact when allowed, a
WorkManager fallback otherwise) wake a bounded Dart worker that sends the
prompt to the saved server with the app closed. Every gateway that can take a
cold authenticated prompt says so with `scheduledPrompts`; the screen opens
only behind `canSchedulePrompts` (that flag, a saved non-transient profile and a
project). Nothing is gated on the flavor.

| Element of the pick | Real source | Verdict |
| --- | --- | --- |
| The prompt's words as the row title | `ScheduledPrompt.prompt` (first non-empty line) | Real. |
| "Every weekday at 9:00" | `ScheduleRecurrence.weekdays(hour, minute)` | Real. |
| "Every day at 9:00" | none today: the engine knows once / weekdays / interval only. An interval of 24 h keeps an absolute anchor and drifts an hour at a daylight-saving change (Egypt changes on 2026-10-29). | Built: a `daily` recurrence kind, wall-clock like weekdays, in both halves of the engine (Dart `nextScheduledOccurrence`, Kotlin recovery `next` and the timezone rebase). This is the only engine change; it needs a native release, not a Shorebird patch. |
| "Tomorrow 9:00" (once) | `ScheduleRecurrence.once(at)` and `nextRunAt` | Real. |
| Custom | `ScheduleRecurrence.interval(hours 1–168)` | Real, as "Every N hours" (the engine's only other rule). No day-of-week picker: the engine has none. |
| A time wheel | local state; saved as hour/minute or the once instant | Real. A wheel in 15-minute steps (plus the saved minute when it is not on a step). |
| Where it runs: project · server | `ScheduledPrompt.projectPath` (its folder name) and the saved profile (`serverDisplayName`) | Real. The server is the same for every row of the screen (one profile per screen), so it is said once, in the bar's line, with the project; a row names its project only when it differs. |
| This conversation / a new one each run | `ScheduledPrompt.sessionId` (null = new conversation each run) | Real. Offered only when the screen was opened from a conversation. |
| Quiet on/off switch | `ScheduledPromptsController.setEnabled` | Real. A finished one-time schedule has nothing to switch on (no next run): its trailing action is Delete instead. |
| Last result | `ScheduledPromptRun` history (`status`: ran / failed / skipped / running; fixed `errorCode`) | Real, worded honestly: `ran` means the server accepted the prompt (on a phone server it also means the reply finished), so the word is "Sent", never "Done". A failed or skipped run says "Didn't go out" / "Skipped" with the plain reason in the sheet. The last result folds into the one state line. |
| "Needs the phone awake? It will wake it." (B) | `setExactAndAllowWhileIdle` when exact alarms are allowed, WorkManager otherwise; a network connection is still needed | Rejected as written (it promises more than Android guarantees). One honest line instead: "Runs with the app closed. Android may start it a few minutes late." and, when exact alarms are not allowed, one quiet "Allow exact timing" line on the list. |
| Upcoming / History tabs (A) | — | Rejected by the pick. |
| "Last ran: yesterday · Done" (A) | — | "Done" is not recorded for remote servers; replaced by "Sent yesterday". |
| Morning summary (today's screen) | `ScheduledPromptDigest` | Kept as one compact switch row under the list. |
| Agent picker, missed-run choice, project path (today's editor) | `ScheduledPrompt.agent`, `missedPolicy` | Removed from the surface: a new schedule keeps the agent the app or the conversation already uses, and a missed run runs once when the phone can (the engine's default). Existing schedules keep what they saved. |
| Run history list (today's screen) | `ScheduledPromptRun` | Folded: the last run is on its schedule's row and in its sheet, where "Open the conversation" opens the run's conversation. |

## What changed

| Before | After |
| --- | --- |
| A "+" in the bar, rows with pause and delete icon buttons under each, a run history list, the morning summary with its description, battery and notification notices | One grouped list: each schedule one compact row (its words, one state line "Every weekday at 9:00 AM · Sent yesterday", a quiet switch); the morning summary as one switch row; one muted line only when exact timing is off ("Runs may start a little late · Allow exact timing"); one mint "New scheduled prompt" pinned at the thumb |
| A full editor page: prompt, a Repeat picker sheet, a time row opening a picker sheet, conversation and agent pickers, a "Timing and delivery" fold with missed-run policy and two notices, Save schedule | One bottom sheet: "What should it do?", Once / Every day / Weekdays / Custom, a time wheel (with a day column for Once, an interval column for Custom), one "where" row ("neon-breaker · Laptop", the conversation as its line; a step chooses this conversation or a new one), the last run with its reason and its conversation (when editing), one honest line, and one main action naming the moment ("Schedule for 9:00 AM", "Schedule for tomorrow, 9:00 AM", "Schedule every 3 hours") |
| Delete as an icon under every row | Delete schedule in the sheet (and the one trailing action on a finished one-time row), after a confirmation |
| Loading said "Running" | The screen's one loading bar |

Kit: `KitWheel` / `KitWheelColumn` (new: roll a value under a band; drag, tap,
arrow keys, screen-reader increase/decrease, reduced motion); `KitSegmented`
gains `fitLabels` (each segment as wide as its words, so four choices of
different lengths stay on one line; other screens unchanged, their goldens
pass); `KitSwitchRow` gains `onOpen` / `openLabel` (a row that opens on tap
while only the switch toggles, with a quieter accent-tint track; other switch
rows unchanged, their goldens pass).

Engine: a `daily` recurrence kind in Dart (`models.dart`, `recurrence.dart`)
and Kotlin (`ScheduledPromptPolicy.nextDaily`, recovery `next`, the timezone
rebase in `ScheduledPromptCalendar`). Nothing else in the run engine changed.

## Visual passes against the concept

Concepts: `/home/eslam/Storage2/launch-footage/screen-concepts/b2-scheduled-prompts/concept-a.png`
(list) and `concept-b.png` (sheet). Renders:
`tool/capture/vision_scheduled_test.dart`, dark at 412x915, 2x pixels, blurred
shadows, frozen aurora.

1. Pass 1. The sheet's four segments did not fit their equal shares and fell
   into a radio list; the wheel's values drew as blocks (its text style lost the
   theme's face); switches were solid mint (three mint pills beside the one main
   button); no back arrow (the page was not pushed); the two groups touched.
   Fixed: `KitSegmented.fitLabels`, the wheel reads `KitText.styleOf`, quiet
   accent-tint switch track, the capture pushes the page, a section gap.
2. Pass 2. Row dividers were inset for a leading icon the rows do not have;
   unselected segments kept room for an invisible check, so their words sat off
   centre; the wheel band's hairline was hard to see; the honest line wrapped to
   a one-word second line; at 2x text the main button's words were cut by its
   icon. Fixed: `leadingIcons: false`, the check only on the chosen segment,
   a two-pixel accent band, shorter copy, no icon at large text.
3. Pass 3. The exact-timing note wrapped its action under the words; Arabic
   showed a Latin comma and Latin digits in "كل 3 ساعات"; the Arabic sheet title
   used a different word for "prompt" than the page title. Fixed: shorter note,
   `scheduleDayAt` with the Arabic comma, locale digits in the hour plurals,
   "مطالبة مجدولة جديدة".
4. Pass 4 (screen standard). Four text sizes in the sheet's first view: the
   wheel now uses the title and headline sizes (three sizes in all).

What the concepts show and the build does not, on purpose: the large page
title (the app's top bar is one standard), a card per row and the mint
schedule text (rejected by the pick), Upcoming / History tabs, "Done" (not
recorded), "It will wake it" (not guaranteed), Weekly (the engine has no
day-of-week rule; Custom is every few hours).

Images, one full phone screen each, in
`/home/eslam/Storage2/launch-footage/vision-scheduled/`:
`main-dark.png`, `main-light.png`, `empty-dark.png`, `exact-timing-off-dark.png`,
`sheet-new-dark.png`, `sheet-new-light.png`, `sheet-once-dark.png`,
`sheet-custom-dark.png`, `sheet-edit-last-run-dark.png`,
`small-320-text2-dark.png`, `small-320-text2-sheet-dark.png`,
`arabic-rtl-dark.png`, `arabic-rtl-sheet-dark.png`.

## Checks (2026-10-20, branch feat/vision-scheduled)

- `TZ=UTC flutter test` on `test/scheduled_prompt_*_test.dart`,
  `test/kit/kit_wheel_test.dart`, `test/kit/kit_segmented_test.dart`,
  `test/kit/kit_row_parts_test.dart`, `test/goldens/kit/kit_segmented_golden_test.dart`,
  `test/goldens/kit/kit_row_parts_golden_test.dart`, `test/kit_ratchet_test.dart`,
  `test/ui_glossary_test.dart`, `test/l10n_coverage_test.dart`,
  `test/gesture_audit_test.dart`, `test/search_index_test.dart`: pass.
- `test/ui_ledger_coverage_test.dart` "every screen file has a home": fails on
  three files this branch did not touch (`chat/permission_ask.dart`,
  `settings/settings_group_page.dart`, `this_phone/this_phone_answer.dart`; other
  vision slices). The Scheduled prompts part of the ledger is updated.
- The daily rule's model test fails with the daily branch reverted (checked).
- Kotlin: `ScheduledPromptPolicyTest` (11 tests, two new) compiled with
  kotlinc and run with JUnit: OK. The recovery and calendar halves need
  `org.json` (Android) and were not run; no Gradle run, no emulator.
- `flutter analyze`: no issues.
