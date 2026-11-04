# Inventory UI-B — Flutter file → native map (247 files assigned; 248 listed)

Machine-readable record: [`ui-b.jsonl`](ui-b.jsonl) (one line per Flutter file; keys `file, lines, kind, summary, native, status, gaps, portability, target_module, catalog_rows, untested`). Base: `native/p12-integrate-2026-11-03` at `71f74de8e`. Analysis only, no product code changed.

Method: each file read; native counterparts found by grepping `native/**` for class names, strings, routes and domain words; reachability checked against `*Entries.kt`, `ui/routes` and `androidApp/graph/*Wiring.kt`. Catalog rows start from Flutter tests that import the file, share its test name, or name its classes, then were trimmed/extended per file by behaviour text. "done" needs named native files; doubt means "partial".

## Totals by status

| status | files | Flutter lines |
|---|---:|---:|
| done | 9 | 2214 |
| partial | 112 | 42587 |
| missing | 58 | 22847 |
| not-needed | 1 | 400 |
| after-switch | 65 | 15204 |
| dropped | 3 | 2422 |
| **total** | **248** | **85674** |

## Status by kind

| kind | done | partial | missing | not-needed | after-switch | dropped | total |
|---|---:|---:|---:|---:|---:|---:|---:|
| domain | 1 | 1 | 1 | 0 | 0 | 0 | 3 |
| kit | 1 | 9 | 7 | 0 | 37 | 0 | 54 |
| other | 1 | 9 | 8 | 1 | 3 | 0 | 22 |
| screen | 6 | 51 | 27 | 0 | 18 | 3 | 105 |
| state | 0 | 4 | 1 | 0 | 0 | 0 | 5 |
| widget | 0 | 38 | 14 | 0 | 7 | 0 | 59 |

## Status by target module

| target module | done | partial | missing | other |
|---|---:|---:|---:|---:|
| `:feature:chat` | 3 | 43 | 6 | 2 |
| `:engine:integrations` | 0 | 1 | 15 | 0 |
| `:ui:kit` | 1 | 8 | 6 | 22 |
| `:feature:chats` | 0 | 9 | 3 | 0 |
| `:feature:linux` | 1 | 7 | 5 | 0 |
| `:feature:agents` | 0 | 9 | 2 | 0 |
| `:feature:phone` | 0 | 1 | 7 | 0 |
| `:feature:settings` | 0 | 5 | 1 | 0 |
| `:feature:files` | 0 | 6 | 0 | 0 |
| `:feature:preview` | 0 | 1 | 3 | 0 |
| `:engine:transcript` | 0 | 3 | 0 | 0 |
| `:feature:voice` | 0 | 2 | 1 | 0 |
| `:feature:onboarding` | 0 | 0 | 3 | 0 |
| `:feature:projects` | 0 | 1 | 2 | 0 |
| `:ui:shell` | 0 | 2 | 0 | 0 |
| `:feature:chat-pages` | 0 | 2 | 0 | 0 |
| `:feature:prompts` | 0 | 2 | 0 | 0 |
| `:engine:diagnostics` | 0 | 2 | 0 | 0 |
| `:ui:kit-content` | 0 | 0 | 1 | 0 |
| `:ui:words` | 0 | 1 | 0 | 0 |
| `:ui:routes` | 0 | 1 | 0 | 0 |
| `:engine:entry` | 0 | 1 | 0 | 0 |
| `:feature:about` | 0 | 1 | 0 | 0 |
| `:engine:agents` | 0 | 1 | 0 | 0 |
| `:engine:delivery` | 1 | 1 | 0 | 0 |
| `:engine:composer` | 0 | 1 | 0 | 0 |
| `:feature:servers` | 1 | 0 | 1 | 0 |
| `:engine:project` | 0 | 0 | 1 | 0 |
| `:engine:runtime` | 0 | 0 | 1 | 0 |
| `:engine:attention` | 0 | 1 | 0 | 0 |
| `:feature:team` | 0 | 0 | 0 | 21 |
| `:engine:requests` | 2 | 0 | 0 | 0 |
| `none` | 0 | 0 | 0 | 1 |
| `:feature:memory` | 0 | 0 | 0 | 21 |
| `:android:termux` | 0 | 0 | 0 | 2 |

## The 20 biggest missing / partial files

