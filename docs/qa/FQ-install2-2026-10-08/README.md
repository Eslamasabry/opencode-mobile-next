# Install-cert2 preparation — 2026-10-08

Branch `sol/ba-install-cert2` starts at coordinator `feat/genui-fe f0e33d96d`.
Finish line: six agents have reviewed actual app launch/rejection, app-side
removal and safely injected storage admission evidence. Non-goal: sign-in,
Claude logout, fabricated readiness or device filling.

Status: offline preparation. No device operation, APK build/signing, matrix
update or new runtime-capability claim. Awaiting coordinator's APK2197 and
reviewed normal/QA artifact receipts. UI entrypoints remain coordinator-owned.

[Frontend/backend contract](../../design/BA-install2-contract.md),
[locked driver instructions](../../../tool/qa/fq_install2/README.md).

## Source evidence

- [Initial host negative control](host-negative-control.log): five behavior
  failures with unavailable removal and unwired QA metadata. Tests were restored
  to the implemented candidate; live/foreign owners still fail closed.
- [Host tests](host-focused-tests.log): safe fixed-UID removal, retained gate/
  account metadata, foreign/unknown-job refusal, exclusion during preparation,
  install retry after removal, raised native target-only spec and normal QA-off
  params, closed storage failure projection.
- [Actual authored removal-script tests](removal-tests.log): surviving target
  PID with absent payload failed before the fix; payload symlinks also failed
  before their safe unlink fix. Child links are unlinked without following
  internal/account/external targets. Root/ancestor/foreign launcher links remain
  refused; current-version launcher validation is intentionally conservative.
- [Claude auth negative control](claude-auth-negative-control.log): restoring
  the all-agent probe block changes Claude signedIn to error while fx removal
  runs. The target-only block preserves unrelated Claude inspection.
- [Controller tests](controller-focused-tests.log): target-only row update,
  retained Claude account/chats, busy guidance and optional-port fallback.
- [Affected integration tests](source-focused-tests.log) and
  [analyzer](source-analyzer.log): checkpoint results are recorded in these logs;
  the seven other affected files passed 155 tests; a missing transport import
  initially prevented the new host test file from loading. That test-only import
  was corrected and only the failed host file was rerun, as recorded above.
- [Offline driver tests](offline-driver-tests.log): bounded app rejection,
  missing product removal action, process/leftover checks, native guard evidence
  versus declared threshold, all-component download counters, artifact receipt
  validation and no device access from plan mode.

Actual daemon launch still needs readiness/auth/smoke prerequisites or an
explicit reviewed app QA entrypoint. The prepared stock-app driver records
rejection and a way forward; it cannot turn an empty draft into daemon proof.
App removal requires Claude to expose `PhoneAgentRemovalSource` through a real
product action. Storage proof requires a separate raised-floor QA candidate
and visible storage advice; default normal APK2197 must keep the flag off.

Device evidence will be added only after coordinator artifact delivery, one
agent at a time under the shared emulator lock with storage checked first.
The normal APK will be restored after flagged candidates. Claude's real account
will not be logged out or cleared.


Gateway admission: optional beforePayloadUse/afterPayloadUse callbacks are wired
on both built-in gateway factories. They hold payload admission across actual
create/resume/direct existing-draft prompt requests, not empty local drafts.
Removal refuses in-flight daemon requests; new requests are denied while removal
is active. The focused gateway run passed 34 tests (10 admission, 15 browser,
7 correlated prompt, 2 file-size ratchet). The final affected integration log
includes those 10 new admission tests; earlier callback denial failed before
wrapping actual daemon dispatch. No real daemon or device launch is claimed.

Offline reviews also caught and fixed failed normal restoration exiting zero,
APK restoration despite a rejected read-only preflight, queued artifact checks
becoming stale, screenshots after leaving the rejection frame, identical
normal/guard hashes and dependency downloads being omitted from storage proof.
New fake-port regressions failed before those fixes. The final Python run passes
53 tests. Plans do not import device helpers or invoke adb; candidate hashes,
signer and file identity are verified again under the lock before replacement.


Final offline checkpoint: 167 affected source tests passed across eight files
(155 in the seven successfully loaded files, plus the corrected host file's
12 cases). Browser/correlated gateway follow-up also passed 22 additional cases.
Analyzer clean (17.1 s), pinned Dart format language3.10 clean, Python syntax
checks and 53 offline driver tests passed. No full suite. The host-only fixture
was corrected to create its fake socket lazily; closing an unlistened fake
stream had stalled teardown, so only this owned test process was interrupted by
its verified exact PID, then the corrected host file passed. No server/device
process was signalled.

No device result is pending interpretation: none was attempted. Certification
matrix partial rows remain unchanged until real app evidence can replace them.
