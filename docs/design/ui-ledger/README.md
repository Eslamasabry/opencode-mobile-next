# UI ledger

An inventory of every surface of the app (screens, tabs, sheets, dialogs,
overlays, wizard steps) and every interactive element on it, with each element
mapped to what it does and where it leads. It exists so that later work can
combine, reorganize and simplify the UI from facts instead of from memory.

Source revision: see `generatedFrom` in `ledger.json` (dev at `bfe662c` when
first produced). This is static analysis of the Dart source. No app was run and
no Dart file was changed.

## Files

| File | Role |
|---|---|
| `ledger.json` | Machine-readable source of truth (generated, see below). |
| `pages.md` | Human index: one section per page with its element table, grouped by area. Generated. |
| `navigation.md` | Roots, per-area Mermaid diagrams, inbound/outbound edges and tap depth per page. Generated. |
| `findings.md` | Hand-written observations for a later reorganization, each with `file:line` evidence. |
| `parts/*.json` | The authored input: one file per source area. Edit these, never `ledger.json`. |
| `parts/_assignments.json` | Which source files belong to which part. |
| `parts/_overrides.json` | Coordinator decisions: roots, route-to-page map, page aliases, area overrides, structural edges. |
| `parts/_BRIEF.md` | The extraction rules every part was written against (schema, vocabulary, method). |
| `build_ledger.py` | Merges the parts, resolves cross references, derives inbound edges, renders the Markdown. |
| `../../../tool/qa/check_ui_ledger.py` | Validator (see "Validation"). |

## Shape of `ledger.json`

```
generatedFrom   git revision the ledger was built from
pages[]         id, title, kind, area, file, widget, presentedBy[], line, purpose,
                reachedFrom[], gates[], elements[]  (+ embeddedIn[], hostPages[] on embedded overlays)
  elements[]    id, type, label, key, file, line, action, target, targetWidget,
                effect, gates[], destructive  (+ hostWiring, widgetFile, unresolvedTarget when relevant)
navigation      roots[], edges[] {from, element, to}  (+ via: embedded|state, secondary: true)
notPages[]      source files that hold no surface and no interactive element, with the reason
notes[]         per-part remarks from extraction (dead code, things not traced, judgement calls)
```

Vocabulary:

- `kind`: `screen`, `tab`, `sheet`, `dialog`, `overlay`, `onboarding-step`.
- `type`: `button`, `icon-button`, `fab`, `list-tile`, `menu-item`, `chip`, `toggle`, `text-field`, `slider`, `dropdown`, `tab`, `link`, `gesture`, `card`, `banner`.
- `action`: `navigate`, `open-sheet`, `open-dialog`, `mutate`, `toggle-setting`, `submit`, `copy`, `external-link`, `dismiss`, `other` (`other` = local view state only).

Conventions worth knowing before querying:

- **Labels are the English strings** resolved from `lib/l10n/app_en.arb`. Labels
  in `[square brackets]` describe an element that has no visible text. Patterns
  such as `<session title>` or `{count}` stand for dynamic text.
- **List rows are recorded once** as a template, plus one element per distinct
  trailing action, swipe or long-press.
- **Popup and context menus are not pages.** The opener is one element and each
  entry is a `menu-item` element on the same page.
- **Confirmations are pages.** A destructive button normally has action
  `open-sheet`/`open-dialog` and the confirmation page's confirm button carries
  `mutate` and `destructive: true`. A `destructive: true` + `mutate` element on a
  non-confirmation page therefore means "acts without confirmation".
- **Embedded widgets** (`embedded-*`, kind `overlay`) are reusable widgets with
  their own controls (composer, message view, tool card, team card, banners).
  `embeddedIn` lists the host classes as written by the extractor and
  `hostPages` the resolved page ids. In the graph the host has a zero-tap
  `via: "embedded"` edge to them.
- **`hostWiring: true`** marks an element recorded on a host page (today only
  `chat`) that is the host's callback wiring for a control whose visible widget
  is already recorded on an `embedded-*` page. They carry the real effect and
  target, which the embedded widget cannot know. Count one of the two, not both:
  `pages.md` marks them with `~` and the density figures in `findings.md`
  exclude them.
- **`widgetFile`** appears when the element is an embedded widget used on the
  page: `file`/`line` then point at the use site in the host and `widgetFile`
  at the widget's own source.
- **`system` page** is a pseudo page that holds the non-UI entry points
  (notification taps, deep links, launcher shortcuts, share intents, named
  routes, automatic state-driven presentation). It is never a navigation root.
