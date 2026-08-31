# OpenCode 2 (v2 API) protocol notes — beta-18600

Reference for porting the Flutter client from the v1 server API to the OpenCode 2 API.

**Sources (in order of authority):**

- Live server `opencode2 serve` **v0.0.0-beta-18600** at `http://127.0.0.1:4097` (all examples below are real captured payloads).
- Typed client `@opencode-ai/client@0.0.0-beta-18600` and its dependencies `@opencode-ai/schema` + `@opencode-ai/protocol` (Effect Schema definitions — the ground truth for every payload the OpenAPI spec hides behind `V2EventEncoded`).
- Captured spec `contracts/opencode2-openapi-beta-18600.json` (114 paths, 222 schemas).

> NOTE: `~/node_modules/@opencode-ai/sdk` (1.1.63) and the 1.4.x SDKs are the **v1** SDK line; their `dist/v2` module describes an older intermediate API (`/global/*`, message "parts", "questions") that does **not** exist on this server. Ignore them.

---

## 1. Auth & connection

- Every endpoint lives under **`/api/...`**. There are **no unauthenticated endpoints** — even `GET /api/health` returns `401` + `WWW-Authenticate: Basic realm="Secure Area"` without credentials.
- Auth is **HTTP Basic**, username is always **`opencode`**, password is the serve password.
  - `Authorization: Basic base64("opencode:<password>")`
  - Alternative for contexts where headers are impossible (WebSocket from browsers, EventSource): query param **`?auth_token=<base64("opencode:<password>")>`** is accepted on any route.
  - PTY WebSocket connects can instead use a one-time **ticket** (`?ticket=...`, §9) which bypasses Basic auth for that one upgrade.
- **Password source** (server side): env `OPENCODE_PASSWORD`, falling back to `OPENCODE_SERVER_PASSWORD`; if neither is set, `opencode2 serve` generates a random 32-byte base64url password and prints `server password <pw>` on stdout. There is no CLI flag for it.
- **Discovery contract** (how first-party clients find a local server): the background service registers itself in `$XDG_STATE_HOME/opencode/service.json` (default `~/.local/state/opencode/service.json`) as `{"id","url","pid","version","password"}`. A client reads that file, probes `GET /api/health`, and checks `body.pid === file.pid` and `body.version === file.version` before trusting it. For our app (remote server over the network) the user supplies host + password; store the password in secure storage and send Basic auth on every request.
- `GET /api/health` (authed) → `{"healthy":true,"version":"0.0.0-beta-18600","pid":1471586}`. Status is **200** when ready, **503** while starting/stopping (with `retry-after: 1`), **500** when the app layer failed to boot. Any non-health request during boot returns 503 `{"code":"service_starting"|"service_stopping"}` or `{"code":"service_failed",...}`.
- `GET /api/server` → `{"urls":["http://127.0.0.1:4097"]}`.
- CORS is enforced (`vary: Origin`); native clients are unaffected.

## 2. Wire conventions

- Plain JSON bodies (`content-type: application/json`). Timestamps are **epoch milliseconds** (numbers). Money is a plain USD number, tokens are `{input, output, reasoning, cache:{read,write}}`.
- ID prefixes: `ses_` session, `msg_` message *and inbox item*, `evt_` event, `per_` permission, `frm_` form, `pty_` pty, `sh_` shell, `cred_` credential, `con_` integration attempt, `psv_` saved permission. Session IDs sort **descending** (newest first); message/event IDs sort ascending.
- Response envelopes:
  - Most reads: `{"data": ...}`.
  - Location-scoped reads: `{"location": {directory, workspaceID?, project:{id,directory,canonical}}, "data": ...}`.
  - Writes that return nothing: **HTTP 204 empty body** (a lot of endpoints — rename, reply, cancel, delete, etc.).
- **Location scoping** — this replaces v1's `?directory=` param. Location-scoped endpoints take a deep-object query param:
  `?location[directory]=/abs/path` and optionally `&location[workspace]=<workspaceID>` (URL-encoded: `location%5Bdirectory%5D=...`). Omitting it uses the server's default location. A client pinned to one project directory should send `location[directory]` on every location-scoped call (agents, models, fs, vcs, pty, shell, skills, commands, forms/permission pending lists, mcp, providers, config, plugin, reference, websearch) and pass `location: {directory}` in the body of `POST /api/session`. `GET /api/location?location[directory]=...` resolves it:

```json
{"directory":"/home/dev/projects/oc_app",
 "project":{"id":"5e625357c202857bf3cd6f591d76b2bec8b5093d",
            "directory":"/home/dev/projects/oc_app",
            "canonical":"/home/dev/projects/oc_app"}}
```

- **Errors** are tagged JSON objects, one per error class, discriminated by `_tag`, with class-specific extra fields and a fixed HTTP status (§12). Live example (404):

```json
{"_tag":"SessionNotFoundError","sessionID":"ses_doesnotexist","message":"Session not found: ses_doesnotexist"}
```

- Schema-validation failures on any endpoint surface as `400 {"_tag":"InvalidRequestError","message":...,"kind"?,"field"?}`.

## 3. Event stream — `GET /api/event`

SSE (`text/event-stream`). Each frame is a bare `data: <json>` line (no `event:`/`id:` SSE fields); keep-alives are SSE comments `: heartbeat`. Client SSE buffers should allow large events (the reference client caps one SSE event at 16 MB).

**Contract: the stream is volatile.** `server.connected` is emitted first; events during a disconnect are lost, and a slow consumer overflows and fails the stream. Reconcile by refetching state (session list, messages, pending permissions/forms) after reconnect — or use the per-session durable log (§3.3) for transcripts.

### 3.1 Event envelope

```json
{
  "id": "evt_04ac0f582001ALjED5PyXUip9c",
  "created": 1787960554882,
  "type": "session.text.delta",
  "location": {"directory": "/home/dev/projects/oc_app"},
  "metadata": { "...": "optional, freeform" },
  "data": { "...": "type-specific payload" },
  "durable": {"aggregateID": "ses_...", "seq": 7, "version": 1}
}
```

- `location` is present when the event belongs to a project directory — **filter on it** if the server hosts several projects.
- `durable` is present only on durable (event-sourced) events: `seq` is the per-aggregate (per-session) sequence number, usable as an `after` cursor on the session log. Ephemeral events (deltas, status, toasts…) have no `durable`.

