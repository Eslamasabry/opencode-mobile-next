# Agent Client Protocol: feasibility and recommendation

Date: 2026-10-02. Baseline: `7282e290`, branch `codex/acp-research`.
Status: research plus isolated protocol spike; no new product backend enabled.
Finish line: evidence-backed adoption decision and testable Dart wire client.
Non-goals: UI, public hosting, production relay, installing agents, provider
authentication, migration of existing native backends, or release work.

## Recommendation: adopt in stages through the existing private Paseo route

**Yes, ACP can expand agent coverage through one common protocol adapter.**
Keep the native OpenCode 1/2 and Codex app-server paths where their richer
contracts already work. First validate an ACP runtime behind the existing Paseo
gateway; a separate relay/client gateway is a fallback, not the starting point.
Do not enable an agent merely because its handshake succeeds.

The decisive finding is that **our pinned Paseo v0.9.2 already supports generic
ACP providers**. Its `agents.providers.<id>` configuration can extend `acp` and
select a host-local command. Our gateway already accepts arbitrary ready provider
IDs and passes them into `create_agent_request`; its name mapper has a fallback.
No new flavor is intrinsically required. ACP adapter lifecycle/permissions/history
still need proof against the actual pinned daemon. See the pinned source paths,
config example and limitations in [host evidence](acp-host-evidence-2026-10-02.md).

Two concrete integration gaps prevent calling this ready: Paseo ACP exposes
permission action IDs, while our gateway sends generic behavior and Claude-style
suggestions, not `selectedActionId`; its static persistent-grant capability must
not imply ACP standing-grant parity. Paseo also has raw-event trace/stderr
diagnostic paths that need a host privacy audit before credential-bearing flows.
Basic allow/reject fallback is plausible, not a substitute for option mapping
and live proof. These are reasons for staged adoption, not a new relay by default.

## Primary-source snapshot and wire contract

