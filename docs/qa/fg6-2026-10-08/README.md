# FG6: Settings and Library with fewer pages (2026-10-08)

Branch `fe/fewer-pages` (from `feat/genui-fe` at 6a7589446). Plan and the
list of what needs the owner: `docs/design/fg6-fewer-pages-plan.md`.

Status: implemented, tested locally, committed locally. Not enabled behind a
flag (these are plain UI moves), not on a device, not pushed, not released.

## Page count

Pages under Settings and Library a person can land on (pushed screens and
tab bodies; sheets and dialogs are not counted). Servers, This phone, AI
Team, Work and Project pages are not in this count.

| | Before | After |
|---|---|---|
| Settings and Library pages | 27 | 23 |

Gone: the Tools hub (3 rows), the standalone MCP servers page, the standalone
External agents list, the Voice licenses page. Commands & tools is now Tools.
The ledger (`docs/design/ui-ledger`) went from 310 to 308 pages. The Settings
hub itself is byte-for-byte unchanged (same rows, same places).

## Where each moved setting lives

| Setting / job | Old path | New path |
|---|---|---|
| MCP servers and resources | Settings › Tools › MCP (page) | Settings › Tools › MCP tab (first tab) |
| Add an MCP server | MCP page › Add (top bar) | Tools › MCP tab › Add MCP server (button at the head of the list) |
| Commands, Tools, Skills, References | Settings › Tools › Commands & tools › tab | Settings › Tools › the same tab |
| External agents list (A2A) | Settings › Tools › External agents (page) | Settings › Tools › External agents tab (last tab) |
| Add an external agent | External agents page › Add (top bar) | External agents tab › Add agent (button at the head of the list) |
| External agent's page and task | External agents › row | unchanged (opened from the tab's row) |
| Voice model licenses | Settings › About › Voice licenses and provenance (page) | Settings › About › Voice licenses (group below Open source, four rows) |

Other doors that now land on the Tools page on the right tab: the `/mcps`
command (MCP), `/tools` (Tools), the enable flows `mcpAdd` (MCP) and
`addExternalAgent` (External agents), the Servers "External agents" door,
and Settings search (`settings-mcp`, `settings-commands-tools`,
`inside-capabilities-*`, `settings-external-agents`, `settings-voice-notices`;
search ids did not change). The chat's provider error card now opens
Providers, as `/integrations` does, instead of the combined MCP and
integrations page.

Without a shared catalog (Codex, Paseo, no saved server) the Tools page
holds External agents alone with the hub's one muted line ("2 settings aren't
available on this server · Why"), as the old Tools hub did.

## Goldens (phone 412 px wide, dark and light)

Before, rendered at the baseline commit (`before/`), and after, from
`test/revamp/fg6_fewer_pages_golden_test.dart` (`after/` are copies of
`test/revamp/goldens/fg6_*.png`):

| What | Before | After |
|---|---|---|
| Settings hub | `before/settings_hub_{dark,light}.png` | `after/settings_hub_{dark,light}.png` (identical) |
| Tools hub (3 rows) | `before/tools_hub_{dark,light}.png` | gone |
| MCP servers page | `before/mcp_page_{dark,light}.png` | `after/tools_mcp_{dark,light}.png` |
| Commands & tools page | `before/commands_and_tools_{dark,light}.png` | `after/tools_commands_{dark,light}.png` |
| External agents page | `before/external_agents_{dark,light}.png` | `after/tools_external_agents_{dark,light}.png` |
| Tools on Codex | `before/tools_hub_codex_{dark,light}.png` | `after/tools_codex_{dark,light}.png` |
| About + Voice licenses page | `before/about_{dark,light}.png`, `before/voice_licences_{dark,light}.png` | `after/about_loaded_{dark,light}.png` |

All paths are under `docs/qa/fg6-2026-10-08/`. Existing goldens of the pages
that changed were regenerated and looked at: `library2_capabilities_*`,
`p310_tools_*`, `p310_about_loaded_dark`, `system_about_*`, `voice_notices_*`.

## Checks run (local, through `tool/qa/machine_lock.sh`, pinned Flutter 3.47.1)

- `flutter analyze --no-pub`: no issues.
- Focused tests, all passing: `settings_hub_test`, `tools_screen_test`,
  `search_index_test`, `first_run_settings_doors_test`,
  `v2_feature_gating_test`, `external_agent_widget_test`,
  `library_integrations_test`, `revamp/screen_library_{1,2,3}_test`,
  `revamp/slice_p10_1_2_test`, `revamp/screen_voice_1_test`,
  `voice_composer_test`, `voice_model_manifest_test`,
  `codex_chat_capabilities_test`, `chat_free_model_note_test`,
  `chat_server_state_ui_test`, `integration_auth_recovery_test`,
  `r16_says_things_once_test`, `this_phone_speed_test`,
  `e7_library_layout_test`, `revamp/slice_provider_truth_test`,
  `revamp/screen_servers_1_test`, `about_alpha_notice_test`,
  `ios_remote_platform_gating_test`, `release_blockers_test`,
  `gesture_audit_test`, `chat_live_events_test` (the `/status`, `/connect`,
  `/mcps`, `/tools` destination tests), and the golden files
  `fg6_fewer_pages`, `slice_p310_settings_ia`, `screen_voice_1`,
  `screen_system_1`, `screen_library_{1,2}`.
- Gates: `kit_ratchet_test`, `ui_glossary_test`, `golden_harness_test`,
  `design_standard_test`, `l10n_coverage_test` pass.
- Not a regression of this slice: `ui_ledger_coverage_test` › "every screen
  file has a home in the ledger" fails on four diagnostics files
  (`crash_report_share.dart`, `crash_reports_section.dart`,
  `exit_history_section.dart`, `recent_error_words.dart`) that were never
  added to the ledger; it fails the same way at the base commit.
- No ARB change, so no `gen-l10n` run. Unused now (left alone to avoid
  generated-file churn): `toolsHubMcpSubtitle`, `toolsHubCatalogSubtitle`,
  `toolsHubExternalAgentsSubtitle`, `e7SettingsUi95`.
- The full serial suite was not run (focused checks only, as asked).
