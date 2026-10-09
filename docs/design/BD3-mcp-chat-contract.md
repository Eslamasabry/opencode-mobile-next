# BD3: MCP connectors in chat

Date: 2026-10-09. Backend owner: Sol BD. UI owner: coordinator.
Base: `feat/genui-fe` f0443a349. No UI, account, signing or release changes.

Finish line: an agent can recommend a catalog connector through a typed card;
a person can explicitly connect it from chat; a fresh server status determines
whether tools can be used on the next model step in the same conversation.
Non-goal: silently installing packages, collecting credentials in cards, changing
an in-flight model request, or implying unsupported runtimes have this feature.

## Feasibility (before implementation)

| Runtime | Add while running | OAuth from phone | Same conversation tools |
| --- | --- | --- | --- |
| OpenCode 1 | POST `/mcp` stores runtime config; no config rewrite/instance disposal | POST `/mcp/{name}/auth`, validated phone callback forwarded to `/auth/callback`; runtime config lookup supported | Connection finishes tool listing; next model step reads current MCP tool registry. Current in-flight model request cannot change. |
| OpenCode 2 | PUT `/api/mcp/{name}`, scoped to location, lost on restart | Disabled for this flow. Existing app gateway has no MCP OAuth bridge; remote-host loopback callbacks are not a phone callback contract. | Connect lists tools; asynchronous tools-changed eventually refreshes registry, but no callable post-connect readiness barrier exists. Never show Loaded tools. |
| Claude Code / ACP via Paseo 0.9.2 | No callable add/update RPC exposed by app/protocol | No callable MCP OAuth API | MCP config supplied only at launch/load/resume. No in-chat mutation or hot-refresh promise. |

OC1 evidence: installed native binary v1.18.23 probed with an isolated
synthetic MCP server; exact official v1.18.23 source independently inspected at
`packages/opencode/src/mcp/index.ts`, `session/prompt.ts`, `session/tools.ts`.
Runtime add/reconnect and retained session were live verified with no model call.
Next-step tool discovery and OAuth semantics are source-backed, not model/provider proof.
OC2 evidence: upstream commit b8cedc1a7a5e2916bbb65dc1d4b620729c261638,
`packages/core/src/mcp/index.ts`, `tool/mcp.ts`, `session/context.ts`;
app `lib/api2/` retains location-scoped runtime add/connect. Exact probe results
and source references belong in `docs/qa/BD3-mcp-chat-2026-10-09/README.md`.
Paseo: published protocol/server 0.9.2 tarballs verified against the shipped
`assets/agents/paseo-package-lock.json` SHA512 integrity; launch configuration
has `mcpServers`, but no MCP add/auth/refresh RPC. Claude's adapter supplies
servers to query launch; ACP supplies them to new/load session. No real Claude
account was accessed.

`/experimental/tool/ids` is the built-in registry, not an MCP tool inventory.
Do not use its length, a prompt echo, or a successful add response as proof.

## Frozen card shape

Use the existing `oc-ui.show` MCP tool and provenance/revision handling. Add an
optional top-level `connector` to its v1 payload; no new node kind:

```json
{
  "v": 1,
  "id": "suggest-design-reference",
  "title": "Suggested connector: Mobbin",
  "body": [{"type": "text", "text": "Design references could help here."}],
  "connector": {
    "catalogId": "com.example/mobbin",
    "reason": "Find design references for this screen."
  }
}
```

The ID above is illustrative, not a claim about a registry listing. The agent
must use an actual catalog identifier. `catalogId`: ASCII namespace/name,
1–256 characters total, `[A-Za-z0-9._-]+/[A-Za-z0-9._-]+`. `reason`: plain text,
1–500 Unicode scalars after existing control stripping. Unknown keys, URLs,
commands, headers, secrets, or simultaneous `ask` are rejected. Existing 32 KiB
payload limits apply. Typed field: `GenUiCard.connector` of
`GenUiConnectorSuggestion?` with `catalogId` and `reason`.

Recommendations arrive on the existing GenUI transcript/event path. Never infer
a connector from free text or a failed tool name. Existing authenticated-source,
assistant/tool execution, card revision and transcript freshness checks remain.
The field is a suggestion, not authorization. No mutation until the person
presses Connect after seeing the catalog endpoint and runtime-only lifetime.

## Frozen state API

`ConnectionController.createMcpChatController(GenUiCard card, McpCatalogItem item)`
returns a conversation-bound `McpChatController` (`ChangeNotifier`). It rejects
cards without a matching catalog identifier, stale/unavailable cards and changed
profile/location/source. UI observes `snapshot`; dispose when card/chat is gone.

