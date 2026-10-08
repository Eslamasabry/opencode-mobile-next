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
Only Claude and fx have supported auth probes; unsupported probes cannot qualify
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
homes and conversations remain in place. This is cleanup proof; uninstall stays
partial until the product has a removal path.

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
A distinct retry job completed. The app auth probe returned `probeUnsupported`,
so the signed-out app cell fails rather than inheriting a guess from the UI's
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
A distinct retry completed. App auth returned `probeUnsupported`; its signed-out
cell fails. Empty-home ACP initialize succeeded and session/new returned
`authentication_required` (-32000) in 2.28 s, with no login, prompt or orphan PID.
Full app launch remains partial. Manual cleanup reclaimed **99,860,480 bytes**;
no target files, links, stages, locks or processes remained. Uninstall and
installed-app low-space coverage retain the shared limits above. Claude/fx
phone-check summaries remained passed when this check finished.
