# BB4 — helper chat scope extension

IMPLEMENTED AND FOCUSED-VERIFIED; fresh candidate analyzer/APK/device and actual BA
agent-work qualification remain pending. [Contract](../../design/BB4-contract.md).
No commit, full-suite, all-agent certification, push or release claim for this
extension is made here.

The original BB4 native/OpenCode slice at
`618a94c1190f7793070809e94f2359fe54dad8d1` qualified on private release QA2213:
five mandatory native lease/setup flags and original metadata restoration passed.
Its install/scenario/normal2195 in-place restoration shared the emulator lock.
That device evidence belongs to the original candidate, not this helper extension.
The normal2195 screenshot shows connecting; authenticated Connected evidence
belongs to QA2213 before that reinstall. See
[original receipt and limits](../BB4-2026-10-07/README.md).

This extension adds canonical-helper profile/name chat ownership, exact helper
admission, separate OpenCode/helper loss, retained logical busy keys after CPU
cap, and generation-bound fresh admission after Stop. BA inventory is queried
using the readable server alias; its canonical helper owner travels over the
lease channel. Idle readiness creates no CPU hold. Setup/sign-in/terminal leases
remain independent. Review fixed the CPU-close path that previously erased the
logical busy key and the fresh-ID admission race after revocation.

Latest [native Gradle compile and focused JVM check](native-work-build.txt)
passed 89 tests: WorkLeases19, NativeWorkLeaseHost49, SetupWorkScopes5 and
IdleStopPolicy16, with zero failures/errors/skips in the inspected [JUnit summary](native-result.json). Flutter AOT was excluded from this native-only invocation.
The 16 idle policy tests are separate BB5 groundwork, not BB5 integration or
device proof. [Exact owned-process cleanup](native-work-owned-processes.json)
reports no survivors and absent worktree intermediates. The
[earlier 83-test snapshot](before-review-native-result.json) predates the
admission latch and retained-busy fixes and is historical.

[Eight native removed-fix controls](native-lease-jvm-red.txt) each ran an original
single green behavior test, then failed exactly one assertion when its fix was
removed: helper profile isolation, helper-running admission, server-loss scope,
busy independent of CPU cap, unknown-work refusal, fresh-ID Stop latch, stale
generation rejection and zero-hold logical-key retention.
[Restored pure JVM classes](native-lease-jvm-restored.txt) passed73
(registry19+host49+scope5); exact original source bytes and test hashes are recorded.
This supplemental runner uses the injected host constructor, not Android device
methods. [Driver receipt](native-agent-regression-driver.txt).

[Final restored Dart checks](dart-agent-final-restored.txt) passed61 across the
helper watcher, bridge, healing and real-provider wiring tests.
The app-lifetime provider assigns `ConnectionController.localPhoneAgentWorkBusy`;
provider tests validate the readable-alias/canonical-owner mapping through a
mocked controller. This proves the assignment, not a real agent turn.
[Eight behavior controls](dart-agent-regression-driver.txt) failed as intended:
unknown creation, obsolete ON, late disposed-owner drain, mandatory old OFF,
native cap, busy tombstone, late closed-owner CPU cleanup and independent names.
Per-control `dart-agent-red-*.txt` retain the failures. Source restoration is
recorded by the driver; the final61 pass belongs to the restored candidate.
[Eleven restored host adapter checks](host-agent-scope-restored.txt) also passed. Removing the sixth required helper flag [fails the legacy-five-flags test](host-agent-scope-red.txt). Removing the real provider assignment [fails its behavioral test](dart-agent-provider-red.txt), then the [restored provider file passes3](dart-agent-provider-restored.txt).

Discovery is retained honestly: the [initial test fixture](dart-initial-fixture-zone.txt)
created queue/timer ownership outside the widget fakeAsync zone, causing empty
call expectations and pending timers. The fixture now constructs the watcher
inside that zone. These failures are not removed-fix evidence. The
[initial disposal-only mutation](dart-agent-initial-redundant-late-disposed-control.txt)
passed because another serialized cleanup still drained the old ID. Its
[driver discovery](dart-agent-regression-discovery.txt) correctly rejected that
redundant control; the final control removes the independent cleanup paths and
produces the intended behavioral failure.

Candidate qualification and limits:

- [Analyzer restored clean](analyzer-restored.txt): no issues. Initial duplicate test-fixture override findings remain in [analyzer.txt](analyzer.txt); removed the redundant subclass fixture and [reran healing29](dart-healing-analyzer-fixture-restored.txt).
- [Merged BA busy-inventory getter focused file](ba-busy-hook-focused.txt):19 pass, including other-folder turns, stale/archive/child overlays and unknown coverage. Offline gateway evidence.
- [Release target2214](target-work-artifact.json) and [matching runner](runner-work-artifact.json) passed; approved1DE signer, pinned release engine unchanged, release AOT present, no producer component. Both exact-owned cleanup receipts report no survivors and absent intermediates. [Runtime source freeze](build-candidate.sha256) is based on merged headf561c3584 plus this completion diff.
- [Whole locked device receipt](host-device-work-leases-restore.txt): all six mandatory native flags passed, including actual expiry, independent setup/sign-in/terminal/chat owners, exact helper admission after OpenCode loss, and fresh-ID rejection after foreground revocation. Uses the production private-process launcher with idle stand-ins in an empty temporary home, not a real authenticated agent turn. Its finally path drains exact children and removes only that temporary home.
- The same session restored authenticated OpenCode2/Connected for the acceptance baseline, original typed policy and active-profile pointer while the app UID was dead, then reinstalled unchanged normal2195 with install-r-d, verified its hash/version/signer and opened MainActivity. [Small restoration screenshot](restored-normal-2195.jpg). Final screenshot alone does not qualify Claude Ready or a real turn.

Device discovery is retained: [first attempt](device-session-driver.txt) stopped before installation because emulator5554 was absent. The existing dev AVD is OC_API35 per the checked-in trust report. Started that same data directory on5554 with no wipe or snapshot load. [Readiness receipt](emulator-readiness-started.json). The [bootstrap retry](device-retry-session-driver.txt) correctly refused a dropped inherited lock descriptor before installation; corrected its handoff to exec, preserving the strict guard. The [final driver](device-final-session-driver.txt) completed install/scenario/restore within one inherited flock. Saved build/device wrappers reproduce the session without APK copies. The emulator remains available for other lanes.

BB4's owned backend lease item is implemented and qualified with these focused and device gates. Real-account authentication, a browser round-trip and certification of actual Claude/Codex conversations remain outside this proof. No provider output, credentials, account sign-in/logout, physical-device or full-suite proof is included. Native changes require a later approved Shorebird release. Own textual evidence whitespace was normalized without changing proof semantics.
