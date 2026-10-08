# BB service fixture integration checkpoint — 2026-10-08

Candidate: BB runtime/work leases at `618a94c1`, merged without rebasing with
coordinator `feat/genui-fe` at `32c26b7fdd6efeb819a785ab4a6a8bde65ed21e5`.
The sole conflict in the private agent launcher preserves both BD2 private
stderr disposal and BB4 sign-in work ownership. Incoming UI and other lanes's
recorded evidence are preserved.

The service fixture now matches sticky restoration, bounded asynchronous tree
drain, exact stop-revision capture, and kind-specific CPU lease revocation.
Seven denial scenarios remain; nine lifecycle scenarios were added. Latches
hold the child drain/cancel while asserting Android callbacks return promptly;
this avoids passing merely because the fake drain happened to finish quickly.

- `focused.txt`: 64 tests pass across service, agent-run, setup, reply-watch and
  named work-lease files, with serial Flutter execution through the test lock.
- `red-*.txt`: removing revision capture, cold restoration dispatch, foreground
  work revocation or the post-restoration sticky check each fails the matching
  behavioral scenario. Compiler failures are explicitly rejected as red proof.
- `restored-service.txt`: all 16 service scenarios pass after byte-exact restore.
- `analyze.txt`: analyzer clean on this merged checkpoint (42.9 seconds).

Native release Flutter/Kotlin compilation and 46 focused Gradle tests pass
(`native-work-build.txt`, `native-work-result.json`). Exact owned Gradle
cleanup is in `native-work-owned-processes.json`. Pinned detekt is a separate
requested gate and is still in progress. This repairs
a host fixture; historical BB3/BB9/BB4 device proof does not become a fresh
merged-build device qualification. No emulator session, APK installation or
account operation is part of this fixture checkpoint.

Localization follow-up: coordinator `feat/genui-fe` advanced to6151bda44
while the first merge was being verified. A second no-rebase merge retains BD
localized English/Arabic service assertions plus BB's nine lifecycle assertions.
`localization-merged-restored.txt`: all18 service +10 native locale scenarios
pass. The preceding log records a wrong auxiliary filename (18 service scenarios
passed but nonexistent auxiliary test failed to load); the corrected run is the
valid gate. No production fix was needed for that invocation error.

Pinned detekt1.23.8 checked this checkpoint against the committed baseline and
reported351 issues (`detekt-initial/`, exit2), including BB4 additions. The
coordinator subsequently transferred this gate explicitly to BD/sol/bd-detekt.
No baseline entries, suppressions or Kotlin gate edits were made by BB. This is
a recorded failing gate, not a pass.
