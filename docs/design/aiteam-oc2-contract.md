# AI Team phone engine on OpenCode 2

Status, 2026-10-14: the OC2 **2.0.29** permission boundary passed a live host
probe, and the Rust driver and Dart integration are implemented. Emulator and
owner-phone qualification remain outstanding. OpenCode **2.0.28 is not enabled**:
only the exact server version proven in this job is admitted.

Update, 2026-11-03 (issue #111): the exact pin is now a range,
`2.0.29+`, `2.1.x` and `2.2.x` (`SUPPORTED` in `engine/phone/src/opencode2.rs`).
Setup installs 2.2.5 on the current line and 2.1.1 in the 2.1.0 release, so the
2.0.29-only gate refused every fresh install. 2.1.1 and 2.2.5 both passed the
live driver proof `engine/phone/tests/oc2_live.rs` (planner answers, worker
writes a file, abort confirms). The OpenAPI contract check still runs on every
server. A new minor line is added only after that live run. Stable OC2 sends no
`session.status` frames on any of these versions (busy is
`session.execution.*`, without a location), so chat admission keeps polling
`/api/session/active`.

Finish line: the Rust phone engine drives AI Team lanes on OC2 with the existing
safety guarantees and the app accepts a supported in-app OC2 profile.
Update, 2026-10-15: OC1 support has been removed by owner decision.
Legacy engine configuration keys are read only to preserve stored setup.

## Protocol selection and driver

`engine/phone/src/opencode.rs` accepts only the pinned OC2 server identified by
authenticated `/api/info`; no response falls back to an OC1 client.
Authentication, transport and unsupported-version failures stop execution.
Mutations never trigger protocol switching or automatic retries.

`opencode2.rs` owns transport and parsing, with strict loopback URLs, Basic user
`opencode`, redirect refusal, bounded responses, and credential-safe errors.
The running version and callable operations are checked before work; missing
or changed contracts fail closed. The OC1 driver has been removed.
The schema proof is bound to the observed server PID. A changed PID requires a
new engine client; the existing client fails closed instead of reusing stale
proof. Completion refetch has a ten-second freshness bound and matches the
session's current idle timestamp to the durable idle record.

OC2-specific behavior:

- Create sessions with deny-all permissions and verify their directory and
  stored policy. Before each prompt, replace permissions, read back the exact
  array, select/read back the build agent and model, and store/read back the
  role instruction. Recheck permissions before admitting the prompt.
- Readonly policy: deny `*`, then allow `read`, `glob`, `grep`. Worker policy adds
  `edit` and **`shell`**. OC2's action is `shell`, not the OC1 action `bash`.
  The OS/filesystem boundary is still required for workers.
- Role text uses
  `PUT /api/experimental/session/{id}/instructions/entries/aiteam-role`, with the
  8 KB value limit checked before network mutations. Model selection is session
  state (`POST .../model`); omission resolves the server's enabled default model,
  while an explicit selection is preserved. No provider credentials/configuration
  are returned to callers or logs.
- `POST /api/session/{id}/prompt {text}` yields an acceptance receipt, not
  completion. Keep its message identity as a fence against older turns. Refetch
  durable inbox work as well as paginated messages, permissions, forms and active
  snapshots. A queued or uncertain turn cannot complete from cached text.
- Completion requires the latest user/assistant/idle sequence, a completed
  successful final assistant with text, matching successful session state,
  inactive snapshots, and no pending request. Failed, interrupted, malformed,
  incomplete or changing evidence cannot become successful completion.
- Stop first revokes all tool permissions and verifies the policy, cancels each
  queued inbox entry, then uses `POST .../interrupt?resume=false` and refetches
  inbox/active state. An idle interrupt alone does not cancel queued work.
  Malformed or uncertain mutation responses never trigger automatic retries.
- `/api/event` is a volatile blocker feed. Comment heartbeats establish transport
  liveness only. Reconnection invalidates observation; durable state must be
  refetched. `/api/session/active` is process-wide, so even the known-directory
  fallback conservatively blocks on any non-team execution. No global idle or
  admission authority is advertised.

## App integration and credentials

`ServerCapabilities.phoneTeamExecution` defaults false and advertises driver
support for OC2. Setup uses the selected in-app profile's capability, including
inactive profiles, rather than a flavor condition or another profile's active
capabilities. The native runtime still checks the exact supported version,
operations and boundary before execution; the capability alone is not proof.

Engine health distinguishes identity (`oc2`) from verification (`oc2Verified`).
OC2 execution requires OC2 verification plus the existing boundary/execution
flags. Missing or contradictory proofs fail closed; older OC1 responses are rejected.

The existing secure-storage → private server password file → native credential
snapshot path is reused. No additional password channel argument, stored format,
or credential logging was introduced. The setup tests cover OC2 credential
restoration and preparation before native launch.

OC2 prompt, shell, and command dispatch use the existing app admission hooks.
They pause team work before dispatch and retain uncertainty until a fresh
busy-to-idle reconciliation. Malformed active snapshots, stale lifecycle reads,
and retired transports cannot fabricate idle.

## What was proven live

See the [probe report](../qa/aiteam-oc2-probe-2026-10-14/README.md) and
[sanitized ZAI evidence](../qa/aiteam-oc2-probe-2026-10-14/provider-results.json).
The successful provider was `zai-coding-plan/glm-5.3-flash` on an isolated local
2.0.29 server. The owner's authorized key was selected inside a script, never
printed or copied into the checkout. The server was stopped by its exact
verified listener PID, and its entire credential-containing scratch directory
was deleted. MiniMax was unnecessary and was not used.

Live proof includes exact policy replacement, readonly `glob`/`read`, role marker
delivery, actual shell permission rejection with no side effect or ask, worker
shell execution, successful busy-to-idle, active interruption, and a real pending
shell `ask` request. To force actual denial rather than mere tool omission, the
model received shell availability before its policy was changed to readonly;
the subsequent dispatch returned `permission.rejected`. The report preserves
this distinction and two earlier misleading discovery attempts.

A separate [queued-stop probe](../qa/aiteam-oc2-probe-2026-10-14/cancel-results.json)
used no model or provider key: interrupt left an accepted `resume:false` prompt
in the inbox; explicit cancellation removed it. The OC2 stop path covers both
this pending window and active execution.

The earlier Zen attempts failed before tools with quota/auth errors. They are
retained as history, not used as positive proof. The coordinator reported
restoring the earlier stray ancestor npm install; this follow-up verified its
private npm prefix and did not touch those package files.

## Validation and release boundary

The locked `cargo test -j 2` gate passed: **240 passed, 0 failed, 1 ignored**.
The existing ignored test requires explicit Landlock ABI6 host proof. See the
[validation record](../qa/aiteam-oc2-probe-2026-10-14/verification.md) for the exact
command, test environment, earlier failures and source fingerprint.
Dart source and tests were formatted with the pinned standalone Dart tool.
**Flutter tests and analysis were not run**, as requested. Run these exact files
with the pinned Flutter and the repository's machine lock:

```text
test/api2_phone_chat_dispatch_test.dart
test/api2_client_test.dart
test/phone_team_setup_test.dart
test/phone_project_engine_controller_test.dart
test/phone_project_engine_gateway_test.dart
test/phone_engine_connection_review_test.dart
test/phone_chat_admission_recovery_test.dart
test/phone_engine_ready_reconnect_test.dart
```

Emulator proof remains required for protected in-app OC2 startup, planner →
worker → checker lanes, chat/team admission, disconnect/refetch, restart with
secure credentials, interruption, pending requests and the existing filesystem
boundary. Positive form interaction and live event-stream reconnect were not
exercised in the host probe. Host wire proof and Rust tests do not establish
Android confinement or complete AI Team product qualification.

This is a **native phone-engine change requiring a new Shorebird release, not a
Dart patch**. No APK was built, signed, installed, deployed or released. Commits
are local only; nothing was pushed.

## Protected-start follow-up, 2026-10-15

APK 2316 exposed a live nondumpable engine whose hidden process metadata
blocked native server-start ownership capture. The authenticated ready frame
now supplies an identity bound to the exact live child, preserving the boundary.
Setup also shares the shell's starter and manual-start coordination. See
[root cause 2](../qa/aiteam-oc2-emulator-2026-10-14/root-cause-2.md) for evidence,
regressions and rebuilt-APK qualification still required. Both engine ABIs are
restaged; this follow-up requires a native release, not a Dart patch.
