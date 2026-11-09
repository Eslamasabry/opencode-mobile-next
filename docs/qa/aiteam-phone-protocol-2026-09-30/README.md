# Phone OC1 protocol slice — 2026-09-30

Finish line: the native driver creates, prompts, observes and aborts sessions on one authenticated loopback OC1 1.18.32 server, enforces planner/checker read-only permissions, and reports supported protocol evidence and missing admission authority honestly.

Non-goal: OC2, spawning servers or ACP agents, UI, provider configuration, a claim of atomic chat priority, or enabling execution without the coordinator's boundary and protocol proof.

Ownership: `engine/phone/src/opencode.rs` including embedded tests, and this README. Cargo, lifecycle, store and UI belong to other slices. Branch `build/aiteam-phone-protocol`.

## Evidence and activation blocker

Primary source was fetched on 2026-09-30 from upstream **v1.18.32**, resolved by GitHub tree API to **545f51d26cc39a907d2867492d498d9607ea5fa4**. Source artifacts are under `/home/owner/Storage/Code/aiteam-protocol-source-1.18.32/`; no download or build artifact was placed in `/tmp`. The revision-linked source below is reproducible without that local artifact directory.

- [Pinned OpenAPI](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/sdk/openapi.json) documents `GET /global/health`, authenticated `GET /doc`, session create/update/status/messages/prompt_async/abort, and pending question/permission list. The runtime driver checks exact health version and current `/doc` operation IDs and callable role/model/permission fields. A local Python source inspection confirmed those ten operation IDs match; this is source evidence, **not a runtime/test pass**.
- [`/doc` route](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/opencode/src/server/routes/instance/httpapi/server.ts#L183-L192) publishes `OpenApi.fromApi(PublicApi)` through auth middleware.
- [Session HTTP handlers](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/opencode/src/server/routes/instance/httpapi/handlers/session.ts) confirm `?directory=` instance scope, status returning `SessionStatus.list`, session permission update appending merged rules, async prompt 204 acceptance, durable message refetch and abort boolean.
- [Prompt fields and permission handling](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/opencode/src/session/prompt.ts#L1492-L1520): exact fields are `model: {providerID, modelID}`, `agent`, `system`, `parts`. Engine roles are conveyed with `system`; `agent: "build"` selects the built-in callable agent. Planner/checker names are not invented server agents. The deprecated `tools` field is deliberately omitted because [it replaces the session permission policy](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/opencode/src/session/prompt.ts#L1060-L1066).
- [Permission evaluation](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/opencode/src/permission/index.ts#L28-L36) uses last matching rule. [Tool visibility](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/opencode/src/permission/index.ts#L204-L220) disables wildcard-denied tools, mapping write/apply_patch to edit. Each role appends wildcard deny, then only allowlisted permissions. Planner/checker allow read/glob/grep; bash/edit/task/custom MCP tools remain denied. Worker additionally allows edit/bash. Permission enforcement is necessary but does not replace the OS filesystem boundary. Built-in MCP resource readers map to read and can read resources; arbitrary MCP/custom tools remain denied.
- [Final model-request tool filtering](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/opencode/src/session/llm/request.ts#L208-L214) applies `Permission.disabled` after resolving all tools, including MCP/custom tools. Read-only enforcement therefore removes denied tools from the callable model request, rather than relying on the role's prose or pending approvals.
- [Status implementation](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/opencode/src/session/status.ts#L25-L46) stores a map in **directory-specific InstanceState**, defaults absent entries to idle and deletes entries when idle. The driver only interprets map absence as idle after checking session existence/scope and fetching fresh status; malformed/failed reads are errors.

**Execution activation is blocked: `global_status_unavailable`.** `/session/status` cannot prove chat idleness across instances. The [global session listing handler](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/opencode/src/server/routes/instance/httpapi/handlers/experimental.ts#L139-L156) and [SQL implementation](https://github.com/anomalyco/opencode/blob/545f51d26cc39a907d2867492d498d9607ea5fa4/packages/opencode/src/session/session.ts#L540-L575) do not fix this:

1. Global inventory returns stored session creation directories. `Session.get(id)` is SQL by ID without a directory restriction, and prompt can execute that session in another requested directory instance. Inventory is not a complete enumeration of active execution instances.
2. Pagination orders by updated time and ID but the next cursor filters strictly `time_updated < cursor`. Equal-time rows at a page boundary can be skipped. A single bounded page with no next cursor avoids that pagination gap, but still cannot prove all active directory instances.
3. Global SSE is an event stream without a complete initial active-instance/status checkpoint. A disconnect, reconnect, or unobserved earlier busy event cannot establish idle. No global process-status snapshot or active-instance enumeration contract appears in the pinned OpenAPI.

`chat_admission()` therefore returns a safe error, never a fabricated idle result. A supported complete status checkpoint/registry would be required to remove this blocker. The coordinator must gate every prompt with admission and the independent boundary proof. Even a future complete observation would provide an admission observation, not atomic scheduling or provider priority.

## Frozen output contract

`verify()` returns only safe evidence metadata:

```json
{"healthy":true,"version":"1.18.32","sourceRevision":"545f51d26cc39a907d2867492d498d9607ea5fa4","pinnedVersion":true,"openapiVerified":true,"capabilities":{"sessionDriver":true,"readOnlyPermissions":true,"chatAdmission":false,"execution":false,"oc2":false},"blockers":["global_status_unavailable"]}
```

An unsupported health version, failed authentication, malformed/missing current schema, or transport error is `ProtocolError`, containing only a static code. Evidence metadata does not claim inference, boundary, or live prompt verification.

`create_session(directory,title)` creates a session with wildcard-denied tools and returns its server-authored `ses...` ID. `prompt()` appends the explicit role permission policy and confirms the response ends in those rules before sending the async prompt. Model strings are `provider/model` (split once, preserving further slashes in the model ID). Planner and checker reject `readonly=false`. Prompt acceptance returns `{"state":"running","sessionId":"ses...","accepted":true}`. Mutating transport/body ambiguity returns `transport_uncertain`; callers must reconcile, never automatically retry the prompt or create.

`observe()` returns:

```json
{"state":"running|completed|blocked|failed|unknown","text":"latest assistant visible text","usage":{"cost":0.0,"tokens":{"total":0,"input":0,"output":0,"reasoning":0,"cache":{"read":0,"write":0}}}}
```

The usage example shows optional shapes, **not default values**: unknown fields are omitted. Cost and each token field are summed across all assistant messages only when every assistant message has a known, nonnegative finite value for that field. Known zero remains zero. `total` is never invented from a formula. No raw pending request, tool input/output, provider error, credential, or server body reaches the observation.

`completed` requires the latest assistant message to belong to the latest user turn, have finish `stop`, positive completed timestamp and visible text, contain no tool calls, have no assistant error or current pending permission/question, and have idle before/after status. Content-filter/length/error finishes are failed; unknown or tool-call finishes cannot complete. Empty/new sessions and stale earlier final replies remain unknown. Pending permission/question is blocked; busy/retry is running. The coordinator parses planner/checker JSON from `text` and applies its schema; transport completion never substitutes for that validation.

Reconnect always runs the same full HTTP refetch. There is no SSE replay/closure completion path. `abort()` requires the pinned boolean success response.

The client accepts numeric loopback HTTP only, `opencode` Basic user and a nonempty password; rejects redirects, proxy use, URL credentials, alternate paths, fragments, remote hosts and DNS names. Credentials are never Debug-formatted, persisted, or wrapped in errors. `/config/providers` is never requested. JSON reads are bounded to 8 MiB and requests to ten seconds.

## Deferred verification

Owner deferred tests: embedded tests were written but **not run**. Coverage cases include normal/latest-turn completion, busy/pending/error/tool-call blockers, incomplete finishes, known-zero/unknown usage, exact role/model fields and wildcard policy, invalid endpoint/path, and malformed status/pending data. No live prompt server was started, no model credential was inspected, and no provider call was made.

`rustfmt --edition 2021 engine/phone/src/opencode.rs` completed. Source-only operation-ID inspection completed. Cargo compile, Rust tests, runtime current-`/doc` verification, pinned inference, phone boundary proof and integration checks remain deferred/unverified. Implementation is present; execution and OC2 remain unavailable; nothing is pushed, deployed, signed, or released.
