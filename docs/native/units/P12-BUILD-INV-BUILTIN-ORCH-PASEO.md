VERIFIED: green — the JSONL has one valid JSON line for each of the 166 listed files, in list order. Every native path it names exists, and every catalog row id it cites exists in `native/catalog/behaviors.jsonl`.

# P12-BUILD-INV-BUILTIN-ORCH-PASEO — file inventory: builtin, orchestration, paseo

Unit INVENTORY-BUILTIN-ORCH-PASEO, parity push of 2026-11-03. This unit is analysis plus a machine-readable record. **No product code was changed.**

- Record: [`docs/native/inventory/builtin-orch-paseo.jsonl`](../inventory/builtin-orch-paseo.jsonl), one line per Flutter file.
- Summary: [`docs/native/inventory/builtin-orch-paseo.md`](../inventory/builtin-orch-paseo.md). It has totals by status and kind, the 20 biggest missing/partial files, and the gaps no catalog row covers.
- Base: `native/p12-integrate-2026-11-03` at `71f74de8`. Branch: `claude/file-inventory-builtin-orch-paseo-5ahosr`.

## Result

| Status | Files | Lines |
|---|---:|---:|
| done | 8 | 3,191 |
| partial | 52 | 19,016 |
| missing | 47 | 13,731 |
| after-switch | 50 | 14,394 |
| dropped | 3 | 544 |
| not-needed | 6 | 25 |

By area: `lib/builtin` has 3 done, 36 partial, 34 missing. `lib/orchestration` is 43 after-switch and 1 partial; that is AI Team / Gas City under D6. `lib/paseo` has 5 done, 15 partial and 13 missing.

The 8 files that are done:
- the Linux manager's Packages, Ports and Storage pages (`:feature:linux`, routed through `LinuxEntries`);
- Paseo transport, mappers, tested versions, lifecycle/retry, and uncertain-start (`:protocol:paseo`).

## What the integrator should know (the biggest blockers for this area's journeys)

1. **Phone agents are engine-only.** `:android:agenthost` is not a dependency of `native/androidApp`, and `AgentsWiring` binds `NoPhoneAgents`. This affects 9 Flutter files of agent host, scripts, sign-in and cleanup. The agent install, removal and Paseo shell scripts have not been ported.
2. **The phone setup engine has not been ported.** That covers `setup_engine` and its 4 parts, plus `components`, `setup_contract`, `phone_setup` and `preflight`. Today `LinuxVoiceWiring.startSetup` fails with NotInstalled, and update or repair returns ServerStartUnavailable. Reset Linux can erase Linux but cannot reinstall it.
3. **The phone-server lifecycle is half wired.** `AppOpenStart`, `DeliberateStop`, the recovery confirm (stubbed to Ok), idle resume and the exit notice exist as engines or not at all, but no wiring uses them.
4. **The heat and budget engines are never constructed.** These are `ThermalPolicy`, `HeatTierPolicy`/`AgentThrottle`, `ServicesHeatHold` and `ProcessBudgetGuard`. "Running on this phone" is bound to an empty list.
5. **`PaseoGateway` has 4 of the 15 facets** the Flutter gateway has. It has no `ServerOperationsGateway` (files, diff, worktrees, terminals, sub-agents, commands). `PaseoChatFeed`, `PaseoIdleGuard`, the host-agent catalog and ACP permissions exist, but nothing in the app calls them.
6. **Modules that are named as owners but do not exist yet:**
   - `:android:termux`. D7 says Termux move-out ships at the switch, and all 6 migration files are missing.
   - `:android:terminal`. There is no PTY, so the sign-in terminal cannot run.
   - `:engine:integrations`. This blocks the quota collector and connector search.
   - `:engine:project`. This blocks git link repair, project export and the on-phone git shell.
7. **Linux services and backups are missing:** `oc-svd`, the service files, the backup/restore zip, and vitals.

## Catalog rows

Every file lists its `catalog_rows`, narrowed to rows about that file's own logic. The rows were first found mechanically: rows whose `sources` cite a Flutter test that imports the file or is named after it. Agents then narrowed them by reading the row text.

The gaps the catalog does **not** cover are listed in section "Gaps the catalog does not cover" of the summary:
- **(A)** 10 files have no Flutter test or no row, for example `deliberate_stop`, `phone_agent_work_watch`, `builtin_server_restore`, `gen_ui_connector_search_js` and `agent_host_cleanup`.
- **(B)** 21 files have an engine that passes its unit rows but that the app never reaches. No row checks that wiring.
- **(C)** Script bodies are checked only for selected properties, never for the full text.

The suggested top-up is Android-level journey rows, checked by an androidApp graph test.

## Method and checks

1. A script computed lines, `part of` parents, importing tests and candidate rows (`scope.jsonl` scope and module).
2. Four parallel read-only analysis passes followed: builtin agents/setup/team; the rest of builtin; orchestration; paseo. Each one read the Flutter files, searched `native/**` and opened the native matches to confirm them.
3. The four parts were merged and checked:
   - JSON parse of every line;
   - the key set;
   - the status values;
   - that every native path exists;
   - that every row id is in the catalog;
   - the file order against the given list.
4. I spot-checked the "done" claims, for example that `LinuxRoute.Ports`/`Storage` are contributed by `LinuxEntries` and that `:feature:linux` is in the app's module list.
5. The diff was scanned for IP addresses and emails; the loopback literals were replaced with words.

No Gradle or Flutter build ran, because this unit changes only docs (AGENTS.md check ladder: a docs-only change needs a diff/link check). For the same reason the Android SDK was not installed.

## Deviations

- The brief says "165 of the 1,522", but the list it gives has **166** paths. All 166 are recorded.
- Each record has one extra key, `evidence`: one sentence naming what was checked natively. The keys that were asked for are all present.
- A "done" status means the behaviour exists natively **and** can be reached from the app. Where native logic exists but no wiring builds it, the record says "partial" with an "engine only" gap.
- `untested` is the analysts' judgement of whether a Flutter test covers the file's main behaviour. Some files reached only through a barrel import (the Gas City DTOs) count as tested.