1. **`lib/ui/screens/global_sessions_screen.dart`** (1188 lines, partial, → `:feature:chats`) — All conversations: every conversation searchable with a project filter, Active/Archived, grouped per project, with row menus (move, copy folder, handoff, open related).
   - no move-to-project, copy folder, copy handoff, open related or import a conversation acts
   - not grouped per project panel (flat paged list)
2. **`lib/ui/screens/development_services_screen.dart`** (1186 lines, missing, → `:feature:preview`) — Development services: the project's saved dev commands and preview links with Start/Stop, Restart, Forget, Remove with Undo, logs, and an add form with drafts.
   - no development services screen, store, route or gateway
   - no service editor with persisted drafts
   - failed-service report attachment (B-NAV-015)
3. **`lib/ui/screens/mcp_setup_screen.dart`** (1029 lines, missing, → `:engine:integrations`) — Manual MCP server form: scope, name, address/command, secret headers, Advanced; save/failed/reconnect/location-changed/unavailable/discard states; prefilled from the catalogue.
   - no MCP setup form
   - no header-secret redaction path
   - McpServerDraft writes not implemented
4. **`lib/ui/screens/agents/agent_sheet.dart`** (1005 lines, partial, → `:feature:agents`) — The one agent sheet: choose an agent and, in place, the steps that make it ready (install with progress, phone check, sign-in, resume), plus signed-in account, Sign in again, Sign out (verified by status check) and Remove with freed size.
   - engine rules exist but no agent sheet UI (choose/install/phone check/sign-in/sign-out/remove)
   - no per-agent row actions in AgentsScreen (rows are title + status only)
   - phone agents not wired: AgentsWiring binds NoPhoneAgents (built-in Linux not in APK, P12-FIX A5)
5. **`lib/ui/screens/chats/chats_home_screen.dart`** (1003 lines, partial, → `:feature:chats`) — The Chats home: every conversation grouped Needs you / Today / Earlier with project labels, Running and project filters, sources, empty/no-match/unreachable states and New chat.
   - no "Show conversations from" sources sheet (per-connection switches)
   - project filter is recent-project chips only: no filter sheet with All projects / Other folders / per-project counts / Open a project
   - no unreachable-server banner with Retry naming the servers (only an incomplete line)
   - no resume notice ("Start new conversation" replacing an old agent chat)
   - Needs you is a filter toggle, not the Inbox door as in Flutter
   - no project-empty state with "Start chat in <project>" and no "Connect a computer" empty action
   - … 1 more in the jsonl
6. **`lib/ui/screens/library/integration_tiles.dart`** (900 lines, missing, → `:engine:integrations`) — Rows for providers and MCP servers: worded state leading the line, urgency order, needs-you mark, one state action ("Sign in to github"), Disconnect/Remove menu, pending MCP OAuth.
   - no provider/MCP tiles
   - no MCP urgency ordering
7. **`lib/ui/kit/terminal_key_bar.dart`** (863 lines, missing, → `:ui:kit-content`) — Terminal extra-keys bar (Esc, Tab, sticky Ctrl/Alt, arrows, one-tap Ctrl-C/Ctrl-D) and the rules turning typed text into control bytes.
   - no terminal key bar in :ui:kit-content (KitTerminalView has only the live grid and accessible send field)
   - Ctrl/Alt modifier byte rules (B-LINUX-224) absent
   - Ctrl-C/Ctrl-D one-tap keys (B-XPLAT-011) absent
   - no native local terminal screen hosts it (feature/linux has no terminal page; only agents SignInTerminalScreen)
8. **`lib/ui/kit/motion/kit_tab_switcher.dart`** (851 lines, partial, → `:ui:kit`) — Tab strip with counts and needs-you marker, overflow scroll, keyboard arrows, and kept-alive bodies switched with fade-through and a top fade mask.
   - no native feature screen uses KitTabSwitcher/KitTabStrip (not reachable)
   - top fade mask under the strip (_KitTopFade) not evident natively
9. **`lib/ui/screens/chat_screen.dart`** (847 lines, partial, → `:feature:chat`) — The conversation screen host: header, transcript, composer, requests, voice, find, drafts and every chat part file, wired to the connection.
   - empty-chat starters, phrase chips, nudges, timeline, prompt editor and history not in the native screen
   - background tasks chip (B-CHAT-007/008) and skill conversations (B-CHAT-108) missing
   - web search review flow (B-CHAT-124) blocked at protocol
   - AI Team watch mode after-switch
