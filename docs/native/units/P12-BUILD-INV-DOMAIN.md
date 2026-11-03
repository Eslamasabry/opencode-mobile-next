VERIFIED: green — docs/native/inventory/domain.jsonl has exactly one valid JSON line for each of the 185 listed `lib/domain/` files. Every cited native path exists and every catalog row id is in `native/catalog/behaviors.jsonl`.

# P12-BUILD-INV-DOMAIN — file inventory of `lib/domain/`

Date: 2026-11-03. Branch `claude/file-inventory-native-parity-puftm3`, based on the P12 integration tip `71f74de8e`.
Unit type: analysis plus a machine-readable record. **No product code changed**; the only files written are:

- `docs/native/inventory/domain.jsonl`: one record per Flutter file, with keys `file, lines, kind, summary, native, status, gaps, portability, target_module, catalog_rows, untested`. Some records also have `notes`, which gives the evidence or the reason for not-needed/dropped/after-switch.
- `docs/native/inventory/domain.md`: totals by status and kind, the 20 biggest missing/partial files, gaps no catalog row covers, and files nothing imports.
- This report.

Shipping state: analysis committed and pushed. No PR, CI run, build or release (none applies to a docs-only change).

## Result

| Status | Files | Dart lines |
|---|---:|---:|
| done | 35 | 8,707 |
| partial | 65 | 13,947 |
| missing | 50 | 7,368 |
| after-switch | 22 | 8,383 |
| dropped | 4 | 631 |
| not-needed | 9 | 482 |

The task listed 184 files, but the list itself has 185 entries; all 185 are recorded.

## Cross-cutting findings for the integrator

These are the gaps behind many partial rows. Several engines are ported and tested but **unreachable** from the app:

1. **Phone agents are not connected to the app.** `androidApp/.../graph/AgentsWiring.kt` binds `AppPhoneAgents` to `NoPhoneAgents`, and `androidApp` does not depend on `:android:agenthost`. The ported auth probe, readiness rows, host, installer, certification, persona and sign-in output are therefore unreachable, and `SignInTerminalScreen` has no route. This blocks about 9 `agent_*` and `phone_agent*` files.
2. **Agent cards can't be answered.** `CardsQualification.OPEN_CODE_2_QUALIFIED = false`, so answer controls never turn on. There is no phone-agent (Paseo) card source. `CardRecovery`/`CardCoverage` are not used.
3. **The return brief never shows.** It is ported in `engine/agents/feed/ReturnBrief.kt`, but the app binds `NoReturnBrief`.
4. **`:engine:attention` holds only `WidgetActionIngress`.** The profile monitor, cross-server attention feed, while-away inbox and per-server notify rules are all missing. `profileAttentionPolling` is false in both gateways.
5. **Queued sends are never reconciled automatically.** Delivery is hard-wired with `AutomationPolicy { false }`.
6. **Diagnostics engines have no screen.** The exit-history and crash engines are not bound to a screen, and there is no crash report preview or share flow.
7. **Modules the plan names that do not exist yet:**
   - `:engine:integrations`: usage subscriptions, provider quota and logos, BYO host, MCP connectors and OAuth.
   - `:protocol:codex`: the Codex account.
   - `:android:termux`: Termux migrate-out.
   - `:engine:project`: listed as Git's target in `scope.jsonl`. Native Git lives in `:engine:git`.
8. **Capability flags hard-coded off,** each blocking a whole family:
   - `agentHandoffFiles`, `sessionImportExport`, `sessionNotes`, `webSearch`, `developmentServices`, `commandReceipts`, `remoteUpgrade`.
   - The setup-config flags.
   - Paseo `backgroundTasksRunning`/`lastTurnFailed`, marked TODO S5-PASEOP1.
9. **Model picker:** no recommendations, no family-then-newest ordering (native sorts alphabetically), no Z.AI/Zhipu merge and no provider marks. `mark_catalog`'s ~150 provider-id marks and their SVGs are not ported.
10. **Safety rules not ported for the staged-revert message count.** `ConversationReview.messagesAfter` lacks the fail-closed boundary recheck, cursor-loop guard and duplicate-id skip (B-CHAT-103/104).

## Scope questions for the coordinator

- **B-PROTO-140** (OpenCode 2 terminal cursor frames) is scoped switch for `:protocol:oc2`. In Flutter it lives only in `pty_terminal_channel.dart`, the browser-mode channel. I recorded that file as **missing** rather than dropped, because native `connectTerminal` is unimplemented.
- **B-ANDR-071, B-SEC-162 and B-STATE-108..110** (session-address links) are scoped switch for `:engine:entry`. A separate `scope.jsonl` reason says session-address links "are off in Flutter and not ported". I recorded `session_address_link.dart` and `session_address_handoff.dart` as dropped. One of the two must be corrected.
- **B-AGENT-232, B-SEC-167 and B-PROTO-108** are still scoped switch, but their Flutter files (`setup_planner.dart`, `sv3_mcp_capabilities.dart`) are imported only by tests. I recorded them as missing. If the features are not wanted, drop the rows.
- Some after-switch/dropped files still carry switch rows (B-AGENT-301/302, B-SEC-176, B-ANDR-107, B-SEC-063). These rows were amended on 2026-11-01 to cover only the profile/migration deletion sweep, so there is no conflict.
- **B-AGENT-322/323** (Claude Agent trial) are dropped, yet `native/engine/agents/.../claudeagent/ClaudeAgentTrial.kt` exists. It may be dead code.

## Method

1. A script took each file's line count, the Flutter tests that import it or are named after it, and the catalog rows whose sources point into those tests.
2. Six analysis agents (agents/models, FactsBook/team, GenUI/chat models, Git/shell/files, server/session, misc) read each Flutter file and searched `native/**` for counterparts. They confirmed production reachability (graph wiring, routes and capability flags), not only engine code or fakes.
3. The agents trimmed the noisy candidate rows to those that exercise the file.
4. A merge script checks keys, enums, the one-line-per-file rule, that native paths exist and that row ids exist. It also adds the 3 top-up rows (B-AGENT-322/323, B-PROTO-140, B-XPLAT-017) whose sources name the file's own test.

"done" requires named native files covering every user-visible rule. Anything uncertain is "partial" with gaps.

## Tests and checks

- JSONL validation (merge script): OK. 185/185 files, all lines parse, no unknown kind/status/portability values, every `native` path exists, every row id is in the catalog, and no done record is missing native evidence.
- No Gradle or Flutter run: this unit changes only docs (AGENTS.md check ladder: docs-only changes need a diff/link check). The Android SDK was not installed for that reason.
- Diff checked for IP addresses and email addresses: none.
