VERIFIED: green — `docs/native/inventory/state.jsonl` covers all 228 listed `lib/state` files (the brief said 227; the list holds 228, and it equals every `.dart` file under `lib/state/`) and every line is valid JSON with the required keys.

# P12-BUILD-INV-STATE — file inventory of `lib/state` (analysis, no port)

Branch `claude/file-inventory-parity-push-9xoap4`, base `native/p12-integrate-2026-11-03` (`71f74de8`). 2026-11-03.

## What was delivered

| File | What it is |
|---|---|
| `docs/native/inventory/state.jsonl` | One JSON line per Flutter file. Keys: `file, lines, kind, summary, native, status, gaps, portability, target_module, catalog_rows, untested`, plus `gaps_uncatalogued` (the gaps that no catalog row covers). |
| `docs/native/inventory/state.md` | Totals by status, kind and target module, the 20 biggest missing or partial files with their gaps, every uncatalogued gap, and the untested files. |

No product code, routes, wiring or strings were changed.

## Result in one table

| Status | Files | Dart lines |
|---|---:|---:|
| done | 25 | 5,722 |
| partial | 94 | 34,801 |
| missing | 59 | 14,830 |
| not-needed | 3 | 467 |
| after-switch | 39 | 11,657 |
| dropped | 8 | 2,667 |

- Of the 189 switch-scope files (everything except after-switch and dropped), only 25 are done.
- 72 files carry gaps that no catalog row covers, so the parity dashboard cannot see them. They are listed in `state.md`.
- 4 files have no Flutter test at all: `connection_check`, `mcp_catalog_host`, `server_presentation` and `browser_secret_storage_io`.
- `catalog_rows`: 673 links to 534 distinct row ids.

## What the integrator must know (largest themes)

1. **Ported but not reachable.** 39 files have engine code that `androidApp` never builds or reaches. The gaps say "not wired", "engine-only" or "never constructed". Examples:
   - `PhoneAgents` is bound to `NoPhoneAgents`, and `:android:agenthost` is not an app dependency. This covers all nine `phone_agents*` files and `local_agent_server`.
   - `FormRequests` is never constructed (`feed_forms`, `forms_inbox`).
   - The consent stores, tips, nudges, phrase chips, `LocalSettingsAccess`, `ReturnBriefStore` (the app uses `NoReturnBrief`), `MobileDownloadConsentStore`, the in-app auto-update rule, `CardCoverage`/`CardRecovery`, and the reply-watch service exist but are never used.
   - Wiring these is the cheapest parity win.
2. **Modules that don't exist yet.** Four target modules hold most of the 59 missing files:
   - `:engine:integrations` (24 files): provider sign-in, MCP, connectors, quota, subscriptions, budgets, pending auth.
   - `:engine:project` (10 files): worktrees, isolated tasks, session notes, dev services.
   - `:protocol:codex`: Codex probe and handoff.
   - `:android:termux`: Termux migrate-out (D7 keeps it).
3. **Attention from other servers.** `:engine:attention` holds only `WidgetActionIngress`. Missing:
   - Polling of saved servers that aren't connected (`profile_monitor`, 1,279 lines).
   - The attention feed's ordering and all-clear rules.
   - Needs-you on Conversations rows (`PreviewRuntime` never fills `needsYou`).
   - Answering requests from the list (`feed_*.dart`).
4. **Persistence that is still memory-only.** Read state, the per-server location and recent locations, the model library and per-session model, session pins (read-only for shortcuts), and the first-paint caches (session tail and session list).
5. **Connection lifecycle.**
   - The event stream is not paused in the background or resumed on return.
   - There is no 15-second quiet reconnect and no retry countdown.
   - There is no "Update password" prompt when the server rejects the password mid-session.
   - The trust probe refuses a confirmed plain-HTTP server, and the certificate pin is not passed to it.

## Journeys / screens

None: no screens were added. `state.jsonl` records how each native counterpart is reached, or that it is not reached, in `native` and `gaps`.

## Catalog rows

Each row in `state.jsonl` lists its own `catalog_rows`. The 72 files with `gaps_uncatalogued` need new catalog rows, because the behaviour catalog was mined from Flutter tests and missed them. The list is in `state.md`, "Gaps not covered by any catalog row".

## Tests and checks run

- A merge script validated the output:
  - Every listed file is present, exactly once.
  - Every `kind`, `status` and `portability` value is allowed.
  - Every `native` path exists in the repo.
  - Every `catalog_rows` id exists in `behaviors.jsonl`.
  - Every `done` row names native files, and every `partial` or `missing` row lists gaps.
  - No IP addresses or emails appear.
  - Result: `OK 228 / 228`.
- No Gradle or Flutter runs. The change is docs-only, so the build cannot change, and per AGENTS.md rule 5 docs-only changes need only a diff check.

## Deviations

- **Android SDK not installed and no Gradle builds.** This unit changes no product code. It is "analysis plus a machine-readable record, not a port", so a build adds no evidence.
- **Extra key `gaps_uncatalogued`.** I added it so `state.md` can list untested behaviour by machine.
- **`kind` is `state` for most files.** All files are under `lib/state`; `platform-bridge`, `background`, `other` and `dead` are used where they fit better.
- **Some `target_module` values name modules that don't exist yet.** These come from scope.jsonl or plan/03: `:engine:integrations`, `:engine:project`, `:protocol:codex`, `:android:termux`, and after the switch `:engine:team` and `:engine:factsbook`.
- **Card files point at `:engine:requests`.** That is their scope.jsonl module, but the native card code lives in `:engine:cards`.
- **Method.** The work ran in seven read-only batches, and each verdict comes from a read of the file plus a search of native production code. Treat a `done` as "evidence named", not "parity-tested on a phone".
