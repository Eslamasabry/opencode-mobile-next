# P10.1 backend feasibility — 2026-09-27

Status: **blocked at feasibility; no implementation added**.

The requested backend finish line is a conversation-scoped, searchable command
catalogue with honest availability and dispatch through each supported backend.
Non-goals: UI changes, plugin command links, provider login, and translating CLI
commands into ordinary prompts without a verified execution contract.

Base: `dcf05c5e`, branch `codex/p101`, initially clean worktree. Read `AGENTS.md`
and only sections 2, 3, 13 and 15 of `STANDARDS.md`. `HANDOFF.md` is absent.
Read set: domain gateway, OpenCode adapters, Paseo/Codex gateways and transports,
existing command UI (read-only), connection documentation, local server source
and generated public protocol schemas. Write set: this QA directory and, if git
cannot commit, root `COMMIT_MSG.txt`. No UI or single-owner file was changed.

## Feasibility result

The full multi-backend slice does not yet meet the prerequisite to confirm the
current callable command contract on the supported server versions. Following
the task's explicit stop-on-failed-feasibility instruction, implementation stops
here. This is a verification gap, **not a claim that the servers cannot support
commands**. Missing app adapters alone are not evidence of missing server APIs.

| Backend | Verified in this checkout | Remaining prerequisite |
| --- | --- | --- |
| OpenCode 1 | `ProductRepository.listCommands()` discovers commands; `OpenCodeApi.slashCommand()` and `.shell()` dispatch in a session. | No new adapter is needed for these paths. Live execution was not attempted. |
| OpenCode 2 | `Api2Operations` command listing uses `client.commands()`; `Api2Gateway.slashCommand()` posts `{command, text}`, and `.shell()` posts `{command}` to the session endpoints. | Live execution was not attempted. |
| Claude Code through Paseo | The app targets daemon 0.8.0/protocol 1. Its `listCommands()` returns an empty list; slash and shell calls reach the typed unavailable fallback. Newer local source has a real session-scoped list handler. | Establish the listing and execution contract on the supported daemon version, including existing-agent IDs and before-first-prompt behavior. The installed package and source are different versions from the app target; no version-matched callable proof was obtained. |
| Direct Codex | The app targets app-server 0.153.4. Its `listCommands()` returns an empty list; slash and shell calls reach the typed unavailable fallback. Schema generation from the installed 0.153.4 binary exposes native command-related RPCs. | Verify which native RPCs are callable on the supported connection and their session/output lifecycle before adapting them. There is no generic slash-command catalogue method in the generated public client request union. |

### Evidence and named missing operations

Repository evidence (paths relative to this README):

- [Gateway contract](../../../lib/domain/server_gateway.dart): `CatalogGateway`
  at line 1325 lists commands without a session argument; `PromptGateway` at
  line 1024 has session-scoped slash and shell methods. `ServerCapabilities`
  has no dedicated command-discovery, slash-execution or shell-execution flag.
  Do not equate a model catalogue or terminal capability with those abilities.
- [OpenCode listing](../../../lib/api/product_repository.dart), line 2234;
  [OpenCode dispatch](../../../lib/api/opencode_api.dart), lines 726 and 753.
- [OpenCode 2 listing](../../../lib/api2/gateway_operations.dart), line 1151;
  [OpenCode 2 dispatch](../../../lib/api2/gateway.dart), lines 429 and 445.
- [Paseo gateway](../../../lib/paseo/gateway.dart), lines 1, 901 and 926:
  pinned version, empty catalogue, and unavailable fallback respectively.
  [Paseo connection notes](../../paseo-connection.md), line 68 explicitly mark
  slash commands unavailable in the app.
- [Codex gateway](../../../lib/codex/gateway.dart), lines 1, 593 and 618:
  pinned version, empty catalogue, and unavailable fallback respectively.

Paseo investigation used the local checkout at
`/home/owner/Storage/Code/paseo-spike/paseo-src`, commit
`d636abd7a4ce302e7ccb9eb6074f637c6dd4d83b`. Its server package identifies as
`0.9.0-beta.2`; the installed `@getpaseo/cli` package identifies as `0.9.1`.
Neither establishes compatibility with the app's documented `0.8.0` target.

- `packages/protocol/src/messages.ts:2862`: `list_commands_request` takes
  `agentId`, `requestId`, and optional `draftConfig`.
- The same file at line 6231: `list_commands_response` contains `agentId`,
  `requestId`, nullable `error`, and command records with `name`, `description`,
  `argumentHint`, and optional `kind` (`command` or `skill`).
- `packages/server/src/server/session.ts:4907`: a real handler resolves the
  existing agent and calls `session.listCommands()`; newer source also has a
  draft-provider path. This is stronger evidence than a schema alone, but does
  not establish the supported 0.8.0 contract.
- `packages/server/src/server/agent/providers/claude/agent.ts:2736`: listing
  queries the Claude SDK's `supportedCommands()` and adds `rewind`. Actual
  project commands must be discovered; no fixed Claude catalogue was inferred.
