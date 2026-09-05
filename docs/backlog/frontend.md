# Frontend backlog

Code-reviewed findings for the ongoing review, implement, and push cycle. IDs stay stable across passes. Scope: Flutter presentation and everyday user flows. No emulator sessions or tests were run for this review.

Pass 2 reviewed Commands, Skills, References, Terminal, and Settings. The open GitHub issue inventory was checked on 2026-09-05. FE-005 through FE-008 are specific implementation gaps outside the existing feature issues; they do not create duplicate GitHub issues. The older audit's general refresh-retention guidance is narrowed to the concrete failure in FE-006.

Pass 3 rechecked onboarding, session finding and request handling, Files navigation, Review, accessibility infrastructure, release notes, and recent QA records on 2026-09-05. Root completed FE-007 and FE-008 in cycle 2026-09-05-02, FE-005, FE-006, and FE-009 in cycle 03, then FE-010 and FE-011 in cycle 04. The v1 scope below maps remaining feature work to its existing GitHub issues instead of creating duplicate tasks. Historical audit ratings and old CI/signing blockers are not treated as current evidence.

## FE-001 — Refresh Workspace sessions with the pull gesture

Status: **Implemented — cycle 2026-09-05-01**

- Trigger and impact: Pull down on Workspace after sessions change elsewhere. The gesture reloads project/workspace metadata but leaves the session list stale.
- Location: `lib/ui/screens/workspace_screen.dart`, `_load` and `RefreshIndicator.onRefresh`.
- Implementation: Add an explicit user refresh operation that refreshes sessions and context. Keep automatic context-only reloads separate.
- Minimal verification: Existing `test/projects_screen_test.dart` and `test/session_needs_you_test.dart` screen harnesses; verify one pull refresh obtains fresh sessions.

## FE-002 — Preserve and consistently execute file searches

Status: **Implemented — cycle 2026-09-05-01**

- Trigger and impact: Type a file name, refresh matching results, or switch Files → Symbols → Files. File search waits for Submit while Symbols searches as typed; Refresh/Retry replaces results with directory contents while the query remains visible; switching surfaces can retain search results after clearing the query.
- Location: `lib/ui/screens/files_screen.dart`, `_searchFiles`, `_selectSurface`, `_fileListContent`, search field callbacks.
- Implementation: Debounce file queries with stale-response guards, use a current-query refresh helper, and reload the original directory when leaving search mode.
- Minimal verification: Relevant file-search and symbol-search cases in `test/product_ui_regression_test.dart`; one short search/refresh/surface-switch check.

## FE-003 — Show the active execution directory in Workspace

Status: **Implemented — cycle 2026-09-05-01**

- Trigger and impact: Work in a known worktree or managed workspace. The header still prefers project root, and the supplementary directory row is suppressed for known worktrees, making the displayed execution context incorrect.
- Location: `lib/ui/screens/workspace_screen.dart`, `_contextSubtitle` and `_selectWorkspace`.
- Implementation: Prefer the controller's current directory or selected workspace directory under the project name. Reconcile selection indicators with controller state after the selection completes or fails.
- Minimal verification: Existing workspace context harness in `test/projects_screen_test.dart`; verify the displayed directory and selection after a successful switch and a failed switch.

## FE-004 — Make project-file preview actions readable on a phone

Status: **Implemented — cycle 2026-09-05-01**

- Trigger and impact: Open a project file from chat with attachment and reference actions available. Up to five icon buttons crowd the unbounded filename; the mobile sheet has no visible Close. Copy can run before content loads and copies the truncated display buffer for large files.
- Location: `lib/ui/screens/files_screen.dart`, `__FileViewerState.build` and its Copy action.
- Implementation: Give filename/path and Close a clear header. Move secondary actions to overflow or a wrapping toolbar. Disable Copy before text loads and copy the full loaded content.
- Minimal verification: Existing file-preview/attachment cases in `test/product_ui_regression_test.dart` and compact text-scale coverage; confirm Copy uses the original content.

