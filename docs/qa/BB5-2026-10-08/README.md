# BB5 offline checkpoint — 2026-10-08

BB5 is groundwork, not a completed feature. BA's prerequisite hooks were merged
without rebase at `f561c3584`; BB4 completed at `30e5d9027` before this checkpoint.
The coordinator reported a kernel OOM at 21:51 and prohibited Gradle/APK builds
and Kotlin Gradle tests until further notice. This checkpoint ran no native
compiler, Gradle task, APK build or device session after that hold.

## Implemented groundwork

The Dart status decoder requires an explicitly supporting APK, correctly typed
admission booleans, bounded integer minutes and a nonnegative integer generation.
Active idle markers require a positive generation. Legacy/malformed receipts
cannot authorize restoration. Idle bridge methods validate owner/token inputs,
bound native awaits and return fixed errors without raw native output or manual
Start fallback. Work observations carry only the readable owner and tri-state
busy flag.

`PhoneServerIdle` sequences server restoration, restoration of a previously live
helper, a fresh status read and final native acknowledgement. It rechecks owner,
foreground, readability and disposal after awaits. Explicit Stop or a changed
generation prevents completion. A previously stopped helper remains stopped;
its completed positive token continues to block automatic helper starts.

The class has **not** been wired into `PhoneServerHealing`. Native status/channel
handlers, persisted policy integration, timer admission, exact idle drain and
helper startup gates are **not** implemented. No idle-stop setting is enabled.
See [the frontend/native contract](../../design/BB5-contract.md).

## Focused Dart evidence

Every test process used the pinned Shorebird Flutter with
`OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test`, `--no-pub`, `--concurrency=1`.
Files ran sequentially, never as a full suite.

- [Bridge: 9 passing tests](dart-final-bridge.txt).
- [Foreground resume: 15 passing tests](dart-final-resume.txt).
- Existing [BuiltinLinux bridge: 23 tests](dart-builtin-existing.txt) and
  [recovery bridge: 4 tests](dart-recovery-existing.txt) pass. Total: 51 focused
  tests, including the 24 new cases.
- [Six removed-fix controls](dart-red-summary.json): strict integer admission,
  input identity, the initial status fence, the native server-return fence,
  previous-helper intent and fresh Stop/generation validation. Every control
  failed with an Expected/Actual assertion; all original source bytes were
  restored in `finally` before the final green runs.
- [Reproduction driver](dart-red-proof.py) and [driver results](dart-red-driver.txt).

The initial bridge test fixture used non-const `PlatformException` constructors
inside const expressions and evaluated synchronous input guards before the
exception matcher. These fixture errors are retained in `dart-bridge-initial.txt`,
`dart-bridge-fixture-initial.txt` and `dart-bridge-fixture-second.txt`. They do not
count as removed-fix evidence. The corrected fixture passes.

The initial scoped analyzer reported nine missing-brace style findings, retained
in `analyzer-initial.txt`. Explicit blocks were added; no ignore or suppression
was introduced. The [scoped rerun is clean](analyzer-scoped.txt). It covers the
two changed/new Dart libraries and the two new test files, rather than claiming
a repository-wide gate. The two new test files were rerun after this style
cleanup. [Source hashes](candidate-source-sha256.json) identify that final
offline candidate; no native integration is included.

## Native preparation and remaining qualification

The worktree retains pure Kotlin policy/state/heartbeat preparation and tests.
`IdleStopPolicy`'s 16 tests passed in the separate pre-hold BB4 native compilation
receipt; that is policy-level evidence only. `NativeIdleState`'s 27 and
`NativeIdleHeartbeat`'s 17 cases have not been compiled or run. These native files
remain uncommitted until their checks and removed-fix proofs can run safely.

BB5 still needs lifecycle/BA callback wiring, native integration, focused native
checks, a release compile, and a whole locked emulator idle-stop/return/tap
scenario with busy work and revocation races. BB7 depends on BB5 and remains
pending. No BB5 device behavior, reboot behavior or Android lifetime extension
is claimed.

## Device restoration carried forward from BB4

The last BB device session completed before this checkpoint and restored
`/home/eslam/Storage/tmp/oc-apk-share/oc-2195.apk` with `adb install -r -d` while
holding the same emulator lock. App data were preserved; no uninstall, clear
data or account logout occurred. The app was reopened. Final normal-app
Connected/Claude Ready status was not certified. See the
[BB4 restoration receipts](../BB4-agent-work-2026-10-08/README.md).

No device session or replacement APK was started for this BB5 checkpoint.
