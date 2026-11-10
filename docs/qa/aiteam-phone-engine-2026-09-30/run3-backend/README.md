# Run 3 backend repair and live acceptance

Finish line: project creation gives its typed refusal or imports a committed scratch repo, then the real GLM-5.3 planner/worker/checker produce a dev merge and a confirmed, durable promotion. Non-goal: UI copy/screens and another device or physical-phone claim.

## Reproduction

Exact signed APK 2082 was installed with `adb -s emulator-5554 install -r` on OC_API35 under the shared emulator lock, with existing data preserved. Its certificate matches the known local release signer. Turn-on reached Ready; New project / Start planning reproduced the generic save-failed error. Metadata-only inspection showed `my-app/.git` had no objects/branch commits. The failed create left `retired/edit-1790793073442151-0.json` while the SQLite command table had no accepted create. No engine token or provider key was read onto the host.

Device-proven causes: (1) proot's `--link2symlink` represents committed loose Git objects as `.l2s.tmp_obj_*` chains, which the native nofollow snapshot rejected; (2) an unborn Git repository cannot seed canonical main/dev; (3) failed import cleanup permanently retired the form's repo ID, poisoning corrected retries; (4) HTTP errors were flattened into `engineUnavailable`/`saveFailed`. The source-root mapping is correct. All four are addressed. No provider key or engine bearer token left the app.

## Verification

Final focused Rust crate gate: **154 passed, 0 failed, 0 ignored** (serial, ignored proofs included). Dart focused gate: **70 passed**; analyzer **clean** after the test-only lint repair. QA release + androidTest compiled with packaged hashes verified. Wrapper checks: **16 passed**. Counterfactual repository retry regression FAILS at retirement assertion on `1afc1ee3`.

First live committed scratch attempt: protection PASS, create/import FAIL. Device metadata positively shows proot `--link2symlink` wrote every loose Git object as an in-directory `.l2s.tmp_obj_*` chain. Native nofollow snapshot rejects it. This confirms the committed-repository cause separately from the unborn-repository/retry defect. The fix materializes only loose/allowed packed Git-object aliases, using the already-pinned parent dirfd. Link parents must lexically match that same object directory, including the system-managed Android app-data alias; target strings never select a new directory. Hops/bytes/cycles are bounded. All decoded ODB hashes are verified before import/fetch, with 64 MiB/object and existing total/count bounds. Config/HEAD/refs/hooks/alternates receive no exception. Nine focused tests PASS; the positive observed-chain import test FAILS on `1afc1ee3` with `unsafe_repository_path`.

The next attempt stopped before model dispatch because the test runner used `data/state.sqlite3` instead of the real `data/oc.teamEngine.<profile>/state.sqlite3`. The runner now has a positive real-workspace read and a negative unscoped-path control. Unknown chat observations still post `known:false`; static internal QA reasons are preserved. This correction changes only the test APK.

## Returned static reason catalog

These are existing static Rust store/repository reasons, now preserved for authenticated command errors as well as durable refused receipts. Names use the existing casing. No raw OS/Git exception, config, path or credential is forwarded. Transport/protocol/native codes remain separately typed.

### Repository

`confirmation_required`, `divergent_import`, `import_binding_mismatch`, `invalid_commit`, `invalid_id`, `invalid_path`, `invalid_proot_object_link`, `invalid_receipt`, `invalid_repository`, `invalid_request`, `legacy_receipt_scope_unknown`, `linked_repository`, `merge_conflict`, `overlapping_roots`, `promotion_not_fast_forward`, `promotion_recovery_required`, `promotion_superseded`, `receipt_encoding`, `repository_busy`, `repository_changed`, `repository_empty`, `repository_exists`, `repository_git`, `repository_io`, `repository_object_hash_mismatch`, `repository_retired`, `repository_too_large`, `repository_unavailable`, `request_id_conflict`, `shared_repository_objects`, `stale_dev`, `stale_main`, `stale_task`, `symbolic_ref_refused`, `symlink_refused`, `task_changed`, `task_not_descendant`, `task_rewritten`, `unsafe_repository_config`, `unsafe_repository_metadata`, `unsafe_repository_path`, `unsafe_source`, `unsafe_task_tree`, `worker_binding_mismatch`, `worker_exists`, `worker_reset_refused`.

### Store

