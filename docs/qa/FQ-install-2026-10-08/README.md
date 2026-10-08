# FQ4–FQ8 phone-agent install certification (2026-10-08)

Finish line: each requested agent has device evidence for app installation,
pinned version, phone check, account-free failure behavior and cleanup, plus
cancel/retry and safely simulated storage-guard outcomes. Record failures and
missing product paths honestly; no account/model/chat qualification is implied.
Non-goal: signing in, changing accounts, clearing application data or filling
the emulator to manufacture storage failure.

Branch `sol/ba-install-cert` starts at coordinator `75ac5ae7`. Normal shared APK
is 2196 (`d777082c`, signer `1DE5BF08…`); only emulator-5554 is authorized.
Each device session uses `/home/eslam/Storage/tmp/oc-emulator.lock`.

## Discovery

Individual-agent removal has no public API or UI action. Cleanup certification
will distinguish bounded manual cleanup from an unavailable app uninstall path.
Only Claude and fx have supported production auth-probe scripts; unsupported probes cannot qualify
signed-out cells. Phone checks cover pinned installation, shared workspace and
Paseo hello, rather than target-agent inference.

## Backend fixes (source verified, device candidate not yet built)

Cancellation during local checks previously never reached the engine's local
cancel flag because no native owner existed yet. Pending architecture and
package-lock preparation could dispatch after cancellation too. The host now
fences each awaited preparation and allows only its own pending run to cancel
before handoff; exact durable-owner checks still protect other jobs afterward.
[Negative regressions](cancel-regressions-before.log): three behavior failures.

Target components omitted their catalog download sizes, making Oh My Pi's
native storage admission use only 300 MB. The host now carries the largest
supported pinned download into the target component, producing 561,073,088
bytes under the existing shared policy. Shared dependency guards are unchanged.
[Negative storage regression](storage-regression-before.log): OMP fails before
fix, fx minimum-floor control passes. [Focused host tests](host-focused-tests.log):
22 passed under machine_lock. [Analyzer](analyzer.log): no issues (42.4 s).

These source fixes do not belong to unmodified APK2196's device evidence.
Codex/Goose's extracted peaks exceed the shared doubled-download/minimum policy;
that separate shared-policy issue is recorded for BC in BA-status.md.

## Shared edge-case limits

[Android storage-policy proof](low-storage-policy-emulator.log) runs the actual
production `SetupDiskSpace` object in dalvikvm with deterministic `usableSpace`
values (0, threshold minus one, exact threshold and healthy-to-low). It performs
no download and fills no storage. The existing harness's device-lock wait was
extended in a temporary copy after contention; no policy or app was changed.
[31 focused storage tests](storage-focused-tests.log) passed under machine_lock.
These prove the policy, rather than an app installation with injected low space.

There is no individual-agent removal action. Each target's owned installation,
launch link, staging files and lock are manually removed only after setup is
terminal and no exact target PIDs remain. Shared Node/Paseo, Claude, account
homes, cached phone-gate metadata and conversations remain in place. This is cleanup proof; uninstall stays
partial until the product has a removal path.

Auth observations below execute the exact production `AgentPhoneScripts.authProbe`
as Ubuntu UID1000 in the matching saved phone-owner profile HOME; only state/error
are projected. They do not qualify the private MethodChannel bridge or a sign-in
journey. The UI's “Sign in needed” line alone does not prove a known auth state.

