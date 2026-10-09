# BA: Claude cold start after app update

Branch: `sol/ba-cold-start-claude`, based on `feat/genui-fe` at `02670b857`.
Contract: [BA-cold-start-contract.md](../../design/BA-cold-start-contract.md).

Finish line: an admitted helper start or a confirmed live helper does not show a stopped-agent/error banner; an opened chat adopts the helper's selection, with a conversation-specific display fallback during startup. Native start admission and permission decisions are unchanged.

## Behavior and coverage

- A start acknowledgement retains a 30-second readiness window. Every three seconds the controller checks the helper and refreshes rows. Readiness closes the window; a helper that stays down still exposes Resume afterward. Disposal/owner changes cancel the timers.
- Helper liveness overrides a stale per-agent process row. Source retry intent survives the unavailable row. Generic agent-feed failure is suppressed during startup/confirmed helper liveness; failures from other servers remain visible.
- Busy/idle status changes no longer invalidate concurrent metadata hydration. Metadata/selection mutations retain their revision fence. Fake Paseo and both OpenCode event shapes cover this distinction.
- The model chip uses the current authoritative selection, otherwise a bounded (200 conversations) profile-scoped display cache. `oc.sessionDisplayModels.<profileId>` is swept on profile deletion, including the `.agents` backend. A confirmed server default clears the fallback. The cache never supplies a send/permission decision.
- Initial connection shows the selected approval mode; actual offline/reconnecting state still shows paused. Unknown model copy uses the existing localized “Choose a model”. No new localization keys or kit components.

## Focused verification

All checks use the pinned Flutter binary:
`/home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter`.
One command runs at a time. Initial checks used `OC_TEST_SLOTS=1`; after the coordinator's SAFETY.md update, remaining checks use `OC_TEST_SLOTS=2`.

The final source passed these 179 distinct focused checks:

| Test file | Passed |
| --- | ---: |
| `phone_agents_controller_test.dart` | 123 |
| `approval_mode_chip_test.dart` | 16 |
| `session_selection_sync_test.dart` | 15 |
| `profile_deletion_test.dart` | 17 |
| `paseo_reconnect_state_test.dart` | 8 |

The final five-file batch passed all 179 tests with `OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test -- <pinned-flutter> test --concurrency 1 <files> --name '^(?!deletion closes auth, setup, host and feeds before ProfileStore$|clearing saved sign-ins closes clients and asks for a restart$)'`. See [final-tests.txt](final-tests.txt). The existing approval fixture now disposes its controller in the test body, before Flutter checks pending timers.

The two excluded cleanup timeouts were reproduced on the unmodified BA14 base and recorded in [BA14 evidence](../BA14-2026-10-09/README.md). They remain outside this slice. The previously failing resumed-conversation title test now passes because metadata hydration survives status events.

The first run failed on the early stopped notice and initial-connection paused label. After correcting a project-less metadata fixture, the reverse-patch check produced eight failures and two passes: [revert-proof.txt](revert-proof.txt). Production files were restored byte-for-byte; the public display API remained as a non-caching stub only so the new tests could compile against the reverted code. Two subsequent edge cases also failed before their fixes: an overlapping start returning busy, and a phone backend with no gateway yet ([late-edge-red.txt](late-edge-red.txt)). Both pass in the final batch.

The analyzer is clean. Its command and output are in [analyze.txt](analyze.txt). After the green test batch, only analyzer-requested brace/super-parameter style fixes were made. The source/test manifest is [candidate.sha256](candidate.sha256).

This is focused fake-gateway/widget verification, not the full repository gate or owner-phone qualification. No Gradle, APK, emulator, push, or release was run.
