# Engine device E2E fixes

Finish line: verify release APK executable hashes and run real no-model safety,
rollback, and idempotence controls on Pixel_6/API 34 and OC_API35/API 35.
Non-goal: UI, model turns, release delivery, or enabling unproven confinement.

## Implementation and coordinator handoff

P0-1 preserves all three `libaiteam_*.so` executables per supported ABI and adds
an automatic actual-APK hash check to release assembly. The checker rejects
stripped/missing/extra/duplicate libraries and mismatched embedded manifests.

P0-2 probes Landlock query/create/add/restrict in an exact disposable child,
reaps it with a bounded timeout, disables core dumps, and returns normal exit
78 on seccomp SIGSYS or unsupported ABI. Native setup then returns
`boundary_unsupported` before daemon startup. There is no permissive fallback.
The no-authority proot diagnostic uses actual isolated controls and a tracer
escape; it never attests a successful boundary or exposes actual private repos.

B-3: the confirmed setup stop opts in with
`BuiltinLinux.stopServerForPhoneEngineSetup()`. An ordinary stop does not
create a rollback ticket. Failed activation restores that setup's captured
server; a repeat start with the same script/port preserves its uptime/process.
The coordinator must use the additive flag and retain its restore/reconnect
flow on every failure. At the pre-server step it must gate on `health.boundary`,
then start OC1 and wait for `canExecute`; requiring OC1-dependent execution
while OC1 is stopped is circular. See the design contract for the full handoff.

B-1 was the old Gas City/Dolt recovery path, not the Rust engine: background
observation previously allowed six minutes. Healthy city reuse avoids blocking
registration, recovery observation is bounded to 60 seconds and cancelable,
and late responses cannot mark canceled work ready. Explicit cold legacy
startup retains its longer allowance. No database acceleration, old-profile
migration, or fresh model latency fix is claimed.

## Verification

- Rust focused crate: 128 passed, 0 failed, 0 ignored (including the inherited
  seccomp query/create/add/restrict and bounded-child regressions).
- Pinned Flutter: 113 focused tests passed across the ten affected backend files;
  analyzer clean. The final controller run passed all 21 tests, including safe propagation of
  `boundary_unsupported`; final analyzer is also clean.
- Python packaging regressions: 14 passed. A consistent but stale native bundle
  is rejected after Rust source changes. APK 2080 itself fails the new source
  freshness check with `stale_engine_sources_rebuild_and_stage`.
- Minified release preview assembly plus release AndroidTest assembly passed;
  actual APK hashes for all six executable entries match the manifest.
  Preview-only keep rules retain the Kotlin/app ABI used by instrumentation;
  stable release R8 behavior is unchanged.
- Pixel_6/API 34 and OC_API35/API 35, x86_64, both passed the no-model device
  regression under `/home/owner/Storage/tmp/oc-emulator.lock`, serial
  `emulator-5554` only. See [API 34](api34-controls.log) and
  [API 35](api35-controls.log). Both report launcher `kernelExit=78`, typed
  `boundary_unsupported`, daemon stopped, no boundary/execution authority,
  failed setup restores its tracked server, repeated identical restart preserves
  uptime, and ordinary intentional stop remains stopped.
- The rollback fixture is a tracked `sleep` service, not an OC1 HTTP-health proof.
  APK 2080's integrated UI also restored its real OC1 server after the failure.
- Proot view positive write/read/stat/readlink/Git controls and canonical-path,
  proc-alias, parent-environment/cmdline and FD negatives passed on both AVDs.
  Killing the verified isolated tracer was permitted: `tracerEscapeDenied=false`,
  `complete=false`, while `fixtureUnchanged=true`. This does NOT demonstrate a
  successful private-file write. It also does NOT prove proot as an authority
  boundary. No fallback receipt or execution capability is issued.

## APK 2080 startup diagnosis

Installed the exact requested APK on OC_API35 with its existing stable app data,
then followed Settings → AI Team → Turn on → Stop and continue. The failure
reproduced, and the existing UI restarted OpenCode.

[Sanitized logcat evidence](2080-api35-startup-signals.log) identifies the actual
processes: sandbox launchers 3821 and 3841 received SIGSYS / SYS_SECCOMP on syscall
444 (`landlock_create_ruleset`). These were launchers, not an authenticated
ready engine. No daemon exit code was observable because that build discards its
stderr; we do not infer a daemon exit reason from the generic UI message.

The APK manifest declares source revision `e3b2bb8f` and source SHA
`becac7ee8ade2fc1f75cc3b202e7c2829329e1a1940dc0367c9c1766b08de3e0`.
Its x86 sandbox is
`052e4914e38e8696ad2b9a0d0986b31291754dc58cc280e6c6e3d99cad326108`.
It therefore contains the pre-fix executable despite the integration's newer
Rust source commits. Preserving symbols fixed stripping, but source commits
alone did not rebuild and stage ELF outputs. The current staged sandbox is
`15f6b74bd74a23eee241d9faaf46f34f16ef92c14a3d185a6c17534abbc996bf`.

The correction stages the safe dual-ABI binaries, rejects stale staged sources
at release assembly, and preflights the kernel before the full boundary proof or
daemon launch. On these AVDs the supported result is a normal, typed refusal.
Protected Ready and a model task/merge/promote journey remain unavailable on
these devices; no physical-phone compatibility claim is made.

## Artifact hashes

Final minified preview APK SHA-256:
`223b1c60b72281577999de05eb078f59f242db2183afde7d97aa3bb491727875`.
Final AndroidTest APK SHA-256:
`7a767f84413f600c886c5327653cb0a3d38a48c295b8b1957bf7edcbef85fbbe`.
These are emulator QA artifacts with the known debug/local signer
`1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`;
no production release, push, delivery or publication was performed.

Final manifest x86 engine SHA-256:
`bf9619e361d1f24be8f8cb731854328f6a507115fd6be7c147ab5567efcf52af`.
ARM64 engine SHA-256:
`384709314221bec07237fdbfcad2cf82ce8b1e638a9a04bd2e758e2051eccda2`.
ARM64 sandbox SHA-256:
`d6f7072eb22ee023182af4037996c7f41b3cf986e216db598fe082b7cbf6c2e9`.

The owner need not change phone state for this fix. The coordinator should
merge this branch's staged binaries and native preflight, use the additive setup
stop method, and build with the automatic source+APK gate. The phone will run
its own exact-binary proof; these emulator refusals are not physical-phone proof.
