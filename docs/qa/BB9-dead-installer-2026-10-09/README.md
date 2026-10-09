# BB9 follow-up: stale CHECK installer admission — 2026-10-09

State: implemented; actual app release Kotlin/Java compile, 18 new JUnit cases,
19 production-method fixture scenarios and 6 existing run cases pass. Both
production hook removals and four independent safety guard removals fail at
behavioral assertions. Source restored exactly. Device proof remains pending
the coordinator's emulator restart; this is not device qualification.

Finish line: a later product check/install/remove can reclaim a dead warm writer
only after complete exact ownership proves it quiescent, preserving pending
rollback and rejecting live/unknown/reused/uncertain identities.
Non-goal: clearing a ticket based on PID disappearance alone, signalling unknown
processes, changing rootfs generations, erasing rollback journals, loosening cold
recovery, app data/account changes or the earlier FQ9 project-storage draft.

## Creation, normal cleanup and reachable stale state

`BuiltinLinux.run()` calls `admitCheckProcess()` to launch a CHECK ticket covering
all five installer targets. Generic scripts are conservatively serialized even
when the caller is doing a status/version check; a CHECK ticket is not evidence
that an installation/update is active. The native stdin/nonce gate persists a
prepared ticket before ProcessBuilder launch, then exact root/leader identities
and observed descendants. The script/output/credentials are not persisted.

Normal `run()` cleanup calls `finishInstaller()` in `finally`; setup INSTALL
scripts do the same from `SetupRunner.runScript()`. Completion removes the
durable ticket and cached owner only after the exact tree drain succeeds.
`stopInstaller()` drains but does not itself remove the ticket. Fresh native
startup runs cold ticket/journal recovery before the first guest command.

The reachable defect is a **one-shot recovery latch plus retained failed-owner
state**. A normal launch/gate failure leaves its durable prepared/committed ticket
for safe recovery; a completion drain failure can retain both ticket and cached
Process. But `componentUpdatesRecovered` is already true: subsequent INSTALL
admission only checks that the old ticket is absent, and CHECK admission can
wait on a dead cached Process. No later warm admission proves/reclaims the dead
reservation. Ordinary process launch failure or loss of a check child reaches
this path without a fixture or credential mutation. A fresh app instance may
recover it, which does not make the retained warm-instance blocker correct.

User installs/setup and component Remove paths using the generic CHECK bridge
can therefore be refused after their writer has died. An active setup worker
or a surviving/unknown descendant remains a legitimate blocker; the fix does
not assume that an absent root/leader means the entire writer exited.

## BC evidence and limits

Read-only evidence from `sol/bc-next@61c6e24b8`:

- `docs/qa/FQ9-2026-10-08/README.md` lines28–33 records all-target CHECK,
  root/leader absent, normal2198 reinstall and later product-side clearance/OC1
  Start/connect. The exact action that cleared it is undocumented.
- `docs/qa/BD7-2026-10-08/README.md` lines40–46 and
  `2198-saved-report-blocked.json` record another CHECK refusal for120seconds,
  with root/leader process presence false. No ticket edits or injected crash.
- `2198-final-restoration.json` retains the later blocked observation; its
  clearance is unproven. Descendant/ESRCH quiescence was not captured.

This recurrence is on normal2198 and distinct from the legacy FQ9 seeder artifact.
The source defect above is established; these receipts do not establish which
launch/cleanup failure created each BC ticket or prove each ticket reclaimable.

## Draft correction

`NativeInstallerAdmission` uses two fresh exact quiescence proofs with owner and
whole-ticket revalidation before durable removal. `BuiltinLinux.reclaimDeadInstaller`
holds the Linux and installer guards. It uses the unchanged ownership/visibility
policy, matching boot/rootfs, independently confirmed ESRCH for absent recorded
root/leader/observed identities, complete UID inventory and exact cached-owner
binding. Current peer exemptions remain live-owner scoped; cold ownership is
not broadened. **The reclamation method sends no signal.**

Live cached owners, surviving owned descendants, unknown/reused PIDs, uncertain
absence, changed boot/rootfs/owner/ticket or failed durable save retain the ticket.
Only successful durable removal clears cached owner/launch references, releases
the dead owner's lease and resets `componentUpdatesRecovered=false`: a pending
INSTALL journal must run normal quiescent rollback before another guest command.
Ticket generation and rootfs generation are preserved.

