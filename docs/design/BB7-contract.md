# BB7 — policy-gated restoration after reboot or app replacement

Status (2026-10-09): **implemented and emulator-qualified** after BB5 PASS.
Real Android 15 reboot and same-signer 2201→2202 update each restored exactly
one wanted server, with idle off, no Activity/helper and one preserved-budget
attempt. Idle-enabled, policy-disabled, Stop and saved-timeout reboot controls
remained stopped with no budget spend. Normal 2202 restored; lock released.
99 integrated JVM, 24 service and 90 combined Python checks pass; analyzer clean.
Evidence: [BB7 QA](../qa/BB7-2026-10-09/README.md). No full-suite, actual quota
expiry, physical-device, push or release claim. Existing policy remains required.

Finish line: a real reboot or same-signer package update restores one previously
wanted, admitted canonical phone server while idle-stop is off, without opening
an Activity; Stop, timeout, disabled policy, malformed state and incompatible
rootfs keep it stopped.
Non-goals: helper/agent/engine restoration, new credentials, stored scripts,
force-stop bypass, foreground-service quota workarounds, frontend edits or
changing the service type.

## Verified source and platform constraints

`BuiltinServerService` currently declares and starts `specialUse`, with the
required subtype description in `android/app/src/main/AndroidManifest.xml`.
`BackgroundConnectionService` is the separate `dataSync` service. BB7 starts only
the existing phone-server service and never starts the background connection,
installer, helper or engine service.

Android lists `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED` as exceptions to the
background foreground-service start restriction. Android 15 additionally bans
boot receivers from starting `dataSync`, camera, media playback, phone call,
media projection and microphone foreground services. The listed ban does not
include `specialUse`; eligibility of the existing phone-server type follows
from those rules, but actual device acceptance is still required. Package
replacement is a distinct exception: do not apply the boot-only dataSync ban to
it, or treat either exception as overriding other platform restrictions.
Sources: [background-start exemptions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start),
[Android 15 boot restrictions](https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed),
[specialUse requirements](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use).

