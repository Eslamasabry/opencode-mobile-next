# BA2: captured provider snapshots and final row coverage

State: partial implementation; full captured four-state matrix is blocked.

A short locked read-only WebSocket session queried the existing Paseo 0.9.2
helper at `127.0.0.1:4099`, using `get_providers_snapshot_request` for cwd
`/root/projects`. Existing helper authentication stayed in process memory only;
no credential entered a fixture, log or output. An ephemeral adb TCP forward was
created and removed by its exact owned port in `finally`. No refresh, restart,
install, sign-in or logout was requested.

The exact listening helper was found by port and PID. Its actual HOME identifies
profile `1791381364862609`; an older Paseo pid file in profile `1790839392073695`
was stale and was not treated as the running helper. Captures record the correct
profile identity. Reading providers for the cwd yielded initial loading and then
settled rows:

| Capture | Provider readiness |
| --- | --- |
| [Claude loading](../../../test/fixtures/host_agent_providers/claude-loading-device.json) | loading |
| [Claude final](../../../test/fixtures/host_agent_providers/claude-ready-device.json) | ready |
| [fx loading](../../../test/fixtures/host_agent_providers/fx-loading-device.json) | loading |
| [fx final](../../../test/fixtures/host_agent_providers/fx-error-device.json) | error, asking for sign-in |

Each fixture includes its sanitized snapshot hash. Only provider/status/enabled/
source fields are preserved. The exact known fx sign-in substring is retained;
unknown surrounding error text is omitted. Model/config/credential payloads are
omitted before file creation.

The focused test composes recorded snapshots with the BA1 CLI results to prove
final row behavior. Those auth captures are from profile `1790839392073695`, so
the composition is contract coverage, not a same-profile live certification.
Ready helper model lists still leave `HostAgentLoginState.unknown` unless a
separate explicit probe reports an account. Synthetic readiness/outcome cases
are explicitly marked synthetic and cover all seven phone agent descriptors.

Remaining evidence gaps: no accounts/installations for Codex, Gemini, Qwen,
Goose or Oh My Pi; no signed-in fx account; no newly signed-out Claude account;
no full signed-in/signed-out/loading/error capture matrix for each agent. These
are unmet BA2 Done-when evidence, not passing certification cells.

Lead owns checks; no test process was launched by this worker:

```sh
tool/qa/machine_lock.sh test -- ~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter test --concurrency=1 test/host_agent_providers_test.dart
```

Regression mutation: temporarily make `paseoProviderNeedsSignIn` return false;
run exact `captured fx provider error reaches final signed-out phone row`.
The captured fx readiness must fail its needsHostSignIn assertion. Restore
and rerun. The CLI account truth remains independent of that legacy readiness
projection; connection wiring regression belongs to BA1/BA3 controller tests.

Lead checks: affected serial Flutter tests passed; corresponding negative
control failed behaviorally, and production safeguards were restored. Failed
controls are recorded in this directory. Full repository suite not run.
