# Phone tools and agent adapters

Date: 2026-10-07 · Code: `lib/domain/agent_tools/agent_tool_adapter.dart` ·
Tests: `test/agent_tool_adapter_test.dart`

Owner request: the phone tools (agent cards, native question UI, photo asks,
and next the agent browser) must be modular and reusable, so any agent can be
adapted quickly.

## Shape

- **Phone tool** (`PhoneTool`): defined once — an app-owned MCP server and its
  tools. Cards are `PhoneTool.cards` (`oc-ui` / `show`). The agent browser
  will be another `PhoneTool`. Its UI is kit-only (`lib/ui/kit/`), shared by
  every agent.
- **Agent adapter** (`AgentToolAdapter`): one per agent, holding only what
  differs between agents:

  | Field | Meaning | Claude Code | OpenCode 1 / 2 | Pi / Oh My Pi |
  | --- | --- | --- | --- | --- |
  | `naming` | MCP tool name in the transcript | `mcp__oc-ui__show` | `oc-ui_show` | none (no MCP) |
  | `config` | where MCP servers are registered | `claude mcp add` (profile) | `mcp.<name>` / `mcp.servers.<name>` | none |
  | `runsAs` | Linux user in the in-app Ubuntu | agent user (uid 1000) | root | agent user |
  | `paseoProvider` | Paseo provider id of its chats | `claude` | — | `pi` / `omp` |
  | `answerKeys` | native question answers keyed by | question text | — | header |
  | `questionToolName`, `planToolName` | its own question / plan tools | `AskUserQuestion`, `ExitPlanMode` | — | any / none |
  | `preAllowsCards` | card tool allowed in its own permission settings | yes | — | — |
  | `cardsQualified` | device evidence that cards work end to end | yes | no (rootfs writable by agents) | — |

- **Registry** (`AgentToolAdapters`): `all`, `withTools`, `byId`,
  `forPaseoProvider` (accepts untrusted daemon values), `forOpenCode`, and
  `cardShowNames`. No other file names an agent for tool purposes:
  card recognition (`gen_ui_codec.dart`), card permission auto-allow and
  native question parsing (`paseo/gateway`), installer paths/users and the
  readiness gate (`builtin/agents/gen_ui_install*.dart`), connection readiness
  (`state/connection/gen_ui.dart`) and Settings names all ask the registry.
- `GenUiAgent` is now a type alias of `AgentToolAdapter`; ids equal the former
  enum names because they name helper folders on disk (`.oc-genui/<id>`).

## Adding an agent (e.g. Codex, Gemini CLI, Qwen Code)

1. Add a `static const` adapter with its `id`, `displayName` and
   `paseoProvider`, and append it to `AgentToolAdapters.all`.
2. Native questions: set `answerKeys` (and `questionToolName` /
   `planToolName` if its requests name them). The question sheet, list
   answers and plan approval then work unchanged.
3. Phone tools: capture one real transcript fixture of an MCP call to learn
   its `naming`. Pick its `config`; if the agent stores MCP servers in a new
   format (Gemini/Qwen `mcpServers` in `settings.json`, Codex
   `[mcp_servers.<name>]` TOML), add one writer for that format to the
   installer with its own tests, then add the enum value.
4. Keep `cardsQualified: false` until the device journey passes (card shown,
   answered from chat and list, receipt, restart) and the run user cannot
   modify the helper it executes. Record the evidence in `docs/qa/`.
5. `test/agent_tool_adapter_test.dart` enforces the contract (unique ids and
   providers, naming with config, format/run-user agreement, qualification
   only with tools).

## Not in this change

The installer writes three formats (`claudeCli`, `openCodeV1`, `openCodeV2`).
New formats are deliberately not written before an agent needs them. The
agent browser will become a second `PhoneTool` through the same adapters once
its gates pass (`docs/verification/agent-browser-b0d-2026-10-07.md`).
