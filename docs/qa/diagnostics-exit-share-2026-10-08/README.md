# Recent app exits (FD1) and sharing saved crash reports (FD2) — evidence (2026-10-08)

Feature: two additions to Report a problem (App diagnostics), built on the BC
diagnostics gateway ([contract](../../design/BC-diagnostics-contract.md),
backend `8644d15d`):

- **FD1:** "Recent app exits" lists Android's record of each time the app
  closed.
- **FD2:** saved crash reports can be previewed and handed to the system share
  sheet.

FD3 (battery pause) is not part of this slice.

## Setup

- Branch `fe/diagnostics-exit-share`, which follows `fe/plain-recent-errors`
  (`1e15a2ad`). It merges `feat/genui-fe` at `6e387668` (the BC backend) in
  `2a42a227`, and l10n was regenerated after that ARB merge.
- Widget tests and goldens only (pinned Flutter via `machine_lock.sh`). No
  emulator, no APK, no device share sheet.

## What was built

- `lib/ui/screens/exit_history_section.dart` (FD1). Rows come from
  `AppDiagnosticsGateway.exitHistory()` (limit 10, newest first). Each row is
  a plain category and its local time:
  - "App closed", "App updated", "App stopped"
  - "Phone needed memory", "App stopped unexpectedly", "Android ended the app"

  Opening a row shows Details (already open) with the fixed safe Summary,
  Reason code and Importance.
- The three non-row states are distinct:
  - **Unsupported:** "Not available on this phone" / "Android 11 and later keep
    this record."
  - **Empty:** "No app exits recorded yet".
  - **Read failure:** "Couldn't read recent app exits. Try again, or reopen the
    app." with Try again.

  A note says Android records each exit and nothing is sent automatically.
- `AppDiagnosticsScreen` gets the gateway from `diagnosticsGateway` (tests) or
  the app's `deviceDiagnosticsGatewayProvider`. Without either, both sections
  stay off the page. It reads exit history when the page opens and whenever the
  person returns to the app. When the history could be read, Android exits are
  dropped from the recent errors so each exit shows once. They stay there when
  the history is unsupported or failed.
- `lib/ui/screens/crash_report_share.dart` (FD2):
  - "Share saved crash reports" appears among the saved reports, only when the
    gateway's `shareSupported` is true and reports exist.
  - It builds the preview through the gateway (at most 20 records, 16 KiB). The
    sheet shows "N reports · size" and exactly that text, selectable.
  - Only the "Share report" tap calls `shareCrashReport`.
  - When the chooser opens, the sheet closes and nothing else is said. Opening
    the chooser is not delivery, so the app never says "sent".
  - **Stale preview** (consent changed, new evidence, store changed): rebuilt in
    place with "Saved details changed. Check the new report before sharing."
    Another tap is needed.
  - **Chooser fails to open:** "Couldn't open sharing. Try again." and the
    preview stays.
  - **Report cannot be built:** the reason shows on the page ("Saved crash
    reports are off…", "No saved crash reports to share.", "These details are
    unavailable…").
  - The button shows a working state and ignores repeated taps while a share is
    in flight.
- 25 new keys in `app_en.arb` / `app_ar.arb` (Arabic complete). Kit parts only.

## Images

Contact sheet: [contact_sheet.png](contact_sheet.png). It shows exits with one
opened, unsupported, the read failure, the crash reports section with Share,
and the share preview, in light and dark.

## Tests

| Command (pinned flutter, via machine_lock, `--concurrency=1`) | Result |
|---|---|
| `test/diagnostics_exit_share_test.dart` (new, 11) + `revamp/diagnostics_fd_golden_test.dart` (new, 10) + the diagnostics page set (`app_diagnostics_screen`, `chat_live_events`, `crash_reports_section`, `failed_job_report_ui`, `goldens/settings_golden`, `recent_errors_plain`, `redaction`, `revamp/crash_reports_golden`, `revamp/screen_settings_1_golden`, `revamp/screen_system_1_golden`, `settings_server_updates`, `setup_progress_report`, `v2_feature_gating`, `kit_ratchet`, `l10n_coverage`, `ui_glossary`) + backend `crash_diagnostics`, `crash_report`, `device_diagnostics_gateway`, `app_exit_history`, `app_diagnostics` | `04:48 +416: All tests passed!` |
| `flutter analyze --no-pub` | `No issues found!` |

Existing goldens did not change: none of those tests passes a gateway.

### Red proof

- Against the previous slice's screen and section (`1e15a2ad`): compile
  failure, because there is no `diagnosticsGateway` parameter.
- Four deliberate behaviour changes: share as soon as the preview opens, report
  a stale preview as a share failure, never drop Android exits from the recent
  errors, and show unsupported as empty. Result: 4 tests fail (`+7 -4`): empty
  vs unsupported, an exit shows once, only Share report hands it over, and the
  stale rebuild. After restoring the code, all 11 pass.

## Not covered

- A real Android share chooser and `ApplicationExitInfo` on a device (no
  emulator run in this lane).
- FD3 battery pause UI (another agent).