- `Future<void> connect(String serverId)` — serverId must equal the resolved
  catalog item's `serverName`. Coalesce repeated taps. Use existing MCP inventory
  and `McpGateway`; add only a reviewed hosted HTTPS endpoint without required
  keys/settings via `McpConfigScope.runtimeLocation`. No local package execution.
  Existing same-name entries require explicit selection in Tools; do not silently
  attach a catalog recommendation to an unknown existing endpoint.
- `Future<void> startOAuth()` — only after `needsAuthentication`, only with the
  capability below. Returns state containing an inert `authorizationUrl` URI;
  the UI MUST use `openExternalLink`, with its normal origin confirmation. The
  existing `McpOAuthLoopbackListener` and state validation forward the code.
- `Future<void> completeOAuth(String callbackOrCode)` — existing validated manual
  code fallback; never log/store it or echo callback text.
- `Future<void> refresh()` — re-read exact server status, including after browser
  return or uncertain network outcome. No automatic resend of add/auth.
- `Future<void> cancelOAuth()` — close owned callback listener, cancel the pending
  server authentication when still on the original source. Does not disconnect
  or delete the MCP server. **The OC1 endpoint clears saved OAuth credentials
  for that connector**; UI must label/confirm this consequence before calling.
  Dispose and source invalidation close local resources only and never invoke
  that endpoint. No-op cancellation preserves existing state.

Snapshot phases: `suggested`, `connecting`, `needsAuthentication`,
`authorizing`, `checkingTools`, `toolsReady`, `connectedReadinessUnknown`,
`failed`, `unavailable`. Snapshot carries phase, fixed failure enum,
`manualCodeRequired` and an inert launch URI during authorizing. When
`manualCodeRequired` is true, show
“Paste the browser return URL or authorization code to finish sign-in.” Do not
claim browser return alone completes authentication. OC1 starts its own
loopback listener before returning the launch URL; on the same phone that port
can already be occupied. Its `/auth` start does not register a callback waiter,
so this path requires the explicit manual fallback. Remote-host OAuth can use
the phone-owned loopback listener. Actual provider/device OAuth remains
unqualified. Snapshots contain no tokens, raw server errors, callback codes,
or tool arguments. Controller holds no persistent preferences/blobs.
Profile deletion, card replacement, lost qualification, or location change
invalidates it and closes the listener. Browser suspension may replace the
transport; the controller retains accepted intent but recovers the authoritative
card before any further request. Later results cannot update another conversation.

Fresh exact-name `connected` status means that runtime's MCP handshake/tool
listing finished, not that a particular tool count is known. `toolsReady` also
requires `mcpChatToolRefresh`. It means available for the next model step in this
same conversation. UI copy: **“Loaded tools. Continue this conversation to use
them.”** Do not auto-send a model prompt or claim an in-flight request updated.
If connection is proven but registry readiness cannot be confirmed, use
**“Connected. Tool availability has not been confirmed.”** OC2 uses this state.
Do not imply a new conversation fixes an unconfirmed registry. For any future
runtime proven to require a new conversation, copy must be “Connected. Start a
new conversation to use these tools.” No current adapter advertises that path;
Paseo cannot connect from chat at all.

## Capabilities and copy

Add default-false fields to `ServerCapabilities`, preserved by `withGenUi`:

| Flag | OC1 | OC2 | Paseo / other defaults |
| --- | --- | --- | --- |
| `mcpChatConnect` | true | true | false |
| `mcpChatOAuth` | true (phone browser flow still needs device qualification) | false | false |
| `mcpChatToolRefresh` | true | false | false |

Recommendation display/connect UI also requires `genUi`; no flavor checks.
Existing `mcpRuntimeAdds`, `mcpOAuth`, and `serverCatalog` remain additional
operation gates. These flags describe callable contracts, not device certification.

Failures have fixed enum keys and plain copy (localize in frontend):

| Key | Copy |
| --- | --- |
| unavailable | Connecting tools from chat is not available for this agent. |
| invalidSuggestion | This connector suggestion is not available. Browse connectors in Tools. |
| setupRequired | This connector needs setup in Tools before it can connect. |
| nameConflict | A connector with this name already exists. Check it in Tools. |
| sourceChanged | This conversation's connection changed. Reopen the connector card. |
| connectFailed | Could not confirm the connection. Check its status before trying again. |
| authenticationFailed | Sign-in could not be confirmed. Check its status before trying again. |
| oauthUnavailable | Sign-in for this connector is not available in this chat. |
| notConnected | The connector is not connected yet. Check its setup in Tools. |