## FE-005 — Run a server command without requiring an existing chat

Status: **Implemented — cycle 2026-09-05-03**

- Trigger and impact: Open More → Commands & tools → Commands on a fresh workspace and tap a command. `_run` only shows “Create a session before running a command.” The user must leave, create a chat, return, and find the command again. Existing-session submissions also close the argument dialog before sending, so failure discards the entered arguments and leaves no in-flight indication.
- Location: `lib/ui/screens/library/commands_screen.dart`, `_CommandsScreenState._run` and command row `onTap`.
- Implementation: Add a New chat destination to a scrollable run dialog, selected by default when there are no sessions. Create the chat only after Run is confirmed, submit through the existing command path, and open the resulting chat. Keep the dialog's arguments and destination on failure; disable repeated Run/taps while sending. When choosing an existing chat, honor that session's selected model rather than silently applying the profile default.
- Minimal verification: One first-command flow covering creation → command → navigation, and one failed submission showing arguments retained and no duplicate dispatch. Existing slash-command behavior is covered in `test/chat_live_events_test.dart` around the “typed server command passes arguments and selected model” case. No build or emulator pass is needed for the catalog-only change.

## FE-006 — Report failed refreshes while keeping useful catalog rows

Status: **Implemented — cycle 2026-09-05-03**

- Trigger and impact: Load Commands, Skills, References, or Terminal, then pull to refresh after the server becomes unavailable. Each screen records an error but renders it only when its list is null. Previously loaded rows remain with no indication that refresh failed, so terminal status and available commands appear current. The same suppression happens after refreshing a successfully loaded empty list.
- Location: `lib/ui/screens/library/commands_screen.dart`, `_load`/`build`; `lib/ui/screens/library/skills_screen.dart`, `_load`/`_body`; `lib/ui/screens/library/references_screen.dart`, `_load`/`_body`; `lib/ui/screens/terminal_screen.dart`, `_TerminalScreenState._load`/`build`.
- Implementation: Keep the previous list and scroll position, but show a compact inline refresh-failed message with Retry whenever an error accompanies loaded data, including an empty list. Clear the notice after successful refresh. Guard overlapping loads or reuse a generation token so an older response cannot overwrite a later refresh. Reuse the existing ProductErrorState only for a first load that has no data.
- Minimal verification: Reuse the fake repositories in `test/library_skills_test.dart` and `test/product_ui_regression_test.dart`; successful load → failing refresh retains rows and shows Retry → successful retry clears the notice. A representative shared pattern check is sufficient; no golden refresh campaign.

## FE-007 — Make Default shell's retry action actually retry

Status: **Implemented — cycle 2026-09-05-02** · Priority: **Medium**

- Trigger and impact: Load Coding defaults successfully, then let the automatic resume refresh fail. The Default shell row says “Tap to retry,” but its `onTap` always calls `_chooseShell`. Since cached settings are non-null, `_chooseShell` opens old choices instead of retrying the failed request.
- Location: `lib/ui/screens/settings/coding_settings_screen.dart`, `_CodingSettingsScreenState._chooseShell` and `default-shell-settings-entry.onTap`.
- Implementation: When `_shellError` is present, route the row action to `_loadShellSettings`; only show the chooser after a successful load. Preserve the selected shell while displaying the failure and loading indicator.
- Minimal verification: Extend the existing shell settings harness in `test/settings_server_updates_test.dart`: initial success → failed reload → tap error row calls the loader and does not open the chooser with stale choices. The v2 capability gate remains covered by `test/v2_feature_gating_test.dart`.

## FE-008 — Keep Commands & tools tab names readable on small screens

Status: **Implemented — cycle 2026-09-05-02** · Priority: **Medium**

