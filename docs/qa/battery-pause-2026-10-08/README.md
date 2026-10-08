# FD3 "Paused to save battery" notice: evidence record (2026-10-08)

Branch `fe/battery-pause`, based on `feat/genui-fe` at `8644d15d` (merge of
`sol/bc-diagnostics`, the FD3 backend). Feature commit `33425f63`. Current
`feat/genui-fe` was merged in (`8f358bf8`), then the compact rework followed
in its own commit.

## Finish line

When Android stopped the app's background connection (6 h / 24 h `dataSync`
limit, battery restriction, app closed while it ran, or an unexplained stop),
the person sees one plain line in the app status slot with
**Resume background connection**. Resume goes through
`AppDiagnosticsGateway.resumeBackground()`. Non-goal: no backend, native,
chat, Report a problem or App diagnostics changes.

## What was built

| Part | File |
| --- | --- |
| Notice owner: which pause, hidden or not, Resume in flight, refresh on return to the app | `lib/state/background_pause_notice.dart` |
| The status line (`KitStatus`, kind `appStopped`) | `lib/ui/widgets/background_pause_notice.dart` |
| Wiring into the app status scope above every route, and the order of the two lines | `lib/ui/widgets/app_connection_status.dart` (`appStoppedLines`) |
| Copy, English and Arabic (11 keys, `backgroundPause*`) | `lib/l10n/app_en.arb`, `lib/l10n/app_ar.arb` + gen-l10n |

The line uses kit parts only: `KitStatus`, `KitAction` and the existing
`KitStatusLineSlot`. No new kit part was needed.

### Placement and the one-notice rule

The line goes in the same app-wide status slot (`AppConnectionStatusScope`)
that already shows the app-exit notice ("Android stopped the app"). It uses
the same `KitStatusKind.appStopped`, and the slot shows only one condition
(ties keep the first), so the pause line and the app-exit line never stack.

Order (`appStoppedLines`):
- **The phone's server is still down:** the app exit shows first. It is the
  bigger event, usually the cause of the pause, and it says what is being
  restarted. The pause line shows once the exit line is dismissed or goes
  away.
- **The phone's server is back:** the exit line is only a recap (it folds
  away by itself 15 s later), while the paused background connection still
  needs a tap. The pause shows first.

Connection problems still win over both (a higher kind).

### States and copy (compact, after coordinator review 2026-10-08)

The owner reported that big blocks of text were spreading into every screen
from this slot. So the line is the reason sentence only (at most 2 lines at
phone width), with one compact inline action. Nothing sits under it.

| State | Line | Inline action | More (⋯) |
| --- | --- | --- | --- |
| Time limit | Background connection paused at 3:10 PM to save battery. | Resume (read as "Resume background connection") | Open Keep running settings |
| Battery restricted | Background connection paused: battery use is restricted. | Resume (or Open Keep running settings when `canResume` is false) | same |
| App closed | Background connection stopped when the app closed at 3:10 PM. | Resume | same |
| Unknown stop | Background connection stopped for an unknown reason. | Resume | same |
| Resume pending | Resuming background connection… (tone progress) | none | none |
| Resume failed / unavailable | Couldn't resume. Open Keep running settings. (tone failure) | Open Keep running settings | Resume background connection |

The consequence paragraph ("Replies and questions won't reach you…") and the
failure paragraph were removed. Kit addition: `KitAction.semanticsLabel`,
passed through `KitButton.fromAction`, so a compact label keeps its full
target for screen readers (test in `test/kit/kit_action_test.dart`).

- A time is given only where the contract says `at` is the stop time (time
  limit, app closed). For a restriction or an unknown stop it is only when
  the app noticed, so the line claims no time.
- `busy` (a permission prompt already deciding) is not shown as a failure;
  the plain Resume stays.
- A confirmed start (gateway state active, not paused) makes the line go
  away. A screen reader hears "Background connection resumed." A failure
  changes the line's message, which the status line announces itself.
- While the start is pending, the line has no action. A second request
  joins the first and Android is asked only once.
- Dismissal: the close button hides that pause (`reason@at`) for good, also
  after a restart (`oc.backgroundPauseDismissed`, app-global like the
  receipt, not profile data). A later, different pause shows again. There is
  no close button while Resume is pending.
- The receipt is re-read when the app comes back to the foreground. The
  startup read stays in `restore()`.

## Tests (pinned Flutter 3.47.1, `tool/qa/machine_lock.sh`, `--concurrency=1`)

