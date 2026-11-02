# INTEGRATE-P12 — P12 integration ledger

Branch `native/p12-integrate-2026-11-03` (worktree `~/wt/claude-integrate`). Local only, never pushed.

## Rules (amended brief, 2026-11-02)

- Merge only verified branches, in the order of `~/wt/verify/verified.txt` (`<unit> p12v/<unit> <commit>`),
  each with `git merge --no-ff`. Units in `~/wt/verify/blocked.txt` are listed here, not merged.
- After each merge: `:androidApp:assemblePreviewDebug compileKotlinDesktop` plus the touched modules' tests.
- After each green wave: preview APK, line appended to `~/wt/p12-integrated.txt`.
- Gradle: `-Dorg.gradle.workers.max=3 -Dorg.gradle.jvmargs=-Xmx3g`, `JAVA_HOME=~/wt/native-repair-20261102/tools/jdk`.
  Desktop UI tests need `LD_LIBRARY_PATH=~/wt/native-repair-20261102/tools/runtime-libs/root/usr/lib/x86_64-linux-gnu`
  (this host has no `libGL.so.1`; without it every Skiko test fails with `LibraryLoadException`).
- Signing: `androidApp` has no release signing config, so wave APKs are `assemblePreviewDebug`.

## Merged before the amended brief (earlier session)

S2-MD, S2-PLATFORM, P3, P6, S2-SERVERS, P12-SETTINGS (`0eac8f3d3`), see `git log`.
An unverified `p12v/projects` merge was in progress at hand-over; it was aborted (`projects` not in
`verified.txt`, its verifier was still working).

## Merge log

| # | Unit | Commit | Merge | Result |
|---|------|--------|-------|--------|
| 1 | P12-COMPOSER (`native/p12-composer-2026-11-03`) | `335056b0f` | `501d3abf9` | Clean merge. assemblePreviewDebug + compileKotlinDesktop green; `:engine:composer`, `:engine:delivery`, `:feature:chat`, `:protocol:oc2`, `:ui:words` desktopTest green; `:quality:test` green except `p3.TranscriptStreamUiTest` (see Known) |

## Known

- `quality/…/p3/TranscriptStreamUiTest` times out at the 1-minute `runComposeUiTest` budget (124 s wall,
  ~8 000 real frames) while the host load average is ~18 (parallel verifiers). Nothing on its path
  (TranscriptEngine, `ChatTranscript`, kit-chat) changed in the merge; load-bound, re-check when the host is quiet.

## Blocked units

(none so far)

## Waves

| Wave | Time | APK | Units |
|------|------|-----|-------|
| 1 | 2026-11-02 22:01 | `~/wt/p12-apks/p12-wave1-501d3abf9-preview-debug.apk` (25.1 MB) | P12-COMPOSER |