- Trigger and impact: Open Commands & tools on a narrow phone or with enlarged text. `CapabilitiesScreen` assigns four long labels to the default equal-width, non-scrollable TabBar. “Commands” and “References” must fit into roughly a quarter of the viewport minus tab padding; the navigation loses readable labels at compact sizes. This is a code-layout finding, not a newly captured screenshot.
- Location: `lib/ui/screens/capabilities_screen.dart`, `CapabilitiesScreen.build` → `AppBar.bottom` → `TabBar`.
- Implementation: Use a horizontally scrollable, start-aligned tab bar when compact or when text scaling needs it, retaining the existing tabs and capability-gated Tools omission. Avoid abbreviations that make the destinations harder to understand.
- Minimal verification: Use the existing `CapabilitiesScreen` harness in `test/v2_feature_gating_test.dart` at 320dp and enlarged text; each label remains fully reachable and switching to References still selects its content. This is a small layout check, not a new golden suite.

## FE-009 — Keep connection-test results tied to the displayed server fields

Status: **Implemented — cycle 2026-09-05-03**

- Trigger and impact: Start Test connection, then edit the URL, username, or password before the reply arrives. The fields stay editable; their callbacks clear the verdict but do not invalidate the pending generation. The old request can restore “Connected — save to finish” or move focus to the password field for values no longer displayed. `_save` also reads that stale result's flavor and version. Cold connection re-probes, so this is misleading onboarding feedback and cached metadata, not proof of a permanent wrong-server connection.
- Location: `lib/ui/screens/servers_screen.dart`, `_urlChanged` (756–788), `_testConnection` (814–839), `_pastePassword` (979–1005), credential `onChanged` callbacks (1245–1263), and `_save` (1063–1080). `_applyPairing` shares the same generation counter.
- Implementation: Centralize invalidation when connection inputs change, including programmatic password paste. Increment the generation, clear the old verdict, and release obsolete testing/pairing busy state; ignore all stale completion and focus changes. Snapshot the tested input tuple so only a matching result can supply the saved flavor/version. Prevent Test and Pair from leaving one another's busy indicator stuck when a later operation supersedes the earlier one.
- Minimal verification: Extend the existing delayed HTTP/probe harness in `test/server_v2_connect_flow_test.dart`: edit URL or password during a deferred success, complete it, and assert no old verdict or focus jump. Then run a fresh test and save its metadata. Reuse the pairing fixture in `test/server_pairing_paste_test.dart` for one superseded-pair check; no emulator run for this change.

## FE-010 — Preserve the reviewed file when refreshed diffs change order

Status: **Implemented — cycle 2026-09-05-04**

- Trigger and impact: Review the second changed file, then refresh after another file was inserted, removed, or reordered. `_load` clears the readable diff, retains only `_selectedFile`'s numeric index, and marks whichever file occupies that index as Viewed. Refresh can silently move the reader to a different file and inflate viewed progress. A failed refresh also removes all previously readable changes. This is a concrete current instance of audit UX-REV-05, not a new general refresh redesign.
- Location: `lib/ui/screens/review_workspace.dart`, `_ReviewWorkspaceState._load` (91–117), `_markViewed` (125–132), and `_reviewContent` (276–305).
- Implementation: Before a same-scope refresh, capture the selected normalized path and retain the current diff. Reconcile selection by path after success, falling back deliberately only if that file disappeared. Preserve scroll and line selection only when the selected patch is unchanged; clear invalid selections when content changes. Remove Viewed marks for files whose patch changed. Show a compact refresh error and Retry over retained content, using the full error state only when nothing has loaded. Keep explicit scope changes separate from same-scope refreshes.
- Minimal verification: Reuse `test/review_workspace_test.dart`'s refresh and viewed-progress cases: insert a preceding file during refresh and keep the selected filename; fail refresh and retain the readable patch; change a previously viewed patch and clear its old viewed mark. No screenshot campaign is necessary.