### 3.2 Full v2 event union (91 types)

Session lifecycle (aggregate = sessionID; ⚡ = ephemeral, others durable):

| type | data payload |
|---|---|
| `session.created` | `{sessionID, projectID, location, subpath?, parentID?, slug, title?, agent?, model?, version, metadata?}` |
| `session.renamed` | `{sessionID, title}` |
| `session.deleted` | `{sessionID}` |
| `session.moved` | `{sessionID, location, projectID, subpath?}` |
| `session.forked` | `{sessionID, parentID, boundary:{type:"before"\|"through", messageID}, instructions?, instructionEntries?}` |
| `session.viewed` | `{sessionID, idle}` (epoch-ms idle watermark marked as seen) |
| `session.agent.selected` | `{sessionID, agent, previous?}` |
| `session.model.selected` | `{sessionID, model:{id,providerID,variant?}, previous?}` |
| ⚡ `session.usage.updated` | `{sessionID, cost, tokens}` (running totals) |
| `session.execution.started` | `{sessionID}` |
| `session.execution.succeeded` | `{sessionID}` |
| `session.execution.failed` | `{sessionID, error}` |
| `session.execution.interrupted` | `{sessionID, reason:"user"\|"shutdown"\|"superseded"}` |
| `session.inbox.enqueued` | `{sessionID, inboxID, item:{type,payload,delivery}}` |
| `session.inbox.delivered` | `{sessionID, inboxID}` |
| `session.inbox.cancelled` | `{sessionID, inboxID}` |
| `session.inbox.delivery.changed` | `{sessionID, inboxID, delivery:"steer"\|"queue"}` |
| `session.instructions.updated` | `{sessionID, delta:{key→hash\|"removed"}, text?}` |
| `session.synthetic` | `{sessionID, text, description?, metadata?}` |
| `session.skill.activated` | `{sessionID, id, name, text}` |
| `session.shell.started` | `{sessionID, shell: Shell.Info}` |
| `session.shell.ended` | `{sessionID, shell: Shell.Info, output: Shell.Output}` |
| `session.step.started` | `{sessionID, assistantMessageID, agent, model, snapshot?}` |
| `session.step.streamed` | `{sessionID, assistantMessageID}` (provider response body finished, before tool settlement) |
| `session.step.ended` | `{sessionID, assistantMessageID, finish, rawFinish?, providerState?, cost, tokens, snapshot?, files?}` |
| `session.step.failed` | `{sessionID, assistantMessageID, error, finish?:"content-filter", rawFinish?, providerState?, cost?, tokens?, snapshot?, files?}` |
| `session.text.started` | `{sessionID, assistantMessageID, ordinal}` |
| ⚡ `session.text.delta` | `{sessionID, assistantMessageID, ordinal, delta}` |
| `session.text.ended` | `{sessionID, assistantMessageID, ordinal, text, state?}` (full text — the replayable boundary) |
| `session.reasoning.started` | `{sessionID, assistantMessageID, ordinal, state?}` |
| ⚡ `session.reasoning.delta` | `{sessionID, assistantMessageID, ordinal, delta}` |
| `session.reasoning.ended` | `{sessionID, assistantMessageID, ordinal, text, state?}` |
| `session.tool.input.started` | `{sessionID, assistantMessageID, id, name}` |
| ⚡ `session.tool.input.delta` | `{sessionID, assistantMessageID, id, delta}` (raw JSON-arg text chunks) |
| `session.tool.input.ended` | `{sessionID, assistantMessageID, id, text}` |
| `session.tool.called` | `{sessionID, assistantMessageID, id, input:{...}, executed, state?}` |
| ⚡ `session.tool.progress` | `{sessionID, assistantMessageID, id, metadata}` (live replacement metadata) |
| `session.tool.success` | `{sessionID, assistantMessageID, id, content:[ToolContent,...], metadata?, executed, resultState?}` |
| `session.tool.failed` | `{sessionID, assistantMessageID, id, error, content?, metadata?, executed, resultState?}` |
| `session.retry.scheduled` | `{sessionID, assistantMessageID, attempt, at, error}` |
| `session.compaction.started` | `{sessionID, reason:"auto"\|"manual", recent, inputID?}` |
| ⚡ `session.compaction.delta` | `{sessionID, text}` |
| `session.compaction.ended` | `{sessionID, reason, text, recent}` |
| `session.compaction.failed` | `{sessionID, reason, error, inputID?}` |
| `session.revert.staged` | `{sessionID, revert: Revert}` |
| `session.revert.cleared` | `{sessionID}` |
| `session.revert.committed` | `{sessionID, to: messageID}` |
| `session.message.content.updated` | `{sessionID, messageID, content:[AssistantContent…]}` (message edited via PATCH) |

Session status (all ⚡): `session.status` `{sessionID, status}` where status is `{type:"idle"}` | `{type:"busy"}` | `{type:"retry", attempt, message, next, action?:{reason,provider,title,message,label,link?}}`; deprecated `session.idle` `{sessionID}`; `session.compacted` `{sessionID}`.

Permissions/forms (all ⚡): `permission.asked` (payload = full `Permission.Request`, §7), `permission.replied` `{sessionID, requestID, reply}`, `form.created` `{form: Form.Info}`, `form.replied` `{id, sessionID, answer}`, `form.cancelled` `{id, sessionID}`.

PTY/shell (all ⚡): `pty.created`/`pty.updated` `{info: Pty.Info}`, `pty.exited` `{id, exitCode}`, `pty.deleted` `{id}`; `persistent-pty.added` `{sessionID, terminal: PersistentPty.Info}`, `persistent-pty.removed` `{sessionID, ptyID}`; `shell.created` `{info: Shell.Info}`, `shell.exited` `{id, exit?, status}`, `shell.deleted` `{id}`.

Catalog/config change pings — all ⚡, empty or tiny payloads, meaning "refetch that list": `agent.updated`, `command.updated`, `config.updated`, `skill.updated`, `catalog.updated`, `models-dev.refreshed`, `integration.updated`, `credential.updated`, `credential.switched` `{integrationID, credentialID|null}`, `plugin.added` `{id}`, `plugin.updated`, `reference.updated`, `websearch.updated`, `project.updated` (full Project.Info fields), `worktree.updated` `{projectID}`, `worktree.resolved` (durable, `{projectID, directory, previous, adopted?}`), `filesystem.changed` `{file, event:"add"|"change"|"unlink"}`, `vcs.branch.updated` `{branch?}`, `mcp.status.changed`/`mcp.resources.changed`/`mcp.tools.changed` `{server}`, `installation.updated`/`installation.update-available` `{version}`, `workspace.ready` `{name}`, `workspace.failed` `{message}`, `workspace.status` `{workspaceID, status:"connected"|"connecting"|"disconnected"|"error"}`, `worktree.ready` `{name, branch?}`, `worktree.failed` `{message}`, `server.connected` `{}`, `global.disposed` `{}`, `lsp.updated` `{}`.