CHECK admission, INSTALL pre-launch admission and runtime-removal admission use
the hook. Installer starts are serialized. The first cold admission retains its
existing authority. Reclaim is deliberately absent from generic `prootCommand()`:
that call occurs between saving a new prepared ticket and ProcessBuilder.start,
so treating that reservation as stale would erase an in-progress launch.
The existing run-output fixture gains only a no-op admission stub; independent
new fixtures exercise real production ownership/admission behavior.

## Queued checks after BB BUILD WINDOW OPEN

18 JUnit methods and19 production-method fixture scenarios are **authored/unrun**.
The fixture extracts actual reclaim/check/install pre-launch source and uses the
real ownership classifier, with only external launch replaced by a sentinel.

Required behavioral reds, then exact restoration and focused green:

1. Remove only CHECK admission's reclaim hook: `check-cached-dead` must fail.
2. Remove only INSTALL admission's hook: `install-durable-dead` must fail.
3. Remove live-owner refusal, second proof, owner/ticket revalidation and durable
   clear-result propagation separately: corresponding safety tests must fail.
4. Run18 new JUnit cases,19 fixture scenarios and the existing run fixture; run
   affected ownership/visibility/component-recovery classes and actual app
   Kotlin/Java compile under the capped/shared build lock.

If a regression fails to compile, fix the fixture and repeat; a compile failure
is not a behavioral red. No full suite is planned in this lane. Then QA target
and runner builds/device follow-up remain separate qualification gates. Device
sessions need the restarted emulator, whole lock, and normal2198 restoration.
No build auto-starts: the original60-minute wait has ended and all watchers exited.

Product-call trace: `lib/builtin/setup/component_removal.dart` runs presence
probes at line186 and the authored removal script at line265 through `_linux.run`;
MainActivity maps the `run` channel method to native `linux.run`. SetupRunner
INSTALL goes through native startInstaller and finishInstaller. These real
product paths establish the affected scope without reproducing a BC fixture.
The native CHECK queue is capped at10seconds; BC’s120second device-admission
refusal is the outer qualification gate, not the native queue timeout.

Compiler-free extraction preflight passed: all production method fragments and
fixture markers resolve; reclaim contains no drain/signal call; authored counts
are18 JUnit methods and19 scenarios. [Receipt](extraction-preflight.json). This
checks fixture preparation only and is not a Kotlin compile, behavioral red or
passing regression result. Focused Dart analysis remained clean after adding
the cached-owner durable-save-failure scenario.


## Exclusive-window results

All 259 focused Gradle/JVM cases passed, including admission18, ownership35,
visibility13 and component recovery21. See the merged BB5
[compile log](../BB5-integration-2026-10-08/native-gradle-focused.txt) and
[counts](../BB5-integration-2026-10-08/native-gradle-merged-counts.txt).
Window-specific fresh admission is5120MiB; Gradle heap3GB/two workers/in-process.
No watchdog kill. One duplicate annotation compilation failure was corrected
and excluded from red proof. Exact owned daemons were cleaned.

[Initial regression receipt](window-regressions.txt) records18 pure and19 fixture
baseline passes plus the CHECK behavioral red. A subsequent fresh5100MiB check
refused to launch; finally restored all source bytes.
[Resumed receipt](window-regressions-resumed.txt) records the INSTALL behavioral
red, live-owner/second-proof/second-owner/durable-clear-result assertion reds,
18 restored pure tests,19 restored fixture cases and6 existing run cases.
Named fixtures: [CHECK red](fixture-check-cached-dead-red.txt),
[INSTALL red](fixture-install-durable-dead-red.txt),
[restored19](fixture-restored-all.txt),
[existing run6](phone-agent-run-restored.txt). The private runner compiles the
actual unchanged ownership classes with Kotlin2.3.20/JDK17 using256MiB compiler
and128MiB test heaps. Every heavy step passes the shared build lock and a fresh
5120MiB check; Flutter fixtures also take the serial test lock.

No device ticket, account or data was changed. BC's exact observed ticket creator
and complete quiescence remain unproven. This fix addresses the independently
reproduced warm admission defect; it does not claim to clear an unsafe BC ticket.
