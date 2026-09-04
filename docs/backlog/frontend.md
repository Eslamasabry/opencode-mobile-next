# Frontend backlog

Code-reviewed findings for the ongoing review, implement, and push cycle. IDs stay stable across passes. Scope: Flutter presentation and everyday user flows. No emulator sessions or tests were run for this review.

Pass 2 reviewed Commands, Skills, References, Terminal, and Settings. The open GitHub issue inventory was checked on 2026-09-05. FE-005 through FE-008 are specific implementation gaps outside the existing feature issues; they do not create duplicate GitHub issues. The older audit's general refresh-retention guidance is narrowed to the concrete failure in FE-006.

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

Status: **Ready** · Priority: **High**

- Trigger and impact: Open More → Commands & tools → Commands on a fresh workspace and tap a command. `_run` only shows “Create a session before running a command.” The user must leave, create a chat, return, and find the command again. Existing-session submissions also close the argument dialog before sending, so failure discards the entered arguments and leaves no in-flight indication.
- Location: `lib/ui/screens/library/commands_screen.dart`, `_CommandsScreenState._run` and command row `onTap`.
- Implementation: Add a New chat destination to a scrollable run dialog, selected by default when there are no sessions. Create the chat only after Run is confirmed, submit through the existing command path, and open the resulting chat. Keep the dialog's arguments and destination on failure; disable repeated Run/taps while sending. When choosing an existing chat, honor that session's selected model rather than silently applying the profile default.
- Minimal verification: One first-command flow covering creation → command → navigation, and one failed submission showing arguments retained and no duplicate dispatch. Existing slash-command behavior is covered in `test/chat_live_events_test.dart` around the “typed server command passes arguments and selected model” case. No build or emulator pass is needed for the catalog-only change.

## FE-006 — Report failed refreshes while keeping useful catalog rows

Status: **Ready** · Priority: **High**

- Trigger and impact: Load Commands, Skills, References, or Terminal, then pull to refresh after the server becomes unavailable. Each screen records an error but renders it only when its list is null. Previously loaded rows remain with no indication that refresh failed, so terminal status and available commands appear current. The same suppression happens after refreshing a successfully loaded empty list.
- Location: `lib/ui/screens/library/commands_screen.dart`, `_load`/`build`; `lib/ui/screens/library/skills_screen.dart`, `_load`/`_body`; `lib/ui/screens/library/references_screen.dart`, `_load`/`_body`; `lib/ui/screens/terminal_screen.dart`, `_TerminalScreenState._load`/`build`.
- Implementation: Keep the previous list and scroll position, but show a compact inline refresh-failed message with Retry whenever an error accompanies loaded data, including an empty list. Clear the notice after successful refresh. Guard overlapping loads or reuse a generation token so an older response cannot overwrite a later refresh. Reuse the existing ProductErrorState only for a first load that has no data.
- Minimal verification: Reuse the fake repositories in `test/library_skills_test.dart` and `test/product_ui_regression_test.dart`; successful load → failing refresh retains rows and shows Retry → successful retry clears the notice. A representative shared pattern check is sufficient; no golden refresh campaign.

## FE-007 — Make Default shell's retry action actually retry

Status: **Ready** · Priority: **Medium**

- Trigger and impact: Load Coding defaults successfully, then let the automatic resume refresh fail. The Default shell row says “Tap to retry,” but its `onTap` always calls `_chooseShell`. Since cached settings are non-null, `_chooseShell` opens old choices instead of retrying the failed request.
- Location: `lib/ui/screens/settings/coding_settings_screen.dart`, `_CodingSettingsScreenState._chooseShell` and `default-shell-settings-entry.onTap`.
- Implementation: When `_shellError` is present, route the row action to `_loadShellSettings`; only show the chooser after a successful load. Preserve the selected shell while displaying the failure and loading indicator.
- Minimal verification: Extend the existing shell settings harness in `test/settings_server_updates_test.dart`: initial success → failed reload → tap error row calls the loader and does not open the chooser with stale choices. The v2 capability gate remains covered by `test/v2_feature_gating_test.dart`.

## FE-008 — Keep Commands & tools tab names readable on small screens

Status: **Ready** · Priority: **Medium**

- Trigger and impact: Open Commands & tools on a narrow phone or with enlarged text. `CapabilitiesScreen` assigns four long labels to the default equal-width, non-scrollable TabBar. “Commands” and “References” must fit into roughly a quarter of the viewport minus tab padding; the navigation loses readable labels at compact sizes. This is a code-layout finding, not a newly captured screenshot.
- Location: `lib/ui/screens/capabilities_screen.dart`, `CapabilitiesScreen.build` → `AppBar.bottom` → `TabBar`.
- Implementation: Use a horizontally scrollable, start-aligned tab bar when compact or when text scaling needs it, retaining the existing tabs and capability-gated Tools omission. Avoid abbreviations that make the destinations harder to understand.
- Minimal verification: Use the existing `CapabilitiesScreen` harness in `test/v2_feature_gating_test.dart` at 320dp and enlarged text; each label remains fully reachable and switching to References still selects its content. This is a small layout check, not a new golden suite.
