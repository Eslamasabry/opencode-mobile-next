# Sign-in foreground lifetime

Branch `sol/ba-signin-foreground`, from coordinator `feat/genui-fe ac8ddc92e`.
Finish line: the actual terminal sign-in path waits for Android foreground-service
admission, holds it through browser handoff and auth verification, and drains its
native process before releasing protection on cancellation or owner deletion.
UI foregrounding, signing into an account, changing background preferences,
new native bridges and APK/device execution are outside this offline slice.

The current UI calls `LocalTerminalSessions.startSignIn`; that PTY path bypassed
the existing native private-auth foreground supervision. It now uses the existing
`oc/background` service via a transient domain lease. Native `enable` takes no
server, provider or account arguments; active acknowledgment is mandatory.
The permanent background preference stays independent of transient claims.

Only the canonical main phone-account owner binds the foreground port. Terminal
cleanup is registered before admission, including pending launches. Replacement
admission waits for the old owner to drain; failed cleanup blocks account-home
deletion and remains retryable. No process is killed by pattern.

Service protection keeps the app process foreground while the browser's Activity
can be in front. It does not move the app UI over the browser or guarantee that
Android cannot terminate it. Native pause/time-limit events cancel leases without
automatically restarting a service the user or Android stopped.

## Regression evidence

- [terminal-negative-control.log](terminal-negative-control.log): four failures
  against the old terminal path: missing binding still launched, reservation was
  ignored, and cancellation/disposal failed to drain a late native ID.
- [background-negative-control.log](background-negative-control.log): the first
  admitted-service case fails against the unavailable foreground-port stub.
- [controller-negative-control.log](controller-negative-control.log): five runtime
  failures before owner binding/drain changes. An earlier attempt was intercepted
  by an in-progress background-part compilation error
  ([controller-initial-compile.log](controller-initial-compile.log)); that is not
  counted as regression evidence.
- [registry-chain-negative-control.log](registry-chain-negative-control.log):
  successive replacement skipped the oldest pending cleanup before the generation
  gate fix (expected no reservation, observed one).
- [background-stop-negative-control.log](background-stop-negative-control.log):
  both native stop exceptions and an active stop receipt incorrectly returned
  success before typed, retryable cleanup failure was implemented.

The first positive terminal run exposed two pending resize timers. Closing now
quiesces the shell synchronously, while native PTY removal and foreground release
remain ordered. The first broader controller run exposed 34 pending helper
heartbeat timers: captured feed sockets, secondary backends and local listeners
now retire promptly; auth/host teardown still waits for the sign-in drain.
The first new deletion assertions omitted the synthetic native-home marker needed
for ProfileStore's cleanup callback; adding that existing fixture preserves exact
cleanup ordering and retry assertions. Nine fake UI cases needed explicit fake
foreground admission after the production path began requiring a binding.
The two former `24 x 80` default-size snapshots now assert the correct canonical
profile, exact Claude sign-in command and positive starting dimensions: the
awaited admission allows terminal layout before native start. Retry and exact-ID
removal assertions stay unchanged. The fake loss stream uses fixture-local
cancellation/completion futures so native cleanup can be awaited in widget
fakeAsync; this changes no production scheduling. Account and copy assertions
stay unchanged.

## Focused checks

Pinned Flutter 3.47.1; pub get once on this branch. Every test command uses
`tool/qa/machine_lock.sh test -- <pinned-flutter> test --concurrency=1 ...`.
No parallel test processes, Gradle build or emulator session.

| Files | Result | Log |
| --- | --- | --- |
| agent_sign_in_foreground, local_terminal_signin_foreground, local_terminal, agent_sign_in_terminal | 43 passed | [terminal-focused-tests-final.log](terminal-focused-tests-final.log) |
| background_signin_foreground, background_live | 54 passed | [background-focused-tests-final.log](background-focused-tests-final.log) |
| phone_agents_controller (including five BB6 owner cases) | 105 passed | [controller-focused-tests-final.log](controller-focused-tests-final.log) |
| profile_deletion, agents_settings_placement, file_size_ratchet | 23 passed; the two UI files in this initial command needed fixture correction | [integration-focused-tests.log](integration-focused-tests.log) |
| agents_account_ui, agents_ui, agent_sign_in_terminal (shared fake admission) | 77 passed, including the 6 terminal cases rerun with the final fixture | [ui-fixtures-tests-final3.log](ui-fixtures-tests-final3.log) |

These are affected-file checks, not the coordinator's full-suite gate or Android
lifetime/device qualification. Contract: [BB6-contract.md](../../design/BB6-contract.md).
No Kotlin or lib/ui source edits, real account use, logout, matrix promotion,
app reinstall or device proof. APK2197 remains deferred by the coordinator's
explicit machine-pressure hold; BA10 + launch/low-storage device rows are batched
when that APK exists.


Final affected coverage: 296 unique tests across 12 files. The six terminal
widget cases ran twice; repeated passes are excluded from that count.
Frontend retry/disposal handling for typed cleanup failure is specified in the
contract for Claude; the backend retains cleanup and blocks owner deletion.

Scoped analyzer: all 16 changed production/test items (including the parent
connection library), clean in 8.7 seconds, machine-locked
`flutter analyze --no-pub <items>`:
[analyze-focused-final.log](analyze-focused-final.log). The initial style-only
multiple-underscore issue was corrected without adding ignores. Pinned Dart
language-3.10 formatting and diff checks pass. The file-size ratchet passes:
phone_agents.dart is 1430 lines, routes is 498; no budget was raised.

Local commits: contract `87c64ff5d`, foreground/terminal `fec5d898c`, followed by
the owner-binding/teardown and evidence commit at this branch's final HEAD.
Branch is ready for backend merge review. No push or device deployment occurred.
