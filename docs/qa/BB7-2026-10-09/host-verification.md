# BB7 host and private fixture verification

This receipt covers the new staged host driver and private native fixture only.
It does not claim compilation, a reboot, a package update, Android 15 qualification,
or a completed device sequence. Root owns that evidence and the production changes.

The host uses QA **2201 → 2202**, then restores the retained normal **2202**.
The sole initial `-d` is guarded by the exact installed normal-2202 hash and the
same release certificate. Package replacement and normal restoration use only
`install -r`. No APK is copied; the coordinator overwrites the initial candidate
path with the higher-version build while retaining the same emulator lock.

## Behavioral checks

All commands were run with `OC_TEST_SLOTS=2` and `tool/qa/machine_lock.sh test`.
No Gradle, Flutter, device, or network operation was launched by these tests.

- [host-observer-red.txt](host-observer-red.txt): four predicate tests, **39 assertion failures**,
  with only `observation_proven` replaced in memory by the permissive `True` result.
  This reproduces the original failing-first stub. No exception or compiler failure
  is counted as red proof.
- [host-safety-red.txt](host-safety-red.txt): 12 tests, **two assertion failures** before
  atomic state replacement and idle-counter preservation were implemented. The
  first naive idle-counter test used a missing helper and produced an error; that
  draft was corrected to an executable copy-only implementation before the retained
  behavioral red run. The retained file contains assertions only.
- [host-green.txt](host-green.txt): **12 tests passed** after the fixes. Checks include
  required event predicates; wrong/unknown event rejection; exact old-tree drain
  for update; all four denial cases; fixed-flag parsing; no-follow, mode-0600 state
  safety; a full fake-clock 65-second denial observation; read-only observation
  persisted before native verification; strict budget type; monotonic idle count;
  and preservation of the original state after an atomic-replacement failure.

Green and safety-red command:

```sh
OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test -- \
  python3 -m unittest discover -s tool/qa -p test_bb7_runtime_acceptance.py
```

Observer mutation command (retained output is deliberately failing):

```sh
OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test -- python3 - <<'PY'
import sys
import unittest
from unittest.mock import patch
sys.path.insert(0, 'tool/qa')
import bb7_runtime_acceptance as subject
import test_bb7_runtime_acceptance as checks
with patch.object(subject, 'observation_proven', return_value=True):
    result = unittest.TextTestRunner(verbosity=1).run(
        unittest.defaultTestLoader.loadTestsFromTestCase(checks.ObservationTest))
raise SystemExit(0 if result.wasSuccessful() else 1)
PY
```

`git diff --check` passed. Native reflection and fixture lifecycle are source-reviewed,
not behavioral JVM or compiled-device proof. Existing BB5/BB9 code is unchanged;
the native runner modification adds only BB7 dispatch and its fixed refusal type.

## Navigation follow-up

The initial shared `H.real_start(require_explicit=True)` helper depended on a
`Switch server` label that is absent from the actual current shell button. BB7
now has its own bounded stdout-only UI helper. It recognizes the exact localized
`In-app Ubuntu` title line (including a displayed server-count suffix), selects
its enabled clickable ancestor, opens Manage servers, observes the product Stop
when currently running, then an explicit management Start. Ambiguous actions,
invalid bounds, oversized XML, entities and absent hierarchy refuse.

- [host-navigation-red.txt](host-navigation-red.txt): 16 tests, three assertion
  failures with empty snapshot/target implementations before the navigation fix.
- [host-navigation-green.txt](host-navigation-green.txt): **17 tests passed**,
  including an end-to-end fake UI sequence beginning at an already healthy chat
  and requiring switcher → Manage servers → More → Stop → Start. This is host
  behavior proof, not actual Android/UI qualification.

The same unittest command above produced both outputs. The native fixture stayed
frozen during this follow-up. Cleanup uses the existing typed preference/high-water
merger while the whole UID is stopped, then this BB7 UI helper; it no longer
calls the older shared UI navigation routine. Final normal restoration still
revalidates exact artifact/hash/certificate/version and uses only `install -r`.

## Current-card More selection

The first real setup navigation exposed three unrelated `More` controls: toolbar,
In-app Ubuntu card, and another server card. Root supplied a sanitized hierarchy
showing that the Ubuntu card is itself a clickable button and contains its own
More child. `ui_card_target` now finds the unique clickable anchor carrying the
exact localized Ubuntu title line and searches only that anchor's subtree. It
never widens to the common list or screen ancestor, and duplicate controls inside
the selected card still refuse.

- [host-card-scope-red.txt](host-card-scope-red.txt): 21 tests, two assertion failures
  before card scoping was implemented.
- [host-card-scope-green.txt](host-card-scope-green.txt): **21 tests passed** with
  toolbar plus two-card synthetic hierarchy, absent-action/no-other-card fallback,
  duplicate own-card action refusal, and the existing full navigation journey.

