# BB7 — policy-gated restoration after reboot or app replacement

Status: design preparation only while BB5's native/device gates wait for memory.
No receiver, permission or boot/update restoration path is implemented by this
document. BB7 remains skipped on the unmet BB5 qualification dependency.

Finish line: a reboot or actual package replacement restores only an already
wanted, admitted phone server when idle-stop is off; explicit Stop, Android
revocation, malformed state or incompatible rootfs leave it stopped.
Non-goal: background agent/helper starts, new credentials, stored scripts,
foreground-service quota workarounds or restoring unrelated processes.

## Native boundary

A nonexported receiver accepts only `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`.
Use credential-protected app storage after normal boot completion; do not add a
locked-boot path that cannot read the existing private runtime state. Receiver
work is bounded and coalesced. It never starts an Activity, acquires a CPU lease,
creates credentials or starts the phone-agent helper.

Before foreground-service admission, read strict durable idle/wanted state.
Require a valid default-off idle policy with idle-stop disabled, no idle stopped
or owed-helper marker, wanted intent and no user Stop/timeout. Missing idle
metadata uses BB5's documented default; malformed data refuses. A positive
completed helper gate must remain intact and must not authorize helper startup.
Native BB3 migration, policy, budget, rootfs and exact-owned-child proofs remain
authoritative. Every asynchronous continuation rechecks its captured revision.

Only the existing service type may be used. Android lists boot/package replacement
as background-start exemptions, with extra boot restrictions for some service
types. Android15 restricts boot starts of dataSync and other listed types; the
receiver must not substitute a type or reset timeouts to evade a restriction.
See [background-start rules](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
and [Android15 boot restrictions](https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed).
A platform refusal leaves wanted intent but reports unavailable; no retry loop,
exact-alarm trick or battery exemption changes that result. `onTimeout` continues
to revoke wanted intent and all pending restoration.

## Package-version compatibility dependency

Current `NativeServerRecipe.compatible` requires the exact package version and
rootfs generation. An actual version increment therefore cannot restore using
the old recipe unchanged. Keep that general compatibility check strict.

The trusted package-replacement path needs a narrow canonical recipe refresh:
only an existing valid authored runtime descriptor, the same durable owner,
unchanged rootfs, admitted policy and preserved retry budget may receive the new
package version. No arbitrary command/argument migration is accepted. Previous
owned children must be proven drained through their original receipt; reboot
identities from another boot are never signal targets. A downgrade, changed
rootfs, missing recipe, unknown live child or stale Stop revision refuses.
Refresh and restart admission must preserve revocation ordering; package update
must not refill the recovery count or erase confirmed history. These are design
requirements, not verified behavior yet.

## Frontend contract and qualification

Reuse BB3 restoration phase/reason and BB5 policy APIs. No new general-purpose
Dart boot/start command is requested. UI wording remains frontend-owned:
“Starting on this phone” while admitted; “The phone server could not start” with
“Open the app and tap Start” on refusal. Fixed technical reason belongs under
Details, with no raw exception or credentials.

Required JVM controls cover action allowlist, disabled/enabled/malformed idle
policy, wanted and Stop/timeout precedence, stale revision, version refresh,
unchanged rootfs/budget and no helper intent creation. Locked emulator checks
must use an actual reboot and actual same-signer replacement, show background
server restoration without opening the Activity, and prove policy-on/Stop cases
remain stopped. A simulated broadcast, synthetic version stamp or unit receipt
alone does not establish the actual reboot/update journey. Restore normal2197
with install-r in the same lock; account/auth data remain untouched.
