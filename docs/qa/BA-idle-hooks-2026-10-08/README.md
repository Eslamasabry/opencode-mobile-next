# BA idle hooks — offline evidence, 2026-10-08

Finish line: BB5/BB7 can read conservative local-agent busy truth and restore
only the previously live helper belonging to a native idle generation, in the
foreground, including with no existing host/backend after a cold process.
Non-goal: native idle admission, manual server startup, UI changes or device
qualification. Branch `sol/ba-idle-hooks`, base `feat/genui-fe` `e01237d2f`.

## Implemented

The exact controller hooks and BB obligations are in
[BA-idle-hooks-contract.md](../../design/BA-idle-hooks-contract.md).
Local gateway inventory covers every folder/provider and independent child
work. Unknown/incomplete/disconnected state denies idle; no lease timeout
supplies idle truth. Request-start revisions preserve intervening pushes, and
stale archived/absent snapshots cannot erase observed active work. New parents
require child coverage before idle, including those discovered via turn,
permission or subagent events.

Restoration validates native generation/intent, canonical owner, foreground and
disposal; coalesces requests; reads only the existing secure secret; dispatches
`idleResume`/`expectedIdleGeneration`; requires pinned Paseo 0.9.2 hello within
30 seconds; then reconnects existing sources/feed without creating/resuming a
chat. A final native receipt rejects Stop/revocation during feed reconciliation.
Ordinary row/watchdog/source starts use foreground/idle gates and a production
callback checked after secure storage returns, before dispatch. No missing
secret is created by guarded restore. A helper not previously live stays off.

The obsolete, unreferenced `_paDisposeBackend` helper was removed to keep the
analyzer clean; owner-scope capture/drain remains the active cleanup path.
No ratchet baseline, UI, Kotlin, credential format or certification changed.

## Final focused gate

Pinned Flutter 3.47.1: pub get completed once before work. Changed Dart files
formatted with `dart format --language-version=3.10`. Every check ran serially
through `tool/qa/machine_lock.sh test --`; no full suite or build was run.

```sh
tool/qa/machine_lock.sh test -- <pinned-flutter> analyze --no-pub
tool/qa/machine_lock.sh test -- <pinned-flutter> test --no-pub --concurrency=1 <one-file>
```

| File | Passed | Log |
| --- | ---: | --- |
| paseo_idle_busy_test.dart | 19 | [gateway](final-paseo_idle_busy_test.log) |
| phone_agents_idle_host_test.dart | 16 | [host](final-phone_agents_idle_host_test.log) |
| phone_agents_controller_test.dart | 118 | [controller](final-phone_agents_controller_test.log) |
| phone_agents_host_test.dart | 24 | [host regressions](final-phone_agents_host_test.log) |
| paseo_gateway_test.dart | 37 | [gateway regressions](final-paseo_gateway_test.log) |
| paseo_payload_use_test.dart | 10 | [payload use](final-paseo_payload_use_test.log) |
| paseo_chat_feed_source_test.dart | 15 | [feed](final-paseo_chat_feed_source_test.log) |
| agents_settings_placement_test.dart | 4 | [settings](final-agents_settings_placement_test.log) |
| file_size_ratchet_test.dart | 2 | [ratchet](final-file_size_ratchet_test.log) |

**245 passed**, all nine manifest files completed. [Analyzer](analyzer.log): no
issues. [Manifest](test-manifest.txt), [completion ledger](completed-files.txt)
and [candidate Dart source hashes](candidate-sources.sha256) identify the exact
candidate. Largest changed library: gateway.dart 1488 lines, under 1500.

## Failing-first controls

These logs contain runtime assertion failures before their corresponding fix:

- [Controller baseline](controller-negative-control-runtime.log): four cases
  covering cold coalescing, conservative busy truth and ordinary/background gates.
- [Host races](host-race-negative-control.log): native generation changes during
  secret/hello awaits and positive-generation automatic startup.
- [Stale liveness](controller-liveness-negative.log): connected unknown inventory
  was incorrectly labelled idle by an earlier not-running observation.
- [Feed revocation](controller-native-race-negative.log): revoked native generation
  during feed reconciliation incorrectly reported restore success.
- [Ordinary start](host-ordinary-negative.log): foreground lost during a secure
  read still dispatched helper startup.
- [Inventory races](gateway-race-negative.log): a new parent during child reads
  and a running push during a stale fetch both incorrectly reported idle.
- [Overlay/ordering](gateway-overlay-negative.log): closed/error parent child
  work, malformed completion ordering and stale archival erased busy truth.
- [Unseen turn parent](gateway-unseen-negative.log) and
  [unseen permission/child parents](gateway-unseen-permission-negative.log):
  completion/resolution reused idle proof without that parent's child inventory.

Deliberate post-fix reversions reproduce four runtime failures:
[controller](reverted-controller.log), [gateway](reverted-gateway.log),
[host](reverted-host.log). Exact original source bytes were then restored and
compared with the candidate hashes; the same four regressions pass in the
restored [controller](restored-controller.log),
[gateway](restored-gateway.log) and [host](restored-host.log) check logs.
Initial exploratory compilation/selector misses and a
stub timeout are not counted as failing-first evidence.

The archived-snapshot test now requires a fresh complete inventory after live
boundaries arrive for a parent whose earlier child coverage was retired. This
asserts conservative idle proof rather than reusing stale coverage.

## Remaining integration/device gates

BB owns delivery of the getter/notifications to native admission and native
owner/generation/foreground/Stop validation at dispatch, persisted idle markers,
exact-child stop and server-first restoration. Native must also gate ordinary
startup independently of these Dart callbacks. Until BB's supporting APK exists,
old/malformed receipts cannot authorize guarded restore. No native API invented.

No APK, emulator, real account or sign-out touched. No server-ready/manual-start
signal or durable recovery-budget reset is emitted. This is the completed BA
backend prerequisite, not BB5/BB7 device certification. The coordinated native
and device batch remains required; the existing build/resource hold is honored.
