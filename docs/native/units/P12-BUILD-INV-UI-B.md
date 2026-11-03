VERIFIED: green — `docs/native/inventory/ui-b.jsonl` has exactly one valid JSON line for each of the 248 listed Flutter files (the brief said 247; the list has 248 unique paths), with every required key; every `native` path exists; every `catalog_rows` id exists in `behaviors.jsonl`.

# P12-BUILD-INV-UI-B — file inventory, UI part B

Unit: INVENTORY-UI-B. Base `native/p12-integrate-2026-11-03` at `71f74de8e`, branch
`claude/file-inventory-ui-b-parity-pj204w`. **Analysis only. No product code changed.** No journeys were added, so
"journeys now working" does not apply.

## Deliverables

- [`docs/native/inventory/ui-b.jsonl`](../inventory/ui-b.jsonl): one line per Flutter file, with keys `file, lines, kind,
  summary, native, status, gaps, portability, target_module, catalog_rows, untested`.
- [`docs/native/inventory/ui-b.md`](../inventory/ui-b.md): totals by status, kind and target module; the 20 biggest
  missing or partial files with their gaps; and the gaps no catalog row covers.

## Result

| status | files | Flutter lines |
|---|---:|---:|
| done | 9 | 2,214 |
| partial | 112 | 42,587 |
| missing | 58 | 22,847 |
| not-needed | 1 | 400 |
| after-switch | 65 | 15,204 |
| dropped | 3 | 2,422 |

- **after-switch (65):** live backgrounds and scenes (D11), the AI Team kit and team conversation (D6), FactsBook (D6),
  and the on-phone summary (S02-3).
- **dropped (3):** A2A external agents (D6) and the two Termux setup screens (D7, migrate-out only).
- **Catalog rows:** 352 distinct rows are mapped across the files. 58 files have no row, and 20 files are untested in
  Flutter. Thirty-six partial or missing files (about 10,000 Flutter lines) have gaps that no catalog row covers. They
  are listed in `ui-b.md`.

## Biggest parity gaps (for the integrator and owners)

1. **The full composer is not wired.** `:feature:chat-composer` exists, but the app routes `BasicComposerHost`. Because
   of that, these are unreachable:
   - prompt history and the full-screen editor
   - Stop and send
   - starters and folder facts
   - phrase chips, nudges and forms (the engines exist but nothing calls them)
   - Clear draft with Undo
   - about 40 app commands and the "Commands and agents" launcher
2. **The chat lacks several pieces:**
   - Stop after this step and Send later.
   - Queued and inbox rows with Edit, Discard and Resend, and the "Waiting to send · N" strip.
   - The timeline sheet: its pill opens Find instead.
   - Multi-question sheets.
   - The picture gallery.
   - From the conversation menu: the approvals sheet, session note, continue-on-another-device, and the subagents and
     related-conversation lists.
3. **Library and integrations are missing entirely**: providers, OAuth, device code, MCP, skills and references.
   - `:engine:integrations` does not exist.
   - The `core/domain/Operations.kt` gateways answer unavailable, and `Oc2Gateway` does not implement them.
4. **Phone agents and phone setup are not wired:**
   - `AgentsWiring` binds `NoPhoneAgents`.
   - There is no setup engine, so the 9 phone-setup screens are missing and Reset Linux cannot set Linux up again.
   - The Linux manager lacks Services, Add a service, Backups and the process data, and there is no terminal key bar
     or local terminal.
5. **Settings rows are hidden and diagnostics stop at a basic report:**
   - `SettingsWiring.OPENS` leaves out Guide, Tips, Keep running, Notices, Providers, Usage, Tools, Agents and
     Permissions.
   - Diagnostics have no screens on top of the crash, exit and perf engines.
   - There is no Development services, Project health, Manage space or Demo screen.
6. **The Style system is a stored setting only**, and `surfaceStyles = false`:
   - Brutal, Paper, Signal and Soft draw nothing.
   - Signal home is missing (B-NAV-027).
   - The motion parts (`KitRefresh`, `KitTabSwitcher`, `KitReveal`) have no callers.
7. **Chats home and New chat are thin:**
   - There is no sources sheet, no project filter sheet, and no unreachable-server banner.
   - There is no New-chat start screen (model, project, worktree, agent, starters).
   - Claude import and Git repair are missing.
   - "Needs you" is a filter, not the Inbox.
   - Opening a chat on a specific request or failure is not wired, and notification taps drop the request id.

## Method

1. I built a mechanical index per file: line count, the Flutter tests that import the file, share its test name or
   name a distinctive class of it (a test counted only if 12 or fewer tests named the class), and the candidate rows
   sourced from those tests.
2. Six parallel read-only passes split the files into kit/FactsBook, chat A, chat B, agents/library, settings/misc and
   flows. Each pass read its files and grepped `native/**` for class names, strings, routes and domain words. It
   checked reachability through `*Entries.kt`, `ui/routes` and `androidApp/graph/*Wiring.kt`, and trimmed or extended
   the catalog rows by their behaviour text.
3. A merge step validated every line against the schema, checked that every native path exists and that every row id
   is real, and refused `done` without native evidence and `partial`/`missing` without gaps.

## Deviations

- I did not install the Android SDK and did not run Gradle, Flutter or the tests. The unit is analysis plus a record,
  with no product code, so builds would prove nothing about it. "VERIFIED: green" covers the record's completeness and
  validity, as the unit defines it.
- The `catalog_rows` for the `part of chat_screen.dart` files are partly keyword matches. Their Flutter widget tests
  (`chat_stop_after_step_test`, `chat_send_later_test`, `chat_picture_gallery_test`, …) are not sources of any catalog
  row. That itself is a catalog gap.
- The `untested` flag is a judgement from the test search. A `false` value means a Flutter test exercises the main
  behaviour, not that a catalog row exists.

## For the integrator

- Before the switch, add catalog rows for the "Gaps no catalog row covers" table in `ui-b.md`.
- Wiring-only gaps are the cheapest parity wins, because their code already exists:
  - route the full composer
  - add the missing `OPENS` entries in `SettingsWiring`
  - add the sign-in terminal route to `AgentsEntries`
  - add callers for the kit motion parts
  - turn on `surfaceStyles` once the styles render
  - connect the starters, phrase chips, nudges and forms engines to the chat
