# BA private auth investigation — 2026-10-08 / 09

READY FOR REVIEW: the concurrent-helper cleanup defect is fixed and verified offline. The original2196/2197 device comparison remains unqualified; no replacement APK was built or installed.

Branch `sol/ba-probe-fix`, base `feat/genui-fe` `9485f0c7c7c39e721d50fb036a76ae77596d14f0`.
Finish line: identify the cause of Claude being Ready on2196 and asking for sign-in on2197, repair only a demonstrated product regression, and verify without changing the Claude account. Non-goals: logout, account-data repair, project deletion, relaxing unknown-ownership gates, or changing another lane's worktree.

## Established observations

- [Original UI and direct CLI](initial-observation.jsonl): normal2197 Agents page says Sign in needed. A direct status command in the canonical owner home returns exit0, valid auth schema, and loggedIn=true. Only these booleans were retained; no account values or raw output.
- [Authored script](script-observation.jsonl): the exact app-authored bounded Python status script returns signedIn in that existing account home.
- [App security context](app-context.json): SELinux remains Enforcing. Diagnostics change only their own process UID/context to the app's observed UID/MCS; no device-wide policy changes.
- [Installed launcher refusal](installed-launcher-refusal.json): APK2197's actual `BuiltinLinux.startAgentProcess` route refuses before the CLI launches. The fixed exception category is State; native frame `ab.m0:21` maps to `projectStorage.prepare()` at `BuiltinLinux.kt:606` in the2197 release mapping.
- [Project structure](project-metadata.jsonl): persistent `files/projects` and hidden legacy `files/linux/ubuntu/root/projects` are both real, nonempty directories. No directory entries or contents were exported.
- [Packaged private probe](packaged-probe-real.json): the installed2197 private `agentAuthProbe` itself returns error/hostUnavailable against the real project backing.

BC's current FQ9 report and BB's `docs/qa/BB-upgrade-2026-10-08/README.md` identify the sole hidden legacy entry as FQ9's externally seeded fixture. The collision guard already exists in2196. The coordinator withdrew BB's broad recovery draft; BC owns exact fixture cleanup and rerun. BA has not moved, deleted, or modified those projects or any account home. This contaminated-device refusal must not be presented as proof that2197 introduced a project migration regression, or as complete explanation of the earlier clean comparison.

## Withdrawn candidate

An early isolated Runtime diagnostic had a same-UID `su` parent in a different SELinux domain. Its unreadable proc metadata caused inventory failure. Changing the diagnostic wrapper to drop its own UID/context while keeping root-only ancestors made ORIGINAL production Runtime return signedIn too. Thus the proposed signal-zero inventory filter was not established as the app regression fix. Its production diff and new harness files were withdrawn; candidate logs are outside the repository. No ownership guard has been relaxed.

Source comparison with BD's BB8 tip `e5b899da86c82cb01c89a08ced69eb8ceec59af3` found unchanged authored script bytes/digest, private profile exports, redirection, base PRoot argv/environment and fake-proc bindings. FE adds cold component admission before guest launch. Current installed-launcher evidence identifies a separate project admission refusal before authentication runs; it does not resolve the earlier clean comparison.

## Reproducible packaged control

The diagnostic entry point in [driver/ProbeRunner.kt](driver/ProbeRunner.kt) loads production launch/auth classes directly from the installed APK. It does not compile or substitute production Runtime. Immutable2197 dex inspection freezes exactly one private status route: MethodCall container `ke0(5, "agentAuthProbe", args)`, then `pi0(linux, call, 0).a()`. Other class IDs and `pi0.n()` include mutating routes and are never invoked. This ABI is specific to2197.

Run from the repo root with the existing owner profile ID, never a new account:

```sh
python3 docs/qa/BA-probe-fix-2026-10-08/driver/build.py --build-dir /tmp/ba-private-probe --profile-id OWNER_PROFILE
flock -w 1800 /home/eslam/Storage/tmp/oc-emulator.lock python3 docs/qa/BA-probe-fix-2026-10-08/driver/run_context.py --build-dir /tmp/ba-private-probe --profile-id OWNER_PROFILE --mode real
flock -w 1800 /home/eslam/Storage/tmp/oc-emulator.lock python3 docs/qa/BA-probe-fix-2026-10-08/driver/run_context.py --build-dir /tmp/ba-private-probe --profile-id OWNER_PROFILE --mode fixture
```

