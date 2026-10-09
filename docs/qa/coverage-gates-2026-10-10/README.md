# Coverage gates — whole app (2026-10-09/10)

Owner request: a systematic way to (1) prove the app uses what each server sends and offers, and
(2) check that it is shown well.

## Method
- **Data gate:** generators in `tool/coverage/` read each source's own schema (Paseo 0.9.1 zod protocol,
  OpenCode 1/2 OpenAPI contracts in `contracts/`, Gas City OpenAPI, the app's own parsers where no contract
  exists) and emit realistic cases with a unique marker per field. Tests in `test/coverage/` run them through
  the real mappers and screens and check every marker against a ledger in `test/fixtures/coverage/`:
  each field is `shown` or `ignored: <reason>`. A new field, a shown field going missing, or an ignored field
  appearing fails the build.
- **Action gate:** every mutating call (POST/PUT/PATCH/DELETE, Paseo mutating requests) is reachable from a
  visible control (tap test + gateway request matched to the contract) or ledgered `not offered: <reason>`.
- **Look gate:** contact sheets per area reviewed by the coordinator (not for the owner).
- **Privacy gates:** marker secrets fed through everything shared/exported (diag), credentials never shown
  (models, tools, settings), all 134 stored preference keys classified against the profile deletion sweep.

## Areas (all merged into feat/genui-fe)
Chat (Paseo tools/items/permissions, OpenCode 1/2 parts/requests/events), lists, servers, models/usage,
tools/MCP, files/changes/worktrees, terminal, settings, phone setup/agents, diagnostics, AI Team.

## Real defects found and fixed (selection)
- Claude Code tool answers (MCP, ToolSearch, grep, fetch, skills) never shown — Paseo `{output: …}` wrapper.
- Delete/Archive conversation lost on 2026-10-03 (Work screen removal) — restored on the conversation menu.
- Opaque tokens leaking into shared problem/failed-job/timing reports — masked.
- AI Team pending questions arrived without their text/options; tool approvals unanswerable.
- Permission cards showed "(all matching requests)" / "$ " for non-shell input; Always allow offered with no rule.
- OpenCode 2 failed runs shown as "Done"; to-dos, errors, notifications, plans, sub-agent logs, search results.

## Final full suite
Candidate `c741f46e4` (+ test-only commit `779b35139`): 1,277 files in 18 chunks. Failures were 11 golden/test
files reflecting intended screen changes (each diff checked); regenerated and re-run green. No lib change after
the suite. `flutter analyze` clean.

## Owner decisions found (backlog OD1)
Files/Changes/Worktrees/Terminal for Claude Code chats; search inside files; update the computer helper from the
phone; Claude Code agent settings (fast mode); import a Claude-app conversation; AI Team schedules/suspend/
delete project/undo merge; show the host's reason when an AI Team agent is unavailable.