## FE-011 — Make Android Back navigate out of nested file folders

Status: **Implemented — cycle 2026-09-05-04**

- Trigger and impact: Open Files, descend into a folder, and use Android Back to return to its parent. Files changes `_path` within one route and has no back handler, so `HomeScreen._onRootPop` instead says “Press back again to exit” and then exits. The root breadcrumb's only label is `/`, which does not describe its destination to assistive technology.
- Location: `lib/ui/screens/home_screen.dart`, `PopScope` (129–131) and `_onRootPop` (216–232); `lib/ui/screens/files_screen.dart`, `_navigateTo` (203–208) and breadcrumb actions (725–747).
- Implementation: Let the active Files surface consume Back while it has search state or a non-root path: clear search first, otherwise navigate one directory up through the existing loader. Only the active IndexedStack child should consume Back; preview routes and keyboard dismissal retain their normal behavior. Use the existing exit guard once Files is at its root. Give the root/ancestor breadcrumbs descriptive semantic labels such as “Project root” and “Open folder lib”, and identify the current folder without presenting it as an actionable destination.
- Minimal verification: Combine `test/home_navigation_test.dart` with the nested-files fixture in `test/product_ui_regression_test.dart`: Back at `lib/src` loads `lib` without sending `SystemNavigator.pop`; Back with a search clears it; the inactive Files tab does not intercept another screen. Check the root breadcrumb's semantic label in the same harness.

## v1 readiness scope — current evidence, not a release approval

The practical candidate is an Android, English-language coding client with remote pairing and guided Termux setup, reliable session/chat recovery, provider/model selection, attachments and persisted draft text, requests, Files/Review, and Running work. Preserve the existing capability gates and v1 compatibility. Completing that scope still requires the current backend queue, FE-005/006/009/010, and the request/revert gaps below. FE-011 is a small navigation completion worth including in a later polish batch. This is a proposed release bar; it does not redefine the user's broader feature-parity goal or mark it complete.

### Existing issues that need implementation, not duplicate backlog IDs

