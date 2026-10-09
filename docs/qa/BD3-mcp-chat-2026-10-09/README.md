# BD3 MCP chat evidence

## Feasibility sources

### OpenCode 2

App-side contracts inspected on 2026-10-09:

- `lib/api2/gateway_operations/ops_catalog.dart`: location-scoped runtime
  add, connect and remove; persistent add is rejected; MCP OAuth is unavailable.
- `lib/api2/dialect.dart`: stable MCP mutations move under
  `/api/experimental/mcp`; beta mutations use `/api/mcp`.
- `docs/opencode2-protocol-notes.md`, MCP and integrations sections: the MCP
  inventory may include an `integrationID`; generic OAuth attempts return an
  authorization URL and support polling. The current MCP adapter does not keep
  that integration ID or bridge this OAuth flow.
- `lib/api2/events.dart` decodes MCP status/tools/resources changes. At the
  pre-implementation snapshot, `gateway_events.dart` drops those hints.

Upstream source below is pinned to commit
`b8cedc1a7a5e2916bbb65dc1d4b620729c261638`. Its
`packages/cli/package.json` identifies version 2.0.10. This is source evidence,
not proof that the different installed beta behaves identically.

- [Runtime routes](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/protocol/src/groups/mcp.ts#L23-L86):
  add/replace, connect, disconnect and remove are runtime operations.
- [MCP lifecycle](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/mcp/index.ts#L355-L405):
  connect fetches tools before publishing connected status and tools-changed.
  Lines 145–186 register remote servers as OAuth integrations; lines 497–517
  reconnect after their credentials change.
- [Tool registry refresh](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/tool/mcp.ts#L124-L142):
  tools-changed triggers a registry reload with a 100 ms debounce.
- [Session context](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/session/context.ts#L121-L155)
  snapshots the current registry; the
  [runner](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/session/runner/llm.ts#L70-L138)
  selects context at the next step. Thus the same conversation can use new tools
  on a later model request; an in-flight request does not change.
- [OAuth callback](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/mcp/oauth.ts#L349-L391)
  binds to server-side `127.0.0.1`; lines 441–475 produce an automatic browser
  authorization flow. A phone browser reaches this callback only when it shares
  that loopback host or explicit forwarding exists. Remote-PC OAuth from a
  phone is unqualified. Chat MCP OAuth remains disabled for OpenCode 2.

### OpenCode 2 local probe boundary

- `opencode2 --version` through the installed wrapper failed because its
  package postinstall had not run. No installation or repair was attempted.
- Direct executable
  `/home/eslam/node_modules/@opencode-ai/cli-linux-x64/bin/opencode2 --version`
  returned `opencode2 v0.0.0-beta-19242`.
- A bounded isolated launch used that direct executable, a temporary working
  directory, separate XDG config/data/cache/state paths, no inherited token/key/
  password environment variables, and an ephemeral loopback port. It made no
  model request and touched no account.
- The process did not provide a successful `/api/health` response within the
  15-second probe window. No authentication material was printed or persisted.
  Exact owned PID `3330055` was terminated and reaped; the temporary directory
  was removed. No endpoints, runtime add, OAuth callback, or tool refresh are
  claimed as live-verified by this attempt.
- This source-backed capability assessment is distinct from device or real
  agent qualification. There were no Flutter/native builds or emulator runs
  in this feasibility probe.

### OpenCode 1

Working native binary: `/home/eslam/node_modules/opencode-linux-x64/bin/opencode`,
v1.18.23. The default wrapper was broken (postinstall missing); no install or
repair was attempted. An isolated fixture used temporary XDG roots and an
app-authored synthetic stdio MCP server. It made zero model requests.

Observed: health/doc/session create HTTP 200; runtime POST `/mcp` returned
connected; GET `/mcp` confirmed it; disconnect and reconnect returned HTTP 200;
the original session remained readable. A disabled synthetic remote with OAuth
explicitly false could be runtime-added; starting auth returned the expected
unsupported-OAuth response, proving lookup of the runtime-only name. It did
not authenticate a provider. Owned PID 3328731 was terminated and reaped;
temporary files removed. No existing configuration/account was accessed.

The built-in `/experimental/tool/ids` count remained 14 before and after the
fixture. It does not enumerate MCP tools and is deliberately not our signal.

Exact-version primary source:

- [Runtime add and cached tools](https://github.com/anomalyco/opencode/blob/v1.18.23/packages/opencode/src/mcp/index.ts#L571-L688): runtime configuration is in memory; connected clients retain the completed tool listing.
- [Runtime OAuth lookup](https://github.com/anomalyco/opencode/blob/v1.18.23/packages/opencode/src/mcp/index.ts#L790-L809): runtime config precedes persisted config.
- [OAuth completion](https://github.com/anomalyco/opencode/blob/v1.18.23/packages/opencode/src/mcp/index.ts#L918-L941): callback completion reconnects the MCP client.
- [Next-step resolution](https://github.com/anomalyco/opencode/blob/v1.18.23/packages/opencode/src/session/prompt.ts#L1088), [tool assembly](https://github.com/anomalyco/opencode/blob/v1.18.23/packages/opencode/src/session/tools.ts#L390): subsequent model steps use current MCP tools. No actual provider prompt was sent to verify model behavior.

Same-phone OAuth limitation: `/auth` starts the server callback listener but
is not the `/authenticate` waiter flow. The phone cannot bind an already-owned
callback port. The controller exposes `manualCodeRequired` and the UI must ask
for the browser return URL/code in that case. Automatic provider OAuth on an
actual phone is **not qualified**. Synthetic loopback tests exercise state
validation, forwarding, completion and cleanup only.

### Paseo / Claude Code / ACP

The installed app targets Paseo 0.9.2. Published protocol and server tarballs
were fetched without account access and their SHA512 SRI matched the shipped
`assets/agents/paseo-package-lock.json`. The protocol has `mcpServers` in launch
configuration, but no MCP add/auth/hot-refresh RPC. Claude applies those servers
to the SDK launch; ACP applies them to new/load sessions, and drops them when a
provider does not support MCP servers. Existing app MCP methods are unavailable.
All new chat mutation/OAuth/readiness flags remain false for these runtimes.
No real Claude process/account was accessed. This is published-source evidence,
not an agent-runtime certification.

## Refined readiness decision

OpenCode 2 emits a tools-changed event before its debounced registry reload.
Its callable protocol has no tool-registry read/barrier; `mcpTools.flush` covers
initial registration, not every later change. Thus connected status does not
confirm which tools the next request will see. `mcpChatToolRefresh` stays false
for OC2 and the snapshot is `connectedReadinessUnknown`. A fixed delay, resource
list, event, or starting a new conversation is not treated as proof.

OpenCode 1 caches the completed tool listing with the connected client, and
reads that cache for each later model step. Only OC1 reports `toolsReady` after
an authoritative connected inventory read. A server with zero tools is possible;
no count or successful use of a particular tool is asserted.

## Ownership and verification

Root owns the entire connection library, controller and check orchestration.
Disjoint workers owned OC1 runtime add, GenUI typed validation/helper schema,
and capability declarations. Workers did not run tests or builds. All Flutter
checks use the pinned 3.47.1 toolchain through `tool/qa/machine_lock.sh`, serial
concurrency 1. Full suite remains coordinator-owned. No Gradle/APK/device work.

Initial RED receipts: `oc1-red.txt`, `genui-red.txt`, `contract-red.txt` and
`controller-red.txt`. The first GenUI attempt also exposed a test import error;
it was fixed before the meaningful `genui-red.txt` run.
The source-guard and late-OAuth-launch mutants are deliberately broken copies
of the implementation, tested independently, then restored in `finally`:
`source-guard-revert.txt`, `late-auth-revert.txt`. Both failed their intended
behavior assertion. These runs do not count as candidate coverage.

## Final result

Implemented backend and contract; frontend remains coordinator-owned.
Implementation candidate: `a0354c92b`. Contract was committed first as
`2fff6ee7a`; protocol/schema/capabilities: `c5f2171e0`; scoped controller and
integration: `a0354c92b`. The first contract commit was initially `91ea09a99` and
had its message body corrected before subsequent commits. After the coordinator
required additive-only commits, no further commit was amended or rewritten.

| Gate | Result | Receipt |
| --- | --- | --- |
| New backend, callback, real ConnectionController integration, capability, gateway, GenUI and file-size checks | 45 passed | `final-focused.txt` |
| Existing GenUI codec/types/server/state, OC2 mappers/MCP scope, product MCP, OAuth and SV3 MCP tests | 117 passed | `affected-tests.txt` |
| Final full pinned analyzer | No issues found | `analyze-final.txt` |
| Removed source guard | Intended single test failed | `source-guard-revert.txt` |
| Removed late-auth cancellation guard | Intended single test failed | `late-auth-revert.txt` |
| G33 before evidence-only commit | 3 commits, 20 Dart files formatted, pass | `commit-check.txt` |

`verification.json` pins all 20 changed Dart source/test file hashes to the
implementation candidate. Earlier green/red logs are development receipts, not
additional unique test counts. The final 45 + 117 = **162** tests are distinct.
The final focused run includes all final code changes. The earlier existing-test
run exercised those paths; subsequent changes affected only new chat-specific
state checks and comments. The final analyzer was clean.
Full-suite execution remains coordinator-owned, per the swarm instructions.

Runtime result:

- **OpenCode 1:** typed recommendation through qualified existing Agent cards;
  reviewed hosted runtime add; explicit OAuth with phone listener or clearly
  labeled manual fallback; fresh connected status proves tool discovery cached
  for the next model step in the same conversation. No nonzero count or actual
  model use claimed.
- **OpenCode 2:** runtime add/connect is available. OAuth is disabled; tool
  readiness cannot be confirmed, so `connectedReadinessUnknown` and the
  corresponding plain copy replace “Loaded tools.” GenUI qualification still
  gates whether a recommendation card can be shown.
- **Paseo Claude Code / ACP:** existing qualified Agent cards can carry the new
  suggestion, but in-chat MCP add/auth/readiness are unavailable. No workaround,
  credential extraction, or real Claude-account use.

Controls checked: no agent-supplied executable configuration; catalog identity
binding and same-name conflict handling; immutable source/profile/location
ownership; authoritative card recovery across transport replacement; revoked
card and qualification invalidation; duplicate taps; no duplicate add after an
uncertain response; OAuth state validation and late-start cancellation; callback
cleanup; no remote credential revocation from disposal/source invalidation.
Explicit OAuth cancellation uses an endpoint that clears the named connector's
stored authorization, and the UI contract requires that consequence to be shown
before invocation.

No `lib/ui/` changes, new persistent formats, generated SDK edits, new dependencies,
Gradle/APK work, emulator session, real provider/model request, push, tag, PR, CI,
signing, deployment or release occurred. No test server remains owned by BD.