- **`via: "state"` edges** connect a parent to bodies it swaps in by itself
  (Termux setup phases, the empty-state welcome on Servers, the workspace folder
  chooser). They cost no tap.
- **`secondary: true` edges** come from a page's `reachedFrom` where the opening
  element can lead to several pages (its `target` names only the first).
- Every page has an `area` used for grouping only: `shell`, `chat`,
  `workspace`, `files`, `servers`, `termux`, `team`, `settings`, `onboarding`,
  `misc`.

## How it was produced

1. The source was split into 13 disjoint areas (`parts/_assignments.json`):
   all of `lib/ui/`, plus `lib/main.dart`, `lib/voice/voice_ui.dart`,
   `lib/voice/notices.dart`, `lib/update/*` and `lib/feedback/bug_report.dart`.
   `chat_screen.dart` (7.5k lines) was split in two by line range.
2. One agent per area read its files, grepped for every interaction marker
   (`onPressed`, `onTap`, `onChanged`, `onSelected`, `onLongPress`,
   `onSubmitted`, `onDismissed`, `onRefresh`, `showModalBottomSheet`,
   `showDialog`, `showMenu`, `PopupMenuButton`, `MenuAnchor`,
   `MaterialPageRoute`, `pushNamed`, `Navigator.`, `showConfirmSheet`,
   `launchUrl`, `Clipboard`) and wrote `parts/<area>.json` following
   `parts/_BRIEF.md`: callbacks were read, not inferred from labels; gates are
   quoted as coded; l10n keys were resolved to English.
3. `build_ledger.py` merged the parts: resolved cross-area targets by Dart
   class / `show*` function name (`targetWidget`, `presentedBy`) and by named
   route, merged pages described by two parts, repaired use-site line numbers,
   derived `reachedFrom` from element targets, synthesized the `system` page,
   computed tap depth and rendered `pages.md` and `navigation.md`.

## Original coverage (historical)

These statistics describe the initial inventory. Use the generated `pages.md`
and `navigation.md` for current counts; the September 28 repair is described below.

Covered: every Dart file under `lib/ui/` (170 files), and the UI-bearing files
outside it listed above. 335 pages and 1,720 elements (53 of them `hostWiring`
duplicates), 820 edges.

Known limits:

- **Static reading only.** Runtime-only behaviour (server-provided slash
  commands, server-defined forms, MCP/provider lists, dynamic theme) is recorded
  as a template element, not enumerated.
- **Large rendering files were read selectively.** `tool_card.dart`,
  `markdown.dart` and `form_renderer.dart` were ledgered from their interaction
  hits plus targeted reads, not end to end. In the first half of
  `chat_screen.dart`, lines 1287-2000, 2040-2190 and 2550-2694 (event and history
  plumbing) were skimmed.
- **Two parts (`i1-team-core`, `j2-library`) did not re-run the marker grep as a
  final checklist**; their elements come from a full read.
- **Snackbar actions** are recorded where the extractor saw them (for example
  Undo after archive or after clearing a draft), but plain informational
  snackbars are not pages.
- **`ProductErrorState` / `ProductEmptyState` / confirm-sheet buttons** live in
  shared widgets. They are recorded on `embedded-product-states` and
  `confirm-sheet`, and again on hosts only where the host passes a specific
  callback. `confirm-sheet` is the generic template; concrete confirmations
  are their own pages and do not link to it.
- **Synthesized `system` elements** (those whose id starts with `system-` and
  ends with `-to-<page>`) are built from another page's non-UI trigger text.
  They have no `line`, their `file` defaults to `lib/main.dart`, and the real
  location is quoted in the label. The validator reports them as warnings.
- **Three open-type elements have no target** (validator warnings): the
  Ctrl/Cmd+1..4 destination shortcut (switches tabs), the orphan
  `RunningAgentsStrip` pill, and the unreachable work-sheet "Open session".
- **Keyboard shortcuts**: bindings are recorded on `global-shortcuts`; Ctrl+F
  and Ctrl+K inside chat are inferred from intent names.
- **One embedded host is unresolved**: `embedded-markdown-text` lists
  `desktop_interaction.dart` as a host, which is a selection wrapper and not a
  page.
- **Line numbers** refer to the revision in `generatedFrom`. 621 of 709
  elements with a `ValueKey` have that key within 25 lines of the recorded line;
  the rest are dynamic key patterns.

## Not a page

Files with no surface of their own and no interactive element (also in
`ledger.json` under `notPages`). Those under `lib/ui/screens/`:

