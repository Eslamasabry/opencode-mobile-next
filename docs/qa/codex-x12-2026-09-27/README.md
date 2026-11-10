# X12 — durable Termux host for phone setup v2

Date: 2026-09-27. Base: `e7762e60`, existing worktree/branch `codex/x12`.

Finish line: the existing component setup engine can execute on Termux, report
component progress, restore a job after app death, resume its selection and
cancel its own execution without confusing callback loss with process death.

Non-goals: UI/routes, app-shell/profile wiring, release/signing, a second
component catalogue, credential storage, or replacing the old wizard.

## Scope and feasibility

Read set: `AGENTS.md`, STANDARDS sections 2, 3, 13 and 15 only, the P1.2
feasibility record, `lib/builtin/setup/`, `BuiltinLinux`, Termux bridge,
`KitRedact`, and the native setup/Termux dispatch and result service.

The earlier [P1.2 record](../codex-p12-2026-09-27/README.md) correctly identified
an absent durable host. This task explicitly authorizes building that contract,
including both halves of `oc/termux`. Existing RUN_COMMAND dispatch and
permission checks are callable. Existing component scripts target Ubuntu and
can run in the managed `opencode-ubuntu` Termux container. No provider
credentials are needed by this installer. The authenticated connect step stays
an injected app-shell responsibility.

Write sets and independent responsibilities:

- Transport: `lib/termux/bridge.dart`, new native Termux host and native
  `oc/termux` dispatch, plus bridge behavior tests. One owner for both channel
  halves. Built-in native `SetupRunner` is retained.
- Engine: `lib/builtin/setup/setup_engine.dart`, the Termux host adapter, and
  focused adapter tests. Depends on the frozen bridge methods below.
- Integration tests: `test/setup_termux_resume_test.dart`, using mock channels
  and the shared engine API. Depends on the two contracts above.
- Coordinator: `setup_finish.dart` host guard and its test, this record,
  `COMMIT_MSG.txt`, integration inspection and formatting.

No edits under `lib/ui/`, to `lib/main.dart`, `lib/state/connection.dart`,
`lib/domain/server_gateway.dart` or `lib/api/product_repository.dart`.
Native edits are the explicit exception in this task's final instruction.

## UI hook-up

Use `ChannelSetupEngine.termux` from
[`setup_engine.dart`](../../../lib/builtin/setup/setup_engine.dart). It runs the
same `setupComponents` catalogue, dependency expansion, check-before-install
logic and `SetupProgress` mapping used by the built-in host.

```dart
final engine = ChannelSetupEngine.termux(
  strings: () => l10n,
  finisher: (request) async {
    // App-shell integration: start the managed Termux server for
    // request.runtime, restart if request.openCodeChanged, then authenticate
    // and connect through the existing secure profile flow.
    // Return null ONLY after that connection succeeds; else a safe reason.
    return connectTermuxServer(request);
  },
);
await engine.restore();
// Subscribe to engine.progress (ValueListenable<SetupProgress>).
await engine.run(selectedComponentIds, params: {
  '_job': {'first': '1'},
  'opencode': {'runtime': 'opencode2'},
});
// After a failed/interrupted/cancelled job:
await engine.resume();
// On explicit cancellation:
await engine.cancel();
// On owner teardown (does not cancel the durable install):
engine.dispose();
```

`connectTermuxServer` in this example is the coordinator's integration hook,
not an API implemented here. No UI or app-shell call site is changed by this
unit. Retain one engine per host rather than switching a running engine's host.
`SetupFinishRequest.host` identifies the host; `BuiltinSetupFinisher` rejects
Termux before accessing profiles or starting the in-app server.

Use existing `TermuxBridge.capabilities`, `requestPermission`, `openTermux`,
`openAppSettings` and `verifyBridge` for the person steps before starting.
RUN_COMMAND permission and Termux's allow-external-apps setting remain
necessary. Do not interpret a timeout as installation failure or process death;
restore/status must reconcile against the durable host. Show the existing
component rows, progress, `canContinue` and error states. Setup is ready only
when the terminal `start` step was successfully acknowledged by the finisher.

The bridge contract is `startSetup`, `setupStatus`, `cancelSetup`,
`completeSetupStep`, `setupHostInstalled`, `setupRun`. `TermuxSetupHost` adapts
it to the shared engine's existing transport shape. Install state is host-wide,
not per profile; deleting a connection profile does not uninstall Termux tools.

## Acceptance and verification

Required behavior coverage: healthy-component skip; restore without a second
start; resume preserves optional selection/runtime; failure and cancellation;
finish acknowledgement only after connection; wrong-host refusal; secret-safe
persistence and permission errors.

Flutter test/analyzer runs are **not performed**, following the user's explicit
instruction that Flutter cannot be run by this builder. This is not a passing
analyzer/test claim. No Gradle, APK, emulator, adb, live server or phone checks
are performed. Native app-kill, Termux-kill, cancellation and RUN_COMMAND
permission behavior need coordinator-run Android evidence before enabling.

Verification results and implementation details are recorded below. No UI
screenshots are applicable to this backend-only change.

## Durable contract and privacy

The native `TermuxSetupRunner` owns an atomic app-private
`files/termux-setup-v2.json` record containing `host: termux`, ordered component
metadata, selection, runtime and job facts. The built-in `SetupRunner` and its
record are unchanged. A detached Termux session executes the same component
specifications. `~/.oc/setup-v2/<job>/` contains fixed state words, PID/start-time/
boot identity, locks, step acknowledgements and numeric percent/byte progress.
Status obtains these through a fresh RUN_COMMAND call; callback registries are
never the source of job truth. UUID callback identities reject stale replies
from an earlier Android process. A per-job lock and a shared install lock prevent
overlapping execution. Probing a dispatch gap fences its delayed launch before
reporting interruption. Finished/skipped components are checked again on resume.

