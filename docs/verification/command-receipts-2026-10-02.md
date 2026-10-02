# Command receipts backend verification — 2026-10-02

Candidate: `1dfc7482` on `codex/command-receipts`; clean tree when the full gate began. Finish line: supported queued prompts dispatch once, survive restart as metadata receipts, and reconcile without another send; OpenCode 2 session creation has an optional backend owner seam. No UI, native, external service, signing, push or release.

## Checks

Pinned Flutter: `/home/eslam/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter`.

- `flutter pub get` explicitly run once successfully. All final verification uses `--no-pub`.
- Changed Dart files formatted with pinned Dart `format --language-version=3.10`.
- `tool/qa/machine_lock.sh analyze -- <flutter> analyze --no-pub`: clean, no issues.
- `tool/qa/machine_lock.sh test -- <flutter> test --no-pub --concurrency=1 <focused manifest below>`: **243 passed, 3 skipped, 0 failures** in 43 seconds. The three skips are existing optional voice screenshot captures.
- `git diff --check`: clean.
- Source review found and fixed admission-after-preflight and profile/gateway authority gaps; regression tests prove no POST after those fences close.

Focused manifest: `test/pending_command_journal_test.dart`, `test/command_receipts_test.dart`, `test/command_receipt_transport_test.dart`, `test/command_receipts_queue_test.dart`, `test/session_creation_receipts_test.dart`, `test/command_receipt_fence_test.dart`, `test/offline_queue_test.dart`, `test/reconnect_hardening_test.dart`, `test/session_selection_sync_test.dart`, `test/revamp/queued_prompt_move_test.dart`, `test/managed_runtime_switch_preflight_test.dart`, `test/staged_revert_workflow_test.dart`, `test/prompt_transport_test.dart`, `test/voice_reply_pipeline_test.dart`, `test/phone_project_engine_gateway_test.dart`.

Compatibility fixtures explicitly disable command receipts unless they implement a correlated receipt send; their existing behavioral assertions remain intact. New receipt fixtures opt in and check actual IDs, pending admission, exact lookup, lost-response/restart recovery, stale/deleted scope, rejected writes, bounded storage and wrong-tab/profile behavior.

## Full serial gate

**PASS** against code candidate `1dfc7482`: all **1,016 files** observed and completed; **15,440 passed, 29 existing skips, zero failures**, in 2673 seconds (44m 33s). Six serial chunks all exited zero. The recursive filesystem scan also found exactly 1,016 files; none were omitted. No implementation/test changes occurred during this gate. Later changes only add/update documentation.

| Chunk | Files | Passed | Skipped | Failed | Seconds |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 | 169 | 1623 | 12 | 0 | 331 |
| 2 | 169 | 3575 | 2 | 0 | 545 |
| 3 | 170 | 3611 | 6 | 0 | 388 |
| 4 | 169 | 2321 | 1 | 0 | 440 |
| 5 | 169 | 1806 | 5 | 0 | 376 |
| 6 | 170 | 2504 | 3 | 0 | 593 |

Each command was `<flutter> test --no-pub --concurrency=1 --reporter=json <chunk files>`, under one `machine_lock.sh test` slot. Manifest, chunks, candidate revision, completed-file ledger, per-chunk JSON logs and statistics are archived at `/home/eslam/Storage/tmp/claude-tmp/claude-1000/-home-eslam-Storage-Code-oc-app/87a8d964-900c-48f3-a841-cd593d87ac4c/scratchpad/codex/a-evidence/full-serial/`. Exact skipped tests are recorded in `/home/eslam/Storage/tmp/claude-tmp/claude-1000/-home-eslam-Storage-Code-oc-app/87a8d964-900c-48f3-a841-cd593d87ac4c/scratchpad/codex/a-evidence/skipped-tests.json`; no skip was added by this branch. The final focused and analyzer logs are archived alongside them. Local originals remain under `build/command-receipts-full/`.

## Product and safety boundary

[Frontend contract](../design/command-receipts-contract.md), [source feasibility matrix](../design/command-receipts-feasibility.md).

Queued supported prompts are wired to the journal and existing reconnect flush. Direct online sends and existing session creation are not rerouted; the controller/UI owner must wire their documented seams. Permission/revert/shell and Codex/Paseo remain on existing behavior. Missing lookups remain uncertain; Retry never repeats receipt-bearing mutations. Receipt confirmation is admission, not assistant/tool completion. No public runtime access or new host/backend.

Per-profile metadata uses `oc.pendingCommands.<profileId>`; profile deletion closes/drains the writer before the generic key sweep. No prompts, headers, URLs, provider credentials or raw errors are persisted in receipts. Profile scope is hashed. Tombstones never expire or silently evict; 1000 entries / 1 MiB stops new admission. SharedPreferences and ordinary HTTP cannot prove exactly-once execution under device rollback/data loss. The pinned Android `shared_preferences_android@2.4.27` legacy path uses `commit()` for setString (`android/src/main/kotlin/io/flutter/plugins/sharedpreferences/LegacySharedPreferencesPlugin.kt:56–66`); this app uses legacy SharedPreferences, not its async apply path. This is source evidence, not native process-death/fsync proof. No live server, phone, native build, signing, screenshots, deployment or release was exercised.