10. **`lib/ui/screens/chat/composer.dart`** (824 lines, partial, → `:feature:chat`) — Maps the chat state onto the composer: glass field, model chip (agent, model, thinking, context), attachment chips, '/' command and '@' file suggestions, send/steer, and the '+' tools sheet.
   - subagent '@' mentions not offered
   - attachment chips show names only, no image thumbnails or file preview sheet
   - model picker depth (favourites, cheapest, cycling) incomplete (B-NAV-033/036)
   - IME image commit / keyboard paste not wired
   - full composer module :feature:chat-composer exists but is not wired (not reachable)
11. **`lib/ui/screens/chat/transcript_rows.dart`** (798 lines, partial, → `:feature:chat`) — Transcript rows besides messages: session markers (model/agent/location switched), notices, compaction line, background shell and agent results, OpenCode 2 variant rows, folded work and reasoning.
   - no model/agent/location-switched marker rows (NoticeKind has only instructions, skill, shell, compaction, other)
   - background agent (subagent) result card not drawn
   - tool artifacts download/open not drawn (B-CHAT-114)
12. **`lib/ui/screens/chat/chat_transcript.dart`** (783 lines, partial, → `:feature:chat`) — Draws the chat transcript list with its rows, the live turn line and pace, turn-stall line, per-block open/closed store with 'Collapse all steps', and stop-then-resend for a steer the server cannot take.
   - no 'Collapse all steps' top-bar action / shared expansion store
   - stop-for-steer (Stop, then resend the withdrawn words) not ported
   - live line has no step count ('Step 3 of 5') or in-app team working state
   - session time formatting of rows not shown
13. **`lib/ui/kit/motion/kit_page_transitions.dart`** (764 lines, partial, → `:ui:shell`) — Page transitions: Android predictive-back/fade-forwards on Android, the kit shared-axis/covered-page transition elsewhere and under reduced motion, plus the row-to-page grow origin.
   - row-to-page grow (KitGrow) is a P2 proof wired only for Chats/Projects rows; KitPageOrigin from other rows not ported
   - motion level from system animator scale only (RowTransformGate notes MotionPolicy Full/Calm/Off/Saver not yet the input)
   - parity with Android 16 fade-forwards and reduced-motion close-only fade not evidenced
14. **`lib/ui/screens/chat/message_view.dart`** (763 lines, partial, → `:feature:chat`) — Draws one message (prompt or reply) with kit parts, its long-press/More menu (copy, fork, read aloud, revert, delete) and the recovery actions of a typed assistant error.
   - copy label variants (complete reply / reply so far / loaded reply) missing
   - 'Continue with...' and compact-after-overflow recovery actions not drawn
   - assistant error cards still a status line (B-CHAT-016)
15. **`lib/ui/screens/phone_setup/phone_setup_start_screen.dart`** (762 lines, missing, → `:feature:phone`) — Setup screen A "On this phone": the hero follows what is true (setup running/stopped, Termux, ready), one Set up button with size/time promise, Customize, and other ways (Termux, by address).
   - no setup start screen
   - no setup engine natively (LinuxLifecycle.startSetup only accepts an existing install)
   - This phone offers no first-time Set up
16. **`lib/ui/screens/chat/attention_card.dart`** (753 lines, partial, → `:feature:chat`) — One request card above the composer for a permission, an OC1 question or an OC2 form: who asks and why, the common answer in place, Details, receipt states, how many more wait, bounded height.
   - OC2 form requests: no in-chat form card/sheet (KitFormSheet not used by the chat slot)
   - card height cap to a share of the window not verified
   - request question naming the project folder ('Run npm install in neon-breaker?') not verified
17. **`lib/ui/screens/chat/permission_sheet.dart`** (748 lines, partial, → `:feature:chat`) — Permission request card and details sheet with the in-flight answer ledger (sending, not confirmed, refused), waiting age, Always allow and Undo.
   - full-value details sheet (whole command / every file) not verified natively
   - wide-window detail pane variant not ported
18. **`lib/ui/screens/chat/transcript_turns.dart`** (748 lines, partial, → `:engine:transcript`) — The transcript's turn model: what each server message is (prompt, step, notice), which turn it belongs to, how parts fold under one work line, and each message's meta line.
   - message meta line (time, model) not shown
   - v2 variant tag rows only partly mapped (no switch markers)
