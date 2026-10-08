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

Device evidence and per-agent outcomes are in progress.
