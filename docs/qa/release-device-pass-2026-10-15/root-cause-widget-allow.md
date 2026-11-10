# P1: cold Conversations widget permission reply

Candidate base: `4d7288a59`, branch `codex/release-p1-widget`.

Finish line: Allow once and Reject from the disconnected Conversations widget
negotiate the server protocol, answer the exact pending request, and settle the
widget receipt without opening the app or enabling Stay connected. Non-goals:
changing permission policy, background lifetime, native retry policy, or other
release-pass defects.

## Finding and evidence

The cold widget runner omitted the health handshake that selects the OpenCode 2
API dialect. A new `Api2Transport` starts with an unknown dialect. The pending
read, `GET /api/permission/request`, works unchanged on beta and stable, so it
does not settle the dialect. The runner then sends:

```text
POST /api/session/{sessionID}/permission/{requestID}/reply
{"reply":"once"}       # before fix
{"decision":"once"}    # stable OC2 contract
```

Reject has the same defect (`reply: reject` versus `decision: reject`). The
existing [`stableRoute`](../../../lib/api2/dialect.dart) translation is correct;
it is used only after the transport knows the server is stable. Transport
fallback detects moved paths/methods after a 404, so a body-only rename on the
same permission route cannot trigger that fallback. `stableResponse` is not
involved in permission reply completion.

The new real-HTTP regression uses the production transport factory, client and
gateway against a synthetic server implementing these stable/beta contracts.
Before the production change, it captured `{"reply":"once"}` and
`{"reply":"reject"}` where stable required `decision`; the server kept the
permission pending. Both stable cases failed. The beta cases failed the missing
handshake-order assertion. Four new runner readiness/guard cases also failed:
**23 passed, 8 failed**, recorded in [widget-allow-before.txt](widget-allow-before.txt).
This is a reproduced client defect consistent with the release observation;
the historical APK's actual network response was not captured in pass 2.

The historical screenshots show the relevant sequence:

- [rel2-23](rel2-23-widget-permission.png): pending request with Allow once / Reject,
  “Connects when you tap.”
- [rel2-25](rel2-25-widget-after-allow.png): “Check the conversation,” request still
  shown after Allow once.
- [rel2-29](rel2-29-app-after-widget.png): the app still has the `sleep 50` permission.

The native receipt path explains the displayed failure. The runner sets
`dispatched` before the POST, so a reply error becomes `uncertainDelivery`;
native renders that as “Check the conversation.” A purely stale scope/identity
rejection instead produces “Action unavailable.” Native retains the uncertain
claim, and the second identical action is blocked from replaying: two taps do
not establish two network POSTs. This behavior is intentionally preserved.

Other hypotheses checked in source:

- Native waits for Dart's `ready` method before `execute`
  ([WidgetActionRuntime](../../../android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/WidgetActionRuntime.kt)).
- Request IDs are passed unchanged and matched together with the session ID
  against a fresh pending-permission read before dispatch.
- The native lease bounds engine startup and the action to 30 seconds. The
  runner guards ownership and deadline before/after reads and before mutation.
- Only an accepted receipt applies the updated snapshot
  ([BackgroundConnectionService](../../../android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BackgroundConnectionService.kt)).

## Change

[`WidgetActionRunner`](../../../lib/background/widget_action_runner.dart) now
awaits `gateway.health()` through its existing bounded, ownership-checked read
helper before permission or stop operations. An unhealthy or failed handshake
returns a sanitized rejection before dispatch. Stable OC2 negotiates via the
existing `/health` → `/info` path and uses `decision`; beta retains `reply`.
Cached server-version metadata is not trusted for negotiation.

The connection is still created for one explicit tap and closed afterward.
No event subscription, reconnect loop, credential persistence change, or Stay
connected preference change is introduced. Team actions retain their existing
path. An old uncertain claim is not automatically replayed after updating;
check and answer that request in the app before testing a fresh widget request.

## Verification

Pinned Flutter: `3.47.1`,
`~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter`.
Heavy checks use `tool/qa/machine_lock.sh`.

Dart command (79 tests passed; [log](widget-allow-dart-tests.txt)):

```sh
FLUTTER="$HOME/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter"
tool/qa/machine_lock.sh test -- "$FLUTTER" test --no-pub --concurrency=1 \
  test/widget_action_runner_http_test.dart \
  test/widget_action_runner_test.dart \
  test/widget_v2_contract_test.dart \
  test/widget_snapshot_test.dart \
  test/api2_dialect_test.dart \
  test/api2_transport_test.dart --reporter expanded
```

The four new HTTP cases cover both dialects and both actions using fresh
production transports, deliberately stale cached version metadata, exact route
and body, location-scoped pending reads, server pending-state removal, accepted
receipts, preservation of another request, and duplicate-tap suppression.
Six new runner cases cover handshake ordering, unhealthy/error responses,
cancellation, deadline expiry, and ownership changes during the handshake.
Existing stop/run-identity, deletion, uncertain-delivery and Team cases pass.

Additional checks:

- `flutter pub get` in the app and `widgetbook/`: passed. The initial analysis
  attempt reported 189 unresolved Widgetbook/analyzer symbols because the fresh
  nested package had no dependency configuration. Resolving that package fixed
  the environment without source changes or ignores.
- `tool/qa/machine_lock.sh analyze -- "$FLUTTER" analyze --no-pub`: **no issues
  found**, 29.8 seconds ([log](widget-allow-analyze.txt)).
- `tool/qa/machine_lock.sh build -- "$FLUTTER" build apk --release --config-only`:
  passed, then `tool/qa/machine_lock.sh build -- bash -c 'cd android && ./gradlew
  :app:testReleaseUnitTest --no-daemon'`: **BUILD SUCCESSFUL**, 3m 6s;
  **456 tests, 29 suites, zero failures/errors/skips**, counted from JUnit XML
  ([log](widget-allow-kotlin-tests.txt)). Includes WidgetActionLeaseTest (7),
  WidgetActionPolicyTest (11), and WidgetV2PolicyTest (15). Gradle emitted
  pre-existing compiler/deprecation and untranslated-language warnings.
- Changed Dart formatting, documentation links and `git diff --check`: passed.
- Separate read-only review of native readiness/receipt handling and the final
  runner/HTTP-test diff found no further actionable issues.

No emulator or physical phone was used in this fix run; no scratch server
process was started. Test HTTP listeners are loopback-only and closed in
`finally`. No emulator lock was needed. This is focused local regression proof,
not a new APK/device pass or a completed repository-wide Dart suite. No APK was
signed, installed, deployed, or released; nothing was pushed. `/home/owner`
package files were not edited.