`approveSpecFirst`, `chargingUnsupported`, `checkerMustBeReadOnly`, `chooseBudget`, `chooseExecutionMode`, `confirmationRequired`, `cursorExpired`, `dependencyCycle`, `invalidCommand`, `invalidCursor`, `invalidFixRounds`, `invalidJob`, `invalidJobPatch`, `invalidJobTransition`, `invalidPlacement`, `invalidPlan`, `invalidProfile`, `invalidRepositoryReceipt`, `invalidRequestId`, `invalidReviewLevel`, `invalidRole`, `invalidServer`, `invalidSpec`, `invalidTaskPatch`, `invalidUsage`, `jobNotFound`, `missingCriteria`, `missingDependency`, `missingProjectDetails`, `needsReconciliation`, `planAlreadyRunning`, `planPhaseInvalid`, `planTaskTitleRequired`, `profileDeleted`, `projectBusy`, `projectNotFound`, `projectPaused`, `projectStopped`, `promptAlreadyDispatched`, `quickTaskNeedsOneRepo`, `repoNotFound`, `requestIdReuse`, `roleInUse`, `roleNotFound`, `sessionAlreadyRecorded`, `staleJobStage`, `staleRepositoryRefs`, `staleRevision`, `storageCorrupt`, `storageUnavailable`, `taskAlreadyDone`, `taskIdAlreadyUsed`, `taskNotFound`, `taskNotPaused`, `unsafeStoragePath`, `unsupportedAction`, `unsupportedServer`, `usageRegression`.

### API command envelopes

`boundaryUnavailable`, `commandInvalid`, `confirmationRequired`, `crossRepoPromotionUnavailable`, `deleteFailed`, `executionUnavailable`, `heartbeatInvalid`, `projectMissing`, `queryInvalid`, `receiptUncertain`, `repoInvalid`, `reposRequired`, `repositoryCleanupPending`, `repositoryUnavailable`, `requestIdRequired`, `staleRevision`, `storeUnavailable`.


Planner proposals now require a nonempty task title (`planTaskTitleRequired`). The planner prompt provides the authored JSON schema explicitly; intake normalizes runtime defaults and checks role/repo/server/phase placement and dependency validity transactionally before publishing `needsPlanApproval`. A missing-title proposal rolls back without changing the workspace or job. This fixes the live GLM planner/approval contract mismatch.

Phase intake also rejects malformed types, missing titles and duplicate IDs with `planPhaseInvalid`; it strips model runtime fields and forces `accepted:false`. The planner receives the configured implementation-role catalog and a role enum. Job update failures preserve their static store reason.

## Commands and candidate

Run on `build/aiteam-phone-engine`, after `517cdcc4`, using the pinned Flutter 3.47.1, JDK 17, Android release instrumentation and the shared emulator/machine locks. Only `adb -s emulator-5554` was used, with OC_API35. Exact APK 2082 first reproduced the error; the temporary same-version backend QA build retained its certificate and data. Both native ABIs were rebuilt and staged. UI files were not changed.

```sh
PINNED_FLUTTER=/home/owner/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- cargo test --manifest-path engine/phone/Cargo.toml --locked -- --test-threads=1 --include-ignored
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- "$PINNED_FLUTTER" test --concurrency=1 test/phone_project_engine_gateway_test.dart test/team_project_controller_test.dart test/phone_project_engine_controller_test.dart test/phone_engine_connection_review_test.dart test/builtin_phone_engine_test.dart
OC_TEST_SLOTS=1 tool/qa/machine_lock.sh analyze -- "$PINNED_FLUTTER" analyze
python3 -m unittest tool.qa.tests.test_phone_engine_acceptance
python3 tool/qa/verify_phone_engine_apk.py --manifest android/app/src/main/assets/aiteam-engine-manifest.json --source-root . --apk build/app/outputs/apk/release/app-release.apk
tool/qa/phone_engine_acceptance.sh --serial emulator-5554 --server http://127.0.0.1:4097 --model zai-coding-plan/glm-5.3 --package io.github.eslamasabry.opencode_mobile --stable-app-qa --allow-model-spend --timeout-seconds 900
```

Build/test scratch and target directories were on Storage. Rust proof tests were serial and included the normally ignored boundary tests. This is focused coverage, not the full Flutter suite. Hash verification covered all six packaged native files and the manifest source digest. The source-digest/hash gate passed after the final planner contract change.

Final-candidate live retry reached plan approval, then the checker returned an explicit fenced JSON verdict followed by explanatory prose. The strict whole-message parser returned `structuredOutputInvalid`. Intake now accepts exactly one JSON/untagged fence with surrounding prose, rejects extra fences or object/array delimiters outside the block, and never searches prose for a JSON object. Existing criteria/findings validation still determines merge eligibility. Regression covers the observed shape, ambiguous/malformed/plain-prose refusals and wrong-criterion rejection. The subsequent complete live retry of this parser candidate passed, as recorded below.

Structured JSON also rejects duplicate decoded object keys at every depth, including escaped equivalent keys, so conflicting `findings`/`status` fields cannot use last-wins overwrite. Raw and fenced duplicate-key regressions pass.

