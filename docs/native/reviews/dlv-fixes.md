# DLV review fixes — 2026-11-01

Reviewed starting tree: `066a00be` on `native/fix-dlv-2026-11-01`.
Source review: `3bb7632e:docs/native/reviews/dlv.md` (the review is absent from this branch's starting tree;
read from local Git history). `:engine:delivery` was unchanged from the reviewed `45a3b0bb` revision.
All eight BLOCKER/MAJOR findings still applied. None was already fixed by integration; none was rejected.
The adjacent MINOR finding was fixed with the Stop changes.

Write set: `native/engine/delivery/**` and this report. No shared core, protocol, UI, build-logic or catalog edits.
The implementation uses INTEGRATE-W1/W2's flat `Failure`, `Outcome`, `StopResult` and `AppDispatchers` contracts.

## Per-finding disposition

Tests below are under `native/engine/delivery/src/`, in `commonTest` except the parallel JVM test in `desktopTest`.

| Review finding | Disposition and behavior | Regression test names |
|---|---|---|
| **1 BLOCKER: concurrent flushes dispatch twice** | **Fixed.** Queue commands, online commands and their background continuations use one injected delivery serial lane. Flush admission coalesces on that lane. Marker persistence also requires the exact captured entry to remain unmarked inside the queue transaction. | `DlvParallelFlushTest`: **B-STATE-042 DLV-1 parallel flush admission dispatches a prompt at most once**. Two real worker threads rendezvous between the old admission check and set; the original code delivered twice. |
| **2 BLOCKER: deferred trimming overwrites newer contents/markers** | **Fixed.** Deferred cleanup takes the queue transaction and saves the current cache, rather than the first-load snapshot. | `DlvTrimTest`: **B-STATE-035 DLV-2 deferred trim preserves an acknowledged addition**; **B-STATE-042 DLV-2 deferred trim preserves a newer dispatch marker across death**. Both inspect what a fresh engine reads from disk. |
| **3 MAJOR: online sends do not fence location changes** | **Fixed.** Capture the dispatch scope with the link epoch before waits; compare both after waking and in the prompt's final dispatch predicate. Receipt scope comes from the captured origin. Commands, restart-stop and ordinary Stop also recheck their target after waking. | `DlvScopeTest`: **B-PROTO-043 DLV-3 a prompt retains its original location through the transport wait**; **B-PROTO-043 DLV-3 commands refuse a changed location or connection after waking** (slash and shell, location and link replacement). |
| **4 MAJOR: an old receipt consumes a replacement draft** | **Fixed.** Queue and online admission refuse ids already present in the profile's receipt journal, including tombstones. Unknown journal state refuses admission. Replacements use fresh ids. Legacy unmarked collisions are retained before marker admission; a different receipt identity cannot prove a new dispatch accepted. | `DlvIdentityTest`: **B-STATE-009 DLV-4 a completed command identity cannot consume a replacement after restart** (fresh replacement delivered through flush and restart); **B-STATE-009 DLV-4 legacy identity collisions survive repeated flush and restart**. `ReceiptsTest`'s replacement test now asserts refusal of the old id and retains a fresh replacement. |
| **5 MAJOR: Check again/resend lose the returned local id** | **Fixed.** Keep the local-to-review id mapping after an uncertain bubble leaves, until the queue entry resolves. Prune resolved mappings. | `DlvReviewIdTest`: **B-STATE-043 DLV-5 check again uses the local id returned by send**; **B-STATE-044 DLV-5 explicit resend uses the local id returned by send**. The existing `PromptDeliveryTest` lost-response test also uses the actual returned id. |
| **6 MAJOR: one completion clears siblings' Stop protection** | **Fixed.** Stop captures every on-wire local send id; acceptance or failure releases only that dispatch's obligation. A late acceptance stops again without clearing another send's obligation. | `DlvStopTest`: **B-AGENT-243 DLV-6 each on-wire send retains its own Stop obligation**. Two delayed sends complete in reverse order, first with two acceptances, then with a refusal followed by acceptance. |
| **7 MAJOR: bulk clear erases an offline send's marker** | **Fixed.** Clear refuses while either an online send or an offline flush dispatch is in flight. | `DlvClearTest`: **B-STATE-045 DLV-7 clear refuses an offline dispatch and preserves its review after response loss**. Restart retains the marker and another flush makes no second attempt. |
| **8 MAJOR: Stop and send ignores failed/uncertain stopping** | **Fixed.** Propagate a stop failure; refuse an uncertain stop with `Refused(DispatchUncertain)`. Dispatch only after `Stopped` or `NothingRunning`. Refusal returns before the marker or bubble, so the caller retains its draft. | `DlvRestartTest`: **B-PROTO-025 DLV-8 restart sends only after a confirmed stop or nothing running**. Covers a failed stop, uncertain stop and both confirmed cases. |
| **9 MINOR: stopping a chip restores its text to the draft** | **Fixed.** Keep the restoration policy in the pending send and honor it when Stop cancels a waiting send. | `DlvChipTest`: **B-AGENT-243 DLV-9 Stop never restores a waiting chip to the draft**. No draft outcome, prompt dispatch or marker remains. |

