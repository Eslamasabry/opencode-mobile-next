# Backend backlog

Read-only reviews of the Flutter client's API adapters, domain and persisted state. IDs stay stable across review and implementation cycles. Status records implementation ownership; it is not a claim that an item passed verification.

## BE-001 — Keep queued drafts when persistence fails

- **Status:** Implemented — cycle 2026-09-05-01
- **Priority / confidence:** High / high
- **Evidence:** `lib/state/connection.dart`, `queuePrompt` and `removeQueuedPrompt`; `lib/ui/screens/chat_screen.dart`, `_queueDraft`. Queue writes ignored `OfflineQueueStore.save` returning false, while the composer treated the enqueue as successful. Failed removal could also resurrect an edited or discarded prompt after restart.
- **User impact:** Unsent text can disappear, or a discarded copy can later send.
- **Implementation:** Persist the new queue before committing it in memory. Report a storage-specific failure and retain composer content. Preserve the existing size-limit message for actual size rejections.
- **Minimal verification:** Existing `test/offline_queue_test.dart` and storage-failure fixtures in `test/profile_deletion_test.dart`; exercise enqueue/removal with a refused write.

## BE-002 — Pin an offline flush to its originating server

- **Status:** Implemented — cycle 2026-09-05-01
- **Priority / confidence:** High / high
- **Evidence:** `lib/state/connection.dart`, `flushOfflineQueue`. The loop captured one profile ID, awaited `prepareActionTransport`, then used whichever gateway came back without rechecking the profile or whether the queued entry had been removed.
- **User impact:** Switching servers while reconnecting can send a queued prompt to the wrong server; removing a prompt during resume can fail to prevent its send.
- **Implementation:** Recheck profile and transport scope after the await and before sending. Stop when scope changes. Skip entries no longer present in the queue.
- **Minimal verification:** Existing `test/offline_queue_test.dart` and `test/app_lifecycle_test.dart`; one deferred transport case for profile change and one for removed entry.

## BE-003 — Search all projects in v2 All chats

- **Status:** Implemented — cycle 2026-09-05-01
- **Priority / confidence:** High / high
- **Evidence:** `lib/api2/gateway_operations.dart`, `listGlobalSessions`, passed `directory: null`; `lib/api2/client.dart`, `sessions`, substituted the pinned directory and workspace for null. `docs/opencode2-protocol-notes.md` section 4 and captured `/api/session` contract confirm that omitted directory/project/workspace filters allow global listing.
- **User impact:** All chats silently omits conversations outside the currently selected location.
- **Implementation:** Add an explicit unscoped sessions option and use it in global search while retaining location filters in ordinary session lists.
- **Minimal verification:** Existing `test/api2_client_test.dart` and `test/global_sessions_screen_test.dart`; inspect the outgoing global query and the unchanged scoped query.

## BE-004 — Resume terminal output using byte offsets

- **Status:** Implemented — cycle 2026-09-05-01
- **Priority / confidence:** High / high
- **Evidence:** `lib/api2/gateway_operations.dart`, `_Api2TerminalChannel._decodeFrame`, and `lib/api/product_repository.dart`, `_IoTerminalChannel._decodeFrame`, incremented the resume cursor with UTF-16 `text.length`. `docs/opencode2-protocol-notes.md` section 9 specifies an absolute byte offset.
- **User impact:** Arabic, emoji and other multibyte output can replay or become corrupted after reconnect.
- **Implementation:** Count incoming binary bytes or UTF-8 encoded string bytes, keeping server metadata cursor resets authoritative.
- **Minimal verification:** Existing terminal transport checks in `test/product_repository_test.dart` and `test/terminal_accessibility_test.dart`; include a multibyte output frame and its resulting resume cursor.

## BE-005 — Carry the v2 global-search pagination cursor

- **Status:** Implemented — cycle 2026-09-05-04 (global finder; scoped session inventory remains separate)
- **Priority / confidence:** Medium / high
- **Evidence:** `lib/api2/gateway_operations.dart`, `listGlobalSessions`, returns an empty list for every non-null integer cursor. `lib/ui/screens/global_sessions_screen.dart`, `_cursor`, derives timestamps. Captured `GET /api/session` and `SessionsResponse` explicitly support opaque `cursor.next` and `cursor.previous` strings.
- **User impact:** Global search cannot reach results beyond the first 50 conversations.
- **Implementation:** Return a page object carrying results and the opaque continuation cursor; let the v1 adapter retain its timestamp-based cursor. Forward v2 tokens verbatim and alone in the next request. Do not guess a timestamp-to-token conversion.
- **Implementation plan:** See the shared BE-005 / BE-010 pagination plan below. The pinned v1 global endpoint already returns its numeric continuation in `x-next-cursor`; preserve that response header instead of deriving it in the UI.
- **Minimal verification:** Existing `test/api2_client_test.dart` cursor fixture and `test/global_sessions_screen_test.dart` load-more checks; traverse two pages for each server flavor.

## BE-006 — Handle every concurrent catalog request immediately

- **Status:** Implemented — cycle 2026-09-05-02
- **Priority / confidence:** High / high
- **Evidence:** `lib/api2/gateway.dart`, `providers`; `lib/api2/gateway_operations.dart`, `loadCatalog`; and `lib/api/product_repository.dart`, `SdkProductRepository.loadCatalog`, start several requests, then attach error handlers by awaiting them one at a time. V2 `loadVersionControlHealth` uses the same delayed handling for its status request.
- **User impact:** If a later request fails while an earlier request is still pending, its error escapes as an unhandled asynchronous error instead of reaching the existing recoverable catalog or project-health state.
- **Implementation:** Await a typed record with `.wait` or use `Future.wait` so every request has a handler immediately. For intentionally optional VCS responses, attach each fallback before awaiting the group. Preserve typed `ApiException`/`ProductException` mapping when unwrapping record-wait errors.
- **Minimal verification:** Existing catalog fixture in `test/product_repository_test.dart`; one delayed first request with an immediately failing later request, asserting one handled failure and no uncaught asynchronous error. Existing project-health checks cover the optional fallback behavior.