Read the [ACP spec repository](https://github.com/agentclientprotocol/agent-client-protocol)
at `9e032156545412be9bba5e092f12d0080c499b6d` (2026-10-01): wire major **1**, schema
**1.10.2**. Do not equate schema/SDK semver with wire major or silently adopt v2
drafts. [Spec/SDK and agent evidence](acp-spec-agents-evidence-2026-10-02.md) records
native versus adapter support, SDKs, and source links for Gemini, Claude, Codex,
Goose, Antigravity, OpenCode, Cursor, Grok, Hermes and additional agents.

In the inspected sources, Gemini, Goose, OpenCode, Cursor and Grok expose native
launches; Hermes has a first-party in-tree ACP server; Claude/Codex use separate
maintained ACP-org adapters. Antigravity now has an official Google proprietary
ACP binary, distinct from third-party wrappers. Native/publication status is
not conformance proof. Agent differences matter: Gemini source advertises load
but not list; current Claude/Codex adapters advertise list/load/resume/delete/
close; Goose advertises list/load/delete/close; Hermes advertises list/load/
resume. Pin and recheck actual capabilities per installed version.

| Concept | Current stable contract / consequence |
|---|---|
| Transport | UTF-8 JSON-RPC 2.0, one JSON message per newline on stdio; bidirectional requests. Standard Streamable HTTP remains a draft. Custom WS/SSH pipes must preserve lifecycle and framing. |
| Handshake / versions | `initialize(protocolVersion:1, clientCapabilities, clientInfo)` returns negotiated version, agent capabilities/info, auth methods. Missing capabilities mean unsupported; reject another wire major. |
| Login | `authenticate(methodId)` selects an advertised method. Current v1 also has optional terminal auth and logout; methods and credentials belong to agent/host. ACP login is separate from relay authentication. No uniform provider OAuth/token API. |
| Sessions | Required `session/new` with absolute `cwd` and `mcpServers`; optional `session/load` replays history. Current v1 has capability-gated list/resume/close/delete, not universal implementations. List supports pagination; resume does not replay. |
| Prompts / streaming | `session/prompt` content blocks, notifications `session/update`; text/resource links baseline, richer blocks negotiated. Prompt response `stopReason` completes the turn. `session/cancel` is a notification, not an acknowledgement of stopped execution. |
| Tools / diff | `tool_call`/`tool_call_update` share `toolCallId`, status/kind/title/locations/content; updates may be partial. Content may include text, old/new file diff or terminal references. These are not a Git revert or session diff endpoint. |
| Permissions | Agent requests `session/request_permission` during prompt; client returns selected offered `optionId` or cancelled. Kinds include allow/reject once/always. No silent approval, no universal standing-grant inventory/revocation. |
| Files / terminal | Agent-to-client `fs/read_text_file`, `fs/write_text_file`, `terminal/create`, output/wait/kill/release are optional client capabilities. They execute on the workspace host; they do not imply app file browsing or a user terminal. |
| Optional updates | Modes/models/config options, commands, plans and usage depend on agent/version. Preserve unknown additive updates; do not execute unknown extensions. Prompt/request IDs do not prove durable message identity or idempotency. |

Primary wire pages: [transport](https://agentclientprotocol.com/protocol/v1/transports),
[initialization](https://agentclientprotocol.com/protocol/v1/initialization),
[authentication](https://agentclientprotocol.com/protocol/v1/authentication),
[session setup](https://agentclientprotocol.com/protocol/v1/session-setup),
[prompt turn](https://agentclientprotocol.com/protocol/v1/prompt-turn),
[tool calls](https://agentclientprotocol.com/protocol/v1/tool-calls),
[terminals](https://agentclientprotocol.com/protocol/v1/terminals).

## Mobile host choices

| Option | Fit | Decision / blocker |
|---|---|---|
| Tiny relay in BYO host bundle | Fixed runtime allowlist, subprocess stdin/stdout, private authenticated duplex channel; no service of ours. Works with agents without a daemon. | Later fallback. A byte proxy is small, but safe pairing, child lifecycle, file/terminal execution, approvals, durable transcript/inventory and reconnect are a product-sized host contract. |
| Existing Paseo daemon | Already generic ACP in pinned v0.9.2; owns workspace/agent process and session/timeline state; our existing private WS gateway can reuse normalization. | Preferred first slice. Run `--no-relay`, bind loopback/Tailscale only, authenticate remote access; test generic-provider shape, history and permission semantics. No upgrade/install/config mutation performed here. |
| Built-in Linux on phone | Same stdio protocol can run beside a Linux agent; no remote relay required if app/native bridge supplies byte streams. | Later, agent by agent: verify ARM64 binaries, proot/Node/Rust dependencies, provider login, RAM/storage, process death, background limits and terminal bridges on target phone. Desktop hello is not device proof. |

MonoCode's MIT source provides a concrete host/connection/session design to learn
from, including real process and ACP code. [Host evidence](acp-host-evidence-2026-10-02.md)
identifies what to reuse conceptually and what conflicts with our restrictions.
Its implementation is evidence, not a claim that a public relay is acceptable.
Our host stays private (verified Tailscale or owner SSH tunnel, even if TLS),
provider auth stays on it, and no raw stderr/wire payloads reach diagnostics.
The repo's [BYO contract](byo-vps-contract.md) and [built-in Linux plan](built-in-linux-plan-2026-09-23.md)
are broader proposals; this spike neither implements nor revalidates them.

## Domain mapping and missing product guarantees

| Existing domain seam | ACP mapping | Missing or unsafe inference |
|---|---|---|
| `HealthGateway` | Successful initialized private connection | Not provider-auth readiness or validated project access. |
| `SessionGateway` | new; list/load/resume only when advertised | Universal inventory, durable message pages, rename, authoritative statuses and deletion semantics are not guaranteed; need host ledger or explicit unsupported gates. |
| `PromptGateway` / `EventGateway` | prompt/cancel plus normalized text/tool/plan updates | No auto-retry/idempotency. Lost response means uncertain dispatch. Reconcile from host snapshots/load, not replay of volatile deltas. |
| `PermissionGateway` | Pending bidirectional request with exact option set | Requests live on transport/process epoch; reconnect must not re-use stale approval IDs. Always grants are not necessarily inspectable/revocable. |
| `ProviderGateway` / session selection | Host runtime plus negotiated models/modes/config | Not global provider API-key configuration, runtime switch within existing session, or a universal model catalog. |
| `FileGateway` / managed shell | Host must implement separate read/browse or terminal product service | Agent client callbacks alone do not satisfy user-facing browsing/search/terminal APIs. |
| `ServerOperationsGateway` | Only verified optional session methods | Revert/restore, compaction, worktrees, Git init/reset, forks and exports remain separate host/agent capabilities; never emulate by destructive shell commands or slash-text guesses. |

`ServerCapabilities` has many true defaults. A standalone gateway must explicitly
disable unsupported operations, use `messageCompletionEndsRun=false`, and keep
offline prompt queue and exact message-ID correlation false. Do not mark
`sessionDiff=true` solely from tool diffs. Current required session reads/mutations
have fewer flags than ACP's optional lifecycle; coordinate new capability gates
with the UI owner rather than treating unavailable data as empty history.
Detailed UI ownership, safe copy, per-profile storage/deletion and acceptance:
[frontend contract](acp-frontend-contract.md).

## Slice plan and frozen ownership

| Slice | Read / write set and owner | Dependency / acceptance / checks |
|---|---|---|
| This feasibility slice | Read spec/agents/MonoCode/Paseo and current gateway; Codex writes `lib/acp/`, focused fake-agent test, smoke tool and these docs only. | Pinned v1 contract; fake duplex framing/permissions/errors/EOF, real installed handshake where possible; format and analyzer. No UI or gateway enablement. |
| ACP through Paseo | Backend owner: isolated new fixture/evidence files first; only later one owner for `lib/paseo/**`. Host config remains Eslam-owned. | Freeze pinned provider config and existing request shapes; fixture and opt-in host proof: create/prompt/tool/approve/reject/cancel, disconnect/restart/history. No reviewer launches tests. |
| Product integration | Claude owns kit/UI/l10n; backend owner owns capabilities/domain contract and profile deletion seam by coordinator allocation. | Ready runtime appears with safe name; save/restart/reopen/permissions/cancel/reconcile/deletion. Focused checks then frozen coordinator integration gate, UI screenshots/accessibility/RTL. |
| Additional agents | One pinned runtime/config at a time, host installer and protocol compatibility owner. | Record binary/licence/architecture/auth plus runtime matrix; unsupported agents stay unavailable. Do not market registry listing as verified compatibility. |
| Standalone relay (conditional) | BYO bundle host owner; isolated transport and durable store, then domain gateway owner. | Only if Paseo demonstrably cannot meet needed contract; private network enforcement, authenticated lifecycle, bounded buffers, scoped callbacks, restart/deletion proof. |

## Verification and owner decisions

Implemented: `lib/acp/acp_client.dart` plus typed models, [API README](../../lib/acp/README.md),
`test/acp_client_test.dart`, and `tool/acp_smoke.dart`. The client accepts duplex
byte streams, bounds frames/requests/queued writes/permission callbacks, supports
initialize/new/text prompt/cancel/explicit agent-managed auth, preserves unknown
additions, rejects unsupported versions, and abandons pending work on timeout,
EOF or dispose. Permissions default to cancelled; only offered recognized IDs
can be selected. Unsupported file/terminal calls return method-not-found. Wire
errors are reduced to local failure kinds/numeric codes, with no remote text.
No dependency, native bridge, gateway, UI, persistence or profile changes.

Checks on the final code snapshot using pinned Flutter/Dart:

- `flutter pub get` explicitly run once: passed, no tracked dependency changes.
- `dart format --language-version=3.10` on the four Dart files: passed.
- `tool/qa/machine_lock.sh test -- <pinned-flutter> test --no-pub --concurrency=1 test/acp_client_test.dart`:
  **26 passed**. Includes fragmented UTF-8, out-of-order IDs, streamed tool/diff/
  terminal-reference updates, unknown additions/methods, permission selection/
  cancellation/late callbacks, redaction, malformed/oversized frames, write
  failure, EOF, timeout/no prompt replay, explicit auth and bounded write queue.
- `tool/qa/machine_lock.sh analyze -- <pinned-flutter> analyze --no-pub`:
  **No issues found**. Four initial new-file analyzer issues were fixed and the
  affected tests/analyzer rerun; no ignores added.
- Real installed Gemini CLI **0.57.0**, `gemini --acp`, through
  `tool/qa/machine_lock.sh test -- <pinned-dart> run tool/acp_smoke.dart -- <installed-gemini> --acp`:
  **handshake passed, protocol 1, loadSession=true, four auth methods**. A
  temporary `GEMINI_CLI_HOME` and empty subprocess cwd isolated configuration;
  the tool disabled Gemini launcher relaunch, discarded stderr, printed only
  safe primitive metadata and confirmed exact-child termination/cleanup.
  No authenticate/new-session/prompt operation was sent, so no provider turn,
  live permission flow or session continuity is claimed.
- All five ACP Markdown files: local relative links checked, none broken;
  staged whitespace diff checked before commit.

Installed-command discovery found Gemini, OpenCode/OpenCode2, Claude and Codex;
no `claude-code-acp`, `claude-agent-acp`, `codex-acp`, Goose or Paseo command was
found. OpenCode's launcher failed `--help` because its postinstall was not run;
it was not repaired. No global software installed. Source snapshots were read
in temporary directories, not other worktrees. No full integration suite, APK,
signing, push, PR, deployment or live relay was run for this research spike.
A focused pass proves the wire client, not a usable mobile feature.

Owner decisions: (1) **Paseo-first staged adoption (recommended)**, independent
relay only after a concrete deficiency, or defer ACP entirely. (2) First runtime:
**Gemini (installed, native ACP)**, Goose (native but not installed), or another
pinned adapter after licence/auth review. (3) Agent without list/load/resume:
exclude from initial product (recommended), or clearly offer fresh conversations
only with host transcript history and no promise of resumed agent context.
(4) Keep initial login host-managed (recommended), or fund separately reviewed
private browser/terminal login handoff; no copying provider credentials to phone.
