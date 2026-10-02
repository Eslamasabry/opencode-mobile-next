# ACP host options: source evidence, 2026-10-02

Finish line: identify a supported private execution boundary and the smallest
next ACP integration slice from actual MonoCode, Paseo and app source.
Non-goals: implement a relay, enable an agent, install tools, change UI/native
bridges, access a phone or use provider credentials.

## Sources and snapshot limits

MonoCode was read at commit
[`1e97594ddf6f40aa24671f7fa09f2048deb1d5eb`](https://github.com/hardbeat920/monocode/tree/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb).
Its [README](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/README.md),
[remote-access documentation](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/docs/remote-access.md),
host implementation and connection/session source were inspected. Its
[license](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/LICENSE)
is MIT, copyright 2026 Nick. Retain its notice if copying substantial code.

Paseo current main was read at
[`bd3986d964b3ff528a8adf045a062dde10616934`](https://github.com/getpaseo/paseo/tree/bd3986d964b3ff528a8adf045a062dde10616934)
(package version `0.11.0-beta.3`). Tag source was separately read for
[`v0.9.2`, commit `c67b7158b441bb09026b38d86ae335cc4b49190a`](https://github.com/getpaseo/paseo/tree/v0.9.2),
`v0.10.0` and `v0.10.2`. GitHub's latest stable release was
[`v0.10.2`](https://github.com/getpaseo/paseo/releases/tag/v0.10.2),
published 2026-09-29. These are source findings, not runtime proofs.
No tools were installed and no paid/authenticated calls or tests were run.

## What MonoCode actually does

The remote boundary is **not raw ACP over HTTP**. The authenticated host API
owns sessions and wraps several different local provider protocols.
[host/providers.ts](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/host/providers.ts)
reuses ten TypeScript adapters, including native Claude/Codex/OpenCode/Pi
transports and ACP adapters. The
[child backend](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/host/child-backend.ts)
launches agent processes and connects stdin/stdout while keeping OpenCode HTTP
on host loopback. This is a useful execution boundary to learn from, rather
than evidence that one generic protocol replaces every provider adapter.

The
[ACP client](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/src/integrations/harness/core/acp.ts)
wraps its JSONL bidirectional JSON-RPC client. The actual
[Antigravity adapter](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/src/integrations/harness/providers/antigravity/antigravity.ts)
spawns `agy_acp_server.par`, initializes protocol version 1, creates/prompts
sessions, handles streamed updates and permissions, and cancels via
`session/cancel`. It tries resume then load for a bound prior conversation;
timeouts stop that ladder, preventing another operation while an uncertain
one may still execute. It may create a new session after definitive restore
failure and emits a visible explanation. Our continuity policy should require
an explicit user choice before substituting a new conversation.
MonoCode's Cursor, Grok and Hermes source also imports the same ACP core;
each retains provider-specific setup, event and permission handling.

Security and persistence are supplied by the host wrapper:

- [host/server.ts](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/host/server.ts)
  accepts authenticated POST `/rpc`, rejects browser origins, checks protocol
  and host identity, and rechecks device revocation after body parsing.
  The documented listener is `127.0.0.1:3774`, reached by SSH forward. Each
  paired desktop receives its own revocable bearer token. The doc's optional
  public HTTPS reverse proxy does **not** meet our owner rule; omit it.
- [host/store.ts](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/host/store.ts)
  uses SQLite WAL/FULL sync for session snapshots, a bounded revision event
  journal, command receipts and hashed device credentials. Host-owned history
  outlives closing a desktop, tab or SSH tunnel. Restart marks interrupted
  turns; uncertain provider operations are not automatically replayed.
- [host/engine.ts](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/host/engine.ts)
  hashes each command and looks up its durable receipt by command ID before
  mutation. Session creation/first prompt is covered by this wrapper's
  transaction, not by a standard ACP idempotency guarantee.
- [connections.ts](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/src/features/connections/model/connections.ts)
  persists an outbox entry **before** dispatch and retries the same command ID.
  Native Tauri HTTP owns credentials. Revision sync assembles bounded chunks;
  [remoteSessionState.ts](https://github.com/hardbeat920/monocode/blob/1e97594ddf6f40aa24671f7fa09f2048deb1d5eb/src/features/connections/model/remoteSessionState.ts)
  projects host snapshots into the existing session view while preserving tab
  identity. The general session model retains provider-specific identity.

Borrow the host-owned session index, receipts, private loopback transport,
installation identity and revocation concepts. Do not copy logging wholesale:
the JSON-RPC parser logs a prefix of non-JSON stdout, which could contain
credentials. Raw agent stdout/stderr must never become our diagnostics.

## Paseo already speaks ACP at our pin

The app's actual install pin is **0.9.2** in
[`local_agents_script.dart`](../../lib/termux/scripts/local_agents_script.dart),
despite historical docs mentioning 0.8.0.
The pinned tag's
[provider registry](https://github.com/getpaseo/paseo/blob/v0.9.2/packages/server/src/server/agent/provider-registry.ts),
[generic ACP provider](https://github.com/getpaseo/paseo/blob/v0.9.2/packages/server/src/server/agent/providers/generic-acp-agent.ts)
and [ACP implementation](https://github.com/getpaseo/paseo/blob/v0.9.2/packages/server/src/server/agent/providers/acp-agent.ts)
prove generic `extends: "acp"` support. The latter uses the official TypeScript
SDK, starts a local stdio process, handles tools/permissions/file/terminal
callbacks and streams translated timeline updates to Paseo clients.
Thus adding ACP does not require a daemon upgrade just to obtain the transport.

Minimal **illustrative host-side** configuration shape, not applied or tested:

```json
{
  "agents": {
    "providers": {
      "gemini": {
        "extends": "acp",
        "label": "Gemini CLI",
        "command": ["gemini", "--acp"]
      }
    }
  }
}
```

Use the launch flag documented by the **installed agent version**; the example
proves Paseo's config shape, not a universally accepted Gemini flag. Pin any
adapter package locally; do not configure auto-downloading `npx --yes` commands.
Agent authentication remains with the host CLI under the host user's account.
Disable Paseo relay/web UI and connect on loopback through SSH or tailnet only.

There is **no discovered app provider allowlist blocker**:
[`PaseoGateway.providers()`](../../lib/paseo/gateway.dart) accepts arbitrary
bounded provider IDs when ready/enabled, supplies a default model if needed,
and creates agents using `ModelRef.providerID`. Follow-up mode changes are
limited to the provider's advertised mode IDs; model changes stay within the
existing session provider. [`mappers.dart`](../../lib/paseo/mappers.dart) uses
friendly names for known providers and falls back to the ID for others.
The pinned daemon's `AgentProviderSchema` is a string. Existing app transport
speaks Paseo protocol 1, rather than ACP directly; compatibility still needs
focused fixtures and one real live provider before enabling this path.

Concrete gaps to prove or implement:

1. **Permission action fidelity.** Pinned ACP mapping emits named `actions`
   with option IDs and supports `selectedActionId` in replies. App permission
   mapping ignores these actions; replies send allow/deny and Claude-style
   suggestions. The daemon falls back to `allow_once` then `allow_always` or
   `reject_once` then `reject_always`. Basic binary choices may work; named
   chooser actions and deliberate standing consent are not faithfully exposed.
   Never promise that app "Always allow" means ACP `allow_always` today.
2. **History versus continuation.** The pinned ACP resume code requires
   advertised `loadSession`, or experimental resume; otherwise it errors.
   Paseo can keep an agent list/transcript while a provider cannot continue
   that exact conversation after process loss. Test both separately.
3. **Privacy.** Paseo ACP trace logging includes raw event/extension payloads;
   diagnostics can retain stderr. Disable raw trace logs and audit capture,
   redaction and export before claiming our no-secret-log invariant.
4. **Capability honesty.** Existing Paseo gateway has files/diffs/git/terminal,
   attachments, fork/revert, todos and questions gated off. Host ACP support
   does not enable those product operations. Enable one proven capability at
   a time, based on live provider negotiation plus gateway implementation.

## Mobile options and recommendation

| Option | Fit | Missing proof / cost |
| --- | --- | --- |
| Tiny relay in BYO host bundle | Technically feasible; agent owns host workspace; phone speaks a private host transport | Authentication, process supervision, receipts, session index/transcripts, permission delivery/reconnect, interruption, deletion and workspace confinement turn a byte pipe into substantial host work |
| Existing Paseo ACP provider | Preferred first executable integration slice; our gateway and install pin already support the relevant execution boundary | Provider-specific permission choices, auth behavior, resume, version fixtures and log privacy need proof; no daemon install exists on this PC for a live test |
| Built-in Linux on phone | Plausible for agents with working Linux ARM64 binaries and noninteractive stdio; reuse host relay/daemon on loopback | Agent-specific ABI/auth/native dependencies, Android process/lifetime limits, storage, thermal/memory budget and device tests; direct Dart `Process.start` is not a replacement for proot/native ownership |

The current native bridge
[`BuiltinLinux.startService`](../../lib/builtin/builtin_linux.dart) owns a
long-running service in its own proot. Its short `run` route explicitly must
not be used for a daemon because the proot is torn down when the script ends.
The earlier design doc is a plan, so the present bridge and
[P1.6a evidence](../qa/gate-P1.6a-2026-09-28/README.md) are stronger: x86_64
signed-out Claude/Paseo worked, but ARM64 Claude native dependency installation
failed, and Paseo/gateway/PTY/authentication on ARM64 remained unverified.
That limitation does not prove other ARM64 agents incompatible. The repo's
Android 15+ dataSync six-hour cap still applies to an on-phone relay.

Recommendation: **LATER for a new ACP product adapter**, retain the pure Dart
protocol spike as groundwork, and prioritize a bounded Paseo-configured ACP
provider proof before duplicating host infrastructure. A standalone relay is
justified if accepted requirements cannot be met by Paseo, or avoiding its
dependency becomes an explicit owner decision. Keep current native OpenCode
and Codex paths: ACP does not provide automatic parity with their richer APIs.

Owner choices: (1) pilot one pinned ACP agent through Paseo with a minimal
chat/stop/permission/reopen journey; (2) invest in the BYO bundle's durable
multi-agent relay after defining that host contract; or (3) defer runtime
integration and retain source research plus the fake-agent client spike.
Prefer (1); on-phone ACP remains a separately measured device slice.