- Its `agent-commands.e2e.test.ts` explicitly identifies itself as fake-daemon
  plumbing coverage. It is not proof of a live authenticated Claude command.

Named unavailable app operations: Claude command discovery and execution,
Claude `rewind` as advertised by the inspected newer source, and Claude shell
execution. CLI actions such as `/compact`, `/clear`, or `/model` must not be
listed as working merely because their names are familiar.

For Codex, generated public schemas locally with:

```sh
/home/owner/.codex/packages/standalone/releases/0.153.4-x86_64-unknown-linux-musl/bin/codex app-server generate-json-schema --out /tmp/p101-codex-schema
```

The command succeeded. `ClientRequest.json` includes `thread/compact/start`,
`review/start`, `skills/list`, `thread/shellCommand`, and `command/exec` (plus
its process lifecycle methods). These are named native operations, not a
server-provided slash catalogue. All remain unmapped in the app command path.
`ThreadShellCommandParams.json` requires `threadId` and `command`, and explicitly
states that execution is unsandboxed rather than inheriting thread policy.
`command/exec` is documented as standalone execution without a thread or turn;
it cannot substitute for the requested in-conversation output card.
Do not silently send `/compact`, `/review`, or `!…` as an ordinary Codex prompt.

## Authentication and privacy

The existing Paseo transport uses the `paseo.bearer.<password>` WebSocket
subprotocol. Direct Codex uses its separate connection capability token during
the WebSocket handshake. Provider authentication stays on the host. This work
did not read provider credentials, connect to a user's session, run a command
against a live agent, or change authentication.

No catalogue, command text, transcript, raw remote error, or credential was
persisted. Any future persistence or diagnostics must use
`KitRedact.text` from `lib/ui/kit/kit_redact.dart`; do not persist raw responses.

## UI hook-up

**No new Dart API is available from this stopped slice.** UI owners must not
expect a controller or wire to an invented endpoint. The existing documented
Dart API is:

```dart
// Existing domain interfaces; not a new implementation.
Future<List<CommandInfo>> CatalogGateway.listCommands();
Future<void> PromptGateway.slashCommand(
  String sessionID, String command, String args, {
  ModelRef? model, String? variant,
});
Future<void> PromptGateway.shell(
  String sessionID, {
  required String command, required String agent,
  ModelRef? model, String? variant,
});
```

For a later implementation:

1. Both the Library row and chat `/` must open one UI sheet backed by the same
   controller and current profile, project/workspace, and conversation scope.
   Existing empty lists on Paseo/Codex mean **unavailable**, not a successfully
   discovered empty catalogue. Show the limitation explicitly.
2. Add a session-aware optional domain command interface (or coordinate a
   change with the `server_gateway.dart` owner). Paseo discovery needs a resolved
   daemon agent ID; the existing sessionless listing signature cannot carry it.
   Avoid making the UI inspect a backend/flavor enum or import protocol clients.
3. Expose separate discovery/execution/shell availability with reason codes,
   immutable entries, plain-language descriptions, search and grouping, and
   typed loading/empty/error states. Only advertised commands are executable;
   plugin links are excluded. Reset state on location/conversation changes and
   discard late results from an earlier scope.
4. Keep arguments editable, execute only after explicit send, and retain the
   current conversation, model and variant. Unknown slash text must not be
   advertised as a successful command dispatch. Never automatically retry an
   execution whose delivery is uncertain.
5. Parse `!` only as an explicit composer action once shell support is verified.
   The UI must disclose skipped approval rules (SEC-10), and the adapter must
   correlate authoritative output/completion with the current conversation.
   An RPC acknowledgement alone is not successful shell completion.

## Required next proof

Resolve the Paseo version contract with the backend owner, then record a
sanitized list → execute → conversation-output test on a permitted test agent.
For Codex, prove native RPC acceptance and output/failure/interrupt semantics
using an isolated supported app-server; a generated schema alone is insufficient.
Keep absent CLI commands named as unavailable. No new provider credential path
or endpoint workaround is proposed.

After that prerequisite, implementation tests should cover returned catalogues,
empty versus unsupported, search/grouping, exact argument dispatch, wrong/stale
session rejection, profile/workspace switches, remote-error sanitization,
duplicate-send prevention, shell output and failure, and disposal during requests.

## Verification and state

- Documentation links checked locally and `git diff --check` run.
- No Dart behavior was added, so no behavior tests, formatter, analyzer or
  Flutter suite was run. This is a docs-only feasibility record, not a passing
  implementation gate or a sandbox-blocked Flutter test claim.
- Implemented: no. Enabled: no. Live-verified: no. UI integrated: no.
- Deployed/released/pushed: no.
- Committed: no. `git add` failed because
  `/home/owner/Storage/Code/oc_app/.git/worktrees/oc_app-codex-p101/index.lock`
  cannot be created on the read-only filesystem. The exact requested message
  is retained in root `COMMIT_MSG.txt`; the README remains untracked.