TUI control events (⚡, emitted for/by the first-party TUI; safe to ignore or honor selectively): `tui.prompt.append` `{text}`, `tui.command.execute` `{command}`, `tui.toast.show` `{title?, message, variant:"info"|"success"|"warning"|"error", duration}`, `tui.session.select` `{sessionID}`.

### 3.3 Durable per-session log — `GET /api/experimental/session/{sessionID}/log?after=<seq>&follow=<bool>`

SSE stream of the session's **durable** events only (everything in §3.2 marked durable, plus internal `session.usage.recorded`), replayed from the exclusive `after` sequence; with `follow=true` it continues with live events. The replay/live boundary is marked once by:

```json
data: {"type":"log.synced","aggregateID":"ses_fb53f2ae6ffe2lNSphGKicmVR2","seq":10}
```

(`seq` absent when the log is empty). This is the reliable way to rebuild a transcript and resume after disconnects: persist the last seen `durable.seq` per session, then `?after=<seq>&follow=true`. Deltas (`*.delta`, `tool.progress`, `usage.updated`, `status`) never appear here — the `*.ended` events carry the full values.

### 3.4 Live capture of a prompt round-trip (abridged, real)

```
data: {"id":"evt_...","type":"server.connected","data":{}}
data: {"type":"session.created", "data":{"sessionID":"ses_fb53...","slug":"stellar-garden","version":"0.0.0-beta-18600","projectID":"5e62...","location":{"directory":"/home/dev/projects/oc_app"},"subpath":"","title":"protocol probe"},"durable":{"aggregateID":"ses_fb53...","seq":0,"version":1}}
data: {"type":"session.inbox.enqueued","data":{"inboxID":"msg_04ac0eafe001...","sessionID":"ses_fb53...","item":{"type":"user","payload":{"text":"Reply with exactly the word: pong"},"delivery":"steer"}},"durable":{"seq":1,...}}
data: {"type":"session.execution.started","data":{"sessionID":"ses_fb53..."},"durable":{"seq":2,...}}
data: {"type":"session.instructions.updated","data":{"sessionID":"ses_fb53...","delta":{"core/environment":"1adeb...","core/date":"6c8d..."}},"durable":{"seq":3,...}}
data: {"type":"session.inbox.delivered","data":{"sessionID":"ses_fb53...","inboxID":"msg_04ac0eafe001..."},"durable":{"seq":4,...}}
data: {"type":"session.step.started","data":{"sessionID":"ses_fb53...","agent":"build","model":{"id":"gpt-5.6-sol","providerID":"openai"},"assistantMessageID":"msg_04ac0eb30001...","snapshot":"c5104f07..."},"durable":{"seq":5,...}}
data: {"type":"session.text.started","data":{"...","ordinal":0},"durable":{"seq":6,...}}
data: {"type":"session.text.delta","data":{"...","ordinal":0,"delta":"pong"}}
data: {"type":"session.text.ended","data":{"...","ordinal":0,"text":"pong","state":{"itemId":"msg_...","phase":"final_answer"}},"durable":{"seq":7,...}}
data: {"type":"session.step.streamed","data":{"..."},"durable":{"seq":8,...}}
data: {"type":"session.step.ended","data":{"...","finish":"stop","providerState":{"responseId":"resp_..."},"cost":0,"tokens":{"input":11157,"output":5,"reasoning":0,"cache":{"read":0,"write":0}},"snapshot":"c5104f07...","files":[]},"durable":{"seq":9,...}}
data: {"type":"session.usage.updated","data":{"sessionID":"ses_fb53...","cost":0,"tokens":{...}}}
data: {"type":"session.execution.succeeded","data":{"sessionID":"ses_fb53..."},"durable":{"seq":10,...}}
```

## 4. Sessions

### 4.1 Session.Info

```json
{
  "id": "ses_fb53f2ae6ffe2lNSphGKicmVR2",
  "parentID": "ses_... (subagent/fork child only)",
  "fork": {"sessionID": "ses_parent", "boundary": {"type": "before|through", "messageID": "msg_..."}},
  "projectID": "5e625357c202857bf3cd6f591d76b2bec8b5093d",
  "agent": "build",
  "model": {"id": "gpt-5.6-sol", "providerID": "openai", "variant": "high"},
  "cost": 0,
  "tokens": {"input": 0, "output": 0, "reasoning": 0, "cache": {"read": 0, "write": 0}},
  "outcome": "succeeded|failed|interrupted",
  "time": {"created": 1787960546605, "updated": 1787960546605, "idle": 0, "viewed": 0, "archived": 0},
  "title": "protocol probe",
  "location": {"directory": "/home/dev/projects/oc_app", "workspaceID": "..."},
  "subpath": "",
  "revert": {"messageID": "msg_...", "snapshot": "...", "files": [FileDiff...]},
  "metadata": {"any": "json (host-supplied, inherited by children/forks)"}
}
```

All optional fields are simply **absent** when unset (the server strips `undefined`). There is no `share`/`version`/`slug` on the projection (slug appears only on the `session.created` event). `agent`/`model` are the session's current selection. Busy/idle is **not** a field — derive it from `session.status` events, from `outcome`+`time.idle`, or from `GET /api/session/active`.

### 4.2 Endpoints