New file `test/background_pause_notice_test.dart` (12 tests) mounts the real
`AppConnectionStatusScope` with a fake gateway (`test/support/fake_pause_gateway.dart`):
- nothing shows while the connection runs, is off, or the phone cannot tell
- each reason is one line with nothing under it; the inline Resume has the full target in semantics
- Resume calls the gateway once (a second request joins), the pending line has no action, then the line goes away and the result is announced
- an unconfirmed resume keeps the pause, offers Open Keep running settings, Resume again under More
- busy is not a failure
- `canResume: false` offers Keep running instead
- the app exit first, the pause after the exit is dismissed (one `KitStatusLine`)
- the Arabic copy shows in Arabic
- `appStoppedLines` order, before and after the server is back
- a dismissed pause stays hidden after a restart, and a new pause shows
- a failed resume is remembered only for that pause
- returning to the app re-reads the receipt

Golden file `test/goldens/background_pause_golden_test.dart` (14 shots).

Results on the final tree:
- `test/background_pause_notice_test.dart test/goldens/background_pause_golden_test.dart test/kit_ratchet_test.dart test/ui_glossary_test.dart`: `00:48 +85: All tests passed!`
- Affected by the scope change (they host `AppConnectionStatusScope` or the full `OcApp`): `work_tab_golden_test`, `work_parts_golden_test`, `app_exit_recovery_test`, `background_notification_navigation_test`, `credential_unreadable_status_test`, `revamp/coord_main_golden_test`, `revamp/cred_status_golden_test`, `app_lifecycle_test`, `builtin_server_autostart_test`, `share_routing_test`, `server_profile_reentry_test`, `launch_shortcut_routing_test`, `session_link_routing_test`, `app_text_scale_test`, `appearance_effects_test`, `product_ui_regression_test`, `launch_session_shortcut_routing_test`, `builtin/setup_finisher_lifetime_test`: all pass.
  - On the first run, `app_lifecycle_test` and 2 tests in `builtin_server_autostart_test` failed. The notice used `AppLifecycleListener`, which asserts on lifecycle jumps that those tests make. It now uses a plain `WidgetsBindingObserver`. Rerun: `00:15 +26: All tests passed!`
  - `team_gate_answer_test` "one alert per gate, ids and fixed copy only, dismissed on settle" fails, **and it fails the same way on the untouched base `8644d15d`** (checked in a temporary detached worktree, since removed). This branch does not touch it. Flagged for the coordinator.
- `flutter analyze --no-pub`: the 4 findings in the new files are fixed. The final changed files report `No issues found!`
- Arabic coverage: every English key has an Arabic value (0 missing).

### Red proofs

- Before: on the base, the new test file does not compile (no `backgroundPauseNoticeProvider`, no copy keys).
- Mutations, all applied together and then restored with `git checkout`:
  R1 the scope passes no pause line; R2 dismissal is not saved;
  R3 a failed resume is not remembered; R4 the order ignores `serverBack`.
  Result: `00:04 +2 -10: Some tests failed.` Among the failures: the dismissed-pause restart test (R2), the failed-resume test (R3), the order test (R4), and every scope test that expects the line (R1).
  After restoring, the same file passes again (`+85` run above).

### Compact rework (after review)

- Test set on the merged and compacted tree: `background_pause_notice_test`, `goldens/background_pause_golden_test` (goldens regenerated, then compared), `kit/kit_action_test`, `kit/kit_status_line_test`, `kit_ratchet_test`, `ui_glossary_test`, `app_exit_recovery_test`, `goldens/work_tab_golden_test`, `goldens/work_parts_golden_test`, `app_lifecycle_test`, `builtin_server_autostart_test`: `01:37 +224: All tests passed!`
- Full `flutter analyze --no-pub`: `No issues found!`
- Red proofs: two mutations, then restored.
  - The kit button drops the `semanticsLabel` wrap: the kit test "a compact label keeps its full target for a screen reader" fails.
  - A supporting line goes back under the paused line: the notice test "each reason is one compact line…" fails.
  - Result: `00:28 +58 -2: Some tests failed.`

## Screens

`contact_sheet.png` (compact version): light on top, dark below, 412x915 frames cropped to the
top. Full frames are in this folder and match the goldens:
`background_pause_{time_limit,restricted,user_stopped,interrupted,resuming,resume_failed,time_limit_ar}_{light,dark}.png`.

## Accessibility

- The line is one live region. Its message (reason, resuming, failed) is
  announced when it changes. Success is announced once through
  `SemanticsService` because the line goes away.
- The actions name their target: the inline "Resume" is read as "Resume
  background connection" (`KitAction.semanticsLabel`), and "Open Keep running
  settings" is spelled out. While the start is pending there is no action
  to tap twice.
- Arabic is laid out right to left (see `time_limit_ar`).

## Privacy / security

No credentials, server text or native strings reach the copy. The reason is
a fixed enum and the time is formatted locally. The only new stored value is
the dismissed pause id (`reason@millis`), app-global. Resume runs only on a
visible tap. There is no automatic retry and no battery exemption request.

## Not verified here

No emulator or device run (not allowed in this lane). Real Android timeout,
battery restriction and OEM swipe behaviour come from the backend lane's
host fixtures, not from this record.