Same locked unittest command as above. No device command or native source edit
was made by this follow-up; root owns the actual device observation.

## Asynchronous product Stop

The driver now latches `stop_requested` immediately after selecting the exact
localized `Stop OpenCode on this phone` action. Until both persisted wanted is
false and authenticated health is absent, it issues no further menu or action
input. This prevents reopening More during a slow drain and obscuring Start.
The generic `Stop` label is no longer accepted; it can belong to another server.

- [host-delayed-stop-red.txt](host-delayed-stop-red.txt): 22 tests, one assertion
  failure from an extra More tap during a simulated delayed shutdown.
- [host-delayed-stop-green.txt](host-delayed-stop-green.txt): **22 tests passed**
  after the latch; the fake journey observes exactly switcher, Manage, More,
  product Stop, then Start, including a still-healthy interval after Stop.

Same locked unittest command as above; no device or native edits by this change.

## Exact authored runtime title

Root's next sanitized hierarchy identified the title line as
`In-app Ubuntu · OpenCode 2`, followed by a separate Connected line. The selector
now derives the exact combination from `phoneSetupOpenPhoneRuntime`,
`phoneServerCardTitle`, and `setupRuntimeTwo` in each shipped locale. BB7's
canonical OpenCode 2 path accepts the base title or that exact runtime-specific
title; it does not accept an OpenCode 1 combination or arbitrary substring.

[host-runtime-title-red.txt](host-runtime-title-red.txt) records two assertion
failures with base-only titles. [host-runtime-title-green.txt](host-runtime-title-green.txt)
records **24 passing tests**, including the actual combined-title shape and
separate exact construction for each runtime. Same locked unittest command;
no device or native changes in this follow-up.

## Rebooted emulator transport

The real reboot completed, but adbd returned as shell UID 2000, making private
snapshots unavailable before verification instrumentation. This was a harness
transport prerequisite failure, not evidence of a product restoration failure.
The host now runs bounded `adb root`, `wait-for-device`, and exact `id -u == 0`
checks after actual boot completion and before private reads. These commands do
not dispatch an Activity, app service, or synthetic broadcast. A changed boot
still refuses a second reboot in the same prepared stage.

[host-reboot-transport-red.txt](host-reboot-transport-red.txt) records two assertion
failures before the transport check. [host-reboot-transport-green.txt](host-reboot-transport-green.txt)
records **26 passing tests**, including fixed call order/bounds, non-root refusal,
and root confirmation before event observation. No device command was run by
these tests. Root owns continuation of the already-rebooted real session.

## Observation-only cached-process thaw

Root's exact thread inspection identified the denied-event app in Android's
cached-process freezer (`do_freezer_trap`), rather than a native verification
lock deadlock. Root then observed that nonsticky thaw of the exact process let
the first instrumentation detach and the repeated native denial verify finish.
The independent 65-second event observation had already been saved.

Host Verify now requires `observation=True, phase='observed'`. After that saved
observation and immediately before instrumentation/detachment checks, it
rechecks the exact app PID/start ticks, issues only bounded nonsticky
`am unfreeze PID`, and rechecks the same identity. Explicit cleanup similarly
enters `phase='cleanup'`; prepare and product-event dispatch never thaw.
No Activity, service, sticky exemption, global freezer policy, or broadcast is
used. An absent app has no thaw target. Existing active instrumentation still
must detach before another run.

[host-observer-thaw-red.txt](host-observer-thaw-red.txt) records eight assertion
failures before the phase/identity/thaw fix. [host-observer-thaw-green.txt](host-observer-thaw-green.txt)
records **30 passing tests**, including persisted observation before Verify,
exact command/order/bounds, early-phase refusal, PID reuse refusal, and thaw
before native invocation. Same locked unittest command as above; root owns all
actual device evidence, and this follow-up ran no device commands.

## Pending external-link confirmation during normal restoration

Root observed a preexisting external-link confirmation on cold normal-app launch,
with no server-navigation controls visible. The host recognizes that sheet only
when exact localized `Open link` and `Copy link` controls occur together, then
selects the unique exact localized Cancel. It never selects Open/Copy and never
uses a generic Cancel without both markers. Dismissals are bounded to three
within the existing 150-second navigation deadline.

[host-link-sheet-red.txt](host-link-sheet-red.txt) records two assertion failures
before the recognition/cancellation fix. [host-link-sheet-green.txt](host-link-sheet-green.txt)
records **33 passing tests**, including a full fake journey beginning with that
sheet, then Cancel → server management → product Stop → Start. Partial markers,
arbitrary Cancel and ambiguous Cancel refuse or produce no cancellation target.
Same locked unittest command; no external navigation, device call or native
change was performed by this follow-up.
