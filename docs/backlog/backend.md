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

- **Status:** Ready
- **Priority / confidence:** Medium / high
- **Evidence:** `lib/api2/gateway_operations.dart`, `listGlobalSessions`, returns an empty list for every non-null integer cursor. `lib/ui/screens/global_sessions_screen.dart`, `_cursor`, derives timestamps. Captured `GET /api/session` and `SessionsResponse` explicitly support opaque `cursor.next` and `cursor.previous` strings.
- **User impact:** Global search cannot reach results beyond the first 50 conversations.
- **Implementation:** Return a page object carrying results and the opaque continuation cursor; let the v1 adapter retain its timestamp-based cursor. Forward v2 tokens verbatim and alone in the next request. Do not guess a timestamp-to-token conversion.
- **Minimal verification:** Existing `test/api2_client_test.dart` cursor fixture and `test/global_sessions_screen_test.dart` load-more checks; traverse two pages for each server flavor.

## BE-006 — Handle every concurrent catalog request immediately

- **Status:** Ready
- **Priority / confidence:** High / high
- **Evidence:** `lib/api2/gateway.dart`, `providers`; `lib/api2/gateway_operations.dart`, `loadCatalog`; and `lib/api/product_repository.dart`, `SdkProductRepository.loadCatalog`, start several requests, then attach error handlers by awaiting them one at a time. V2 `loadVersionControlHealth` uses the same delayed handling for its status request.
- **User impact:** If a later request fails while an earlier request is still pending, its error escapes as an unhandled asynchronous error instead of reaching the existing recoverable catalog or project-health state.
- **Implementation:** Await a typed record with `.wait` or use `Future.wait` so every request has a handler immediately. For intentionally optional VCS responses, attach each fallback before awaiting the group. Preserve typed `ApiException`/`ProductException` mapping when unwrapping record-wait errors.
- **Minimal verification:** Existing catalog fixture in `test/product_repository_test.dart`; one delayed first request with an immediately failing later request, asserting one handled failure and no uncaught asynchronous error. Existing project-health checks cover the optional fallback behavior.

## BE-007 — Keep OAuth attempt context through code confirmation

- **Status:** Ready
- **Priority / confidence:** High / high
- **Evidence:** `lib/api2/gateway_operations.dart`, `completeIntegrationOAuth`, removes `_oauthAttemptIntegration[attemptID]` immediately after POST success. `lib/ui/screens/library/integrations_screen.dart`, `_enterOAuthCode`, immediately calls `integrationOAuthStatus`, whose `_attemptIntegration` now throws that the attempt is no longer tracked. The captured OAuth complete route returns 204 and stores the credential; the status read is part of the existing UI flow.
- **User impact:** A successful code-based provider connection is shown as a failed/abandoned sign-in and does not finish refreshing the model catalog.
- **Implementation:** Keep the attempt's integration context through its required status read. If retiring it at a terminal status, cache that terminal result long enough for the existing completion/retry path. Cancel should still retire pending state. Do not alter the v1 completion contract.
- **Minimal verification:** Existing code-OAuth widget scenario in `test/library_integrations_test.dart`; route a start → complete (204) → status (complete) sequence through the real v2 adapter and assert catalog refresh remains reachable.

## BE-008 — Keep provider and MCP actions in the displayed location

- **Status:** Ready
- **Priority / confidence:** High / high
- **Evidence:** `lib/api2/gateway_operations.dart`, `listIntegrations` and `listMcpServers`, send `_loc()`, but `connectIntegrationKey`, OAuth start/status/complete/cancel, and MCP add/connect/disconnect omit location queries. The captured contract declares the same optional deep-object location on each of these routes.
- **User impact:** A project-specific provider or MCP server can be listed correctly but fail to connect, disconnect, or authenticate because the action is resolved against the server's default location.
- **Implementation:** Forward the pinned directory/workspace on the affected location-scoped mutations and reads. Snapshot the starting location with OAuth attempt context and reuse that snapshot for later attempt actions. Credential-ID deletion remains governed by its own global route contract.
- **Minimal verification:** Inspect outgoing requests from a v2 gateway pinned to both a directory and a workspace; verify list and actions carry the same scope. Reuse the MCP and integration fixture shapes in `test/product_repository_test.dart`; no native UI run is needed.

## BE-009 — Encode MCP timeout in the v2 wire shape

- **Status:** Ready
- **Priority / confidence:** High / high
- **Evidence:** `lib/domain/server_gateway.dart`, `McpServerDraft.toConfigJson`, emits a scalar `timeout: timeoutMs`. `lib/api2/gateway_operations.dart`, `addMcpServer`, forwards that map unchanged. Captured `Mcp.LocalConfigEncoded` and `Mcp.RemoteConfigEncoded` instead define `timeout` as an object with optional positive integer `startup`, `catalog`, and `execution` fields. Existing scalar assertions in `test/product_repository_test.dart` belong to the v1 adapter.
- **User impact:** Entering any optional timeout in Add MCP server makes the v2 request invalid, even though the form validates it successfully.
- **Implementation:** Translate the draft at the v2 adapter boundary. Map the existing generic timeout to the named startup/catalog/execution durations consistently, or expose separate fields if different values are desired. Keep omitted timeout omitted and retain the v1 scalar representation.
- **Minimal verification:** One local and one remote v2 MCP request with a timeout must match the captured object schema; existing v1 scalar expectations must remain unchanged.

## Review notes

- Initial five findings were inspected in source and checked against the captured v2 contract; no live server behavior was claimed.
- The second pass checked provider/catalog loading, OAuth, MCP configuration, profile storage, probing, and supporting device services. BE-006 through BE-009 were selected from concrete control flow and captured schemas; weaker findings were omitted.
- Deduplicated the second pass against this backlog, the frontend backlog, the checked-in audit/reverification documents, and the repository's open GitHub issues on 2026-09-05. Existing model-sync, read-state, aggregate-usage and persistent-stash issues were not re-entered.
- Current review pass is documentation-only. Root owns implementation and final verification.