Credentials stay in runtime-owned storage. URLs remain inert outside the normal
external-link gate. Runtime-only add does not persist config and never rewrites
project/global configuration, restarts a server, or starts a new conversation.


## Agent connector search (owner addition, 2026-10-09)

Finish line: the agent discovers real catalog IDs using a read-only tool before
suggesting a connector; cached results work offline without installing anything.
Non-goal: automatic registry downloads, inferring OAuth support, or expanding
which runtimes qualify for Agent cards.

The same `oc-ui` MCP helper advertises `find_connectors` next to `show` (runtime
names may be `oc-ui_find_connectors` / `mcp__oc-ui__find_connectors`). Arguments:
`{"query":"design inspiration","limit":5}`. Query is 1–100 Unicode scalars after
trim, limit is an integer 1–10 (default 5); unknown properties, URLs, credentials
and malformed inputs fail with **“Invalid connector search request.”** No query
is sent to the public registry. Matching is case-insensitive across catalog ID,
name and description, requiring all whitespace-separated terms; canonical IDs
are sorted and deduplicated. At most 100 cached rows and 10 results are inspected
and returned respectively.

Success shape (no URLs, packages, commands, headers, keys or raw metadata):

```json
{"status":"ok","matches":[{"catalogId":"com.example/design","name":"Design",
"description":"Search design inspiration.","runtime":"hosted",
"needsSignIn":"unknown","connected":null}]}
```

`runtime` is `hosted`, `npx`, `uvx`, `docker` or `unavailable`, projected using
`McpCatalogItem` exactly as Tools > MCP. `needsSignIn` is `required`,
`not_required` or `unknown`: the current registry has no reliable OAuth field,
so **all current entries return `unknown`**. Required key declarations are not
OAuth evidence. `connected` is boolean only from an exact-name current source
inventory; null means that inventory could not be confirmed. It follows the
catalogue's name mapping, is not proof of endpoint identity, and never authorizes
attaching to a name collision. Description is single-line, at most 300 scalars;
name at most 120. Registry prose is untrusted, redacted and URL-stripped.

A missing or unreadable cache returns:
`{"status":"catalogue_not_loaded","message":"Catalogue not loaded. Open Tools > MCP to load it.","matches":[]}`.
A valid empty cache/search returns `ok` with no matches. Search uses the saved
default catalogue, not transient online search results in another screen.
The existing profile cache and its deletion rules are reused; search never
changes opt-in, fetches, installs, connects or saves credentials.

**Transport decision:** round-trip to the app, rather than reading the public
registry directly. The app alone owns the Tools catalogue cache and current
profile/source truth. The helper reads an app-published adjacent ephemeral
bridge descriptor, then POSTs to a fixed IPv4 loopback endpoint with an
unpredictable bearer. It sends validated arguments plus its own process working
directory (not supplied by the agent). The app validates profile/generation,
Agent-cards qualification and source before and after asynchronous reads.
Only the matching source can supply connected status; ambiguity yields null.
The descriptor is at `<enabled-marker>.search.json`, atomically written with
private permissions through the existing managed host bridge. It contains no
MCP credentials. It expires in effect when the in-memory HTTP server closes;
it grants only this bounded read-only search. A stale helper returns
`{"status":"unavailable","message":"Connector search is unavailable. Try again from Tools.","matches":[]}`.
No daemon, arbitrary URL, remote registry request, or new persistent app format.
HTTP request <=2 KiB, response <=16 KiB, <=4 concurrent searches, 5-second
request deadline. The helper rechecks the enabled marker on every call and
rejects symlink descriptors. No transport token or raw exception is logged.

**UI contract:** this is a normal tool step, not a card or question. Recognize
only the trusted Agent-cards tool name/provider using the existing runtime tool
identity rules and show **“Searched connectors ›”** on completion (and normal
working/failure state during the call). Its bounded `structuredContent` and
JSON text contain the same result; expand the step to show matches or the fixed
empty/unloaded/unavailable message. Do not interpret search as user consent.
The agent may pass a returned `catalogId` into `show.connector`; Connect still
requires the existing reviewed card flow. No new `ServerCapabilities` override:
search exists only on the same installed and qualified Agent-cards MCP helper.
OC1/OC2/Paseo keep their existing cards qualification; search does not turn on
Paseo runtime add/OAuth/refresh. Helper/bridge host tests do not qualify a real
phone agent or provider.