19. **`lib/ui/screens/home_screen.dart`** (741 lines, partial, → `:ui:shell`) — Main shell: Conversations home with server pill, search and gear on phones; Conversations/Files/Settings rail or sidebar on wider windows; deep-link and filter landing.
   - no medium/expanded rail or sidebar (KitNavRail exists but unused)
   - legacy inbox/filter links land on plain Conversations without applying the filter (B-NAV-025)
   - desktop shortcuts and scrollbar behaviours not evidenced
   - opening-shell cached rows missing
20. **`lib/ui/screens/isolated_task_sheet.dart`** (725 lines, missing, → `:engine:project`) — "Start in a separate copy": asks for the task, makes a git worktree on its own branch, waits for setup, opens a conversation there and sends the task.
   - no isolated-task sheet or flow (only gateway createWorktree)
   - ready-event wait, failed setup keep, timeout acceptance (B-AGENT-115..117)
   - task field draft per profile

## Gaps no catalog row covers (untested behaviour the catalog missed)

These partial/missing files have **no catalog row** pointing at them, or their main behaviour is marked `untested`. Their gaps would not show up in a catalog-driven parity check, so they need rows (or an owner decision) before the switch.

| file | lines | status | no rows | untested | gaps |
|---|---:|---|---|---|---|
| `lib/ui/screens/global_sessions_screen.dart` | 1188 | partial | yes |  | no move-to-project, copy folder, copy handoff, open related or import a conversation acts; not grouped per project panel (flat paged list) |
| `lib/ui/screens/chat/voice_conversation.dart` | 670 | missing | yes |  | no voice conversation mode natively (only ReadRequest.automatic flag exists); reply watch bound to dispatch id / voice epoch not ported |
| `lib/ui/screens/chat/chat_commands.dart` | 666 | partial |  | yes | no shell dialog (only typed !line); built-in command list missing (see chat_command_actions); connector catalogue enable from command missing |
| `lib/ui/screens/chat/chat_states.dart` | 604 | partial | yes |  | opening excerpt while history loads missing; status line kinds for staged revert, subagent context and sharing missing; agent-named load-error body not verified |
| `lib/ui/screens/chat/chat_command_actions.dart` | 570 | partial |  | yes | only /new(/clear) and /sessions(/resume,/continue) app commands exist; ~38 others missing; no 'Commands and agents' launcher; model/variant cycling and favourites missing |
| `lib/ui/screens/chat/chat_history.dart` | 496 | partial |  | yes | opening excerpt (cached session tail) is not saved or shown; scroll anchor retention when older pages load not verified |
| `lib/ui/screens/chat/chat_scroll.dart` | 417 | partial | yes |  | jump to a message with a lift mark (from timeline/search) only via Find; earlier-messages count not verified |
| `lib/ui/screens/linux_manager/linux_backups_page.dart` | 372 | missing | yes |  | no Backups page, route or Linux home row; no backup/restore job or zip save |
| `lib/ui/screens/chat/chat_page.dart` | 344 | partial | yes | yes | hardware keyboard shortcuts for chat and Find not found in feature/chat |
| `lib/ui/screens/chat/composer_tools.dart` | 339 | partial | yes |  | no '+' sheet with setting rows (Plan progress, Send to background); no attachment thumbnails; returnWithdrawnToDraft (withdrawn prompt back into draft with its files) not ported |
| `lib/ui/screens/linux_manager/linux_service_add_page.dart` | 315 | missing | yes |  | no Services feature natively (no add page, validation or supervisor definitions) |
| `lib/ui/screens/linux_manager/linux_services_page.dart` | 308 | missing | yes |  | no Services list page, route or home row |
| `lib/ui/screens/linux_manager/linux_service_page.dart` | 303 | missing | yes |  | no service page or live service log |
| `lib/ui/screens/files/files_entries.dart` | 292 | partial | yes |  | hidden filtering and load failure exist; no deleted-files-in-place, no reader order; no Report a bug on load failure; no symbol list |
| `lib/ui/screens/library/references_screen.dart` | 272 | missing | yes |  | no references screen; CatalogGateway.references() is a default-unavailable stub |
| `lib/ui/screens/chat/timeline_sheet.dart` | 270 | missing |  | yes | no timeline sheet: the native pill opens Find instead; jump-to-message with carried search and Fork from a timeline row missing |
| `lib/ui/screens/chat/chat_stop_after_step.dart` | 231 | missing | yes |  | PromptDelivery refuses StopMode.AfterThisStep (Unavailable); KitComposerStop stopAfterStep never passed by the chat; no Undo of the steering note, no live-line path, no tip |
| `lib/ui/screens/chat/chat_status_line.dart` | 209 | partial |  | yes | only Offline/Connecting/SendFailed/SendUnconfirmed/RunFailed/ActionFailed/LinkRefused; staged revert, sharing, subagent context and composer notes missing |
| `lib/ui/screens/chats/git_repair_sheet.dart` | 203 | missing |  | yes | no Git repair sheet or repair engine (looking/restoring/asking Termux/checking stages); no Termux-not-allowed / no-answer recovery, no missing-objects outcome; new chat failure offers no Repair action |
| `lib/ui/screens/chat/prompt_editor.dart` | 202 | missing | yes |  | no full-screen prompt editor from the chat composer (chat-composer module not wired, has no editor page); discard-changes confirmation not ported |
| `lib/ui/screens/agents/cards_from_agents_row.dart` | 168 | partial | yes |  | CardsSettingsScreen reachable only from a chat card slot, not from Settings › Agents; per-server rows instead of per-connection row (design check) |
| `lib/ui/screens/crash_report_share.dart` | 168 | partial |  | yes | no share-preview sheet UI; stale-preview rebuild and single share-sheet guard not wired to any screen |
| `lib/ui/screens/inbox/inbox_items.dart` | 164 | partial | yes |  | only a Needs-you filter on Conversations; no cross-connection inbox entries; watched-server (monitor) entries and finished-not-opened list missing |
| `lib/ui/screens/chats/chats_project_sheet.dart` | 160 | partial | yes | yes | no filter sheet: native offers recent-project chips and a separate Projects page; no "Other folders" bucket; no per-project chat/running/needs-you counts or git kind glyph |
| `lib/ui/screens/phone_setup/phone_setup_hero.dart` | 154 | missing | yes |  | no setup hero layout (no setup screens natively); hero drawing is an illustration scene (after-switch) but the layout is needed for setup |
| `lib/ui/screens/agents/agent_model_sheet.dart` | 144 | partial | yes |  | no Choose-a-model sheet for a phone agent; no agentDefaultModelName rule ("Default (Opus)" -> Opus, generic aliases name nothing); no remembered per-agent model names for the row label |
| `lib/ui/screens/chat/stuck_command.dart` | 120 | partial | yes |  | stuck facts never produced: ConversationActivity/LivePhase.Stuck not used by the chat; 'Stop and send' only in unwired :feature:chat-composer (not reachable) |
| `lib/ui/screens/dev_server_logs.dart` | 94 | missing |  | yes | no dev server log sheet or log panel kit part; bounded tail read with control sequences stripped (B-LINUX-064) |
| `lib/ui/screens/chat/sent_photos.dart` | 91 | missing | yes |  | prompt rows carry text only (PromptRow has no attachments): no thumbnails under sent prompts; server file:// pictures not read through the app's file transport |
| `lib/ui/screens/dev_preview_conversation.dart` | 91 | missing | yes |  | preview has no "Ask for a change..." field or conversation binding (P12-PREVIEW-QR §6) |
| `lib/ui/screens/chat/session_sheets.dart` | 86 | partial | yes |  | no tasks chip or Plan row in chat; Tasks page only reachable from Saved prompts; 'Step N of M' under the live line missing |
| `lib/ui/screens/chats/chats_sources_sheet.dart` | 84 | missing |  | yes | no sources sheet; the native feed merges saved connections without a per-source on/off choice; no locked "always" main source, unreachable marker or retry per source |
| `lib/ui/screens/agents/agents_screen.dart` | 63 | partial | yes |  | phone part always shows the unavailable line (phone agents not wired: AgentsWiring binds NoPhoneAgents (built-in Linux not in APK, P12-FIX A5)); no Switch to built-in server action; Cards from agents row not on this page |
| `lib/ui/screens/chat/composer_tray.dart` | 62 | partial |  | yes | Camera (Take photo) hidden: needs FileProvider (B-MEDIA-008); tray drawn as an 'Add to prompt' sheet, no round-button tray or More entry |
| `lib/ui/navigation/chat_route.dart` | 44 | partial | yes | yes | ChatRoute has no landOnRequestID / landOnFailure / menuAction fields; chatRequestArrivalId (land on exact request card) missing |
| `lib/ui/screens/home_opening.dart` | 38 | partial | yes |  | native Opening phase shows skeleton rows, not last time's cached titles; no saved-server notice card with Try again/Details above rows while opening |

36 files, 10002 Flutter lines.

Untested files in any status: 20; files with no catalog row: 58.