Cancellation writes a durable request and signals only the verified setup
process group. A cancellation remains running while that group still owns its
lock; the UI must await terminal progress. `dispose()` stops Dart observation,
not the installer. App-owned step acknowledgement is scoped by job and component.
The injected finisher must be **idempotent**: an acknowledgement lost to a
transport error can cause another connection attempt.

Only supported installation parameters are accepted (`opencode.runtime/version`,
`_job.first/adding`). The engine validates them before generating scripts and the
bridge validates/redacts metadata with `KitRedact` before native persistence.
Raw errors, logs, stages and runtime-produced versions are not persisted by the
Termux shell; numeric progress and fixed failure copy are retained. Executable
scripts are held in the RUN_COMMAND payload/process memory, not written as job
scripts. Package files and the verified Ubuntu archive are installation data.
Setup never reads, creates or stores provider credentials or server passwords.
The final connection must reuse the existing secure profile flow.

Fresh base installation reuses the existing managed container name
`opencode-ubuntu`, Canonical Ubuntu Base 24.04.4 downloads and the existing
architecture-specific SHA-256 pins. A usable base is left installed. Known
Termux rootfs layouts are checked before extraction. No existing container or
projects are deleted.

## Limits and coordinator follow-up

- Setup requires Termux RUN_COMMAND permission/service, allow-external-apps,
  `flock` and `setsid`. Missing native prerequisites fail before saving a new
  job. No unverified permission or package workaround is used.
- Killing the Android app leaves the Termux-owned job independent. Killing
  Termux/its process group allows restore to report interruption and resume
  by checking components. These Android behaviors still require device proof.
- If Termux is killed during Ubuntu extraction, a damaged existing container
  may need explicit manual recovery. This unit refuses to erase it on retry;
  it does not claim automatic repair of every interrupted base extraction.
- If only the recorded shell owner is killed while descendants retain the
  lock, status remains running. Cancel refuses to signal an unverifiable owner;
  stopping Termux or waiting for the descendants is required. It never kills
  processes by command pattern or risks another session's server.
- The v2 install lock serializes v2 jobs. The UI coordinator must prevent
  concurrent use of the legacy installer while a v2 job runs; retiring that
  wizard remains P1.3.
- No raw diagnostic log tail is available for this host. Progress reports
  component state, percent and bytes; detailed stage strings are omitted.
- Termux provides its own process supervision. No new app notification path,
  foreground service, UI selection, route, or authenticated finisher is wired.
- `MainActivity.kt` dispatch was inspected but not compiled as the full app.
  No claim is made about an APK build or Android runtime integration.

## Focused checks

Written Flutter behavior tests (not executed here):

- `test/setup_termux_host_test.dart`: transport, check exit status, redacted
  permission failure, wrong host, unsafe params, unavailable/corrupt status,
  ambiguous launch recovery, and queued cancellation after lost replies.
- `test/setup_termux_resume_test.dart`: cold restore, selection/runtime resume,
  healthy-component skipping, connection acknowledgement, failure and cancel.
- `test/termux_setup_bridge_test.dart`: pinned native bootstrap selection,
  redacted persisted metadata, rejected parameters, safe step acknowledgement.
- `test/setup_finish_host_test.dart`: built-in finisher refuses Termux before
  any profile/connection access.
- `test/termux_setup_native_test.dart`: wrapper around the actual generated
  shell lifecycle harness. Reports an explicit skip when `kotlinc` is absent.

Coordinator commands (pinned Flutter, one at a time):

```bash
~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter test --concurrency=1 test/setup_termux_host_test.dart test/setup_termux_resume_test.dart test/termux_setup_bridge_test.dart test/setup_finish_host_test.dart test/termux_setup_native_test.dart test/setup_engine_test.dart
~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter analyze
```

Executed here:

- Pinned `dart format --language-version=3.10` on all changed Dart files: exit 0.
  Warning: this checkout cannot resolve `package:flutter_lints/flutter.yaml`;
  formatting success is not analyzer evidence.
- `kotlinc` compilation of `TermuxSetupRunner.kt`, `TermuxSetupShell.kt`,
  `SetupJob.kt` and `TermuxResultService.kt` against
  `/home/owner/Android/Sdk/platforms/android-37.0/android.jar`: exit 0.
- Compiled the production `TermuxSetupShell.kt` with
  `test/native/termux_setup_harness.kt` and ran its three scenarios directly
  with Java. All passed: progress/bytes and step completion without persisted
  diagnostics; scoped cancellation and skipped-component resume; fenced delayed
  dispatch plus process-group death and recovery. Results are in
  [native-shell-results.txt](native-shell-results.txt). The harness remaps only
  the fixed durable directory to temporary storage and never runs installers,
  Termux, or live servers.
- Extracted the pinned base script and checked `bash -n`: exit 0.
- `git diff --check` and local README link check: passed.

The base revision, final source hashes and check statuses are recorded in
[verification.json](verification.json).

This is focused host-side evidence, not the Flutter suite, a full Android
compile, or device lifecycle proof. No failing-first Flutter run was possible.

## State

Implemented: backend/native host contract and shared-engine integration.
Enabled: callable API only; no product route or finisher wiring.
Verified: formatting, focused Kotlin compilation and shell lifecycle harness;
Flutter behavior tests/analyzer and Android device checks remain unrun.
Committed: see this branch's X12 commit and root `COMMIT_MSG.txt`.
Pushed/deployed/released: no.

Contract problems: no remaining missing host contract. Product P1.2 remains
pending coordinator app-shell/UI wiring and Android verification. Manual
recovery limits above are explicit and are not claimed as passing automation.
