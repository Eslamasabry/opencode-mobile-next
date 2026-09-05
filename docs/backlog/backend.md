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

- **Status:** Ready
- **Priority / confidence:** High / high
- **Evidence:** `lib/api2/gateway.dart`, `messages`, starts with `order: 'asc'`, follows at most `maxPages = 20` pages of `pageLimit = 200`, and returns without exposing the remaining cursor. Captured `GET /api/session/{sessionID}/message` explicitly supports newest-first `desc` and opaque continuation tokens. `SessionGateway.messages` currently returns only a list. The gateway's own pagination TODO acknowledges this interface gap.
- **User impact:** Above 4,000 server message records, opening or refreshing a session can omit its newest response. Search, context estimates, and Markdown export also operate on an incomplete history without telling the user. Raising the cap only moves the failure point.
- **Implementation:** Hydrate a bounded newest-first page, map it into chronological display order, and retain the opaque older-history cursor in a page result. Add incremental older-history loading with deduplication, scroll preservation, and a visible loading/error/end state. Preserve the v1 adapter's actual pagination contract. Treat complete JSON export separately under #48; loaded Markdown must not imply a complete backup. Apply the same explicit continuation model to the bounded session inventory and BE-005.
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
| High | **Reach every conversation and recent reply:** BE-005 and BE-010 remain incomplete. | Carry real cursors through gateway, state, and UI; load more with recoverable errors. Do not declare history coverage from short fixtures. |
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

## Review notes

- Initial five findings were inspected in source and checked against the captured v2 contract; no live server behavior was claimed.
- The second pass checked provider/catalog loading, OAuth, MCP configuration, profile storage, probing, and supporting device services. BE-006 through BE-009 were selected from concrete control flow and captured schemas; weaker findings were omitted.
- Deduplicated the second pass against this backlog, the frontend backlog, the checked-in audit/reverification documents, and the repository's open GitHub issues on 2026-09-05. Existing model-sync, read-state, aggregate-usage and persistent-stash issues were not re-entered.
- The release review compared both captured contracts, current gateways/state/UI, release scripts/workflow, historical parity documents, and current open issues/published releases. It added BE-010 and BE-011 and retained the broader unimplemented v2 inventory. No product files, tests, builds, emulators, CI runs, or release state were changed in this pass.
- Current review pass is documentation-only. Root owns implementation and final verification.
