# Composer second-eye fixes

Date: 2026-11-01. Source checkout: `native/fix-compe-2026-11-01` at `066a00be`.
Reviewed `docs/native/reviews/compe.md` from commit `3bb7632e`: that review is absent from this sparse checkout's current revision.
The binding contracts are plan 01/03, `native/AGENTS.md`, and INTEGRATE-W1/W2's flat core contract.

**Result: all eight applicable MAJOR findings fixed; the BLOCKER was already fixed.**
Code changes are confined to `native/engine/composer`; no shared contracts changed.
The final composer desktop tests pass: **121 tests, 0 failures, 0 errors, 0 skipped**.
Composer's desktop and Android compilations also pass.

## Per-finding disposition

Test names below omit the runner's `[desktop]` suffix.

| Finding | Disposition and implementation | Regression evidence |
|---|---|---|
| 1 BLOCKER — removed core failure types | **Already fixed** by `6bd1c439`, integrated before this checkout. Composer uses `Failure.StorageRefused` and core's `DartJson`; no `Failure.Storage`/`StorageProblem` imports remain. Current desktop/Android compilation and all tests confirm the integrated contract. | Existing composer suite; no new fix or red test needed. |
| 2 MAJOR — autosave cancels itself and loses pending input | **Fixed.** Cancel only the debounce timer, serialize saves, and keep the pending snapshot's identity until successful acknowledgement. Cancellation leaves the snapshot retryable; an older save cannot settle newer input. | `DraftAutosaveTest`: `B-STATE-100 compe-2 debounce acknowledges a suspending save without cancelling itself`; `B-STATE-102 compe-2 leaving waits for an active save and acknowledges its snapshot`; `B-STATE-102 compe-2 cancelled flush keeps its snapshot available for retry`. All three failed before the fix and pass after. The newer-snapshot test also passes. |
| 3 MAJOR — saved prompts persist unsanitized input | **Fixed.** Sanitize the whole copied snapshot before publishing JSON or payloads. Refuse sensitive routing identities and URLs; redact text, filenames, MIME metadata and reference fields. Decode supported UTF-8 textual data URLs, redact, and re-encode changed text. Preserve clean representations and binary media. Refuse a sensitive data-URL MIME header before re-encoding. | `StashTest`: `B-SEC-151 saved copy metadata and decoded text payloads are sanitized before publication`; `B-SEC-151 sensitive saved prompt routing identities and URLs are refused before publication`. Both failed before the fix and pass after. Assertions inspect persisted JSON and decoded vault content. The MIME-header edge case separately failed before its final guard and passes after. Tests construct a fake marker at runtime. |
| 4 MAJOR — committed deletion discards Undo on cleanup failure | **Fixed.** Validate shelf metadata before mutation. Once metadata removal commits, acknowledge deletion and return Undo even if payload collection fails. Keep the durable owner marker so subsequent stash/removal/profile cleanup retries collection. | `StashTest`: `B-STATE-085 committed deletion returns Undo when payload collection fails and cleanup retries`. Failed before, passes after; checks missing metadata, retained cleanup ownership, exact attachment Undo and eventual collection. Existing refused-commit and corrupt-shelf tests still pass. |
| 5 MAJOR — filesystem read/list errors escape `Outcome` | **Fixed.** Every `FilePayloadStore` operation translates expected `IOException` to the existing `PayloadStoreException`; the vault returns flat `Failure.StorageRefused`. Cancellation remains uncaught. A Java desktop-test fixture supplies deterministic filesystem failures because kotlinx-io seals its Kotlin `FileSystem` interface. | `FilePayloadStoreTest`: `COMPE-5 filesystem listing IO failure is a refused draft save`; `COMPE-5 filesystem metadata IO failure is a refused draft save`; `COMPE-5 filesystem source IO failure is a refused draft save`. All three threw I/O exceptions before the fix and return typed failures after. `COMPE-5 filesystem cancellation propagates from payload operations` also passes. |
| 6 MAJOR — failed photo publication leaves orphan bytes | **Fixed.** Discard always collects the recorded owner's photo root, regardless of optional reference/path fields. Profile cleanup collects the requested owner even without a pending record, or when another owner has the pending record. Failed collection retains the record for retry. | `PhotoRecoveryTest`: `B-STATE-088 COMPE-6 discard after failed photo publication collects unreferenced bytes`; `B-STATE-088 COMPE-6 failed orphan cleanup keeps the photo record for retry`; `B-STATE-088 COMPE-6 profile cleanup collects orphan photos without a pending record`; `B-STATE-088 COMPE-6 profile cleanup preserves another profile pending photo`. All four failed before and pass after. Tests recreate the store after failed publication. |
| 7 MAJOR — a legacy draft edit loses attachments | **Fixed.** Resolve the effective previous draft using the same legacy fallback as restore. Recover and copy its attachments into the destination owner before removing legacy metadata. Refuse an incomplete migration and retain the original source. | `DraftsTest`: `B-STATE-101 compe-7 editing a legacy draft migrates and preserves its exact attachments`. Failed before, passes after; exercises legacy restore, text-only edit, restart and exact file recovery. `B-STATE-101 compe-7 refused legacy migration keeps its original attachments recoverable` also passes. |
| 8 MAJOR — file-only share inherits unrelated saved text | **Fixed.** Either initial text or initial attachments selects the replacement opening. File-only shares return no saved text and do not recover saved attachments for that opening. | `DraftsTest`: `B-STATE-100 compe-8 file-only shares replace saved words for that opening`. Failed before, passes after; asserts the whole restore result. |
| 9 MAJOR — stash acknowledges temporary attachment URLs | **Fixed.** Route every attachment through vault validation before publishing stash metadata. Unsupported schemes refuse the complete stash; partial data publications are collected and caller input remains available. | `StashTest`: `B-CHAT-088 temporary attachment schemes refuse the entire stash without acknowledging partial input`. Failed before, passes after for both temporary schemes. |
| 10 MINOR — older tests miss claimed mutations | **Rejected from this change's scope:** the request targets BLOCKER and MAJOR findings. This does not reject the review's test-quality observations. | No change to those three existing scenarios. New regression tests independently cover the requested MAJOR findings. |
| 11 MINOR — no-project starter behavior | **Rejected from this change's scope:** MINOR and unrelated to the requested persistence/compilation fixes. This does not claim the existing starter behavior satisfies the catalog. | No starter change. |