Every compiler is serialized through machine_lock with a256MiB JVM cap; no Gradle, Kotlin Gradle task, or APK build. `fixture` redirects only the project-storage fields of a newly constructed diagnostic host object to a newly created empty cache fixture. Real project paths, runtime rootfs, component admission and canonical auth account home are untouched. The fixture is removed by that diagnostic object on completion. This control distinguishes project admission from the packaged private probe; it does not qualify UI behavior, real-app hidden-API enforcement, startup concurrency or a replacement APK.

Only fixed state/error categories and structural booleans leave memory. No CLI output, account labels, tokens, provider data, exception messages or transcripts are retained. Normal2197 is required before running; no app reinstall/uninstall or data clear occurs. The runner checks normal version and removes its own cache afterward.

## Demonstrated concurrent-helper defect and fix

`PhoneAgentAuthProcessTree.drained()` previously counted every new same-UID process outside its private ledger as unknown. A Paseo/version helper can start after the private probe takes its baseline: the successful auth result then becomes hostUnavailable, and the retired probe cannot drain while that independent helper stays alive. Startup row refresh starts source/helper synchronization asynchronously, so this is a real concurrency path. A deterministic test launches that work between baseline and cleanup and verifies two consecutive signedIn results without stopping the helper.

`BuiltinLinux.startAgentProcess` now registers non-private launches with a dedicated `PhoneAgentAuthOtherOwners` ledger. It captures the PID and kernel birth tick immediately, rechecks both identity and Process liveness, and prunes unreadable, dead or reused entries. Private-output auth launches are never registered there. Runtime passes this registry to the private tree; observed helper descendants retain their exact identities after reparenting. Private identities always win, helpers receive no auth cleanup signals, and unobserved or reused orphans remain blocking. Callbacks never acquire the BuiltinLinux monitor while the tree holds its ledger, avoiding lock inversion. Cold census, component admission, stale OAuth-lock checks and private-output projection are unchanged.

This repairs a demonstrated source defect. It is not yet proof that this race caused the coordinator's original2196/2197 comparison.

## Validation and outstanding work

Pinned Flutter pub get ran once. Each test file ran alone through machine_lock with concurrency1:

- `test/phone_agent_auth_native_test.dart`: baseline38 passed; new cases initially42 passed/5 failed; fixed47 passed. Reverting only the outside-owner drainage exclusion reproduces the concurrent-helper failure; restored source47 passed again.
- `test/phone_agent_auth_other_owners_test.dart`: registration scaffold7 passed/1 failed; implemented8 passed, covering private exclusion, unknown launch, dead/reused identities, inaccessible metadata and invalid birth ticks.
- `test/agent_auth_probe_test.dart`:12 passed, synthetic accounts only.
- `test/builtin_linux_test.dart`:23 passed.
- Focused analyzer on both changed Dart tests and the diagnostic script: no issues.
- All production PhoneAgentAuth Kotlin sources plus PhoneAgentPaths compile against SDK37 with a256MiB JVM cap, without Gradle. This is not a full BuiltinLinux/Flutter APK compile. Read-only native security review approved exact ownership and lock ordering.

Normalized [checks.json](checks.json) records commands, results and source digests. Test output contains only synthetic data and fixed diagnostic categories. Diagnostic Kotlin/SDK37, D8 and tiny NDK compilations ran serially with256MiB caps.

The `flock -w1800` packaged project control timed out after30 minutes on2026-10-09 without running any device command; another lane continued holding the lock. BC owns the FQ9 fixture cleanup/rerun. Latest available memory3901MiB is below the required6144MiB, so no Gradle/APK build was admitted. Normal2197 remains installed; BA has not logged out Claude, cleared app data, replaced the APK, or edited account/project data.

Outstanding: after BC restores its fixture and releases the emulator, run the packaged control/real probe and re-observe the actual Agents state; when memory admission permits, compile/install the repaired APK under the required cap and prove the helper race no longer invalidates auth, restoring2197 afterward. The original device regression cannot yet be declared fixed.
