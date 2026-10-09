# BA12 — Claude reconnect state

Branch `sol/ba-claude-reconnect`, based on `feat/genui-fe` at
`4658af763`. Scope: Paseo gateway state; no UI, native, authentication,
capability, storage-format or generated SDK changes.

Finish line: after a dropped reply, a fresh running agent snapshot restores
busy/Stop and removes interruption; a fresh idle snapshot finishes the reply.
Non-goal: device qualification, builds, publication, or replaying missed events.

## Cause and repair

`ConnectionController.refreshSessions()` starts its status read before its
session page. OpenCode 1 reads `/session/status` and OpenCode 2 reads active
sessions over HTTP. Paseo instead copies its existing `_statuses` map, then
its subsequent fresh agent page silently replaces that map. Session hydration
also changes this cache without notifying the controller. Thus the controller
can retain stale idle while its gateway already knows the agent is running.

Agent snapshots now publish status transitions through the existing
`session.status` event path. The controller's existing revision fencing prevents
an earlier cached status read from overwriting the newer event. Reconnect
already refetches agent snapshots and clears pre-drop turn guards; it continues
to use refetch, without replay. Authoritative idle completes open message records.
Sub-agent changes no longer assign the status before the emitting helper's
comparison (which suppressed their events). The existing two-idle watcher
retains ownership of its debounce; its first idle read is not published early.

Existing OpenCode 1/2 network status reads do not share Paseo's silent-cache
problem. Their shared controller reconciliation remains unchanged.

Contract: [BA12-contract.md](../../design/BA12-contract.md).

## Verification

The final six-test regression file failed 6/6 on the base implementation
with the production patch temporarily reverted (exit 1), then passed 6/6
with the patch restored. See [negative-control.txt](negative-control.txt).
The final unchanged candidate passed all **110 tests across eight files**
(exit 0), with no skips: [focused-tests.txt](focused-tests.txt).
[Source and test hashes](candidate.sha256) identify the checked candidate.

| File | Passed |
| --- | ---: |
| `paseo_reconnect_state_test.dart` | 6 |
| `paseo_gateway_test.dart` | 37 |
| `paseo_idle_busy_test.dart` | 19 |
| `paseo_correlated_prompt_test.dart` | 7 |
| `paseo_chat_feed_source_test.dart` | 15 |
| `connection_v2_requests_test.dart` | 18 |
| `reconnect_hardening_test.dart` | 6 |
| `file_size_ratchet_test.dart` | 2 |

The fake daemon's running/idle reconnect cases use the real Paseo gateway,
controller, ChatScreen, KitTurn and KitComposer. Both clear the local busy hint
after the drop; running must restore it and remove interruption, idle must
finish. Other cases cover stale cached idle, direct hydration, missing terminal
events, and sub-agent status delivery. No sleeps are used in the regressions;
widget fake time drives reconnect and UI transitions.

Tests use pinned Flutter 3.47.1 only through
`OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- <flutter> test --concurrency 1 <files>`.
The final command listed all eight files above with `--concurrency 1` in one
locked process. Pinned Dart format (`--language-version=3.10`), source diff
whitespace and contract-link checks pass. Scoped analyzer passed with no issues: `flutter analyze --no-pub lib/paseo test/paseo_reconnect_state_test.dart`, under the same one-slot lock. See [analyzer.txt](analyzer.txt).
No full suite, Gradle, emulator, build, signing, push, release or live account action.

Crash leftover: inspected the masked `fx-installed.jpg` from the prior QA
batch and committed it separately as `9a6f7785d` on `sol/ba-2198-cert` before
creating this branch. It is historical install evidence, not new qualification.