## Primary UI mappings for this repair

| Code | Meaning |
| --- | --- |
| `repository_empty` | Make the repository's initial commit before importing it. A rejected import can now be corrected and retried using the same form/repo ID. |
| `invalid_proot_object_link` | The Git-object alias is outside the strictly accepted proot representation. |
| `repository_object_hash_mismatch` | A decoded Git object does not match its advertised hash. |
| `unsafe_repository_path` | A linked path outside the permitted Git-object aliases was refused. |
| `repository_retired` | This ID belongs to a deleted repository authority; choose a new repository ID. Old ambiguous retirement records are not resurrected. |
| `planTaskTitleRequired` | The planner/task proposal needs a nonempty title. |
| `planPhaseInvalid` | A phase has missing/invalid fields or a duplicate ID. |
| `invalidPlan` / `invalidPlacement` / `missingDependency` / `dependencyCycle` | The proposed plan does not fit the configured roles, repositories, phases or dependency graph. |
| `structuredOutputInvalid` | No single valid JSON verdict was returned; model prose alone is not a verdict. |
| `staleRevision` | The command's project revision is no longer current. |
| `confirmationRequired` | The app's explicit confirmation is required. |

Other existing authenticated command refusals retain the static codes catalogued above. There are no raw OS/Git/parser/provider errors in the UI result.

## Final QA artifact SHA-256

| Artifact | SHA-256 |
| --- | --- |
| Temporary stable QA APK (2082) | `fdc5aeea275fcc15a48ce4eada4442e1f096193b3bfe5024178b1035c0bffa07` |
| Release androidTest APK | `31f766a288b050bc91b55a12d819cb6dfb814f9cf673769ed378e91761186430` |
| arm64 engine | `9fa48a6f0c9fdc4819679e108b95d43315a4ec240820bf46aafa64c4937c3edd` |
| x86_64 engine | `31239b3fe7aea7a2402ded7a25bcd66396c61ddcd801f2add46a09cd52f2b6a0` |
| arm64 sandbox | `aa0dbb276e30f654eba48a75c580b0ac3bf1125306b944e3e455208e78cf7277` |
| x86_64 sandbox | `f6acbae80479f901535cdae5c2c7cec51107d1b5fde0beebd0ef6c37ba7e90e6` |

The QA APK certificate matches original APK 2082 / local release certificate `1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`. This is temporary emulator QA, not a published release. [Packaged hash checks](packaged-hashes.log) and [focused check summaries](focused-checks.log) contain no credentials or model response text.

## Final live result (completed 2026-10-01 Asia/Dubai)

The exact final staged/parser bundle passed on OC_API35. Actual assistant message records for all three owned sessions confirm **zai-coding-plan/glm-5.3**; planner and task job rows are durably `completed`. The worker/checker sessions are distinct. No provider key, engine token or existing person-project data was exported. A bounded part of the owned failed QA checker response was inspected during diagnosis; no response text was saved in the committed evidence. QA profile: `qa_4792c7ec4fda466c8ff51b71d78ec456`.

| Step | Result | Evidence |
| --- | --- | --- |
| Engine start / signed exact-binary protection | PASS | Native startup + live authenticated health, proot tier |
| Committed scratch import / create | PASS | Canonical main and dev equal seed |
| Real planner session | PASS | Durable running/completed events and actual GLM message model |
| App plan approval | PASS | Normalized one-task proposal accepted |
| Real worker session | PASS | Dispatched session; committed task branch |
| Real checker session | PASS | Distinct session; exact criterion result met, no findings |
| Merge to dev | PASS | Canonical dev advances, main remains seed; private SQLite merge receipt; committed file bytes and changed-file list verified |
| Promote without confirmation | PASS (refused) | `confirmationRequired`; canonical main unchanged |
| Confirmed expected-SHA promotion | PASS | Canonical refs match; applied private-file + SQLite receipt survives replay |
| Stable server restoration | PASS | Existing native restart controls restore the server |

[Sanitized live step log](live-acceptance.log) and [durable job/model summary](durable-job-summary.log) are committed. Earlier rejected attempts remain separately documented above; their coverage is not substituted for this final-candidate pass. API34 and a physical phone were not retested in this run. Proot remains ptrace path translation, not a kernel boundary, as authorized by the owner.

Cleanup PASS: exact original APK 2082 was restored with preserved data and byte-for-byte SHA-256 `c69a3bec27cb02a1b22e4b6a0fb4beccd9903904597184e4aa49ad8db658b4ad`. The owned OC_API35 emulator was stopped; its PID is gone and the shared emulator lock is released. No push, physical-phone installation or release occurred.