## Verification

The first valid regression run against the starting implementation compiled and ran **12 new tests; all 12 failed**
with behavioral assertions matching findings 1–9. An earlier draft of the two trim tests used incompatible coroutine
schedulers; that fixture error was corrected before recording this red run. The corrected tests hold only the cleanup
launch, keeping store commits on the same virtual-time scheduler.

A further upgrade regression exposed the legacy identity-collision path: an unmarked replacement admitted by an older
version could still be consumed on the second flush. It was reproduced red before adding the pre-marker guard.

Commands run from `native/`, serialized with `flock /tmp/oc-native-gradle.lock`:

```sh
JAVA_HOME=/home/eslam/Storage/tools/jdk-21.0.13+9 \
ANDROID_HOME=/home/eslam/Android/Sdk \
./gradlew --no-daemon \
  -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m -XX:+UseParallelGC -Dfile.encoding=UTF-8' \
  --max-workers=2 -Pkotlin.incremental=false \
  :engine:delivery:desktopTest :engine:delivery:compileAndroidMain
```

The explicit JVM/worker flags keep the requested PC limits even though this host's user Gradle properties request a
larger heap. Targeted queue, receipt, scope and review-id runs passed during implementation. **Final result: BUILD SUCCESSFUL; 78 desktop tests, 0 failures, 0 errors, 0 skipped; desktop and Android compilation passed.**

No formatter script is configured (`tool/format-changed.sh` is absent). Only changed files were styled;
`git diff --check` passed. Existing warnings remain in `ConversationActivity.kt` (coroutines opt-in) and
`GatewaySendLink.kt` (unnecessary non-null assertion). No emulator, device, APK, live server, iOS or app-graph
qualification was performed.

## Code commits

- `a1586b59` — queue serial admission, current-cache trimming, protected bulk clear (1, 2, 7).
- `7dbf2f70` — reserve durable receipt identities (4).
- `2a541112` — original location and link fencing (3).
- `3fb705ba` — retain the returned local review id (5).
- `a94e9d40` — independent Stop obligations, confirmed restart, chip restoration policy (6, 8, 9).
- `d42c02cb` — retain collisions already persisted by older versions (4).

The following documentation commit adds this disposition and verification record.

## Local commit delivery

The sandbox made the shared worktree's Git metadata read-only, so `git add` and `git commit` there were rejected
(`index.lock`: read-only filesystem). Source edits remain in the requested worktree. Small commits with the requested
subject suffix and both trailers are preserved in a writable local repository at `/tmp/dlv-fix-history.git`, rooted
at the unchanged starting commit. No shared refs were changed and nothing was pushed.

The final history is exported as `/tmp/dlv-fixes.bundle`, with `066a00be` as its prerequisite. From a shell with write
access to the shared Git metadata, import it into a separate review branch:

```sh
git fetch /tmp/dlv-fixes.bundle \
  refs/heads/native/fix-dlv-2026-11-01:refs/heads/native/fix-dlv-reviewed
```

This imports the commits without modifying the current worktree or discarding its source edits.
