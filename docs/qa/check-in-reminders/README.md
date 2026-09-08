# Check-in reminders verification

Candidate: `feature/run-checkin-reminders`, completing preserved WIP `91bb994`.
The feature remains opt-in, reports sampled busy intervals rather than an
actual run-start timestamp, and opens the existing guarded session route only
after a tap. No prompt or request reply is sent by a reminder.

## Local checks

Pinned Flutter 3.47.2, Java 17 and this worktree's own package configuration.
`flutter pub get` resolved dependencies, then exited 1 at the existing Windows
plugin symlink prerequisite. No system setting was changed. Subsequent Flutter
commands used `--no-pub`. Localization generation and changed-file formatting
completed.

All tests used `flutter test --no-pub --concurrency=1`:

| Files | Final result |
| --- | --- |
| `test/profile_monitor_check_in_test.dart` | 15 passed: observed intervals, gaps, failed polls, idle/absence, restart, durable pre-dispatch claim, refused storage, failed delivery, rule toggles, batch progress, deletion and workspace changes |
| `test/profile_monitor_screen_test.dart`, `test/profile_monitor_navigation_test.dart` | 6 passed: default-off monitoring, duration save, 320 px/2.5× controls, active-server Inbox, source preflight and opaque private native payload |
| `test/profile_monitor_test.dart`, `test/profile_deletion_test.dart`, `test/background_live_test.dart`, `test/l10n_coverage_test.dart` | 64 passed |
| `tool/capture/check_in_reminders_test.dart` | 10 passed, all PNGs inspected |
| `flutter analyze --no-pub` | No issues found, 21.0 seconds |
| `git diff --check` | Passed |

The first reminder file could not compile because an original WIP fixture
passed a permission model where a monitored request was required. This was
corrected. A new storage-refusal test initially inspected an aged snapshot;
an additional observation made its due-row assertion current. The affected
checks passed afterward. Two analyzer findings were corrected, including the
interval decode expression; all 15 reminder tests ran again with that final
expression. No unresolved check failure remains in this focused set.

The durable claim deliberately prioritizes at-most-once dispatch attempts:
a crash or native refusal after the saved claim can miss a notification. The
in-app due row remains. Storage writes omit session titles; lock-screen copy
is fixed in the native `checkin` branch. Native source was inspected and the
Dart channel payload was tested, but no Android notification-delivery or
native compilation claim is made here.

## Production widget captures

Synthetic saved-server/session data rendered through real screens, fonts and
themes. These are widget captures, not device screenshots. Narrow settings
use 320 px and 2× text; the separate control test reaches 2.5×.

| State | Light | Dark |
| --- | --- | --- |
| Rule and due interval | [Light](controls-light.png) | [Dark](controls-dark.png) |
| Narrow duration and observed interval | [Light](narrow-light.png) | [Dark](narrow-dark.png) |
| Active-server Inbox | [Light](inbox-light.png) | [Dark](inbox-dark.png) |
| Unavailable observation | [Light](unavailable-light.png) | [Dark](unavailable-dark.png) |
| Default off | [Light](off-light.png) | [Dark](off-dark.png) |

The due interval stays separate from pending permission/question/form counts.
Unknown observations hide busy rows. Large-text content scrolls vertically;
the duration remains full-width and the interval copy wraps without overflow.
Independent review, integration, full-suite coverage and native release work
remain separate coordinator gates.

## Coordinator integration correction

Integrated above `eced3c8` from immutable feature `d5ecbfc`. Independent review
found two issues: a foreground return during a held persistence write could
still post a notification, and the sampled-time wording claimed a duration
floor that intermittent work cannot establish. The corrected source rechecks
foreground/background admission after the route and durable claim writes,
retains a claim already saved, and says "Busy at checks spanning N min".

Pinned `flutter gen-l10n` succeeded. Serial `--no-pub --concurrency=1` checks
passed all87 cases in `profile_monitor_check_in`, `profile_monitor_screen`,
`profile_monitor_navigation`, `profile_monitor`, `profile_deletion`,
`background_live` and `l10n_coverage`. This includes both held-write foreground
regressions and the visible sampled-span wording. Analyzer was clean (31.5s).
All10 capture cases were regenerated; the narrow light, dark controls and dark
Inbox images were inspected. The independent correction reread passed at source
level. These remain widget checks, not Android notification-delivery evidence.

Visual follow-up found during integration: the Activity empty state can still
say "All clear" beneath a due check-in row, because reminders intentionally do
not increase the pending-request count. The coordinator is correcting that
empty-state presentation before the final installed-app walkthrough.