## Verification

Every Gradle invocation ran from `native/`, under the shared `/home/eslam/Storage/tmp/oc-build.lock`, one build at a time:

```bash
JAVA_HOME=/home/eslam/Storage/tools/jdk-21.0.13+9 \
ANDROID_HOME=/home/eslam/Android/Sdk \
./gradlew --no-daemon -Pkotlin.incremental=false \
  -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m -XX:+UseParallelGC -Dfile.encoding=UTF-8' \
  --max-workers=2 \
  :engine:composer:compileKotlinDesktop \
  :engine:composer:compileAndroidMain \
  :engine:composer:desktopTest
```

The explicit resource flags override the host's larger user-level Gradle heap. Composer has no Android host-test target; its common tests run on desktop and its production code compiles for Android.

| Stage | Result |
|---|---|
| Draft regressions against original production | 109 tests, 5 failures: three autosave, legacy edit, file-only share. Log: `/tmp/compe-baseline.log`. |
| Remaining regressions before stash/payload/photo fixes; drafts already fixed | 59 selected tests, 11 failures: three filesystem, four photo, four stash. Draft regressions pass. Log: `/tmp/compe-red.log`. |
| MIME-header regression before its final guard | 1 selected test, 1 failure. Log: `/tmp/compe-mime-red.log`. |
| Final command above, after all production/test/formatting changes | **BUILD SUCCESSFUL**, desktop and Android compiler tasks executed; **121/121 tests passed**, none skipped. Log: `/tmp/compe-final.log`. |
| Formatting and whitespace | All 11 changed Kotlin files pass ktfmt 0.64 `--kotlinlang-style --dry-run --set-exit-if-changed`; `git diff --check` passes. The formatter jar was SHA-256 checked against plan 03's pin. `tool/format-changed.sh` is absent here, so the pinned formatter was invoked directly on changed files only. |

The first filesystem-test attempt failed compilation because Kotlin forbids an external implementation of sealed `FileSystem`; the Java test fixture corrected that harness before the behavioral red run. No production behavior was changed to accommodate the harness.

These are local module checks. No emulator, device, live-server, release or iOS qualification is claimed.

## Commits and sandbox limitation

The sandbox mounts this checkout's shared Git metadata read-only. The first `git add` failed creating `index.lock`; consequently **the original checkout branch remains at `066a00be` and its edits are unstaged**.

To preserve the requested small commits, they were created in the writable temporary repository `/tmp/compe-fixes-git`, based on the exact original tree, without changing the source checkout's metadata:

| Commit | Scope |
|---|---|
| `5a8dc964` | Finding 2: autosave and its tests. |
| `37159f6f` | Findings 7/8: legacy attachments, share opening and their tests. |
| `329a124e` | Finding 5: filesystem translation and fault tests. |
| `ecf0a10b` | Finding 6: owner-based photo cleanup and tests. |
| `8311e291` | Findings 3/4/9: stash sanitization, deletion acknowledgement, attachment validation and tests. |
| Following documentation commit | This report. |

Every new subject ends with `[skip ci]` and every commit has the two requested trailers.
All six commits are exported as `/tmp/compe-fixes.bundle`, with branch `native/fix-compe-2026-11-01` and prerequisite `066a00be`. The bundle can be imported from a shell with writable repository metadata. The code files and this report also remain present in this checkout. **Nothing was pushed.**
