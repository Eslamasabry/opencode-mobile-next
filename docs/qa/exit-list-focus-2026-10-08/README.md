# Recent app exits: problems first — evidence (2026-10-08)

Trigger: device check on APK 2196 ([before_apk2196.png](before_apk2196.png)).
7 of 10 rows were "App updated". Routine exits buried the real stops and pushed
the rest of Report a problem down.

## Setup

- Branch `fe/exit-list-focus` from `feat/genui-fe` at `c272a3fc`.
- Widget tests and goldens only (pinned Flutter via `machine_lock.sh`). No
  emulator, no APK.

## What changed (`lib/ui/screens/exit_history_section.dart`)

- **Problem exits only, by default.** These are crashes (including "stopped
  responding"), low memory, Android ending the app, and exits Android cannot
  explain. The last one is `REASON_UNKNOWN`, which the gateway files as
  "normal"; it now reads "Closed for an unknown reason".
- **At most 5 problem rows**, then "Show all N".
- **Routine exits fold under one quiet row**, "N routine closes (updates, you
  closed it)". These are updates and permission/package changes, the person
  closing or stopping the app, and an exit the app made itself. The row opens
  to list them with category and time.
- **No problem exits:** one line, "No unexpected closes recently.", with no
  empty card. The routine row still follows when there are routine exits.
- **Times** are "Today 08:37" or "Yesterday 21:42" by the local calendar day,
  otherwise the app's `shortDateLabel` plus the time ("Oct 6 08:05"). The time
  uses `DateFormat.Hm` for the locale. A 23- or 25-hour day around a clock
  change still counts as one day.
- The screen now asks the gateway for 50 exits (the contract maximum) instead
  of 10, so problems are found among the routine updates.
- Unsupported and read-failure states are unchanged.
- Copy:
  - 6 new keys in `app_en.arb` / `app_ar.arb`, with Arabic plural forms for the
    routine row.
  - The old "No app exits recorded yet" key was removed, because the empty
    history now uses the one line.
  - `AppDiagnosticsScreen.clock` (tests only) fixes "Today" for tests and
    goldens.

## Images

Contact sheet: [contact_sheet.png](contact_sheet.png). The first image is the
APK 2196 screen before the change. The others are the same kind of history
after it: the problem rows (crash opened to Details), the routine row opened,
and no problems. Light on top, dark below.

## Tests

| Command (pinned flutter, via machine_lock, `--concurrency=1`) | Result |
|---|---|
| `test/exit_list_focus_test.dart` (new, 7) + `test/diagnostics_exit_share_test.dart` | `00:04 +18: All tests passed!` |
| `revamp/diagnostics_fd_golden_test.dart` (`--update-goldens` for `exit_history_*` only, 4 new images) | `+14: All tests passed!` |
| Page set: the two above + `app_diagnostics_screen`, `crash_reports_section`, `recent_errors_plain`, `goldens/settings_golden`, `revamp/crash_reports_golden`, `revamp/screen_system_1_golden`, `revamp/screen_settings_1_golden`, `failed_job_report_ui`, `redaction`, `settings_server_updates`, `setup_progress_report`, `v2_feature_gating`, `kit_ratchet`, `l10n_coverage`, `ui_glossary` | all pass except 7 tests outside this page (below) |
| `flutter analyze --no-pub` | `No issues found!` |

Failures outside this slice (files not touched here):

- **`screen_settings_1_golden`, saved permissions × 5:** the goldens still show
  "webfetch". The page now renders "Use Fetch page" since the plain tool names
  change merged into `feat/genui-fe`.
- **`v2_feature_gating`, two "pending form" tests:** a pending 2 s timer from
  `state/connection/chat_feed.dart` `_feedScheduleRefresh`. This comes from the
  connection-layer refresh change, which this slice does not touch.

### Red proof

- Against the branch's base section and screen: compile failure (no `clock`,
  `exitTimeLabel` or `isProblemExit`).
- Three deliberate behaviour changes: every exit counts as a problem, no cap of
  5, and days counted by elapsed hours instead of the calendar. Result: 6 of 7
  tests fail (`+1 -6`). After restoring the code, all pass.

## Not covered

- A device run of the new list (needs a new APK).