| Existing work | Current code evidence and next implementation slice |
| --- | --- |
| [#10 — permission-card edge cases](https://github.com/Eslamasabry/opencode-mobile-next/issues/10) | Chat already dismisses a resolved permission route via `chat_screen.dart::_dismissResolvedPermissionDialog`. Activity's `ActivityPermissionTile` calls the snapshot-only `showPermissionSheet` in `chat/permission_sheet.dart`; `_QuestionSheetState` in `activity_screen.dart` also never observes controller changes. Withdrawal or resolution elsewhere leaves those sheets actionable until the user taps again. Share the pending-ID lifecycle guard across entry points, dismiss only the matching sheet, and retain the controller's duplicate-reply protections. One withdrawn Activity permission and one resolved question scenario can extend `test/chat_permission_test.dart` / `test/chat_question_card_test.dart`. |
| [#55 — staged revert, commit, clear](https://github.com/Eslamasabry/opencode-mobile-next/issues/55) | Implemented in cycle 08: persistent Review banner, recognizable boundary prompt, returned file patches, separate confirmed Clear/Commit, large-text scrolling, stale-review guards and truthful hidden-history behavior. Drafts and queued prompts require explicit resolution instead of the server's implicit commit on Send. Pinned beta-18600 stage/clear/commit file/history effects verified live; see `docs/verification/staged-revert-beta-18600.md`. V1 retains its existing flow; final device UX remains in release verification. |
| [#53 — cross-client model/agent state](https://github.com/Eslamasabry/opencode-mobile-next/issues/53), [#57 — unread/viewed sessions](https://github.com/Eslamasabry/opencode-mobile-next/issues/57) | #53 client implementation completed in cycle 07: matching-session selection, explicit picker writes, reactive untouched drafts, retained variant edits, and offline snapshots. Final cross-client/device verification remains. #57 remains open: show a text/semantic unread state, mark viewed only when the idle chat is foregrounded, and honor the issue's privacy opt-out. |
| [#58 — bounded session note](https://github.com/Eslamasabry/opencode-mobile-next/issues/58), [#54 — aggregate usage](https://github.com/Eslamasabry/opencode-mobile-next/issues/54), [#48 — JSON export](https://github.com/Eslamasabry/opencode-mobile-next/issues/48) | Session context is a read view, usage is per session, and `_exportTranscript` still exports rendered Markdown. Add one note action scoped to `mobile.note`, one secondary usage screen with range/project scope, and a redacted server JSON option in the existing export action. Keep unsupported-server actions hidden. These are real missing feature-parity surfaces; fixing cosmetic issues does not complete them. |
| [#29 — pin/unarchive](https://github.com/Eslamasabry/opencode-mobile-next/issues/29), [#47 — persistent prompt stash](https://github.com/Eslamasabry/opencode-mobile-next/issues/47), [#25 — transcript find](https://github.com/Eslamasabry/opencode-mobile-next/issues/25) | `_showArchived` explicitly offers no unarchive. Pinning and persistent stash remain separate from existing session drafts/prompt reuse. Timeline already searches loaded message text and jumps to a message (`chat/timeline_sheet.dart`); #25 should add match navigation/highlighting and honest search coverage, not another search-only sheet. Follow the existing issue acceptance criteria and reuse these entry points. |

BE-005 owns All sessions pagination; FE-006 owns catalog refresh failures. Neither should be re-entered as new frontend work. Provider/OAuth/MCP parity is the current backend implementation lane. Open issues #61/#62 describe Running work already present in code and recent QA; their open status alone is not evidence that the feature is missing. Likewise #37 predates Android notification authentication and needs a current residual-scope check before treating its entire description as absent.

### Accessibility and audience completeness

- The app already has reduced-motion branches in the composer, transcript, permission/form rendering, tool cards, and running indicators. `test/accessibility_guidelines_test.dart` checks critical surfaces for target size, labels, and contrast. Do not replace this with a new blanket motion or theme rewrite.
- FE-008's compact/large-text tabs are fixed; FE-011 covers the remaining Files navigation semantics. For a release candidate, one short TalkBack path through first connection → chat → request → Files/Review should establish focus order and understandable announcements; automated semantics do not prove that interaction. The recent QA documents show large-text/model/composer evidence, not this full TalkBack path. This review did not run it.
- `lib/l10n/app_localizations.dart:95` advertises English only. Externalization and Arabic/RTL/locale selection remain in [#15](https://github.com/Eslamasabry/opencode-mobile-next/issues/15), [#18](https://github.com/Eslamasabry/opencode-mobile-next/issues/18), [#19](https://github.com/Eslamasabry/opencode-mobile-next/issues/19), and [#20](https://github.com/Eslamasabry/opencode-mobile-next/issues/20). An English v1 can be described honestly; multilingual completeness cannot yet be claimed. Keep new strings in ARB without increasing the localization baseline.
- Camera/gallery convenience (#28), expanded desktop packaging/real-runtime work (#22–24), and optional platform enhancements remain existing scope, not hidden requirements to redesign the core Android flow. Desktop is still explicitly experimental in `docs/desktop.md` and README.

### Final release evidence to consolidate once

README and `docs/release-alpha-notes.md` still describe a public alpha. The recent `docs/qa/model-picker.md`, `docs/qa/composer-workflows.md`, and `docs/qa/running-work.md` document debug APK/device or fixture checks and explicitly distinguish them from live v2 and release signing. Before changing the public promise to stable v1, root should record the actual release commit/version/signer and one focused supported-server/device smoke after the final batch, then update the release notes and compatibility claims to that evidence. Do not rerun the whole app for each backlog item, and do not carry forward the August audit's obsolete CI-billing claims. This read-only pass did not inspect CI state or run the app.
