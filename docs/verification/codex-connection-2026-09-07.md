# Codex connection verification — 2026-09-07

## Result

The local Codex CLI `codex-cli 0.153.4` accepted the app-server contract in an
isolated temporary environment. Experimental transport, controller and UI integration are available in the
development source. Final checks are recorded below; this is not a release
or a live-model verification.

The server used temporary configuration and a synthetic connection token.
No model, provider, `turn/start`, or account action was invoked.

## Proven contract

The server was started with:

```text
codex app-server --listen ws://127.0.0.1:4141 --ws-auth capability-token --ws-token-file <temporary-token-file>
```

The token file was synthetic, private (`0600`), and used only inside the
temporary proof directory. The sanitized protocol result was:

| Check | Result |
| --- | --- |
| Missing bearer token | HTTP 401 |
| Wrong bearer token | HTTP 401 |
| Correct bearer token | WebSocket 101 |
| `initialize` followed by `initialized` | Result accepted; returned server identity fields |
| `thread/list` for the temporary project | Result accepted; zero threads |

The app's endpoint policy therefore supports `ws://` for loopback/tunneled
connections and `wss://` for remote connections, with bearer authentication.
Remote clear-text `ws://`, URL credentials, URL paths, and URL queries remain
outside the supported endpoint shape.

## Attempt record

Earlier isolated listener attempts ended before protocol requests. The final
proof completed and its owned listener was stopped. The adjacent
[protocol summary](codex-connection-2026-09-07-real-cli.json) retains only
sanitized protocol status and result fields.

## Product limits recorded for integration

The current gateway surface is text-only chat, history list/resume, one-shot
approvals, and cancel/interrupt. Reconnect resumes tracked history and clears
in-memory approval state. Since the local app-server schema provides no
pending-approval listing/recovery operation, a pending approval at reconnect
must be reviewed in the computer Codex client. No background cross-profile
attention, background file access, or terminal capability is claimed.

## Integration checkpoint

The final source analyzer is clean. Focused tests cover storage/rollback,
backend and folder selection, authentication recovery, uncertain delivery,
streaming/approval/cancel, navigation gating, drafts and editor submission.
The complete recursive serial run covered **223 files**: **2,186 passed,
three skipped, six failed**, in 470.08 seconds. The six failures were five
legacy editor interaction tests and the localization ratchet. The editor tests
needed focus dismissal before scrolling and visibility before tapping; new
labels were moved into ARB without raising the ratchet baseline. Both editor
files then passed all 18 tests, and the localization gate passed.

The final source full suite has **not** been rerun after these corrections.
Do not combine the earlier full result and later focused results into a claimed
full pass. Repeat the frozen full suite on the destination machine before
promoting this checkpoint. The [structured run summary](codex-connection-2026-09-07-tests.json)
retains the original candidate fingerprint, manifest and failures.

Root's rendered Android review found and corrected:

- A stale saved-but-failed error after a successful connection retry.
- An attachment tooltip and persistent approval action unsupported by Codex.
- Completed response items incorrectly clearing the running turn's Stop action.
- Saved token re-entry falling into unrelated local-server failure guidance.
- Existing Codex Save & connect returning to the list instead of Home.

Earlier synthetic Android checks exercised authentication rejection/retry,
history, text response, allow-once/reject, new conversation, disconnect and
history resume, explicit cancellation, and restart persistence. RPC counts
confirmed zero new turns on an offline send attempt or automatic reconnect;
a retained draft was sent only after an explicit tap. Corrected late UI paths
must be confirmed against the final APK before claiming final-build coverage.

Local validation uses Flutter 3.47.2 / Dart 3.13.2 with the matching source tag
and standard debug engine, JDK 17, and Android SDK API 37. It does not use the
release-pinned Shorebird engine or signing workflow. The device used is an
Android 14 x86_64 emulator, not a physical handset.

The reproducible synthetic protocol fixture is checked in under
`tool/qa/codex_fixture/`. It contains public test values only and invokes no
provider, account or command execution. Its README describes fresh-start use.

## Final transfer checks

Source checkpoint: `499c0e1a0aaaddd6edd9b1bd899b79133a687f87`.

- Final affected UI/recovery/localization group: **30 tests passed**.
- Corrected first-run and profile-editor regression files: **18 tests passed**.
- Main analyzer: clean. SDK analyzer: clean; **47 SDK tests passed**.
- Changed Dart files: **41 checked, zero formatting changes**; diff check clean.
- Standard-engine debug APK: built, installed over the emulator's debug app,
  and launched successfully with the saved Codex connection and folder.
- Final APK: Stop remained visible after a response item completed while the
  turn was running; an explicit tap sent exactly one interrupt and ended busy.
- Final APK: the approval review showed Allow once and Reject, with no
  unsupported persistent grant option. Root inspected the rendered screens.
- No Flutter-error or Android-runtime-error entries were present in the
  collected emulator log. This is scoped runtime evidence, not exhaustive QA.

APK SHA-256:
`bcf29b46a2dc8cc236ae456f0d2ba71d52f3861eb75f96a6d5dbc130f4b14afb`.

Synthetic screenshots: [running turn](../qa/codex-connection-2026-09-07/final-running-after-item.png)
and [supported approval](../qa/codex-connection-2026-09-07/final-supported-approval.png).
The reusable fixture's fresh-start smoke also passed initialization/history,
normal text, approval, interrupt, rejected authentication and forced disconnect.

Still pending: a full suite on the corrected source, complete final-APK
connection-editor/token-reentry/reconnect/large-text sweep, physical-device
checks and an explicitly authorized real provider turn. No release claim is
made. Transfer this checkpoint and continue those checks on the next machine.
