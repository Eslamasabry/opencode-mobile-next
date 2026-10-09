# BB7 staged device runbook

Run only on emulator-5554, with one inherited exclusive
`/home/eslam/Storage/tmp/oc-emulator.lock` descriptor held across setup, all events,
the coordinator's intervening build, and final normal-2202 restoration. Do not
release the lock after a failed command before restoring the normal app.
The driver verifies that descriptor on every invocation. It does not start builds.

`tool/qa/bb7_runtime_acceptance.py` has these stages:

| Command | Prerequisite | Effect |
| --- | --- | --- |
| `init` | Exact known normal 2202 is installed and healthy | Validate normal/target/runner metadata and certificate, snapshot private original metadata, inspect current owned tree, bounded setup drain, install QA2201 and runner, actual UI Start |
| `prepare --event boot --case wanted` | Ready live canonical owner | Validate quiescence, retain actual owner, save safe fixture, disable idle-stop, finish/remove Activity |
| `reboot` | Prepared boot case | Actual `adb reboot`, observe new boot and completed boot, prove result before instrumentation, then native verify |
| `cleanup` | Existing state | Native exact cleanup; restore typed original metadata with monotonic counters, actual UI Start; ready for another case |
| `prepare --event update --case wanted` | Ready QA2201 | Prepare as above, retain old kernel identities |
| `replace --target-sha NEW_SHA` | Same path now contains validated QA2202 | Actual `install -r`, prove higher version/same boot/exact old tree gone before instrumentation, then native verify |
| `restore` | Any initialized phase with recoverable fixture | Cleanup, retained normal `install -r`, verify exact hash/version and actual Connected UI |

Every command requires `--emulator-go --state ABSOLUTE_PRIVATE_PATH
--inherited-emulator-lock-fd FD`. The private state path must be outside the
repository, mode 0600, regular, owned by the current host user, no symlink, and
created exclusively by `init`. It contains original preference XML; **do not
publish, print, copy to evidence, or commit it**. Later writes replace it atomically.
After confirmed normal restoration, the coordinator can remove this exact private
state file; retaining it privately permits recovery if a later step fails.

`init` additionally requires `--apk`, `--runner-apk`, `--normal-apk`,
`--normal-sidecar`, `--apksigner`, `--aapt`, `--target-sha`, `--runner-sha`.
The normal path is fixed to `/home/eslam/Storage/tmp/oc-apk-share/oc-2202.apk`,
with SHA-256 `e63fb2e4ff32280ad4c739aee9c17db508eab2e99a42573c4e83bd66dc0babb0`.
The shared helper validates the certificate against
`1de5bf08146f269bcd9eb5c2ffc94469ce4617d37806285955f978a62494d60c`.
Only initial QA2201 may use `-d`, only against that exact installed normal artifact.

The minimum positive journey is:

1. Build QA2201/runner, `init`, prepare boot/wanted, `reboot`.
2. `cleanup` and prepare update/wanted.
3. Coordinator builds QA2202 over the SAME target APK path while keeping the
   same emulator lock. Keep the matching runner already installed; do not copy
   either APK. No device mutation is needed during the build.
4. `replace --target-sha ...`, then `restore` before releasing the lock.

Other boot cases may be inserted between cleanups: `idle_enabled`,
`policy_disabled`, `stopped`, `timeout`. Each observes a full 65 seconds with no
health, FGS, helper/unknown payload, Activity, or attempt spend. Timeout seeds the
ordinary persisted `systemTimeout` revocation through the native Stop path; it
is **not** a real OS quota callback or six-hour-service proof. A same-signer actual
update denial can use one of these cases in a separate full initial-2201 session;
one update consumes the available 2201→2202 transition.

Successful cases wait up to 100 seconds for authenticated health, foreground
service, current owner and complete exact-owned tree, one attempt, and no
Activity/helper. The shared BB5 inspector verifies any exact app-authored GenUI
child against generated source; it does not admit arbitrary script/MCP children.
Boot requires a different kernel boot and unchanged package; update requires the
same boot, higher installed package and disappearance of exact old PID/start pairs.
Private native verification occurs **after** the read-only event proof has been
saved. A denied event may have no app process; attachment after that proof is
explicitly observation setup and cannot count as event-created process evidence.

Cleanups restore the original enabled/idle policies and typed marker/active
pointer while preserving maximum recovery attempts, runtime generation and idle
counter. The explicit actual UI Start between independent cases may exercise the
product's legitimate manual reset path; no host or fixture writes a lower budget.
Fixtures contain no credentials, scripts, helper home or launch arguments. No
existing home is deleted, no synthetic broadcast is sent, and no production
policy/ownership gate is bypassed.

On failure the driver prints only a fixed safe code, retains private state, and
stops the stage. It does not silently label partial observation as a pass or
release the coordinator's lock. Native fixture failure and any final restore
failure must remain visible in the root report.

After reboot the harness reestablishes emulator-only root transport, waits for
adbd and verifies shell UID 0 before private state reads. If a prepared stage has
already crossed a boot, never rerun its `reboot` command: it intentionally refuses
that changed boot. Under the same held lock, the coordinator can call
`ensure_root_transport(device)` and continue `wait_event` for the saved prepared
stage, then native verification, preserving the pre-instrumentation observation
order. This repair changes transport privileges only and must not be reported
as an app restore or used to manufacture event evidence.

A denied event may leave a cached app process frozen. After independent read-only
observation is saved as `phase='observed'`, native Verify may nonstickily thaw
only that exact PID/start identity. Manual continuation calls
`instrument(device, 'bb7RebootVerify', observation=True, phase='observed')` (or
the update equivalent). Cleanup has a separate explicit cleanup phase. Never
thaw during the event observation window, open an Activity to bypass freezing,
set a sticky exemption, or change global freezer settings.