## BE-007 — Keep OAuth attempt context through code confirmation

- **Status:** Implemented — cycle 2026-09-05-02
- **Priority / confidence:** High / high
- **Evidence:** `lib/api2/gateway_operations.dart`, `completeIntegrationOAuth`, removes `_oauthAttemptIntegration[attemptID]` immediately after POST success. `lib/ui/screens/library/integrations_screen.dart`, `_enterOAuthCode`, immediately calls `integrationOAuthStatus`, whose `_attemptIntegration` now throws that the attempt is no longer tracked. The captured OAuth complete route returns 204 and stores the credential; the status read is part of the existing UI flow.
- **User impact:** A successful code-based provider connection is shown as a failed/abandoned sign-in and does not finish refreshing the model catalog.
- **Implementation:** Keep the attempt's integration context through its required status read. If retiring it at a terminal status, cache that terminal result long enough for the existing completion/retry path. Cancel should still retire pending state. Do not alter the v1 completion contract.
- **Minimal verification:** Existing code-OAuth widget scenario in `test/library_integrations_test.dart`; route a start → complete (204) → status (complete) sequence through the real v2 adapter and assert catalog refresh remains reachable.

## BE-008 — Keep provider and MCP actions in the displayed location

- **Status:** Implemented — cycle 2026-09-05-02
- **Priority / confidence:** High / high
- **Evidence:** `lib/api2/gateway_operations.dart`, `listIntegrations` and `listMcpServers`, send `_loc()`, but `connectIntegrationKey`, OAuth start/status/complete/cancel, and MCP add/connect/disconnect omit location queries. The captured contract declares the same optional deep-object location on each of these routes.
- **User impact:** A project-specific provider or MCP server can be listed correctly but fail to connect, disconnect, or authenticate because the action is resolved against the server's default location.
- **Implementation:** Forward the pinned directory/workspace on the affected location-scoped mutations and reads. Snapshot the starting location with OAuth attempt context and reuse that snapshot for later attempt actions. Credential-ID deletion remains governed by its own global route contract.
- **Minimal verification:** Inspect outgoing requests from a v2 gateway pinned to both a directory and a workspace; verify list and actions carry the same scope. Reuse the MCP and integration fixture shapes in `test/product_repository_test.dart`; no native UI run is needed.

## BE-009 — Encode MCP timeout in the v2 wire shape

- **Status:** Implemented — cycle 2026-09-05-02
- **Priority / confidence:** High / high
- **Evidence:** `lib/domain/server_gateway.dart`, `McpServerDraft.toConfigJson`, emits a scalar `timeout: timeoutMs`. `lib/api2/gateway_operations.dart`, `addMcpServer`, forwards that map unchanged. Captured `Mcp.LocalConfigEncoded` and `Mcp.RemoteConfigEncoded` instead define `timeout` as an object with optional positive integer `startup`, `catalog`, and `execution` fields. Existing scalar assertions in `test/product_repository_test.dart` belong to the v1 adapter.
- **User impact:** Entering any optional timeout in Add MCP server makes the v2 request invalid, even though the form validates it successfully.
- **Implementation:** Translate the draft at the v2 adapter boundary. Map the existing generic timeout to the named startup/catalog/execution durations consistently, or expose separate fields if different values are desired. Keep omitted timeout omitted and retain the v1 scalar representation.
- **Minimal verification:** One local and one remote v2 MCP request with a timeout must match the captured object schema; existing v1 scalar expectations must remain unchanged.

## BE-010 — Keep the newest messages reachable in long v2 sessions

- **Status:** Message history implemented — cycle 2026-09-05-05; scoped v2 inventory implemented — cycle 2026-09-05-06. Pinned v1 compatibility limits remain documented below.
- **Priority / confidence:** High / high
- **Evidence:** `lib/api2/gateway.dart`, `messages`, starts with `order: 'asc'`, follows at most `maxPages = 20` pages of `pageLimit = 200`, and returns without exposing the remaining cursor. Captured `GET /api/session/{sessionID}/message` explicitly supports newest-first `desc` and opaque continuation tokens. `SessionGateway.messages` currently returns only a list. The gateway's own pagination TODO acknowledges this interface gap.
- **User impact:** Above 4,000 server message records, opening or refreshing a session can omit its newest response. Search, context estimates, and Markdown export also operate on an incomplete history without telling the user. Raising the cap only moves the failure point.
- **Implementation:** Hydrate a bounded newest-first page, map it into chronological display order, and retain the opaque older-history cursor in a page result. Add incremental older-history loading with deduplication, scroll preservation, and a visible loading/error/end state. Preserve the v1 adapter's actual pagination contract. Treat complete JSON export separately under #48; loaded Markdown must not imply a complete backup. Apply the same explicit continuation model to the bounded session inventory and BE-005.
- **Implementation plan:** See below. V1 message `before` is also an opaque server token, returned in response headers; it is not a message ID or timestamp.
- **Minimal verification:** One fixture extending beyond the old cap must still show the latest reply on first open; a second page must prepend older records without duplicate or displaced live messages. Reuse the existing `api2_client_test.dart`, gateway/mapper fixtures, and chat hydration checks rather than adding a broad suite.

