# OD1: features the servers had and the app didn't (2026-10-10)

The coverage gates (`docs/qa/coverage-gates-2026-10-10/`) listed seven things the servers support that the app
did not offer. The owner answered "All". Three lanes built them, each checking the server contract first;
anything the server cannot do was recorded as not feasible instead of worked around.

## Outcome

| # | Feature | Result | Where a person finds it |
|---|---|---|---|
| 1 | Files, Changes, Worktrees, Terminal for Claude Code / Pi (Paseo) | Built | Files tab → project rows; Worktrees create/delete; Terminal create/type/rename/stop |
| 2 | Search inside files | Built for OpenCode 1 (`/find`) only | Files search → "Search inside files for "…"" row (also a filter). OpenCode 2 and Paseo have no text search in their contracts |
| 3 | Update the computer's agents | Built (`daemon.update`) | Server settings → "Update agents on <computer>" → confirmation |
| 4 | Agent switches (fast mode) | Built (`set_agent_feature`) | Chip next to the model chip → "<Agent> settings" sheet |
| 5 | Import a conversation started in Claude Code | Built (`import_agent_request`) | New conversation → "Import from Claude Code" |
| 6 | AI Team controls | Pause/resume project, delete project, force stop, scheduled jobs on/off built | Project page; agent ⋯ → Force stop, also offered after a stop that didn't work |
| 6 | Undo merge | Not feasible | No revert/undo route in the supervisor, host front or phone engine |
| 6 | Scheduled job create/delete | Not feasible | Supervisor has list and enable/disable only |
| 6 | Per-session permission mode | Not built | Supervisor takes a provider-defined free string with no allowed values |
| 7 | Why an AI Team agent can't start | Built | Agent page notice "<agent> can't start" + plain reason; host text under Technical details |

Ledgers moved from "not offered: owner decision needed" to "reachable"/"shown" with tap tests that check the
gateway call against the contract (`test/fixtures/coverage/*_ledger.json`, `test/coverage/`).

## Review rounds before merge
- Fast mode first sat inside "Choose a model"; the owner called it a UI/UX mess. It moved to its own chip and sheet.
- Paseo failures read "It changed on the server in the meantime" (every Paseo failure mapped to 409). Now only
  real clashes keep that; unreachable cases say "Couldn't reach your computer…".
- AI Team notice showed a raw id ("gastown.witness") and said "Can't start" twice; fixed.
- Full suite caught a regression: Worktrees for Paseo keyed the Files tab on `worktreeCreate` (default true), so
  Codex and other no-project servers showed Files. New `worktreeBrowsing` flag (3cdf47512).
- `team_engine_coverage_test` compared fixed 2026-10-09 times with the real clock; the sample now dates itself (a833904fc).

## Emulator proof (build 2207, Pixel_6, real Paseo 0.9.1 daemon + OpenCode 1.18.23)
Pass: project rows, Files browse/read, Changes, Terminal `ls`, Worktree create (in a scratch repo, removed
after), Fast mode chip/sheet, model chip shows "Opus 5.5", Import list (empty for the scratch folder), Update
agents confirmation (cancelled; daemon stayed 0.9.1), Search inside files.
Skipped: AI Team (no Gas City host running; covered by widget/gateway tests only — force stop and delete
semantics follow the contract, not a live host).

Bugs it found, fixed in 63b6071e8 and 4d955a77b:
- new Claude Code conversation said "Ask OpenCode…" before the first send;
- the daemon's "Claude" label leaked ("Ask Claude…", "Claude settings"); one mapping now says "Claude Code";
- worktree copy said "OpenCode makes a separate branch" on Paseo;
- opening a folder on OpenCode 1.0.193 showed "Bad state: Could not hydrate pending questions…": pending
  questions no longer block opening, and location, catalog, session-list and connection failures carry the
  failure and show plain words (technical text under Details).

Not a bug: on the emulator, servers at 127.0.0.1 are treated as "This phone" — on a real phone that address is the phone.

## Final gate
Candidate 27488c0ee: full suite 1,295 files in 18 chunks, all passed; `flutter analyze lib test` clean. APK 2208 built from it (signer 1de5bf08…).