- `lib/ui/screens/chat/form_flow.dart`: `presentConnectionForm` is glue that calls `presentForm` from `lib/ui/widgets/form_renderer.dart` (page `form-sheet`) and routes submit/cancel.
- `lib/ui/screens/usage_refresh_slot.dart`: a nonvisual ChangeNotifier holding Usage refresh callbacks.
- `lib/ui/screens/library/pending_auth_recovery.dart`: `_authSourceFor` is a nonvisual equality identity for authentication actions; the sign-in confirmation lives in `integrations_screen.dart`.
- `lib/ui/screens/chats/chats_host.dart`: no surface; the one seam between the Chats screens and the connection (feed source, server pill, opening a conversation, model chip).
- `lib/ui/screens/agents/agents_text.dart`: no surface; the words and glyphs of the agent surfaces.
- `lib/ui/screens/agents/phone_check_view.dart`: no surface of its own; the four phone-check steps drawn inside the agent sheet and Settings.
- `lib/ui/screens/library_screen.dart`: no surface since UX phase 2 (the More tab merged into the Settings hub); it only hosts the library part files and `defaultModelLabel()`.
- `lib/ui/screens/automation_settings_screen.dart`: `AutomationSettingsSection` is the last section of the Notifications and background page (`notifications-settings`); its elements are recorded there.
- `lib/ui/screens/settings/plugins_screen.dart`: AI Team row helpers (`teamPhoneProfile`, `teamRowSubtitle`, `teamErrorReason`) that outlived the retired Plugins page; no surface.
- `lib/ui/screens/settings/server_plugins_section.dart`: `ServerPluginsSection` is the 'Plugins on this server' section of the This server page (`server-settings`).
- `lib/ui/screens/keep_running_screen.dart`: `KeepRunningSection` is the Keep running section of the Notifications and background page (`notifications-settings`); `openKeepRunningScreen` opens that page at it.
- `lib/ui/screens/team/team_page.dart`: `TeamPage` / `openTeamPage`, the one AI Team route (P3.4): it shows `team-home` while the server's team is on and `team-intro` while it is off, following the connection; no surface of its own.
- `lib/ui/screens/team/policy_block.dart`: `TeamPolicyBlock` / `TeamBoundariesRow` are read-only rendering blocks embedded in the run overview and the start-run sheet; no taps.
- `lib/ui/screens/phone_setup/phone_setup_hero.dart`: `PhoneSetupHero` lays out setup start and ready (the drawing at the top, then the state's slots); its actions are the host screens' and are recorded on their pages.
- `lib/ui/screens/phone_setup/phone_setup_routes.dart`: navigation only; opens the phone setup screens and routes a setup notification tap.
- `lib/ui/screens/phone_setup/phone_setup_selection.dart`: pure arithmetic over the setup component registry (selection, totals, size and time text).
- `lib/ui/screens/phone_setup/phone_setup_welcome_entry.dart`: the first-run welcome's status line about an existing setup job, embedded in `servers-welcome`.

- `lib/ui/screens/chat/chat_states.dart`: part of chat_screen.dart (design standard §9 step 5): the chat page's own loading/error states and its one status line, not a surface of their own; shown inline on the chat page.
- `lib/ui/screens/activity/activity_details.dart`: Part file of lib/ui/screens/activity_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/activity/activity_pending.dart`: Part file of lib/ui/screens/activity_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/activity/activity_question_form.dart`: Part file of lib/ui/screens/activity_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/activity/activity_rows.dart`: Part file of lib/ui/screens/activity_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/activity/activity_tiles.dart`: Part file of lib/ui/screens/activity_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_attachments.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_body.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_command_actions.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_commands.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_composer_region.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_drafts.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_files.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_history.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_message_actions.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_notices.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_page.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_queue.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_requests.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_running_work.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_scroll.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/receipt_check.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_send.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_session_actions.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_session_menu.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_start.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_status_line.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_stream.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_top_bar.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_transcript.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/chat_voice.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/composer_tools.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/pending_sends_strip.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/team_agent_conversation.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/team_conversation_actions.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/team_conversation_parts.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/transcript_rows.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/transcript_turns.dart`: Part file of lib/ui/screens/chat_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/files/files_actions.dart`: Part file of lib/ui/screens/files_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/files/files_body.dart`: Part file of lib/ui/screens/files_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/files/files_entries.dart`: Part file of lib/ui/screens/files_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/files/files_loading.dart`: Part file of lib/ui/screens/files_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/files/files_viewer.dart`: Part file of lib/ui/screens/files_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/library/integrations_connect.dart`: Part file of lib/ui/screens/library_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/library/integrations_mcp.dart`: Part file of lib/ui/screens/library_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/library/integrations_page.dart`: Part file of lib/ui/screens/library_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/library/integrations_signin.dart`: Part file of lib/ui/screens/library_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/servers/profile_editor.dart`: Part file of lib/ui/screens/servers_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/servers/profile_editor_fields.dart`: Part file of lib/ui/screens/servers_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/servers/profile_editor_pairing.dart`: Part file of lib/ui/screens/servers_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/servers/profile_editor_probe.dart`: Part file of lib/ui/screens/servers_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/servers/profile_editor_steps.dart`: Part file of lib/ui/screens/servers_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/servers/profile_editor_widgets.dart`: Part file of lib/ui/screens/servers_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/servers/server_rows.dart`: Part file of lib/ui/screens/servers_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/servers/servers_actions.dart`: Part file of lib/ui/screens/servers_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/servers/servers_state.dart`: Part file of lib/ui/screens/servers_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/shared_storage_access_flow.dart`: Flow helper, not a surface of its own: the shared-storage access question (grant, restart prompt) run by Files and Projects before a shared folder opens; its prompts are the sheets and dialogs those screens' entries already list.
- `lib/ui/screens/team/projects/screen/board_timeline.dart`: Part file of lib/ui/screens/team/projects/team_projects_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/team/projects/screen/overview.dart`: Part file of lib/ui/screens/team/projects/team_projects_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/team/projects/screen/overview_helpers.dart`: Part file of lib/ui/screens/team/projects/team_projects_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/team/projects/screen/servers_card.dart`: Part file of lib/ui/screens/team/projects/team_projects_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/terminal/terminal_accessible.dart`: Part file of lib/ui/screens/terminal_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/terminal/terminal_input.dart`: Part file of lib/ui/screens/terminal_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/terminal/terminal_list.dart`: Part file of lib/ui/screens/terminal_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/terminal/terminal_surface.dart`: Part file of lib/ui/screens/terminal_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/workspace/workspace_actions.dart`: Part file of lib/ui/screens/workspace_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/workspace/workspace_build.dart`: Part file of lib/ui/screens/workspace_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/workspace/workspace_load.dart`: Part file of lib/ui/screens/workspace_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/workspace/workspace_sheets.dart`: Part file of lib/ui/screens/workspace_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/workspace/workspace_widgets.dart`: Part file of lib/ui/screens/workspace_screen.dart (same screen, same library): its elements are listed under that screen's ledger entries; no separate surface.
- `lib/ui/screens/chat/empty_chat.dart`: part of chat_screen.dart: ChatStartFacts plus the empty-conversation starters/body shown inline on the chat page before the first message, not a surface of their own.
- `lib/ui/screens/chat/watching.dart`: part of chat_screen.dart (design standard §9 step 5): how the chat page shows a conversation someone else drives (an AI Team worker's own OpenCode session) — the same 'chat' page and route, not a surface of its own. Its status banner and empty state are read-only; its composer (chat-watching-composer), send, receipt and the About <name> action are recorded on the chat page. _openWatchedChild (a child session delegated to, watched the same way) is wired from chat_screen.dart.
- `lib/ui/screens/local_terminal_screen.dart`: LocalTerminalView: the "This phone" source of the Terminal page (terminal_screen.dart, same route), a shell in the built-in Ubuntu with its key bar and the shell list in the top bar's menu; not a page of its own (docs/design/local-terminal-2026-09-24.md).
- `lib/ui/screens/perf_trace_section.dart`: PerfTraceSection is the 'Performance' block of App diagnostics (embedded via lib/ui/screens/app_diagnostics_screen.dart:252, host page app-diagnostics), not a surface of its own; its Copy report and Clear buttons are recorded on app-diagnostics with `file` set here.
- `lib/ui/screens/team/team_needs_you.dart`: Embedded blocks, not pages of their own: TeamNeedsYouCard (one question as a request block) is shown on team-home, where it is also its task's row, and on team-run-overview-tab (run_screen.dart:780); TeamGateRow (a question whose task is not listed) is a row of team-home's one list. Both open the Gate sheet.
- `lib/ui/screens/team/team_states.dart`: Shared helper (design standard §3, §4), not a page: teamScreenState/teamScreenLoading/teamScreenFailed/teamStatusLine render the AI Team screens' connecting/error/stale states and one status line; its retry buttons are recorded as elements of the host pages (team-home, team-run, team-agent, team-agents) with `file` set here.
- `lib/ui/screens/team_conversation/team_conversation.dart`: The routing API for the task conversation, not a page of its own: TeamConversation.open/start push TeamConversationScreen (team-conversation, chat/team_conversation_view.dart). TeamTaskConversationRow (the Overview's 'Open conversation' row) is recorded as team-run-overview-open-conversation on team-run-overview-tab; TeamAgentTranscript and TeamOpenConversationRow are re-exports of chat/team_conversation_view.dart (see team-conversation's own elements and team-agent-output).

Outside `lib/ui/screens/`:

- `lib/feedback/bug_report.dart`: no surface; `openBugReport()` launches a prefilled GitHub issue URL.
- `lib/ui/app_iconography.dart`, `lib/ui/app_theme.dart`, `lib/ui/theme_packs.dart`, `lib/ui/early_l10n.dart`, `lib/ui/permission_presentation.dart`: theme, icon and string helpers.
- `lib/ui/navigation/chat_route.dart`: `ChatRouteArguments` value class.
- `lib/ui/desktop/desktop_interaction.dart`: scroll/selection/cursor behaviour wrappers.
- `lib/ui/widgets/agent_color.dart`, `code_highlight.dart`, `connect_methods.dart`, `connection_failure.dart`, `entrance.dart`, `glass_surface.dart`, `provider_logo.dart`, `relative_time.dart`, `request_routes.dart`, `session_read_state.dart`, `session_title.dart`, `setup_ui_messages.dart`, `team_vocabulary.dart`, `technical_direction.dart`, `transcript_highlight.dart`: pure rendering, formatting or non-visual helpers.

## Validation

```
python3 tool/qa/check_ui_ledger.py
```

checks that the JSON parses; page and element ids are unique; `kind`, `type`
and `action` are in the vocabulary; every `target`, `reachedFrom.page`, root and
edge endpoint is an existing page id and every referenced element belongs to the
page it is attributed to; every `file` exists and every `line` is inside it; and
every file under `lib/ui/screens/` is referenced by a page or element or is
listed above under "Not a page". Warnings list open-type elements without a
resolved target.

## Regenerating and extending

- After a UI change, edit the affected `parts/<area>.json` (follow
  `parts/_BRIEF.md`; ids are stable, so keep existing ids and add new ones),
  then run:

  ```
  python3 docs/design/ui-ledger/build_ledger.py --stats
  python3 tool/qa/check_ui_ledger.py
  ```

  `--stats` prints element density, fan-in per page, orphans, depth and label
  statistics (the raw numbers behind `findings.md`); `--verbose` prints
  unresolved references.
- A new source file: add it to `parts/_assignments.json` and to a part (as a
  page, as an element `file`, or under `notPages`).
- A cross-area target that does not resolve: either give the target page a
  `presentedBy` entry with the function name, or add the name to
  `widgetToPage` in `parts/_overrides.json`.
- To redo an area from scratch, hand `parts/_BRIEF.md` plus the file list from
  `parts/_assignments.json` to an agent and replace that part file.
- `findings.md` is hand-written; re-check its numbers against `--stats` after a
  rebuild.

### September 28 source-anchor repair

The rebuilt inventory has 293 pages and 1,690 elements. The structural validator
reports 0 errors and 52 existing warnings; 97 gestures have 97 audit rows.

The September 27 kit extraction moved controls out of the old screen and
wrapper implementations. The parts now point affected anchors at their current
widget keys or callback wiring (including KitDiffView, KitViewer, KitCodeBlock,
KitDialog, KitConfirmSheet and KitContextRegion). These are source repairs,
not a new end-to-end inventory of every unchanged entry.

Retired surfaces were removed rather than pointed at unrelated lines:

- The Tasks sheet became `_openPlan` navigation within the transcript
  (`chat/session_sheets.dart`).
- Team discovery is now Settings → TeamPage / TeamIntroScreen;
  `team_discover.dart` contains helpers only.
- The agent details sheet became the inline Technical details fold.
- The separate Always allow confirmation dialog became the request sheet's
  inline risky switch, with its scope shown before the choice.
- The separate team project chooser delegates to
  `ProjectFolderActions.openFolder`.

Retired controls were removed: the permission rejection back step and separate
full-diff opener, the code block overflow menu and duplicate wrap action,
manual unified/split selectors (the diff follows available width), the old
agent step groups, and the theme preview's chip/button actions (the whole
preview is now an ignored-pointer image in KitThemePreview). References that
still open an action now point to that action's current host.
