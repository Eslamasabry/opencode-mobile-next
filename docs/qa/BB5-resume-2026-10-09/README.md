# BB5 crash-resume qualification

Worktree: `oc_app-sol-bb`, branch `sol/bb-runtime`.

The frontend merge is local commit `18c56b149`; no rebase or publication. The
normal restore is version2202 with SHA256
`e63fb2e4ff32280ad4c739aee9c17db508eab2e99a42573c4e83bd66dc0babb0`,
using the existing local signer. Every device session holds the shared emulator
lock and validates this normal artifact before mutation.

## Completed checkpoints

- Initial merged candidate:55 affected Dart tests,131 native tests and clean
  analysis. Pinned release QA app and runner compiled under the shared build
  lock with fresh memory admission,4GB Gradle heap and two workers. Exact owned
  daemons and this checkout's intermediates were cleaned. See the build logs,
  `candidate.json`, and `signed-qa-artifacts.json`.
- First device attempt exposed `bb5_fixture_owner_changed` before idle timing.
  The private fixture's synthetic owner raced the real controller binding.
  `device-session.txt`, `device-failure.json`, and `final-restoration.json` retain
  failure and restored-normal evidence.
- Revised fixture retains the saved owner, recipe, budget and helper home.
  `release-runner-v2-retry.txt` proves compilation; the preceding v2 log is a
  failed invocation and is not counted as success. `device-session-v2.txt`
  reached runtime preparation and the real idle wait, then refused incomplete
  helper completion. Native cleanup and normal2202 restore passed.
- The original28-second observer is shorter than the current explicitly timed
  Dart path (up to86seconds before unbounded reconciliation). V3 allows100seconds
  as a diagnostic budget and never completes over a live/in-flight helper.
  `fixture-guard-v3-red.txt` is a behavioral old-budget mutation failure;
  `fixture-guard-v3-green.txt` contains6 passing JVM guard tests.
- Host evidence now retains only allowlisted boolean milestones even on native
  failure. `observation-host-red.txt` shows the missing behavior;
  `observation-host-green.txt` contains40 passing tests. False/missing native
  observations are recorded as unproven, never as success.

## Current limitation

BB5 remains device-unqualified. The v2 run does not prove Dart automatic helper
return. A separate failing-first controller regression confirms that a same-owner
transport reconnect invalidates a pending idle helper restore. Its correction
passes22 idle controller tests, including owner/lifecycle/native revocations.
The initial two same-owner regression cases failed before the fix; all three
same-owner cases (including a protocol alias) pass after it. The full controller
file has126passes and3failures; the same resumed-title, profile-deletion and
sign-in-reset failures reproduce with the correction removed. These baseline
failures are retained in `controller-baseline-failures.txt` and left unchanged.
The corrected app and V3 runner still require rebuild and device proof.
BB7 is a design plan only and is not counted as implemented or verified.

No physical-phone, real-agent authentication, full-suite, release or deployment
claim is made. The shared normal2202 app is restored after each attempt.

## Third candidate, before installation

Commit `7efee4661` fixes same-owner idle cancellation. Analysis is clean. The
corrected app built in217.4seconds; its new runner built in1m41s. Fresh available
memory was7081MiB and8060MiB respectively; both used the required4GB/two-worker
caps, cleaned their exact owned daemons (3576748 and3585160), and removed their
intermediates. `candidate-v3.json` and `signed-qa-artifacts-v3.json` bind source
and artifact identities. No watchdog abort.

The first V3 preinstall check refused the GenUI MCP child introduced by the
required frontend merge, before any QA installation. The normal2202 app stayed
healthy. `genui-source-comparison.json` and `genui-history-comparison.json` show
that the installed helper exactly matches generated source at immutable commit
`6015aea474e470ada3b3a661f6165b64682d7cf9` (18204bytes, SHA256
`19be25af7575ac6e17958980cb07d4e253f0f32a20f5ad2d65e545be96bf8a7e`). It differs
from the current helper. Narrow read-only admission is being updated to accept
only a verified app-authored helper, never an arbitrary Node process. This
setup admission is not proof of logical chat idleness.

The GenUI setup admission now passes44 host tests, including exact source
regeneration and adversarial children/path/content checks. The original host
fails its new positive case with `bb5_initial_non_server_payload_refused`.
`device-session-v3.txt` confirms this admission on the actual emulator, but the
native scenario then refused `bb5_other_process_refused` before entering idle.
`final-restoration-v3.json` confirms normal2202 restored, healthy, idle policy
still off, and no private fixture. No idle behavior was qualified by that run.

The V4 private runner adds bounded startup settlement before any fixture write:
it repeats the same complete quiescence proof for at most15seconds, preserving
persistent refusal and immediately failing owner/foreground revocation. The
real production idle gates are unchanged. Nine JVM tests pass; an immediate-only
mutation fails three behavioral tests. See `startup-settlement-red.txt` and
`startup-settlement-green.txt`. V4 Android compile passed in1m38s with7830MiB fresh admission; owned daemon3604225 and intermediates cleaned. Signer/hash/source checks pass; target app SHA is unchanged. Device validation is pending.