## BE-011 — Match MCP setup scope to the v2 contract

- **Status:** Ready
- **Priority / confidence:** Medium / high for scope; persistence is unverified
- **Evidence:** `lib/ui/screens/mcp_setup_screen.dart`, `build`, always offers `This project` / `All projects` and says it writes project/global configuration. `Api2OperationsGateway.addMcpServer` ignores `McpConfigScope`; captured `PUT /api/mcp/{server}` accepts a location query and `{config}`, with no global/project write selector. `integrations_screen.dart`, `_mcpSection`, repeats the all-project promise. This is separate from BE-008's correct location routing.
- **User impact:** Choosing All projects on v2 does not perform the global configuration operation the form describes.
- **Implementation:** Distinguish persistent project/global configuration writes from location-scoped MCP add in the capability model. Keep the existing v1 scope selector; show the actual selected location and supported operation on v2. Do not describe the v2 route as ephemeral: its contract says add at runtime, while the gateway comment and port matrix claim persistence. Confirm restart behavior from server implementation or a single live add/restart check before keeping or changing the restart promise.
- **Minimal verification:** The same form renders the project/global selector for v1 and the current location for v2; outgoing requests match that displayed scope. No new broad test pass is needed.

## V1 release requirements — current review, 2026-09-05

This is a source/contract inventory, not a release sign-off. Open GitHub issues were refreshed on this date. An open issue is not proof that its implementation is absent; current source determines the status below. BE-006–009 are already assigned to root. Existing issue IDs remain the source of truth for overlapping feature work.

### Required product parity work

