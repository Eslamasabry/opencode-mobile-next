# FG6: Settings and Library with fewer pages (2026-10-08)

Finish line: fewer Settings and Library pages, moving the app toward
`docs/ux-system/target-ia.md` §1.3 (Settings IA), §1.4 (page list after merges)
and §2 (canonical paths). Non-goals: anything outside Settings and Library, new
features, changing what a setting does, removing a capability. Every setting
stays reachable; only where it lives changes.

Branch `fe/fewer-pages`, worktree `/home/eslam/Storage/Code/oc_app-fe-fewer-pages`,
from `feat/genui-fe` at 6a7589446.

## 1. What is already done (not counted)

The earlier revamp (P3.10, P3.11, P7) already did most of §1.3/§1.4 for this
area, so the audit found these **already in the target shape**:

| Target merge (§1.4) | State today |
|---|---|
| settings-transcript-display-sheet → settings | two switches on the hub (`settings-show-reasoning`, `settings-show-timestamps`) |
| about-privacy-tab → privacy-settings | Privacy and data holds the policy; About has no tabs |
| appearance-picker-sheet → inline control | gone; Appearance is one page |
| plugins-settings → capabilities | page gone; the server's plugin inventory is a section of Settings › This server |
| import conversation → Work | `library-import-session` is a Work destination |
| usage + provider-quota → usage-hub | one Usage page, Spent · Remaining tabs |
| notifications, keep-running, automation | one page, sections (fewer than the target's three) |
| integrations-oauth-code ↔ mcp-oauth-code, forget-uncertain ↔ forget-pending, command-auth confirm, skills preview, quota enrol | shared dialogs/sheets already |
| hub: 5 groups, at most 5 rows each, "N settings need a newer server · Why" | in place (20 rows) |

## 2. Settings and Library pages today (screens a person can land on)

"Page" = a pushed full screen or a tab destination. Sheets and dialogs are not
counted. Pages that belong to other programmes (Servers, This phone, AI Team,
Work, Project) are listed in section 5 and not touched.

| # | Page (screen file) | What is on it | How you reach it |
|---|---|---|---|
| 1 | settings (`settings_screen.dart`) | the hub: 5 groups | dock tab 4, `$mod+,` |
| 2 | server-settings (`settings/server_settings_screen.dart`) | health, sign-in, updates, plugins, Disconnect | hub › This server |
| 3 | ai-setup (`settings/ai_setup_screen.dart`) | review of the server's config | This server › AI setup |
| 4 | host-management (`host_management_screen.dart`) | Linux service on a remote host | This server › row |
| 5 | agents-screen (`agents/agents_screen.dart`) | Claude Code and friends on this phone | hub › Agents on this phone |
| 6 | integrations, Providers mode (`library/integrations_*.dart`) | provider sign-ins | hub › Providers; model picker; chat errors |
| 7 | agent-account (`agent_account_screen.dart`) | the Codex account | hub › Providers on Codex |
| 8 | **tools-hub** (`tools_hub_screen.dart`) | 3 rows: MCP, Commands & tools, External agents; unavailable line | hub › Tools |
| 9 | **integrations, MCP mode** | MCP servers, resources, Add | Tools hub › MCP; `/mcps`; enable-flow `mcpAdd`; search |
| 10 | mcp-catalog (`mcp_catalog_screen.dart`) | registry servers | MCP › Add sheet › Browse |
| 11 | mcp-setup (`mcp_setup_screen.dart`) | manual MCP form | MCP › Add sheet › Enter manually |
| 12 | **capabilities** "Commands & tools" (`capabilities_screen.dart`) | 4 tabs: Commands · Tools · Skills · References | Tools hub › Commands & tools; `/tools`; search |
| 13 | **external-agents** (`external_agents_screen.dart`) | saved A2A agents, urgency order, Add | Tools hub › External agents; Servers; enable-flow `addExternalAgent` |
| 14 | add-agent (same file) | add an A2A agent | External agents › Add |
| 15 | external-agent-detail (same file) | one agent | External agents › row |
| 16 | external-task (same file) | one task | agent detail › task |
| 17 | notifications-settings (`settings/notifications_settings_screen.dart`) | notify, keep running, what runs by itself | hub › Notifications |
| 18 | saved-permissions (`saved_permissions_screen.dart`) | always-allowed actions | Notifications › What runs by itself |
| 19 | appearance-settings (`settings/personal_settings_screens.dart`) | theme, effects, language | hub › Appearance |
| 20 | privacy-settings (same file) | local data sizes, policy | hub › Privacy and data |
| 21 | usage-hub (`usage_hub_screen.dart`) | Spent · Remaining | hub › Usage |
| 22 | guide (`guide_screen.dart`) | setup guide | hub › Setup guide, `/guide` |
| 23 | demo (`demo_screen.dart`) | offline demo | guide › Try it |
| 24 | app-diagnostics (`app_diagnostics_screen.dart`) | Report a problem | hub › Report a problem, `/debug` |
| 25 | server-capabilities (`server_capabilities_screen.dart`) | Available on this server | hub › Available…, the "Why" lines |
| 26 | about (`about_screen.dart`) | build, tips, open source | hub › About, `/about` |
| 27 | **voice-notices** (`voice/notices.dart` `VoiceNoticesPage`) | 4 voice-component licences | About › Voice licences |

**27 pages.** Skills and References also open on their own from a conversation
(`/skills`, attach a reference) as pickers; those entries stay.

## 3. Plan: current page to target home

| Current page | Target home (target-ia) | Decision |
|---|---|---|
| tools-hub | `capabilities` renamed Tools (§1.3 row 7: "MCP, Commands, Skills, Plugins, Outside agents, Tools and references"; the app's word for Outside agents is External agents) | **Merged into Tools** (M1) |
| integrations (MCP mode) | Tools › MCP (§1.3 row 7; "integrations keeps only Providers") | **Merged into Tools** as the MCP tab (M1) |
| external-agents | Tools › External agents tab (§1.4: "external-agents goes to Tools") | **Merged into Tools** as a tab (M1) |
| capabilities (Commands & tools) | Tools | **Stays**, becomes the Tools page with 6 tabs (M1) |
| voice-notices | `about-open-source-tab` (§1.4: "voice-notices merged into about-open-source-tab"; §1.3 row 22 "Open source including the voice licences") | **Merged into About** (M2) |
| add-agent, external-agent-detail, external-task | stay (§1.4 survivors) | stays, reached from the External agents tab |
| mcp-catalog, mcp-setup | stay (§1.4 survivors) | stays, reached from MCP › Add |
| all other rows in section 2 | survivors in §1.4 | stays |

Merges built in phase 2, one commit each:

- **M1 Tools is one page.** Settings › Tools opens the former Commands & tools
  page, now titled Tools, with tabs **MCP · Commands · Tools · Skills ·
  References · External agents** (the strip scrolls, up to 8 tabs; tabs are
  built on first visit). Where the server shares no catalog (Codex, Paseo, no
  saved server) the five catalog tabs are absent and only External agents
  shows, with the hub's one muted line "N settings aren't available on this
  server · Why". The Tools hub page is deleted. `/mcps`, the `mcpAdd` and
  `addExternalAgent` enable-flows, the Servers door to External agents and the
  search entries (`settings-mcp`, `settings-commands-tools`,
  `settings-external-agents`) all land on the Tools page on the right tab.
  The chat's "providers" error card stops opening the combined
  "MCP and integrations" page and opens Providers like `/integrations` does.
- **M2 Voice licences live in About.** The four voice-component rows become a
  group under About › Open source; the Voice licences page is deleted. Each
  row still opens the licence text in the kit viewer.

## 4. Page count and setting paths

Before: **27**. After: **23** (Tools hub, the standalone MCP page, the
standalone External agents list and Voice licences are gone; Commands & tools
is now Tools). Depth: the Tools jobs go from hub › Tools › row › page (3
taps) to hub › Tools › tab (2 taps).

| Setting / job | Old path | New path |
|---|---|---|
| MCP servers, resources | Settings › Tools › MCP servers (page) | Settings › Tools › MCP tab |
| Add an MCP server | MCP page › Add (top bar) | Tools › MCP tab › Add MCP server (button at the top of the tab) |
| Commands, Tools, Skills, References | Settings › Tools › Commands & tools › tab | Settings › Tools › the same tab |
| External agents (A2A) list | Settings › Tools › External agents (page) | Settings › Tools › External agents tab |
| Add / open an external agent | External agents page › Add / row | External agents tab › Add agent / row (the detail and task pages are unchanged) |
| Voice model licences | Settings › About › Voice licences (page) | Settings › About › Open source › the four voice rows |

Search entry ids do not change (`settings-mcp`, `settings-commands-tools`,
`settings-external-agents`, `settings-voice-notices`); only what they open does.

## 5. Needs owner (not built)

1. **One "Tools and references" tab.** §1.3 row 7 writes "Tools and references"
   as one item. The app has two tabs (Tools: the model's tool list; References:
   things you attach). Merging them changes what a tab does. Kept separate.
2. **Plugins in Tools.** §1.3 puts Plugins in Tools; today the server's plugin
   inventory is a section of Settings › This server (decided after the Plugins
   page was retired). Moving it adds a 7th tab and no page. Left in place.
3. **Agents on this phone** (`agents-screen`, hub › Server group). Newer than
   target-ia (2026-10-03); the target keeps Claude Code under This phone
   (`phone-component-sheet`). Merging it into This phone is a Servers/This
   phone decision, outside this slice.
4. **AI setup** (`ai-setup` under This server). Added after target-ia
   (review-only, 2026-09-28); the target has the "Ask the setup assistant" row
   at the top of the hub instead. Needs a decision on where the review lives
   once the assistant exists.
5. **Notifications, Keep running, What runs by itself.** The target has three
   pages (§1.3 rows 9, 14, 15); the app already has one page with three
   sections. That is fewer pages, so it was left. Say if the target's split is
   still wanted.
6. **Host management** (`host-management`, This server). Survives in the
   target under Servers and This phone; not Settings/Library.
7. **One word: Outside or External agents.** target-ia writes "Outside
   agents"; the tab, the page, search and the settings aliases say "External
   agents", while the empty state already says "outside agent". Which word
   wins is a copy decision across both languages. Kept as External agents.
8. **Hub row count.** 20 rows against the target's 22: the two missing are
   "Ask the setup assistant" (a feature) and the separate "What runs by itself"
   row (see 5). Neither is a page merge.

Also outside the slice and untouched: Servers, This phone, Tailscale, the AI
Team pages, Work, Project.

## 6. Risks

- **Test churn.** Files that reference the moved pages: `settings_hub_test`,
  `tools_screen_test`, `search_index_test`, `first_run_settings_doors_test`,
  `v2_feature_gating_test`, `external_agent_widget_test`,
  `library_integrations_test`, `revamp/screen_library_{1,2,3}_test` and their
  goldens, `revamp/slice_p310_settings_ia_golden_test`,
  `revamp/slice_p10_1_2_test`, `revamp/screen_voice_1_*`, `voice_composer_test`,
  `voice_model_manifest_test`, `chat_live_events/commands_timeline_tests`.
  `IntegrationsScreen` and `ExternalAgentsScreen` keep their standalone
  behaviour (a new `embedded` flag), so tests that pump them directly stay valid.
- **Six tabs.** The strip scrolls and the tab labels are short; check 200 %
  text and Arabic in the goldens. Tabs build lazily so opening Tools does not
  start MCP and A2A reads that nobody asked for.
- **State kept across tabs.** MCP sign-in in progress, the external-agent
  store and a half-typed search must survive a tab switch; the kit switcher
  keeps visited tabs alive. The external-agent store is created when the tab
  is first opened and disposed with the page.
- **Top-bar actions.** The MCP "Add" and the agents "Add agent" lived in the
  top bar; in a tab they become a visible button at the top of the tab
  (obvious options first), not an overflow item.
- **Codex / Paseo / no server.** External agents must still work with no
  catalog, as the Tools hub did; the muted "N settings aren't available" line
  keeps its Why.
- **UI ledger.** `test/ui_ledger_coverage_test.dart` fails if a deleted screen
  file stays in the ledger; the ledger parts and generated `ledger.json` are
  updated with each merge.
- **Deep links.** No named route points at the deleted pages. Pushed-route
  callers (`/mcps`, enable-flows, Servers, search) are re-pointed in the same
  commit as the merge.
- **Shared-library ownership.** M1 touches one line each in
  `chat/chat_command_actions.dart` and `chat/chat_notices.dart` (chat library)
  and `servers/servers_actions.dart`, only to re-point a push; nothing else in
  those libraries changes.
