# Plain recent errors — evidence (2026-10-08)

Feature: the "recent errors" rows on Report a problem follow the no-raw-errors
rule. Each title is in plain words, chosen by where the error came from. The
time is the subtitle. The source id and the redacted message appear only under
the row's Details. The problem report payload is unchanged.

## Setup

- Branch `fe/plain-recent-errors` from `feat/genui-fe` at `c09fd449`.
- Widget tests and goldens only (pinned Flutter 3.47.1 via `machine_lock.sh`).
  No emulator, no APK.

## What changed

- `lib/ui/screens/recent_error_words.dart`: `recentErrorTitle` maps each
  source (and the Android-exit kind) to a plain title. It also holds
  `crashStoreSources`.

  | Source | Title |
  |---|---|
  | `flutter`, `widget` | A screen couldn't be drawn |
  | `sse`, `sse.*`, `events` | Lost the live connection to the server |
  | `crash.last` (native summary) | The app closed unexpectedly |
  | `android.exit` / Android-exit kind | Android closed the app |
  | `thermal`, `thermal.*`, `android.thermal` | Phone temperature changed |
  | `bootstrap`, `bootstrap-reset`, `notify-migration` | The app had trouble starting |
  | `report-problem` | Couldn't open saved problem reports |
  | anything else (`app`, `platform`, …) | Something went wrong |

- `AppDiagnosticsScreen`: the row title is the plain title (up to two lines),
  and the subtitle is the time plus "N occurrences" when it repeats. Opening a
  row shows a `KitDetailsFold`, already open: Source, then the redacted message
  and stack, with Copy all.
- The crash-record filter from the previous slice is narrower. It now matches
  only the sources the opt-in crash store writes (`crash.flutter`,
  `crash.platform`, `crash.widget`, `crash.native`, `crash.anr`). The previous
  `startsWith('crash.')` also hid the native `crash.last` summary that
  `AppExitRecovery` records. That summary is listed again, as "The app closed
  unexpectedly".
- 7 new strings in `app_en.arb` and `app_ar.arb`. Source and the crash titles
  reuse the existing keys.
- The report text (`ProblemReport.build`) is untouched. A test checks that it
  still has the raw messages and sources.

## Images

Contact sheet: [contact_sheet.png](contact_sheet.png). It shows the list,
one row opened to Details, and the list above Crash reports, in light and dark.

## Tests

| Command (pinned flutter, via machine_lock, `--concurrency=1`) | Result |
|---|---|
| `test/recent_errors_plain_test.dart` (new) + `app_diagnostics_screen_test`, `chat_live_events_test`, `crash_reports_section_test`, `failed_job_report_ui_test`, `goldens/settings_golden_test`, `redaction_test`, `revamp/crash_reports_golden_test`, `revamp/screen_settings_1_golden_test`, `revamp/screen_system_1_golden_test`, `settings_server_updates_test`, `setup_progress_report_test`, `v2_feature_gating_test`, `kit_ratchet_test`, `l10n_coverage_test`, `ui_glossary_test`, `crash_diagnostics_test`, `app_diagnostics_test` | `01:46 +359: All tests passed!` |
| `flutter analyze --no-pub` | `No issues found!` |

Updated goldens (all render this page):
- `test/goldens/settings_diagnostics{,_empty}_{dark,light}`. The two `_empty`
  images had been out of date since the Details gutter fix in fe/crash-consent,
  which was merged without them.
- `test/revamp/goldens/system_app_diagnostics_*` and
  `system_app_diagnostics_clear_sheet_*`.
- `system_report_problem_preview_1280x800_dark`.
- `crash_reports_off_with_errors_*`.
- New: `recent_error_details_*`.

### Red proof

The new test file was run against the screen as it was on `c09fd449`. 4 fail
(`+2 -4`): plain rows/no raw text, Details under the row, `crash.last` listed,
and Arabic titles. The title-map unit test and the report-payload test pass in
both versions, as they should. With the change, all 6 pass.

## Not covered

- Device run and screen-reader walkthrough.