| Priority | Requirement and current evidence | Concrete completion requirement |
|---|---|---|
| High | **Reach every conversation and recent reply:** global finder, message history, and scoped v2 inventory now preserve continuation. | Verify the final candidate against live servers. Retain documented v1 endpoint limitations; client pagination does not establish full release coverage. |
| High | **Server-authoritative model, variant, and agent:** [#53](https://github.com/Eslamasabry/opencode-mobile-next/issues/53). `mapApi2Session` reads model/agent but drops model variant; `ConnectionController.modelForSession` uses local choices/profile defaults; `_applySelection` writes them before each send. Selected events are parsed in `events.dart` but not forwarded by `Api2EventAdapter`. `Api2Client.createSession` supports defaults; the gateway does not pass them. | Hydrate and merge matching-session selections on connect/reconnect and events, including variant. Persist intentional picker changes through the v2 selection APIs; do not overwrite another client's choice on ordinary sends. Keep v1 per-prompt selections and new-session fallback behavior. |
| High | **Truthful revert workflow:** [#55](https://github.com/Eslamasabry/opencode-mobile-next/issues/55). The current v2 adapter exposes stage/clear through the one-shot v1 interface; commit is absent and the domain retains only `reverted: bool`. Revert events are parsed but not forwarded. | Add stage, preview, explicit commit, and clear, with persistent staged state and busy/conflict recovery. Confirm actual file effects on the pinned server. Preserve the existing undoable v1 workflow. |
| High | **Provider setup and MCP correctness:** BE-006–009, then BE-011. Key/OAuth setup exists; this is correction of shipped flows, not a missing integrations screen. | Complete root's scoped requests/OAuth/timeout work, then correct scope presentation. A failed/retried setup must leave a usable recoverable state. |
| Medium | **Complete backup and transfer:** [#48](https://github.com/Eslamasabry/opencode-mobile-next/issues/48) is sanitized JSON export. `_exportTranscript` currently writes only rendered Markdown. The v2 export/import routes exist; neither is exposed by the product gateway. | Add server-generated JSON with `sanitize=true` beside Markdown. Keep import a separate feature: validate the transfer payload, select destination location, handle the declared 409 conflict, and retain the original on failure. Import was excluded from #48, not from the broader parity goal. Captured v1 has no equivalent transfer routes. |
| Medium | **Cross-client unread completion:** [#57](https://github.com/Eslamasabry/opencode-mobile-next/issues/57). `Api2SessionTime` parses idle/viewed, but `mapApi2Session` loses them; viewed events have no adapter consumer and `/view` has no client workflow. | Preserve idle/viewed watermarks, show unread completions, acknowledge only foreground viewing, reconcile events, and implement the issue's privacy opt-out/local fallback. Never mark read from background refresh. |
| Medium | **Agent notes:** [#58](https://github.com/Eslamasabry/opencode-mobile-next/issues/58). V2 instruction-entry endpoints exist; no product gateway or screen uses them. | Read/save/remove only `mobile.note`, handle the server's size rejection, and reconcile instruction updates. Keep server-owned keys out of the editor. V1 has no corresponding route in the captured contract. |
| Medium | **Aggregate usage:** [#54](https://github.com/Eslamasabry/opencode-mobile-next/issues/54). Current session totals are implemented; `/api/session/stats` and a usage screen are absent. | Add the agreed range/project/timezone controls and tool reliability fields with explicit empty/error states. Preserve the existing session view. Gate aggregate data on actual support, not a fabricated v1 sum. |

### Remaining v2-native surface that must receive an explicit product disposition

These routes are present in `contracts/opencode2-openapi-beta-18600.json`; absence from the current UI must not be described as upstream lack of support. They remain part of the parity inventory until implemented or explicitly recorded as outside the app's user workflows.

- **Integration command sign-in and individual credential management:** `integrations_screen.dart`, `_connectIntegration`, filters methods to key/OAuth. V2 has command start/status/cancel plus credential update/activate. Add a supported sign-in route for command-only methods and an account-level switch/rename/remove flow; do not disconnect every credential to switch accounts. Resume/cancel pending attempts truthfully after navigation or app restart. The v1 compatibility API must be checked separately for each operation.
- **Context, skills, and web search:** the existing context screen estimates a breakdown from loaded messages; v2 `/session/{id}/context` returns the actual context message list. Skills are catalog previews, while v2 also provides `/session/{id}/skill`. `/websearch/provider` and `/websearch` have no app workflow. Complete these as targeted context inspection, skill activation, and reviewable result attachment workflows if included in the promised coding-client parity; do not relabel catalog viewing as activation.
- **MCP removal, branch discovery, and workspace creation:** v2 exposes `DELETE /mcp/{server}`, `/vcs/branches`, and workspace create/destroy. MCP currently only adds/connects/disconnects. `managedWorkspaces: false` intentionally hides the v1 manager because v2 has no corresponding inventory/adapters/warp API; create/destroy support still exists. Do not turn on the entire v1 workspace surface with one flag. Add only workflows the v2 discovery model can support and handle unavailable providers.
- **Persistent session terminals:** ordinary PTY and managed shell output are implemented, but the experimental per-session terminal/snapshot/handoff APIs are not. They are distinct from current Running work and must not be advertised as present or silently wired to ephemeral shell IDs.
- **Other protocol operations:** session environment, message content update, one-shot generate, plugin/debug/migration inspection, experimental durable log, permission/form creation, and worktree refresh need an explicit user-workflow decision. Server-internal controls are not automatically useful UI features. A raw endpoint count cannot establish product parity, and refresh must not be treated as destructive reset without semantic evidence.

### Implemented and unsupported are different states

- **Implemented on both adapters:** core session list/create/rename/delete, prompt/stream/stop, attachments, forks/compaction, files, project/worktree management within each protocol's limits, working-tree/branch review, ordinary terminals, catalogs/commands/skills/references, key/OAuth integrations, MCP list/add/connect/disconnect, and saved permission management. This describes code presence; the corrections and lossy mappings above remain open.
- **Implemented v2 additions:** structured forms, steer/queue/cancel inbox, server usage/retry events, scoped global search, managed Running work output/timeout/stop, and session background requests. Do not reopen these as absent merely because the August port plan lists them under future work. V1 background subagent support remains runtime-gated; managed shells are a separate v2 capability.
- **No direct v2 counterpart in the captured contract:** share/archive/todos/message deletion, workspace warp/steal/console organizations, MCP OAuth endpoints, workspace symbol/text search, LSP/formatter status, tool inventory, shell discovery/default selection, server upgrade, diagnostics upload, Git initialization, provider-runtime refresh, and destructive worktree reset. Preserve capability gating and explain meaningful limitations. Forms replace legacy questions. V2 working-tree diff is not a session-only diff, and v2 move does not expose v1's move-changes toggle.
- **V1-specific limitations:** the captured v1 contract has no v2 inbox/forms, managed-shell control, JSON transfer, instruction entries, aggregate stats, or viewed-state endpoints. Shared UI should use explicit capabilities and retain v1's supported alternatives. Do not call v1's `/api/*` compatibility surface equivalent to the complete v2 protocol.

### Release evidence and documentation requirements

1. **Choose the actual release identity before publishing.** Current `pubspec.yaml` and `docs/release-alpha-notes.md` target `v1.0.34+35`; GitHub's latest published release is still [v1.0.33+34](https://github.com/Eslamasabry/opencode-mobile-next/releases/tag/v1.0.33%2B34), a prerelease. A stable release needs deliberate stable wording/channel selection and matching notes extraction; `.github/workflows/android-release.yml` currently creates a draft prerelease and expects an `Alpha` heading. Do not reset the existing version sequence to `1.0.0` or reuse a published tag.
2. **Use the established signed-artifact path.** `android-release.yml` already verifies tag/pubspec agreement, the APK version, public signer, source commit, and SHA-256 before attaching it to a draft. [#12](https://github.com/Eslamasabry/opencode-mobile-next/issues/12) is stale as a claim that the workflow is absent. Signing-secret availability and a successful run for the final release commit were not checked here. The expected new signer is `842284B27AA297FB74CF831779FD16498517E1BC2104451459FEC2EA7AC11D1C`; use release artifacts, not CI test-signed APKs.
3. **Upgrade instructions corrected in cycle 02.** The release draft now consistently explains signer mismatch and no longer promises an in-place upgrade over all previews. README records that the old v1.0.33 signer differs and one uninstall is needed, deleting local profiles/drafts/queue. Give one consistent migration instruction and preserve the exact signer and package identity in the artifact evidence.
4. **Record focused real-release evidence once.** The checked-in release preamble requires final-commit quality results and physical-device smoke evidence. Existing [#8](https://github.com/Eslamasabry/opencode-mobile-next/issues/8), [#9](https://github.com/Eslamasabry/opencode-mobile-next/issues/9), and [#10](https://github.com/Eslamasabry/opencode-mobile-next/issues/10) track Android/lifecycle/permission scenarios. Cover connection, long reply, permissions/forms, reconnect with an unsent draft, and notification/background resumption against each claimed protocol; add the exact release APK install/update check. Reuse one combined run and record untested devices/flows instead of implying exhaustive parity. [#43](https://github.com/Eslamasabry/opencode-mobile-next/issues/43) remains a separate patch-channel rehearsal if Shorebird patch delivery is promised.
5. **Compatibility pin corrected in cycle 02.** README now identifies beta-18600 as the v2 capture; `contracts/README.md` identifies `f12e14cf` as the v1 snapshot. Align supported-server version/build evidence, release notes, and capability documentation. The August `opencode-sdk-coverage`, port-plan, public-release-audit, and reverification documents are historical records, not current gates: they contain resolved issues and stale future-feature lists. Android is the primary release target; desktop, store distribution, and untranslated locales must retain accurate status until their separate work is completed.

## BE-005 / BE-010 — implementation-ready pagination plan

**Status:** BE-005 global finder implemented in cycle 04; BE-010 message history implemented in cycle 05; scoped v2 session inventory implemented in cycle 06. All paginated routes retain `ServerPage` continuation. The plan below records the implementation requirements and pinned v1 / captured v2 beta-18600 contracts.

### Verified direction and cursor contracts

| Collection | First request | Older/next request | Authoritative continuation and order |
|---|---|---|---|
| V1 global finder | `/experimental/session?roots=true&limit=50`, plus search/archive filters; omit the selected directory/workspace as the current repository does | Repeat the same filters and limit, adding numeric `cursor` | `x-next-cursor` response header. Server sorts by updated time descending, then ID descending. No header means no following page on the pinned server. |
| V2 global finder | `/api/session?order=desc&limit=50&parentID=null`, plus search; retain `unscoped: true` | `/api/session?cursor=<token>` only | Body `cursor.next`, following the requested newest-first order toward older results. `cursor.previous` moves toward the already visited/newer side, not older history. |
| V1 chat history | `/session/{id}/message?limit=100`, with the pinned directory/workspace | Same path/location and positive `limit`, plus `before=<token>` | `X-Next-Cursor` response header, also supplied as `Link: ...; rel="next"`. The server selects the newest N records and returns that page in chronological order. **`before` is an opaque base64url cursor, not a message ID.** |
| V2 chat history | `/api/session/{id}/message?order=desc&limit=100` | Same session path with `cursor=<token>` only | Body `cursor.next` continues toward older messages. Reverse each newest-first response page for chronological rendering. Do not reverse cursor direction when reversing the display list. |

The v1 OpenAPI file declares `limit` and `before`, but omits the pagination response headers. Those details are confirmed in the exact upstream commit used by `contracts/README.md`: [message HTTP handler, lines 106–144](https://github.com/anomalyco/opencode/blob/f12e14cf1640cbf0dfb6b1ff425b2daaef459eec/packages/opencode/src/server/routes/instance/httpapi/handlers/session.ts#L106), [message paging implementation, lines 425–466](https://github.com/anomalyco/opencode/blob/f12e14cf1640cbf0dfb6b1ff425b2daaef459eec/packages/opencode/src/session/message-v2.ts#L425), and [global finder handler, lines 138–156](https://github.com/anomalyco/opencode/blob/f12e14cf1640cbf0dfb6b1ff425b2daaef459eec/packages/opencode/src/server/routes/instance/httpapi/handlers/experimental.ts#L138). The v1 message handler rejects `before` without `limit`; omitted/zero limit takes its legacy full-history path. Use a positive limit. Existing generated `sessionMessages` and `experimentalSessionList` return Dio `Response` objects with the original headers, including on the successful-response deserialization fallback.

V2 evidence is the captured `/api/session` and `/api/session/{sessionID}/message` parameter definitions, `SessionsResponse` / `SessionMessagesResponse`, and `docs/opencode2-protocol-notes.md` sections 4.2 and 12. Items retain the requested order across pages. `Api2Client.sessions` and `messages` already send continuation tokens alone; retain that behavior.

### 1. Small domain and adapter change

Add one page value in `lib/domain/server_gateway.dart` and make continuation opaque outside the adapter:

```dart
class ServerPage<T> {
  final List<T> items;
  final String? nextCursor;

  const ServerPage({required this.items, this.nextCursor});
  bool get hasMore => nextCursor != null;
}

// SessionOperationsGateway / ProductRepository:
Future<ServerPage<GlobalSessionResult>> listGlobalSessions({
  String? search,
  bool includeArchived = false,
  String? cursor,
  int limit = 50,
});

// SessionGateway:
// Each page is chronological; nextCursor always requests older history.
Future<ServerPage<MessageWithParts>> messagePage(
  String sessionID, {
  String? cursor,
  int limit = 100,
});
```

- **`SdkProductRepository.listGlobalSessions`:** parse a supplied domain token into the SDK's numeric cursor at this boundary only; reject invalid tokens rather than falling back to page one. Return the response's `x-next-cursor` string with mapped items. Keep query/archive/root semantics unchanged. Header names are case-insensitive.
- **`Api2OperationsGateway.listGlobalSessions`:** remove the non-null-cursor empty-list shortcut, request explicit `desc` on page one, and carry `page.nextCursor`. Any local archive/root filtering or duplicate suppression must not discard the server cursor; a sparse or even empty rendered page can still have another page.
- **`OpenCodeApi.messagePage`:** pass `limit` and `before` to the existing generated `sessionMessages` call and return mapped bundles plus the response cursor. Preserve headers from `DioException.response` when applying the existing successful-raw-response fallback. Prefer `X-Next-Cursor`; if a Link-only fallback is needed, extract just its `before` token and call the known API path, never follow a server-provided URL to another origin. Do not fabricate or decode/re-encode the token.
- **`Api2Gateway.messagePage`:** fetch exactly one raw page using the contract above and map `page.data.reversed`; `mapApi2Messages` maps records independently, so a page boundary does not require fetching all previous records. Carry the raw page's cursor even when mapping removes an invalid record. Preserve server order and equal-time ordering; remove ChatScreen's time-only sort for this path.
- Keep `messages(id)` temporarily only as an explicitly documented latest-page compatibility wrapper for the small smoke helper. Move every production read to `messagePage`: ChatScreen `_load`, `_discardUntouchedMobileSession`, and SessionContextScreen `_load`. Remove the `maxPages` message-fetch loop; neither normal open nor refresh should drain history. Update existing fake gateways to expose their fixture as one complete page; this is a fixture-interface migration, not a new broad test suite.

### 2. Global finder state and loading

In `_GlobalSessionsScreenState`, replace `_cursor(results)` and timestamp comparisons with stored `_nextCursor`. `_reload` replaces items/cursor atomically; `_loadMore` appends unseen session IDs and adopts the response cursor. `hasMore` comes solely from the cursor, never `items.length == limit` or `added.isNotEmpty`.

Bind a request to its repository/profile, query text, archive flag, and `_queryGeneration`; recheck after `prepareActionRepository` and after the HTTP request. Invalidate the old generation and disable load-more immediately when search/archive input changes, before the debounce fires, so an old continuation cannot run with a new v1 search filter. Keep already loaded results on load-more failure and retry the same token. An empty/duplicate page with a different cursor remains navigable; a repeated token must stop with a retry/reload error, not silently announce the end or start an automatic loop. Preserve the existing explicit Load more/retry affordance alongside near-bottom loading. Totals are loaded counts (`50+`), not a complete server count.

### 3. Chat open, older pages, and refresh merging

The current route is `GlobalSessionsScreen._open` → select location → `/chat/{sessionID}` → `ChatScreen.initState` → `_load`. `_onConnectionChanged` rehydrates after `dataRefreshRevision`; mutations also call `_load`. `ConnectionController` does not own transcript messages, so pagination belongs in ChatScreen (or a small dedicated transcript controller), not its global session map.

Add `_olderCursor`, `_loadingOlder`, `_olderError`, and a history epoch. Capture the API object, profile ID, location revision, session ID, epoch, and `_eventVersion` for each request. A location/server/session reset invalidates both head and older requests; latest refresh and older loads must be serialized or explicitly invalidate one another. Keep the existing generation checks and message/part version protection.

1. **First open / explicit history reset:** request one latest page, display its chronological rows, store its older cursor, and retain only pending sends/live changes newer than the request snapshot through the existing reconciliation logic. The latest reply is immediately present even in a 4,000+ record session. Show the empty-conversation screen only when both rows and continuation are absent; an empty mapped page with a cursor still needs a Load older action.
2. **Load older:** request exactly `_olderCursor`; merge snapshots by message ID and part ID with the existing version checks, prepend new older rows in server order, and retain every existing row outside that page. Do not call `_mergeHydratedMessages` unchanged: its final loop currently drops all unchanged records omitted from the response. Introduce an explicit older-page merge mode that preserves those unseen rows and does not resurrect a message removed after the request started. Adopt the next cursor only after accepting this response.
3. **Refresh the live end:** read a fresh latest page while keeping the visible transcript. When it overlaps the current canonical history, replace the covered recent segment using the version-aware merge and retain the already loaded older prefix and its continuation. Missing IDs outside the fetched segment are not deletions. Keep page-boundary/ordered-ID information rather than inferring the covered segment from timestamps. An explicit delete/revert/compaction invalidates affected cached history; fetch a fresh window instead of preserving removed snapshots.
4. **Reconnect / no-overlap recovery:** do not concatenate a new head with an old cached tail and conceal the missing middle. Start a fresh contiguous latest window with its returned older cursor, retaining pending sends and newer live events. Reconnect should invalidate cached older windows, since deletions or structural changes may have happened while offline. Keep the visible message anchor when still present; otherwise show that history was refreshed and move to the latest window. All missing older history remains reachable through the new cursor, with no fetch-all loop or arbitrary page cap.
5. **Failures:** older-page errors appear at the older edge with Retry and preserve text, position, and cursor. Latest-refresh errors preserve the existing transcript and composer. Invalid/expired cursors require an explicit fresh-history reload; do not silently append page one again. Absence of a cursor is the only normal end condition.

### 4. Preserve the reversed transcript's reading position

The current `ScrollablePositionedList` is reversed: list index 0 is newest, and `_messages` is chronological. Put the older-history loading/retry/end row at list index `_renderedMessageCount`, not index 0. Keep stable `message-{id}` keys.

Before merging older rows, capture one visible message ID and its `itemLeadingEdge` through `_messagePositions`. After layout, restore that ID/alignment with `_messageScroll` if a shift occurred. If `_pinnedMessageCount` is active, increase it by the number of newly prepended older records: otherwise the existing pinned prefix will hide previously visible recent rows. Preserve `_awayFromLatest`; loading older must never invoke `_jumpToLatest`.

Use one message-ID-to-list-index helper for timeline/search/tool jumps. With the current UI the calculation is `renderedCount - 1 - chronologicalIndex`; the older footer adds no leading offset. `_jumpToMessage` currently still adds an offset when busy although the builder no longer creates a busy row—remove that stale assumption while adopting the shared helper. Exclude the footer from `_earlierMessageCount` and viewport message-anchor calculations. Provide an explicit Load older action usable by touch, keyboard, and screen reader; automatic near-edge loading is optional and must be single-flight.

### 5. Callers and remaining limits

- **Empty-session discard:** use `messagePage(limit: 1)`; delete only after a successful, unambiguously empty response with no continuation. A failure or an empty mapped page that still has a cursor keeps the session. There is no reason to download a transcript to establish non-emptiness.
- **Context, transcript search, timeline, Markdown export:** these currently read the in-memory transcript. Carry whether older history remains and label their loaded-window limits. Session-reported usage remains authoritative; do not turn a page's summed tokens into lifetime totals. Search/timeline must offer access to older history, and Markdown export must state when it contains only loaded messages. Complete server JSON export remains #48.
- **Scoped session inventory (implemented cycle 06):** `sessionPage` replaces the v2 20-page enumeration cap with newest-first, cursor-only continuation. Controller merges preserve cached entities and alerts; completing a walk removes absent inventory membership only. Confirmed DELETE/events/404 remove entities, with revision guards and tombstones preventing stale resurrection. Recent, Archived, and command destinations expose continuation and loaded-only labels. Active/directly opened sessions hydrate independently of inventory. Metadata reads do not suppress concurrent status snapshots, and status failure leaves older loading available. The v1 `/session` route has no declared older-page cursor; its native list remains unchanged and `start` is not repurposed. BE-005 remains the independent server-wide finder.
- **V1 global timestamp ties:** pinned [Session.listGlobal, lines 555–574](https://github.com/anomalyco/opencode/blob/f12e14cf1640cbf0dfb6b1ff425b2daaef459eec/packages/opencode/src/session/session.ts#L555) filters `time_updated < cursor` even though ordering also uses ID. Equal timestamps split across pages can therefore be skipped by the upstream legacy endpoint. Preserve its returned cursor and record this server limitation; subtracting time or inventing a compound cursor cannot fix it. A future alternate compatibility endpoint needs its own verified root/archive/order semantics before substitution.
- **Compatibility evidence:** these headers/semantics are confirmed for the pinned v1 implementation, not every historical 1.x build or a proxy that strips headers. Do not convert a 400/missing-token problem into a successful empty page. Both pagers remain read-only and must reject stale scope/results without exposing tokens in user-facing copy.

**Focused acceptance for implementation:** verify latest-first first requests and correct cursor-only/limit-plus-before continuations; an empty middle page with a valid cursor; cursor-error retry; overlapping live events/deletion while an older page is pending; profile/query changes mid-request; and preserving the same visible message while older rows are prepended. Reuse existing client/global-session/chat hydration fixtures. No tests or product files were changed in this design pass.

## #53 / #55 — focused v2 reconciliation plan

**Status:** Ready for implementation planning; no product changes or checks performed in this pass. Existing issues [#53](https://github.com/Eslamasabry/opencode-mobile-next/issues/53) and [#55](https://github.com/Eslamasabry/opencode-mobile-next/issues/55) retain ownership. Contract evidence is `contracts/opencode2-openapi-beta-18600.json` (`Session.Info`, `Model.Ref`, `Session.Revert`, and the session model/agent/revert routes); event evidence is `docs/opencode2-protocol-notes.md` section 3.2 and the already parsed event types in `lib/api2/events.dart`.

### #53 — model, variant, and agent belong to the server session

| Missing link | Current code | Required change |
|---|---|---|
| Complete selection value | `Api2Session.model` retains variant, but `mapApi2Session` flattens to `providerID/modelID`; `Session.copyWith` cannot explicitly clear nullable selection fields. | Keep a full typed per-session selection, including variant, and represent unknown/unhydrated separately from a server-inherited selection. Provide explicit clearing for a removed variant. |
| Server state reaches consumers | `modelForSession` / `variantForSession` prefer `sessionModels` then profile defaults; `selectedAgent` is profile-wide. `Api2SessionCreatedEvent` parses model/agent but its adapter omits them. | Hydrate per-session state from session reads and creation responses/events. Add `agentForSession` and a session-scoped setter; profile preferences are defaults for new sessions, not overrides of existing v2 sessions. Fetch a directly opened session by ID if inventory pagination has not loaded it. |
| Live changes reach state | `Api2SessionModelSelectedEvent` and `Api2SessionAgentSelectedEvent` exist, but `Api2EventAdapter.adapt` has no handlers. | Forward explicit selected events with session ID and full model/agent payload. Merge only that session, call `_markSessionChanged`, and notify consumers. Use existing revision/gateway-generation guards so an earlier REST result cannot overwrite a later event. Partial rename/move events must merge their fields instead of replacing the full session and erasing its selection/revert state. |
| User choices reach the API | `selectModelForSession` writes local preferences only; `pickers.dart`, `_agentPicker`, uses `selectedAgent` / `selectAgent`. `_applySelection` instead writes model/agent before each ordinary prompt/shell/command. | Expose domain session-selection operations backed by `Api2Client.switchModel` / `switchAgent`. Invoke them for intentional picker/cycle changes. Pass `sessionID` through the agent picker and update chat chips/send paths to session getters. Remove unconditional selection writes from ordinary v2 sends. |
| Creation uses defaults | `Api2Client.createSession` already accepts model and agent, but `SessionGateway.createSession` / `ConnectionController.createSession` / `Api2Gateway.createSession` do not pass them. | Extend the creation options to carry model, variant, and agent. Apply the profile's chosen defaults only when creating the new v2 session, then adopt the returned server selection. |

**Mutation/reconciliation behavior:** serialize selection mutations per session, capture profile/API/session identity before awaits, and expose saving/error state beside the affected picker. A 204 means the mutation was accepted, not that a stale optimistic value may overwrite a newer remote selection; settle through the matching event or a revision-guarded session read. Re-selecting the already confirmed value is a no-op. Unknown catalog entries remain visible as unavailable/refreshable instead of silently selecting another provider/model. Remote selections should not populate the user's local favorites or recents.

**Preserve:** v1's per-prompt model/agent/variant payloads; local per-chat model isolation; model favorites/recents/cycling; availability validation; composer content after failures. Ordinary v2 sends must use the server's current session selection. Do not lose explicit selection snapshots carried by offline queue/retry flows when removing `_applySelection`: distinguish an intentional queued override from a profile/default display value and apply such overrides deliberately through the selection operation. The v2 prompt body has no per-inbox model/agent field, so do not promise atomic model binding to a queued item while another client can change the shared session. Explain that selection changes govern subsequent provider turns, as the route descriptions specify.

**Acceptance:** another client changes model, clears a variant, or changes agent and only the matching conversation's visible selection updates; reconnect adopts server state; an ordinary send issues no redundant model/agent POST; a failed picker write keeps the draft and allows retry; a delayed response/profile switch cannot rewrite another session; a new session receives defaults while existing sessions retain their own selections. Relevant existing fixtures are `test/session_model_scope_test.dart`, `test/api2_gateway_events_test.dart`, `test/api2_interaction_gateway_test.dart`, and `test/model_library_test.dart`; extend only the affected scenarios when implementing.

### #55 — retain the staged boundary and expose stage / commit / clear

The captured stage route returns **HTTP 200 `{data: Session.Revert}`**, with required `messageID` and optional `partID`, `snapshot`, and `files: FileDiff.Info[]`. Stage can move a reversible boundary and optionally apply file changes. Commit and clear return 204; all three declare 409 `SessionBusyError`. No endpoint accepts a compare-and-swap boundary/revision parameter.

| Missing link | Current code | Required change |
|---|---|---|
| Revert state | `Api2Session.fromJson` / `mapApi2Session` retain only `reverted: bool`; `Session.copyWith` cannot clear or replace revert data. | Carry a typed nullable staged-revert value through wire model, domain session, and copy/update path. Retain boundary and returned file patches; use `FileDiff.fromJson`/the existing patch renderer rather than a whole-working-tree substitute. Missing `files` means preview unavailable, not zero changed files. |
| Separate operations | `Api2OperationsGateway.revertSession` calls stage with `files: true` but discards the response. `restoreSession` calls clear; commit has no domain/API/UI link. | Add `stageSessionRevert(id, messageID, applyFiles)` returning the typed stage, plus `commitSessionRevert(id)` and `clearSessionRevert(id)`, behind a v2 staged-revert capability. Keep the old v1 revert/restore methods intact. |
| Events and immediate state | `Api2SessionRevertEvent` already parses staged/cleared/committed (including committed `to`), but the adapter drops them. Chat `_revertLast` / `_restore` then only call `_load`, which reloads messages; `session.reverted` and Restore can stay stale. | Forward each event explicitly. Merge/clear staged state for the matching session, advance its revision, and refresh its transcript/changes after structural operations. Apply the stage's 200 response immediately; after commit/clear, reconcile the session itself as well as messages. Do not wait for an unrelated full session-list refresh. |
| Lasting, accurate UI | `_revertLast`, `_restore`, `/undo`, `/redo`, and the session action sheet present the v1 one-shot flow. | On v2, stage the chosen canonical prompt, show returned affected files and a persistent staged-boundary banner, and provide distinct Commit and Clear actions. Closing the sheet or reopening the chat must preserve that banner via hydrated session state. Keep keyboard/slash/menu affordances consistent with the same state and operation. |

**Mutation/reconciliation behavior:** allow only one revert mutation per session; disable while busy and handle a server-side 409 without losing the staged preview. Capture profile, repository, session ID, and the displayed boundary before any await. If a remote event changes that boundary, invalidate an open commit confirmation and show the new preview; read the current session before committing and compare the boundary/snapshot the user reviewed. The protocol has no atomic conditional commit, so this reduces but cannot eliminate a cross-client change between that read and POST. After a timeout/ambiguous response, reconcile state before presenting a retry instead of assuming that no mutation occurred. A committed event invalidates cached transcript pages from BE-010; it must not be handled as an ordinary append.

**Preserve and verify semantics:** retain v1's undoable revert/restore flow and its existing confirmation. Do not automatically commit on v2, and do not claim that merely staging is a permanent rollback. The current adapter uses `files: true`, so staging may already change files; a preview shown after staging must not claim those files are untouched. #55 explicitly requires one pinned-server check of stage → clear and stage → commit file/history effects before final UI wording and release sign-off. The contract establishes route/data/state distinctions, not every working-tree recovery guarantee.

**Acceptance:** stage immediately enables a visible persistent Clear/Commit state; returned patches can be reviewed; leaving/reopening and reconnecting preserve the server boundary; remote stage/clear/commit changes update only the matching chat; busy/missing-anchor failures leave a recoverable UI; stale confirmations cannot knowingly commit a different boundary; commit/clear refresh both session metadata and transcript; v1 keeps the current one-shot UI. Reuse `test/api2_interaction_gateway_test.dart`, `test/api2_gateway_events_test.dart`, and existing chat mutation fixtures when implementing. No tests were run for this plan.

## Review notes

- Initial five findings were inspected in source and checked against the captured v2 contract; no live server behavior was claimed.
- The second pass checked provider/catalog loading, OAuth, MCP configuration, profile storage, probing, and supporting device services. BE-006 through BE-009 were selected from concrete control flow and captured schemas; weaker findings were omitted.
- Deduplicated the second pass against this backlog, the frontend backlog, the checked-in audit/reverification documents, and the repository's open GitHub issues on 2026-09-05. Existing model-sync, read-state, aggregate-usage and persistent-stash issues were not re-entered.
- The release review compared both captured contracts, current gateways/state/UI, release scripts/workflow, historical parity documents, and current open issues/published releases. It added BE-010 and BE-011 and retained the broader unimplemented v2 inventory. No product files, tests, builds, emulators, CI runs, or release state were changed in this pass.
- Current review pass is documentation-only. Root owns implementation and final verification.
