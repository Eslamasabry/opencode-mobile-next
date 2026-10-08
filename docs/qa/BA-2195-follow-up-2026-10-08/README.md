# APK 2195 backend regressions

Finish line: OpenCode 1 Cards pass the strict live verifier after a runtime
switch, and a completed phone agent keeps its checked result while later agents
are checked, with failing regressions and emulator evidence.
Non-goal: qualifying OpenCode 2 Cards, weakening phone/runtime checks, changing
credentials, UI/Kotlin edits or publishing a build.

Base `e2b3fe1664659e26b3316bd53ab5790dbd39e1aa`, branch `sol/ba-fixes-3`.
Device sessions use emulator-5554 only, each guarded by oc-emulator.lock.
No uninstall or data clearing. Original APK 2195 must be restored after proof.

## Diagnosis on installed APK 2195

[Before](before-cards.jpg) shows the failed OC1 Cards status despite its connected
server. The exact unchanged production OC1 verification script executed as the
app UID in its Ubuntu view [exited zero](device-verifier-exit.json). Sanitized
[health/MCP checks](device-initial.txt) and [manifest guards](device-manifest.txt)
confirm OpenCode 1.18.32, connected oc-ui, the correct owner, matching helper hash,
and the approved `/opt/node/bin/node` path. No credentials were captured.

The retained result came from staging both runtimes while OC2 was active. Setup
and attempt caches were keyed only by their shared owner, surviving the runtime
switch; Check this phone did not retry Cards. Connection retirement now clears
qualification and fences late results. Requalification runs once the new
transport is connected, including All projects with no selected directory.
Phone checks also retry a current verification failure when Cards are enabled.
Queued setting writes still drain, preserving an explicit disable.

The phone-row race was an inventory scan started by install's early done event,
before the final gate was saved. The controller reused that stale in-flight
scan when the check completed. It now drains that scan and reads the persisted
gate afresh before publishing the result and returning to the next agent.
Failed rechecks still revoke readiness; retired hosts cannot publish results.

## Focused verification

[Negative control](negative-control.txt): reverting the connection fixes to the
base made five new behavior regressions fail. The disabled-Cards and failed
recheck controls passed. [Phone publication before](phone-check-before.txt) and
[Cards before](cards-before.txt) record the initial failures.
[All projects before](all-projects-before.txt) separately failed before moving
qualification above the directory guard.

[Final tests](test-final-result.txt): 195 passed, serially through machine_lock:

- test/gen_ui_controller_test.dart
- test/phone_agents_controller_test.dart
- test/gen_ui_install_test.dart
- test/phone_agents_host_test.dart
- test/agents_settings_placement_test.dart
- test/file_size_ratchet_test.dart

[Final analyzer](analyzer-final-result.txt): no issues (16.3 s). Changed files
formatted with the pinned Dart SDK and `--language-version=3.10`.
[Candidate hashes](candidate.sha256) identify the source/test snapshot.
No full-suite claim. Contracts: [BA5](../../design/BA5-contract.md) and
[BA6](../../design/BA6-contract.md).

## Release and device proof

The signed x64 release build passed (513.7 s), including packaged engine hash
checks: [build log](build-result.txt), [artifact hashes, signer and cleanup](build-cleanup-and-signers.json).
Candidate source is commit `20752b84`; APK SHA256 is
`3c9de78242b9880f53f646c778f30779596ad6eae3326fc2820330b55e94a7ce`.
Both candidate and original use the unchanged `1DE5BF08…` certificate.
Only this worktree's newest APK remains; intermediates and generated Kotlin
cache were removed, its Gradle daemon exited, and its temporary signing-config
symlink was removed. The metadata retry required no tracked native or toolchain changes.

The first native attempt used --no-pub and retained the generated dev-plugin
registrant: [failure](build-metadata-failure.txt). The normal release command
regenerated it; dependencies and the checked source snapshot stayed unchanged.
The emulator also needed a data-preserving reboot after Android's package
installer failed during a stuck boot fallback. No uninstall or data clearing.

- **Phone checks:** [Ready screenshot](claude-ready-during-fx.jpg),
  [final check screenshot](phone-check-after.jpg),
  [captured states](device-phone-proof.json), [session](device-phone-session.txt).
  Frames 13–19 simultaneously report Claude Ready, its successful check summary,
  and Checking fx. Frame 20 reports both checks complete. The screenshot shows
  Claude's Ready row; the fx progress text is below the viewport and recorded
  in the accessibility capture. Final persisted gates remain authoritative.
- **Cards:** switched the candidate from connected OpenCode 2 to connected
  OpenCode 1 in All projects. [Screenshot](cards-after-runtime-switch.jpg) and
  [12 captured frames](device-cards-proof.json) show
  `On for Claude Code, OpenCode 1` without a failure. The initial capture
  assertion expected a trailing period, which the fully qualified copy omits;
  [semantic validation](device-cards-proof-validation.json) verifies the
  runtime transition, both qualified names, and restoration from the captures.
  The [session log](device-cards-session.txt) retains that assertion failure
  transparently; it is a capture-copy mismatch, not an application failure.

[Final restoration](device-final-restoration.json) verifies the installed APK's
bytes match the original shared APK 2195, version code 2195, and active runtime
v1. All device interactions were limited to emulator-5554 under its shared lock.
Contracts and focused checks are complete; no push, publication or release.