| method path | notes |
|---|---|
| `GET /api/session` | List. Query: `directory=<abs>` **or** `project=<projectID>&subpath=?` (or neither = all), plus `workspace?`, `limit?` (default 50), `order=asc\|desc` (default desc = newest first), `search?` (title filter), `parentID=<ses_...\|null>` (`null` → roots only), `cursor?`. Response `{"data":[Session.Info...],"cursor":{"previous"?,"next"?}}`. Cursors are opaque base64url strings; pass one back as `?cursor=` **alone** (don't combine with `order`/`limit` changes — the filter set is baked into the cursor). |
| `POST /api/session` | Body `{id?, title?, agent?, model?:{id,providerID,variant?}, location?:{directory,workspaceID?}, metadata?}` → `{"data": Session.Info}`. |
| `GET /api/session/active` | `{"data": {"ses_x": {"type":"running"}, ...}}` — sessions with live execution owned by this server process. |
| `GET /api/session/stats` | Query `from?,to?` (epoch ms), `project?`, `timezone?`, `tools?=none\|summary\|detail` → aggregate usage/activity/tool-reliability (`SessionStats.Info`). |
| `GET /api/session/{id}` | `{"data": Session.Info}` |
| `DELETE /api/session/{id}` | 204. Deletes children too. |
| `POST /api/session/{id}/fork` | `{boundary: {type:"before", messageID} \| {type:"through"}}` → new child session. |
| `POST /api/session/{id}/agent` | `{agent: "plan"}` → 204 (switch agent for subsequent turns). |
| `POST /api/session/{id}/model` | `{model: {id, providerID, variant?}}` → 204. |
| `POST /api/session/{id}/rename` | `{title}` → 204. |
| `POST /api/session/{id}/move` | `{directory, workspaceID?, delivery?}` → 204. Moves session to another project dir. |
| `POST /api/session/{id}/view` | `{idle: <epoch-ms>}` → 204. Marks the idle transition as viewed (unread tracking). |
| `POST /api/session/{id}/wait` | 204 when the agent loop becomes idle (long-poll; used it live, ~3 s). |
| `GET /api/session/{id}/export` / `POST /api/session/import` | `{"data": {info: Session.Info, messages: [Message...]}}` / same shape + `location?` (import parents before children). |
| `PUT /api/session/{id}/environment` | `{variables: {K:V}}` → 204. Env for session-local shell commands. |
| `GET/PUT/DELETE /api/session/{id}/instructions/entries[/{key}]` | API-managed instruction entries: `{"data":[{key,value}]}`; PUT body `{value: <json ≤8KB>}` (413 `InstructionEntryValueTooLargeError` above). Announced to the model at the next step boundary. |
| `POST /api/session/{id}/generate` | `{prompt}` → `{"data":{"text":"..."}}` — side-channel generation over the session context, doesn't touch history. |
| `POST /api/generate` | `{prompt, model?}` → `{"data":{"text"}}` — fully stateless one-shot. |

## 5. Messages & content

**There are no "parts" as separate objects in v2.** `GET /api/session/{id}/message` returns a paginated list of *messages*; assistant messages embed their content array. The message union (`Session.Message.Info`, discriminated by `type`) has **10 variants**:

| `type` | fields (besides `id`, `time.created`, `metadata?`) |
|---|---|
| `user` | `text`, `files?: [FileAttachment]` (server-side form: `{data: base64, mime, source:{type:"inline"}\|{type:"uri",uri}, name?, description?, mention?}`), `agents?`, `skills?` |
| `assistant` | `agent`, `model:{id,providerID,variant?}`, `content: [AssistantContent...]`, `snapshot?:{start?,end?,files?}`, `finish?` (`stop\|length\|tool-calls\|content-filter\|error\|unknown`), `rawFinish?`, `providerState?`, `cost?`, `tokens?`, `error?`, `retry?:{attempt,at,error}`, `time:{created, streamed?, completed?}` |
| `synthetic` | `text`, `description?` — client/tool-injected context shown to the model |
| `system` | `text` (model-facing update), `description?` |
| `skill` | `skill` (id), `name`, `text` |
| `shell` | `shellID`, `command`, `status:"running"\|"exited"\|"timeout"\|"killed"`, `exit?`, `output?:{output,cursor,size,truncated}`, `time:{created,completed?}` |
| `agent-switched` | `agent`, `previous?` |
| `model-switched` | `model`, `previous?` |
| `location-switched` | `location`, `projectID?`, `subpath?`, `previous?` |
| `compaction` | `status:"running"\|"completed"\|"failed"`, `reason:"auto"\|"manual"`, `summary`/`recent` (running/completed) or `error` (failed) |

**AssistantContent** union (discriminated by `type`):

- `{"type":"text","text":"...","state?":{...provider state...}}`
- `{"type":"reasoning","text":"...","state?":{...},"time?":{created,completed?}}`
- `{"type":"tool","id":"<callID>","name":"read","executed?":bool,"providerState?":{},"providerResultState?":{},"state": ToolState,"time":{created,ran?,completed?}}`

**ToolState** union (discriminated by `status`):

- `{"status":"streaming","input":"<partial raw json text>"}`
- `{"status":"running","input":{...},"metadata":{...}}` — `metadata` is live tool progress (replaced wholesale by `session.tool.progress`)
- `{"status":"completed","input":{...},"content":[{"type":"text","text":"..."} | {"type":"file","uri","mime","name?"}, ...],"metadata?":{...}}`
- `{"status":"error","input":{...},"error":{type,message,status?},"content?":[...],"metadata?":{...}}`

Tool output is `content` — an array of `Tool.Content` (`text` and/or `file`) — **not** a single output string like v1.

Endpoints:

| method path | notes |
|---|---|
| `GET /api/session/{id}/message` | Query `limit?` (1–200), `order=asc\|desc` (default desc), `cursor?` (opaque; don't combine with `order`). → `{"data":[Message...],"cursor":{previous?,next?}}` |
| `GET /api/session/{id}/message/{messageID}` | `{"data": Message}` |
| `PATCH /api/session/{id}/message/{messageID}` | `{content:[AssistantContent...]}` → edits a completed assistant message in an idle session (409 `SessionBusyError`/`ConflictError` otherwise). Emits `session.message.content.updated`. |
| `GET /api/session/{id}/context` | `{"data":[Message...]}` — the live model context (everything after the last compaction). |

**Streaming model:** while a turn runs, the messages endpoint lags; build the in-flight assistant message from events: `step.started` (gives `assistantMessageID`, agent, model) → per-ordinal `text/reasoning .started/.delta/.ended` → per-callID `tool.input.started/.delta/.ended` → `tool.called` (parsed input) → `tool.progress`* → `tool.success|failed` → `step.streamed` → `step.ended` (finish/cost/tokens) → next step or `execution.succeeded|failed|interrupted`. `message.updated`-style events do not exist; refetch or project.

## 6. Prompt flow, inbox, steer/queue

### 6.1 `POST /api/session/{id}/prompt`

```json
{
  "id": "msg_optional-client-generated",
  "text": "Reply with exactly the word: pong",
  "files": [{"uri": "file:///abs/path.png", "name": "path.png", "description": "optional", "mention": {"start":0,"end":9,"text":"@path.png"}}],
  "agents": [{"name": "explore", "mention": {"start":..,"end":..,"text":"@explore"}}],
  "skills": [{"id": "skill-id", "mention": {...}}],
  "metadata": {"any": "json"},
  "delivery": "steer",
  "resume": true
}
```

→ `202`-style `200` immediately with the **inbox item**, not the message:

```json
{"data":{"id":"msg_04ac0eafe001j709Plu4Jd3Gco","sessionID":"ses_fb53...","timeCreated":1787960552193,
         "type":"user","payload":{"text":"Reply with exactly the word: pong"},"delivery":"steer"}}
```

Key facts:

- **No model/agent/system/tools fields on the prompt.** Model + agent are session state (`POST .../model`, `.../agent`, or set at create). `agents` here are @-mentions (attachment hints), not agent selection. This is the biggest v1 difference.
- **File attachments are URIs**: `data:<mime>;base64,....` for inline uploads, or `file:///abs/path` for server-local files (text files accept `?start=<line>&end=<line>` ranges). Max 20 MB per attachment; images are auto-normalized/resized server-side. The server materializes them to base64 `FileAttachment`s on the stored user message.
- **Prompting is asynchronous & durable.** The prompt is admitted to the session **inbox** and the agent loop is scheduled (`resume:false` admits without scheduling). Completion is observed via events (`session.execution.succeeded`) or `POST .../wait`.
- **`delivery`** (default **"steer"**): `"steer"` interrupts/injects at the next step boundary of a running turn; `"queue"` waits until the current work finishes. When idle they behave the same.
- 409 `ConflictError` when reusing a client-supplied `id`.

### 6.2 Inbox management

- `GET /api/session/{id}/inbox` → `{"data":[InboxItem...]}` — durably admitted, not-yet-delivered work. Item union by `type`: `user` (payload = prompt), `synthetic` `{text,description?,metadata?}`, `compaction` `{}`, `move` `{location,projectID,subpath?}`; every item: `{id, sessionID, timeCreated, type, payload, delivery}`.
- `DELETE /api/session/{id}/inbox/{inboxID}` → 204 cancel undelivered item (409 if already delivered).
- `POST /api/session/{id}/inbox/{inboxID}/steer` / `.../queue` → 204 flip delivery mode of a pending item.
- A client only needs this UI when it lets users queue prompts while a turn runs (show pending items, allow cancel/steer). Fire-and-forget clients can ignore it beyond the prompt response.

### 6.3 Related session inputs

- `POST /api/session/{id}/synthetic` `{id?, text, description?, metadata?, delivery?, resume?}` → `{"data": InboxSynthetic}` — inject context without a user turn.
- `POST /api/session/{id}/command` `{command, text, files?, agents?, skills?, delivery?}` → 204 — execute a registered slash command (404 `CommandNotFoundError`, 500 `CommandExecutionError`).
- `POST /api/session/{id}/skill` `{id?, skill, resume?}` → 204 — activate a skill.
- `POST /api/session/{id}/compact` `{id?, delivery?}` → `{"data": InboxCompaction}` — manual compaction (steers by default).
- `POST /api/session/{id}/shell` `{id?, command}` → 204 — run a shell command *in the transcript* (emits `session.shell.started/ended`, shows as a `shell` message).
- **Interrupt**: `POST /api/session/{id}/interrupt[?continue=true]` → `{"interrupted": true|false}` (live-verified `false` on idle). `continue=true` resumes pending steering input after the interrupt.
- `POST /api/session/{id}/background` → 204 — move blocking backgroundable tools to background observation.
- Revert: `POST .../revert/stage` `{messageID, files?}` → `{"data": Revert}`; `POST .../revert/clear`; `POST .../revert/commit` (both 204; 409 `SessionBusyError` while running).

## 7. Permissions

Shapes (`@opencode-ai/schema/permission`):

```json
// Permission.Request  (also the payload of the ephemeral event "permission.asked")
{
  "id": "per_...",
  "sessionID": "ses_...",
  "action": "bash",                       // permission action, e.g. read|edit|bash|webfetch|...
  "resources": ["git push*"],             // what it wants to touch
  "save": ["git push*"],                  // patterns that "always" would persist (optional)
  "metadata": {"...": "tool-specific"},
  "source": {"type": "tool", "messageID": "msg_...", "id": "<callID>"},
  "message": "optional human context"
}
```

- `Permission.Reply` = `"once" | "always" | "reject"`; `Permission.Effect` = `"allow" | "deny" | "ask"`.
- Agent permission config is an ordered ruleset: `[{action, resource, effect}]` (see `Agent.Info.permissions`).

| method path | notes |
|---|---|
| `GET /api/permission/request?location[directory]=...` | All pending requests for a location → `{location, data:[Request...]}` (live: `[]`). |
| `GET /api/session/{id}/permission` | Pending requests owned by one session → `{"data":[...]}`. |
| `GET /api/session/{id}/permission/{requestID}` | One request. |
| `POST /api/session/{id}/permission/{requestID}/reply` | `{reply: "once"\|"always"\|"reject", message?}` → 204. `message` is shown to the model on reject. |
| `POST /api/session/{id}/permission` | Create/evaluate a request programmatically → `{"data":{id, effect}}`. |
| `GET /api/permission/saved?projectID=?` / `DELETE /api/permission/saved/{id}` | "always" persisted grants: `{id, projectID, action, resource}`. |

Flow: tool hits a rule with effect `ask` → server emits **`permission.asked`** (ephemeral) and the tool blocks → client replies via REST → `permission.replied` broadcast. After reconnect, poll the pending list — asks are *not* replayed on `/api/event`.

## 8. Forms (replaces v1 "questions")

The model/tool asks structured questions through **forms**; MCP elicitation also creates forms (with the sentinel `sessionID: "global"` — treat `sessionID` as a plain string here).

```json
// Form.Info
{
  "id": "frm_...",
  "sessionID": "ses_... | \"global\"",
  "title": "Connect to Sentry",
  "metadata": {"...": "..."},
  "fields": [ Field, ... ]                // non-empty
}
```

**Field union** (discriminant `type`; common fields `key`, `title?`, `description?`, `required?`, `when?`):

- `string`: `format?` (`email|uri|date|date-time`), `minLength?`, `maxLength?`, `pattern?`, `placeholder?`, `default?`, `options?: [{value,label,description?}]` (select), `custom?` (allow free text alongside options)
- `number` / `integer`: `minimum?`, `maximum?`, `default?`
- `boolean`: `default?`
- `multiselect`: `options` (required), `minItems?`, `maxItems?`, `custom?`, `default?: [string]`
- `external`: `{key, type:"external", url, title?, description?}` — open the URL (OAuth-style side flow); no answer value.

`when?: [{key, op:"eq"|"neq", value}]` — ALL conditions must hold against current answers for the field to be active (against a multiselect answer `eq` = "includes"); referenced keys must be defined earlier; unanswered reference ⇒ condition false; inactive fields are neither required nor answerable.

Answer/reply/state:

```json
// POST /api/session/{id}/form/{formID}/reply
{"answer": {"env": "production", "confirm": true, "tags": ["a","b"], "retries": 3}}
// GET  /api/session/{id}/form/{formID}/state
{"data": {"status": "pending"}} | {"status":"answered","answer":{...}} | {"status":"cancelled"}
```

| method path | notes |
|---|---|
| `GET /api/form/request?location[directory]=...` | All pending forms for a location (live: `[]`). |
| `GET /api/session/{id}/form` | Pending forms for a session. |
| `POST /api/session/{id}/form` | Create (client-side tooling): `{id?, title, metadata?, fields}`. |
| `GET .../form/{formID}` · `GET .../form/{formID}/state` | Read. |
| `POST .../form/{formID}/reply` | `{answer}` → 204. 400 `FormInvalidAnswerError`, 409 `FormAlreadySettledError`. |
| `POST .../form/{formID}/cancel` | → 204. |

Events: `form.created` `{form}`, `form.replied` `{id, sessionID, answer}`, `form.cancelled` `{id, sessionID}` (all ephemeral — poll pending lists after reconnect).

## 9. PTY (interactive terminals)

`Pty.Info`: `{id:"pty_...", title, command, args, cwd, status:"running"|"exited", pid, exitCode?}`.

| method path | notes |
|---|---|
| `GET /api/pty?location[directory]=...` | list (includes exited until removed) |
| `POST /api/pty?location[...]` | `{command?, args?, cwd?, title?, env?}` → `{location, data: Pty.Info}` (defaults to the user's shell, e.g. `/bin/bash -l`, cwd = location dir; live-verified) |
| `GET /api/pty/{ptyID}` / `PUT` / `DELETE` | get / update `{title?, size?:{rows,cols}}` (**resize is REST, not socket**) / kill+remove (204) |
| `POST /api/pty/{ptyID}/connect-token` | **requires header `x-opencode-ticket: 1`** (CSRF guard — 403 `ForbiddenError` without it, live-verified) → `{location, data: {ticket, expires_in: 60}}` — single-use, 60 s |
| `GET /api/pty/{ptyID}/connect` | **WebSocket upgrade.** Query: `ticket=<ticket>` (skips Basic auth) or standard auth (`?auth_token=` works for WS); `cursor=<n>` absolute byte offset to resume output from (`-1` = all retained history); `location[directory]=...` |

**Socket framing** (from the server implementation):

- Server→client: binary frames of **raw terminal output bytes**, except a **meta frame** whose first byte is `0x00` followed by JSON — currently `{"cursor": <n>}`, sent once after the replay backlog so the client knows its resume offset. Replay is chunked at 64 KB.
- Client→server: text frames (or UTF-8 binary) containing **raw input bytes** for the PTY (keystrokes/paste). No JSON wrapper, no resize over the socket.
- Close codes: `1000` process ended; `4404` "session not found" / "session exited".
- Reconnect flow: `POST connect-token` → `GET .../connect?ticket=...&cursor=<last>` → apply replayed bytes → watch for the `0x00` meta frame to update cursor.

**Persistent PTYs** (experimental, session-owned, survive server handoff): `GET/POST /api/experimental/session/{sessionID}/terminal`, `GET .../terminal/read?lines=N` → `{"data": {ptyID, title, cwd, foregroundProcess, screen:{text, cols, rows, cursor:{x,y}}} | null}` (screen-scrape without attaching); `GET/PUT/DELETE /api/experimental/persistent-pty/{ptyID}` (update takes `{attachmentID?, size:{cols,rows}}`), `GET .../snapshot` (info+text+checkpoint+cursor), `.../connect-token` + `.../connect` WebSocket (extra query params `role`, `attachment_id`, `takeover`, `input_protocol`), `POST /api/experimental/persistent-pty/shutdown|handoff`. `PersistentPty.Info` = `Pty.Info` + `{sessionID, foregroundProcess, size:{cols,rows}, output:{head,tail}}`. Events: `persistent-pty.added/removed`.

## 10. Shell (non-interactive commands)

`Shell.Info`: `{id:"sh_...", status:"running"|"exited"|"timeout"|"killed", command, cwd, shell, file, pid?, exit?, metadata, time:{started, completed?}}` (`file` = server path of the combined stdout/stderr capture).

| method path | notes |
|---|---|
| `GET /api/shell?location[...]` | running commands only |
| `POST /api/shell` | `{command, cwd?, timeout (ms, required; 0 = none), metadata?}` → Shell.Info |
| `GET /api/shell/{id}` | includes exit/status after exit |
| `GET /api/shell/{id}/output?cursor=&limit=` | `{output, cursor, size, truncated}` — page by absolute byte cursor; more available while `cursor < size` |
| `PATCH /api/shell/{id}/timeout` | `{timeout}` — replace timeout from now (0 clears) |
| `DELETE /api/shell/{id}` | terminate + drop retained output |

Events: `shell.created`/`shell.exited`/`shell.deleted`.

## 11. Filesystem, VCS, worktrees, workspace, catalog, config, MCP, integrations

### Filesystem (all take `location[directory]`)

- `GET /api/fs/read/<relative/path>` → raw file bytes (binary-safe; no JSON envelope).
- `GET /api/fs/list?path=<rel>` → `{location, data:[{path:"lib/api/", type:"directory"|"file"}]}` (dirs have trailing `/`; live-verified).
- `GET /api/fs/find?query=<q>&type=file|directory&limit=` → same Entry list, recursively ranked (fuzzy filename search).

### VCS

- `GET /api/vcs` → `{location, data:{branch:{current?, default?}}}`.
- `GET /api/vcs/status` → `{location, data:[{file, additions, deletions, status:"added"|"deleted"|"modified"}]}` — uncommitted changes.
- `GET /api/vcs/diff?mode=working|branch&context=?` → `{location, data:[{file, patch, additions, deletions, status}]}` (`working` = vs HEAD, `branch` = vs default-branch merge base).
- `GET /api/vcs/branches?search=&limit=` → `{location, data:["local", "origin/remote", ...]}` (live-verified).

### Worktrees & workspaces

- `GET /api/worktree/{projectID}` → `[{directory, strategy?}]`; `POST` `{strategy, from?, directory, name?, branch?}` → `{directory}` (runs the project's setup script); `DELETE` `{directory, force}` (400 `WorktreeError` with `data.forceRequired` when dirty); `POST .../refresh` reconciles. Errors here use the v1-style `{name:"WorktreeError", data:{message, forceRequired?}}` shape, not `_tag`.
- `POST /api/workspace` `{id?, provider}` → `{"data": workspaceID}` (idempotent per id+provider); `DELETE /api/workspace/{workspaceID}` → `{destroyed: bool}`. Workspaces are remote/sandbox scopes referenced by `location[workspace]`.

### Models / providers / agents / commands / skills

- `GET /api/model?location[...]` → `{location, data:[Model.Info...]}` — live: 349 models. `Model.Info`: `{id, modelID, providerID, family?, name, package, settings?, headers?, body?, capabilities:{tools, input:[...], output:[...]}, variants:[{id, settings?...}], time:{released}, cost:[{input,output,cache:{read,write},tier?}], status:"alpha|beta|deprecated|active", enabled, limit:{context, input?, output}, compatibility?}`. Model reference strings parse as `providerID/modelID[#variant]`.
- `GET /api/model/default` → `{location, data: Model.Info | undefined}` — resolves the effective default; use it when creating sessions without explicit model.
- `GET /api/provider` / `GET /api/provider/{id}` → `Provider.Info`: `{id, integrationID?, name, activation:"auto|enabled|disabled", package, settings?, headers?, body?}` (no keys are exposed).
- `GET /api/agent` / `GET /api/agent/{agentID}` → `Agent.Info`: `{id, name, model?, request:{settings,headers,body}, system?, description?, mode:"subagent|primary|all", hidden, color?, steps?, permissions:[{action,resource,effect}]}`. Live: `build, general, explore, compaction, title, summary, plan`.
- `GET /api/command` → `{data:[{name, description?}]}` (v2 slimmed this to name+description; run via `POST /api/session/{id}/command`).
- `GET /api/skill` → `{data:[{id, name, description?, slash?, autoinvoke?, location, content}]}` (live: 80 skills).
- `GET /api/reference` → configured reference dirs/repos.
- `POST /api/websearch` `{query, providerID?}` → `{location, data:{providerID, results:[{url,title?,content?,time:{published?}}]}}`; `GET /api/websearch/provider` lists providers.

### Config

`GET /api/config?location[...]` → **array** of config entries, lowest→highest priority (not one merged object):
`{type:"document", path?, info: Config.Info}` | `{type:"directory", path}` | `{type:"agents", path}` | `{type:"claude", path}`. `Config.Info` fields of client interest: `model` (selection string), `default_agent`, `share`, `username`, `permissions` (ruleset), `agents{}`, `commands{}`, `mcp{}`, `providers{}`, `compaction`, `skills[]`, `instructions[]`, `experimental{}`. There is **no config-write endpoint** in v2 (v1 had PATCH /config).

### MCP

- `GET /api/mcp` → `{location, data:[{name, status:{status:"connected"|"pending"|"disabled"|"failed"(+error)|"needs_auth"}, integrationID?}]}`.
- `PUT /api/mcp/{server}` `{config: {type:"local", command:[...], cwd?, environment?, disabled?, codemode?, timeout?} | {type:"remote", url, headers?, oauth?|false, disabled?, codemode?, timeout?}}` → 204 add/replace at runtime.
- `DELETE /api/mcp/{server}`, `POST /api/mcp/{server}/connect`, `POST /api/mcp/{server}/disconnect` → 204.
- `GET /api/mcp/resource` → `{location, data:{resources:[{server,name,uri,description?,mimeType?}], templates:[{server,name,uriTemplate,...}]}}`.

### Integrations & credentials (the new provider-auth model)

Provider auth is now generic "integrations" with connect methods; credentials are stored server-side and never returned.

- `GET /api/integration` / `GET /api/integration/{id}` → `Integration.Info`: `{id, name, methods:[Method], connections:[{type:"credential", id:"cred_...", label} | {type:"env", name}]}`. Method union: `{id, type:"oauth", label, form?}` | `{id, type:"command", label, command:[...]}` | `{type:"key", label?, form?}` | `{type:"env", names:[...]}` (the `form` is a Form.Fields array for extra config).
- Key connect: `POST /api/integration/{id}/connect/key` `{key, answer?, label?}` → 204.
- OAuth connect: `POST .../connect/oauth` `{methodID, answer?, label?}` → `{location, data:{attemptID, url, instructions, mode:"auto"|"code", time:{created,expires}}}` → open `url`; poll `GET .../connect/oauth/{attemptID}` → `{status:"pending"|"complete"|"failed"(+message)|"expired"}`; for `mode:"code"` finish with `POST .../connect/oauth/{attemptID}/complete` `{code}`; `DELETE` cancels.
- Command connect (e.g. CLI login): `POST .../connect/command` `{methodID, label?}` → `{attemptID, time}`; poll `GET .../connect/command/{attemptID}` (status + optional message); `DELETE` cancels.
- Credentials: `PATCH /api/credential/{credID}` `{label}`, `POST /api/credential/{credID}/activate`, `DELETE /api/credential/{credID}` (all 204). Events: `credential.updated`, `credential.switched`.
- `POST /api/experimental/integration/wellknown` `{url}` registers a wellknown integration source.

### Misc

- `GET /api/plugin` → plugin list `{id, source, status:"active"|"failed"(+error), tui}`.
- `GET /api/experimental/migration/v1` → `{status:"required"|"completed"}` | `{status:"running", progress:{label, numerator?, denominator?}}` | `{status:"error", error}` — v1→v2 history migration progress (show a blocking screen while `running`).
- `GET /api/debug/location` → `[Location.Ref]` loaded by the server; `DELETE /api/debug/location?location[...]` evicts cached services.

## 12. Error catalog

All (except `WorktreeError`, above) serialize as `{"_tag": "<Name>", "message": "...", ...extras}` with these statuses:

| status | `_tag` (extras) |
|---|---|
| 400 | `InvalidRequestError` (`kind?`, `field?`) · `InvalidCursorError` · `FormInvalidAnswerError` (`id`) |
| 401 | `UnauthorizedError` |
| 403 | `ForbiddenError` |
| 404 | `SessionNotFoundError` (`sessionID`) · `MessageNotFoundError` (`sessionID`,`messageID`) · `ProviderNotFoundError` (`providerID`) · `AgentNotFoundError` (`agentID`) · `SkillNotFoundError` (`skill`) · `CommandNotFoundError` (`command`) · `McpServerNotFoundError` (`server`) · `PermissionNotFoundError` (`requestID`) · `FormNotFoundError` (`id`) · `PtyNotFoundError` (`ptyID`) · `ShellNotFoundError` (`id`) · `ProjectNotFoundError` (`projectID`) |
| 409 | `ConflictError` (`resource?`) · `SessionBusyError` (`sessionID`) · `FormAlreadySettledError` (`id`) |
| 413 | `InstructionEntryValueTooLargeError` (`actualBytes`,`maxBytes`) |
| 500 | `UnknownError` (`ref?`) · `CommandExecutionError` (`command`) |
| 503 | `ServiceUnavailableError` (`service?`) — also the boot-time `{"code":"service_starting"...}` bodies from §1 |

In-session model/provider failures are **not** HTTP errors; they arrive as `Session.StructuredError` `{type, message, status?}` inside `session.error`-carrying events (`execution.failed`, `step.failed`, `tool.failed`, assistant `error` field, `retry.scheduled`).

**Pagination convention** (sessions & messages): `{"data":[...], "cursor":{"previous"?: "<opaque>", "next"?: "<opaque>"}}`; items keep the requested order across pages; pass the cursor back verbatim as `?cursor=`; a missing key means no more pages in that direction.

## 13. v1 → v2: what will bite a v1 client

1. **Every path moved under `/api`** and most v1 paths don't exist (`/session/:id/message` → `/api/session/:id/message`, `/event` → `/api/event`, `/config/providers` → `/api/provider` + `/api/model`, `/app` → gone).
2. **Auth is mandatory** — v1 servers were open by default; v2 401s everything without Basic `opencode:<password>`.
3. **The event model is completely different**: v1's ~45 coarse events (`message.updated`, `message.part.updated`, `message.part.delta`…) are replaced by ~91 fine-grained event-sourced types. Nothing sends you a full message object on the stream anymore — you either project from `session.text/reasoning/tool.*` events or refetch messages. Envelope changed too (`properties` → `data`, plus `id/created/location/durable`).
4. **Parts are gone.** v1 `Message + Part[]` (12 part types with `sessionID/messageID` on each) became a 10-variant message union; assistant content is an inline array of 3 content kinds; step/snapshot/patch/agent parts have no direct equivalent (steps are events now).
5. **Tool results changed shape**: v1 `ToolStateCompleted.output: string` (+`title`, `attachments`) → v2 `content: [{type:"text"|"file",...}]`, no title; `pending` status → `streaming` with raw input text; time fields moved (`time.created/ran/completed` on the content item).
6. **Prompt no longer takes model/agent/parts.** v1 `{parts:[{type:"text"...},{type:"file", url:"data:..."}], model:{providerID,modelID}, agent, system, tools}` → v2 `{text, files:[{uri}], agents, skills, delivery, resume}`; model/agent are switched per-session via dedicated endpoints; attachments are `data:`/`file:` **URIs** with a 20 MB cap. The response is an inbox receipt, not the assistant message.
7. **Prompting is queue-based and async**: responses come via events; `delivery: steer|queue` and the inbox endpoints are new concepts; `POST .../wait` replaces v1's long-running prompt response. Default is **steer** (v1 behavior was effectively queue-and-block).
8. **Sessions**: no `version/share/slug` fields; `directory` → `location.{directory,workspaceID}`; times are `created/updated/idle/viewed/archived`; new `outcome`, `metadata`, `fork`; list is cursor-paginated (`{data, cursor}` instead of a bare array) with `directory`/`project`+`subpath` filters instead of v1's `?directory=`.
9. **Questions → Forms**: v1 `question.asked` with option lists became typed multi-field forms (`string/number/integer/boolean/multiselect/external`, `when` conditions) with reply/cancel/state endpoints and different events.
10. **Permissions**: v1 `{permission (single string), patterns, always}` + reply body `{reply}` → v2 `{action, resources[], save[], source, message?}` at `/api/session/{id}/permission/{requestID}/reply` with optional reject `message`; plus location-wide pending list and saved-permission management.
11. **Timestamps/enums**: all times are epoch-ms numbers everywhere (v1 mixed seconds objects); `finish` reasons normalized (`tool-calls`, `content-filter`); cost/tokens moved from message-level only to both step events and session totals.
12. **Errors**: v1 `{data:{...}, name}` / `BadRequestError {errors[], success:false}` → `_tag`-discriminated objects with typed extras and strict statuses.
13. **SSE mechanics**: heartbeats are comment lines; the stream is explicitly volatile; durable replay exists only per-session via `/api/experimental/session/{id}/log?after&follow` — a client that assumed it could miss nothing on `/event` must add reconcile-on-reconnect.
14. **Config is read-only** and returns a priority-ordered entry list, not a merged document; provider credentials moved to the integrations/credentials flow (v1 `PUT /auth/{providerID}` is gone).
15. **PTY**: connect-token now demands the `x-opencode-ticket: 1` header + allowed Origin; resize is via `PUT /api/pty/{id}`; the `0x00` meta frame and byte-cursor resume are new; and there's a parallel experimental persistent-PTY API.
16. **Version drift is real**: the beta server changes weekly (17823 → 18600 added 15 paths, `session.step.streamed`, `session.message.content.updated`, command-payload changes, interrupt response body). Pin client expectations to `GET /api/health`.version and tolerate unknown event types/fields.