Android 15's six-hours-per-24-hours allowance applies to `dataSync` and
`mediaProcessing`, shared across an app's services of each type. It is not a
six-hour rule for `specialUse`. BB7 must preserve `onTimeout` revocation and
prompt `stopSelf()` regardless, and cannot reset or evade another service's
quota. Source: [foreground-service timeouts](https://developer.android.com/develop/background-work/services/fgs/timeout).

`ACTION_MY_PACKAGE_REPLACED` is a protected system broadcast delivered only to
the replaced package, with no extra payload. Use that action, not broad
`PACKAGE_REPLACED` or caller-provided package/version data. Source:
[Intent.ACTION_MY_PACKAGE_REPLACED](https://developer.android.com/reference/android/content/Intent#ACTION_MY_PACKAGE_REPLACED).

## Baseline gaps addressed by this implementation

- The baseline lacked a boot/update receiver and `RECEIVE_BOOT_COMPLETED`
  permission; both are now present.
- The generic `BuiltinServerService.restoreRuntimeForStart` path restores only
  a null intent. A private event action now routes the opaque ticket explicitly;
  ordinary `start()` still does not manufacture restoration authority.
- Generic recipe compatibility still requires exact package/rootfs identity.
  The protected replacement path separately validates and promotes only an
  otherwise admitted recipe; ordinary reclaim remains strict.
- `NativeRuntimeOwnership.plan` still requires the current boot. Cross-boot
  events use a separate complete-inventory absence proof and never signal the
  prior boot's numeric PIDs.
- `idleBlocksRestoration()` retains BB3 behavior. The event ticket additionally
  pins the idle counter and requires idle-stop disabled through final release.

## Native API and sequence

These are private native entrypoints. Keep the complete native runtime and
both sides of its channel with one owner.

Add `NativeServerRestoreEvent` with `BOOT_COMPLETED` and `PACKAGE_REPLACED`, and
an immutable `NativeServerRestoreTicket` holding event, owner, wanted revision,
supervision generation, schedule id, package version, rootfs generation and
previous receipt boot. These are private native types, never Intent extras from
an external caller or a new Dart restore command.

1. A nonexported `BuiltinRestoreReceiver.onReceive` accepts exactly the two
   protected actions. Add `RECEIVE_BOOT_COMPLETED`; no direct-boot awareness or
   `LOCKED_BOOT_COMPLETED` filter. Read only credential-protected state after
   normal boot. Map the action to the typed event; ignore all other actions and
   payloads. Perform bounded admission and service dispatch, with no process
   launch, waiting, cleanup, network request, Activity or CPU lease in the
   receiver. If bounded I/O uses `goAsync`, always finish its pending result;
   Android still expects completion in under ten seconds
   ([broadcast guidance](https://developer.android.com/develop/background-work/background-tasks/broadcasts#effects-process-state)).
2. `BuiltinLinux.prepareServerEventRestore(event)` produces a ticket or null;
   failures publish a fixed refusal reason in existing native status. Require valid saved wanted/not-user-stopped state; matching
   owner, supervision, migration marker and current automation policy; a strict
   recipe and committed ownership receipt; valid budget with attempts below
   three; valid idle state with `enabled == false`, `stopped == false` and
   `helperStopped == false`; no incompatible or pending component update. A
   missing idle record uses BB5's existing default-off snapshot; malformed
   storage refuses. Preserve a completed helper gate without consuming it.
   Duplicate events, a queued worker or a live server produce no second start,
   recipe write or budget reservation.
3. `BuiltinServerService.startForRestore(context, ticket)` uses an explicit
   private service action and app-owned ticket identity. Dispatch immediately
   while the broadcast exception applies. Catch platform start denial, record
   unavailable without clearing wanted or spending a recovery attempt, and
   abandon this event. No delayed retry, WorkManager/alarm workaround or type
   substitution. Recheck the ticket when the service handles it: a stale Stop,
   deletion, owner change or idle-policy change can win between dispatch and
   execution. Invalid tickets stop an otherwise idle service. Accepted events return START_STICKY so later process reclaim still uses the strict null-intent recipe checks; the event Intent itself is never redelivered.
4. `restoreRuntimeForStart` recognizes only that private event action, establishes
   the same foreground notification first, then invokes
   `BuiltinLinux.restoreServerAfterSystemEvent(ticket)`, returning whether the
   worker was synchronously admitted. Keep the null-intent
   BB3 path and ordinary manual starts unchanged. Both restoration entrypoints
   use the existing single-worker/coalescing discipline.
5. Reuse `runColdRestore`, `withColdRestoreAdmission`,
   `reserveNativeAttempt`, gated `launchService` and health receipts. Every delay,
   ownership check, recipe commit, attempt reservation and launch must validate
   the captured ticket and event-specific idle gate. Service destruction
   invalidates an active event under the same short lock used for gate release;
   it preserves wanted state. Reservation spends from
   the existing budget once; it is never a manual reset. Stop and timeout keep
   their reason and immediately invalidate in-flight work.

## Reboot ownership and package compatibility

Keep `NativeServerRecipe.read`, `compatible`, and the existing ownership planner
strict. Add explicit event-aware decisions rather than weakening their general
contracts or editing a stored receipt's boot to make it match.

For a boot event, require the current package/rootfs to exactly match the recipe
and the validated prior receipt to be from a different boot. Old process
identities cannot survive that kernel boot, so never signal their numeric PIDs.
Add a pure reboot admission helper that requires a complete readable current
same-UID inventory to contain only the current registered app processes; any
unknown current child refuses. Repeat the inventory check immediately before
reservation/launch. The ordinary launch gate writes a fresh current-boot
receipt and nonce. Prepared prior receipts refuse; do not turn an interrupted
launch into an authorized server. A duplicate boot event in the original boot
cannot use this cross-boot path.

A null-intent service rejection can run before the protected package event.
It preserves wanted intent but may persist cleanup for the old server. The
event accepts only that same owner and exact old receipt, with helper cleanup
disabled. It proves the old server drained (or absent after reboot) before
clearing that matching marker; foreign, newer or malformed drain state refuses.

For package replacement, permit exact-version recipes unchanged (same-version
reinstall), or a strictly increasing version through a narrow promotion helper.
Require unchanged rootfs, identical recipe owner/runtime and durable authority,
no prepared or pending installer state, and available preserved budget. This
helper returns a new recipe candidate; it cannot write preferences itself.
A lower installed version refuses. The receiver-provided version is never
trusted: read the installed package through `packageIdentity()`.

Use the original same-boot receipt with `drainColdServer` and the existing exact
PID/start/nonce proof; unknown children refuse. If the receipt is from an older
boot, apply the same no-signal reboot inventory proof before promotion. Under
`recoveryLock`, recheck authority/ticket/rootfs/current package, commit only the
promoted recipe, and then use the normal reservation and launch path. Preserve
budget attempts, pending/confirmed receipt history, idle state and helper gate.
A crash after recipe promotion but before reservation leaves an exact-version
recipe with unchanged budget; later admission still needs every existing gate.
Never call `startManualWithRecipe`, `resetServerRecovery` or configure supervision
to manufacture authorization. No new durable schema or stored launch input is
needed; profile deletion already removes the scoped recipe and receipt.

## Ownership and files

All paths in this table are relative to the repository. The ownership split
applies to the current BB7 implementation and its remaining verification.

| Owner / boundary | Read set | Write set and purpose |
| --- | --- | --- |
| Runtime owner (one editor) | `BuiltinLinux.kt`, `NativeServerRecipe.kt`, `NativeRuntimeOwnership.kt`, `NativeIdleState.kt`, `NativeRecoveryBudget.kt`, `NativeInstallerOwnership.kt` | `BuiltinLinux.kt` event ticket/admission, shared worker and safe recipe promotion; new `NativeServerRestoreEvent.kt` for pure decisions/tickets; only extend recipe/ownership files if required without weakening existing contracts |
| Android entry owner, after runtime API freezes | Runtime ticket API, existing `BuiltinServerService.kt` | New `BuiltinRestoreReceiver.kt`, `BuiltinServerService.kt` private dispatch/foreground routing, `AndroidManifest.xml` permission and receiver |
| Verification owner, after production owners release files | Native contracts and existing test harnesses | Pure event/recipe/ownership tests, service/receiver harness and BB7 instrumentation/host runner; evidence under `docs/qa/BB7-2026-10-09/` |

Kotlin sources above are under
`android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/`; JVM tests
belong under the matching `src/test/kotlin/` package. No `lib/ui/`, connection
library, generated SDK, credentials or localization edits belong to this slice.
No worker runs compilers while another owns the machine check slot.

## Required red/green and device evidence

Before each production fix, add a meaningful failing behavioral test. Remove
only that fix to capture red, restore it and capture green. Cover:

- Action allowlist, nonexported manifest and boot permission; unrelated actions
  cannot instantiate a worker or start any service.
- The non-null private restore action actually restores after foreground
  promotion, while ordinary starts/null-intent BB3 behavior remain intact.
- Missing default-off versus malformed idle state; idle-enabled even before an
  idle stop; owed helper; completed helper gate unchanged; policy, migration,
  owner, wanted, deletion, Stop and timeout refusal.
- Duplicate broadcasts, service delivery and Stop/owner/policy races at each
  asynchronous boundary; at most one attempt/launch and no stale reason write.
- Strict recipe parser/compatibility unchanged; update promotion accepts only
  allowed versions/rootfs/owner/runtime, retains budget and receipts, and
  rejects arbitrary fields, downgrade, failed persistence or stale ticket.
- Cross-boot numeric PID reuse never produces a signal; unknown current child,
  unreadable inventory, prepared receipt and pending installer state refuse.
  Same-boot update drain signals only proven old server children.
- Platform dispatch failure spends no attempt, launches no child/helper and
  leaves a fixed unavailable reason with a manual Start path.

Then run affected JVM/native harness and Dart tests once through their required
locks, analyzer at integration, and release compilation only when available
memory is at least 6 GB with the binding Gradle cap. Root/coordinator schedules
those commands; integrated native compilation and actual device event results
are recorded in the linked QA receipt.

Device acceptance uses only `emulator-5554`, under a whole-session
`flock -w 3600 /home/eslam/Storage/tmp/oc-emulator.lock`. Capture candidate hash,
installed version/target SDK/signer and baseline state without secret values.
Use a real authenticated canonical server with saved wanted/policy state, close
the Activity, perform an actual reboot, and show the new boot ID, receiver/FGS,
server health and one budget spend before any Activity/instrumentation attachment.
No helper or engine is restarted. Repeat with idle enabled, persisted Stop and
policy disabled to show each remains stopped without budget use.

For update proof, install a real same-signer higher-version candidate with
`adb -s emulator-5554 install -r`, without clearing data or opening the Activity.
Show the version transition, updated recipe identity, unchanged rootfs and
credentials/account storage, exact old-child drain, healthy replacement and one
budget spend. Include Stop/update race and denied-start controls. A synthetic
broadcast, manually edited package stamp, simulated timeout or pure test is
separate evidence, never a substitute for the actual event journey.

Restore `/home/eslam/Storage/tmp/oc-apk-share/oc-2202.apk` in the same emulator lock
using install-r, never uninstall. The higher-version update fixture must use a
restorable release build/install scheme agreed before the run: do not discover
a downgrade rejection at cleanup or bypass it by deleting app data. Verify
normal version 2202, signer, launch/server usability and preserved profile/auth
state before releasing the lock. Record any blocked gate honestly; no physical
owner-phone test or release is authorized by this plan.