Account-free CLI probes use an isolated empty HOME and authored clean environment,
never authenticate, log in or send prompts. Raw stdout/stderr stay private and
are deleted; evidence contains closed states and counts only. Protocol startup
alone does not certify the app's unavailable authenticated conversation route.
The requests follow the [Codex app-server contract](https://developers.openai.com/codex/app-server/)
and [ACP session setup](https://github.com/agentclientprotocol/agent-client-protocol/blob/main/docs/protocol/v1/session-setup.mdx).

## Codex

[Device observations](codex-device.json), [validated report](codex-report.json),
[cancel screenshot](codex-cancelled.jpg), [phone check](codex-after-install-check.jpg),
[after cleanup](codex-after-cleanup.jpg).

On APK2196, app installation and fresh public phone check passed; the catalog
receipt and CLI both reported **0.160.0**. Free space before install was
1,884,884,992 bytes. Cancel at 4,288,512 / 109,304,578 download bytes produced a
cancelled native job; staging/lock vanished, leaving only an empty target parent.
A distinct retry job completed. The production auth-probe script returned `probeUnsupported`,
so the signed-out cell fails rather than inheriting a guess from the UI's
“Sign in needed” line. Empty-home app-server initialize and account/read returned
signed out, requires sign-in, in 2.63 s; no prompt/login or orphan process.
App launch qualification remains partial because the app cannot route this
unverified signed-out agent into a conversation. Scoped cleanup freed
**289,132,544 bytes** and left no target files, links, stages, locks or PIDs.
Uninstall and installed-app low-storage cases remain partial for the shared
limits above. Claude remained Ready after the completed public checks.

The early navigation/canonical-directory harness mistakes were corrected and
excluded from the final observations. They are not agent failures.


## Gemini

[Device observations](gemini-device.json), [validated report](gemini-report.json),
[cancel screenshot](gemini-cancelled.jpg), [phone check](gemini-after-install-check.jpg),
[after cleanup](gemini-after-cleanup.jpg).

Gemini CLI **0.62.0** installed via the app and passed a fresh public phone check.
The pinned receipt/link and CLI version matched. Free space before installation
was 1,885,786,112 bytes. Cancellation at 10,784,768 / 20,787,241 pinned download
bytes stopped the job and removed stage/lock; native total was temporarily zero
for the chunked response, so the catalog's known length bounds this observation.
A distinct retry completed. The production auth-probe script returned `probeUnsupported`; its signed-out
cell fails. Empty-home ACP initialize succeeded and session/new returned
`authentication_required` (-32000) in 2.28 s, with no login, prompt or orphan PID.
Full app launch remains partial. Manual cleanup reclaimed **99,860,480 bytes**;
no target files, links, stages, locks or processes remained. Uninstall and
installed-app low-space coverage retain the shared limits above. Claude/fx
phone-check summaries remained passed when this check finished.


## Qwen

[Device observations](qwen-device.json), [validated report](qwen-report.json),
[cancel screenshot](qwen-cancelled.jpg), [phone check](qwen-after-install-check.jpg),
[after cleanup](qwen-after-cleanup.jpg).

Qwen Code **0.24.7** installed through the app. Receipt/link, CLI version and
fresh public phone check passed. Before install, free space was 1,856,380,928
bytes. Cancel at 7,647,232 / 31,240,308 catalog download bytes (chunked native
total temporarily zero) stopped the job; staging/lock disappeared. A distinct
retry completed. The production auth-probe script returned `probeUnsupported`. Empty-home ACP
initialize succeeded, and session/new returned authentication-required (-32000)
in 4.60 s; no prompt, login or orphan process. The app launch cell remains
partial. Manual cleanup reclaimed **112,664,576 bytes**, with no owned target
leftovers. App uninstall and injected app low-space coverage retain the shared
limits. A stale startup sheet in the early driver was corrected before this
fresh phone check; it was a navigation issue, not an install failure.


## Goose

[Device observations](goose-device.json), [validated report](goose-report.json),
[cancel screenshot](goose-cancelled.jpg), [phone check](goose-after-install-check.jpg),
[after cleanup](goose-after-cleanup.jpg).

Goose **1.53.0** installed via the app and passed exact receipt/link/CLI version
and a fresh public phone check. Free space before install was 1,919,569,920 bytes.
Cancel at 5,926,912 / 94,629,008 download bytes stopped the job; a distinct retry
completed. Stage/lock cleanup passed. The production auth-probe script returned
`probeUnsupported`. Empty-home ACP initialize succeeded, but session/new
returned an internal RPC error (-32603) in 0.22 s. This is bounded rejection,
not proven signed-out or authenticated operation; raw technical output was not
exported. No prompt, login or orphan PID. App launch remains partial. Manual
cleanup increased free space by **298,692,608 bytes** and left no target payload,
link, stage, lock or PID. App uninstall and injected low-space limitations remain.
The reusable locked driver completed this replay, including waiting for
automatic and later phone checks before cleanup.


## omp-acp

Oh My Pi: [device observations](omp-acp-device.json),
[validated report](omp-acp-report.json), [cancel screenshot](omp-acp-cancelled.jpg),
[phone check](omp-acp-after-install-check.jpg), [after cleanup](omp-acp-after-cleanup.jpg).

The app installed **18.5.1**; receipt/link, CLI version and fresh public phone
check passed. Free space before install was 1,825,345,536 bytes. Cancel at
10,485,760 / 280,536,544 download bytes stopped the job and cleaned stage/lock.
A distinct retry completed. The production auth-probe script returned `probeUnsupported`. Empty-home
ACP initialized and created a session without a prompt in 10.53 s; this does
not prove inference or signed-out status. No login, prompt or orphan PID.
App launch qualification remains partial. Scoped cleanup increased free space
by **280,174,592 bytes**; no target payload/link/stage/lock/process remained.
App uninstall and injected app low-space cases retain the shared limitations.
The earlier explicit check missed an oversized merged control, so the final run
was repeated with the corrected action bounds and all public check summaries
completed before cleanup.


## fx

[Device observations](fx-device.json), [validated report](fx-report.json),
[cancel screenshot](fx-cancelled.jpg), [phone check](fx-after-install-check.jpg),
[after cleanup](fx-after-cleanup.jpg).

The app installed **0.0.12**; receipt/link, CLI version and fresh public phone
check passed. Free space before install was 1,901,670,400 bytes. Its supported
production auth-probe script returned named `signedOut` in the saved app-owner
profile context. Empty-home ACP initialize returned authentication-required
(-32600) in 0.14 s; no login, prompt or orphan PID. This is not sign-in or bridge
qualification. Full app conversation launch remains partial.

An unlimited-speed pilot completed before the cancel tap. The final cancellation
case required the observed unlimited network baseline, then temporarily limited
the emulator to 1024 kbps under its lock. Cancel at **4,096 / 5,482,652 bytes**
stopped the job and removed stage/lock. Unlimited rates were restored before
the distinct successful retry, with error-path restoration too. Latency was
unchanged; no storage was filled. The emulator version was 36.2.12.0. Scoped
cleanup increased free space by **11,952,128 bytes**, with no target payload,
link, stage, lock or PID. App uninstall and injected app low-space limits remain.

## Batch outcome

All six targets pass actual app install, exact pinned version, fresh public
phone check and observed partial-download cancel/retry. The signed-out script
cell passes for fx; the other five fail with explicit `probeUnsupported`.
Account-free protocol starts were bounded without login or prompt, but full
app launch remains partial. Uninstall stays partial because the app has no
removal action; measured scoped payload cleanup passed. Low-space stays partial
because only the production policy harness can safely inject the threshold.
No authenticated/model/chat capability was enabled. Claude's reviewed runtime
cells are preserved; both arm64 and x64 capability tests keep the six targets
closed. Source fixes were tested with failing-before controls; no candidate APK
was built, so their device activation awaits the coordinator's integration.


## Final verification and device restoration

[Focused integration tests](final-focused-tests.log): **110 passed** across
`agent_certification_test.dart`, `phone_agents_controller_test.dart`,
`agents_settings_placement_test.dart` and `file_size_ratchet_test.dart`, serially
through machine_lock. [Final analyzer](final-analyzer.log): clean (53.0 s).
[Generator tests](generator-final-tests.log): 14 passed. All six report projections
and the bundled snapshot pass their generator `--check` modes. Pinned Dart
language-version 3.10 formatting and Python syntax checks passed. No full suite
was run. Claude/OpenCode runtime rows are unchanged from branch base 75ac5ae7.

[Final device state](final-device-state.json) and [Agents screenshot](final-agents.jpg)
confirm installed APK2196 equals the original SHA-256
`78074bc8c8f3244b252ae11f23cb3dc4d748b989c95c33e25111f6c16716af56`.
All six owned target inventories have zero allocated bytes, no payload/link/stage/
lock and no target PID. Unlimited upload/download rates are restored, available
space is 1,901,592,576 bytes, and Claude remains Ready. Its screenshot still
shows “Can't reopen old conversations”; this batch does not claim a deployed
resume-capability fix. No app uninstall, data clearing, account change, signing
change, push or publication occurred.
