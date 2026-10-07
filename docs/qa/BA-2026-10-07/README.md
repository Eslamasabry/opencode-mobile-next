# BA stability handoff — 2026-10-07

State: BLOCKED, with local implementation/fixtures committed on sol/ba-agent-cert.
Product-code candidate: ae7e787a (plus removal of two redundant test imports).
No UI/Kotlin changes, full suite, APK build/install, push, PR, signing or release.

| Item | State | Evidence and next dependency |
| --- | --- | --- |
| BA5 | blocked | Owner migration/switch/cache/deletion implemented and tested; actual emulator switch/restart journey needs signed candidate. Commits 1f8a87c5, fa032af2. |
| BA6 | blocked | Qualified OC1 dialect + pinned healthy server/MCP verifier implemented; chat/list answer receipts need candidate. Commit 132f5fc9. |
| BA1 | blocked | Claude/fx CLI script captures meet <10s; private native method absent. BB request in BA1 contract. Other uninstalled agents explicitly unsupported. Commit 51d8743f. |
| BA2 | blocked | Real Claude/fx provider loading/final captures plus labelled synthetic row coverage; missing full per-agent account/capture matrix (OW1). Commit 73faf208. |
| BA3 | blocked | Probe-only confirmation/stale-result fencing implemented; private native method + frontend removal of terminal row fallback needed. Commit 51d8743f. |
| BA8 | blocked | Account/logout contracts and verification implemented; private bridge + frontend account/sign-out integration needed. Commit 51d8743f. |
| BA7 | done | Actual connection timers classify all three outcomes, bounded probes, wait suppression and stale results tested; frontend consumes turnStallFor. Commit ae7e787a. |
| BA4 | skipped | FQ1 matrix absent. Data shape in BA4-contract; no invented capability wiring. |

## Local checks

Pinned Flutter 3.47.1 pub get passed once. All Flutter commands used the shared
machine lock and --concurrency=1. No full repository suite run.

- Ownership + controller boundary: 67 passed before later auth/watchdog additions.
- Card installer/adapter boundary: 71 passed. Reverted BA6 production files
  caused three behavioral failures; restored four targeted cases passed.
- Integrated affected batch: 127 passed across owner, script/probe, host,
  provider snapshot, tracker/controller and adapter files before native bridge
  correction. Subsequent relevant changes were verified in affected files.
- Final native-contract correction: 86 host/controller tests passed, including
  absent private bridge, native Claude fallback, auth/logout/stale fencing,
  protocol switching, and all three watchdog class timer cases.
- Script/model/provider/tracker tests passed in the preceding 115-test batch;
  their production logic was unchanged by the native channel correction.
- Final flutter analyze --no-pub: No issues found. Changed Dart files formatted
  with --language-version=3.10; git diff --check clean.
- Negative controls for BA1, BA2, BA3, stale BA3, BA7 and BA8 each failed
  behaviorally; safeguards restored. Logs in each item QA directory.
- Empty gate migration test failed before refinement (empty vs checked owner)
  and all three ownership tests passed after it.

Device evidence is limited to short locked read-only CLI/provider captures on
emulator-5554, documented per item. Existing Claude account was not logged out.
The multi-agent private bridge is NOT present in this base; generic agent-user
setup run intentionally strips auth JSON. Do not bypass its output filter.

## Integration handoff

Read docs/design/BA1-contract.md for the exact BB private agentAuthProbe request;
read BA3/BA8 contracts for the optional PhoneAgentAccountSource frontend API.
The host hides new logout until the bridge responds and retains existing direct
Claude native status as fallback. Unavailable agent auth is a final typed error.

The worktree has no android/key.properties. Existing emulator signer 1DE5BF08
configuration was requested; no replacement identity generated. BA5 and BA6
must not be called Done until a same-signer candidate runs their device journeys.
Claude coordinator owns merging, full suite and frontend. Every commit has
[skip ci]; nothing pushed, tagged, published or deployed.
