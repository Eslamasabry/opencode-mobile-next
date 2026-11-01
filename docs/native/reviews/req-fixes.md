# REQ review fixes

Date: 2026-11-02. Reviewed checkout baseline: `066a00be`.
Review source: `3bb7632e:docs/native/reviews/req.md` (the review file is absent from this checkout but available in local Git history).
Binding contracts: plan 01/03 and `INTEGRATE-W1.md` / `INTEGRATE-W2.md`.

All five MAJOR findings still applied at the baseline. None was already fixed by integration. The review contains no BLOCKER findings.
Only `native/engine/requests` production code and tests changed; no shared contracts or feature modules changed.

| Finding | Disposition | Implementation and proof |
|---|---|---|
| 1 MAJOR: cancelling the initiating caller strands the write | **Fixed** | The connection scope owns the write. Callers independently await its shared result. Abnormal job completion also completes the result and moves a remaining request to `NotAccepted(Refused(DispatchUncertain))`, without replay. Red-first tests: `B-AGENT-031 req-1 cancelling the initiating caller does not strand the shared write`; `B-AGENT-031 req-1 connection cancellation completes the shared result as uncertain without replay`. |
| 2 MAJOR: settlement races claim/capture and dispatch | **Fixed** | Capture the immutable entry in the same atomic update that publishes `Sending`; never look it up with `first` afterward. Run the write on the structural lane and check the claim before and after transport wake. Red-first tests: `B-PROTO-048 req-2 settlement at claim publication never loses the captured entry`; `B-PROTO-048 req-2 settlement during transport wake resolves both callers without dispatch`. The claim-publication test produced the reported `NoSuchElementException` before the fix. |
| 3 MAJOR: a location-scoped snapshot removes another project's ask | **Fixed** | Use the review's project-stream alternative: consume `events.project`, matching the scope of the pending-list snapshots. Other-project attention remains with its owner. Red-first test: `B-PROTO-048 req-3 project snapshots never admit or tombstone another projects asks`. It also changes directory and hydrates the previously excluded request, then sends its held answer once. |
| 4 MAJOR: a generic 404 silently resolves a listed request | **Fixed** | Silent timeout recognition requires the tag for the actual request kind and no conflicting request ID. Generic tags, wrong-kind tags and mismatched IDs go through pending-list reconciliation; a listed request remains retryable. Red-first tests: `B-CHAT-076 req-4 generic not found without an id keeps a listed request retryable`; `B-CHAT-076 req-4 wrong request-kind and mismatched-id errors do not resolve listed permissions`. |
| 5 MAJOR: invalid question answers reach the port | **Fixed** | Check prompt count, required answers, single-choice cardinality, duplicates, blank answers, offered labels and custom-answer permission. Check public admission and revalidate atomically when claiming a held/retried answer against updated prompts. Invalid data uses the existing flat `Failure.Rejected` with `QUESTION_INVALID_ANSWER`, following W2's invalid-value convention. Red-first tests: `B-CHAT-072 req-5 direct engine commands reject invalid question payloads without writes`; `B-CHAT-072 req-5 held answers are checked against updated prompts before claiming`. Positive coverage: `B-CHAT-072 req-5 valid optional custom multiple and multi-prompt answers still send`. |
| 6 MINOR: an invalidated Always-allow confirmation can reopen | **Rejected from this batch: severity scope only.** | The requested repair scope is BLOCKER and MAJOR. The current model still retains `local.confirm` while hiding its derived confirmation. This is an outstanding MINOR, not an already-fixed finding. The feature module was not edited. |

## Validation

Every Gradle invocation ran from `native/` under the shared `/home/eslam/Storage/tmp/oc-build.lock`, with:

```sh
JAVA_HOME=/home/eslam/Storage/tools/jdk-21.0.13+9 ANDROID_HOME=/home/eslam/Android/Sdk ./gradlew --no-daemon -Pkotlin.incremental=false :engine:requests:desktopTest
```

The checked-in `-Xmx2g`, two-worker and in-process Kotlin settings were retained. No overlapping build was launched by this task.

- Initial regressions against unchanged production: **85 tests, 5 failures**, one per MAJOR finding.
- Additional lifecycle regressions against original production: **84 tests, 4 failures**, including the claim-publication exception and connection cancellation.
- Expanded 404 regressions before their fix: **87 tests, 2 failures**.
- Direct question validation before its fix: **89 tests, 1 failure**.
- Updated held-question validation before its fix: **90 tests, 1 failure**.
- Final formatted source: **90 tests, 0 failures, 0 errors, 0 skipped**. The module's desktop production and test code compiled in this task.

Nine new regressions were observed red before the relevant fix; the tenth new test checks valid question shapes. Existing tests remain green.
`git diff --check` passes. The prescribed `tool/format-changed.sh` is absent; ktfmt 0.64 was applied to changed code blocks while preserving unrelated formatting.
No Android device, emulator, APK, iOS or live-server qualification is claimed.

## Local commits

The checkout's actual Git metadata is mounted read-only. A normal commit failed while creating its worktree `index.lock`.
The requested small commits were therefore made using isolated writable Git metadata at `/tmp/req-fix-commits.git`, based on the exact checkout baseline, and exported to `/tmp/req-fixes.bundle`.
The bundle contains the fixes and this report. Every commit subject ends in `[skip ci]` and carries both requested trailers.
The checkout's branch ref could not be advanced; the source edits remain visible in its working tree. Nothing was pushed.
